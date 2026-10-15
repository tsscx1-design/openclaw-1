import fs from "node:fs/promises";
import path from "node:path";
import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import {
  ageFromBirthday,
  idolPhotoDir,
  idolPhotoIndex,
  idolPhotoFileOf,
  idolRoster,
  looksLikeIdolImage,
  serializeRosterMember,
  upsertRosterLines,
  type IdolMember,
} from "./idol-master.js";

// "Fill the roster from the group's official site" in two steps, like the Wikidata fill: step 1 (check) reads the
// member list and each member's profile page and keeps what it would fill, writing nothing; step 2 (apply) writes
// exactly those lines (after one backup) and saves the photos of people who have none. Only empty fields are filled.
// The pages are public and robots.txt allows them; they are read one at a time with a pause between requests.
// The profile texts and photos belong to the agency: they are kept on this PC for personal use only.
// Only AKB48 for now (the other groups' sites are built differently).

const OFFICIAL = {
  AKB48: {
    listUrl: "https://www.akb48.co.jp/about/members/",
    base: "https://www.akb48.co.jp",
    source: "公式サイト akb48.co.jp",
  },
} as const;
type OfficialGroup = keyof typeof OFFICIAL;
const PHOTO_HOSTS = new Set(["d2r1lkk9i7row.cloudfront.net", "www.akb48.co.jp", "s.akb48.co.jp"]);
const PAUSE_MS = 1_200;
const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
const MAX_PAGE_BYTES = 2 * 1024 * 1024;
const USER_AGENT = "OpenClaw-idol-master/1.0 (personal use; one page at a time)";

export function officialGroups(): string[] {
  return Object.keys(OFFICIAL);
}

export type OfficialPerson = {
  mid: string;
  name: string;
  english?: string;
  /** "1994-10-04" */
  birthday?: string;
  /** "13期" */
  generation?: string;
  nickname?: string;
  hometown?: string;
  agency?: string;
  photoUrl?: string;
};

const squash = (value: string): string => value.replace(/[\s　]+/g, "").toLowerCase();
const decode = (value: string): string =>
  value
    .replace(/&#x([0-9a-f]+);/gi, (_m, h: string) => String.fromCodePoint(Number.parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_m, d: string) => String.fromCodePoint(Number(d)))
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&times;/g, "×")
    .replace(/&nbsp;/g, " ");
const textOf = (html: string): string =>
  decode(html.replace(/<[^>]+>/g, " "))
    .replace(/\s+/g, " ")
    .trim();

function isoDate(text: string): string | undefined {
  const m = /(\d{4})\D+(\d{1,2})\D+(\d{1,2})/.exec(text);
  if (!m) return undefined;
  const iso = `${m[1]}-${m[2]!.padStart(2, "0")}-${m[3]!.padStart(2, "0")}`;
  return ageFromBirthday(iso) === undefined ? undefined : iso;
}

/** The member list page: one card per member (name, English name, birthday, intake, photo, profile id). */
export function parseOfficialList(html: string, base = OFFICIAL.AKB48.base): OfficialPerson[] {
  const people: OfficialPerson[] = [];
  for (const card of html.split(/<li class="memberList/).slice(1)) {
    const block = card.split("</li>")[0] ?? "";
    const mid = /detail\?mid=(\d+)/.exec(block)?.[1];
    const name = textOf(/<p class="name[^"]*">([\s\S]*?)<\/p>/.exec(block)?.[1] ?? "");
    if (!mid || !name) continue;
    const img = /<img[^>]+src="([^"]+)"/.exec(block)?.[1];
    const generation = /(\d+)期生/.exec(textOf(block))?.[1];
    const english = textOf(/<p class="enName[^"]*">([\s\S]*?)<\/p>/.exec(block)?.[1] ?? "");
    const birthday = isoDate(
      textOf(/<figcaption[^>]*>([\s\S]*?)<\/figcaption>/.exec(block)?.[1] ?? ""),
    );
    people.push({
      mid,
      name: name.replace(/[\s　]+/g, ""),
      ...(english ? { english } : {}),
      ...(birthday ? { birthday } : {}),
      ...(generation ? { generation: `${generation}期` } : {}),
      ...(img ? { photoUrl: new URL(decode(img), base).href } : {}),
    });
  }
  return people;
}

/** One profile page: the term/value pairs (事務所, ニックネーム, 生年月日, 出身地 ...). */
export function parseOfficialProfile(html: string): Partial<OfficialPerson> {
  const pairs = new Map<string, string>();
  for (const m of html.matchAll(/<dt[^>]*>([\s\S]*?)<\/dt>\s*<dd[^>]*>([\s\S]*?)<\/dd>/g)) {
    pairs.set(textOf(m[1] ?? ""), textOf(m[2] ?? ""));
  }
  const birthday = isoDate(pairs.get("生年月日") ?? "");
  return {
    ...(pairs.get("所属事務所") ? { agency: pairs.get("所属事務所")! } : {}),
    ...(pairs.get("ニックネーム") ? { nickname: pairs.get("ニックネーム")! } : {}),
    ...(birthday ? { birthday } : {}),
    ...(pairs.get("出身地") ? { hometown: pairs.get("出身地")! } : {}),
  };
}

/**
 * The member with the empty fields filled from the official site. Nothing already set is replaced.
 * `filled` names the fields that were empty and are now set.
 */
export function mergeOfficialIntoMember(
  member: IdolMember,
  person: OfficialPerson,
  source: string,
): { member: IdolMember; filled: string[] } {
  const merged: IdolMember = { ...member };
  const filled: string[] = [];
  const take = <K extends keyof IdolMember>(
    key: K,
    value: IdolMember[K] | undefined,
    label: string,
  ) => {
    if (merged[key] === undefined && value !== undefined && value !== "") {
      merged[key] = value;
      filled.push(label);
    }
  };
  take("birthday", person.birthday, "誕生日");
  take("generation", person.generation, "期");
  take("nickname", person.nickname, "愛称");
  take("hometown", person.hometown, "出身地");
  take("agency", person.agency, "事務所");
  if (filled.length === 0) return { member, filled };
  if (merged.age === undefined) {
    const age = ageFromBirthday(merged.birthday);
    if (age !== undefined) merged.age = age;
  }
  merged.source = !merged.source
    ? source
    : merged.source.includes(source)
      ? merged.source
      : `${merged.source} / ${source}`;
  return { member: merged, filled };
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function fetchText(url: string): Promise<string> {
  const { response, release } = await fetchWithSsrFGuard({
    url,
    requireHttps: true,
    maxRedirects: 2,
    timeoutMs: 30_000,
    init: { headers: { "User-Agent": USER_AGENT, Accept: "text/html" } },
  });
  try {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return (
      await readResponseWithLimit(response, MAX_PAGE_BYTES, {
        timeoutMs: 30_000,
        chunkTimeoutMs: 10_000,
      })
    ).toString("utf-8");
  } finally {
    await release();
  }
}

export type OfficialFetcher = {
  list: (group: OfficialGroup) => Promise<OfficialPerson[]>;
  profile: (group: OfficialGroup, mid: string) => Promise<Partial<OfficialPerson>>;
};

export function createOfficialFetcher(delayMs = PAUSE_MS): OfficialFetcher {
  return {
    list: async (group) =>
      parseOfficialList(await fetchText(OFFICIAL[group].listUrl), OFFICIAL[group].base),
    profile: async (group, mid) => {
      await sleep(delayMs);
      return parseOfficialProfile(
        await fetchText(
          `${OFFICIAL[group].base}/about/members/detail?mid=${encodeURIComponent(mid)}`,
        ),
      );
    },
  };
}

export type OfficialState = {
  status: "idle" | "running" | "done" | "cancelled" | "error";
  group?: string;
  total: number;
  done: number;
  /** Roster people found on the official list. */
  found: number;
  /** Found people that have something new to fill (or no photo yet). */
  withNew: number;
  fieldCounts: Record<string, number>;
  /** People who still have no photo file and whose official photo can be saved. */
  photos: number;
  unmatched: string[];
  errors: number;
  error?: string;
  startedAt?: number;
  finishedAt?: number;
  applied?: { added: number; updated: number; photosSaved: number; backup?: string };
};

type Match = { name: string; group: string; filled: string[]; line?: string; photoUrl?: string };

const IDLE: OfficialState = {
  status: "idle",
  total: 0,
  done: 0,
  found: 0,
  withNew: 0,
  fieldCounts: {},
  photos: 0,
  unmatched: [],
  errors: 0,
};
let state: OfficialState = { ...IDLE };
let matches: Match[] = [];
let cancelRequested = false;

export function officialState(): OfficialState {
  return { ...state, unmatched: state.unmatched.slice(0, 400) };
}

export function cancelOfficial(): OfficialState {
  if (state.status === "running") cancelRequested = true;
  return officialState();
}

/** Step 1 in the background (nothing is written). Returns at once; poll officialState(). */
export function startOfficialCheck(options: {
  group: string;
  fetcher?: OfficialFetcher;
}): OfficialState {
  if (state.status === "running") throw new Error("すでに、調べている最中です");
  if (!(options.group in OFFICIAL))
    throw new Error(
      `「${options.group}」は、まだ公式サイトからの取り込みに対応していません（対応：${officialGroups().join("、")}）`,
    );
  const group = options.group as OfficialGroup;
  const fetcher = options.fetcher ?? createOfficialFetcher();
  const people = idolRoster().members.filter((member) => member.group === group);
  if (people.length === 0) throw new Error("名簿に、そのグループの人がいません");
  matches = [];
  cancelRequested = false;
  state = { ...IDLE, status: "running", group, total: people.length, startedAt: Date.now() };
  void (async () => {
    try {
      const official = await fetcher.list(group);
      if (official.length === 0)
        throw new Error(
          "公式サイトの一覧を読み取れませんでした（ページの作りが変わったかもしれません）",
        );
      const byName = new Map(official.map((person) => [squash(person.name), person]));
      const photoIndex = await idolPhotoIndex();
      let consecutiveErrors = 0;
      for (const member of people) {
        if (cancelRequested) {
          state.status = "cancelled";
          state.finishedAt = Date.now();
          return;
        }
        const person = byName.get(squash(member.name));
        if (!person) {
          state.unmatched.push(member.name);
          state.done += 1;
          continue;
        }
        state.found += 1;
        try {
          // The list already has birthday and intake; the profile page adds nickname, hometown and agency.
          const detail = await fetcher.profile(group, person.mid);
          consecutiveErrors = 0;
          const merged = mergeOfficialIntoMember(
            member,
            { ...person, ...detail },
            OFFICIAL[group].source,
          );
          const hasPhoto =
            idolPhotoFileOf(photoIndex, member) !== undefined || Boolean(member.photoUrl);
          const wantsPhoto = !hasPhoto && Boolean(person.photoUrl);
          if (merged.filled.length > 0 || wantsPhoto) {
            state.withNew += 1;
            for (const label of merged.filled)
              state.fieldCounts[label] = (state.fieldCounts[label] ?? 0) + 1;
            if (wantsPhoto) state.photos += 1;
            matches.push({
              name: member.name,
              group,
              filled: merged.filled,
              ...(merged.filled.length > 0 ? { line: serializeRosterMember(merged.member) } : {}),
              ...(wantsPhoto ? { photoUrl: person.photoUrl } : {}),
            });
          }
        } catch (error) {
          state.errors += 1;
          consecutiveErrors += 1;
          if (consecutiveErrors >= 5) {
            state.status = "error";
            state.error = `公式サイトから続けて読めませんでした：${error instanceof Error ? error.message : String(error)}`;
            state.finishedAt = Date.now();
            return;
          }
        }
        state.done += 1;
      }
      state.status = "done";
      state.finishedAt = Date.now();
    } catch (error) {
      state.status = "error";
      state.error = error instanceof Error ? error.message : String(error);
      state.finishedAt = Date.now();
    }
  })();
  return officialState();
}

/** Saves one official photo as photos/<group>/<name>.<ext>; returns false when it is not a small, real image. */
async function savePhoto(group: string, name: string, photoUrl: string): Promise<boolean> {
  let url: URL;
  try {
    url = new URL(photoUrl);
  } catch {
    return false;
  }
  if (url.protocol !== "https:" || !PHOTO_HOSTS.has(url.hostname)) return false;
  const { response, release } = await fetchWithSsrFGuard({
    url: url.href,
    requireHttps: true,
    maxRedirects: 2,
    timeoutMs: 30_000,
    init: { headers: { "User-Agent": USER_AGENT } },
  });
  try {
    if (!response.ok) return false;
    const mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
    const ext =
      mime === "image/jpeg"
        ? ".jpg"
        : mime === "image/png"
          ? ".png"
          : mime === "image/webp"
            ? ".webp"
            : "";
    const data = await readResponseWithLimit(response, MAX_PHOTO_BYTES, {
      timeoutMs: 30_000,
      chunkTimeoutMs: 10_000,
    });
    if (!ext || !data.length || !looksLikeIdolImage(data, mime)) return false;
    const dir = path.join(idolPhotoDir(), group);
    await fs.mkdir(dir, { recursive: true });
    const file = path.join(dir, `${name}${ext}`);
    try {
      await fs.access(file);
      return false; // never replace a file that is already there
    } catch {
      // not there yet
    }
    await fs.writeFile(file, data);
    return true;
  } finally {
    await release();
  }
}

/** Step 2: writes the lines kept by step 1 (one backup first) and saves the photos. Only after a finished check. */
export async function applyOfficial(
  options: { savePhotos?: boolean; pauseMs?: number } = {},
): Promise<{
  added: number;
  updated: number;
  photosSaved: number;
  backup?: string;
}> {
  if (state.status !== "done" && state.status !== "cancelled")
    throw new Error("先に「調べる」を、最後まで行ってください");
  if (state.applied)
    throw new Error("この結果は、すでに反映しました。もう一度「調べる」から行ってください");
  if (matches.length === 0) throw new Error("反映する項目が、ありません");
  const lines = matches.flatMap((match) => (match.line ? [match.line] : []));
  const rosterResult = lines.length > 0 ? await upsertRosterLines(lines) : { added: 0, updated: 0 };
  let photosSaved = 0;
  if (options.savePhotos !== false) {
    for (const match of matches) {
      if (!match.photoUrl) continue;
      try {
        if (await savePhoto(match.group, match.name, match.photoUrl)) photosSaved += 1;
      } catch {
        // One photo that cannot be saved does not stop the others.
      }
      await sleep(options.pauseMs ?? 400);
    }
  }
  const result = { ...rosterResult, photosSaved };
  state = { ...state, applied: result };
  matches = [];
  return result;
}

/** For tests: forget everything. */
export function resetOfficial(): void {
  state = { ...IDLE };
  matches = [];
  cancelRequested = false;
}
