import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  characterSheetValues,
  partnerOfCharacter,
  syncPartnerFromCharacter,
} from "./character-partner.js";
import { cleanSheetValues, cleanSpec, type LibraryCharacter } from "./comic-characters.js";
import { listPartnerProfiles, loadPartnerCharacterSheet } from "./server-methods/partners.js";

let home = "";
let before: string | undefined;

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "partner-test-"));
  before = process.env.OPENCLAW_HOME;
  process.env.OPENCLAW_HOME = home;
});
afterEach(async () => {
  if (before === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = before;
  await fs.rm(home, { recursive: true, force: true });
});

function character(extra: Partial<LibraryCharacter> = {}): LibraryCharacter {
  return {
    id: "chara-12345678",
    name: "テスト子",
    look: "1girl",
    profile: "明るい人",
    spec: cleanSpec({
      age: 24,
      firstPerson: "わたし",
      hair: "黒のボブ",
      hobbies: "散歩",
      personality: "人懐っこい",
      likes: "お茶",
    }),
    loraStatus: "none",
    createdAt: 1,
    updatedAt: 1,
    ...extra,
  };
}

describe("a library character chats as a partner", () => {
  it("fills the partner-sheet items from the character's settings, and typed items win", () => {
    const values = characterSheetValues(
      character({ sheetValues: { 一人称: "あたし", 口癖: "x" } }),
    );
    expect(values["外見の年齢"]).toBe("24歳");
    expect(values["髪型・髪色"]).toBe("黒のボブ");
    expect(values["趣味・休日の過ごし方"]).toBe("散歩");
    expect(values["一人称"]).toBe("あたし");
  });

  it("makes a partner once, with the sheet, and keeps its own text when the character changes", async () => {
    const made = await syncPartnerFromCharacter(character(), { create: true });
    expect(made?.sourceCharacterId).toBe("chara-12345678");
    expect(made?.persona).toContain("テスト子");
    const sheet = await loadPartnerCharacterSheet(made!);
    expect(sheet).toContain("- **一人称**: わたし");
    expect(sheet).toContain("人懐っこい");

    const edited = character({ spec: cleanSpec({ age: 25, firstPerson: "ボク" }) });
    const again = await syncPartnerFromCharacter(edited, { create: false });
    expect(again?.id).toBe(made?.id);
    expect(await loadPartnerCharacterSheet(again!)).toContain("- **一人称**: ボク");
    expect(
      (await listPartnerProfiles()).filter((p) => p.sourceCharacterId === "chara-12345678"),
    ).toHaveLength(1);
    expect(await partnerOfCharacter(edited)).toBeDefined();
  });

  it("makes no partner when only a refresh is asked and there is none", async () => {
    expect(await syncPartnerFromCharacter(character(), { create: false })).toBeUndefined();
  });

  it("keeps non-empty sheet items only", () => {
    expect(cleanSheetValues({ 一人称: " 私 ", 口癖: "  ", x: 3 })).toEqual({
      一人称: "私",
      x: "3",
    });
    expect(cleanSheetValues({})).toBeUndefined();
  });
});
