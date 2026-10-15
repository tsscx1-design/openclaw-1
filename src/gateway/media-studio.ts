import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";
import { ANIMA_FILES } from "./anima-comfy.js";
import {
  allAvatarFrames,
  ANATOMY_CHEST,
  ANATOMY_GROIN,
  avatarFramePrompt,
  MINI_FRAMES,
  miniFramePrompt,
} from "./avatar-frames.js";
import { importGeneratedModel } from "./avatar-models.js";
import {
  characterAssetDir,
  characterLook,
  DATASET_VIEWS,
  datasetCaption,
  getCharacter,
  type LibraryCharacter,
  lookWithOutfit,
  resolveCast,
  saveCharacter,
  triggerFor,
  updateCharacter,
} from "./comic-characters.js";
import { type ComicPanel, getComicSeries, renderPanel, saveComicSeries } from "./comic-series.js";
import { FACEFUSION_PYTHON } from "./face-swap.js";
import { type GenStyle, readGenStyle } from "./gen-style.js";
import { irodoriInstalled } from "./irodori-tts.js";
import { waitForChatTurnEnd } from "./main-llm-idle.js";
import { removeBook } from "./manga-book-store.js";
import { MANGA_EXTRA_NEGATIVE } from "./manga-script.js";
import { generationCudaDevice, resolveGpuMode } from "./media-gpu.js";
import { episodeDir, runComicJob } from "./media/comic.js";
import {
  DMD2_LORA,
  isSdxlModel,
  isWanImageModel,
  KEEP_FAILED,
  MAX_QUEUE,
  MEDIA_MODEL_DIR,
  REMIX_HIGH,
  REMIX_LOW,
  RIFE_SCRIPT,
  SDXL_MODELS,
  sdxlModelOf,
  sdxlSpecOf,
} from "./media/config.js";
import { COMFY_PYTHON_PATH, defaultJobSec, REALESRGAN_EXE } from "./media/estimates.js";
import { resolveSwapTarget, runFaceSwapJob } from "./media/faceswap.js";
import {
  acquireGpu,
  latestTrainingState,
  releaseGpu,
  trainingDatasetCount,
} from "./media/gpu-lease.js";
import {
  IMAGE_GRACE_MS,
  imageSlots,
  imageWorkerChildren,
  renderImage,
  runImageJob,
  stopImageWorker,
  waitForMoreJobs,
} from "./media/image.js";
import { askScriptWriter, MANGA_COMPOSE, runMangaJob } from "./media/manga.js";
import { MOTION_SCRIPT, runMotionJob } from "./media/motion-comic.js";
import { runMusicJob } from "./media/music.js";
import { exists, outDir, pauseFlag } from "./media/paths.js";
import { runQualityPass } from "./media/quality.js";
import {
  activeChildren,
  killProcessTree,
  markStage,
  runTool,
  runWan2gpManifest,
} from "./media/runner.js";
import { mediaStudioStatus } from "./media/status.js";
import { assertAllowedRequest, sanitizeMediaText } from "./media/text.js";
import type { ImageModel, MediaJob, MediaKind, MediaOptions, SdxlModel } from "./media/types.js";
import {
  QWEN_TURBO_RUNNER,
  runVideoJob,
  WAN2GP_DIR,
  WAN2GP_PYTHON,
  WAN2GP_RUNNER,
} from "./media/video.js";
import { runVoiceJob, sanitizeVoiceScript, VOICE_FFMPEG } from "./media/voice-drama.js";
import { isStoreModelId, storeSpecOf } from "./model-store.js";
import { renderModel3d } from "./model3d.js";
import { analyzePhoto, createFaceReference } from "./photo-analysis.js";
import {
  adultBaseFile,
  loadSampleIndex,
  PRECISE_SAMPLE_KEYS,
  resolveSampleSubject,
  sampleFileName,
  samplePrompt,
  samplesDir,
  saveSampleIndex,
} from "./sample-factory.js";
export {
  type ImageModel,
  type MediaJob,
  type MediaJobStatus,
  type MediaKind,
  type MediaOptions,
  type VideoEngine,
  type WanImageModel,
} from "./media/types.js";
export { isWanImageModel, sdxlSampling } from "./media/config.js";
export { mediaStudioStatus } from "./media/status.js";
export {
  assertAllowedRequest,
  isSexualRequest,
  sanitizeH3Prompt,
  sanitizeMediaText,
} from "./media/text.js";
export {
  buildIpAdapterOptions,
  estimateFaceSwapJob,
  estimateMediaJob,
  type FaceSwapEstimate,
  type MediaEstimate,
  selectBgmPrompt,
} from "./media/estimates.js";
export { latestTrainingState, trainingDatasetCount } from "./media/gpu-lease.js";
export { sanitizeLyrics } from "./media/music.js";
export { sanitizeVoiceScript } from "./media/voice-drama.js";
export { sdcppVideoArgs, wan2gpFrames } from "./media/video.js";
export { writeCharacterLook, writeComicCast } from "./media/comic.js";
export { bridgePlan, joinEstimateSec } from "./media/motion-join.js";

// Local image / video / music generation on the RTX 4060 (8 GB).
// The main chat LLM holds almost all VRAM, so a job stops it (the watchdog pause flag keeps it
// down) and restarts it once the queue is empty. Jobs run one at a time.
//   image: stable-diffusion.cpp (CUDA) + WAI-Illustrious SDXL (uncensored anime)
//   video: stable-diffusion.cpp vid_gen + Wan 2.2 14B Rapid AIO NSFW (image-to-video, 4 steps);
//          text-to-video renders a still with the image model first, then animates it
//   music: acestep.cpp (ace-lm + ace-synth) + ACE-Step 1.5 turbo; lyrics are written by its LM

/** The job being worked on and the tool process it runs; a delete stops them. */
export let currentJob: MediaJob | undefined;

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

/** Removes backgrounds (rembg, CPU) from every PNG in a folder and crops them to one box. */
const REMOVE_BG_SCRIPT = "D:\\media-tools\\image\\remove_bg.py";

/** Where a character's avatar pictures are kept. */
export function avatarDir(characterId: string): string {
  return path.join(characterAssetDir(characterId), "avatar");
}

/**
 * The avatar pictures of a character: every expression with the mouth closed and open, and a
 * blink, drawn from the sheet by Qwen Image 2.1 (Viggle Turbo, ~20 s each). The phone shows them
 * in the chat screen; nothing is shown until all are done.
 */
export async function runCharacterAvatar(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（アバターの元になります）");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error("アバターの絵を作る部品（Viggle Turbo）が見つかりません");
  }
  const frames = allAvatarFrames();
  const work = path.join(characterAssetDir(character.id), "avatar-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, `アバターの表情を作っています（0/${frames.length}）`);
  const produced = await runWan2gpManifest(
    job,
    work,
    frames.map((frame) => ({
      ...qwenTurboSettings(),
      prompt: avatarFramePrompt(frame),
      image_refs: [source],
      video_prompt_type: "KI",
      resolution: "832x1216",
      // The same seed for every frame keeps the pictures close to each other.
      seed: 20261007,
    })),
    {
      timeoutMs: 60 * 60_000,
      onComplete: (done) => {
        markStage(job, `アバターの表情を作っています（${done}/${frames.length}）`);
      },
    },
  );
  const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (images.length < frames.length)
    throw new Error(`アバターの絵が足りません（${images.length}/${frames.length}枚）`);
  const out = avatarDir(character.id);
  await fs.rm(out, { recursive: true, force: true });
  await fs.mkdir(out, { recursive: true });
  for (const [index, frame] of frames.entries()) {
    const image = images[index];
    if (!image) continue;
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", image, path.join(out, `${frame.key}.png`)],
      { timeoutMs: 60_000 },
    );
  }
  await fs.rm(work, { recursive: true, force: true });
  // The background goes (transparent PNG) and every picture is cut to the same box around her.
  markStage(job, "背景を透明にしています");
  await runTool(WAN2GP_PYTHON, [REMOVE_BG_SCRIPT, out], {
    cwd: WAN2GP_DIR,
    timeoutMs: 15 * 60_000,
  });
  job.file = path.join(out, "normal_closed.png");
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/** Where a character's mini pictures (the helper walking about the app) are kept. */
export function miniDir(characterId: string): string {
  return path.join(characterAssetDir(characterId), "mini");
}

/** The mini character's pictures (front, three-quarter, side, back views and a wave), drawn from the sheet. */
export async function runCharacterMini(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（ミニキャラの元になります）");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA))))
    throw new Error("絵を作る部品（Viggle Turbo）が見つかりません");
  const work = path.join(characterAssetDir(character.id), "mini-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, `ミニキャラを作っています（0/${MINI_FRAMES.length}）`);
  const produced = await runWan2gpManifest(
    job,
    work,
    MINI_FRAMES.map((frame) => ({
      ...qwenTurboSettings(),
      prompt: miniFramePrompt(frame),
      image_refs: [source],
      video_prompt_type: "KI",
      resolution: "832x1216",
      seed: 20261009,
    })),
    {
      timeoutMs: 60 * 60_000,
      onComplete: (done) => {
        markStage(job, `ミニキャラを作っています（${done}/${MINI_FRAMES.length}）`);
      },
    },
  );
  const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (images.length < MINI_FRAMES.length)
    throw new Error(`ミニキャラの絵が足りません（${images.length}/${MINI_FRAMES.length}枚）`);
  const out = miniDir(character.id);
  await fs.rm(out, { recursive: true, force: true });
  await fs.mkdir(out, { recursive: true });
  for (const [index, frame] of MINI_FRAMES.entries()) {
    const image = images[index];
    if (image)
      await runTool(
        VOICE_FFMPEG,
        ["-y", "-loglevel", "error", "-i", image, path.join(out, `${frame.key}.png`)],
        { timeoutMs: 60_000 },
      );
  }
  await fs.rm(work, { recursive: true, force: true });
  markStage(job, "背景を透明にしています");
  await runTool(WAN2GP_PYTHON, [REMOVE_BG_SCRIPT, out], {
    cwd: WAN2GP_DIR,
    timeoutMs: 15 * 60_000,
  });
  job.file = path.join(out, "mini_front.png");
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/**
 * Makes a 3D model (GLB) from the character's sheet with ComfyUI (Pixal3D + TRELLIS.2) and adds it
 * to her models. Showing it in the chat is a separate step (Live2D is shown; VRM / glb are kept).
 */
export async function runCharacterModel3d(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（3Dモデルの元になります）");
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const work = path.join(characterAssetDir(character.id), "model3d-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  const glb = path.join(work, "model.glb");
  await renderModel3d(source, glb, {
    onStage: (stage) => markStage(job, stage),
    isCancelled: () => job.cancelRequested === true,
    cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
  });
  markStage(job, "3Dモデルを登録しています");
  await importGeneratedModel(character.id, glb, `${character.name}の3Dモデル`);
  await fs.rm(work, { recursive: true, force: true });
  // A small picture of the source stands for the job in the lists.
  const thumb = path.join(outDir(), `${job.id}.png`);
  await fs.copyFile(source, thumb);
  job.file = thumb;
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/** Width and height of a PNG from its header. */
async function pngSize(file: string): Promise<{ width: number; height: number }> {
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
function photoEngineSettings(engine: "fast" | "precise" | "edit2509"): {
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

/**
 * One sample picture of the sample factory: the character's standing picture (or the adult base made
 * from it) is edited with Qwen Image 2.1 (Viggle Turbo) so that only the chosen setting changes.
 * The result is kept in the factory folder and listed in its index.
 */
export async function runCharacterSample(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const makingAdultBase = job.options.sampleAdultBase === true;
  const base = job.options.sampleBase === "adult" && !makingAdultBase ? "adult" : "normal";
  const source = base === "adult" ? adultBaseFile(character.id) : character.refImage;
  if (!source || !(await exists(source))) {
    throw new Error(
      base === "adult"
        ? "先に「成人向けの基準画像」を作ってください"
        : "先に設定画を決めてください（元になります）",
    );
  }
  const wanted = job.options.sampleEngine ?? "fast";
  // Large changes of the body (a limbless body) only work with the precise editor.
  const engine =
    wanted === "fast" && PRECISE_SAMPLE_KEYS.has(job.options.sampleKey ?? "") ? "precise" : wanted;
  const picked = photoEngineSettings(engine);
  if (picked.needsLora && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error(`サンプルを作るための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
  }
  let prompt: string;
  let key = "";
  if (makingAdultBase) {
    prompt =
      "Keep the same adult woman exactly as in the picture: face, hair, eye color, body shape and the same plain background. " +
      "Change only this: she is completely nude, standing, front view, full body, arms relaxed at her sides.";
  } else {
    key = job.options.sampleKey ?? "";
    const subject = await resolveSampleSubject(key);
    if (!subject) throw new Error("その項目が見つかりません");
    assertAllowedRequest(subject.tags, job.options.sampleNote ?? "");
    const index = await loadSampleIndex();
    // What is written now wins; otherwise the saved default instruction of this setting.
    const note =
      job.options.sampleNote !== undefined ? job.options.sampleNote : (index.defaults[key] ?? "");
    prompt = samplePrompt(subject, base, note);
    job.options.sampleNote = note;
  }
  await fs.mkdir(samplesDir(), { recursive: true });
  const work = path.join(samplesDir(), `work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  // About one megapixel in multiples of 16, keeping the shape of the base picture.
  const fitted = path.join(work, "source.png");
  const budget = 1_000_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      fitted,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(fitted);
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, makingAdultBase ? "成人向けの基準画像を作っています" : "サンプルを作っています");
  const request = path.join(work, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...picked.settings,
        prompt,
        image_refs: [fitted],
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
  if (!image) throw new Error("サンプルができませんでした");
  if (makingAdultBase) {
    await fs.copyFile(image, adultBaseFile(character.id));
    job.file = adultBaseFile(character.id);
    job.mime = "image/png";
  } else {
    const file = path.join(samplesDir(), sampleFileName(key));
    // Shown on the phone: at most 768 px tall, JPEG.
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", image, "-vf", "scale=-2:'min(768,ih)'", "-q:v", "3", file],
      { timeoutMs: 60_000 },
    );
    const index = await loadSampleIndex();
    index.items[key] = { file, base, note: job.options.sampleNote ?? "", at: Date.now() };
    await saveSampleIndex(index);
    job.file = file;
    job.mime = "image/jpeg";
  }
  await fs.rm(work, { recursive: true, force: true });
  job.comic = { seriesId: "", character: character.name, characterId: character.id, sample: true };
}

/** How many samples one Wan2GP start draws: the model loads once, and both GPUs share the chunk. */
const SAMPLE_BATCH_CHUNK = 8;

/**
 * Many samples (a whole genre) in one job. The picture, prompt and model are the same as for a single
 * sample; only the loading is shared and the work is split over both GPUs. A chunk that does not
 * return exactly one picture per setting is counted as failed, because the pictures could not be
 * matched to their settings.
 */
export async function runSampleBatch(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const base = job.options.sampleBase === "adult" ? "adult" : "normal";
  const source = base === "adult" ? adultBaseFile(character.id) : character.refImage;
  if (!source || !(await exists(source))) {
    throw new Error(
      base === "adult"
        ? "先に「成人向けの基準画像」を作ってください"
        : "先に設定画を決めてください（元になります）",
    );
  }
  const wanted = job.options.sampleEngine ?? "fast";
  await fs.mkdir(samplesDir(), { recursive: true });
  const work = path.join(samplesDir(), `work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  const fitted = path.join(work, "source.png");
  const budget = 1_000_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      fitted,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(fitted);

  type SampleEntry = {
    key: string;
    note: string;
    prompt: string;
    engine: "fast" | "precise" | "edit2509";
  };
  const entries: SampleEntry[] = [];
  let skipped = 0;
  const index0 = await loadSampleIndex();
  for (const key of job.options.sampleKeys ?? []) {
    const subject = await resolveSampleSubject(key);
    if (!subject) {
      skipped += 1;
      continue;
    }
    const note = index0.defaults[key] ?? "";
    try {
      // The user's own check (blocked-terms) decides; a refused setting is left out, not fatal.
      assertAllowedRequest(subject.tags, note);
    } catch {
      skipped += 1;
      continue;
    }
    entries.push({
      key,
      note,
      prompt: samplePrompt(subject, base, note),
      // Large changes of the body only work with the precise editor.
      engine: wanted === "fast" && PRECISE_SAMPLE_KEYS.has(key) ? "precise" : wanted,
    });
  }
  if (entries.length === 0) throw new Error("作れる項目がありませんでした");

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const total = entries.length;
  let done = 0;
  let failed = 0;
  let lastFile = "";
  for (const engine of ["fast", "precise", "edit2509"] as const) {
    const group = entries.filter((entry) => entry.engine === engine);
    if (group.length === 0) continue;
    const picked = photoEngineSettings(engine);
    if (picked.needsLora && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
      throw new Error(`サンプルを作るための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
    }
    for (let at = 0; at < group.length; at += SAMPLE_BATCH_CHUNK) {
      if (job.cancelRequested) throw new Error("中止しました");
      const chunk = group.slice(at, at + SAMPLE_BATCH_CHUNK);
      markStage(job, `サンプルを作っています ${done + failed}/${total}`);
      const chunkDir = path.join(work, `chunk-${engine}-${at}`);
      await fs.mkdir(chunkDir, { recursive: true });
      let files: string[] = [];
      try {
        files = await runWan2gpManifest(
          job,
          chunkDir,
          chunk.map((entry) => ({
            ...picked.settings,
            prompt: entry.prompt,
            image_refs: [fitted],
            video_prompt_type: "KI",
            resolution: `${size.width}x${size.height}`,
            seed: -1,
          })),
          {
            timeoutMs: 90 * 60_000,
            onComplete: (finished) => {
              job.stage = `サンプルを作っています ${done + failed + finished}/${total}`;
            },
          },
        );
      } catch (err) {
        if (job.cancelRequested) throw err;
      }
      const images = files.filter((file) => /\.(png|jpe?g|webp)$/i.test(file));
      if (images.length !== chunk.length) {
        failed += chunk.length;
        continue;
      }
      const index = await loadSampleIndex();
      for (const [n, entry] of chunk.entries()) {
        const picture = images[n];
        if (!picture) continue;
        const file = path.join(samplesDir(), sampleFileName(entry.key));
        // Shown on the phone: at most 768 px tall, JPEG.
        await runTool(
          VOICE_FFMPEG,
          [
            "-y",
            "-loglevel",
            "error",
            "-i",
            picture,
            "-vf",
            "scale=-2:'min(768,ih)'",
            "-q:v",
            "3",
            file,
          ],
          { timeoutMs: 60_000 },
        );
        index.items[entry.key] = { file, base, note: entry.note, at: Date.now() };
        lastFile = file;
      }
      await saveSampleIndex(index);
      done += chunk.length;
    }
  }
  await fs.rm(work, { recursive: true, force: true });
  if (done === 0) throw new Error("サンプルが1枚もできませんでした");
  markStage(
    job,
    `完成（${done}枚${failed + skipped > 0 ? `、できなかった ${failed + skipped} 件` : ""}）`,
  );
  job.file = lastFile;
  job.mime = "image/jpeg";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, sample: true };
}

/**
 * The newest finished front sheet of a character becomes its training reference when none was
 * chosen in the app (a chat asks for training in one go). Returns the updated entry.
 */
export async function adoptLatestSheet(character: LibraryCharacter): Promise<LibraryCharacter> {
  if (character.refImage && (await exists(character.refImage))) return character;
  const sheets = (await listMediaJobs(10_000)).filter(
    (job) =>
      job.kind === "comic" &&
      job.status === "done" &&
      job.comic?.characterId === character.id &&
      job.comic?.view !== "turnaround" &&
      !job.comic?.sample &&
      job.file,
  );
  const latest = sheets[0];
  if (!latest?.file || !(await exists(latest.file))) return character;
  const icon = path.join(characterAssetDir(character.id), "icon.png");
  await makeCharacterIcon(latest.file, icon).catch(() => undefined);
  const updated = {
    ...character,
    refImage: latest.file,
    sheetJobId: latest.id,
    icon,
    updatedAt: Date.now(),
  };
  await saveCharacter(updated);
  return updated;
}

/** Square face picture from a sheet (anime face detector; the upper middle when none is found). */
export async function makeCharacterIcon(sheet: string, icon: string): Promise<void> {
  await runTool(WAN2GP_PYTHON, [MANGA_COMPOSE, "--icon", sheet, icon], { timeoutMs: 2 * 60_000 });
}

const SD_SCRIPTS_DIR = "D:\\media-tools\\sd-scripts";

const SD_SCRIPTS_PYTHON = path.join(SD_SCRIPTS_DIR, "venv", "Scripts", "python.exe");

/**
 * Qwen Image 2.1 (7B, already installed) with the Viggle Turbo LoRA: 6 steps instead of ~30, so a
 * reference picture becomes a new view in about 25 s. The LoRA files sit in Wan2GP's loras folder.
 */
const QWEN_EDIT_MODEL = "qwen_image_21_7B";

const QWEN_LORA_DIR = "D:\\media-tools\\Wan2GP\\loras\\qwen21";

const VIGGLE_TURBO_LORA = "Qwen-Image-2.1-viggle-turbo-v0.3-6step-lora-r256.safetensors";

const OUTPAINT_LORA = "qwen-image-2.1-outpaint-v2.safetensors";

/** The Wan2GP settings that turn Qwen Image 2.1 into the 6-step editor (plus extra LoRAs, if any). */
export function qwenTurboSettings(extraLoras: string[] = []): Record<string, unknown> {
  return {
    model_type: QWEN_EDIT_MODEL,
    num_inference_steps: 6,
    guidance_scale: 1,
    sample_solver: "viggle_v02",
    activated_loras: [...extraLoras, VIGGLE_TURBO_LORA],
    loras_multipliers: [...extraLoras, VIGGLE_TURBO_LORA].map(() => "1").join(" "),
    negative_prompt: "",
  };
}

const LORA_REPEATS = 8;

const LORA_EPOCHS = 6;

/**
 * Character LoRA: about 24 pictures of the character made from their sheet with Qwen Image Edit
 * (angles, expressions, poses, light; the person stays the same), captioned with a trigger word
 * and only what varies, then a LoRA on the SDXL checkpoint trained with 8 GB settings (U-Net
 * only, gradient checkpointing, 8-bit AdamW, cached latents and text encoder outputs).
 */
export async function runCharacterLora(job: MediaJob): Promise<void> {
  const found = await getCharacter(job.options.comicCharacterId ?? "");
  if (!found) throw new Error("その登場人物はいません");
  // Asked in one go from a chat: the sheet drawn just before (same queue) is the reference.
  const character = await adoptLatestSheet(found);
  if (!character.refImage || !(await exists(character.refImage))) {
    throw new Error("先に設定画を決めてください（学習の元になります）");
  }
  if (!(await exists(SD_SCRIPTS_PYTHON)))
    throw new Error("学習ソフト（sd-scripts）が見つかりません");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error("学習用の絵を作る部品（Viggle Turbo）が見つかりません");
  }
  const update = async (patch: Partial<LibraryCharacter>) => {
    const latest = (await getCharacter(character.id)) ?? character;
    await saveCharacter({ ...latest, ...patch, updatedAt: Date.now() });
  };
  const trigger = triggerFor(character.id);
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "lora-work");
  const imageDir = path.join(work, "img", `${LORA_REPEATS}_${trigger}`);
  try {
    await update({
      loraStatus: "training",
      loraStep: "学習用の絵を作っています",
      loraError: undefined,
    });
    // A training that was cut off (the gateway restarted ...) goes on from where it was: the pictures made for it are kept,
    // and the training itself starts from the last saved epoch (see the resume below).
    const kept = await trainingDatasetCount(imageDir);
    const reuse = kept >= 10;
    if (!reuse) {
      await fs.rm(work, { recursive: true, force: true });
      await fs.mkdir(imageDir, { recursive: true });
    } else {
      markStage(job, "前回の続きから始めます（学習用の絵は、できています）");
      await update({ loraStep: "前回の続きから始めます" });
    }
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    let datasetCount = kept;

    if (!reuse) {
      // 1. Dataset: every view in one Wan2GP session (the 20B editor loads once).
      markStage(job, `学習用の絵を作っています（${DATASET_VIEWS.length}枚・Qwen Image Edit）`);
      const editDir = path.join(work, "edit");
      await fs.mkdir(editDir, { recursive: true });
      const produced = await runWan2gpManifest(
        job,
        editDir,
        DATASET_VIEWS.map((view) => ({
          ...qwenTurboSettings(),
          prompt: `${view.edit} Keep the same anime art style, the same face, hair, eye color and body. Plain composition, no text.`,
          image_refs: [character.refImage],
          video_prompt_type: "KI",
          resolution: "832x1216",
          seed: -1,
        })),
        {
          timeoutMs: 90 * 60_000,
          onComplete: (done) => {
            markStage(job, `学習用の絵を作っています（${done}/${DATASET_VIEWS.length}）`);
            void update({
              loraStep: `学習用の絵 ${done}/${DATASET_VIEWS.length}`,
            });
          },
        },
      );
      const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
      if (images.length < 10) throw new Error(`学習用の絵が足りません（${images.length}枚）`);
      const currentLook = characterLook(character);
      const gender = /\b1boy\b/.test(currentLook)
        ? "1boy"
        : /\b1girl\b/.test(currentLook)
          ? "1girl"
          : "";
      for (const [i, image] of images.entries()) {
        const target = path.join(
          imageDir,
          `${String(i + 1).padStart(2, "0")}${path.extname(image)}`,
        );
        await fs.copyFile(image, target);
        const view = DATASET_VIEWS[i];
        await fs.writeFile(
          target.replace(/\.\w+$/, ".txt"),
          datasetCaption(trigger, gender, view?.tags ?? ""),
        );
      }
      // The sheet itself is the clearest picture of them.
      await fs.copyFile(character.refImage, path.join(imageDir, "00.png"));
      await fs.writeFile(
        path.join(imageDir, "00.txt"),
        datasetCaption(trigger, gender, "full body, standing, front view, white background"),
      );
      datasetCount = images.length + 1;
    }
    // 2. Training.
    const steps = datasetCount * LORA_REPEATS * LORA_EPOCHS;
    markStage(job, `専用学習をしています（約${steps}ステップ）`);
    // The name of the result stays the same when the training goes on, so its saved epochs are found again.
    const trainFile = path.join(work, "train.json");
    const savedName = await fs.readFile(trainFile, "utf-8").then(
      (text) => (JSON.parse(text) as { outName?: string }).outName,
      () => undefined,
    );
    const outName =
      savedName && /^[\w-]+$/.test(savedName)
        ? savedName
        : `${trigger}-${randomBytes(2).toString("hex")}`;
    await fs.writeFile(trainFile, JSON.stringify({ outName }), "utf-8");
    const resumeFrom = await latestTrainingState(dir, outName);
    const model = sdxlSpecOf(sdxlModelOf(job));
    let lastPercent = -1;
    await runTool(
      SD_SCRIPTS_PYTHON,
      [
        "sdxl_train_network.py",
        "--pretrained_model_name_or_path",
        model.file,
        ...(model.vpred ? ["--v_parameterization", "--zero_terminal_snr"] : []),
        "--train_data_dir",
        path.join(work, "img"),
        "--output_dir",
        dir,
        "--output_name",
        outName,
        "--save_model_as",
        "safetensors",
        "--caption_extension",
        ".txt",
        "--resolution",
        "1024,1024",
        "--enable_bucket",
        "--min_bucket_reso",
        "512",
        "--max_bucket_reso",
        "1536",
        "--network_module",
        "networks.lora",
        "--network_dim",
        "16",
        "--network_alpha",
        "8",
        "--network_train_unet_only",
        "--train_batch_size",
        "1",
        "--max_train_epochs",
        String(LORA_EPOCHS),
        "--learning_rate",
        "1e-4",
        "--unet_lr",
        "1e-4",
        "--lr_scheduler",
        "cosine",
        "--lr_warmup_steps",
        "50",
        "--optimizer_type",
        "AdamW8bit",
        "--mixed_precision",
        "fp16",
        "--save_precision",
        "fp16",
        "--gradient_checkpointing",
        "--cache_latents",
        "--cache_latents_to_disk",
        "--cache_text_encoder_outputs",
        "--sdpa",
        "--no_half_vae",
        "--max_data_loader_n_workers",
        "1",
        "--seed",
        "42",
        // every epoch is saved (with the optimizer's state, only the last one is kept), so a cut-off run goes on from there
        "--save_every_n_epochs",
        "1",
        "--save_state",
        "--save_last_n_epochs_state",
        "1",
        ...(resumeFrom ? ["--resume", resumeFrom] : []),
      ],
      {
        cwd: SD_SCRIPTS_DIR,
        timeoutMs: 4 * 60 * 60_000,
        onLine: (line) => {
          // tqdm: "steps:  45%|████▌     | 450/1000 [10:01<12:15, ...]"
          const match = /steps:\s+(\d+)%.*?\|\s*(\d+)\/(\d+)\s*\[[^<]*<([\d:]+)/.exec(line);
          if (!match) return;
          const percent = Number(match[1]);
          if (percent === lastPercent) return;
          lastPercent = percent;
          markStage(job, `専用学習をしています ${percent}%（残り約${match[4]}）`);
          void update({ loraStep: `学習中 ${percent}%（残り約${match[4]}）` });
        },
      },
    );
    const lora = path.join(dir, `${outName}.safetensors`);
    if (!(await exists(lora))) throw new Error("学習結果のファイルができませんでした");
    // the saved epochs and states are not needed any more
    for (const name of await fs.readdir(dir).catch(() => [] as string[])) {
      if (
        name.startsWith(`${outName}-`) &&
        (name.endsWith("-state") || /-\d{6}\.safetensors$/.test(name))
      )
        await fs.rm(path.join(dir, name), { recursive: true, force: true }).catch(() => undefined);
    }
    await update({ loraStatus: "ready", lora, trigger, loraStep: undefined, loraError: undefined });
    job.file = character.refImage;
    job.mime = "image/png";
    job.comic = { seriesId: "", character: character.name, characterId: character.id };
  } catch (err) {
    await update({
      loraStatus: "failed",
      loraStep: undefined,
      loraError: String(err instanceof Error ? err.message : err).slice(0, 300),
    });
    throw err;
  } finally {
    // Keep the dataset pictures for a look; drop the editor's scratch output.
    await fs.rm(path.join(work, "edit"), { recursive: true, force: true }).catch(() => undefined);
  }
}

// ---------- Queue ----------

export const jobs = new Map<string, MediaJob>();

export const queue: string[] = [];

export let working = false;

function metaPath(id: string): string {
  return path.join(outDir(), `${id}.json`);
}

export async function saveJobMeta(job: MediaJob): Promise<void> {
  const { sourceImageBase64: _drop, ...options } = job.options;
  await fs.writeFile(metaPath(job.id), JSON.stringify({ ...job, options }));
}

export async function loadJobMeta(id: string): Promise<MediaJob | undefined> {
  if (!/^[a-f0-9]{12}$/.test(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(metaPath(id), "utf-8")) as MediaJob;
  } catch {
    return undefined;
  }
}

async function pruneResults(): Promise<void> {
  const failed = (await listMediaJobs(10_000)).filter((job) => job.status === "error");
  for (const job of failed.slice(KEEP_FAILED)) {
    await fs.rm(metaPath(job.id), { force: true });
    if (job.file) await fs.rm(job.file, { force: true });
  }
}

async function processQueue(): Promise<void> {
  if (working) return;
  working = true;
  try {
    for (let id = queue.shift(); id; id = queue.shift()) {
      const job = jobs.get(id);
      if (!job) continue;
      currentJob = job;
      job.status = "running";
      try {
        // Not needed while the GPU is already ours (the chat model is down then).
        if (job.chat && !(await exists(pauseFlag()))) {
          job.stage = "チャットの返事が終わるのを待っています";
          await waitForChatTurnEnd();
        }
        if (job.cancelRequested) throw new Error("中止しました");
        job.startedAt = Date.now();
        await fs.mkdir(outDir(), { recursive: true });
        // ── 作業内容を stage に設定（Android の「何をしているか」表示に使う）──
        const kindLabel: Record<string, string> = {
          image: "画像を生成しています",
          manga: "漫画を描いています",
          comic: "コミックエピソードを作っています",
          voice: "ボイスを合成しています",
          motion: "モーションコミックを作っています",
          faceswap: "顔を入れ替えています",
          video: "動画を生成しています",
          music: "音楽を生成しています",
        };
        if (!job.stage || job.stage === "チャットの返事が終わるのを待っています") {
          job.stage = kindLabel[job.kind] ?? `${job.kind} を処理しています`;
        }
        if (job.kind === "image") await runImageJob(job);
        else if (job.kind === "manga") await runMangaJob(job);
        else if (job.kind === "comic") await runComicJob(job);
        else if (job.kind === "voice") await runVoiceJob(job);
        else if (job.kind === "motion") await runMotionJob(job);
        else if (job.kind === "faceswap") await runFaceSwapJob(job);
        else if (job.kind === "video") await runVideoJob(job);
        else await runMusicJob(job);
        await runQualityPass(job);
        job.status = "done";
        markStage(job, "完成");
      } catch (err) {
        job.status = "error";
        job.error = String(err instanceof Error ? err.message : err).slice(0, 600);
        job.stage = undefined;
      } finally {
        currentJob = undefined;
        job.finishedAt = Date.now();
        job.durationSec = Math.round((job.finishedAt - (job.startedAt ?? job.finishedAt)) / 1000);
        job.options.sourceImageBase64 = undefined;
        if (job.cancelRequested) {
          // Deleted while running: nothing of it is kept.
          jobs.delete(job.id);
          if (job.file) await fs.rm(job.file, { force: true }).catch(() => undefined);
        } else {
          await saveJobMeta(job).catch(() => undefined);
          for (const listener of finishedListeners) {
            void Promise.resolve()
              .then(() => listener(job))
              .catch(() => undefined);
          }
        }
        // Hand the GPU back between jobs only when nothing else is waiting. A warm image worker
        // gets a short grace period so a follow-up image skips the start-up — but not after a
        // job asked for in chat: the conversation needs its model back to go on.
        const nextMode = resolveGpuMode(queue[0] ? jobs.get(queue[0])?.options.gpuMode : undefined);
        const modeChanged = nextMode !== resolveGpuMode(job.options.gpuMode);
        if (
          (queue.length === 0 || modeChanged) &&
          !(
            imageSlots[0]!.child &&
            !modeChanged &&
            !job.chat &&
            (await waitForMoreJobs(IMAGE_GRACE_MS))
          )
        ) {
          stopImageWorker();
          await releaseGpu();
        }
      }
    }
    await pruneResults().catch(() => undefined);
  } finally {
    working = false;
    if (queue.length === 0) {
      stopImageWorker();
      await releaseGpu();
    }
  }
}

type FinishedListener = (job: MediaJob) => void | Promise<void>;

const finishedListeners = new Set<FinishedListener>();

/** Calls `listener` with every job that ends, done or failed (not with deleted ones). */
export function onMediaJobFinished(listener: FinishedListener): () => void {
  finishedListeners.add(listener);
  return () => finishedListeners.delete(listener);
}

/** Real-person checkpoints to use when the switch says "photo", best first. */
const PHOTO_MODEL_CANDIDATES = ["asianreal", "cyberreal", "lustify"] as const;

/**
 * Makes the picture models of a job match the style switch: with "photo" an anime model is replaced by a real-person
 * model, with "anime" a real-person model is replaced by WAI. Models that can draw either (Z-Image, Qwen ...) stay.
 * Only the kinds that draw pictures get a model when none was chosen; "auto" changes nothing.
 */
export async function applyGenStyle(
  kind: MediaKind,
  options: MediaOptions,
  style: GenStyle,
  fileExists: (file: string) => Promise<boolean> = exists,
): Promise<MediaOptions> {
  if (style === "auto") return options;
  const swap = async (
    model: ImageModel | undefined,
    drawsPictures: boolean,
  ): Promise<ImageModel | undefined> => {
    if (model === undefined && !drawsPictures) return undefined;
    const effective: ImageModel = model ?? "wai";
    if (isWanImageModel(effective)) return model;
    const isPhoto = effective === "anima" ? false : sdxlSpecOf(effective as SdxlModel).photo;
    if (style === "photo" && !isPhoto) {
      for (const candidate of PHOTO_MODEL_CANDIDATES)
        if (await fileExists(SDXL_MODELS[candidate].file)) return candidate;
      return model;
    }
    if (style === "anime" && isPhoto) return "wai";
    return model;
  };
  const drawsPictures = kind === "image" || kind === "manga" || kind === "comic";
  const imageModel = await swap(options.imageModel, drawsPictures);
  const motionImageModel = options.motionImageModel
    ? await swap(options.motionImageModel, false)
    : undefined;
  return {
    ...options,
    ...(imageModel ? { imageModel } : {}),
    ...(motionImageModel ? { motionImageModel } : {}),
  };
}

export async function startMediaJob(
  kind: MediaKind,
  request: string,
  options: MediaOptions,
  origin: { chat?: { partnerId?: string } } = {},
): Promise<MediaJob> {
  // A job that did not choose a GPU mode runs in the default one (both GPUs), wherever it was started from.
  options = { ...options, gpuMode: resolveGpuMode(options.gpuMode) };
  // The style switch (anime / photo) decides which kind of picture model draws.
  options = await applyGenStyle(kind, options, await readGenStyle());
  const inheritedId = options.sceneState?.inheritJobId ?? options.branch?.parentJobId;
  if (inheritedId) {
    const source = await getMediaJob(inheritedId);
    if (source) {
      options = {
        ...options,
        characterLock: options.characterLock ?? source.options.characterLock,
        sceneState: {
          ...source.options.sceneState,
          ...options.sceneState,
          inheritJobId: inheritedId,
        },
      };
    }
  }
  const status = await mediaStudioStatus();
  const engine = options.engine ?? "standard";
  const usesWan2gp = kind === "video" && engine !== "standard" && engine !== "remix";
  const needed = usesWan2gp ? status.wan2gp : status[kind];
  if (!needed.ready) {
    throw new Error(`${kind} generation is not installed (missing: ${needed.missing.join(", ")})`);
  }
  if (
    kind === "video" &&
    engine === "remix" &&
    !((await exists(REMIX_HIGH)) && (await exists(REMIX_LOW)))
  ) {
    throw new Error("Remix のモデルが見つかりません");
  }
  if (kind === "video" && options.smooth && !usesWan2gp && !(await exists(RIFE_SCRIPT))) {
    throw new Error("なめらか補間（RIFE）が見つかりません");
  }
  if (isWanImageModel(options.imageModel) && !status.wan2gp.ready) {
    throw new Error("この画像モデルには Wan2GP が必要です");
  }
  if (options.imageModel === "qwenimgturbo" && !(await exists(QWEN_TURBO_RUNNER))) {
    throw new Error("Qwen Image 2.1 Turbo の実行ファイルが見つかりません");
  }
  if (options.imageModel === "anima") {
    for (const file of [COMFY_PYTHON_PATH, ...ANIMA_FILES]) {
      if (!(await exists(file)))
        throw new Error(`Anima のファイルが見つかりません（${path.basename(file)}）`);
    }
  }
  if (
    isSdxlModel(options.imageModel) &&
    isStoreModelId(options.imageModel) &&
    !storeSpecOf(options.imageModel)
  ) {
    throw new Error(
      "このモデルは、まだダウンロードされていません（モデルの導入画面から入れてください）",
    );
  }
  if (isSdxlModel(options.imageModel) && !(await exists(sdxlSpecOf(options.imageModel).file))) {
    throw new Error(`画像モデル（${options.imageModel}）のファイルが見つかりません`);
  }
  if (options.fast && !(await exists(DMD2_LORA))) {
    throw new Error("高速モード（DMD2）のファイルが見つかりません");
  }
  // A voice line keeps Irodori's emojis (moans, breaths) and its line breaks.
  const clean = kind === "voice" ? sanitizeVoiceScript(request) : sanitizeMediaText(request);
  // A comic episode may be left to the writer; sheets and redraws need no text at all.
  if (!clean && kind !== "video" && kind !== "comic" && kind !== "faceswap")
    throw new Error("内容を入力してください");
  if (kind === "faceswap") {
    if (!resolveSwapTarget(options)) throw new Error("入れ替える動画か画像を選んでください");
    if (!options.swapSources?.length)
      throw new Error("入れ替える顔（キャラか写真）を選んでください");
    if (!(await exists(FACEFUSION_PYTHON)))
      throw new Error("顔入れ替えのソフト（FaceFusion）が見つかりません");
  }
  if (kind === "comic") {
    const forCharacter =
      options.comicTask === "sheet" ||
      options.comicTask === "lora" ||
      options.comicTask === "outpaint" ||
      options.comicTask === "avatar" ||
      options.comicTask === "sample" ||
      options.comicTask === "model3d" ||
      options.comicTask === "mini";
    if (
      forCharacter
        ? !(await getCharacter(options.comicCharacterId ?? ""))
        : !(await getComicSeries(options.comicSeriesId ?? ""))
    ) {
      throw new Error(forCharacter ? "その登場人物はいません" : "作品が見つかりません");
    }
    if (options.comicTask === "lora" && !(await exists(SD_SCRIPTS_PYTHON))) {
      throw new Error("学習ソフト（sd-scripts）が見つかりません");
    }
  }
  assertAllowedRequest(clean, options.lyrics ?? "");
  if (kind === "motion") {
    if (
      options.motionRevoiceJobId &&
      (options.motionContinueJobId || options.motionJoinJobIds?.length)
    ) {
      throw new Error("声の変更と、下書きの続行・動画の結合は同時に指定できません");
    }
    if (options.motionRevoiceJobId && !options.voiceId && !options.voiceCaption?.trim()) {
      throw new Error("変更後の声か声の説明を選んでください");
    }
    if (!(await exists(MOTION_SCRIPT)))
      throw new Error("モーションコミックの仕組み（motion_comic.py）が見つかりません");
    if (!(await irodoriInstalled())) throw new Error("声のソフト（Irodori-TTS）が見つかりません");
    if (options.motionImageModel) {
      const spec = isSdxlModel(options.motionImageModel)
        ? sdxlSpecOf(options.motionImageModel)
        : undefined;
      if (!spec || spec.vpred || !(await exists(spec.file))) {
        throw new Error(
          "この画像モデルは、モーションコミックでは使えません（SDXL系で、V-pred ではないもの）",
        );
      }
    }
    for (const id of options.motionJoinJobIds ?? []) {
      const src = await getMediaJob(id);
      // A film still being made is fine: the join waits in the queue behind it.
      const waiting =
        src && (src.status === "queued" || src.status === "running") && src.kind === "motion";
      if (!src || (!waiting && (src.status !== "done" || !src.mime?.startsWith("video/"))))
        throw new Error("つなぐ動画は、完成した動画から選んでください");
    }
  }
  if (queue.length >= MAX_QUEUE)
    throw new Error("順番待ちがいっぱいです。少し待ってからもう一度どうぞ");
  const job: MediaJob = {
    id: randomBytes(6).toString("hex"),
    kind,
    status: "queued",
    request: clean,
    stage: working ? "順番待ち" : "準備中",
    createdAt: Date.now(),
    options,
    ...(origin.chat ? { chat: origin.chat } : {}),
  };
  job.estimateSec = defaultJobSec(kind, options);
  jobs.set(job.id, job);
  queue.push(job.id);
  void processQueue();
  return job;
}

export async function getMediaJob(id: string): Promise<MediaJob | undefined> {
  return jobs.get(id) ?? (await loadJobMeta(id));
}

/** The saved results of one kind (or all), newest first, with how many each kind has. */
export async function listMediaLibrary(
  kind: MediaKind | undefined,
  limit: number,
): Promise<{ jobs: MediaJob[]; counts: Record<MediaKind, number> }> {
  const all = await listMediaJobs(10_000);
  const counts: Record<MediaKind, number> = {
    image: 0,
    video: 0,
    music: 0,
    manga: 0,
    comic: 0,
    motion: 0,
    voice: 0,
    faceswap: 0,
  };
  for (const job of all) if (job.status === "done") counts[job.kind] += 1;
  return {
    jobs: (kind ? all.filter((job) => job.kind === kind) : all).slice(0, limit),
    counts,
  };
}

export async function listMediaJobs(limit = 30): Promise<MediaJob[]> {
  const byId = new Map<string, MediaJob>();
  try {
    for (const name of await fs.readdir(outDir())) {
      const id = /^([a-f0-9]{12})\.json$/.exec(name)?.[1];
      if (!id) continue;
      const job = await loadJobMeta(id);
      if (job) byId.set(job.id, job);
    }
  } catch {
    // No results yet.
  }
  for (const job of jobs.values()) byId.set(job.id, job);
  return [...byId.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, limit);
}

export async function readMediaResult(
  id: string,
  part = 0,
): Promise<{ mime: string; base64: string; parts?: number } | undefined> {
  const job = await getMediaJob(id);
  // A comic episode is several strips; part picks one of them.
  if (job?.files?.length && job.status === "done") {
    const file = job.files[Math.max(0, Math.min(job.files.length - 1, part))];
    if (!file) return undefined;
    return {
      mime: "image/png",
      base64: (await fs.readFile(file)).toString("base64"),
      parts: job.files.length,
    };
  }
  if (!job?.file || job.status !== "done" || !job.mime) return undefined;
  return { mime: job.mime, base64: (await fs.readFile(job.file)).toString("base64") };
}

/**
 * Deletes a job. A waiting job leaves the queue; a running one is stopped (its tool process tree,
 * or the image worker it draws with) and discarded by the runner; a finished one loses its files.
 */
export async function deleteMediaJob(id: string): Promise<boolean> {
  const job = await getMediaJob(id);
  if (!job) return false;
  if (job.status === "queued") {
    const index = queue.indexOf(id);
    if (index >= 0) queue.splice(index, 1);
    jobs.delete(id);
    return true;
  }
  if (job.status === "running") {
    job.cancelRequested = true;
    job.stage = "中止しています";
    await Promise.all([...activeChildren].map((child) => killProcessTree(child.pid)));
    if (job.kind === "image" || !job.options.sourceImageJobId) {
      // The still is drawn by the resident image worker; stopping it ends that request.
      const workers = imageWorkerChildren();
      stopImageWorker();
      await Promise.all(workers.map((worker) => killProcessTree(worker.pid)));
    }
    return true;
  }
  jobs.delete(id);
  await fs.rm(metaPath(id), { force: true });
  if (job.file) await fs.rm(job.file, { force: true });
  // A multi-page manga keeps one file per page.
  if (job.kind === "manga") {
    await Promise.all(
      (job.files ?? []).map((f) => fs.rm(f, { force: true }).catch(() => undefined)),
    );
    await removeBook(id);
  }
  if (job.kind === "comic" && job.comic?.episodeNo && !job.comic.targetJobId) {
    // The episode's panels and strips, and its entry in the series.
    await fs.rm(episodeDir(id), { recursive: true, force: true });
    const series = await getComicSeries(job.comic.seriesId);
    if (series) {
      series.episodes = series.episodes.filter((e) => e.jobId !== id);
      await saveComicSeries(series);
    }
  }
  return true;
}
