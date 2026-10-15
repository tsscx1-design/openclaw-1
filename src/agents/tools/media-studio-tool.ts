import { Type } from "typebox";
import {
  deleteMediaJob,
  estimateMediaJob,
  getMediaJob,
  listMediaJobs,
} from "../../gateway/media-studio.js";
import {
  minutesText,
  pickOne,
  startStudioJob,
  STUDIO_ACTIONS,
  STUDIO_ASPECTS,
  STUDIO_GUIDE,
  STUDIO_IMAGE_MODELS,
  STUDIO_KINDS,
  STUDIO_MANGA_LAYOUTS,
  STUDIO_VIDEO_ENGINES,
  studioOptionsFrom,
  summarizeStudioJob,
  trainStudioVoice,
} from "../../gateway/studio-assistant.js";
import { listVoiceProjects } from "../../gateway/voice-studio.js";
import { stringEnum } from "../schema/string-enum.js";
import type { AnyAgentTool } from "./common.js";
import { jsonResult, ToolInputError } from "./common.js";

const MediaStudioToolSchema = Type.Object(
  {
    action: stringEnum(STUDIO_ACTIONS, {
      description:
        "generate=作り始める / status=進み具合（jobId省略で作業中と最近の作品）/ cancel=順番待ちの取り消し・作成中の中止・作品の削除 / estimate=かかる時間の目安 / options=使えるモデルと目安時間 / voices=声作りスタジオの声と学習状況 / train_voice=声の学習（now=trueで今から）",
    }),
    kind: Type.Optional(
      stringEnum(STUDIO_KINDS, {
        description:
          "generate/estimate: image | video | music | manga（漫画）| voice（声・セリフ）",
      }),
    ),
    prompt: Type.Optional(
      Type.String({
        description: "generate: 作りたい内容（日本語でよい）。動画は動きの指示、音楽は曲調",
      }),
    ),
    imageModel: Type.Optional(
      stringEnum(STUDIO_IMAGE_MODELS, { description: "画像・動画の最初の1枚のモデル" }),
    ),
    fast: Type.Optional(Type.Boolean({ description: "画像を速く（zimage と noobai 以外）" })),
    aspect: Type.Optional(stringEnum(STUDIO_ASPECTS)),
    engine: Type.Optional(stringEnum(STUDIO_VIDEO_ENGINES, { description: "動画の作り方" })),
    durationSec: Type.Optional(
      Type.Number({ description: "動画（standard/remix以外）や音楽の長さ（秒）" }),
    ),
    smooth: Type.Optional(Type.Boolean({ description: "動画をなめらか（2倍の滑らかさ）に" })),
    sourceImageJobId: Type.Optional(
      Type.String({ description: "動画: この完成画像ジョブの絵を動かす" }),
    ),
    instrumental: Type.Optional(Type.Boolean({ description: "音楽: 歌なし" })),
    voiceCaption: Type.Optional(
      Type.String({ description: "voice: 声の説明（voiceId がないとき）" }),
    ),
    irodoriModel: Type.Optional(
      stringEnum(["large", "small"] as const, {
        description: "voice: large=高品質（GPU）/ small=速い",
      }),
    ),
    mangaLayout: Type.Optional(
      stringEnum(STUDIO_MANGA_LAYOUTS, {
        description: "漫画: 4koma=縦の4コマ / page=5コマの1ページ / book=複数ページの本格漫画",
      }),
    ),
    lyrics: Type.Optional(Type.String({ description: "音楽: 歌詞（省略するとAIが作詞）" })),
    jobId: Type.Optional(Type.String({ description: "status/cancel の対象" })),
    voiceId: Type.Optional(
      Type.String({
        description: "train_voice の対象の声（idか名前）／voice: 話す声のid（voices で一覧）",
      }),
    ),
    now: Type.Optional(
      Type.Boolean({ description: "train_voice: true=今から学習、false=今夜の学習に予約" }),
    ),
  },
  { additionalProperties: false },
);

/**
 * Drives this PC's media studio (images, videos, music) and voice studio from the agent, so a
 * generation-assistant partner can start, watch and stop work in the main chat. Jobs started
 * here wait until the agent's reply is finished before they take the GPU.
 */
export function createMediaStudioTool(): AnyAgentTool {
  return {
    name: "media_studio",
    label: "Media Studio",
    description:
      "このPCの生成スタジオで画像・動画・音楽・漫画・声（セリフの音声。絵文字で喘ぎ・吐息・囁き）を作る、進み具合を確かめる、取り消す・中止する、かかる時間の目安を出す、使えるモデルを調べる。声作りスタジオの声の一覧と学習状況を見て、学習を今から始める・今夜に予約することもできる。生成を頼まれたら、選んだ設定と時間の目安をユーザーに伝えてから generate すること。",
    parameters: MediaStudioToolSchema,
    execute: async (_toolCallId, rawArgs) => {
      const args = (rawArgs ?? {}) as Record<string, unknown>;
      const action = pickOne(args.action, STUDIO_ACTIONS);
      if (!action) {
        throw new ToolInputError(`action must be one of ${STUDIO_ACTIONS.join(", ")}`);
      }
      switch (action) {
        case "options":
          return jsonResult({ guide: STUDIO_GUIDE });
        case "estimate": {
          const kind = pickOne(args.kind, STUDIO_KINDS);
          if (!kind) throw new ToolInputError("estimate needs kind");
          const estimate = await estimateMediaJob(kind, studioOptionsFrom(args));
          return jsonResult({ ...estimate, text: `完成まで${minutesText(estimate.totalSec)}` });
        }
        case "generate": {
          if (!pickOne(args.kind, STUDIO_KINDS)) throw new ToolInputError("generate needs kind");
          const { job, estimateText } = await startStudioJob(args);
          return jsonResult({
            started: await summarizeStudioJob(job),
            estimate: estimateText,
            note: "この返事が終わってから作り始める。作っている間はチャットのAIが止まり、終わると戻る。完成するとスマホに通知が届き、作品はアプリの生成スタジオの保存庫で見られる。",
          });
        }
        case "status": {
          if (typeof args.jobId === "string" && args.jobId.trim()) {
            const job = await getMediaJob(args.jobId.trim());
            return jsonResult(job ? await summarizeStudioJob(job) : { error: "見つかりません" });
          }
          const jobs = await listMediaJobs(30);
          const active = jobs.filter((job) => job.status === "queued" || job.status === "running");
          const recent = jobs
            .filter((job) => job.status === "done" || job.status === "error")
            .slice(0, 5);
          return jsonResult({
            active: await Promise.all(active.map(summarizeStudioJob)),
            recent: await Promise.all(recent.map(summarizeStudioJob)),
          });
        }
        case "cancel": {
          if (typeof args.jobId !== "string" || !args.jobId.trim()) {
            throw new ToolInputError("cancel needs jobId");
          }
          return jsonResult({ cancelled: await deleteMediaJob(args.jobId.trim()) });
        }
        case "voices": {
          const voices = await listVoiceProjects();
          return jsonResult({
            voices: voices.map((voice) => ({
              voiceId: voice.id,
              name: voice.name,
              status: voice.status,
              step: voice.step,
              source: voice.source ?? "recording",
              files: voice.files.length,
              error: voice.error,
            })),
          });
        }
        case "train_voice": {
          if (typeof args.voiceId !== "string" || !args.voiceId.trim()) {
            throw new ToolInputError("train_voice needs voiceId");
          }
          const now = args.now === true;
          try {
            const voice = await trainStudioVoice(args.voiceId, now);
            return jsonResult({
              voiceId: voice.id,
              name: voice.name,
              status: voice.status,
              note: now
                ? "この返事のあと学習を始める。学習中（数十分）はチャットと生成が止まり、終わるとスマホに通知が届く。"
                : "今夜の学習に予約した。",
            });
          } catch (err) {
            return jsonResult({ error: err instanceof Error ? err.message : String(err) });
          }
        }
      }
      throw new ToolInputError(`unknown action ${String(action)}`);
    },
  };
}
