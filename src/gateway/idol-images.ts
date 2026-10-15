import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import { searchTmdbImages } from "./idol-external.js";
import { idolRosterFile, looksLikeIdolImage } from "./idol-master.js";
import { pictureFromUrl } from "./idol-photo-url.js";

// Picture search for one person of the fan book, by the person's name: Wikimedia Commons and Openverse (both are
// open-license picture collections with an official API). Nothing is crawled; the user looks at the list and picks one.
// The search is for the people of the fan book (the name comes from a member), and a hit may be a different person
// with the same name: the user decides.

export type PersonImage = {
  id: string;
  source: "commons" | "openverse" | "tmdb" | "google";
  title: string;
  license: string;
  creator: string;
  /** Small picture for the list (https). */
  thumbUrl: string;
  /** The picture that is saved when picked (https, about 960px). */
  imageUrl: string;
  pageUrl: string;
};

const UA = "OpenClaw-idol-master/1.0 (personal use; picture search for one fan book member)";
const API_HOSTS = [
  "commons.wikimedia.org",
  "api.openverse.org",
  "www.wikidata.org",
  "www.googleapis.com",
  "discoveryengine.googleapis.com",
];
export const IMAGE_HOSTS = [
  "thumb.wikimedia.org",
  "upload.wikimedia.org",
  "commons.wikimedia.org",
  "api.openverse.org",
  "image.tmdb.org",
];
const MAX_IMAGE_BYTES = 6 * 1024 * 1024;

let lastCall = 0;
async function getJson(url: string): Promise<unknown> {
  const wait = lastCall + 700 - Date.now();
  if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
  lastCall = Date.now();
  const { response, release } = await fetchWithSsrFGuard({
    url,
    requireHttps: true,
    maxRedirects: 1,
    timeoutMs: 25_000,
    policy: { hostnameAllowlist: API_HOSTS },
    init: { headers: { "User-Agent": UA, Accept: "application/json" } },
  });
  try {
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return JSON.parse(
      (
        await readResponseWithLimit(response, 2_000_000, {
          timeoutMs: 25_000,
          chunkTimeoutMs: 10_000,
        })
      ).toString("utf-8"),
    );
  } finally {
    await release();
  }
}

const squash = (value: string): string =>
  value
    .normalize("NFKC")
    .replace(/[\s　・･·_\-.]+/g, "")
    .toLowerCase();

/** Whether a text names the person (spaces, dots, full-width and case are ignored). */
export function mentions(text: string, name: string): boolean {
  const key = squash(name);
  return key.length >= 2 && squash(text).includes(key);
}

const stripTags = (value: string): string =>
  value
    .replace(/<[^>]*>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, " ")
    .trim();

type CommonsPage = {
  pageid?: number;
  title?: string;
  imageinfo?: Array<{
    thumburl?: string;
    descriptionurl?: string;
    extmetadata?: Record<string, { value?: string } | undefined>;
  }>;
};

function commonsRows(pages: CommonsPage[], onlyAbout?: string): PersonImage[] {
  const rows: PersonImage[] = [];
  for (const page of pages) {
    const info = page.imageinfo?.[0];
    const thumb = info?.thumburl;
    if (!thumb || !/^https:\/\/(thumb|upload)\.wikimedia\.org\//.test(thumb)) continue;
    const meta = info?.extmetadata ?? {};
    // the picture must be about the person: her name is in the file name or in the description
    if (onlyAbout) {
      const about = `${page.title ?? ""} ${stripTags(meta.ImageDescription?.value ?? "")} ${stripTags(meta.ObjectName?.value ?? "")}`;
      if (!mentions(about, onlyAbout)) continue;
    }
    rows.push({
      id: `commons:${page.pageid ?? page.title}`,
      source: "commons",
      title: (page.title ?? "").replace(/^File:/, "").replace(/\.[A-Za-z]+$/, ""),
      license: stripTags(meta.LicenseShortName?.value ?? "") || "（ライセンス不明）",
      creator: stripTags(meta.Artist?.value ?? "").slice(0, 60),
      thumbUrl: thumb,
      // Wikimedia only makes thumbnails in fixed widths (960 is one of them)
      imageUrl: thumb.replace(/\/\d+px-/, "/960px-").replace(/\?.*$/, ""),
      pageUrl: info?.descriptionurl ?? "",
    });
  }
  return rows;
}

/** The pictures of a person on Commons: the file pages that carry her name (as one phrase) in the file name or the description. */
export async function searchCommonsImages(
  name: string,
  limit = 12,
  offset = 0,
): Promise<PersonImage[]> {
  const body = (await getJson(
    `https://commons.wikimedia.org/w/api.php?${new URLSearchParams({
      format: "json",
      formatversion: "2",
      action: "query",
      generator: "search",
      // the name as one phrase (without quotes the words are looked for one by one and anything matches)
      gsrsearch: `"${name.replace(/"/g, " ")}" filetype:bitmap`,
      gsrnamespace: "6",
      gsrlimit: String(limit),
      gsroffset: String(offset),
      prop: "imageinfo",
      iiprop: "url|extmetadata",
      iiurlwidth: "320",
    }).toString()}`,
  )) as { query?: { pages?: CommonsPage[] } };
  return commonsRows(body.query?.pages ?? [], name);
}

/**
 * The pictures in the Commons category of a person (Wikidata "Commons category", P373): they are her pictures for sure.
 * Needs her Wikidata item (the roster line of a person from Wikidata names it in its source).
 */
export async function searchCommonsCategoryImages(
  qid: string,
  limit = 12,
  offset = 0,
): Promise<PersonImage[]> {
  if (!/^Q\d+$/.test(qid)) return [];
  const entity = (await getJson(
    `https://www.wikidata.org/w/api.php?${new URLSearchParams({ format: "json", action: "wbgetentities", ids: qid, props: "claims" }).toString()}`,
  )) as {
    entities?: Record<
      string,
      { claims?: Record<string, Array<{ mainsnak?: { datavalue?: { value?: unknown } } }>> }
    >;
  };
  const category = entity.entities?.[qid]?.claims?.P373?.[0]?.mainsnak?.datavalue?.value;
  if (typeof category !== "string" || !category.trim()) return [];
  const body = (await getJson(
    `https://commons.wikimedia.org/w/api.php?${new URLSearchParams({
      format: "json",
      formatversion: "2",
      action: "query",
      generator: "categorymembers",
      gcmtitle: `Category:${category}`,
      gcmtype: "file",
      gcmlimit: String(Math.min(50, limit + offset)),
      prop: "imageinfo",
      iiprop: "url|extmetadata",
      iiurlwidth: "320",
    }).toString()}`,
  )) as { query?: { pages?: CommonsPage[] } };
  const pages = (body.query?.pages ?? []).slice(offset, offset + limit);
  return commonsRows(pages);
}

type OpenverseRow = {
  id?: string;
  title?: string;
  license?: string;
  license_version?: string;
  creator?: string;
  thumbnail?: string;
  foreign_landing_url?: string;
};

/** The pictures of a person on Openverse (open-license pictures from Flickr, Wikimedia and others). */
export async function searchOpenverseImages(
  name: string,
  limit = 12,
  page = 1,
): Promise<PersonImage[]> {
  const body = (await getJson(
    `https://api.openverse.org/v1/images/?${new URLSearchParams({ q: `"${name.replace(/"/g, " ")}"`, page_size: String(limit), page: String(page), mature: "false" }).toString()}`,
  )) as { results?: OpenverseRow[] };
  const rows: PersonImage[] = [];
  for (const row of body.results ?? []) {
    if (!row.id || !row.thumbnail || !/^https:\/\/api\.openverse\.org\//.test(row.thumbnail))
      continue;
    // only the pictures that name the person (the title, or the tags of the picture)
    const tags =
      (row as { tags?: Array<{ name?: string }> }).tags?.map((tag) => tag.name ?? "").join(" ") ??
      "";
    if (!mentions(`${row.title ?? ""} ${tags}`, name)) continue;
    rows.push({
      id: `openverse:${row.id}`,
      source: "openverse",
      title: (row.title ?? "").slice(0, 80),
      license:
        `${(row.license ?? "").toUpperCase()} ${row.license_version ?? ""}`.trim() ||
        "（ライセンス不明）",
      creator: (row.creator ?? "").slice(0, 60),
      thumbUrl: row.thumbnail,
      imageUrl: row.thumbnail,
      pageUrl: row.foreign_landing_url ?? "",
    });
  }
  return rows;
}

/** Whether Google picture search is set up (a Programmable Search Engine with picture search, in ~/.openclaw/.env). */
export function googleImagesReady(): boolean {
  return Boolean(process.env.GOOGLE_CSE_KEY?.trim() && process.env.GOOGLE_CSE_ID?.trim());
}

type GoogleRow = {
  title?: string;
  link?: string;
  displayLink?: string;
  image?: { thumbnailLink?: string; contextLink?: string };
};

/**
 * Google picture search through its official API (Custom Search JSON API: a key and a search engine id; 10 pictures a
 * page, the first 100 hits of a search). Google's own pages are never read. Each picture belongs to another site, so
 * the license is unknown: the list says so.
 */
export async function searchGoogleImages(name: string, page = 1): Promise<PersonImage[]> {
  const key = process.env.GOOGLE_CSE_KEY?.trim();
  const cx = process.env.GOOGLE_CSE_ID?.trim();
  if (!key || !cx) return [];
  const start = (Math.max(1, Math.trunc(page)) - 1) * 10 + 1;
  if (start > 91) return [];
  const body = (await getJson(
    `https://www.googleapis.com/customsearch/v1?${new URLSearchParams({ key, cx, q: name, searchType: "image", num: "10", start: String(start), safe: "off", hl: "ja", gl: "jp" }).toString()}`,
  )) as { items?: GoogleRow[] };
  const rows: PersonImage[] = [];
  for (const row of body.items ?? []) {
    const thumb = row.image?.thumbnailLink ?? "";
    if (
      !row.link ||
      !/^https:\/\//.test(row.link) ||
      !/^https:\/\/encrypted-tbn\d\.gstatic\.com\//.test(thumb)
    )
      continue;
    rows.push({
      id: `google:${row.link}`,
      source: "google",
      title: (row.title ?? "").slice(0, 80),
      license: "（ライセンス不明・出典サイトの規約に従う）",
      creator: (row.displayLink ?? "").slice(0, 60),
      thumbUrl: thumb,
      imageUrl: row.link,
      pageUrl: row.image?.contextLink ?? "",
    });
  }
  return rows;
}

// ---------------------------------------------------------------- Google Cloud (Vertex AI Search, website search)
// The user's own search app over the sites the user registered in the Google Cloud console. Nothing here chooses sites: the
// list lives in the console. The access token comes from the user's gcloud login (no key is stored by OpenClaw).
// ~/.openclaw/.env: VERTEX_SEARCH_PROJECT, VERTEX_SEARCH_ENGINE, VERTEX_SEARCH_ACCOUNT (the gcloud account to use).

function vertexConfig(): { project: string; engine: string; account: string } | undefined {
  const project = process.env.VERTEX_SEARCH_PROJECT?.trim() ?? "";
  const engine = process.env.VERTEX_SEARCH_ENGINE?.trim() ?? "";
  const account = process.env.VERTEX_SEARCH_ACCOUNT?.trim() ?? "";
  if (
    !/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(project) ||
    !/^[A-Za-z0-9_-]{1,100}$/.test(engine) ||
    !/^[\w.+-]+@[\w.-]+$/.test(account)
  )
    return undefined;
  return { project, engine, account };
}

/** Whether Vertex AI Search is set up (see the comment above). */
export function vertexImagesReady(): boolean {
  return vertexConfig() !== undefined;
}

let vertexToken: { account: string; value: string; until: number } | undefined;

/** An access token of the gcloud login of that account (kept for 45 minutes; the token is never logged). */
async function vertexAccessToken(account: string): Promise<string> {
  if (vertexToken && vertexToken.account === account && vertexToken.until > Date.now())
    return vertexToken.value;
  const windows = process.platform === "win32";
  const value = await new Promise<string>((resolve, reject) => {
    execFile(
      windows ? "gcloud.cmd" : "gcloud",
      ["auth", "print-access-token", `--account=${account}`],
      { shell: windows, timeout: 30_000, windowsHide: true },
      (error, stdout) => {
        const token = String(stdout ?? "").trim();
        if (error || !/^ya29\.[\w.-]+$/.test(token))
          reject(new Error("gcloud のログインから、アクセスの鍵を取れませんでした"));
        else resolve(token);
      },
    );
  });
  vertexToken = { account, value, until: Date.now() + 45 * 60_000 };
  return value;
}

type VertexRow = {
  document?: {
    derivedStructData?: {
      title?: string;
      link?: string;
      displayLink?: string;
      snippets?: Array<{ snippet?: string }>;
      pagemap?: { cse_thumbnail?: Array<{ src?: string }>; cse_image?: Array<{ src?: string }> };
    };
  };
};

// How many hits one call asks for. The biggest size the service accepts is found on the first call (50, then 25, then 10)
// and kept. There is no cap on the pages: the caller reads on as far as the service goes.
let vertexPageSize = 50;

async function vertexSearchPage(
  config: { project: string; engine: string; account: string },
  token: string,
  name: string,
  page: number,
  size: number,
): Promise<{ results?: VertexRow[] }> {
  const offset = (Math.max(1, Math.trunc(page)) - 1) * size;
  const base = `https://discoveryengine.googleapis.com/v1/projects/${config.project}/locations/global/collections/default_collection/engines/${config.engine}/servingConfigs/default_search:search`;
  const { response, release } = await fetchWithSsrFGuard({
    url: base,
    requireHttps: true,
    maxRedirects: 0,
    timeoutMs: 25_000,
    policy: { hostnameAllowlist: API_HOSTS },
    init: {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Goog-User-Project": config.project,
        "Content-Type": "application/json",
        Accept: "application/json",
        "User-Agent": UA,
      },
      body: JSON.stringify({ query: name, pageSize: size, offset, languageCode: "ja" }),
    },
  });
  try {
    if (!response.ok)
      throw Object.assign(
        new Error(`Google Cloud の検索に失敗しました (HTTP ${response.status})`),
        { status: response.status },
      );
    return JSON.parse(
      (
        await readResponseWithLimit(response, 4_000_000, {
          timeoutMs: 25_000,
          chunkTimeoutMs: 10_000,
        })
      ).toString("utf-8"),
    ) as { results?: VertexRow[] };
  } finally {
    await release();
  }
}

/**
 * The user's Vertex AI Search app, by a name: pages of the registered sites. A hit is a page, not a picture: when it is
 * picked, the picture the page names as its own (og:image) is taken, the polite way (robots.txt kept).
 */
export async function searchVertexImages(name: string, page = 1): Promise<PersonImage[]> {
  const config = vertexConfig();
  if (!config) return [];
  const token = await vertexAccessToken(config.account);
  let body: { results?: VertexRow[] };
  for (;;) {
    try {
      body = await vertexSearchPage(config, token, name, page, vertexPageSize);
      break;
    } catch (error) {
      // a size the service does not accept (HTTP 400): ask for a smaller one
      const next = vertexPageSize > 25 ? 25 : vertexPageSize > 10 ? 10 : 0;
      if ((error as { status?: number }).status === 400 && next > 0) {
        vertexPageSize = next;
        continue;
      }
      throw error;
    }
  }
  const rows: PersonImage[] = [];
  for (const hit of body.results ?? []) {
    const data = hit.document?.derivedStructData;
    const link = data?.link ?? "";
    if (!/^https:\/\//.test(link)) continue;
    const thumb = data?.pagemap?.cse_thumbnail?.[0]?.src ?? "";
    rows.push({
      id: `google:${link}`,
      source: "google",
      title: (data?.title ?? "").slice(0, 80),
      license: "（ライセンス不明・出典サイトの規約に従う）",
      creator: (data?.displayLink ?? "").slice(0, 60),
      thumbUrl: /^https:\/\/encrypted-tbn\d\.gstatic\.com\//.test(thumb) ? thumb : "",
      imageUrl: link,
      pageUrl: link,
    });
  }
  return rows;
}

const PAGE_SIZE = 12;

/**
 * Both collections for one name, Commons first; a failing collection does not hide the other. `page` starts at 1:
 * each page asks both collections for the next 12 pictures, so the list can be read on and on.
 */
export async function searchPersonImages(
  name: string,
  page = 1,
  qid?: string,
): Promise<PersonImage[]> {
  const text = name.replace(/\s+/g, " ").trim().slice(0, 60);
  if (!text) return [];
  const at = Math.min(Math.max(Math.trunc(page) || 1, 1), 200);
  const results = await Promise.allSettled([
    // her own Commons category first (when she is known to Wikidata), then the pictures that name her
    qid
      ? searchCommonsCategoryImages(qid, PAGE_SIZE, (at - 1) * PAGE_SIZE)
      : Promise.resolve([] as PersonImage[]),
    searchCommonsImages(text, PAGE_SIZE, (at - 1) * PAGE_SIZE),
    searchOpenverseImages(text, PAGE_SIZE, at),
    vertexImagesReady() ? searchVertexImages(text, at) : searchGoogleImages(text, at),
    // TMDB (needs the key; its pictures come with the first page only)
    at === 1 && process.env.TMDB_API_KEY
      ? searchTmdbImages(text).then((rows): PersonImage[] =>
          rows.map((row) => ({
            id: row.id,
            source: "tmdb",
            title: row.title,
            license: "TMDB（出典の表示が必要）",
            creator: "",
            thumbUrl: row.thumbUrl,
            imageUrl: row.imageUrl,
            pageUrl: row.pageUrl,
          })),
        )
      : Promise.resolve([] as PersonImage[]),
  ]);
  const seen = new Set<string>();
  const rows: PersonImage[] = [];
  for (const result of results) {
    if (result.status !== "fulfilled") continue;
    for (const row of result.value) {
      // the same picture is often in both collections
      const key = row.source === "google" || !row.title ? row.id : row.title.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      rows.push(row);
    }
  }
  if (rows.length === 0 && results.every((result) => result.status === "rejected"))
    throw new Error("画像の検索先に、つながりませんでした");
  return rows;
}

/** Downloads one picked picture (only from the known hosts, at most 6 MB, only a real JPEG / PNG / WebP). */
export async function downloadPersonImage(url: string): Promise<{ mime: string; data: Buffer }> {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    throw new Error("画像のアドレスが正しくありません");
  }
  if (parsed.protocol !== "https:" || parsed.username || parsed.password)
    throw new Error("この画像の取得先は、使えません");
  // a Google hit lives on any site: it is fetched the polite way (public addresses only, robots.txt, no bot-check evasion)
  if (!IMAGE_HOSTS.includes(parsed.hostname)) {
    const { mime, data } = await pictureFromUrl(parsed.href);
    return { mime, data };
  }
  const { response, release } = await fetchWithSsrFGuard({
    url: parsed.href,
    requireHttps: true,
    maxRedirects: 2,
    timeoutMs: 30_000,
    policy: { hostnameAllowlist: IMAGE_HOSTS },
    init: { headers: { "User-Agent": UA } },
  });
  try {
    if (!response.ok) throw new Error(`画像を取得できませんでした (HTTP ${response.status})`);
    const mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
    const data = await readResponseWithLimit(response, MAX_IMAGE_BYTES, {
      timeoutMs: 30_000,
      chunkTimeoutMs: 10_000,
    });
    if (
      !["image/jpeg", "image/png", "image/webp"].includes(mime) ||
      !data.length ||
      !looksLikeIdolImage(data, mime)
    )
      throw new Error("画像として読めませんでした");
    return { mime, data };
  } finally {
    await release();
  }
}

export type PhotoCredit = {
  title: string;
  license: string;
  creator: string;
  source: string;
  pageUrl: string;
  savedAt: string;
};

/** Notes where a saved picture came from (photo-credits.json next to the roster), so the license can be looked up later. */
export async function recordPhotoCredit(
  group: string,
  name: string,
  credit: Omit<PhotoCredit, "savedAt">,
  extraKey?: string,
): Promise<void> {
  const file = path.join(path.dirname(idolRosterFile()), "photo-credits.json");
  let all: Record<string, PhotoCredit> = {};
  try {
    all = JSON.parse(await fs.readFile(file, "utf-8")) as Record<string, PhotoCredit>;
  } catch {
    all = {};
  }
  const cut = (value: string, max: number): string => value.slice(0, max);
  all[extraKey ? `${group}/${name}#${extraKey}` : `${group}/${name}`] = {
    title: cut(credit.title, 120),
    license: cut(credit.license, 40),
    creator: cut(credit.creator, 80),
    source: cut(credit.source, 20),
    pageUrl: /^https:\/\//.test(credit.pageUrl) ? cut(credit.pageUrl, 300) : "",
    savedAt: new Date().toISOString(),
  };
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, JSON.stringify(all, null, 1), "utf-8");
  await fs.rename(temp, file);
}
