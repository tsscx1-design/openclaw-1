import * as path from "node:path";
import { FACEFUSION_DIR, FACEFUSION_PYTHON } from "../face-swap.js";
import { IRODORI_DIR, irodoriInstalled } from "../irodori-tts.js";
import { currentJob, queue, working } from "../media-studio.js";
import {
  IMAGE_MODEL,
  MUSIC_DIT,
  MUSIC_LM,
  MUSIC_MODEL_DIR,
  REMIX_HIGH,
  REMIX_LOW,
  RIFE_SCRIPT,
  VIDEO_CLIP_VISION,
  VIDEO_MODEL,
  VIDEO_T5,
  VIDEO_VAE,
} from "./config.js";
import { clearStalePauseFlag } from "./gpu-lease.js";
import { IMAGE_SERVER } from "./image.js";
import { MANGA_COMPOSE, MANGA_FONT } from "./manga.js";
import { MOTION_SCRIPT } from "./motion-comic.js";
import { aceBin, exists, sdCli } from "./paths.js";
import type { MediaKind } from "./types.js";
import { WAN2GP_PYTHON, WAN2GP_RUNNER } from "./video.js";

export async function mediaStudioStatus(): Promise<{
  image: { ready: boolean; missing: string[] };
  video: { ready: boolean; missing: string[] };
  wan2gp: { ready: boolean; missing: string[] };
  music: { ready: boolean; missing: string[] };
  manga: { ready: boolean; missing: string[] };
  comic: { ready: boolean; missing: string[] };
  voice: { ready: boolean; missing: string[] };
  motion: { ready: boolean; missing: string[] };
  faceswap: { ready: boolean; missing: string[] };
  busy: boolean;
  queued: number;
  /** The job being worked on now (what the phone shows as "the chat AI is paused for ..."). */
  current?: { kind: MediaKind; stage?: string; request: string; elapsedSec: number };
  /** True while the chat model is stopped to give the GPU to a job. */
  chatPaused: boolean;
}> {
  const check = async (files: string[]) => {
    const missing: string[] = [];
    for (const file of files) if (!(await exists(file))) missing.push(path.basename(file));
    return { ready: missing.length === 0, missing };
  };
  return {
    image: await check([sdCli(), IMAGE_MODEL]),
    video: await check([sdCli(), IMAGE_MODEL, VIDEO_MODEL, VIDEO_VAE, VIDEO_T5, VIDEO_CLIP_VISION]),
    // Wan2GP engines download their models on first use, so only the runner is checked here.
    wan2gp: await check([WAN2GP_PYTHON, WAN2GP_RUNNER]),
    music: await check([
      aceBin("ace-lm"),
      aceBin("ace-synth"),
      path.join(MUSIC_MODEL_DIR, MUSIC_LM),
      path.join(MUSIC_MODEL_DIR, MUSIC_DIT),
      path.join(MUSIC_MODEL_DIR, "Qwen3-Embedding-0.6B-Q8_0.gguf"),
      path.join(MUSIC_MODEL_DIR, "vae-BF16.gguf"),
    ]),
    manga: await check([sdCli(), IMAGE_MODEL, WAN2GP_PYTHON, MANGA_COMPOSE, MANGA_FONT]),
    comic: await check([IMAGE_SERVER, IMAGE_MODEL, WAN2GP_PYTHON, MANGA_COMPOSE, MANGA_FONT]),
    voice: (await irodoriInstalled())
      ? { ready: true, missing: [] }
      : { ready: false, missing: ["Irodori-TTS"] },
    // Key images, Wan 2.2 Remix cuts (sd.cpp), RIFE and the Irodori voices.
    motion: await check([
      MOTION_SCRIPT,
      sdCli(),
      IMAGE_MODEL,
      REMIX_HIGH,
      REMIX_LOW,
      WAN2GP_PYTHON,
      RIFE_SCRIPT,
      path.join(IRODORI_DIR, ".venv"),
    ]),
    faceswap: await check([FACEFUSION_PYTHON, path.join(FACEFUSION_DIR, "facefusion.py")]),
    busy: working,
    queued: queue.length,
    ...(currentJob
      ? {
          current: {
            kind: currentJob.kind,
            stage: currentJob.stage,
            request: currentJob.request,
            elapsedSec: Math.round((Date.now() - (currentJob.startedAt ?? Date.now())) / 1000),
          },
        }
      : {}),
    chatPaused: await clearStalePauseFlag(),
  };
}
