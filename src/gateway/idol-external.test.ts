import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  externalListable,
  externalRosterLine,
  listExternalPeople,
  personFromAniList,
  personFromMusicBrainz,
  personFromTmdb,
  tmdbRosterLine,
} from "./idol-external.js";

const NOW = new Date("2026-10-13T00:00:00Z");
const KEY = "0123456789abcdef0123456789abcdef";

describe("people of the outside sources", () => {
  it("reads a TMDB person: Japanese name, birthday, birthplace, gender, pictures, adult flag", () => {
    const person = personFromTmdb(
      {
        id: 231364,
        name: " 新垣結衣 ",
        gender: 1,
        birthday: "1988-06-11",
        place_of_birth: "Naha, Okinawa, Japan",
        profile_path: "/abc.jpg",
        known_for_department: "Acting",
        known_for: [{ title: "恋空" }, { name: "逃げ恥" }],
        adult: false,
      },
      NOW,
    )!;
    expect(person).toMatchObject({
      id: 231364,
      name: "新垣結衣",
      gender: "female",
      birthday: "1988-06-11",
      age: 38,
      knownFor: ["恋空", "逃げ恥"],
    });
    expect(person.thumbUrl).toBe("https://image.tmdb.org/t/p/w185/abc.jpg");
    expect(person.photoUrl).toBe("https://image.tmdb.org/t/p/w500/abc.jpg");
    expect(person.adult).toBeUndefined();
    expect(personFromTmdb({ id: 1, name: "X", adult: true })?.adult).toBe(true);
    expect(personFromTmdb({ id: 1, name: "X", profile_path: "/../../evil" })?.photoUrl).toBe("");
    expect(personFromTmdb({ name: "no id" })).toBeUndefined();
  });

  it("writes the roster line with the source", () => {
    const person = personFromTmdb(
      {
        id: 5,
        name: "山田 | 花子",
        gender: 1,
        birthday: "1999-01-02",
        place_of_birth: "Tokyo",
        profile_path: "/p.jpg",
        known_for_department: "Acting",
        known_for: [{ title: "映画A" }],
      },
      NOW,
    )!;
    expect(tmdbRosterLine(person, "おすすめ 俳優")).toBe(
      "おすすめ 俳優 | 山田 花子 | 映画A | 俳優 | 誕生日=1999-01-02 | 出身地=Tokyo | 写真=https://image.tmdb.org/t/p/w500/p.jpg | 出典=TMDB 5",
    );
  });

  it("reads a MusicBrainz person with the Japanese alias and a full birth date only", () => {
    const person = personFromMusicBrainz(
      {
        id: "11111111-2222-3333-4444-555555555555",
        name: "Yui Aragaki",
        type: "Person",
        gender: "Female",
        "life-span": { begin: "1988-06-11" },
        area: { name: "沖縄" },
        aliases: [{ name: "新垣結衣", locale: "ja" }],
        tags: [{ name: "j-pop", count: 3 }],
      },
      NOW,
    )!;
    expect(person).toMatchObject({
      source: "musicbrainz",
      name: "新垣結衣",
      gender: "female",
      birthday: "1988-06-11",
      place: "沖縄",
    });
    expect(
      personFromMusicBrainz(
        {
          id: "11111111-2222-3333-4444-555555555555",
          name: "Someone",
          "life-span": { begin: "1990" },
        },
        NOW,
      )?.birthday,
    ).toBeUndefined();
  });

  it("reads an AniList staff: native name, birthday only when year, month and day are known, pictures only from AniList", () => {
    const staff = personFromAniList(
      {
        id: 95001,
        name: { full: "Yui Ishikawa", native: "石川由依" },
        image: { large: "https://s4.anilist.co/file/a.jpg" },
        gender: "Female",
        dateOfBirth: { year: 1989, month: 5, day: 30 },
        homeTown: "東京都",
        primaryOccupations: ["Voice Actor"],
      },
      NOW,
    )!;
    expect(staff).toMatchObject({
      name: "石川由依",
      birthday: "1989-05-30",
      age: 37,
      profession: "声優",
      photoUrl: "https://s4.anilist.co/file/a.jpg",
    });
    expect(
      personFromAniList(
        {
          id: 1,
          name: { native: "名前" },
          dateOfBirth: { year: 1990, month: null, day: null },
          image: { large: "https://evil.example/a.jpg" },
        },
        NOW,
      ),
    ).toMatchObject({ photoUrl: "" });
    expect(
      personFromAniList(
        { id: 1, name: { native: "名前" }, dateOfBirth: { year: 1990, month: null, day: null } },
        NOW,
      )?.birthday,
    ).toBeUndefined();
  });

  it("lists a person marked adult unless the birthday shows under 18 (an unknown birthday is listed)", () => {
    expect(externalListable({})).toBe(true);
    expect(externalListable({ adult: true })).toBe(true);
    expect(externalListable({ adult: true, age: 17 })).toBe(false);
    expect(externalListable({ adult: true, age: 18 })).toBe(true);
    expect(
      externalRosterLine(
        {
          source: "anilist",
          id: "9",
          name: "A",
          gender: "other",
          thumbUrl: "",
          photoUrl: "",
          note: "",
        },
        "G",
      ),
    ).toBe("G | A |  |  | 出典=AniList 9");
  });
});

describe("TMDB through the gateway", () => {
  const original = process.env.TMDB_API_KEY;
  const seen: string[] = [];
  beforeEach(() => {
    seen.length = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        seen.push(String(url));
        const u = new URL(String(url));
        if (u.pathname === "/3/search/person") {
          return new Response(
            JSON.stringify({
              results: [
                {
                  id: 1,
                  name: "女性A",
                  gender: 1,
                  adult: false,
                  profile_path: "/a.jpg",
                  known_for: [],
                },
                { id: 2, name: "男性B", gender: 2, adult: false, known_for: [] },
                { id: 3, name: "成人C", gender: 1, adult: true, known_for: [] },
                { id: 4, name: "成人D", gender: 1, adult: true, known_for: [] },
              ],
            }),
            { status: 200 },
          );
        }
        const id = Number(u.pathname.split("/").pop());
        const birthdays: Record<number, string> = {
          1: "2000-01-01",
          2: "1990-02-02",
          3: "1995-03-03",
          4: "2010-04-04",
        };
        return new Response(
          JSON.stringify({
            id,
            name: `人${id}`,
            gender: 1,
            birthday: birthdays[id],
            adult: id >= 3,
          }),
          { status: 200 },
        );
      }),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    if (original === undefined) delete process.env.TMDB_API_KEY;
    else process.env.TMDB_API_KEY = original;
  });

  it("does not run without a key and never puts the key in an error", async () => {
    delete process.env.TMDB_API_KEY;
    await expect(listExternalPeople({ source: "tmdb", query: "x" })).rejects.toThrow(
      "TMDB_API_KEY",
    );
    process.env.TMDB_API_KEY = KEY;
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error(`failed ${KEY}`);
      }),
    );
    await expect(listExternalPeople({ source: "tmdb", query: "x" })).rejects.toThrow(
      "TMDB につながりませんでした",
    );
    await expect(listExternalPeople({ source: "tmdb", query: "x" })).rejects.not.toThrow(KEY);
  });

  it("asks for adult entries only when the switch is on, and keeps an adult-flagged person only when 18 or more", async () => {
    process.env.TMDB_API_KEY = KEY;
    const off = await listExternalPeople({ source: "tmdb", query: "x", adult: false });
    expect(seen.some((url) => url.includes("include_adult=false"))).toBe(true);
    expect(off.map((one) => one.name)).toContain("女性A");
    seen.length = 0;
    const on = await listExternalPeople({ source: "tmdb", query: "x", adult: true });
    expect(seen.some((url) => url.includes("include_adult=true"))).toBe(true);
    // 成人C is 31 (kept), 成人D is 16 (dropped), the others are not marked adult
    expect(on.some((one) => one.adult && one.age === 16)).toBe(false);
    expect(on.some((one) => one.adult && (one.age ?? 0) >= 18)).toBe(true);
  });

  it("applies the gender and age filters", async () => {
    process.env.TMDB_API_KEY = KEY;
    const women = await listExternalPeople({
      source: "tmdb",
      query: "x",
      gender: "female",
      ageMin: 18,
      ageMax: 30,
    });
    expect(
      women.every(
        (one) => one.gender === "female" && (one.age ?? 0) >= 18 && (one.age ?? 99) <= 30,
      ),
    ).toBe(true);
  });
});
