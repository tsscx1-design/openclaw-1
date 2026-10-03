import { describe, expect, it } from "vitest";
import { mergeConsecutiveRoles } from "./partners.js";

describe("mergeConsecutiveRoles", () => {
  it("joins back-to-back turns of one role so strict chat templates accept a group transcript", () => {
    expect(
      mergeConsecutiveRoles([
        { role: "system", content: "persona" },
        { role: "user", content: "[ユーザー]: 旅行どこ行く？" },
        { role: "user", content: "[シオン]: 山がいい！" },
        { role: "assistant", content: "海かな" },
        { role: "user", content: "[アリス]: いいね" },
      ]),
    ).toEqual([
      { role: "system", content: "persona" },
      { role: "user", content: "[ユーザー]: 旅行どこ行く？\n\n[シオン]: 山がいい！" },
      { role: "assistant", content: "海かな" },
      { role: "user", content: "[アリス]: いいね" },
    ]);
  });
});
