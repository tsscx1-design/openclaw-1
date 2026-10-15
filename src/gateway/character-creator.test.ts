import { describe, expect, it } from "vitest";
import { describeImageTags } from "./character-creator.js";

describe("describeImageTags", () => {
  it("keeps the look and drops acts, framing and scenery", () => {
    const { look, minor, note } = describeImageTags({
      general: [
        ["1girl", 0.99],
        ["black_hair", 0.97],
        ["sex", 0.97],
        ["hime_cut", 0.83],
        ["red_eyes", 0.78],
        ["black_dress", 0.9],
        ["candle", 0.91],
        ["large_breasts", 0.78],
        ["pov", 0.9],
      ],
      character: [],
    });
    expect(look).toBe("black_hair, hime_cut, red_eyes, black_dress, large_breasts");
    expect(minor).toBe(false);
    expect(note).toContain("black hair");
    expect(note).toContain("character_create");
  });

  it("flags pictures of children or school settings so no character is made from them", () => {
    const { minor, note } = describeImageTags({
      general: [
        ["1girl", 0.99],
        ["school_uniform", 0.9],
        ["brown_hair", 0.9],
      ],
      character: [],
    });
    expect(minor).toBe(true);
    expect(note).toContain("このキャラは作らず");
  });

  it("names a known character as a reference, not a copy", () => {
    const { note } = describeImageTags({
      general: [["blue_hair", 0.9]],
      character: [["hatsune_miku", 0.95]],
    });
    expect(note).toContain("hatsune miku");
    expect(note).toContain("そのまま写さず");
  });
});
