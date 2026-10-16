import { describe, expect, it } from "vitest";
import { normalizeAffect } from "./partner-affect.js";
import {
  parseSelfiePlan,
  SELFIE_GAP_MS,
  selfieDayKey,
  selfieHourAllowed,
  shouldMakeVideo,
  shouldSendSelfie,
} from "./partner-selfie.js";

const at = (hour: number, minute = 0) => new Date(2026, 9, 16, hour, minute);
const always = () => 0;

describe("selfie schedule", () => {
  it("sends from 8:00 until 2:00", () => {
    expect(selfieHourAllowed(7)).toBe(false);
    expect(selfieHourAllowed(8)).toBe(true);
    expect(selfieHourAllowed(23)).toBe(true);
    expect(selfieHourAllowed(1)).toBe(true);
    expect(selfieHourAllowed(2)).toBe(false);
  });

  it("counts the hours after midnight to the evening before", () => {
    expect(selfieDayKey(new Date(2026, 9, 17, 1, 30))).toBe(selfieDayKey(at(20)));
    expect(selfieDayKey(new Date(2026, 9, 17, 9))).not.toBe(selfieDayKey(at(20)));
  });

  it("keeps the daily count, the gap and the quiet time", () => {
    const now = at(21);
    const base = { now, perDay: 2, busy: false, random: always };
    expect(shouldSendSelfie({ ...base, state: {} })).toBe(true);
    expect(shouldSendSelfie({ ...base, state: { date: selfieDayKey(now), count: 2 } })).toBe(false);
    // Yesterday's count does not matter.
    expect(shouldSendSelfie({ ...base, state: { date: "2026-10-15", count: 2 } })).toBe(true);
    expect(
      shouldSendSelfie({ ...base, state: { lastAt: now.getTime() - SELFIE_GAP_MS + 60_000 } }),
    ).toBe(false);
    expect(shouldSendSelfie({ ...base, state: {}, lastChatAt: now.getTime() - 5 * 60_000 })).toBe(
      false,
    );
    expect(shouldSendSelfie({ ...base, state: {}, busy: true })).toBe(false);
    expect(shouldSendSelfie({ ...base, state: {}, random: () => 0.9 })).toBe(false);
    expect(shouldSendSelfie({ ...base, now: at(4), state: {} })).toBe(false);
  });

  it("makes a film more often while she is aroused", () => {
    const calm = normalizeAffect({ arousal: 10 } as never);
    const hot = normalizeAffect({ arousal: 80 } as never);
    expect(shouldMakeVideo(calm, () => 0.3)).toBe(false);
    expect(shouldMakeVideo(hot, () => 0.3)).toBe(true);
  });
});

describe("selfie plan", () => {
  it("reads the caption, the photo and the motion", () => {
    const text =
      '```json\n{"caption":"今お風呂上がり…見て？","photo":"湯上がりのバスタオル姿、鏡越しの自撮り","motion":"髪を拭きながら微笑む"}\n```';
    expect(parseSelfiePlan(text, true)).toEqual({
      caption: "今お風呂上がり…見て？",
      photo: "湯上がりのバスタオル姿、鏡越しの自撮り",
      motion: "髪を拭きながら微笑む",
    });
    expect(parseSelfiePlan(text, false)?.motion).toBeUndefined();
  });

  it("gives up when the caption or the photo is missing", () => {
    expect(parseSelfiePlan('{"caption":"やっほー"}', false)).toBeUndefined();
    expect(parseSelfiePlan("写真を送るね", false)).toBeUndefined();
  });
});
