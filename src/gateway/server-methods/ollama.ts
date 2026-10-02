import { execFile, execSync } from "node:child_process";
import os from "node:os";
import path from "node:path";
import { OllamaManager } from "../../../extensions/ollama/src/ollama-manager.js";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { mutateConfigFileWithRetry } from "../../config/config.js";
import {
  readConfigFileSnapshotForWrite,
  readSourceConfigSnapshot,
  writeConfigFile,
} from "../../config/io.runtime.js";
import type { OpenClawConfig } from "../../config/types.openclaw.js";
import type { GatewayRequestHandlers } from "./types.js";

const ollamaManager = new OllamaManager();

const BONSAI_MODEL_FILES: Record<string, string> = {
  "ternary-bonsai-2-27b": "Ternary-Bonsai-2-27B-PQ2_0.gguf",
  "ternary-bonsai-pq2": "Ternary-Bonsai-2-27B-PQ2_0.gguf",
  "ternary-bonsai-ptq1": "Ternary-Bonsai-2-27B-PTQ1_0.gguf",
  "bonsai2-pq1": "Ternary-Bonsai-2-27B-PTQ1_0.gguf",
  bonsai2clackpq1: "Bonsai-2-27B-PTQ1_0-CRACK.gguf",
  "bonsai-crack": "Bonsai-2-27B-PQ2_0-CRACK.gguf",
  "bonsai-crack-pq2": "Bonsai-2-27B-PQ2_0-CRACK.gguf",
  "swift-bonsai": "Swift-Bonsai-2-PQ2_0.gguf",
  "swift-bonsai-pq2": "Swift-Bonsai-2-PQ2_0.gguf",
  "swift-bonsai-ptq1": "Swift-Bonsai-2-PTQ1_0.gguf",
};
const DEFAULT_BONSAI_MODEL_FILE = "Swift-Bonsai-2-PTQ1_0.gguf";

// thinkingLevel → reasoning_budget (off:0 / low:512 / medium:2048 / high:8192 / max:-1 unlimited)
const THINKING_BUDGETS: Record<string, number> = {
  off: 0,
  none: 0,
  low: 512,
  medium: 2048,
  high: 8192,
  max: -1,
};

// Values below reach a PowerShell script and the llama-server command line; keep them to
// plain model-alias characters so a client cannot smuggle quotes or extra arguments.
const SAFE_MODEL_ALIAS = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;

function resolveStartModelScriptPath(): string {
  const home = process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
  return path.join(home, "bonsai", "bin", "start_model.ps1");
}

type OllamaModelEntry = string | { id?: string; params?: Record<string, unknown> };

/** Returns the authored ollama provider model list, creating the provider entry when absent. */
function ensureOllamaProviderModels(draft: OpenClawConfig): OllamaModelEntry[] {
  const models = (draft.models ??= {}) as { providers?: Record<string, unknown> };
  const providers = (models.providers ??= {});
  const provider = (providers.ollama ??= {
    baseUrl: "http://127.0.0.1:11434",
    api: "ollama",
    models: [],
  }) as { models?: unknown };
  if (!Array.isArray(provider.models)) {
    provider.models = [];
  }
  return provider.models as OllamaModelEntry[];
}

function ollamaModelEntryId(entry: OllamaModelEntry): string | undefined {
  return typeof entry === "string" ? entry : entry.id;
}

/** Adds the model when missing; with refreshKeepAlive, also updates an existing object entry. */
function upsertOllamaModel(
  draft: OpenClawConfig,
  id: string,
  keepAlive: string,
  refreshKeepAlive = false,
): void {
  const entries = ensureOllamaProviderModels(draft);
  const index = entries.findIndex((entry) => ollamaModelEntryId(entry) === id);
  if (index === -1) {
    entries.push({ id, name: id, params: { keep_alive: keepAlive } } as OllamaModelEntry);
    return;
  }
  const existing = entries[index];
  if (refreshKeepAlive && existing && typeof existing === "object") {
    existing.params = { ...existing.params, keep_alive: keepAlive };
  }
}

function setPrimaryModel(draft: OpenClawConfig, modelRef: string): void {
  const defaults = ((draft.agents ??= {}).defaults ??= {}) as { model?: unknown };
  const current = defaults.model;
  defaults.model =
    current && typeof current === "object"
      ? { ...current, primary: modelRef }
      : { primary: modelRef };
}

/** Patches the authored config in place; never rewrites it from a partial object. */
async function mutateOllamaConfig(mutate: (draft: OpenClawConfig) => void): Promise<void> {
  await mutateConfigFileWithRetry({ base: "source", afterWrite: { mode: "auto" }, mutate });
}

interface ModelPullProgress {
  model: string;
  status: string;
  completed: number;
  total: number;
  percent: number;
  done: boolean;
  error?: string;
  updatedAt: number;
}

const pullProgressMap = new Map<string, ModelPullProgress>();

// pullProgressMap の自動クリーンアップ: 完了から5分以上経過したエントリを削除 (問題2修正)
setInterval(() => {
  const threshold = Date.now() - 5 * 60 * 1000;
  for (const [k, v] of pullProgressMap.entries()) {
    if (v.done && v.updatedAt < threshold) pullProgressMap.delete(k);
  }
}, 60_000).unref();

export const ollamaHandlers: GatewayRequestHandlers = {
  "ollama.catalog.list": async ({ respond }) => {
    const catalog = {
      categories: [
        {
          id: "japanese",
          name: "日本語対応・高性能 (Japanese Capable)",
          models: [
            {
              id: "qwen2.5:latest",
              name: "Qwen 2.5 (7B / 32B)",
              description: "最高峰の日本語対話能力・高速・推論力抜群（標準推奨）",
            },
            {
              id: "hf.co/elyza/Llama-3-ELYZA-JP-8B-GGUF",
              name: "ELYZA Llama 3 (8B)",
              description: "日本のELYZA社による高品質な日本語特化モデル (Hugging Face)",
            },
            {
              id: "hf.co/rinna/bilingual-gpt-neox-4b-gguf",
              name: "Rinna Bilingual (4B)",
              description: "rinna社による軽量で親しみやすい日本語モデル (Hugging Face)",
            },
            {
              id: "hf.co/tokyotech-llm/Llama-3-Swallow-8B-Instruct-v0.1-GGUF",
              name: "Swallow Llama 3 (8B)",
              description: "東工大研究グループによる日本語Swallowモデル (Hugging Face)",
            },
          ],
        },
        {
          id: "bonsai",
          name: "Bonsai Models (Custom)",
          models: [
            {
              id: "bonsai2 pq1",
              name: "bonsai2 pq1",
              description: "Bonsai 2 PQ1 quantized model",
            },
            {
              id: "bonsai2CLACKpq1",
              name: "bonsai2CLACKpq1",
              description: "Bonsai 2 CLACK PQ1 quantized model",
            },
            {
              id: "swift-bonsai-ptq1",
              name: "swift-bonsai-ptq1",
              description: "Swift Bonsai PTQ1 quantized model",
            },
          ],
        },
        {
          id: "roleplay",
          name: "キャラクター・AIパートナー (Roleplay & Partner)",
          models: [
            {
              id: "hf.co/TheBloke/Kunoichi-DPO-v2-7B-GGUF",
              name: "Kunoichi DPO v2 (7B)",
              description: "感情豊かで会話が弾むキャラクター対話特化モデル (Hugging Face)",
            },
            {
              id: "hf.co/mradermacher/Magnum-v2-12b-GGUF",
              name: "Magnum v2 (12B)",
              description: "高い表現力とペルソナ維持力を持つ高品質RPモデル (Hugging Face)",
            },
            {
              id: "hf.co/TheBloke/Fimbulvetr-11B-v2-GGUF",
              name: "Fimbulvetr (11B)",
              description: "自然な掛け合いと表現力に優れたクリエイティブモデル (Hugging Face)",
            },
          ],
        },
        {
          id: "general",
          name: "汎用・知識モデル (General & Reasoning)",
          models: [
            {
              id: "llama3.1:latest",
              name: "Meta Llama 3.1 (8B)",
              description: "Metaの汎用フラグシップモデル。長文読解と汎用性に強み",
            },
            {
              id: "gemma2:9b",
              name: "Google Gemma 2 (9B)",
              description: "Google開発の高知能・高品質テキスト生成モデル",
            },
            {
              id: "phi3:mini",
              name: "Microsoft Phi-3 Mini",
              description: "スマホや軽量環境でも超高速に動く軽量インテリジェントモデル",
            },
          ],
        },
        {
          id: "coding",
          name: "プログラミング・作業支援 (Coding & Tools)",
          models: [
            {
              id: "qwen2.5-coder:latest",
              name: "Qwen 2.5 Coder (7B)",
              description: "最先端のコード生成・修正・技術QAモデル",
            },
            {
              id: "codestral:22b",
              name: "Mistral Codestral (22B)",
              description: "Mistral社の巨大コーディング・推論専用モデル",
            },
          ],
        },
      ],
    };
    respond(true, catalog, undefined);
  },

  "ollama.models.list": async ({ respond }) => {
    try {
      const models = await ollamaManager.listModels();
      // Also get the current active model from config
      const snapshot = await readSourceConfigSnapshot();
      const configuredModel = snapshot.sourceConfig?.agents?.defaults?.model;
      const currentPrimary =
        (typeof configuredModel === "string" ? configuredModel : configuredModel?.primary) ??
        "ollama/qwen2.5:latest";
      respond(true, { models, currentModel: currentPrimary }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "ollama.model.switch": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const rawModel = (params as { model?: string })?.model;
    if (typeof rawModel !== "string" || !rawModel.trim()) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing or invalid 'model' parameter"),
      );
      return;
    }

    const isBonsaiModel =
      rawModel.startsWith("bonsai/") ||
      rawModel.startsWith("prism-local/") ||
      rawModel.includes("bonsai") ||
      rawModel.includes("ptq1") ||
      rawModel.includes("pq2");
    const cleanModel = rawModel.replace(/^(ollama|bonsai|prism-local)\//, "").trim();
    const fullModelRef = isBonsaiModel
      ? rawModel.includes("/")
        ? rawModel
        : `bonsai/${cleanModel}`
      : `ollama/${cleanModel}`;
    const keepAlive = (params as { keepAlive?: string })?.keepAlive || "1h";
    const requestedThinkingLevel = (params as { thinkingLevel?: unknown })?.thinkingLevel;
    const thinkingLevel =
      typeof requestedThinkingLevel === "string" &&
      Object.hasOwn(THINKING_BUDGETS, requestedThinkingLevel.toLowerCase())
        ? requestedThinkingLevel.toLowerCase()
        : "medium";
    const reasoningBudget = THINKING_BUDGETS[thinkingLevel] ?? 2048;
    if (isBonsaiModel && !SAFE_MODEL_ALIAS.test(cleanModel)) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Invalid local model name"));
      return;
    }

    // Bonsai/ローカルGGUFモデルの場合、安全にプロセス切替＆思考バジェット設定を行う
    if (isBonsaiModel) {
      try {
        const targetFile =
          BONSAI_MODEL_FILES[cleanModel.toLowerCase()] ?? DEFAULT_BONSAI_MODEL_FILE;
        const scriptPath = resolveStartModelScriptPath();
        const isPQ2 = targetFile.includes("PQ2");

        // PQ2モデルの場合: VRAM確保のため不要プロセスを停止
        if (isPQ2) {
          console.log("[Gateway] PQ2モデル切替: VRAMを確保するため不要プロセスを停止中...");
          const processesToKill = [
            "chrome",
            "msedge",
            "msedgewebview2",
            "steamwebhelper",
            "steam",
            "nearby_share",
          ];
          for (const proc of processesToKill) {
            try {
              execSync(`taskkill /IM "${proc}.exe" /F 2>nul`, { stdio: "ignore" });
              console.log(`[Gateway] 停止: ${proc}.exe`);
            } catch {
              // プロセスが存在しない場合は無視
            }
          }
          // 少し待ってVRAMが解放されるのを待つ
          await new Promise<void>((r) => setTimeout(r, 2000));
          console.log("[Gateway] VRAM確保完了");
        }

        // PTQ1 and PQ2 share start_model.ps1: it stops the old llama-server, starts the
        // single model detached, and waits for the port. Arguments go through execFile
        // (no shell), and every value is validated or comes from the tables above.
        console.log(
          `[Gateway] llama-server switch: File=${targetFile}, Alias=${cleanModel}, Budget=${reasoningBudget}`,
        );
        await new Promise<void>((resolve) => {
          execFile(
            "powershell.exe",
            [
              "-NoProfile",
              "-ExecutionPolicy",
              "Bypass",
              "-File",
              scriptPath,
              "-ModelFile",
              targetFile,
              "-Alias",
              cleanModel,
              "-ThinkingLevel",
              thinkingLevel,
              "-ReasoningBudget",
              String(reasoningBudget),
            ],
            { windowsHide: true },
            (err) => {
              if (err) {
                console.warn("[Gateway] start_model.ps1 reported error (continuing):", err);
              }
              resolve();
            },
          );
        });
        console.log("[Gateway] Llama-server switch finished.");
      } catch (err) {
        console.error("[Gateway] Failed to switch model:", err);
      }
    }

    try {
      // isBonsaiModelの場合はwriteConfigFileを呼ばない！
      // （writeConfigFileを呼ぶとホットリロードが発火し、GatewayプロセスがSIGUSR1で再起動してクライアント切断・オフラインエラーになるため）
      if (!isBonsaiModel) {
        try {
          await mutateOllamaConfig((draft) => {
            setPrimaryModel(draft, fullModelRef);
            upsertOllamaModel(draft, cleanModel, keepAlive);
          });
        } catch (configErr) {
          console.warn("Failed to persist the selected Ollama model:", configErr);
        }
      }

      const targetPartnerId = (params as { partnerId?: string })?.partnerId;
      if (targetPartnerId) {
        try {
          const fs = await import("fs/promises");
          const os = await import("os");
          const path = await import("path");
          const partnersJsonPath = path.join(os.homedir(), ".openclaw", "partners.json");
          const raw = await fs.readFile(partnersJsonPath, "utf8");
          const partnersList = JSON.parse(raw);
          if (Array.isArray(partnersList)) {
            const pIdx = partnersList.findIndex((p: any) => p.id === targetPartnerId);
            if (pIdx !== -1) {
              partnersList[pIdx].modelId = fullModelRef;
              partnersList[pIdx].updatedAt = Date.now();
              await fs.writeFile(partnersJsonPath, JSON.stringify(partnersList, null, 2), "utf8");
            }
          }
        } catch (partnerErr) {
          console.warn("Failed to update partner modelId:", partnerErr);
        }
      }

      // Preload / keep model in VRAM (Bonsaiはllama-server側なのでスキップ)
      if (!isBonsaiModel) {
        try {
          const fetchUrl = "http://127.0.0.1:11434/api/generate";
          await fetch(fetchUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              model: cleanModel,
              keep_alive: keepAlive === "0" ? 0 : keepAlive || "24h",
            }),
          }).catch(() => undefined);
        } catch {}
      }

      respond(
        true,
        {
          ok: true,
          success: true,
          activeModel: fullModelRef,
          keepAlive,
          thinkingLevel,
          reasoningBudget,
          partnerId: targetPartnerId,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },
  "ollama.pull": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const modelName = (params as { name?: string })?.name;
    if (typeof modelName !== "string" || !modelName) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing or invalid 'name' parameter"),
      );
      return;
    }

    const cleanName = modelName.replace(/^ollama\//, "").trim();

    // 問題10修正: 同一モデルが既にダウンロード中の場合は重複防止
    const existing = pullProgressMap.get(cleanName);
    if (existing && !existing.done) {
      respond(true, { status: "already_pulling", name: cleanName }, undefined);
      return;
    }
    pullProgressMap.set(cleanName, {
      model: cleanName,
      status: "starting",
      completed: 0,
      total: 0,
      percent: 0,
      done: false,
      updatedAt: Date.now(),
    });

    try {
      // Background streaming pull with live progress tracking
      ollamaManager
        .pullModelWithProgress(cleanName, (p) => {
          pullProgressMap.set(cleanName, {
            model: cleanName,
            status: p.status,
            completed: p.completed || 0,
            total: p.total || 0,
            percent: p.percent || 0,
            done: false,
            updatedAt: Date.now(),
          });
        })
        .then(async () => {
          pullProgressMap.set(cleanName, {
            model: cleanName,
            status: "success",
            completed: 100,
            total: 100,
            percent: 100,
            done: true,
            updatedAt: Date.now(),
          });
          try {
            await mutateOllamaConfig((draft) => {
              upsertOllamaModel(draft, cleanName, "1h");
            });
          } catch (err) {
            console.error(`Failed to register pulled model ${cleanName} into config:`, err);
          }
        })
        .catch((e) => {
          console.error(`Ollama pull error for ${cleanName}:`, e);
          pullProgressMap.set(cleanName, {
            model: cleanName,
            status: "error",
            completed: 0,
            total: 0,
            percent: 0,
            done: true,
            error: String(e),
            updatedAt: Date.now(),
          });
        });

      respond(true, { status: "pulling", name: cleanName }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "ollama.pull.progress": async ({ params, respond }) => {
    const rawName = (params as { name?: string })?.name;
    const modelName = rawName ? rawName.replace(/^ollama\//, "").trim() : undefined;
    if (modelName) {
      const progress = pullProgressMap.get(modelName);
      respond(true, { progress: progress || null }, undefined);
    } else {
      const all: Record<string, ModelPullProgress> = {};
      for (const [k, v] of pullProgressMap.entries()) {
        all[k] = v;
      }
      respond(true, { progresses: all }, undefined);
    }
  },

  "ollama.stop": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const modelName = (params as { name?: string })?.name;
    if (typeof modelName !== "string" || !modelName) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing or invalid 'name' parameter"),
      );
      return;
    }

    try {
      await ollamaManager.stopModel(modelName);
      respond(true, { status: "stopped", name: modelName }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "ollama.delete": async ({ params, respond }) => {
    const rawName = (params as { name?: string })?.name;
    if (typeof rawName !== "string" || !rawName.trim()) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing or invalid 'name' parameter"),
      );
      return;
    }
    const cleanName = rawName.replace(/^ollama\//, "").trim();

    try {
      await ollamaManager.deleteModel(cleanName);

      // openclaw.json からモデル設定を安全に削除
      try {
        await mutateOllamaConfig((draft) => {
          const provider = (draft.models as { providers?: Record<string, { models?: unknown }> })
            ?.providers?.ollama;
          if (!provider || !Array.isArray(provider.models)) {
            return;
          }
          provider.models = (provider.models as OllamaModelEntry[]).filter(
            (entry) => ollamaModelEntryId(entry) !== cleanName,
          );
        });
      } catch (configErr) {
        console.warn("Failed to update config after model deletion:", configErr);
      }

      respond(true, { ok: true, success: true, deleted: cleanName }, undefined);
    } catch (e) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INTERNAL_ERROR, `Failed to delete model ${cleanName}: ${e}`),
      );
    }
  },

  "ollama.hf.search": async ({ params, respond }) => {
    const p =
      (params as {
        query?: string;
        category?: string;
        sort?: "downloads" | "likes" | "lastModified";
        limit?: number;
      }) ?? {};
    try {
      const models = await ollamaManager.searchHuggingFace({
        query: p.query,
        category: p.category,
        sort: p.sort,
        limit: p.limit,
      });
      respond(true, { models }, undefined);
    } catch (e) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INTERNAL_ERROR, `Hugging Face search failed: ${e}`),
      );
    }
  },

  "ollama.gguf.scan": async ({ params, respond }) => {
    const p = (params as { customDirs?: string[] }) ?? {};
    try {
      const files = await ollamaManager.scanLocalGgufFiles(p.customDirs);
      respond(true, { files }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, `GGUF scan failed: ${e}`));
    }
  },

  "ollama.gguf.import": async ({ params, respond }) => {
    const rawName = (params as { name?: string; filePath?: string })?.name;
    const rawFilePath = (params as { name?: string; filePath?: string })?.filePath;
    if (!rawName || !rawFilePath) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing required 'name' or 'filePath' parameter"),
      );
      return;
    }

    // 問題4修正: ファイルパスのセキュリティ検証 (.gguf のみ許可)
    const normalizedPath = path.resolve(rawFilePath);
    if (!normalizedPath.toLowerCase().endsWith(".gguf")) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Only .gguf files are allowed for import"),
      );
      return;
    }
    const filePath = normalizedPath;
    const modelName = rawName.replace(/^ollama\//, "").trim();

    // バックグラウンドで進捗追跡を開始
    pullProgressMap.set(modelName, {
      model: modelName,
      status: "importing gguf",
      completed: 0,
      total: 100,
      percent: 5,
      done: false,
      updatedAt: Date.now(),
    });

    ollamaManager
      .createModelFromGguf(modelName, filePath, (prog) => {
        pullProgressMap.set(modelName, {
          model: modelName,
          status: prog.status,
          completed: prog.completed ?? 0,
          total: prog.total ?? 100,
          percent: prog.percent ?? 50,
          done: false,
          updatedAt: Date.now(),
        });
      })
      .then(() => {
        pullProgressMap.set(modelName, {
          model: modelName,
          status: "success",
          completed: 100,
          total: 100,
          percent: 100,
          done: true,
          updatedAt: Date.now(),
        });
      })
      .catch((err) => {
        pullProgressMap.set(modelName, {
          model: modelName,
          status: "error",
          completed: 0,
          total: 100,
          percent: 0,
          done: true,
          error: String(err?.message ?? err),
          updatedAt: Date.now(),
        });
      });

    respond(true, { status: "importing", name: modelName, filePath }, undefined);
  },

  "ollama.model.optimize": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated by field access defaults below.
      const p = (params || {}) as {
        modelSizeGb?: number;
        targetDevice?: "vram" | "ram" | "auto";
      };
      const tuning = await ollamaManager.optimizeGgufOptions(p);
      respond(true, { ok: true, payload: tuning, ...tuning }, undefined);
    } catch (err) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(err)));
    }
  },

  "ollama.dual.chat": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated below.
      const { vramModel, ramModel, messages, personaPrompt } = (params || {}) as {
        vramModel?: string;
        ramModel?: string;
        messages?: Array<{ role: string; content: string }>;
        personaPrompt?: string;
      };

      if (!vramModel || !Array.isArray(messages) || messages.length === 0) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, "Missing required 'vramModel' or 'messages'"),
        );
        return;
      }

      const result = await ollamaManager.dualChatOrchestrate({
        vramModel,
        ramModel,
        messages,
        personaPrompt,
      });

      respond(true, { ok: true, payload: result, ...result }, undefined);
    } catch (err) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(err)));
    }
  },

  "ollama.vram_ram.apply": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated below.
      const { vramModel, ramModel, mode, partnerId } = (params || {}) as {
        vramModel?: string;
        ramModel?: string;
        mode?: string;
        partnerId?: string;
      };

      if (!vramModel) {
        respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "vramModel is required"));
        return;
      }

      const cleanVram = vramModel.replace(/^ollama\//, "").trim();
      const cleanRam = ramModel ? ramModel.replace(/^ollama\//, "").trim() : "";
      const fullVramRef = `ollama/${cleanVram}`;

      // Update partner modelId if partnerId is provided
      if (partnerId) {
        try {
          const fs = await import("fs/promises");
          const os = await import("os");
          const path = await import("path");
          const partnersJsonPath = path.join(os.homedir(), ".openclaw", "partners.json");
          const raw = await fs.readFile(partnersJsonPath, "utf8");
          const partnersList = JSON.parse(raw);
          if (Array.isArray(partnersList)) {
            const pIdx = partnersList.findIndex((p: any) => p.id === partnerId);
            if (pIdx !== -1) {
              partnersList[pIdx].modelId = fullVramRef;
              partnersList[pIdx].updatedAt = Date.now();
              await fs.writeFile(partnersJsonPath, JSON.stringify(partnersList, null, 2), "utf8");
            }
          }
        } catch (partnerErr) {
          console.warn("Failed to update partner modelId:", partnerErr);
        }
      }

      // Update openclaw.json for persistence across Gateway restarts
      // A validation-bypassing raw-file fallback used to live here; config writes now go
      // through the validated mutation path only, so a rejected write leaves the file intact.
      try {
        await mutateOllamaConfig((draft) => {
          setPrimaryModel(draft, fullVramRef);
          upsertOllamaModel(draft, cleanVram, "24h", true);
          if (cleanRam && cleanRam !== "none") {
            upsertOllamaModel(draft, cleanRam, "24h", true);
          }
        });
      } catch (confErr) {
        console.warn("Failed to persist VRAM/RAM settings to openclaw.json:", confErr);
      }

      // Preload VRAM model into GPU (keep_alive: 24h)
      try {
        await fetch("http://127.0.0.1:11434/api/generate", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ model: cleanVram, keep_alive: "24h" }),
        }).catch(() => undefined);
      } catch {}

      // Preload RAM model if specified
      if (cleanRam && cleanRam !== "none") {
        try {
          await ollamaManager.loadModelToRam(cleanRam).catch(() => undefined);
        } catch {}
      }

      respond(
        true,
        {
          ok: true,
          success: true,
          vramModel: fullVramRef,
          ramModel: cleanRam ? `ollama/${cleanRam}` : "",
          mode: mode || "GPU_DEFAULT",
          partnerId,
        },
        undefined,
      );
    } catch (err) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(err)));
    }
  },

  "ollama.sub_llm.load": async ({ params, respond }) => {
    try {
      // SAFETY: params payload structure is manually validated below.
      const { model, role } = (params || {}) as {
        model?: string;
        role?: string;
      };

      if (!model) {
        respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "model is required"));
        return;
      }

      const cleanModel = model.replace(/^ollama\//, "").trim();
      const effectiveRole = role || "support_brain";

      // Load model into RAM with num_gpu = 0
      await ollamaManager.loadModelToRam(cleanModel);

      // 問題7修正: writeConfigFile を使用してスキーマ管理下で保存
      try {
        const writeSnapshot = await readConfigFileSnapshotForWrite();
        const { snapshot, writeOptions } = writeSnapshot;
        const nextConfig = { ...snapshot.sourceConfig } as any;
        if (!nextConfig.models) nextConfig.models = {};
        nextConfig.models.subLlm = {
          model: `ollama/${cleanModel}`,
          role: effectiveRole,
          loaded: true,
          updatedAt: Date.now(),
        };
        await writeConfigFile(nextConfig, writeOptions).catch((e) =>
          console.warn("Failed to persist sub_llm config:", e),
        );
      } catch (saveErr) {
        console.warn("Failed to persist sub_llm config:", saveErr);
      }

      respond(
        true,
        {
          ok: true,
          success: true,
          model: `ollama/${cleanModel}`,
          role: effectiveRole,
          status: "loaded_in_ram",
        },
        undefined,
      );
    } catch (err) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(err)));
    }
  },

  "ollama.sub_llm.unload": async ({ params, respond }) => {
    try {
      const { model } = (params || {}) as {
        model?: string;
      };

      let targetModel = model ? model.replace(/^ollama\//, "").trim() : "";

      // 問題7修正: writeConfigFile ベースで設定を読み取り
      const writeSnapshot = await readConfigFileSnapshotForWrite().catch(() => null);
      const sourceConf = writeSnapshot?.snapshot?.sourceConfig as any;

      if (!targetModel && sourceConf?.models?.subLlm?.model) {
        targetModel = (sourceConf.models.subLlm.model as string).replace(/^ollama\//, "").trim();
      }

      if (targetModel) {
        await ollamaManager.stopModel(targetModel);
      }

      // 問題7修正: writeConfigFile を使用してスキーマ管理下で更新
      if (writeSnapshot) {
        try {
          const { snapshot, writeOptions } = writeSnapshot;
          const nextConfig = { ...snapshot.sourceConfig } as any;
          if (nextConfig.models?.subLlm) {
            nextConfig.models.subLlm.loaded = false;
            nextConfig.models.subLlm.updatedAt = Date.now();
          }
          await writeConfigFile(nextConfig, writeOptions).catch((e) =>
            console.warn("Failed to persist sub_llm unload config:", e),
          );
        } catch (e) {
          console.warn("Failed to update sub_llm unload config:", e);
        }
      }

      respond(
        true,
        {
          ok: true,
          success: true,
          model: targetModel ? `ollama/${targetModel}` : "",
          status: "unloaded",
        },
        undefined,
      );
    } catch (err) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(err)));
    }
  },
};
