import { Type } from "typebox";
import {
  clip,
  digestCharacterSources,
  generatePartnerSheet,
  workerSheetCompletion,
  type SheetCompletion,
} from "../../gateway/partner-sheet-generator.js";
import { savePartnerProfileWithSheet } from "../../gateway/server-methods/partners.js";
import { finishSpecialistJob, startSpecialistJob } from "../../gateway/specialist-jobs.js";
import { createBackgroundChatNotifier } from "./background-chat-notice.js";
import type { AnyAgentTool } from "./common.js";
import { jsonResult, ToolInputError } from "./common.js";
import { callInProcessGatewayTool, type InProcessGatewayCaller } from "./in-process-gateway.js";
import { extractUrls, toolResultText } from "./specialist-task-tool.js";

const PartnerCreateToolSchema = Type.Object(
  {
    name: Type.String({
      description: "キャラクター名・人物名（例: 智慧之王 ラファエル, 竈門炭治郎）",
    }),
    source: Type.Optional(
      Type.String({
        description: "登場作品・肩書き（例: 転生したらスライムだった件, 鬼滅の刃, 俳優）",
      }),
    ),
    notes: Type.Optional(
      Type.String({
        description: "ユーザーの追加要望（呼び方、関係性、口調の調整など）。シートより優先される",
      }),
    ),
    urls: Type.Optional(
      Type.Array(Type.String(), {
        maxItems: 3,
        description: "参考にするページURL（任意、最大3件）",
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
  /** Test seam: the extraction model call. */
  complete?: SheetCompletion;
  /** Test seam: persistence. */
  save?: typeof savePartnerProfileWithSheet;
};

const MAX_SOURCE_CHARS = 3_500;
const MAX_SOURCES = 4;
export function createPartnerCreateTool(options: PartnerCreateToolOptions = {}): AnyAgentTool {
  const gatewayCall = options.callGateway ?? callInProcessGatewayTool;
  const complete = options.complete ?? workerSheetCompletion;
  const save = options.save ?? savePartnerProfileWithSheet;

  return {
    name: "partner_create_from_character",
    label: "Create Partner From Character",
    description:
      "アニメ・漫画・ゲームのキャラクターや有名人をAIパートナーとして作成（または既存パートナーを作り直し）する。ユーザーにキャラクターや人物をパートナーにしてと頼まれたら、自分でシートを書かずに必ずこのツールを使うこと。裏作業員がWebで原作情報を調べ、100項目のキャラクターシートと自由記述（口調・セリフ例）を項目ごとに作成して保存する。数分かかるためバックグラウンドで実行され、完了はチャットに報告される。呼び出した後はユーザーに作成を任せたことを伝えて会話を続けること。",
    parameters: PartnerCreateToolSchema,
    execute: async (toolCallId, rawArgs) => {
      const args = (rawArgs ?? {}) as {
        name?: string;
        source?: string;
        notes?: string;
        urls?: string[];
        partnerId?: string;
      };
      const name = args.name?.trim();
      if (!name) {
        throw new ToolInputError("name is required for partner_create_from_character");
      }
      const source = args.source?.trim() || undefined;
      const notes = args.notes?.trim() || undefined;
      const partnerId = args.partnerId?.trim() || undefined;
      const urls = (args.urls ?? [])
        .map((url) => url.trim())
        .filter((url) => /^https?:\/\//i.test(url))
        .slice(0, 3);
      const label = source ? `${name}（${source}）` : name;

      const notifier = createBackgroundChatNotifier({
        sessionKey: options.agentSessionKey,
        agentId: options.agentId,
        sessionId: options.sessionId,
        gatewayCall,
        isMainRunActive: options.isMainRunActive,
      });

      const gatherSources = async (): Promise<string[]> => {
        const sources: string[] = [];
        const toFetch = [...urls, `https://dic.pixiv.net/a/${encodeURIComponent(name)}`];
        if (options.webSearchTool) {
          const query = [name, source, "キャラクター 性格 口調 一人称"].filter(Boolean).join(" ");
          try {
            const searchText = toolResultText(
              await options.webSearchTool.execute(`${toolCallId}-search`, { query, count: 6 }),
            );
            if (searchText) {
              sources.push(`## 検索結果: ${query}\n${clip(searchText, MAX_SOURCE_CHARS)}`);
              for (const url of extractUrls(searchText)) {
                if (!toFetch.includes(url)) {
                  toFetch.push(url);
                }
              }
            }
          } catch {
            // Search is optional; fetched pages may still be enough.
          }
        }
        if (options.webFetchTool) {
          for (const [index, url] of toFetch.entries()) {
            if (sources.length >= MAX_SOURCES + 1) {
              break;
            }
            try {
              const text = toolResultText(
                await options.webFetchTool.execute(`${toolCallId}-fetch-${index}`, {
                  url,
                  extractMode: "text",
                  maxChars: MAX_SOURCE_CHARS,
                }),
              );
              if (text.length > 200) {
                sources.push(`## ページ: ${url}\n${clip(text, MAX_SOURCE_CHARS)}`);
              }
            } catch {
              // Missing pages (e.g. no pixiv entry) are expected.
            }
          }
        }
        return sources;
      };

      const run = async () => {
        await notifier.progress(`🔎 **パートナー作成中**: ${label} の原作情報を調査しています...`);
        const sources = await gatherSources();
        const digest = await digestCharacterSources({ name, source, sources, complete });
        await notifier.progress(
          `✍️ **パートナー作成中**: ${label} のキャラクターシートを項目ごとに作成しています...`,
        );
        const { sheet, persona, filled, total } = await generatePartnerSheet({
          name,
          source,
          notes,
          digest,
          complete,
        });
        const saved = await save({ partnerId, name, persona, characterSheet: sheet });
        return { saved, filled, total, sourceCount: sources.length };
      };

      void notifier.post(
        `🛠️ **パートナー作成を裏作業員に任せました**: ${label}\n原作情報を調べてキャラクターシート（100項目＋自由記述）を作ります。数分かかります。その間も会話を続けられます。`,
        "partner-create-started",
      );
      const job = startSpecialistJob({
        kind: "partner-create",
        title: `パートナー作成: ${label}`,
        workerName: "Gemma 3 多言語・翻訳専門ワーカー",
      });
      void run()
        .then(async ({ saved, filled, total, sourceCount }) => {
          finishSpecialistJob(job, { resultPreview: `${saved.name}: ${filled}/${total}項目` });
          await notifier.progress(`✅ **パートナー作成完了**: ${saved.name}`);
          await notifier.post(
            `✅ **パートナー「${saved.name}」を${partnerId ? "作り直しました" : "作成しました"}**\n- 埋まった項目: ${filled}/${total}（原作に情報が無い項目は空のままです）\n- 参照した資料: ${sourceCount}件\n- 自由記述: 口調の特徴・セリフ例を追加\n設定画面の「キャラクターシート」で確認・修正できます。`,
            "partner-create-completed",
          );
        })
        .catch(async (err) => {
          finishSpecialistJob(job, { error: String(err) });
          await notifier.progress(`❌ **パートナー作成エラー**: ${label}`);
          await notifier.post(
            `❌ **パートナー「${label}」の作成に失敗しました**\n理由: ${String(err)}`,
            "partner-create-failed",
          );
        });

      const json = jsonResult({ status: "background_started", name, source, partnerId });
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
