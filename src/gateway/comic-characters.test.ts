import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installBlockedTerms } from "./blocked-terms-test-support.js";

let restoreBlockedTerms: () => void = () => undefined;
beforeAll(() => {
  restoreBlockedTerms = installBlockedTerms();
});
afterAll(() => {
  restoreBlockedTerms();
});

let home = "";
vi.mock("./partner-store.js", () => ({ resolveOpenClawHome: () => home }));

const {
  applyCharacterEdit,
  cleanOutfits,
  characterOptionIds,
  characterOptionLabels,
  characterOptionTags,
  cleanSpec,
  lookWithOutfit,
  getCharacter,
  heightTags,
  listCharacters,
  mergeLookTags,
  parseCreatorReply,
  registerCharacter,
  resolveCast,
  saveCharacter,
  specSummary,
  updateCharacter,
} = await import("./comic-characters.js");
const { renderPanel } = await import("./comic-series.js");

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "chara-"));
});
afterEach(async () => {
  await fs.rm(home, { recursive: true, force: true });
});

describe("character spec", () => {
  it("keeps adults adult and heights in range", () => {
    expect(cleanSpec({ age: 15, heightCm: 999, hair: " 銀髪 " })).toEqual({
      age: 20,
      hair: "銀髪",
    });
    expect(cleanSpec({ age: 28, heightCm: 172.4 })).toEqual({ age: 28, heightCm: 172 });
  });

  it("turns centimetres into height words by gender", () => {
    expect(heightTags({ gender: "女性", heightCm: 170 })).toBe("tall, long legs");
    expect(heightTags({ gender: "男性", heightCm: 170 })).toBe("");
    expect(heightTags({ gender: "女性", heightCm: 148 })).toBe(
      "short stature, slim waist, mature female",
    );
    expect(heightTags({ gender: "男性", heightCm: 192 })).toBe("very tall, long legs");
  });

  it("keeps only known character options, shows their names and turns them into exact tags", () => {
    const spec = cleanSpec({
      lookOptions: "navel_piercing, glasses , virgin, nonsense, navel_piercing",
      legs: " むっちり ",
      hips: "大きめ",
    });
    expect(spec.lookOptions).toBe("navel_piercing,glasses");
    expect(spec.legs).toBe("むっちり");
    expect(characterOptionTags(spec.lookOptions)).toBe("navel piercing, glasses");
    expect(characterOptionLabels(spec.lookOptions)).toEqual(["へそピアス", "眼鏡"]);
    expect(characterOptionIds(["bondage_rope", "cat_ears"])).toEqual(["cat_ears"]);
    expect(specSummary(spec)).toContain("外見オプション: へそピアス・眼鏡");
    expect(cleanSpec({ lookOptions: "nonsense" }).lookOptions).toBeUndefined();
  });

  it("joins look tags without repeating one", () => {
    expect(mergeLookTags("1girl, glasses, black hair", "tall, Glasses", "", "navel piercing")).toBe(
      "1girl, glasses, black hair, tall, navel piercing",
    );
  });

  it("summarises the spec in Japanese", () => {
    expect(specSummary({ gender: "女性", age: 24, heightCm: 165, hair: "銀髪" })).toBe(
      "性別: 女性 / 年齢: 24歳 / 身長: 165cm / 髪: 銀髪",
    );
  });

  it("swaps the clothes of a look for an outfit and reads outfits as text lines", () => {
    expect(
      lookWithOutfit(
        "1girl, black hair, red eyes, gothic lolita, black dress, black thighhighs",
        "white bikini, sandals",
      ),
    ).toBe("1girl, black hair, red eyes, white bikini, sandals");
    expect(cleanOutfits("水着: white bikini\nドレス：red evening gown, high heels")).toEqual([
      { name: "水着", tags: "white bikini" },
      { name: "ドレス", tags: "red evening gown, high heels" },
    ]);
  });

  it("keeps the setting sheet and leaves its adult part out of general scripts", () => {
    const spec = cleanSpec({
      firstPerson: "ウチ",
      catchphrase: "マジ卍",
      weakSpots: "耳",
      kinks: "言葉責め",
      age: 17,
    });
    expect(spec.age).toBe(20);
    expect(specSummary(spec)).toContain("弱いところ・感じやすいところ: 耳");
    expect(specSummary(spec, { adult: false })).toBe("年齢: 20歳 / 一人称: ウチ / 口癖: マジ卍");
    const edited = applyCharacterEdit(
      {
        id: "chara-00000001",
        name: "キララ",
        look: "1girl",
        profile: "p",
        loraStatus: "none",
        createdAt: 0,
        updatedAt: 0,
      },
      { spec: { moanStyle: "高い声で甘える" } },
    );
    expect(edited.spec).toEqual({ moanStyle: "高い声で甘える" });
  });

  it("reads the designer's JSON", () => {
    expect(
      parseCreatorReply(
        '```json\n{"look":"1girl, mature female, silver hair","profile":"静かな魔法使い"}\n```',
      ),
    ).toEqual({ look: "1girl, mature female, silver hair", profile: "静かな魔法使い" });
  });
});

describe("library", () => {
  it("updates a few fields of a stored character, keeps its id, and does nothing for a missing one", async () => {
    const entry = await registerCharacter({ name: "ノア", look: "1girl, black hair", profile: "" });
    const updated = await updateCharacter(entry.id, {
      turnaroundJobId: "abc123abc123",
      id: "chara-ffffffff",
    });
    expect(updated?.id).toBe(entry.id);
    expect((await getCharacter(entry.id))?.turnaroundJobId).toBe("abc123abc123");
    expect((await getCharacter(entry.id))?.name).toBe("ノア");
    expect(await updateCharacter("chara-00000000", { name: "x" })).toBeUndefined();
  });

  it("registers once, edits, and feeds the trained LoRA and height to series casts", async () => {
    const entry = await registerCharacter({
      name: "リリア",
      look: "1girl, elf, silver hair",
      profile: "魔法使い",
    });
    expect((await listCharacters()).map((c) => c.id)).toEqual([entry.id]);
    expect((await registerCharacter({ ...entry, characterId: entry.id })).id).toBe(entry.id);

    await saveCharacter({
      ...applyCharacterEdit(entry, { name: "リリアナ" }),
      spec: { heightCm: 172 },
      lora: "D:/x/lilia.safetensors",
      trigger: "ocxabc123",
      loraStatus: "ready",
    });
    const cast = await resolveCast([
      { name: "古い名前", look: "old", profile: "", characterId: entry.id },
      { name: "ユウト", look: "1boy, black hair", profile: "", heightCm: 158 },
    ]);
    expect(cast[0]).toMatchObject({
      name: "リリアナ",
      lora: "D:/x/lilia.safetensors",
      heightCm: 172,
    });
    expect((await getCharacter(entry.id))?.name).toBe("リリアナ");

    const render = renderPanel(cast, {
      frame: "wide",
      scene: "village",
      characters: [
        { name: "リリアナ", pos: "left", action: "smile" },
        { name: "ユウト", pos: "right", action: "surprised" },
      ],
      lines: [],
    });
    expect(render.prompt).toContain("height difference");
    expect(render.regions?.[0]?.prompt).toContain("ocxabc123");
    expect(render.regions?.[0]?.prompt).toContain("taller");
    expect(render.regions?.[1]?.prompt).toContain("shorter");
  });

  it("does not use an unfinished LoRA", async () => {
    const entry = await registerCharacter({ name: "カレン", look: "1girl, red hair", profile: "" });
    await saveCharacter({
      ...entry,
      lora: "D:/x/k.safetensors",
      trigger: "ocxk",
      loraStatus: "training",
    });
    const [member] = await resolveCast([
      { name: "カレン", look: "", profile: "", characterId: entry.id },
    ]);
    expect(member?.lora).toBeUndefined();
  });
});
