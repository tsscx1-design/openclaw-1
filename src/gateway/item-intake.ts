import { CHARACTER_OPTION_CATALOG } from "./character-options.js";
import { ensureAgentModel } from "./group-chat-model.js";
import type { ResearchIO } from "./partner-research.js";
import { addCustomSetting, loadCustomSettings, type CustomSetting } from "./sample-factory.js";

// Adding settings in bulk: words separated by 、 / or new lines are read one by one by the local
// model, which picks the genre of each and writes what to draw. Words it does not know are looked
// up on the web (the word only) and read again. Each setting is then added to its genre.

const GEMMA_PORT = 11437;
const FALLBACK_GENRE = "自分で追加";
const MAX_WORDS = 40;

export type IntakeResult = {
  label: string;
  genre: string;
  instruction: string;
  /** "added" | "duplicate" | "failed" */
  status: "added" | "duplicate" | "failed";
  /** True when the web was used to understand the word. */
  searched: boolean;
  entry?: CustomSetting;
};

export function genreNames(): string[] {
  return CHARACTER_OPTION_CATALOG.map((genre) => genre.name);
}

/** The words of the text: split at 、 , / ／ ・ and new lines, trimmed, without repeats. */
export function splitWords(text: string): string[] {
  const seen = new Set<string>();
  const words: string[] = [];
  for (const part of text.split(/[、,，/／・\n\r;；]+/)) {
    const word = part
      .trim()
      .replace(/^[-・*•\s]+/, "")
      .slice(0, 40);
    if (word && !seen.has(word)) {
      seen.add(word);
      words.push(word);
    }
  }
  return words.slice(0, MAX_WORDS);
}

type Judged = {
  label: string;
  genre: string;
  part: string;
  instruction: string;
  known: boolean;
  /** A short Japanese explanation of what the word is. */
  ja?: string;
};
type Source = { title: string; url: string };

const MAIN_PORT = 11434;

async function chat(port: number, prompt: string, timeoutMs: number): Promise<string> {
  const res = await fetch(`http://127.0.0.1:${port}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify({
      messages: [{ role: "user", content: prompt }],
      max_tokens: 3500,
      temperature: 0.2,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`ローカルAIが応答しません（${res.status}）`);
  const data = (await res.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return data.choices?.[0]?.message?.content ?? "";
}

/**
 * Uses the main agent model so classification does not accidentally inherit a smaller group-chat
 * model. When the GPU is lent to a media job, the CPU worker remains the bounded fallback.
 */
export async function ask(prompt: string): Promise<string> {
  try {
    await ensureAgentModel();
    return await chat(MAIN_PORT, prompt, 240_000);
  } catch {
    // fall through to the CPU worker
  }
  return await chat(GEMMA_PORT, prompt, 180_000);
}

/** The rows of a JSON array in the answer, cleaned; a genre that is not in the list becomes the fallback. */
export function parseJudged(
  text: string,
  genres: string[],
  partsByGenre: ReadonlyMap<string, readonly string[]> = new Map(),
): Judged[] {
  // The model does not always answer with a clean array: it may drop the brackets, wrap the rows in
  // another pair of braces, or put them in a code block. Every flat {...} object that has a "label"
  // is read on its own.
  const objects = text.match(/\{[^{}]*"label"[^{}]*\}/g) ?? [];
  return objects.flatMap((chunk): Judged[] => {
    let row: Record<string, unknown>;
    try {
      row = JSON.parse(chunk) as Record<string, unknown>;
    } catch {
      return [];
    }
    const label = typeof row.label === "string" ? row.label.trim().slice(0, 40) : "";
    if (!label) return [];
    const genre =
      typeof row.genre === "string" && genres.includes(row.genre.trim())
        ? row.genre.trim()
        : FALLBACK_GENRE;
    const requestedPart = typeof row.part === "string" ? row.part.trim() : "";
    const part = partsByGenre.get(genre)?.includes(requestedPart) ? requestedPart : "";
    const instruction =
      typeof row.instruction === "string" ? row.instruction.trim().slice(0, 400) : "";
    const ja = typeof row.ja === "string" ? row.ja.trim().slice(0, 120) : "";
    return [
      {
        label,
        genre,
        part,
        instruction,
        known: row.known !== false && Boolean(instruction),
        ...(ja ? { ja } : {}),
      },
    ];
  });
}

function catalogParts(): Map<string, readonly string[]> {
  return new Map(
    CHARACTER_OPTION_CATALOG.map((genre) => [genre.name, genre.parts.map((part) => part.name)]),
  );
}

const rulesText = (genres: string[], partsByGenre: ReadonlyMap<string, readonly string[]>) =>
  `分類は次の中から1つだけ選ぶ（当てはまらなければ「${FALLBACK_GENRE}」）: ${genres.join(" / ")}。` +
  `小分類は、選んだ分類にある名前を完全一致で1つ選ぶ。該当しなければ空文字にする。\n${[
    ...partsByGenre.entries(),
  ]
    .map(([genre, parts]) => `${genre}: ${parts.join(" / ")}`)
    .join("\n")}\n` +
  '各語を、{"label":"入力された語を一字も変えずそのまま","genre":"大分類","part":"小分類","instruction":"画像AIへ渡す具体的な英語の視覚指示。形、色、素材、位置、周囲との関係まで書く","ja":"その語が何かを説明する日本語の短い文（60字以内）","known":true}の形にして、入力と同じ件数・順番のJSON配列だけで返す。' +
  "意味が分からない語や、知らない固有名詞は、想像で書かず known を false にして instruction を空にする。";

/** Reads every word; the unknown ones are searched on the web (the word only) and read again. */
export async function readWords(
  words: string[],
  io?: Pick<ResearchIO, "searchWeb">,
): Promise<Array<Judged & { searched: boolean; sources: Source[] }>> {
  const genres = genreNames();
  const partsByGenre = catalogParts();
  // Ten rows keeps the category guide and every visual instruction inside the local model's
  // reliable structured-output range.
  const first: Judged[] = [];
  for (let start = 0; start < words.length; start += 10) {
    const slice = words.slice(start, start + 10);
    first.push(
      ...parseJudged(
        await ask(
          `次の語を、画像生成の「項目」として読み取ってください。\n語: ${slice.join(" / ")}\n${rulesText(genres, partsByGenre)}`,
        ),
        genres,
        partsByGenre,
      ),
    );
  }
  const byLabel = new Map(first.map((row) => [row.label, row]));
  const results: Array<Judged & { searched: boolean; sources: Source[] }> = [];
  for (const word of words) {
    let row =
      byLabel.get(word) ??
      first.find((candidate) => candidate.label.includes(word) || word.includes(candidate.label));
    let searched = false;
    let sources: Source[] = [];
    if ((!row || !row.known) && io?.searchWeb) {
      try {
        const hits = await io.searchWeb(`${word} とは`, 5);
        const notes = hits
          .map((hit) => `${hit.title} ${hit.snippet ?? ""}`.trim())
          .filter(Boolean)
          .join("\n")
          .slice(0, 2500);
        if (notes) {
          searched = true;
          sources = hits.slice(0, 5).map((hit) => ({ title: hit.title, url: hit.url }));
          const again = parseJudged(
            await ask(
              `「${word}」という語を、次の検索結果を読んで、画像生成の「項目」として読み取ってください。\n検索結果:\n${notes}\n${rulesText(genres, partsByGenre)}`,
            ),
            genres,
            partsByGenre,
          );
          if (again[0]) row = { ...again[0], label: word };
        }
      } catch {
        // searching is a help; the word is kept below even without it
      }
    }
    results.push({
      label: word,
      genre: row?.genre ?? FALLBACK_GENRE,
      part: row?.part ?? "",
      // an unknown word is still kept: its own name is what to draw
      instruction: row?.instruction || word,
      known: Boolean(row?.known),
      ...(row?.ja ? { ja: row.ja } : {}),
      searched,
      sources,
    });
  }
  return results;
}

/** Splits the text, reads the words and adds each one to its genre. */
export async function intakeItems(
  text: string,
  io?: Pick<ResearchIO, "searchWeb">,
): Promise<IntakeResult[]> {
  const words = splitWords(text);
  if (words.length === 0)
    throw new Error("追加する項目を書いてください（「、」「/」「改行」で区切れます）");
  const read = await readWords(words, io);
  const existing = new Set((await loadCustomSettings()).map((item) => item.label));
  const out: IntakeResult[] = [];
  for (const row of read) {
    const category = row.part ? `${row.genre}／${row.part}` : row.genre;
    if (existing.has(row.label)) {
      out.push({
        label: row.label,
        genre: category,
        instruction: row.instruction,
        status: "duplicate",
        searched: row.searched,
      });
      continue;
    }
    try {
      const entry = await addCustomSetting({
        genre: category,
        label: row.label,
        instruction: row.instruction,
        ja: row.ja,
        sources: row.sources,
      });
      existing.add(row.label);
      out.push({
        label: row.label,
        genre: entry.genre,
        instruction: entry.instruction,
        status: "added",
        searched: row.searched,
        entry,
      });
    } catch {
      out.push({
        label: row.label,
        genre: category,
        instruction: row.instruction,
        status: "failed",
        searched: row.searched,
      });
    }
  }
  return out;
}
