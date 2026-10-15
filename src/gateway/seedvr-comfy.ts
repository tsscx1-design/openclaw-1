import { spawn, execFile, type ChildProcess } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { COMFY_DIR, COMFY_PYTHON } from "./anima-comfy.js";
import { mediaProcessEnv } from "./media-gpu.js";

// SeedVR2 v2.5 (ComfyUI-SeedVR2_VideoUpscaler) makes a picture sharper and larger. ComfyUI is
// started for the job on its own port, asked over its local API and stopped again.
// Measured on the RTX 4060 (8 GB): the 3B Q4_K_M model takes about 10 s for a picture to 1080p,
// the 7B Q4_K_M model is slower and a little more detailed.

const COMFY_PORT = 8190;
export const SEEDVR_MODELS = {
  "3b": "seedvr2_ema_3b-Q4_K_M.gguf",
  "7b": "seedvr2_ema_7b-Q4_K_M.gguf",
} as const;
export type SeedVrModel = keyof typeof SEEDVR_MODELS;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

function killTree(child: ChildProcess | undefined): Promise<void> {
  if (!child?.pid) return Promise.resolve();
  return new Promise((resolve) => {
    execFile("taskkill", ["/PID", String(child.pid), "/T", "/F"], { windowsHide: true }, () =>
      resolve(),
    );
  });
}

export function buildSeedVrGraph(
  inputName: string,
  model: SeedVrModel,
  resolution: number,
  seed: number,
) {
  return {
    "1": { class_type: "LoadImage", inputs: { image: inputName } },
    "2": {
      class_type: "SeedVR2LoadDiTModel",
      inputs: {
        model: SEEDVR_MODELS[model],
        device: "cuda:0",
        blocks_to_swap: 20,
        swap_io_components: true,
        offload_device: "cpu",
        cache_model: false,
        attention_mode: "sdpa",
      },
    },
    "3": {
      class_type: "SeedVR2LoadVAEModel",
      inputs: {
        model: "ema_vae_fp16.safetensors",
        device: "cuda:0",
        encode_tiled: true,
        decode_tiled: true,
        offload_device: "cpu",
        cache_model: false,
      },
    },
    "4": {
      class_type: "SeedVR2VideoUpscaler",
      inputs: {
        image: ["1", 0],
        dit: ["2", 0],
        vae: ["3", 0],
        seed,
        resolution,
        max_resolution: 0,
        batch_size: 1,
        uniform_batch_size: false,
        color_correction: "lab",
        offload_device: "cpu",
      },
    },
    "5": {
      class_type: "SaveImage",
      inputs: { images: ["4", 0], filename_prefix: "openclaw-seedvr" },
    },
  };
}

/** Starts ComfyUI, upscales one picture and stops ComfyUI. The result is written to [output]. */
export async function upscaleWithSeedVr(
  input: string,
  output: string,
  options: { model: SeedVrModel; resolution: number },
  hooks: { onStage: (stage: string) => void; isCancelled: () => boolean; cudaDevice?: string },
): Promise<void> {
  const base = `http://127.0.0.1:${COMFY_PORT}`;
  hooks.onStage("高画質化（SeedVR2）を起動しています");
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
      "--lowvram",
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
  const inputName = `openclaw-seedvr-${Date.now()}${path.extname(input) || ".png"}`;
  try {
    const startedAt = Date.now();
    for (;;) {
      if (hooks.isCancelled()) throw new Error("中止しました");
      if (exited) throw new Error("ComfyUI が起動できませんでした");
      if (Date.now() - startedAt > 4 * 60_000)
        throw new Error("ComfyUI の起動がタイムアウトしました");
      try {
        if ((await fetch(`${base}/system_stats`)).ok) break;
      } catch {
        // not up yet
      }
      await sleep(1_500);
    }
    await fs.copyFile(input, path.join(COMFY_DIR, "input", inputName));
    hooks.onStage("SeedVR2 で拡大しています");
    const seed = Math.floor(Math.random() * 1_000_000);
    const res = await fetch(`${base}/prompt`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: buildSeedVrGraph(inputName, options.model, options.resolution, seed),
        client_id: "openclaw",
      }),
    });
    const queued = (await res.json()) as {
      prompt_id?: string;
      error?: unknown;
      node_errors?: unknown;
    };
    if (!res.ok || !queued.prompt_id) {
      throw new Error(
        `高画質化を頼めませんでした: ${JSON.stringify(queued.node_errors ?? queued.error ?? res.status).slice(0, 300)}`,
      );
    }
    const waitedFrom = Date.now();
    for (;;) {
      if (hooks.isCancelled()) throw new Error("中止しました");
      if (exited) throw new Error("ComfyUI が途中で終了しました");
      if (Date.now() - waitedFrom > 30 * 60_000) throw new Error("高画質化がタイムアウトしました");
      const history = (await (await fetch(`${base}/history/${queued.prompt_id}`)).json()) as Record<
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
      if (entry?.status?.status_str === "error") throw new Error("高画質化でエラーが出ました");
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
    await fs.rm(path.join(COMFY_DIR, "input", inputName), { force: true }).catch(() => undefined);
  }
}
