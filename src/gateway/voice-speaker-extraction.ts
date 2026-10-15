import { spawn } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";
import { mediaProcessEnv } from "./media-gpu.js";
import { loadVoiceProject, projectDir, saveProject, type VoiceProject } from "./voice-studio.js";

const PYTHON = "D:\\media-tools\\Wan2GP\\venv\\Scripts\\python.exe";
const WAN2GP = "D:\\media-tools\\Wan2GP";
function extractorPath(): string {
  const root = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
  if (!root) {
    throw new Error("OpenClaw の実行場所を確認できません。");
  }
  return path.join(root, "scripts", "media", "extract-voice-speakers.py");
}
const CANDIDATE = /^speaker-([1-9][0-9]?)\.wav$/;
const MAX_SOURCE_BYTES = 300 * 1024 * 1024;
const analyzing = new Set<string>();

export type SpeakerCandidate = { name: string; seconds: number; bytes: number };

function candidateDir(id: string): string {
  return path.join(projectDir(id), "candidates");
}

export async function speakerCandidates(id: string): Promise<SpeakerCandidate[]> {
  const dir = candidateDir(id);
  const names = await fs.readdir(dir).catch(() => []);
  const found: SpeakerCandidate[] = [];
  for (const name of names.filter((value) => CANDIDATE.test(value)).toSorted()) {
    const size = (await fs.stat(path.join(dir, name))).size;
    // The extractor writes PCM16 mono at 16 kHz.
    found.push({ name, seconds: Math.max(0, (size - 44) / 32_000), bytes: size });
  }
  return found;
}

export async function analyzeVoiceSpeakers(id: string): Promise<SpeakerCandidate[]> {
  const project = await loadVoiceProject(id);
  if (project.extractionMode !== "pending" || project.status !== "uploading") {
    throw new Error("この声は話者の解析を受け付けません");
  }
  if (project.files.length !== 1) {
    throw new Error("MP4/MP3を1ファイル選んでください");
  }
  const source = path.join(projectDir(id), "raw", project.files[0]!.name);
  if ((await fs.stat(source)).size > MAX_SOURCE_BYTES) {
    throw new Error("ファイルは300MBまでです");
  }
  if (analyzing.has(id)) {
    throw new Error("この音声は解析中です");
  }
  analyzing.add(id);
  try {
    await fs.rm(candidateDir(id), { recursive: true, force: true });
    await fs.mkdir(candidateDir(id), { recursive: true });
    await new Promise<void>((resolve, reject) => {
      const child = spawn(PYTHON, [extractorPath(), source, candidateDir(id)], {
        cwd: WAN2GP,
        windowsHide: true,
        env: mediaProcessEnv({ PYTHONUTF8: "1", PYTHONIOENCODING: "utf-8" }),
        stdio: ["ignore", "pipe", "pipe"],
      });
      let tail = "";
      for (const stream of [child.stdout, child.stderr]) {
        stream.on("data", (chunk: Buffer) => {
          tail = (tail + chunk.toString("utf8")).slice(-1600);
        });
      }
      child.on("error", reject);
      child.on("exit", (code) =>
        code === 0 ? resolve() : reject(new Error(`話者の解析に失敗しました: ${tail.slice(-500)}`)),
      );
    });
    const candidates = await speakerCandidates(id);
    if (!candidates.length) {
      throw new Error("話者の声を取り出せませんでした。会話が明瞭な素材を選んでください");
    }
    return candidates;
  } finally {
    analyzing.delete(id);
  }
}

export async function speakerPreview(id: string, name: string): Promise<Buffer> {
  const match = CANDIDATE.exec(name);
  if (!match) {
    throw new Error("話者を選んでください");
  }
  await loadVoiceProject(id);
  return await fs.readFile(path.join(candidateDir(id), `speaker-${match[1]}-preview.wav`));
}

export async function selectVoiceSpeaker(id: string, name: string): Promise<VoiceProject> {
  if (!CANDIDATE.test(name)) {
    throw new Error("話者を選んでください");
  }
  const project = await loadVoiceProject(id);
  if (
    (project.extractionMode !== "pending" && project.extractionMode !== "selected") ||
    project.status !== "uploading"
  ) {
    throw new Error("この声では話者を選べません");
  }
  const selected = path.join(candidateDir(id), name);
  await fs.access(selected);
  const raw = path.join(projectDir(id), "raw");
  const sourceDir = path.join(projectDir(id), "source");
  await fs.mkdir(sourceDir, { recursive: true });
  await fs.copyFile(selected, path.join(raw, name));
  if (project.extractionMode === "pending") {
    for (const file of project.files) {
      await fs.rename(path.join(raw, file.name), path.join(sourceDir, file.name));
    }
  } else {
    for (const file of project.files) {
      if (file.name !== name) {
        await fs.rm(path.join(raw, file.name), { force: true });
      }
    }
  }
  project.files = [{ name, bytes: (await fs.stat(selected)).size }];
  project.extractionMode = "selected";
  // Preserve the existing project ID and its normal training path.
  await saveProject(project);
  return project;
}

export async function readVoiceArtifact(
  id: string,
  kind: "candidate" | "model",
  name: string,
  offset: number,
): Promise<{ base64: string; total: number; next: number }> {
  if (!Number.isSafeInteger(offset) || offset < 0) {
    throw new Error("読み込み位置が無効です");
  }
  const project = await loadVoiceProject(id);
  const file =
    kind === "candidate"
      ? CANDIDATE.test(name)
        ? path.join(candidateDir(id), name)
        : undefined
      : project.status === "ready"
        ? path.join(projectDir(id), "out", `${id}.aivmx`)
        : undefined;
  if (!file) {
    throw new Error("ダウンロードする声が見つかりません");
  }
  const handle = await fs.open(file, "r");
  try {
    const total = (await handle.stat()).size;
    const bytes = Buffer.alloc(Math.min(1024 * 1024, Math.max(0, total - offset)));
    const { bytesRead } = await handle.read(bytes, 0, bytes.length, offset);
    return {
      base64: bytes.subarray(0, bytesRead).toString("base64"),
      total,
      next: offset + bytesRead,
    };
  } finally {
    await handle.close();
  }
}
