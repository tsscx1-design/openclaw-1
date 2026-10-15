import { describe, expect, it } from "vitest";
import { waitForChatTurnEnd } from "./main-llm-idle.js";

describe("waitForChatTurnEnd", () => {
  it("waits until the model has been quiet for the whole window", async () => {
    // Busy for the first three polls (the agent writing its reply), then idle.
    let polls = 0;
    const isBusy = async () => ++polls <= 3;
    const quiet = await waitForChatTurnEnd({ quietMs: 30, maxMs: 2_000, pollMs: 5, isBusy });
    expect(quiet).toBe(true);
    expect(polls).toBeGreaterThan(3);
  });

  it("gives up when the model never goes quiet", async () => {
    const quiet = await waitForChatTurnEnd({
      quietMs: 30,
      maxMs: 60,
      pollMs: 5,
      isBusy: async () => true,
    });
    expect(quiet).toBe(false);
  });
});
