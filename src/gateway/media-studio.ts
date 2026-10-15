import { execFile, spawn } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";
import { ANIMA_FILES, ANIMA_NEGATIVE, renderAnima } from "./anima-comfy.js";
import {
  allAvatarFrames,
  ANATOMY_CHEST,
  ANATOMY_GROIN,
  avatarFramePrompt,
  MINI_FRAMES,
  miniFramePrompt,
} from "./avatar-frames.js";
import { importGeneratedModel } from "./avatar-models.js";
import {
  assertAgeAllowed,
  assertNoBlockedTerms,
  isSexualText,
  negativeTags,
} from "./blocked-terms-config.js";
import { runTagger } from "./character-creator.js";
import { characterMotionPrompt, readCharacterMotionProfile } from "./character-motion-profile.js";
import {
  listeningLoopbackPid,
  readChatEngine,
  setChatEngineBusyCheck,
  startSelectedChatEngine,
  stopStrataProcesses,
} from "./chat-engine.js";
import { registerChatModelRestore } from "./chat-model-gate.js";
import {
  lookWithOutfit,
  type CharacterSpec,
  characterAssetDir,
  characterOptionTags,
  characterLook,
  creatorSystemPrompt,
  DATASET_VIEWS,
  breastTags,
  characterOptionIds,
  imageHeightTags,
  stripBreastTags,
  mergeLookTags,
  parseCreatorReply,
  specSummary,
  datasetCaption,
  getCharacter,
  listCharacters,
  type LibraryCharacter,
  resolveCast,
  saveCharacter,
  updateCharacter,
  triggerFor,
} from "./comic-characters.js";
import {
  castSystemPrompt,
  clip,
  COMIC_PANELS_PER_EPISODE,
  COMIC_PANELS_PER_PART,
  COMIC_OUTLINE_SCHEMA,
  comicScriptSchema,
  type ComicCharacter,
  type ComicPanel,
  type ComicScript,
  type ComicSeries,
  episodeOutlinePrompt,
  episodeSystemPrompt,
  FRAME_ASPECT,
  firstJsonObject,
  getComicSeries,
  parseCast,
  parseComicScript,
  renderPanel,
  saveComicSeries,
  speakerPositions,
  type StagePosition,
} from "./comic-series.js";
import {
  FACEFUSION_DIR,
  FACEFUSION_PYTHON,
  type SwapOptions,
  estimateSwap,
  faceFusionPath,
  probeMedia,
  resolveInputFile,
  swapFrameCount,
  swapPassArgs,
} from "./face-swap.js";
import { readGenStyle, type GenStyle } from "./gen-style.js";
import { enterGroupChatMode } from "./group-chat-model.js";
import {
  IRODORI_DIR,
  irodoriInstalled,
  stopIrodoriWorker,
  synthesizeIrodori,
} from "./irodori-tts.js";
import { waitForChatTurnEnd } from "./main-llm-idle.js";
import { loadBook, previouslyText, removeBook, saveBook } from "./manga-book-store.js";
import { type PageLayout, planPage, slotAspect } from "./manga-layout.js";
import {
  type BookPanel,
  type BubbleKind,
  bookOutlinePrompt,
  bookPagePrompt,
  MANGA_DEFAULT_PAGES,
  MANGA_EXTRA_NEGATIVE,
  MANGA_MAX_PAGES,
  MANGA_MIN_PAGES,
  MANGA_PANEL_ASPECTS,
  MANGA_PANEL_COUNT,
  type MangaLayout,
  mangaScriptSystemPrompt,
  parseBookOutline,
  parseBookPage,
  parseMangaScript,
  mergeBookCast,
} from "./manga-script.js";
import { createWork, getWork, touchWork } from "./manga-works.js";
import {
  generationCudaDevice,
  generationCudaDevices,
  hasDedicatedMediaGpu,
  mediaProcessEnv,
  resolveGpuMode,
} from "./media-gpu.js";
import type {
  MediaProductionOptions,
  MediaQualityMode,
  MediaVoiceTimelineCue,
} from "./media-production-controls.js";
import { adultLead, adultSentence } from "./minor-safeguards.js";
import { isStoreModelId, storeSpecOf, type StoreSdxlSpec } from "./model-store.js";
import { renderModel3d } from "./model3d.js";
import {
  motionImageNegative,
  type MotionMinutes,
  type MotionScript,
  type MotionPhase,
  type MotionTemplateId,
  type MotionOptionId,
  MOTION_TEMPLATES,
  motionOptionNotes,
  motionCuts,
  motionKeyJobs,
  motionSummary,
  motionSystemPrompt,
  parseMotionScript,
} from "./motion-comic.js";
import {
  applyMotionFlow,
  type VoiceDrama,
  DEFAULT_MOTION_SFX,
  MOTION_TALK,
  phasesInFlow,
  sfxForCut,
  talkNote,
  voiceStyleNote,
  type MotionFlowStep,
  type MotionSfxId,
  type MotionTalk,
  type MotionVoiceStyle,
} from "./motion-extras.js";
import { resolveOpenClawHome } from "./partner-store.js";
import { referenceClip } from "./partner-voice-clone.js";
import { analyzePhoto, createFaceReference } from "./photo-analysis.js";
import {
  adultBaseFile,
  PRECISE_SAMPLE_KEYS,
  loadSampleDescriptions,
  loadSampleIndex,
  resolveSampleSubject,
  sampleFileName,
  samplePrompt,
  samplesDir,
  saveSampleIndex,
} from "./sample-factory.js";
import { upscaleWithSeedVr } from "./seedvr-comfy.js";
import {
  SPECIALIST_WORKERS,
  startSpecialistWorker,
  waitForWorkerReady,
} from "./specialist-router.js";
import { listVoiceProjects } from "./voice-studio.js";

// Local image / video / music generation on the RTX 4060 (8 GB).
// The main chat LLM holds almost all VRAM, so a job stops it (the watchdog pause flag keeps it
// down) and restarts it once the queue is empty. Jobs run one at a time.
//   image: stable-diffusion.cpp (CUDA) + WAI-Illustrious SDXL (uncensored anime)
//   video: stable-diffusion.cpp vid_gen + Wan 2.2 14B Rapid AIO NSFW (image-to-video, 4 steps);
//          text-to-video renders a still with the image model first, then animates it
//   music: acestep.cpp (ace-lm + ace-synth) + ACE-Step 1.5 turbo; lyrics are written by its LM

export type MediaKind =
  | "image"
  | "video"
  | "music"
  | "manga"
  | "comic"
  | "voice"
  | "motion"
  | "faceswap";
export type MediaJobStatus = "queued" | "running" | "done" | "error";

export type MediaJob = {
  id: string;
  kind: MediaKind;
  status: MediaJobStatus;
  /** What the user asked for, as typed. */
  request: string;
  /** Short Japanese progress note for the app. */
  stage?: string;
  createdAt: number;
  startedAt?: number;
  finishedAt?: number;
  durationSec?: number;
  error?: string;
  /** Milliseconds per stage, kept with the result to diagnose slow runs. */
  timings?: Record<string, number>;
  file?: string;
  mime?: string;
  options: MediaOptions;
  /** Prompt used by generators after character and scene continuity have been resolved. */
  productionPrompt?: string;
  /** Result of the optional post-generation quality pass. */
  quality?: { mode: MediaQualityMode; status: "done" | "warning"; note: string };
  /** Set when the user deletes the job while it runs; the runner then discards it. */
  cancelRequested?: boolean;
  /**
   * Started from a conversation (the generation assistant): the chat model is still writing its
   * reply, so the job waits for it before taking the GPU, and its end is announced to that
   * partner's chat (or the phone when no partner is named).
   */
  chat?: { partnerId?: string };
  /** motion: the working folder (script, voices, key images, cuts) and whether this is a draft */
  motion?: { dir: string; draft?: boolean; preview?: boolean };
  /** About how many seconds the job takes (set when it starts; the app shows what is left). */
  estimateSec?: number;
  /** Pictures of this job are drawn on both GPUs at once (stage text comes from the first). */
  parallelPictures?: boolean;
  /** manga: the title and each panel's words, as written by the script writer. */
  manga?: { title: string; lines: string[]; volume?: number; cover?: boolean };
  /** comic: an episode is several strips (parts); file is the first. */
  files?: string[];
  comic?: {
    seriesId: string;
    episodeNo?: number;
    title?: string;
    character?: string;
    characterId?: string;
    view?: "front" | "turnaround" | "three_view" | "360";
    /** A sheet drawn in the adult version. */
    adult?: boolean;
    /** A picture of the sample factory (not a sheet of the character). */
    sample?: boolean;
    targetJobId?: string;
    panel?: number;
  };
};

export type MediaOptions = {
  /** image/video framing */
  aspect?: "square" | "portrait" | "landscape";
  /** video: animate this finished image job instead of drawing a new still */
  sourceImageJobId?: string;
  /** video: base64 image supplied by the app */
  sourceImageBase64?: string;
  /**
   * video engine: "standard" = sd.cpp + Wan 2.2 14B Rapid NSFW (2 s); the others run in Wan2GP:
   * "long" = Wan 2.2 SVI 2 Pro Lightning (sliding windows), "audio" = LTX-2 distilled with sound,
   * "fast" = FastWan 2.2 5B (3 steps).
   */
  engine?: VideoEngine;
  /** video: interpolate to twice the frame rate (RIFE) so motion looks smoother; same length */
  smooth?: boolean;
  /** image model for images and for the first frame of text-to-video */
  imageModel?: ImageModel;
  /** SDXL (not V-Pred): sample in 8 steps with the DMD2 LoRA, about 3x faster, slightly different look */
  fast?: boolean;
  /** music, and video length for the Wan2GP engines */
  lyrics?: string;
  instrumental?: boolean;
  durationSec?: number;
  /** manga: a vertical 4-panel strip, one page of five panels, or a multi-page book */
  mangaLayout?: MangaLayout;
  /** manga "book": how many pages (2-16) */
  mangaPages?: number;
  /** manga "book": the job of the volume this one continues (same cast, story carried on) */
  mangaSequelOf?: string;
  /** manga "book": false = only the characters picked from the library, nobody new is made */
  mangaAutoCast?: boolean;
  /** manga "book": false = no cover (the default is to draw one as the first file) */
  mangaCover?: boolean;
  /** manga: "mono" makes black-and-white pages with screentone; the default keeps the colours */
  mangaTone?: "color" | "mono";
  /** comic: the series, and what to do (a new episode, a character sheet, redraw one panel) */
  comicSeriesId?: string;
  comicTask?:
    | "episode"
    | "sheet"
    | "redraw"
    | "lora"
    | "outpaint"
    | "avatar"
    | "sample"
    | "model3d"
    | "mini";
  /** sample: the setting ("opt:<id>", "motion:<id>", "pose:<id>", "custom:<id>"), the base picture and an instruction. */
  sampleKey?: string;
  sampleBase?: "normal" | "adult";
  /** The editor for a sample: "fast" (Qwen Image 2.1, 6 steps, the default), "precise" (30 steps) or "edit2509". */
  sampleEngine?: "fast" | "precise" | "edit2509";
  sampleNote?: string;
  /** sample: many settings in one job (a whole genre); the model loads once and both GPUs share the work. */
  sampleKeys?: string[];
  /** sample: make the adult base picture itself (the standing picture without clothes). */
  sampleAdultBase?: boolean;
  /** outpaint: how much to add on each side, as a fraction of the picture (0.5 = half as much again) */
  comicOutpaint?: { top: number; bottom: number; left: number; right: number };
  comicCharacter?: string;
  /** sheet / lora: the character library entry */
  comicCharacterId?: string;
  /** voice: speak with this voice-studio voice, or a voice described in words */
  voiceId?: string;
  voiceCaption?: string;
  /** 2人目キャラクターID（2人シーン用） */
  motionCharacterId2?: string;
  /** 2人目衣装名（2人シーン用） */
  motionOutfit2?: string;
  /** voice: Irodori model ("large" is more natural; the GPU is taken for it) */
  irodoriModel?: "small" | "large" | "fast";
  /** voice: write a long audio drama about the request (minutes) with a background sound, instead of reading the lines given */
  voiceDrama?: VoiceDrama;
  /**
   * When true, the gateway requests phoneme-level timing from Irodori TTS so the
   * motion-comic renderer can drive lip-sync frame-accurately.
   * Requires Irodori ≥ 2.1 with the /phonemes endpoint.
   */
  phonemeTimingSync?: boolean;
  /** sheet: one front view (the training reference) or a front/side/back turnaround */
  /** sheet view mode:
   *  front      = 正面立ち絵1枚（学習用リファレンス）
   *  three_view = 三面図（正面・側面・背面を横並びで正確なレイアウト）
   *  360        = 360度ターンアラウンド（8方向、2×4グリッド）
   *  (旧) turnaround はthree_viewの別名として互換維持
   */
  comicSheetView?: "front" | "three_view" | "360" | "turnaround";
  /** sheet: generate nude / adult version alongside */
  comicSheetAdult?: boolean;
  /** sheet (front view): the pose she is drawn in (Japanese or English; empty = standing, arms at the sides) */
  comicSheetPose?: string;
  /** sheet: base64 reference photo for character creation from image */
  comicSheetRefImageBase64?: string;
  comicTargetJobId?: string;
  comicPanel?: number;
  /** sheet: draw the character in this outfit (by name) */
  comicOutfit?: string;
  /** motion: about how long the film is, and whether it ends with fellatio (else the writer decides) */
  motionMinutes?: MotionMinutes;
  motionFellatio?: boolean;
  /** motion: the heroine is this character library entry (look, and its LoRA once trained) */
  motionCharacterId?: string;
  /** motion: stop after the key images and show them (a draft to approve before the video) */
  motionDraft?: boolean;
  /** motion: position / scene template (MOTION_TEMPLATES), and the heroine's outfit by name */
  motionTemplate?: MotionTemplateId;
  motionOutfit?: string;
  /** motion: any number of options (piercings, womb tattoo, collar, bondage, fainting, virgin...) */
  motionOptions?: MotionOptionId[];
  /** motion: continue a copy of this draft job; with motionRedrawKeys, redraw those first */
  motionContinueJobId?: string;
  /** Rebuild the soundtrack of a finished motion comic, keeping its generated images and cuts. */
  motionRevoiceJobId?: string;
  motionRedrawKeys?: string[];
  /** motion: 多人数シーン（2〜4人）の人数 */
  motionSceneCount?: number;
  /** motion: 3人目キャラクターID */
  motionCharacterId3?: string;
  motionOutfit3?: string;
  /** motion: 4人目キャラクターID */
  motionCharacterId4?: string;
  motionOutfit4?: string;
  /** motion: 前段シーン（着衣→脱衣→挿入）カスタムステップ */
  motionApproachSteps?: Array<{ label: string; tags: string; fontStyle: string }>;
  /** motion: how much she talks, kinds of voice, effect sounds, and the order of the cuts */
  motionTalk?: MotionTalk;
  motionVoiceStyles?: MotionVoiceStyle[];
  motionSfx?: MotionSfxId[];
  motionFlow?: MotionFlowStep[];
  /** motion: take the cast, voice, look and place from this finished motion comic */
  motionInheritJobId?: string;
  /** motion: join these finished films in order, with a made-up bridge of this many seconds between each */
  motionJoinJobIds?: string[];
  motionBridgeSeconds?: number;
  /** motion join: where each film ends (seconds from its start; null keeps it whole), to branch into another position from the middle */
  motionJoinTrimSec?: Array<number | null>;
  /** image: make this finished picture sharper and larger with SeedVR2 (the shortest edge becomes upscaleResolution) */
  upscaleSourceJobId?: string;
  upscaleResolution?: number;
  upscaleModel?: "3b" | "7b";
  /** motion: the model the key pictures are drawn with */
  motionImageModel?: ImageModel;
  /** motion: stop after one sample picture, to be approved before the rest is made (default on) */
  motionPreview?: boolean;
} & SwapOptions &
  MediaProductionOptions;

/** "standard" and "remix" run in sd.cpp; the others in Wan2GP. */
export type VideoEngine = "standard" | "remix" | "long" | "audio" | "fast" | "h3";
type SdcppVideoEngine = "standard" | "remix";
export type ImageModel =
  | "wai" // WAI Illustrious SDXL（アニメ標準）
  | "noobai" // NoobAI XL V-Pred（アニメ成人向け強）
  | "zimage" // Z-Image Turbo（構図が正確・日本人の実写も自然）
  | "anima" // Anima 2B（ComfyUI・アニメ絵。タグと文章の両方で指示できる）
  | "klein" // FLUX.2 Klein 9B（Wan2GP・4ステップの高速モデル。文章の指示に強い）
  | "qwenimg" // Qwen Image 2.1（Wan2GP・6ステップ。文章の指示と日本語に強い）
  | "qwenimgturbo" // Qwen Image 2.1 Turbo（Wan2GP・公式8ステップ。高速生成）
  | "lustify" // Lustify V9 Zenith（実写）
  | "cyberreal" // CyberRealistic XL（実写・高解像）
  | "asianreal" // Asian Realism（アジア系実写）
  | "hassaku" // Hassaku XL (Illustrious) v3.4（アニメ・癖のない明るい絵）
  | "illustrious" // Illustrious XL 1.1（アニメ・本家の土台）
  | "novaanime" // Nova Anime XL IL v19（アニメ・2.5D寄り）
  | "plantmilk" // Plant Milk Walnut（アニメ・Illustrious と NoobAI の混ぜモデル）
  | "juggernaut" // Juggernaut XL Ragnarok（実写・定番）
  | "realvis" // RealVisXL V5.0（実写・人物写真）
  | "realismil" // Realism Illustrious v5.5（半実写・booru タグで指示）
  | "illust2" // Illustrious XL 2.0（アニメ・本家の土台の後継）
  | "animagine" // Animagine XL 4.0 opt（アニメ・タグで指示）
  | "rouwei" // RouWei 0.8 epsilon（アニメ・成人向けも可）
  | "chenkin" // ChenkinNoob-XL V0.5（アニメ・NoobAI系）
  | "janku" // JANKU v7.77（アニメ・成人向け）
  | "ntrmix" // NTR MIX XIII（アニメ・濃いめ塗り）
  | "desire" // Anime Desire v3（アニメ・成人向け）
  | "cyberanime" // CyberIllustrious Anime V4.0（アニメ）
  | "cybersemi" // CyberIllustrious Semi-Real V3.0（半実写）
  | "pencil" // illustrious_pencil-XL v5（アニメ・日本寄りのイラスト）
  | "cyberpony" // CyberRealistic Pony V18（実写・成人向けも可）
  | `st_${string}`; // a model downloaded from the model store (SDXL type)
/** Still-image models that run in Wan2GP instead of the SDXL image worker. */
export type WanImageModel = "zimage" | "klein" | "qwenimg" | "qwenimgturbo";
export function isWanImageModel(model: unknown): model is WanImageModel {
  return model === "zimage" || model === "klein" || model === "qwenimg" || model === "qwenimgturbo";
}
/** Models that are neither Wan2GP nor SDXL checkpoints: Anima runs in ComfyUI. */
const isSdxlModel = (model: unknown): model is SdxlModel =>
  Boolean(model) && !isWanImageModel(model) && model !== "anima";
type SdxlModel = Exclude<ImageModel, WanImageModel | "anima">;
type StaticSdxlModel = Exclude<SdxlModel, `st_${string}`>;
type SdxlSampler = "euler_a" | "dpmpp_2m_sde_karras" | "lcm";

const MEDIA_MODEL_DIR = "D:\\media-models";
const IMAGE_MODEL = path.join(MEDIA_MODEL_DIR, "image", "waiIllustriousSDXL_v170.safetensors");
/** DMD2 4-step LoRA (tianweiy/DMD2); with LCM it works on SDXL epsilon checkpoints, not V-Pred. */
const DMD2_LORA = path.join(
  MEDIA_MODEL_DIR,
  "image",
  "lora",
  "dmd2_sdxl_4step_lora_fp16.safetensors",
);
const DMD2_SETTINGS = { sampler: "lcm" as SdxlSampler, steps: 8, cfg: 1.5 };

const PHOTO_NEGATIVE =
  "cartoon, illustration, anime, painting, CGI, 3D render, low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, deformed, watermark, logo, text";

/** SDXL checkpoints served by the resident worker; Z-Image runs in Wan2GP instead. */
const SDXL_MODELS: Record<
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
function sdxlSpecOf(model: SdxlModel): StoreSdxlSpec {
  return SDXL_MODELS[model as StaticSdxlModel] ?? storeSpecOf(model) ?? SDXL_MODELS.wai;
}

/** The SDXL model a job uses; anything else (unset, Z-Image's sd.cpp fallback) means WAI. */
function sdxlModelOf(job: MediaJob): SdxlModel {
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
const VIDEO_MODEL = path.join(VIDEO_DIR, "wan2.2-i2v-rapid-aio-v10-nsfw-Q3_K_S.gguf");
const VIDEO_VAE = path.join(VIDEO_DIR, "wan_2.1_vae.safetensors");
const VIDEO_T5 = path.join(VIDEO_DIR, "umt5-xxl-encoder-Q8_0.gguf");
const VIDEO_CLIP_VISION = path.join(VIDEO_DIR, "clip_vision_h.safetensors");
// Wan 2.2 Remix v3 I2V (photo-real, uncensored): MoE pair, 4-8 steps at CFG 1, shift 5-10.
const REMIX_HIGH = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vHighV30-Q3_K_M.gguf");
const REMIX_LOW = path.join(VIDEO_DIR, "remix", "wan22RemixT2VI2V_i2vLowV30-Q3_K_M.gguf");
/** RIFE v4.26 frame interpolation (Wan2GP's weights) for the sd.cpp engines. */
const RIFE_SCRIPT = "D:\\media-tools\\image\\rife_video.py";
const MUSIC_MODEL_DIR = path.join(MEDIA_MODEL_DIR, "music");
// acestep.cpp looks models up by full file name, .gguf included.
const MUSIC_LM = "acestep-5Hz-lm-4B-Q8_0.gguf";
const MUSIC_DIT = "acestep-v15-turbo-Q8_0.gguf";

const JOB_TIMEOUT_MS = 40 * 60_000;
const MAX_QUEUE = 4;
/** Finished results are kept until the user deletes them; only failed attempts are trimmed. */
const KEEP_FAILED = 30;
const MAX_TEXT = 500;

const VIDEO_NEGATIVE =
  "static, still image, blurry, low quality, distorted, deformed, extra limbs, flicker, watermark, text";

/**
 * 動画のネガティブ。dist/blocked-terms.json の videoNegative を、どの動画にも足す。
 * 成人向けのときは adultOnly も足す。
 */
function videoNegative(sexual: boolean): string {
  return [VIDEO_NEGATIVE, negativeTags("videoNegative"), sexual ? negativeTags("adultOnly") : ""]
    .filter(Boolean)
    .join(", ");
}

const SIZES: Record<
  NonNullable<MediaOptions["aspect"]>,
  { image: [number, number]; video: [number, number] }
> = {
  square: { image: [1024, 1024], video: [624, 624] },
  // SDXL buckets with the same shape as the video frame, so a first frame needs no crop
  portrait: { image: [768, 1344], video: [480, 832] },
  landscape: { image: [1344, 768], video: [832, 480] },
};

// ---------- Paths ----------

function home(): string {
  return resolveOpenClawHome();
}
function sdCli(): string {
  return path.join(home(), "image", "bin-cuda", "sd-cli.exe");
}
function aceBin(name: "ace-lm" | "ace-synth"): string {
  return path.join(home(), "music", "src", "build", "Release", `${name}.exe`);
}
function outDir(): string {
  return path.join(home(), "media", "out");
}

/** Finished files live in one folder per kind (out/image, out/video, out/music). */
async function resultPath(job: MediaJob, ext: string): Promise<string> {
  const dir = path.join(outDir(), job.kind);
  await fs.mkdir(dir, { recursive: true });
  return path.join(dir, `${job.id}${ext}`);
}
function pauseFlag(): string {
  return path.join(home(), "bonsai", "llm-pause.flag");
}
function lastMainCmdline(): string {
  return path.join(home(), "bonsai", "last_main_cmdline.txt");
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

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

// ---------- Safety ----------

/**
 * 性的な依頼かどうか。成人向けの絵にするか、成人向けのネガティブタグを足すかを決めるための判定で、
 * 禁止の判定ではない。語句は dist/blocked-terms.json の sexualTerms にあり、コードには持たない。
 */
export function isSexualRequest(text: string): boolean {
  return isSexualText(text);
}

/**
 * dist/blocked-terms.json の禁止語句が含まれる依頼は作らない。ファイルが無い・読めないときも作らない。
 * 固定の禁止語は持たず、禁止の中身はすべてそのファイルで決まる。
 */
export function assertAllowedRequest(...texts: string[]): void {
  assertNoBlockedTerms(...texts);
}

/** Printable text only, so a prompt can never turn into extra command-line flags. */
export function sanitizeMediaText(raw: string, max = MAX_TEXT): string {
  return raw
    .replace(/[\r\n\t]+/g, " ")
    .replace(
      /[^\p{L}\p{N}\s,.:;!?()'"_+\-/&\u3001\u3002\u30FB\u30FC\u301C\uFF5E\uFF01\uFF1F\u300C\u300D]/gu,
      "",
    )
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

// ---------- Prompt translation (main LLM on the GPU, else the gemma worker on CPU) ----------

const MAIN_LLM = "http://127.0.0.1:11434";

/**
 * The chat model on the GPU writes a prompt in ~1-2 s where the CPU gemma worker took 5-10 s.
 * It is skipped (undefined) while the GPU is lent out (pause flag), while it is busy answering a
 * chat (one slot), or when it fails, so a media job never waits on the conversation.
 */
async function askMainLlm(
  system: string,
  user: string,
  maxTokens: number,
  timeoutMs = 30_000,
  schema?: Record<string, unknown>,
): Promise<string | undefined> {
  if (await exists(pauseFlag())) return undefined;
  try {
    const slots = await fetch(`${MAIN_LLM}/slots`, { signal: AbortSignal.timeout(2_000) });
    if (slots.ok) {
      const list = (await slots.json()) as Array<{ is_processing?: boolean }>;
      if (list.some((slot) => slot.is_processing)) return undefined;
    }
    const response = await fetch(`${MAIN_LLM}/v1/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        max_tokens: maxTokens,
        temperature: 0.3,
        chat_template_kwargs: { enable_thinking: false },
        // A schema makes llama.cpp generate only text that fits it (see comic-series.ts).
        ...(schema
          ? { response_format: { type: "json_schema", json_schema: { name: "script", schema } } }
          : {}),
      }),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) return undefined;
    const data = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    return data.choices?.[0]?.message?.content?.trim() || undefined;
  } catch {
    return undefined;
  }
}

async function askPromptWriter(system: string, user: string, maxTokens: number): Promise<string> {
  return (await askMainLlm(system, user, maxTokens)) ?? (await askGemma(system, user, maxTokens));
}

async function askGemma(system: string, user: string, maxTokens: number): Promise<string> {
  const worker = SPECIALIST_WORKERS.find((entry) => entry.key === "gemma");
  if (!worker) throw new Error("gemma worker is not configured");
  // Probe /health first: the PowerShell port lookup in startSpecialistWorker is slow when the
  // gateway runs as a scheduled task, and the worker is normally already up.
  const up = await fetch(`http://127.0.0.1:${worker.port}/health`, {
    signal: AbortSignal.timeout(3_000),
  })
    .then((res) => res.ok)
    .catch(() => false);
  if (!up) {
    const started = await startSpecialistWorker("gemma");
    if (!started.success) throw new Error(started.message);
    if (!(await waitForWorkerReady(worker.port)))
      throw new Error("gemma worker did not become ready");
  }
  const response = await fetch(`http://127.0.0.1:${worker.port}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      max_tokens: maxTokens,
      temperature: 0.3,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(5 * 60_000),
  });
  if (!response.ok) throw new Error(`gemma worker HTTP ${response.status}`);
  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return data.choices?.[0]?.message?.content?.trim() ?? "";
}

const ASCII_ONLY = (text: string) => text.replace(/[^\x20-\x7E]/g, " ");

async function toImageTags(request: string): Promise<string> {
  const tags = await askPromptWriter(
    "You write prompts for an anime Stable Diffusion XL model (Illustrious). Convert the user's request into 10-30 comma-separated English danbooru-style tags: subject count and gender, appearance, clothing, pose, expression, background, lighting, composition. Keep every detail the user asked for. Output only the tags.",
    request,
    200,
  );
  return sanitizeMediaText(ASCII_ONLY(tags), 700);
}

/** Photo-real SDXL checkpoints follow short descriptive phrases better than anime tags. */
async function toPhotoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a photorealistic Stable Diffusion XL model. Convert the user's request into one line of 15-35 comma-separated short English phrases: subject, age, ethnicity, hair, face and expression, body, clothing, pose, setting, lighting, camera shot and lens. People are adults; write an explicit adult age such as 'in her mid 20s'. Unless the user names another ethnicity or nationality, every person is Japanese: write 'Japanese woman' or 'Japanese man' and add 'East Asian facial features'. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 800);
}

/** Z-Image understands sentences better than booru tags. */
async function toNaturalImagePrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a text-to-image model. Rewrite the user's request as two to four vivid English sentences describing subject, appearance, clothing, pose, expression, setting, lighting, art style (anime illustration unless the user asks otherwise) and composition. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 900);
}

async function productionRequest(job: MediaJob): Promise<string> {
  if (job.productionPrompt) return job.productionPrompt;
  const notes: string[] = [];
  const lock = job.options.characterLock;
  if (lock) {
    const characters = (await Promise.all(lock.characterIds.map((id) => getCharacter(id)))).filter(
      (entry): entry is LibraryCharacter => Boolean(entry),
    );
    if (characters.length > 0) {
      const details = characters.map((entry) => {
        const outfit = lock.outfit
          ? entry.outfits?.find((candidate) => candidate.name === lock.outfit)?.tags
          : undefined;
        const look = characterLook(entry);
        return `${entry.name}: ${outfit ? lookWithOutfit(look, outfit) : look}`;
      });
      const kept = [
        lock.keepFace ? "顔" : "",
        lock.keepBody ? "体格" : "",
        lock.keepHair ? "髪" : "",
        lock.keepOutfit ? "衣装" : "",
      ].filter(Boolean);
      notes.push(
        `【キャラクター固定】${details.join(" / ")}。${kept.join("・")}を全カットで同一に保つ。`,
      );
    }
  }
  const scene = job.options.sceneState;
  if (scene) {
    const inherited = scene.inheritJobId ? await getMediaJob(scene.inheritJobId) : undefined;
    const sourceScene = inherited?.options.sceneState;
    const values = {
      place: scene.place ?? sourceScene?.place,
      camera: scene.camera ?? sourceScene?.camera,
      pose: scene.pose ?? sourceScene?.pose,
      wardrobe: scene.wardrobe ?? sourceScene?.wardrobe,
      continuity: scene.continuity ?? sourceScene?.continuity,
    };
    const sceneLines = [
      values.place ? `場所: ${values.place}` : "",
      values.camera ? `カメラ: ${values.camera}` : "",
      values.pose ? `姿勢: ${values.pose}` : "",
      values.wardrobe ? `衣服の状態: ${values.wardrobe}` : "",
      values.continuity ? `引き継ぐ状態: ${values.continuity}` : "",
    ].filter(Boolean);
    if (sceneLines.length > 0)
      notes.push(`【シーン状態】${sceneLines.join("。")}。前の場面との連続性を保つ。`);
  }
  job.productionPrompt = [job.request, ...notes].filter(Boolean).join("\n");
  return job.productionPrompt;
}

/** Prompt text for the chosen image model: booru tags for anime SDXL, phrases for photo SDXL, sentences for Z-Image. */
async function imagePromptFor(job: MediaJob): Promise<string> {
  const request = await productionRequest(job);
  if (isWanImageModel(job.options.imageModel)) return toNaturalImagePrompt(request);
  return sdxlSpecOf(sdxlModelOf(job)).photo ? toPhotoPrompt(request) : toImageTags(request);
}

async function toMotionPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for an image-to-video model. Describe in one or two short English sentences the motion and camera movement the user wants (what moves, how, camera). Output only the description.",
    request || "subtle natural motion, gentle camera movement",
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 500);
}

/** LTX-2 renders sound too, so its prompt also describes what is heard. */
async function toAudioVideoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a video model that also generates the soundtrack. In two or three short English sentences describe the motion, the camera, and what is heard (voices, ambient sound, music, effects). Keep any spoken line in the user's language inside quotes. Output only the description.",
    request || "subtle natural motion, gentle camera movement, soft ambient sound",
    200,
  );
  return sanitizeMediaText(text.replace(/[\r\n]+/g, " "), 700);
}

const H3_PREFIX = "integrated_multimodal_description:";

/** MiniMax H3 reads a two-part script: the shot (with optional dialogue) and the soundscape. */
async function toH3Prompt(request: string, seconds: number): Promise<string> {
  const text = await askPromptWriter(
    `You write prompts for MiniMax H3, which animates a first frame into a ${seconds}-second realistic clip with synchronized sound. Write exactly two lines in English:\n${H3_PREFIX} [Shot 1] A ${seconds}-second realistic single take. <who is in the frame, what they do, their expression, how the camera moves>\noverall_soundscape: <the sounds heard>\nPeople are adults. If someone should speak, put the words in Japanese inside the first line as (S1) <d>[Japanese] spoken words</d>. Keep every detail the user asked for. Output only the two lines.`,
    request || "natural subtle motion with a gentle camera push in and soft ambient sound",
    260,
  );
  return sanitizeH3Prompt(text);
}

/**
 * H3's script needs line breaks, brackets and <d> tags, which sanitizeMediaText removes. It only
 * travels inside the Wan2GP request JSON, never on a command line.
 */
export function sanitizeH3Prompt(raw: string): string {
  const text = raw
    .replace(/\r\n?/g, "\n")
    .replace(/[^\p{L}\p{N} \n,.:;!?()'"_+\-/&[\]<>、。・ー〜～！？「」]/gu, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim()
    .slice(0, 1_500);
  return text.startsWith(H3_PREFIX) ? text : `${H3_PREFIX} [Shot 1] ${text}`;
}

async function toMusicCaption(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write captions for a text-to-music model. Rewrite the user's request as one English sentence listing genre, mood, tempo feel, instruments and vocal type (for example: 'Upbeat J-pop with bright synths, punchy drums and a cheerful female vocal'). Output only the caption.",
    request,
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 400);
}

// ---------- GPU lease ----------

let pausedByUs = false;
/**
 * The chat model comes back this long after the last job, not at once: jobs often follow each other
 * and every restart costs 1-1.5 minutes of loading. A chat message brings it back sooner.
 */
const CHAT_RESTORE_DELAY_MS = 10 * 60_000;
let chatRestoreTimer: ReturnType<typeof setTimeout> | undefined;

function cancelChatRestoreTimer(): void {
  if (chatRestoreTimer) clearTimeout(chatRestoreTimer);
  chatRestoreTimer = undefined;
}

// A chat message that arrives while the model waits to be restored starts it right away.
// The chat model switch (Gemma / Strata) must not cut a running job off.
setChatEngineBusyCheck(() => working || queue.length > 0);

registerChatModelRestore(async () => {
  if (!chatRestoreTimer || working || queue.length > 0) return false;
  await restoreChatModel();
  return true;
});

function runPowerShell(command: string): Promise<string> {
  return new Promise((resolve) => {
    const ps = spawn("powershell.exe", ["-NoProfile", "-Command", command], {
      windowsHide: true,
      stdio: ["ignore", "pipe", "ignore"],
    });
    let out = "";
    ps.stdout?.on("data", (chunk) => (out += String(chunk)));
    ps.on("exit", () => resolve(out.trim()));
    ps.on("error", () => resolve(out.trim()));
  });
}

async function acquireGpu(job: MediaJob): Promise<void> {
  const speedPriority = job.options.gpuMode === "fast";
  cancelChatRestoreTimer();
  if (hasDedicatedMediaGpu() && !speedPriority) {
    if (pausedByUs) await restoreChatModel();
    return;
  }
  // The small Irodori model may be resident next to the chat model for partner voices.
  stopIrodoriWorker();
  // Voice training may already hold the flag; then it owns the restart too.
  const strataChat = readChatEngine() === "strata";
  if (!(await exists(pauseFlag()))) {
    await fs.writeFile(pauseFlag(), String(Date.now()));
    pausedByUs = true;
  } else if (strataChat) {
    // Strata keeps the flag up all the time; the chat model still has to come back after this job.
    pausedByUs = true;
  }
  // netstat + kill instead of PowerShell: PowerShell starts very slowly when the gateway runs
  // as a scheduled task, and this sits on the critical path of every job.
  const pid = await listeningPid(11434);
  if (pid) {
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
    // Strata's engine and vision helper hold the GPUs and may outlive its Python server.
    if (strataChat) await stopStrataProcesses();
    for (let i = 0; i < 20 && (await listeningPid(11434)); i++) {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
}

function listeningPid(port: number): Promise<number | undefined> {
  // Only a listener on this PC (loopback or all addresses). A Tailscale forward listens on the
  // tailnet address of the same port (11434) and must never be taken for the model server.
  return listeningLoopbackPid(port);
}

/**
 * Stops the CPU worker LLMs (~16 GB of RAM) for jobs that need nearly all memory; whoever uses
 * a worker next starts it again on demand.
 */
async function stopCpuWorkers(): Promise<void> {
  for (const worker of SPECIALIST_WORKERS) {
    const pid = await listeningPid(worker.port);
    if (!pid) continue;
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
  }
}

/**
 * True while the chat model is paused for a job. A flag left behind (the gateway was restarted
 * while a job ran, so nobody releases it) is removed once nothing is running or waiting and the
 * flag is old, so the chat AI is not paused for ever.
 */
async function clearStalePauseFlag(): Promise<boolean> {
  const flag = pauseFlag();
  if (!(await exists(flag))) return false;
  const age =
    Date.now() -
    (await fs
      .stat(flag)
      .then((s) => s.mtimeMs)
      .catch(() => Date.now()));
  const idle = !working && queue.length === 0 && !pausedByUs;
  if (idle && age > 15 * 60_000 && !(await voiceTrainingActive())) {
    await fs.rm(flag, { force: true });
    return false;
  }
  return true;
}

async function voiceTrainingActive(): Promise<boolean> {
  try {
    return (await listVoiceProjects()).some((project) => project.status === "training");
  } catch {
    return false;
  }
}

/** Called when the queue is empty: the chat model returns after a delay (see CHAT_RESTORE_DELAY_MS). */
async function releaseGpu(): Promise<void> {
  if (!pausedByUs) return;
  cancelChatRestoreTimer();
  chatRestoreTimer = setTimeout(() => {
    chatRestoreTimer = undefined;
    if (working || queue.length > 0) return;
    void restoreChatModel().catch(() => undefined);
  }, CHAT_RESTORE_DELAY_MS);
  chatRestoreTimer.unref?.();
}

/** Starts the chat model again now: removes the pause flag and does not wait for the watchdog. */
async function restoreChatModel(): Promise<void> {
  cancelChatRestoreTimer();
  if (!pausedByUs) return;
  pausedByUs = false;
  // Strata is the chosen chat model: start it again and keep the pause flag (the watchdog only knows Gemma).
  if (readChatEngine() === "strata") {
    await startSelectedChatEngine();
    return;
  }
  await fs.rm(pauseFlag(), { force: true });
  // Bring the chat model back right away instead of waiting for the watchdog (~1 minute).
  if (await exists(lastMainCmdline())) {
    await runPowerShell(
      `if (-not (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue)) { $cmd = (Get-Content '${lastMainCmdline()}' -Raw).Trim(); ([wmiclass]'Win32_Process').Create($cmd, '${path.join(home(), "bonsai", "bin")}', $null) | Out-Null }`,
    );
  }
}

/** How many pictures (with their caption) the training dataset folder holds. */
export async function trainingDatasetCount(imageDir: string): Promise<number> {
  const names = await fs.readdir(imageDir).catch(() => [] as string[]);
  const pictures = names.filter((name) => /\.(png|jpe?g|webp)$/i.test(name));
  return pictures.filter((name) => names.includes(name.replace(/\.\w+$/, ".txt"))).length;
}

/** The folder of the last saved training state of a run (kept by --save_state), or undefined. */
export async function latestTrainingState(
  dir: string,
  outName: string,
): Promise<string | undefined> {
  const names = (await fs.readdir(dir).catch(() => [] as string[]))
    .filter((name) => name.startsWith(`${outName}-`) && name.endsWith("-state"))
    .sort();
  return names.length > 0 ? path.join(dir, names[names.length - 1]!) : undefined;
}

/**
 * A training that was running when the gateway stopped is left marked "training" with nothing running: it is marked as
 * cut off, and pressing "train" again goes on from where it was (the pictures and the saved epoch are kept).
 */
async function markCutOffTrainings(): Promise<void> {
  if (working || queue.length > 0) return;
  for (const character of await listCharacters()) {
    if (character.loraStatus !== "training" && character.loraStatus !== "queued") continue;
    await saveCharacter({
      ...character,
      loraStatus: "failed",
      loraStep: undefined,
      loraError:
        "中断しました（ゲートウェイの再起動など）。もう一度「学習」を押すと、続きから始まります",
      updatedAt: Date.now(),
    });
  }
}

/**
 * The timer that brings the chat model back after a job lives in memory, so a gateway restart during or just after a job
 * left the pause flag behind and the chat model stopped for good. Some time after the gateway starts, a flag with no job
 * running is taken as such a leftover: the chat model is started again.
 */
async function restoreStuckChatModel(): Promise<void> {
  if (working || queue.length > 0) return;
  if (!(await exists(pauseFlag()))) return;
  pausedByUs = true;
  await restoreChatModel().catch(() => undefined);
}
if (!process.env.VITEST) {
  const stuck = setTimeout(
    () =>
      void restoreStuckChatModel()
        .then(markCutOffTrainings)
        .catch(() => undefined),
    90_000,
  );
  stuck.unref?.();
}

// ---------- Runners ----------

/** Sets the progress note and records how long the previous stage took. */
function markStage(job: MediaJob, stage: string): void {
  const now = Date.now();
  const timings = (job.timings ??= {});
  const last = timings.__at ?? job.startedAt ?? now;
  // Step counters ("画像を描いています 12/28") roll up into one entry per stage.
  const key = job.stage?.replace(/\s+\d+\/\d+$/, "");
  if (key) timings[key] = (timings[key] ?? 0) + (now - last);
  timings.__at = now;
  job.stage = stage;
}

/** The job being worked on and the tool process it runs; a delete stops them. */
let currentJob: MediaJob | undefined;
const activeChildren = new Set<ReturnType<typeof spawn>>();

/** Stops a tool and everything it started (Wan2GP spawns its own workers). */
function killProcessTree(pid: number | undefined): Promise<void> {
  if (!pid) return Promise.resolve();
  return new Promise((resolve) => {
    execFile("taskkill", ["/PID", String(pid), "/T", "/F"], { windowsHide: true }, () => resolve());
  });
}

function runTool(
  exe: string,
  args: string[],
  opts: {
    cwd?: string;
    env?: NodeJS.ProcessEnv;
    cudaDevice?: string;
    onLine?: (line: string) => void;
    timeoutMs?: number;
  },
): Promise<void> {
  if (currentJob?.cancelRequested) return Promise.reject(new Error("中止しました"));
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, {
      cwd: opts.cwd ?? path.dirname(exe),
      env: mediaProcessEnv(
        opts.env,
        opts.cudaDevice ?? generationCudaDevice(currentJob?.options.gpuMode === "fast"),
      ),
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    activeChildren.add(child);
    child.on("exit", () => {
      activeChildren.delete(child);
    });
    // The gateway runs as a below-normal scheduled task and children inherit that class, which on
    // Windows also lowers disk I/O priority: loading a 7 GB model then took minutes instead of seconds.
    try {
      if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
    } catch {
      // Priority is best-effort.
    }
    let tail = "";
    const onData = (chunk: Buffer) => {
      const text = chunk.toString("utf-8");
      tail = (tail + text).slice(-3_000);
      if (opts.onLine)
        for (const line of text.split(/\r?\n|\r/)) if (line.trim()) opts.onLine(line.trim());
    };
    child.stdout?.on("data", onData);
    child.stderr?.on("data", onData);
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error(`${path.basename(exe)} timed out`));
    }, opts.timeoutMs ?? JOB_TIMEOUT_MS);
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      if (code === 0) resolve();
      else
        reject(
          new Error(`${path.basename(exe)} exited with code ${code}: ${tail.trim().slice(-500)}`),
        );
    });
  });
}

// Local FP16/BF16 throughput measures the 4060 at about 1.24x the 3060, so the
// fastest-first device order completes equal image work closest to 55:45.
const DUAL_GPU_PRIMARY_SHARE = 0.55;

/** Runs a Wan2GP image manifest on one GPU, or splits independent images across both GPUs. */
async function runWan2gpManifest(
  job: MediaJob,
  workDir: string,
  settings: Record<string, unknown>[],
  opts: {
    timeoutMs: number;
    onComplete?: (done: number, total: number) => void;
  },
): Promise<string[]> {
  const devices = generationCudaDevices(job.options.gpuMode === "fast");
  const dual = devices.length >= 2 && settings.length >= 2;
  const split = dual
    ? Math.max(1, Math.ceil(settings.length * DUAL_GPU_PRIMARY_SHARE))
    : settings.length;
  const shards = dual ? [settings.slice(0, split), settings.slice(split)] : [settings];
  let done = 0;
  const runs = shards.map(async (shard, index) => {
    const shardDir = dual ? path.join(workDir, `gpu-${index + 1}`) : workDir;
    await fs.mkdir(shardDir, { recursive: true });
    const request = path.join(shardDir, "request.json");
    await fs.writeFile(request, JSON.stringify({ output_dir: shardDir, settings: shard }));
    let files: string[] = [];
    await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
      cwd: WAN2GP_DIR,
      cudaDevice: devices[index] ?? devices[0],
      timeoutMs: opts.timeoutMs,
      onLine: (line) => {
        const result = /^RESULT (.*)$/.exec(line)?.[1];
        if (result) files = JSON.parse(result) as string[];
        if (/^PROGRESS 100\b/.test(line)) {
          done += 1;
          opts.onComplete?.(Math.min(done, settings.length), settings.length);
        }
      },
    });
    return files;
  });
  let produced: string[][];
  try {
    produced = await Promise.all(runs);
  } catch (error) {
    await Promise.all([...activeChildren].map((child) => killProcessTree(child.pid)));
    await Promise.allSettled(runs);
    throw error;
  }
  return produced.flat();
}

/** sd.cpp prints "|====>   | 12/28 - 1.23s/it"; turn it into a short stage note. */
function stepNote(prefix: string, job: MediaJob) {
  return (line: string) => {
    const match = /(\d+)\/(\d+)\s*-\s*[\d.]+\s*(?:s\/it|it\/s)/.exec(line);
    if (!match) return;
    const stage = `${prefix} ${match[1]}/${match[2]}`;
    if (job.stage?.startsWith(prefix)) job.stage = stage;
    else markStage(job, stage);
  };
}

// ---------- Resident SDXL worker (PyTorch) ----------
// diffusers on the GPU samples ~2.5x faster than sd.cpp, and keeping the process (weights in RAM)
// between images also skips ~15 s of start-up. It holds the CUDA context, so it is stopped before
// the GPU goes back to the chat model.

const IMAGE_SERVER = "D:\\media-tools\\image\\sdxl_server.py";
/** After the queue drains, wait this long for another job before handing the GPU back. */
const IMAGE_GRACE_MS = 45_000;
/**
 * One resident worker per GPU: slot 0 is the first GPU (the one every job uses); slot 1 is the
 * second GPU, started only when a job spreads its pictures over both (a multi-page manga).
 */
type ImageWorkerSlot = {
  child?: ReturnType<typeof spawn>;
  ready?: Promise<void>;
  device?: string;
};
const imageSlots: ImageWorkerSlot[] = [{}, {}];
const imageRequests = new Map<
  string,
  { resolve: () => void; reject: (err: Error) => void; onStep: (step: string) => void }
>();

function startImageWorker(index = 0): Promise<void> {
  const slot = imageSlots[index] ?? imageSlots[0]!;
  const device = generationCudaDevices(currentJob?.options.gpuMode === "fast")[index];
  if (slot.child && slot.device !== device) stopImageWorker(slot);
  if (slot.child && slot.ready) return slot.ready;
  const child = spawn(WAN2GP_PYTHON, [IMAGE_SERVER], {
    cwd: path.dirname(IMAGE_SERVER),
    env: mediaProcessEnv({ PYTHONUTF8: "1", PYTHONIOENCODING: "utf-8" }, device),
    windowsHide: true,
    stdio: ["pipe", "pipe", "pipe"],
  });
  try {
    if (child.pid) os.setPriority(child.pid, os.constants.priority.PRIORITY_NORMAL);
  } catch {
    // Priority is best-effort.
  }
  slot.child = child;
  slot.device = device;
  let stderrTail = "";
  child.stderr?.on("data", (chunk) => {
    stderrTail = (stderrTail + String(chunk)).slice(-1_500);
  });
  slot.ready = new Promise<void>((resolve, reject) => {
    let buffer = "";
    child.stdout?.on("data", (chunk) => {
      buffer += String(chunk);
      let newline: number;
      while ((newline = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newline).trim();
        buffer = buffer.slice(newline + 1);
        if (line === "READY") {
          resolve();
          continue;
        }
        const match = /^(\S+) (STEP|RESULT|ERROR) ?(.*)$/.exec(line);
        const pending = match ? imageRequests.get(match[1] ?? "") : undefined;
        if (!match || !pending) continue;
        if (match[2] === "STEP") pending.onStep(match[3] ?? "");
        else {
          imageRequests.delete(match[1] ?? "");
          if (match[2] === "RESULT") pending.resolve();
          else pending.reject(new Error(match[3] || "image worker error"));
        }
      }
    });
    child.on("exit", (code) => {
      const error = new Error(`image worker exited (${code}): ${stderrTail.trim().slice(-300)}`);
      reject(error);
      for (const pending of imageRequests.values()) pending.reject(error);
      imageRequests.clear();
      if (slot.child === child) {
        slot.child = undefined;
        slot.ready = undefined;
      }
    });
    child.on("error", reject);
  });
  return slot.ready;
}

/** Stops one worker, or (no argument) every worker. */
function stopImageWorker(only?: ImageWorkerSlot): void {
  for (const slot of only ? [only] : imageSlots) {
    const child = slot.child;
    slot.child = undefined;
    slot.ready = undefined;
    slot.device = undefined;
    if (!child) continue;
    child.stdin?.end();
    setTimeout(() => child.kill(), 10_000).unref();
  }
}

/** The processes of the resident workers (to stop them when a job is cancelled). */
function imageWorkerChildren(): Array<ReturnType<typeof spawn>> {
  return imageSlots.flatMap((slot) => (slot.child ? [slot.child] : []));
}

type WorkerParams = {
  prompt: string;
  negative: string;
  width: number;
  height: number;
  output: string;
  /** Different people side by side: each region of the picture reads its own prompt. */
  regions?: Array<{ prompt: string; x0: number; x1: number }>;
  /** Character LoRAs, applied on top of the sampling LoRA (DMD2). */
  loras?: Array<{ path: string; scale: number }>;
  seed?: number;
};

async function renderWithImageWorker(job: MediaJob, params: WorkerParams, gpu = 0): Promise<void> {
  const slot = imageSlots[gpu] ?? imageSlots[0]!;
  if (gpu === 0) markStage(job, "画像の準備をしています");
  await startImageWorker(gpu);
  const id = randomBytes(4).toString("hex");
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      imageRequests.delete(id);
      reject(new Error("image worker timed out"));
    }, 10 * 60_000);
    imageRequests.set(id, {
      resolve: () => {
        clearTimeout(timer);
        resolve();
      },
      reject: (err) => {
        clearTimeout(timer);
        reject(err);
      },
      onStep: (step) => {
        if (gpu !== 0 && job.parallelPictures) return;
        const stage = `画像を描いています ${step}`;
        if (job.stage?.startsWith("画像を描いています")) job.stage = stage;
        else markStage(job, stage);
      },
    });
    const model = sdxlModelOf(job);
    const spec = sdxlSpecOf(model);
    const sampling = sdxlSampling(model, job.options.fast === true);
    slot.child?.stdin?.write(
      `${JSON.stringify({
        id,
        model: spec.file,
        vpred: spec.vpred,
        sampler: sampling.sampler,
        steps: sampling.steps,
        cfg: sampling.cfg,
        lora: sampling.lora ?? null,
        lora_scale: 1.0,
        seed: -1,
        ...params,
      })}\n`,
    );
  });
}

/** Keeps the GPU (and the warm image worker) for a short while in case another job follows. */
async function waitForMoreJobs(ms: number): Promise<boolean> {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    if (queue.length > 0) return true;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return queue.length > 0;
}

/** Z-Image Turbo (6B, 8 steps) through the Wan2GP runner; it reads natural-language prompts. */
/** The Wan2GP settings of each still-image model (steps and guidance as the model was tuned). */
function wanImageSettings(model: WanImageModel): {
  label: string;
  settings: Record<string, unknown>;
} {
  switch (model) {
    case "klein":
      return {
        label: "FLUX.2 Klein 9B",
        settings: { model_type: "flux2_klein_9b", num_inference_steps: 4, guidance_scale: 1 },
      };
    case "qwenimg":
      return { label: "Qwen Image 2.1", settings: qwenTurboSettings() };
    case "qwenimgturbo":
      return {
        label: "Qwen Image 2.1 Turbo",
        settings: {
          model_type: "qwen_image_21_turbo",
          num_inference_steps: 8,
          guidance_scale: 1,
          sample_solver: "default",
          activated_loras: [],
          loras_multipliers: "",
          negative_prompt: "",
        },
      };
    default:
      // Nunchaku SVDQ INT4 (kernels in the Wan2GP venv): 49 s vs 65 s for the INT8 build, same look.
      return {
        label: "Z-Image",
        settings: {
          model_type: "z_image_nunchaku_r256_int4",
          num_inference_steps: 8,
          guidance_scale: 0,
        },
      };
  }
}

async function renderWithWan2gpImage(
  job: MediaJob,
  model: WanImageModel,
  prompt: string,
  file: string,
  aspect: NonNullable<MediaOptions["aspect"]> = job.options.aspect ?? "portrait",
): Promise<void> {
  const size = ZIMAGE_SIZES[aspect];
  if (model === "qwenimgturbo") {
    const [width, height] = size.split("x").map(Number);
    const workDir = path.join(outDir(), `${job.id}-qwen-turbo`);
    const request = path.join(workDir, "request.json");
    await fs.mkdir(workDir, { recursive: true });
    await fs.writeFile(
      request,
      JSON.stringify({
        output: file,
        settings: { prompt, width, height, seed: Math.floor(Math.random() * 2 ** 31) },
      }),
    );
    markStage(job, "画像を描いています（Qwen Image 2.1 Turbo・公式8ステップ）");
    await runTool(WAN2GP_PYTHON, [QWEN_TURBO_RUNNER, request], {
      cwd: path.dirname(QWEN_TURBO_RUNNER),
      timeoutMs: 2 * 60 * 60_000,
    });
    await fs.rm(workDir, { recursive: true, force: true });
    return;
  }
  const spec = wanImageSettings(model);
  const workDir = path.join(outDir(), `${job.id}-wanimg`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: workDir,
      settings: { ...spec.settings, prompt, resolution: size, seed: -1 },
    }),
  );
  markStage(job, `画像を描いています（${spec.label}。初回はモデルのダウンロードがあります）`);
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 2 * 60 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error(`${spec.label} が画像を出力しませんでした`);
  await fs.rename(image, file);
  await fs.rm(workDir, { recursive: true, force: true });
}

const ZIMAGE_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "1024x1024",
  portrait: "832x1216",
  landscape: "1216x832",
};

/**
 * The trained LoRAs of the characters picked from the library (characterLock), up to four. With
 * several characters each LoRA is weaker so the faces do not blend into one. Only the SDXL worker
 * can use them; the other image models describe the characters in words only.
 */
async function lockedCharacterLoras(job: MediaJob): Promise<NonNullable<WorkerParams["loras"]>> {
  const ids = job.options.characterLock?.characterIds ?? [];
  const paths: string[] = [];
  for (const id of ids) {
    const entry = await getCharacter(id);
    if (entry?.lora && entry.loraStatus === "ready" && (await exists(entry.lora)))
      paths.push(entry.lora);
  }
  const scale = paths.length > 1 ? 0.6 : 0.8;
  return paths.map((file) => ({ path: file, scale }));
}

async function renderImage(
  job: MediaJob,
  tags: string,
  sexual: boolean,
  file: string,
  /** A manga panel picks its own shape and keeps text and frames out of the picture. */
  panel: {
    aspect?: NonNullable<MediaOptions["aspect"]>;
    negative?: string;
    regions?: WorkerParams["regions"];
    loras?: WorkerParams["loras"];
    seed?: number;
    /** Which GPU's resident worker draws it (0 = first, 1 = second). */
    gpu?: number;
  } = {},
): Promise<void> {
  const aspect = panel.aspect ?? job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].image;
  if (isWanImageModel(job.options.imageModel)) {
    const people = panel.regions?.map((r) => r.prompt).join(". ") ?? "";
    await renderWithWan2gpImage(
      job,
      job.options.imageModel,
      `${adultSentence(sexual)}${tags}${people ? `. ${people}` : ""}`,
      file,
      aspect,
    );
    return;
  }
  if (job.options.imageModel === "anima") {
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    const extraNegative = [
      negativeTags("imageNegative"),
      sexual ? negativeTags("adultOnly") : "",
      panel.negative ?? "",
    ]
      .filter(Boolean)
      .join(", ");
    await renderAnima(
      {
        positive: `${adultLead(sexual)}${tags}`,
        negative: [ANIMA_NEGATIVE, extraNegative].filter(Boolean).join(", "),
        width,
        height,
        seed: panel.seed ?? Math.floor(Math.random() * 2 ** 31),
        turbo: job.options.fast === true,
      },
      file,
      {
        onStage: (stage) => markStage(job, stage),
        isCancelled: () => job.cancelRequested === true,
        cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
      },
    );
    return;
  }
  const spec = sdxlSpecOf(sdxlModelOf(job));
  const negative = [
    spec.negative,
    negativeTags("imageNegative"),
    sexual ? negativeTags("adultOnly") : "",
    panel.negative ?? "",
  ]
    .filter(Boolean)
    .join(", ");
  const positive = `${spec.quality}, ${adultLead(sexual)}${tags}`;
  // The characters picked from the library bring their trained look (SDXL LoRAs) unless the panel has its own.
  const loras = panel.loras?.length ? panel.loras : await lockedCharacterLoras(job);
  if (await exists(IMAGE_SERVER)) {
    try {
      await renderWithImageWorker(
        job,
        {
          prompt: positive,
          negative,
          width,
          height,
          output: file,
          ...(panel.regions?.length ? { regions: panel.regions } : {}),
          ...(loras.length ? { loras } : {}),
          ...(panel.seed !== undefined ? { seed: panel.seed } : {}),
        },
        panel.gpu ?? 0,
      );
      return;
    } catch (err) {
      // Fall through to sd.cpp so a broken Python environment never blocks image jobs.
      if (job.cancelRequested) throw err;
      stopImageWorker(imageSlots[panel.gpu ?? 0]);
      markStage(job, `画像を描いています（予備の方式に切替: ${String(err).slice(0, 80)}）`);
    }
  }
  await runTool(
    sdCli(),
    [
      "-m",
      IMAGE_MODEL,
      "-p",
      positive,
      "-n",
      negative,
      "-W",
      String(width),
      "-H",
      String(height),
      "--steps",
      "28",
      "--cfg-scale",
      "6",
      "--sampling-method",
      "euler_a",
      "--diffusion-fa",
      "--vae-tiling",
      "-s",
      "-1",
      "-o",
      file,
    ],
    { onLine: stepNote("画像を描いています", job) },
  );
}

/** A finished picture made sharper and larger with SeedVR2 (ComfyUI is started for it and stopped after). */
async function runUpscaleJob(job: MediaJob): Promise<void> {
  const src = await getMediaJob(job.options.upscaleSourceJobId ?? "");
  if (
    !src ||
    src.status !== "done" ||
    !src.file ||
    !src.mime?.startsWith("image/") ||
    !(await exists(src.file))
  ) {
    throw new Error("高画質化する絵が見つかりません（完成した絵を選んでください）");
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  const file = await resultPath(job, ".png");
  await upscaleWithSeedVr(
    src.file,
    file,
    {
      model: job.options.upscaleModel ?? "3b",
      resolution: Math.min(3072, Math.max(768, job.options.upscaleResolution ?? 1536)),
    },
    {
      onStage: (stage) => markStage(job, stage),
      isCancelled: () => job.cancelRequested === true,
      cudaDevice: generationCudaDevice(job.options.gpuMode === "fast"),
    },
  );
  job.file = file;
  job.mime = "image/png";
}

const MEDIA_REPAIR_SCRIPT = "D:\\media-tools\\motion-comic\\mc_image.py";

/** Repaints one selected area and keeps every pixel outside it unchanged. */
async function runRepairJob(job: MediaJob): Promise<void> {
  const repair = job.options.repair;
  if (!repair) throw new Error("直す場所が指定されていません");
  const source = await getMediaJob(repair.sourceJobId);
  const sourceFile = source?.files?.[repair.part ?? 0] ?? source?.file;
  if (
    !source ||
    source.status !== "done" ||
    !sourceFile ||
    !(source.mime?.startsWith("image/") || source.files?.length) ||
    !(await exists(sourceFile))
  ) {
    throw new Error("部分修正する完成画像が見つかりません");
  }
  markStage(job, "元の絵を読み取っています");
  const detected = await runTagger(sourceFile).catch(() => undefined);
  const sourceTags = detected?.general
    .filter(([, score]) => score >= 0.35)
    .slice(0, 80)
    .map(([tag]) => tag)
    .join(", ");
  const requested = await imagePromptFor(job);
  const positive = [sourceTags, requested].filter(Boolean).join(", ");
  assertAllowedRequest(job.request, repair.instruction, positive);
  const probe = await probeMedia(sourceFile);
  const model = isSdxlModel(source.options.imageModel)
    ? source.options.imageModel
    : isSdxlModel(job.options.imageModel)
      ? job.options.imageModel
      : "wai";
  const spec = sdxlSpecOf(model);
  const work = path.join(outDir(), `${job.id}-repair`);
  await fs.mkdir(work, { recursive: true });
  const output = path.join(work, "repaired.png");
  const config = path.join(work, "repair.json");
  const { x, y, width, height } = repair.region;
  await fs.writeFile(
    config,
    JSON.stringify({
      model: spec.file,
      negative: [spec.negative, negativeTags("imageNegative")].filter(Boolean).join(", "),
      width: probe.width,
      height: probe.height,
      jobs: [
        {
          mode: "inpaint",
          src: sourceFile,
          mask: [x, y, x + width, y + height],
          prompt: `${spec.quality}, ${positive}`,
          strength: repair.strength,
          seed: Math.floor(Math.random() * 2 ** 31),
          out: output,
        },
      ],
    }),
  );
  markStage(job, "選んだ場所だけ直しています");
  await acquireGpu(job);
  stopImageWorker();
  await runTool(WAN2GP_PYTHON, [MEDIA_REPAIR_SCRIPT, config], {
    timeoutMs: 30 * 60_000,
  });
  if (!(await exists(output))) throw new Error("部分修正した画像ができませんでした");
  const file = await resultPath(job, ".png");
  await fs.copyFile(output, file);
  await fs.rm(work, { recursive: true, force: true }).catch(() => undefined);
  job.file = file;
  job.mime = "image/png";
}

async function runImageJob(job: MediaJob): Promise<void> {
  if (job.options.repair) return runRepairJob(job);
  if (job.options.upscaleSourceJobId) return runUpscaleJob(job);
  markStage(job, "指示を英語のタグに変換しています");
  const tags = await imagePromptFor(job);
  assertAllowedRequest(job.request, tags);
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = await resultPath(job, ".png");
  await renderImage(job, tags, isSexualRequest(`${job.request} ${tags}`), file);
  job.file = file;
  job.mime = "image/png";
}

const TIMELINE_SFX: Record<"thrust" | "spank" | "wet", string> = {
  thrust: "D:\\media-tools\\motion-comic\\sfx\\synth\\thrust_slow.wav",
  spank: "D:\\media-tools\\motion-comic\\sfx\\synth\\spank.wav",
  wet: "D:\\media-tools\\motion-comic\\sfx\\synth\\wet_slow.wav",
};

const VOICE_FFMPEG = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffmpeg.exe";
const VOICE_MAX_LINES = 40;

/** A script for Irodori: printable text with its emojis and line breaks kept. */
export function sanitizeVoiceScript(raw: string): string {
  return raw
    .replace(/\r\n?/g, "\n")
    .replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, "")
    .split("\n")
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, VOICE_MAX_LINES)
    .join("\n")
    .slice(0, 4_000);
}

/**
 * A voice line or a short script with Irodori-TTS: each line (Irodori speaks up to ~30 s at a
 * time) in the chosen voice, joined with short pauses. The large model takes the GPU like any
 * media job; the small one is quick but plainer.
 */
const DRAMA_SYSTEM = `あなたは成人向けの音声ドラマ（ASMRのような、耳元で聞く物語）の脚本家。お題から、1人の女性が聞き手（あなた）に語りかけるセリフを、順番に書く。
出力はJSONオブジェクト1つだけ（説明や\`\`\`は書かない）: {"lines":[{"text":"セリフ","pause":1.2}]}
- text は1つ60字以内の日本語。行ごとの演技を角括弧で付ける: [囁き] [吐息:弱] [喘ぎ:強] [荒い息] [照れ] [甘え] [懇願] [息をのむ] [水音] [間] [ゆっくり] [悲鳴:強] など。強さは 弱・中・強。絵文字は書かない（システムが変換する）。
- pause はその行のあとの無音の秒数（0.3〜6）。ゆっくりな場面は長く、高まる場面は短く。
- 全体は、静かな導入 → 少しずつ距離が近づく → 高まり → 絶頂 → 余韻 の流れで、与えられた範囲の分だけ書く。同じセリフを繰り返さない。
- 聞き手を「あなた」と呼び、耳元で語るような口調にする。名前・口調はお題に従う。`;

function parseDramaLines(reply: string): Array<{ text: string; pause: number }> {
  const data = firstJsonObject(reply);
  const rows = Array.isArray(data.lines) ? (data.lines as Array<Record<string, unknown>>) : [];
  const out = rows
    .map((row) => ({
      text: String(row.text ?? "")
        .replace(/[\r\n]+/g, " ")
        .trim()
        .slice(0, 120),
      pause: Math.min(6, Math.max(0.3, Number(row.pause) || 1)),
    }))
    .filter((row) => row.text.length > 0);
  if (out.length === 0) throw new Error("セリフがありません");
  return out;
}

/** A long script in parts of about 24 lines, each part told what came before so the story goes on. */
async function writeDramaScript(
  job: MediaJob,
  drama: VoiceDrama,
): Promise<Array<{ text: string; pause: number }>> {
  const request = await productionRequest(job);
  const target = Math.min(210, Math.max(8, Math.round(drama.minutes * 7)));
  const out: Array<{ text: string; pause: number }> = [];
  while (out.length < target) {
    if (job.cancelRequested) throw new Error("中止しました");
    const want = Math.min(24, target - out.length);
    const from = Math.round((out.length / target) * 100);
    const to = Math.round(((out.length + want) / target) * 100);
    const recap = out
      .slice(-4)
      .map((l) => l.text)
      .join(" / ");
    const user =
      `お題: ${request}\n全体の ${from}% 〜 ${to}% の部分を、${want}行で書く。` +
      (out.length === 0
        ? "ここが物語の始まり。"
        : `直前までの最後のセリフ: ${recap}\nその続きから、話を進める。`) +
      (to >= 100 ? "ここで物語を終える（余韻と、おやすみの言葉まで）。" : "");
    const part = await writeScript(
      job,
      `台本を書いています（${out.length}/${target}行）`,
      DRAMA_SYSTEM,
      user,
      3_000,
      parseDramaLines,
    );
    assertAllowedRequest(request, ...part.map((l) => l.text));
    out.push(...part.slice(0, want));
  }
  return out;
}

async function runVoiceJob(job: MediaJob): Promise<void> {
  const drama = job.options.voiceDrama;
  const written = drama ? await writeDramaScript(job, drama) : undefined;
  const timeline = job.options.voiceTimeline;
  const lineCues = timeline?.filter(
    (cue): cue is MediaVoiceTimelineCue & { kind: "line"; text: string } =>
      cue.kind === "line" && Boolean(cue.text),
  );
  const lines = lineCues?.length
    ? lineCues.map((cue) => cue.text)
    : written
      ? written.map((l) => l.text)
      : job.request.split("\n").filter((line) => line.trim());
  const pauses = written ? written.map((l) => l.pause) : [];
  if (lines.length === 0) throw new Error("読み上げる文がありません");
  const model = job.options.irodoriModel ?? (drama ? "fast" : "large");
  const referenceWav = job.options.voiceId ? await referenceClip(job.options.voiceId) : undefined;
  const caption = referenceWav ? undefined : job.options.voiceCaption;
  if (!referenceWav && !caption)
    throw new Error("声（声作りスタジオの声か、声の説明）を選んでください");
  if (model === "large") {
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
  }
  const dir = path.join(outDir(), `${job.id}-voice`);
  await fs.mkdir(dir, { recursive: true });
  try {
    const parts: string[] = [];
    let seed: number | undefined;
    // With two GPUs (the large model, several lines) two workers take lines side by side. The first line is
    // made alone: a described voice (no recording) keeps its seed from it, so every line is the same person.
    // If the second GPU cannot hold the model, its line goes back to the first worker and the second one stops.
    const voiceDevices = generationCudaDevices(job.options.gpuMode === "fast");
    const voiceDual = model === "large" && lines.length > 1 && voiceDevices.length >= 2;
    const makeLine = async (index: number, lane: number) => {
      markStage(
        job,
        `声を作っています（${index + 1}/${lines.length}行${index === 0 ? "・最初はモデルの読み込みがあります" : ""}）`,
      );
      const result = await synthesizeIrodori({
        text: lines[index]!,
        model,
        caption,
        referenceWav,
        // The same seed keeps a described voice the same person from line to line.
        ...(seed !== undefined && caption ? { seed } : {}),
        ...(model === "large" ? { device: "cuda" as const } : {}),
        ...(voiceDual ? { lane, cudaDevice: voiceDevices[lane] } : {}),
      });
      seed ??= result.seed;
      const file = path.join(dir, `line-${String(index + 1).padStart(2, "0")}.wav`);
      await fs.writeFile(file, result.audio);
      parts[index] = file;
    };
    if (!voiceDual) {
      for (const index of lines.keys()) {
        if (job.cancelRequested) throw new Error("中止しました");
        await makeLine(index, 0);
      }
    } else {
      await makeLine(0, 0);
      let nextLine = 1;
      const runLane = async (lane: number): Promise<void> => {
        for (;;) {
          if (job.cancelRequested) throw new Error("中止しました");
          const index = nextLine++;
          if (index >= lines.length) return;
          try {
            await makeLine(index, lane);
          } catch (error) {
            if (lane === 0) throw error;
            await makeLine(index, 0);
            return;
          }
        }
      };
      await Promise.all([runLane(0), runLane(1)]);
    }
    markStage(job, "つないでいます");
    const output = await resultPath(job, ".wav");
    if (timeline?.length) {
      const existingSfx = (
        await Promise.all(
          timeline
            .filter(
              (
                cue,
              ): cue is MediaVoiceTimelineCue & {
                kind: "sfx";
                sfx: "thrust" | "spank" | "wet";
              } => cue.kind === "sfx" && Boolean(cue.sfx),
            )
            .map(async (cue) => ({
              cue,
              file: TIMELINE_SFX[cue.sfx],
              exists: await exists(TIMELINE_SFX[cue.sfx]),
            })),
        )
      ).filter((entry) => entry.exists);
      const inputs = [...parts, ...existingSfx.map((entry) => entry.file)].flatMap((file) => [
        "-i",
        file,
      ]);
      const filters: string[] = [];
      lineCues?.forEach((cue, index) => {
        const delay = Math.round(cue.atSec * 1000);
        filters.push(
          `[${index}:a]aresample=48000,aformat=channel_layouts=mono,volume=${cue.volume},adelay=${delay}|${delay}[line${index}]`,
        );
      });
      existingSfx.forEach(({ cue }, index) => {
        const input = parts.length + index;
        const delay = Math.round(cue.atSec * 1000);
        filters.push(
          `[${input}:a]aresample=48000,aformat=channel_layouts=mono,volume=${cue.volume},adelay=${delay}|${delay}[sfx${index}]`,
        );
      });
      const labels = [
        ...(lineCues ?? []).map((_, index) => `[line${index}]`),
        ...existingSfx.map((_, index) => `[sfx${index}]`),
      ];
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          ...inputs,
          "-filter_complex",
          `${filters.join(";")};${labels.join("")}amix=inputs=${labels.length}:duration=longest:normalize=0[out]`,
          "-map",
          "[out]",
          output,
        ],
        { timeoutMs: 30 * 60_000 },
      );
    } else if (parts.length === 1) {
      await fs.copyFile(parts[0] as string, output);
    } else {
      // Lines joined with 0.45 s of silence between them.
      const inputs = parts.flatMap((p) => ["-i", p]);
      const chain = parts
        .map(
          (_, i) =>
            `[${i}:a]aresample=48000,aformat=channel_layouts=mono,apad=pad_dur=${pauses[i] ?? 0.45}[a${i}]`,
        )
        .join(";");
      const concat = parts.map((_, i) => `[a${i}]`).join("");
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          ...inputs,
          "-filter_complex",
          `${chain};${concat}concat=n=${parts.length}:v=0:a=1[out]`,
          "-map",
          "[out]",
          output,
        ],
        { timeoutMs: 5 * 60_000 },
      );
    }
    if (drama && drama.ambience !== "none") {
      // The background sound runs under the whole story, quietly.
      markStage(job, "背景の音を重ねています");
      const mixed = path.join(dir, "mixed.wav");
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          "-i",
          output,
          "-stream_loop",
          "-1",
          "-i",
          path.join("D:\\media-tools\\motion-comic\\sfx\\ambience", `${drama.ambience}.wav`),
          "-filter_complex",
          "[0:a]aresample=48000,aformat=channel_layouts=mono[v];[1:a]aresample=48000,aformat=channel_layouts=mono,volume=0.2[amb];[v][amb]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[out]",
          "-map",
          "[out]",
          mixed,
        ],
        { timeoutMs: 30 * 60_000 },
      );
      await fs.copyFile(mixed, output);
    }
    job.file = output;
    job.mime = "audio/wav";
  } finally {
    // The large model holds ~7 GB of VRAM; give it back before the chat model returns.
    if (model === "large") stopIrodoriWorker();
    await fs.rm(dir, { recursive: true, force: true }).catch(() => undefined);
  }
}

/** The target file of a face swap job: "in:<file>" or a finished library job ("job:<id>"). */
function resolveSwapTarget(options: MediaOptions): string | undefined {
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
async function runFaceSwapJob(job: MediaJob): Promise<void> {
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

/** Motion comic runner (key images, Wan 2.2 cuts, film) and its sound effects; outside the repo. */
const MOTION_SCRIPT = "D:\\media-tools\\motion-comic\\motion_comic.py";
const MOTION_FELLATIO_SFX = "D:\\media-tools\\motion-comic\\sfx\\tsumugi\\008_フェラ.wav";
/** Spoken calmly in the described voice; the film's lines then copy this voice. */
const MOTION_VOICE_SAMPLE =
  "…こんばんは。今日は、ゆっくりしていってね。あなたのこと、ずっと待ってたんだから。";
/** A cut of 3 s took ~11 min at 832x480; the fast profile (640x368, Real-ESRGAN) takes ~6 min, see clips.py. */
const MOTION_TIMEOUT_MS = 4 * 60 * 60_000;

/**
 * An adult scene as AI video cuts: the writer fills in the content, the voices are made with
 * Irodori (large), then motion_comic.py draws the key images, makes every cut and joins the film.
 */
async function runMotionJob(job: MediaJob): Promise<void> {
  if (job.options.motionRevoiceJobId) return revoiceMotionJob(job);
  if (job.options.motionContinueJobId) return continueMotionJob(job);
  if (job.options.motionJoinJobIds?.length) return runMotionJoin(job);
  const inherited = await applyMotionInherit(job);
  const talk = job.options.motionTalk;
  const minutes = job.options.motionMinutes ?? 1;
  // A library character keeps the same face as in the comics (its look and trained LoRA).
  const lockedCharacterId = job.options.characterLock?.characterIds[0];
  const heroineId = job.options.motionCharacterId ?? lockedCharacterId;
  const heroine = heroineId ? await getCharacter(heroineId) : undefined;
  // 多人数シーン: 2〜4人目のキャラクター
  const heroine2 = job.options.motionCharacterId2
    ? await getCharacter(job.options.motionCharacterId2)
    : undefined;
  const heroine3 = job.options.motionCharacterId3
    ? await getCharacter(job.options.motionCharacterId3)
    : undefined;
  const heroine4 = job.options.motionCharacterId4
    ? await getCharacter(job.options.motionCharacterId4)
    : undefined;
  // Her whole setting sheet, adult part included: first person, catchphrase, weak spots, kinks,
  // how she moans — the writer has to use them in the lines.
  const castNote = heroine
    ? `\n女性はキャラ図鑑の「${heroine.name}」で固定（name はこの名前）。設定: ${heroine.profile}${
        heroine.spec
          ? `\n設定シート（セリフと流れに必ず反映する。一人称・口癖・口調を守り、弱いところ・性癖・好きなこと・喘ぎ方を使い、NGは避ける）: ${specSummary(heroine.spec)}`
          : ""
      }${heroine2 ? `\n2人目: 「${heroine2.name}」設定: ${heroine2.profile}` : ""}${heroine3 ? `\n3人目: 「${heroine3.name}」設定: ${heroine3.profile}` : ""}${heroine4 ? `\n4人目: 「${heroine4.name}」設定: ${heroine4.profile}` : ""}`
    : "";
  // 前段シーンのカスタムステップをプロンプトに注入
  const approachNote =
    job.options.motionApproachSteps && job.options.motionApproachSteps.length > 0
      ? `\n\n【前段シーン構成】本番前に以下のステップを順番に描写すること:\n${job.options.motionApproachSteps
          .map((s, i) => `${i + 1}. ${s.label}${s.tags ? ` (${s.tags})` : ""}`)
          .join("\n")}`
      : "";
  const production = await productionRequest(job);
  const script = await writeScript(
    job,
    "台本を書いています",
    motionSystemPrompt(minutes),
    `依頼: ${production}${castNote}${inherited.note}${talkNote(talk)}${voiceStyleNote(job.options.motionVoiceStyles ?? [])}${approachNote}${
      job.options.motionTemplate
        ? `\n体位・場面は「${MOTION_TEMPLATES[job.options.motionTemplate].label}」で固定。セリフもこの体位に合わせる。`
        : ""
    }${motionOptionNotes(job.options.motionOptions) ? `\n${motionOptionNotes(job.options.motionOptions)}` : ""}`,
    2_500,
    (reply) =>
      parseMotionScript(reply, talk === "tuna" ? 0 : talk === "few" || talk === "silent" ? 1 : 2),
  );
  if (talk) {
    // The writer does not always keep to the number it was given.
    const limit = MOTION_TALK[talk].lines;
    script.lines.slow = script.lines.slow.slice(0, limit.slow);
    script.lines.fast = script.lines.fast.slice(0, limit.fast);
    script.lines.climax = script.lines.climax.slice(0, limit.climax);
    script.lines.after = script.lines.after.slice(0, limit.climax);
  }
  if (inherited.script) {
    // The same person as before, in the same place: name, age, voice, look.
    script.name = inherited.script.name;
    script.age = inherited.script.age;
    script.voice = inherited.script.voice;
    script.look = inherited.script.look;
    script.place = inherited.script.place;
  }
  if (job.options.motionFellatio !== undefined) script.fellatio = job.options.motionFellatio;
  if (heroine) {
    script.name = heroine.name;
    // An outfit from her library entry replaces the clothes of her usual look.
    const outfit = heroine.outfits?.find((o) => o.name === job.options.motionOutfit);
    const heroineLook = characterLook(heroine);
    const look = outfit ? lookWithOutfit(heroineLook, outfit.tags) : heroineLook;
    script.look = [heroine.trigger, look].filter(Boolean).join(", ");
  }
  // 多人数シーン: 2〜4人目の look を追加 (Python スクリプトに渡すため拡張)
  const extraLoras: Array<{ path: string; scale: number }> = [];
  const extraLooks: string[] = [];
  for (const [extra, outfitKey] of [
    [heroine2, job.options.motionOutfit2],
    [heroine3, job.options.motionOutfit3],
    [heroine4, job.options.motionOutfit4],
    // SAFETY: each element is [CharacterEntry|undefined, string|undefined]; the tuple type is exact.
  ] as [typeof heroine, string | undefined][]) {
    if (!extra) continue;
    const outfit = extra.outfits?.find((o) => o.name === outfitKey);
    const extraLook = characterLook(extra);
    const look = outfit ? lookWithOutfit(extraLook, outfit.tags) : extraLook;
    extraLooks.push([extra.trigger, look].filter(Boolean).join(", "));
    if (extra.lora && extra.loraStatus === "ready")
      extraLoras.push({ path: extra.lora, scale: 0.8 });
  }
  const allLines = Object.values(script.lines).flat();
  assertAllowedRequest(job.request, script.look, script.position, ...allLines);
  assertAgeAllowed(script.age);
  job.manga = { title: motionSummary(script, minutes), lines: allLines };

  const dir = path.join(outDir(), `${job.id}-motion`);
  await fs.mkdir(dir, { recursive: true });
  if (inherited.dir) {
    // The same voice as before: its reference recording is carried over.
    const reference = path.join(inherited.dir, "voice-reference.wav");
    const mine = path.join(dir, "voice-reference.wav");
    if ((await exists(reference)) && !(await exists(mine))) await fs.copyFile(reference, mine);
  }
  const state: MotionState = {
    script,
    extraLoras,
    extraLooks,
    minutes,
    options: job.options,
    baseSeed: Number.parseInt(job.id.slice(0, 6), 16) % 100_000,
  };
  await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
  if (job.options.motionPreview !== false) return motionPreviewPhase(job, dir, state);
  return motionFilmPhase(job, dir, state);
}

/** Voices, cuts and the film, once the script (and the sample picture) is settled. */
async function motionFilmPhase(job: MediaJob, dir: string, state: MotionState): Promise<void> {
  const { script, extraLoras, extraLooks, minutes, options } = state;
  const heroine = options.motionCharacterId
    ? await getCharacter(options.motionCharacterId)
    : undefined;
  const heroine2 = options.motionCharacterId2
    ? await getCharacter(options.motionCharacterId2)
    : undefined;
  const heroine3 = options.motionCharacterId3
    ? await getCharacter(options.motionCharacterId3)
    : undefined;
  const heroine4 = options.motionCharacterId4
    ? await getCharacter(options.motionCharacterId4)
    : undefined;
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();

  // Voices first, while the GPU is free of the image and video models. Every line speaks with one
  // reference recording: lines made from a description alone drift to another person by the end.
  // The voice asked for, else the heroine's own voice from the character library.
  const voiceId = options.voiceId ?? (options.voiceCaption?.trim() ? undefined : heroine?.voiceId);
  let referenceWav = voiceId ? await referenceClip(voiceId) : undefined;
  if (voiceId && !referenceWav)
    throw new Error("選んだ声の音声見本が見つかりません。声の設定を確認してください");
  const voiceFiles = new Map<string, string>();
  const toSpeak: Array<[string, string]> = [];
  const flowPhases = phasesInFlow(options.motionFlow);
  for (const [phase, lines] of Object.entries(script.lines)) {
    if (flowPhases && !flowPhases.has(phase as MotionPhase)) continue;
    lines.forEach((line, i) => toSpeak.push([`line-${phase}-${i}`, line]));
  }
  for (const [phase, text] of Object.entries(script.breaths))
    toSpeak.push([`breath-${phase}`, text]);
  const voiceDevices = generationCudaDevices(job.options.gpuMode === "fast");
  const voiceDual = voiceDevices.length >= 2;
  try {
    if (!referenceWav) {
      // A calm sample of the described voice becomes the reference for every line.
      const caption = options.voiceCaption ?? heroine?.voiceCaption ?? script.voice;
      const captionKey = createHash("sha256")
        .update(JSON.stringify(["irodori-large", caption, MOTION_VOICE_SAMPLE]))
        .digest("hex");
      const sample = path.join(dir, `voice-reference-${captionKey}.wav`);
      if (!(await exists(sample))) {
        markStage(job, "声の見本を作っています（最初はモデルの読み込みがあります）");
        const result = await synthesizeIrodori({
          text: MOTION_VOICE_SAMPLE,
          model: "large",
          caption,
          device: "cuda",
          lane: 0,
          cudaDevice: voiceDual ? voiceDevices[0] : undefined,
        });
        await fs.writeFile(sample, result.audio);
      }
      referenceWav = sample;
    }
    const referenceKey = createHash("sha256")
      .update(await fs.readFile(referenceWav))
      .digest("hex");
    // Every line speaks with the same reference recording, so the lines do not depend on each other:
    // with two GPUs, two workers take lines side by side. If the second GPU cannot hold the model
    // (the smaller card), its line goes back to the first worker and the second one stops.
    const speakOne = async (index: number, lane: number) => {
      const [name, text] = toSpeak[index]!;
      const audioKey = createHash("sha256")
        .update(JSON.stringify(["irodori-large", referenceKey, text]))
        .digest("hex");
      const file = path.join(dir, `${name}-${audioKey}.wav`);
      if (!(await exists(file))) {
        markStage(job, `声を作っています（${index + 1}/${toSpeak.length}）`);
        const result = await synthesizeIrodori({
          text,
          model: "large",
          referenceWav,
          device: "cuda",
          lane,
          cudaDevice: voiceDual ? voiceDevices[lane] : undefined,
        });
        await fs.writeFile(file, result.audio);
      }
      voiceFiles.set(name, file);
    };
    let nextLine = 0;
    const runLane = async (lane: number): Promise<void> => {
      for (;;) {
        if (job.cancelRequested) throw new Error("中止しました");
        const index = nextLine++;
        if (index >= toSpeak.length) return;
        try {
          await speakOne(index, lane);
        } catch (error) {
          if (lane === 0 || !voiceDual) throw error;
          await speakOne(index, 0);
          return;
        }
      }
    };
    await Promise.all(voiceDual ? [runLane(0), runLane(1)] : [runLane(0)]);
  } finally {
    stopIrodoriWorker();
  }

  const cuts = applyMotionFlow(
    motionCuts(script, minutes, options.motionTemplate, options.motionOptions, options.motionTalk),
    options.motionFlow,
  );
  if (heroine && !job.options.motionRevoiceJobId) {
    const constraints = characterMotionPrompt(readCharacterMotionProfile(heroine.id));
    if (constraints) for (const cut of cuts) cut.prompt = `${cut.prompt}, ${constraints}`;
  }
  const sfxSelected = options.motionSfx ?? DEFAULT_MOTION_SFX;
  const baseSeed = state.baseSeed ?? Number.parseInt(job.id.slice(0, 6), 16) % 100_000;
  const linesOf = (phase?: MotionPhase) =>
    phase
      ? script.lines[phase].map((text, i) => ({ wav: voiceFiles.get(`line-${phase}-${i}`), text }))
      : [];
  const plan = {
    name: script.name,
    out: "motion.mp4",
    cuts: cuts.map((cut) => ({
      clip: `${cut.name}.webm`,
      loop: cut.loop,
      seconds: cut.seconds,
      lines: linesOf(cut.phase),
      ...(cut.breath ? { breath: voiceFiles.get(`breath-${cut.breath}`) } : {}),
      ...(cut.fade ? { fade: cut.fade } : {}),
      ...(cut.flash ? { flash: true } : {}),
      ...(cut.insert ? { insert: cut.insert } : {}),
      ...(() => {
        const sfx = [
          ...(cut.sfx === "fellatio" ? [{ wav: MOTION_FELLATIO_SFX, gain: 0.55 }] : []),
          ...sfxForCut(cut.name, sfxSelected),
        ];
        return sfx.length > 0 ? { sfx } : {};
      })(),
      ...(options.motionTalk ? { breathGain: MOTION_TALK[options.motionTalk].breathGain } : {}),
    })),
  };
  const request = path.join(dir, "job.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(options.motionImageModel),
      reuseCutsOnly: Boolean(job.options.motionRevoiceJobId),
      // The tags of the JSON, and those the user set on each heroine in the app.
      negative: [
        motionImageNegative(),
        ...[heroine, heroine2, heroine3, heroine4].map((h) => h?.spec?.customNegative),
      ]
        .filter(Boolean)
        .join(", "),
      keys: motionKeyJobs(script, baseSeed, options.motionTemplate, options.motionOptions),
      loras: [
        ...(heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : []),
        ...extraLoras,
      ],
      // 多人数シーン: 2〜4人目の look タグ（Pythonスクリプトがキャラごとのプロンプトに使用）
      extraLooks: extraLooks.length > 0 ? extraLooks : undefined,
      // 前段シーン: カスタムステップ（Pythonスクリプトが着衣→本番シーケンスを生成）
      approachSteps: options.motionApproachSteps,
      cuts: cuts.map((cut, i) => ({
        name: cut.name,
        start: cut.start,
        end: cut.end,
        prompt: cut.prompt,
        seed: baseSeed + 10 + i,
      })),
      plan,
      // A draft stops after the key images and shows them on one sheet.
      stopAfterKeys: options.motionDraft === true,
    }),
  );
  await runMotionScript(job, dir, request, options.motionDraft === true);
}

/** Runs motion_comic.py on a job.json; a draft gives the key-image sheet, else the film. */
async function runMotionScript(
  job: MediaJob,
  dir: string,
  request: string,
  draft: boolean,
): Promise<void> {
  let produced = "";
  const devices = generationCudaDevices(job.options.gpuMode === "fast");
  await runTool(WAN2GP_PYTHON, [MOTION_SCRIPT, request], {
    cwd: path.dirname(MOTION_SCRIPT),
    cudaDevice: devices[0],
    env:
      devices.length > 1
        ? { OPENCLAW_DUAL_CUDA_DEVICES: devices.join(";") }
        : { OPENCLAW_DUAL_CUDA_DEVICES: "" },
    timeoutMs: MOTION_TIMEOUT_MS,
    onLine: (line) => {
      if (line.startsWith("STAGE ")) markStage(job, line.slice(6));
      else if (line.startsWith("RESULT ")) produced = line.slice(7).trim();
    },
  });
  if (!produced || !(await exists(produced)))
    throw new Error(draft ? "下書きの絵ができませんでした" : "動画ができませんでした");
  const file = await resultPath(job, draft ? ".png" : ".mp4");
  await fs.copyFile(produced, file);
  job.file = file;
  job.mime = draft ? "image/png" : "video/mp4";
  job.motion = { dir, ...(draft ? { draft: true } : {}) };
}

/** The checkpoint file of the key pictures: the chosen SDXL model, else the standard one. */
function motionModelFile(model?: ImageModel): string {
  return (model && isSdxlModel(model) ? sdxlSpecOf(model) : SDXL_MODELS.wai).file;
}

/** Takes the cast, voice, look and place of a finished motion comic for the new one. */
async function applyMotionInherit(
  job: MediaJob,
): Promise<{ note: string; script?: MotionScript; dir?: string }> {
  const id = job.options.motionInheritJobId;
  if (!id) return { note: "" };
  const source = await getMediaJob(id);
  const dir = source?.motion?.dir;
  if (!source || !dir) throw new Error("引き継ぐモーションコミックが見つかりません");
  const state = await fs
    .readFile(path.join(dir, "state.json"), "utf-8")
    .then((text) => JSON.parse(text) as MotionState)
    .catch(() => undefined);
  const from = state?.options ?? source.options;
  const mine = job.options as Record<string, unknown>;
  for (const key of [
    "motionCharacterId",
    "motionCharacterId2",
    "motionCharacterId3",
    "motionCharacterId4",
    "motionOutfit",
    "motionOutfit2",
    "motionOutfit3",
    "motionOutfit4",
    "voiceId",
    "voiceCaption",
    "motionImageModel",
  ] as const) {
    const value = (from as Record<string, unknown>)[key];
    if (mine[key] === undefined && value !== undefined) mine[key] = value;
  }
  const script = state?.script;
  const note = script
    ? `\n【前作の続き】前作と同じ登場人物。名前は「${script.name}」、${script.age}歳。声は「${script.voice}」。見た目と場所はそのまま。前作の続きの話として書く。`
    : "";
  return { note, ...(script ? { script } : {}), dir };
}

/** What a motion comic keeps in its folder once the script is written: enough to go on after the sample picture is approved, and to be inherited. */
type MotionState = {
  script: MotionScript;
  extraLoras: Array<{ path: string; scale: number }>;
  extraLooks: string[];
  minutes: MotionMinutes;
  options: MediaOptions;
  baseSeed?: number;
};

/** The first of the key pictures only, shown for approval before voices and video are made. */
async function motionPreviewPhase(job: MediaJob, dir: string, state: MotionState): Promise<void> {
  const { script, extraLoras, extraLooks, options } = state;
  const heroine = options.motionCharacterId
    ? await getCharacter(options.motionCharacterId)
    : undefined;
  const others = await Promise.all(
    [options.motionCharacterId2, options.motionCharacterId3, options.motionCharacterId4].map(
      (id) => (id ? getCharacter(id) : undefined),
    ),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, "確認用の絵を1枚描いています");
  const baseSeed = state.baseSeed ?? Number.parseInt(job.id.slice(0, 6), 16) % 100_000;
  const request = path.join(dir, "job.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(options.motionImageModel),
      negative: [motionImageNegative(), ...[heroine, ...others].map((h) => h?.spec?.customNegative)]
        .filter(Boolean)
        .join(", "),
      keys: motionKeyJobs(script, baseSeed, options.motionTemplate, options.motionOptions),
      loras: [
        ...(heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : []),
        ...extraLoras,
      ],
      extraLooks: extraLooks.length > 0 ? extraLooks : undefined,
      approachSteps: options.motionApproachSteps,
      cuts: [],
      plan: {},
      previewOnly: true,
      stopAfterKeys: true,
    }),
  );
  await runMotionScript(job, dir, request, true);
  job.motion = { dir, draft: true, preview: true };
}

// ---------- joining finished films ----------

const FFPROBE = path.join(path.dirname(VOICE_FFMPEG), "ffprobe.exe");

/** Cuts a bridge is made of: about 4 seconds each, up to 81 frames (16 fps) per cut. */
export function bridgePlan(seconds: number): { segments: number; frames: number } {
  const total = Math.min(60, Math.max(2, seconds));
  const segments = Math.max(1, Math.round(total / 4));
  const frames = Math.min(81, Math.max(33, Math.round((total / segments) * 16) + 1));
  return { segments, frames };
}

/** About how long the bridges and the final join take. */
export function joinEstimateSec(count: number, seconds: number): number {
  const { segments, frames } = bridgePlan(seconds);
  const perCut = 330 + frames * 8;
  return Math.max(0, count - 1) * (segments * perCut + 150 + (segments - 1) * 60) + 240;
}

async function probeVideo(
  file: string,
): Promise<{ width: number; height: number; seconds: number; audio: boolean }> {
  const lines: string[] = [];
  await runTool(
    FFPROBE,
    [
      "-v",
      "error",
      "-show_entries",
      "stream=codec_type,width,height:format=duration",
      "-of",
      "json",
      file,
    ],
    { onLine: (line) => lines.push(line), timeoutMs: 60_000 },
  );
  const data = JSON.parse(lines.join("")) as {
    streams?: Array<{ codec_type?: string; width?: number; height?: number }>;
    format?: { duration?: string };
  };
  const video = data.streams?.find((s) => s.codec_type === "video");
  return {
    width: video?.width ?? 1280,
    height: video?.height ?? 720,
    seconds: Number(data.format?.duration ?? 0) || 0,
    audio: data.streams?.some((s) => s.codec_type === "audio") ?? false,
  };
}

const BRIDGE_QUALITY = "masterpiece, best quality, amazing quality, very aesthetic, absurdres";

type BridgeStep = { tags: string; motion: string };

/** The writer looks at the last picture of the first film and the first picture of the next, and plans the moves between. */
async function planBridge(
  tagsA: string,
  tagsB: string,
  segments: number,
  contextA: string,
  contextB: string,
): Promise<BridgeStep[]> {
  const fallback: BridgeStep[] = Array.from({ length: segments }, () => ({
    tags: "",
    motion:
      "the scene changes smoothly from the first picture to the last picture, the two people move naturally into the new position, fixed camera",
  }));
  const system =
    "あなたは成人向けアニメ動画の編集者。2つの場面の間をつなぐ、自然な動きの流れを考える。出力はJSONオブジェクト1つだけ（説明や```は書かない）: " +
    '{"summary":"つなぎの流れの日本語1行","steps":[{"tags":"その区間が終わった時点の絵のdanbooruタグ（英語・カンマ区切り。人物の見た目、体位、場所、状態を全部書く）","motion":"その区間の動き（英語の短い文。固定カメラ）"}]}。' +
    "steps はちょうど指定された数。最後の step の tags は、つなぎ先の絵と同じ状態になる想定で書く（絵は使われない）。" +
    "体位が違うなら体位の移動（離れる、向きを変える、四つん這いになる、またがる など）、挿入されているなら抜く・入れ直す、射精後なら余韻から次の動作への移り、場所が違うなら移動、服が違うなら着替えや脱ぐ動き、を順に入れる。" +
    "同じ人物のまま、見た目（髪・目・体型・服）を変えない。急に場面が飛ばないよう、1区間で1つの動きにする。";
  const user =
    `つなぐ区間の数: ${segments}\n【前の場面の最後の絵のタグ】${tagsA}${contextA ? `\n【前の場面の情報】${contextA}` : ""}\n` +
    `【次の場面の最初の絵のタグ】${tagsB}${contextB ? `\n【次の場面の情報】${contextB}` : ""}`;
  try {
    const data = firstJsonObject(await askScriptWriter(system, user, 1_200));
    const steps = Array.isArray(data.steps) ? (data.steps as Array<Record<string, unknown>>) : [];
    const cleaned = steps.slice(0, segments).map((s) => ({
      tags: String(s.tags ?? "")
        .replace(/[^a-zA-Z0-9 ,()'.:_\-]/g, " ")
        .replace(/\s+/g, " ")
        .slice(0, 400)
        .trim(),
      motion: String(s.motion ?? "")
        .replace(/[^a-zA-Z0-9 ,()'.:_\-]/g, " ")
        .replace(/\s+/g, " ")
        .slice(0, 300)
        .trim(),
    }));
    while (cleaned.length < segments) cleaned.push(fallback[0] as BridgeStep);
    return cleaned.map((s) => (s.motion ? s : (fallback[0] as BridgeStep)));
  } catch {
    return fallback;
  }
}

async function stateContext(job: MediaJob | undefined): Promise<string> {
  const dir = job?.motion?.dir;
  if (!dir) return "";
  try {
    const state = JSON.parse(await fs.readFile(path.join(dir, "state.json"), "utf-8")) as {
      script?: MotionScript;
    };
    const s = state.script;
    return s ? `${s.title}／${s.name}／体位 ${s.position}／場所 ${s.place}` : "";
  } catch {
    return "";
  }
}

/** One bridge: pictures between, a video for each stretch, and the stretches put together. */
async function makeBridge(
  job: MediaJob,
  dir: string,
  index: number,
  label: string,
  from: { file: string; job: MediaJob },
  to: { file: string; job: MediaJob },
  segments: number,
  frames: number,
): Promise<string> {
  const id = (n: number) => `p${index}-s${n}`;
  const tail = path.join(dir, `${id(0)}.png`);
  const head = path.join(dir, `${id(segments)}.png`);
  markStage(job, `${label}：前後の絵を取り出しています`);
  const fit =
    "scale=1344:768:force_original_aspect_ratio=decrease,pad=1344:768:(ow-iw)/2:(oh-ih)/2,setsar=1";
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-sseof",
      "-0.4",
      "-i",
      from.file,
      "-vf",
      fit,
      "-update",
      "1",
      "-frames:v",
      "999",
      tail,
    ],
    { timeoutMs: 120_000 },
  );
  await runTool(
    VOICE_FFMPEG,
    ["-y", "-loglevel", "error", "-i", to.file, "-vf", fit, "-frames:v", "1", head],
    { timeoutMs: 120_000 },
  );
  for (const n of [0, segments]) {
    await runTool(
      VOICE_FFMPEG,
      [
        "-y",
        "-loglevel",
        "error",
        "-i",
        path.join(dir, `${id(n)}.png`),
        "-vf",
        "scale=832:480",
        path.join(dir, `${id(n)}-v.png`),
      ],
      { timeoutMs: 60_000 },
    );
  }

  markStage(job, `${label}：場面を解析して、つなぎを考えています`);
  const tagsOf = async (file: string) =>
    (
      await runTagger(file).catch(() => ({ general: [] as Array<[string, number]>, character: [] }))
    ).general
      .slice(0, 45)
      .map(([tag]) => tag)
      .join(", ");
  const tagsA = await tagsOf(tail);
  const tagsB = await tagsOf(head);
  const steps = await planBridge(
    tagsA,
    tagsB,
    segments,
    await stateContext(from.job),
    await stateContext(to.job),
  );

  const baseSeed = (Number.parseInt(job.id.slice(0, 6), 16) % 90_000) + index * 100;
  const options = from.job.options;
  const sourceState = await fs
    .readFile(path.join(from.job.motion?.dir ?? "", "state.json"), "utf-8")
    .then((text) => JSON.parse(text) as { options?: MediaOptions })
    .catch(() => undefined);
  const heroine = (sourceState?.options ?? options).motionCharacterId
    ? await getCharacter((sourceState?.options ?? options).motionCharacterId ?? "")
    : undefined;
  const keys = Array.from({ length: Math.max(0, segments - 1) }, (_, k) => ({
    id: id(k + 1),
    mode: "i2i" as const,
    src: id(k),
    strength: 0.72,
    seed: baseSeed + k + 1,
    prompt: `${BRIDGE_QUALITY}, ${steps[k]?.tags || tagsA}`,
  }));
  const cuts = steps.map((step, k) => ({
    name: `b${index}-${k}`,
    start: id(k),
    end: id(k + 1),
    prompt: `anime, ${step.motion}, smooth natural motion, fixed camera`,
    seed: baseSeed + 50 + k,
    frames,
  }));
  const bridgeName = `bridge-${index}.mp4`;
  const request = path.join(dir, `bridge-${index}.json`);
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(sourceState?.options?.motionImageModel),
      negative: [motionImageNegative(), heroine?.spec?.customNegative].filter(Boolean).join(", "),
      keys,
      loras:
        heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : [],
      cuts,
      plan: {
        name: "bridge",
        out: bridgeName,
        cuts: cuts.map((cut) => ({
          clip: `${cut.name}.webm`,
          loop: false,
          seconds: (frames - 1) / 16,
          lines: [],
        })),
      },
    }),
  );
  let produced = "";
  await runTool(WAN2GP_PYTHON, [MOTION_SCRIPT, request], {
    cwd: path.dirname(MOTION_SCRIPT),
    timeoutMs: MOTION_TIMEOUT_MS,
    onLine: (line) => {
      if (line.startsWith("STAGE ")) markStage(job, `${label}：${line.slice(6)}`);
      else if (line.startsWith("RESULT ")) produced = line.slice(7).trim();
    },
  });
  if (!produced || !(await exists(produced))) throw new Error("つなぎの動画ができませんでした");
  return produced;
}

/**
 * Joins finished films in the order given. Between two films a bridge is made: the last picture of
 * the first and the first picture of the next are read, the writer plans the moves between them
 * (changing position, pulling out or entering, moving to another place), pictures and short videos
 * are made for each step, and everything is put together. The result is a film too, so it can be
 * joined again, without a limit on how long it grows.
 */
async function runMotionJoin(job: MediaJob): Promise<void> {
  const ids = job.options.motionJoinJobIds ?? [];
  if (ids.length < 2) throw new Error("つなぐ動画を2本以上選んでください");
  const dir = path.join(outDir(), `${job.id}-join`);
  await fs.mkdir(dir, { recursive: true });
  const sources: Array<{ file: string; job: MediaJob }> = [];
  for (const [i, id] of ids.entries()) {
    const src = await getMediaJob(id);
    if (
      !src ||
      src.status !== "done" ||
      !src.file ||
      !src.mime?.startsWith("video/") ||
      !(await exists(src.file))
    ) {
      throw new Error(`つなぐ動画が見つかりません（${id}）。作っている途中か、失敗しています`);
    }
    const trim = job.options.motionJoinTrimSec?.[i];
    if (typeof trim === "number" && trim > 0) {
      // Only the first part of this film is kept: the other position takes over from there.
      const cut = path.join(dir, `trim-${i}.mp4`);
      if (!(await exists(cut))) {
        markStage(job, `${i + 1}本目を ${Math.round(trim)} 秒で区切っています`);
        await runTool(
          VOICE_FFMPEG,
          [
            "-y",
            "-loglevel",
            "error",
            "-i",
            src.file,
            "-t",
            String(trim),
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            "17",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            cut,
          ],
          { timeoutMs: 20 * 60_000 },
        );
      }
      sources.push({ file: cut, job: src });
    } else {
      sources.push({ file: src.file, job: src });
    }
  }
  const { segments, frames } = bridgePlan(job.options.motionBridgeSeconds ?? 3);
  job.motion = { dir };
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();

  const bridges: string[] = [];
  for (let i = 0; i < sources.length - 1; i++) {
    if (job.cancelRequested) throw new Error("中止しました");
    const done = path.join(dir, `bridge-${i}.mp4`);
    const label = `つなぎ目 ${i + 1}/${sources.length - 1}`;
    bridges.push(
      (await exists(done))
        ? done
        : await makeBridge(
            job,
            dir,
            i,
            label,
            sources[i] as (typeof sources)[number],
            sources[i + 1] as (typeof sources)[number],
            segments,
            frames,
          ),
    );
  }

  markStage(job, "動画を1本につないでいます");
  const segmentsList: string[] = [];
  sources.forEach((source, i) => {
    segmentsList.push(source.file);
    if (bridges[i]) segmentsList.push(bridges[i] as string);
  });
  const probes = await Promise.all(segmentsList.map((file) => probeVideo(file)));
  const first = probes[0] as (typeof probes)[number];
  const W = first.width - (first.width % 2);
  const H = first.height - (first.height % 2);
  const args: string[] = ["-y", "-loglevel", "error"];
  const filters: string[] = [];
  let inputIndex = 0;
  segmentsList.forEach((file, i) => {
    const probe = probes[i] as (typeof probes)[number];
    args.push("-i", file);
    const video = inputIndex++;
    filters.push(
      `[${video}:v]scale=${W}:${H}:force_original_aspect_ratio=decrease,pad=${W}:${H}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p[v${i}]`,
    );
    if (probe.audio) {
      filters.push(
        `[${video}:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo[a${i}]`,
      );
    } else {
      args.push(
        "-f",
        "lavfi",
        "-t",
        String(Math.max(0.1, probe.seconds)),
        "-i",
        "anullsrc=r=48000:cl=stereo",
      );
      const silent = inputIndex++;
      filters.push(`[${silent}:a]aformat=sample_fmts=fltp:channel_layouts=stereo[a${i}]`);
    }
  });
  filters.push(
    `${segmentsList.map((_, i) => `[v${i}][a${i}]`).join("")}concat=n=${segmentsList.length}:v=1:a=1[v][a]`,
  );
  const file = await resultPath(job, ".mp4");
  args.push(
    "-filter_complex",
    filters.join(";"),
    "-map",
    "[v]",
    "-map",
    "[a]",
    "-c:v",
    "libx264",
    "-preset",
    "medium",
    "-crf",
    "18",
    "-pix_fmt",
    "yuv420p",
    "-c:a",
    "aac",
    "-b:a",
    "192k",
    "-movflags",
    "+faststart",
    file,
  );
  await runTool(VOICE_FFMPEG, args, { timeoutMs: 120 * 60_000 });
  job.file = file;
  job.mime = "video/mp4";
  job.manga = { title: `つないだ動画（${sources.length}本）`, lines: [] };
}

/** Copy a finished production into this job's workspace before changing its soundtrack. */
async function revoiceMotionJob(job: MediaJob): Promise<void> {
  const source = await getMediaJob(job.options.motionRevoiceJobId ?? "");
  const sourceDir = source?.motion?.dir;
  if (
    !source ||
    source.kind !== "motion" ||
    source.status !== "done" ||
    !source.mime?.startsWith("video/") ||
    !sourceDir
  ) {
    throw new Error("声を変更する完成済みモーションコミックが見つかりません");
  }
  const state = JSON.parse(
    await fs.readFile(path.join(sourceDir, "state.json"), "utf-8"),
  ) as MotionState;
  const previous = JSON.parse(await fs.readFile(path.join(sourceDir, "job.json"), "utf-8")) as {
    keys: Array<{ id: string; seed: number }>;
    cuts: Array<{ name: string }>;
  };
  // Never silently regenerate a missing source cut with a different identity or seed.
  for (const cut of previous.cuts) {
    if (!(await exists(path.join(sourceDir, `${cut.name}.webm`)))) {
      throw new Error(
        "元作品の動画素材が不足しています。元作品を完成させてから声を変更してください",
      );
    }
  }
  const dir = path.join(outDir(), `${job.id}-motion`);
  markStage(job, "映像素材を引き継いでいます（元作品は保存されます）");
  await fs.cp(sourceDir, dir, { recursive: true, errorOnExist: true, force: false });
  state.baseSeed ??= previous.keys.find((key) => key.id === "k1")?.seed;
  if (state.baseSeed === undefined) throw new Error("元作品の生成設定が不足しています");
  state.options = {
    ...state.options,
    voiceId: job.options.voiceId,
    voiceCaption: job.options.voiceCaption,
  };
  await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
  job.manga = source.manga;
  await motionFilmPhase(job, dir, state);
}

/**
 * Goes on from a copy of a draft, keeping the script, voices and approved pictures.
 * With motionRedrawKeys the named key images (and those made from them) are drawn again with new
 * seeds and the result is another draft; otherwise the cuts and the film are made.
 */
async function continueMotionJob(job: MediaJob): Promise<void> {
  const source = await getMediaJob(job.options.motionContinueJobId ?? "");
  const sourceDir = source?.motion?.dir;
  if (
    !source ||
    source.status !== "done" ||
    !sourceDir ||
    !(await exists(path.join(sourceDir, "job.json")))
  )
    throw new Error("続きを作る下書きが見つかりません");
  const dir = path.join(outDir(), `${job.id}-motion`);
  await fs.cp(sourceDir, dir, { recursive: true, errorOnExist: true, force: false });
  const request = path.join(dir, "job.json");
  job.manga = source.manga;
  if (source.motion?.preview && !job.options.motionRedrawKeys?.length) {
    // The sample picture is approved: make voices, cuts and the film in the copied workspace.
    const state = JSON.parse(
      await fs.readFile(path.join(dir, "state.json"), "utf-8"),
    ) as MotionState;
    const previous = JSON.parse(await fs.readFile(request, "utf-8")) as {
      keys: Array<{ id: string; seed: number }>;
    };
    state.baseSeed ??= previous.keys.find((key) => key.id === "k1")?.seed;
    if (state.baseSeed === undefined) throw new Error("下書きの生成設定が不足しています");
    await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
    return motionFilmPhase(job, dir, state);
  }
  const data = JSON.parse(await fs.readFile(request, "utf-8")) as {
    dir: string;
    keys: Array<{ id: string; seed: number; src?: string }>;
    cuts: Array<{ name: string; start: string; end: string }>;
    plan?: {
      cuts: Array<{
        lines?: Array<{ wav?: string }>;
        breath?: string;
        sfx?: Array<{ wav?: string }>;
      }>;
    };
    stopAfterKeys?: boolean;
    previewOnly?: boolean;
  };
  data.dir = dir;
  const localAudio = (file: string | undefined): string | undefined =>
    file && file.startsWith(sourceDir + path.sep)
      ? path.join(dir, path.relative(sourceDir, file))
      : file;
  for (const cut of data.plan?.cuts ?? []) {
    for (const line of cut.lines ?? []) line.wav = localAudio(line.wav);
    cut.breath = localAudio(cut.breath);
    for (const effect of cut.sfx ?? []) effect.wav = localAudio(effect.wav);
  }
  const redraw = new Set<string>();
  for (const key of job.options.motionRedrawKeys ?? []) {
    if (!data.keys.some((k) => k.id === key)) continue;
    redraw.add(key);
  }
  // Follow the actual dependency graph, including indirect derivatives.
  let expanded = true;
  while (expanded) {
    expanded = false;
    for (const key of data.keys) {
      if (key.src && redraw.has(key.src) && !redraw.has(key.id)) {
        redraw.add(key.id);
        expanded = true;
      }
    }
  }
  for (const key of redraw) {
    const entry = data.keys.find((k) => k.id === key);
    if (entry) entry.seed += 977;
    for (const name of [`${key}.png`, `${key}-v.png`, `${key}-mask.png`]) {
      await fs.rm(path.join(dir, name), { force: true });
    }
  }
  // Cuts drawn from a redrawn picture are made again too.
  for (const cut of data.cuts) {
    if (redraw.has(cut.start) || redraw.has(cut.end))
      await fs.rm(path.join(dir, `${cut.name}.webm`), { force: true });
  }
  const draft = redraw.size > 0;
  data.stopAfterKeys = draft;
  await fs.writeFile(request, JSON.stringify(data));
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  await runMotionScript(job, dir, request, draft);
  if (data.previewOnly) job.motion = { dir, draft: true, preview: true };
}

/** Typesets panels into a page with vertical bubbles (PIL, in the Wan2GP venv). */
const MANGA_COMPOSE = "D:\\media-tools\\image\\manga_compose.py";
const MANGA_FONT = "C:\\Windows\\Fonts\\BIZ-UDGothicB.ttc";

/**
 * Text for the chat model that writes scripts. The script is written before the GPU is taken;
 * when a previous job still holds the GPU it is handed back first, and while the model is busy
 * answering a chat this waits for it (up to a few minutes) before falling back to the CPU worker.
 */
async function askScriptWriter(
  system: string,
  user: string,
  maxTokens: number,
  schema?: Record<string, unknown>,
): Promise<string> {
  if (pausedByUs) {
    stopImageWorker();
    await restoreChatModel();
  }
  // Scripts are written by the partner chat model (Gemma 4 26B): its Japanese dialogue is far
  // more natural than the agent model's. Swapping it in takes ~35 s; it stays for the chats.
  await enterGroupChatMode().catch(() => undefined);
  const deadline = Date.now() + 4 * 60_000;
  while (Date.now() < deadline) {
    const reply = await askMainLlm(system, user, maxTokens, 6 * 60_000, schema);
    if (reply) return reply;
    // Voice training owns the GPU: the chat model will not come back soon.
    if ((await exists(pauseFlag())) && !pausedByUs) break;
    await new Promise((resolve) => setTimeout(resolve, 5_000));
  }
  return askGemma(system, user, maxTokens);
}

async function writeScript<T>(
  job: MediaJob,
  stage: string,
  system: string,
  user: string,
  maxTokens: number,
  parse: (reply: string) => T,
  schema?: Record<string, unknown>,
): Promise<T> {
  markStage(job, stage);
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    if (job.cancelRequested) throw new Error("中止しました");
    try {
      return parse(await askScriptWriter(system, user, maxTokens, schema));
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`脚本を作れませんでした: ${lastError}`);
}

type DrawnPanel = {
  image: string;
  lines: Array<ComicPanel["lines"][number] & { kind?: BubbleKind }>;
  narration?: string;
  /** Where each speaker stands, so their bubble goes on their side. */
  speakers: Record<string, StagePosition>;
  /** Multi-page manga: sound effects and a speed/focus effect drawn over the picture. */
  sfx?: string[];
  effect?: string;
};

/** Draws one panel of a manga page or comic episode. */
async function drawPanel(
  job: MediaJob,
  cast: ComicCharacter[],
  panel: ComicPanel,
  aspect: NonNullable<MediaOptions["aspect"]>,
  sexual: boolean,
  file: string,
  /** Which GPU's resident worker draws it (multi-page manga uses both). */
  gpu = 0,
): Promise<DrawnPanel> {
  const render = renderPanel(cast, panel);
  await renderImage(job, render.prompt, sexual, file, {
    aspect,
    negative: `${MANGA_EXTRA_NEGATIVE}, ${render.negative}`,
    regions: render.regions,
    loras: render.loras,
    gpu,
  });
  return {
    image: file,
    lines: panel.lines,
    ...(panel.narration ? { narration: panel.narration } : {}),
    speakers: speakerPositions(panel),
  };
}

/**
 * Runs picture tasks on one resident image worker per GPU (two when both can draw an SDXL-type
 * picture, else one). Each lane takes the next task as soon as it is free, so a queue of pictures
 * takes about half as long. The first failure stops both lanes and is thrown.
 */
async function drawOnLanes(
  job: MediaJob,
  tasks: Array<(gpu: number) => Promise<void>>,
  doneLabel: string,
): Promise<void> {
  const lanes =
    generationCudaDevices(job.options.gpuMode === "fast").length >= 2 &&
    !isWanImageModel(job.options.imageModel) &&
    job.options.imageModel !== "anima" &&
    (await exists(IMAGE_SERVER))
      ? 2
      : 1;
  job.parallelPictures = lanes > 1;
  let next = 0;
  let done = 0;
  let stopped = false;
  const results = await Promise.allSettled(
    Array.from({ length: lanes }, async (_, gpu) => {
      while (next < tasks.length && !stopped) {
        if (job.cancelRequested) throw new Error("中止しました");
        const task = tasks[next++]!;
        try {
          await task(gpu);
        } catch (error) {
          stopped = true;
          throw error;
        }
        done += 1;
        markStage(job, `${doneLabel}（${done}/${tasks.length}）${lanes > 1 ? "・GPU2枚" : ""}`);
      }
    }),
  );
  const failure = results.find((r): r is PromiseRejectedResult => r.status === "rejected");
  if (failure) throw failure.reason;
}

/** Runs the typesetter; returns the files it wrote (one page, or the parts of a strip). */
async function composePanels(
  workDir: string,
  spec: {
    layout: string;
    title: string;
    episodeTitle?: string;
    panels?: DrawnPanel[];
    /** layout "book": every page with its slots in page pixels */
    pages?: Array<{
      id: string;
      slots: PageLayout["slots"];
      panels: DrawnPanel[];
    }>;
    tone?: "color" | "mono";
    /** layout "book": the cover picture, drawn first and written as the first file */
    cover?: { image: string; title: string; volume: number };
    out: string;
  },
): Promise<string[]> {
  const specFile = path.join(workDir, "page.json");
  await fs.writeFile(specFile, JSON.stringify({ ...spec, font: MANGA_FONT, fontBold: MANGA_FONT }));
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [MANGA_COMPOSE, specFile], {
    cwd: workDir,
    timeoutMs: 10 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (!result) return;
      produced = result.trim().startsWith("[") ? (JSON.parse(result) as string[]) : [result.trim()];
    },
  });
  return produced.length > 0 ? produced : [spec.out];
}

function scriptText(cast: ComicCharacter[], panels: ComicPanel[], title: string): string[] {
  return [
    title,
    ...cast.map((c) => c.look),
    ...panels.flatMap((p) => [
      p.scene,
      p.narration ?? "",
      ...p.characters.map((c) => c.action),
      ...p.lines.map((l) => l.text),
    ]),
  ];
}

function linesSummary(panels: ComicPanel[]): string[] {
  return panels.map((p) =>
    [p.narration ? `（${p.narration}）` : "", ...p.lines.map((l) => `${l.who}「${l.text}」`)]
      .filter(Boolean)
      .join(" "),
  );
}

/**
 * Manga: the chat model writes the script while it still has the GPU, then every panel is drawn
 * with the image model and the panels are put on a page with frames and speech bubbles.
 */
async function runMangaJob(job: MediaJob): Promise<void> {
  const layout = job.options.mangaLayout ?? "4koma";
  if (layout === "book") return runBookJob(job);
  const production = await productionRequest(job);
  const adult = isSexualRequest(production);
  const script = await writeScript(
    job,
    "脚本（コマ割りと台詞）を考えています",
    mangaScriptSystemPrompt(layout, adult),
    production,
    2_500,
    (reply) => parseMangaScript(reply, layout),
  );
  const allText = scriptText(script.cast, script.panels, script.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = isSexualRequest(`${production} ${allText.join(" ")}`);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const workDir = path.join(outDir(), `${job.id}-manga`);
  await fs.mkdir(workDir, { recursive: true });
  try {
    const aspects = MANGA_PANEL_ASPECTS[layout];
    const panels = new Array<DrawnPanel>(script.panels.length);
    await drawOnLanes(
      job,
      script.panels.map((panel, index) => async (gpu: number) => {
        const file = path.join(workDir, `panel-${index + 1}.png`);
        panels[index] = await drawPanel(
          job,
          script.cast,
          panel,
          aspects[index] ?? "landscape",
          sexual,
          file,
          gpu,
        );
      }),
      "コマを描きました",
    );
    markStage(job, "コマを並べて吹き出しを入れています");
    const file = await resultPath(job, ".png");
    await composePanels(workDir, {
      layout,
      title: script.title,
      panels,
      tone: job.options.mangaTone,
      out: file,
    });
    job.file = file;
    job.mime = "image/png";
    // The script goes with the result so the app (and the assistant) can show what was written.
    job.manga = { title: script.title, lines: linesSummary(script.panels) };
  } finally {
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => undefined);
  }
}

/** The cover picture: the main characters standing together, looking at the reader. */
function coverPanel(cast: ComicCharacter[]): ComicPanel {
  const people = cast.slice(0, 3);
  const spots: StagePosition[] =
    people.length === 1
      ? ["center"]
      : people.length === 2
        ? ["left", "right"]
        : ["left", "center", "right"];
  return {
    frame: "tall",
    scene:
      "book cover illustration, key visual, dynamic composition, cowboy shot, looking at viewer, detailed background, vivid colors",
    characters: people.map((c, i) => ({
      name: c.name,
      pos: spots[i] ?? "center",
      action: "standing, looking at viewer, smile",
    })),
    lines: [],
  };
}

/**
 * Multi-page manga: the chat model writes the outline, then each page (3-6 panels with a camera,
 * a size, bubble kinds and sound effects); every page gets a template that fits its panels, the
 * panels are drawn in the shape of their slot, and the typesetter lays the pages out.
 */
async function runBookJob(job: MediaJob): Promise<void> {
  const pageCount = Math.max(
    MANGA_MIN_PAGES,
    Math.min(MANGA_MAX_PAGES, Math.round(job.options.mangaPages ?? MANGA_DEFAULT_PAGES)),
  );
  const production = await productionRequest(job);
  // A sequel starts from what the earlier volume saved: its cast, summary and how it ended.
  const earlier = job.options.mangaSequelOf ? await loadBook(job.options.mangaSequelOf) : undefined;
  if (job.options.mangaSequelOf && !earlier) throw new Error("前の巻の記録が見つかりません");
  const adult = isSexualRequest(`${production} ${earlier ? earlier.request : ""}`);
  const previously = earlier ? previouslyText(earlier) : undefined;
  // People already decided: the last volume's cast, then the ones picked from the character
  // library (linked ones take the library's current look and LoRA). The model fills in the rest.
  const picked: ComicCharacter[] = [];
  for (const id of job.options.characterLock?.characterIds ?? []) {
    const entry = await getCharacter(id);
    if (entry)
      picked.push({
        characterId: entry.id,
        name: entry.name,
        look: characterLook(entry),
        profile: entry.profile,
      });
  }
  const carried = earlier?.cast ?? [];
  const fixed = await resolveCast(
    [
      ...carried,
      ...picked.filter(
        (p) =>
          !carried.some(
            (c) => (c.characterId && c.characterId === p.characterId) || c.name === p.name,
          ),
      ),
    ],
    { adult },
  );
  const autoCast = job.options.mangaAutoCast !== false;
  const outline = await writeScript(
    job,
    earlier ? `第${earlier.volume + 1}巻の流れを考えています` : "全体の流れを考えています",
    bookOutlinePrompt(pageCount, adult, {
      cast: fixed,
      autoCast,
      ...(earlier && previously ? { previously, volume: earlier.volume } : {}),
    }),
    production,
    4_000,
    (reply) => parseBookOutline(reply, pageCount),
  );
  // Decided characters are drawn from their saved looks, whatever the model wrote this time.
  outline.cast = mergeBookCast(outline.cast, fixed, autoCast);
  if (earlier) outline.previously = previously;
  const pages: BookPanel[][] = [];
  for (let index = 0; index < pageCount; index++) {
    const previous = (pages.at(-1) ?? [])
      .slice(-2)
      .map((p) =>
        [p.narration, ...p.lines.map((l) => `${l.who}「${l.text}」`)].filter(Boolean).join(" "),
      )
      .join("\n");
    pages.push(
      await writeScript(
        job,
        `脚本を書いています（${index + 1}/${pageCount}ページ）`,
        bookPagePrompt(outline, index + 1, pageCount, previous, adult),
        production,
        4_000,
        parseBookPage,
      ),
    );
  }
  const everyPanel = pages.flat();
  const allText = scriptText(outline.cast, everyPanel, outline.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = isSexualRequest(`${production} ${allText.join(" ")}`);

  // The templates are chosen before drawing so each panel is drawn in the shape of its slot.
  const layouts: PageLayout[] = [];
  let previousId: string | undefined;
  for (const [index, panels] of pages.entries()) {
    const big = panels.findIndex((p) => p.size === "large");
    const layout = planPage(panels.length, {
      pageNo: index + 1,
      bigIndex: big >= 0 ? big : undefined,
      previousId,
      slant: index % 2 === 1,
    });
    previousId = layout.id;
    layouts.push(layout);
  }

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const workDir = path.join(outDir(), `${job.id}-manga`);
  await fs.mkdir(workDir, { recursive: true });
  try {
    // Every picture (the cover, then each page's panels) is a task; with two GPUs two resident
    // workers take the next task each, so the pictures take about half as long.
    const drawn: DrawnPanel[][] = pages.map((panels) => new Array<DrawnPanel>(panels.length));
    const wantCover = job.options.mangaCover !== false;
    const coverFile = path.join(workDir, "cover.png");
    const tasks: Array<(gpu: number) => Promise<void>> = [];
    if (wantCover) {
      tasks.push(async (gpu) => {
        await drawPanel(
          job,
          outline.cast,
          coverPanel(outline.cast),
          "portrait",
          sexual,
          coverFile,
          gpu,
        );
      });
    }
    for (const [pageIndex, panels] of pages.entries()) {
      for (const [index, panel] of panels.entries()) {
        tasks.push(async (gpu) => {
          const slot = layouts[pageIndex]!.slots[index]!;
          const file = path.join(workDir, `panel-${pageIndex + 1}-${index + 1}.png`);
          const picture = await drawPanel(
            job,
            outline.cast,
            panel,
            slotAspect(slot),
            sexual,
            file,
            gpu,
          );
          drawn[pageIndex]![index] = {
            ...picture,
            lines: picture.lines.map((line, i) => ({ ...line, kind: panel.kinds[i] ?? "say" })),
            sfx: panel.sfx,
            effect: panel.effect,
          };
        });
      }
    }
    await drawOnLanes(job, tasks, "絵を描きました");
    markStage(job, "ページを組んで吹き出しを入れています");
    const file = await resultPath(job, ".png");
    const volume = (earlier?.volume ?? 0) + 1;
    const produced = await composePanels(workDir, {
      layout: "book",
      title: outline.title,
      pages: drawn.map((panels, i) => ({
        id: layouts[i]!.id,
        slots: layouts[i]!.slots,
        panels,
      })),
      tone: job.options.mangaTone,
      ...(wantCover ? { cover: { image: coverFile, title: outline.title, volume } } : {}),
      out: file,
    });
    job.file = produced[0] ?? file;
    job.files = produced;
    job.mime = "image/png";
    // A sequel stays in its work (folder); a first volume starts a new one named after it.
    const workId =
      (earlier?.workId && (await getWork(earlier.workId)) ? earlier.workId : undefined) ??
      (await createWork(outline.title)).id;
    await touchWork(workId);
    job.manga = { title: outline.title, lines: linesSummary(everyPanel), volume, cover: wantCover };
    // Kept so the next volume can be written from it (cast, story so far, how this one ended).
    await saveBook({
      workId,
      jobId: job.id,
      volume,
      title: outline.title,
      request: production,
      cast: outline.cast,
      outline: outline.pages,
      pages,
      summary: outline.summary,
      hook: outline.hook,
      history: earlier
        ? [
            ...earlier.history,
            { volume: earlier.volume, title: earlier.title, summary: earlier.summary },
          ]
        : [],
      ...(earlier ? { previousJobId: earlier.jobId } : {}),
      createdAt: Date.now(),
    });
  } finally {
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => undefined);
  }
}

// ---------- serial comics ----------

/** An episode keeps its script and panel images so single panels can be redrawn later. */
function episodeDir(jobId: string): string {
  return path.join(outDir(), "comic", jobId);
}

type EpisodeFiles = {
  seriesId: string;
  script: ComicScript;
  panels: DrawnPanel[];
  aspects: Array<NonNullable<MediaOptions["aspect"]>>;
  sexual: boolean;
  episodeNo: number;
};

async function composeEpisode(
  job: MediaJob,
  dir: string,
  series: ComicSeries,
  files: EpisodeFiles,
) {
  markStage(job, "縦読みに組んで吹き出しを入れています");
  const parts = await composePanels(dir, {
    layout: "webtoon",
    title: series.title,
    episodeTitle: `第${files.episodeNo}話 ${files.script.title}`,
    panels: files.panels,
    out: path.join(dir, "part.png"),
  });
  return parts;
}

async function runComicJob(job: MediaJob): Promise<void> {
  const task = job.options.comicTask ?? "episode";
  // Sheets and training belong to a library character, not to one series.
  if (task === "sheet") return runCharacterSheet(job);
  if (task === "lora") return runCharacterLora(job);
  if (task === "outpaint") return runCharacterOutpaint(job);
  if (task === "avatar") return runCharacterAvatar(job);
  if (task === "mini") return runCharacterMini(job);
  if (task === "model3d") return runCharacterModel3d(job);
  if (task === "sample")
    return job.options.sampleKeys?.length ? runSampleBatch(job) : runCharacterSample(job);
  const seriesId = job.options.comicSeriesId ?? "";
  const series = await getComicSeries(seriesId);
  if (!series) throw new Error("作品が見つかりません");
  // Linked characters draw with the library's current look, sheet and trained LoRA.
  series.cast = await resolveCast(series.cast, { adult: series.rating === "adult" });
  if (task === "redraw") return runPanelRedraw(job, series);

  const episodeNo = (series.episodes.at(-1)?.no ?? 0) + 1;
  const panelCount = COMIC_PANELS_PER_EPISODE;
  const production = await productionRequest(job);
  const request = production ? `今回の話への要望: ${production}` : "今回の話への要望: おまかせ";
  const outline = await writeScript(
    job,
    `第${episodeNo}話の流れを考えています`,
    episodeOutlinePrompt(series),
    request,
    // 12-16 beats did not fit in 1,500 tokens and came back cut off.
    4_000,
    (reply) => {
      const data = firstJsonObject(reply);
      const beats = Array.isArray(data.beats)
        ? data.beats.map((b) => clip(b, 200)).filter(Boolean)
        : [];
      if (beats.length < 3) throw new Error("話の流れが短すぎます");
      return `題名案: ${clip(data.title, 30)}\n${beats.map((b, i) => `${i + 1}. ${b}`).join("\n")}\n引き: ${clip(data.hook, 200)}`;
    },
    COMIC_OUTLINE_SCHEMA,
  );
  // The episode is written in parts of 24 panels; each part sees how the previous one ended.
  const partCount = Math.ceil(panelCount / COMIC_PANELS_PER_PART);
  let script: ComicScript | undefined;
  for (let index = 1; index <= partCount; index++) {
    const previous = script?.panels
      .slice(-4)
      .map((p) =>
        [p.narration, ...p.lines.map((l) => `${l.who}「${l.text}」`)].filter(Boolean).join(" "),
      )
      .join("\n");
    const part = await writeScript(
      job,
      `第${episodeNo}話の脚本を書いています（${index}/${partCount}、全${panelCount}コマ）`,
      episodeSystemPrompt(series, COMIC_PANELS_PER_PART, outline, {
        index,
        count: partCount,
        previous,
      }),
      request,
      9_000,
      (reply) => parseComicScript(reply, COMIC_PANELS_PER_PART),
      comicScriptSchema(COMIC_PANELS_PER_PART),
    );
    script = script
      ? {
          title: script.title || part.title,
          summary: part.summary || script.summary,
          panels: [...script.panels, ...part.panels],
        }
      : part;
  }
  if (!script) throw new Error("脚本を作れませんでした");
  const allText = scriptText(series.cast, script.panels, script.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = series.rating === "adult" || isSexualRequest(`${production} ${allText.join(" ")}`);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const dir = episodeDir(job.id);
  await fs.mkdir(dir, { recursive: true });
  const aspects = script.panels.map((p) => FRAME_ASPECT[p.frame]);
  const panels = new Array<DrawnPanel>(script.panels.length);
  await drawOnLanes(
    job,
    script.panels.map((panel, index) => async (gpu: number) => {
      const file = path.join(dir, `panel-${String(index + 1).padStart(2, "0")}.png`);
      panels[index] = await drawPanel(
        job,
        series.cast,
        panel,
        aspects[index] ?? "square",
        sexual,
        file,
        gpu,
      );
    }),
    "コマを描きました",
  );
  const files: EpisodeFiles = { seriesId, script, panels, aspects, sexual, episodeNo };
  await fs.writeFile(path.join(dir, "episode.json"), JSON.stringify(files, null, 2));
  const parts = await composeEpisode(job, dir, series, files);
  job.file = parts[0];
  job.files = parts;
  job.mime = "image/png";
  job.comic = { seriesId, episodeNo, title: script.title };
  job.manga = { title: `第${episodeNo}話 ${script.title}`, lines: linesSummary(script.panels) };
  // Reload: the series may have changed (a character sheet) while this episode was drawn.
  const latest = (await getComicSeries(seriesId)) ?? series;
  latest.episodes.push({
    no: episodeNo,
    title: script.title,
    summary: script.summary,
    jobId: job.id,
    createdAt: Date.now(),
  });
  latest.updatedAt = Date.now();
  await saveComicSeries(latest);
}

// ─── キャラクターシート生成：正確なプロンプト定義 ───────────────────────────

/**
 * 三面図（三面） ― 正面・側面・背面を横1列で固定レイアウト
 * 絶対に向きが混在しないよう ControlNet / img2img ではなくプロンプトで完全指定。
 * SD系モデルは "character sheet" + 各ビューを明示すると横並びで出力する。
 */
const THREE_VIEW_SCENE =
  "character reference sheet, orthographic views, three views, " +
  "left column: front view facing viewer straight on, " +
  "center column: side view facing right, profile view, exact 90 degrees, " +
  "right column: back view facing away from viewer, " +
  "full body, standing upright, arms at sides, neutral pose, " +
  "plain white background, no background, studio lighting, flat lighting, " +
  "same character consistent design across all three views";

const THREE_VIEW_NEG =
  "text, watermark, signature, speech bubble, tilted, angled incorrectly, " +
  "diagonal view, 3/4 view, multiple characters, duplicates, " +
  "bad anatomy, extra limbs, deformed, lowres, blurry";

/**
 * 360度ターンアラウンド（8方向, 2×4グリッド）
 * 0° / 45° / 90° / 135° / 180° / 225° / 270° / 315° を並べる。
 */
const TURNAROUND_360_SCENE =
  "character turnaround sheet, 360 degree rotation reference, 8 views, " +
  "2x4 grid layout, " +
  "top row left to right: front view 0deg, 45deg front-right diagonal, right side view 90deg, 135deg back-right diagonal, " +
  "bottom row left to right: back view 180deg, 225deg back-left diagonal, left side view 270deg, 315deg front-left diagonal, " +
  "consistent character design all views, full body standing, arms at sides, " +
  "plain white background, studio lighting";

const TURNAROUND_360_NEG =
  "text, watermark, multiple characters, inconsistent design, " +
  "wrong angle, bad anatomy, deformed, lowres, blurry";

/** The colour word of her hair in a look ("blonde hair" → "blonde"), for pubic hair of the same colour. */
export function hairColorOf(look: string): string | undefined {
  const match =
    /\b(black|brown|dark brown|light brown|blonde|golden|white|silver|grey|gray|red|pink|blue|light blue|dark blue|navy|green|purple|violet|lavender|orange|aqua|teal|cyan)\s+(?:long |short |medium )?hair/i.exec(
      look,
    );
  return match?.[1]?.toLowerCase();
}

/** 成人向け追加プロンプト（look の服装を上書き）。乳首・乳輪・陰毛（髪と同じ色）・局部も描くように書く。 */
export function nsfwLook(baseLook: string): string {
  const color = hairColorOf(baseLook);
  return (
    baseLook.replace(
      /\b(wearing|dressed in|clothes|outfit|uniform|shirt|dress|skirt|pants|jacket|coat|blouse)[^,]*/gi,
      "",
    ) +
    ", nude, completely nude, naked, bare body, no clothes, nipples, areolae, navel, pussy, " +
    (color ? `${color} pubic hair, ` : "pubic hair, ") +
    "uncensored"
  );
}

/**
 * The sheet view the app asked for. The app sends `view` ("front" / "three_view" / "360") and the
 * old `turnaround` flag; anything else means the front view.
 */
export function comicSheetViewOf(
  view: unknown,
  turnaround?: unknown,
): NonNullable<MediaOptions["comicSheetView"]> {
  if (view === "three_view" || view === "360" || view === "turnaround" || view === "front")
    return view;
  return turnaround === true ? "three_view" : "front";
}

/** The pose as a short English phrase: Japanese is translated by the local model; English is used as written. */
async function poseTags(raw: string | undefined): Promise<string> {
  const text = (raw ?? "")
    .replace(/[\r\n]+/g, " ")
    .trim()
    .slice(0, 200);
  if (!text) return "";
  if (/^[\x20-\x7e]+$/.test(text)) return text;
  try {
    const reply = await askScriptWriter(
      "次の日本語のポーズ指定を、画像生成用の短い英語（danbooru風のタグか短い句、カンマ区切り、20語以内）に直してください。出力は英語のみ。",
      text,
      200,
    );
    const english = reply
      .replace(/[^\x20-\x7e]/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 200);
    return english || text;
  } catch {
    return text;
  }
}

/** A character sheet: front view / three-view / 360-degree turnaround. */
async function runCharacterSheet(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");

  const rawView = job.options.comicSheetView ?? "front";
  // 旧 "turnaround" は "three_view" として互換維持
  const view = rawView === "turnaround" ? "three_view" : rawView;
  const isAdult = job.options.comicSheetAdult === true;
  const pose = view === "front" ? await poseTags(job.options.comicSheetPose) : "";

  // Made from a photo: the front sheet keeps the person of the photo.
  if (character.photoRef && view === "front" && !isAdult && (await exists(character.photoRef))) {
    await runSheetFromPhoto(job, character);
    return;
  }
  // The adult standing picture of a character that already has a standing picture: the same
  // picture is edited (Qwen Image 2.1), so it is the same person whatever the SDXL model can draw.
  if (isAdult && view === "front" && character.refImage && (await exists(character.refImage))) {
    await runSheetByEdit(job, character, {
      source: character.refImage,
      adult: true,
      stage: "立ち絵から、成人向けの立ち絵を作っています",
      prompt:
        "Keep the same adult woman exactly as in the picture: face, hair, eye color, body shape and the same plain background. " +
        `Change only this: she is completely nude, ${pose ? `in this pose: ${pose}` : "standing, arms relaxed at her sides"}, front view, full body. ` +
        `${ANATOMY_CHEST}. ${ANATOMY_GROIN}.`,
    });
    return;
  }

  let scene: string;
  let aspect: "portrait" | "landscape" | "square";
  let negative: string;

  switch (view) {
    case "three_view":
      scene = THREE_VIEW_SCENE;
      aspect = "landscape";
      negative = THREE_VIEW_NEG;
      break;
    case "360":
      scene = TURNAROUND_360_SCENE;
      aspect = "landscape"; // 2×4グリッドは横長
      negative = TURNAROUND_360_NEG;
      break;
    default: // "front"
      scene = `full body, ${pose || "standing"}, front view, looking at viewer, simple background, white background`;
      aspect = "portrait";
      negative = MANGA_EXTRA_NEGATIVE;
  }

  const panel: ComicPanel = {
    frame: view === "front" ? "tall" : "wide",
    scene,
    characters: [
      {
        name: character.name,
        pos: "center",
        action: pose ? `${pose}, light smile` : "neutral pose, arms at sides, light smile",
      },
    ],
    lines: [],
  };

  assertAllowedRequest(characterLook(character));
  markStage(job, "GPU を空けています");
  await acquireGpu(job);

  const dir = characterAssetDir(character.id);
  await fs.mkdir(dir, { recursive: true });
  const suffix = `${view}${isAdult ? "-adult" : ""}`;
  const file = path.join(dir, `sheet-${suffix}-${randomBytes(3).toString("hex")}.png`);

  const cast = await resolveCast([{ ...character, characterId: character.id }]);
  // アウトフィット差し替え（成人向けは裸体ルックに上書き）
  const outfit = character.outfits?.find((o) => o.name === job.options.comicOutfit);
  if (outfit && cast[0]) cast[0] = { ...cast[0], look: lookWithOutfit(cast[0].look, outfit.tags) };
  if (isAdult && cast[0]) cast[0] = { ...cast[0], look: nsfwLook(cast[0].look) };

  const render = renderPanel(cast, panel);
  await renderImage(job, render.prompt, false, file, {
    aspect,
    negative: `${negative}, ${render.negative}`,
    loras: render.loras,
  });

  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: job.options.comicSeriesId ?? "",
    character: character.name,
    characterId: character.id,
    view,
    ...(isAdult ? { adult: true } : {}),
  };

  // turnaroundJobId を常に更新（front 以外は turnaroundJobId に格納）
  if (view !== "front") {
    await updateCharacter(character.id, { turnaroundJobId: job.id });
  }
}

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
async function runCharacterAvatar(job: MediaJob): Promise<void> {
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
async function runCharacterMini(job: MediaJob): Promise<void> {
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
async function runCharacterModel3d(job: MediaJob): Promise<void> {
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

/** Width and height of a PNG from its header. */
async function pngSize(file: string): Promise<{ width: number; height: number }> {
  const handle = await fs.open(file, "r");
  try {
    const head = Buffer.alloc(24);
    await handle.read(head, 0, 24, 0);
    return { width: head.readUInt32BE(16), height: head.readUInt32BE(20) };
  } finally {
    await handle.close();
  }
}

/**
 * Widens the character's sheet: gray is added on the chosen sides and Qwen Image 2.1 (with the
 * outpaint LoRA) draws the continuation, keeping the original where it is. The result is a new
 * sheet candidate; "この設定画にする" in the app keeps it.
 */
async function runCharacterOutpaint(job: MediaJob): Promise<void> {
  const character = await getCharacter(job.options.comicCharacterId ?? "");
  if (!character) throw new Error("その登場人物はいません");
  const source = character.refImage;
  if (!source || !(await exists(source)))
    throw new Error("先に設定画を決めてください（広げる元になります）");
  const sides = job.options.comicOutpaint;
  if (!sides || sides.top + sides.bottom + sides.left + sides.right <= 0)
    throw new Error("広げる方向を選んでください");
  for (const file of [OUTPAINT_LORA, VIGGLE_TURBO_LORA]) {
    if (!(await exists(path.join(QWEN_LORA_DIR, file))))
      throw new Error(`広げるための部品が見つかりません（${file}）`);
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "outpaint-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });

  // Gray on the chosen sides, then about 1.2 megapixels in multiples of 16.
  const padded = path.join(work, "padded.png");
  const { top, bottom, left, right } = sides;
  const budget = 1_200_000;
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-i",
      source,
      "-vf",
      `pad=w='trunc(iw*(1+${left}+${right}))':h='trunc(ih*(1+${top}+${bottom}))':x='trunc(iw*${left})':y='trunc(ih*${top})':color=0x808080,` +
        `scale=w='trunc(iw*sqrt(${budget}/(iw*ih))/16)*16':h='trunc(ih*sqrt(${budget}/(iw*ih))/16)*16'`,
      padded,
    ],
    { timeoutMs: 60_000 },
  );
  const size = await pngSize(padded);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, "設定画を広げています");
  const request = path.join(work, "request.json");
  const note = job.request ? ` ${job.request}` : "";
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...qwenTurboSettings([OUTPAINT_LORA]),
        prompt:
          "Outpaint the image: replace the solid gray areas with a seamless continuation of the scene, " +
          "keeping the existing picture exactly as it is, the same character, face, hair and art style." +
          note,
        image_refs: [padded],
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
  if (!image) throw new Error("広げた絵ができませんでした");
  const file = path.join(dir, `sheet-outpaint-${randomBytes(3).toString("hex")}.png`);
  await runTool(VOICE_FFMPEG, ["-y", "-loglevel", "error", "-i", image, file], {
    timeoutMs: 60_000,
  });
  await fs.rm(work, { recursive: true, force: true });

  job.file = file;
  job.mime = "image/png";
  job.comic = { seriesId: "", character: character.name, characterId: character.id, view: "front" };
}

/**
 * A full-body front sheet drawn from the photo the character was made from: Qwen Image 2.1 with
 * the Viggle Turbo LoRA keeps the face, hair, body and clothes of the photo (about 2-3 minutes).
 */
const PHOTO_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Full-body standing photograph of the same person as in the reference photo, front view, looking at the viewer, plain white background, " +
    "the whole body from head to feet, photorealistic. Keep the face, hairstyle, hair color, skin tone, body shape and clothes exactly the same as the photo.",
  anime:
    "Redraw the person in the reference photo as an anime character: full body, front view, looking at the viewer, plain white background, " +
    "from head to feet, clean line art and cel shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo, so the person is clearly recognizable.",
  illustration:
    "Redraw the person in the reference photo as a soft colored illustration: full body, front view, plain white background, from head to feet, " +
    "gentle shading. Keep the same face shape, eye color, hair color and hairstyle, skin tone, body shape and clothes as the photo.",
};

/** The same character sheet, when the reference is an anime picture (the character's design is kept as it is). */
const ANIME_SOURCE_STYLE_PROMPT: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Re-imagine the anime character in the reference picture as a real person in a live-action photograph: full-body standing, front view, looking at the viewer, plain white background, " +
    "from head to feet, photorealistic, real human skin and natural body proportions. Keep the character's hair color and hairstyle, eye color, outfit design and colors, accessories and markings exactly as in the reference, like a faithful live-action adaptation.",
  anime:
    "Redraw the anime character in the reference picture as a clean character sheet: full body, front view, looking at the viewer, plain white background, from head to feet, " +
    "clean line art and cel shading. Keep the exact character design of the reference: face, eye color and shape, hair color and hairstyle, outfit, accessories, colors and proportions.",
  illustration:
    "Redraw the character in the reference picture as a soft colored illustration: full body, front view, plain white background, from head to feet, gentle shading. " +
    "Keep the exact character design of the reference: face, eyes, hair, outfit, accessories and colors.",
};

const ANIME_SOURCE_IDENTITY_PROMPT =
  " Change the rendering only; keep the individual design of reference 1: hair color and hairstyle silhouette, eye color, outfit, accessories, visible marks. Do not invent a different character. When the result must be a photograph, give the face natural human proportions while keeping the character recognizable.";

const RANDOM_BODIES = ["slender", "average", "athletic", "soft and curvy", "petite"];
const RANDOM_OUTFITS = [
  "a white blouse and a navy pleated skirt",
  "a casual hoodie and jeans",
  "a simple summer dress",
  "a knit sweater and a long skirt",
  "a denim jacket over a T-shirt with slim pants",
  "a plain blazer, a shirt and tailored trousers",
  "a trench coat over a dress",
  "a cardigan, a camisole and a mini skirt",
  "a sporty tracksuit",
  "a floral one-piece dress",
];
const RANDOM_SHOES = [
  "white sneakers",
  "loafers",
  "ankle boots",
  "flat sandals",
  "low-heeled pumps",
];

/**
 * When the reference shows only a face, there is nothing to copy for the body and clothes: they are chosen at random
 * (a different pick each time), an ordinary everyday outfit; the face, hair and skin stay exactly as in the reference.
 */
export function randomBodyPrompt(random: () => number = Math.random, outfit?: string): string {
  const pick = <T>(list: readonly T[]): T =>
    list[Math.min(list.length - 1, Math.floor(random() * list.length))]!;
  // An outfit that is already set (her group's or category's costume) wins over a random one.
  const wearing = outfit?.trim()
    ? outfit.trim()
    : `${pick(RANDOM_OUTFITS)} and ${pick(RANDOM_SHOES)}`;
  return (
    ` The reference shows only the face, with no body or clothes. Draw a natural adult full body for her: ${pick(RANDOM_BODIES)} build, wearing ${wearing}. ` +
    (outfit?.trim()
      ? "The outfit is her group's costume; the build was chosen at random because the reference has no body. "
      : "These were chosen at random because the reference has no body. ") +
    FACE_ONLY_PROPORTIONS +
    "Take only the face, hair and skin tone from the reference and keep them exactly."
  );
}

/**
 * A face close-up fills its whole frame, so the editor tends to draw the head as big as in the close-up, on a small
 * body. The sentence asks for the real proportions of a full body: the head is a small part of the height.
 */
export const FACE_ONLY_PROPORTIONS =
  "The reference is a close-up, so the face looks large in it: do NOT keep that scale. Draw the whole body in natural adult proportions, about 7 to 8 heads tall, with the head small compared to the body, narrow shoulders, long neck and long legs, and show the full body from head to feet. ";

/** Whether none of a character's reference pictures shows the body. Read from the pictures once, then remembered. */
async function referenceIsFaceOnly(character: LibraryCharacter): Promise<boolean> {
  if (character.photoFaceOnly !== undefined) return character.photoFaceOnly;
  const files = (character.photoRefs ?? (character.photoRef ? [character.photoRef] : [])).slice(
    0,
    4,
  );
  const found: Array<Awaited<ReturnType<typeof analyzePhoto>>> = [];
  for (const file of files) {
    if (await exists(file)) found.push(await analyzePhoto(file));
  }
  const read = found.filter((one): one is NonNullable<typeof one> => Boolean(one));
  // Pictures that could not be read are not guessed about.
  const faceOnly = read.length > 0 && !read.some((one) => (one.kind ?? "").includes("全身"));
  await saveCharacter({ ...character, photoFaceOnly: faceOnly, updatedAt: Date.now() });
  return faceOnly;
}

/** The style sentence for a character's sheet: depends on what the reference is (photo / anime) and what is wanted. */
export function photoStylePrompt(
  source: "photo" | "anime" | undefined,
  style: "photo" | "anime" | "illustration",
): string {
  return source === "anime" ? ANIME_SOURCE_STYLE_PROMPT[style] : PHOTO_STYLE_PROMPT[style];
}

export function photoIdentityPrompt(source: "photo" | "anime" | undefined): string {
  return source === "anime" ? ANIME_SOURCE_IDENTITY_PROMPT : PHOTO_IDENTITY_PROMPT;
}

const PHOTO_IDENTITY_PROMPT =
  " Change the rendering style only; preserve the individual design of reference 1. Keep the distinctive face outline, brow shape, eye spacing and tilt, nose and mouth shape, hairline, hairstyle silhouette, eye and hair colors, visible marks, glasses and accessories. Do not replace the face with a generic attractive model or generic anime face. Avoid enlarging the eyes or narrowing the jaw beyond what the requested style requires. Reference 1 is authoritative when other references disagree.";

/** The Wan2GP settings of the three editors for a photo (the stronger ones are slower). */
function photoEngineSettings(engine: "fast" | "precise" | "edit2509"): {
  settings: Record<string, unknown>;
  needsLora: boolean;
} {
  if (engine === "fast") return { settings: qwenTurboSettings(), needsLora: true };
  if (engine === "edit2509") {
    return {
      settings: {
        model_type: "qwen_image_edit_plus_20B_nunchaku_r128_int4",
        num_inference_steps: 4,
        guidance_scale: 1,
      },
      needsLora: false,
    };
  }
  // Qwen Image 2.1 at its own step count (about 5 minutes), without the speed LoRA: closer to the photo.
  return {
    settings: {
      model_type: QWEN_EDIT_MODEL,
      num_inference_steps: 30,
      guidance_scale: 4,
      negative_prompt: " ",
    },
    needsLora: false,
  };
}

/** What each kind of photo is used for, in English (for the editor). */
const PHOTO_ROLE_EN: Record<string, string> = {
  顔: "the face and skin",
  髪: "the hair (color and hairstyle)",
  服: "the clothes and outfit",
  体型: "the body shape and proportions",
  雰囲気: "the overall mood and color palette",
  小物: "the accessories",
};

const STYLE_TAIL: Record<"photo" | "anime" | "illustration", string> = {
  photo:
    "Draw the same person as a realistic photograph: full body, standing, front view, looking at the viewer, plain white background, from head to feet.",
  anime:
    "Draw the same person as an anime character: full body, standing, front view, looking at the viewer, plain white background, from head to feet, clean line art and cel shading.",
  illustration:
    "Draw the same person as a soft colored illustration: full body, standing, front view, plain white background, from head to feet, gentle shading.",
};

/** The editor's instruction when several photos are given: blend them, or take one part from each. */
export function multiPhotoPrompt(
  style: "photo" | "anime" | "illustration",
  mode: "subject" | "blend" | "roles",
  roles: string[],
  count: number,
): string {
  const intro =
    mode === "subject"
      ? `Create ONE adult character. All ${count} reference pictures show the SAME target person. Use reference picture 1 as the identity anchor for face, hair and skin. Other pictures provide only visible details of this same person, such as body shape, clothing and accessories. Never combine features from bystanders or background objects.`
      : mode === "roles"
        ? `Create ONE adult person from ${count} reference pictures. ` +
          Array.from(
            { length: count },
            (_, i) =>
              `Use reference picture ${i + 1} only for ${PHOTO_ROLE_EN[roles[i] ?? ""] ?? PHOTO_ROLE_EN["顔"]}.`,
          ).join(" ")
        : `Create ONE new adult person by blending the people in the ${count} reference pictures: mix their faces, eyes, hair and style evenly, so that this is a new person who resembles all of them a little.`;
  return `${intro} ${STYLE_TAIL[style]}`;
}

async function runSheetFromPhoto(job: MediaJob, character: LibraryCharacter): Promise<void> {
  const style = character.photoStyle ?? "photo";
  const sources =
    (character.photoRefs ?? []).length > 1
      ? (
          await Promise.all(
            (character.photoRefs ?? []).map(async (file) => ((await exists(file)) ? file : "")),
          )
        ).filter(Boolean)
      : [];
  const several = sources.length > 1;
  // Creation places the identity anchor first and the most complementary analyzed photos next.
  const engine = character.photoEngine ?? "precise";
  // 2509 is documented for 1–3 inputs; the installed 2.1 backend supports up to 10.
  const editorSources = sources.slice(0, engine === "edit2509" ? 3 : 10);
  const traits = character.photoTraits ? ` Details to keep exactly: ${character.photoTraits}.` : "";
  // A face-only reference has no body to copy: another picture with a body is used when there is one (above),
  // otherwise the body and clothes are chosen at random.
  const randomBody = (await referenceIsFaceOnly(character))
    ? randomBodyPrompt(Math.random, character.spec?.outfit)
    : "";
  // A pose the user asked for replaces the plain standing pose of the sheet.
  const pose = await poseTags(job.options.comicSheetPose);
  const poseLine = pose
    ? ` Draw the character in this pose instead of a plain standing pose: ${pose}. Keep the face, hair and clothes of the source exactly.`
    : "";
  if (engine === "ipadapter") {
    await runSheetByIpAdapter(
      job,
      character,
      sources.length ? sources : [character.photoRef as string],
      photoStylePrompt(character.photoSource, style) +
        photoIdentityPrompt(character.photoSource) +
        randomBody +
        traits +
        poseLine,
    );
    return;
  }
  const picked = photoEngineSettings(engine);
  const high = character.photoQuality === "high";
  await runSheetByEdit(job, character, {
    source: editorSources[0] ?? (character.photoRef as string),
    extraSources: several ? editorSources.slice(1) : [],
    identityReferenceLimit:
      !several || (character.photoMode ?? "subject") === "subject"
        ? engine === "edit2509"
          ? 3
          : 10
        : undefined,
    adult: false,
    stage: several
      ? `${sources.length}枚を解析し、選んだ${editorSources.length}枚から設定画を作っています`
      : engine === "precise"
        ? "写真から設定画を作っています（高精度・約5分）"
        : "写真から設定画を作っています",
    prompt:
      (several
        ? multiPhotoPrompt(
            style,
            character.photoMode ?? "subject",
            character.photoRoles ?? [],
            editorSources.length,
          )
        : photoStylePrompt(character.photoSource, style)) +
      (character.photoSource === "anime" && several
        ? ` The reference pictures are anime pictures. ${ANIME_SOURCE_STYLE_PROMPT[style]}`
        : "") +
      (!several || (character.photoMode ?? "subject") === "subject"
        ? photoIdentityPrompt(character.photoSource)
        : "") +
      randomBody +
      traits +
      poseLine,
    settings: picked.settings,
    needsLora: picked.needsLora,
    upscale: high ? (style === "photo" ? "photo" : "anime") : undefined,
  });
}

/** Ordinary Plus uses visual embeddings; it does not use FaceID or a voice/personality model. */
async function runSheetByIpAdapter(
  job: MediaJob,
  character: LibraryCharacter,
  sources: string[],
  prompt: string,
): Promise<void> {
  if (character.photoMode === "roles") {
    throw new Error(
      "IP-Adapter Plus は部位ごとの指定に対応していません。Qwen を選ぶか、同じ人物の参照に変更してください。",
    );
  }
  const primary = sources[0];
  if (!primary) {
    throw new Error("参照画像が見つかりません。");
  }
  const assets = path.join(MEDIA_MODEL_DIR, "image", "ip-adapter-plus");
  for (const filename of [
    "models/image_encoder/config.json",
    "models/image_encoder/model.safetensors",
    "sdxl_models/ip-adapter-plus_sdxl_vit-h.safetensors",
  ]) {
    if (!(await exists(path.join(assets, filename)))) {
      throw new Error(
        "IP-Adapter Plus のモデルがありません。PCで scripts/media/setup_ip_adapter.py を実行して導入してください。",
      );
    }
  }
  const style = character.photoStyle ?? "photo";
  const model = SDXL_MODELS[style === "photo" ? "realvis" : "animagine"];
  if (!(await exists(model.file))) {
    throw new Error("IP-Adapter Plus に使う画像モデルが見つかりません。");
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, `ipadapter-work-${job.id}`);
  await fs.mkdir(work, { recursive: true });
  const file = path.join(dir, `sheet-front-${randomBytes(3).toString("hex")}.png`);
  try {
    let references = sources.slice(0, character.photoMode === "blend" ? 4 : 1);
    if (character.photoMode !== "blend") {
      const face = path.join(work, "identity-face.png");
      if (await createFaceReference(primary, face)) references = [...references, face];
    } else {
      prompt = multiPhotoPrompt(style, "blend", [], references.length);
    }
    const request = path.join(work, "request.json");
    await fs.writeFile(
      request,
      JSON.stringify({
        assets,
        model: model.file,
        references,
        prompt: `${model.quality}, ${prompt}`,
        negative: model.negative,
        steps: model.steps,
        cfg: model.cfg,
        scale: 0.65,
        output: file,
      }),
    );
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
    markStage(job, "参照画像から設定画を作っています（IP-Adapter Plus）");
    const packageRoot = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
    if (!packageRoot) {
      throw new Error("OpenClaw の実行場所を確認できません。");
    }
    await runTool(
      WAN2GP_PYTHON,
      [path.join(packageRoot, "scripts", "media", "ip_adapter_render.py"), request],
      {
        timeoutMs: 60 * 60_000,
        onLine: (line) => {
          const step = /^STEP (\d+\/\d+)$/.exec(line);
          if (step) {
            markStage(job, `設定画を作っています（IP-Adapter Plus ${step[1]}）`);
          }
        },
      },
    );
    if (!(await exists(file))) {
      throw new Error("IP-Adapter Plus の設定画ができませんでした。");
    }
    if (character.photoQuality === "high") {
      markStage(job, "設定画を高画質にしています");
      const big = path.join(work, "upscaled.png");
      await runTool(
        REALESRGAN_EXE,
        [
          "-i",
          file,
          "-o",
          big,
          "-n",
          style === "photo" ? "realesrgan-x4plus" : "realesrgan-x4plus-anime",
          "-s",
          "4",
          "-t",
          "256",
          "-m",
          path.join(path.dirname(REALESRGAN_EXE), "models"),
        ],
        { timeoutMs: 20 * 60_000 },
      );
      await runTool(
        VOICE_FFMPEG,
        ["-y", "-loglevel", "error", "-i", big, "-vf", "scale=iw/2:-2:flags=lanczos", file],
        { timeoutMs: 120_000 },
      );
    }
    job.file = file;
    job.mime = "image/png";
    job.comic = {
      seriesId: job.options.comicSeriesId ?? "",
      character: character.name,
      characterId: character.id,
      view: "front",
    };
  } finally {
    await fs.rm(work, { recursive: true, force: true });
  }
}

/** A front sheet made by editing a picture with Qwen Image 2.1 (Viggle Turbo): a photo, or the standing picture itself. */
async function runSheetByEdit(
  job: MediaJob,
  character: LibraryCharacter,
  params: {
    source: string;
    extraSources?: string[];
    identityReferenceLimit?: number;
    prompt: string;
    adult: boolean;
    stage: string;
    settings?: Record<string, unknown>;
    needsLora?: boolean;
    upscale?: "anime" | "photo";
  },
): Promise<void> {
  if ((params.needsLora ?? true) && !(await exists(path.join(QWEN_LORA_DIR, VIGGLE_TURBO_LORA)))) {
    throw new Error(`絵を編集するための部品が見つかりません（${VIGGLE_TURBO_LORA}）`);
  }
  const dir = characterAssetDir(character.id);
  const work = path.join(dir, "edit-work");
  await fs.rm(work, { recursive: true, force: true });
  await fs.mkdir(work, { recursive: true });
  let imageRefs = [params.source, ...(params.extraSources ?? [])];
  let prompt = params.prompt;
  let stage = params.stage;
  if (params.identityReferenceLimit) {
    const face = path.join(work, "identity-face.png");
    if (await createFaceReference(params.source, face)) {
      imageRefs = [...imageRefs.slice(0, params.identityReferenceLimit - 1), face];
      prompt +=
        " The final reference is a close-up cropped from reference 1, showing the SAME identity. Use it for fine facial details only; preserve the full-body layout and clothing from reference 1.";
    } else {
      stage += "（顔拡大なし・元画像を参照）";
    }
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, stage);
  const request = path.join(work, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: work,
      settings: {
        ...(params.settings ?? qwenTurboSettings()),
        prompt,
        image_refs: imageRefs,
        video_prompt_type: "KI",
        resolution: "832x1216",
        seed: -1,
      },
    }),
  );
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    timeoutMs: 60 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const image = produced.find((f) => /\.(png|jpe?g|webp)$/i.test(f));
  if (!image) throw new Error("設定画ができませんでした");
  const file = path.join(
    dir,
    `sheet-front${params.adult ? "-adult" : ""}-${randomBytes(3).toString("hex")}.png`,
  );
  if (params.upscale) {
    // High quality: Real-ESRGAN enlarges it 4 times (the anime model for drawings), then it is brought to twice the size.
    markStage(job, "高画質にしています（拡大と整え）");
    const big = path.join(work, "upscaled.png");
    await runTool(
      REALESRGAN_EXE,
      [
        "-i",
        image,
        "-o",
        big,
        "-n",
        params.upscale === "photo" ? "realesrgan-x4plus" : "realesrgan-x4plus-anime",
        "-s",
        "4",
        "-t",
        "256",
        "-m",
        path.join(path.dirname(REALESRGAN_EXE), "models"),
      ],
      { timeoutMs: 20 * 60_000 },
    );
    await runTool(
      VOICE_FFMPEG,
      ["-y", "-loglevel", "error", "-i", big, "-vf", "scale=iw/2:-2:flags=lanczos", file],
      { timeoutMs: 120_000 },
    );
  } else {
    await runTool(VOICE_FFMPEG, ["-y", "-loglevel", "error", "-i", image, file], {
      timeoutMs: 60_000,
    });
  }
  await fs.rm(work, { recursive: true, force: true });
  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: job.options.comicSeriesId ?? "",
    character: character.name,
    characterId: character.id,
    view: "front",
    ...(params.adult ? { adult: true } : {}),
  };
}

/**
 * One sample picture of the sample factory: the character's standing picture (or the adult base made
 * from it) is edited with Qwen Image 2.1 (Viggle Turbo) so that only the chosen setting changes.
 * The result is kept in the factory folder and listed in its index.
 */
async function runCharacterSample(job: MediaJob): Promise<void> {
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
async function runSampleBatch(job: MediaJob): Promise<void> {
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

const SD_SCRIPTS_DIR = "D:\\media-tools\\sd-scripts";
const SD_SCRIPTS_PYTHON = path.join(SD_SCRIPTS_DIR, "venv", "Scripts", "python.exe");
/**
 * Qwen Image 2.1 (7B, already installed) with the Viggle Turbo LoRA: 6 steps instead of ~30, so a
 * reference picture becomes a new view in about 25 s. The LoRA files sit in Wan2GP's loras folder.
 */
const QWEN_EDIT_MODEL = "qwen_image_21_7B";
const QWEN_LORA_DIR = "D:\\media-tools\\Wan2GP\\loras\\qwen21";
const VIGGLE_TURBO_LORA = "Qwen-Image-2.1-viggle-turbo-v0.3-6step-lora-r256.safetensors";
const OUTPAINT_LORA = "qwen-image-2.1-outpaint-v2.safetensors";
/** The Wan2GP settings that turn Qwen Image 2.1 into the 6-step editor (plus extra LoRAs, if any). */
function qwenTurboSettings(extraLoras: string[] = []): Record<string, unknown> {
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
async function runCharacterLora(job: MediaJob): Promise<void> {
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

/** Draws one panel of a finished episode again (new seed) and re-typesets the episode. */
async function runPanelRedraw(job: MediaJob, series: ComicSeries): Promise<void> {
  const target = await getMediaJob(job.options.comicTargetJobId ?? "");
  if (!target || target.kind !== "comic" || !target.comic?.episodeNo) {
    throw new Error("描き直す話が見つかりません");
  }
  const dir = episodeDir(target.id);
  const files = JSON.parse(
    await fs.readFile(path.join(dir, "episode.json"), "utf-8"),
  ) as EpisodeFiles;
  const index = (job.options.comicPanel ?? 0) - 1;
  const panel = files.script.panels[index];
  if (!panel) throw new Error("そのコマはありません");
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = path.join(dir, `panel-${String(index + 1).padStart(2, "0")}.png`);
  files.panels[index] = await drawPanel(
    job,
    series.cast,
    panel,
    files.aspects[index] ?? "square",
    files.sexual,
    file,
  );
  await fs.writeFile(path.join(dir, "episode.json"), JSON.stringify(files, null, 2));
  const parts = await composeEpisode(job, dir, series, files);
  target.files = parts;
  target.file = parts[0];
  await saveJobMeta(target).catch(() => undefined);
  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: series.id,
    episodeNo: files.episodeNo,
    targetJobId: target.id,
    panel: index + 1,
  };
}

/** The look (English tags) and profile for a character entered in the creator. */
export async function writeCharacterLook(
  name: string,
  spec: CharacterSpec,
): Promise<{ look: string; profile: string }> {
  // What each chosen option looks like, in words the local model understands (slang, positions, outfits).
  const explained: string[] = [];
  const written = await loadSampleDescriptions();
  for (const id of characterOptionIds(spec.lookOptions)) {
    const row = written[`opt:${id}`];
    const subject = row ? await resolveSampleSubject(`opt:${id}`) : undefined;
    if (row && subject) explained.push(`- ${subject.label}：${row.ja}（${row.en}）`);
  }
  const user =
    `名前: ${name}\n${specSummary(spec)}` +
    (explained.length > 0
      ? `\n選んだ外見オプションの説明（かならず絵に反映する）:\n${explained.join("\n")}`
      : "");
  const system = creatorSystemPrompt();
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const reply = working
        ? await askGemma(system, user, 800)
        : await askScriptWriter(system, user, 800);
      const parsed = parseCreatorReply(reply);
      // The words the AI wrote, her height and the options chosen in the app (exact tags, no repeats).
      const sizeTags = breastTags(spec.breastSize);
      const look = mergeLookTags(
        sizeTags ? stripBreastTags(parsed.look) : parsed.look,
        imageHeightTags(spec),
        sizeTags,
        characterOptionTags(spec.lookOptions),
      );
      return { look, profile: parsed.profile };
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`見た目を作れませんでした: ${lastError}`);
}

/**
 * The cast for a new series, written by the chat model from its premise (JSON, Japanese
 * profiles, English look tags).
 */
export async function writeComicCast(series: ComicSeries): Promise<ComicCharacter[]> {
  const user = `作品名: ${series.title}\nジャンル: ${series.genre}\n設定: ${series.premise}`;
  const system = castSystemPrompt(series.rating);
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const reply = working
        ? await askGemma(system, user, 1_500)
        : await askScriptWriter(system, user, 1_500);
      return parseCast(reply);
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`登場人物を作れませんでした: ${lastError}`);
}

/** LTX-2.3 ("audio") and H3 also need the soundtrack described; the other engines only motion. */
async function motionPromptFor(job: MediaJob): Promise<string> {
  const request = await productionRequest(job);
  if (job.options.engine === "h3") return toH3Prompt(request, job.options.durationSec ?? 5);
  return job.options.engine === "audio" ? toAudioVideoPrompt(request) : toMotionPrompt(request);
}

async function runVideoJob(job: MediaJob): Promise<void> {
  const aspect = job.options.aspect ?? "portrait";
  const [width, height] = SIZES[aspect].video;
  const production = await productionRequest(job);
  let initImage: string;
  let motion: string | undefined;
  let sexual = isSexualRequest(production);
  if (job.options.sourceImageJobId) {
    const source =
      jobs.get(job.options.sourceImageJobId) ?? (await loadJobMeta(job.options.sourceImageJobId));
    if (!source?.file || source.kind !== "image") throw new Error("元の画像が見つかりません");
    initImage = source.file;
    sexual ||= isSexualRequest(source.request);
    markStage(job, "動きの指示を英語に変換しています");
    assertAllowedRequest(job.request, source.request);
  } else if (job.options.sourceImageBase64) {
    initImage = path.join(outDir(), `${job.id}-source.png`);
    await fs.writeFile(initImage, Buffer.from(job.options.sourceImageBase64, "base64"));
    assertAllowedRequest(job.request);
  } else {
    // Text-to-video: draw the first frame with the image model, then animate it.
    markStage(job, "指示を英語のタグに変換しています");
    const tags = await imagePromptFor(job);
    assertAllowedRequest(job.request, tags);
    sexual ||= isSexualRequest(tags);
    // Written before the GPU is taken, while the chat model can still do it quickly.
    motion = await motionPromptFor(job);
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    initImage = path.join(outDir(), `${job.id}-first.png`);
    await renderImage(job, tags, sexual, initImage);
  }
  const engine = job.options.engine ?? "standard";
  motion ??= await motionPromptFor(job);
  assertAllowedRequest(job.request, motion);
  if (engine !== "standard" && engine !== "remix") {
    await runWan2gpVideo(job, engine, initImage, motion, sexual, aspect);
    return;
  }
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = await resultPath(job, ".webm");
  await runTool(
    sdCli(),
    sdcppVideoArgs(engine, {
      initImage,
      prompt: motion,
      negative: videoNegative(sexual),
      width,
      height,
      output: file,
    }),
    { onLine: stepNote("動画を作っています", job) },
  );
  job.file = file;
  job.mime = "video/webm";
  if (job.options.smooth) await smoothVideo(job);
}

/** sd.cpp arguments for a 2 s (33 frames, 16 fps) image-to-video clip. */
export function sdcppVideoArgs(
  engine: SdcppVideoEngine,
  params: {
    initImage: string;
    prompt: string;
    negative: string;
    width: number;
    height: number;
    output: string;
  },
): string[] {
  const model =
    engine === "remix"
      ? [
          "--diffusion-model",
          REMIX_LOW,
          "--high-noise-diffusion-model",
          REMIX_HIGH,
          "--steps",
          "6",
          "--high-noise-steps",
          "3",
          "--cfg-scale",
          "1",
          "--high-noise-cfg-scale",
          "1",
          "--sampling-method",
          "euler",
          "--high-noise-sampling-method",
          "euler",
          "--flow-shift",
          "5",
        ]
      : [
          "--diffusion-model",
          VIDEO_MODEL,
          "--clip_vision",
          VIDEO_CLIP_VISION,
          "--steps",
          "4",
          "--cfg-scale",
          "1",
          "--sampling-method",
          "euler_a",
        ];
  return [
    "-M",
    "vid_gen",
    ...model,
    "--vae",
    VIDEO_VAE,
    // The text encoder runs on the GPU too (offloaded between stages): 17 s instead of 31 s on CPU.
    "--t5xxl",
    VIDEO_T5,
    "-i",
    params.initImage,
    "-p",
    params.prompt,
    "-n",
    params.negative,
    "-W",
    String(params.width),
    "-H",
    String(params.height),
    "--video-frames",
    "33",
    "--fps",
    "16",
    "--offload-to-cpu",
    "--diffusion-fa",
    // Tiles above the 256 px default need >7 GB for the Wan VAE on this card.
    "--vae-tiling",
    "-s",
    "-1",
    "-o",
    params.output,
  ];
}

/** Doubles the frame rate with RIFE (16 -> 32 fps); the clip keeps its length and motion. */
async function smoothVideo(job: MediaJob): Promise<void> {
  if (!job.file) return;
  markStage(job, "なめらかにしています");
  const smoothed = path.join(outDir(), `${job.id}-smooth.mp4`);
  await runTool(WAN2GP_PYTHON, [RIFE_SCRIPT, job.file, smoothed, "2"], {
    cwd: path.dirname(RIFE_SCRIPT),
    timeoutMs: 10 * 60_000,
  });
  const file = await resultPath(job, ".mp4");
  await fs.rename(smoothed, file);
  await fs.rm(job.file, { force: true });
  job.file = file;
  job.mime = "video/mp4";
}

// Wan2GP engines run headless through its Python API (openclaw_job.py); nothing is served on the network.
const WAN2GP_DIR = "D:\\media-tools\\Wan2GP";
const WAN2GP_PYTHON = path.join(WAN2GP_DIR, "venv", "Scripts", "python.exe");
const WAN2GP_RUNNER = path.join(WAN2GP_DIR, "openclaw_job.py");
const QWEN_TURBO_RUNNER = path.resolve("scripts/media/external/qwen_image_turbo_job.py");
const WAN2GP_SIZES: Record<NonNullable<MediaOptions["aspect"]>, string> = {
  square: "624x624",
  portrait: "480x832",
  landscape: "832x480",
};
const WAN2GP_ENGINES: Record<
  Exclude<VideoEngine, SdcppVideoEngine>,
  {
    modelType: string;
    fps: number;
    frameStep: number;
    maxSec: number;
    label: string;
    /** Shortest clip the model makes; lengths grow from it in frameStep increments. */
    minFrames?: number;
    /** Needs most of the 64 GB RAM, so the CPU worker LLMs are stopped first. */
    freesRam?: boolean;
  }
> = {
  long: {
    modelType: "i2v_2_2_Enhanced_Lightning_v2_svi2pro",
    fps: 16,
    frameStep: 4,
    maxSec: 30,
    label: "長い動画",
  },
  audio: {
    modelType: "ltx2_distilled_gguf_q4_k_m",
    fps: 24,
    frameStep: 8,
    maxSec: 10,
    label: "音付き動画",
  },
  fast: { modelType: "ti2v_2_2_fastwan", fps: 24, frameStep: 4, maxSec: 5, label: "高速動画" },
  // MiniMax H3 FL2VA pruned 20B with the 8-step PDD heads: video and sound together; ~45 GB of
  // weights stream from RAM (Wan2GP profile 4). 5 s at 480x832 took ~9 min on the 4060.
  h3: {
    modelType: "minimax_h3_fl2va_pruned_pdd",
    fps: 24,
    frameStep: 17,
    minFrames: 107,
    maxSec: 10,
    label: "音付き実写",
    freesRam: true,
  },
};

/** Frame count the model accepts: 1 + a multiple of its frame step, or minFrames + a multiple. */
export function wan2gpFrames(
  seconds: number,
  fps: number,
  frameStep: number,
  maxSec: number,
  minFrames?: number,
): number {
  const clamped = Math.min(maxSec, Math.max(2, seconds));
  if (minFrames)
    return minFrames + frameStep * Math.max(0, Math.ceil((clamped * fps - minFrames) / frameStep));
  return Math.ceil((clamped * fps) / frameStep) * frameStep + 1;
}

async function runWan2gpVideo(
  job: MediaJob,
  engine: Exclude<VideoEngine, SdcppVideoEngine>,
  initImage: string,
  prompt: string,
  sexual: boolean,
  aspect: NonNullable<MediaOptions["aspect"]>,
): Promise<void> {
  const spec = WAN2GP_ENGINES[engine];
  const workDir = path.join(outDir(), `${job.id}-w2g`);
  await fs.mkdir(workDir, { recursive: true });
  const request = path.join(workDir, "request.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      output_dir: workDir,
      settings: {
        model_type: spec.modelType,
        prompt,
        negative_prompt: videoNegative(sexual),
        image_prompt_type: "S",
        image_start: initImage,
        resolution: WAN2GP_SIZES[aspect],
        video_length: wan2gpFrames(
          job.options.durationSec ?? 5,
          spec.fps,
          spec.frameStep,
          spec.maxSec,
          spec.minFrames,
        ),
        seed: -1,
        // Wan2GP's own RIFE pass keeps the soundtrack of the audio engine in sync.
        ...(job.options.smooth ? { temporal_upsampling: "rife2" } : {}),
      },
    }),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  if (spec.freesRam) await stopCpuWorkers();
  markStage(job, `${spec.label}を作っています（初回はモデルのダウンロードに時間がかかります）`);
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [WAN2GP_RUNNER, request], {
    cwd: WAN2GP_DIR,
    // The first run of an engine downloads tens of GB of weights.
    timeoutMs: 3 * 60 * 60_000,
    onLine: (line) => {
      const progress = /^PROGRESS (\d+) (.*)$/.exec(line);
      if (progress) job.stage = `${spec.label}を作っています ${progress[1]}%`;
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (result) produced = JSON.parse(result) as string[];
    },
  });
  const video = produced.find((file) => /\.(mp4|webm|mkv)$/i.test(file));
  if (!video) throw new Error("Wan2GP が動画を出力しませんでした");
  const ext = path.extname(video).toLowerCase();
  const file = await resultPath(job, ext);
  await fs.rename(video, file);
  await fs.rm(workDir, { recursive: true, force: true });
  job.file = file;
  job.mime = ext === ".webm" ? "video/webm" : ext === ".mkv" ? "video/x-matroska" : "video/mp4";
}

async function runMusicJob(job: MediaJob): Promise<void> {
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
async function runQualityPass(job: MediaJob): Promise<void> {
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

// ---------- Queue ----------

const jobs = new Map<string, MediaJob>();
const queue: string[] = [];
let working = false;

function metaPath(id: string): string {
  return path.join(outDir(), `${id}.json`);
}

async function saveJobMeta(job: MediaJob): Promise<void> {
  const { sourceImageBase64: _drop, ...options } = job.options;
  await fs.writeFile(metaPath(job.id), JSON.stringify({ ...job, options }));
}

async function loadJobMeta(id: string): Promise<MediaJob | undefined> {
  if (!/^[a-f0-9]{12}$/.test(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(metaPath(id), "utf-8")) as MediaJob;
  } catch {
    return undefined;
  }
}

async function pruneResults(): Promise<void> {
  const failed = (await listMediaJobs(10_000)).filter((job) => job.status === "error");
  for (const job of failed.slice(KEEP_FAILED)) {
    await fs.rm(metaPath(job.id), { force: true });
    if (job.file) await fs.rm(job.file, { force: true });
  }
}

async function processQueue(): Promise<void> {
  if (working) return;
  working = true;
  try {
    for (let id = queue.shift(); id; id = queue.shift()) {
      const job = jobs.get(id);
      if (!job) continue;
      currentJob = job;
      job.status = "running";
      try {
        // Not needed while the GPU is already ours (the chat model is down then).
        if (job.chat && !(await exists(pauseFlag()))) {
          job.stage = "チャットの返事が終わるのを待っています";
          await waitForChatTurnEnd();
        }
        if (job.cancelRequested) throw new Error("中止しました");
        job.startedAt = Date.now();
        await fs.mkdir(outDir(), { recursive: true });
        // ── 作業内容を stage に設定（Android の「何をしているか」表示に使う）──
        const kindLabel: Record<string, string> = {
          image: "画像を生成しています",
          manga: "漫画を描いています",
          comic: "コミックエピソードを作っています",
          voice: "ボイスを合成しています",
          motion: "モーションコミックを作っています",
          faceswap: "顔を入れ替えています",
          video: "動画を生成しています",
          music: "音楽を生成しています",
        };
        if (!job.stage || job.stage === "チャットの返事が終わるのを待っています") {
          job.stage = kindLabel[job.kind] ?? `${job.kind} を処理しています`;
        }
        if (job.kind === "image") await runImageJob(job);
        else if (job.kind === "manga") await runMangaJob(job);
        else if (job.kind === "comic") await runComicJob(job);
        else if (job.kind === "voice") await runVoiceJob(job);
        else if (job.kind === "motion") await runMotionJob(job);
        else if (job.kind === "faceswap") await runFaceSwapJob(job);
        else if (job.kind === "video") await runVideoJob(job);
        else await runMusicJob(job);
        await runQualityPass(job);
        job.status = "done";
        markStage(job, "完成");
      } catch (err) {
        job.status = "error";
        job.error = String(err instanceof Error ? err.message : err).slice(0, 600);
        job.stage = undefined;
      } finally {
        currentJob = undefined;
        job.finishedAt = Date.now();
        job.durationSec = Math.round((job.finishedAt - (job.startedAt ?? job.finishedAt)) / 1000);
        job.options.sourceImageBase64 = undefined;
        if (job.cancelRequested) {
          // Deleted while running: nothing of it is kept.
          jobs.delete(job.id);
          if (job.file) await fs.rm(job.file, { force: true }).catch(() => undefined);
        } else {
          await saveJobMeta(job).catch(() => undefined);
          for (const listener of finishedListeners) {
            void Promise.resolve()
              .then(() => listener(job))
              .catch(() => undefined);
          }
        }
        // Hand the GPU back between jobs only when nothing else is waiting. A warm image worker
        // gets a short grace period so a follow-up image skips the start-up — but not after a
        // job asked for in chat: the conversation needs its model back to go on.
        const nextMode = resolveGpuMode(queue[0] ? jobs.get(queue[0])?.options.gpuMode : undefined);
        const modeChanged = nextMode !== resolveGpuMode(job.options.gpuMode);
        if (
          (queue.length === 0 || modeChanged) &&
          !(
            imageSlots[0]!.child &&
            !modeChanged &&
            !job.chat &&
            (await waitForMoreJobs(IMAGE_GRACE_MS))
          )
        ) {
          stopImageWorker();
          await releaseGpu();
        }
      }
    }
    await pruneResults().catch(() => undefined);
  } finally {
    working = false;
    if (queue.length === 0) {
      stopImageWorker();
      await releaseGpu();
    }
  }
}

type FinishedListener = (job: MediaJob) => void | Promise<void>;
const finishedListeners = new Set<FinishedListener>();

/** Calls `listener` with every job that ends, done or failed (not with deleted ones). */
export function onMediaJobFinished(listener: FinishedListener): () => void {
  finishedListeners.add(listener);
  return () => finishedListeners.delete(listener);
}

/** Real-person checkpoints to use when the switch says "photo", best first. */
const PHOTO_MODEL_CANDIDATES = ["asianreal", "cyberreal", "lustify"] as const;

/**
 * Makes the picture models of a job match the style switch: with "photo" an anime model is replaced by a real-person
 * model, with "anime" a real-person model is replaced by WAI. Models that can draw either (Z-Image, Qwen ...) stay.
 * Only the kinds that draw pictures get a model when none was chosen; "auto" changes nothing.
 */
export async function applyGenStyle(
  kind: MediaKind,
  options: MediaOptions,
  style: GenStyle,
  fileExists: (file: string) => Promise<boolean> = exists,
): Promise<MediaOptions> {
  if (style === "auto") return options;
  const swap = async (
    model: ImageModel | undefined,
    drawsPictures: boolean,
  ): Promise<ImageModel | undefined> => {
    if (model === undefined && !drawsPictures) return undefined;
    const effective: ImageModel = model ?? "wai";
    if (isWanImageModel(effective)) return model;
    const isPhoto = effective === "anima" ? false : sdxlSpecOf(effective as SdxlModel).photo;
    if (style === "photo" && !isPhoto) {
      for (const candidate of PHOTO_MODEL_CANDIDATES)
        if (await fileExists(SDXL_MODELS[candidate].file)) return candidate;
      return model;
    }
    if (style === "anime" && isPhoto) return "wai";
    return model;
  };
  const drawsPictures = kind === "image" || kind === "manga" || kind === "comic";
  const imageModel = await swap(options.imageModel, drawsPictures);
  const motionImageModel = options.motionImageModel
    ? await swap(options.motionImageModel, false)
    : undefined;
  return {
    ...options,
    ...(imageModel ? { imageModel } : {}),
    ...(motionImageModel ? { motionImageModel } : {}),
  };
}

export async function startMediaJob(
  kind: MediaKind,
  request: string,
  options: MediaOptions,
  origin: { chat?: { partnerId?: string } } = {},
): Promise<MediaJob> {
  // A job that did not choose a GPU mode runs in the default one (both GPUs), wherever it was started from.
  options = { ...options, gpuMode: resolveGpuMode(options.gpuMode) };
  // The style switch (anime / photo) decides which kind of picture model draws.
  options = await applyGenStyle(kind, options, await readGenStyle());
  const inheritedId = options.sceneState?.inheritJobId ?? options.branch?.parentJobId;
  if (inheritedId) {
    const source = await getMediaJob(inheritedId);
    if (source) {
      options = {
        ...options,
        characterLock: options.characterLock ?? source.options.characterLock,
        sceneState: {
          ...source.options.sceneState,
          ...options.sceneState,
          inheritJobId: inheritedId,
        },
      };
    }
  }
  const status = await mediaStudioStatus();
  const engine = options.engine ?? "standard";
  const usesWan2gp = kind === "video" && engine !== "standard" && engine !== "remix";
  const needed = usesWan2gp ? status.wan2gp : status[kind];
  if (!needed.ready) {
    throw new Error(`${kind} generation is not installed (missing: ${needed.missing.join(", ")})`);
  }
  if (
    kind === "video" &&
    engine === "remix" &&
    !((await exists(REMIX_HIGH)) && (await exists(REMIX_LOW)))
  ) {
    throw new Error("Remix のモデルが見つかりません");
  }
  if (kind === "video" && options.smooth && !usesWan2gp && !(await exists(RIFE_SCRIPT))) {
    throw new Error("なめらか補間（RIFE）が見つかりません");
  }
  if (isWanImageModel(options.imageModel) && !status.wan2gp.ready) {
    throw new Error("この画像モデルには Wan2GP が必要です");
  }
  if (options.imageModel === "qwenimgturbo" && !(await exists(QWEN_TURBO_RUNNER))) {
    throw new Error("Qwen Image 2.1 Turbo の実行ファイルが見つかりません");
  }
  if (options.imageModel === "anima") {
    for (const file of [COMFY_PYTHON_PATH, ...ANIMA_FILES]) {
      if (!(await exists(file)))
        throw new Error(`Anima のファイルが見つかりません（${path.basename(file)}）`);
    }
  }
  if (
    isSdxlModel(options.imageModel) &&
    isStoreModelId(options.imageModel) &&
    !storeSpecOf(options.imageModel)
  ) {
    throw new Error(
      "このモデルは、まだダウンロードされていません（モデルの導入画面から入れてください）",
    );
  }
  if (isSdxlModel(options.imageModel) && !(await exists(sdxlSpecOf(options.imageModel).file))) {
    throw new Error(`画像モデル（${options.imageModel}）のファイルが見つかりません`);
  }
  if (options.fast && !(await exists(DMD2_LORA))) {
    throw new Error("高速モード（DMD2）のファイルが見つかりません");
  }
  // A voice line keeps Irodori's emojis (moans, breaths) and its line breaks.
  const clean = kind === "voice" ? sanitizeVoiceScript(request) : sanitizeMediaText(request);
  // A comic episode may be left to the writer; sheets and redraws need no text at all.
  if (!clean && kind !== "video" && kind !== "comic" && kind !== "faceswap")
    throw new Error("内容を入力してください");
  if (kind === "faceswap") {
    if (!resolveSwapTarget(options)) throw new Error("入れ替える動画か画像を選んでください");
    if (!options.swapSources?.length)
      throw new Error("入れ替える顔（キャラか写真）を選んでください");
    if (!(await exists(FACEFUSION_PYTHON)))
      throw new Error("顔入れ替えのソフト（FaceFusion）が見つかりません");
  }
  if (kind === "comic") {
    const forCharacter =
      options.comicTask === "sheet" ||
      options.comicTask === "lora" ||
      options.comicTask === "outpaint" ||
      options.comicTask === "avatar" ||
      options.comicTask === "sample" ||
      options.comicTask === "model3d" ||
      options.comicTask === "mini";
    if (
      forCharacter
        ? !(await getCharacter(options.comicCharacterId ?? ""))
        : !(await getComicSeries(options.comicSeriesId ?? ""))
    ) {
      throw new Error(forCharacter ? "その登場人物はいません" : "作品が見つかりません");
    }
    if (options.comicTask === "lora" && !(await exists(SD_SCRIPTS_PYTHON))) {
      throw new Error("学習ソフト（sd-scripts）が見つかりません");
    }
  }
  assertAllowedRequest(clean, options.lyrics ?? "");
  if (kind === "motion") {
    if (
      options.motionRevoiceJobId &&
      (options.motionContinueJobId || options.motionJoinJobIds?.length)
    ) {
      throw new Error("声の変更と、下書きの続行・動画の結合は同時に指定できません");
    }
    if (options.motionRevoiceJobId && !options.voiceId && !options.voiceCaption?.trim()) {
      throw new Error("変更後の声か声の説明を選んでください");
    }
    if (!(await exists(MOTION_SCRIPT)))
      throw new Error("モーションコミックの仕組み（motion_comic.py）が見つかりません");
    if (!(await irodoriInstalled())) throw new Error("声のソフト（Irodori-TTS）が見つかりません");
    if (options.motionImageModel) {
      const spec = isSdxlModel(options.motionImageModel)
        ? sdxlSpecOf(options.motionImageModel)
        : undefined;
      if (!spec || spec.vpred || !(await exists(spec.file))) {
        throw new Error(
          "この画像モデルは、モーションコミックでは使えません（SDXL系で、V-pred ではないもの）",
        );
      }
    }
    for (const id of options.motionJoinJobIds ?? []) {
      const src = await getMediaJob(id);
      // A film still being made is fine: the join waits in the queue behind it.
      const waiting =
        src && (src.status === "queued" || src.status === "running") && src.kind === "motion";
      if (!src || (!waiting && (src.status !== "done" || !src.mime?.startsWith("video/"))))
        throw new Error("つなぐ動画は、完成した動画から選んでください");
    }
  }
  if (queue.length >= MAX_QUEUE)
    throw new Error("順番待ちがいっぱいです。少し待ってからもう一度どうぞ");
  const job: MediaJob = {
    id: randomBytes(6).toString("hex"),
    kind,
    status: "queued",
    request: clean,
    stage: working ? "順番待ち" : "準備中",
    createdAt: Date.now(),
    options,
    ...(origin.chat ? { chat: origin.chat } : {}),
  };
  job.estimateSec = defaultJobSec(kind, options);
  jobs.set(job.id, job);
  queue.push(job.id);
  void processQueue();
  return job;
}

export async function getMediaJob(id: string): Promise<MediaJob | undefined> {
  return jobs.get(id) ?? (await loadJobMeta(id));
}

/** The saved results of one kind (or all), newest first, with how many each kind has. */
export async function listMediaLibrary(
  kind: MediaKind | undefined,
  limit: number,
): Promise<{ jobs: MediaJob[]; counts: Record<MediaKind, number> }> {
  const all = await listMediaJobs(10_000);
  const counts: Record<MediaKind, number> = {
    image: 0,
    video: 0,
    music: 0,
    manga: 0,
    comic: 0,
    motion: 0,
    voice: 0,
    faceswap: 0,
  };
  for (const job of all) if (job.status === "done") counts[job.kind] += 1;
  return {
    jobs: (kind ? all.filter((job) => job.kind === kind) : all).slice(0, limit),
    counts,
  };
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

export async function readMediaResult(
  id: string,
  part = 0,
): Promise<{ mime: string; base64: string; parts?: number } | undefined> {
  const job = await getMediaJob(id);
  // A comic episode is several strips; part picks one of them.
  if (job?.files?.length && job.status === "done") {
    const file = job.files[Math.max(0, Math.min(job.files.length - 1, part))];
    if (!file) return undefined;
    return {
      mime: "image/png",
      base64: (await fs.readFile(file)).toString("base64"),
      parts: job.files.length,
    };
  }
  if (!job?.file || job.status !== "done" || !job.mime) return undefined;
  return { mime: job.mime, base64: (await fs.readFile(job.file)).toString("base64") };
}

/**
 * Deletes a job. A waiting job leaves the queue; a running one is stopped (its tool process tree,
 * or the image worker it draws with) and discarded by the runner; a finished one loses its files.
 */
export async function deleteMediaJob(id: string): Promise<boolean> {
  const job = await getMediaJob(id);
  if (!job) return false;
  if (job.status === "queued") {
    const index = queue.indexOf(id);
    if (index >= 0) queue.splice(index, 1);
    jobs.delete(id);
    return true;
  }
  if (job.status === "running") {
    job.cancelRequested = true;
    job.stage = "中止しています";
    await Promise.all([...activeChildren].map((child) => killProcessTree(child.pid)));
    if (job.kind === "image" || !job.options.sourceImageJobId) {
      // The still is drawn by the resident image worker; stopping it ends that request.
      const workers = imageWorkerChildren();
      stopImageWorker();
      await Promise.all(workers.map((worker) => killProcessTree(worker.pid)));
    }
    return true;
  }
  jobs.delete(id);
  await fs.rm(metaPath(id), { force: true });
  if (job.file) await fs.rm(job.file, { force: true });
  // A multi-page manga keeps one file per page.
  if (job.kind === "manga") {
    await Promise.all(
      (job.files ?? []).map((f) => fs.rm(f, { force: true }).catch(() => undefined)),
    );
    await removeBook(id);
  }
  if (job.kind === "comic" && job.comic?.episodeNo && !job.comic.targetJobId) {
    // The episode's panels and strips, and its entry in the series.
    await fs.rm(episodeDir(id), { recursive: true, force: true });
    const series = await getComicSeries(job.comic.seriesId);
    if (series) {
      series.episodes = series.episodes.filter((e) => e.jobId !== id);
      await saveComicSeries(series);
    }
  }
  return true;
}

// ---------- Time estimates ----------

/**
 * Typical seconds for one job once the GPU is ready (RTX 4060, 2026-10-03 measurements and the
 * engine notes shown in the app). Real results of the same settings replace them.
 */
const BASE_IMAGE_SEC: Record<ImageModel, number> = {
  wai: 30,
  asianreal: 30,
  lustify: 30,
  cyberreal: 30,
  noobai: 60,
  zimage: 90,
  anima: 75,
  klein: 60,
  qwenimg: 80,
  qwenimgturbo: 45,
  hassaku: 30,
  illustrious: 30,
  novaanime: 30,
  plantmilk: 30,
  juggernaut: 30,
  realvis: 30,
  realismil: 30,
  illust2: 30,
  animagine: 30,
  rouwei: 30,
  chenkin: 30,
  janku: 30,
  ntrmix: 30,
  desire: 30,
  cyberanime: 30,
  cybersemi: 30,
  pencil: 30,
  cyberpony: 30,
};
const FAST_IMAGE_SEC = 15;
const REALESRGAN_EXE = "D:\\media-tools\\upscale\\realesrgan-ncnn-vulkan.exe";
const COMFY_PYTHON_PATH = "D:\\media-tools\\ComfyUI\\venv\\Scripts\\python.exe";
/** Video seconds of work per second of clip, and the clip length when none is chosen. */
const VIDEO_SEC_PER_CLIP_SEC: Record<VideoEngine, number> = {
  standard: 150,
  remix: 210,
  long: 150,
  audio: 90,
  fast: 40,
  h3: 110,
};
const DEFAULT_CLIP_SEC: Record<VideoEngine, number> = {
  standard: 2,
  remix: 2,
  long: 5,
  audio: 4,
  fast: 3,
  h3: 5,
};
const SMOOTH_SEC = 60;
/** Stopping the chat model and loading the generator when nothing is running yet. */
const COLD_START_SEC = 60;

/** Jobs with the same settings take about the same time; this key groups them. */
function estimateKey(kind: MediaKind, options: MediaOptions): string {
  const quality = options.qualityMode ?? "off";
  const gpu = resolveGpuMode(options.gpuMode);
  if (kind === "image")
    return `${gpu}|image|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}|${options.repair ? "repair" : ""}|${quality}`;
  if (kind === "music")
    return `${gpu}|music|${options.durationSec ?? 90}|${options.instrumental ? 1 : 0}`;
  if (kind === "voice") return `${gpu}|voice|${options.irodoriModel ?? "large"}`;
  if (kind === "faceswap")
    return `${gpu}|faceswap|${options.swapQuality ?? "standard"}|${options.swapEnhancer ? 1 : 0}`;
  if (kind === "motion") {
    if (options.motionRevoiceJobId)
      return `${gpu}|motion-revoice|${options.voiceId ?? "description"}`;
    if (options.motionJoinJobIds?.length)
      return `${gpu}|motion-join|${options.motionJoinJobIds.length}|${options.motionBridgeSeconds ?? 3}`;
    return `${gpu}|motion|${options.motionMinutes ?? 1}|${options.motionFellatio ? 1 : 0}|${options.motionPreview === false ? "" : "p"}`;
  }
  if (kind === "comic") {
    return `${gpu}|comic|${options.comicTask ?? "episode"}|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}${options.sampleKeys?.length ? `|batch${options.sampleKeys.length}` : ""}`;
  }
  if (kind === "manga") {
    return `${gpu}|manga|${options.mangaLayout ?? "4koma"}|${options.mangaPages ?? ""}|${options.imageModel ?? "wai"}|${options.fast ? "fast" : ""}`;
  }
  const engine = options.engine ?? "standard";
  const clip = options.durationSec ?? DEFAULT_CLIP_SEC[engine];
  const source = options.sourceImageJobId || options.sourceImageBase64 ? "src" : "";
  return `${gpu}|video|${engine}|${clip}|${options.smooth ? "smooth" : ""}|${source}`;
}

function baseJobSec(kind: MediaKind, options: MediaOptions): number {
  // Anima starts ComfyUI for every picture: 45 s measured with the distilled model, 75 s without it.
  const still =
    options.imageModel === "anima" && options.fast
      ? 45
      : options.fast && !isWanImageModel(options.imageModel) && options.imageModel !== "noobai"
        ? FAST_IMAGE_SEC
        : (BASE_IMAGE_SEC[options.imageModel ?? "wai"] ?? 30);
  if (kind === "image" && options.repair) return 240;
  if (kind === "image" && options.upscaleSourceJobId)
    return options.upscaleModel === "7b" ? 150 : 70;
  if (kind === "image") return still;
  if (kind === "music") return 30 + (options.durationSec ?? 90);
  // Script by the chat model, every panel drawn, then the page put together.
  if (kind === "manga" && options.mangaLayout === "book") {
    const pages = Math.max(
      MANGA_MIN_PAGES,
      Math.min(MANGA_MAX_PAGES, options.mangaPages ?? MANGA_DEFAULT_PAGES),
    );
    // Outline, then about 40 s of script per page, then about 5 panels a page (and a cover) drawn
    // on one or two GPUs, and the typesetting.
    const lanes = generationCudaDevices(options.gpuMode === "fast").length >= 2 ? 2 : 1;
    const pictures = pages * MANGA_PANEL_COUNT.book + (options.mangaCover === false ? 0 : 1);
    return 90 + pages * 40 + Math.ceil((pictures * still) / lanes) + 20;
  }
  if (kind === "manga") return 60 + still * MANGA_PANEL_COUNT[options.mangaLayout ?? "4koma"] + 10;
  // Model load, then a few seconds per line.
  if (kind === "voice" && options.voiceDrama) {
    const lines = options.voiceDrama.minutes * 7;
    return (
      120 +
      Math.ceil(lines / 24) * 90 +
      lines * (options.irodoriModel === "large" ? 14 : options.irodoriModel === "small" ? 5 : 1.5)
    );
  }
  if (kind === "voice")
    return options.irodoriModel === "fast" ? 15 : options.irodoriModel === "small" ? 25 : 70;
  // The real figure depends on the video; media.estimate computes it from the file.
  if (kind === "faceswap") return 600;
  // Script, ~12 voice lines (large), 3-5 pictures, then 6-8 cuts of ~6 min (shared by two GPUs) and the film.
  if (kind === "motion") {
    if (options.motionRevoiceJobId) return 120 + 12 * 45 + 120;
    if (options.motionJoinJobIds?.length)
      return joinEstimateSec(options.motionJoinJobIds.length, options.motionBridgeSeconds ?? 3);
    // A cut takes about 350 s (640x368 clip ~326 s + Real-ESRGAN ~25 s); the two GPUs share the cuts.
    return 120 + 12 * 45 + 5 * 40 + (options.motionFellatio ? 8 : 6) * 350 + 120;
  }
  if (kind === "comic") {
    // Outline and script by the chat model (~3 min), panels (two-person ones take longer), strips.
    if (options.comicTask === "sheet") return still;
    if (options.comicTask === "outpaint") return 200;
    // Qwen Image 2.1 edit of the standing picture (about 2.5 minutes with the start-up).
    if (options.comicTask === "sample" && options.sampleKeys?.length)
      return 120 + options.sampleKeys.length * (options.sampleEngine === "precise" ? 200 : 55);
    if (options.comicTask === "sample")
      return options.sampleEngine === "precise"
        ? 360
        : options.sampleEngine === "edit2509"
          ? 240
          : 150;
    if (options.comicTask === "avatar") return 120 + 22 * allAvatarFrames().length;
    if (options.comicTask === "mini") return 120 + 25 * MINI_FRAMES.length;
    if (options.comicTask === "model3d") return 420;
    if (options.comicTask === "redraw") return still * 1.5 + 20;
    // Qwen Image Edit makes ~23 pictures (~25 s each after loading), then ~1,100 training steps.
    if (options.comicTask === "lora")
      return 120 + 25 * DATASET_VIEWS.length + 2.2 * (DATASET_VIEWS.length + 1) * 8 * 6;
    return 180 + still * 1.3 * COMIC_PANELS_PER_EPISODE + 30;
  }
  const engine = options.engine ?? "standard";
  const clip = options.durationSec ?? DEFAULT_CLIP_SEC[engine];
  const drawsStill = !(options.sourceImageJobId || options.sourceImageBase64);
  return (
    VIDEO_SEC_PER_CLIP_SEC[engine] * clip +
    (options.smooth ? SMOOTH_SEC : 0) +
    (drawsStill ? still : 0)
  );
}

function defaultJobSec(kind: MediaKind, options: MediaOptions): number {
  const base = baseJobSec(kind, options);
  const mode = options.qualityMode;
  if (!mode || mode === "off") return base;
  const finish =
    kind === "image"
      ? mode === "max"
        ? 150
        : 70
      : kind === "manga" || kind === "comic"
        ? mode === "max"
          ? 420
          : 210
        : kind === "voice" || kind === "music"
          ? 30
          : mode === "max"
            ? 300
            : 150;
  return base + finish;
}

function median(values: number[]): number {
  const sorted = values.toSorted((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)] ?? 0;
}

export type MediaEstimate = {
  /** This job alone. */
  jobSec: number;
  /** Jobs ahead of it: the rest of the running one plus the queued ones. */
  waitSec: number;
  totalSec: number;
  /** How many finished jobs with the same settings the estimate is based on (0 = typical values). */
  samples: number;
};

/** Roughly how long a new job would take, including the queue in front of it. */
export async function estimateMediaJob(
  kind: MediaKind,
  options: MediaOptions,
): Promise<MediaEstimate> {
  const history = (await listMediaJobs(10_000)).filter(
    (job) => job.status === "done" && (job.durationSec ?? 0) > 0,
  );
  const estimateOf = (jobKind: MediaKind, jobOptions: MediaOptions) => {
    const key = estimateKey(jobKind, jobOptions);
    const same = history
      .filter((job) => estimateKey(job.kind, job.options) === key)
      .slice(0, 5)
      .map((job) => job.durationSec ?? 0);
    return same.length > 0
      ? { sec: median(same), samples: same.length }
      : { sec: defaultJobSec(jobKind, jobOptions), samples: 0 };
  };
  const own = estimateOf(kind, options);
  let waitSec = 0;
  for (const job of jobs.values()) {
    if (job.status === "running") {
      const elapsed = Math.round((Date.now() - (job.startedAt ?? Date.now())) / 1000);
      waitSec += Math.max(30, estimateOf(job.kind, job.options).sec - elapsed);
    } else if (job.status === "queued") {
      waitSec += estimateOf(job.kind, job.options).sec;
    }
  }
  // Real results already include the start-up; typical values need it when nothing is running.
  const coldStart = !working && own.samples === 0 ? COLD_START_SEC : 0;
  const jobSec = own.sec + coldStart;
  return { jobSec, waitSec, totalSec: jobSec + waitSec, samples: own.samples };
}

export type FaceSwapEstimate = MediaEstimate & {
  frames: number;
  passes: number;
  /** Seconds per frame per person that the estimate uses. */
  secPerFrame: number;
  /** True when the speed comes from real earlier jobs. */
  measured: boolean;
  videoSec: number;
};

/** How long a face swap of this file would take: its frames × the speed, plus the queue in front. */
export async function estimateFaceSwapJob(options: MediaOptions): Promise<FaceSwapEstimate> {
  const target = resolveSwapTarget(options);
  if (!target || !(await exists(target))) throw new Error("入れ替える動画か画像が見つかりません");
  const probe = await probeMedia(target);
  const key = estimateKey("faceswap", options);
  const speeds = (await listMediaJobs(10_000))
    .filter(
      (job) =>
        job.kind === "faceswap" &&
        job.status === "done" &&
        (job.timings?.spf ?? 0) > 0 &&
        estimateKey(job.kind, job.options) === key,
    )
    .slice(0, 5)
    .map((job) => (job.timings?.spf ?? 0) / 1000);
  const own = estimateSwap(probe, options, speeds.length > 0 ? median(speeds) : undefined);
  const queued = await estimateMediaJob("faceswap", options);
  const coldStart = !working ? COLD_START_SEC : 0;
  const jobSec = own.jobSec + coldStart;
  return {
    jobSec,
    waitSec: queued.waitSec,
    totalSec: jobSec + queued.waitSec,
    samples: speeds.length,
    frames: own.frames,
    passes: own.passes,
    secPerFrame: own.secPerFrame,
    measured: own.measured,
    videoSec: Math.round(probe.durationSec),
  };
}

// ─── BGM自動選択ヘルパー ────────────────────────────────────────────────────

/**
 * モーションコミックのシチュエーションに合ったBGMジャンルを返す。
 * 実際のBGM生成は ACE-Step / Suno API を呼ぶ予定。現時点はプロンプト文字列のみ返す。
 */
export function selectBgmPrompt(templateId: string): string {
  const BGM_MAP: Record<string, string> = {
    missionary: "slow romantic piano, soft strings, intimate bedroom ambiance",
    cowgirl: "upbeat sensual jazz, medium tempo, piano and bass",
    doggystyle: "driving rhythm, dark electronic beat, tension building",
    bath: "ambient spa music, water sounds, gentle synth pads",
    rape_scene: "dark ambient, tense strings, heavy atmosphere",
    orgy_scene: "club music, heavy bass, electronic dance beat",
    outdoor_sex: "nature ambiance, birds, light breeze, acoustic guitar",
    sugar_daddy_scene: "smooth jazz, upscale lounge, piano",
    default: "soft ambient music, subtle strings, intimate mood",
  };
  return BGM_MAP[templateId] ?? BGM_MAP.default ?? "";
}

// ─── IP-Adapter 顔合わせヘルパー ─────────────────────────────────────────────

/**
 * useIpAdapter フラグが立っているキャラクターの場合、
 * 設定画（sheetJobId）を参照画像としてプロンプトに付加する。
 * 実際のIP-Adapter呼び出しは img2img パスで行い、strength を低めに設定する。
 */
export function buildIpAdapterOptions(
  heroine: { useIpAdapter?: boolean; sheetJobId?: string } | undefined,
): {
  ipAdapterEnabled: boolean;
  referenceJobId?: string;
} {
  if (!heroine?.useIpAdapter || !heroine?.sheetJobId) {
    return { ipAdapterEnabled: false };
  }
  return {
    ipAdapterEnabled: true,
    referenceJobId: heroine.sheetJobId,
  };
}
