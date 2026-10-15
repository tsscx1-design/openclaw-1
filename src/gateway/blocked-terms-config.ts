// 禁止設定（blocked-terms.json）の読み込み口。
//
// 置き場所は dist/blocked-terms.json の1か所だけ。このファイルを作ったりコピーしたりはしない。
// ここ以外から読まず、禁止語やネガティブタグの代わりの既定値も持たない。
//
// 止めずに動かし、間違いは知らせる:
//   ・ファイルが無い        → 中身が空（何も禁止しない）として動く。
//   ・ファイルが壊れている  → 中身が空として動き、間違いを blockedTermsProblems() で返す。
//   ・正規表現などが不正    → その語句だけ無視して動き、間違いを blockedTermsProblems() で返す。
// 間違いはログには出さない（アプリの管理画面だけで知らせる）。読み込みは使うたびに更新時刻を見るので、
// アプリでの編集がすぐ効く。

import { readFileSync, statSync } from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const FILE_NAME = "blocked-terms.json";

type Toggle = { enabled?: boolean };
type TermList = Toggle & { terms?: string[]; patterns?: string[] };

export type NegativeKind = "adultOnly" | "imageNegative" | "videoNegative";
export type CharacterRule = "adultsOnly" | "forbidChildlikeTags" | "forbidPetite";

export type HeightRule = {
  shortThresholdCm?: number;
  tallThresholdCm?: number;
  veryTallThresholdCm?: number;
  shortExtra?: string;
};

export type BlockedTermsConfig = {
  blockedTermsJa?: TermList | string[];
  blockedTermsEnPatterns?: TermList | string[];
  blockedBodyModifications?: Toggle & { ja?: string[]; enPatterns?: string[] };
  /** 禁止する年齢の範囲（from 以上 to 以下、両端を含む）。範囲は JSON で決める。 */
  blockedAges?: Toggle & { from?: number; to?: number };
  /**
   * 性的な依頼かどうかを見分ける語句。禁止ではなく、成人向けの処理（成人向けのネガティブタグや画質タグ）
   * をするかどうかの判定に使う。terms は文字列、patterns は正規表現。
   */
  sexualTerms?: TermList;
  characterRules?: Partial<Record<CharacterRule, Toggle | boolean>>;
  negativePromptTerms?: Partial<Record<NegativeKind, TermList>>;
  heightRules?: { female?: HeightRule; male?: HeightRule; forbiddenTags?: string[] };
  /** 作品や脚本の指示文に足す、あなたの言葉。コードには固定の文を持たない。 */
  promptRules?: { generalSeries?: string };
  /** 声のクローンのルール。requireConsent は、同意の確認を必須にするか（無いときは必須）。 */
  voiceRules?: { requireConsent?: boolean };
};

/** 禁止設定ファイルの場所を決められない、または書き換えが読めないとき。 */
export class BlockedTermsFileError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BlockedTermsFileError";
  }
}

let fileOverride: string | undefined;

/** テスト専用: 読むファイルを差し替える（undefined で元に戻す）。本番では使わない。 */
export function setBlockedTermsFileForTesting(file: string | undefined): void {
  fileOverride = file;
  cache = undefined;
}

function isOpenClawRoot(dir: string): boolean {
  try {
    const pkg = JSON.parse(readFileSync(path.join(dir, "package.json"), "utf-8")) as {
      name?: unknown;
    };
    return pkg.name === "openclaw";
  } catch {
    return false;
  }
}

/** 禁止設定ファイルの絶対パス: <リポジトリ>/dist/blocked-terms.json。 */
export function blockedTermsFilePath(): string {
  if (fileOverride) return fileOverride;
  let dir = path.dirname(fileURLToPath(import.meta.url));
  for (;;) {
    // ビルド後はこのモジュールが dist の中にあり、ファイルはその隣。
    if (path.basename(dir) === "dist") return path.join(dir, FILE_NAME);
    // ソースから動かすときは、リポジトリの dist を指す。
    if (isOpenClawRoot(dir)) return path.join(dir, "dist", FILE_NAME);
    const parent = path.dirname(dir);
    if (parent === dir) {
      throw new BlockedTermsFileError(
        `禁止設定ファイル（dist/${FILE_NAME}）の場所を特定できません`,
      );
    }
    dir = parent;
  }
}

type Matcher = { test: (text: string) => string | undefined };

type Loaded = {
  file: string;
  mtimeMs: number;
  size: number;
  config: BlockedTermsConfig;
  matchers: Matcher[];
  /** 性的な依頼かどうかを見分ける語句。 */
  sexual: Matcher[];
  /** 禁止する年齢の範囲。使わないとき（無い・enabled:false・数値でない）は undefined。 */
  ages?: [number, number];
  /** ファイルの間違い（日本語の説明）。無ければ空。 */
  problems: string[];
};

/** ファイルが無いときに使う、何も禁止しない設定。 */
const EMPTY_CONFIG: BlockedTermsConfig = Object.freeze({});

let cache: Loaded | undefined;

function cleanList(list: unknown): string[] {
  return Array.isArray(list)
    ? list.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];
}

/** 配列形式・オブジェクト形式のどちらでも、有効な語句の一覧を返す（enabled:false なら空）。 */
function activeTerms(
  section: TermList | string[] | undefined,
  keys: Array<"terms" | "patterns">,
): string[] {
  if (Array.isArray(section)) return cleanList(section);
  if (!section || section.enabled === false) return [];
  for (const key of keys) {
    if (Array.isArray(section[key])) return cleanList(section[key]);
  }
  return [];
}

function literalMatcher(term: string): Matcher {
  const needle = term.toLowerCase();
  return { test: (text) => (text.toLowerCase().includes(needle) ? term : undefined) };
}

function patternMatcher(pattern: string, problems: string[]): Matcher | undefined {
  try {
    const re = new RegExp(pattern, "i");
    return { test: (text) => re.exec(text)?.[0] };
  } catch {
    problems.push(
      `英語の禁止パターンが正規表現として正しくありません（この語句は無視しています）: ${pattern}`,
    );
    return undefined;
  }
}

function agesOf(config: BlockedTermsConfig, problems: string[]): [number, number] | undefined {
  const section = config.blockedAges;
  if (section === undefined || section.enabled === false) return undefined;
  const from = Number(section.from);
  const to = Number(section.to);
  if (
    section.from === undefined ||
    section.to === undefined ||
    !Number.isFinite(from) ||
    !Number.isFinite(to)
  ) {
    problems.push("blockedAges の from と to が数値ではないため、年齢の判定をしていません");
    return undefined;
  }
  return [Math.min(from, to), Math.max(from, to)];
}

function analyze(
  file: string,
  mtimeMs: number,
  size: number,
  config: BlockedTermsConfig,
  problems: string[],
): Loaded {
  const body = config.blockedBodyModifications;
  const bodyOn = body !== undefined && body.enabled !== false;
  const patterns = [
    ...activeTerms(config.blockedTermsEnPatterns, ["patterns", "terms"]),
    ...(bodyOn ? cleanList(body?.enPatterns) : []),
  ];
  const matchers: Matcher[] = [
    ...activeTerms(config.blockedTermsJa, ["terms"]).map(literalMatcher),
    ...(bodyOn ? cleanList(body?.ja).map(literalMatcher) : []),
  ];
  for (const pattern of patterns) {
    const matcher = patternMatcher(pattern, problems);
    if (matcher) matchers.push(matcher);
  }
  const sexual: Matcher[] = activeTerms(config.sexualTerms, ["terms"]).map(literalMatcher);
  for (const pattern of activeTerms(config.sexualTerms, ["patterns"])) {
    const matcher = patternMatcher(pattern, problems);
    if (matcher) sexual.push(matcher);
  }
  const ages = agesOf(config, problems);
  return { file, mtimeMs, size, config, matchers, sexual, ages, problems };
}

function load(): Loaded {
  const file = blockedTermsFilePath();
  let stat;
  try {
    stat = statSync(file);
  } catch {
    return analyze(file, 0, 0, EMPTY_CONFIG, []);
  }
  if (cache && cache.file === file && cache.mtimeMs === stat.mtimeMs && cache.size === stat.size)
    return cache;
  const problems: string[] = [];
  let config: BlockedTermsConfig = EMPTY_CONFIG;
  try {
    // 先頭の BOM（U+FEFF）は JSON.parse が受け付けないので外す。
    const parsed: unknown = JSON.parse(readFileSync(file, "utf-8").replace(/^﻿/, ""));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      problems.push(`禁止設定ファイルの全体が { } の形になっていません（${file}）`);
    } else {
      config = parsed as BlockedTermsConfig;
    }
  } catch (err) {
    problems.push(
      `禁止設定ファイルを読めません（${file}）: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
  cache = analyze(file, stat.mtimeMs, stat.size, config, problems);
  return cache;
}

/**
 * dist/blocked-terms.json を読む。ファイルが無い・壊れているときは、空の設定（何も禁止しない）。
 * 壊れているときの理由は blockedTermsProblems() で分かる。
 */
export function loadBlockedTermsConfig(): BlockedTermsConfig {
  return load().config;
}

/** 禁止設定ファイルの間違い（日本語の説明）。無ければ空。アプリや生成の返事で知らせるために使う。 */
export function blockedTermsProblems(): string[] {
  return [...load().problems];
}

/** 性的な依頼かどうか（dist/blocked-terms.json の sexualTerms による）。無ければ常に false。禁止の判定ではない。 */
export function isSexualText(text: string): boolean {
  return load().sexual.some((matcher) => matcher.test(text) !== undefined);
}

/** 禁止設定に当たる語句が文章にあれば、その語句（正規表現なら当たった部分）を返す。 */
export function findBlockedTerm(...texts: string[]): string | undefined {
  const all = texts.join(" \n ");
  for (const matcher of load().matchers) {
    const hit = matcher.test(all);
    if (hit) return hit;
  }
  return undefined;
}

const KANJI_DIGITS: Record<string, number> = {
  〇: 0,
  一: 1,
  二: 2,
  三: 3,
  四: 4,
  五: 5,
  六: 6,
  七: 7,
  八: 8,
  九: 9,
};

/** "15" "１５" "十五" "二十" などを数にする。読めなければ undefined。 */
function ageNumber(raw: string): number | undefined {
  const text = raw.normalize("NFKC");
  if (/^[0-9]+$/.test(text)) return Number(text);
  if (!/^[〇一二三四五六七八九十]+$/.test(text)) return undefined;
  if (text.includes("十")) {
    const [left = "", right = ""] = text.split("十");
    const tens = left === "" ? 1 : KANJI_DIGITS[left];
    const ones = right === "" ? 0 : KANJI_DIGITS[right];
    return tens === undefined || ones === undefined ? undefined : tens * 10 + ones;
  }
  return Number([...text].map((ch) => KANJI_DIGITS[ch]).join(""));
}

const AGE_PATTERNS = [
  /([0-9０-９]{1,3}|[〇一二三四五六七八九十]{1,3})\s*[歳才]/g,
  /\b([0-9]{1,3})\s*[- ]?\s*(?:years?|yrs?)[- ]?\s*old\b/gi,
  /\b([0-9]{1,3})\s*(?:yo|y\.o\.?)(?![a-z])/gi,
  /\bage[ds]?\s*[:=]?\s*([0-9]{1,3})\b/gi,
  /年齢\s*[:：]?\s*([0-9０-９]{1,3})/g,
];

/** 禁止する年齢の範囲に入る年齢が文章にあれば、その書き方（例: "15歳"）を返す。 */
export function findBlockedAge(...texts: string[]): string | undefined {
  const range = load().ages;
  if (!range) return undefined;
  const all = texts.join(" \n ");
  for (const pattern of AGE_PATTERNS) {
    for (const match of all.matchAll(pattern)) {
      const age = ageNumber(match[1] ?? "");
      if (age !== undefined && age >= range[0] && age <= range[1]) return match[0].trim();
    }
  }
  return undefined;
}

/** 数値の年齢（キャラの設定の年齢など）が禁止する範囲なら投げる。数でなければ何もしない。 */
export function assertAgeAllowed(age: unknown): void {
  const range = load().ages;
  if (!range || age === undefined || age === null || age === "") return;
  const value = Number(age);
  if (Number.isFinite(value) && value >= range[0] && value <= range[1]) {
    throw new Error(
      `禁止設定（blocked-terms.json）の禁止する年齢（${range[0]}〜${range[1]}歳）に当たるため、生成できません（${value}歳）`,
    );
  }
}

/** 禁止設定に当たる語句・年齢があれば投げる。生成の入口で呼ぶ。 */
export function assertNoBlockedTerms(...texts: string[]): void {
  const hit = findBlockedTerm(...texts);
  if (hit) {
    throw new Error(
      `禁止設定（blocked-terms.json）の語句「${hit}」が含まれているため、生成できません`,
    );
  }
  const age = findBlockedAge(...texts);
  if (age) {
    const range = load().ages as [number, number];
    throw new Error(
      `禁止設定（blocked-terms.json）の禁止する年齢（${range[0]}〜${range[1]}歳）に当たる「${age}」が含まれているため、生成できません`,
    );
  }
}

/** ネガティブプロンプトに足すタグ（カンマ区切り）。項目が無い・enabled:false なら空。 */
export function negativeTags(kind: NegativeKind): string {
  return activeTerms(load().config.negativePromptTerms?.[kind], ["terms"]).join(", ");
}

/** キャラクターのルールが有効か。項目が無い、または enabled が false でなければ有効。 */
export function characterRuleEnabled(rule: CharacterRule): boolean {
  const raw = load().config.characterRules?.[rule];
  if (typeof raw === "boolean") return raw;
  return raw?.enabled !== false;
}

/** 指示文に足す文。無い・空なら空文字。generalSeries = 全年齢向けの連載の指示。 */
export function promptRule(name: "generalSeries"): string {
  const text = load().config.promptRules?.[name];
  return typeof text === "string" ? text.trim() : "";
}

/** 声のクローンで本人の同意の確認を必須にするか。voiceRules.requireConsent が false のときだけ不要。 */
export function voiceConsentRequired(): boolean {
  return load().config.voiceRules?.requireConsent !== false;
}

/** 身長のルール（しきい値・低身長の追加タグ・使ってはいけないタグ）。 */
export function heightRules(): NonNullable<BlockedTermsConfig["heightRules"]> {
  return load().config.heightRules ?? {};
}
