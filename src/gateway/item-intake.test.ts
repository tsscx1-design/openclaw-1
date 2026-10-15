import { describe, expect, it } from "vitest";
import { parseJudged, splitWords } from "./item-intake.js";

describe("item-intake", () => {
  it("splits at 、 / and new lines without repeats", () => {
    expect(splitWords("ナイトプール、水面の照明 / ラッシュガード\nナイトプール")).toEqual([
      "ナイトプール",
      "水面の照明",
      "ラッシュガード",
    ]);
  });

  it("keeps a genre of the catalog and falls back for others", () => {
    const rows = parseJudged(
      '[{"label":"ナイトプール","genre":"背景・場所","instruction":"夜のプール","known":true},{"label":"x","genre":"でたらめ","instruction":"","known":true}]',
      ["背景・場所"],
    );
    expect(rows[0]).toMatchObject({ genre: "背景・場所", known: true });
    expect(rows[1]).toMatchObject({ genre: "自分で追加", known: false });
  });

  it("keeps the Japanese explanation of a word and ignores an empty one", () => {
    const rows = parseJudged(
      '[{"label":"ナイトプール","genre":"背景・場所","instruction":"night pool","ja":"夜に照明で彩られたプール。","known":true},{"label":"y","genre":"背景・場所","instruction":"y","ja":"  ","known":true}]',
      ["背景・場所"],
    );
    expect(rows[0]?.ja).toBe("夜に照明で彩られたプール。");
    expect(rows[1]).not.toHaveProperty("ja");
  });

  it("reads a broken answer: no array brackets, wrapped in braces and a code block", () => {
    const broken =
      '```json\n{\n  {"label":"星空の下のハンモック","genre":"背景・場所","instruction":"満天の星空の下のハンモック","known":true}\n  {"label":"露天風呂の湯けむり","genre":"背景・場所","instruction":"立ちのぼる湯気","known":true}\n}\n```';
    const rows = parseJudged(broken, ["背景・場所"]);
    expect(rows.map((row) => row.label)).toEqual(["星空の下のハンモック", "露天風呂の湯けむり"]);
    expect(rows.every((row) => row.genre === "背景・場所" && row.known)).toBe(true);
  });
});
