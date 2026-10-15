import { describe, expect, it } from "vitest";
import { pagePictureUrl, pictureFromUrl } from "./idol-photo-url.js";

describe("a picture from an address", () => {
  it("takes the picture a page names as its own, as an absolute https address", () => {
    expect(
      pagePictureUrl('<meta property="og:image" content="/img/a.jpg">', "https://example.jp/p/1"),
    ).toBe("https://example.jp/img/a.jpg");
    expect(
      pagePictureUrl(
        '<meta content="https://cdn.example.jp/b.png?x=1&amp;y=2" property="og:image">',
        "https://example.jp/",
      ),
    ).toBe("https://cdn.example.jp/b.png?x=1&y=2");
    expect(
      pagePictureUrl(
        '<meta name="twitter:image" content="https://example.jp/t.webp">',
        "https://example.jp/",
      ),
    ).toBe("https://example.jp/t.webp");
    expect(
      pagePictureUrl(
        '<meta property="og:image" content="http://example.jp/plain.jpg">',
        "https://example.jp/",
      ),
    ).toBeUndefined();
    expect(pagePictureUrl("<html></html>", "https://example.jp/")).toBeUndefined();
  });

  it("refuses an address that is not a plain https address", async () => {
    await expect(pictureFromUrl("not an address")).rejects.toThrow("アドレス");
    await expect(pictureFromUrl("http://example.jp/a.jpg")).rejects.toThrow("https://");
    await expect(pictureFromUrl("https://user:pw@example.jp/a.jpg")).rejects.toThrow("https://");
    await expect(pictureFromUrl("file:///C:/secret.png")).rejects.toThrow("https://");
  });
});
