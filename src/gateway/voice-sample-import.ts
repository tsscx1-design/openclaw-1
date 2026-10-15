import * as fs from "node:fs/promises";
import * as path from "node:path";
import { readResponseWithLimit } from "../infra/http-body.js";
import { fetchWithSsrFGuard } from "../infra/net/fetch-guard.js";
import { voiceConsentRequired } from "./blocked-terms-config.js";
import { analyzeVoiceCaption, type VoiceAcoustics } from "./voice-acoustics.js";
import { searchVoiceSamples } from "./voice-sample-search.js";
import { createVoiceProject, projectDir, saveProject, type VoiceProject } from "./voice-studio.js";

const MAX_BYTES = 20 * 1024 * 1024;
let activeImports = 0;

/** Import only the exact published audio association selected by the operator. */
export async function importVoiceSample(
  source: string,
  id: number,
  requestedUrl: string,
  consent: unknown,
): Promise<{ voice: VoiceProject; analysis?: VoiceAcoustics; analysisError?: string }> {
  if (consent !== true && voiceConsentRequired()) {
    throw new Error("音声の利用条件と話者の許可を確認してください。");
  }
  if (activeImports >= 2) {
    throw new Error("音声の取り込み中です。完了してからもう一度実行してください。");
  }
  activeImports++;
  try {
    const found = await searchVoiceSamples(source, id, "");
    const sample = found.links.find(
      (link) =>
        link.kind === "public-audio" && link.verified && link.directAudioUrl === requestedUrl,
    );
    if (!sample) {
      throw new Error("この音声資料を確認できません。対象を再検索して選択してください。");
    }
    const url = new URL(requestedUrl);
    const extension = /\.(mp3|wav|m4a|aac|ogg|flac|opus|webm)$/i
      .exec(url.pathname)?.[1]
      ?.toLowerCase();
    if (
      url.protocol !== "https:" ||
      url.hostname !== "upload.wikimedia.org" ||
      url.username ||
      url.password ||
      url.port ||
      !extension ||
      !url.pathname.startsWith("/wikipedia/commons/")
    ) {
      throw new Error("取り込める公開音声形式ではありません。");
    }
    const { response, release } = await fetchWithSsrFGuard({
      url: url.href,
      requireHttps: true,
      maxRedirects: 0,
      timeoutMs: 60_000,
      policy: { hostnameAllowlist: ["upload.wikimedia.org"] },
      init: {
        headers: {
          "User-Agent": "OpenClaw-voice-sample-import/1.0 (https://github.com/openclaw/openclaw)",
        },
      },
    });
    let data: Buffer;
    try {
      if (!response.ok) {
        throw new Error(`公開音声を取得できませんでした (HTTP ${response.status})。`);
      }
      data = await readResponseWithLimit(response, MAX_BYTES, {
        timeoutMs: 60_000,
        chunkTimeoutMs: 15_000,
      });
      if (!data.length) {
        throw new Error("公開音声ファイルが空です。");
      }
    } finally {
      await release();
    }
    const voice = await createVoiceProject(found.name, consent);
    const fileName = `sample.${extension}`;
    try {
      await fs.writeFile(path.join(projectDir(voice.id), "raw", fileName), data);
      voice.files = [{ name: fileName, bytes: data.length }];
      voice.source = "recording";
      await saveProject(voice);
      // A named import artifact carries attribution without injecting it into the voice prompt.
      await fs.writeFile(
        path.join(projectDir(voice.id), "source-note.txt"),
        [
          sample.title,
          `資料元: ${sample.sourceUrl}`,
          `音声: ${url.href}`,
          `ライセンス: ${sample.license}`,
          `ライセンスURL: ${sample.licenseUrl}`,
          `帰属: ${sample.attribution ?? ""}`,
          sample.note,
        ].join("\n"),
        "utf8",
      );
    } catch (error) {
      voice.status = "failed";
      voice.error = error instanceof Error ? error.message : "音声の保存に失敗しました。";
      await saveProject(voice);
      return { voice, analysisError: `音声の保存に失敗しました: ${voice.error}` };
    }
    try {
      const analysis = await analyzeVoiceCaption(voice.id, undefined, true);
      return { voice: analysis.voice ?? voice, analysis };
    } catch (error) {
      return {
        voice,
        analysisError: error instanceof Error ? error.message : "声の特徴を解析できませんでした。",
      };
    }
  } finally {
    activeImports--;
  }
}
