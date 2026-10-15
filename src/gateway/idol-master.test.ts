import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
  addCategory,
  addGroup,
  ageFromBirthday,
  allIdolMembers,
  findIdolMember,
  idolCharacterDraft,
  idolCategories,
  idolCategoriesFile,
  idolGroups,
  idolMembers,
  idolPhotoDir,
  idolPhotoFileOf,
  idolPhotoIndex,
  idolPhotoStem,
  createFanBookBackup,
  listFanBookBackups,
  restoreFanBookBackup,
  idolRemovedFile,
  idolRoster,
  idolRosterFile,
  manualMemberLine,
  moveIdolCategory,
  moveIdolGroup,
  parseIdolRoster,
  readIdolPhoto,
  removeIdolCategory,
  removeIdolGroup,
  removeIdolMember,
  renameIdolCategory,
  saveIdolPhoto,
  setGroupCategory,
  setIdolCategoryParent,
  upsertRosterLine,
} from "./idol-master.js";

// All members now come from the roster file in the home folder. The tests use a home folder of their own
// with a small roster, so the user's real roster does not matter.
const SEED = [
  "# test roster",
  "Alpha | Aki | idol, bright smile | pop",
  "Alpha | Bea | idol",
  "Beta | Cleo | idol, cool look | cool",
  "",
].join("\n");
let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-master-test-"));
  process.env.OPENCLAW_HOME = home;
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

describe("idol master", () => {
  it("lists every group once, with its member count, in the order they first appear", () => {
    expect(idolGroups()).toEqual([
      { group: "Alpha", count: 2 },
      { group: "Beta", count: 1 },
    ]);
  });

  it("has unique keys and a name for every member", () => {
    const members = allIdolMembers();
    expect(members.length).toBe(3);
    expect(new Set(members.map((m) => m.key)).size).toBe(members.length);
    expect(members.every((m) => m.name.trim() && m.description.trim())).toBe(true);
  });

  it("finds a member of a group by id and not by the id of another group", () => {
    const member = idolMembers("Alpha")[0]!;
    expect(findIdolMember("Alpha", member.id)?.key).toBe(member.key);
    expect(findIdolMember("Beta", member.id)).toBeUndefined();
    expect(idolMembers("no such group")).toEqual([]);
  });

  it("names a photo file from the group letters and digits (lower case) and the id", () => {
    expect(idolPhotoStem("STU48", 501)).toBe("stu48_501");
    expect(idolPhotoStem("Nogizaka46", 601)).toBe("nogizaka46_601");
    expect(idolPhotoStem("My Group-2", 7)).toBe("mygroup2_7");
  });

  it("finds a photo by id or by name (group letters and digits, lower case)", () => {
    const member = parseIdolRoster("AKB48 | 岩立沙穂 | idol").members[0]!;
    const byName = new Map([["akb48_岩立沙穂", "AKB48_岩立沙穂.jpg"]]);
    const byId = new Map([[idolPhotoStem(member.group, member.id), `akb48_${member.id}.png`]]);
    expect(idolPhotoFileOf(byName, member)).toBe("AKB48_岩立沙穂.jpg");
    expect(idolPhotoFileOf(byId, member)).toBe(`akb48_${member.id}.png`);
    expect(idolPhotoFileOf(new Map(), member)).toBeUndefined();
  });

  it("reads photos from the photos folder and from a folder named after the group", async () => {
    const photos = idolPhotoDir();
    fs.mkdirSync(path.join(photos, "Alpha"), { recursive: true });
    fs.writeFileSync(path.join(photos, "Alpha", "Aki.jpg"), Buffer.from([0xff, 0xd8, 0xff, 0xd9]));
    fs.writeFileSync(path.join(photos, "Alpha", "_メンバー名一覧.txt"), "not a photo");
    fs.writeFileSync(path.join(photos, "beta_cleo.png"), Buffer.from([0x89, 0x50, 0x4e, 0x47]));
    const index = await idolPhotoIndex();
    expect(index.get("alpha_aki")).toBe("Alpha/Aki.jpg");
    expect(index.get("beta_cleo")).toBe("beta_cleo.png");
    expect([...index.keys()].some((key) => key.includes("メンバー名一覧"))).toBe(false);
    const aki = idolMembers("Alpha").find((m) => m.name === "Aki")!;
    const bea = idolMembers("Alpha").find((m) => m.name === "Bea")!;
    const cleo = idolMembers("Beta")[0]!;
    expect(idolPhotoFileOf(index, aki)).toBe("Alpha/Aki.jpg");
    expect((await readIdolPhoto("Alpha", aki.id))?.mime).toBe("image/jpeg");
    expect((await readIdolPhoto("Beta", cleo.id))?.mime).toBe("image/png");
    expect(await readIdolPhoto("Alpha", bea.id)).toBeUndefined(); // no photo and no photoUrl
  });

  it("keeps only an https photoUrl of a member", () => {
    expect(
      allIdolMembers().every((m) => m.photoUrl === undefined || m.photoUrl.startsWith("https://")),
    ).toBe(true);
  });

  it("writes the library entry: creator look convention, the given name and a profile with its source", () => {
    const member = allIdolMembers()[0]!;
    const draft = idolCharacterDraft(member, "  別名  ");
    expect(draft.name).toBe("別名");
    expect(draft.look.startsWith("1girl, mature female, ")).toBe(true);
    expect(draft.look.endsWith(member.description)).toBe(true);
    expect(draft.profile).toContain(`マスターデータ ${member.key} から作成`);
    expect(idolCharacterDraft(member).name).toBe(member.name);
  });
});

describe("idol roster file", () => {
  it("reads one member per line, ignores blank and # lines and reports the broken ones", () => {
    const text = [
      "# my list",
      "",
      "NewGroup | Hana | idol, gentle smile, long brown hair | gentle/pop | https://example.com/a.jpg",
      "NewGroup | Mio | idol, cool look",
      "| NoGroup | idol",
      "OnlyGroup",
      "NewGroup | Rin | | cute, active | http://example.com/not-https.jpg",
    ].join("\n");
    const { members, skippedLines } = parseIdolRoster(text);
    expect(members.map((m) => m.name)).toEqual(["Hana", "Mio", "Rin"]);
    expect(skippedLines).toEqual([5, 6]);
    expect(members[0]).toMatchObject({
      group: "NewGroup",
      tags: ["gentle", "pop"],
      photoUrl: "https://example.com/a.jpg",
    });
    expect(members[1]?.tags).toEqual([]);
    expect(members[2]).toMatchObject({ description: "idol", tags: ["cute", "active"] });
    expect(members[2]?.photoUrl).toBeUndefined();
  });

  it("reads key=value parts: birthday (age counted from it), age, height, blood type, hometown, intake, nickname, bio, photo, X", () => {
    const line =
      "G | Hana | idol, gentle smile | gentle/pop | 誕生日=2000年4月1日 | 血液型=A型 | 身長=160cm | 出身地=福岡県 | 期=3期 | 愛称=はなちゃん | 事務所=ABC | 略歴=2018年に加入。 | 写真=https://example.com/h.jpg | X=https://x.com/hana";
    const m = parseIdolRoster(line).members[0]!;
    expect(m).toMatchObject({
      description: "idol, gentle smile",
      tags: ["gentle", "pop"],
      birthday: "2000-04-01",
      bloodType: "A",
      heightCm: 160,
      hometown: "福岡県",
      generation: "3期",
      nickname: "はなちゃん",
      agency: "ABC",
      bio: "2018年に加入。",
      photoUrl: "https://example.com/h.jpg",
      xUrl: "https://x.com/hana",
    });
    expect(m.age).toBeGreaterThanOrEqual(25);
  });

  it("keeps one decimal of the height (156.5cm) and drops a height that is not a person's", () => {
    expect(parseIdolRoster("G | Saho | idol | 身長=156.5cm").members[0]?.heightCm).toBe(156.5);
    expect(parseIdolRoster("G | Saho | idol | 身長=156").members[0]?.heightCm).toBe(156);
    expect(parseIdolRoster("G | Saho | idol | 身長=15").members[0]?.heightCm).toBeUndefined();
    expect(parseIdolRoster("G | Saho | idol | 身長=ひみつ").members[0]?.heightCm).toBeUndefined();
  });

  it("lets key=value parts replace the description and tag columns, and an explicit age wins over the birthday", () => {
    const m = parseIdolRoster("G | Mio | 年齢=22 | 誕生日=2000-01-01 | 期=5期").members[0]!;
    expect(m).toMatchObject({ description: "idol", tags: [], age: 22, generation: "5期" });
  });

  it("counts age in whole years and ignores a broken birthday", () => {
    const now = new Date(Date.UTC(2026, 9, 11));
    expect(ageFromBirthday("2000-10-11", now)).toBe(26);
    expect(ageFromBirthday("2000-10-12", now)).toBe(25);
    expect(ageFromBirthday("2000-02-30", now)).toBeUndefined();
    expect(ageFromBirthday("sometime", now)).toBeUndefined();
    expect(
      parseIdolRoster("G | Rin | idol | cute | 誕生日=いつか | 身長=3").members[0],
    ).not.toHaveProperty("birthday");
  });

  it("puts the extra facts and the history into the library profile", () => {
    const m = parseIdolRoster(
      "G | Hana | idol | pop | 期=3期 | 出身地=福岡県 | 愛称=はなちゃん | 略歴=2018年に加入。",
    ).members[0]!;
    const draft = idolCharacterDraft(m);
    expect(draft.profile).toContain("期: 3期");
    expect(draft.profile).toContain("出身地: 福岡県");
    expect(draft.profile).toContain("愛称: はなちゃん");
    expect(draft.profile).toContain("略歴: 2018年に加入。");
  });

  it("gives the same member the same id every time, and different members different ids", () => {
    const a = parseIdolRoster("G | Hana | idol").members[0]!;
    const b = parseIdolRoster("X | Y | idol\nG | Hana | idol").members[1]!;
    const c = parseIdolRoster("G | Mio | idol").members[0]!;
    expect(a.id).toBe(b.id);
    expect(a.id).not.toBe(c.id);
    expect(a.id).toBeGreaterThanOrEqual(100_000);
  });

  it("follows the file when it changes: new groups and members appear, and a broken line is reported", () => {
    const file = idolRosterFile();
    expect(idolRoster().members.length).toBe(3);
    fs.writeFileSync(
      file,
      `${SEED}Gamma | Dee | idol, calm\nbroken line\nAlpha | Eli | idol\n`,
      "utf-8",
    );
    const groups = idolGroups();
    expect(groups).toEqual([
      { group: "Alpha", count: 3 },
      { group: "Beta", count: 1 },
      { group: "Gamma", count: 1 },
    ]);
    expect(idolRoster().skippedLines).toEqual([6]);
    fs.writeFileSync(file, SEED, "utf-8");
    expect(idolGroups().length).toBe(2);
  });
});

describe("group categories", () => {
  it("puts groups that are in no category under その他 while no category file exists", () => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
    expect(idolCategories()).toEqual([
      { name: "歌手", groups: [] },
      { name: "アイドル", groups: [] },
      { name: "アニメ", groups: [] },
      { name: "アダルトアニメ", groups: [] },
      { name: "俳優", groups: [] },
      { name: "グラビアアイドル", groups: [] },
      { name: "AV女優", groups: [] },
      { name: "モデル", groups: [] },
      { name: "地下アイドル", groups: [] },
      { name: "Vチューバー", groups: [] },
      { name: "YouTuber", groups: [] },
      {
        name: "その他",
        groups: [
          { group: "Alpha", count: 2 },
          { group: "Beta", count: 1 },
        ],
      },
    ]);
  });

  it("uses the defaults for the known groups (48グループ, 坂道シリーズ)", () => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(
      idolRosterFile(),
      [
        "AKB48 | A | idol",
        "SKE48 | B | idol",
        "Nogizaka46 | C | idol",
        "NewGroup | D | idol",
        "",
      ].join("\n"),
      "utf-8",
    );
    expect(idolCategories().map((c) => [c.name, c.groups.map((g) => g.group)])).toEqual([
      ["歌手", []],
      ["アイドル", []],
      ["48グループ", ["AKB48", "SKE48"]],
      ["坂道シリーズ", ["Nogizaka46"]],
      ["アニメ", []],
      ["アダルトアニメ", []],
      ["俳優", []],
      ["グラビアアイドル", []],
      ["AV女優", []],
      ["モデル", []],
      ["地下アイドル", []],
      ["Vチューバー", []],
      ["YouTuber", []],
      ["その他", ["NewGroup"]],
    ]);
  });

  it("moves a group into a category (made when new) and remembers it", async () => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(
      idolRosterFile(),
      ["AKB48 | A | idol", "NewGroup | D | idol", ""].join("\n"),
      "utf-8",
    );
    await setGroupCategory("NewGroup", "K-POP");
    await setGroupCategory("AKB48", "K-POP");
    const after = idolCategories();
    expect(after.find((c) => c.name === "K-POP")?.groups.map((g) => g.group)).toEqual([
      "NewGroup",
      "AKB48",
    ]);
    expect(after.find((c) => c.name === "48グループ")?.groups).toEqual([]);
    await expect(setGroupCategory("NoSuchGroup", "K-POP")).rejects.toThrow();
    await expect(setGroupCategory("AKB48", "  ")).rejects.toThrow();
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  });
});

describe("category tree, order and removal", () => {
  const setup = (roster: string) => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.rmSync(idolRemovedFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), roster, "utf-8");
  };

  it("puts 48グループ and 坂道シリーズ under アイドル by default", () => {
    setup(["AKB48 | A | idol", "Nogizaka46 | C | idol", ""].join("\n"));
    const parents = Object.fromEntries(idolCategories().map((c) => [c.name, c.parent]));
    expect(parents["48グループ"]).toBe("アイドル");
    expect(parents["坂道シリーズ"]).toBe("アイドル");
    expect(parents["歌手"]).toBeUndefined();
  });

  it("puts a category under a big item, takes it out again, and allows two levels only", async () => {
    setup(SEED);
    await addCategory("K-POP");
    await setIdolCategoryParent("K-POP", "歌手");
    expect(idolCategories().find((c) => c.name === "K-POP")?.parent).toBe("歌手");
    await expect(setIdolCategoryParent("アイドル", "K-POP")).rejects.toThrow(); // アイドル has inner categories
    await expect(setIdolCategoryParent("K-POP", "K-POP")).rejects.toThrow();
    await setIdolCategoryParent("K-POP", "");
    expect(idolCategories().find((c) => c.name === "K-POP")?.parent).toBeUndefined();
  });

  it("moves groups and categories one place and renames or deletes a category you made", async () => {
    setup(SEED);
    await addCategory("Mine");
    await addGroup("Mine", "G1");
    await addGroup("Mine", "G2");
    await moveIdolGroup("G2", -1);
    expect(
      idolCategories()
        .find((c) => c.name === "Mine")
        ?.groups.map((g) => g.group),
    ).toEqual(["G2", "G1"]);
    await moveIdolCategory("Mine", -1);
    const names = idolCategories().map((c) => c.name);
    expect(names.indexOf("Mine")).toBeLessThan(names.indexOf("YouTuber"));
    await renameIdolCategory("Mine", "Mine2");
    expect(idolCategories().some((c) => c.name === "Mine2")).toBe(true);
    await expect(renameIdolCategory("俳優", "x")).rejects.toThrow();
    await removeIdolCategory("Mine2");
    expect(idolCategories().some((c) => c.name === "Mine2")).toBe(false);
    expect(
      idolCategories()
        .find((c) => c.name === "その他")
        ?.groups.map((g) => g.group),
    ).toEqual(expect.arrayContaining(["G1", "G2"]));
    // a built-in category is hidden (not made again), and comes back when it is added again
    await removeIdolCategory("歌手");
    expect(idolCategories().some((c) => c.name === "歌手")).toBe(false);
    expect(idolCategories().some((c) => c.name === "アイドル")).toBe(true);
    await addCategory("歌手");
    expect(idolCategories().some((c) => c.name === "歌手")).toBe(true);
    await expect(removeIdolCategory("その他")).rejects.toThrow();
  });

  it("keeps a group inside another group (one level), moves them together, and frees them when the parent is removed", async () => {
    setup(SEED);
    await addCategory("Mine");
    await addGroup("Mine", "Parent");
    await addGroup("Mine", "Child", "Parent");
    const row = (name: string) =>
      idolCategories()
        .find((c) => c.name === "Mine")
        ?.groups.find((g) => g.group === name);
    expect(row("Child")?.parent).toBe("Parent");
    expect(row("Parent")?.parent).toBeUndefined();
    // one level only
    await expect(addGroup("Mine", "Grandchild", "Child")).rejects.toThrow();
    await expect(addGroup("Mine", "Orphan", "Nobody")).rejects.toThrow();
    // the child goes with its parent when the parent changes category
    await addCategory("Other");
    await setGroupCategory("Parent", "Other");
    const other = idolCategories().find((c) => c.name === "Other");
    expect(other?.groups.map((g) => g.group)).toEqual(expect.arrayContaining(["Parent", "Child"]));
    expect(other?.groups.find((g) => g.group === "Child")?.parent).toBe("Parent");
    // removing the parent frees the child
    await removeIdolGroup("Parent");
    const left = idolCategories()
      .flatMap((c) => c.groups)
      .find((g) => g.group === "Child");
    expect(left?.parent).toBeUndefined();
  });

  it("hides a group or a member, and shows it again when it is added again", async () => {
    setup(SEED);
    const alpha = idolMembers("Alpha");
    expect(alpha.length).toBe(2);
    await removeIdolMember("Alpha", alpha[0]!.id);
    expect(idolMembers("Alpha").length).toBe(1);
    await upsertRosterLine(`Alpha | ${alpha[0]!.name} | idol`);
    expect(idolMembers("Alpha").length).toBe(2);
    await removeIdolGroup("Beta");
    expect(idolGroups().map((g) => g.group)).toEqual(["Alpha"]);
    expect(idolCategories().flatMap((c) => c.groups.map((g) => g.group))).not.toContain("Beta");
    await upsertRosterLine("Beta | Back | idol");
    expect(idolGroups().map((g) => g.group)).toContain("Beta");
    fs.rmSync(idolRemovedFile(), { force: true });
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  });
});

describe("fan book backups", () => {
  it("makes a backup, lists it, and puts it back (the present state is saved first)", async () => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.rmSync(idolRemovedFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
    await addCategory("BackupCat");
    const made = await createFanBookBackup();
    expect(made.members).toBe(3);
    expect((await listFanBookBackups()).some((b) => b.file === made.file && b.members === 3)).toBe(
      true,
    );
    // change things after the backup
    fs.writeFileSync(idolRosterFile(), `${SEED}Gamma | Dee | idol\n`, "utf-8");
    await removeIdolGroup("Beta");
    const restored = await restoreFanBookBackup(made.file);
    expect(restored.members).toBe(3);
    expect(
      idolGroups()
        .map((g) => g.group)
        .sort(),
    ).toEqual(["Alpha", "Beta"]);
    expect(idolCategories().some((c) => c.name === "BackupCat")).toBe(true);
    expect((await listFanBookBackups()).some((b) => b.file === restored.safety)).toBe(true);
    await expect(restoreFanBookBackup("../evil.json")).rejects.toThrow();
    await expect(restoreFanBookBackup("fanbook-19990101-000000.json")).rejects.toThrow();
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.rmSync(idolRemovedFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  });
});

describe("free additions", () => {
  it("adds a category and an empty group inside it, and refuses a group that already exists", async () => {
    fs.rmSync(idolCategoriesFile(), { force: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
    await addCategory("K-POP");
    await addGroup("K-POP", "NewJeans");
    const after = idolCategories();
    expect(after.find((c) => c.name === "K-POP")?.groups).toEqual([
      { group: "NewJeans", count: 0 },
    ]);
    await expect(addGroup("K-POP", "NewJeans")).rejects.toThrow();
    await expect(addGroup("K-POP", "Alpha")).rejects.toThrow(); // already in the roster
    await expect(addGroup("K-POP", "   ")).rejects.toThrow();
    await expect(addCategory("  ")).rejects.toThrow();
    // an empty group can still be moved to another category
    await setGroupCategory("NewJeans", "その他");
    expect(
      idolCategories()
        .find((c) => c.name === "その他")
        ?.groups.map((g) => g.group),
    ).toContain("NewJeans");
    fs.rmSync(idolCategoriesFile(), { force: true });
  });

  it("writes the roster line of a member typed by hand, without breaking the line format", () => {
    const line = manualMemberLine({
      group: "Alpha",
      name: "Mia | X",
      nickname: "みあ",
      generation: "3期",
      birthday: "2001-02-03",
      hometown: "東京都",
    });
    expect(line).toBe(
      "Alpha | Mia X | idol | 愛称=みあ | 期=3期 | 誕生日=2001-02-03 | 出身地=東京都",
    );
    const member = parseIdolRoster(line).members[0]!;
    expect(member).toMatchObject({
      group: "Alpha",
      name: "Mia X",
      nickname: "みあ",
      generation: "3期",
      birthday: "2001-02-03",
      hometown: "東京都",
    });
    expect(() => manualMemberLine({ group: "Alpha", name: " " })).toThrow();
  });

  it("saves a picked picture as the member's photo, found by name, also for a Japanese group name", async () => {
    fs.writeFileSync(
      idolRosterFile(),
      ["ひなた坂 | 青空 さくら | idol", "Alpha | Aki | idol", ""].join("\n"),
      "utf-8",
    );
    const member = idolMembers("ひなた坂")[0]!;
    const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);
    const file = await saveIdolPhoto(member, "image/jpeg", jpeg);
    expect(file.endsWith(path.join("ひなた坂", "青空 さくら.jpg"))).toBe(true);
    const index = await idolPhotoIndex();
    expect(idolPhotoFileOf(index, member)).toBe("ひなた坂/青空 さくら.jpg");
    expect((await readIdolPhoto("ひなた坂", member.id))?.mime).toBe("image/jpeg");
    // a PNG replaces the JPEG (one photo per member)
    await saveIdolPhoto(
      member,
      "image/png",
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00]),
    );
    expect(fs.existsSync(file)).toBe(false);
    await expect(
      saveIdolPhoto(member, "image/jpeg", Buffer.from("not an image")),
    ).rejects.toThrow();
    await expect(saveIdolPhoto(member, "text/plain", jpeg)).rejects.toThrow();
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  });
});
