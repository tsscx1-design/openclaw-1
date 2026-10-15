import * as fs from "node:fs/promises";
import * as path from "node:path";
import { firstJsonObject } from "../comic-series.js";
import { stopIrodoriWorker, synthesizeIrodori } from "../irodori-tts.js";
import { generationCudaDevices } from "../media-gpu.js";
import type { MediaVoiceTimelineCue } from "../media-production-controls.js";
import type { VoiceDrama } from "../motion-extras.js";
import { referenceClip } from "../partner-voice-clone.js";
import { acquireGpu } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { writeScript } from "./manga.js";
import { exists, outDir, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import { assertAllowedRequest, productionRequest } from "./text.js";
import type { MediaJob } from "./types.js";

const TIMELINE_SFX: Record<"thrust" | "spank" | "wet", string> = {
  thrust: "D:\\media-tools\\motion-comic\\sfx\\synth\\thrust_slow.wav",
  spank: "D:\\media-tools\\motion-comic\\sfx\\synth\\spank.wav",
  wet: "D:\\media-tools\\motion-comic\\sfx\\synth\\wet_slow.wav",
};

export const VOICE_FFMPEG = "D:\\media-tools\\Wan2GP\\ffmpeg_bins\\ffmpeg.exe";

const VOICE_MAX_LINES = 40;

/** A script for Irodori: printable text with its emojis and line breaks kept. */
export function sanitizeVoiceScript(raw: string): string {
  return raw
    .replace(/\r\n?/g, "\n")
    .replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, "")
    .split("\n")
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .slice(0, VOICE_MAX_LINES)
    .join("\n")
    .slice(0, 4_000);
}

/**
 * A voice line or a short script with Irodori-TTS: each line (Irodori speaks up to ~30 s at a
 * time) in the chosen voice, joined with short pauses. The large model takes the GPU like any
 * media job; the small one is quick but plainer.
 */
const DRAMA_SYSTEM = `あなたは成人向けの音声ドラマ（ASMRのような、耳元で聞く物語）の脚本家。お題から、1人の女性が聞き手（あなた）に語りかけるセリフを、順番に書く。
出力はJSONオブジェクト1つだけ（説明や\`\`\`は書かない）: {"lines":[{"text":"セリフ","pause":1.2}]}
- text は1つ60字以内の日本語。行ごとの演技を角括弧で付ける: [囁き] [吐息:弱] [喘ぎ:強] [荒い息] [照れ] [甘え] [懇願] [息をのむ] [水音] [間] [ゆっくり] [悲鳴:強] など。強さは 弱・中・強。絵文字は書かない（システムが変換する）。
- pause はその行のあとの無音の秒数（0.3〜6）。ゆっくりな場面は長く、高まる場面は短く。
- 全体は、静かな導入 → 少しずつ距離が近づく → 高まり → 絶頂 → 余韻 の流れで、与えられた範囲の分だけ書く。同じセリフを繰り返さない。
- 聞き手を「あなた」と呼び、耳元で語るような口調にする。名前・口調はお題に従う。`;

function parseDramaLines(reply: string): Array<{ text: string; pause: number }> {
  const data = firstJsonObject(reply);
  const rows = Array.isArray(data.lines) ? (data.lines as Array<Record<string, unknown>>) : [];
  const out = rows
    .map((row) => ({
      text: String(row.text ?? "")
        .replace(/[\r\n]+/g, " ")
        .trim()
        .slice(0, 120),
      pause: Math.min(6, Math.max(0.3, Number(row.pause) || 1)),
    }))
    .filter((row) => row.text.length > 0);
  if (out.length === 0) throw new Error("セリフがありません");
  return out;
}

/** A long script in parts of about 24 lines, each part told what came before so the story goes on. */
async function writeDramaScript(
  job: MediaJob,
  drama: VoiceDrama,
): Promise<Array<{ text: string; pause: number }>> {
  const request = await productionRequest(job);
  const target = Math.min(210, Math.max(8, Math.round(drama.minutes * 7)));
  const out: Array<{ text: string; pause: number }> = [];
  while (out.length < target) {
    if (job.cancelRequested) throw new Error("中止しました");
    const want = Math.min(24, target - out.length);
    const from = Math.round((out.length / target) * 100);
    const to = Math.round(((out.length + want) / target) * 100);
    const recap = out
      .slice(-4)
      .map((l) => l.text)
      .join(" / ");
    const user =
      `お題: ${request}\n全体の ${from}% 〜 ${to}% の部分を、${want}行で書く。` +
      (out.length === 0
        ? "ここが物語の始まり。"
        : `直前までの最後のセリフ: ${recap}\nその続きから、話を進める。`) +
      (to >= 100 ? "ここで物語を終える（余韻と、おやすみの言葉まで）。" : "");
    const part = await writeScript(
      job,
      `台本を書いています（${out.length}/${target}行）`,
      DRAMA_SYSTEM,
      user,
      3_000,
      parseDramaLines,
    );
    assertAllowedRequest(request, ...part.map((l) => l.text));
    out.push(...part.slice(0, want));
  }
  return out;
}

export async function runVoiceJob(job: MediaJob): Promise<void> {
  const drama = job.options.voiceDrama;
  const written = drama ? await writeDramaScript(job, drama) : undefined;
  const timeline = job.options.voiceTimeline;
  const lineCues = timeline?.filter(
    (cue): cue is MediaVoiceTimelineCue & { kind: "line"; text: string } =>
      cue.kind === "line" && Boolean(cue.text),
  );
  const lines = lineCues?.length
    ? lineCues.map((cue) => cue.text)
    : written
      ? written.map((l) => l.text)
      : job.request.split("\n").filter((line) => line.trim());
  const pauses = written ? written.map((l) => l.pause) : [];
  if (lines.length === 0) throw new Error("読み上げる文がありません");
  const model = job.options.irodoriModel ?? (drama ? "fast" : "large");
  const referenceWav = job.options.voiceId ? await referenceClip(job.options.voiceId) : undefined;
  const caption = referenceWav ? undefined : job.options.voiceCaption;
  if (!referenceWav && !caption)
    throw new Error("声（声作りスタジオの声か、声の説明）を選んでください");
  if (model === "large") {
    markStage(job, "GPU を空けています");
    await acquireGpu(job);
    stopImageWorker();
  }
  const dir = path.join(outDir(), `${job.id}-voice`);
  await fs.mkdir(dir, { recursive: true });
  try {
    const parts: string[] = [];
    let seed: number | undefined;
    // With two GPUs (the large model, several lines) two workers take lines side by side. The first line is
    // made alone: a described voice (no recording) keeps its seed from it, so every line is the same person.
    // If the second GPU cannot hold the model, its line goes back to the first worker and the second one stops.
    const voiceDevices = generationCudaDevices(job.options.gpuMode === "fast");
    const voiceDual = model === "large" && lines.length > 1 && voiceDevices.length >= 2;
    const makeLine = async (index: number, lane: number) => {
      markStage(
        job,
        `声を作っています（${index + 1}/${lines.length}行${index === 0 ? "・最初はモデルの読み込みがあります" : ""}）`,
      );
      const result = await synthesizeIrodori({
        text: lines[index]!,
        model,
        caption,
        referenceWav,
        // The same seed keeps a described voice the same person from line to line.
        ...(seed !== undefined && caption ? { seed } : {}),
        ...(model === "large" ? { device: "cuda" as const } : {}),
        ...(voiceDual ? { lane, cudaDevice: voiceDevices[lane] } : {}),
      });
      seed ??= result.seed;
      const file = path.join(dir, `line-${String(index + 1).padStart(2, "0")}.wav`);
      await fs.writeFile(file, result.audio);
      parts[index] = file;
    };
    if (!voiceDual) {
      for (const index of lines.keys()) {
        if (job.cancelRequested) throw new Error("中止しました");
        await makeLine(index, 0);
      }
    } else {
      await makeLine(0, 0);
      let nextLine = 1;
      const runLane = async (lane: number): Promise<void> => {
        for (;;) {
          if (job.cancelRequested) throw new Error("中止しました");
          const index = nextLine++;
          if (index >= lines.length) return;
          try {
            await makeLine(index, lane);
          } catch (error) {
            if (lane === 0) throw error;
            await makeLine(index, 0);
            return;
          }
        }
      };
      await Promise.all([runLane(0), runLane(1)]);
    }
    markStage(job, "つないでいます");
    const output = await resultPath(job, ".wav");
    if (timeline?.length) {
      const existingSfx = (
        await Promise.all(
          timeline
            .filter(
              (
                cue,
              ): cue is MediaVoiceTimelineCue & {
                kind: "sfx";
                sfx: "thrust" | "spank" | "wet";
              } => cue.kind === "sfx" && Boolean(cue.sfx),
            )
            .map(async (cue) => ({
              cue,
              file: TIMELINE_SFX[cue.sfx],
              exists: await exists(TIMELINE_SFX[cue.sfx]),
            })),
        )
      ).filter((entry) => entry.exists);
      const inputs = [...parts, ...existingSfx.map((entry) => entry.file)].flatMap((file) => [
        "-i",
        file,
      ]);
      const filters: string[] = [];
      lineCues?.forEach((cue, index) => {
        const delay = Math.round(cue.atSec * 1000);
        filters.push(
          `[${index}:a]aresample=48000,aformat=channel_layouts=mono,volume=${cue.volume},adelay=${delay}|${delay}[line${index}]`,
        );
      });
      existingSfx.forEach(({ cue }, index) => {
        const input = parts.length + index;
        const delay = Math.round(cue.atSec * 1000);
        filters.push(
          `[${input}:a]aresample=48000,aformat=channel_layouts=mono,volume=${cue.volume},adelay=${delay}|${delay}[sfx${index}]`,
        );
      });
      const labels = [
        ...(lineCues ?? []).map((_, index) => `[line${index}]`),
        ...existingSfx.map((_, index) => `[sfx${index}]`),
      ];
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          ...inputs,
          "-filter_complex",
          `${filters.join(";")};${labels.join("")}amix=inputs=${labels.length}:duration=longest:normalize=0[out]`,
          "-map",
          "[out]",
          output,
        ],
        { timeoutMs: 30 * 60_000 },
      );
    } else if (parts.length === 1) {
      await fs.copyFile(parts[0] as string, output);
    } else {
      // Lines joined with 0.45 s of silence between them.
      const inputs = parts.flatMap((p) => ["-i", p]);
      const chain = parts
        .map(
          (_, i) =>
            `[${i}:a]aresample=48000,aformat=channel_layouts=mono,apad=pad_dur=${pauses[i] ?? 0.45}[a${i}]`,
        )
        .join(";");
      const concat = parts.map((_, i) => `[a${i}]`).join("");
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          ...inputs,
          "-filter_complex",
          `${chain};${concat}concat=n=${parts.length}:v=0:a=1[out]`,
          "-map",
          "[out]",
          output,
        ],
        { timeoutMs: 5 * 60_000 },
      );
    }
    if (drama && drama.ambience !== "none") {
      // The background sound runs under the whole story, quietly.
      markStage(job, "背景の音を重ねています");
      const mixed = path.join(dir, "mixed.wav");
      await runTool(
        VOICE_FFMPEG,
        [
          "-y",
          "-loglevel",
          "error",
          "-i",
          output,
          "-stream_loop",
          "-1",
          "-i",
          path.join("D:\\media-tools\\motion-comic\\sfx\\ambience", `${drama.ambience}.wav`),
          "-filter_complex",
          "[0:a]aresample=48000,aformat=channel_layouts=mono[v];[1:a]aresample=48000,aformat=channel_layouts=mono,volume=0.2[amb];[v][amb]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[out]",
          "-map",
          "[out]",
          mixed,
        ],
        { timeoutMs: 30 * 60_000 },
      );
      await fs.copyFile(mixed, output);
    }
    job.file = output;
    job.mime = "audio/wav";
  } finally {
    // The large model holds ~7 GB of VRAM; give it back before the chat model returns.
    if (model === "large") stopIrodoriWorker();
    await fs.rm(dir, { recursive: true, force: true }).catch(() => undefined);
  }
}
