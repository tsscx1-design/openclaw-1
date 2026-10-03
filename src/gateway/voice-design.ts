import { execFile } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Voice design: a voice made from a Japanese description (Irodori-TTS v4.1 VoiceDesign), with no
// recording and no real speaker. A draft is listened to first; a saved draft becomes the
// recording that voice cloning imitates. ~26 s per draft on the GPU next to the chat model
// (most of it is model loading), ~80 s on CPU while a media job holds the GPU.

const IRODORI_DIR = "D:\\media-tools\\Irodori-TTS";
const IRODORI_PYTHON = path.join(IRODORI_DIR, ".venv", "Scripts", "python.exe");
const IRODORI_CHECKPOINT = "Aratako/Irodori-TTS-v4.1-Small";
const DEFAULT_SAMPLE_TEXT = "こんにちは。今日もよろしくね。いっしょに、いろんなお話をしようね。";
const DRAFT_ID = /^draft-[a-f0-9]{12}$/;
const DRAFT_MAX_AGE_MS = 24 * 60 * 60_000;

function draftDir(): string {
  return path.join(resolveOpenClawHome(), "tts", "voices", "_drafts");
}

export function draftPath(draftId: string): string {
  if (!DRAFT_ID.test(draftId)) throw new Error("Invalid draft id");
  return path.join(draftDir(), `${draftId}.wav`);
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

/** Keeps the description to plain text of a sensible length. */
export function sanitizeVoiceCaption(raw: string): string {
  return raw
    .replace(/[\r\n\t]+/g, " ")
    .replace(/[^\p{L}\p{N}\s、。・ー〜～！？!?,.()（）「」]/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 200);
}

async function pruneDrafts(): Promise<void> {
  let names: string[] = [];
  try {
    names = await fs.readdir(draftDir());
  } catch {
    return;
  }
  const now = Date.now();
  for (const name of names) {
    const file = path.join(draftDir(), name);
    const stat = await fs.stat(file).catch(() => undefined);
    if (stat && now - stat.mtimeMs > DRAFT_MAX_AGE_MS) await fs.rm(file, { force: true });
  }
}

let queue: Promise<unknown> = Promise.resolve();

/** Makes one draft voice from a description; returns its id and the WAV to listen to. */
export function designVoiceDraft(
  caption: string,
  sampleText?: string,
): Promise<{ draftId: string; audio: Buffer }> {
  const job = queue.then(() => runDesign(caption, sampleText));
  queue = job.catch(() => undefined);
  return job;
}

async function runDesign(
  rawCaption: string,
  rawText?: string,
): Promise<{ draftId: string; audio: Buffer }> {
  const caption = sanitizeVoiceCaption(rawCaption);
  if (!caption) throw new Error("声の説明を入力してください");
  if (!(await exists(IRODORI_PYTHON))) throw new Error("Irodori-TTS がインストールされていません");
  const text = sanitizeVoiceCaption(rawText ?? "") || DEFAULT_SAMPLE_TEXT;
  await fs.mkdir(draftDir(), { recursive: true });
  await pruneDrafts();
  const draftId = `draft-${randomBytes(6).toString("hex")}`;
  const output = draftPath(draftId);
  // While a media job owns the GPU (pause flag), draw the voice on the CPU instead.
  const busy = await exists(path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag"));
  const device = busy
    ? ["--model-device", "cpu", "--codec-device", "cpu", "--model-precision", "fp32"]
    : ["--model-device", "cuda", "--codec-device", "cuda", "--model-precision", "bf16"];
  await new Promise<void>((resolve, reject) => {
    const child = execFile(
      IRODORI_PYTHON,
      [
        "infer.py",
        "--hf-checkpoint",
        IRODORI_CHECKPOINT,
        "--text",
        text,
        "--caption",
        caption,
        "--no-ref",
        ...device,
        "--output-wav",
        output,
      ],
      { cwd: IRODORI_DIR, windowsHide: true, timeout: 5 * 60_000, maxBuffer: 8 * 1024 * 1024 },
      (err, _stdout, stderr) => {
        if (err) reject(new Error(`voice design failed: ${String(stderr).trim().slice(-300)}`));
        else resolve();
      },
    );
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
    } catch {
      // Priority is best-effort.
    }
  });
  return { draftId, audio: await fs.readFile(output) };
}
