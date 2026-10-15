import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import os from "node:os";
import * as path from "node:path";

// Meaning-based search for the reference shelf: Qwen3-Embedding-0.6B finds passages by meaning,
// Qwen3-Reranker-0.6B puts the best ones first. Both run on the CPU in small llama-servers that are
// started on first use. Anything that fails (server not up yet, files missing) returns null and the
// caller keeps the plain word-matching result, so chat is never held up.

const MODEL_DIR = "D:\llm-models\embed";
const SERVER = path.join(os.homedir(), ".openclaw", "llamacpp-mainline", "llama-server.exe");
const EMBED_PORT = 11438;
const RERANK_PORT = 11439;
const QUERY_PREFIX = "Instruct: Given a question, retrieve passages that answer it\nQuery:";

const enabled = (): boolean => !process.env.VITEST && process.env.OPENCLAW_KNOWLEDGE_NEURAL !== "0";

async function up(port: number): Promise<boolean> {
  try {
    const res = await fetch(`http://127.0.0.1:${port}/health`, {
      signal: AbortSignal.timeout(1500),
    });
    return res.ok;
  } catch {
    return false;
  }
}

const starting = new Map<number, number>();
/** True when the server answers now; otherwise starts it in the background (once a minute) and says false. */
async function ready(port: number, model: string, extra: string[]): Promise<boolean> {
  if (await up(port)) return true;
  const last = starting.get(port) ?? 0;
  if (Date.now() - last > 60_000) {
    starting.set(port, Date.now());
    const file = path.join(MODEL_DIR, model);
    if (
      await fs.access(file).then(
        () => true,
        () => false,
      )
    ) {
      const child = spawn(
        SERVER,
        [
          "-m",
          file,
          "--port",
          String(port),
          "--host",
          "127.0.0.1",
          "-ngl",
          "0",
          "--device",
          "none",
          "-t",
          "4",
          "-c",
          "8192",
          ...extra,
        ],
        {
          detached: true,
          stdio: "ignore",
          windowsHide: true,
        },
      );
      child.unref();
    }
  }
  return false;
}

async function post<T>(port: number, route: string, body: unknown, timeoutMs: number): Promise<T> {
  const res = await fetch(`http://127.0.0.1:${port}${route}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`${route} ${res.status}`);
  return (await res.json()) as T;
}

async function embed(texts: string[]): Promise<number[][]> {
  const out: number[][] = [];
  for (let i = 0; i < texts.length; i += 16) {
    const data = await post<{ data: Array<{ embedding: number[] }> }>(
      EMBED_PORT,
      "/v1/embeddings",
      { model: "x", input: texts.slice(i, i + 16) },
      120_000,
    );
    for (const row of data.data) out.push(row.embedding.map((n) => Math.round(n * 1e4) / 1e4));
  }
  return out;
}

const cosine = (a: number[], b: number[]): number => {
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) {
    const x = a[i] ?? 0;
    const y = b[i] ?? 0;
    dot += x * y;
    na += x * x;
    nb += y * y;
  }
  return dot / (Math.sqrt(na * nb) || 1);
};

const building = new Set<string>();
/** The saved meaning-vectors of one document's passages, or null while they are being made in the background. */
async function passageVectors(cacheFile: string, passages: string[]): Promise<number[][] | null> {
  try {
    const saved = JSON.parse(await fs.readFile(cacheFile, "utf-8")) as {
      count: number;
      vectors: number[][];
    };
    if (saved.count === passages.length) return saved.vectors;
  } catch {
    // not made yet
  }
  if (!building.has(cacheFile)) {
    building.add(cacheFile);
    void embed(passages)
      .then((vectors) =>
        fs.writeFile(cacheFile, JSON.stringify({ count: passages.length, vectors }), "utf-8"),
      )
      .catch(() => undefined)
      .finally(() => building.delete(cacheFile));
  }
  return null;
}

export type NeuralPassage = {
  key: string;
  text: string;
  cacheFile: string;
  index: number;
  docPassages: string[];
};

/**
 * Re-orders candidate passages by meaning. [wordRanked] is the word-matching order (best first);
 * returns the keys in the new order with a relevance score, or null to keep the word-matching result.
 */
export async function neuralSearch(
  query: string,
  candidates: NeuralPassage[],
  wordRanked: string[],
  limit: number,
): Promise<Array<{ key: string; score: number }> | null> {
  if (!enabled() || candidates.length === 0) return null;
  try {
    const [e, r] = await Promise.all([
      ready(EMBED_PORT, "emb.gguf", ["--embedding", "--pooling", "last", "-ub", "2048"]),
      ready(RERANK_PORT, "rerank.gguf", ["--reranking"]),
    ]);
    if (!e || !r) return null;
    const files = [...new Set(candidates.map((c) => c.cacheFile))];
    const vectors = new Map<string, number[][]>();
    for (const file of files) {
      const doc = candidates.find((c) => c.cacheFile === file)!.docPassages;
      const v = await passageVectors(file, doc);
      if (!v) return null;
      vectors.set(file, v);
    }
    const q = (await embed([QUERY_PREFIX + query]))[0] ?? [];
    const byMeaning = candidates
      .map((c) => ({ key: c.key, score: cosine(q, vectors.get(c.cacheFile)?.[c.index] ?? []) }))
      .toSorted((a, b) => b.score - a.score)
      .map((x) => x.key);
    // Reciprocal rank fusion of word-matching and meaning.
    const fused = new Map<string, number>();
    for (const order of [wordRanked, byMeaning])
      order.forEach((key, i) => fused.set(key, (fused.get(key) ?? 0) + 1 / (60 + i)));
    const top = [...fused.entries()]
      .toSorted((a, b) => b[1] - a[1])
      .slice(0, 12)
      .map(([key]) => key);
    const text = new Map(candidates.map((c) => [c.key, c.text]));
    const ranked = await post<{ results: Array<{ index: number; relevance_score: number }> }>(
      RERANK_PORT,
      "/v1/rerank",
      { query, documents: top.map((k) => text.get(k) ?? "") },
      60_000,
    );
    return ranked.results
      .toSorted((a, b) => b.relevance_score - a.relevance_score)
      .slice(0, limit)
      .flatMap((x) =>
        top[x.index] ? [{ key: top[x.index] as string, score: x.relevance_score }] : [],
      );
  } catch {
    return null;
  }
}
