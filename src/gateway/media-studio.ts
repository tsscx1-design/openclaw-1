import { execFile, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";
import {
  SPECIALIST_WORKERS,
  startSpecialistWorker,
  waitForWorkerReady,
} from "./specialist-router.js";

// Local image / video / music generation on the RTX 4060 (8 GB).
// The main chat LLM holds almost all VRAM, so a job stops it (the watchdog pause flag keeps it
// down) and restarts it once the queue is empty. Jobs run one at a time.
//   image: stable-diffusion.cpp (CUDA) + WAI-Illustrious SDXL (uncensored anime)
//   video: stable-diffusion.cpp vid_gen + Wan 2.2 14B Rapid AIO NSFW (image-to-video, 4 steps);
//          text-to-video renders a still with the image model first, then animates it
//   music: acestep.cpp (ace-lm + ace-synth) + ACE-Step 1.5 turbo; lyrics are written by its LM

export type MediaKind = "image" | "video" | "music";
export type MediaJobStatus = "queued" | "running" | "done" | "error";

export type MediaJob = {
  id: string;
  kind: MediaKind;
  status: MediaJobStatus;
  /** What the user asked for, as typed. */
  request: string;
  /** Short Japanese progress note for the app. */
  stage?: string;
  createdAt: number;
  startedAt?: number;
  finishedAt?: number;
  durationSec?: number;
  error?: string;
  /** Milliseconds per stage, kept with the result to diagnose slow runs. */
  timings?: Record<string, number>;
  file?: string;
  mime?: string;
  options: MediaOptions;
};

export type MediaOptions = {
  /** image/video framing */
  aspect?: "square" | "portrait" | "landscape";
  /** video: animate this finished image job instead of drawing a new still */
  sourceImageJobId?: string;
  /** video: base64 image supplied by the app */
  sourceImageBase64?: string;
  /**
   * video engine: "standard" = sd.cpp + Wan 2.2 14B Rapid NSFW (2 s); the others run in Wan2GP:
   * "long" = Wan 2.2 SVI 2 Pro Lightning (sliding windows), "audio" = LTX-2 distilled with sound,
   * "fast" = FastWan 2.2 5B (3 steps).
   */
  engine?: VideoEngine;
  /** image model for images and for the first frame of text-to-video */
  imageModel?: ImageModel;
  /** music, and video length for the Wan2GP engines */
  lyrics?: string;
  instrumental?: boolean;
  durationSec?: number;
};

export type VideoEngine = "standard" | "long" | "audio" | "fast";
export type ImageModel = "wai" | "noobai" | "zimage";

const MEDIA_MODEL_DIR = "D:\\media-models";
const IMAGE_MODEL = path.join(MEDIA_MODEL_DIR, "image", "waiIllustriousSDXL_v170.safetensors");

/** SDXL checkpoints served by the resident worker; Z-Image runs in Wan2GP instead. */
const SDXL_MODELS: Record<
  Exclude<ImageModel, "zimage">,
  { file: string; vpred: boolean; steps: number; cfg: number; quality: string; negative: string }
> = {
  wai: {
    file: IMAGE_MODEL,
    vpred: false,
    steps: 28,
    cfg: 6,
    quality: "masterpiece, best quality, amazing quality, very aesthetic, absurdres",
    negative:
      "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
  // NoobAI-XL V-Pred 1.0: recommended Euler with CFG 3.5-5.5 and 28-35 steps.
  noobai: {
    file: path.join(MEDIA_MODEL_DIR, "image", "NoobAI-XL-Vpred-v1.0.safetensors"),
    vpred: true,
    steps: 30,
    cfg: 4.5,
    quality: "masterpiece, best quality, newest, absurdres, highres",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
};
const VIDEO_DIR = path.join(MEDIA_MODEL_DIR, "video");
const VIDEO_MODEL = path.join(VIDEO_DIR, "wan2.2-i2v-rapid-aio-v10-nsfw-Q3_K_S.gguf");
const VIDEO_VAE = path.join(VIDEO_DIR, "wan_2.1_vae.safetensors");
const VIDEO_T5 = path.join(VIDEO_DIR, "umt5-xxl-encoder-Q8_0.gguf");
const VIDEO_CLIP_VISION = path.join(VIDEO_DIR, "clip_vision_h.safetensors");
const MUSIC_MODEL_DIR = path.join(MEDIA_MODEL_DIR, "music");
// acestep.cpp looks models up by full file name, .gguf included.
const MUSIC_LM = "acestep-5Hz-lm-4B-Q8_0.gguf";
const MUSIC_DIT = "acestep-v15-turbo-Q8_0.gguf";

const JOB_TIMEOUT_MS = 40 * 60_000;
const MAX_QUEUE = 4;
const KEEP_RESULTS = 60;
const MAX_TEXT = 500;

const VIDEO_NEGATIVE =
  "static, still image, blurry, low quality, distorted, deformed, extra limbs, flicker, watermark, text";
// Added whenever a request is sexual, on top of refusing sexual requests that mention minors.
const ADULT_ONLY_NEGATIVE = "child, loli, shota, underage, young, petite, flat chest, kid";

const SIZES: Record<
  NonNullable<MediaOptions["aspect"]>,
  { image: [number, number]; video: [number, number] }
> = {
  square: { image: [1024, 1024], video: [624, 624] },
  // SDXL buckets with the same shape as the video frame, so a first frame needs no crop
  portrait: { image: [768, 1344], video: [480, 832] },
  landscape: { image: [1344, 768], video: [832, 480] },
};

// ---------- Paths ----------

function home(): string {
  return resolveOpenClawHome();
}
function sdCli(): string {
  return path.join(home(), "image", "bin-cuda", "sd-cli.exe");
}
function aceBin(name: "ace-lm" | "ace-synth"): string {
  return path.join(home(), "music", "src", "build", "Release", `${name}.exe`);
}
function outDir(): string {
  return path.join(home(), "media", "out");
}
function pauseFlag(): string {
  return path.join(home(), "bonsai", "llm-pause.flag");
}
function lastMainCmdline(): string {
  return path.join(home(), "bonsai", "last_main_cmdline.txt");
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function mediaStudioStatus(): Promise<{
  image: { ready: boolean; missing: string[] };
  video: { ready: boolean; missing: string[] };
  wan2gp: { ready: boolean; missing: string[] };
  music: { ready: boolean; missing: string[] };
  busy: boolean;
  queued: number;
}> {
  const check = async (files: string[]) => {
    const missing: string[] = [];
    for (const file of files) if (!(await exists(file))) missing.push(path.basename(file));
    return { ready: missing.length === 0, missing };
  };
  return {
    image: await check([sdCli(), IMAGE_MODEL]),
    video: await check([sdCli(), IMAGE_MODEL, VIDEO_MODEL, VIDEO_VAE, VIDEO_T5, VIDEO_CLIP_VISION]),
    // Wan2GP engines download their models on first use, so only the runner is checked here.
    wan2gp: await check([WAN2GP_PYTHON, WAN2GP_RUNNER]),
    music: await check([
      aceBin("ace-lm"),
      aceBin("ace-synth"),
      path.join(MUSIC_MODEL_DIR, MUSIC_LM),
      path.join(MUSIC_MODEL_DIR, MUSIC_DIT),
      path.join(MUSIC_MODEL_DIR, "Qwen3-Embedding-0.6B-Q8_0.gguf"),
      path.join(MUSIC_MODEL_DIR, "vae-BF16.gguf"),
    ]),
    busy: working,
    queued: queue.length,
  };
}

// ---------- Safety ----------

const MINOR_TERMS =
  /\b(child|children|kid|kids|loli|lolicon|shota|shotacon|toddler|infant|baby|underage|minor|preteen|pre-teen|elementary school|middle school|grade schooler)\b|子供|子ども|こども|幼女|幼児|児童|小学生|中学生|ロリ|ショタ|園児|赤ちゃん|幼い/i;
const SEXUAL_TERMS =
  /\b(nsfw|nude|nudity|naked|sex|sexual|porn|explicit|hentai|erotic|nipples?|genitals?|pussy|penis|breasts?|underwear|panties|lingerie|topless|bottomless)\b|裸|全裸|ヌード|エロ|セックス|性的|乳首|下着|パンツ|おっぱい|性器|えっち|エッチ/i;

export function isSexualRequest(text: string): boolean {
  return SEXUAL_TERMS.test(text);
}

/** Sexual content involving minors is never generated, whatever the model allows. */
export function assertAllowedRequest(...texts: string[]): void {
  const all = texts.join(" \n ");
  if (MINOR_TERMS.test(all) && SEXUAL_TERMS.test(all)) {
    throw new Error("未成年を性的に描く内容は生成できません");
  }
}

/** Printable text only, so a prompt can never turn into extra command-line flags. */
export function sanitizeMediaText(raw: string, max = MAX_TEXT): string {
  return raw
    .replace(/[\r\n\t]+/g, " ")
    .replace(/[^\p{L}\p{N}\s,.:;!?()'"_+\-/&、。・ー〜～！？「」]/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

// ---------- Prompt translation (gemma worker, CPU) ----------

async function askGemma(system: string, user: string, maxTokens: number): Promise<string> {
  const worker = SPECIALIST_WORKERS.find((entry) => entry.key === "gemma");
  if (!worker) throw new Error("gemma worker is not configured");
  // Probe /health first: the PowerShell port lookup in startSpecialistWorker is slow when the
  // gateway runs as a scheduled task, and the worker is normally already up.
  const up = await fetch(`http://127.0.0.1:${worker.port}/health`, {
    signal: AbortSignal.timeout(3_000),
  })
    .then((res) => res.ok)
    .catch(() => false);
  if (!up) {
    const started = await startSpecialistWorker("gemma");
    if (!started.success) throw new Error(started.message);
    if (!(await waitForWorkerReady(worker.port)))
      throw new Error("gemma worker did not become ready");
  }
  const response = await fetch(`http://127.0.0.1:${worker.port}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      max_tokens: maxTokens,
      temperature: 0.3,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(5 * 60_000),
  });
  if (!response.ok) throw new Error(`gemma worker HTTP ${response.status}`);
  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return data.choices?.[0]?.message?.content?.trim() ?? "";
}

const ASCII_ONLY = (text: string) => text.replace(/[^\x20-\x7E]/g, " ");

async function toImageTags(request: string): Promise<string> {
  const tags = await askGemma(
    "You write prompts for an anime Stable Diffusion XL model (Illustrious). Convert the user's request into 10-30 comma-separated English danbooru-style tags: subject count and gender, appearance, clothing, pose, expression, background, lighting, composition. Keep every detail the user asked for. Output only the tags.",
    request,
    200,
  );
  return sanitizeMediaText(ASCII_ONLY(tags), 700);
}

/** Z-Image understands sentences better than booru tags. */
async function toNaturalImagePrompt(request: string): Promise<string> {
  const text = await askGemma(
    "You write prompts for a text-to-image model. Rewrite the user's request as two to four vivid English sentences describing subject, appearance, clothing, pose, expression, setting, lighting, art style (anime illustration unless the user asks otherwise) and composition. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 900);
}

/** Prompt text for the chosen image model: booru tags for SDXL, sentences for Z-Image. */
function imagePromptFor(job: MediaJob): Promise<string> {
  return job.options.imageModel === "zimage"
    ? toNaturalImagePrompt(job.request)
    : toImageTags(job.request);
}

async function toMotionPrompt(request: string): Promise<string> {
  const text = await askGemma(
    "You write prompts for an image-to-video model. Describe in one or two short English sentences the motion and camera movement the user wants (what moves, how, camera). Output only the description.",
    request || "subtle natural motion, gentle camera movement",
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 500);
}

/** LTX-2 renders sound too, so its prompt also describes what is heard. */
async function toAudioVideoPrompt(request: string): Promise<string> {
  const text = await askGemma(
    "You write prompts for a video model that also generates the soundtrack. In two or three short English sentences describe the motion, the camera, and what is heard (voices, ambient sound, music, effects). Keep any spoken line in the user's language inside quotes. Output only the description.",
    request || "subtle natural motion, gentle camera movement, soft ambient sound",
    200,
  );
  return sanitizeMediaText(text.replace(/[\r\n]+/g, " "), 700);
}

async function toMusicCaption(request: string): Promise<string> {
  const text = await askGemma(
    "You write captions for a text-to-music model. Rewrite the user's request as one English sentence listing genre, mood, tempo feel, instruments and vocal type (for example: 'Upbeat J-pop with bright synths, punchy drums and a cheerful female vocal'). Output only the caption.",
    request,
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 400);
}

// ---------- GPU lease ----------

let pausedByUs = false;

function runPowerShell(command: string): Promise<string> {
  return new Promise((resolve) => {
    const ps = spawn("powershell.exe", ["-NoProfile", "-Command", command], {
      windowsHide: true,
      stdio: ["ignore", "pipe", "ignore"],
    });
    let out = "";
    ps.stdout?.on("data", (chunk) => (out += String(chunk)));
    ps.on("exit", () => resolve(out.trim()));
    ps.on("error", () => resolve(out.trim()));
  });
}

async function acquireGpu(): Promise<void> {
  // Voice training may already hold the flag; then it owns the restart too.
  if (!(await exists(pauseFlag()))) {
    await fs.writeFile(pauseFlag(), String(Date.now()));
    pausedByUs = true;
  }
  // netstat + kill instead of PowerShell: PowerShell starts very slowly when the gateway runs
  // as a scheduled task, and this sits on the critical path of every job.
  const pid = await listeningPid(11434);
  if (pid) {
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
    for (let i = 0; i < 20 && (await listeningPid(11434)); i++) {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
}

function listeningPid(port: number): Promise<number | undefined> {
  return new Promise((resolve) => {
    execFile(
      "netstat",
      ["-ano", "-p", "TCP"],
      { windowsHide: true, timeout: 15_000 },
      (err, stdout) => {
        if (err) return resolve(undefined);
        for (const line of stdout.split(/\r?\n/)) {
          const cols = line.trim().split(/\s+/);
          if (cols[1]?.endsWith(`:${port}`) && cols[3] === "LISTENING")
            return resolve(Number(cols[4]) || undefined);
        }
        resolve(undefined);
      },
    );
  });
}

async function releaseGpu(): Promise<void> {
  if (!pausedByUs) return;
  pausedByUs = false;
  await fs.rm(pauseFlag(), { force: true });
  // Bring the chat model back right away instead of waiting for the watchdog (~1 minute).
  if (await exists(lastMainCmdline())) {
    await runPowerShell(
      `if (-not (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue)) { $cmd = (Get-Content '${lastMainCmdline()}' -Raw).Trim(); ([wmiclass]'Win32_Process').Create($cmd, '${path.join(home(), "bonsai", "bin")}', $null) | Out-Null }`,
    );
  }
}

// ---------- Runners ----------

/** Sets the progress note and records how long the previous stage took. */
function markStage(job: MediaJob, stage: string): void {
  const now = Date.now();
  const timings = (job.timings ??= {});
  const last = timings.__at ?? job.startedAt ?? now;
  // Step counters ("画像を描いています 12/28") roll up into one entry per stage.
  const key = job.stage?.replace(/\s+\d+\/\d+$/, "");
  if (key) timings[key] = (timings[key] ?? 0) + (now - last);
  timings.__at = now;
  job.stage = stage;
}

function runTool(
  exe: string,
  args: string[],
  opts: { cwd?: string; onLine?: (line: string) => void; timeoutMs?: number },
): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, {
      cwd: opts.cwd ?? path.dirname(exe),
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    // The gateway runs as a below-normal scheduled task and children inherit that class, which on
    // Windows also lowers disk I/O priority: loading a 7 GB model then took minutes instead of seconds.
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
    } catch {
      // Priority is best-effort.
    }
    let tail = "";
    const onData = (chunk: Buffer) => {
      const text = chunk.toString("utf-8");
      tail = (tail + text).slice(-3_000);
      if (opts.onLine)
        for (const line of text.split(/\r?\n|\r/)) if (line.trim()) opts.onLine(line.trim());
    };
    child.stdout?.on("data", onData);
    child.stderr?.on("data", onData);
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error(`${path.basename(exe)} timed out`));
    }, opts.timeoutMs ?? JOB_TIMEOUT_MS);
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      if (code === 0) resolve();
      else
        reject(
          new Error(`${path.basename(exe)} exited with code ${code}: ${tail.trim().slice(-500)}`),
        );
    });
  });
}

/** sd.cpp prints "|====>   | 12/28 - 1.23s/it"; turn it into a short stage note. */
function stepNote(prefix: string, job: MediaJob) {
  return (line: string) => {
    const match = /(\d+)\/(\d+)\s*-\s*[\d.]+\s*(?:s\/it|it\/s)/.exec(line);
    if (!match) return;
    const stage = `${prefix} ${match[1]}/${match[2]}`;
    if (job.stage?.startsWith(prefix)) job.stage = stage;
    else markStage(job, stage);
  };
}

// ---------- Resident SDXL worker (PyTorch) ----------
// diffusers on the GPU samples ~2.5x faster than sd.cpp, and keeping the process (weights in RAM)
// between images also skips ~15 s of start-up. It holds the CUDA context, so it is stopped before
// the GPU goes back to the chat model.

const IMAGE_SERVER = "D:\\media-tools\\image\\sdxl_server.py";
/** After the queue drains, wait this long for another job before handing the GPU back. */
const IMAGE_GRACE_MS = 45_000;
let imageWorker: ReturnType<typeof spawn> | undefined;
let imageWorkerReady: Promise<void> | undefined;
const imageRequests = new Map<
  string,
  { resolve: () => void; reject: (err: Error) => void; onStep: (step: string) => void }
>();

function startImageWorker(): Promise<void> {
  if (imageWorker && imageWorkerReady) return imageWorkerReady;
  const child = spawn(WAN2GP_PYTHON, [IMAGE_SERVER], {
    cwd: path.dirname(IMAGE_SERVER),
    windowsHide: true,
    stdio: ["pipe", "pipe", "pipe"],
  });
  try {
    if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
  } catch {
    // Priority is best-effort.
  }
  imageWorker = child;
  let stderrTail = "";
  child.stderr?.on("data", (chunk) => {
    stderrTail = (stderrTail + String(chunk)).slice(-1_500);
  });
  imageWorkerReady = new Promise<void>((resolve, reject) => {
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
      if (imageWorker === child) {
        imageWorker = undefined;
        imageWorkerReady = undefined;
      }
    });
    child.on("error", reject);
  });
  return imageWorkerReady;
}

function stopImageWorker(): void {
  const child = imageWorker;
  imageWorker = undefined;
  imageWorkerReady = undefined;
  if (!child) return;
  child.stdin?.end();
  setTimeout(() => child.kill(), 10_000).unref();
}

async function renderWithImageWorker(
  job: MediaJob,
  params: { prompt: string; negative: string; width: number; height: number; output: string },
): Promise<void> {
  markStage(job, "画像の準備をしています");
  await startImageWorker();
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
        const stage = `画像を描いています ${step}`;
        if (job.stage?.startsWith("画像を描いています")) job.stage = stage;
        else markStage(job, stage);
      },
    });
    const spec = SDXL_MODELS[job.options.imageModel === "noobai" ? "noobai" : "wai"];
    imageWorker?.stdin?.write(
      `${JSON.stringify({ id, model: spec.file, vpred: spec.vpred, steps: spec.steps, cfg: spec.cfg, seed: -1, ...params })}\n`,
    );
  });
}

/** Keeps the GPU (and the warm image worker) for a short while in case another job follows. */
async function waitForMoreJobs(ms: number): Promise<boolean> {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    if (queue.length > 0) return true;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return queue.length > 0;
}

/** Z-Image Turbo (6B, 8 steps) through the Wan2GP runner; it reads natural-language prompts. */
async function renderWithZImage(job: MediaJob, prompt: string, file: string): Promise<void> {
  const size = ZIMAGE_SIZES[job.options.aspect ?? "portrait"];
  const workDir = path.join(outDir(), `${job.id}-zimg`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: workDir,
      settings: {
        model_type: "z_image",
        prompt,
        resolution: size,
        num_inference_steps: 8,
        guidance_scale: 0,
        seed: -1,
      },
    }),
  );
  markStage(job, "画像を描いています（Z-Image。初回はモデルのダウンロードがあります）");
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
  if (!image) throw new Error("Z-Image が画像を出力しませんでした");
  await fs.rename(image, file);
  await fs.rm(workDir, { recursive: true, force: true });
}

const ZIMAGE_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "1024x1024",
  portrait: "832x1216",
  landscape: "1216x832",
};

async function renderImage(
  job: MediaJob,
  tags: string,
  sexual: boolean,
  file: string,
): Promise<void> {
  const [width, height] = SIZES[job.options.aspect ?? "portrait"].image;
  if (job.options.imageModel === "zimage") {
    await renderWithZImage(job, `${sexual ? "An adult. " : ""}${tags}`, file);
    return;
  }
  const spec = SDXL_MODELS[job.options.imageModel === "noobai" ? "noobai" : "wai"];
  const negative = sexual ? `${spec.negative}, ${ADULT_ONLY_NEGATIVE}` : spec.negative;
  const positive = `${spec.quality}, ${sexual ? "adult, " : ""}${tags}`;
  if (await exists(IMAGE_SERVER)) {
    try {
      await renderWithImageWorker(job, { prompt: positive, negative, width, height, output: file });
      return;
    } catch (err) {
      // Fall through to sd.cpp so a broken Python environment never blocks image jobs.
      stopImageWorker();
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

async function runImageJob(job: MediaJob): Promise<void> {
  markStage(job, "指示を英語のタグに変換しています");
  const tags = await imagePromptFor(job);
  assertAllowedRequest(job.request, tags);
  markStage(job, "GPU を空けています");
  await acquireGpu();
  const file = path.join(outDir(), `${job.id}.png`);
  await renderImage(job, tags, isSexualRequest(`${job.request} ${tags}`), file);
  job.file = file;
  job.mime = "image/png";
}

async function runVideoJob(job: MediaJob): Promise<void> {
  const aspect = job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].video;
  let initImage: string;
  let sexual = isSexualRequest(job.request);
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
    markStage(job, "GPU を空けています");
    await acquireGpu();
    initImage = path.join(outDir(), `${job.id}-first.png`);
    await renderImage(job, tags, sexual, initImage);
  }
  const engine = job.options.engine ?? "standard";
  const motion =
    engine === "audio" ? await toAudioVideoPrompt(job.request) : await toMotionPrompt(job.request);
  assertAllowedRequest(job.request, motion);
  if (engine !== "standard") {
    await runWan2gpVideo(job, engine, initImage, motion, sexual, aspect);
    return;
  }
  markStage(job, "GPU を空けています");
  await acquireGpu();
  const file = path.join(outDir(), `${job.id}.webm`);
  await runTool(
    sdCli(),
    [
      "-M",
      "vid_gen",
      "--diffusion-model",
      VIDEO_MODEL,
      "--vae",
      VIDEO_VAE,
      "--t5xxl",
      VIDEO_T5,
      "--clip_vision",
      VIDEO_CLIP_VISION,
      "-i",
      initImage,
      "-p",
      motion,
      "-n",
      sexual ? `${VIDEO_NEGATIVE}, ${ADULT_ONLY_NEGATIVE}` : VIDEO_NEGATIVE,
      "-W",
      String(width),
      "-H",
      String(height),
      "--video-frames",
      "33",
      "--fps",
      "16",
      "--steps",
      "4",
      "--cfg-scale",
      "1",
      "--sampling-method",
      "euler_a",
      "--offload-to-cpu",
      "--diffusion-fa",
      "--vae-tiling",
      "--backend",
      "te=cpu",
      "-s",
      "-1",
      "-o",
      file,
    ],
    { onLine: stepNote("動画を作っています", job) },
  );
  job.file = file;
  job.mime = "video/webm";
}

// Wan2GP engines run headless through its Python API (openclaw_job.py); nothing is served on the network.
const WAN2GP_DIR = "D:\\media-tools\\Wan2GP";
const WAN2GP_PYTHON = path.join(WAN2GP_DIR, "venv", "Scripts", "python.exe");
const WAN2GP_RUNNER = path.join(WAN2GP_DIR, "openclaw_job.py");
const WAN2GP_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "624x624",
  portrait: "480x832",
  landscape: "832x480",
};
const WAN2GP_ENGINES: Record<
  Exclude<VideoEngine, "standard">,
  { modelType: string; fps: number; frameStep: number; maxSec: number; label: string }
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
};

/** Frame count the model accepts: 1 + a multiple of its frame step. */
export function wan2gpFrames(
  seconds: number,
  fps: number,
  frameStep: number,
  maxSec: number,
): number {
  const clamped = Math.min(maxSec, Math.max(2, seconds));
  return Math.ceil((clamped * fps) / frameStep) * frameStep + 1;
}

async function runWan2gpVideo(
  job: MediaJob,
  engine: Exclude<VideoEngine, "standard">,
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
        negative_prompt: sexual ? `${VIDEO_NEGATIVE}, ${ADULT_ONLY_NEGATIVE}` : VIDEO_NEGATIVE,
        image_prompt_type: "S",
        image_start: initImage,
        resolution: WAN2GP_SIZES[aspect],
        video_length: wan2gpFrames(
          job.options.durationSec ?? 5,
          spec.fps,
          spec.frameStep,
          spec.maxSec,
        ),
        seed: -1,
      },
    }),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu();
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
  const file = path.join(outDir(), `${job.id}${ext}`);
  await fs.rename(video, file);
  await fs.rm(workDir, { recursive: true, force: true });
  job.file = file;
  job.mime = ext === ".webm" ? "video/webm" : ext === ".mkv" ? "video/x-matroska" : "video/mp4";
}

async function runMusicJob(job: MediaJob): Promise<void> {
  markStage(job, "曲の説明を英語に変換しています");
  const caption = await toMusicCaption(job.request);
  const lyrics = job.options.instrumental
    ? "[Instrumental]"
    : sanitizeLyrics(job.options.lyrics ?? "");
  assertAllowedRequest(job.request, caption, lyrics);
  const workDir = path.join(outDir(), `${job.id}-work`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      caption,
      lyrics,
      duration: Math.min(240, Math.max(15, job.options.durationSec ?? 90)),
      vocal_language: job.options.instrumental ? "unknown" : "ja",
      inference_steps: 8,
      shift: 3.0,
      lm_model: MUSIC_LM,
      synth_model: MUSIC_DIT,
      output_format: "mp3",
      mp3_bitrate: 192,
    }),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu();
  job.stage = lyrics ? "曲の構成を考えています" : "歌詞と曲の構成を考えています";
  await runTool(aceBin("ace-lm"), ["--models", MUSIC_MODEL_DIR, "--request", request], {});
  markStage(job, "音にしています");
  await runTool(
    aceBin("ace-synth"),
    ["--models", MUSIC_MODEL_DIR, "--request", path.join(workDir, "request0.json")],
    {},
  );
  const file = path.join(outDir(), `${job.id}.mp3`);
  await fs.rename(path.join(workDir, "request00.mp3"), file);
  // Keep the generated lyrics with the result so the app can show them.
  try {
    const enriched = JSON.parse(
      await fs.readFile(path.join(workDir, "request0.json"), "utf-8"),
    ) as {
      lyrics?: string;
    };
    job.options.lyrics = enriched.lyrics ?? lyrics;
  } catch {
    // Lyrics display is best-effort.
  }
  await fs.rm(workDir, { recursive: true, force: true });
  job.file = file;
  job.mime = "audio/mpeg";
}

/** Lyrics keep their line breaks and section tags like [Verse]. */
export function sanitizeLyrics(raw: string): string {
  return raw
    .replace(/\r\n?/g, "\n")
    .replace(/[^\p{L}\p{N}\s\[\],.:;!?()'"_+\-/&ー〜～、。「」『』！？]/gu, "")
    .trim()
    .slice(0, 3_000);
}

// ---------- Queue ----------

const jobs = new Map<string, MediaJob>();
const queue: string[] = [];
let working = false;

function metaPath(id: string): string {
  return path.join(outDir(), `${id}.json`);
}

async function saveJobMeta(job: MediaJob): Promise<void> {
  const { sourceImageBase64: _drop, ...options } = job.options;
  await fs.writeFile(metaPath(job.id), JSON.stringify({ ...job, options }));
}

async function loadJobMeta(id: string): Promise<MediaJob | undefined> {
  if (!/^[a-f0-9]{12}$/.test(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(metaPath(id), "utf-8")) as MediaJob;
  } catch {
    return undefined;
  }
}

async function pruneResults(): Promise<void> {
  const metas = (await listMediaJobs(1_000)).filter(
    (job) => job.status === "done" || job.status === "error",
  );
  for (const job of metas.slice(KEEP_RESULTS)) {
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
      job.status = "running";
      job.startedAt = Date.now();
      try {
        await fs.mkdir(outDir(), { recursive: true });
        if (job.kind === "image") await runImageJob(job);
        else if (job.kind === "video") await runVideoJob(job);
        else await runMusicJob(job);
        job.status = "done";
        markStage(job, "完成");
      } catch (err) {
        job.status = "error";
        job.error = String(err instanceof Error ? err.message : err).slice(0, 600);
        job.stage = undefined;
      } finally {
        job.finishedAt = Date.now();
        job.durationSec = Math.round((job.finishedAt - (job.startedAt ?? job.finishedAt)) / 1000);
        job.options.sourceImageBase64 = undefined;
        await saveJobMeta(job).catch(() => undefined);
        // Hand the GPU back between jobs only when nothing else is waiting. A warm image worker
        // gets a short grace period so a follow-up image skips the start-up.
        if (queue.length === 0 && !(imageWorker && (await waitForMoreJobs(IMAGE_GRACE_MS)))) {
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

export async function startMediaJob(
  kind: MediaKind,
  request: string,
  options: MediaOptions,
): Promise<MediaJob> {
  const status = await mediaStudioStatus();
  const usesWan2gp = kind === "video" && (options.engine ?? "standard") !== "standard";
  const needed = usesWan2gp ? status.wan2gp : status[kind];
  if (!needed.ready) {
    throw new Error(`${kind} generation is not installed (missing: ${needed.missing.join(", ")})`);
  }
  if (options.imageModel === "zimage" && !status.wan2gp.ready) {
    throw new Error("Z-Image には Wan2GP が必要です");
  }
  if (options.imageModel === "noobai" && !(await exists(SDXL_MODELS.noobai.file))) {
    throw new Error("NoobAI のモデルが見つかりません");
  }
  const clean = sanitizeMediaText(request);
  if (!clean && kind !== "video") throw new Error("内容を入力してください");
  assertAllowedRequest(clean, options.lyrics ?? "");
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
  };
  jobs.set(job.id, job);
  queue.push(job.id);
  void processQueue();
  return job;
}

export async function getMediaJob(id: string): Promise<MediaJob | undefined> {
  return jobs.get(id) ?? (await loadJobMeta(id));
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
): Promise<{ mime: string; base64: string } | undefined> {
  const job = await getMediaJob(id);
  if (!job?.file || job.status !== "done" || !job.mime) return undefined;
  return { mime: job.mime, base64: (await fs.readFile(job.file)).toString("base64") };
}

export async function deleteMediaJob(id: string): Promise<boolean> {
  const job = await getMediaJob(id);
  if (!job || job.status === "running" || job.status === "queued") return false;
  jobs.delete(id);
  await fs.rm(metaPath(id), { force: true });
  if (job.file) await fs.rm(job.file, { force: true });
  return true;
}
