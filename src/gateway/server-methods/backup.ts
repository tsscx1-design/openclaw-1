import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { readConfigFileSnapshotForWrite, writeConfigFile } from "../../config/io.runtime.js";
import type { GatewayRequestHandlers } from "./types.js";

function getOpenClawHome(): string {
  return process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
}

export const backupHandlers: GatewayRequestHandlers = {
  "system.backup.create": async ({ respond }) => {
    try {
      const openclawHome = getOpenClawHome();
      const openclawJsonPath = path.join(openclawHome, "openclaw.json");
      const partnersJsonPath = path.join(openclawHome, "partners.json");

      let openclawConfig = "";
      try {
        openclawConfig = await fs.readFile(openclawJsonPath, "utf-8");
      } catch {
        const { snapshot } = await readConfigFileSnapshotForWrite();
        openclawConfig = JSON.stringify(snapshot.sourceConfig, null, 2);
      }

      let partnersConfig = "[]";
      try {
        partnersConfig = await fs.readFile(partnersJsonPath, "utf-8");
      } catch {
        // partners.json may not exist yet
      }

      const backup = {
        version: 1,
        timestamp: Date.now(),
        hostname: os.hostname(),
        openclawConfig,
        partnersConfig,
      };

      respond(true, backup, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "system.backup.restore": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const data = params as { openclawConfig?: string; partnersConfig?: string };
    if (
      !data ||
      (typeof data.openclawConfig !== "string" && typeof data.partnersConfig !== "string")
    ) {
      respond(
        false,
        undefined,
        errorShape(
          ErrorCodes.INVALID_REQUEST,
          "Missing openclawConfig or partnersConfig in payload",
        ),
      );
      return;
    }

    try {
      const openclawHome = getOpenClawHome();
      await fs.mkdir(openclawHome, { recursive: true });

      if (data.openclawConfig) {
        // Validate JSON before writing
        const parsed = JSON.parse(data.openclawConfig);
        await writeConfigFile(parsed);
      }

      if (data.partnersConfig) {
        // B-6 修正: パース結果が配列であることを検証
        const parsed = JSON.parse(data.partnersConfig);
        if (!Array.isArray(parsed)) {
          respond(
            false,
            undefined,
            errorShape(ErrorCodes.INVALID_REQUEST, "partnersConfig must be a JSON array"),
          );
          return;
        }
        const partnersJsonPath = path.join(openclawHome, "partners.json");
        // E-3準拠: アトミック書き込み
        const tmpFile = partnersJsonPath + `.tmp-${Date.now()}`;
        await fs.writeFile(tmpFile, data.partnersConfig, "utf-8");
        await fs.rename(tmpFile, partnersJsonPath);
      }

      // K-5 修正: クライアントにGateway再読み込みが必要なことを通知
      respond(
        true,
        {
          success: true,
          message: "Gateway configuration restored successfully",
          reloadNeeded: true,
        },
        undefined,
      );
    } catch (e) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INTERNAL_ERROR, `Failed to restore config: ${String(e)}`),
      );
    }
  },
};
