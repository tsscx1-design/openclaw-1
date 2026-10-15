import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import type { CharacterHit } from "./anime-lookup.js";
import { ageFromBirthday, looksLikeIdolImage } from "./idol-master.js";
import { searchWikidata, wikidataSparql } from "./idol-wikidata.js";

// The fan book's "find in a database" side: people (actors, gravure idols, singers) from Wikidata with a gender and
// age filter and a random browse, and the characters of an anime from AniList / Jikan (see anime-lookup.ts).
// Nothing is written here; the user picks from the list and the handler adds the picked ones to the roster.

export type PersonKind =
  | "actor"
  | "singer"
  | "gravure"
  | "model"
  | "vtuber"
  | "youtuber"
  | "idol"
  | "av"
  | "all";
export type GenderFilter = "male" | "female" | "any";

// Wikidata occupations ("idol" is the Japanese idol and the live idol, which is what the fan book calls 地下アイドル).
const OCCUPATION: Record<PersonKind, string[]> = {
  actor: ["Q33999"],
  singer: ["Q177220"],
  gravure: ["Q1328668"],
  model: ["Q4610556"],
  vtuber: ["Q55155641"],
  youtuber: ["Q17125263"],
  idol: ["Q11346521", "Q226008"],
  // AV女優 and pornographic actors (Japanese citizens only, see JAPANESE_ONLY)
  av: ["Q1079215", "Q488111"],
  // everyone except the gravure kind, which has its own list
  all: ["Q33999", "Q177220", "Q4610556", "Q55155641", "Q17125263", "Q11346521", "Q226008"],
};
/** Kinds whose people are searched among Japanese citizens only (the others are Japanese by their nature). */
const JAPANESE_ONLY = new Set<PersonKind>(["actor", "singer", "model", "youtuber", "av", "all"]);
export const PERSON_KINDS = Object.keys(OCCUPATION) as PersonKind[];
const GENDER_ITEM: Record<Exclude<GenderFilter, "any">, string> = {
  male: "Q6581097",
  female: "Q6581072",
};
const ADULT_AGE = 18;
/** The gravure kind (and the adult anime works) leaves out a person whose age is written as under 18; an unknown age is listed. */
export const adultOnlyKind = (kind: PersonKind): boolean => kind === "gravure" || kind === "av";

export type PersonHit = {
  qid: string;
  name: string;
  /** "" when Wikidata has no birthday for the person. */
  birthday: string;
  age?: number;
  gender: "male" | "female" | "other";
  popularity: number;
  /** A small picture (Wikimedia Commons, https) or "" when Wikidata has none. */
  imageUrl: string;
};

export type PersonSearch = {
  kind: PersonKind;
  gender?: GenderFilter;
  ageMin?: number;
  ageMax?: number;
  /** Part of a name (Japanese). */
  query?: string;
  /** Random order (a different pick each time) instead of the well-known first. */
  random?: boolean;
  offset?: number;
  limit?: number;
  seed?: string;
  /** "young": the youngest first (newcomers are mostly young); default: the best known first. */
  order?: "popular" | "young";
  now?: Date;
};

const isoDate = (date: Date): string => date.toISOString().slice(0, 10);

/** The day a person must have been born on or before to be at least `years` old at `now`. */
function bornOnOrBefore(now: Date, years: number): string {
  const date = new Date(
    Date.UTC(now.getUTCFullYear() - years, now.getUTCMonth(), now.getUTCDate()),
  );
  return isoDate(date);
}

const clampInt = (value: number | undefined, min: number, max: number, fallback: number): number =>
  typeof value === "number" && Number.isFinite(value)
    ? Math.min(Math.max(Math.trunc(value), min), max)
    : fallback;

/**
 * The SPARQL for a search. A birthday is required only when an age filter is used (or for the adult-only kind, which
 * is kept to 18 and over whatever the filter says).
 */
export function buildPeopleQuery(search: PersonSearch): string {
  const now = search.now ?? new Date();
  const adultOnly = adultOnlyKind(search.kind);
  const strict = search.ageMin !== undefined || search.ageMax !== undefined;
  const ageMin = clampInt(search.ageMin, adultOnly ? ADULT_AGE : 0, 99, adultOnly ? ADULT_AGE : 0);
  const ageMax = clampInt(search.ageMax, ageMin, 120, 120);
  const limit = clampInt(search.limit, 1, 60, 30);
  const offset = clampInt(search.offset, 0, 5000, 0);
  const gender = search.gender && search.gender !== "any" ? search.gender : undefined;
  const text = (search.query ?? "")
    .trim()
    .slice(0, 40)
    .replace(/[\\"\r\n]/g, "");
  const seed = (search.seed ?? "0").replace(/[^\w-]/g, "").slice(0, 16) || "0";
  const lines = [
    "SELECT ?p ?pLabel ?birth ?gender ?links ?image WHERE {",
    `  VALUES ?occ { ${OCCUPATION[search.kind].map((id) => `wd:${id}`).join(" ")} }`,
    `  ?p wdt:P31 wd:Q5; wdt:P106 ?occ; wikibase:sitelinks ?links.`,
    // People without a birthday are listed too; a birthday that shows an age outside the range (or under 18 for the adult-only kinds) is not.
    "  OPTIONAL { ?p wdt:P569 ?birth. }",
    JAPANESE_ONLY.has(search.kind) ? "  ?p wdt:P27 wd:Q17." : "",
    gender ? `  ?p wdt:P21 wd:${GENDER_ITEM[gender]}.` : "",
    // Living people only (no date of death).
    "  FILTER NOT EXISTS { ?p wdt:P570 ?died. }",
    "  OPTIONAL { ?p wdt:P21 ?gender. }",
    "  OPTIONAL { ?p wdt:P18 ?image. }",
    strict || adultOnly
      ? `  FILTER(!BOUND(?birth) || (?birth <= "${bornOnOrBefore(now, ageMin)}T00:00:00Z"^^xsd:dateTime && ?birth > "${bornOnOrBefore(now, ageMax + 1)}T00:00:00Z"^^xsd:dateTime))`
      : "",
    '  ?p rdfs:label ?pLabel. FILTER(LANG(?pLabel) = "ja")',
    text ? `  FILTER(CONTAINS(?pLabel, "${text}"))` : "",
    "}",
    search.random
      ? `ORDER BY MD5(CONCAT(STR(?p), "${seed}"))`
      : search.order === "young"
        ? "ORDER BY DESC(?birth) DESC(?links)"
        : "ORDER BY DESC(?links)",
    `LIMIT ${limit * 2} OFFSET ${offset}`,
  ];
  return lines.filter(Boolean).join("\n");
}

type SparqlRow = Record<string, { value?: string } | undefined>;

/** People from the SPARQL result: no duplicate; for the adult-only kind nobody under 18 or without a birthday. */
export function parsePeopleRows(
  body: unknown,
  limit: number,
  now: Date = new Date(),
  adultOnly = false,
): PersonHit[] {
  const rows = ((body as { results?: { bindings?: SparqlRow[] } } | undefined)?.results?.bindings ??
    []) as SparqlRow[];
  const seen = new Set<string>();
  const hits: PersonHit[] = [];
  for (const row of rows) {
    const qid = /\/(Q[1-9]\d*)$/.exec(row.p?.value ?? "")?.[1];
    const name = row.pLabel?.value?.trim();
    const birthday = /^(\d{4}-\d{2}-\d{2})/.exec(row.birth?.value ?? "")?.[1];
    if (!qid || !name || seen.has(qid)) continue;
    const age = birthday ? ageFromBirthday(birthday, now) : undefined;
    if (adultOnly && age !== undefined && age < ADULT_AGE) continue;
    const genderItem = /\/(Q\d+)$/.exec(row.gender?.value ?? "")?.[1];
    seen.add(qid);
    hits.push({
      qid,
      name,
      birthday: birthday ?? "",
      ...(age !== undefined ? { age } : {}),
      gender:
        genderItem === GENDER_ITEM.male
          ? "male"
          : genderItem === GENDER_ITEM.female
            ? "female"
            : "other",
      popularity: Number(row.links?.value) || 0,
      imageUrl: commonsThumb(row.image?.value),
    });
    if (hits.length >= limit) break;
  }
  return hits;
}

/** A 240px-wide https address of a Commons file ("http://commons.wikimedia.org/wiki/Special:FilePath/X.jpg"). */
export function commonsThumb(value: string | undefined): string {
  if (!value) return "";
  const match = /^https?:\/\/commons\.wikimedia\.org\/wiki\/Special:FilePath\/([^?#]+)$/.exec(
    value,
  );
  return match ? `https://commons.wikimedia.org/wiki/Special:FilePath/${match[1]}?width=240` : "";
}

const THUMB_HOSTS = [
  "commons.wikimedia.org",
  "upload.wikimedia.org",
  "thumb.wikimedia.org",
  "api.openverse.org",
  "image.tmdb.org",
  "s4.anilist.co",
  "cdn.myanimelist.net",
  "encrypted-tbn0.gstatic.com",
  "encrypted-tbn1.gstatic.com",
  "encrypted-tbn2.gstatic.com",
  "encrypted-tbn3.gstatic.com",
];
const MAX_THUMB_BYTES = 1_500_000;

/** One small picture for a list row: only from the known picture hosts, only a real JPEG / PNG / WebP, as base64. */
export async function fetchThumbnail(
  url: string,
): Promise<{ mime: string; base64: string } | undefined> {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return undefined;
  }
  if (
    parsed.protocol !== "https:" ||
    parsed.username ||
    parsed.password ||
    !THUMB_HOSTS.includes(parsed.hostname)
  )
    return undefined;
  try {
    const { response, release } = await fetchWithSsrFGuard({
      url: parsed.href,
      requireHttps: true,
      maxRedirects: 2,
      timeoutMs: 20_000,
      policy: { hostnameAllowlist: THUMB_HOSTS },
      init: {
        headers: { "User-Agent": "OpenClaw-idol-master/1.0 (personal use; list thumbnails)" },
      },
    });
    try {
      if (!response.ok) return undefined;
      const mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
      const data = await readResponseWithLimit(response, MAX_THUMB_BYTES, {
        timeoutMs: 20_000,
        chunkTimeoutMs: 10_000,
      });
      if (
        !["image/jpeg", "image/png", "image/webp"].includes(mime) ||
        !data.length ||
        !looksLikeIdolImage(data, mime)
      )
        return undefined;
      return { mime, base64: data.toString("base64") };
    } finally {
      await release();
    }
  } catch {
    return undefined;
  }
}

export async function browsePeople(search: PersonSearch): Promise<PersonHit[]> {
  const limit = clampInt(search.limit, 1, 60, 30);
  const now = search.now ?? new Date();
  return parsePeopleRows(
    await wikidataSparql(buildPeopleQuery({ ...search, now })),
    limit,
    now,
    adultOnlyKind(search.kind),
  );
}

// ---------------------------------------------------------------- anime characters

/** The first whole number in an age text ("17", "17-18", "17歳"), or undefined for "unknown" and the like. */
export function characterAge(text: string | undefined): number | undefined {
  const match = /\d{1,3}/.exec(text ?? "");
  return match ? Number(match[0]) : undefined;
}

/**
 * Whether a character may be listed under a work. For an adult work a character whose age is written as under 18 is
 * left out (an unwritten age is listed); for the others any age is fine (the age rule of blocked-terms.json is applied when a character is made).
 */
export function characterListable(hit: Pick<CharacterHit, "age">, adultWork: boolean): boolean {
  const age = characterAge(hit.age);
  if (adultWork) return age === undefined || age >= ADULT_AGE;
  return true;
}

const GENDER_JA: Record<string, string> = {
  female: "女性",
  male: "男性",
  "non-binary": "ノンバイナリー",
};

const clean = (value: string, max: number): string =>
  value
    .replace(/[\r\n|=]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);

/** The roster line for a character of a work (the work is the group). */
export function characterRosterLine(
  hit: CharacterHit,
  work: string,
  source: "AniList" | "Jikan",
): string {
  const gender = GENDER_JA[(hit.gender ?? "").toLowerCase()];
  const age = characterAge(hit.age);
  const parts = [
    clean(work, 40),
    clean(hit.name || hit.englishName, 40),
    clean(hit.description, 200) || "anime character",
    ["キャラ", gender].filter(Boolean).join("/"),
    age !== undefined && age < 130 ? `年齢=${age}` : "",
    hit.imageUrl && /^https:\/\//i.test(hit.imageUrl) ? `写真=${hit.imageUrl}` : "",
    `出典=${source} ${hit.id}`,
  ];
  return parts.filter((part, index) => index < 3 || part).join(" | ");
}

// ---------------------------------------------------------------- Wikipedia (Japanese) category lists
// For the kinds that Wikidata knows few people of (live idols, VTubers ...): the pages of a Wikipedia category, with the
// picture of each page. Wikipedia has no birthday or gender field, so only the male / female categories can narrow it.

type WikiCategories = { male: string[]; female: string[]; any: string[] };

const WIKI_CATEGORIES: Record<PersonKind, WikiCategories> = {
  actor: { male: ["日本の男優"], female: ["日本の女優"], any: ["日本の男優", "日本の女優"] },
  singer: {
    male: ["日本の男性アイドル"],
    female: ["日本の女性アイドル"],
    any: ["日本の男性アイドル", "日本の女性アイドル"],
  },
  gravure: { male: [], female: ["グラビアアイドル"], any: ["グラビアアイドル"] },
  model: {
    male: ["日本の男性ファッションモデル"],
    female: ["日本の女性ファッションモデル"],
    any: ["日本の男性ファッションモデル", "日本の女性ファッションモデル"],
  },
  vtuber: {
    male: ["バーチャルYouTuber"],
    female: ["バーチャルYouTuber"],
    any: ["バーチャルYouTuber"],
  },
  youtuber: {
    male: ["日本の男性YouTuber"],
    female: ["日本の女性YouTuber"],
    any: ["日本の男性YouTuber", "日本の女性YouTuber"],
  },
  idol: { male: ["ライブアイドル"], female: ["ライブアイドル"], any: ["ライブアイドル"] },
  av: { male: [], female: ["日本のAV女優"], any: ["日本のAV女優"] },
  all: {
    male: [
      "日本の男優",
      "日本の男性ファッションモデル",
      "日本の男性YouTuber",
      "バーチャルYouTuber",
      "ライブアイドル",
    ],
    female: [
      "日本の女優",
      "日本の女性ファッションモデル",
      "日本の女性YouTuber",
      "バーチャルYouTuber",
      "ライブアイドル",
    ],
    any: [
      "日本の男優",
      "日本の女優",
      "日本の男性ファッションモデル",
      "日本の女性ファッションモデル",
      "日本の男性YouTuber",
      "日本の女性YouTuber",
      "バーチャルYouTuber",
      "ライブアイドル",
    ],
  },
};

export type WikiHit = { title: string; name: string; imageUrl: string; category: string };

/** "山田花子 (タレント)" -> "山田花子". */
export function wikiPersonName(title: string): string {
  return title.replace(/\s*[（(][^）)]*[）)]\s*$/, "").trim() || title;
}

const WIKI_HOSTS = ["ja.wikipedia.org"];
let wikiLast = 0;

async function wikiGet(params: Record<string, string>): Promise<unknown> {
  const url = `https://ja.wikipedia.org/w/api.php?${new URLSearchParams({ format: "json", formatversion: "2", ...params }).toString()}`;
  for (let attempt = 1; ; attempt++) {
    const wait = wikiLast + 800 - Date.now();
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    wikiLast = Date.now();
    const { response, release } = await fetchWithSsrFGuard({
      url,
      requireHttps: true,
      maxRedirects: 1,
      timeoutMs: 30_000,
      policy: { hostnameAllowlist: WIKI_HOSTS },
      init: {
        headers: {
          "User-Agent": "OpenClaw-idol-master/1.0 (personal use; reads category lists)",
          Accept: "application/json",
        },
      },
    });
    try {
      if ((response.status === 429 || response.status === 503) && attempt < 3) {
        await new Promise((resolve) => setTimeout(resolve, 5000 * attempt));
        continue;
      }
      if (!response.ok) throw new Error(`Wikipedia から読めませんでした (HTTP ${response.status})`);
      return JSON.parse(
        (
          await readResponseWithLimit(response, 3_000_000, {
            timeoutMs: 30_000,
            chunkTimeoutMs: 10_000,
          })
        ).toString("utf-8"),
      );
    } finally {
      await release();
    }
  }
}

/** Kana a page's reading can start with: a random one makes the "random" browse start somewhere different each time. */
const KANA =
  "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわ";

/** One page (up to `limit`) of a category's people with their pictures. `random` starts at a random kana. */
export async function browseWikipedia(search: {
  kind: PersonKind;
  gender?: GenderFilter;
  query?: string;
  random?: boolean;
  offset?: number;
  limit?: number;
  order?: "popular" | "young";
}): Promise<WikiHit[]> {
  const limit = clampInt(search.limit, 1, 50, 30);
  const text = (search.query ?? "").trim().slice(0, 40);
  const categories =
    WIKI_CATEGORIES[search.kind][
      search.gender === "male" || search.gender === "female" ? search.gender : "any"
    ];
  const hits: WikiHit[] = [];
  const seen = new Set<string>();
  for (const category of categories) {
    if (hits.length >= limit) break;
    let body: unknown;
    if (text) {
      // inside the category, by words in the page title
      body = await wikiGet({
        action: "query",
        generator: "search",
        gsrsearch: `incategory:"${category}" intitle:${text.replace(/["\\]/g, " ")}`,
        gsrlimit: String(limit),
        gsroffset: String(clampInt(search.offset, 0, 5000, 0)),
        prop: "pageimages",
        piprop: "thumbnail",
        pithumbsize: "240",
        pilimit: String(limit),
      });
    } else {
      body = await wikiGet({
        action: "query",
        generator: "categorymembers",
        gcmtitle: `Category:${category}`,
        gcmtype: "page",
        gcmlimit: String(Math.min(50, limit + clampInt(search.offset, 0, 5000, 0))),
        // "young": the pages most recently put into the category come first (new people are put in newly)
        ...(search.order === "young" && !search.random
          ? { gcmsort: "timestamp", gcmdir: "desc" }
          : { gcmsort: "sortkey" }),
        ...(search.random
          ? { gcmstartsortkeyprefix: KANA[Math.floor(Math.random() * KANA.length)]! }
          : {}),
        prop: "pageimages",
        piprop: "thumbnail",
        pithumbsize: "240",
        pilimit: "50",
      });
    }
    const pages = (
      (
        body as {
          query?: {
            pages?: Array<{ title?: string; index?: number; thumbnail?: { source?: string } }>;
          };
        }
      )?.query?.pages ?? []
    ).slice();
    pages.sort((a, b) => (a.index ?? 0) - (b.index ?? 0));
    for (const page of pages.slice(text ? 0 : clampInt(search.offset, 0, 5000, 0))) {
      const title = page.title?.trim();
      // project / category / list pages are not people
      if (!title || seen.has(title) || /[:：]/.test(title) || title.includes("一覧")) continue;
      seen.add(title);
      const image = page.thumbnail?.source ?? "";
      hits.push({
        title,
        name: wikiPersonName(title),
        imageUrl: /^https:\/\/upload\.wikimedia\.org\//.test(image) ? image : "",
        category,
      });
      if (hits.length >= limit) break;
    }
  }
  return hits;
}

/** The roster line of a Wikipedia page (the page title is the source; the picture is the page's thumbnail). */
export function wikipediaRosterLine(
  hit: { title: string; imageUrl?: string },
  group: string,
  tags: string[],
): string {
  const clean = (value: string, max: number): string =>
    value
      .replace(/[\r\n|=]+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, max);
  const parts = [
    clean(group, 40),
    clean(wikiPersonName(hit.title), 40),
    "",
    tags
      .map((tag) => clean(tag, 20))
      .filter(Boolean)
      .slice(0, 3)
      .join("/"),
    hit.imageUrl && /^https:\/\/upload\.wikimedia\.org\//.test(hit.imageUrl)
      ? `写真=${hit.imageUrl}`
      : "",
    `出典=Wikipedia ${clean(hit.title, 80)}`,
  ];
  return parts.filter((part, index) => index < 4 || part).join(" | ");
}

// ---------------------------------------------------------------- idol groups (search by name, or a list) and their members
// A group is found by its name (Wikidata search, any group: idol groups, bands, units) or, with no name, in the list of the
// Japanese Wikipedia category of women's / men's idol groups. The members are the people Wikidata lists as its present
// members (all of them, or only the women / the men).

const GROUP_CATEGORIES: Record<GenderFilter, string[]> = {
  female: ["日本の女性アイドルグループ"],
  male: ["日本の男性アイドルグループ"],
  any: ["日本の女性アイドルグループ", "日本の男性アイドルグループ"],
};
const GROUP_WORDS = /グループ|ユニット|バンド|アイドル|ボーイズ|ガールズ|組|group|band|unit|idol/i;
const NOT_A_GROUP =
  /シングル|アルバム|楽曲|曲|映画|番組|ドラマ|テレビ|ゲーム|漫画|ライブ|ツアー|single|album|song|film/i;

export type GroupHit = {
  title: string;
  name: string;
  qid: string;
  imageUrl: string;
  description: string;
};

/** Groups by name (any kind of group), or without a name the pages of the category (popular order; random starts at a random kana). */
export async function browseGroups(search: {
  gender?: GenderFilter;
  query?: string;
  random?: boolean;
  offset?: number;
  limit?: number;
}): Promise<GroupHit[]> {
  const limit = clampInt(search.limit, 1, 50, 30);
  const text = (search.query ?? "").trim().slice(0, 40);
  const offset = clampInt(search.offset, 0, 5000, 0);
  if (text) {
    // by name: every kind of group; the search answers 12 at most, so the "more" button only repeats the same ones
    const found = await searchWikidata(text, 12);
    return found
      .filter((one) => GROUP_WORDS.test(one.description) && !NOT_A_GROUP.test(one.description))
      .slice(0, limit)
      .map((one) => ({
        title: one.label,
        name: wikiPersonName(one.label),
        qid: one.qid,
        imageUrl: "",
        description: one.description,
      }))
      .slice(offset > 0 ? 12 : 0);
  }
  const gender: GenderFilter =
    search.gender === "male" || search.gender === "female" ? search.gender : "any";
  const hits: GroupHit[] = [];
  for (const category of GROUP_CATEGORIES[gender]) {
    if (hits.length >= limit) break;
    const body = await wikiGet({
      action: "query",
      generator: "categorymembers",
      gcmtitle: `Category:${category}`,
      gcmtype: "page",
      gcmlimit: String(Math.min(50, limit + offset)),
      gcmsort: "sortkey",
      ...(search.random
        ? { gcmstartsortkeyprefix: KANA[Math.floor(Math.random() * KANA.length)]! }
        : {}),
      prop: "pageimages|pageprops",
      piprop: "thumbnail",
      pithumbsize: "240",
      pilimit: "50",
      ppprop: "wikibase_item",
    });
    const pages = (
      (
        body as {
          query?: {
            pages?: Array<{
              title?: string;
              index?: number;
              thumbnail?: { source?: string };
              pageprops?: { wikibase_item?: string };
            }>;
          };
        }
      )?.query?.pages ?? []
    ).slice();
    pages.sort((a, b) => (a.index ?? 0) - (b.index ?? 0));
    for (const page of pages.slice(offset)) {
      const title = page.title?.trim();
      const qid = page.pageprops?.wikibase_item ?? "";
      if (
        !title ||
        !/^Q\d+$/.test(qid) ||
        /[:：]/.test(title) ||
        title.includes("一覧") ||
        hits.some((hit) => hit.qid === qid)
      )
        continue;
      const image = page.thumbnail?.source ?? "";
      hits.push({
        title,
        name: wikiPersonName(title),
        qid,
        imageUrl: /^https:\/\/upload\.wikimedia\.org\//.test(image) ? image : "",
        description: "",
      });
      if (hits.length >= limit) break;
    }
  }
  return hits;
}

/** The people Wikidata lists as present members of a group: all, or only the women / the men. */
export async function groupMembers(
  groupQid: string,
  gender: GenderFilter = "any",
): Promise<string[]> {
  if (!/^Q\d+$/.test(groupQid)) throw new Error("グループの番号が正しくありません");
  const genderPart =
    gender === "female" || gender === "male" ? `?p wdt:P21 wd:${GENDER_ITEM[gender]}.` : "";
  const body = (await wikidataSparql(
    `SELECT DISTINCT ?p WHERE { ?p p:P463 ?st. ?st ps:P463 wd:${groupQid}. FILTER NOT EXISTS { ?st pq:P582 ?end } ?p wdt:P31 wd:Q5. ${genderPart} }`,
  )) as { results?: { bindings?: SparqlRow[] } };
  const ids: string[] = [];
  for (const row of body.results?.bindings ?? []) {
    const qid = /\/(Q\d+)$/.exec(row.p?.value ?? "")?.[1];
    if (qid && !ids.includes(qid)) ids.push(qid);
  }
  return ids;
}
