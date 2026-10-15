import fs from "node:fs/promises";
import path from "node:path";
import {
  addExtraPhoto,
  cleanTags,
  getMemberNote,
  listExtraPhotos,
  normalizeName,
  readExtraPhoto,
  setMemberNote,
} from "./idol-extras.js";
import {
  addGroup,
  allIdolMembers,
  createFanBookBackup,
  idolCategories,
  idolMembers,
  idolPhotoDir,
  idolPhotoFileOf,
  idolPhotoIndex,
  idolRosterFile,
  removeIdolGroup,
  saveIdolPhoto,
  serializeRosterMember,
  upsertRosterLines,
  type IdolMember,
} from "./idol-master.js";

// Merging fan-book groups: the people of several groups go into one new group; a person who is in more than one of them
// (the same name once spaces, dots and variant kanji are ignored) becomes one person, with the empty fields filled, the
// notes and tags joined and every picture kept (the main photo of the one, the others as extra photos). Then the old
// groups are taken out and their picture folders are deleted. One backup of the fan book is made first.

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
const MAX_GROUP_NAME = 40;
const safe = (value: string): string => value.replace(/[\\/:*?"<>|\r\n]+/g, "_").trim();
const MIME: Record<string, string> = {
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".webp": "image/webp",
};

export type GroupMergePlan = {
  groups: string[];
  newName: string;
  /** People in the old groups / people the new group will have / names that were the same person. */
  people: number;
  result: number;
  duplicates: number;
};

export type GroupMergeResult = GroupMergePlan & {
  category: string;
  photos: number;
  extraPhotos: number;
  foldersDeleted: number;
  backup: string;
};

function cleanName(value: string): string {
  return value
    .replace(/[\r\n|\\/]+/g, " ")
    .trim()
    .slice(0, MAX_GROUP_NAME);
}

function clusters(groups: string[]): IdolMember[][] {
  const wanted = new Set(groups);
  const byName = new Map<string, IdolMember[]>();
  for (const member of allIdolMembers()) {
    if (!wanted.has(member.group)) continue;
    const key = normalizeName(member.name) || member.name;
    byName.set(key, [...(byName.get(key) ?? []), member]);
  }
  return [...byName.values()];
}

/** What a merge would do (nothing is changed). */
export function planGroupMerge(groups: string[], newName: string): GroupMergePlan {
  const unique = [...new Set(groups.map((group) => group.trim()).filter(Boolean))];
  if (unique.length < 2) throw new Error("合体するグループを、2つ以上選んでください");
  const name = cleanName(newName);
  if (!name) throw new Error("新しいグループの名前を入れてください");
  if (unique.includes(name)) throw new Error("新しい名前は、元のグループと別の名前にしてください");
  const all = clusters(unique);
  const people = all.reduce((sum, list) => sum + list.length, 0);
  return {
    groups: unique,
    newName: name,
    people,
    result: all.length,
    duplicates: people - all.length,
  };
}

export async function mergeGroups(options: {
  groups: string[];
  newName: string;
  category?: string;
}): Promise<GroupMergeResult> {
  const plan = planGroupMerge(options.groups, options.newName);
  const known = new Set(
    idolCategories().flatMap((category) => category.groups.map((row) => row.group)),
  );
  for (const group of plan.groups)
    if (!known.has(group) && idolMembers(group).length === 0)
      throw new Error(`「${group}」が見つかりません`);
  const category =
    options.category?.trim() ||
    idolCategories().find((entry) => entry.groups.some((row) => plan.groups.includes(row.group)))
      ?.name ||
    "その他";
  const backup = (await createFanBookBackup()).file;

  // the new group (a name that is there already is refused)
  await addGroup(category, plan.newName);

  const lines: string[] = [];
  const merged: Array<{ members: IdolMember[]; keeper: IdolMember }> = [];
  const photoIndex = await idolPhotoIndex();
  for (const list of clusters(plan.groups)) {
    // the keeper: the one with a photo, else the first
    const keeper =
      list.find((member) => idolPhotoFileOf(photoIndex, member) !== undefined || member.photoUrl) ??
      list[0]!;
    const one: IdolMember = { ...keeper, group: plan.newName, tags: [...keeper.tags] };
    for (const other of list) {
      if (other === keeper) continue;
      for (const field of FILLABLE)
        if (one[field] === undefined && other[field] !== undefined)
          (one as Record<string, unknown>)[field] = other[field];
      for (const tag of other.tags) if (!one.tags.includes(tag)) one.tags.push(tag);
    }
    lines.push(serializeRosterMember(one));
    merged.push({ members: list, keeper });
  }
  await upsertRosterLines(lines);

  // pictures and notes: kept for every person
  let photos = 0;
  let extraPhotos = 0;
  for (const { members, keeper } of merged) {
    const target = idolMembers(plan.newName).find(
      (member) => normalizeName(member.name) === normalizeName(keeper.name),
    );
    if (!target) continue;
    let haveMain = false;
    for (const member of [keeper, ...members.filter((one) => one !== keeper)]) {
      const rel = idolPhotoFileOf(photoIndex, member);
      if (rel) {
        try {
          const file = path.join(idolPhotoDir(), rel);
          const data = await fs.readFile(file);
          const mime = MIME[path.extname(file).toLowerCase()];
          if (mime) {
            if (!haveMain) {
              await saveIdolPhoto(target, mime, data);
              haveMain = true;
              photos += 1;
            } else {
              await addExtraPhoto(target, mime, data);
              extraPhotos += 1;
            }
          }
        } catch {
          // a picture that cannot be read is skipped
        }
      }
      for (const n of await listExtraPhotos(member)) {
        const extra = await readExtraPhoto(member, n);
        if (!extra) continue;
        try {
          await addExtraPhoto(target, extra.mime, Buffer.from(extra.base64, "base64"));
          extraPhotos += 1;
        } catch {
          // skipped
        }
      }
    }
    const notes = members.map((member) => getMemberNote(member.group, member.name));
    const memo = [...new Set(notes.map((note) => note.memo.trim()).filter(Boolean))].join(
      "\n---\n",
    );
    const tags = cleanTags(notes.flatMap((note) => note.tags));
    if (memo || tags.length > 0) await setMemberNote(plan.newName, target.name, memo, tags);
  }

  // the old groups: out of the fan book, and their picture folders deleted
  let foldersDeleted = 0;
  const roots = [idolPhotoDir(), path.join(path.dirname(idolRosterFile()), "photos-extra")];
  for (const group of plan.groups) {
    await removeIdolGroup(group);
    for (const root of roots) {
      const dir = path.join(root, safe(group));
      if (path.dirname(dir) !== root || !safe(group)) continue;
      try {
        await fs.access(dir);
        await fs.rm(dir, { recursive: true, force: true });
        foldersDeleted += 1;
      } catch {
        // no folder
      }
    }
  }
  return { ...plan, category, photos, extraPhotos, foldersDeleted, backup };
}
