import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { partnerFile, readJsonFile, writeJsonFile } from "./partner-store.js";
import {
  SPECIALIST_WORKERS,
  resolveModelPath,
  startSpecialistWorker,
  waitForWorkerReady,
} from "./specialist-router.js";

// Photo album: the user shares photos with a partner; the local vision worker (Gemma 4 E4B + mmproj)
// describes each one so the partner can talk about it. Images stay on this PC.

const MAX_IMAGE_BYTES = 6 * 1024 * 1024;
const MAX_ALBUM_ITEMS = 200;
const ALLOWED_MIME: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};
const ITEM_ID_PATTERN = /^[a-f0-9]{12}$/;
const VISION_WORKER_KEY = "gemma";

export type AlbumItem = {
  id: string;
  file: string;
  mime: string;
  addedAt: string;
  /** Plain description of the photo from the vision model; empty until captioning finishes. */
  caption: string;
  /** Short in-character reaction written by the partner. */
  comment: string;
  captionStatus: "pending" | "done" | "error";
  captionError?: string;
};

function albumDir(partnerId: string): string {
  return partnerFile(partnerId, "album");
}

function indexFile(partnerId: string): string {
  return path.join(albumDir(partnerId), "index.json");
}

export function isValidAlbumItemId(id: unknown): id is string {
  return typeof id === "string" && ITEM_ID_PATTERN.test(id);
}

export async function listAlbum(partnerId: string): Promise<AlbumItem[]> {
  const items = await readJsonFile<AlbumItem[]>(indexFile(partnerId), []);
  return Array.isArray(items) ? items : [];
}

async function saveAlbum(partnerId: string, items: AlbumItem[]): Promise<void> {
  await writeJsonFile(indexFile(partnerId), items);
}

// Serialises read-modify-write of each album index (captioning runs in the background).
const albumLocks = new Map<string, Promise<unknown>>();

function withAlbumLock<T>(partnerId: string, task: () => Promise<T>): Promise<T> {
  const previous = albumLocks.get(partnerId) ?? Promise.resolve();
  const next = previous.then(task, task);
  albumLocks.set(
    partnerId,
    next.catch(() => undefined),
  );
  return next;
}

export function parseImageDataUri(input: string): { mime: string; bytes: Buffer } {
  const match = /^data:(image\/[a-z]+);base64,([A-Za-z0-9+/=\s]+)$/.exec(input.trim());
  if (!match || !ALLOWED_MIME[match[1] ?? ""]) {
    throw new Error("Unsupported image (use JPEG, PNG or WebP)");
  }
  const bytes = Buffer.from((match[2] ?? "").replace(/\s+/g, ""), "base64");
  if (bytes.length === 0) throw new Error("Image is empty");
  if (bytes.length > MAX_IMAGE_BYTES) throw new Error("Image is too large (max 6 MB)");
  return { mime: match[1] as string, bytes };
}

export async function addAlbumPhoto(partnerId: string, imageDataUri: string): Promise<AlbumItem> {
  const { mime, bytes } = parseImageDataUri(imageDataUri);
  const id = randomBytes(6).toString("hex");
  const file = `${id}.${ALLOWED_MIME[mime]}`;
  await fs.mkdir(albumDir(partnerId), { recursive: true });
  await fs.writeFile(path.join(albumDir(partnerId), file), bytes);
  const item: AlbumItem = {
    id,
    file,
    mime,
    addedAt: new Date().toISOString(),
    caption: "",
    comment: "",
    captionStatus: "pending",
  };
  await withAlbumLock(partnerId, async () => {
    const items = await listAlbum(partnerId);
    if (items.length >= MAX_ALBUM_ITEMS) {
      await fs.rm(path.join(albumDir(partnerId), file), { force: true });
      throw new Error(`Album is full (max ${MAX_ALBUM_ITEMS} photos)`);
    }
    await saveAlbum(partnerId, [item, ...items]);
  });
  return item;
}

export async function readAlbumPhoto(
  partnerId: string,
  itemId: string,
): Promise<{ item: AlbumItem; imageDataUri: string } | undefined> {
  const item = (await listAlbum(partnerId)).find((entry) => entry.id === itemId);
  if (!item) return undefined;
  const bytes = await fs.readFile(path.join(albumDir(partnerId), item.file));
  return { item, imageDataUri: `data:${item.mime};base64,${bytes.toString("base64")}` };
}

export async function deleteAlbumPhoto(partnerId: string, itemId: string): Promise<boolean> {
  return await withAlbumLock(partnerId, async () => {
    const items = await listAlbum(partnerId);
    const target = items.find((entry) => entry.id === itemId);
    if (!target) return false;
    await fs.rm(path.join(albumDir(partnerId), target.file), { force: true });
    await saveAlbum(
      partnerId,
      items.filter((entry) => entry.id !== itemId),
    );
    return true;
  });
}

export async function updateAlbumItem(
  partnerId: string,
  itemId: string,
  patch: Partial<AlbumItem>,
): Promise<void> {
  await withAlbumLock(partnerId, async () => {
    const items = await listAlbum(partnerId);
    await saveAlbum(
      partnerId,
      items.map((entry) => (entry.id === itemId ? { ...entry, ...patch, id: entry.id } : entry)),
    );
  });
}

export async function visionReady(): Promise<{ ready: boolean; missing: string[] }> {
  const worker = SPECIALIST_WORKERS.find((entry) => entry.key === VISION_WORKER_KEY);
  const missing: string[] = [];
  for (const file of [worker?.modelFile ?? "", worker?.mmprojFile ?? ""]) {
    try {
      if (!file) throw new Error("not configured");
      await fs.access(resolveModelPath(file));
    } catch {
      missing.push(path.basename(file) || "mmproj");
    }
  }
  return { ready: missing.length === 0, missing };
}

/** Asks the vision worker for a short Japanese description of the photo. */
export async function describePhoto(imageDataUri: string): Promise<string> {
  const worker = SPECIALIST_WORKERS.find((entry) => entry.key === VISION_WORKER_KEY);
  if (!worker) throw new Error("Vision worker is not configured");
  const state = await visionReady();
  if (!state.ready)
    throw new Error(`Vision model is not installed (missing: ${state.missing.join(", ")})`);
  const started = await startSpecialistWorker(VISION_WORKER_KEY);
  if (!started.success) throw new Error(started.message);
  if (!(await waitForWorkerReady(worker.port)))
    throw new Error("Vision worker did not become ready");
  const response = await fetch(`http://127.0.0.1:${worker.port}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      messages: [
        {
          role: "user",
          content: [
            {
              type: "text",
              text: "この写真に何が写っているかを、日本語で2〜3文で具体的に説明してください。場所・人や物・雰囲気・色を含め、推測で話を作らないでください。",
            },
            { type: "image_url", image_url: { url: imageDataUri } },
          ],
        },
      ],
      max_tokens: 300,
      temperature: 0.2,
    }),
    signal: AbortSignal.timeout(10 * 60_000),
  });
  if (!response.ok) {
    throw new Error(
      `Vision worker responded with HTTP ${response.status}: ${(await response.text()).slice(0, 300)}`,
    );
  }
  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  const text = data.choices?.[0]?.message?.content?.trim() ?? "";
  if (!text) throw new Error("Vision worker returned no description");
  return text;
}
