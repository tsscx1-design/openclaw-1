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
  deleteComicSeries,
  episodeSystemPrompt,
  getComicSeries,
  listComicSeries,
  newComicSeries,
  parseCast,
  parseComicScript,
  renderPanel,
  saveComicSeries,
  speakerPositions,
} = await import("./comic-series.js");

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "comic-"));
});
afterEach(async () => {
  await fs.rm(home, { recursive: true, force: true });
});

const cast = [
  {
    name: "ユウト",
    look: "1boy, black hair, short hair, adventurer clothes",
    profile: "主人公。一人称は俺",
  },
  {
    name: "エルナ",
    look: "1girl, elf, pointy ears, long blonde hair, green eyes, white dress",
    profile: "エルフの魔法使い",
  },
  { name: "ミラ", look: "1girl, red hair, ponytail, knight armor", profile: "騎士" },
];

describe("series storage", () => {
  it("saves, lists and deletes a series", async () => {
    const series = newComicSeries({
      title: "転生したら最強でした",
      genre: "異世界転生",
      premise: "社畜が異世界へ",
      rating: "adult",
      cast,
    });
    await saveComicSeries(series);
    expect((await listComicSeries()).map((s) => s.id)).toEqual([series.id]);
    expect((await getComicSeries(series.id))?.cast).toHaveLength(3);
    expect(await deleteComicSeries(series.id)).toBe(true);
    expect(await listComicSeries()).toEqual([]);
  });

  it("rejects path-like ids", async () => {
    expect(await getComicSeries("../x")).toBeUndefined();
  });
});

describe("parseCast", () => {
  it("keeps named characters with looks and strips prompt syntax", () => {
    const parsed = parseCast(
      '{"cast":[{"name":"ミラ","look":"1girl, <lora:x>, 銀髪, red hair","profile":"騎士"},{"name":"","look":"x"}]}',
    );
    expect(parsed).toHaveLength(1);
    expect(parsed[0]?.look).toBe("1girl, lora:x, red hair");
  });
});

describe("parseComicScript", () => {
  const panel = (i: number) => ({
    frame: i % 2 ? "wide" : "tall",
    scene: `forest ${i}`,
    characters: [{ name: "エルナ", pos: "left", action: "smile" }],
    lines: [{ who: "エルナ", text: `台詞${i}` }],
  });

  it("accepts a slightly short script and defaults unknown values", () => {
    const reply = JSON.stringify({
      title: "第1話",
      summary: "出会い",
      panels: [
        ...Array.from({ length: 20 }, (_, i) => panel(i)),
        { frame: "huge", scene: "x", characters: [{ name: "ユウト", pos: "above" }], lines: [] },
      ],
    });
    const script = parseComicScript(reply, 24);
    expect(script.panels).toHaveLength(21);
    expect(script.panels[20]?.frame).toBe("square");
    expect(script.panels[20]?.characters[0]?.pos).toBe("center");
  });

  it("fails when far too short", () => {
    expect(() => parseComicScript(JSON.stringify({ panels: [panel(0), panel(1)] }), 24)).toThrow(
      /足りません/,
    );
  });
});

describe("prompts", () => {
  it("carries the story so far, the outline and the rating rules", () => {
    const series = newComicSeries({
      title: "t",
      genre: "ハーレム",
      premise: "p",
      rating: "adult",
      cast,
    });
    series.episodes.push({
      no: 1,
      title: "出会い",
      summary: "ユウトがエルナに助けられる",
      jobId: "a",
      createdAt: 0,
    });
    const prompt = episodeSystemPrompt(series, 24, "1. 村に着く");
    expect(prompt).toContain("第1話「出会い」");
    expect(prompt).toContain("1. 村に着く");
    expect(prompt).toContain("20歳以上");
    expect(prompt).toContain("ちょうど24コマ");
  });

  it("writes a long episode in parts, the second continuing from the first", () => {
    const series = newComicSeries({ title: "t", genre: "g", premise: "p", rating: "adult", cast });
    const first = episodeSystemPrompt(series, 24, "1. 村に着く", { index: 1, count: 2 });
    expect(first).toContain("前半");
    expect(first).toContain("次の場面へ続く形で止める");
    const second = episodeSystemPrompt(series, 24, "1. 村に着く", {
      index: 2,
      count: 2,
      previous: "エルナ「行こう」",
    });
    expect(second).toContain("後半");
    expect(second).toContain("エルナ「行こう」");
    expect(second).toContain("引きで終える");
  });
});

describe("renderPanel", () => {
  const series = newComicSeries({ title: "t", genre: "g", premise: "p", rating: "adult", cast });

  it("draws one person with their whole look, as an adult", () => {
    const render = renderPanel(series.cast, {
      frame: "tall",
      scene: "forest, night",
      characters: [{ name: "エルナ", pos: "center", action: "casting spell, serious" }],
      lines: [],
    });
    expect(render.prompt).toBe(
      "1girl, solo, mature female, elf, pointy ears, long blonde hair, green eyes, white dress, casting spell, serious, forest, night",
    );
    expect(render.regions).toBeUndefined();
    expect(render.negative).toBe("");
  });

  it("adds only the negative tags the user gave the characters in the panel", () => {
    const withNegative = series.cast.map((c) =>
      c.name === "エルナ"
        ? { ...c, negative: "freckles, mole" }
        : c.name === "ミラ"
          ? { ...c, negative: "glasses" }
          : c,
    );
    const solo = renderPanel(withNegative, {
      frame: "tall",
      scene: "forest",
      characters: [{ name: "エルナ", pos: "center", action: "standing" }],
      lines: [],
    });
    expect(solo.negative).toBe("freckles, mole");
    const duo = renderPanel(withNegative, {
      frame: "wide",
      scene: "tavern",
      characters: [
        { name: "ミラ", pos: "right", action: "arms crossed" },
        { name: "エルナ", pos: "left", action: "waving" },
      ],
      lines: [],
    });
    expect(duo.negative).toBe("freckles, mole, glasses");
  });

  it("gives each of several people their own region, left to right", () => {
    const render = renderPanel(series.cast, {
      frame: "wide",
      scene: "tavern",
      characters: [
        { name: "ミラ", pos: "right", action: "arms crossed" },
        { name: "ユウト", pos: "left", action: "waving" },
      ],
      lines: [],
    });
    expect(render.prompt).toBe("1girl, 1boy, tavern");
    expect(render.regions?.map((r) => r.x0)).toEqual([0, 0.48]);
    expect(render.regions?.[0]?.prompt).toContain("black hair");
    expect(render.regions?.[0]?.prompt).not.toContain("red hair");
    expect(render.regions?.[1]?.prompt).toContain("red hair, ponytail");
  });

  it("uses trained character LoRAs with their trigger word", () => {
    const withLora = series.cast.map((c) =>
      c.name === "エルナ" ? { ...c, lora: "D:/x/elna.safetensors", trigger: "elna_oc" } : c,
    );
    const render = renderPanel(withLora, {
      frame: "square",
      scene: "close-up",
      characters: [{ name: "エルナ", pos: "center", action: "smile" }],
      lines: [],
    });
    expect(render.prompt).toContain("elna_oc, mature female");
    expect(render.loras).toEqual([{ path: "D:/x/elna.safetensors", scale: 0.85 }]);
  });

  it("knows which side each speaker stands on", () => {
    expect(
      speakerPositions({
        frame: "wide",
        scene: "",
        characters: [{ name: "ユウト", pos: "left", action: "" }],
        lines: [],
      }),
    ).toEqual({ ユウト: "left" });
  });
});
