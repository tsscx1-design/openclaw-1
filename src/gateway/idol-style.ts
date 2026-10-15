import { idolCategories } from "./idol-master.js";

// What a character made from the fan book wears and how she talks, by the group and the category it sits in:
// an idol group gets an idol costume, an actor gets an actor's formal wear, and so on. A character of the same group
// that is already in the library gives its outfit to the next one (the group looks matched). Real speech cannot be
// copied without the person's own words, so the tone is the group's general atmosphere; lines of the member's memo that
// start with 口調 / 話し方 are added to it.

export type GroupStyle = {
  label: string;
  outfit: string;
  tone: string;
  /** The voice, described in words (for the voice of the character). */ voice: string;
};

const IDOL: GroupStyle = {
  label: "アイドル",
  outfit: "a cute idol stage costume with a ribbon, a layered short skirt and matching accessories",
  tone: "明るく元気で、ファンに丁寧に話すアイドルらしい口調",
  voice: "明るく元気な、若い女性の声",
};
const FORTY_EIGHT: GroupStyle = {
  label: "48グループ",
  outfit:
    "a checkered pleated-skirt school-uniform-style idol costume with a ribbon tie, knee-high socks and loafers",
  tone: "明るく元気で、ファンに丁寧に話すアイドルらしい口調",
  voice: "明るく元気な、若い女性の声",
};
const SAKAMICHI: GroupStyle = {
  label: "坂道シリーズ",
  outfit: "an elegant simple one-piece dress in a soft pastel color, a ladylike idol costume",
  tone: "上品で落ち着いた、やわらかい口調",
  voice: "落ち着いた、上品でやわらかい女性の声",
};
const BY_ROOT: Record<string, GroupStyle> = {
  歌手: IDOL,
  アイドル: IDOL,
  地下アイドル: IDOL,
  俳優: {
    label: "俳優",
    outfit: "a neat elegant formal outfit suited to an actor at a press event",
    tone: "落ち着いた、丁寧で知的な口調",
    voice: "落ち着いた、知的な女性の声",
  },
  モデル: {
    label: "モデル",
    outfit: "a fashionable trendy outfit like a fashion model on a runway",
    tone: "洗練された、さっぱりした口調",
    voice: "洗練された、落ち着いた女性の声",
  },
  Vチューバー: {
    label: "Vチューバー",
    outfit: "a colorful fantasy virtual-streamer outfit with decorative accessories",
    tone: "明るくテンションの高い配信者の口調",
    voice: "テンションの高い、明るい女性の声",
  },
  YouTuber: {
    label: "YouTuber",
    outfit: "a casual trendy streetwear outfit like a video creator",
    tone: "親しみやすく、くだけた口調",
    voice: "親しみやすい、元気な女性の声",
  },
  グラビアアイドル: {
    label: "グラビアアイドル",
    outfit: "a stylish summer casual outfit with a sundress and sandals",
    tone: "明るく親しみやすい口調",
    voice: "明るく、やわらかい女性の声",
  },
  AV女優: {
    label: "AV女優",
    outfit: "a stylish everyday casual outfit with a blouse and a skirt",
    tone: "親しみやすく、やわらかい口調",
    voice: "やわらかく、親しみやすい女性の声",
  },
};

/** The category a group sits in and the top item above it ("48グループ" in "歌手"). */
export function categoryOfGroup(group: string): { name: string; root: string } | undefined {
  const all = idolCategories();
  const entry = all.find((category) => category.groups.some((row) => row.group === group));
  if (!entry) return undefined;
  return { name: entry.name, root: entry.parent ?? entry.name };
}

/** The outfit and tone for a group; none for anime categories (the work's own costume is used) and unknown places. */
export function groupStyleFor(group: string): GroupStyle | undefined {
  const place = categoryOfGroup(group);
  if (!place) return undefined;
  if (place.name === "48グループ") return FORTY_EIGHT;
  if (place.name === "坂道シリーズ") return SAKAMICHI;
  return BY_ROOT[place.root] ?? BY_ROOT[place.name];
}

/** The lines of a memo that tell how the person talks ("口調: …", "話し方：…"). */
export function toneLinesFromMemo(memo: string): string[] {
  return memo
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => /^(口調|話し方|しゃべり方|口ぐせ|口癖)\s*[:：]/.test(line))
    .map((line) =>
      line.replace(
        /^(口調|話し方|しゃべり方|口ぐせ|口癖)\s*[:：]\s*/,
        (_m, key: string) => `${key === "口癖" || key === "口ぐせ" ? "口癖" : "話し方"}: `,
      ),
    )
    .slice(0, 6);
}
