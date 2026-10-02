/** Canonical projection from config to system-owned context compression cron jobs. */
import { listAgentIds } from "../agents/agent-scope.js";
import type { OpenClawConfig } from "../config/types.openclaw.js";
import { CONTEXT_COMPRESSION_DECLARATION_PREFIX } from "../cron/system-owned-declaration.js";
import type { CronJob, CronJobCreate } from "../cron/types.js";
import { resolveHeartbeatSchedulerSeed } from "../infra/heartbeat-runner.js";
import { resolveHeartbeatPhaseMs } from "../infra/heartbeat-schedule.js";

// Run context compression checks every 1 hour by default
const CONTEXT_COMPRESSION_EVERY_MS = 60 * 60_000;

export function contextCompressionJobAgentId(job: CronJob): string | undefined {
  const key = job.declarationKey;
  if (!key?.startsWith(CONTEXT_COMPRESSION_DECLARATION_PREFIX)) {
    return undefined;
  }
  return key.slice(CONTEXT_COMPRESSION_DECLARATION_PREFIX.length) || undefined;
}

/** One system-owned compression job per configured agent. */
export function resolveContextCompressionSpecs(
  cfg: OpenClawConfig,
  options: { schedulerSeed?: string } = {},
): Array<{ agentId: string; input: CronJobCreate }> {
  const schedulerSeed = resolveHeartbeatSchedulerSeed(options.schedulerSeed);
  // Default to enabled for context compression
  const enabled = true;
  return listAgentIds(cfg).map((agentId) => ({
    agentId,
    input: {
      declarationKey: `${CONTEXT_COMPRESSION_DECLARATION_PREFIX}${agentId}`,
      name: `context-compression-${agentId}`,
      displayName: `Context compression (${agentId})`,
      agentId,
      enabled,
      schedule: {
        kind: "every",
        everyMs: CONTEXT_COMPRESSION_EVERY_MS,
        anchorMs: resolveHeartbeatPhaseMs({
          schedulerSeed,
          agentId,
          intervalMs: CONTEXT_COMPRESSION_EVERY_MS,
        }),
      },
      payload: {
        kind: "agentTurn",
        message: "/compact",
        toolsAllow: [],
        toolsAllowIsDefault: true,
      },
      sessionTarget: "isolated",
      delivery: { mode: "none" },
      wakeMode: "now",
    },
  }));
}
