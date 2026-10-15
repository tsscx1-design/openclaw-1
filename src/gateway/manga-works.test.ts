import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { loadBook, removeBook, saveBook, type SavedBook } from "./manga-book-store.js";
import { createWork, listWorks, moveVolume, renameWork } from "./manga-works.js";

const home = path.join(os.tmpdir(), `manga-works-test-${process.pid}`);
const previous = process.env.OPENCLAW_HOME;

afterEach(async () => {
  if (previous === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previous;
  await fs.rm(home, { recursive: true, force: true });
});

const book = (jobId: string, patch: Partial<SavedBook> = {}): SavedBook => ({
  jobId,
  volume: 1,
  title: `題名${jobId}`,
  request: "お題",
  cast: [],
  outline: [],
  pages: [],
  summary: "",
  hook: "",
  history: [],
  createdAt: Date.now(),
  ...patch,
});

describe("manga works", () => {
  it("lists a work with its volumes in order and drops a work with none", async () => {
    process.env.OPENCLAW_HOME = home;
    const work = await createWork("雨の日");
    await saveBook(book("aaaaaa111111", { workId: work.id, volume: 2 }));
    await saveBook(book("bbbbbb222222", { workId: work.id, volume: 1 }));
    await createWork("空の作品");
    const works = await listWorks();
    expect(works).toHaveLength(1);
    expect(works[0]?.title).toBe("雨の日");
    expect(works[0]?.volumes.map((v) => v.volume)).toEqual([1, 2]);
  });

  it("renames a work and refuses an empty name", async () => {
    process.env.OPENCLAW_HOME = home;
    const work = await createWork("仮の名前");
    expect((await renameWork(work.id, "  新しい名前 ")).title).toBe("新しい名前");
    await expect(renameWork(work.id, "   ")).rejects.toThrow(/作品名/);
  });

  it("moves a volume to another work or into a new one, and a emptied work disappears", async () => {
    process.env.OPENCLAW_HOME = home;
    const one = await createWork("一つ目");
    const two = await createWork("二つ目");
    const first = book("aaaaaa111111", { workId: one.id });
    await saveBook(first);
    await saveBook(book("bbbbbb222222", { workId: two.id }));
    await moveVolume("aaaaaa111111", { workId: two.id }, first);
    expect((await loadBook("aaaaaa111111"))?.workId).toBe(two.id);
    expect((await listWorks()).map((w) => w.id)).toEqual([two.id]);
    const fresh = await moveVolume("bbbbbb222222", { title: "独立" }, book("bbbbbb222222"));
    expect(fresh.title).toBe("独立");
    expect(await listWorks()).toHaveLength(2);
    await removeBook("bbbbbb222222");
    expect((await listWorks()).map((w) => w.id)).toEqual([two.id]);
  });
});
