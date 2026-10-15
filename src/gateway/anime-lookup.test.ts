import { describe, expect, it } from "vitest";
import {
  cleanDescription,
  parseAniList,
  parseFill,
  parseJikan,
  pickBestHit,
  searchCharacters,
} from "./anime-lookup.js";

const aniList = {
  data: {
    Page: {
      characters: [
        {
          id: 88575,
          name: { full: "Rem", native: "レム" },
          gender: "Female",
          age: "17-",
          description: "__Rem__ is a maid. ~!Spoiler text!~<br>She has blue hair.",
          image: { large: "https://example.test/rem.png" },
          siteUrl: "https://anilist.co/character/88575",
          media: {
            nodes: [{ title: { native: "Re:ゼロから始める異世界生活", romaji: "Re:Zero" } }],
          },
        },
      ],
    },
  },
};

const fakeFetch = (body: unknown, ok = true) =>
  (async () => ({ ok, status: ok ? 200 : 500, json: async () => body })) as unknown as typeof fetch;

describe("anime-lookup", () => {
  it("cleans markup and spoilers out of a description", () => {
    expect(cleanDescription("__Rem__ is a maid. ~!Spoiler!~<br>Blue hair.")).toBe(
      "Rem is a maid. \nBlue hair.",
    );
  });

  it("reads an AniList answer", () => {
    const [hit] = parseAniList(aniList);
    expect(hit).toMatchObject({
      source: "anilist",
      name: "レム",
      englishName: "Rem",
      gender: "Female",
      works: ["Re:ゼロから始める異世界生活"],
    });
    expect(hit?.description).not.toContain("Spoiler");
  });

  it("reads a Jikan answer", () => {
    const hits = parseJikan({
      data: [
        {
          mal_id: 118763,
          name: "Rem",
          name_kanji: "レム",
          about: "A maid.",
          url: "https://myanimelist.net/character/118763",
        },
      ],
    });
    expect(hits[0]).toMatchObject({ source: "jikan", id: 118763, name: "レム" });
  });

  it("falls back to Jikan when AniList fails, and gives [] when both fail", async () => {
    const calls: string[] = [];
    const mixed = (async (url: string) => {
      calls.push(String(url));
      if (String(url).includes("anilist")) throw new Error("down");
      return {
        ok: true,
        status: 200,
        json: async () => ({ data: [{ mal_id: 1, name: "Rem", about: "x" }] }),
      };
    }) as unknown as typeof fetch;
    const hits = await searchCharacters("fallback-test", mixed);
    expect(hits[0]?.source).toBe("jikan");
    expect(calls.length).toBe(2);
    expect(await searchCharacters("both-fail", fakeFetch({}, false))).toEqual([]);
  });

  it("picks the character that matches the name and the work", () => {
    const hits = parseAniList(aniList);
    expect(pickBestHit(hits, "レム", "Re:ゼロ")?.id).toBe(88575);
    expect(pickBestHit(hits, "レム", "別の作品")).toBeUndefined();
    expect(pickBestHit(hits, "全然違う名前")).toBeUndefined();
  });

  it("reads the fields of an answer and the age as given", () => {
    const [hit] = parseAniList(aniList);
    const fill = parseFill(
      '```json\n{"name":"レム","gender":"女性","age":"17","hair":"水色","eyes":"青"}\n```',
      hit!,
    );
    expect(fill).toMatchObject({
      name: "レム",
      gender: "女性",
      hair: "水色",
      eyes: "青",
      age: "17",
    });
    expect(parseFill('{"age":"22"}', hit!).age).toBe("22");
    expect(parseFill("not json", hit!)).toMatchObject({ name: "レム", gender: "女性" });
  });
});

describe("anime-lookup titles", () => {
  it("reads the works of AniList and Jikan", async () => {
    const { parseAniListTitles, parseJikanTitles } = await import("./anime-lookup.js");
    expect(
      parseAniListTitles({
        data: {
          Page: {
            media: [
              {
                id: 21355,
                title: { native: "Re:ゼロから始める異世界生活", romaji: "Re:Zero" },
                format: "TV",
                seasonYear: 2016,
              },
            ],
          },
        },
      })[0],
    ).toMatchObject({ id: 21355, title: "Re:ゼロから始める異世界生活", year: 2016 });
    expect(
      parseJikanTitles({
        data: [
          { mal_id: 31240, title: "Re:Zero", title_japanese: "Re:ゼロ", type: "TV", year: 2016 },
        ],
      })[0],
    ).toMatchObject({ source: "jikan", id: 31240, title: "Re:ゼロ" });
  });

  it("searches each word alone when work name + character name finds nothing, and ranks by the work", async () => {
    const answers: Record<string, unknown> = {
      "レム Re:ゼロ": { data: { Page: { characters: [] } } },
      レム: aniList,
      "Re:ゼロ": { data: { Page: { characters: [] } } },
    };
    const fetchBySearch = (async (_url: string, init?: { body?: string }) => {
      const search = JSON.parse(init?.body ?? "{}").variables?.search as string;
      return {
        ok: true,
        status: 200,
        json: async () => answers[search] ?? { data: { Page: { characters: [] } } },
      };
    }) as unknown as typeof fetch;
    const hits = await searchCharacters("レム Re:ゼロ", fetchBySearch);
    expect(hits[0]?.id).toBe(88575);
  });
});

describe("anime-lookup multi-word", () => {
  it("drops fuzzy hits that match none of the words", async () => {
    const other = {
      data: {
        Page: {
          characters: [
            {
              id: 1,
              name: { full: "Satoru Gojo", native: "五条悟" },
              media: { nodes: [{ title: { native: "呪術廻戦" } }] },
            },
          ],
        },
      },
    };
    const answers: Record<string, unknown> = {
      "レム Re:ゼロ": other,
      レム: aniList,
      "Re:ゼロ": other,
    };
    const fetchBySearch = (async (_url: string, init?: { body?: string }) => {
      const search = JSON.parse(init?.body ?? "{}").variables?.search as string;
      return {
        ok: true,
        status: 200,
        json: async () => answers[search] ?? { data: { Page: { characters: [] } } },
      };
    }) as unknown as typeof fetch;
    const hits = await searchCharacters("レム Re:ゼロ ", fetchBySearch);
    expect(hits.map((hit) => hit.id)).toEqual([88575]);
  });
});
