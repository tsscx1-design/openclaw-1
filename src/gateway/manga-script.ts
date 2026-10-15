// Manga pages (a 4-panel strip or one page of five panels): the chat model writes a title, a cast
// with fixed looks and the panels as JSON; the panel model and image requests are shared with
// serial comics (comic-series.ts).

import { characterRuleEnabled } from "./blocked-terms-config.js";
import {
  type ComicCharacter,
  type ComicPanel,
  castSystemPrompt,
  cleanCharacter,
  clip,
  firstJsonObject,
  PANEL_RULES,
  panelJsonSpec,
  parsePanel,
} from "./comic-series.js";

/** "book" is a multi-page manga: pages of varied panels, drawn from a page-by-page script. */
export type MangaLayout = "4koma" | "page" | "book";

export type MangaScript = {
  title: string;
  cast: ComicCharacter[];
  panels: ComicPanel[];
};

/** For "book" this is panels per page, on average (the time estimate multiplies by pages). */
export const MANGA_PANEL_COUNT: Record<MangaLayout, number> = { "4koma": 4, page: 5, book: 5 };

/** Shape each panel is drawn in, so the crop into its frame loses little. */
export const MANGA_PANEL_ASPECTS: Record<
  MangaLayout,
  Array<"landscape" | "square" | "portrait">
> = {
  "4koma": ["landscape", "landscape", "landscape", "landscape"],
  page: ["landscape", "square", "portrait", "square", "landscape"],
  book: ["landscape"],
};

export const MANGA_MIN_PAGES = 2;
export const MANGA_MAX_PAGES = 32;
export const MANGA_DEFAULT_PAGES = 8;

export type BubbleKind = "say" | "shout" | "think" | "whisper";
export type PanelSize = "small" | "normal" | "large";
export type PanelEffect = "none" | "focus" | "speed";

/** A panel of a multi-page manga: the shared panel plus what makes a page read like manga. */
export type BookPanel = ComicPanel & {
  size: PanelSize;
  effect: PanelEffect;
  /** Sound effects (ドン, ガタッ), drawn large beside the action. */
  sfx: string[];
  /** One per line: how it is spoken, which decides the bubble's shape. */
  kinds: BubbleKind[];
};

export type BookOutline = {
  title: string;
  cast: ComicCharacter[];
  /** What happens on each page. */
  pages: string[];
  /** The whole story in a few lines, and how it ends (the next volume starts from these). */
  summary: string;
  hook: string;
  /** A sequel: what the earlier volumes told (set by the caller, not the model). */
  previously?: string;
};

const BUBBLE_KINDS: readonly BubbleKind[] = ["say", "shout", "think", "whisper"];
const PANEL_SIZES: readonly PanelSize[] = ["small", "normal", "large"];
const PANEL_EFFECTS: readonly PanelEffect[] = ["none", "focus", "speed"];

function bookRatingLines(adult: boolean): string {
  const adultsOnlyLine = characterRuleEnabled("adultsOnly") ? "登場人物は全員成人として描く。" : "";
  const adultLine = adult
    ? "性的な場面も、お題に合わせて遠慮なく書いてよい（台詞・ナレーション・タグとも）。"
    : "";
  return `${adultsOnlyLine}${adultLine}`;
}

/**
 * What the outline writer is told up front: characters that are already decided (picked from the
 * library, or carried over from the last volume) and, for a sequel, what happened so far.
 * `autoCast: false` means nobody new may be added.
 */
export type OutlineBrief = {
  cast: ComicCharacter[];
  previously?: string;
  volume?: number;
  autoCast?: boolean;
};

/** Step 1: title, cast and what happens on each page. */
export function bookOutlinePrompt(pages: number, adult = false, brief?: OutlineBrief): string {
  const castSpec = castSystemPrompt(adult ? "adult" : "general")
    .split("\n")
    .find((line) => line.startsWith('{"cast"'))
    ?.slice(1, -1);
  const sequel = brief?.previously !== undefined;
  const fixed = brief?.cast ?? [];
  const blocks: string[] = [];
  if (sequel) {
    blocks.push(
      `これは第${(brief?.volume ?? 1) + 1}巻（続編）です。これまでの話:\n${brief?.previously}`,
    );
  }
  if (fixed.length > 0) {
    blocks.push(
      `${sequel ? "前の巻の登場人物" : "決まっている登場人物"}（名前・性格・口調はそのまま。見た目は後でシステムが固定する）:\n${fixed.map((c) => `- ${c.name}: ${c.profile}`).join("\n")}`,
    );
  }
  const rules: string[] = [];
  if (fixed.length > 0) {
    rules.push(
      `- 決まっている登場人物を cast にそのまま入れる（名前と profile を変えない）。${brief?.autoCast === false ? "新しい人物は足さない。" : "話に必要なら新しい人物を足してよい（全員で多くても4人）。"}`,
    );
  }
  if (sequel) {
    rules.push("- 前の巻の終わり方から話を続け、関係性・出来事・設定と矛盾させない。");
  }
  const preface = blocks.length > 0 ? `${blocks.join("\n")}\n` : "";
  const extraRules = rules.length > 0 ? `${rules.join("\n")}\n` : "";
  return `あなたは日本の漫画の原作者です。ユーザーのお題から、${pages}ページの${sequel ? "続編の" : "読み切り"}漫画の流れを作ります。
${preface}次のJSONだけを出力してください（説明や前置きは不要）。
{"title":"題名（15文字以内）",${castSpec},"pages":["1ページ目で起きること（1〜2文）","2ページ目で起きること","…"],"summary":"この巻のあらすじ（150文字以内。続きを書く人への引き継ぎ）","hook":"この巻の終わり方（次の巻へのつながり。50文字以内）"}
ルール:
- pages はちょうど${pages}個。起承転結をつけ、1ページ目で状況と人物を見せ、最後のページで印象的に締める。山場は後半に置く。
- cast は登場人物全員（多くても4人）。見た目は互いにはっきり違うようにする。
${extraRules}- ${bookRatingLines(adult)}`;
}

/**
 * Decided characters (picked from the library, or carried over from the last volume) keep their
 * saved looks whatever the model wrote; people the model added follow them, unless `autoCast` is
 * off (then only the decided ones appear).
 */
export function mergeBookCast(
  modelCast: ComicCharacter[],
  fixed: ComicCharacter[],
  autoCast = true,
): ComicCharacter[] {
  if (fixed.length === 0) return modelCast;
  const known = new Set(fixed.map((c) => c.name));
  const added = autoCast ? modelCast.filter((c) => !known.has(c.name)) : [];
  return [...fixed, ...added].slice(0, 6);
}

export function parseBookOutline(reply: string, pages: number): BookOutline {
  const data = firstJsonObject(reply);
  const cast = (Array.isArray(data.cast) ? data.cast : [])
    .map(cleanCharacter)
    .filter((c) => c.name && c.look)
    .slice(0, 4);
  const list = (Array.isArray(data.pages) ? data.pages : [])
    .map((p) => clip(p, 200))
    .filter(Boolean);
  if (list.length < Math.ceil(pages * 0.6)) {
    throw new Error(`ページの流れが足りません（${list.length}/${pages}）`);
  }
  // A model sometimes stops short; the missing pages repeat the last beat's mood as a closing page.
  while (list.length < pages) list.push(list.at(-1) ?? "");
  return {
    title: clip(data.title, 30),
    cast,
    pages: list.slice(0, pages),
    summary: clip(data.summary, 300),
    hook: clip(data.hook, 100),
  };
}

const BOOK_PANEL_SPEC = `{"size":"small か normal か large","characters":[{"name":"このコマに映る登場人物の名前","pos":"left か center か right（画面のどこに立つか）","action":"English tags for this character only: pose, action, facial expression, clothing state"}],"scene":"English tags: camera first (close-up, cowboy shot, wide shot, from above, from below, dutch angle), then background, place, time, lighting","lines":[{"who":"話す人の名前","kind":"say か shout か think か whisper","text":"台詞"}],"narration":"ナレーション（不要なら空文字）","sfx":["擬音（カタカナ。必要なコマだけ）"],"effect":"none か focus か speed"}`;

/** Step 2: one page, as 1-7 panels. */
export function bookPagePrompt(
  outline: BookOutline,
  pageNo: number,
  pages: number,
  previous: string,
  adult = false,
): string {
  const cast = outline.cast.map((c) => `- ${c.name}: ${c.profile}`).join("\n");
  const flow = outline.pages.map((p, i) => `${i + 1}ページ: ${p}`).join("\n");
  const closing = pageNo === pages ? "最後のページ。印象的なコマで締める。" : "";
  const earlier = outline.previously
    ? `これまでの話（続編です。矛盾させない）:\n${outline.previously}\n\n`
    : "";
  return `あなたは日本の漫画家です。漫画「${outline.title}」の${pageNo}ページ目（全${pages}ページ）を、コマ割りと台詞に落とします。
${earlier}登場人物:
${cast}

全体の流れ:
${flow}

${previous ? `前のページの終わり:\n${previous}\n\n` : ""}${pageNo}ページ目の内容に沿って、次のJSONだけを出力してください（説明や前置きは不要）。
{"panels":[${BOOK_PANEL_SPEC}]}
ルール:
- panels は3〜6個（山場のページは1〜3個の大きなコマでもよい）。${closing}
- size は見せ場のコマだけ large（1ページに多くても1個）、間を取るコマは small、ほかは normal。
- scene の先頭にカメラを入れ、同じ構図を3コマ続けない。顔のアップ、全身、見下ろし、見上げを織り交ぜる。
- 台詞の kind は、普通は say、叫びは shout、心の声は think、小声は whisper。
- sfx は音や動きが大きいコマだけ（0〜2個）。effect は、驚き・衝撃・集中は focus、素早い動きは speed、それ以外は none。
${PANEL_RULES}
- ${bookRatingLines(adult)}`;
}

export function parseBookPanel(raw: unknown): BookPanel | undefined {
  const base = parsePanel(raw);
  if (!base) return undefined;
  const p = (raw ?? {}) as Record<string, unknown>;
  const rawLines = (Array.isArray(p.lines) ? p.lines : [])
    .map((l) => (l ?? {}) as Record<string, unknown>)
    .filter((l) => clip(l.text, 45))
    .slice(0, 3);
  const kinds = base.lines.map(
    (_, i) => BUBBLE_KINDS.find((k) => k === rawLines[i]?.kind) ?? "say",
  );
  const sfx = (Array.isArray(p.sfx) ? p.sfx : [])
    .map((s) => clip(s, 8))
    .filter(Boolean)
    .slice(0, 2);
  return {
    ...base,
    size: PANEL_SIZES.find((s) => s === p.size) ?? "normal",
    effect: PANEL_EFFECTS.find((e) => e === p.effect) ?? "none",
    sfx,
    kinds,
  };
}

export function parseBookPage(reply: string): BookPanel[] {
  const data = firstJsonObject(reply);
  const panels = (Array.isArray(data.panels) ? data.panels : [])
    .map(parseBookPanel)
    .filter((p): p is BookPanel => p !== undefined)
    .slice(0, 7);
  if (panels.length === 0) throw new Error("このページのコマがありません");
  // One highlight per page: the first "large" stays, later ones are ordinary.
  let seen = false;
  return panels.map((p) => {
    if (p.size !== "large") return p;
    if (seen) return { ...p, size: "normal" as const };
    seen = true;
    return p;
  });
}

export function mangaScriptSystemPrompt(layout: MangaLayout, adult = false): string {
  const count = MANGA_PANEL_COUNT[layout];
  const form =
    layout === "4koma"
      ? "4コマ漫画（起・承・転・結。4コマ目でオチをつける）"
      : `${count}コマの1ページ漫画（1コマ目で状況を見せ、最後のコマで印象的に締める）`;
  const castSpec = castSystemPrompt(adult ? "adult" : "general")
    .split("\n")
    .find((line) => line.startsWith('{"cast"'))
    ?.slice(1, -1);
  const adultsOnlyLine = characterRuleEnabled("adultsOnly") ? "登場人物は全員成人として描く。" : "";
  const adultLine = adult ? "性的な場面も、お題に合わせて書いてよい。" : "";
  return `あなたは日本の漫画の脚本家です。ユーザーのお題から${form}の脚本を作ります。
次のJSONだけを出力してください（説明や前置きは不要）。
{"title":"題名（15文字以内）",${castSpec},"panels":[${panelJsonSpec(false)}]}
ルール:
- panels はちょうど${count}個。
- cast は登場人物全員（多くても3人）。見た目は互いにはっきり違うようにする。
${PANEL_RULES}
- ${adultsOnlyLine}${adultLine}`;
}

export function parseMangaScript(reply: string, layout: MangaLayout): MangaScript {
  const data = firstJsonObject(reply);
  const cast = (Array.isArray(data.cast) ? data.cast : [])
    .map(cleanCharacter)
    .filter((c) => c.name && c.look)
    .slice(0, 4);
  const want = MANGA_PANEL_COUNT[layout];
  const panels = (Array.isArray(data.panels) ? data.panels : [])
    .map(parsePanel)
    .filter((p): p is ComicPanel => p !== undefined)
    .slice(0, want);
  if (panels.length < want) {
    throw new Error(`脚本のコマが足りません（${panels.length}/${want}）`);
  }
  return { title: clip(data.title, 30), cast, panels };
}

export const MANGA_EXTRA_NEGATIVE =
  "text, speech bubble, comic, manga panel, multiple views, split screen, panel border, frame, english text, signature, watermark";
