import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

/** Local VOICEVOX-compatible engines (AivisSpeech speaks the same HTTP API). */
export type HttpTtsEngineId = "voicevox" | "aivis";
/** "clone" speaks with a voice-studio recording through Qwen3-TTS (partner-voice-clone.ts). */
export type TtsEngineId = HttpTtsEngineId | "clone";

export const VOICE_PROJECT_ID = /^voice-[a-z0-9-]{8,60}$/;

export type PartnerVoice = {
  engine: TtsEngineId;
  /** Style id passed as `speaker` to the engine (unused by "clone"). */
  speakerId: number;
  /** Voice-studio project whose recording "clone" imitates. */
  voiceId?: string;
  /** Display label, e.g. "No.7（アナウンス）". */
  label?: string;
  speed?: number;
  pitch?: number;
  intonation?: number;
};

export type TtsSpeakerStyle = {
  engine: TtsEngineId;
  speakerId: number;
  voiceId?: string;
  speakerName: string;
  styleName: string;
};

const ENGINES: Record<HttpTtsEngineId, { port: number; dir: string; name: string }> = {
  voicevox: { port: 50021, dir: "voicevox", name: "VOICEVOX" },
  aivis: { port: 10101, dir: "aivis", name: "AivisSpeech" },
};

const MAX_TEXT_CHARS = 400;
const ENGINE_READY_TIMEOUT_MS = 180_000;
const starting = new Map<HttpTtsEngineId, Promise<void>>();

function engineUrl(engine: HttpTtsEngineId): string {
  return `http://127.0.0.1:${ENGINES[engine].port}`;
}

async function isEngineReady(engine: HttpTtsEngineId): Promise<boolean> {
  try {
    const res = await fetch(`${engineUrl(engine)}/version`, { signal: AbortSignal.timeout(3_000) });
    return res.ok;
  } catch {
    return false;
  }
}

async function findRunExe(dir: string, depth = 3): Promise<string | undefined> {
  let entries: Array<{ name: string; isDirectory(): boolean }>;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return undefined;
  }
  const direct = entries.find((e) => !e.isDirectory() && e.name.toLowerCase() === "run.exe");
  if (direct) return path.join(dir, direct.name);
  if (depth <= 0) return undefined;
  for (const entry of entries.filter((e) => e.isDirectory())) {
    const found = await findRunExe(path.join(dir, entry.name), depth - 1);
    if (found) return found;
  }
  return undefined;
}

/** Starts the engine on demand (detached, CPU) and waits until it answers. */
export async function ensureTtsEngine(engine: HttpTtsEngineId): Promise<void> {
  if (await isEngineReady(engine)) return;
  const pending = starting.get(engine);
  if (pending) return await pending;
  const run = (async () => {
    const root = path.join(resolveOpenClawHome(), "tts", ENGINES[engine].dir);
    const exe = await findRunExe(root);
    if (!exe) {
      throw new Error(`${ENGINES[engine].name} is not installed under ${root}`);
    }
    const child = spawn(exe, ["--host", "127.0.0.1", "--port", String(ENGINES[engine].port)], {
      cwd: path.dirname(exe),
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    });
    child.unref();
    const deadline = Date.now() + ENGINE_READY_TIMEOUT_MS;
    while (Date.now() < deadline) {
      if (await isEngineReady(engine)) return;
      await new Promise((resolve) => setTimeout(resolve, 1_500));
    }
    throw new Error(`${ENGINES[engine].name} did not become ready`);
  })();
  starting.set(engine, run);
  try {
    await run;
  } finally {
    starting.delete(engine);
  }
}

/** Every style of every installed engine, for the app's voice picker. */
export async function listTtsSpeakers(): Promise<TtsSpeakerStyle[]> {
  const out: TtsSpeakerStyle[] = [];
  for (const engine of Object.keys(ENGINES) as HttpTtsEngineId[]) {
    try {
      await ensureTtsEngine(engine);
      const res = await fetch(`${engineUrl(engine)}/speakers`, {
        signal: AbortSignal.timeout(15_000),
      });
      if (!res.ok) continue;
      const speakers = (await res.json()) as Array<{
        name: string;
        styles: Array<{ id: number; name: string }>;
      }>;
      for (const speaker of speakers) {
        for (const style of speaker.styles ?? []) {
          out.push({
            engine,
            speakerId: style.id,
            speakerName: speaker.name,
            styleName: style.name,
          });
        }
      }
    } catch {
      // An engine that is not installed simply contributes no voices.
    }
  }
  return out;
}

/** Text for speech: drops action notes like （笑顔で頷く） and markdown noise. */
export function prepareSpeechText(text: string): string {
  return text
    .replace(/（[^）]*）|\([^)]*\)/g, "")
    .replace(/[*_#`>]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, MAX_TEXT_CHARS);
}

/** Synthesizes WAV audio with the engine's audio_query → synthesis flow. */
export async function synthesizeSpeech(voice: PartnerVoice, text: string): Promise<Buffer> {
  const speech = prepareSpeechText(text);
  if (!speech) throw new Error("Nothing to read aloud");
  if (voice.engine === "clone")
    throw new Error("Clone voices are synthesized by partner-voice-clone");
  await ensureTtsEngine(voice.engine);
  const base = engineUrl(voice.engine);
  const queryRes = await fetch(
    `${base}/audio_query?speaker=${voice.speakerId}&text=${encodeURIComponent(speech)}`,
    { method: "POST", signal: AbortSignal.timeout(60_000) },
  );
  if (!queryRes.ok) throw new Error(`audio_query failed: HTTP ${queryRes.status}`);
  const query = (await queryRes.json()) as Record<string, unknown>;
  if (voice.speed) query.speedScale = voice.speed;
  if (voice.pitch !== undefined) query.pitchScale = voice.pitch;
  if (voice.intonation !== undefined) query.intonationScale = voice.intonation;
  const synthRes = await fetch(`${base}/synthesis?speaker=${voice.speakerId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(query),
    signal: AbortSignal.timeout(120_000),
  });
  if (!synthRes.ok) throw new Error(`synthesis failed: HTTP ${synthRes.status}`);
  return Buffer.from(await synthRes.arrayBuffer());
}

export function sanitizePartnerVoice(raw: unknown): PartnerVoice | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const v = raw as Record<string, unknown>;
  if (
    (v.engine !== "voicevox" && v.engine !== "aivis" && v.engine !== "clone") ||
    typeof v.speakerId !== "number" ||
    !Number.isInteger(v.speakerId)
  ) {
    return undefined;
  }
  // A clone voice must point at a voice-studio project (recorded with the speaker's consent).
  const voiceId =
    typeof v.voiceId === "string" && VOICE_PROJECT_ID.test(v.voiceId) ? v.voiceId : undefined;
  if (v.engine === "clone" && !voiceId) return undefined;
  const clamp = (value: unknown, min: number, max: number) =>
    typeof value === "number" && Number.isFinite(value)
      ? Math.min(max, Math.max(min, value))
      : undefined;
  const voice: PartnerVoice = {
    engine: v.engine,
    speakerId: v.speakerId,
    voiceId: v.engine === "clone" ? voiceId : undefined,
    label: typeof v.label === "string" ? v.label.slice(0, 80) : undefined,
    speed: clamp(v.speed, 0.5, 2),
    pitch: clamp(v.pitch, -0.15, 0.15),
    intonation: clamp(v.intonation, 0, 2),
  };
  return Object.fromEntries(
    Object.entries(voice).filter(([, x]) => x !== undefined),
  ) as PartnerVoice;
}
