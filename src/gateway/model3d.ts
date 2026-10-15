import { spawn, execFile, type ChildProcess } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { COMFY_DIR, COMFY_PYTHON } from "./anima-comfy.js";
import { mediaProcessEnv } from "./media-gpu.js";
import {
  MODEL3D_GRAPH,
  MODEL3D_LOAD_NODE,
  MODEL3D_SAVE_NODE,
  MODEL3D_SEED_INPUTS,
  type Model3dGraph,
} from "./model3d-graph.js";

// A 3D model (GLB) from one picture: ComfyUI's own "Pixal3D + TRELLIS.2" workflow, run on the local
// ComfyUI. It is started for the job and stopped again so the GPU memory goes back to the chat model.
// Measured on an RTX 4060 8GB: about 3-4 minutes, a GLB of about 50 MB.

const PORT = 8190;
export const MODEL3D_FILES = [
  path.join(COMFY_DIR, "models", "diffusion_models", "trellis_2_int8_convrot.safetensors"),
  path.join(COMFY_DIR, "models", "diffusion_models", "pixal3d_int8_convrot.safetensors"),
  path.join(COMFY_DIR, "models", "vae", "trellis_2_shape_vae_bf16.safetensors"),
  path.join(COMFY_DIR, "models", "vae", "trellis_2_texture_vae_bf16.safetensors"),
  path.join(COMFY_DIR, "models", "clip_vision", "dino_v3_L_naf_fp32.safetensors"),
  path.join(COMFY_DIR, "models", "geometry_estimation", "moge_2_vitl_normal_fp16.safetensors"),
  path.join(COMFY_DIR, "models", "background_removal", "birefnet.safetensors"),
];

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

/** The workflow with this picture and fresh random seeds. */
export function buildModel3dGraph(imageName: string, outputPrefix: string): Model3dGraph {
  const graph = JSON.parse(JSON.stringify(MODEL3D_GRAPH)) as Model3dGraph;
  const load = graph[MODEL3D_LOAD_NODE];
  if (load) load.inputs.image = imageName;
  for (const [node, input] of MODEL3D_SEED_INPUTS) {
    const target = graph[node];
    if (target) target.inputs[input] = Math.floor(Math.random() * 2 ** 31);
  }
  const save = graph[MODEL3D_SAVE_NODE];
  if (save) save.inputs.filename_prefix = outputPrefix;
  return graph;
}

/** Makes the GLB of the picture at `input` and copies it to `output`. */
export async function renderModel3d(
  input: string,
  output: string,
  hooks: { onStage: (stage: string) => void; isCancelled: () => boolean; cudaDevice?: string },
): Promise<void> {
  for (const file of MODEL3D_FILES) {
    if (
      !(await fs.access(file).then(
        () => true,
        () => false,
      ))
    )
      throw new Error(`3D生成のファイルが見つかりません（${path.basename(file)}）`);
  }
  const tag = `openclaw-3d-${Date.now().toString(36)}`;
  const imageName = `${tag}.png`;
  await fs.copyFile(input, path.join(COMFY_DIR, "input", imageName));
  const base = `http://127.0.0.1:${PORT}`;
  hooks.onStage("3D生成（ComfyUI）を起動しています");
  const child = spawn(
    COMFY_PYTHON,
    ["main.py", "--listen", "127.0.0.1", "--port", String(PORT), "--disable-auto-launch"],
    {
      cwd: COMFY_DIR,
      windowsHide: true,
      stdio: "ignore",
      // The GPU the job was given (a single one); without it ComfyUI would see every GPU.
      env: mediaProcessEnv({ PYTHONUTF8: "1" }, hooks.cudaDevice),
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
    hooks.onStage("3Dモデルを作っています（約3〜5分）");
    const res = await fetch(`${base}/prompt`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: buildModel3dGraph(imageName, `3d/${tag}`),
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
        `3D生成に頼めませんでした: ${JSON.stringify(queued.node_errors ?? queued.error ?? res.status).slice(0, 300)}`,
      );
    }
    const waitedFrom = Date.now();
    for (;;) {
      if (hooks.isCancelled()) throw new Error("中止しました");
      if (exited) throw new Error("ComfyUI が途中で終了しました");
      if (Date.now() - waitedFrom > 40 * 60_000) throw new Error("3D生成がタイムアウトしました");
      const history = (await getJson(`${base}/history/${queued.prompt_id}`)) as Record<
        string,
        { status?: { status_str?: string } }
      >;
      const entry = history[queued.prompt_id];
      if (entry?.status?.status_str === "error") throw new Error("3D生成でエラーが出ました");
      if (entry?.status?.status_str === "success") break;
      await sleep(3_000);
    }
    const outDir = path.join(COMFY_DIR, "output", "3d");
    const found = (await fs.readdir(outDir).catch(() => [] as string[])).filter(
      (name) => name.startsWith(tag) && name.endsWith(".glb"),
    );
    const glb = found.sort().at(-1);
    if (!glb) throw new Error("できた3Dモデルが見つかりません");
    await fs.copyFile(path.join(outDir, glb), output);
    await fs.rm(path.join(outDir, glb), { force: true });
  } finally {
    await killTree(child);
    await fs.rm(path.join(COMFY_DIR, "input", imageName), { force: true }).catch(() => undefined);
  }
}
