import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let home = "";
vi.mock("./partner-store.js", () => ({
  getPartnerDir: (id: string) => path.join(home, "partners", id),
}));

const {
  addKnowledge,
  deleteKnowledge,
  getKnowledgeText,
  htmlToText,
  joinPassages,
  knowledgeNoteFor,
  listKnowledge,
  normalizeDocText,
  rankPassages,
  searchKnowledge,
  splitPassages,
  termsOf,
  updateKnowledge,
} = await import("./partner-knowledge.js");

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "knowledge-"));
});
afterEach(async () => {
  await fs.rm(home, { recursive: true, force: true });
});

describe("splitPassages", () => {
  it("cuts long text at sentence ends with a small overlap", () => {
    const text = Array.from({ length: 120 }, (_, i) => `これは${i}番目の文です。`).join("");
    const passages = splitPassages(text);
    expect(passages.length).toBeGreaterThan(1);
    for (const p of passages) expect(p.length).toBeLessThanOrEqual(480);
    expect(passages[0]?.endsWith("。")).toBe(true);
  });
});

describe("normalizeDocText", () => {
  it("joins Japanese characters that a PDF spaced apart", () => {
    expect(normalizeDocText("設 計 年 月 日 abc def\n\n\n\n次")).toBe("設計年月日 abc def\n\n次");
  });
});

describe("termsOf", () => {
  it("keeps kanji/katakana bigrams and ASCII words, skips hiragana-only pairs", () => {
    const terms = termsOf("猫の飼い方を教えて GPU");
    expect(terms).toContain("飼い");
    expect(terms).toContain("gpu");
    expect(terms).not.toContain("えて");
  });
});

describe("rankPassages", () => {
  it("ranks the passage about the asked topic first and drops unrelated ones", () => {
    const doc = {
      id: "d1",
      title: "取扱説明書",
      kind: "text" as const,
      source: "",
      chars: 0,
      passages: 0,
      addedAt: 0,
    };
    const hits = rankPassages(
      "電池の交換方法は？",
      [
        {
          doc,
          passages: [
            "本体の電源ボタンを長押しすると起動します。",
            "電池の交換は、裏ぶたを外して単三電池2本を入れ替えます。",
            "保証期間は購入日から1年間です。",
          ],
        },
      ],
      3,
    );
    expect(hits[0]?.text).toContain("電池の交換");
    expect(hits.some((h) => h.text.includes("保証期間"))).toBe(false);
  });
});

describe("shelf storage", () => {
  it("adds, searches, replaces the same source and deletes", async () => {
    const doc = await addKnowledge("p1", {
      title: "ルナの設定資料",
      kind: "text",
      source: "memo-1",
      text: "ルナの好きな食べ物はいちごのショートケーキ。苦手なものはピーマン。誕生日は3月14日。",
    });
    expect((await listKnowledge("p1")).map((d) => d.id)).toEqual([doc.id]);

    const hits = await searchKnowledge("p1", "ルナの好きな食べ物は？");
    expect(hits[0]?.text).toContain("ショートケーキ");
    const note = await knowledgeNoteFor("p1", "好きな食べ物って何だっけ");
    expect(note).toContain("ショートケーキ");
    expect(await knowledgeNoteFor("p1", "明日の天気")).toBe("");

    const again = await addKnowledge("p1", {
      title: "ルナの設定資料 v2",
      kind: "text",
      source: "memo-1",
      text: "ルナの好きな食べ物はチョコレートパフェ。苦手なものはセロリ。",
    });
    expect((await listKnowledge("p1")).map((d) => d.id)).toEqual([again.id]);

    expect((await getKnowledgeText("p1", again.id))?.text).toContain("チョコレートパフェ");
    const edited = await updateKnowledge("p1", again.id, {
      title: "ルナの設定資料 v3",
      text: "ルナの好きな食べ物はモンブラン。苦手なものはセロリとピーマン。",
    });
    expect(edited?.title).toBe("ルナの設定資料 v3");
    expect((await getKnowledgeText("p1", again.id))?.text).toContain("モンブラン");
    expect((await searchKnowledge("p1", "好きな食べ物"))[0]?.text).toContain("モンブラン");
    await expect(updateKnowledge("p1", again.id, { text: "短い" })).rejects.toThrow();
    expect(await updateKnowledge("p1", "kn-missing", { title: "x" })).toBeUndefined();

    expect(await deleteKnowledge("p1", again.id)).toBe(true);
    expect(await listKnowledge("p1")).toEqual([]);
  });

  it("rebuilds the text of older documents from their passages", () => {
    const text = "あいうえおかきくけこ。".repeat(120);
    expect(joinPassages(splitPassages(text))).toBe(text);
  });

  it("refuses documents with almost no text", async () => {
    await expect(
      addKnowledge("p1", { title: "x", kind: "pdf", source: "a.pdf", text: "  " }),
    ).rejects.toThrow();
  });
});

describe("htmlToText", () => {
  it("drops scripts and navigation and keeps the title", () => {
    const { text, title } = htmlToText(
      "<html><head><title>記事 &amp; 解説</title><script>var x=1</script></head><body><nav>メニュー</nav><p>本文です。</p><p>二段落目。</p></body></html>",
    );
    expect(title).toBe("記事 & 解説");
    expect(text).toContain("本文です。");
    expect(text).not.toContain("メニュー");
    expect(text).not.toContain("var x");
  });
});
