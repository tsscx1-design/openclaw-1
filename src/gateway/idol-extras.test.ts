import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  addExtraPhoto,
  cleanTags,
  findDuplicates,
  getMemberNote,
  listExtraPhotos,
  mergeMembers,
  normalizeName,
  noteSummary,
  promoteExtraPhoto,
  readExtraPhoto,
  removeExtraPhoto,
  searchMembers,
  setMemberNote,
} from "./idol-extras.js";
import {
  findIdolMember,
  idolCategoriesFile,
  idolMembers,
  idolRemovedFile,
  idolRosterFile,
  moveIdolCategoryTo,
  moveIdolGroupTo,
  idolCategories,
  addCategory,
  addGroup,
} from "./idol-master.js";

const SEED = [
  "Alpha | 髙橋 みなみ | idol | pop | 誕生日=1991-04-08 | 出身地=東京都",
  "Beta | 高橋みなみ | idol | cool | 愛称=たかみな | 事務所=Office B",
  "Alpha | 山田 花子 | idol",
  "Gamma | 佐藤 次郎 | idol | rock | 出身地=大阪府",
  "",
].join("\n");
const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(64, 1)]);

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-extras-test-"));
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
  fs.rmSync(path.join(path.dirname(idolRosterFile()), "photos-extra"), {
    recursive: true,
    force: true,
  });
  fs.rmSync(path.join(path.dirname(idolRosterFile()), "photos"), { recursive: true, force: true });
});

describe("notes", () => {
  it("keeps a memo and own tags apart from the roster, and drops an empty note", async () => {
    const member = idolMembers("Gamma")[0]!;
    expect(getMemberNote("Gamma", member.name)).toEqual({ memo: "", tags: [] });
    await setMemberNote("Gamma", member.name, "最推し\nライブに行った", ["推し", "ロック", "推し"]);
    expect(getMemberNote("Gamma", member.name)).toEqual({
      memo: "最推し\nライブに行った",
      tags: ["推し", "ロック"],
    });
    expect(noteSummary("Gamma", member.name)).toEqual({
      myTags: ["推し", "ロック"],
      hasMemo: true,
    });
    await setMemberNote("Gamma", member.name, "  ", []);
    expect(noteSummary("Gamma", member.name)).toEqual({ myTags: [], hasMemo: false });
  });

  it("cleans tags: separators, duplicates, length and count", () => {
    expect(cleanTags("a, b、c  d")).toEqual(["a", "b", "c", "d"]);
    expect(cleanTags(["x".repeat(40)])[0]!.length).toBe(20);
    expect(cleanTags(Array.from({ length: 30 }, (_, i) => `t${i}`)).length).toBe(12);
  });
});

describe("extra photos", () => {
  it("adds, lists, reads, removes and makes one the main photo (the old main photo is kept)", async () => {
    const member = idolMembers("Gamma")[0]!;
    const first = await addExtraPhoto(member, "image/jpeg", JPEG);
    const second = await addExtraPhoto(
      member,
      "image/jpeg",
      Buffer.concat([JPEG, Buffer.from("second")]),
    );
    expect([first, second]).toEqual([1, 2]);
    expect(await listExtraPhotos(member)).toEqual([1, 2]);
    expect((await readExtraPhoto(member, 2))?.mime).toBe("image/jpeg");
    await expect(addExtraPhoto(member, "image/gif", JPEG)).rejects.toThrow();
    await expect(
      addExtraPhoto(member, "image/jpeg", Buffer.from("not an image")),
    ).rejects.toThrow();
    await removeExtraPhoto(member, 1);
    expect(await listExtraPhotos(member)).toEqual([2]);
    await promoteExtraPhoto(member, 2);
    // the extra became the main photo and no photo is left over from before (there was no main photo)
    expect(await listExtraPhotos(member)).toEqual([]);
    await expect(removeExtraPhoto(member, 9)).rejects.toThrow();
  });

  it("has no limit on the number of extra photos", async () => {
    const member = idolMembers("Gamma")[0]!;
    for (let i = 0; i < 14; i++) await addExtraPhoto(member, "image/jpeg", JPEG);
    expect((await listExtraPhotos(member)).length).toBe(14);
  });
});

describe("search over the whole fan book", () => {
  it("makes full-width, spaces, dots and variant kanji equal", () => {
    expect(normalizeName("髙橋 みなみ")).toBe(normalizeName("高橋みなみ"));
    expect(normalizeName("ＡＢＣ")).toBe("abc");
  });

  it("finds by name, nickname, hometown, group, own tag and memo, all words must match", async () => {
    const gamma = idolMembers("Gamma")[0]!;
    await setMemberNote("Gamma", gamma.name, "ライブで会った", ["推し"]);
    const names = async (q: string) => (await searchMembers(q)).map((hit) => hit.name);
    expect(await names("たかみな")).toEqual(["高橋みなみ"]);
    expect(await names("大阪")).toEqual(["佐藤 次郎"]);
    expect(await names("推し")).toEqual(["佐藤 次郎"]);
    expect(await names("ライブ 佐藤")).toEqual(["佐藤 次郎"]);
    expect(await names("ライブ 高橋")).toEqual([]);
    expect(await names("高橋みなみ")).toHaveLength(2); // both spellings
    expect(await searchMembers("   ")).toEqual([]);
  });
});

describe("duplicates and merging", () => {
  it("finds the same person under two spellings and merges them into one", async () => {
    const clusters = await findDuplicates();
    expect(clusters).toHaveLength(1);
    expect(clusters[0]!.members.map((m) => m.group).sort()).toEqual(["Alpha", "Beta"]);
    const keep = idolMembers("Beta")[0]!;
    const other = idolMembers("Alpha").find((m) => m.name.includes("髙"))!;
    await setMemberNote("Alpha", other.name, "元のメモ", ["a"]);
    const result = await mergeMembers({ group: "Beta", id: keep.id }, [
      { group: "Alpha", id: other.id },
    ]);
    expect(result.removed).toBe(1);
    const merged = findIdolMember("Beta", keep.id)!;
    expect(merged.birthday).toBe("1991-04-08"); // filled from the other
    expect(merged.hometown).toBe("東京都");
    expect(merged.nickname).toBe("たかみな"); // kept
    expect(idolMembers("Alpha").map((m) => m.name)).toEqual(["山田 花子"]); // the other is hidden
    expect(getMemberNote("Beta", merged.name)).toEqual({ memo: "元のメモ", tags: ["a"] });
    expect(await findDuplicates()).toEqual([]);
    await expect(mergeMembers({ group: "Beta", id: keep.id }, [])).rejects.toThrow();
  });
});

describe("drag and drop places", () => {
  it("puts a group and a category at a place", async () => {
    await addCategory("Mine");
    await addGroup("Mine", "G1");
    await addGroup("Mine", "G2");
    await addGroup("Mine", "G3");
    await moveIdolGroupTo("G3", 0);
    expect(
      idolCategories()
        .find((c) => c.name === "Mine")!
        .groups.map((g) => g.group),
    ).toEqual(["G3", "G1", "G2"]);
    await moveIdolGroupTo("G3", 99);
    expect(
      idolCategories()
        .find((c) => c.name === "Mine")!
        .groups.map((g) => g.group),
    ).toEqual(["G1", "G2", "G3"]);
    await moveIdolCategoryTo("Mine", 0);
    expect(idolCategories()[0]!.name).toBe("Mine");
    await moveIdolCategoryTo("Mine", 99);
    const names = idolCategories().map((c) => c.name);
    expect(names.indexOf("Mine")).toBeGreaterThan(names.indexOf("YouTuber") - 1);
  });
});
