import * as fs from "node:fs/promises";
import * as path from "node:path";
import { getCharacter } from "../comic-characters.js";
import {
  adultBaseFile,
  loadSampleIndex,
  PRECISE_SAMPLE_KEYS,
  resolveSampleSubject,
  sampleFileName,
  samplePrompt,
  samplesDir,
  saveSampleIndex,
} from "../sample-factory.js";
import { QWEN_LORA_DIR, VIGGLE_TURBO_LORA } from "./character-lora.js";
import { photoEngineSettings, pngSize } from "./character-sheet.js";
import { acquireGpu } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { exists } from "./paths.js";
import { markStage, runTool, runWan2gpManifest } from "./runner.js";
import { assertAllowedRequest } from "./text.js";
import type { MediaJob } from "./types.js";
import { WAN2GP_DIR, WAN2GP_PYTHON, WAN2GP_RUNNER } from "./video.js";
import { VOICE_FFMPEG } from "./voice-drama.js";

/**
 * One sample picture of the sample factory: the character's standing picture (or the adult base made
 * from it) is edited with Qwen Image 2.1 (Viggle Turbo) so that only the chosen setting changes.
 * The result is kept in the factory folder and listed in its index.
 */
export async function runCharacterSample(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const makingAdultBase = job.options.sampleAdultBase === true;
  const base = job.options.sampleBase === "adult" && !makingAdultBase ? "adult" : "normal";
  const source = base === "adult" ? adultBaseFile(character.id) : character.refImage;
  if (!source || !(await exists(source))) {
    throw new Error(
      base === "adult"
        ? "先に「成人向けの基準画像」を作ってください"
        : "先に設定画を決めてください（元になります）",
    );
  }
  const wanted = job.options.sampleEngine ?? "fast";
  // Large changes of the body (a limbless body) only work with the precise editor.
  const engine =
    wanted === "fast" && PRECISE_SAMPLE_KEYS.has(job.options.sampleKey ?? "") ? "precise" : wanted;
  const picked = photoEngineSettings(engine);
  if (picked.needsLora && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error(`サンプルを作るための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
  }
  let prompt: string;
  let key = "";
  if (makingAdultBase) {
    prompt =
      "Keep the same adult woman exactly as in the picture: face, hair, eye color, body shape and the same plain background. " +
      "Change only this: she is completely nude, standing, front view, full body, arms relaxed at her sides.";
  } else {
    key = job.options.sampleKey ?? "";
    const subject = await resolveSampleSubject(key);
    if (!subject) throw new Error("その項目が見つかりません");
    assertAllowedRequest(subject.tags, job.options.sampleNote ?? "");
    const index = await loadSampleIndex();
    // What is written now wins; otherwise the saved default instruction of this setting.
    const note =
      job.options.sampleNote !== undefined ? job.options.sampleNote : (index.defaults[key] ?? "");
    prompt = samplePrompt(subject, base, note);
    job.options.sampleNote = note;
  }
  await fs.mkdir(samplesDir(), { recursive: true });
  const work = path.join(samplesDir(), `work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  // About one megapixel in multiples of 16, keeping the shape of the base picture.
  const fitted = path.join(work, "source.png");
  const budget = 1_000_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      fitted,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(fitted);
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, makingAdultBase ? "成人向けの基準画像を作っています" : "サンプルを作っています");
  const request = path.join(work, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...picked.settings,
        prompt,
        image_refs: [fitted],
        video_prompt_type: "KI",
        resolution: `${size.width}x${size.height}`,
        seed: -1,
      },
    }),
  );
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 30 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error("サンプルができませんでした");
  if (makingAdultBase) {
    await fs.copyFile(image, adultBaseFile(character.id));
    job.file = adultBaseFile(character.id);
    job.mime = "image/png";
  } else {
    const file = path.join(samplesDir(), sampleFileName(key));
    // Shown on the phone: at most 768 px tall, JPEG.
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", image, "-vf", "scale=-2:'min(768,ih)'", "-q:v", "3", file],
      { timeoutMs: 60_000 },
    );
    const index = await loadSampleIndex();
    index.items[key] = { file, base, note: job.options.sampleNote ?? "", at: Date.now() };
    await saveSampleIndex(index);
    job.file = file;
    job.mime = "image/jpeg";
  }
  await fs.rm(work, { recursive: true, force: true });
  job.comic = { seriesId: "", character: character.name, characterId: character.id, sample: true };
}

/** How many samples one Wan2GP start draws: the model loads once, and both GPUs share the chunk. */
const SAMPLE_BATCH_CHUNK = 8;

/**
 * Many samples (a whole genre) in one job. The picture, prompt and model are the same as for a single
 * sample; only the loading is shared and the work is split over both GPUs. A chunk that does not
 * return exactly one picture per setting is counted as failed, because the pictures could not be
 * matched to their settings.
 */
export async function runSampleBatch(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const base = job.options.sampleBase === "adult" ? "adult" : "normal";
  const source = base === "adult" ? adultBaseFile(character.id) : character.refImage;
  if (!source || !(await exists(source))) {
    throw new Error(
      base === "adult"
        ? "先に「成人向けの基準画像」を作ってください"
        : "先に設定画を決めてください（元になります）",
    );
  }
  const wanted = job.options.sampleEngine ?? "fast";
  await fs.mkdir(samplesDir(), { recursive: true });
  const work = path.join(samplesDir(), `work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  const fitted = path.join(work, "source.png");
  const budget = 1_000_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      fitted,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(fitted);

  type SampleEntry = {
    key: string;
    note: string;
    prompt: string;
    engine: "fast" | "precise" | "edit2509";
  };
  const entries: SampleEntry[] = [];
  let skipped = 0;
  const index0 = await loadSampleIndex();
  for (const key of job.options.sampleKeys ?? []) {
    const subject = await resolveSampleSubject(key);
    if (!subject) {
      skipped += 1;
      continue;
    }
    const note = index0.defaults[key] ?? "";
    try {
      // The user's own check (blocked-terms) decides; a refused setting is left out, not fatal.
      assertAllowedRequest(subject.tags, note);
    } catch {
      skipped += 1;
      continue;
    }
    entries.push({
      key,
      note,
      prompt: samplePrompt(subject, base, note),
      // Large changes of the body only work with the precise editor.
      engine: wanted === "fast" && PRECISE_SAMPLE_KEYS.has(key) ? "precise" : wanted,
    });
  }
  if (entries.length === 0) throw new Error("作れる項目がありませんでした");

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const total = entries.length;
  let done = 0;
  let failed = 0;
  let lastFile = "";
  for (const engine of ["fast", "precise", "edit2509"] as const) {
    const group = entries.filter((entry) => entry.engine === engine);
    if (group.length === 0) continue;
    const picked = photoEngineSettings(engine);
    if (picked.needsLora && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
      throw new Error(`サンプルを作るための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
    }
    for (let at = 0; at < group.length; at += SAMPLE_BATCH_CHUNK) {
      if (job.cancelRequested) throw new Error("中止しました");
      const chunk = group.slice(at, at + SAMPLE_BATCH_CHUNK);
      markStage(job, `サンプルを作っています ${done + failed}/${total}`);
      const chunkDir = path.join(work, `chunk-${engine}-${at}`);
      await fs.mkdir(chunkDir, { recursive: true });
      let files: string[] = [];
      try {
        files = await runWan2gpManifest(
          job,
          chunkDir,
          chunk.map((entry) => ({
            ...picked.settings,
            prompt: entry.prompt,
            image_refs: [fitted],
            video_prompt_type: "KI",
            resolution: `${size.width}x${size.height}`,
            seed: -1,
          })),
          {
            timeoutMs: 90 * 60_000,
            onComplete: (finished) => {
              job.stage = `サンプルを作っています ${done + failed + finished}/${total}`;
            },
          },
        );
      } catch (err) {
        if (job.cancelRequested) throw err;
      }
      const images = files.filter((file) => /\.(png|jpe?g|webp)$/i.test(file));
      if (images.length !== chunk.length) {
        failed += chunk.length;
        continue;
      }
      const index = await loadSampleIndex();
      for (const [n, entry] of chunk.entries()) {
        const picture = images[n];
        if (!picture) continue;
        const file = path.join(samplesDir(), sampleFileName(entry.key));
        // Shown on the phone: at most 768 px tall, JPEG.
        await runTool(
          VOICE_FFMPEG,
          [
            "-y",
            "-loglevel",
            "error",
            "-i",
            picture,
            "-vf",
            "scale=-2:'min(768,ih)'",
            "-q:v",
            "3",
            file,
          ],
          { timeoutMs: 60_000 },
        );
        index.items[entry.key] = { file, base, note: entry.note, at: Date.now() };
        lastFile = file;
      }
      await saveSampleIndex(index);
      done += chunk.length;
    }
  }
  await fs.rm(work, { recursive: true, force: true });
  if (done === 0) throw new Error("サンプルが1枚もできませんでした");
  markStage(
    job,
    `完成（${done}枚${failed + skipped > 0 ? `、できなかった ${failed + skipped} 件` : ""}）`,
  );
  job.file = lastFile;
  job.mime = "image/jpeg";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, sample: true };
}
