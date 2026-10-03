import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { readJsonFile, resolveOpenClawHome, writeJsonFile } from "./partner-store.js";

// The group chat (マルチチャット) can use its own model. Entering the group chat swaps the main
// llama-server (port 11434) to that model and remembers what ran before; leaving swaps it back.
// If the app never says it left, ten quiet minutes or the next 1:1 partner chat swap back too.

export type GroupChatModel = {
  id: string;
  label: string;
  description: string;
  file: string;
  /** Extra llama-server flags (e.g. how much VRAM --fit leaves free). */
  extra: string[];
};

const UNCENSORED = "D:\\llm-models\\uncensored";

/**
 * Measured on the real group chat flow with Bonsai stopped (2026-10-03, RTX 4060 8 GB).
 * --fit lets llama.cpp keep as many MoE experts on the GPU as fit; --fit-target 1800 leaves
 * ~1.8 GB free so the partners' clone voices (Qwen3-TTS) can speak next to the model.
 * Dropped: Gemma 4 12B (13 tok/s, failed mid-conversation), Qwen3 30B-A3B ERP (failed).
 */
export const GROUP_CHAT_MODELS: GroupChatModel[] = [
  {
    id: "gemma4-26b-a4b-jp",
    label: "Gemma 4 26B 日本語（おすすめ）",
    description: "賢くキャラの個性がはっきり出る。毎秒約30。",
    file: path.join(UNCENSORED, "gemma-4-26B-A4B-it-qat-heretic-japanese-96e-Q4_0.gguf"),
    extra: ["--fit-target", "1800"],
  },
  {
    id: "nemoaurora-12b",
    label: "NemoAurora RP 12B",
    description: "日本語ロールプレイ専用。感情表現が豊か。毎秒約18。",
    file: path.join(UNCENSORED, "NemoAurora-RP-12B-IQ4_XS.gguf"),
    extra: ["--fit-target", "1800"],
  },
  {
    id: "shisa-nemo-12b",
    label: "Shisa v2 Nemo 12B",
    description: "日本語が安定。落ち着いた会話向き。毎秒約19。",
    file: path.join(UNCENSORED, "Shisa-v2-Mistral-Nemo-12B-Abliterated.i1-IQ4_XS.gguf"),
    extra: ["--fit-target", "1800"],
  },
];

/** Used until the user picks one. */
const DEFAULT_GROUP_MODEL = "gemma4-26b-a4b-jp";
const IDLE_RESTORE_MS = 10 * 60_000;
const READY_TIMEOUT_MS = 240_000;

function bonsaiDir(): string {
  return path.join(resolveOpenClawHome(), "bonsai");
}
function settingsFile(): string {
  return path.join(bonsaiDir(), "groupchat_model.json");
}
/** The main model's command line while the group model runs; its presence means "in group mode". */
function previousCmdFile(): string {
  return path.join(bonsaiDir(), "groupchat_prev_cmdline.txt");
}
function mainlineServer(): string {
  return path.join(resolveOpenClawHome(), "llamacpp-mainline", "llama-server.exe");
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

export async function getGroupChatModelId(): Promise<string> {
  const saved = await readJsonFile<{ modelId?: string }>(settingsFile(), {});
  const id = saved.modelId ?? DEFAULT_GROUP_MODEL;
  return id === "main" || GROUP_CHAT_MODELS.some((m) => m.id === id) ? id : DEFAULT_GROUP_MODEL;
}

export async function setGroupChatModelId(modelId: string): Promise<string> {
  if (modelId !== "main" && !GROUP_CHAT_MODELS.some((m) => m.id === modelId))
    throw new Error(`Unknown group chat model: ${modelId}`);
  await writeJsonFile(settingsFile(), { modelId });
  return modelId;
}

/** llama-server command line for a group model (upstream build: Gemma 4 / Qwen3 MoE need it). */
export function groupModelCmdline(model: GroupChatModel, server = mainlineServer()): string {
  return [
    `"${server}"`,
    `-m "${model.file}"`,
    "--host 127.0.0.1 --port 11434 -c 8192 -np 1 -fa on -ctk q8_0 -ctv q8_0",
    "--load-mode none --jinja",
    ...model.extra,
    `-a groupchat-${model.id}`,
  ].join(" ");
}

function runPowerShell(command: string): Promise<void> {
  return new Promise((resolve) => {
    const ps = spawn("powershell.exe", ["-NoProfile", "-Command", command], {
      windowsHide: true,
      stdio: "ignore",
    });
    ps.on("exit", () => resolve());
    ps.on("error", () => resolve());
  });
}

async function isMainReady(): Promise<boolean> {
  try {
    const res = await fetch("http://127.0.0.1:11434/health", {
      signal: AbortSignal.timeout(3_000),
    });
    return res.ok;
  } catch {
    return false;
  }
}

/** Stops whatever serves port 11434 and starts `cmdline` detached, then waits for it. */
async function swapMainServer(cmdline: string, workDir: string): Promise<void> {
  const escaped = cmdline.replace(/'/g, "''");
  await runPowerShell(
    `Get-CimInstance Win32_Process -Filter "Name='llama-server.exe'" | Where-Object { $_.CommandLine -match '--port\\s+11434(\\s|$)' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }; Start-Sleep -Seconds 2; ([wmiclass]'Win32_Process').Create('${escaped}', '${workDir}', $null) | Out-Null`,
  );
  const deadline = Date.now() + READY_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (await isMainReady()) return;
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error("model did not become ready in time");
}

let idleTimer: ReturnType<typeof setTimeout> | undefined;
let switching: Promise<unknown> = Promise.resolve();

/** Serializes swaps so enter and leave never interleave. */
function serialized<T>(task: () => Promise<T>): Promise<T> {
  const next = switching.then(task, task);
  switching = next.catch(() => undefined);
  return next;
}

/** Keeps group mode while turns keep coming; swaps back after ten quiet minutes. */
export function touchGroupChatMode(): void {
  if (idleTimer) clearTimeout(idleTimer);
  idleTimer = setTimeout(() => void leaveGroupChatMode().catch(() => undefined), IDLE_RESTORE_MS);
  idleTimer.unref?.();
}

export function enterGroupChatMode(): Promise<{
  modelId: string;
  switched: boolean;
  busy?: boolean;
}> {
  return serialized(async () => {
    const modelId = await getGroupChatModelId();
    if (modelId === "main") return { modelId, switched: false };
    // A media job holds the GPU; the chat model comes back when it ends.
    if (await exists(path.join(bonsaiDir(), "llm-pause.flag")))
      return { modelId, switched: false, busy: true };
    const model = GROUP_CHAT_MODELS.find((m) => m.id === modelId);
    if (!model || !(await exists(model.file))) throw new Error(`model file missing: ${modelId}`);
    if (!(await exists(previousCmdFile()))) {
      const previous = await fs.readFile(path.join(bonsaiDir(), "last_main_cmdline.txt"), "utf-8");
      await fs.writeFile(previousCmdFile(), previous.trim(), "utf-8");
    }
    touchGroupChatMode();
    await swapMainServer(groupModelCmdline(model), path.dirname(mainlineServer()));
    return { modelId, switched: true };
  });
}

/** Swaps the model that ran before the group chat back in; a no-op outside group mode. */
export function leaveGroupChatMode(): Promise<{ restored: boolean }> {
  return serialized(async () => {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = undefined;
    if (!(await exists(previousCmdFile()))) return { restored: false };
    const previous = (await fs.readFile(previousCmdFile(), "utf-8")).trim();
    await fs.rm(previousCmdFile(), { force: true });
    if (!previous) return { restored: false };
    // The watchdog and media jobs restart "the last model"; make that the main model again.
    await fs.writeFile(path.join(bonsaiDir(), "last_main_cmdline.txt"), previous, "utf-8");
    if (await exists(path.join(bonsaiDir(), "llm-pause.flag"))) return { restored: false };
    await swapMainServer(previous, path.join(bonsaiDir(), "bin"));
    return { restored: true };
  });
}
