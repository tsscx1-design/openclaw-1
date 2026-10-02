import { exec, execSync } from "node:child_process";
import { existsSync } from "node:fs";
import * as fs from "node:fs/promises";
import * as path from "node:path";

export interface SpecialistConfig {
  key: string;
  name: string;
  port: number;
  /** File name under MODEL_DIR, or an absolute path (large models live on D:). */
  modelFile: string;
  /** Vision projector for image input; absolute path. */
  mmprojFile?: string;
  parallel: number;
  ctxSize: number;
  role: string;
  tags: string[];
}

export interface SpecialistStatus {
  key: string;
  name: string;
  port: number;
  modelFile: string;
  role: string;
  online: boolean;
  pid?: number;
  fileReady: boolean;
}

const BONSAI_DIR = "C:\\Users\\sd190901\\.openclaw\\bonsai";
const MODEL_DIR = path.join(BONSAI_DIR, "model");
const EXT_MODEL_DIR = "D:\\llm-models";

// general, coder and phi share one MoE server (Nemotron 3 Nano 30B-A3B) on 11435; keep in sync
// with scripts/specialist_squad.ps1.
// Uncensored (abliterated/heretic) builds are preferred once downloaded; same rule as the .ps1.
const UNCENSORED_MODEL_DIR = path.join(EXT_MODEL_DIR, "uncensored");
function preferFile(preferred: string, fallback: string): string {
  return existsSync(preferred) ? preferred : fallback;
}
const SHARED_GENERAL_MODEL = preferFile(
  path.join(UNCENSORED_MODEL_DIR, "NVIDIA-Nemotron-3-Nano-30B-A3B-BF16-heretic.i1-Q4_K_S.gguf"),
  path.join(EXT_MODEL_DIR, "Nemotron-3-Nano-30B-A3B-UD-Q4_K_XL.gguf"),
);
const GEMMA_UNCENSORED_MODEL = path.join(
  UNCENSORED_MODEL_DIR,
  "Gemma-4-E4B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
);
const GEMMA_UNCENSORED_MMPROJ = path.join(
  UNCENSORED_MODEL_DIR,
  "mmproj-Gemma-4-E4B-Uncensored-HauhauCS-Aggressive-f16.gguf",
);
const USE_GEMMA_UNCENSORED =
  existsSync(GEMMA_UNCENSORED_MODEL) && existsSync(GEMMA_UNCENSORED_MMPROJ);

export function resolveModelPath(file: string): string {
  return path.isAbsolute(file) ? file : path.join(MODEL_DIR, file);
}

export const SPECIALIST_WORKERS: SpecialistConfig[] = [
  {
    key: "general",
    name: "Nemotron 3 Nano 汎用ワーカー",
    port: 11435,
    modelFile: SHARED_GENERAL_MODEL,
    parallel: 2,
    ctxSize: 16384,
    role: "汎用要約・メモリ整理・会話分類",
    tags: [
      "general",
      "summarize",
      "classify",
      "memory",
      "default",
      "research",
      "web",
      "search",
      "reader",
    ],
  },
  {
    key: "coder",
    name: "Nemotron 3 Nano（コード担当）",
    port: 11435,
    modelFile: SHARED_GENERAL_MODEL,
    parallel: 2,
    ctxSize: 16384,
    role: "コード生成・レビュー・デバッグ補助",
    tags: ["code", "coding", "coder", "review", "debug", "refactor"],
  },
  {
    key: "gemma",
    name: "Gemma 4 E4B 多言語・翻訳・画像ワーカー",
    port: 11437,
    modelFile: USE_GEMMA_UNCENSORED
      ? GEMMA_UNCENSORED_MODEL
      : path.join(EXT_MODEL_DIR, "gemma-4-E4B-it-UD-Q4_K_XL.gguf"),
    mmprojFile: USE_GEMMA_UNCENSORED
      ? GEMMA_UNCENSORED_MMPROJ
      : path.join(EXT_MODEL_DIR, "mmproj-gemma-4-E4B-it-f16.gguf"),
    parallel: 2,
    ctxSize: 16384,
    role: "翻訳・多言語・文章校正",
    tags: ["translate", "translation", "multilingual", "proofread", "lang"],
  },
  {
    key: "phi",
    name: "Nemotron 3 Nano（数理・推論担当）",
    port: 11435,
    modelFile: SHARED_GENERAL_MODEL,
    parallel: 2,
    ctxSize: 16384,
    role: "数学・データ分析・論理推論",
    tags: ["math", "logic", "analyze", "reasoning", "calc"],
  },
];

/**
 * 指定ポートの待機状態とPIDを確認する
 */
export async function getPortPid(port: number): Promise<number | null> {
  return new Promise((resolve) => {
    exec(
      `powershell -Command "Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -First 1"`,
      (err, stdout) => {
        if (err || !stdout) {
          resolve(null);
          return;
        }
        const pid = parseInt(stdout.trim(), 10);
        resolve(Number.isFinite(pid) ? pid : null);
      },
    );
  });
}

/**
 * 各専門モデル部隊の現在のステータスを取得する
 */
export async function getSpecialistSquadStatus(): Promise<SpecialistStatus[]> {
  const results: SpecialistStatus[] = [];

  for (const worker of SPECIALIST_WORKERS) {
    const activeModelFile = worker.modelFile;
    const modelPath = resolveModelPath(activeModelFile);
    let fileReady = false;
    try {
      await fs.access(modelPath);
      fileReady = true;
    } catch {
      fileReady = false;
    }

    const pid = await getPortPid(worker.port);
    const online = pid !== null;

    results.push({
      key: worker.key,
      name: worker.name,
      port: worker.port,
      modelFile: activeModelFile,
      role: worker.role,
      online,
      pid: pid ?? undefined,
      fileReady,
    });
  }

  return results;
}

/**
 * 特定のワーカーを起動する
 */
export async function startSpecialistWorker(
  key: string,
): Promise<{ success: boolean; message: string; port?: number }> {
  const worker = SPECIALIST_WORKERS.find((w) => w.key === key);
  if (!worker) {
    return { success: false, message: `Unknown worker: ${key}` };
  }

  const existingPid = await getPortPid(worker.port);
  if (existingPid) {
    return {
      success: true,
      message: `Worker ${key} is already running on port ${worker.port} (PID: ${existingPid})`,
      port: worker.port,
    };
  }

  return new Promise((resolve) => {
    exec(
      `powershell -ExecutionPolicy Bypass -File "c:\\オープン\\openclaw\\scripts\\specialist_squad.ps1" -Action start -Worker ${key}`,
      (err, stdout, stderr) => {
        if (err) {
          resolve({
            success: false,
            message: `Failed to start worker ${key}: ${stderr || err.message}`,
          });
        } else {
          resolve({ success: true, message: stdout.trim(), port: worker.port });
        }
      },
    );
  });
}

/**
 * 特定のワーカーを停止する
 */
export async function stopSpecialistWorker(
  key: string,
): Promise<{ success: boolean; message: string }> {
  const worker = SPECIALIST_WORKERS.find((w) => w.key === key);
  if (!worker) {
    return { success: false, message: `Unknown worker: ${key}` };
  }

  const pid = await getPortPid(worker.port);
  if (!pid) {
    return { success: true, message: `Worker ${key} is not running (Port ${worker.port} is free)` };
  }

  return new Promise((resolve) => {
    try {
      execSync(`taskkill /PID ${pid} /F 2>nul`);
      resolve({ success: true, message: `Stopped worker ${key} (PID: ${pid})` });
    } catch (err) {
      resolve({ success: false, message: `Failed to stop worker ${key}: ${String(err)}` });
    }
  });
}

const WORKER_READY_TIMEOUT_MS = 180_000;

/**
 * llama-server は待受開始後もモデル読込中は 503 を返すため、/health が 200 になるまで待つ。
 */
export async function waitForWorkerReady(
  port: number,
  timeoutMs = WORKER_READY_TIMEOUT_MS,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/health`, {
        signal: AbortSignal.timeout(5_000),
      });
      if (res.ok) {
        return true;
      }
    } catch {
      // not listening yet
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  return false;
}

/**
 * タスクの種類やプロンプトから最適なワーカーポートを選択する
 */
export async function routeTaskToWorker(taskType?: string): Promise<number> {
  const squad = await getSpecialistSquadStatus();
  const normalized = (taskType || "general").toLowerCase();

  // 1. タグマッチングで最適なモデルを探す
  const matched = SPECIALIST_WORKERS.find((w) => w.tags.some((t) => normalized.includes(t)));

  if (matched) {
    const status = squad.find((s) => s.key === matched.key);
    // オンラインであればそのワーカーを使用
    if (status?.online) {
      return matched.port;
    }
    // 未起動かつファイルが存在するなら自動起動を試みる
    if (status?.fileReady) {
      await startSpecialistWorker(matched.key);
      if (await waitForWorkerReady(matched.port)) {
        return matched.port;
      }
    }
  }

  // 2. フォールバック: 汎用ワーカー（11435）
  const generalStatus = squad.find((s) => s.key === "general");
  if (!generalStatus?.online && generalStatus?.fileReady) {
    await startSpecialistWorker("general");
  }
  if (!(await waitForWorkerReady(11435))) {
    throw new Error("Specialist worker (port 11435) did not become ready");
  }

  return 11435;
}
export function findWorkerByPort(port: number): SpecialistConfig | undefined {
  return SPECIALIST_WORKERS.find((w) => w.port === port);
}

export function buildSpecialistCompletionReport(params: {
  workerName: string;
  workerRole: string;
  taskType?: string;
  latencyMs: number;
  result: string;
}): string {
  const seconds = (params.latencyMs / 1000).toFixed(1);
  return `📢 **【裏作業完了報告】**
**実行担当**: ${params.workerName}（${params.workerRole}）
**処理時間**: ${seconds}秒

---
**成果物・解析結果:**
${params.result.trim()}`;
}

/**
 * 専門モデル部隊にタスクを発行して結果を取得する
 */
export async function executeSpecialistTask(params: {
  taskType?: string;
  prompt: string;
  systemPrompt?: string;
  port?: number;
  maxTokens?: number;
  temperature?: number;
  timeoutMs?: number;
}): Promise<{
  text: string;
  workerPort: number;
  workerKey: string;
  workerName: string;
  workerRole: string;
  latencyMs: number;
}> {
  const startTime = Date.now();
  const targetPort = params.port || (await routeTaskToWorker(params.taskType));
  const worker = findWorkerByPort(targetPort) || {
    key: "general",
    name: `ワーカー (Port ${targetPort})`,
    role: "専門推論ワーカー",
    port: targetPort,
    modelFile: "",
    parallel: 1,
    ctxSize: 16384,
    tags: [],
  };

  const messages: Array<{ role: string; content: string }> = [];
  if (params.systemPrompt) {
    messages.push({ role: "system", content: params.systemPrompt });
  }
  messages.push({ role: "user", content: params.prompt });

  const body = {
    messages,
    max_tokens: params.maxTokens ?? 2048,
    temperature: params.temperature ?? 0.7,
  };

  // CPU workers are slow; bound the wait so a stuck worker cannot hold the delegation forever.
  const response = await fetch(`http://127.0.0.1:${targetPort}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(params.timeoutMs ?? 600_000),
  });

  if (!response.ok) {
    throw new Error(
      `Specialist worker on port ${targetPort} responded with HTTP ${response.status}: ${await response.text()}`,
    );
  }

  // SAFETY: llama-server OpenAI-compatible JSON completion response schema
  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  const text = data?.choices?.[0]?.message?.content || "";
  const latencyMs = Date.now() - startTime;

  return {
    text,
    workerPort: targetPort,
    workerKey: worker.key,
    workerName: worker.name,
    workerRole: worker.role,
    latencyMs,
  };
}
