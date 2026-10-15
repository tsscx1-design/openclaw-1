import type { SwapOptions } from "../face-swap.js";
import type { MangaLayout } from "../manga-script.js";
import type { MediaProductionOptions, MediaQualityMode } from "../media-production-controls.js";
import type { MotionMinutes, MotionOptionId, MotionTemplateId } from "../motion-comic.js";
import type {
  MotionFlowStep,
  MotionSfxId,
  MotionTalk,
  MotionVoiceStyle,
  VoiceDrama,
} from "../motion-extras.js";

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

export type SdcppVideoEngine = "standard" | "remix";

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
  | `st_${string}`;

// a model downloaded from the model store (SDXL type)
/** Still-image models that run in Wan2GP instead of the SDXL image worker. */
export type WanImageModel = "zimage" | "klein" | "qwenimg" | "qwenimgturbo";

export type SdxlModel = Exclude<ImageModel, WanImageModel | "anima">;

export type StaticSdxlModel = Exclude<SdxlModel, `st_${string}`>;

export type SdxlSampler = "euler_a" | "dpmpp_2m_sde_karras" | "lcm";
