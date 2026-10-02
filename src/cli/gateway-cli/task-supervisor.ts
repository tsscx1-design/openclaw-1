// Windows Task Scheduler bridge: retain the Job Object owner until the Gateway exits.
import { quoteCmdScriptArg } from "../../daemon/cmd-argv.js";
import { WINDOWS_TASK_SUPERVISOR_FLAG } from "../../daemon/windows-task-supervisor-contract.js";
import { readGatewayRestartHandoffSync } from "../../infra/restart-handoff.js";
import { getProcessSupervisor } from "../../process/supervisor/index.js";

function renderGatewayTaskCommand(): string {
  const childArgs = [...process.execArgv, ...process.argv.slice(1)].filter(
    (argument) => argument !== WINDOWS_TASK_SUPERVISOR_FLAG,
  );
  if (childArgs.length === 0) {
    throw new Error("Windows task supervisor could not resolve the Gateway command");
  }
  return [process.execPath, ...childArgs].map((argument) => quoteCmdScriptArg(argument)).join(" ");
}

/**
 * A clean exit is a planned supervisor restart only when that child generation
 * persisted a restart handoff after it started. Stops, crashes, and stale rows end the task.
 */
function childRequestedSupervisorRestart(generationStartedAt: number): boolean {
  const handoff = readGatewayRestartHandoffSync(process.env);
  return handoff !== null && handoff.createdAt >= generationStartedAt;
}

/**
 * Runs the real Gateway inside the Windows Job Object owned by ProcessSupervisor.
 * The Task Scheduler action waits on this process; parent loss closes the Job and
 * its entire child tree, so a detached launcher cannot leave a stale Gateway behind.
 *
 * The Gateway's own restart helper cannot relaunch the task from inside this Job:
 * the task stays Running until every Job process (including that helper) exits, so
 * Task Scheduler drops the relaunch. This supervisor therefore owns planned restarts.
 */
export async function runWindowsGatewayTaskSupervisor(): Promise<void> {
  if (process.platform !== "win32") {
    throw new Error("--task-supervisor is only available to the Windows Gateway service");
  }
  const command = renderGatewayTaskCommand();
  let stopping = false;
  let cancelCurrent: (() => void) | undefined;
  const cancel = () => {
    stopping = true;
    cancelCurrent?.();
  };
  process.once("SIGINT", cancel);
  process.once("SIGTERM", cancel);
  try {
    for (let generation = 0; ; generation += 1) {
      const generationStartedAt = Date.now();
      const managed = await getProcessSupervisor().spawn({
        mode: "anchored-shell",
        command,
        scopeKey:
          generation === 0
            ? `gateway-task-supervisor:${process.pid}`
            : `gateway-task-supervisor:${process.pid}:${generation}`,
        captureOutput: false,
      });
      cancelCurrent = () => managed.cancel("signal");
      if (stopping) {
        cancelCurrent();
      }
      const result = await managed.wait();
      await managed.waitForExtinction?.();
      cancelCurrent = undefined;
      if (
        !stopping &&
        result.exitCode === 0 &&
        childRequestedSupervisorRestart(generationStartedAt)
      ) {
        continue;
      }
      if (result.exitCode !== 0) {
        process.exitCode = result.exitCode ?? 1;
      }
      return;
    }
  } finally {
    process.removeListener("SIGINT", cancel);
    process.removeListener("SIGTERM", cancel);
  }
}
