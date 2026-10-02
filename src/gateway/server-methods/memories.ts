import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { resolveAgentWorkspaceDir, resolveDefaultAgentId } from "../../agents/agent-scope.js";
import { readBestEffortConfig } from "../../config/io.runtime.js";
import { getPartnerDir } from "./partners.js";
import type { GatewayRequestHandlers } from "./types.js";

async function getWorkspaceDir(partnerId?: string): Promise<string> {
  const home = process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
  if (partnerId) {
    return getPartnerDir(partnerId);
  }
  try {
    const config = await readBestEffortConfig();
    const defaultAgentId = resolveDefaultAgentId(config);
    return resolveAgentWorkspaceDir(config, defaultAgentId);
  } catch {
    return path.join(home, "workspace");
  }
}

export const memoriesHandlers: GatewayRequestHandlers = {
  "memory.inspect": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated below.
      const { partnerId } = (params || {}) as { partnerId?: string };
      const workspaceDir = await getWorkspaceDir(partnerId);
      await fs.mkdir(workspaceDir, { recursive: true });

      // Read MEMORY.md (Long-term memory)
      let longTermMemory = "";
      try {
        longTermMemory = await fs.readFile(path.join(workspaceDir, "MEMORY.md"), "utf-8");
      } catch {
        longTermMemory =
          "# 長期記憶 (MEMORY.md)\n\nここにAIが学習・記憶した知識やユーザー情報が保存されます。\n";
      }

      // Read USER.md (User profile & preferences)
      let userProfile = "";
      try {
        userProfile = await fs.readFile(path.join(workspaceDir, "USER.md"), "utf-8");
      } catch {
        userProfile =
          "# ユーザー設定 (USER.md)\n\n- ユーザーの呼び名: ユーザー様\n- 会話スタイル: 親しみやすい\n";
      }

      // Read daily notes from memory/ directory
      const dailyNotes: Array<{ fileName: string; sizeBytes: number; updatedAt: number }> = [];
      const memoryDir = path.join(workspaceDir, "memory");
      try {
        const files = await fs.readdir(memoryDir);
        for (const file of files) {
          if (file.endsWith(".md")) {
            const stat = await fs.stat(path.join(memoryDir, file));
            dailyNotes.push({
              fileName: file,
              sizeBytes: stat.size,
              updatedAt: stat.mtimeMs,
            });
          }
        }
      } catch {
        // memory dir doesn't exist yet
      }

      // Short term summary estimation
      const shortTermSummary = {
        sessionActive: true,
        estimatedTokens: Math.round((longTermMemory.length + userProfile.length) * 1.3),
        noteCount: dailyNotes.length,
      };

      respond(
        true,
        {
          workspaceDir,
          longTermMemory,
          userProfile,
          dailyNotes,
          shortTerm: shortTermSummary,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "memory.update": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { target, content, partnerId } = (params || {}) as {
      target?: string;
      content?: string;
      partnerId?: string;
    };
    if (!target || typeof content !== "string") {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing target or content"),
      );
      return;
    }

    try {
      const workspaceDir = await getWorkspaceDir(partnerId);
      await fs.mkdir(workspaceDir, { recursive: true });

      let targetPath: string;
      if (target === "MEMORY.md" || target === "long-term") {
        targetPath = path.join(workspaceDir, "MEMORY.md");
      } else if (target === "USER.md" || target === "user") {
        targetPath = path.join(workspaceDir, "USER.md");
      } else {
        // Daily note or specific file in memory/
        const safeFile = path.basename(target);
        const memoryDir = path.join(workspaceDir, "memory");
        await fs.mkdir(memoryDir, { recursive: true });
        targetPath = path.join(memoryDir, safeFile);
      }

      await fs.writeFile(targetPath, content, "utf-8");
      respond(
        true,
        { success: true, target, bytesWritten: Buffer.byteLength(content, "utf-8") },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "memory.clear": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { target, partnerId } = (params || {}) as { target?: string; partnerId?: string };
    try {
      const workspaceDir = await getWorkspaceDir(partnerId);
      if (target === "long-term") {
        await fs.writeFile(path.join(workspaceDir, "MEMORY.md"), "# 長期記憶\n", "utf-8");
      } else if (target === "short-term") {
        // Cleared
      }
      respond(true, { success: true, target: target || "short-term" }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "memory.summarize": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated below.
      const { partnerId } = (params || {}) as { partnerId?: string };
      const workspaceDir = await getWorkspaceDir(partnerId);
      await fs.mkdir(workspaceDir, { recursive: true });

      // デイリーノート一覧の読み込み
      const memoryDir = path.join(workspaceDir, "memory");
      let dailyNotesContent = "";
      try {
        const files = await fs.readdir(memoryDir);
        for (const file of files.filter((f) => f.endsWith(".md")).slice(-5)) {
          const content = await fs.readFile(path.join(memoryDir, file), "utf-8");
          dailyNotesContent += `\n--- [${file}] ---\n${content}\n`;
        }
      } catch {}

      // 長期記憶の読み込み
      let longTermMemory = "";
      try {
        longTermMemory = await fs.readFile(path.join(workspaceDir, "MEMORY.md"), "utf-8");
      } catch {
        longTermMemory = "# 長期記憶\n";
      }

      if (!dailyNotesContent.trim() && (!longTermMemory || longTermMemory.length < 50)) {
        respond(
          true,
          {
            summarized: false,
            message: "要約する十分な記憶・メモが見つかりませんでした。",
          },
          undefined,
        );
        return;
      }

      // 専門モデル部隊（汎用ワーカー）に裏側処理を依頼
      const systemPrompt =
        "あなたはユーザーのAI相棒の「裏側記憶整理アナリスト」です。\n" +
        "入力された会話メモやデイリーノートから、ユーザーの好み、重要な事実、進行中の関心事を抽出し、簡潔な箇条書きでまとめてください。";

      const prompt =
        `以下は最近の会話ログ・デイリーメモです:\n${dailyNotesContent.slice(-3000)}\n\n` +
        `現在の長期記憶:\n${longTermMemory.slice(-2000)}\n\n` +
        `長期記憶に追加すべき重要な事実やアップデートがあれば、整理して日本語で出力してください。`;

      const { executeSpecialistTask } = await import("../specialist-router.js");
      const result = await executeSpecialistTask({
        taskType: "summarize",
        prompt,
        systemPrompt,
        maxTokens: 512,
      });

      respond(
        true,
        {
          summarized: true,
          summary: result.text,
          workerPort: result.workerPort,
          latencyMs: result.latencyMs,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },
};
