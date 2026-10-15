/** A bound for stored descriptions and worker requests, separate from model token capacity. */
export const MAX_VOICE_CAPTION_CHARS = 16_000;

export function normalizeVoiceCaption(raw: string): string {
  const text = raw
    .replace(/\r\n?/g, "\n")
    .replace(/[\p{Cc}\p{Cf}]/gu, (character) =>
      character === "\n" || character === "\t" ? character : "",
    )
    .trim();
  if (text.length > MAX_VOICE_CAPTION_CHARS) {
    throw new Error(
      `声の詳細設定は${MAX_VOICE_CAPTION_CHARS}文字以内で入力してください。内容は切り詰めていません。`,
    );
  }
  return text;
}
