import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  allAvatarFrames,
  avatarFramePrompt,
  MINI_FRAMES,
  miniFramePrompt,
} from "../avatar-frames.js";
import { importGeneratedModel } from "../avatar-models.js";
import {
  characterAssetDir,
  getCharacter,
  type LibraryCharacter,
  saveCharacter,
} from "../comic-characters.js";
import { generationCudaDevice } from "../media-gpu.js";
import { renderModel3d } from "../model3d.js";
import {
  QWEN_LORA_DIR,
  qwenTurboSettings,
  VIGGLE_TURBO_LORA,
  WAN2GP_DIR,
  WAN2GP_PYTHON,
} from "./config.js";
import { acquireGpu } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { MANGA_COMPOSE } from "./manga.js";
import { exists, outDir } from "./paths.js";
import { markStage, runTool, runWan2gpManifest } from "./runner.js";
import { listMediaJobs } from "./state.js";
import type { MediaJob } from "./types.js";
import { VOICE_FFMPEG } from "./voice-drama.js";

/** Removes backgrounds (rembg, CPU) from every PNG in a folder and crops them to one box. */
const REMOVE_BG_SCRIPT = "D:\\media-tools\\image\\remove_bg.py";

/** Where a character's avatar pictures are kept. */
export function avatarDir(characterId: string): string {
  return path.join(characterAssetDir(characterId), "avatar");
}

/**
 * The avatar pictures of a character: every expression with the mouth closed and open, and a
 * blink, drawn from the sheet by Qwen Image 2.1 (Viggle Turbo, ~20 s each). The phone shows them
 * in the chat screen; nothing is shown until all are done.
 */
export async function runCharacterAvatar(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（アバターの元になります）");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error("アバターの絵を作る部品（Viggle Turbo）が見つかりません");
  }
  const frames = allAvatarFrames();
  const work = path.join(characterAssetDir(character.id), "avatar-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, `アバターの表情を作っています（0/${frames.length}）`);
  const produced = await runWan2gpManifest(
    job,
    work,
    frames.map((frame) => ({
      ...qwenTurboSettings(),
      prompt: avatarFramePrompt(frame),
      image_refs: [source],
      video_prompt_type: "KI",
      resolution: "832x1216",
      // The same seed for every frame keeps the pictures close to each other.
      seed: 20261007,
    })),
    {
      timeoutMs: 60 * 60_000,
      onComplete: (done) => {
        markStage(job, `アバターの表情を作っています（${done}/${frames.length}）`);
      },
    },
  );
  const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (images.length < frames.length)
    throw new Error(`アバターの絵が足りません（${images.length}/${frames.length}枚）`);
  const out = avatarDir(character.id);
  await fs.rm(out, { recursive: true, force: true });
  await fs.mkdir(out, { recursive: true });
  for (const [index, frame] of frames.entries()) {
    const image = images[index];
    if (!image) continue;
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", image, path.join(out, `${frame.key}.png`)],
      { timeoutMs: 60_000 },
    );
  }
  await fs.rm(work, { recursive: true, force: true });
  // The background goes (transparent PNG) and every picture is cut to the same box around her.
  markStage(job, "背景を透明にしています");
  await runTool(WAN2GP_PYTHON, [REMOVE_BG_SCRIPT, out], {
    cwd: WAN2GP_DIR,
    timeoutMs: 15 * 60_000,
  });
  job.file = path.join(out, "normal_closed.png");
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/** Where a character's mini pictures (the helper walking about the app) are kept. */
export function miniDir(characterId: string): string {
  return path.join(characterAssetDir(characterId), "mini");
}

/** The mini character's pictures (front, three-quarter, side, back views and a wave), drawn from the sheet. */
export async function runCharacterMini(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（ミニキャラの元になります）");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA))))
    throw new Error("絵を作る部品（Viggle Turbo）が見つかりません");
  const work = path.join(characterAssetDir(character.id), "mini-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, `ミニキャラを作っています（0/${MINI_FRAMES.length}）`);
  const produced = await runWan2gpManifest(
    job,
    work,
    MINI_FRAMES.map((frame) => ({
      ...qwenTurboSettings(),
      prompt: miniFramePrompt(frame),
      image_refs: [source],
      video_prompt_type: "KI",
      resolution: "832x1216",
      seed: 20261009,
    })),
    {
      timeoutMs: 60 * 60_000,
      onComplete: (done) => {
        markStage(job, `ミニキャラを作っています（${done}/${MINI_FRAMES.length}）`);
      },
    },
  );
  const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (images.length < MINI_FRAMES.length)
    throw new Error(`ミニキャラの絵が足りません（${images.length}/${MINI_FRAMES.length}枚）`);
  const out = miniDir(character.id);
  await fs.rm(out, { recursive: true, force: true });
  await fs.mkdir(out, { recursive: true });
  for (const [index, frame] of MINI_FRAMES.entries()) {
    const image = images[index];
    if (image)
      await runTool(
        VOICE_FFMPEG,
        ["-y", "-loglevel", "error", "-i", image, path.join(out, `${frame.key}.png`)],
        { timeoutMs: 60_000 },
      );
  }
  await fs.rm(work, { recursive: true, force: true });
  markStage(job, "背景を透明にしています");
  await runTool(WAN2GP_PYTHON, [REMOVE_BG_SCRIPT, out], {
    cwd: WAN2GP_DIR,
    timeoutMs: 15 * 60_000,
  });
  job.file = path.join(out, "mini_front.png");
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/**
 * Makes a 3D model (GLB) from the character's sheet with ComfyUI (Pixal3D + TRELLIS.2) and adds it
 * to her models. Showing it in the chat is a separate step (Live2D is shown; VRM / glb are kept).
 */
export async function runCharacterModel3d(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（3Dモデルの元になります）");
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const work = path.join(characterAssetDir(character.id), "model3d-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  const glb = path.join(work, "model.glb");
  await renderModel3d(source, glb, {
    onStage: (stage) => markStage(job, stage),
    isCancelled: () => job.cancelRequested === true,
    cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
  });
  markStage(job, "3Dモデルを登録しています");
  await importGeneratedModel(character.id, glb, `${character.name}の3Dモデル`);
  await fs.rm(work, { recursive: true, force: true });
  // A small picture of the source stands for the job in the lists.
  const thumb = path.join(outDir(), `${job.id}.png`);
  await fs.copyFile(source, thumb);
  job.file = thumb;
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/**
 * The newest finished front sheet of a character becomes its training reference when none was
 * chosen in the app (a chat asks for training in one go). Returns the updated entry.
 */
export async function adoptLatestSheet(character: LibraryCharacter): Promise<LibraryCharacter> {
  if (character.refImage && (await exists(character.refImage))) return character;
  const sheets = (await listMediaJobs(10_000)).filter(
    (job) =>
      job.kind === "comic" &&
      job.status === "done" &&
      job.comic?.characterId === character.id &&
      job.comic?.view !== "turnaround" &&
      !job.comic?.sample &&
      job.file,
  );
  const latest = sheets[0];
  if (!latest?.file || !(await exists(latest.file))) return character;
  const icon = path.join(characterAssetDir(character.id), "icon.png");
  await makeCharacterIcon(latest.file, icon).catch(() => undefined);
  const updated = {
    ...character,
    refImage: latest.file,
    sheetJobId: latest.id,
    icon,
    updatedAt: Date.now(),
  };
  await saveCharacter(updated);
  return updated;
}

/** Square face picture from a sheet (anime face detector; the upper middle when none is found). */
export async function makeCharacterIcon(sheet: string, icon: string): Promise<void> {
  await runTool(WAN2GP_PYTHON, [MANGA_COMPOSE, "--icon", sheet, icon], { timeoutMs: 2 * 60_000 });
}
