import { rememberHits, type CharacterHit } from "./anime-lookup.js";

// びしょげDB (bishogedb.com): characters of FANZA's visual-novel games, with a public JSON API (no key;
// robots.txt allows it). Name, reading, voice actor, role, one-line note, game and maker are text only:
// the pictures of the site belong to the game makers and are not fetched.

const API = "https://bishogedb.com/api/characters";
const CACHE_MS = 30 * 60_000;
const cache = new Map<string, { at: number; hits: CharacterHit[] }>();

type Row = {
  id?: number | string;
  name?: string;
  reading?: string | null;
  cv?: string | null;
  role?: string | null;
  tagline?: string | null;
  item_title?: string | null;
  item_maker?: string | null;
  release_at?: number | string | null;
};

export function parseBishoge(body: unknown): CharacterHit[] {
  const list = (body as { characters?: Row[] })?.characters;
  if (!Array.isArray(list)) return [];
  return list.flatMap((row): CharacterHit[] => {
    const id = Number(row.id);
    const name = row.name?.trim() ?? "";
    if (!Number.isFinite(id) || !name) return [];
    const seconds = Number(row.release_at);
    const released =
      Number.isFinite(seconds) && seconds > 0
        ? new Date(seconds * 1000).toLocaleDateString("ja-JP", {
            year: "numeric",
            month: "numeric",
            day: "numeric",
          })
        : "";
    const extra = [
      row.cv ? `CV ${row.cv}` : "",
      row.role ?? "",
      row.item_maker ?? "",
      released ? `発売 ${released}` : "",
    ]
      .filter(Boolean)
      .join("／");
    return [
      {
        source: "bishoge",
        id,
        name,
        englishName: row.reading?.trim() ?? "",
        works: row.item_title ? [row.item_title.trim()] : [],
        description: row.tagline?.trim() ?? "",
        extra,
        url: `https://bishogedb.com/characters/${id}`,
      },
    ];
  });
}

/** Characters whose name, reading, voice actor, game or maker match the words. Never throws: no answer is []. */
export async function searchBishoge(
  query: string,
  fetchImpl: typeof fetch = fetch,
): Promise<CharacterHit[]> {
  const key = query.trim().toLowerCase();
  if (!key) return [];
  const cached = cache.get(key);
  if (cached && Date.now() - cached.at < CACHE_MS) return cached.hits;
  try {
    const res = await fetchImpl(`${API}?limit=30&q=${encodeURIComponent(query.trim())}`, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    });
    if (!res.ok) return [];
    const hits = parseBishoge(await res.json());
    rememberHits(hits);
    if (hits.length > 0) cache.set(key, { at: Date.now(), hits });
    return hits;
  } catch {
    return [];
  }
}
