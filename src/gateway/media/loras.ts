import { createWriteStream } from "node:fs";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import type { ReadableStream as WebReadableStream } from "node:stream/web";
import { COMFY_DIR } from "../anima-comfy.js";
import { listCharacters } from "../comic-characters.js";
import { readJsonFile, writeJsonFile } from "../partner-store.js";
import { SD15_LORA_DIR } from "../sd15-comfy.js";
import {
  DMD2_LORA,
  MEDIA_MODEL_DIR,
  OUTPAINT_LORA,
  VIGGLE_TURBO_LORA,
  WAN2GP_DIR,
} from "./config.js";
import { home } from "./paths.js";
import type { MediaJob, VideoEngine } from "./types.js";

// The LoRA catalog: every LoRA on this PC (the SDXL folder, Wan2GP's folders per model family and
// the characters' trained ones) with what the user noted about each (name, trigger words, the
// strength that works, a note, a sample picture). Jobs add the LoRAs picked for them
// (options.extraLoras) on top of their own; a LoRA can also be fetched from Hugging Face.

export const LORA_FAMILIES = [
  "sdxl",
  "anima",
  "sd15",
  "wan",
  "wan_5B",
  "minimax_h3",
  "ltx2",
  "qwen21",
] as const;
export type LoraFamily = (typeof LORA_FAMILIES)[number];

export const LORA_FAMILY_INFO: Record<
  LoraFamily,
  { label: string; usedBy: string; dir: string; defaultScale: number }
> = {
  sdxl: {
    label: "画像（SDXL・Illustrious・Pony）",
    usedBy: "画像・漫画（SDXL 系のモデル）",
    dir: path.join(MEDIA_MODEL_DIR, "image", "lora"),
    defaultScale: 0.8,
  },
  anima: {
    label: "画像（Anima）",
    usedBy: "画像・漫画（Anima モデル）",
    dir: path.join(COMFY_DIR, "models", "loras"),
    defaultScale: 0.8,
  },
  sd15: {
    label: "画像（SD1.5）",
    usedBy: "画像・漫画（SD1.5 系のモデル）",
    dir: SD15_LORA_DIR,
    defaultScale: 0.8,
  },
  wan: {
    label: "動画（Wan 2.2 14B）",
    usedBy: "動画の「長い動画」",
    dir: path.join(WAN2GP_DIR, "loras", "wan"),
    defaultScale: 1,
  },
  wan_5B: {
    label: "動画（Wan 2.2 5B）",
    usedBy: "動画の「高速動画」",
    dir: path.join(WAN2GP_DIR, "loras", "wan_5B"),
    defaultScale: 1,
  },
  minimax_h3: {
    label: "動画（MiniMax H3）",
    usedBy: "動画の「音付き実写」",
    dir: path.join(WAN2GP_DIR, "loras", "minimax_h3"),
    defaultScale: 1,
  },
  ltx2: {
    label: "動画（LTX-2）",
    usedBy: "動画の「音付き動画」",
    dir: path.join(WAN2GP_DIR, "loras", "ltx2"),
    defaultScale: 1,
  },
  qwen21: {
    label: "Qwen Image 2.1",
    usedBy: "画像の Qwen Image 2.1",
    dir: path.join(WAN2GP_DIR, "loras", "qwen21"),
    defaultScale: 1,
  },
};

/** The Wan2GP video engine each family belongs to. */
export const VIDEO_ENGINE_FAMILY: Partial<Record<VideoEngine, LoraFamily>> = {
  long: "wan",
  fast: "wan_5B",
  h3: "minimax_h3",
  audio: "ltx2",
};

/** Files the studio already uses by itself (the speed and editor LoRAs, SVI); listed but not picked. */
function isBuiltin(fileName: string): boolean {
  return (
    fileName === path.basename(DMD2_LORA) ||
    fileName === VIGGLE_TURBO_LORA ||
    fileName === OUTPAINT_LORA ||
    fileName.startsWith("SVI_")
  );
}

export type LoraNote = {
  name?: string;
  trigger?: string;
  scale?: number;
  note?: string;
  sampleJobId?: string;
  source?: string;
  /** The model page, for Civitai imports ("source" there is the fixed string "civitai"). */
  civitaiUrl?: string;
  /** A preview picture saved alongside the LoRA (Civitai's own sample, not one we drew). */
  thumbnail?: string;
};

export type LoraEntry = LoraNote & {
  id: string;
  family: LoraFamily;
  file: string;
  fileName: string;
  sizeMb: number;
  name: string;
  scale: number;
  /** Used by the studio itself, or a character's own LoRA (picked through the character library). */
  pickable: boolean;
  builtin?: boolean;
  characterId?: string;
};

function notesPath(): string {
  return path.join(home(), "media", "lora-catalog.json");
}

async function readNotes(): Promise<Record<string, LoraNote>> {
  return readJsonFile<Record<string, LoraNote>>(notesPath(), {});
}

const clampScale = (value: number): number =>
  Math.round(Math.min(2, Math.max(0, value)) * 100) / 100;

/** Every LoRA on the PC with its notes, newest files first within each family. */
export async function listLoras(): Promise<LoraEntry[]> {
  const notes = await readNotes();
  const entries: LoraEntry[] = [];
  for (const family of LORA_FAMILIES) {
    const info = LORA_FAMILY_INFO[family];
    let names: string[] = [];
    try {
      names = (await fs.readdir(info.dir)).filter((name) => name.endsWith(".safetensors"));
    } catch {
      continue;
    }
    const files = await Promise.all(
      names.map(async (fileName) => {
        const file = path.join(info.dir, fileName);
        const stat = await fs.stat(file).catch(() => undefined);
        return stat?.isFile() ? { fileName, file, stat } : undefined;
      }),
    );
    for (const found of files
      .filter((f): f is NonNullable<typeof f> => Boolean(f))
      .toSorted((a, b) => b.stat.mtimeMs - a.stat.mtimeMs)) {
      const id = `${family}/${found.fileName}`;
      const note = notes[id] ?? {};
      const builtin = isBuiltin(found.fileName);
      entries.push({
        ...note,
        id,
        family,
        file: found.file,
        fileName: found.fileName,
        sizeMb: Math.round(found.stat.size / 1_048_576),
        name: note.name || found.fileName.replace(/\.safetensors$/, ""),
        scale: note.scale ?? info.defaultScale,
        pickable: !builtin,
        ...(builtin ? { builtin } : {}),
      });
    }
  }
  for (const character of await listCharacters()) {
    if (character.loraStatus !== "ready" || !character.lora) continue;
    const stat = await fs.stat(character.lora).catch(() => undefined);
    if (!stat) continue;
    const id = `char/${character.id}`;
    entries.push({
      ...notes[id],
      id,
      family: "sdxl",
      file: character.lora,
      fileName: path.basename(character.lora),
      sizeMb: Math.round(stat.size / 1_048_576),
      name: `${character.name}（キャラ図鑑）`,
      scale: 0.8,
      pickable: false,
      characterId: character.id,
    });
  }
  return entries;
}

/** Saves what the user noted about one LoRA; an empty text clears that field. */
export async function updateLoraNote(id: string, patch: LoraNote): Promise<LoraNote> {
  const notes = await readNotes();
  const next: LoraNote = { ...notes[id] };
  for (const key of [
    "name",
    "trigger",
    "note",
    "sampleJobId",
    "source",
    "civitaiUrl",
    "thumbnail",
  ] as const) {
    const value = patch[key];
    if (typeof value !== "string") continue;
    const limit = key === "note" ? 600 : 200;
    if (value.trim()) next[key] = value.trim().slice(0, limit);
    else delete next[key];
  }
  if (typeof patch.scale === "number" && Number.isFinite(patch.scale))
    next.scale = clampScale(patch.scale);
  notes[id] = next;
  await writeJsonFile(notesPath(), notes);
  return next;
}

export type ChosenLora = { file: string; fileName: string; scale: number; trigger?: string };

/** The LoRAs picked for a job that fit this model family (others are left out). */
export async function chosenLoras(job: MediaJob, family: LoraFamily): Promise<ChosenLora[]> {
  const picked = job.options.extraLoras ?? [];
  if (picked.length === 0) return [];
  const byId = new Map((await listLoras()).map((entry) => [entry.id, entry]));
  return picked.flatMap(({ id, scale }) => {
    const entry = byId.get(id);
    if (!entry || !entry.pickable || entry.family !== family) return [];
    return [
      {
        file: entry.file,
        fileName: entry.fileName,
        scale: typeof scale === "number" ? clampScale(scale) : entry.scale,
        ...(entry.trigger ? { trigger: entry.trigger } : {}),
      },
    ];
  });
}

/** Trigger words of the chosen LoRAs, to put in front of the prompt. */
export function triggerText(loras: ChosenLora[]): string {
  return loras
    .map((lora) => lora.trigger)
    .filter(Boolean)
    .join(", ");
}

// ---------- Hugging Face download ----------

export type LoraDownload = {
  id: string;
  url: string;
  family: LoraFamily;
  fileName: string;
  received: number;
  total?: number;
  status: "downloading" | "done" | "error";
  error?: string;
  startedAt: number;
};

const downloads = new Map<string, LoraDownload>();

export function listLoraDownloads(): LoraDownload[] {
  return [...downloads.values()].toSorted((a, b) => b.startedAt - a.startedAt);
}

/**
 * The direct file link of a Hugging Face page or file link ("…/blob/main/x.safetensors" →
 * "…/resolve/main/x.safetensors"), and an ASCII file name for the tools.
 */
export function huggingFaceFile(raw: string): { url: string; fileName: string } {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error("URL の形が正しくありません");
  }
  if (url.hostname !== "huggingface.co" && url.hostname !== "hf.co")
    throw new Error("Hugging Face（huggingface.co）の URL を入れてください");
  const parts = url.pathname.split("/").filter(Boolean);
  const at = parts.findIndex((part) => part === "blob" || part === "resolve");
  if (at < 2 || parts.length < at + 3)
    throw new Error("ファイルの URL を入れてください（…/blob/main/名前.safetensors の形）");
  if (!parts.at(-1)!.endsWith(".safetensors"))
    throw new Error(".safetensors のファイルを選んでください");
  parts[at] = "resolve";
  const base = decodeURIComponent(parts.at(-1)!).replace(/\.safetensors$/, "");
  const ascii = base.replace(/[^A-Za-z0-9._-]+/g, "_").replace(/^_+|_+$/g, "");
  const fileName = `${ascii || `lora_${Date.now()}`}.safetensors`;
  return { url: `https://huggingface.co/${parts.join("/")}`, fileName };
}

/** Starts fetching a LoRA from Hugging Face into its family's folder; progress is in listLoraDownloads. */
export async function startLoraDownload(rawUrl: string, family: LoraFamily): Promise<LoraDownload> {
  const { url, fileName } = huggingFaceFile(rawUrl);
  const dir = LORA_FAMILY_INFO[family].dir;
  const dest = path.join(dir, fileName);
  if (
    await fs
      .stat(dest)
      .then(() => true)
      .catch(() => false)
  )
    throw new Error(`同じ名前のファイルがあります（${fileName}）`);
  const id = `${family}/${fileName}`;
  if (downloads.get(id)?.status === "downloading") throw new Error("このファイルは取得中です");
  const download: LoraDownload = {
    id,
    url,
    family,
    fileName,
    received: 0,
    status: "downloading",
    startedAt: Date.now(),
  };
  downloads.set(id, download);
  void (async () => {
    const part = `${dest}.part`;
    try {
      await fs.mkdir(dir, { recursive: true });
      const token = process.env.HF_TOKEN?.trim();
      const res = await fetch(url, token ? { headers: { Authorization: `Bearer ${token}` } } : {});
      if (!res.ok || !res.body)
        throw new Error(
          res.status === 401 || res.status === 403
            ? "このファイルは、Hugging Face のログイン（HF_TOKEN）が必要です"
            : `取得できませんでした（HTTP ${res.status}）`,
        );
      const length = Number(res.headers.get("content-length"));
      if (Number.isFinite(length) && length > 0) download.total = length;
      const body = Readable.fromWeb(res.body as WebReadableStream<Uint8Array>);
      body.on("data", (chunk: Buffer) => {
        download.received += chunk.length;
      });
      await pipeline(body, createWriteStream(part));
      await fs.rename(part, dest);
      await updateLoraNote(id, { source: url });
      download.status = "done";
    } catch (err) {
      await fs.rm(part, { force: true }).catch(() => undefined);
      download.status = "error";
      download.error = String(err instanceof Error ? err.message : err).slice(0, 300);
    }
  })();
  return download;
}

// ---------- Civitai import ----------

/**
 * Civitai's own base model names ("Illustrious", "SDXL Hyper", "Wan Video 14B i2v 720p", ...) are
 * not a fixed list; new variants of the same family show up over time. Matched by substring, not
 * an exact lookup, so a new "SDXL ..." or "Wan Video ..." label is still recognized.
 */
function civitaiBaseModelFamily(baseModel: string): LoraFamily | undefined {
  if (/illustrious|pony|noobai|^sdxl/i.test(baseModel)) return "sdxl";
  if (/^anima$/i.test(baseModel)) return "anima";
  if (/wan video|wan2|wan\s?2\./i.test(baseModel)) return "wan";
  if (/^sd\s?1\.?5$/i.test(baseModel)) return "sd15";
  return undefined;
}

/** Civitai sends its page as an app, not an API client; a plain User-Agent avoids its bot check. */
const CIVITAI_HEADERS = { "User-Agent": "Mozilla/5.0" };

function civitaiApiKey(): string {
  const key = process.env.CIVITAI_API_KEY?.trim() ?? "";
  if (!key)
    throw new Error("Civitai の鍵が設定されていません（~/.openclaw/.env の CIVITAI_API_KEY）");
  return key;
}

/** The model id (and version id, if the URL names one) out of a Civitai model page link. */
export function civitaiModelId(raw: string): { modelId: number; versionId?: number } {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error("URL の形が正しくありません");
  }
  if (url.hostname !== "civitai.com")
    throw new Error("Civitai（civitai.com）の URL を入れてください");
  const match = url.pathname.match(/\/models\/(\d+)/);
  if (!match) throw new Error("モデルページの URL を入れてください（…/models/数字/… の形）");
  const versionRaw = url.searchParams.get("modelVersionId");
  const versionId = versionRaw ? Number(versionRaw) : undefined;
  return {
    modelId: Number(match[1]),
    versionId: versionId && Number.isFinite(versionId) ? versionId : undefined,
  };
}

type CivitaiFile = { name: string; sizeKB: number; downloadUrl: string; primary?: boolean };
type CivitaiImage = { url: string };
type CivitaiVersion = {
  id: number;
  baseModel?: string;
  trainedWords?: string[];
  files?: CivitaiFile[];
  images?: CivitaiImage[];
};
type CivitaiModel = { id: number; name: string; modelVersions?: CivitaiVersion[] };

function civitaiFileName(modelName: string, baseModel: string): string {
  const ascii = modelName
    .normalize("NFKD")
    .replace(/[^A-Za-z0-9._-]+/g, "_")
    .replace(/^_+|_+$/g, "");
  const suffix = baseModel.replace(/[^A-Za-z0-9]+/g, "").toLowerCase();
  return `${ascii || `civitai_${Date.now()}`}_${suffix}.safetensors`;
}

/**
 * Imports one Civitai model page: downloads its LoRA file into the right family's folder (by
 * baseModel) and one of its own sample pictures as the thumbnail. Progress is in listLoraDownloads,
 * same as a Hugging Face download.
 */
export async function startCivitaiLoraDownload(rawUrl: string): Promise<LoraDownload> {
  const key = civitaiApiKey();
  const { modelId, versionId } = civitaiModelId(rawUrl);
  const res = await fetch(`https://civitai.com/api/v1/models/${modelId}`, {
    headers: { Authorization: `Bearer ${key}`, ...CIVITAI_HEADERS },
  });
  if (!res.ok) throw new Error(`Civitai から情報を取得できませんでした（HTTP ${res.status}）`);
  const model = (await res.json()) as CivitaiModel;
  // No version named: prefer one whose baseModel this studio can use, over just the newest upload.
  const version =
    (versionId && model.modelVersions?.find((v) => v.id === versionId)) ??
    model.modelVersions?.find((v) => v.baseModel && civitaiBaseModelFamily(v.baseModel)) ??
    model.modelVersions?.[0];
  if (!version) throw new Error("このモデルにはバージョンがありません");
  const family = version.baseModel ? civitaiBaseModelFamily(version.baseModel) : undefined;
  if (!family)
    throw new Error(
      `対応していないベースモデルです（${version.baseModel ?? "不明"}）。今は Illustrious・Pony・NoobAI・SDXL・Anima のみ使えます`,
    );
  const file =
    version.files?.find((f) => f.primary && f.name.endsWith(".safetensors")) ??
    version.files?.find((f) => f.name.endsWith(".safetensors"));
  if (!file) throw new Error("このバージョンに .safetensors のファイルがありません");
  const dir = LORA_FAMILY_INFO[family].dir;
  const fileName = civitaiFileName(model.name, version.baseModel ?? family);
  const dest = path.join(dir, fileName);
  if (
    await fs
      .stat(dest)
      .then(() => true)
      .catch(() => false)
  )
    throw new Error(`同じ名前のファイルがあります（${fileName}）`);
  const id = `${family}/${fileName}`;
  if (downloads.get(id)?.status === "downloading") throw new Error("このファイルは取得中です");
  const download: LoraDownload = {
    id,
    url: rawUrl,
    family,
    fileName,
    received: 0,
    status: "downloading",
    startedAt: Date.now(),
  };
  downloads.set(id, download);
  void (async () => {
    const part = `${dest}.part`;
    try {
      await fs.mkdir(dir, { recursive: true });
      const fileRes = await fetch(file.downloadUrl, {
        headers: { Authorization: `Bearer ${key}`, ...CIVITAI_HEADERS },
      });
      if (!fileRes.ok || !fileRes.body)
        throw new Error(
          fileRes.status === 401 || fileRes.status === 403
            ? "このファイルは、Civitai のログイン（CIVITAI_API_KEY）が必要です"
            : `取得できませんでした（HTTP ${fileRes.status}）`,
        );
      const length = Number(fileRes.headers.get("content-length"));
      if (Number.isFinite(length) && length > 0) download.total = length;
      const body = Readable.fromWeb(fileRes.body as WebReadableStream<Uint8Array>);
      body.on("data", (chunk: Buffer) => {
        download.received += chunk.length;
      });
      await pipeline(body, createWriteStream(part));
      await fs.rename(part, dest);
      let thumbnail: string | undefined;
      // Civitai's CDN blocks requests with no User-Agent, same as its own API and file downloads above;
      // try each sample picture in order in case the first one is missing or fails.
      for (const sample of version.images?.map((img) => img.url).filter(Boolean) ?? []) {
        try {
          const thumbRes = await fetch(sample, { headers: CIVITAI_HEADERS });
          if (thumbRes.ok && thumbRes.body) {
            const thumbDir = path.join(home(), "lora-thumbnails");
            await fs.mkdir(thumbDir, { recursive: true });
            const thumbPath = path.join(thumbDir, `${fileName.replace(/\.safetensors$/, "")}.jpg`);
            await pipeline(
              Readable.fromWeb(thumbRes.body as WebReadableStream<Uint8Array>),
              createWriteStream(thumbPath),
            );
            thumbnail = thumbPath;
            break;
          }
        } catch {
          // Try the next sample picture; the LoRA itself is already saved either way.
        }
      }
      await updateLoraNote(id, {
        source: "civitai",
        civitaiUrl: `https://civitai.com/models/${modelId}`,
        name: model.name,
        ...(version.trainedWords?.length ? { trigger: version.trainedWords.join(", ") } : {}),
        ...(thumbnail ? { thumbnail } : {}),
      });
      download.status = "done";
    } catch (err) {
      await fs.rm(part, { force: true }).catch(() => undefined);
      download.status = "error";
      download.error = String(err instanceof Error ? err.message : err).slice(0, 300);
    }
  })();
  return download;
}

/** Adds the chosen LoRAs to Wan2GP settings, in front of the ones the model already uses. */
export function withWan2gpLoras(
  settings: Record<string, unknown>,
  loras: ChosenLora[],
): Record<string, unknown> {
  if (loras.length === 0) return settings;
  const names = Array.isArray(settings.activated_loras)
    ? (settings.activated_loras as string[])
    : [];
  const multipliers =
    typeof settings.loras_multipliers === "string" && settings.loras_multipliers.trim()
      ? settings.loras_multipliers.trim().split(/\s+/)
      : names.map(() => "1");
  return {
    ...settings,
    activated_loras: [...loras.map((lora) => lora.fileName), ...names],
    loras_multipliers: [...loras.map((lora) => String(lora.scale)), ...multipliers].join(" "),
  };
}
