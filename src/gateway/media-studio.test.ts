import { describe, expect, it } from "vitest";
import {
  assertAllowedRequest,
  isSexualRequest,
  sanitizeH3Prompt,
  sanitizeLyrics,
  sanitizeMediaText,
  sdcppVideoArgs,
  sdxlSampling,
  wan2gpFrames,
} from "./media-studio.js";

describe("assertAllowedRequest", () => {
  it("refuses sexual requests that involve minors, in Japanese or English", () => {
    expect(() => assertAllowedRequest("小学生 の ヌード")).toThrow();
    expect(() => assertAllowedRequest("nude", "loli, 1girl")).toThrow();
    expect(() => assertAllowedRequest("child in lingerie")).toThrow();
  });

  it("allows adult sexual requests and non-sexual requests about children", () => {
    expect(() => assertAllowedRequest("大人の女性 ヌード")).not.toThrow();
    expect(() => assertAllowedRequest("子供が公園で遊んでいる")).not.toThrow();
    expect(() => assertAllowedRequest("cat ears maid serving coffee")).not.toThrow();
  });
});

describe("isSexualRequest", () => {
  it("detects sexual wording so adult-only negatives get added", () => {
    expect(isSexualRequest("nsfw, 1girl")).toBe(true);
    expect(isSexualRequest("全裸で")).toBe(true);
    expect(isSexualRequest("sunset over the sea")).toBe(false);
  });
});

describe("sanitizeMediaText", () => {
  it("keeps prompt text and drops characters that could start shell syntax", () => {
    expect(sanitizeMediaText("1girl, smile `calc` $(x) <b>")).toBe("1girl, smile calc (x) b");
  });

  it("collapses whitespace and bounds the length", () => {
    expect(sanitizeMediaText("a\n\tb   c")).toBe("a b c");
    expect(sanitizeMediaText("x".repeat(2_000)).length).toBe(500);
  });
});

describe("sanitizeLyrics", () => {
  it("keeps line breaks and section tags", () => {
    expect(sanitizeLyrics("[Verse]\r\n風に乗って、笑顔！\n[Chorus]\n歌おう")).toBe(
      "[Verse]\n風に乗って、笑顔！\n[Chorus]\n歌おう",
    );
  });
});

describe("sdxlSampling", () => {
  it("uses each checkpoint's own sampler unless fast mode is asked for", () => {
    expect(sdxlSampling("wai", false)).toEqual({ sampler: "euler_a", steps: 28, cfg: 6 });
    expect(sdxlSampling("asianreal", false).sampler).toBe("dpmpp_2m_sde_karras");
  });

  it("switches epsilon checkpoints to DMD2 with CFG above 1 so negatives still apply", () => {
    const fast = sdxlSampling("lustify", true);
    expect(fast).toMatchObject({ sampler: "lcm", steps: 8 });
    expect(fast.cfg).toBeGreaterThan(1);
    expect(fast.lora).toMatch(/dmd2/);
  });

  it("ignores fast mode for the V-Pred checkpoint", () => {
    expect(sdxlSampling("noobai", true).lora).toBeUndefined();
  });
});

describe("sdcppVideoArgs", () => {
  const params = {
    initImage: "in.png",
    prompt: "she smiles",
    negative: "blurry",
    width: 480,
    height: 832,
    output: "out.webm",
  };

  it("runs the Remix pair as high- and low-noise models without clip vision", () => {
    const args = sdcppVideoArgs("remix", params);
    expect(args).toContain("--high-noise-diffusion-model");
    expect(args).not.toContain("--clip_vision");
    expect(args[args.indexOf("--flow-shift") + 1]).toBe("5");
  });

  it("keeps the text encoder on the GPU and the default VAE tiles", () => {
    const args = sdcppVideoArgs("standard", params);
    expect(args).toContain("--clip_vision");
    expect(args).not.toContain("--backend");
    expect(args).not.toContain("--vae-tile-size");
  });
});

describe("wan2gpFrames", () => {
  it("returns 1 + a multiple of the frame step, clamped to the engine's range", () => {
    expect(wan2gpFrames(5, 16, 4, 30)).toBe(81);
    expect(wan2gpFrames(10, 24, 8, 10)).toBe(241);
    expect(wan2gpFrames(60, 24, 8, 10)).toBe(241);
    expect(wan2gpFrames(0, 24, 4, 5)).toBe(49);
  });

  it("grows MiniMax H3 clips from its 107-frame minimum in 17-frame steps", () => {
    expect(wan2gpFrames(5, 24, 17, 10, 107)).toBe(124);
    expect(wan2gpFrames(2, 24, 17, 10, 107)).toBe(107);
    expect(wan2gpFrames(30, 24, 17, 10, 107)).toBe(243);
  });
});

describe("sanitizeH3Prompt", () => {
  it("keeps the two-line script with its dialogue tags", () => {
    const script =
      "integrated_multimodal_description: [Shot 1] She smiles and says (S1) <d>[Japanese] おかえり！</d>\r\noverall_soundscape: waves, `rm` $HOME";
    expect(sanitizeH3Prompt(script)).toBe(
      "integrated_multimodal_description: [Shot 1] She smiles and says (S1) <d>[Japanese] おかえり！</d>\noverall_soundscape: waves, rm HOME",
    );
  });

  it("adds the header when the writer forgot it", () => {
    expect(sanitizeH3Prompt("A woman waves.")).toBe(
      "integrated_multimodal_description: [Shot 1] A woman waves.",
    );
  });
});
