import { describe, expect, it } from "vitest";
import { DAILY_DEFAULTS, dueDailyKinds, sanitizeDailySettings } from "./partner-daily.js";

const at = (iso: string) => new Date(iso);
const on = { ...DAILY_DEFAULTS, greetingEnabled: true, nightEnabled: true };

describe("sanitizeDailySettings", () => {
  it("defaults to everything off so nobody gets surprise notifications", () => {
    const settings = sanitizeDailySettings(undefined);
    expect(settings.greetingEnabled).toBe(false);
    expect(settings.nightEnabled).toBe(false);
  });

  it("keeps valid values and replaces invalid times", () => {
    const settings = sanitizeDailySettings({
      greetingEnabled: true,
      greetingTime: "6:5",
      nightTime: "21:45",
    });
    expect(settings.greetingEnabled).toBe(true);
    expect(settings.greetingTime).toBe("07:30");
    expect(settings.nightTime).toBe("21:45");
  });
});

describe("dueDailyKinds", () => {
  it("is not due before the chosen time", () => {
    expect(dueDailyKinds({ settings: on, state: {}, now: at("2026-10-02T07:10:00") })).toEqual([]);
  });

  it("is due from the chosen time and only once per day", () => {
    expect(dueDailyKinds({ settings: on, state: {}, now: at("2026-10-02T07:40:00") })).toEqual([
      "morning",
    ]);
    expect(
      dueDailyKinds({
        settings: on,
        state: { morningDate: "2026-10-02" },
        now: at("2026-10-02T07:55:00"),
      }),
    ).toEqual([]);
  });

  it("makes up a missed slot for a few hours, then skips it", () => {
    expect(dueDailyKinds({ settings: on, state: {}, now: at("2026-10-02T09:00:00") })).toEqual([
      "morning",
    ]);
    expect(dueDailyKinds({ settings: on, state: {}, now: at("2026-10-02T12:00:00") })).toEqual([]);
  });

  it("sends the night message at its own time and respects the enable flags", () => {
    expect(
      dueDailyKinds({
        settings: on,
        state: { morningDate: "2026-10-02" },
        now: at("2026-10-02T22:05:00"),
      }),
    ).toEqual(["night"]);
    expect(
      dueDailyKinds({
        settings: { ...on, nightEnabled: false },
        state: { morningDate: "2026-10-02" },
        now: at("2026-10-02T22:05:00"),
      }),
    ).toEqual([]);
  });
});
