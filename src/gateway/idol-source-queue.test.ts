import { describe, expect, it } from "vitest";
import {
  cancelSourceJob,
  clearSourceQueue,
  enqueueSource,
  sourceQueue,
} from "./idol-source-queue.js";
import type { SourceFetcher } from "./idol-source.js";

const nothing: SourceFetcher = {
  list: async () => "<body>nothing</body>",
  profile: async () => "",
};

async function settle() {
  for (
    let i = 0;
    i < 400 && sourceQueue().some((job) => job.status === "waiting" || job.status === "running");
    i++
  ) {
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
}

describe("the import waiting line", () => {
  it("runs the lines one after the other, and an unreadable page ends as an error without stopping the next", async () => {
    const first = enqueueSource({
      group: "QueueA",
      url: "https://example.jp/a/",
      fetcher: nothing,
    });
    const second = enqueueSource({
      group: "QueueB",
      url: "https://example.jp/b/",
      fetcher: nothing,
    });
    expect(["waiting", "running"]).toContain(first.status);
    expect(second.status).toBe("waiting");
    expect(sourceQueue().map((job) => job.id)).toEqual([first.id, second.id]);
    await settle();
    const jobs = sourceQueue();
    expect(jobs.map((job) => job.status)).toEqual(["error", "error"]);
    expect(jobs[0]!.error).toMatch(/メンバー一覧のページ/);
    expect(jobs[0]!.finishedAt).toBeDefined();
    expect(clearSourceQueue()).toEqual([]);
  });

  it("refuses the same address twice while it waits, and a waiting line can be taken out", async () => {
    const slow: SourceFetcher = {
      list: async () => {
        await new Promise((resolve) => setTimeout(resolve, 200));
        return "<body>nothing</body>";
      },
      profile: async () => "",
    };
    enqueueSource({ group: "QueueC", url: "https://example.jp/c/", fetcher: slow });
    const waiting = enqueueSource({ group: "QueueD", url: "https://example.jp/d/", fetcher: slow });
    expect(() =>
      enqueueSource({ group: "QueueD", url: "https://example.jp/d/", fetcher: slow }),
    ).toThrow(/すでに/);
    cancelSourceJob(waiting.id);
    expect(sourceQueue().find((job) => job.id === waiting.id)?.status).toBe("cancelled");
    await settle();
    expect(sourceQueue().find((job) => job.group === "QueueC")?.status).toBe("error");
    clearSourceQueue();
  });

  it("needs a group and an address", () => {
    expect(() => enqueueSource({ group: "", url: "https://example.jp/" })).toThrow();
    expect(() => enqueueSource({ group: "X", url: "" })).toThrow();
  });

  it("runs lines of different sites at the same time, and lines of one site one after the other", async () => {
    const slow: SourceFetcher = {
      list: async () => {
        await new Promise((resolve) => setTimeout(resolve, 600));
        return "<body>nothing</body>";
      },
      profile: async () => "",
    };
    enqueueSource({ group: "ParA", url: "https://site-a.example/list/", fetcher: slow });
    enqueueSource({ group: "ParB", url: "https://site-b.example/list/", fetcher: slow });
    enqueueSource({ group: "ParA2", url: "https://site-a.example/other/", fetcher: slow });
    // a moment later: the two sites are both running, the second page of site A is still waiting
    await new Promise((resolve) => setTimeout(resolve, 150));
    const now = sourceQueue();
    expect(now.find((job) => job.group === "ParA")?.status).toBe("running");
    expect(now.find((job) => job.group === "ParB")?.status).toBe("running");
    expect(now.find((job) => job.group === "ParA2")?.status).toBe("waiting");
    await settle();
    expect(
      sourceQueue()
        .filter((job) => job.group.startsWith("Par"))
        .map((job) => job.status),
    ).toEqual(["error", "error", "error"]);
    clearSourceQueue();
  });
});
