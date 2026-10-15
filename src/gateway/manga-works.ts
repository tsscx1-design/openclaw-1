// Manga "works": the folder a series of volumes lives in. A work has a name; its volumes are the
// saved books (manga-book-store.ts) that carry its id. A sequel stays in its work; a first volume
// starts a new one; the user can rename a work and move a volume to another work.

import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { listBooks, type SavedBook, updateBook } from "./manga-book-store.js";
import { resolveOpenClawHome } from "./partner-store.js";

export type MangaWork = { id: string; title: string; createdAt: number; updatedAt: number };

export type MangaWorkListing = MangaWork & {
  volumes: Array<{ jobId: string; volume: number; title: string }>;
};

function worksDir(): string {
  return path.join(resolveOpenClawHome(), "media", "manga-works");
}

export function isWorkId(id: string): boolean {
  return /^work-[a-f0-9]{8}$/.test(id);
}

function workFile(id: string): string {
  if (!isWorkId(id)) throw new Error("Invalid work id");
  return path.join(worksDir(), `${id}.json`);
}

function cleanTitle(raw: unknown): string {
  return (typeof raw === "string" ? raw : "").replace(/\s+/g, " ").trim().slice(0, 40);
}

async function saveWork(work: MangaWork): Promise<void> {
  await fs.mkdir(worksDir(), { recursive: true });
  await fs.writeFile(workFile(work.id), JSON.stringify(work, null, 2), "utf-8");
}

export async function getWork(id: string): Promise<MangaWork | undefined> {
  if (!isWorkId(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(workFile(id), "utf-8")) as MangaWork;
  } catch {
    return undefined;
  }
}

export async function createWork(title: string): Promise<MangaWork> {
  const now = Date.now();
  const work: MangaWork = {
    id: `work-${randomBytes(4).toString("hex")}`,
    title: cleanTitle(title) || "無題の作品",
    createdAt: now,
    updatedAt: now,
  };
  await saveWork(work);
  return work;
}

export async function renameWork(id: string, title: string): Promise<MangaWork> {
  const work = await getWork(id);
  if (!work) throw new Error("作品が見つかりません");
  const name = cleanTitle(title);
  if (!name) throw new Error("作品名を入れてください");
  const next = { ...work, title: name, updatedAt: Date.now() };
  await saveWork(next);
  return next;
}

/** Every work with its volumes (oldest first); a work with no volume left is removed. */
export async function listWorks(): Promise<MangaWorkListing[]> {
  let names: string[] = [];
  try {
    names = await fs.readdir(worksDir());
  } catch {
    return [];
  }
  const books = await listBooks();
  const out: MangaWorkListing[] = [];
  for (const name of names) {
    if (!/^work-[a-f0-9]{8}\.json$/.test(name)) continue;
    const work = await getWork(name.slice(0, -5));
    if (!work) continue;
    const volumes = books
      .filter((b) => b.workId === work.id)
      .toSorted((a, b) => a.volume - b.volume || a.createdAt - b.createdAt)
      .map((b) => ({ jobId: b.jobId, volume: b.volume, title: b.title }));
    if (volumes.length === 0) {
      await fs.rm(workFile(work.id), { force: true }).catch(() => undefined);
      continue;
    }
    out.push({ ...work, volumes });
  }
  return out.toSorted((a, b) => b.updatedAt - a.updatedAt);
}

/**
 * Puts a volume into a work: `workId` an existing one, or none to start a new work (named
 * `title`, else the volume's own title). Returns the work.
 */
export async function moveVolume(
  jobId: string,
  target: { workId?: string; title?: string },
  book: SavedBook,
): Promise<MangaWork> {
  let work: MangaWork | undefined;
  if (target.workId) {
    work = await getWork(target.workId);
    if (!work) throw new Error("作品が見つかりません");
  } else {
    work = await createWork(target.title || book.title);
  }
  await updateBook(jobId, { workId: work.id });
  await saveWork({ ...work, updatedAt: Date.now() });
  return work;
}

/** Marks a work as used just now (a volume was added), so it sorts first. */
export async function touchWork(id: string): Promise<void> {
  const work = await getWork(id);
  if (work) await saveWork({ ...work, updatedAt: Date.now() });
}
