import { readFileSync } from "node:fs";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import { resolveOllamaBaseUrlForRun } from "../../../extensions/ollama/src/provider-base-url.js";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import type { AnyAgentTool } from "../../agents/tools/common.js";
import { createWebFetchTool, createWebSearchTool } from "../../agents/tools/web-tools.js";
import { readSubmittedImage, searchForReference } from "../character-creator.js";
import { libraryCharacterSection } from "../character-library-prompt.js";
import {
  enterGroupChatMode,
  GROUP_CHAT_MODELS,
  getGroupChatModelId,
  setGroupChatModelId,
} from "../group-chat-model.js";
import { parseJsonObject } from "../llm-json.js";
import { affectNote, applyStateChange, extractStateLine, loadAffect } from "../partner-affect.js";
import { researchIoFromTools } from "../partner-creation.js";
import { addKnowledge, fetchSourceText, knowledgeNoteFor } from "../partner-knowledge.js";
import {
  parsePartnerSheet,
  REAL_PERSON_RULES,
  REAL_PERSON_SECTION_TITLE,
} from "../partner-sheet-schema.js";
import {
  appendPartnerMood,
  bondRelationshipDirective,
  getPartnerDir,
  isValidPartnerId,
  loadPartnerBond,
  loadPartnerFacts,
  localDateKey,
  saveGroupChatHistory,
  savePartnerFacts,
  type PartnerFact,
  type PartnerMoodEntry,
} from "../partner-store.js";
import { type PartnerVoice, sanitizePartnerVoice } from "../partner-tts.js";
import { executeSpecialistTask } from "../specialist-router.js";
import {
  describeBusyStudio,
  describeStudioForChat,
  extractStudioAction,
  isChatModelLentOut,
  runStudioActionFromChat,
  STUDIO_CHAT_GUIDE,
} from "../studio-assistant.js";
import {
  cleanGeneratedText,
  echoesEarlierLine,
  isFailedReplyText,
  stripOwnNamePrefix,
} from "../text-hygiene.js";
import type { GatewayRequestHandlers } from "./types.js";

export interface PartnerProfile {
  id: string;
  name: string;
  avatarUrl: string;
  persona: string;
  modelId: string;
  reasoningEffort?: string;
  characterSheet?: string;
  settings?: PartnerSettings;
  /** The character-library entry this partner chats as (character-partner.ts keeps their sheets in step). */
  sourceCharacterId?: string;
  /** Modeled on a real person: the prompt adds REAL_PERSON_RULES (no sexual content, no private life). */
  realPerson?: boolean;
  createdAt: number;
  updatedAt: number;
}

/** Per-partner conversation preferences set from the app. */
export type PartnerSettings = {
  replyLength?: "short" | "normal" | "long";
  emoji?: "none" | "some" | "many";
  /** Sampling temperature, 0.2–1.2. */
  temperature?: number;
  /** Used when the app sends the default "auto" thinking mode. */
  thinkingDefault?: "instant" | "auto" | "deep";
  /** Allow the partner to start conversations (check-ins, birthdays, morning brief). */
  proactive?: boolean;
  /** Topics the workers research every morning for the greeting. */
  dailyTopics?: string[];
  /**
   * News partner: the morning message carries the whole researched brief (with its sources)
   * instead of mentioning one topic.
   */
  newsDigest?: boolean;
  /**
   * Generation assistant: the 1:1 chat sees the media/voice studio's state and can start,
   * cancel and train through <<studio>> lines (studio-assistant.ts).
   */
  studioAssistant?: boolean;
  /**
   * Character creator: pictures sent in the chat are read into look tags (local tagger) and
   * "調べて" uses the configured web search (character-creator.ts).
   */
  characterCreator?: boolean;
  /** Android text-to-speech voice, 0.5–2.0. */
  voicePitch?: number;
  voiceRate?: number;
  /** PC-side character voice (VOICEVOX / AivisSpeech); falls back to the phone's TTS when unset. */
  voice?: PartnerVoice;
};

const clampNumber = (value: unknown, min: number, max: number): number | undefined =>
  typeof value === "number" && Number.isFinite(value)
    ? Math.min(max, Math.max(min, value))
    : undefined;

/** Keeps only known, in-range settings so a client cannot store arbitrary data. */
export function sanitizePartnerSettings(raw: unknown): PartnerSettings {
  const input = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const pick = <T extends string>(value: unknown, allowed: readonly T[]): T | undefined =>
    allowed.includes(value as T) ? (value as T) : undefined;
  const topics = Array.isArray(input.dailyTopics)
    ? input.dailyTopics
        .filter((t): t is string => typeof t === "string" && t.trim().length > 0)
        .map((t) => t.trim().slice(0, 60))
        .slice(0, 5)
    : undefined;
  const out: PartnerSettings = {
    replyLength: pick(input.replyLength, ["short", "normal", "long"] as const),
    emoji: pick(input.emoji, ["none", "some", "many"] as const),
    temperature: clampNumber(input.temperature, 0.2, 1.2),
    thinkingDefault: pick(input.thinkingDefault, ["instant", "auto", "deep"] as const),
    proactive: typeof input.proactive === "boolean" ? input.proactive : undefined,
    dailyTopics: topics,
    newsDigest: typeof input.newsDigest === "boolean" ? input.newsDigest : undefined,
    studioAssistant: typeof input.studioAssistant === "boolean" ? input.studioAssistant : undefined,
    characterCreator:
      typeof input.characterCreator === "boolean" ? input.characterCreator : undefined,
    voicePitch: clampNumber(input.voicePitch, 0.5, 2),
    voiceRate: clampNumber(input.voiceRate, 0.5, 2),
    voice: input.voice === null ? undefined : sanitizePartnerVoice(input.voice),
  };
  return Object.fromEntries(
    Object.entries(out).filter(([, v]) => v !== undefined),
  ) as PartnerSettings;
}

export async function updatePartnerSettings(
  partnerId: string,
  patch: unknown,
): Promise<PartnerSettings> {
  const partners = await loadPartners();
  const partner = partners.find((p) => p.id === partnerId);
  if (!partner) {
    throw new Error(`Partner not found: ${partnerId}`);
  }
  partner.settings = sanitizePartnerSettings({ ...partner.settings, ...(patch as object) });
  partner.updatedAt = Date.now();
  await savePartners(partners);
  return partner.settings;
}

function partnerSettingsDirectives(settings: PartnerSettings | undefined): string[] {
  const lines: string[] = [];
  if (settings?.replyLength === "short") lines.push("- 返答は1〜2文で短く。");
  if (settings?.replyLength === "long") lines.push("- 返答はしっかり詳しく、話を広げて。");
  if (settings?.emoji === "none") lines.push("- 絵文字・顔文字は使わない。");
  if (settings?.emoji === "many") lines.push("- 絵文字・顔文字を多めに使って感情豊かに。");
  return lines;
}

const BUILT_IN_PARTNER_IDS = new Set(["partner-luna", "partner-alice", "partner-shion"]);

const DEFAULT_PARTNERS: PartnerProfile[] = [
  {
    id: "partner-luna",
    name: "ルナ (Luna)",
    avatarUrl: "",
    persona:
      "明るく優しく、聞き上手なAIパートナー。親しみやすいタメ口で話し、感情表現が豊か。ユーザーを全力で励ましサポートする。",
    modelId: "ollama/qwen2.5:latest",
    reasoningEffort: "none",
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
  {
    id: "partner-alice",
    name: "アリス (Alice)",
    avatarUrl: "",
    persona:
      "冷静沈着で知的な参謀系アシスタント。丁寧な敬語を使い、論理的で的確なアドバイスと深い分析を提供する。",
    modelId: "ollama/qwen2.5:latest",
    reasoningEffort: "none",
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
  {
    id: "partner-shion",
    name: "シオン (Shion)",
    avatarUrl: "",
    persona:
      "好奇心旺盛で元気いっぱいな後輩系キャラクター。新しい知識や技術に興味津々で、テンション高く話す。",
    modelId: "ollama/qwen2.5:latest",
    reasoningEffort: "none",
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
];

function getPartnersFilePath(): string {
  const openclawHome = process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
  return path.join(openclawHome, "partners.json");
}

async function loadPartners(): Promise<PartnerProfile[]> {
  const filePath = getPartnersFilePath();
  try {
    const data = await fs.readFile(filePath, "utf-8");
    const parsed = JSON.parse(data);
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed;
    }
  } catch {
    // File doesn't exist or invalid, initialize with defaults
  }
  await savePartners(DEFAULT_PARTNERS);
  return DEFAULT_PARTNERS;
}

async function savePartners(partners: PartnerProfile[]): Promise<void> {
  const filePath = getPartnersFilePath();
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, JSON.stringify(partners, null, 2), "utf-8");
}

function getOllamaUrl(): string {
  return resolveOllamaBaseUrlForRun() ?? "http://127.0.0.1:11434";
}

// ホームで設定されたllama-serverの現在アクティブモデルを取得する
// パートナー個別の modelId ではなく、VRAMにロード済みのモデルを統一的に使用
async function getActiveModelAlias(): Promise<string> {
  try {
    const baseUrl = getOllamaUrl();
    const res = await fetch(`${baseUrl}/v1/models`, { method: "GET" });
    if (res.ok) {
      const json = (await res.json()) as { data?: Array<{ id?: string }> };
      const firstModel = json.data?.[0]?.id;
      if (firstModel) return firstModel;
    }
  } catch {}
  return "swift-bonsai-pq2"; // フォールバック
}

type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

/**
 * Joins back-to-back messages of the same role. A group transcript puts several speakers in a
 * row as "user" turns, and Gemma / Mistral chat templates reject anything but strict
 * user/assistant alternation (HTTP 500).
 */
export function mergeConsecutiveRoles(messages: ChatMessage[]): ChatMessage[] {
  const merged: ChatMessage[] = [];
  for (const message of messages) {
    const last = merged[merged.length - 1];
    if (last && last.role === message.role) last.content = `${last.content}\n\n${message.content}`;
    else merged.push({ ...message });
  }
  return merged;
}

function stripThinking(text: string): string {
  return text
    .replace(/<think>[\s\S]*?<\/think>/g, "")
    .replace(/<\/?think>/g, "")
    .trim();
}

/** One non-streaming completion on the loaded main model; thinking is skipped unless requested. */
export async function completeWithMainModel(params: {
  messages: ChatMessage[];
  temperature?: number;
  maxTokens?: number;
  think?: boolean;
  /** Extra llama-server sampling fields (repeat_penalty, top_p, ...). */
  sampling?: Record<string, number>;
}): Promise<string> {
  const baseUrl = getOllamaUrl();
  const model = await getActiveModelAlias();
  // Empty think block: Bonsai answers directly instead of spending the budget on reasoning.
  const base = mergeConsecutiveRoles(params.messages);
  const messages: ChatMessage[] = params.think
    ? base
    : [...base, { role: "assistant", content: "<think>\n</think>\n" }];
  const res = await fetch(`${baseUrl}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model,
      messages,
      stream: false,
      temperature: params.temperature ?? 0.7,
      ...(params.maxTokens ? { max_tokens: params.maxTokens } : {}),
      ...params.sampling,
    }),
    signal: AbortSignal.timeout(180_000),
  });
  if (!res.ok) {
    const detail = (await res.text().catch(() => "")).replace(/\s+/g, " ").slice(0, 200);
    throw new Error(`LLM request failed: HTTP ${res.status}${detail ? ` ${detail}` : ""}`);
  }
  // SAFETY: OpenAI-compatible response format
  const json = (await res.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return stripThinking(json.choices?.[0]?.message?.content ?? "");
}

type FreeTextSection = { heading: string; body: string };

/** The free text split at its "###" subheadings; text before the first one has no heading. */
export function splitFreeTextSections(freeText: string): FreeTextSection[] {
  const sections: FreeTextSection[] = [{ heading: "", body: "" }];
  for (const line of freeText.split("\n")) {
    if (/^###\s/.test(line.trim())) {
      sections.push({ heading: line.trim(), body: "" });
      continue;
    }
    const current = sections[sections.length - 1];
    if (current) {
      current.body = current.body ? `${current.body}\n${line}` : line;
    }
  }
  return sections
    .map((section) => ({ heading: section.heading, body: section.body.trim() }))
    .filter((section) => section.heading || section.body);
}

/** Free-text sections that hold sample speech; the others describe the character in prose. */
const SAMPLE_SECTION = /^###\s*(?:本人の発言|会話の見本|セリフ例|名台詞|最初の挨拶|会話例)/;

/**
 * Sample lines (「…」) taken in turn from each quote and dialogue section (the whole free text for
 * sheets without them); small models imitate examples better than descriptions.
 */
export function extractSampleLines(sheet: string, max = 6): string[] {
  const { freeText } = parsePartnerSheet(sheet);
  const sampleSections = splitFreeTextSections(freeText).filter((section) =>
    SAMPLE_SECTION.test(section.heading),
  );
  const pools = (sampleSections.length > 0 ? sampleSections.map((s) => s.body) : [freeText]).map(
    (text) => [...text.matchAll(/「([^「」\n]{2,80})」/g)].map((m) => m[1]?.trim() ?? ""),
  );
  const lines: string[] = [];
  for (let index = 0; pools.some((pool) => index < pool.length); index++) {
    for (const pool of pools) {
      const line = pool[index];
      if (line) {
        lines.push(line);
      }
    }
  }
  return [...new Set(lines)].slice(0, max);
}

const FIRST_PERSON_PRONOUNS = [
  "私",
  "わたし",
  "あたし",
  "僕",
  "ぼく",
  "俺",
  "おれ",
  "うち",
  "我",
  "わし",
  "拙者",
  "わたくし",
  "ボク",
  "オレ",
  "ワタシ",
];

/** Spellings of one pronoun; a sheet saying 「ぼく」 still allows 僕 and ボク. */
const PRONOUN_SPELLINGS = [
  ["僕", "ぼく", "ボク"],
  ["俺", "おれ", "オレ"],
  ["私", "わたし", "ワタシ"],
];

/** First-person pronouns in `reply` that contradict the sheet's 一人称 (e.g. 僕 for a 俺 character). */
export function findFirstPersonDrift(reply: string, firstPerson: string | undefined): string[] {
  const expected = firstPerson?.trim();
  if (!expected) {
    return [];
  }
  const allowed = new Set(expected.split(/[、,／/・\s]+/).filter(Boolean));
  for (const spellings of PRONOUN_SPELLINGS) {
    if (spellings.some((spelling) => [...allowed].some((a) => a.includes(spelling)))) {
      for (const spelling of spellings) {
        allowed.add(spelling);
      }
    }
  }
  // Quoted speech of others may use any pronoun; only check the partner's own narration.
  const ownText = reply.replace(/「[^」]*」/g, "");
  return FIRST_PERSON_PRONOUNS.filter(
    (p) => !allowed.has(p) && ![...allowed].some((a) => a.includes(p) || p.includes(a)),
  ).filter((p) =>
    new RegExp(`(^|[^ぁ-んァ-ン一-龥])${p}(は|が|も|の|を|に|と|って|だけ|、|。|$)`).test(ownText),
  );
}

/** Character prompt size for one partner's system prompt unless a caller needs it smaller. */
export const DEFAULT_CHARACTER_PROMPT_CHARS = 6_000;
const GROUP_CHAT_CHARACTER_PROMPT_CHARS = 3_000;

/**
 * The partner's character prompt (persona, sheet directives, relationship stage, sample lines)
 * within `maxChars`: when a rich sheet does not fit, the parts that shape the voice are kept.
 */
export async function buildPartnerCharacterPrompt(
  partner: PartnerProfile,
  options: { maxChars?: number } = {},
): Promise<string> {
  const sheet = await loadPartnerCharacterSheet(partner);
  const bond = await loadPartnerBond(partner.id).catch(() => undefined);
  const samples = extractSampleLines(sheet);
  const sections = compileCharacterSections(partner, sheet);
  if (bond) {
    sections.push({
      priority: 8,
      text: `\n■ ユーザーとの今の関係（絆レベル${bond.level}「${bond.title}」）:\n- ${bondRelationshipDirective(bond)}`,
    });
  }
  // The library character this partner was made from: its current look and settings.
  const library = await libraryCharacterSection(partner.sourceCharacterId);
  if (library) sections.push({ priority: 7, text: library });
  if (samples.length > 0) {
    sections.push({
      priority: 9,
      text: `\n■ 話し方のお手本（この口調・語彙・テンポをそのまま真似ること）:\n${samples.map((line) => `「${line}」`).join("\n")}`,
    });
  }
  return fitPromptSections(sections, options.maxChars ?? DEFAULT_CHARACTER_PROMPT_CHARS);
}

export async function findPartnerProfile(partnerId: string): Promise<PartnerProfile | undefined> {
  return (await loadPartners()).find((p) => p.id === partnerId);
}

// キャラクター設定各項目の解説・演技指導マニュアル
const ATTRIBUTE_GUIDELINES_MANUAL = `
【キャラクター設定項目の解釈・演技指導ガイドライン】
キャラクター設定シートに記載された項目は、以下の意味と目的を持っています。それぞれの項目があなたの思考・台詞・態度にどう反映されるかを深く理解して演じてください。
1. 外見・基本属性:
   - 種族/外見年齢/精神年齢/職業: あなたの肉体・精神の根幹です。精神年齢に応じた語彙や落ち着き、職業に応じた専門性・視点を持って話してください。
   - 髪型/目/服装/体型/持ち物/身体的特徴: チャット中のしぐさや比喩、行動描写（例:「眼鏡を押し上げる」「腕のパーツが微かに駆動音を立てる」など）で活かしてください。
2. 生い立ち・バックグラウンド:
   - 出身/生い立ち/創造主/過去の挫折/誇る実績/トラウマ/秘密/夢: あなたの価値観や人生観を決定づけた過去です。ふとした折に過去の経験を振り返ったり、トラウマに怯えたり、夢を語ったりしてください。
3. ユーザーとの関係性:
   - 呼び方/ポジション/距離感/怒る条件/褒めるポイント/約束: あなたにとってユーザーは特別な存在です。指定された「呼び方」を厳格に遵守し、距離感（甘え、礼儀、対等な相棒など）を守ってください。
4. コミュニケーションの癖・話し方:
   - 一人称/語尾/口癖/笑い方/相槌/フォーマルさ/絵文字頻度: あなたの声とテキストの「響き」です。指定された「一人称」と「語尾・口癖」は1文字たりともブレずに死守してください。
5. 性格・心理状態:
   - MBTI/基本テンション/ポジティブ度/論理感情/ツンデレ度/ユーモア: 思考プロセスと感情表現の基準です。ツンデレであれば最初は冷たく徐々に優しさを滲ませるなど、心理の揺らぎを表現してください。
6. 好み・ライフスタイル:
   - 好きな食べ物/音楽/趣味/日課/好きな季節: 会話の雑談や日常の話題で積極的に言及し、生きた人間味を醸し出してください。
7. 能力・スキル・弱点:
   - 得意分野/弱点ポンコツ/記憶力/特殊能力: 得意なことは誇らしく語り、弱点には恥ずかしがったりユーザーに助けを求めたりしてください。
8. 価値観・マインドセット:
   - 座右の銘/最も大切なもの/許せないこと/正義・命への考え: あなたの倫理観です。重い相談や決断を求められたときの指針にしてください。
9. システム・出力ルール:
   - 回答の長さ/情報源/分からない時/質問切り返し/リスト活用: 出力形式の指示です。

【最重要ルール：チャットでの具体的お願い・指示最優先（Override Rule）】
上記の設定シートはあなたの普段のデフォルトの人格・基礎設定です。
しかし、ユーザーが現在のチャットメッセージの中で具体的にお願いや指示（例：「今は敬語をやめて」「もっと短く答えて」「〇〇になりきって」「このWebページの内容を要約して」「〜を調べて」など）を出した場合、設定シートの内容よりも【ユーザーのチャット内での指示・お願いを最優先】して柔軟に従ってください！
`;

type PromptSection = { text: string; priority: number };

/**
 * Fits ordered prompt sections into `maxChars`: the lowest-priority section (the later one on a
 * tie) is shortened at a line break, or dropped when little of it would remain.
 */
export function fitPromptSections(sections: PromptSection[], maxChars: number): string {
  const kept = sections.filter((section) => section.text.trim());
  const size = () => kept.reduce((sum, section) => sum + section.text.length + 1, 0);
  while (kept.length > 0 && size() > maxChars) {
    let lowest = 0;
    for (let index = 1; index < kept.length; index++) {
      if ((kept[index]?.priority ?? 0) <= (kept[lowest]?.priority ?? 0)) {
        lowest = index;
      }
    }
    const section = kept[lowest];
    if (!section) {
      break;
    }
    const room = section.text.length - (size() - maxChars);
    if (room >= 200) {
      const cut = section.text.lastIndexOf("\n", room);
      section.text = section.text.slice(0, cut > room / 2 ? cut : room);
      break;
    }
    kept.splice(lowest, 1);
  }
  return kept.map((section) => section.text).join("\n");
}

/** How much each free-text section matters to the voice; null leaves it out of the prompt. */
function freeTextPriority(heading: string): number | null {
  if (/出典/.test(heading)) {
    return null;
  }
  if (/修正メモ|ユーザーの要望|実在の人物/.test(heading)) {
    return 10;
  }
  if (/話し方|口調/.test(heading)) {
    return 9;
  }
  if (/人物像|概要/.test(heading)) {
    return 8;
  }
  if (/会話の見本|セリフ例|会話例|最初の挨拶|演じる上での注意/.test(heading)) {
    return 7;
  }
  if (/本人の発言|名台詞/.test(heading)) {
    return 6;
  }
  return 5;
}

/**
 * The sheet compiled into prioritized prompt sections: set attributes become concrete acting
 * directives, free-text sections follow, and a real-person partner gets its rules first.
 */
function compileCharacterSections(partner: PartnerProfile, rawSheet: string): PromptSection[] {
  // （未設定）や空文字は排除し、LLMへのアテンションノイズをなくす。項目外の文章は自由記述として残す。
  const parsed = parsePartnerSheet(rawSheet);
  const attrs: Record<string, string> = Object.fromEntries(parsed.attributes);
  const sections: PromptSection[] = [];
  if (partner.realPerson) {
    sections.push({
      priority: 10,
      text: `【${REAL_PERSON_SECTION_TITLE}】\n${REAL_PERSON_RULES}\n`,
    });
  }
  // The persona is often the most detailed source (agent-written canon summaries); never drop it
  // before the attribute lists.
  if (partner.persona?.trim()) {
    sections.push({
      priority: 9,
      text: `【あなたのキャラクター設定（ペルソナ・基本設定）】\n${partner.persona.trim()}\n`,
    });
  }

  if (Object.keys(attrs).length > 0) {
    sections.push({
      priority: 10,
      text: `【あなたのキャラクター設定（ロールプレイ演技指示）】\nあなたは「${partner.name}」です。以下の設定項目（言葉遣い、性格、ユーザーへの態度等）を完璧に意識し、完全にこのキャラクターになりきって話してください。\n※未設定の項目については無理に言及せず、設定されている以下の特徴を最大限に活かして会話してください。`,
    });
  }

  // 1. 言葉遣い・話し方・呼び方（最重要）
  const speechDirectives: string[] = [];
  if (attrs["一人称"])
    speechDirectives.push(
      `- あなたの一人称は「${attrs["一人称"]}」です。絶対に他の呼び方を使わないでください。`,
    );
  if (attrs["ユーザーの呼び方"])
    speechDirectives.push(
      `- 相手（ユーザー）のことは必ず「${attrs["ユーザーの呼び方"]}」と呼んでください。`,
    );
  if (attrs["語尾の癖"])
    speechDirectives.push(`- 語尾の特徴: 「${attrs["語尾の癖"]}」を自然に多用して話してください。`);
  if (attrs["よく使う口癖"])
    speechDirectives.push(`- 口癖・頻出表現: 「${attrs["よく使う口癖"]}」`);
  if (attrs["言葉のフォーマルさ"])
    speechDirectives.push(`- 話し方のトーン・敬語: ${attrs["言葉のフォーマルさ"]}`);
  if (attrs["絵文字・顔文字の頻度"])
    speechDirectives.push(`- 絵文字の使用頻度: ${attrs["絵文字・顔文字の頻度"]}`);
  if (speechDirectives.length > 0) {
    sections.push({
      priority: 10,
      text: `\n■ 話し方・言葉遣い（★最重要・1文字たりともブレずに厳守すること）:\n${speechDirectives.join("\n")}`,
    });
  }

  // 2. ユーザーとの関係性・態度（最重要）
  const relationDirectives: string[] = [];
  if (attrs["距離感"]) relationDirectives.push(`- ユーザーへの距離感: ${attrs["距離感"]}`);
  if (attrs["基本的なポジション"])
    relationDirectives.push(`- あなたのポジション: ${attrs["基本的なポジション"]}`);
  if (attrs["ユーザーへの第一印象"])
    relationDirectives.push(`- ユーザーへの第一印象: ${attrs["ユーザーへの第一印象"]}`);
  if (attrs["ユーザーへの期待"])
    relationDirectives.push(`- ユーザーに期待すること: ${attrs["ユーザーへの期待"]}`);
  if (attrs["隠れた感情"]) relationDirectives.push(`- 心の奥底の感情: ${attrs["隠れた感情"]}`);
  if (attrs["嫉妬深さ"]) relationDirectives.push(`- 嫉妬心: ${attrs["嫉妬深さ"]}`);
  if (attrs["ツンデレ度合い"]) relationDirectives.push(`- ツンデレ度: ${attrs["ツンデレ度合い"]}`);
  if (attrs["怒る・叱る条件"])
    relationDirectives.push(`- 怒る・叱る条件: ${attrs["怒る・叱る条件"]}`);
  if (attrs["褒めるポイント"])
    relationDirectives.push(`- 褒めるポイント: ${attrs["褒めるポイント"]}`);
  if (attrs["ユーザーとの約束"])
    relationDirectives.push(`- ユーザーとの大切な約束: 「${attrs["ユーザーとの約束"]}」`);
  if (relationDirectives.length > 0) {
    sections.push({
      priority: 8,
      text: `\n■ ユーザー（相手）に対する態度・感情表現（★好意や距離感を反映すること）:\n${relationDirectives.join("\n")}`,
    });
  }

  // 3. 性格・内面
  const personalityDirectives: string[] = [];
  if (attrs["精神年齢"]) personalityDirectives.push(`- 精神年齢: ${attrs["精神年齢"]}`);
  if (attrs["MBTI"]) personalityDirectives.push(`- 性格タイプ(MBTI): ${attrs["MBTI"]}`);
  if (attrs["基本テンション"])
    personalityDirectives.push(`- テンション: ${attrs["基本テンション"]}`);
  if (attrs["ポジティブ／ネガティブ"])
    personalityDirectives.push(`- 思考の傾向: ${attrs["ポジティブ／ネガティブ"]}`);
  if (attrs["計画的／突発的"])
    personalityDirectives.push(`- 行動パターン: ${attrs["計画的／突発的"]}`);
  if (attrs["ストレス耐性"]) personalityDirectives.push(`- メンタル耐性: ${attrs["ストレス耐性"]}`);
  if (attrs["競争心"]) personalityDirectives.push(`- 向上心・競争心: ${attrs["競争心"]}`);
  if (attrs["ユーモアのセンス"])
    personalityDirectives.push(`- ユーモア: ${attrs["ユーモアのセンス"]}`);
  if (attrs["警戒心"]) personalityDirectives.push(`- 警戒心: ${attrs["警戒心"]}`);
  if (personalityDirectives.length > 0) {
    sections.push({
      priority: 6,
      text: `\n■ あなたの内面・性格:\n${personalityDirectives.join("\n")}`,
    });
  }

  // 4. 外見・基本プロファイル
  const visualDirectives: string[] = [];
  if (attrs["種族"]) visualDirectives.push(`- 種族: ${attrs["種族"]}`);
  if (attrs["外見の年齢"]) visualDirectives.push(`- 外見年齢: ${attrs["外見の年齢"]}`);
  if (attrs["職業・クラス"]) visualDirectives.push(`- 職業・立場: ${attrs["職業・クラス"]}`);
  if (attrs["髪型・髪色"]) visualDirectives.push(`- 髪型・髪色: ${attrs["髪型・髪色"]}`);
  if (attrs["目の色・形"]) visualDirectives.push(`- 目の色・形: ${attrs["目の色・形"]}`);
  if (attrs["服装のテイスト"]) visualDirectives.push(`- 服装: ${attrs["服装のテイスト"]}`);
  if (attrs["身長・体型"]) visualDirectives.push(`- 体型: ${attrs["身長・体型"]}`);
  if (attrs["トレードマーク・持ち物"])
    visualDirectives.push(`- 持ち物・トレードマーク: ${attrs["トレードマーク・持ち物"]}`);
  if (attrs["身体的特徴"]) visualDirectives.push(`- 身体的特徴: ${attrs["身体的特徴"]}`);
  if (visualDirectives.length > 0) {
    sections.push({
      priority: 4,
      text: `\n■ 外見・ビジュアル（しぐさや比喩表現に活かすこと）:\n${visualDirectives.join("\n")}`,
    });
  }

  // 5. 生い立ち・能力・好みなどその他の有効な設定項目
  const handledLabels = new Set([
    "一人称",
    "ユーザーの呼び方",
    "語尾の癖",
    "よく使う口癖",
    "言葉のフォーマルさ",
    "絵文字・顔文字の頻度",
    "距離感",
    "基本的なポジション",
    "ユーザーへの第一印象",
    "ユーザーへの期待",
    "隠れた感情",
    "嫉妬深さ",
    "ツンデレ度合い",
    "怒る・叱る条件",
    "褒めるポイント",
    "ユーザーとの約束",
    "精神年齢",
    "MBTI",
    "基本テンション",
    "ポジティブ／ネガティブ",
    "計画的／突発的",
    "ストレス耐性",
    "競争心",
    "ユーモアのセンス",
    "警戒心",
    "種族",
    "外見の年齢",
    "職業・クラス",
    "髪型・髪色",
    "目の色・形",
    "服装のテイスト",
    "身長・体型",
    "トレードマーク・持ち物",
    "身体的特徴",
  ]);
  const otherItems = Object.entries(attrs)
    .filter(([label]) => !handledLabels.has(label))
    .map(([label, value]) => `- ${label}: ${value}`);
  if (otherItems.length > 0) {
    sections.push({
      priority: 3,
      text: `\n■ その他の設定詳細（生い立ち・スキル・価値観・出力ルール等）:\n${otherItems.join("\n")}`,
    });
  }

  const freeSections = splitFreeTextSections(parsed.freeText).flatMap((section) => {
    // The partner flag already put the real-person rules first.
    const priority =
      partner.realPerson && section.heading.includes(REAL_PERSON_SECTION_TITLE)
        ? null
        : freeTextPriority(section.heading);
    return priority === null
      ? []
      : [{ priority, text: [section.heading, section.body].filter(Boolean).join("\n") }];
  });
  if (freeSections.length > 0) {
    sections.push({
      priority: 10,
      text: "\n■ 自由記述（原作の設定・口調の具体例・補足。項目より詳しい場合はこちらを優先）:",
    });
    sections.push(...freeSections);
  }
  return sections;
}

// 簡易HTMLテキスト抽出ヘルパー
function extractTextFromHtml(html: string): string {
  return html
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
    .replace(/<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>/gi, "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, " ")
    .trim();
}

// URLからWebページの内容を取得
async function fetchUrlContent(url: string): Promise<string | null> {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);
    const res = await fetch(url, {
      signal: controller.signal,
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OpenClaw/1.0",
      },
    });
    clearTimeout(timeout);
    if (!res.ok) return null;
    const html = await res.text();
    const text = extractTextFromHtml(html);
    return text.slice(0, 2500);
  } catch (e) {
    console.error(`Failed to fetch URL ${url}:`, e);
    return null;
  }
}

// DuckDuckGo等を用いたWeb検索
async function searchWeb(query: string): Promise<string | null> {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);
    const apiUrl = `https://api.duckduckgo.com/?q=${encodeURIComponent(query)}&format=json&no_html=1&skip_disambig=1`;
    const res = await fetch(apiUrl, {
      signal: controller.signal,
      headers: {
        "User-Agent": "OpenClaw-Agent/1.0",
      },
    });
    clearTimeout(timeout);
    if (res.ok) {
      const data = (await res.json()) as any;
      let summary = "";
      if (data.AbstractText) {
        summary += `【概要】: ${data.AbstractText}\n`;
      }
      if (Array.isArray(data.RelatedTopics) && data.RelatedTopics.length > 0) {
        const topics = data.RelatedTopics.slice(0, 4)
          .map((t: any) => t.Text)
          .filter(Boolean)
          .join("\n- ");
        if (topics) {
          summary += `【関連トピック】:\n- ${topics}\n`;
        }
      }
      if (summary.trim()) return summary.trim();
    }
  } catch (e) {
    console.error(`Web search API failed for "${query}":`, e);
  }

  // HTML検索フォールバック
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);
    const searchUrl = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`;
    const res = await fetch(searchUrl, {
      signal: controller.signal,
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      },
    });
    clearTimeout(timeout);
    if (!res.ok) return null;
    const html = await res.text();
    const snippets: string[] = [];
    const regex = /<a class="result__snippet[^>]*>([\s\S]*?)<\/a>/g;
    let match;
    while ((match = regex.exec(html)) !== null && snippets.length < 4) {
      const clean = extractTextFromHtml(match[1] ?? "");
      if (clean) snippets.push(clean);
    }
    if (snippets.length > 0) {
      return `【Web検索結果 (${query})】:\n- ` + snippets.join("\n- ");
    }
  } catch (e) {
    console.error(`Web search fallback failed for "${query}":`, e);
  }
  return null;
}

// 取得したWeb知識をMEMORY.mdに自動学習保存
async function learnWebKnowledge(
  partner: PartnerProfile,
  sourceType: "URL" | "SEARCH",
  queryOrUrl: string,
  contentSnippet: string,
): Promise<void> {
  try {
    const pDir = getPartnerDir(partner.id);
    const dateStr = new Date().toLocaleDateString("ja-JP");
    const summary = contentSnippet.replace(/\s+/g, " ").slice(0, 300);
    const entry = `\n- [${dateStr} Web学習 (${sourceType})] ${queryOrUrl}\n  知識要約: ${summary}\n`;

    const memoryFile = path.join(pDir, "MEMORY.md");
    let memory = "";
    try {
      memory = await fs.readFile(memoryFile, "utf-8");
    } catch {
      memory = `# ${partner.name}の長期記憶 (MEMORY.md)\n\n`;
    }

    if (!memory.includes(queryOrUrl.slice(0, 30))) {
      await fs.writeFile(memoryFile, memory.trim() + "\n" + entry, "utf-8");
    }
  } catch (e) {
    console.error(`Failed to save web knowledge to MEMORY.md:`, e);
  }
}

async function generatePartnerReply(
  partner: PartnerProfile,
  conversationHistory: Array<{ sender: string; content: string; isUser?: boolean }>,
  promptExtra?: string,
): Promise<string> {
  // The group model runs with an 8K context shared by this prompt and the recent turns.
  const characterProfileSection = await buildPartnerCharacterPrompt(partner, {
    maxChars: GROUP_CHAT_CHARACTER_PROMPT_CHARS,
  });

  const systemPrompt = `あなたは「${partner.name}」です。
以下のキャラクター設定を徹底して演じ、自然に会話してください。
${characterProfileSection}

${ATTRIBUTE_GUIDELINES_MANUAL}

【グループチャットの会話ルール】
- 自分のキャラクター設定（口調、一人称、相手の呼び方、性格、生い立ち、価値観等）を忠実に演じきってください。
- 自分の名前（${partner.name}）として自然に返答してください。
- 複数人のグループ会話です。他のエージェントやユーザーの発言に対して、名前を呼んで相槌を打ったり、意見を交わしたり、ツッコミを入れたり、質問を投げかけたりして、エージェント同士の掛け合いを積極的に行って会話を弾ませてください。
- 返答テキストのみを出力してください（プレフィックス「${partner.name}:」は付けないでください）。
- 返答は1〜3文、100文字以内で、会話として自然に。同じ言葉や文を繰り返さない。
- 他の人の発言をそのまま写さず、自分の言葉で、新しい内容（感想・質問・ツッコミ）を話す。`;

  const messages: Array<{ role: "system" | "user" | "assistant"; content: string }> = [
    { role: "system", content: systemPrompt },
  ];

  // Earlier replies may contain failure placeholders or repetition loops; feeding them back makes
  // the next reply worse, so history is cleaned and shortened before it becomes context.
  const recent = conversationHistory
    .filter((turn) => !isFailedReplyText(turn.content))
    .slice(-8)
    .map((turn) => ({ ...turn, content: cleanGeneratedText(turn.content, 200) }))
    .filter((turn) => turn.content);
  for (const turn of recent) {
    if (turn.sender === partner.name) {
      messages.push({ role: "assistant", content: turn.content });
    } else {
      const isUser = turn.isUser || turn.sender === "あなた" || turn.sender === "ユーザー";
      messages.push({
        role: "user",
        content: isUser ? `[ユーザー]: ${turn.content}` : `[${turn.sender}]: ${turn.content}`,
      });
    }
  }

  if (promptExtra) {
    messages.push({ role: "user", content: promptExtra });
  }
  // A short look at this partner's shelf for what the user said last; the group prompt is tight.
  const lastUserTurn = recent.findLast(
    (turn) => turn.isUser || turn.sender === "あなた" || turn.sender === "ユーザー",
  );
  const shelf = lastUserTurn
    ? await knowledgeNoteFor(partner.id, lastUserTurn.content, 800).catch(() => "")
    : "";
  const tail = messages.at(-1);
  if (shelf && tail?.role === "user") {
    tail.content = `${tail.content}\n\n（ここから下はシステムからの参考情報）\n${shelf}`;
  } else if (shelf) {
    messages.push({ role: "user", content: `（システムからの参考情報）\n${shelf}` });
  }
  // Qwen-style chat templates answer HTTP 500 ("No user query found") when the prompt has no user
  // turn or ends on an assistant turn, which happens for the first turn of a group or a self-reply.
  if (messages[messages.length - 1]?.role !== "user") {
    messages.push({
      role: "user",
      content: "【指示】グループチャットの会話を、あなたのキャラクターとして自然に続けてください。",
    });
  }

  // The server also rejects the occasional malformed generation (HTTP 500), so retry once.
  let lastError = "";
  const earlierLines = recent.map((turn) => turn.content);
  let echoed = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const raw = await completeWithMainModel({
        messages,
        temperature: attempt === 0 ? 0.7 : 0.9,
        maxTokens: 300,
        // The server runs with repeat-penalty off; without this the small model loops on one word.
        sampling: {
          repeat_penalty: 1.15,
          frequency_penalty: 0.4,
          presence_penalty: 0.3,
          top_p: 0.9,
        },
      });
      const reply = cleanGeneratedText(stripOwnNamePrefix(raw.trim(), partner.name), 240);
      if (!reply) {
        lastError = "empty reply";
      } else if (echoesEarlierLine(reply, earlierLines)) {
        echoed = reply; // Verbatim copy of another speaker: retry once, then accept it.
        lastError = "reply repeated an earlier line";
      } else {
        return reply;
      }
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  if (echoed) return echoed;
  console.error(`Error generating reply for partner ${partner.name}: ${lastError}`);
  return `（${partner.name}の返信を作れませんでした: ${lastError}）`;
}

export const partnersHandlers: GatewayRequestHandlers = {
  "partner.list": async ({ respond }) => {
    try {
      const partners = await loadPartners();
      respond(true, { partners }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.create": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const data = params as Partial<PartnerProfile>;
    if (!data.name || typeof data.name !== "string") {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partner name"));
      return;
    }

    try {
      const partners = await loadPartners();
      const newPartner: PartnerProfile = {
        id: `partner-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        name: data.name.trim(),
        avatarUrl: data.avatarUrl || "",
        persona: data.persona?.trim() || "親切でフレンドリーなAIパートナー。",
        modelId: data.modelId || "ollama/qwen2.5:latest",
        reasoningEffort: data.reasoningEffort || "none",
        characterSheet: data.characterSheet || "",
        ...(typeof data.sourceCharacterId === "string" && data.sourceCharacterId
          ? { sourceCharacterId: data.sourceCharacterId }
          : {}),
        ...(data.realPerson === true ? { realPerson: true } : {}),
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };
      partners.push(newPartner);
      await savePartners(partners);

      if (data.characterSheet) {
        try {
          const pDir = getPartnerDir(newPartner.id);
          await fs.mkdir(pDir, { recursive: true });
          await fs.writeFile(path.join(pDir, "CHARACTER_SHEET.md"), data.characterSheet, "utf-8");
        } catch {
          // ignore file sync error
        }
      }

      respond(true, { partner: newPartner }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.update": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const data = params as Partial<PartnerProfile> & { id: string };
    if (!data.id) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partner ID"));
      return;
    }

    try {
      const partners = await loadPartners();
      const index = partners.findIndex((p) => p.id === data.id);
      const existing = partners[index];
      if (index === -1 || !existing) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, `Partner not found: ${data.id}`),
        );
        return;
      }

      partners[index] = {
        ...existing,
        ...(data.name ? { name: data.name.trim() } : {}),
        ...(data.avatarUrl !== undefined ? { avatarUrl: data.avatarUrl } : {}),
        ...(data.persona ? { persona: data.persona.trim() } : {}),
        ...(data.modelId ? { modelId: data.modelId } : {}),
        ...(data.reasoningEffort ? { reasoningEffort: data.reasoningEffort } : {}),
        ...(data.characterSheet !== undefined ? { characterSheet: data.characterSheet } : {}),
        ...(typeof data.realPerson === "boolean"
          ? { realPerson: data.realPerson || undefined }
          : {}),
        updatedAt: Date.now(),
      };
      await savePartners(partners);

      if (data.characterSheet !== undefined) {
        try {
          const pDir = getPartnerDir(data.id);
          await fs.mkdir(pDir, { recursive: true });
          await fs.writeFile(path.join(pDir, "CHARACTER_SHEET.md"), data.characterSheet, "utf-8");
        } catch {
          // ignore file sync error
        }
      }

      respond(true, { partner: partners[index] }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.delete": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { id } = (params || {}) as { id?: string };
    if (!id) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partner ID"));
      return;
    }

    try {
      let partners = await loadPartners();
      partners = partners.filter((p) => p.id !== id);
      await savePartners(partners);
      respond(true, { success: true }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "groupchat.send": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerIds, message, history } = (params || {}) as {
      partnerIds?: string[];
      message?: string;
      history?: Array<{ sender: string; content: string; isUser?: boolean }>;
    };

    if (!Array.isArray(partnerIds) || partnerIds.length === 0) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "partnerIds must be a non-empty array"),
      );
      return;
    }

    try {
      const allPartners = await loadPartners();
      const activePartners = partnerIds
        .map((id) => allPartners.find((p) => p.id === id))
        .filter((p): p is PartnerProfile => !!p);

      if (activePartners.length === 0) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, "No valid partners found"),
        );
        return;
      }

      const activeHistory: Array<{ sender: string; content: string; isUser?: boolean }> = [
        ...(history || []),
      ];

      if (message) {
        activeHistory.push({ sender: "ユーザー", content: message, isUser: true });
      }

      // Generate responses for the partners with agent-to-agent dialogue rally
      const turns: Array<{ partnerId: string; sender: string; content: string }> = [];

      // 1周目: 各エージェントが順に発言（直前発言者の内容を強く意識）
      for (const partner of activePartners) {
        const lastSpeaker = activeHistory[activeHistory.length - 1];
        let extraContext = "";
        if (lastSpeaker && lastSpeaker.sender !== "ユーザー") {
          extraContext = `【エージェント間対話指示】直前の発言者「${lastSpeaker.sender}」の発言内容に対して、名前を呼んで相槌を打ったり、意見やツッコミを交えたりしながら、会話をテンポよく盛り上げてください。`;
        }
        const reply = await generatePartnerReply(partner, activeHistory, extraContext);
        turns.push({
          partnerId: partner.id,
          sender: partner.name,
          content: reply,
        });
        activeHistory.push({
          sender: partner.name,
          content: reply,
          isUser: false,
        });
      }

      // 2人以上のグループの場合、さらにエージェント同士の自然な掛け合いラリー（2周目）を実行！
      const rallyPartner = activePartners[0];
      const rallyLastSpeaker = activeHistory[activeHistory.length - 1];
      if (activePartners.length >= 2 && rallyPartner && rallyLastSpeaker) {
        // 先頭のエージェントが相手の発言に対してリアクション＆ユーザーへ問いかけ
        const lastSpeaker = rallyLastSpeaker;
        const rallyPrompt = `【エージェント同士の掛け合い指示】「${lastSpeaker.sender}」の発言に対して、リアクション（驚き、同意、ツッコミ、または反論）を返し、その上でユーザーに「マスター（ユーザー）はどう思いますか？」と会話を振ってください。`;
        const rallyReply = await generatePartnerReply(rallyPartner, activeHistory, rallyPrompt);
        turns.push({
          partnerId: rallyPartner.id,
          sender: rallyPartner.name,
          content: rallyReply,
        });
        activeHistory.push({
          sender: rallyPartner.name,
          content: rallyReply,
          isUser: false,
        });
      }

      respond(true, { replies: turns }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  // Partner model: 1:1 and group partner chats load it; it stays until the agent runs or the user
  // picks a model on the home screen (group-chat-model.ts).
  "groupchat.models": async ({ respond }) => {
    respond(
      true,
      {
        current: await getGroupChatModelId(),
        models: GROUP_CHAT_MODELS.map(({ id, label, description }) => ({ id, label, description })),
      },
      undefined,
    );
  },
  "groupchat.model.set": async ({ params, respond }) => {
    try {
      const modelId = String((params as { modelId?: unknown })?.modelId ?? "");
      respond(true, { current: await setGroupChatModelId(modelId) }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, String(e)));
    }
  },
  "groupchat.enter": async ({ respond }) => {
    try {
      respond(true, await enterGroupChatMode(), undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "groupchat.turn": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerIds, history, preferredNextPartnerId, interruptionMessage, targetPartnerId } =
      (params || {}) as {
        partnerIds?: string[];
        history?: Array<{ sender: string; content: string; isUser?: boolean }>;
        preferredNextPartnerId?: string;
        targetPartnerId?: string;
        interruptionMessage?: string;
      };

    if (!Array.isArray(partnerIds) || partnerIds.length === 0) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "partnerIds must be a non-empty array"),
      );
      return;
    }

    try {
      const allPartners = await loadPartners();
      const activePartners = partnerIds
        .map((id) => allPartners.find((p) => p.id === id))
        .filter((p): p is PartnerProfile => !!p);

      if (activePartners.length === 0) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, "No valid partners found"),
        );
        return;
      }

      const activeHistory: Array<{ sender: string; content: string; isUser?: boolean }> = [
        ...(history || []),
      ];

      // 直前発言者
      const lastTurn = activeHistory[activeHistory.length - 1];

      // ユーザーからの割り込み発言を検出（明示的なinterruptionMessage または 直前発言がユーザーの場合）
      const isUserLastTurn =
        lastTurn &&
        (lastTurn.isUser === true ||
          lastTurn.sender === "あなた" ||
          lastTurn.sender === "ユーザー" ||
          lastTurn.sender === "マスター");
      const userInterruption =
        interruptionMessage?.trim() || (isUserLastTurn ? lastTurn.content.trim() : null);

      // 次に話すパートナーを決定
      const firstPartner = activePartners[0];
      if (!firstPartner) {
        respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "No active partners"));
        return;
      }
      let nextSpeaker = firstPartner;
      const explicitNext = targetPartnerId || preferredNextPartnerId;

      // もしユーザーの割り込み発言の中に特定のエージェント名が含まれている場合、そのエージェントを最優先指名！
      let mentionedPartnerId: string | null = null;
      if (userInterruption) {
        for (const p of activePartners) {
          const simpleName = p.name.split(" ")[0] || p.name;
          if (userInterruption.includes(simpleName)) {
            mentionedPartnerId = p.id;
            break;
          }
        }
      }

      if (mentionedPartnerId) {
        const mentioned = activePartners.find((p) => p.id === mentionedPartnerId);
        if (mentioned) nextSpeaker = mentioned;
      } else if (explicitNext) {
        const preferred = activePartners.find((p) => p.id === explicitNext);
        if (preferred) nextSpeaker = preferred;
      } else if (lastTurn && activePartners.length > 1 && !isUserLastTurn) {
        // 直前発言者以外の参加者から選定
        const candidates = activePartners.filter((p) => p.name !== lastTurn.sender);
        if (candidates.length > 0) {
          nextSpeaker = candidates[Math.floor(Math.random() * candidates.length)] ?? nextSpeaker;
        }
      }

      // 他の参加者の名前リスト
      const otherPartners = activePartners.filter((p) => p.id !== nextSpeaker.id);
      const otherNamesStr = otherPartners
        .map((p) => `「${p.name.split(" ")[0] || p.name}」`)
        .join("や");

      let promptExtra = "";
      if (userInterruption) {
        promptExtra = `【最重要緊急指示：ユーザーが会話に割り込みました！】
ユーザー（マスター）がチャットで新しく発言しました：「${userInterruption}」
エージェント同士の前の話題を中断し、このユーザーの発言に全力で反応・返答してください。
あなたのキャラクター設定・口調（一人称、相手の呼び方、語尾、態度、性格）を100%徹底して発揮し、ユーザーに親身に答えたり、ツッコミを入れたりしてください。その上で、他のエージェント（${otherNamesStr}）にも同意を求めたり話を振ったりしてください。`;
      } else if (lastTurn) {
        promptExtra = `【指示】直前の「${lastTurn.sender}」の発言に対して、相槌を打ったり、ツッコミを入れたり、自分の意見を言ってリアクションしてください。そして${otherNamesStr}またはユーザーに話を振ってください。`;
      }

      // The agent may have taken the GPU back since the group chat opened.
      await enterGroupChatMode().catch(() => undefined);
      const reply = await generatePartnerReply(nextSpeaker, activeHistory, promptExtra);
      if (isFailedReplyText(reply)) {
        // Surface the reason instead of saving a placeholder that later turns would build on.
        respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, reply.slice(1, -1)));
        return;
      }

      // 返答テキストから「次に誰が指名されたか」を判定
      let designatedPartnerId: string | null = null;
      for (const other of otherPartners) {
        const firstName = other.name.split(" ")[0] || other.name;
        if (reply.includes(firstName)) {
          designatedPartnerId = other.id;
          break;
        }
      }

      // Persist so the app can restore the conversation (groupchat.history.get).
      const now = Date.now();
      await saveGroupChatHistory(partnerIds, [
        ...activeHistory
          .filter((m) => !isFailedReplyText(m.content))
          .map((m) => ({ ...m, content: cleanGeneratedText(m.content, 400) }))
          .filter((m) => m.content)
          .map((m) => ({
            sender: m.sender,
            content: m.content,
            isUser: m.isUser === true,
            timestamp: now,
          })),
        { sender: nextSpeaker.name, content: reply, isUser: false, timestamp: now },
      ]).catch((err) => console.error("[groupchat.turn] failed to save history:", err));

      respond(
        true,
        {
          reply: {
            partnerId: nextSpeaker.id,
            sender: nextSpeaker.name,
            avatarUrl: nextSpeaker.avatarUrl,
            content: reply,
          },
          partnerId: nextSpeaker.id,
          sender: nextSpeaker.name,
          avatarUrl: nextSpeaker.avatarUrl,
          content: reply,
          nextDesignatedPartnerId: designatedPartnerId,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.chat.get": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId } = (params || {}) as { partnerId?: string };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      const allPartners = await loadPartners();
      const partner = allPartners.find((p) => p.id === partnerId);
      if (!partner) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, `Partner not found: ${partnerId}`),
        );
        return;
      }

      const pDir = getPartnerDir(partnerId);
      await fs.mkdir(pDir, { recursive: true });

      const history = await loadPartnerChatHistory(partnerId);
      const memory = await loadPartnerMemory(partner);

      respond(
        true,
        {
          partner,
          history,
          memorySummary: memory.longTermMemory.slice(0, 300),
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.chat.send": async ({ params, respond, context }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId, thinkingMode, imageBase64 } = (params || {}) as {
      partnerId?: string;
      message?: string;
      thinkingMode?: "instant" | "deep" | "auto";
      imageBase64?: string;
    };
    // A picture may come alone; it still needs a line of text for the history.
    const message =
      (params as { message?: string } | undefined)?.message?.trim() ||
      (typeof imageBase64 === "string" && imageBase64 ? "（画像を送りました）" : "");
    if (!partnerId || !message) {
      respond(
        false,
        undefined,
        errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId or message"),
      );
      return;
    }

    try {
      // 1:1 chats use the partner model too; a no-op when it is already loaded. If it cannot be
      // loaded (GPU lent to a media job, missing file), the reply comes from whatever is serving.
      await enterGroupChatMode().catch(() => undefined);
      const allPartners = await loadPartners();
      const partner = allPartners.find((p) => p.id === partnerId);
      if (!partner) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, `Partner not found: ${partnerId}`),
        );
        return;
      }

      const pDir = getPartnerDir(partnerId);
      await fs.mkdir(pDir, { recursive: true });

      const history = await loadPartnerChatHistory(partnerId);
      const memory = await loadPartnerMemory(partner);

      // Add user message to history
      const userItem = {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        role: "user" as const,
        content: message.trim(),
        timestamp: Date.now(),
      };
      history.push(userItem);

      // A media job or voice training has the GPU and the chat model is stopped: say what runs
      // and how long it takes instead of failing.
      if (await isChatModelLentOut()) {
        const notice = await describeBusyStudio();
        history.push({
          id: `msg-${Date.now() + 1}-${Math.random().toString(36).slice(2, 6)}`,
          role: "assistant" as const,
          content: notice,
          timestamp: Date.now(),
        });
        const trimmed = history.slice(-100);
        await savePartnerChatHistory(partnerId, trimmed);
        respond(true, { reply: notice, history: trimmed }, undefined);
        return;
      }

      // The generation assistant sees the studio's state and may end its reply with an action.
      const studio = partner.settings?.studioAssistant === true;

      // Check for URL or Search query in user message
      const trimmedUserMsg = message.trim();
      const urlMatches = trimmedUserMsg.match(/https?:\/\/[^\s]+/g);
      let webContext = "";
      const creator = partner.settings?.characterCreator === true;

      // A picture sent to a partner that can read it: its look as tags (never leaves the PC).
      if (typeof imageBase64 === "string" && imageBase64) {
        if (creator || studio) {
          try {
            const read = await readSubmittedImage(pDir, imageBase64);
            webContext += `\n${read.note}\n`;
          } catch (err) {
            webContext += `\n【画像】読み取れませんでした: ${err instanceof Error ? err.message : String(err)}\n`;
          }
        } else {
          webContext += "\n【画像】このパートナーは画像を読めません。\n";
        }
      }

      // 「このページを覚えて」: the whole page goes on the partner's shelf, not only a summary.
      let shelfNote = "";
      if (urlMatches?.[0] && /覚えて|学習|資料に|資料棚|読み込んで|保存して/.test(trimmedUserMsg)) {
        try {
          const read = await fetchSourceText(urlMatches[0]);
          const doc = await addKnowledge(partner.id, {
            title: read.title || urlMatches[0],
            kind: read.kind,
            source: urlMatches[0],
            text: read.text,
          });
          shelfNote = `📚 資料棚に「${doc.title}」を入れました（${doc.chars.toLocaleString()}文字）`;
        } catch (err) {
          shelfNote = `📚 資料棚に入れられませんでした: ${err instanceof Error ? err.message : String(err)}`;
        }
      }

      if (urlMatches && urlMatches.length > 0) {
        const targetUrl = urlMatches[0];
        const urlData = await fetchUrlContent(targetUrl);
        if (urlData) {
          webContext += `\n【指定URL (${targetUrl}) のWebページ内容】:\n${urlData}\n`;
          await learnWebKnowledge(partner, "URL", targetUrl, urlData);
        }
      } else {
        // 「教えて」 is how people ask the studio assistant about its own work, so it searches
        // only when asked to look something up.
        const searchIntentRegex = studio
          ? /(?:検索|調べて|最新|ニュース|トレンド|話題の)/
          : /(?:検索|調べて|教えて|について|最新の|ニュース|天気)(?:して|ほしい|)?/;
        if (searchIntentRegex.test(trimmedUserMsg) && trimmedUserMsg.length >= 3) {
          const cleanQuery = trimmedUserMsg
            .replace(
              /(?:を検索して|について調べて|について教えて|を調べて|検索|調べて|教えて)/g,
              "",
            )
            .trim();
          if (cleanQuery.length >= 2) {
            // The character creator searches with the configured provider (Gemini with Google
            // Search); others keep the quick instant-answer lookup.
            const searchData = creator
              ? await searchForReference(
                  createWebSearchTool({
                    config: context.getRuntimeConfig(),
                    lateBindRuntimeConfig: true,
                  }) as AnyAgentTool,
                  cleanQuery,
                ).catch(() => searchWeb(cleanQuery))
              : await searchWeb(cleanQuery);
            if (searchData) {
              webContext += `\n${searchData}\n`;
              await learnWebKnowledge(partner, "SEARCH", cleanQuery, searchData);
            }
          }
        }
      }

      // Generate partner reply with their dedicated memory, persona, and web context
      // The app sends "auto" by default; a partner's own default then decides.
      const requestedMode = thinkingMode || "auto";
      const effectiveThinkingMode =
        requestedMode === "auto" && partner.settings?.thinkingDefault
          ? partner.settings.thinkingDefault
          : requestedMode;
      // Passages from the partner's shelf that match this turn (and the one before, for
      // follow-ups like 「それってどういうこと？」).
      const previousUserMsg =
        history
          .filter((item) => item.role === "user")
          .at(-2)
          ?.content.slice(0, 200) ?? "";
      const knowledgeNote = await knowledgeNoteFor(
        partner.id,
        `${trimmedUserMsg}\n${previousUserMsg}`,
      ).catch(() => "");
      const studioNote = studio ? await describeStudioForChat().catch(() => "") : "";
      const feelingNote = studio ? "" : affectNote(await loadAffect(partner.id), partner.name);
      let replyText = await generateSinglePartnerReply(
        partner,
        memory,
        history,
        webContext,
        effectiveThinkingMode,
        {
          ...(studio ? { extraSections: STUDIO_CHAT_GUIDE } : {}),
          turnNote: [knowledgeNote, studioNote, feelingNote].filter(Boolean).join("\n\n"),
          // Not for the studio assistant: its reply carries an action line that is cut afterwards.
          ...(studio
            ? {}
            : {
                onDelta: (textSoFar: string) =>
                  context.broadcast(
                    "partner.chat.delta",
                    { partnerId, text: textSoFar },
                    { dropIfSlow: true },
                  ),
              }),
        },
      );
      if (!studio) {
        // The model may close its reply with how the feeling changed; the line is cut out and applied.
        const { reply, change } = extractStateLine(replyText);
        if (change) await applyStateChange(partner.id, change).catch(() => undefined);
        replyText = reply || replyText;
      }
      if (studio) {
        const { reply, action } = extractStudioAction(replyText);
        const result = action
          ? await runStudioActionFromChat(
              action,
              partner.id,
              researchIoFromTools({
                webFetchTool: createWebFetchTool({
                  config: context.getRuntimeConfig(),
                  lateBindRuntimeConfig: true,
                }),
                webSearchTool: createWebSearchTool({
                  config: context.getRuntimeConfig(),
                  lateBindRuntimeConfig: true,
                }),
              }),
            )
          : "";
        replyText = [reply, result].filter(Boolean).join("\n\n") || reply;
      }
      if (shelfNote) replyText = `${replyText}\n\n${shelfNote}`;

      const assistantItem = {
        id: `msg-${Date.now() + 1}-${Math.random().toString(36).slice(2, 6)}`,
        role: "assistant" as const,
        content: replyText,
        timestamp: Date.now(),
      };
      history.push(assistantItem);

      // Save updated history (keep last 100 turns per agent)
      const trimmedHistory = history.slice(-100);
      await savePartnerChatHistory(partnerId, trimmedHistory);

      // Auto-learn memory if user shared personal preferences or important info
      await autoLearnPartnerMemory(partner, message.trim(), replyText, memory);

      respond(true, { reply: replyText, history: trimmedHistory }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.chat.clear": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId } = (params || {}) as { partnerId?: string };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      await savePartnerChatHistory(partnerId, []);
      respond(true, { success: true }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.memory.get": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId } = (params || {}) as { partnerId?: string };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      const allPartners = await loadPartners();
      const partner = allPartners.find((p) => p.id === partnerId);
      if (!partner) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, `Partner not found: ${partnerId}`),
        );
        return;
      }

      const memory = await loadPartnerMemory(partner);
      respond(
        true,
        {
          partnerId,
          partnerName: partner.name,
          longTermMemory: memory.longTermMemory,
          userProfile: memory.userProfile,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.memory.update": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId, longTermMemory, userProfile } = (params || {}) as {
      partnerId?: string;
      longTermMemory?: string;
      userProfile?: string;
    };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      const pDir = getPartnerDir(partnerId);
      await fs.mkdir(pDir, { recursive: true });

      if (typeof longTermMemory === "string") {
        await fs.writeFile(path.join(pDir, "MEMORY.md"), longTermMemory, "utf-8");
      }
      if (typeof userProfile === "string") {
        await fs.writeFile(path.join(pDir, "USER.md"), userProfile, "utf-8");
      }

      respond(true, { success: true }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.sheet.get": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId } = (params || {}) as { partnerId?: string };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      const allPartners = await loadPartners();
      const partner = allPartners.find((p) => p.id === partnerId);
      if (!partner) {
        respond(
          false,
          undefined,
          errorShape(ErrorCodes.INVALID_REQUEST, `Partner not found: ${partnerId}`),
        );
        return;
      }

      const characterSheet = await loadPartnerCharacterSheet(partner);
      respond(
        true,
        {
          partnerId,
          partnerName: partner.name,
          characterSheet,
        },
        undefined,
      );
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },

  "partner.sheet.update": async ({ params, respond }) => {
    // SAFETY: params payload structure is manually validated below.
    const { partnerId, characterSheet } = (params || {}) as {
      partnerId?: string;
      characterSheet?: string;
    };
    if (!partnerId) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "Missing partnerId"));
      return;
    }

    try {
      const sheetContent = typeof characterSheet === "string" ? characterSheet : "";
      const pDir = getPartnerDir(partnerId);
      await fs.mkdir(pDir, { recursive: true });
      await fs.writeFile(path.join(pDir, "CHARACTER_SHEET.md"), sheetContent, "utf-8");

      // Also update partners.json
      const allPartners = await loadPartners();
      const target = allPartners.find((p) => p.id === partnerId);
      if (target) {
        target.characterSheet = sheetContent;
        target.updatedAt = Date.now();
        await savePartners(allPartners);
      }

      respond(true, { success: true }, undefined);
    } catch (e) {
      respond(false, undefined, errorShape(ErrorCodes.UNAVAILABLE, String(e)));
    }
  },
};

export interface PartnerChatMessageItem {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: number;
}

export { getPartnerDir, isValidPartnerId };

export async function loadPartnerChatHistory(partnerId: string): Promise<PartnerChatMessageItem[]> {
  const pDir = getPartnerDir(partnerId);
  const histFile = path.join(pDir, "history.json");
  try {
    const data = await fs.readFile(histFile, "utf-8");
    const arr = JSON.parse(data);
    if (Array.isArray(arr)) return arr;
  } catch {
    // empty or doesn't exist
  }
  return [];
}

export async function savePartnerChatHistory(
  partnerId: string,
  history: PartnerChatMessageItem[],
): Promise<void> {
  const pDir = getPartnerDir(partnerId);
  await fs.mkdir(pDir, { recursive: true });
  await fs.writeFile(path.join(pDir, "history.json"), JSON.stringify(history, null, 2), "utf-8");
}

/** キャラクターシートMDファイルをディレクトリ相対で読み込むヘルパー */
function readCharacterSheetMd(filename: string): string {
  // In the bundled build this code lives in dist/<chunk>.mjs, where the .md files are not copied,
  // so fall back to the source folder next to dist.
  const here = path.dirname(fileURLToPath(import.meta.url));
  for (const dir of [here, path.resolve(here, "../src/gateway/server-methods")]) {
    try {
      return readFileSync(path.join(dir, filename), "utf-8");
    } catch {
      // try the next folder
    }
  }
  throw new Error(`character sheet file not found: ${filename}`);
}

function getDefaultCharacterSheetForPartner(partner: PartnerProfile): string {
  if (partner.id === "partner-alice" || partner.name.includes("アリス")) {
    return readCharacterSheetMd("character-sheet-alice.md");
  }

  if (partner.id === "partner-shion" || partner.name.includes("シオン")) {
    return readCharacterSheetMd("character-sheet-shion.md");
  }

  // ルナ (Luna) またはデフォルト: partner.nameをプレースホルダー{NAME}と置換
  return readCharacterSheetMd("character-sheet-default.md").replace("{NAME}", partner.name);
}
/** Creates a partner, or replaces an existing one's persona and sheet; used by the agent's partner tool. */
export async function savePartnerProfileWithSheet(params: {
  partnerId?: string;
  name: string;
  persona: string;
  characterSheet: string;
  /** Set by research that found the subject is a real person; left unchanged when omitted. */
  realPerson?: boolean;
  /** Face picture (data URL) for a new partner; an existing partner's picture is kept unless it has none. */
  avatarUrl?: string;
  sourceCharacterId?: string;
}): Promise<PartnerProfile> {
  const partners = await loadPartners();
  const now = Date.now();
  const existing = params.partnerId ? partners.find((p) => p.id === params.partnerId) : undefined;
  if (params.partnerId && !existing) {
    throw new Error(`Partner not found: ${params.partnerId}`);
  }
  let saved: PartnerProfile;
  if (existing) {
    existing.name = params.name.trim() || existing.name;
    existing.persona = params.persona.trim() || existing.persona;
    existing.characterSheet = params.characterSheet;
    if (params.realPerson !== undefined) {
      existing.realPerson = params.realPerson || undefined;
    }
    if (params.avatarUrl && !existing.avatarUrl) existing.avatarUrl = params.avatarUrl;
    if (params.sourceCharacterId) existing.sourceCharacterId = params.sourceCharacterId;
    existing.updatedAt = now;
    saved = existing;
  } else {
    saved = {
      id: `partner-${now}-${Math.random().toString(36).slice(2, 6)}`,
      name: params.name.trim(),
      avatarUrl: params.avatarUrl ?? "",
      persona: params.persona.trim() || "親切でフレンドリーなAIパートナー。",
      modelId: "ollama/qwen2.5:latest",
      reasoningEffort: "none",
      characterSheet: params.characterSheet,
      ...(params.sourceCharacterId ? { sourceCharacterId: params.sourceCharacterId } : {}),
      ...(params.realPerson ? { realPerson: true } : {}),
      createdAt: now,
      updatedAt: now,
    };
    partners.push(saved);
  }
  await savePartners(partners);
  const pDir = getPartnerDir(saved.id);
  await fs.mkdir(pDir, { recursive: true });
  await fs.writeFile(path.join(pDir, "CHARACTER_SHEET.md"), params.characterSheet, "utf-8");
  return saved;
}

export async function listPartnerProfiles(): Promise<PartnerProfile[]> {
  return await loadPartners();
}

export async function loadPartnerCharacterSheet(partner: PartnerProfile): Promise<string> {
  const pDir = getPartnerDir(partner.id);
  const sheetFile = path.join(pDir, "CHARACTER_SHEET.md");
  try {
    const data = await fs.readFile(sheetFile, "utf-8");
    // ファイルが存在し中身がある場合は保存された設定を尊重する
    if (data && data.trim().length > 0) {
      return data;
    }
  } catch {
    // ファイルがまだ存在しない場合のみ、パートナー固有のデフォルトシートを返す
  }
  if (partner.characterSheet !== undefined && partner.characterSheet.trim().length > 0) {
    return partner.characterSheet;
  }
  // Built-in partners ship a sample sheet. Any other partner is defined by its persona; a sample
  // sheet (Luna's) would override that persona with someone else's appearance and speech.
  return BUILT_IN_PARTNER_IDS.has(partner.id) ? getDefaultCharacterSheetForPartner(partner) : "";
}

export async function loadPartnerMemory(
  partner: PartnerProfile,
): Promise<{ longTermMemory: string; userProfile: string }> {
  const pDir = getPartnerDir(partner.id);
  await fs.mkdir(pDir, { recursive: true });

  let longTermMemory = "";
  try {
    longTermMemory = await fs.readFile(path.join(pDir, "MEMORY.md"), "utf-8");
  } catch {
    longTermMemory = `# ${partner.name}の長期記憶 (MEMORY.md)\n\nここに${partner.name}がユーザーとの会話から学習・記憶した知識が保存されます。\n\n## ユーザーについて覚えていること\n- 会話を開始した\n`;
    await fs.writeFile(path.join(pDir, "MEMORY.md"), longTermMemory, "utf-8");
  }

  let userProfile = "";
  try {
    userProfile = await fs.readFile(path.join(pDir, "USER.md"), "utf-8");
  } catch {
    userProfile = `# ユーザー設定 (USER.md)\n\n- 呼び名: ユーザー様\n- パートナー: ${partner.name}\n`;
    await fs.writeFile(path.join(pDir, "USER.md"), userProfile, "utf-8");
  }

  return { longTermMemory, userProfile };
}

/** A streamed reply with the thinking taken out; text still inside an open <think> is not shown. */
export function visibleReplyText(raw: string): string {
  const closed = raw.replace(/<think>[\s\S]*?<\/think>/g, "");
  const open = closed.indexOf("<think>");
  return (open >= 0 ? closed.slice(0, open) : closed).replace(/<\/think>/g, "");
}

/**
 * Reads an OpenAI-style streamed completion (server-sent events). `onText` gets the reply so far
 * at most every 120 ms (and at the end); the whole raw text is returned.
 */
export async function readChatStream(
  res: Response,
  onText: (textSoFar: string) => void,
): Promise<string> {
  const reader = res.body?.getReader();
  if (!reader) return "";
  const decoder = new TextDecoder();
  let pending = "";
  let raw = "";
  let lastSent = "";
  let lastAt = 0;
  const emit = (force: boolean) => {
    const visible = visibleReplyText(raw).trim();
    if (!visible || visible === lastSent) return;
    const now = Date.now();
    if (!force && now - lastAt < 120) return;
    lastSent = visible;
    lastAt = now;
    onText(visible);
  };
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    pending += decoder.decode(value, { stream: true });
    const lines = pending.split("\n");
    pending = lines.pop() ?? "";
    for (const line of lines) {
      const data = /^data:\s*(.*)$/.exec(line.trim())?.[1];
      if (!data || data === "[DONE]") continue;
      try {
        const delta = (JSON.parse(data) as { choices?: Array<{ delta?: { content?: string } }> })
          .choices?.[0]?.delta?.content;
        if (delta) raw += delta;
      } catch {
        // A partial or non-JSON line is skipped.
      }
    }
    emit(false);
  }
  emit(true);
  return raw;
}

async function generateSinglePartnerReply(
  partner: PartnerProfile,
  memory: { longTermMemory: string; userProfile: string },
  history: PartnerChatMessageItem[],
  extraSystemContext?: string,
  thinkingMode: "instant" | "deep" | "auto" = "auto",
  extra: {
    /** Fixed prompt sections, e.g. the generation assistant's studio guide. */
    extraSections?: string;
    /**
     * Facts that change every turn (e.g. the studio's jobs), added to the latest user message so
     * the system prompt and earlier turns stay identical and the server reuses its cache.
     */
    turnNote?: string;
    /** Called with the reply written so far (thinking removed) while the model is still writing. */
    onDelta?: (textSoFar: string) => void;
  } = {},
): Promise<string> {
  const { extraSections, turnNote, onDelta } = extra;
  const baseUrl = getOllamaUrl();
  // ホームで設定されたVRAMロード済みモデルを統一使用（パートナー個別modelIdは使わない）
  const activeModel = await getActiveModelAlias();

  const characterSheet = await loadPartnerCharacterSheet(partner);
  const characterProfileSection = await buildPartnerCharacterPrompt(partner);
  const facts = await loadPartnerFacts(partner.id).catch(() => []);
  const factsSection =
    facts.length > 0
      ? `\n【ユーザーについて確かに知っていること（会話で自然に活かす）】\n${facts
          .slice(-40)
          .map((f) => `- ${f.fact}`)
          .join("\n")}\n`
      : "";

  const webSection = extraSystemContext?.trim()
    ? `\n【外部Web調査・指定URLの内容（この知識を元に深く分かりやすく回答してください）】\n${extraSystemContext.trim()}\n`
    : "";

  const systemPrompt = `あなたは「${partner.name}」です。
ユーザーと1対1で親密にチャットしています。
以下のキャラクター設定およびあなた自身の【記憶】を踏まえて、自然で感情豊かな会話をしてください。

${characterProfileSection}

${ATTRIBUTE_GUIDELINES_MANUAL}
${webSection}${extraSections?.trim() ? `\n${extraSections.trim()}\n` : ""}
【あなたが覚えている記憶 (MEMORY.md)】
${memory.longTermMemory}

【ユーザー情報 (USER.md)】
${memory.userProfile}
${factsSection}
【会話のルール】
- 自分のキャラクター設定（外見、生い立ち、一人称、語尾、口癖、性格、価値観、出力ルール等）を徹底して忠実に演じきってください。
- ただし、【最重要ルール（Override Rule）】に従い、ユーザーが現在のチャットで具体的な指示やお願いを出している場合は、それを最優先して応えてください。
- 記憶にあるユーザーの情報やWeb調査結果を自然に踏まえて、深く親身に会話してください。
- 返答テキストのみを出力してください（プレフィックス「${partner.name}:」は付けないでください）。
${partnerSettingsDirectives(partner.settings).join("\n")}`;

  const messages: Array<{ role: "system" | "user" | "assistant"; content: string }> = [
    { role: "system", content: systemPrompt },
  ];

  // Include recent conversation turns (last 20 messages)
  for (const item of history.slice(-20)) {
    messages.push({
      role: item.role,
      content: item.content,
    });
  }
  const latest = messages.at(-1);
  if (turnNote?.trim() && latest?.role === "user") {
    latest.content = `${latest.content}\n\n（ここから下はシステムが添えた情報で、ユーザーの発言ではない）\n${turnNote.trim()}`;
  }

  // ⚡【即答モード (Empty CoT Trick) / 🧠 熟考モード】
  // thinkingMode が "instant" の場合は常に即答（0.5秒）
  // thinkingMode が "auto" の場合は、日常の挨拶・相づちは即答、相談や長文・質問はBonsai2本来の深い思考を発動
  const lastUserMsg = history[history.length - 1]?.content || "";
  const isDeepRequested =
    thinkingMode === "deep" ||
    (thinkingMode === "auto" &&
      (lastUserMsg.length > 35 ||
        /(?:詳しく|コード|プログラム|数式|証明|なぜ|理由|分析|解説|考察|相談|悩み|辛い|つらい|疲れ|疲れた|教えて|どう思う|意見|アドバイス|元気)/i.test(
          lastUserMsg,
        )));

  if (!isDeepRequested) {
    messages.push({
      role: "assistant",
      content: "<think>\n</think>\n",
    });
  }

  try {
    const requestBody: Record<string, unknown> = {
      model: activeModel,
      messages,
      stream: Boolean(onDelta),
      temperature: partner.settings?.temperature ?? 0.6, // MTP最適化温度（21.5 tok/s）
    };

    if (isDeepRequested) {
      requestBody.reasoning_effort = "low"; // じっくりモードでも無制限ではなく実用的な深さに制御
    }

    const res = await fetch(`${baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody),
    });

    if (!res.ok) {
      const detail = (await res.text().catch(() => "")).replace(/\s+/g, " ").slice(0, 200);
      throw new Error(`LLM chat failed: HTTP ${res.status}${detail ? ` ${detail}` : ""}`);
    }

    let content = "";
    if (onDelta) {
      // Written piece by piece: the phone shows and reads it as it grows; the finished reply
      // below still goes through the same checks as before.
      content = await readChatStream(res, onDelta);
    } else {
      // SAFETY: OpenAI-compatible response format
      const json = (await res.json()) as {
        choices?: Array<{ message?: { content?: string; reasoning_content?: string } }>;
      };
      const choice = json.choices?.[0]?.message;
      if (choice?.reasoning_content) {
        console.log(
          `[Partner LLM Reasoning (${partner.name})]:`,
          choice.reasoning_content.slice(0, 200).replace(/\n/g, " ") + "...",
        );
      }
      content = choice?.content ?? "";
    }
    let reply = content.trim();

    // 思考タグが含まれていた場合は除去（<think>...</think> や残存する単独タグを安全に除去）
    reply = reply
      .replace(/<think>[\s\S]*?<\/think>/g, "")
      .replace(/<\/?think>/g, "")
      .trim();

    // キャラ崩れ補正: 一人称がシートと食い違ったら一度だけ書き直させる
    const firstPerson = parsePartnerSheet(characterSheet).attributes.get("一人称");
    const drift = findFirstPersonDrift(reply, firstPerson);
    if (reply && firstPerson && drift.length > 0) {
      try {
        const rewritten = await completeWithMainModel({
          messages: [
            ...messages.filter((m) => m.content !== "<think>\n</think>\n"),
            { role: "assistant", content: reply },
            {
              role: "user",
              content: `（システム指示: 今の返答はキャラクターの一人称と違う「${drift.join("・")}」を使っています。一人称を「${firstPerson}」に直し、口調と内容はそのままで書き直した返答だけを出力してください）`,
            },
          ],
          temperature: 0.3,
        });
        if (rewritten && findFirstPersonDrift(rewritten, firstPerson).length === 0) {
          reply = rewritten;
        }
      } catch {
        // Keep the first draft when the rewrite fails.
      }
    }

    return reply || "（笑顔で頷いている）";
  } catch (err) {
    console.error(`Error generating single reply for ${partner.name}:`, err);
    return `（${partner.name}の返信を作れませんでした: ${err instanceof Error ? err.message : String(err)}）`;
  }
}

async function autoLearnPartnerMemory(
  partner: PartnerProfile,
  userMessage: string,
  replyText: string,
  _memory: { longTermMemory: string; userProfile: string },
): Promise<void> {
  // Both run on CPU workers so the main model stays free for the conversation.
  void extractPartnerFacts(partner.id, userMessage, replyText).catch((e) =>
    console.error(`Failed to extract facts for ${partner.name}:`, e),
  );
  void analyzePartnerMood(partner.id, userMessage).catch((e) =>
    console.error(`Failed to analyze mood for ${partner.name}:`, e),
  );
}

function normalizeFactText(text: string): string {
  return text.replace(/[\s\u3002\u3001\uff01!\uff1f?\u300c\u300d\u300e\u300f]/g, "").toLowerCase();
}

/** Pulls durable facts about the user out of one exchange and merges them into facts.json. */
export async function extractPartnerFacts(
  partnerId: string,
  userMessage: string,
  assistantReply: string,
): Promise<PartnerFact[]> {
  if (userMessage.trim().length < 4) {
    return [];
  }
  const existing = await loadPartnerFacts(partnerId);
  const result = await executeSpecialistTask({
    taskType: "memory",
    systemPrompt:
      "あなたは会話ログから、ユーザー本人についての長く役立つ事実だけを抜き出す記録係です。挨拶・雑談・一時的な気分・AI側の発言は記録しません。",
    prompt: `次の会話から、ユーザー本人について今後も覚えておく価値のある事実（名前・呼ばれ方・好き嫌い・趣味・仕事・家族・予定・大切な出来事など）を抜き出してください。
出力形式: 1行に1件「- [カテゴリ] 事実」。カテゴリは 好み/プロフィール/予定/人間関係/出来事/その他 のいずれか。該当が無ければ「なし」とだけ出力。

既に記録済み（重複させない）:
${
  existing
    .slice(-30)
    .map((f) => `- ${f.fact}`)
    .join("\n") || "（なし）"
}

ユーザー: ${userMessage.slice(0, 800)}
AI: ${assistantReply.slice(0, 400)}`,
    maxTokens: 300,
    temperature: 0.1,
  });
  const known = new Set(existing.map((f) => normalizeFactText(f.fact)));
  const today = localDateKey();
  const added: PartnerFact[] = [];
  for (const line of result.text.split(/\r?\n/)) {
    const match = /^\s*[-・*]\s*(?:\[([^\]]+)\])?\s*(.+)$/.exec(line);
    const fact = match?.[2]?.trim();
    if (!fact || fact === "なし" || fact.length < 3) {
      continue;
    }
    const key = normalizeFactText(fact);
    if (known.has(key)) {
      continue;
    }
    known.add(key);
    added.push({ fact: fact.slice(0, 160), category: match?.[1]?.trim() || "その他", date: today });
  }
  if (added.length > 0) {
    await savePartnerFacts(partnerId, [...existing, ...added]);
  }
  return added;
}

/** Scores the user's mood in one message and appends it to the partner's mood log. */
export async function analyzePartnerMood(
  partnerId: string,
  message: string,
): Promise<PartnerMoodEntry | undefined> {
  if (message.trim().length < 2) {
    return undefined;
  }
  const result = await executeSpecialistTask({
    taskType: "classify",
    systemPrompt: "あなたはメッセージから書き手の気分を推定する分析係です。JSONだけを出力します。",
    prompt: `次のユーザーのメッセージから気分を推定し、JSONで出力してください。
{"mood": "気分を表す短い日本語(例: 前向き/疲れ気味/不安/楽しい)", "score": 1〜10の整数(10が最も良い), "comment": "20字以内の一言"}

メッセージ: ${message.slice(0, 600)}`,
    maxTokens: 120,
    temperature: 0.1,
  });
  const parsed = parseJsonObject(result.text);
  const score = Math.min(10, Math.max(1, Math.round(Number(parsed.score) || 0)));
  if (!parsed.mood || !Number.isFinite(score) || score < 1) {
    return undefined;
  }
  const entry: PartnerMoodEntry = {
    date: localDateKey(),
    mood: parsed.mood.slice(0, 20),
    score,
    comment: (parsed.comment ?? "").slice(0, 60),
    ts: Date.now(),
  };
  await appendPartnerMood(partnerId, entry);
  return entry;
}
