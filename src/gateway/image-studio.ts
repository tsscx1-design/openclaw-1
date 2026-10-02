import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Local avatar image generation: stable-diffusion.cpp (CPU build) + an anime SD1.5 checkpoint.
// CPU-only on purpose: the GPU is fully used by the main chat model, and one image taking a few
// minutes is fine for an avatar. Jobs run one at a time at below-normal priority.

const MODEL_FILE = "anylora-bakedvae-fp16.safetensors";
const GENERATION_THREADS = 4;
const GENERATION_TIMEOUT_MS = 15 * 60_000;
const JOB_TTL_MS = 60 * 60_000;
const MAX_PROMPT_CHARS = 600;

const QUALITY_TAGS =
  "masterpiece, best quality, anime style, 1 character, upper body, looking at viewer, simple background";
const NEGATIVE_PROMPT =
  "nsfw, nude, lowres, bad anatomy, bad hands, extra fingers, missing fingers, text, watermark, signature, username, blurry, worst quality, low quality, jpeg artifacts, realistic photo, multiple people";

export type AvatarJobStatus = "queued" | "running" | "done" | "error";

export type AvatarJob = {
  id: string;
  status: AvatarJobStatus;
  prompt: string;
  createdAt: number;
  finishedAt?: number;
  /** Seconds the generation took, for the "this usually takes N minutes" hint in the app. */
  durationSec?: number;
  imageDataUri?: string;
  error?: string;
};

const jobs = new Map<string, AvatarJob>();
const queue: string[] = [];
let working = false;

function imageRoot(): string {
  return path.join(resolveOpenClawHome(), "image");
}

function sdCliPath(): string {
  return path.join(imageRoot(), "bin", "sd-cli.exe");
}

function modelPath(): string {
  return path.join(imageRoot(), "models", MODEL_FILE);
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function imageStudioStatus(): Promise<{
  ready: boolean;
  missing: string[];
  busy: boolean;
}> {
  const missing: string[] = [];
  if (!(await exists(sdCliPath()))) missing.push("sd-cli.exe");
  if (!(await exists(modelPath()))) missing.push(MODEL_FILE);
  return { ready: missing.length === 0, missing, busy: working || queue.length > 0 };
}

/** Keeps only printable prompt text so a client cannot smuggle extra command-line flags. */
export function sanitizeImagePrompt(raw: string): string {
  return raw
    .replace(/[\r\n\t]+/g, " ")
    .replace(/[^\p{L}\p{N}\s,.:;()'"_+\-/]/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, MAX_PROMPT_CHARS);
}

function pruneJobs(now = Date.now()): void {
  for (const [id, job] of jobs) {
    if (job.finishedAt && now - job.finishedAt > JOB_TTL_MS) jobs.delete(id);
  }
}

function runSd(job: AvatarJob, outFile: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const args = [
      "-m",
      modelPath(),
      "-p",
      `${QUALITY_TAGS}, ${job.prompt}`,
      "-n",
      NEGATIVE_PROMPT,
      "-W",
      "512",
      "-H",
      "512",
      "--steps",
      "16",
      "--cfg-scale",
      "7",
      "--sampling-method",
      "euler_a",
      "--clip-skip",
      "2",
      "-s",
      "-1",
      "-t",
      String(GENERATION_THREADS),
      "-o",
      outFile,
    ];
    const child = spawn(sdCliPath(), args, {
      windowsHide: true,
      stdio: ["ignore", "ignore", "pipe"],
    });
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_BELOW_NORMAL);
    } catch {
      // Priority is best-effort.
    }
    let stderr = "";
    child.stderr?.on("data", (chunk) => {
      stderr = (stderr + String(chunk)).slice(-2_000);
    });
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error("Image generation timed out"));
    }, GENERATION_TIMEOUT_MS);
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      if (code === 0) resolve();
      else reject(new Error(`sd-cli exited with code ${code}: ${stderr.trim().slice(-400)}`));
    });
  });
}

async function processQueue(): Promise<void> {
  if (working) return;
  working = true;
  try {
    for (let id = queue.shift(); id; id = queue.shift()) {
      const job = jobs.get(id);
      if (!job) continue;
      job.status = "running";
      const started = Date.now();
      const outDir = path.join(imageRoot(), "out");
      await fs.mkdir(outDir, { recursive: true });
      const outFile = path.join(outDir, `${job.id}.jpg`);
      try {
        await runSd(job, outFile);
        const bytes = await fs.readFile(outFile);
        job.imageDataUri = `data:image/jpeg;base64,${bytes.toString("base64")}`;
        job.status = "done";
      } catch (err) {
        job.status = "error";
        job.error = String(err instanceof Error ? err.message : err);
      } finally {
        job.finishedAt = Date.now();
        job.durationSec = Math.round((job.finishedAt - started) / 1000);
        await fs.rm(outFile, { force: true });
      }
    }
  } finally {
    working = false;
  }
}

export async function startAvatarJob(prompt: string): Promise<AvatarJob> {
  const status = await imageStudioStatus();
  if (!status.ready) {
    throw new Error(`Image generation is not installed (missing: ${status.missing.join(", ")})`);
  }
  const clean = sanitizeImagePrompt(prompt);
  if (!clean) throw new Error("Prompt is empty");
  pruneJobs();
  if (queue.length >= 3) throw new Error("Too many image jobs are waiting; try again later");
  const job: AvatarJob = {
    id: randomBytes(6).toString("hex"),
    status: "queued",
    prompt: clean,
    createdAt: Date.now(),
  };
  jobs.set(job.id, job);
  queue.push(job.id);
  void processQueue();
  return job;
}

export function getAvatarJob(id: string): AvatarJob | undefined {
  pruneJobs();
  return jobs.get(id);
}
