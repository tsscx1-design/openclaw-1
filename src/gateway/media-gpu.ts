/** Environment shared by local image, video, and audio workers. */
export function mediaProcessEnv(
  extra: NodeJS.ProcessEnv = {},
  cudaDevice = process.env.OPENCLAW_MEDIA_CUDA_DEVICE?.trim(),
): NodeJS.ProcessEnv {
  const env = { ...process.env, ...extra };
  if (cudaDevice) {
    env.CUDA_DEVICE_ORDER = "PCI_BUS_ID";
    env.CUDA_VISIBLE_DEVICES = cudaDevice;
  }
  return env;
}

/** Resolve the physical GPU for one generation job. UUIDs keep the choice stable across runtimes. */
export function generationCudaDevice(speedPriority: boolean): string | undefined {
  return generationCudaDevices(speedPriority)[0];
}

/** Physical GPUs available to one generation job, fastest first in dual-GPU mode. */
export function generationCudaDevices(speedPriority: boolean): string[] {
  const devices = speedPriority
    ? [process.env.OPENCLAW_MAIN_CUDA_UUID, process.env.OPENCLAW_MEDIA_CUDA_DEVICE]
    : [process.env.OPENCLAW_MEDIA_CUDA_DEVICE];
  return [...new Set(devices.map((device) => device?.trim()).filter(Boolean))] as string[];
}

/**
 * The mode of a job that did not ask for one: "fast" (both GPUs, the chat model paused while it runs).
 * Set OPENCLAW_MEDIA_DEFAULT_GPU_MODE=parallel to go back to one GPU with the chat model left alone.
 */
export function resolveGpuMode(mode?: "parallel" | "fast"): "parallel" | "fast" {
  if (mode) return mode;
  return process.env.OPENCLAW_MEDIA_DEFAULT_GPU_MODE?.trim().toLowerCase() === "parallel"
    ? "parallel"
    : "fast";
}

/** A dedicated media GPU lets the chat model remain resident during generation and training. */
export function hasDedicatedMediaGpu(): boolean {
  const enabled = process.env.OPENCLAW_MEDIA_GPU_DEDICATED?.trim().toLowerCase();
  return (
    Boolean(process.env.OPENCLAW_MEDIA_CUDA_DEVICE?.trim()) &&
    (enabled === "1" || enabled === "true" || enabled === "yes")
  );
}
