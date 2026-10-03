import { describe, expect, it } from "vitest";
import { analyzeSpeech, isUsableSheetValue } from "./partner-sheet-generator.js";

describe("analyzeSpeech", () => {
  it("counts the first person and usual endings from the subject's own words", () => {
    const facts = analyzeSpeech({
      quotes: [
        "MVがいつもさ、僕一人じゃん？でも今回ドラマ仕立てで出演者がいるよっていう。うれしいって感じ！",
        "僕そのとき、度を超えた人見知りだったのね、マジで喋れなくて",
        "岡山天音くん今、もう超忙しいと思うんだけど",
        "僕は感情を吐き出す手段が音楽しかないので、曲を作ると自分自身も救われる",
        "でも音楽はやりたいので、寝る時間を削ったりしながら作ってました",
        "マジでびっくりしちゃって。めっちゃ怖かったんだけど",
        "めっちゃうれしかったけど、どうしよう…っていう不安の方が大きくて",
      ],
      reference: "本名と生年は非公開で、一人称は「ぼく」。",
    });
    expect(facts.firstPerson).toBe("僕（ぼく）");
    expect(facts.summary.join("\n")).toContain("「〜んだけど」(2)");
    expect(facts.summary.join("\n")).toContain("「マジで」(2)");
    expect(facts.summary.join("\n")).toContain("「めっちゃ」(2)");
    expect(facts.summary.join("\n")).toMatch(/丁寧語（です・ます）の割合: 約\d+%/);
  });

  it("does not take a pronoun from another person's quoted words", () => {
    const facts = analyzeSpeech({
      quotes: ["先輩に「俺は帰る」って言われた", "私はね、残ったの", "私も行く"],
    });
    expect(facts.firstPerson).toBe("私");
  });
});

describe("isUsableSheetValue", () => {
  it("drops no-data notes and copies of the field hint", () => {
    expect(isUsableSheetValue("資料に書かれていない")).toBe(false);
    expect(isUsableSheetValue("不明")).toBe(false);
    expect(isUsableSheetValue("好きな季節がある。（具体的な名前は不明）")).toBe(false);
    expect(isUsableSheetValue("（特になし）")).toBe(false);
    expect(
      isUsableSheetValue("小さな努力を見逃さない、結果が出た時だけ絶賛", {
        hint: "小さな努力を見逃さない、結果が出た時だけ絶賛など",
      }),
    ).toBe(false);
    // A short canon value that also appears among the examples is still a fact.
    expect(
      isUsableSheetValue("マスター", { hint: "マスター、〇〇さん、お前、キミ、先輩など" }),
    ).toBe(true);
  });

  it("keeps a real person's private life and health out of the sheet", () => {
    expect(isUsableSheetValue("兄の職業は学校の先生", { kind: "real_person" })).toBe(false);
    expect(isUsableSheetValue("身長166cm・体重44.4kg", { kind: "real_person" })).toBe(false);
    expect(isUsableSheetValue("好きな食べ物は汁、エビ、お菓子", { kind: "real_person" })).toBe(
      true,
    );
    expect(isUsableSheetValue("苦手な食べ物は親子丼", { kind: "real_person" })).toBe(true);
    expect(isUsableSheetValue("兄を守るために戦う", { kind: "fictional" })).toBe(true);
  });
});
