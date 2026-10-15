import { rememberHits, type CharacterHit } from "./anime-lookup.js";
import {
  isMusicBrainzId,
  membershipText,
  musicBrainzBiography,
  musicBrainzMembers,
  type MusicBrainzMembership,
} from "./musicbrainz-lookup.js";

type Language = "ja" | "en";
type Claim = { rank?: string; mainsnak?: { datavalue?: { value?: { id?: string } | string } } };
type Entity = { claims?: Record<string, Claim[]>; sitelinks?: Record<string, { title?: string }> };
type WikiPage = {
  pageid?: number;
  title?: string;
  extract?: string;
  fullurl?: string;
  thumbnail?: { source?: string };
  pageprops?: { wikibase_item?: string; disambiguation?: string };
};
const headers = {
  "User-Agent": "OpenClaw-public-subject-lookup/1.0 (+https://github.com/openclaw/openclaw)",
};

function entityClaimId(claim: Claim): string | undefined {
  const value = claim.mainsnak?.datavalue?.value;
  return typeof value === "object" ? value.id : undefined;
}

function artistId(entity: Entity | undefined): string | undefined {
  const ids = [
    ...new Set(
      (entity?.claims?.P434 ?? [])
        .filter((claim) => claim.rank !== "deprecated")
        .flatMap((claim) => {
          const value = claim.mainsnak?.datavalue?.value;
          return typeof value === "string" && isMusicBrainzId(value) ? [value.toLowerCase()] : [];
        }),
    ),
  ];
  return ids.length === 1 ? ids[0] : undefined;
}

async function json(url: URL): Promise<unknown> {
  const result = await fetch(url, { headers, signal: AbortSignal.timeout(15_000) });
  if (!result.ok) {
    throw new Error(`公開資料の取得に失敗しました（HTTP ${result.status}）`);
  }
  return result.json();
}

function wikiUrl(language: Language, values: Record<string, string>): URL {
  const url = new URL(`https://${language}.wikipedia.org/w/api.php`);
  url.search = new URLSearchParams({
    action: "query",
    format: "json",
    formatversion: "2",
    ...values,
  }).toString();
  return url;
}

async function entities(ids: string[]): Promise<Record<string, Entity>> {
  if (!ids.length) {
    return {};
  }
  const url = new URL("https://www.wikidata.org/w/api.php");
  url.search = new URLSearchParams({
    action: "wbgetentities",
    format: "json",
    ids: ids.slice(0, 40).join("|"),
    props: "claims|sitelinks",
  }).toString();
  return ((await json(url)) as { entities?: Record<string, Entity> }).entities ?? {};
}

async function pages(language: Language, values: Record<string, string>): Promise<WikiPage[]> {
  const body = await json(
    wikiUrl(language, {
      prop: "extracts|pageimages|info|pageprops",
      explaintext: "1",
      exchars: "1200",
      inprop: "url",
      piprop: "thumbnail",
      pithumbsize: "800",
      ...values,
    }),
  );
  return (body as { query?: { pages?: WikiPage[] } }).query?.pages ?? [];
}

function hit(
  page: WikiPage,
  language: Language,
  publicKind: "person" | "group",
): CharacterHit | undefined {
  if (
    !page.pageid ||
    !page.title ||
    !page.extract ||
    page.pageprops?.disambiguation !== undefined
  ) {
    return undefined;
  }
  return {
    source: language === "ja" ? "wikipedia-ja" : "wikipedia-en",
    id: page.pageid,
    name: page.title.replace(/\s*\([^)]*\)$/, ""),
    englishName: "",
    works: [],
    description: page.extract.slice(0, 3500),
    imageUrl: page.thumbnail?.source,
    url: page.fullurl ?? `https://${language}.wikipedia.org/wiki/${encodeURIComponent(page.title)}`,
    publicKind,
    wikidataId: page.pageprops?.wikibase_item,
    extra: "Wikipedia の公開資料。画像・人物設定は取り込み前に確認してください。",
  };
}

/** Search public biographies; verify human entities using Wikidata rather than name guesses. */
export async function searchPublicSubjects(
  query: string,
  kind: "person" | "group",
): Promise<CharacterHit[]> {
  const term = query.trim().slice(0, 150);
  if (!term) {
    return [];
  }
  for (const language of ["ja", "en"] as const) {
    const found = await pages(language, {
      generator: "search",
      gsrsearch: term,
      gsrlimit: "8",
      gsrnamespace: "0",
      exintro: "1",
    });
    const facts = await entities(
      found.flatMap((page) =>
        page.pageprops?.wikibase_item ? [page.pageprops.wikibase_item] : [],
      ),
    );
    const hits = found.flatMap((page) => {
      const human =
        facts[page.pageprops?.wikibase_item ?? ""]?.claims?.P31?.some(
          (claim) => entityClaimId(claim) === "Q5",
        ) ?? false;
      const group =
        !human &&
        /(?:バンド|グループ|声優ユニット|音楽ユニット)(?:である|。|です)|\b(?:is|are|was|were) (?:an? )?(?:[\w-]+ ){0,5}(?:band|musical group|idol group)\b/i.test(
          page.extract?.slice(0, 800) ?? "",
        );
      if (kind === "person" ? !human : !group) {
        return [];
      }
      const value = hit(page, language, kind);
      return value ? [value] : [];
    });
    if (hits.length) {
      rememberHits(hits);
      return hits;
    }
  }
  return [];
}

/** Verified biographies with Wikidata members and canonical MusicBrainz membership records. */
export async function publicGroupMembers(group: CharacterHit): Promise<CharacterHit[]> {
  if (group.publicKind !== "group" || !group.wikidataId || !/^Q\d+$/.test(group.wikidataId)) {
    return [];
  }
  const facts = await entities([group.wikidataId]);
  const ids = [
    ...new Set(
      (facts[group.wikidataId]?.claims?.P527 ?? [])
        .filter((claim) => claim.rank !== "deprecated")
        .flatMap((claim) => {
          const id = entityClaimId(claim);
          return id ? [id] : [];
        }),
    ),
  ].slice(0, 40);
  const members = await entities(ids);
  const hits: CharacterHit[] = [];
  for (const language of ["ja", "en"] as const) {
    const titles = ids
      .filter((id) => members[id]?.claims?.P31?.some((claim) => entityClaimId(claim) === "Q5"))
      .filter((id) => language === "ja" || !members[id]?.sitelinks?.jawiki?.title)
      .flatMap((id) => {
        const title = members[id]?.sitelinks?.[`${language}wiki`]?.title;
        return title ? [title] : [];
      });
    if (!titles.length) {
      continue;
    }
    for (const page of await pages(language, {
      titles: titles.join("|"),
      redirects: "1",
      exintro: "1",
    })) {
      const member = hit(page, language, "person");
      if (member) {
        hits.push({
          ...member,
          works: [group.name],
          extra: "公開資料の掲載メンバー（過去の所属を含む場合があります）",
        });
      }
    }
  }
  const groupArtistId = artistId(facts[group.wikidataId]);
  if (groupArtistId) {
    try {
      await supplementMembers(group, groupArtistId, members, hits);
    } catch (error) {
      const status = "MusicBrainz 補完を取得できませんでした。後でグループを再検索してください。";
      group.extra = status;
      for (const member of hits) {
        member.extra = `${member.extra ?? ""}\n${status}`;
      }
      if (!hits.length) {
        throw error;
      }
    }
  }
  rememberHits([...hits, group]);
  return hits;
}

async function supplementMembers(
  group: CharacterHit,
  groupArtistId: string,
  knownEntities: Record<string, Entity>,
  hits: CharacterHit[],
): Promise<void> {
  const memberships = await musicBrainzMembers(groupArtistId);
  if (!memberships.length) {
    return;
  }
  const source = `https://musicbrainz.org/artist/${groupArtistId}`;
  const byArtist = new Map<string, MusicBrainzMembership[]>();
  for (const membership of memberships) {
    const key = membership.artistId.toLowerCase();
    const rows = byArtist.get(key) ?? [];
    rows.push(membership);
    byArtist.set(key, rows);
  }
  const unresolved: string[] = [];
  let biographyRequests = 0;
  for (const [id, rows] of [...byArtist].slice(0, 40)) {
    let member = hits.find((value) => artistId(knownEntities[value.wikidataId ?? ""]) === id);
    // A biography linked by MusicBrainz supplies canonical identity even without a P527 claim.
    if (!member && biographyRequests < 12) {
      biographyRequests++;
      const links = await musicBrainzBiography(id);
      let biography = links.wikipedia;
      if (links.wikidataId) {
        const record = (await entities([links.wikidataId]))[links.wikidataId];
        const title = record?.sitelinks?.jawiki?.title ?? record?.sitelinks?.enwiki?.title;
        if (title) {
          biography = { language: record?.sitelinks?.jawiki?.title ? "ja" : "en", title };
        }
        member = hits.find((value) => value.wikidataId === links.wikidataId);
      }
      if (!member && biography) {
        const found = await pages(biography.language, {
          titles: biography.title,
          redirects: "1",
          exintro: "1",
        });
        const candidate = found[0] && hit(found[0], biography.language, "person");
        if (candidate?.wikidataId) {
          const record = (await entities([candidate.wikidataId]))[candidate.wikidataId];
          if (record?.claims?.P31?.some((claim) => entityClaimId(claim) === "Q5")) {
            member = hits.find((value) => value.wikidataId === candidate.wikidataId) ?? candidate;
            if (!hits.includes(member)) {
              hits.push(member);
            }
          }
        }
      }
    }
    const text = rows.slice(0, 12).map(membershipText).join("\n").slice(0, 3000);
    if (member) {
      member.works = [...new Set([...member.works, group.name])];
      const facts = `MusicBrainz 掲載の所属期間（最新の在籍を保証しません）\n${text}\n資料元：${source}`;
      member.extra = `${member.extra ?? ""}\n${facts}`;
      member.description = `${member.description}\n\n${group.name}\n${facts}`;
    } else {
      unresolved.push(`${text} https://musicbrainz.org/artist/${id}`);
    }
  }
  // Preserve sourced names lacking a biography as group facts rather than fabricate page IDs.
  group.extra =
    `MusicBrainz 所属情報：${source}${unresolved.length ? `\n人物ページ未連携：\n${unresolved.join("\n")}` : ""}`.slice(
      0,
      6000,
    );
}
