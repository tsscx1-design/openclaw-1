import * as fs from "node:fs/promises";
import * as path from "node:path";
import { gptSovitsInstalled, gptSovitsRunning } from "./gpt-sovits-tts.js";
import { irodoriInstalled } from "./irodori-tts.js";
import { resolveOpenClawHome } from "./partner-store.js";

// What the audio studio's "声のエンジン" tab shows: every voice engine OpenClaw can use, whether it is installed, whether it
// is running now, and what is missing. Nothing here starts, stops or changes an engine: it only looks at files and ports.

export type VoiceEngineStatus = {
  id: string;
  name: string;
  /** "http" = a local server with fixed speakers, "clone" = imitates a studio recording, "cloud" = a hosted service. */
  kind: "http" | "clone" | "cloud";
  installed: boolean;
  /** Whether it answers right now (null = it is started per request and has no server to ask). */
  running: boolean | null;
  /** What is installed, or what is missing (one line, Japanese). */
  detail: string;
  license: string;
};

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

async function answers(port: number, pathName = "/version"): Promise<boolean> {
  try {
    await fetch(`http://127.0.0.1:${port}${pathName}`, { signal: AbortSignal.timeout(1200) });
    return true;
  } catch {
    return false;
  }
}

const ttsDir = (...parts: string[]): string => path.join(resolveOpenClawHome(), "tts", ...parts);
const MODELS = "D:\\llm-models\\tts";

export async function voiceEngineStatuses(): Promise<VoiceEngineStatus[]> {
  const [voicevoxIn, aivisIn, qwenIn, irodoriIn, gptIn, gptUp, vox, aivis] = await Promise.all([
    exists(ttsDir("voicevox")),
    exists(ttsDir("aivis")),
    exists(path.join(MODELS, "Qwen3-TTS-12Hz-0.6B-Base.Q8_0.gguf")),
    irodoriInstalled(),
    gptSovitsInstalled(),
    gptSovitsRunning(),
    answers(50021),
    answers(10101),
  ]);
  const [outeModel, outeEnv, fishWeights, fishExe, fishKey] = await Promise.all([
    exists(path.join(MODELS, "outetts", "OuteTTS-1.0-0.6B-Q8_0.gguf")),
    exists("D:\\media-tools\\outetts\\venv\\Scripts\\python.exe"),
    exists(path.join(MODELS, "fish-s2", "weights", "s2-pro-q8_0-transformer-only.gguf")),
    exists(path.join(MODELS, "fish-s2", "src", "s2.cpp", "build-cpu", "Release", "s2.exe")),
    Promise.resolve(Boolean(process.env.FISH_API_KEY || process.env.FISH_AUDIO_API_KEY)),
  ]);
  const vox2 =
    (await exists(path.join(MODELS, "voxcpm2", "voxcpm2-q8_0.gguf"))) &&
    (await exists("D:\\media-tools\\crispasr\\build\\bin\\crispasr.exe"));
  const list: VoiceEngineStatus[] = [
    {
      id: "voicevox",
      name: "VOICEVOX",
      kind: "http",
      installed: voicevoxIn,
      running: vox,
      detail: voicevoxIn ? "CPU 版 0.25.2（決まった話者から選ぶ）" : "未インストール",
      license: "キャラクターごとの規約",
    },
    {
      id: "aivis",
      name: "AivisSpeech",
      kind: "http",
      installed: aivisIn,
      running: aivis,
      detail: aivisIn ? "自作の声（Style-Bert-VITS2 で学習）も、ここで読む" : "未インストール",
      license: "モデルごとの規約",
    },
    {
      id: "clone",
      name: "声まね（Qwen3-TTS）",
      kind: "clone",
      installed: qwenIn,
      running: null,
      detail: qwenIn ? "録音（約10秒）をまねる。0.6B と 1.7B" : "モデルがありません",
      license: "Apache-2.0",
    },
    {
      id: "irodori",
      name: "Irodori-TTS",
      kind: "clone",
      installed: irodoriIn,
      running: null,
      detail: irodoriIn ? "録音をまねる。絵文字で感情を指定できる" : "未インストール",
      license: "要確認",
    },
    {
      id: "gptsovits",
      name: "GPT-SoVITS（v5 パッケージ）",
      kind: "clone",
      installed: gptIn,
      running: gptIn ? gptUp : null,
      detail: gptIn
        ? "録音をまねる。必要なときだけ起動し、10分使わなければ止まる（標準は CPU）"
        : "未インストール",
      license: "MIT（モデルは各自の規約）",
    },
    {
      id: "outetts",
      name: "OuteTTS 1.0（0.6B）",
      kind: "clone",
      installed: outeModel && outeEnv,
      running: null,
      detail: outeModel
        ? outeEnv
          ? "録音をまねる（手本の文字起こしも使う）"
          : "モデルはあるが、実行環境（Python）がない"
        : "モデルがありません",
      license: "Apache-2.0",
    },
    {
      id: "fishlocal",
      name: "Fish S2 Pro（ローカル）",
      kind: "clone",
      installed: fishWeights && fishExe,
      running: null,
      detail: fishWeights
        ? fishExe
          ? "録音をまねる（手本の文字起こしも使う）。研究・非商用のみ"
          : "重みはあるが、s2.cpp のビルドがない"
        : "重みがありません",
      license: "Fish Audio Research License（研究・非商用）",
    },
    {
      id: "voxcpm2",
      name: "VoxCPM2",
      kind: "clone",
      installed: vox2,
      running: null,
      detail: vox2
        ? "録音をまねる（CPU・一文で約30秒）。同意済みの声だけ使える。出力の先頭に断り書きの音声が付く"
        : "モデルまたは CrispASR がありません",
      license: "Apache-2.0",
    },
    {
      id: "fish-hosted",
      name: "Fish Audio（サーバー）",
      kind: "cloud",
      installed: true,
      running: null,
      detail: fishKey ? "API キー設定済み" : "拡張は入っている。API キー（FISH_API_KEY）が未設定",
      license: "Fish Audio の利用規約（有料プランで API）",
    },
  ];
  return list;
}
