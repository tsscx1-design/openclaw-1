import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";
import { normalizeVoiceCaption } from "./voice-caption.js";
import { loadVoiceProject, projectDir, saveProject, type VoiceProject } from "./voice-studio.js";

export type VoiceAcoustics = {
  caption: string;
  warnings: string[];
  metrics: Record<string, number>;
  voice?: VoiceProject;
};
const busy = new Set<string>();
const CANDIDATE = /^speaker-([1-9][0-9]?)\.wav$/;
const fingerprint = (project: VoiceProject) =>
  JSON.stringify([project.files, project.extractionMode]);

/** Analyze a chosen candidate or project recordings; save only against the same current source. */
export async function analyzeVoiceCaption(
  id: string,
  name?: string,
  save = false,
): Promise<VoiceAcoustics> {
  const project = await loadVoiceProject(id);
  if (busy.has(id) || busy.size >= 2) {
    throw new Error("音声の特徴を解析中です。完了してからもう一度実行してください。");
  }
  if (name && !CANDIDATE.test(name)) {
    throw new Error("解析する話者を選んでください。");
  }
  if (save && name) {
    throw new Error("話者候補の解析は保存しません。先に対象話者を選んでください。");
  }
  if (!name && project.extractionMode === "pending") {
    throw new Error("先に対象の話者を選んでください。");
  }
  const records = project.files.filter((file) => path.basename(file.name) === file.name);
  if (!name && !records.length) {
    throw new Error("解析する録音がありません。");
  }
  const selected = name
    ? [path.join(projectDir(id), "candidates", name)]
    : (records.length <= 12
        ? records
        : Array.from(
            { length: 12 },
            (_, index) => records[Math.round((index * (records.length - 1)) / 11)]!,
          )
      ).map((file) => path.join(projectDir(id), "raw", file.name));
  const root = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
  if (!root) {
    throw new Error("OpenClaw の実行場所を確認できません。");
  }
  busy.add(id);
  try {
    const stats = await Promise.all(selected.map((file) => fs.stat(file)));
    if (stats.some((stat) => stat.size > 300 * 1024 * 1024)) {
      throw new Error("解析するファイルは300MBまでです。");
    }
    const stdout = await new Promise<string>((resolve, reject) => {
      execFile(
        "D:\\media-tools\\Wan2GP\\venv\\Scripts\\python.exe",
        [path.join(root, "scripts", "media", "describe_voice.py"), JSON.stringify(selected)],
        {
          windowsHide: true,
          timeout: 240_000,
          maxBuffer: 128 * 1024,
          env: { ...process.env, PYTHONUTF8: "1", PYTHONIOENCODING: "utf-8" },
        },
        (error, output, stderr) => {
          if (error) {
            reject(
              new Error(`声の解析に失敗しました：${stderr.trim().slice(-700) || error.message}`),
            );
          } else {
            resolve(output);
          }
        },
      );
    });
    const line = stdout.split(/\r?\n/).find((value) => value.startsWith("RESULT "));
    if (!line) {
      throw new Error("声の解析結果を取得できませんでした。");
    }
    const result = JSON.parse(line.slice(7)) as VoiceAcoustics;
    if (
      typeof result.caption !== "string" ||
      !Array.isArray(result.warnings) ||
      !result.metrics ||
      Object.values(result.metrics).some(
        (value) => typeof value !== "number" || !Number.isFinite(value),
      )
    ) {
      throw new Error("声の解析結果を読み取れませんでした。");
    }
    result.caption = normalizeVoiceCaption(result.caption);
    result.warnings = result.warnings
      .filter((value): value is string => typeof value === "string")
      .slice(0, 12);
    if (!name && records.length > 12) {
      result.warnings.push(`${records.length}ファイルから均等に選んだ12ファイルを解析しました。`);
    }
    if (save) {
      const latest = await Promise.all(selected.map((file) => fs.stat(file)));
      const current = await loadVoiceProject(id);
      if (
        fingerprint(current) !== fingerprint(project) ||
        latest.some(
          (stat, index) =>
            stat.size !== stats[index]!.size || stat.mtimeMs !== stats[index]!.mtimeMs,
        )
      ) {
        throw new Error("解析中に録音が変更されました。新しい録音で再解析してください。");
      }
      // Preserve the user's written description; replace only the previous measured section.
      const manual = (current.caption ?? "")
        .replace(/\n?\[音声計測\][\s\S]*?\[\/音声計測\]\n?/gu, "")
        .trim();
      current.caption = normalizeVoiceCaption(
        [manual, `[音声計測]\n${result.caption}\n[/音声計測]`].filter(Boolean).join("\n\n"),
      );
      await saveProject(current);
      result.voice = current;
    }
    return result;
  } finally {
    busy.delete(id);
  }
}
