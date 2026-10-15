import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { createWebSearchTool } from "../../agents/tools/web-tools.js";
import {
  characterDetails,
  fillPrompt,
  parseFill,
  rememberedHit,
  searchAdultCharacters,
  searchCharacters,
  searchTitles,
  titleCharacters,
} from "../anime-lookup.js";
import { conciergePrompt, parseConciergeReply } from "../app-guide.js";
import { allAvatarFrames, MINI_FRAMES } from "../avatar-frames.js";
import {
  activateAvatarModel,
  deleteAvatarModel,
  listAvatarModels,
  saveModelChunk,
  deactivateAvatarModels,
  importLocalModel,
  listLocalModelFiles,
  listModelFiles,
  readModelChunk,
} from "../avatar-models.js";
import {
  backupTask,
  deleteBackup,
  listBackups,
  readBackupChunk,
  savePhoneChunk,
  startPcBackup,
  startPcRestore,
} from "../backup.js";
import { searchBishoge } from "../bishoge-db.js";
import { assertAgeAllowed } from "../blocked-terms-config.js";
import {
  createFolder,
  deleteFolder,
  folderIconBase64,
  listFolders,
  moveCharacter,
  reorderCharacters,
  reorderFolders,
  updateFolder,
} from "../character-folders.js";
import {
  cleanCharacterMotionProfile,
  readCharacterMotionProfile,
  saveCharacterMotionProfile,
  deleteCharacterMotionProfile,
} from "../character-motion-profile.js";
import { syncPartnerFromCharacter } from "../character-partner.js";
import {
  characterPhotoUploads,
  removeCharacterPhotoUploads,
  saveCharacterPhotoUpload,
} from "../character-photo-uploads.js";
import { chatEngineStatus, switchChatEngine } from "../chat-engine.js";
import {
  applyCharacterEdit,
  characterAssetDir,
  cleanSheetValues,
  cleanSpec,
  DATASET_LABELS,
  specSummary,
  deleteCharacter,
  getCharacter,
  type LibraryCharacter,
  listCharacters,
  registerCharacter,
  resolveCast,
  saveCharacter,
  triggerFor,
} from "../comic-characters.js";
import {
  MAX_COMIC_CAST,
  type ComicCharacter,
  cleanCharacter,
  deleteComicSeries,
  getComicSeries,
  listComicSeries,
  newComicSeries,
  saveComicSeries,
} from "../comic-series.js";
import {
  applyDevRequest,
  createDevRequest,
  devDiff,
  devEnabled,
  listDevRequests,
  rejectDevRequest,
  setDevEnabled,
} from "../crie-dev.js";
import { addCustomMotion, listCustomMotions, removeCustomMotion } from "../custom-motions.js";
import {
  SWAP_QUALITIES,
  deleteSwapInput,
  detectSwapFaces,
  listSwapInputs,
  probeMedia,
  resolveInputFile,
  saveSwapChunk,
  swapInputDir,
  type SwapSource,
} from "../face-swap.js";
import { readGenStyle, writeGenStyle } from "../gen-style.js";
import {
  browseGroups,
  browsePeople,
  browseWikipedia,
  groupMembers,
  fetchThumbnail,
  wikipediaRosterLine,
  PERSON_KINDS,
  characterListable,
  characterRosterLine,
  type GenderFilter,
  type PersonKind,
} from "../idol-catalog.js";
import { attachMemberPhotos } from "../idol-character-photos.js";
import {
  EXTERNAL_SOURCES,
  type ExternalSource,
  externalPersonDetail,
  externalRosterLine,
  listExternalPeople,
} from "../idol-external.js";
import {
  addExtraPhoto,
  findDuplicates,
  getMemberNote,
  listExtraPhotos,
  mergeMembers,
  noteSummary,
  promoteExtraPhoto,
  readExtraPhoto,
  removeExtraPhoto,
  searchMembers,
  setMemberNote,
} from "../idol-extras.js";
import {
  downloadPersonImage,
  recordPhotoCredit,
  searchPersonImages,
  searchVertexImages,
} from "../idol-images.js";
import {
  addCategory,
  addGroup,
  findIdolMember,
  idolCharacterDraft,
  idolCategories,
  idolGroups,
  idolMembers,
  idolPhotoDir,
  idolPhotoFileOf,
  idolPhotoIndex,
  idolRoster,
  idolRosterFile,
  manualMemberLine,
  readIdolPhoto,
  saveIdolPhoto,
  createFanBookBackup,
  fanBookBackupDir,
  listFanBookBackups,
  restoreFanBookBackup,
  moveIdolCategory,
  moveIdolCategoryTo,
  moveIdolGroupTo,
  moveIdolGroup,
  removeIdolCategory,
  removeIdolGroup,
  removeIdolMember,
  renameIdolCategory,
  setGroupCategory,
  setIdolCategoryParent,
  upsertRosterLine,
  upsertRosterLines,
  mergeIdolCategories,
} from "../idol-master.js";
import { mergeGroups, planGroupMerge } from "../idol-merge.js";
import {
  applyOfficial,
  cancelOfficial,
  officialGroups,
  officialState,
  startOfficialCheck,
} from "../idol-official.js";
import { pictureFromUrl } from "../idol-photo-url.js";
import { gatherMaterials, parseResearch, researchPrompt } from "../idol-research.js";
import {
  cancelSourceJob,
  clearSourceQueue,
  enqueueAllSources,
  enqueueSource,
  sourceQueue,
} from "../idol-source-queue.js";
import {
  applySource,
  cancelSource,
  listSources,
  removeSource,
  saveSource,
  saveSources,
  SOURCE_PRESETS,
  sourceState,
  splitUrls,
  startSourceCheck,
} from "../idol-source.js";
import { groupStyleFor, toneLinesFromMemo } from "../idol-style.js";
import { maybeStartWatch, runWatchCheck, settleWatch, watchState } from "../idol-watch.js";
import { applyBulk, bulkState, cancelBulk, startBulkCheck } from "../idol-wikidata-bulk.js";
import { isQid, readWikidataPerson, searchWikidata, wikidataRosterLine } from "../idol-wikidata.js";
import { ask as askLocalModel, intakeItems } from "../item-intake.js";
import { listBooks, loadBook, updateBook } from "../manga-book-store.js";
import { listWorks, moveVolume, renameWork } from "../manga-works.js";
import { mediaProductionOptionsFromParams } from "../media-production-controls.js";
import {
  assertAllowedRequest,
  avatarDir,
  miniDir,
  comicSheetViewOf,
  deleteMediaJob,
  makeCharacterIcon,
  writeCharacterLook,
  writeComicCast,
  estimateFaceSwapJob,
  estimateMediaJob,
  getMediaJob,
  listMediaLibrary,
  mediaStudioStatus,
  readMediaResult,
  startMediaJob,
  type MediaJob,
  type ImageModel,
  type MediaKind,
  type MediaOptions,
} from "../media-studio.js";
import {
  cancelStoreDownload,
  deleteStoreModel,
  installedStoreIds,
  listStore,
  setCivitaiKey,
  startStoreDownload,
} from "../model-store.js";
import { motionCatalog } from "../motion-catalog.js";
import { MOTION_MINUTES, MOTION_OPTION_IDS, MOTION_TEMPLATE_IDS } from "../motion-comic.js";
import { motionExtrasFromParams, voiceDramaFromParams } from "../motion-extras.js";
import { researchIoFromTools } from "../partner-research-io.js";
import { readJsonFile, resolveOpenClawHome } from "../partner-store.js";
import {
  analyzePhoto,
  isolatePhotoSubject,
  traitsForEditor,
  type PhotoTraits,
} from "../photo-analysis.js";
import { publicGroupMembers, searchPublicSubjects } from "../public-subject-lookup.js";
import {
  adultBaseFile,
  addCustomSetting,
  deleteCustomSetting,
  listSampleSubjects,
  loadSampleDescriptions,
  loadSampleIndex,
  resolveSampleSubject,
  itemInstructions,
  saveSampleIndex,
  updateCustomSetting,
} from "../sample-factory.js";
import { startMotionBranch } from "../studio-assistant.js";
import { searchVndbCharacters, vndbCharacterDetails } from "../vndb-lookup.js";
import { normalizeVoiceCaption } from "../voice-caption.js";
import { waifuImages, waifuTags } from "../waifu-im.js";
import type { GatewayRequestHandlers, RespondFn } from "./types.js";

type Params = Record<string, unknown>;

const KINDS: readonly MediaKind[] = [
  "image",
  "video",
  "music",
  "manga",
  "comic",
  "voice",
  "motion",
  "faceswap",
];
const MANGA_LAYOUTS = ["4koma", "page", "book"] as const;
const COMIC_TASKS = ["episode", "sheet", "redraw", "lora", "mini"] as const;
const COMIC_RATINGS = ["general", "adult"] as const;
const ASPECTS = ["square", "portrait", "landscape"] as const;
const VIDEO_ENGINES = ["standard", "remix", "long", "audio", "fast", "h3"] as const;
const STATIC_IMAGE_MODELS = [
  "wai",
  "noobai",
  "zimage",
  "anima",
  "klein",
  "qwenimg",
  "qwenimgturbo",
  "lustify",
  "cyberreal",
  "asianreal",
  "hassaku",
  "illustrious",
  "novaanime",
  "plantmilk",
  "juggernaut",
  "realvis",
  "realismil",
  "illust2",
  "animagine",
  "rouwei",
  "chenkin",
  "janku",
  "ntrmix",
  "desire",
  "cyberanime",
  "cybersemi",
  "pencil",
  "cyberpony",
] as const;
/** Image models by id: the built-in ones, and the ones downloaded from the model store. */
const IMAGE_MODELS = {
  find(predicate: (value: ImageModel) => boolean): ImageModel | undefined {
    return (
      STATIC_IMAGE_MODELS.find(predicate) ?? (installedStoreIds() as ImageModel[]).find(predicate)
    );
  },
};
// The result travels inside one WebSocket frame (25 MB); keep uploads well below it.
const MAX_SOURCE_IMAGE_BASE64 = 8 * 1024 * 1024;

function str(params: Params, key: string): string | undefined {
  const value = params[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function fail(
  respond: RespondFn,
  message: string,
  code:
    | typeof ErrorCodes.INVALID_REQUEST
    | typeof ErrorCodes.UNAVAILABLE = ErrorCodes.INVALID_REQUEST,
) {
  respond(false, undefined, errorShape(code, message));
}

/** What the app shows in its list; the media bytes come from media.job.result. */
function summary(job: MediaJob) {
  return {
    jobId: job.id,
    kind: job.kind,
    status: job.status,
    request: job.request,
    stage: job.stage,
    createdAt: job.createdAt,
    durationSec: job.durationSec,
    elapsedSec:
      job.startedAt && !job.finishedAt
        ? Math.round((Date.now() - job.startedAt) / 1000)
        : undefined,
    estimateSec: job.estimateSec,
    remainingSec:
      job.status === "running" && job.estimateSec && job.startedAt
        ? Math.max(0, job.estimateSec - Math.round((Date.now() - job.startedAt) / 1000))
        : job.status === "queued"
          ? job.estimateSec
          : undefined,
    preview: job.motion?.preview === true ? true : undefined,
    error: job.error,
    mime: job.mime,
    lyrics: job.kind === "music" ? job.options.lyrics : undefined,
    mangaLayout: job.kind === "manga" ? (job.options.mangaLayout ?? "4koma") : undefined,
    mangaTitle: job.manga?.title,
    mangaVolume: job.manga?.volume,
    mangaCover: job.manga?.cover === true ? true : undefined,
    mangaLines: job.manga?.lines,
    parts: job.files?.length,
    comic: job.comic,
    quality: job.quality,
    branch: job.options.branch,
  };
}

/** The character the samples are made with: the one named by the app, else Crie, else the first favorite. */
async function sampleOwner(params: Params): Promise<LibraryCharacter | undefined> {
  const byId = await getCharacter(str(params, "characterId") ?? "");
  if (byId) return byId;
  const all = await listCharacters();
  return all.find((c) => c.name === "クリエ") ?? all.find((c) => c.isFavorite) ?? all[0];
}

function nonNegative(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) && value >= 0
    ? Math.round(value * 100) / 100
    : undefined;
}

/** The faces to put in: up to four people, each a library character or a picture in the input folder. */
function parseSwapSources(value: unknown): SwapSource[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const out: SwapSource[] = [];
  for (const item of value.slice(0, 4)) {
    if (typeof item !== "object" || item === null) continue;
    const row = item as Record<string, unknown>;
    const characterId =
      typeof row.characterId === "string" && /^[A-Za-z0-9_-]{4,60}$/.test(row.characterId)
        ? row.characterId
        : undefined;
    const inputId =
      typeof row.inputId === "string" && /^in:.{1,100}$/.test(row.inputId)
        ? row.inputId
        : undefined;
    if (!characterId && !inputId) continue;
    const position =
      typeof row.position === "number" &&
      Number.isInteger(row.position) &&
      row.position >= 0 &&
      row.position < 20
        ? row.position
        : undefined;
    out.push({
      ...(characterId ? { characterId } : {}),
      ...(inputId ? { inputId } : {}),
      ...(position !== undefined ? { position } : {}),
    });
  }
  return out.length > 0 ? out : undefined;
}

/** The generation settings of media.generate / media.estimate, each value from its allowed set. */
function parseMediaOptions(params: Params): MediaOptions {
  const duration = typeof params.durationSec === "number" ? params.durationSec : undefined;
  return {
    gpuMode: (["parallel", "fast"] as const).find((value) => value === str(params, "gpuMode")),
    aspect: ASPECTS.find((value) => value === str(params, "aspect")),
    engine: VIDEO_ENGINES.find((value) => value === str(params, "engine")),
    imageModel: IMAGE_MODELS.find((value) => value === str(params, "imageModel")),
    fast: params.fast === true,
    smooth: params.smooth === true,
    sourceImageJobId: str(params, "sourceImageJobId"),
    sourceImageBase64: str(params, "sourceImageBase64"),
    lyrics: str(params, "lyrics"),
    instrumental: params.instrumental === true,
    durationSec: duration && Number.isFinite(duration) ? Math.round(duration) : undefined,
    mangaLayout: MANGA_LAYOUTS.find((value) => value === str(params, "mangaLayout")),
    mangaPages:
      typeof params.mangaPages === "number" && Number.isFinite(params.mangaPages)
        ? Math.max(2, Math.min(32, Math.round(params.mangaPages)))
        : undefined,
    mangaTone: (["color", "mono"] as const).find((value) => value === str(params, "mangaTone")),
    mangaAutoCast: params.mangaAutoCast === false ? false : undefined,
    mangaCover: params.mangaCover === false ? false : undefined,
    mangaSequelOf: /^[a-f0-9]{6,32}$/.test(str(params, "mangaSequelOf") ?? "")
      ? str(params, "mangaSequelOf")
      : undefined,
    comicSeriesId: str(params, "comicSeriesId"),
    comicTask: COMIC_TASKS.find((value) => value === str(params, "comicTask")),
    voiceId: /^voice-[a-z0-9-]{8,60}$/.test(str(params, "voiceId") ?? "")
      ? str(params, "voiceId")
      : undefined,
    voiceCaption: normalizeVoiceCaption(str(params, "voiceCaption") ?? "") || undefined,
    irodoriModel: (["small", "large", "fast"] as const).find(
      (value) => value === str(params, "irodoriModel"),
    ),
    motionMinutes: MOTION_MINUTES.find((value) => value === params.motionMinutes),
    motionFellatio: typeof params.motionFellatio === "boolean" ? params.motionFellatio : undefined,
    motionCharacterId: str(params, "motionCharacterId"),
    motionDraft: params.motionDraft === true ? true : undefined,
    ...motionExtrasFromParams(params),
    voiceDrama: voiceDramaFromParams(params.voiceDrama),
    upscaleSourceJobId: /^[a-f0-9]{6,32}$/.test(str(params, "upscaleSourceJobId") ?? "")
      ? str(params, "upscaleSourceJobId")
      : undefined,
    upscaleResolution:
      typeof params.upscaleResolution === "number"
        ? Math.round(params.upscaleResolution)
        : undefined,
    upscaleModel:
      str(params, "upscaleModel") === "7b"
        ? ("7b" as const)
        : str(params, "upscaleModel") === "3b"
          ? ("3b" as const)
          : undefined,
    motionImageModel: IMAGE_MODELS.find((value) => value === str(params, "motionImageModel")),
    motionTemplate: MOTION_TEMPLATE_IDS.find((value) => value === str(params, "motionTemplate")),
    motionOptions: Array.isArray(params.motionOptions)
      ? MOTION_OPTION_IDS.filter((id) => (params.motionOptions as unknown[]).includes(id))
      : undefined,
    swapTarget: /^(in:.{1,100}|job:[a-f0-9]{6,32})$/.test(str(params, "swapTarget") ?? "")
      ? str(params, "swapTarget")
      : undefined,
    swapSources: parseSwapSources(params.swapSources),
    swapEnhancer: params.swapEnhancer === true ? true : undefined,
    swapQuality: SWAP_QUALITIES.find((value) => value === str(params, "swapQuality")),
    swapTrimStartSec: nonNegative(params.swapTrimStartSec),
    swapTrimEndSec: nonNegative(params.swapTrimEndSec),
    swapReferenceSec: nonNegative(params.swapReferenceSec),
    motionOutfit: str(params, "motionOutfit")?.slice(0, 20),
    comicOutfit: str(params, "comicOutfit")?.slice(0, 20),
    motionContinueJobId: /^[a-f0-9]{6,32}$/.test(str(params, "motionContinueJobId") ?? "")
      ? str(params, "motionContinueJobId")
      : undefined,
    motionRevoiceJobId: /^[a-f0-9]{6,32}$/.test(str(params, "motionRevoiceJobId") ?? "")
      ? str(params, "motionRevoiceJobId")
      : undefined,
    motionRedrawKeys: Array.isArray(params.motionRedrawKeys)
      ? params.motionRedrawKeys.filter(
          (k): k is string => typeof k === "string" && /^[kf][1-4]$/.test(k),
        )
      : undefined,
    // ④ 多人数シーン（2〜4人目）
    motionSceneCount:
      typeof params.motionSceneCount === "number"
        ? Math.min(4, Math.max(1, Math.floor(params.motionSceneCount)))
        : undefined,
    motionCharacterId2: str(params, "motionCharacterId2"),
    motionOutfit2: str(params, "motionOutfit2")?.slice(0, 20),
    motionCharacterId3: str(params, "motionCharacterId3"),
    motionOutfit3: str(params, "motionOutfit3")?.slice(0, 20),
    motionCharacterId4: str(params, "motionCharacterId4"),
    motionOutfit4: str(params, "motionOutfit4")?.slice(0, 20),
    // ⑤ 前段シーン（着衣→脱衣→挿入）カスタムステップ
    motionApproachSteps: Array.isArray(params.motionApproachSteps)
      ? (params.motionApproachSteps as unknown[])
          .filter((s): s is Record<string, string> => typeof s === "object" && s !== null)
          .slice(0, 10)
          .map((s) => ({
            label: String(s["label"] ?? "").slice(0, 30),
            tags: String(s["tags"] ?? "").slice(0, 120),
            fontStyle: String(s["fontStyle"] ?? "normal").slice(0, 20),
          }))
      : undefined,
    ...mediaProductionOptionsFromParams(params),
  };
}

/** Links every cast member to the character library, adding the new ones. */
async function linkCast(cast: ComicCharacter[]): Promise<ComicCharacter[]> {
  const out: ComicCharacter[] = [];
  for (const member of cast) {
    const entry = await registerCharacter(member);
    out.push({ ...member, characterId: entry.id });
  }
  return out;
}

/** characterId, or seriesId + name (linking that cast member to the library if needed). */
async function characterFromParams(params: Params): Promise<LibraryCharacter | undefined> {
  const byId = await getCharacter(str(params, "characterId") ?? "");
  if (byId) return byId;
  const series = await getComicSeries(str(params, "seriesId") ?? "");
  const member = series?.cast.find((c) => c.name === str(params, "name"));
  if (!series || !member) return undefined;
  if (member.characterId) {
    const linked = await getCharacter(member.characterId);
    if (linked) return linked;
  }
  series.cast = await linkCast(series.cast);
  await saveComicSeries(series);
  const linkedId = series.cast.find((c) => c.name === member.name)?.characterId;
  return linkedId ? getCharacter(linkedId) : undefined;
}

/** The sides to widen a sheet on, as fractions of the picture (0 to 1.5 each); none means no outpaint. */
function outpaintOf(
  raw: unknown,
): { top: number; bottom: number; left: number; right: number } | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const side = (value: unknown) =>
    typeof value === "number" && value > 0 ? Math.min(1.5, Math.round(value * 100) / 100) : 0;
  const input = raw as Record<string, unknown>;
  const sides = {
    top: side(input.top),
    bottom: side(input.bottom),
    left: side(input.left),
    right: side(input.right),
  };
  return sides.top + sides.bottom + sides.left + sides.right > 0 ? sides : undefined;
}

/** What the app shows for a library character: the icon inline (small), never file paths. */
const GALLERY_FFMPEG = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffmpeg.exe";

/**
 * A fan book photo made small (a JPEG whose longer side is at most `maxSide`), kept in photos-cache next to the photos
 * and made again only when the photo changes. A list shows many photos, so a few tens of KB each instead of megabytes.
 * Returns undefined when the member has no photo file or the picture cannot be made small (the caller sends the original).
 */
async function smallIdolPhoto(
  group: string,
  id: number,
  maxSide: number,
): Promise<{ base64: string; version: number } | undefined> {
  const member = findIdolMember(group, id);
  const name = member ? idolPhotoFileOf(await idolPhotoIndex(), member) : undefined;
  if (!name) return undefined;
  const source = path.join(idolPhotoDir(), name);
  const stat = await fs.stat(source).catch(() => undefined);
  if (!stat?.isFile()) return undefined;
  const dir = path.join(path.dirname(idolPhotoDir()), "photos-cache");
  const target = path.join(
    dir,
    `${createHash("sha1").update(name).digest("hex").slice(0, 16)}_${maxSide}.jpg`,
  );
  const fresh = await fs
    .stat(target)
    .then((t) => t.mtimeMs >= stat.mtimeMs)
    .catch(() => false);
  if (!fresh) {
    await fs.mkdir(dir, { recursive: true });
    const made = await new Promise<boolean>((resolve) => {
      execFile(
        GALLERY_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          "-i",
          source,
          "-vf",
          `scale='min(${maxSide},iw)':'min(${maxSide},ih)':force_original_aspect_ratio=decrease`,
          "-q:v",
          "4",
          target,
        ],
        { windowsHide: true, timeout: 60_000 },
        (err) => resolve(!err),
      );
    });
    if (!made) return undefined;
  }
  return { base64: await fs.readFile(target, "base64"), version: Math.floor(stat.mtimeMs / 1000) };
}

/** The version of a member's photo file (its time in seconds), 0 when there is none: the app keeps its saved copy by it. */
async function idolPhotoVersionOf(
  member: { group: string; id: number; name: string },
  index: Awaited<ReturnType<typeof idolPhotoIndex>>,
): Promise<number> {
  const name = idolPhotoFileOf(index, member as never);
  if (!name) return 0;
  return Math.floor(
    ((await fs.stat(path.join(idolPhotoDir(), name)).catch(() => undefined))?.mtimeMs ?? 0) / 1000,
  );
}

/** Pictures of a character that exist on disk: sheet, turnaround, training set (in view order). */
async function galleryFiles(
  entry: LibraryCharacter,
): Promise<Array<{ key: string; label: string; file: string }>> {
  const out: Array<{ key: string; label: string; file: string }> = [];
  const exists = (file: string) =>
    fs.access(file).then(
      () => true,
      () => false,
    );
  if (entry.refImage && (await exists(entry.refImage)))
    out.push({ key: "sheet", label: "設定画", file: entry.refImage });
  // the photos she was made from (from a picture, or from the fan book)
  const photos = entry.photoRefs?.length ? entry.photoRefs : entry.photoRef ? [entry.photoRef] : [];
  for (const [index, file] of photos.entries()) {
    if (await exists(file))
      out.push({
        key: `photo${index + 1}`,
        label: index === 0 ? "元の写真" : `元の写真 ${index + 1}`,
        file,
      });
  }
  const turnaround = entry.turnaroundJobId ? await getMediaJob(entry.turnaroundJobId) : undefined;
  if (turnaround?.file && (await exists(turnaround.file)))
    out.push({ key: "turnaround", label: "3面図", file: turnaround.file });
  const dataset = path.join(
    characterAssetDir(entry.id),
    "lora-work",
    "img",
    `8_${triggerFor(entry.id)}`,
  );
  for (const [index, label] of DATASET_LABELS.entries()) {
    const n = String(index + 1).padStart(2, "0");
    for (const ext of [".png", ".jpg", ".webp"]) {
      const file = path.join(dataset, `${n}${ext}`);
      if (await exists(file)) {
        out.push({ key: `d${n}`, label, file });
        break;
      }
    }
  }
  // The chat avatar's pictures (the app loads them by these keys).
  for (const frame of allAvatarFrames()) {
    const file = path.join(avatarDir(entry.id), `${frame.key}.png`);
    if (await exists(file))
      out.push({ key: `av_${frame.key}`, label: `アバター ${frame.label}`, file });
  }
  // The mini character (the helper walking about the app).
  for (const frame of MINI_FRAMES) {
    const file = path.join(miniDir(entry.id), `${frame.key}.png`);
    if (await exists(file)) out.push({ key: frame.key, label: frame.label, file });
  }
  return out;
}

async function characterView(entry: LibraryCharacter) {
  let iconBase64: string | undefined;
  if (entry.icon) iconBase64 = await fs.readFile(entry.icon, "base64").catch(() => undefined);
  return {
    id: entry.id,
    name: entry.name,
    look: entry.look,
    profile: entry.profile,
    sheetJobId: entry.sheetJobId,
    turnaroundJobId: entry.turnaroundJobId,
    hasSheet: Boolean(entry.refImage),
    folderId: entry.folderId ?? "",
    libraryOrder: entry.libraryOrder ?? 0,
    spec: entry.spec,
    motionProfile: readCharacterMotionProfile(entry.id),
    sheetValues: entry.sheetValues ?? {},
    hasAvatar: await fs.access(path.join(avatarDir(entry.id), "normal_closed.png")).then(
      () => true,
      () => false,
    ),
    imageModel: entry.imageModel ?? "",
    specText: entry.spec ? specSummary(entry.spec) : "",
    voiceId: entry.voiceId,
    voiceCaption: entry.voiceCaption,
    outfits: entry.outfits ?? [],
    iconBase64,
    loraStatus: entry.loraStatus,
    loraStep: entry.loraStep,
    loraError: entry.loraError,
    trained: entry.loraStatus === "ready" && Boolean(entry.lora),
    /** お気に入り（ホーム表示・パートナー同期） */
    isFavorite: entry.isFavorite ?? false,
    /** 紐づくパートナーID（キャラ↔パートナー同期） */
    partnerId: entry.partnerId ?? "",
    /** 多人数シーン登場人数上限 */
    sceneCapacity: entry.sceneCapacity ?? 1,
    updatedAt: entry.updatedAt,
  };
}

function guarded(
  handler: (params: Params, respond: RespondFn) => Promise<void>,
): GatewayRequestHandlers[string] {
  return async (ctx) => {
    try {
      await handler((ctx.params ?? {}) as Params, ctx.respond);
    } catch (err) {
      fail(ctx.respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  };
}

export const mediaStudioHandlers: GatewayRequestHandlers = {
  "media.status": guarded(async (_params, respond) => {
    respond(true, await mediaStudioStatus(), undefined);
  }),

  // Starts a background job; the app polls media.job.get and then fetches media.job.result.
  "media.generate": guarded(async (params, respond) => {
    const kind = str(params, "kind") as MediaKind | undefined;
    if (!kind || !KINDS.includes(kind)) {
      fail(respond, "kind must be image, video, music, manga, comic or voice");
      return;
    }
    const sourceImageBase64 = str(params, "sourceImageBase64");
    if (sourceImageBase64 && sourceImageBase64.length > MAX_SOURCE_IMAGE_BASE64) {
      fail(respond, "画像が大きすぎます（6MB まで）");
      return;
    }
    const options = parseMediaOptions(params);
    try {
      // Items chosen in the app (library options, motion options, poses) are added to the request.
      const extraKeys = (Array.isArray(params.extraItems) ? params.extraItems : []).filter(
        (value): value is string => typeof value === "string",
      );
      const extra = extraKeys.length > 0 ? await itemInstructions(extraKeys) : "";
      const request = str(params, "prompt") ?? "";
      const job = await startMediaJob(
        kind,
        extra ? `${request}（絵の指示：${extra}）` : request,
        options,
      );
      respond(true, summary(job), undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  }),

  // Starts a new editable branch while retaining the source job and its production settings.
  "media.branch": guarded(async (params, respond) => {
    const parentJobId = str(params, "parentJobId") ?? "";
    const source = await getMediaJob(parentJobId);
    if (!source || source.status !== "done") {
      fail(respond, "分岐元の完成作品が見つかりません");
      return;
    }
    const overrides = Object.fromEntries(
      Object.entries(parseMediaOptions(params)).filter(([, value]) => value !== undefined),
    ) as MediaOptions;
    const options: MediaOptions = {
      ...source.options,
      ...overrides,
      sourceImageBase64: undefined,
      repair: overrides.repair,
      upscaleSourceJobId: overrides.upscaleSourceJobId,
      branch: {
        parentJobId,
        ...(str(params, "branchName") ? { name: str(params, "branchName") } : {}),
        ...(typeof params.atSec === "number" ? { atSec: Math.max(0, params.atSec) } : {}),
      },
      sceneState: {
        ...source.options.sceneState,
        ...overrides.sceneState,
        inheritJobId: parentJobId,
      },
      ...(source.kind === "motion" ? { motionInheritJobId: parentJobId } : {}),
    };
    const request = str(params, "prompt") ?? source.request;
    const job = await startMediaJob(source.kind, request, options);
    respond(true, summary(job), undefined);
  }),

  "media.job.get": guarded(async (params, respond) => {
    const job = await getMediaJob(str(params, "jobId") ?? "");
    if (!job) {
      fail(respond, "見つかりません（削除された可能性があります）");
      return;
    }
    respond(true, summary(job), undefined);
  }),

  "media.job.result": guarded(async (params, respond) => {
    const part = typeof params.part === "number" ? Math.max(0, Math.floor(params.part)) : 0;
    const result = await readMediaResult(str(params, "jobId") ?? "", part);
    if (!result) {
      fail(respond, "まだ完成していないか、見つかりません");
      return;
    }
    respond(true, result, undefined);
  }),

  // The library: every saved result, optionally one kind only, plus per-kind counts for the tabs.
  "media.job.list": guarded(async (params, respond) => {
    const limit = typeof params.limit === "number" ? Math.min(300, Math.max(1, params.limit)) : 30;
    const kind = KINDS.find((value) => value === str(params, "kind"));
    const { jobs, counts } = await listMediaLibrary(kind, limit);
    const partners = await readJsonFile<Array<{ id?: string; name?: string }>>(
      path.join(resolveOpenClawHome(), "partners.json"),
      [],
    );
    const partnerNames = new Map(
      partners.filter((p) => p.id && p.name).map((p) => [p.id as string, p.name as string]),
    );
    const rows = [];
    for (const job of jobs) {
      // Whose it is: the character (sheets, motion comics) or the partner it was made for in chat.
      const motionId = job.options.motionCharacterId;
      const owner =
        job.comic?.character ||
        (motionId ? (await getCharacter(motionId))?.name : undefined) ||
        (job.chat?.partnerId ? partnerNames.get(job.chat.partnerId) : undefined);
      rows.push({ ...summary(job), ...(owner ? { owner } : {}) });
    }
    respond(true, { jobs: rows, counts }, undefined);
  }),

  // Rough time to finish a job with these settings, queue included (shown before "作る").
  "media.estimate": guarded(async (params, respond) => {
    const kind = str(params, "kind") as MediaKind | undefined;
    if (!kind || !KINDS.includes(kind)) {
      fail(respond, "kind must be image, video, music, manga, comic or voice");
      return;
    }
    const options = parseMediaOptions(params);
    if (kind === "faceswap") {
      respond(true, await estimateFaceSwapJob(options), undefined);
      return;
    }
    // Only whether a source image is given matters for the estimate, not its bytes.
    if (params.hasSourceImage === true && !options.sourceImageJobId)
      options.sourceImageBase64 = "-";
    respond(true, await estimateMediaJob(kind, options), undefined);
  }),

  // ---- sample picture factory ----
  // Every setting that can have a sample, the pictures made so far, and the saved default instructions.
  "sample.list": guarded(async (params, respond) => {
    const owner = await sampleOwner(params);
    const index = await loadSampleIndex();
    const samples: Record<string, { base: string; note: string; at: number }> = {};
    for (const [key, entry] of Object.entries(index.items))
      samples[key] = { base: entry.base, note: entry.note, at: entry.at };
    const hasAdultBase = owner
      ? await fs.access(adultBaseFile(owner.id)).then(
          () => true,
          () => false,
        )
      : false;
    respond(
      true,
      {
        owner: owner
          ? {
              id: owner.id,
              name: owner.name,
              hasStandingPicture: Boolean(owner.refImage),
              hasAdultBase,
            }
          : null,
        subjects: await listSampleSubjects(),
        samples,
        defaults: index.defaults,
        // The Japanese explanation of each setting (written by the local model; empty until it is done).
        descriptions: Object.fromEntries(
          Object.entries(await loadSampleDescriptions()).map(([key, row]) => [key, row.ja]),
        ),
      },
      undefined,
    );
  }),

  "sample.image": guarded(async (params, respond) => {
    const index = await loadSampleIndex();
    const entry = index.items[str(params, "key") ?? ""];
    if (!entry) {
      fail(respond, "まだサンプルがありません");
      return;
    }
    respond(
      true,
      {
        imageBase64: await fs.readFile(entry.file, "base64"),
        base: entry.base,
        note: entry.note,
        at: entry.at,
      },
      undefined,
    );
  }),

  // Makes (or remakes) the sample of one setting; `note` is the instruction written for it.
  "sample.generate": guarded(async (params, respond) => {
    const owner = await sampleOwner(params);
    const key = str(params, "key") ?? "";
    if (!owner) {
      fail(respond, "サンプルを作るキャラ（クリエ）が図鑑にいません");
      return;
    }
    if (!(await resolveSampleSubject(key))) {
      fail(respond, "その項目が見つかりません");
      return;
    }
    const job = await startMediaJob("comic", "", {
      comicTask: "sample",
      comicCharacterId: owner.id,
      sampleKey: key,
      sampleBase: str(params, "base") === "adult" ? "adult" : "normal",
      // Both GPUs (and the chat model paused) unless the caller asks for one, like the batch below.
      gpuMode: str(params, "gpuMode") === "parallel" ? "parallel" : "fast",
      ...((["fast", "precise", "edit2509"] as const).find(
        (value) => value === str(params, "engine"),
      )
        ? {
            sampleEngine: (["fast", "precise", "edit2509"] as const).find(
              (value) => value === str(params, "engine"),
            ),
          }
        : {}),
      ...(typeof params.note === "string" ? { sampleNote: params.note.trim().slice(0, 300) } : {}),
    });
    respond(true, summary(job), undefined);
  }),

  // Many samples in one job (a whole genre). Settings that already have a sample made from the same
  // base picture are left out unless `force` is set; the single-sample method above stays as it is.
  "sample.generate.batch": guarded(async (params, respond) => {
    const owner = await sampleOwner(params);
    if (!owner) {
      fail(respond, "サンプルを作るキャラ（クリエ）が図鑑にいません");
      return;
    }
    const asked = Array.isArray(params.keys)
      ? params.keys.filter((value): value is string => typeof value === "string")
      : [];
    const base = str(params, "base") === "adult" ? "adult" : "normal";
    const force = params.force === true;
    const index = await loadSampleIndex();
    const keys: string[] = [];
    let alreadyDone = 0;
    for (const key of new Set(asked)) {
      if (!force && index.items[key]?.base === base) {
        alreadyDone += 1;
        continue;
      }
      if (!(await resolveSampleSubject(key))) continue;
      keys.push(key);
      if (keys.length >= 500) break;
    }
    if (keys.length === 0) {
      respond(true, { started: false, count: 0, alreadyDone }, undefined);
      return;
    }
    const engine = (["fast", "precise", "edit2509"] as const).find(
      (value) => value === str(params, "engine"),
    );
    const job = await startMediaJob("comic", "", {
      comicTask: "sample",
      comicCharacterId: owner.id,
      sampleKeys: keys,
      sampleBase: base,
      // Both GPUs share the work unless the caller asks for one.
      gpuMode: str(params, "gpuMode") === "parallel" ? "parallel" : "fast",
      ...(engine ? { sampleEngine: engine } : {}),
    });
    respond(true, { ...summary(job), started: true, count: keys.length, alreadyDone }, undefined);
  }),

  // The adult base picture (the standing picture without clothes) that adult samples are made from.
  "sample.adultbase.generate": guarded(async (params, respond) => {
    const owner = await sampleOwner(params);
    if (!owner) {
      fail(respond, "サンプルを作るキャラ（クリエ）が図鑑にいません");
      return;
    }
    const job = await startMediaJob("comic", "", {
      comicTask: "sample",
      comicCharacterId: owner.id,
      sampleAdultBase: true,
    });
    respond(true, summary(job), undefined);
  }),

  // The default instruction of a setting (empty removes it); it is used when nothing else is written.
  "sample.default.set": guarded(async (params, respond) => {
    const key = str(params, "key") ?? "";
    if (!(await resolveSampleSubject(key))) {
      fail(respond, "その項目が見つかりません");
      return;
    }
    const index = await loadSampleIndex();
    const note = typeof params.note === "string" ? params.note.trim().slice(0, 300) : "";
    if (note) index.defaults[key] = note;
    else delete index.defaults[key];
    await saveSampleIndex(index);
    respond(true, { key, note }, undefined);
  }),

  "sample.delete": guarded(async (params, respond) => {
    const index = await loadSampleIndex();
    const key = str(params, "key") ?? "";
    const entry = index.items[key];
    if (entry) {
      await fs.rm(entry.file, { force: true }).catch(() => undefined);
      delete index.items[key];
      await saveSampleIndex(index);
    }
    respond(true, { deleted: Boolean(entry) }, undefined);
  }),

  // A setting of your own (a name and what to draw); it is listed with the others.
  "sample.custom.add": guarded(async (params, respond) => {
    const entry = await addCustomSetting({
      genre: params.genre,
      label: params.label,
      instruction: params.instruction,
    });
    respond(true, { custom: entry }, undefined);
  }),

  // Many settings at once: words split by 、 / or new lines are read by the local model and added to their genres.
  "sample.custom.bulk": async (ctx) => {
    try {
      const text = str((ctx.params ?? {}) as Params, "text") ?? "";
      const io = researchIoFromTools({
        webSearchTool: createWebSearchTool({
          config: ctx.context.getRuntimeConfig(),
          lateBindRuntimeConfig: true,
        }),
      });
      ctx.respond(true, { results: await intakeItems(text, io) }, undefined);
    } catch (err) {
      fail(ctx.respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  },

  // The chat model: Gemma or Strata (the switch runs in the background; poll chatengine.status).
  "chatengine.status": guarded(async (_params, respond) => {
    respond(true, await chatEngineStatus(), undefined);
  }),
  "chatengine.switch": guarded(async (params, respond) => {
    const engine = str(params, "engine");
    if (engine !== "gemma" && engine !== "strata") {
      fail(respond, "gemma か strata を指定してください");
      return;
    }
    respond(true, await switchChatEngine(engine), undefined);
  }),

  // The setting master data: pick a group, then a member, and add the member to the character library.
  "idolmaster.groups": guarded(async (_params, respond) => {
    const roster = idolRoster();
    respond(
      true,
      {
        groups: idolGroups(),
        categories: idolCategories(),
        roster: {
          file: idolRosterFile(),
          count: roster.members.length,
          skippedLines: roster.skippedLines,
        },
      },
      undefined,
    );
  }),

  "idolmaster.members": guarded(async (params, respond) => {
    const group = str(params, "group");
    if (!group) {
      fail(respond, "グループを選んでください");
      return;
    }
    const photos = await idolPhotoIndex();
    const versions = new Map<number, number>();
    for (const member of idolMembers(group))
      versions.set(member.id, await idolPhotoVersionOf(member, photos));
    const members = idolMembers(group).map((member) => ({
      id: member.id,
      photoV: versions.get(member.id) ?? 0,
      name: member.name,
      tags: member.tags,
      description: member.description,
      photo: idolPhotoFileOf(photos, member) !== undefined || Boolean(member.photoUrl),
      // What the data knows about her (only the ones that are written).
      ...(member.generation ? { generation: member.generation } : {}),
      ...(member.nickname ? { nickname: member.nickname } : {}),
      ...(member.age !== undefined ? { age: member.age } : {}),
      ...(member.birthday ? { birthday: member.birthday } : {}),
      ...(member.hometown ? { hometown: member.hometown } : {}),
      ...(member.bloodType ? { bloodType: member.bloodType } : {}),
      ...(member.heightCm ? { heightCm: member.heightCm } : {}),
      ...(member.agency ? { agency: member.agency } : {}),
      ...(member.bio ? { bio: member.bio } : {}),
      ...(member.xUrl ? { xUrl: member.xUrl } : {}),
      ...(member.source ? { source: member.source } : {}),
      ...noteSummary(member.group, member.name),
    }));
    respond(true, { group, members }, undefined);
  }),

  // A member's face photo (only one the user put in the photos folder).
  "idolmaster.photo": guarded(async (params, respond) => {
    const group = str(params, "group");
    const id = typeof params.id === "number" ? params.id : Number(str(params, "id"));
    if (!group || !Number.isFinite(id)) {
      fail(respond, "グループと番号を指定してください");
      return;
    }
    const photo = await readIdolPhoto(group, id);
    if (!photo) {
      respond(true, { found: false }, undefined);
      return;
    }
    // With maxSide a small JPEG (made once, kept on the PC) goes out instead of the original, and its version with it.
    const maxSide =
      typeof params.maxSide === "number"
        ? Math.min(Math.max(Math.trunc(params.maxSide), 64), 1600)
        : 0;
    const small = maxSide > 0 ? await smallIdolPhoto(group, id, maxSide) : undefined;
    respond(
      true,
      small
        ? { found: true, mime: "image/jpeg", base64: small.base64, version: small.version }
        : { found: true, ...photo },
      undefined,
    );
  }),

  // Wikidata (CC0): search a name, look at the facts of one item, then write it into the roster file.
  "idolmaster.wikidata.search": guarded(async (params, respond) => {
    const query = str(params, "query");
    if (!query) {
      fail(respond, "名前を入力してください");
      return;
    }
    respond(true, { candidates: await searchWikidata(query) }, undefined);
  }),

  "idolmaster.wikidata.preview": guarded(async (params, respond) => {
    const qid = str(params, "qid");
    if (!qid || !isQid(qid)) {
      fail(respond, "Wikidata の番号が正しくありません");
      return;
    }
    const person = await readWikidataPerson(qid);
    respond(true, { person, line: wikidataRosterLine(person) }, undefined);
  }),

  // Fill the roster from Wikidata: check first (writes nothing), then apply what the check kept.
  "idolmaster.wikidata.bulk.start": guarded(async (params, respond) => {
    respond(true, { state: startBulkCheck({ group: str(params, "group") }) }, undefined);
  }),
  "idolmaster.wikidata.bulk.status": guarded(async (_params, respond) => {
    respond(true, { state: bulkState() }, undefined);
  }),
  "idolmaster.wikidata.bulk.cancel": guarded(async (_params, respond) => {
    respond(true, { state: cancelBulk() }, undefined);
  }),
  "idolmaster.wikidata.bulk.apply": guarded(async (_params, respond) => {
    const result = await applyBulk();
    respond(true, { ...result, state: bulkState() }, undefined);
  }),

  // A group's information source: the member-list page the user pastes (read by its structure, never around a bot check).
  "idolmaster.source.list": guarded(async (_params, respond) => {
    respond(
      true,
      { sources: await listSources(), presets: SOURCE_PRESETS, state: sourceState() },
      undefined,
    );
  }),
  "idolmaster.source.set": guarded(async (params, respond) => {
    respond(
      true,
      {
        sources: await saveSources(str(params, "group") ?? "", splitUrls(str(params, "url") ?? "")),
      },
      undefined,
    );
  }),
  "idolmaster.source.remove": guarded(async (params, respond) => {
    respond(true, { sources: await removeSource(str(params, "group") ?? "") }, undefined);
  }),
  "idolmaster.source.start": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const url =
      str(params, "url") ??
      (await listSources()).find((source) => source.group === group)?.url ??
      "";
    if (!group || !url) {
      fail(respond, "グループと、メンバー一覧のページの URL を指定してください");
      return;
    }
    // A pasted address is kept for next time.
    if (str(params, "url")) await saveSource(group, url);
    respond(true, { state: startSourceCheck({ group, url }) }, undefined);
  }),
  "idolmaster.source.status": guarded(async (_params, respond) => {
    respond(true, { state: sourceState() }, undefined);
  }),
  "idolmaster.source.cancel": guarded(async (_params, respond) => {
    respond(true, { state: cancelSource() }, undefined);
  }),
  // The waiting line for imports: add one (the notes were agreed to in the app), look at the line, stop one, clear the finished.
  "idolmaster.source.queue.add": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    // one address, or several (one under another): each one is a line of the waiting line
    const pasted = splitUrls(str(params, "url") ?? "");
    const urls =
      pasted.length > 0
        ? pasted
        : (await listSources())
            .filter((source) => source.group === group)
            .map((source) => source.url);
    if (!group || urls.length === 0) {
      fail(respond, "グループと、メンバー一覧のページの URL を指定してください");
      return;
    }
    if (pasted.length > 0) await saveSources(group, pasted);
    const jobs = [];
    const refused: string[] = [];
    for (const url of urls) {
      try {
        jobs.push(
          enqueueSource({
            group,
            url,
            savePhotos: params.savePhotos !== false,
            addMissing: params.addMissing !== false,
          }),
        );
      } catch (error) {
        refused.push(`${url}：${error instanceof Error ? error.message : String(error)}`);
      }
    }
    if (jobs.length === 0) {
      fail(respond, refused.join("\n") || "待機に入れられませんでした");
      return;
    }
    respond(true, { job: jobs[0], jobs, refused, queue: sourceQueue() }, undefined);
  }),
  "idolmaster.source.queue.addall": guarded(async (params, respond) => {
    respond(
      true,
      await enqueueAllSources({
        savePhotos: params.savePhotos !== false,
        addMissing: params.addMissing !== false,
      }),
      undefined,
    );
  }),
  "idolmaster.source.queue.list": guarded(async (_params, respond) => {
    respond(true, { queue: sourceQueue() }, undefined);
  }),
  "idolmaster.source.queue.cancel": guarded(async (params, respond) => {
    respond(true, { queue: cancelSourceJob(str(params, "id") ?? "") }, undefined);
  }),
  "idolmaster.source.queue.clear": guarded(async (_params, respond) => {
    respond(true, { queue: clearSourceQueue() }, undefined);
  }),
  "idolmaster.source.apply": guarded(async (params, respond) => {
    const result = await applySource({
      savePhotos: params.savePhotos !== false,
      addMissing: params.addMissing === true,
    });
    respond(true, { ...result, state: sourceState() }, undefined);
  }),

  // Merging: groups into one new group (the same person becomes one person; the old groups and their picture folders are
  // deleted after one backup), or categories into one. With "plan": true nothing is changed and the counts are returned.
  "idolmaster.group.merge": guarded(async (params, respond) => {
    const groups = Array.isArray(params.groups)
      ? params.groups.filter((one): one is string => typeof one === "string")
      : [];
    const name = str(params, "name") ?? "";
    if (params.plan === true) {
      respond(true, { plan: planGroupMerge(groups, name) }, undefined);
      return;
    }
    respond(
      true,
      {
        result: await mergeGroups({ groups, newName: name, category: str(params, "category") }),
        categories: idolCategories(),
      },
      undefined,
    );
  }),
  "idolmaster.category.merge": guarded(async (params, respond) => {
    const names = Array.isArray(params.names)
      ? params.names.filter((one): one is string => typeof one === "string")
      : [];
    const backup = (await createFanBookBackup()).file;
    respond(
      true,
      {
        result: await mergeIdolCategories(names, str(params, "name") ?? ""),
        backup,
        categories: idolCategories(),
      },
      undefined,
    );
  }),

  // Free additions to the fan book: a category, a group inside it, a member inside a group, a member's photo.
  "idolmaster.category.add": guarded(async (params, respond) => {
    await addCategory(str(params, "name") ?? "");
    respond(true, { categories: idolCategories() }, undefined);
  }),
  // Edit the tree: rename / move / put under a big item / delete a category; move / delete a group; delete a member.
  "idolmaster.category.edit": guarded(async (params, respond) => {
    const name = str(params, "name") ?? "";
    const action = str(params, "action");
    if (action === "move") await moveIdolCategory(name, Number(params.direction) || 1);
    else if (action === "moveTo") await moveIdolCategoryTo(name, Number(params.index));
    else if (action === "parent") await setIdolCategoryParent(name, str(params, "value") ?? "");
    else if (action === "rename") await renameIdolCategory(name, str(params, "value") ?? "");
    else if (action === "remove") await removeIdolCategory(name);
    else {
      fail(respond, "操作が正しくありません");
      return;
    }
    respond(true, { categories: idolCategories() }, undefined);
  }),
  "idolmaster.group.edit": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const action = str(params, "action");
    if (action === "move") await moveIdolGroup(group, Number(params.direction) || 1);
    else if (action === "moveTo") await moveIdolGroupTo(group, Number(params.index));
    else if (action === "remove") await removeIdolGroup(group);
    else {
      fail(respond, "操作が正しくありません");
      return;
    }
    respond(true, { categories: idolCategories() }, undefined);
  }),
  "idolmaster.member.remove": guarded(async (params, respond) => {
    await removeIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : Number(str(params, "id")),
    );
    respond(true, { categories: idolCategories() }, undefined);
  }),
  // Picture search for one member of the fan book (Commons + Openverse): look at the list, then save the picked one.
  "idolmaster.image.search": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const member = findIdolMember(group, typeof params.id === "number" ? params.id : -1);
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const query = (str(params, "query") ?? "").trim() || member.name;
    // a person from Wikidata names her item in the source of the roster line: her Commons category gives sure pictures
    const qid = /Wikidata (Q\d+)/.exec(member.source ?? "")?.[1];
    respond(
      true,
      {
        query,
        page: Number(params.page) || 1,
        images: await searchPersonImages(query, Number(params.page) || 1, qid),
      },
      undefined,
    );
  }),
  // Search by a name over the sites registered in the user's Google Cloud search app (no member needed): the names of
  // the hit pages, to add members to a group or groups to a category.
  "idolmaster.web.search": guarded(async (params, respond) => {
    const query = (str(params, "query") ?? "").trim();
    const page = Number(params.page) || 1;
    respond(
      true,
      { query, page, images: query ? await searchVertexImages(query, page) : [] },
      undefined,
    );
  }),
  "idolmaster.image.save": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const member = findIdolMember(group, typeof params.id === "number" ? params.id : -1);
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    // Several pictures at once (`images`), or one (`url`). With firstIsMain the first becomes the main photo and the
    // rest are extra photos; otherwise every one is an extra photo (asExtra keeps the single-picture form working).
    type Wanted = {
      url?: unknown;
      title?: unknown;
      license?: unknown;
      creator?: unknown;
      source?: unknown;
      pageUrl?: unknown;
    };
    const wanted: Wanted[] = Array.isArray(params.images)
      ? (params.images as Wanted[]).slice(0, 12)
      : [
          {
            url: params.url,
            title: params.title,
            license: params.license,
            creator: params.creator,
            source: params.source,
            pageUrl: params.pageUrl,
          },
        ];
    const firstIsMain = Array.isArray(params.images)
      ? params.firstIsMain === true
      : params.asExtra !== true;
    const text = (value: unknown): string => (typeof value === "string" ? value : "");
    let saved = 0;
    let failed = 0;
    let lastFile = "";
    for (const [position, one] of wanted.entries()) {
      try {
        const image = await downloadPersonImage(text(one.url));
        const asMain = firstIsMain && position === 0;
        lastFile = asMain
          ? await saveIdolPhoto(member, image.mime, image.data)
          : `extra:${await addExtraPhoto(member, image.mime, image.data)}`;
        await recordPhotoCredit(
          member.group,
          member.name,
          {
            title: text(one.title),
            license: text(one.license),
            creator: text(one.creator),
            source: text(one.source),
            pageUrl: text(one.pageUrl),
          },
          asMain ? undefined : lastFile,
        );
        saved += 1;
      } catch (error) {
        failed += 1;
        if (wanted.length === 1) throw error;
      }
    }
    if (saved === 0) {
      fail(respond, "画像を保存できませんでした");
      return;
    }
    respond(true, { saved, failed, file: lastFile }, undefined);
  }),

  // The anime / photo switch for everything that is generated (kept on the PC, the same on every screen).
  "studio.style.get": guarded(async (_params, respond) => {
    respond(true, { style: await readGenStyle() }, undefined);
  }),
  "studio.style.set": guarded(async (params, respond) => {
    respond(true, { style: await writeGenStyle(params.style) }, undefined);
  }),

  // New people / people who left, from the official member lists of the groups (checked once a day, or now).
  "idolmaster.watch.status": guarded(async (_params, respond) => {
    maybeStartWatch();
    respond(true, await watchState(), undefined);
  }),
  "idolmaster.watch.check": guarded(async (_params, respond) => {
    void runWatchCheck().catch(() => undefined);
    respond(true, { ...(await watchState()), running: true }, undefined);
  }),
  // The user's decision about names of a group: add the new ones (name only), take out the ones who left, or ignore them.
  "idolmaster.watch.apply": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const names = (Array.isArray(params.names) ? params.names : [])
      .filter((n): n is string => typeof n === "string")
      .slice(0, 80);
    const action = str(params, "action");
    if (!group || names.length === 0) {
      fail(respond, "グループと名前を指定してください");
      return;
    }
    if (action === "add") {
      const lines = names.map((name) => manualMemberLine({ group, name }));
      lines.forEach((line, index) => assertAllowedRequest(names[index]!, line));
      await upsertRosterLines(lines);
      await settleWatch(group, names, false);
    } else if (action === "leave") {
      for (const member of idolMembers(group))
        if (names.includes(member.name)) await removeIdolMember(group, member.id);
      await settleWatch(group, names, false);
    } else if (action === "ignore") {
      await settleWatch(group, names, true);
    } else {
      fail(respond, "操作が正しくありません");
      return;
    }
    respond(true, { ...(await watchState()), categories: idolCategories() }, undefined);
  }),

  // Looks a member up on the internet by name: profile, career, works, the way of talking and the voice (a draft to read).
  "idolmaster.research": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const materials = await gatherMaterials((str(params, "name") ?? "").trim() || member.name);
    if (!materials.wikipedia && !materials.quotes && materials.works.length === 0) {
      respond(true, { found: false, sources: [] }, undefined);
      return;
    }
    const fields = parseResearch(await askLocalModel(researchPrompt(materials)));
    respond(true, { found: true, fields, sources: materials.sources }, undefined);
  }),
  // Writes the draft (as edited by the user) into the character made from the member and / or the member's memo.
  "idolmaster.research.apply": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const f = (params.fields ?? {}) as Record<string, unknown>;
    const field = (key: string, max: number): string =>
      typeof f[key] === "string"
        ? (f[key] as string).replace(/\r?\n/g, " ").trim().slice(0, max)
        : "";
    const fields = {
      intro: field("intro", 600),
      career: field("career", 900),
      works: field("works", 400),
      speech: field("speech", 500),
      voice: field("voice", 160),
    };
    const done: string[] = [];
    if (params.toCharacter === true) {
      const marker = `マスターデータ ${member.key} から作成`;
      const character = (await listCharacters()).find((one) => one.profile.includes(marker));
      if (!character) {
        fail(respond, "先に「🎤 キャラ図鑑に作る」で、図鑑にキャラを作ってください");
        return;
      }
      const section = [
        "【ネットで調べた内容】",
        fields.intro ? `自己紹介: ${fields.intro}` : "",
        fields.career ? `経歴: ${fields.career}` : "",
        fields.works ? `主な作品: ${fields.works}` : "",
        fields.speech ? `話し方: ${fields.speech}` : "",
        fields.voice ? `声: ${fields.voice}` : "",
      ]
        .filter(Boolean)
        .join(" ");
      assertAllowedRequest(character.name, section);
      const without = character.profile.replace(/\s*【ネットで調べた内容】[\s\S]*$/, "");
      await saveCharacter({
        ...character,
        profile: `${without} ${section}`.trim(),
        ...(fields.voice ? { voiceCaption: normalizeVoiceCaption(fields.voice) } : {}),
        updatedAt: Date.now(),
      });
      done.push("キャラ図鑑");
    }
    if (params.toMemo === true) {
      const note = getMemberNote(member.group, member.name);
      const lines = [
        fields.speech ? `話し方: ${fields.speech}` : "",
        fields.voice ? `声: ${fields.voice}` : "",
        fields.works ? `作品: ${fields.works}` : "",
        fields.intro ? `紹介: ${fields.intro}` : "",
      ].filter(Boolean);
      const kept = note.memo
        .split("\n")
        .filter((line) => !/^(話し方|声|作品|紹介)\s*[:：]/.test(line.trim()));
      await setMemberNote(
        member.group,
        member.name,
        [...kept, ...lines].join("\n").trim(),
        note.tags,
      );
      done.push("メモ");
    }
    respond(true, { saved: done }, undefined);
  }),

  // The character made from a member (if any) and the pictures it has, to show next to the member's photo.
  "idolmaster.member.character": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const marker = `マスターデータ ${member.key} から作成`;
    const character = (await listCharacters()).find((one) => one.profile.includes(marker));
    if (!character) {
      respond(true, { found: false }, undefined);
      return;
    }
    const items = (await galleryFiles(character))
      .slice(0, 6)
      .map(({ key, label }) => ({ key, label }));
    respond(
      true,
      { found: true, characterId: character.id, name: character.name, items },
      undefined,
    );
  }),

  // A picture for a member from an address (the picture itself, or a page that shows her): the main photo or one more.
  "idolmaster.photo.fromurl": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const picture = await pictureFromUrl(str(params, "url") ?? "");
    const asExtra = params.asExtra === true;
    const file = asExtra
      ? `extra:${await addExtraPhoto(member, picture.mime, picture.data)}`
      : await saveIdolPhoto(member, picture.mime, picture.data);
    await recordPhotoCredit(
      member.group,
      member.name,
      {
        title: "アドレスから入れた写真",
        license: "（確認してください）",
        creator: "",
        source: "url",
        pageUrl: picture.from,
      },
      asExtra ? file : undefined,
    );
    respond(true, { saved: true, file }, undefined);
  }),

  // Memo and own tags of a member (kept apart from the roster, so an import never overwrites them).
  "idolmaster.note.get": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    respond(true, getMemberNote(member.group, member.name), undefined);
  }),
  "idolmaster.note.set": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    respond(
      true,
      await setMemberNote(member.group, member.name, str(params, "memo") ?? "", params.tags),
      undefined,
    );
  }),

  // More pictures of one member: list / get / add / remove / make the main photo.
  "idolmaster.photo.extras": guarded(async (params, respond) => {
    const member = findIdolMember(
      str(params, "group") ?? "",
      typeof params.id === "number" ? params.id : -1,
    );
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const action = str(params, "action") ?? "list";
    const n = Number(params.index);
    if (action === "list") {
      respond(true, { items: await listExtraPhotos(member) }, undefined);
    } else if (action === "get") {
      const photo = await readExtraPhoto(member, n);
      respond(true, photo ? { found: true, ...photo } : { found: false }, undefined);
    } else if (action === "add") {
      const base64 = str(params, "base64") ?? "";
      if (base64.length > 9 * 1024 * 1024) {
        fail(respond, "画像が大きすぎます（6MB まで）");
        return;
      }
      respond(
        true,
        {
          index: await addExtraPhoto(
            member,
            str(params, "mime") ?? "",
            Buffer.from(base64, "base64"),
          ),
        },
        undefined,
      );
    } else if (action === "remove") {
      await removeExtraPhoto(member, n);
      respond(true, { items: await listExtraPhotos(member) }, undefined);
    } else if (action === "promote") {
      await promoteExtraPhoto(member, n);
      respond(true, { items: await listExtraPhotos(member) }, undefined);
    } else {
      fail(respond, "操作が正しくありません");
    }
  }),

  // Search the whole fan book, find people that look like the same person, and merge them.
  "idolmaster.search": guarded(async (params, respond) => {
    respond(true, { hits: await searchMembers(str(params, "query") ?? "") }, undefined);
  }),
  "idolmaster.duplicates": guarded(async (_params, respond) => {
    respond(true, { clusters: await findDuplicates() }, undefined);
  }),
  "idolmaster.merge": guarded(async (params, respond) => {
    const keep = params.keep as { group?: unknown; id?: unknown } | undefined;
    const others = (Array.isArray(params.remove) ? params.remove : []) as Array<{
      group?: unknown;
      id?: unknown;
    }>;
    if (!keep || typeof keep.group !== "string" || typeof keep.id !== "number") {
      fail(respond, "残す人を指定してください");
      return;
    }
    const result = await mergeMembers(
      { group: keep.group, id: keep.id },
      others.flatMap((one) =>
        typeof one.group === "string" && typeof one.id === "number"
          ? [{ group: one.group, id: one.id }]
          : [],
      ),
    );
    respond(true, { ...result, categories: idolCategories() }, undefined);
  }),

  // Backups of the fan book (roster, categories, removed list): make one, list them, put one back.
  "idolmaster.backup.create": guarded(async (_params, respond) => {
    respond(true, { ...(await createFanBookBackup()), dir: fanBookBackupDir() }, undefined);
  }),
  "idolmaster.backup.list": guarded(async (_params, respond) => {
    respond(true, { backups: await listFanBookBackups(), dir: fanBookBackupDir() }, undefined);
  }),
  "idolmaster.backup.restore": guarded(async (params, respond) => {
    const result = await restoreFanBookBackup(str(params, "file") ?? "");
    respond(true, { ...result, categories: idolCategories() }, undefined);
  }),
  "idolmaster.group.add": guarded(async (params, respond) => {
    await addGroup(
      str(params, "category") ?? "",
      str(params, "group") ?? "",
      str(params, "parent"),
    );
    respond(true, { categories: idolCategories() }, undefined);
  }),
  "idolmaster.member.add": guarded(async (params, respond) => {
    const line = manualMemberLine({
      group: str(params, "group") ?? "",
      name: str(params, "name") ?? "",
      description: str(params, "description"),
      nickname: str(params, "nickname"),
      generation: str(params, "generation"),
      birthday: str(params, "birthday"),
      hometown: str(params, "hometown"),
    });
    assertAllowedRequest(str(params, "name") ?? "", line);
    const result = await upsertRosterLine(line);
    respond(true, { ...result }, undefined);
  }),
  "idolmaster.photo.set": guarded(async (params, respond) => {
    const group = str(params, "group") ?? "";
    const member = findIdolMember(group, typeof params.id === "number" ? params.id : -1);
    if (!member) {
      fail(respond, "その人が名簿に見つかりません");
      return;
    }
    const base64 = str(params, "base64") ?? "";
    if (base64.length > 9 * 1024 * 1024) {
      fail(respond, "画像が大きすぎます（6MB まで）");
      return;
    }
    const file = await saveIdolPhoto(
      member,
      str(params, "mime") ?? "",
      Buffer.from(base64, "base64"),
    );
    respond(true, { saved: true, file }, undefined);
  }),

  // Put a group under a category ("48グループ" ...); a new category name makes the category.
  "idolmaster.category.set": guarded(async (params, respond) => {
    const group = str(params, "group");
    const category = str(params, "category");
    if (!group || !category) {
      fail(respond, "グループと分類の名前を指定してください");
      return;
    }
    await setGroupCategory(group, category);
    respond(true, { categories: idolCategories() }, undefined);
  }),

  // The group's official site (AKB48 for now): check (writes nothing), then apply (roster lines and photos).
  "idolmaster.official.start": guarded(async (params, respond) => {
    respond(
      true,
      { state: startOfficialCheck({ group: str(params, "group") ?? "AKB48" }) },
      undefined,
    );
  }),
  "idolmaster.official.status": guarded(async (_params, respond) => {
    respond(true, { state: officialState(), groups: officialGroups() }, undefined);
  }),
  "idolmaster.official.cancel": guarded(async (_params, respond) => {
    respond(true, { state: cancelOfficial() }, undefined);
  }),
  "idolmaster.official.apply": guarded(async (params, respond) => {
    const result = await applyOfficial({ savePhotos: params.savePhotos !== false });
    respond(true, { ...result, state: officialState() }, undefined);
  }),

  "idolmaster.wikidata.import": guarded(async (params, respond) => {
    const qid = str(params, "qid");
    if (!qid || !isQid(qid)) {
      fail(respond, "Wikidata の番号が正しくありません");
      return;
    }
    const person = await readWikidataPerson(qid);
    const line = wikidataRosterLine(person, {
      group: str(params, "group"),
      name: str(params, "name"),
    });
    assertAllowedRequest(person.name, line);
    const result = await upsertRosterLine(line);
    respond(true, { ...result, line }, undefined);
  }),

  // The fan book's database side: people (actors, gravure idols, singers) with a gender / age filter and a random
  // browse, and the characters of an anime work. Listing writes nothing; "add" writes what the user picked.
  "idolmaster.catalog.people": guarded(async (params, respond) => {
    // TMDB / MusicBrainz / AniList: people with a birthday, a birthplace and a picture where the source has them
    const outside = str(params, "source");
    if (outside && (EXTERNAL_SOURCES as readonly string[]).includes(outside)) {
      const gender = str(params, "gender");
      const people = await listExternalPeople({
        source: outside as ExternalSource,
        query: str(params, "query"),
        page: Math.floor((typeof params.offset === "number" ? params.offset : 0) / 20) + 1,
        gender: gender === "male" || gender === "female" ? gender : "any",
        ageMin: typeof params.ageMin === "number" ? params.ageMin : undefined,
        ageMax: typeof params.ageMax === "number" ? params.ageMax : undefined,
        adult: params.adult === true,
      });
      respond(
        true,
        {
          people: people.map((one) => ({
            source: one.source,
            key: `${one.source}:${one.id}`,
            title: one.id,
            name: one.name,
            gender: one.gender,
            ...(one.age !== undefined ? { age: one.age } : {}),
            imageUrl: one.thumbUrl,
            description: [one.place, one.note].filter(Boolean).join("・").slice(0, 100),
            ...(one.adult ? { adult: true } : {}),
          })),
        },
        undefined,
      );
      return;
    }
    const kind = str(params, "kind");
    if (!kind || !PERSON_KINDS.includes(kind as PersonKind)) {
      fail(respond, "探す種類を選んでください");
      return;
    }
    const gender = str(params, "gender");
    if (str(params, "source") === "wikipedia") {
      const hits = await browseWikipedia({
        kind: kind as PersonKind,
        gender: (gender === "male" || gender === "female" ? gender : "any") as GenderFilter,
        query: str(params, "query"),
        random: params.random === true,
        offset: typeof params.offset === "number" ? params.offset : 0,
        order: str(params, "order") === "young" ? "young" : "popular",
      });
      respond(
        true,
        {
          people: hits.map((hit) => ({
            source: "wikipedia",
            key: `wp:${hit.title}`,
            title: hit.title,
            name: hit.name,
            imageUrl: hit.imageUrl,
          })),
        },
        undefined,
      );
      return;
    }
    const people = await browsePeople({
      kind: kind as PersonKind,
      gender: (gender === "male" || gender === "female" ? gender : "any") as GenderFilter,
      ageMin: typeof params.ageMin === "number" ? params.ageMin : undefined,
      ageMax: typeof params.ageMax === "number" ? params.ageMax : undefined,
      query: str(params, "query"),
      random: params.random === true,
      offset: typeof params.offset === "number" ? params.offset : 0,
      order: str(params, "order") === "young" ? "young" : "popular",
      seed: String(Date.now()),
    });
    respond(true, { people }, undefined);
  }),
  // One list-row picture (a small image from a known picture host), as base64.
  "idolmaster.catalog.thumb": guarded(async (params, respond) => {
    const thumb = await fetchThumbnail(str(params, "url") ?? "");
    respond(true, thumb ? { found: true, ...thumb } : { found: false }, undefined);
  }),
  // Characters by name (not by work), over all anime; the group of an added one is the first work it appears in.
  "idolmaster.catalog.charsearch": guarded(async (params, respond) => {
    const query = (str(params, "query") ?? "").trim();
    if (!query) {
      fail(respond, "キャラの名前を入れてください");
      return;
    }
    const adult = params.adult === true;
    const found = (
      adult ? await searchAdultCharacters(query) : await searchCharacters(query)
    ).filter((hit) => hit.publicKind !== "group");
    const hits = found.filter((hit) => characterListable(hit, adult));
    respond(
      true,
      {
        // how many were left out because their age is written as under 18 (so that "none found" can be told from "all under 18")
        hiddenUnder18: found.length - hits.length,
        characters: hits.map((hit) => ({
          source: hit.source,
          id: hit.id,
          name: hit.name || hit.englishName,
          work: hit.works[0] ?? "",
          gender: hit.gender ?? "",
          age: hit.age ?? "",
          imageUrl: hit.imageUrl ?? "",
        })),
      },
      undefined,
    );
  }),
  // Groups to find by name (or in a list) and to add whole, with their members (all, or only the women / the men).
  "idolmaster.catalog.groups": guarded(async (params, respond) => {
    const groupGender = str(params, "gender");
    const groups = await browseGroups({
      gender: (groupGender === "male" || groupGender === "female"
        ? groupGender
        : "any") as GenderFilter,
      query: str(params, "query"),
      random: params.random === true,
      offset: typeof params.offset === "number" ? params.offset : 0,
    });
    respond(true, { groups }, undefined);
  }),
  "idolmaster.catalog.addgroup": guarded(async (params, respond) => {
    const qid = str(params, "qid") ?? "";
    const name = (str(params, "name") ?? "").trim();
    const category = str(params, "category") || "アイドル";
    if (!isQid(qid) || !name) {
      fail(respond, "グループを選んでください");
      return;
    }
    const lines: string[] = [];
    let skipped = 0;
    const memberGender = str(params, "gender");
    for (const memberQid of (
      await groupMembers(
        qid,
        (memberGender === "male" || memberGender === "female"
          ? memberGender
          : "any") as GenderFilter,
      )
    ).slice(0, 60)) {
      try {
        const person = await readWikidataPerson(memberQid);
        const line = wikidataRosterLine(person, { group: name });
        assertAllowedRequest(person.name, line);
        lines.push(line);
      } catch {
        skipped += 1;
      }
    }
    if (lines.length === 0) {
      fail(
        respond,
        "このグループのメンバーは、Wikidata に登録がありませんでした。メンバーを、自分で追加するか、公式サイトの URL から取り込んでください",
      );
      return;
    }
    try {
      await addGroup(category, name);
    } catch {
      // the group exists already: the members are added to it
    }
    const result = await upsertRosterLines(lines);
    await setGroupCategory(name, category);
    respond(true, { ...result, group: name, skipped, categories: idolCategories() }, undefined);
  }),
  "idolmaster.catalog.characters": guarded(async (params, respond) => {
    const source = str(params, "source") === "jikan" ? "jikan" : "anilist";
    const adult = params.adult === true;
    const found = await titleCharacters(source, Number(params.id));
    const hits = found.filter((hit) => characterListable(hit, adult));
    respond(
      true,
      {
        // how many were left out because their age is written as under 18 (so that "none" can be told from "all under 18")
        hiddenUnder18: found.length - hits.length,
        totalFound: found.length,
        characters: hits.map((hit) => ({
          id: hit.id,
          name: hit.name || hit.englishName,
          gender: hit.gender ?? "",
          age: hit.age ?? "",
          imageUrl: hit.imageUrl ?? "",
          description: hit.description.slice(0, 120),
        })),
        adultOnlyKnownAge: adult,
      },
      undefined,
    );
  }),
  "idolmaster.catalog.add": guarded(async (params, respond) => {
    const category = str(params, "category") ?? "";
    const group = str(params, "group") ?? "";
    if (!category || (!group && str(params, "kind") !== "found")) {
      fail(respond, "分類とグループを入れてください");
      return;
    }
    const lines: string[] = [];
    let skipped = 0;
    if (str(params, "kind") === "people") {
      const qids = (Array.isArray(params.qids) ? params.qids : []).filter(isQid).slice(0, 20);
      for (const qid of qids) {
        try {
          const person = await readWikidataPerson(qid);
          // The gravure kind leaves out a person written as under 18 (an unknown age is fine).
          const line = wikidataRosterLine(person, { group });
          assertAllowedRequest(person.name, line);
          lines.push(line);
        } catch {
          skipped += 1;
        }
      }
    } else if (str(params, "kind") === "external") {
      const outside = str(params, "source") as ExternalSource;
      if (!(EXTERNAL_SOURCES as readonly string[]).includes(outside)) {
        fail(respond, "情報元が正しくありません");
        return;
      }
      for (const id of (Array.isArray(params.ids) ? params.ids : []).map(String).slice(0, 30)) {
        try {
          const person = await externalPersonDetail(outside, id);
          // one marked adult needs a birthday that shows 18 or more
          const line = externalRosterLine(person, group);
          assertAllowedRequest(person.name, line);
          lines.push(line);
        } catch {
          skipped += 1;
        }
      }
    } else if (str(params, "kind") === "wikipedia") {
      for (const entry of (Array.isArray(params.items) ? params.items : []).slice(0, 30)) {
        try {
          const row = entry as { title?: unknown; imageUrl?: unknown };
          const title = typeof row.title === "string" ? row.title.trim() : "";
          if (!title) throw new Error("題名がありません");
          const line = wikipediaRosterLine(
            { title, imageUrl: typeof row.imageUrl === "string" ? row.imageUrl : "" },
            group,
            [str(params, "personKind") ?? ""].filter(Boolean),
          );
          assertAllowedRequest(title, line);
          lines.push(line);
        } catch {
          skipped += 1;
        }
      }
    } else if (str(params, "kind") === "found") {
      // characters picked from a name search: each is added under the first work it appears in
      const adult = params.adult === true;
      for (const entry of (Array.isArray(params.found) ? params.found : []).slice(0, 30)) {
        try {
          const row = entry as { source?: unknown; id?: unknown };
          const hit = rememberedHit(String(row.source), Number(row.id));
          if (!hit || !characterListable(hit, adult)) throw new Error("見つかりません");
          const line = characterRosterLine(
            hit,
            hit.works[0] ?? "その他の作品",
            hit.source === "jikan" ? "Jikan" : "AniList",
          );
          assertAllowedRequest(hit.name, line);
          lines.push(line);
        } catch {
          skipped += 1;
        }
      }
    } else {
      const source = str(params, "source") === "jikan" ? "jikan" : "anilist";
      const adult = params.adult === true;
      const ids = new Set(
        (Array.isArray(params.ids) ? params.ids : []).map(Number).filter(Number.isFinite),
      );
      const hits = (await titleCharacters(source, Number(params.workId))).filter((hit) =>
        ids.has(hit.id),
      );
      for (const hit of hits) {
        try {
          if (!characterListable(hit, adult)) throw new Error("年齢が確認できません");
          const line = characterRosterLine(hit, group, source === "jikan" ? "Jikan" : "AniList");
          assertAllowedRequest(hit.name, line);
          lines.push(line);
        } catch {
          skipped += 1;
        }
      }
    }
    if (lines.length === 0) {
      fail(
        respond,
        skipped > 0
          ? `追加できる人がいませんでした（条件に合わない：${skipped}人）`
          : "追加する人を選んでください",
      );
      return;
    }
    const result = await upsertRosterLines(lines);
    // each added line belongs to a group (for characters found by name, the work of each one)
    const groupsOfLines = [
      ...new Set(lines.map((line) => line.split(" | ")[0]!.trim()).filter(Boolean)),
    ];
    for (const one of groupsOfLines) await setGroupCategory(one, category);
    respond(true, { ...result, skipped, categories: idolCategories() }, undefined);
  }),

  "idolmaster.import": guarded(async (params, respond) => {
    const group = str(params, "group");
    const id = typeof params.id === "number" ? params.id : Number(str(params, "id"));
    const member = group && Number.isFinite(id) ? findIdolMember(group, id) : undefined;
    if (!member) {
      fail(respond, "そのメンバーが見つかりません");
      return;
    }
    const baseDraft = idolCharacterDraft(member, str(params, "name"));
    // The same checks as the character creator: the user's age rule (blocked-terms.json) and the allowed-request
    // check. An empty age passes the age rule, so the answer says when the roster has no age for this person.
    assertAgeAllowed(member.age);
    assertAllowedRequest(baseDraft.name, baseDraft.look, baseDraft.profile);
    // The same member is not added twice under the same name.
    const marker = `マスターデータ ${member.key} から作成`;
    const library = await listCharacters();
    const existing = library.find(
      (one) => one.name === baseDraft.name && one.profile.includes(marker),
    );
    if (existing) {
      // The photos were chosen again on purpose: attach them to the character that is already there.
      // (a character made before the photos were carried over gets them now: the main photo unless others were chosen)
      const wanted = Array.isArray(params.photos) ? params.photos : [{ kind: "main" }];
      const attached =
        wanted.length > 0 && !existing.photoRef
          ? await attachMemberPhotos(existing.id, member, wanted)
          : 0;
      respond(
        true,
        { existing: true, characterId: existing.id, name: existing.name, photosAttached: attached },
        undefined,
      );
      return;
    }
    // What she wears and how she talks follows her group and category: the outfit of a character of the same group
    // that is already in the library is used first (the group looks matched), else the category's outfit.
    const style = groupStyleFor(member.group);
    const sibling = library.find(
      (one) => one.profile.includes(`マスターデータ ${member.group}:`) && one.spec?.outfit,
    );
    const outfit = sibling?.spec?.outfit ?? style?.outfit;
    const tone = [
      style ? `話し方: ${style.tone}` : "",
      ...toneLinesFromMemo(getMemberNote(member.group, member.name).memo),
    ]
      .filter(Boolean)
      .join(" ");
    const draft = {
      ...baseDraft,
      look: outfit ? `${baseDraft.look}, wearing ${outfit}` : baseDraft.look,
      profile: tone ? `${baseDraft.profile} ${tone}` : baseDraft.profile,
    };
    assertAllowedRequest(draft.name, draft.look, draft.profile);
    const created = await registerCharacter(draft);
    await saveCharacter({
      ...created,
      // a voice that fits the group's atmosphere (the voice is described in words, as in the character creator)
      ...(style?.voice ? { voiceCaption: normalizeVoiceCaption(style.voice) } : {}),
      spec: {
        gender: "女性",
        occupation: `アイドル（${member.group}）`,
        ...(outfit ? { outfit } : {}),
        ...(member.heightCm ? { heightCm: member.heightCm } : {}),
        ...(member.age !== undefined ? { age: member.age } : {}),
      },
      updatedAt: Date.now(),
    });
    // The photo of the fan book goes with her: the chosen ones (default: the main photo) become her reference photos.
    const choice = Array.isArray(params.photos) ? params.photos : [{ kind: "main" }];
    const photosAttached =
      choice.length > 0 ? await attachMemberPhotos(created.id, member, choice) : 0;
    const sheetJob =
      params.makeSheet === true && photosAttached > 0
        ? await startMediaJob("comic", "", {
            comicTask: "sheet",
            comicCharacterId: created.id,
            comicSheetView: comicSheetViewOf(undefined, undefined),
          })
        : undefined;
    respond(
      true,
      {
        existing: false,
        characterId: created.id,
        name: created.name,
        photosAttached,
        ...(sheetJob ? { jobId: sheetJob.id } : {}),
        ...(member.age === undefined ? { ageUnknown: true } : {}),
      },
      undefined,
    );
  }),

  "sample.custom.update": guarded(async (params, respond) => {
    const entry = await updateCustomSetting(str(params, "id") ?? "", {
      genre: params.genre,
      label: params.label,
      instruction: params.instruction,
    });
    if (!entry) {
      fail(respond, "その項目が見つかりません");
      return;
    }
    respond(true, { custom: entry }, undefined);
  }),

  "sample.custom.delete": guarded(async (params, respond) => {
    respond(true, { deleted: await deleteCustomSetting(str(params, "id") ?? "") }, undefined);
  }),

  // ---- avatar models (Live2D zip, VRM / glb, 3D source files) ----
  "avatar.model.upload": guarded(async (params, respond) => {
    const data = str(params, "dataBase64");
    if (!data || data.length > 4 * 1024 * 1024) {
      fail(respond, "1回に送るデータが大きすぎます（3MB まで）");
      return;
    }
    const result = await saveModelChunk({
      ownerId: str(params, "ownerId") ?? "",
      name: str(params, "name") ?? "",
      index: typeof params.index === "number" ? params.index : -1,
      total: typeof params.total === "number" ? params.total : 0,
      dataBase64: data,
      uploadId: str(params, "uploadId"),
    });
    respond(true, result, undefined);
  }),

  "avatar.model.list": guarded(async (params, respond) => {
    respond(true, { models: await listAvatarModels(str(params, "ownerId")) }, undefined);
  }),

  "avatar.model.delete": guarded(async (params, respond) => {
    respond(
      true,
      { deleted: await deleteAvatarModel(str(params, "ownerId") ?? "", str(params, "id") ?? "") },
      undefined,
    );
  }),

  // A 3D model (GLB) of the character, made from her sheet picture; it is added to her models.
  // ---- backups (zip, by date, kept on this PC): the PC's data, and the phone's zip
  "backup.list": guarded(async (_params, respond) => {
    respond(true, { backups: await listBackups() }, undefined);
  }),
  "backup.pc.create": guarded(async (params, respond) => {
    const task = await startPcBackup({ includeOutputs: params.includeOutputs !== false });
    respond(true, { task }, undefined);
  }),
  "backup.pc.restore": guarded(async (params, respond) => {
    // restoring replaces data: the app must say it was confirmed
    if (params.confirm !== true) {
      fail(respond, "復元は、確認してから行います");
      return;
    }
    try {
      respond(true, { task: await startPcRestore(str(params, "name") ?? "") }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "backup.status": guarded(async (params, respond) => {
    respond(true, { task: backupTask(str(params, "taskId") ?? "") ?? null }, undefined);
  }),
  "backup.phone.upload": guarded(async (params, respond) => {
    const data = str(params, "dataBase64");
    if (!data || data.length > 4 * 1024 * 1024) {
      fail(respond, "1回に送るデータが大きすぎます（3MB まで）");
      return;
    }
    try {
      respond(
        true,
        await savePhoneChunk({
          name: str(params, "name") ?? "",
          index: typeof params.index === "number" ? params.index : -1,
          total: typeof params.total === "number" ? params.total : 0,
          dataBase64: data,
        }),
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "backup.read": guarded(async (params, respond) => {
    try {
      const kind = str(params, "kind") === "pc" ? "pc" : "phone";
      respond(
        true,
        await readBackupChunk(
          kind,
          str(params, "name") ?? "",
          Number(params.offset) || 0,
          Number(params.length) || 1_000_000,
        ),
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "backup.delete": guarded(async (params, respond) => {
    respond(
      true,
      {
        deleted: await deleteBackup(
          str(params, "kind") === "pc" ? "pc" : "phone",
          str(params, "name") ?? "",
        ),
      },
      undefined,
    );
  }),

  // The poses and options of the motion comic (the app shows exactly these).
  "motion.catalog": guarded(async (_params, respond) => {
    respond(true, motionCatalog(), undefined);
  }),

  // --- Crie's development mode (settings switch, requests, review, apply) ---
  "crie.dev.get": guarded(async (_params, respond) => {
    respond(true, { enabled: await devEnabled(), requests: await listDevRequests() }, undefined);
  }),
  "crie.dev.set": guarded(async (params, respond) => {
    await setDevEnabled(params.enabled === true);
    respond(true, { enabled: await devEnabled() }, undefined);
  }),
  "crie.dev.request": guarded(async (params, respond) => {
    try {
      respond(
        true,
        {
          request: await createDevRequest({
            title: str(params, "title") ?? "",
            description: str(params, "description") ?? "",
            githubUrl: str(params, "githubUrl") ?? "",
          }),
        },
        undefined,
      );
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  "crie.dev.diff": guarded(async (params, respond) => {
    try {
      respond(true, await devDiff(str(params, "id") ?? ""), undefined);
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  "crie.dev.apply": guarded(async (params, respond) => {
    try {
      respond(true, { request: await applyDevRequest(str(params, "id") ?? "") }, undefined);
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  "crie.dev.reject": guarded(async (params, respond) => {
    try {
      respond(true, { request: await rejectDevRequest(str(params, "id") ?? "") }, undefined);
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  // The concierge (the mini character that helps with the app).
  "concierge.ask": guarded(async (params, respond) => {
    const message = (str(params, "message") ?? "").trim().slice(0, 500);
    if (!message) {
      fail(respond, "聞きたいことを書いてください");
      return;
    }
    const history = (Array.isArray(params.history) ? params.history : [])
      .slice(-6)
      .flatMap((row): Array<{ role: "user" | "assistant"; text: string }> => {
        const r = row as { role?: unknown; text?: unknown };
        return (r.role === "user" || r.role === "assistant") && typeof r.text === "string"
          ? [{ role: r.role, text: r.text.slice(0, 400) }]
          : [];
      });
    const { system, user } = conciergePrompt(message, history);
    try {
      respond(
        true,
        parseConciergeReply(
          await askLocalModel(`${system}

${user}`),
        ),
        undefined,
      );
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err), ErrorCodes.UNAVAILABLE);
    }
  }),
  "motion.branch": guarded(async (params, respond) => {
    try {
      const result = await startMotionBranch({
        jobId: str(params, "jobId") ?? "",
        template: str(params, "template") ?? "",
        ...(typeof params.atSec === "number" ? { atSec: params.atSec } : {}),
        ...(typeof params.seconds === "number" ? { seconds: params.seconds } : {}),
        ...(str(params, "request") ? { request: str(params, "request") as string } : {}),
      });
      respond(true, result, undefined);
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  "motion.custom.list": guarded(async (_params, respond) => {
    respond(true, await listCustomMotions(), undefined);
  }),
  "motion.custom.add": guarded(async (params, respond) => {
    try {
      respond(true, await addCustomMotion(params as Record<string, unknown>), undefined);
    } catch (err) {
      fail(respond, err instanceof Error ? err.message : String(err));
    }
  }),
  "motion.custom.remove": guarded(async (params, respond) => {
    respond(true, { removed: await removeCustomMotion(str(params, "id") ?? "") }, undefined);
  }),

  // The mini character's pictures (the helper walking about the app) from the character's sheet.
  "comic.character.mini": guarded(async (params, respond) => {
    try {
      const job = await startMediaJob("comic", "", {
        comicTask: "mini",
        comicCharacterId: str(params, "characterId") ?? "",
      });
      respond(true, summary(job), undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  }),

  "model3d.generate": guarded(async (params, respond) => {
    const characterId = str(params, "characterId") ?? "";
    try {
      const job = await startMediaJob("comic", "", {
        comicTask: "model3d",
        comicCharacterId: characterId,
      });
      respond(true, summary(job), undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  }),

  "avatar.model.local.list": guarded(async (_params, respond) => {
    respond(true, { files: await listLocalModelFiles() }, undefined);
  }),
  "avatar.model.import": guarded(async (params, respond) => {
    try {
      respond(
        true,
        { model: await importLocalModel(str(params, "ownerId") ?? "", str(params, "name") ?? "") },
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "avatar.model.files": guarded(async (params, respond) => {
    try {
      respond(
        true,
        { files: await listModelFiles(str(params, "ownerId") ?? "", str(params, "id") ?? "") },
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "avatar.model.read": guarded(async (params, respond) => {
    try {
      respond(
        true,
        await readModelChunk(
          str(params, "ownerId") ?? "",
          str(params, "id") ?? "",
          str(params, "path") ?? "",
          Number(params.offset) || 0,
          Number(params.length) || 1_000_000,
        ),
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "avatar.model.deactivate": guarded(async (params, respond) => {
    await deactivateAvatarModels(str(params, "ownerId") ?? "");
    respond(true, { ok: true }, undefined);
  }),

  "avatar.model.activate": guarded(async (params, respond) => {
    const model = await activateAvatarModel(str(params, "ownerId") ?? "", str(params, "id") ?? "");
    if (!model) {
      fail(respond, "そのモデルが見つかりません");
      return;
    }
    respond(true, { model }, undefined);
  }),

  // ---- face swap ----
  // A file for the swap arrives in pieces (about 1 MB each); the last piece completes it.
  "faceswap.upload": guarded(async (params, respond) => {
    const data = str(params, "dataBase64");
    if (!data || data.length > 4 * 1024 * 1024) {
      fail(respond, "1回に送るデータが大きすぎます（3MB まで）");
      return;
    }
    const result = await saveSwapChunk({
      name: str(params, "name") ?? "",
      index: typeof params.index === "number" ? params.index : -1,
      total: typeof params.total === "number" ? params.total : 0,
      dataBase64: data,
    });
    respond(true, result, undefined);
  }),

  "faceswap.inputs": guarded(async (_params, respond) => {
    respond(true, { inputs: await listSwapInputs(), dir: swapInputDir() }, undefined);
  }),

  "faceswap.input.delete": guarded(async (params, respond) => {
    respond(true, { deleted: await deleteSwapInput(str(params, "inputId") ?? "") }, undefined);
  }),

  // Length, size and frame rate of a file (the app shows them before the estimate).
  "faceswap.probe": guarded(async (params, respond) => {
    const file = resolveInputFile(str(params, "inputId"));
    if (!file) {
      fail(respond, "ファイルが見つかりません");
      return;
    }
    respond(true, await probeMedia(file), undefined);
  }),

  // The faces in one moment of the video, cut out and numbered left to right (assign a person to each).
  "faceswap.faces": guarded(async (params, respond) => {
    const target = str(params, "target") ?? "";
    let file = resolveInputFile(target);
    if (!file && /^job:[a-f0-9]{6,32}$/.test(target))
      file = (await getMediaJob(target.slice(4)))?.file;
    if (!file) {
      fail(respond, "ファイルが見つかりません");
      return;
    }
    const atSec = nonNegative(params.atSec) ?? 0;
    const faces = await detectSwapFaces(file, atSec, path.join(swapInputDir(), ".faces"));
    respond(true, { faces }, undefined);
  }),

  // A finished swap, one piece at a time (a long film does not fit one message).
  "faceswap.result.chunk": guarded(async (params, respond) => {
    const job = await getMediaJob(str(params, "jobId") ?? "");
    if (!job || job.kind !== "faceswap" || job.status !== "done" || !job.file) {
      fail(respond, "まだ完成していないか、見つかりません");
      return;
    }
    const offset =
      typeof params.offset === "number" && params.offset >= 0 ? Math.floor(params.offset) : 0;
    const length = Math.min(
      3 * 1024 * 1024,
      typeof params.length === "number" && params.length > 0
        ? Math.floor(params.length)
        : 3 * 1024 * 1024,
    );
    const handle = await fs.open(job.file, "r");
    try {
      const size = (await handle.stat()).size;
      const buffer = Buffer.alloc(Math.max(0, Math.min(length, size - offset)));
      await handle.read(buffer, 0, buffer.length, offset);
      respond(
        true,
        { size, offset, mime: job.mime, dataBase64: buffer.toString("base64") },
        undefined,
      );
    } finally {
      await handle.close();
    }
  }),

  // ---- serial comics ----
  "comic.series.list": guarded(async (_params, respond) => {
    const all = await listComicSeries();
    for (const series of all) series.cast = await resolveCast(series.cast);
    respond(true, { series: all }, undefined);
  }),

  "comic.series.get": guarded(async (params, respond) => {
    const series = await getComicSeries(str(params, "seriesId") ?? "");
    if (!series) {
      fail(respond, "作品が見つかりません");
      return;
    }
    series.cast = await resolveCast(series.cast);
    respond(true, { series }, undefined);
  }),

  // A new series; without a cast the chat model writes one from the premise (takes ~30 s).
  // Every cast member goes into the character library.
  "comic.series.create": guarded(async (params, respond) => {
    const title = str(params, "title");
    const premise = str(params, "premise") ?? "";
    if (!title && !premise) {
      fail(respond, "題名か設定を入力してください");
      return;
    }
    const series = newComicSeries({
      title: title ?? "",
      genre: str(params, "genre") ?? "",
      premise,
      rating: COMIC_RATINGS.find((r) => r === str(params, "rating")) ?? "adult",
      imageModel: IMAGE_MODELS.find((value) => value === str(params, "imageModel")),
      cast: Array.isArray(params.cast) ? params.cast : [],
    });
    assertAllowedRequest(series.title, series.premise, ...series.cast.map((c) => c.look));
    if (series.cast.length === 0) series.cast = await writeComicCast(series);
    // Library characters picked in the app join as they are.
    const picked = Array.isArray(params.characterIds) ? params.characterIds : [];
    for (const id of picked) {
      const entry = typeof id === "string" ? await getCharacter(id) : undefined;
      if (entry && !series.cast.some((c) => c.characterId === entry.id)) {
        series.cast.push({
          name: entry.name,
          look: entry.look,
          profile: entry.profile,
          characterId: entry.id,
        });
      }
    }
    series.cast = await linkCast(series.cast);
    await saveComicSeries(series);
    series.cast = await resolveCast(series.cast);
    respond(true, { series }, undefined);
  }),

  // Title, premise, rating, model, and the cast (names, looks, profiles) as edited in the app;
  // edits to linked characters go to the library.
  "comic.series.update": guarded(async (params, respond) => {
    const series = await getComicSeries(str(params, "seriesId") ?? "");
    if (!series) {
      fail(respond, "作品が見つかりません");
      return;
    }
    const title = str(params, "title");
    if (title) series.title = title.slice(0, 40);
    if (typeof params.premise === "string") series.premise = params.premise.trim().slice(0, 1500);
    if (typeof params.genre === "string") series.genre = params.genre.trim().slice(0, 40);
    const rating = COMIC_RATINGS.find((r) => r === str(params, "rating"));
    if (rating) series.rating = rating;
    const model = IMAGE_MODELS.find((value) => value === str(params, "imageModel"));
    if (model) series.imageModel = model;
    if (Array.isArray(params.cast)) {
      const edited = params.cast
        .map(cleanCharacter)
        .filter((c) => c.name && c.look)
        .slice(0, MAX_COMIC_CAST);
      for (const member of edited) {
        const entry = member.characterId ? await getCharacter(member.characterId) : undefined;
        if (entry) await saveCharacter(applyCharacterEdit(entry, member));
      }
      series.cast = await linkCast(edited);
    }
    if (params.regenerateCast === true) series.cast = await linkCast(await writeComicCast(series));
    assertAllowedRequest(series.title, series.premise, ...series.cast.map((c) => c.look));
    series.updatedAt = Date.now();
    await saveComicSeries(series);
    series.cast = await resolveCast(series.cast);
    respond(true, { series }, undefined);
  }),

  // Puts a library character into a series (or takes one out).
  "comic.series.cast": guarded(async (params, respond) => {
    const series = await getComicSeries(str(params, "seriesId") ?? "");
    const entry = await getCharacter(str(params, "characterId") ?? "");
    if (!series || !entry) {
      fail(respond, "作品か登場人物が見つかりません");
      return;
    }
    if (params.remove === true) {
      series.cast = series.cast.filter((c) => c.characterId !== entry.id);
    } else if (!series.cast.some((c) => c.characterId === entry.id)) {
      series.cast.push({
        name: entry.name,
        look: entry.look,
        profile: entry.profile,
        characterId: entry.id,
      });
    }
    series.updatedAt = Date.now();
    await saveComicSeries(series);
    series.cast = await resolveCast(series.cast);
    respond(true, { series }, undefined);
  }),

  "comic.series.delete": guarded(async (params, respond) => {
    respond(true, { deleted: await deleteComicSeries(str(params, "seriesId") ?? "") }, undefined);
  }),

  // Starts the next episode (request: what should happen; empty = the writer decides).
  "comic.episode.generate": guarded(async (params, respond) => {
    const series = await getComicSeries(str(params, "seriesId") ?? "");
    if (!series) {
      fail(respond, "作品が見つかりません");
      return;
    }
    const job = await startMediaJob("comic", str(params, "request") ?? "", {
      comicSeriesId: series.id,
      comicTask: "episode",
      imageModel: IMAGE_MODELS.find((value) => value === series.imageModel),
      fast: params.fast === true,
    });
    respond(true, summary(job), undefined);
  }),

  // Draws a character sheet (front, full body): by characterId, or seriesId + name.
  "comic.sheet.generate": guarded(async (params, respond) => {
    const entry = await characterFromParams(params);
    if (!entry) {
      fail(respond, "その登場人物が見つかりません");
      return;
    }
    const series = await getComicSeries(str(params, "seriesId") ?? "");
    const outpaint = outpaintOf(params.outpaint);
    const asked = IMAGE_MODELS.find((value) => value === str(params, "imageModel"));
    // The model chosen in the app is kept for the next redraw.
    if (asked && entry.imageModel !== asked)
      await saveCharacter({ ...entry, imageModel: asked, updatedAt: Date.now() });
    const job = await startMediaJob("comic", outpaint ? (str(params, "note") ?? "") : "", {
      comicTask: outpaint ? "outpaint" : params.avatar === true ? "avatar" : "sheet",
      ...(outpaint ? { comicOutpaint: outpaint } : {}),
      comicCharacterId: entry.id,
      comicSheetView: comicSheetViewOf(str(params, "view"), params.turnaround),
      comicSheetAdult: params.adult === true,
      comicSheetPose: str(params, "pose")?.slice(0, 200),
      ...(series ? { comicSeriesId: series.id } : {}),
      imageModel:
        asked ?? IMAGE_MODELS.find((value) => value === (series?.imageModel ?? entry.imageModel)),
    });
    respond(true, summary(job), undefined);
  }),

  // Keeps a finished sheet as the character's reference picture and makes their icon from it.
  "comic.sheet.choose": guarded(async (params, respond) => {
    const job = await getMediaJob(str(params, "jobId") ?? "");
    const entry = await getCharacter(job?.comic?.characterId ?? "");
    if (!job?.file || job.status !== "done" || !entry) {
      fail(respond, "設定画が見つかりません");
      return;
    }
    // A turnaround is for looking at; the front sheet stays the reference for training.
    if (job.comic?.view === "turnaround") {
      await saveCharacter({ ...entry, turnaroundJobId: job.id, updatedAt: Date.now() });
      respond(
        true,
        { character: await characterView((await getCharacter(entry.id)) ?? entry) },
        undefined,
      );
      return;
    }
    const icon = path.join(characterAssetDir(entry.id), "icon.png");
    await makeCharacterIcon(job.file, icon).catch(() => undefined);
    const lookChanged = entry.refImage !== job.file && entry.loraStatus === "ready";
    await saveCharacter({
      ...entry,
      refImage: job.file,
      sheetJobId: job.id,
      icon,
      updatedAt: Date.now(),
      // A new sheet is a new reference; the old LoRA still works until retrained.
      ...(lookChanged ? { loraStep: "設定画が変わりました。必要なら学習し直してください" } : {}),
    });
    respond(
      true,
      { character: await characterView((await getCharacter(entry.id)) ?? entry) },
      undefined,
    );
  }),

  // Draws one panel of an episode again and rebuilds its strips.
  "comic.panel.redraw": guarded(async (params, respond) => {
    const target = await getMediaJob(str(params, "jobId") ?? "");
    const panel = typeof params.panel === "number" ? Math.floor(params.panel) : 0;
    if (!target?.comic?.episodeNo || panel < 1) {
      fail(respond, "描き直す話かコマが見つかりません");
      return;
    }
    const series = await getComicSeries(target.comic.seriesId);
    const job = await startMediaJob("comic", "", {
      comicSeriesId: target.comic.seriesId,
      comicTask: "redraw",
      comicTargetJobId: target.id,
      comicPanel: panel,
      imageModel: IMAGE_MODELS.find((value) => value === series?.imageModel),
    });
    respond(true, summary(job), undefined);
  }),

  // ---- character library ----
  "comic.character.photo.upload": guarded(async (params, respond) => {
    const uploadId = str(params, "uploadId") ?? "";
    const index = typeof params.index === "number" ? params.index : -1;
    const data = str(params, "dataBase64") ?? "";
    await saveCharacterPhotoUpload(uploadId, index, data);
    respond(true, { index }, undefined);
  }),
  // The character creator: settings in Japanese (height in cm, body, hair, outfit, personality…)
  // become a look for the image model; a full-body sheet is drawn right away.
  "comic.character.create": guarded(async (params, respond) => {
    const name = str(params, "name");
    if (!name) {
      fail(respond, "名前を入力してください");
      return;
    }
    let spec = cleanSpec(params.spec);
    const sheetValues = cleanSheetValues(params.sheetValues);
    assertAgeAllowed(spec.age);
    assertAllowedRequest(...Object.values(sheetValues ?? {}));
    // The photo is read first (the local model sees pictures): what it shows fills the settings that
    // were left empty, and is told to the editor in words. Failing to read it is not an error.
    const inlinePhotos = (
      Array.isArray(params.refImagesBase64) ? params.refImagesBase64 : [params.refImageBase64]
    ).filter((value): value is string => typeof value === "string" && value.length > 0);
    const uploadId = str(params, "photoUploadId");
    const uploadedPhotos = uploadId
      ? await characterPhotoUploads(uploadId, params.photoCount as number)
      : [];
    if (inlinePhotos.some((one) => one.length > MAX_SOURCE_IMAGE_BASE64)) {
      fail(respond, "写真が大きすぎます（6MBまで）");
      return;
    }
    const photoSources = uploadedPhotos.length
      ? [...inlinePhotos, ...uploadedPhotos]
      : inlinePhotos;
    const processedDir = photoSources.length
      ? await fs.mkdtemp(path.join(os.tmpdir(), "openclaw-character-photo-"))
      : "";
    const processedPhotos: string[] = [];
    let photoTraitsLine: string | undefined;
    const photoTraits: PhotoTraits[] = [];
    let mergedTraits: Awaited<ReturnType<typeof analyzePhoto>>;
    try {
      for (const [photoIndex, source] of photoSources.entries()) {
        const uploaded = uploadedPhotos.includes(source);
        const original = uploaded
          ? source
          : path.join(processedDir, `original-${photoIndex}.image`);
        if (!uploaded) await fs.writeFile(original, Buffer.from(source, "base64"));
        const isolated = path.join(processedDir, `subject-${photoIndex}.png`);
        await isolatePhotoSubject(original, isolated);
        processedPhotos.push(isolated);
        const traits = await analyzePhoto(isolated);
        if (!traits)
          throw new Error(
            `${photoIndex + 1}枚目の人物解析に失敗しました。画像解析モデルを確認してください`,
          );
        photoTraits.push(traits);
        if (photoIndex === 0) mergedTraits = traits;
        else if (str(params, "photoMode") !== "blend") {
          mergedTraits = {
            ...traits,
            ...Object.fromEntries(Object.entries(mergedTraits ?? {}).filter(([, value]) => value)),
          };
        }
      }
    } catch (error) {
      if (processedDir) await fs.rm(processedDir, { recursive: true, force: true });
      if (uploadId) await removeCharacterPhotoUploads(uploadId);
      throw error;
    }
    if (mergedTraits) {
      // Keep the first face; later full-body photos fill only missing attributes.
      {
        const fill = (
          key: "hair" | "hairstyle" | "eyes" | "skin" | "outfit" | "accessories" | "body",
          value: string | undefined,
        ) => (value && !spec[key] ? { [key]: value } : {});
        spec = cleanSpec({
          ...spec,
          ...fill("hair", mergedTraits.hair),
          ...fill("hairstyle", mergedTraits.hairstyle),
          ...fill("eyes", mergedTraits.eyes),
          ...fill("skin", mergedTraits.skin),
          ...fill("outfit", mergedTraits.outfit),
          ...fill("accessories", mergedTraits.accessories),
          ...(mergedTraits.body && (!spec.body || spec.body === "普通")
            ? { body: mergedTraits.body }
            : {}),
          ...(mergedTraits.face && !spec.features ? { features: mergedTraits.face } : {}),
        });
      }
    }
    // Preserve the first identity image. Rank the rest by visible information and complementary
    // role, so later full-body or clothing photos can enter the editor's bounded image context.
    const rawRoles = (Array.isArray(params.photoRoles) ? params.photoRoles : [])
      .filter((v): v is string => typeof v === "string")
      .map((v) => v.slice(0, 12));
    const remaining = photoTraits.map((_, index) => index).slice(1);
    const selected = [0];
    while (remaining.length && selected.length < 10) {
      const score = (index: number) => {
        const traits = photoTraits[index];
        const fields = [
          traits?.face,
          traits?.hair,
          traits?.hairstyle,
          traits?.eyes,
          traits?.skin,
          traits?.outfit,
          traits?.accessories,
          traits?.body,
        ];
        const newFields = fields.filter(
          (value, field) =>
            value &&
            selected.every(
              (picked) =>
                value !==
                [
                  photoTraits[picked]?.face,
                  photoTraits[picked]?.hair,
                  photoTraits[picked]?.hairstyle,
                  photoTraits[picked]?.eyes,
                  photoTraits[picked]?.skin,
                  photoTraits[picked]?.outfit,
                  photoTraits[picked]?.accessories,
                  photoTraits[picked]?.body,
                ][field],
            ),
        ).length;
        const role = rawRoles[index];
        return (
          newFields * 3 +
          fields.filter(Boolean).length +
          (traits?.kind?.includes("全身") ? 3 : 0) +
          (str(params, "photoMode") === "roles" &&
          role &&
          selected.every((picked) => rawRoles[picked] !== role)
            ? 8
            : 0)
        );
      };
      remaining.sort((a, b) => score(b) - score(a) || a - b);
      selected.push(remaining.shift()!);
    }
    const photoOrder = [...selected, ...remaining];
    if (photoTraits.length > 0) {
      const subjectMode = !["roles", "blend"].includes(str(params, "photoMode") ?? "");
      photoTraitsLine = photoOrder
        .map((index, position) => {
          const traits = photoTraits[index] ?? {};
          const visible =
            subjectMode && index !== 0
              ? { outfit: traits.outfit, accessories: traits.accessories, body: traits.body }
              : traits;
          return `Reference ${position + 1}: ${traitsForEditor(visible)}`;
        })
        .join(" ")
        .slice(0, 1800);
    }
    const written = await writeCharacterLook(name, spec);
    assertAllowedRequest(written.look, written.profile);
    const entry = await registerCharacter({ name, look: written.look, profile: written.profile });
    const model = IMAGE_MODELS.find((value) => value === str(params, "imageModel"));
    // The photos to make her from: kept in her folder; her front sheet is drawn from them.
    let photoRef: string | undefined;
    const photoRefs: string[] = [];
    for (const [photoIndex, sourceIndex] of photoOrder.entries()) {
      const isolated = processedPhotos[sourceIndex];
      if (!isolated) throw new Error("解析済みの写真が見つかりません");
      const dir = characterAssetDir(entry.id);
      await fs.mkdir(dir, { recursive: true });
      const file = path.join(
        dir,
        photoIndex === 0 ? "photo-ref.png" : `photo-ref-${photoIndex + 1}.png`,
      );
      await fs.copyFile(isolated, file);
      photoRefs.push(file);
      if (photoIndex === 0) photoRef = file;
    }
    const several = photoRefs.length > 1;
    const newVoiceId = str(params, "voiceId");
    const newVoiceCaption = str(params, "voiceCaption");
    const saved = {
      ...entry,
      spec,
      ...(sheetValues ? { sheetValues } : {}),
      ...(model ? { imageModel: model } : {}),
      ...(newVoiceId && /^voice-[a-z0-9-]{8,60}$/.test(newVoiceId) ? { voiceId: newVoiceId } : {}),
      ...(newVoiceCaption ? { voiceCaption: normalizeVoiceCaption(newVoiceCaption) } : {}),
      ...(photoRef
        ? {
            photoRef,
            photoStyle:
              (["photo", "anime", "illustration"] as const).find(
                (value) => value === str(params, "photoStyle"),
              ) ?? "photo",
            photoSource:
              str(params, "photoSource") === "anime" ? ("anime" as const) : ("photo" as const),
            // No picture shows a body: the sheet's body and clothes are chosen at random.
            ...(photoTraits.length > 0
              ? {
                  photoFaceOnly: !photoTraits.some((traits) =>
                    (traits.kind ?? "").includes("全身"),
                  ),
                }
              : {}),
            photoEngine:
              (["fast", "precise", "edit2509", "ipadapter"] as const).find(
                (value) => value === str(params, "photoEngine"),
              ) ?? "precise",
            ...(several
              ? {
                  photoRefs,
                  photoMode:
                    str(params, "photoMode") === "roles"
                      ? ("roles" as const)
                      : str(params, "photoMode") === "blend"
                        ? ("blend" as const)
                        : ("subject" as const),
                  photoRoles: photoOrder.map((index) => rawRoles[index] ?? "顔"),
                }
              : {}),
            photoQuality:
              str(params, "photoQuality") === "high" ? ("high" as const) : ("normal" as const),
            ...(photoTraitsLine ? { photoTraits: photoTraitsLine } : {}),
          }
        : {}),
      updatedAt: Date.now(),
    };
    await saveCharacter(saved);
    if (processedDir) await fs.rm(processedDir, { recursive: true, force: true });
    if (uploadId) await removeCharacterPhotoUploads(uploadId);
    const job = await startMediaJob("comic", "", {
      comicTask: "sheet",
      comicCharacterId: saved.id,
      comicSheetView: comicSheetViewOf(str(params, "view"), params.turnaround),
      comicSheetAdult: params.adult === true,
      comicSheetPose: str(params, "pose")?.slice(0, 200),
      imageModel: IMAGE_MODELS.find((value) => value === str(params, "imageModel")),
    });
    respond(true, { character: await characterView(saved), job: summary(job) }, undefined);
  }),

  // The model store: researched picture models that can be downloaded from the app.
  "model.store.list": guarded(async (_params, respond) => {
    respond(true, await listStore(), undefined);
  }),
  "model.store.download": guarded(async (params, respond) => {
    try {
      startStoreDownload(str(params, "id") ?? "");
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
      return;
    }
    respond(true, { started: true }, undefined);
  }),
  "model.store.cancel": guarded(async (params, respond) => {
    cancelStoreDownload(str(params, "id") ?? "");
    respond(true, { cancelled: true }, undefined);
  }),
  "model.store.delete": guarded(async (params, respond) => {
    try {
      await deleteStoreModel(str(params, "id") ?? "");
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
      return;
    }
    respond(true, { deleted: true }, undefined);
  }),
  // The user's Civitai API key (empty text removes it). It is never sent back.
  "model.store.civitai.key": guarded(async (params, respond) => {
    try {
      await setCivitaiKey(typeof params.key === "string" ? params.key : "");
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
      return;
    }
    respond(true, { saved: true }, undefined);
  }),

  // ---- library folders: name, icon, order, and which character is in which folder
  "comic.folder.list": guarded(async (_params, respond) => {
    const folders = [];
    for (const folder of await listFolders()) {
      folders.push({
        ...folder,
        iconBase64: folder.hasIcon ? await folderIconBase64(folder.id) : undefined,
      });
    }
    respond(true, { folders }, undefined);
  }),
  "comic.folder.create": guarded(async (params, respond) => {
    try {
      respond(true, { folder: await createFolder(params.name) }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "comic.folder.update": guarded(async (params, respond) => {
    try {
      respond(
        true,
        {
          folder: await updateFolder(params.id, {
            name: params.name,
            iconBase64: params.iconBase64,
          }),
        },
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "comic.folder.delete": guarded(async (params, respond) => {
    respond(true, { deleted: await deleteFolder(params.id) }, undefined);
  }),
  "comic.folder.reorder": guarded(async (params, respond) => {
    await reorderFolders(params.ids);
    respond(true, { ok: true }, undefined);
  }),
  "comic.character.move": guarded(async (params, respond) => {
    try {
      await moveCharacter(
        str(params, "characterId") ?? "",
        typeof params.folderId === "string" ? params.folderId : "",
      );
      respond(true, { ok: true }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "comic.character.reorder": guarded(async (params, respond) => {
    await reorderCharacters(params.ids);
    respond(true, { ok: true }, undefined);
  }),

  // ---- manga works (folders of volumes) and the characters of a volume
  "manga.work.list": guarded(async (_params, respond) => {
    respond(true, { works: await listWorks() }, undefined);
  }),
  "manga.work.rename": guarded(async (params, respond) => {
    try {
      respond(
        true,
        { work: await renameWork(str(params, "id") ?? "", str(params, "title") ?? "") },
        undefined,
      );
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  // Moves a volume to another work, or (no workId) into a new work named title.
  "manga.work.move": guarded(async (params, respond) => {
    try {
      const jobId = str(params, "jobId") ?? "";
      const book = await loadBook(jobId);
      if (!book) {
        fail(respond, "この巻の記録が見つかりません");
        return;
      }
      const work = await moveVolume(
        jobId,
        { workId: str(params, "workId"), title: str(params, "title") },
        book,
      );
      respond(true, { work }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),
  "manga.book.get": guarded(async (params, respond) => {
    const jobId = str(params, "jobId") ?? "";
    const book = await loadBook(jobId);
    if (!book) {
      fail(respond, "この巻の記録が見つかりません");
      return;
    }
    respond(
      true,
      {
        jobId,
        workId: book.workId,
        volume: book.volume,
        title: book.title,
        summary: book.summary,
        hook: book.hook,
        outline: book.outline,
        cast: book.cast.map((c) => ({
          name: c.name,
          profile: c.profile,
          look: c.look,
          characterId: c.characterId,
        })),
      },
      undefined,
    );
  }),
  // Adds one of a volume's (auto-made) characters to the character library, and links it in every
  // volume of the work so the next ones draw her from the library entry.
  "manga.book.register": guarded(async (params, respond) => {
    try {
      const jobId = str(params, "jobId") ?? "";
      const name = str(params, "name") ?? "";
      const book = await loadBook(jobId);
      const member = book?.cast.find((c) => c.name === name);
      if (!book || !member) {
        fail(respond, "この登場人物が見つかりません");
        return;
      }
      const entry = await registerCharacter(member);
      for (const other of await listBooks()) {
        if (other.jobId !== jobId && !(other.workId && other.workId === book.workId)) continue;
        if (!other.cast.some((c) => c.name === name && !c.characterId)) continue;
        await updateBook(other.jobId, {
          cast: other.cast.map((c) =>
            c.name === name && !c.characterId ? { ...c, characterId: entry.id } : c,
          ),
        });
      }
      respond(true, { characterId: entry.id, name: entry.name }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
    }
  }),

  // Every finished sheet picture of a character, newest first: the candidates stay until one is
  // chosen or deleted, whatever the app was doing meanwhile (the list does not live in the app).
  // Searches the anime / manga character databases by a character name, a work name or both.
  "comic.character.lookup": guarded(async (params, respond) => {
    const query = (str(params, "query") ?? "").trim();
    if (!query) {
      fail(respond, "作品名かキャラ名を入れてください");
      return;
    }
    if (params.groupId !== undefined) {
      const group = rememberedHit(str(params, "source") ?? "", Number(params.groupId));
      if (!group || group.publicKind !== "group") {
        fail(respond, "グループをもう一度検索して選んでください");
        return;
      }
      const members = await publicGroupMembers(group);
      respond(true, { hits: members, extra: group.extra ?? "" }, undefined);
      return;
    }
    // site "game": the visual-novel character database; otherwise AniList / Jikan
    const site = str(params, "site");
    const hits =
      site === "person" || site === "group"
        ? await searchPublicSubjects(query, site)
        : site === "vndb"
          ? await searchVndbCharacters(query)
          : site === "game"
            ? await searchBishoge(query)
            : str(params, "adult") === "adult"
              ? await searchAdultCharacters(query)
              : await searchCharacters(query);
    respond(
      true,
      { hits: hits.map((hit) => ({ ...hit, description: hit.description.slice(0, 400) })) },
      undefined,
    );
  }),

  // Works (anime / manga) that match the words, and the characters of one work.
  "comic.character.title.search": guarded(async (params, respond) => {
    const query = (str(params, "query") ?? "").trim();
    const adult =
      str(params, "adult") === "adult"
        ? "adult"
        : str(params, "adult") === "general"
          ? "general"
          : "all";
    const sort = str(params, "sort") === "newest" ? "newest" : "popular";
    // without words, the newest titles (or the adult ones) are listed
    if (!query && sort !== "newest" && adult === "all") {
      fail(respond, "作品名を入れるか、「新しい順」「アダルト」を選んでください");
      return;
    }
    respond(true, { titles: await searchTitles(query, fetch, { adult, sort }) }, undefined);
  }),
  "comic.character.title.characters": guarded(async (params, respond) => {
    const source = str(params, "source") === "jikan" ? "jikan" : "anilist";
    const hits = await titleCharacters(source, Number(params.id));
    respond(
      true,
      { hits: hits.map((hit) => ({ ...hit, description: hit.description.slice(0, 200) })) },
      undefined,
    );
  }),

  // waifu.im: tags, and pictures by tag (used as the reference picture of a new character).
  "comic.character.waifu.tags": guarded(async (_params, respond) => {
    respond(true, { tags: (await waifuTags()).slice(0, 300) }, undefined);
  }),
  "comic.character.waifu.images": guarded(async (params, respond) => {
    const tags = Array.isArray(params.tags)
      ? params.tags.filter((tag): tag is string => typeof tag === "string")
      : [];
    const adult =
      str(params, "adult") === "adult"
        ? "adult"
        : str(params, "adult") === "all"
          ? "all"
          : "general";
    respond(
      true,
      { images: await waifuImages({ tags, adult, page: Number(params.page) || 1 }) },
      undefined,
    );
  }),

  // Reads one hit of the search into the fields of the character creator (in Japanese).
  "comic.character.lookup.fill": guarded(async (params, respond) => {
    const query = (str(params, "query") ?? "").trim();
    const id = Number(params.id);
    const source = str(params, "source") ?? "";
    if (
      source === "vndb" &&
      params.externalId !== undefined &&
      str(params, "externalId") !== `c${id}`
    ) {
      fail(respond, "キャラIDが一致しません。もう一度検索してください");
      return;
    }
    const vndbDetails = source === "vndb" ? await vndbCharacterDetails(id) : undefined;
    const hit =
      vndbDetails?.hit ??
      rememberedHit(source, id) ??
      (source === "vndb" ? [] : await searchCharacters(query)).find(
        (row) => row.id === id && row.source === source,
      );
    if (!hit) {
      fail(respond, "そのキャラが見つかりません。もう一度検索してください");
      return;
    }
    if (hit.publicKind === "group") {
      fail(respond, "グループから人物を選んでください");
      return;
    }
    const details = vndbDetails ?? (await characterDetails(hit));
    const answer = await askLocalModel(fillPrompt(details.hit));
    respond(
      true,
      {
        fill: parseFill(answer, details.hit),
        imageUrl: details.imageUrls[0],
        imageUrls: details.imageUrls,
        url: hit.url,
        extra: details.hit.extra,
      },
      undefined,
    );
  }),

  "comic.character.candidates": guarded(async (params, respond) => {
    const id = str(params, "characterId") ?? "";
    const entry = await getCharacter(id);
    if (!entry) {
      fail(respond, "その登場人物が見つかりません");
      return;
    }
    const { jobs } = await listMediaLibrary("comic", 300);
    const candidates = jobs
      .filter(
        (job) =>
          job.status === "done" &&
          job.comic?.characterId === id &&
          (job.options.comicTask === "sheet" || job.options.comicTask === "outpaint") &&
          job.file,
      )
      .map((job) => ({
        jobId: job.id,
        view: job.comic?.view ?? "front",
        adult: job.comic?.adult === true,
        createdAt: job.createdAt,
        current: job.id === entry.sheetJobId,
      }));
    respond(true, { candidates }, undefined);
  }),

  "comic.character.list": guarded(async (_params, respond) => {
    const all = await listCharacters();
    const series = await listComicSeries();
    const views = [];
    for (const entry of all) {
      views.push({
        ...(await characterView(entry)),
        series: series
          .filter((s) => s.cast.some((c) => c.characterId === entry.id))
          .map((s) => ({ id: s.id, title: s.title })),
      });
    }
    respond(true, { characters: views }, undefined);
  }),

  // Name, profile, look, and an icon picked on the phone (iconBase64, PNG/JPEG).
  "comic.character.update": guarded(async (params, respond) => {
    const entry = await getCharacter(str(params, "characterId") ?? "");
    if (!entry) {
      fail(respond, "その登場人物が見つかりません");
      return;
    }
    const next = applyCharacterEdit(entry, params);
    const motionProfile =
      params.motionProfile === undefined
        ? undefined
        : cleanCharacterMotionProfile(params.motionProfile);
    assertAllowedRequest(
      next.look,
      ...Object.values(next.spec ?? {}).map(String),
      ...Object.values(next.sheetValues ?? {}),
    );
    assertAgeAllowed(next.spec?.age);
    const iconBase64 = str(params, "iconBase64");
    if (iconBase64) {
      if (iconBase64.length > MAX_SOURCE_IMAGE_BASE64) {
        fail(respond, "画像が大きすぎます");
        return;
      }
      const dir = characterAssetDir(entry.id);
      await fs.mkdir(dir, { recursive: true });
      const upload = path.join(dir, "icon-upload.png");
      await fs.writeFile(upload, Buffer.from(iconBase64, "base64"));
      next.icon = path.join(dir, "icon.png");
      await makeCharacterIcon(upload, next.icon).catch(async () => {
        await fs.copyFile(upload, next.icon as string);
      });
    }
    await saveCharacter(next);
    if (motionProfile !== undefined) saveCharacterMotionProfile(next.id, motionProfile);
    // A favourite chats as a partner: made on the first request, kept in step after every edit.
    const wantsPartner =
      params.ensurePartner === true || (params.isFavorite === true && !next.partnerId);
    const partner = await syncPartnerFromCharacter(next, { create: wantsPartner });
    if (partner && next.partnerId !== partner.id) {
      next.partnerId = partner.id;
      await saveCharacter(next);
    }
    respond(true, { character: await characterView(next) }, undefined);
  }),

  // The 360° gallery: the sheet, the turnaround and every training picture, by key.
  "comic.character.gallery": guarded(async (params, respond) => {
    const entry = await getCharacter(str(params, "characterId") ?? "");
    if (!entry) {
      fail(respond, "その登場人物が見つかりません");
      return;
    }
    // `version` (the file's time) tells the app when its saved copy of a picture is out of date.
    const items = await Promise.all(
      (await galleryFiles(entry)).map(async ({ key, label, file }) => ({
        key,
        label,
        version: Math.floor((await fs.stat(file).catch(() => undefined))?.mtimeMs ?? 0),
      })),
    );
    respond(true, { items }, undefined);
  }),

  "comic.character.image": guarded(async (params, respond) => {
    const entry = await getCharacter(str(params, "characterId") ?? "");
    const item = entry
      ? (await galleryFiles(entry)).find((f) => f.key === str(params, "key"))
      : undefined;
    if (!entry || !item) {
      fail(respond, "その絵が見つかりません");
      return;
    }
    // A JPEG of at most 720 px, cached next to the character's files. The avatar pictures keep
    // their transparent background, so they go out as PNG (at most 720 px tall).
    const keepAlpha = item.key.startsWith("av_") || item.key.startsWith("mini_");
    const thumbs = path.join(characterAssetDir(entry.id), "thumbs");
    await fs.mkdir(thumbs, { recursive: true });
    const thumb = path.join(thumbs, `${item.key}.${keepAlpha ? "png" : "jpg"}`);
    const fresh = await fs
      .stat(thumb)
      .then((t) => fs.stat(item.file).then((s) => t.mtimeMs >= s.mtimeMs))
      .catch(() => false);
    if (!fresh) {
      await new Promise<void>((resolve, reject) => {
        execFile(
          GALLERY_FFMPEG,
          keepAlpha
            ? ["-y", "-loglevel", "error", "-i", item.file, "-vf", "scale=-2:'min(720,ih)'", thumb]
            : [
                "-y",
                "-loglevel",
                "error",
                "-i",
                item.file,
                "-vf",
                "scale='min(720,iw)':-2",
                "-q:v",
                "4",
                thumb,
              ],
          { windowsHide: true, timeout: 60_000 },
          (err) => (err ? reject(err) : resolve()),
        );
      });
    }
    respond(
      true,
      { key: item.key, label: item.label, imageBase64: await fs.readFile(thumb, "base64") },
      undefined,
    );
  }),

  "comic.character.delete": guarded(async (params, respond) => {
    const id = str(params, "characterId") ?? "";
    // Series keep their copy of the character (without the library's sheet and LoRA).
    for (const series of await listComicSeries()) {
      if (!series.cast.some((c) => c.characterId === id)) continue;
      series.cast = series.cast.map(({ characterId, ...rest }) =>
        characterId === id ? rest : { ...rest, characterId },
      );
      await saveComicSeries(series);
    }
    const deleted = await deleteCharacter(id);
    if (deleted) deleteCharacterMotionProfile(id);
    respond(true, { deleted }, undefined);
  }),

  // Character LoRA training (about an hour; the chat model is off meanwhile).
  "comic.character.train": guarded(async (params, respond) => {
    const entry = await getCharacter(str(params, "characterId") ?? "");
    if (!entry) {
      fail(respond, "その登場人物が見つかりません");
      return;
    }
    if (!entry.refImage) {
      fail(respond, "先に設定画を描いて決めてください（学習の元になります）");
      return;
    }
    const job = await startMediaJob("comic", "", {
      comicTask: "lora",
      comicCharacterId: entry.id,
      imageModel: IMAGE_MODELS.find((value) => value === str(params, "imageModel")),
    });
    await saveCharacter({
      ...entry,
      loraStatus: "queued",
      loraStep: "順番待ち",
      updatedAt: Date.now(),
    });
    respond(true, summary(job), undefined);
  }),

  // Deletes a result, takes a waiting job out of the queue, or stops a running one.
  "media.job.delete": guarded(async (params, respond) => {
    const deleted = await deleteMediaJob(str(params, "jobId") ?? "");
    respond(true, { deleted }, undefined);
  }),
};
