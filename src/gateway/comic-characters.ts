// The character library: every comic character made in any series, with a name, an icon (a face
// picture), the character sheet, how they look and talk, and their trained LoRA. Series casts
// link to library entries by characterId, so a character trained once looks the same in every
// series they appear in.

import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { characterRuleEnabled, heightRules } from "./blocked-terms-config.js";
import {
  CHARACTER_OPTION_CATALOG,
  LEGACY_OPTION_ITEMS,
  type CharacterOptionItem,
  type CharacterOptionPart,
} from "./character-options.js";
import { type ComicCharacter, cleanCharacter, clip } from "./comic-series.js";
import { resolveOpenClawHome } from "./partner-store.js";
import { normalizeVoiceCaption } from "./voice-caption.js";

export type LoraStatus = "none" | "queued" | "training" | "ready" | "failed";

/** What the user filled in when creating a character (Japanese, shown as the profile card). */
export type CharacterSpec = {
  // ── 基本 ────────────────────────────────────
  gender?: string;
  age?: number;
  heightCm?: number;
  body?: string;
  // ── 見た目・外見 ───────────────────────────────
  hair?: string; // 髪の色・長さ・波・ウェーブなど
  hairstyle?: string; // 髪型（ツインテール・ポニーテール・お団子・型など）
  eyes?: string; // 目の色・形（たれ目・吹き上げ目など）
  contactLens?: string; // カラコン・コンタクトの色（赤いカラコン、ハートカラコンなど）
  faceType?: string; // 顔の印象（童顔・大人びた・冷たい系・やさしい系など）
  skin?: string; // 肌の色合い
  breastSize?: string; // 胸のサイズ・形（英語タグ向けに記述）
  waistHip?: string; // ウエスト・ヒップラインの印象
  // ── 服装・アクセサリー ───────────────────────
  outfit?: string; // メインの服装（トップス・ボトムス、ワンピースなど）
  underwear?: string; // 下着（色・素材・デザイン）
  legwear?: string; // 靴下・ニーハイ・ストッキング・ガーターベルトなど
  shoes?: string; // 靴・ヒール・ブーツ・サンダル
  accessories?: string; // アクセサリー（眼鏡・首輪・イヤリング・こうもりなど）
  nails?: string; // ネイル（色・デザイン）
  makeup?: string; // メイク・化粧
  lipColor?: string; // 口紅の色・リップ（真っ赤なルージュ、ベビーピンクなど）
  features?: string; // 特徴（エルフ耳・しっぽ・角・アホゲなど）
  bodyMarks?: string; // タトゥー・ほくろ・傷・刺青など
  legs?: string; // 脚（細い・むっちり・長いなど）
  hips?: string; // お尻
  muscle?: string; // 筋肉・引き締まり
  neckArms?: string; // 首・肩・腕
  lookNotes?: string; // 外見オプションの小分類ごとの自由入力（「小分類名：内容」を「 / 」でつないだ1行）
  lookOptions?: string; // 外見オプションの id（カンマ区切り。ピアス・眼鏡・猫耳など）
  fragrance?: string; // 香り・フレグランス
  // ── 内面・プロフィール ────────────────────────
  personality?: string;
  speech?: string;
  firstPerson?: string;
  background?: string;
  occupation?: string; // 職業・立場
  hobbies?: string; // 趣味
  livingPlace?: string; // 住んでいる場所・居住環境
  /** Inner life, written into every script (comics and motion comics). */
  catchphrase?: string;
  likes?: string;
  dislikes?: string;
  favoriteFood?: string; // 好きな食べ物・飲み物
  skills?: string;
  secret?: string;
  relations?: string;
  treasured?: string; // 一番大切なもの・宝物
  // ── 成人向け ─────────────────────────────────
  /** Adult scenes (motion comics, adult series): what she likes, where she is sensitive, how she sounds. */
  adultLikes?: string; // 好きなこと・好きな体位
  weakSpots?: string; // 感じやすい部位・弱いところ
  nippleShape?: string; // 乳首の形（大きめ・小さめ・斥った・ピンクサイズなど）
  nippleColor?: string; // 乳首の色（ピンク・視紅・濠い色・褐色など）
  kinks?: string; // 性癖
  dominance?: string; // 攻め・受け・どちらでも
  experience?: string; // 性経験・経験度（乾いている・経験豊富など）
  preferredPlay?: string; // 好きなプレイの種類（SM・調教・バニラなど）
  breathPlay?: string; // 首絞め・腹部圧迫・息詰めプレイの詳細
  adultUnderwear?: string; // ランジェリー・部屋着・寄り道着
  moanStyle?: string;
  climaxStyle?: string; // 絶頂の仕方・達し方
  afterPlay?: string; // 事後・余韻・事後の様子
  afterWords?: string; // 事後に言うこと・言葉
  secretKink?: string; // 本当は好きだけど言えない性癖
  adultNg?: string;
  // ── 禁止タグ ─────────────────────────────────
  /** Extra negative-prompt tags for image/video generation (English, comma-separated).
   *  Lets the user add, remove or adjust blocked content per character at any time. */
  customNegative?: string;
};

export const SPEC_LABELS: Record<keyof CharacterSpec, string> = {
  // 基本
  gender: "性別",
  age: "年齢",
  heightCm: "身長（cm）",
  body: "体型",
  // 見た目
  hair: "髪の色・長さ",
  hairstyle: "髪型（ツインテール・ポニーテールなど）",
  eyes: "目の色・形",
  contactLens: "カラコン・コンタクトの色",
  faceType: "顔の印象（童顔・大人びた・冷たい系など）",
  skin: "肌",
  breastSize: "胸のサイズ・形",
  waistHip: "ウエスト・ヒップラインの印象",
  // 服装・アクセサリー
  outfit: "服装（トップス・ボトムス・ワンピースなど）",
  underwear: "下着（色・素材・デザイン）",
  legwear: "靴下・ニーハイ・ストッキング・ガーターベルト",
  shoes: "靴・ヒール・ブーツ",
  accessories: "アクセサリー（眼鏡・首輪・イヤリング・こうもりなど）",
  nails: "ネイル（色・デザイン）",
  makeup: "メイク・化粧",
  lipColor: "口紅の色・リップ",
  features: "特徴（エルフ耳・しっぽ・角・アホゲなど）",
  bodyMarks: "タトゥー・ほくろ・傷",
  legs: "脚",
  hips: "お尻",
  muscle: "筋肉・引き締まり",
  neckArms: "首・肩・腕",
  lookOptions: "外見オプション",
  lookNotes: "外見の追記",
  fragrance: "香り",
  // 内面・プロフィール
  personality: "性格",
  speech: "口調",
  firstPerson: "一人称",
  background: "生い立ち・設定",
  occupation: "職業・立場",
  hobbies: "趣味",
  livingPlace: "住んでいる場所",
  catchphrase: "口癖",
  likes: "好きなもの",
  dislikes: "苦手なもの",
  favoriteFood: "好きな食べ物・飲み物",
  skills: "特技",
  secret: "秘密",
  relations: "関係・呼び方",
  treasured: "一番大切なもの・宝物",
  // 成人向け
  adultLikes: "好きなこと・好きな体位",
  weakSpots: "弱いところ・感じやすいところ",
  nippleShape: "乳首の形",
  nippleColor: "乳首の色",
  kinks: "性癖",
  dominance: "攻め・受け",
  experience: "性経験・経験度",
  preferredPlay: "好きなプレイの種類",
  breathPlay: "首絞め・腹部圧迫・息詰めプレイ",
  adultUnderwear: "ランジェリー・部屋着",
  moanStyle: "喘ぎ方・声の出し方",
  climaxStyle: "絶頂の仕方",
  afterPlay: "事後・余韻の様子",
  afterWords: "事後に言うこと・言葉",
  secretKink: "本当は好きだけど言えない性癖",
  adultNg: "苦手なこと・NG",
  // 禁止タグ
  customNegative: "追加の禁止タグ（英語・カンマ区切り）",
};

/** The adult part of a spec: only adult scripts (motion comics, adult series) read it. */
export const ADULT_SPEC_KEYS: ReadonlyArray<keyof CharacterSpec> = [
  "adultLikes",
  "weakSpots",
  "kinks",
  "dominance",
  "experience",
  "preferredPlay",
  "breathPlay",
  "adultUnderwear",
  "moanStyle",
  "climaxStyle",
  "afterPlay",
  "afterWords",
  "secretKink",
  "adultNg",
];

/**
 * The look options that describe the character herself (piercings, marks, ears, limbs, aids...).
 * Chosen once in the creator, they stay in every picture of her. They are grouped by genre and
 * part (see character-options.ts); a part that allows only one keeps the first one given.
 */
// Items that mean the same as another one are not listed any more; their ids still work for saved characters.
const LEGACY_PART: CharacterOptionPart = { name: "（以前の項目）", pick: "many", items: [] };
const OPTION_BY_ID = new Map<string, { item: CharacterOptionItem; part: CharacterOptionPart }>([
  ...CHARACTER_OPTION_CATALOG.flatMap((genre) =>
    genre.parts.flatMap((part) => part.items.map((item) => [item.id, { item, part }] as const)),
  ),
  ...LEGACY_OPTION_ITEMS.map((item) => [item.id, { item, part: LEGACY_PART }] as const),
]);

function characterOption(id: string): CharacterOptionItem | undefined {
  return OPTION_BY_ID.get(id)?.item;
}

/** Known option ids from a comma-separated string or a list; anything else is dropped. */
export function characterOptionIds(raw: unknown): string[] {
  const ids = Array.isArray(raw) ? raw.map(String) : String(raw ?? "").split(/[,、\s]+/);
  const out: string[] = [];
  const takenSingles = new Set<CharacterOptionPart>();
  for (const id of ids.map((s) => s.trim())) {
    const found = OPTION_BY_ID.get(id);
    if (!found || out.includes(id)) continue;
    if (found.part.pick === "one") {
      if (takenSingles.has(found.part)) continue;
      takenSingles.add(found.part);
    }
    out.push(id);
  }
  return out;
}

/** English tags of the chosen options. */
export function characterOptionTags(raw: unknown): string {
  return characterOptionIds(raw)
    .map((id) => characterOption(id)?.tags ?? "")
    .filter(Boolean)
    .join(", ");
}

/** Japanese names of the chosen options. */
export function characterOptionLabels(raw: unknown): string[] {
  return characterOptionIds(raw).map((id) => characterOption(id)?.label ?? id);
}

/** Joins tag lists, keeping the first of any tag that appears twice. */
export function mergeLookTags(...parts: string[]): string {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of parts) {
    for (const tag of part
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)) {
      const key = tag.toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        out.push(tag);
      }
    }
  }
  return out.join(", ");
}

export function cleanSpec(raw: unknown): CharacterSpec {
  const r = (raw ?? {}) as Record<string, unknown>;
  const text = (key: keyof CharacterSpec, max = 120) => {
    const v = clip(r[key], max);
    return v ? { [key]: v } : {};
  };
  const age = Number(r.age);
  const height = Number(r.heightCm);
  // カスタムキー（既知キー以外）をそのまま保持（上限500文字）
  const knownKeys = new Set(
    Object.keys({
      gender: 1,
      age: 1,
      heightCm: 1,
      body: 1,
      hair: 1,
      hairstyle: 1,
      eyes: 1,
      contactLens: 1,
      faceType: 1,
      skin: 1,
      breastSize: 1,
      waistHip: 1,
      outfit: 1,
      underwear: 1,
      legwear: 1,
      shoes: 1,
      accessories: 1,
      nails: 1,
      makeup: 1,
      lipColor: 1,
      features: 1,
      bodyMarks: 1,
      fragrance: 1,
      personality: 1,
      speech: 1,
      firstPerson: 1,
      background: 1,
      occupation: 1,
      hobbies: 1,
      livingPlace: 1,
      catchphrase: 1,
      likes: 1,
      dislikes: 1,
      favoriteFood: 1,
      skills: 1,
      secret: 1,
      relations: 1,
      treasured: 1,
      adultLikes: 1,
      weakSpots: 1,
      nippleShape: 1,
      nippleColor: 1,
      kinks: 1,
      dominance: 1,
      experience: 1,
      preferredPlay: 1,
      breathPlay: 1,
      adultUnderwear: 1,
      moanStyle: 1,
      climaxStyle: 1,
      afterPlay: 1,
      afterWords: 1,
      secretKink: 1,
      adultNg: 1,
      customNegative: 1,
      legs: 1,
      hips: 1,
      muscle: 1,
      neckArms: 1,
      lookOptions: 1,
      lookNotes: 1,
    }),
  );
  const customFields: Record<string, string> = {};
  for (const k of Object.keys(r)) {
    if (!knownKeys.has(k)) {
      const v = clip(r[k], 500);
      if (v) customFields[k] = v;
    }
  }
  return {
    ...customFields,
    ...text("gender", 20),
    ...(Number.isFinite(age) && age > 0 ? { age: Math.round(age) } : {}),
    ...(Number.isFinite(height) && height >= 50 && height <= 200
      ? { heightCm: Math.round(height) }
      : {}),
    ...text("body"),
    // 見た目
    ...text("hair"),
    ...text("hairstyle", 120),
    ...text("eyes"),
    ...text("contactLens", 120),
    ...text("faceType", 120),
    ...text("skin"),
    ...text("breastSize", 120),
    ...text("waistHip", 120),
    ...text("legs", 120),
    ...text("hips", 120),
    ...text("muscle", 120),
    ...text("neckArms", 120),
    ...text("lookNotes", 1500),
    ...(characterOptionIds(r.lookOptions).length > 0
      ? { lookOptions: characterOptionIds(r.lookOptions).join(",") }
      : {}),
    // 服装・アクセサリー
    ...text("outfit", 200),
    ...text("underwear", 200),
    ...text("legwear", 200),
    ...text("shoes", 120),
    ...text("accessories", 200),
    ...text("nails", 120),
    ...text("makeup", 120),
    ...text("lipColor", 80),
    ...text("features", 200),
    ...text("bodyMarks", 200),
    ...text("fragrance", 80),
    // 内面・プロフィール
    ...text("personality", 200),
    ...text("speech", 120),
    ...text("firstPerson", 20),
    ...text("background", 400),
    ...text("occupation", 120),
    ...text("hobbies", 200),
    ...text("livingPlace", 120),
    ...text("catchphrase", 120),
    ...text("likes", 200),
    ...text("dislikes", 200),
    ...text("favoriteFood", 120),
    ...text("skills", 200),
    ...text("secret", 200),
    ...text("relations", 300),
    ...text("treasured", 200),
    // 成人向け
    ...text("adultLikes", 300),
    ...text("weakSpots", 200),
    ...text("nippleShape", 80),
    ...text("nippleColor", 80),
    ...text("kinks", 300),
    ...text("dominance", 80),
    ...text("experience", 200),
    ...text("preferredPlay", 200),
    ...text("breathPlay", 300),
    ...text("adultUnderwear", 200),
    ...text("moanStyle", 200),
    ...text("climaxStyle", 200),
    ...text("afterPlay", 200),
    ...text("afterWords", 200),
    ...text("secretKink", 300),
    ...text("adultNg", 200),
    // 禁止タグ（英語、画像・動画生成のネガティブプロンプトに追加される）
    ...text("customNegative", 500),
  };
}

/** Height for the image model, which understands words, not centimetres.
 *  For short adult women the function adds "slim" to prevent the model widening the body
 *  as a workaround – the narrowness must be signalled explicitly. */
/**
 * The chest size as weighted tags, so the pictures really differ from one size to the next. The AI that
 * writes the look often kept "medium breasts"; these fixed tags are added after its words, and its own
 * size words are removed (see stripBreastTags).
 */
export function breastTags(size: string | undefined): string {
  const text = (size ?? "").trim();
  if (!text) return "";
  if (/超乳|ミルクタンク|gigantic/i.test(text)) return "(gigantic breasts:1.5), (huge breasts:1.3)";
  if (/爆乳|超巨乳/.test(text)) return "(huge breasts:1.4), (gigantic breasts:1.2)";
  if (/巨乳|でかい|豊満|huge/i.test(text)) return "(huge breasts:1.3), (large breasts:1.2)";
  if (/大きめ|大きい|やや大|large|big/i.test(text)) return "(large breasts:1.2)";
  if (/控えめ|小さめ|小ぶり|small/i.test(text)) return "(small breasts:1.2)";
  if (/普通|ふつう|標準|並|medium/i.test(text)) return "medium breasts";
  return "";
}

/** Removes the size words the AI wrote (small/medium/large/huge/gigantic breasts, flat chest, with or without weights). */
export function stripBreastTags(look: string): string {
  return look
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean)
    .filter(
      (tag) =>
        !/^\(?\s*(flat chest|(small|medium|large|big|huge|gigantic|giant|massive)\s+(breasts|boobs|tits))(\s*:\s*[\d.]+)?\s*\)?$/i.test(
          tag,
        ),
    )
    .join(", ");
}

export function heightTags(spec: CharacterSpec): string {
  const cm = spec.heightCm;
  if (!cm) return "";
  const male = /\u7537|male|boy/i.test(spec.gender ?? "");
  const rules = heightRules();
  const cfg = male ? rules.male : rules.female;
  const short = cfg?.shortThresholdCm ?? (male ? 165 : 152);
  const tall = cfg?.tallThresholdCm ?? (male ? 180 : 168);
  const veryTall = cfg?.veryTallThresholdCm ?? (male ? 190 : 178);
  if (cm >= veryTall) return "very tall, long legs";
  if (cm >= tall) return "tall, long legs";
  if (cm < short) {
    // "short" alone causes the model to widen the body; anchor it with slim + adult proportions.
    const extra =
      cfg?.shortExtra ??
      (male ? "short stature, slim build" : "short stature, slim waist, mature female");
    return extra;
  }
  return "";
}

/** Exact height and visible body-scale cues for every image prompt.
 *  A lone character has no absolute ruler, so centimetres are paired with proportion words that
 *  image models can render while keeping an adult body shape. */
export function imageHeightTags(spec: CharacterSpec): string {
  const cm = spec.heightCm;
  if (!cm) return "";
  const male = /\u7537|male|boy/i.test(spec.gender ?? "");
  const adult = male
    ? "adult man"
    : /\u5973|female|woman/i.test(spec.gender ?? "")
      ? "adult woman"
      : "adult person";
  const exact = `(${cm} cm tall ${adult}:1.25)`;
  const proportions =
    cm >= 195
      ? "exceptionally tall, towering stature, very long legs, elongated but natural adult proportions"
      : cm >= 185
        ? "noticeably tall stature, long legs, natural adult proportions"
        : cm <= 140
          ? "very short adult stature, compact natural adult proportions"
          : cm <= 152
            ? "short adult stature, compact natural adult proportions"
            : "natural adult proportions";
  // No framing word here: this goes into every shot, and "full body" made face close-ups draw the
  // whole body. The standing picture and the sheets add their own "full body".
  return mergeLookTags(exact, heightTags(spec), proportions);
}

export function specSummary(
  spec: CharacterSpec,
  options: { adult?: boolean } = { adult: true },
): string {
  return (Object.keys(SPEC_LABELS) as Array<keyof CharacterSpec>)
    .filter((key) => spec[key] !== undefined && spec[key] !== "")
    .filter((key) => options.adult !== false || !ADULT_SPEC_KEYS.includes(key))
    .map((key) => {
      const value = spec[key];
      const shown =
        key === "heightCm"
          ? `${value}cm`
          : key === "age"
            ? `${value}歳`
            : key === "lookOptions"
              ? characterOptionLabels(value).join("・")
              : String(value);
      return `${SPEC_LABELS[key]}: ${shown}`;
    })
    .join(" / ");
}

/** Asks the model to turn the Japanese spec into a look (English tags) and a profile line. */
export function creatorSystemPrompt(): string {
  const adultsOnly = characterRuleEnabled("adultsOnly");
  const noChildTags = characterRuleEnabled("forbidChildlikeTags");
  const noPetite = characterRuleEnabled("forbidPetite");
  const forbidden = (heightRules().forbiddenTags ?? []).join(", ");

  const adultLine = adultsOnly ? "登場人物は成人。" : "";
  const childLine = noChildTags ? "子どもっぽい体型や年齢を示すタグは使わない。" : "";
  const petiteLine = noPetite
    ? `身長が低い（小柄・低身長）場合でも slim, mature female などで大人の体型を明示する。${forbidden ? forbidden + " は絶対に使わない。" : ""}`
    : "";

  return `あなたはアニメ・漫画のキャラクターデザイナーです。入力された設定から、画像生成AI用の見た目タグと、脚本家向けの人物紹介を作ります。
次のJSONだけを出力してください。
{"look":"English danbooru tags, 15-25 tags: 1girl or 1boy, mature female or mature male, hair color, hair length and style, eye color, skin tone, body type and proportions, distinctive features, outfit with colors and details","profile":"役割・性格・口調・一人称を1〜2文で（日本語、120文字以内）"}
- 入力に書かれていることは全部タグにする。書かれていないことは、設定に合うものを選んで補う。
- ${adultLine}${childLine}
- ${petiteLine}`;
}

export function parseCreatorReply(reply: string): { look: string; profile: string } {
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(reply)?.[1] ?? reply;
  const start = fenced.indexOf("{");
  const end = fenced.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("見た目を作れませんでした");
  const data = JSON.parse(fenced.slice(start, end + 1)) as Record<string, unknown>;
  const look = cleanCharacter({ name: "x", look: data.look }).look;
  if (!look) throw new Error("見た目を作れませんでした");
  return { look, profile: clip(data.profile, 300) };
}

export type LibraryCharacter = ComicCharacter & {
  id: string;
  /** Settings entered in the character creator, if made there. */
  spec?: CharacterSpec;
  /**
   * The partner sheet's items (label → text) typed for this character: how she talks, what she
   * thinks, her past. They go to the partner this character chats as (character-partner.ts).
   */
  sheetValues?: Record<string, string>;
  /** The picture model her sheets are drawn with (chosen in the app; used again when she is redrawn). */
  imageModel?: string;
  /** Her voice: a voice-studio voice (spoken in motion comics), or a described one. */
  voiceId?: string;
  voiceCaption?: string;
  /** Outfits she can wear (English clothing tags); the look's own clothes are the default. */
  outfits?: Array<{ name: string; tags: string }>;
  /** A front/side/back sheet drawn to look at (not used for training). */
  turnaroundJobId?: string;
  /** Square face picture shown in the app (PNG), when there is one. */
  icon?: string;
  /** The library folder she is in (none = unsorted) and her place in it (smaller comes first). */
  folderId?: string;
  libraryOrder?: number;
  /**
   * A photo the character was made from. The front sheet is then drawn from it (same face, hair and
   * clothes) instead of from the look text.
   */
  photoRef?: string;
  /** All selected reference photos (the first is photoRef). */
  photoRefs?: string[];
  /** "subject" keeps the first person's identity; blend and roles are explicit alternatives. */
  photoMode?: "subject" | "blend" | "roles";
  /** For "roles": what each photo is used for (Japanese labels, in the order of the photos). */
  photoRoles?: string[];
  /** "high": the sheet is enlarged and cleaned up after it is drawn. */
  photoQuality?: "normal" | "high";
  /** How the photo becomes her picture: as it is (photo), an anime picture, or a soft illustration. */
  photoStyle?: "photo" | "anime" | "illustration";
  /** What the reference picture is (chosen with a switch, not guessed): a photo, or an anime picture / illustration. */
  photoSource?: "photo" | "anime";
  /** True when none of the reference pictures shows the body (face only): the body and clothes are then chosen at random. */
  photoFaceOnly?: boolean;
  /** Qwen editors or "ipadapter" (SDXL IP-Adapter Plus visual reference conditioning). */
  photoEngine?: "fast" | "precise" | "edit2509" | "ipadapter";
  /** What was read from the photo (hair, eyes, skin, clothes…), told to the editor in words. */
  photoTraits?: string;
  loraStatus: LoraStatus;
  /** Progress note while training ("学習用の絵を作っています 12/24", "学習中 45%"). */
  loraStep?: string;
  loraError?: string;
  /** お気に入り登録（ホームに表示、パートナー画面に優先表示） */
  isFavorite?: boolean;
  /** 紐づいているパートナーID（キャラ図鑑⇔パートナー双方向同期） */
  partnerId?: string;
  /**
   * 多人数シーン対応: このキャラが何人目として登場できるか
   * 例: sceneCapacity = 4 → 2人・3人・4人シーンすべてに使用可
   * 省略 = 1人のみ（デフォルト）
   */
  sceneCapacity?: 1 | 2 | 3 | 4;
  createdAt: number;
  updatedAt: number;
};

/** The current drawable look, including catalog choices edited after the character was created. */
export function characterLook(character: Pick<LibraryCharacter, "look" | "spec">): string {
  return [
    mergeLookTags(
      // Characters saved before 2026-10-11 still carry the old framing tag in their stored look.
      character.look
        .split(",")
        .filter((tag) => tag.trim() !== "full body shown from head to feet")
        .join(","),
      characterOptionTags(character.spec?.lookOptions),
      character.spec ? imageHeightTags(character.spec) : "",
    ),
    character.spec?.lookNotes?.trim(),
  ]
    .filter(Boolean)
    .join(", ");
}

function replaceCharacterOptionTags(
  look: string,
  previousOptions: string | undefined,
  nextOptions: string | undefined,
): string {
  const previousTags = new Set(
    characterOptionTags(previousOptions)
      .split(",")
      .map((tag) => tag.trim().toLowerCase())
      .filter(Boolean),
  );
  const baseLook = look
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag && !previousTags.has(tag.toLowerCase()))
    .join(", ");
  return mergeLookTags(baseLook, characterOptionTags(nextOptions));
}

export function isCharacterId(id: string): boolean {
  return /^chara-[a-f0-9]{8}$/.test(id);
}

export function characterDir(): string {
  return path.join(resolveOpenClawHome(), "media", "comics", "characters");
}

/** Files of one character: icon, sheets, LoRA dataset and weights. */
export function characterAssetDir(id: string): string {
  if (!isCharacterId(id)) throw new Error("Invalid character id");
  return path.join(characterDir(), id);
}

function characterFile(id: string): string {
  if (!isCharacterId(id)) throw new Error("Invalid character id");
  return path.join(characterDir(), `${id}.json`);
}

export async function listCharacters(): Promise<LibraryCharacter[]> {
  let names: string[] = [];
  try {
    names = await fs.readdir(characterDir());
  } catch {
    return [];
  }
  const all: LibraryCharacter[] = [];
  for (const name of names) {
    if (!/^chara-[a-f0-9]{8}\.json$/.test(name)) continue;
    try {
      all.push(
        JSON.parse(await fs.readFile(path.join(characterDir(), name), "utf-8")) as LibraryCharacter,
      );
    } catch {
      // Skip a broken file.
    }
  }
  return all.toSorted((a, b) => b.updatedAt - a.updatedAt);
}

export async function getCharacter(id: string): Promise<LibraryCharacter | undefined> {
  if (!isCharacterId(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(characterFile(id), "utf-8")) as LibraryCharacter;
  } catch {
    return undefined;
  }
}

/** Merges a few fields into a stored character and saves it; undefined when there is no such character. */
export async function updateCharacter(
  id: string,
  patch: Partial<LibraryCharacter>,
): Promise<LibraryCharacter | undefined> {
  const current = await getCharacter(id);
  if (!current) return undefined;
  const updated: LibraryCharacter = { ...current, ...patch, id: current.id, updatedAt: Date.now() };
  await saveCharacter(updated);
  return updated;
}

export async function saveCharacter(character: LibraryCharacter): Promise<void> {
  await fs.mkdir(characterDir(), { recursive: true });
  const file = characterFile(character.id);
  // tmp は characters/ の親（comics/）に置く。
  // characters/ 内には chara-XXXXXXXX/ というアセットサブフォルダが存在するため、
  // 同ディレクトリに chara-XXXXXXXX.json.PID.tmp を作ると Windows の rename が
  // ENOENT で失敗するケースがある。親フォルダに置くことで衝突を回避する。
  const tmp = path.join(
    path.dirname(characterDir()),
    `${character.id}.${process.pid}.${randomBytes(4).toString("hex")}.tmp`,
  );
  await fs.writeFile(tmp, JSON.stringify(character, null, 2), "utf-8");
  await fs.rename(tmp, file);
}

export async function deleteCharacter(id: string): Promise<boolean> {
  try {
    await fs.rm(characterFile(id));
    await fs.rm(characterAssetDir(id), { recursive: true, force: true });
    return true;
  } catch {
    return false;
  }
}

/** A trigger word for the LoRA: rare letters so it means nothing else to the model. */
export function triggerFor(id: string): string {
  return `ocx${id.slice(-6)}`;
}

/** Adds a series character to the library (or returns the linked entry). */
export async function registerCharacter(raw: ComicCharacter): Promise<LibraryCharacter> {
  if (raw.characterId) {
    const existing = await getCharacter(raw.characterId);
    if (existing) return existing;
  }
  const now = Date.now();
  const { characterId: _drop, ...base } = cleanCharacter(raw);
  const character: LibraryCharacter = {
    ...base,
    id: `chara-${randomBytes(4).toString("hex")}`,
    loraStatus: "none",
    createdAt: now,
    updatedAt: now,
  };
  await saveCharacter(character);
  return character;
}

/** Outfits as a list of {name, tags} or as text lines "名前: tags" (at most 12). */
export function cleanOutfits(raw: unknown): Array<{ name: string; tags: string }> {
  const items: Array<{ name?: unknown; tags?: unknown }> = Array.isArray(raw)
    ? raw
    : typeof raw === "string"
      ? raw.split(/\r?\n/).map((line) => {
          const [name, ...rest] = line.split(/[:：]/);
          return { name, tags: rest.join(":") };
        })
      : [];
  return items
    .map((o) => ({
      name: clip(o?.name, 20),
      tags: String(o?.tags ?? "")
        .replace(/[^a-zA-Z0-9 ,()'.\-]/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, 300),
    }))
    .filter((o) => o.name && o.tags)
    .slice(0, 12);
}

/** Clothing words in a look; an outfit replaces them. */
const CLOTHING_TAG =
  /(dress|shirt|blouse|skirt|shorts|pants|jeans|thighhighs|pantyhose|stockings|socks|jacket|coat|hoodie|sweater|cardigan|vest|kimono|yukata|apron|maid|nun|bikini|swimsuit|leotard|bodysuit|uniform|armor|cape|cloak|gloves|boots|shoes|heels|sandals|necktie|bowtie|lingerie|corset|garter|frills|lace|lolita|crop top|tank top|t-shirt|top\b)/i;

/** The look with its clothes swapped for an outfit's tags. */
export function lookWithOutfit(look: string, outfitTags: string): string {
  const kept = look
    .split(",")
    .map((t) => t.trim())
    .filter((t) => t && !CLOTHING_TAG.test(t));
  return [...kept, outfitTags].filter(Boolean).join(", ");
}

/** Partner-sheet items from the app: label → text, empty ones dropped (at most 200 items). */
export function cleanSheetValues(raw: unknown): Record<string, string> | undefined {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return undefined;
  const out: Record<string, string> = {};
  for (const [label, value] of Object.entries(raw as Record<string, unknown>).slice(0, 200)) {
    const key = clip(label, 60);
    const text = String(value ?? "")
      .trim()
      .slice(0, 600);
    if (key && text) out[key] = text;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

/** Name, profile, look and the setting sheet as edited in the app. */
export function applyCharacterEdit(
  character: LibraryCharacter,
  edit: {
    name?: unknown;
    profile?: unknown;
    look?: unknown;
    spec?: unknown;
    voiceId?: unknown;
    voiceCaption?: unknown;
    outfits?: unknown;
    sheetValues?: unknown;
    /** お気に入り登録フラグ */
    isFavorite?: unknown;
    /** 紐づくパートナーID（キャラ↔パートナー双方向同期） */
    partnerId?: unknown;
    /** 多人数シーンでの登場可能人数上限 (1〜4) */
    sceneCapacity?: unknown;
  },
): LibraryCharacter {
  const next = { ...character };
  if (typeof edit.voiceId === "string") {
    next.voiceId = /^voice-[a-z0-9-]{8,60}$/.test(edit.voiceId) ? edit.voiceId : undefined;
  }
  if (typeof edit.voiceCaption === "string") {
    next.voiceCaption = normalizeVoiceCaption(edit.voiceCaption) || undefined;
  }
  if (edit.outfits !== undefined) next.outfits = cleanOutfits(edit.outfits);
  if (edit.spec && typeof edit.spec === "object") {
    next.spec = cleanSpec(edit.spec);
    next.look = replaceCharacterOptionTags(
      character.look,
      character.spec?.lookOptions,
      next.spec?.lookOptions,
    );
  }
  if (edit.sheetValues !== undefined) next.sheetValues = cleanSheetValues(edit.sheetValues);
  const name = clip(edit.name, 20);
  if (name) next.name = name;
  if (typeof edit.profile === "string") next.profile = clip(edit.profile, 300);
  if (typeof edit.look === "string") {
    const look = cleanCharacter({ name: "x", look: edit.look }).look;
    if (look && look !== character.look) next.look = look;
  }
  // お気に入り・パートナー同期・多人数シーン
  if (typeof edit.isFavorite === "boolean") next.isFavorite = edit.isFavorite;
  if (typeof edit.partnerId === "string") next.partnerId = edit.partnerId.trim() || undefined;
  if (
    typeof edit.sceneCapacity === "number" &&
    [1, 2, 3, 4].includes(edit.sceneCapacity as number)
  ) {
    next.sceneCapacity = edit.sceneCapacity as 1 | 2 | 3 | 4;
  }
  next.updatedAt = Date.now();
  return next;
}

/**
 * The cast a series draws with: each linked entry takes the library's current name, look,
 * sheet and LoRA (only a finished LoRA is used).
 */
export async function resolveCast(
  cast: ComicCharacter[],
  options: { adult?: boolean } = {},
): Promise<ComicCharacter[]> {
  const out: ComicCharacter[] = [];
  for (const member of cast) {
    const entry = member.characterId ? await getCharacter(member.characterId) : undefined;
    if (!entry) {
      out.push(member);
      continue;
    }
    // The setting sheet rides with the profile so every script keeps the personality, catchphrase
    // and first person; its adult part only goes to adult series.
    const sheet = entry.spec ? specSummary(entry.spec, { adult: options.adult === true }) : "";
    out.push({
      characterId: entry.id,
      name: entry.name,
      look: characterLook(entry),
      profile: sheet ? `${entry.profile}\n設定: ${sheet}` : entry.profile,
      ...(entry.refImage ? { refImage: entry.refImage } : {}),
      ...(entry.sheetJobId ? { sheetJobId: entry.sheetJobId } : {}),
      ...(entry.spec?.heightCm ? { heightCm: entry.spec.heightCm } : {}),
      ...(entry.spec?.customNegative ? { negative: entry.spec.customNegative } : {}),
      ...(entry.loraStatus === "ready" && entry.lora && entry.trigger
        ? { lora: entry.lora, trigger: entry.trigger }
        : {}),
    });
  }
  return out;
}

// ---------- LoRA dataset ----------

/**
 * Views for the training pictures, made from the character sheet with Qwen Image Edit. They vary
 * angle, expression, pose, framing, light and background but never the person, so the trigger
 * word learns who they are. The captions name only what varies.
 */
export const DATASET_VIEWS: Array<{ edit: string; tags: string }> = [
  {
    edit: "Show the same character from the front, upper body, smiling, plain white background.",
    tags: "upper body, front view, smile, white background",
  },
  {
    edit: "Show the same character in three-quarter view facing left, upper body, calm expression, plain grey background.",
    tags: "upper body, three-quarter view, looking to the side, grey background",
  },
  {
    edit: "Show the same character in profile facing right, head and shoulders, neutral expression.",
    tags: "portrait, from side, profile, simple background",
  },
  {
    edit: "Close-up of the same character's face, laughing with eyes closed.",
    tags: "close-up, face, laughing, closed eyes",
  },
  {
    edit: "Close-up of the same character's face, angry, frowning.",
    tags: "close-up, face, angry, frown",
  },
  {
    edit: "Close-up of the same character's face, crying with tears.",
    tags: "close-up, face, crying, tears",
  },
  {
    edit: "Close-up of the same character's face, surprised with open mouth.",
    tags: "close-up, face, surprised, open mouth",
  },
  {
    edit: "Close-up of the same character's face, blushing and embarrassed, looking away.",
    tags: "close-up, face, blush, embarrassed, looking away",
  },
  {
    edit: "The same character sitting on a chair in a cozy room, full body.",
    tags: "full body, sitting, chair, indoors",
  },
  {
    edit: "The same character walking on a city street in daylight, full body, from the side.",
    tags: "full body, walking, from side, street, day",
  },
  {
    edit: "The same character running toward the viewer in a grassy field, dynamic pose.",
    tags: "full body, running, dynamic pose, field, outdoors",
  },
  {
    edit: "The same character seen from behind, looking back over the shoulder.",
    tags: "from behind, looking back, cowboy shot",
  },
  {
    edit: "The same character seen from above, looking up at the viewer.",
    tags: "from above, looking up, upper body",
  },
  {
    edit: "The same character seen from below, low angle, arms crossed, confident.",
    tags: "from below, arms crossed, confident, cowboy shot",
  },
  {
    edit: "The same character at night under street lights, upper body, soft light.",
    tags: "upper body, night, street lights",
  },
  {
    edit: "The same character in a fantasy forest, holding out a hand, sunlight through trees.",
    tags: "cowboy shot, outstretched hand, forest, sunlight",
  },
  {
    edit: "The same character lying on a bed, relaxed, from above.",
    tags: "lying, on bed, from above, relaxed",
  },
  {
    edit: "The same character waving with one hand, cheerful, full body, plain background.",
    tags: "full body, waving, smile, simple background",
  },
  {
    edit: "The same character pointing at the viewer, determined expression, upper body.",
    tags: "upper body, pointing at viewer, determined",
  },
  {
    edit: "The same character sleeping peacefully, head on a pillow, close-up.",
    tags: "close-up, sleeping, closed eyes, pillow",
  },
  {
    edit: "The same character wearing a simple swimsuit at the beach, full body, keep the face, hair and body the same.",
    tags: "full body, swimsuit, beach, outdoors",
  },
  {
    edit: "The same character in casual clothes (t-shirt and jeans), standing, full body, keep the face and hair the same.",
    tags: "full body, standing, t-shirt, jeans, casual",
  },
  // Strong expressions, so comics and motion comics can draw them on the same face.
  {
    edit: "Close-up of the same character's face with an ahegao expression: eyes rolled upward, tongue out, drooling, heavy blush, sweat.",
    tags: "close-up, face, ahegao, rolling eyes, tongue out, drooling, heavy blush",
  },
  {
    edit: "Close-up of the same character's face, lovestruck, heart-shaped pupils, dreamy smile, blushing.",
    tags: "close-up, face, heart-shaped pupils, lovestruck, smile, blush",
  },
  {
    edit: "The same character fainted, lying limp on her back with eyes closed and a dizzy face, upper body.",
    tags: "upper body, fainted, unconscious, lying, closed eyes, dizzy",
  },
  {
    edit: "Close-up of the same character's face making a silly goofy face, tongue sticking out to the side, crossed eyes.",
    tags: "close-up, face, silly face, tongue out, crossed eyes",
  },
  {
    edit: "Close-up of the same character's face with empty lifeless eyes without highlights, blank expression, shadowed face.",
    tags: "close-up, face, empty eyes, expressionless, shaded face",
  },
  {
    edit: "Close-up of the same character's face with her eyes rolled back showing only the whites, mouth open.",
    tags: "close-up, face, white eyes, rolling eyes, open mouth",
  },
  {
    edit: "The same character panicking, sweating heavily, wide eyes, flustered, waving her hands, upper body.",
    tags: "upper body, panicking, flustered, sweat, wide-eyed, waving hands",
  },
];

/** What each training picture shows (same order as DATASET_VIEWS), for the 360° gallery. */
export const DATASET_LABELS: readonly string[] = [
  "正面・笑顔",
  "斜め左",
  "真横",
  "大笑い",
  "怒り",
  "泣き顔",
  "驚き",
  "照れ",
  "椅子に座る",
  "街を歩く（横）",
  "草原を走る",
  "後ろ姿・振り返り",
  "上から見下ろす",
  "下から見上げる・腕組み",
  "夜の街灯",
  "森の木漏れ日",
  "ベッドに寝そべる",
  "手を振る",
  "指さし",
  "寝顔",
  "水着・海辺",
  "Tシャツとジーンズ",
  "アヘ顔",
  "メロメロ（ハートの目）",
  "気絶",
  "おバカ顔",
  "目が死んでる",
  "白目",
  "焦り",
];

export function datasetCaption(trigger: string, gender: string, tags: string): string {
  return [trigger, gender, tags].filter(Boolean).join(", ");
}
