import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let home = "";
vi.mock("./partner-store.js", () => ({ resolveOpenClawHome: () => home }));

const {
  createFolder,
  deleteFolder,
  folderIconBase64,
  listFolders,
  moveCharacter,
  reorderCharacters,
  reorderFolders,
  updateFolder,
} = await import("./character-folders.js");
const { getCharacter, saveCharacter } = await import("./comic-characters.js");

const character = (id: string, updatedAt: number) =>
  ({ id, name: id, look: "", profile: "", createdAt: 1, updatedAt }) as never;

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "folders-test-"));
});
afterEach(async () => {
  await fs.rm(home, { recursive: true, force: true });
});

describe("character folders", () => {
  it("creates, renames, orders and deletes folders; an icon can be set and removed", async () => {
    const a = await createFolder("  お気に入り  ");
    const b = await createFolder("敵キャラ");
    expect((await listFolders()).map((f) => f.name)).toEqual(["お気に入り", "敵キャラ"]);
    await reorderFolders([b.id, a.id]);
    expect((await listFolders()).map((f) => f.name)).toEqual(["敵キャラ", "お気に入り"]);
    await updateFolder(a.id, { name: "好き", iconBase64: Buffer.from("png").toString("base64") });
    expect((await listFolders()).find((f) => f.id === a.id)).toMatchObject({
      name: "好き",
      hasIcon: true,
    });
    expect(await folderIconBase64(a.id)).toBe(Buffer.from("png").toString("base64"));
    await updateFolder(a.id, { iconBase64: "" });
    expect((await listFolders()).find((f) => f.id === a.id)?.hasIcon).toBe(false);
    await expect(createFolder("   ")).rejects.toThrow();
    expect(await deleteFolder(a.id)).toBe(true);
    expect((await listFolders()).map((f) => f.id)).toEqual([b.id]);
  });

  it("moves characters into a folder, orders them, and frees them when the folder is deleted", async () => {
    const folder = await createFolder("組");
    await saveCharacter(character("chara-00000001", 100));
    await saveCharacter(character("chara-00000002", 200));
    await moveCharacter("chara-00000001", folder.id);
    await moveCharacter("chara-00000002", folder.id);
    // moving does not change when she was edited
    expect((await getCharacter("chara-00000001"))?.updatedAt).toBe(100);
    expect((await getCharacter("chara-00000001"))?.folderId).toBe(folder.id);
    await reorderCharacters(["chara-00000002", "chara-00000001"]);
    const first = (await getCharacter("chara-00000002"))?.libraryOrder ?? 0;
    const second = (await getCharacter("chara-00000001"))?.libraryOrder ?? 0;
    expect(first).toBeLessThan(second);
    await expect(moveCharacter("chara-00000001", "folder-ffffffff")).rejects.toThrow();
    await deleteFolder(folder.id);
    expect((await getCharacter("chara-00000001"))?.folderId).toBeUndefined();
  });
});
