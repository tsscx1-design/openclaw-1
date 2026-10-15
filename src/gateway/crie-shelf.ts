import { addKnowledge, type KnowledgeDoc } from "./partner-knowledge.js";
import type { ResearchIO } from "./partner-research.js";

// Lets the studio assistant (Crie) fill her own reference shelf from the internet: a search phrase
// or a web address becomes shelf documents that later chats draw on.

const PAGE_CHARS = 40_000;

export type ShelfRequest = { query?: string; url?: string; title?: string; count?: number };

/** Searches (or takes the given address), reads the pages and puts them on the partner's shelf. */
export async function shelfFromWeb(
  partnerId: string,
  request: ShelfRequest,
  io: Pick<ResearchIO, "fetchPage" | "searchWeb">,
): Promise<{ added: KnowledgeDoc[]; skipped: number }> {
  const urls: Array<{ url: string; title?: string }> = [];
  if (request.url) {
    if (!/^https?:\/\//i.test(request.url))
      throw new Error("アドレスは http(s):// で始まるものを指定してください");
    urls.push({ url: request.url, title: request.title });
  } else if (request.query?.trim()) {
    if (!io.searchWeb)
      throw new Error("ネット検索がまだ使えません（設定でネット検索を有効にしてください）");
    const count = Math.min(Math.max(request.count ?? 4, 1), 8);
    for (const hit of await io.searchWeb(request.query.trim(), count))
      urls.push({ url: hit.url, title: hit.title });
  } else {
    throw new Error("検索する言葉か、アドレスが必要です");
  }
  const added: KnowledgeDoc[] = [];
  let skipped = 0;
  for (const { url, title } of urls.slice(0, 8)) {
    try {
      const page = await io.fetchPage(url, PAGE_CHARS);
      if (!page?.text) {
        skipped += 1;
        continue;
      }
      added.push(
        await addKnowledge(partnerId, {
          title: (title || page.title || url).slice(0, 120),
          kind: "url",
          source: url,
          text: page.text,
        }),
      );
    } catch {
      skipped += 1;
    }
  }
  return { added, skipped };
}
