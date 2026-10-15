import * as fs from "node:fs";
import * as fsp from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { STORE_MODELS, type StoreModelData } from "./model-store-data.js";

// The model store: picture models that were researched but are not installed yet. Each can be
// downloaded from the app (Hugging Face needs no key; Civitai needs the user's own API key, kept in
// a file on this PC and never sent back). An SDXL-type checkpoint can be used for pictures as soon as
// it is on the disk: its id ("st_...") is accepted everywhere an image model is.

const STORE_DIR = path.join("D:\\media-models", "image", "store");
const KEY_FILE = path.join(os.homedir(), ".openclaw", "media", "model-store", "civitai.key");
const MIN_BYTES = 1_000_000_000;

export type StoreSdxlSpec = {
  file: string;
  vpred: boolean;
  photo: boolean;
  sampler: "euler_a" | "dpmpp_2m_sde_karras" | "lcm";
  steps: number;
  cfg: number;
  quality: string;
  negative: string;
};

export type StoreState = "installed" | "downloading" | "downloadable" | "needs-key" | "info";
export type StoreRow = StoreModelData & {
  state: StoreState;
  received?: number;
  total?: number;
  error?: string;
};

type Download = {
  received: number;
  total: number;
  error?: string;
  done: boolean;
  abort: AbortController;
};
const downloads = new Map<string, Download>();
const vpredCache = new Map<string, boolean>();

export const isStoreModelId = (value: unknown): value is `st_${string}` =>
  typeof value === "string" && /^st_[a-z0-9_]+$/.test(value);

export function storeEntry(id: string): StoreModelData | undefined {
  return STORE_MODELS.find((entry) => entry.id === id);
}

export function storeFileOf(id: string): string {
  return path.join(STORE_DIR, `${id}.safetensors`);
}

function installed(id: string): boolean {
  try {
    return fs.statSync(storeFileOf(id)).size >= MIN_BYTES;
  } catch {
    return false;
  }
}

/** Ids of the store models that are on the disk and can be used for pictures. */
export function installedStoreIds(): string[] {
  return STORE_MODELS.filter((entry) => entry.usable && installed(entry.id)).map(
    (entry) => entry.id,
  );
}

/** Whether the checkpoint was trained as v-prediction (NoobAI style); read from its header. */
function isVpred(file: string): boolean {
  const cached = vpredCache.get(file);
  if (cached !== undefined) return cached;
  let result = false;
  try {
    const fd = fs.openSync(file, "r");
    try {
      const lenBuf = Buffer.alloc(8);
      fs.readSync(fd, lenBuf, 0, 8, 0);
      const len = Number(lenBuf.readBigUInt64LE());
      if (len > 0 && len < 50_000_000) {
        const head = Buffer.alloc(len);
        fs.readSync(fd, head, 0, len, 8);
        const keys = Object.keys(JSON.parse(head.toString("utf8")) as Record<string, unknown>);
        result = keys.some((key) => key === "v_pred" || key === "ztsnr");
      }
    } finally {
      fs.closeSync(fd);
    }
  } catch {
    result = false;
  }
  vpredCache.set(file, result);
  return result;
}

const Q_ANIME = "masterpiece, best quality, newest, absurdres, highres, very aesthetic";
const NEG_ANIME =
  "worst quality, old, early, low quality, lowres, signature, username, logo, bad hands, mutated hands, bad anatomy, watermark";
const NEG_PHOTO =
  "cartoon, illustration, anime, painting, CGI, 3D render, low quality, worst quality, blurry, bad anatomy, bad hands, extra fingers, deformed, watermark, logo, text";

/** Picture settings of a downloaded store checkpoint, from its kind (anime / Pony / photo) and the researched values. */
export function storeSpecOf(id: string): StoreSdxlSpec | undefined {
  const entry = storeEntry(id);
  if (!entry?.usable || !installed(id)) return undefined;
  const file = storeFileOf(id);
  const photo = entry.genre === "real";
  const pony = entry.arch === "pony";
  const prefix = pony ? "score_9, score_8_up, score_7_up, " : "";
  return {
    file,
    vpred: isVpred(file),
    photo,
    sampler: photo ? "dpmpp_2m_sde_karras" : "euler_a",
    steps: entry.steps ?? (photo ? 30 : 28),
    cfg: entry.cfg ?? 5,
    quality: photo
      ? `${prefix}photograph, realistic, high detail, sharp focus`
      : `${prefix}${Q_ANIME}`,
    negative: photo
      ? `${pony ? "score_6, score_5, score_4, " : ""}${NEG_PHOTO}`
      : `${pony ? "score_6, score_5, score_4, " : ""}${NEG_ANIME}`,
  };
}

// ---- Civitai key ------------------------------------------------------------------------------

export async function hasCivitaiKey(): Promise<boolean> {
  return (await readKey()) !== undefined;
}

async function readKey(): Promise<string | undefined> {
  try {
    const key = (await fsp.readFile(KEY_FILE, "utf8")).trim();
    return key || undefined;
  } catch {
    return undefined;
  }
}

/** Saves (or, with an empty text, removes) the user's Civitai API key. It is never returned. */
export async function setCivitaiKey(key: string): Promise<void> {
  const clean = key.trim();
  if (!clean) {
    await fsp.rm(KEY_FILE, { force: true });
    return;
  }
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(clean))
    throw new Error("鍵の形が違います（英数字だけの長い文字列です）");
  await fsp.mkdir(path.dirname(KEY_FILE), { recursive: true });
  await fsp.writeFile(KEY_FILE, clean, "utf8");
}

// ---- list ---------------------------------------------------------------------------------------

export async function listStore(): Promise<{ models: StoreRow[]; hasKey: boolean }> {
  const hasKey = await hasCivitaiKey();
  const models = STORE_MODELS.map((entry): StoreRow => {
    const job = downloads.get(entry.id);
    if (installed(entry.id) && entry.usable) return { ...entry, state: "installed" };
    if (job && !job.done)
      return { ...entry, state: "downloading", received: job.received, total: job.total };
    const error = job?.error;
    if (!entry.usable) return { ...entry, state: "info" };
    if (entry.civitaiId && !entry.hfRepo && !hasKey) return { ...entry, state: "needs-key", error };
    return { ...entry, state: "downloadable", error };
  });
  return { models, hasKey };
}

// ---- download -----------------------------------------------------------------------------------

type HfFile = { rfilename: string; size?: number };

async function resolveHf(repo: string): Promise<{ url: string; size: number }> {
  const res = await fetch(`https://huggingface.co/api/models/${repo}?blobs=true`, {
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`Hugging Face に接続できません（${res.status}）`);
  const info = (await res.json()) as { siblings?: HfFile[] };
  const files = (info.siblings ?? [])
    .filter(
      (file) =>
        file.rfilename.endsWith(".safetensors") &&
        !file.rfilename.includes("/") &&
        (file.size ?? 0) >= MIN_BYTES &&
        (file.size ?? 0) <= 8_000_000_000,
    )
    .sort((a, b) => (b.size ?? 0) - (a.size ?? 0));
  const best = files[0];
  if (!best) throw new Error("使える単体のファイルが見つかりません");
  return {
    url: `https://huggingface.co/${repo}/resolve/main/${best.rfilename}`,
    size: best.size ?? 0,
  };
}

async function resolveCivitai(
  modelId: number,
  key: string,
): Promise<{ url: string; size: number }> {
  const res = await fetch(`https://civitai.com/api/v1/models/${modelId}`, {
    headers: { Authorization: `Bearer ${key}` },
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`Civitai に接続できません（${res.status}）`);
  const info = (await res.json()) as {
    modelVersions?: Array<{
      baseModel?: string;
      files?: Array<{
        type?: string;
        name?: string;
        sizeKB?: number;
        downloadUrl?: string;
        metadata?: { fp?: string; format?: string };
      }>;
    }>;
  };
  for (const version of info.modelVersions ?? []) {
    if (!/SDXL|Pony|Illustrious|NoobAI/i.test(version.baseModel ?? "")) continue;
    const files = (version.files ?? []).filter(
      (file) =>
        file.type === "Model" &&
        /safetensors/i.test(file.metadata?.format ?? file.name ?? "") &&
        file.downloadUrl,
    );
    files.sort((a, b) => Number(b.metadata?.fp === "fp16") - Number(a.metadata?.fp === "fp16"));
    const file = files[0];
    if (file?.downloadUrl)
      return { url: file.downloadUrl, size: Math.round((file.sizeKB ?? 0) * 1024) };
  }
  throw new Error("SDXL 系の最新版のファイルが見つかりません");
}

/** Starts downloading a store model in the background; progress is read with listStore(). */
export function startStoreDownload(id: string): void {
  const entry = storeEntry(id);
  if (!entry) throw new Error("そのモデルが見つかりません");
  if (!entry.usable) throw new Error("このモデルは、別の読み込み方が必要で、まだ対応していません");
  if (installed(id)) return;
  const running = downloads.get(id);
  if (running && !running.done) return;
  const job: Download = { received: 0, total: 0, done: false, abort: new AbortController() };
  downloads.set(id, job);
  void (async () => {
    const part = `${storeFileOf(id)}.part`;
    try {
      const key = await readKey();
      let target: { url: string; size: number };
      const headers: Record<string, string> = {};
      if (entry.hfRepo) {
        target = await resolveHf(entry.hfRepo);
      } else if (entry.civitaiId) {
        if (!key) throw new Error("Civitai の API 鍵が必要です");
        target = await resolveCivitai(entry.civitaiId, key);
        headers.Authorization = `Bearer ${key}`;
      } else {
        throw new Error("ダウンロード元が分かりません");
      }
      job.total = target.size;
      await fsp.mkdir(STORE_DIR, { recursive: true });
      const res = await fetch(target.url, {
        headers,
        redirect: "follow",
        signal: job.abort.signal,
      });
      if (!res.ok || !res.body) throw new Error(`ダウンロードできません（${res.status}）`);
      job.total = Number(res.headers.get("content-length")) || job.total;
      const source = Readable.fromWeb(res.body as never);
      source.on("data", (chunk: Buffer) => {
        job.received += chunk.length;
      });
      await pipeline(source, fs.createWriteStream(part));
      if (job.received < MIN_BYTES)
        throw new Error("ファイルが小さすぎます（ダウンロードに失敗した可能性があります）");
      await fsp.rename(part, storeFileOf(id));
      vpredCache.delete(storeFileOf(id));
      job.done = true;
    } catch (err) {
      job.error = job.abort.signal.aborted
        ? "中止しました"
        : String(err instanceof Error ? err.message : err);
      job.done = true;
      await fsp.rm(part, { force: true }).catch(() => undefined);
    }
  })();
}

export function cancelStoreDownload(id: string): void {
  downloads.get(id)?.abort.abort();
}

export async function deleteStoreModel(id: string): Promise<void> {
  if (!storeEntry(id)) throw new Error("そのモデルが見つかりません");
  await fsp.rm(storeFileOf(id), { force: true });
  downloads.delete(id);
}
