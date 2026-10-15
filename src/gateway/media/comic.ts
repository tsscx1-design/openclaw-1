import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  breastTags,
  characterOptionIds,
  characterOptionTags,
  type CharacterSpec,
  creatorSystemPrompt,
  imageHeightTags,
  mergeLookTags,
  parseCreatorReply,
  resolveCast,
  specSummary,
  stripBreastTags,
} from "../comic-characters.js";
import {
  castSystemPrompt,
  clip,
  COMIC_OUTLINE_SCHEMA,
  COMIC_PANELS_PER_EPISODE,
  COMIC_PANELS_PER_PART,
  type ComicCharacter,
  type ComicScript,
  comicScriptSchema,
  type ComicSeries,
  episodeOutlinePrompt,
  episodeSystemPrompt,
  firstJsonObject,
  FRAME_ASPECT,
  getComicSeries,
  parseCast,
  parseComicScript,
  saveComicSeries,
} from "../comic-series.js";
import {
  getMediaJob,
  runCharacterAvatar,
  runCharacterLora,
  runCharacterMini,
  runCharacterModel3d,
  runCharacterOutpaint,
  runCharacterSample,
  runCharacterSheet,
  runSampleBatch,
  saveJobMeta,
  working,
} from "../media-studio.js";
import { loadSampleDescriptions, resolveSampleSubject } from "../sample-factory.js";
import { acquireGpu } from "./gpu-lease.js";
import {
  askScriptWriter,
  composePanels,
  type DrawnPanel,
  drawOnLanes,
  drawPanel,
  linesSummary,
  scriptText,
  writeScript,
} from "./manga.js";
import { outDir } from "./paths.js";
import { markStage } from "./runner.js";
import { askGemma, assertAllowedRequest, isSexualRequest, productionRequest } from "./text.js";
import type { MediaJob, MediaOptions } from "./types.js";

// ---------- serial comics ----------

/** An episode keeps its script and panel images so single panels can be redrawn later. */
export function episodeDir(jobId: string): string {
  return path.join(outDir(), "comic", jobId);
}

type EpisodeFiles = {
  seriesId: string;
  script: ComicScript;
  panels: DrawnPanel[];
  aspects: Array<NonNullable<MediaOptions["aspect"]>>;
  sexual: boolean;
  episodeNo: number;
};

async function composeEpisode(
  job: MediaJob,
  dir: string,
  series: ComicSeries,
  files: EpisodeFiles,
) {
  markStage(job, "縦読みに組んで吹き出しを入れています");
  const parts = await composePanels(dir, {
    layout: "webtoon",
    title: series.title,
    episodeTitle: `第${files.episodeNo}話 ${files.script.title}`,
    panels: files.panels,
    out: path.join(dir, "part.png"),
  });
  return parts;
}

export async function runComicJob(job: MediaJob): Promise<void> {
  const task = job.options.comicTask ?? "episode";
  // Sheets and training belong to a library character, not to one series.
  if (task === "sheet") return runCharacterSheet(job);
  if (task === "lora") return runCharacterLora(job);
  if (task === "outpaint") return runCharacterOutpaint(job);
  if (task === "avatar") return runCharacterAvatar(job);
  if (task === "mini") return runCharacterMini(job);
  if (task === "model3d") return runCharacterModel3d(job);
  if (task === "sample")
    return job.options.sampleKeys?.length ? runSampleBatch(job) : runCharacterSample(job);
  const seriesId = job.options.comicSeriesId ?? "";
  const series = await getComicSeries(seriesId);
  if (!series) throw new Error("作品が見つかりません");
  // Linked characters draw with the library's current look, sheet and trained LoRA.
  series.cast = await resolveCast(series.cast, { adult: series.rating === "adult" });
  if (task === "redraw") return runPanelRedraw(job, series);

  const episodeNo = (series.episodes.at(-1)?.no ?? 0) + 1;
  const panelCount = COMIC_PANELS_PER_EPISODE;
  const production = await productionRequest(job);
  const request = production ? `今回の話への要望: ${production}` : "今回の話への要望: おまかせ";
  const outline = await writeScript(
    job,
    `第${episodeNo}話の流れを考えています`,
    episodeOutlinePrompt(series),
    request,
    // 12-16 beats did not fit in 1,500 tokens and came back cut off.
    4_000,
    (reply) => {
      const data = firstJsonObject(reply);
      const beats = Array.isArray(data.beats)
        ? data.beats.map((b) => clip(b, 200)).filter(Boolean)
        : [];
      if (beats.length < 3) throw new Error("話の流れが短すぎます");
      return `題名案: ${clip(data.title, 30)}\n${beats.map((b, i) => `${i + 1}. ${b}`).join("\n")}\n引き: ${clip(data.hook, 200)}`;
    },
    COMIC_OUTLINE_SCHEMA,
  );
  // The episode is written in parts of 24 panels; each part sees how the previous one ended.
  const partCount = Math.ceil(panelCount / COMIC_PANELS_PER_PART);
  let script: ComicScript | undefined;
  for (let index = 1; index <= partCount; index++) {
    const previous = script?.panels
      .slice(-4)
      .map((p) =>
        [p.narration, ...p.lines.map((l) => `${l.who}「${l.text}」`)].filter(Boolean).join(" "),
      )
      .join("\n");
    const part = await writeScript(
      job,
      `第${episodeNo}話の脚本を書いています（${index}/${partCount}、全${panelCount}コマ）`,
      episodeSystemPrompt(series, COMIC_PANELS_PER_PART, outline, {
        index,
        count: partCount,
        previous,
      }),
      request,
      9_000,
      (reply) => parseComicScript(reply, COMIC_PANELS_PER_PART),
      comicScriptSchema(COMIC_PANELS_PER_PART),
    );
    script = script
      ? {
          title: script.title || part.title,
          summary: part.summary || script.summary,
          panels: [...script.panels, ...part.panels],
        }
      : part;
  }
  if (!script) throw new Error("脚本を作れませんでした");
  const allText = scriptText(series.cast, script.panels, script.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = series.rating === "adult" || isSexualRequest(`${production} ${allText.join(" ")}`);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const dir = episodeDir(job.id);
  await fs.mkdir(dir, { recursive: true });
  const aspects = script.panels.map((p) => FRAME_ASPECT[p.frame]);
  const panels = new Array<DrawnPanel>(script.panels.length);
  await drawOnLanes(
    job,
    script.panels.map((panel, index) => async (gpu: number) => {
      const file = path.join(dir, `panel-${String(index + 1).padStart(2, "0")}.png`);
      panels[index] = await drawPanel(
        job,
        series.cast,
        panel,
        aspects[index] ?? "square",
        sexual,
        file,
        gpu,
      );
    }),
    "コマを描きました",
  );
  const files: EpisodeFiles = { seriesId, script, panels, aspects, sexual, episodeNo };
  await fs.writeFile(path.join(dir, "episode.json"), JSON.stringify(files, null, 2));
  const parts = await composeEpisode(job, dir, series, files);
  job.file = parts[0];
  job.files = parts;
  job.mime = "image/png";
  job.comic = { seriesId, episodeNo, title: script.title };
  job.manga = { title: `第${episodeNo}話 ${script.title}`, lines: linesSummary(script.panels) };
  // Reload: the series may have changed (a character sheet) while this episode was drawn.
  const latest = (await getComicSeries(seriesId)) ?? series;
  latest.episodes.push({
    no: episodeNo,
    title: script.title,
    summary: script.summary,
    jobId: job.id,
    createdAt: Date.now(),
  });
  latest.updatedAt = Date.now();
  await saveComicSeries(latest);
}

/** Draws one panel of a finished episode again (new seed) and re-typesets the episode. */
async function runPanelRedraw(job: MediaJob, series: ComicSeries): Promise<void> {
  const target = await getMediaJob(job.options.comicTargetJobId ?? "");
  if (!target || target.kind !== "comic" || !target.comic?.episodeNo) {
    throw new Error("描き直す話が見つかりません");
  }
  const dir = episodeDir(target.id);
  const files = JSON.parse(
    await fs.readFile(path.join(dir, "episode.json"), "utf-8"),
  ) as EpisodeFiles;
  const index = (job.options.comicPanel ?? 0) - 1;
  const panel = files.script.panels[index];
  if (!panel) throw new Error("そのコマはありません");
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const file = path.join(dir, `panel-${String(index + 1).padStart(2, "0")}.png`);
  files.panels[index] = await drawPanel(
    job,
    series.cast,
    panel,
    files.aspects[index] ?? "square",
    files.sexual,
    file,
  );
  await fs.writeFile(path.join(dir, "episode.json"), JSON.stringify(files, null, 2));
  const parts = await composeEpisode(job, dir, series, files);
  target.files = parts;
  target.file = parts[0];
  await saveJobMeta(target).catch(() => undefined);
  job.file = file;
  job.mime = "image/png";
  job.comic = {
    seriesId: series.id,
    episodeNo: files.episodeNo,
    targetJobId: target.id,
    panel: index + 1,
  };
}

/** The look (English tags) and profile for a character entered in the creator. */
export async function writeCharacterLook(
  name: string,
  spec: CharacterSpec,
): Promise<{ look: string; profile: string }> {
  // What each chosen option looks like, in words the local model understands (slang, positions, outfits).
  const explained: string[] = [];
  const written = await loadSampleDescriptions();
  for (const id of characterOptionIds(spec.lookOptions)) {
    const row = written[`opt:${id}`];
    const subject = row ? await resolveSampleSubject(`opt:${id}`) : undefined;
    if (row && subject) explained.push(`- ${subject.label}：${row.ja}（${row.en}）`);
  }
  const user =
    `名前: ${name}\n${specSummary(spec)}` +
    (explained.length > 0
      ? `\n選んだ外見オプションの説明（かならず絵に反映する）:\n${explained.join("\n")}`
      : "");
  const system = creatorSystemPrompt();
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const reply = working
        ? await askGemma(system, user, 800)
        : await askScriptWriter(system, user, 800);
      const parsed = parseCreatorReply(reply);
      // The words the AI wrote, her height and the options chosen in the app (exact tags, no repeats).
      const sizeTags = breastTags(spec.breastSize);
      const look = mergeLookTags(
        sizeTags ? stripBreastTags(parsed.look) : parsed.look,
        imageHeightTags(spec),
        sizeTags,
        characterOptionTags(spec.lookOptions),
      );
      return { look, profile: parsed.profile };
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`見た目を作れませんでした: ${lastError}`);
}

/**
 * The cast for a new series, written by the chat model from its premise (JSON, Japanese
 * profiles, English look tags).
 */
export async function writeComicCast(series: ComicSeries): Promise<ComicCharacter[]> {
  const user = `作品名: ${series.title}\nジャンル: ${series.genre}\n設定: ${series.premise}`;
  const system = castSystemPrompt(series.rating);
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const reply = working
        ? await askGemma(system, user, 1_500)
        : await askScriptWriter(system, user, 1_500);
      return parseCast(reply);
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`登場人物を作れませんでした: ${lastError}`);
}
