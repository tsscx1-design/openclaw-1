import { describe, expect, it } from "vitest";
import {
  assertAllowedRequest,
  isSexualRequest,
  sanitizeLyrics,
  sanitizeMediaText,
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

describe("wan2gpFrames", () => {
  it("returns 1 + a multiple of the frame step, clamped to the engine's range", () => {
    expect(wan2gpFrames(5, 16, 4, 30)).toBe(81);
    expect(wan2gpFrames(10, 24, 8, 10)).toBe(241);
    expect(wan2gpFrames(60, 24, 8, 10)).toBe(241);
    expect(wan2gpFrames(0, 24, 4, 5)).toBe(49);
  });
});
