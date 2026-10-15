import { describe, expect, it } from "vitest";
import { resolveSampleSubject, sampleFileName, samplePrompt } from "./sample-factory.js";

describe("sample factory", () => {
  it("finds a look setting by its key and keeps the tags", async () => {
    const subject = await resolveSampleSubject("opt:sh_boots_long");
    expect(subject).toMatchObject({ label: "ロングブーツ", mode: "look" });
    expect(subject?.tags).toContain("boots");
  });

  it("uses a plain sentence for settings the editor misreads from tags", async () => {
    const subject = await resolveSampleSubject("opt:missing_legs");
    expect(subject?.tags).toContain("wheelchair");
    expect(subject?.tags).toContain("no lower legs");
  });

  it("treats a pose as a scene with a partner and motion options as looks", async () => {
    expect((await resolveSampleSubject("pose:missionary"))?.mode).toBe("scene");
    expect((await resolveSampleSubject("motion:whip"))?.mode).toBe("look");
  });

  it("knows nothing about an unknown or malformed key", async () => {
    expect(await resolveSampleSubject("opt:nope")).toBeUndefined();
    expect(await resolveSampleSubject("something")).toBeUndefined();
  });

  it("keeps the person and adds the user's instruction", () => {
    const prompt = samplePrompt(
      { key: "opt:x", label: "x", tags: "knee-high boots", mode: "look" },
      "normal",
      "red boots",
    );
    expect(prompt).toContain("Keep the same woman exactly");
    expect(prompt).toContain("knee-high boots");
    expect(prompt).toContain("Also: red boots.");
    expect(
      samplePrompt({ key: "opt:x", label: "x", tags: "t", mode: "look" }, "adult", ""),
    ).toContain("adult woman");
  });

  it("makes safe file names", () => {
    expect(sampleFileName("opt:sh_boots_long")).toBe("opt_sh_boots_long.jpg");
    expect(sampleFileName("../x")).toBe("___x.jpg");
  });
});

import { breastTags, stripBreastTags } from "./comic-characters.js";

describe("chest size tags", () => {
  it("gives every size its own weighted tags", () => {
    expect(breastTags("控えめ")).toContain("small breasts");
    expect(breastTags("普通")).toBe("medium breasts");
    expect(breastTags("大きめ")).toContain("large breasts");
    expect(breastTags("巨乳")).toContain("huge breasts");
    expect(breastTags("爆乳")).toContain("1.4");
    expect(breastTags("超乳")).toContain("gigantic breasts:1.5");
    expect(breastTags("")).toBe("");
  });

  it("removes the size words the AI wrote, with or without weights", () => {
    expect(
      stripBreastTags("1girl, medium breasts, (large breasts:1.2), flat chest, black hair"),
    ).toBe("1girl, black hair");
  });
});

import { findSettings } from "./sample-factory.js";

describe("finding settings from a word", () => {
  it("turns everyday words into setting ids", async () => {
    const ears = await findSettings("ケモミミ", 20);
    expect(ears.some((row) => row.key === "opt:cat_ears")).toBe(true);
    const boots = await findSettings("ブーツ", 20);
    expect(boots.some((row) => row.key === "opt:sh_boots_long")).toBe(true);
    expect(await findSettings("  ", 5)).toEqual([]);
  });
});

import { parseTraits, traitsForEditor } from "./photo-analysis.js";

describe("photo traits", () => {
  it("reads the JSON part of the answer and drops empty fields", () => {
    const traits = parseTraits(
      '結果です。{"hair":"黒・肩までのストレート","eyes":"茶色","skin":"","outfit":"白いパーカー","accessories":""} 以上',
    );
    expect(traits).toMatchObject({
      hair: "黒・肩までのストレート",
      eyes: "茶色",
      outfit: "白いパーカー",
    });
    expect(traits?.skin).toBeUndefined();
    expect(traitsForEditor(traits!)).toBe(
      "hair: 黒・肩までのストレート; eyes: 茶色; outfit: 白いパーカー",
    );
  });

  it("gives nothing for text without usable JSON", () => {
    expect(parseTraits("よく分かりません")).toBeUndefined();
    expect(parseTraits('{"hair":"","eyes":""}')).toBeUndefined();
  });
});
