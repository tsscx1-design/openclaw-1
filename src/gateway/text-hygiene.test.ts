import { describe, expect, it } from "vitest";
import { cleanGeneratedText, echoesEarlierLine, isFailedReplyText } from "./text-hygiene.js";

describe("cleanGeneratedText", () => {
  it("leaves normal replies untouched", () => {
    const text = "おはよう、マスター。今日はいい天気だね！散歩にでも行こうか？";
    expect(cleanGeneratedText(text)).toBe(text);
  });

  it("collapses a repetition loop from the model", () => {
    const loop = `シオンちゃん、その連呼、${"もうもうもうもう、".repeat(40)}`;
    const cleaned = cleanGeneratedText(loop);
    expect(cleaned.length).toBeLessThan(40);
    expect(cleaned.startsWith("シオンちゃん、その連呼、")).toBe(true);
  });

  it("collapses single-character floods and repeated sentences", () => {
    expect(cleanGeneratedText(`うわーっ${"！".repeat(30)}`).length).toBeLessThan(12);
    expect(cleanGeneratedText("そうだね。そうだね。そうだね。次に行こう。")).toBe(
      "そうだね。次に行こう。",
    );
  });

  it("strips a leading speaker prefix and bounds the length", () => {
    expect(cleanGeneratedText("[ルナ]: こんにちは")).toBe("こんにちは");
    const long = `${"あいうえおかきくけこ、".repeat(5)}。`.repeat(1) + "さしすせそ。".repeat(100);
    expect(cleanGeneratedText(long, 100).length).toBeLessThanOrEqual(101);
  });
});

describe("isFailedReplyText", () => {
  it("recognises both placeholder styles but not normal speech", () => {
    expect(isFailedReplyText("（ルナ (Luna)は考え中...）")).toBe(true);
    expect(
      isFailedReplyText("（ルナの返信を作れませんでした: LLM request failed: HTTP 500）"),
    ).toBe(true);
    expect(isFailedReplyText("（笑顔で頷いている）")).toBe(false);
    expect(isFailedReplyText("考え中...")).toBe(false);
  });
});

describe("echoesEarlierLine", () => {
  const earlier = [
    "ふふっ、シオンちゃん、その「答えが出たら困るから考えてる」って観察、さすがだね！",
  ];

  it("detects a verbatim copy of someone else's line", () => {
    expect(
      echoesEarlierLine(
        "ふふっ、シオンちゃん、その「答えが出たら困るから考えてる」って観察、さすがだね！ あと…",
        earlier,
      ),
    ).toBe(true);
  });

  it("accepts a different reply and ignores very short ones", () => {
    expect(echoesEarlierLine("へえ、そうなんだ。私はお茶でも淹れてくるね。", earlier)).toBe(false);
    expect(echoesEarlierLine("うん", earlier)).toBe(false);
  });
});
