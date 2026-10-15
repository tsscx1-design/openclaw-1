import { rememberedHit, type CharacterHit } from "./anime-lookup.js";
import { fetchPublicMetadata } from "./public-metadata-fetch.js";

export type VoiceSampleLink = {
  title: string;
  url: string;
  kind: "official-site" | "search" | "public-audio";
  /** Confirms published identity association; public audio is not necessarily official. */
  verified: boolean;
  note: string;
  directAudioUrl?: string;
  sourceUrl?: string;
  license?: string;
  licenseUrl?: string;
  mime?: string;
  size?: number;
  attribution?: string;
  evidenceProperty?: "P990" | "P51";
};

export type VoiceSampleSearchResult = {
  name: string;
  target: "person" | "character";
  links: VoiceSampleLink[];
  note: string;
};

type Claim = { rank?: string; mainsnak?: { datavalue?: { value?: unknown } } };
type Entity = { claims?: Record<string, Claim[]> };
type CommonsInfo = {
  url?: string;
  descriptionurl?: string;
  mime?: string;
  size?: number;
  extmetadata?: Record<string, { value?: string }>;
};
type VoiceWork = {
  id: string;
  title: string;
  alttitle?: string | null;
  extlinks?: Array<{ name?: string; url?: string }>;
  va?: Array<{
    character?: { id: string };
    staff?: { name?: string; original?: string | null };
  }>;
};

const HEADERS = {
  Accept: "application/json",
  "User-Agent": "OpenClaw-voice-sample-search/1.0 (https://github.com/openclaw/openclaw)",
};
const calls: number[] = [];

function safeUrl(value: unknown): string | undefined {
  if (typeof value !== "string" || value.length > 2048) {
    return undefined;
  }
  try {
    const url = new URL(value);
    if (!["https:", "http:"].includes(url.protocol) || url.username || url.password) {
      return undefined;
    }
    // Returned links are user-opened only. Never fetch arbitrary site content here.
    return url.href;
  } catch {
    return undefined;
  }
}

function searchLink(title: string, terms: string, note: string): VoiceSampleLink {
  return {
    title,
    url: `https://www.google.com/search?q=${encodeURIComponent(terms.slice(0, 300))}`,
    kind: "search",
    verified: false,
    note,
  };
}

async function entities(ids: string[]): Promise<Record<string, Entity>> {
  const selected = [...new Set(ids)].filter((id) => /^Q[1-9]\d*$/.test(id)).slice(0, 3);
  if (!selected.length) {
    return {};
  }
  const url = new URL("https://www.wikidata.org/w/api.php");
  url.search = new URLSearchParams({
    action: "wbgetentities",
    ids: selected.join("|"),
    props: "claims",
    format: "json",
  }).toString();
  const response = await fetchPublicMetadata(url, {
    headers: HEADERS,
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    throw new Error(`Wikidata HTTP ${response.status}`);
  }
  const body = (await response.json()) as { entities?: Record<string, Entity> };
  return body.entities ?? {};
}

function officialLinks(
  entity: Entity | undefined,
  name: string,
  context: string,
): VoiceSampleLink[] {
  return (entity?.claims?.P856 ?? [])
    .filter((claim) => claim.rank !== "deprecated")
    .flatMap((claim): VoiceSampleLink[] => {
      const url = safeUrl(claim.mainsnak?.datavalue?.value);
      return url
        ? [
            {
              title: `${name}：公式サイト`,
              url,
              kind: "official-site",
              verified: true,
              note: `${context}。Wikidata P856に掲載。音声サンプルの有無・現在のサイト運営者は未確認です。`,
            },
          ]
        : [];
    })
    .slice(0, 3);
}

function metadataText(value: string | undefined): string {
  return (value ?? "")
    .replace(/<[^>]*>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 1000);
}

async function publicAudioLinks(
  entity: Entity,
  name: string,
  qid: string,
): Promise<VoiceSampleLink[]> {
  const evidence = new Map<string, "P990" | "P51">();
  // P990 is the spoken-voice relation; P51 can also contain other kinds of audio.
  for (const property of ["P990", "P51"] as const) {
    for (const claim of entity.claims?.[property] ?? []) {
      const value = claim.mainsnak?.datavalue?.value;
      if (
        claim.rank !== "deprecated" &&
        typeof value === "string" &&
        value.length > 0 &&
        value.length <= 240 &&
        !value.includes("|") &&
        !evidence.has(value)
      ) {
        evidence.set(value, property);
      }
    }
  }
  const filenames = [...evidence.keys()].slice(0, 4);
  if (!filenames.length) {
    return [];
  }
  const url = new URL("https://commons.wikimedia.org/w/api.php");
  url.search = new URLSearchParams({
    action: "query",
    format: "json",
    titles: filenames.map((file) => `File:${file}`).join("|"),
    prop: "imageinfo",
    iiprop: "url|mime|size|extmetadata",
    iiextmetadatafilter:
      "LicenseShortName|LicenseUrl|Artist|Credit|AttributionRequired|ImageDescription",
  }).toString();
  const response = await fetchPublicMetadata(url, {
    headers: HEADERS,
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    throw new Error(`Commons HTTP ${response.status}`);
  }
  const body = (await response.json()) as {
    query?: { pages?: Record<string, { title?: string; imageinfo?: CommonsInfo[] }> };
  };
  const links: VoiceSampleLink[] = [];
  for (const page of Object.values(body.query?.pages ?? {})) {
    const info = page.imageinfo?.[0];
    if (!info || !info.mime?.startsWith("audio/") || !info.size || info.size > 20 * 1024 * 1024) {
      continue;
    }
    const directAudioUrl = safeUrl(info.url);
    const sourceUrl = safeUrl(info.descriptionurl);
    if (!directAudioUrl || !sourceUrl) {
      continue;
    }
    const audio = new URL(directAudioUrl);
    const source = new URL(sourceUrl);
    if (
      audio.protocol !== "https:" ||
      audio.hostname !== "upload.wikimedia.org" ||
      !audio.pathname.startsWith("/wikipedia/commons/") ||
      source.protocol !== "https:" ||
      source.hostname !== "commons.wikimedia.org" ||
      !source.pathname.startsWith("/wiki/File:")
    ) {
      continue;
    }
    // Commons adds tracking parameters to imageinfo URLs; retain a stable identity for import.
    audio.search = "";
    audio.hash = "";
    const metadata = info.extmetadata ?? {};
    const license = metadataText(metadata.LicenseShortName?.value);
    const licenseUrl = safeUrl(metadata.LicenseUrl?.value);
    if (!license || !licenseUrl) {
      continue;
    }
    const attribution = [metadataText(metadata.Artist?.value), metadataText(metadata.Credit?.value)]
      .filter(Boolean)
      .join(" / ")
      .slice(0, 1500);
    links.push({
      title: `${name}：${(page.title ?? "公開音声").replace(/^File:/, "").slice(0, 180)}`,
      url: sourceUrl,
      kind: "public-audio",
      verified: true,
      directAudioUrl: audio.href,
      sourceUrl,
      license,
      licenseUrl,
      mime: info.mime,
      size: info.size,
      attribution,
      evidenceProperty: evidence.get((page.title ?? "").replace(/^File:/, "")),
      note: `Wikidata ${qid}の${evidence.get((page.title ?? "").replace(/^File:/, "")) ?? "音声資料"}に紐づくCommons公開音声資料です。公式配布とは限りません。別話者・音楽が含まれる可能性があり、本人の声だけとは保証しません。ライセンス: ${license}。`,
    });
  }
  return links.slice(0, 4);
}

async function kana<T>(
  path: "character" | "vn",
  filters: unknown[],
  fields: string,
  results: number,
): Promise<T[]> {
  const now = Date.now();
  while (calls.length && now - calls[0]! > 300_000) {
    calls.shift();
  }
  if (calls.length >= 30) {
    throw new Error("VNDBへの音声資料検索が多いため、5分ほど待ってください");
  }
  calls.push(now);
  const response = await fetchPublicMetadata(`https://api.vndb.org/kana/${path}`, {
    method: "POST",
    headers: { ...HEADERS, "Content-Type": "application/json" },
    body: JSON.stringify({ filters, fields, results, sort: "id" }),
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    throw new Error(`VNDB HTTP ${response.status}`);
  }
  const body = (await response.json()) as { results?: T[] };
  return Array.isArray(body.results) ? body.results.slice(0, results) : [];
}

async function vndbWorks(hit: CharacterHit): Promise<VoiceWork[]> {
  const canonicalId = `c${hit.id}`;
  if (hit.externalId !== canonicalId) {
    throw new Error("VNDBのキャラを再検索して選択してください");
  }
  const rows = await kana<{ id: string; vns?: Array<{ id: string; spoiler?: number }> }>(
    "character",
    ["id", "=", canonicalId],
    "vns{id,spoiler}",
    1,
  );
  if (rows[0]?.id !== canonicalId) {
    throw new Error("VNDBのキャラ資料が見つかりません");
  }
  const ids = [
    ...new Set(
      (rows[0].vns ?? [])
        .filter((vn) => vn.spoiler === 0 && /^v[1-9]\d*$/.test(vn.id))
        .map((vn) => vn.id),
    ),
  ].slice(0, 3);
  if (!ids.length) {
    return [];
  }
  const filters = ids.map((id) => ["id", "=", id]);
  return kana<VoiceWork>(
    "vn",
    ids.length === 1 ? filters[0]! : ["or", ...filters],
    "title,alttitle,extlinks{name,url},va{character.id,staff{name,original}}",
    ids.length,
  );
}

/** Resolve a selected canonical result; never substitute a similarly named person. */
export async function searchVoiceSamples(
  source: string,
  id: number,
  _query: string,
): Promise<VoiceSampleSearchResult> {
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error("対象の人物・キャラを選択してください");
  }
  const hit = rememberedHit(source, id);
  if (!hit || hit.publicKind === "group") {
    throw new Error("人物・キャラを再検索して選択してください");
  }
  const isPerson =
    (source === "wikipedia-ja" || source === "wikipedia-en") && hit.publicKind === "person";
  const name = hit.name.slice(0, 150);
  const links: VoiceSampleLink[] = [];
  const notes: string[] = [];
  if (isPerson) {
    try {
      if (hit.wikidataId && /^Q[1-9]\d*$/.test(hit.wikidataId)) {
        const data = await entities([hit.wikidataId]);
        const entity = data[hit.wikidataId];
        const human = entity?.claims?.P31?.some(
          (claim) =>
            claim.rank !== "deprecated" &&
            typeof claim.mainsnak?.datavalue?.value === "object" &&
            claim.mainsnak.datavalue.value !== null &&
            "id" in claim.mainsnak.datavalue.value &&
            claim.mainsnak.datavalue.value.id === "Q5",
        );
        if (human && entity) {
          links.push(...officialLinks(entity, name, "選択した実在人物の公式サイト資料"));
          try {
            links.push(...(await publicAudioLinks(entity, name, hit.wikidataId)));
          } catch {
            notes.push("Commonsの音声資料を取得できませんでした。時間を置いて再検索してください。");
          }
        }
      }
    } catch {
      notes.push("公式サイト情報を取得できませんでした。時間を置いて再検索してください。");
    }
    links.push(
      searchLink(
        `${name}：声のサンプルを検索`,
        `"${name}" 公式 ボイスサンプル`,
        "未確認の検索結果です。同名人物と、本人の普段の声・役の演技を区別してください。",
      ),
    );
  } else {
    if (source === "vndb") {
      try {
        const works = await vndbWorks(hit);
        const identities = works
          .flatMap((work) =>
            (work.extlinks ?? []).flatMap((link) => {
              const match =
                link.name === "wikidata"
                  ? /^https:\/\/www\.wikidata\.org\/wiki\/(Q[1-9]\d*)$/.exec(link.url ?? "")
                  : null;
              return match ? [{ work, qid: match[1]! }] : [];
            }),
          )
          .slice(0, 3);
        const data = await entities(identities.map((entry) => entry.qid));
        for (const { work, qid } of identities) {
          links.push(
            ...officialLinks(
              data[qid],
              work.alttitle || work.title,
              `VNDB ${work.id}に紐づく作品のサイト。対象キャラ: ${name}`,
            ),
          );
        }
        for (const work of works) {
          const title = (work.alttitle || work.title).slice(0, 150);
          links.push(
            searchLink(
              `${name}／${title}：キャラ音声を検索`,
              `"${title}" "${name}" 公式 ボイスサンプル`,
              "対象キャラの演技を探す未確認の検索結果です。作品・版・言語を確認してください。",
            ),
          );
          const actors = [
            ...new Set(
              (work.va ?? [])
                .filter((voice) => voice.character?.id === hit.externalId)
                .map((voice) => voice.staff?.original || voice.staff?.name)
                .filter((actor): actor is string => Boolean(actor)),
            ),
          ].slice(0, 2);
          for (const actor of actors) {
            links.push(
              searchLink(
                `${actor}：声優本人のサンプルを検索`,
                `"${actor.slice(0, 150)}" 公式 ボイスサンプル`,
                `VNDBの${title}の配役資料。声優本人の声は${name}の演技と同じとは限りません。検索結果は未確認です。`,
              ),
            );
          }
        }
      } catch {
        notes.push(
          "VNDBまたは作品の公式サイト情報を取得できませんでした。時間を置いて再検索してください。",
        );
      }
    }
    if (!links.some((link) => link.kind === "search")) {
      links.push(
        searchLink(
          `${name}：キャラ音声を検索`,
          `${hit.works.slice(0, 1).join(" ")} "${name}" 公式 ボイスサンプル`,
          "未確認の検索結果です。対象キャラ・作品・版・言語を確認してください。声優本人の声と区別します。",
        ),
      );
    }
  }
  return {
    name,
    target: isPerson ? "person" : "character",
    links: [...new Map(links.map((link) => [link.url, link])).values()].slice(0, 12),
    note: [
      ...notes,
      "公式サイト・Commons公開音声資料・未確認の検索リンクを区別しています。公開音声は利用条件への同意後、選択して取り込めます。その他のリンクは外部サイトで確認してください。",
    ].join("\n"),
  };
}
