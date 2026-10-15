import * as fs from "node:fs/promises";
import * as path from "node:path";
import { runTagger } from "../character-creator.js";
import { getCharacter } from "../comic-characters.js";
import { firstJsonObject } from "../comic-series.js";
import { motionImageNegative, type MotionScript } from "../motion-comic.js";
import { MOTION_SCRIPT, MOTION_TIMEOUT_MS, motionModelFile, WAN2GP_PYTHON } from "./config.js";
import { acquireGpu } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { askScriptWriter } from "./manga.js";
import { exists, outDir, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import { getMediaJob } from "./state.js";
import type { MediaJob, MediaOptions } from "./types.js";
import { VOICE_FFMPEG } from "./voice-drama.js";

// ---------- joining finished films ----------

const FFPROBE = path.join(path.dirname(VOICE_FFMPEG), "ffprobe.exe");

/** Cuts a bridge is made of: about 4 seconds each, up to 81 frames (16 fps) per cut. */
export function bridgePlan(seconds: number): { segments: number; frames: number } {
  const total = Math.min(60, Math.max(2, seconds));
  const segments = Math.max(1, Math.round(total / 4));
  const frames = Math.min(81, Math.max(33, Math.round((total / segments) * 16) + 1));
  return { segments, frames };
}

/** About how long the bridges and the final join take. */
export function joinEstimateSec(count: number, seconds: number): number {
  const { segments, frames } = bridgePlan(seconds);
  const perCut = 330 + frames * 8;
  return Math.max(0, count - 1) * (segments * perCut + 150 + (segments - 1) * 60) + 240;
}

async function probeVideo(
  file: string,
): Promise<{ width: number; height: number; seconds: number; audio: boolean }> {
  const lines: string[] = [];
  await runTool(
    FFPROBE,
    [
      "-v",
      "error",
      "-show_entries",
      "stream=codec_type,width,height:format=duration",
      "-of",
      "json",
      file,
    ],
    { onLine: (line) => lines.push(line), timeoutMs: 60_000 },
  );
  const data = JSON.parse(lines.join("")) as {
    streams?: Array<{ codec_type?: string; width?: number; height?: number }>;
    format?: { duration?: string };
  };
  const video = data.streams?.find((s) => s.codec_type === "video");
  return {
    width: video?.width ?? 1280,
    height: video?.height ?? 720,
    seconds: Number(data.format?.duration ?? 0) || 0,
    audio: data.streams?.some((s) => s.codec_type === "audio") ?? false,
  };
}

const BRIDGE_QUALITY = "masterpiece, best quality, amazing quality, very aesthetic, absurdres";

type BridgeStep = { tags: string; motion: string };

/** The writer looks at the last picture of the first film and the first picture of the next, and plans the moves between. */
async function planBridge(
  tagsA: string,
  tagsB: string,
  segments: number,
  contextA: string,
  contextB: string,
): Promise<BridgeStep[]> {
  const fallback: BridgeStep[] = Array.from({ length: segments }, () => ({
    tags: "",
    motion:
      "the scene changes smoothly from the first picture to the last picture, the two people move naturally into the new position, fixed camera",
  }));
  const system =
    "あなたは成人向けアニメ動画の編集者。2つの場面の間をつなぐ、自然な動きの流れを考える。出力はJSONオブジェクト1つだけ（説明や```は書かない）: " +
    '{"summary":"つなぎの流れの日本語1行","steps":[{"tags":"その区間が終わった時点の絵のdanbooruタグ（英語・カンマ区切り。人物の見た目、体位、場所、状態を全部書く）","motion":"その区間の動き（英語の短い文。固定カメラ）"}]}。' +
    "steps はちょうど指定された数。最後の step の tags は、つなぎ先の絵と同じ状態になる想定で書く（絵は使われない）。" +
    "体位が違うなら体位の移動（離れる、向きを変える、四つん這いになる、またがる など）、挿入されているなら抜く・入れ直す、射精後なら余韻から次の動作への移り、場所が違うなら移動、服が違うなら着替えや脱ぐ動き、を順に入れる。" +
    "同じ人物のまま、見た目（髪・目・体型・服）を変えない。急に場面が飛ばないよう、1区間で1つの動きにする。";
  const user =
    `つなぐ区間の数: ${segments}\n【前の場面の最後の絵のタグ】${tagsA}${contextA ? `\n【前の場面の情報】${contextA}` : ""}\n` +
    `【次の場面の最初の絵のタグ】${tagsB}${contextB ? `\n【次の場面の情報】${contextB}` : ""}`;
  try {
    const data = firstJsonObject(await askScriptWriter(system, user, 1_200));
    const steps = Array.isArray(data.steps) ? (data.steps as Array<Record<string, unknown>>) : [];
    const cleaned = steps.slice(0, segments).map((s) => ({
      tags: String(s.tags ?? "")
        .replace(/[^a-zA-Z0-9 ,()'.:_\-]/g, " ")
        .replace(/\s+/g, " ")
        .slice(0, 400)
        .trim(),
      motion: String(s.motion ?? "")
        .replace(/[^a-zA-Z0-9 ,()'.:_\-]/g, " ")
        .replace(/\s+/g, " ")
        .slice(0, 300)
        .trim(),
    }));
    while (cleaned.length < segments) cleaned.push(fallback[0] as BridgeStep);
    return cleaned.map((s) => (s.motion ? s : (fallback[0] as BridgeStep)));
  } catch {
    return fallback;
  }
}

async function stateContext(job: MediaJob | undefined): Promise<string> {
  const dir = job?.motion?.dir;
  if (!dir) return "";
  try {
    const state = JSON.parse(await fs.readFile(path.join(dir, "state.json"), "utf-8")) as {
      script?: MotionScript;
    };
    const s = state.script;
    return s ? `${s.title}／${s.name}／体位 ${s.position}／場所 ${s.place}` : "";
  } catch {
    return "";
  }
}

/** One bridge: pictures between, a video for each stretch, and the stretches put together. */
async function makeBridge(
  job: MediaJob,
  dir: string,
  index: number,
  label: string,
  from: { file: string; job: MediaJob },
  to: { file: string; job: MediaJob },
  segments: number,
  frames: number,
): Promise<string> {
  const id = (n: number) => `p${index}-s${n}`;
  const tail = path.join(dir, `${id(0)}.png`);
  const head = path.join(dir, `${id(segments)}.png`);
  markStage(job, `${label}：前後の絵を取り出しています`);
  const fit =
    "scale=1344:768:force_original_aspect_ratio=decrease,pad=1344:768:(ow-iw)/2:(oh-ih)/2,setsar=1";
  await runTool(
    VOICE_FFMPEG,
    [
      "-y",
      "-loglevel",
      "error",
      "-sseof",
      "-0.4",
      "-i",
      from.file,
      "-vf",
      fit,
      "-update",
      "1",
      "-frames:v",
      "999",
      tail,
    ],
    { timeoutMs: 120_000 },
  );
  await runTool(
    VOICE_FFMPEG,
    ["-y", "-loglevel", "error", "-i", to.file, "-vf", fit, "-frames:v", "1", head],
    { timeoutMs: 120_000 },
  );
  for (const n of [0, segments]) {
    await runTool(
      VOICE_FFMPEG,
      [
        "-y",
        "-loglevel",
        "error",
        "-i",
        path.join(dir, `${id(n)}.png`),
        "-vf",
        "scale=832:480",
        path.join(dir, `${id(n)}-v.png`),
      ],
      { timeoutMs: 60_000 },
    );
  }

  markStage(job, `${label}：場面を解析して、つなぎを考えています`);
  const tagsOf = async (file: string) =>
    (
      await runTagger(file).catch(() => ({ general: [] as Array<[string, number]>, character: [] }))
    ).general
      .slice(0, 45)
      .map(([tag]) => tag)
      .join(", ");
  const tagsA = await tagsOf(tail);
  const tagsB = await tagsOf(head);
  const steps = await planBridge(
    tagsA,
    tagsB,
    segments,
    await stateContext(from.job),
    await stateContext(to.job),
  );

  const baseSeed = (Number.parseInt(job.id.slice(0, 6), 16) % 90_000) + index * 100;
  const options = from.job.options;
  const sourceState = await fs
    .readFile(path.join(from.job.motion?.dir ?? "", "state.json"), "utf-8")
    .then((text) => JSON.parse(text) as { options?: MediaOptions })
    .catch(() => undefined);
  const heroine = (sourceState?.options ?? options).motionCharacterId
    ? await getCharacter((sourceState?.options ?? options).motionCharacterId ?? "")
    : undefined;
  const keys = Array.from({ length: Math.max(0, segments - 1) }, (_, k) => ({
    id: id(k + 1),
    mode: "i2i" as const,
    src: id(k),
    strength: 0.72,
    seed: baseSeed + k + 1,
    prompt: `${BRIDGE_QUALITY}, ${steps[k]?.tags || tagsA}`,
  }));
  const cuts = steps.map((step, k) => ({
    name: `b${index}-${k}`,
    start: id(k),
    end: id(k + 1),
    prompt: `anime, ${step.motion}, smooth natural motion, fixed camera`,
    seed: baseSeed + 50 + k,
    frames,
  }));
  const bridgeName = `bridge-${index}.mp4`;
  const request = path.join(dir, `bridge-${index}.json`);
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(sourceState?.options?.motionImageModel),
      negative: [motionImageNegative(), heroine?.spec?.customNegative].filter(Boolean).join(", "),
      keys,
      loras:
        heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : [],
      cuts,
      plan: {
        name: "bridge",
        out: bridgeName,
        cuts: cuts.map((cut) => ({
          clip: `${cut.name}.webm`,
          loop: false,
          seconds: (frames - 1) / 16,
          lines: [],
        })),
      },
    }),
  );
  let produced = "";
  await runTool(WAN2GP_PYTHON, [MOTION_SCRIPT, request], {
    cwd: path.dirname(MOTION_SCRIPT),
    timeoutMs: MOTION_TIMEOUT_MS,
    onLine: (line) => {
      if (line.startsWith("STAGE ")) markStage(job, `${label}：${line.slice(6)}`);
      else if (line.startsWith("RESULT ")) produced = line.slice(7).trim();
    },
  });
  if (!produced || !(await exists(produced))) throw new Error("つなぎの動画ができませんでした");
  return produced;
}

/**
 * Joins finished films in the order given. Between two films a bridge is made: the last picture of
 * the first and the first picture of the next are read, the writer plans the moves between them
 * (changing position, pulling out or entering, moving to another place), pictures and short videos
 * are made for each step, and everything is put together. The result is a film too, so it can be
 * joined again, without a limit on how long it grows.
 */
export async function runMotionJoin(job: MediaJob): Promise<void> {
  const ids = job.options.motionJoinJobIds ?? [];
  if (ids.length < 2) throw new Error("つなぐ動画を2本以上選んでください");
  const dir = path.join(outDir(), `${job.id}-join`);
  await fs.mkdir(dir, { recursive: true });
  const sources: Array<{ file: string; job: MediaJob }> = [];
  for (const [i, id] of ids.entries()) {
    const src = await getMediaJob(id);
    if (
      !src ||
      src.status !== "done" ||
      !src.file ||
      !src.mime?.startsWith("video/") ||
      !(await exists(src.file))
    ) {
      throw new Error(`つなぐ動画が見つかりません（${id}）。作っている途中か、失敗しています`);
    }
    const trim = job.options.motionJoinTrimSec?.[i];
    if (typeof trim === "number" && trim > 0) {
      // Only the first part of this film is kept: the other position takes over from there.
      const cut = path.join(dir, `trim-${i}.mp4`);
      if (!(await exists(cut))) {
        markStage(job, `${i + 1}本目を ${Math.round(trim)} 秒で区切っています`);
        await runTool(
          VOICE_FFMPEG,
          [
            "-y",
            "-loglevel",
            "error",
            "-i",
            src.file,
            "-t",
            String(trim),
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            "17",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            cut,
          ],
          { timeoutMs: 20 * 60_000 },
        );
      }
      sources.push({ file: cut, job: src });
    } else {
      sources.push({ file: src.file, job: src });
    }
  }
  const { segments, frames } = bridgePlan(job.options.motionBridgeSeconds ?? 3);
  job.motion = { dir };
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();

  const bridges: string[] = [];
  for (let i = 0; i < sources.length - 1; i++) {
    if (job.cancelRequested) throw new Error("中止しました");
    const done = path.join(dir, `bridge-${i}.mp4`);
    const label = `つなぎ目 ${i + 1}/${sources.length - 1}`;
    bridges.push(
      (await exists(done))
        ? done
        : await makeBridge(
            job,
            dir,
            i,
            label,
            sources[i] as (typeof sources)[number],
            sources[i + 1] as (typeof sources)[number],
            segments,
            frames,
          ),
    );
  }

  markStage(job, "動画を1本につないでいます");
  const segmentsList: string[] = [];
  sources.forEach((source, i) => {
    segmentsList.push(source.file);
    if (bridges[i]) segmentsList.push(bridges[i] as string);
  });
  const probes = await Promise.all(segmentsList.map((file) => probeVideo(file)));
  const first = probes[0] as (typeof probes)[number];
  const W = first.width - (first.width % 2);
  const H = first.height - (first.height % 2);
  const args: string[] = ["-y", "-loglevel", "error"];
  const filters: string[] = [];
  let inputIndex = 0;
  segmentsList.forEach((file, i) => {
    const probe = probes[i] as (typeof probes)[number];
    args.push("-i", file);
    const video = inputIndex++;
    filters.push(
      `[${video}:v]scale=${W}:${H}:force_original_aspect_ratio=decrease,pad=${W}:${H}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p[v${i}]`,
    );
    if (probe.audio) {
      filters.push(
        `[${video}:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo[a${i}]`,
      );
    } else {
      args.push(
        "-f",
        "lavfi",
        "-t",
        String(Math.max(0.1, probe.seconds)),
        "-i",
        "anullsrc=r=48000:cl=stereo",
      );
      const silent = inputIndex++;
      filters.push(`[${silent}:a]aformat=sample_fmts=fltp:channel_layouts=stereo[a${i}]`);
    }
  });
  filters.push(
    `${segmentsList.map((_, i) => `[v${i}][a${i}]`).join("")}concat=n=${segmentsList.length}:v=1:a=1[v][a]`,
  );
  const file = await resultPath(job, ".mp4");
  args.push(
    "-filter_complex",
    filters.join(";"),
    "-map",
    "[v]",
    "-map",
    "[a]",
    "-c:v",
    "libx264",
    "-preset",
    "medium",
    "-crf",
    "18",
    "-pix_fmt",
    "yuv420p",
    "-c:a",
    "aac",
    "-b:a",
    "192k",
    "-movflags",
    "+faststart",
    file,
  );
  await runTool(VOICE_FFMPEG, args, { timeoutMs: 120 * 60_000 });
  job.file = file;
  job.mime = "video/mp4";
  job.manga = { title: `つないだ動画（${sources.length}本）`, lines: [] };
}
