import { describe, expect, it } from "vitest";
import {
  estimateSwap,
  resolveInputFile,
  safeInputName,
  swapFrameCount,
  swapPassArgs,
  type MediaProbe,
} from "./face-swap.js";

const video: MediaProbe = {
  kind: "video",
  durationSec: 600,
  fps: 30,
  width: 1920,
  height: 1080,
  frames: 18000,
};

describe("face swap helpers", () => {
  it("keeps upload names inside the input folder", () => {
    expect(safeInputName("..\\..\\evil.mp4")).toBe("evil.mp4");
    expect(safeInputName("a/b/c d.mov")).toBe("c d.mov");
    expect(resolveInputFile("in:../../x.mp4")?.endsWith("x.mp4")).toBe(true);
    expect(resolveInputFile("job:abc")).toBeUndefined();
  });

  it("counts the frames of the trimmed part", () => {
    expect(swapFrameCount(video, {})).toBe(18000);
    expect(swapFrameCount(video, { swapTrimStartSec: 10, swapTrimEndSec: 70 })).toBe(1800);
    expect(swapFrameCount({ ...video, kind: "image" }, {})).toBe(1);
  });

  it("estimates longer videos, more people and the enhancer as slower", () => {
    const one = estimateSwap(video, { swapSources: [{ characterId: "a" }] });
    const two = estimateSwap(video, { swapSources: [{ characterId: "a" }, { characterId: "b" }] });
    const enhanced = estimateSwap(video, {
      swapSources: [{ characterId: "a" }],
      swapEnhancer: true,
    });
    const hour = estimateSwap(
      { ...video, durationSec: 3600, frames: 108000 },
      { swapSources: [{ characterId: "a" }] },
    );
    expect(two.jobSec).toBeGreaterThan(one.jobSec * 1.9);
    expect(enhanced.jobSec).toBeGreaterThan(one.jobSec);
    expect(hour.jobSec).toBeGreaterThan(one.jobSec * 5);
    expect(estimateSwap(video, { swapSources: [{ characterId: "a" }] }, 0.05).jobSec).toBeLessThan(
      one.jobSec,
    );
  });

  it("builds the arguments for one person or for every face", () => {
    const base = {
      source: "s.png",
      target: "t.mp4",
      output: "o.mp4",
      tempDir: "tmp",
      quality: "standard" as const,
      enhancer: false,
    };
    const all = swapPassArgs(base);
    expect(all).toContain("many");
    expect(all).not.toContain("--reference-face-position");
    const one = swapPassArgs({
      ...base,
      position: 1,
      referenceFrame: 90,
      enhancer: true,
      trim: { startFrame: 30, endFrame: 60 },
    });
    expect(one.join(" ")).toContain("--reference-face-position 1");
    expect(one.join(" ")).toContain("--reference-frame-number 90");
    expect(one.join(" ")).toContain("face_swapper face_enhancer");
    expect(one.join(" ")).toContain("--trim-frame-start 30 --trim-frame-end 60");
  });
});
