import fs from "node:fs/promises";
import path from "node:path";
import { parseHTML as parseHtmlRaw } from "linkedom";
import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import { addExtraPhoto } from "./idol-extras.js";
import {
  ageFromBirthday,
  idolPhotoDir,
  idolPhotoFileOf,
  idolMembers,
  idolPhotoIndex,
  idolRoster,
  idolRosterFile,
  looksLikeIdolImage,
  manualMemberLine,
  saveIdolPhoto,
  serializeRosterMember,
  upsertRosterLines,
  type IdolMember,
} from "./idol-master.js";

// "Information sources": the user gives a group's member-list page (one URL); the gateway reads the people on it
// (name, birthday, intake, hometown, photo ...) and fills the roster. Step 1 (check) writes nothing; step 2 (apply)
// writes after one backup. Only empty fields are filled; people who are not in the roster yet can be added.
// The page is read by its structure (repeated cards with a picture, a name and a date), no AI; only when that finds
// nobody, the local chat model (Gemma) reads the page text, and the result is marked as read by the AI.
// Pages that robots.txt forbids, or that sit behind a bot check, are not read.

/** The few DOM members this file uses (linkedom's own types are not usable under this tsconfig). */
type DomNode = {
  nodeType: number;
  nodeValue: string | null;
  tagName?: string;
  className?: string;
  parentElement: DomNode | null;
  nextElementSibling: DomNode | null;
  childNodes: ArrayLike<DomNode>;
  querySelectorAll: (selector: string) => ArrayLike<DomNode>;
  getAttribute: (name: string) => string | null;
  textContent: string | null;
};
type DomDocument = {
  body: DomNode | null;
  documentElement: DomNode | null;
  querySelectorAll: (selector: string) => ArrayLike<DomNode>;
};
const parseHTML = parseHtmlRaw as unknown as (html: string) => { document: DomDocument };

const USER_AGENT = "OpenClaw-idol-master/1.0 (personal use; one page at a time)";
const ROBOTS_TOKEN = "openclaw-idol-master";
const PAUSE_MS = 1_200;
const MAX_PAGE_BYTES = 3 * 1024 * 1024;
const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
const MIN_PEOPLE = 3;
/** Pictures taken from one MIXCH page. */
const MAX_MIXCH_IMAGES = 30;

export type SourcePerson = {
  name: string;
  english?: string;
  /** "1994-10-04" */
  birthday?: string;
  generation?: string;
  nickname?: string;
  hometown?: string;
  agency?: string;
  bloodType?: string;
  heightCm?: number;
  photoUrl?: string;
  profileUrl?: string;
  /** The member's X (Twitter) account, found in a link of the card or of her own page. */
  xUrl?: string;
  /** The picture of that X page (its og:image), when the page gave one. */
  xPhotoUrl?: string;
  /** The member's MIXCH (mixch.tv) page, found in a link of the card or of her own page. */
  mixchUrl?: string;
  /** All the pictures on that MIXCH page (its og:image and every <img>). */
  mixchPhotoUrls?: string[];
};

const X_HOSTS = new Set([
  "x.com",
  "www.x.com",
  "twitter.com",
  "www.twitter.com",
  "mobile.twitter.com",
]);
const X_RESERVED =
  /^(?:intent|share|home|search|i|hashtag|explore|login|signup|tos|privacy|about|settings|messages|notifications|compose|download)$/i;
const SOCIAL_HOST =
  /(?:^|\.)(?:x\.com|twitter\.com|instagram\.com|facebook\.com|tiktok\.com|youtube\.com|youtu\.be|line\.me|note\.com|ameblo\.jp|mixch\.tv)$/i;

/** "https://mixch.tv/u/123" for a link to a page of mixch.tv (not its top page), else undefined. */
export function mixchProfileLink(
  href: string | null | undefined,
  base: string,
): string | undefined {
  const raw = (href ?? "").trim();
  if (!raw || raw.startsWith("#") || /^(mailto|tel|javascript):/i.test(raw)) return undefined;
  let url: URL;
  try {
    url = new URL(raw, base);
  } catch {
    return undefined;
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") return undefined;
  const host = url.hostname.toLowerCase();
  if (host !== "mixch.tv" && host !== "www.mixch.tv") return undefined;
  const path = url.pathname.replace(/\/+$/, "");
  return path ? `https://mixch.tv${path}` : undefined;
}

/** "https://x.com/handle" for a link to an X account (not a share or intent link), else undefined. */
export function xProfileLink(href: string | null | undefined, base: string): string | undefined {
  const raw = (href ?? "").trim();
  if (!raw || raw.startsWith("#") || /^(mailto|tel|javascript):/i.test(raw)) return undefined;
  let url: URL;
  try {
    url = new URL(raw, base);
  } catch {
    return undefined;
  }
  if (!X_HOSTS.has(url.hostname.toLowerCase())) return undefined;
  const handle = url.pathname.split("/").filter(Boolean)[0] ?? "";
  if (!/^[A-Za-z0-9_]{1,15}$/.test(handle) || X_RESERVED.test(handle)) return undefined;
  return `https://x.com/${handle}`;
}

const isSocialLink = (href: string, base: string): boolean => {
  try {
    return SOCIAL_HOST.test(new URL(href, base).hostname);
  } catch {
    return false;
  }
};

/** "www.ske48.co.jp" and "ske48.co.jp" and "sp.stu48.com" / "www.stu48.com" are the same site: compare the registrable domain. */
export function sameSite(a: string, b: string): boolean {
  const base = (value: string): string | undefined => {
    try {
      const labels = new URL(value).hostname.toLowerCase().split(".");
      const second = labels[labels.length - 2];
      const keep =
        labels.length >= 3 &&
        second !== undefined &&
        ["co", "or", "ne", "ac", "go", "ed"].includes(second)
          ? 3
          : 2;
      return labels.slice(-keep).join(".");
    } catch {
      return undefined;
    }
  };
  const x = base(a);
  return x !== undefined && x === base(b);
}

const squash = (value: string): string => value.replace(/[\s　]+/g, "").toLowerCase();
const collapse = (value: string): string => value.replace(/[\s　 ]+/g, " ").trim();

// ---------------------------------------------------------------- reading values out of text

/** "1994.10.04" / "1994年10月4日" / "1994/10/4" / "1994-10-04" -> "1994-10-04" (a year alone is not enough). */
export function isoDateIn(text: string): string | undefined {
  const m = /(\d{4})\s*[.\-/年]\s*(\d{1,2})\s*[.\-/月]\s*(\d{1,2})\s*日?/.exec(text);
  if (!m) return undefined;
  const iso = `${m[1]}-${m[2]!.padStart(2, "0")}-${m[3]!.padStart(2, "0")}`;
  return ageFromBirthday(iso) === undefined ? undefined : iso;
}

const PREFECTURE = /(北海道|東京都|大阪府|京都府|[぀-ヿ一-鿿]{2,3}県)/;

export function generationIn(text: string): string | undefined {
  const m = /(?:ドラフト)?(\d{1,2})\s*期(?:生)?/.exec(text);
  return m ? `${m[1]}期` : undefined;
}

/** What a "label: value" pair on a profile page means (the label in Japanese or English). */
export function applyProfileLabel(person: SourcePerson, rawLabel: string, rawValue: string): void {
  const label = collapse(rawLabel).replace(/[：:]$/, "");
  const value = collapse(rawValue);
  // The label itself can be the intake ("7期生" with the day she joined as its value).
  const intake = /^(?:ドラフト)?(\d{1,2})\s*期生?$/.exec(label);
  if (intake) {
    person.generation ??= `${intake[1]}期`;
    return;
  }
  if (!value) return;
  if (/生年月日|誕生日|birth/i.test(label)) person.birthday ??= isoDateIn(value);
  else if (/出身/.test(label))
    person.hometown ??= PREFECTURE.exec(value)?.[1] ?? (value.length <= 12 ? value : undefined);
  else if (/血液型|blood/i.test(label)) {
    const blood = /(AB|A|B|O)/i.exec(value)?.[1]?.toUpperCase();
    if (blood) person.bloodType ??= blood;
  } else if (/身長|height/i.test(label)) {
    const cm = Number.parseFloat(/(\d{3}(?:\.\d)?)/.exec(value)?.[1] ?? "");
    if (cm >= 100 && cm <= 250) person.heightCm ??= cm;
  } else if (/ニックネーム|愛称|nickname/i.test(label)) person.nickname ??= value.slice(0, 40);
  else if (/事務所|agency/i.test(label)) person.agency ??= value.slice(0, 60);
  else if (/^期|期生|generation/i.test(label)) person.generation ??= generationIn(value);
}

/** A profile page: term/value pairs in <dt><dd>, <th><td>, and "label：value" lines. */
export function parseProfileHtml(html: string): Partial<SourcePerson> {
  const { document } = parseHTML(html);
  const person: SourcePerson = { name: "" };
  for (const dt of Array.from(document.querySelectorAll("dt"))) {
    const dd = dt.nextElementSibling;
    if (dd && (dd.tagName ?? "").toLowerCase() === "dd")
      applyProfileLabel(person, dt.textContent ?? "", dd.textContent ?? "");
  }
  for (const th of Array.from(document.querySelectorAll("th"))) {
    const td = th.nextElementSibling;
    if (td && (td.tagName ?? "").toLowerCase() === "td")
      applyProfileLabel(person, th.textContent ?? "", td.textContent ?? "");
  }
  // <tr><td>label</td><td>value</td></tr> (a table with no header cells)
  for (const cell of Array.from(document.querySelectorAll("td"))) {
    const next = cell.nextElementSibling;
    if (next && (next.tagName ?? "").toLowerCase() === "td")
      applyProfileLabel(person, cell.textContent ?? "", next.textContent ?? "");
  }
  // <li><span class="data">label</span>value</li>
  for (const li of Array.from(document.querySelectorAll("li"))) {
    const span = Array.from(li.querySelectorAll("span"))[0];
    const label = collapse(span?.textContent ?? "");
    if (!span || !label || label.length > 12) continue;
    applyProfileLabel(
      person,
      label,
      collapse(li.textContent ?? "")
        .replace(label, "")
        .trim(),
    );
  }
  const text = (document.body?.textContent || document.documentElement?.textContent || "").replace(
    /[ \t　]+/g,
    " ",
  );
  for (const m of text.matchAll(
    /(生年月日|誕生日|出身地?|血液型|身長|ニックネーム|愛称|所属事務所|事務所)\s*[：:]\s*([^\n\r]{1,40})/g,
  )) {
    applyProfileLabel(person, m[1] ?? "", m[2] ?? "");
  }
  // the X account linked on her page (the account of the whole group is dropped later: it is on every page)
  const x = Array.from(document.querySelectorAll("a"))
    .map((a) => xProfileLink(a.getAttribute("href"), "https://example.invalid/"))
    .find(Boolean);
  if (x) person.xUrl = x;
  const mixch = Array.from(document.querySelectorAll("a"))
    .map((a) => mixchProfileLink(a.getAttribute("href"), "https://example.invalid/"))
    .find(Boolean);
  if (mixch) person.mixchUrl = mixch;
  const { name: _name, ...rest } = person;
  return rest;
}

// ---------------------------------------------------------------- reading the member list by its structure

const LABEL_WORDS =
  /^(ニックネーム|愛称|生年月日|誕生日|出身地?|血液型|身長|所属事務所|事務所|詳細|more|read more|プロフィール|profile|メンバー|member|一覧|チーム|team|new|ブログ|twitter|instagram|x|tiktok)$/i;

function looksLikeName(segment: string): boolean {
  const s = collapse(segment);
  if (s.length < 2 || s.length > 24) return false;
  if (!/[぀-ヿ一-鿿]/.test(s)) return false; // needs kanji or kana
  if (/[\d０-９]{3,}/.test(s) || /[:：@/]/.test(s)) return false;
  if (LABEL_WORDS.test(s) || /期生?$|^\d/.test(s)) return false;
  if (/(都|道|府|県)$/.test(s) && s.length <= 5) return false;
  return true;
}

function absolute(url: string | null | undefined, base: string): string | undefined {
  if (!url) return undefined;
  const raw = url.trim().split(/\s+/)[0] ?? "";
  if (!raw || raw.startsWith("data:") || raw.startsWith("#") || raw.startsWith("javascript:"))
    return undefined;
  try {
    const resolved = new URL(raw, base);
    return resolved.protocol === "https:" || resolved.protocol === "http:"
      ? resolved.href
      : undefined;
  } catch {
    return undefined;
  }
}

function textSegments(
  node: DomNode,
  out: Array<{ text: string; cls: string }> = [],
): Array<{ text: string; cls: string }> {
  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === 3) {
      const text = collapse(child.nodeValue ?? "");
      if (text) out.push({ text, cls: `${node.className ?? ""}` });
    } else if (child.nodeType === 1) {
      const tag = (child.tagName ?? "").toLowerCase();
      if (tag === "script" || tag === "style" || tag === "noscript" || tag === "svg") continue;
      textSegments(child, out);
    }
  }
  return out;
}

/** The card of one picture: the biggest ancestor that holds this picture and no other. */
function cardOf(img: DomNode): DomNode | null {
  let card: DomNode | null = img.parentElement;
  let best: DomNode | null = null;
  let hops = 0;
  while (card && hops < 6) {
    const tag = (card.tagName ?? "").toLowerCase();
    if (tag === "body" || tag === "html" || tag === "main") break;
    if (card.querySelectorAll("img").length > 1) break;
    best = card;
    card = card.parentElement;
    hops += 1;
  }
  return best;
}

const signatureOf = (card: DomNode): string =>
  `${(card.tagName ?? "").toLowerCase()}.${collapse(`${card.className ?? ""}`)
    .split(" ")
    .filter(Boolean)
    .sort()
    .join(".")}`;

const PLACEHOLDER =
  /dummy|spacer|blank|pixel|placeholder|loading|1x1|transparent|noimage|no_image|no-image/i;

const styleUrl = (style: string | null | undefined): string | undefined =>
  /url\(\s*['"]?([^'")]+)['"]?\s*\)/.exec(style ?? "")?.[1];

/**
 * The real picture of a card: a lazy-load attribute, a background-image in the style of the picture or of the box
 * around it, else its src. Placeholder pictures (dummy.png, spacer.gif ...) are skipped.
 */
function photoOf(img: DomNode, card: DomNode, base: string): string | undefined {
  const candidates: Array<string | null | undefined> = [
    img.getAttribute("data-src"),
    img.getAttribute("data-original"),
    img.getAttribute("data-lazy-src"),
    img.getAttribute("data-lazy"),
    styleUrl(img.getAttribute("style")),
  ];
  for (
    let node: DomNode | null = img.parentElement;
    node;
    node = node === card ? null : node.parentElement
  ) {
    candidates.push(
      styleUrl(node.getAttribute("style")),
      node.getAttribute("data-bg"),
      node.getAttribute("data-background"),
    );
    if (node === card) break;
  }
  candidates.push(img.getAttribute("src"));
  for (const candidate of candidates) {
    const url = absolute(candidate, base);
    if (url && !PLACEHOLDER.test(url)) return url;
  }
  return undefined;
}

function personFromCard(card: DomNode, base: string): SourcePerson | undefined {
  const segments = textSegments(card);
  const all = segments.map((s) => s.text).join("\n");
  const named = segments.find((s) => /name|nm\b|title|ttl/i.test(s.cls) && looksLikeName(s.text));
  const name = (named ?? segments.find((s) => looksLikeName(s.text)))?.text;
  if (!name) return undefined;
  const person: SourcePerson = { name: name.replace(/[\s　]+/g, "") };
  const english = segments.find(
    (s) => /^[A-Za-z][A-Za-z .'-]{2,40}$/.test(s.text) && s.text.includes(" "),
  )?.text;
  if (english) person.english = english;
  const birthday = isoDateIn(all);
  if (birthday) person.birthday = birthday;
  const generation = generationIn(all);
  if (generation) person.generation = generation;
  const pref = PREFECTURE.exec(all)?.[1];
  if (pref && /出身|出身地/.test(all)) person.hometown = pref;
  const img = Array.from(card.querySelectorAll("img"))[0];
  if (img) {
    const photo = photoOf(img, card, base);
    if (photo) person.photoUrl = photo;
  }
  const anchors = [
    ...(card.tagName?.toLowerCase() === "a" ? [card] : []),
    ...Array.from(card.querySelectorAll("a")),
  ];
  const hrefs = anchors
    .map((a) => absolute(a.getAttribute("href"), base))
    .filter((href): href is string => Boolean(href));
  // the page of the member: the first link that is not a social-media link
  const profile = hrefs.find((href) => !isSocialLink(href, base));
  if (profile) person.profileUrl = profile;
  const x = anchors.map((a) => xProfileLink(a.getAttribute("href"), base)).find(Boolean);
  if (x) person.xUrl = x;
  const mixch = anchors.map((a) => mixchProfileLink(a.getAttribute("href"), base)).find(Boolean);
  if (mixch) person.mixchUrl = mixch;
  return person;
}

/**
 * The people on a member-list page: the most repeated card shape that has a picture and a name (a date, intake and
 * link are taken when they are there). Returns nothing when fewer than three people can be found.
 */
const NOT_A_NAME =
  /[「」『』【】（）()：:！!？?／/]|トレカ|企画|キャンペーン|プレゼント|限定|くじ|ラリー|チケット|グッズ|特典|入会|会員|ファンクラブ|お知らせ|ニュース|NEWS|詳細|一覧|ランキング|ページ|ログイン|メニュー|スケジュール/;

/** Whether a text can be the name of a person (not a campaign title or a menu word). */
export function isPersonName(value: string): boolean {
  const name = value.replace(/[\s　]+/g, "");
  return name.length >= 2 && name.length <= 16 && !NOT_A_NAME.test(name);
}

/**
 * The mobile address of a site ("sp.example.jp", "m.example.jp") often shows another page or a reduced one: the desktop
 * address is tried (the same path without the "sp." / "m." part) when the mobile page gives nobody.
 */
export function desktopAddress(url: string): string | undefined {
  try {
    const u = new URL(url);
    const host = u.hostname.replace(/^(?:sp|m|mobile|smp)\./i, "");
    return host === u.hostname ? undefined : `${u.protocol}//${host}${u.pathname}${u.search}`;
  } catch {
    return undefined;
  }
}

export function extractPeopleFromHtml(html: string, baseUrl: string): SourcePerson[] {
  const { document } = parseHTML(html);
  const groups = new Map<string, DomNode[]>();
  for (const img of Array.from(document.querySelectorAll("img"))) {
    const card = cardOf(img);
    if (!card) continue;
    const key = signatureOf(card);
    const list = groups.get(key) ?? [];
    if (!list.includes(card)) list.push(card);
    groups.set(key, list);
  }
  let best: SourcePerson[] = [];
  for (const cards of groups.values()) {
    if (cards.length < MIN_PEOPLE) continue;
    const people: SourcePerson[] = [];
    const seen = new Set<string>();
    for (const card of cards) {
      const person = personFromCard(card, baseUrl);
      if (!person || !isPersonName(person.name) || seen.has(squash(person.name))) continue;
      seen.add(squash(person.name));
      people.push(person);
    }
    // The shape with the most people that also carry a date or an intake is the member list.
    const score = (list: SourcePerson[]) =>
      list.length + list.filter((p) => p.birthday || p.generation).length * 2;
    if (people.length >= MIN_PEOPLE && score(people) > score(best)) best = people;
  }
  return best;
}

// ---------------------------------------------------------------- the page, robots.txt, the bot check

/** Decodes a page body by its declared charset (UTF-8, Shift_JIS, EUC-JP ...). */
export function decodeHtml(buffer: Buffer, contentType: string | null): string {
  let charset = /charset=([\w-]+)/i.exec(contentType ?? "")?.[1];
  if (!charset)
    charset = /<meta[^>]+charset=["']?([\w-]+)/i.exec(
      buffer.subarray(0, 4096).toString("latin1"),
    )?.[1];
  try {
    return new TextDecoder((charset ?? "utf-8").toLowerCase()).decode(buffer);
  } catch {
    return buffer.toString("utf-8");
  }
}

/** The bot check of a protection service (Cloudflare and the like): such a page is not read around. */
export function looksLikeBotCheck(status: number, body: string): boolean {
  if (status === 403 || status === 429 || status === 503) return true;
  return /Just a moment|cf-chl|Enable JavaScript and cookies to continue|しばらくお待ちください|Attention Required|Checking your browser/i.test(
    body.slice(0, 6000),
  );
}

/** Whether robots.txt lets the path be read (a missing or unreadable robots.txt allows it). */
export function robotsAllows(robotsText: string, pathname: string): boolean {
  let applies = false;
  let specific = false;
  const rules: Array<{ allow: boolean; path: string; specific: boolean }> = [];
  for (const rawLine of robotsText.split(/\r?\n/)) {
    const line = rawLine.replace(/#.*/, "").trim();
    const m = /^(user-agent|allow|disallow)\s*:\s*(.*)$/i.exec(line);
    if (!m) continue;
    const field = m[1]!.toLowerCase();
    const value = (m[2] ?? "").trim();
    if (field === "user-agent") {
      const agent = value.toLowerCase();
      applies = agent === "*" || ROBOTS_TOKEN.includes(agent) || agent.includes("openclaw");
      specific = agent !== "*";
    } else if (applies && value) {
      rules.push({ allow: field === "allow", path: value, specific });
    }
  }
  const matching = rules.filter((rule) => {
    const pattern = rule.path
      .replace(/[.+?^${}()|[\]\\]/g, "\\$&")
      .replace(/\*/g, ".*")
      .replace(/\\\$$/, "$");
    return new RegExp(`^${pattern}`).test(pathname);
  });
  if (matching.length === 0) return true;
  const hasSpecific = matching.some((rule) => rule.specific);
  const used = hasSpecific ? matching.filter((rule) => rule.specific) : matching;
  // The longest matching rule wins; an Allow wins a tie.
  used.sort((a, b) => b.path.length - a.path.length || Number(b.allow) - Number(a.allow));
  return used[0]!.allow;
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function getPage(
  url: string,
  accept = "text/html",
): Promise<{ status: number; body: string }> {
  const { response, release } = await fetchWithSsrFGuard({
    url,
    requireHttps: false,
    maxRedirects: 3,
    timeoutMs: 30_000,
    init: {
      headers: { "User-Agent": USER_AGENT, Accept: accept, "Accept-Language": "ja,en;q=0.8" },
    },
  });
  try {
    const buffer = await readResponseWithLimit(response, MAX_PAGE_BYTES, {
      timeoutMs: 30_000,
      chunkTimeoutMs: 10_000,
    });
    return {
      status: response.status,
      body: decodeHtml(buffer, response.headers.get("content-type")),
    };
  } finally {
    await release();
  }
}

// ---------------------------------------------------------------- index pages with one page per member
// Some sites show the members as pictures only, each linking to its own page ("/feature/otani_emiri"). The member pages
// are found as a run of links of the same shape, and each page gives the name (its title), the picture and the profile.

const NOT_MEMBER_SLUG =
  /(?:^|[_-])(?:specialsite|mail|contact|faq|about|profile|producer|news|schedule|live|media|goods|shop|fanclub|offer|privacy|terms|sitemap|special)(?:[_-]|$)/i;

export function memberLinksFromHtml(html: string, baseUrl: string): string[] {
  const { document } = parseHTML(html);
  const byFolder = new Map<string, Set<string>>();
  for (const a of Array.from(document.querySelectorAll("a"))) {
    const href = a.getAttribute("href");
    if (!href || href.startsWith("#") || /^(mailto|tel|javascript):/i.test(href)) continue;
    let url: URL;
    try {
      url = new URL(href, baseUrl);
    } catch {
      continue;
    }
    if (!sameSite(url.href, baseUrl)) continue;
    const parts = url.pathname.split("/").filter(Boolean);
    const slug = parts[parts.length - 1] ?? "";
    // romaji of a name: two to four words joined by "_" or "-"
    if (!/^[a-z]+(?:[_-][a-z0-9]+){1,3}$/i.test(slug) || NOT_MEMBER_SLUG.test(slug)) continue;
    const folder = `${url.origin}/${parts.slice(0, -1).join("/")}`;
    const set = byFolder.get(folder) ?? new Set<string>();
    set.add(`${url.origin}${url.pathname}`);
    byFolder.set(folder, set);
  }
  let best: string[] = [];
  for (const set of byFolder.values()) if (set.size > best.length) best = [...set];
  return best.length >= 5 ? best.slice(0, 80) : [];
}

const first = (document: DomDocument, selector: string): DomNode | undefined =>
  Array.from(document.querySelectorAll(selector))[0];

const META = (document: DomDocument, property: string): string =>
  first(document, `meta[property="${property}"]`)?.getAttribute("content")?.trim() ?? "";

/** One member from her own page: the name (from the title), the picture (og:image) and the profile lines. */
export function personFromProfileHtml(html: string, pageUrl: string): SourcePerson | undefined {
  const { document } = parseHTML(html);
  // The title is the best place ("大谷 映美里 | ＝LOVE オフィシャルサイト"); a site title in og:title is only the last choice.
  const candidates = [
    first(document, "title")?.textContent,
    first(document, "h1")?.textContent,
    META(document, "og:title"),
  ];
  let name: string | undefined;
  for (const raw of candidates) {
    const parts = (raw ?? "")
      .replace(/&amp;/g, "&")
      .split(/\s*[|｜\-–—:：]\s*/)
      .map((part) => collapse(part))
      .filter(Boolean);
    name = parts.find(
      (part) =>
        /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u.test(part) &&
        part.replace(/\s+/g, "").length >= 2 &&
        part.replace(/\s+/g, "").length <= 14 &&
        !/OFFICIAL|オフィシャル|公式|サイト|プロフィール|PROFILE|メンバー|ホーム|TOP/i.test(part),
    );
    if (name) break;
  }
  if (!name) return undefined;
  // "大谷 映美里" -> "大谷映美里" (the roster has no space in a name)
  name = name.replace(/\s+/g, "");
  const image = META(document, "og:image");
  let photoUrl: string | undefined;
  try {
    photoUrl = image ? new URL(image, pageUrl).href : undefined;
  } catch {
    photoUrl = undefined;
  }
  return {
    name,
    ...parseProfileHtml(html),
    ...(photoUrl ? { photoUrl } : {}),
    profileUrl: undefined,
  };
}

export type SourceFetcher = {
  /** The member-list page (the robots.txt check and the bot check are done here). */
  list: (url: string) => Promise<string>;
  profile: (url: string) => Promise<string>;
  /** The page's text read by the AI when the structure gave nobody (undefined when no AI is available). */
  ai?: (pageText: string) => Promise<SourcePerson[]>;
  /** The picture (og:image) of a member's X page, or undefined when the page does not give one (login wall, bot check, robots.txt). Never read around. */
  social?: (
    url: string,
  ) => Promise<{ image?: string; reason?: "robots" | "botcheck" | "noimage" | "error" }>;
  /** All the pictures on a member's MIXCH page; the page is read the same careful way as the X page. */
  mixch?: (
    url: string,
  ) => Promise<{ images?: string[]; reason?: "robots" | "botcheck" | "noimage" | "error" }>;
};

export function createSourceFetcher(delayMs = PAUSE_MS): SourceFetcher {
  const robots = new Map<string, string>();
  const allowed = async (url: string): Promise<void> => {
    const u = new URL(url);
    if (!robots.has(u.origin)) {
      let text = "";
      try {
        const res = await getPage(`${u.origin}/robots.txt`, "text/plain");
        if (res.status === 200) text = res.body;
      } catch {
        // unreadable: allowed
      }
      robots.set(u.origin, text);
    }
    if (!robotsAllows(robots.get(u.origin) ?? "", `${u.pathname}${u.search}`))
      throw new Error("このサイトの robots.txt が、このページの自動取得を断っています");
  };
  const read = async (url: string): Promise<string> => {
    await allowed(url);
    const { status, body } = await getPage(url);
    if (looksLikeBotCheck(status, body))
      throw new Error("ボット対策で守られたページで、読めません（回避は、しません）");
    if (status >= 400) throw new Error(`HTTP ${status}`);
    return body;
  };
  return {
    list: read,
    profile: async (url) => {
      await sleep(delayMs);
      return read(url);
    },
    ai: askAiForPeople,
    social: async (url) => {
      if (!xProfileLink(url, url)) return { reason: "error" };
      await sleep(delayMs);
      try {
        const html = await read(url);
        const { document } = parseHTML(html);
        const image = META(document, "og:image") || META(document, "twitter:image");
        // only a profile picture of the account (not the site's default card)
        return /^https:\/\/pbs\.twimg\.com\/profile_images\//.test(image)
          ? { image: image.replace(/_(?:normal|bigger|mini|200x200)(\.\w+)$/, "_400x400$1") }
          : { reason: "noimage" };
      } catch (error) {
        // why the page was not read: robots.txt, a bot check, or something else (never read around)
        const text = error instanceof Error ? error.message : String(error);
        return {
          reason: text.includes("robots.txt")
            ? "robots"
            : text.includes("ボット対策")
              ? "botcheck"
              : "error",
        };
      }
    },
    mixch: async (url) => {
      if (!mixchProfileLink(url, url)) return { reason: "error" };
      await sleep(delayMs);
      try {
        const { document } = parseHTML(await read(url));
        const found = new Set<string>();
        const add = (raw: string | null | undefined): void => {
          const resolved = absolute(raw, url);
          if (
            resolved?.startsWith("https://") &&
            !PLACEHOLDER.test(resolved) &&
            !/\.(?:svg|gif|ico)(?:[?#]|$)/i.test(resolved)
          )
            found.add(resolved);
        };
        add(META(document, "og:image"));
        add(META(document, "twitter:image"));
        for (const img of Array.from(document.querySelectorAll("img"))) {
          const srcset = (img.getAttribute("srcset") ?? img.getAttribute("data-srcset") ?? "")
            .split(",")
            .map((part) => part.trim().split(/\s+/)[0]);
          for (const candidate of [
            img.getAttribute("src"),
            img.getAttribute("data-src"),
            img.getAttribute("data-original"),
            img.getAttribute("data-lazy-src"),
            srcset[srcset.length - 1],
          ])
            add(candidate);
        }
        const images = [...found].slice(0, MAX_MIXCH_IMAGES);
        return images.length > 0 ? { images } : { reason: "noimage" };
      } catch (error) {
        const text = error instanceof Error ? error.message : String(error);
        return {
          reason: text.includes("robots.txt")
            ? "robots"
            : text.includes("ボット対策")
              ? "botcheck"
              : "error",
        };
      }
    },
  };
}

/** The local chat model reads the page text (a fallback; the result must be checked by the user). */
async function askAiForPeople(pageText: string): Promise<SourcePerson[]> {
  const text = pageText.slice(0, 14_000);
  const prompt =
    "次のページの文章から、グループのメンバー（人）の一覧を、JSON の配列だけで出力してください。各要素は " +
    '{"name":"漢字の名前（空白なし）","birthday":"YYYY-MM-DD か空","generation":"例 3期 か空","hometown":"例 東京都 か空"} の形。' +
    "文章に書かれていない人や値は作らず、わからない値は空にしてください。説明や前置きは書かないでください。\n\n" +
    text;
  const res = await fetch("http://127.0.0.1:11434/v1/chat/completions", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      model: "x",
      messages: [{ role: "user", content: prompt }],
      temperature: 0,
      max_tokens: 6000,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(300_000),
  });
  if (!res.ok) throw new Error("AI（会話のモデル）が使えません");
  const content =
    ((await res.json()) as { choices?: Array<{ message?: { content?: string } }> }).choices?.[0]
      ?.message?.content ?? "";
  const json = /\[[\s\S]*\]/.exec(content)?.[0];
  if (!json) return [];
  const rows = JSON.parse(json) as Array<Record<string, unknown>>;
  const out: SourcePerson[] = [];
  for (const row of rows) {
    const name = typeof row.name === "string" ? row.name.replace(/[\s　]+/g, "") : "";
    // The name must really be in the page text (the AI may invent people).
    if (!looksLikeName(name) || !squash(text).includes(name.toLowerCase())) continue;
    const person: SourcePerson = { name };
    const birthday = typeof row.birthday === "string" ? isoDateIn(row.birthday) : undefined;
    if (birthday) person.birthday = birthday;
    if (typeof row.generation === "string" && generationIn(row.generation))
      person.generation = generationIn(row.generation);
    if (typeof row.hometown === "string" && PREFECTURE.test(row.hometown))
      person.hometown = PREFECTURE.exec(row.hometown)?.[1];
    out.push(person);
  }
  return out;
}

// ---------------------------------------------------------------- the saved sources

export type SavedSource = { group: string; url: string };

/** The member-list pages of the official sites that were tried by hand (one button each in the app). */
export type SourcePreset = {
  group: string;
  url: string;
  readable: boolean;
  note?: string;
  /** A list that shows new faces only: checked for new people, not for who left. */ newOnly?: boolean;
};
export const SOURCE_PRESETS: SourcePreset[] = [
  { group: "SKE48", url: "https://www.ske48.co.jp/profile/list.php", readable: true },
  { group: "NGT48", url: "https://ngt48.jp/profile", readable: true },
  { group: "STU48", url: "https://www.stu48.com/", readable: true },
  { group: "Sakurazaka46", url: "https://sakurazaka46.com/s/s46/search/artist", readable: true },
  {
    group: "Hinatazaka46",
    url: "https://www.hinatazaka46.com/s/official/search/artist",
    readable: true,
  },
  { group: "Bokuao", url: "https://bokuao.com/", readable: true },
  { group: "=LOVE", url: "https://equal-love.jp/feature/profile", readable: true },
  // AV女優 lists (robots.txt allows them; each page is read one at a time). They show many faces, so only new people are watched.
  {
    group: "みんなのAV",
    url: "https://www.minnano-av.com/actress_list.php",
    readable: true,
    newOnly: true,
  },
  { group: "XCITY", url: "https://xcity.jp/idol/", readable: true, newOnly: true },
  { group: "AV-WIKI", url: "https://av-wiki.net/", readable: true, newOnly: true },
  { group: "HKT48", url: "https://www.hkt48.jp/profile/", readable: true },
  {
    group: "NMB48",
    url: "https://www.nmb48.com/member/",
    readable: false,
    note: "このサイトに接続できませんでした（読めませんでした）",
  },
  {
    group: "Nogizaka46",
    url: "https://www.nogizaka46.com/s/n46/search/artist",
    readable: false,
    note: "ページが JavaScript で表示される作りで、読めません（Wikidata で補えます）",
  },
];

export function idolSourcesFile(): string {
  return path.join(path.dirname(idolRosterFile()), "sources.json");
}

export async function listSources(): Promise<SavedSource[]> {
  try {
    const parsed = JSON.parse(await fs.readFile(idolSourcesFile(), "utf-8")) as {
      sources?: unknown;
    };
    if (!Array.isArray(parsed.sources)) return [];
    return parsed.sources.flatMap((entry) => {
      const item = entry as { group?: unknown; url?: unknown };
      return typeof item.group === "string" && typeof item.url === "string"
        ? [{ group: item.group, url: item.url }]
        : [];
    });
  } catch {
    return [];
  }
}

function checkUrl(raw: string): string {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error(
      "URL が正しくありません（https:// で始まる、メンバー一覧のページのアドレスを貼ってください）",
    );
  }
  if (url.protocol !== "https:" && url.protocol !== "http:")
    throw new Error("http か https の URL だけ使えます");
  if (url.username || url.password)
    throw new Error("ユーザー名やパスワード入りの URL は使えません");
  return url.href;
}

export async function saveSource(group: string, rawUrl: string): Promise<SavedSource[]> {
  if (!group.trim()) throw new Error("グループを指定してください");
  const url = checkUrl(rawUrl);
  const sources = (await listSources()).filter((source) => source.group !== group);
  sources.push({ group, url });
  await writeSources(sources);
  return sources;
}

/** Several addresses written one under another (or separated by spaces or commas) -> the list of them. */
export function splitUrls(raw: string): string[] {
  return [
    ...new Set(
      raw
        .split(/[\s,、，]+/)
        .map((part) => part.trim())
        .filter(Boolean),
    ),
  ];
}

/** Keeps all the addresses of a group (they replace what was saved for the group). */
export async function saveSources(group: string, rawUrls: string[]): Promise<SavedSource[]> {
  if (!group.trim()) throw new Error("グループを指定してください");
  const urls = [...new Set(rawUrls.map((raw) => checkUrl(raw)))];
  if (urls.length === 0) throw new Error("URL を入れてください");
  const sources = [
    ...(await listSources()).filter((source) => source.group !== group),
    ...urls.map((url) => ({ group, url })),
  ];
  await writeSources(sources);
  return sources;
}

export async function removeSource(group: string): Promise<SavedSource[]> {
  const sources = (await listSources()).filter((source) => source.group !== group);
  await writeSources(sources);
  return sources;
}

async function writeSources(sources: SavedSource[]): Promise<void> {
  const file = idolSourcesFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  await fs.writeFile(tmp, JSON.stringify({ sources }, null, 2), "utf-8");
  await fs.rename(tmp, file);
}

// ---------------------------------------------------------------- check and apply

/** The member with the empty fields filled from the source (nothing already set is replaced). */
export function mergeSourceIntoMember(
  member: IdolMember,
  person: SourcePerson,
  label: string,
): { member: IdolMember; filled: string[] } {
  const merged: IdolMember = { ...member };
  const filled: string[] = [];
  const take = <K extends keyof IdolMember>(
    key: K,
    value: IdolMember[K] | undefined,
    name: string,
  ) => {
    if (merged[key] === undefined && value !== undefined && value !== "") {
      merged[key] = value;
      filled.push(name);
    }
  };
  take("birthday", person.birthday, "誕生日");
  take("generation", person.generation, "期");
  take("nickname", person.nickname, "愛称");
  take("hometown", person.hometown, "出身地");
  take("agency", person.agency, "事務所");
  take("bloodType", person.bloodType, "血液型");
  take("heightCm", person.heightCm, "身長");
  take("xUrl", person.xUrl, "X");
  if (filled.length === 0) return { member, filled };
  if (merged.age === undefined) {
    const age = ageFromBirthday(merged.birthday);
    if (age !== undefined) merged.age = age;
  }
  merged.source = !merged.source
    ? label
    : merged.source.includes(label)
      ? merged.source
      : `${merged.source} / ${label}`;
  return { member: merged, filled };
}

export type SourceState = {
  status: "idle" | "running" | "done" | "cancelled" | "error";
  group?: string;
  url?: string;
  /** "structure" (read by the page's shape) or "ai" (read by the chat model; check it). */
  method?: "structure" | "ai" | "links";
  total: number;
  done: number;
  /** People found on the page. */
  found: number;
  /** Roster people found on the page that have something new to fill (or no photo yet). */
  withNew: number;
  fieldCounts: Record<string, number>;
  photos: number;
  /** Members with an X account found / X pages that gave a picture. */
  xLinks: number;
  xPhotos: number;
  /** Why the X pages gave no picture: robots.txt, a bot check, no picture on the page, another error (counts). */
  xSkipped: Record<string, number>;
  /** Members with a MIXCH page found / MIXCH pages that gave a picture. */
  mixchLinks: number;
  mixchPhotos: number;
  /** On the page, not in the roster yet. */
  missing: string[];
  /** In the roster, not on the page. */
  unmatched: string[];
  errors: number;
  error?: string;
  startedAt?: number;
  finishedAt?: number;
  applied?: { added: number; updated: number; photosSaved: number; backup?: string };
};

type Match = {
  name: string;
  group: string;
  line?: string;
  photoUrl?: string;
  xPhotoUrl?: string;
  mixchPhotoUrls?: string[];
};
type Missing = { person: SourcePerson; line: string };

const IDLE: SourceState = {
  status: "idle",
  total: 0,
  done: 0,
  found: 0,
  withNew: 0,
  fieldCounts: {},
  photos: 0,
  xLinks: 0,
  xPhotos: 0,
  xSkipped: {},
  mixchLinks: 0,
  mixchPhotos: 0,
  missing: [],
  unmatched: [],
  errors: 0,
};
/**
 * One run of the import (check, then apply) with its own state. The waiting line gives every job its own run, so jobs on
 * different sites can run at the same time; the functions below the factory are the single run of the by-hand screen.
 */
export type SourceRun = {
  state: () => SourceState;
  start: (options: { group: string; url: string; fetcher?: SourceFetcher }) => SourceState;
  cancel: () => SourceState;
  apply: (options?: {
    savePhotos?: boolean;
    addMissing?: boolean;
    pauseMs?: number;
  }) => Promise<{ added: number; updated: number; photosSaved: number; backup?: string }>;
  reset: () => void;
};

export function createSourceRun(): SourceRun {
  let state: SourceState = { ...IDLE };
  let matches: Match[] = [];
  let missingPeople: Missing[] = [];
  let cancelRequested = false;

  function sourceState(): SourceState {
    return {
      ...state,
      unmatched: state.unmatched.slice(0, 400),
      missing: state.missing.slice(0, 400),
    };
  }

  function cancelSource(): SourceState {
    if (state.status === "running") cancelRequested = true;
    return sourceState();
  }

  /** Step 1 in the background (nothing is written). Returns at once; poll sourceState(). */
  function startSourceCheck(options: {
    group: string;
    url: string;
    fetcher?: SourceFetcher;
  }): SourceState {
    if (state.status === "running") {
      // The same group again (the screen was closed and opened): carry on with the run that is going.
      if (state.group === options.group) return sourceState();
      throw new Error(
        `「${state.group ?? "別のグループ"}」を、調べている最中です。終わるのを待つか、そちらを開いて「とめる」を押してください`,
      );
    }
    let url = checkUrl(options.url);
    const group = options.group;
    const fetcher = options.fetcher ?? createSourceFetcher();
    const label = `情報元 ${new URL(url).hostname}`;
    const people = idolRoster().members.filter((member) => member.group === group);
    matches = [];
    missingPeople = [];
    cancelRequested = false;
    state = { ...IDLE, status: "running", group, url, total: 0, startedAt: Date.now() };
    void (async () => {
      try {
        let html = await fetcher.list(url);
        let found = extractPeopleFromHtml(html, url);
        state.method = "structure";
        if (found.length < MIN_PEOPLE && memberLinksFromHtml(html, url).length === 0) {
          const desktop = desktopAddress(url);
          if (desktop) {
            try {
              const other = await fetcher.list(desktop);
              if (
                extractPeopleFromHtml(other, desktop).length >= MIN_PEOPLE ||
                memberLinksFromHtml(other, desktop).length > 0
              ) {
                html = other;
                found = extractPeopleFromHtml(html, desktop);
                url = desktop;
              }
            } catch {
              // the desktop page cannot be read: the first page stays
            }
          }
        }
        // Pictures that link to one page per member: read each member's page.
        if (found.length < MIN_PEOPLE) {
          const links = memberLinksFromHtml(html, url);
          if (links.length > 0) {
            const crawled: SourcePerson[] = [];
            for (const link of links) {
              if (cancelRequested) {
                state.status = "cancelled";
                state.finishedAt = Date.now();
                return;
              }
              try {
                const person = personFromProfileHtml(await fetcher.profile(link), link);
                if (person) crawled.push(person);
              } catch {
                // a page that cannot be read is skipped
              }
            }
            if (crawled.length >= MIN_PEOPLE) {
              found = crawled;
              state.method = "links";
            }
          }
        }
        if (found.length < MIN_PEOPLE && fetcher.ai) {
          const doc = parseHTML(html).document;
          const text = doc.body?.textContent || doc.documentElement?.textContent || "";
          // (the AI too may take a campaign title for a name: such names are dropped)
          const fromAi = (await fetcher.ai(text)).filter((person) => isPersonName(person.name));
          if (fromAi.length >= MIN_PEOPLE) {
            found = fromAi;
            state.method = "ai";
          }
        }
        if (found.length < MIN_PEOPLE)
          throw new Error(
            "このページは、メンバー一覧のページではないようです（メンバーの名前が並んだページの URL を入れてください。スマホ用のアドレスで読めないときは、パソコン用のアドレスを試してください）",
          );
        state.found = found.length;
        state.total = found.length;
        const byName = new Map(found.map((person) => [squash(person.name), person]));
        const photoIndex = await idolPhotoIndex();
        const rosterByName = new Map(people.map((member) => [squash(member.name), member]));
        // 1. The page behind each picture (hometown, blood type, height, the X account ...). A site that fails three times in
        // a row is left alone; nothing else stops the check.
        const hostFails = new Map<string, number>();
        for (const person of found) {
          if (cancelRequested) {
            state.status = "cancelled";
            state.finishedAt = Date.now();
            return;
          }
          const target = person.profileUrl;
          if (target && !isSocialLink(target, url)) {
            let host = "";
            try {
              host = new URL(target).hostname;
            } catch {
              host = "";
            }
            if (host && (hostFails.get(host) ?? 0) < 3) {
              try {
                const extra = parseProfileHtml(await fetcher.profile(target));
                for (const [key, value] of Object.entries(extra))
                  if (value !== undefined && (person as Record<string, unknown>)[key] === undefined)
                    (person as Record<string, unknown>)[key] = value;
                hostFails.set(host, 0);
              } catch {
                state.errors += 1;
                hostFails.set(host, (hostFails.get(host) ?? 0) + 1);
              }
            }
          }
          state.done += 1;
        }
        // An X account that more than one member has is the account of the group (it is on every page), not a member's own.
        const xCount = new Map<string, number>();
        for (const person of found)
          if (person.xUrl) xCount.set(person.xUrl, (xCount.get(person.xUrl) ?? 0) + 1);
        for (const person of found)
          if (person.xUrl && (xCount.get(person.xUrl) ?? 0) > 1) delete person.xUrl;
        state.xLinks = found.filter((person) => person.xUrl).length;
        // 2. The picture of each X page, when it gives one (a login wall, a bot check or robots.txt leaves it out).
        if (fetcher.social) {
          for (const person of found) {
            if (!person.xUrl) continue;
            if (cancelRequested) {
              state.status = "cancelled";
              state.finishedAt = Date.now();
              return;
            }
            const got = await fetcher.social(person.xUrl);
            if (got.image) {
              person.xPhotoUrl = got.image;
              state.xPhotos += 1;
            } else if (got.reason) {
              state.xSkipped[got.reason] = (state.xSkipped[got.reason] ?? 0) + 1;
            }
          }
        }
        // 2b. The MIXCH page of each member (a link to mixch.tv on her card or page): its picture is taken the same way. A
        // MIXCH page that more than one member has is the group's, not a member's own, and is left out.
        const mixchCount = new Map<string, number>();
        for (const person of found)
          if (person.mixchUrl)
            mixchCount.set(person.mixchUrl, (mixchCount.get(person.mixchUrl) ?? 0) + 1);
        for (const person of found)
          if (person.mixchUrl && (mixchCount.get(person.mixchUrl) ?? 0) > 1) delete person.mixchUrl;
        state.mixchLinks = found.filter((person) => person.mixchUrl).length;
        if (fetcher.mixch) {
          for (const person of found) {
            if (!person.mixchUrl) continue;
            if (cancelRequested) {
              state.status = "cancelled";
              state.finishedAt = Date.now();
              return;
            }
            const got = await fetcher.mixch(person.mixchUrl);
            if (got.images?.length) person.mixchPhotoUrls = got.images;
          }
          // a picture that is on more than one page is the site's own (logo, icon, banner), not a member's
          const mixchPics = new Map<string, number>();
          for (const person of found)
            for (const pic of person.mixchPhotoUrls ?? [])
              mixchPics.set(pic, (mixchPics.get(pic) ?? 0) + 1);
          for (const person of found) {
            if (!person.mixchPhotoUrls) continue;
            const own = person.mixchPhotoUrls.filter((pic) => (mixchPics.get(pic) ?? 0) === 1);
            if (own.length > 0) person.mixchPhotoUrls = own;
            else delete person.mixchPhotoUrls;
            state.mixchPhotos += own.length;
          }
        }
        // 3. Who has something new.
        for (const person of found) {
          const member = rosterByName.get(squash(person.name));
          if (!member) {
            const line =
              manualMemberLine({
                group,
                name: person.name,
                nickname: person.nickname,
                generation: person.generation,
                birthday: person.birthday,
                hometown: person.hometown,
              }) + (person.xUrl ? ` | X=${person.xUrl}` : "");
            missingPeople.push({ person, line });
            state.missing.push(person.name);
          } else {
            const merged = mergeSourceIntoMember(member, person, label);
            const hasPhoto =
              idolPhotoFileOf(photoIndex, member) !== undefined || Boolean(member.photoUrl);
            const wantsPhoto = !hasPhoto && Boolean(person.photoUrl);
            if (
              merged.filled.length > 0 ||
              wantsPhoto ||
              person.xPhotoUrl ||
              person.mixchPhotoUrls
            ) {
              state.withNew += 1;
              for (const name of merged.filled)
                state.fieldCounts[name] = (state.fieldCounts[name] ?? 0) + 1;
              if (wantsPhoto) state.photos += 1;
              matches.push({
                name: member.name,
                group,
                ...(merged.filled.length > 0 ? { line: serializeRosterMember(merged.member) } : {}),
                ...(wantsPhoto ? { photoUrl: person.photoUrl } : {}),
                ...(person.xPhotoUrl ? { xPhotoUrl: person.xPhotoUrl } : {}),
                ...(person.mixchPhotoUrls ? { mixchPhotoUrls: person.mixchPhotoUrls } : {}),
              });
            }
          }
        }
        state.unmatched = people
          .filter((member) => !byName.has(squash(member.name)))
          .map((member) => member.name);
        state.status = "done";
        state.finishedAt = Date.now();
      } catch (error) {
        state.status = "error";
        state.error = error instanceof Error ? error.message : String(error);
        state.finishedAt = Date.now();
      }
    })();
    return sourceState();
  }

  async function savePhotoFromUrl(group: string, name: string, photoUrl: string): Promise<boolean> {
    const { response, release } = await fetchWithSsrFGuard({
      url: photoUrl,
      requireHttps: false,
      maxRedirects: 2,
      timeoutMs: 30_000,
      init: { headers: { "User-Agent": USER_AGENT } },
    });
    try {
      if (!response.ok) return false;
      const mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
      const ext =
        mime === "image/jpeg"
          ? ".jpg"
          : mime === "image/png"
            ? ".png"
            : mime === "image/webp"
              ? ".webp"
              : "";
      const data = await readResponseWithLimit(response, MAX_PHOTO_BYTES, {
        timeoutMs: 30_000,
        chunkTimeoutMs: 10_000,
      });
      if (!ext || !data.length || !looksLikeIdolImage(data, mime)) return false;
      const safe = (value: string) => value.replace(/[\\/:*?"<>|\r\n]+/g, "_").trim();
      const dir = path.join(idolPhotoDir(), safe(group));
      await fs.mkdir(dir, { recursive: true });
      const file = path.join(dir, `${safe(name)}${ext}`);
      try {
        await fs.access(file);
        return false; // never replace a file that is already there
      } catch {
        // not there yet
      }
      await fs.writeFile(file, data);
      return true;
    } finally {
      await release();
    }
  }

  // The pictures of X pages that were saved already (x-photos.json), so that updating from the same link again does not
  // add the same picture a second time.
  const xPhotosFile = (): string => path.join(path.dirname(idolRosterFile()), "x-photos.json");

  async function readXPhotos(): Promise<Record<string, string>> {
    try {
      return JSON.parse(await fs.readFile(xPhotosFile(), "utf-8")) as Record<string, string>;
    } catch {
      return {};
    }
  }

  /**
   * The picture of a member's X page: the main photo when she has none, else one more extra photo. The same picture is
   * saved once (it is noted by the member and the address). Returns whether a picture was saved.
   */
  async function saveXPhoto(
    group: string,
    name: string,
    pictureUrl: string,
    kind: "x" | "mixch" = "x",
  ): Promise<boolean> {
    const stem = pictureUrl.replace(/_(?:normal|bigger|mini|200x200|400x400)(\.\w+)$/, "$1");
    // a member has one X picture, but many MIXCH pictures: each is noted by its own address
    const key = kind === "x" ? `${group}\n${name}` : `${kind}\n${group}\n${name}\n${stem}`;
    const known = await readXPhotos();
    if (known[key] === stem) return false;
    const { response, release } = await fetchWithSsrFGuard({
      url: pictureUrl,
      requireHttps: true,
      maxRedirects: 2,
      timeoutMs: 30_000,
      init: { headers: { "User-Agent": USER_AGENT } },
    });
    let mime = "";
    let data: Buffer;
    try {
      if (!response.ok) return false;
      mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
      data = await readResponseWithLimit(response, MAX_PHOTO_BYTES, {
        timeoutMs: 30_000,
        chunkTimeoutMs: 10_000,
      });
    } finally {
      await release();
    }
    if (
      !["image/jpeg", "image/png", "image/webp"].includes(mime) ||
      !data.length ||
      !looksLikeIdolImage(data, mime)
    )
      return false;
    const member = idolMembers(group).find((one) => one.name === name);
    if (!member) return false;
    const index = await idolPhotoIndex();
    if (idolPhotoFileOf(index, member) === undefined && !member.photoUrl)
      await saveIdolPhoto(member, mime, data);
    else await addExtraPhoto(member, mime, data);
    known[key] = stem;
    await fs.writeFile(xPhotosFile(), JSON.stringify(known, null, 1), "utf-8");
    return true;
  }

  /** Step 2: writes the lines kept by step 1 (one backup), adds the people who are missing when asked, saves photos. */
  async function applySource(
    options: { savePhotos?: boolean; addMissing?: boolean; pauseMs?: number } = {},
  ): Promise<{
    added: number;
    updated: number;
    photosSaved: number;
    backup?: string;
  }> {
    if (state.status !== "done" && state.status !== "cancelled")
      throw new Error("先に「調べる」を、最後まで行ってください");
    if (state.applied)
      throw new Error("この結果は、すでに反映しました。もう一度「調べる」から行ってください");
    const addMissing = options.addMissing === true;
    if (matches.length === 0 && (!addMissing || missingPeople.length === 0))
      throw new Error("反映する項目が、ありません");
    const lines = [
      ...matches.flatMap((match) => (match.line ? [match.line] : [])),
      ...(addMissing ? missingPeople.map((entry) => entry.line) : []),
    ];
    const rosterResult =
      lines.length > 0 ? await upsertRosterLines(lines) : { added: 0, updated: 0 };
    let photosSaved = 0;
    if (options.savePhotos !== false) {
      const targets = [
        ...matches
          .filter((match) => match.photoUrl)
          .map((match) => ({ group: match.group, name: match.name, url: match.photoUrl! })),
        ...(addMissing
          ? missingPeople
              .filter((entry) => entry.person.photoUrl)
              .map((entry) => ({
                group: state.group ?? "",
                name: entry.person.name,
                url: entry.person.photoUrl!,
              }))
          : []),
      ];
      for (const target of targets) {
        try {
          if (target.group && (await savePhotoFromUrl(target.group, target.name, target.url)))
            photosSaved += 1;
        } catch {
          // One photo that cannot be saved does not stop the others.
        }
        await sleep(options.pauseMs ?? 400);
      }
      // the pictures of the X pages: saved whether or not she already has a photo (an extra photo then)
      const xTargets = [
        ...matches
          .filter((match) => match.xPhotoUrl)
          .map((match) => ({
            group: match.group,
            name: match.name,
            url: match.xPhotoUrl!,
            kind: "x" as const,
          })),
        ...(addMissing
          ? missingPeople
              .filter((entry) => entry.person.xPhotoUrl)
              .map((entry) => ({
                group: state.group ?? "",
                name: entry.person.name,
                url: entry.person.xPhotoUrl!,
                kind: "x" as const,
              }))
          : []),
        ...matches.flatMap((match) =>
          (match.mixchPhotoUrls ?? []).map((url) => ({
            group: match.group,
            name: match.name,
            url,
            kind: "mixch" as const,
          })),
        ),
        ...(addMissing
          ? missingPeople.flatMap((entry) =>
              (entry.person.mixchPhotoUrls ?? []).map((url) => ({
                group: state.group ?? "",
                name: entry.person.name,
                url,
                kind: "mixch" as const,
              })),
            )
          : []),
      ];
      for (const target of xTargets) {
        try {
          if (
            target.group &&
            (await saveXPhoto(target.group, target.name, target.url, target.kind))
          )
            photosSaved += 1;
        } catch {
          // One picture that cannot be saved does not stop the others.
        }
        await sleep(options.pauseMs ?? 400);
      }
    }
    const result = { ...rosterResult, photosSaved };
    state = { ...state, applied: result };
    matches = [];
    missingPeople = [];
    return result;
  }

  /** For tests: forget everything. */
  function resetSource(): void {
    state = { ...IDLE };
    matches = [];
    missingPeople = [];
    cancelRequested = false;
  }

  return {
    state: sourceState,
    start: startSourceCheck,
    cancel: cancelSource,
    apply: applySource,
    reset: resetSource,
  };
}

/** The run of the by-hand screen (one at a time). */
const manual = createSourceRun();
export const sourceState = (): SourceState => manual.state();
export const cancelSource = (): SourceState => manual.cancel();
export const startSourceCheck = (options: {
  group: string;
  url: string;
  fetcher?: SourceFetcher;
}): SourceState => manual.start(options);
export const applySource = (
  options: { savePhotos?: boolean; addMissing?: boolean; pauseMs?: number } = {},
) => manual.apply(options);
export const resetSource = (): void => manual.reset();
