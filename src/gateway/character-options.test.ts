import { describe, expect, it } from "vitest";
import { CHARACTER_OPTION_CATALOG } from "./character-options.js";
import { characterOptionIds, characterOptionTags } from "./comic-characters.js";

describe("character options catalog", () => {
  const items = CHARACTER_OPTION_CATALOG.flatMap((g) => g.parts.flatMap((p) => p.items));

  it("has no repeated id and no empty label or tags", () => {
    expect(new Set(items.map((i) => i.id)).size).toBe(items.length);
    expect(items.every((i) => i.label && i.tags)).toBe(true);
  });

  it("keeps the ids saved characters already use", () => {
    const old =
      "navel_piercing ear_piercing nipple_piercing tongue_piercing lip_piercing womb_tattoo hickey tattoo body_writing tan_lines glasses collar leash eyepatch blindfold ball_gag cat_ears bunny_ears demon shaved pubic_hair".split(
        " ",
      );
    const ids = new Set(items.map((i) => i.id));
    expect(old.filter((id) => !ids.has(id))).toEqual([]);
  });

  it("keeps only the first one of a part that allows one", () => {
    expect(characterOptionIds("eyepatch_left, eyepatch_right, glasses, sunglasses")).toEqual([
      "eyepatch_left",
      "glasses",
      "sunglasses",
    ]);
    expect(characterOptionIds("wheelchair, crutches, hearing_aid")).toEqual([
      "wheelchair",
      "hearing_aid",
    ]);
  });

  it("gives the tags of the chosen options", () => {
    expect(characterOptionTags("missing_legs, wheelchair")).toContain("double leg amputee");
    expect(characterOptionTags("missing_legs, wheelchair")).toContain("wheelchair");
  });
});

describe("look notes", () => {
  it("are kept on the spec and shown in the summary on one line", async () => {
    const { cleanSpec, specSummary } = await import("./comic-characters.js");
    const spec = cleanSpec({ lookNotes: "手・腕：左手に傷跡 / 耳：長い" });
    expect(spec.lookNotes).toBe("手・腕：左手に傷跡 / 耳：長い");
    expect(specSummary(spec)).toContain("外見の追記: 手・腕：左手に傷跡 / 耳：長い");
  });
});
