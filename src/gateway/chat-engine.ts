import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

/**
 * The model that answers chat on port 11434: Gemma (llama-server) or Strata (a 125B MoE on both GPUs
 * plus RAM). The choice is kept in a file; Strata serves on the same port, so every client (browser
 * chat, memory proxy, partners) keeps using the one address.
 *
 * The LLM watchdog only knows llama-server. While Strata runs, the pause flag is kept fresh so the
 * watchdog does not start Gemma next to it.
 */
export type ChatEngine = "gemma" | "strata";
export type ChatEngineRunning = ChatEngine | "none" | "other";

const CHAT_PORT = 11434;
/** The CPU workers (embedding stays: it is tiny); Strata needs most of the 64 GB of RAM. */
const CPU_WORKER_PORTS = [11435, 11437];
const STRATA_DIR = "D:\\media-tools\\Strata";
const STRATA_PYTHON = path.join(STRATA_DIR, ".venv", "Scripts", "python.exe");
const STRATA_SERVER = path.join(STRATA_DIR, "serve", "server.py");
const STRATA_CONFIG = path.join(STRATA_DIR, "strata-iq2_xs.json");
const FLAG_REFRESH_MS = 30 * 60_000;
const READY_TIMEOUT_MS = 8 * 60_000;

function bonsaiDir(): string {
  return path.join(resolveOpenClawHome(), "bonsai");
}
function pauseFlagFile(): string {
  return path.join(bonsaiDir(), "llm-pause.flag");
}
function selectionFile(): string {
  return path.join(resolveOpenClawHome(), "chat-engine.json");
}

export function readChatEngine(): ChatEngine {
  try {
    const parsed = JSON.parse(fs.readFileSync(selectionFile(), "utf-8")) as { engine?: string };
    return parsed.engine === "strata" ? "strata" : "gemma";
  } catch {
    return "gemma";
  }
}

function writeChatEngine(engine: ChatEngine): void {
  fs.mkdirSync(path.dirname(selectionFile()), { recursive: true });
  fs.writeFileSync(selectionFile(), JSON.stringify({ engine, updatedAt: Date.now() }), "utf-8");
}

function touchPauseFlag(): void {
  fs.mkdirSync(bonsaiDir(), { recursive: true });
  fs.writeFileSync(pauseFlagFile(), String(Date.now()));
}

let flagTimer: ReturnType<typeof setInterval> | undefined;
/** Keeps the pause flag fresh while Strata is the chat model (the watchdog ignores a flag older than 8 h). */
export function syncChatEngineFlagKeeper(): void {
  const wanted = readChatEngine() === "strata";
  if (wanted && !flagTimer) {
    flagTimer = setInterval(() => {
      try {
        if (readChatEngine() === "strata") touchPauseFlag();
      } catch {
        // Best effort.
      }
    }, FLAG_REFRESH_MS);
    flagTimer.unref?.();
  } else if (!wanted && flagTimer) {
    clearInterval(flagTimer);
    flagTimer = undefined;
  }
}

/** The process listening on this PC's loopback (or every address) at the port; never the tailnet forward. */
export function listeningLoopbackPid(port: number): Promise<number | undefined> {
  return new Promise((resolve) => {
    execFile(
      "netstat",
      ["-ano", "-p", "TCP"],
      { windowsHide: true, timeout: 15_000 },
      (err, stdout) => {
        if (err) return resolve(undefined);
        for (const line of stdout.split(/\r?\n/)) {
          const cols = line.trim().split(/\s+/);
          if (
            cols[3] === "LISTENING" &&
            (cols[1] === `127.0.0.1:${port}` ||
              cols[1] === `0.0.0.0:${port}` ||
              cols[1] === `[::1]:${port}` ||
              cols[1] === `[::]:${port}`)
          )
            return resolve(Number(cols[4]) || undefined);
        }
        resolve(undefined);
      },
    );
  });
}

function run(file: string, args: string[], timeoutMs = 20_000): Promise<string> {
  return new Promise((resolve) => {
    execFile(file, args, { windowsHide: true, timeout: timeoutMs }, (_err, stdout) =>
      resolve(String(stdout ?? "")),
    );
  });
}

/** Starts a process that outlives the gateway (the same way the LLM watchdog starts llama-server). */
function startDetached(commandLine: string, cwd: string): Promise<string> {
  const esc = (s: string) => s.replace(/'/g, "''");
  return run(
    "powershell.exe",
    [
      "-NoProfile",
      "-Command",
      `$r = ([wmiclass]'Win32_Process').Create('${esc(commandLine)}', '${esc(cwd)}', $null); "$($r.ReturnValue) $($r.ProcessId)"`,
    ],
    60_000,
  );
}

/** Which model answers on the chat port right now. */
export async function probeChatEngine(): Promise<ChatEngineRunning> {
  try {
    const res = await fetch(`http://127.0.0.1:${CHAT_PORT}/v1/models`, {
      signal: AbortSignal.timeout(2_500),
    });
    if (!res.ok) return "none";
    const body = (await res.json()) as { data?: Array<{ id?: string }> };
    const id = (body.data?.[0]?.id ?? "").toLowerCase();
    if (id.includes("qwen3.8-flash") || id.includes("strata")) return "strata";
    if (id.includes("gemma")) return "gemma";
    return "other";
  } catch {
    return "none";
  }
}

/** Stops whatever serves the chat port (Gemma's llama-server or Strata and its helper processes). */
export async function stopChatModelProcesses(): Promise<void> {
  const pid = await listeningLoopbackPid(CHAT_PORT);
  if (pid) await run("taskkill", ["/F", "/T", "/PID", String(pid)]);
  await stopStrataProcesses();
  for (let i = 0; i < 30 && (await listeningLoopbackPid(CHAT_PORT)); i++) {
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

/** Strata's engine and vision helper may outlive the Python server; they hold the GPUs. */
export async function stopStrataProcesses(): Promise<void> {
  await run("taskkill", ["/F", "/IM", "strata.exe"]);
  await run("taskkill", ["/F", "/IM", "strata-vision.exe"]);
}

/** Waits (at most 40 s) until both GPUs hold almost nothing, so the next model sees the whole VRAM. */
async function waitGpusFree(): Promise<void> {
  for (let i = 0; i < 20; i++) {
    const out = await run(
      "nvidia-smi",
      ["--query-gpu=memory.used", "--format=csv,noheader,nounits"],
      10_000,
    );
    const used = out
      .split(/\r?\n/)
      .map((line) => Number.parseInt(line.trim(), 10))
      .filter((n) => Number.isFinite(n));
    if (used.length === 0 || used.every((n) => n < 1_500)) return;
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
}

async function stopCpuWorkers(): Promise<void> {
  for (const port of CPU_WORKER_PORTS) {
    const pid = await listeningLoopbackPid(port);
    if (pid) await run("taskkill", ["/F", "/PID", String(pid)]);
  }
}

async function startGemma(): Promise<void> {
  const last = path.join(bonsaiDir(), "last_main_cmdline.txt");
  const def = path.join(bonsaiDir(), "default_main_cmdline.txt");
  let cmd = "";
  for (const file of [last, def]) {
    try {
      cmd = fs.readFileSync(file, "utf-8").trim();
      if (cmd) break;
    } catch {
      // Try the next file.
    }
  }
  if (!cmd) throw new Error("Gemma の起動コマンドが見つかりません");
  await startDetached(cmd, path.join(bonsaiDir(), "bin"));
}

async function startStrata(): Promise<void> {
  const cmd = `"${STRATA_PYTHON}" "${STRATA_SERVER}" --engine strata --config "${STRATA_CONFIG}" --port ${CHAT_PORT}`;
  await startDetached(cmd, STRATA_DIR);
}

/** Starts the chosen chat model now (no waiting); used after a media job gave the GPUs back. */
export async function startSelectedChatEngine(): Promise<ChatEngine> {
  const engine = readChatEngine();
  if (engine === "strata") {
    touchPauseFlag();
    syncChatEngineFlagKeeper();
    if ((await probeChatEngine()) !== "strata" && !(await listeningLoopbackPid(CHAT_PORT)))
      await startStrata();
  }
  return engine;
}

let switching: { to: ChatEngine; startedAt: number } | undefined;
let lastError = "";
let busyCheck: (() => boolean) | undefined;

/** The media studio tells whether a job runs (a switch would cut it off). */
export function setChatEngineBusyCheck(fn: () => boolean): void {
  busyCheck = fn;
}

export async function chatEngineStatus(): Promise<{
  selected: ChatEngine;
  running: ChatEngineRunning;
  switching: boolean;
  switchingTo?: ChatEngine;
  elapsedSec?: number;
  error?: string;
}> {
  syncChatEngineFlagKeeper();
  return {
    selected: readChatEngine(),
    running: await probeChatEngine(),
    switching: Boolean(switching),
    ...(switching
      ? {
          switchingTo: switching.to,
          elapsedSec: Math.round((Date.now() - switching.startedAt) / 1000),
        }
      : {}),
    ...(lastError ? { error: lastError } : {}),
  };
}

/** Switches the chat model; returns at once, the switch runs in the background (poll chatEngineStatus). */
export async function switchChatEngine(
  target: ChatEngine,
): Promise<{ started: boolean; reason?: string }> {
  if (switching) return { started: false, reason: "いま切り替え中です" };
  if (busyCheck?.())
    return { started: false, reason: "画像・動画などを作っている間は切り替えられません" };
  if ((await probeChatEngine()) === target && readChatEngine() === target) {
    return { started: false, reason: "すでにそのモデルです" };
  }
  lastError = "";
  switching = { to: target, startedAt: Date.now() };
  void (async () => {
    try {
      writeChatEngine(target);
      syncChatEngineFlagKeeper();
      // The pause flag stays up while the new model loads, so the watchdog does not start Gemma next to it.
      touchPauseFlag();
      await stopChatModelProcesses();
      if (target === "strata") await stopCpuWorkers();
      // Gemma fits itself to the free VRAM at start: a start while the old model still lets go of the GPUs
      // leaves it half on the CPU with a short context.
      await waitGpusFree();
      if (target === "strata") await startStrata();
      else await startGemma();
      const deadline = Date.now() + READY_TIMEOUT_MS;
      let ready = false;
      while (Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 5_000));
        if ((await probeChatEngine()) === target) {
          ready = true;
          break;
        }
      }
      if (target === "gemma") fs.rmSync(pauseFlagFile(), { force: true });
      if (!ready) lastError = "起動を確認できませんでした（時間切れ）";
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
      // Do not hold the watchdog off for good when the start failed.
      if (target === "gemma") fs.rmSync(pauseFlagFile(), { force: true });
    } finally {
      switching = undefined;
    }
  })();
  return { started: true };
}

syncChatEngineFlagKeeper();
