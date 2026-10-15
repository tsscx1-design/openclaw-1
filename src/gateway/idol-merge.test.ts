import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { addExtraPhoto, getMemberNote, listExtraPhotos, setMemberNote } from "./idol-extras.js";
import {
  addCategory,
  addGroup,
  idolCategories,
  idolCategoriesFile,
  idolGroups,
  idolMembers,
  idolPhotoDir,
  idolRemovedFile,
  idolRosterFile,
  mergeIdolCategories,
  saveIdolPhoto,
} from "./idol-master.js";
import { mergeGroups, planGroupMerge } from "./idol-merge.js";

const SEED = [
  "MergeA | 髙橋 みなみ | idol | pop | 誕生日=1991-04-08 | 出身地=東京都",
  "MergeB | 高橋みなみ | idol | cool | 愛称=たかみな | 事務所=Office B",
  "MergeA | 山田 花子 | idol",
  "MergeB | 佐藤 次郎 | idol | rock | 出身地=大阪府",
  "",
].join("\n");
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(64, 1)]);

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-merge-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});
beforeEach(() => {
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  for (const file of [
    idolCategoriesFile(),
    idolRemovedFile(),
    path.join(path.dirname(idolRosterFile()), "member-notes.json"),
  ])
    fs.rmSync(file, { force: true });
  for (const dir of ["photos-extra", "photos", "backups-fanbook"])
    fs.rmSync(path.join(path.dirname(idolRosterFile()), dir), { recursive: true, force: true });
});

describe("merging groups", () => {
  it("counts the people and the same person before anything is changed", () => {
    expect(planGroupMerge(["MergeA", "MergeB"], "合体グループ")).toMatchObject({
      people: 4,
      result: 3,
      duplicates: 1,
      newName: "合体グループ",
    });
    expect(() => planGroupMerge(["MergeA"], "X")).toThrow();
    expect(() => planGroupMerge(["MergeA", "MergeB"], "MergeA")).toThrow();
    expect(() => planGroupMerge(["MergeA", "MergeB"], "")).toThrow();
  });

  it("makes one new group with the same person once, keeps every picture and note, and deletes the old groups and their folders", async () => {
    const a = idolMembers("MergeA").find((m) => m.name.includes("みなみ"))!;
    const b = idolMembers("MergeB").find((m) => m.name.includes("みなみ"))!;
    await saveIdolPhoto(a, "image/jpeg", JPEG);
    await saveIdolPhoto(b, "image/jpeg", JPEG);
    await addExtraPhoto(a, "image/jpeg", JPEG);
    await setMemberNote("MergeA", a.name, "メモA", ["タグ1"]);
    await setMemberNote("MergeB", b.name, "メモB", ["タグ2"]);
    await addCategory("テスト分類");
    await addGroup("テスト分類", "MergeC");

    const result = await mergeGroups({
      groups: ["MergeA", "MergeB"],
      newName: "合体グループ",
      category: "テスト分類",
    });
    expect(result).toMatchObject({ people: 4, result: 3, duplicates: 1, category: "テスト分類" });
    expect(
      fs.existsSync(path.join(path.dirname(idolRosterFile()), "backups-fanbook", result.backup)),
    ).toBe(true);

    const members = idolMembers("合体グループ");
    expect(members.map((m) => m.name).sort()).toHaveLength(3);
    const minami = members.find((m) => m.name.includes("みなみ"))!;
    expect(minami).toMatchObject({
      birthday: "1991-04-08",
      hometown: "東京都",
      nickname: "たかみな",
      agency: "Office B",
    });
    // main photo of one, the other's main photo and the extra as extra photos
    expect(fs.readdirSync(path.join(idolPhotoDir(), "合体グループ"))).toHaveLength(1);
    expect((await listExtraPhotos(minami)).length).toBe(2);
    expect(getMemberNote("合体グループ", minami.name)).toMatchObject({ memo: "メモA\n---\nメモB" });
    expect(getMemberNote("合体グループ", minami.name).tags.sort()).toEqual(["タグ1", "タグ2"]);

    // the old groups are gone, and so are their folders
    expect(idolGroups().map((row) => row.group)).not.toContain("MergeA");
    expect(idolGroups().map((row) => row.group)).not.toContain("MergeB");
    expect(fs.existsSync(path.join(idolPhotoDir(), "MergeA"))).toBe(false);
    expect(fs.existsSync(path.join(path.dirname(idolRosterFile()), "photos-extra", "MergeA"))).toBe(
      false,
    );
    expect(result.foldersDeleted).toBeGreaterThanOrEqual(2);
    expect(
      idolCategories()
        .find((c) => c.name === "テスト分類")
        ?.groups.map((g) => g.group),
    ).toContain("合体グループ");
  });
});

describe("merging categories", () => {
  it("moves the groups into one new category and deletes the old ones you made", async () => {
    await addCategory("分類X");
    await addCategory("分類Y");
    await addGroup("分類X", "GX");
    await addGroup("分類Y", "GY");
    const result = await mergeIdolCategories(["分類X", "分類Y"], "分類XY");
    expect(result.groups).toBe(2);
    expect([...result.removed].sort()).toEqual(["分類X", "分類Y"]);
    const names = idolCategories().map((c) => c.name);
    expect(names).toContain("分類XY");
    expect(names).not.toContain("分類X");
    expect(names).not.toContain("分類Y");
    expect(
      idolCategories()
        .find((c) => c.name === "分類XY")
        ?.groups.map((g) => g.group)
        .sort(),
    ).toEqual(["GX", "GY"]);
    await expect(mergeIdolCategories(["分類XY"], "Z")).rejects.toThrow();
  });
});
