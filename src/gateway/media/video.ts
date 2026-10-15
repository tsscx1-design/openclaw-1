import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  REMIX_HIGH,
  REMIX_LOW,
  RIFE_SCRIPT,
  SIZES,
  VIDEO_CLIP_VISION,
  VIDEO_MODEL,
  VIDEO_T5,
  VIDEO_VAE,
  videoNegative,
  WAN2GP_DIR,
  WAN2GP_PYTHON,
  WAN2GP_RUNNER,
} from "./config.js";
import { acquireGpu, stopCpuWorkers } from "./gpu-lease.js";
import { renderImage } from "./image.js";
import { outDir, resultPath, sdCli } from "./paths.js";
import { markStage, runTool, stepNote } from "./runner.js";
import { jobs, loadJobMeta } from "./state.js";
import {
  assertAllowedRequest,
  imagePromptFor,
  isSexualRequest,
  productionRequest,
  toAudioVideoPrompt,
  toH3Prompt,
  toMotionPrompt,
} from "./text.js";
import type { MediaJob, MediaOptions, SdcppVideoEngine, VideoEngine } from "./types.js";

/** LTX-2.3 ("audio") and H3 also need the soundtrack described; the other engines only motion. */
async function motionPromptFor(job: MediaJob): Promise<string> {
  const request = await productionRequest(job);
  if (job.options.engine === "h3") return toH3Prompt(request, job.options.durationSec ?? 5);
  return job.options.engine === "audio" ? toAudioVideoPrompt(request) : toMotionPrompt(request);
}

export async function runVideoJob(job: MediaJob): Promise<void> {
  const aspect = job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].video;
  const production = await productionRequest(job);
  let initImage: string;
  let motion: string | undefined;
  let sexual = isSexualRequest(production);
  if (job.options.sourceImageJobId) {
    const source =
      jobs.get(job.options.sourceImageJobId) ?? (await loadJobMeta(job.options.sourceImageJobId));
    if (!source?.file || source.kind !== "image") throw new Error("元の画像が見つかりません");
    initImage = source.file;
    sexual ||= isSexualRequest(source.request);
    markStage(job, "動きの指示を英語に変換しています");
    assertAllowedRequest(job.request, source.request);
  } else if (job.options.sourceImageBase64) {
    initImage = path.join(outDir(), `${job.id}-source.png`);
    await fs.writeFile(initImage, Buffer.from(job.options.sourceImageBase64, "base64"));
    assertAllowedRequest(job.request);
  } else {
    // Text-to-video: draw the first frame with the image model, then animate it.
    markStage(job, "指示を英語のタグに変換しています");
    const tags = await imagePromptFor(job);
    assertAllowedRequest(job.request, tags);
    sexual ||= isSexualRequest(tags);
    // Written before the GPU is taken, while the chat model can still do it quickly.
    motion = await motionPromptFor(job);
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    initImage = path.join(outDir(), `${job.id}-first.png`);
    await renderImage(job, tags, sexual, initImage);
  }
  const engine = job.options.engine ?? "standard";
  motion ??= await motionPromptFor(job);
  assertAllowedRequest(job.request, motion);
  if (engine !== "standard" && engine !== "remix") {
    await runWan2gpVideo(job, engine, initImage, motion, sexual, aspect);
    return;
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = await resultPath(job, ".webm");
  await runTool(
    sdCli(),
    sdcppVideoArgs(engine, {
      initImage,
      prompt: motion,
      negative: videoNegative(sexual),
      width,
      height,
      output: file,
    }),
    { onLine: stepNote("動画を作っています", job) },
  );
  job.file = file;
  job.mime = "video/webm";
  if (job.options.smooth) await smoothVideo(job);
}

/** sd.cpp arguments for a 2 s (33 frames, 16 fps) image-to-video clip. */
export function sdcppVideoArgs(
  engine: SdcppVideoEngine,
  params: {
    initImage: string;
    prompt: string;
    negative: string;
    width: number;
    height: number;
    output: string;
  },
): string[] {
  const model =
    engine === "remix"
      ? [
          "--diffusion-model",
          REMIX_LOW,
          "--high-noise-diffusion-model",
          REMIX_HIGH,
          "--steps",
          "6",
          "--high-noise-steps",
          "3",
          "--cfg-scale",
          "1",
          "--high-noise-cfg-scale",
          "1",
          "--sampling-method",
          "euler",
          "--high-noise-sampling-method",
          "euler",
          "--flow-shift",
          "5",
        ]
      : [
          "--diffusion-model",
          VIDEO_MODEL,
          "--clip_vision",
          VIDEO_CLIP_VISION,
          "--steps",
          "4",
          "--cfg-scale",
          "1",
          "--sampling-method",
          "euler_a",
        ];
  return [
    "-M",
    "vid_gen",
    ...model,
    "--vae",
    VIDEO_VAE,
    // The text encoder runs on the GPU too (offloaded between stages): 17 s instead of 31 s on CPU.
    "--t5xxl",
    VIDEO_T5,
    "-i",
    params.initImage,
    "-p",
    params.prompt,
    "-n",
    params.negative,
    "-W",
    String(params.width),
    "-H",
    String(params.height),
    "--video-frames",
    "33",
    "--fps",
    "16",
    "--offload-to-cpu",
    "--diffusion-fa",
    // Tiles above the 256 px default need >7 GB for the Wan VAE on this card.
    "--vae-tiling",
    "-s",
    "-1",
    "-o",
    params.output,
  ];
}

/** Doubles the frame rate with RIFE (16 -> 32 fps); the clip keeps its length and motion. */
async function smoothVideo(job: MediaJob): Promise<void> {
  if (!job.file) return;
  markStage(job, "なめらかにしています");
  const smoothed = path.join(outDir(), `${job.id}-smooth.mp4`);
  await runTool(WAN2GP_PYTHON, [RIFE_SCRIPT, job.file, smoothed, "2"], {
    cwd: path.dirname(RIFE_SCRIPT),
    timeoutMs: 10 * 60_000,
  });
  const file = await resultPath(job, ".mp4");
  await fs.rename(smoothed, file);
  await fs.rm(job.file, { force: true });
  job.file = file;
  job.mime = "video/mp4";
}

// Wan2GP engines run headless through its Python API (openclaw_job.py); nothing is served on the network.
const WAN2GP_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "624x624",
  portrait: "480x832",
  landscape: "832x480",
};

const WAN2GP_ENGINES: Record<
  Exclude<VideoEngine, SdcppVideoEngine>,
  {
    modelType: string;
    fps: number;
    frameStep: number;
    maxSec: number;
    label: string;
    /** Shortest clip the model makes; lengths grow from it in frameStep increments. */
    minFrames?: number;
    /** Needs most of the 64 GB RAM, so the CPU worker LLMs are stopped first. */
    freesRam?: boolean;
  }
> = {
  long: {
    modelType: "i2v_2_2_Enhanced_Lightning_v2_svi2pro",
    fps: 16,
    frameStep: 4,
    maxSec: 30,
    label: "長い動画",
  },
  audio: {
    modelType: "ltx2_distilled_gguf_q4_k_m",
    fps: 24,
    frameStep: 8,
    maxSec: 10,
    label: "音付き動画",
  },
  fast: { modelType: "ti2v_2_2_fastwan", fps: 24, frameStep: 4, maxSec: 5, label: "高速動画" },
  // MiniMax H3 FL2VA pruned 20B with the 8-step PDD heads: video and sound together; ~45 GB of
  // weights stream from RAM (Wan2GP profile 4). 5 s at 480x832 took ~9 min on the 4060.
  h3: {
    modelType: "minimax_h3_fl2va_pruned_pdd",
    fps: 24,
    frameStep: 17,
    minFrames: 107,
    maxSec: 10,
    label: "音付き実写",
    freesRam: true,
  },
};

/** Frame count the model accepts: 1 + a multiple of its frame step, or minFrames + a multiple. */
export function wan2gpFrames(
  seconds: number,
  fps: number,
  frameStep: number,
  maxSec: number,
  minFrames?: number,
): number {
  const clamped = Math.min(maxSec, Math.max(2, seconds));
  if (minFrames)
    return minFrames + frameStep * Math.max(0, Math.ceil((clamped * fps - minFrames) / frameStep));
  return Math.ceil((clamped * fps) / frameStep) * frameStep + 1;
}

async function runWan2gpVideo(
  job: MediaJob,
  engine: Exclude<VideoEngine, SdcppVideoEngine>,
  initImage: string,
  prompt: string,
  sexual: boolean,
  aspect: NonNullable<MediaOptions["aspect"]>,
): Promise<void> {
  const spec = WAN2GP_ENGINES[engine];
  const workDir = path.join(outDir(), `${job.id}-w2g`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: workDir,
      settings: {
        model_type: spec.modelType,
        prompt,
        negative_prompt: videoNegative(sexual),
        image_prompt_type: "S",
        image_start: initImage,
        resolution: WAN2GP_SIZES[aspect],
        video_length: wan2gpFrames(
          job.options.durationSec ?? 5,
          spec.fps,
          spec.frameStep,
          spec.maxSec,
          spec.minFrames,
        ),
        seed: -1,
        // Wan2GP's own RIFE pass keeps the soundtrack of the audio engine in sync.
        ...(job.options.smooth ? { temporal_upsampling: "rife2" } : {}),
      },
    }),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  if (spec.freesRam) await stopCpuWorkers();
  markStage(job, `${spec.label}を作っています（初回はモデルのダウンロードに時間がかかります）`);
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    // The first run of an engine downloads tens of GB of weights.
    timeoutMs: 3 * 60 * 60_000,
    onLine: (line) => {
      const progress = /^PROGRESS (\d+) (.*)$/.exec(line);
      if (progress) job.stage = `${spec.label}を作っています ${progress[1]}%`;
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const video = produced.find((file) => /\.(mp4|webm|mkv)$/i.test(file));
  if (!video) throw new Error("Wan2GP が動画を出力しませんでした");
  const ext = path.extname(video).toLowerCase();
  const file = await resultPath(job, ext);
  await fs.rename(video, file);
  await fs.rm(workDir, { recursive: true, force: true });
  job.file = file;
  job.mime = ext === ".webm" ? "video/webm" : ext === ".mkv" ? "video/x-matroska" : "video/mp4";
}
