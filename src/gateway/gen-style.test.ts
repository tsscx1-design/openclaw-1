import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { parseGenStyle, readGenStyle, writeGenStyle } from "./gen-style.js";
import {
  applyGenStyle,
  photoIdentityPrompt,
  photoStylePrompt,
  randomBodyPrompt,
} from "./media-studio.js";

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "gen-style-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

const always = async () => true;
const never = async () => false;

describe("the anime / photo switch", () => {
  it("keeps the style and treats anything else as auto", async () => {
    expect(await readGenStyle()).toBe("auto");
    expect(await writeGenStyle("photo")).toBe("photo");
    expect(await readGenStyle()).toBe("photo");
    expect(await writeGenStyle("anime")).toBe("anime");
    expect(await writeGenStyle("nonsense")).toBe("auto");
    expect(parseGenStyle(undefined)).toBe("auto");
  });

  it("with photo, replaces an anime model by a real-person model, and leaves models that draw either", async () => {
    expect(
      (await applyGenStyle("image", { imageModel: "wai" } as never, "photo", always)).imageModel,
    ).toBe("asianreal");
    expect(
      (await applyGenStyle("image", { imageModel: "noobai" } as never, "photo", always)).imageModel,
    ).toBe("asianreal");
    expect(
      (await applyGenStyle("image", { imageModel: "cyberreal" } as never, "photo", always))
        .imageModel,
    ).toBe("cyberreal");
    expect(
      (await applyGenStyle("image", { imageModel: "zimage" } as never, "photo", always)).imageModel,
    ).toBe("zimage");
    // no real-person checkpoint on the disk: the chosen model stays
    expect(
      (await applyGenStyle("image", { imageModel: "wai" } as never, "photo", never)).imageModel,
    ).toBe("wai");
  });

  it("with anime, replaces a real-person model by WAI", async () => {
    expect(
      (await applyGenStyle("comic", { imageModel: "asianreal" } as never, "anime", always))
        .imageModel,
    ).toBe("wai");
    expect(
      (await applyGenStyle("comic", { imageModel: "noobai" } as never, "anime", always)).imageModel,
    ).toBe("noobai");
  });

  it("gives a model to the kinds that draw pictures when none was chosen, but not to the others", async () => {
    expect((await applyGenStyle("image", {} as never, "photo", always)).imageModel).toBe(
      "asianreal",
    );
    expect((await applyGenStyle("comic", {} as never, "photo", always)).imageModel).toBe(
      "asianreal",
    );
    expect((await applyGenStyle("voice", {} as never, "photo", always)).imageModel).toBeUndefined();
    expect((await applyGenStyle("music", {} as never, "photo", always)).imageModel).toBeUndefined();
  });

  it("also swaps the picture model of a motion comic, and changes nothing with auto", async () => {
    expect(
      (await applyGenStyle("video", { motionImageModel: "wai" } as never, "photo", always))
        .motionImageModel,
    ).toBe("asianreal");
    const same = { imageModel: "wai" } as never;
    expect(await applyGenStyle("image", same, "auto", always)).toBe(same);
  });
});

describe("a character made from a picture", () => {
  it("writes a different instruction for each source and style, and keeps the design for anime sources", () => {
    expect(photoStylePrompt("photo", "anime")).toContain(
      "Redraw the person in the reference photo as an anime character",
    );
    expect(photoStylePrompt(undefined, "photo")).toContain("photograph of the same person");
    expect(photoStylePrompt("anime", "photo")).toContain("real person in a live-action photograph");
    expect(photoStylePrompt("anime", "anime")).toContain("Keep the exact character design");
    expect(photoIdentityPrompt("anime")).toContain("hair color and hairstyle silhouette");
    expect(photoIdentityPrompt("photo")).toContain("distinctive face outline");
  });
});

describe("a face-only reference", () => {
  it("chooses the body and clothes at random, and keeps the face", () => {
    const first = randomBodyPrompt(() => 0);
    const last = randomBodyPrompt(() => 0.999);
    expect(first).toContain("slender build");
    expect(first).toContain("white blouse");
    expect(last).toContain("petite build");
    expect(first).not.toBe(last);
    expect(first).toContain("Take only the face, hair and skin tone");
  });
});

describe("a face-only reference with an outfit already set", () => {
  it("uses the outfit and chooses only the build at random", () => {
    const text = randomBodyPrompt(() => 0, "an idol costume");
    expect(text).toContain("wearing an idol costume");
    expect(text).toContain("group's costume");
    expect(text).not.toContain("white blouse");
  });
});
