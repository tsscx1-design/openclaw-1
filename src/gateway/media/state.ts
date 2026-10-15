import * as fs from "node:fs/promises";
import * as path from "node:path";
import { outDir } from "./paths.js";
import type { MediaJob } from "./types.js";

/** The job being worked on and the tool process it runs; a delete stops them. */
export let currentJob: MediaJob | undefined;

export function setCurrentJob(job: MediaJob | undefined): void {
  currentJob = job;
}

// ---------- Queue ----------

export const jobs = new Map<string, MediaJob>();

export const queue: string[] = [];

export let working = false;

export function setWorking(value: boolean): void {
  working = value;
}

export function metaPath(id: string): string {
  return path.join(outDir(), `${id}.json`);
}

export async function saveJobMeta(job: MediaJob): Promise<void> {
  const { sourceImageBase64: _drop, ...options } = job.options;
  await fs.writeFile(metaPath(job.id), JSON.stringify({ ...job, options }));
}

export async function loadJobMeta(id: string): Promise<MediaJob | undefined> {
  if (!/^[a-f0-9]{12}$/.test(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(metaPath(id), "utf-8")) as MediaJob;
  } catch {
    return undefined;
  }
}

export async function getMediaJob(id: string): Promise<MediaJob | undefined> {
  return jobs.get(id) ?? (await loadJobMeta(id));
}

export async function listMediaJobs(limit = 30): Promise<MediaJob[]> {
  const byId = new Map<string, MediaJob>();
  try {
    for (const name of await fs.readdir(outDir())) {
      const id = /^([a-f0-9]{12})\.json$/.exec(name)?.[1];
      if (!id) continue;
      const job = await loadJobMeta(id);
      if (job) byId.set(job.id, job);
    }
  } catch {
    // No results yet.
  }
  for (const job of jobs.values()) byId.set(job.id, job);
  return [...byId.values()].sort((a, b) => b.createdAt - a.createdAt).slice(0, limit);
}
