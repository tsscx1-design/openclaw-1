import { spawn, execFile, type ChildProcess } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { mediaProcessEnv } from "./media-gpu.js";

// Anima (CircleStone Labs, 2B anime model) runs in ComfyUI, which is started for the job, asked over
// its local API (127.0.0.1 only) and stopped again so the GPU memory goes back to the chat model.

export const COMFY_DIR = "D:\\media-tools\\ComfyUI";
export const COMFY_PYTHON = path.join(COMFY_DIR, "venv", "Scripts", "python.exe");
const COMFY_PORT = 8188;
export const ANIMA_FILES = [
  path.join(COMFY_DIR, "models", "diffusion_models", "anima-aesthetic-v1.1.safetensors"),
  path.join(COMFY_DIR, "models", "diffusion_models", "anima-turbo-v1.1.safetensors"),
  path.join(COMFY_DIR, "models", "text_encoders", "qwen_3_06b_base.safetensors"),
  path.join(COMFY_DIR, "models", "vae", "qwen_image_vae.safetensors"),
];
/** What the model card suggests for a negative prompt. */
export const ANIMA_NEGATIVE =
  "worst quality, low quality, score_1, score_2, score_3, blurry, jpeg artifacts, sepia";

export type AnimaRequest = {
  positive: string;
  negative: string;
  width: number;
  height: number;
  seed: number;
  /** The distilled model: about 10 steps with CFG 1 instead of 30 steps with CFG 4. */
  turbo: boolean;
};

/** The ComfyUI graph (API format) of a text-to-image run, as in ComfyUI's own Anima template. */
export function buildAnimaGraph(
  request: AnimaRequest,
): Record<string, { class_type: string; inputs: Record<string, unknown> }> {
  return {
    "1": {
      class_type: "UNETLoader",
      inputs: {
        unet_name: request.turbo
          ? "anima-turbo-v1.1.safetensors"
          : "anima-aesthetic-v1.1.safetensors",
        weight_dtype: "default",
      },
    },
    "2": {
      class_type: "CLIPLoader",
      inputs: {
        clip_name: "qwen_3_06b_base.safetensors",
        type: "stable_diffusion",
        device: "default",
      },
    },
    "3": { class_type: "VAELoader", inputs: { vae_name: "qwen_image_vae.safetensors" } },
    "4": { class_type: "CLIPTextEncode", inputs: { text: request.positive, clip: ["2", 0] } },
    "5": { class_type: "CLIPTextEncode", inputs: { text: request.negative, clip: ["2", 0] } },
    "6": {
      class_type: "EmptyLatentImage",
      inputs: { width: request.width, height: request.height, batch_size: 1 },
    },
    "7": {
      class_type: "KSampler",
      inputs: {
        model: ["1", 0],
        positive: ["4", 0],
        negative: ["5", 0],
        latent_image: ["6", 0],
        seed: request.seed,
        steps: request.turbo ? 10 : 30,
        cfg: request.turbo ? 1 : 4,
        sampler_name: "euler",
        scheduler: "simple",
        denoise: 1,
      },
    },
    "8": { class_type: "VAEDecode", inputs: { samples: ["7", 0], vae: ["3", 0] } },
    "9": {
      class_type: "SaveImage",
      inputs: { images: ["8", 0], filename_prefix: "openclaw-anima" },
    },
  };
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function killTree(child: ChildProcess | undefined): Promise<void> {
  if (!child?.pid) return Promise.resolve();
  return new Promise((resolve) => {
    execFile("taskkill", ["/PID", String(child.pid), "/T", "/F"], { windowsHide: true }, () =>
      resolve(),
    );
  });
}

async function getJson(url: string): Promise<unknown> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`ComfyUI ${res.status}`);
  return res.json();
}

/**
 * Starts ComfyUI, draws one picture and stops ComfyUI. `onStage` gets short Japanese progress notes;
 * `isCancelled` is polled while waiting.
 */
export async function renderAnima(
  request: AnimaRequest,
  output: string,
  hooks: { onStage: (stage: string) => void; isCancelled: () => boolean; cudaDevice?: string },
): Promise<void> {
  const base = `http://127.0.0.1:${COMFY_PORT}`;
  hooks.onStage("Anima（ComfyUI）を起動しています");
  const child = spawn(
    COMFY_PYTHON,
    [
      "main.py",
      "--listen",
      "127.0.0.1",
      "--port",
      String(COMFY_PORT),
      "--disable-auto-launch",
      "--disable-metadata",
      "--offline",
    ],
    {
      cwd: COMFY_DIR,
      windowsHide: true,
      stdio: "ignore",
      env: mediaProcessEnv({ PYTHONUTF8: "1", PYTHONIOENCODING: "utf-8" }, hooks.cudaDevice),
    },
  );
  let exited = false;
  child.on("exit", () => {
    exited = true;
  });
  try {
    const startedAt = Date.now();
    for (;;) {
      if (hooks.isCancelled()) throw new Error("中止しました");
      if (exited) throw new Error("ComfyUI が起動できませんでした");
      if (Date.now() - startedAt > 4 * 60_000)
        throw new Error("ComfyUI の起動がタイムアウトしました");
      try {
        await getJson(`${base}/system_stats`);
        break;
      } catch {
        await sleep(1_500);
      }
    }
    hooks.onStage("Anima で絵を描いています");
    const res = await fetch(`${base}/prompt`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: buildAnimaGraph(request), client_id: "openclaw" }),
    });
    const queued = (await res.json()) as {
      prompt_id?: string;
      error?: unknown;
      node_errors?: unknown;
    };
    if (!res.ok || !queued.prompt_id) {
      throw new Error(
        `Anima に頼めませんでした: ${JSON.stringify(queued.node_errors ?? queued.error ?? res.status).slice(0, 300)}`,
      );
    }
    const waitedFrom = Date.now();
    for (;;) {
      if (hooks.isCancelled()) throw new Error("中止しました");
      if (exited) throw new Error("ComfyUI が途中で終了しました");
      if (Date.now() - waitedFrom > 20 * 60_000)
        throw new Error("Anima の生成がタイムアウトしました");
      const history = (await getJson(`${base}/history/${queued.prompt_id}`)) as Record<
        string,
        {
          outputs?: Record<
            string,
            { images?: Array<{ filename: string; subfolder: string; type: string }> }
          >;
          status?: { status_str?: string };
        }
      >;
      const entry = history[queued.prompt_id];
      if (entry?.status?.status_str === "error") throw new Error("Anima の生成でエラーが出ました");
      const image = Object.values(entry?.outputs ?? {}).flatMap((o) => o.images ?? [])[0];
      if (image) {
        const query = new URLSearchParams({
          filename: image.filename,
          subfolder: image.subfolder,
          type: image.type,
        });
        const picture = await fetch(`${base}/view?${query.toString()}`);
        if (!picture.ok) throw new Error("できた絵を受け取れませんでした");
        await fs.writeFile(output, Buffer.from(await picture.arrayBuffer()));
        return;
      }
      await sleep(1_500);
    }
  } finally {
    await killTree(child);
  }
}
