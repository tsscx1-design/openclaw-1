import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

// Cutting the person out needs the Python models; the test checks the fallback (the picture itself is kept).
vi.mock("./media-studio.js", () => ({
  makeCharacterIcon: async (_source: string, icon: string) => {
    fs.writeFileSync(icon, "icon");
  },
}));
vi.mock("./photo-analysis.js", () => ({
  isolatePhotoSubject: async () => {
    throw new Error("no models in the test");
  },
}));

import { getCharacter, registerCharacter } from "./comic-characters.js";
import { attachMemberPhotos } from "./idol-character-photos.js";
import { addExtraPhoto } from "./idol-extras.js";
import { findIdolMember, idolMembers, idolRosterFile, saveIdolPhoto } from "./idol-master.js";

const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(64, 7)]);
let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-char-photos-test-"));
  process.env.OPENCLAW_HOME = home;
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(idolRosterFile(), "Gamma | Sato | idol | rock\n", "utf-8");
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

describe("carrying fan book photos over to a character", () => {
  it("makes the chosen photos her reference photos (main first, then the extras)", async () => {
    const member = findIdolMember("Gamma", idolMembers("Gamma")[0]!.id)!;
    await saveIdolPhoto(member, "image/jpeg", JPEG);
    await addExtraPhoto(member, "image/jpeg", Buffer.concat([JPEG, Buffer.from("x")]));
    const created = await registerCharacter({ name: "Sato", look: "1girl", profile: "test" });
    const attached = await attachMemberPhotos(created.id, member, [
      { kind: "main" },
      { kind: "extra", index: 1 },
      { kind: "extra", index: 99 },
      { kind: "bad" },
    ]);
    expect(attached).toBe(2);
    const saved = (await getCharacter(created.id))!;
    expect(saved.photoRef && fs.existsSync(saved.photoRef)).toBe(true);
    expect(saved.photoRefs).toHaveLength(2);
    expect(saved.photoStyle).toBe("photo");
    // the library shows her by a face picture right away
    expect(saved.icon && fs.existsSync(saved.icon)).toBe(true);
  });

  it("attaches nothing when no photo is chosen or found", async () => {
    const member = findIdolMember("Gamma", idolMembers("Gamma")[0]!.id)!;
    const created = await registerCharacter({ name: "Sato2", look: "1girl", profile: "test" });
    expect(await attachMemberPhotos(created.id, member, [])).toBe(0);
    expect(await attachMemberPhotos(created.id, member, [{ kind: "extra", index: 42 }])).toBe(0);
    expect((await getCharacter(created.id))?.photoRef).toBeUndefined();
  });
});
