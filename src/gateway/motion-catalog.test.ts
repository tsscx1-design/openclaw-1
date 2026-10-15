import { describe, expect, it } from "vitest";
import { motionCatalog, poseGroupOf } from "./motion-catalog.js";
import { MOTION_OPTIONS, MOTION_TEMPLATES } from "./motion-comic.js";

describe("motion catalog", () => {
  it("lists every pose and every option exactly once, in groups", () => {
    const catalog = motionCatalog();
    expect(catalog.poses.length).toBe(Object.keys(MOTION_TEMPLATES).length);
    expect(catalog.options.flatMap((g) => g.items).length).toBe(Object.keys(MOTION_OPTIONS).length);
    expect(new Set(catalog.poses.map((p) => p.id)).size).toBe(catalog.poses.length);
    expect(catalog.poses.every((p) => p.group.length > 0)).toBe(true);
  });

  it("groups poses by the kind of play", () => {
    expect(poseGroupOf({ label: "騎乗位", position: "cowgirl position" })).toBe("騎乗位");
    expect(poseGroupOf({ label: "バック", position: "doggystyle, from behind" })).toBe(
      "後背位（バック）",
    );
    expect(poseGroupOf({ label: "ひとり", position: "x", act: "masturbation, solo" })).toBe(
      "ひとり",
    );
  });
});
