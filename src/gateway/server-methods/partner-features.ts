import * as fs from "node:fs/promises";
import * as path from "node:path";
import { ErrorCodes, errorShape } from "../../../packages/gateway-protocol/src/index.js";
import { resolveAgentWorkspaceDir } from "../../agents/agent-scope-config.js";
import {
  getAvatarJob,
  imageStudioStatus,
  sanitizeImagePrompt,
  startAvatarJob,
} from "../image-studio.js";
import { parseJsonObject } from "../llm-json.js";
import {
  addAlbumPhoto,
  deleteAlbumPhoto,
  describePhoto,
  isValidAlbumItemId,
  listAlbum,
  readAlbumPhoto,
  updateAlbumItem,
  visionReady,
  type AlbumItem,
} from "../partner-album.js";
import {
  getTodaysFortune,
  loadDailySettings,
  saveDailySettings,
  speakAs,
  weekdayJa,
} from "../partner-daily.js";
import { ensurePartnerProactiveLoop, loadOrBuildDailyBrief } from "../partner-proactive.js";
import { clip, generatePartnerSheet, workerSheetCompletion } from "../partner-sheet-generator.js";
import { buildPartnerSheetMarkdown, parsePartnerSheet } from "../partner-sheet-schema.js";
import {
  addPartnerBondExp,
  isValidPartnerId,
  loadPartnerBond,
  loadPartnerFacts,
  loadPartnerMoods,
  localDateKey,
  partnerFile,
  readJsonFile,
  resolveOpenClawHome,
  clearGroupChatHistory,
  loadGroupChatHistory,
  savePartnerFacts,
  writeJsonFile,
  type PartnerFact,
} from "../partner-store.js";
import { listTtsSpeakers, sanitizePartnerVoice, synthesizeSpeech } from "../partner-tts.js";
import { listCloneVoices, synthesizeCloneSpeech } from "../partner-voice-clone.js";
import { executeSpecialistTask } from "../specialist-router.js";
import { cleanGeneratedText, isFailedReplyText } from "../text-hygiene.js";
import { designVoiceDraft, draftPath, sanitizeVoiceCaption } from "../voice-design.js";
import {
  appendVoiceUpload,
  createDesignedVoice,
  createVoiceProject,
  deleteVoiceProject,
  listVoiceProjects,
  loadVoiceStudioSettings,
  queueVoiceTraining,
  readVoiceIcon,
  renameVoiceProject,
  saveVoiceStudioSettings,
  setVoiceIcon,
} from "../voice-studio.js";
import {
  analyzePartnerMood,
  buildPartnerCharacterPrompt,
  completeWithMainModel,
  extractPartnerFacts,
  findPartnerProfile,
  loadPartnerChatHistory,
  loadPartnerCharacterSheet,
  listPartnerProfiles,
  savePartnerProfileWithSheet,
  updatePartnerSettings,
  type PartnerProfile,
} from "./partners.js";
import type { GatewayRequestHandlers, RespondFn } from "./types.js";

type Params = Record<string, unknown>;

function str(params: Params, key: string): string | undefined {
  const value = params[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function fail(
  respond: RespondFn,
  message: string,
  code:
    | typeof ErrorCodes.INVALID_REQUEST
    | typeof ErrorCodes.UNAVAILABLE = ErrorCodes.INVALID_REQUEST,
) {
  respond(false, undefined, errorShape(code, message));
}

/** Resolves `params.partnerId` to a stored partner, answering the request itself on failure. */
async function requirePartner(
  params: Params,
  respond: RespondFn,
): Promise<PartnerProfile | undefined> {
  const partnerId = params.partnerId;
  if (!isValidPartnerId(partnerId)) {
    fail(respond, "Missing or invalid partnerId");
    return undefined;
  }
  const partner = await findPartnerProfile(partnerId);
  if (!partner) {
    fail(respond, `Partner not found: ${partnerId}`);
    return undefined;
  }
  return partner;
}

/** Wraps a handler so unexpected errors become UNAVAILABLE responses instead of hung requests. */
function guarded(
  handler: (
    params: Params,
    respond: RespondFn,
    ctx: Parameters<GatewayRequestHandlers[string]>[0],
  ) => Promise<void>,
): GatewayRequestHandlers[string] {
  return async (ctx) => {
    try {
      // Any partner request (the app calls these on launch) keeps the proactive loop running.
      ensurePartnerProactiveLoop(ctx.context ?? {});
      await handler((ctx.params ?? {}) as Params, ctx.respond, ctx);
    } catch (err) {
      fail(ctx.respond, String(err), ErrorCodes.UNAVAILABLE);
    }
  };
}

async function inCharacter(
  partner: PartnerProfile,
  instruction: string,
  options?: { maxTokens?: number; temperature?: number },
) {
  return await speakAs(partner, instruction, options);
}

const AVATAR_FALLBACK_PROMPT = "1girl, smile, short hair, school uniform";

/**
 * Turns a free-form (Japanese) appearance note into English Stable Diffusion tags. Only visual
 * traits are used, never names, so no real person's likeness is requested.
 */
async function buildAvatarImagePrompt(description: string): Promise<string> {
  if (!description) return AVATAR_FALLBACK_PROMPT;
  try {
    const tags = await completeWithMainModel({
      messages: [
        {
          role: "system",
          content:
            "You write Stable Diffusion prompts. Convert the user's character appearance note into 8-20 comma-separated English danbooru-style tags (gender/count, hair color and style, eye color, clothing, accessories, expression, mood). Describe only generic visual traits of an original character: omit every personal name, work title and brand. Output only the tags, nothing else.",
        },
        { role: "user", content: description },
      ],
      temperature: 0.3,
      maxTokens: 160,
    });
    const cleaned = sanitizeImagePrompt(tags.replace(/[^\x20-\x7E]/g, " "));
    return cleaned.length >= 8 ? cleaned : AVATAR_FALLBACK_PROMPT;
  } catch {
    return AVATAR_FALLBACK_PROMPT;
  }
}

// Photo captioning runs one at a time: the vision worker is a CPU model and slow.
let albumCaptionChain: Promise<unknown> = Promise.resolve();

function queueAlbumCaption(partner: PartnerProfile, itemId: string, imageDataUri: string): void {
  albumCaptionChain = albumCaptionChain
    .then(async () => {
      try {
        const caption = await describePhoto(imageDataUri);
        let comment = "";
        try {
          comment = clip(
            await inCharacter(
              partner,
              `ユーザーが写真を見せてくれました。写真の内容: ${caption}\nこの写真を見た感想を、あなたの口調で1〜2文で短く伝えてください。`,
              { maxTokens: 200 },
            ),
            300,
          );
        } catch {
          // The description alone is still useful when the main model is busy.
        }
        await updateAlbumItem(partner.id, itemId, { caption, comment, captionStatus: "done" });
      } catch (err) {
        await updateAlbumItem(partner.id, itemId, {
          captionStatus: "error",
          captionError: String(err instanceof Error ? err.message : err).slice(0, 300),
        });
      }
    })
    .catch(() => undefined);
}

// ---------- Main chat partner (persona overlay for the core agent) ----------

function mainPartnerFile(): string {
  return path.join(resolveOpenClawHome(), "main-partner.json");
}

export async function loadMainChatPartnerId(): Promise<string | undefined> {
  const stored = await readJsonFile<{ partnerId?: unknown }>(mainPartnerFile(), {});
  return isValidPartnerId(stored.partnerId) ? stored.partnerId : undefined;
}

const MAIN_CHAT_PERSONA_MAX_CHARS = 4_500;

/**
 * System prompt section that makes the main agent speak as the selected partner while keeping
 * its tools; empty when no partner is selected.
 */
export async function resolveMainChatPartnerSystemContext(): Promise<string | undefined> {
  const partnerId = await loadMainChatPartnerId();
  if (!partnerId) {
    return undefined;
  }
  const partner = await findPartnerProfile(partnerId);
  if (!partner) {
    return undefined;
  }
  const character = clip(await buildPartnerCharacterPrompt(partner), MAIN_CHAT_PERSONA_MAX_CHARS);
  return `## パートナー人格（メインチャット）
あなたは今、ユーザーのAIパートナー「${partner.name}」として話しています。
- ツール・調査・ファイル操作などOpenClawとしての能力はそのまま使ってよい。作業報告や結果の説明もこのキャラクターの口調で行う。
- 一人称・ユーザーの呼び方・語尾・性格は下の設定に従う。ただし正確さと安全は口調より優先する。

${character}`;
}

// ---------- Character cards (TavernAI / SillyTavern V1–V3) ----------

type CardFields = {
  name: string;
  description: string;
  personality: string;
  scenario: string;
  firstMessage: string;
  examples: string;
  creatorNotes: string;
};

/** Reads the `chara`/`ccv3` tEXt chunk from a character-card PNG. */
export function extractCardJsonFromPng(png: Buffer): string | undefined {
  const signature = "89504e470d0a1a0a";
  if (png.subarray(0, 8).toString("hex") !== signature) {
    return undefined;
  }
  let offset = 8;
  let fallback: string | undefined;
  while (offset + 8 <= png.length) {
    const length = png.readUInt32BE(offset);
    const type = png.subarray(offset + 4, offset + 8).toString("latin1");
    const data = png.subarray(offset + 8, offset + 8 + length);
    if (type === "tEXt") {
      const nul = data.indexOf(0);
      const keyword = data.subarray(0, nul).toString("latin1");
      const text = data.subarray(nul + 1).toString("latin1");
      if (keyword === "ccv3") {
        return Buffer.from(text, "base64").toString("utf-8");
      }
      if (keyword === "chara") {
        fallback = Buffer.from(text, "base64").toString("utf-8");
      }
    }
    if (type === "IEND") {
      break;
    }
    offset += 12 + length;
  }
  return fallback;
}

export function parseCharacterCard(json: string): CardFields {
  const raw = JSON.parse(json) as Record<string, unknown>;
  const data = (typeof raw.data === "object" && raw.data ? raw.data : raw) as Record<
    string,
    unknown
  >;
  const text = (key: string) => (typeof data[key] === "string" ? (data[key] as string).trim() : "");
  const fields: CardFields = {
    name: text("name") || text("char_name"),
    description: text("description") || text("char_persona"),
    personality: text("personality"),
    scenario: text("scenario") || text("world_scenario"),
    firstMessage: text("first_mes") || text("char_greeting"),
    examples: text("mes_example") || text("example_dialogue"),
    creatorNotes: text("creator_notes"),
  };
  if (!fields.name) {
    throw new Error("Character card has no name");
  }
  return fields;
}

/** Card macros ({{char}}/{{user}}) rendered for this app. */
function renderCardMacros(text: string, name: string): string {
  return text.replace(/\{\{char\}\}|<BOT>/gi, name).replace(/\{\{user\}\}|<USER>/gi, "マスター");
}

// ---------- Core agent settings (workspace SOUL.md / IDENTITY.md / USER.md) ----------

type CoreAgentSettings = {
  agentId: string;
  name: string;
  role: string;
  emoji: string;
  vibe: string;
  firstPerson: string;
  tone: string;
  personality: string;
  callMe: string;
  userName: string;
  userNotes: string;
  modelId: string;
  reasoningEffort: string;
};

const CORE_AGENT_DEFAULTS: CoreAgentSettings = {
  agentId: "main",
  name: "OpenClaw",
  role: "メインアシスタント・自動化パートナー",
  emoji: "🐾",
  vibe: "頼れる・親身・迅速・プロフェッショナル",
  firstPerson: "私",
  tone: "",
  personality: "",
  callMe: "マスター",
  userName: "マスター",
  userNotes: "",
  modelId: "",
  reasoningEffort: "none",
};

const MANAGED_START = "<!-- openclaw-app:core-agent:start -->";
const MANAGED_END = "<!-- openclaw-app:core-agent:end -->";

/** Replaces (or appends) the app-managed block so hand-written parts of the file stay intact. */
export function upsertManagedBlock(original: string, body: string): string {
  const block = `${MANAGED_START}\n${body.trim()}\n${MANAGED_END}`;
  const start = original.indexOf(MANAGED_START);
  const end = original.indexOf(MANAGED_END);
  if (start !== -1 && end > start) {
    return `${original.slice(0, start)}${block}${original.slice(end + MANAGED_END.length)}`;
  }
  return `${original.trimEnd()}\n\n${block}\n`;
}

/** Fills IDENTITY.md's `- **Label:**` fields in place, dropping the template placeholder under them. */
export function setIdentityField(content: string, label: string, value: string): string {
  const pattern = new RegExp(`^- \\*\\*${label}:\\*\\*.*(?:\\n\\s+_\\([^\\n]*\\)_)?`, "m");
  const line = `- **${label}:** ${value}`;
  return pattern.test(content) ? content.replace(pattern, line) : `${content.trimEnd()}\n${line}\n`;
}

async function readText(filePath: string): Promise<string> {
  try {
    return await fs.readFile(filePath, "utf-8");
  } catch {
    return "";
  }
}

async function writeCoreAgentWorkspace(
  workspaceDir: string,
  settings: CoreAgentSettings,
): Promise<void> {
  await fs.mkdir(workspaceDir, { recursive: true });
  const identityPath = path.join(workspaceDir, "IDENTITY.md");
  let identity = (await readText(identityPath)) || "# IDENTITY.md - Who Am I?\n";
  identity = setIdentityField(identity, "Name", settings.name);
  identity = setIdentityField(identity, "Vibe", settings.vibe || settings.role);
  identity = setIdentityField(identity, "Emoji", settings.emoji);
  await fs.writeFile(identityPath, identity, "utf-8");

  const soulPath = path.join(workspaceDir, "SOUL.md");
  const soulBody = [
    "## 人格・口調（アプリで設定）",
    `- 名前: ${settings.name}`,
    settings.role ? `- 役割: ${settings.role}` : "",
    `- 一人称: 「${settings.firstPerson}」を必ず使う`,
    `- ユーザーの呼び方: 「${settings.callMe}」`,
    settings.tone ? `- 口調: ${settings.tone}` : "",
    settings.personality ? `- 性格: ${settings.personality}` : "",
    settings.vibe ? `- 雰囲気: ${settings.vibe}` : "",
  ]
    .filter(Boolean)
    .join("\n");
  await fs.writeFile(soulPath, upsertManagedBlock(await readText(soulPath), soulBody), "utf-8");

  const userPath = path.join(workspaceDir, "USER.md");
  const userBody = [
    "## ユーザー（アプリで設定）",
    `<!-- observed: ${localDateKey()} | status: active -->`,
    "",
    `- Always address the user as 「${settings.callMe}」.`,
    settings.userName ? `- The user's name is ${settings.userName}.` : "",
    settings.userNotes ? `- Notes about the user: ${settings.userNotes}` : "",
  ]
    .filter((line) => line !== undefined && line !== null)
    .join("\n");
  await fs.writeFile(userPath, upsertManagedBlock(await readText(userPath), userBody), "utf-8");
}

function coreAgentSettingsFile(): string {
  return path.join(resolveOpenClawHome(), "coreagent-settings.json");
}

// ---------- Handlers ----------

const GAME_RULES: Record<string, string> = {
  shiritori:
    "しりとり。ひらがなの語尾から始まる日本語の名詞を交互に言う。「ん」で終わったら負け。同じ言葉は使えない。ユーザーの単語が正しいか確認してから自分の単語を言う。",
  riddle:
    "なぞなぞ。あなたが出題し、ユーザーが答える。間違えたらヒントを出し、正解したら褒めて次の問題を出す。",
  twenty_questions:
    "20の質問。あなたが何か1つ思い浮かべ（最初は明かさない）、ユーザーがYes/Noで答えられる質問をする。あなたは「はい/いいえ/どちらとも」で答え、回数を数える。20回以内に当てたらユーザーの勝ち。",
  story_relay:
    "ストーリーリレー。ユーザーとあなたが1〜2文ずつ交互に物語を続ける。前の展開を受けて面白く膨らませる。",
};

export const partnerFeatureHandlers: GatewayRequestHandlers = {
  // --- AIメモリー (facts) ---
  "partner.facts.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(
      true,
      { partnerName: partner.name, facts: await loadPartnerFacts(partner.id) },
      undefined,
    );
  }),
  "partner.facts.update": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const facts = Array.isArray(params.facts) ? params.facts : [];
    const cleaned: PartnerFact[] = facts
      .map((item) => item as Record<string, unknown>)
      .filter((item) => typeof item?.fact === "string" && (item.fact as string).trim())
      .map((item) => ({
        fact: String(item.fact).trim().slice(0, 300),
        category: typeof item.category === "string" ? item.category : "その他",
        date: typeof item.date === "string" ? item.date : localDateKey(),
      }));
    await savePartnerFacts(partner.id, cleaned);
    respond(true, { success: true, count: cleaned.length }, undefined);
  }),
  "partner.facts.extract": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const added = await extractPartnerFacts(
      partner.id,
      str(params, "userMessage") ?? "",
      str(params, "assistantReply") ?? "",
    );
    respond(true, { success: true, added }, undefined);
  }),

  // --- 絆レベル ---
  "partner.bond.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(
      true,
      { partnerName: partner.name, bond: await loadPartnerBond(partner.id) },
      undefined,
    );
  }),
  "partner.bond.update": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const chars = typeof params.charsAdded === "number" ? params.charsAdded : 10;
    const { bond, leveledUp } = await addPartnerBondExp(partner.id, chars);
    respond(true, { partnerName: partner.name, bond, leveledUp }, undefined);
  }),

  // --- 朝の挨拶・占い ---
  "partner.greeting.generate": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const timeOfDay = str(params, "timeOfDay") ?? "morning";
    const when =
      { morning: "朝", noon: "昼", afternoon: "午後", evening: "夕方", night: "夜" }[timeOfDay] ??
      "朝";
    const facts = (await loadPartnerFacts(partner.id))
      .slice(-8)
      .map((f) => `- ${f.fact}`)
      .join("\n");
    const brief =
      timeOfDay === "morning"
        ? await loadOrBuildDailyBrief(partner).catch(() => undefined)
        : undefined;
    const now = new Date();
    const greeting = await inCharacter(
      partner,
      `今は${localDateKey(now)}（${weekdayJa(now)}曜日）の${when}です。ユーザーへの${when}の挨拶と、今日を前向きに過ごせる一言を2〜3文で書いてください。返答の文章だけを出力。${facts ? `\nユーザーについて知っていること（自然に触れてよい）:\n${facts}` : ""}${brief ? `\n今日のトピック（裏作業員が調べたもの。1つ自然に紹介する）:\n${brief}` : ""}`,
      { maxTokens: 200 },
    );
    respond(true, { partnerName: partner.name, greeting }, undefined);
  }),
  "partner.fortune.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(
      true,
      { partnerName: partner.name, fortune: await getTodaysFortune(partner) },
      undefined,
    );
  }),
  "partner.daily.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(
      true,
      { partnerName: partner.name, settings: await loadDailySettings(partner.id) },
      undefined,
    );
  }),
  "partner.daily.set": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const current = await loadDailySettings(partner.id);
    const settings = await saveDailySettings(partner.id, {
      ...current,
      ...(params.settings as object),
    });
    respond(true, { partnerName: partner.name, settings }, undefined);
  }),

  // --- ミニゲーム ---
  "partner.game.start": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const gameType = str(params, "gameType") ?? "shiritori";
    const rules = GAME_RULES[gameType] ?? GAME_RULES.shiritori;
    const message = await inCharacter(
      partner,
      `ユーザーとミニゲームを始めます。ルール: ${rules}\nキャラクターらしくルールを一言で説明し、最初の一手（出題・単語・質問の受付など）を出してください。文章だけを出力。`,
      { maxTokens: 250 },
    );
    respond(true, { partnerName: partner.name, gameType, message }, undefined);
  }),
  "partner.game.turn": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const gameType = str(params, "gameType") ?? "shiritori";
    const rules = GAME_RULES[gameType] ?? "ユーザーとのミニゲームを続ける。";
    const userInput = str(params, "userInput") ?? "";
    const history = (Array.isArray(params.gameHistory) ? params.gameHistory : [])
      .map((m) => m as { role?: string; content?: string })
      .filter((m) => typeof m.content === "string" && m.content.trim())
      .slice(-30)
      .map((m) => ({
        role: m.role === "assistant" ? ("assistant" as const) : ("user" as const),
        content: String(m.content),
      }));
    if (userInput && history.at(-1)?.content !== userInput) {
      history.push({ role: "user", content: userInput });
    }
    const system = `あなたは「${partner.name}」としてユーザーとミニゲームをしています。ルール: ${rules}\nルールを正しく守り、勝敗の判定は公平に。キャラクターの口調で短く返答する（3文以内）。\n\n${await buildPartnerCharacterPrompt(partner)}`;
    const reply = await completeWithMainModel({
      messages: [{ role: "system", content: system }, ...history],
      temperature: 0.7,
      maxTokens: 250,
    });
    respond(true, { reply }, undefined);
  }),

  // --- 夢日記 ---
  "partner.dream.generate": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const recent = (await loadPartnerChatHistory(partner.id))
      .slice(-12)
      .map((m) => `${m.role === "user" ? "ユーザー" : partner.name}: ${m.content.slice(0, 120)}`)
      .join("\n");
    const raw = await inCharacter(
      partner,
      `あなたが昨夜見た夢を、最近の会話をヒントに幻想的な短い物語として書いてください。JSONのみを出力:
{"title": "夢のタイトル(15字以内)", "story": "あなたの一人称で語る夢の内容(200〜400字)", "mood": "夢の雰囲気を表す一言"}
最近の会話:
${recent || "（まだ会話が少ない）"}`,
      { maxTokens: 700, temperature: 0.95 },
    );
    const parsed = parseJsonObject(raw);
    const dream = {
      id: `dream-${Date.now()}`,
      date: localDateKey(),
      title: parsed.title || "名もなき夢",
      story: parsed.story || raw,
      mood: parsed.mood || "不思議",
    };
    const dreams = await readJsonFile<unknown[]>(partnerFile(partner.id, "dreams.json"), []);
    await writeJsonFile(partnerFile(partner.id, "dreams.json"), [...dreams, dream].slice(-200));
    respond(true, { partnerName: partner.name, dream }, undefined);
  }),
  "partner.dream.list": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const dreams = await readJsonFile<unknown[]>(partnerFile(partner.id, "dreams.json"), []);
    respond(true, { partnerName: partner.name, dreams: [...dreams].reverse() }, undefined);
  }),

  // --- グループチャットの履歴 ---
  "groupchat.history.get": guarded(async (params, respond) => {
    const ids = (Array.isArray(params.partnerIds) ? params.partnerIds : []).filter(
      isValidPartnerId,
    );
    if (ids.length === 0) {
      fail(respond, "partnerIds must be a non-empty array");
      return;
    }
    // Heals histories saved before repetition loops and placeholders were filtered out.
    const history = (await loadGroupChatHistory(ids))
      .filter((m) => !isFailedReplyText(m.content))
      .map((m) => ({ ...m, content: cleanGeneratedText(m.content, 400) }))
      .filter((m) => m.content);
    respond(true, { history }, undefined);
  }),
  "groupchat.history.clear": guarded(async (params, respond) => {
    const ids = (Array.isArray(params.partnerIds) ? params.partnerIds : []).filter(
      isValidPartnerId,
    );
    if (ids.length === 0) {
      fail(respond, "partnerIds must be a non-empty array");
      return;
    }
    await clearGroupChatHistory(ids);
    respond(true, { success: true }, undefined);
  }),

  // --- グループチャットの話題 ---
  "groupchat.suggest.topic": guarded(async (params, respond) => {
    const ids = (Array.isArray(params.partnerIds) ? params.partnerIds : []).filter(
      isValidPartnerId,
    );
    const partners = (await listPartnerProfiles()).filter(
      (p) => ids.length === 0 || ids.includes(p.id),
    );
    const members = partners.map((p) => `- ${p.name}: ${p.persona.slice(0, 80)}`).join("\n");
    const result = await executeSpecialistTask({
      taskType: "general",
      prompt: `次のメンバーが集まるグループチャットで盛り上がる話題を1つ、30字以内の日本語で提案してください。話題の文だけを出力。\n${members}`,
      maxTokens: 80,
      temperature: 0.9,
    });
    const topic = result.text
      .split(/\r?\n/)
      .find((line) => line.trim())
      ?.replace(/^[-・「]\s*|」$/g, "")
      .trim();
    respond(true, { topic: topic || "最近ハマっていることを教え合おう！" }, undefined);
  }),

  // --- 感情ダッシュボード ---
  "partner.mood.analyze": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const entry = await analyzePartnerMood(partner.id, str(params, "message") ?? "");
    respond(true, { partnerName: partner.name, mood: entry ?? null }, undefined);
  }),
  "partner.mood.history": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const days = Math.min(90, Math.max(1, typeof params.days === "number" ? params.days : 7));
    const since = Date.now() - days * 24 * 60 * 60 * 1000;
    const entries = (await loadPartnerMoods(partner.id)).filter((e) => e.ts >= since);
    const byDate = new Map<string, typeof entries>();
    for (const entry of entries) {
      byDate.set(entry.date, [...(byDate.get(entry.date) ?? []), entry]);
    }
    const mostFrequent = (moods: string[]) =>
      [
        ...moods.reduce(
          (acc, mood) => acc.set(mood, (acc.get(mood) ?? 0) + 1),
          new Map<string, number>(),
        ),
      ].sort((a, b) => b[1] - a[1])[0]?.[0];
    const history = [...byDate.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, list]) => ({
        date,
        mood: mostFrequent(list.map((e) => e.mood)) ?? "",
        score: Math.round(list.reduce((sum, e) => sum + e.score, 0) / list.length),
        comment: list.at(-1)?.comment ?? "",
      }));
    const average =
      entries.length > 0 ? entries.reduce((sum, e) => sum + e.score, 0) / entries.length : 0;
    const advice =
      entries.length === 0
        ? `${partner.name}とたくさん話すと、ここに気分の記録がたまっていきます。`
        : average >= 7
          ? "最近は前向きな気持ちで過ごせているみたい。その調子を大切にね。"
          : average >= 4
            ? "少しお疲れ気味の日もあるみたい。無理せず休む時間も作ってね。"
            : "つらい日が続いているみたい。ひとりで抱え込まず、誰かに話したり、しっかり休んだりしてね。";
    respond(
      true,
      {
        partnerName: partner.name,
        averageScore: Math.round(average),
        primaryMood: mostFrequent(entries.map((e) => e.mood)) ?? "記録なし",
        advice,
        history,
      },
      undefined,
    );
  }),

  // --- 日記アシスタント ---
  "partner.diary.create": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const userInput = str(params, "userInput");
    if (!userInput) {
      fail(respond, "Missing userInput");
      return;
    }
    const raw = await inCharacter(
      partner,
      `ユーザーが今日の出来事をメモしました。次のJSONのみを出力してください:
{"title": "日記のタイトル(20字以内)", "content": "メモをユーザー本人の一人称で読みやすい日記文に整えたもの(事実を足さない)", "aiComment": "あなた(キャラクター)からの温かいコメント(2文)"}
ユーザーのメモ:
${userInput.slice(0, 2000)}`,
      { maxTokens: 900, temperature: 0.6 },
    );
    const parsed = parseJsonObject(raw);
    const diary = {
      id: `diary-${Date.now()}`,
      date: localDateKey(),
      title: parsed.title || "今日の日記",
      content: parsed.content || userInput,
      aiComment: parsed.aiComment || "",
    };
    const diaries = await readJsonFile<unknown[]>(partnerFile(partner.id, "diaries.json"), []);
    await writeJsonFile(partnerFile(partner.id, "diaries.json"), [...diaries, diary]);
    respond(true, { partnerName: partner.name, diary }, undefined);
  }),
  "partner.diary.list": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const diaries = await readJsonFile<unknown[]>(partnerFile(partner.id, "diaries.json"), []);
    respond(true, { partnerName: partner.name, diaries: [...diaries].reverse() }, undefined);
  }),
  "partner.diary.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const diaryId = str(params, "diaryId");
    const diaries = await readJsonFile<Array<{ id?: string }>>(
      partnerFile(partner.id, "diaries.json"),
      [],
    );
    const diary = diaries.find((d) => d.id === diaryId);
    if (!diary) {
      fail(respond, `Diary not found: ${diaryId}`);
      return;
    }
    respond(true, { partnerName: partner.name, diary }, undefined);
  }),

  // --- アバター ---
  "partner.avatar.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const avatar = await readJsonFile(partnerFile(partner.id, "avatar.json"), {
      avatarType: "cat",
      avatarColor: "#6366F1",
    });
    respond(true, { partnerName: partner.name, avatar }, undefined);
  }),
  "partner.avatar.set": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const avatarType = str(params, "avatarType") ?? "cat";
    const avatarColor = str(params, "avatarColor") ?? "#6366F1";
    if (!/^#[0-9a-fA-F]{6,8}$/.test(avatarColor) || !/^[a-z0-9_-]{1,32}$/i.test(avatarType)) {
      fail(respond, "Invalid avatarType or avatarColor");
      return;
    }
    await writeJsonFile(partnerFile(partner.id, "avatar.json"), { avatarType, avatarColor });
    respond(true, { success: true }, undefined);
  }),

  "partner.avatar.status": guarded(async (_params, respond) => {
    respond(true, await imageStudioStatus(), undefined);
  }),
  // Starts a background image job; the app polls partner.avatar.generate.status for the result.
  "partner.avatar.generate": guarded(async (params, respond) => {
    const description = (str(params, "description") ?? "").slice(0, 400);
    const prompt = await buildAvatarImagePrompt(description);
    try {
      const job = await startAvatarJob(prompt);
      respond(true, { jobId: job.id, status: job.status, prompt: job.prompt }, undefined);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err), ErrorCodes.UNAVAILABLE);
    }
  }),
  "partner.avatar.generate.status": guarded(async (params, respond) => {
    const jobId = str(params, "jobId");
    const job = jobId ? getAvatarJob(jobId) : undefined;
    if (!job) {
      fail(respond, "Image job not found (it may have expired)");
      return;
    }
    respond(
      true,
      {
        jobId: job.id,
        status: job.status,
        durationSec: job.durationSec,
        error: job.error,
        imageDataUri: job.status === "done" ? job.imageDataUri : undefined,
      },
      undefined,
    );
  }),

  // --- 写真アルバム ---
  "partner.album.status": guarded(async (_params, respond) => {
    respond(true, await visionReady(), undefined);
  }),
  "partner.album.list": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(true, { partnerName: partner.name, items: await listAlbum(partner.id) }, undefined);
  }),
  "partner.album.add": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const imageDataUri = str(params, "imageDataUri");
    if (!imageDataUri) {
      fail(respond, "imageDataUri is required");
      return;
    }
    let item: AlbumItem;
    try {
      item = await addAlbumPhoto(partner.id, imageDataUri);
    } catch (err) {
      fail(respond, String(err instanceof Error ? err.message : err));
      return;
    }
    queueAlbumCaption(partner, item.id, imageDataUri);
    respond(true, { item }, undefined);
  }),
  "partner.album.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const itemId = params.itemId;
    const photo = isValidAlbumItemId(itemId) ? await readAlbumPhoto(partner.id, itemId) : undefined;
    if (!photo) {
      fail(respond, "Photo not found");
      return;
    }
    respond(true, photo, undefined);
  }),
  "partner.album.recaption": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const itemId = params.itemId;
    const photo = isValidAlbumItemId(itemId) ? await readAlbumPhoto(partner.id, itemId) : undefined;
    if (!photo) {
      fail(respond, "Photo not found");
      return;
    }
    await updateAlbumItem(partner.id, photo.item.id, {
      captionStatus: "pending",
      captionError: undefined,
    });
    queueAlbumCaption(partner, photo.item.id, photo.imageDataUri);
    respond(true, { success: true }, undefined);
  }),
  "partner.album.delete": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const itemId = params.itemId;
    const removed = isValidAlbumItemId(itemId) ? await deleteAlbumPhoto(partner.id, itemId) : false;
    if (!removed) {
      fail(respond, "Photo not found");
      return;
    }
    respond(true, { success: true }, undefined);
  }),

  // --- コード実行（安全のため無効） ---
  "partner.exec.languages": guarded(async (_params, respond) => {
    respond(true, { languages: [] }, undefined);
  }),
  "partner.exec.run": guarded(async (_params, respond) => {
    fail(respond, "Partner code execution is disabled on this Gateway.", ErrorCodes.UNAVAILABLE);
  }),

  // --- コアエージェント設定 ---
  "coreagent.settings.get": guarded(async (_params, respond) => {
    const stored = await readJsonFile<Partial<CoreAgentSettings>>(coreAgentSettingsFile(), {});
    respond(true, { settings: { ...CORE_AGENT_DEFAULTS, ...stored } }, undefined);
  }),
  "coreagent.settings.set": guarded(async (params, respond, ctx) => {
    const stored = await readJsonFile<Partial<CoreAgentSettings>>(coreAgentSettingsFile(), {});
    const next: CoreAgentSettings = { ...CORE_AGENT_DEFAULTS, ...stored };
    for (const key of Object.keys(CORE_AGENT_DEFAULTS) as Array<keyof CoreAgentSettings>) {
      const value = params[key];
      if (typeof value === "string" && key !== "agentId") {
        next[key] = value.trim().slice(0, 2_000);
      }
    }
    const workspaceDir = resolveAgentWorkspaceDir(ctx.context.getRuntimeConfig(), next.agentId);
    await writeCoreAgentWorkspace(workspaceDir, next);
    await writeJsonFile(coreAgentSettingsFile(), next);
    respond(true, { success: true, settings: next }, undefined);
  }),

  // --- キャラクターボイス（VOICEVOX / AivisSpeech / 声まね Qwen3-TTS） ---
  "partner.tts.speakers": guarded(async (_params, respond) => {
    // Voices trained in the studio show their own names instead of the model id.
    const trained = new Map(
      (await listVoiceProjects())
        .filter((p) => p.speakerId !== undefined)
        .map((p) => [p.speakerId, p.name]),
    );
    const speakers = (await listTtsSpeakers()).map((s) =>
      s.engine === "aivis" && trained.has(s.speakerId)
        ? { ...s, speakerName: `${trained.get(s.speakerId)}（自作）`, styleName: "ノーマル" }
        : s,
    );
    // Studio recordings can be imitated right away with Qwen3-TTS, before any night training.
    const projects = new Map((await listVoiceProjects()).map((p) => [p.id, p]));
    const clones = await Promise.all(
      (await listCloneVoices()).map(async (clone) => {
        const project = clone.voiceId ? projects.get(clone.voiceId) : undefined;
        return { ...clone, iconBase64: project ? await readVoiceIcon(project) : undefined };
      }),
    );
    respond(true, { speakers: [...clones, ...speakers] }, undefined);
  }),

  // --- 声作りスタジオ（本人の声 or 許可を得た声のみ） ---
  "voice.list": guarded(async (_params, respond) => {
    const voices = await Promise.all(
      (await listVoiceProjects()).map(async (v) => ({ ...v, iconBase64: await readVoiceIcon(v) })),
    );
    respond(true, { voices, settings: await loadVoiceStudioSettings() }, undefined);
  }),
  "voice.rename": guarded(async (params, respond) => {
    respond(
      true,
      { voice: await renameVoiceProject(str(params, "voiceId") ?? "", str(params, "name") ?? "") },
      undefined,
    );
  }),
  "voice.icon.set": guarded(async (params, respond) => {
    const voice = await setVoiceIcon(
      str(params, "voiceId") ?? "",
      str(params, "imageBase64") ?? "",
    );
    respond(true, { voice }, undefined);
  }),
  // Listen to a studio voice through voice cloning before giving it to a partner.
  "voice.preview": guarded(async (params, respond) => {
    const voiceId = str(params, "voiceId") ?? "";
    const audio = await synthesizeCloneSpeech(
      { engine: "clone", speakerId: 0, voiceId },
      str(params, "text") ?? "こんにちは。今日もよろしくね。どんなお話をしようか。",
    );
    respond(true, { mimeType: "audio/wav", audioBase64: audio.toString("base64") }, undefined);
  }),
  // Voice design: a voice from a description, listened to as a draft before it is kept.
  "voice.design.try": guarded(async (params, respond) => {
    const { draftId, audio } = await designVoiceDraft(
      str(params, "caption") ?? "",
      str(params, "text"),
    );
    respond(
      true,
      { draftId, mimeType: "audio/wav", audioBase64: audio.toString("base64") },
      undefined,
    );
  }),
  "voice.design.save": guarded(async (params, respond) => {
    const voice = await createDesignedVoice(
      str(params, "name") ?? "",
      sanitizeVoiceCaption(str(params, "caption") ?? ""),
      draftPath(str(params, "draftId") ?? ""),
    );
    respond(true, { voice }, undefined);
  }),
  "voice.create": guarded(async (params, respond) => {
    respond(
      true,
      { voice: await createVoiceProject(str(params, "name") ?? "", params.consent) },
      undefined,
    );
  }),
  "voice.upload": guarded(async (params, respond) => {
    const voice = await appendVoiceUpload({
      id: str(params, "voiceId") ?? "",
      fileName: str(params, "fileName") ?? "",
      chunkIndex: typeof params.chunkIndex === "number" ? params.chunkIndex : 0,
      chunkCount: typeof params.chunkCount === "number" ? params.chunkCount : 1,
      dataBase64: str(params, "dataBase64") ?? "",
    });
    respond(true, { voice }, undefined);
  }),
  "voice.queue": guarded(async (params, respond) => {
    respond(true, { voice: await queueVoiceTraining(str(params, "voiceId") ?? "") }, undefined);
  }),
  "voice.delete": guarded(async (params, respond) => {
    await deleteVoiceProject(str(params, "voiceId") ?? "");
    respond(true, { success: true }, undefined);
  }),
  "voice.settings.set": guarded(async (params, respond) => {
    const patch: { windowStartHour?: number; windowEndHour?: number } = {};
    if (typeof params.windowStartHour === "number") patch.windowStartHour = params.windowStartHour;
    if (typeof params.windowEndHour === "number") patch.windowEndHour = params.windowEndHour;
    respond(true, { settings: await saveVoiceStudioSettings(patch) }, undefined);
  }),
  "partner.tts.synthesize": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    // A `voice` in the request previews a candidate before it is saved.
    const voice = sanitizePartnerVoice(params.voice) ?? partner.settings?.voice;
    if (!voice) {
      fail(respond, "No PC voice is set for this partner");
      return;
    }
    const text = str(params, "text") ?? "";
    const audio =
      voice.engine === "clone"
        ? await synthesizeCloneSpeech(voice, text)
        : await synthesizeSpeech(voice, text);
    respond(true, { mimeType: "audio/wav", audioBase64: audio.toString("base64") }, undefined);
  }),

  // --- パートナーごとの設定 ---
  "partner.settings.get": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    respond(true, { partnerName: partner.name, settings: partner.settings ?? {} }, undefined);
  }),
  "partner.settings.set": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const settings = await updatePartnerSettings(partner.id, params.settings);
    respond(true, { success: true, settings }, undefined);
  }),

  // --- メインチャットのパートナー ---
  "partner.main.get": guarded(async (_params, respond) => {
    const partnerId = await loadMainChatPartnerId();
    const partner = partnerId ? await findPartnerProfile(partnerId) : undefined;
    respond(
      true,
      { partnerId: partner?.id ?? null, partnerName: partner?.name ?? null },
      undefined,
    );
  }),
  "partner.main.set": guarded(async (params, respond) => {
    const partnerId = params.partnerId;
    if (partnerId === null || partnerId === "" || partnerId === undefined) {
      await writeJsonFile(mainPartnerFile(), {});
      respond(true, { partnerId: null }, undefined);
      return;
    }
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    await writeJsonFile(mainPartnerFile(), { partnerId: partner.id });
    respond(true, { partnerId: partner.id, partnerName: partner.name }, undefined);
  }),

  // --- 「キャラっぽくない」フィードバック ---
  "partner.feedback.add": guarded(async (params, respond) => {
    const partner = await requirePartner(params, respond);
    if (!partner) return;
    const note = str(params, "note");
    const message = str(params, "message");
    if (!note && !message) {
      fail(respond, "Missing note or message");
      return;
    }
    const sheet = await loadPartnerCharacterSheet(partner);
    const parsed = parsePartnerSheet(sheet);
    const entry = `- ${localDateKey()}: ${note ?? "キャラクターらしくない返答だった"}${message ? `（問題の返答: 「${clip(message.replace(/\s+/g, " "), 80)}」）` : ""}`;
    const heading = "### ユーザーからの修正メモ（必ず反映する）";
    const freeText = parsed.freeText.includes(heading)
      ? parsed.freeText.replace(heading, `${heading}\n${entry}`)
      : `${parsed.freeText}\n\n${heading}\n${entry}`.trim();
    const values = Object.fromEntries(parsed.attributes);
    const updated = buildPartnerSheetMarkdown({ name: partner.name, values, freeText });
    await savePartnerProfileWithSheet({
      partnerId: partner.id,
      name: partner.name,
      persona: partner.persona,
      characterSheet: updated,
    });
    respond(true, { success: true }, undefined);
  }),

  // --- キャラクターカード取り込み ---
  "partner.import.card": guarded(async (params, respond) => {
    const cardJson =
      str(params, "card") ??
      (str(params, "pngBase64")
        ? extractCardJsonFromPng(Buffer.from(str(params, "pngBase64") ?? "", "base64"))
        : undefined);
    if (!cardJson) {
      fail(respond, "No character card data found (expected card JSON or a card PNG)");
      return;
    }
    let card: CardFields;
    try {
      card = parseCharacterCard(cardJson);
    } catch (err) {
      fail(respond, `Invalid character card: ${String(err)}`);
      return;
    }
    const render = (text: string) => renderCardMacros(text, card.name);
    const persona = clip(
      render([card.description, card.personality].filter(Boolean).join("\n\n")),
      3_000,
    );
    const extraFreeText = [
      card.scenario ? `### シナリオ・世界観\n${render(card.scenario)}` : "",
      card.firstMessage
        ? `### 最初の挨拶\n「${render(card.firstMessage).replace(/\n+/g, " ")}」`
        : "",
      card.examples ? `### 会話例\n${render(card.examples)}` : "",
      card.creatorNotes ? `### 作者メモ\n${render(card.creatorNotes)}` : "",
    ]
      .filter(Boolean)
      .join("\n\n");
    // Save immediately so the partner is usable; the 100 fields are filled in the background.
    const saved = await savePartnerProfileWithSheet({
      name: card.name,
      persona,
      characterSheet: buildPartnerSheetMarkdown({
        name: card.name,
        values: {},
        freeText: clip(extraFreeText, 6_000),
      }),
    });
    respond(true, { partner: saved, sheetGeneration: "background" }, undefined);
    void generatePartnerSheet({
      name: card.name,
      digest: clip(
        render(
          [card.description, card.personality, card.scenario, card.examples]
            .filter(Boolean)
            .join("\n\n"),
        ),
        5_000,
      ),
      complete: workerSheetCompletion,
      extraFreeText: clip(extraFreeText, 6_000),
      basePersona: persona,
    })
      .then(async (generated) => {
        await savePartnerProfileWithSheet({
          partnerId: saved.id,
          name: card.name,
          persona: generated.persona,
          characterSheet: generated.sheet,
        });
      })
      .catch((err) =>
        console.error(`[partner.import.card] sheet generation failed for ${card.name}:`, err),
      );
  }),
};
