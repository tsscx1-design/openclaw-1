import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  isBookJobId,
  loadBook,
  previouslyText,
  removeBook,
  saveBook,
  type SavedBook,
} from "./manga-book-store.js";

const book = (patch: Partial<SavedBook> = {}): SavedBook => ({
  jobId: "abc123def456",
  volume: 1,
  title: "雨の日",
  request: "雨宿りで出会う話",
  cast: [{ name: "ルナ", look: "1girl, short black hair", profile: "明るい" }],
  outline: ["雨宿り", "出会い"],
  pages: [],
  summary: "ルナは雨宿りでソラと出会った。",
  hook: "ソラが傘を忘れて帰る。",
  history: [],
  createdAt: 1,
  ...patch,
});

const home = path.join(os.tmpdir(), `book-store-test-${process.pid}`);
const previous = process.env.OPENCLAW_HOME;

afterEach(async () => {
  if (previous === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previous;
  await fs.rm(home, { recursive: true, force: true });
});

describe("manga book store", () => {
  it("rejects ids that are not job ids", () => {
    expect(isBookJobId("abc123def456")).toBe(true);
    expect(isBookJobId("../x")).toBe(false);
    expect(isBookJobId("")).toBe(false);
  });

  it("saves, loads and removes a volume", async () => {
    process.env.OPENCLAW_HOME = home;
    await saveBook(book());
    const loaded = await loadBook("abc123def456");
    expect(loaded?.cast[0]?.look).toBe("1girl, short black hair");
    expect(loaded?.hook).toBe("ソラが傘を忘れて帰る。");
    await removeBook("abc123def456");
    expect(await loadBook("abc123def456")).toBeUndefined();
  });

  it("tells a sequel the story so far and how the last volume ended", () => {
    const text = previouslyText(
      book({
        volume: 2,
        title: "晴れの日",
        summary: "二人は傘を返しに行く。",
        history: [{ volume: 1, title: "雨の日", summary: "ルナは雨宿りでソラと出会った。" }],
      }),
    );
    expect(text).toContain("第1巻「雨の日」");
    expect(text).toContain("第2巻「晴れの日」");
    expect(text).toContain("前の巻の終わり方: ソラが傘を忘れて帰る。");
  });
});
