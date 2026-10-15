import { describe, expect, it } from "vitest";
import { parseBishoge } from "./bishoge-db.js";

describe("bishoge-db", () => {
  it("reads the characters of the API", () => {
    const hits = parseBishoge({
      characters: [
        {
          id: 19657,
          name: "深田 亞矢",
          reading: "ふかだ あや",
          cv: "藤崎紗矢香",
          role: "ヒロイン",
          tagline: "主人公の初恋の相手",
          item_title: "初恋BLUE",
          item_maker: "LiLiM DARKNESS",
        },
        { id: "x", name: "" },
      ],
    });
    expect(hits).toHaveLength(1);
    expect(hits[0]).toMatchObject({
      source: "bishoge",
      id: 19657,
      name: "深田 亞矢",
      englishName: "ふかだ あや",
      works: ["初恋BLUE"],
      description: "主人公の初恋の相手",
    });
    expect(hits[0]?.extra).toBe("CV 藤崎紗矢香／ヒロイン／LiLiM DARKNESS");
  });
});
