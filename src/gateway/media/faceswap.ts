import * as fs from "node:fs/promises";
import * as path from "node:path";
import { characterAssetDir, getCharacter } from "../comic-characters.js";
import {
  FACEFUSION_DIR,
  FACEFUSION_PYTHON,
  faceFusionPath,
  probeMedia,
  resolveInputFile,
  swapFrameCount,
  swapPassArgs,
} from "../face-swap.js";
import { jobs, stopImageWorker } from "../media-studio.js";
import { acquireGpu } from "./gpu-lease.js";
import { exists, outDir, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import type { MediaJob, MediaOptions } from "./types.js";

/** The target file of a face swap job: "in:<file>" or a finished library job ("job:<id>"). */
export function resolveSwapTarget(options: MediaOptions): string | undefined {
  const target = options.swapTarget ?? "";
  if (target.startsWith("in:")) return resolveInputFile(target);
  const match = /^job:([a-f0-9]{6,32})$/.exec(target);
  const job = match?.[1] ? jobs.get(match[1]) : undefined;
  return job?.file;
}

/** One face picture for a swap: a library character's icon (or sheet), or a picture in the input folder. */
async function swapSourceFile(source: { characterId?: string; inputId?: string }): Promise<string> {
  if (source.inputId) {
    const file = resolveInputFile(source.inputId);
    if (file && (await exists(file))) return file;
    throw new Error("顔の写真が見つかりません");
  }
  const character = await getCharacter(source.characterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const icon = path.join(characterAssetDir(character.id), "icon.png");
  if (await exists(icon)) return icon;
  if (character.refImage && (await exists(character.refImage))) return character.refImage;
  throw new Error(`${character.name} の顔の絵がありません。先に設定画を作ってください`);
}

/**
 * Face swap with FaceFusion: one pass per person (each pass takes the previous pass's film), then
 * the film is the job's result. The audio stays; the temp frames are JPEG and removed afterwards.
 */
export async function runFaceSwapJob(job: MediaJob): Promise<void> {
  const options = job.options;
  const target = resolveSwapTarget(options);
  if (!target || !(await exists(target))) throw new Error("入れ替える動画か画像が見つかりません");
  const sources = options.swapSources ?? [];
  if (sources.length === 0) throw new Error("入れ替える顔がありません");
  const probe = await probeMedia(target);
  const frames = swapFrameCount(probe, options);
  const total = Math.max(1, sources.length);
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const work = path.join(outDir(), `${job.id}-swap`);
  await fs.mkdir(work, { recursive: true });
  const startFrame = Math.round((options.swapTrimStartSec ?? 0) * probe.fps);
  const trimmed =
    probe.kind === "video" &&
    (options.swapTrimStartSec !== undefined || options.swapTrimEndSec !== undefined);
  const referenceFrame = Math.round((options.swapReferenceSec ?? 0) * probe.fps);
  const ext = probe.kind === "image" ? ".png" : ".mp4";
  const env = { ...process.env, PYTHONUTF8: "1", PATH: await faceFusionPath(process.env.PATH) };
  try {
    let current = target;
    for (const [index, source] of sources.entries()) {
      if (job.cancelRequested) throw new Error("中止しました");
      const output = path.join(work, `pass-${index + 1}${ext}`);
      const args = swapPassArgs({
        source: await swapSourceFile(source),
        target: current,
        output,
        tempDir: path.join(work, "temp"),
        quality: options.swapQuality ?? "standard",
        enhancer: options.swapEnhancer === true,
        position:
          sources.length > 1 || source.position !== undefined
            ? (source.position ?? index)
            : undefined,
        referenceFrame: index === 0 ? referenceFrame : Math.max(0, referenceFrame - startFrame),
        // Only the first pass cuts the part out; later passes already work on it.
        ...(trimmed && index === 0
          ? {
              trim: {
                startFrame,
                endFrame: startFrame + frames,
              },
            }
          : {}),
      });
      const label = total > 1 ? `${index + 1}/${total}人目` : "";
      markStage(job, `顔を入れ替えています${label ? `（${label}・準備中）` : "（準備中）"}`);
      const passStart = Date.now();
      await runTool(FACEFUSION_PYTHON, args, {
        cwd: FACEFUSION_DIR,
        env,
        timeoutMs: 12 * 60 * 60_000,
        onLine: (line) => {
          const percent = /(\d{1,3})%\|/.exec(line)?.[1];
          if (percent && /processing/i.test(line)) {
            markStage(job, `顔を入れ替えています（${label ? `${label}・` : ""}${percent}%）`);
          }
        },
      });
      if (!(await exists(output))) throw new Error("入れ替えた結果のファイルができませんでした");
      // Milliseconds per frame per pass, kept so the next estimate uses the real speed.
      (job.timings ??= {}).spf = Math.round((Date.now() - passStart) / Math.max(1, frames));
      current = output;
    }
    const finalFile = await resultPath(job, ext);
    await fs.copyFile(current, finalFile);
    job.file = finalFile;
    job.mime = probe.kind === "image" ? "image/png" : "video/mp4";
  } finally {
    await fs.rm(work, { recursive: true, force: true }).catch(() => undefined);
  }
}
