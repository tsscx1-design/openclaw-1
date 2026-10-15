import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "../partner-store.js";
import type { MediaJob } from "./types.js";

// ---------- Paths ----------

export function home(): string {
  return resolveOpenClawHome();
}

export function sdCli(): string {
  return path.join(home(), "image", "bin-cuda", "sd-cli.exe");
}

export function aceBin(name: "ace-lm" | "ace-synth"): string {
  return path.join(home(), "music", "src", "build", "Release", `${name}.exe`);
}

export function outDir(): string {
  return path.join(home(), "media", "out");
}

/** Finished files live in one folder per kind (out/image, out/video, out/music). */
export async function resultPath(job: MediaJob, ext: string): Promise<string> {
  const dir = path.join(outDir(), job.kind);
  await fs.mkdir(dir, { recursive: true });
  return path.join(dir, `${job.id}${ext}`);
}

export function pauseFlag(): string {
  return path.join(home(), "bonsai", "llm-pause.flag");
}

export function lastMainCmdline(): string {
  return path.join(home(), "bonsai", "last_main_cmdline.txt");
}

export async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}
