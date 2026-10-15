import * as fs from "node:fs/promises";
import * as path from "node:path";
import { MUSIC_DIT, MUSIC_LM, MUSIC_MODEL_DIR } from "./config.js";
import { acquireGpu } from "./gpu-lease.js";
import { aceBin, outDir, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import { assertAllowedRequest, toMusicCaption } from "./text.js";
import type { MediaJob } from "./types.js";

export async function runMusicJob(job: MediaJob): Promise<void> {
  markStage(job, "曲の説明を英語に変換しています");
  const caption = await toMusicCaption(job.request);
  const lyrics = job.options.instrumental
    ? "[Instrumental]"
    : sanitizeLyrics(job.options.lyrics ?? "");
  assertAllowedRequest(job.request, caption, lyrics);
  const workDir = path.join(outDir(), `${job.id}-work`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      caption,
      lyrics,
      duration: Math.min(240, Math.max(15, job.options.durationSec ?? 90)),
      vocal_language: job.options.instrumental ? "unknown" : "ja",
      inference_steps: 8,
      shift: 3.0,
      lm_model: MUSIC_LM,
      synth_model: MUSIC_DIT,
      output_format: "mp3",
      mp3_bitrate: 192,
    }),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  job.stage = lyrics ? "曲の構成を考えています" : "歌詞と曲の構成を考えています";
  await runTool(aceBin("ace-lm"), ["--models", MUSIC_MODEL_DIR, "--request", request], {});
  markStage(job, "音にしています");
  await runTool(
    aceBin("ace-synth"),
    ["--models", MUSIC_MODEL_DIR, "--request", path.join(workDir, "request0.json")],
    {},
  );
  const file = await resultPath(job, ".mp3");
  await fs.rename(path.join(workDir, "request00.mp3"), file);
  // Keep the generated lyrics with the result so the app can show them.
  try {
    const enriched = JSON.parse(
      await fs.readFile(path.join(workDir, "request0.json"), "utf-8"),
    ) as {
      lyrics?: string;
    };
    job.options.lyrics = enriched.lyrics ?? lyrics;
  } catch {
    // Lyrics display is best-effort.
  }
  await fs.rm(workDir, { recursive: true, force: true });
  job.file = file;
  job.mime = "audio/mpeg";
}

/** Lyrics keep their line breaks and section tags like [Verse]. */
export function sanitizeLyrics(raw: string): string {
  return raw
    .replace(/\r\n?/g, "\n")
    .replace(
      /[^\p{L}\p{N}\s\[\],.:;!?()'"_+\-/&\u30FC\u301C\uFF5E\u3001\u3002\u300C\u300D\u300E\u300F\uFF01\uFF1F]/gu,
      "",
    )
    .trim()
    .slice(0, 3_000);
}
