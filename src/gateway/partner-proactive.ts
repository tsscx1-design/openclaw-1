import { randomUUID } from "node:crypto";
import { createWebSearchTool } from "../agents/tools/web-tools.js";
import type { OpenClawConfig } from "../config/types.openclaw.js";
import { onMediaJobFinished, type MediaJob } from "./media-studio.js";
import { appendNewsDigest, loadDailySettings, runDailyMessages } from "./partner-daily.js";
import {
  localDateKey,
  partnerFile,
  readJsonFile,
  writeJsonFile,
  loadPartnerFacts,
} from "./partner-store.js";
import { readPcHealth } from "./pc-health.js";
import {
  buildPartnerCharacterPrompt,
  completeWithMainModel,
  listPartnerProfiles,
  loadPartnerChatHistory,
  savePartnerChatHistory,
  type PartnerProfile,
} from "./server-methods/partners.js";
import { executeSpecialistTask } from "./specialist-router.js";
import { finishedJobNotice } from "./studio-assistant.js";
import { runVoiceTrainingTick } from "./voice-studio.js";

type NotifyNode = { nodeId: string; connId?: string; platform?: string; commands: string[] };
type NodeRegistryLike = {
  listConnected(): NotifyNode[];
  invoke(params: {
    nodeId: string;
    expectedConnId?: string;
    command: string;
    params: Record<string, unknown>;
    timeoutMs?: number;
    idempotencyKey?: string;
  }): Promise<{ ok: boolean }>;
};

type ProactiveState = { lastSentAt?: number; lastBriefDate?: string; lastBirthdayYear?: number };
type ProactiveReason = "morning" | "birthday" | "checkin";

const TICK_MS = 15 * 60_000;
/** Search text kept per daily topic: five topics plus the prompt fit an 8K-token context. */
const BRIEF_CHARS_PER_TOPIC = 1_500;
const BRIEF_SEARCH_GAP_MS = 6_000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Runs `task`, waiting and retrying when the search provider reports its rate limit (HTTP 429 /
 * quota). The daily brief runs once a morning, so waiting a minute beats losing a topic.
 */
export async function withRateLimitRetry<T>(
  task: () => Promise<T>,
  delaysMs: readonly number[] = [30_000, 60_000],
): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await task();
    } catch (err) {
      const delay = delaysMs[attempt];
      if (delay === undefined || !/\b429\b|quota|rate limit/i.test(String(err))) throw err;
      await sleep(delay);
    }
  }
}
const QUIET_START_HOUR = 22;
const QUIET_END_HOUR = 8;
const CHECKIN_AFTER_MS = 24 * 60 * 60_000;

let nodeRegistry: NodeRegistryLike | undefined;
let getConfig: (() => OpenClawConfig) | undefined;
let timer: ReturnType<typeof setInterval> | undefined;
let ticking = false;

/**
 * Starts the once-per-process loop that lets partners with `settings.proactive` start a
 * conversation. Handlers call this with their context, which also refreshes the node registry.
 */
export function ensurePartnerProactiveLoop(context: {
  nodeRegistry?: unknown;
  getRuntimeConfig?: () => OpenClawConfig;
}): void {
  if (context.nodeRegistry) {
    nodeRegistry = context.nodeRegistry as NodeRegistryLike;
  }
  if (context.getRuntimeConfig) {
    getConfig = context.getRuntimeConfig;
  }
  if (timer) {
    return;
  }
  onMediaJobFinished((job) => announceFinishedJob(job));
  timer = setInterval(() => void runPartnerProactiveTick(), TICK_MS);
  timer.unref?.();
  setTimeout(() => void runPartnerProactiveTick(), 60_000).unref?.();
}

/** Parses 「誕生日は5月3日」/「誕生日 5/3」 style facts. */
export function birthdayFromFacts(
  facts: Array<{ fact: string }>,
): { month: number; day: number } | undefined {
  for (const { fact } of facts) {
    if (!fact.includes("誕生日")) continue;
    const match =
      /(\d{1,2})\s*月\s*(\d{1,2})\s*日/.exec(fact) ?? /(\d{1,2})\s*[/／]\s*(\d{1,2})/.exec(fact);
    if (match) {
      const month = Number(match[1]);
      const day = Number(match[2]);
      if (month >= 1 && month <= 12 && day >= 1 && day <= 31) return { month, day };
    }
  }
  return undefined;
}

/** Decides whether a partner should reach out now. Pure so the schedule is testable. */
export function chooseProactiveReason(params: {
  now: Date;
  state: ProactiveState;
  lastChatAt?: number;
  birthday?: { month: number; day: number };
  /** The partner already sends a scheduled morning greeting (see partner-daily.ts). */
  skipMorning?: boolean;
}): ProactiveReason | undefined {
  const { now, state } = params;
  const hour = now.getHours();
  if (hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR) return undefined;
  const today = localDateKey(now);
  if (
    params.birthday &&
    params.birthday.month === now.getMonth() + 1 &&
    params.birthday.day === now.getDate() &&
    state.lastBirthdayYear !== now.getFullYear()
  ) {
    return "birthday";
  }
  if (!params.skipMorning && hour < 10 && state.lastBriefDate !== today) return "morning";
  const quietFor = now.getTime() - (params.lastChatAt ?? 0);
  const sinceLastPing = now.getTime() - (state.lastSentAt ?? 0);
  if (params.lastChatAt && quietFor >= CHECKIN_AFTER_MS && sinceLastPing >= CHECKIN_AFTER_MS)
    return "checkin";
  return undefined;
}

/**
 * Site names a search cited. Answer providers (Gemini) cite through redirect links, so their
 * titles (usually the domain) name the site; listing providers give the real page URL.
 */
export function citedSites(details: unknown, max = 4): string[] {
  const record = (details ?? {}) as { citations?: unknown; results?: unknown };
  const hits = Array.isArray(record.citations)
    ? record.citations
    : Array.isArray(record.results)
      ? record.results
      : [];
  const sites: string[] = [];
  for (const item of hits) {
    const hit = (item ?? {}) as { title?: unknown; url?: unknown };
    let host = "";
    try {
      host = typeof hit.url === "string" ? new URL(hit.url).hostname.replace(/^www\./, "") : "";
    } catch {
      host = "";
    }
    const title = typeof hit.title === "string" ? hit.title.trim() : "";
    const site = host && !host.includes("vertexaisearch") ? host : title;
    if (site && site.length <= 80 && !sites.includes(site)) sites.push(site);
  }
  return sites.slice(0, max);
}

/** Researches the partner's daily topics on a worker; cached for the day. */
export async function loadOrBuildDailyBrief(partner: PartnerProfile): Promise<string | undefined> {
  const topics = partner.settings?.dailyTopics ?? [];
  if (topics.length === 0) return undefined;
  const news = partner.settings?.newsDigest === true;
  const cachePath = partnerFile(partner.id, "daily-brief.json");
  const today = localDateKey();
  const cached = await readJsonFile<{ date?: string; text?: string }>(cachePath, {});
  if (cached.date === today && cached.text) return cached.text;
  const searchTool = getConfig
    ? createWebSearchTool({ config: getConfig(), lateBindRuntimeConfig: true })
    : null;
  if (!searchTool) {
    console.warn("[daily-brief] web search is not available yet; skipping the brief");
    return undefined;
  }
  const now = new Date();
  const material: string[] = [];
  for (const [index, topic] of topics.entries()) {
    // The free Gemini tier allows only a few grounded searches a minute.
    if (index > 0) await sleep(BRIEF_SEARCH_GAP_MS);
    try {
      const result = (await withRateLimitRetry(() =>
        searchTool.execute(`daily-brief-${randomUUID()}`, {
          // A news partner wants this month's announcements, not the best-known old pages.
          query: news
            ? `${topic} 最新 ${now.getFullYear()}年${now.getMonth() + 1}月`
            : `${topic} 最新`,
          count: news ? 6 : 4,
        }),
      )) as {
        content?: Array<{ type?: string; text?: string }>;
        details?: unknown;
      };
      const text = (result.content ?? [])
        .filter((c) => c.type === "text")
        .map((c) => c.text ?? "")
        .join("\n");
      const sites = news ? citedSites(result.details) : [];
      // Five topics must fit the CPU worker's 8K context (the fallback summarizer).
      if (text) {
        material.push(
          `## ${topic}\n${text.slice(0, BRIEF_CHARS_PER_TOPIC)}${sites.length > 0 ? `\n出典: ${sites.join(", ")}` : ""}`,
        );
      }
    } catch (err) {
      // A failed topic just drops out of today's brief.
      console.warn(`[daily-brief] search for "${topic}" failed: ${String(err)}`);
    }
  }
  if (material.length === 0) return undefined;
  const system = "あなたはニュースを要約する編集者です。資料に書かれた事実だけを使います。";
  const prompt = news
    ? `今日は${today}です。次の資料から、テーマごとに新しい発表・更新・話題を1〜3件ずつ、次の形式でまとめてください。\n■テーマ名\n- 内容を1行で\n出典: サイト名, サイト名\n前置き・あいさつ・締めの文は書かない。資料にない事実や日付は書かない。古い話題は省く。URLは書かない。合計800字以内。\n\n${material.join("\n\n")}`
    : `次の資料から、テーマごとに今日知っておくとよいことを1〜2行ずつ、日本語の箇条書き（「- 」で始める）でまとめてください。前置きは書かない（合計300字以内）。\n\n${material.join("\n\n")}`;
  const maxTokens = news ? 900 : 400;
  let text = "";
  try {
    // The GPU chat model writes this in well under a minute; the CPU worker takes minutes.
    text = (
      await completeWithMainModel({
        messages: [
          { role: "system", content: system },
          { role: "user", content: prompt },
        ],
        temperature: 0.2,
        maxTokens,
      })
    ).trim();
  } catch (err) {
    console.warn(`[daily-brief] chat model summary failed: ${String(err)}`);
  }
  if (!text) {
    // The GPU is lent to a media job or voice training: summarize on the CPU worker instead.
    try {
      const summary = await executeSpecialistTask({
        taskType: "summarize",
        systemPrompt: system,
        prompt,
        maxTokens,
        temperature: 0.2,
        thinking: false,
      });
      text = summary.text.trim();
    } catch (err) {
      console.warn(`[daily-brief] worker summary failed: ${String(err)}`);
    }
  }
  text = tidyBrief(text);
  if (text) await writeJsonFile(cachePath, { date: today, text });
  return text || undefined;
}

/**
 * Drops the preamble a summarizer adds before the first heading or bullet
 * ("編集者として…まとめます。") and turns markdown headings into the ■ headings the chat shows.
 */
export function tidyBrief(text: string): string {
  const lines = text.split("\n");
  const first = lines.findIndex((line) => /^\s*(?:■|#{1,4}\s|[-・*]\s?)/.test(line));
  return (first > 0 ? lines.slice(first) : lines)
    .join("\n")
    .replace(/^#{1,4}\s*/gm, "■")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

async function composeProactiveMessage(
  partner: PartnerProfile,
  reason: ProactiveReason,
): Promise<string> {
  const brief =
    reason === "morning" ? await loadOrBuildDailyBrief(partner).catch(() => undefined) : undefined;
  const news = brief && partner.settings?.newsDigest === true ? brief : undefined;
  const instruction =
    reason === "birthday"
      ? "今日はユーザーの誕生日です。キャラクターらしく心からお祝いするメッセージを2〜3文で。"
      : reason === "morning"
        ? `朝です。ユーザーに自分から声をかける朝の挨拶を2〜3文で。${news ? `今日のニュース（このあと全文を届ける）から1つ選んで、ひとこと感想を添えて:\n${news}` : brief ? `次の今日のトピックにも自然に触れて:\n${brief}` : ""}`
        : "ユーザーとしばらく話していません。キャラクターらしく、近況をさりげなく尋ねる短いメッセージを1〜2文で。重くならないように。";
  const message = await completeWithMainModel({
    messages: [
      {
        role: "system",
        content: `あなたは「${partner.name}」です。キャラクター設定を守ってください。\n\n${await buildPartnerCharacterPrompt(partner)}`,
      },
      { role: "user", content: `${instruction}\n返答の文章だけを出力。` },
    ],
    temperature: 0.8,
    maxTokens: 300,
  });
  return appendNewsDigest(message, news);
}

async function notifyPhones(title: string, body: string): Promise<void> {
  const registry = nodeRegistry;
  if (!registry) return;
  const targets = registry
    .listConnected()
    .filter(
      (node) =>
        node.commands.includes("system.notify") &&
        (node.platform ?? "").toLowerCase().includes("android"),
    );
  await Promise.all(
    targets.map((node) =>
      registry
        .invoke({
          nodeId: node.nodeId,
          expectedConnId: node.connId,
          command: "system.notify",
          params: { title, body: body.slice(0, 240), priority: "active", delivery: "auto" },
          timeoutMs: 10_000,
          idempotencyKey: randomUUID(),
        })
        .catch(() => ({ ok: false })),
    ),
  );
}

const PC_ALERT_INTERVAL_MS = 60 * 60_000;
let lastPcAlertAt = 0;

/** Warns the phone when the always-on PC overheats or the main LLM stays down (at most hourly). */
async function checkPcHealthAlert(now: Date): Promise<void> {
  const health = await readPcHealth().catch(() => undefined);
  if (!health || (health.level !== "danger" && health.level !== "stopped")) return;
  if (now.getTime() - lastPcAlertAt < PC_ALERT_INTERVAL_MS) return;
  lastPcAlertAt = now.getTime();
  await notifyPhones(
    health.level === "danger" ? "⚠️ PCが危険な状態です" : "⛔ PCのサービスが停止中",
    health.reasons.join(" / "),
  );
}

/** Appends a message to the partner's chat and pushes it to the phone. */
async function deliverToChat(partner: PartnerProfile, message: string): Promise<void> {
  const history = await loadPartnerChatHistory(partner.id);
  history.push({
    id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    role: "assistant",
    content: message,
    timestamp: Date.now(),
  });
  await savePartnerChatHistory(partner.id, history.slice(-100));
  await notifyPhones(partner.name, message);
}

/**
 * A job started from a conversation is finished: the partner chat that asked for it gets the
 * notice (and the phone); a job from the main chat notifies the phone only.
 */
async function announceFinishedJob(job: MediaJob): Promise<void> {
  if (!job.chat) return;
  const { title, text } = finishedJobNotice(job);
  const partnerId = job.chat.partnerId;
  const partner = partnerId
    ? (await listPartnerProfiles()).find((p) => p.id === partnerId)
    : undefined;
  if (partner) {
    await deliverToChat(partner, text);
  } else {
    await notifyPhones(title, text);
  }
}

/**
 * Trains a queued voice (now for "今から学習", else inside the night window) and tells the phones
 * when it is done. Runs detached so check-ins keep their schedule.
 */
export function startVoiceTraining(now = new Date()): void {
  void runVoiceTrainingTick(now, async (project) => {
    await notifyPhones(
      project.status === "ready"
        ? `🎙 声「${project.name}」ができました`
        : `🎙 声「${project.name}」の作成に失敗`,
      project.status === "ready"
        ? "パートナー編集の「声を選ぶ」から設定できます。"
        : (project.error ?? "").slice(0, 200),
    );
  }).catch((err) => console.error("[voice-studio] training tick failed:", err));
}

export async function runPartnerProactiveTick(now = new Date()): Promise<void> {
  if (ticking) return;
  ticking = true;
  try {
    await checkPcHealthAlert(now);
    startVoiceTraining(now);
    const allPartners = await listPartnerProfiles();
    await runDailyMessages({
      now,
      partners: allPartners,
      loadBrief: loadOrBuildDailyBrief,
      deliver: deliverToChat,
    });
    const partners = allPartners.filter((p) => p.settings?.proactive === true);
    for (const partner of partners) {
      const statePath = partnerFile(partner.id, "proactive.json");
      const state = await readJsonFile<ProactiveState>(statePath, {});
      const history = await loadPartnerChatHistory(partner.id);
      const reason = chooseProactiveReason({
        now,
        state,
        lastChatAt: history.at(-1)?.timestamp,
        birthday: birthdayFromFacts(await loadPartnerFacts(partner.id)),
        skipMorning: (await loadDailySettings(partner.id)).greetingEnabled,
      });
      if (!reason) continue;
      const message = (await composeProactiveMessage(partner, reason)).trim();
      if (!message) continue;
      history.push({
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        role: "assistant",
        content: message,
        timestamp: Date.now(),
      });
      await savePartnerChatHistory(partner.id, history.slice(-100));
      await notifyPhones(partner.name, message);
      await writeJsonFile(statePath, {
        ...state,
        lastSentAt: now.getTime(),
        ...(reason === "morning" ? { lastBriefDate: localDateKey(now) } : {}),
        ...(reason === "birthday" ? { lastBirthdayYear: now.getFullYear() } : {}),
      });
    }
  } catch (err) {
    console.error("[partner-proactive] tick failed:", err);
  } finally {
    ticking = false;
  }
}
