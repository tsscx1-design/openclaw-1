import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { restoreChatModelIfPending } from "./chat-model-gate.js";
import { readJsonFile, resolveOpenClawHome, writeJsonFile } from "./partner-store.js";

// Partner chats (1:1 and the group chat, マルチチャット) run their own model: a partner chat swaps the
// main llama-server (port 11434) to that model and remembers what ran before. The partner model
// stays loaded between chats; the previous model (the agent's) comes back only when the agent
// runs (ensureAgentModel) or the user picks a model from the home screen.

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
 * Measured with a real 1:1 partner chat on the RTX 4060 8 GB (2026-10-03, 16K context).
 * --fit lets llama.cpp keep as many MoE experts on the GPU as fit; --fit-target 1800 leaves
 * ~1.8 GB free so the partners' clone voices (Qwen3-TTS) can speak next to the model.
 * The Gemma 4 26B builds break the agent's tool calls, so they serve partner chats only.
 * Dropped: Gemma 4 12B (13 tok/s), Qwen3 30B-A3B ERP (failed).
 */
export const GROUP_CHAT_MODELS: GroupChatModel[] = [
  {
    id: "gemma4-26b-a4b-jp-72e",
    label: "Gemma 4 26B 日本語・軽量（おすすめ）",
    description: "会話が自然で、いちばん速い。毎秒約37。",
    file: path.join(UNCENSORED, "gemma-4-26B-A4B-it-qat-heretic-japanese-72e-Q4_0.gguf"),
    extra: ["--fit-target", "1800"],
  },
  {
    id: "gemma4-26b-a4b-jp",
    label: "Gemma 4 26B 日本語（標準）",
    description: "賢くキャラの個性がはっきり出る。毎秒約28。",
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
const DEFAULT_GROUP_MODEL = "gemma4-26b-a4b-jp-72e";
/** A 1:1 chat sends the full character sheet plus 20 turns; 8K was too small for that. */
const PARTNER_MODEL_CONTEXT = 16384;
const READY_TIMEOUT_MS = 240_000;
const PARTNER_ALIAS_PREFIX = "groupchat-";

function bonsaiDir(): string {
  return path.join(resolveOpenClawHome(), "bonsai");
}
function settingsFile(): string {
  return path.join(bonsaiDir(), "groupchat_model.json");
}
/** The agent model's command line while a partner model runs; its presence means "partner mode". */
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

/** "main" means partner chats use the agent's model, so a loaded partner model is swapped out. */
export async function setGroupChatModelId(modelId: string): Promise<string> {
  if (modelId !== "main" && !GROUP_CHAT_MODELS.some((m) => m.id === modelId))
    throw new Error(`Unknown group chat model: ${modelId}`);
  await writeJsonFile(settingsFile(), { modelId });
  if (modelId === "main") await leaveGroupChatMode();
  return modelId;
}

/** llama-server command line for a partner model (upstream build: Gemma 4 / Qwen3 MoE need it). */
export function groupModelCmdline(model: GroupChatModel, server = mainlineServer()): string {
  const device = process.env.OPENCLAW_MAIN_CUDA_DEVICE?.trim();
  return [
    `"${server}"`,
    `-m "${model.file}"`,
    `--host 127.0.0.1 --port 11434 -c ${PARTNER_MODEL_CONTEXT} -np 1 -fa on -ctk q8_0 -ctv q8_0`,
    "--load-mode none --jinja",
    ...(device ? [`--device ${device}`] : []),
    ...model.extra,
    `-a ${PARTNER_ALIAS_PREFIX}${model.id}`,
  ].join(" ");
}

/** True for a command line that runs a partner model rather than the agent's model. */
export function isPartnerModelCmdline(cmdline: string): boolean {
  return cmdline.includes(`-a ${PARTNER_ALIAS_PREFIX}`);
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

/** The alias the main server reports (llama-server lists only the loaded model). */
async function loadedAlias(): Promise<string | undefined> {
  try {
    const res = await fetch("http://127.0.0.1:11434/v1/models", {
      signal: AbortSignal.timeout(3_000),
    });
    if (!res.ok) return undefined;
    const json = (await res.json()) as { data?: Array<{ id?: string }> };
    return json.data?.[0]?.id;
  } catch {
    return undefined;
  }
}

/**
 * After a generation job the chat model is not restarted at once (media-studio waits a while).
 * A chat message ends the wait: the model starts now and this returns when it answers.
 */
async function wakeChatModel(): Promise<void> {
  if (!(await restoreChatModelIfPending())) return;
  const deadline = Date.now() + READY_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (await isMainReady()) return;
    await new Promise((resolve) => setTimeout(resolve, 2_000));
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

/**
 * The agent model to come back to. The watchdog records whatever runs (a partner model too), so a
 * recorded partner model falls back to the default model rather than becoming "the agent's".
 */
async function agentModelCmdline(): Promise<string> {
  const last = await fs
    .readFile(path.join(bonsaiDir(), "last_main_cmdline.txt"), "utf-8")
    .then((text) => text.trim())
    .catch(() => "");
  if (last && !isPartnerModelCmdline(last)) return last;
  return (await fs.readFile(path.join(bonsaiDir(), "default_main_cmdline.txt"), "utf-8")).trim();
}

let switching: Promise<unknown> = Promise.resolve();

/** Serializes swaps so entering and leaving never interleave. */
function serialized<T>(task: () => Promise<T>): Promise<T> {
  const next = switching.then(task, task);
  switching = next.catch(() => undefined);
  return next;
}

/**
 * Makes sure the partner model serves port 11434 (1:1 and group partner chats call this before
 * every reply). A no-op when it is already loaded, so only the first message after the agent or
 * a media job waits for the swap.
 */
export function enterGroupChatMode(): Promise<{
  modelId: string;
  switched: boolean;
  busy?: boolean;
}> {
  return serialized(async () => {
    await wakeChatModel();
    const modelId = await getGroupChatModelId();
    if (modelId === "main") return { modelId, switched: false };
    // A media job or voice training holds the GPU; the chat model comes back when it ends.
    if (await exists(path.join(bonsaiDir(), "llm-pause.flag")))
      return { modelId, switched: false, busy: true };
    const model = GROUP_CHAT_MODELS.find((m) => m.id === modelId);
    if (!model || !(await exists(model.file))) throw new Error(`model file missing: ${modelId}`);
    if (
      (await exists(previousCmdFile())) &&
      (await loadedAlias()) === `${PARTNER_ALIAS_PREFIX}${model.id}`
    ) {
      return { modelId, switched: false };
    }
    if (!(await exists(previousCmdFile()))) {
      await fs.writeFile(previousCmdFile(), await agentModelCmdline(), "utf-8");
    }
    await swapMainServer(groupModelCmdline(model), path.dirname(mainlineServer()));
    return { modelId, switched: true };
  });
}

/** Swaps the agent's model back in; a no-op outside partner mode. */
export function leaveGroupChatMode(): Promise<{ restored: boolean }> {
  return serialized(async () => {
    if (!(await exists(previousCmdFile()))) return { restored: false };
    const previous = (await fs.readFile(previousCmdFile(), "utf-8")).trim();
    await fs.rm(previousCmdFile(), { force: true });
    if (!previous) return { restored: false };
    // The watchdog and media jobs restart "the last model"; make that the agent's model again.
    await fs.writeFile(path.join(bonsaiDir(), "last_main_cmdline.txt"), previous, "utf-8");
    if (await exists(path.join(bonsaiDir(), "llm-pause.flag"))) return { restored: false };
    await swapMainServer(previous, path.join(bonsaiDir(), "bin"));
    return { restored: true };
  });
}

/**
 * Called before the main agent talks to its model: the partner models write tool calls as text,
 * so the agent's own model must be loaded. Costs one file check outside partner mode.
 */
export async function ensureAgentModel(): Promise<void> {
  await wakeChatModel();
  if (await exists(previousCmdFile())) {
    await leaveGroupChatMode();
  }
}

/**
 * The user picked a model from the home screen: that model is the agent's from now on, so the
 * partner mode forgets the one it would have restored.
 */
export async function forgetPartnerModeState(): Promise<void> {
  await serialized(() => fs.rm(previousCmdFile(), { force: true }));
}
