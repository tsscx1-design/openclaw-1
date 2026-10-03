import { Type } from "typebox";
import {
  createPartnerFromResearch,
  researchIoFromTools,
  sanitizePartnerCreationRequest,
} from "../../gateway/partner-creation.js";
import type { ResearchIO } from "../../gateway/partner-research.js";
import type { SheetCompletion } from "../../gateway/partner-sheet-generator.js";
import type { savePartnerProfileWithSheet } from "../../gateway/server-methods/partners.js";
import {
  finishSpecialistJob,
  setSpecialistJobStage,
  startSpecialistJob,
} from "../../gateway/specialist-jobs.js";
import { stringEnum } from "../schema/string-enum.js";
import { createBackgroundChatNotifier } from "./background-chat-notice.js";
import type { AnyAgentTool } from "./common.js";
import { jsonResult, ToolInputError } from "./common.js";
import { callInProcessGatewayTool, type InProcessGatewayCaller } from "./in-process-gateway.js";

const PartnerCreateToolSchema = Type.Object(
  {
    name: Type.String({
      description:
        "パートナーにする人物名・キャラクター名（例: あのちゃん, 竈門炭治郎, 智慧之王 ラファエル）",
    }),
    source: Type.Optional(
      Type.String({
        description: "キャラクターなら登場作品（例: 鬼滅の刃）、実在の人物なら肩書き（例: 歌手）",
      }),
    ),
    kind: Type.Optional(
      stringEnum(["real_person", "fictional"] as const, {
        description:
          "real_person=実在の人物（タレント・歌手・俳優など）、fictional=作品のキャラクター。分からなければ省略（Wikipediaで自動判定）",
      }),
    ),
    aliases: Type.Optional(
      Type.Array(Type.String(), {
        maxItems: 5,
        description: "別名・愛称・英字表記（例: ano, あの）。資料探しに使う",
      }),
    ),
    notes: Type.Optional(
      Type.String({
        description: "ユーザーの追加要望（呼び方、関係性、口調の調整など）。シートより優先される",
      }),
    ),
    urls: Type.Optional(
      Type.Array(Type.String(), {
        maxItems: 5,
        description: "ユーザーが指定した参考ページのURL（任意、最大5件）",
      }),
    ),
    partnerId: Type.Optional(
      Type.String({ description: "既存パートナーを作り直す場合のID。省略すると新規作成" }),
    ),
  },
  { additionalProperties: false },
);

type PartnerCreateToolOptions = {
  agentSessionKey?: string;
  agentId?: string;
  sessionId?: string;
  callGateway?: InProcessGatewayCaller;
  webSearchTool?: AnyAgentTool | null;
  webFetchTool?: AnyAgentTool | null;
  isMainRunActive?: (sessionId: string) => boolean;
  /** Test seams: research IO, model calls and persistence. */
  io?: ResearchIO;
  complete?: SheetCompletion;
  save?: typeof savePartnerProfileWithSheet;
};

export function createPartnerCreateTool(options: PartnerCreateToolOptions = {}): AnyAgentTool {
  const gatewayCall = options.callGateway ?? callInProcessGatewayTool;

  return {
    name: "partner_create_from_character",
    label: "Create Partner From Character",
    description:
      "実在の有名人（タレント・歌手・俳優など）やアニメ・漫画・ゲームのキャラクターを、本人そっくりに話すAIパートナーとして作成（または既存パートナーを作り直し）する。ユーザーに人物やキャラクターをパートナーにして・作ってと頼まれたら、自分でシートを書かずに必ずこのツールを使うこと。裏作業員が複数のサイト（Wikipedia、そこに載っているインタビュー記事、ピクシブ百科事典、Web検索）を調べ、本人の発言を原文のまま集め、100項目のキャラクターシート・話し方の特徴・会話の見本を作って保存する。10〜30分かかるためバックグラウンドで実行され、完了はチャットに報告される。呼び出した後はユーザーに作成を任せたことを伝えて会話を続けること。",
    parameters: PartnerCreateToolSchema,
    execute: async (_toolCallId, rawArgs) => {
      const request = sanitizePartnerCreationRequest(rawArgs);
      if (!request) {
        throw new ToolInputError("name is required for partner_create_from_character");
      }
      const label = request.source ? `${request.name}（${request.source}）` : request.name;
      const io =
        options.io ??
        researchIoFromTools({
          webFetchTool: options.webFetchTool,
          webSearchTool: options.webSearchTool,
        });

      const notifier = createBackgroundChatNotifier({
        sessionKey: options.agentSessionKey,
        agentId: options.agentId,
        sessionId: options.sessionId,
        gatewayCall,
        isMainRunActive: options.isMainRunActive,
      });

      void notifier.post(
        `🛠️ **パートナー作成を裏作業員に任せました**: ${label}\n複数のサイトを調べて、本人の発言・話し方の特徴・会話の見本つきのキャラクターシートを作ります。10〜30分ほどかかります。その間も会話を続けられます。`,
        "partner-create-started",
      );
      const job = startSpecialistJob({
        kind: "partner-create",
        title: `パートナー作成: ${label}`,
        workerName: "パートナー調査（Web調査＋ローカルLLM）",
      });
      void createPartnerFromResearch({
        ...request,
        io,
        ...(options.complete
          ? { reader: options.complete, fast: options.complete, writer: options.complete }
          : {}),
        save: options.save,
        onProgress: (message) => {
          setSpecialistJobStage(job, message);
          void notifier.progress(message);
        },
      })
        .then(async (result) => {
          const minutes = Math.max(1, Math.round(result.durationMs / 60_000));
          finishSpecialistJob(job, {
            resultPreview: `${result.name}: ${result.filled}/${result.total}項目、資料${result.sourceCount}件、発言${result.quoteCount}件`,
          });
          await notifier.progress(`✅ **パートナー作成完了**: ${result.name}`);
          await notifier.post(
            [
              `✅ **パートナー「${result.name}」を${request.partnerId ? "作り直しました" : "作成しました"}**（約${minutes}分）`,
              `- 調べたサイト: ${result.sites.join("、")}（資料${result.sourceCount}件）`,
              `- 集めた本人の発言: ${result.quoteCount}件（原文と照合済み）`,
              `- 埋まった項目: ${result.filled}/${result.total}（資料に無い項目は空のままです）`,
              result.kind === "real_person"
                ? "- 実在の人物なので、性的な会話には応じず、私生活の話題も扱わない設定にしました。"
                : "",
              "設定画面の「キャラクターシート」で確認・修正できます。",
            ]
              .filter(Boolean)
              .join("\n"),
            "partner-create-completed",
          );
        })
        .catch(async (err) => {
          const reason = err instanceof Error ? err.message : String(err);
          finishSpecialistJob(job, { error: reason });
          await notifier.progress(`❌ **パートナー作成エラー**: ${label}`);
          await notifier.post(
            `❌ **パートナー「${label}」の作成に失敗しました**\n理由: ${reason}`,
            "partner-create-failed",
          );
        });

      const json = jsonResult({
        status: "background_started",
        name: request.name,
        source: request.source,
        partnerId: request.partnerId,
      });
      return {
        ...json,
        content: [
          {
            type: "text",
            text: `パートナー「${label}」の作成を裏作業員に任せました。完了するとチャットに報告されます。ユーザーにそのことを伝え、結果を待たずに会話を続けてください。`,
          },
          ...json.content,
        ],
      };
    },
  };
}
