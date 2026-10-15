// Page layouts for multi-page manga: how many panels a page has decides the template, and a
// "large" panel (a highlight) is steered into the template's biggest slot. Slots are in page
// pixels; a slanted row of two panels gets polygons so the gutter between them runs diagonally.
// Reading order is Japanese: top to bottom, right to left within a row, so the first slot of a
// row is its rightmost.

export const PAGE_WIDTH = 1800;
export const PAGE_HEIGHT = 2560;
const MARGIN = 70;
const GUTTER = 28;

export type PageSlot = {
  x: number;
  y: number;
  w: number;
  h: number;
  /** Corners in page pixels, for a panel whose edges are not straight up and down. */
  poly?: Array<[number, number]>;
};

export type PageLayout = {
  id: string;
  slots: PageSlot[];
  /** Index of the biggest slot. */
  big: number;
};

type Row = { h: number; w: number[]; slant?: boolean };
type Template = { id: string; rows: Row[] };

const THIRD = 1 / 3;

const TEMPLATES: Template[] = [
  { id: "1a", rows: [{ h: 1, w: [1] }] },
  {
    id: "2a",
    rows: [
      { h: 0.62, w: [1] },
      { h: 0.38, w: [1] },
    ],
  },
  {
    id: "2b",
    rows: [
      { h: 0.4, w: [1] },
      { h: 0.6, w: [1] },
    ],
  },
  {
    id: "3a",
    rows: [
      { h: 0.34, w: [1] },
      { h: 0.33, w: [1] },
      { h: 0.33, w: [1] },
    ],
  },
  {
    id: "3b",
    rows: [
      { h: 0.45, w: [1] },
      { h: 0.55, w: [0.55, 0.45], slant: true },
    ],
  },
  {
    id: "3c",
    rows: [
      { h: 0.33, w: [0.5, 0.5], slant: true },
      { h: 0.67, w: [1] },
    ],
  },
  {
    id: "4a",
    rows: [
      { h: 0.3, w: [1] },
      { h: 0.38, w: [0.55, 0.45], slant: true },
      { h: 0.32, w: [1] },
    ],
  },
  {
    id: "4b",
    rows: [
      { h: 0.36, w: [0.45, 0.55], slant: true },
      { h: 0.34, w: [1] },
      { h: 0.3, w: [1] },
    ],
  },
  {
    id: "4c",
    rows: [
      { h: 0.5, w: [1] },
      { h: 0.25, w: [0.5, 0.5] },
      { h: 0.25, w: [1] },
    ],
  },
  {
    id: "5a",
    rows: [
      { h: 0.3, w: [1] },
      { h: 0.38, w: [0.55, 0.45], slant: true },
      { h: 0.32, w: [0.4, 0.6] },
    ],
  },
  {
    id: "5b",
    rows: [
      { h: 0.25, w: [0.5, 0.5] },
      { h: 0.5, w: [1] },
      { h: 0.25, w: [0.5, 0.5], slant: true },
    ],
  },
  {
    id: "5c",
    rows: [
      { h: 0.3, w: [0.6, 0.4] },
      { h: 0.4, w: [1] },
      { h: 0.3, w: [0.5, 0.5], slant: true },
    ],
  },
  {
    id: "6a",
    rows: [
      { h: 0.32, w: [0.6, 0.4], slant: true },
      { h: 0.36, w: [0.45, 0.55] },
      { h: 0.32, w: [0.55, 0.45] },
    ],
  },
  {
    id: "6b",
    rows: [
      { h: 0.22, w: [1] },
      { h: 0.26, w: [0.5, 0.5], slant: true },
      { h: 0.26, w: [0.5, 0.5] },
      { h: 0.26, w: [1] },
    ],
  },
  {
    id: "6c",
    rows: [
      { h: 0.22, w: [1] },
      { h: 0.26, w: [0.4, 0.6] },
      { h: 0.26, w: [0.6, 0.4], slant: true },
      { h: 0.26, w: [1] },
    ],
  },
  {
    id: "7a",
    rows: [
      { h: 0.25, w: [0.5, 0.5] },
      { h: 0.4, w: [0.4, 0.6], slant: true },
      { h: 0.35, w: [THIRD, THIRD, THIRD] },
    ],
  },
  {
    id: "7b",
    rows: [
      { h: 0.2, w: [1] },
      { h: 0.27, w: [0.5, 0.5] },
      { h: 0.27, w: [0.5, 0.5], slant: true },
      { h: 0.26, w: [0.5, 0.5] },
    ],
  },
];

export const MAX_PANELS_PER_PAGE = 7;

function build(template: Template, slant: boolean): PageSlot[] {
  const innerW = PAGE_WIDTH - 2 * MARGIN;
  const innerH = PAGE_HEIGHT - 2 * MARGIN - GUTTER * (template.rows.length - 1);
  const slots: PageSlot[] = [];
  let y = MARGIN;
  for (const row of template.rows) {
    const rowH = Math.round(innerH * row.h);
    const usable = innerW - GUTTER * (row.w.length - 1);
    let right = PAGE_WIDTH - MARGIN;
    if (slant && row.slant && row.w.length === 2) {
      const rowX = MARGIN;
      const rightW = Math.round(usable * row.w[0]!);
      const split = rowX + innerW - rightW - GUTTER / 2;
      const lean = Math.min(90, Math.round(rowH * 0.12));
      const rightPoly: Array<[number, number]> = [
        [split + lean + GUTTER / 2, y],
        [rowX + innerW, y],
        [rowX + innerW, y + rowH],
        [split - lean + GUTTER / 2, y + rowH],
      ];
      const leftPoly: Array<[number, number]> = [
        [rowX, y],
        [split + lean - GUTTER / 2, y],
        [split - lean - GUTTER / 2, y + rowH],
        [rowX, y + rowH],
      ];
      for (const poly of [rightPoly, leftPoly]) {
        const xs = poly.map((p) => p[0]);
        const x0 = Math.round(Math.min(...xs));
        const x1 = Math.round(Math.max(...xs));
        slots.push({
          x: x0,
          y,
          w: x1 - x0,
          h: rowH,
          poly: poly.map(([a, b]) => [Math.round(a), b] as [number, number]),
        });
      }
    } else {
      for (const part of row.w) {
        const w = Math.round(usable * part);
        slots.push({ x: right - w, y, w, h: rowH });
        right -= w + GUTTER;
      }
    }
    y += rowH + GUTTER;
  }
  return slots;
}

function biggest(slots: PageSlot[]): number {
  let best = 0;
  slots.forEach((s, i) => {
    if (s.w * s.h > slots[best]!.w * slots[best]!.h) best = i;
  });
  return best;
}

/**
 * Picks the template for a page of `count` panels. `bigIndex` is the panel the script marked as
 * the highlight (if any); `previousId` is last page's template, avoided so pages do not repeat.
 */
export function planPage(
  count: number,
  options: { pageNo: number; bigIndex?: number; previousId?: string; slant?: boolean },
): PageLayout {
  const want = Math.max(1, Math.min(MAX_PANELS_PER_PAGE, Math.round(count)));
  const sized = TEMPLATES.map((t) => ({ t, n: t.rows.reduce((a, r) => a + r.w.length, 0) })).filter(
    (c) => c.n === want,
  );
  const candidates = sized.length > 0 ? sized : [{ t: TEMPLATES[3]!, n: 3 }];
  let best = candidates[0]!;
  let bestScore = Number.POSITIVE_INFINITY;
  candidates.forEach((c, i) => {
    const slots = build(c.t, options.slant ?? false);
    const mismatch = options.bigIndex !== undefined && biggest(slots) !== options.bigIndex ? 10 : 0;
    const repeat = c.t.id === options.previousId ? 5 : 0;
    const flat = options.slant && !slots.some((s) => s.poly) ? 3 : 0;
    const rotate = (i + options.pageNo) % candidates.length;
    const score = mismatch + repeat + flat + rotate * 0.1;
    if (score < bestScore) {
      best = c;
      bestScore = score;
    }
  });
  const slots = build(best.t, options.slant ?? false);
  return { id: best.t.id, slots, big: biggest(slots) };
}

/** Drawing shape that loses the least when the picture is cropped into the slot. */
export function slotAspect(slot: PageSlot): "landscape" | "square" | "portrait" {
  const ratio = slot.w / slot.h;
  if (ratio > 1.25) return "landscape";
  if (ratio < 0.8) return "portrait";
  return "square";
}
