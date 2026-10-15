import { allAvatarFrames, MINI_FRAMES } from "../avatar-frames.js";
import { DATASET_VIEWS } from "../comic-characters.js";
import { COMIC_PANELS_PER_EPISODE } from "../comic-series.js";
import { estimateSwap, probeMedia } from "../face-swap.js";
import {
  MANGA_DEFAULT_PAGES,
  MANGA_MAX_PAGES,
  MANGA_MIN_PAGES,
  MANGA_PANEL_COUNT,
} from "../manga-script.js";
import { generationCudaDevices, resolveGpuMode } from "../media-gpu.js";
import {
  jobs,
  joinEstimateSec,
  listMediaJobs,
  resolveSwapTarget,
  working,
} from "../media-studio.js";
import { isWanImageModel } from "./config.js";
import { exists } from "./paths.js";
import type { ImageModel, MediaKind, MediaOptions, VideoEngine } from "./types.js";

// ---------- Time estimates ----------

/**
 * Typical seconds for one job once the GPU is ready (RTX 4060, 2026-10-03 measurements and the
 * engine notes shown in the app). Real results of the same settings replace them.
 */
const BASE_IMAGE_SEC: Record<ImageModel, number> = {
  wai: 30,
  asianreal: 30,
  lustify: 30,
  cyberreal: 30,
  noobai: 60,
  zimage: 90,
  anima: 75,
  klein: 60,
  qwenimg: 80,
  qwenimgturbo: 45,
  hassaku: 30,
  illustrious: 30,
  novaanime: 30,
  plantmilk: 30,
  juggernaut: 30,
  realvis: 30,
  realismil: 30,
  illust2: 30,
  animagine: 30,
  rouwei: 30,
  chenkin: 30,
  janku: 30,
  ntrmix: 30,
  desire: 30,
  cyberanime: 30,
  cybersemi: 30,
  pencil: 30,
  cyberpony: 30,
};

const FAST_IMAGE_SEC = 15;

export const REALESRGAN_EXE = "D:\\media-tools\\upscale\\realesrgan-ncnn-vulkan.exe";

export const COMFY_PYTHON_PATH = "D:\\media-tools\\ComfyUI\\venv\\Scripts\\python.exe";

/** Video seconds of work per second of clip, and the clip length when none is chosen. */
const VIDEO_SEC_PER_CLIP_SEC: Record<VideoEngine, number> = {
  standard: 150,
  remix: 210,
  long: 150,
  audio: 90,
  fast: 40,
  h3: 110,
};

const DEFAULT_CLIP_SEC: Record<VideoEngine, number> = {
  standard: 2,
  remix: 2,
  long: 5,
  audio: 4,
  fast: 3,
  h3: 5,
};

const SMOOTH_SEC = 60;

/** Stopping the chat model and loading the generator when nothing is running yet. */
const COLD_START_SEC = 60;

/** Jobs with the same settings take about the same time; this key groups them. */
function estimateKey(kind: MediaKind, options: MediaOptions): string {
  const quality = options.qualityMode ?? "off";
  const gpu = resolveGpuMode(options.gpuMode);
  if (kind === "image")
    return `${gpu}|image|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}|${options.repair ? "repair" : ""}|${quality}`;
  if (kind === "music")
    return `${gpu}|music|${options.durationSec ?? 90}|${options.instrumental ? 1 : 0}`;
  if (kind === "voice") return `${gpu}|voice|${options.irodoriModel ?? "large"}`;
  if (kind === "faceswap")
    return `${gpu}|faceswap|${options.swapQuality ?? "standard"}|${options.swapEnhancer ? 1 : 0}`;
  if (kind === "motion") {
    if (options.motionRevoiceJobId)
      return `${gpu}|motion-revoice|${options.voiceId ?? "description"}`;
    if (options.motionJoinJobIds?.length)
      return `${gpu}|motion-join|${options.motionJoinJobIds.length}|${options.motionBridgeSeconds ?? 3}`;
    return `${gpu}|motion|${options.motionMinutes ?? 1}|${options.motionFellatio ? 1 : 0}|${options.motionPreview === false ? "" : "p"}`;
  }
  if (kind === "comic") {
    return `${gpu}|comic|${options.comicTask ?? "episode"}|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}${options.sampleKeys?.length ? `|batch${options.sampleKeys.length}` : ""}`;
  }
  if (kind === "manga") {
    return `${gpu}|manga|${options.mangaLayout ?? "4koma"}|${options.mangaPages ?? ""}|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}`;
  }
  const engine = options.engine ?? "standard";
  const clip = options.durationSec ?? DEFAULT_CLIP_SEC[engine];
  const source = options.sourceImageJobId || options.sourceImageBase64 ? "src" : "";
  return `${gpu}|video|${engine}|${clip}|${options.smooth ? "smooth" : ""}|${source}`;
}

function baseJobSec(kind: MediaKind, options: MediaOptions): number {
  // Anima starts ComfyUI for every picture: 45 s measured with the distilled model, 75 s without it.
  const still =
    options.imageModel === "anima" && options.fast
      ? 45
      : options.fast && !isWanImageModel(options.imageModel) && options.imageModel !== "noobai"
        ? FAST_IMAGE_SEC
        : (BASE_IMAGE_SEC[options.imageModel ?? "wai"] ?? 30);
  if (kind === "image" && options.repair) return 240;
  if (kind === "image" && options.upscaleSourceJobId)
    return options.upscaleModel === "7b" ? 150 : 70;
  if (kind === "image") return still;
  if (kind === "music") return 30 + (options.durationSec ?? 90);
  // Script by the chat model, every panel drawn, then the page put together.
  if (kind === "manga" && options.mangaLayout === "book") {
    const pages = Math.max(
      MANGA_MIN_PAGES,
      Math.min(MANGA_MAX_PAGES, options.mangaPages ?? MANGA_DEFAULT_PAGES),
    );
    // Outline, then about 40 s of script per page, then about 5 panels a page (and a cover) drawn
    // on one or two GPUs, and the typesetting.
    const lanes = generationCudaDevices(options.gpuMode === "fast").length >= 2 ? 2 : 1;
    const pictures = pages * MANGA_PANEL_COUNT.book + (options.mangaCover === false ? 0 : 1);
    return 90 + pages * 40 + Math.ceil((pictures * still) / lanes) + 20;
  }
  if (kind === "manga") return 60 + still * MANGA_PANEL_COUNT[options.mangaLayout ?? "4koma"] + 10;
  // Model load, then a few seconds per line.
  if (kind === "voice" && options.voiceDrama) {
    const lines = options.voiceDrama.minutes * 7;
    return (
      120 +
      Math.ceil(lines / 24) * 90 +
      lines * (options.irodoriModel === "large" ? 14 : options.irodoriModel === "small" ? 5 : 1.5)
    );
  }
  if (kind === "voice")
    return options.irodoriModel === "fast" ? 15 : options.irodoriModel === "small" ? 25 : 70;
  // The real figure depends on the video; media.estimate computes it from the file.
  if (kind === "faceswap") return 600;
  // Script, ~12 voice lines (large), 3-5 pictures, then 6-8 cuts of ~6 min (shared by two GPUs) and the film.
  if (kind === "motion") {
    if (options.motionRevoiceJobId) return 120 + 12 * 45 + 120;
    if (options.motionJoinJobIds?.length)
      return joinEstimateSec(options.motionJoinJobIds.length, options.motionBridgeSeconds ?? 3);
    // A cut takes about 350 s (640x368 clip ~326 s + Real-ESRGAN ~25 s); the two GPUs share the cuts.
    return 120 + 12 * 45 + 5 * 40 + (options.motionFellatio ? 8 : 6) * 350 + 120;
  }
  if (kind === "comic") {
    // Outline and script by the chat model (~3 min), panels (two-person ones take longer), strips.
    if (options.comicTask === "sheet") return still;
    if (options.comicTask === "outpaint") return 200;
    // Qwen Image 2.1 edit of the standing picture (about 2.5 minutes with the start-up).
    if (options.comicTask === "sample" && options.sampleKeys?.length)
      return 120 + options.sampleKeys.length * (options.sampleEngine === "precise" ? 200 : 55);
    if (options.comicTask === "sample")
      return options.sampleEngine === "precise"
        ? 360
        : options.sampleEngine === "edit2509"
          ? 240
          : 150;
    if (options.comicTask === "avatar") return 120 + 22 * allAvatarFrames().length;
    if (options.comicTask === "mini") return 120 + 25 * MINI_FRAMES.length;
    if (options.comicTask === "model3d") return 420;
    if (options.comicTask === "redraw") return still * 1.5 + 20;
    // Qwen Image Edit makes ~23 pictures (~25 s each after loading), then ~1,100 training steps.
    if (options.comicTask === "lora")
      return 120 + 25 * DATASET_VIEWS.length + 2.2 * (DATASET_VIEWS.length + 1) * 8 * 6;
    return 180 + still * 1.3 * COMIC_PANELS_PER_EPISODE + 30;
  }
  const engine = options.engine ?? "standard";
  const clip = options.durationSec ?? DEFAULT_CLIP_SEC[engine];
  const drawsStill = !(options.sourceImageJobId || options.sourceImageBase64);
  return (
    VIDEO_SEC_PER_CLIP_SEC[engine] * clip +
    (options.smooth ? SMOOTH_SEC : 0) +
    (drawsStill ? still : 0)
  );
}

export function defaultJobSec(kind: MediaKind, options: MediaOptions): number {
  const base = baseJobSec(kind, options);
  const mode = options.qualityMode;
  if (!mode || mode === "off") return base;
  const finish =
    kind === "image"
      ? mode === "max"
        ? 150
        : 70
      : kind === "manga" || kind === "comic"
        ? mode === "max"
          ? 420
          : 210
        : kind === "voice" || kind === "music"
          ? 30
          : mode === "max"
            ? 300
            : 150;
  return base + finish;
}

function median(values: number[]): number {
  const sorted = values.toSorted((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)] ?? 0;
}

export type MediaEstimate = {
  /** This job alone. */
  jobSec: number;
  /** Jobs ahead of it: the rest of the running one plus the queued ones. */
  waitSec: number;
  totalSec: number;
  /** How many finished jobs with the same settings the estimate is based on (0 = typical values). */
  samples: number;
};

/** Roughly how long a new job would take, including the queue in front of it. */
export async function estimateMediaJob(
  kind: MediaKind,
  options: MediaOptions,
): Promise<MediaEstimate> {
  const history = (await listMediaJobs(10_000)).filter(
    (job) => job.status === "done" && (job.durationSec ?? 0) > 0,
  );
  const estimateOf = (jobKind: MediaKind, jobOptions: MediaOptions) => {
    const key = estimateKey(jobKind, jobOptions);
    const same = history
      .filter((job) => estimateKey(job.kind, job.options) === key)
      .slice(0, 5)
      .map((job) => job.durationSec ?? 0);
    return same.length > 0
      ? { sec: median(same), samples: same.length }
      : { sec: defaultJobSec(jobKind, jobOptions), samples: 0 };
  };
  const own = estimateOf(kind, options);
  let waitSec = 0;
  for (const job of jobs.values()) {
    if (job.status === "running") {
      const elapsed = Math.round((Date.now() - (job.startedAt ?? Date.now())) / 1000);
      waitSec += Math.max(30, estimateOf(job.kind, job.options).sec - elapsed);
    } else if (job.status === "queued") {
      waitSec += estimateOf(job.kind, job.options).sec;
    }
  }
  // Real results already include the start-up; typical values need it when nothing is running.
  const coldStart = !working && own.samples === 0 ? COLD_START_SEC : 0;
  const jobSec = own.sec + coldStart;
  return { jobSec, waitSec, totalSec: jobSec + waitSec, samples: own.samples };
}

export type FaceSwapEstimate = MediaEstimate & {
  frames: number;
  passes: number;
  /** Seconds per frame per person that the estimate uses. */
  secPerFrame: number;
  /** True when the speed comes from real earlier jobs. */
  measured: boolean;
  videoSec: number;
};

/** How long a face swap of this file would take: its frames × the speed, plus the queue in front. */
export async function estimateFaceSwapJob(options: MediaOptions): Promise<FaceSwapEstimate> {
  const target = resolveSwapTarget(options);
  if (!target || !(await exists(target))) throw new Error("入れ替える動画か画像が見つかりません");
  const probe = await probeMedia(target);
  const key = estimateKey("faceswap", options);
  const speeds = (await listMediaJobs(10_000))
    .filter(
      (job) =>
        job.kind === "faceswap" &&
        job.status === "done" &&
        (job.timings?.spf ?? 0) > 0 &&
        estimateKey(job.kind, job.options) === key,
    )
    .slice(0, 5)
    .map((job) => (job.timings?.spf ?? 0) / 1000);
  const own = estimateSwap(probe, options, speeds.length > 0 ? median(speeds) : undefined);
  const queued = await estimateMediaJob("faceswap", options);
  const coldStart = !working ? COLD_START_SEC : 0;
  const jobSec = own.jobSec + coldStart;
  return {
    jobSec,
    waitSec: queued.waitSec,
    totalSec: jobSec + queued.waitSec,
    samples: speeds.length,
    frames: own.frames,
    passes: own.passes,
    secPerFrame: own.secPerFrame,
    measured: own.measured,
    videoSec: Math.round(probe.durationSec),
  };
}

// ─── BGM自動選択ヘルパー ────────────────────────────────────────────────────

/**
 * モーションコミックのシチュエーションに合ったBGMジャンルを返す。
 * 実際のBGM生成は ACE-Step / Suno API を呼ぶ予定。現時点はプロンプト文字列のみ返す。
 */
export function selectBgmPrompt(templateId: string): string {
  const BGM_MAP: Record<string, string> = {
    missionary: "slow romantic piano, soft strings, intimate bedroom ambiance",
    cowgirl: "upbeat sensual jazz, medium tempo, piano and bass",
    doggystyle: "driving rhythm, dark electronic beat, tension building",
    bath: "ambient spa music, water sounds, gentle synth pads",
    rape_scene: "dark ambient, tense strings, heavy atmosphere",
    orgy_scene: "club music, heavy bass, electronic dance beat",
    outdoor_sex: "nature ambiance, birds, light breeze, acoustic guitar",
    sugar_daddy_scene: "smooth jazz, upscale lounge, piano",
    default: "soft ambient music, subtle strings, intimate mood",
  };
  return BGM_MAP[templateId] ?? BGM_MAP.default ?? "";
}

// ─── IP-Adapter 顔合わせヘルパー ─────────────────────────────────────────────

/**
 * useIpAdapter フラグが立っているキャラクターの場合、
 * 設定画（sheetJobId）を参照画像としてプロンプトに付加する。
 * 実際のIP-Adapter呼び出しは img2img パスで行い、strength を低めに設定する。
 */
export function buildIpAdapterOptions(
  heroine: { useIpAdapter?: boolean; sheetJobId?: string } | undefined,
): {
  ipAdapterEnabled: boolean;
  referenceJobId?: string;
} {
  if (!heroine?.useIpAdapter || !heroine?.sheetJobId) {
    return { ipAdapterEnabled: false };
  }
  return {
    ipAdapterEnabled: true,
    referenceJobId: heroine.sheetJobId,
  };
}
