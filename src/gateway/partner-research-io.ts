import type { AnyAgentTool } from "../agents/tools/common.js";
import { hitAsResearchText, pickBestHit, searchCharacters } from "./anime-lookup.js";
import type { ResearchIO, SearchHit } from "./partner-research.js";

// Research IO for partner creation: the agent's web tools, the Wikipedia API and the anime
// character lookup.

const WIKIPEDIA_API_PREFIX = "https://ja.wikipedia.org/w/api.php?";
// Wikimedia asks API clients for a descriptive User-Agent.
const WIKIPEDIA_USER_AGENT =
  "OpenClaw-partner-research/1.0 (+https://github.com/openclaw/openclaw)";

/** Page text inside the untrusted-content envelope that web_fetch / web_search put around it. */
export function unwrapExternalText(text: string): string {
  const match = /<<<[A-Z_]+ id="([^"]+)">>>\n[\s\S]*?\n---\n([\s\S]*?)\n<<<[A-Z_]+ id="\1">>>/.exec(
    text,
  );
  return (match?.[2] ?? text).trim();
}

function toolText(result: unknown): string {
  const content = (result as { content?: Array<{ type?: string; text?: string }> } | undefined)
    ?.content;
  return Array.isArray(content)
    ? content
        .filter((item) => item?.type === "text" && typeof item.text === "string")
        .map((item) => item.text as string)
        .join("\n")
    : "";
}

/**
 * Research IO on top of the agent's web tools: pages go through the SSRF-guarded web_fetch and
 * searches through the configured web_search provider. Only the fixed Wikipedia API host is
 * called directly.
 */
export function researchIoFromTools(params: {
  webFetchTool?: AnyAgentTool | null;
  webSearchTool?: AnyAgentTool | null;
  fetchImpl?: typeof fetch;
}): ResearchIO {
  const fetchImpl = params.fetchImpl ?? fetch;
  const fetchTool = params.webFetchTool;
  const searchTool = params.webSearchTool;
  let callCount = 0;
  return {
    fetchJson: async (url) => {
      if (!url.startsWith(WIKIPEDIA_API_PREFIX)) {
        throw new Error(`Only the Wikipedia API is fetched directly: ${url}`);
      }
      const res = await fetchImpl(url, {
        headers: { "User-Agent": WIKIPEDIA_USER_AGENT },
        signal: AbortSignal.timeout(20_000),
      });
      if (!res.ok) {
        throw new Error(`Wikipedia API HTTP ${res.status}`);
      }
      return await res.json();
    },
    fetchPage: async (url, maxChars) => {
      if (!fetchTool) {
        return undefined;
      }
      callCount += 1;
      const result = await fetchTool.execute(`partner-research-fetch-${callCount}`, {
        url,
        extractMode: "markdown",
        maxChars,
      });
      const details = (
        result as { details?: { status?: unknown; text?: unknown; title?: unknown } }
      ).details;
      const status = typeof details?.status === "number" ? details.status : 200;
      const raw = typeof details?.text === "string" ? details.text : toolText(result);
      const text = unwrapExternalText(raw);
      if (status >= 400 || !text) {
        return undefined;
      }
      const title =
        typeof details?.title === "string" ? unwrapExternalText(details.title) : undefined;
      return { title, text };
    },
    lookupCharacter: async (name, work) => {
      const hit = pickBestHit(await searchCharacters(name, fetchImpl), name, work);
      if (!hit) return undefined;
      return {
        url: hit.url,
        title: `${hit.source === "anilist" ? "AniList" : "MyAnimeList"}「${hit.name}」`,
        site: hit.source === "anilist" ? "anilist.co" : "myanimelist.net",
        text: hitAsResearchText(hit),
      };
    },
    searchWeb: searchTool
      ? async (query, count) => {
          callCount += 1;
          const result = await searchTool.execute(`partner-research-search-${callCount}`, {
            query,
            count,
          });
          const details = (
            result as {
              details?: {
                kind?: unknown;
                message?: unknown;
                results?: unknown;
                citations?: unknown;
              };
            }
          ).details;
          if (details?.kind === "error") {
            throw new Error(String(details.message ?? "web_search failed"));
          }
          // Listing providers (Tavily, Brave, DuckDuckGo) return results; answer providers
          // (Gemini with Google Search) return the pages they cite.
          const items = Array.isArray(details?.results)
            ? details.results
            : Array.isArray(details?.citations)
              ? details.citations
              : [];
          return items.flatMap((item): SearchHit[] => {
            const hit = item as { title?: unknown; url?: unknown; snippet?: unknown };
            return typeof hit.url === "string"
              ? [
                  {
                    url: hit.url,
                    title: typeof hit.title === "string" ? unwrapExternalText(hit.title) : "",
                    snippet:
                      typeof hit.snippet === "string" ? unwrapExternalText(hit.snippet) : undefined,
                  },
                ]
              : [];
          });
        }
      : undefined,
  };
}
