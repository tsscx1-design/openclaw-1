import { execFile, spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { generationCudaDevice, generationCudaDevices, mediaProcessEnv } from "../media-gpu.js";
import { JOB_TIMEOUT_MS, WAN2GP_DIR, WAN2GP_PYTHON, WAN2GP_RUNNER } from "./config.js";
import { currentJob } from "./state.js";
import type { MediaJob } from "./types.js";

// ---------- Runners ----------

/** Sets the progress note and records how long the previous stage took. */
export function markStage(job: MediaJob, stage: string): void {
  const now = Date.now();
  const timings = (job.timings ??= {});
  const last = timings.__at ?? job.startedAt ?? now;
  // Step counters ("画像を描いています 12/28") roll up into one entry per stage.
  const key = job.stage?.replace(/\s+\d+\/\d+$/, "");
  if (key) timings[key] = (timings[key] ?? 0) + (now - last);
  timings.__at = now;
  job.stage = stage;
}

export const activeChildren = new Set<ReturnType<typeof spawn>>();

/** Stops a tool and everything it started (Wan2GP spawns its own workers). */
export function killProcessTree(pid: number | undefined): Promise<void> {
  if (!pid) return Promise.resolve();
  return new Promise((resolve) => {
    execFile("taskkill", ["/PID", String(pid), "/T", "/F"], { windowsHide: true }, () => resolve());
  });
}

export function runTool(
  exe: string,
  args: string[],
  opts: {
    cwd?: string;
    env?: NodeJS.ProcessEnv;
    cudaDevice?: string;
    onLine?: (line: string) => void;
    timeoutMs?: number;
  },
): Promise<void> {
  if (currentJob?.cancelRequested) return Promise.reject(new Error("中止しました"));
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, {
      cwd: opts.cwd ?? path.dirname(exe),
      env: mediaProcessEnv(
        opts.env,
        opts.cudaDevice ?? generationCudaDevice(currentJob?.options.gpuMode === "fast"),
      ),
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    activeChildren.add(child);
    child.on("exit", () => {
      activeChildren.delete(child);
    });
    // The gateway runs as a below-normal scheduled task and children inherit that class, which on
    // Windows also lowers disk I/O priority: loading a 7 GB model then took minutes instead of seconds.
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
    } catch {
      // Priority is best-effort.
    }
    let tail = "";
    const onData = (chunk: Buffer) => {
      const text = chunk.toString("utf-8");
      tail = (tail + text).slice(-3_000);
      if (opts.onLine)
        for (const line of text.split(/\r?\n|\r/)) if (line.trim()) opts.onLine(line.trim());
    };
    child.stdout?.on("data", onData);
    child.stderr?.on("data", onData);
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error(`${path.basename(exe)} timed out`));
    }, opts.timeoutMs ?? JOB_TIMEOUT_MS);
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      if (code === 0) resolve();
      else
        reject(
          new Error(`${path.basename(exe)} exited with code ${code}: ${tail.trim().slice(-500)}`),
        );
    });
  });
}

// Local FP16/BF16 throughput measures the 4060 at about 1.24x the 3060, so the
// fastest-first device order completes equal image work closest to 55:45.
const DUAL_GPU_PRIMARY_SHARE = 0.55;

/** Runs a Wan2GP image manifest on one GPU, or splits independent images across both GPUs. */
export async function runWan2gpManifest(
  job: MediaJob,
  workDir: string,
  settings: Record<string, unknown>[],
  opts: {
    timeoutMs: number;
    onComplete?: (done: number, total: number) => void;
  },
): Promise<string[]> {
  const devices = generationCudaDevices(job.options.gpuMode === "fast");
  const dual = devices.length >= 2 && settings.length >= 2;
  const split = dual
    ? Math.max(1, Math.ceil(settings.length * DUAL_GPU_PRIMARY_SHARE))
    : settings.length;
  const shards = dual ? [settings.slice(0, split), settings.slice(split)] : [settings];
  let done = 0;
  const runs = shards.map(async (shard, index) => {
    const shardDir = dual ? path.join(workDir, `gpu-${index + 1}`) : workDir;
    await fs.mkdir(shardDir, { recursive: true });
    const request = path.join(shardDir, "request.json");
    await fs.writeFile(request, JSON.stringify({ output_dir: shardDir, settings: shard }));
    let files: string[] = [];
    await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
      cwd: WAN2GP_DIR,
      cudaDevice: devices[index] ?? devices[0],
      timeoutMs: opts.timeoutMs,
      onLine: (line) => {
        const result = /^RESULT (.*)$/.exec(line)?.[1];
        if (result) files = JSON.parse(result) as string[];
        if (/^PROGRESS 100\b/.test(line)) {
          done += 1;
          opts.onComplete?.(Math.min(done, settings.length), settings.length);
        }
      },
    });
    return files;
  });
  let produced: string[][];
  try {
    produced = await Promise.all(runs);
  } catch (error) {
    await Promise.all([...activeChildren].map((child) => killProcessTree(child.pid)));
    await Promise.allSettled(runs);
    throw error;
  }
  return produced.flat();
}

/** sd.cpp prints "|====>   | 12/28 - 1.23s/it"; turn it into a short stage note. */
export function stepNote(prefix: string, job: MediaJob) {
  return (line: string) => {
    const match = /(\d+)\/(\d+)\s*-\s*[\d.]+\s*(?:s\/it|it\/s)/.exec(line);
    if (!match) return;
    const stage = `${prefix} ${match[1]}/${match[2]}`;
    if (job.stage?.startsWith(prefix)) job.stage = stage;
    else markStage(job, stage);
  };
}
