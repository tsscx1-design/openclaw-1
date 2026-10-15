import { cleanDescription, rememberHits, type CharacterHit } from "./anime-lookup.js";
import { fetchPublicMetadata } from "./public-metadata-fetch.js";

const ENDPOINT = "https://api.vndb.org/kana";
// Select text explicitly: never request character images or VN screenshots.
const CHARACTER_FIELDS =
  "name,original,aliases,description,age,gender,height,weight,blood_type,vns{id,title,alttitle,role,spoiler}";
type Vn = { id: string; title: string; alttitle?: string | null; role?: string; spoiler?: number };
type VndbCharacter = {
  id: string;
  name: string;
  original?: string | null;
  aliases?: string[];
  description?: string | null;
  age?: number | null;
  gender?: Array<string | null> | null;
  height?: number | null;
  weight?: number | null;
  blood_type?: string | null;
  vns?: Vn[];
};
type VoiceVn = Vn & {
  va?: Array<{
    character?: { id: string };
    staff?: { name?: string; original?: string | null };
    note?: string | null;
  }>;
};
const calls: number[] = [];

async function query<T>(
  path: "character" | "vn",
  filters: unknown[],
  fields: string,
  results: number,
  fetchImpl: typeof fetchPublicMetadata,
): Promise<T[]> {
  const now = Date.now();
  while (calls.length && now - calls[0]! >= 5 * 60_000) {
    calls.shift();
  }
  // A shared bound for search and detail calls, below Kana's 200 requests / five minutes.
  if (calls.length >= 60) {
    throw new Error("VNDB の検索回数が多いため、5分ほど待って再検索してください");
  }
  calls.push(now);
  const response = await fetchImpl(`${ENDPOINT}/${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": "OpenClaw-character-lookup/1.0 (https://github.com/openclaw/openclaw)",
    },
    body: JSON.stringify({ filters, fields, results, sort: "id" }),
    signal: AbortSignal.timeout(15_000),
  }).catch((cause: unknown) => {
    throw new Error("VNDB に接続できませんでした。Gateway の通信環境を確認して再検索してください", {
      cause,
    });
  });
  if (!response.ok) {
    if (response.status === 429) {
      throw new Error("VNDB の利用制限に達しました。しばらく待って再検索してください");
    }
    throw new Error(
      `VNDB に接続できませんでした（HTTP ${response.status}）。時間を置いて再検索してください`,
    );
  }
  const body = (await response.json()) as { results?: T[] };
  if (!Array.isArray(body.results)) {
    throw new Error("VNDB の応答を読み取れませんでした");
  }
  return body.results.slice(0, results);
}

function plain(value: string | null | undefined): string {
  return cleanDescription(
    (value ?? "")
      .replace(/\[spoiler\][\s\S]*?\[\/spoiler\]/gi, "")
      .replace(/\[url=[^\]]*\]([\s\S]*?)\[\/url\]/gi, "$1")
      .replace(/\[\/?(?:b|i|u|s|code|quote|raw|url)(?:=[^\]]*)?\]/gi, ""),
  );
}

function toHit(row: VndbCharacter): CharacterHit | undefined {
  if (!/^c[1-9]\d*$/.test(row.id)) {
    return undefined;
  }
  const id = Number(row.id.slice(1));
  if (!Number.isSafeInteger(id) || !row.name) {
    return undefined;
  }
  const works = [
    ...new Set(
      (row.vns ?? [])
        .filter((vn) => vn.spoiler === 0)
        .map((vn) => vn.alttitle || vn.title)
        .filter(Boolean),
    ),
  ].slice(0, 10);
  const facts = [
    row.aliases?.length ? `別名: ${row.aliases.slice(0, 8).join("、")}` : "",
    row.height ? `身長: ${row.height}cm` : "",
    row.weight ? `体重: ${row.weight}kg` : "",
    row.blood_type ? `血液型: ${row.blood_type.toUpperCase()}型` : "",
  ].filter(Boolean);
  return {
    source: "vndb",
    externalId: row.id,
    id,
    name: row.original || row.name,
    englishName: row.name,
    gender: row.gender?.[0] === "f" ? "Female" : row.gender?.[0] === "m" ? "Male" : undefined,
    age: row.age == null ? undefined : String(row.age),
    works,
    description: [plain(row.description), ...facts].filter(Boolean).join("\n").slice(0, 1800),
    extra: "資料元: VNDB（テキスト情報のみ）",
    url: `https://vndb.org/${row.id}`,
  };
}

export async function searchVndbCharacters(
  text: string,
  fetchImpl: typeof fetchPublicMetadata = fetchPublicMetadata,
): Promise<CharacterHit[]> {
  const search = text.trim().slice(0, 150);
  if (!search) {
    return [];
  }
  const rows = await query<VndbCharacter>(
    "character",
    ["or", ["search", "=", search], ["vn", "=", ["search", "=", search]]],
    CHARACTER_FIELDS,
    20,
    fetchImpl,
  );
  const hits = rows.map(toHit).filter((hit): hit is CharacterHit => Boolean(hit));
  rememberHits(hits);
  return hits;
}

/** Resolve by canonical ID even after the transient search-result cache has expired. */
export async function vndbCharacterDetails(
  id: number,
  fetchImpl: typeof fetchPublicMetadata = fetchPublicMetadata,
): Promise<{ hit: CharacterHit; imageUrls: string[] } | undefined> {
  if (!Number.isSafeInteger(id) || id <= 0) {
    return undefined;
  }
  const externalId = `c${id}`;
  const rows = await query<VndbCharacter>(
    "character",
    ["id", "=", externalId],
    CHARACTER_FIELDS,
    1,
    fetchImpl,
  );
  const row = rows[0];
  if (!row) {
    return undefined;
  }
  const hit = toHit(row);
  if (!hit || hit.externalId !== externalId) {
    return undefined;
  }
  const works = [
    ...new Set(
      (row.vns ?? [])
        .filter((vn) => vn.spoiler === 0 && /^v[1-9]\d*$/.test(vn.id))
        .map((vn) => vn.id),
    ),
  ].slice(0, 3);
  if (works.length) {
    const predicates = works.map((work) => ["id", "=", work]);
    const voices = await query<VoiceVn>(
      "vn",
      works.length === 1 ? predicates[0]! : ["or", ...predicates],
      "title,alttitle,va{character.id,staff{name,original},note}",
      works.length,
      fetchImpl,
    );
    const credits = voices
      .flatMap((vn) =>
        (vn.va ?? [])
          .filter((voice) => voice.character?.id === externalId)
          .slice(0, 4)
          .map((voice) => {
            const actor = voice.staff?.original || voice.staff?.name;
            return actor
              ? `声優: ${actor}（${vn.alttitle || vn.title}${voice.note ? `・${plain(voice.note)}` : ""}）`
              : "";
          }),
      )
      .filter(Boolean)
      .slice(0, 8);
    hit.extra = [hit.extra, ...credits].join("\n").slice(0, 1800);
  }
  rememberHits([hit]);
  return { hit, imageUrls: [] };
}
