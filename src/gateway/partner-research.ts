/**
 * Web research for partner creation. Finds pages about a real person or a fictional character on
 * several sites (Wikipedia and the articles it cites, the pixiv encyclopedia, user URLs, optional
 * web search) and keeps the parts that describe the subject, including their own words verbatim.
 * Network access is injected (ResearchIO) so the agent tool, the Gateway method and tests share it.
 */

export type SubjectKind = "real_person" | "fictional" | "unknown";

export type SearchHit = { title: string; url: string; snippet?: string };

export type FetchedPage = { title?: string; text: string };

export type ResearchIO = {
  /** JSON from the Wikipedia API (fixed host). */
  fetchJson: (url: string) => Promise<unknown>;
  /** Readable page text (Markdown), fetched through the guarded web fetch; undefined on failure. */
  fetchPage: (url: string, maxChars: number) => Promise<FetchedPage | undefined>;
  /** General web search (the configured web_search provider); optional. */
  searchWeb?: (query: string, count: number) => Promise<SearchHit[]>;
};

export type SourceOrigin = "wikipedia" | "user" | "encyclopedia" | "reference" | "search";

export type ResearchSource = {
  url: string;
  title: string;
  site: string;
  origin: SourceOrigin;
  /** Passages about the subject, bounded for one model call. */
  text: string;
  /** Readable text of the whole page, kept to verify quotes a model claims to have copied. */
  fullText: string;
};

export type ResearchQuote = { text: string; url: string; site: string };

export type ResearchBundle = {
  name: string;
  kind: SubjectKind;
  aliases: string[];
  wikipediaTitle?: string;
  sources: ResearchSource[];
  quotes: ResearchQuote[];
  /** Candidate pages that could not be read or did not mention the subject. */
  failedPages: number;
};

const WIKI_API = "https://ja.wikipedia.org/w/api.php";
const PAGE_FETCH_CHARS = 20_000;
const PASSAGE_CHARS = 3_500;
const WIKI_PASSAGE_CHARS = 6_000;
const FULL_TEXT_CHARS = 30_000;
const MAX_PER_SITE = 3;
const FETCH_CONCURRENCY = 3;
const MAX_QUOTES = 40;
const MAX_QUOTES_PER_PAGE = 15;

function clipText(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) : text;
}

function siteOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "";
  }
}

/** Comparable form of a name: width-folded, without a "(歌手)" qualifier, spaces or dots. */
export function normalizeName(value: string): string {
  return value
    .normalize("NFKC")
    .replace(/\s*\([^)]*\)\s*$/u, "")
    .replace(/[\s・･=＝]/gu, "")
    .toLowerCase();
}

/** Comparable form of a sentence for verbatim checks: spacing and quote marks are ignored. */
export function normalizeForMatch(value: string): string {
  return value
    .normalize("NFKC")
    .replace(/[\s「」『』"'“”‘’()[\]【】]/gu, "")
    .replace(/[…‥]|\.{2,}/gu, "…")
    .replace(/[～〜~]/gu, "〜")
    .toLowerCase();
}

/** True when `quote` appears (ignoring spacing and quote marks) in one of the texts. */
export function isVerbatimIn(quote: string, texts: string[]): boolean {
  const needle = normalizeForMatch(quote);
  if (needle.length < 4) {
    return false;
  }
  return texts.some((text) => normalizeForMatch(text).includes(needle));
}

// ---------- Wikipedia ----------

function wikiUrl(params: Record<string, string>): string {
  const query = new URLSearchParams({ format: "json", formatversion: "2", ...params });
  return `${WIKI_API}?${query.toString()}`;
}

async function searchWikipediaTitles(io: ResearchIO, query: string): Promise<string[]> {
  const data = (await io.fetchJson(
    wikiUrl({ action: "query", list: "search", srsearch: query, srlimit: "6" }),
  )) as { query?: { search?: Array<{ title?: unknown }> } } | undefined;
  return (data?.query?.search ?? [])
    .map((hit) => hit.title)
    .filter((title): title is string => typeof title === "string");
}

/**
 * The search hit that is the subject's own article: the same name, a qualified title ("橋本環奈
 * (女優)"), or the top hit when the asked name extends it (nickname "あのちゃん" -> "あの").
 */
export function pickSubjectTitle(name: string, titles: string[]): string | undefined {
  const target = normalizeName(name);
  if (!target) {
    return undefined;
  }
  return titles.find((title, index) => {
    const candidate = normalizeName(title);
    return (
      candidate === target || (index === 0 && candidate !== "" && target.startsWith(candidate))
    );
  });
}

/** Real people carry birth/death-year categories (or 存命人物); characters carry 架空の…. */
export function kindFromCategories(categories: string[]): SubjectKind {
  const names = categories.map((category) => category.replace(/^Category:/, ""));
  if (names.some((name) => name === "存命人物" || /^\d{1,4}年(?:生|没)$/.test(name))) {
    return "real_person";
  }
  if (names.some((name) => /架空の|キャラクター|登場人物/.test(name))) {
    return "fictional";
  }
  return "unknown";
}

type WikiPage = { title: string; url: string; extract: string; categories: string[] };

async function loadWikipediaPage(io: ResearchIO, title: string): Promise<WikiPage | undefined> {
  const data = (await io.fetchJson(
    wikiUrl({
      action: "query",
      prop: "extracts|categories|info",
      explaintext: "1",
      inprop: "url",
      cllimit: "max",
      redirects: "1",
      titles: title,
    }),
  )) as
    | {
        query?: {
          pages?: Array<{
            title?: string;
            fullurl?: string;
            extract?: string;
            missing?: boolean;
            categories?: Array<{ title?: string }>;
          }>;
        };
      }
    | undefined;
  const page = data?.query?.pages?.[0];
  if (!page || page.missing || !page.extract) {
    return undefined;
  }
  return {
    title: page.title ?? title,
    url: page.fullurl ?? `https://ja.wikipedia.org/wiki/${encodeURIComponent(title)}`,
    extract: page.extract,
    categories: (page.categories ?? [])
      .map((category) => category.title)
      .filter((value): value is string => typeof value === "string"),
  };
}

function decodeEntities(text: string): string {
  return text
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

/** External links of a rendered article (its cited interviews and news) with their titles. */
export function parseReferenceLinks(html: string): SearchHit[] {
  const hits: SearchHit[] = [];
  const seen = new Set<string>();
  for (const match of html.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/g)) {
    const attrs = match[1] ?? "";
    if (!/class="[^"]*\bexternal\b/.test(attrs)) {
      continue;
    }
    const href = decodeEntities(/href="([^"]+)"/.exec(attrs)?.[1] ?? "");
    if (!/^https?:\/\//.test(href) || seen.has(href)) {
      continue;
    }
    seen.add(href);
    const title = decodeEntities((match[2] ?? "").replace(/<[^>]+>/g, ""))
      .replace(/\s+/g, " ")
      .trim();
    hits.push({ title, url: href });
  }
  return hits;
}

async function loadReferenceLinks(io: ResearchIO, title: string): Promise<SearchHit[]> {
  const data = (await io.fetchJson(
    wikiUrl({ action: "parse", page: title, prop: "text", disabletoc: "1" }),
  )) as { parse?: { text?: unknown } } | undefined;
  const html = data?.parse?.text;
  return typeof html === "string" ? parseReferenceLinks(html) : [];
}

const WIKI_KEEP_SECTION =
  /人物|人物像|エピソード|趣味|嗜好|性格|特徴|来歴|経歴|プロフィール|概要|キャラクター|口癖|名言|発言|交友|人間関係|設定|能力|容姿|外見/;
const WIKI_DROP_SECTION =
  /出演|作品|ディスコグラフィ|脚注|出典|参考|外部リンク|関連項目|書籍|CM|ライブ|イベント|受賞|楽曲|映画|ドラマ|番組|不祥事|騒動|事件|逮捕|訴訟|炎上|スキャンダル|書誌|単行本|主題歌/;

/**
 * The intro and the sections that describe a person (人物, エピソード, 趣味…) of a plain-text
 * extract. Works lists and controversies are left out: they do not shape how someone talks.
 */
export function selectWikipediaSections(extract: string, maxChars: number): string {
  const parts = extract.split(/\n(?==+\s*[^=\n]+?\s*=+\s*$)/m);
  const kept: string[] = [];
  let dropDepth = 0;
  for (const [index, part] of parts.entries()) {
    const heading = /^(=+)\s*([^=\n]+?)\s*=+\s*$/m.exec(part);
    if (index === 0 || !heading) {
      kept.push(part.trim());
      continue;
    }
    const depth = heading[1]?.length ?? 2;
    const label = heading[2] ?? "";
    if (dropDepth && depth > dropDepth) {
      continue;
    }
    dropDepth = 0;
    if (WIKI_DROP_SECTION.test(label) || (depth <= 2 && !WIKI_KEEP_SECTION.test(label))) {
      dropDepth = depth;
      continue;
    }
    kept.push(clipText(part.trim(), 1_600));
  }
  return clipText(
    kept
      .filter(Boolean)
      .join("\n\n")
      .replace(/\n{3,}/g, "\n\n"),
    maxChars,
  );
}

// ---------- Candidate pages ----------

const BLOCKED_SITES =
  /(?:^|\.)(?:youtube\.com|youtu\.be|x\.com|twitter\.com|instagram\.com|tiktok\.com|facebook\.com|threads\.net|amazon\.co\.jp|amazon\.com|musicbrainz\.org|archive\.org|spotify\.com|apple\.com|line\.me|google\.com|google\.co\.jp|wikipedia\.org|wikidata\.org|wikimedia\.org|prtimes\.jp|kyodonewsprwire\.jp|townwork\.net|froma\.com|eplus\.jp|pia\.jp|tower\.jp|hmv\.co\.jp|jasrac\.or\.jp|nicovideo\.jp|openrec\.tv|chiebukuro\.yahoo\.co\.jp)$/;
const RICH_SITES =
  /(?:^|\.)(?:natalie\.mu|realsound\.jp|rockinon\.com|qjweb\.jp|cinra\.net|wired\.jp|mdpr\.jp|thetv\.jp|oricon\.co\.jp|bunshun\.jp|gendai\.media|sponichi\.co\.jp|nikkansports\.com|daily\.co\.jp|hochi\.news|sanspo\.com|encount\.press|news\.mynavi\.jp|barks\.jp|dic\.pixiv\.net|atwiki\.jp|wikiwiki\.jp|seesaawiki\.jp|animatetimes\.com|animeanime\.jp|febri\.jp|crank-in\.net|nylon\.jp|tokyo-sports\.co\.jp|smart-flash\.jp|tv-tokyo\.co\.jp|tbsradio\.jp|allnightnippon\.com)$/;
const INTERVIEW_TITLE =
  /インタビュー|interview|語る|語った|語り|告白|本音|素顔|対談|鼎談|ロング|明か|激白|トーク|独占|特集|ぶっちゃけ|胸中|心境|思い|素直|悩み/i;
const INTERVIEW_URL = /\/pp\/|interview|\/special\/|\/feature\/|\/column\//i;

type Candidate = SearchHit & { origin: SourceOrigin; score: number };

/** Interviews and content-rich sites first; sites without readable text are excluded (-1). */
export function scoreCandidate(hit: SearchHit, origin: SourceOrigin): number {
  const site = siteOf(hit.url);
  if (!site || BLOCKED_SITES.test(site)) {
    return -1;
  }
  let score =
    origin === "user" ? 100 : origin === "encyclopedia" ? 70 : origin === "search" ? 30 : 20;
  if (INTERVIEW_TITLE.test(`${hit.title} ${hit.snippet ?? ""}`)) {
    score += 40;
  }
  if (INTERVIEW_URL.test(hit.url)) {
    score += 25;
  }
  if (RICH_SITES.test(site)) {
    score += 15;
  }
  return score;
}

/** Highest-scoring pages first, at most MAX_PER_SITE per site so several sites are read. */
export function rankCandidates(candidates: Candidate[], limit: number): Candidate[] {
  const unique = new Map<string, Candidate>();
  for (const candidate of candidates) {
    if (candidate.score < 0) {
      continue;
    }
    const key = candidate.url.replace(/#.*$/, "").replace(/\/$/, "");
    const existing = unique.get(key);
    if (!existing || existing.score < candidate.score) {
      unique.set(key, candidate);
    }
  }
  const perSite = new Map<string, number>();
  const ranked: Candidate[] = [];
  for (const candidate of [...unique.values()].toSorted((a, b) => b.score - a.score)) {
    const site = siteOf(candidate.url);
    const count = perSite.get(site) ?? 0;
    if (count >= MAX_PER_SITE && candidate.origin !== "user") {
      continue;
    }
    perSite.set(site, count + 1);
    ranked.push(candidate);
    if (ranked.length >= limit) {
      break;
    }
  }
  return ranked;
}

export function searchQueriesFor(
  name: string,
  source: string | undefined,
  kind: SubjectKind,
): string[] {
  const subject = [name, source].filter(Boolean).join(" ");
  const real = [`${name} インタビュー`, `${name} 口調 一人称 口癖`, `${name} 性格 素顔`];
  const fictional = [`${subject} 性格 口調 一人称`, `${subject} 名言 セリフ`, `${subject} 口癖`];
  if (kind === "real_person") {
    return real;
  }
  if (kind === "fictional") {
    return fictional;
  }
  return [`${subject} 性格 口調 一人称`, `${name} インタビュー`, `${subject} 名言 セリフ`];
}

// ---------- Passages and quotes ----------

/** Markdown from the fetcher reduced to plain lines: images dropped, links kept as their text. */
export function cleanPageText(text: string): string {
  return text
    .replace(/!\[[^\]]*\]\([^)]*\)/g, "")
    .replace(/\[([^\]]*)\]\((?:[^()]|\([^)]*\))*\)/g, "$1")
    .replace(/\r/g, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{3,}/g, "\n\n");
}

const QUESTION_MARK = /^(?:[*_]{0,2})(?:[─━―—–]{1,2}|ーー|[QＱ]\d*[.．:：]|[QＱ]\s)/u;
const END_OF_ARTICLE =
  /^(?:#{1,6}\s*)?(?:プロフィール|PROFILE|Profile|リリース情報|公演情報|ライブ情報|ツアー情報|作品情報|番組情報|配信情報|放送情報|イベント情報|関連記事|あわせて読みたい|INFORMATION|Information|この記事を書いた人)/;
const SPEAKER_LABEL = /^(?:[*_]{0,2})([^\s：:「」『』、。]{1,12}?)(?:[*_]{0,2})(?:　|\s*[：:])\s*/u;

function questionText(line: string): string | undefined {
  if (!QUESTION_MARK.test(line)) {
    return undefined;
  }
  const text = line.replace(QUESTION_MARK, "").replace(/[*_]/g, "").trim();
  // Decorative rules ("――――") are not questions.
  return text.replace(/^[─━―—–ー\s]+/u, "").length >= 2 ? text : undefined;
}

/** Speaker labels of a dialogue (「ano　…」, 「幾田：…」) that repeat across the page. */
function findSpeakerLabels(lines: string[]): Set<string> {
  const counts = new Map<string, number>();
  for (const line of lines) {
    const label = SPEAKER_LABEL.exec(line)?.[1];
    if (label && line.length > label.length + 4) {
      counts.set(label, (counts.get(label) ?? 0) + 1);
    }
  }
  return new Set([...counts].filter(([, count]) => count >= 3).map(([label]) => label));
}

function matchesAlias(label: string, aliases: string[]): boolean {
  const normalized = normalizeName(label);
  return aliases.some((alias) => {
    const target = normalizeName(alias);
    return target.length > 0 && (normalized === target || normalized.startsWith(target));
  });
}

export type InterviewExtract = { answers: string[]; pairs: string[] };

/**
 * Answers of an interview (questions start with "──", "――" or "Q."). On a dialogue page only the
 * lines labeled with the subject's name count; other speakers' words are not the subject's.
 */
export function extractInterview(text: string, aliases: string[]): InterviewExtract | undefined {
  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const questionCount = lines.filter((line) => questionText(line) !== undefined).length;
  const speakers = findSpeakerLabels(lines);
  const subjectLabels = new Set([...speakers].filter((label) => matchesAlias(label, aliases)));
  if (questionCount < 3 && subjectLabels.size === 0) {
    return undefined;
  }
  const answers: string[] = [];
  const pairs: string[] = [];
  let question = "";
  let inAnswer = false;
  for (const line of lines) {
    if (answers.length > 0 && END_OF_ARTICLE.test(line)) {
      break;
    }
    const asked = questionText(line);
    if (asked !== undefined) {
      question = asked;
      inAnswer = true;
      continue;
    }
    if (line.startsWith("#")) {
      inAnswer = false;
      continue;
    }
    let answer = line;
    let isAnswer = inAnswer;
    const label = speakers.size > 0 ? SPEAKER_LABEL.exec(line) : null;
    if (label && speakers.has(label[1] ?? "")) {
      if (!subjectLabels.has(label[1] ?? "")) {
        continue;
      }
      answer = line.slice(label[0].length);
      isAnswer = true;
    } else if (subjectLabels.size > 0) {
      // A dialogue page: unlabeled lines are narration, not the subject's words.
      continue;
    }
    answer = answer.replace(/[*_]{2}/g, "").trim();
    if (!isAnswer || answer.length < 8 || /^[(（※]/.test(answer)) {
      continue;
    }
    answers.push(answer);
    pairs.push(question ? `──${clipText(question, 60)}\n${answer}` : answer);
    question = "";
  }
  return answers.length > 0 ? { answers, pairs } : undefined;
}

/**
 * Sentences of an answer short enough to serve as sample lines (10–90 characters). A sentence
 * ends at 。！？ outside quotation marks, so 「もっとできるんじゃない？」みたいな stays whole.
 */
export function splitSpokenSentences(answer: string): string[] {
  const sentences: string[] = [];
  let depth = 0;
  let current = "";
  for (const char of answer) {
    current += char;
    if (char === "「" || char === "『") {
      depth += 1;
    } else if ((char === "」" || char === "』") && depth > 0) {
      depth -= 1;
    } else if (depth === 0 && /[。！？!?]/u.test(char)) {
      sentences.push(current);
      current = "";
    }
  }
  sentences.push(current);
  return sentences
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length >= 10 && sentence.length <= 90);
}

/** Lines of an encyclopedia "名台詞" list: a whole line that is one quotation. */
function extractListedQuotes(lines: string[]): string[] {
  return lines
    .map((line) => /^(?:[-*・]\s*)?「([^「」]{4,90})」\s*$/u.exec(line.trim())?.[1]?.trim() ?? "")
    .filter(Boolean);
}

/** Matches distinctive aliases only: two-letter names such as "あの" are ordinary words too. */
function mentionPattern(aliases: string[]): RegExp | undefined {
  const parts = aliases
    .map((alias) => alias.trim())
    .filter((alias) => alias.length >= 3)
    .map((alias) => {
      const escaped = alias.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return /^[\x20-\x7e]+$/.test(alias) ? `\\b${escaped}\\b` : escaped;
    });
  return parts.length > 0 ? new RegExp(parts.join("|"), "iu") : undefined;
}

export type PagePassages = { passages: string; quotes: string[] };

/**
 * The parts of a page about the subject. Interviews give their answers (the subject's own words);
 * encyclopedia and user pages are about the subject throughout; other pages give the paragraphs
 * that mention them. Quotes in prose are left to the digest model, which can tell whose they are.
 */
export function selectPassages(params: {
  text: string;
  aliases: string[];
  origin: SourceOrigin;
  maxChars: number;
}): PagePassages {
  const text = cleanPageText(params.text);
  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const interview = extractInterview(text, params.aliases);
  if (interview) {
    return {
      passages: clipText(interview.pairs.join("\n"), params.maxChars),
      quotes: interview.answers.flatMap(splitSpokenSentences),
    };
  }
  const wholePage = params.origin === "encyclopedia" || params.origin === "user";
  const mention = mentionPattern(params.aliases);
  const kept: string[] = [];
  for (const [index, line] of lines.entries()) {
    if (
      /^#{1,6}\s*(?:関連タグ|関連記事|関連イラスト|コメント|外部リンク|脚注|関連項目)/.test(line)
    ) {
      if (wholePage) {
        break;
      }
      continue;
    }
    const relevant =
      wholePage ||
      (mention !== undefined &&
        (mention.test(line) || (index > 0 && mention.test(lines[index - 1] ?? ""))));
    if (relevant && line.length >= 6) {
      kept.push(line);
    }
  }
  return {
    passages: clipText(kept.join("\n"), params.maxChars),
    quotes: params.origin === "encyclopedia" ? extractListedQuotes(lines) : [],
  };
}

// ---------- Orchestration ----------

export type ResearchProgress = (message: string) => void;

/** Nicknames and stage names from a Wikipedia intro (愛称は…, ano名義), plus the given ones. */
export function collectAliases(params: {
  name: string;
  wikipediaTitle?: string;
  intro?: string;
  extra?: string[];
}): string[] {
  const aliases = [params.name, ...(params.extra ?? [])];
  if (params.wikipediaTitle) {
    aliases.push(params.wikipediaTitle.replace(/\s*\([^)]*\)\s*$/u, ""));
  }
  const intro = params.intro ?? "";
  for (const match of intro.matchAll(
    /(?:愛称|通称|ニックネーム)は[、,]?\s*「?([^\s。、」]{2,15})/gu,
  )) {
    aliases.push(match[1] ?? "");
  }
  for (const match of intro.matchAll(/([A-Za-z][A-Za-z0-9.\-' ]{1,20}?)\s*"?\s*名義/gu)) {
    aliases.push((match[1] ?? "").trim());
  }
  return [...new Set(aliases.map((alias) => alias.trim()).filter(Boolean))];
}

async function mapLimit<T, R>(
  items: T[],
  limit: number,
  task: (item: T) => Promise<R>,
): Promise<R[]> {
  const results: R[] = Array.from({ length: items.length });
  let next = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await task(items[index] as T);
    }
  });
  await Promise.all(workers);
  return results;
}

type WikipediaFindings = {
  kind: SubjectKind;
  title?: string;
  intro: string;
  source?: ResearchSource;
  references: SearchHit[];
};

/**
 * The subject's own article (or, for a character, its work's 登場人物 article): the passages
 * about the subject, whether it is a real person, and the pages the article cites.
 */
async function researchWikipedia(params: {
  name: string;
  source?: string;
  kind: SubjectKind;
  extraAliases?: string[];
  io: ResearchIO;
}): Promise<WikipediaFindings> {
  const { name, source, io } = params;
  const none: WikipediaFindings = { kind: params.kind, intro: "", references: [] };
  const titles = await searchWikipediaTitles(io, name);
  const subjectTitle = pickSubjectTitle(name, titles);
  let title = subjectTitle;
  if (!title && params.kind !== "real_person") {
    const listQuery = source ? `${source} 登場人物` : titles[0] ? `${titles[0]} 登場人物` : "";
    const listTitles = listQuery ? await searchWikipediaTitles(io, listQuery) : [];
    title = listTitles.find((candidate) => candidate.includes("登場人物"));
  }
  const page = title ? await loadWikipediaPage(io, title) : undefined;
  if (!page) {
    return none;
  }
  const intro = page.extract.split(/\n==/)[0] ?? "";
  const kind =
    subjectTitle && params.kind === "unknown" ? kindFromCategories(page.categories) : params.kind;
  const text = subjectTitle
    ? selectWikipediaSections(page.extract, WIKI_PASSAGE_CHARS)
    : selectPassages({
        text: page.extract,
        aliases: collectAliases({ name, extra: params.extraAliases }),
        origin: "reference",
        maxChars: WIKI_PASSAGE_CHARS,
      }).passages;
  return {
    kind,
    title: page.title,
    intro: subjectTitle ? intro : "",
    references: subjectTitle ? await loadReferenceLinks(io, page.title) : [],
    source:
      text.length >= 100
        ? {
            url: page.url,
            title: `Wikipedia「${page.title}」`,
            site: "ja.wikipedia.org",
            origin: "wikipedia",
            text,
            fullText: clipText(page.extract, FULL_TEXT_CHARS),
          }
        : undefined,
  };
}

/**
 * Researches a subject on several sites. Wikipedia (official API) tells whether the subject is a
 * real person and lists the interviews it cites; those, the pixiv encyclopedia, user URLs and web
 * search results are ranked so that several different sites are read.
 */
export async function researchPartnerSubject(params: {
  name: string;
  source?: string;
  kind?: SubjectKind;
  aliases?: string[];
  urls?: string[];
  io: ResearchIO;
  maxPages?: number;
  onProgress?: ResearchProgress;
}): Promise<ResearchBundle> {
  const { name, source, io } = params;
  const maxPages = params.maxPages ?? 10;
  const progress = params.onProgress ?? (() => undefined);
  const sources: ResearchSource[] = [];
  const quotes: ResearchQuote[] = [];
  const candidates: Candidate[] = [];

  progress("Wikipedia を調べています");
  let wikipedia: WikipediaFindings = {
    kind: params.kind ?? "unknown",
    intro: "",
    references: [],
  };
  try {
    wikipedia = await researchWikipedia({
      name,
      source,
      kind: params.kind ?? "unknown",
      extraAliases: params.aliases,
      io,
    });
  } catch {
    // Wikipedia is one source among several; the others still run.
  }
  const kind = wikipedia.kind;
  if (wikipedia.source) {
    sources.push(wikipedia.source);
  }
  for (const hit of wikipedia.references) {
    candidates.push({ ...hit, origin: "reference", score: scoreCandidate(hit, "reference") });
  }
  const aliases = collectAliases({
    name,
    wikipediaTitle: wikipedia.intro ? wikipedia.title : undefined,
    intro: wikipedia.intro,
    extra: params.aliases,
  });

  for (const url of params.urls ?? []) {
    const hit = { title: url, url };
    candidates.push({ ...hit, origin: "user", score: scoreCandidate(hit, "user") });
  }
  for (const alias of aliases.filter((value) => value.length >= 3).slice(0, 2)) {
    const hit = {
      title: `ピクシブ百科事典「${alias}」`,
      url: `https://dic.pixiv.net/a/${encodeURIComponent(alias)}`,
    };
    const score = scoreCandidate(hit, "encyclopedia") - (kind === "real_person" ? 30 : 0);
    candidates.push({ ...hit, origin: "encyclopedia", score });
  }

  if (io.searchWeb) {
    progress("Web検索をしています");
    for (const query of searchQueriesFor(name, source, kind)) {
      try {
        const hits = await io.searchWeb(query, 8);
        if (hits.length === 0) {
          break;
        }
        for (const hit of hits) {
          candidates.push({ ...hit, origin: "search", score: scoreCandidate(hit, "search") });
        }
      } catch {
        // A blocked or failing provider ends searching; the other candidates remain.
        break;
      }
    }
  }

  // A same-name encyclopedia entry about something else must mention the work.
  const sourceKey = source ? normalizeForMatch(source).slice(0, 4) : "";
  const ranked = rankCandidates(candidates, maxPages * 2);
  let failedPages = 0;
  let read = 0;
  const pending = [...ranked];
  while (pending.length > 0 && sources.length < maxPages + 1) {
    const batch = pending.splice(0, FETCH_CONCURRENCY * 2);
    const pages = await mapLimit(batch, FETCH_CONCURRENCY, async (candidate) => {
      read += 1;
      progress(`ページを読んでいます（${read}/${ranked.length}）: ${siteOf(candidate.url)}`);
      try {
        return await io.fetchPage(candidate.url, PAGE_FETCH_CHARS);
      } catch {
        return undefined;
      }
    });
    for (const [index, page] of pages.entries()) {
      const candidate = batch[index];
      if (!candidate || sources.length >= maxPages + 1) {
        continue;
      }
      const offTopic =
        candidate.origin === "encyclopedia" &&
        sourceKey !== "" &&
        !normalizeForMatch(page?.text ?? "").includes(sourceKey);
      if (!page || page.text.length < 300 || offTopic) {
        failedPages += 1;
        continue;
      }
      const selected = selectPassages({
        text: page.text,
        aliases,
        origin: candidate.origin,
        maxChars: PASSAGE_CHARS,
      });
      if (selected.passages.length < 150) {
        failedPages += 1;
        continue;
      }
      const site = siteOf(candidate.url);
      sources.push({
        url: candidate.url,
        title: page.title?.trim() || candidate.title || site,
        site,
        origin: candidate.origin,
        text: selected.passages,
        fullText: clipText(cleanPageText(page.text), FULL_TEXT_CHARS),
      });
      // One long interview must not crowd out the quotes of the other pages.
      for (const quote of selected.quotes.slice(0, MAX_QUOTES_PER_PAGE)) {
        quotes.push({ text: quote, url: candidate.url, site });
      }
    }
  }

  const seenQuotes = new Set<string>();
  const uniqueQuotes = quotes.filter((quote) => {
    const key = normalizeForMatch(quote.text);
    if (seenQuotes.has(key)) {
      return false;
    }
    seenQuotes.add(key);
    return true;
  });
  return {
    name,
    kind,
    aliases,
    wikipediaTitle: wikipedia.title,
    sources,
    quotes: uniqueQuotes.slice(0, MAX_QUOTES),
    failedPages,
  };
}
