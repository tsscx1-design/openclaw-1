import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { ANIMA_FILES } from "./anima-comfy.js";
import { getCharacter } from "./comic-characters.js";
import { getComicSeries, saveComicSeries } from "./comic-series.js";
import { FACEFUSION_PYTHON } from "./face-swap.js";
import { type GenStyle, readGenStyle } from "./gen-style.js";
import { irodoriInstalled } from "./irodori-tts.js";
import { waitForChatTurnEnd } from "./main-llm-idle.js";
import { removeBook } from "./manga-book-store.js";
import { resolveGpuMode } from "./media-gpu.js";
import { SD_SCRIPTS_PYTHON } from "./media/character-lora.js";
import { episodeDir, runComicJob } from "./media/comic.js";
import {
  DMD2_LORA,
  isSdxlModel,
  isWanImageModel,
  KEEP_FAILED,
  MAX_QUEUE,
  REMIX_HIGH,
  REMIX_LOW,
  RIFE_SCRIPT,
  SDXL_MODELS,
  sdxlSpecOf,
} from "./media/config.js";
import { COMFY_PYTHON_PATH, defaultJobSec } from "./media/estimates.js";
import { resolveSwapTarget, runFaceSwapJob } from "./media/faceswap.js";
import { releaseGpu } from "./media/gpu-lease.js";
import {
  IMAGE_GRACE_MS,
  imageSlots,
  imageWorkerChildren,
  runImageJob,
  stopImageWorker,
  waitForMoreJobs,
} from "./media/image.js";
import { runMangaJob } from "./media/manga.js";
import { MOTION_SCRIPT, runMotionJob } from "./media/motion-comic.js";
import { runMusicJob } from "./media/music.js";
import { exists, outDir, pauseFlag } from "./media/paths.js";
import { runQualityPass } from "./media/quality.js";
import { activeChildren, killProcessTree, markStage } from "./media/runner.js";
import { mediaStudioStatus } from "./media/status.js";
import { assertAllowedRequest, sanitizeMediaText } from "./media/text.js";
import type { ImageModel, MediaJob, MediaKind, MediaOptions, SdxlModel } from "./media/types.js";
import { QWEN_TURBO_RUNNER, runVideoJob } from "./media/video.js";
import { runVoiceJob, sanitizeVoiceScript } from "./media/voice-drama.js";
import { isStoreModelId, storeSpecOf } from "./model-store.js";
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
export {
  comicSheetViewOf,
  FACE_ONLY_PROPORTIONS,
  hairColorOf,
  multiPhotoPrompt,
  nsfwLook,
  photoIdentityPrompt,
  photoStylePrompt,
  randomBodyPrompt,
} from "./media/character-sheet.js";
export {
  adoptLatestSheet,
  avatarDir,
  makeCharacterIcon,
  miniDir,
} from "./media/character-avatar.js";

// Local image / video / music generation on the RTX 4060 (8 GB).
// The main chat LLM holds almost all VRAM, so a job stops it (the watchdog pause flag keeps it
// down) and restarts it once the queue is empty. Jobs run one at a time.
//   image: stable-diffusion.cpp (CUDA) + WAI-Illustrious SDXL (uncensored anime)
//   video: stable-diffusion.cpp vid_gen + Wan 2.2 14B Rapid AIO NSFW (image-to-video, 4 steps);
//          text-to-video renders a still with the image model first, then animates it
//   music: acestep.cpp (ace-lm + ace-synth) + ACE-Step 1.5 turbo; lyrics are written by its LM

/** The job being worked on and the tool process it runs; a delete stops them. */
export let currentJob: MediaJob | undefined;

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
