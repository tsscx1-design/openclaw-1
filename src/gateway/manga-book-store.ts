// What a multi-page manga leaves behind so a sequel can be written from it: the cast with their
// fixed looks, the story outline, every page's script, a summary and how it ended. The next
// volume reads this instead of starting over, so people and story do not drift apart.

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type { ComicCharacter } from "./comic-series.js";
import type { BookPanel } from "./manga-script.js";
import { resolveOpenClawHome } from "./partner-store.js";

export type BookHistoryEntry = { volume: number; title: string; summary: string };

export type SavedBook = {
  /** The media job that drew this volume. */
  jobId: string;
  volume: number;
  title: string;
  /** What the user asked for. */
  request: string;
  cast: ComicCharacter[];
  /** What happens on each page, as outlined. */
  outline: string[];
  /** Each page's panels as written (lines, camera, effects). */
  pages: BookPanel[][];
  summary: string;
  /** How it ended, so the next volume can pick it up. */
  hook: string;
  /** The earlier volumes of the series, oldest first. */
  history: BookHistoryEntry[];
  previousJobId?: string;
  /** The work (folder) this volume is in (manga-works.ts). */
  workId?: string;
  createdAt: number;
};

function booksDir(): string {
  return path.join(resolveOpenClawHome(), "media", "manga-books");
}

export function isBookJobId(id: string): boolean {
  return /^[a-f0-9]{6,32}$/.test(id);
}

function bookFile(jobId: string): string {
  if (!isBookJobId(jobId)) throw new Error("Invalid job id");
  return path.join(booksDir(), `${jobId}.json`);
}

export async function saveBook(book: SavedBook): Promise<void> {
  await fs.mkdir(booksDir(), { recursive: true });
  await fs.writeFile(bookFile(book.jobId), JSON.stringify(book, null, 2), "utf-8");
}

export async function loadBook(jobId: string): Promise<SavedBook | undefined> {
  if (!isBookJobId(jobId)) return undefined;
  try {
    return JSON.parse(await fs.readFile(bookFile(jobId), "utf-8")) as SavedBook;
  } catch {
    return undefined;
  }
}

export async function listBooks(): Promise<SavedBook[]> {
  let names: string[] = [];
  try {
    names = await fs.readdir(booksDir());
  } catch {
    return [];
  }
  const out: SavedBook[] = [];
  for (const name of names) {
    if (!name.endsWith(".json")) continue;
    const book = await loadBook(name.slice(0, -5));
    if (book) out.push(book);
  }
  return out;
}

/** Changes some fields of a saved volume (the work it is in, who in the cast is in the library). */
export async function updateBook(jobId: string, patch: Partial<SavedBook>): Promise<SavedBook> {
  const book = await loadBook(jobId);
  if (!book) throw new Error("この巻の記録が見つかりません");
  const next = { ...book, ...patch, jobId: book.jobId };
  await saveBook(next);
  return next;
}

export async function removeBook(jobId: string): Promise<void> {
  if (!isBookJobId(jobId)) return;
  await fs.rm(bookFile(jobId), { force: true }).catch(() => undefined);
}

/** What the sequel's writer is told about everything so far. */
export function previouslyText(book: SavedBook): string {
  const earlier = [
    ...book.history,
    { volume: book.volume, title: book.title, summary: book.summary },
  ]
    .slice(-4)
    .map((h) => `- 第${h.volume}巻「${h.title}」: ${h.summary}`)
    .join("\n");
  return `${earlier}\n前の巻の終わり方: ${book.hook || "（指定なし）"}`;
}
