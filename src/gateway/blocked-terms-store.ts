// dist/blocked-terms.json の読み書き（アプリの禁止語句管理画面から使う）。
//
// 置き場所は blocked-terms-config.ts の blockedTermsFilePath() だけ。ファイルが無いときは、
// 読み取りは空の設定を返し、書き換えは失敗にする（作らない・コピーしない）。復元（import）だけが、
// ユーザーの操作でファイルを書く。
// 書き込むのは、管理画面でユーザーが押した操作（追加・削除・スイッチ・復元）のときだけ。読み取りは
// 何も書かず、自動で書くことはない。一時ファイルもバックアップも作らない（バックアップはユーザーが
// 自分で取る）。語句の長さや形式に、ここで
// 制限はかけない（正規表現が不正でも保存でき、読むときに無視して知らせる）。

import * as fs from "node:fs/promises";
import {
  BlockedTermsFileError,
  blockedTermsFilePath,
  blockedTermsProblems,
} from "./blocked-terms-config.js";

export type RawBlockedTerms = Record<string, unknown>;

const ARRAY_FIELDS = ["terms", "patterns", "ja"] as const;

function parseRaw(text: string, file: string): RawBlockedTerms {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text.replace(/^﻿/, ""));
  } catch (err) {
    throw new BlockedTermsFileError(
      `禁止設定ファイルを読めません（${file}）: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new BlockedTermsFileError(`禁止設定ファイルの形式が正しくありません（${file}）`);
  }
  return parsed as RawBlockedTerms;
}

async function readText(file: string): Promise<string> {
  try {
    return await fs.readFile(file, "utf-8");
  } catch {
    throw new BlockedTermsFileError(`禁止設定ファイルがありません: ${file}`);
  }
}

/**
 * いまのファイルの中身（コメント用のキーも含めてそのまま）と、その場所。無いときは空の設定。
 * 壊れているときも空の設定で返し、間違いの説明を problems に入れる（画面で知らせるため）。
 */
export async function readBlockedTermsRaw(): Promise<{
  file: string;
  config: RawBlockedTerms;
  exists: boolean;
  problems: string[];
}> {
  const file = blockedTermsFilePath();
  const text = await fs.readFile(file, "utf-8").catch(() => undefined);
  if (text === undefined) return { file, config: {}, exists: false, problems: [] };
  let config: RawBlockedTerms = {};
  try {
    config = parseRaw(text, file);
  } catch {
    // 壊れている: 空の設定で返し、理由は problems で知らせる。
  }
  return { file, config, exists: true, problems: blockedTermsProblems() };
}

/** ファイルに直接書く（一時ファイルも控えも作らない）。呼ぶのは、画面で押された操作だけ。 */
async function writeConfig(file: string, config: RawBlockedTerms): Promise<void> {
  await fs.writeFile(file, `${JSON.stringify(config, null, 2)}\n`, "utf-8");
}

/** 読み込み→変更→書き込み。ファイルが無いときは失敗（作らない）。 */
async function mutate(change: (config: RawBlockedTerms) => void): Promise<RawBlockedTerms> {
  const file = blockedTermsFilePath();
  const config = parseRaw(await readText(file), file);
  change(config);
  await writeConfig(file, config);
  return config;
}

function checkSegment(segment: string, dotted: string): void {
  if (!/^[A-Za-z0-9_]+$/.test(segment) || segment === "__proto__" || segment === "constructor") {
    throw new Error(`セクション名が正しくありません: ${dotted}`);
  }
}

/** "negativePromptTerms.imageNegative" のような道筋で、設定の中のオブジェクトを探す。 */
function objectAt(config: RawBlockedTerms, dotted: string): Record<string, unknown> {
  let node: unknown = config;
  for (const segment of dotted.split(".")) {
    checkSegment(segment, dotted);
    node = (node as Record<string, unknown> | undefined)?.[segment];
  }
  if (!node || typeof node !== "object" || Array.isArray(node)) {
    throw new Error(`セクション "${dotted}" が見つかりません`);
  }
  return node as Record<string, unknown>;
}

/** objectAt と同じ道筋で、無い所は空のオブジェクトとして作りながら進む。 */
function ensureObjectAt(config: RawBlockedTerms, dotted: string): Record<string, unknown> {
  let node: Record<string, unknown> = config;
  for (const segment of dotted.split(".")) {
    checkSegment(segment, dotted);
    const next = node[segment];
    if (next === undefined) node[segment] = {};
    else if (!next || typeof next !== "object" || Array.isArray(next)) {
      throw new Error(`"${segment}" はオブジェクトではないため、項目を作れません`);
    }
    node = node[segment] as Record<string, unknown>;
  }
  return node;
}

function arrayField(
  section: Record<string, unknown>,
  dotted: string,
  field: string | undefined,
): string {
  const key = field ?? ARRAY_FIELDS.find((name) => Array.isArray(section[name]));
  if (!key || !Array.isArray(section[key])) {
    throw new Error(`セクション "${dotted}" に語句の一覧がありません`);
  }
  return key;
}

/** 語句を足す。無いセクションや一覧は作る（`{}` のファイルにも足せる）。 */
export function addBlockedTerm(
  dotted: string,
  rawTerm: string,
  field?: string,
): Promise<RawBlockedTerms> {
  const term = rawTerm.trim();
  if (!term) return Promise.reject(new Error("語句が空です"));
  return mutate((config) => {
    const section = ensureObjectAt(config, dotted);
    if (field !== undefined) {
      checkSegment(field, field);
      if (section[field] === undefined) section[field] = [];
    }
    const key = arrayField(section, dotted, field);
    const list = section[key] as unknown[];
    if (!list.includes(term)) list.push(term);
  });
}

export function removeBlockedTerm(
  dotted: string,
  term: string,
  field?: string,
): Promise<RawBlockedTerms> {
  return mutate((config) => {
    const section = objectAt(config, dotted);
    const key = arrayField(section, dotted, field);
    section[key] = (section[key] as unknown[]).filter((item) => item !== term);
  });
}

/**
 * enabled などの値を同じ型で書き換える。create を付けると、無いセクションや項目は作る
 * （空の `{}` のファイルにも書ける）。数値には範囲の制限をかけない（有限の数であればよい）。
 */
export function setBlockedTermsValue(
  dotted: string,
  key: string,
  value: unknown,
  options: { create?: boolean } = {},
): Promise<RawBlockedTerms> {
  return mutate((config) => {
    const section = options.create ? ensureObjectAt(config, dotted) : objectAt(config, dotted);
    if (!/^[A-Za-z0-9_]+$/.test(key) || key === "__proto__" || key === "constructor") {
      throw new Error(`項目名が正しくありません: ${key}`);
    }
    if (typeof value === "number" && !Number.isFinite(value))
      throw new Error("数値が正しくありません");
    if (!(key in section)) {
      if (!options.create) throw new Error(`"${dotted}" に項目 "${key}" がありません`);
    } else if (typeof value !== typeof section[key]) {
      throw new Error(`型が一致しません (期待: ${typeof section[key]}, 受信: ${typeof value})`);
    }
    section[key] = value;
  });
}

/** ファイルの中身をそのまま返す。 */
export async function exportBlockedTerms(): Promise<string> {
  return (await readText(blockedTermsFilePath())).replace(/^﻿/, "");
}

/** 復元: JSON のオブジェクトであればそのまま書き込む（中身の形は問わない）。控えは作らない。 */
export async function importBlockedTerms(json: string): Promise<void> {
  const file = blockedTermsFilePath();
  await writeConfig(file, parseRaw(json, file));
}
