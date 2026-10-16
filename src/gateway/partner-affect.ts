import * as fs from "node:fs/promises";
import * as path from "node:path";
import type { MediaSceneState } from "./media-production-controls.js";
import { getPartnerDir } from "./partner-store.js";

// How a partner feels right now: affection (好感度, 0..100, only moves slowly), arousal (高まり,
// 0..100, rises when she is touched and falls by itself) and a mood. Where she likes or dislikes
// being touched, the touch presets the user saved, and the scene the conversation is in (place,
// clothes, pose, what is going on) belong to the same file. Chat reads it (a note in the prompt)
// and may change it (a state line at the end of a reply); the avatar reports touches to it.

export const TOUCH_ZONES = [
  "head",
  "face",
  "neck",
  "shoulder",
  "chest",
  "belly",
  "waist",
  "hips",
  "thighs",
  "legs",
  "hands",
] as const;
export type TouchZone = (typeof TOUCH_ZONES)[number];
export const TOUCH_ZONE_LABELS: Record<TouchZone, string> = {
  head: "頭",
  face: "顔・口",
  neck: "首筋",
  shoulder: "肩・背中",
  chest: "胸",
  belly: "お腹",
  waist: "腰",
  hips: "お尻",
  thighs: "太もも",
  legs: "脚・足",
  hands: "手",
};
export const TOUCH_KINDS = ["tap", "hold", "slide"] as const;
export type TouchKind = (typeof TOUCH_KINDS)[number];

export const MOODS = [
  "calm",
  "happy",
  "shy",
  "aroused",
  "sulky",
  "lonely",
  "jealous",
  "sleepy",
  "playful",
] as const;
export type Mood = (typeof MOODS)[number];
export const MOOD_LABELS: Record<Mood, string> = {
  calm: "穏やか",
  happy: "うれしい",
  shy: "照れ",
  aroused: "火照り",
  sulky: "拗ね",
  lonely: "寂しい",
  jealous: "やきもち",
  sleepy: "眠い",
  playful: "いたずら",
};

export type TouchStep = { zone: TouchZone; kind: TouchKind; count: number };
export type TouchPreset = { id: string; name: string; steps: TouchStep[] };
export type TouchProfile = {
  favorite: TouchZone[];
  weak: TouchZone[];
  dislike: TouchZone[];
  presets: TouchPreset[];
};
/** Where the conversation is now; pictures made for her copy it (see sceneForMedia). */
export type PartnerScene = {
  place?: string;
  outfit?: string;
  pose?: string;
  situation?: string;
  updatedAt?: number;
};
export const SCENE_FIELDS = ["place", "outfit", "pose", "situation"] as const;
export type SceneField = (typeof SCENE_FIELDS)[number];
export const SCENE_FIELD_LABELS: Record<SceneField, string> = {
  place: "場所",
  outfit: "服装",
  pose: "姿勢",
  situation: "状況",
};
const SCENE_FIELD_MAX: Record<SceneField, number> = {
  place: 80,
  outfit: 120,
  pose: 100,
  situation: 200,
};
/** A field set to "" clears it. */
export type SceneChange = Partial<Record<SceneField, string>>;

export type Affect = {
  affection: number;
  arousal: number;
  mood: Mood;
  updatedAt: number;
  profile: TouchProfile;
  scene: PartnerScene;
};

const AROUSAL_FALL_PER_MIN = 6;

const clamp = (n: number, lo = 0, hi = 100): number => Math.min(hi, Math.max(lo, Math.round(n)));
const zones = (raw: unknown): TouchZone[] =>
  Array.isArray(raw) ? TOUCH_ZONES.filter((z) => raw.includes(z)) : [];

function normalizeScene(raw: unknown): PartnerScene {
  const input = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const scene: PartnerScene = {};
  for (const field of SCENE_FIELDS) {
    const value = input[field];
    if (typeof value === "string" && value.trim())
      scene[field] = value.trim().slice(0, SCENE_FIELD_MAX[field]);
  }
  if (typeof input.updatedAt === "number" && Object.keys(scene).length > 0)
    scene.updatedAt = input.updatedAt;
  return scene;
}

/** Applies a change to the scene: a text replaces the field, "" clears it. */
export function mergeScene(
  scene: PartnerScene,
  change: SceneChange,
  now = Date.now(),
): PartnerScene {
  const next: PartnerScene = { ...scene };
  let changed = false;
  for (const field of SCENE_FIELDS) {
    const value = change[field];
    if (typeof value !== "string") continue;
    changed = true;
    if (value.trim()) next[field] = value.trim().slice(0, SCENE_FIELD_MAX[field]);
    else delete next[field];
  }
  if (!changed) return scene;
  const hasAny = SCENE_FIELDS.some((field) => next[field]);
  if (hasAny) next.updatedAt = now;
  else delete next.updatedAt;
  return next;
}

/** The scene as the media studio's scene state (place, pose, clothes and what carries over). */
export function sceneForMedia(scene: PartnerScene): MediaSceneState | undefined {
  const state: MediaSceneState = {
    ...(scene.place ? { place: scene.place } : {}),
    ...(scene.pose ? { pose: scene.pose } : {}),
    ...(scene.outfit ? { wardrobe: scene.outfit } : {}),
    ...(scene.situation ? { continuity: scene.situation } : {}),
  };
  return Object.keys(state).length > 0 ? state : undefined;
}

function file(partnerId: string): string {
  return path.join(getPartnerDir(partnerId), "affect.json");
}

export function normalizeAffect(raw: Partial<Affect> | undefined, now = Date.now()): Affect {
  const profile = raw?.profile;
  const presets = (profile?.presets ?? []).slice(0, 20).map((p, i): TouchPreset => ({
    id: typeof p.id === "string" && p.id ? p.id.slice(0, 40) : `preset-${i}`,
    name: String(p.name ?? "").slice(0, 30) || `プリセット${i + 1}`,
    steps: (Array.isArray(p.steps) ? p.steps : []).slice(0, 30).flatMap((s): TouchStep[] => {
      const zone = TOUCH_ZONES.find((z) => z === s.zone);
      const kind = TOUCH_KINDS.find((k) => k === s.kind);
      return zone && kind ? [{ zone, kind, count: clamp(Number(s.count ?? 1), 1, 20) }] : [];
    }),
  }));
  const savedAt = typeof raw?.updatedAt === "number" ? raw.updatedAt : now;
  const minutes = Math.max(0, (now - savedAt) / 60_000);
  return {
    affection: clamp(Number(raw?.affection ?? 30)),
    // Arousal cools down by itself while nothing touches her.
    arousal: clamp(Number(raw?.arousal ?? 0) - minutes * AROUSAL_FALL_PER_MIN),
    mood: MOODS.find((m) => m === raw?.mood) ?? "calm",
    updatedAt: now,
    profile: {
      favorite: zones(profile?.favorite),
      weak: zones(profile?.weak),
      dislike: zones(profile?.dislike),
      presets,
    },
    scene: normalizeScene(raw?.scene),
  };
}

export async function loadAffect(partnerId: string): Promise<Affect> {
  try {
    return normalizeAffect(
      JSON.parse(await fs.readFile(file(partnerId), "utf-8")) as Partial<Affect>,
    );
  } catch {
    return normalizeAffect(undefined);
  }
}

export async function saveAffect(partnerId: string, affect: Affect): Promise<void> {
  const target = file(partnerId);
  await fs.mkdir(path.dirname(target), { recursive: true });
  const tmp = `${target}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(affect), "utf-8");
  await fs.rename(tmp, target);
}

export async function setTouchProfile(
  partnerId: string,
  input: Partial<TouchProfile>,
): Promise<Affect> {
  const current = await loadAffect(partnerId);
  const next = normalizeAffect({
    ...current,
    profile: { ...current.profile, ...input } as TouchProfile,
  });
  // A place cannot be both a weak spot and a dislike.
  next.profile.dislike = next.profile.dislike.filter(
    (z) => !next.profile.weak.includes(z) && !next.profile.favorite.includes(z),
  );
  await saveAffect(partnerId, next);
  return next;
}

/** Changes the scene from the app; `null` clears all of it. */
export async function setPartnerScene(
  partnerId: string,
  change: SceneChange | null,
): Promise<Affect> {
  const affect = await loadAffect(partnerId);
  affect.scene = change ? mergeScene(affect.scene, change) : {};
  await saveAffect(partnerId, affect);
  return affect;
}

export type TouchResult = {
  affect: Affect;
  /** Extra levels of arousal the avatar adds at once (a weak spot makes her feel more). */
  boost: number;
  /** "weak" / "favorite" / "dislike" / "plain": how she took this place. */
  reaction: "weak" | "favorite" | "dislike" | "plain";
};

/** One touch on a place: moves affection, arousal and mood by what she likes. */
export async function applyTouch(
  partnerId: string,
  zone: TouchZone,
  kind: TouchKind,
  count = 1,
): Promise<TouchResult> {
  const affect = await loadAffect(partnerId);
  const { weak, favorite, dislike } = affect.profile;
  const reaction = weak.includes(zone)
    ? "weak"
    : favorite.includes(zone)
      ? "favorite"
      : dislike.includes(zone)
        ? "dislike"
        : "plain";
  const strength = { tap: 6, hold: 12, slide: 26 }[kind];
  const times = clamp(count, 1, 20);
  for (let i = 0; i < times; i++) {
    if (reaction === "dislike") {
      affect.affection = clamp(affect.affection - 2);
      affect.arousal = clamp(affect.arousal - 4);
      affect.mood = "sulky";
      continue;
    }
    const factor = reaction === "weak" ? 2 : reaction === "favorite" ? 1.5 : 1;
    affect.arousal = clamp(affect.arousal + strength * factor);
    affect.affection = clamp(affect.affection + (reaction === "plain" ? 0.4 : 1));
    affect.mood = affect.arousal >= 60 ? "aroused" : reaction === "plain" ? "shy" : "happy";
  }
  affect.updatedAt = Date.now();
  await saveAffect(partnerId, affect);
  return { affect, boost: reaction === "weak" ? 2 : reaction === "favorite" ? 1 : 0, reaction };
}

/** The note added to a chat turn so she answers as she feels. */
export function affectNote(affect: Affect, name: string): string {
  const label = (list: TouchZone[]) => list.map((z) => TOUCH_ZONE_LABELS[z]).join("・") || "なし";
  const feeling =
    affect.affection >= 80
      ? "深く愛している"
      : affect.affection >= 55
        ? "とても好き"
        : affect.affection >= 30
          ? "好意がある"
          : "まだ距離がある";
  const heat =
    affect.arousal >= 70
      ? "息が上がるほど高まっている"
      : affect.arousal >= 35
        ? "体が火照ってきている"
        : affect.arousal >= 10
          ? "少しどきどきしている"
          : "落ち着いている";
  const sceneParts = SCENE_FIELDS.flatMap((field) =>
    affect.scene[field] ? [`${SCENE_FIELD_LABELS[field]}: ${affect.scene[field]}`] : [],
  );
  const sceneText = sceneParts.length
    ? `【いまの場面】${sceneParts.join("／")}。この場面の続きとして話す（場所・服装・姿勢が急に変わらないように）。`
    : "【いまの場面】まだ決まっていない。";
  return (
    `【${name}のいまの気持ち】好感度 ${affect.affection}/100（ユーザーを${feeling}）／高まり ${affect.arousal}/100（${heat}）／気分: ${MOOD_LABELS[affect.mood]}。` +
    `触られて好きな場所: ${label(affect.profile.favorite)}／弱い場所: ${label(affect.profile.weak)}／嫌な場所: ${label(affect.profile.dislike)}。\n` +
    `${sceneText}\n` +
    "この気持ちが、口調・言葉・仕草にそのまま出るように話す。会話の流れで気持ちや場面が変わったら、返事の最後に次の行を1つ付けてもよい（ユーザーには見えない）: " +
    '<<state {"affection":2,"arousal":-5,"mood":"照れ","place":"寝室","outfit":"下着姿","pose":"ベッドに座っている","situation":"見つめ合っている"}>>' +
    "（affection・arousal は増減する数。mood は 穏やか/うれしい/照れ/火照り/拗ね/寂しい/やきもち/眠い/いたずら のどれか。" +
    'place=場所、outfit=服装（脱いだ・着替えたなど今の状態）、pose=姿勢、situation=いま何をしているか。変わった項目だけ書く。場面を終えるときは "" にする。何も変わらなければ行ごと書かない）'
  );
}

const STATE_LINE = /<<\s*state\s*(\{[\s\S]*?\})\s*>>/i;

/** Cuts the state line out of a reply and returns what it asked for. */
export type StateChange = {
  affection?: number;
  arousal?: number;
  mood?: Mood;
  scene?: SceneChange;
};

export function extractStateLine(reply: string): {
  reply: string;
  change?: StateChange;
} {
  const match = STATE_LINE.exec(reply);
  const visible = reply.replace(/<<\s*state[\s\S]*?>>/gi, "").trim();
  if (!match?.[1]) return { reply: visible };
  try {
    const data = JSON.parse(match[1]) as Record<string, unknown>;
    const mood = Object.entries(MOOD_LABELS).find(
      ([id, label]) => data.mood === id || data.mood === label,
    )?.[0] as Mood | undefined;
    const num = (v: unknown) =>
      typeof v === "number" && Number.isFinite(v) ? Math.max(-15, Math.min(15, v)) : undefined;
    const scene: SceneChange = {};
    for (const field of SCENE_FIELDS) {
      const value = data[field];
      if (typeof value === "string") scene[field] = value;
    }
    const change: StateChange = {
      affection: num(data.affection),
      arousal: num(data.arousal),
      mood,
      ...(Object.keys(scene).length > 0 ? { scene } : {}),
    };
    return { reply: visible, change };
  } catch {
    return { reply: visible };
  }
}

export async function applyStateChange(partnerId: string, change: StateChange): Promise<Affect> {
  const affect = await loadAffect(partnerId);
  if (change.affection) affect.affection = clamp(affect.affection + change.affection);
  if (change.arousal) affect.arousal = clamp(affect.arousal + change.arousal);
  if (change.mood) affect.mood = change.mood;
  if (change.scene) affect.scene = mergeScene(affect.scene, change.scene);
  affect.updatedAt = Date.now();
  await saveAffect(partnerId, affect);
  return affect;
}
