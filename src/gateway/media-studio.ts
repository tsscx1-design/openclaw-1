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
  /** music */
  lyrics?: string;
  instrumental?: boolean;
  durationSec?: number;
};

const MEDIA_MODEL_DIR = "D:\\media-models";
const IMAGE_MODEL = path.join(MEDIA_MODEL_DIR, "image", "waiIllustriousSDXL_v170.safetensors");
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

const IMAGE_QUALITY = "masterpiece, best quality, amazing quality, very aesthetic, absurdres";
const IMAGE_NEGATIVE =
  "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch";
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

async function toMotionPrompt(request: string): Promise<string> {
  const text = await askGemma(
    "You write prompts for an image-to-video model. Describe in one or two short English sentences the motion and camera movement the user wants (what moves, how, camera). Output only the description.",
    request || "subtle natural motion, gentle camera movement",
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 500);
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
  opts: { cwd?: string; onLine?: (line: string) => void },
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
    }, JOB_TIMEOUT_MS);
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

async function renderImage(
  job: MediaJob,
  tags: string,
  sexual: boolean,
  file: string,
): Promise<void> {
  const [width, height] = SIZES[job.options.aspect ?? "portrait"].image;
  const negative = sexual ? `${IMAGE_NEGATIVE}, ${ADULT_ONLY_NEGATIVE}` : IMAGE_NEGATIVE;
  const positive = `${IMAGE_QUALITY}, ${sexual ? "adult, " : ""}${tags}`;
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
  const tags = await toImageTags(job.request);
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
    const tags = await toImageTags(job.request);
    assertAllowedRequest(job.request, tags);
    sexual ||= isSexualRequest(tags);
    markStage(job, "GPU を空けています");
    await acquireGpu();
    initImage = path.join(outDir(), `${job.id}-first.png`);
    await renderImage(job, tags, sexual, initImage);
  }
  const motion = await toMotionPrompt(job.request);
  assertAllowedRequest(job.request, motion);
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
        // Hand the GPU back between jobs only when nothing else is waiting.
        if (queue.length === 0) await releaseGpu();
      }
    }
    await pruneResults().catch(() => undefined);
  } finally {
    working = false;
    if (queue.length === 0) await releaseGpu();
  }
}

export async function startMediaJob(
  kind: MediaKind,
  request: string,
  options: MediaOptions,
): Promise<MediaJob> {
  const status = await mediaStudioStatus();
  if (!status[kind].ready) {
    throw new Error(
      `${kind} generation is not installed (missing: ${status[kind].missing.join(", ")})`,
    );
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
