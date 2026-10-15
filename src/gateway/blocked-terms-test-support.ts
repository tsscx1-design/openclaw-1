// テスト専用: 禁止設定の読み込み先を、架空の内容の一時ファイルに差し替える。
// 本物の dist/blocked-terms.json は読まない・コピーしない。

import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { type BlockedTermsConfig, setBlockedTermsFileForTesting } from "./blocked-terms-config.js";

/** テストで使う架空の禁止設定。語句は本物のファイルとは無関係。 */
export const TEST_BLOCKED_TERMS: BlockedTermsConfig = {
  blockedTermsJa: { enabled: true, terms: ["学生", "幼女", "小学生"] },
  blockedTermsEnPatterns: {
    enabled: true,
    patterns: ["loli(?:ta)?", "child(?:ren)?", "school\\s*girl"],
  },
  blockedBodyModifications: { enabled: true, ja: ["幼くする"], enPatterns: ["make.*younger"] },
  sexualTerms: {
    enabled: true,
    terms: ["nude", "naked", "nsfw", "エロ", "全裸", "裸"],
    patterns: ["explicit"],
  },
  characterRules: {
    adultsOnly: { enabled: true },
    forbidChildlikeTags: { enabled: true },
    forbidPetite: { enabled: true },
  },
  negativePromptTerms: {
    adultOnly: { enabled: true, terms: ["test-adult-neg"] },
    imageNegative: { enabled: true, terms: ["test-image-neg"] },
    videoNegative: { enabled: true, terms: ["test-video-neg"] },
  },
  heightRules: {
    female: {
      shortThresholdCm: 152,
      tallThresholdCm: 168,
      veryTallThresholdCm: 178,
      shortExtra: "short stature, slim waist, mature female",
    },
    male: {
      shortThresholdCm: 165,
      tallThresholdCm: 180,
      veryTallThresholdCm: 190,
      shortExtra: "short stature, slim build",
    },
    forbiddenTags: ["petite"],
  },
};

/**
 * 読み込み先を一時ファイルにする（先頭に BOM を付けて書く）。戻り値を呼ぶと元に戻して一時ファイルを消す。
 * config に文字列を渡すと、その文字列をそのままファイルにする（壊れた JSON の試験用）。
 */
export function installBlockedTerms(
  config: BlockedTermsConfig | string = TEST_BLOCKED_TERMS,
): () => void {
  const dir = mkdtempSync(path.join(os.tmpdir(), "blocked-terms-test-"));
  const file = path.join(dir, "blocked-terms.json");
  writeFileSync(file, `﻿${typeof config === "string" ? config : JSON.stringify(config)}`, "utf-8");
  setBlockedTermsFileForTesting(file);
  return () => {
    setBlockedTermsFileForTesting(undefined);
    rmSync(dir, { recursive: true, force: true });
  };
}
