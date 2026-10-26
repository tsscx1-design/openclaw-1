import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  type ClapResult,
  listenToSounds,
  loadVectors,
  nearestTaught,
} from "./material-shelf-audio.js";
import {
  buildParts,
  buildSoundRecord,
  classifyPart,
  dominantKind,
  parseTranscript,
  SOUND_KINDS,
  type SoundRecord,
} from "./material-shelf-sound.js";
import { clock, INVENTED_LINES, transcribeSounds } from "./material-shelf-transcribe.js";
import type { ShelfItem } from "./material-shelf.js";
import { startSpecialistWorker, waitForWorkerReady } from "./specialist-router.js";

// Looking into the material shelf: a picture (or a few frames of a video) is described by the local Gemma worker that
// sees pictures, a sound is measured (length, loudness, silence). The notes are kept next to the shelf index, so the
// shelf can be searched by what is in the files and not only by their names.

const GEMMA_PORT = 11437;
const FFMPEG = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffmpeg.exe";
const FFPROBE = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffprobe.exe";
const CRISPASR = "D:\\media-tools\\crispasr\\build\\bin\\crispasr.exe";
const WHISPER_MODEL = "D:\\media-tools\\crispasr\\models\\ggml-large-v3-turbo-q5_0.bin";

/** The note of a file: what the PC guessed (`text`) and what the user said about it (a verdict, a text of their own). */
export type ShelfNote = {
  mtimeMs: number;
  text: string;
  /** A few short words to search by, and the kind it falls under (person / background / voice / effect ...). */
  tags?: string[];
  category?: string;
  status?: "ok" | "wrong";
  userText?: string;
  /** The structured record of a sound (kind, words with times, feeling, strength, length). */
  sound?: SoundRecord;
  /** The kind the user chose for a sound; it wins over the guess. */
  userKind?: string;
  /** The kind the user chose for single stretches of a long sound (by the number of the stretch). */
  partKinds?: Record<number, string>;
};

/** A sound the user has described or filed: the others are compared with it. */
type TaughtSound = { id: string; label: string; category?: string; embedding: readonly number[] };

/** What a look into a file found. */
export type ShelfDetail = { text: string; tags: string[]; category?: string; sound?: SoundRecord };

const notesFile = (): string => path.join(os.homedir(), ".openclaw", "shelf", "notes.json");

export async function loadShelfNotes(): Promise<Record<string, ShelfNote>> {
  try {
    const parsed = JSON.parse(await fs.readFile(notesFile(), "utf-8")) as unknown;
    return parsed && typeof parsed === "object" ? (parsed as Record<string, ShelfNote>) : {};
  } catch {
    return {};
  }
}

// The notes file is read, changed and written whole: two writes at once would lose one of them, so they go one after the other.
let notesWrite: Promise<unknown> = Promise.resolve();
function withNotesLock<T>(work: () => Promise<T>): Promise<T> {
  const next = notesWrite.then(work, work);
  notesWrite = next.catch(() => undefined);
  return next;
}

export function saveShelfNote(id: string, note: ShelfNote): Promise<void> {
  return withNotesLock(async () => {
    const notes = await loadShelfNotes();
    // A new guess for the same file keeps what the user said about the file.
    const before = notes[id];
    notes[id] = before && before.mtimeMs === note.mtimeMs ? { ...before, ...note } : note;
    await fs.mkdir(path.dirname(notesFile()), { recursive: true });
    await fs.writeFile(notesFile(), JSON.stringify(notes), "utf-8");
  });
}

function run(exe: string, args: string[], timeoutMs: number): Promise<string> {
  return new Promise((resolve, reject) => {
    execFile(
      exe,
      args,
      { windowsHide: true, timeout: timeoutMs, maxBuffer: 8 * 1024 * 1024 },
      (error, stdout, stderr) => {
        // ffmpeg writes its measurements to stderr
        if (error) reject(new Error(String(stderr).trim().slice(-200) || error.message));
        else resolve(`${stdout}\n${stderr}`);
      },
    );
  });
}

async function durationSeconds(file: string): Promise<number> {
  const out = await run(
    FFPROBE,
    ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file],
    60_000,
  );
  const value = Number.parseFloat(out.trim().split(/\s+/)[0] ?? "");
  return Number.isFinite(value) ? value : 0;
}

/** A picture file as a small jpeg (any format ffmpeg reads), for the vision worker. */
async function smallJpeg(source: string, target: string, atSeconds?: number): Promise<void> {
  await run(
    FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      ...(atSeconds ? ["-ss", String(atSeconds)] : []),
      "-i",
      source,
      "-frames:v",
      "1",
      "-vf",
      "scale='min(768,iw)':-2",
      target,
    ],
    2 * 60_000,
  );
}

/** 画像・動画・音の説明を書くワーカー(11437)は常駐ではないので、使う前に起動しておく（既に起動中なら何もしない）。 */
async function ensureGemmaWorker(): Promise<void> {
  await startSpecialistWorker("gemma").catch(() => undefined);
  await waitForWorkerReady(GEMMA_PORT, 60_000);
}

const DESCRIBE_HINT = "これまで、ユーザーが直してくれた例です。同じ間違いをしないでください：\n";

const JAPANESE_ONLY =
  "必ず日本語だけで答えてください。英語は一文字も使わないこと。JSONのキーの名前も、中身の文章も全部日本語にする。";

const IMAGE_PROMPT =
  "この絵（または写真）を、できるだけ詳しく分析して、JSONだけで返してください。" +
  '形: {"分類":"人物／背景／小物／その他 のどれか1つ","説明":"2〜4文。見たままを詳しく","被写体":"何が・誰が写っているか（人数、髪・服・表情・ポーズ）","場所と時間帯":"","雰囲気":"","色":"主な色","タッチ":"アニメ調／実写／水彩など","使いどころ":"どんな場面で使えそうか","タグ":["検索に使う短い言葉を5〜10個"]}。' +
  "見えないものは空の文字列にする。想像で足さない。" +
  JAPANESE_ONLY;

const VIDEO_PROMPT = (times: string) =>
  "これは、1本の動画から取り出したコマです（動画の" +
  times +
  " の順）。動画の中身を、できるだけ詳しく分析して、JSONだけで返してください。" +
  '形: {"分類":"人物／背景／動き／その他 のどれか1つ","説明":"動画全体を2〜3文","場面":["0〜5秒：その間の様子"],"雰囲気":"","色":"","タッチ":"アニメ調／実写など","使いどころ":"","タグ":["短い言葉を5〜10個"]}。' +
  "「場面」は、コマごとに1つ、秒の範囲つきで。コマに違いが見えなければ「動きはほぼなし」と書き、想像で変化を足さない。見えないものは空の文字列にする。" +
  JAPANESE_ONLY;

const field = (row: Record<string, unknown>, key: string, max = 160): string => {
  const value = row[key];
  return typeof value === "string" ? value.replace(/\s+/g, " ").trim().slice(0, max) : "";
};

/** The JSON of the vision worker's answer as a note (a plain answer is kept as it is). */
export function detailFromAnswer(answer: string, extra = ""): ShelfDetail {
  const start = answer.indexOf("{");
  const end = answer.lastIndexOf("}");
  let row: Record<string, unknown> | undefined;
  if (start >= 0 && end > start) {
    try {
      row = JSON.parse(answer.slice(start, end + 1)) as Record<string, unknown>;
    } catch {
      row = undefined;
    }
  }
  if (!row) {
    const plain = answer.replace(/\s+/g, " ").trim().slice(0, 600);
    return { text: `${extra}${plain}`, tags: [] };
  }
  const scenes = Array.isArray(row["場面"])
    ? row["場面"]
        .filter((v): v is string => typeof v === "string")
        .map((v) => v.replace(/\s+/g, " ").trim().slice(0, 100))
        .slice(0, 8)
    : [];
  const parts = [
    field(row, "説明", 320),
    field(row, "被写体") && `被写体：${field(row, "被写体")}`,
    field(row, "場所と時間帯", 80) && `場所：${field(row, "場所と時間帯", 80)}`,
    field(row, "雰囲気", 80) && `雰囲気：${field(row, "雰囲気", 80)}`,
    field(row, "色", 80) && `色：${field(row, "色", 80)}`,
    field(row, "タッチ", 40) && `タッチ：${field(row, "タッチ", 40)}`,
    scenes.length > 0 && `場面：${scenes.join(" ／ ")}`,
    field(row, "使いどころ", 100) && `使いどころ：${field(row, "使いどころ", 100)}`,
  ].filter(Boolean);
  const tags = Array.isArray(row["タグ"])
    ? row["タグ"]
        .filter((v): v is string => typeof v === "string")
        .map((v) => v.trim().slice(0, 20))
        .filter(Boolean)
        .slice(0, 10)
    : [];
  const category = field(row, "分類", 12) || undefined;
  return {
    text: `${extra}${parts.join("｜")}`.slice(0, 900),
    tags,
    ...(category ? { category } : {}),
  };
}

async function describePictures(
  files: string[],
  prompt: string,
  exceptText?: string,
): Promise<string> {
  const examples = await correctionExamples(6, exceptText).catch(() => "");
  const images = await Promise.all(
    files.map(async (file) => (await fs.readFile(file)).toString("base64")),
  );
  const res = await fetch(`http://127.0.0.1:${GEMMA_PORT}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify({
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: (examples ? `${DESCRIBE_HINT}${examples}\n\n` : "") + prompt },
            ...images.map((data) => ({
              type: "image_url",
              image_url: { url: `data:image/jpeg;base64,${data}` },
            })),
          ],
        },
      ],
      max_tokens: 900,
      temperature: 0.1,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(240_000),
  });
  if (!res.ok) throw new Error(`画像を読むモデルが答えませんでした（${res.status}）`);
  const data = (await res.json()) as { choices?: Array<{ message?: { content?: string } }> };
  const text = (data.choices?.[0]?.message?.content ?? "").trim();
  if (!text) throw new Error("画像の説明が空でした");
  return text;
}

/** One question to the local Gemma worker (text only). */
export async function askText(prompt: string, maxTokens = 200): Promise<string> {
  const res = await fetch(`http://127.0.0.1:${GEMMA_PORT}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify({
      messages: [{ role: "user", content: prompt }],
      max_tokens: maxTokens,
      temperature: 0.1,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(120_000),
  });
  if (!res.ok) throw new Error(`文章のモデルが答えませんでした（${res.status}）`);
  const data = (await res.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return (data.choices?.[0]?.message?.content ?? "").trim();
}

const stamp = (text: string): number => {
  const [h, m, sec] = text.split(":").map(Number);
  return (h ?? 0) * 3600 + (m ?? 0) * 60 + (sec ?? 0);
};

/** The words in a sound with their times ("0:03「…」"), by Whisper large-v3-turbo on the CPU (about 10 s a clip); [] when none. */
async function transcribe(file: string, work: string): Promise<string[]> {
  try {
    await fs.access(WHISPER_MODEL);
  } catch {
    return [];
  }
  const wav = path.join(work, "speech.wav");
  await run(
    FFMPEG,
    ["-y", "-loglevel", "error", "-i", file, "-t", "1800", "-ac", "1", "-ar", "16000", wav],
    5 * 60_000,
  );
  const out = await new Promise<string>((resolve) => {
    execFile(
      CRISPASR,
      ["-m", WHISPER_MODEL, "-l", "ja", "-t", "6", "-f", wav],
      { windowsHide: true, timeout: 40 * 60_000, maxBuffer: 16 * 1024 * 1024 },
      (_error, stdout) => resolve(String(stdout ?? "")),
    );
  });
  const segments: string[] = [];
  for (const match of out.matchAll(/\[(\d+:\d+:\d+(?:\.\d+)?)\s*-->\s*[\d:.]+\]\s*(.*)/g)) {
    const text = (match[2] ?? "").replace(/\s+/g, " ").trim();
    if (!text || INVENTED_LINES.test(text)) continue;
    segments.push(`${clock(stamp(match[1] ?? "0:0:0"))}「${text.slice(0, 80)}」`);
  }
  return segments.slice(0, 400);
}

/** Length, loudness, silence, and the words of a sound split by time. */
async function measureSound(
  file: string,
  work: string,
  name: string,
  heard?: ClapResult,
  alike?: { label: string; similarity: number; category?: string },
  taughtSounds: TaughtSound[] = [],
  spokenWords?: string[],
): Promise<ShelfDetail> {
  const seconds = await durationSeconds(file);
  const out = await run(
    FFMPEG,
    [
      "-hide_banner",
      "-nostats",
      "-i",
      file,
      "-af",
      "ebur128=peak=true,silencedetect=n=-45dB:d=0.3",
      "-f",
      "null",
      "-",
    ],
    5 * 60_000,
  );
  const loudness = [...out.matchAll(/^\s*I:\s*(-?[\d.]+) LUFS/gm)].at(-1)?.[1];
  const peak = [...out.matchAll(/^\s*Peak:\s*(-?[\d.]+) dBFS/gm)].at(-1)?.[1];
  const silent = [...out.matchAll(/silence_duration: ([\d.]+)/g)].reduce(
    (sum, m) => sum + Number(m[1]),
    0,
  );
  const pieces = [...out.matchAll(/silence_end: ([\d.]+)/g)].length;
  const share = seconds > 0 ? Math.min(100, Math.round((silent / seconds) * 100)) : 0;
  // Words already read on the GPU are used; otherwise the CPU reads them. A sound that is nearly all silence has nothing to read.
  const words =
    spokenWords ??
    (seconds >= 0.8 && share < 95 ? await transcribe(file, work).catch(() => [] as string[]) : []);
  const kind = seconds < 2 ? "短い音" : seconds > 20 ? "長い音" : "数秒〜20秒の音";
  const astText = heard?.ast?.length
    ? heard.ast.map((tag) => `${tag.label}(${Math.round(tag.score * 100)}%)`).join("、")
    : "";
  const vadText = heard?.vad?.length
    ? heard.vad.map(([from, to]) => `${from}〜${to}秒`).join(" ")
    : "";
  // Emotion and pitch say something only for a voice.
  const hasVoice =
    Boolean(heard?.vad?.length) &&
    Boolean(
      heard?.ast?.some((tag) =>
        /speech|voice|talk|conversation|narration|sing|laugh|cry|shout|yell|whisper|humming|human/i.test(
          tag.label,
        ),
      ),
    );
  const emotionText =
    hasVoice && heard?.emotion?.length
      ? heard.emotion.map((tag) => `${tag.label}(${Math.round(tag.score * 100)}%)`).join("、")
      : "";
  const pitchText =
    hasVoice && heard?.pitch
      ? `声の高さ 中心${heard.pitch.median}Hz（${heard.pitch.low}〜${heard.pitch.high}Hz）`
      : "";
  const heardText = heard?.tags.length
    ? heard.tags.map((tag) => `${tag.ja}(${Math.round(tag.score * 100)}%)`).join("、")
    : "";
  const alikeText = alike
    ? `あなたが教えた「${alike.label.slice(0, 40)}」に似ている(${Math.round(alike.similarity * 100)}%)`
    : "";
  const facts = [
    `長さ約${seconds.toFixed(1)}秒（${kind}）`,
    loudness ? `音量 ${loudness} LUFS${peak ? `・最大 ${peak} dBFS` : ""}` : "",
    `無音 約${share}%${pieces > 0 ? `・音の区間 約${pieces + 1}個` : ""}`,
    heardText ? `音の分類モデルの推測：${heardText}` : "",
    astText ? `音の種類（AudioSet）：${astText}` : "",
    vadText ? `声や音のある区間：${vadText}` : "",
    emotionText ? `声の感情（目安）：${emotionText}` : "",
    pitchText,
    alikeText,
    words.length
      ? `言葉（参考）：${words.slice(0, 12).join(" ")}${words.length > 12 ? `…ほか${words.length - 12}件` : ""}`
      : "",
  ].filter(Boolean);
  // Gemma weighs everything together with the file name and decides the kind and a one-line description.
  let category = words.length ? "話し声" : seconds < 3 ? "効果音" : "環境音・音楽";
  let summary = "";
  let tags = [
    category,
    kind,
    ...(heard?.tags.map((tag) => tag.ja) ?? []),
    ...(share > 50 ? ["無音が多い"] : []),
  ];
  // A sound that was listened to already has its kind, words, feeling and strength from the models above, stretch by
  // stretch; the small text model's one-line description (about 20 s a sound on the CPU) adds little to that, so it is
  // skipped unless OPENCLAW_SHELF_DESCRIBE=1. The kind of the whole comes from AudioSet and the words.
  const describe = !heard || process.env.OPENCLAW_SHELF_DESCRIBE === "1";
  if (!describe) {
    const whole = classifyPart({ text: words.join(" "), ast: heard?.ast });
    if (whole.kind !== "その他") category = whole.kind;
    tags = [
      category,
      kind,
      ...(heard?.tags.map((tag) => tag.ja) ?? []),
      ...(share > 50 ? ["無音が多い"] : []),
    ];
  }
  try {
    if (!describe) throw new Error("not asked");
    const answer = await askText(
      `音のファイルを分類してください。手がかり：\nファイル名：${name}\n${facts.join("\n")}\n` +
        `JSONだけで答える。形: {"分類":"${SOUND_KINDS.join("／")} のどれか1つ","説明":"何の音か、どんな場面に合うかを1〜2文","タグ":["検索用の短い言葉を3〜8個"]}。` +
        "手がかりが弱いときは、分からないと書き、想像で足さない。",
      300,
    );
    const start = answer.indexOf("{");
    const end = answer.lastIndexOf("}");
    const row =
      start >= 0 && end > start
        ? (JSON.parse(answer.slice(start, end + 1)) as Record<string, unknown>)
        : {};
    if (typeof row["分類"] === "string" && row["分類"].trim())
      category = row["分類"].trim().slice(0, 12);
    if (typeof row["説明"] === "string")
      summary = row["説明"].replace(/\s+/g, " ").trim().slice(0, 200);
    if (Array.isArray(row["タグ"])) {
      tags = [
        category,
        ...row["タグ"]
          .filter((v): v is string => typeof v === "string")
          .map((v) => v.trim().slice(0, 20))
          .filter(Boolean),
      ].slice(0, 10);
    }
  } catch {
    // the facts alone are a usable note
  }
  // A sound the user taught that this one is much like decides the kind, ahead of the model's guess.
  const taughtKind = alike?.category && alike.category.trim() ? alike.category.trim() : undefined;
  if (taughtKind) {
    category = taughtKind;
    tags = [category, ...tags.filter((tag) => tag !== category)].slice(0, 10);
  }
  // Every stretch is read on its own; a long file is filed under the kind that fills most of its time.
  const parts = heard?.segments?.length
    ? buildParts({
        segments: heard.segments,
        transcript: parseTranscript(words),
        taught: (embedding) => nearestTaught(embedding, taughtSounds, "", 0.85)?.category,
      })
    : [];
  const dominant = dominantKind(parts);
  if (!taughtKind && dominant && dominant !== "その他" && parts.length > 1) {
    category = dominant;
    tags = [category, ...tags.filter((tag) => tag !== category)].slice(0, 10);
  }
  const sound = buildSoundRecord({
    kind: category,
    source: taughtKind ? "taught" : "guess",
    durationSec: seconds,
    transcript: words,
    heard,
    hasVoice,
    loudnessLufs: loudness === undefined ? undefined : Number(loudness),
    parts,
  });
  const text = [
    `${category}${summary ? `：${summary}` : ""}`,
    ...facts,
    `強さ ${sound.intensity}/100`,
  ].join("｜");
  return { text: text.slice(0, 900), tags, category, sound };
}

/** What a look into one shelf item found (a picture or video is described by the vision worker, a sound is measured). */
export async function inspectDetail(
  item: ShelfItem,
  exceptText?: string,
  listened?: {
    heard?: ClapResult;
    alike?: { label: string; similarity: number; category?: string };
    taught?: TaughtSound[];
    words?: string[];
  },
): Promise<ShelfDetail | undefined> {
  if (item.kind === "doc") return undefined;
  const work = await fs.mkdtemp(path.join(os.tmpdir(), "shelf-inspect-"));
  try {
    if (item.kind === "audio")
      return await measureSound(
        item.path,
        work,
        item.name,
        listened?.heard,
        listened?.alike,
        listened?.taught,
        listened?.words,
      );
    if (item.kind === "image") {
      const jpg = path.join(work, "a.jpg");
      await smallJpeg(item.path, jpg);
      return detailFromAnswer(await describePictures([jpg], IMAGE_PROMPT, exceptText));
    }
    const seconds = await durationSeconds(item.path);
    const count = Math.max(3, Math.min(6, Math.ceil(seconds / 4)));
    const moments = Array.from({ length: count }, (_, index) => ((index + 0.5) / count) * seconds);
    const frames = await Promise.all(
      moments.map(async (at, index) => {
        const jpg = path.join(work, `f${index}.jpg`);
        await smallJpeg(item.path, jpg, Math.max(0, at));
        return jpg;
      }),
    );
    const times = moments.map((at) => `${at.toFixed(0)}秒`).join("、");
    return detailFromAnswer(
      await describePictures(frames, VIDEO_PROMPT(times), exceptText),
      `長さ約${seconds.toFixed(1)}秒｜`,
    );
  } finally {
    await fs.rm(work, { recursive: true, force: true });
  }
}

/** The note text of one shelf item. */
export async function inspectItem(
  item: ShelfItem,
  exceptText?: string,
): Promise<string | undefined> {
  if (item.kind !== "doc") await ensureGemmaWorker();
  return (await inspectDetail(item, exceptText))?.text;
}

let running: Promise<void> | undefined;
export let inspectProgress: { done: number; total: number; failed: number; running: boolean } = {
  done: 0,
  total: 0,
  failed: 0,
  running: false,
};

/** Describes up to `limit` items that have no (or an old) note, one after another, in the background. Returns how many wait. */
export async function startShelfInspect(
  items: readonly ShelfItem[],
  limit: number,
): Promise<{ waiting: number; started: number; alreadyRunning: boolean }> {
  const notes = await loadShelfNotes();
  // A sound whose note was made before the structured record existed is looked into again.
  const waiting = items.filter(
    (item) =>
      item.kind !== "doc" &&
      (notes[item.id]?.mtimeMs !== item.mtimeMs ||
        (item.kind === "audio" && !notes[item.id]?.sound)),
  );
  if (running) return { waiting: waiting.length, started: 0, alreadyRunning: true };
  const batch = waiting.slice(0, Math.max(1, Math.min(limit, 500)));
  if (batch.length === 0) return { waiting: 0, started: 0, alreadyRunning: false };
  inspectProgress = { done: 0, total: batch.length, failed: 0, running: true };
  running = (async () => {
    // 画像・動画・音の説明を書くワーカーが止まっていると、全部が黙って失敗するので先に起こす。
    await ensureGemmaWorker();
    // Groups of 20: the sounds of a group are listened to in one run of the sound model (loading it takes a while), then
    // each is described and saved. A group that fails does not lose the others, and what is done stays done. The
    // sounds the user described are the examples.
    const GROUP = 20;
    for (let from = 0; from < batch.length; from += GROUP) {
      const group = batch.slice(from, from + GROUP);
      const heard = await listenToSounds(group.filter((item) => item.kind === "audio")).catch(
        () => new Map<string, ClapResult>(),
      );
      // The words of the group are read together on the GPU; a sound it could not read falls back to the CPU reader.
      const spoken = await transcribeSounds(group.filter((item) => item.kind === "audio")).catch(
        () => new Map<string, string[]>(),
      );
      const vectors = await loadVectors();
      const taught = items
        // A long file is a poor example (its vector is the average of everything in it): only short sounds teach.
        .filter(
          (item) =>
            item.kind === "audio" &&
            item.verified &&
            item.note &&
            vectors[item.id] &&
            (item.sound?.durationSec ?? 0) <= 20,
        )
        .map((item) => ({
          id: item.id,
          label: item.note as string,
          category: item.category,
          embedding: vectors[item.id]!.embedding,
        }));
      // Two sounds at a time: the small text model that writes the one-line descriptions has two places (--parallel 2).
      let next = 0;
      const lane = async (): Promise<void> => {
        for (let item = group[next++]; item; item = group[next++]) {
          try {
            const own = heard.get(item.id);
            // A sound that could not be listened to is left for the next run, not saved as if it had been looked into.
            if (item.kind === "audio" && !own) {
              inspectProgress.failed += 1;
              continue;
            }
            const alike =
              own && !item.verified ? nearestTaught(own.embedding, taught, item.id) : undefined;
            const detail = await inspectDetail(item, undefined, {
              heard: own,
              alike,
              taught: taught.filter((example) => example.category),
              words: spoken.get(item.id),
            });
            if (detail) {
              await saveShelfNote(item.id, {
                mtimeMs: item.mtimeMs,
                text: detail.text,
                tags: detail.tags,
                ...(detail.category ? { category: detail.category } : {}),
                ...(detail.sound ? { sound: detail.sound } : {}),
              });
            }
            inspectProgress.done += 1;
          } catch {
            inspectProgress.failed += 1;
          }
        }
      };
      await Promise.all([lane(), lane()]);
    }
  })().finally(() => {
    inspectProgress.running = false;
    running = undefined;
  });
  return { waiting: waiting.length, started: batch.length, alreadyRunning: false };
}

/** The user's word on one file: "ok" (the guess is right), "wrong" (do not use it), "clear" (back to the guess), a text of their own. */
export function reviewShelfItem(
  item: { id: string; mtimeMs: number },
  review: { verdict?: "ok" | "wrong" | "clear"; text?: string; kind?: string; part?: number },
): Promise<void> {
  return withNotesLock(() => applyShelfReview(item, review));
}

async function applyShelfReview(
  item: { id: string; mtimeMs: number },
  review: { verdict?: "ok" | "wrong" | "clear"; text?: string; kind?: string; part?: number },
): Promise<void> {
  const notes = await loadShelfNotes();
  const before =
    notes[item.id] && notes[item.id]!.mtimeMs === item.mtimeMs
      ? notes[item.id]!
      : { mtimeMs: item.mtimeMs, text: "" };
  const next: ShelfNote = { ...before };
  if (review.verdict === "clear") {
    delete next.status;
    delete next.userText;
    delete next.userKind;
    delete next.partKinds;
  } else if (review.verdict) {
    next.status = review.verdict;
  }
  const text = review.text?.replace(/\s+/g, " ").trim().slice(0, 400);
  if (text) {
    next.userText = text;
    delete next.status; // a text of the user's own is the answer; it needs no verdict
  }
  // The kind of a sound chosen by the user wins over the guess, and counts as the user's word (the sound then teaches the others).
  const kind = review.kind?.replace(/\s+/g, " ").trim().slice(0, 20);
  if (kind && review.part !== undefined && Number.isInteger(review.part) && review.part >= 0) {
    // The kind of one stretch of a long sound.
    next.partKinds = { ...next.partKinds, [review.part]: kind };
  } else if (kind) {
    next.userKind = kind;
    if (!next.userText && next.status !== "wrong") next.status = "ok";
  }
  notes[item.id] = next;
  await fs.mkdir(path.dirname(notesFile()), { recursive: true });
  await fs.writeFile(notesFile(), JSON.stringify(notes), "utf-8");
}

/** Cases where the user corrected a guess: shown to the vision worker so the same mistake is made less often. */
export async function correctionExamples(limit = 6, exceptText?: string): Promise<string> {
  const notes = Object.values(await loadShelfNotes()).filter(
    (n) => n.userText && n.text && n.text !== n.userText && n.userText !== exceptText,
  );
  return notes
    .slice(-limit)
    .map(
      (n) => `・間違えた説明「${n.text.slice(0, 80)}」→ 正しくは「${n.userText!.slice(0, 120)}」`,
    )
    .join("\n");
}
