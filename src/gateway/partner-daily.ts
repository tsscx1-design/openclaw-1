import { localDateKey, partnerFile, readJsonFile, writeJsonFile } from "./partner-store.js";
import {
  buildPartnerCharacterPrompt,
  completeWithMainModel,
  type PartnerProfile,
} from "./server-methods/partners.js";

// Per-partner daily messages: a morning greeting (optionally with the day's fortune) and a night
// "otsukare" message at the times chosen in the app's 朝の挨拶 & 占い screen.

export type DailySettings = {
  greetingEnabled: boolean;
  greetingTime: string;
  nightEnabled: boolean;
  nightTime: string;
  fortuneWithGreeting: boolean;
};

export type DailyKind = "morning" | "night";
type DailyState = { morningDate?: string; nightDate?: string };

export const DAILY_DEFAULTS: DailySettings = {
  // Off until the user opts in, so nobody gets surprise notifications.
  greetingEnabled: false,
  greetingTime: "07:30",
  nightEnabled: false,
  nightTime: "22:00",
  fortuneWithGreeting: true,
};

const TIME_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)$/;
/** A missed slot (gateway down, PC asleep) is only made up within this window. */
const CATCH_UP_WINDOW_MS = 3 * 60 * 60_000;

function validTime(value: unknown, fallback: string): string {
  return typeof value === "string" && TIME_PATTERN.test(value) ? value : fallback;
}

export function sanitizeDailySettings(raw: unknown): DailySettings {
  const input = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const flag = (key: keyof DailySettings, fallback: boolean) =>
    typeof input[key] === "boolean" ? (input[key] as boolean) : fallback;
  return {
    greetingEnabled: flag("greetingEnabled", DAILY_DEFAULTS.greetingEnabled),
    greetingTime: validTime(input.greetingTime, DAILY_DEFAULTS.greetingTime),
    nightEnabled: flag("nightEnabled", DAILY_DEFAULTS.nightEnabled),
    nightTime: validTime(input.nightTime, DAILY_DEFAULTS.nightTime),
    fortuneWithGreeting: flag("fortuneWithGreeting", DAILY_DEFAULTS.fortuneWithGreeting),
  };
}

export async function loadDailySettings(partnerId: string): Promise<DailySettings> {
  return sanitizeDailySettings(
    await readJsonFile(partnerFile(partnerId, "daily-settings.json"), {}),
  );
}

export async function saveDailySettings(partnerId: string, raw: unknown): Promise<DailySettings> {
  const settings = sanitizeDailySettings(raw);
  await writeJsonFile(partnerFile(partnerId, "daily-settings.json"), settings);
  return settings;
}

function minutesOfDay(time: string): number {
  const [h = "0", m = "0"] = time.split(":");
  return Number(h) * 60 + Number(m);
}

/** Which daily messages are due right now. Pure so the schedule is testable. */
export function dueDailyKinds(params: {
  settings: DailySettings;
  state: DailyState;
  now: Date;
}): DailyKind[] {
  const { settings, state, now } = params;
  const today = localDateKey(now);
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  const due: DailyKind[] = [];
  const slots: Array<[DailyKind, boolean, string, string | undefined]> = [
    ["morning", settings.greetingEnabled, settings.greetingTime, state.morningDate],
    ["night", settings.nightEnabled, settings.nightTime, state.nightDate],
  ];
  for (const [kind, enabled, time, lastDate] of slots) {
    if (!enabled || lastDate === today) continue;
    const lateByMs = (nowMinutes - minutesOfDay(time)) * 60_000;
    if (lateByMs >= 0 && lateByMs <= CATCH_UP_WINDOW_MS) due.push(kind);
  }
  return due;
}

/** Stable per-day pseudo random so the fortune does not change on every refresh. */
export function seededPick<T>(items: readonly T[], seed: string): T {
  let hash = 2166136261;
  for (const ch of seed) {
    hash = Math.imul(hash ^ ch.charCodeAt(0), 16777619);
  }
  return items[Math.abs(hash) % items.length] as T;
}

export function weekdayJa(date = new Date()): string {
  return ["日", "月", "火", "水", "木", "金", "土"][date.getDay()] ?? "";
}

/** One in-character completion on the main model. */
export async function speakAs(
  partner: PartnerProfile,
  instruction: string,
  options?: { maxTokens?: number; temperature?: number },
): Promise<string> {
  const system = `あなたは「${partner.name}」です。以下のキャラクター設定を守り、その口調で書いてください。\n\n${await buildPartnerCharacterPrompt(partner)}`;
  return await completeWithMainModel({
    messages: [
      { role: "system", content: system },
      { role: "user", content: instruction },
    ],
    temperature: options?.temperature ?? 0.8,
    maxTokens: options?.maxTokens ?? 600,
  });
}

export type Fortune = { luck: string; item: string; color: string; message: string };

const FORTUNE_ITEMS = [
  "ハンカチ",
  "温かい飲み物",
  "お気に入りのペン",
  "イヤホン",
  "手帳",
  "飴玉",
  "腕時計",
  "観葉植物",
] as const;
const FORTUNE_COLORS = [
  "赤",
  "青",
  "緑",
  "黄色",
  "白",
  "紫",
  "オレンジ",
  "ピンク",
  "水色",
] as const;

/** Today's fortune for the partner; generated once per day and cached. */
export async function getTodaysFortune(partner: PartnerProfile): Promise<Fortune> {
  const today = localDateKey();
  const cachePath = partnerFile(partner.id, "fortune.json");
  const cached = await readJsonFile<{ date?: string; fortune?: Fortune }>(cachePath, {});
  if (cached.date === today && cached.fortune) return cached.fortune;
  const seed = `${partner.id}:${today}`;
  const luck = seededPick(["大吉", "中吉", "小吉", "吉", "末吉", "凶"] as const, `${seed}:luck`);
  const item = seededPick(FORTUNE_ITEMS, `${seed}:item`);
  const color = seededPick(FORTUNE_COLORS, `${seed}:color`);
  const message = await speakAs(
    partner,
    `今日のユーザーの運勢は「${luck}」、ラッキーアイテムは「${item}」、ラッキーカラーは「${color}」です。これを伝える占いのメッセージを2文で書いてください。凶でも前向きな助言で締めること。文章だけを出力。`,
    { maxTokens: 160 },
  );
  const fortune = { luck, item, color, message };
  await writeJsonFile(cachePath, { date: today, fortune });
  return fortune;
}

async function composeDailyMessage(
  partner: PartnerProfile,
  kind: DailyKind,
  settings: DailySettings,
  loadBrief: (partner: PartnerProfile) => Promise<string | undefined>,
): Promise<string> {
  const now = new Date();
  if (kind === "night") {
    return await speakAs(
      partner,
      `夜です。今日一日を頑張ったユーザーへ、${partner.name}らしく労いと「おやすみ」の気持ちを込めたメッセージを2〜3文で書いてください。返答の文章だけを出力。`,
      { maxTokens: 250 },
    );
  }
  const brief = await loadBrief(partner).catch(() => undefined);
  const fortune = settings.fortuneWithGreeting
    ? await getTodaysFortune(partner).catch(() => undefined)
    : undefined;
  const greeting = await speakAs(
    partner,
    `今は${localDateKey(now)}（${weekdayJa(now)}曜日）の朝です。ユーザーへの朝の挨拶と、今日を前向きに過ごせる一言を2〜3文で書いてください。返答の文章だけを出力。${brief ? `\n今日のトピック（1つ自然に紹介する）:\n${brief}` : ""}`,
    { maxTokens: 250 },
  );
  return fortune
    ? `${greeting.trim()}\n\n🔮 今日の運勢: ${fortune.luck}（ラッキーアイテム: ${fortune.item} / ラッキーカラー: ${fortune.color}）\n${fortune.message.trim()}`
    : greeting.trim();
}

/**
 * Sends every daily message that is due for the given partners. `deliver` appends it to the
 * partner's chat and notifies the phone; the sent date is recorded only after delivery succeeds.
 */
export async function runDailyMessages(params: {
  now: Date;
  partners: PartnerProfile[];
  loadBrief: (partner: PartnerProfile) => Promise<string | undefined>;
  deliver: (partner: PartnerProfile, message: string) => Promise<void>;
}): Promise<void> {
  const today = localDateKey(params.now);
  for (const partner of params.partners) {
    const settings = await loadDailySettings(partner.id);
    const statePath = partnerFile(partner.id, "daily-state.json");
    const state = await readJsonFile<DailyState>(statePath, {});
    for (const kind of dueDailyKinds({ settings, state, now: params.now })) {
      try {
        const message = (
          await composeDailyMessage(partner, kind, settings, params.loadBrief)
        ).trim();
        if (!message) continue;
        await params.deliver(partner, message);
        Object.assign(state, kind === "morning" ? { morningDate: today } : { nightDate: today });
        await writeJsonFile(statePath, state);
      } catch (err) {
        console.error(`[partner-daily] ${kind} message for ${partner.name} failed:`, err);
      }
    }
  }
}
