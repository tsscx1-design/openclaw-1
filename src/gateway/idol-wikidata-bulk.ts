import {
  ageFromBirthday,
  idolRoster,
  serializeRosterMember,
  upsertRosterLines,
  type IdolMember,
} from "./idol-master.js";
import { readWikidataPerson, searchWikidata, type WikidataPerson } from "./idol-wikidata.js";

// "Fill the roster from Wikidata" in two steps. Step 1 (check) looks each roster person up on Wikidata and keeps
// what it would fill, writing nothing. Step 2 (apply) writes exactly those lines, after one backup. A person is taken
// only when the name fits AND Wikidata's own record puts the person in the roster's group, and only empty fields are
// filled (what is already in the roster is never replaced).

/** Names a roster group goes by on Wikidata (lower-cased substrings of a group label or a description). */
const GROUP_ALIASES: Record<string, string[]> = {
  AKB48: ["akb48"],
  SKE48: ["ske48"],
  NMB48: ["nmb48"],
  HKT48: ["hkt48"],
  NGT48: ["ngt48"],
  STU48: ["stu48"],
  Nogizaka46: ["乃木坂46", "nogizaka46"],
  Sakurazaka46: ["櫻坂46", "sakurazaka46", "欅坂46", "keyakizaka46"],
  Hinatazaka46: ["日向坂46", "hinatazaka46", "けやき坂46", "hiragana keyakizaka46"],
  Bokuao: ["僕が見たかった青空", "bokuao", "boku ga mitakatta aozora"],
};

export function groupAliases(group: string): string[] {
  return GROUP_ALIASES[group] ?? [group.toLowerCase()];
}

const squash = (value: string): string => value.replace(/[\s　]+/g, "").toLowerCase();

/** True when the text names the group (by any of its Wikidata names). */
export function namesGroup(text: string, group: string): boolean {
  const hay = text.toLowerCase();
  return groupAliases(group).some((alias) => hay.includes(alias));
}

/** The person belongs to the group according to Wikidata's own record (membership, else the description). */
export function personInGroup(person: WikidataPerson, group: string): boolean {
  return (
    person.groups.some((label) => namesGroup(label, group)) || namesGroup(person.description, group)
  );
}

/**
 * The member with the empty fields filled from Wikidata. Nothing that is already set is replaced. `filled` names the
 * fields that were empty and are now set; with nothing filled the member comes back unchanged.
 */
export function mergeWikidataIntoMember(
  member: IdolMember,
  person: WikidataPerson,
): { member: IdolMember; filled: string[] } {
  const merged: IdolMember = { ...member };
  const filled: string[] = [];
  const take = <K extends keyof IdolMember>(
    key: K,
    value: IdolMember[K] | undefined,
    label: string,
  ) => {
    if (merged[key] === undefined && value !== undefined) {
      merged[key] = value;
      filled.push(label);
    }
  };
  take("birthday", person.birthday, "誕生日");
  take("bloodType", person.bloodType, "血液型");
  take("hometown", person.hometown, "出身地");
  take("heightCm", person.heightCm, "身長");
  take("xUrl", person.xUrl, "X");
  take("photoUrl", person.photoUrl, "写真");
  if (filled.length === 0) return { member, filled };
  if (merged.age === undefined) {
    const age = ageFromBirthday(merged.birthday);
    if (age !== undefined) merged.age = age;
  }
  const tag = `Wikidata ${person.qid}`;
  merged.source = !merged.source
    ? tag
    : merged.source.includes(person.qid)
      ? merged.source
      : `${merged.source} / ${tag}`;
  return { member: merged, filled };
}

export type BulkLookup = (member: IdolMember) => Promise<WikidataPerson | undefined>;

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Looks one roster person up on Wikidata: candidates by name, and a candidate is read only when its description names the
 * group, or its label is the person's name; the person is then kept only if Wikidata puts them in the group.
 */
export function createWikidataLookup(delayMs = 0): BulkLookup {
  return async (member) => {
    const candidates = await searchWikidata(member.name, 4);
    let reads = 0;
    for (const candidate of candidates) {
      const sameName = squash(candidate.label) === squash(member.name);
      if (!namesGroup(candidate.description, member.group) && !sameName) continue;
      if (reads >= 2) break;
      reads += 1;
      await sleep(delayMs);
      const person = await readWikidataPerson(candidate.qid);
      if (
        personInGroup(person, member.group) &&
        (sameName ||
          squash(person.name) === squash(member.name) ||
          namesGroup(candidate.description, member.group))
      )
        return person;
    }
    return undefined;
  };
}

export type BulkState = {
  status: "idle" | "running" | "done" | "cancelled" | "error";
  group?: string;
  total: number;
  done: number;
  /** People found on Wikidata (in their group). */
  found: number;
  /** Found people that have something new to fill. */
  withNew: number;
  fieldCounts: Record<string, number>;
  unmatched: string[];
  errors: number;
  error?: string;
  startedAt?: number;
  finishedAt?: number;
  applied?: { added: number; updated: number; backup?: string };
};

type Match = { key: string; name: string; qid: string; filled: string[]; line: string };

const IDLE: BulkState = {
  status: "idle",
  total: 0,
  done: 0,
  found: 0,
  withNew: 0,
  fieldCounts: {},
  unmatched: [],
  errors: 0,
};
let state: BulkState = { ...IDLE };
let matches: Match[] = [];
let cancelRequested = false;

export function bulkState(): BulkState {
  return { ...state, unmatched: state.unmatched.slice(0, 400) };
}

export function cancelBulk(): BulkState {
  if (state.status === "running") cancelRequested = true;
  return bulkState();
}

/** Starts step 1 in the background (nothing is written). Returns at once; poll bulkState(). */
export function startBulkCheck(options: { group?: string; lookup?: BulkLookup } = {}): BulkState {
  if (state.status === "running") throw new Error("すでに、調べている最中です");
  const lookup = options.lookup ?? createWikidataLookup();
  const people = idolRoster().members.filter(
    (member) => !options.group || member.group === options.group,
  );
  if (people.length === 0) throw new Error("名簿に、対象の人がいません");
  matches = [];
  cancelRequested = false;
  state = {
    ...IDLE,
    status: "running",
    ...(options.group ? { group: options.group } : {}),
    total: people.length,
    startedAt: Date.now(),
  };
  void (async () => {
    let consecutiveErrors = 0;
    for (const member of people) {
      if (cancelRequested) {
        state.status = "cancelled";
        state.finishedAt = Date.now();
        return;
      }
      try {
        const person = await lookup(member);
        consecutiveErrors = 0;
        if (!person) state.unmatched.push(member.name);
        else {
          state.found += 1;
          const { member: merged, filled } = mergeWikidataIntoMember(member, person);
          if (filled.length > 0) {
            state.withNew += 1;
            for (const label of filled)
              state.fieldCounts[label] = (state.fieldCounts[label] ?? 0) + 1;
            matches.push({
              key: `${member.group}\n${member.name}`,
              name: member.name,
              qid: person.qid,
              filled,
              line: serializeRosterMember(merged),
            });
          }
        }
      } catch (error) {
        state.errors += 1;
        consecutiveErrors += 1;
        if (consecutiveErrors >= 5) {
          state.status = "error";
          state.error = `Wikidata から続けて読めませんでした：${error instanceof Error ? error.message : String(error)}`;
          state.finishedAt = Date.now();
          return;
        }
      }
      state.done += 1;
    }
    state.status = "done";
    state.finishedAt = Date.now();
  })();
  return bulkState();
}

/** Step 2: writes the lines kept by step 1 (one backup first). Only after a finished check. */
export async function applyBulk(): Promise<{ added: number; updated: number; backup?: string }> {
  if (state.status !== "done" && state.status !== "cancelled")
    throw new Error("先に「調べる」を、最後まで行ってください");
  if (state.applied)
    throw new Error("この結果は、すでに反映しました。もう一度「調べる」から行ってください");
  if (matches.length === 0) throw new Error("反映する項目が、ありません");
  const result = await upsertRosterLines(matches.map((match) => match.line));
  state = { ...state, applied: result };
  matches = [];
  return result;
}

/** For tests: forget everything. */
export function resetBulk(): void {
  state = { ...IDLE };
  matches = [];
  cancelRequested = false;
}
