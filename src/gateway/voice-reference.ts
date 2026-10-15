import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";
import { referenceClip } from "./partner-voice-clone.js";
import { projectDir } from "./voice-studio.js";

// The exact words of a studio voice's reference clip. Some cloning engines (GPT-SoVITS, Fish S2, OuteTTS) need them next to
// the clip. They are written once with Whisper on the CPU (the Style-Bert-VITS2 venv has it) and kept next to the clip as
// clone-ref.txt; a changed clip (the clip is rebuilt when uploads change) is transcribed again.

const SCRIPT = "transcribe_ref.py";

function venvPython(): string {
  return path.join(resolveOpenClawHome(), "tts", "sbv2", "venv", "Scripts", "python.exe");
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

/** Transcribes any wav (Japanese). Used by the studio voices below and by tests. */
export async function transcribeWav(wav: string, model = "medium"): Promise<string> {
  const script = path.join(resolveOpenClawHome(), "tts", SCRIPT);
  if (!(await exists(venvPython())) || !(await exists(script)))
    throw new Error("文字起こしの道具（Whisper）が、見つかりません");
  const stdout = await new Promise<string>((resolve, reject) => {
    execFile(
      venvPython(),
      [script, wav, model],
      {
        windowsHide: true,
        timeout: 300_000,
        maxBuffer: 4 * 1024 * 1024,
        env: { ...process.env, PYTHONUTF8: "1" },
      },
      (error, out, err) => {
        if (error) reject(new Error(`文字起こしに失敗しました: ${String(err).trim().slice(-200)}`));
        else resolve(String(out));
      },
    );
  });
  const line = stdout.trim().split(/\r?\n/).filter(Boolean).pop() ?? "{}";
  const text = (JSON.parse(line) as { text?: string }).text?.trim() ?? "";
  if (!text) throw new Error("手本の音声から、言葉を読み取れませんでした");
  return text;
}

/** The words of a studio voice's reference clip (cached). */
export async function referenceText(voiceId: string): Promise<string> {
  const clip = await referenceClip(voiceId);
  const cache = path.join(projectDir(voiceId), "clone-ref.txt");
  const [clipStat, cacheStat] = await Promise.all([
    fs.stat(clip),
    fs.stat(cache).catch(() => undefined),
  ]);
  if (cacheStat && cacheStat.mtimeMs >= clipStat.mtimeMs) {
    const saved = (await fs.readFile(cache, "utf-8")).trim();
    if (saved) return saved;
  }
  const text = await transcribeWav(clip);
  await fs.writeFile(cache, text, "utf-8");
  return text;
}
