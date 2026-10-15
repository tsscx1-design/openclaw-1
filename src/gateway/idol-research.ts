import { listTmdbPeople, tmdbPersonCredits } from "./idol-external.js";

// Looks a person up on the open internet by name and gathers what is written about them: the profile, the career, the
// works, the way they talk and their voice. The sources are the Japanese Wikipedia and Wikiquote (fixed hosts) and TMDB
// (works, with the user's key). The local chat model then writes a short summary from those texts only. Quotes of speeches
// are not copied: only the way of talking is described.

const UA = "OpenClaw-idol-master/1.0 (personal use; reads one person at a time)";
const MAX_TEXT = 7000;

export type Materials = {
  name: string;
  wikipedia?: { title: string; url: string; text: string };
  quotes?: { title: string; url: string; text: string };
  works: string[];
  sources: Array<{ label: string; url: string }>;
};

let lastCall = 0;
async function wikiApi(
  host: "ja.wikipedia.org" | "ja.wikiquote.org",
  params: Record<string, string>,
): Promise<unknown> {
  const wait = lastCall + 800 - Date.now();
  if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
  lastCall = Date.now();
  const response = await fetch(
    `https://${host}/w/api.php?${new URLSearchParams({ format: "json", formatversion: "2", ...params }).toString()}`,
    {
      headers: { "User-Agent": UA, Accept: "application/json" },
      signal: AbortSignal.timeout(25_000),
    },
  );
  if (!response.ok) throw new Error(`${host} から読めませんでした (HTTP ${response.status})`);
  return response.json();
}

const squash = (value: string): string =>
  value
    .normalize("NFKC")
    .replace(/[\s　・]/g, "")
    .toLowerCase();

/** The page of that exact title (or the one the search puts first when its title starts with the name), with its text. */
async function pageText(
  host: "ja.wikipedia.org" | "ja.wikiquote.org",
  name: string,
  maxChars: number,
): Promise<{ title: string; text: string } | undefined> {
  const found = (await wikiApi(host, {
    action: "query",
    list: "search",
    srsearch: name,
    srlimit: "5",
  })) as { query?: { search?: Array<{ title?: string }> } };
  const titles = (found.query?.search ?? []).map((row) => row.title ?? "").filter(Boolean);
  const title =
    titles.find((one) => squash(one) === squash(name)) ??
    titles.find((one) => squash(one).startsWith(squash(name)));
  if (!title) return undefined;
  const body = (await wikiApi(host, {
    action: "query",
    prop: "extracts",
    explaintext: "1",
    exsectionformat: "plain",
    titles: title,
    redirects: "1",
  })) as {
    query?: { pages?: Array<{ title?: string; extract?: string }> };
  };
  const page = body.query?.pages?.[0];
  const text = (page?.extract ?? "").replace(/\n{3,}/g, "\n\n").trim();
  return page?.title && text ? { title: page.title, text: text.slice(0, maxChars) } : undefined;
}

const pageUrl = (host: string, title: string): string =>
  `https://${host}/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`;

/** Gathers the texts (each source on its own: a failing one does not stop the others). */
export async function gatherMaterials(name: string): Promise<Materials> {
  const materials: Materials = { name, works: [], sources: [] };
  const [wiki, quotes, works] = await Promise.allSettled([
    pageText("ja.wikipedia.org", name, MAX_TEXT),
    pageText("ja.wikiquote.org", name, 2500),
    (async (): Promise<string[]> => {
      if (!process.env.TMDB_API_KEY) return [];
      const person = (await listTmdbPeople({ query: name })).find(
        (one) => squash(one.name) === squash(name),
      );
      return person ? tmdbPersonCredits(person.id) : [];
    })(),
  ]);
  if (wiki.status === "fulfilled" && wiki.value) {
    materials.wikipedia = { ...wiki.value, url: pageUrl("ja.wikipedia.org", wiki.value.title) };
    materials.sources.push({ label: "ウィキペディア", url: materials.wikipedia.url });
  }
  if (quotes.status === "fulfilled" && quotes.value) {
    materials.quotes = { ...quotes.value, url: pageUrl("ja.wikiquote.org", quotes.value.title) };
    materials.sources.push({ label: "ウィキクォート", url: materials.quotes.url });
  }
  if (works.status === "fulfilled" && works.value.length > 0) {
    materials.works = works.value;
    materials.sources.push({ label: "TMDB（出演作）", url: "https://www.themoviedb.org/" });
  }
  return materials;
}

export type Research = {
  intro: string;
  career: string;
  works: string;
  speech: string;
  voice: string;
};

/** The instruction for the local model: write only from the given texts, and leave a field empty when the texts do not say. */
export function researchPrompt(materials: Materials): string {
  return [
    `次の人物について、下の資料だけを根拠に、日本語で、JSONだけを返してください。資料に書かれていない項目は、空の文字列にしてください。想像で足さないでください。`,
    `形: {"intro":"自己紹介（本人の言葉づかいではなく、紹介文。3〜5文）","career":"経歴（デビューや主な出来事。3〜6文）","works":"主な作品・出演作（読点で区切る。多くて12個）","speech":"話し方の特徴（口調、口癖、雰囲気。2〜3文。発言の引用はしない）","voice":"声の特徴（高さ・質感・雰囲気。1文。資料に声の記述がなければ、空）"}`,
    `人物名: ${materials.name}`,
    materials.wikipedia ? `【ウィキペディア】\n${materials.wikipedia.text}` : "",
    materials.quotes ? `【ウィキクォート（発言）】\n${materials.quotes.text}` : "",
    materials.works.length > 0 ? `【出演作（TMDB）】\n${materials.works.join("、")}` : "",
  ]
    .filter(Boolean)
    .join("\n\n");
}

/** The fields of the model's answer (a JSON object, maybe inside other words); missing fields are empty. */
export function parseResearch(answer: string): Research {
  const empty: Research = { intro: "", career: "", works: "", speech: "", voice: "" };
  const start = answer.indexOf("{");
  const end = answer.lastIndexOf("}");
  if (start < 0 || end <= start) return empty;
  try {
    const parsed = JSON.parse(answer.slice(start, end + 1)) as Record<string, unknown>;
    const text = (key: string, max: number): string =>
      typeof parsed[key] === "string"
        ? (parsed[key] as string).replace(/\s+/g, " ").trim().slice(0, max)
        : "";
    return {
      intro: text("intro", 600),
      career: text("career", 900),
      works: text("works", 400),
      speech: text("speech", 500),
      voice: text("voice", 160),
    };
  } catch {
    return empty;
  }
}
