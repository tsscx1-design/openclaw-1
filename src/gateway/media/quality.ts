import * as fs from "node:fs/promises";
import * as path from "node:path";
import { generationCudaDevice } from "../media-gpu.js";
import type { MediaQualityMode } from "../media-production-controls.js";
import { upscaleWithSeedVr } from "../seedvr-comfy.js";
import { resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import type { MediaJob } from "./types.js";
import { VOICE_FFMPEG } from "./voice-drama.js";

async function improveImageFile(
  job: MediaJob,
  file: string,
  mode: MediaQualityMode,
): Promise<void> {
  const temp = `${file}.quality.png`;
  await upscaleWithSeedVr(
    file,
    temp,
    { model: mode === "max" ? "7b" : "3b", resolution: mode === "max" ? 2048 : 1536 },
    {
      onStage: (stage) => markStage(job, stage),
      isCancelled: () => job.cancelRequested === true,
      cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
    },
  );
  await fs.copyFile(temp, file);
  await fs.rm(temp, { force: true });
}

async function improveAudioFile(file: string): Promise<void> {
  const ext = path.extname(file).toLowerCase();
  const temp = `${file}.quality${ext || ".wav"}`;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      file,
      "-af",
      "loudnorm=I=-16:TP=-1.5:LRA=11",
      ...(ext === ".mp3" ? ["-c:a", "libmp3lame", "-b:a", "192k"] : ["-c:a", "pcm_s16le"]),
      temp,
    ],
    { timeoutMs: 30 * 60_000 },
  );
  await fs.copyFile(temp, file);
  await fs.rm(temp, { force: true });
}

async function improveVideoFile(
  job: MediaJob,
  file: string,
  mode: MediaQualityMode,
): Promise<void> {
  const temp = `${file}.quality.mp4`;
  const videoFilter =
    mode === "max"
      ? "hqdn3d=1.2:1.2:6:6,unsharp=5:5:0.8:5:5:0"
      : "hqdn3d=0.8:0.8:4:4,unsharp=5:5:0.45:5:5:0";
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      file,
      "-vf",
      videoFilter,
      "-c:v",
      "libx264",
      "-preset",
      mode === "max" ? "slow" : "medium",
      "-crf",
      mode === "max" ? "17" : "19",
      "-map",
      "0:v:0",
      "-map",
      "0:a?",
      "-c:a",
      "aac",
      "-b:a",
      "192k",
      "-movflags",
      "+faststart",
      temp,
    ],
    { timeoutMs: 4 * 60 * 60_000 },
  );
  const finalFile = await resultPath(job, ".mp4");
  await fs.copyFile(temp, finalFile);
  await fs.rm(temp, { force: true });
  if (file !== finalFile) await fs.rm(file, { force: true }).catch(() => undefined);
  job.file = finalFile;
  job.mime = "video/mp4";
}

/** Optional finishing pass shared by images, comics, video, motion, face swap, voice and music. */
export async function runQualityPass(job: MediaJob): Promise<void> {
  const mode = job.options.qualityMode;
  if (!mode || mode === "off") return;
  markStage(job, "仕上がりを整えています");
  try {
    const imageFiles = job.files?.length
      ? job.files
      : job.file && job.mime?.startsWith("image/")
        ? [job.file]
        : [];
    if (imageFiles.length > 0) {
      for (const [index, file] of imageFiles.entries()) {
        markStage(job, `画像を高画質化しています（${index + 1}/${imageFiles.length}）`);
        await improveImageFile(job, file, mode);
      }
    } else if (job.file && job.mime?.startsWith("video/")) {
      await improveVideoFile(job, job.file, mode);
    } else if (job.file && job.mime?.startsWith("audio/")) {
      await improveAudioFile(job.file);
    } else {
      throw new Error("仕上げ対象のファイルがありません");
    }
    job.quality = { mode, status: "done", note: "高画質仕上げ済み" };
  } catch (error) {
    job.quality = {
      mode,
      status: "warning",
      note: `元の生成結果を保存しました。仕上げのみ失敗: ${String(error instanceof Error ? error.message : error).slice(0, 240)}`,
    };
  }
}
