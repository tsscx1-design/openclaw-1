import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Model catalog for the app's model settings screen: every switchable model with a description,
// measured generation time and a sample to view or play. Chat entries come from a benchmark file
// (catalog/chat.json) because their speeds are measured on this PC; the media entries are static.

export type CatalogCategory = "chat" | "image" | "video" | "music" | "tts";
export type SampleKind = "image" | "video" | "audio" | "text";

export type CatalogEntry = {
  id: string;
  name: string;
  /** Short chips: アニメ / 実写 / 制限解除 / 日本語 / 速い ... */
  badges: string[];
  description: string;
  /** Measured on this PC (RTX 4060 8 GB), e.g. "1枚 約30秒" or "毎秒42文字". */
  speed: string;
  sample?: { kind: SampleKind; file?: string; text?: string };
  /** false: listen/view only for now (not yet wired into generation). */
  selectable: boolean;
};

export type MediaSettings = {
  image: string;
  video: string;
  music: string;
  tts: string;
};

const DEFAULT_SETTINGS: MediaSettings = {
  image: "wai",
  video: "standard",
  music: "acestep",
  tts: "sbv2",
};

function mediaDir(): string {
  return path.join(resolveOpenClawHome(), "media");
}
function samplesDir(): string {
  return path.join(mediaDir(), "samples");
}
function settingsFile(): string {
  return path.join(mediaDir(), "model-settings.json");
}
function chatCatalogFile(): string {
  return path.join(mediaDir(), "catalog", "chat.json");
}

const sample = (kind: SampleKind, file: string) => ({ kind, file });

export const MEDIA_CATALOG: Record<Exclude<CatalogCategory, "chat">, CatalogEntry[]> = {
  image: [
    {
      id: "wai",
      name: "WAI-Illustrious v17",
      badges: ["アニメ", "定番"],
      description: "アニメ絵で一番人気の定番。タグの理解が正確で、色がきれい。",
      speed: "1枚 約30秒（続けて作るとき）／最初の1枚 約45秒",
      sample: sample("image", "image-wai.png"),
      selectable: true,
    },
    {
      id: "noobai",
      name: "NoobAI-XL V-Pred 1.0",
      badges: ["アニメ", "制限解除", "成人向け◎"],
      description: "アニメの成人向けで評価が最も高い。体の描写と構図が正確。",
      speed: "1枚 約30秒〜1分",
      sample: sample("image", "image-noobai.png"),
      selectable: true,
    },
    {
      id: "zimage",
      name: "Z-Image Turbo 6B",
      badges: ["アニメ", "実写", "文章で指示"],
      description: "普通の文章の指示に強く、小物や構図が正確。アニメも写真風も描ける。",
      speed: "1枚 約1分半",
      sample: sample("image", "image-zimage.png"),
      selectable: true,
    },
    {
      id: "anima",
      name: "Anima 2B",
      badges: ["アニメ", "新しい", "タグ・文章どちらも"],
      description:
        "アニメ・イラスト専用の新しい軽量モデル。ComfyUI で動かします（fast は蒸留版）。",
      speed: "1枚 約75秒（fast 約45秒）",
      selectable: true,
    },
    {
      id: "klein",
      name: "FLUX.2 Klein 9B",
      badges: ["アニメ", "実写", "文章で指示"],
      description: "文章の指示に強い高速モデル（4ステップ）。非商用ライセンス。",
      speed: "1枚 約1分",
      selectable: true,
    },
    {
      id: "qwenimg",
      name: "Qwen Image 2.1",
      badges: ["アニメ", "実写", "日本語に強い"],
      description: "日本語の指示と細かい描写に強い。同じ顔のまま編集する用途にも使う。",
      speed: "1枚 約1分20秒",
      selectable: true,
    },
    {
      id: "qwenimgturbo",
      name: "Qwen Image 2.1 Turbo",
      badges: ["高速", "生成・編集", "透明PNG"],
      description:
        "Qwen Image 2.1の公式8ステップ版。サンプル、表情・衣装差分、ポーズ・アングル、モーションコミック素材、透明背景に対応。",
      speed: "1枚 約45秒（GPU・解像度による）",
      selectable: true,
    },
    {
      id: "lustify",
      name: "LUSTIFY V9 Zenith",
      badges: ["実写", "制限解除", "成人向け◎"],
      description: "写真のような実写の人物。成人向けの描写に強い最新版（SDXL最終版）。",
      speed: "1枚 約30秒〜1分",
      sample: sample("image", "image-lustify.png"),
      selectable: true,
    },
    {
      id: "cyberreal",
      name: "CyberRealistic XL v10",
      badges: ["実写", "制限解除"],
      description: "人気の実写モデルの最新版。肌や光の質感が自然。",
      speed: "1枚 約30秒〜1分",
      sample: sample("image", "image-cyberreal.png"),
      selectable: true,
    },
    {
      id: "hassaku",
      name: "Hassaku XL (Illustrious) v3.4",
      badges: ["アニメ", "癖がない"],
      description: "癖のない、明るいアニメ絵。追加の部品（LoRA）と相性がよく、絵が崩れにくい。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "illustrious",
      name: "Illustrious XL 1.1",
      badges: ["アニメ", "土台"],
      description: "アニメ系の本家の土台。各種LoRAを合わせやすい。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "novaanime",
      name: "Nova Anime XL IL v19",
      badges: ["アニメ", "2.5D寄り"],
      description: "アニメと2.5Dの中間。NoobAI系と Illustrious の混ぜモデル。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "plantmilk",
      name: "Plant Milk（Walnut）",
      badges: ["アニメ", "混ぜモデル"],
      description: "Illustrious と NoobAI の混ぜモデル。味つけの違う絵になる。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "juggernaut",
      name: "Juggernaut XL Ragnarok",
      badges: ["実写", "定番"],
      description: "実写系の定番。手や全身の描写がよくなった最新版。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "realvis",
      name: "RealVisXL V5.0",
      badges: ["実写", "人物写真"],
      description: "人物写真向け。自然なフィルム調の質感。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "realismil",
      name: "Realism Illustrious v5.5",
      badges: ["半実写", "アニメ寄り"],
      description: "アニメ寄りの実写。タグで指示でき、体の比率が正確。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "illust2",
      name: "Illustrious XL 2.0",
      badges: ["アニメ", "本家の土台"],
      description: "1.1の後継。構図とタグの理解がよい。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "animagine",
      name: "Animagine XL 4.0",
      badges: ["アニメ", "キャラ再現"],
      description: "キャラ名タグの再現に強い定番。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "rouwei",
      name: "RouWei 0.8",
      badges: ["アニメ", "成人向け可"],
      description: "色と構図が良く、成人向けも出しやすい。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "chenkin",
      name: "ChenkinNoob XL",
      badges: ["アニメ", "NoobAI系"],
      description: "AIらしさが少ない、NoobAI系の新しい絵。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "janku",
      name: "JANKU v7.77",
      badges: ["アニメ", "成人向け"],
      description: "高精細で背景・衣装が細かい。成人向けも可。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "ntrmix",
      name: "NTR MIX XIII",
      badges: ["アニメ", "成人向け"],
      description: "濃いめの塗りで、成人向けに強い。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "desire",
      name: "Anime Desire v3",
      badges: ["アニメ", "成人向け"],
      description: "成人向けのアニメ・同人調に特化。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "cyberanime",
      name: "CyberIllustrious Anime",
      badges: ["アニメ", "質感"],
      description: "光と肌の質感が自然なアニメ絵。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "cybersemi",
      name: "CyberIllustrious 半実写",
      badges: ["半実写", "質感"],
      description: "アニメ寄りの顔で、質感は実写寄り。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "pencil",
      name: "pencil XL",
      badges: ["アニメ", "やわらか"],
      description: "日本のイラスト寄りの、やわらかい絵。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
    {
      id: "cyberpony",
      name: "CyberRealistic Pony",
      badges: ["実写", "成人向け"],
      description: "Pony 系の実写。成人向けに強く、ポーズが多彩。",
      speed: "1枚 約30秒（続けて作るとき）",
      selectable: true,
    },
  ],
  video: [
    {
      id: "standard",
      name: "標準（Wan 2.2 14B 制限解除）",
      badges: ["アニメ", "実写", "制限解除"],
      description: "絵を2秒動かす。実写にしたいときは画像のモデルを実写系にする。",
      speed: "2秒の動画 約5分",
      sample: sample("video", "video-standard.webm"),
      selectable: true,
    },
    {
      id: "long",
      name: "長い動画（Wan 2.2 SVI 2 Pro）",
      badges: ["アニメ", "実写", "長い"],
      description: "5〜30秒。長くしても顔や服が崩れにくい。",
      speed: "5秒 約10〜15分（見込み）",
      sample: sample("video", "video-long.mp4"),
      selectable: true,
    },
    {
      id: "audio",
      name: "音付き（LTX-2）",
      badges: ["音付き"],
      description: "映像と音を同時に作る。4〜10秒。",
      speed: "6秒 約10分（見込み）",
      sample: sample("video", "video-audio.mp4"),
      selectable: true,
    },
    {
      id: "fast",
      name: "高速（FastWan 5B）",
      badges: ["速い"],
      description: "3〜5秒を手早く。動きは控えめ。",
      speed: "3秒 約3〜7分",
      sample: sample("video", "video-fast.mp4"),
      selectable: true,
    },
  ],
  music: [
    {
      id: "acestep",
      name: "ACE-Step 1.5",
      badges: ["日本語の歌", "速い"],
      description: "歌詞を空欄にするとAIが日本語で作詞。Suno v4.5〜v5くらいの品質。",
      speed: "60秒の曲 約1分",
      sample: sample("audio", "music-acestep.mp3"),
      selectable: true,
    },
    {
      id: "minimax",
      name: "MiniMax Music 3",
      badges: ["高品質", "遅い"],
      description: "2026年8月の最新・最高クラス。このPCでは大きすぎて時間がかかる。",
      speed: "60秒の曲 約55分",
      sample: sample("audio", "music-minimax.wav"),
      selectable: true,
    },
  ],
  tts: [
    {
      id: "sbv2",
      name: "Style-Bert-VITS2（いまの声）",
      badges: ["日本語", "声作り済み"],
      description: "パートナーの会話で今使っている声。アクセントが正確で軽い。",
      speed: "1文 1秒前後",
      sample: sample("audio", "tts-sbv2.wav"),
      selectable: true,
    },
    {
      id: "irodori",
      name: "Irodori-TTS v4.1",
      badges: ["日本語", "感情表現", "声まね"],
      description: "絵文字でささやき・笑い・泣きなどを表現できる日本語専用の最新TTS。",
      speed: "1文 約4秒（読み込み込みで約20秒）",
      sample: sample("audio", "tts-irodori.wav"),
      selectable: false,
    },
    {
      id: "qwen3tts",
      name: "Qwen3-TTS（3秒の声まね）",
      badges: ["声まね", "多言語"],
      description: "3秒の声から同じ声で話す。日本語を含む10か国語。",
      speed: "計測中",
      sample: sample("audio", "tts-qwen3tts.wav"),
      selectable: false,
    },
  ],
};

export async function readMediaSettings(): Promise<MediaSettings> {
  try {
    return {
      ...DEFAULT_SETTINGS,
      ...(JSON.parse(await fs.readFile(settingsFile(), "utf-8")) as Partial<MediaSettings>),
    };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export async function writeMediaSetting(
  category: keyof MediaSettings,
  id: string,
): Promise<MediaSettings> {
  const entry = MEDIA_CATALOG[category].find((item) => item.id === id);
  if (!entry) throw new Error(`Unknown ${category} model: ${id}`);
  if (!entry.selectable) throw new Error("このモデルはまだ試聴のみです");
  const settings = { ...(await readMediaSettings()), [category]: id };
  await fs.mkdir(mediaDir(), { recursive: true });
  await fs.writeFile(settingsFile(), JSON.stringify(settings, null, 2));
  return settings;
}

async function readChatCatalog(): Promise<CatalogEntry[]> {
  try {
    return JSON.parse(await fs.readFile(chatCatalogFile(), "utf-8")) as CatalogEntry[];
  } catch {
    return [];
  }
}

async function exists(file: string): Promise<boolean> {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

/** Catalog with `hasSample` resolved, so the app only offers samples that exist. */
export async function listCatalog(): Promise<
  Record<CatalogCategory, Array<CatalogEntry & { hasSample: boolean }>>
> {
  const withSamples = async (entries: CatalogEntry[]) =>
    Promise.all(
      entries.map(async (entry) => ({
        ...entry,
        hasSample: Boolean(
          entry.sample?.text ||
          (entry.sample?.file && (await exists(path.join(samplesDir(), entry.sample.file)))),
        ),
      })),
    );
  return {
    chat: await withSamples(await readChatCatalog()),
    image: await withSamples(MEDIA_CATALOG.image),
    video: await withSamples(MEDIA_CATALOG.video),
    music: await withSamples(MEDIA_CATALOG.music),
    tts: await withSamples(MEDIA_CATALOG.tts),
  };
}

const SAMPLE_MIME: Record<string, string> = {
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webm": "video/webm",
  ".mp4": "video/mp4",
  ".mp3": "audio/mpeg",
  ".wav": "audio/wav",
};

export async function readCatalogSample(
  category: CatalogCategory,
  id: string,
): Promise<{ kind: SampleKind; mime?: string; base64?: string; text?: string } | undefined> {
  const entries = category === "chat" ? await readChatCatalog() : MEDIA_CATALOG[category];
  const entry = entries?.find((item) => item.id === id);
  if (!entry?.sample) return undefined;
  if (entry.sample.text) return { kind: entry.sample.kind, text: entry.sample.text };
  const file = entry.sample.file;
  // Sample names are fixed in the catalog; never let a request choose a path.
  if (!file || path.basename(file) !== file) return undefined;
  const full = path.join(samplesDir(), file);
  if (!(await exists(full))) return undefined;
  return {
    kind: entry.sample.kind,
    mime: SAMPLE_MIME[path.extname(file).toLowerCase()] ?? "application/octet-stream",
    base64: (await fs.readFile(full)).toString("base64"),
  };
}
