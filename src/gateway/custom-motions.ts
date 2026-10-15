import * as fsSync from "node:fs";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  MOTION_OPTION_IDS,
  MOTION_OPTIONS,
  MOTION_TEMPLATE_IDS,
  MOTION_TEMPLATES,
} from "./motion-comic.js";
import { resolveOpenClawHome } from "./partner-store.js";

// Poses and options the user adds (by talking with Crie) on top of the built-in motion comic lists.
// A custom pose is a built-in pose with its own name and wording; a custom option is a name with
// image tags and a phrase for the movement. They are applied to the live lists, so they work
// everywhere those lists are used without building the gateway again.

export type CustomPose = {
  id: string;
  label: string;
  group: string;
  /** Built-in pose whose settings are copied first. */
  base: string;
  position?: string;
  camera?: string;
  slow?: string;
  fast?: string;
  build?: string;
  climaxMotion?: string;
  after?: string;
};
export type CustomOption = {
  id: string;
  label: string;
  group: string;
  tags: string;
  motion?: string;
  note?: string;
  climaxOnly?: boolean;
};
type Store = { poses: CustomPose[]; options: CustomOption[] };

const file = () => path.join(resolveOpenClawHome(), "media", "custom-motions.json");

function readSync(): Store {
  try {
    const raw = JSON.parse(fsSync.readFileSync(file(), "utf-8")) as Partial<Store>;
    return { poses: raw.poses ?? [], options: raw.options ?? [] };
  } catch {
    return { poses: [], options: [] };
  }
}

const customGroups = new Map<string, string>();
export const customGroupOf = (id: string): string | undefined => customGroups.get(id);

function apply(store: Store): void {
  const templates = MOTION_TEMPLATES as unknown as Record<string, Record<string, unknown>>;
  const options = MOTION_OPTIONS as unknown as Record<string, Record<string, unknown>>;
  const templateIds = MOTION_TEMPLATE_IDS as string[];
  const optionIds = MOTION_OPTION_IDS as string[];
  for (const pose of store.poses) {
    const base = templates[pose.base] ?? templates.missionary;
    const { id, base: _base, group, ...over } = pose;
    templates[id] = {
      ...base,
      ...Object.fromEntries(
        Object.entries(over).filter(([, v]) => typeof v === "string" && v.trim()),
      ),
    };
    customGroups.set(id, group);
    if (!templateIds.includes(id)) templateIds.push(id);
  }
  for (const option of store.options) {
    options[option.id] = {
      label: option.label,
      group: option.group,
      tags: option.tags,
      motion: option.motion,
      note: option.note,
      climaxOnly: option.climaxOnly,
    };
    customGroups.set(option.id, option.group);
    if (!optionIds.includes(option.id)) optionIds.push(option.id);
  }
}

apply(readSync());

export const listCustomMotions = async (): Promise<Store> => readSync();

async function save(store: Store): Promise<void> {
  await fs.mkdir(path.dirname(file()), { recursive: true });
  const tmp = `${file()}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(store, null, 1), "utf-8");
  await fs.rename(tmp, file());
}

const text = (v: unknown, max: number): string =>
  typeof v === "string" ? v.trim().slice(0, max) : "";

export async function addCustomMotion(
  input: Record<string, unknown>,
): Promise<{ kind: "pose" | "option"; id: string; label: string }> {
  const label = text(input.label, 40);
  if (!label) throw new Error("名前が必要です");
  const store = readSync();
  const group = text(input.group, 20) || "自分で追加";
  const stamp = Date.now().toString(36);
  if (input.kind === "option") {
    const tags = text(input.tags, 400);
    if (!tags) throw new Error("絵のタグ（英語）が必要です");
    const id = `cm-${stamp}`;
    const entry: CustomOption = {
      id,
      label,
      group,
      tags,
      motion: text(input.motion, 400) || undefined,
      note: text(input.note, 300) || undefined,
      climaxOnly: input.climaxOnly === true,
    };
    store.options.push(entry);
    await save(store);
    apply({ poses: [], options: [entry] });
    return { kind: "option", id, label };
  }
  const base =
    typeof input.base === "string" && input.base in MOTION_TEMPLATES ? input.base : "missionary";
  const id = `cp-${stamp}`;
  const pose: CustomPose = {
    id,
    label,
    group,
    base,
    position: text(input.position, 400) || undefined,
    camera: text(input.camera, 300) || undefined,
    slow: text(input.slow, 900) || undefined,
    fast: text(input.fast, 900) || undefined,
    build: text(input.build, 900) || undefined,
    climaxMotion: text(input.climaxMotion, 900) || undefined,
    after: text(input.after, 600) || undefined,
  };
  store.poses.push(pose);
  await save(store);
  apply({ poses: [pose], options: [] });
  return { kind: "pose", id, label };
}

export async function removeCustomMotion(id: string): Promise<boolean> {
  const store = readSync();
  const before = store.poses.length + store.options.length;
  store.poses = store.poses.filter((p) => p.id !== id);
  store.options = store.options.filter((o) => o.id !== id);
  if (store.poses.length + store.options.length === before) return false;
  await save(store);
  delete (MOTION_TEMPLATES as unknown as Record<string, unknown>)[id];
  delete (MOTION_OPTIONS as unknown as Record<string, unknown>)[id];
  for (const ids of [MOTION_TEMPLATE_IDS as string[], MOTION_OPTION_IDS as string[]]) {
    const i = ids.indexOf(id);
    if (i >= 0) ids.splice(i, 1);
  }
  customGroups.delete(id);
  return true;
}
