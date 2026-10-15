import fsSync from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";
import {
  allIdolMembers,
  findIdolMember,
  idolPhotoDir,
  idolPhotoFileOf,
  idolPhotoIndex,
  idolRosterFile,
  looksLikeIdolImage,
  removeIdolMember,
  saveIdolPhoto,
  serializeRosterMember,
  upsertRosterLines,
  type IdolMember,
} from "./idol-master.js";

// Extras of the fan book that sit next to the roster and are never overwritten by an import:
//  - notes: a memo and the user's own tags per member (member-notes.json)
//  - extra photos: more pictures per member (photos-extra/<group>/<name>/<n>.jpg)
//  - search over everything, duplicates and merging

const homeDir = (): string => path.dirname(idolRosterFile());
const noteKey = (group: string, name: string): string => `${group}\n${name}`;

// ---------------------------------------------------------------- notes (memo + own tags)

export type MemberNote = { memo: string; tags: string[] };
type NoteFile = Record<string, MemberNote>;

const MAX_MEMO = 2000;
const MAX_TAGS = 12;
const MAX_TAG_LENGTH = 20;

const notesFile = (): string => path.join(homeDir(), "member-notes.json");
let notesCache: { file: string; mtime: number; data: NoteFile } | undefined;

function readNotes(): NoteFile {
  const file = notesFile();
  try {
    const mtime = fsSync.statSync(file).mtimeMs;
    if (notesCache && notesCache.file === file && notesCache.mtime === mtime)
      return notesCache.data;
    const parsed = JSON.parse(fsSync.readFileSync(file, "utf-8")) as NoteFile;
    const data: NoteFile = {};
    for (const [key, value] of Object.entries(parsed ?? {})) {
      if (value && typeof value.memo === "string" && Array.isArray(value.tags))
        data[key] = {
          memo: value.memo,
          tags: value.tags.filter((t): t is string => typeof t === "string"),
        };
    }
    notesCache = { file, mtime, data };
    return data;
  } catch {
    return {};
  }
}

export function cleanTags(tags: unknown): string[] {
  const list = Array.isArray(tags)
    ? tags
    : typeof tags === "string"
      ? tags.split(/[,、，\s]+/)
      : [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of list) {
    if (typeof raw !== "string") continue;
    const tag = raw
      .replace(/[\r\n|]+/g, " ")
      .trim()
      .slice(0, MAX_TAG_LENGTH);
    if (!tag || seen.has(tag)) continue;
    seen.add(tag);
    out.push(tag);
    if (out.length >= MAX_TAGS) break;
  }
  return out;
}

export function getMemberNote(group: string, name: string): MemberNote {
  return readNotes()[noteKey(group, name)] ?? { memo: "", tags: [] };
}

export async function setMemberNote(
  group: string,
  name: string,
  memo: string,
  tags: unknown,
): Promise<MemberNote> {
  const all = { ...readNotes() };
  const note: MemberNote = {
    memo: memo.replace(/\r\n/g, "\n").slice(0, MAX_MEMO),
    tags: cleanTags(tags),
  };
  if (!note.memo.trim() && note.tags.length === 0) delete all[noteKey(group, name)];
  else all[noteKey(group, name)] = note;
  const file = notesFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, JSON.stringify(all, null, 1), "utf-8");
  await fs.rename(temp, file);
  notesCache = undefined;
  return note;
}

/** The tags and whether a memo exists, for the member lists (the memo itself is read on the member's page). */
export function noteSummary(group: string, name: string): { myTags: string[]; hasMemo: boolean } {
  const note = readNotes()[noteKey(group, name)];
  return { myTags: note?.tags ?? [], hasMemo: Boolean(note?.memo.trim()) };
}

// ---------------------------------------------------------------- extra photos

const EXTRA_EXT: Record<string, string> = {
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
};
const EXTRA_MIME: Record<string, string> = {
  ".jpg": "image/jpeg",
  ".png": "image/png",
  ".webp": "image/webp",
};
const safeName = (value: string): string => value.replace(/[\\/:*?"<>|\r\n]+/g, "_").trim();

function extraDir(member: Pick<IdolMember, "group" | "name">): string {
  return path.join(homeDir(), "photos-extra", safeName(member.group), safeName(member.name));
}

export async function listExtraPhotos(
  member: Pick<IdolMember, "group" | "name">,
): Promise<number[]> {
  try {
    return (await fs.readdir(extraDir(member)))
      .map((name) => ({
        n: Number(path.basename(name, path.extname(name))),
        ext: path.extname(name).toLowerCase(),
      }))
      .filter((row) => Number.isInteger(row.n) && row.n > 0 && EXTRA_MIME[row.ext])
      .map((row) => row.n)
      .sort((a, b) => a - b);
  } catch {
    return [];
  }
}

async function extraFile(
  member: Pick<IdolMember, "group" | "name">,
  n: number,
): Promise<string | undefined> {
  for (const ext of Object.keys(EXTRA_MIME)) {
    const file = path.join(extraDir(member), `${n}${ext}`);
    try {
      await fs.access(file);
      return file;
    } catch {
      // try the next extension
    }
  }
  return undefined;
}

export async function addExtraPhoto(
  member: Pick<IdolMember, "group" | "name">,
  mime: string,
  data: Buffer,
): Promise<number> {
  const ext = EXTRA_EXT[mime.toLowerCase()];
  if (!ext || !data.length || !looksLikeIdolImage(data, mime.toLowerCase()))
    throw new Error("jpg・png・webp の画像だけ使えます");
  if (data.length > 6 * 1024 * 1024) throw new Error("画像が大きすぎます（6MB まで）");
  const existing = await listExtraPhotos(member);
  const n = (existing[existing.length - 1] ?? 0) + 1;
  await fs.mkdir(extraDir(member), { recursive: true });
  await fs.writeFile(path.join(extraDir(member), `${n}${ext}`), data);
  return n;
}

export async function extraPhotoFile(
  member: Pick<IdolMember, "group" | "name">,
  n: number,
): Promise<string | undefined> {
  return extraFile(member, n);
}

export async function readExtraPhoto(
  member: Pick<IdolMember, "group" | "name">,
  n: number,
): Promise<{ mime: string; base64: string } | undefined> {
  const file = await extraFile(member, n);
  if (!file) return undefined;
  try {
    const stat = await fs.stat(file);
    if (stat.size > 6 * 1024 * 1024) return undefined;
    return {
      mime: EXTRA_MIME[path.extname(file).toLowerCase()]!,
      base64: (await fs.readFile(file)).toString("base64"),
    };
  } catch {
    return undefined;
  }
}

export async function removeExtraPhoto(
  member: Pick<IdolMember, "group" | "name">,
  n: number,
): Promise<void> {
  const file = await extraFile(member, n);
  if (!file) throw new Error("その写真が見つかりません");
  await fs.rm(file, { force: true });
}

/** The extra picture becomes the main photo; the present main photo goes to the extras (nothing is lost). */
export async function promoteExtraPhoto(member: IdolMember, n: number): Promise<void> {
  const picked = await extraFile(member, n);
  if (!picked) throw new Error("その写真が見つかりません");
  const pickedData = await fs.readFile(picked);
  const pickedMime = EXTRA_MIME[path.extname(picked).toLowerCase()]!;
  const index = await idolPhotoIndex();
  const present = idolPhotoFileOf(index, member);
  let presentData: Buffer | undefined;
  let presentMime = "";
  if (present) {
    const file = path.join(idolPhotoDir(), present);
    try {
      presentData = await fs.readFile(file);
      presentMime =
        (
          {
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".png": "image/png",
            ".webp": "image/webp",
          } as Record<string, string>
        )[path.extname(file).toLowerCase()] ?? "";
    } catch {
      presentData = undefined;
    }
  }
  await saveIdolPhoto(member, pickedMime, pickedData);
  await fs.rm(picked, { force: true });
  if (presentData && presentMime)
    await addExtraPhoto(member, presentMime, presentData).catch(() => undefined);
}

// ---------------------------------------------------------------- search over the whole fan book

/** Lower case, full-width to half-width, no spaces or middle dots; a few variant kanji are made equal. */
export function normalizeName(value: string): string {
  const variants: Record<string, string> = {
    髙: "高",
    﨑: "崎",
    德: "徳",
    邊: "辺",
    邉: "辺",
    澤: "沢",
    濱: "浜",
    嶋: "島",
    條: "条",
    國: "国",
    齋: "斎",
    齊: "斉",
    與: "与",
    廣: "広",
    櫻: "桜",
  };
  return value
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[\s　・･·\-_.]+/g, "")
    .replace(/[髙﨑德邊邉澤濱嶋條國齋齊與廣櫻]/g, (char) => variants[char] ?? char);
}

export type MemberHit = {
  group: string;
  id: number;
  name: string;
  nickname: string;
  photo: boolean;
  photoV: number;
  matched: string;
};

/** Members whose name, nickname, group, hometown, generation, agency, tags or memo contain the words (all words must match). */
export async function searchMembers(query: string, limit = 80): Promise<MemberHit[]> {
  const words = query
    .normalize("NFKC")
    .toLowerCase()
    .split(/[\s　]+/)
    .map(normalizeName)
    .filter(Boolean)
    .slice(0, 5);
  if (words.length === 0) return [];
  const photos = await idolPhotoIndex();
  const scored: Array<{ hit: MemberHit; score: number }> = [];
  for (const member of allIdolMembers()) {
    const note = getMemberNote(member.group, member.name);
    const fields: Array<[string, string]> = [
      ["名前", member.name],
      ["愛称", member.nickname ?? ""],
      ["グループ", member.group],
      ["出身地", member.hometown ?? ""],
      ["期", member.generation ?? ""],
      ["事務所", member.agency ?? ""],
      ["タイプ", member.tags.join(" ")],
      ["自分のタグ", note.tags.join(" ")],
      ["メモ", note.memo],
    ];
    const normalized = fields.map(([label, value]) => [label, normalizeName(value)] as const);
    let score = 0;
    let matchedLabel = "";
    let all = true;
    for (const word of words) {
      const found = normalized.find(([, value]) => value.includes(word));
      if (!found) {
        all = false;
        break;
      }
      score +=
        found[0] === "名前"
          ? normalizeName(member.name) === word
            ? 100
            : normalizeName(member.name).startsWith(word)
              ? 60
              : 40
          : found[0] === "愛称"
            ? 30
            : 10;
      matchedLabel ||= found[0];
    }
    if (!all) continue;
    scored.push({
      hit: {
        group: member.group,
        id: member.id,
        name: member.name,
        nickname: member.nickname ?? "",
        photo: idolPhotoFileOf(photos, member) !== undefined || Boolean(member.photoUrl),
        photoV: 0,
        matched: matchedLabel,
      },
      score,
    });
  }
  scored.sort((a, b) => b.score - a.score);
  return scored.slice(0, limit).map((row) => row.hit);
}

// ---------------------------------------------------------------- duplicates and merging

export type DuplicateCluster = {
  name: string;
  members: Array<{ group: string; id: number; name: string; photo: boolean }>;
};

/** People that look like the same person: the same name once spaces, dots and variant kanji are ignored. */
export async function findDuplicates(limit = 100): Promise<DuplicateCluster[]> {
  const photos = await idolPhotoIndex();
  const byName = new Map<string, IdolMember[]>();
  for (const member of allIdolMembers()) {
    const key = normalizeName(member.name);
    if (key.length < 2) continue;
    byName.set(key, [...(byName.get(key) ?? []), member]);
  }
  const clusters: DuplicateCluster[] = [];
  for (const members of byName.values()) {
    if (members.length < 2) continue;
    clusters.push({
      name: members[0]!.name,
      members: members.map((member) => ({
        group: member.group,
        id: member.id,
        name: member.name,
        photo: idolPhotoFileOf(photos, member) !== undefined || Boolean(member.photoUrl),
      })),
    });
  }
  return clusters.slice(0, limit);
}

const FILLABLE = [
  "birthday",
  "age",
  "bloodType",
  "heightCm",
  "hometown",
  "generation",
  "nickname",
  "agency",
  "bio",
  "photoUrl",
  "xUrl",
  "source",
] as const;

/** Keeps one member: empty fields are filled from the others, the memo and tags are joined, the others are hidden. */
export async function mergeMembers(
  keep: { group: string; id: number },
  others: Array<{ group: string; id: number }>,
): Promise<{ filled: string[]; removed: number }> {
  const target = findIdolMember(keep.group, keep.id);
  if (!target) throw new Error("残す人が見つかりません");
  const sources = others
    .map((one) => findIdolMember(one.group, one.id))
    .filter(
      (one): one is IdolMember =>
        Boolean(one) && !(one!.group === target.group && one!.id === target.id),
    );
  if (sources.length === 0) throw new Error("まとめる相手が見つかりません");
  const merged: IdolMember = { ...target, tags: [...target.tags] };
  const filled: string[] = [];
  for (const source of sources) {
    for (const field of FILLABLE) {
      if (merged[field] === undefined && source[field] !== undefined) {
        (merged as Record<string, unknown>)[field] = source[field];
        filled.push(field);
      }
    }
    for (const tag of source.tags) if (!merged.tags.includes(tag)) merged.tags.push(tag);
  }
  await upsertRosterLines([serializeRosterMember(merged)]);
  // the main photo: when the kept one has none, the first of the others that has one
  const photos = await idolPhotoIndex();
  if (idolPhotoFileOf(photos, target) === undefined && !target.photoUrl) {
    for (const source of sources) {
      const name = idolPhotoFileOf(photos, source);
      if (!name) continue;
      const file = path.join(idolPhotoDir(), name);
      const mime = (
        {
          ".jpg": "image/jpeg",
          ".jpeg": "image/jpeg",
          ".png": "image/png",
          ".webp": "image/webp",
        } as Record<string, string>
      )[path.extname(file).toLowerCase()];
      if (!mime) continue;
      try {
        await saveIdolPhoto(target, mime, await fs.readFile(file));
        break;
      } catch {
        // try the next one
      }
    }
  }
  // the notes
  const notes = [
    getMemberNote(target.group, target.name),
    ...sources.map((one) => getMemberNote(one.group, one.name)),
  ];
  const memo = notes
    .map((note) => note.memo.trim())
    .filter(Boolean)
    .join("\n---\n");
  const tags = cleanTags(notes.flatMap((note) => note.tags));
  if (memo || tags.length > 0) await setMemberNote(target.group, target.name, memo, tags);
  for (const source of sources) await removeIdolMember(source.group, source.id);
  return { filled: [...new Set(filled)], removed: sources.length };
}
