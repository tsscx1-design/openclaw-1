import * as os from "node:os";
import * as path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { setBlockedTermsFileForTesting } from "./blocked-terms-config.js";
import { installBlockedTerms } from "./blocked-terms-test-support.js";
import {
  assertAllowedRequest,
  comicSheetViewOf,
  isSexualRequest,
  sanitizeH3Prompt,
  sanitizeLyrics,
  sanitizeMediaText,
  sdcppVideoArgs,
  sdxlSampling,
  wan2gpFrames,
} from "./media-studio.js";

let restoreBlockedTerms: () => void = () => undefined;
beforeAll(() => {
  restoreBlockedTerms = installBlockedTerms();
});
afterAll(() => {
  restoreBlockedTerms();
});

describe("assertAllowedRequest", () => {
  it("refuses a request that holds a word of the blocked-terms file, in Japanese or English", () => {
    expect(() => assertAllowedRequest("小学生 の ヌード")).toThrow("禁止設定");
    expect(() => assertAllowedRequest("nude", "loli, 1girl")).toThrow("禁止設定");
    expect(() => assertAllowedRequest("child in lingerie")).toThrow("禁止設定");
  });

  it("refuses a blocked word even when nothing in the request is sexual", () => {
    expect(() => assertAllowedRequest("学生が教室で勉強している")).toThrow("「学生」");
  });

  it("allows requests without a blocked word, adult or not", () => {
    expect(() => assertAllowedRequest("大人の女性 ヌード")).not.toThrow();
    expect(() => assertAllowedRequest("cat ears maid serving coffee")).not.toThrow();
  });

  it("blocks nothing while the file is missing, and has no built-in list to fall back on", () => {
    restoreBlockedTerms();
    setBlockedTermsFileForTesting(
      path.join(os.tmpdir(), "missing-blocked-terms-dir", "blocked-terms.json"),
    );
    try {
      expect(() => assertAllowedRequest("学生 loli 小学生")).not.toThrow();
    } finally {
      setBlockedTermsFileForTesting(undefined);
      restoreBlockedTerms = installBlockedTerms();
    }
  });
});

describe("comicSheetViewOf", () => {
  it("passes the view the app names, and maps the old turnaround flag to the three-view", () => {
    expect(comicSheetViewOf("three_view")).toBe("three_view");
    expect(comicSheetViewOf("360")).toBe("360");
    expect(comicSheetViewOf("front", true)).toBe("front");
    expect(comicSheetViewOf(undefined, true)).toBe("three_view");
    expect(comicSheetViewOf(undefined)).toBe("front");
    expect(comicSheetViewOf("unknown")).toBe("front");
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

describe("estimateMediaJob", () => {
  it("uses typical times until a job with the same settings has finished", async () => {
    const fs = await import("node:fs/promises");
    const os = await import("node:os");
    const path = await import("node:path");
    const { vi } = await import("vitest");
    const home = await fs.mkdtemp(path.join(os.tmpdir(), "openclaw-media-estimate-"));
    vi.stubEnv("OPENCLAW_HOME", home);
    try {
      const { estimateMediaJob } = await import("./media-studio.js");
      // Nothing ran yet: typical drawing time plus the chat-model hand-off.
      const image = await estimateMediaJob("image", { imageModel: "wai" });
      expect(image).toEqual({ jobSec: 90, waitSec: 0, totalSec: 90, samples: 0 });
      // A 5 s long-video clip from text: 150 s per clip second, a still first, smoothing.
      const video = await estimateMediaJob("video", {
        engine: "long",
        durationSec: 5,
        smooth: true,
      });
      expect(video.jobSec).toBe(150 * 5 + 30 + 60 + 60);

      const out = path.join(home, "media", "out");
      await fs.mkdir(out, { recursive: true });
      for (const [id, durationSec] of [
        ["aaaaaaaaaaa1", 40],
        ["aaaaaaaaaaa2", 50],
        ["aaaaaaaaaaa3", 300],
      ] as const) {
        await fs.writeFile(
          path.join(out, `${id}.json`),
          JSON.stringify({
            id,
            kind: "image",
            status: "done",
            request: "x",
            createdAt: Number(id.slice(-1)),
            durationSec,
            options: { imageModel: "wai" },
          }),
        );
      }
      // Real results of the same settings replace the typical value (median, start-up included).
      expect(await estimateMediaJob("image", { imageModel: "wai" })).toEqual({
        jobSec: 50,
        waitSec: 0,
        totalSec: 50,
        samples: 3,
      });
    } finally {
      vi.unstubAllEnvs();
      await fs.rm(home, { recursive: true, force: true });
    }
  });
});

describe("multiPhotoPrompt", () => {
  it("blends photos, or takes one part from each", async () => {
    const { multiPhotoPrompt } = await import("./media-studio.js");
    expect(multiPhotoPrompt("anime", "blend", [], 3)).toContain(
      "blending the people in the 3 reference pictures",
    );
    const roles = multiPhotoPrompt("photo", "roles", ["顔", "髪", "服"], 3);
    expect(roles).toContain("reference picture 1 only for the face and skin");
    expect(roles).toContain("reference picture 2 only for the hair");
    expect(roles).toContain("reference picture 3 only for the clothes");
    expect(roles).toContain("realistic photograph");
  });
});
