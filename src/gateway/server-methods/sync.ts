import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { getPartnerDir, isValidPartnerId } from "./partners.js";
import type { GatewayRequestHandlers } from "./types.js";

function getOpenClawHome(): string {
  return process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
}

export const syncHandlers: GatewayRequestHandlers = {
  /**
   * Bidirectional mutual synchronization between Android client and PC Gateway.
   * Merges offline client deltas into PC Gateway, and returns the latest PC state.
   */
  "sync.bidirectional": async ({ params, respond }) => {
    try {
      const openclawHome = getOpenClawHome();
      const partnersJsonPath = path.join(openclawHome, "partners.json");
      const userMdPath = path.join(openclawHome, "workspace", "USER.md");
      const bondJsonPath = path.join(openclawHome, "partner_bonds.json");

      await fs.mkdir(path.join(openclawHome, "workspace"), { recursive: true });

      // SAFETY: params payload structure is manually validated by field access below.
      const payload = (params || {}) as {
        clientTimestamp?: number;
        pendingMessages?: Array<{
          partnerId?: string;
          role: string;
          content: string;
          timestamp: number;
        }>;
        pendingFacts?: Array<{
          partnerId?: string;
          fact: string;
          timestamp: number;
        }>;
        /** @deprecated レガシー互換。新クライアントは pendingBondExpMap を使用 */
        pendingBondExp?: number;
        /** パートナーID別の絆EXP（新形式） */
        pendingBondExpMap?: Record<string, number>;
      };

      // ── K-1 修正: pendingMessages をパートナーIDごとの history.json にマージ ──
      let mergedMessagesCount = 0;
      if (Array.isArray(payload.pendingMessages) && payload.pendingMessages.length > 0) {
        // パートナーIDごとにグルーピング
        const byPartner = new Map<
          string,
          Array<{ role: string; content: string; timestamp: number }>
        >();
        for (const msg of payload.pendingMessages) {
          const pid = msg.partnerId || "partner-luna";
          if (!byPartner.has(pid)) byPartner.set(pid, []);
          byPartner.get(pid)!.push({
            role: msg.role,
            content: msg.content,
            timestamp: msg.timestamp,
          });
        }

        for (const [partnerId, newMessages] of byPartner) {
          if (!isValidPartnerId(partnerId)) {
            continue;
          }
          const pDir = getPartnerDir(partnerId);
          await fs.mkdir(pDir, { recursive: true });
          const histFile = path.join(pDir, "history.json");

          // 既存履歴を読み込み
          let existing: Array<{ id?: string; role: string; content: string; timestamp: number }> =
            [];
          try {
            const raw = await fs.readFile(histFile, "utf-8");
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) existing = parsed;
          } catch {
            // ファイルが存在しない場合は空配列
          }

          // 重複排除（timestampとcontentの組み合わせで判定）
          const existingKeys = new Set(
            existing.map((m) => `${m.timestamp}:${m.content.slice(0, 50)}`),
          );
          for (const msg of newMessages) {
            const key = `${msg.timestamp}:${msg.content.slice(0, 50)}`;
            if (!existingKeys.has(key)) {
              existing.push({
                id: `sync-${msg.timestamp}-${Math.random().toString(36).slice(2, 6)}`,
                role: msg.role,
                content: msg.content,
                timestamp: msg.timestamp,
              });
              mergedMessagesCount++;
            }
          }

          // タイムスタンプ順にソートして保存
          existing.sort((a, b) => a.timestamp - b.timestamp);
          await fs.writeFile(histFile, JSON.stringify(existing, null, 2), "utf-8");
        }
      }

      // ── K-3 修正: pendingFacts をパートナー固有の MEMORY.md に書き込み ──
      let mergedFactsCount = 0;
      if (Array.isArray(payload.pendingFacts) && payload.pendingFacts.length > 0) {
        // パートナーIDごとにグルーピング
        const factsByPartner = new Map<string, string[]>();
        for (const f of payload.pendingFacts) {
          const pid = f.partnerId || "global";
          if (!factsByPartner.has(pid)) factsByPartner.set(pid, []);
          // BUG-6 修正: ISO 8601形式に統一
          const dateStr = new Date(f.timestamp).toISOString().slice(0, 10);
          factsByPartner.get(pid)!.push(`- [モバイル同期 ${dateStr}] ${f.fact}`);
          mergedFactsCount++;
        }

        for (const [partnerId, factLines] of factsByPartner) {
          if (partnerId !== "global" && !isValidPartnerId(partnerId)) {
            continue;
          }
          let memoryFilePath: string;
          if (partnerId === "global") {
            // グローバル記憶（パートナーIDなし）はワークスペースに保存
            await fs.mkdir(path.join(openclawHome, "workspace"), { recursive: true });
            memoryFilePath = path.join(openclawHome, "workspace", "MEMORY.md");
          } else {
            // パートナー固有の MEMORY.md に保存（partners.ts と同じパス）
            const pDir = getPartnerDir(partnerId);
            await fs.mkdir(pDir, { recursive: true });
            memoryFilePath = path.join(pDir, "MEMORY.md");
          }

          let currentMemory = "";
          try {
            currentMemory = await fs.readFile(memoryFilePath, "utf-8");
          } catch {
            const header =
              partnerId === "global"
                ? "# 長期記憶 (MEMORY.md)\n\n"
                : `# ${partnerId}の長期記憶 (MEMORY.md)\n\n`;
            currentMemory = header;
          }

          // 重複チェック（同じファクトが既に存在する場合はスキップ）
          const dedupedLines = factLines.filter(
            (line) => !currentMemory.includes(line.slice(0, 40)),
          );
          if (dedupedLines.length > 0) {
            currentMemory += `\n\n## モバイル端末からの自動同期記憶\n${dedupedLines.join("\n")}\n`;
            await fs.writeFile(memoryFilePath, currentMemory, "utf-8");
          }
        }
      }

      // ── K-2 修正: 絆ポイントをパートナーID別に反映 ──
      let currentBonds: Record<string, { exp: number; level: number; title: string }> = {};
      try {
        const bondRaw = await fs.readFile(bondJsonPath, "utf-8");
        currentBonds = JSON.parse(bondRaw);
      } catch {
        currentBonds = {};
      }

      let totalMergedBondExp = 0;
      let bondsChanged = false;

      // 新形式: pendingBondExpMap（パートナーID別）
      if (payload.pendingBondExpMap && typeof payload.pendingBondExpMap === "object") {
        for (const [partnerId, expAmount] of Object.entries(payload.pendingBondExpMap)) {
          if (typeof expAmount === "number" && expAmount > 0) {
            if (!currentBonds[partnerId]) {
              currentBonds[partnerId] = { exp: 0, level: 1, title: "初対面のふたり" };
            }
            currentBonds[partnerId].exp += expAmount;
            currentBonds[partnerId].level = Math.max(
              1,
              Math.floor(currentBonds[partnerId].exp / 100) + 1,
            );
            totalMergedBondExp += expAmount;
            bondsChanged = true;
          }
        }
      }

      // レガシー互換: pendingBondExp（単一数値 → pendingMessages のパートナーID群に均等分配）
      if (
        typeof payload.pendingBondExp === "number" &&
        payload.pendingBondExp > 0 &&
        !bondsChanged
      ) {
        const partnerIds = Array.isArray(payload.pendingMessages)
          ? [...new Set(payload.pendingMessages.map((m) => m.partnerId || "partner-luna"))]
          : ["partner-luna"];
        const expPerPartner = Math.ceil(payload.pendingBondExp / partnerIds.length);

        for (const pid of partnerIds) {
          if (!currentBonds[pid]) {
            currentBonds[pid] = { exp: 0, level: 1, title: "初対面のふたり" };
          }
          currentBonds[pid].exp += expPerPartner;
          currentBonds[pid].level = Math.max(1, Math.floor(currentBonds[pid].exp / 100) + 1);
        }
        totalMergedBondExp = payload.pendingBondExp;
        bondsChanged = true;
      }

      if (bondsChanged) {
        await fs.writeFile(bondJsonPath, JSON.stringify(currentBonds, null, 2), "utf-8");
      }

      // 3. Read latest PC state to return to client
      let partnersConfig = "[]";
      try {
        partnersConfig = await fs.readFile(partnersJsonPath, "utf-8");
      } catch {
        // partners.json may not exist yet
      }

      // パートナー固有の記憶を集約して返す
      let longTermMemory = "";
      try {
        // グローバル記憶
        longTermMemory = await fs.readFile(
          path.join(openclawHome, "workspace", "MEMORY.md"),
          "utf-8",
        );
      } catch {
        longTermMemory = "# 長期記憶 (MEMORY.md)\n";
      }

      let userProfile = "";
      try {
        userProfile = await fs.readFile(userMdPath, "utf-8");
      } catch {
        userProfile = "# ユーザー設定 (USER.md)\n";
      }

      const syncResult = {
        success: true,
        serverTimestamp: Date.now(),
        mergedFactsCount,
        mergedMessagesCount,
        mergedBondExp: totalMergedBondExp,
        latestData: {
          partnersConfig,
          longTermMemory,
          userProfile,
          bonds: currentBonds,
        },
      };

      respond(true, syncResult, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, `Sync failed: ${String(e)}`));
    }
  },
};
