import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { mutateConfigFileWithRetry } from "../../config/config.js";
import type { GatewayRequestHandlers } from "./types.js";

export interface FeatureSettings {
  webSearch: {
    enabled: boolean;
    provider: string; // "duckduckgo" | "brave" | "google" | "searxng"
    autoSaveToMemory: boolean;
    apiKey?: string; // Brave Search API / Google Custom Search API キー
    googleCseId?: string; // Google Custom Search Engine ID
    searxngBaseUrl?: string; // SearXNG インスタンスのベースURL
  };
  earthquake: {
    enabled: boolean;
    minIntensity: string;
    targetRegion: string;
    notifySound: boolean;
  };
}

const DEFAULT_FEATURE_SETTINGS: FeatureSettings = {
  webSearch: {
    enabled: true,
    provider: "duckduckgo",
    autoSaveToMemory: true,
    apiKey: "",
    googleCseId: "",
    searxngBaseUrl: "",
  },
  earthquake: {
    enabled: true,
    minIntensity: "3",
    targetRegion: "全国",
    notifySound: true,
  },
};

function getFeaturesFilePath(): string {
  const home = process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
  return path.join(home, "features.json");
}

async function loadFeatureSettings(): Promise<FeatureSettings> {
  const filePath = getFeaturesFilePath();
  try {
    const data = await fs.readFile(filePath, "utf-8");
    const parsed = JSON.parse(data);
    return {
      webSearch: { ...DEFAULT_FEATURE_SETTINGS.webSearch, ...parsed.webSearch },
      earthquake: { ...DEFAULT_FEATURE_SETTINGS.earthquake, ...parsed.earthquake },
    };
  } catch {
    await saveFeatureSettings(DEFAULT_FEATURE_SETTINGS);
    return DEFAULT_FEATURE_SETTINGS;
  }
}

async function saveFeatureSettings(settings: FeatureSettings): Promise<void> {
  const filePath = getFeaturesFilePath();
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, JSON.stringify(settings, null, 2), "utf-8");
}

export const featuresHandlers: GatewayRequestHandlers = {
  "features.get": async ({ respond }) => {
    try {
      const settings = await loadFeatureSettings();
      respond(true, { settings }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },

  "features.update": async ({ params, respond }) => {
    // SAFETY: params is optional partial settings payload validated manually.
    const incoming = (params || {}) as Partial<FeatureSettings>;
    try {
      const current = await loadFeatureSettings();
      const updated: FeatureSettings = {
        webSearch: {
          ...current.webSearch,
          ...(incoming.webSearch || {}),
        },
        earthquake: {
          ...current.earthquake,
          ...(incoming.earthquake || {}),
        },
      };

      await saveFeatureSettings(updated);

      // O-4 修正: webSearch設定が変更された場合のみopenclaw.jsonを書き換える
      // （地震通知のみの変更時に不要なconfigファイル書き込みを防止）
      const webSearchChanged = incoming.webSearch != null;
      if (webSearchChanged) {
        try {
          // Patch only tools.web.search.provider in the authored config; a whole-file
          // write from a partial object would drop every other setting.
          if (updated.webSearch.enabled) {
            await mutateConfigFileWithRetry({
              base: "source",
              afterWrite: { mode: "auto" },
              mutate: (draft) => {
                draft.tools = {
                  ...draft.tools,
                  web: {
                    ...draft.tools?.web,
                    search: { ...draft.tools?.web?.search, provider: updated.webSearch.provider },
                  },
                };
              },
            });
          }
        } catch (configErr) {
          console.warn("Could not sync feature settings to openclaw.json:", configErr);
        }
      }

      respond(true, { settings: updated }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INTERNAL_ERROR, String(e)));
    }
  },
};
