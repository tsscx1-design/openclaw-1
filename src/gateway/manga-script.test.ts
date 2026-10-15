import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { installBlockedTerms } from "./blocked-terms-test-support.js";
import {
  bookOutlinePrompt,
  bookPagePrompt,
  mangaScriptSystemPrompt,
  mergeBookCast,
  parseBookOutline,
  parseBookPage,
  parseMangaScript,
} from "./manga-script.js";

let restoreBlockedTerms: () => void = () => undefined;
beforeAll(() => {
  restoreBlockedTerms = installBlockedTerms();
});
afterAll(() => {
  restoreBlockedTerms();
});

const reply = `はい、脚本です。
\`\`\`json
{"title":"いつもの朝","cast":[{"name":"ルナ","look":"1girl, short black hair, blue inner hair, white hoodie","profile":"明るい"},{"name":"ソラ","look":"1boy, brown hair, glasses","profile":"真面目"}],
"panels":[
 {"characters":[{"name":"ルナ","pos":"center","action":"waving, smile"}],"scene":"bedroom, morning light","lines":[{"who":"ルナ","text":"おはよう！"}],"narration":"ある晴れた朝"},
 {"characters":[{"name":"ソラ","pos":"left","action":"surprised"},{"name":"ルナ","pos":"right","action":"grin"}],"scene":"living room","lines":[{"who":"ソラ","text":"えっ、もう起きてるの？"},{"who":"ルナ","text":"ふふん。"}]},
 {"characters":[],"scene":"kitchen, burnt toast, smoke","lines":[]},
 {"characters":[{"name":"ルナ","pos":"left","action":"laughing"},{"name":"ソラ","pos":"right","action":"laughing"}],"scene":"table","lines":[{"who":"ルナ","text":"パン、焦げちゃった。\\n"}],"narration":""}
]}
\`\`\``;

describe("parseMangaScript", () => {
  it("reads the fenced JSON with who stands where, and keeps the dialogue", () => {
    const script = parseMangaScript(reply, "4koma");
    expect(script.title).toBe("いつもの朝");
    expect(script.cast.map((c) => c.name)).toEqual(["ルナ", "ソラ"]);
    expect(script.panels).toHaveLength(4);
    expect(script.panels[1]?.characters.map((c) => c.pos)).toEqual(["left", "right"]);
    expect(script.panels[0]?.narration).toBe("ある晴れた朝");
    expect(script.panels[3]?.lines[0]?.text).toBe("パン、焦げちゃった。");
    expect(script.panels[3]?.narration).toBeUndefined();
  });

  it("fails when a page lacks panels", () => {
    expect(() => parseMangaScript(reply, "page")).toThrow(/コマが足りません/);
  });
});

const outlineReply = `{"title":"雨の日","cast":[{"name":"ルナ","look":"1girl, short black hair","profile":"明るい"}],"pages":["雨宿り","出会い","告白"]}`;

const pageReply = `{"panels":[
 {"size":"large","characters":[{"name":"ルナ","pos":"center","action":"surprised"}],"scene":"close-up, rain","lines":[{"who":"ルナ","kind":"shout","text":"えっ！？"},{"who":"ルナ","kind":"nonsense","text":"うそ…"}],"sfx":["ザァァ","ドン","ガタッ"],"effect":"focus"},
 {"size":"large","characters":[],"scene":"wide shot, street","lines":[{"who":"ルナ","kind":"think","text":"どうしよう"}],"effect":"zoom"}
]}`;

describe("multi-page manga script", () => {
  it("reads the outline and fills a short outline up to the page count", () => {
    const outline = parseBookOutline(outlineReply, 4);
    expect(outline.title).toBe("雨の日");
    expect(outline.pages).toHaveLength(4);
    expect(outline.cast[0]?.name).toBe("ルナ");
    expect(() => parseBookOutline(outlineReply, 8)).toThrow(/ページの流れが足りません/);
  });

  it("keeps bubble kinds, sound effects and effects, and one highlight per page", () => {
    const panels = parseBookPage(pageReply);
    expect(panels).toHaveLength(2);
    expect(panels[0]?.kinds).toEqual(["shout", "say"]);
    expect(panels[0]?.sfx).toEqual(["ザァァ", "ドン"]);
    expect(panels[0]?.effect).toBe("focus");
    expect(panels[1]?.effect).toBe("none");
    expect(panels.map((p) => p.size)).toEqual(["large", "normal"]);
  });

  it("writes a sequel from the earlier cast and keeps those characters as they were", () => {
    const earlier = [{ name: "ルナ", look: "1girl, short black hair", profile: "明るい" }];
    const prompt = bookOutlinePrompt(4, false, {
      cast: earlier,
      previously: "- 第1巻「雨の日」: 出会い",
      volume: 1,
    });
    expect(prompt).toContain("第2巻（続編）");
    expect(prompt).toContain("- 第1巻「雨の日」: 出会い");
    expect(prompt).toContain("ルナ: 明るい");
    const merged = mergeBookCast(
      [
        { name: "ルナ", look: "1girl, long red hair", profile: "別人" },
        { name: "ソラ", look: "1boy, glasses", profile: "真面目" },
      ],
      earlier,
    );
    expect(merged.map((c) => c.name)).toEqual(["ルナ", "ソラ"]);
    expect(merged[0]?.look).toBe("1girl, short black hair");
    expect(
      bookPagePrompt({ ...parseBookOutline(outlineReply, 3), previously: "出会った" }, 1, 3, ""),
    ).toContain("これまでの話");
  });

  it("asks for the page count and the extra fields", () => {
    expect(bookOutlinePrompt(8)).toContain("ちょうど8個");
    const outline = parseBookOutline(outlineReply, 3);
    const prompt = bookPagePrompt(outline, 3, 3, "前のページ");
    expect(prompt).toContain("3ページ目");
    expect(prompt).toContain('"kind"');
    expect(prompt).toContain('"sfx"');
    expect(prompt).toContain("最後のページ");
  });
});

describe("mangaScriptSystemPrompt", () => {
  it("asks for the panel count, a cast with looks and per-panel positions", () => {
    const prompt = mangaScriptSystemPrompt("4koma");
    expect(prompt).toContain("ちょうど4個");
    expect(prompt).toContain('"cast":[');
    expect(prompt).toContain('"pos"');
    expect(mangaScriptSystemPrompt("page")).toContain("ちょうど5個");
  });
});
