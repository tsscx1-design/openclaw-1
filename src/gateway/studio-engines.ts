import { execFile } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { gptSovitsInstalled, synthesizeGptSovits } from "./gpt-sovits-tts.js";
import { resolveOpenClawHome } from "./partner-store.js";
import {
  prepareSpeechText,
  type PartnerVoice,
  type StudioEngineId,
  type TtsSpeakerStyle,
} from "./partner-tts.js";
import { referenceClip } from "./partner-voice-clone.js";
import { referenceText } from "./voice-reference.js";
import { listVoiceProjects, loadVoiceProject } from "./voice-studio.js";

// Cloning engines that imitate a voice-studio recording like "clone" (Qwen3-TTS) does, but need the exact words of the
// reference too: GPT-SoVITS, OuteTTS and Fish S2 (local). They run on the CPU (the chat model holds the GPU), one voice
// request at a time. Nothing here is real time: GPT-SoVITS and OuteTTS take tens of seconds, Fish S2 about 20 s per second
// of speech on a CPU, so they suit pre-rendered lines (voice dramas) better than live chat.

const MODELS = "D:\\llm-models\\tts";
const OUTETTS_ROOT = "D:\\media-tools\\outetts";
const OUTETTS_MODEL = path.join(MODELS, "outetts", "OuteTTS-1.0-0.6B-Q8_0.gguf");
const FISH_DIR = path.join(MODELS, "fish-s2");
const FISH_EXE = path.join(FISH_DIR, "src", "s2.cpp", "build-cpu", "Release", "s2.exe");
const FISH_MODEL = path.join(FISH_DIR, "weights", "s2-pro-q8_0.gguf");
const FISH_TOKENIZER = path.join(FISH_DIR, "weights", "tokenizer.json");

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

const outettsPython = (): string => path.join(OUTETTS_ROOT, "venv", "Scripts", "python.exe");
const outettsScript = (): string => path.join(resolveOpenClawHome(), "tts", "outetts_run.py");

const CRISPASR_EXE = "D:\\media-tools\\crispasr\\build\\bin\\crispasr.exe";
const VOXCPM2_MODEL = path.join(MODELS, "voxcpm2", "voxcpm2-q8_0.gguf");

const installed: Record<Exclude<StudioEngineId, "clone" | "irodori">, () => Promise<boolean>> = {
  gptsovits: gptSovitsInstalled,
  outetts: async () =>
    (await exists(outettsPython())) &&
    (await exists(outettsScript())) &&
    (await exists(OUTETTS_MODEL)),
  fishlocal: async () =>
    (await exists(FISH_EXE)) && (await exists(FISH_MODEL)) && (await exists(FISH_TOKENIZER)),
  voxcpm2: async () => (await exists(CRISPASR_EXE)) && (await exists(VOXCPM2_MODEL)),
};

const LABELS: Record<keyof typeof installed, { suffix: string; style: string }> = {
  gptsovits: { suffix: "GPT-SoVITS", style: "録音をまねる（CPU・数十秒かかる）" },
  outetts: { suffix: "OuteTTS", style: "録音をまねる（CPU・数十秒かかる）" },
  fishlocal: {
    suffix: "Fish S2",
    style: "録音をまねる（CPU・遅い・ボイスドラマ向け。研究・非商用のみ）",
  },
  voxcpm2: { suffix: "VoxCPM2", style: "録音をまねる（CPU・一文で約30秒。同意済みの声だけ）" },
};

/** Studio recordings that these engines can imitate (shown next to the other engines' voices). */
export async function listStudioEngineVoices(): Promise<TtsSpeakerStyle[]> {
  const projects = (await listVoiceProjects()).filter(
    (p) => p.files.length > 0 && p.extractionMode !== "pending",
  );
  const out: TtsSpeakerStyle[] = [];
  for (const id of Object.keys(installed) as Array<keyof typeof installed>) {
    if (!(await installed[id]())) continue;
    for (const project of projects) {
      // VoxCPM2 (CrispASR) clones only with the speaker's permission declared, so only consented voices are offered.
      if (id === "voxcpm2" && !project.consent) continue;
      out.push({
        engine: id,
        speakerId: 0,
        voiceId: project.id,
        speakerName: `${project.name}（${LABELS[id].suffix}）`,
        styleName: LABELS[id].style,
      });
    }
  }
  return out;
}

function run(
  exe: string,
  args: string[],
  timeoutMs: number,
  env?: NodeJS.ProcessEnv,
): Promise<void> {
  return new Promise((resolve, reject) => {
    execFile(
      exe,
      args,
      {
        windowsHide: true,
        timeout: timeoutMs,
        maxBuffer: 16 * 1024 * 1024,
        env: env ?? process.env,
      },
      (error, _stdout, stderr) => {
        if (error)
          reject(
            new Error(
              `${path.basename(exe)} failed: ${String(stderr).trim().slice(-300) || error.message}`,
            ),
          );
        else resolve();
      },
    );
  });
}

let queue: Promise<unknown> = Promise.resolve();

/** One request at a time: these engines are heavy and share the CPU. */
export function speakWithStudioEngine(voice: PartnerVoice, text: string): Promise<Buffer> {
  const job = queue.then(() => synthesize(voice, text));
  queue = job.catch(() => undefined);
  return job;
}

async function synthesize(voice: PartnerVoice, text: string): Promise<Buffer> {
  const engine = voice.engine as keyof typeof installed;
  const speech = prepareSpeechText(text);
  if (!speech) throw new Error("読み上げる文がありません");
  if (!voice.voiceId) throw new Error("この声には録音がありません");
  if (!installed[engine] || !(await installed[engine]()))
    throw new Error(`${LABELS[engine]?.suffix ?? engine} がインストールされていません`);
  const clip = await referenceClip(voice.voiceId);
  const words = await referenceText(voice.voiceId);

  if (engine === "gptsovits")
    return synthesizeGptSovits({ text: speech, referenceWav: clip, promptText: words });
  if (engine === "voxcpm2" && !(await loadVoiceProject(voice.voiceId)).consent)
    throw new Error("VoxCPM2 は、同意の確認を済ませた声だけで使えます");

  const stem = path.join(os.tmpdir(), `openclaw-studio-${randomBytes(6).toString("hex")}`);
  const output = `${stem}.wav`;
  try {
    if (engine === "outetts") {
      const textFile = `${stem}.txt`;
      await fs.writeFile(textFile, speech, "utf-8");
      await run(
        outettsPython(),
        [
          outettsScript(),
          "--ref",
          clip,
          "--ref-text",
          words,
          "--text-file",
          textFile,
          "--out",
          output,
        ],
        900_000,
        { ...process.env, PYTHONUTF8: "1" },
      );
      await fs.rm(textFile, { force: true });
    } else if (engine === "voxcpm2") {
      // The "synthetic voice" notice CrispASR prepends is left in; --i-have-rights declares the speaker's consent (checked above).
      await run(
        CRISPASR_EXE,
        [
          "--backend",
          "voxcpm2-tts",
          "-m",
          VOXCPM2_MODEL,
          "--tts",
          speech,
          "--voice",
          clip,
          "--i-have-rights",
          "--tts-output",
          output,
        ],
        900_000,
      );
    } else {
      await run(
        FISH_EXE,
        [
          "-m",
          FISH_MODEL,
          "-t",
          FISH_TOKENIZER,
          "-text",
          speech,
          "-pa",
          clip,
          "-pt",
          words,
          "-o",
          output,
          "-threads",
          String(Math.max(2, Math.min(8, os.cpus().length - 2))),
          "-max-tokens",
          "1500",
        ],
        3_000_000,
      );
    }
    return await fs.readFile(output);
  } finally {
    await fs.rm(output, { force: true });
  }
}
