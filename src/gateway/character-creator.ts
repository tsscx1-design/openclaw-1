// The character creator partner ("キャラクリ"): designs characters for the character library in
// conversation. Two helpers beyond a normal studio assistant:
//   - a submitted picture is read by a local anime tagger (WD SwinV2 v3, CPU, ~3 s) into look
//     tags, so the picture never leaves the PC and adult pictures are not refused;
//   - "調べて" searches with the configured web_search provider (Gemini with Google Search)
//     instead of the instant-answer lookup ordinary partners use.

import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import type { AnyAgentTool } from "../agents/tools/common.js";

/** Text inside the untrusted-content envelope web_search puts around it (as partner-creation.ts). */
function unwrapExternalText(text: string): string {
  const match = /<<<[A-Z_]+ id="([^"]+)">>>\n[\s\S]*?\n---\n([\s\S]*?)\n<<<[A-Z_]+ id="\1">>>/.exec(
    text,
  );
  return (match?.[2] ?? text).trim();
}

const TAGGER_PYTHON = "D:\\media-tools\\Wan2GP\\venv\\Scripts\\python.exe";
const TAGGER_SCRIPT = "D:\\media-tools\\image\\tagger.py";

export type ImageTags = { general: Array<[string, number]>; character: Array<[string, number]> };

/** Tags that describe how a character looks (what a character sheet should keep). */
const LOOK_TAG =
  /(hair|bangs|_cut|bob|hime|ponytail|twintails|braid|bun|ahoge|sidelocks|eyes|eyelashes|pupils|breasts|skin|ears|horns|tail|wings|halo|freckles|mole|scar|makeup|lipstick|nail|tan|muscular|curvy|plump|tall|dress|shirt|blouse|skirt|shorts|pants|jeans|thighhighs|pantyhose|stockings|socks|choker|collar|jacket|coat|hoodie|sweater|cardigan|vest|kimono|yukata|apron|maid|nun|bikini|swimsuit|leotard|bodysuit|uniform|armor|cape|cloak|glasses|hat|cap|beret|ribbon|bow|hairband|hairclip|headphones|gloves|boots|shoes|heels|sandals|necktie|bowtie|earrings|necklace|bracelet|garter|lingerie|corset|frills|lace|gothic|lolita|jirai|gyaru|elf|demon|angel|fox|cat|bunny)/;

/** Acts, framing and scenery: not part of a character's look. */
const NOT_LOOK =
  /^(1girl|1boy|2girls|solo|solo_focus|hetero|sex|vaginal|anal|oral|fellatio|penis|pussy|nipples|cum|nude|completely_nude|pov|lying|on_back|on_side|sitting|standing|spread_legs|missionary|indoors|outdoors|bed|on_bed|pillow|candle|blush|sweat|breath|heavy_breathing|open_mouth|closed_mouth|looking_at_viewer|smile|uncensored|censored|clothes_pull|clothes_lift|feet|barefoot|no_shoes|m_legs|simple_background|white_background)$/;

export function runTagger(file: string): Promise<ImageTags> {
  return new Promise((resolve, reject) => {
    execFile(
      TAGGER_PYTHON,
      [TAGGER_SCRIPT, file],
      { windowsHide: true, timeout: 120_000 },
      (err, stdout) => {
        if (err) return reject(new Error(`画像を読めませんでした: ${err.message}`));
        try {
          const line = stdout.trim().split(/\r?\n/).at(-1) ?? "";
          resolve(JSON.parse(line) as ImageTags);
        } catch {
          reject(new Error("画像の解析結果を読めませんでした"));
        }
      },
    );
  });
}

/** The tagger's output as a note for the chat model, with the look tags picked out. */
export function describeImageTags(tags: ImageTags): { note: string; look: string; minor: boolean } {
  const names = tags.general.map(([tag]) => tag);
  const minor = false; // 制限なし
  const look = names.filter((tag) => LOOK_TAG.test(tag) && !NOT_LOOK.test(tag)).slice(0, 30);
  const known = tags.character.map(([tag]) => tag.replace(/_/g, " "));
  const lines = [
    "【ユーザーが送った画像の解析（PCの中のタグ付けAIの結果）】",
    `見た目のタグ: ${look.map((t) => t.replace(/_/g, " ")).join(", ") || "（読み取れず）"}`,
    known.length > 0
      ? `既存の作品のキャラに似ている: ${known.join(", ")}（そのまま写さず、要素を参考に新しいキャラにする）`
      : "",
    "このタグを参考に、名前・年齢・性格・口調を相談して決め、character_create の description に見た目を日本語でまとめる。",
  ];
  return { note: lines.filter(Boolean).join("\n"), look: look.join(", "), minor };
}

/** Saves a submitted picture (base64 from the app) under the partner's folder and reads it. */
export async function readSubmittedImage(
  partnerDir: string,
  base64: string,
): Promise<{ file: string; note: string; minor: boolean }> {
  const data = Buffer.from(base64.replace(/^data:[^,]+,/, ""), "base64");
  if (data.length < 100 || data.length > 12 * 1024 * 1024)
    throw new Error("画像の大きさが合いません（12MBまで）");
  const dir = path.join(partnerDir, "uploads");
  await fs.mkdir(dir, { recursive: true });
  const file = path.join(dir, `image-${Date.now()}.png`);
  await fs.writeFile(file, data);
  const described = describeImageTags(await runTagger(file));
  return { file, note: described.note, minor: described.minor };
}

/** Web search through the configured provider; a short list of what was found. */
export async function searchForReference(searchTool: AnyAgentTool, query: string): Promise<string> {
  const result = await searchTool.execute(`character-creator-${Date.now()}`, { query, count: 5 });
  const details = (result as { details?: Record<string, unknown> }).details ?? {};
  const rawAnswer =
    typeof details.content === "string"
      ? details.content
      : typeof details.answer === "string"
        ? details.answer
        : "";
  const answer = unwrapExternalText(rawAnswer);
  const items = (
    Array.isArray(details.results)
      ? details.results
      : Array.isArray(details.citations)
        ? details.citations
        : []
  ) as Array<{
    title?: unknown;
    url?: unknown;
    snippet?: unknown;
  }>;
  const list = items
    .slice(0, 5)
    .map(
      (item) =>
        `- ${unwrapExternalText(String(item.title ?? ""))}: ${unwrapExternalText(String(item.snippet ?? "")).slice(0, 200)} (${String(item.url ?? "")})`,
    );
  return [`【「${query}」の検索結果】`, answer.slice(0, 1500), ...list].filter(Boolean).join("\n");
}
