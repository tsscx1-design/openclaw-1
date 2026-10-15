import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  generationCudaDevice,
  generationCudaDevices,
  hasDedicatedMediaGpu,
  mediaProcessEnv,
  resolveGpuMode,
} from "./media-gpu.js";

const KEYS = [
  "OPENCLAW_MAIN_CUDA_UUID",
  "OPENCLAW_MEDIA_CUDA_DEVICE",
  "OPENCLAW_MEDIA_GPU_DEDICATED",
  "OPENCLAW_MEDIA_DEFAULT_GPU_MODE",
] as const;
const saved: Record<string, string | undefined> = {};

beforeEach(() => {
  for (const key of KEYS) {
    saved[key] = process.env[key];
    delete process.env[key];
  }
});
afterEach(() => {
  for (const key of KEYS) {
    if (saved[key] === undefined) delete process.env[key];
    else process.env[key] = saved[key];
  }
});

describe("generation GPUs", () => {
  it("uses both GPUs (main first) for the fast mode and only the media GPU otherwise", () => {
    process.env.OPENCLAW_MAIN_CUDA_UUID = "GPU-main";
    process.env.OPENCLAW_MEDIA_CUDA_DEVICE = "GPU-media";
    expect(generationCudaDevices(true)).toEqual(["GPU-main", "GPU-media"]);
    expect(generationCudaDevices(false)).toEqual(["GPU-media"]);
    expect(generationCudaDevice(true)).toBe("GPU-main");
    expect(generationCudaDevice(false)).toBe("GPU-media");
  });

  it("counts one GPU when both settings name the same one, or only one is set", () => {
    process.env.OPENCLAW_MAIN_CUDA_UUID = "GPU-same";
    process.env.OPENCLAW_MEDIA_CUDA_DEVICE = " GPU-same ";
    expect(generationCudaDevices(true)).toEqual(["GPU-same"]);
    delete process.env.OPENCLAW_MAIN_CUDA_UUID;
    expect(generationCudaDevices(true)).toEqual(["GPU-same"]);
    delete process.env.OPENCLAW_MEDIA_CUDA_DEVICE;
    expect(generationCudaDevices(true)).toEqual([]);
  });

  it("keeps the chat model resident only when a dedicated media GPU is set and switched on", () => {
    expect(hasDedicatedMediaGpu()).toBe(false);
    process.env.OPENCLAW_MEDIA_CUDA_DEVICE = "GPU-media";
    expect(hasDedicatedMediaGpu()).toBe(false);
    process.env.OPENCLAW_MEDIA_GPU_DEDICATED = "1";
    expect(hasDedicatedMediaGpu()).toBe(true);
  });

  it("limits a child process to the given GPU in PCI order", () => {
    const env = mediaProcessEnv({ X: "1" }, "GPU-media");
    expect(env).toMatchObject({
      X: "1",
      CUDA_DEVICE_ORDER: "PCI_BUS_ID",
      CUDA_VISIBLE_DEVICES: "GPU-media",
    });
    expect(mediaProcessEnv({}, undefined).CUDA_VISIBLE_DEVICES).toBeUndefined();
  });
});

describe("default GPU mode", () => {
  it("is the fast mode for a job that did not choose, and keeps an explicit choice", () => {
    expect(resolveGpuMode(undefined)).toBe("fast");
    expect(resolveGpuMode("parallel")).toBe("parallel");
    expect(resolveGpuMode("fast")).toBe("fast");
  });

  it("goes back to one GPU when OPENCLAW_MEDIA_DEFAULT_GPU_MODE=parallel", () => {
    process.env.OPENCLAW_MEDIA_DEFAULT_GPU_MODE = "parallel";
    expect(resolveGpuMode(undefined)).toBe("parallel");
    expect(resolveGpuMode("fast")).toBe("fast");
  });
});
