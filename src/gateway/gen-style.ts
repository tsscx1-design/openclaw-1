import fs from "node:fs/promises";
import path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

/**
 * The switch for what the generated pictures look like: "anime", "photo" (real people), or "auto" (the model chosen on
 * the screen is used as it is). It is kept in one small file, so it is the same on every screen and after a restart.
 */
export type GenStyle = "auto" | "anime" | "photo";

const file = (): string => path.join(resolveOpenClawHome(), "media", "studio-style.json");

export function parseGenStyle(value: unknown): GenStyle {
  return value === "anime" || value === "photo" ? value : "auto";
}

export async function readGenStyle(): Promise<GenStyle> {
  try {
    return parseGenStyle(
      (JSON.parse(await fs.readFile(file(), "utf-8")) as { style?: unknown }).style,
    );
  } catch {
    return "auto";
  }
}

export async function writeGenStyle(value: unknown): Promise<GenStyle> {
  const style = parseGenStyle(value);
  await fs.mkdir(path.dirname(file()), { recursive: true });
  const temp = `${file()}.tmp-${process.pid}`;
  await fs.writeFile(temp, JSON.stringify({ style }), "utf-8");
  await fs.rename(temp, file());
  return style;
}
