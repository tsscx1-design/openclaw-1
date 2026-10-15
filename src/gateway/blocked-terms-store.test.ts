import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { setBlockedTermsFileForTesting } from "./blocked-terms-config.js";
import {
  addBlockedTerm,
  exportBlockedTerms,
  importBlockedTerms,
  readBlockedTermsRaw,
  removeBlockedTerm,
  setBlockedTermsValue,
} from "./blocked-terms-store.js";

// 架空の内容。本物の blocked-terms.json とは無関係。
const SAMPLE = {
  "//": "メモ",
  blockedTermsJa: { enabled: true, terms: ["あ"] },
  blockedTermsEnPatterns: { enabled: true, patterns: ["x+"] },
  blockedBodyModifications: { enabled: true, ja: ["い"], enPatterns: ["y+"] },
  negativePromptTerms: { imageNegative: { enabled: true, terms: ["n1"] } },
  heightRules: { forbiddenTags: ["tag1"] },
};

let dir = "";
let file = "";
let home = "";
let previousHome: string | undefined;

beforeEach(async () => {
  dir = await fs.mkdtemp(path.join(os.tmpdir(), "blocked-store-"));
  file = path.join(dir, "blocked-terms.json");
  home = path.join(dir, "home");
  previousHome = process.env.OPENCLAW_HOME;
  process.env.OPENCLAW_HOME = home;
  await fs.writeFile(file, `﻿${JSON.stringify(SAMPLE)}`, "utf-8");
  setBlockedTermsFileForTesting(file);
});

afterEach(async () => {
  setBlockedTermsFileForTesting(undefined);
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  await fs.rm(dir, { recursive: true, force: true });
});

const onDisk = async () =>
  JSON.parse((await fs.readFile(file, "utf-8")).replace(/^﻿/, "")) as typeof SAMPLE;

describe("reading", () => {
  it("returns the whole file, memo keys included, even with a byte order mark", async () => {
    const { config, file: where } = await readBlockedTermsRaw();
    expect(where).toBe(file);
    expect(config["//"]).toBe("メモ");
  });
});

describe("reading never writes", () => {
  it("leaves the file and the folder exactly as they were, for a good file, a broken one and a missing one", async () => {
    const before = await fs.stat(file);
    await readBlockedTermsRaw();
    await exportBlockedTerms();
    expect((await fs.stat(file)).mtimeMs).toBe(before.mtimeMs);
    await fs.writeFile(file, "{ broken", "utf-8");
    const brokenStat = await fs.stat(file);
    await readBlockedTermsRaw();
    expect((await fs.stat(file)).mtimeMs).toBe(brokenStat.mtimeMs);
    await fs.rm(file);
    await readBlockedTermsRaw();
    expect(await fs.readdir(dir)).toEqual([]);
  });
});

describe("a broken file", () => {
  it("is read as empty with the problem attached, so the app can show what is wrong", async () => {
    await fs.writeFile(file, '{ "blockedTermsJa": ', "utf-8");
    const raw = await readBlockedTermsRaw();
    expect(raw.exists).toBe(true);
    expect(raw.config).toEqual({});
    expect(raw.problems[0]).toContain("禁止設定ファイルを読めません");
    await expect(addBlockedTerm("blockedTermsJa", "か")).rejects.toThrow("読めません");
  });
});

describe("editing", () => {
  it("adds a word once and keeps the rest of the file", async () => {
    await addBlockedTerm("blockedTermsJa", "か");
    await addBlockedTerm("blockedTermsJa", "か");
    const saved = await onDisk();
    expect(saved.blockedTermsJa.terms).toEqual(["あ", "か"]);
    expect(saved["//"]).toBe("メモ");
    expect((await fs.readFile(file, "utf-8")).charCodeAt(0)).not.toBe(0xfeff);
  });

  it("creates a missing section or list when a word is added, so an empty file can be filled", async () => {
    await fs.writeFile(file, "{}", "utf-8");
    await addBlockedTerm("sexualTerms", "w1", "terms");
    await addBlockedTerm("sexualTerms", "w2+", "patterns");
    await addBlockedTerm("negativePromptTerms.imageNegative", "n2", "terms");
    const saved = (await readBlockedTermsRaw()).config as Record<string, Record<string, unknown>>;
    expect(saved.sexualTerms).toEqual({ terms: ["w1"], patterns: ["w2+"] });
    expect((saved.negativePromptTerms as Record<string, unknown>).imageNegative).toEqual({
      terms: ["n2"],
    });
    await expect(addBlockedTerm("sexualTerms", "x", "__proto__")).rejects.toThrow(
      "セクション名が正しくありません",
    );
  });

  it("finds the list of a nested section, or the one named by field", async () => {
    await addBlockedTerm("negativePromptTerms.imageNegative", "n2");
    await addBlockedTerm("blockedBodyModifications", "z+", "enPatterns");
    await addBlockedTerm("heightRules", "tag2", "forbiddenTags");
    const saved = await onDisk();
    expect(saved.negativePromptTerms.imageNegative.terms).toEqual(["n1", "n2"]);
    expect(saved.blockedBodyModifications.enPatterns).toEqual(["y+", "z+"]);
    expect(saved.heightRules.forbiddenTags).toEqual(["tag1", "tag2"]);
  });

  it("puts no limit on a word (any length, any pattern); only an empty word or a bad section name is refused", async () => {
    const long = "あ".repeat(500);
    await addBlockedTerm("blockedTermsJa", long);
    await addBlockedTerm("blockedTermsEnPatterns", "(open", "patterns");
    const saved = await onDisk();
    expect(saved.blockedTermsJa.terms).toEqual(["あ", long]);
    expect(saved.blockedTermsEnPatterns.patterns).toEqual(["x+", "(open"]);
    await expect(addBlockedTerm("__proto__", "x")).rejects.toThrow(
      "セクション名が正しくありません",
    );
    await expect(addBlockedTerm("noSuchSection", "x")).rejects.toThrow("語句の一覧がありません");
    await expect(addBlockedTerm("blockedTermsJa", "   ")).rejects.toThrow("空です");
  });

  it("removes a word", async () => {
    await removeBlockedTerm("blockedTermsJa", "あ");
    expect((await onDisk()).blockedTermsJa.terms).toEqual([]);
  });

  it("changes a value that exists, with the same type only", async () => {
    await setBlockedTermsValue("blockedTermsJa", "enabled", false);
    expect((await onDisk()).blockedTermsJa.enabled).toBe(false);
    await expect(setBlockedTermsValue("blockedTermsJa", "enabled", "no")).rejects.toThrow(
      "型が一致しません",
    );
    await expect(setBlockedTermsValue("blockedTermsJa", "missing", true)).rejects.toThrow("項目");
  });

  it("creates a missing section or key on request, and takes any finite number", async () => {
    await fs.writeFile(file, "{}", "utf-8");
    await setBlockedTermsValue("blockedAges", "from", 7, { create: true });
    expect((await readBlockedTermsRaw()).config.blockedAges).toEqual({ from: 7 });
    for (const value of [-5, 0, 3.5, 150, 1e9]) {
      await setBlockedTermsValue("blockedAges", "from", value);
      expect(((await readBlockedTermsRaw()).config.blockedAges as { from: number }).from).toBe(
        value,
      );
    }
    await expect(setBlockedTermsValue("blockedAges", "from", Number.NaN)).rejects.toThrow(
      "数値が正しくありません",
    );
    await expect(setBlockedTermsValue("blockedAges", "from", "20")).rejects.toThrow(
      "型が一致しません",
    );
    await expect(setBlockedTermsValue("blockedAges", "other", 1)).rejects.toThrow("項目");
  });

  it("makes no backup and no file other than the file itself", async () => {
    await addBlockedTerm("blockedTermsJa", "か");
    await removeBlockedTerm("blockedTermsJa", "か");
    await setBlockedTermsValue("blockedTermsJa", "enabled", false);
    expect(await fs.readdir(dir)).toEqual(["blocked-terms.json"]);
    await expect(fs.stat(home)).rejects.toThrow();
  });
});

describe("the file is never created by an edit", () => {
  it("reads as empty and fails to edit when the file is missing, leaving nothing behind", async () => {
    await fs.rm(file);
    const raw = await readBlockedTermsRaw();
    expect(raw.exists).toBe(false);
    expect(raw.config).toEqual({});
    await expect(addBlockedTerm("blockedTermsJa", "か")).rejects.toThrow(
      "禁止設定ファイルがありません",
    );
    await expect(removeBlockedTerm("blockedTermsJa", "あ")).rejects.toThrow(
      "禁止設定ファイルがありません",
    );
    await expect(setBlockedTermsValue("blockedTermsJa", "enabled", true)).rejects.toThrow(
      "禁止設定ファイルがありません",
    );
    await expect(exportBlockedTerms()).rejects.toThrow("禁止設定ファイルがありません");
    expect(await fs.readdir(dir)).not.toContain("blocked-terms.json");
  });
});

describe("restore", () => {
  it("writes any JSON object as it is, and makes no backup of the old file", async () => {
    const next = { ...SAMPLE, blockedTermsJa: { enabled: true, terms: ["新"] } };
    await importBlockedTerms(JSON.stringify(next));
    expect((await onDisk()).blockedTermsJa.terms).toEqual(["新"]);
    expect(await fs.readdir(dir)).toEqual(["blocked-terms.json"]);
    await expect(fs.stat(home)).rejects.toThrow();
  });

  it("takes an empty object and a shape of its own, so an empty file can be restored", async () => {
    await importBlockedTerms("{}");
    expect(JSON.parse(await fs.readFile(file, "utf-8"))).toEqual({});
    await importBlockedTerms(
      JSON.stringify({ other: 1, blockedTermsEnPatterns: { patterns: ["(open"] } }),
    );
    expect(JSON.parse(await fs.readFile(file, "utf-8")).other).toBe(1);
  });

  it("refuses only what is not a JSON object, and leaves the file alone", async () => {
    await expect(importBlockedTerms("{ nope")).rejects.toThrow("読めません");
    await expect(importBlockedTerms("[1, 2]")).rejects.toThrow("形式が正しくありません");
    expect((await onDisk()).blockedTermsJa.terms).toEqual(["あ"]);
  });

  it("is the one action that writes the file when it is missing", async () => {
    await fs.rm(file);
    await importBlockedTerms(JSON.stringify(SAMPLE));
    expect((await onDisk()).blockedTermsJa.terms).toEqual(["あ"]);
  });

  it("exports the content as it is, without the byte order mark", async () => {
    const text = await exportBlockedTerms();
    expect(text.charCodeAt(0)).not.toBe(0xfeff);
    expect(JSON.parse(text).blockedTermsJa.terms).toEqual(["あ"]);
  });
});
