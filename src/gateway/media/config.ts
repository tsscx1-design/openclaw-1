import * as path from "node:path";
import { negativeTags } from "../blocked-terms-config.js";
import { type StoreSdxlSpec, storeSpecOf } from "../model-store.js";
import type {
  ImageModel,
  MediaJob,
  MediaOptions,
  SdxlModel,
  SdxlSampler,
  StaticSdxlModel,
  WanImageModel,
} from "./types.js";

export function isWanImageModel(model: unknown): model is WanImageModel {
  return model === "zimage" || model === "klein" || model === "qwenimg" || model === "qwenimgturbo";
}

/** Models that are neither Wan2GP nor SDXL checkpoints: Anima runs in ComfyUI. */
export const isSdxlModel = (model: unknown): model is SdxlModel =>
  Boolean(model) && !isWanImageModel(model) && model !== "anima";

export const MEDIA_MODEL_DIR = "D:\\media-models";

export const IMAGE_MODEL = path.join(
  MEDIA_MODEL_DIR,
  "image",
  "waiIllustriousSDXL_v170.safetensors",
);

/** DMD2 4-step LoRA (tianweiy/DMD2); with LCM it works on SDXL epsilon checkpoints, not V-Pred. */
export const DMD2_LORA = path.join(
  MEDIA_MODEL_DIR,
  "image",
  "lora",
  "dmd2_sdxl_4step_lora_fp16.safetensors",
);

const DMD2_SETTINGS = { sampler: "lcm" as SdxlSampler, steps: 8, cfg: 1.5 };

const PHOTO_NEGATIVE =
  "cartoon, illustration, anime, painting, CGI, 3D render, low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, deformed, watermark, logo, text";

/** SDXL checkpoints served by the resident worker; Z-Image runs in Wan2GP instead. */
export const SDXL_MODELS: Record<
  StaticSdxlModel,
  {
    file: string;
    vpred: boolean;
    /** Photo-real checkpoints get descriptive phrases instead of danbooru tags. */
    photo: boolean;
    sampler: SdxlSampler;
    steps: number;
    cfg: number;
    quality: string;
    negative: string;
  }
> = {
  wai: {
    file: IMAGE_MODEL,
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 6,
    quality: "masterpiece, best quality, amazing quality, very aesthetic, absurdres",
    negative:
      "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
  // NoobAI-XL V-Pred 1.0: recommended Euler with CFG 3.5-5.5 and 28-35 steps.
  noobai: {
    file: path.join(MEDIA_MODEL_DIR, "image", "NoobAI-XL-Vpred-v1.0.safetensors"),
    vpred: true,
    photo: false,
    sampler: "euler_a",
    steps: 30,
    cfg: 4.5,
    quality: "masterpiece, best quality, newest, absurdres, highres",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // LUSTIFY V9 Zenith (photo-real, uncensored): DPM++ 2M SDE Karras, ~30 steps, CFG 4-7.
  lustify: {
    file: path.join(MEDIA_MODEL_DIR, "image", "lustifyNSFWCheckpoint_zenithV9.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 4.5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // CyberRealistic XL Play v10: DPM++ 2M SDE Karras, 30+ steps, CFG 3-5.
  cyberreal: {
    file: path.join(MEDIA_MODEL_DIR, "image", "CyberRealisticXLPlay_V10.0_FP16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 4,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // Asian Realism by Stable Yogi (Illustrious) v2.0: East Asian faces; DPM++ 2M Karras, 20-30 steps, CFG 5-7.
  asianreal: {
    file: path.join(MEDIA_MODEL_DIR, "image", "asianRealismByStable_v20FP16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 28,
    cfg: 5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // Hassaku XL (Illustrious) v3.4: a clean, style-neutral merge; Euler a, CFG 5-6.
  hassaku: {
    file: path.join(MEDIA_MODEL_DIR, "image", "hassakuXL_illustrious_v34.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5.5,
    quality: "masterpiece, best quality, amazing quality, very aesthetic, absurdres",
    negative:
      "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
  // Illustrious XL 1.1 (official): the base most LoRAs are made for; Euler a, CFG 5.
  illustrious: {
    file: path.join(MEDIA_MODEL_DIR, "image", "Illustrious-XL-v1.1.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "masterpiece, best quality, very aesthetic, absurdres",
    negative:
      "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
  // Nova Anime XL IL v19 (NoobAI EPS + Illustrious merge, epsilon): Euler a, CFG 4-6.
  novaanime: {
    file: path.join(MEDIA_MODEL_DIR, "image", "novaAnimeXL_ilV190.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // Plant Milk Walnut (Illustrious / NoobAI merge): Euler a, CFG 5.
  plantmilk: {
    file: path.join(MEDIA_MODEL_DIR, "image", "plantMilk_walnut_illustrious.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "masterpiece, best quality, amazing quality, very aesthetic, absurdres",
    negative:
      "lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
  // Juggernaut XL Ragnarok (photo-real): DPM++ 2M SDE Karras, ~30 steps, CFG 3-6.
  juggernaut: {
    file: path.join(MEDIA_MODEL_DIR, "image", "juggernautXL_ragnarok.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 4.5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // RealVisXL V5.0 (photo-real portraits): DPM++ 2M SDE Karras, 25-30 steps, CFG 4-6.
  realvis: {
    file: path.join(MEDIA_MODEL_DIR, "image", "RealVisXL_V5.0_fp16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "dpmpp_2m_sde_karras",
    steps: 30,
    cfg: 5,
    quality: "photograph, realistic, high detail, sharp focus",
    negative: PHOTO_NEGATIVE,
  },
  // Illustrious XL 2.0（アニメ・本家の土台の後継）
  illust2: {
    file: path.join(MEDIA_MODEL_DIR, "image", "Illustrious-XL-v2.0.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5.5,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // Animagine XL 4.0 opt（アニメ・タグで指示）
  animagine: {
    file: path.join(MEDIA_MODEL_DIR, "image", "animagine-xl-4.0-opt.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "masterpiece, high score, great score, absurdres",
    negative:
      "lowres, bad anatomy, bad hands, text, error, missing finger, extra digits, fewer digits, cropped, worst quality, low quality, low score, bad score, average score, signature, watermark, username, blurry",
  },
  // RouWei 0.8 epsilon（アニメ・成人向けも可）
  rouwei: {
    file: path.join(MEDIA_MODEL_DIR, "image", "rouwei_080_epsilon_fp16.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 26,
    cfg: 6,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // ChenkinNoob-XL V0.5（アニメ・NoobAI系）
  chenkin: {
    file: path.join(MEDIA_MODEL_DIR, "image", "ChenkinNoob-XL-V0.5.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5.5,
    quality: "masterpiece, best quality, newest, high resolution, aesthetic, excellent",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // JANKU v7.77（アニメ・成人向け）
  janku: {
    file: path.join(MEDIA_MODEL_DIR, "image", "JANKUTrainedChenkinNoobai_v777.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 4,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // NTR MIX XIII（アニメ・濃いめ塗り）
  ntrmix: {
    file: path.join(MEDIA_MODEL_DIR, "image", "ntrMIXIllustriousXL_xiii.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 25,
    cfg: 5.5,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // Anime Desire v3（アニメ・成人向け）
  desire: {
    file: path.join(MEDIA_MODEL_DIR, "image", "animeDesireIllustrious_v3.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "best quality, masterpiece, absurdres, newest",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // CyberIllustrious Anime V4.0（アニメ）
  cyberanime: {
    file: path.join(MEDIA_MODEL_DIR, "image", "CyberIllustrious_Anime_V4.0.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 30,
    cfg: 5,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // CyberIllustrious Semi-Real V3.0（半実写）
  cybersemi: {
    file: path.join(MEDIA_MODEL_DIR, "image", "CyberIllustrious_Semi-Real_V3.0.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 30,
    cfg: 5,
    quality: "masterpiece, best quality, newest, absurdres, highres, realistic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // illustrious_pencil-XL v5（アニメ・日本寄りのイラスト）
  pencil: {
    file: path.join(MEDIA_MODEL_DIR, "image", "illustrious_pencil-XL-v5.0.0.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "masterpiece, best quality, newest, absurdres, highres, very aesthetic",
    negative:
      "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark",
  },
  // CyberRealistic Pony V18（実写・成人向けも可）
  cyberpony: {
    file: path.join(MEDIA_MODEL_DIR, "image", "CyberRealisticPony_V18.0_F16.safetensors"),
    vpred: false,
    photo: true,
    sampler: "euler_a",
    steps: 30,
    cfg: 5,
    quality: "score_9, score_8_up, score_7_up, photograph, realistic, high detail",
    negative:
      "score_6, score_5, score_4, cartoon, illustration, anime, painting, CGI, 3D render, low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, deformed, watermark, logo, text",
  },
  // Realism Illustrious v5.5 (semi-real, Illustrious): booru tags; Euler a, CFG 4-6.
  realismil: {
    file: path.join(MEDIA_MODEL_DIR, "image", "realismIllustrious_v55FP16.safetensors"),
    vpred: false,
    photo: false,
    sampler: "euler_a",
    steps: 28,
    cfg: 5,
    quality: "realistic, photorealistic, masterpiece, best quality, very aesthetic, absurdres",
    negative:
      "anime, cartoon, illustration, 3d, lowres, worst quality, bad quality, bad anatomy, bad hands, extra digits, fewer digits, jpeg artifacts, signature, watermark, username, blurry, sketch",
  },
};

/** Settings of an SDXL model: a built-in one, or one downloaded from the model store. */
export function sdxlSpecOf(model: SdxlModel): StoreSdxlSpec {
  return SDXL_MODELS[model as StaticSdxlModel] ?? storeSpecOf(model) ?? SDXL_MODELS.wai;
}

/** The SDXL model a job uses; anything else (unset, Z-Image's sd.cpp fallback) means WAI. */
export function sdxlModelOf(job: MediaJob): SdxlModel {
  const model = job.options.imageModel;
  return isSdxlModel(model) ? model : "wai";
}

/** Sampling settings for an SDXL job; "fast" swaps in DMD2 where the checkpoint allows it. */
export function sdxlSampling(
  model: SdxlModel,
  fast: boolean,
): { sampler: SdxlSampler; steps: number; cfg: number; lora?: string } {
  const spec = sdxlSpecOf(model);
  // CFG stays above 1 so the negative prompt (including the adult-only one) still applies.
  if (fast && !spec.vpred) return { ...DMD2_SETTINGS, lora: DMD2_LORA };
  return { sampler: spec.sampler, steps: spec.steps, cfg: spec.cfg };
}

const VIDEO_DIR = path.join(MEDIA_MODEL_DIR, "video");

export const VIDEO_MODEL = path.join(VIDEO_DIR, "wan2.2-i2v-rapid-aio-v10-nsfw-Q3_K_S.gguf");

export const VIDEO_VAE = path.join(VIDEO_DIR, "wan_2.1_vae.safetensors");

export const VIDEO_T5 = path.join(VIDEO_DIR, "umt5-xxl-encoder-Q8_0.gguf");

export const VIDEO_CLIP_VISION = path.join(VIDEO_DIR, "clip_vision_h.safetensors");

// Wan 2.2 Remix v3 I2V (photo-real, uncensored): MoE pair, 4-8 steps at CFG 1, shift 5-10.
export const REMIX_HIGH = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vHighV30-Q3_K_M.gguf");

export const REMIX_LOW = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vLowV30-Q3_K_M.gguf");

/** RIFE v4.26 frame interpolation (Wan2GP's weights) for the sd.cpp engines. */
export const RIFE_SCRIPT = "D:\\media-tools\\image\\rife_video.py";

export const MUSIC_MODEL_DIR = path.join(MEDIA_MODEL_DIR, "music");

// acestep.cpp looks models up by full file name, .gguf included.
export const MUSIC_LM = "acestep-5Hz-lm-4B-Q8_0.gguf";

export const MUSIC_DIT = "acestep-v15-turbo-Q8_0.gguf";

export const JOB_TIMEOUT_MS = 40 * 60_000;

export const MAX_QUEUE = 4;

/** Finished results are kept until the user deletes them; only failed attempts are trimmed. */
export const KEEP_FAILED = 30;

export const MAX_TEXT = 500;

const VIDEO_NEGATIVE =
  "static, still image, blurry, low quality, distorted, deformed, extra limbs, flicker, watermark, text";

/**
 * 動画のネガティブ。dist/blocked-terms.json の videoNegative を、どの動画にも足す。
 * 成人向けのときは adultOnly も足す。
 */
export function videoNegative(sexual: boolean): string {
  return [VIDEO_NEGATIVE, negativeTags("videoNegative"), sexual ? negativeTags("adultOnly") : ""]
    .filter(Boolean)
    .join(", ");
}

export const SIZES: Record<
  NonNullable<MediaOptions["aspect"]>,
  { image: [number, number]; video: [number, number] }
> = {
  square: { image: [1024, 1024], video: [624, 624] },
  // SDXL buckets with the same shape as the video frame, so a first frame needs no crop
  portrait: { image: [768, 1344], video: [480, 832] },
  landscape: { image: [1344, 768], video: [832, 480] },
};

export const WAN2GP_DIR = "D:\\media-tools\\Wan2GP";

export const WAN2GP_PYTHON = path.join(WAN2GP_DIR, "venv", "Scripts", "python.exe");

export const WAN2GP_RUNNER = path.join(WAN2GP_DIR, "openclaw_job.py");

export const QWEN_TURBO_RUNNER = path.resolve("scripts/media/external/qwen_image_turbo_job.py");

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

/** Motion comic runner (key images, Wan 2.2 cuts, film) and its sound effects; outside the repo. */
export const MOTION_SCRIPT = "D:\\media-tools\\motion-comic\\motion_comic.py";

/** A cut of 3 s took ~11 min at 832x480; the fast profile (640x368, Real-ESRGAN) takes ~6 min, see clips.py. */
export const MOTION_TIMEOUT_MS = 4 * 60 * 60_000;

/** The checkpoint file of the key pictures: the chosen SDXL model, else the standard one. */
export function motionModelFile(model?: ImageModel): string {
  return (model && isSdxlModel(model) ? sdxlSpecOf(model) : SDXL_MODELS.wai).file;
}
