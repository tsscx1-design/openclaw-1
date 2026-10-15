import { describe, expect, it } from "vitest";
import { readChatStream, visibleReplyText } from "./server-methods/partners.js";

function sse(lines: string[]): Response {
  const body = lines.map((line) => `data: ${line}\n\n`).join("") + "data: [DONE]\n\n";
  return new Response(new Blob([body]).stream(), { status: 200 });
}
const chunk = (content: string) => JSON.stringify({ choices: [{ delta: { content } }] });

describe("a streamed partner reply", () => {
  it("hides thinking, also while it is still open", () => {
    expect(visibleReplyText("<think>考え中")).toBe("");
    expect(visibleReplyText("<think>考えた</think>こんにちは")).toBe("こんにちは");
    expect(visibleReplyText("こんにちは。<think>")).toBe("こんにちは。");
    expect(visibleReplyText("</think>はい")).toBe("はい");
  });

  it("returns the whole raw text and reports the visible text so far, ending with all of it", async () => {
    const seen: string[] = [];
    const raw = await readChatStream(
      sse([
        chunk("<think>\n</think>"),
        chunk("おはよう！"),
        chunk("今日も"),
        chunk("いい天気だね。"),
      ]),
      (t) => seen.push(t),
    );
    expect(raw).toBe("<think>\n</think>おはよう！今日もいい天気だね。");
    expect(seen.at(-1)).toBe("おはよう！今日もいい天気だね。");
    expect(
      seen.every((t, i) => i === 0 || t.startsWith(seen[i - 1]!) || seen[i - 1]!.startsWith(t)),
    ).toBe(true);
  });

  it("skips lines it cannot read", async () => {
    const seen: string[] = [];
    const raw = await readChatStream(sse(["not json", chunk("やあ")]), (t) => seen.push(t));
    expect(raw).toBe("やあ");
    expect(seen).toEqual(["やあ"]);
  });
});
