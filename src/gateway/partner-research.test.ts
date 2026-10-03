import { describe, expect, it } from "vitest";
import {
  extractInterview,
  isVerbatimIn,
  kindFromCategories,
  parseReferenceLinks,
  pickSubjectTitle,
  rankCandidates,
  researchPartnerSubject,
  scoreCandidate,
  selectWikipediaSections,
  splitSpokenSentences,
  type ResearchIO,
} from "./partner-research.js";

const INTERVIEW = `# anoインタビュー
取材・文 / 編集部
──アルバム完成おめでとうございます。
ありがとうございます。寝る時間を削ったりしながら作ってました。
──達成感はありますか？
ありますね、やっぱり。胸を張って「このアルバム聴いてほしいな」って言えます。
──バンドについては？
自分で限界を決めてるなっていうのがすごく嫌で、「いや、もっとできるんじゃない？」みたいな気持ちになってった。
## プロフィール
ano 9月4日生まれ。シンガーソングライター。`;

describe("interview extraction", () => {
  it("keeps the answers and stops at the profile block", () => {
    const result = extractInterview(INTERVIEW, ["あのちゃん", "ano"]);
    expect(result?.answers).toHaveLength(3);
    expect(result?.answers[0]).toContain("寝る時間を削ったり");
    expect(result?.answers.join("")).not.toContain("9月4日生まれ");
    expect(result?.pairs[1]).toMatch(/^──達成感はありますか？\n/);
  });

  it("keeps only the subject's lines on a dialogue page", () => {
    const dialogue = [
      "ano　僕はずっと人見知りだったんだよね。",
      "幾田　そうなんだ、意外！",
      "ano　マジで喋れなかったの。今も緊張するし。",
      "幾田　でも今日は楽しそう。",
      "ano　うん、楽しい。ありがとね。",
      "幾田　こちらこそ。",
    ].join("\n");
    const result = extractInterview(dialogue, ["ano"]);
    expect(result?.answers).toEqual([
      "僕はずっと人見知りだったんだよね。",
      "マジで喋れなかったの。今も緊張するし。",
      "うん、楽しい。ありがとね。",
    ]);
  });

  it("ignores pages without questions or the subject's label", () => {
    expect(extractInterview("ふつうのニュース記事です。\n――――\n本文です。", ["ano"])).toBe(
      undefined,
    );
  });

  it("splits sentences outside quotation marks only", () => {
    expect(
      splitSpokenSentences(
        "自分で限界を決めてるなっていうのがすごく嫌で、「いや、もっとできるんじゃない？」みたいな気持ちになってった。うん。",
      ),
    ).toEqual([
      "自分で限界を決めてるなっていうのがすごく嫌で、「いや、もっとできるんじゃない？」みたいな気持ちになってった。",
    ]);
  });
});

describe("Wikipedia helpers", () => {
  it("picks the subject's own article, also for a nickname", () => {
    expect(pickSubjectTitle("あのちゃん", ["あの", "ちびまる子ちゃん"])).toBe("あの");
    expect(pickSubjectTitle("橋本環奈", ["インフルエンス", "橋本環奈 (女優)"])).toBe(
      "橋本環奈 (女優)",
    );
    expect(pickSubjectTitle("竈門炭治郎", ["鬼滅の刃", "花江夏樹"])).toBeUndefined();
  });

  it("tells real people from characters by category", () => {
    expect(kindFromCategories(["Category:日本の女性歌手", "Category:存命人物"])).toBe(
      "real_person",
    );
    expect(kindFromCategories(["Category:1990年生"])).toBe("real_person");
    expect(kindFromCategories(["Category:架空の剣士"])).toBe("fictional");
    expect(kindFromCategories(["Category:日本の漫画"])).toBe("unknown");
  });

  it("keeps persona sections and drops works lists and controversies", () => {
    const extract = [
      "あのは、日本の歌手。",
      "== 来歴 ==",
      "2013年にデビュー。",
      "=== 不祥事 ===",
      "番組を降板した。",
      "== 人物 ==",
      "一人称は「ぼく」。",
      "=== 趣味・嗜好 ===",
      "好きな食べ物は汁。",
      "== 出演 ==",
      "=== テレビ ===",
      "あのちゃんねる",
    ].join("\n");
    const kept = selectWikipediaSections(extract, 5_000);
    expect(kept).toContain("一人称は「ぼく」");
    expect(kept).toContain("好きな食べ物は汁");
    expect(kept).toContain("2013年にデビュー");
    expect(kept).not.toContain("降板");
    expect(kept).not.toContain("あのちゃんねる");
  });

  it("reads cited links with their titles", () => {
    const html =
      '<li><a rel="nofollow" class="external text" href="https://natalie.mu/music/pp/ano05">anoインタビュー &amp; 特集</a></li>' +
      '<a href="/wiki/x" title="x">内部</a>' +
      '<a class="external free" href="https://example.com/a?x=1&amp;y=2">https://example.com/a</a>';
    expect(parseReferenceLinks(html)).toEqual([
      { title: "anoインタビュー & 特集", url: "https://natalie.mu/music/pp/ano05" },
      { title: "https://example.com/a", url: "https://example.com/a?x=1&y=2" },
    ]);
  });
});

describe("candidate ranking", () => {
  it("prefers interviews, drops sites without text and limits pages per site", () => {
    const hit = (url: string, title: string) => ({
      url,
      title,
      origin: "reference" as const,
      score: scoreCandidate({ url, title }, "reference"),
    });
    const ranked = rankCandidates(
      [
        hit("https://www.youtube.com/watch?v=1", "MV"),
        hit("https://news.example.jp/1", "新曲リリース"),
        hit("https://natalie.mu/music/pp/ano05", "anoインタビュー"),
        hit("https://www.sponichi.co.jp/a", "あのちゃん告白"),
        hit("https://www.sponichi.co.jp/b", "あのちゃん本音"),
        hit("https://www.sponichi.co.jp/c", "あのちゃん激白"),
        hit("https://www.sponichi.co.jp/d", "あのちゃん語る"),
      ],
      10,
    );
    expect(ranked[0]?.url).toBe("https://natalie.mu/music/pp/ano05");
    expect(ranked.some((candidate) => candidate.url.includes("youtube"))).toBe(false);
    expect(ranked.filter((candidate) => candidate.url.includes("sponichi"))).toHaveLength(3);
    expect(ranked.at(-1)?.url).toBe("https://news.example.jp/1");
  });
});

describe("researchPartnerSubject", () => {
  it("reads Wikipedia and the interviews it cites, keeping the subject's own words", async () => {
    const fetched: string[] = [];
    const io: ResearchIO = {
      fetchJson: async (url) => {
        const params = new URL(url).searchParams;
        if (params.get("list") === "search") {
          return { query: { search: [{ title: "あの" }, { title: "ちびまる子ちゃん" }] } };
        }
        if (params.get("action") === "parse") {
          return {
            parse: {
              text: '<a class="external text" href="https://natalie.mu/music/pp/ano05">ano ロングインタビュー</a><a class="external text" href="https://www.youtube.com/watch?v=x">MV</a>',
            },
          };
        }
        return {
          query: {
            pages: [
              {
                title: "あの",
                fullurl: "https://ja.wikipedia.org/wiki/あの",
                categories: [{ title: "Category:存命人物" }],
                extract:
                  "あのは、日本の歌手。2020年からはano名義で活動。愛称は、あのちゃん。\n== 人物 ==\n一人称は「ぼく」。好きな食べ物は汁、エビ、お菓子。人の歯を見ることが趣味。".repeat(
                    2,
                  ),
              },
            ],
          },
        };
      },
      fetchPage: async (url) => {
        fetched.push(url);
        const related = "関連記事の見出しです。".repeat(30);
        return url.includes("natalie")
          ? { title: "anoインタビュー", text: `${INTERVIEW}\n${related}` }
          : undefined;
      },
    };
    const bundle = await researchPartnerSubject({ name: "あのちゃん", io });
    expect(bundle.kind).toBe("real_person");
    expect(bundle.aliases).toEqual(expect.arrayContaining(["あのちゃん", "あの", "ano"]));
    expect(bundle.sources.map((source) => source.origin)).toEqual(["wikipedia", "reference"]);
    expect(fetched.some((url) => url.includes("youtube"))).toBe(false);
    expect(bundle.quotes.map((quote) => quote.text)).toContain(
      "寝る時間を削ったりしながら作ってました。",
    );
    expect(bundle.quotes.every((quote) => quote.site === "natalie.mu")).toBe(true);
  });

  it("checks quotes against the page text", () => {
    expect(isVerbatimIn("僕は人見知り", ["実は　僕は 人見知り だった"])).toBe(true);
    expect(isVerbatimIn("僕は社交的", ["実は僕は人見知りだった"])).toBe(false);
  });
});
