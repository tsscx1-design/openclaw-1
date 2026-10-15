import { ageFromBirthday } from "./idol-master.js";

// TMDB (The Movie Database): people of films and TV (actors, voice actors ...) with birthday, birthplace and pictures, in
// Japanese. It is read with the user's own API key (TMDB_API_KEY in ~/.openclaw/.env, never written into the code or logs).
// Only the paths below are asked for, on the fixed host; the key goes only into the query of those requests.

const BASE = "https://api.themoviedb.org/3";
const IMAGE_BASE = "https://image.tmdb.org/t/p";
const ALLOWED_PATH =
  /^\/(?:search\/person|person\/popular|person\/\d{1,9}|person\/\d{1,9}\/images|person\/\d{1,9}\/combined_credits)$/;

export const TMDB_NOTICE =
  "This product uses the TMDB API but is not endorsed or certified by TMDB.";

function apiKey(): string {
  const key = process.env.TMDB_API_KEY?.trim() ?? "";
  if (!/^[0-9a-f]{32}$/i.test(key))
    throw new Error("TMDB の鍵が設定されていません（~/.openclaw/.env の TMDB_API_KEY）");
  return key;
}

let lastCall = 0;
async function tmdbGet(pathname: string, params: Record<string, string> = {}): Promise<unknown> {
  if (!ALLOWED_PATH.test(pathname)) throw new Error("TMDB の問い合わせ先が正しくありません");
  for (let attempt = 1; ; attempt++) {
    const wait = lastCall + 120 - Date.now();
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    lastCall = Date.now();
    const key = apiKey();
    let response: Response;
    try {
      response = await fetch(
        `${BASE}${pathname}?${new URLSearchParams({ ...params, api_key: key }).toString()}`,
        {
          headers: { Accept: "application/json" },
          signal: AbortSignal.timeout(20_000),
        },
      );
    } catch {
      // the message never carries the address (it has the key in it)
      throw new Error("TMDB につながりませんでした");
    }
    if (response.status === 429 && attempt < 3) {
      await new Promise((resolve) => setTimeout(resolve, 1500 * attempt));
      continue;
    }
    if (response.status === 401) throw new Error("TMDB の鍵が、受け付けられませんでした");
    if (!response.ok) throw new Error(`TMDB から読めませんでした (HTTP ${response.status})`);
    return response.json();
  }
}

export type TmdbPerson = {
  id: number;
  name: string;
  gender: "female" | "male" | "other";
  birthday?: string;
  age?: number;
  place?: string;
  /** Small picture for a list (https), "" when TMDB has none. */
  thumbUrl: string;
  /** The picture kept as the photo (https). */
  photoUrl: string;
  knownFor: string[];
  profession?: string;
  /** Marked adult by TMDB. */
  adult?: boolean;
};

type RawPerson = {
  id?: number;
  name?: string;
  gender?: number;
  birthday?: string | null;
  place_of_birth?: string | null;
  profile_path?: string | null;
  known_for_department?: string;
  adult?: boolean;
  known_for?: Array<{ title?: string; name?: string }>;
};

const imageUrl = (path: string | null | undefined, size: string): string =>
  path && /^\/[\w.-]+\.(?:jpg|png)$/i.test(path) ? `${IMAGE_BASE}/${size}${path}` : "";

export function personFromTmdb(raw: RawPerson, now: Date = new Date()): TmdbPerson | undefined {
  if (typeof raw.id !== "number" || !raw.name?.trim()) return undefined;
  const birthday = /^\d{4}-\d{2}-\d{2}$/.test(raw.birthday ?? "") ? raw.birthday! : undefined;
  return {
    id: raw.id,
    name: raw.name.trim(),
    gender: raw.gender === 1 ? "female" : raw.gender === 2 ? "male" : "other",
    ...(birthday ? { birthday, age: ageFromBirthday(birthday, now) } : {}),
    ...(raw.place_of_birth ? { place: raw.place_of_birth.trim().slice(0, 60) } : {}),
    thumbUrl: imageUrl(raw.profile_path, "w185"),
    photoUrl: imageUrl(raw.profile_path, "w500"),
    knownFor: (raw.known_for ?? [])
      .map((item) => (item.title ?? item.name ?? "").trim())
      .filter(Boolean)
      .slice(0, 3),
    ...(raw.known_for_department ? { profession: raw.known_for_department } : {}),
    ...(raw.adult === true ? { adult: true } : {}),
  };
}

/** People by name, or the popular ones (page 1, 2 ...; 20 a page). */
export async function listTmdbPeople(options: {
  query?: string;
  page?: number;
  adult?: boolean;
}): Promise<TmdbPerson[]> {
  const page = String(Math.min(Math.max(Math.trunc(options.page ?? 1) || 1, 1), 50));
  const text = (options.query ?? "").trim().slice(0, 60);
  const body = (await (text
    ? tmdbGet("/search/person", {
        query: text,
        language: "ja-JP",
        page,
        include_adult: options.adult ? "true" : "false",
      })
    : tmdbGet("/person/popular", { language: "ja-JP", page }))) as { results?: RawPerson[] };
  return (body.results ?? [])
    .map((raw) => personFromTmdb(raw))
    .filter((one): one is TmdbPerson => Boolean(one));
}

/** One person with the birthday and the birthplace (they are not in the lists). */
export async function tmdbPersonDetail(id: number): Promise<TmdbPerson> {
  if (!Number.isInteger(id) || id <= 0) throw new Error("TMDB の番号が正しくありません");
  const person = personFromTmdb(
    (await tmdbGet(`/person/${id}`, { language: "ja-JP" })) as RawPerson,
  );
  if (!person) throw new Error("その人が見つかりません");
  return person;
}

/** The titles of the films and shows a person appears in (the best known first). */
export async function tmdbPersonCredits(id: number): Promise<string[]> {
  const body = (await tmdbGet(`/person/${id}/combined_credits`, { language: "ja-JP" })) as {
    cast?: Array<{ title?: string; name?: string; popularity?: number; vote_count?: number }>;
  };
  const titles = (body.cast ?? [])
    .sort((a, b) => (b.vote_count ?? 0) - (a.vote_count ?? 0))
    .map((one) => (one.title ?? one.name ?? "").trim())
    .filter(Boolean);
  return [...new Set(titles)].slice(0, 15);
}

/** The pictures TMDB has of a person (file paths -> https addresses). */
export async function tmdbPersonImages(
  id: number,
): Promise<Array<{ thumbUrl: string; imageUrl: string }>> {
  const body = (await tmdbGet(`/person/${id}/images`)) as {
    profiles?: Array<{ file_path?: string }>;
  };
  return (body.profiles ?? [])
    .map((one) => ({
      thumbUrl: imageUrl(one.file_path, "w185"),
      imageUrl: imageUrl(one.file_path, "w500"),
    }))
    .filter((one) => one.thumbUrl && one.imageUrl)
    .slice(0, 12);
}

const clean = (value: string, max: number): string =>
  value
    .replace(/[\r\n|=]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);

/** The roster line of a TMDB person (the source is written into the line). */
export function tmdbRosterLine(person: TmdbPerson, group: string): string {
  const parts = [
    clean(group, 40),
    clean(person.name, 40),
    clean(person.knownFor.join("、"), 120),
    person.profession
      ? person.profession === "Acting"
        ? "俳優"
        : clean(person.profession, 20)
      : "",
    person.birthday ? `誕生日=${person.birthday}` : "",
    person.place ? `出身地=${clean(person.place, 60)}` : "",
    person.photoUrl ? `写真=${person.photoUrl}` : "",
    `出典=TMDB ${person.id}`,
  ];
  return parts.filter((part, index) => index < 4 || part).join(" | ");
}

// ---------------------------------------------------------------- common shape for the outside sources
// TMDB (film / TV people), MusicBrainz (musicians) and AniList (voice actors) give the same kind of row, so the fan book
// can list them, filter them and add them in one way. Each source is asked on its own fixed host, only for the paths below.

export type ExternalSource = "tmdb" | "musicbrainz" | "anilist";
export const EXTERNAL_SOURCES: readonly ExternalSource[] = ["tmdb", "musicbrainz", "anilist"];

export type ExternalPerson = {
  source: ExternalSource;
  /** The id in that source (a number, or a MusicBrainz id). */
  id: string;
  name: string;
  gender: "female" | "male" | "other";
  birthday?: string;
  age?: number;
  place?: string;
  thumbUrl: string;
  photoUrl: string;
  /** Works / areas / occupations, for the list and the roster line. */
  note: string;
  profession?: string;
  /** Marked adult by the source: such a person is listed only with a birthday that shows 18 or more. */
  adult?: boolean;
};

export type ExternalSearch = {
  source: ExternalSource;
  query?: string;
  page?: number;
  gender?: "female" | "male" | "any";
  ageMin?: number;
  ageMax?: number;
  /** TMDB: include entries marked adult (they are kept only when the birthday shows 18 or more). */
  adult?: boolean;
};

const UA = "OpenClaw-idol-master/1.0 (personal use; reads one person at a time)";

async function limited<T>(
  items: T[],
  size: number,
  run: (item: T) => Promise<void>,
): Promise<void> {
  for (let i = 0; i < items.length; i += size) await Promise.all(items.slice(i, i + size).map(run));
}

export function toExternal(person: TmdbPerson): ExternalPerson {
  return {
    source: "tmdb",
    id: String(person.id),
    name: person.name,
    gender: person.gender,
    ...(person.birthday ? { birthday: person.birthday, age: person.age } : {}),
    ...(person.place ? { place: person.place } : {}),
    thumbUrl: person.thumbUrl,
    photoUrl: person.photoUrl,
    note: person.knownFor.join("、"),
    profession: person.profession === "Acting" ? "俳優" : person.profession,
    ...(person.adult ? { adult: true } : {}),
  };
}

/** Pictures of people with this name on TMDB, for the picture search of a member (up to two people). */
export async function searchTmdbImages(
  name: string,
): Promise<
  Array<{ id: string; title: string; thumbUrl: string; imageUrl: string; pageUrl: string }>
> {
  const people = (await listTmdbPeople({ query: name }))
    .filter((one) => one.name.replace(/\s+/g, "") === name.replace(/\s+/g, ""))
    .slice(0, 2);
  const rows: Array<{
    id: string;
    title: string;
    thumbUrl: string;
    imageUrl: string;
    pageUrl: string;
  }> = [];
  for (const person of people) {
    const images = await tmdbPersonImages(person.id);
    images.forEach((image, index) =>
      rows.push({
        id: `tmdb:${person.id}:${index}`,
        title: `${person.name}（TMDB）`,
        thumbUrl: image.thumbUrl,
        imageUrl: image.imageUrl,
        pageUrl: `https://www.themoviedb.org/person/${person.id}`,
      }),
    );
  }
  return rows;
}

// ---------------------------------------------------------------- MusicBrainz (musicians; no key, one request a second)

const MB_BASE = "https://musicbrainz.org/ws/2";
const MB_PATH = /^\/artist(?:\/[0-9a-f-]{36})?$/i;
let mbLast = 0;

async function mbGet(pathname: string, params: Record<string, string>): Promise<unknown> {
  if (!MB_PATH.test(pathname)) throw new Error("MusicBrainz の問い合わせ先が正しくありません");
  for (let attempt = 1; ; attempt++) {
    const wait = mbLast + 1100 - Date.now();
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    mbLast = Date.now();
    let response: Response;
    try {
      response = await fetch(
        `${MB_BASE}${pathname}?${new URLSearchParams({ ...params, fmt: "json" }).toString()}`,
        {
          headers: { "User-Agent": UA, Accept: "application/json" },
          signal: AbortSignal.timeout(25_000),
        },
      );
    } catch {
      throw new Error("MusicBrainz につながりませんでした");
    }
    if ((response.status === 429 || response.status === 503) && attempt < 3) {
      await new Promise((resolve) => setTimeout(resolve, 2500 * attempt));
      continue;
    }
    if (!response.ok) throw new Error(`MusicBrainz から読めませんでした (HTTP ${response.status})`);
    return response.json();
  }
}

type MbArtist = {
  id?: string;
  name?: string;
  type?: string;
  gender?: string | null;
  country?: string | null;
  disambiguation?: string;
  "life-span"?: { begin?: string | null };
  area?: { name?: string } | null;
  "begin-area"?: { name?: string } | null;
  tags?: Array<{ name?: string; count?: number }>;
  aliases?: Array<{ name?: string; locale?: string | null; primary?: boolean | null }>;
};

export function personFromMusicBrainz(
  raw: MbArtist,
  now: Date = new Date(),
): ExternalPerson | undefined {
  if (!raw.id || !raw.name?.trim()) return undefined;
  // the Japanese alias when there is one (the artist name may be romaji)
  const japanese = raw.aliases?.find(
    (alias) =>
      alias.locale === "ja" &&
      alias.name &&
      /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u.test(alias.name),
  )?.name;
  const name = (
    /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u.test(raw.name)
      ? raw.name
      : (japanese ?? raw.name)
  ).trim();
  const begin = raw["life-span"]?.begin ?? "";
  const birthday = /^\d{4}-\d{2}-\d{2}$/.test(begin) ? begin : undefined;
  const genderRaw = (raw.gender ?? "").toLowerCase();
  const place = raw["begin-area"]?.name ?? raw.area?.name;
  return {
    source: "musicbrainz",
    id: raw.id,
    name,
    gender: genderRaw === "female" ? "female" : genderRaw === "male" ? "male" : "other",
    ...(birthday ? { birthday, age: ageFromBirthday(birthday, now) } : {}),
    ...(place ? { place: place.slice(0, 60) } : {}),
    thumbUrl: "",
    photoUrl: "",
    note: [
      raw.disambiguation,
      ...(raw.tags ?? [])
        .sort((a, b) => (b.count ?? 0) - (a.count ?? 0))
        .slice(0, 2)
        .map((tag) => tag.name),
    ]
      .filter(Boolean)
      .join("、")
      .slice(0, 100),
    profession: raw.type === "Group" ? "グループ" : "歌手",
  };
}

async function listMusicBrainz(options: ExternalSearch): Promise<ExternalPerson[]> {
  const text = (options.query ?? "").trim().replace(/["\\]/g, " ").slice(0, 60);
  const gender =
    options.gender === "female" || options.gender === "male" ? ` AND gender:${options.gender}` : "";
  // without a name: the people of Japan with a birth date, which MusicBrainz finds by area
  const query = `${text ? `artist:"${text}" OR alias:"${text}"` : "country:JP"} AND type:person${gender}`;
  const page = Math.min(Math.max(Math.trunc(options.page ?? 1) || 1, 1), 50);
  const body = (await mbGet("/artist", {
    query,
    limit: "20",
    offset: String((page - 1) * 20),
  })) as { artists?: MbArtist[] };
  return (body.artists ?? [])
    .map((raw) => personFromMusicBrainz(raw))
    .filter((one): one is ExternalPerson => Boolean(one));
}

async function detailMusicBrainz(id: string): Promise<ExternalPerson> {
  if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error("MusicBrainz の番号が正しくありません");
  const person = personFromMusicBrainz(
    (await mbGet(`/artist/${id}`, { inc: "aliases+tags" })) as MbArtist,
  );
  if (!person) throw new Error("その人が見つかりません");
  return person;
}

// ---------------------------------------------------------------- AniList (voice actors and staff; no key)

const ANILIST_URL = "https://graphql.anilist.co";
const STAFF_FIELDS = `id name { full native } image { large medium } gender dateOfBirth { year month day } homeTown primaryOccupations languageV2`;
let aniLast = 0;

async function aniGet(query: string, variables: Record<string, unknown>): Promise<unknown> {
  for (let attempt = 1; ; attempt++) {
    const wait = aniLast + 800 - Date.now();
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    aniLast = Date.now();
    let response: Response;
    try {
      response = await fetch(ANILIST_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          "User-Agent": UA,
        },
        body: JSON.stringify({ query, variables }),
        signal: AbortSignal.timeout(25_000),
      });
    } catch {
      throw new Error("AniList につながりませんでした");
    }
    if (response.status === 429 && attempt < 3) {
      await new Promise((resolve) => setTimeout(resolve, 3000 * attempt));
      continue;
    }
    if (!response.ok) throw new Error(`AniList から読めませんでした (HTTP ${response.status})`);
    return response.json();
  }
}

type AniStaff = {
  id?: number;
  name?: { full?: string | null; native?: string | null };
  image?: { large?: string | null; medium?: string | null };
  gender?: string | null;
  dateOfBirth?: { year?: number | null; month?: number | null; day?: number | null };
  homeTown?: string | null;
  primaryOccupations?: string[] | null;
};

export function personFromAniList(
  raw: AniStaff,
  now: Date = new Date(),
): ExternalPerson | undefined {
  const name = (raw.name?.native || raw.name?.full || "").trim();
  if (typeof raw.id !== "number" || !name) return undefined;
  const dob = raw.dateOfBirth;
  const birthday =
    dob?.year && dob.month && dob.day
      ? `${dob.year}-${String(dob.month).padStart(2, "0")}-${String(dob.day).padStart(2, "0")}`
      : undefined;
  const genderRaw = (raw.gender ?? "").toLowerCase();
  const image = (value: string | null | undefined): string =>
    value && /^https:\/\/s4\.anilist\.co\//.test(value) ? value : "";
  return {
    source: "anilist",
    id: String(raw.id),
    name,
    gender: genderRaw === "female" ? "female" : genderRaw === "male" ? "male" : "other",
    ...(birthday ? { birthday, age: ageFromBirthday(birthday, now) } : {}),
    ...(raw.homeTown ? { place: raw.homeTown.trim().slice(0, 60) } : {}),
    thumbUrl: image(raw.image?.medium) || image(raw.image?.large),
    photoUrl: image(raw.image?.large) || image(raw.image?.medium),
    note: (raw.primaryOccupations ?? []).slice(0, 3).join("、"),
    profession: "声優",
  };
}

async function listAniList(options: ExternalSearch): Promise<ExternalPerson[]> {
  const text = (options.query ?? "").trim().slice(0, 60);
  const page = Math.min(Math.max(Math.trunc(options.page ?? 1) || 1, 1), 50);
  const body = (await aniGet(
    `query ($page: Int, $search: String) { Page(page: $page, perPage: 20) { staff(search: $search, sort: FAVOURITES_DESC) { ${STAFF_FIELDS} } } }`,
    { page, ...(text ? { search: text } : {}) },
  )) as { data?: { Page?: { staff?: AniStaff[] } } };
  return (body.data?.Page?.staff ?? [])
    .map((raw) => personFromAniList(raw))
    .filter((one): one is ExternalPerson => Boolean(one));
}

async function detailAniList(id: string): Promise<ExternalPerson> {
  const number = Number(id);
  if (!Number.isInteger(number) || number <= 0) throw new Error("AniList の番号が正しくありません");
  const body = (await aniGet(`query ($id: Int) { Staff(id: $id) { ${STAFF_FIELDS} } }`, {
    id: number,
  })) as { data?: { Staff?: AniStaff } };
  const person = body.data?.Staff ? personFromAniList(body.data.Staff) : undefined;
  if (!person) throw new Error("その人が見つかりません");
  return person;
}

// ---------------------------------------------------------------- the one way in

/** Whether a person may be listed: one marked adult needs a birthday that shows 18 or more. */
export function externalListable(person: Pick<ExternalPerson, "adult" | "age">): boolean {
  // Marked adult: listed unless the birthday shows under 18 (an unknown birthday is listed).
  return !person.adult || person.age === undefined || person.age >= 18;
}

/** Lists people from a source, with the gender and age filters of the fan book applied. */
export async function listExternalPeople(options: ExternalSearch): Promise<ExternalPerson[]> {
  let people: ExternalPerson[];
  if (options.source === "tmdb") {
    const raw = await listTmdbPeople({
      query: options.query,
      page: options.page,
      adult: options.adult === true,
    });
    people = raw
      .map(toExternal)
      .filter((one) =>
        options.gender === "female"
          ? one.gender === "female"
          : options.gender === "male"
            ? one.gender === "male"
            : true,
      );
    // the lists carry no birthday: it is read for each one (a few at a time), so the age can be shown and filtered
    await limited(people, 5, async (one) => {
      try {
        const detail = await tmdbPersonDetail(Number(one.id));
        Object.assign(one, {
          birthday: detail.birthday,
          age: detail.age,
          place: detail.place,
          ...(detail.adult || one.adult ? { adult: true } : {}),
        });
      } catch {
        // the row stays as it is
      }
    });
  } else if (options.source === "musicbrainz") {
    people = await listMusicBrainz(options);
  } else {
    people = (await listAniList(options)).filter((one) =>
      options.gender === "female"
        ? one.gender === "female"
        : options.gender === "male"
          ? one.gender === "male"
          : true,
    );
  }
  const min = options.ageMin;
  const max = options.ageMax;
  return people.filter(
    (one) =>
      externalListable(one) &&
      (min === undefined || one.age === undefined || one.age >= min) &&
      (max === undefined || one.age === undefined || one.age <= max),
  );
}

export async function externalPersonDetail(
  source: ExternalSource,
  id: string,
): Promise<ExternalPerson> {
  if (source === "tmdb") return toExternal(await tmdbPersonDetail(Number(id)));
  if (source === "musicbrainz") return detailMusicBrainz(id);
  return detailAniList(id);
}

const SOURCE_LABEL: Record<ExternalSource, string> = {
  tmdb: "TMDB",
  musicbrainz: "MusicBrainz",
  anilist: "AniList",
};

/** The roster line of a person from an outside source (the source and its id are written into the line). */
export function externalRosterLine(person: ExternalPerson, group: string): string {
  const parts = [
    clean(group, 40),
    clean(person.name, 40),
    clean(person.note, 120),
    person.profession ? clean(person.profession, 20) : "",
    person.birthday ? `誕生日=${person.birthday}` : "",
    person.place ? `出身地=${clean(person.place, 60)}` : "",
    person.photoUrl ? `写真=${person.photoUrl}` : "",
    `出典=${SOURCE_LABEL[person.source]} ${person.id}`,
  ];
  return parts.filter((part, index) => index < 4 || part).join(" | ");
}
