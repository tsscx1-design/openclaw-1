import { createHash } from "node:crypto";
import fsSync from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";
import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import type { ComicCharacter } from "./comic-series.js";
import { AKB48_MASTER_DATA_SEC5 } from "./idol_master_akb48.js";
import { Bokuao_MASTER_DATA } from "./idol_master_bokuao.js";
import { Hinatazaka46_MASTER_DATA } from "./idol_master_hinatazaka46.js";
import { HKT48_MASTER_DATA } from "./idol_master_hkt48.js";
import { NGT48_MASTER_DATA } from "./idol_master_ngt48.js";
import { NMB48_MASTER_DATA } from "./idol_master_nmb48.js";
import { Nogizaka46_MASTER_DATA } from "./idol_master_nogizaka46.js";
import { Sakurazaka46_MASTER_DATA } from "./idol_master_sakurazaka46.js";
import { SKE48_MASTER_DATA } from "./idol_master_ske48.js";
import { STU48_MASTER_DATA } from "./idol_master_stu48.js";
import { resolveOpenClawHome } from "./partner-store.js";

// The setting master data: groups, and the members of each. The phone picks a group, then a name, and
// the member is added to the character library (idolmaster.import) with the look written in the data.

type RawMember = {
  id: number;
  name: string;
  description: string;
  group: string;
  tags: string[];
  birthday?: string;
  bloodType?: string;
  height?: number;
  agency?: string;
  photoUrl?: string;
};

export type IdolMember = {
  /** "<group>:<id>" - the id alone repeats between groups. */
  key: string;
  id: number;
  name: string;
  group: string;
  /** English tags of how the member looks (as written in the data). */
  description: string;
  tags: string[];
  birthday?: string;
  bloodType?: string;
  heightCm?: number;
  agency?: string;
  /** An https URL of a face photo (downloaded once into the photos folder when it is shown). */
  photoUrl?: string;
  /** Where she is from, her intake ("3期"), nickname and a short history (from the roster file). */
  hometown?: string;
  generation?: string;
  nickname?: string;
  bio?: string;
  /** Age: as written, else counted from the birthday today. */
  age?: number;
  /** A link to her account (shown as a link only; nothing is collected from it). */
  xUrl?: string;
  /** Where the values came from (e.g. "Wikidata Q17238494"), written in the roster line. */
  source?: string;
};

const SOURCES: ReadonlyArray<readonly RawMember[]> = [
  AKB48_MASTER_DATA_SEC5,
  SKE48_MASTER_DATA,
  NMB48_MASTER_DATA,
  HKT48_MASTER_DATA,
  NGT48_MASTER_DATA,
  STU48_MASTER_DATA,
  Nogizaka46_MASTER_DATA,
  Sakurazaka46_MASTER_DATA,
  Hinatazaka46_MASTER_DATA,
  Bokuao_MASTER_DATA,
];

let cache: IdolMember[] | undefined;

/** The members written in the data files, in the order of the groups above. */
function codeIdolMembers(): IdolMember[] {
  if (cache) return cache;
  const seen = new Set<string>();
  const out: IdolMember[] = [];
  for (const rows of SOURCES) {
    for (const row of rows) {
      const key = `${row.group}:${row.id}`;
      if (seen.has(key)) continue; // a repeated id inside a group keeps its first row
      seen.add(key);
      out.push({
        key,
        id: row.id,
        name: row.name,
        group: row.group,
        description: row.description,
        tags: [...row.tags],
        ...(row.birthday ? { birthday: row.birthday } : {}),
        ...(ageFromBirthday(row.birthday) !== undefined
          ? { age: ageFromBirthday(row.birthday)! }
          : {}),
        ...(row.bloodType ? { bloodType: row.bloodType } : {}),
        ...(typeof row.height === "number" && row.height > 0 ? { heightCm: row.height } : {}),
        ...(row.agency ? { agency: row.agency } : {}),
        ...(typeof row.photoUrl === "string" && /^https:\/\//i.test(row.photoUrl.trim())
          ? { photoUrl: row.photoUrl.trim() }
          : {}),
      });
    }
  }
  cache = out;
  return out;
}

// ---------------------------------------------------------------- roster file
// Members (and whole groups) can also be written in a plain text file, one member per line, with no code:
//   <home>/media/idol-master/members.txt
//   group | name | description (English tags) | tags separated by / | photoUrl (https, optional)
// Blank lines and lines starting with # are ignored. The file is read again whenever it changes, so
// nothing has to be built or restarted. The id of such a member is made from its group and name.

export function idolRosterFile(): string {
  return path.join(resolveOpenClawHome(), "media", "idol-master", "members.txt");
}

export type IdolRosterResult = { members: IdolMember[]; skippedLines: number[] };

/** The words a roster line may use before "=", in Japanese or English, mapped to one field name. */
const ROSTER_KEYS: Record<string, string> = {
  誕生日: "birthday",
  birthday: "birthday",
  年齢: "age",
  age: "age",
  身長: "height",
  height: "height",
  血液型: "bloodType",
  bloodtype: "bloodType",
  blood: "bloodType",
  出身地: "hometown",
  出身: "hometown",
  hometown: "hometown",
  期: "generation",
  generation: "generation",
  愛称: "nickname",
  ニックネーム: "nickname",
  nickname: "nickname",
  事務所: "agency",
  agency: "agency",
  略歴: "bio",
  経歴: "bio",
  bio: "bio",
  写真: "photo",
  photo: "photo",
  x: "x",
  twitter: "x",
  ツイッター: "x",
  出典: "source",
  source: "source",
};

function rosterKeyOf(part: string): { field: string; value: string } | undefined {
  const at = part.indexOf("=");
  if (at <= 0 || at > 16) return undefined;
  const field = ROSTER_KEYS[part.slice(0, at).trim().toLowerCase()];
  return field ? { field, value: part.slice(at + 1).trim() } : undefined;
}

/** "2000-04-01", "2000/4/1" or "2000年4月1日" as "2000-04-01"; undefined for anything else. */
function normalizeBirthday(value: string): string | undefined {
  const m = /^(\d{4})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})\s*日?$/.exec(value.trim());
  if (!m) return undefined;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(y, mo - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== mo - 1 || date.getUTCDate() !== d)
    return undefined;
  return `${m[1]}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/** Whole years from an ISO birthday to `now` (undefined when the birthday is not usable). */
export function ageFromBirthday(
  birthday: string | undefined,
  now: Date = new Date(),
): number | undefined {
  const iso = birthday ? normalizeBirthday(birthday) : undefined;
  if (!iso) return undefined;
  const [y, mo, d] = iso.split("-").map(Number) as [number, number, number];
  let age = now.getUTCFullYear() - y;
  if (now.getUTCMonth() + 1 < mo || (now.getUTCMonth() + 1 === mo && now.getUTCDate() < d))
    age -= 1;
  return age >= 0 && age < 130 ? age : undefined;
}

/**
 * Reads roster text. A line is: group | name | description | tags | then any number of "key=value" parts
 * (誕生日 年齢 身長 血液型 出身地 期 愛称 事務所 略歴 写真 X) or one bare https:// photo address.
 * A line that has no group or no name is skipped (its line number is returned).
 */
export function parseIdolRoster(text: string): IdolRosterResult {
  const members: IdolMember[] = [];
  const skippedLines: number[] = [];
  const used = new Set<string>();
  const lines = text.replace(/^﻿/, "").split(/\r?\n/);
  for (const [index, raw] of lines.entries()) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const parts = line.split("|").map((part) => part.trim());
    const group = parts[0] ?? "";
    const name = parts[1] ?? "";
    if (!group || !name || group.length > 40 || name.length > 40) {
      skippedLines.push(index + 1);
      continue;
    }
    let description = "";
    let tagText = "";
    const fields: Record<string, string> = {};
    for (const [position, part] of parts.slice(2).entries()) {
      const keyed = rosterKeyOf(part);
      if (keyed) fields[keyed.field] = keyed.value;
      else if (/^https:\/\//i.test(part)) fields.photo ??= part;
      else if (position === 0) description = part;
      else if (position === 1) tagText = part;
    }
    let id =
      100_000 + (createHash("sha1").update(`${group}\n${name}`).digest().readUInt32BE(0) % 900_000);
    while (used.has(`${group}:${id}`)) id += 1;
    used.add(`${group}:${id}`);
    const birthday = fields.birthday ? normalizeBirthday(fields.birthday) : undefined;
    const given = fields.age ? Number(fields.age.replace(/[^\d]/g, "")) : NaN;
    const age =
      Number.isFinite(given) && given > 0 && given < 130 ? given : ageFromBirthday(birthday);
    // "156.5cm" and "160" both work; one decimal place is kept.
    const heightRaw = fields.height ? Number.parseFloat(fields.height.replace(/[^\d.]/g, "")) : NaN;
    const height = Number.isFinite(heightRaw) ? Math.round(heightRaw * 10) / 10 : NaN;
    const blood = fields.bloodType?.toUpperCase().replace(/型/g, "").trim();
    const short = (value: string | undefined, max: number) =>
      value ? value.slice(0, max) : undefined;
    const optional = {
      birthday,
      bloodType: blood && /^(A|B|O|AB)$/.test(blood) ? blood : undefined,
      heightCm: height >= 100 && height <= 250 ? height : undefined,
      agency: short(fields.agency, 60),
      hometown: short(fields.hometown, 60),
      generation: short(fields.generation, 40),
      nickname: short(fields.nickname, 60),
      bio: short(fields.bio, 300),
      age,
      photoUrl: fields.photo && /^https:\/\//i.test(fields.photo) ? fields.photo : undefined,
      xUrl: fields.x && /^https:\/\//i.test(fields.x) ? fields.x : undefined,
      source: short(fields.source, 200),
    };
    members.push({
      key: `${group}:${id}`,
      id,
      name,
      group,
      description: (description || "idol").slice(0, 400),
      tags: tagText
        .split(/[/／,、]/)
        .map((tag) => tag.trim())
        .filter(Boolean)
        .slice(0, 8),
      ...Object.fromEntries(Object.entries(optional).filter(([, value]) => value !== undefined)),
    });
  }
  return { members, skippedLines };
}

let rosterCache: { file: string; mtime: number; result: IdolRosterResult } | undefined;

/** The roster file's members (empty when there is no file). Read again when the file's time changes. */
export function idolRoster(): IdolRosterResult {
  const file = idolRosterFile();
  try {
    const mtime = fsSync.statSync(file).mtimeMs;
    if (rosterCache && rosterCache.file === file && rosterCache.mtime === mtime)
      return rosterCache.result;
    const result = parseIdolRoster(fsSync.readFileSync(file, "utf-8"));
    rosterCache = { file, mtime, result };
    return result;
  } catch {
    return { members: [], skippedLines: [] };
  }
}

const BACKUPS_KEPT = 10;

const cleanField = (value: string): string =>
  value
    .replace(/[\r\n]+/g, " ")
    .replace(/\|/g, "｜")
    .trim();

/** A member as one roster line (every field that is set; an age is written only when there is no birthday). */
export function serializeRosterMember(m: IdolMember): string {
  const parts = [
    cleanField(m.group),
    cleanField(m.name),
    m.description === "idol" ? "" : cleanField(m.description),
    m.tags.map(cleanField).filter(Boolean).join("/"),
  ];
  const keyed = [
    m.birthday ? `誕生日=${m.birthday}` : "",
    !m.birthday && m.age !== undefined ? `年齢=${m.age}` : "",
    m.bloodType ? `血液型=${m.bloodType}` : "",
    m.heightCm ? `身長=${m.heightCm}` : "",
    m.hometown ? `出身地=${cleanField(m.hometown)}` : "",
    m.generation ? `期=${cleanField(m.generation)}` : "",
    m.nickname ? `愛称=${cleanField(m.nickname)}` : "",
    m.agency ? `事務所=${cleanField(m.agency)}` : "",
    m.bio ? `略歴=${cleanField(m.bio)}` : "",
    m.photoUrl ? `写真=${m.photoUrl}` : "",
    m.xUrl ? `X=${m.xUrl}` : "",
    m.source ? `出典=${cleanField(m.source)}` : "",
  ].filter(Boolean);
  if (keyed.length === 0) while (parts.length > 2 && parts[parts.length - 1] === "") parts.pop();
  return [...parts, ...keyed].join(" | ");
}

/**
 * Writes many roster lines at once: a line replaces the line of the same group and name, otherwise it is added at the
 * end. One backup is taken first (the newest 10 are kept) and the file is written through a temporary file.
 */
export async function upsertRosterLines(
  lines: string[],
): Promise<{ added: number; updated: number; backup?: string }> {
  const incoming = new Map<string, string>();
  for (const line of lines) {
    const parsed = parseIdolRoster(line);
    if (parsed.members.length !== 1 || parsed.skippedLines.length > 0)
      throw new Error("名簿の1行の形が正しくありません");
    const member = parsed.members[0]!;
    incoming.set(`${member.group}\n${member.name}`, line);
  }
  const file = idolRosterFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  let text = "";
  try {
    text = await fs.readFile(file, "utf-8");
  } catch {
    text = "";
  }
  const rows = text.length ? text.replace(/\r?\n$/, "").split(/\r?\n/) : [];
  let updated = 0;
  const seen = new Set<string>();
  for (const [index, row] of rows.entries()) {
    const trimmed = row.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const parts = trimmed.split("|").map((part) => part.trim());
    const key = `${parts[0] ?? ""}\n${parts[1] ?? ""}`;
    const replacement = incoming.get(key);
    if (replacement !== undefined && !seen.has(key)) {
      rows[index] = replacement;
      seen.add(key);
      updated += 1;
    }
  }
  let added = 0;
  for (const [key, line] of incoming) {
    if (seen.has(key)) continue;
    rows.push(line);
    added += 1;
  }
  let backup: string | undefined;
  if (text.length) {
    const stamp = new Date().toISOString().replace(/[-:T]/g, "").slice(0, 14);
    backup = `${file}.bak-${stamp}`;
    await fs.writeFile(backup, text, "utf-8");
    const dir = path.dirname(file);
    const old = (await fs.readdir(dir))
      .filter((n) => n.startsWith(`${path.basename(file)}.bak-`))
      .sort();
    for (const stale of old.slice(0, Math.max(0, old.length - BACKUPS_KEPT)))
      await fs.rm(path.join(dir, stale), { force: true });
  }
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, `${rows.join("\n")}\n`, "utf-8");
  await fs.rename(temp, file);
  rosterCache = undefined;
  await unhide(
    [...incoming.keys()].map((key) => ({ group: key.split("\n")[0]!, name: key.split("\n")[1] })),
  );
  return { added, updated, ...(backup ? { backup } : {}) };
}

/**
 * Writes one roster line: replaces the line of the same group and name, or adds it at the end. The file is backed up
 * first (the newest 10 backups are kept) and written through a temporary file. Returns what happened.
 */
export async function upsertRosterLine(
  line: string,
): Promise<{ action: "added" | "updated"; group: string; name: string; backup?: string }> {
  const parsed = parseIdolRoster(line);
  if (parsed.members.length !== 1 || parsed.skippedLines.length > 0)
    throw new Error("名簿の1行の形が正しくありません");
  const { group, name } = parsed.members[0]!;
  const file = idolRosterFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  let text = "";
  try {
    text = await fs.readFile(file, "utf-8");
  } catch {
    text = "";
  }
  const keyOf = (row: string): string => {
    const parts = row.split("|").map((part) => part.trim());
    return `${parts[0] ?? ""}\n${parts[1] ?? ""}`;
  };
  const target = `${group}\n${name}`;
  const lines = text.length ? text.replace(/\r?\n$/, "").split(/\r?\n/) : [];
  const at = lines.findIndex(
    (row) => row.trim() && !row.trim().startsWith("#") && keyOf(row) === target,
  );
  if (at >= 0) lines[at] = line;
  else lines.push(line);
  let backup: string | undefined;
  if (text.length) {
    const stamp = new Date().toISOString().replace(/[-:T]/g, "").slice(0, 14);
    backup = `${file}.bak-${stamp}`;
    await fs.writeFile(backup, text, "utf-8");
    const old = (await fs.readdir(path.dirname(file)))
      .filter((n) => n.startsWith(`${path.basename(file)}.bak-`))
      .sort();
    for (const name of old.slice(0, Math.max(0, old.length - BACKUPS_KEPT)))
      await fs.rm(path.join(path.dirname(file), name), { force: true });
  }
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, `${lines.join("\n")}\n`, "utf-8");
  await fs.rename(temp, file);
  rosterCache = undefined;
  await unhide([{ group, name }]);
  return { action: at >= 0 ? "updated" : "added", group, name, ...(backup ? { backup } : {}) };
}

// ---------------------------------------------------------------- removed (hidden) groups and members
// The built-in data cannot be edited, so "delete" hides: removed.json next to the roster lists the hidden groups and
// members (a member is found again by group and name). Adding the same group or member again shows it again.

type RemovedList = { groups: string[]; members: Array<{ group: string; name: string }> };

export function idolRemovedFile(): string {
  return path.join(path.dirname(idolRosterFile()), "removed.json");
}

let removedCache: { file: string; mtime: number; list: RemovedList } | undefined;

function readRemoved(): RemovedList {
  const file = idolRemovedFile();
  try {
    const mtime = fsSync.statSync(file).mtimeMs;
    if (removedCache && removedCache.file === file && removedCache.mtime === mtime)
      return removedCache.list;
    const parsed = JSON.parse(fsSync.readFileSync(file, "utf-8")) as Partial<RemovedList>;
    const list: RemovedList = {
      groups: Array.isArray(parsed.groups)
        ? parsed.groups.filter((g): g is string => typeof g === "string")
        : [],
      members: Array.isArray(parsed.members)
        ? parsed.members.filter(
            (m): m is { group: string; name: string } =>
              typeof m?.group === "string" && typeof m?.name === "string",
          )
        : [],
    };
    removedCache = { file, mtime, list };
    return list;
  } catch {
    return { groups: [], members: [] };
  }
}

async function writeRemoved(list: RemovedList): Promise<void> {
  const file = idolRemovedFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, JSON.stringify(list, null, 2), "utf-8");
  await fs.rename(temp, file);
  removedCache = undefined;
}

/** Shows again what was hidden when the same group or member is added again. */
async function unhide(entries: Array<{ group: string; name?: string }>): Promise<void> {
  const list = readRemoved();
  const groups = new Set(entries.map((entry) => entry.group));
  const names = new Set(
    entries.flatMap((entry) =>
      entry.name
        ? [
            `${entry.group}\
${entry.name}`,
          ]
        : [],
    ),
  );
  const next: RemovedList = {
    groups: list.groups.filter((group) => !groups.has(group)),
    members: list.members.filter(
      (member) =>
        !names.has(`${member.group}\
${member.name}`),
    ),
  };
  if (next.groups.length !== list.groups.length || next.members.length !== list.members.length)
    await writeRemoved(next);
}

/** Hides one member (by its id in the group). */
export async function removeIdolMember(group: string, id: number): Promise<void> {
  const member = idolMembers(group).find((one) => one.id === id);
  if (!member) throw new Error("その人が見つかりません");
  const list = readRemoved();
  if (!list.members.some((one) => one.group === member.group && one.name === member.name))
    list.members.push({ group: member.group, name: member.name });
  await writeRemoved(list);
}

/** Hides a group with all its members and takes it out of its category. */
export async function removeIdolGroup(group: string): Promise<void> {
  const list = readRemoved();
  if (!list.groups.includes(group)) list.groups.push(group);
  await writeRemoved(list);
  const { spec } = readCategorySpec();
  for (const entry of spec) entry.groups = entry.groups.filter((g) => g !== group);
  // the groups that sat inside it become groups of their own
  const parents = readGroupParents();
  delete parents[group];
  for (const [child, up] of Object.entries(parents)) if (up === group) delete parents[child];
  await writeCategorySpec(spec, parents);
}

/** Every member of every group: the data files first, then the roster file (groups keep their first-seen order). */
export function allIdolMembers(): IdolMember[] {
  const base = codeIdolMembers();
  const extra = idolRoster().members;
  const all = (() => {
    if (extra.length === 0) return base;
    const seen = new Set(base.map((member) => member.key));
    return [...base, ...extra.filter((member) => !seen.has(member.key))];
  })();
  const removed = readRemoved();
  if (removed.groups.length === 0 && removed.members.length === 0) return all;
  const hiddenGroups = new Set(removed.groups);
  const hiddenMembers = new Set(removed.members.map((member) => `${member.group}\n${member.name}`));
  return all.filter(
    (member) =>
      !hiddenGroups.has(member.group) && !hiddenMembers.has(`${member.group}\n${member.name}`),
  );
}

/** The groups, in the order of the sources, with how many members each has. */
export function idolGroups(): Array<{ group: string; count: number }> {
  const counts = new Map<string, number>();
  for (const member of allIdolMembers())
    counts.set(member.group, (counts.get(member.group) ?? 0) + 1);
  return [...counts.entries()].map(([group, count]) => ({ group, count }));
}

// ---------------------------------------------------------------- group categories
// Groups are gathered under categories ("48グループ", "坂道シリーズ", ...) that the user can make and fill. They are
// kept in categories.json next to the roster: { "categories": [{ "name": "...", "groups": ["AKB48", ...] }] }.
// Until that file exists the known groups use the defaults below. A group that is in no category is shown under "その他".

type CategorySpec = {
  name: string;
  groups: string[];
  parent?: string;
  /** A built-in category the user removed: it stays in the file (so it is not made again) but is not shown. */ hidden?: boolean;
};

const DEFAULT_CATEGORIES: CategorySpec[] = [
  { name: "48グループ", groups: ["AKB48", "SKE48", "NMB48", "HKT48", "NGT48", "STU48"] },
  { name: "坂道シリーズ", groups: ["Nogizaka46", "Sakurazaka46", "Hinatazaka46"] },
];
// Categories that are always on the fan book, even while empty (the user fills them: a group is a work / agency, a member is a character / person).
const ALWAYS_CATEGORIES = [
  "歌手",
  "アイドル",
  "アニメ",
  "アダルトアニメ",
  "俳優",
  "グラビアアイドル",
  "AV女優",
  "モデル",
  "地下アイドル",
  "Vチューバー",
  "YouTuber",
];
export const UNCATEGORIZED = "その他";
const MAX_CATEGORY_NAME = 30;

export function idolCategoriesFile(): string {
  return path.join(path.dirname(idolRosterFile()), "categories.json");
}

/** The categories that go under "アイドル" (a top item of its own, next to "歌手"). */
const IDOL_CHILDREN = ["48グループ", "坂道シリーズ"];

function withAlwaysCategories(spec: CategorySpec[]): CategorySpec[] {
  const result = spec.map((entry) => ({ ...entry }));
  if (!result.some((entry) => entry.name === "歌手")) result.unshift({ name: "歌手", groups: [] });
  if (!result.some((entry) => entry.name === "アイドル")) {
    result.splice(result.findIndex((entry) => entry.name === "歌手") + 1, 0, {
      name: "アイドル",
      groups: [],
    });
    // The first time "アイドル" is made, 48グループ and 坂道シリーズ move under it (from "歌手" or from the top).
    for (const entry of result)
      if (
        IDOL_CHILDREN.includes(entry.name) &&
        (entry.parent === undefined || entry.parent === "歌手")
      )
        entry.parent = "アイドル";
  }
  const missing = ALWAYS_CATEGORIES.filter((name) => !result.some((entry) => entry.name === name));
  return [...result, ...missing.map((name) => ({ name, groups: [] as string[] }))];
}

function readCategorySpec(): { spec: CategorySpec[]; fromFile: boolean } {
  try {
    const parsed = JSON.parse(fsSync.readFileSync(idolCategoriesFile(), "utf-8")) as {
      categories?: unknown;
    };
    if (Array.isArray(parsed.categories)) {
      const spec = parsed.categories.flatMap((entry) => {
        const item = entry as {
          name?: unknown;
          groups?: unknown;
          parent?: unknown;
          hidden?: unknown;
        };
        if (typeof item.name !== "string" || !item.name.trim() || !Array.isArray(item.groups))
          return [];
        const parent =
          typeof item.parent === "string" && item.parent.trim()
            ? item.parent.trim().slice(0, MAX_CATEGORY_NAME)
            : undefined;
        return [
          {
            name: item.name.trim().slice(0, MAX_CATEGORY_NAME),
            groups: item.groups.filter((g): g is string => typeof g === "string"),
            ...(parent ? { parent } : {}),
            ...(item.hidden === true ? { hidden: true } : {}),
          },
        ];
      });
      return { spec: withAlwaysCategories(spec), fromFile: true };
    }
  } catch {
    // No file yet (or a broken one): the defaults apply.
  }
  // The defaults only name groups that really are in the roster (so a first save does not invent empty groups).
  const present = new Set(idolGroups().map((row) => row.group));
  return {
    spec: withAlwaysCategories(
      DEFAULT_CATEGORIES.map((category) => ({
        ...category,
        groups: category.groups.filter((group) => present.has(group)),
      })),
    ),
    fromFile: false,
  };
}

/** The categories in order, each with its groups (and their member counts); a group in no category goes to "その他". */
export function idolCategories(): Array<{
  name: string;
  parent?: string;
  groups: Array<{ group: string; count: number; parent?: string }>;
}> {
  const counts = new Map(idolGroups().map((row) => [row.group, row.count] as const));
  const { spec, fromFile } = readCategorySpec();
  const groupParents = readGroupParents();
  const placed = new Set<string>();
  const result: Array<{
    name: string;
    parent?: string;
    groups: Array<{ group: string; count: number; parent?: string }>;
  }> = [];
  const names = new Set(spec.map((entry) => entry.name));
  for (const category of spec) {
    // A built-in category the user removed is not shown.
    if (category.hidden) continue;
    // Once the user has saved categories, a group they made stays even with no member yet (count 0).
    const groups = category.groups.filter(
      (group) => (counts.has(group) || fromFile) && !placed.has(group),
    );
    for (const group of groups) placed.add(group);
    // Defaults with no group present stay hidden; a category the user made stays even when it is empty.
    if (groups.length > 0 || fromFile || ALWAYS_CATEGORIES.includes(category.name)) {
      const parent =
        category.parent && category.parent !== category.name && names.has(category.parent)
          ? category.parent
          : undefined;
      // A group inside another group counts only when the parent group is in the same category (and is not inside one itself).
      const inside = (group: string): string | undefined => {
        const up = groupParents[group];
        return up && groups.includes(up) && !groupParents[up] ? up : undefined;
      };
      result.push({
        name: category.name,
        ...(parent ? { parent } : {}),
        groups: groups.map((group) => ({
          group,
          count: counts.get(group) ?? 0,
          ...(inside(group) ? { parent: inside(group) } : {}),
        })),
      });
    }
  }
  const rest = [...counts.keys()].filter((group) => !placed.has(group));
  if (rest.length > 0) {
    const existing = result.find((category) => category.name === UNCATEGORIZED);
    const rows = rest.map((group) => ({ group, count: counts.get(group) ?? 0 }));
    if (existing) existing.groups.push(...rows);
    else result.push({ name: UNCATEGORIZED, groups: rows });
  }
  return result;
}

/** Moves a group into a category (made when it is new). The group must be in the roster. */
export async function setGroupCategory(group: string, category: string): Promise<void> {
  const name = cleanCategoryName(category);
  const { spec } = readCategorySpec();
  if (
    !idolGroups().some((row) => row.group === group) &&
    !spec.some((entry) => entry.groups.includes(group))
  )
    throw new Error("名簿に、そのグループがありません");
  // the groups inside this group move with it (a group that sits inside another one moves out of it)
  const parents = readGroupParents();
  const inner = Object.entries(parents)
    .filter(([, up]) => up === group)
    .map(([child]) => child);
  delete parents[group];
  const moving = [group, ...inner];
  for (const entry of spec) entry.groups = entry.groups.filter((g) => !moving.includes(g));
  const target = spec.find((entry) => entry.name === name);
  if (target) target.groups.push(...moving);
  else spec.push({ name, groups: moving });
  await writeCategorySpec(spec, parents);
}

/** The groups that sit inside another group ("チームA" inside "AKB48"): { child: parent }, one level only. */
function readGroupParents(): Record<string, string> {
  try {
    const parsed = JSON.parse(fsSync.readFileSync(idolCategoriesFile(), "utf-8")) as {
      groupParents?: unknown;
    };
    const raw = parsed.groupParents;
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
    const result: Record<string, string> = {};
    for (const [child, parent] of Object.entries(raw as Record<string, unknown>)) {
      if (typeof parent === "string" && parent && parent !== child) result[child] = parent;
    }
    return result;
  } catch {
    return {};
  }
}

function cleanCategoryName(category: string): string {
  const name = category
    .replace(/[\r\n|]+/g, " ")
    .trim()
    .slice(0, MAX_CATEGORY_NAME);
  if (!name) throw new Error("分類の名前を入れてください");
  return name;
}

async function writeCategorySpec(
  spec: CategorySpec[],
  groupParents: Record<string, string> = readGroupParents(),
): Promise<void> {
  const file = idolCategoriesFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  await fs.writeFile(
    tmp,
    JSON.stringify(
      { categories: spec, ...(Object.keys(groupParents).length > 0 ? { groupParents } : {}) },
      null,
      2,
    ),
    "utf-8",
  );
  await fs.rename(tmp, file);
}

/** Makes a new, empty category (a name that is already there is left as it is). */
export async function addCategory(category: string): Promise<void> {
  const name = cleanCategoryName(category);
  const { spec } = readCategorySpec();
  const existing = spec.find((entry) => entry.name === name);
  if (!existing) spec.push({ name, groups: [] });
  else if (existing.hidden) delete existing.hidden; // a removed built-in category comes back
  await writeCategorySpec(spec);
}

const MAX_GROUP_NAME = 40;

/**
 * Makes a new group (no member yet) inside a category. A group name that already exists is refused. With a parent group,
 * the new group sits inside it (one level only) and goes to the parent's category.
 */
export async function addGroup(
  category: string,
  group: string,
  parentGroup?: string,
): Promise<void> {
  const name = cleanCategoryName(category);
  const label = group
    .replace(/[\r\n|\\/]+/g, " ")
    .trim()
    .slice(0, MAX_GROUP_NAME);
  if (!label) throw new Error("グループの名前を入れてください");
  if (!groupSlug(label)) throw new Error("グループの名前に、文字か数字を入れてください");
  const { spec } = readCategorySpec();
  const exists =
    idolGroups().some((row) => row.group === label) ||
    spec.some((entry) => entry.groups.includes(label));
  if (exists) throw new Error(`「${label}」は、すでにあります`);
  const parentLabel = (parentGroup ?? "").trim();
  const parents = readGroupParents();
  let target = spec.find((entry) => entry.name === name);
  if (parentLabel) {
    const home = spec.find((entry) => entry.groups.includes(parentLabel));
    if (!home) throw new Error(`親のグループ「${parentLabel}」が見つかりません`);
    if (parents[parentLabel])
      throw new Error(
        "グループの中のグループは、1段までです（このグループは、別のグループの中にあります）",
      );
    target = home;
    parents[label] = parentLabel;
  }
  if (target) target.groups.push(label);
  else spec.push({ name, groups: [label] });
  await writeCategorySpec(spec, parents);
  await unhide([{ group: label }]);
}

/** The spec with every group of the roster placed (the ones in no category go to "その他"), for editing the order. */
function materializedSpec(): CategorySpec[] {
  const { spec } = readCategorySpec();
  const placed = new Set(spec.flatMap((entry) => entry.groups));
  const rest = idolGroups()
    .map((row) => row.group)
    .filter((group) => !placed.has(group));
  if (rest.length > 0) {
    const other = spec.find((entry) => entry.name === UNCATEGORIZED);
    if (other) other.groups.push(...rest);
    else spec.push({ name: UNCATEGORIZED, groups: rest });
  }
  return spec;
}

/** Moves a group one place earlier (-1) or later (+1) inside its category. */
export async function moveIdolGroup(group: string, direction: number): Promise<void> {
  const spec = materializedSpec();
  const entry = spec.find((one) => one.groups.includes(group));
  if (!entry) throw new Error("そのグループが見つかりません");
  const at = entry.groups.indexOf(group);
  const to = at + (direction < 0 ? -1 : 1);
  if (to < 0 || to >= entry.groups.length) return;
  [entry.groups[at], entry.groups[to]] = [entry.groups[to]!, entry.groups[at]!];
  await writeCategorySpec(spec);
}

const parentOf = (spec: CategorySpec[], entry: CategorySpec): string | undefined =>
  entry.parent && entry.parent !== entry.name && spec.some((one) => one.name === entry.parent)
    ? entry.parent
    : undefined;

/** Moves a category one place earlier or later among the categories with the same parent. */
export async function moveIdolCategory(name: string, direction: number): Promise<void> {
  const spec = materializedSpec();
  const entry = spec.find((one) => one.name === name);
  if (!entry) throw new Error("その分類が見つかりません");
  const siblings = spec.filter(
    (one) => !one.hidden && parentOf(spec, one) === parentOf(spec, entry),
  );
  const at = siblings.indexOf(entry);
  const other = siblings[at + (direction < 0 ? -1 : 1)];
  if (!other) return;
  const i = spec.indexOf(entry);
  const j = spec.indexOf(other);
  spec[i] = other;
  spec[j] = entry;
  await writeCategorySpec(spec);
}

/** Puts a category under a big item ("歌手" ...) or, with an empty parent, back at the top. Two levels only. */
export async function setIdolCategoryParent(name: string, parent: string): Promise<void> {
  const spec = materializedSpec();
  const entry = spec.find((one) => one.name === name);
  if (!entry) throw new Error("その分類が見つかりません");
  const target = parent.trim();
  if (!target) {
    delete entry.parent;
  } else {
    const upper = spec.find((one) => one.name === target);
    if (!upper || upper === entry) throw new Error("入れ先の大項目が見つかりません");
    if (parentOf(spec, upper)) throw new Error("入れ先は、別の大項目の中にあります（2段までです）");
    if (spec.some((one) => parentOf(spec, one) === name))
      throw new Error("この分類は、中に別の分類があるので、入れられません");
    entry.parent = target;
  }
  await writeCategorySpec(spec);
}

export async function renameIdolCategory(name: string, newName: string): Promise<void> {
  const label = cleanCategoryName(newName);
  const spec = materializedSpec();
  const entry = spec.find((one) => one.name === name);
  if (!entry) throw new Error("その分類が見つかりません");
  if (ALWAYS_CATEGORIES.includes(name)) throw new Error("この分類の名前は、変えられません");
  if (spec.some((one) => one.name === label && one !== entry))
    throw new Error(`「${label}」は、すでにあります`);
  for (const one of spec) if (one.parent === name) one.parent = label;
  entry.name = label;
  await writeCategorySpec(spec);
}

/**
 * Deletes a category: its groups go to "その他" and its inner categories come up to the top. A built-in category (it is
 * made again whenever it is missing) is hidden instead: it stays in the file, marked hidden, until it is added again.
 */
export async function removeIdolCategory(name: string): Promise<void> {
  if (name === UNCATEGORIZED) throw new Error("この分類は、消せません");
  const spec = materializedSpec();
  const entry = spec.find((one) => one.name === name);
  if (!entry) throw new Error("その分類が見つかりません");
  const builtIn = ALWAYS_CATEGORIES.includes(name);
  const rest = builtIn ? spec : spec.filter((one) => one !== entry);
  for (const one of rest) if (one.parent === name) delete one.parent;
  if (entry.groups.length > 0) {
    const other = rest.find((one) => one.name === UNCATEGORIZED);
    if (other) other.groups.push(...entry.groups);
    else rest.push({ name: UNCATEGORIZED, groups: [...entry.groups] });
  }
  if (builtIn) {
    entry.groups = [];
    entry.hidden = true;
    delete entry.parent;
  }
  await writeCategorySpec(rest);
}

/**
 * Merges categories into one: the groups of all of them go into the new one (which may be one of the old names), the
 * categories you made are deleted, the built-in ones (and "その他") stay, empty. Categories inside an old one come up to
 * the new one.
 */
export async function mergeIdolCategories(
  names: string[],
  newName: string,
): Promise<{ groups: number; removed: string[]; kept: string[] }> {
  const label = cleanCategoryName(newName);
  const spec = materializedSpec();
  const sources = [...new Set(names)]
    .map((name) => spec.find((one) => one.name === name))
    .filter((one): one is CategorySpec => Boolean(one));
  if (sources.length < 2) throw new Error("合体する分類を、2つ以上選んでください");
  if (spec.some((one) => one.name === label && !sources.includes(one)))
    throw new Error(`「${label}」は、すでにあります`);
  let target = spec.find((one) => one.name === label);
  if (!target) {
    target = {
      name: label,
      groups: [],
      ...(sources[0]!.parent ? { parent: sources[0]!.parent } : {}),
    };
    spec.splice(spec.indexOf(sources[0]!) + 1, 0, target);
  }
  const removed: string[] = [];
  const kept: string[] = [];
  let moved = 0;
  for (const source of sources) {
    if (source === target) continue;
    for (const group of source.groups) {
      if (!target.groups.includes(group)) {
        target.groups.push(group);
        moved += 1;
      }
    }
    source.groups = [];
    for (const one of spec) {
      if (one.parent === source.name) {
        if (target.parent)
          delete one.parent; // two levels only
        else one.parent = target.name;
      }
    }
    if (ALWAYS_CATEGORIES.includes(source.name) || source.name === UNCATEGORIZED)
      kept.push(source.name);
    else removed.push(source.name);
  }
  await writeCategorySpec(spec.filter((one) => !removed.includes(one.name)));
  return { groups: moved, removed, kept };
}

/** A roster line for a member typed in by hand (only the name is needed; the rest is optional). */
export function manualMemberLine(input: {
  group: string;
  name: string;
  description?: string;
  nickname?: string;
  generation?: string;
  birthday?: string;
  hometown?: string;
}): string {
  const clean = (value: string | undefined, max: number): string =>
    (value ?? "")
      .replace(/[\r\n|=]+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, max);
  const group = clean(input.group, MAX_GROUP_NAME);
  const name = clean(input.name, 60);
  if (!group || !name) throw new Error("グループと名前を入れてください");
  const parts = [group, name, clean(input.description, 200) || "idol"];
  const extra: Array<[string, string]> = [
    ["愛称", clean(input.nickname, 40)],
    ["期", clean(input.generation, 20)],
    ["誕生日", clean(input.birthday, 20)],
    ["出身地", clean(input.hometown, 30)],
  ];
  for (const [key, value] of extra) if (value) parts.push(`${key}=${value}`);
  return parts.join(" | ");
}

const PHOTO_EXT: Record<string, string> = {
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
};

/**
 * Saves a picture (picked on the phone) as the photo of a member: photos/<group>/<name>.<ext>. A picture the member
 * already has (any extension) is replaced. Only a real JPEG / PNG / WebP of at most 6 MB is accepted.
 */
export async function saveIdolPhoto(
  member: IdolMember,
  mime: string,
  data: Buffer,
): Promise<string> {
  const ext = PHOTO_EXT[mime.toLowerCase()];
  if (!ext || data.length === 0 || !looksLikeImage(data, mime.toLowerCase()))
    throw new Error("jpg・png・webp の画像だけ使えます");
  if (data.length > 6 * 1024 * 1024) throw new Error("画像が大きすぎます（6MB まで）");
  const safe = (value: string) => value.replace(/[\\/:*?"<>|\r\n]+/g, "_").trim();
  const dir = path.join(idolPhotoDir(), safe(member.group));
  await fs.mkdir(dir, { recursive: true });
  const stem = safe(member.name);
  if (!stem) throw new Error("名前が空です");
  for (const other of Object.values(PHOTO_EXT)) {
    if (other !== ext) await fs.rm(path.join(dir, `${stem}${other}`), { force: true });
  }
  const file = path.join(dir, `${stem}${ext}`);
  await fs.writeFile(file, data);
  return file;
}

// ---------------------------------------------------------------- photos
// Face photos are only the ones the user puts in the photos folder; nothing is fetched or drawn here.
// A photo is named "<group letters and digits, lower case>_<id>.<jpg|jpeg|png|webp>", e.g. stu48_501.jpg.

const PHOTO_MIME: Record<string, string> = {
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".webp": "image/webp",
};
const MAX_PHOTO_BYTES = 3 * 1024 * 1024;

export function idolPhotoDir(): string {
  return path.join(resolveOpenClawHome(), "media", "idol-master", "photos");
}

/** A group's letters and digits (any language), lower case: the part of a photo name that stands for the group. */
export function groupSlug(group: string): string {
  return group.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "");
}

/** The file name (without extension) a member's photo must have. */
export function idolPhotoStem(group: string, id: number): string {
  return `${groupSlug(group)}_${id}`;
}

/** The other name a photo file may have: "<group>_<name>" (e.g. akb48_岩立沙穂.jpg), lower case. */
export function idolPhotoNameStem(group: string, name: string): string {
  return `${groupSlug(group)}_${name.trim().toLowerCase()}`;
}

/** The photo file of a member in the photos folder (by id, else by name), or undefined. */
export function idolPhotoFileOf(
  index: Map<string, string>,
  member: IdolMember,
): string | undefined {
  return (
    index.get(idolPhotoStem(member.group, member.id)) ??
    index.get(idolPhotoNameStem(member.group, member.name))
  );
}

/**
 * Photo files present now, by stem (lower case) -> path under the photos folder. A photo is either
 * "<group>_<name or id>.jpg" in the folder itself, or "<name or id>.jpg" inside a folder named after the
 * group ("AKB48/岩立沙穂.jpg"). A missing folder means no photos.
 */
export async function idolPhotoIndex(): Promise<Map<string, string>> {
  const found = new Map<string, string>();
  const root = idolPhotoDir();
  let entries: import("node:fs").Dirent[] = [];
  try {
    entries = await fs.readdir(root, { withFileTypes: true });
  } catch {
    return found;
  }
  const add = (stem: string, relative: string) => {
    if (!found.has(stem)) found.set(stem, relative);
  };
  for (const entry of entries) {
    if (entry.isDirectory()) {
      const slug = groupSlug(entry.name);
      if (!slug) continue;
      let inside: string[] = [];
      try {
        inside = await fs.readdir(path.join(root, entry.name));
      } catch {
        continue;
      }
      for (const name of inside) {
        const ext = path.extname(name).toLowerCase();
        if (PHOTO_MIME[ext])
          add(
            `${slug}_${path.basename(name, path.extname(name)).toLowerCase()}`,
            `${entry.name}/${name}`,
          );
      }
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (PHOTO_MIME[ext])
        add(path.basename(entry.name, path.extname(entry.name)).toLowerCase(), entry.name);
    }
  }
  return found;
}

/** The first bytes of a JPEG, PNG or WebP picture (the server's content-type alone is not trusted). */
export { looksLikeImage as looksLikeIdolImage };
function looksLikeImage(data: Buffer, mime: string): boolean {
  if (mime === "image/jpeg") return data.length > 3 && data[0] === 0xff && data[1] === 0xd8;
  if (mime === "image/png")
    return data.length > 8 && data.subarray(1, 4).toString("latin1") === "PNG";
  if (mime === "image/webp")
    return (
      data.length > 12 &&
      data.subarray(0, 4).toString("latin1") === "RIFF" &&
      data.subarray(8, 12).toString("latin1") === "WEBP"
    );
  return false;
}

/**
 * Downloads the member's photoUrl once into the photos folder (https only, through the guarded fetch that
 * refuses private addresses; at most 3 MB; only a real JPEG, PNG or WebP). Returns the photo, or undefined.
 */
async function downloadIdolPhoto(
  member: IdolMember,
): Promise<{ mime: string; base64: string } | undefined> {
  if (!member.photoUrl) return undefined;
  let url: URL;
  try {
    url = new URL(member.photoUrl);
  } catch {
    return undefined;
  }
  if (url.protocol !== "https:" || url.username || url.password) return undefined;
  try {
    const { response, release } = await fetchWithSsrFGuard({
      url: url.href,
      requireHttps: true,
      maxRedirects: 2,
      timeoutMs: 30_000,
      init: { headers: { "User-Agent": "OpenClaw-idol-photo/1.0" } },
    });
    let data: Buffer;
    let mime = "";
    try {
      if (!response.ok) return undefined;
      mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
      data = await readResponseWithLimit(response, MAX_PHOTO_BYTES, {
        timeoutMs: 30_000,
        chunkTimeoutMs: 10_000,
      });
    } finally {
      await release();
    }
    const ext =
      mime === "image/jpeg"
        ? ".jpg"
        : mime === "image/png"
          ? ".png"
          : mime === "image/webp"
            ? ".webp"
            : "";
    if (!ext || !data.length || !looksLikeImage(data, mime)) return undefined;
    await fs.mkdir(idolPhotoDir(), { recursive: true });
    await fs.writeFile(
      path.join(idolPhotoDir(), `${idolPhotoStem(member.group, member.id)}${ext}`),
      data,
    );
    return { mime, base64: data.toString("base64") };
  } catch {
    return undefined;
  }
}

/**
 * A member's photo as base64: the file in the photos folder, else (when the data has a photoUrl) the one
 * downloaded now. Undefined when there is none (or a file is larger than 3 MB).
 */
export async function readIdolPhoto(
  group: string,
  id: number,
): Promise<{ mime: string; base64: string } | undefined> {
  const member = findIdolMember(group, id);
  const index = await idolPhotoIndex();
  const name = member ? idolPhotoFileOf(index, member) : index.get(idolPhotoStem(group, id));
  if (!name) return member ? await downloadIdolPhoto(member) : undefined;
  const file = path.join(idolPhotoDir(), name);
  try {
    const stat = await fs.stat(file);
    if (!stat.isFile() || stat.size > MAX_PHOTO_BYTES) return undefined;
    const mime = PHOTO_MIME[path.extname(name).toLowerCase()]!;
    return { mime, base64: (await fs.readFile(file)).toString("base64") };
  } catch {
    return undefined;
  }
}

/** The members of a group (empty when there is no such group). */
export function idolMembers(group: string): IdolMember[] {
  return allIdolMembers().filter((member) => member.group === group);
}

export function findIdolMember(group: string, id: number): IdolMember | undefined {
  return allIdolMembers().find((member) => member.group === group && member.id === id);
}

/**
 * The library entry for a member. The look follows the creator's convention (1girl, mature female, then
 * the tags); the profile lists what the data knows. `name` replaces the data's name when given.
 */
export function idolCharacterDraft(
  member: IdolMember,
  name?: string,
): ComicCharacter & { heightCm?: number } {
  const facts = [
    member.generation ? `期: ${member.generation}` : "",
    member.nickname ? `愛称: ${member.nickname}` : "",
    member.age !== undefined ? `年齢: ${member.age}歳` : "",
    member.birthday ? `誕生日: ${member.birthday}` : "",
    member.hometown ? `出身地: ${member.hometown}` : "",
    member.bloodType ? `血液型: ${member.bloodType}型` : "",
    member.heightCm ? `身長: ${member.heightCm}cm` : "",
    member.agency ? `事務所: ${member.agency}` : "",
  ].filter(Boolean);
  const profile = [
    `${member.group}のアイドル。`,
    member.tags.length ? `タイプ: ${member.tags.join("・")}。` : "",
    facts.length ? `${facts.join(" / ")}。` : "",
    member.bio ? `略歴: ${member.bio}` : "",
    `マスターデータ ${member.key} から作成。`,
  ]
    .filter(Boolean)
    .join(" ");
  return {
    name: (name?.trim() || member.name).slice(0, 40),
    look: ["1girl", "mature female", member.description].join(", "),
    profile,
    ...(member.heightCm ? { heightCm: member.heightCm } : {}),
  };
}

// ---------------------------------------------------------------- backups of the fan book
// One file holds the roster, the categories and the removed list (photos are not in it: they stay in the photos folder).

const BACKUPS_OF_FANBOOK_KEPT = 20;

export function fanBookBackupDir(): string {
  return path.join(path.dirname(idolRosterFile()), "backups-fanbook");
}

const readTextOrNull = async (file: string): Promise<string | null> => {
  try {
    return await fs.readFile(file, "utf-8");
  } catch {
    return null;
  }
};

export async function createFanBookBackup(): Promise<{ file: string; members: number }> {
  const dir = fanBookBackupDir();
  await fs.mkdir(dir, { recursive: true });
  const roster = await readTextOrNull(idolRosterFile());
  const body = {
    version: 1,
    createdAt: new Date().toISOString(),
    roster,
    categories: await readTextOrNull(idolCategoriesFile()),
    removed: await readTextOrNull(idolRemovedFile()),
  };
  const stamp = new Date().toISOString().replace(/[-:T]/g, "").slice(0, 14);
  let file = `fanbook-${stamp.slice(0, 8)}-${stamp.slice(8, 14)}.json`;
  for (let n = 1; await readTextOrNull(path.join(dir, file)); n++)
    file = `fanbook-${stamp.slice(0, 8)}-${stamp.slice(8, 14)}-${n}.json`;
  await fs.writeFile(path.join(dir, file), JSON.stringify(body), "utf-8");
  const old = (await fs.readdir(dir)).filter((name) => /^fanbook-.*\.json$/.test(name)).sort();
  for (const stale of old.slice(0, Math.max(0, old.length - BACKUPS_OF_FANBOOK_KEPT)))
    await fs.rm(path.join(dir, stale), { force: true });
  return { file, members: parseIdolRoster(roster ?? "").members.length };
}

export async function listFanBookBackups(): Promise<
  Array<{ file: string; createdAt: string; members: number }>
> {
  const dir = fanBookBackupDir();
  let names: string[] = [];
  try {
    names = (await fs.readdir(dir))
      .filter((name) => /^fanbook-[\d-]+\.json$/.test(name))
      .sort()
      .reverse();
  } catch {
    return [];
  }
  const rows: Array<{ file: string; createdAt: string; members: number }> = [];
  for (const name of names) {
    try {
      const data = JSON.parse(await fs.readFile(path.join(dir, name), "utf-8")) as {
        createdAt?: string;
        roster?: string | null;
      };
      rows.push({
        file: name,
        createdAt: data.createdAt ?? "",
        members: parseIdolRoster(data.roster ?? "").members.length,
      });
    } catch {
      // a broken file is not listed
    }
  }
  return rows;
}

/** Puts a backup back (the present state is backed up first, so a restore can be undone). */
export async function restoreFanBookBackup(
  name: string,
): Promise<{ members: number; safety: string }> {
  if (!/^fanbook-[\d-]+\.json$/.test(name)) throw new Error("バックアップの名前が正しくありません");
  const text = await readTextOrNull(path.join(fanBookBackupDir(), name));
  if (!text) throw new Error("そのバックアップが見つかりません");
  const data = JSON.parse(text) as {
    version?: number;
    roster?: string | null;
    categories?: string | null;
    removed?: string | null;
  };
  if (data.version !== 1) throw new Error("このバックアップは、読めない形式です");
  const safety = (await createFanBookBackup()).file;
  const put = async (file: string, content: string | null | undefined): Promise<void> => {
    if (typeof content === "string") {
      await fs.mkdir(path.dirname(file), { recursive: true });
      const temp = `${file}.tmp-${process.pid}`;
      await fs.writeFile(temp, content, "utf-8");
      await fs.rename(temp, file);
    } else {
      await fs.rm(file, { force: true });
    }
  };
  await put(idolRosterFile(), data.roster);
  await put(idolCategoriesFile(), data.categories);
  await put(idolRemovedFile(), data.removed);
  rosterCache = undefined;
  removedCache = undefined;
  return { members: parseIdolRoster(data.roster ?? "").members.length, safety };
}

/** Puts a group at a place (0 = first) inside its category (drag and drop). */
export async function moveIdolGroupTo(group: string, index: number): Promise<void> {
  const spec = materializedSpec();
  const entry = spec.find((one) => one.groups.includes(group));
  if (!entry) throw new Error("そのグループが見つかりません");
  const at = entry.groups.indexOf(group);
  entry.groups.splice(at, 1);
  entry.groups.splice(Math.min(Math.max(Math.trunc(index) || 0, 0), entry.groups.length), 0, group);
  await writeCategorySpec(spec);
}

/** Puts a category at a place (0 = first) among the categories with the same parent (drag and drop). */
export async function moveIdolCategoryTo(name: string, index: number): Promise<void> {
  const spec = materializedSpec();
  const entry = spec.find((one) => one.name === name);
  if (!entry) throw new Error("その分類が見つかりません");
  const parent = parentOf(spec, entry);
  const rest = spec.filter((one) => one !== entry);
  const siblings = rest.filter((one) => parentOf(spec, one) === parent);
  const place = Math.min(Math.max(Math.trunc(index) || 0, 0), siblings.length);
  const before = siblings[place];
  const at = before
    ? rest.indexOf(before)
    : siblings.length > 0
      ? rest.indexOf(siblings[siblings.length - 1]!) + 1
      : rest.length;
  rest.splice(at, 0, entry);
  await writeCategorySpec(rest);
}
