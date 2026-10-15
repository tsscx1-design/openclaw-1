import { execFile, spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// GPT-SoVITS (v2ProPlus weights of the v5 package) through its own HTTP server (api_v2.py).
// The server is started when a voice needs it and stopped after it was idle for a while. A voice-studio recording is the
// reference (the exact words of the reference are passed too, when known). The CPU is the default device because the chat
// model holds most of the GPU memory. The GPU is used when OPENCLAW_VOICE_DEVICE=cuda, or (unset) when the media GPU
// (OPENCLAW_MEDIA_CUDA_DEVICE, the RTX 3060) has room (MIN_FREE_MB) and no media job holds it (llm-pause.flag); the server
// then sees only that GPU. If the GPU start fails it starts again on the CPU.

const ROOT =
  process.env.OPENCLAW_GPTSOVITS_DIR ?? "D:\\media-tools\\GPT-SoVITS\\pkg\\GPT-SoVITS-v5-20261006";
const PORT = 9880;
const READY_TIMEOUT_MS = 240_000;
const IDLE_MS = 10 * 60_000;
const MIN_FREE_MB = 2500;

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

const url = (suffix: string): string => `http://127.0.0.1:${PORT}${suffix}`;

export async function gptSovitsInstalled(): Promise<boolean> {
  return (
    (await exists(path.join(ROOT, "api_v2.py"))) &&
    (await exists(path.join(ROOT, "runtime", "python.exe"))) &&
    (await exists(path.join(ROOT, "GPT_SoVITS", "pretrained_models", "s1v3.ckpt"))) &&
    (await exists(path.join(ROOT, "GPT_SoVITS", "pretrained_models", "v2Pro", "s2Gv2ProPlus.pth")))
  );
}

/** True when something answers on the server port (any HTTP status counts: the root path has no page). */
export async function gptSovitsRunning(): Promise<boolean> {
  try {
    await fetch(url("/"), { signal: AbortSignal.timeout(1500) });
    return true;
  } catch {
    return false;
  }
}

/** Free memory (MB) of one GPU, by its UUID or index; undefined when nvidia-smi cannot tell. */
function freeMemoryMb(gpu: string): Promise<number | undefined> {
  return new Promise((resolve) => {
    execFile(
      "nvidia-smi",
      ["--query-gpu=uuid,index,memory.free", "--format=csv,noheader,nounits"],
      { windowsHide: true, timeout: 8000 },
      (error, stdout) => {
        if (error) return resolve(undefined);
        for (const line of String(stdout).split(/\r?\n/)) {
          const [uuid, index, free] = line.split(",").map((part) => part.trim());
          if (uuid === gpu || index === gpu) return resolve(Number(free));
        }
        resolve(undefined);
      },
    );
  });
}

/** The GPU for the server (CUDA_VISIBLE_DEVICES value), or undefined for the CPU. */
async function pickGpu(): Promise<string | undefined> {
  const wanted = process.env.OPENCLAW_VOICE_DEVICE;
  if (wanted === "cpu") return undefined;
  const gpu = process.env.OPENCLAW_VOICE_CUDA_DEVICE ?? process.env.OPENCLAW_MEDIA_CUDA_DEVICE;
  if (!gpu) return undefined;
  if (wanted === "cuda") return gpu;
  // automatic: not while a media job (it pauses the chat model with this flag) holds the GPUs, and only with enough free memory
  if (await exists(path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag"))) return undefined;
  const free = await freeMemoryMb(gpu);
  return free !== undefined && free >= MIN_FREE_MB ? gpu : undefined;
}

let gpuInUse: string | undefined;

async function writeConfig(gpu: string | undefined): Promise<string> {
  const dir = path.join(resolveOpenClawHome(), "tts");
  await fs.mkdir(dir, { recursive: true });
  const file = path.join(dir, "gptsovits_infer.yaml");
  const dev = gpu ? "cuda" : "cpu";
  const text = [
    "custom:",
    "  bert_base_path: GPT_SoVITS/pretrained_models/chinese-roberta-wwm-ext-large",
    "  cnhuhbert_base_path: GPT_SoVITS/pretrained_models/chinese-hubert-base",
    `  device: ${dev}`,
    `  is_half: ${dev === "cuda" ? "true" : "false"}`,
    "  t2s_weights_path: GPT_SoVITS/pretrained_models/s1v3.ckpt",
    "  version: v2ProPlus",
    "  vits_weights_path: GPT_SoVITS/pretrained_models/v2Pro/s2Gv2ProPlus.pth",
    "",
  ].join("\n");
  await fs.writeFile(file, text, "utf-8");
  return file;
}

let starting: Promise<void> | undefined;
let idleTimer: NodeJS.Timeout | undefined;

function touchIdle(): void {
  if (idleTimer) clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    void fetch(url("/control?command=exit"), { signal: AbortSignal.timeout(3000) }).catch(
      () => undefined,
    );
  }, IDLE_MS);
  idleTimer.unref?.();
}

async function startServer(gpu: string | undefined): Promise<void> {
  const config = await writeConfig(gpu);
  const env: NodeJS.ProcessEnv = { ...process.env, PYTHONUTF8: "1", KMP_DUPLICATE_LIB_OK: "TRUE" };
  // the server sees only the chosen GPU (the media jobs keep using theirs); with none chosen it sees no GPU at all
  env.CUDA_VISIBLE_DEVICES = gpu ?? "";
  const child = spawn(
    path.join(ROOT, "runtime", "python.exe"),
    ["api_v2.py", "-a", "127.0.0.1", "-p", String(PORT), "-c", config],
    {
      cwd: ROOT,
      detached: true,
      stdio: "ignore",
      windowsHide: true,
      env,
    },
  );
  let exited = false;
  child.once("exit", () => {
    exited = true;
  });
  child.unref();
  const deadline = Date.now() + READY_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (await gptSovitsRunning()) {
      gpuInUse = gpu;
      return;
    }
    if (exited) throw new Error("GPT-SoVITS が、起動の途中で止まりました");
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error("GPT-SoVITS の起動に時間がかかりすぎました");
}

export async function ensureGptSovits(): Promise<void> {
  if (!(await gptSovitsInstalled())) throw new Error("GPT-SoVITS がインストールされていません");
  if (await gptSovitsRunning()) return;
  if (!starting) {
    starting = (async () => {
      const gpu = await pickGpu();
      try {
        await startServer(gpu);
      } catch (error) {
        if (!gpu) throw error;
        // the GPU start failed (memory, driver): start again on the CPU
        await startServer(undefined);
      }
    })().finally(() => {
      starting = undefined;
    });
  }
  await starting;
}

/** Which device the running server uses ("cuda" or "cpu"; undefined when it is not running). */
export async function gptSovitsDevice(): Promise<"cuda" | "cpu" | undefined> {
  if (!(await gptSovitsRunning())) return undefined;
  return gpuInUse ? "cuda" : "cpu";
}

let queue: Promise<unknown> = Promise.resolve();

/** One synthesis at a time (the server is single-threaded). `referenceWav` must be an absolute path. */
export function synthesizeGptSovits(request: {
  text: string;
  referenceWav: string;
  promptText?: string;
}): Promise<Buffer> {
  const job = queue.then(() => run(request));
  queue = job.catch(() => undefined);
  return job;
}

async function run(request: {
  text: string;
  referenceWav: string;
  promptText?: string;
}): Promise<Buffer> {
  await ensureGptSovits();
  touchIdle();
  const response = await fetch(url("/tts"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      text: request.text,
      text_lang: "ja",
      ref_audio_path: request.referenceWav,
      prompt_text: request.promptText ?? "",
      prompt_lang: "ja",
      text_split_method: "cut5",
      media_type: "wav",
      streaming_mode: false,
      speed_factor: 1.0,
    }),
    signal: AbortSignal.timeout(600_000),
  });
  if (!response.ok)
    throw new Error(`GPT-SoVITS: HTTP ${response.status} ${(await response.text()).slice(0, 200)}`);
  return Buffer.from(await response.arrayBuffer());
}
