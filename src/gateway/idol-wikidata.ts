import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import { ageFromBirthday } from "./idol-master.js";

// Reads one person at a time from Wikidata (CC0, official API) for the master data. Nothing is crawled:
// the user searches a name, picks a candidate and sees the facts before anything is written.

const UA = "OpenClaw-idol-master/1.0 (personal use; reads one Wikidata item at a time)";
const HOSTS = [
  "www.wikidata.org",
  "query.wikidata.org",
  "commons.wikimedia.org",
  "upload.wikimedia.org",
];
const MAX_JSON_BYTES = 3 * 1024 * 1024;

export type WikidataCandidate = { qid: string; label: string; description: string };

export type WikidataPerson = {
  qid: string;
  name: string;
  description: string;
  birthday?: string;
  age?: number;
  bloodType?: string;
  hometown?: string;
  heightCm?: number;
  xUrl?: string;
  /** A Wikimedia Commons file address (https); the gateway downloads it when it is shown. */
  photoUrl?: string;
  /** The Commons page of that file (its license is shown there). */
  photoPageUrl?: string;
  groups: string[];
  occupations: string[];
  wikipediaUrl?: string;
  sourceUrl: string;
};

type Snak = { datavalue?: { value?: unknown } };
type Claim = { mainsnak?: Snak; rank?: string };
type Entity = {
  labels?: Record<string, { value?: string }>;
  descriptions?: Record<string, { value?: string }>;
  claims?: Record<string, Claim[]>;
  sitelinks?: Record<string, { title?: string }>;
};

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Wikimedia asks for serial, unhurried requests: keep at least this long between two requests. */
const MIN_GAP_MS = 800;
const MAX_ATTEMPTS = 4;
let lastRequestAt = 0;

/** Seconds from a Retry-After header (a number of seconds), at most 90; undefined when it is absent or not a number. */
export function retryAfterSeconds(value: string | null | undefined): number | undefined {
  const seconds = Number.parseInt(value ?? "", 10);
  return Number.isFinite(seconds) && seconds >= 0 ? Math.min(seconds, 90) : undefined;
}

async function getJson(url: string, timeoutMs = 30_000): Promise<unknown> {
  for (let attempt = 1; ; attempt++) {
    const wait = lastRequestAt + MIN_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    lastRequestAt = Date.now();
    const { response, release } = await fetchWithSsrFGuard({
      url,
      requireHttps: true,
      maxRedirects: 1,
      timeoutMs,
      policy: { hostnameAllowlist: HOSTS },
      init: { headers: { "User-Agent": UA, Accept: "application/json" } },
    });
    try {
      // Too many requests (429) or busy (503): wait as told (or longer each time) and try again.
      if ((response.status === 429 || response.status === 503) && attempt < MAX_ATTEMPTS) {
        const told = retryAfterSeconds(response.headers.get("retry-after"));
        await sleep((told ?? 5 * 2 ** (attempt - 1)) * 1000);
        continue;
      }
      if (!response.ok) throw new Error(`Wikidata から読めませんでした (HTTP ${response.status})`);
      const data = await readResponseWithLimit(response, MAX_JSON_BYTES, {
        timeoutMs,
        chunkTimeoutMs: 10_000,
      });
      return JSON.parse(data.toString("utf-8"));
    } finally {
      await release();
    }
  }
}

/** Runs a read-only SPARQL query on the Wikidata query service (the result as JSON). */
export async function wikidataSparql(query: string): Promise<unknown> {
  // The query service allows a query 60 seconds: a big set (singers, ordered by fame) can need most of it.
  return getJson(
    `https://query.wikidata.org/sparql?format=json&query=${encodeURIComponent(query)}`,
    65_000,
  );
}

export function isQid(value: unknown): value is string {
  return typeof value === "string" && /^Q[1-9]\d{0,12}$/.test(value);
}

/** Candidates for a name (Japanese first). */
export async function searchWikidata(query: string, limit = 8): Promise<WikidataCandidate[]> {
  const text = query.trim().slice(0, 80);
  if (!text) return [];
  const url =
    "https://www.wikidata.org/w/api.php?action=wbsearchentities&type=item&format=json&language=ja&uselang=ja" +
    `&limit=${Math.min(Math.max(limit, 1), 12)}&search=${encodeURIComponent(text)}`;
  const data = (await getJson(url)) as {
    search?: Array<{ id?: string; label?: string; description?: string }>;
  };
  return (data.search ?? []).flatMap((row) =>
    isQid(row.id)
      ? [{ qid: row.id, label: row.label ?? row.id, description: row.description ?? "" }]
      : [],
  );
}

const claimValue = (entity: Entity, property: string): unknown => {
  const claims = entity.claims?.[property] ?? [];
  const best =
    claims.find((claim) => claim.rank === "preferred") ??
    claims.find((claim) => claim.rank !== "deprecated");
  return best?.mainsnak?.datavalue?.value;
};
const claimValues = (entity: Entity, property: string): unknown[] =>
  (entity.claims?.[property] ?? [])
    .filter((claim) => claim.rank !== "deprecated")
    .map((claim) => claim.mainsnak?.datavalue?.value);
const itemId = (value: unknown): string | undefined =>
  value && typeof value === "object" && isQid((value as { id?: unknown }).id)
    ? (value as { id: string }).id
    : undefined;

/** Day-precision birthday from a Wikidata time value ("+1994-10-04T00:00:00Z", precision 11). */
function birthdayOf(value: unknown): string | undefined {
  const time = value as { time?: string; precision?: number } | undefined;
  if (!time?.time || (time.precision ?? 0) < 11) return undefined;
  const m = /^\+?(\d{4})-(\d{2})-(\d{2})/.exec(time.time);
  return m && m[2] !== "00" && m[3] !== "00" ? `${m[1]}-${m[2]}-${m[3]}` : undefined;
}

/** Centimetres from a Wikidata quantity (centimetre or metre units only). */
function heightOf(value: unknown): number | undefined {
  const q = value as { amount?: string; unit?: string } | undefined;
  const amount = Number.parseFloat(q?.amount ?? "");
  if (!Number.isFinite(amount)) return undefined;
  const unit = q?.unit ?? "";
  const cm = unit.endsWith("/Q174728") ? amount : unit.endsWith("/Q11573") ? amount * 100 : NaN;
  return cm >= 100 && cm <= 250 ? Math.round(cm * 10) / 10 : undefined;
}

/** "A", "B", "O" or "AB" from the label of the blood type item ("B", "blood type B", "B型" …). */
export function bloodTypeFromLabel(label: string | undefined): string | undefined {
  const m = /(?:^|[^A-Z])(AB|A|B|O)(?:[^A-Z]|$)/.exec(
    (label ?? "").toUpperCase().replace(/型/g, " "),
  );
  return m?.[1];
}

/** The facts of one Wikidata item, with the labels of the items it points to (qid -> Japanese/English label). */
export function extractWikidataPerson(
  qid: string,
  entity: Entity,
  labels: ReadonlyMap<string, string>,
  now: Date = new Date(),
): WikidataPerson {
  const label = (id: string | undefined) => (id ? labels.get(id) : undefined);
  const name = entity.labels?.ja?.value ?? entity.labels?.en?.value ?? qid;
  const birthday = birthdayOf(claimValue(entity, "P569"));
  const x = claimValue(entity, "P2002");
  const photo = claimValue(entity, "P18");
  const wikiTitle = entity.sitelinks?.jawiki?.title;
  return {
    qid,
    name,
    description: entity.descriptions?.ja?.value ?? entity.descriptions?.en?.value ?? "",
    ...(birthday
      ? {
          birthday,
          ...(ageFromBirthday(birthday, now) !== undefined
            ? { age: ageFromBirthday(birthday, now)! }
            : {}),
        }
      : {}),
    ...(bloodTypeFromLabel(label(itemId(claimValue(entity, "P1853"))))
      ? { bloodType: bloodTypeFromLabel(label(itemId(claimValue(entity, "P1853"))))! }
      : {}),
    ...(label(itemId(claimValue(entity, "P19")))
      ? { hometown: label(itemId(claimValue(entity, "P19")))! }
      : {}),
    ...(heightOf(claimValue(entity, "P2048")) !== undefined
      ? { heightCm: heightOf(claimValue(entity, "P2048"))! }
      : {}),
    ...(typeof x === "string" && /^[A-Za-z0-9_]{1,15}$/.test(x)
      ? { xUrl: `https://x.com/${x}` }
      : {}),
    ...(typeof photo === "string" && photo
      ? {
          photoUrl: `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(photo)}?width=600`,
          photoPageUrl: `https://commons.wikimedia.org/wiki/File:${encodeURIComponent(photo.replace(/ /g, "_"))}`,
        }
      : {}),
    groups: claimValues(entity, "P463")
      .map((v) => label(itemId(v)))
      .filter((v): v is string => Boolean(v))
      .slice(0, 4),
    occupations: claimValues(entity, "P106")
      .map((v) => label(itemId(v)))
      .filter((v): v is string => Boolean(v))
      .slice(0, 4),
    ...(wikiTitle
      ? {
          wikipediaUrl: `https://ja.wikipedia.org/wiki/${encodeURIComponent(wikiTitle.replace(/ /g, "_"))}`,
        }
      : {}),
    sourceUrl: `https://www.wikidata.org/wiki/${qid}`,
  };
}

/** Reads one item (and the labels of the items it points to) and returns its facts. */
export async function readWikidataPerson(qid: string): Promise<WikidataPerson> {
  if (!isQid(qid)) throw new Error("Wikidata の番号が正しくありません");
  const data = (await getJson(`https://www.wikidata.org/wiki/Special:EntityData/${qid}.json`)) as {
    entities?: Record<string, Entity>;
  };
  const entity = data.entities?.[qid];
  if (!entity) throw new Error("その項目が見つかりません");
  const wanted = new Set<string>();
  for (const property of ["P1853", "P19"]) {
    const id = itemId(claimValue(entity, property));
    if (id) wanted.add(id);
  }
  for (const property of ["P463", "P106"])
    for (const value of claimValues(entity, property).slice(0, 4)) {
      const id = itemId(value);
      if (id) wanted.add(id);
    }
  const labels = new Map<string, string>();
  if (wanted.size > 0) {
    const ids = [...wanted].join("|");
    const found = (await getJson(
      `https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=labels&languages=ja|en&ids=${encodeURIComponent(ids)}`,
    )) as { entities?: Record<string, Entity> };
    for (const [id, item] of Object.entries(found.entities ?? {})) {
      const text = item.labels?.ja?.value ?? item.labels?.en?.value;
      if (text) labels.set(id, text);
    }
  }
  return extractWikidataPerson(qid, entity, labels);
}

/** Text safe for a roster field: no line breaks and no "|". */
const clean = (value: string): string =>
  value
    .replace(/[\r\n]+/g, " ")
    .replace(/\|/g, "｜")
    .trim();

/**
 * The roster line for a person. `group` and `name` may be given by the user; the group defaults to the first
 * group on Wikidata. The source is written into the line so every value can be traced.
 */
export function wikidataRosterLine(
  person: WikidataPerson,
  options: { group?: string; name?: string; tags?: string[] } = {},
): string {
  const group = clean(options.group || person.groups[0] || "未分類").slice(0, 40);
  const name = clean(options.name || person.name).slice(0, 40);
  const tags = (options.tags ?? person.occupations)
    .map(clean)
    .filter(Boolean)
    .slice(0, 3)
    .join("/");
  const keyed = [
    person.birthday ? `誕生日=${person.birthday}` : "",
    person.bloodType ? `血液型=${person.bloodType}` : "",
    person.hometown ? `出身地=${clean(person.hometown)}` : "",
    person.heightCm ? `身長=${person.heightCm}` : "",
    person.xUrl ? `X=${person.xUrl}` : "",
    person.photoUrl ? `写真=${person.photoUrl}` : "",
    `出典=Wikidata ${person.qid}`,
  ].filter(Boolean);
  return [group, name, "", tags, ...keyed].join(" | ");
}
