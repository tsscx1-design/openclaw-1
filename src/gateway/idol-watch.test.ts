import { describe, expect, it } from "vitest";
import { compareNames } from "./idol-watch.js";

describe("who is new and who left", () => {
  const roster = ["大谷映美里", "大場 花菜", "音嶋莉沙"];

  it("finds the new names and the ones missing from the page, ignoring spaces and variant kanji", () => {
    const page = ["大谷 映美里", "大場花菜", "新人 太郎", "新人 太郎"];
    expect(compareNames(roster, page, { newOnly: false })).toEqual({
      added: ["新人 太郎"],
      left: ["音嶋莉沙"],
    });
  });

  it("does not report who left from a new-faces list or from a page with only a few names", () => {
    expect(
      compareNames(roster, ["新しい人A", "新しい人B", "新しい人C"], { newOnly: true }),
    ).toEqual({ added: ["新しい人A", "新しい人B", "新しい人C"], left: [] });
    expect(compareNames(roster, ["大谷映美里"], { newOnly: false })).toEqual({
      added: [],
      left: [],
    });
  });

  it("leaves out the names the user chose to ignore", () => {
    const page = ["大谷映美里", "大場花菜", "音嶋莉沙", "無視する人", "追加する人"];
    expect(compareNames(roster, page, { newOnly: false, dismissed: ["無視する人"] })).toEqual({
      added: ["追加する人"],
      left: [],
    });
    expect(
      compareNames(roster, ["A子", "B子", "C子"], { newOnly: false, dismissed: ["大谷映美里"] })
        .left,
    ).toEqual(["大場 花菜", "音嶋莉沙"]);
  });
});
