import { execFile } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";
import { prepareSpeechText, type PartnerVoice, type TtsSpeakerStyle } from "./partner-tts.js";
import { listVoiceProjects, loadVoiceProject, projectDir } from "./voice-studio.js";

// Voice cloning for partners: Qwen3-TTS 0.6B Base (GGUF) through upstream llama.cpp's llama-tts.
// The reference is a voice-studio recording, so only voices recorded with the speaker's consent
// can be imitated. On the RTX 4060 it fits next to the Bonsai chat model: 10 s of speech in
// ~3 s (~5 s with start-up), measured 2026-10-03. While a media job holds the GPU it runs on CPU.

const TTS_DIR = "D:\\llm-models\\tts";
const TTS_MODEL = path.join(TTS_DIR, "Qwen3-TTS-12Hz-0.6B-Base.Q8_0.gguf");
const TTS_MMPROJ = path.join(TTS_DIR, "Qwen3-TTS-12Hz-0.6B-Base.mmproj-Q8_0.gguf");
const FFMPEG =
  "D:\\media-tools\\Wan2GP\\venv\\Lib\\site-packages\\imageio_ffmpeg\\binaries\\ffmpeg-win-x86_64-v7.1.exe";
/** Reference length: a few clean seconds clone well; longer only slows the speaker encoder. */
const REFERENCE_SECONDS = 10;
const REFERENCE_FILE = "clone-ref.wav";

function llamaTts(): string {
  return path.join(resolveOpenClawHome(), "llamacpp-mainline", "llama-tts.exe");
}

function pauseFlag(): string {
  return path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag");
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

function run(exe: string, args: string[], timeoutMs: number): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = execFile(
      exe,
      args,
      { windowsHide: true, timeout: timeoutMs, maxBuffer: 8 * 1024 * 1024 },
      (err, _stdout, stderr) => {
        if (err)
          reject(new Error(`${path.basename(exe)} failed: ${String(stderr).trim().slice(-300)}`));
        else resolve();
      },
    );
    // The gateway runs below normal priority; a lowered child would load the model slowly.
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
    } catch {
      // Priority is best-effort.
    }
  });
}

/** Voice-studio voices with at least one recording, listed next to the engine voices. */
export async function listCloneVoices(): Promise<TtsSpeakerStyle[]> {
  if (!(await exists(llamaTts())) || !(await exists(TTS_MODEL))) return [];
  return (await listVoiceProjects())
    .filter((project) => project.files.length > 0)
    .map((project) => ({
      engine: "clone",
      speakerId: 0,
      voiceId: project.id,
      speakerName: `${project.name}（声まね）`,
      styleName: "Qwen3-TTS",
    }));
}

/** A short mono 24 kHz clip cut from the voice's first recording, rebuilt when uploads change. */
async function referenceClip(voiceId: string): Promise<string> {
  const project = await loadVoiceProject(voiceId);
  const source = project.files[0];
  if (!source) throw new Error("この声には録音がありません");
  const dir = projectDir(voiceId);
  const raw = path.join(dir, "raw", source.name);
  const clip = path.join(dir, REFERENCE_FILE);
  const clipStat = await fs.stat(clip).catch(() => undefined);
  if (clipStat && clipStat.mtimeMs >= project.updatedAt) return clip;
  await run(
    FFMPEG,
    [
      "-y",
      "-i",
      raw,
      // Skip leading silence so the clip is all voice.
      "-af",
      "silenceremove=start_periods=1:start_threshold=-45dB",
      "-t",
      String(REFERENCE_SECONDS),
      "-ac",
      "1",
      "-ar",
      "24000",
      clip,
    ],
    60_000,
  );
  return clip;
}

let queue: Promise<unknown> = Promise.resolve();

/** One synthesis at a time, so two replies never compete for the same few hundred MB of VRAM. */
export function synthesizeCloneSpeech(voice: PartnerVoice, text: string): Promise<Buffer> {
  const job = queue.then(() => synthesize(voice, text));
  queue = job.catch(() => undefined);
  return job;
}

async function synthesize(voice: PartnerVoice, text: string): Promise<Buffer> {
  const speech = prepareSpeechText(text);
  if (!speech) throw new Error("Nothing to read aloud");
  if (!voice.voiceId) throw new Error("No voice recording is set for this clone voice");
  if (!(await exists(llamaTts())) || !(await exists(TTS_MODEL)))
    throw new Error("声まね（Qwen3-TTS）がインストールされていません");
  const reference = await referenceClip(voice.voiceId);
  const stem = path.join(os.tmpdir(), `openclaw-clone-${randomBytes(6).toString("hex")}`);
  const promptFile = `${stem}.txt`;
  const output = `${stem}.wav`;
  // The text goes through a UTF-8 file: Japanese on the command line is not safe on Windows.
  await fs.writeFile(promptFile, speech, "utf-8");
  // A media job owns the GPU while its pause flag exists; speak on the CPU then (~1.6x real time).
  const device = (await exists(pauseFlag()))
    ? ["-ngl", "0", "--device", "none", "-mmdev", "none", "-t", "8"]
    : ["-ngl", "99"];
  try {
    await run(
      llamaTts(),
      [
        "-m",
        TTS_MODEL,
        "--mmproj",
        TTS_MMPROJ,
        "-f",
        promptFile,
        "--tts-lang",
        "ja",
        "--tts-speaker-file",
        reference,
        "-c",
        "2048",
        ...device,
        "-o",
        output,
      ],
      180_000,
    );
    return await fs.readFile(output);
  } finally {
    await fs.rm(promptFile, { force: true });
    await fs.rm(output, { force: true });
  }
}
