import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import {
  deleteMediaJob,
  getMediaJob,
  listMediaLibrary,
  mediaStudioStatus,
  readMediaResult,
  startMediaJob,
  type MediaJob,
  type MediaKind,
  type MediaOptions,
} from "../media-studio.js";
import type { GatewayRequestHandlers, RespondFn } from "./types.js";

type Params = Record<string, unknown>;

const KINDS: readonly MediaKind[] = ["image", "video", "music"];
const ASPECTS = ["square", "portrait", "landscape"] as const;
const VIDEO_ENGINES = ["standard", "remix", "long", "audio", "fast", "h3"] as const;
const IMAGE_MODELS = ["wai", "noobai", "zimage", "lustify", "cyberreal", "asianreal"] as const;
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
    error: job.error,
    mime: job.mime,
    lyrics: job.kind === "music" ? job.options.lyrics : undefined,
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
      fail(respond, "kind must be image, video or music");
      return;
    }
    const aspect = str(params, "aspect");
    const sourceImageBase64 = str(params, "sourceImageBase64");
    if (sourceImageBase64 && sourceImageBase64.length > MAX_SOURCE_IMAGE_BASE64) {
      fail(respond, "画像が大きすぎます（6MB まで）");
      return;
    }
    const duration = typeof params.durationSec === "number" ? params.durationSec : undefined;
    const options: MediaOptions = {
      aspect: ASPECTS.find((value) => value === aspect),
      engine: VIDEO_ENGINES.find((value) => value === str(params, "engine")),
      imageModel: IMAGE_MODELS.find((value) => value === str(params, "imageModel")),
      fast: params.fast === true,
      smooth: params.smooth === true,
      sourceImageJobId: str(params, "sourceImageJobId"),
      sourceImageBase64,
      lyrics: str(params, "lyrics"),
      instrumental: params.instrumental === true,
      durationSec: duration && Number.isFinite(duration) ? Math.round(duration) : undefined,
    };
    try {
      const job = await startMediaJob(kind, str(params, "prompt") ?? "", options);
      respond(true, summary(job), undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
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
    const result = await readMediaResult(str(params, "jobId") ?? "");
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
    respond(true, { jobs: jobs.map(summary), counts }, undefined);
  }),

  "media.job.delete": guarded(async (params, respond) => {
    const deleted = await deleteMediaJob(str(params, "jobId") ?? "");
    respond(true, { deleted }, undefined);
  }),
};
