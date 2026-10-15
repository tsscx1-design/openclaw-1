import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  listeningLoopbackPid,
  readChatEngine,
  setChatEngineBusyCheck,
  startSelectedChatEngine,
  stopStrataProcesses,
} from "../chat-engine.js";
import { registerChatModelRestore } from "../chat-model-gate.js";
import { listCharacters, saveCharacter } from "../comic-characters.js";
import { stopIrodoriWorker } from "../irodori-tts.js";
import { hasDedicatedMediaGpu } from "../media-gpu.js";
import { queue, working } from "../media-studio.js";
import { SPECIALIST_WORKERS } from "../specialist-router.js";
import { listVoiceProjects } from "../voice-studio.js";
import { exists, home, lastMainCmdline, pauseFlag } from "./paths.js";
import type { MediaJob } from "./types.js";

// ---------- GPU lease ----------

export let pausedByUs = false;

/**
 * The chat model comes back this long after the last job, not at once: jobs often follow each other
 * and every restart costs 1-1.5 minutes of loading. A chat message brings it back sooner.
 */
const CHAT_RESTORE_DELAY_MS = 10 * 60_000;

let chatRestoreTimer: ReturnType<typeof setTimeout> | undefined;

function cancelChatRestoreTimer(): void {
  if (chatRestoreTimer) clearTimeout(chatRestoreTimer);
  chatRestoreTimer = undefined;
}

// A chat message that arrives while the model waits to be restored starts it right away.
// The chat model switch (Gemma / Strata) must not cut a running job off.
setChatEngineBusyCheck(() => working || queue.length > 0);

registerChatModelRestore(async () => {
  if (!chatRestoreTimer || working || queue.length > 0) return false;
  await restoreChatModel();
  return true;
});

function runPowerShell(command: string): Promise<string> {
  return new Promise((resolve) => {
    const ps = spawn("powershell.exe", ["-NoProfile", "-Command", command], {
      windowsHide: true,
      stdio: ["ignore", "pipe", "ignore"],
    });
    let out = "";
    ps.stdout?.on("data", (chunk) => (out += String(chunk)));
    ps.on("exit", () => resolve(out.trim()));
    ps.on("error", () => resolve(out.trim()));
  });
}

export async function acquireGpu(job: MediaJob): Promise<void> {
  const speedPriority = job.options.gpuMode === "fast";
  cancelChatRestoreTimer();
  if (hasDedicatedMediaGpu() && !speedPriority) {
    if (pausedByUs) await restoreChatModel();
    return;
  }
  // The small Irodori model may be resident next to the chat model for partner voices.
  stopIrodoriWorker();
  // Voice training may already hold the flag; then it owns the restart too.
  const strataChat = readChatEngine() === "strata";
  if (!(await exists(pauseFlag()))) {
    await fs.writeFile(pauseFlag(), String(Date.now()));
    pausedByUs = true;
  } else if (strataChat) {
    // Strata keeps the flag up all the time; the chat model still has to come back after this job.
    pausedByUs = true;
  }
  // netstat + kill instead of PowerShell: PowerShell starts very slowly when the gateway runs
  // as a scheduled task, and this sits on the critical path of every job.
  const pid = await listeningPid(11434);
  if (pid) {
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
    // Strata's engine and vision helper hold the GPUs and may outlive its Python server.
    if (strataChat) await stopStrataProcesses();
    for (let i = 0; i < 20 && (await listeningPid(11434)); i++) {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
}

function listeningPid(port: number): Promise<number | undefined> {
  // Only a listener on this PC (loopback or all addresses). A Tailscale forward listens on the
  // tailnet address of the same port (11434) and must never be taken for the model server.
  return listeningLoopbackPid(port);
}

/**
 * Stops the CPU worker LLMs (~16 GB of RAM) for jobs that need nearly all memory; whoever uses
 * a worker next starts it again on demand.
 */
export async function stopCpuWorkers(): Promise<void> {
  for (const worker of SPECIALIST_WORKERS) {
    const pid = await listeningPid(worker.port);
    if (!pid) continue;
    try {
      process.kill(pid);
    } catch {
      // Already gone.
    }
  }
}

/**
 * True while the chat model is paused for a job. A flag left behind (the gateway was restarted
 * while a job ran, so nobody releases it) is removed once nothing is running or waiting and the
 * flag is old, so the chat AI is not paused for ever.
 */
export async function clearStalePauseFlag(): Promise<boolean> {
  const flag = pauseFlag();
  if (!(await exists(flag))) return false;
  const age =
    Date.now() -
    (await fs
      .stat(flag)
      .then((s) => s.mtimeMs)
      .catch(() => Date.now()));
  const idle = !working && queue.length === 0 && !pausedByUs;
  if (idle && age > 15 * 60_000 && !(await voiceTrainingActive())) {
    await fs.rm(flag, { force: true });
    return false;
  }
  return true;
}

async function voiceTrainingActive(): Promise<boolean> {
  try {
    return (await listVoiceProjects()).some((project) => project.status === "training");
  } catch {
    return false;
  }
}

/** Called when the queue is empty: the chat model returns after a delay (see CHAT_RESTORE_DELAY_MS). */
export async function releaseGpu(): Promise<void> {
  if (!pausedByUs) return;
  cancelChatRestoreTimer();
  chatRestoreTimer = setTimeout(() => {
    chatRestoreTimer = undefined;
    if (working || queue.length > 0) return;
    void restoreChatModel().catch(() => undefined);
  }, CHAT_RESTORE_DELAY_MS);
  chatRestoreTimer.unref?.();
}

/** Starts the chat model again now: removes the pause flag and does not wait for the watchdog. */
export async function restoreChatModel(): Promise<void> {
  cancelChatRestoreTimer();
  if (!pausedByUs) return;
  pausedByUs = false;
  // Strata is the chosen chat model: start it again and keep the pause flag (the watchdog only knows Gemma).
  if (readChatEngine() === "strata") {
    await startSelectedChatEngine();
    return;
  }
  await fs.rm(pauseFlag(), { force: true });
  // Bring the chat model back right away instead of waiting for the watchdog (~1 minute).
  if (await exists(lastMainCmdline())) {
    await runPowerShell(
      `if (-not (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue)) { $cmd = (Get-Content '${lastMainCmdline()}' -Raw).Trim(); ([wmiclass]'Win32_Process').Create($cmd, '${path.join(home(), "bonsai", "bin")}', $null) | Out-Null }`,
    );
  }
}

/** How many pictures (with their caption) the training dataset folder holds. */
export async function trainingDatasetCount(imageDir: string): Promise<number> {
  const names = await fs.readdir(imageDir).catch(() => [] as string[]);
  const pictures = names.filter((name) => /\.(png|jpe?g|webp)$/i.test(name));
  return pictures.filter((name) => names.includes(name.replace(/\.\w+$/, ".txt"))).length;
}

/** The folder of the last saved training state of a run (kept by --save_state), or undefined. */
export async function latestTrainingState(
  dir: string,
  outName: string,
): Promise<string | undefined> {
  const names = (await fs.readdir(dir).catch(() => [] as string[]))
    .filter((name) => name.startsWith(`${outName}-`) && name.endsWith("-state"))
    .sort();
  return names.length > 0 ? path.join(dir, names[names.length - 1]!) : undefined;
}

/**
 * A training that was running when the gateway stopped is left marked "training" with nothing running: it is marked as
 * cut off, and pressing "train" again goes on from where it was (the pictures and the saved epoch are kept).
 */
async function markCutOffTrainings(): Promise<void> {
  if (working || queue.length > 0) return;
  for (const character of await listCharacters()) {
    if (character.loraStatus !== "training" && character.loraStatus !== "queued") continue;
    await saveCharacter({
      ...character,
      loraStatus: "failed",
      loraStep: undefined,
      loraError:
        "中断しました（ゲートウェイの再起動など）。もう一度「学習」を押すと、続きから始まります",
      updatedAt: Date.now(),
    });
  }
}

/**
 * The timer that brings the chat model back after a job lives in memory, so a gateway restart during or just after a job
 * left the pause flag behind and the chat model stopped for good. Some time after the gateway starts, a flag with no job
 * running is taken as such a leftover: the chat model is started again.
 */
async function restoreStuckChatModel(): Promise<void> {
  if (working || queue.length > 0) return;
  if (!(await exists(pauseFlag()))) return;
  pausedByUs = true;
  await restoreChatModel().catch(() => undefined);
}

if (!process.env.VITEST) {
  const stuck = setTimeout(
    () =>
      void restoreStuckChatModel()
        .then(markCutOffTrainings)
        .catch(() => undefined),
    90_000,
  );
  stuck.unref?.();
}
