import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { idolRosterFile, parseIdolRoster, upsertRosterLine } from "./idol-master.js";
import {
  bloodTypeFromLabel,
  extractWikidataPerson,
  isQid,
  retryAfterSeconds,
  wikidataRosterLine,
} from "./idol-wikidata.js";

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-wikidata-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

const item = (id: string) => ({ mainsnak: { datavalue: { value: { id } } } });
const text = (value: string) => ({ mainsnak: { datavalue: { value } } });
const ENTITY = {
  labels: { ja: { value: "岩立沙穂" }, en: { value: "Saho Iwatate" } },
  descriptions: { ja: { value: "日本の歌手" } },
  claims: {
    P569: [
      { mainsnak: { datavalue: { value: { time: "+1994-10-04T00:00:00Z", precision: 11 } } } },
    ],
    P1853: [item("Q1")],
    P19: [item("Q2")],
    P2002: [text("yahho_sahho")],
    P18: [text("Iwatate Saho.jpg")],
    P463: [item("Q3")],
    P106: [item("Q4")],
  },
  sitelinks: { jawiki: { title: "岩立沙穂" } },
};
const LABELS = new Map([
  ["Q1", "blood type B"],
  ["Q2", "神奈川県"],
  ["Q3", "AKB48"],
  ["Q4", "歌手"],
]);

describe("wikidata person", () => {
  it("accepts only item numbers", () => {
    expect(isQid("Q17238494")).toBe(true);
    expect(isQid("Q0")).toBe(false);
    expect(isQid("P569")).toBe(false);
    expect(isQid("Q1; drop")).toBe(false);
    expect(isQid(undefined)).toBe(false);
  });

  it("reads Retry-After as seconds, at most 90, and ignores a missing or odd value", () => {
    expect(retryAfterSeconds("7")).toBe(7);
    expect(retryAfterSeconds("600")).toBe(90);
    expect(retryAfterSeconds("0")).toBe(0);
    expect(retryAfterSeconds(null)).toBeUndefined();
    expect(retryAfterSeconds("soon")).toBeUndefined();
  });

  it("reads the blood type from the label of the blood type item", () => {
    expect(bloodTypeFromLabel("AB型")).toBe("AB");
    expect(bloodTypeFromLabel("blood type B")).toBe("B");
    expect(bloodTypeFromLabel("O")).toBe("O");
    expect(bloodTypeFromLabel("Rh+")).toBeUndefined();
    expect(bloodTypeFromLabel(undefined)).toBeUndefined();
  });

  it("extracts the facts of an item and resolves the labels it points to", () => {
    const p = extractWikidataPerson("Q17238494", ENTITY, LABELS, new Date(Date.UTC(2026, 9, 11)));
    expect(p).toMatchObject({
      qid: "Q17238494",
      name: "岩立沙穂",
      description: "日本の歌手",
      birthday: "1994-10-04",
      age: 32,
      bloodType: "B",
      hometown: "神奈川県",
      xUrl: "https://x.com/yahho_sahho",
      groups: ["AKB48"],
      occupations: ["歌手"],
      wikipediaUrl: "https://ja.wikipedia.org/wiki/%E5%B2%A9%E7%AB%8B%E6%B2%99%E7%A9%82",
      sourceUrl: "https://www.wikidata.org/wiki/Q17238494",
    });
    expect(p.heightCm).toBeUndefined();
    expect(p.photoUrl).toBe(
      "https://commons.wikimedia.org/wiki/Special:FilePath/Iwatate%20Saho.jpg?width=600",
    );
    expect(p.photoPageUrl).toBe("https://commons.wikimedia.org/wiki/File:Iwatate_Saho.jpg");
  });

  it("keeps a height in centimetres or metres and ignores a year-only birthday and an odd height", () => {
    const quantity = (amount: string, unit: string) => [
      { mainsnak: { datavalue: { value: { amount, unit } } } },
    ];
    const base = { labels: { ja: { value: "A" } }, claims: {} as Record<string, unknown[]> };
    const cm = extractWikidataPerson(
      "Q5",
      { ...base, claims: { P2048: quantity("+156.5", "http://www.wikidata.org/entity/Q174728") } },
      new Map(),
    );
    const metre = extractWikidataPerson(
      "Q5",
      { ...base, claims: { P2048: quantity("+1.65", "http://www.wikidata.org/entity/Q11573") } },
      new Map(),
    );
    const odd = extractWikidataPerson(
      "Q5",
      { ...base, claims: { P2048: quantity("+15", "http://www.wikidata.org/entity/Q174728") } },
      new Map(),
    );
    const yearOnly = extractWikidataPerson(
      "Q5",
      {
        ...base,
        claims: {
          P569: [
            { mainsnak: { datavalue: { value: { time: "+1994-00-00T00:00:00Z", precision: 9 } } } },
          ],
        },
      },
      new Map(),
    );
    expect(cm.heightCm).toBe(156.5);
    expect(metre.heightCm).toBe(165);
    expect(odd.heightCm).toBeUndefined();
    expect(yearOnly.birthday).toBeUndefined();
  });

  it("writes a roster line that reads back to the same facts, with its source", () => {
    const person = extractWikidataPerson(
      "Q17238494",
      ENTITY,
      LABELS,
      new Date(Date.UTC(2026, 9, 11)),
    );
    const line = wikidataRosterLine(person);
    const back = parseIdolRoster(line).members[0]!;
    expect(back).toMatchObject({
      group: "AKB48",
      name: "岩立沙穂",
      tags: ["歌手"],
      birthday: "1994-10-04",
      bloodType: "B",
      hometown: "神奈川県",
      xUrl: "https://x.com/yahho_sahho",
      source: "Wikidata Q17238494",
    });
    expect(back.photoUrl).toBe(person.photoUrl);
    const custom = parseIdolRoster(
      wikidataRosterLine(person, { group: "My|Group", name: "別名\n行", tags: ["a", "b"] }),
    ).members[0]!;
    expect(custom).toMatchObject({ group: "My｜Group", name: "別名 行", tags: ["a", "b"] });
  });
});

describe("roster upsert", () => {
  it("adds a new line, replaces the line of the same group and name, backs up first and keeps the rest", async () => {
    const file = idolRosterFile();
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, "# list\nAlpha | Aki | idol | pop\nBeta | Cleo | idol\n", "utf-8");
    const added = await upsertRosterLine("Gamma | Dee | idol | calm | 誕生日=2000-01-01");
    expect(added).toMatchObject({ action: "added", group: "Gamma", name: "Dee" });
    expect(added.backup && fs.existsSync(added.backup)).toBe(true);
    const updated = await upsertRosterLine("Alpha | Aki | idol | pop | 期=3期");
    expect(updated.action).toBe("updated");
    const lines = fs.readFileSync(file, "utf-8").split("\n");
    expect(lines).toEqual([
      "# list",
      "Alpha | Aki | idol | pop | 期=3期",
      "Beta | Cleo | idol",
      "Gamma | Dee | idol | calm | 誕生日=2000-01-01",
      "",
    ]);
  });

  it("refuses a line that is not one member, and creates the file when there is none", async () => {
    await expect(upsertRosterLine("OnlyGroup")).rejects.toThrow();
    await expect(upsertRosterLine("A | B | idol\nC | D | idol")).rejects.toThrow();
    const file = idolRosterFile();
    fs.rmSync(file);
    const created = await upsertRosterLine("New | Eli | idol");
    expect(created).toMatchObject({ action: "added", group: "New", name: "Eli" });
    expect(created.backup).toBeUndefined();
    expect(fs.readFileSync(file, "utf-8")).toBe("New | Eli | idol\n");
  });
});
