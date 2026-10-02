// Converges the system-owned context compression jobs at startup and reload.
import { setImmediate as yieldToEventLoop } from "node:timers/promises";
import type { OpenClawConfig } from "../config/types.openclaw.js";
import { partitionSystemMonitors } from "../cron/system-monitor-jobs.js";
import type { CronJob } from "../cron/types.js";
import {
  resolveContextCompressionSpecs,
  contextCompressionJobAgentId,
} from "../tasks/context-compression-task.js";
import type { GatewayCronServiceContract } from "./server-cron-contract.js";

type ContextCompressionJobCron = Pick<GatewayCronServiceContract, "add" | "list" | "remove">;

export async function reconcileContextCompressionJobs(params: {
  cron: ContextCompressionJobCron;
  cfg: OpenClawConfig;
  logger: { warn: (obj: unknown, msg?: string) => void };
  commitGuard?: () => void;
}): Promise<{ ok: boolean }> {
  let ok = true;
  let jobs: CronJob[];
  try {
    jobs = await params.cron.list({ includeDisabled: true });
  } catch (error) {
    params.logger.warn(
      { err: String(error) },
      "cron-context-compression: monitor inventory failed",
    );
    return { ok: false };
  }
  params.commitGuard?.();

  const specs = resolveContextCompressionSpecs(params.cfg);
  const desired = new Set(specs.map((spec) => spec.agentId));
  const { retained, duplicates } = partitionSystemMonitors(jobs, contextCompressionJobAgentId);
  // Let I/O run between mutations, then fence stale passes before wrappers
  // that can stop process owners ahead of their database commit guard.
  for (const { agentId, job } of duplicates) {
    await yieldToEventLoop();
    params.commitGuard?.();
    try {
      await params.cron.remove(job.id, {
        systemOwned: true,
        ...(params.commitGuard ? { commitGuard: params.commitGuard } : {}),
      });
    } catch (error) {
      params.commitGuard?.();
      ok = false;
      params.logger.warn(
        { agentId, err: String(error) },
        "cron-context-compression: duplicate monitor cleanup failed",
      );
    }
  }
  for (const spec of specs) {
    await yieldToEventLoop();
    params.commitGuard?.();
    try {
      await params.cron.add(spec.input, {
        enabledExplicit: true,
        systemOwned: true,
        matchesExisting: (job) => contextCompressionJobAgentId(job) === spec.agentId,
        ...(params.commitGuard ? { commitGuard: params.commitGuard } : {}),
      });
    } catch (error) {
      params.commitGuard?.();
      ok = false;
      params.logger.warn(
        { agentId: spec.agentId, err: String(error) },
        "cron-context-compression: monitor convergence failed",
      );
    }
  }

  for (const [agentId, job] of retained) {
    if (desired.has(agentId)) {
      continue;
    }
    await yieldToEventLoop();
    params.commitGuard?.();
    try {
      await params.cron.remove(job.id, {
        systemOwned: true,
        ...(params.commitGuard ? { commitGuard: params.commitGuard } : {}),
      });
    } catch (error) {
      params.commitGuard?.();
      ok = false;
      params.logger.warn(
        { agentId, err: String(error) },
        "cron-context-compression: stale monitor cleanup failed",
      );
    }
  }
  return { ok };
}
