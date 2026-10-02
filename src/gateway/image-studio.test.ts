import { describe, expect, it } from "vitest";
import { sanitizeImagePrompt } from "./image-studio.js";
import { parseImageDataUri } from "./partner-album.js";

describe("sanitizeImagePrompt", () => {
  it("keeps plain tags and strips shell metacharacters (the prompt is one argv entry, never a shell string)", () => {
    expect(sanitizeImagePrompt("1girl, silver hair --steps 99 `calc` $(x)")).toBe(
      "1girl, silver hair --steps 99 calc (x)",
    );
  });

  it("collapses whitespace and bounds the length", () => {
    expect(sanitizeImagePrompt("a\n\tb   c")).toBe("a b c");
    expect(sanitizeImagePrompt("x".repeat(2_000)).length).toBe(600);
  });

  it("returns an empty string when nothing printable remains", () => {
    expect(sanitizeImagePrompt("<>|&")).toBe("");
  });
});

describe("parseImageDataUri", () => {
  const png = Buffer.from("not really a png").toString("base64");

  it("accepts jpeg/png/webp data URIs", () => {
    expect(parseImageDataUri(`data:image/png;base64,${png}`).mime).toBe("image/png");
    expect(parseImageDataUri(`data:image/jpeg;base64,${png}`).bytes.length).toBeGreaterThan(0);
  });

  it("rejects other types and empty payloads", () => {
    expect(() => parseImageDataUri(`data:image/svg+xml;base64,${png}`)).toThrow(/Unsupported/);
    expect(() => parseImageDataUri("data:text/html;base64,AAAA")).toThrow(/Unsupported/);
    expect(() => parseImageDataUri("not a data uri")).toThrow(/Unsupported/);
  });

  it("rejects images over 6 MB", () => {
    const big = Buffer.alloc(6 * 1024 * 1024 + 1).toString("base64");
    expect(() => parseImageDataUri(`data:image/png;base64,${big}`)).toThrow(/too large/);
  });
});
