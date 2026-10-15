import { describe, expect, it } from "vitest";
import {
  birthdayFromFacts,
  chooseProactiveReason,
  citedSites,
  tidyBrief,
  withRateLimitRetry,
} from "./partner-proactive.js";
import { sanitizePartnerSettings } from "./server-methods/partners.js";
import {
  finishSpecialistJob,
  listSpecialistJobs,
  resetSpecialistJobsForTest,
  startSpecialistJob,
} from "./specialist-jobs.js";

const at = (iso: string) => new Date(iso);

describe("chooseProactiveReason", () => {
  it("stays quiet at night", () => {
    expect(chooseProactiveReason({ now: at("2026-10-01T23:30:00"), state: {} })).toBeUndefined();
    expect(chooseProactiveReason({ now: at("2026-10-01T06:00:00"), state: {} })).toBeUndefined();
  });

  it("sends one morning greeting per day", () => {
    expect(chooseProactiveReason({ now: at("2026-10-01T08:30:00"), state: {} })).toBe("morning");
    expect(
      chooseProactiveReason({
        now: at("2026-10-01T09:00:00"),
        state: { lastBriefDate: "2026-10-01" },
      }),
    ).toBeUndefined();
  });

  it("checks in after a quiet day but not twice", () => {
    const now = at("2026-10-01T15:00:00");
    const lastChatAt = now.getTime() - 30 * 60 * 60_000;
    expect(chooseProactiveReason({ now, state: {}, lastChatAt })).toBe("checkin");
    expect(
      chooseProactiveReason({ now, state: { lastSentAt: now.getTime() - 60_000 }, lastChatAt }),
    ).toBeUndefined();
    expect(
      chooseProactiveReason({ now, state: {}, lastChatAt: now.getTime() - 60_000 }),
    ).toBeUndefined();
  });

  it("celebrates a birthday once a year", () => {
    const now = at("2026-05-03T12:00:00");
    const birthday = { month: 5, day: 3 };
    expect(chooseProactiveReason({ now, state: {}, birthday })).toBe("birthday");
    expect(
      chooseProactiveReason({
        now,
        state: { lastBirthdayYear: 2026, lastBriefDate: "2026-05-03" },
        birthday,
      }),
    ).toBeUndefined();
  });
});

describe("birthdayFromFacts", () => {
  it("reads common Japanese date formats", () => {
    expect(birthdayFromFacts([{ fact: "誕生日は5月3日" }])).toEqual({ month: 5, day: 3 });
    expect(birthdayFromFacts([{ fact: "誕生日: 12/24" }])).toEqual({ month: 12, day: 24 });
    expect(birthdayFromFacts([{ fact: "猫が好き" }])).toBeUndefined();
  });
});

describe("sanitizePartnerSettings", () => {
  it("keeps known in-range values only", () => {
    expect(
      sanitizePartnerSettings({
        replyLength: "short",
        emoji: "loud",
        temperature: 5,
        proactive: true,
        dailyTopics: ["天気", "", 3, "AIニュース"],
        newsDigest: true,
        voicePitch: 0.1,
        injected: "x",
      }),
    ).toEqual({
      replyLength: "short",
      temperature: 1.2,
      proactive: true,
      dailyTopics: ["天気", "AIニュース"],
      newsDigest: true,
      voicePitch: 0.5,
    });
  });
});

describe("withRateLimitRetry", () => {
  it("waits out a rate limit but not other errors", async () => {
    let calls = 0;
    const flaky = async () => {
      calls += 1;
      if (calls < 3) throw new Error("Gemini API error (429): You exceeded your current quota");
      return "ok";
    };
    expect(await withRateLimitRetry(flaky, [1, 1])).toBe("ok");
    expect(calls).toBe(3);
    await expect(
      withRateLimitRetry(async () => {
        throw new Error("HTTP 500");
      }, [1, 1]),
    ).rejects.toThrow("HTTP 500");
    await expect(
      withRateLimitRetry(async () => {
        throw new Error("429 Too Many Requests");
      }, [1]),
    ).rejects.toThrow("429");
  });
});

describe("tidyBrief", () => {
  it("drops the summarizer's preamble and uses ■ headings", () => {
    expect(
      tidyBrief(
        "編集者として、資料に基づきまとめます。\n\n## 画像生成AI 新モデル\n- FLUX 3 が公開\n出典: a.jp\n\n\n## 音楽生成AI\n- Suno v5.5",
      ),
    ).toBe("■画像生成AI 新モデル\n- FLUX 3 が公開\n出典: a.jp\n\n■音楽生成AI\n- Suno v5.5");
    expect(tidyBrief("- 晴れ\n- 阪神が勝利")).toBe("- 晴れ\n- 阪神が勝利");
  });
});

describe("citedSites", () => {
  it("names Gemini citations by title and listed results by host", () => {
    expect(
      citedSites({
        citations: [
          {
            title: "itmedia.co.jp",
            url: "https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc",
          },
          { title: "itmedia.co.jp", url: "https://vertexaisearch.cloud.google.com/x" },
          { title: "gigazine.net", url: "https://vertexaisearch.cloud.google.com/y" },
        ],
      }),
    ).toEqual(["itmedia.co.jp", "gigazine.net"]);
    expect(
      citedSites({ results: [{ title: "新モデル公開", url: "https://www.example.com/news/1" }] }),
    ).toEqual(["example.com"]);
    expect(citedSites(undefined)).toEqual([]);
  });
});

describe("specialist jobs", () => {
  it("lists newest first with outcome", () => {
    resetSpecialistJobsForTest();
    const first = startSpecialistJob({ kind: "delegation", title: "要約", workerName: "Qwen" });
    const second = startSpecialistJob({
      kind: "partner-create",
      title: "作成",
      workerName: "Gemma",
    });
    finishSpecialistJob(first, { resultPreview: "done" });
    finishSpecialistJob(second, { error: "boom" });
    expect(listSpecialistJobs().map((j) => [j.title, j.status])).toEqual([
      ["作成", "failed"],
      ["要約", "completed"],
    ]);
  });
});
