import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { characterDir, getCharacter, listCharacters, saveCharacter } from "./comic-characters.js";

// Folders of the character library: a name, an icon picture and an order that the user sets. A
// character is in at most one folder (folderId on the character; none = "unsorted") and has its
// own place in that folder (libraryOrder). Moving or ordering does not change when she was edited.

export type CharacterFolder = { id: string; name: string; order: number; hasIcon: boolean };

const MAX_FOLDERS = 200;
const MAX_ICON_BYTES = 2 * 1024 * 1024;

const baseDir = () => path.dirname(characterDir());
const foldersFile = () => path.join(baseDir(), "character-folders.json");
const iconDir = () => path.join(baseDir(), "folder-icons");
const iconFile = (id: string) => path.join(iconDir(), `${id}.png`);
const isFolderId = (id: unknown): id is string =>
  typeof id === "string" && /^folder-[a-f0-9]{8}$/.test(id);

async function load(): Promise<Array<{ id: string; name: string; order: number }>> {
  try {
    const parsed = JSON.parse(await fs.readFile(foldersFile(), "utf-8")) as unknown;
    return Array.isArray(parsed)
      ? parsed.filter(
          (row): row is { id: string; name: string; order: number } =>
            isFolderId((row as { id?: unknown })?.id) &&
            typeof (row as { name?: unknown }).name === "string",
        )
      : [];
  } catch {
    return [];
  }
}

async function save(rows: Array<{ id: string; name: string; order: number }>): Promise<void> {
  await fs.mkdir(baseDir(), { recursive: true });
  const tmp = `${foldersFile()}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(rows, null, 2), "utf-8");
  await fs.rename(tmp, foldersFile());
}

const cleanName = (raw: unknown): string =>
  typeof raw === "string" ? raw.trim().slice(0, 40) : "";

export async function listFolders(): Promise<CharacterFolder[]> {
  const rows = (await load()).toSorted((a, b) => a.order - b.order);
  const out: CharacterFolder[] = [];
  for (const row of rows) {
    out.push({
      ...row,
      hasIcon: await fs.access(iconFile(row.id)).then(
        () => true,
        () => false,
      ),
    });
  }
  return out;
}

export async function createFolder(nameRaw: unknown): Promise<CharacterFolder> {
  const name = cleanName(nameRaw);
  if (!name) throw new Error("フォルダの名前を入れてください");
  const rows = await load();
  if (rows.length >= MAX_FOLDERS) throw new Error("フォルダが多すぎます");
  const folder = {
    id: `folder-${randomBytes(4).toString("hex")}`,
    name,
    order: rows.reduce((max, row) => Math.max(max, row.order), 0) + 10,
  };
  await save([...rows, folder]);
  return { ...folder, hasIcon: false };
}

/** Renames a folder and/or sets its icon (a picture, base64); an empty icon text removes the icon. */
export async function updateFolder(
  id: unknown,
  patch: { name?: unknown; iconBase64?: unknown },
): Promise<CharacterFolder> {
  if (!isFolderId(id)) throw new Error("フォルダが見つかりません");
  const rows = await load();
  const row = rows.find((item) => item.id === id);
  if (!row) throw new Error("フォルダが見つかりません");
  if (patch.name !== undefined) {
    const name = cleanName(patch.name);
    if (!name) throw new Error("フォルダの名前を入れてください");
    row.name = name;
  }
  if (typeof patch.iconBase64 === "string") {
    if (patch.iconBase64 === "") {
      await fs.rm(iconFile(id), { force: true });
    } else {
      const data = Buffer.from(patch.iconBase64, "base64");
      if (data.length === 0 || data.length > MAX_ICON_BYTES)
        throw new Error("アイコンの画像が大きすぎます（2MBまで）");
      await fs.mkdir(iconDir(), { recursive: true });
      await fs.writeFile(iconFile(id), data);
    }
  }
  await save(rows);
  return {
    ...row,
    hasIcon: await fs.access(iconFile(id)).then(
      () => true,
      () => false,
    ),
  };
}

/** Deletes a folder; its characters become unsorted (they are not deleted). */
export async function deleteFolder(id: unknown): Promise<boolean> {
  if (!isFolderId(id)) return false;
  const rows = await load();
  if (!rows.some((row) => row.id === id)) return false;
  await save(rows.filter((row) => row.id !== id));
  await fs.rm(iconFile(id), { force: true });
  for (const character of await listCharacters()) {
    if (character.folderId === id) await saveCharacter({ ...character, folderId: undefined });
  }
  return true;
}

export async function reorderFolders(ids: unknown): Promise<void> {
  if (!Array.isArray(ids)) return;
  const rows = await load();
  const order = new Map(ids.filter(isFolderId).map((id, index) => [id, index * 10]));
  let next = order.size * 10;
  for (const row of rows) row.order = order.get(row.id) ?? (next += 10);
  await save(rows);
}

export async function folderIconBase64(id: unknown): Promise<string | undefined> {
  if (!isFolderId(id)) return undefined;
  return await fs.readFile(iconFile(id), "base64").catch(() => undefined);
}

/** Puts a character in a folder ("" = unsorted), at the end of it. */
export async function moveCharacter(characterId: string, folderId: string): Promise<void> {
  const character = await getCharacter(characterId);
  if (!character) throw new Error("そのキャラが見つかりません");
  if (folderId !== "" && !(await load()).some((row) => row.id === folderId))
    throw new Error("フォルダが見つかりません");
  const same = (await listCharacters()).filter((c) => (c.folderId ?? "") === folderId);
  const last = same.reduce((max, c) => Math.max(max, c.libraryOrder ?? 0), 0);
  await saveCharacter({ ...character, folderId: folderId || undefined, libraryOrder: last + 10 });
}

/** Sets the order of characters: the first id comes first. */
export async function reorderCharacters(ids: unknown): Promise<void> {
  if (!Array.isArray(ids)) return;
  let index = 0;
  for (const id of ids) {
    if (typeof id !== "string") continue;
    const character = await getCharacter(id);
    if (character) await saveCharacter({ ...character, libraryOrder: (index += 1) * 10 });
  }
}
