import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { type IrodoriModel, irodoriInstalled, synthesizeIrodori } from "./irodori-tts.js";
import { resolveOpenClawHome } from "./partner-store.js";
import { normalizeVoiceCaption } from "./voice-caption.js";

// Voice design: a voice made from a Japanese description (Irodori-TTS VoiceDesign), with no
// recording and no real speaker. A draft is listened to first; a saved draft becomes the
// recording that voice cloning (and Irodori) speaks with. The small model answers in a few
// seconds next to the chat model; the large one is more natural but runs on the CPU then.

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

/** Keeps the description to plain text of a sensible length. */
export function sanitizeVoiceCaption(raw: string): string {
  return normalizeVoiceCaption(raw);
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

/** Makes one draft voice from a description; returns its id and the WAV to listen to. */
export async function designVoiceDraft(
  rawCaption: string,
  rawText?: string,
  model: IrodoriModel = "small",
): Promise<{ draftId: string; audio: Buffer }> {
  const caption = sanitizeVoiceCaption(rawCaption);
  if (!caption) throw new Error("声の説明を入力してください");
  if (!(await irodoriInstalled())) throw new Error("Irodori-TTS がインストールされていません");
  // The sample text may carry Irodori's emojis (breaths, moans), so it is not sanitized away.
  const text = (rawText ?? "").trim().slice(0, 200) || DEFAULT_SAMPLE_TEXT;
  await fs.mkdir(draftDir(), { recursive: true });
  await pruneDrafts();
  const draftId = `draft-${randomBytes(6).toString("hex")}`;
  const { audio } = await synthesizeIrodori({ text, caption, model });
  await fs.writeFile(draftPath(draftId), audio);
  return { draftId, audio };
}
