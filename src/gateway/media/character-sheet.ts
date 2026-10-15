import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../../infra/openclaw-root.js";
import { ANATOMY_CHEST, ANATOMY_GROIN } from "../avatar-frames.js";
import {
  characterAssetDir,
  characterLook,
  getCharacter,
  type LibraryCharacter,
  lookWithOutfit,
  resolveCast,
  saveCharacter,
  updateCharacter,
} from "../comic-characters.js";
import { type ComicPanel, renderPanel } from "../comic-series.js";
import { MANGA_EXTRA_NEGATIVE } from "../manga-script.js";
import { analyzePhoto, createFaceReference } from "../photo-analysis.js";
import {
  OUTPAINT_LORA,
  QWEN_EDIT_MODEL,
  QWEN_LORA_DIR,
  qwenTurboSettings,
  VIGGLE_TURBO_LORA,
} from "./character-lora.js";
import { MEDIA_MODEL_DIR, SDXL_MODELS } from "./config.js";
import { REALESRGAN_EXE } from "./estimates.js";
import { acquireGpu } from "./gpu-lease.js";
import { renderImage, stopImageWorker } from "./image.js";
import { askScriptWriter } from "./manga.js";
import { exists } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import { assertAllowedRequest } from "./text.js";
import type { MediaJob, MediaOptions } from "./types.js";
import { WAN2GP_DIR, WAN2GP_PYTHON, WAN2GP_RUNNER } from "./video.js";
import { VOICE_FFMPEG } from "./voice-drama.js";

// ─── キャラクターシート生成：正確なプロンプト定義 ───────────────────────────

/**
 * 三面図（三面） ― 正面・側面・背面を横1列で固定レイアウト
 * 絶対に向きが混在しないよう ControlNet / img2img ではなくプロンプトで完全指定。
 * SD系モデルは "character sheet" + 各ビューを明示すると横並びで出力する。
 */
const THREE_VIEW_SCENE =
  "character reference sheet, orthographic views, three views, " +
  "left column: front view facing viewer straight on, " +
  "center column: side view facing right, profile view, exact 90 degrees, " +
  "right column: back view facing away from viewer, " +
  "full body, standing upright, arms at sides, neutral pose, " +
  "plain white background, no background, studio lighting, flat lighting, " +
  "same character consistent design across all three views";

const THREE_VIEW_NEG =
  "text, watermark, signature, speech bubble, tilted, angled incorrectly, " +
  "diagonal view, 3/4 view, multiple characters, duplicates, " +
  "bad anatomy, extra limbs, deformed, lowres, blurry";

/**
 * 360度ターンアラウンド（8方向, 2×4グリッド）
 * 0° / 45° / 90° / 135° / 180° / 225° / 270° / 315° を並べる。
 */
const TURNAROUND_360_SCENE =
  "character turnaround sheet, 360 degree rotation reference, 8 views, " +
  "2x4 grid layout, " +
  "top row left to right: front view 0deg, 45deg front-right diagonal, right side view 90deg, 135deg back-right diagonal, " +
  "bottom row left to right: back view 180deg, 225deg back-left diagonal, left side view 270deg, 315deg front-left diagonal, " +
  "consistent character design all views, full body standing, arms at sides, " +
  "plain white background, studio lighting";

const TURNAROUND_360_NEG =
  "text, watermark, multiple characters, inconsistent design, " +
  "wrong angle, bad anatomy, deformed, lowres, blurry";

/** The colour word of her hair in a look ("blonde hair" → "blonde"), for pubic hair of the same colour. */
export function hairColorOf(look: string): string | undefined {
  const match =
    /\b(black|brown|dark brown|light brown|blonde|golden|white|silver|grey|gray|red|pink|blue|light blue|dark blue|navy|green|purple|violet|lavender|orange|aqua|teal|cyan)\s+(?:long |short |medium )?hair/i.exec(
      look,
    );
  return match?.[1]?.toLowerCase();
}

/** 成人向け追加プロンプト（look の服装を上書き）。乳首・乳輪・陰毛（髪と同じ色）・局部も描くように書く。 */
export function nsfwLook(baseLook: string): string {
  const color = hairColorOf(baseLook);
  return (
    baseLook.replace(
      /\b(wearing|dressed in|clothes|outfit|uniform|shirt|dress|skirt|pants|jacket|coat|blouse)[^,]*/gi,
      "",
    ) +
    ", nude, completely nude, naked, bare body, no clothes, nipples, areolae, navel, pussy, " +
    (color ? `${color} pubic hair, ` : "pubic hair, ") +
    "uncensored"
  );
}

/**
 * The sheet view the app asked for. The app sends `view` ("front" / "three_view" / "360") and the
 * old `turnaround` flag; anything else means the front view.
 */
export function comicSheetViewOf(
  view: unknown,
  turnaround?: unknown,
): NonNullable<MediaOptions["comicSheetView"]> {
  if (view === "three_view" || view === "360" || view === "turnaround" || view === "front")
    return view;
  return turnaround === true ? "three_view" : "front";
}

/** The pose as a short English phrase: Japanese is translated by the local model; English is used as written. */
async function poseTags(raw: string | undefined): Promise<string> {
  const text = (raw ?? "")
    .replace(/[\r\n]+/g, " ")
    .trim()
    .slice(0, 200);
  if (!text) return "";
  if (/^[\x20-\x7e]+$/.test(text)) return text;
  try {
    const reply = await askScriptWriter(
      "次の日本語のポーズ指定を、画像生成用の短い英語（danbooru風のタグか短い句、カンマ区切り、20語以内）に直してください。出力は英語のみ。",
      text,
      200,
    );
    const english = reply
      .replace(/[^\x20-\x7e]/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 200);
    return english || text;
  } catch {
    return text;
  }
}

/** A character sheet: front view / three-view / 360-degree turnaround. */
export async function runCharacterSheet(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");

  const rawView = job.options.comicSheetView ?? "front";
  // 旧 "turnaround" は "three_view" として互換維持
  const view = rawView === "turnaround" ? "three_view" : rawView;
  const isAdult = job.options.comicSheetAdult === true;
  const pose = view === "front" ? await poseTags(job.options.comicSheetPose) : "";

  // Made from a photo: the front sheet keeps the person of the photo.
  if (character.photoRef && view === "front" && !isAdult && (await exists(character.photoRef))) {
    await runSheetFromPhoto(job, character);
    return;
  }
  // The adult standing picture of a character that already has a standing picture: the same
  // picture is edited (Qwen Image 2.1), so it is the same person whatever the SDXL model can draw.
  if (isAdult && view === "front" && character.refImage && (await exists(character.refImage))) {
    await runSheetByEdit(job, character, {
      source: character.refImage,
      adult: true,
      stage: "立ち絵から、成人向けの立ち絵を作っています",
      prompt:
        "Keep the same adult woman exactly as in the picture: face, hair, eye color, body shape and the same plain background. " +
        `Change only this: she is completely nude, ${pose ? `in this pose: ${pose}` : "standing, arms relaxed at her sides"}, front view, full body. ` +
        `${ANATOMY_CHEST}. ${ANATOMY_GROIN}.`,
    });
    return;
  }

  let scene: string;
  let aspect: "portrait" | "landscape" | "square";
  let negative: string;

  switch (view) {
    case "three_view":
      scene = THREE_VIEW_SCENE;
      aspect = "landscape";
      negative = THREE_VIEW_NEG;
      break;
    case "360":
      scene = TURNAROUND_360_SCENE;
      aspect = "landscape"; // 2×4グリッドは横長
      negative = TURNAROUND_360_NEG;
      break;
    default: // "front"
      scene = `full body, ${pose || "standing"}, front view, looking at viewer, simple background, white background`;
      aspect = "portrait";
      negative = MANGA_EXTRA_NEGATIVE;
  }

  const panel: ComicPanel = {
    frame: view === "front" ? "tall" : "wide",
    scene,
    characters: [
      {
        name: character.name,
        pos: "center",
        action: pose ? `${pose}, light smile` : "neutral pose, arms at sides, light smile",
      },
    ],
    lines: [],
  };

  assertAllowedRequest(characterLook(character));
  markStage(job, "GPU を空けています");
  await acquireGpu(job);

  const dir = characterAssetDir(character.id);
  await fs.mkdir(dir, { recursive: true });
  const suffix = `${view}${isAdult ? "-adult" : ""}`;
  const file = path.join(dir, `sheet-${suffix}-${randomBytes(3).toString("hex")}.png`);

  const cast = await resolveCast([{ ...character, characterId: character.id }]);
  // アウトフィット差し替え（成人向けは裸体ルックに上書き）
  const outfit = character.outfits?.find((o) => o.name === job.options.comicOutfit);
  if (outfit && cast[0]) cast[0] = { ...cast[0], look: lookWithOutfit(cast[0].look, outfit.tags) };
  if (isAdult && cast[0]) cast[0] = { ...cast[0], look: nsfwLook(cast[0].look) };

  const render = renderPanel(cast, panel);
  await renderImage(job, render.prompt, false, file, {
    aspect,
    negative: `${negative}, ${render.negative}`,
    loras: render.loras,
  });

  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: job.options.comicSeriesId ?? "",
    character: character.name,
    characterId: character.id,
    view,
    ...(isAdult ? { adult: true } : {}),
  };

  // turnaroundJobId を常に更新（front 以外は turnaroundJobId に格納）
  if (view !== "front") {
    await updateCharacter(character.id, { turnaroundJobId: job.id });
  }
}

/** Width and height of a PNG from its header. */
export async function pngSize(file: string): Promise<{ width: number; height: number }> {
  const handle = await fs.open(file, "r");
  try {
    const head = Buffer.alloc(24);
    await handle.read(head, 0, 24, 0);
    return { width: head.readUInt32BE(16), height: head.readUInt32BE(20) };
  } finally {
    await handle.close();
  }
}

/**
 * Widens the character's sheet: gray is added on the chosen sides and Qwen Image 2.1 (with the
 * outpaint LoRA) draws the continuation, keeping the original where it is. The result is a new
 * sheet candidate; "この設定画にする" in the app keeps it.
 */
export async function runCharacterOutpaint(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（広げる元になります）");
  const sides = job.options.comicOutpaint;
  if (!sides || sides.top + sides.bottom + sides.left + sides.right <= 0)
    throw new Error("広げる方向を選んでください");
  for (const file of [OUTPAINT_LORA, VIGGLE_TURBO_LORA]) {
    if (!(await exists(path.join(QWEN_LORA_DIR, file))))
      throw new Error(`広げるための部品が見つかりません（${file}）`);
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "outpaint-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });

  // Gray on the chosen sides, then about 1.2 megapixels in multiples of 16.
  const padded = path.join(work, "padded.png");
  const { top, bottom, left, right } = sides;
  const budget = 1_200_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `pad=w='trunc(iw*(1+${left}+${right}))':h='trunc(ih*(1+${top}+${bottom}))':x='trunc(iw*${left})':y='trunc(ih*${top})':color=0x808080,` +
        `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      padded,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(padded);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, "設定画を広げています");
  const request = path.join(work, "request.json");
  const note = job.request ? ` ${job.request}` : "";
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...qwenTurboSettings([OUTPAINT_LORA]),
        prompt:
          "Outpaint the image: replace the solid gray areas with a seamless continuation of the scene, " +
          "keeping the existing picture exactly as it is, the same character, face, hair and art style." +
          note,
        image_refs: [padded],
        video_prompt_type: "KI",
        resolution: `${size.width}x${size.height}`,
        seed: -1,
      },
    }),
  );
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 30 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error("広げた絵ができませんでした");
  const file = path.join(dir, `sheet-outpaint-${randomBytes(3).toString("hex")}.png`);
  await runTool(VOICE_FFMPEG, ["-y", "-loglevel", "error", "-i", image, file], {
    timeoutMs: 60_000,
  });
  await fs.rm(work, { recursive: true, force: true });

  job.file = file;
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/**
 * A full-body front sheet drawn from the photo the character was made from: Qwen Image 2.1 with
 * the Viggle Turbo LoRA keeps the face, hair, body and clothes of the photo (about 2-3 minutes).
 */
const PHOTO_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Full-body standing photograph of the same person as in the reference photo, front view, looking at the viewer, plain white background, " +
    "the whole body from head to feet, photorealistic. Keep the face, hairstyle, hair color, skin tone, body shape and clothes exactly the same as the photo.",
  anime:
    "Redraw the person in the reference photo as an anime character: full body, front view, looking at the viewer, plain white background, " +
    "from head to feet, clean line art and cel shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo, so the person is clearly recognizable.",
  illustration:
    "Redraw the person in the reference photo as a soft colored illustration: full body, front view, plain white background, from head to feet, " +
    "gentle shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo.",
};

/** The same character sheet, when the reference is an anime picture (the character's design is kept as it is). */
const ANIME_SOURCE_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Re-imagine the anime character in the reference picture as a real person in a live-action photograph: full-body standing, front view, looking at the viewer, plain white background, " +
    "from head to feet, photorealistic, real human skin and natural body proportions. Keep the character's hair color and hairstyle, eye color, outfit design and colors, accessories and markings exactly as in the reference, like a faithful live-action adaptation.",
  anime:
    "Redraw the anime character in the reference picture as a clean character sheet: full body, front view, looking at the viewer, plain white background, from head to feet, " +
    "clean line art and cel shading. Keep the exact character design of the reference: face, eye color and shape, hair color and hairstyle, outfit, accessories, colors and proportions.",
  illustration:
    "Redraw the character in the reference picture as a soft colored illustration: full body, front view, plain white background, from head to feet, gentle shading. " +
    "Keep the exact character design of the reference: face, eyes, hair, outfit, accessories and colors.",
};

const ANIME_SOURCE_IDENTITY_PROMPT =
  " Change the rendering only; keep the individual design of reference 1: hair color and hairstyle silhouette, eye color, outfit, accessories, visible marks. Do not invent a different character. When the result must be a photograph, give the face natural human proportions while keeping the character recognizable.";

const RANDOM_BODIES = ["slender", "average", "athletic", "soft and curvy", "petite"];

const RANDOM_OUTFITS = [
  "a white blouse and a navy pleated skirt",
  "a casual hoodie and jeans",
  "a simple summer dress",
  "a knit sweater and a long skirt",
  "a denim jacket over a T-shirt with slim pants",
  "a plain blazer, a shirt and tailored trousers",
  "a trench coat over a dress",
  "a cardigan, a camisole and a mini skirt",
  "a sporty tracksuit",
  "a floral one-piece dress",
];

const RANDOM_SHOES = [
  "white sneakers",
  "loafers",
  "ankle boots",
  "flat sandals",
  "low-heeled pumps",
];

/**
 * When the reference shows only a face, there is nothing to copy for the body and clothes: they are chosen at random
 * (a different pick each time), an ordinary everyday outfit; the face, hair and skin stay exactly as in the reference.
 */
export function randomBodyPrompt(random: () => number = Math.random, outfit?: string): string {
  const pick = <T>(list: readonly T[]): T =>
    list[Math.min(list.length - 1, Math.floor(random() * list.length))]!;
  // An outfit that is already set (her group's or category's costume) wins over a random one.
  const wearing = outfit?.trim()
    ? outfit.trim()
    : `${pick(RANDOM_OUTFITS)} and ${pick(RANDOM_SHOES)}`;
  return (
    ` The reference shows only the face, with no body or clothes. Draw a natural adult full body for her: ${pick(RANDOM_BODIES)} build, wearing ${wearing}. ` +
    (outfit?.trim()
      ? "The outfit is her group's costume; the build was chosen at random because the reference has no body. "
      : "These were chosen at random because the reference has no body. ") +
    FACE_ONLY_PROPORTIONS +
    "Take only the face, hair and skin tone from the reference and keep them exactly."
  );
}

/**
 * A face close-up fills its whole frame, so the editor tends to draw the head as big as in the close-up, on a small
 * body. The sentence asks for the real proportions of a full body: the head is a small part of the height.
 */
export const FACE_ONLY_PROPORTIONS =
  "The reference is a close-up, so the face looks large in it: do NOT keep that scale. Draw the whole body in natural adult proportions, about 7 to 8 heads tall, with the head small compared to the body, narrow shoulders, long neck and long legs, and show the full body from head to feet. ";

/** Whether none of a character's reference pictures shows the body. Read from the pictures once, then remembered. */
async function referenceIsFaceOnly(character: LibraryCharacter): Promise<boolean> {
  if (character.photoFaceOnly !== undefined) return character.photoFaceOnly;
  const files = (character.photoRefs ?? (character.photoRef ? [character.photoRef] : [])).slice(
    0,
    4,
  );
  const found: Array<Awaited<ReturnType<typeof analyzePhoto>>> = [];
  for (const file of files) {
    if (await exists(file)) found.push(await analyzePhoto(file));
  }
  const read = found.filter((one): one is NonNullable<typeof one> => Boolean(one));
  // Pictures that could not be read are not guessed about.
  const faceOnly = read.length > 0 && !read.some((one) => (one.kind ?? "").includes("全身"));
  await saveCharacter({ ...character, photoFaceOnly: faceOnly, updatedAt: Date.now() });
  return faceOnly;
}

/** The style sentence for a character's sheet: depends on what the reference is (photo / anime) and what is wanted. */
export function photoStylePrompt(
  source: "photo" | "anime" | undefined,
  style: "photo" | "anime" | "illustration",
): string {
  return source === "anime" ? ANIME_SOURCE_STYLE_PROMPT[style] : PHOTO_STYLE_PROMPT[style];
}

export function photoIdentityPrompt(source: "photo" | "anime" | undefined): string {
  return source === "anime" ? ANIME_SOURCE_IDENTITY_PROMPT : PHOTO_IDENTITY_PROMPT;
}

const PHOTO_IDENTITY_PROMPT =
  " Change the rendering style only; preserve the individual design of reference 1. Keep the distinctive face outline, brow shape, eye spacing and tilt, nose and mouth shape, hairline, hairstyle silhouette, eye and hair colors, visible marks, glasses and accessories. Do not replace the face with a generic attractive model or generic anime face. Avoid enlarging the eyes or narrowing the jaw beyond what the requested style requires. Reference 1 is authoritative when other references disagree.";

/** The Wan2GP settings of the three editors for a photo (the stronger ones are slower). */
export function photoEngineSettings(engine: "fast" | "precise" | "edit2509"): {
  settings: Record<string, unknown>;
  needsLora: boolean;
} {
  if (engine === "fast") return { settings: qwenTurboSettings(), needsLora: true };
  if (engine === "edit2509") {
    return {
      settings: {
        model_type: "qwen_image_edit_plus_20B_nunchaku_r128_int4",
        num_inference_steps: 4,
        guidance_scale: 1,
      },
      needsLora: false,
    };
  }
  // Qwen Image 2.1 at its own step count (about 5 minutes), without the speed LoRA: closer to the photo.
  return {
    settings: {
      model_type: QWEN_EDIT_MODEL,
      num_inference_steps: 30,
      guidance_scale: 4,
      negative_prompt: " ",
    },
    needsLora: false,
  };
}

/** What each kind of photo is used for, in English (for the editor). */
const PHOTO_ROLE_EN: Record<string, string> = {
  顔: "the face and skin",
  髪: "the hair (color and hairstyle)",
  服: "the clothes and outfit",
  体型: "the body shape and proportions",
  雰囲気: "the overall mood and color palette",
  小物: "the accessories",
};

const STYLE_TAIL: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Draw the same person as a realistic photograph: full body, standing, front view, looking at the viewer, plain white background, from head to feet.",
  anime:
    "Draw the same person as an anime character: full body, standing, front view, looking at the viewer, plain white background, from head to feet, clean line art and cel shading.",
  illustration:
    "Draw the same person as a soft colored illustration: full body, standing, front view, plain white background, from head to feet, gentle shading.",
};

/** The editor's instruction when several photos are given: blend them, or take one part from each. */
export function multiPhotoPrompt(
  style: "photo" | "anime" | "illustration",
  mode: "subject" | "blend" | "roles",
  roles: string[],
  count: number,
): string {
  const intro =
    mode === "subject"
      ? `Create ONE adult character. All ${count} reference pictures show the SAME target person. Use reference picture 1 as the identity anchor for face, hair and skin. Other pictures provide only visible details of this same person, such as body shape, clothing and accessories. Never combine features from bystanders or background objects.`
      : mode === "roles"
        ? `Create ONE adult person from ${count} reference pictures. ` +
          Array.from(
            { length: count },
            (_, i) =>
              `Use reference picture ${i + 1} only for ${PHOTO_ROLE_EN[roles[i] ?? ""] ?? PHOTO_ROLE_EN["顔"]}.`,
          ).join(" ")
        : `Create ONE new adult person by blending the people in the ${count} reference pictures: mix their faces, eyes, hair and style evenly, so that this is a new person who resembles all of them a little.`;
  return `${intro} ${STYLE_TAIL[style]}`;
}

async function runSheetFromPhoto(job: MediaJob, character: LibraryCharacter): Promise<void> {
  const style = character.photoStyle ?? "photo";
  const sources =
    (character.photoRefs ?? []).length > 1
      ? (
          await Promise.all(
            (character.photoRefs ?? []).map(async (file) => ((await exists(file)) ? file : "")),
          )
        ).filter(Boolean)
      : [];
  const several = sources.length > 1;
  // Creation places the identity anchor first and the most complementary analyzed photos next.
  const engine = character.photoEngine ?? "precise";
  // 2509 is documented for 1–3 inputs; the installed 2.1 backend supports up to 10.
  const editorSources = sources.slice(0, engine === "edit2509" ? 3 : 10);
  const traits = character.photoTraits ? ` Details to keep exactly: ${character.photoTraits}.` : "";
  // A face-only reference has no body to copy: another picture with a body is used when there is one (above),
  // otherwise the body and clothes are chosen at random.
  const randomBody = (await referenceIsFaceOnly(character))
    ? randomBodyPrompt(Math.random, character.spec?.outfit)
    : "";
  // A pose the user asked for replaces the plain standing pose of the sheet.
  const pose = await poseTags(job.options.comicSheetPose);
  const poseLine = pose
    ? ` Draw the character in this pose instead of a plain standing pose: ${pose}. Keep the face, hair and clothes of the source exactly.`
    : "";
  if (engine === "ipadapter") {
    await runSheetByIpAdapter(
      job,
      character,
      sources.length ? sources : [character.photoRef as string],
      photoStylePrompt(character.photoSource, style) +
        photoIdentityPrompt(character.photoSource) +
        randomBody +
        traits +
        poseLine,
    );
    return;
  }
  const picked = photoEngineSettings(engine);
  const high = character.photoQuality === "high";
  await runSheetByEdit(job, character, {
    source: editorSources[0] ?? (character.photoRef as string),
    extraSources: several ? editorSources.slice(1) : [],
    identityReferenceLimit:
      !several || (character.photoMode ?? "subject") === "subject"
        ? engine === "edit2509"
          ? 3
          : 10
        : undefined,
    adult: false,
    stage: several
      ? `${sources.length}枚を解析し、選んだ${editorSources.length}枚から設定画を作っています`
      : engine === "precise"
        ? "写真から設定画を作っています（高精度・約5分）"
        : "写真から設定画を作っています",
    prompt:
      (several
        ? multiPhotoPrompt(
            style,
            character.photoMode ?? "subject",
            character.photoRoles ?? [],
            editorSources.length,
          )
        : photoStylePrompt(character.photoSource, style)) +
      (character.photoSource === "anime" && several
        ? ` The reference pictures are anime pictures. ${ANIME_SOURCE_STYLE_PROMPT[style]}`
        : "") +
      (!several || (character.photoMode ?? "subject") === "subject"
        ? photoIdentityPrompt(character.photoSource)
        : "") +
      randomBody +
      traits +
      poseLine,
    settings: picked.settings,
    needsLora: picked.needsLora,
    upscale: high ? (style === "photo" ? "photo" : "anime") : undefined,
  });
}

/** Ordinary Plus uses visual embeddings; it does not use FaceID or a voice/personality model. */
async function runSheetByIpAdapter(
  job: MediaJob,
  character: LibraryCharacter,
  sources: string[],
  prompt: string,
): Promise<void> {
  if (character.photoMode === "roles") {
    throw new Error(
      "IP-Adapter Plus は部位ごとの指定に対応していません。Qwen を選ぶか、同じ人物の参照に変更してください。",
    );
  }
  const primary = sources[0];
  if (!primary) {
    throw new Error("参照画像が見つかりません。");
  }
  const assets = path.join(MEDIA_MODEL_DIR, "image", "ip-adapter-plus");
  for (const filename of [
    "models/image_encoder/config.json",
    "models/image_encoder/model.safetensors",
    "sdxl_models/ip-adapter-plus_sdxl_vit-h.safetensors",
  ]) {
    if (!(await exists(path.join(assets, filename)))) {
      throw new Error(
        "IP-Adapter Plus のモデルがありません。PCで scripts/media/setup_ip_adapter.py を実行して導入してください。",
      );
    }
  }
  const style = character.photoStyle ?? "photo";
  const model = SDXL_MODELS[style === "photo" ? "realvis" : "animagine"];
  if (!(await exists(model.file))) {
    throw new Error("IP-Adapter Plus に使う画像モデルが見つかりません。");
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, `ipadapter-work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  const file = path.join(dir, `sheet-front-${randomBytes(3).toString("hex")}.png`);
  try {
    let references = sources.slice(0, character.photoMode === "blend" ? 4 : 1);
    if (character.photoMode !== "blend") {
      const face = path.join(work, "identity-face.png");
      if (await createFaceReference(primary, face)) references = [...references, face];
    } else {
      prompt = multiPhotoPrompt(style, "blend", [], references.length);
    }
    const request = path.join(work, "request.json");
    await fs.writeFile(
      request,
      JSON.stringify({
        assets,
        model: model.file,
        references,
        prompt: `${model.quality}, ${prompt}`,
        negative: model.negative,
        steps: model.steps,
        cfg: model.cfg,
        scale: 0.65,
        output: file,
      }),
    );
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    markStage(job, "参照画像から設定画を作っています（IP-Adapter Plus）");
    const packageRoot = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
    if (!packageRoot) {
      throw new Error("OpenClaw の実行場所を確認できません。");
    }
    await runTool(
      WAN2GP_PYTHON,
      [path.join(packageRoot, "scripts", "media", "ip_adapter_render.py"), request],
      {
        timeoutMs: 60 * 60_000,
        onLine: (line) => {
          const step = /^STEP (\d+\/\d+)$/.exec(line);
          if (step) {
            markStage(job, `設定画を作っています（IP-Adapter Plus ${step[1]}）`);
          }
        },
      },
    );
    if (!(await exists(file))) {
      throw new Error("IP-Adapter Plus の設定画ができませんでした。");
    }
    if (character.photoQuality === "high") {
      markStage(job, "設定画を高画質にしています");
      const big = path.join(work, "upscaled.png");
      await runTool(
        REALESRGAN_EXE,
        [
          "-i",
          file,
          "-o",
          big,
          "-n",
          style === "photo" ? "realesrgan-x4plus" : "realesrgan-x4plus-anime",
          "-s",
          "4",
          "-t",
          "256",
          "-m",
          path.join(path.dirname(REALESRGAN_EXE), "models"),
        ],
        { timeoutMs: 20 * 60_000 },
      );
      await runTool(
        VOICE_FFMPEG,
        ["-y", "-loglevel", "error", "-i", big, "-vf", "scale=iw/2:-2:flags=lanczos", file],
        { timeoutMs: 120_000 },
      );
    }
    job.file = file;
    job.mime = "image/png";
    job.comic = {
      seriesId: job.options.comicSeriesId ?? "",
      character: character.name,
      characterId: character.id,
      view: "front",
    };
  } finally {
    await fs.rm(work, { recursive: true, force: true });
  }
}

/** A front sheet made by editing a picture with Qwen Image 2.1 (Viggle Turbo): a photo, or the standing picture itself. */
async function runSheetByEdit(
  job: MediaJob,
  character: LibraryCharacter,
  params: {
    source: string;
    extraSources?: string[];
    identityReferenceLimit?: number;
    prompt: string;
    adult: boolean;
    stage: string;
    settings?: Record<string, unknown>;
    needsLora?: boolean;
    upscale?: "anime" | "photo";
  },
): Promise<void> {
  if ((params.needsLora ?? true) && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error(`絵を編集するための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "edit-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  let imageRefs = [params.source, ...(params.extraSources ?? [])];
  let prompt = params.prompt;
  let stage = params.stage;
  if (params.identityReferenceLimit) {
    const face = path.join(work, "identity-face.png");
    if (await createFaceReference(params.source, face)) {
      imageRefs = [...imageRefs.slice(0, params.identityReferenceLimit - 1), face];
      prompt +=
        " The final reference is a close-up cropped from reference 1, showing the SAME identity. Use it for fine facial details only; preserve the full-body layout and clothing from reference 1.";
    } else {
      stage += "（顔拡大なし・元画像を参照）";
    }
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, stage);
  const request = path.join(work, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...(params.settings ?? qwenTurboSettings()),
        prompt,
        image_refs: imageRefs,
        video_prompt_type: "KI",
        resolution: "832x1216",
        seed: -1,
      },
    }),
  );
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 60 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error("設定画ができませんでした");
  const file = path.join(
    dir,
    `sheet-front${params.adult ? "-adult" : ""}-${randomBytes(3).toString("hex")}.png`,
  );
  if (params.upscale) {
    // High quality: Real-ESRGAN enlarges it 4 times (the anime model for drawings), then it is brought to twice the size.
    markStage(job, "高画質にしています（拡大と整え）");
    const big = path.join(work, "upscaled.png");
    await runTool(
      REALESRGAN_EXE,
      [
        "-i",
        image,
        "-o",
        big,
        "-n",
        params.upscale === "photo" ? "realesrgan-x4plus" : "realesrgan-x4plus-anime",
        "-s",
        "4",
        "-t",
        "256",
        "-m",
        path.join(path.dirname(REALESRGAN_EXE), "models"),
      ],
      { timeoutMs: 20 * 60_000 },
    );
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", big, "-vf", "scale=iw/2:-2:flags=lanczos", file],
      { timeoutMs: 120_000 },
    );
  } else {
    await runTool(VOICE_FFMPEG, ["-y", "-loglevel", "error", "-i", image, file], {
      timeoutMs: 60_000,
    });
  }
  await fs.rm(work, { recursive: true, force: true });
  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: job.options.comicSeriesId ?? "",
    character: character.name,
    characterId: character.id,
    view: "front",
    ...(params.adult ? { adult: true } : {}),
  };
}
