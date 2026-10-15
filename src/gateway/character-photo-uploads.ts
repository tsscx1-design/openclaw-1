import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const MAX_PHOTO_BYTES = 6 * 1024 * 1024;

function uploadDir(id: string): string {
  if (!ID.test(id)) throw new Error("写真の送信IDが無効です");
  return path.join(resolveOpenClawHome(), "media", "comics", "photo-uploads", id);
}

async function removeExpiredUploads(): Promise<void> {
  const root = path.dirname(uploadDir("00000000-0000-0000-0000-000000000000"));
  const entries = await fs.readdir(root, { withFileTypes: true }).catch(() => []);
  const cutoff = Date.now() - 24 * 60 * 60 * 1000;
  for (const entry of entries) {
    if (!entry.isDirectory() || !ID.test(entry.name)) continue;
    const dir = path.join(root, entry.name);
    const info = await fs.stat(dir).catch(() => undefined);
    if (info && info.mtimeMs < cutoff) await fs.rm(dir, { recursive: true, force: true });
  }
}

export async function saveCharacterPhotoUpload(
  id: string,
  index: number,
  encoded: string,
): Promise<void> {
  if (!Number.isSafeInteger(index) || index < 0 || index > 100_000) {
    throw new Error("写真の番号が無効です");
  }
  if (encoded.length > (MAX_PHOTO_BYTES * 4) / 3 + 16 || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) {
    throw new Error("写真が大きすぎるか、画像データが無効です（6MBまで）");
  }
  const bytes = Buffer.from(encoded, "base64");
  if (bytes.length > MAX_PHOTO_BYTES || bytes.length < 16)
    throw new Error("写真のサイズが無効です");
  if (index === 0) await removeExpiredUploads();
  const dir = uploadDir(id);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(path.join(dir, `${index}.image`), bytes);
}

export async function characterPhotoUploads(id: string, count: number): Promise<string[]> {
  if (!Number.isSafeInteger(count) || count < 1 || count > 100_001) {
    throw new Error("写真の枚数が無効です");
  }
  const dir = uploadDir(id);
  const photos = Array.from({ length: count }, (_, index) => path.join(dir, `${index}.image`));
  for (const photo of photos) await fs.access(photo);
  return photos;
}

export async function removeCharacterPhotoUploads(id: string): Promise<void> {
  await fs.rm(uploadDir(id), { recursive: true, force: true });
}
