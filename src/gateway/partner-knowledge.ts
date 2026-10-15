// Per-partner reference shelf: PDFs, web pages and pasted text a partner can draw on in chat.
// Documents are split into short passages; each turn the passages that best match the
// conversation (character-bigram BM25, which suits Japanese without a tokenizer) are added to
// the prompt, so a long PDF never has to fit in the model's context.

import { randomUUID } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { neuralSearch } from "./knowledge-neural.js";
import { getPartnerDir } from "./partner-store.js";

export type KnowledgeKind = "pdf" | "url" | "text";

export type KnowledgeDoc = {
  id: string;
  title: string;
  kind: KnowledgeKind;
  /** URL or original file name. */
  source: string;
  chars: number;
  passages: number;
  addedAt: number;
};

type KnowledgeIndex = { docs: KnowledgeDoc[] };
type StoredPassages = { passages: string[] };

/** Longer documents are cut here; a shelf is for reference material, not whole libraries. */
export const MAX_DOC_CHARS = 400_000;
export const MAX_DOCS = 200;
const PASSAGE_CHARS = 480;
const PASSAGE_OVERLAP = 60;
const FETCH_TIMEOUT_MS = 20_000;

function knowledgeDir(partnerId: string): string {
  return path.join(getPartnerDir(partnerId), "knowledge");
}

async function readJson<T>(file: string, fallback: T): Promise<T> {
  try {
    return JSON.parse(await fs.readFile(file, "utf-8")) as T;
  } catch {
    return fallback;
  }
}

async function writeJson(file: string, value: unknown): Promise<void> {
  await fs.mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(value), "utf-8");
  await fs.rename(tmp, file);
}

export async function listKnowledge(partnerId: string): Promise<KnowledgeDoc[]> {
  const index = await readJson<KnowledgeIndex>(path.join(knowledgeDir(partnerId), "index.json"), {
    docs: [],
  });
  return index.docs;
}

/** Collapses whitespace runs but keeps paragraph breaks, which the splitter uses. */
export function normalizeDocText(text: string): string {
  return (
    text
      .replace(/\r\n?/g, "\n")
      .replace(/[ \t　]+/g, " ")
      // Some PDFs put a space between every Japanese character (「設 計」), which breaks search.
      .replace(
        /(?<=[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}ー、。]) (?=[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}ー、。])/gu,
        "",
      )
      .replace(/ *\n */g, "\n")
      .replace(/\n{3,}/g, "\n\n")
      .trim()
  );
}

/** Splits into passages of about PASSAGE_CHARS, preferring paragraph and sentence ends. */
export function splitPassages(text: string): string[] {
  const passages: string[] = [];
  let start = 0;
  while (start < text.length) {
    let end = Math.min(text.length, start + PASSAGE_CHARS);
    if (end < text.length) {
      const window = text.slice(start + PASSAGE_CHARS / 2, end);
      const breakAt = Math.max(
        window.lastIndexOf("\n"),
        window.lastIndexOf("。"),
        window.lastIndexOf(". "),
        window.lastIndexOf("！"),
        window.lastIndexOf("？"),
      );
      if (breakAt >= 0) end = start + PASSAGE_CHARS / 2 + breakAt + 1;
    }
    const passage = text.slice(start, end).trim();
    if (passage.length >= 8) passages.push(passage);
    if (end >= text.length) break;
    start = Math.max(end - PASSAGE_OVERLAP, start + 1);
  }
  return passages;
}

export async function addKnowledge(
  partnerId: string,
  input: { title: string; kind: KnowledgeKind; source: string; text: string },
): Promise<KnowledgeDoc> {
  const text = normalizeDocText(input.text).slice(0, MAX_DOC_CHARS);
  if (text.length < 20) throw new Error("読み取れた文字がほとんどありません（画像だけのPDFなど）");
  const dir = knowledgeDir(partnerId);
  const index = await readJson<KnowledgeIndex>(path.join(dir, "index.json"), { docs: [] });
  if (index.docs.length >= MAX_DOCS) throw new Error(`資料は${MAX_DOCS}件までです`);
  const passages = splitPassages(text);
  const doc: KnowledgeDoc = {
    id: `kn-${randomUUID().slice(0, 8)}`,
    title: input.title.trim().slice(0, 120) || "無題の資料",
    kind: input.kind,
    source: input.source.slice(0, 500),
    chars: text.length,
    passages: passages.length,
    addedAt: Date.now(),
  };
  await writeJson(path.join(dir, `${doc.id}.json`), { passages } satisfies StoredPassages);
  // The whole text too, so the shelf can be read and edited later.
  await fs.writeFile(path.join(dir, `${doc.id}.txt`), text, "utf-8");
  // Same source again replaces the old copy instead of doubling every passage.
  const replaced = index.docs.filter((d) => d.source && d.source === doc.source);
  for (const old of replaced) {
    await fs.rm(path.join(dir, `${old.id}.json`), { force: true });
    await fs.rm(path.join(dir, `${old.id}.txt`), { force: true });
  }
  index.docs = [...index.docs.filter((d) => !replaced.includes(d)), doc];
  await writeJson(path.join(dir, "index.json"), index);
  return doc;
}

export async function deleteKnowledge(partnerId: string, docId: string): Promise<boolean> {
  const dir = knowledgeDir(partnerId);
  const index = await readJson<KnowledgeIndex>(path.join(dir, "index.json"), { docs: [] });
  const next = index.docs.filter((d) => d.id !== docId);
  if (next.length === index.docs.length) return false;
  await fs.rm(path.join(dir, `${path.basename(docId)}.json`), { force: true });
  await fs.rm(path.join(dir, `${path.basename(docId)}.txt`), { force: true });
  await writeJson(path.join(dir, "index.json"), { docs: next });
  return true;
}

/** Joins passages back into one text, dropping the overlap the splitter repeats. */
export function joinPassages(passages: string[]): string {
  let text = "";
  for (const passage of passages) {
    let overlap = 0;
    for (let n = Math.min(text.length, passage.length, PASSAGE_OVERLAP); n >= 4; n--) {
      if (text.endsWith(passage.slice(0, n))) {
        overlap = n;
        break;
      }
    }
    text += overlap ? passage.slice(overlap) : (text ? "\n" : "") + passage;
  }
  return text;
}

/** The whole text of a document; older documents (no saved text) are rebuilt from passages. */
export async function getKnowledgeText(
  partnerId: string,
  docId: string,
): Promise<{ doc: KnowledgeDoc; text: string } | undefined> {
  const dir = knowledgeDir(partnerId);
  const doc = (await listKnowledge(partnerId)).find((d) => d.id === docId);
  if (!doc) return undefined;
  const id = path.basename(docId);
  try {
    return { doc, text: await fs.readFile(path.join(dir, `${id}.txt`), "utf-8") };
  } catch {
    const stored = await readJson<StoredPassages>(path.join(dir, `${id}.json`), { passages: [] });
    return { doc, text: joinPassages(stored.passages) };
  }
}

/** Replaces a document's title and/or text; the passages are rebuilt for search. */
export async function updateKnowledge(
  partnerId: string,
  docId: string,
  input: { title?: string; text?: string },
): Promise<KnowledgeDoc | undefined> {
  const dir = knowledgeDir(partnerId);
  const index = await readJson<KnowledgeIndex>(path.join(dir, "index.json"), { docs: [] });
  const doc = index.docs.find((d) => d.id === docId);
  if (!doc) return undefined;
  const id = path.basename(docId);
  if (input.title !== undefined) doc.title = input.title.trim().slice(0, 120) || doc.title;
  if (input.text !== undefined) {
    const text = normalizeDocText(input.text).slice(0, MAX_DOC_CHARS);
    if (text.length < 20) throw new Error("文章が短すぎます（20文字以上）");
    const passages = splitPassages(text);
    await writeJson(path.join(dir, `${id}.json`), { passages } satisfies StoredPassages);
    await fs.writeFile(path.join(dir, `${id}.txt`), text, "utf-8");
    doc.chars = text.length;
    doc.passages = passages.length;
  }
  await writeJson(path.join(dir, "index.json"), index);
  return doc;
}

// ---- search ----

/** Lowercased character bigrams of letters, digits, kana and kanji; ASCII words count whole. */
export function termsOf(text: string): string[] {
  const terms: string[] = [];
  const lower = text.toLowerCase();
  for (const word of lower.match(/[a-z0-9]{2,}/g) ?? []) terms.push(word);
  const cjk = lower.replace(/[^\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}ー]+/gu, " ");
  for (const run of cjk.split(" ")) {
    for (let i = 0; i + 1 < run.length; i++) {
      const gram = run.slice(i, i + 2);
      // Two hiragana are mostly particles and endings ("して", "です"): too common to help.
      if (/^[\p{Script=Hiragana}ー]{2}$/u.test(gram)) continue;
      terms.push(gram);
    }
    if (run.length === 1 && /\p{Script=Han}/u.test(run)) terms.push(run);
  }
  return terms;
}

export type KnowledgeHit = { docId: string; title: string; text: string; score: number };

export function rankPassages(
  query: string,
  docs: Array<{ doc: KnowledgeDoc; passages: string[] }>,
  limit: number,
): KnowledgeHit[] {
  const queryTerms = [...new Set(termsOf(query))];
  if (queryTerms.length === 0) return [];
  const all = docs.flatMap(({ doc, passages }) =>
    passages.map((text) => ({ doc, text, terms: termsOf(text) })),
  );
  if (all.length === 0) return [];
  const df = new Map<string, number>();
  for (const p of all) {
    for (const t of new Set(p.terms)) if (queryTerms.includes(t)) df.set(t, (df.get(t) ?? 0) + 1);
  }
  const avgLen = all.reduce((n, p) => n + p.terms.length, 0) / all.length || 1;
  const k1 = 1.2;
  const b = 0.75;
  const hits: KnowledgeHit[] = [];
  for (const p of all) {
    const tf = new Map<string, number>();
    for (const t of p.terms) if (df.has(t)) tf.set(t, (tf.get(t) ?? 0) + 1);
    let score = 0;
    let matched = 0;
    for (const [t, f] of tf) {
      const n = df.get(t) ?? 0;
      const idf = Math.log(1 + (all.length - n + 0.5) / (n + 0.5));
      score += (idf * f * (k1 + 1)) / (f + k1 * (1 - b + (b * p.terms.length) / avgLen));
      matched++;
    }
    // One shared bigram is usually chance; ask for a little overlap before it counts.
    if (matched >= Math.min(2, queryTerms.length) && score > 0) {
      hits.push({ docId: p.doc.id, title: p.doc.title, text: p.text, score });
    }
  }
  return hits.toSorted((x, y) => y.score - x.score).slice(0, limit);
}

export async function searchKnowledge(
  partnerId: string,
  query: string,
  limit = 4,
): Promise<KnowledgeHit[]> {
  const dir = knowledgeDir(partnerId);
  const docs = await listKnowledge(partnerId);
  if (docs.length === 0 || !query.trim()) return [];
  const loaded = await Promise.all(
    docs.map(async (doc) => ({
      doc,
      passages: (await readJson<StoredPassages>(path.join(dir, `${doc.id}.json`), { passages: [] }))
        .passages,
    })),
  );
  const wordHits = rankPassages(query, loaded, 40);
  // Meaning search (embedding + reranker) when its small servers are up; otherwise the word-matching result stands.
  const candidates = loaded.flatMap(({ doc, passages }) =>
    passages.map((text, index) => ({
      key: `${doc.id}:${index}`,
      text,
      index,
      docPassages: passages,
      cacheFile: path.join(dir, `${doc.id}.emb.json`),
      title: doc.title,
      docId: doc.id,
    })),
  );
  const wordKeys = wordHits.map(
    (h) => candidates.find((c) => c.docId === h.docId && c.text === h.text)?.key ?? "",
  );
  const neural = await neuralSearch(query, candidates, wordKeys, limit);
  if (!neural || neural.length === 0) return wordHits.slice(0, limit);
  const byKey = new Map(candidates.map((c) => [c.key, c]));
  return neural.flatMap(({ key, score }) => {
    const c = byKey.get(key);
    return c ? [{ docId: c.docId, title: c.title, text: c.text, score }] : [];
  });
}

/** Matching passages as a note for the latest chat turn, or "" when nothing fits. */
export async function knowledgeNoteFor(
  partnerId: string,
  query: string,
  maxChars = 2000,
): Promise<string> {
  const hits = await searchKnowledge(partnerId, query, 6).catch(() => []);
  if (hits.length === 0) return "";
  const best = hits[0]?.score ?? 0;
  const lines: string[] = [];
  let used = 0;
  for (const hit of hits) {
    if (hit.score < best * 0.35) break; // Far weaker matches are noise.
    const block = `［${hit.title}］${hit.text}`;
    if (used + block.length > maxChars) break;
    lines.push(block);
    used += block.length;
  }
  if (lines.length === 0) return "";
  return `【あなたの資料棚から、いまの話題に関係しそうな部分】\n資料に書かれていることは資料の内容として正確に使い、書かれていないことを資料の内容のように話さない。\n${lines.join("\n\n")}`;
}

// ---- reading sources ----

export async function pdfToText(data: Uint8Array): Promise<{ text: string; title?: string }> {
  const { openPdf } = await import("clawpdf");
  const pdf = await openPdf(data);
  try {
    const text = pdf.text({ maxChars: MAX_DOC_CHARS });
    const title = pdf.metadata.title?.trim();
    return { text, ...(title ? { title } : {}) };
  } finally {
    pdf.destroy();
  }
}

/** Main text of an HTML page: drops scripts, styles and page chrome, keeps block breaks. */
export function htmlToText(html: string): { text: string; title?: string } {
  const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1];
  const body = html
    .replace(/<(script|style|noscript|svg|nav|header|footer|aside|form)\b[\s\S]*?<\/\1>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<(br|\/p|\/div|\/li|\/h\d|\/tr|\/section|\/article)\b[^>]*>/gi, "\n")
    .replace(/<[^>]+>/g, " ");
  return {
    text: decodeEntities(body),
    ...(title ? { title: decodeEntities(title).replace(/\s+/g, " ").trim() } : {}),
  };
}

function decodeEntities(text: string): string {
  return text
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n: string) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n: string) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&amp;/g, "&");
}

export async function fetchSourceText(
  url: string,
): Promise<{ text: string; title?: string; kind: KnowledgeKind }> {
  if (!/^https?:\/\//i.test(url)) throw new Error("http(s) のURLを指定してください");
  const res = await fetch(url, {
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    redirect: "follow",
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      "Accept-Language": "ja,en;q=0.8",
    },
  });
  if (!res.ok) throw new Error(`ページを開けませんでした（HTTP ${res.status}）`);
  const type = res.headers.get("content-type") ?? "";
  const bytes = new Uint8Array(await res.arrayBuffer());
  if (type.includes("pdf") || /\.pdf(?:$|[?#])/i.test(url)) {
    return { ...(await pdfToText(bytes)), kind: "pdf" };
  }
  const charset = type.match(/charset=([\w-]+)/i)?.[1];
  let html = new TextDecoder(charset || "utf-8").decode(bytes);
  const declared = html.slice(0, 2048).match(/<meta[^>]+charset=["']?([\w-]+)/i)?.[1];
  if (!charset && declared && !/utf-?8/i.test(declared)) {
    try {
      html = new TextDecoder(declared).decode(bytes);
    } catch {
      // Unknown label: keep the UTF-8 reading.
    }
  }
  return type.includes("html") || /<html|<body/i.test(html.slice(0, 4096))
    ? { ...htmlToText(html), kind: "url" }
    : { text: html, kind: "url" };
}
