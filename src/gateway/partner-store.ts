import { createHash } from "node:crypto";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";

// Partner ids are server-generated slugs; anything else (e.g. "..", path separators)
// would let a client read or write files outside ~/.openclaw/partners.
const PARTNER_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;

export function isValidPartnerId(partnerId: unknown): partnerId is string {
  return typeof partnerId === "string" && PARTNER_ID_PATTERN.test(partnerId);
}

export function resolveOpenClawHome(): string {
  return process.env.OPENCLAW_HOME || path.join(os.homedir(), ".openclaw");
}

export function getPartnerDir(partnerId: string): string {
  if (!isValidPartnerId(partnerId)) {
    throw new Error(`Invalid partner id: ${JSON.stringify(partnerId).slice(0, 80)}`);
  }
  return path.join(resolveOpenClawHome(), "partners", partnerId);
}

export async function readJsonFile<T>(filePath: string, fallback: T): Promise<T> {
  try {
    return JSON.parse(await fs.readFile(filePath, "utf-8")) as T;
  } catch {
    return fallback;
  }
}

export async function writeJsonFile(filePath: string, value: unknown): Promise<void> {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  const tmp = `${filePath}.${process.pid}.tmp`;
  await fs.writeFile(tmp, JSON.stringify(value, null, 2), "utf-8");
  await fs.rename(tmp, filePath);
}

export function partnerFile(partnerId: string, name: string): string {
  return path.join(getPartnerDir(partnerId), name);
}

/** Local calendar date (YYYY-MM-DD) in the Gateway host's timezone. */
export function localDateKey(date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

// ---------- Facts (structured long-term memory) ----------

export type PartnerFact = { fact: string; category: string; date: string };

export async function loadPartnerFacts(partnerId: string): Promise<PartnerFact[]> {
  const facts = await readJsonFile<unknown>(partnerFile(partnerId, "facts.json"), []);
  if (!Array.isArray(facts)) {
    return [];
  }
  return facts
    .filter(
      (item): item is PartnerFact => typeof item?.fact === "string" && item.fact.trim().length > 0,
    )
    .map((item) => ({
      fact: item.fact.trim(),
      category: item.category || "general",
      date: item.date || "",
    }));
}

export async function savePartnerFacts(partnerId: string, facts: PartnerFact[]): Promise<void> {
  await writeJsonFile(partnerFile(partnerId, "facts.json"), facts.slice(-300));
}

// ---------- Bond (relationship level) ----------

export type PartnerBadge = { title: string; desc: string; icon: string; unlocked: boolean };

export type PartnerBond = {
  exp: number;
  level: number;
  title: string;
  nextExp: number;
  totalMessages: number;
  streakDays: number;
  lastActiveDate?: string;
  badges: PartnerBadge[];
};

const BOND_TITLES: Array<[number, string]> = [
  [1, "初対面のふたり"],
  [3, "仲良しの友達"],
  [5, "気心の知れた相棒"],
  [8, "かけがえのない親友"],
  [10, "唯一無二のパートナー"],
];

export function bondTitleForLevel(level: number): string {
  let title = BOND_TITLES[0]?.[1] ?? "";
  for (const [min, name] of BOND_TITLES) {
    if (level >= min) {
      title = name;
    }
  }
  return title;
}

/** Exp needed to go from `level` to `level + 1`; grows so later levels take longer. */
export function bondExpForNextLevel(level: number): number {
  return 100 + (level - 1) * 50;
}

function computeBadges(
  bond: Pick<PartnerBond, "totalMessages" | "streakDays" | "level">,
): PartnerBadge[] {
  const defs: Array<[string, string, string, boolean]> = [
    ["はじめまして", "最初の会話をした", "👋", bond.totalMessages >= 1],
    ["おしゃべり好き", "100回会話した", "💬", bond.totalMessages >= 100],
    ["語り尽くせない", "500回会話した", "📚", bond.totalMessages >= 500],
    ["3日連続", "3日続けて話した", "🔥", bond.streakDays >= 3],
    ["1週間連続", "7日続けて話した", "🌟", bond.streakDays >= 7],
    ["相棒", "絆レベル5に到達", "🤝", bond.level >= 5],
    ["親友", "絆レベル10に到達", "💖", bond.level >= 10],
  ];
  return defs.map(([title, desc, icon, unlocked]) => ({ title, desc, icon, unlocked }));
}

function bondsFilePath(): string {
  return path.join(resolveOpenClawHome(), "partner_bonds.json");
}

function normalizeBond(raw: Partial<PartnerBond> | undefined): PartnerBond {
  const level = Math.max(1, Math.floor(raw?.level ?? 1));
  const base = {
    exp: Math.max(0, Math.floor(raw?.exp ?? 0)),
    level,
    totalMessages: Math.max(0, Math.floor(raw?.totalMessages ?? 0)),
    streakDays: Math.max(0, Math.floor(raw?.streakDays ?? 0)),
    lastActiveDate: raw?.lastActiveDate,
  };
  return {
    ...base,
    title: bondTitleForLevel(level),
    nextExp: bondExpForNextLevel(level),
    badges: computeBadges(base),
  };
}

export async function loadPartnerBond(partnerId: string): Promise<PartnerBond> {
  const all = await readJsonFile<Record<string, Partial<PartnerBond>>>(bondsFilePath(), {});
  return normalizeBond(all[partnerId]);
}

/** Adds exp for one exchange; longer messages earn a little more. Returns the new bond and whether it leveled up. */
export async function addPartnerBondExp(
  partnerId: string,
  charsAdded: number,
  now = new Date(),
): Promise<{ bond: PartnerBond; leveledUp: boolean }> {
  const all = await readJsonFile<Record<string, Partial<PartnerBond>>>(bondsFilePath(), {});
  const bond = normalizeBond(all[partnerId]);
  const today = localDateKey(now);
  if (bond.lastActiveDate !== today) {
    const yesterday = localDateKey(new Date(now.getTime() - 24 * 60 * 60 * 1000));
    bond.streakDays = bond.lastActiveDate === yesterday ? bond.streakDays + 1 : 1;
    bond.lastActiveDate = today;
  }
  bond.totalMessages += 1;
  bond.exp += Math.min(30, 5 + Math.floor(Math.max(0, charsAdded) / 20));
  const startLevel = bond.level;
  while (bond.exp >= bondExpForNextLevel(bond.level)) {
    bond.exp -= bondExpForNextLevel(bond.level);
    bond.level += 1;
  }
  const updated = normalizeBond(bond);
  all[partnerId] = {
    exp: updated.exp,
    level: updated.level,
    totalMessages: updated.totalMessages,
    streakDays: updated.streakDays,
    lastActiveDate: updated.lastActiveDate,
  };
  await writeJsonFile(bondsFilePath(), all);
  return { bond: updated, leveledUp: updated.level > startLevel };
}

/** How the relationship stage should colour the partner's tone. */
export function bondRelationshipDirective(bond: PartnerBond): string {
  if (bond.level >= 10) {
    return "ユーザーとは唯一無二のパートナー。遠慮のない本音や深い信頼、思い出話を自然に交える。";
  }
  if (bond.level >= 5) {
    return "ユーザーとは気心の知れた相棒。冗談や軽口も交え、打ち解けた距離感で話す。";
  }
  if (bond.level >= 3) {
    return "ユーザーとは仲良しの友達。親しみを込めつつ、まだ少し照れや遠慮が残る。";
  }
  return "ユーザーとはまだ知り合ったばかり。キャラクターらしさを保ちつつ、少し距離を置いて丁寧に接する。";
}

// ---------- Mood log ----------

export type PartnerMoodEntry = {
  date: string;
  mood: string;
  score: number;
  comment: string;
  ts: number;
};

export async function loadPartnerMoods(partnerId: string): Promise<PartnerMoodEntry[]> {
  const entries = await readJsonFile<unknown>(partnerFile(partnerId, "mood.json"), []);
  return Array.isArray(entries)
    ? entries.filter(
        (e): e is PartnerMoodEntry => typeof e?.mood === "string" && typeof e?.score === "number",
      )
    : [];
}

export async function appendPartnerMood(partnerId: string, entry: PartnerMoodEntry): Promise<void> {
  const entries = await loadPartnerMoods(partnerId);
  entries.push(entry);
  await writeJsonFile(partnerFile(partnerId, "mood.json"), entries.slice(-500));
}

// ---------- Group chat history ----------

export type GroupChatHistoryItem = {
  sender: string;
  content: string;
  isUser: boolean;
  timestamp: number;
};

function groupChatFile(partnerIds: string[]): string {
  const ids = [...new Set(partnerIds.filter(isValidPartnerId))].sort();
  if (ids.length === 0) {
    throw new Error("partnerIds must contain at least one valid partner id");
  }
  // The same members share one history regardless of selection order.
  const key = createHash("sha256").update(ids.join(",")).digest("hex").slice(0, 24);
  return path.join(resolveOpenClawHome(), "groupchats", `${key}.json`);
}

export async function loadGroupChatHistory(partnerIds: string[]): Promise<GroupChatHistoryItem[]> {
  const items = await readJsonFile<unknown>(groupChatFile(partnerIds), []);
  return Array.isArray(items)
    ? items.filter(
        (i): i is GroupChatHistoryItem =>
          typeof i?.content === "string" && typeof i?.sender === "string",
      )
    : [];
}

export async function saveGroupChatHistory(
  partnerIds: string[],
  items: GroupChatHistoryItem[],
): Promise<void> {
  await writeJsonFile(groupChatFile(partnerIds), items.slice(-200));
}

export async function clearGroupChatHistory(partnerIds: string[]): Promise<void> {
  await fs.rm(groupChatFile(partnerIds), { force: true });
}
