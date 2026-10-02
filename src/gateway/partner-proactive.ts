import { randomUUID } from "node:crypto";
import { createWebSearchTool } from "../agents/tools/web-tools.js";
import type { OpenClawConfig } from "../config/types.openclaw.js";
import { loadDailySettings, runDailyMessages } from "./partner-daily.js";
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

/** Researches the partner's daily topics on a worker; cached for the day. */
export async function loadOrBuildDailyBrief(partner: PartnerProfile): Promise<string | undefined> {
  const topics = partner.settings?.dailyTopics ?? [];
  if (topics.length === 0) return undefined;
  const cachePath = partnerFile(partner.id, "daily-brief.json");
  const today = localDateKey();
  const cached = await readJsonFile<{ date?: string; text?: string }>(cachePath, {});
  if (cached.date === today && cached.text) return cached.text;
  const searchTool = getConfig
    ? createWebSearchTool({ config: getConfig(), lateBindRuntimeConfig: true })
    : null;
  const material: string[] = [];
  for (const topic of topics) {
    if (!searchTool) break;
    try {
      const result = (await searchTool.execute(`daily-brief-${randomUUID()}`, {
        query: `${topic} 最新`,
        count: 4,
      })) as {
        content?: Array<{ type?: string; text?: string }>;
      };
      const text = (result.content ?? [])
        .filter((c) => c.type === "text")
        .map((c) => c.text ?? "")
        .join("\n");
      if (text) material.push(`## ${topic}\n${text.slice(0, 2_000)}`);
    } catch {
      // A failed topic just drops out of today's brief.
    }
  }
  if (material.length === 0) return undefined;
  const summary = await executeSpecialistTask({
    taskType: "summarize",
    systemPrompt: "あなたはニュースを要約する編集者です。資料に書かれた事実だけを使います。",
    prompt: `次の資料から、テーマごとに今日知っておくとよいことを1〜2行ずつ、日本語の箇条書きでまとめてください（合計300字以内）。\n\n${material.join("\n\n")}`,
    maxTokens: 400,
    temperature: 0.2,
  });
  const text = summary.text.trim();
  if (text) await writeJsonFile(cachePath, { date: today, text });
  return text || undefined;
}

async function composeProactiveMessage(
  partner: PartnerProfile,
  reason: ProactiveReason,
): Promise<string> {
  const brief =
    reason === "morning" ? await loadOrBuildDailyBrief(partner).catch(() => undefined) : undefined;
  const instruction =
    reason === "birthday"
      ? "今日はユーザーの誕生日です。キャラクターらしく心からお祝いするメッセージを2〜3文で。"
      : reason === "morning"
        ? `朝です。ユーザーに自分から声をかける朝の挨拶を2〜3文で。${brief ? `次の今日のトピックにも自然に触れて:\n${brief}` : ""}`
        : "ユーザーとしばらく話していません。キャラクターらしく、近況をさりげなく尋ねる短いメッセージを1〜2文で。重くならないように。";
  return await completeWithMainModel({
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

export async function runPartnerProactiveTick(now = new Date()): Promise<void> {
  if (ticking) return;
  ticking = true;
  try {
    await checkPcHealthAlert(now);
    // Night-window voice training; runs detached so check-ins keep their schedule.
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
