import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { readJsonFile, resolveOpenClawHome, writeJsonFile } from "./partner-store.js";
import { ensureTtsEngine, VOICE_PROJECT_ID as VOICE_ID } from "./partner-tts.js";

/**
 * Voice studio: users upload recordings of their own (or consenting) voice; training runs in a
 * night window with the main LLM paused so the GPU is free, then the voice joins AivisSpeech.
 */
export type VoiceStatus = "uploading" | "queued" | "training" | "ready" | "failed";

export type VoiceProject = {
  id: string;
  name: string;
  consent: true;
  status: VoiceStatus;
  createdAt: number;
  updatedAt: number;
  files: Array<{ name: string; bytes: number }>;
  step?: string;
  error?: string;
  /** AivisSpeech style id once installed. */
  speakerId?: number;
  /** Picture shown next to the voice (file name inside the project folder). */
  icon?: string;
  /** "design" = made from a text description (no real speaker), else recordings. */
  source?: "recording" | "design";
  /** The description a designed voice was made from. */
  caption?: string;
};

export type VoiceStudioSettings = { windowStartHour: number; windowEndHour: number };

const DEFAULT_SETTINGS: VoiceStudioSettings = { windowStartHour: 1, windowEndHour: 6 };
const MAX_UPLOAD_BYTES = 300 * 1024 * 1024;
const ALLOWED_EXT = /\.(mp3|wav|m4a|aac|ogg|flac|opus|webm|mp4)$/i;

function studioRoot(): string {
  return path.join(resolveOpenClawHome(), "tts", "voices");
}

export function projectDir(id: string): string {
  if (!VOICE_ID.test(id)) throw new Error(`Invalid voice id: ${id.slice(0, 40)}`);
  return path.join(studioRoot(), id);
}

async function saveProject(project: VoiceProject): Promise<void> {
  project.updatedAt = Date.now();
  await writeJsonFile(path.join(projectDir(project.id), "meta.json"), project);
}

export async function loadVoiceProject(id: string): Promise<VoiceProject> {
  const meta = await readJsonFile<VoiceProject | null>(
    path.join(projectDir(id), "meta.json"),
    null,
  );
  if (!meta) throw new Error(`Voice not found: ${id}`);
  return meta;
}

export async function listVoiceProjects(): Promise<VoiceProject[]> {
  let names: string[] = [];
  try {
    names = await fs.readdir(studioRoot());
  } catch {
    return [];
  }
  const projects: VoiceProject[] = [];
  for (const name of names.filter((n) => VOICE_ID.test(n))) {
    const meta = await readJsonFile<VoiceProject | null>(
      path.join(studioRoot(), name, "meta.json"),
      null,
    );
    if (meta) projects.push(meta);
  }
  return projects.sort((a, b) => b.createdAt - a.createdAt);
}

export async function createVoiceProject(name: string, consent: unknown): Promise<VoiceProject> {
  // Only the speaker (or someone with their permission) may turn a voice into a model.
  if (consent !== true)
    throw new Error(
      "Consent is required: the recordings must be your own voice or used with the speaker's permission",
    );
  const trimmed = name.trim().slice(0, 40);
  if (!trimmed) throw new Error("Voice name is required");
  const now = Date.now();
  const project: VoiceProject = {
    id: `voice-${randomUUID().slice(0, 18)}`,
    name: trimmed,
    consent: true,
    status: "uploading",
    createdAt: now,
    updatedAt: now,
    files: [],
  };
  await fs.mkdir(path.join(projectDir(project.id), "raw"), { recursive: true });
  await saveProject(project);
  return project;
}

/** Appends one base64 chunk; chunk 0 starts the file. Chunks keep each Gateway frame small. */
export async function appendVoiceUpload(params: {
  id: string;
  fileName: string;
  chunkIndex: number;
  chunkCount: number;
  dataBase64: string;
}): Promise<VoiceProject> {
  const project = await loadVoiceProject(params.id);
  if (project.status !== "uploading" && project.status !== "failed")
    throw new Error("This voice no longer accepts uploads");
  const safeName = path
    .basename(params.fileName)
    .replace(/[^\w.\-ぁ-んァ-ン一-龥]/g, "_")
    .slice(0, 80);
  if (!ALLOWED_EXT.test(safeName)) throw new Error("Unsupported audio format");
  const target = path.join(projectDir(project.id), "raw", safeName);
  const data = Buffer.from(params.dataBase64, "base64");
  if (params.chunkIndex === 0) {
    await fs.writeFile(target, data);
  } else {
    await fs.appendFile(target, data);
  }
  const size = (await fs.stat(target)).size;
  const total =
    project.files.filter((f) => f.name !== safeName).reduce((sum, f) => sum + f.bytes, 0) + size;
  if (total > MAX_UPLOAD_BYTES) {
    await fs.rm(target, { force: true });
    throw new Error("Uploads exceed 300MB for one voice");
  }
  project.files = [
    ...project.files.filter((f) => f.name !== safeName),
    { name: safeName, bytes: size },
  ];
  project.status = "uploading";
  await saveProject(project);
  return project;
}

export async function queueVoiceTraining(id: string): Promise<VoiceProject> {
  const project = await loadVoiceProject(id);
  if (project.files.length === 0) throw new Error("Upload at least one recording first");
  project.status = "queued";
  project.error = undefined;
  project.step = "夜間の学習待ち";
  await saveProject(project);
  return project;
}

export async function renameVoiceProject(id: string, name: string): Promise<VoiceProject> {
  const trimmed = name.trim().slice(0, 40);
  if (!trimmed) throw new Error("Voice name is required");
  const project = await loadVoiceProject(id);
  project.name = trimmed;
  await saveProject(project);
  return project;
}

const ICON_TYPES: Array<{ ext: string; test: (b: Buffer) => boolean }> = [
  { ext: ".jpg", test: (b) => b[0] === 0xff && b[1] === 0xd8 },
  { ext: ".png", test: (b) => b.subarray(0, 4).toString("hex") === "89504e47" },
  { ext: ".webp", test: (b) => b.subarray(8, 12).toString("latin1") === "WEBP" },
];
const MAX_ICON_BYTES = 1024 * 1024;

/** Stores the voice's picture (JPEG/PNG/WebP, ≤1 MB; the app sends a small square). */
export async function setVoiceIcon(id: string, imageBase64: string): Promise<VoiceProject> {
  const project = await loadVoiceProject(id);
  const data = Buffer.from(imageBase64, "base64");
  if (data.length === 0 || data.length > MAX_ICON_BYTES) throw new Error("Icon must be under 1 MB");
  const type = ICON_TYPES.find((t) => t.test(data));
  if (!type) throw new Error("Icon must be a JPEG, PNG or WebP image");
  if (project.icon) await fs.rm(path.join(projectDir(id), project.icon), { force: true });
  project.icon = `icon${type.ext}`;
  await fs.writeFile(path.join(projectDir(id), project.icon), data);
  await saveProject(project);
  return project;
}

/** The voice's picture as base64, for lists in the app. */
export async function readVoiceIcon(project: VoiceProject): Promise<string | undefined> {
  if (!project.icon) return undefined;
  try {
    return (await fs.readFile(path.join(projectDir(project.id), project.icon))).toString("base64");
  } catch {
    return undefined;
  }
}

/**
 * Turns a voice made from a text description into a studio voice. There is no real speaker,
 * so no consent is involved; the sample becomes the recording that voice cloning imitates.
 */
export async function createDesignedVoice(
  name: string,
  caption: string,
  sampleWav: string,
): Promise<VoiceProject> {
  const project = await createVoiceProject(name, true);
  const target = path.join(projectDir(project.id), "raw", "design.wav");
  await fs.copyFile(sampleWav, target);
  project.files = [{ name: "design.wav", bytes: (await fs.stat(target)).size }];
  project.source = "design";
  project.caption = caption.slice(0, 200);
  await saveProject(project);
  return project;
}

export async function deleteVoiceProject(id: string): Promise<void> {
  const project = await loadVoiceProject(id);
  if (project.status === "training")
    throw new Error("Training is running; try again after it finishes");
  await fs.rm(projectDir(id), { recursive: true, force: true });
}

function settingsPath(): string {
  return path.join(studioRoot(), "settings.json");
}

export async function loadVoiceStudioSettings(): Promise<VoiceStudioSettings> {
  const raw = await readJsonFile<Partial<VoiceStudioSettings>>(settingsPath(), {});
  const hour = (v: unknown, fallback: number) =>
    typeof v === "number" && v >= 0 && v <= 23 ? Math.floor(v) : fallback;
  return {
    windowStartHour: hour(raw.windowStartHour, DEFAULT_SETTINGS.windowStartHour),
    windowEndHour: hour(raw.windowEndHour, DEFAULT_SETTINGS.windowEndHour),
  };
}

export async function saveVoiceStudioSettings(
  patch: Partial<VoiceStudioSettings>,
): Promise<VoiceStudioSettings> {
  const next = { ...(await loadVoiceStudioSettings()), ...patch };
  await writeJsonFile(settingsPath(), next);
  return await loadVoiceStudioSettings();
}

/** True when `hour` falls in [start, end), wrapping past midnight (e.g. 23→6). */
export function isInTrainingWindow(hour: number, settings: VoiceStudioSettings): boolean {
  const { windowStartHour: start, windowEndHour: end } = settings;
  if (start === end) return false;
  return start < end ? hour >= start && hour < end : hour >= start || hour < end;
}

// ---------- Runner ----------

let running = false;

function sbv2Dir(): string {
  return path.join(resolveOpenClawHome(), "tts", "sbv2");
}

function pauseFlagPath(): string {
  return path.join(resolveOpenClawHome(), "bonsai", "llm-pause.flag");
}

async function stopMainLlm(): Promise<void> {
  await fs.writeFile(pauseFlagPath(), String(Date.now()));
  await new Promise<void>((resolve) => {
    // Main server and CPU workers both hold memory; training needs the GPU and RAM headroom.
    const ps = spawn(
      "powershell.exe",
      [
        "-NoProfile",
        "-Command",
        "Get-CimInstance Win32_Process -Filter \"Name='llama-server.exe'\" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }",
      ],
      { windowsHide: true, stdio: "ignore" },
    );
    ps.on("exit", () => resolve());
    ps.on("error", () => resolve());
  });
}

async function resumeMainLlm(): Promise<void> {
  // The watchdog restarts the last model within a minute once the flag is gone.
  await fs.rm(pauseFlagPath(), { force: true });
}

function runTrainingScript(project: VoiceProject, onStep: (step: string) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const python = path.join(sbv2Dir(), "venv", "Scripts", "python.exe");
    const script = path.join(resolveOpenClawHome(), "tts", "train_voice.py");
    const child = spawn(
      python,
      [script, "--project", projectDir(project.id), "--name", project.id],
      {
        cwd: sbv2Dir(),
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    let tail = "";
    const onData = (chunk: Buffer) => {
      const text = chunk.toString("utf-8");
      tail = (tail + text).slice(-2_000);
      for (const line of text.split(/\r?\n/)) {
        const step = /^STEP:\s*(.+)$/.exec(line.trim())?.[1];
        if (step) onStep(step);
      }
    };
    child.stdout.on("data", onData);
    child.stderr.on("data", onData);
    child.on("error", reject);
    child.on("exit", (code) =>
      code === 0
        ? resolve()
        : reject(new Error(`training exited with ${code}: ${tail.slice(-400)}`)),
    );
  });
}

type AivisSpeaker = { name: string; speaker_uuid?: string; styles: Array<{ id: number }> };

async function listAivisStyleIds(): Promise<Set<number>> {
  const speakers = (await (
    await fetch("http://127.0.0.1:10101/speakers")
  ).json()) as AivisSpeaker[];
  return new Set(speakers.flatMap((s) => s.styles.map((st) => st.id)));
}

async function installIntoAivis(project: VoiceProject): Promise<number | undefined> {
  await ensureTtsEngine("aivis");
  const before = await listAivisStyleIds();
  const aivmx = path.join(projectDir(project.id), "out", `${project.id}.aivmx`);
  const data = await fs.readFile(aivmx);
  const form = new FormData();
  form.append("file", new Blob([data]), `${project.id}.aivmx`);
  const res = await fetch("http://127.0.0.1:10101/aivm_models/install", {
    method: "POST",
    body: form,
    signal: AbortSignal.timeout(300_000),
  });
  if (!res.ok) throw new Error(`AivisSpeech install failed: HTTP ${res.status}`);
  // The engine names the speaker from the model manifest, so find the new style by diffing.
  const after = await listAivisStyleIds();
  return [...after].find((id) => !before.has(id));
}

/**
 * Trains the oldest queued voice when inside the window. Returns the project it worked on.
 * `onDone` reports success or failure (phone/chat notices).
 */
export async function runVoiceTrainingTick(
  now: Date,
  onDone?: (project: VoiceProject) => Promise<void>,
): Promise<VoiceProject | undefined> {
  if (running) return undefined;
  const settings = await loadVoiceStudioSettings();
  if (!isInTrainingWindow(now.getHours(), settings)) return undefined;
  const next = (await listVoiceProjects())
    .filter((p) => p.status === "queued")
    .sort((a, b) => a.updatedAt - b.updatedAt)[0];
  if (!next) return undefined;
  running = true;
  const project = next;
  try {
    project.status = "training";
    project.step = "メインLLMを一時停止";
    await saveProject(project);
    await stopMainLlm();
    await runTrainingScript(project, (step) => {
      project.step = step;
      void saveProject(project);
    });
    project.step = "AivisSpeechに追加";
    await saveProject(project);
    project.speakerId = await installIntoAivis(project);
    project.status = "ready";
    project.step = "完成";
  } catch (err) {
    project.status = "failed";
    project.error = String(err).slice(0, 500);
  } finally {
    await resumeMainLlm();
    await saveProject(project);
    running = false;
  }
  await onDone?.(project).catch(() => undefined);
  return project;
}
