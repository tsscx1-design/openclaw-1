import { describe, expect, it } from "vitest";
import { parseWaifuImages, parseWaifuTags } from "./waifu-im.js";

describe("waifu-im", () => {
  it("reads the tags", () => {
    expect(
      parseWaifuTags({
        items: [
          { slug: "waifu", name: "Waifu", description: "x", imageCount: 4278 },
          { name: "no slug" },
        ],
      }),
    ).toEqual([{ slug: "waifu", name: "Waifu", description: "x", count: 4278 }]);
  });

  it("reads the pictures and keeps only https addresses", () => {
    const images = parseWaifuImages({
      items: [
        {
          id: 1,
          url: "https://cdn.waifu.im/1.png",
          width: 10,
          height: 20,
          isNsfw: true,
          tags: [{ slug: "ero" }],
          artists: [{ name: "a" }],
          source: "https://x",
        },
        { id: 2, url: "http://insecure/2.png" },
      ],
    });
    expect(images).toHaveLength(1);
    expect(images[0]).toMatchObject({ id: 1, adult: true, tags: ["ero"], artists: ["a"] });
  });
});
