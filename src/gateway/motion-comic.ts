// Motion comic: an adult scene told in short AI video cuts. The script writer only fills in the
// content (character, place, position, lines); the cut structure and every image and motion
// prompt come from a fixed template that is known to work with Wan 2.2 (first/last frame):
//   loop cuts start and end on the same key image, so they repeat without a seam;
//   change cuts go from one key image to another (to the climax pose, to the cum shot).
// Key images: k1 = base (txt2img), k2 = climax pose (img2img 0.6 of k1, same composition),
// k3 = cum (inpaint of k1), f1 = fellatio (txt2img), f2 = cum in mouth (inpaint of f1's mouth).

import { findBlockedTerm, negativeTags } from "./blocked-terms-config.js";
import { clip, firstJsonObject } from "./comic-series.js";
import { IRODORI_EMOJI_GUIDE } from "./irodori-tts.js";
import { MOTION_TALK, type MotionTalk } from "./motion-extras.js";

export const MOTION_MINUTES = [1, 3, 5] as const;
export type MotionMinutes = (typeof MOTION_MINUTES)[number];
export type MotionFinish = "creampie" | "outside";

export const MOTION_PHASES = ["slow", "fast", "climax", "after", "fellatio", "end"] as const;
export type MotionPhase = (typeof MOTION_PHASES)[number];
export type BreathPhase = "slow" | "fast" | "climax";

export type MotionScript = {
  title: string;
  name: string;
  age: number;
  /** Japanese description of her voice, for a voice described in words. */
  voice: string;
  /** English tags: hair, eyes, body, outfit. */
  look: string;
  /** English tags: the room and light. */
  place: string;
  /** English tags: the position of the main scene (seen from above, full body). */
  position: string;
  finish: MotionFinish;
  fellatio: boolean;
  lines: Record<MotionPhase, string[]>;
  breaths: Record<BreathPhase, string>;
  /**
   * 前段シーン（着衣→脱衣→前戯）のステップ。省略時は前段なし。
   */
  approach?: ApproachStep[];
  /** 本番中のセリフ吹き出し位置（デフォルト right） */
  dialogueBubblePos?: "right" | "left";
};

/**
 * 前段シーン（着衣→脱衣→前戯）の各ステップ。
 * 各ステップに English image tags + セリフ行（日本語） + フォントスタイルが入る。
 */
export type ApproachStep = {
  /** English tags for the key image of this step. */
  tags: string;
  /** 短い日本語セリフ or 状況説明（30字以内）。キャラ横に表示する。 */
  line: string;
  /**
   * 表示フォントの雰囲気（Android 側がマッピングする）:
   *  "normal"  → 明朝・普通
   *  "whisper" → 細い・細明朝（ひそひそ・色っぽい）
   *  "shout"   → 太ゴシック（叫び・驚き）
   *  "thought" → 斜体・丸っこい（心の声）
   *  "sfx"     → 効果音フォント（擬音）
   *  "caption" → 状況説明テキスト（枠付き小字）
   */
  fontStyle: "normal" | "whisper" | "shout" | "thought" | "sfx" | "caption";
  /** セリフ吹き出しの位置 */
  bubblePos: "right" | "left" | "top" | "bottom-caption";
};

export type MotionKeyJob = {
  id: "k1" | "k2" | "k3" | "k4" | "f1" | "f2";
  mode: "t2i" | "i2i" | "inpaint";
  prompt: string;
  src?: string;
  strength?: number;
  /** inpaint: an ellipse (0..1 of the picture), or "mouth" = around the face found in src. */
  mask?: [number, number, number, number] | "mouth";
  seed: number;
};

export type MotionCut = {
  name: string;
  start: MotionKeyJob["id"];
  end: MotionKeyJob["id"];
  loop: boolean;
  prompt: string;
  seconds: number;
  phase?: MotionPhase;
  breath?: BreathPhase;
  fade?: "in" | "out" | "both";
  flash?: boolean;
  /** fellatio cuts: the slurping sound effect under the voice */
  sfx?: "fellatio";
  /** A 0.5 s close-up of this area (0..1) cut into the middle, as the reference films do. */
  insert?: [number, number, number, number];
};

// ─────────────────────────────────────────────────────────────────────────────
// 未成年・学生・子どもに関する禁止語句
// 禁止語句・パターンと有効・無効は dist/blocked-terms.json だけで決まる。ここには持たない。
// 年齢を数値で検査することもしない（年齢の扱いも禁止語句で決める）。
// ───────────────────────────────────────────────────────

/** dist/blocked-terms.json の禁止語句が含まれるか。ファイルが無い・読めないときは投げる。 */
export function hasStudentTerms(...texts: string[]): boolean {
  return findBlockedTerm(...texts) !== undefined;
}

export function motionSystemPrompt(minutes: MotionMinutes): string {
  return `あなたは成人向けモーションコミックの脚本家。依頼をもとに、1つの濡れ場（約${minutes}分）の中身をJSONで書く。カット割りと絵の指示はシステムが作るので、あなたは中身だけを書く。
出力はJSONオブジェクト1つだけ（説明や\`\`\`は書かない）:
{"title":"作品の題名（日本語、20字以内）",
 "name":"女性の名前（カタカナかひらがな）",
 "age":25,
 "voice":"声の説明（日本語）。例: 25歳くらいの大人の女性。低めで気だるげな声で、淡々と話す。",
 "look":"見た目の英語タグ（カンマ区切り）: 髪の色と長さ、髪型、目の色、体型（large breasts など）、衣装。例: black hair, long hair, hime cut, red eyes, jitome, large breasts, gothic lolita, black dress, black choker, black thighhighs",
 "place":"場所と光の英語タグ。例: gothic bedroom, on bed, black sheets, dim room, candlelight",
 "position":"本番の体位の英語タグ（上から全身が見える構図）。例: lying on back, missionary, spread legs",
 "finish":"creampie（中出し）か outside（お腹に外出し）",
 "fellatio":true,
 "lines":{"slow":["ゆっくりの場面のセリフ","…"],"fast":["激しい場面"],"climax":["絶頂"],"after":["余韻"],"fellatio":["フェラの前の一言"],"end":["口に出されたあとの一言"]},
 "breaths":{"slow":"ゆっくりの息づかい（短い）","fast":"激しい息づかい","climax":"絶頂の声"}}
- セリフは1つ30字以内。slow は${minutes >= 3 ? 3 : 2}つ、fast は${minutes >= 3 ? 3 : 2}つ、climax・after は1〜2つ、fellatio・end は1つ。
- 性格と口調をセリフに出す。名前・年齢・性格は依頼に従う（指定がなければ考える）。
- fellatio は依頼に「フェラ」「口で」などがあれば true、なければ false（false でも lines.fellatio と lines.end は空の配列でよい）。
- approach は必ず3〜5ステップ書く。①着衣の全身（fully clothed）→ ②服をずらす・脱がす・破く → ③半裸・胸やお尻露出 → ④前戯（fondling/kissing）の流れで。各ステップの tags は英語タグ、line は30字以内の日本語セリフ、fontStyle は「normal」「whisper」「shout」「thought」「sfx」「caption」から選ぶ、bubblePos は「right」か「left」。
- ${IRODORI_EMOJI_GUIDE}
- 😱（絶叫）は使わない（断末魔のような叫びになる）。絶頂も 🥵（喘ぎ）・😮‍💨（吐息）・🌬️（息切れ）・🙏（懇願）で、色っぽく高まる声にする。`;
}

/** 😱 makes Irodori scream like a death cry; a climax should rise, not shriek. */
export function softenMoans(text: string): string {
  return text.replace(/😱/gu, "🥵");
}

const TAG_TEXT = /[^a-zA-Z0-9 ,()'.:\-]/g;

function tags(raw: unknown, max: number): string {
  return clip(
    String(raw ?? "")
      .replace(TAG_TEXT, " ")
      .replace(/\s+/g, " ")
      .replace(/\s*,\s*/g, ", "),
    max,
  ).replace(/(^[, ]+|[, ]+$)/g, "");
}

function lineList(raw: unknown, max: number): string[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((line) => softenMoans(clip(String(line ?? "").replace(/[\r\n]+/g, " "), 60)))
    .filter((line) => line.length > 0)
    .slice(0, max);
}

export function parseMotionScript(reply: string, minLines = 2): MotionScript {
  const data = firstJsonObject(reply);
  const age = Number(data.age);
  if (!Number.isFinite(age)) throw new Error("年齢が不正です");
  const look = tags(data.look, 400);
  const position = tags(data.position, 200);
  if (look.length < 10 || position.length < 5) throw new Error("見た目か体位の指示が短すぎます");
  const lines = Object.fromEntries(
    MOTION_PHASES.map((phase) => [
      phase,
      lineList((data.lines as Record<string, unknown> | undefined)?.[phase], 4),
    ]),
  ) as Record<MotionPhase, string[]>;
  if (lines.slow.length + lines.fast.length + lines.climax.length < minLines)
    throw new Error("セリフが足りません");
  const breathsRaw = (data.breaths ?? {}) as Record<string, unknown>;
  const breath = (key: BreathPhase, fallback: string) =>
    softenMoans(clip(String(breathsRaw[key] ?? ""), 40)) || fallback;
  // approach ステップのパース（省略可）
  const approachRaw: Record<string, unknown>[] = Array.isArray(data.approach)
    ? (data.approach as Record<string, unknown>[])
    : [];
  const approachSteps: ApproachStep[] = approachRaw.map((s) => ({
    tags: clip(String(s["tags"] ?? ""), 150),
    line: clip(String(s["line"] ?? ""), 60),
    fontStyle: (["normal", "whisper", "shout", "thought", "sfx", "caption"] as const).includes(
      s["fontStyle"] as ApproachStep["fontStyle"],
    )
      ? (s["fontStyle"] as ApproachStep["fontStyle"])
      : "normal",
    bubblePos:
      s["bubblePos"] === "left"
        ? "left"
        : s["bubblePos"] === "top"
          ? "top"
          : s["bubblePos"] === "bottom-caption"
            ? "bottom-caption"
            : "right",
  }));
  const script: MotionScript = {
    title: clip(data.title, 30) || "無題",
    name: clip(data.name, 12) || "彼女",
    age: Math.round(age),
    voice: clip(data.voice, 120) || "大人の女性の、やわらかく色っぽい声。",
    look,
    place: tags(data.place, 200) || "bedroom, on bed, dim room",
    position,
    finish: data.finish === "outside" ? "outside" : "creampie",
    fellatio: data.fellatio === true,
    approach: approachSteps,
    dialogueBubblePos: data.dialogueBubblePos === "left" ? "left" : "right",
    lines,
    breaths: {
      slow: breath("slow", "ん…😮‍💨 …ふぅ…"),
      fast: breath("fast", "はぁ…🌬️ …っ、ん…🥵"),
      climax: breath("climax", "あっ、あっ、あっ…🥵"),
    },
  };
  return script;
}

const QUALITY = "masterpiece, best quality, amazing quality, very aesthetic, absurdres";

const MOTION_IMAGE_NEGATIVE_BASE =
  "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, extra legs, extra arms, jpeg artifacts, signature, watermark, username, blurry, text, speech bubble, comic, multiple views, censored, mosaic censoring, bar censor, close-up, upper body, cropped legs";

const MOTION_VIDEO_NEGATIVE_BASE =
  "static, still image, blurry, low quality, distorted, deformed, extra limbs, flicker, watermark, text, camera movement, zoom, scene change";

/** 絵のネガティブ。追加の禁止タグは dist/blocked-terms.json の imageNegative から、使うたびに読む。 */
export function motionImageNegative(): string {
  const extra = negativeTags("imageNegative");
  return extra ? `${MOTION_IMAGE_NEGATIVE_BASE}, ${extra}` : MOTION_IMAGE_NEGATIVE_BASE;
}

/** 動画のネガティブ。追加の禁止タグは dist/blocked-terms.json の videoNegative から、使うたびに読む。 */
export function motionVideoNegative(): string {
  const extra = negativeTags("videoNegative");
  return extra ? `${MOTION_VIDEO_NEGATIVE_BASE}, ${extra}` : MOTION_VIDEO_NEGATIVE_BASE;
}

/**
 * Positions and scenes. Each template fixes the composition of the key images (so the climax
 * pose made with img2img keeps it), the motion of every cut, where the cum is painted and the
 * close-ups cut in. "missionary" is the original flow.
 */
export type MotionTemplate = {
  label: string;
  /** English tags of the position and the camera for k1/k2. */
  position: string;
  camera: string;
  /** Extra climax tags for k2 (with the same composition). */
  climax: string;
  slow: string;
  fast: string;
  build: string;
  climaxMotion: string;
  /** Cum painted on k1 (ellipse, 0..1) for creampie / outside, and its tags. */
  cumMask: {
    creampie: [number, number, number, number];
    outside: [number, number, number, number];
  };
  cumOutsideTags: string;
  cumOutsideMotion: string;
  after: string;
  /** Close-ups cut into the fast and the climax loop. */
  insertFast: [number, number, number, number];
  insertClimax: [number, number, number, number];
  /** What the key images show besides the position (default: vaginal sex). */
  act?: string;
  /** The "creampie" finish of a template where that does not fit (e.g. cum on the breasts). */
  cumInsideTags?: string;
  cumInsideMotion?: string;
};

/** A position template for a vaginal act with pose-neutral motion text (the pose itself is in position and camera). */
function pose(label: string, position: string, camera: string): MotionTemplate {
  return {
    label,
    position,
    camera,
    climax: "arched back, head back, toes curled, trembling, flushed face, sweat",
    slow: "the man and the woman make slow, deep, sensual sex in this position, their bodies moving in rhythm, her breasts sway softly",
    fast: "the man thrusts faster and harder in this position, vigorous sex, her breasts bounce with soft physics, she moans loudly",
    build:
      "the pace quickens, the woman starts to climax, she arches her back, her face flushes and her toes curl",
    climaxMotion:
      "the woman has an intense orgasm, her whole body trembles and convulses, her back arches, she gasps with her mouth open",
    cumMask: { creampie: [0.3, 0.4, 0.7, 1.0], outside: [0.3, 0.3, 0.7, 0.95] },
    cumOutsideTags: "cum on stomach, cum on breasts, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her body, his penis pulses, thick white cum spurts and drips down",
    after:
      "the woman lies exhausted and breathes heavily, thick white cum slowly drips from her, her body still trembling",
    insertFast: [0.3, 0.5, 0.7, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  };
}

/** A position template for an act that is not vaginal sex (oral, hands, feet, thighs, breasts). */
function special(
  label: string,
  position: string,
  camera: string,
  act: string,
  motions: { slow: string; fast: string; build: string; climaxMotion: string },
  cum: { tags: string; motion: string },
  after: string,
): MotionTemplate {
  return {
    label,
    position,
    camera,
    climax: "heavy blush, open mouth, sweat, flushed face",
    ...motions,
    act,
    cumMask: { creampie: [0.25, 0.2, 0.75, 0.85], outside: [0.25, 0.2, 0.75, 0.85] },
    cumInsideTags: cum.tags,
    cumInsideMotion: cum.motion,
    cumOutsideTags: cum.tags,
    cumOutsideMotion: cum.motion,
    after,
    insertFast: [0.25, 0.35, 0.75, 0.9],
    insertClimax: [0.25, 0.3, 0.75, 0.9],
  };
}

/** A solo (self-pleasure) template: no partner, hands and cushions only. */
function solo(label: string, position: string, camera: string): MotionTemplate {
  return special(
    label,
    position,
    camera,
    "masturbation, solo",
    {
      slow: "she slowly touches herself, her hips rolling gently, her breath unsteady, her eyes half closed",
      fast: "she touches herself faster, her hips moving, her breasts shaking, moaning with her mouth open",
      build: "her movements get faster and rougher, her body tenses, her toes curl, she gasps",
      climaxMotion:
        "she cums hard, her whole body trembles, her back arches, squirting, her legs shaking",
    },
    { tags: "squirting, wet, orgasm", motion: "her whole body convulses as she squirts" },
    "she lies limp and breathing heavily, her cheeks flushed, her thighs trembling and wet",
  );
}

export const MOTION_TEMPLATES = {
  missionary: {
    label: "正常位",
    position: "lying on back, missionary, spread legs, on bed",
    camera: "pov, full body, from above, wide shot",
    climax: "arched back, head back, toes curled, grabbing sheets, stomach bulge",
    slow: "a man slowly and deeply thrusts his hips into the woman lying on her back, slow sensual sex, long strokes, her large breasts sway softly with each thrust, her belly rises and falls with deep breathing, her legs twitch, her toes wiggle, her fingers clench near her face, she moans with half-closed eyes",
    fast: "a man thrusts his hips hard and fast into the woman, vigorous pounding sex, her large breasts bounce wildly and jiggle independently with soft physics, her lower belly bulges with each deep thrust, her thighs shake and ripple on impact, her legs kick and her toes curl, her hands clench, her head rocks back and forth, she pants and moans loudly with an open mouth",
    build:
      "the man thrusts faster and deeper, the woman starts to climax, she arches her back, throws her head back and sticks out her tongue, she grabs the sheets with both hands, her belly bulges from the deep penetration, her breasts bounce, her legs tense up and her toes curl tightly",
    climaxMotion:
      "the woman is having an intense orgasm, her whole body convulses and trembles, her back arches and twitches, her belly bulges and pulses with each deep thrust, her breasts shake and jiggle, her legs spasm and her toes curl, she grips the sheets tightly, tongue out, eyes rolled back, the man keeps thrusting deep",
    cumMask: { creampie: [0.33, 0.45, 0.65, 1.05], outside: [0.3, 0.3, 0.7, 0.95] },
    cumOutsideTags: "cum on stomach, cum on breasts, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly, his penis pulses, thick white cum spurts onto her stomach and breasts and drips down",
    after:
      "the woman lies exhausted and breathes heavily, her chest and belly rise and fall, her breasts sway gently, thick white cum slowly drips and oozes and runs down, her thighs twitch weakly, her toes twitch, small aftershocks, the man stays still",
    insertFast: [0.32, 0.55, 0.68, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  cowgirl: {
    label: "騎乗位",
    position:
      "girl on top, cowgirl position, straddling, squatting, hands on another's chest, on bed",
    camera: "pov, from below, full body, man lying",
    climax: "head back, arched back, toes curled, trembling, stomach bulge",
    slow: "the woman slowly rides the man, rolling her hips back and forth, grinding down on him, her large breasts sway, her belly tightens, her thighs flex, her hands press on his chest, she moans softly with half-closed eyes and looks down at the viewer",
    fast: "the woman bounces hard up and down on the man's hips, vigorous riding, her large breasts bounce wildly and jiggle independently with soft physics, her lower belly bulges each time she drops down, her thighs and buttocks ripple on impact, her hair swings, she pants and moans loudly with an open mouth",
    build:
      "the woman rides faster and faster, she starts to climax, she throws her head back, arches her back and sticks out her tongue, her belly bulges, her breasts bounce, her thighs tremble and her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm on top of the man, her whole body convulses, she sinks down and trembles, her belly pulses, her breasts shake, her thighs spasm, tongue out, eyes rolled back, the man thrusts up into her",
    cumMask: { creampie: [0.3, 0.5, 0.7, 1.05], outside: [0.3, 0.3, 0.7, 0.8] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "she lifts her hips and the man ejaculates, thick white cum spurts onto her belly and thighs and drips down",
    after:
      "the woman sits on the man exhausted and breathes heavily, then leans forward, her chest rises and falls, her breasts sway gently, thick white cum slowly drips down, her thighs twitch weakly",
    insertFast: [0.3, 0.5, 0.7, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  doggystyle: {
    label: "後背位",
    position: "doggystyle, all fours, from behind, ass, looking back, on bed",
    camera: "pov, from behind, from above, full body",
    climax: "arched back, head up, looking back, trembling, grabbing sheets",
    slow: "the man slowly thrusts into the woman on all fours from behind, holding her hips, her round ass jiggles softly with each thrust, her back arches, her breasts sway below her, she looks back over her shoulder and moans",
    fast: "the man pounds hard and fast into the woman from behind, her ass ripples and bounces on every impact, her hanging breasts swing and jiggle wildly, her back arches, her head jerks forward with each thrust, she grips the sheets and moans loudly",
    build:
      "the man thrusts faster and deeper from behind, the woman starts to climax, her back arches sharply, she lifts her head and cries out, her legs tremble, she claws the sheets",
    climaxMotion:
      "the woman is having an intense orgasm on all fours, her whole body convulses, her ass and thighs quiver, her back arches and twitches, she buries her face in the sheets, her toes curl, the man keeps thrusting deep",
    cumMask: { creampie: [0.3, 0.35, 0.7, 0.85], outside: [0.2, 0.15, 0.8, 0.75] },
    cumOutsideTags: "cum on ass, cum on back, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her ass and back, thick white cum spurts and drips down her skin",
    after:
      "the woman collapses onto the bed exhausted, breathing heavily, her ass still raised, thick white cum slowly drips out and runs down her thighs, her legs twitch weakly",
    insertFast: [0.3, 0.3, 0.7, 0.75],
    insertClimax: [0.25, 0, 0.75, 0.45],
  },
  standing: {
    label: "立ちバック（壁際・片脚上げ）",
    position: "standing sex, against wall, leg lift, one leg up, standing on one leg",
    camera: "side view, full body, wide shot, indoors",
    climax: "head back, arched back, trembling, hand on wall",
    slow: "the man slowly thrusts into the woman standing against the wall while holding up one of her legs, her large breasts sway, her standing leg trembles, her hand presses on the wall, she moans and bites her lip",
    fast: "the man pounds hard into the woman pinned against the wall, her lifted leg shakes, her large breasts bounce wildly, her hips and ass ripple on impact, her hair swings, she clings to him and moans loudly",
    build:
      "the man thrusts faster, the woman starts to climax, she throws her head back against the wall, her standing leg buckles, her toes curl, she clings to his shoulders",
    climaxMotion:
      "the woman is having an intense orgasm standing against the wall, her whole body convulses, her legs give way and he holds her up, her breasts shake, her toes curl, tongue out",
    cumMask: { creampie: [0.35, 0.45, 0.65, 0.85], outside: [0.3, 0.35, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and thighs, thick white cum spurts and drips down her legs",
    after:
      "the woman leans against the wall exhausted, breathing heavily, her legs trembling, thick white cum slowly drips down her thighs",
    insertFast: [0.3, 0.4, 0.7, 0.8],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  bath: {
    label: "お風呂",
    position:
      "bathroom, sitting on edge of bathtub, spread legs, wet, steam, water drops, wet hair",
    camera: "pov, full body, from above",
    climax: "head back, arched back, toes curled, trembling, wet skin, stomach bulge",
    slow: "in a steamy bathroom, the man slowly thrusts into the woman sitting on the edge of the bathtub, water drips from her wet skin, her large breasts sway, her belly rises and falls, steam drifts, she moans softly",
    fast: "in the steamy bathroom, the man thrusts hard and fast, water splashes, her wet breasts bounce wildly and jiggle, her belly bulges, her thighs shake, her wet hair swings, she moans loudly and her voice echoes",
    build:
      "the man thrusts faster and deeper, the woman starts to climax, she arches her back over the bathtub, throws her head back, water drips, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm on the edge of the bathtub, her wet body convulses and trembles, her belly pulses, her breasts shake, water splashes, tongue out, eyes rolled back",
    cumMask: { creampie: [0.33, 0.45, 0.67, 1.0], outside: [0.3, 0.3, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on breasts, cum on body, wet",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her wet belly and breasts, thick white cum spurts and mixes with the water drops",
    after:
      "the woman leans back on the bathtub exhausted, breathing heavily in the steam, water and thick white cum drip down her wet skin, her thighs twitch weakly",
    insertFast: [0.32, 0.5, 0.68, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  reverse_cowgirl: {
    label: "背面騎乗位",
    position:
      "reverse cowgirl position, girl on top, from behind, ass, looking back, straddling, on bed",
    camera: "pov, from below, man lying, full body",
    climax: "head back, arched back, looking back, trembling, toes curled",
    slow: "the woman slowly rides the man facing away from him, rolling her round ass back and forth, her back arches, she looks back over her shoulder with half-closed eyes and moans",
    fast: "the woman bounces hard on the man facing away, her round ass slaps down and ripples on every drop, her back arches, her hair swings, she moans loudly and looks back",
    build:
      "the woman rides faster, she starts to climax, she arches her back and throws her head back, her thighs tremble, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm riding backward, her whole body convulses, her ass and thighs quiver, her back twitches, the man thrusts up into her",
    cumMask: { creampie: [0.3, 0.45, 0.7, 1.0], outside: [0.2, 0.2, 0.8, 0.8] },
    cumOutsideTags: "cum on ass, cum on back, cum on body",
    cumOutsideMotion:
      "she lifts her hips and the man ejaculates onto her ass and back, thick white cum spurts and drips down",
    after:
      "the woman sits back on the man exhausted, breathing heavily, thick white cum slowly drips down, her thighs twitch weakly",
    insertFast: [0.3, 0.45, 0.7, 0.95],
    insertClimax: [0.25, 0, 0.75, 0.45],
  },
  mating_press: {
    label: "種付けプレス",
    position: "mating press, legs up, legs over shoulders, folded, lying on back, on bed",
    camera: "pov, from above, close, full body",
    climax: "eyes rolled back, tongue out, toes curled, legs trembling, stomach bulge",
    slow: "the man leans over the woman, her legs folded up over his shoulders, he slowly drives deep into her, her breasts are pressed and sway, her toes point up and wiggle, she moans with half-closed eyes",
    fast: "the man pounds straight down into the woman with his full weight, her folded legs bounce over his shoulders, her breasts bounce wildly, her belly bulges with each deep thrust, her toes curl, she cries out",
    build:
      "the man pounds harder and deeper, the woman starts to climax, her eyes roll back, she sticks out her tongue, her legs shake over his shoulders, her toes curl tightly",
    climaxMotion:
      "the woman is having an intense orgasm under the man's weight, her whole body convulses, her raised legs spasm, her belly pulses, tongue out, eyes rolled back, the man keeps driving deep",
    cumMask: { creampie: [0.3, 0.4, 0.7, 0.95], outside: [0.3, 0.3, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and thighs, thick white cum spurts and drips down",
    after:
      "the woman lies folded and exhausted, legs slowly sliding down, breathing heavily, thick white cum oozes out and runs down, her legs twitch",
    insertFast: [0.3, 0.4, 0.7, 0.85],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  spooning: {
    label: "側位",
    position: "spooning, lying on side, from behind, leg lift, on bed",
    camera: "side view, full body, wide shot",
    climax: "head back, arched back, trembling, grabbing sheets, toes curled",
    slow: "the man lies behind the woman on their sides and slowly thrusts into her, his hand on her breast, her lifted leg trembles, her breasts sway, she moans softly and turns her face back toward him",
    fast: "the man thrusts hard into the woman from behind as they lie on their sides, her ass ripples against his hips, her breasts bounce, her lifted leg shakes, she grips the sheets and moans loudly",
    build:
      "the man thrusts faster, the woman starts to climax, she arches her back against him, her lifted leg tenses, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm lying on her side, her whole body trembles, her back arches against him, her legs spasm, she clutches the sheets",
    cumMask: { creampie: [0.35, 0.4, 0.7, 0.85], outside: [0.3, 0.3, 0.75, 0.8] },
    cumOutsideTags: "cum on ass, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her ass and thighs, thick white cum spurts and drips down",
    after:
      "the woman lies on her side exhausted, breathing heavily, his arm around her, thick white cum slowly drips down her thighs",
    insertFast: [0.35, 0.35, 0.75, 0.8],
    insertClimax: [0.25, 0, 0.75, 0.45],
  },
  lotus: {
    label: "対面座位",
    position:
      "sitting, face to face, straddling, lotus position, hugging, arms around neck, on bed",
    camera: "pov, close, upper body and hips",
    climax: "head back, arched back, trembling, clinging, tongue out",
    slow: "the woman sits in the man's lap face to face with her arms around his neck and slowly grinds her hips, her large breasts press and sway against his chest, she kisses him and moans softly",
    fast: "the woman bounces hard in the man's lap face to face, her large breasts bounce wildly, her hips slam down, her hair swings, she clings to his neck and moans loudly into his ear",
    build:
      "the woman moves faster, she starts to climax, she throws her head back, arches her back, her nails dig into his shoulders, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm in the man's lap, her whole body convulses, she clings to him and trembles, her breasts shake, tongue out, eyes rolled back",
    cumMask: { creampie: [0.3, 0.55, 0.7, 1.0], outside: [0.3, 0.4, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on body",
    cumOutsideMotion:
      "she lifts herself and the man ejaculates onto her belly, thick white cum spurts and drips down",
    after:
      "the woman slumps against the man's chest exhausted, breathing heavily, he holds her, thick white cum slowly drips down",
    insertFast: [0.3, 0.5, 0.7, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.45],
  },
  prone: {
    label: "寝バック",
    position: "prone bone, lying on stomach, face down, from behind, ass, on bed",
    camera: "pov, from behind, from above, full body",
    climax: "head up, arched back, trembling, grabbing pillow, toes curled",
    slow: "the man lies over the woman lying face down and slowly thrusts into her from behind, her round ass jiggles under his hips, she hugs the pillow and moans with half-closed eyes",
    fast: "the man pounds hard into the woman lying flat on her stomach, her ass ripples and bounces on every thrust, her body slides on the sheets, she grips the pillow and moans loudly into it",
    build:
      "the man thrusts faster and deeper, the woman starts to climax, she lifts her head and arches her back, her legs kick, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm lying face down, her whole body convulses, her ass and thighs quiver, she bites the pillow, her toes curl, the man keeps thrusting",
    cumMask: { creampie: [0.3, 0.35, 0.7, 0.8], outside: [0.2, 0.2, 0.8, 0.75] },
    cumOutsideTags: "cum on ass, cum on back, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her ass and back, thick white cum spurts and drips down",
    after:
      "the woman lies flat on her stomach exhausted, breathing heavily into the pillow, thick white cum slowly oozes out and runs down, her legs twitch weakly",
    insertFast: [0.3, 0.3, 0.7, 0.75],
    insertClimax: [0.25, 0, 0.75, 0.4],
  },
  paizuri: {
    label: "パイズリ",
    position:
      "paizuri, breasts squeezed together, penis between breasts, kneeling between legs, looking at viewer",
    camera: "pov, from above, upper body",
    climax: "heavy blush, open mouth, tongue out, sweat",
    slow: "the woman slowly slides her large breasts up and down the penis, squeezing them together with both hands, her soft breasts jiggle, she looks up at the viewer and smiles seductively",
    fast: "the woman pumps her large breasts up and down the penis fast, squeezing hard, her breasts bounce and jiggle with soft physics, she licks the tip and pants",
    build:
      "the woman moves her breasts faster and licks the tip, the penis twitches, she looks up with a flushed face and open mouth",
    climaxMotion:
      "the woman keeps squeezing and pumping her breasts around the throbbing penis, her breasts jiggle, she sticks out her tongue under the tip",
    act: "paizuri, penis between breasts",
    cumMask: { creampie: [0.25, 0.2, 0.75, 0.85], outside: [0.25, 0.2, 0.75, 0.85] },
    cumInsideTags: "cum on breasts, cum on face, facial, cum on tongue",
    cumInsideMotion:
      "the penis pulses and shoots thick white cum onto her breasts and face, cum splashes on her tongue and drips down her chest",
    cumOutsideTags: "cum on breasts, cum on face, facial",
    cumOutsideMotion:
      "the penis pulses and shoots thick white cum onto her breasts and face, cum drips down her chest",
    after:
      "the woman squeezes her cum-covered breasts together and licks the cum from her lips, breathing heavily, thick white cum drips down her chest",
    insertFast: [0.25, 0.35, 0.75, 0.9],
    insertClimax: [0.3, 0, 0.7, 0.45],
  },
  suspended_congress: {
    label: "駅弁（櫓立ち）",
    position:
      "suspended congress, carrying, lifted by another, legs wrapped around waist, arms around neck, standing",
    camera: "side view, full body, wide shot",
    climax: "head back, arched back, legs trembling, clinging",
    slow: "the man stands holding the woman up in his arms, her legs wrapped around his waist, he slowly lifts and lowers her onto him, her large breasts press and sway against his chest, she clings to his neck and moans",
    fast: "the man bounces the woman hard up and down in his arms while standing, her body drops onto him with each thrust, her breasts bounce wildly, her hair swings, her legs tighten around him, she cries out",
    build:
      "the man bounces her faster, the woman starts to climax, she throws her head back, her legs lock around him and tremble, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm held up in the man's arms, her whole body convulses and clings to him, her legs spasm around his waist, her breasts shake",
    cumMask: { creampie: [0.35, 0.45, 0.65, 0.8], outside: [0.3, 0.35, 0.7, 0.8] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man lowers her and ejaculates onto her belly and thighs, thick white cum spurts and slides down",
    after:
      "the man holds the exhausted woman against him, she rests her head on his shoulder breathing heavily, thick white cum slowly drips down her thighs",
    insertFast: [0.3, 0.4, 0.7, 0.8],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  full_nelson: {
    label: "フルネルソン（後ろから抱え上げ）",
    position: "full nelson, from behind, lifted by another, legs up, spread legs, arms behind head",
    camera: "front view, full body",
    climax: "head back, eyes rolled back, tongue out, legs trembling, toes curled",
    slow: "the man holds the woman up from behind with her legs spread high and slowly thrusts up into her, her large breasts sway, her spread legs twitch, she moans with half-closed eyes",
    fast: "the man pounds up into the woman held from behind with her legs spread wide, her breasts bounce wildly, her belly bulges, her raised legs shake, she pants and moans loudly",
    build:
      "the man thrusts faster, the woman starts to climax, her head falls back against him, her spread legs tense, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm held up from behind, her whole body convulses, her raised legs spasm, her belly pulses, tongue out",
    cumMask: { creampie: [0.33, 0.5, 0.67, 0.9], outside: [0.3, 0.35, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates upward onto her belly and thighs, thick white cum spurts and drips down",
    after:
      "the woman hangs exhausted in the man's arms with her legs still spread, breathing heavily, thick white cum slowly oozes out and drips",
    insertFast: [0.33, 0.45, 0.67, 0.9],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  piledriver: {
    label: "まんぐり返し（理非知らず）",
    position: "piledriver, legs over head, folded, upside-down, ass up, spread legs, on bed",
    camera: "from above, full body",
    climax: "eyes rolled back, tongue out, toes curled, trembling",
    slow: "the woman lies folded with her hips up and her legs over her head, the man slowly drives down into her from above, her legs sway, she moans looking up between her legs",
    fast: "the man pounds straight down into the woman folded with her hips raised, her ass and thighs ripple on every impact, her breasts are squeezed and bounce, her legs shake over her head, she cries out",
    build:
      "the man pounds harder, the woman starts to climax, her eyes roll back, her legs tremble over her head, her toes curl tightly",
    climaxMotion:
      "the woman is having an intense orgasm folded with her hips up, her whole body convulses, her legs spasm, tongue out, eyes rolled back",
    cumMask: { creampie: [0.3, 0.2, 0.7, 0.65], outside: [0.25, 0.25, 0.75, 0.85] },
    cumOutsideTags: "cum on ass, cum on stomach, cum on breasts, cum on face",
    cumOutsideMotion:
      "the man pulls out and ejaculates down onto her, thick white cum spurts onto her belly, breasts and face",
    after:
      "the woman stays folded and exhausted, breathing heavily, thick white cum slowly wells up and runs down her skin",
    insertFast: [0.3, 0.2, 0.7, 0.65],
    insertClimax: [0.3, 0.55, 0.7, 1.0],
  },
  amazon: {
    label: "アマゾン（女性上位で押さえ込む）",
    position: "amazon position, girl on top, femdom, legs up, holding legs, on bed",
    camera: "full body, side view",
    climax: "head back, arched back, trembling, smirk",
    slow: "the woman sits on top of the man whose legs are raised and slowly grinds down onto him, she looks down at him with a teasing smile, her large breasts sway",
    fast: "the woman pounds down hard onto the man pinned under her, her large breasts bounce wildly, her hips slam down, her hair swings, she laughs and moans",
    build:
      "the woman moves faster, she starts to climax, she throws her head back and arches her back, her thighs tremble",
    climaxMotion:
      "the woman is having an intense orgasm on top of the man, her whole body convulses, her breasts shake, she grips his legs",
    cumMask: { creampie: [0.35, 0.4, 0.7, 0.85], outside: [0.3, 0.3, 0.7, 0.8] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "she lifts her hips and the man ejaculates, thick white cum spurts onto her belly and thighs",
    after:
      "the woman sits back exhausted on the man, breathing heavily and smiling, thick white cum slowly drips down",
    insertFast: [0.35, 0.4, 0.7, 0.85],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  bent_over_desk: {
    label: "机に手をついて立ちバック",
    position: "bent over desk, sex from behind, standing, hands on desk, ass, looking back, office",
    camera: "side view, full body, wide shot",
    climax: "head up, arched back, trembling, legs shaking",
    slow: "in an office, the man slowly thrusts into the woman bent over the desk from behind, her ass jiggles softly, her breasts sway over the desk, she looks back and bites her lip",
    fast: "the man pounds hard into the woman bent over the desk, the desk shakes, her ass ripples on every impact, her breasts swing, her legs tremble, she covers her mouth to muffle her moans",
    build:
      "the man thrusts faster, the woman starts to climax, she arches her back over the desk, her legs give way, her toes curl in her shoes",
    climaxMotion:
      "the woman is having an intense orgasm bent over the desk, her whole body convulses, she grips the edge of the desk, her legs shake",
    cumMask: { creampie: [0.3, 0.35, 0.65, 0.75], outside: [0.25, 0.25, 0.7, 0.75] },
    cumOutsideTags: "cum on ass, cum on back, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her ass and lower back, thick white cum spurts and slides down",
    after:
      "the woman lies over the desk exhausted, breathing heavily, thick white cum slowly drips down her thighs",
    insertFast: [0.3, 0.3, 0.65, 0.75],
    insertClimax: [0.3, 0, 0.75, 0.45],
  },
  knees_to_chest: {
    label: "屈曲位（笠舟本手）",
    position:
      "missionary, knees to chest, legs up, holding own legs, spread legs, lying on back, on bed",
    camera: "pov, from above, full body",
    climax: "eyes rolled back, tongue out, toes curled, stomach bulge",
    slow: "the woman lies on her back holding her knees to her chest, the man slowly thrusts deep into her, her breasts sway between her legs, her toes wiggle, she moans with half-closed eyes",
    fast: "the man pounds hard into the woman holding her knees to her chest, her breasts bounce wildly, her belly bulges, her raised legs shake, she pants and moans loudly",
    build:
      "the man thrusts deeper, the woman starts to climax, she pulls her knees tighter, her eyes roll back, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm holding her legs up, her whole body convulses, her belly pulses, her legs spasm",
    cumMask: { creampie: [0.33, 0.4, 0.67, 0.9], outside: [0.3, 0.3, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on breasts, cum on thighs",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and breasts, thick white cum spurts and drips down",
    after:
      "the woman lets her legs fall open exhausted, breathing heavily, thick white cum slowly oozes out and runs down",
    insertFast: [0.33, 0.4, 0.67, 0.9],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  matsubakuzushi: {
    label: "松葉崩し",
    position:
      "lying on side, leg lift, leg on shoulder, legs crossed with another, sideways, on bed",
    camera: "side view, full body, wide shot",
    climax: "head back, arched back, trembling, grabbing sheets",
    slow: "the woman lies on her side with one leg raised on the man's shoulder, their legs interlocked like scissors, he slowly thrusts deep, her breasts sway, she moans softly",
    fast: "the man thrusts hard with their legs interlocked, the woman's raised leg shakes on his shoulder, her breasts bounce, she grips the sheets and moans loudly",
    build:
      "the man thrusts deeper, the woman starts to climax, her raised leg tenses, she arches her back, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm lying on her side, her whole body trembles, her raised leg spasms, she clutches the sheets",
    cumMask: { creampie: [0.35, 0.4, 0.7, 0.85], outside: [0.3, 0.3, 0.75, 0.85] },
    cumOutsideTags: "cum on thighs, cum on stomach, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her thighs and belly, thick white cum spurts and drips down",
    after:
      "the woman lies on her side exhausted, breathing heavily, thick white cum slowly drips down her thigh",
    insertFast: [0.35, 0.35, 0.75, 0.85],
    insertClimax: [0.25, 0, 0.75, 0.45],
  },
  squatting_cowgirl: {
    label: "しゃがみ騎乗位（茶臼）",
    position:
      "squatting cowgirl position, squatting, spread legs, girl on top, hands on knees, on bed",
    camera: "pov, from below, full body, man lying",
    climax: "head back, arched back, trembling, toes curled, stomach bulge",
    slow: "the woman squats over the man with her legs spread wide and slowly lowers herself up and down, her large breasts sway, her thighs flex, she looks down at the viewer with half-closed eyes",
    fast: "the woman bounces hard in a squat on the man, her spread thighs and ass slap down, her large breasts bounce wildly, her belly bulges, she moans loudly",
    build:
      "the woman bounces faster, she starts to climax, her thighs tremble, she throws her head back, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm squatting on the man, her spread legs shake, her whole body convulses, she sinks down onto him",
    cumMask: { creampie: [0.3, 0.55, 0.7, 1.05], outside: [0.3, 0.3, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "she rises and the man ejaculates, thick white cum spurts onto her belly and thighs",
    after:
      "the woman sinks down exhausted, breathing heavily, thick white cum slowly drips out between her spread thighs",
    insertFast: [0.3, 0.5, 0.7, 1.0],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  standing_missionary: {
    label: "立ち鼎（向かい合って立つ）",
    position: "standing missionary, standing sex, face to face, leg lift, against wall, hugging",
    camera: "side view, full body",
    climax: "head back, trembling, clinging, toes curled",
    slow: "the man and woman stand face to face, her leg lifted, he slowly thrusts into her against the wall, her breasts press against him, she kisses him and moans",
    fast: "the man thrusts hard into the woman standing face to face, her lifted leg shakes, her breasts bounce, she clings to his shoulders and moans loudly",
    build:
      "the man thrusts faster, the woman starts to climax, she throws her head back against the wall, her standing leg buckles",
    climaxMotion:
      "the woman is having an intense orgasm standing face to face, her whole body convulses, she clings to him, her legs give way",
    cumMask: { creampie: [0.35, 0.45, 0.65, 0.8], outside: [0.3, 0.35, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on thighs, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and thighs, thick white cum spurts and drips down her legs",
    after:
      "the woman leans against the wall exhausted in his arms, breathing heavily, thick white cum slowly drips down her thighs",
    insertFast: [0.35, 0.4, 0.65, 0.8],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  desk_missionary: {
    label: "机の上で正常位",
    position: "lying on desk, on table, missionary, spread legs, legs up, office, papers",
    camera: "pov, from above, full body",
    climax: "arched back, head back, toes curled, stomach bulge",
    slow: "in an office, the woman lies on the desk with her legs spread and the man slowly thrusts into her, papers scatter, her breasts sway, she moans with half-closed eyes",
    fast: "the man pounds hard into the woman on the desk, the desk rattles, her breasts bounce wildly, her belly bulges, her legs kick, she covers her mouth and moans",
    build:
      "the man thrusts faster, the woman starts to climax, she arches her back on the desk, her toes curl",
    climaxMotion:
      "the woman is having an intense orgasm on the desk, her whole body convulses, she grips the edge of the desk, her belly pulses",
    cumMask: { creampie: [0.33, 0.45, 0.67, 1.0], outside: [0.3, 0.3, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on breasts, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and breasts, thick white cum spurts and drips down",
    after:
      "the woman lies on the desk exhausted, breathing heavily, thick white cum slowly oozes out and drips onto the desk",
    insertFast: [0.33, 0.45, 0.67, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  sixtynine: {
    label: "シックスナイン",
    position: "69, fellatio, cunnilingus, girl on top, lying on another, ass, on bed",
    camera: "full body, from side",
    climax: "heavy blush, trembling, open mouth, tongue out",
    slow: "the woman lies on top of the man in a 69, slowly sucking his penis while he licks her, her head bobs slowly, her hips twitch, her breasts press and sway",
    fast: "the woman sucks the penis fast in a 69, her head bobs quickly, her hips squirm and shake as he licks her, she moans around the penis",
    build:
      "the woman sucks harder, she starts to climax from his tongue, her hips tremble, she lets go and moans",
    climaxMotion:
      "the woman trembles in climax on top of the man, her hips shake, she keeps sucking the throbbing penis",
    cumMask: { creampie: [0.0, 0.0, 0.5, 0.6], outside: [0.0, 0.0, 0.55, 0.65] },
    cumOutsideTags: "cum on face, cum in mouth, facial",
    cumOutsideMotion:
      "the penis pulses and shoots thick white cum onto her face and into her mouth",
    after:
      "the woman lies on the man exhausted, breathing heavily, licking the cum from her lips, cum drips down her chin",
    insertFast: [0.0, 0.0, 0.5, 0.6],
    insertClimax: [0.4, 0.2, 0.9, 0.8],
    act: "69, fellatio, cunnilingus",
    cumInsideTags: "cum in mouth, cum on face, facial, cum drip",
    cumInsideMotion:
      "the penis pulses and shoots thick white cum into her mouth and onto her face, cum spills from her lips",
  },
  // ─────────────────── 行為系テンプレート ───────────────────
  blowjob: {
    label: "フェラチオ",
    position: "kneeling, blowjob, fellatio, penis in mouth, looking up, on knees, indoors",
    camera: "pov, from above, close shot",
    climax: "heavy blush, eyes looking up, drool, open mouth, deep throat",
    slow: "the woman kneels and slowly sucks the penis, looking up at the viewer, her tongue swirls around the tip, she strokes the shaft with one hand, her breasts sway, she hums softly",
    fast: "the woman sucks the penis fast and deep, her head bobs quickly, saliva drips down her chin, she moans around the penis, her breasts bounce, her tongue works feverishly",
    build:
      "the woman takes it deeper into her throat, her eyes water, saliva drips, she sucks harder as the penis throbs",
    climaxMotion:
      "the woman strokes and sucks fast as the penis reaches climax, her tongue works the tip, her breasts heave with her breathing",
    cumMask: { creampie: [0.0, 0.0, 0.55, 0.6], outside: [0.0, 0.0, 0.6, 0.65] },
    cumOutsideTags: "facial, cum on face, cum on tongue, cum drip",
    cumOutsideMotion:
      "the penis pulses and shoots thick white cum onto her face, her tongue, and into her open mouth, cum drips down her chin",
    after:
      "the woman kneels exhausted, breathing heavily, cum drips down her lips and chin, she licks it slowly",
    insertFast: [0.0, 0.0, 0.55, 0.6],
    insertClimax: [0.3, 0.2, 0.8, 0.75],
    act: "blowjob, fellatio",
    cumInsideTags: "cum in mouth, mouthful, cum drip",
    cumInsideMotion:
      "the penis pulses and shoots thick white cum into her mouth, she swallows, cum spills from her lips",
  },
  anal: {
    label: "アナル",
    position: "anal sex, from behind, doggystyle, on bed, ass, anal penetration",
    camera: "pov, from behind, from above, full body",
    climax: "arched back, head up, looking back, trembling, grabbing sheets",
    slow: "the man slowly penetrates the woman's ass from behind, holding her hips, her round ass jiggles softly with each slow thrust, her back arches, she moans and trembles slightly at the tight sensation",
    fast: "the man pounds the woman's ass hard from behind, her ass ripples and bounces on every impact, her back arches, her head jerks forward, she grips the sheets and moans loudly",
    build:
      "the man thrusts faster and deeper, the woman starts to climax, her back arches sharply, she lifts her head and cries out, her legs tremble",
    climaxMotion:
      "the woman is having an intense orgasm from anal sex, her whole body convulses, her ass and thighs quiver, her back twitches, the man keeps thrusting deep",
    cumMask: { creampie: [0.25, 0.3, 0.75, 0.8], outside: [0.2, 0.15, 0.8, 0.75] },
    cumOutsideTags: "cum on ass, cum on back",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her ass and back, thick white cum spurts and drips down her skin",
    after:
      "the woman lies exhausted on the bed, breathing heavily, cum slowly drips from her ass and runs down her thighs",
    insertFast: [0.3, 0.3, 0.7, 0.75],
    insertClimax: [0.25, 0, 0.75, 0.45],
  },
  rape_scene: {
    label: "レイプ・強制",
    position:
      "struggling, forced sex, held down, pinned, missionary, on bed or floor, distressed expression",
    camera: "full body, close face, dynamic angle",
    climax: "tears, desperate expression, trembling, body betrayal orgasm, ahegao",
    slow: "a man pins the woman down and forces himself on her, she struggles and cries, tears run down her face, she tries to push him away, her body trembles",
    fast: "the man pounds hard into the woman who is pinned down, she cries out with each thrust, her body shakes, tears stream down her face, her hands push against him weakly",
    build:
      "despite her tears and resistance her body begins to betray her, her moans mix with sobs, her hips start to move involuntarily, she is overwhelmed with shame",
    climaxMotion:
      "the woman is forced to climax against her will, her whole body convulses, her expression is a mix of tears and involuntary pleasure, tongue out, eyes rolling",
    cumMask: { creampie: [0.3, 0.45, 0.7, 1.0], outside: [0.3, 0.3, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on body, cum on thighs",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly and thighs, cum drips down as she lies trembling",
    after:
      "the woman lies still and trembling after the violation, breathing in ragged gasps, tears on her cheeks, cum slowly drips from her body",
    insertFast: [0.3, 0.4, 0.7, 0.9],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  sleep_sex: {
    label: "睡眠姦",
    position:
      "sleeping, lying on back, man on top, missionary, nighttime, bedroom, pajamas or naked",
    camera: "full body, pov, from above",
    climax: "half-closed eyes, sleepy moans, waking up mid-climax, dazed expression",
    slow: "a woman lies sleeping, a man carefully undresses her and slowly penetrates her, she stirs slightly in her sleep, soft unconscious moans escape her lips, her brow furrows gently",
    fast: "the man thrusts faster, the sleeping woman's body moves with each thrust, her breasts bounce, soft moans grow louder, she begins to half-wake with a confused and dazed expression",
    build:
      "the woman is on the edge of waking and climax simultaneously, her eyes flutter half-open, she moans loudly in her dazed state, her hips move involuntarily",
    climaxMotion:
      "the woman climaxes while half-asleep, her body convulses, her eyes flutter open in confusion, she gasps and moans, overwhelmed by the sensation she did not consent to",
    cumMask: { creampie: [0.33, 0.45, 0.67, 1.0], outside: [0.3, 0.3, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her sleeping belly, cum drips down as she lies still",
    after:
      "the woman lies sleeping or barely conscious, cum on her body, soft sleeping breaths, unaware of what happened",
    insertFast: [0.32, 0.45, 0.68, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  train_molest: {
    label: "痴漢（電車）",
    position:
      "standing in crowded train, groped from behind, clothed, train interior, rush hour, skirt",
    camera: "close shot, pov, from behind",
    climax: "biting lip to suppress moans, knees buckling, drool, flushed face",
    slow: "in a crowded rush hour train, a woman in a skirt feels a hand slide under her skirt from behind, she tries to stay still and not make a sound, her face flushes red, she bites her lip",
    fast: "fingers move faster under her skirt, she grips the handrail tight, her knees tremble, she bites her lip hard to suppress her moans as the train rocks",
    build:
      "her whole body is trembling, she can barely stand, she bites her sleeve to muffle her moans as she approaches climax",
    climaxMotion:
      "the woman silently climaxes in the crowded train, her knees buckle, she grips the rail, her face is deep red and she bites down hard to keep silent",
    cumMask: { creampie: [0.35, 0.6, 0.65, 1.0], outside: [0.3, 0.5, 0.7, 0.9] },
    cumOutsideTags: "cum on thighs, cum on skirt",
    cumOutsideMotion: "cum drips down her thighs under her skirt in the crowded train car",
    after:
      "the woman stands in the train, legs trembling and wet, face still flushed red, gripping the handrail as the train continues",
    insertFast: [0.35, 0.6, 0.65, 0.95],
    insertClimax: [0.35, 0.4, 0.65, 0.8],
    act: "groping, fingering, clothed, train",
  },
  sugar_daddy_scene: {
    label: "パパ活",
    position:
      "hotel room, luxury setting, consensual sex, man and young woman, bed, nice clothes on floor",
    camera: "full body, medium shot, wide shot, romantic lighting",
    climax: "head back, arched back, moaning, toes curled, satisfied expression",
    slow: "in a luxury hotel room, the young woman slowly makes love with the older man, she moves her hips gently, her expensive clothes lie on the floor, she moans softly and looks at him with knowing eyes",
    fast: "the young woman rides the man energetically in the hotel bed, her breasts bounce, her hips slam down, she moans loudly and digs her nails into his chest",
    build:
      "she rides faster, approaching climax, she throws her head back and arches her spine, her breasts bounce freely, she moans his name",
    climaxMotion:
      "the young woman climaxes on top of the man, her body convulses with pleasure, she clings to him and trembles, moaning loudly",
    cumMask: { creampie: [0.3, 0.5, 0.7, 1.0], outside: [0.3, 0.3, 0.7, 0.85] },
    cumOutsideTags: "cum on stomach, cum on body",
    cumOutsideMotion: "she lifts off and the man ejaculates onto her belly, thick white cum spurts",
    after:
      "the young woman lies satisfied in the luxury hotel bed, breathing peacefully, cum on her skin, she smiles",
    insertFast: [0.3, 0.5, 0.7, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  orgy_scene: {
    label: "乱交・輪姦",
    position:
      "multiple men, one woman, group sex, gangbang, surrounded, multiple penetration, bedroom or studio",
    camera: "full body, wide shot, dynamic angles, multiple subjects",
    climax: "ahegao, eyes rolled back, tongue out, multiple orgasm, overwhelmed, trembling",
    slow: "the woman is surrounded by multiple men who take turns with her, she sucks one while another thrusts from behind, a third gropes her breasts, she is overwhelmed but compliant",
    fast: "the woman is taken hard by multiple men simultaneously, she is penetrated from both ends, her breasts bounce, moans are muffled by the penis in her mouth, she is sandwiched between bodies",
    build:
      "the woman reaches the edge of climax from the relentless stimulation from multiple men, her body shakes, her mind is overwhelmed",
    climaxMotion:
      "the woman is in the midst of continuous orgasms from the group, her whole body convulses, her eyes roll back, tongue out, she is completely overwhelmed",
    cumMask: { creampie: [0.2, 0.3, 0.8, 1.0], outside: [0.0, 0.0, 1.0, 1.0] },
    cumOutsideTags: "cum on face, cum on body, cum on breasts, facial, multiple cumshots",
    cumOutsideMotion:
      "multiple men ejaculate onto and into the woman, thick white cum covers her face, breasts, belly, and drips everywhere",
    after:
      "the woman lies covered in cum from multiple men, breathing heavily, her body used and trembling, cum drips from her everywhere",
    insertFast: [0.2, 0.2, 0.8, 0.95],
    insertClimax: [0.2, 0, 0.8, 0.5],
  },
  ntr_scene: {
    label: "寝取り・NTR",
    position:
      "another man's partner, cuckold scenario, passionate sex with another man, emotional infidelity, bedroom, explicit",
    camera: "full body, close face, pov, dramatic",
    climax: "pleasured expression, passionate moaning, orgasm face, ahegao",
    slow: "a woman is in bed with a man who is not her partner, she kisses him passionately and lets him undress her, moaning as he touches her body, she whispers that it feels better than her partner",
    fast: "the woman is being taken passionately by the other man, she wraps her legs around him, her breasts bounce as he thrusts hard, she moans loudly and calls out his name",
    build:
      "she approaches climax with the other man, she clutches him tight and moans that she is going to cum, her legs tremble around him",
    climaxMotion:
      "the woman has the most intense orgasm of her life with the other man, her whole body convulses, she clings to him and cries out as he fills her deeply",
    cumMask: { creampie: [0.3, 0.45, 0.7, 1.0], outside: [0.3, 0.3, 0.7, 0.9] },
    cumOutsideTags: "cum on stomach, cum on body",
    cumOutsideMotion:
      "the man pulls out and ejaculates onto her belly, cum drips down as she lies in pleasure",
    after:
      "the woman lies in bed exhausted and satisfied, cum inside her and on her body, a contented smile on her lips",
    insertFast: [0.3, 0.45, 0.7, 0.95],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  outdoor_sex: {
    label: "露出・野外",
    position:
      "outdoors, public place, park or rooftop or alley, partially clothed, exhibitionism, risky sex",
    camera: "wide shot, full body, environment visible, dynamic",
    climax: "biting lip, suppressed moans, thrill expression, sweaty, toes curled",
    slow: "the couple has sex outdoors in a semi-public location, the woman leans against a tree or wall, clothed sex, she bites her lip to stay quiet, the thrill of being discovered makes her flush",
    fast: "they thrust hard and fast outdoors, the woman moans into his shoulder to muffle the sound, her skirt rides up, the risk of discovery makes every sensation more intense",
    build:
      "the woman approaches climax in the outdoor location, she grips his arm, her knees shake, she tries desperately to stay quiet as her orgasm builds",
    climaxMotion:
      "the woman climaxes outdoors, her whole body trembles, she buries her face in his shoulder to muffle her cries, her legs shake and her toes curl",
    cumMask: { creampie: [0.33, 0.5, 0.67, 0.95], outside: [0.3, 0.35, 0.7, 0.85] },
    cumOutsideTags: "cum on thighs, cum on skirt, outdoor",
    cumOutsideMotion:
      "the man ejaculates onto her thighs outdoors, cum drips down as they quickly readjust their clothing",
    after:
      "the woman stands in the outdoor location breathing heavily, cum dripping down her legs, trying to look composed while trembling",
    insertFast: [0.33, 0.45, 0.67, 0.9],
    insertClimax: [0.3, 0, 0.7, 0.4],
  },
  // ─ 追加の体位・特殊プレイ（四十八手や、よくある変わった体位を参考に追加）─
  prone_bone: pose(
    "寝バック",
    "prone bone, lying on stomach, man on top from behind, face pressed to pillow, hips slightly raised, bed",
    "pov, from side, full body",
  ),
  backward_sitting: pose(
    "背面座位",
    "reverse seated position, woman sitting on man's lap facing away, man behind, hands holding her hips",
    "from behind, full body",
  ),
  wheelbarrow: pose(
    "手押し車",
    "wheelbarrow position, woman on hands with legs held up by the man standing behind, held by the legs",
    "from side, full body",
  ),
  scissors: pose(
    "松葉（貝合わせ）",
    "scissors position, side-lying, legs crossed, man between her legs",
    "from side, full body",
  ),
  pretzel: pose(
    "プレッツェル",
    "pretzel position, side-lying, one leg over his hip, man kneeling behind",
    "from side, full body",
  ),
  naruto: pose(
    "鳴門（側位・片脚を肩に）",
    "side position, one leg lifted over the man's shoulder, side-lying, on bed",
    "from side, full body",
  ),
  chidori: pose(
    "千鳥（側位・背後から）",
    "side-lying, man behind, one leg lifted, held by the thigh, on bed",
    "from behind, full body",
  ),
  cowgirl_leaning: pose(
    "前傾騎乗位（抱きつき）",
    "leaning forward cowgirl, woman on top leaning down, arms around his neck, breasts pressed to his chest, kissing",
    "from side, full body",
  ),
  bridge_missionary: pose(
    "ブリッジ正常位",
    "bridge position, woman arching her back in a bridge, man kneeling between her legs holding her hips",
    "from side, full body",
  ),
  standing_carry_wall: pose(
    "壁立ち抱きかかえ",
    "standing against wall, carried, legs wrapped around his waist, back pressed to the wall",
    "from side, full body",
  ),
  car_sex: pose(
    "車の中",
    "sex in a car, back seat, woman on top straddling, car interior, steamed windows",
    "pov, full body",
  ),
  shower_sex: pose(
    "シャワー",
    "sex in a shower, standing, wet bodies, steam, water running, from behind against the tiles",
    "from side, full body",
  ),
  mirror_sex: pose(
    "鏡の前",
    "sex in front of a mirror, from behind, standing, reflection visible in the mirror",
    "from side, mirror reflection",
  ),
  kitchen_counter: pose(
    "キッチンカウンター",
    "sitting on kitchen counter, legs wrapped around his waist, man standing, kitchen",
    "from side, full body",
  ),
  bondage_missionary: pose(
    "拘束正常位",
    "missionary, wrists tied above her head with rope, legs spread, restrained on bed",
    "pov, full body, from above",
  ),
  threesome_mmf: pose(
    "3P（男2人・女1人）",
    "threesome, one woman and two men, she is between the two men, double penetration",
    "from side, full body",
  ),
  threesome_ffm: pose(
    "3P（女2人・男1人）",
    "threesome, two women and one man, one woman riding, the other kissing him, on bed",
    "from above, full body",
  ),
  deepthroat: special(
    "ディープスロート",
    "deepthroat, kneeling, penis deep in throat, saliva dripping, tears in eyes",
    "pov, from above, upper body",
    "deepthroat, penis deep in throat",
    {
      slow: "the woman slowly takes the penis deep into her throat, saliva stretching, her eyes watering",
      fast: "the woman takes the penis deep and fast, gagging softly, saliva dripping down her chin",
      build:
        "the woman deepthroats harder, her nose against his body, tears running, the penis throbbing",
      climaxMotion:
        "the woman holds the penis deep in her throat as it pulses, her eyes rolling up",
    },
    {
      tags: "cum in mouth, cum dripping from mouth, cum on face",
      motion: "the penis pulses deep in her throat and cum spills from her lips and nose",
    },
    "the woman coughs softly and wipes the saliva and cum from her chin, panting",
  ),
  cunnilingus: special(
    "クンニ",
    "cunnilingus, man between her legs, she lies back with legs spread over his shoulders, his face between her thighs",
    "from above, full body",
    "cunnilingus, oral sex on her",
    {
      slow: "the man slowly licks her, his tongue moving gently, her thighs tremble and her hands grip the sheets",
      fast: "the man licks faster and sucks, her hips rise, she moans loudly and grips his hair",
      build: "her breathing quickens, her thighs squeeze his head, her toes curl",
      climaxMotion:
        "the woman cums against his mouth, her body convulses and her back arches, squirting",
    },
    {
      tags: "squirting, wet, orgasm",
      motion: "her body convulses as she squirts against his mouth",
    },
    "the woman lies limp and panting, her thighs trembling, her cheeks flushed",
  ),
  facesitting: special(
    "顔面騎乗",
    "facesitting, woman straddling the man's face, man lying on his back, her hands on the headboard",
    "from side, full body",
    "facesitting, oral sex on her",
    {
      slow: "the woman slowly grinds on his face, her hips rolling, her eyes closed, her breasts swaying",
      fast: "the woman grinds faster on his face, gripping the headboard, moaning loudly",
      build: "the woman grinds harder and trembles, her thighs tightening around his head",
      climaxMotion: "the woman cums on his face, shaking all over, her head thrown back",
    },
    { tags: "squirting, wet, orgasm", motion: "the woman squirts as she shudders on his face" },
    "the woman slides off, shaking and panting, her face deeply flushed",
  ),
  handjob: special(
    "手コキ",
    "handjob, kneeling beside him, stroking the penis with her hand, looking at viewer",
    "pov, from above, upper body",
    "handjob, stroking penis",
    {
      slow: "the woman slowly strokes the penis with her hand, twisting her wrist, smiling seductively",
      fast: "the woman strokes faster with both hands, her breasts bouncing, saliva dripping on the penis",
      build: "the woman strokes faster and licks the tip, the penis twitches, she looks up flushed",
      climaxMotion:
        "the woman keeps stroking the throbbing penis, her face close to it, her tongue out",
    },
    {
      tags: "cum on hand, cum on face, cum on breasts",
      motion: "the penis pulses and shoots thick white cum over her hand and face",
    },
    "the woman looks at the cum on her hand and licks it, smiling, breathing heavily",
  ),
  footjob: special(
    "足コキ",
    "footjob, woman sitting, bare feet on the penis, man lying back, toes curling",
    "from side, full body",
    "footjob, penis between feet",
    {
      slow: "the woman slowly rubs the penis with the soles of her feet, her toes curling, she smirks",
      fast: "the woman rubs faster with both feet, her legs moving rhythmically",
      build: "the woman presses and rubs faster, the penis twitches, she bites her lip",
      climaxMotion: "the woman keeps rubbing the throbbing penis with her feet, her toes spreading",
    },
    {
      tags: "cum on feet, cum on legs, cum on stomach",
      motion: "the penis pulses and shoots thick white cum over her feet and legs",
    },
    "the woman wiggles her cum-covered toes and smiles, breathing heavily",
  ),
  thighjob: special(
    "素股",
    "thigh sex, penis between her thighs, lying on her side, legs pressed together",
    "from side, lower body",
    "thigh sex, penis between thighs",
    {
      slow: "the man slowly slides his penis between her tightly closed thighs, her thighs glistening with lotion",
      fast: "the man thrusts faster between her thighs, her buttocks jiggling, she moans",
      build: "the thrusts speed up, her thighs squeeze tighter, she looks back flushed",
      climaxMotion: "the man thrusts hard between her thighs, the penis throbbing against her",
    },
    {
      tags: "cum on thighs, cum on buttocks, cum on stomach",
      motion: "the penis pulses and shoots thick white cum over her thighs and buttocks",
    },
    "the woman lies on her side, panting, cum dripping down her thighs",
  ),
  titfuck_lying: special(
    "寝パイズリ",
    "lying paizuri, man lying on his back, woman kneeling over his chest pressing her breasts around the penis",
    "pov, from above, upper body",
    "paizuri, penis between breasts",
    {
      slow: "the woman slowly slides her breasts along the penis, squeezing them with both hands, looking down at him",
      fast: "the woman pumps her breasts fast, her breasts bouncing and jiggling with soft physics",
      build: "the woman squeezes harder and licks the tip, the penis twitches",
      climaxMotion: "the woman keeps squeezing the throbbing penis between her breasts, tongue out",
    },
    {
      tags: "cum on breasts, cum on face, facial",
      motion: "the penis pulses and shoots thick white cum over her breasts and face",
    },
    "the woman squeezes her cum-covered breasts and licks her lips, panting",
  ),
  double_blowjob: special(
    "ダブルフェラ",
    "double blowjob, two women kneeling side by side, licking the same penis, looking up",
    "pov, from above, upper body",
    "double blowjob, two women licking one penis",
    {
      slow: "two women slowly lick the penis from both sides, their tongues meeting at the tip",
      fast: "two women take turns sucking and licking quickly, saliva dripping between them",
      build: "two women lick and suck faster, the penis twitches, they look up flushed",
      climaxMotion: "two women keep licking the throbbing penis with their faces close together",
    },
    {
      tags: "cum on face, facial, cum on tongue, two women",
      motion: "the penis pulses and shoots thick white cum over both women's faces",
    },
    "two women lick the cum from each other's faces, laughing and panting",
  ),
  // ─ 追加の体位（体位の一覧記事・海外の呼び名・四十八手より）─
  extended_missionary: pose(
    "伸長位（しがらみ）",
    "extended missionary, lying on back with legs together, man lying on top, bodies pressed together, arms around each other",
    "pov, from side, full body",
  ),
  daishuki_hold: pose(
    "だいしゅきホールド",
    "legs wrapped around his waist, arms around his neck, missionary, tight embrace, holding on tightly",
    "pov, from above, full body",
  ),
  cat_position: pose(
    "CAT体位",
    "coital alignment technique, man on top shifted forward, close grinding, chest to chest",
    "from side, full body",
  ),
  stake_driver: pose(
    "杭打ち騎乗位",
    "squatting cowgirl, bouncing hard up and down, hands on his knees, legs spread wide",
    "from below, pov, full body",
  ),
  spider_cowgirl: pose(
    "スパイダー騎乗位",
    "spider position, cowgirl leaning back with hands behind her, legs spread wide, arched back",
    "from front, full body",
  ),
  lying_cowgirl: pose(
    "密着騎乗位",
    "lying cowgirl, woman lying on top of the man chest to chest, arms around him",
    "from side, full body",
  ),
  rolls_royce: pose(
    "ロールスロイス体位",
    "doggystyle, man pulling her arms back like reins, arched back, from behind",
    "from behind, full body",
  ),
  honkoma: pose(
    "本駒駆け",
    "seated position, woman sitting on his lap sideways, legs over his thigh, his arm around her back",
    "from side, full body",
  ),
  wine_opener: pose(
    "ワインオープナー",
    "legs raised in a V shape, woman on her back, man kneeling holding her ankles",
    "pov, from above, full body",
  ),
  face_off: pose(
    "フェイスオフ",
    "face to face seated, legs wrapped around him, upright, foreheads touching, tightly embracing",
    "from side, full body",
  ),
  pretzel_dip: pose(
    "レッツェル・ディップ",
    "side-lying, man straddling her lower leg, her upper leg held up, deep",
    "from side, full body",
  ),
  straight_iron: pose(
    "ストレートアイロン",
    "prone with legs together, man lying on top of her back with his legs outside hers",
    "from side, full body",
  ),
  g_spot_master: pose(
    "Gスポットの達人",
    "woman on back with hips raised on a pillow, legs over his shoulders, man kneeling, angled thrust",
    "pov, from above, full body",
  ),
  cowgirl_helper: pose(
    "騎乗位ヘルパー",
    "cowgirl with the man sitting up supporting her back, hands on her hips, face to face",
    "from side, full body",
  ),
  leapfrog: pose(
    "馬跳び",
    "leapfrog position, woman on all fours with chest and head down, man kneeling behind, deep",
    "from side, full body",
  ),
  ballet_dancer: pose(
    "バレエダンサー",
    "standing, one leg raised straight up held by the man, ballet leg, face to face",
    "from side, full body",
  ),
  cross_booty: pose(
    "クロス・ブーティ",
    "side-lying with legs crossed, man behind her",
    "from behind, full body",
  ),
  caboose: pose(
    "カブース",
    "man sitting, woman sitting on his lap facing away, leaning back against him",
    "from side, full body",
  ),
  reverse_spoon: pose(
    "リバース・スプーン",
    "reverse spooning, side-lying, her back to him, one leg lifted",
    "from side, full body",
  ),
  golden_arch: pose(
    "ゴールデン・アーチ",
    "woman on her back with an arched back and lifted hips, man kneeling holding her hips",
    "from side, full body",
  ),
  clam: pose(
    "貝殻",
    "woman on her back with knees pulled up to her chest, man leaning forward over her",
    "pov, from above, full body",
  ),
  blender: pose(
    "かくはん機",
    "woman on top rotating her hips in a circle on him, turning around while seated",
    "from side, full body",
  ),
  pinball_wizard: pose(
    "ピンボール名人",
    "woman lying at the edge of the bed with her hips at the edge, legs held up, man standing",
    "from side, full body",
  ),
  split_spoon: pose(
    "先割れスプーン",
    "side-lying spooning with her top leg straddling his thigh, split legs",
    "from behind, full body",
  ),
  seated_wheelbarrow: pose(
    "座位での手押し車",
    "woman sitting leaning back supporting herself with her hands, legs held by the man",
    "from side, full body",
  ),
  tabletop: pose(
    "テーブルトップ",
    "woman lying on the edge of a table with legs over his shoulders, man standing",
    "from side, full body",
  ),
  champagne_room: pose(
    "シャンパンルーム",
    "man seated in a chair, woman sitting on his lap facing him sideways, intimate",
    "from side, full body",
  ),
  om_position: pose(
    "オーム",
    "lotus position, seated face to face with legs crossed, yoga style, slow",
    "from side, full body",
  ),
  good_x: pose(
    "グッド・エックス",
    "bodies crossed in an X shape, her legs open and his legs crossed over, side position",
    "from above, full body",
  ),
  lazy_man: pose(
    "怠け者",
    "man sitting back relaxed at the edge of the bed, woman sitting on his lap doing all the work",
    "from side, full body",
  ),
  snow_angel: pose(
    "雪の天使",
    "woman lying on her back with arms and legs spread out like angel wings, man between her legs",
    "from above, full body",
  ),
  primitive_lotus: pose(
    "未開の蓮",
    "seated, she leans back arched with her hands on the floor behind, legs around him",
    "from side, full body",
  ),
  snake: pose(
    "ヘビ",
    "woman lying on her stomach with legs together, man lying along her back, slithering motion",
    "from side, full body",
  ),
  bow_cowgirl: pose(
    "反り騎乗位",
    "reverse cowgirl leaning back with an arched back, hands behind on the bed, legs spread",
    "from side, full body",
  ),
  panther: pose(
    "女豹のポーズ",
    "panther pose, on all fours arching her back, head up, looking at viewer",
    "from side, full body",
  ),
  togawa_gatame: pose(
    "戸川固め",
    "woman on her back with both legs raised and folded toward her head, man pressing down from above with his legs placed over hers, locked hold, deep",
    "pov, from above, full body",
  ),
  valedictorian: pose(
    "卒業生総代（ヴァレディクトリアン）",
    "face to face, man seated reclining against the headboard, woman straddling him upright, leaning back slightly, hands on his thighs",
    "from side, full body",
  ),
  leg_hold: pose(
    "足抱え",
    "woman holding her own legs up and apart with her hands, missionary, man between her legs",
    "pov, from above, full body",
  ),
  spit_roast: pose(
    "串刺し（前後から）",
    "spit-roast, woman on all fours between two men, one in front in her mouth, one behind",
    "from side, full body",
  ),
  solo_prone: solo(
    "うつぶせオナニー",
    "lying on stomach, hips pressed to the bed, hand between legs, grinding",
    "from side, full body",
  ),
  solo_bubble_bath: solo(
    "泡風呂オナニー",
    "in a bubble bath, hand between her legs under the foam, head back, steam",
    "from above, upper body",
  ),
  solo_mirror: solo(
    "鏡を見ながら",
    "sitting in front of a mirror, legs apart, looking at herself in the mirror, hand between legs",
    "from front, mirror reflection",
  ),
  solo_circles: solo(
    "円を描く（指）",
    "lying on her back, fingers circling, knees up, eyes closed",
    "from above, full body",
  ),
  solo_sofa_grind: solo(
    "ソファでグラインド",
    "grinding on a sofa cushion, straddling it, hips moving",
    "from side, full body",
  ),
  solo_g_spot: solo(
    "Gスポット責め（指）",
    "lying on her back with knees up, fingers curled inside, back arched",
    "from above, full body",
  ),
  solo_tap: solo(
    "タップダンス（指先）",
    "lying on her back, fingertips tapping rhythmically, legs trembling",
    "from above, full body",
  ),
  // ─ 3Pや特殊プレイの体位（一覧記事・江戸の3P体位より）─
  all_cunnilingus: pose(
    "全員クンニ（3P）",
    "threesome, two women lying together while a man licks both of them in turn, face between their thighs",
    "from above, full body",
  ),
  all_fellatio: pose(
    "全員フェラ（3P）",
    "threesome, two men standing while a woman kneeling between them licks both penises in turn",
    "pov, from above, upper body",
  ),
  cowgirl_caress: pose(
    "騎乗位愛撫（3P）",
    "threesome, woman riding the man while a second woman kisses and caresses her from behind",
    "from side, full body",
  ),
  back_caress: pose(
    "バック愛撫（3P）",
    "threesome, doggystyle with a second woman lying underneath caressing her breasts",
    "from side, full body",
  ),
  missionary_caress: pose(
    "正常位愛撫（3P）",
    "threesome, missionary while a second woman kisses the first woman's neck and plays with her breasts",
    "from above, full body",
  ),
  double_cowgirl: pose(
    "W騎乗位（騎乗位＋顔面騎乗）",
    "threesome, one woman riding the man while another sits on his face facing her, kissing each other",
    "from side, full body",
  ),
  double_paizuri: pose(
    "Wパイズリ",
    "double paizuri, two women pressing their breasts around one penis together, kneeling",
    "pov, from above, upper body",
  ),
  double_fingering: pose(
    "W手マン",
    "threesome, two men fingering one woman at the same time, legs spread",
    "from above, full body",
  ),
  nipple_lick_handjob: pose(
    "W乳首舐め手コキ",
    "two women licking his nipples while one strokes his penis, man lying back",
    "from above, upper body",
  ),
  insertion_fellatio: pose(
    "挿入とフェラの2点責め",
    "threesome, woman fucked from behind while sucking another man's penis, spit-roast style",
    "from side, full body",
  ),
  double_handjob: pose(
    "W手コキ",
    "two women stroking one penis together with their hands, kneeling at his sides",
    "pov, from above, upper body",
  ),
  handjob_fellatio: pose(
    "手コキフェラ",
    "woman stroking one man's penis with her hand while sucking another's, kneeling between two men",
    "pov, from above, upper body",
  ),
  breast_play_cunni: pose(
    "おっぱい責めクンニ",
    "man licking her pussy while a second woman plays with and licks her breasts, lying on her back",
    "from above, full body",
  ),
  edo_ichifu_nijo: pose(
    "一夫二女（江戸の3P体位）",
    "one man and two women lying together, he is between them, one woman riding him while the other lies beside him kissing him",
    "from above, full body",
  ),
  edo_kagetsugi: pose(
    "花月擬（江戸の3P体位）",
    "threesome in a flowing posture, woman lying on her back with a man between her legs while another woman sits at her head, kissing",
    "from above, full body",
  ),
  fisting_pose: pose(
    "フィスト（上級者）",
    "adult couple, fisting, woman on her back with knees up, man's hand and wrist inserted",
    "from side, lower body",
  ),
  // ─ 体位（追加：世界の体位・場所別）─
  g4p_lotus: pose(
    "蓮華座（あぐら対面）",
    "sitting cross-legged, facing each other, arms and legs wrapped around",
    "pov, from side, upper body",
  ),
  g4p_spoon: pose(
    "スプーン（添い寝後ろから）",
    "lying on side, spooning, from behind, bodies pressed together",
    "from side, full body",
  ),
  g4p_cross_legs_front: pose(
    "交差位（脚を交差）",
    "lying on back, legs crossed, partner kneeling between",
    "pov, from above",
  ),
  g4p_standing_front_lift: pose(
    "立位・片脚持ち上げ",
    "standing, facing each other, one leg lifted by partner",
    "full body, from side",
  ),
  g4p_standing_wall_front: pose(
    "壁際の立位（向かい合い）",
    "standing against wall, facing each other, close embrace",
    "from side, full body",
  ),
  g4p_wall_back_press: pose(
    "壁に押し付け後背立位",
    "standing, pressed against wall, from behind, hands on wall",
    "from side, full body",
  ),
  g4p_kitchen_counter: pose(
    "キッチンカウンターで対面",
    "sitting on counter edge, legs wrapped around partner, facing each other",
    "from side, full body",
  ),
  g4p_bed_edge_missionary: pose(
    "ベッドの縁で正常位",
    "lying on back at bed edge, partner standing, legs on shoulders",
    "pov, from front",
  ),
  g4p_legs_on_shoulders: pose(
    "肩掛け正常位",
    "lying on back, both legs on partner shoulders, deep angle",
    "pov, from above",
  ),
  g4p_pretzel: pose(
    "プレッツェル（交差横位）",
    "lying on side, one leg between partner legs, facing",
    "from side, full body",
  ),
  g4p_scissors: pose(
    "シザース（貝合わせ）",
    "lying, legs interlocked, hips pressed together, facing each other",
    "from side, full body",
  ),
  g4p_butterfly: pose(
    "バタフライ（机の端で）",
    "lying on back on table edge, hips at edge, partner standing",
    "from front, full body",
  ),
  g4p_crab: pose(
    "クラブ（後ろ手座位）",
    "sitting on bed, leaning back on hands, partner kneeling in front",
    "from side, full body",
  ),
  g4p_reverse_lotus: pose(
    "背面座位（膝の上）",
    "sitting on lap, facing away, back against chest",
    "from side, full body",
  ),
  g4p_reverse_lotus_mirror: pose(
    "鏡の前の背面座位",
    "sitting on lap facing away, mirror in front, watching reflection",
    "from front, mirror reflection",
  ),
  g4p_chair_straddle: pose(
    "椅子での対面座位",
    "sitting on chair, partner straddling lap, arms around neck",
    "from side, full body",
  ),
  g4p_chair_reverse: pose(
    "椅子での背面座位",
    "sitting on chair, partner sitting on lap facing away, hands on knees",
    "from side, full body",
  ),
  g4p_sofa_arm_doggy: pose(
    "ソファの肘掛けにもたれる後背位",
    "bent over sofa arm, from behind, hands gripping cushion",
    "from side, full body",
  ),
  g4p_sofa_cowgirl: pose(
    "ソファで騎乗位",
    "man sitting on sofa, woman straddling, hands on his shoulders",
    "from front, full body",
  ),
  g4p_sofa_leg_up: pose(
    "ソファで片脚を背もたれに",
    "lying on sofa, one leg on backrest, partner kneeling",
    "from above, full body",
  ),
  g4p_stair_doggy: pose(
    "階段の途中で後背位",
    "on stairs, hands on step, from behind, kneeling",
    "from below, full body",
  ),
  g4p_stair_sitting: pose(
    "階段に座って対面",
    "sitting on stairs, facing each other, legs around waist",
    "from side, full body",
  ),
  g4p_car_back_seat: pose(
    "車の後部座席で対面座位",
    "back seat of car, straddling lap, steamy windows",
    "from side, close up",
  ),
  g4p_car_front_seat_reverse: pose(
    "助手席で背面座位",
    "front seat of car, sitting on lap facing away",
    "from side, upper body",
  ),
  g4p_pool_edge: pose(
    "プールの縁にもたれて",
    "leaning on pool edge, waist deep in water, from behind",
    "from side, water splash",
  ),
  g4p_pool_float: pose(
    "浮き輪につかまって後ろから",
    "holding float ring, in water, from behind, wet skin",
    "from side, water splash",
  ),
  g4p_bath_cowgirl: pose(
    "湯船の中で騎乗位",
    "sitting in bathtub, straddling lap, steam, wet hair",
    "from side, steam",
  ),
  g4p_bath_edge_sit: pose(
    "浴槽の縁に座って対面",
    "sitting on bathtub edge, legs spread, partner kneeling in water",
    "from front, upper body",
  ),
  g4p_shower_wall: pose(
    "シャワー室の壁に手をついて",
    "standing in shower, hands on tiled wall, from behind, water running",
    "from side, wet skin",
  ),
  g4p_shower_lift: pose(
    "シャワーの下で抱え上げ",
    "lifted in arms, legs around waist, water running down",
    "from side, full body",
  ),
  g4p_onsen_rock: pose(
    "露天風呂の岩にもたれて",
    "leaning on rock in hot spring, from behind, steam, night",
    "from side, steam",
  ),
  g4p_table_edge_front: pose(
    "テーブルの端に座って対面",
    "sitting on table edge, legs open, partner standing",
    "from front, full body",
  ),
  g4p_desk_bend_over: pose(
    "デスクに上半身を伏せて",
    "bent over desk, chest on desk, from behind",
    "from side, full body",
  ),
  g4p_desk_chair_lap: pose(
    "オフィスチェアの膝上",
    "sitting on lap in office chair, facing, skirt hiked",
    "from side, upper body",
  ),
  g4p_bed_prone_bone: pose(
    "うつ伏せの寝バック（脚を閉じて）",
    "lying on stomach, legs together, partner on top from behind",
    "from side, from above",
  ),
  g4p_prone_arch: pose(
    "腰を反らせる伏せ位",
    "lying on stomach, hips lifted, arched back, pillow under hips",
    "from side, full body",
  ),
  g4p_pillow_hips: pose(
    "腰枕の正常位",
    "lying on back, pillow under hips, legs spread",
    "pov, from above",
  ),
  g4p_legs_up_v: pose(
    "V字開脚正常位",
    "lying on back, legs raised in V shape, ankles held",
    "pov, from front",
  ),
  g4p_ankle_hold: pose(
    "足首を持って開く正常位",
    "lying on back, ankles held apart by partner, kneeling",
    "pov, from front",
  ),
  g4p_hug_missionary: pose(
    "抱きしめ正常位",
    "lying on back, arms around partner back, chest pressed",
    "from side, close up",
  ),
  g4p_kiss_missionary: pose(
    "キスしながらの正常位",
    "missionary, deep kiss, fingers interlaced beside head",
    "from side, close up",
  ),
  g4p_hands_clasped_missionary: pose(
    "両手を握り合う正常位",
    "missionary, hands clasped above head, eye contact",
    "from above, close up",
  ),
  g4p_face_sit_cunni: pose(
    "顔面騎乗（口での愛撫）",
    "woman sitting over partner face, hands on headboard",
    "from side, from front",
  ),
  g4p_cunnilingus_lying: pose(
    "寝て脚を開き口で愛撫される",
    "lying on back, legs apart, partner head between thighs",
    "from above, from side",
  ),
  g4p_cunnilingus_standing: pose(
    "立って片脚を肩に乗せられる愛撫",
    "standing, one leg on partner shoulder, partner kneeling",
    "from side, full body",
  ),
  g4p_cunnilingus_sitting: pose(
    "椅子に座って脚を開く愛撫",
    "sitting on chair, legs apart, partner kneeling in front",
    "from front, from above",
  ),
  g4p_fellatio_kneeling: pose(
    "ひざまずく口奉仕",
    "kneeling, looking up, face near crotch, hand on thigh",
    "pov, from above",
  ),
  g4p_fellatio_sitting_edge: pose(
    "ベッドの縁に座らせて口奉仕",
    "man sitting on bed edge, woman kneeling between legs",
    "from side, from above",
  ),
  g4p_fellatio_lying: pose(
    "寝かせて覆いかぶさる口奉仕",
    "man lying on back, woman lying between legs, hair swept aside",
    "from above, from side",
  ),
  g4p_fellatio_shower: pose(
    "シャワーでの口奉仕",
    "kneeling in shower, water running over face, looking up",
    "from above, wet skin",
  ),
  g4p_deep_throat_edge: pose(
    "ベッドの縁で頭を垂らす口奉仕",
    "lying on back, head hanging off bed edge, partner standing",
    "from side, upper body",
  ),
  g4p_handjob_behind: pose(
    "後ろから抱きついて手コキ",
    "sitting, partner embracing from behind, hand stroking",
    "from front, from above",
  ),
  g4p_handjob_lap: pose(
    "膝に座って手で愛撫",
    "sitting on lap, hand between bodies, kissing",
    "from side, upper body",
  ),
  g4p_fingering_back_hug: pose(
    "後ろから抱いて指で愛撫",
    "standing, hugged from behind, hand between thighs",
    "from front, upper body",
  ),
  g4p_fingering_lying: pose(
    "寝かせて指で愛撫",
    "lying on back, legs bent, partner hand between legs",
    "from above, close up",
  ),
  g4p_fingering_kiss: pose(
    "キスしながら指で愛撫",
    "sitting, kissing, hand between thighs under skirt",
    "from side, upper body",
  ),
  g4p_titfuck_kneel: pose(
    "ひざまずいてパイズリ（谷間に挟む）",
    "kneeling, pressing breasts together around cock, looking up",
    "pov, close up",
  ),
  g4p_titfuck_lying: pose(
    "仰向けの男にまたがるパイズリ",
    "straddling chest, breasts pressed together",
    "from side, from above",
  ),
  g4p_thigh_job: pose(
    "太もも挟み（素股）",
    "lying on side, cock between thighs, from behind",
    "from side, close up",
  ),
  g4p_thigh_job_front: pose(
    "向かい合って素股",
    "lying face to face, thighs squeezed together, hips moving",
    "from side, full body",
  ),
  g4p_footjob_sitting: pose(
    "足で責める（座位）",
    "sitting, feet on partner crotch, smirk",
    "from front, full body",
  ),
  g4p_footjob_lying: pose(
    "寝て足で愛撫",
    "lying on back, partner sitting, feet on crotch",
    "from side, full body",
  ),
  g4p_buttjob: pose(
    "尻で擦る（お尻コキ）",
    "bent over, buttocks pressed against crotch, looking back",
    "from behind, from side",
  ),
  g4p_armpit_hold: pose(
    "腋に挟む",
    "raised arm, cock pressed in armpit, sweat",
    "close up, from side",
  ),
  g4p_mutual_oral_side: pose(
    "横向きシックスナイン",
    "lying on side, head to crotch, mutual oral",
    "from side, full body",
  ),
  g4p_mutual_oral_standing: pose(
    "立ちシックスナイン（逆さ抱え）",
    "upside down, held in arms, mutual oral, standing",
    "from side, full body",
  ),
  g4p_yab_yum: pose(
    "ヤブユム（神聖な抱擁）",
    "sitting, woman on lap facing, foreheads touching, breathing together",
    "from side, full body",
  ),
  g4p_indian_cross: pose(
    "インディアン・クロス",
    "lying on back, legs crossed over partner hips, face to face",
    "from side, full body",
  ),
  g4p_twining: pose(
    "ツイスト位（絡み合い）",
    "lying, legs twisted together, torsos facing, close embrace",
    "from side, full body",
  ),
  g4p_splitting_bamboo: pose(
    "竹割り（片脚を上げる）",
    "lying on back, one leg raised over partner shoulder, other flat",
    "from side, full body",
  ),
  g4p_supported_kneel: pose(
    "持ち上げ座位（ひざ立ち）",
    "kneeling, woman lifted by partner hands on thighs, facing",
    "from side, full body",
  ),
  g4p_fan_position: pose(
    "扇の位（脚を広げて後ろから）",
    "kneeling on all fours, legs spread wide, partner behind",
    "from behind, from side",
  ),
  g4p_elephant: pose(
    "エレファント（低い後背位）",
    "lying flat on stomach, legs together, partner kneeling over",
    "from side, from behind",
  ),
  g4p_cat_position: pose(
    "キャット（密着して擦る正常位）",
    "missionary, partner higher up, grinding, pelvis pressed",
    "from side, close up",
  ),
  g4p_bridge: pose(
    "ブリッジ位",
    "woman bending backward as a bridge, partner kneeling",
    "from side, full body",
  ),
  g4p_wheelbarrow: pose(
    "手押し車（四つん這いで脚を持つ）",
    "hands on floor, legs held up by partner standing behind",
    "from side, full body",
  ),
  g4p_standing_arms_back: pose(
    "立って両腕を引かれる後背位",
    "standing, arms pulled back by partner from behind, leaning forward",
    "from side, full body",
  ),
  g4p_bent_forward_wall: pose(
    "壁に額をつけた後背立位",
    "standing bent forward, forehead on wall, partner behind",
    "from side, full body",
  ),
  g4p_tied_knot: pose(
    "結び目（座位の絡み）",
    "sitting, legs wrapped around waist, leaning back, arms held",
    "from side, full body",
  ),
  g4p_rocking_chair: pose(
    "揺り椅子の座位",
    "rocking chair, sitting on lap, slow rocking",
    "from side, full body",
  ),
  g4p_hammock: pose(
    "ハンモックの上で",
    "lying in hammock, partner above, swaying, outdoors night",
    "from side, full body",
  ),
  g4p_balcony_rail: pose(
    "バルコニーの手すりに手をついて",
    "standing, hands on balcony railing, from behind, night city",
    "from side, city lights",
  ),
  g4p_rooftop_wall: pose(
    "屋上の壁際で",
    "against low wall, rooftop, skyline background",
    "from side, wide",
  ),
  g4p_beach_lying: pose(
    "砂浜で寝て正常位",
    "lying on sand, missionary, waves in background, sunset",
    "from side, wide",
  ),
  g4p_beach_cowgirl: pose(
    "砂浜の騎乗位",
    "straddling on sand, hair blowing, sunset light",
    "from front, full body",
  ),
  g4p_boat_deck: pose(
    "ヨットのデッキで後背位",
    "on yacht deck, hands on railing, from behind, ocean",
    "from side, wide",
  ),
  g4p_tent_sleeping_bag: pose(
    "テントの寝袋の中で",
    "inside tent, sleeping bag, lantern light, cuddling close",
    "from side, close up",
  ),
  g4p_elevator_wall: pose(
    "エレベーターの壁で抱え上げ",
    "lifted against elevator wall, legs around waist, mirrors",
    "from side, full body",
  ),
  g4p_changing_room_stand: pose(
    "フィッティングルームの立位",
    "standing in fitting room, back against mirror, clothes half off",
    "from side, mirror reflection",
  ),
  g4p_car_hood: pose(
    "ボンネットに手をついて",
    "bent over car hood, from behind, night parking lot",
    "from side, full body",
  ),
  g4p_piano_top: pose(
    "ピアノの上で正常位",
    "lying on grand piano lid, partner standing, dim light",
    "from side, full body",
  ),
  g4p_dining_table_lying: pose(
    "ダイニングテーブルに寝て",
    "lying on dining table, legs open, partner standing",
    "from front, from above",
  ),
  g4p_kitchen_behind: pose(
    "キッチンで後ろから抱いて",
    "standing at kitchen sink, hugged from behind, apron",
    "from side, upper body",
  ),
  g4p_laundry_machine: pose(
    "洗濯機の上で",
    "sitting on washing machine, legs around waist",
    "from side, full body",
  ),
  g4p_window_press: pose(
    "窓ガラスに押し付けて後背位",
    "pressed against window glass, from behind, city view night",
    "from outside, full body",
  ),
  g4p_mirror_standing_back: pose(
    "鏡に手をついた立ちバック",
    "standing, hands on mirror, from behind, face visible in mirror",
    "from front, mirror reflection",
  ),
  g4p_mirror_cowgirl: pose(
    "鏡の前の騎乗位",
    "straddling, facing mirror, reflection shows both",
    "from front, mirror reflection",
  ),
  g4p_slow_grind_cowgirl: pose(
    "ゆっくり腰を回す騎乗位",
    "straddling, hips grinding slowly, hands on chest",
    "from below, upper body",
  ),
  g4p_lean_forward_cowgirl: pose(
    "前に倒れて抱きつく騎乗位",
    "straddling, leaning forward, chest pressed, kissing",
    "from side, upper body",
  ),
  g4p_squat_reverse: pose(
    "しゃがむ背面騎乗位（足裏をつく）",
    "squatting, facing away, hands on knees, back view",
    "from behind, full body",
  ),
  g4p_reverse_cowgirl_lean: pose(
    "背面騎乗位で仰け反る",
    "reverse straddle, leaning back on partner chest",
    "from side, full body",
  ),
  g4p_kneeling_lap: pose(
    "ひざ立ちで向かい合う",
    "kneeling, facing each other, holding hips, chest touching",
    "from side, full body",
  ),
  g4p_sideways_straddle: pose(
    "横向きにまたがる座位",
    "sitting sideways on lap, arm around neck",
    "from side, upper body",
  ),
  g4p_solo_pillow: pose(
    "枕を抱えての一人遊び",
    "lying on side, hugging pillow between thighs, flushed",
    "from side, full body",
  ),
  g4p_solo_mirror: pose(
    "鏡を見ながら一人遊び",
    "sitting before mirror, legs apart, touching self",
    "from front, mirror reflection",
  ),
  g4p_solo_bath: pose(
    "お風呂での一人遊び",
    "in bathtub, leaning back, hand between thighs, steam",
    "from side, steam",
  ),
  g4p_solo_bed_toy: pose(
    "ベッドで道具を使う一人遊び",
    "lying on back, knees up, using vibrator, sheets rumpled",
    "from above, from side",
  ),
  g4p_solo_phone: pose(
    "スマホを見ながら一人遊び",
    "lying in bed, phone in hand, other hand under blanket",
    "from above, upper body",
  ),
  g4p_solo_kneel_pillow: pose(
    "ひざ立ちで枕にまたがる",
    "kneeling, straddling pillow, grinding, hand on headboard",
    "from side, full body",
  ),
  g4p_solo_wall: pose(
    "壁にもたれて立ったまま",
    "standing against wall, one hand between legs, head back",
    "from front, full body",
  ),
  g4p_solo_chair_edge: pose(
    "椅子の角に擦りつける",
    "sitting, grinding against chair corner, thighs trembling",
    "from side, full body",
  ),
  g4p_self_breast_lying: pose(
    "仰向けで胸に触れる",
    "lying on back, hands on own breasts, eyes closed",
    "from above, upper body",
  ),
  g4p_tease_stripping: pose(
    "ゆっくり脱いで見せる",
    "standing, slowly lowering straps, glancing over shoulder",
    "from front, from behind",
  ),
  g4p_lap_dance: pose(
    "ラップダンス（膝の上で踊る）",
    "dancing on lap, back to him, hips swaying",
    "from side, full body",
  ),
  g4p_cuddle_after: pose(
    "事後の添い寝",
    "lying close, arms around each other, sheets, afterglow",
    "from above, from side",
  ),
  g4p_morning_wake: pose(
    "朝の寝起き抱きつき",
    "lying in bed, sunlight, sleepy, arms around each other",
    "from above, close up",
  ),
  g4p_knee_pillow_kiss: pose(
    "膝枕でキス",
    "head on lap, looking up, leaning down for kiss",
    "from side, close up",
  ),
  // ─ 体位（追加：世界の体位・場所別）─
  h5p_001: pose(
    "燕返し",
    "lying on back, one leg raised high, partner kneeling between, leg over shoulder",
    "pov, from side, full body",
  ),
  h5p_002: pose(
    "鴨の入首",
    "lying on back, knees drawn up to chest, partner leaning over, close embrace",
    "from above, close-up",
  ),
  h5p_003: pose(
    "帆かけ茶臼",
    "woman on top, one leg raised straight up, partner holding the ankle",
    "from side, full body",
  ),
  h5p_004: pose(
    "窓の月",
    "side lying, one leg lifted, partner kneeling behind facing her, moonlight window",
    "from side, full body, window light",
  ),
  h5p_005: pose(
    "本駒掛け",
    "lying on side, legs interlaced like scissors, partners facing",
    "from above, full body",
  ),
  h5p_006: pose(
    "千鳥",
    "side lying, partner behind her, her top leg bent over his hip",
    "from side, full body",
  ),
  h5p_007: pose(
    "仏壇返し",
    "woman on all fours, back arched, partner kneeling behind, leaning forward",
    "from side, full body",
  ),
  h5p_008: pose(
    "浮橋",
    "woman bridging with hips lifted, partner kneeling supporting her waist",
    "from side, low angle",
  ),
  h5p_009: pose(
    "御所車",
    "partner standing, woman lifted by thighs facing him, arms around neck",
    "from side, full body",
  ),
  h5p_010: pose(
    "立ち松葉",
    "both standing facing, one leg lifted and hooked around his waist",
    "from side, full body",
  ),
  h5p_011: pose(
    "鵯越え",
    "woman lying on back, ankles on partner shoulders, partner kneeling upright",
    "pov, from front",
  ),
  h5p_012: pose(
    "抱き上げ",
    "partner holding her up against his chest, her legs around his hips",
    "from behind, full body",
  ),
  h5p_013: pose(
    "撞木反り",
    "partner seated leaning back on hands, woman sitting on lap facing, leaning backward",
    "from side, full body",
  ),
  h5p_014: pose(
    "筏茶臼",
    "partner lying flat, woman sitting astride with knees drawn up, hands on his legs",
    "from front, full body",
  ),
  h5p_015: pose(
    "手懸け",
    "woman seated on partner lap facing, partner holding her hands behind her",
    "from side, close-up",
  ),
  h5p_016: pose(
    "提灯ぶら下がり",
    "woman hanging on partner neck, legs lifted around his waist",
    "from side, full body",
  ),
  h5p_017: pose(
    "菊一文字",
    "lying on back, legs straight and open in a line, partner facing between",
    "from above, full body",
  ),
  h5p_018: pose(
    "二つ巴",
    "side lying head to toe, intertwined, mutual closeness",
    "from above, full body",
  ),
  h5p_019: pose(
    "絞り芙蓉",
    "seated woman facing partner, legs crossed behind him tightly",
    "from side, close-up",
  ),
  h5p_020: pose(
    "後櫓",
    "woman seated on partner lap facing forward, leaning back on his chest",
    "from front, upper body",
  ),
  h5p_021: pose(
    "碁盤攻め",
    "woman seated on table edge, partner standing, legs apart",
    "from side, full body",
  ),
  h5p_022: pose(
    "錦の帯",
    "woman lying on back, one leg over partner shoulder, one leg straight",
    "pov, from front",
  ),
  h5p_023: pose(
    "洞入り",
    "woman crouched low, partner behind covering her back",
    "from side, low angle",
  ),
  h5p_024: pose(
    "唐草居茶臼",
    "partner seated on floor, woman seated facing him, arms and legs entwined like vines",
    "from side, full body",
  ),
  h5p_025: pose(
    "窓越しの立ち",
    "woman leaning on window frame, partner standing behind, curtains drifting",
    "from behind, full body",
  ),
  h5p_026: pose(
    "とんぼ返り",
    "woman on all fours turning head back toward partner, arched spine",
    "from side, upper body",
  ),
  h5p_027: pose(
    "つる首",
    "woman kneeling upright, partner behind, neck stretched, hands on her waist",
    "from side, full body",
  ),
  h5p_028: pose(
    "菊座重ね",
    "lying on stomach, partner lying on top with pillows under hips",
    "from side, full body",
  ),
  h5p_029: pose(
    "座り鼎",
    "both seated facing, legs open in triangle, hands clasped",
    "from front, full body",
  ),
  h5p_030: pose(
    "櫓立ち前抱え",
    "partner standing holding her up by the thighs, her back against a wall",
    "from side, full body",
  ),
  h5p_031: pose(
    "壁際の片脚上げ立位",
    "standing facing wall, one leg raised on partner arm",
    "from side, full body",
  ),
  h5p_032: pose(
    "カーマの蓮華座",
    "partner seated cross-legged, woman sitting facing him, arms wrapped around",
    "from side, full body",
  ),
  h5p_033: pose(
    "蓮の花",
    "woman seated facing partner, both legs wrapped behind him, foreheads touching",
    "from side, close-up",
  ),
  h5p_034: pose(
    "ヨーニの開き",
    "lying on back, knees up and open, ankles crossed behind partner back",
    "pov, from front",
  ),
  h5p_035: pose(
    "かさね枕の位",
    "woman lying on back, hips raised on cushion, legs hooked on his shoulders",
    "from side, full body",
  ),
  h5p_036: pose(
    "蛇の抱擁",
    "lying face to face, bodies entwined in full-length embrace, legs braided",
    "from above, full body",
  ),
  h5p_037: pose(
    "つる草の抱擁",
    "standing, woman clinging to partner like a vine, one leg wrapped around him",
    "from side, full body",
  ),
  h5p_038: pose(
    "木登りの抱擁",
    "woman stands on one foot, other leg hooked on his thigh, arms around his neck",
    "from side, full body",
  ),
  h5p_039: pose(
    "胡麻と米の抱擁",
    "lying side by side, bodies pressed completely together, legs entwined",
    "from above, full body",
  ),
  h5p_040: pose(
    "牛乳と水の抱擁",
    "woman seated on lap, deep close embrace, nearly merged",
    "from side, close-up",
  ),
  h5p_041: pose(
    "ブランコ座位",
    "woman seated in swing chair, partner standing facing, rocking motion",
    "from side, full body",
  ),
  h5p_042: pose(
    "ハンモック位",
    "lying in a hammock together, woman on top, swaying",
    "from side, full body",
  ),
  h5p_043: pose(
    "ベッドの端から",
    "woman lying on bed edge on back, partner standing at bedside, her legs on his hips",
    "from side, full body",
  ),
  h5p_044: pose(
    "ベッドの端で膝立ち",
    "woman kneeling on bed edge, partner standing behind her",
    "from side, full body",
  ),
  h5p_045: pose(
    "ソファの背もたれ越し",
    "woman leaning over sofa back, partner standing behind",
    "from side, full body",
  ),
  h5p_046: pose(
    "ソファの肘掛け位",
    "woman lying over sofa armrest, hips lifted, partner kneeling behind",
    "from side, full body",
  ),
  h5p_047: pose(
    "窓辺の対面立位",
    "standing facing, woman back against the window, city lights behind",
    "from side, full body, night",
  ),
  h5p_048: pose(
    "鏡の前の立位",
    "standing before a mirror, partner behind, both watching the reflection",
    "from side, mirror, full body",
  ),
  h5p_049: pose(
    "鏡の前の騎乗",
    "woman on top facing a large mirror, partner lying back",
    "from side, mirror, full body",
  ),
  h5p_050: pose(
    "椅子で後ろ向き座位",
    "woman seated on partner lap facing away on a chair, hands on knees",
    "from behind, full body",
  ),
  h5p_051: pose(
    "椅子の背抱き",
    "woman straddling a chair backwards, partner standing behind",
    "from side, full body",
  ),
  h5p_052: pose(
    "階段の途中で",
    "woman leaning on stair rail, partner one step below behind her",
    "from side, full body",
  ),
  h5p_053: pose(
    "バルコニーの手すり立位",
    "woman holding balcony railing, partner behind, night skyline",
    "from behind, full body, night",
  ),
  h5p_054: pose(
    "シャワー壁立位",
    "standing under shower, woman back against tiles, one leg lifted",
    "from side, full body, steam",
  ),
  h5p_055: pose(
    "湯船の縁に座って",
    "woman seated on bath edge, partner in water standing between her legs",
    "from side, full body, steam",
  ),
  h5p_056: pose(
    "湯船の対面座位",
    "both in hot tub facing, woman straddling, water rippling",
    "from side, upper body, steam",
  ),
  h5p_057: pose(
    "湯船の背面座位",
    "woman seated on partner in tub facing away, leaning back",
    "from side, upper body, steam",
  ),
  h5p_058: pose(
    "洗い場の四つん這い",
    "woman on all fours on bathroom floor, foam on skin, partner kneeling",
    "from side, full body",
  ),
  h5p_059: pose(
    "プールサイドの縁",
    "woman seated on pool edge, partner standing in water between her legs",
    "from side, full body",
  ),
  h5p_060: pose(
    "プールの中の抱え上げ",
    "partner standing waist-deep holding her up, legs around him",
    "from side, full body, water",
  ),
  h5p_061: pose(
    "波打ち際の騎乗",
    "woman on top lying on shore sand with waves",
    "from side, full body, beach",
  ),
  h5p_062: pose(
    "砂浜の側位",
    "side lying on sand, spoon position, waves behind",
    "from above, full body, beach",
  ),
  h5p_063: pose(
    "ハンモックのスプーン",
    "spooning in hammock, partner behind, gentle sway",
    "from side, full body",
  ),
  h5p_064: pose(
    "テントの中の対面座位",
    "inside tent, seated facing, lantern light, blanket over shoulders",
    "from side, close-up",
  ),
  h5p_065: pose(
    "寝袋の中のスプーン",
    "spooning under one open sleeping bag",
    "from side, close-up",
  ),
  h5p_066: pose(
    "車の後部座席の騎乗",
    "woman straddling partner on the car back seat, fogged windows",
    "from front, upper body",
  ),
  h5p_067: pose(
    "ボンネットに手をつく立位",
    "woman bent over car hood, partner behind, night parking",
    "from side, full body",
  ),
  h5p_068: pose(
    "運転席での座位",
    "woman seated on partner lap in driver seat, facing him",
    "from side, upper body",
  ),
  h5p_069: pose(
    "テーブルの端に座る対面",
    "woman seated on dining table edge, partner standing, glasses nearby",
    "from side, full body",
  ),
  h5p_070: pose(
    "キッチンカウンター座位",
    "woman seated on kitchen counter, legs around partner waist",
    "from side, full body",
  ),
  h5p_071: pose(
    "洗面台の上で",
    "woman seated on bathroom vanity, partner standing, mirror behind",
    "from side, full body",
  ),
  h5p_072: pose(
    "冷蔵庫に背中をつけて",
    "woman standing against refrigerator, one leg lifted, partner pressing close",
    "from side, full body",
  ),
  h5p_073: pose(
    "本棚の前の立位",
    "standing before bookshelf, woman back against books, partner holding her hand above her head",
    "from side, full body",
  ),
  h5p_074: pose(
    "ピアノの上で",
    "woman seated on closed piano lid, partner standing close",
    "from side, full body",
  ),
  h5p_075: pose(
    "舞台袖の壁際",
    "standing against backstage wall, curtains and dim light",
    "from side, full body",
  ),
  h5p_076: pose(
    "楽屋の鏡台前",
    "woman seated on dressing table, partner standing, mirror bulbs glowing",
    "from side, full body",
  ),
  h5p_077: pose(
    "撮影スタジオの四つん這い",
    "woman on all fours on white backdrop paper, partner kneeling behind, studio light",
    "from side, full body",
  ),
  h5p_078: pose(
    "サウナベンチの上段",
    "woman seated on upper sauna bench, partner seated below facing her",
    "from side, upper body, steam",
  ),
  h5p_079: pose(
    "マッサージベッドでうつ伏せ",
    "woman lying on stomach on massage table, partner leaning over her back",
    "from side, full body",
  ),
  h5p_080: pose(
    "温泉の岩に寄りかかって",
    "woman leaning against rocks in open-air bath, partner behind in water",
    "from behind, upper body, steam",
  ),
  h5p_081: pose(
    "畳の上の膝抱え位",
    "woman lying on tatami, knees to chest, partner kneeling",
    "from above, full body",
  ),
  h5p_082: pose(
    "布団の中のスプーン",
    "spooning under futon, partner arm around her waist",
    "from side, close-up",
  ),
  h5p_083: pose(
    "布団の上の対面座位",
    "seated on futon facing, yukata slipping from shoulders",
    "from side, close-up",
  ),
  h5p_084: pose(
    "縁側の四つん這い",
    "woman on all fours on engawa veranda, garden behind",
    "from side, full body",
  ),
  h5p_085: pose(
    "障子の影の側位",
    "side lying silhouette seen through shoji screen",
    "from far, silhouette",
  ),
  h5p_086: pose(
    "天蓋ベッドの騎乗",
    "woman on top inside canopy bed, sheer curtains",
    "from side, full body",
  ),
  h5p_087: pose(
    "ベッドの足元で膝立ち対面",
    "both kneeling facing at foot of bed, embracing",
    "from side, full body",
  ),
  h5p_088: pose(
    "肩に乗せた騎乗",
    "woman seated astride partner shoulders while he kneels on bed",
    "from front, full body",
  ),
  h5p_089: pose(
    "腰を浮かせた座位",
    "woman seated facing, knees lifted off bed, partner holding hips",
    "from side, full body",
  ),
  h5p_090: pose(
    "ひざ抱え位",
    "lying on back, woman hugging own knees, partner leaning over",
    "pov, from front",
  ),
  h5p_091: pose(
    "足首を揃えた横倒し位",
    "lying on back, ankles together raised to one side, partner kneeling",
    "from side, full body",
  ),
  h5p_092: pose(
    "足首を肩に乗せる両手つなぎ位",
    "lying on back, ankles on partner shoulders, hands clasped",
    "pov, from front",
  ),
  h5p_093: pose(
    "片脚を抱える位",
    "lying on back, partner holding one leg against his chest",
    "from side, full body",
  ),
  h5p_094: pose(
    "枕を腰に敷く位",
    "lying on back, pillow under hips, partner kneeling",
    "from side, full body",
  ),
  h5p_095: pose(
    "重なり位",
    "lying on back, partner lying fully on top, arms around neck",
    "from above, close-up",
  ),
  h5p_096: pose(
    "マットレスの縁の四つん這い",
    "on all fours at mattress edge, partner standing behind",
    "from side, full body",
  ),
  h5p_097: pose(
    "両腕を後ろに引く位",
    "woman kneeling, partner holding both her wrists gently behind, arched back",
    "from side, full body",
  ),
  h5p_098: pose(
    "上体を起こして背中を預ける位",
    "kneeling upright back against partner chest, hands on her hips",
    "from side, full body",
  ),
  h5p_099: pose(
    "頬を寄せる低い四つん這い",
    "on all fours, chest down, cheek on pillow, partner behind",
    "from side, full body",
  ),
  h5p_100: pose(
    "前かがみ騎乗",
    "woman on top leaning forward, hands on his chest, hair hanging",
    "from side, upper body",
  ),
  h5p_101: pose(
    "指を絡める騎乗",
    "woman on top, fingers interlaced with partner hands",
    "from front, upper body",
  ),
  h5p_102: pose(
    "のけぞり騎乗",
    "woman on top, arched backward, hands on his thighs",
    "from side, full body",
  ),
  h5p_103: pose(
    "反り騎乗",
    "woman on top facing away, leaning back on her hands",
    "from side, full body",
  ),
  h5p_104: pose(
    "横向き騎乗",
    "woman on top sitting sideways, knees together to one side",
    "from side, full body",
  ),
  h5p_105: pose(
    "膝立ち対面座位",
    "both kneeling, woman sitting on thighs, foreheads touching",
    "from side, close-up",
  ),
  h5p_106: pose(
    "ダンスのような立位",
    "standing facing with partner arm at her waist, dancing embrace, one leg lifted",
    "from side, full body",
  ),
  h5p_107: pose(
    "壁際の抱きしめ立位",
    "woman held gently against wall, her leg lifted over partner arm, loving embrace",
    "from side, full body",
  ),
  h5p_108: pose("抱き合うスプーン", "face-to-face spooning, legs entwined", "from above, close-up"),
  h5p_109: pose(
    "脚を後ろにかけるスプーン",
    "spooning, partner behind, her leg thrown back over his thigh",
    "from side, full body",
  ),
  h5p_110: pose(
    "長座位の抱擁",
    "seated with legs extended, woman on lap, arms around neck",
    "from side, full body",
  ),
  h5p_111: pose(
    "あぐら座位",
    "partner seated cross-legged, woman on lap facing with legs around his back",
    "from side, full body",
  ),
  h5p_112: pose(
    "鏡に向かう後ろ抱え座位",
    "partner seated behind, holding her waist, both facing a mirror",
    "from front, upper body, mirror",
  ),
  h5p_113: pose(
    "胸の上の背面位",
    "woman lying on partner chest facing up, partner underneath",
    "from side, full body",
  ),
  h5p_114: pose(
    "膝の間に座る背面位",
    "woman seated between partner open knees, back against his chest",
    "from front, full body",
  ),
  h5p_115: pose(
    "舟漕ぎ位",
    "both seated facing, legs extended, hands clasped, rocking back and forth like rowing",
    "from side, full body",
  ),
  h5p_116: pose(
    "ヨガ風バランス座位",
    "partner seated, woman balancing on his lap with legs extended, hands holding",
    "from side, full body",
  ),
  h5p_117: pose(
    "肩にもたれる立位",
    "woman leaning her head on his shoulder, standing embrace, hips close",
    "from side, upper body",
  ),
  h5p_118: pose(
    "首にぶら下がる立位",
    "standing, woman hanging from partner neck, feet off the floor",
    "from side, full body",
  ),
  h5p_119: pose(
    "窓ガラスに手をつく立位",
    "woman palms on the glass, partner behind, city night below",
    "from behind, full body, night",
  ),
  // ─ 体位（追加：世界の体位・場所別）─
  h6p_m_legs_lying: solo(
    "M字開脚（寝て）",
    "lying on back, m legs, knees bent and spread wide, feet flat, legs apart, spread legs",
    "from front, full body, from above, wide shot",
  ),
  h6p_m_legs_sitting: solo(
    "M字開脚（座って）",
    "sitting, m legs, knees up and spread wide, leaning back on hands, spread legs",
    "from front, full body",
  ),
  h6p_m_legs_wall: solo(
    "M字開脚（壁にもたれて）",
    "sitting against wall, m legs, knees up and spread wide, spread legs",
    "from front, full body",
  ),
  h6p_m_legs_bed: solo(
    "M字開脚（ベッドの上）",
    "on bed, m legs, knees bent and spread wide, lying back on pillows, spread legs",
    "from front, pov, full body",
  ),
  h6p_m_legs_hands_knees: solo(
    "M字開脚（自分で膝を持つ）",
    "lying on back, holding own knees apart, m legs, spread legs, hands on knees",
    "from front, from above, full body",
  ),
  h6p_m_legs_hands_thigh: solo(
    "M字開脚（太ももを持つ）",
    "lying on back, holding own thighs, m legs, legs pulled apart",
    "from front, pov, full body",
  ),
  h6p_m_legs_chair: solo(
    "M字開脚（椅子に座って）",
    "sitting on chair, m legs, legs spread on armrests, spread legs",
    "from front, full body",
  ),
  h6p_m_legs_sofa: solo(
    "M字開脚（ソファの上）",
    "sitting on sofa, m legs, legs spread wide, leaning back, spread legs",
    "from front, full body",
  ),
  h6p_m_legs_mirror: solo(
    "M字開脚（鏡の前）",
    "sitting in front of mirror, m legs, spread legs, reflection in mirror",
    "from front, mirror, full body",
  ),
  h6p_m_legs_panties_aside: solo(
    "M字開脚（下着をずらす）",
    "lying on back, m legs, pulling panties aside with one hand, spread legs",
    "from front, pov, from above",
  ),
  h6p_m_legs_tiptoe: solo(
    "M字開脚（つま先立ち・しゃがむ）",
    "squatting, m legs, spread legs on tiptoes, knees wide apart",
    "from front, full body",
  ),
  h6p_m_legs_bridge: solo(
    "M字開脚（腰を浮かせる）",
    "lying on back, hips raised, m legs, knees apart, feet flat",
    "from front, full body, from side",
  ),
  h6p_kupaa_two_fingers: solo(
    "くぱぁ（片手の人差し指と中指で広げる）",
    "lying on back, m legs, spreading pussy with one hand using index and middle finger, spread pussy, pussy spread open",
    "from front, pov, close-up, from above",
  ),
  h6p_kupaa_both_index: solo(
    "くぱぁ（両手の人差し指で広げる）",
    "lying on back, m legs, spreading pussy with both hands using only the index fingers, spread pussy, pussy spread open, both hands",
    "from front, pov, close-up, from above",
  ),
  h6p_kupaa_both_hands: solo(
    "くぱぁ（両手で大きく広げる）",
    "lying on back, m legs, spreading pussy wide with both hands, spread pussy",
    "from front, pov, close-up",
  ),
  h6p_kupaa_sitting: solo(
    "くぱぁ（座って片手で広げる）",
    "sitting, m legs, spreading pussy with one hand using two fingers, spread pussy",
    "from front, close-up",
  ),
  h6p_kupaa_standing_aside: solo(
    "くぱぁ（立って下着をずらして広げる）",
    "standing, one leg raised, pulling panties aside and spreading pussy with two fingers, spread pussy",
    "from front, full body",
  ),
  h6p_kupaa_from_behind: solo(
    "くぱぁ（四つん這いで後ろから）",
    "on all fours, looking back, spreading pussy with one hand using two fingers, spread pussy, from behind",
    "from behind, from below, close-up",
  ),
  h6p_kupaa_bed_blush: solo(
    "くぱぁ（顔を赤くしながら）",
    "lying on bed, m legs, spreading pussy with two fingers, blushing, looking away, embarrassed, spread pussy",
    "from front, pov, from above",
  ),
  h6p_kupaa_wet: solo(
    "くぱぁ（濡れているところを見せる）",
    "lying on back, m legs, spreading pussy with two fingers, wet pussy, pussy juice, spread pussy",
    "from front, pov, close-up",
  ),
  h6p_hogtie_floor: solo(
    "手足をまとめて縛る（床・同意の上）",
    "hogtied, wrists and ankles tied together behind her back with rope, lying on stomach on the floor, bondage, consensual play",
    "from side, full body",
  ),
  h6p_hogtie_bed: solo(
    "手足をまとめて縛る（ベッドの上）",
    "hogtied on bed, wrists and ankles bound together with soft rope, lying on side, bondage, consensual",
    "from front, full body, from above",
  ),
  h6p_hogtie_kneeling: solo(
    "手足をまとめて縛る（ひざ立ち）",
    "kneeling with ankles and wrists tied together with rope, hands behind back, bondage, consensual",
    "from front, full body",
  ),
  h6p_frogtie: solo(
    "かえる縛り（足を折りたたんで縛る）",
    "frogtie, thighs tied to calves with rope, lying on back, legs folded and spread, bondage, consensual",
    "from front, from above, full body",
  ),
  h6p_box_tie: solo(
    "後ろ手に縛る（箱縛り）",
    "box tie, arms bound behind back with rope, standing, bondage, consensual",
    "from side, full body",
  ),
  h6p_car_back_seat: pose(
    "車の後部座席で",
    "in car back seat, lying on seat, legs up, sex in car, car interior",
    "from front, close, from above",
  ),
  h6p_car_hood: pose(
    "車のボンネットに手をついて",
    "bent over car hood, hands on car, from behind, standing sex, outdoors at night",
    "from behind, from side, wide shot",
  ),
  h6p_car_driver_seat: pose(
    "運転席にまたがって",
    "straddling on driver seat, cowgirl position in car, sitting on lap, steering wheel behind her",
    "from front, close, car interior",
  ),
  h6p_car_open_door: pose(
    "ドアを開けた車のそばで",
    "standing beside open car door, one leg on seat, sex in parking lot at night",
    "from side, wide shot",
  ),
  h6p_outdoor_tree: pose(
    "野外・木に手をついて",
    "bent over against a tree, hands on tree trunk, from behind, outdoors, standing sex",
    "from behind, from side, wide shot",
  ),
  h6p_outdoor_grass: pose(
    "野外・草の上で",
    "lying on grass, missionary position, outdoors, sunlight through trees",
    "from side, wide shot",
  ),
  h6p_outdoor_rain: pose(
    "雨の中で",
    "standing in the rain, soaked clothes, embracing, sex outdoors in the rain",
    "from side, wide shot",
  ),
  h6p_outdoor_bench: pose(
    "公園のベンチで（夜）",
    "sitting on park bench, straddling lap, cowgirl position outdoors at night",
    "from side, wide shot",
  ),
  h6p_outdoor_beach: pose(
    "砂浜の岩かげで",
    "behind rocks on the beach, lying on sand, missionary, outdoors",
    "from side, wide shot",
  ),
  h6p_closet_standing: pose(
    "押入れの中で（立って）",
    "inside a closet, standing, pressed against the wall, tight space, from behind",
    "from side, close, dark room",
  ),
  h6p_closet_lying: pose(
    "押入れの中で（重なって）",
    "inside a closet, lying close together, futon, tight space, missionary",
    "from above, close, dim light",
  ),
  h6p_closet_hiding: pose(
    "押入れで息をひそめて",
    "hiding together inside a closet, bodies pressed close, whispering, tight space",
    "from front, close, dim light",
  ),
} satisfies Record<string, MotionTemplate>;

export type MotionTemplateId = keyof typeof MOTION_TEMPLATES;
export const MOTION_TEMPLATE_IDS = Object.keys(MOTION_TEMPLATES) as MotionTemplateId[];

function templateOf(id?: string): MotionTemplate {
  return MOTION_TEMPLATES[(id && id in MOTION_TEMPLATES ? id : "missionary") as MotionTemplateId];
}

/**
 * Options added on top of a template, any number of them: what she wears or carries, marks on
 * her body, her state. Each adds image tags (all key images, or only the climax ones), a phrase
 * to the motion of the loops, and a note for the script writer.
 */
export type MotionOption = {
  label: string;
  group: string;
  tags: string;
  /** Only on the climax and after pictures (k2, k4) and their cuts. */
  climaxOnly?: boolean;
  motion?: string;
  note?: string;
  /** Extra tags for the pulled-out picture (k4), e.g. blood mixed with the cum. */
  pulloutTags?: string;
};

export const MOTION_OPTIONS = {
  virgin: {
    label: "処女（血あり）",
    group: "体験",
    tags: "virgin, defloration, blood, blood on penis, blood on sheets, tears",
    motion:
      "she winces in pain at first and a little blood trickles where they are joined, then she slowly relaxes and starts to feel it",
    note: "彼女は初めて（処女）。最初は痛がって涙ぐみ、少し血が出る。だんだん痛みが快感に変わっていく流れのセリフにする。",
    pulloutTags: "pink cum, blood mixed with cum, blood on sheets",
  },
  navel_piercing: { label: "へそピアス", group: "ピアス", tags: "navel piercing" },
  ear_piercing: { label: "耳ピアス", group: "ピアス", tags: "earrings, ear piercing" },
  nipple_piercing: { label: "乳首ピアス", group: "ピアス", tags: "nipple piercing" },
  tongue_piercing: { label: "舌ピアス", group: "ピアス", tags: "tongue piercing" },
  lip_piercing: { label: "口ピアス", group: "ピアス", tags: "lip piercing" },
  womb_tattoo: {
    label: "淫紋",
    group: "体の印",
    tags: "womb tattoo, glowing pubic tattoo, magic circle on lower belly",
    motion: "the glowing womb tattoo on her lower belly pulses with pink light",
    note: "下腹部に淫紋があり、感じるほど光る。",
  },
  hickey: { label: "痣（キスマーク・あざ）", group: "体の印", tags: "hickey, kiss mark, bruise" },
  tattoo: { label: "タトゥー", group: "体の印", tags: "tattoo, arm tattoo, thigh tattoo" },
  body_writing: { label: "体に落書き", group: "体の印", tags: "body writing, tally" },
  tan_lines: { label: "日焼け跡", group: "体の印", tags: "tan, tanlines" },
  glasses: { label: "眼鏡", group: "小物", tags: "glasses" },
  collar: { label: "首輪", group: "小物", tags: "collar" },
  leash: {
    label: "首輪とリード",
    group: "小物",
    tags: "collar, leash, leash pull",
    motion: "the man pulls the leash on her collar",
  },
  eyepatch: { label: "眼帯", group: "小物", tags: "eyepatch" },
  blindfold: {
    label: "目隠し",
    group: "小物",
    tags: "blindfold",
    note: "目隠しをされていて見えない。",
  },
  ball_gag: {
    label: "ボールギャグ",
    group: "小物",
    tags: "ball gag, gag, drooling",
    motion: "she moans muffled through the ball gag, drool drips from her mouth",
    note: "ボールギャグで口がふさがれている。セリフは「んっ…んーっ…！」のようなくぐもった声だけにする。",
  },
  bound_wrists: {
    label: "手首の拘束",
    group: "拘束",
    tags: "bound wrists, arms up, restrained",
    motion: "her bound wrists strain against the restraints",
    note: "手首を拘束されていて動けない。",
  },
  shibari: {
    label: "縄（緊縛）",
    group: "拘束",
    tags: "shibari, rope, bound, bound breasts",
    motion: "the ropes dig into her skin as she squirms",
    note: "縄で縛られている。",
  },
  handcuffs: {
    label: "手錠",
    group: "拘束",
    tags: "handcuffs, cuffs, hands behind back",
    note: "手錠をかけられている。",
  },
  drool: {
    label: "ヨダレ",
    group: "状態",
    tags: "drooling, saliva, saliva trail",
    motion: "drool drips from her open mouth",
  },
  foam: {
    label: "口から泡",
    group: "状態",
    tags: "foaming at the mouth, saliva bubbles",
    climaxOnly: true,
    motion: "she foams at the mouth from too much pleasure",
  },
  faint: {
    label: "失神",
    group: "状態",
    tags: "fainted, unconscious, eyes rolled back, limp",
    climaxOnly: true,
    motion: "she faints from the pleasure and goes limp",
    note: "最後は気持ちよすぎて失神する。",
  },
  tears: {
    label: "涙",
    group: "状態",
    tags: "tears, crying, teary eyes",
    motion: "tears run down her cheeks",
  },
  sweaty: {
    label: "汗だく",
    group: "状態",
    tags: "very sweaty, sweat drops, wet skin, steam",
    motion: "sweat drips down her glistening skin",
  },
  oiled: {
    label: "ローションでぬるぬる",
    group: "状態",
    tags: "oiled skin, lotion, shiny skin, wet",
    motion: "her oiled skin glistens and slides",
  },
  lactation: {
    label: "母乳",
    group: "状態",
    tags: "lactation, breast milk",
    motion: "milk drips from her nipples as her breasts bounce",
  },
  cat_ears: {
    label: "猫耳としっぽ",
    group: "獣耳など",
    tags: "cat ears, cat tail",
    motion: "her cat ears twitch and her tail sways",
  },
  bunny_ears: { label: "うさ耳", group: "獣耳など", tags: "rabbit ears, bunny tail" },
  demon: {
    label: "悪魔の角としっぽ",
    group: "獣耳など",
    tags: "demon horns, demon tail, succubus",
    motion: "her demon tail curls and sways",
  },
  // ─ 拘束追加 ─
  spreader_bar: {
    label: "脚開き拘束",
    group: "拘束",
    tags: "spreader bar, legs spread, restrained legs",
    motion: "the spreader bar forces her legs wide apart",
    note: "脚を大きく開いた状態で固定されている。",
  },
  suspension: {
    label: "宙吊り",
    group: "拘束",
    tags: "suspension, hung up, rope suspension, shibari suspension",
    motion: "she hangs suspended in the air by the ropes, unable to touch the ground",
    note: "縄で宙吊りにされている。",
  },
  // ─ 体液追加 ─
  cum_on_body: {
    label: "外出し・ぶっかけ",
    group: "状態",
    tags: "cum on body, cum on breasts, cum on stomach, facial, multiple cumshots",
    motion: "thick white cum covers her body, dripping from her breasts and belly",
  },
  multiple_creampie: {
    label: "中出し連続",
    group: "状態",
    tags: "multiple creampie, dripping cum, overflowing cum",
    motion: "thick white cum overflows and drips down from inside her after repeated creampies",
    climaxOnly: true,
  },
  // ─ シチュエーション ─
  rape: {
    label: "レイプ・強制",
    group: "シチュエーション",
    tags: "forced, rape, struggling, tears, held down, resist",
    motion: "she struggles and cries, trying to resist but unable to stop him",
    note: "強制・レイプシーン。彼女は泣いて抵抗するが逃げられない。体が徐々に反応してしまい、羞恥と快感が混ざるセリフにする。",
  },
  sleep: {
    label: "睡眠姦",
    group: "シチュエーション",
    tags: "sleeping, unconscious, sleep, half-closed eyes, dazed",
    motion: "she stirs slightly in her sleep, soft unconscious moans escaping her lips",
    note: "彼女は眠っている。途中で半分目を覚ます場合は混乱した表情とセリフにする。",
  },
  voyeur: {
    label: "盗撮・のぞき",
    group: "シチュエーション",
    tags: "camera, voyeur, hidden camera, filming, recorded",
    motion: "she is unaware she is being watched and filmed",
    note: "のぞき・盗撮されているシーン。彼女は気づいていない。",
  },
  exhibition: {
    label: "露出・野外",
    group: "シチュエーション",
    tags: "outdoors, public, exhibitionism, park, alley, risky",
    motion: "the thrill of being caught outdoors makes her tremble with extra excitement",
    note: "屋外・公共の場での行為。見つかるスリルを感じながら。",
  },
  train_molestation: {
    label: "痴漢（電車）",
    group: "シチュエーション",
    tags: "crowded train, clothed, groped, train, rush hour, standing",
    motion: "she bites her lip hard to suppress her moans in the crowded train",
    note: "電車内での痴漢。声を出せない状況でセリフは心の声や抑えた声にする。",
  },
  blackmail: {
    label: "脅迫・弱みを握る",
    group: "シチュエーション",
    tags: "blackmail, coercion, reluctant, forced consent",
    motion: "she complies reluctantly, shame and helplessness in her eyes",
    note: "脅迫・弱みを握られて拒めない状況。渋々従うが羞恥心でいっぱいのセリフ。",
  },
  hypnosis: {
    label: "催眠",
    group: "シチュエーション",
    tags: "hypnotized, blank eyes, obedient, entranced, glazed eyes",
    motion: "she obeys mechanically with glazed hypnotized eyes",
    note: "催眠状態。命令に従順で、思考を奪われている様子のセリフ。",
  },
  drugged: {
    label: "媚薬",
    group: "シチュエーション",
    tags: "aphrodisiac, drugged, flushed, desperate, needy",
    motion: "the aphrodisiac makes her body burn with need and desire against her will",
    note: "媚薬を盛られている。体が燃えるように感じ、自分でも戸惑う。欲求が制御できないセリフ。",
  },
  mind_break: {
    label: "理性崩壊",
    group: "シチュエーション",
    tags: "mind break, ahegao, eyes rolled back, drooling, pleasure broken",
    motion:
      "her mind breaks from too much pleasure, she is reduced to ahegao and incoherent moaning",
    note: "理性が崩壊している。まともなセリフは言えず、快楽の言葉だけ。",
    climaxOnly: true,
  },
  ntr: {
    label: "寝取り・NTR",
    group: "シチュエーション",
    tags: "netorare, cheating, another man, infidelity, passionate",
    motion:
      "she moans that it feels so much better than her boyfriend as she clings to the other man",
    note: "寝取り・NTR。彼女は別の男と寝ている。彼氏より気持ちいいと感じている言動を入れる。",
  },
  cheating: {
    label: "浮気・不倫",
    group: "シチュエーション",
    tags: "affair, cheating, forbidden love, secret, passionate",
    motion: "the forbidden excitement of the affair makes every touch more intense",
    note: "不倫・浮気。禁断のスリルを感じながら。",
  },
  sugar_daddy: {
    label: "パパ活",
    group: "シチュエーション",
    tags: "sugar daddy, older man, hotel, luxury, money",
    motion: "she performs eagerly for the wealthy older man who has paid for her time",
    note: "パパ活シーン。対価のある関係で従順に奉仕する。",
  },
  orgy: {
    label: "乱交・輪姦",
    group: "シチュエーション",
    tags: "gangbang, group sex, multiple men, orgy, surrounded, multiple penetration",
    motion: "she is overwhelmed by multiple men who use her simultaneously",
    note: "乱交・輪姦。複数の男に同時に使われ、頭が真っ白になっている。",
  },
  gangbang: {
    label: "ギャング姦",
    group: "シチュエーション",
    tags: "gangbang, multiple men, rough group sex, degrading",
    motion: "multiple men take her roughly from all sides simultaneously",
    note: "荒々しい集団プレイ。",
  },
  femdom: {
    label: "女王様・主従逆転",
    group: "シチュエーション",
    tags: "femdom, dominant woman, submissive man, riding, commanding",
    motion: "she rides him aggressively and commands him not to cum without her permission",
    note: "女王様プレイ。彼女が支配する立場。命令口調で高圧的なセリフ。",
  },
  peeping: {
    label: "のぞき・隠し撮り",
    group: "シチュエーション",
    tags: "peeping, voyeur, hidden camera, watching, secret filming",
    motion: "she is completely unaware of the hidden camera capturing her most intimate moments",
    note: "隠し撮り・のぞきシーン。気づかれていない演出。",
  },
  prostitution: {
    label: "売春・援助交際",
    group: "シチュエーション",
    tags: "prostitution, escort, paid sex, hotel, customer",
    motion: "she performs the requested acts professionally but with growing real pleasure",
    note: "売春・援助交際シーン。仕事としての行為だが次第に本気になる流れ。",
  },
  office: {
    label: "オフィス・職場",
    group: "シチュエーション",
    tags: "office, workplace, desk, business attire, secretary, boss",
    motion: "they have secret office sex while trying to avoid being caught by colleagues",
    note: "オフィス・職場での秘密の行為。見つかりそうでドキドキするシーン。",
  },
  teacher_student: {
    label: "先生と生徒（成人）",
    group: "シチュエーション",
    tags: "teacher and student, college, adult student, forbidden",
    motion:
      "the forbidden teacher-student relationship makes every touch feel more taboo and electric",
    note: "先生と成人生徒の禁断の関係。成人設定必須。",
  },
  doctor: {
    label: "診察・医師",
    group: "シチュエーション",
    tags: "doctor, medical, clinic, examination, white coat",
    motion: "what starts as an examination escalates into something far more intimate",
    note: "医師と患者の禁断シーン。診察から始まる展開。",
  },
  nurse: {
    label: "ナース",
    group: "シチュエーション",
    tags: "nurse, hospital, uniform, white stockings, medical",
    motion: "the nurse tends to her patient in ways far beyond her duties",
    note: "ナースコスプレ・ナース役割プレイ。",
  },
  maid: {
    label: "メイド",
    group: "シチュエーション",
    tags: "maid, maid uniform, frilly dress, serving, obedient",
    motion: "she curtsies and obediently serves her master in every way he desires",
    note: "メイドシーン。ご主人様に従順に仕えるセリフ。",
  },
  // ─ 調教・支配 ─
  bdsm: {
    label: "BDSM総合",
    group: "調教・支配",
    tags: "bdsm, bondage, domination, submission, sadism, masochism",
    motion: "the dominant partner controls every sensation and response of their submissive",
    note: "BDSM総合シーン。支配と服従の関係。命令と従順さを表現。",
  },
  pet_play: {
    label: "ペット調教",
    group: "調教・支配",
    tags: "pet play, on all fours, collar, leash, acting like a pet, obedient",
    motion: "she crawls on all fours like a pet and obeys every command of her owner",
    note: "ペット調教。四つん這いで主人の命令に従う。動物のような声と行動も混ぜる。",
  },
  ahegao_training: {
    label: "アヘ顔調教",
    group: "調教・支配",
    tags: "ahegao training, forced orgasm, pleasure conditioning",
    motion: "she is trained to make the ahegao face on command, tongue out, eyes rolling",
    note: "アヘ顔になるように調教されている。絶頂のたびにアヘ顔を強制される。",
  },
  creampie_training: {
    label: "中出し調教",
    group: "調教・支配",
    tags: "creampie, trained, conditioned, bred",
    motion: "she has been trained to beg for creampies and love the feeling of being filled",
    note: "中出しを求めるように調教されている。内に出されることへの渇望を表現。",
  },
  orgasm_denial: {
    label: "絶頂禁止",
    group: "調教・支配",
    tags: "orgasm denial, edging, forbidden, desperately holding back",
    motion: "she desperately fights the orgasm, trembling and whimpering, forbidden to cum",
    note: "絶頂を禁止されている。イキそうになっても我慢するセリフ。",
  },
  forced_orgasm: {
    label: "強制絶頂",
    group: "調教・支配",
    tags: "forced orgasm, overwhelmed, continuous orgasm, too much pleasure",
    motion:
      "she is forced into continuous orgasms one after another, overwhelmed and begging to stop",
    note: "強制的に絶頂させられ続ける。もう許してというセリフ。",
    climaxOnly: true,
  },
  // ─ 体験追加 ─
  first_anal: {
    label: "アナル初体験",
    group: "体験",
    tags: "first anal, anal virgin, tight, painful at first, anal",
    motion: "she winces at the tight unfamiliar sensation of her first anal experience",
    note: "初めてのアナル体験。最初の違和感と痛みが快感に変わる流れ。",
  },
  first_time: {
    label: "初体験・初エッチ",
    group: "体験",
    tags: "first time, first sex, virgin, inexperienced, nervous",
    motion: "she is nervous and inexperienced for her very first sexual experience",
    note: "初めてのSEX体験。初めて特有の緊張と戸惑い、だんだん感じていく展開。",
  },
  shaved: { label: "パイパン", group: "毛", tags: "shaved pussy, hairless" },
  pubic_hair: { label: "陰毛あり", group: "毛", tags: "pubic hair" },
  // ─ SM・グッズ・表情（カスタムメイドの分類を参考に追加）─
  pillory: {
    label: "ギロチン拘束（首と手首を固定）",
    group: "拘束",
    tags: "pillory, stocks, head and wrists locked in wooden stocks, bent over",
    motion: "she is locked in the wooden stocks, unable to move, her hips pushed out",
    note: "首と手首を板で固定されて動けない。",
  },
  x_frame: {
    label: "X字の磔台",
    group: "拘束",
    tags: "St. Andrew's cross, X-frame, arms and legs spread and strapped, standing restrained",
    motion: "she strains against the straps of the cross",
    note: "X字の台に手足を開いて固定されている。",
  },
  armbinder: {
    label: "アームバインダー",
    group: "拘束",
    tags: "armbinder, arms bound behind back in a single glove, leather restraint",
    motion: "she squirms with her arms locked behind her back",
    note: "両腕を後ろでまとめて固定されている。",
  },
  restraint_chair: {
    label: "拘束椅子",
    group: "拘束",
    tags: "restraint chair, strapped to chair, legs spread on chair arms, leather straps",
    motion: "she is strapped into the chair with her legs held open",
    note: "椅子に手足を固定されている。",
  },
  wooden_horse: {
    label: "三角木馬",
    group: "拘束",
    tags: "wooden horse, straddling a triangular wooden horse, legs weighted, bound hands",
    motion: "she trembles on the wooden horse, rocking slightly",
    note: "三角木馬にまたがらされている。",
  },
  bondage_tape: {
    label: "ボンデージテープ・ラップ",
    group: "拘束",
    tags: "bondage tape, wrapped in tape, mummified, tape over body",
    motion: "she squirms wrapped in tape",
    note: "テープで全身をぐるぐる巻きにされている。",
  },
  body_harness: {
    label: "ボディハーネス（革ベルト）",
    group: "拘束",
    tags: "body harness, leather harness, straps across chest and hips",
    motion: "the leather harness creaks as she moves",
  },
  ring_gag: {
    label: "口枷（開口器）",
    group: "拘束",
    tags: "ring gag, mouth held open, drooling",
    motion: "she drools through the open ring gag",
    note: "口を開いたまま閉じられない。",
  },
  chains: {
    label: "鎖・足枷",
    group: "拘束",
    tags: "chains, shackles, ankle chains, wrist chains",
    motion: "the chains clink as she struggles",
    note: "鎖と足枷でつながれている。",
  },
  whip: {
    label: "鞭",
    group: "SM・責め",
    tags: "whip, being whipped, red marks on skin, leather whip",
    motion: "the whip cracks and she flinches, red marks appear",
    note: "鞭で打たれる。痛みと快感が混ざる。",
  },
  riding_crop: {
    label: "乗馬鞭",
    group: "SM・責め",
    tags: "riding crop, crop, teasing with a crop",
    motion: "the crop taps her skin and she shivers",
    note: "乗馬鞭で軽く打たれたり、なぞられたりする。",
  },
  paddle_spank: {
    label: "スパンキング",
    group: "SM・責め",
    tags: "spanking, red buttocks, hand print on buttocks, paddle",
    motion: "each smack leaves a red mark and she gasps",
    note: "お尻を叩かれる。",
  },
  wax_play: {
    label: "ろうそく（低温）",
    group: "SM・責め",
    tags: "candle wax, wax play, dripping wax on skin, candles",
    motion: "hot wax drips on her skin and she shivers",
    note: "低温ろうそくのろうを垂らされる。",
  },
  feather_tickle: {
    label: "くすぐり・羽根",
    group: "SM・責め",
    tags: "feather tickling, ticklish, squirming, laughing and moaning",
    motion: "the feather tickles her skin and she squirms",
    note: "羽根でくすぐられて悶える。",
  },
  nipple_clamps: {
    label: "乳首クリップ",
    group: "SM・責め",
    tags: "nipple clamps, chain between clamps",
    motion: "the clamps pull on her nipples as the chain swings",
    note: "乳首クリップでつながれている。",
  },
  ice_play: {
    label: "氷責め",
    group: "SM・責め",
    tags: "ice play, ice cube on skin, melting ice, shivering",
    motion: "an ice cube slides over her skin and she shivers",
    note: "氷を肌に滑らせる。",
  },
  vibrator: {
    label: "バイブ",
    group: "グッズ",
    tags: "vibrator, sex toy, vibrating toy",
    motion: "the vibrator hums and her body twitches",
    note: "バイブを使われている。",
  },
  wand_massager: {
    label: "電マ",
    group: "グッズ",
    tags: "wand massager, magic wand, vibrating wand pressed against her",
    motion: "the wand massager buzzes and she arches her back",
    note: "電マを当てられて身をよじる。",
  },
  rotor: {
    label: "ローター",
    group: "グッズ",
    tags: "bullet vibrator, egg vibrator, remote vibrator, vibrator wire",
    motion: "the rotor buzzes inside and she bites her lip",
    note: "ローターを入れられ、リモコンで操作される。",
  },
  dildo: {
    label: "ディルド",
    group: "グッズ",
    tags: "dildo, sex toy",
    motion: "she is penetrated with the toy and moans",
  },
  butt_plug: {
    label: "アナルプラグ（しっぽ付き含む）",
    group: "グッズ",
    tags: "butt plug, anal plug, tail plug, fox tail plug",
    motion: "the plug tail sways as she moves",
  },
  chastity_belt: {
    label: "貞操帯",
    group: "グッズ",
    tags: "chastity belt, locked chastity device, key",
    motion: "she squirms helplessly in the locked chastity belt",
    note: "貞操帯をつけられて、触れられない。",
  },
  aphrodisiac_cream: {
    label: "媚薬クリーム・ローション",
    group: "グッズ",
    tags: "aphrodisiac cream, applying lotion, glistening, flushed",
    motion: "the cream makes her skin tingle and her face flush",
  },
  aphrodisiac: {
    label: "媚薬で発情",
    group: "状態",
    tags: "aphrodisiac, in heat, flushed face, heavy breathing, trembling, dazed eyes, sweating",
    motion: "her body burns from the aphrodisiac, she trembles and pants",
    note: "媚薬を飲まされて発情している。理性が溶けていくセリフ。",
  },
  exhausted: {
    label: "ぐったり（絶頂後）",
    group: "状態",
    tags: "exhausted, limp, lying spent, afterglow, heavy breathing, messy hair",
    climaxOnly: true,
    motion: "she lies limp and panting after the climax",
    note: "絶頂のあと、動けないほどぐったりしている。",
  },
  heart_eyes: {
    label: "ハートの目",
    group: "状態",
    tags: "heart-shaped pupils, heart eyes, blushing, lovestruck",
    motion: "her heart-shaped eyes sparkle with pleasure",
  },
  face_ahegao: {
    label: "アヘ顔（舌出し・白目寄り）",
    group: "表情",
    tags: "ahegao, tongue out, eyes rolled up, drooling, blushing, open mouth",
    motion: "her face melts into an ahegao, tongue out and eyes rolling up",
    note: "アヘ顔で喘ぐ。",
  },
  face_melted: {
    label: "とろけ顔（半開きの目）",
    group: "表情",
    tags: "melted face, half-closed eyes, dazed, blushing, parted lips, hazy eyes",
    motion: "her half-closed eyes grow hazy, lips parted",
  },
  face_flushed: {
    label: "上気した頬・潤んだ瞳",
    group: "表情",
    tags: "flushed cheeks, teary eyes, wet eyes, blushing deeply, heavy breathing",
    motion: "her cheeks flush and her eyes glisten",
  },
  face_bite_lip: {
    label: "唇を噛んで耐える",
    group: "表情",
    tags: "biting lip, enduring pleasure, furrowed brows, blushing, eyes shut tight",
    motion: "she bites her lip to hold back her voice",
    note: "声を押し殺して耐える。",
  },
  face_climax: {
    label: "絶頂の顔",
    group: "表情",
    tags: "orgasm face, eyes closed tight, mouth open, back arched, trembling, blushing",
    climaxOnly: true,
    motion: "her face tightens as the orgasm hits and her whole body trembles",
    note: "絶頂の顔。",
  },
  face_seductive: {
    label: "誘う目つき・挑発の笑み",
    group: "表情",
    tags: "seductive smile, half-lidded eyes, smirk, looking at viewer, inviting look",
    motion: "she smiles seductively with half-lidded eyes",
  },
  face_contempt: {
    label: "見下す目つき",
    group: "表情",
    tags: "looking down on viewer, contemptuous gaze, cold eyes, smug smile, from below",
    motion: "she looks down with cold contempt",
  },
  // ─ プレイ（追加2）─
  squirt: {
    label: "潮吹き",
    group: "プレイ（追加2）",
    tags: "squirting, female ejaculation, wet, fluids splashing",
    climaxOnly: true,
    motion: "she squirts, fluid splashing as her whole body trembles",
    note: "潮を吹く。",
  },
  pregnant: {
    label: "妊娠（おなかが大きい）",
    group: "プレイ（追加2）",
    tags: "pregnant, large pregnant belly",
    motion: "she holds her large belly with one hand while she moves",
    note: "妊娠している（おなかが大きい）。",
  },
  self_nipple_play: {
    label: "自分で乳首をいじる",
    group: "プレイ（追加2）",
    tags: "playing with own nipples, pinching own nipples",
    motion: "she pinches and rolls her own nipples while moaning",
    note: "自分で乳首をいじりながら感じている。",
  },
  self_breast_play: {
    label: "自分で胸をいじる",
    group: "プレイ（追加2）",
    tags: "groping own breasts, squeezing own breasts",
    motion: "she squeezes and gropes her own breasts",
    note: "自分で胸を揉みながら感じている。",
  },
  fingering: {
    label: "手マン",
    group: "プレイ（追加2）",
    tags: "fingering, man's hand between her legs, wet sounds",
    motion: "his fingers move inside her and she arches her back",
    note: "指で責められて感じている。",
  },
  clit_piercing: {
    label: "クリトリスピアス",
    group: "プレイ（追加2）",
    tags: "clitoris piercing, clit piercing",
    motion: "the piercing glints as she trembles",
  },
  double_peace: {
    label: "ダブルピース",
    group: "プレイ（追加2）",
    tags: "double peace sign, v sign with both hands, smiling, tongue out",
    motion: "she makes a double peace sign with a dazed smile",
    note: "ダブルピースで笑う。",
  },
  anal_training: {
    label: "アナル開発",
    group: "プレイ（追加2）",
    tags: "anal training, anal beads, butt plug, stretched",
    motion: "she squirms as the beads are slowly pushed in and pulled out",
    note: "アナルを少しずつ開発されている。",
  },
  swallow: {
    label: "ごっくん",
    group: "プレイ（追加2）",
    tags: "swallowing cum, cum in mouth, gulping, throat moving",
    climaxOnly: true,
    motion: "she swallows the cum, her throat moving, and shows her empty mouth",
    note: "出されたものを飲み込む。",
  },
  breeding: {
    label: "孕ませ（中出し・種付け）",
    group: "プレイ（追加2）",
    tags: "creampie, cum inside, breeding, cum overflowing",
    climaxOnly: true,
    motion: "she trembles as it is filled deep inside, cum overflowing",
    note: "奥に出されて、孕ませられる流れ。",
  },
  breast_sucking: {
    label: "乳吸",
    group: "プレイ（追加2）",
    tags: "breast sucking, man sucking her nipple, hand on his head",
    motion: "he sucks her nipple and she hugs his head",
    note: "乳首を吸われている。",
  },
  enema: {
    label: "浣腸（プレイ）",
    group: "プレイ（追加2）",
    tags: "enema play, enema bag, embarrassed",
    motion: "she squirms with a flushed face",
    note: "浣腸のプレイ。恥ずかしがる。",
  },
  watersports: {
    label: "聖水（おしっこ）",
    group: "プレイ（追加2）",
    tags: "urination, golden shower, pee stream",
    motion: "the stream splashes as she blushes deeply",
    note: "おしっこを見せる／浴びるプレイ。",
  },
  invisible_man: {
    label: "男透明化（ペニスだけ見える）",
    group: "プレイ（追加2）",
    tags: "invisible man, only penis visible, no man's body, no man's face",
    motion: "only his penis is visible as it moves",
    note: "相手の男は透明で、ペニスだけが見える。",
  },
  pov_view: {
    label: "主観（POV）",
    group: "プレイ（追加2）",
    tags: "pov, first person view, looking at viewer",
    motion: "she looks straight at the viewer while it happens",
    note: "主観視点。カメラ目線。",
  },
  // ─ プレイ（追加3）─
  tease_play: {
    label: "焦らしプレイ",
    group: "プレイ（追加3）",
    tags: "teasing, edging, being denied touch, squirming, pleading eyes",
    motion: "she squirms as his touch stops just short, pleading with her eyes",
    note: "焦らされて、お願いしてしまう。",
  },
  blindfold_play: {
    label: "目隠しプレイ",
    group: "プレイ（追加3）",
    tags: "blindfolded, blindfold play, anticipating touch",
    motion: "she shivers at every unseen touch, her blindfold wet at the edges",
    note: "目隠しされて、次に何が来るか分からない。",
  },
  abandon_play: {
    label: "放置プレイ",
    group: "プレイ（追加3）",
    tags: "left alone tied up with a vibrator, waiting, squirming, flushed",
    motion: "she squirms alone with the vibrator humming, glancing at the door",
    note: "縛られて放置され、待たされている。",
  },
  dirty_talk: {
    label: "言葉責め",
    group: "プレイ（追加3）",
    tags: "man whispering in her ear, teased with words, embarrassed, blushing",
    motion: "he whispers teasing words in her ear and she blushes deeper",
    note: "耳元で言葉責めされる。恥ずかしがる。",
  },
  watched_play: {
    label: "視姦プレイ",
    group: "プレイ（追加3）",
    tags: "being watched, a man staring at her body, embarrassed, covering herself",
    motion: "she squirms under his stare, trying to cover herself",
    note: "じっと見られて、恥ずかしがる。",
  },
  brush_play: {
    label: "筆責め",
    group: "プレイ（追加3）",
    tags: "tickled with a calligraphy brush, shivering, goosebumps",
    motion: "the brush slides over her skin and she shivers with goosebumps",
    note: "筆で肌をなぞられて、ぞくぞくする。",
  },
  tickle_play: {
    label: "くすぐりプレイ",
    group: "プレイ（追加3）",
    tags: "tickling, laughing, squirming, tears of laughter",
    motion: "she laughs and squirms as he tickles her",
    note: "くすぐられて笑い転げる。",
  },
  shame_play: {
    label: "羞恥プレイ",
    group: "プレイ（追加3）",
    tags: "embarrassment play, being exposed, covering with hands, deep blush",
    motion: "she covers herself with trembling hands, blushing deeply",
    note: "恥ずかしい格好をさせられて、顔が赤い。",
  },
  remote_vibe_public: {
    label: "遠隔バイブ（外で）",
    group: "プレイ（追加3）",
    tags: "remote vibrator in public, hiding it, flushed, trying to look normal",
    motion: "she bites her lip and tries to look calm as the toy buzzes",
    note: "人前で遠隔バイブを入れられ、平静を装う。",
  },
  hold_back_climax: {
    label: "イキ我慢",
    group: "プレイ（追加3）",
    tags: "holding back climax, trembling, biting lip, clenched fists",
    motion: "she bites her lip and clenches her fists, trembling to hold back",
    note: "イクのを必死にこらえる。",
  },
  shaving_play: {
    label: "剃毛プレイ",
    group: "プレイ（追加3）",
    tags: "shaving play, shaving cream, razor, shaved pubic area",
    motion: "he carefully shaves her while she lies still and blushes",
    note: "剃毛される。恥ずかしがる。",
  },
  ejaculation_control: {
    label: "射精管理",
    group: "プレイ（追加3）",
    tags: "ejaculation control, hand on his penis, denial, male chastity",
    motion: "she holds him on the edge with a smug smile and denies release",
    note: "射精を管理して、焦らす。",
  },
  trampling: {
    label: "踏みつけプレイ",
    group: "プレイ（追加3）",
    tags: "trampling, stepping on him with a bare foot, foot on his chest, looking down",
    motion: "she presses her bare foot on his chest and looks down smiling",
    note: "足で踏みつけて、見下ろす。",
  },
  wrap_bondage: {
    label: "ラップ拘束",
    group: "プレイ（追加3）",
    tags: "wrapped in plastic wrap, mummified in cling film, bound",
    motion: "she squirms wrapped in clinging film",
    note: "ラップで巻かれて動けない。",
  },
  male_squirt: {
    label: "男の潮吹き",
    group: "プレイ（追加3）",
    tags: "male squirting, wet, fluids",
    climaxOnly: true,
    motion: "his body trembles as he squirts",
    note: "男が潮を吹く。",
  },
  mutual_masturbation: {
    label: "相互オナニー",
    group: "プレイ（追加3）",
    tags: "mutual masturbation, both touching themselves facing each other",
    motion: "they touch themselves while watching each other",
    note: "お互いを見ながら、オナニーする。",
  },
  cleaning_fellatio: {
    label: "お掃除フェラ（バキューム）",
    group: "プレイ（追加3）",
    tags: "post-sex cleaning fellatio, sucking the penis clean, hollowed cheeks",
    motion: "she sucks the penis clean with hollowed cheeks, looking up",
    note: "終わったあと、口できれいにする。",
  },
  vacuum_fellatio: {
    label: "バキュームフェラ",
    group: "プレイ（追加3）",
    tags: "vacuum fellatio, hollowed cheeks, lips pursed tightly around the penis",
    motion: "she sucks hard with hollowed cheeks and pursed lips",
    note: "強く吸うフェラ。",
  },
  irrumatio: {
    label: "イラマチオ",
    group: "プレイ（追加3）",
    tags: "deepthroat, penis pushed deep, hands on her head, drool",
    motion: "he moves deep as drool runs down her chin and she holds his thighs",
    note: "喉の奥まで入れられる（同意の上）。",
  },
  saliva_play: {
    label: "唾液プレイ",
    group: "プレイ（追加3）",
    tags: "saliva play, saliva strands, spit, kissing with tongues",
    motion: "saliva strings between their mouths as they kiss deeply",
    note: "唾液をたっぷり使う。",
  },
  ball_licking: {
    label: "玉舐め",
    group: "プレイ（追加3）",
    tags: "licking testicles, looking up while licking",
    motion: "she licks and kisses his testicles while looking up",
    note: "玉を舐める。",
  },
  tongue_cum: {
    label: "舌上射精",
    group: "プレイ（追加3）",
    tags: "cum on tongue, tongue out, showing cum in mouth",
    climaxOnly: true,
    motion: "cum lands on her outstretched tongue and she shows it",
    note: "舌の上に出される。",
  },
  cum_bubble: {
    label: "ザーメンぶくぶく",
    group: "プレイ（追加3）",
    tags: "cum bubble, blowing a bubble of cum from her mouth",
    climaxOnly: true,
    motion: "she blows a bubble from the cum in her mouth",
    note: "口の中の精液でふくらませる。",
  },
  toilet_play: {
    label: "トイレでプレイ",
    group: "プレイ（追加3）",
    tags: "sex in a public restroom stall, toilet cubicle",
    motion: "they keep their voices low in the cramped stall",
    note: "トイレの個室で、声を殺す。",
  },
  karaoke_play: {
    label: "カラオケボックス",
    group: "プレイ（追加3）",
    tags: "karaoke box, microphone, screen glow, private room",
    motion: "the screen glows as the song plays and she tries not to moan",
    note: "カラオケの個室で、声をこらえる。",
  },
  uterus_sex: {
    label: "ウテルスセックス（深い挿入）",
    group: "プレイ（追加3）",
    tags: "uterus sex, deep insertion, stomach bulge, x-ray view",
    motion: "her belly bulges with each deep thrust",
    note: "奥まで深く入る（空想的な描写）。",
  },
  love_doll_play: {
    label: "ラブドール化",
    group: "プレイ（追加3）",
    tags: "love doll, doll joints, vacant eyes, posed like a doll",
    motion: "she holds still like a doll, only her breath moving",
    note: "人形のように動かず、されるまま（同意の上）。",
  },
  nip_slip: {
    label: "ポロリ",
    group: "プレイ（追加3）",
    tags: "accidental nip slip, breast popping out of clothes, embarrassed",
    motion: "her breast pops out and she hurries to cover it",
    note: "ふとした拍子に、胸が出てしまう。",
  },
  coat_flash: {
    label: "裸コート",
    group: "プレイ（追加3）",
    tags: "trench coat only, naked under the coat, opening the coat",
    motion: "she opens her coat to show she is naked underneath",
    note: "コートの下は裸。",
  },
  scream_pleasure: {
    label: "快感の絶叫",
    group: "プレイ（追加3）",
    tags: "screaming in pleasure, mouth wide open, head thrown back, tears of pleasure",
    climaxOnly: true,
    motion: "she screams in pleasure with her head thrown back",
    note: "叫ぶほど感じている。",
  },
  milk_feeding: {
    label: "母乳飲食",
    group: "プレイ（追加3）",
    tags: "drinking breast milk, lactation play, milk dripping",
    motion: "milk drips as he drinks from her",
    note: "母乳を飲まれる。",
  },
  soap_play: {
    label: "ソープ（泡・マット）",
    group: "プレイ（追加3）",
    tags: "soapland, bath mat play, lotion, soap bubbles",
    motion: "she slides over him on the mat with lots of soap",
    note: "泡とローションのマットプレイ。",
  },
  deep_kiss: {
    label: "ディープキス",
    group: "プレイ（追加3）",
    tags: "deep kiss, french kiss, tongues touching, saliva trail",
    motion: "they kiss deeply with tongues entwined and a thread of saliva",
    note: "舌を絡める濃いキス。",
  },
  // ─ プレイ（追加4：SMグッズ）─
  nose_hook: {
    label: "鼻フック",
    group: "プレイ（追加4）",
    tags: "nose hook, nose pulled up by a hook, pig nose, humiliated",
    motion: "the hook pulls her nose up as she blushes and breathes through her mouth",
    note: "鼻フックで鼻を持ち上げられて、恥ずかしがる。",
  },
  mouth_spreader: {
    label: "強制開口器（開口マスク）",
    group: "プレイ（追加4）",
    tags: "mouth spreader, forced open mouth gag, ring gag with straps, drooling",
    motion: "her mouth is held wide open and drool runs down her chin",
    note: "口を開いたまま閉じられない（同意の上のプレイ）。",
  },
  tongue_clamp: {
    label: "舌クリップ",
    group: "プレイ（追加4）",
    tags: "tongue clamp, tongue pulled out by a clip, drooling",
    motion: "her clipped tongue hangs out as she drools and moans",
    note: "舌を挟まれて、しゃべれない。",
  },
  pony_play: {
    label: "ポニープレイ",
    group: "プレイ（追加4）",
    tags: "pony play gear, bit gag, reins, harness, tail plug, hoof boots, on all fours",
    motion: "she trots on all fours like a pony as the reins pull her head back",
    note: "馬のように四つん這いで扱われる（同意の上のロールプレイ）。",
  },
  pig_play: {
    label: "豚プレイ（鼻フック・しっぽ）",
    group: "プレイ（追加4）",
    tags: "pig play, nose hook, pig tail plug, on all fours, oinking",
    motion: "she snorts like a pig on all fours with her nose hooked up",
    note: "豚のように扱われるロールプレイ（同意の上）。",
  },
  human_furniture: {
    label: "人間椅子・家具プレイ",
    group: "プレイ（追加4）",
    tags: "human furniture, human chair, on all fours as a seat, man sitting on her back",
    motion: "she holds still on all fours as a seat, trembling under the weight",
    note: "人間椅子になる（同意の上のロールプレイ）。",
  },
  sensory_deprivation: {
    label: "感覚遮断（耳栓と目隠し）",
    group: "プレイ（追加4）",
    tags: "earplugs and blindfold, sensory deprivation, anticipating touch",
    motion: "she twitches at every touch she cannot see or hear coming",
    note: "見えない、聞こえない中で触られる。",
  },
  nipple_weights: {
    label: "乳首ウェイト（おもり）",
    group: "プレイ（追加4）",
    tags: "nipple weights, weighted nipple clamps, chains hanging",
    motion: "the weights tug her nipples as the chains swing",
    note: "乳首におもりをつけられている。",
  },
  clothespins_body: {
    label: "洗濯ばさみ（体につける）",
    group: "プレイ（追加4）",
    tags: "clothespins on skin, clothespins on nipples and body",
    motion: "she flinches as clothespins are clipped along her skin",
    note: "体に洗濯ばさみをいくつもつけられる。",
  },
  posture_collar: {
    label: "ネックコルセット（首を固定）",
    group: "プレイ（追加4）",
    tags: "posture collar, tall leather neck corset, chin raised",
    motion: "her chin stays lifted by the stiff collar as she breathes shallowly",
    note: "首を固定されて、あごが上がったまま。",
  },
  ballet_boots: {
    label: "バレエブーツ（つま先立ち）",
    group: "プレイ（追加4）",
    tags: "ballet boots, extreme heels, standing on tiptoes, unsteady",
    motion: "she wobbles on tiptoe in the extreme boots",
    note: "つま先立ちのブーツで、ふらつく。",
  },
  ball_chain: {
    label: "足かせの鉄球",
    group: "プレイ（追加4）",
    tags: "ball and chain on the ankle",
    motion: "the ball and chain drag and clank as she shifts",
    note: "足に鉄球をつけられている。",
  },
  // ─ シチュエーション（追加）─
  g4s_night_pool: {
    label: "ナイトプール",
    group: "シチュエーション（場所）",
    tags: "night pool, glowing pool lights, water reflections, swimsuit, wet skin",
    motion: "she leans on the pool edge as the lights ripple on the water",
    note: "夜のプールで、水面の照明が揺れている。",
  },
  g4s_onsen_open_air: {
    label: "露天風呂",
    group: "シチュエーション（場所）",
    tags: "open-air hot spring, steam, wooden bucket, stone rim, night sky, wet hair",
    motion: "she sinks into the hot water and lets out a sigh",
    note: "湯けむりの露天風呂で、肩まで湯につかる。",
  },
  g4s_private_onsen_room: {
    label: "部屋付き露天風呂",
    group: "シチュエーション（場所）",
    tags: "private open-air bath on balcony, steam, yukata nearby, lantern light",
    motion: "they soak together, her head resting on his shoulder",
    note: "客室専用の露天風呂で二人きり。",
  },
  g4s_love_hotel: {
    label: "ラブホテルの部屋",
    group: "シチュエーション（場所）",
    tags: "love hotel room, round bed, pink lighting, mirror ceiling, neon",
    motion: "she flops onto the big bed and laughs",
    note: "派手な照明のラブホテルの部屋。",
  },
  g4s_luxury_hotel_suite: {
    label: "高級ホテルのスイート",
    group: "シチュエーション（場所）",
    tags: "luxury hotel suite, city night view, king bed, champagne, silk robe",
    motion: "she stands at the window in a robe, glass in hand",
    note: "夜景が広がるスイートルーム。",
  },
  g4s_car_interior_night: {
    label: "夜の車内",
    group: "シチュエーション（場所）",
    tags: "inside parked car at night, dim dashboard light, steamy windows, back seat",
    motion: "she climbs over the seat toward him",
    note: "夜の駐車場に停めた車の中。",
  },
  g4s_car_rain_parking: {
    label: "雨の駐車場の車内",
    group: "シチュエーション（場所）",
    tags: "car interior, rain on windshield, blurred lights, close seats",
    motion: "the rain taps the glass as they lean closer",
    note: "雨音が響く車の中で身を寄せる。",
  },
  g4s_elevator_late: {
    label: "深夜のエレベーター",
    group: "シチュエーション（場所）",
    tags: "elevator, metal walls, mirror, dim light, formal wear",
    motion: "the doors close and she steps closer to him",
    note: "二人きりのエレベーターで距離が縮まる。",
  },
  g4s_rooftop_night: {
    label: "屋上の夜景",
    group: "シチュエーション（場所）",
    tags: "rooftop, city lights, night wind, railing, skyline",
    motion: "her hair blows in the wind as she looks at the lights",
    note: "ビルの屋上で夜景を見下ろす。",
  },
  g4s_photo_studio: {
    label: "撮影スタジオ",
    group: "シチュエーション（場所）",
    tags: "photo studio, softbox lights, backdrop paper, camera tripod, model pose",
    motion: "she changes poses under the bright lights",
    note: "ライトが当たる撮影スタジオ。",
  },
  g4s_backstage_live: {
    label: "ライブ会場の楽屋",
    group: "シチュエーション（場所）",
    tags: "backstage dressing room, makeup mirror with bulbs, stage costume, flowers",
    motion: "she sits at the mirror, still flushed after the show",
    note: "ライブ後の熱が残る楽屋。",
  },
  g4s_live_house_crowd: {
    label: "ライブハウス",
    group: "シチュエーション（場所）",
    tags: "live music club, stage lights, crowd, sweat, dim",
    motion: "she dances with the beat, sweat shining",
    note: "音と熱気にあふれるライブハウス。",
  },
  g4s_library_closed: {
    label: "閉館後の図書館",
    group: "シチュエーション（場所）",
    tags: "public library after hours, tall bookshelves, warm lamp, quiet, adult",
    motion: "she whispers between the shelves, finger on her lips",
    note: "人のいない書架の間で声をひそめる。",
  },
  g4s_office_after_hours: {
    label: "残業のオフィス",
    group: "シチュエーション（場所）",
    tags: "office after hours, empty desks, monitor glow, suit, city night window",
    motion: "she leans on the desk and loosens her tie",
    note: "誰もいない夜のオフィス。",
  },
  g4s_meeting_room: {
    label: "会議室",
    group: "シチュエーション（場所）",
    tags: "meeting room, long table, blinds, projector, business suit",
    motion: "she sits on the table edge, crossing her legs",
    note: "ブラインドを下ろした会議室。",
  },
  g4s_clinic_roleplay: {
    label: "診察室ごっこ",
    group: "シチュエーション（場所）",
    tags: "clinic room roleplay, white coat costume, examination bed, curtain, adults",
    motion: "she plays a doctor, stethoscope around her neck",
    note: "大人同士の診察ロールプレイ。",
  },
  g4s_nurse_station_play: {
    label: "ナース服のロールプレイ",
    group: "シチュエーション（場所）",
    tags: "nurse costume roleplay, adult, pastel room, stethoscope",
    motion: "she smiles and adjusts her cap",
    note: "ナース服で遊ぶ大人の役割演技。",
  },
  g4s_ryokan_room: {
    label: "旅館の和室",
    group: "シチュエーション（場所）",
    tags: "ryokan tatami room, futon, yukata, paper lantern, low table",
    motion: "she kneels on the tatami and pours tea",
    note: "畳と布団の静かな旅館の部屋。",
  },
  g4s_ryokan_corridor: {
    label: "旅館の廊下",
    group: "シチュエーション（場所）",
    tags: "ryokan wooden corridor, paper lanterns, slippers, yukata, night",
    motion: "she walks the creaking corridor in a loose yukata",
    note: "灯りの落ちた旅館の廊下。",
  },
  g4s_camp_tent: {
    label: "キャンプのテント",
    group: "シチュエーション（場所）",
    tags: "camping tent interior, lantern, sleeping bags, night forest sounds",
    motion: "they zip the tent and snuggle in the lantern light",
    note: "ランタンの灯るテントの中。",
  },
  g4s_campfire_night: {
    label: "焚き火の夜",
    group: "シチュエーション（場所）",
    tags: "campfire, night forest, blanket, sparks, flannel shirt",
    motion: "she warms her hands and leans against him",
    note: "焚き火のそばで寄り添う。",
  },
  g4s_beach_sunset: {
    label: "夕暮れのビーチ",
    group: "シチュエーション（場所）",
    tags: "beach at sunset, golden light, bikini, waves, bare feet",
    motion: "she walks along the waterline, sand on her feet",
    note: "夕日に染まる砂浜。",
  },
  g4s_private_beach: {
    label: "プライベートビーチ",
    group: "シチュエーション（場所）",
    tags: "secluded private beach, white sand, turquoise water, beach chair, swimsuit",
    motion: "she lies on a beach chair with sunglasses on",
    note: "人のいない南国のビーチ。",
  },
  g4s_yacht_deck: {
    label: "ヨットのデッキ",
    group: "シチュエーション（場所）",
    tags: "yacht deck, ocean horizon, sunbathing, white railing, swimsuit",
    motion: "she stretches on the deck cushion in the sun",
    note: "海の上のヨットで二人きり。",
  },
  g4s_yacht_cabin: {
    label: "ヨットの船室",
    group: "シチュエーション（場所）",
    tags: "yacht cabin, small bed, porthole, warm lamp, gentle sway",
    motion: "the cabin sways softly as she lies back",
    note: "波に揺れる船室のベッド。",
  },
  g4s_night_club_vip: {
    label: "クラブのVIPルーム",
    group: "シチュエーション（場所）",
    tags: "nightclub VIP room, neon lights, leather sofa, bass, cocktail dress",
    motion: "she sips a cocktail, eyes half closed",
    note: "音が遠く響くVIP席。",
  },
  g4s_dance_floor: {
    label: "クラブのダンスフロア",
    group: "シチュエーション（場所）",
    tags: "nightclub dance floor, strobe lights, crowd, sweat, party dress",
    motion: "she dances close, hips swaying",
    note: "人混みの中で体を寄せて踊る。",
  },
  g4s_tatami_room: {
    label: "和室の畳",
    group: "シチュエーション（場所）",
    tags: "japanese tatami room, shoji screen, zabuton, afternoon light, kimono",
    motion: "she kneels gracefully and straightens her sleeve",
    note: "障子越しの柔らかな光の和室。",
  },
  g4s_castle_chamber: {
    label: "城の寝室",
    group: "シチュエーション（場所）",
    tags: "castle bedroom, canopy bed, stone walls, candlelight, fantasy dress",
    motion: "she lifts the heavy curtain and smiles",
    note: "蝋燭が灯る西洋の城の寝室。",
  },
  g4s_amusement_park_night: {
    label: "夜の遊園地",
    group: "シチュエーション（場所）",
    tags: "amusement park at night, ferris wheel lights, carousel, couple",
    motion: "she looks up at the glittering wheel",
    note: "光に包まれた観覧車の下。",
  },
  g4s_ferris_wheel: {
    label: "観覧車のゴンドラ",
    group: "シチュエーション（場所）",
    tags: "ferris wheel gondola, city view, sunset, small space, couple",
    motion: "they sit close as the gondola rises",
    note: "ゴンドラの中の小さな密室。",
  },
  g4s_movie_theater: {
    label: "映画館の最後列",
    group: "シチュエーション（場所）",
    tags: "movie theater back row, dark, screen glow, popcorn, couple",
    motion: "she leans on his shoulder in the flickering light",
    note: "暗い映画館で肩を寄せる。",
  },
  g4s_gym_after_hours: {
    label: "閉店後のジム",
    group: "シチュエーション（場所）",
    tags: "gym after hours, mats, mirrors, sportswear, sweat",
    motion: "she wipes sweat with a towel in front of the mirror",
    note: "誰もいないジムのマットの上。",
  },
  g4s_yoga_studio: {
    label: "ヨガスタジオ",
    group: "シチュエーション（場所）",
    tags: "yoga studio, wooden floor, mirrors, yoga pants, soft light",
    motion: "she folds forward into a deep stretch",
    note: "静かなヨガスタジオ。",
  },
  g4s_sauna_room: {
    label: "サウナ室",
    group: "シチュエーション（場所）",
    tags: "sauna wooden room, steam, towel, sweat, dim light",
    motion: "she wraps a towel and wipes her neck",
    note: "汗が流れる木のサウナ室。",
  },
  g4s_wedding_afterparty: {
    label: "結婚式の二次会",
    group: "シチュエーション（場所）",
    tags: "wedding after-party, party dress, balloons, banquet hall, champagne",
    motion: "she tilts her glass with a flushed smile",
    note: "華やかなパーティー会場。",
  },
  g4s_home_party: {
    label: "ホームパーティー",
    group: "シチュエーション（場所）",
    tags: "home party, living room, wine glasses, dim lights, friends gone",
    motion: "she is the last guest, curled on the sofa",
    note: "パーティー後の静かなリビング。",
  },
  g4s_bar_counter: {
    label: "バーのカウンター",
    group: "シチュエーション（場所）",
    tags: "cocktail bar counter, dim light, bottles, jazz, evening dress",
    motion: "she swirls her drink and glances sideways",
    note: "薄暗いバーのカウンター席。",
  },
  g4s_izakaya_booth: {
    label: "居酒屋の個室",
    group: "シチュエーション（場所）",
    tags: "izakaya private booth, sake bottles, low light, flushed face",
    motion: "she rests her chin in her hand, cheeks red",
    note: "酔いがまわった個室。",
  },
  g4s_karaoke_room: {
    label: "カラオケの個室",
    group: "シチュエーション（場所）",
    tags: "karaoke room, colored lights, sofa, microphone, screen glow",
    motion: "she sings softly and sits close",
    note: "ミラーボールのカラオケ個室。",
  },
  g4s_bath_house: {
    label: "銭湯の貸切風呂",
    group: "シチュエーション（場所）",
    tags: "private bathhouse, tiled walls, wooden stool, steam, wash basin",
    motion: "she washes his back with a towel",
    note: "タイル張りの貸切風呂。",
  },
  g4s_bathroom_home: {
    label: "自宅の浴室",
    group: "シチュエーション（場所）",
    tags: "home bathroom, steam, bubble bath, candles, wet tiles",
    motion: "she lowers into the bubbles with a sigh",
    note: "泡風呂とキャンドルのバスルーム。",
  },
  g4s_bubble_bath: {
    label: "泡風呂",
    group: "シチュエーション（場所）",
    tags: "bubble bath, foam, candles, rose petals, bare shoulders",
    motion: "she blows foam from her hand",
    note: "泡に包まれた湯船。",
  },
  g4s_shower_room: {
    label: "シャワールーム",
    group: "シチュエーション（場所）",
    tags: "shower stall, running water, glass door fogged, wet skin",
    motion: "she tilts her head back under the water",
    note: "曇ったガラスのシャワールーム。",
  },
  g4s_kitchen_morning: {
    label: "朝のキッチン",
    group: "シチュエーション（場所）",
    tags: "morning kitchen, sunlight, apron, coffee, bare legs, oversized shirt",
    motion: "she pours coffee in his oversized shirt",
    note: "朝の光が差す台所。",
  },
  g4s_living_sofa: {
    label: "リビングのソファ",
    group: "シチュエーション（場所）",
    tags: "living room sofa, soft lamp, blanket, wine, rainy window",
    motion: "she tucks her feet and pats the cushion beside her",
    note: "ソファでくつろぐ夜。",
  },
  g4s_bedroom_dim: {
    label: "間接照明の寝室",
    group: "シチュエーション（場所）",
    tags: "bedroom, indirect lighting, white sheets, warm glow, pillows",
    motion: "she lies on her side facing him",
    note: "ほの暗い寝室のベッド。",
  },
  g4s_balcony_night: {
    label: "夜のバルコニー",
    group: "シチュエーション（場所）",
    tags: "balcony at night, city lights, railing, night robe, wind",
    motion: "she leans on the railing and the robe flutters",
    note: "夜風が吹くバルコニー。",
  },
  g4s_stairwell_emergency: {
    label: "非常階段",
    group: "シチュエーション（場所）",
    tags: "emergency stairwell, concrete walls, dim light, echo, couple",
    motion: "her footsteps echo as she leans on the wall",
    note: "人気のない非常階段。",
  },
  g4s_parking_garage: {
    label: "地下駐車場",
    group: "シチュエーション（場所）",
    tags: "underground parking, concrete pillars, fluorescent light, car",
    motion: "she presses her back against a pillar",
    note: "柱の影の地下駐車場。",
  },
  g4s_fitting_room: {
    label: "試着室",
    group: "シチュエーション（場所）",
    tags: "fitting room, curtain, mirror, new clothes, tight space",
    motion: "she peeks through the curtain and pulls him in",
    note: "カーテンの狭い試着室。",
  },
  g4s_hot_spring_town: {
    label: "温泉街の夜",
    group: "シチュエーション（場所）",
    tags: "hot spring town street at night, lanterns, yukata, wooden sandals",
    motion: "she walks in geta, yukata sleeves swaying",
    note: "浴衣で歩く温泉街。",
  },
  g4s_festival_shrine: {
    label: "祭りの神社の裏",
    group: "シチュエーション（場所）",
    tags: "festival shrine back path, lanterns, yukata, distant music, trees",
    motion: "she hides behind a tree, hearing the drums",
    note: "祭りの賑わいから離れた境内。",
  },
  g4s_garden_pavilion: {
    label: "庭園の東屋",
    group: "シチュエーション（場所）",
    tags: "japanese garden gazebo, pond, maple leaves, kimono",
    motion: "she sits quietly watching the pond",
    note: "池のある庭の東屋。",
  },
  g4s_bamboo_forest: {
    label: "竹林の小径",
    group: "シチュエーション（場所）",
    tags: "bamboo forest path, light through leaves, kimono, quiet",
    motion: "she walks slowly, hem brushing the leaves",
    note: "竹林を歩く静かな時間。",
  },
  g4s_greenhouse: {
    label: "温室",
    group: "シチュエーション（場所）",
    tags: "greenhouse, tropical plants, humid air, glass roof, sundress",
    motion: "she touches a leaf, sweat on her collarbone",
    note: "蒸し暑い植物園の温室。",
  },
  g4s_ice_skating_rink: {
    label: "スケートリンク",
    group: "シチュエーション（場所）",
    tags: "ice rink, soft lights, scarf, gloves, breath mist",
    motion: "she skates holding his hand",
    note: "白い息のスケートリンク。",
  },
  g4s_ski_lodge: {
    label: "スキー場のロッジ",
    group: "シチュエーション（場所）",
    tags: "ski lodge, fireplace, wool blanket, hot cocoa, snow window",
    motion: "she warms by the fire, cheeks red",
    note: "暖炉のあるロッジ。",
  },
  g4s_mountain_cabin: {
    label: "山小屋",
    group: "シチュエーション（場所）",
    tags: "mountain cabin, wood stove, bearskin-style rug, snow outside, warm light",
    motion: "she lies on the rug by the stove",
    note: "雪の山小屋の暖炉の前。",
  },
  g4s_space_station: {
    label: "宇宙ステーション",
    group: "シチュエーション（場所）",
    tags: "space station corridor, window with stars, bodysuit, zero gravity",
    motion: "she floats, hair spreading in the air",
    note: "星が見える無重力の部屋。",
  },
  g4s_cyberpunk_alley: {
    label: "ネオン街の路地",
    group: "シチュエーション（場所）",
    tags: "neon alley, rain, holograms, cyberpunk, jacket",
    motion: "she leans in a doorway lit by neon",
    note: "ネオンが滲む雨の路地。",
  },
  g4s_fantasy_inn: {
    label: "ファンタジーの宿屋",
    group: "シチュエーション（場所）",
    tags: "fantasy inn room, wooden beams, candle, bed, tavern sounds below",
    motion: "she unbuckles her armor on the bed",
    note: "冒険者の宿の一室。",
  },
  g4s_harem_tent: {
    label: "砂漠のテント",
    group: "シチュエーション（場所）",
    tags: "desert tent, silk cushions, lanterns, sheer curtains, dancer outfit",
    motion: "she dances slowly on the cushions",
    note: "絹のクッションが並ぶ天幕。",
  },
  g4s_train_sleeper: {
    label: "寝台列車の個室",
    group: "シチュエーション（場所）",
    tags: "sleeper train compartment, small bunk, window night scenery, lamp",
    motion: "she watches lights pass outside the window",
    note: "揺れる夜行列車の個室。",
  },
  g4s_airplane_lounge: {
    label: "飛行機のファーストクラス",
    group: "シチュエーション（場所）",
    tags: "first class airplane suite, privacy door, blanket, night flight, adult",
    motion: "she pulls the blanket over her legs",
    note: "個室のような機内。",
  },
  g4s_cruise_cabin: {
    label: "クルーズ船のキャビン",
    group: "シチュエーション（場所）",
    tags: "cruise ship cabin, balcony window, ocean night, wine",
    motion: "she opens the balcony door to the sea breeze",
    note: "海を見下ろす船室。",
  },
  g4s_art_museum_night: {
    label: "夜の美術館",
    group: "シチュエーション（場所）",
    tags: "art gallery after hours, spotlights, paintings, marble floor, evening dress",
    motion: "her heels echo as she gazes at a painting",
    note: "閉館後の静かな展示室。",
  },
  g4s_aquarium_dark: {
    label: "暗い水族館",
    group: "シチュエーション（場所）",
    tags: "aquarium tunnel, blue light, fish shadows, dim, couple",
    motion: "blue light ripples over her face",
    note: "青い光に包まれた水族館。",
  },
  g4s_planetarium: {
    label: "プラネタリウム",
    group: "シチュエーション（場所）",
    tags: "planetarium dome, reclining seats, stars projected, dark",
    motion: "she leans back and looks at the stars",
    note: "星空が映る暗いドーム。",
  },
  g4s_laundromat_night: {
    label: "深夜のコインランドリー",
    group: "シチュエーション（場所）",
    tags: "laundromat at night, dryers, fluorescent light, hoodie, bare legs",
    motion: "she sits on a dryer and swings her legs",
    note: "深夜の静かな洗濯機の音。",
  },
  g4s_convenience_night: {
    label: "深夜のコンビニ前",
    group: "シチュエーション（場所）",
    tags: "late night street, convenience store glow, hoodie, vending machine light",
    motion: "she holds a canned drink and smiles",
    note: "自販機の光の下。",
  },
  g4s_bookstore_cafe: {
    label: "夜のブックカフェ",
    group: "シチュエーション（場所）",
    tags: "book cafe at night, warm lamp, wooden shelves, coffee, sweater",
    motion: "she hides a smile behind a book",
    note: "ランプが灯る静かなカフェ。",
  },
  g4s_tea_ceremony_room: {
    label: "茶室",
    group: "シチュエーション（場所）",
    tags: "tea ceremony room, tatami, kimono, tea bowl, quiet",
    motion: "she whisks tea with a gentle hand",
    note: "静かな茶室で向き合う。",
  },
  g4s_dojo_empty: {
    label: "道場",
    group: "シチュエーション（場所）",
    tags: "empty dojo, wooden floor, practice wear, morning light, quiet",
    motion: "she ties her belt and bows",
    note: "朝の静かな道場。",
  },
  g4s_stage_theater: {
    label: "劇場の舞台袖",
    group: "シチュエーション（場所）",
    tags: "theater wings, curtains, stage lights, costume, dust in light",
    motion: "she waits behind the curtain, heart racing",
    note: "幕の裏の薄暗い空間。",
  },
  g4s_swimming_pool_private: {
    label: "貸切の室内プール",
    group: "シチュエーション（場所）",
    tags: "private indoor pool, bright water, tiled floor, swimsuit, echo",
    motion: "she dips her toes and kicks lightly",
    note: "貸切の室内プール。",
  },
  g4s_waterfall_pool: {
    label: "滝つぼ",
    group: "シチュエーション（場所）",
    tags: "waterfall pool, forest, mist, wet skin, bikini, sunlight",
    motion: "she steps into the mist and shivers",
    note: "水しぶきの滝つぼ。",
  },
  g4s_beach_house: {
    label: "海辺のコテージ",
    group: "シチュエーション（場所）",
    tags: "beach cottage, wooden deck, sheer curtains, ocean breeze, white sheets",
    motion: "curtains flutter as she lies on the bed",
    note: "潮風が入るコテージ。",
  },
  g4s_farm_barn: {
    label: "牧場の納屋",
    group: "シチュエーション（場所）",
    tags: "farm barn, hay bales, sunlight through slats, sundress, straw hat",
    motion: "she sits on hay with a straw hat",
    note: "干し草の香りの納屋。",
  },
  g4s_lighthouse: {
    label: "灯台",
    group: "シチュエーション（場所）",
    tags: "lighthouse top, sea wind, spiral stairs, night, beam of light",
    motion: "she stands in the sweeping beam",
    note: "海風が吹く灯台の上。",
  },
  g4s_boss_subordinate: {
    label: "上司と部下",
    group: "シチュエーション（関係・役割）",
    tags: "office, adult man and woman, business suit, tension, tie, desk",
    motion: "she hands him a document and lingers",
    note: "職場で立場が違う二人。",
  },
  g4s_female_boss: {
    label: "女上司と部下",
    group: "シチュエーション（関係・役割）",
    tags: "female boss, tight suit, glasses, office, commanding gaze",
    motion: "she taps her pen and smiles at him",
    note: "主導権を握る女上司。",
  },
  g4s_secretary_role: {
    label: "秘書",
    group: "シチュエーション（関係・役割）",
    tags: "secretary roleplay, pencil skirt, glasses, tablet, office",
    motion: "she leans over his desk to adjust his tie",
    note: "主人を支える秘書の役。",
  },
  g4s_housewife_neighbor: {
    label: "人妻（隣人）",
    group: "シチュエーション（関係・役割）",
    tags: "married woman, neighbor, apartment doorway, apron, wedding ring, adult",
    motion: "she opens the door with a hesitant smile",
    note: "隣の部屋の人妻という設定。",
  },
  g4s_older_sister_type: {
    label: "頼れるお姉さん",
    group: "シチュエーション（関係・役割）",
    tags: "older sister type, adult woman, relaxed smile, teasing, long hair",
    motion: "she ruffles his hair and teases him",
    note: "年上の余裕で甘やかす。",
  },
  g4s_maid_master: {
    label: "メイドと主人",
    group: "シチュエーション（関係・役割）",
    tags: "maid costume, mansion, master, apron, headband, adult",
    motion: "she bows deeply and offers tea",
    note: "主人に仕えるメイド。",
  },
  g4s_butler_lady: {
    label: "執事とお嬢様",
    group: "シチュエーション（関係・役割）",
    tags: "butler suit, white gloves, mansion, lady in dress, adult",
    motion: "he kneels and kisses her gloved hand",
    note: "仕える執事と気高い令嬢。",
  },
  g4s_master_servant: {
    label: "主従の契約",
    group: "シチュエーション（関係・役割）",
    tags: "master and servant, collar, kneeling, dim room, obedient, consensual",
    motion: "she kneels, waiting for his command",
    note: "合意の上の主従ロールプレイ。",
  },
  g4s_couple_lovers: {
    label: "恋人同士",
    group: "シチュエーション（関係・役割）",
    tags: "lovers, adult couple, holding hands, warm smile, casual clothes",
    motion: "she squeezes his hand and rests her head on him",
    note: "付き合っている二人の日常。",
  },
  g4s_newlyweds: {
    label: "新婚夫婦",
    group: "シチュエーション（関係・役割）",
    tags: "newlywed couple, apartment, wedding ring, apron, boxes, happy",
    motion: "she greets him with a hug at the door",
    note: "新居での甘い新婚生活。",
  },
  g4s_rival_tension: {
    label: "ライバル同士",
    group: "シチュエーション（関係・役割）",
    tags: "rivals, adult, competing, intense gaze, tension, close face",
    motion: "she glares up at him without backing down",
    note: "競い合う二人の火花。",
  },
  g4s_ex_boyfriend: {
    label: "元カレとの再会",
    group: "シチュエーション（関係・役割）",
    tags: "ex-boyfriend reunion, bar, dim light, old feelings, adult",
    motion: "she looks away, then back at him",
    note: "別れた相手とのもう一度。",
  },
  g4s_ex_girlfriend: {
    label: "元カノとの再会",
    group: "シチュエーション（関係・役割）",
    tags: "ex-girlfriend reunion, cafe, rain, old feelings, adult",
    motion: "she stirs her coffee, avoiding his eyes",
    note: "忘れられない元カノ。",
  },
  g4s_best_friend_sibling: {
    label: "親友の兄",
    group: "シチュエーション（関係・役割）",
    tags: "best friend's older brother, adult, living room, awkward, casual",
    motion: "she sits stiffly, avoiding eye contact",
    note: "親友の兄との微妙な空気。",
  },
  g4s_coworker_secret: {
    label: "同僚との秘密",
    group: "シチュエーション（関係・役割）",
    tags: "coworkers, office, secret glance, whisper, ID badge",
    motion: "she whispers by the copier and smiles",
    note: "職場の同僚との内緒の関係。",
  },
  g4s_business_trip: {
    label: "出張先の同室",
    group: "シチュエーション（関係・役割）",
    tags: "business trip, hotel twin room, suit, tired, adult colleagues",
    motion: "she kicks off her heels and sinks onto the bed",
    note: "出張先で同じ部屋になる。",
  },
  g4s_landlady: {
    label: "管理人のお姉さん",
    group: "シチュエーション（関係・役割）",
    tags: "apartment landlady, adult woman, apron, hallway, kind smile",
    motion: "she brings food and lingers at the door",
    note: "アパートの優しい管理人。",
  },
  g4s_bartender_guest: {
    label: "バーテンダーと客",
    group: "シチュエーション（関係・役割）",
    tags: "bartender, vest, shaker, bar, customer, adult",
    motion: "she slides a drink across the counter",
    note: "カウンター越しの距離。",
  },
  g4s_idol_manager: {
    label: "アイドルと担当者",
    group: "シチュエーション（関係・役割）",
    tags: "adult idol, stage costume, manager, backstage, secret",
    motion: "she takes off her wig and sighs",
    note: "ステージを降りた素顔の二人。",
  },
  g4s_actress_director: {
    label: "女優と監督",
    group: "シチュエーション（関係・役割）",
    tags: "actress, director, set, script, adult, late night",
    motion: "she rehearses her lines close to him",
    note: "撮影の合間に二人きり。",
  },
  g4s_model_photographer: {
    label: "モデルとカメラマン",
    group: "シチュエーション（関係・役割）",
    tags: "model, photographer, studio, camera, adult, intense gaze",
    motion: "she changes pose to his voice",
    note: "ファインダー越しの緊張。",
  },
  g4s_fortune_teller: {
    label: "占い師と相談者",
    group: "シチュエーション（関係・役割）",
    tags: "fortune teller, incense, veil, dim tent, adult, tarot",
    motion: "she turns a card and smiles knowingly",
    note: "占いの部屋で見つめ合う。",
  },
  g4s_princess_knight: {
    label: "姫と騎士",
    group: "シチュエーション（関係・役割）",
    tags: "princess in gown, knight in armor, castle, kneeling, adult",
    motion: "he kneels, she lifts his chin",
    note: "主君に忠誠を誓う騎士。",
  },
  g4s_witch_apprentice: {
    label: "魔女と弟子",
    group: "シチュエーション（関係・役割）",
    tags: "witch, hat, potion room, candlelight, adult apprentice",
    motion: "she leans in to whisper a spell",
    note: "魔女の部屋で秘密のレッスン。",
  },
  g4s_vampire_lover: {
    label: "吸血鬼と恋人",
    group: "シチュエーション（関係・役割）",
    tags: "vampire lover, gothic mansion, moonlight, fangs, adult, pale skin",
    motion: "she tilts her head, revealing her neck",
    note: "月夜の古い館で寄り添う。",
  },
  g4s_succubus_encounter: {
    label: "サキュバスとの出会い",
    group: "シチュエーション（関係・役割）",
    tags: "succubus, horns, tail, bedroom, moonlight, adult, playful",
    motion: "she curls her tail around his wrist",
    note: "誘うように微笑む悪魔。",
  },
  g4s_angel_demon: {
    label: "天使と悪魔",
    group: "シチュエーション（関係・役割）",
    tags: "angel and demon, wings, ruined church, adults, tension",
    motion: "she touches his horn gently",
    note: "相反する二人の距離。",
  },
  g4s_elf_adventurer: {
    label: "エルフと冒険者",
    group: "シチュエーション（関係・役割）",
    tags: "elf, pointed ears, forest camp, adventurer, adult, firelight",
    motion: "she braids her hair by the fire",
    note: "森の野営での夜。",
  },
  g4s_ninja_kunoichi: {
    label: "くノ一",
    group: "シチュエーション（関係・役割）",
    tags: "kunoichi, adult, ninja outfit, rooftop, moonlight, mask",
    motion: "she lowers her mask and smirks",
    note: "月夜の屋根の上。",
  },
  g4s_samurai_courtesan: {
    label: "侍と花魁",
    group: "シチュエーション（関係・役割）",
    tags: "oiran, elaborate kimono, hairpins, samurai, teahouse, adult",
    motion: "she fans herself and eyes him",
    note: "茶屋で出会う二人。",
  },
  g4s_queen_subject: {
    label: "女王様と下僕",
    group: "シチュエーション（関係・役割）",
    tags: "dominant queen, gown, throne, kneeling servant, consensual play",
    motion: "she rests her heel on his shoulder",
    note: "合意のうえの主従遊び。",
  },
  g4s_athlete_trainer: {
    label: "トレーナーとお客さん",
    group: "シチュエーション（関係・役割）",
    tags: "personal trainer, adult, gym, sportswear, close guidance",
    motion: "he adjusts her posture from behind",
    note: "ジムでの個人指導。",
  },
  g4s_yoga_instructor: {
    label: "ヨガのインストラクター",
    group: "シチュエーション（関係・役割）",
    tags: "yoga instructor, adult, studio, tight wear, guiding hands",
    motion: "she presses his back into a deeper stretch",
    note: "手取り足取りのレッスン。",
  },
  g4s_masseuse: {
    label: "マッサージ師",
    group: "シチュエーション（関係・役割）",
    tags: "masseuse, adult, oil, towel, dim spa room, relaxing",
    motion: "she oils her hands and begins on his shoulders",
    note: "薄暗いスパでのマッサージ。",
  },
  g4s_hairdresser: {
    label: "美容師",
    group: "シチュエーション（関係・役割）",
    tags: "hairdresser, adult, salon chair, cape, mirror, close",
    motion: "she combs his hair, leaning close",
    note: "鏡越しに目が合う。",
  },
  g4s_neighbor_widow: {
    label: "未亡人",
    group: "シチュエーション（関係・役割）",
    tags: "widow, adult woman, black dress, quiet house, lonely smile",
    motion: "she pours tea with a sad smile",
    note: "寂しさを抱えた静かな家。",
  },
  g4s_stepsister_adult: {
    label: "義姉（成人）",
    group: "シチュエーション（関係・役割）",
    tags: "adult stepsister-in-law, living room, relaxed clothes, awkward",
    motion: "she sits beside him on the sofa and sighs",
    note: "義理の姉との微妙な距離。",
  },
  g4s_cohabitation: {
    label: "同棲中の夜",
    group: "シチュエーション（関係・役割）",
    tags: "living together, adult couple, apartment, pajamas, late night",
    motion: "she crawls onto his lap with a yawn",
    note: "同棲している二人の夜。",
  },
  g4s_long_distance: {
    label: "遠距離恋愛の再会",
    group: "シチュエーション（関係・役割）",
    tags: "long distance couple reunion, airport, suitcase, running hug",
    motion: "she drops her bag and runs into his arms",
    note: "久しぶりに会えた二人。",
  },
  g4s_first_date_end: {
    label: "初デートの帰り道",
    group: "シチュエーション（関係・役割）",
    tags: "first date end, night street, nervous, adult, streetlights",
    motion: "she stops and fidgets with her bag",
    note: "別れ際の緊張。",
  },
  g4s_blind_date: {
    label: "お見合い",
    group: "シチュエーション（関係・役割）",
    tags: "arranged meeting, kimono, tea room, formal, nervous adults",
    motion: "she bows and peeks up shyly",
    note: "格式ばった出会い。",
  },
  g4s_honeymoon: {
    label: "ハネムーン",
    group: "シチュエーション（関係・役割）",
    tags: "honeymoon, resort villa, white sheets, flowers, ocean view",
    motion: "she sets the flowers on the bed",
    note: "二人きりの新婚旅行。",
  },
  g4s_anniversary_dinner: {
    label: "記念日のディナー",
    group: "シチュエーション（関係・役割）",
    tags: "anniversary dinner, candlelit table, evening dress, wine",
    motion: "she lifts her glass and gazes at him",
    note: "ろうそくの灯る食卓。",
  },
  g4s_rain_shelter: {
    label: "雨宿り",
    group: "シチュエーション（天気・時間）",
    tags: "rain, bus stop shelter, wet clothes, clinging fabric, puddles",
    motion: "she wrings out her hair, shivering",
    note: "びしょ濡れで軒下に逃げ込む。",
  },
  g4s_rainy_window: {
    label: "雨の窓辺",
    group: "シチュエーション（天気・時間）",
    tags: "rain on window, gray light, warm room, blanket, tea",
    motion: "she watches the rain, wrapped in a blanket",
    note: "雨音だけの静かな午後。",
  },
  g4s_snow_night: {
    label: "雪の夜",
    group: "シチュエーション（天気・時間）",
    tags: "snowy night, streetlight, scarf, breath mist, soft snow",
    motion: "she catches snowflakes on her gloves",
    note: "しんしんと雪が降る夜。",
  },
  g4s_snow_cabin: {
    label: "雪に閉ざされた小屋",
    group: "シチュエーション（天気・時間）",
    tags: "snowed-in cabin, fireplace, blankets, storm outside",
    motion: "she throws another log in the fire",
    note: "吹雪の中の二人きり。",
  },
  g4s_dusk_balcony: {
    label: "夕暮れのベランダ",
    group: "シチュエーション（天気・時間）",
    tags: "dusk, orange sky, balcony, soft breeze, silhouettes",
    motion: "she leans on the railing in the golden light",
    note: "夕焼けに染まる時間。",
  },
  g4s_sunset_beach_walk: {
    label: "夕日の浜辺の散歩",
    group: "シチュエーション（天気・時間）",
    tags: "sunset, beach walk, long shadows, holding hands",
    motion: "their shadows stretch across the sand",
    note: "二人の影が長く伸びる。",
  },
  g4s_midnight_city: {
    label: "真夜中の街",
    group: "シチュエーション（天気・時間）",
    tags: "midnight city street, empty road, neon, quiet, coat",
    motion: "she walks under the glowing signs",
    note: "眠った街を二人で歩く。",
  },
  g4s_fireworks_night: {
    label: "花火の夜",
    group: "シチュエーション（天気・時間）",
    tags: "fireworks, night sky, yukata, riverbank, colorful light on face",
    motion: "fireworks light up her face",
    note: "花火に照らされる横顔。",
  },
  g4s_summer_festival: {
    label: "夏祭り",
    group: "シチュエーション（天気・時間）",
    tags: "summer festival, yatai stalls, yukata, lanterns, crowd, shaved ice",
    motion: "she holds a candy apple and laughs",
    note: "屋台が並ぶ夏の夜。",
  },
  g4s_tanabata_night: {
    label: "七夕の夜",
    group: "シチュエーション（天気・時間）",
    tags: "tanabata, bamboo decoration, starry sky, yukata, wishes",
    motion: "she hangs a paper strip and looks up",
    note: "短冊と満天の星。",
  },
  g4s_cherry_blossom_night: {
    label: "夜桜",
    group: "シチュエーション（天気・時間）",
    tags: "night cherry blossoms, petals falling, lanterns, kimono",
    motion: "petals land in her hair",
    note: "夜桜の下で見つめ合う。",
  },
  g4s_autumn_leaves: {
    label: "紅葉の道",
    group: "シチュエーション（天気・時間）",
    tags: "autumn leaves, red maple path, scarf, afternoon light",
    motion: "she picks up a red leaf and smiles",
    note: "紅葉の中の散歩。",
  },
  g4s_new_year_shrine: {
    label: "初詣",
    group: "シチュエーション（天気・時間）",
    tags: "new year shrine visit, kimono, crowd, cold breath, lanterns",
    motion: "she blows on her hands and smiles",
    note: "新年の冷たい空気。",
  },
  g4s_christmas_night: {
    label: "クリスマスの夜",
    group: "シチュエーション（天気・時間）",
    tags: "christmas night, tree lights, santa-style outfit, snow, gifts",
    motion: "she tucks a ribbon into her hair",
    note: "イルミネーションの夜。",
  },
  g4s_valentine: {
    label: "バレンタイン",
    group: "シチュエーション（天気・時間）",
    tags: "valentine's day, chocolate, ribbon, apron, kitchen, shy",
    motion: "she holds out a box with both hands",
    note: "チョコを渡す緊張の瞬間。",
  },
  g4s_summer_heat: {
    label: "うだるような夏の日",
    group: "シチュエーション（天気・時間）",
    tags: "hot summer afternoon, cicadas, sweat, fan, tank top, tatami",
    motion: "she fans herself, sweat on her neck",
    note: "蝉の声が響く暑い昼。",
  },
  g4s_thunderstorm: {
    label: "雷雨の夜",
    group: "シチュエーション（天気・時間）",
    tags: "thunderstorm, lightning flash, dark room, window, rain",
    motion: "she flinches at thunder and clings to him",
    note: "雷に驚いて抱きつく。",
  },
  g4s_typhoon_inside: {
    label: "台風の夜の室内",
    group: "シチュエーション（天気・時間）",
    tags: "typhoon night, candle, power outage, wind outside, blanket",
    motion: "candle light flickers on her face",
    note: "停電で灯りは蝋燭だけ。",
  },
  g4s_power_outage: {
    label: "停電",
    group: "シチュエーション（天気・時間）",
    tags: "power outage, dark apartment, flashlight, close, whisper",
    motion: "she holds a flashlight under her chin and laughs",
    note: "真っ暗な部屋の二人。",
  },
  g4s_morning_after: {
    label: "朝チュン",
    group: "シチュエーション（天気・時間）",
    tags: "morning after, sunlight through curtains, tangled sheets, sleepy smile, bare shoulders",
    motion: "she squints at the sun and burrows into the pillow",
    note: "朝の光とほどけたシーツ。",
  },
  g4s_early_morning_dawn: {
    label: "明け方の空",
    group: "シチュエーション（天気・時間）",
    tags: "dawn, pale sky, bedroom window, quiet, blanket",
    motion: "she sits up and watches the sky brighten",
    note: "うっすら明るい明け方。",
  },
  g4s_noon_siesta: {
    label: "昼寝の時間",
    group: "シチュエーション（天気・時間）",
    tags: "noon siesta, sunlit room, breeze, thin blanket, lazy",
    motion: "she dozes in a patch of sunlight",
    note: "陽だまりの昼寝。",
  },
  g4s_late_night_kitchen: {
    label: "真夜中の台所",
    group: "シチュエーション（天気・時間）",
    tags: "midnight kitchen, fridge light, oversized shirt, bare legs",
    motion: "she drinks water in the fridge glow",
    note: "冷蔵庫の光だけの台所。",
  },
  g4s_moonlit_room: {
    label: "月明かりの寝室",
    group: "シチュエーション（天気・時間）",
    tags: "moonlight through window, silhouette, white sheets, silent",
    motion: "moonlight outlines her figure",
    note: "青い月光の部屋。",
  },
  g4s_full_moon_garden: {
    label: "満月の庭",
    group: "シチュエーション（天気・時間）",
    tags: "full moon, garden, pond reflection, kimono, night breeze",
    motion: "she looks up at the moon, hair swaying",
    note: "満月に照らされる庭。",
  },
  g4s_starry_sky: {
    label: "満天の星空",
    group: "シチュエーション（天気・時間）",
    tags: "starry sky, grass field, blanket, night, lying side by side",
    motion: "they lie on the blanket counting stars",
    note: "草原に寝転んで星を見る。",
  },
  g4s_foggy_morning: {
    label: "霧の朝",
    group: "シチュエーション（天気・時間）",
    tags: "foggy morning, lake, soft light, sweater, quiet",
    motion: "she exhales into the mist",
    note: "霧の湖畔。",
  },
  g4s_spring_breeze: {
    label: "春の風",
    group: "シチュエーション（天気・時間）",
    tags: "spring breeze, flower field, sundress, soft light",
    motion: "the dress flutters as she spins",
    note: "やわらかな春風。",
  },
  g4s_rainy_season_damp: {
    label: "梅雨の湿った夜",
    group: "シチュエーション（天気・時間）",
    tags: "rainy season, humid night, open window, damp air, fan",
    motion: "she lifts her damp hair off her neck",
    note: "湿気のこもる夜。",
  },
  g4s_hot_night_tropical: {
    label: "熱帯夜",
    group: "シチュエーション（天気・時間）",
    tags: "tropical night, no air conditioning, sweat, thin sheets, fan",
    motion: "she kicks the sheet off, glistening with sweat",
    note: "寝苦しい暑い夜。",
  },
  g4s_winter_kotatsu: {
    label: "こたつの冬",
    group: "シチュエーション（天気・時間）",
    tags: "winter kotatsu, tangerines, blanket, living room, sweater",
    motion: "she lies half under the kotatsu, cheeks flushed",
    note: "こたつでぬくぬくする。",
  },
  g4s_new_years_eve: {
    label: "大晦日のカウントダウン",
    group: "シチュエーション（天気・時間）",
    tags: "new years eve countdown, crowd, fireworks, midnight, coat",
    motion: "she counts down and kisses at zero",
    note: "年が明ける瞬間。",
  },
  g4s_golden_hour_window: {
    label: "黄金色の午後",
    group: "シチュエーション（天気・時間）",
    tags: "golden hour, sunlight beam, dust motes, bedroom, white shirt",
    motion: "light shines through her loose shirt",
    note: "逆光の窓辺。",
  },
  g4s_sweet_mood: {
    label: "あまあまな雰囲気",
    group: "シチュエーション（雰囲気）",
    tags: "sweet mood, soft pastel light, smile, cuddling, blush",
    motion: "she nuzzles into his chest happily",
    note: "とろけるように甘い空気。",
  },
  g4s_intense_passion: {
    label: "激しく求め合う",
    group: "シチュエーション（雰囲気）",
    tags: "intense passion, sweat, tousled hair, heated gaze, dim light",
    motion: "she grips his shoulders, breath uneven",
    note: "息もつかせない激しさ。",
  },
  g4s_shy_bashful: {
    label: "恥じらい",
    group: "シチュエーション（雰囲気）",
    tags: "bashful, blushing, covering chest with arms, averted eyes",
    motion: "she hides her face with her hands",
    note: "頬を染めて目をそらす。",
  },
  g4s_taking_lead: {
    label: "主導権を握る",
    group: "シチュエーション（雰囲気）",
    tags: "taking lead, confident smile, pinning down, looking down, seductive",
    motion: "she pushes him back and smirks",
    note: "女性がリードする。",
  },
  g4s_teasing_slowly: {
    label: "焦らす",
    group: "シチュエーション（雰囲気）",
    tags: "teasing, slow touch, playful smile, fingertip, breathing",
    motion: "she traces a finger down and stops",
    note: "ぎりぎりまで焦らす。",
  },
  g4s_eye_contact: {
    label: "見つめ合う",
    group: "シチュエーション（雰囲気）",
    tags: "eye contact, close faces, soft light, breathing, silence",
    motion: "their eyes lock and neither looks away",
    note: "言葉もなく見つめ合う。",
  },
  g4s_whisper_ear: {
    label: "耳元で囁く",
    group: "シチュエーション（雰囲気）",
    tags: "whispering in ear, close face, hair brushing, blush, shiver",
    motion: "she shivers as his voice brushes her ear",
    note: "吐息が耳にかかる。",
  },
  g4s_princess_carry: {
    label: "お姫様抱っこ",
    group: "シチュエーション（雰囲気）",
    tags: "princess carry, lifted in arms, arms around neck, smile",
    motion: "she wraps her arms around his neck and laughs",
    note: "軽々と抱き上げられる。",
  },
  g4s_wall_slam: {
    label: "壁ドン",
    group: "シチュエーション（雰囲気）",
    tags: "wall slam, hand on wall, close face, looking down, tension",
    motion: "she is trapped against the wall, heart racing",
    note: "壁に手をついて迫る。",
  },
  g4s_lap_pillow: {
    label: "膝枕",
    group: "シチュエーション（雰囲気）",
    tags: "lap pillow, lying on lap, stroking hair, gentle smile, soft light",
    motion: "she strokes his hair as he rests",
    note: "膝の上でくつろぐ。",
  },
  g4s_sleep_together: {
    label: "添い寝",
    group: "シチュエーション（雰囲気）",
    tags: "sleeping together, cuddling, one blanket, quiet, warm",
    motion: "she wraps an arm around him and sighs",
    note: "寄り添って眠る。",
  },
  g4s_forehead_touch: {
    label: "おでこをくっつける",
    group: "シチュエーション（雰囲気）",
    tags: "forehead touching, eyes closed, soft breath, intimate",
    motion: "they press foreheads and smile",
    note: "おでこを合わせて微笑む。",
  },
  g4s_back_hug: {
    label: "バックハグ",
    group: "シチュエーション（雰囲気）",
    tags: "hugging from behind, arms around waist, chin on shoulder, warm",
    motion: "she leans back into his chest",
    note: "後ろから包み込まれる。",
  },
  g4s_chin_lift: {
    label: "あごクイ",
    group: "シチュエーション（雰囲気）",
    tags: "chin lift, looking down, close face, tension, hand under chin",
    motion: "he tilts her chin up, she trembles",
    note: "顔を持ち上げられて見つめる。",
  },
  g4s_kiss_forehead: {
    label: "おでこにキス",
    group: "シチュエーション（雰囲気）",
    tags: "kiss on forehead, gentle, eyes closed, tender",
    motion: "she closes her eyes at the soft kiss",
    note: "やさしいキス。",
  },
  g4s_deep_kiss: {
    label: "濃厚なキス",
    group: "シチュエーション（雰囲気）",
    tags: "deep kiss, tangled hair, hands on face, flushed, breath",
    motion: "she pulls him closer by the collar",
    note: "息が混ざるキス。",
  },
  g4s_neck_kiss: {
    label: "首筋へのキス",
    group: "シチュエーション（雰囲気）",
    tags: "kiss on neck, shiver, hair lifted, eyes closed",
    motion: "she tilts her head to give him space",
    note: "首筋に唇が触れる。",
  },
  g4s_hair_play: {
    label: "髪をなでる",
    group: "シチュエーション（雰囲気）",
    tags: "stroking hair, gentle, soft smile, lying close",
    motion: "his fingers run through her hair",
    note: "やさしく髪をすく。",
  },
  g4s_dry_hair: {
    label: "ドライヤーで髪を乾かす",
    group: "シチュエーション（雰囲気）",
    tags: "drying hair, bathroom, towel, towel-wrapped, steam, tender",
    motion: "he dries her hair, warm air swirling",
    note: "お風呂上がりのひととき。",
  },
  g4s_towel_wrap: {
    label: "バスタオル姿",
    group: "シチュエーション（雰囲気）",
    tags: "bath towel only, wet hair, steam, flushed skin",
    motion: "she tucks the towel edge and glances away",
    note: "風呂上がりのタオル一枚。",
  },
  g4s_oversized_shirt: {
    label: "彼シャツ",
    group: "シチュエーション（雰囲気）",
    tags: "oversized shirt, bare legs, sleeves rolled, morning, messy hair",
    motion: "she sniffs the collar and smiles",
    note: "大きな彼のシャツを着る。",
  },
  g4s_apron_naked: {
    label: "裸エプロン",
    group: "シチュエーション（雰囲気）",
    tags: "naked apron, kitchen, back view, bare skin, cooking",
    motion: "she glances back over her shoulder, spoon in hand",
    note: "エプロンだけで料理。",
  },
  g4s_lingerie_reveal: {
    label: "下着姿のお披露目",
    group: "シチュエーション（雰囲気）",
    tags: "new lingerie, lace, mirror, shy, bedroom",
    motion: "she turns slowly, hands behind her back",
    note: "新しい下着を見せる。",
  },
  g4s_stockings_garter: {
    label: "ストッキングとガーター",
    group: "シチュエーション（雰囲気）",
    tags: "stockings, garter belt, lace, sitting on bed, legs crossed",
    motion: "she slides a stocking up slowly",
    note: "脚のラインを見せる。",
  },
  g4s_swimsuit_oil: {
    label: "サンオイルを塗る",
    group: "シチュエーション（雰囲気）",
    tags: "applying sunscreen, swimsuit, beach, back, oil glossy",
    motion: "she asks him to rub oil on her back",
    note: "背中に塗ってもらう。",
  },
  g4s_feed_each_other: {
    label: "食べさせ合う",
    group: "シチュエーション（雰囲気）",
    tags: "feeding each other, strawberries, spoon, close, giggling",
    motion: "she holds a strawberry to his lips",
    note: "甘い食べさせ合い。",
  },
  g4s_drunk_cuddly: {
    label: "酔って甘えん坊",
    group: "シチュエーション（雰囲気）",
    tags: "drunk, flushed, cuddly, clinging, sleepy eyes, adult",
    motion: "she clings to his arm and mumbles",
    note: "酔ってくっついて離れない。",
  },
  g4s_jealous_mood: {
    label: "やきもち",
    group: "シチュエーション（雰囲気）",
    tags: "jealous, pouting, cheeks puffed, crossed arms, looking away",
    motion: "she pouts and tugs his sleeve",
    note: "拗ねて甘える。",
  },
  g4s_tsundere_blush: {
    label: "ツンデレ",
    group: "シチュエーション（雰囲気）",
    tags: "tsundere, blushing, crossed arms, turning away, secretly smiling",
    motion: "she says she doesn't care while blushing",
    note: "素直になれない。",
  },
  g4s_yandere_gaze: {
    label: "ヤンデレの瞳",
    group: "シチュエーション（雰囲気）",
    tags: "yandere, adult, dark eyes, smile, clinging, soft light, no violence",
    motion: "she hugs him tight with an unwavering smile",
    note: "重たい愛情の視線。",
  },
  g4s_confession: {
    label: "告白",
    group: "シチュエーション（雰囲気）",
    tags: "confession, nervous, blushing, evening, holding hands, looking down",
    motion: "she squeezes her hands and meets his eyes",
    note: "勇気を出して気持ちを伝える。",
  },
  g4s_first_time_nervous: {
    label: "初めての夜",
    group: "シチュエーション（雰囲気）",
    tags: "first night together, nervous, adult, soft lamp, trembling, tender",
    motion: "she takes a deep breath and nods",
    note: "ぎこちなく優しい夜。",
  },
  g4s_reunion_hug: {
    label: "再会の抱擁",
    group: "シチュエーション（雰囲気）",
    tags: "reunion hug, tears of joy, tight embrace, lifted off the ground",
    motion: "she leaps into his arms",
    note: "強く抱きしめる。",
  },
  g4s_make_up: {
    label: "仲直り",
    group: "シチュエーション（雰囲気）",
    tags: "making up after quarrel, quiet, hands touching, tearful eyes",
    motion: "she wipes her eyes and takes his hand",
    note: "ケンカのあとの仲直り。",
  },
  g4s_bedroom_eyes: {
    label: "誘うまなざし",
    group: "シチュエーション（雰囲気）",
    tags: "seductive gaze, half-lidded eyes, lip bite, soft lamp",
    motion: "she bites her lip and beckons with a finger",
    note: "誘うように見つめる。",
  },
  g4s_strip_tease_slow: {
    label: "ストリップティーズ",
    group: "シチュエーション（雰囲気）",
    tags: "striptease, slow, silhouette, dim light, garment sliding",
    motion: "she unbuttons slowly, eyes on him",
    note: "ゆっくり脱いで見せる。",
  },
  g4s_lap_sitting: {
    label: "膝の上に座る",
    group: "シチュエーション（雰囲気）",
    tags: "sitting on his lap, arms around neck, close, laughing",
    motion: "she settles on his lap and kisses his cheek",
    note: "膝の上でじゃれる。",
  },
  g4s_play_fight: {
    label: "じゃれ合い",
    group: "シチュエーション（雰囲気）",
    tags: "playful wrestling, pillow fight, laughing, tangled, bed",
    motion: "she tackles him onto the bed laughing",
    note: "ベッドでじゃれる。",
  },
  g4s_tickle_fight: {
    label: "くすぐり合い",
    group: "シチュエーション（雰囲気）",
    tags: "tickling, laughing, tears of laughter, bed",
    motion: "she squirms and gasps for breath",
    note: "笑いながらくすぐる。",
  },
  g4s_massage_back: {
    label: "背中のマッサージ",
    group: "シチュエーション（雰囲気）",
    tags: "back massage, oil, bare back, towel, relaxed, hands",
    motion: "his thumbs press along her spine",
    note: "オイルで背中をほぐす。",
  },
  g4s_foot_massage: {
    label: "足のマッサージ",
    group: "シチュエーション（雰囲気）",
    tags: "foot massage, sofa, soft lamp, relaxed, bare legs",
    motion: "she wiggles her toes and sighs",
    note: "疲れた足をもみほぐす。",
  },
  g4s_bath_together: {
    label: "一緒に入浴",
    group: "シチュエーション（雰囲気）",
    tags: "bathing together, bubble bath, facing each other, steam, candles",
    motion: "she rests her foot on his knee in the water",
    note: "二人で湯船につかる。",
  },
  g4s_wash_hair: {
    label: "髪を洗ってもらう",
    group: "シチュエーション（雰囲気）",
    tags: "washing hair, bath, foam, closed eyes, gentle hands",
    motion: "she closes her eyes as he lathers her hair",
    note: "シャンプーの泡と指先。",
  },
  g4s_stargazing_blanket: {
    label: "毛布で星を見る",
    group: "シチュエーション（雰囲気）",
    tags: "blanket, stargazing, rooftop, huddled, night",
    motion: "she pulls the blanket around both of them",
    note: "毛布一枚を分け合う。",
  },
  g4s_secret_glance: {
    label: "こっそり目配せ",
    group: "シチュエーション（雰囲気）",
    tags: "secret glance, crowded room, hidden smile, finger touch",
    motion: "she touches his hand under the table",
    note: "人前で秘密の合図。",
  },
  g4s_under_table_touch: {
    label: "テーブルの下で手をつなぐ",
    group: "シチュエーション（雰囲気）",
    tags: "holding hands under table, restaurant, composed face, flushed",
    motion: "she keeps a calm face but squeezes tight",
    note: "平静を装う甘いいたずら。",
  },
  g4s_marked_neck: {
    label: "首筋のキスマーク",
    group: "シチュエーション（雰囲気）",
    tags: "love bite mark on neck, collar pulled, mirror, shy",
    motion: "she hides the mark with her hair",
    note: "隠しきれない痕。",
  },
  g4s_mirror_gaze: {
    label: "鏡越しに見つめる",
    group: "シチュエーション（雰囲気）",
    tags: "mirror gaze, standing behind, reflection, close, soft light",
    motion: "she watches his reflection hugging her",
    note: "鏡の中で目が合う。",
  },
  g4s_lost_in_pleasure: {
    label: "余韻にひたる",
    group: "シチュエーション（雰囲気）",
    tags: "afterglow, flushed, half-closed eyes, sweat, soft smile, sheets",
    motion: "she sighs and nestles into the pillow",
    note: "ぼんやり満たされた余韻。",
  },
  g4s_pillow_talk: {
    label: "ピロートーク",
    group: "シチュエーション（雰囲気）",
    tags: "pillow talk, whispering, lying face to face, sheets, soft lamp",
    motion: "she traces circles on his chest while talking",
    note: "ベッドでの小声の会話。",
  },
  g4s_sleepy_morning_kiss: {
    label: "寝起きのキス",
    group: "シチュエーション（雰囲気）",
    tags: "sleepy morning kiss, messy hair, warm sunlight, smile",
    motion: "she kisses him before opening her eyes",
    note: "寝ぼけながらのキス。",
  },
  g4s_dancing_slow: {
    label: "ゆっくりダンス",
    group: "シチュエーション（雰囲気）",
    tags: "slow dancing, living room, dim light, arms around, music, barefoot",
    motion: "they sway in place with her head on his chest",
    note: "音楽に合わせて揺れる。",
  },
  g4s_piano_duet: {
    label: "ピアノの連弾",
    group: "シチュエーション（雰囲気）",
    tags: "piano duet, sitting close, shoulders touching, evening light",
    motion: "her hand slips over his on the keys",
    note: "寄り添って弾く。",
  },
  g4s_stolen_kiss: {
    label: "不意打ちのキス",
    group: "シチュエーション（雰囲気）",
    tags: "stolen kiss, surprise, wide eyes, blush, tiptoe",
    motion: "she stands on tiptoe and kisses his cheek",
    note: "不意にされるキス。",
  },
  g4s_breathless_chase: {
    label: "追いかけっこ",
    group: "シチュエーション（雰囲気）",
    tags: "playful chase, giggling, hallway, bare feet, towel",
    motion: "she runs off laughing and he follows",
    note: "笑いながら追いかける。",
  },
  g4s_domination_soft: {
    label: "優しく命令される",
    group: "シチュエーション（雰囲気）",
    tags: "soft domination, calm voice, kneeling, consensual, dim light",
    motion: "she obeys with a flushed nod",
    note: "低い声に従う甘い支配。",
  },
  g4s_submissive_wait: {
    label: "待て（お預け）",
    group: "シチュエーション（雰囲気）",
    tags: "waiting obediently, kneeling, eyes pleading, flushed, consensual",
    motion: "she waits trembling for his word",
    note: "お預けのドキドキ。",
  },
  g4s_affection_praise: {
    label: "褒められてとろける",
    group: "シチュエーション（雰囲気）",
    tags: "being praised, head pat, melting expression, blush",
    motion: "she closes her eyes as he pats her head",
    note: "頭をなでられて蕩ける。",
  },
  // ─ シチュエーション（追加）─
  h5s_001: {
    label: "レストランの個室",
    group: "シチュエーション（場所）",
    tags: "adult couple, private dining room, candlelight, wine glasses, elegant dress",
    motion: "she leans across the table and touches his hand",
    note: "個室で二人きりの静かなディナー。",
  },
  h5s_002: {
    label: "夜景の見えるレストラン",
    group: "シチュエーション（場所）",
    tags: "rooftop restaurant, city lights, evening dress, wine",
    motion: "she gazes at the skyline then at him",
    note: "夜景を背に、グラスを傾ける。",
  },
  h5s_003: {
    label: "屋上のインフィニティプール",
    group: "シチュエーション（場所）",
    tags: "infinity pool, rooftop, night, wet skin, swimsuit",
    motion: "she floats at the pool edge with the skyline behind",
    note: "屋上のプールで夜景を眺める。",
  },
  h5s_004: {
    label: "ホテルのスカイラウンジ",
    group: "シチュエーション（場所）",
    tags: "sky lounge, floor to ceiling windows, cocktails, dim lights",
    motion: "she sits close to him by the glass",
    note: "高層階のラウンジで肩を寄せる。",
  },
  h5s_005: {
    label: "ホテルのバスローブの朝",
    group: "シチュエーション（場所）",
    tags: "hotel room, white bathrobe, morning light, breakfast tray",
    motion: "she feeds him a strawberry in bed",
    note: "朝日の差すホテルで朝食を。",
  },
  h5s_006: {
    label: "ホテルのバスタブ(窓付き)",
    group: "シチュエーション（場所）",
    tags: "bathtub by large window, bubbles, city view, candle",
    motion: "she rests her head back as bubbles drift",
    note: "窓際のバスタブで夜景とお風呂。",
  },
  h5s_007: {
    label: "ジャグジー付きのテラス",
    group: "シチュエーション（場所）",
    tags: "private terrace jacuzzi, night sky, steam, champagne",
    motion: "she hands him a glass from the warm water",
    note: "テラスのジャグジーで乾杯。",
  },
  h5s_008: {
    label: "ダブルのコネクティングルーム",
    group: "シチュエーション（場所）",
    tags: "hotel connecting door, half open, soft light, robe",
    motion: "she opens the door between the rooms and smiles",
    note: "隣室とつながるドアをそっと開ける。",
  },
  h5s_009: {
    label: "メゾネットの寝室",
    group: "シチュエーション（場所）",
    tags: "loft bedroom, spiral stairs, warm lamp, white sheets",
    motion: "she looks down from the loft and beckons",
    note: "吹き抜けのロフトベッド。",
  },
  h5s_010: {
    label: "和モダンの寝室",
    group: "シチュエーション（場所）",
    tags: "japanese modern bedroom, low bed, paper lamp, tatami",
    motion: "she kneels by the low bed and loosens her hair",
    note: "行灯の灯る和風の寝室。",
  },
  h5s_011: {
    label: "洞窟風呂",
    group: "シチュエーション（場所）",
    tags: "cave bath, stone walls, dim lantern, steam, hot spring",
    motion: "she wades through the steam toward him",
    note: "岩肌に囲まれた洞窟の温泉。",
  },
  h5s_012: {
    label: "岩風呂の混浴",
    group: "シチュエーション（場所）",
    tags: "rock bath, open air, steam, towel wrapped, shared bath",
    motion: "she tucks the towel and slips into the water beside him",
    note: "二人で入る岩風呂。",
  },
  h5s_013: {
    label: "貸切の家族風呂風(大人二人)",
    group: "シチュエーション（場所）",
    tags: "private cypress bath, wooden tub, steam, adult couple",
    motion: "she pours water over his shoulder",
    note: "檜の貸切風呂で二人きり。",
  },
  h5s_014: {
    label: "足湯のベンチ",
    group: "シチュエーション（場所）",
    tags: "foot bath bench, onsen town, yukata, evening",
    motion: "she dangles her feet and leans on his shoulder",
    note: "温泉街の足湯でのんびり。",
  },
  h5s_015: {
    label: "砂風呂",
    group: "シチュエーション（場所）",
    tags: "sand bath, warm sand, yukata, seaside",
    motion: "she lies half buried in warm sand and laughs",
    note: "海辺の温かい砂に包まれる。",
  },
  h5s_016: {
    label: "岩盤浴",
    group: "シチュエーション（場所）",
    tags: "bedrock bath room, mat, towel, dim pink light, sweat",
    motion: "she lies side by side with him, glowing with sweat",
    note: "薄暗い岩盤浴で寄り添う。",
  },
  h5s_017: {
    label: "ミストサウナ",
    group: "シチュエーション（場所）",
    tags: "mist sauna, white steam, tiled bench, towel",
    motion: "she wipes damp hair and smiles through the mist",
    note: "白い蒸気の中の二人。",
  },
  h5s_018: {
    label: "サウナ後の水風呂",
    group: "シチュエーション（場所）",
    tags: "cold plunge, sauna cool down, steam from skin, towel",
    motion: "she steps out of the plunge, skin flushed",
    note: "整った後の火照った肌。",
  },
  h5s_019: {
    label: "テントサウナと湖",
    group: "シチュエーション（場所）",
    tags: "tent sauna, lakeside, forest, wooden stove, dusk",
    motion: "she runs from the tent to the lake in a towel",
    note: "湖畔のテントサウナ。",
  },
  h5s_020: {
    label: "スパのトリートメントルーム",
    group: "シチュエーション（場所）",
    tags: "spa room, aroma candles, massage bed, warm oil",
    motion: "she lies face down as warm oil is poured",
    note: "アロマの香るスパの個室。",
  },
  h5s_021: {
    label: "スパのカップルルーム",
    group: "シチュエーション（場所）",
    tags: "couple spa room, two beds, petals, soft lights",
    motion: "she turns her head and meets his eyes",
    note: "カップル用スパで並んで施術。",
  },
  h5s_022: {
    label: "ヘアサロンの閉店後",
    group: "シチュエーション（場所）",
    tags: "hair salon, mirrors, closing time, empty chairs, soft light",
    motion: "she sits in the chair and he combs her hair",
    note: "閉店後のサロンで髪を整える。",
  },
  h5s_023: {
    label: "エステサロンのベッド",
    group: "シチュエーション（場所）",
    tags: "esthetic salon, white towels, dim light, oil",
    motion: "she relaxes as shoulders are rubbed",
    note: "静かなエステの時間。",
  },
  h5s_024: {
    label: "ネイルサロンの帰り",
    group: "シチュエーション（場所）",
    tags: "nail salon, pastel interior, adult woman",
    motion: "she holds out her new nails to show him",
    note: "新しいネイルを見せる。",
  },
  h5s_025: {
    label: "撮影スタジオのホリゾント",
    group: "シチュエーション（場所）",
    tags: "photo studio, white cyclorama, strobe light, tripod",
    motion: "she poses and turns with a smile at the flash",
    note: "白い背景でのポートレート撮影。",
  },
  h5s_026: {
    label: "ブライダル衣装の試着室",
    group: "シチュエーション（場所）",
    tags: "bridal fitting room, white dress, mirrors, curtains",
    motion: "she twirls in the dress and asks his opinion",
    note: "ウエディングドレスの試着。",
  },
  h5s_027: {
    label: "ドレスの更衣室",
    group: "シチュエーション（場所）",
    tags: "dressing room, evening gowns on racks, lights",
    motion: "she zips her dress and checks the mirror",
    note: "ドレス選びの更衣室。",
  },
  h5s_028: {
    label: "劇場のボックス席",
    group: "シチュエーション（場所）",
    tags: "opera box seat, red velvet, dim stage light, evening dress",
    motion: "she leans toward him during the performance",
    note: "劇場の特別席で寄り添う。",
  },
  h5s_029: {
    label: "劇場のロビー",
    group: "シチュエーション（場所）",
    tags: "theater lobby, chandelier, evening wear, champagne",
    motion: "she adjusts his tie and whispers",
    note: "幕間のロビーで囁く。",
  },
  h5s_030: {
    label: "オペラ座の廊下",
    group: "シチュエーション（場所）",
    tags: "opera house corridor, gilded walls, red carpet, night",
    motion: "she glances back and walks ahead slowly",
    note: "終演後の静かな廊下。",
  },
  h5s_031: {
    label: "舞台の上の二人",
    group: "シチュエーション（場所）",
    tags: "empty stage, spotlight, curtain, theater",
    motion: "she steps into the spotlight and curtsies",
    note: "誰もいない舞台で二人きり。",
  },
  h5s_032: {
    label: "美術館のギャラリー",
    group: "シチュエーション（場所）",
    tags: "art museum gallery, white walls, paintings, quiet",
    motion: "she stands close to him before a large painting",
    note: "絵の前で肩を寄せる。",
  },
  h5s_033: {
    label: "彫刻の庭",
    group: "シチュエーション（場所）",
    tags: "sculpture garden, dusk, statues, paths",
    motion: "she touches a statue and looks at him",
    note: "夕暮れの彫刻庭園。",
  },
  h5s_034: {
    label: "ジャズバー",
    group: "シチュエーション（場所）",
    tags: "jazz bar, live saxophone, dim amber light, whiskey",
    motion: "she sways to the music at the counter",
    note: "ジャズの流れる夜のバー。",
  },
  h5s_035: {
    label: "ピアノバー",
    group: "シチュエーション（場所）",
    tags: "piano bar, grand piano, candles, cocktails",
    motion: "she leans on the piano as he plays",
    note: "生演奏のピアノバー。",
  },
  h5s_036: {
    label: "ワインセラー",
    group: "シチュエーション（場所）",
    tags: "wine cellar, stone walls, barrels, candle, tasting",
    motion: "she sniffs a glass and smiles",
    note: "地下のワインセラーで試飲。",
  },
  h5s_037: {
    label: "ルーフトップバー",
    group: "シチュエーション（場所）",
    tags: "rooftop bar, string lights, skyline, cocktails, breeze",
    motion: "she holds her hair against the wind",
    note: "屋上バーで夜風にあたる。",
  },
  h5s_038: {
    label: "シガーバー",
    group: "シチュエーション（場所）",
    tags: "cigar bar, leather chairs, wood paneling, low light",
    motion: "she sits across with a drink, smiling slyly",
    note: "革張りの椅子の大人の隠れ家。",
  },
  h5s_039: {
    label: "隠れ家バー",
    group: "シチュエーション（場所）",
    tags: "hidden bar, narrow door, candlelight, brick wall",
    motion: "she slips through the door and looks back",
    note: "看板のない扉の奥のバー。",
  },
  h5s_040: {
    label: "屋台のカウンター",
    group: "シチュエーション（場所）",
    tags: "yatai food stall, red lantern, steam, night street",
    motion: "she shares a bowl of ramen with him",
    note: "赤提灯の屋台で一杯。",
  },
  h5s_041: {
    label: "立ち飲み屋",
    group: "シチュエーション（場所）",
    tags: "standing bar, small tables, after work, crowd",
    motion: "she clinks glasses, cheeks flushed",
    note: "仕事帰りの立ち飲み。",
  },
  h5s_042: {
    label: "夜市",
    group: "シチュエーション（場所）",
    tags: "night market, lanterns, food stalls, crowd",
    motion: "she holds his hand through the crowd",
    note: "にぎやかな夜市を歩く。",
  },
  h5s_043: {
    label: "ライブハウスの最前列",
    group: "シチュエーション（場所）",
    tags: "live music venue, front row, stage lights, crowd",
    motion: "she jumps to the beat and grins at him",
    note: "熱気あふれる最前列。",
  },
  h5s_044: {
    label: "クラブのバルコニー席",
    group: "シチュエーション（場所）",
    tags: "club balcony, neon, overlooking dance floor",
    motion: "she looks down at the floor and then pulls him close",
    note: "フロアを見下ろす席。",
  },
  h5s_045: {
    label: "カジノのテーブル",
    group: "シチュエーション（場所）",
    tags: "casino, green felt, chips, evening dress",
    motion: "she blows on the dice for luck",
    note: "カジノで運試し。",
  },
  h5s_046: {
    label: "ボウリング場の夜",
    group: "シチュエーション（場所）",
    tags: "bowling alley, neon lights, rental shoes, night",
    motion: "she throws the ball and cheers",
    note: "ボウリングで盛り上がる。",
  },
  h5s_047: {
    label: "ダーツバー",
    group: "シチュエーション（場所）",
    tags: "darts bar, board, casual outfit, dim lights",
    motion: "she lines up a throw with one eye closed",
    note: "ダーツで勝負。",
  },
  h5s_048: {
    label: "ビリヤード場",
    group: "シチュエーション（場所）",
    tags: "billiard hall, green table, low lamp, cue",
    motion: "she leans over the table to line up a shot",
    note: "ビリヤードで寄り添う。",
  },
  h5s_049: {
    label: "卓球ができる温泉宿",
    group: "シチュエーション（場所）",
    tags: "ryokan game room, ping pong, yukata, laughter",
    motion: "she smashes the ball with a yukata sleeve flapping",
    note: "浴衣で卓球。",
  },
  h5s_050: {
    label: "ゲームセンター",
    group: "シチュエーション（場所）",
    tags: "arcade, neon, claw machine, crowd",
    motion: "she cheers as a prize drops",
    note: "ゲーセンでデート。",
  },
  h5s_051: {
    label: "プリクラの個室",
    group: "シチュエーション（場所）",
    tags: "photo booth, curtains, bright light, adult couple",
    motion: "she poses with a peace sign inside the booth",
    note: "二人で写真を撮る。",
  },
  h5s_052: {
    label: "水族館のトンネル",
    group: "シチュエーション（場所）",
    tags: "aquarium tunnel, blue light, fish, dim",
    motion: "she looks up at the passing shark and holds his arm",
    note: "青い光のトンネル。",
  },
  h5s_053: {
    label: "動物園の夕暮れ",
    group: "シチュエーション（場所）",
    tags: "zoo evening, golden light, path, couple",
    motion: "she leans on the railing smiling",
    note: "閉園前の動物園。",
  },
  h5s_054: {
    label: "植物園の温室",
    group: "シチュエーション（場所）",
    tags: "botanical garden greenhouse, tropical plants, humid",
    motion: "she brushes a leaf aside and steps in",
    note: "蒸し暑い温室で二人。",
  },
  h5s_055: {
    label: "バラ園",
    group: "シチュエーション（場所）",
    tags: "rose garden, blooming roses, spring, path",
    motion: "she smells a rose and looks up",
    note: "満開のバラ園。",
  },
  h5s_056: {
    label: "ラベンダー畑",
    group: "シチュエーション（場所）",
    tags: "lavender field, purple, summer breeze, sun hat",
    motion: "she walks through the rows with a hat",
    note: "紫の花畑を歩く。",
  },
  h5s_057: {
    label: "ひまわり畑",
    group: "シチュエーション（場所）",
    tags: "sunflower field, summer, blue sky, straw hat",
    motion: "she looks back over her shoulder",
    note: "黄色い花畑。",
  },
  h5s_059: {
    label: "コスモス畑",
    group: "シチュエーション（場所）",
    tags: "cosmos field, autumn, pastel flowers",
    motion: "she holds a bouquet of cosmos",
    note: "秋桜の揺れる丘。",
  },
  h5s_060: {
    label: "棚田の夕景",
    group: "シチュエーション（場所）",
    tags: "terraced rice fields, sunset, orange sky",
    motion: "she stands on the path as the light fades",
    note: "夕日の棚田。",
  },
  h5s_061: {
    label: "吊り橋の上",
    group: "シチュエーション（場所）",
    tags: "suspension bridge, forest valley, wooden planks",
    motion: "she grips his arm as the bridge sways",
    note: "揺れる吊り橋で近づく。",
  },
  h5s_062: {
    label: "展望台の夜景",
    group: "シチュエーション（場所）",
    tags: "observation deck, night skyline, glass, couple",
    motion: "she presses her hand to the glass",
    note: "高い展望台で夜景。",
  },
  h5s_063: {
    label: "回転展望台",
    group: "シチュエーション（場所）",
    tags: "revolving observation deck, panoramic windows, city",
    motion: "she turns slowly with the view behind",
    note: "回りながら景色を眺める。",
  },
  h5s_064: {
    label: "観瀑台の水しぶき",
    group: "シチュエーション（場所）",
    tags: "waterfall viewpoint, mist, rainbow",
    motion: "she lifts her face to the spray",
    note: "滝のしぶきを浴びる。",
  },
  h5s_065: {
    label: "海中展望塔",
    group: "シチュエーション（場所）",
    tags: "underwater observation tower, fish, blue light",
    motion: "she watches the fish pass the window",
    note: "水中の景色を見る。",
  },
  h5s_066: {
    label: "灯台の下の岬",
    group: "シチュエーション（場所）",
    tags: "cape lighthouse, cliffs, sea wind, dusk",
    motion: "she holds her hair as the wind blows",
    note: "岬の風。",
  },
  h5s_067: {
    label: "断崖の遊歩道",
    group: "シチュエーション（場所）",
    tags: "cliff path, ocean view, wind, summer",
    motion: "she leans on the railing facing the sea",
    note: "海を見下ろす道。",
  },
  h5s_068: {
    label: "朝の海辺の散歩",
    group: "シチュエーション（場所）",
    tags: "morning beach, soft light, waves, barefoot",
    motion: "she walks barefoot along the wet sand",
    note: "朝の波打ち際。",
  },
  h5s_069: {
    label: "夜の海岸",
    group: "シチュエーション（場所）",
    tags: "night beach, moonlight, calm waves, blanket",
    motion: "she sits on a blanket and looks at the stars",
    note: "月明かりの砂浜。",
  },
  h5s_070: {
    label: "入り江の秘密のビーチ",
    group: "シチュエーション（場所）",
    tags: "secluded cove, white sand, clear water, rocks",
    motion: "she wades into the clear water laughing",
    note: "誰もいない入り江。",
  },
  h5s_071: {
    label: "南国のヴィラ",
    group: "シチュエーション（場所）",
    tags: "tropical villa, private pool, palm trees, open air bed",
    motion: "she lies on the open air bed with the breeze",
    note: "開放的な南国ヴィラ。",
  },
  h5s_072: {
    label: "水上コテージ",
    group: "シチュエーション（場所）",
    tags: "overwater bungalow, glass floor, turquoise sea",
    motion: "she sits on the deck with her feet in the water",
    note: "水上の部屋。",
  },
  h5s_073: {
    label: "ガラス張りのドームホテル",
    group: "シチュエーション（場所）",
    tags: "glass dome room, stars, bed, forest",
    motion: "she lies on the bed looking at the star sky",
    note: "星空の見える部屋。",
  },
  h5s_074: {
    label: "ツリーハウス",
    group: "シチュエーション（場所）",
    tags: "treehouse, wooden room, lanterns, forest",
    motion: "she pulls up the rope ladder and smiles",
    note: "木の上の隠れ家。",
  },
  h5s_075: {
    label: "氷のホテル",
    group: "シチュエーション（場所）",
    tags: "ice hotel, ice sculptures, fur blankets, blue light",
    motion: "she wraps herself in furs",
    note: "氷の部屋で毛布にくるまる。",
  },
  h5s_076: {
    label: "洞窟ホテル",
    group: "シチュエーション（場所）",
    tags: "cave hotel, stone arches, warm lamps",
    motion: "she walks through the arched stone room",
    note: "岩を掘った部屋。",
  },
  h5s_077: {
    label: "古民家の宿",
    group: "シチュエーション（場所）",
    tags: "old japanese house, irori hearth, wooden beams",
    motion: "she pokes the fire in the hearth",
    note: "囲炉裏のある宿。",
  },
  h5s_078: {
    label: "町家の離れ",
    group: "シチュエーション（場所）",
    tags: "machiya annex, small garden, tatami, lantern",
    motion: "she opens the sliding door to the garden",
    note: "京風の離れ。",
  },
  h5s_079: {
    label: "茶畑の見える宿",
    group: "シチュエーション（場所）",
    tags: "tea plantation, green hills, morning mist, inn",
    motion: "she holds a tea cup on the veranda",
    note: "緑の丘の宿の朝。",
  },
  h5s_080: {
    label: "寺の宿坊",
    group: "シチュエーション（場所）",
    tags: "temple lodging, wooden hall, incense, tatami",
    motion: "she kneels quietly in a yukata",
    note: "静かな宿坊の夜。",
  },
  h5s_081: {
    label: "神社の石段",
    group: "シチュエーション（場所）",
    tags: "shrine stone steps, lanterns, dusk, yukata",
    motion: "she climbs the steps and turns",
    note: "石段の途中で振り返る。",
  },
  h5s_082: {
    label: "竹林の露天",
    group: "シチュエーション（場所）",
    tags: "bamboo forest open bath, steam, morning",
    motion: "she slips into the water as mist rises",
    note: "竹林に囲まれた露天。",
  },
  h5s_083: {
    label: "雪見露天",
    group: "シチュエーション（場所）",
    tags: "snow open air bath, steam, snowflakes, night",
    motion: "she sinks into hot water with snow on her hair",
    note: "雪の降る露天風呂。",
  },
  h5s_084: {
    label: "雪見の縁側",
    group: "シチュエーション（場所）",
    tags: "snowy garden, engawa, hot tea, kotatsu",
    motion: "she holds a cup watching the snow",
    note: "雪景色の縁側。",
  },
  h5s_085: {
    label: "スキー場のゴンドラ",
    group: "シチュエーション（場所）",
    tags: "ski gondola, snowy mountain, winter wear",
    motion: "she waves from the gondola window",
    note: "雪山のゴンドラ。",
  },
  h5s_086: {
    label: "ゲレンデのカフェ",
    group: "シチュエーション（場所）",
    tags: "ski resort cafe, wooden, hot chocolate, goggles",
    motion: "she warms her hands on a cup",
    note: "滑った後のカフェ。",
  },
  h5s_087: {
    label: "スノーモービル",
    group: "シチュエーション（場所）",
    tags: "snowmobile, snowy field, goggles",
    motion: "she wraps her arms around him from behind",
    note: "雪原を走る。",
  },
  h5s_088: {
    label: "ソリ遊び",
    group: "シチュエーション（場所）",
    tags: "sledding, snowy hill, laughter",
    motion: "she slides down the hill with her arms up",
    note: "雪の丘のソリ。",
  },
  h5s_089: {
    label: "かまくらの中",
    group: "シチュエーション（場所）",
    tags: "kamakura snow hut, candle, rice cake, warm",
    motion: "she toasts mochi inside the snow hut",
    note: "かまくらで暖まる。",
  },
  h5s_090: {
    label: "氷の祭りの会場",
    group: "シチュエーション（場所）",
    tags: "ice sculpture festival, colored lights, night",
    motion: "she poses by a glowing ice statue",
    note: "氷像の前で写真。",
  },
  h5s_091: {
    label: "イルミネーションの並木",
    group: "シチュエーション（場所）",
    tags: "illumination avenue, winter, coats, lights",
    motion: "she strolls under the lights with scarf",
    note: "光のトンネルを歩く。",
  },
  h5s_092: {
    label: "クリスマスマーケット",
    group: "シチュエーション（場所）",
    tags: "christmas market, wooden stalls, mulled wine, lights",
    motion: "she sips hot wine, cheeks pink",
    note: "屋台の並ぶ冬の市。",
  },
  h5s_093: {
    label: "ヨットのクルージング",
    group: "シチュエーション（場所）",
    tags: "sailing yacht, sea breeze, deck, sunset",
    motion: "she holds the rail as the sail billows",
    note: "風を受けて走る。",
  },
  h5s_094: {
    label: "ヨットの船首",
    group: "シチュエーション（場所）",
    tags: "yacht bow, open sea, wind, swimsuit",
    motion: "she stands at the bow with arms out",
    note: "海を見渡す船首。",
  },
  h5s_095: {
    label: "カタマランのネット",
    group: "シチュエーション（場所）",
    tags: "catamaran net, sea below, sunset, relaxed",
    motion: "she lies on the net with her hair blowing",
    note: "海面の上のネット。",
  },
  h5s_096: {
    label: "釣り船の朝",
    group: "シチュエーション（場所）",
    tags: "fishing boat, dawn, sea, rod, thermos",
    motion: "she holds the rod and shrieks at a bite",
    note: "朝の釣り。",
  },
  h5s_097: {
    label: "遊覧船のデッキ",
    group: "シチュエーション（場所）",
    tags: "sightseeing boat deck, river, spring breeze",
    motion: "she leans on the railing and points",
    note: "川を行く遊覧船。",
  },
  h5s_098: {
    label: "屋形船",
    group: "シチュエーション（場所）",
    tags: "yakata boat, tatami, lanterns, river night, fireworks",
    motion: "she toasts as lights reflect on water",
    note: "屋形船の宴。",
  },
  h5s_099: {
    label: "ゴンドラの舟遊び",
    group: "シチュエーション（場所）",
    tags: "gondola, canal, old buildings, evening",
    motion: "she trails her hand in the water",
    note: "運河のゴンドラ。",
  },
  h5s_100: {
    label: "カヌーで湖",
    group: "シチュエーション（場所）",
    tags: "canoe, calm lake, mist, morning",
    motion: "she paddles slowly and smiles back",
    note: "朝霧の湖。",
  },
  h5s_101: {
    label: "ボートハウス",
    group: "シチュエーション（場所）",
    tags: "lakeside boathouse, wooden, sunset, rope",
    motion: "she sits on the dock with her feet in the water",
    note: "湖畔の桟橋。",
  },
  h5s_102: {
    label: "海辺のテラス",
    group: "シチュエーション（場所）",
    tags: "seaside terrace, cafe, parasol, breeze",
    motion: "she sips a cold drink and looks at the sea",
    note: "海を望むカフェ。",
  },
  h5s_103: {
    label: "キャンプ場の星空",
    group: "シチュエーション（場所）",
    tags: "campsite, tent, lantern, stars, camp chairs",
    motion: "she looks up with a blanket on her shoulders",
    note: "星の下のキャンプ。",
  },
  h5s_104: {
    label: "グランピングのドーム",
    group: "シチュエーション（場所）",
    tags: "glamping dome tent, bed, string lights, forest",
    motion: "she opens the tent flap to the morning",
    note: "豪華なドームテント。",
  },
  h5s_105: {
    label: "トレーラーハウス",
    group: "シチュエーション（場所）",
    tags: "trailer house, small kitchen, wood, night",
    motion: "she cooks something in the tiny kitchen",
    note: "小さな家での生活。",
  },
  h5s_106: {
    label: "バンライフの車内",
    group: "シチュエーション（場所）",
    tags: "campervan interior, bed, fairy lights, night",
    motion: "she tucks into the van bed with blankets",
    note: "車の中の小さな寝室。",
  },
  h5s_107: {
    label: "湖畔のコテージ",
    group: "シチュエーション（場所）",
    tags: "lakeside cottage, fireplace, wooden floor, snow",
    motion: "she throws a log on the fire",
    note: "暖炉の前。",
  },
  h5s_108: {
    label: "暖炉の前の毛皮のラグ",
    group: "シチュエーション（場所）",
    tags: "fireplace, fur rug, wine, warm glow",
    motion: "she lies on the rug in the firelight",
    note: "ラグの上でくつろぐ。",
  },
  h5s_109: {
    label: "山頂の朝日",
    group: "シチュエーション（場所）",
    tags: "mountain summit, sunrise, hiking, orange sky",
    motion: "she cheers with arms raised",
    note: "朝日の山頂。",
  },
  h5s_110: {
    label: "登山小屋の夜",
    group: "シチュエーション（場所）",
    tags: "mountain hut, wooden bunk, lantern, blankets",
    motion: "she wraps blankets and giggles",
    note: "山小屋の相部屋。",
  },
  h5s_111: {
    label: "森の中の小川",
    group: "シチュエーション（場所）",
    tags: "forest stream, sunlight, rocks, summer",
    motion: "she splashes water with her feet",
    note: "小川の足遊び。",
  },
  h5s_112: {
    label: "森の木漏れ日",
    group: "シチュエーション（場所）",
    tags: "forest, sunlight through leaves, path, hat",
    motion: "she twirls in the dappled light",
    note: "木漏れ日の散歩。",
  },
  h5s_113: {
    label: "洞窟探検",
    group: "シチュエーション（場所）",
    tags: "cave, flashlight, stalactites, wet rock",
    motion: "she shines the light and holds his sleeve",
    note: "洞窟でぴたりと寄り添う。",
  },
  h5s_114: {
    label: "火山のふもとの温泉",
    group: "シチュエーション（場所）",
    tags: "volcano foothill, steam vents, hot spring, barren",
    motion: "she dips her feet at the foot of the volcano",
    note: "硫黄の香る温泉。",
  },
  h5s_115: {
    label: "砂丘の夕暮れ",
    group: "シチュエーション（場所）",
    tags: "sand dunes, sunset, long shadows, wind",
    motion: "she walks the ridge with her scarf fluttering",
    note: "砂丘の稜線。",
  },
  h5s_116: {
    label: "草原の丘",
    group: "シチュエーション（場所）",
    tags: "grass hill, blue sky, windy, picnic",
    motion: "she lays out a picnic cloth in the wind",
    note: "丘の上のピクニック。",
  },
  h5s_117: {
    label: "牧場のサイロ",
    group: "シチュエーション（場所）",
    tags: "farm, silo, hay, evening",
    motion: "she sits on a hay bale with a cup",
    note: "牧場の夕方。",
  },
  h5s_119: {
    label: "茶室の庭",
    group: "シチュエーション（場所）",
    tags: "tea house garden, stepping stones, moss",
    motion: "she walks across stones in a kimono",
    note: "苔の庭の飛び石。",
  },
  h5s_120: {
    label: "日本庭園の池",
    group: "シチュエーション（場所）",
    tags: "japanese garden pond, koi, stone lantern",
    motion: "she feeds the koi and smiles",
    note: "池のほとりの散歩。",
  },
  h5s_121: {
    label: "和傘の小径",
    group: "シチュエーション（場所）",
    tags: "path with wagasa umbrellas, rain, kimono",
    motion: "she twirls a red umbrella",
    note: "和傘と雨の小径。",
  },
  h5s_122: {
    label: "着物の着付け部屋",
    group: "シチュエーション（場所）",
    tags: "kimono dressing room, obi, tatami, mirror",
    motion: "she holds still as the obi is tied",
    note: "着物を着せてもらう。",
  },
  h5s_123: {
    label: "温泉街の射的場",
    group: "シチュエーション（場所）",
    tags: "onsen town shooting gallery, yukata, wooden counter",
    motion: "she aims and squeals at a miss",
    note: "浴衣で遊ぶ。",
  },
  h5s_124: {
    label: "温泉卵のコーナー",
    group: "シチュエーション（場所）",
    tags: "onsen eggs, steaming pot, onsen town, yukata",
    motion: "she cracks an egg and blows on it",
    note: "湯気の中の温泉卵。",
  },
  h5s_125: {
    label: "縁日の金魚すくい",
    group: "シチュエーション（場所）",
    tags: "festival goldfish scooping, yukata, lanterns",
    motion: "she holds a bag with a goldfish up",
    note: "縁日の遊び。",
  },
  h5s_126: {
    label: "露店のりんご飴",
    group: "シチュエーション（場所）",
    tags: "festival stall, candy apple, lanterns, yukata",
    motion: "she takes a bite of the candy apple",
    note: "赤いりんご飴。",
  },
  h5s_127: {
    label: "神楽殿",
    group: "シチュエーション（場所）",
    tags: "kagura stage, shrine, drum, night",
    motion: "she watches the dance with her hands clasped",
    note: "神社の舞台を見る。",
  },
  h5s_128: {
    label: "神輿の通り",
    group: "シチュエーション（場所）",
    tags: "portable shrine parade, festival happi coat, crowd",
    motion: "she shouts with the crowd in happi coat",
    note: "担ぎ手の熱気。",
  },
  h5s_129: {
    label: "盆踊りの輪",
    group: "シチュエーション（場所）",
    tags: "bon dance circle, yukata, drum tower, paper lanterns",
    motion: "she dances in the circle with a fan",
    note: "盆踊りの輪。",
  },
  h5s_130: {
    label: "灯篭流し",
    group: "シチュエーション（場所）",
    tags: "lantern floating, river, dusk, yukata",
    motion: "she sets a lantern on the river",
    note: "川の灯り。",
  },
  h5s_131: {
    label: "新幹線の窓際",
    group: "シチュエーション（旅・乗り物）",
    tags: "shinkansen window seat, landscape passing, bento",
    motion: "she rests her head on his shoulder as scenery slides by",
    note: "新幹線で肩を寄せる。",
  },
  h5s_132: {
    label: "ロマンスカーの展望席",
    group: "シチュエーション（旅・乗り物）",
    tags: "observation seat train, front window, tunnel",
    motion: "she points ahead at the winding track",
    note: "前面展望の席。",
  },
  h5s_133: {
    label: "ローカル線の無人駅",
    group: "シチュエーション（旅・乗り物）",
    tags: "rural station, small platform, evening, vending machine",
    motion: "she waits on the bench swinging her legs",
    note: "無人駅で電車を待つ。",
  },
  h5s_134: {
    label: "夜行バスの隣席",
    group: "シチュエーション（旅・乗り物）",
    tags: "night bus, reclining seats, dim light, blanket",
    motion: "she falls asleep leaning on him",
    note: "夜行バスでうとうと。",
  },
  h5s_135: {
    label: "フェリーのデッキ",
    group: "シチュエーション（旅・乗り物）",
    tags: "ferry deck, sea wind, gulls, sunset",
    motion: "she holds her hair in the wind",
    note: "フェリーの上で風を浴びる。",
  },
  h5s_136: {
    label: "フェリーの個室",
    group: "シチュエーション（旅・乗り物）",
    tags: "ferry private cabin, bunk, small window, waves",
    motion: "she looks out of the porthole at the night sea",
    note: "船の小さな個室。",
  },
  h5s_137: {
    label: "豪華列車のラウンジ",
    group: "シチュエーション（旅・乗り物）",
    tags: "luxury train lounge, velvet seats, bar, night",
    motion: "she raises a glass as the train sways",
    note: "豪華列車で乾杯。",
  },
  h5s_138: {
    label: "トロッコ列車",
    group: "シチュエーション（旅・乗り物）",
    tags: "sightseeing open train, valley, autumn leaves",
    motion: "she waves at the passing valley",
    note: "渓谷を行くトロッコ。",
  },
  h5s_139: {
    label: "ロープウェイ",
    group: "シチュエーション（旅・乗り物）",
    tags: "ropeway gondola, mountain view, windows",
    motion: "she looks down at the valley below",
    note: "空中の移動。",
  },
  h5s_140: {
    label: "ビジネスクラスのシート",
    group: "シチュエーション（旅・乗り物）",
    tags: "business class seat, flat bed, blanket, night flight",
    motion: "she reclines and sips champagne",
    note: "ビジネスクラスの機内。",
  },
  h5s_141: {
    label: "プライベートジェット",
    group: "シチュエーション（旅・乗り物）",
    tags: "private jet cabin, leather seats, sunrise through window",
    motion: "she stretches out on the sofa seat",
    note: "専用機の機内。",
  },
  h5s_142: {
    label: "空港のラウンジ",
    group: "シチュエーション（旅・乗り物）",
    tags: "airport lounge, runway view, coffee, travel bags",
    motion: "she checks the boarding pass and sighs",
    note: "搭乗前の静かな時間。",
  },
  h5s_143: {
    label: "空港のロビーで待ち合わせ",
    group: "シチュエーション（旅・乗り物）",
    tags: "airport arrival hall, suitcases, crowd",
    motion: "she runs and jumps into his arms",
    note: "到着ロビーで再会。",
  },
  h5s_144: {
    label: "ホテルのロビー",
    group: "シチュエーション（旅・乗り物）",
    tags: "hotel lobby, marble floor, chandelier, luggage",
    motion: "she checks in and holds the key card up",
    note: "チェックインの瞬間。",
  },
  h5s_145: {
    label: "ベルボーイの去った後の部屋",
    group: "シチュエーション（旅・乗り物）",
    tags: "hotel room entrance, bags, curtains open, city view",
    motion: "she kicks off her heels and flops on the bed",
    note: "部屋に入ってほっとする。",
  },
  h5s_146: {
    label: "レンタカーのドライブ",
    group: "シチュエーション（旅・乗り物）",
    tags: "rental convertible, coastal road, sunglasses, summer",
    motion: "she lifts her arms in the wind",
    note: "海沿いのドライブ。",
  },
  h5s_147: {
    label: "夜のドライブ",
    group: "シチュエーション（旅・乗り物）",
    tags: "night drive, city lights, car interior, radio",
    motion: "she looks out the window with city glow on her face",
    note: "夜のドライブ。",
  },
  h5s_148: {
    label: "高速道路のサービスエリア",
    group: "シチュエーション（旅・乗り物）",
    tags: "highway rest area, night, vending lights, coffee",
    motion: "she shares a cup of coffee under the lamp",
    note: "休憩所の夜。",
  },
  h5s_149: {
    label: "オープンカー",
    group: "シチュエーション（旅・乗り物）",
    tags: "open car, wind, scarf, mountain road",
    motion: "she laughs with hair flying",
    note: "風を切って走る。",
  },
  h5s_150: {
    label: "サイドカー付きバイク",
    group: "シチュエーション（旅・乗り物）",
    tags: "motorcycle with sidecar, leather jacket, road",
    motion: "she waves from the sidecar",
    note: "ツーリング。",
  },
  h5s_151: {
    label: "バイクの二人乗り",
    group: "シチュエーション（旅・乗り物）",
    tags: "motorcycle tandem, helmet, riding jacket, sunset",
    motion: "she holds him tightly from behind",
    note: "後ろに乗ってしがみつく。",
  },
  h5s_152: {
    label: "自転車の二人乗り風景",
    group: "シチュエーション（旅・乗り物）",
    tags: "tandem bicycle, riverbank, evening",
    motion: "she pedals and laughs behind him",
    note: "土手の自転車。",
  },
  h5s_153: {
    label: "馬車の旅",
    group: "シチュエーション（旅・乗り物）",
    tags: "horse carriage, cobblestone, old town, evening",
    motion: "she waves from the carriage",
    note: "石畳の馬車。",
  },
  h5s_154: {
    label: "気球の上",
    group: "シチュエーション（旅・乗り物）",
    tags: "hot air balloon basket, sunrise, clouds, valley",
    motion: "she grips the basket and gasps at the view",
    note: "気球で空へ。",
  },
  h5s_155: {
    label: "ヘリコプターの遊覧",
    group: "シチュエーション（旅・乗り物）",
    tags: "helicopter ride, headset, city below",
    motion: "she presses her face to the window",
    note: "空から夜景。",
  },
  h5s_156: {
    label: "海上のクルーザー",
    group: "シチュエーション（旅・乗り物）",
    tags: "motor cruiser deck, sunbathing, sea, champagne",
    motion: "she lies on the deck cushion in sunglasses",
    note: "クルーザーの日光浴。",
  },
  h5s_157: {
    label: "寝台特急のラウンジカー",
    group: "シチュエーション（旅・乗り物）",
    tags: "sleeper train lounge car, dim lamp, bar, night",
    motion: "she stirs a cocktail as the window rolls by",
    note: "夜行列車のバー。",
  },
  h5s_158: {
    label: "キャラバンのテント",
    group: "シチュエーション（旅・乗り物）",
    tags: "desert caravan tent, carpets, lanterns, night",
    motion: "she pours tea on a rug",
    note: "砂漠の夜の宿営地。",
  },
  h5s_159: {
    label: "登山鉄道の窓",
    group: "シチュエーション（旅・乗り物）",
    tags: "mountain railway, snow peak, window",
    motion: "she points at the white mountain",
    note: "雪山を行く鉄道。",
  },
  h5s_160: {
    label: "バスの最後列で",
    group: "シチュエーション（旅・乗り物）",
    tags: "bus back row, afternoon sun, rural road",
    motion: "she dozes with her head against the window",
    note: "のんびり走る路線バス。",
  },
  h5s_161: {
    label: "地下鉄の終電",
    group: "シチュエーション（旅・乗り物）",
    tags: "last subway train, empty car, dim light",
    motion: "she leans her head back, slightly drunk",
    note: "終電のほろ酔い。",
  },
  h5s_162: {
    label: "駅のホーム別れ",
    group: "シチュエーション（旅・乗り物）",
    tags: "train platform, departure, coat, evening",
    motion: "she grips his hand until the train arrives",
    note: "別れの駅。",
  },
  h5s_163: {
    label: "旅先の古い宿場町",
    group: "シチュエーション（旅・乗り物）",
    tags: "old post town, wooden houses, lanterns, evening",
    motion: "she walks the stone lane in yukata",
    note: "宿場町を歩く。",
  },
  h5s_164: {
    label: "初日の出",
    group: "シチュエーション（季節・行事）",
    tags: "new year sunrise, mountain or beach, cold, coat",
    motion: "she lifts her hands as the sun rises",
    note: "元日の朝日。",
  },
  h5s_165: {
    label: "おせちの正月",
    group: "シチュエーション（季節・行事）",
    tags: "new year feast, osechi, kimono, tatami",
    motion: "she pours sake for him",
    note: "正月のお祝い。",
  },
  h5s_166: {
    label: "振袖の成人式(大人)",
    group: "シチュエーション（季節・行事）",
    tags: "furisode kimono, hair ornament, outdoors, adult woman",
    motion: "she poses in the kimono smiling",
    note: "着物姿の記念撮影。",
  },
  h5s_167: {
    label: "節分の豆まき",
    group: "シチュエーション（季節・行事）",
    tags: "setsubun, oni mask, beans, shrine",
    motion: "she throws beans laughing",
    note: "豆まき。",
  },
  h5s_168: {
    label: "梅の花見",
    group: "シチュエーション（季節・行事）",
    tags: "plum blossoms, early spring, shrine garden",
    motion: "she looks up at the pink blossoms",
    note: "梅の香る庭。",
  },
  h5s_169: {
    label: "ひな祭りの夜",
    group: "シチュエーション（季節・行事）",
    tags: "hinamatsuri dolls, peach blossom, tatami, warm",
    motion: "she arranges the dolls with care",
    note: "雛人形を飾る。",
  },
  h5s_170: {
    label: "ホワイトデーのお返し",
    group: "シチュエーション（季節・行事）",
    tags: "white day, gift box, cafe, ribbon",
    motion: "she opens the gift and beams",
    note: "お返しの贈り物。",
  },
  h5s_171: {
    label: "桜並木のお花見",
    group: "シチュエーション（季節・行事）",
    tags: "cherry blossom row, petals falling, picnic sheet",
    motion: "she catches a falling petal",
    note: "桜吹雪の下。",
  },
  h5s_172: {
    label: "お花見の宴会",
    group: "シチュエーション（季節・行事）",
    tags: "hanami party, blue sheet, sake, petals",
    motion: "she pours a cup and smiles",
    note: "花見酒。",
  },
  h5s_173: {
    label: "ゴールデンウィークの旅",
    group: "シチュエーション（季節・行事）",
    tags: "golden week trip, fresh green, travel bag",
    motion: "she waves from the trail",
    note: "新緑の旅。",
  },
  h5s_174: {
    label: "こいのぼりの川辺",
    group: "シチュエーション（季節・行事）",
    tags: "koinobori flags, river, clear sky, spring",
    motion: "she watches the carp streamers swim",
    note: "初夏の川辺。",
  },
  h5s_175: {
    label: "母の日(大人)の贈り物",
    group: "シチュエーション（季節・行事）",
    tags: "mothers day, carnation, adult daughter, cafe",
    motion: "she hands over flowers with a smile",
    note: "カーネーションを贈る。",
  },
  h5s_176: {
    label: "紫陽花の小径",
    group: "シチュエーション（季節・行事）",
    tags: "hydrangea path, rain, umbrella, june",
    motion: "she looks at the flowers under a clear umbrella",
    note: "梅雨の花。",
  },
  h5s_177: {
    label: "蛍の夜",
    group: "シチュエーション（季節・行事）",
    tags: "firefly night, riverside, dark, glowing lights",
    motion: "she cups her hands near a drifting light",
    note: "ホタルの光。",
  },
  h5s_178: {
    label: "七夕の短冊",
    group: "シチュエーション（季節・行事）",
    tags: "tanabata, bamboo, paper strips, summer night",
    motion: "she ties a wish strip to the bamboo",
    note: "願いを書く。",
  },
  h5s_179: {
    label: "夏の海開き",
    group: "シチュエーション（季節・行事）",
    tags: "beach opening, summer sun, swimsuit, parasol",
    motion: "she runs into the sea laughing",
    note: "海水浴場の朝。",
  },
  h5s_180: {
    label: "海の家",
    group: "シチュエーション（季節・行事）",
    tags: "beach house, shaved ice, summer, wooden floor",
    motion: "she scoops the shaved ice and offers a bite",
    note: "かき氷。",
  },
  h5s_181: {
    label: "スイカ割り",
    group: "シチュエーション（季節・行事）",
    tags: "watermelon splitting game, beach, blindfold, laughter",
    motion: "she swings the stick blindfolded",
    note: "ビーチの遊び。",
  },
  h5s_182: {
    label: "夏の花火(手持ち)",
    group: "シチュエーション（季節・行事）",
    tags: "sparklers, yukata, night yard, small fire",
    motion: "she holds a sparkler and watches it glow",
    note: "線香花火。",
  },
  h5s_183: {
    label: "大輪の打ち上げ花火",
    group: "シチュエーション（季節・行事）",
    tags: "large fireworks, night sky, rooftop or riverbank",
    motion: "she looks up as colors reflect on her face",
    note: "夜空に咲く花。",
  },
  h5s_184: {
    label: "花火大会の人混み",
    group: "シチュエーション（季節・行事）",
    tags: "fireworks festival crowd, yukata, riverbank, lights",
    motion: "he shields her from the crowd",
    note: "人混みで手を引く。",
  },
  h5s_185: {
    label: "夏の縁日",
    group: "シチュエーション（季節・行事）",
    tags: "summer festival, yatai, yukata, lanterns",
    motion: "she holds his hand through the stalls",
    note: "縁日の夜。",
  },
  h5s_186: {
    label: "夏の風鈴",
    group: "シチュエーション（季節・行事）",
    tags: "wind chimes, veranda, summer afternoon, watermelon",
    motion: "she listens to the chime and fans herself",
    note: "軒下の風鈴。",
  },
  h5s_187: {
    label: "蚊帳の中",
    group: "シチュエーション（季節・行事）",
    tags: "mosquito net, futon, summer night, fan",
    motion: "she lies inside the net with a fan",
    note: "蚊帳の涼しい夜。",
  },
  h5s_188: {
    label: "お盆の帰省",
    group: "シチュエーション（季節・行事）",
    tags: "obon homecoming, countryside, old house, sunset",
    motion: "she lights the welcome fire at dusk",
    note: "田舎の夏。",
  },
  h5s_189: {
    label: "夏の入道雲",
    group: "シチュエーション（季節・行事）",
    tags: "summer cloudscape, field road, bicycle",
    motion: "she shields her eyes and points",
    note: "入道雲の道。",
  },
  h5s_190: {
    label: "秋の月見",
    group: "シチュエーション（季節・行事）",
    tags: "tsukimi, full moon, pampas grass, dango, veranda",
    motion: "she sets dumplings under the moon",
    note: "お月見。",
  },
  h5s_191: {
    label: "秋の紅葉狩り",
    group: "シチュエーション（季節・行事）",
    tags: "autumn leaves, mountain path, red maple",
    motion: "she holds a red leaf up to the light",
    note: "真っ赤な紅葉。",
  },
  h5s_192: {
    label: "紅葉の露天風呂",
    group: "シチュエーション（季節・行事）",
    tags: "autumn open bath, red maple leaves, steam",
    motion: "she lets a leaf float in the water",
    note: "紅葉を浮かべて。",
  },
  h5s_193: {
    label: "栗拾い",
    group: "シチュエーション（季節・行事）",
    tags: "chestnut picking, autumn forest, basket",
    motion: "she crouches picking nuts",
    note: "秋の恵み。",
  },
  h5s_194: {
    label: "芋煮会",
    group: "シチュエーション（季節・行事）",
    tags: "imoni gathering, riverside, pot, autumn",
    motion: "she ladles soup with a grin",
    note: "川原の芋煮。",
  },
  h5s_195: {
    label: "ハロウィンパーティー(大人)",
    group: "シチュエーション（季節・行事）",
    tags: "halloween party, costumes, jack o lantern, adult",
    motion: "she adjusts a witch hat and winks",
    note: "大人の仮装パーティー。",
  },
  h5s_196: {
    label: "ハロウィンの小悪魔仮装",
    group: "シチュエーション（季節・行事）",
    tags: "halloween costume, adult, party lights",
    motion: "she poses with a candy basket",
    note: "仮装で盛り上がる。",
  },
  h5s_197: {
    label: "ハロウィンの屋敷",
    group: "シチュエーション（季節・行事）",
    tags: "halloween house, cobwebs, candles, adult couple",
    motion: "she peeks through a cobweb curtain",
    note: "屋敷のお化け演出。",
  },
  h5s_199: {
    label: "ボジョレーの解禁",
    group: "シチュエーション（季節・行事）",
    tags: "new wine, bar, candle, autumn",
    motion: "she tastes the new wine and nods",
    note: "新酒の夜。",
  },
  h5s_200: {
    label: "勤労感謝の温泉",
    group: "シチュエーション（季節・行事）",
    tags: "autumn weekend, onsen town, yukata, leaves",
    motion: "she strolls in yukata with a scarf",
    note: "秋の温泉旅行。",
  },
  h5s_201: {
    label: "冬至のゆず湯",
    group: "シチュエーション（季節・行事）",
    tags: "yuzu bath, citrus floating, steam, winter",
    motion: "she scoops a yuzu from the tub",
    note: "ゆず湯。",
  },
  h5s_202: {
    label: "クリスマスのイルミネーション",
    group: "シチュエーション（季節・行事）",
    tags: "christmas lights, street, couple, coats",
    motion: "she looks up at the shining tree",
    note: "聖夜のイルミ。",
  },
  h5s_203: {
    label: "クリスマスのディナー",
    group: "シチュエーション（季節・行事）",
    tags: "christmas dinner, roast chicken, candles, tree",
    motion: "she pours wine by the tree lights",
    note: "二人のクリスマス。",
  },
  h5s_204: {
    label: "クリスマスのサンタ衣装(大人)",
    group: "シチュエーション（季節・行事）",
    tags: "santa costume, adult woman, tree, gift",
    motion: "she holds a gift behind her back",
    note: "プレゼント。",
  },
  h5s_205: {
    label: "クリスマスケーキ",
    group: "シチュエーション（季節・行事）",
    tags: "christmas cake, strawberries, candle, home",
    motion: "she blows out the candle with a smile",
    note: "ケーキを分ける。",
  },
  h5s_206: {
    label: "大晦日の年越しそば",
    group: "シチュエーション（季節・行事）",
    tags: "new years eve, soba, kotatsu, tv",
    motion: "she slurps noodles and laughs",
    note: "年越しそば。",
  },
  h5s_207: {
    label: "除夜の鐘",
    group: "シチュエーション（季節・行事）",
    tags: "temple bell, midnight, cold, breath visible",
    motion: "she claps her hands with a wish",
    note: "鐘の音を聞く。",
  },
  h5s_208: {
    label: "年越しの花火",
    group: "シチュエーション（季節・行事）",
    tags: "new year countdown fireworks, midnight, crowd",
    motion: "she kisses his cheek as the fireworks burst",
    note: "カウントダウン。",
  },
  h5s_209: {
    label: "お正月の羽子板",
    group: "シチュエーション（季節・行事）",
    tags: "hagoita, new year, kimono, garden",
    motion: "she swings the paddle with a laugh",
    note: "正月遊び。",
  },
  h5s_210: {
    label: "バレンタインの手作りチョコ",
    group: "シチュエーション（季節・行事）",
    tags: "valentine, kitchen, chocolate, apron",
    motion: "she tastes the melted chocolate",
    note: "手作りチョコ。",
  },
  h5s_211: {
    label: "バレンタインのディナー",
    group: "シチュエーション（季節・行事）",
    tags: "valentine dinner, red roses, candles, dress",
    motion: "she receives a rose and smiles",
    note: "バレンタインの夜。",
  },
  h5s_212: {
    label: "誕生日のサプライズ",
    group: "シチュエーション（季節・行事）",
    tags: "birthday surprise, cake, candles, dim room",
    motion: "she gasps as the lights turn on",
    note: "誕生日のサプライズ。",
  },
  h5s_213: {
    label: "誕生日のホテルステイ",
    group: "シチュエーション（季節・行事）",
    tags: "birthday hotel room, balloons, champagne, cake",
    motion: "she blows out candles in bed",
    note: "ホテルでお祝い。",
  },
  h5s_214: {
    label: "記念日のクルーズ",
    group: "シチュエーション（季節・行事）",
    tags: "anniversary cruise, deck dinner, sunset, dress",
    motion: "she raises a glass at sunset",
    note: "記念日のディナークルーズ。",
  },
  h5s_215: {
    label: "結婚式の控え室",
    group: "シチュエーション（季節・行事）",
    tags: "wedding waiting room, white dress, veil, nervous",
    motion: "she breathes deeply and smiles",
    note: "式の前。",
  },
  h5s_216: {
    label: "結婚式のバージンロード",
    group: "シチュエーション（季節・行事）",
    tags: "wedding chapel aisle, flowers, light through stained glass",
    motion: "she walks down the aisle with a bouquet",
    note: "チャペル。",
  },
  h5s_217: {
    label: "結婚式のブーケトス",
    group: "シチュエーション（季節・行事）",
    tags: "wedding bouquet toss, garden, applause",
    motion: "she throws the bouquet backwards",
    note: "ブーケトス。",
  },
  h5s_218: {
    label: "二次会のダンスフロア",
    group: "シチュエーション（季節・行事）",
    tags: "wedding after party dance, suits, dresses, lights",
    motion: "she spins with a glass in hand",
    note: "二次会。",
  },
  h5s_219: {
    label: "ガーデンウェディング",
    group: "シチュエーション（季節・行事）",
    tags: "garden wedding, white chairs, flowers, afternoon",
    motion: "she kisses him under a flower arch",
    note: "庭の結婚式。",
  },
  h5s_220: {
    label: "フォトウェディング",
    group: "シチュエーション（季節・行事）",
    tags: "wedding photo shoot, studio or beach, dress",
    motion: "she poses with a veil blowing",
    note: "写真撮影。",
  },
  h5s_221: {
    label: "新居の引っ越し",
    group: "シチュエーション（季節・行事）",
    tags: "new apartment, boxes, sunlight, laughing",
    motion: "she sits on a box and drinks tea",
    note: "引っ越し。",
  },
  h5s_222: {
    label: "新居の初夜",
    group: "シチュエーション（季節・行事）",
    tags: "new home bedroom, bare, boxes, mattress on floor",
    motion: "she leans on the mattress, smiling",
    note: "新居の最初の夜。",
  },
  h5s_223: {
    label: "夕立のあとの虹",
    group: "シチュエーション（天気・時間）",
    tags: "after rain, rainbow, wet street, summer",
    motion: "she points at the rainbow",
    note: "虹。",
  },
  h5s_224: {
    label: "通り雨の軒下",
    group: "シチュエーション（天気・時間）",
    tags: "eaves, sudden rain, wet clothes, summer evening",
    motion: "she wrings her hair, laughing",
    note: "軒下の雨宿り。",
  },
  h5s_225: {
    label: "びしょ濡れの帰り道",
    group: "シチュエーション（天気・時間）",
    tags: "soaked from rain, evening street, umbrella lost",
    motion: "she pushes wet hair back and laughs",
    note: "雨で濡れて。",
  },
  h5s_226: {
    label: "雨のカフェの窓際",
    group: "シチュエーション（天気・時間）",
    tags: "rainy cafe window, steam, cup, drops",
    motion: "she draws on the fogged glass",
    note: "雨の日のカフェ。",
  },
  h5s_227: {
    label: "雨音の寝室",
    group: "シチュエーション（天気・時間）",
    tags: "rain sound, bedroom, grey light, sheets",
    motion: "she listens to the rain curled up",
    note: "雨の日の寝坊。",
  },
  h5s_228: {
    label: "雨のバスルーム",
    group: "シチュエーション（天気・時間）",
    tags: "bathroom with window, rain, steam, candles",
    motion: "she soaks and listens to the rain",
    note: "雨音を聞きながら入浴。",
  },
  h5s_229: {
    label: "秋の長雨",
    group: "シチュエーション（天気・時間）",
    tags: "autumn rain, quiet room, blanket, tea",
    motion: "she warms hands on a cup",
    note: "雨の日の読書。",
  },
  h5s_230: {
    label: "吹雪の山荘",
    group: "シチュエーション（天気・時間）",
    tags: "blizzard, mountain lodge, fireplace, blankets",
    motion: "she huddles near the fire",
    note: "吹雪の夜。",
  },
  h5s_231: {
    label: "粉雪の朝",
    group: "シチュエーション（天気・時間）",
    tags: "powder snow morning, quiet street, scarf",
    motion: "she makes a footprint trail",
    note: "初雪。",
  },
  h5s_232: {
    label: "雪の降る駅",
    group: "シチュエーション（天気・時間）",
    tags: "snow falling at station, evening, coat, breath",
    motion: "she waits holding a hot can",
    note: "雪の駅。",
  },
  h5s_233: {
    label: "霧の湖畔",
    group: "シチュエーション（天気・時間）",
    tags: "misty lake, dawn, silence, boat",
    motion: "she stands at the dock in the fog",
    note: "霧の朝。",
  },
  h5s_234: {
    label: "朝焼けのベランダ",
    group: "シチュエーション（天気・時間）",
    tags: "morning glow balcony, coffee, robe",
    motion: "she leans on the rail with a mug",
    note: "朝焼け。",
  },
  h5s_235: {
    label: "朝の寝起き",
    group: "シチュエーション（天気・時間）",
    tags: "morning bed, sunlight, messy hair, sheets",
    motion: "she stretches and smiles sleepily",
    note: "寝起き。",
  },
  h5s_236: {
    label: "朝のシャワー",
    group: "シチュエーション（天気・時間）",
    tags: "morning shower, steam, window light",
    motion: "she tilts her face into the water",
    note: "目覚めのシャワー。",
  },
  h5s_237: {
    label: "朝の洗面台",
    group: "シチュエーション（天気・時間）",
    tags: "morning bathroom, mirror, toothbrush, tank top",
    motion: "she brushes teeth and leans on him",
    note: "歯磨き。",
  },
  h5s_238: {
    label: "朝食のテーブル",
    group: "シチュエーション（天気・時間）",
    tags: "breakfast table, toast, sunlight, coffee",
    motion: "she butters toast and offers a bite",
    note: "朝ごはん。",
  },
  h5s_239: {
    label: "昼下がりのカフェ",
    group: "シチュエーション（天気・時間）",
    tags: "afternoon cafe, window seat, sunlight, cake",
    motion: "she feeds him a bite of cake",
    note: "昼のカフェ。",
  },
  h5s_240: {
    label: "昼下がりのソファ",
    group: "シチュエーション（天気・時間）",
    tags: "sunny living room, sofa, afternoon light, book",
    motion: "she dozes with a book on her chest",
    note: "日向のソファ。",
  },
  h5s_241: {
    label: "夕方のスーパーの帰り",
    group: "シチュエーション（天気・時間）",
    tags: "evening shopping bags, sunset street, couple",
    motion: "she carries a bag and hums",
    note: "買い物帰り。",
  },
  h5s_242: {
    label: "夕暮れの公園",
    group: "シチュエーション（天気・時間）",
    tags: "dusk park, swings, orange sky, couple",
    motion: "she sits on the swing smiling",
    note: "公園の夕方。",
  },
  h5s_243: {
    label: "黄昏の橋の上",
    group: "シチュエーション（天気・時間）",
    tags: "twilight bridge, river, city glow",
    motion: "she leans on the rail looking at the water",
    note: "橋の上。",
  },
  h5s_244: {
    label: "薄暮の海",
    group: "シチュエーション（天気・時間）",
    tags: "dusk sea, blue hour, calm waves",
    motion: "she sits on the sand hugging her knees",
    note: "ブルーアワー。",
  },
  h5s_245: {
    label: "月夜のテラス",
    group: "シチュエーション（天気・時間）",
    tags: "moonlit terrace, white dress, breeze",
    motion: "she dances slowly in the moonlight",
    note: "月の光。",
  },
  h5s_246: {
    label: "新月の星空",
    group: "シチュエーション（天気・時間）",
    tags: "new moon, milky way, field, blanket",
    motion: "she points out a shooting star",
    note: "満天の星。",
  },
  h5s_247: {
    label: "真夜中のラーメン",
    group: "シチュエーション（天気・時間）",
    tags: "midnight ramen, counter, steam, hungry",
    motion: "she slurps noodles with satisfied face",
    note: "深夜の一杯。",
  },
  h5s_248: {
    label: "真夜中のベッドで読書",
    group: "シチュエーション（天気・時間）",
    tags: "midnight bed, reading lamp, quiet",
    motion: "she turns pages and looks over",
    note: "夜更かし。",
  },
  h5s_249: {
    label: "深夜のドライブスルー",
    group: "シチュエーション（天気・時間）",
    tags: "late night drive through, neon, car",
    motion: "she hands the bag over, grinning",
    note: "夜食。",
  },
  h5s_250: {
    label: "未明の台所",
    group: "シチュエーション（天気・時間）",
    tags: "dawn kitchen, kettle, dim, pajamas",
    motion: "she pours tea for two",
    note: "眠れない夜。",
  },
  h5s_251: {
    label: "真昼のプール",
    group: "シチュエーション（天気・時間）",
    tags: "noon pool, bright sun, splash, sunglasses",
    motion: "she floats on a ring",
    note: "昼のプール。",
  },
  h5s_252: {
    label: "真夏の縁側",
    group: "シチュエーション（天気・時間）",
    tags: "midsummer veranda, cicadas, watermelon, fan",
    motion: "she wipes sweat and eats melon",
    note: "夏の昼。",
  },
  h5s_253: {
    label: "猛暑の室内",
    group: "シチュエーション（天気・時間）",
    tags: "hot room, fan, ice, tank top, sweat",
    motion: "she holds ice on her neck",
    note: "暑さにだれる。",
  },
  h5s_254: {
    label: "扇風機の前",
    group: "シチュエーション（天気・時間）",
    tags: "electric fan, hair blowing, summer, room",
    motion: "she speaks into the fan",
    note: "扇風機の夏。",
  },
  h5s_255: {
    label: "エアコンの効いた部屋",
    group: "シチュエーション（天気・時間）",
    tags: "cool room, air conditioner, blanket, summer",
    motion: "she wraps in a blanket with ice cream",
    note: "涼しい部屋。",
  },
  h5s_256: {
    label: "梅雨明けの青空",
    group: "シチュエーション（天気・時間）",
    tags: "after rainy season, blue sky, clean air",
    motion: "she stretches arms at the window",
    note: "梅雨明け。",
  },
  h5s_257: {
    label: "台風一過",
    group: "シチュエーション（天気・時間）",
    tags: "after typhoon, clear sky, fallen leaves, breeze",
    motion: "she steps out and breathes",
    note: "嵐のあと。",
  },
  h5s_258: {
    label: "停電のろうそく",
    group: "シチュエーション（天気・時間）",
    tags: "power outage, candles, flicker, kitchen table",
    motion: "she lights a candle and smiles",
    note: "停電の夜。",
  },
  h5s_259: {
    label: "雷雨の窓辺",
    group: "シチュエーション（天気・時間）",
    tags: "thunderstorm, window flash, dark room",
    motion: "she jumps at thunder and grabs his arm",
    note: "雷が怖い。",
  },
  h5s_260: {
    label: "朝霧の高原",
    group: "シチュエーション（天気・時間）",
    tags: "highland morning mist, meadow, wooden fence",
    motion: "she walks through mist in a coat",
    note: "高原の朝。",
  },
  h5s_261: {
    label: "同棲カップルの日曜",
    group: "シチュエーション（関係・役割）",
    tags: "living together, sunday morning, pajamas, sofa",
    motion: "she curls up against him",
    note: "日曜の朝。",
  },
  h5s_262: {
    label: "結婚記念日の夫婦",
    group: "シチュエーション（関係・役割）",
    tags: "married couple, anniversary, dinner, rings",
    motion: "she smiles and clinks glasses",
    note: "結婚記念日。",
  },
  h5s_263: {
    label: "年上の恋人",
    group: "シチュエーション（関係・役割）",
    tags: "older lover, calm, stylish, hotel bar",
    motion: "she tucks his tie and teases",
    note: "大人の余裕。",
  },
  h5s_264: {
    label: "年下の恋人(成人)",
    group: "シチュエーション（関係・役割）",
    tags: "younger adult lover, casual, playful",
    motion: "she pokes his cheek playfully",
    note: "甘えてくる。",
  },
  h5s_266: {
    label: "同窓会のあと(大人同士)",
    group: "シチュエーション（関係・役割）",
    tags: "reunion after party, night street, formal wear",
    motion: "she walks him home slightly tipsy",
    note: "同窓会の帰り道。",
  },
  h5s_267: {
    label: "隣の部屋の住人",
    group: "シチュエーション（関係・役割）",
    tags: "neighbors, apartment corridor, grocery bag",
    motion: "she asks to borrow some salt",
    note: "隣の人。",
  },
  h5s_268: {
    label: "ルームシェア",
    group: "シチュエーション（関係・役割）",
    tags: "roommates, shared apartment, kitchen, casual",
    motion: "she sits on the counter eating cereal",
    note: "同居人。",
  },
  h5s_269: {
    label: "シェアハウスの夜",
    group: "シチュエーション（関係・役割）",
    tags: "share house living room, night, wine, friends",
    motion: "she curls on the sofa with a glass",
    note: "共有スペース。",
  },
  h5s_270: {
    label: "同僚との出張",
    group: "シチュエーション（関係・役割）",
    tags: "colleagues business trip, hotel, suitcases, suits",
    motion: "she kicks off heels, relaxes",
    note: "出張先。",
  },
  h5s_271: {
    label: "同僚の飲み会帰り",
    group: "シチュエーション（関係・役割）",
    tags: "coworkers after drinks, night street, suits",
    motion: "she leans on him, flushed",
    note: "二次会のあと。",
  },
  h5s_272: {
    label: "先輩と後輩(職場)",
    group: "シチュエーション（関係・役割）",
    tags: "senior and junior coworker, office after hours",
    motion: "she explains something close to his ear",
    note: "職場の先輩。",
  },
  h5s_273: {
    label: "社長と秘書",
    group: "シチュエーション（関係・役割）",
    tags: "executive office, city view, secretary suit",
    motion: "she pours coffee and lingers",
    note: "社長室。",
  },
  h5s_274: {
    label: "編集者と作家",
    group: "シチュエーション（関係・役割）",
    tags: "editor and writer, desk, manuscripts, night",
    motion: "she hands back a red-marked page",
    note: "編集者。",
  },
  h5s_276: {
    label: "写真家とモデル",
    group: "シチュエーション（関係・役割）",
    tags: "photographer and model, studio, softbox",
    motion: "she changes pose per direction",
    note: "撮影。",
  },
  h5s_277: {
    label: "ダンサーとパートナー",
    group: "シチュエーション（関係・役割）",
    tags: "dance partners, studio, leotard, mirrors",
    motion: "she leans into a lift",
    note: "ダンス。",
  },
  h5s_278: {
    label: "ピアニストと聴き手",
    group: "シチュエーション（関係・役割）",
    tags: "pianist, concert hall at night, black dress",
    motion: "she plays and glances back",
    note: "ピアノ。",
  },
  h5s_279: {
    label: "バーテンダーとの夜",
    group: "シチュエーション（関係・役割）",
    tags: "bartender, shaker, bar counter, late",
    motion: "she watches him mix and smiles",
    note: "カクテル。",
  },
  h5s_280: {
    label: "ソムリエとお客",
    group: "シチュエーション（関係・役割）",
    tags: "sommelier, restaurant, wine bottle, napkin",
    motion: "she swirls and tastes",
    note: "ワインの講釈。",
  },
  h5s_281: {
    label: "料理人と客",
    group: "シチュエーション（関係・役割）",
    tags: "chef and customer, open kitchen, counter",
    motion: "she tastes from a spoon",
    note: "カウンターの寿司。",
  },
  h5s_282: {
    label: "旅先で出会った二人",
    group: "シチュエーション（関係・役割）",
    tags: "travelers meeting, hostel lounge, backpacks",
    motion: "she shares a map and a laugh",
    note: "旅の出会い。",
  },
  h5s_283: {
    label: "ビーチで知り合った二人",
    group: "シチュエーション（関係・役割）",
    tags: "met on beach, sunset, bar",
    motion: "she offers a cold drink",
    note: "ビーチの出会い。",
  },
  h5s_284: {
    label: "スキー場の出会い",
    group: "シチュエーション（関係・役割）",
    tags: "met at ski resort, lodge, goggles",
    motion: "she lifts goggles and smiles",
    note: "雪山で出会う。",
  },
  h5s_285: {
    label: "登山の途中の出会い",
    group: "シチュエーション（関係・役割）",
    tags: "met on hiking trail, hut, rest",
    motion: "she shares tea from a thermos",
    note: "山の出会い。",
  },
  h5s_286: {
    label: "航海中の船客",
    group: "シチュエーション（関係・役割）",
    tags: "ship passenger, deck, evening, formal",
    motion: "she hands him a glass at the rail",
    note: "船上のロマンス。",
  },
  h5s_287: {
    label: "外国人の恋人",
    group: "シチュエーション（関係・役割）",
    tags: "international couple, travel, airport, hug",
    motion: "she says a word in her language softly",
    note: "異国の恋。",
  },
  h5s_288: {
    label: "元恋人との旅",
    group: "シチュエーション（関係・役割）",
    tags: "ex-lovers reunited trip, car, silent, afterglow",
    motion: "she looks at him with a nostalgic smile",
    note: "復縁。",
  },
  h5s_289: {
    label: "許嫁との再会",
    group: "シチュエーション（関係・役割）",
    tags: "arranged fiance reunion, kimono, garden",
    motion: "she bows and looks up shyly",
    note: "許嫁。",
  },
  h5s_290: {
    label: "長年仕えた執事",
    group: "シチュエーション（関係・役割）",
    tags: "butler, formal, mansion, calm",
    motion: "she receives a cup of tea",
    note: "執事。",
  },
  h5s_291: {
    label: "お屋敷の令嬢と護衛",
    group: "シチュエーション（関係・役割）",
    tags: "noble lady and bodyguard, mansion, dress, suit",
    motion: "she dismisses others and tilts her head",
    note: "護衛。",
  },
  h5s_292: {
    label: "女将と常連客",
    group: "シチュエーション（関係・役割）",
    tags: "ryokan proprietress, kimono, regular guest, sake",
    motion: "she pours a cup with graceful hands",
    note: "女将。",
  },
  h5s_293: {
    label: "仲居と宿泊客",
    group: "シチュエーション（関係・役割）",
    tags: "ryokan attendant, kimono, futon laying, evening",
    motion: "she folds the sheet with a smile",
    note: "仲居。",
  },
  h5s_294: {
    label: "和服の師匠と弟子",
    group: "シチュエーション（関係・役割）",
    tags: "tea master, kimono, tatami, disciple",
    motion: "she corrects his posture gently",
    note: "茶道。",
  },
  h5s_295: {
    label: "ヨガのパートナー",
    group: "シチュエーション（関係・役割）",
    tags: "partner yoga, mat, studio, leggings",
    motion: "she balances against his back",
    note: "ヨガ。",
  },
  h5s_297: {
    label: "ジムのトレーナー",
    group: "シチュエーション（関係・役割）",
    tags: "gym trainer, adult, towel, equipment",
    motion: "she corrects his form with a hand",
    note: "トレーニング。",
  },
  h5s_298: {
    label: "スタイリストと客",
    group: "シチュエーション（関係・役割）",
    tags: "stylist, fitting room, clothes, mirrors",
    motion: "she pins a hem on him",
    note: "スタイリスト。",
  },
  h5s_299: {
    label: "ホテルのコンシェルジュ",
    group: "シチュエーション（関係・役割）",
    tags: "hotel concierge, lobby, uniform, night",
    motion: "she slides a key card over the desk",
    note: "コンシェルジュ。",
  },
  h5s_300: {
    label: "客室乗務員と機長",
    group: "シチュエーション（関係・役割）",
    tags: "flight attendant and captain, layover hotel, uniform",
    motion: "she removes her scarf wearily",
    note: "ホテルでの乗務員。",
  },
  h5s_301: {
    label: "バスガイドの休憩",
    group: "シチュエーション（関係・役割）",
    tags: "tour guide, bus, hat, break",
    motion: "she sips tea and sighs",
    note: "旅の休憩。",
  },
  h5s_302: {
    label: "オフの日の女医",
    group: "シチュエーション（関係・役割）",
    tags: "doctor off duty, bar, casual",
    motion: "she takes off her glasses, relaxing",
    note: "オフの先生。",
  },
  h5s_303: {
    label: "弁護士と依頼人",
    group: "シチュエーション（関係・役割）",
    tags: "lawyer, office night, documents",
    motion: "she closes the file and sighs",
    note: "事務所。",
  },
  h5s_304: {
    label: "記者と取材相手",
    group: "シチュエーション（関係・役割）",
    tags: "reporter, cafe, notebook, recorder",
    motion: "she leans to hear his answer",
    note: "取材。",
  },
  h5s_305: {
    label: "夜の書店員",
    group: "シチュエーション（関係・役割）",
    tags: "bookstore clerk, evening, shelves, apron",
    motion: "she pulls a book down for him",
    note: "書店。",
  },
  h5s_306: {
    label: "花屋の店主",
    group: "シチュエーション（関係・役割）",
    tags: "florist, flower shop, apron, bouquet",
    motion: "she fixes a flower in his lapel",
    note: "花屋。",
  },
  h5s_307: {
    label: "パティシエと試食",
    group: "シチュエーション（関係・役割）",
    tags: "patissier, kitchen, tasting, apron",
    motion: "she holds a spoon out",
    note: "試食。",
  },
  h5s_308: {
    label: "ライバル会社の二人",
    group: "シチュエーション（関係・役割）",
    tags: "rival company colleagues, bar, suits",
    motion: "she raises a glass with a challenge",
    note: "ライバル。",
  },
  h5s_309: {
    label: "ライバルのレーサー",
    group: "シチュエーション（関係・役割）",
    tags: "rival racers, garage, suits, helmets",
    motion: "she tosses him her keys",
    note: "レース。",
  },
  h5s_310: {
    label: "剣の師範と門下(大人)",
    group: "シチュエーション（関係・役割）",
    tags: "swordsmaster, dojo, hakama, adult disciple",
    motion: "she adjusts his grip",
    note: "道場。",
  },
  h5s_311: {
    label: "貴族と庶民",
    group: "シチュエーション（関係・役割）",
    tags: "noble and commoner, ball, palace garden",
    motion: "she slips away from the ballroom",
    note: "舞踏会。",
  },
  h5s_312: {
    label: "王妃と近衛",
    group: "シチュエーション（関係・役割）",
    tags: "queen and royal guard, throne room, fantasy",
    motion: "she extends her hand to be kissed",
    note: "近衛。",
  },
  h5s_313: {
    label: "踊り子と旅人",
    group: "シチュエーション（関係・役割）",
    tags: "dancer and traveler, desert tavern, silk",
    motion: "she dances and beckons",
    note: "砂漠の宿。",
  },
  h5s_314: {
    label: "海賊と船長(大人)",
    group: "シチュエーション（関係・役割）",
    tags: "pirate captain, ship cabin, rum",
    motion: "she points to a map",
    note: "船長室。",
  },
  h5s_315: {
    label: "魔法使いと使い魔",
    group: "シチュエーション（関係・役割）",
    tags: "wizard and familiar, tower room, candles",
    motion: "she traces a rune in the air",
    note: "魔法使い。",
  },
  h5s_316: {
    label: "手を恋人つなぎ",
    group: "シチュエーション（雰囲気）",
    tags: "holding hands interlaced, walking, street",
    motion: "she swings their hands",
    note: "恋人つなぎ。",
  },
  h5s_317: {
    label: "腕を組んで歩く",
    group: "シチュエーション（雰囲気）",
    tags: "arm in arm walking, evening street",
    motion: "she hugs his arm",
    note: "腕組み。",
  },
  h5s_318: {
    label: "肩にもたれる",
    group: "シチュエーション（雰囲気）",
    tags: "resting head on shoulder, train or bench",
    motion: "she leans on his shoulder and closes her eyes",
    note: "もたれる。",
  },
  h5s_319: {
    label: "後ろからハグして耳に息",
    group: "シチュエーション（雰囲気）",
    tags: "hugging from behind, whisper, warm",
    motion: "she shivers as he breathes at her ear",
    note: "耳元の吐息。",
  },
  h5s_320: {
    label: "前髪を直してもらう",
    group: "シチュエーション（雰囲気）",
    tags: "adjusting her bangs, close face, tender",
    motion: "she holds still as fingers brush her hair",
    note: "優しい仕草。",
  },
  h5s_321: {
    label: "ボタンを留めてもらう",
    group: "シチュエーション（雰囲気）",
    tags: "buttoning her blouse, close, intimate",
    motion: "she lifts her chin as he fastens",
    note: "身支度。",
  },
  h5s_322: {
    label: "ネクタイを結ぶ",
    group: "シチュエーション（雰囲気）",
    tags: "tying his tie, close, morning",
    motion: "she pulls the knot up with a smile",
    note: "朝の支度。",
  },
  h5s_323: {
    label: "ファスナーを上げてもらう",
    group: "シチュエーション（雰囲気）",
    tags: "zipping dress, back, mirror",
    motion: "she holds hair up as he zips",
    note: "背中。",
  },
  h5s_324: {
    label: "ペアルックの朝",
    group: "シチュエーション（雰囲気）",
    tags: "matching pajamas, morning, couple",
    motion: "she yawns beside him",
    note: "ペアパジャマ。",
  },
  h5s_325: {
    label: "一つのマフラー",
    group: "シチュエーション（雰囲気）",
    tags: "sharing scarf, winter, couple",
    motion: "she wraps the scarf around both",
    note: "マフラー。",
  },
  h5s_326: {
    label: "一つの傘",
    group: "シチュエーション（雰囲気）",
    tags: "sharing umbrella, rain, close",
    motion: "she pulls him closer under it",
    note: "相合い傘。",
  },
  h5s_327: {
    label: "ひとつの毛布",
    group: "シチュエーション（雰囲気）",
    tags: "sharing blanket, sofa, movie night",
    motion: "she tucks in beside him",
    note: "毛布。",
  },
  h5s_328: {
    label: "映画鑑賞の夜",
    group: "シチュエーション（雰囲気）",
    tags: "movie night, projector, popcorn, sofa",
    motion: "she turns away from the screen to look at him",
    note: "映画。",
  },
  h5s_329: {
    label: "一緒に料理",
    group: "シチュエーション（雰囲気）",
    tags: "cooking together, kitchen, apron, laughing",
    motion: "she tastes sauce from his spoon",
    note: "二人で料理。",
  },
  h5s_330: {
    label: "キッチンでのつまみ食い",
    group: "シチュエーション（雰囲気）",
    tags: "kitchen snack steal, apron, laughing",
    motion: "she slaps his hand playfully",
    note: "つまみ食い。",
  },
  h5s_331: {
    label: "後ろから包丁を握る手",
    group: "シチュエーション（雰囲気）",
    tags: "guiding hands cooking, behind, kitchen",
    motion: "she focuses as he shows how to slice",
    note: "手を添える。",
  },
  h5s_332: {
    label: "お風呂上がりのタオルドライ",
    group: "シチュエーション（雰囲気）",
    tags: "after bath, towel, wet hair, warm",
    motion: "she dries his hair with a towel",
    note: "お風呂上がり。",
  },
  h5s_333: {
    label: "お酒を口移し風に",
    group: "シチュエーション（雰囲気）",
    tags: "sharing sip of wine, close faces, candle",
    motion: "she holds her glass to his lips",
    note: "グラスを分ける。",
  },
  h5s_334: {
    label: "アイスの食べさせ合い",
    group: "シチュエーション（雰囲気）",
    tags: "sharing ice cream, summer, tongue, laugh",
    motion: "she licks and offers it",
    note: "アイス。",
  },
  h5s_335: {
    label: "ポッキーゲーム",
    group: "シチュエーション（雰囲気）",
    tags: "stick candy game, close faces, giggle",
    motion: "she bites her end with a grin",
    note: "お菓子のゲーム。",
  },
  h5s_336: {
    label: "ダンスの手ほどき",
    group: "シチュエーション（雰囲気）",
    tags: "dance lesson, close hold, living room",
    motion: "she places her hand on his shoulder",
    note: "ダンス。",
  },
  h5s_337: {
    label: "マッサージの交換",
    group: "シチュエーション（雰囲気）",
    tags: "exchanging massage, bed, oil, soft",
    motion: "she presses his shoulders",
    note: "肩もみ。",
  },
  h5s_338: {
    label: "肩もみのお返し",
    group: "シチュエーション（雰囲気）",
    tags: "shoulder massage thanks, sofa, relaxed",
    motion: "she purrs as he kneads",
    note: "マッサージ。",
  },
  h5s_339: {
    label: "首筋のマッサージ",
    group: "シチュエーション（雰囲気）",
    tags: "neck massage, hair up, warm",
    motion: "she sighs and tilts her head",
    note: "首筋。",
  },
  h5s_340: {
    label: "オイルマッサージ",
    group: "シチュエーション（雰囲気）",
    tags: "oil massage, bed, towel, candle",
    motion: "she lies down while warm hands slide",
    note: "オイル。",
  },
  h5s_341: {
    label: "足裏マッサージ",
    group: "シチュエーション（雰囲気）",
    tags: "foot massage, sofa, relaxed",
    motion: "she giggles from the tickle",
    note: "足裏。",
  },
  h5s_342: {
    label: "耳かきのお返し",
    group: "シチュエーション（雰囲気）",
    tags: "ear cleaning thanks, lap pillow, quiet",
    motion: "she closes her eyes on his lap",
    note: "耳かき。",
  },
  h5s_343: {
    label: "彼の膝に頭を乗せる",
    group: "シチュエーション（雰囲気）",
    tags: "head on his lap, reading, afternoon",
    motion: "she looks up as he strokes her hair",
    note: "膝枕の逆。",
  },
  h5s_344: {
    label: "抱きしめて眠る",
    group: "シチュエーション（雰囲気）",
    tags: "sleeping in embrace, bed, quiet",
    motion: "she breathes slowly against his chest",
    note: "抱擁。",
  },
  h5s_345: {
    label: "腕枕",
    group: "シチュエーション（雰囲気）",
    tags: "arm pillow, bed, quiet night",
    motion: "she nestles in his arm",
    note: "腕枕。",
  },
  h5s_346: {
    label: "背中合わせ",
    group: "シチュエーション（雰囲気）",
    tags: "back to back, sitting, relax",
    motion: "she leans her weight on his back",
    note: "背中合わせ。",
  },
  h5s_347: {
    label: "寄り添って星を見る",
    group: "シチュエーション（雰囲気）",
    tags: "stargazing together, blanket",
    motion: "she traces constellations on his palm",
    note: "星空。",
  },
  h5s_348: {
    label: "ベランダで月を見る",
    group: "シチュエーション（雰囲気）",
    tags: "moon viewing balcony, wine, night",
    motion: "she raises a glass to the moon",
    note: "月見酒。",
  },
  h5s_349: {
    label: "暖炉の前のダンス",
    group: "シチュエーション（雰囲気）",
    tags: "fireplace dance, slow, night",
    motion: "she rests her cheek on his chest",
    note: "ダンス。",
  },
  h5s_350: {
    label: "キャンドルライトのバス",
    group: "シチュエーション（雰囲気）",
    tags: "candlelit bath, petals, wine",
    motion: "she floats petals with a fingertip",
    note: "キャンドルバス。",
  },
  h5s_351: {
    label: "花びらを散らしたベッド",
    group: "シチュエーション（雰囲気）",
    tags: "rose petals on bed, hotel, night",
    motion: "she lies among petals",
    note: "薔薇のベッド。",
  },
  h5s_352: {
    label: "風船の部屋",
    group: "シチュエーション（雰囲気）",
    tags: "room full of balloons, party, confetti",
    motion: "she bursts into laughter",
    note: "サプライズ。",
  },
  h5s_353: {
    label: "紙吹雪のキス",
    group: "シチュエーション（雰囲気）",
    tags: "confetti, kiss, party",
    motion: "she kisses him as confetti falls",
    note: "キス。",
  },
  h5s_354: {
    label: "指輪の贈り物",
    group: "シチュエーション（雰囲気）",
    tags: "ring gift, kneeling, proposal, candle",
    motion: "she covers her mouth in surprise",
    note: "プロポーズ。",
  },
  h5s_355: {
    label: "プロポーズの夜景",
    group: "シチュエーション（雰囲気）",
    tags: "proposal, skyline, tears of joy",
    motion: "she says yes with a smile",
    note: "プロポーズ。",
  },
  h5s_356: {
    label: "手紙を読む",
    group: "シチュエーション（雰囲気）",
    tags: "reading love letter, window, soft light",
    motion: "she holds the letter to her chest",
    note: "手紙。",
  },
  h5s_357: {
    label: "照れ隠しの視線",
    group: "シチュエーション（雰囲気）",
    tags: "shy gaze, looking away, blush",
    motion: "she turns her head away and peeks",
    note: "照れ。",
  },
  h5s_358: {
    label: "潤んだ瞳で見上げる",
    group: "シチュエーション（雰囲気）",
    tags: "looking up with moist eyes, close",
    motion: "she whispers his name",
    note: "見上げる。",
  },
  h5s_359: {
    label: "いたずらな笑み",
    group: "シチュエーション（雰囲気）",
    tags: "mischievous grin, teasing",
    motion: "she tugs his collar with a smirk",
    note: "いたずら。",
  },
  h5s_360: {
    label: "耳たぶを甘噛み",
    group: "シチュエーション（雰囲気）",
    tags: "gentle ear nibble, close, whisper",
    motion: "she flinches and smiles",
    note: "甘噛み。",
  },
  h5s_361: {
    label: "指先にキス",
    group: "シチュエーション（雰囲気）",
    tags: "kiss on fingertips, hand held",
    motion: "she lets him kiss each fingertip",
    note: "指先。",
  },
  h5s_362: {
    label: "手の甲へのキス",
    group: "シチュエーション（雰囲気）",
    tags: "hand kiss, formal, dress",
    motion: "she extends her hand gracefully",
    note: "手の甲。",
  },
  h5s_363: {
    label: "うなじへのキス",
    group: "シチュエーション（雰囲気）",
    tags: "nape kiss, hair up, behind",
    motion: "she shivers as hair is lifted",
    note: "うなじ。",
  },
  h5s_364: {
    label: "鎖骨のキス",
    group: "シチュエーション（雰囲気）",
    tags: "collarbone kiss, off shoulder, soft light",
    motion: "she tilts her neck",
    note: "鎖骨。",
  },
  h5s_365: {
    label: "背中へのキス",
    group: "シチュエーション（雰囲気）",
    tags: "back kiss, bare back, bed",
    motion: "she closes her eyes",
    note: "背中。",
  },
  h5s_366: {
    label: "ひざへのキス",
    group: "シチュエーション（雰囲気）",
    tags: "kiss on knee, dress, bedside",
    motion: "she smiles down",
    note: "ひざ。",
  },
  h5s_367: {
    label: "足の指先へのキス",
    group: "シチュエーション（雰囲気）",
    tags: "foot kiss, bed, tender",
    motion: "she curls her toes",
    note: "足。",
  },
  h5s_368: {
    label: "濡れた髪をかきあげる",
    group: "シチュエーション（雰囲気）",
    tags: "pushing back wet hair, pool or bath",
    motion: "she slicks her hair back",
    note: "濡れ髪。",
  },
  h5s_369: {
    label: "濡れたシャツ越しの肌",
    group: "シチュエーション（雰囲気）",
    tags: "wet shirt, translucent, rain or pool",
    motion: "she tugs the fabric as it clings",
    note: "濡れた服。",
  },
  h5s_370: {
    label: "バスローブをはだける",
    group: "シチュエーション（雰囲気）",
    tags: "robe slipping off the shoulder, bed",
    motion: "she lets the robe fall slowly",
    note: "バスローブ。",
  },
  h5s_371: {
    label: "シーツにくるまる",
    group: "シチュエーション（雰囲気）",
    tags: "wrapped in sheets, morning, bed",
    motion: "she peeks out from sheets",
    note: "シーツ。",
  },
  h5s_372: {
    label: "水着の紐を結んでもらう",
    group: "シチュエーション（雰囲気）",
    tags: "tying swimsuit string, behind, beach",
    motion: "she lifts her hair",
    note: "水着。",
  },
  h5s_373: {
    label: "日焼け止めを塗り合う",
    group: "シチュエーション（雰囲気）",
    tags: "applying sunscreen, beach, lotion",
    motion: "she rubs it on his back",
    note: "日焼け止め。",
  },
  h5s_374: {
    label: "砂のついた肌",
    group: "シチュエーション（雰囲気）",
    tags: "sand on skin, beach, wet",
    motion: "she brushes sand off her legs",
    note: "砂。",
  },
  h5s_375: {
    label: "ビーチタオルに寝そべる",
    group: "シチュエーション（雰囲気）",
    tags: "lying on towel, beach, sunglasses",
    motion: "she props up on her elbows",
    note: "ビーチ。",
  },
  h5s_376: {
    label: "プールでじゃれ合い",
    group: "シチュエーション（雰囲気）",
    tags: "pool horseplay, splash, water",
    motion: "she splashes and swims away",
    note: "プール。",
  },
  h5s_377: {
    label: "浮き輪で漂う",
    group: "シチュエーション（雰囲気）",
    tags: "floating on tube, pool, sun",
    motion: "she drifts with eyes closed",
    note: "浮き輪。",
  },
  h5s_378: {
    label: "水かけっこ",
    group: "シチュエーション（雰囲気）",
    tags: "water splash fight, summer, laughing",
    motion: "she sprays water with a hose",
    note: "水遊び。",
  },
  h5s_379: {
    label: "雪合戦",
    group: "シチュエーション（雰囲気）",
    tags: "snowball fight, winter, scarves",
    motion: "she throws and runs",
    note: "雪遊び。",
  },
  h5s_380: {
    label: "雪だるまづくり",
    group: "シチュエーション（雰囲気）",
    tags: "building snowman, gloves, winter",
    motion: "she places a scarf on it",
    note: "雪だるま。",
  },
  h5s_381: {
    label: "温かい飲み物を分ける",
    group: "シチュエーション（雰囲気）",
    tags: "sharing a hot drink, cold night",
    motion: "she holds the cup for him to sip",
    note: "ココア。",
  },
  h5s_382: {
    label: "酔いつぶれた肩を貸す",
    group: "シチュエーション（雰囲気）",
    tags: "helping drunk partner, night street",
    motion: "she supports him laughing",
    note: "酔っ払い。",
  },
  h5s_383: {
    label: "二日酔いの朝の看病",
    group: "シチュエーション（雰囲気）",
    tags: "hangover morning, water, bed",
    motion: "she brings water and a sigh",
    note: "看病。",
  },
  h5s_384: {
    label: "風邪の看病",
    group: "シチュエーション（雰囲気）",
    tags: "caring for cold, bed, towel, soup",
    motion: "she presses a cool cloth",
    note: "看病。",
  },
  h5s_385: {
    label: "寝顔を見つめる",
    group: "シチュエーション（雰囲気）",
    tags: "watching partner sleep, morning light",
    motion: "she smiles looking at his face",
    note: "寝顔。",
  },
  h5s_386: {
    label: "寝ぼけて抱きつく",
    group: "シチュエーション（雰囲気）",
    tags: "sleepy hug, bed, morning",
    motion: "she clings with eyes closed",
    note: "寝ぼけ。",
  },
  h5s_387: {
    label: "いってきますのキス",
    group: "シチュエーション（雰囲気）",
    tags: "goodbye kiss at door, morning, suit",
    motion: "she straightens his collar",
    note: "玄関。",
  },
  h5s_388: {
    label: "おかえりのハグ",
    group: "シチュエーション（雰囲気）",
    tags: "welcome home hug, evening, door",
    motion: "she jumps into his arms",
    note: "玄関。",
  },
  // ─ シチュエーション（追加）─
  h6s_rain_walk: {
    label: "雨の中",
    group: "シチュエーション（天気・時間）",
    tags: "walking in the rain, heavy rain, wet street, wet hair, rain drops, gray sky",
    motion: "she walks through the rain with her face turned up, rain running down her cheeks",
    note: "強い雨の中を歩いていて、全身に雨が当たる。",
  },
  h6s_rain_downpour: {
    label: "土砂降りの雨",
    group: "シチュエーション（天気・時間）",
    tags: "downpour, torrential rain, puddles, splashing water, soaked",
    motion: "she runs through the downpour, water splashing at her feet",
    note: "叩きつけるような雨で、水しぶきが上がる。",
  },
  h6s_rain_umbrella: {
    label: "ひとつの傘で",
    group: "シチュエーション（天気・時間）",
    tags: "sharing an umbrella in the rain, close together, wet shoulders, evening",
    motion: "they walk under one umbrella, shoulders touching",
    note: "ひとつの傘の下で寄りそう。",
  },
  h6s_rain_alley: {
    label: "雨の路地裏",
    group: "シチュエーション（天気・時間）",
    tags: "rainy back alley, neon reflections on wet ground, wet walls, night",
    motion: "she leans against the wet wall as neon lights reflect in puddles",
    note: "ネオンが水たまりに映る、雨の夜の路地。",
  },
  h6s_soaked: {
    label: "ずぶ濡れ",
    group: "シチュエーション（雰囲気）",
    tags: "completely soaked, drenched, wet hair stuck to face, wet clothes clinging to skin, dripping water, see-through wet clothes",
    motion: "she wrings out her dripping clothes, water running down her body",
    note: "全身ずぶ濡れで、服が肌に張りついている。",
  },
  h6s_soaked_shirt: {
    label: "濡れ透け（シャツ）",
    group: "シチュエーション（雰囲気）",
    tags: "wet white shirt, see-through wet shirt, clinging to skin, bra visible through shirt, dripping",
    motion: "she pulls at her wet shirt that clings to her skin",
    note: "濡れた白いシャツが透けている。",
  },
  h6s_soaked_dress: {
    label: "濡れたワンピース",
    group: "シチュエーション（雰囲気）",
    tags: "wet dress clinging to body, thin dress soaked, dripping hem, wet hair",
    motion: "she holds the hem of her soaked dress, water dripping",
    note: "雨で濡れたワンピースが体に張りつく。",
  },
  h6s_soaked_towel: {
    label: "ずぶ濡れのあとタオルで拭く",
    group: "シチュエーション（雰囲気）",
    tags: "wiping with a towel after getting soaked, wet hair, towel on shoulders, wet clothes, entryway",
    motion: "she dries her hair with a towel, still dripping",
    note: "雨に濡れたあと、玄関でタオルで拭く。",
  },
  h6s_soaked_shower: {
    label: "シャワーを浴びる",
    group: "シチュエーション（場所）",
    tags: "taking a shower, water streaming over body, steamy bathroom, wet skin, wet hair",
    motion: "she tilts her head back under the shower, water streaming down",
    note: "シャワーのお湯が体を流れる。",
  },
  h6s_car_night: {
    label: "夜の車の中",
    group: "シチュエーション（場所）",
    tags: "inside a car at night, dim dashboard lights, fogged windows, parked car, intimate atmosphere",
    motion: "she leans toward her partner in the dim car, the windows fogging",
    note: "暗い車内。窓が曇っている。",
  },
  h6s_car_back_seat: {
    label: "車の後部座席",
    group: "シチュエーション（場所）",
    tags: "car back seat, sitting close together, dim light through windows, steamy windows, rear seat",
    motion: "she slides across the back seat toward her partner",
    note: "後部座席で体を寄せ合う。",
  },
  h6s_car_parking_lot: {
    label: "駐車場に停めた車",
    group: "シチュエーション（場所）",
    tags: "car in a parking lot at night, street lamp light, fogged windows, empty parking lot",
    motion: "the car sways slightly as the windows fog up",
    note: "夜の駐車場で、車の窓が曇っている。",
  },
  h6s_car_mountain: {
    label: "山道の展望スポットの車内",
    group: "シチュエーション（場所）",
    tags: "car parked at a mountain overlook at night, city lights below, starry sky, windows fogged",
    motion: "she looks out at the city lights, then turns to her partner",
    note: "夜景の見える山道で車を停める。",
  },
  h6s_car_rain: {
    label: "雨の日の車内",
    group: "シチュエーション（場所）",
    tags: "inside a car in the rain, rain on windshield, fogged windows, wet clothes, dim light",
    motion: "raindrops run down the windshield as she leans close",
    note: "雨音が響く車内で、窓が曇る。",
  },
  h6s_outdoor_night_park: {
    label: "夜の公園",
    group: "シチュエーション（場所）",
    tags: "night park, street lamps, empty bench, trees, quiet outdoor, moonlight",
    motion: "she looks around the quiet park before moving closer",
    note: "人のいない夜の公園。",
  },
  h6s_outdoor_forest: {
    label: "森の中",
    group: "シチュエーション（場所）",
    tags: "in a forest, sunlight through trees, grass, secluded clearing, outdoors",
    motion: "she lies in the grass of a secluded clearing",
    note: "人目につかない森の空き地。",
  },
  h6s_outdoor_beach_night: {
    label: "夜の海辺",
    group: "シチュエーション（場所）",
    tags: "night beach, waves, moonlight, sand, rocks, outdoors",
    motion: "she walks along the waterline, waves washing at her feet",
    note: "月明かりの夜の砂浜。",
  },
  h6s_outdoor_rooftop: {
    label: "夜の屋上",
    group: "シチュエーション（場所）",
    tags: "rooftop at night, city lights, wind, fence, outdoors, starry sky",
    motion: "the wind blows her hair as she leans on the rooftop fence",
    note: "夜景の見える屋上。",
  },
  h6s_outdoor_tent: {
    label: "テントの中",
    group: "シチュエーション（場所）",
    tags: "inside a tent, sleeping bag, lantern light, camping, outdoors at night, cozy",
    motion: "she lies in the lantern light inside the tent",
    note: "ランタンの灯るテントの中。",
  },
  h6s_outdoor_exposure: {
    label: "野外で服をゆるめる",
    group: "シチュエーション（雰囲気）",
    tags: "outdoors, loosening clothes in a secluded place, blushing, looking around nervously, daytime",
    motion: "she glances around nervously while loosening her clothes",
    note: "人目を気にしながら、野外で服をゆるめる。",
  },
  h6s_outdoor_trail: {
    label: "ひと気のない遊歩道",
    group: "シチュエーション（場所）",
    tags: "quiet walking trail, trees, deserted path, evening, outdoors",
    motion: "she stops on the deserted path and turns around",
    note: "ひと気のない夕暮れの遊歩道。",
  },
  h6s_closet_dark: {
    label: "押入れの中",
    group: "シチュエーション（場所）",
    tags: "inside a closet, futon, dark, narrow space, dim light through the gap, sliding door ajar",
    motion: "she sits curled up in the narrow closet, light slipping through the gap",
    note: "暗くてせまい押入れの中。",
  },
  h6s_closet_hide: {
    label: "押入れに隠れる",
    group: "シチュエーション（雰囲気）",
    tags: "hiding in a closet together, close together, whispering, dim light, tight space",
    motion: "she holds her breath beside her partner in the closet",
    note: "ふたりで押入れに隠れて、息をひそめる。",
  },
  h6s_closet_futon: {
    label: "押入れから布団を出す",
    group: "シチュエーション（雰囲気）",
    tags: "taking out a futon from the closet, japanese room, tatami, night, preparing the bed",
    motion: "she pulls the futon out of the closet and spreads it on the tatami",
    note: "押入れから布団を出して、寝る支度をする。",
  },
  h6s_bound_hogtie: {
    label: "手足をまとめて縛る（同意の上）",
    group: "シチュエーション（雰囲気）",
    tags: "hogtie bondage, wrists and ankles tied together with soft rope, lying on bed, consensual play, trust",
    motion: "she lies hogtied on the bed with a trusting expression",
    note: "同意の上のロールプレイで、手足をまとめて縛る。",
  },
  h6s_bound_wrists: {
    label: "手首を縛られる（同意の上）",
    group: "シチュエーション（雰囲気）",
    tags: "wrists tied together with rope above head, lying on bed, consensual play, blushing",
    motion: "she tugs gently at the rope around her wrists, blushing",
    note: "同意の上で、手首を頭の上で縛る。",
  },
  h6s_bound_chair: {
    label: "椅子に縛られる（同意の上）",
    group: "シチュエーション（雰囲気）",
    tags: "tied to a chair with rope, ankles and wrists bound, consensual play, bedroom",
    motion: "she shifts on the chair, testing the ropes with a flushed smile",
    note: "同意の上で、椅子に座ったまま縛られる。",
  },
  h6s_bound_silk: {
    label: "シルクのスカーフで縛る（同意の上）",
    group: "シチュエーション（雰囲気）",
    tags: "wrists tied with a silk scarf, soft bondage, lying on bed, consensual, romantic lighting",
    motion: "she smiles as her wrists are loosely tied with a silk scarf",
    note: "やわらかいスカーフで、やさしく縛る。",
  },
  h6s_mlegs_bed: {
    label: "M字に脚を開いて見せる",
    group: "シチュエーション（雰囲気）",
    tags: "lying on bed with m legs, knees bent and spread, embarrassed, blushing, looking away",
    motion: "she opens her knees slowly, blushing and looking away",
    note: "恥ずかしがりながら、脚をM字に開く。",
  },
  h6s_mlegs_inspect: {
    label: "ベッドで脚を開いて待つ",
    group: "シチュエーション（雰囲気）",
    tags: "waiting on bed with legs spread in m shape, inviting, flushed face, soft lighting",
    motion: "she waits on the bed with her knees apart, her eyes inviting",
    note: "ベッドの上で、脚を開いて待っている。",
  },
} satisfies Record<string, MotionOption>;

export type MotionOptionId = keyof typeof MOTION_OPTIONS;
export const MOTION_OPTION_IDS = Object.keys(MOTION_OPTIONS) as MotionOptionId[];

function optionsOf(ids?: readonly string[]): MotionOption[] {
  return (ids ?? [])
    .filter((id): id is MotionOptionId => id in MOTION_OPTIONS)
    .map((id) => MOTION_OPTIONS[id]);
}

/** Notes for the script writer from the chosen options. */
export function motionOptionNotes(ids?: readonly string[]): string {
  return optionsOf(ids)
    .map((o) => o.note)
    .filter(Boolean)
    .join("\n");
}

/** The key images for a script; seeds come from one base so a retry draws the same pictures. */
export function motionKeyJobs(
  script: MotionScript,
  seed: number,
  templateId?: string,
  optionIds?: readonly string[],
): MotionKeyJob[] {
  const t = templateOf(templateId);
  const chosen = optionsOf(optionIds);
  const always = chosen.filter((o) => !o.climaxOnly).map((o) => o.tags);
  const climax = chosen.map((o) => o.tags);
  const who = `1girl, 1boy, hetero, mature female, adult, ${script.age} years old`;
  const outside = script.finish === "outside";
  const keys: MotionKeyJob[] = [
    {
      id: "k1",
      mode: "t2i",
      seed,
      prompt: `${QUALITY}, ${who}, ${script.look}, half-closed eyes, blush, sweat, open mouth, navel, breasts out, ${t.position}, ${t.act ?? "sex, vaginal, penis"}, ${t.camera}, ${script.place}`,
    },
    {
      id: "k2",
      mode: "i2i",
      src: "k1",
      strength: 0.6,
      seed: seed + 1,
      prompt: `${QUALITY}, ${who}, ${script.look}, orgasm, eyes rolled back, open mouth, tongue out, drooling, heavy blush, sweat, ${t.climax}, ${t.position}, ${t.act ?? "sex, vaginal, deep penetration, penis"}, ${t.camera}, ${script.place}`,
    },
    {
      id: "k3",
      mode: "inpaint",
      src: "k1",
      strength: 0.7,
      seed: seed + 2,
      mask: outside ? t.cumMask.outside : t.cumMask.creampie,
      prompt: outside
        ? `${QUALITY}, ${who}, after sex, cum, ${t.cumOutsideTags}, excessive cum, thick white liquid, sticky, sweat, penis`
        : `${QUALITY}, ${who}, after sex, cum, ${t.cumInsideTags ?? "cum in pussy, creampie, penis inside, cum leaking a little, white cum at the base"}, thick white liquid, sticky, sweat, penis`,
    },
  ];
  // A vaginal creampie gets a fourth picture: the penis pulled out and the cum dripping out.
  if (pullsOut(t, script)) {
    keys.push({
      id: "k4",
      mode: "inpaint",
      src: "k3",
      strength: 0.65,
      seed: seed + 5,
      mask: t.cumMask.creampie,
      prompt: `${QUALITY}, ${who}, after sex, penis pulled out, cum dripping from pussy, cum leaking, cum string, cum pool on sheets, thick white liquid, sticky, sweat, pussy`,
    });
  }
  if (script.fellatio) {
    keys.push(
      {
        id: "f1",
        mode: "t2i",
        seed: seed + 3,
        prompt: `${QUALITY}, ${who}, ${script.look}, half-closed eyes, blush, sweat, fellatio, penis in mouth, licking penis, hand on penis, looking at viewer, pov, from above, kneeling between legs, breasts out, cum on body, after sex, ${script.place}`,
      },
      {
        id: "f2",
        mode: "inpaint",
        src: "f1",
        strength: 0.7,
        seed: seed + 4,
        mask: "mouth",
        prompt: `${QUALITY}, ${who}, cum in mouth, cum overflow from mouth, cum on face, cum drip, open mouth, tongue out, thick white liquid, penis, pov`,
      },
    );
  }
  // Options: the climax-only ones (fainting, foam) go on the climax and after pictures.
  for (const key of keys) {
    const extra = key.id === "k2" || key.id === "k4" ? climax : always;
    const pullout = key.id === "k4" ? chosen.map((o) => o.pulloutTags).filter(Boolean) : [];
    if (extra.length + pullout.length > 0)
      key.prompt = `${key.prompt}, ${[...extra, ...pullout].join(", ")}`;
  }
  return keys;
}

/** A vaginal creampie ends with a slow pull-out and the cum dripping out (k4). */
function pullsOut(t: MotionTemplate, script: MotionScript): boolean {
  return script.finish !== "outside" && !t.act && !t.cumInsideTags;
}

/** Seconds of each loop cut for the chosen length (change cuts play once, ~3 s). */
function loopSeconds(minutes: MotionMinutes, fellatio: boolean): Record<string, number> {
  const scale = minutes === 1 ? 1 : minutes === 3 ? 3 : 5;
  const base = { slow: 12, fast: 14, climax: 8, after: 8, fellatio: 10 };
  const total = base.slow + base.fast + base.climax + base.after + (fellatio ? base.fellatio : 0);
  // Scale the loop parts so the whole film (with the ~3 s change cuts) is about the target.
  const changeSec = 6 + (fellatio ? 3 : 0);
  const factor = Math.max(1, (scale * 60 - changeSec) / total);
  return Object.fromEntries(Object.entries(base).map(([k, v]) => [k, Math.round(v * factor)]));
}

export function motionCuts(
  script: MotionScript,
  minutes: MotionMinutes,
  templateId?: string,
  optionIds?: readonly string[],
  talk?: MotionTalk,
): MotionCut[] {
  const t = templateOf(templateId);
  const sec = loopSeconds(minutes, script.fellatio);
  const chosen = optionsOf(optionIds);
  const talkInfo = talk ? MOTION_TALK[talk] : undefined;
  const moves = [
    ...chosen.filter((o) => o.motion && !o.climaxOnly).map((o) => o.motion as string),
    ...(talkInfo?.motion ? [talkInfo.motion] : []),
  ];
  const climaxMoves = [
    ...chosen.filter((o) => o.motion).map((o) => o.motion as string),
    ...(talkInfo?.climaxMotion ? [talkInfo.climaxMotion] : []),
  ];
  const loop = (text: string, climax = false) =>
    `anime, ${[text, ...(climax ? climaxMoves : moves)].join(", ")}, fixed camera, seamless loop`;
  const once = (text: string) => `anime, ${text}, fixed camera`;
  // Not one burst: the cum comes in several pulses, then (inside) a slow pull-out and dripping.
  const cum =
    script.finish === "outside"
      ? once(
          `${t.cumOutsideMotion}, the cum comes in four separate spurts, each smaller than the last, her body jerks with each spurt, then the cum slowly slides down her skin, she trembles and pants`,
        )
      : once(
          t.cumInsideMotion ??
            "the man pushes deep and stops, his penis throbs slowly four times, each pulse pumps cum deep inside her, her lower belly twitches with each pulse, only a little white cum seeps out around the base, she trembles and holds her breath",
        );
  const out = pullsOut(t, script);
  const cuts: MotionCut[] = [
    {
      name: "c1-slow",
      start: "k1",
      end: "k1",
      loop: true,
      seconds: sec.slow ?? 12,
      phase: "slow",
      breath: "slow",
      fade: "in",
      prompt: loop(t.slow),
    },
    {
      name: "c2-fast",
      start: "k1",
      end: "k1",
      loop: true,
      seconds: sec.fast ?? 14,
      phase: "fast",
      breath: "fast",
      insert: t.insertFast,
      prompt: loop(t.fast),
    },
    { name: "c3-build", start: "k1", end: "k2", loop: false, seconds: 3, prompt: once(t.build) },
    {
      name: "c4-climax",
      start: "k2",
      end: "k2",
      loop: true,
      seconds: sec.climax ?? 8,
      phase: "climax",
      breath: "climax",
      insert: t.insertClimax,
      prompt: loop(t.climaxMotion, true),
    },
    { name: "c5-cum", start: "k2", end: "k3", loop: false, seconds: 3, flash: true, prompt: cum },
    ...(out
      ? [
          {
            name: "c5b-pullout",
            start: "k3",
            end: "k4",
            loop: false,
            seconds: 3,
            prompt: once(
              "the man slowly pulls his penis out, a string of white cum stretches between them and breaks, then thick white cum slowly wells up and oozes out of her pussy and runs down onto the sheets, her hips twitch",
            ),
          } satisfies MotionCut,
        ]
      : []),
    {
      name: "c6-after",
      start: out ? "k4" : "k3",
      end: out ? "k4" : "k3",
      loop: true,
      seconds: sec.after ?? 8,
      phase: "after",
      fade: "out",
      prompt: loop(
        out
          ? `afterglow, ${t.after}, thick drops of white cum keep slowly dripping out of her pussy, her pussy twitches and pushes out more cum, a small pool of cum forms on the sheet`
          : `afterglow, ${t.after}`,
      ),
    },
  ];
  if (script.fellatio) {
    cuts.push(
      {
        name: "c7-fella",
        start: "f1",
        end: "f1",
        loop: true,
        seconds: sec.fellatio ?? 10,
        phase: "fellatio",
        fade: "in",
        sfx: "fellatio",
        prompt: loop(
          "the woman sucks the penis, her head bobs up and down rhythmically, her lips slide along the shaft, her cheeks hollow, her tongue licks, her hand strokes the base, saliva drips, her breasts sway with the motion, she looks up at the viewer",
        ),
      },
      {
        name: "c8-mouth",
        start: "f1",
        end: "f2",
        loop: false,
        seconds: 4,
        phase: "end",
        fade: "out",
        sfx: "fellatio",
        prompt: once(
          "the man ejaculates into her mouth, the penis pulses, thick white cum fills her mouth and overflows from her lips and drips down her chin, she keeps sucking and swallows, her eyes half closed",
        ),
      },
    );
  }
  return cuts;
}

/** One-line summary of the film for the job list. */
export function motionSummary(script: MotionScript, minutes: MotionMinutes): string {
  const finish = script.finish === "outside" ? "外出し" : "中出し";
  return `${script.title}（${script.name}・${script.age}歳、約${minutes}分、${finish}${script.fellatio ? "＋フェラ" : ""}）`;
}
