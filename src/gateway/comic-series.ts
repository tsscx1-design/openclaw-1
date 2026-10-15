// Comics and manga: the shared panel model (who is in a panel, where they stand, what they say),
// how a panel becomes an image request, and serial comics — a series keeps its title, genre,
// cast (fixed looks for the image model, how each character talks, an optional trained LoRA) and
// what happened so far, so every episode continues the same story with the same people.
// Episodes are vertical-scroll strips (webtoon style) of about 24 panels.

import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { characterRuleEnabled, promptRule } from "./blocked-terms-config.js";
import { ADULT_TAGS } from "./minor-safeguards.js";
import { resolveOpenClawHome } from "./partner-store.js";

export type ComicRating = "general" | "adult";
export type PanelFrame = "tall" | "wide" | "square";
export type StagePosition = "left" | "center" | "right";

export type ComicCharacter = {
  name: string;
  /** English danbooru tags fixing the look (1girl/1boy, hair, eyes, body, outfit). */
  look: string;
  /** Personality, role and way of speaking, in Japanese. */
  profile: string;
  /** Character sheet drawn for this series (front, full body), once made. */
  refImage?: string;
  /** Trained character LoRA and the word that calls it up. */
  lora?: string;
  trigger?: string;
  /** The character library entry this cast member is (comic-characters.ts). */
  characterId?: string;
  /** The media job that drew the chosen character sheet (the app shows it). */
  sheetJobId?: string;
  /** Height from the character creator; panels with several people show who is taller. */
  heightCm?: number;
  /** Negative-prompt tags the user set for this character in the app (English, comma-separated). */
  negative?: string;
};

export type PanelCharacter = { name: string; pos: StagePosition; action: string };

export type ComicPanel = {
  frame: PanelFrame;
  /** Background, light, camera — what is not about one person. */
  scene: string;
  characters: PanelCharacter[];
  lines: Array<{ who: string; text: string }>;
  narration?: string;
};

export type ComicEpisode = {
  no: number;
  title: string;
  /** What happened, for the next episode's script. */
  summary: string;
  jobId: string;
  createdAt: number;
};

export type ComicSeries = {
  id: string;
  title: string;
  genre: string;
  premise: string;
  rating: ComicRating;
  imageModel?: string;
  cast: ComicCharacter[];
  episodes: ComicEpisode[];
  createdAt: number;
  updatedAt: number;
};

export type ComicScript = { title: string; summary: string; panels: ComicPanel[] };

/** A full episode, about a weekly magazine chapter; written in parts of COMIC_PANELS_PER_PART. */
export const COMIC_PANELS_PER_EPISODE = 100;
/** A local model keeps a 24-panel script consistent; longer ones drift or stop short. */
export const COMIC_PANELS_PER_PART = 25;
/** Cast members a series keeps (a harem needs more than a handful). */
export const MAX_COMIC_CAST = 16;

// ---------- storage ----------

function seriesDir(): string {
  return path.join(resolveOpenClawHome(), "media", "comics");
}

export function isComicSeriesId(id: string): boolean {
  return /^comic-[a-f0-9]{8}$/.test(id);
}

function seriesFile(id: string): string {
  if (!isComicSeriesId(id)) throw new Error("Invalid series id");
  return path.join(seriesDir(), `${id}.json`);
}

/** Per-series files: character sheets, LoRA datasets and weights. */
export function comicAssetDir(id: string): string {
  if (!isComicSeriesId(id)) throw new Error("Invalid series id");
  return path.join(seriesDir(), id);
}

export async function listComicSeries(): Promise<ComicSeries[]> {
  let names: string[] = [];
  try {
    names = await fs.readdir(seriesDir());
  } catch {
    return [];
  }
  const all: ComicSeries[] = [];
  for (const name of names) {
    if (!/^comic-[a-f0-9]{8}\.json$/.test(name)) continue;
    try {
      all.push(JSON.parse(await fs.readFile(path.join(seriesDir(), name), "utf-8")) as ComicSeries);
    } catch {
      // Skip a broken file.
    }
  }
  return all.toSorted((a, b) => b.updatedAt - a.updatedAt);
}

export async function getComicSeries(id: string): Promise<ComicSeries | undefined> {
  if (!isComicSeriesId(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(seriesFile(id), "utf-8")) as ComicSeries;
  } catch {
    return undefined;
  }
}

export async function saveComicSeries(series: ComicSeries): Promise<void> {
  await fs.mkdir(seriesDir(), { recursive: true });
  const file = seriesFile(series.id);
  const tmp = `${file}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(series, null, 2), "utf-8");
  await fs.rename(tmp, file);
}

export async function deleteComicSeries(id: string): Promise<boolean> {
  try {
    await fs.rm(seriesFile(id));
    await fs.rm(comicAssetDir(id), { recursive: true, force: true });
    return true;
  } catch {
    return false;
  }
}

export function newComicSeries(input: {
  title: string;
  genre: string;
  premise: string;
  rating: ComicRating;
  imageModel?: string;
  cast?: unknown[];
}): ComicSeries {
  const now = Date.now();
  return {
    id: `comic-${randomBytes(4).toString("hex")}`,
    title: clip(input.title, 40) || "無題の作品",
    genre: clip(input.genre, 40),
    premise: clip(input.premise, 1500),
    rating: input.rating,
    ...(input.imageModel ? { imageModel: input.imageModel } : {}),
    cast: (input.cast ?? [])
      .map(cleanCharacter)
      .filter((c) => c.name && c.look)
      .slice(0, MAX_COMIC_CAST),
    episodes: [],
    createdAt: now,
    updatedAt: now,
  };
}

// ---------- text cleanup ----------

export function clip(raw: unknown, max: number): string {
  if (typeof raw !== "string") return "";
  return raw
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

/** Tags the image model reads: printable ASCII, no weights or prompt syntax. */
export function cleanTags(raw: unknown, max: number): string {
  return clip(raw, max * 2)
    .replace(/[^\x20-\x7E]/g, " ")
    .replace(/[<>[\]{}|\\`$]/g, " ")
    .replace(/\s+/g, " ")
    .replace(/\s*,\s*/g, ", ")
    .replace(/(?:,\s*)+/g, ", ")
    .replace(/^,\s*|,\s*$/g, "")
    .trim()
    .slice(0, max);
}

export function cleanCharacter(raw: unknown): ComicCharacter {
  const c = (raw ?? {}) as Record<string, unknown>;
  return {
    name: clip(c.name, 20),
    look: cleanTags(c.look, 320),
    profile: clip(c.profile, 300),
    ...(typeof c.refImage === "string" && c.refImage ? { refImage: c.refImage } : {}),
    ...(typeof c.lora === "string" && c.lora ? { lora: c.lora } : {}),
    ...(typeof c.trigger === "string" && /^[a-z0-9_]{3,40}$/.test(c.trigger)
      ? { trigger: c.trigger }
      : {}),
    ...(typeof c.characterId === "string" && /^chara-[a-f0-9]{8}$/.test(c.characterId)
      ? { characterId: c.characterId }
      : {}),
    ...(typeof c.sheetJobId === "string" && /^[a-f0-9]{12}$/.test(c.sheetJobId)
      ? { sheetJobId: c.sheetJobId }
      : {}),
    ...(typeof c.negative === "string" && c.negative.trim()
      ? { negative: clip(c.negative, 500) }
      : {}),
  };
}

export function firstJsonObject(text: string): Record<string, unknown> {
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(text)?.[1] ?? text;
  const start = fenced.indexOf("{");
  const end = fenced.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("返事がJSONになっていません");
  return JSON.parse(fenced.slice(start, end + 1)) as Record<string, unknown>;
}

// ---------- output shapes ----------
// The writer's reply is held to these JSON schemas (llama.cpp grammar), so a long script can not
// come back with broken keys or brackets — the local model often broke them past ~20 panels.

/** maxLength keeps the model from running one string on until the token limit cuts the JSON. */
const str = (maxLength: number) => ({ type: "string", maxLength }) as const;

export const COMIC_OUTLINE_SCHEMA = {
  type: "object",
  properties: {
    title: str(40),
    beats: { type: "array", minItems: 6, maxItems: 16, items: str(160) },
    hook: str(160),
  },
  required: ["title", "beats", "hook"],
};

export function comicScriptSchema(panels: number): Record<string, unknown> {
  const panel = {
    type: "object",
    properties: {
      frame: { type: "string", enum: ["tall", "wide", "square"] },
      scene: str(300),
      characters: {
        type: "array",
        maxItems: 3,
        items: {
          type: "object",
          properties: {
            name: str(20),
            pos: { type: "string", enum: ["left", "center", "right"] },
            action: str(200),
          },
          required: ["name", "pos", "action"],
        },
      },
      lines: {
        type: "array",
        maxItems: 3,
        items: {
          type: "object",
          properties: { who: str(20), text: str(60) },
          required: ["who", "text"],
        },
      },
      narration: str(40),
    },
    required: ["frame", "scene", "characters", "lines"],
  };
  return {
    type: "object",
    properties: {
      title: str(40),
      summary: str(400),
      panels: { type: "array", minItems: panels, maxItems: panels, items: panel },
    },
    required: ["title", "summary", "panels"],
  };
}

// ---------- prompts ----------

export function ratingRules(rating: ComicRating): string {
  const adultsOnly = characterRuleEnabled("adultsOnly");
  return rating === "adult"
    ? `- 成人向けの作品。性的な場面・性行為の描写も、物語に合わせて遠慮なく書いてよい（台詞・ナレーション・タグとも）。`
    : `- 全年齢向けの作品。${promptRule("generalSeries")}${adultsOnly ? "登場人物は成人。" : ""}`;
}

export function castSystemPrompt(rating: ComicRating): string {
  return `あなたは日本の漫画の原作者です。作品の設定から主要な登場人物を3〜5人作ります。
次のJSONだけを出力してください。
{"cast":[{"name":"名前（カタカナか漢字）","look":"English danbooru tags fixing the appearance, 12-20 tags: 1girl or 1boy, mature female or mature male, hair color, hair length and style, eye color, distinctive features (elf ears, horns, scar...), body type, outfit with colors","profile":"役割・性格・口調・一人称（日本語、100文字以内）"}]}
- 1人目は主人公。見た目は互いにはっきり違う（髪の色・髪型・服の色を重ねない）。
${ratingRules(rating)}`;
}

export function parseCast(reply: string): ComicCharacter[] {
  const data = firstJsonObject(reply);
  const cast = (Array.isArray(data.cast) ? data.cast : [])
    .map(cleanCharacter)
    .filter((c) => c.name && c.look)
    .slice(0, 8);
  if (cast.length === 0) throw new Error("登場人物を作れませんでした");
  return cast;
}

export function castBlock(cast: ComicCharacter[]): string {
  return cast.map((c) => `- ${c.name}: ${c.profile}`).join("\n");
}

/** How every panel is written, shared by manga pages and comic episodes. */
export function panelJsonSpec(withFrame: boolean): string {
  return `{${withFrame ? '"frame":"tall か wide か square",' : ""}"characters":[{"name":"このコマに映る登場人物の名前","pos":"left か center か right（画面のどこに立つか）","action":"English tags for this character only: pose, action, facial expression, clothing state"}],"scene":"English tags for the rest: background, place, time, lighting, camera angle (cowboy shot, close-up, from above...)","lines":[{"who":"話す人の名前","text":"台詞"}],"narration":"ナレーション（不要なら空文字）"}`;
}

export const PANEL_RULES = `- characters にはそのコマに映る人物だけを入れる（声だけの人は入れない）。多くても3人。2人以上なら pos を重ねない。
- 見た目（髪・目・服）は書かない。システムが登場人物の設定から自動で足す。
- scene と action は英語のタグだけ。絵の中に文字・吹き出し・コマ枠は描かせない。
- 1コマの台詞は0〜2個、1つの台詞は30文字以内。登場人物の口調・一人称を守った自然な日本語。
- narration は必要なコマだけ、20文字以内。`;

export function seriesBible(series: ComicSeries): string {
  const story = series.episodes
    .slice(-6)
    .map((e) => `- 第${e.no}話「${e.title}」: ${e.summary}`)
    .join("\n");
  return [
    `作品名: ${series.title}`,
    `ジャンル: ${series.genre}`,
    `設定: ${series.premise}`,
    `登場人物:\n${castBlock(series.cast)}`,
    story ? `これまでの話:\n${story}` : "これまでの話: なし（今回が第1話）",
  ].join("\n");
}

/** Step 1 of an episode: the story beats, so the panel script has a plot to follow. */
export function episodeOutlinePrompt(series: ComicSeries): string {
  return `あなたは日本の漫画の原作者です。次の作品の新しい1話の流れを考えます。
${seriesBible(series)}

次のJSONだけを出力してください。
{"title":"この話の題名（20文字以内）","beats":["場面1で起きること（1〜2文）","場面2…"],"hook":"最後の引き（次が気になる終わり方）"}
- beats は12〜16個。起承転結をつけ、キャラの魅力が出る見せ場を入れる。
- これまでの話と矛盾させない。
${ratingRules(series.rating)}`;
}

/** Step 2: the outline split into panels with dialogue. */
export function episodeSystemPrompt(
  series: ComicSeries,
  panels: number,
  outline: string,
  part?: { index: number; count: number; previous?: string },
): string {
  const partNote = part
    ? part.index === 1
      ? `\nこの1話は${part.count}回に分けて書きます。今回は前半（流れの前の方）だけを、ちょうど${panels}コマで書いてください。話を終わらせず、後半へ続く所で止める。summary は前半のあらすじ。`
      : `\nこの1話は${part.count}回に分けて書きます。今回は後半（流れの残り）を、ちょうど${panels}コマで書いてください。前半の最後はこうでした:\n${part.previous ?? ""}\nsummary は1話全体のあらすじ。`
    : "";
  return `あなたは日本の縦読み漫画（ウェブトゥーン）の脚本家です。次の作品の1話を、ちょうど${panels}コマの脚本にします。${partNote}
${seriesBible(series)}

この話の流れ:
${outline}

次のJSONだけを出力してください（説明や前置きは不要）。
{"title":"この話の題名","summary":"この話で起きたことのあらすじ（150文字以内、次の話に引き継ぐ）","panels":[${panelJsonSpec(true)}]}
ルール:
- panels はちょうど${panels}個。流れの順に、見せ場は大きく（tall）、場所の紹介は wide、会話の顔は square。会話の続く所は square を続けてテンポを出し、山場の前後は tall で間をとる。
- ${part && part.index < part.count ? "最後のコマは次の場面へ続く形で止める。" : "最後のコマは引きで終える。"}
${PANEL_RULES}
${ratingRules(series.rating)}`;
}

const FRAMES: readonly PanelFrame[] = ["tall", "wide", "square"];
const POSITIONS: readonly StagePosition[] = ["left", "center", "right"];

export function parsePanel(raw: unknown): ComicPanel | undefined {
  const p = (raw ?? {}) as Record<string, unknown>;
  const characters = (Array.isArray(p.characters) ? p.characters : [])
    .map((c) => {
      const entry = (c ?? {}) as Record<string, unknown>;
      return {
        name: clip(entry.name, 20),
        pos: POSITIONS.find((x) => x === entry.pos) ?? "center",
        action: cleanTags(entry.action, 200),
      };
    })
    .filter((c) => c.name)
    .slice(0, 3);
  const lines = (Array.isArray(p.lines) ? p.lines : [])
    .map((l) => {
      const line = (l ?? {}) as Record<string, unknown>;
      return { who: clip(line.who, 20), text: clip(line.text, 45) };
    })
    .filter((l) => l.text)
    .slice(0, 3);
  const scene = cleanTags(p.scene, 400);
  if (!scene && characters.length === 0) return undefined;
  const narration = clip(p.narration, 30);
  return {
    frame: FRAMES.find((f) => f === p.frame) ?? "square",
    scene,
    characters,
    lines,
    ...(narration ? { narration } : {}),
  };
}

export function parseComicScript(reply: string, panels: number): ComicScript {
  const data = firstJsonObject(reply);
  const list = (Array.isArray(data.panels) ? data.panels : [])
    .map(parsePanel)
    .filter((p): p is ComicPanel => p !== undefined)
    .slice(0, panels);
  // A model sometimes stops a few panels short; most of an episode is still worth drawing.
  if (list.length < Math.ceil(panels * 0.6)) {
    throw new Error(`脚本のコマが足りません（${list.length}/${panels}）`);
  }
  return { title: clip(data.title, 30), summary: clip(data.summary, 300), panels: list };
}

// ---------- panel → image request ----------

export type PanelRender = {
  prompt: string;
  /** Extra negative tags for this panel. */
  negative: string;
  regions?: Array<{ prompt: string; x0: number; x1: number }>;
  loras: Array<{ path: string; scale: number }>;
};

const COUNT_TAG = /^(?:\d+|multiple)(?:girls?|boys?|others?)$/;

/** "1girl, mature female, ..." → gender bucket and the tags without the count. */
function splitLook(look: string): { gender: "girl" | "boy" | "other"; rest: string } {
  const tags = look
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);
  const count = tags.find((t) => COUNT_TAG.test(t)) ?? "";
  const gender = /girl/.test(count) ? "girl" : /boy/.test(count) ? "boy" : "other";
  return { gender, rest: tags.filter((t) => !COUNT_TAG.test(t)).join(", ") };
}

function countTags(genders: Array<"girl" | "boy" | "other">): string {
  const girls = genders.filter((g) => g === "girl").length;
  const boys = genders.filter((g) => g === "boy").length;
  const others = genders.filter((g) => g === "other").length;
  return [
    girls ? `${girls}girl${girls > 1 ? "s" : ""}` : "",
    boys ? `${boys}boy${boys > 1 ? "s" : ""}` : "",
    others ? `${others}other${others > 1 ? "s" : ""}` : "",
  ]
    .filter(Boolean)
    .join(", ");
}

/** Spans across the picture for characters standing left → right. */
function spansFor(count: number): Array<[number, number]> {
  if (count === 2)
    return [
      [0, 0.52],
      [0.48, 1],
    ];
  return [
    [0, 0.36],
    [0.32, 0.68],
    [0.64, 1],
  ];
}

/**
 * The image request for one panel: everyone in it with their fixed look (and LoRA trigger), the
 * panel's actions and scene. With two or more people, each gets a region of the picture with
 * their own prompt so looks do not bleed into each other.
 */
export function renderPanel(cast: ComicCharacter[], panel: ComicPanel): PanelRender {
  const people = panel.characters
    .map((pc) => ({ pc, c: cast.find((c) => c.name === pc.name) }))
    .filter((x): x is { pc: PanelCharacter; c: ComicCharacter } => x.c !== undefined);
  const order: Record<StagePosition, number> = { left: 0, center: 1, right: 2 };
  people.sort((a, b) => order[a.pc.pos] - order[b.pc.pos]);
  const loras = people
    .filter((p) => p.c.lora)
    .map((p) => ({ path: p.c.lora as string, scale: people.length > 1 ? 0.65 : 0.85 }));
  const describe = (p: { pc: PanelCharacter; c: ComicCharacter }) => {
    const { gender, rest } = splitLook(p.c.look);
    return {
      gender,
      text: [p.c.trigger ?? "", ADULT_TAGS[gender], rest, p.pc.action].filter(Boolean).join(", "),
    };
  };
  const described = people.map(describe);
  const count = countTags(described.map((d) => d.gender));
  // The negative tags the user gave each character in the picture; no fixed tags are added here.
  const negative = people
    .map((p) => p.c.negative ?? "")
    .filter(Boolean)
    .join(", ");
  if (people.length === 0) {
    return { prompt: panel.scene || "scenery, no humans", negative, loras };
  }
  if (people.length === 1) {
    return {
      prompt: [count, "solo", described[0]?.text, panel.scene].filter(Boolean).join(", "),
      negative,
      loras,
    };
  }
  const spans = spansFor(people.length);
  // Heights in cm become "who is taller" when the difference shows (10 cm or more).
  const heights = people.map((p) => p.c.heightCm ?? 0);
  const known = heights.filter((h) => h > 0);
  const tallest = known.length >= 2 ? Math.max(...known) : 0;
  const shortest = known.length >= 2 ? Math.min(...known) : 0;
  const differs = tallest - shortest >= 10;
  const heightTag = (h: number) =>
    !differs || h <= 0 ? "" : h === tallest ? "taller" : h === shortest ? "shorter" : "";
  return {
    // The whole-picture prompt names everyone briefly; the regions carry the details.
    prompt: [count, differs ? "height difference" : "", panel.scene].filter(Boolean).join(", "),
    negative,
    regions: described.map((d, i) => ({
      prompt: [d.text, heightTag(heights[i] ?? 0), panel.scene].filter(Boolean).join(", "),
      x0: spans[i]?.[0] ?? 0,
      x1: spans[i]?.[1] ?? 1,
    })),
    loras,
  };
}

/** Where each speaker stands, so the typesetter can put the bubble on their side. */
export function speakerPositions(panel: ComicPanel): Record<string, StagePosition> {
  return Object.fromEntries(panel.characters.map((c) => [c.name, c.pos]));
}

export const FRAME_ASPECT: Record<PanelFrame, "portrait" | "landscape" | "square"> = {
  tall: "portrait",
  wide: "landscape",
  square: "square",
};
