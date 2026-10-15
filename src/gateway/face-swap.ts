import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Face swap: FaceFusion (outside the repo) swaps the faces of a picture or a video. The inputs
// (a video or picture, and one face picture per person) live in ~/.openclaw/media/swap-input; the
// app uploads them there in pieces, or points at a finished video in the library.

export const FACEFUSION_DIR = "D:\\media-tools\\FaceFusion";
export const FACEFUSION_PYTHON = path.join(FACEFUSION_DIR, "venv", "Scripts", "python.exe");
const FACES_SCRIPT = path.join(FACEFUSION_DIR, "openclaw_faces.py");
const FFMPEG_DIR = "D:\\media-tools\\Wan2GP\\ffmpeg_bins";
const FFPROBE = path.join(FFMPEG_DIR, "ffprobe.exe");
const FFMPEG = path.join(FFMPEG_DIR, "ffmpeg.exe");

export type SwapQuality = "fast" | "standard" | "high";
export const SWAP_QUALITIES: readonly SwapQuality[] = ["fast", "standard", "high"];

export type SwapSource = {
  /** Which face of the target this person replaces: 0 = the leftmost face in the reference picture. */
  position?: number;
  /** A face from the character library… */
  characterId?: string;
  /** …or a picture in the swap-input folder ("in:<file>"). */
  inputId?: string;
};

export type SwapOptions = {
  /** "in:<file>" in the swap-input folder, or "job:<id>" for a finished library video or picture. */
  swapTarget?: string;
  swapSources?: SwapSource[];
  swapEnhancer?: boolean;
  swapQuality?: SwapQuality;
  /** Only this part of the video (seconds). */
  swapTrimStartSec?: number;
  swapTrimEndSec?: number;
  /** The moment the face positions were looked at (seconds). */
  swapReferenceSec?: number;
};

export type MediaProbe = {
  kind: "video" | "image";
  durationSec: number;
  fps: number;
  width: number;
  height: number;
  frames: number;
};

export function swapInputDir(): string {
  return path.join(resolveOpenClawHome(), "media", "swap-input");
}

/** A file name from the app, reduced to something safe inside the input folder. */
export function safeInputName(raw: string): string {
  const base = path
    .basename(raw.replace(/\\/g, "/"))
    .replace(/[^\p{L}\p{N}._ -]/gu, "_")
    .trim();
  return base.slice(-80) || "file";
}

const INPUT_EXT = /\.(mp4|mov|mkv|webm|avi|m4v|png|jpe?g|webp|bmp)$/i;

/** Receives one piece of an uploaded file; the last piece closes it. */
export async function saveSwapChunk(params: {
  name: string;
  index: number;
  total: number;
  dataBase64: string;
}): Promise<{ inputId: string; done: boolean; bytes: number }> {
  const name = safeInputName(params.name);
  if (!INPUT_EXT.test(name))
    throw new Error("動画（mp4/mov/mkv/webm）か画像（png/jpg/webp）を選んでください");
  if (
    !Number.isInteger(params.index) ||
    !Number.isInteger(params.total) ||
    params.index < 0 ||
    params.index >= params.total
  ) {
    throw new Error("アップロードの番号が正しくありません");
  }
  const dir = swapInputDir();
  await fs.mkdir(dir, { recursive: true });
  const part = path.join(dir, `${name}.part`);
  const data = Buffer.from(params.dataBase64, "base64");
  if (params.index === 0) await fs.writeFile(part, data);
  else await fs.appendFile(part, data);
  const done = params.index === params.total - 1;
  if (done) await fs.rename(part, path.join(dir, name));
  const bytes = (await fs.stat(done ? path.join(dir, name) : part)).size;
  return { inputId: `in:${name}`, done, bytes };
}

export async function listSwapInputs(): Promise<
  Array<{ inputId: string; name: string; bytes: number; modifiedAt: number }>
> {
  const dir = swapInputDir();
  const names = await fs.readdir(dir).catch(() => [] as string[]);
  const out: Array<{ inputId: string; name: string; bytes: number; modifiedAt: number }> = [];
  for (const name of names) {
    if (!INPUT_EXT.test(name)) continue;
    const stat = await fs.stat(path.join(dir, name)).catch(() => undefined);
    if (stat?.isFile())
      out.push({ inputId: `in:${name}`, name, bytes: stat.size, modifiedAt: stat.mtimeMs });
  }
  return out.toSorted((a, b) => b.modifiedAt - a.modifiedAt);
}

export async function deleteSwapInput(inputId: string): Promise<boolean> {
  const file = resolveInputFile(inputId);
  if (!file) return false;
  await fs.rm(file, { force: true });
  return true;
}

/** "in:<file>" → the file in the input folder (nothing else is reachable). */
export function resolveInputFile(inputId: string | undefined): string | undefined {
  const match = /^in:(.+)$/.exec(inputId ?? "");
  if (!match?.[1]) return undefined;
  return path.join(swapInputDir(), safeInputName(match[1]));
}

function run(
  exe: string,
  args: string[],
  timeoutMs = 120_000,
  env?: NodeJS.ProcessEnv,
): Promise<string> {
  return new Promise((resolve, reject) => {
    execFile(
      exe,
      args,
      {
        windowsHide: true,
        timeout: timeoutMs,
        maxBuffer: 16 * 1024 * 1024,
        ...(env ? { env, cwd: FACEFUSION_DIR } : {}),
      },
      (err, stdout, stderr) =>
        err
          ? reject(new Error(`${path.basename(exe)}: ${String(stderr || err.message).slice(-400)}`))
          : resolve(stdout),
    );
  });
}

function parseRate(raw: string | undefined): number {
  const [a, b] = (raw ?? "").split("/").map(Number);
  return a && b ? a / b : Number(raw) || 0;
}

/** Length, frame rate and size of a video or picture. */
export async function probeMedia(file: string): Promise<MediaProbe> {
  const json = JSON.parse(
    await run(FFPROBE, [
      "-v",
      "error",
      "-select_streams",
      "v:0",
      "-show_entries",
      "stream=width,height,avg_frame_rate,nb_frames,duration,codec_name:format=duration",
      "-of",
      "json",
      file,
    ]),
  ) as { streams?: Array<Record<string, string>>; format?: { duration?: string } };
  const stream = json.streams?.[0];
  if (!stream) throw new Error("映像が見つかりません");
  const durationSec = Number(stream.duration) || Number(json.format?.duration) || 0;
  const isImage = /\.(png|jpe?g|webp|bmp)$/i.test(file);
  const fps = parseRate(stream.avg_frame_rate) || 30;
  const frames = isImage ? 1 : Number(stream.nb_frames) || Math.round(durationSec * fps);
  return {
    kind: isImage ? "image" : "video",
    durationSec: isImage ? 0 : durationSec,
    fps,
    width: Number(stream.width) || 0,
    height: Number(stream.height) || 0,
    frames,
  };
}

/**
 * Seconds per frame for one pass of the swap at 1080p, measured on the RTX 4060 with FaceFusion's
 * own benchmark (fast = 256 px swapper: 34.6 fps, 12.7 fps with the enhancer; standard = 512 px:
 * 15.5 fps, 2.9 fps with the enhancer; high = 1024 px: 3.9 fps). "high" with the enhancer was not
 * measured; it is the sum of the two. Replaced by the real speed of earlier jobs once there are some.
 */
export const SWAP_SEC_PER_FRAME: Record<SwapQuality, { plain: number; enhanced: number }> = {
  fast: { plain: 0.029, enhanced: 0.079 },
  standard: { plain: 0.065, enhanced: 0.34 },
  high: { plain: 0.26, enhanced: 0.56 },
};

/** Measured on the sample video: 101 s with a face picked by number against 28 s for every face. */
export const REFERENCE_MODE_FACTOR = 4;

/** Fixed time for a start (models loading, joining the pieces). */
export const SWAP_START_SEC = 45;

export type SwapEstimate = {
  frames: number;
  passes: number;
  secPerFrame: number;
  /** Total for this job alone. */
  jobSec: number;
  /** True when secPerFrame comes from real earlier jobs. */
  measured: boolean;
};

/** The frames a job will really process (the trimmed part of the video). */
export function swapFrameCount(probe: MediaProbe, options: SwapOptions): number {
  if (probe.kind === "image") return 1;
  const start = Math.max(0, options.swapTrimStartSec ?? 0);
  const end = Math.min(probe.durationSec, options.swapTrimEndSec ?? probe.durationSec);
  return Math.max(1, Math.round(Math.max(0, end - start) * probe.fps));
}

export function estimateSwap(
  probe: MediaProbe,
  options: SwapOptions,
  measuredSecPerFrame?: number,
): SwapEstimate {
  const quality = options.swapQuality ?? "standard";
  const table = SWAP_SEC_PER_FRAME[quality];
  const base = options.swapEnhancer ? table.enhanced : table.plain;
  // The table is for 1080p. Smaller pictures are faster, but not in proportion (540p measured at
  // about 0.56 of 1080p), so part of the cost is fixed.
  const pixels = Math.max(probe.width * probe.height, 1);
  const scale = Math.min(4, Math.max(0.4, 0.4 + 0.6 * (pixels / (1920 * 1080))));
  const secPerFrame = measuredSecPerFrame ?? base * scale;
  const frames = swapFrameCount(probe, options);
  const sources = options.swapSources ?? [];
  const passes = Math.max(1, sources.length);
  // Picking a face by number (reference mode, also used whenever there are several people) matches
  // faces on every frame; measured about 4-5 times slower than swapping every face.
  const referencePasses = sources.filter(
    (source) => sources.length > 1 || source.position !== undefined,
  ).length;
  const weight = passes - referencePasses + referencePasses * REFERENCE_MODE_FACTOR;
  return {
    frames,
    passes,
    secPerFrame,
    jobSec: Math.round(SWAP_START_SEC * passes + frames * secPerFrame * weight),
    measured: measuredSecPerFrame !== undefined,
  };
}

/** The faces in one moment of a video (or in a picture), cut out and numbered left to right. */
export async function detectSwapFaces(
  file: string,
  atSec: number,
  workDir: string,
): Promise<
  Array<{ index: number; x: number; y: number; w: number; h: number; thumbBase64: string }>
> {
  await fs.mkdir(workDir, { recursive: true });
  const frame = path.join(workDir, "frame.png");
  if (/\.(png|jpe?g|webp|bmp)$/i.test(file)) await fs.copyFile(file, frame);
  else
    await run(FFMPEG, [
      "-y",
      "-loglevel",
      "error",
      "-ss",
      String(Math.max(0, atSec)),
      "-i",
      file,
      "-frames:v",
      "1",
      frame,
    ]);
  const env = { ...process.env, PYTHONUTF8: "1", PATH: await faceFusionPath(process.env.PATH) };
  const out = await run(
    FACEFUSION_PYTHON,
    [FACES_SCRIPT, frame, path.join(workDir, "faces")],
    300_000,
    env,
  );
  const line = out.split(/\r?\n/).find((l) => l.startsWith("FACES "));
  if (!line)
    throw new Error(
      `顔を探せませんでした: ${out.split(/\r?\n/).find((l) => l.startsWith("ERROR")) ?? "不明なエラー"}`,
    );
  const faces = JSON.parse(line.slice(6)) as Array<{
    index: number;
    x: number;
    y: number;
    w: number;
    h: number;
    file: string;
  }>;
  const result = [];
  for (const face of faces) {
    const thumb = await fs.readFile(path.join(workDir, "faces", face.file));
    result.push({
      index: face.index,
      x: face.x,
      y: face.y,
      w: face.w,
      h: face.h,
      thumbBase64: thumb.toString("base64"),
    });
  }
  return result;
}

/** PATH for FaceFusion: ffmpeg and the CUDA libraries that come with its Python packages. */
export async function faceFusionPath(base: string | undefined): Promise<string> {
  const nvidia = path.join(FACEFUSION_DIR, "venv", "Lib", "site-packages", "nvidia");
  const dirs = await fs.readdir(nvidia).catch(() => [] as string[]);
  const bins: string[] = [];
  for (const dir of dirs) {
    const bin = path.join(nvidia, dir, "bin");
    if (
      await fs.access(bin).then(
        () => true,
        () => false,
      )
    )
      bins.push(bin);
  }
  return [FFMPEG_DIR, path.join(FACEFUSION_DIR, "venv", "Scripts"), ...bins, base ?? ""].join(";");
}

/** The FaceFusion arguments for one pass (one person). */
export function swapPassArgs(params: {
  source: string;
  target: string;
  output: string;
  tempDir: string;
  quality: SwapQuality;
  enhancer: boolean;
  /** Face of the target to replace; undefined = every face. */
  position?: number;
  referenceFrame?: number;
  trim?: { startFrame: number; endFrame: number };
}): string[] {
  const pixelBoost =
    params.quality === "fast" ? "256x256" : params.quality === "standard" ? "512x512" : "1024x1024";
  const args = [
    "facefusion.py",
    "headless-run",
    "--source-paths",
    params.source,
    "--target-path",
    params.target,
    "--output-path",
    params.output,
    "--temp-path",
    params.tempDir,
    "--processors",
    "face_swapper",
    ...(params.enhancer ? ["face_enhancer"] : []),
    "--face-swapper-pixel-boost",
    pixelBoost,
    "--temp-frame-format",
    "jpeg",
    "--output-video-quality",
    "85",
    "--execution-providers",
    "cuda",
    "--log-level",
    "info",
  ];
  if (params.position === undefined) {
    args.push("--face-selector-mode", "many");
  } else {
    args.push(
      "--face-selector-mode",
      "reference",
      "--face-selector-order",
      "left-right",
      "--reference-face-position",
      String(params.position),
      "--reference-frame-number",
      String(Math.max(0, params.referenceFrame ?? 0)),
    );
  }
  if (params.trim)
    args.push(
      "--trim-frame-start",
      String(params.trim.startFrame),
      "--trim-frame-end",
      String(params.trim.endFrame),
    );
  return args;
}
