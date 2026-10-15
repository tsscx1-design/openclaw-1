import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import { looksLikeIdolImage } from "./idol-master.js";
import { createSourceFetcher } from "./idol-source.js";

// A picture for a member from an address the user gives: the address of the picture itself, or of a page that shows her (the
// picture the page names as its own, og:image, is taken). A page is read the way the source import reads one: robots.txt is
// kept and a bot check is not worked around. Only a real JPEG / PNG / WebP of at most 6 MB is taken.

const UA = "OpenClaw-idol-master/1.0 (personal use; one picture the user pointed at)";
const MAX_IMAGE_BYTES = 6 * 1024 * 1024;
const IMAGE_MIME = ["image/jpeg", "image/png", "image/webp"];

/** The picture a page names as its own: og:image, twitter:image or the first link rel="image_src" (absolute address). */
export function pagePictureUrl(html: string, pageUrl: string): string | undefined {
  const tag = (pattern: RegExp): string | undefined => pattern.exec(html)?.[1];
  const raw =
    tag(/<meta[^>]+property=["']og:image(?::secure_url)?["'][^>]*content=["']([^"']+)["']/i) ??
    tag(/<meta[^>]+content=["']([^"']+)["'][^>]*property=["']og:image(?::secure_url)?["']/i) ??
    tag(/<meta[^>]+name=["']twitter:image(?::src)?["'][^>]*content=["']([^"']+)["']/i) ??
    tag(/<meta[^>]+content=["']([^"']+)["'][^>]*name=["']twitter:image(?::src)?["']/i) ??
    tag(/<link[^>]+rel=["']image_src["'][^>]*href=["']([^"']+)["']/i);
  if (!raw) return undefined;
  try {
    const url = new URL(raw.replace(/&amp;/g, "&"), pageUrl);
    return url.protocol === "https:" ? url.href : undefined;
  } catch {
    return undefined;
  }
}

function checkedUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw new Error("アドレスが正しくありません");
  }
  if (url.protocol !== "https:" || url.username || url.password)
    throw new Error("https:// で始まる、ふつうのアドレスを入れてください");
  return url;
}

async function download(url: URL): Promise<{ mime: string; data: Buffer } | { html: string }> {
  const { response, release } = await fetchWithSsrFGuard({
    url: url.href,
    requireHttps: true,
    maxRedirects: 3,
    timeoutMs: 30_000,
    init: { headers: { "User-Agent": UA, Accept: "image/*,text/html;q=0.8" } },
  });
  try {
    if (!response.ok) throw new Error(`読めませんでした (HTTP ${response.status})`);
    const mime = (response.headers.get("content-type") ?? "").split(";")[0]!.trim().toLowerCase();
    if (IMAGE_MIME.includes(mime)) {
      const data = await readResponseWithLimit(response, MAX_IMAGE_BYTES, {
        timeoutMs: 30_000,
        chunkTimeoutMs: 10_000,
      });
      if (!data.length || !looksLikeIdolImage(data, mime))
        throw new Error("画像として読めませんでした");
      return { mime, data };
    }
    if (mime.includes("html")) return { html: "page" };
    throw new Error("画像でも、ページでもありません");
  } finally {
    await release();
  }
}

/** The picture at an address, or the picture of the page at that address. Returns the picture and the address it came from. */
export async function pictureFromUrl(
  value: string,
): Promise<{ mime: string; data: Buffer; from: string }> {
  const url = checkedUrl(value);
  const first = await download(url);
  if ("data" in first) return { ...first, from: url.href };
  // a page: read it politely (robots.txt, no bot-check evasion), then take the picture it names
  const html = await createSourceFetcher().list(url.href);
  const picture = pagePictureUrl(html, url.href);
  if (!picture)
    throw new Error(
      "このページに、写真が見つかりませんでした。写真そのもののアドレスを入れてください",
    );
  const second = await download(checkedUrl(picture));
  if (!("data" in second)) throw new Error("このページの写真を、読めませんでした");
  return { ...second, from: picture };
}
