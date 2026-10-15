// waifu.im: a tagged collection of anime pictures with an open, key-free API. The character
// creator lists pictures by tag (character, work, genre) and uses the chosen one as the reference
// picture of "create from a photo". Only the list (addresses and tags) passes through the gateway;
// the app loads the pictures itself.

const API = "https://api.waifu.im";
const TAGS_CACHE_MS = 60 * 60_000;

export type WaifuTag = { slug: string; name: string; description: string; count: number };
export type WaifuImage = {
  id: number;
  url: string;
  width: number;
  height: number;
  adult: boolean;
  tags: string[];
  artists: string[];
  source?: string;
};

let tagsCache: { at: number; tags: WaifuTag[] } | undefined;

export function parseWaifuTags(body: unknown): WaifuTag[] {
  const items = (body as { items?: Array<Record<string, unknown>> })?.items;
  if (!Array.isArray(items)) return [];
  return items.flatMap((row): WaifuTag[] => {
    const slug = typeof row.slug === "string" ? row.slug : "";
    if (!slug) return [];
    return [
      {
        slug,
        name: String(row.name ?? slug),
        description: String(row.description ?? ""),
        count: Number(row.imageCount ?? 0),
      },
    ];
  });
}

export function parseWaifuImages(body: unknown): WaifuImage[] {
  const items = (body as { items?: Array<Record<string, any>> })?.items;
  if (!Array.isArray(items)) return [];
  return items.flatMap((row): WaifuImage[] => {
    if (typeof row.url !== "string" || !row.url.startsWith("https://")) return [];
    return [
      {
        id: Number(row.id),
        url: row.url,
        width: Number(row.width ?? 0),
        height: Number(row.height ?? 0),
        adult: row.isNsfw === true,
        tags: (row.tags ?? []).map((tag: { slug?: string }) => tag.slug ?? "").filter(Boolean),
        artists: (row.artists ?? [])
          .map((artist: { name?: string }) => artist.name ?? "")
          .filter(Boolean),
        source: typeof row.source === "string" ? row.source : undefined,
      },
    ];
  });
}

export async function waifuTags(fetchImpl: typeof fetch = fetch): Promise<WaifuTag[]> {
  if (tagsCache && Date.now() - tagsCache.at < TAGS_CACHE_MS) return tagsCache.tags;
  const res = await fetchImpl(`${API}/tags`, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(15_000),
  });
  if (!res.ok) throw new Error(`waifu.im HTTP ${res.status}`);
  const tags = parseWaifuTags(await res.json()).sort((a, b) => b.count - a.count);
  tagsCache = { at: Date.now(), tags };
  return tags;
}

/** Pictures with the tags; `adult` is "general" (all ages), "adult" (adult content only) or "all". */
export async function waifuImages(
  params: { tags: string[]; adult: "general" | "adult" | "all"; page: number },
  fetchImpl: typeof fetch = fetch,
): Promise<WaifuImage[]> {
  const query = new URLSearchParams({
    PageSize: "24",
    Page: String(Math.max(1, params.page)),
    IsNsfw: params.adult === "adult" ? "True" : params.adult === "all" ? "All" : "False",
  });
  for (const tag of params.tags.slice(0, 5)) query.append("IncludedTags", tag);
  const res = await fetchImpl(`${API}/images?${query.toString()}`, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`waifu.im HTTP ${res.status}`);
  return parseWaifuImages(await res.json());
}
