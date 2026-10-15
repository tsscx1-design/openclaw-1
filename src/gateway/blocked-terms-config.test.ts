import { utimesSync, writeFileSync } from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  assertAgeAllowed,
  assertNoBlockedTerms,
  blockedTermsFilePath,
  blockedTermsProblems,
  findBlockedAge,
  characterRuleEnabled,
  findBlockedTerm,
  heightRules,
  isSexualText,
  negativeTags,
  promptRule,
  setBlockedTermsFileForTesting,
  voiceConsentRequired,
} from "./blocked-terms-config.js";
import { installBlockedTerms, TEST_BLOCKED_TERMS } from "./blocked-terms-test-support.js";

let restore: (() => void) | undefined;
afterEach(() => {
  restore?.();
  restore = undefined;
});

describe("where the file is read from", () => {
  it("is dist/blocked-terms.json of the repository, and nowhere else", () => {
    const file = blockedTermsFilePath();
    expect(path.basename(file)).toBe("blocked-terms.json");
    expect(path.basename(path.dirname(file))).toBe("dist");
  });

  it("works as an empty file (nothing blocked) when the file is missing", () => {
    setBlockedTermsFileForTesting(
      path.join(os.tmpdir(), "no-such-dir-blocked", "blocked-terms.json"),
    );
    expect(findBlockedTerm("学生 loli")).toBeUndefined();
    expect(() => assertNoBlockedTerms("学生 loli")).not.toThrow();
    expect(negativeTags("imageNegative")).toBe("");
    expect(characterRuleEnabled("adultsOnly")).toBe(true);
    expect(heightRules()).toEqual({});
    setBlockedTermsFileForTesting(undefined);
  });

  it("works as an empty file for {}", () => {
    restore = installBlockedTerms({});
    expect(findBlockedTerm("学生 loli")).toBeUndefined();
    expect(negativeTags("adultOnly")).toBe("");
  });

  it("runs as an empty file when the file is broken, and says what is wrong (not in the log)", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      restore = installBlockedTerms("{ not json");
      expect(findBlockedTerm("学生 loli")).toBeUndefined();
      expect(() => assertNoBlockedTerms("学生 loli")).not.toThrow();
      expect(blockedTermsProblems()).toHaveLength(1);
      expect(blockedTermsProblems()[0]).toContain("禁止設定ファイルを読めません");
      expect(warn).not.toHaveBeenCalled();
    } finally {
      warn.mockRestore();
    }
  });

  it("ignores only the bad pattern, keeps the rest, and says which one is wrong", () => {
    restore = installBlockedTerms({
      blockedTermsJa: { enabled: true, terms: ["禁止語"] },
      blockedTermsEnPatterns: { enabled: true, patterns: ["(unclosed", "good+"] },
    });
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      expect(findBlockedTerm("a goodd b")).toBe("goodd");
      expect(findBlockedTerm("禁止語")).toBe("禁止語");
      expect(blockedTermsProblems()).toEqual([
        "英語の禁止パターンが正規表現として正しくありません（この語句は無視しています）: (unclosed",
      ]);
    } finally {
      warn.mockRestore();
    }
  });

  it("has no problems for a good file, for {} and for a missing file", () => {
    restore = installBlockedTerms();
    expect(blockedTermsProblems()).toEqual([]);
    restore();
    restore = installBlockedTerms({});
    expect(blockedTermsProblems()).toEqual([]);
    setBlockedTermsFileForTesting(
      path.join(os.tmpdir(), "no-such-dir-blocked", "blocked-terms.json"),
    );
    expect(blockedTermsProblems()).toEqual([]);
    setBlockedTermsFileForTesting(undefined);
  });
});

describe("prompt and voice rules", () => {
  it("read the general-series rule as the file words it, and nothing when it is missing", () => {
    restore = installBlockedTerms({ promptRules: { generalSeries: "  性的な描写はしない。 " } });
    expect(promptRule("generalSeries")).toBe("性的な描写はしない。");
    restore();
    restore = installBlockedTerms({});
    expect(promptRule("generalSeries")).toBe("");
  });

  it("ask for consent to clone a voice unless the file says requireConsent is false", () => {
    restore = installBlockedTerms({});
    expect(voiceConsentRequired()).toBe(true);
    restore();
    restore = installBlockedTerms({ voiceRules: { requireConsent: true } });
    expect(voiceConsentRequired()).toBe(true);
    restore();
    restore = installBlockedTerms({ voiceRules: { requireConsent: false } });
    expect(voiceConsentRequired()).toBe(false);
  });
});

describe("sexual terms", () => {
  it("tell a sexual request from the list in the file, and from nothing else", () => {
    restore = installBlockedTerms();
    expect(isSexualText("NSFW, 1girl")).toBe(true);
    expect(isSexualText("全裸で")).toBe(true);
    expect(isSexualText("an explicit scene")).toBe(true);
    expect(isSexualText("sunset over the sea")).toBe(false);
  });

  it("find nothing when the list is missing, empty or turned off", () => {
    restore = installBlockedTerms({});
    expect(isSexualText("nsfw 全裸")).toBe(false);
    restore();
    restore = installBlockedTerms({ sexualTerms: { enabled: false, terms: ["nsfw"] } });
    expect(isSexualText("nsfw")).toBe(false);
  });

  it("do not make anything refused by themselves", () => {
    restore = installBlockedTerms();
    expect(() => assertNoBlockedTerms("nsfw 全裸 explicit")).not.toThrow();
  });
});

describe("blocked ages", () => {
  const ages = (from: unknown, to: unknown, enabled = true) =>
    installBlockedTerms({ blockedAges: { enabled, from, to } } as never);

  it("refuses an age inside the range written in the file, both ends included", () => {
    restore = ages(1, 20);
    expect(findBlockedAge("1歳の子")).toBe("1歳");
    expect(findBlockedAge("15歳の女の子")).toBe("15歳");
    expect(findBlockedAge("20歳の女性")).toBe("20歳");
    expect(findBlockedAge("21歳の女性")).toBeUndefined();
    expect(findBlockedAge("20代の女性")).toBeUndefined();
    expect(() => assertNoBlockedTerms("17歳です")).toThrow("1〜20歳");
  });

  it("reads the ways an age is written in Japanese and English", () => {
    restore = ages(1, 20);
    for (const text of [
      "１５歳",
      "十五歳",
      "二十歳",
      "十八才",
      "15 years old",
      "15-year-old",
      "15yo",
      "age 15",
      "age: 15",
      "aged 15",
      "年齢：15",
    ]) {
      expect(findBlockedAge(text), text).toBeDefined();
    }
    for (const text of ["30歳", "三十歳", "25 years old", "age 30", "100歳"]) {
      expect(findBlockedAge(text), text).toBeUndefined();
    }
  });

  it("checks the number of a character setting", () => {
    restore = ages(1, 20);
    expect(() => assertAgeAllowed(17)).toThrow("禁止する年齢");
    expect(() => assertAgeAllowed("17")).toThrow("禁止する年齢");
    expect(() => assertAgeAllowed(20)).toThrow("禁止する年齢");
    expect(() => assertAgeAllowed(21)).not.toThrow();
    expect(() => assertAgeAllowed(undefined)).not.toThrow();
    expect(() => assertAgeAllowed("不明")).not.toThrow();
  });

  it("is off when the section is missing, turned off, or not numbers (and says so)", () => {
    restore = installBlockedTerms({});
    expect(findBlockedAge("15歳")).toBeUndefined();
    restore();
    restore = ages(1, 20, false);
    expect(findBlockedAge("15歳")).toBeUndefined();
    expect(blockedTermsProblems()).toEqual([]);
    restore();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      restore = ages("a", 20);
      expect(findBlockedAge("15歳")).toBeUndefined();
      expect(blockedTermsProblems()[0]).toContain("blockedAges");
    } finally {
      warn.mockRestore();
    }
  });

  it("takes any range the file gives, even written the other way round", () => {
    restore = ages(30, 5);
    expect(findBlockedAge("10歳")).toBe("10歳");
    expect(findBlockedAge("31歳")).toBeUndefined();
  });
});

describe("blocked words", () => {
  it("reads a file that starts with a byte order mark", () => {
    restore = installBlockedTerms();
    expect(findBlockedTerm("幼女の絵")).toBe("幼女");
  });

  it("refuses any request holding a blocked word, with no other condition", () => {
    restore = installBlockedTerms();
    expect(findBlockedTerm("学生が教室にいる")).toBe("学生");
    expect(findBlockedTerm("a Loli, 1girl")).toBe("Loli");
    expect(findBlockedTerm("two children walking")).toBe("children");
    expect(findBlockedTerm("please make her younger")).toBe("make her younger");
    expect(findBlockedTerm("もっと幼くする")).toBe("幼くする");
    expect(() => assertNoBlockedTerms("大人の女性", "cat ears maid")).not.toThrow();
    expect(() => assertNoBlockedTerms("大人の女性", "loli")).toThrow("「loli」");
  });

  it("ignores empty entries and sections turned off", () => {
    restore = installBlockedTerms({
      blockedTermsJa: { enabled: true, terms: ["", "  ", "禁止語"] },
      blockedTermsEnPatterns: { enabled: false, patterns: ["anything"] },
      blockedBodyModifications: { enabled: false, ja: ["幼くする"] },
    });
    expect(findBlockedTerm("普通の文章")).toBeUndefined();
    expect(findBlockedTerm("anything")).toBeUndefined();
    expect(findBlockedTerm("幼くする")).toBeUndefined();
    expect(findBlockedTerm("禁止語を含む")).toBe("禁止語");
  });

  it("takes an edit of the file at once, without a restart", () => {
    restore = installBlockedTerms({ blockedTermsJa: { enabled: true, terms: ["あ"] } });
    expect(findBlockedTerm("いう")).toBeUndefined();
    const file = blockedTermsFilePath();
    writeFileSync(
      file,
      JSON.stringify({ blockedTermsJa: { enabled: true, terms: ["あ", "い"] } }),
      "utf-8",
    );
    const later = new Date(Date.now() + 5000);
    utimesSync(file, later, later);
    expect(findBlockedTerm("いう")).toBe("い");
  });
});

describe("the other rules", () => {
  it("returns the negative tags of each kind, and nothing for a section turned off", () => {
    restore = installBlockedTerms({
      negativePromptTerms: {
        adultOnly: { enabled: true, terms: ["a", "b"] },
        imageNegative: { enabled: false, terms: ["c"] },
      },
    });
    expect(negativeTags("adultOnly")).toBe("a, b");
    expect(negativeTags("imageNegative")).toBe("");
    expect(negativeTags("videoNegative")).toBe("");
  });

  it("reads character rules written as objects or booleans", () => {
    restore = installBlockedTerms({
      characterRules: {
        adultsOnly: { enabled: false },
        forbidChildlikeTags: true,
        forbidPetite: false,
      },
    });
    expect(characterRuleEnabled("adultsOnly")).toBe(false);
    expect(characterRuleEnabled("forbidChildlikeTags")).toBe(true);
    expect(characterRuleEnabled("forbidPetite")).toBe(false);
  });

  it("returns the height rules", () => {
    restore = installBlockedTerms();
    expect(heightRules().forbiddenTags).toEqual(TEST_BLOCKED_TERMS.heightRules?.forbiddenTags);
  });
});
