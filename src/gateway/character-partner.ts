import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { characterOptionLabels, type LibraryCharacter } from "./comic-characters.js";
import { buildPartnerSheetMarkdown, parsePartnerSheet } from "./partner-sheet-schema.js";
import {
  listPartnerProfiles,
  loadPartnerCharacterSheet,
  savePartnerProfileWithSheet,
  updatePartnerSettings,
  type PartnerProfile,
} from "./server-methods/partners.js";

// A library character that chats is a partner whose sheet is made from the character's settings.
// The library holds the look and the personality; the partner holds the chat settings (model,
// reply length, voice...). Editing the character updates the partner's sheet; the partner's own
// text stays unless the library has something for the same item.

const FFMPEG = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffmpeg.exe";

const joinText = (...parts: Array<string | undefined>): string =>
  parts
    .map((p) => p?.trim())
    .filter(Boolean)
    .join("、");

/** Partner-sheet items (by label) that the character's settings answer. */
export function characterSheetValues(character: LibraryCharacter): Record<string, string> {
  const s = character.spec ?? {};
  const options = characterOptionLabels(s.lookOptions).join("・");
  const from: Record<string, string | undefined> = {
    外見の年齢: s.age ? `${s.age}歳` : undefined,
    "職業・クラス": s.occupation,
    "髪型・髪色": joinText(s.hair, s.hairstyle),
    "目の色・形": joinText(s.eyes, s.contactLens),
    服装のテイスト: s.outfit,
    "身長・体型": joinText(s.heightCm ? `${s.heightCm}cm` : undefined, s.body),
    "トレードマーク・持ち物": s.accessories,
    身体的特徴: joinText(s.features, s.bodyMarks, options, s.lookNotes),
    一人称: s.firstPerson,
    よく使う口癖: s.catchphrase,
    言葉のフォーマルさ: s.speech,
    "好きな食べ物・飲み物": s.favoriteFood,
    "趣味・休日の過ごし方": s.hobbies,
    隠している秘密: s.secret,
    特殊能力: s.skills,
    最も大切にしているもの: s.treasured,
    育った環境: s.background,
    "家族構成・創造主": s.relations,
    ...character.sheetValues,
  };
  return Object.fromEntries(Object.entries(from).filter(([, v]) => v && v.trim())) as Record<
    string,
    string
  >;
}

/** The text sections of the sheet that have no item of their own (personality, likes, dislikes). */
function characterFreeText(character: LibraryCharacter): string {
  const s = character.spec ?? {};
  const lines: string[] = [];
  const profile = joinText(character.profile, s.personality);
  if (profile) lines.push(`### 性格・プロフィール\n${profile}`);
  const likes = [
    s.likes ? `好きなもの: ${s.likes}` : "",
    s.dislikes ? `苦手なもの: ${s.dislikes}` : "",
  ].filter(Boolean);
  if (likes.length > 0) lines.push(`### 好き・苦手\n${likes.join("\n")}`);
  return lines.join("\n\n");
}

/** A face picture for the partner list: the icon as a JPEG of at most 512 px, as a data URL. */
async function iconDataUrl(icon: string | undefined): Promise<string | undefined> {
  if (!icon) return undefined;
  const out = path.join(os.tmpdir(), `partner-avatar-${process.pid}-${Date.now()}.jpg`);
  try {
    await new Promise<void>((resolve, reject) => {
      execFile(
        FFMPEG,
        ["-y", "-loglevel", "error", "-i", icon, "-vf", "scale='min(512,iw)':-2", "-q:v", "3", out],
        { windowsHide: true, timeout: 60_000 },
        (err) => (err ? reject(err) : resolve()),
      );
    });
    return `data:image/jpeg;base64,${await fs.readFile(out, "base64")}`;
  } catch {
    return undefined;
  } finally {
    await fs.rm(out, { force: true });
  }
}

/** The partner this character chats as, if there is one. */
export async function partnerOfCharacter(
  character: LibraryCharacter,
): Promise<PartnerProfile | undefined> {
  const partners = await listPartnerProfiles();
  return partners.find((p) => p.sourceCharacterId === character.id || p.id === character.partnerId);
}

/**
 * Makes or updates the partner of a character. A new partner gets the persona, the sheet, the
 * face picture and ordinary chat settings; an existing one keeps its persona, model and settings
 * and only takes the sheet items the character answers. Returns the partner.
 */
export async function syncPartnerFromCharacter(
  character: LibraryCharacter,
  options: { create: boolean },
): Promise<PartnerProfile | undefined> {
  const existing = await partnerOfCharacter(character);
  if (!existing && !options.create) return undefined;
  const supplied = characterSheetValues(character);
  if (existing) {
    const parsed = parsePartnerSheet(await loadPartnerCharacterSheet(existing));
    const sheet = buildPartnerSheetMarkdown({
      name: existing.name,
      values: { ...Object.fromEntries(parsed.attributes), ...supplied },
      freeText: parsed.freeText || characterFreeText(character),
    });
    // a partner that has no voice yet takes the voice of its character
    if (character.voiceId && !existing.settings?.voice) {
      await updatePartnerSettings(existing.id, {
        voice: { engine: "clone" as const, speakerId: 0, voiceId: character.voiceId },
      });
    }
    return await savePartnerProfileWithSheet({
      partnerId: existing.id,
      name: existing.name,
      persona: existing.persona,
      characterSheet: sheet,
      sourceCharacterId: character.id,
      avatarUrl: await iconDataUrl(character.icon),
    });
  }
  const sheet = buildPartnerSheetMarkdown({
    name: character.name,
    values: supplied,
    freeText: characterFreeText(character),
  });
  const partner = await savePartnerProfileWithSheet({
    name: character.name,
    persona: `あなたは${character.name}です。${character.profile || "図鑑の設定どおりに振る舞ってください。"}`,
    characterSheet: sheet,
    sourceCharacterId: character.id,
    avatarUrl: await iconDataUrl(character.icon),
  });
  await updatePartnerSettings(partner.id, {
    replyLength: "normal",
    emoji: "some",
    thinkingDefault: "auto",
    temperature: 0.7,
    // the voice chosen with the character (a voice-studio voice) is the partner's voice
    ...(character.voiceId
      ? { voice: { engine: "clone" as const, speakerId: 0, voiceId: character.voiceId } }
      : {}),
  });
  return partner;
}
