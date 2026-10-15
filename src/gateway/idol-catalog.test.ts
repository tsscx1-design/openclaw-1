import { describe, expect, it } from "vitest";
import {
  buildPeopleQuery,
  characterAge,
  commonsThumb,
  wikiPersonName,
  wikipediaRosterLine,
  characterListable,
  characterRosterLine,
  parsePeopleRows,
} from "./idol-catalog.js";

const NOW = new Date("2026-10-12T00:00:00Z");
const row = (qid: string, name: string, birth?: string, gender?: string) => ({
  p: { value: `http://www.wikidata.org/entity/${qid}` },
  pLabel: { value: name },
  ...(birth ? { birth: { value: `${birth}T00:00:00Z` } } : {}),
  ...(gender ? { gender: { value: `http://www.wikidata.org/entity/${gender}` } } : {}),
  links: { value: "12" },
});

describe("idol catalog (people)", () => {
  it("builds a query that always requires a birthday and an age of 18 or more", () => {
    const query = buildPeopleQuery({ kind: "gravure", gender: "female", ageMin: 5, now: NOW });
    expect(query).toContain("wd:Q1328668");
    expect(query).toContain("wdt:P21 wd:Q6581072");
    expect(query).toContain("wdt:P569 ?birth");
    // age 5 is lifted to 18: born on or before 2008-10-12
    expect(query).toContain('"2008-10-12T00:00:00Z"');
    expect(query).not.toContain("wdt:P27");
  });

  it("searches the idol kind across both idol occupations", () => {
    expect(buildPeopleQuery({ kind: "idol", now: NOW })).toContain(
      "VALUES ?occ { wd:Q11346521 wd:Q226008 }",
    );
    expect(buildPeopleQuery({ kind: "vtuber", now: NOW })).toContain("wd:Q55155641");
  });

  it("limits the age range and keeps a random order seeded", () => {
    const query = buildPeopleQuery({
      kind: "actor",
      ageMin: 20,
      ageMax: 29,
      random: true,
      seed: "abc",
      now: NOW,
    });
    expect(query).toContain("wdt:P27 wd:Q17");
    expect(query).toContain('"2006-10-12T00:00:00Z"'); // at least 20
    expect(query).toContain('"1996-10-12T00:00:00Z"'); // at most 29
    expect(query).toContain('MD5(CONCAT(STR(?p), "abc"))');
  });

  it("does not let a name break the query", () => {
    expect(buildPeopleQuery({ kind: "singer", query: 'a" ) } #', now: NOW })).toContain(
      'CONTAINS(?pLabel, "a ) } #")',
    );
  });

  it("does not require a birthday or an age floor for the other kinds, only when an age filter is used", () => {
    const open = buildPeopleQuery({ kind: "singer", now: NOW });
    expect(open).toContain("OPTIONAL { ?p wdt:P569 ?birth. }");
    expect(open).not.toContain("FILTER(?birth");
    const filtered = buildPeopleQuery({ kind: "singer", ageMin: 15, ageMax: 17, now: NOW });
    expect(filtered).toContain("?p wdt:P569 ?birth.");
    expect(filtered).toContain('"2011-10-12T00:00:00Z"');
  });

  it("drops duplicates, and for the gravure kind people written as under 18 (an unknown age is listed)", () => {
    const hits = parsePeopleRows(
      {
        results: {
          bindings: [
            row("Q1", "大人", "1995-05-01", "Q6581072"),
            row("Q1", "大人", "1995-05-01", "Q6581072"),
            row("Q2", "誕生日なし"),
            row("Q3", "未成年", "2012-01-01", "Q6581097"),
            row("Q4", "男性", "1980-03-04", "Q6581097"),
          ],
        },
      },
      10,
      NOW,
      true,
    );
    expect(hits.map((hit) => [hit.qid, hit.gender, hit.age])).toEqual([
      ["Q1", "female", 31],
      ["Q2", "other", undefined],
      ["Q4", "male", 46],
    ]);
    const all = parsePeopleRows(
      { results: { bindings: [row("Q2", "誕生日なし"), row("Q3", "十代", "2012-01-01")] } },
      10,
      NOW,
    );
    expect(all.map((hit) => [hit.qid, hit.age])).toEqual([
      ["Q2", undefined],
      ["Q3", 14],
    ]);
  });
});

describe("idol catalog (pictures and all kinds)", () => {
  it("turns a Commons file address into a small https address", () => {
    expect(commonsThumb("http://commons.wikimedia.org/wiki/Special:FilePath/A%20b.jpg")).toBe(
      "https://commons.wikimedia.org/wiki/Special:FilePath/A%20b.jpg?width=240",
    );
    expect(commonsThumb("http://example.com/a.jpg")).toBe("");
    expect(commonsThumb(undefined)).toBe("");
  });

  it("searches every occupation except gravure with the all kind", () => {
    const query = buildPeopleQuery({ kind: "all", now: NOW });
    expect(query).toContain("wd:Q33999");
    expect(query).toContain("wd:Q55155641");
    expect(query).not.toContain("wd:Q1328668");
  });

  it("keeps the picture of a hit", () => {
    const hits = parsePeopleRows(
      {
        results: {
          bindings: [
            {
              ...row("Q9", "写真あり"),
              image: { value: "http://commons.wikimedia.org/wiki/Special:FilePath/X.jpg" },
            },
          ],
        },
      },
      5,
      NOW,
    );
    expect(hits[0]?.imageUrl).toBe(
      "https://commons.wikimedia.org/wiki/Special:FilePath/X.jpg?width=240",
    );
  });
});

describe("idol catalog (anime characters)", () => {
  it("reads the first number of an age", () => {
    expect(characterAge("17-18")).toBe(17);
    expect(characterAge("Unknown")).toBeUndefined();
    expect(characterAge(undefined)).toBeUndefined();
  });

  it("leaves out characters written as under 18 under an adult work (an unwritten age is listed)", () => {
    expect(characterListable({ age: "22" }, true)).toBe(true);
    expect(characterListable({ age: "17" }, true)).toBe(false);
    expect(characterListable({ age: undefined }, true)).toBe(true);
    expect(characterListable({ age: undefined }, false)).toBe(true);
  });

  it("writes a roster line with the work as the group", () => {
    const line = characterRosterLine(
      {
        source: "anilist",
        id: 7,
        name: "テスト | キャラ",
        englishName: "Test",
        gender: "Female",
        age: "21",
        works: ["作品"],
        description: "説明",
        imageUrl: "https://s4.anilist.co/a.jpg",
        url: "https://anilist.co/character/7",
      },
      "作品",
      "AniList",
    );
    expect(line).toBe(
      "作品 | テスト キャラ | 説明 | キャラ/女性 | 年齢=21 | 写真=https://s4.anilist.co/a.jpg | 出典=AniList 7",
    );
  });
});

describe("idol catalog (Wikipedia lists)", () => {
  it("drops a trailing disambiguation from a page title", () => {
    expect(wikiPersonName("山田花子 (タレント)")).toBe("山田花子");
    expect(wikiPersonName("AH(嗚呼)")).toBe("AH");
    expect(wikiPersonName("相川なつ")).toBe("相川なつ");
  });

  it("writes a roster line with the source and the picture", () => {
    const line = wikipediaRosterLine(
      { title: "山田花子 (タレント)", imageUrl: "https://upload.wikimedia.org/a/b.jpg" },
      "地下アイドル",
      ["idol"],
    );
    expect(line).toBe(
      "地下アイドル | 山田花子 |  | idol | 写真=https://upload.wikimedia.org/a/b.jpg | 出典=Wikipedia 山田花子 (タレント)",
    );
    expect(
      wikipediaRosterLine({ title: "A", imageUrl: "http://evil.example/a.jpg" }, "G", []),
    ).toBe("G | A |  |  | 出典=Wikipedia A");
  });
});

describe("idol images (person picture search)", () => {
  it("only fetches from the known picture hosts", async () => {
    const { downloadPersonImage } = await import("./idol-images.js");
    await expect(downloadPersonImage("http://upload.wikimedia.org/a.jpg")).rejects.toThrow();
    await expect(downloadPersonImage("https://evil.example.com/a.jpg")).rejects.toThrow();
    await expect(
      downloadPersonImage("https://user:pw@upload.wikimedia.org/a.jpg"),
    ).rejects.toThrow();
    await expect(downloadPersonImage("not a url")).rejects.toThrow();
  });
});
