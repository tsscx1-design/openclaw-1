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
  /** video: interpolate to twice the frame rate (RIFE) so motion looks smoother; same length */
  smooth?: boolean;
  /** image model for images and for the first frame of text-to-video */
  imageModel?: ImageModel;
  /** SDXL (not V-Pred): sample in 8 steps with the DMD2 LoRA, about 3x faster, slightly different look */
  fast?: boolean;
  /** music, and video length for the Wan2GP engines */
  lyrics?: string;
  instrumental?: boolean;
  durationSec?: number;
};

/** "standard" and "remix" run in sd.cpp; the others in Wan2GP. */
export type VideoEngine = "standard" | "remix" | "long" | "audio" | "fast" | "h3";
type SdcppVideoEngine = "standard" | "remix";
export type ImageModel = "wai" | "noobai" | "zimage" | "lustify" | "cyberreal" | "asianreal";
type SdxlModel = Exclude<ImageModel, "zimage">;
type SdxlSampler = "euler_a" | "dpmpp_2m_sde_karras" | "lcm";

const MEDIA_MODEL_DIR = "D:\\media-models";
const IMAGE_MODEL = path.join(MEDIA_MODEL_DIR, "image", "waiIllustriousSDXL_v170.safetensors");
/** DMD2 4-step LoRA (tianweiy/DMD2); with LCM it works on SDXL epsilon checkpoints, not V-Pred. */
const DMD2_LORA = path.join(
  MEDIA_MODEL_DIR,
  "image",
  "lora",
  "dmd2_sdxl_4step_lora_fp16.safetensors",
);
const DMD2_SETTINGS = { sampler: "lcm" as SdxlSampler, steps: 8, cfg: 1.5 };

const PHOTO_NEGATIVE =
  "cartoon, illustration, anime, painting, CGI, 3D render, low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, deformed, watermark, logo, text";

/** SDXL checkpoints served by the resident worker; Z-Image runs in Wan2GP instead. */
const SDXL_MODELS: Record<
  SdxlModel,
  {
    file: string;
    vpred: boolean;
    /** Photo-real checkpoints get descriptive phrases instead of danbooru tags. */
    photo: boolean;
    sampler: SdxlSampler;
    steps: number;
    cfg: number;
    quality: string;
    negative: string;
  }
> = {
  wai: {
    file: IMAGE_MODEL,
    vpred: false,
    photo: false,
    sampler: "euler_a",
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
    photo: false,
    sampler: "euler_a",
    steps: 30,
    cfg: 4.5,
    quality: "masterpiece, best quality, newest, absurdres, highres",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // LUSTIFY V9 Zenith (photo-real, uncensored): DPM++ 2M SDE Karras, ~30 steps, CFG 4-7.
  lustify: {
    file: path.join(MEDIA_MODEL_DIR, "image", "lustifyNSFWCheckpoint_zenithV9.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 4.5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // CyberRealistic XL Play v10: DPM++ 2M SDE Karras, 30+ steps, CFG 3-5.
  cyberreal: {
    file: path.join(MEDIA_MODEL_DIR, "image", "CyberRealisticXLPlay_V10.0_FP16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 4,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // Asian Realism by Stable Yogi (Illustrious) v2.0: East Asian faces; DPM++ 2M Karras, 20-30 steps, CFG 5-7.
  asianreal: {
    file: path.join(MEDIA_MODEL_DIR, "image", "asianRealismByStable_v20FP16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 28,
    cfg: 5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
};

/** The SDXL model a job uses; anything else (unset, Z-Image's sd.cpp fallback) means WAI. */
function sdxlModelOf(job: MediaJob): SdxlModel {
  const model = job.options.imageModel;
  return model && model !== "zimage" ? model : "wai";
}

/** Sampling settings for an SDXL job; "fast" swaps in DMD2 where the checkpoint allows it. */
export function sdxlSampling(
  model: SdxlModel,
  fast: boolean,
): { sampler: SdxlSampler; steps: number; cfg: number; lora?: string } {
  const spec = SDXL_MODELS[model];
  // CFG stays above 1 so the negative prompt (including the adult-only one) still applies.
  if (fast && !spec.vpred) return { ...DMD2_SETTINGS, lora: DMD2_LORA };
  return { sampler: spec.sampler, steps: spec.steps, cfg: spec.cfg };
}
const VIDEO_DIR = path.join(MEDIA_MODEL_DIR, "video");
const VIDEO_MODEL = path.join(VIDEO_DIR, "wan2.2-i2v-rapid-aio-v10-nsfw-Q3_K_S.gguf");
const VIDEO_VAE = path.join(VIDEO_DIR, "wan_2.1_vae.safetensors");
const VIDEO_T5 = path.join(VIDEO_DIR, "umt5-xxl-encoder-Q8_0.gguf");
const VIDEO_CLIP_VISION = path.join(VIDEO_DIR, "clip_vision_h.safetensors");
// Wan 2.2 Remix v3 I2V (photo-real, uncensored): MoE pair, 4-8 steps at CFG 1, shift 5-10.
const REMIX_HIGH = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vHighV30-Q3_K_M.gguf");
const REMIX_LOW = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vLowV30-Q3_K_M.gguf");
/** RIFE v4.26 frame interpolation (Wan2GP's weights) for the sd.cpp engines. */
const RIFE_SCRIPT = "D:\\media-tools\\image\\rife_video.py";
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

// ---------- Prompt translation (main LLM on the GPU, else the gemma worker on CPU) ----------

const MAIN_LLM = "http://127.0.0.1:11434";

/**
 * The chat model on the GPU writes a prompt in ~1-2 s where the CPU gemma worker took 5-10 s.
 * It is skipped (undefined) while the GPU is lent out (pause flag), while it is busy answering a
 * chat (one slot), or when it fails, so a media job never waits on the conversation.
 */
async function askMainLlm(
  system: string,
  user: string,
  maxTokens: number,
): Promise<string | undefined> {
  if (await exists(pauseFlag())) return undefined;
  try {
    const slots = await fetch(`${MAIN_LLM}/slots`, { signal: AbortSignal.timeout(2_000) });
    if (slots.ok) {
      const list = (await slots.json()) as Array<{ is_processing?: boolean }>;
      if (list.some((slot) => slot.is_processing)) return undefined;
    }
    const response = await fetch(`${MAIN_LLM}/v1/chat/completions`, {
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
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) return undefined;
    const data = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    return data.choices?.[0]?.message?.content?.trim() || undefined;
  } catch {
    return undefined;
  }
}

async function askPromptWriter(system: string, user: string, maxTokens: number): Promise<string> {
  return (await askMainLlm(system, user, maxTokens)) ?? (await askGemma(system, user, maxTokens));
}

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
  const tags = await askPromptWriter(
    "You write prompts for an anime Stable Diffusion XL model (Illustrious). Convert the user's request into 10-30 comma-separated English danbooru-style tags: subject count and gender, appearance, clothing, pose, expression, background, lighting, composition. Keep every detail the user asked for. Output only the tags.",
    request,
    200,
  );
  return sanitizeMediaText(ASCII_ONLY(tags), 700);
}

/** Photo-real SDXL checkpoints follow short descriptive phrases better than anime tags. */
async function toPhotoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a photorealistic Stable Diffusion XL model. Convert the user's request into one line of 15-35 comma-separated short English phrases: subject, age, ethnicity, hair, face and expression, body, clothing, pose, setting, lighting, camera shot and lens. People are adults; write an explicit adult age such as 'in her mid 20s'. Unless the user names another ethnicity or nationality, every person is Japanese: write 'Japanese woman' or 'Japanese man' and add 'East Asian facial features'. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 800);
}

/** Z-Image understands sentences better than booru tags. */
async function toNaturalImagePrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a text-to-image model. Rewrite the user's request as two to four vivid English sentences describing subject, appearance, clothing, pose, expression, setting, lighting, art style (anime illustration unless the user asks otherwise) and composition. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 900);
}

/** Prompt text for the chosen image model: booru tags for anime SDXL, phrases for photo SDXL, sentences for Z-Image. */
function imagePromptFor(job: MediaJob): Promise<string> {
  if (job.options.imageModel === "zimage") return toNaturalImagePrompt(job.request);
  return SDXL_MODELS[sdxlModelOf(job)].photo
    ? toPhotoPrompt(job.request)
    : toImageTags(job.request);
}

async function toMotionPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for an image-to-video model. Describe in one or two short English sentences the motion and camera movement the user wants (what moves, how, camera). Output only the description.",
    request || "subtle natural motion, gentle camera movement",
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 500);
}

/** LTX-2 renders sound too, so its prompt also describes what is heard. */
async function toAudioVideoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a video model that also generates the soundtrack. In two or three short English sentences describe the motion, the camera, and what is heard (voices, ambient sound, music, effects). Keep any spoken line in the user's language inside quotes. Output only the description.",
    request || "subtle natural motion, gentle camera movement, soft ambient sound",
    200,
  );
  return sanitizeMediaText(text.replace(/[\r\n]+/g, " "), 700);
}

const H3_PREFIX = "integrated_multimodal_description:";

/** MiniMax H3 reads a two-part script: the shot (with optional dialogue) and the soundscape. */
async function toH3Prompt(request: string, seconds: number): Promise<string> {
  const text = await askPromptWriter(
    `You write prompts for MiniMax H3, which animates a first frame into a ${seconds}-second realistic clip with synchronized sound. Write exactly two lines in English:\n${H3_PREFIX} [Shot 1] A ${seconds}-second realistic single take. <who is in the frame, what they do, their expression, how the camera moves>\noverall_soundscape: <the sounds heard>\nPeople are adults. If someone should speak, put the words in Japanese inside the first line as (S1) <d>[Japanese] spoken words</d>. Keep every detail the user asked for. Output only the two lines.`,
    request || "natural subtle motion with a gentle camera push in and soft ambient sound",
    260,
  );
  return sanitizeH3Prompt(text);
}

/**
 * H3's script needs line breaks, brackets and <d> tags, which sanitizeMediaText removes. It only
 * travels inside the Wan2GP request JSON, never on a command line.
 */
export function sanitizeH3Prompt(raw: string): string {
  const text = raw
    .replace(/\r\n?/g, "\n")
    .replace(/[^\p{L}\p{N} \n,.:;!?()'"_+\-/&[\]<>、。・ー〜～！？「」]/gu, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim()
    .slice(0, 1_500);
  return text.startsWith(H3_PREFIX) ? text : `${H3_PREFIX} [Shot 1] ${text}`;
}

async function toMusicCaption(request: string): Promise<string> {
  const text = await askPromptWriter(
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

/**
 * Stops the CPU worker LLMs (~16 GB of RAM) for jobs that need nearly all memory; whoever uses
 * a worker next starts it again on demand.
 */
async function stopCpuWorkers(): Promise<void> {
  for (const worker of SPECIALIST_WORKERS) {
    const pid = await listeningPid(worker.port);
    if (!pid) continue;
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
  }
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
    const model = sdxlModelOf(job);
    const spec = SDXL_MODELS[model];
    const sampling = sdxlSampling(model, job.options.fast === true);
    imageWorker?.stdin?.write(
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
        // Nunchaku SVDQ INT4 (kernels in the Wan2GP venv): 49 s vs 65 s for the INT8 build, same look.
        model_type: "z_image_nunchaku_r256_int4",
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
  const spec = SDXL_MODELS[sdxlModelOf(job)];
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

/** LTX-2 ("audio") and H3 also need the soundtrack described; the other engines only motion. */
function motionPromptFor(job: MediaJob): Promise<string> {
  if (job.options.engine === "h3") return toH3Prompt(job.request, job.options.durationSec ?? 5);
  return job.options.engine === "audio"
    ? toAudioVideoPrompt(job.request)
    : toMotionPrompt(job.request);
}

async function runVideoJob(job: MediaJob): Promise<void> {
  const aspect = job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].video;
  let initImage: string;
  let motion: string | undefined;
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
    // Written before the GPU is taken, while the chat model can still do it quickly.
    motion = await motionPromptFor(job);
    markStage(job, "GPU を空けています");
    await acquireGpu();
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
  await acquireGpu();
  const file = path.join(outDir(), `${job.id}.webm`);
  await runTool(
    sdCli(),
    sdcppVideoArgs(engine, {
      initImage,
      prompt: motion,
      negative: sexual ? `${VIDEO_NEGATIVE}, ${ADULT_ONLY_NEGATIVE}` : VIDEO_NEGATIVE,
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
  const file = path.join(outDir(), `${job.id}.mp4`);
  await fs.rename(smoothed, file);
  await fs.rm(job.file, { force: true });
  job.file = file;
  job.mime = "video/mp4";
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
        negative_prompt: sexual ? `${VIDEO_NEGATIVE}, ${ADULT_ONLY_NEGATIVE}` : VIDEO_NEGATIVE,
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
  await acquireGpu();
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
  if (options.imageModel === "zimage" && !status.wan2gp.ready) {
    throw new Error("Z-Image には Wan2GP が必要です");
  }
  if (
    options.imageModel &&
    options.imageModel !== "zimage" &&
    !(await exists(SDXL_MODELS[options.imageModel].file))
  ) {
    throw new Error(`画像モデル（${options.imageModel}）のファイルが見つかりません`);
  }
  if (options.fast && !(await exists(DMD2_LORA))) {
    throw new Error("高速モード（DMD2）のファイルが見つかりません");
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
