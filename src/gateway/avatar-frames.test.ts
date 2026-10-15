import { describe, expect, it } from "vitest";
import { AVATAR_EXPRESSIONS, avatarFramePrompt, avatarFrames } from "./avatar-frames.js";

describe("avatar frames", () => {
  it("has every expression with the mouth closed and open, and one blink", () => {
    const keys = avatarFrames().map((f) => f.key);
    expect(keys).toHaveLength(AVATAR_EXPRESSIONS.length * 2 + 1);
    expect(new Set(keys).size).toBe(keys.length);
    expect(keys).toContain("normal_closed");
    expect(keys).toContain("happy_open");
    expect(keys.at(-1)).toBe("blink");
  });

  it("asks to change only the face", () => {
    const prompt = avatarFramePrompt(avatarFrames()[3]!);
    expect(prompt).toContain("Change only the face");
    expect(prompt).toContain("the same clothes");
  });
});

describe("avatar extra frames", () => {
  it("adds the extra faces and the undress steps with unique keys", async () => {
    const { allAvatarFrames, extraFrames, undressFrames } = await import("./avatar-frames.js");
    const keys = allAvatarFrames().map((f) => f.key);
    expect(new Set(keys).size).toBe(keys.length);
    expect(extraFrames().map((f) => f.key)).toEqual([
      "blush",
      "excited",
      "ahegao",
      "climax",
      "disgust",
      "collapse",
      "squirt",
    ]);
    expect(undressFrames().map((f) => f.key)).toEqual(
      Array.from({ length: 16 }, (_, i) => `un_${i + 1}`),
    );
    // clothes and the collapse change the body, the faces only the face
    expect(avatarFramePrompt(undressFrames()[0]!)).toContain("Change only her clothes");
  });
});
