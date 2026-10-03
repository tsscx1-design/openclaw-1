import { describe, expect, it, vi } from "vitest";
import { parseJsonObject } from "../../gateway/llm-json.js";
import { unwrapExternalText } from "../../gateway/partner-creation.js";
import type { ResearchIO } from "../../gateway/partner-research.js";
import {
  digestResearch,
  parseDialogue,
  pickSheetQuotes,
} from "../../gateway/partner-sheet-generator.js";
import {
  buildPartnerSheetMarkdown,
  parsePartnerSheet,
  PARTNER_SHEET_CATEGORIES,
} from "../../gateway/partner-sheet-schema.js";
import { createPartnerCreateTool } from "./partner-create-tool.js";

describe("partner sheet schema", () => {
  it("round-trips fixed fields and keeps free text", () => {
    const markdown = buildPartnerSheetMarkdown({
      name: "ラファエル",
      values: { 一人称: "私", 語尾の癖: "〜です", 独自項目: "解析鑑定" },
      freeText: "### セリフ例\n「告。解析が完了しました」",
    });
    const parsed = parsePartnerSheet(markdown);
    expect(parsed.attributes.get("一人称")).toBe("私");
    expect(parsed.attributes.get("独自項目")).toBe("解析鑑定");
    expect(parsed.attributes.has("種族")).toBe(false);
    expect(parsed.freeText).toContain("告。解析が完了しました");
    expect(parsed.freeText).toContain("### セリフ例");
    expect(parsed.freeText).not.toContain("詳細キャラクター設定");
  });

  it("covers the Android editor's 100 fields", () => {
    expect(PARTNER_SHEET_CATEGORIES.flatMap((c) => c.items)).toHaveLength(100);
  });
});

describe("parseJsonObject", () => {
  it("reads fenced JSON and salvages almost-JSON", () => {
    expect(parseJsonObject('はい\n```json\n{"一人称": "俺"}\n```')).toEqual({ 一人称: "俺" });
    expect(parseJsonObject('{"一人称": "俺", "語尾の癖": "だぜ",}')).toEqual({
      一人称: "俺",
      語尾の癖: "だぜ",
    });
  });
});

describe("research digest", () => {
  it("keeps only quotes that the page really contains", async () => {
    const digest = await digestResearch({
      bundle: {
        name: "あのちゃん",
        kind: "real_person",
        aliases: ["あのちゃん"],
        sources: [
          {
            url: "https://thetv.jp/news/1",
            title: "あの、交友を明かす",
            site: "thetv.jp",
            origin: "reference",
            text: "あのは「MVがいつもさ、僕一人じゃん？」と語った。",
            fullText: "あのは「MVがいつもさ、僕一人じゃん？」と語った。",
          },
        ],
        quotes: [],
        failedPages: 0,
      },
      complete: async () =>
        [
          "- [話し方] 一人称は「僕」",
          "- [発言] 「MVがいつもさ、僕一人じゃん？」",
          "- [発言] 「みんな大好きだよ！」",
          "- [不明なタグ] 無視される",
        ].join("\n"),
    });
    expect(digest.notes.話し方).toEqual(["一人称は「僕」"]);
    expect(digest.quotes.map((quote) => quote.text)).toEqual(["MVがいつもさ、僕一人じゃん？"]);
  });

  it("prefers short conversational quotes and spreads them over pages", () => {
    const quotes = [
      ...Array.from({ length: 8 }, (_, index) => ({
        text: `アルバムの制作は大変ではありましたが、充実していました（${index}）`,
        url: "https://natalie.mu/a",
        site: "natalie.mu",
      })),
      { text: "マジで喋れなかったんだよね", url: "https://thetv.jp/b", site: "thetv.jp" },
    ];
    const picked = pickSheetQuotes(quotes, 7);
    expect(picked[0]?.text).toBe("マジで喋れなかったんだよね");
    expect(picked.filter((quote) => quote.site === "natalie.mu")).toHaveLength(6);
  });

  it("reads sample dialogue in any speaker spelling", () => {
    expect(
      parseDialogue(
        "1. ユーザー: おはよう\nあの：「おはよ。眠い」\n説明文\n**ユーザー**: ご飯なに食べた？\nano: エビ。",
        "あのちゃん",
      ),
    ).toEqual([
      "ユーザー: おはよう",
      "あのちゃん: 「おはよ。眠い」",
      "ユーザー: ご飯なに食べた？",
      "あのちゃん: 「エビ。」",
    ]);
  });

  it("unwraps the untrusted-content envelope of fetched pages", () => {
    const wrapped =
      'SECURITY NOTICE\n\n<<<EXTERNAL_UNTRUSTED_CONTENT id="abc">>>\nSource: Web Fetch\n---\n本文です\n<<<END_EXTERNAL_UNTRUSTED_CONTENT id="abc">>>';
    expect(unwrapExternalText(wrapped)).toBe("本文です");
    expect(unwrapExternalText("そのままの文")).toBe("そのままの文");
  });
});

describe("partner_create_from_character", () => {
  it("researches several sites, writes the sheet with the subject's own words and saves it", async () => {
    const interview = [
      "──最近どうですか？",
      "うーん、ずっと曲作ってる。マジで寝てないんだけど、楽しいからいい。",
      "──人見知りは直りました？",
      "全然。僕、今でも初対面だと何も喋れないです。",
      "──好きな食べ物は？",
      "汁とエビ。あとお菓子があれば生きていける。",
      "──最後にファンの方へ。",
      "いつもありがとう。僕はこれからも好きなことしかやらないけど、ついてきてくれたらうれしいです。",
    ].join("\n");
    const io: ResearchIO = {
      fetchJson: async (url) => {
        const params = new URL(url).searchParams;
        if (params.get("list") === "search") {
          return { query: { search: [{ title: "あの" }] } };
        }
        if (params.get("action") === "parse") {
          return {
            parse: {
              text: '<a class="external text" href="https://natalie.mu/music/pp/ano01">ano インタビュー</a>',
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
                extract: `あのは、日本の歌手、タレント。愛称は、あのちゃん。\n== 人物 ==\n${"一人称は「ぼく」。人の歯を見ることが趣味。".repeat(4)}`,
              },
            ],
          },
        };
      },
      fetchPage: async (url) =>
        url.includes("natalie")
          ? { title: "anoインタビュー", text: `${interview}\n${"関連記事です。".repeat(40)}` }
          : undefined,
    };
    const complete = vi.fn(async ({ prompt }: { prompt: string }) => {
      if (prompt.includes("分類タグ")) {
        return "- [話し方] 一人称は「僕」、タメ口\n- [好き] 汁、エビ、お菓子\n- [性格] 人見知り";
      }
      if (prompt.includes('- "一人称"')) {
        return '{"一人称": "僕（ぼく）"}';
      }
      if (prompt.includes("### 人物像")) {
        return "### 人物像\n人見知りで正直なアーティスト。\n### 話し方の特徴\n- 一人称は僕\n### 演じる上での注意\n- 敬語にしない";
      }
      if (prompt.includes("会話の見本")) {
        return "ユーザー: おはよう\nあのちゃん: おはよ。眠すぎる";
      }
      return "{}";
    });
    const save = vi.fn(
      async (params: {
        name: string;
        persona: string;
        characterSheet: string;
        realPerson?: boolean;
      }) => ({
        id: "partner-x",
        name: params.name,
        avatarUrl: "",
        persona: params.persona,
        modelId: "",
        createdAt: 0,
        updatedAt: 0,
      }),
    );
    const callGateway = vi.fn(async () => ({}));
    const tool = createPartnerCreateTool({
      agentSessionKey: "agent:main:main",
      callGateway: callGateway as never,
      isMainRunActive: () => false,
      io,
      complete,
      save: save as never,
    });

    const result = await tool.execute("call-1", { name: "あのちゃん" });

    expect(JSON.stringify(result)).toContain("background_started");
    await vi.waitFor(() => expect(save).toHaveBeenCalled(), { timeout: 5_000 });
    const saved = save.mock.calls[0]?.[0];
    const sheet = parsePartnerSheet(saved?.characterSheet ?? "");
    expect(saved?.realPerson).toBe(true);
    expect(sheet.attributes.get("一人称")).toBe("僕（ぼく）");
    expect(sheet.freeText).toContain("### 本人の発言（資料から原文のまま）");
    expect(sheet.freeText).toContain("「僕、今でも初対面だと何も喋れないです。」（natalie.mu）");
    expect(sheet.freeText).toContain("あのちゃん: 「おはよ。眠すぎる」");
    expect(sheet.freeText).toContain("### 実在の人物について（必ず守る）");
    expect(sheet.freeText).toContain("https://natalie.mu/music/pp/ano01");
    expect(saved?.persona).toContain("人見知りで正直なアーティスト");
    await vi.waitFor(
      () =>
        expect(callGateway).toHaveBeenCalledWith(
          "chat.inject",
          expect.objectContaining({ message: expect.stringContaining("作成しました") }),
        ),
      { timeout: 5_000 },
    );
    const done = callGateway.mock.calls
      .map((call) => (call as unknown[])[1] as { message?: string })
      .find((params) => params.message?.includes("作成しました"));
    expect(done?.message).toContain("natalie.mu");
    expect(done?.message).toContain("性的な会話には応じず");
  });

  it("reports a next step instead of saving an empty character when nothing was found", async () => {
    const save = vi.fn();
    const callGateway = vi.fn(async () => ({}));
    const tool = createPartnerCreateTool({
      agentSessionKey: "agent:main:main",
      callGateway: callGateway as never,
      isMainRunActive: () => false,
      io: {
        fetchJson: async () => ({ query: { search: [] } }),
        fetchPage: async () => undefined,
      },
      complete: async () => "{}",
      save: save as never,
    });
    await tool.execute("call-2", { name: "存在しない人物" });
    await vi.waitFor(
      () =>
        expect(callGateway).toHaveBeenCalledWith(
          "chat.inject",
          expect.objectContaining({ message: expect.stringContaining("参考ページのURL") }),
        ),
      { timeout: 5_000 },
    );
    expect(save).not.toHaveBeenCalled();
  });
});
