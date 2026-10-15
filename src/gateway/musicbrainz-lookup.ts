/** Public artist metadata, with one shared request queue for MusicBrainz's application limit. */
import { fetchPublicMetadata } from "./public-metadata-fetch.js";

export type MusicBrainzMembership = {
  artistId: string;
  name: string;
  begin?: string;
  end?: string;
  ended: boolean;
};

type Artist = {
  id?: string;
  name?: string;
  type?: string;
  relations?: {
    type?: string;
    direction?: string;
    "type-id"?: string;
    begin?: string | null;
    end?: string | null;
    ended?: boolean;
    artist?: { id?: string; name?: string; type?: string };
    url?: { resource?: string };
  }[];
};

export const isMusicBrainzId = (value: string): boolean =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

let queue: Promise<void> = Promise.resolve();
let lastRequest = 0;

function membershipDate(value: string | null | undefined): string | undefined {
  return value && /^\d{4}(?:-\d{2}){0,2}$/.test(value) ? value : undefined;
}

async function lookup(id: string, include: string): Promise<Artist> {
  if (!isMusicBrainzId(id)) {
    throw new Error("MusicBrainz の識別子が不正です");
  }
  // Reserve the start slot atomically before any network awaits, including concurrent callers.
  const turn = queue.then(async () => {
    const delay = Math.max(0, lastRequest + 1100 - Date.now());
    if (delay) {
      await new Promise<void>((resolve) => {
        setTimeout(resolve, delay);
      });
    }
    lastRequest = Date.now();
  });
  queue = turn.catch(() => {});
  await turn;
  const url = new URL(`https://musicbrainz.org/ws/2/artist/${id}`);
  url.search = new URLSearchParams({ inc: include, fmt: "json" }).toString();
  const result = await fetchPublicMetadata(url, {
    headers: {
      "User-Agent": "OpenClaw-public-subject-lookup/1.0 (+https://github.com/openclaw/openclaw)",
      Accept: "application/json",
    },
    signal: AbortSignal.timeout(15_000),
  });
  if (!result.ok) {
    throw new Error(`MusicBrainz の取得に失敗しました（HTTP ${result.status}）`);
  }
  const artist = (await result.json()) as Artist;
  if (artist.id?.toLowerCase() !== id.toLowerCase()) {
    throw new Error("MusicBrainz の識別子が一致しませんでした");
  }
  return artist;
}

/** The caller must obtain the group MBID from the group's canonical Wikidata P434 claim. */
export async function musicBrainzMembers(groupId: string): Promise<MusicBrainzMembership[]> {
  const group = await lookup(groupId, "artist-rels");
  if (group.type !== "Group") {
    return [];
  }
  return (group.relations ?? [])
    .slice(0, 500)
    .flatMap((relation) => {
      const artist = relation.artist;
      if (
        relation["type-id"] !== "5be4c609-9afa-4ea0-910b-12ffb71e3821" ||
        relation.direction !== "backward" ||
        artist?.type !== "Person" ||
        !artist.id ||
        !isMusicBrainzId(artist.id) ||
        !artist.name
      ) {
        return [];
      }
      return [
        {
          artistId: artist.id,
          name: artist.name.slice(0, 200),
          begin: membershipDate(relation.begin),
          end: membershipDate(relation.end),
          ended: relation.ended === true,
        },
      ];
    })
    .slice(0, 120);
}

/** Return fixed-host biography links, never fetch arbitrary websites named by public data. */
export async function musicBrainzBiography(id: string): Promise<{
  wikidataId?: string;
  wikipedia?: { language: "ja" | "en"; title: string };
}> {
  const artist = await lookup(id, "url-rels");
  if (artist.type !== "Person") {
    return {};
  }
  const links: { wikidataId?: string; wikipedia?: { language: "ja" | "en"; title: string } } = {};
  for (const relation of artist.relations ?? []) {
    if (!relation.url?.resource) {
      continue;
    }
    let url: URL;
    try {
      url = new URL(relation.url.resource);
    } catch {
      continue;
    }
    const wd = /^\/wiki\/(Q\d+)$/.exec(url.pathname);
    if (url.hostname === "www.wikidata.org" && wd) {
      links.wikidataId = wd[1];
    }
    if (url.hostname !== "ja.wikipedia.org" && url.hostname !== "en.wikipedia.org") {
      continue;
    }
    if (!url.pathname.startsWith("/wiki/")) {
      continue;
    }
    try {
      const title = decodeURIComponent(url.pathname.slice(6)).replaceAll("_", " ");
      if (
        title &&
        title.length <= 300 &&
        (!links.wikipedia || url.hostname === "ja.wikipedia.org")
      ) {
        links.wikipedia = { language: url.hostname === "ja.wikipedia.org" ? "ja" : "en", title };
      }
    } catch {
      // A malformed escaped path does not identify a biography.
    }
  }
  return links;
}

export function membershipText(membership: MusicBrainzMembership): string {
  const period = `${membership.begin || "開始不明"} ～ ${membership.end || (membership.ended ? "終了日不明" : "終了記録なし")}`;
  return `${membership.name}：${period}${membership.ended ? "（終了）" : ""}`;
}
