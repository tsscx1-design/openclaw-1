import { describe, expect, it } from "vitest";
import {
  MAX_PANELS_PER_PAGE,
  PAGE_HEIGHT,
  PAGE_WIDTH,
  planPage,
  slotAspect,
} from "./manga-layout.js";

describe("planPage", () => {
  it("has a template for every panel count and keeps slots on the page", () => {
    for (let count = 1; count <= MAX_PANELS_PER_PAGE; count++) {
      for (const slant of [false, true]) {
        const page = planPage(count, { pageNo: 1, slant });
        expect(page.slots).toHaveLength(count);
        for (const s of page.slots) {
          expect(s.x).toBeGreaterThanOrEqual(0);
          expect(s.y).toBeGreaterThanOrEqual(0);
          expect(s.x + s.w).toBeLessThanOrEqual(PAGE_WIDTH);
          expect(s.y + s.h).toBeLessThanOrEqual(PAGE_HEIGHT);
          expect(s.w).toBeGreaterThan(100);
        }
      }
    }
  });

  it("puts the highlight panel in the biggest slot when a template allows it", () => {
    const page = planPage(5, { pageNo: 1, bigIndex: 2 });
    expect(page.big).toBe(2);
  });

  it("does not repeat the previous page's template", () => {
    const first = planPage(4, { pageNo: 1 });
    const second = planPage(4, { pageNo: 2, previousId: first.id });
    expect(second.id).not.toBe(first.id);
  });

  it("slants a two-panel row into polygons only when asked", () => {
    expect(planPage(4, { pageNo: 1, slant: false }).slots.some((s) => s.poly)).toBe(false);
    const slanted = planPage(4, { pageNo: 1, slant: true, previousId: "none" });
    expect(slanted.slots.filter((s) => s.poly)).toHaveLength(2);
  });

  it("names the drawing shape from the slot", () => {
    expect(slotAspect({ x: 0, y: 0, w: 1600, h: 600 })).toBe("landscape");
    expect(slotAspect({ x: 0, y: 0, w: 600, h: 1200 })).toBe("portrait");
    expect(slotAspect({ x: 0, y: 0, w: 800, h: 800 })).toBe("square");
  });
});
