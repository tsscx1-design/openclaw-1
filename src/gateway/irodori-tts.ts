import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";
import { mediaProcessEnv } from "./media-gpu.js";
import { resolveOpenClawHome } from "./partner-store.js";
import { normalizeVoiceCaption } from "./voice-caption.js";

// Irodori-TTS (Japanese, emoji-driven delivery: 🥵 moans, 😮‍💨 breaths, 👂 whispers, ...) through a
// resident worker from scripts/media/external so the model loads once.
// "small" (v4.1-Small) sits next to the chat model on the GPU and reads partner replies;
// "large" (v4-Large, 3.3B, ~7 GB in bf16) needs the GPU to itself, so it is used by media jobs
// (which take the GPU) or on the CPU.

export const IRODORI_DIR = "D:\\media-tools\\Irodori-TTS";
const IRODORI_PYTHON = path.join(IRODORI_DIR, ".venv", "Scripts", "python.exe");
function serverPath(): string {
  const root = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
  if (!root) {
    throw new Error("OpenClaw の実行場所を確認できません。");
  }
  return path.join(root, "scripts", "media", "external", "irodori_openclaw_server.py");
}

export type IrodoriModel = "small" | "large" | "fast";
export const IRODORI_CHECKPOINTS: Record<IrodoriModel, string> = {
  small: "Aratako/Irodori-TTS-v4.1-Small",
  large: "Aratako/Irodori-TTS-v4-Large",
  /** MeanFlow distilled v4.1-Small: 4 steps instead of 40, about five times quicker (0.65 s a line against 3.2 s measured). */
  fast: "Aratako/Irodori-TTS-v4.1-Small-MF",
};

/** The emojis the model reads as delivery and non-verbal sounds (from its emoji palette). */
export const IRODORI_EMOJIS: Array<{ emoji: string; label: string }> = [
  { emoji: "👂", label: "囁き" },
  { emoji: "😮‍💨", label: "吐息・溜息" },
  { emoji: "🌬️", label: "息切れ・荒い息" },
  { emoji: "🥵", label: "喘ぎ・うめき" },
  { emoji: "😮", label: "息をのむ" },
  { emoji: "😖", label: "苦しげ" },
  { emoji: "😱", label: "悲鳴・絶叫" },
  { emoji: "👅", label: "舐める音・水音" },
  { emoji: "💋", label: "リップノイズ" },
  { emoji: "🥤", label: "飲み込む" },
  { emoji: "🤐", label: "口を塞ぐ" },
  { emoji: "😏", label: "からかう・甘える" },
  { emoji: "🫣", label: "照れ" },
  { emoji: "🙏", label: "懇願" },
  { emoji: "🫶", label: "優しく" },
  { emoji: "🥺", label: "震え声" },
  { emoji: "😭", label: "泣き声" },
  { emoji: "🤭", label: "笑い" },
  { emoji: "😆", label: "喜び" },
  { emoji: "😪", label: "眠そう・気だるげ" },
  { emoji: "😌", label: "安堵・満足げ" },
  { emoji: "😠", label: "怒り・拗ねる" },
  { emoji: "😰", label: "慌てる・動揺" },
  { emoji: "⏸️", label: "間" },
  { emoji: "🐢", label: "ゆっくり" },
  { emoji: "⏩", label: "早口" },
];

const EMOJI_LIST = IRODORI_EMOJIS.map((e) => `${e.emoji}=${e.label}`).join(" ");
export const IRODORI_EMOJI_GUIDE = `Irodori の声は、セリフの中に絵文字を入れると演技が変わる: ${EMOJI_LIST}。行ごとの演技は [囁き] [喘ぎ:強] [吐息:弱] のように角括弧で書いてもよい（弱・中・強で強さ）。絵文字は声を出したい位置の直前か直後に置く（例: 「ん…😮‍💨 だめぇ…🥵」）。`;

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function irodoriInstalled(): Promise<boolean> {
  return (await exists(IRODORI_PYTHON)) && (await exists(serverPath()));
}

/** True once the model's weights are in the Hugging Face cache (the large one is ~13 GB). */
export async function irodoriModelReady(model: IrodoriModel): Promise<boolean> {
  const dir = path.join(
    os.homedir(),
    ".cache",
    "huggingface",
    "hub",
    `models--${IRODORI_CHECKPOINTS[model].replace("/", "--")}`,
    "snapshots",
  );
  try {
    for (const snap of await fs.readdir(dir)) {
      if (await exists(path.join(dir, snap, "model.safetensors"))) return true;
    }
  } catch {
    // Not downloaded.
  }
  return false;
}

/**
 * Delivery written per line: [囁き] [喘ぎ:強] [吐息:弱] and so on become Irodori's emojis. The word
 * after the colon (弱 / 中 / 強, or 1 to 3) says how many times the emoji is repeated; a strong
 * direction also closes the line with it. Unknown words are dropped (they are not read aloud).
 */
const DIRECTION_EMOJI: Record<string, string> = {
  囁き: "👂",
  ささやき: "👂",
  吐息: "😮‍💨",
  溜息: "😮‍💨",
  ため息: "😮‍💨",
  荒い息: "🌬️",
  息切れ: "🌬️",
  喘ぎ: "🥵",
  あえぎ: "🥵",
  うめき: "🥵",
  悲鳴: "😱",
  絶叫: "😱",
  息をのむ: "😮",
  苦しげ: "😖",
  水音: "👅",
  舐める: "👅",
  リップ: "💋",
  飲み込む: "🥤",
  口を塞ぐ: "🤐",
  からかい: "😏",
  甘え: "😏",
  照れ: "🫣",
  懇願: "🙏",
  優しく: "🫶",
  震え: "🥺",
  泣き: "😭",
  笑い: "🤭",
  喜び: "😆",
  気だるげ: "😪",
  満足: "😌",
  怒り: "😠",
  慌て: "😰",
  間: "⏸️",
  ゆっくり: "🐢",
  早口: "⏩",
};
const DIRECTION_LEVEL: Record<string, number> = { 弱: 1, 中: 2, 強: 3, "1": 1, "2": 2, "3": 3 };

export function applyVoiceDirections(text: string): string {
  let closing = "";
  const out = text.replace(
    /[[【]\s*([^\]】:：]+?)\s*(?:[:：]\s*(弱|中|強|[1-3]))?\s*[\]】]/g,
    (_all, word: string, level?: string) => {
      const emoji = DIRECTION_EMOJI[word];
      if (!emoji) return "";
      const n = DIRECTION_LEVEL[level ?? ""] ?? 1;
      if (n >= 3) closing += emoji;
      return emoji.repeat(n);
    },
  );
  return out + closing;
}

/** Text Irodori reads: keeps emojis, drops stage directions and markdown. */
export function prepareIrodoriText(text: string): string {
  return applyVoiceDirections(text)
    .replace(/（[^）]*）|\([^)]*\)/g, "")
    .replace(/[*_#`>]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 300);
}

function pauseFlag(): string {
  return path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag");
}

/**
 * One resident worker per lane. Lane 0 is what every caller used before; lane 1 is a second worker
 * on the other GPU, started only when a caller spreads its lines over both GPUs (cudaDevice set).
 */
type WorkerSlot = {
  worker?: ReturnType<typeof spawn>;
  ready?: Promise<void>;
  /** "default" (no GPU chosen) or the GPU the worker was started on. */
  key?: string;
};
const LANES = 2;
const slots: WorkerSlot[] = Array.from({ length: LANES }, () => ({}));
let idleTimer: NodeJS.Timeout | undefined;
const pending = new Map<
  string,
  { resolve: (seed: number) => void; reject: (err: Error) => void; lane: number }
>();
/** The worker keeps a model in VRAM; let it go after a quiet while. */
const IDLE_MS = 10 * 60_000;

function startWorker(lane: number, cudaDevice?: string): Promise<void> {
  const slot = slots[lane] ?? slots[0]!;
  const key = cudaDevice ?? "default";
  if (slot.worker && slot.key !== key) stopSlot(slot);
  if (slot.worker && slot.ready) return slot.ready;
  const baseEnv = { PYTHONIOENCODING: "utf-8", HF_HUB_DISABLE_SYMLINKS_WARNING: "1" };
  const child = spawn(IRODORI_PYTHON, [serverPath()], {
    cwd: IRODORI_DIR,
    windowsHide: true,
    stdio: ["pipe", "pipe", "pipe"],
    env: cudaDevice ? mediaProcessEnv(baseEnv, cudaDevice) : { ...process.env, ...baseEnv },
  });
  try {
    if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
  } catch {
    // Priority is best-effort.
  }
  slot.worker = child;
  slot.key = key;
  let stderrTail = "";
  child.stderr?.on("data", (chunk) => {
    stderrTail = (stderrTail + String(chunk)).slice(-1_500);
  });
  slot.ready = new Promise<void>((resolve, reject) => {
    let buffer = "";
    child.stdout?.setEncoding("utf-8");
    child.stdout?.on("data", (chunk: string) => {
      buffer += chunk;
      let nl: number;
      while ((nl = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, nl).trim();
        buffer = buffer.slice(nl + 1);
        if (line === "READY") {
          resolve();
          continue;
        }
        const m = /^(\S+) (RESULT|ERROR) ?(.*)$/.exec(line);
        const job = m ? pending.get(m[1] ?? "") : undefined;
        if (!m || !job) continue;
        pending.delete(m[1] ?? "");
        if (m[2] === "RESULT") job.resolve(Number((m[3] ?? "").split(" ").pop()) || 0);
        else job.reject(new Error(m[3] || "Irodori の読み上げに失敗しました"));
      }
    });
    child.on("exit", (code) => {
      const err = new Error(`Irodori worker exited (${code}): ${stderrTail.trim().slice(-300)}`);
      reject(err);
      for (const [id, job] of [...pending]) {
        if (job.lane !== lane) continue;
        job.reject(err);
        pending.delete(id);
      }
      if (slot.worker === child) {
        slot.worker = undefined;
        slot.ready = undefined;
        slot.key = undefined;
      }
    });
    child.on("error", reject);
  });
  return slot.ready;
}

function stopSlot(slot: WorkerSlot): void {
  const child = slot.worker;
  slot.worker = undefined;
  slot.ready = undefined;
  slot.key = undefined;
  if (!child) return;
  child.stdin?.end();
  setTimeout(() => child.kill(), 10_000).unref();
}

/** Ends the workers (and frees their VRAM): media jobs call this before taking the GPU. */
export function stopIrodoriWorker(): void {
  if (idleTimer) clearTimeout(idleTimer);
  for (const slot of slots) stopSlot(slot);
}

const queues: Array<Promise<unknown>> = Array.from({ length: LANES }, () => Promise.resolve());

export type IrodoriRequest = {
  text: string;
  model?: IrodoriModel;
  /** Describes the voice (voice design) when there is no reference recording. */
  caption?: string;
  /** A recording of the voice to speak with (designed draft or voice-studio clip). */
  referenceWav?: string;
  seed?: number;
  /** Force a device; by default the GPU unless a media job holds it. */
  device?: "cuda" | "cpu";
  /**
   * Which worker speaks (0 or 1). Requests of one lane run one at a time, the two lanes run side by
   * side. Give lane 1 a cudaDevice of its own, or it shares lane 0's GPU.
   */
  lane?: number;
  /** The GPU (UUID) this lane's worker runs on; without it the worker takes the default GPU. */
  cudaDevice?: string;
};

/** One line of speech as WAV, one request at a time per lane. */
export function synthesizeIrodori(req: IrodoriRequest): Promise<{ audio: Buffer; seed: number }> {
  const lane = Math.min(LANES - 1, Math.max(0, Math.trunc(req.lane ?? 0)));
  const job = (queues[lane] ?? Promise.resolve()).then(() => runIrodori(req, lane));
  queues[lane] = job.catch(() => undefined);
  return job;
}

async function runIrodori(
  req: IrodoriRequest,
  lane: number,
): Promise<{ audio: Buffer; seed: number }> {
  const caption = req.caption === undefined ? undefined : normalizeVoiceCaption(req.caption);
  if (!(await irodoriInstalled())) throw new Error("Irodori-TTS がインストールされていません");
  const text = prepareIrodoriText(req.text);
  if (!text) throw new Error("読み上げる文がありません");
  let model = req.model ?? "small";
  if (model === "large" && !(await irodoriModelReady("large"))) model = "small";
  // A media job holds the GPU while the pause flag exists; the large model also does not fit
  // next to the chat model. Both cases run on the CPU unless the caller owns the GPU.
  const gpuBusy = await exists(pauseFlag());
  const device = req.device ?? (gpuBusy || model === "large" ? "cpu" : "cuda");
  await startWorker(lane, req.cudaDevice);
  const worker = slots[lane]?.worker;
  if (idleTimer) clearTimeout(idleTimer);
  const output = path.join(os.tmpdir(), `openclaw-irodori-${randomBytes(6).toString("hex")}.wav`);
  const id = randomBytes(4).toString("hex");
  const seed = await new Promise<number>((resolve, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error("Irodori の読み上げが時間切れになりました"));
    }, 10 * 60_000);
    pending.set(id, {
      lane,
      resolve: (s) => {
        clearTimeout(timer);
        resolve(s);
      },
      reject: (e) => {
        clearTimeout(timer);
        reject(e);
      },
    });
    worker?.stdin?.write(
      `${JSON.stringify({
        id,
        checkpoint: IRODORI_CHECKPOINTS[model],
        text,
        caption: caption || null,
        ref_wav: req.referenceWav ?? null,
        seed: req.seed ?? null,
        device,
        output,
      })}\n`,
    );
  });
  idleTimer = setTimeout(stopIrodoriWorker, IDLE_MS);
  idleTimer.unref();
  try {
    return { audio: await fs.readFile(output), seed };
  } finally {
    await fs.rm(output, { force: true }).catch(() => undefined);
  }
}
