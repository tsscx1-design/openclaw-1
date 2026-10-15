import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
  addCategory,
  addGroup,
  idolCategoriesFile,
  idolRemovedFile,
  idolRosterFile,
  setGroupCategory,
} from "./idol-master.js";
import { categoryOfGroup, groupStyleFor, toneLinesFromMemo } from "./idol-style.js";

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-style-test-"));
  process.env.OPENCLAW_HOME = home;
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(
    idolRosterFile(),
    [
      "AKB48 | A | idol",
      "Nogizaka46 | B | idol",
      "SomeIdols | C | idol",
      "ActorsGroup | D | idol",
      "Works | E | idol",
      "",
    ].join("\n"),
    "utf-8",
  );
  fs.rmSync(idolCategoriesFile(), { force: true });
  fs.rmSync(idolRemovedFile(), { force: true });
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

describe("outfit and tone by group and category", () => {
  it("knows the category and the top item of a group", () => {
    expect(categoryOfGroup("AKB48")).toEqual({ name: "48グループ", root: "アイドル" });
    expect(categoryOfGroup("Nogizaka46")).toEqual({ name: "坂道シリーズ", root: "アイドル" });
    expect(categoryOfGroup("Nobody")).toBeUndefined();
  });

  it("gives the 48 group, the Sakamichi and the plain idol different outfits", async () => {
    expect(groupStyleFor("AKB48")?.outfit).toContain("checkered pleated-skirt");
    expect(groupStyleFor("Nogizaka46")?.outfit).toContain("elegant");
    await addGroup("歌手", "SomeIdols2");
    await setGroupCategory("SomeIdols", "アイドル");
    expect(groupStyleFor("SomeIdols")?.outfit).toContain("idol stage costume");
  });

  it("gives actors, models and the like their own, and nothing to anime categories or unknown groups", async () => {
    await setGroupCategory("ActorsGroup", "俳優");
    expect(groupStyleFor("ActorsGroup")?.outfit).toContain("formal");
    await setGroupCategory("Works", "アニメ");
    expect(groupStyleFor("Works")).toBeUndefined();
    expect(groupStyleFor("Nobody")).toBeUndefined();
    await addCategory("Mine");
  });

  it("takes the tone lines out of a memo", () => {
    expect(
      toneLinesFromMemo("好き\n口調: 関西弁で明るい\n話し方：ゆっくり\n口癖：えーっと\nただのメモ"),
    ).toEqual(["話し方: 関西弁で明るい", "話し方: ゆっくり", "口癖: えーっと"]);
    expect(toneLinesFromMemo("")).toEqual([]);
  });
});
