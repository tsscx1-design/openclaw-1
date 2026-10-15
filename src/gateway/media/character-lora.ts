import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  characterAssetDir,
  characterLook,
  DATASET_VIEWS,
  datasetCaption,
  getCharacter,
  type LibraryCharacter,
  saveCharacter,
  triggerFor,
} from "../comic-characters.js";
import { adoptLatestSheet } from "./character-avatar.js";
import { sdxlModelOf, sdxlSpecOf } from "./config.js";
import { acquireGpu, latestTrainingState, trainingDatasetCount } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { exists } from "./paths.js";
import { markStage, runTool, runWan2gpManifest } from "./runner.js";
import type { MediaJob } from "./types.js";

const SD_SCRIPTS_DIR = "D:\\media-tools\\sd-scripts";

export const SD_SCRIPTS_PYTHON = path.join(SD_SCRIPTS_DIR, "venv", "Scripts", "python.exe");

/**
 * Qwen Image 2.1 (7B, already installed) with the Viggle Turbo LoRA: 6 steps instead of ~30, so a
 * reference picture becomes a new view in about 25 s. The LoRA files sit in Wan2GP's loras folder.
 */
export const QWEN_EDIT_MODEL = "qwen_image_21_7B";

export const QWEN_LORA_DIR = "D:\\media-tools\\Wan2GP\\loras\\qwen21";

export const VIGGLE_TURBO_LORA = "Qwen-Image-2.1-viggle-turbo-v0.3-6step-lora-r256.safetensors";

export const OUTPAINT_LORA = "qwen-image-2.1-outpaint-v2.safetensors";

/** The Wan2GP settings that turn Qwen Image 2.1 into the 6-step editor (plus extra LoRAs, if any). */
export function qwenTurboSettings(extraLoras: string[] = []): Record<string, unknown> {
  return {
    model_type: QWEN_EDIT_MODEL,
    num_inference_steps: 6,
    guidance_scale: 1,
    sample_solver: "viggle_v02",
    activated_loras: [...extraLoras, VIGGLE_TURBO_LORA],
    loras_multipliers: [...extraLoras, VIGGLE_TURBO_LORA].map(() => "1").join(" "),
    negative_prompt: "",
  };
}

const LORA_REPEATS = 8;

const LORA_EPOCHS = 6;

/**
 * Character LoRA: about 24 pictures of the character made from their sheet with Qwen Image Edit
 * (angles, expressions, poses, light; the person stays the same), captioned with a trigger word
 * and only what varies, then a LoRA on the SDXL checkpoint trained with 8 GB settings (U-Net
 * only, gradient checkpointing, 8-bit AdamW, cached latents and text encoder outputs).
 */
export async function runCharacterLora(job: MediaJob): Promise<void> {
  const found = await getCharacter(job.options.comicCharacterId ?? "");
  if (!found) throw new Error("その登場人物はいません");
  // Asked in one go from a chat: the sheet drawn just before (same queue) is the reference.
  const character = await adoptLatestSheet(found);
  if (!character.refImage || !(await exists(character.refImage))) {
    throw new Error("先に設定画を決めてください（学習の元になります）");
  }
  if (!(await exists(SD_SCRIPTS_PYTHON)))
    throw new Error("学習ソフト（sd-scripts）が見つかりません");
  if (!(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error("学習用の絵を作る部品（Viggle Turbo）が見つかりません");
  }
  const update = async (patch: Partial<LibraryCharacter>) => {
    const latest = (await getCharacter(character.id)) ?? character;
    await saveCharacter({ ...latest, ...patch, updatedAt: Date.now() });
  };
  const trigger = triggerFor(character.id);
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "lora-work");
  const imageDir = path.join(work, "img", `${LORA_REPEATS}_${trigger}`);
  try {
    await update({
      loraStatus: "training",
      loraStep: "学習用の絵を作っています",
      loraError: undefined,
    });
    // A training that was cut off (the gateway restarted ...) goes on from where it was: the pictures made for it are kept,
    // and the training itself starts from the last saved epoch (see the resume below).
    const kept = await trainingDatasetCount(imageDir);
    const reuse = kept >= 10;
    if (!reuse) {
      await fs.rm(work, { recursive: true, force: true });
      await fs.mkdir(imageDir, { recursive: true });
    } else {
      markStage(job, "前回の続きから始めます（学習用の絵は、できています）");
      await update({ loraStep: "前回の続きから始めます" });
    }
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    let datasetCount = kept;

    if (!reuse) {
      // 1. Dataset: every view in one Wan2GP session (the 20B editor loads once).
      markStage(job, `学習用の絵を作っています（${DATASET_VIEWS.length}枚・Qwen Image Edit）`);
      const editDir = path.join(work, "edit");
      await fs.mkdir(editDir, { recursive: true });
      const produced = await runWan2gpManifest(
        job,
        editDir,
        DATASET_VIEWS.map((view) => ({
          ...qwenTurboSettings(),
          prompt: `${view.edit} Keep the same anime art style, the same face, hair, eye color and body. Plain composition, no text.`,
          image_refs: [character.refImage],
          video_prompt_type: "KI",
          resolution: "832x1216",
          seed: -1,
        })),
        {
          timeoutMs: 90 * 60_000,
          onComplete: (done) => {
            markStage(job, `学習用の絵を作っています（${done}/${DATASET_VIEWS.length}）`);
            void update({
              loraStep: `学習用の絵 ${done}/${DATASET_VIEWS.length}`,
            });
          },
        },
      );
      const images = produced.filter((f) => /\.(png|jpe?g|webp)$/i.test(f));
      if (images.length < 10) throw new Error(`学習用の絵が足りません（${images.length}枚）`);
      const currentLook = characterLook(character);
      const gender = /\b1boy\b/.test(currentLook)
        ? "1boy"
        : /\b1girl\b/.test(currentLook)
          ? "1girl"
          : "";
      for (const [i, image] of images.entries()) {
        const target = path.join(
          imageDir,
          `${String(i + 1).padStart(2, "0")}${path.extname(image)}`,
        );
        await fs.copyFile(image, target);
        const view = DATASET_VIEWS[i];
        await fs.writeFile(
          target.replace(/\.\w+$/, ".txt"),
          datasetCaption(trigger, gender, view?.tags ?? ""),
        );
      }
      // The sheet itself is the clearest picture of them.
      await fs.copyFile(character.refImage, path.join(imageDir, "00.png"));
      await fs.writeFile(
        path.join(imageDir, "00.txt"),
        datasetCaption(trigger, gender, "full body, standing, front view, white background"),
      );
      datasetCount = images.length + 1;
    }
    // 2. Training.
    const steps = datasetCount * LORA_REPEATS * LORA_EPOCHS;
    markStage(job, `専用学習をしています（約${steps}ステップ）`);
    // The name of the result stays the same when the training goes on, so its saved epochs are found again.
    const trainFile = path.join(work, "train.json");
    const savedName = await fs.readFile(trainFile, "utf-8").then(
      (text) => (JSON.parse(text) as { outName?: string }).outName,
      () => undefined,
    );
    const outName =
      savedName && /^[\w-]+$/.test(savedName)
        ? savedName
        : `${trigger}-${randomBytes(2).toString("hex")}`;
    await fs.writeFile(trainFile, JSON.stringify({ outName }), "utf-8");
    const resumeFrom = await latestTrainingState(dir, outName);
    const model = sdxlSpecOf(sdxlModelOf(job));
    let lastPercent = -1;
    await runTool(
      SD_SCRIPTS_PYTHON,
      [
        "sdxl_train_network.py",
        "--pretrained_model_name_or_path",
        model.file,
        ...(model.vpred ? ["--v_parameterization", "--zero_terminal_snr"] : []),
        "--train_data_dir",
        path.join(work, "img"),
        "--output_dir",
        dir,
        "--output_name",
        outName,
        "--save_model_as",
        "safetensors",
        "--caption_extension",
        ".txt",
        "--resolution",
        "1024,1024",
        "--enable_bucket",
        "--min_bucket_reso",
        "512",
        "--max_bucket_reso",
        "1536",
        "--network_module",
        "networks.lora",
        "--network_dim",
        "16",
        "--network_alpha",
        "8",
        "--network_train_unet_only",
        "--train_batch_size",
        "1",
        "--max_train_epochs",
        String(LORA_EPOCHS),
        "--learning_rate",
        "1e-4",
        "--unet_lr",
        "1e-4",
        "--lr_scheduler",
        "cosine",
        "--lr_warmup_steps",
        "50",
        "--optimizer_type",
        "AdamW8bit",
        "--mixed_precision",
        "fp16",
        "--save_precision",
        "fp16",
        "--gradient_checkpointing",
        "--cache_latents",
        "--cache_latents_to_disk",
        "--cache_text_encoder_outputs",
        "--sdpa",
        "--no_half_vae",
        "--max_data_loader_n_workers",
        "1",
        "--seed",
        "42",
        // every epoch is saved (with the optimizer's state, only the last one is kept), so a cut-off run goes on from there
        "--save_every_n_epochs",
        "1",
        "--save_state",
        "--save_last_n_epochs_state",
        "1",
        ...(resumeFrom ? ["--resume", resumeFrom] : []),
      ],
      {
        cwd: SD_SCRIPTS_DIR,
        timeoutMs: 4 * 60 * 60_000,
        onLine: (line) => {
          // tqdm: "steps:  45%|████▌     | 450/1000 [10:01<12:15, ...]"
          const match = /steps:\s+(\d+)%.*?\|\s*(\d+)\/(\d+)\s*\[[^<]*<([\d:]+)/.exec(line);
          if (!match) return;
          const percent = Number(match[1]);
          if (percent === lastPercent) return;
          lastPercent = percent;
          markStage(job, `専用学習をしています ${percent}%（残り約${match[4]}）`);
          void update({ loraStep: `学習中 ${percent}%（残り約${match[4]}）` });
        },
      },
    );
    const lora = path.join(dir, `${outName}.safetensors`);
    if (!(await exists(lora))) throw new Error("学習結果のファイルができませんでした");
    // the saved epochs and states are not needed any more
    for (const name of await fs.readdir(dir).catch(() => [] as string[])) {
      if (
        name.startsWith(`${outName}-`) &&
        (name.endsWith("-state") || /-\d{6}\.safetensors$/.test(name))
      )
        await fs.rm(path.join(dir, name), { recursive: true, force: true }).catch(() => undefined);
    }
    await update({ loraStatus: "ready", lora, trigger, loraStep: undefined, loraError: undefined });
    job.file = character.refImage;
    job.mime = "image/png";
    job.comic = { seriesId: "", character: character.name, characterId: character.id };
  } catch (err) {
    await update({
      loraStatus: "failed",
      loraStep: undefined,
      loraError: String(err instanceof Error ? err.message : err).slice(0, 300),
    });
    throw err;
  } finally {
    // Keep the dataset pictures for a look; drop the editor's scratch output.
    await fs.rm(path.join(work, "edit"), { recursive: true, force: true }).catch(() => undefined);
  }
}
