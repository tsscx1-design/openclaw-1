import { getCharacter, specSummary } from "./comic-characters.js";

// What the character library says about a character (look, profile, the settings picked in the
// creator, outfits), as a part of the chat prompt: a partner made from a library character talks
// with the library's current settings, not only with the partner sheet copied once.

const MAX_CHARS = 1_400;

/** The prompt section for the library character behind a partner, or undefined when there is none. */
export async function libraryCharacterSection(
  sourceCharacterId: string | undefined,
): Promise<string | undefined> {
  if (!sourceCharacterId) return undefined;
  const character = await getCharacter(sourceCharacterId).catch(() => undefined);
  if (!character) return undefined;
  const lines: string[] = [];
  if (character.look?.trim()) lines.push(`- 見た目：${character.look.trim()}`);
  if (character.profile?.trim()) lines.push(`- 設定：${character.profile.trim()}`);
  if (character.spec) {
    const summary = specSummary(character.spec).trim();
    if (summary) lines.push(`- 図鑑で選んだ設定：${summary}`);
  }
  if (character.photoTraits?.trim())
    lines.push(`- 写真から読み取った特徴：${character.photoTraits.trim()}`);
  if (character.outfits && character.outfits.length > 0) {
    lines.push(`- 持っている衣装：${character.outfits.map((outfit) => outfit.name).join("、")}`);
  }
  if (lines.length === 0) return undefined;
  const text = `\n■ キャラ図鑑の設定（外見・設定。会話でも、この見た目と設定のとおりに振る舞い、聞かれたらこの内容で答える）:\n${lines.join("\n")}`;
  return text.length > MAX_CHARS ? `${text.slice(0, MAX_CHARS)}…` : text;
}
