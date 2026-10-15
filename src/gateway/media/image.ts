import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ANIMA_NEGATIVE, renderAnima } from "../anima-comfy.js";
import { negativeTags } from "../blocked-terms-config.js";
import { runTagger } from "../character-creator.js";
import { getCharacter } from "../comic-characters.js";
import { probeMedia } from "../face-swap.js";
import { generationCudaDevice, generationCudaDevices, mediaProcessEnv } from "../media-gpu.js";
import { currentJob, getMediaJob, queue, qwenTurboSettings } from "../media-studio.js";
import { adultLead, adultSentence } from "../minor-safeguards.js";
import { upscaleWithSeedVr } from "../seedvr-comfy.js";
import {
  IMAGE_MODEL,
  isSdxlModel,
  isWanImageModel,
  sdxlModelOf,
  sdxlSampling,
  sdxlSpecOf,
  SIZES,
} from "./config.js";
import { acquireGpu } from "./gpu-lease.js";
import { exists, outDir, resultPath, sdCli } from "./paths.js";
import { markStage, runTool, stepNote } from "./runner.js";
import { assertAllowedRequest, imagePromptFor, isSexualRequest } from "./text.js";
import type { MediaJob, MediaOptions, WanImageModel } from "./types.js";
import { QWEN_TURBO_RUNNER, WAN2GP_DIR, WAN2GP_PYTHON, WAN2GP_RUNNER } from "./video.js";

// ---------- Resident SDXL worker (PyTorch) ----------
// diffusers on the GPU samples ~2.5x faster than sd.cpp, and keeping the process (weights in RAM)
// between images also skips ~15 s of start-up. It holds the CUDA context, so it is stopped before
// the GPU goes back to the chat model.

export const IMAGE_SERVER = "D:\\media-tools\\image\\sdxl_server.py";

/** After the queue drains, wait this long for another job before handing the GPU back. */
export const IMAGE_GRACE_MS = 45_000;

/**
 * One resident worker per GPU: slot 0 is the first GPU (the one every job uses); slot 1 is the
 * second GPU, started only when a job spreads its pictures over both (a multi-page manga).
 */
type ImageWorkerSlot = {
  child?: ReturnType<typeof spawn>;
  ready?: Promise<void>;
  device?: string;
};

export const imageSlots: ImageWorkerSlot[] = [{}, {}];

const imageRequests = new Map<
  string,
  { resolve: () => void; reject: (err: Error) => void; onStep: (step: string) => void }
>();

function startImageWorker(index = 0): Promise<void> {
  const slot = imageSlots[index] ?? imageSlots[0]!;
  const device = generationCudaDevices(currentJob?.options.gpuMode === "fast")[index];
  if (slot.child && slot.device !== device) stopImageWorker(slot);
  if (slot.child && slot.ready) return slot.ready;
  const child = spawn(WAN2GP_PYTHON, [IMAGE_SERVER], {
    cwd: path.dirname(IMAGE_SERVER),
    env: mediaProcessEnv({ PYTHONUTF8: "1", PYTHONIOENCODING: "utf-8" }, device),
    windowsHide: true,
    stdio: ["pipe", "pipe", "pipe"],
  });
  try {
    if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
  } catch {
    // Priority is best-effort.
  }
  slot.child = child;
  slot.device = device;
  let stderrTail = "";
  child.stderr?.on("data", (chunk) => {
    stderrTail = (stderrTail + String(chunk)).slice(-1_500);
  });
  slot.ready = new Promise<void>((resolve, reject) => {
    let buffer = "";
    child.stdout?.on("data", (chunk) => {
      buffer += String(chunk);
      let newline: number;
      while ((newline = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newline).trim();
        buffer = buffer.slice(newline + 1);
        if (line === "READY") {
          resolve();
          continue;
        }
        const match = /^(\S+) (STEP|RESULT|ERROR) ?(.*)$/.exec(line);
        const pending = match ? imageRequests.get(match[1] ?? "") : undefined;
        if (!match || !pending) continue;
        if (match[2] === "STEP") pending.onStep(match[3] ?? "");
        else {
          imageRequests.delete(match[1] ?? "");
          if (match[2] === "RESULT") pending.resolve();
          else pending.reject(new Error(match[3] || "image worker error"));
        }
      }
    });
    child.on("exit", (code) => {
      const error = new Error(`image worker exited (${code}): ${stderrTail.trim().slice(-300)}`);
      reject(error);
      for (const pending of imageRequests.values()) pending.reject(error);
      imageRequests.clear();
      if (slot.child === child) {
        slot.child = undefined;
        slot.ready = undefined;
      }
    });
    child.on("error", reject);
  });
  return slot.ready;
}

/** Stops one worker, or (no argument) every worker. */
export function stopImageWorker(only?: ImageWorkerSlot): void {
  for (const slot of only ? [only] : imageSlots) {
    const child = slot.child;
    slot.child = undefined;
    slot.ready = undefined;
    slot.device = undefined;
    if (!child) continue;
    child.stdin?.end();
    setTimeout(() => child.kill(), 10_000).unref();
  }
}

/** The processes of the resident workers (to stop them when a job is cancelled). */
export function imageWorkerChildren(): Array<ReturnType<typeof spawn>> {
  return imageSlots.flatMap((slot) => (slot.child ? [slot.child] : []));
}

type WorkerParams = {
  prompt: string;
  negative: string;
  width: number;
  height: number;
  output: string;
  /** Different people side by side: each region of the picture reads its own prompt. */
  regions?: Array<{ prompt: string; x0: number; x1: number }>;
  /** Character LoRAs, applied on top of the sampling LoRA (DMD2). */
  loras?: Array<{ path: string; scale: number }>;
  seed?: number;
};

async function renderWithImageWorker(job: MediaJob, params: WorkerParams, gpu = 0): Promise<void> {
  const slot = imageSlots[gpu] ?? imageSlots[0]!;
  if (gpu === 0) markStage(job, "画像の準備をしています");
  await startImageWorker(gpu);
  const id = randomBytes(4).toString("hex");
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      imageRequests.delete(id);
      reject(new Error("image worker timed out"));
    }, 10 * 60_000);
    imageRequests.set(id, {
      resolve: () => {
        clearTimeout(timer);
        resolve();
      },
      reject: (err) => {
        clearTimeout(timer);
        reject(err);
      },
      onStep: (step) => {
        if (gpu !== 0 && job.parallelPictures) return;
        const stage = `画像を描いています ${step}`;
        if (job.stage?.startsWith("画像を描いています")) job.stage = stage;
        else markStage(job, stage);
      },
    });
    const model = sdxlModelOf(job);
    const spec = sdxlSpecOf(model);
    const sampling = sdxlSampling(model, job.options.fast === true);
    slot.child?.stdin?.write(
      `${JSON.stringify({
        id,
        model: spec.file,
        vpred: spec.vpred,
        sampler: sampling.sampler,
        steps: sampling.steps,
        cfg: sampling.cfg,
        lora: sampling.lora ?? null,
        lora_scale: 1.0,
        seed: -1,
        ...params,
      })}\n`,
    );
  });
}

/** Keeps the GPU (and the warm image worker) for a short while in case another job follows. */
export async function waitForMoreJobs(ms: number): Promise<boolean> {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    if (queue.length > 0) return true;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return queue.length > 0;
}

/** Z-Image Turbo (6B, 8 steps) through the Wan2GP runner; it reads natural-language prompts. */
/** The Wan2GP settings of each still-image model (steps and guidance as the model was tuned). */
function wanImageSettings(model: WanImageModel): {
  label: string;
  settings: Record<string, unknown>;
} {
  switch (model) {
    case "klein":
      return {
        label: "FLUX.2 Klein 9B",
        settings: { model_type: "flux2_klein_9b", num_inference_steps: 4, guidance_scale: 1 },
      };
    case "qwenimg":
      return { label: "Qwen Image 2.1", settings: qwenTurboSettings() };
    case "qwenimgturbo":
      return {
        label: "Qwen Image 2.1 Turbo",
        settings: {
          model_type: "qwen_image_21_turbo",
          num_inference_steps: 8,
          guidance_scale: 1,
          sample_solver: "default",
          activated_loras: [],
          loras_multipliers: "",
          negative_prompt: "",
        },
      };
    default:
      // Nunchaku SVDQ INT4 (kernels in the Wan2GP venv): 49 s vs 65 s for the INT8 build, same look.
      return {
        label: "Z-Image",
        settings: {
          model_type: "z_image_nunchaku_r256_int4",
          num_inference_steps: 8,
          guidance_scale: 0,
        },
      };
  }
}

async function renderWithWan2gpImage(
  job: MediaJob,
  model: WanImageModel,
  prompt: string,
  file: string,
  aspect: NonNullable<MediaOptions["aspect"]> = job.options.aspect ?? "portrait",
): Promise<void> {
  const size = ZIMAGE_SIZES[aspect];
  if (model === "qwenimgturbo") {
    const [width, height] = size.split("x").map(Number);
    const workDir = path.join(outDir(), `${job.id}-qwen-turbo`);
    const request = path.join(workDir, "request.json");
    await fs.mkdir(workDir, { recursive: true });
    await fs.writeFile(
      request,
      JSON.stringify({
        output: file,
        settings: { prompt, width, height, seed: Math.floor(Math.random() * 2 ** 31) },
      }),
    );
    markStage(job, "画像を描いています（Qwen Image 2.1 Turbo・公式8ステップ）");
    await runTool(WAN2GP_PYTHON, [QWEN_TURBO_RUNNER, request], {
      cwd: path.dirname(QWEN_TURBO_RUNNER),
      timeoutMs: 2 * 60 * 60_000,
    });
    await fs.rm(workDir, { recursive: true, force: true });
    return;
  }
  const spec = wanImageSettings(model);
  const workDir = path.join(outDir(), `${job.id}-wanimg`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: workDir,
      settings: { ...spec.settings, prompt, resolution: size, seed: -1 },
    }),
  );
  markStage(job, `画像を描いています（${spec.label}。初回はモデルのダウンロードがあります）`);
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 2 * 60 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error(`${spec.label} が画像を出力しませんでした`);
  await fs.rename(image, file);
  await fs.rm(workDir, { recursive: true, force: true });
}

const ZIMAGE_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "1024x1024",
  portrait: "832x1216",
  landscape: "1216x832",
};

/**
 * The trained LoRAs of the characters picked from the library (characterLock), up to four. With
 * several characters each LoRA is weaker so the faces do not blend into one. Only the SDXL worker
 * can use them; the other image models describe the characters in words only.
 */
async function lockedCharacterLoras(job: MediaJob): Promise<NonNullable<WorkerParams["loras"]>> {
  const ids = job.options.characterLock?.characterIds ?? [];
  const paths: string[] = [];
  for (const id of ids) {
    const entry = await getCharacter(id);
    if (entry?.lora && entry.loraStatus === "ready" && (await exists(entry.lora)))
      paths.push(entry.lora);
  }
  const scale = paths.length > 1 ? 0.6 : 0.8;
  return paths.map((file) => ({ path: file, scale }));
}

export async function renderImage(
  job: MediaJob,
  tags: string,
  sexual: boolean,
  file: string,
  /** A manga panel picks its own shape and keeps text and frames out of the picture. */
  panel: {
    aspect?: NonNullable<MediaOptions["aspect"]>;
    negative?: string;
    regions?: WorkerParams["regions"];
    loras?: WorkerParams["loras"];
    seed?: number;
    /** Which GPU's resident worker draws it (0 = first, 1 = second). */
    gpu?: number;
  } = {},
): Promise<void> {
  const aspect = panel.aspect ?? job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].image;
  if (isWanImageModel(job.options.imageModel)) {
    const people = panel.regions?.map((r) => r.prompt).join(". ") ?? "";
    await renderWithWan2gpImage(
      job,
      job.options.imageModel,
      `${adultSentence(sexual)}${tags}${people ? `. ${people}` : ""}`,
      file,
      aspect,
    );
    return;
  }
  if (job.options.imageModel === "anima") {
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    const extraNegative = [
      negativeTags("imageNegative"),
      sexual ? negativeTags("adultOnly") : "",
      panel.negative ?? "",
    ]
      .filter(Boolean)
      .join(", ");
    await renderAnima(
      {
        positive: `${adultLead(sexual)}${tags}`,
        negative: [ANIMA_NEGATIVE, extraNegative].filter(Boolean).join(", "),
        width,
        height,
        seed: panel.seed ?? Math.floor(Math.random() * 2 ** 31),
        turbo: job.options.fast === true,
      },
      file,
      {
        onStage: (stage) => markStage(job, stage),
        isCancelled: () => job.cancelRequested === true,
        cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
      },
    );
    return;
  }
  const spec = sdxlSpecOf(sdxlModelOf(job));
  const negative = [
    spec.negative,
    negativeTags("imageNegative"),
    sexual ? negativeTags("adultOnly") : "",
    panel.negative ?? "",
  ]
    .filter(Boolean)
    .join(", ");
  const positive = `${spec.quality}, ${adultLead(sexual)}${tags}`;
  // The characters picked from the library bring their trained look (SDXL LoRAs) unless the panel has its own.
  const loras = panel.loras?.length ? panel.loras : await lockedCharacterLoras(job);
  if (await exists(IMAGE_SERVER)) {
    try {
      await renderWithImageWorker(
        job,
        {
          prompt: positive,
          negative,
          width,
          height,
          output: file,
          ...(panel.regions?.length ? { regions: panel.regions } : {}),
          ...(loras.length ? { loras } : {}),
          ...(panel.seed !== undefined ? { seed: panel.seed } : {}),
        },
        panel.gpu ?? 0,
      );
      return;
    } catch (err) {
      // Fall through to sd.cpp so a broken Python environment never blocks image jobs.
      if (job.cancelRequested) throw err;
      stopImageWorker(imageSlots[panel.gpu ?? 0]);
      markStage(job, `画像を描いています（予備の方式に切替: ${String(err).slice(0, 80)}）`);
    }
  }
  await runTool(
    sdCli(),
    [
      "-m",
      IMAGE_MODEL,
      "-p",
      positive,
      "-n",
      negative,
      "-W",
      String(width),
      "-H",
      String(height),
      "--steps",
      "28",
      "--cfg-scale",
      "6",
      "--sampling-method",
      "euler_a",
      "--diffusion-fa",
      "--vae-tiling",
      "-s",
      "-1",
      "-o",
      file,
    ],
    { onLine: stepNote("画像を描いています", job) },
  );
}

/** A finished picture made sharper and larger with SeedVR2 (ComfyUI is started for it and stopped after). */
async function runUpscaleJob(job: MediaJob): Promise<void> {
  const src = await getMediaJob(job.options.upscaleSourceJobId ?? "");
  if (
    !src ||
    src.status !== "done" ||
    !src.file ||
    !src.mime?.startsWith("image/") ||
    !(await exists(src.file))
  ) {
    throw new Error("高画質化する絵が見つかりません（完成した絵を選んでください）");
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const file = await resultPath(job, ".png");
  await upscaleWithSeedVr(
    src.file,
    file,
    {
      model: job.options.upscaleModel ?? "3b",
      resolution: Math.min(3072, Math.max(768, job.options.upscaleResolution ?? 1536)),
    },
    {
      onStage: (stage) => markStage(job, stage),
      isCancelled: () => job.cancelRequested === true,
      cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
    },
  );
  job.file = file;
  job.mime = "image/png";
}

const MEDIA_REPAIR_SCRIPT = "D:\\media-tools\\motion-comic\\mc_image.py";

/** Repaints one selected area and keeps every pixel outside it unchanged. */
async function runRepairJob(job: MediaJob): Promise<void> {
  const repair = job.options.repair;
  if (!repair) throw new Error("直す場所が指定されていません");
  const source = await getMediaJob(repair.sourceJobId);
  const sourceFile = source?.files?.[repair.part ?? 0] ?? source?.file;
  if (
    !source ||
    source.status !== "done" ||
    !sourceFile ||
    !(source.mime?.startsWith("image/") || source.files?.length) ||
    !(await exists(sourceFile))
  ) {
    throw new Error("部分修正する完成画像が見つかりません");
  }
  markStage(job, "元の絵を読み取っています");
  const detected = await runTagger(sourceFile).catch(() => undefined);
  const sourceTags = detected?.general
    .filter(([, score]) => score >= 0.35)
    .slice(0, 80)
    .map(([tag]) => tag)
    .join(", ");
  const requested = await imagePromptFor(job);
  const positive = [sourceTags, requested].filter(Boolean).join(", ");
  assertAllowedRequest(job.request, repair.instruction, positive);
  const probe = await probeMedia(sourceFile);
  const model = isSdxlModel(source.options.imageModel)
    ? source.options.imageModel
    : isSdxlModel(job.options.imageModel)
      ? job.options.imageModel
      : "wai";
  const spec = sdxlSpecOf(model);
  const work = path.join(outDir(), `${job.id}-repair`);
  await fs.mkdir(work, { recursive: true });
  const output = path.join(work, "repaired.png");
  const config = path.join(work, "repair.json");
  const { x, y, width, height } = repair.region;
  await fs.writeFile(
    config,
    JSON.stringify({
      model: spec.file,
      negative: [spec.negative, negativeTags("imageNegative")].filter(Boolean).join(", "),
      width: probe.width,
      height: probe.height,
      jobs: [
        {
          mode: "inpaint",
          src: sourceFile,
          mask: [x, y, x + width, y + height],
          prompt: `${spec.quality}, ${positive}`,
          strength: repair.strength,
          seed: Math.floor(Math.random() * 2 ** 31),
          out: output,
        },
      ],
    }),
  );
  markStage(job, "選んだ場所だけ直しています");
  await acquireGpu(job);
  stopImageWorker();
  await runTool(WAN2GP_PYTHON, [MEDIA_REPAIR_SCRIPT, config], {
    timeoutMs: 30 * 60_000,
  });
  if (!(await exists(output))) throw new Error("部分修正した画像ができませんでした");
  const file = await resultPath(job, ".png");
  await fs.copyFile(output, file);
  await fs.rm(work, { recursive: true, force: true }).catch(() => undefined);
  job.file = file;
  job.mime = "image/png";
}

export async function runImageJob(job: MediaJob): Promise<void> {
  if (job.options.repair) return runRepairJob(job);
  if (job.options.upscaleSourceJobId) return runUpscaleJob(job);
  markStage(job, "指示を英語のタグに変換しています");
  const tags = await imagePromptFor(job);
  assertAllowedRequest(job.request, tags);
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = await resultPath(job, ".png");
  await renderImage(job, tags, isSexualRequest(`${job.request} ${tags}`), file);
  job.file = file;
  job.mime = "image/png";
}
