import { describe, expect, it } from "vitest";
import { parseResearch, researchPrompt } from "./idol-research.js";

describe("researching a person by name", () => {
  it("writes the instruction from the texts only, and says to leave out what the texts do not say", () => {
    const prompt = researchPrompt({
      name: "新垣結衣",
      wikipedia: {
        title: "新垣結衣",
        url: "https://ja.wikipedia.org/wiki/x",
        text: "沖縄県出身の女優。",
      },
      works: ["恋空", "逃げるは恥だが役に立つ"],
      sources: [],
    });
    expect(prompt).toContain("資料に書かれていない項目は、空の文字列");
    expect(prompt).toContain("【ウィキペディア】\n沖縄県出身の女優。");
    expect(prompt).toContain("恋空、逃げるは恥だが役に立つ");
    expect(prompt).not.toContain("【ウィキクォート");
  });

  it("reads the fields of the answer, cuts them to their length, and gives empty fields for a bad answer", () => {
    const research = parseResearch(
      'はい。{"intro":"紹介文","career":"経歴","works":"A、B","speech":"明るい口調","voice":"高めで柔らかい声"}',
    );
    expect(research).toEqual({
      intro: "紹介文",
      career: "経歴",
      works: "A、B",
      speech: "明るい口調",
      voice: "高めで柔らかい声",
    });
    expect(parseResearch('{"intro":"' + "あ".repeat(900) + '"}').intro.length).toBe(600);
    expect(parseResearch("JSONではない答え")).toEqual({
      intro: "",
      career: "",
      works: "",
      speech: "",
      voice: "",
    });
    expect(parseResearch("{broken")).toEqual({
      intro: "",
      career: "",
      works: "",
      speech: "",
      voice: "",
    });
  });
});
