import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { characterAssetDir, getCharacter, saveCharacter } from "./comic-characters.js";
import { readExtraPhoto } from "./idol-extras.js";
import { readIdolPhoto, type IdolMember } from "./idol-master.js";
import { makeCharacterIcon } from "./media-studio.js";
import { isolatePhotoSubject } from "./photo-analysis.js";

// Carries the pictures chosen in the fan book over to the character made from a member: they become her reference
// photos (the same files the character creator keeps: photo-ref.png, photo-ref-2.png ...), so her setting picture can
// be drawn from them.

const MAX_PHOTOS = 5;
const EXT: Record<string, string> = {
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
};

type Choice = { kind?: unknown; index?: unknown };

/** Returns how many reference photos were attached. */
export async function attachMemberPhotos(
  characterId: string,
  member: IdolMember,
  choices: unknown[],
): Promise<number> {
  const picks: Array<{ kind: "main" | "extra"; index: number }> = [];
  for (const choice of choices as Choice[]) {
    if (choice?.kind === "main") picks.push({ kind: "main", index: 0 });
    else if (choice?.kind === "extra" && typeof choice.index === "number")
      picks.push({ kind: "extra", index: choice.index });
    if (picks.length >= MAX_PHOTOS) break;
  }
  if (picks.length === 0) return 0;
  const dir = characterAssetDir(characterId);
  const work = await fs.mkdtemp(path.join(os.tmpdir(), "openclaw-member-photo-"));
  const refs: string[] = [];
  try {
    for (const pick of picks) {
      const photo =
        pick.kind === "main"
          ? await readIdolPhoto(member.group, member.id)
          : await readExtraPhoto(member, pick.index);
      if (!photo) continue;
      const original = path.join(work, `original-${refs.length}${EXT[photo.mime] ?? ".jpg"}`);
      await fs.writeFile(original, Buffer.from(photo.base64, "base64"));
      await fs.mkdir(dir, { recursive: true });
      const target = path.join(
        dir,
        refs.length === 0 ? "photo-ref.png" : `photo-ref-${refs.length + 1}.png`,
      );
      try {
        await isolatePhotoSubject(original, target);
      } catch {
        // The person could not be cut out: the picture itself is kept as it is.
        await fs.copyFile(original, target);
      }
      refs.push(target);
    }
  } finally {
    await fs.rm(work, { recursive: true, force: true });
  }
  if (refs.length === 0) return 0;
  const character = await getCharacter(characterId);
  if (!character) return 0;
  // The library shows the character by her face picture: made from the photo (the face is found, else the upper middle),
  // so the fan book's picture is the one the library shows right away (a sheet drawn later replaces it).
  let icon = character.icon;
  if (!icon) {
    const target = path.join(dir, "icon.png");
    try {
      await makeCharacterIcon(refs[0]!, target);
      icon = target;
    } catch {
      icon = undefined;
    }
  }
  await saveCharacter({
    ...character,
    ...(icon ? { icon } : {}),
    photoRef: refs[0],
    photoStyle: "photo",
    photoEngine: "precise",
    photoQuality: "normal",
    ...(refs.length > 1
      ? { photoRefs: refs, photoMode: "subject" as const, photoRoles: refs.map(() => "顔") }
      : {}),
    updatedAt: Date.now(),
  });
  return refs.length;
}
