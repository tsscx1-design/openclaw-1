import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { CHARACTER_OPTION_CATALOG, LEGACY_OPTION_ITEMS } from "./character-options.js";
import { MOTION_OPTIONS, MOTION_TEMPLATES } from "./motion-comic.js";
import { readJsonFile, resolveOpenClawHome, writeJsonFile } from "./partner-store.js";

// Sample picture factory: one picture per setting (a boot, a missing leg, a pose, a motion comic
// option), made by editing the model character's standing picture (Crie's) so that the face, hair
// and body stay the same. The pictures are shown when a setting is chosen. A setting can carry a
// default instruction, and settings that do not exist can be added with a name and an instruction.

export type SampleBase = "normal" | "adult";

export type SampleEntry = {
  /** The sample picture (JPEG). */
  file: string;
  base: SampleBase;
  /** The instruction it was made with (empty = the setting's own tags only). */
  note: string;
  at: number;
};

export type SampleIndex = {
  items: Record<string, SampleEntry>;
  /** Default instruction per setting, used when nothing else is written (editable). */
  defaults: Record<string, string>;
};

export type CustomSetting = {
  id: string;
  /** Where it is listed (a genre of the catalog, or a name of its own). */
  genre: string;
  label: string;
  /** What to draw, in plain words or tags. */
  instruction: string;
  /** A short Japanese explanation of what it is (written when the item was added in bulk). */
  ja?: string;
  /** The web pages the explanation was read from, when the word had to be searched. */
  sources?: Array<{ title: string; url: string }>;
  createdAt: number;
};

/** The sources kept with an item: well-formed http(s) links only, at most five. */
function cleanSources(value: unknown): Array<{ title: string; url: string }> {
  if (!Array.isArray(value)) return [];
  return value
    .flatMap((raw): Array<{ title: string; url: string }> => {
      const row = raw as { title?: unknown; url?: unknown };
      const url = clip(row?.url, 500);
      return /^https?:\/\//.test(url) ? [{ title: clip(row?.title, 120) || url, url }] : [];
    })
    .slice(0, 5);
}

export function samplesDir(): string {
  return path.join(resolveOpenClawHome(), "media", "sample-factory");
}
const indexFile = () => path.join(samplesDir(), "index.json");
const descriptionsFile = () => path.join(samplesDir(), "descriptions.json");

/** A short Japanese explanation and a plain English description of what a setting looks like. */
export type SampleDescription = { ja: string; en: string };

let descriptionsCache: { at: number; data: Record<string, SampleDescription> } | undefined;

/**
 * The written descriptions of the settings (made by the local model, kept next to the samples).
 * They are read again when the file changes; a missing file just means no descriptions yet.
 */
export async function loadSampleDescriptions(): Promise<Record<string, SampleDescription>> {
  try {
    const stat = await fs.stat(descriptionsFile());
    if (descriptionsCache && descriptionsCache.at === stat.mtimeMs) return descriptionsCache.data;
    const data = JSON.parse(await fs.readFile(descriptionsFile(), "utf-8")) as Record<
      string,
      SampleDescription
    >;
    descriptionsCache = { at: stat.mtimeMs, data };
    return data;
  } catch {
    return {};
  }
}
const customFile = () => path.join(samplesDir(), "custom.json");

/** The base pictures: the model character's standing picture, and the adult one made from it. */
export function adultBaseFile(characterId: string): string {
  return path.join(samplesDir(), `adult-base-${characterId}.png`);
}

export async function loadSampleIndex(): Promise<SampleIndex> {
  const index = await readJsonFile<Partial<SampleIndex>>(indexFile(), {});
  return { items: index.items ?? {}, defaults: index.defaults ?? {} };
}

export async function saveSampleIndex(index: SampleIndex): Promise<void> {
  await fs.mkdir(samplesDir(), { recursive: true });
  await writeJsonFile(indexFile(), index);
}

export async function loadCustomSettings(): Promise<CustomSetting[]> {
  const list = await readJsonFile<CustomSetting[]>(customFile(), []);
  return Array.isArray(list) ? list : [];
}

async function saveCustomSettings(list: CustomSetting[]): Promise<void> {
  await fs.mkdir(samplesDir(), { recursive: true });
  await writeJsonFile(customFile(), list);
}

const clip = (value: unknown, max: number): string =>
  typeof value === "string" ? value.trim().slice(0, max) : "";

export async function addCustomSetting(params: {
  genre?: unknown;
  label?: unknown;
  instruction?: unknown;
  ja?: unknown;
  sources?: unknown;
}): Promise<CustomSetting> {
  const label = clip(params.label, 40);
  const instruction = clip(params.instruction, 400);
  if (!label) throw new Error("名前を入力してください");
  if (!instruction) throw new Error("どんな絵にするか（指示）を入力してください");
  const list = await loadCustomSettings();
  const ja = clip(params.ja, 120);
  const sources = cleanSources(params.sources);
  const entry: CustomSetting = {
    id: `c${randomBytes(4).toString("hex")}`,
    genre: clip(params.genre, 80) || "自分で追加",
    label,
    instruction,
    ...(ja ? { ja } : {}),
    ...(sources.length ? { sources } : {}),
    createdAt: Date.now(),
  };
  await saveCustomSettings([...list, entry]);
  return entry;
}

export async function updateCustomSetting(
  id: string,
  params: { genre?: unknown; label?: unknown; instruction?: unknown },
): Promise<CustomSetting | undefined> {
  const list = await loadCustomSettings();
  const found = list.find((item) => item.id === id);
  if (!found) return undefined;
  const label = clip(params.label, 40);
  const instruction = clip(params.instruction, 400);
  const genre = clip(params.genre, 80);
  if (label) found.label = label;
  if (instruction) found.instruction = instruction;
  if (genre) found.genre = genre;
  await saveCustomSettings(list);
  return found;
}

export async function deleteCustomSetting(id: string): Promise<boolean> {
  const list = await loadCustomSettings();
  const next = list.filter((item) => item.id !== id);
  if (next.length === list.length) return false;
  await saveCustomSettings(next);
  const index = await loadSampleIndex();
  const key = `custom:${id}`;
  const entry = index.items[key];
  if (entry) await fs.rm(entry.file, { force: true }).catch(() => undefined);
  delete index.items[key];
  delete index.defaults[key];
  await saveSampleIndex(index);
  return true;
}

/**
 * Plain sentences for settings that the picture editor does not understand from tags alone (a tag
 * like "missing legs" came out as floating shoes). They replace the tags for the sample only.
 */
export const SAMPLE_HINTS: Record<string, string> = {
  missing_arm_left:
    "her left arm is missing from the shoulder: the left sleeve hangs empty and flat, there is no left arm and no left hand",
  missing_arm_right:
    "her right arm is missing from the shoulder: the right sleeve hangs empty and flat, there is no right arm and no right hand",
  missing_arms:
    "both of her arms are missing from the shoulders: both sleeves hang empty and flat, there are no arms and no hands",
  missing_leg_left:
    "her left leg is missing from the knee down: the left leg ends at the knee, there is no left lower leg, no left foot and no left shoe; she stands balanced on her right leg",
  missing_leg_right:
    "her right leg is missing from the knee down: the right leg ends at the knee, there is no right lower leg, no right foot and no right shoe; she stands balanced on her left leg",
  missing_legs:
    "she sits in a wheelchair, and both of her legs end at the knees: there are no lower legs, no feet and no shoes at all",
  missing_limbs:
    "remove both of her arms and both of her legs completely, a plain visual state without any cutting, blood, wounds or violence. Only her head and torso remain. " +
    "She sits upright on a soft cushion on the floor like a doll, the torso resting on the cushion, seen from the front. Her sleeves hang empty and flat. Same face, same hair, same clothes, same plain background",
  prosthetic_arm_left: "her left forearm and hand are a visible metal prosthetic arm",
  prosthetic_arm_right: "her right forearm and hand are a visible metal prosthetic arm",
  prosthetic_arms: "both of her forearms and hands are visible metal prosthetic arms",
  prosthetic_leg_left:
    "her left lower leg is a visible metal prosthetic leg (a pylon with a prosthetic foot)",
  prosthetic_leg_right:
    "her right lower leg is a visible metal prosthetic leg (a pylon with a prosthetic foot)",
  prosthetic_legs:
    "both of her lower legs are visible metal prosthetic legs (pylons with prosthetic feet)",
};

/** Settings that the quick editor cannot draw (large changes of the body): the precise editor is used for them. */
export const PRECISE_SAMPLE_KEYS: ReadonlySet<string> = new Set([
  "opt:missing_limbs",
  "opt:missing_arms",
  "opt:missing_legs",
]);

export type SampleSubject = {
  key: string;
  label: string;
  /** What to draw: a plain English sentence when one was written, else the English tags. */
  tags: string;
  /** The Japanese explanation, when one was written. */
  ja?: string;
  /** "look" changes how she looks; "scene" redraws a scene with a partner (poses). */
  mode: "look" | "scene";
};

/** The setting behind a key: "opt:<id>", "motion:<id>", "pose:<id>" or "custom:<id>". */
export async function resolveSampleSubject(key: string): Promise<SampleSubject | undefined> {
  const [kind, id] = [key.slice(0, key.indexOf(":")), key.slice(key.indexOf(":") + 1)];
  const written = (await loadSampleDescriptions())[key];
  /** The editor follows a sentence better than tags; the tags stay as a second part. */
  const withSentence = (tags: string) => (written?.en ? `${written.en} (${tags})` : tags);
  if (kind === "opt") {
    for (const genre of CHARACTER_OPTION_CATALOG)
      for (const part of genre.parts) {
        const item = part.items.find((entry) => entry.id === id);
        if (item)
          return {
            key,
            label: item.label,
            tags: SAMPLE_HINTS[item.id] ?? withSentence(item.tags),
            ...(written?.ja ? { ja: written.ja } : {}),
            mode: "look",
          };
      }
    // an item that is no longer listed (it means the same as another one) still has its old sample
    const legacy = LEGACY_OPTION_ITEMS.find((entry) => entry.id === id);
    if (legacy)
      return {
        key,
        label: legacy.label,
        tags: SAMPLE_HINTS[legacy.id] ?? withSentence(legacy.tags),
        ...(written?.ja ? { ja: written.ja } : {}),
        mode: "look",
      };
    return undefined;
  }
  if (kind === "motion") {
    const option = (MOTION_OPTIONS as Record<string, { label: string; tags: string }>)[id];
    return option
      ? {
          key,
          label: option.label,
          tags: withSentence(option.tags),
          ...(written?.ja ? { ja: written.ja } : {}),
          mode: "look",
        }
      : undefined;
  }
  if (kind === "pose") {
    const template = (
      MOTION_TEMPLATES as Record<string, { label: string; position: string; camera: string }>
    )[id];
    return template
      ? {
          key,
          label: template.label,
          tags: withSentence(`${template.position}, ${template.camera}`),
          ...(written?.ja ? { ja: written.ja } : {}),
          mode: "scene",
        }
      : undefined;
  }
  if (kind === "custom") {
    const custom = (await loadCustomSettings()).find((item) => item.id === id);
    return custom
      ? {
          key,
          label: custom.label,
          tags: custom.instruction,
          ...(custom.ja ? { ja: custom.ja } : {}),
          mode: "look",
        }
      : undefined;
  }
  return undefined;
}

/**
 * The picture instructions of chosen items (library options "opt:", motion options "motion:", poses
 * "pose:" and the user's own "custom:"), joined with commas, for adding to a prompt.
 */
export async function itemInstructions(keys: string[]): Promise<string> {
  const parts: string[] = [];
  for (const key of keys.slice(0, 40)) {
    const subject = await resolveSampleSubject(key);
    if (subject?.tags) parts.push(subject.tags);
  }
  return parts.join(", ");
}

/**
 * The instruction sent to the picture editor. The first sentence keeps the person; the second says
 * what changes. `note` is what the user wrote for this picture (or the saved default).
 */
export function samplePrompt(subject: SampleSubject, base: SampleBase, note: string): string {
  const keep =
    base === "adult"
      ? "Keep the same adult woman exactly as in the picture: face, hair, eye color, body shape and skin, and the same plain background."
      : "Keep the same woman exactly as in the picture: face, hair, eye color, body shape, and the same plain background.";
  const extra = note.trim() ? ` Also: ${note.trim()}.` : "";
  if (subject.mode === "scene") {
    return `${keep} Redraw her in this scene together with a man: ${subject.tags}. Both are adults.${extra}`;
  }
  return `${keep} Change only this, and show it clearly in a full-body picture: ${subject.tags}.${extra}`;
}

/** The file name of a sample picture inside the factory folder. */
export function sampleFileName(key: string): string {
  return `${key.replace(/[^a-zA-Z0-9_-]/g, "_")}.jpg`;
}

/**
 * Finds settings by a word (Japanese name, explanation, English tags or description), for a
 * conversation that must turn "boots" or "ahegao" into the id of a setting.
 */
export async function findSettings(
  query: string,
  limit = 12,
): Promise<Array<{ key: string; label: string; ja?: string; where: string; hasSample: boolean }>> {
  // Everyday words that the names of the settings do not contain.
  const aliases: Record<string, string> = {
    ケモミミ: "耳",
    けもみみ: "耳",
    獣耳: "耳",
    獣人: "耳",
    ふたなり: "",
    ブルマ: "",
  };
  const words = query
    .toLowerCase()
    .split(/[\s、,，]+/)
    .filter(Boolean)
    .map((word) => aliases[word] ?? word)
    .filter(Boolean);
  if (words.length === 0) return [];
  const all = await listSampleSubjects();
  const written = await loadSampleDescriptions();
  const index = await loadSampleIndex();
  const rows: Array<{ key: string; label: string; where: string; tags: string }> = [];
  for (const row of all.options)
    rows.push({ key: row.key, label: row.label, where: `${row.genre}／${row.part}`, tags: "" });
  for (const row of all.motion)
    rows.push({ key: row.key, label: row.label, where: `モーション／${row.group}`, tags: "" });
  for (const row of all.poses)
    rows.push({ key: row.key, label: row.label, where: "体位", tags: "" });
  for (const row of all.custom)
    rows.push({
      key: `custom:${row.id}`,
      label: row.label,
      where: row.genre,
      tags: row.instruction,
    });
  const scored: Array<{ score: number; row: (typeof rows)[number] }> = [];
  for (const row of rows) {
    const description = written[row.key];
    const text =
      `${row.label} ${description?.ja ?? ""} ${description?.en ?? ""} ${row.tags}`.toLowerCase();
    let score = 0;
    for (const word of words) {
      if (row.label.toLowerCase().includes(word)) score += 3;
      else if (text.includes(word)) score += 1;
      else {
        score = 0;
        break;
      }
    }
    if (score > 0) scored.push({ score, row });
  }
  return scored
    .toSorted((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(({ row }) => ({
      key: row.key,
      label: row.label,
      ...(written[row.key]?.ja ? { ja: written[row.key]?.ja } : {}),
      where: row.where,
      hasSample: Boolean(index.items[row.key]),
    }));
}

/** Every setting that can have a sample, for the list on the phone (labels only; pictures come apart). */
export async function listSampleSubjects(): Promise<{
  options: Array<{ key: string; genre: string; part: string; label: string }>;
  motion: Array<{ key: string; group: string; label: string }>;
  poses: Array<{ key: string; label: string }>;
  custom: CustomSetting[];
}> {
  const options = CHARACTER_OPTION_CATALOG.flatMap((genre) =>
    genre.parts.flatMap((part) =>
      part.items.map((item) => ({
        key: `opt:${item.id}`,
        genre: genre.name,
        part: part.name,
        label: item.label,
      })),
    ),
  );
  const motion = Object.entries(
    MOTION_OPTIONS as Record<string, { label: string; group: string }>,
  ).map(([id, option]) => ({
    key: `motion:${id}`,
    group: option.group,
    label: option.label,
  }));
  const poses = Object.entries(MOTION_TEMPLATES as Record<string, { label: string }>).map(
    ([id, template]) => ({ key: `pose:${id}`, label: template.label }),
  );
  return { options, motion, poses, custom: await loadCustomSettings() };
}
