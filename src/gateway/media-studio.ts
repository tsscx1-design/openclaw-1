// Local image / video / music generation on the RTX 4060 (8 GB).
// The main chat LLM holds almost all VRAM, so a job stops it (the watchdog pause flag keeps it
// down) and restarts it once the queue is empty. Jobs run one at a time.
//   image: stable-diffusion.cpp (CUDA) + WAI-Illustrious SDXL (uncensored anime)
//   video: stable-diffusion.cpp vid_gen + Wan 2.2 14B Rapid AIO NSFW (image-to-video, 4 steps);
//          text-to-video renders a still with the image model first, then animates it
//   music: acestep.cpp (ace-lm + ace-synth) + ACE-Step 1.5 turbo; lyrics are written by its LM

export {
  type ImageModel,
  type MediaJob,
  type MediaJobStatus,
  type MediaKind,
  type MediaOptions,
  type VideoEngine,
  type WanImageModel,
} from "./media/types.js";
export { isWanImageModel, sdxlSampling } from "./media/config.js";
export { mediaStudioStatus } from "./media/status.js";
export {
  assertAllowedRequest,
  isSexualRequest,
  sanitizeH3Prompt,
  sanitizeMediaText,
} from "./media/text.js";
export {
  buildIpAdapterOptions,
  estimateFaceSwapJob,
  estimateMediaJob,
  type FaceSwapEstimate,
  type MediaEstimate,
  selectBgmPrompt,
} from "./media/estimates.js";
export { latestTrainingState, trainingDatasetCount } from "./media/gpu-lease.js";
export { sanitizeLyrics } from "./media/music.js";
export { sanitizeVoiceScript } from "./media/voice-drama.js";
export { sdcppVideoArgs, wan2gpFrames } from "./media/video.js";
export { writeCharacterLook, writeComicCast } from "./media/comic.js";
export { bridgePlan, joinEstimateSec } from "./media/motion-join.js";
export { comicSheetViewOf, hairColorOf, nsfwLook } from "./media/character-sheet.js";
export {
  FACE_ONLY_PROPORTIONS,
  multiPhotoPrompt,
  photoIdentityPrompt,
  photoStylePrompt,
  randomBodyPrompt,
} from "./media/character-prompts.js";
export {
  adoptLatestSheet,
  avatarDir,
  makeCharacterIcon,
  miniDir,
} from "./media/character-avatar.js";
export {
  applyGenStyle,
  deleteMediaJob,
  listMediaLibrary,
  onMediaJobFinished,
  readMediaResult,
  startMediaJob,
} from "./media/queue.js";
export { getMediaJob, listMediaJobs } from "./media/state.js";
