import { describe, expect, it } from "vitest";
import { birthdayFromFacts, chooseProactiveReason } from "./partner-proactive.js";
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
        voicePitch: 0.1,
        injected: "x",
      }),
    ).toEqual({
      replyLength: "short",
      temperature: 1.2,
      proactive: true,
      dailyTopics: ["天気", "AIニュース"],
      voicePitch: 0.5,
    });
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
