import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { readPcHealth } from "../pc-health.js";
import { listSpecialistJobs } from "../specialist-jobs.js";
import {
  executeSpecialistTask,
  getSpecialistSquadStatus,
  SPECIALIST_WORKERS,
  startSpecialistWorker,
  stopSpecialistWorker,
} from "../specialist-router.js";
import type { GatewayRequestHandlers } from "./types.js";

export const specialistHandlers: GatewayRequestHandlers = {
  /** Recent background jobs (delegations, partner creation), newest first. */
  /** PC vitals for the 24/7 host: GPU/CPU/RAM/disk, service liveness and an overall level. */
  "pc.health.get": async ({ respond }) => {
    try {
      respond(true, { health: await readPcHealth() }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },
  "specialist.jobs.list": async ({ respond }) => {
    respond(true, { jobs: listSpecialistJobs() }, undefined);
  },

  /**
   * 専門モデル部隊の現在のステータス一覧を取得
   */
  "specialist.status": async ({ respond }) => {
    try {
      const status = await getSpecialistSquadStatus();
      respond(true, { squad: status }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  /**
   * 専門モデルワーカーの起動
   */
  "specialist.start": async ({ params, respond }) => {
    try {
      // SAFETY: params payload is manually validated for optional worker string.
      const { worker } = (params || {}) as { worker?: string };
      const targetWorker = worker || "general";

      if (targetWorker === "all") {
        const results = [];
        for (const w of SPECIALIST_WORKERS) {
          const res = await startSpecialistWorker(w.key);
          results.push({ worker: w.key, ...res });
        }
        respond(true, { results }, undefined);
      } else {
        const result = await startSpecialistWorker(targetWorker);
        respond(true, result, undefined);
      }
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  /**
   * 専門モデルワーカーの停止
   */
  "specialist.stop": async ({ params, respond }) => {
    try {
      // SAFETY: params payload is manually validated for optional worker string.
      const { worker } = (params || {}) as { worker?: string };
      const targetWorker = worker || "general";

      if (targetWorker === "all") {
        const results = [];
        for (const w of SPECIALIST_WORKERS) {
          const res = await stopSpecialistWorker(w.key);
          results.push({ worker: w.key, ...res });
        }
        respond(true, { results }, undefined);
      } else {
        const result = await stopSpecialistWorker(targetWorker);
        respond(true, result, undefined);
      }
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  /**
   * 専門モデル部隊へのタスク発行・実行
   */
  "specialist.run": async ({ params, respond }) => {
    try {
      // SAFETY: params payload is manually validated for specialist run parameters.
      const { prompt, taskType, systemPrompt, port, maxTokens, temperature } = (params || {}) as {
        prompt?: string;
        taskType?: string;
        systemPrompt?: string;
        port?: number;
        maxTokens?: number;
        temperature?: number;
      };

      if (!prompt) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, "Missing prompt parameter"),
        );
        return;
      }

      const result = await executeSpecialistTask({
        prompt,
        taskType,
        systemPrompt,
        port,
        maxTokens,
        temperature,
      });

      respond(true, result, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  /**
   * 専門モデル定義一覧
   */
  "specialist.models": async ({ respond }) => {
    try {
      respond(true, { workers: SPECIALIST_WORKERS }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },
};
