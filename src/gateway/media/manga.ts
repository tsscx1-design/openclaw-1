import * as fs from "node:fs/promises";
import * as path from "node:path";
import { characterLook, getCharacter, resolveCast } from "../comic-characters.js";
import {
  type ComicCharacter,
  type ComicPanel,
  renderPanel,
  speakerPositions,
  type StagePosition,
} from "../comic-series.js";
import { enterGroupChatMode } from "../group-chat-model.js";
import { loadBook, previouslyText, saveBook } from "../manga-book-store.js";
import { type PageLayout, planPage, slotAspect } from "../manga-layout.js";
import {
  bookOutlinePrompt,
  bookPagePrompt,
  type BookPanel,
  type BubbleKind,
  MANGA_DEFAULT_PAGES,
  MANGA_EXTRA_NEGATIVE,
  MANGA_MAX_PAGES,
  MANGA_MIN_PAGES,
  MANGA_PANEL_ASPECTS,
  mangaScriptSystemPrompt,
  mergeBookCast,
  parseBookOutline,
  parseBookPage,
  parseMangaScript,
} from "../manga-script.js";
import { createWork, getWork, touchWork } from "../manga-works.js";
import { generationCudaDevices } from "../media-gpu.js";
import { isWanImageModel } from "./config.js";
import { acquireGpu, pausedByUs, restoreChatModel } from "./gpu-lease.js";
import { IMAGE_SERVER, renderImage, stopImageWorker } from "./image.js";
import { exists, outDir, pauseFlag, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import {
  askGemma,
  askMainLlm,
  assertAllowedRequest,
  isSexualRequest,
  productionRequest,
} from "./text.js";
import type { MediaJob, MediaOptions } from "./types.js";
import { WAN2GP_PYTHON } from "./video.js";

/** Typesets panels into a page with vertical bubbles (PIL, in the Wan2GP venv). */
export const MANGA_COMPOSE = "D:\\media-tools\\image\\manga_compose.py";

export const MANGA_FONT = "C:\\Windows\\Fonts\\BIZ-UDGothicB.ttc";

/**
 * Text for the chat model that writes scripts. The script is written before the GPU is taken;
 * when a previous job still holds the GPU it is handed back first, and while the model is busy
 * answering a chat this waits for it (up to a few minutes) before falling back to the CPU worker.
 */
export async function askScriptWriter(
  system: string,
  user: string,
  maxTokens: number,
  schema?: Record<string, unknown>,
): Promise<string> {
  if (pausedByUs) {
    stopImageWorker();
    await restoreChatModel();
  }
  // Scripts are written by the partner chat model (Gemma 4 26B): its Japanese dialogue is far
  // more natural than the agent model's. Swapping it in takes ~35 s; it stays for the chats.
  await enterGroupChatMode().catch(() => undefined);
  const deadline = Date.now() + 4 * 60_000;
  while (Date.now() < deadline) {
    const reply = await askMainLlm(system, user, maxTokens, 6 * 60_000, schema);
    if (reply) return reply;
    // Voice training owns the GPU: the chat model will not come back soon.
    if ((await exists(pauseFlag())) && !pausedByUs) break;
    await new Promise((resolve) => setTimeout(resolve, 5_000));
  }
  return askGemma(system, user, maxTokens);
}

export async function writeScript<T>(
  job: MediaJob,
  stage: string,
  system: string,
  user: string,
  maxTokens: number,
  parse: (reply: string) => T,
  schema?: Record<string, unknown>,
): Promise<T> {
  markStage(job, stage);
  let lastError = "";
  for (let attempt = 0; attempt < 2; attempt++) {
    if (job.cancelRequested) throw new Error("中止しました");
    try {
      return parse(await askScriptWriter(system, user, maxTokens, schema));
    } catch (err) {
      lastError = String(err instanceof Error ? err.message : err);
    }
  }
  throw new Error(`脚本を作れませんでした: ${lastError}`);
}

export type DrawnPanel = {
  image: string;
  lines: Array<ComicPanel["lines"][number] & { kind?: BubbleKind }>;
  narration?: string;
  /** Where each speaker stands, so their bubble goes on their side. */
  speakers: Record<string, StagePosition>;
  /** Multi-page manga: sound effects and a speed/focus effect drawn over the picture. */
  sfx?: string[];
  effect?: string;
};

/** Draws one panel of a manga page or comic episode. */
export async function drawPanel(
  job: MediaJob,
  cast: ComicCharacter[],
  panel: ComicPanel,
  aspect: NonNullable<MediaOptions["aspect"]>,
  sexual: boolean,
  file: string,
  /** Which GPU's resident worker draws it (multi-page manga uses both). */
  gpu = 0,
): Promise<DrawnPanel> {
  const render = renderPanel(cast, panel);
  await renderImage(job, render.prompt, sexual, file, {
    aspect,
    negative: `${MANGA_EXTRA_NEGATIVE}, ${render.negative}`,
    regions: render.regions,
    loras: render.loras,
    gpu,
  });
  return {
    image: file,
    lines: panel.lines,
    ...(panel.narration ? { narration: panel.narration } : {}),
    speakers: speakerPositions(panel),
  };
}

/**
 * Runs picture tasks on one resident image worker per GPU (two when both can draw an SDXL-type
 * picture, else one). Each lane takes the next task as soon as it is free, so a queue of pictures
 * takes about half as long. The first failure stops both lanes and is thrown.
 */
export async function drawOnLanes(
  job: MediaJob,
  tasks: Array<(gpu: number) => Promise<void>>,
  doneLabel: string,
): Promise<void> {
  const lanes =
    generationCudaDevices(job.options.gpuMode === "fast").length >= 2 &&
    !isWanImageModel(job.options.imageModel) &&
    job.options.imageModel !== "anima" &&
    (await exists(IMAGE_SERVER))
      ? 2
      : 1;
  job.parallelPictures = lanes > 1;
  let next = 0;
  let done = 0;
  let stopped = false;
  const results = await Promise.allSettled(
    Array.from({ length: lanes }, async (_, gpu) => {
      while (next < tasks.length && !stopped) {
        if (job.cancelRequested) throw new Error("中止しました");
        const task = tasks[next++]!;
        try {
          await task(gpu);
        } catch (error) {
          stopped = true;
          throw error;
        }
        done += 1;
        markStage(job, `${doneLabel}（${done}/${tasks.length}）${lanes > 1 ? "・GPU2枚" : ""}`);
      }
    }),
  );
  const failure = results.find((r): r is PromiseRejectedResult => r.status === "rejected");
  if (failure) throw failure.reason;
}

/** Runs the typesetter; returns the files it wrote (one page, or the parts of a strip). */
export async function composePanels(
  workDir: string,
  spec: {
    layout: string;
    title: string;
    episodeTitle?: string;
    panels?: DrawnPanel[];
    /** layout "book": every page with its slots in page pixels */
    pages?: Array<{
      id: string;
      slots: PageLayout["slots"];
      panels: DrawnPanel[];
    }>;
    tone?: "color" | "mono";
    /** layout "book": the cover picture, drawn first and written as the first file */
    cover?: { image: string; title: string; volume: number };
    out: string;
  },
): Promise<string[]> {
  const specFile = path.join(workDir, "page.json");
  await fs.writeFile(specFile, JSON.stringify({ ...spec, font: MANGA_FONT, fontBold: MANGA_FONT }));
  let produced: string[] = [];
  await runTool(WAN2GP_PYTHON, [MANGA_COMPOSE, specFile], {
    cwd: workDir,
    timeoutMs: 10 * 60_000,
    onLine: (line) => {
      const result = /^RESULT (.*)$/.exec(line)?.[1];
      if (!result) return;
      produced = result.trim().startsWith("[") ? (JSON.parse(result) as string[]) : [result.trim()];
    },
  });
  return produced.length > 0 ? produced : [spec.out];
}

export function scriptText(cast: ComicCharacter[], panels: ComicPanel[], title: string): string[] {
  return [
    title,
    ...cast.map((c) => c.look),
    ...panels.flatMap((p) => [
      p.scene,
      p.narration ?? "",
      ...p.characters.map((c) => c.action),
      ...p.lines.map((l) => l.text),
    ]),
  ];
}

export function linesSummary(panels: ComicPanel[]): string[] {
  return panels.map((p) =>
    [p.narration ? `（${p.narration}）` : "", ...p.lines.map((l) => `${l.who}「${l.text}」`)]
      .filter(Boolean)
      .join(" "),
  );
}

/**
 * Manga: the chat model writes the script while it still has the GPU, then every panel is drawn
 * with the image model and the panels are put on a page with frames and speech bubbles.
 */
export async function runMangaJob(job: MediaJob): Promise<void> {
  const layout = job.options.mangaLayout ?? "4koma";
  if (layout === "book") return runBookJob(job);
  const production = await productionRequest(job);
  const adult = isSexualRequest(production);
  const script = await writeScript(
    job,
    "脚本（コマ割りと台詞）を考えています",
    mangaScriptSystemPrompt(layout, adult),
    production,
    2_500,
    (reply) => parseMangaScript(reply, layout),
  );
  const allText = scriptText(script.cast, script.panels, script.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = isSexualRequest(`${production} ${allText.join(" ")}`);

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const workDir = path.join(outDir(), `${job.id}-manga`);
  await fs.mkdir(workDir, { recursive: true });
  try {
    const aspects = MANGA_PANEL_ASPECTS[layout];
    const panels = new Array<DrawnPanel>(script.panels.length);
    await drawOnLanes(
      job,
      script.panels.map((panel, index) => async (gpu: number) => {
        const file = path.join(workDir, `panel-${index + 1}.png`);
        panels[index] = await drawPanel(
          job,
          script.cast,
          panel,
          aspects[index] ?? "landscape",
          sexual,
          file,
          gpu,
        );
      }),
      "コマを描きました",
    );
    markStage(job, "コマを並べて吹き出しを入れています");
    const file = await resultPath(job, ".png");
    await composePanels(workDir, {
      layout,
      title: script.title,
      panels,
      tone: job.options.mangaTone,
      out: file,
    });
    job.file = file;
    job.mime = "image/png";
    // The script goes with the result so the app (and the assistant) can show what was written.
    job.manga = { title: script.title, lines: linesSummary(script.panels) };
  } finally {
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => undefined);
  }
}

/** The cover picture: the main characters standing together, looking at the reader. */
function coverPanel(cast: ComicCharacter[]): ComicPanel {
  const people = cast.slice(0, 3);
  const spots: StagePosition[] =
    people.length === 1
      ? ["center"]
      : people.length === 2
        ? ["left", "right"]
        : ["left", "center", "right"];
  return {
    frame: "tall",
    scene:
      "book cover illustration, key visual, dynamic composition, cowboy shot, looking at viewer, detailed background, vivid colors",
    characters: people.map((c, i) => ({
      name: c.name,
      pos: spots[i] ?? "center",
      action: "standing, looking at viewer, smile",
    })),
    lines: [],
  };
}

/**
 * Multi-page manga: the chat model writes the outline, then each page (3-6 panels with a camera,
 * a size, bubble kinds and sound effects); every page gets a template that fits its panels, the
 * panels are drawn in the shape of their slot, and the typesetter lays the pages out.
 */
async function runBookJob(job: MediaJob): Promise<void> {
  const pageCount = Math.max(
    MANGA_MIN_PAGES,
    Math.min(MANGA_MAX_PAGES, Math.round(job.options.mangaPages ?? MANGA_DEFAULT_PAGES)),
  );
  const production = await productionRequest(job);
  // A sequel starts from what the earlier volume saved: its cast, summary and how it ended.
  const earlier = job.options.mangaSequelOf ? await loadBook(job.options.mangaSequelOf) : undefined;
  if (job.options.mangaSequelOf && !earlier) throw new Error("前の巻の記録が見つかりません");
  const adult = isSexualRequest(`${production} ${earlier ? earlier.request : ""}`);
  const previously = earlier ? previouslyText(earlier) : undefined;
  // People already decided: the last volume's cast, then the ones picked from the character
  // library (linked ones take the library's current look and LoRA). The model fills in the rest.
  const picked: ComicCharacter[] = [];
  for (const id of job.options.characterLock?.characterIds ?? []) {
    const entry = await getCharacter(id);
    if (entry)
      picked.push({
        characterId: entry.id,
        name: entry.name,
        look: characterLook(entry),
        profile: entry.profile,
      });
  }
  const carried = earlier?.cast ?? [];
  const fixed = await resolveCast(
    [
      ...carried,
      ...picked.filter(
        (p) =>
          !carried.some(
            (c) => (c.characterId && c.characterId === p.characterId) || c.name === p.name,
          ),
      ),
    ],
    { adult },
  );
  const autoCast = job.options.mangaAutoCast !== false;
  const outline = await writeScript(
    job,
    earlier ? `第${earlier.volume + 1}巻の流れを考えています` : "全体の流れを考えています",
    bookOutlinePrompt(pageCount, adult, {
      cast: fixed,
      autoCast,
      ...(earlier && previously ? { previously, volume: earlier.volume } : {}),
    }),
    production,
    4_000,
    (reply) => parseBookOutline(reply, pageCount),
  );
  // Decided characters are drawn from their saved looks, whatever the model wrote this time.
  outline.cast = mergeBookCast(outline.cast, fixed, autoCast);
  if (earlier) outline.previously = previously;
  const pages: BookPanel[][] = [];
  for (let index = 0; index < pageCount; index++) {
    const previous = (pages.at(-1) ?? [])
      .slice(-2)
      .map((p) =>
        [p.narration, ...p.lines.map((l) => `${l.who}「${l.text}」`)].filter(Boolean).join(" "),
      )
      .join("\n");
    pages.push(
      await writeScript(
        job,
        `脚本を書いています（${index + 1}/${pageCount}ページ）`,
        bookPagePrompt(outline, index + 1, pageCount, previous, adult),
        production,
        4_000,
        parseBookPage,
      ),
    );
  }
  const everyPanel = pages.flat();
  const allText = scriptText(outline.cast, everyPanel, outline.title);
  assertAllowedRequest(job.request, ...allText);
  const sexual = isSexualRequest(`${production} ${allText.join(" ")}`);

  // The templates are chosen before drawing so each panel is drawn in the shape of its slot.
  const layouts: PageLayout[] = [];
  let previousId: string | undefined;
  for (const [index, panels] of pages.entries()) {
    const big = panels.findIndex((p) => p.size === "large");
    const layout = planPage(panels.length, {
      pageNo: index + 1,
      bigIndex: big >= 0 ? big : undefined,
      previousId,
      slant: index % 2 === 1,
    });
    previousId = layout.id;
    layouts.push(layout);
  }

  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  const workDir = path.join(outDir(), `${job.id}-manga`);
  await fs.mkdir(workDir, { recursive: true });
  try {
    // Every picture (the cover, then each page's panels) is a task; with two GPUs two resident
    // workers take the next task each, so the pictures take about half as long.
    const drawn: DrawnPanel[][] = pages.map((panels) => new Array<DrawnPanel>(panels.length));
    const wantCover = job.options.mangaCover !== false;
    const coverFile = path.join(workDir, "cover.png");
    const tasks: Array<(gpu: number) => Promise<void>> = [];
    if (wantCover) {
      tasks.push(async (gpu) => {
        await drawPanel(
          job,
          outline.cast,
          coverPanel(outline.cast),
          "portrait",
          sexual,
          coverFile,
          gpu,
        );
      });
    }
    for (const [pageIndex, panels] of pages.entries()) {
      for (const [index, panel] of panels.entries()) {
        tasks.push(async (gpu) => {
          const slot = layouts[pageIndex]!.slots[index]!;
          const file = path.join(workDir, `panel-${pageIndex + 1}-${index + 1}.png`);
          const picture = await drawPanel(
            job,
            outline.cast,
            panel,
            slotAspect(slot),
            sexual,
            file,
            gpu,
          );
          drawn[pageIndex]![index] = {
            ...picture,
            lines: picture.lines.map((line, i) => ({ ...line, kind: panel.kinds[i] ?? "say" })),
            sfx: panel.sfx,
            effect: panel.effect,
          };
        });
      }
    }
    await drawOnLanes(job, tasks, "絵を描きました");
    markStage(job, "ページを組んで吹き出しを入れています");
    const file = await resultPath(job, ".png");
    const volume = (earlier?.volume ?? 0) + 1;
    const produced = await composePanels(workDir, {
      layout: "book",
      title: outline.title,
      pages: drawn.map((panels, i) => ({
        id: layouts[i]!.id,
        slots: layouts[i]!.slots,
        panels,
      })),
      tone: job.options.mangaTone,
      ...(wantCover ? { cover: { image: coverFile, title: outline.title, volume } } : {}),
      out: file,
    });
    job.file = produced[0] ?? file;
    job.files = produced;
    job.mime = "image/png";
    // A sequel stays in its work (folder); a first volume starts a new one named after it.
    const workId =
      (earlier?.workId && (await getWork(earlier.workId)) ? earlier.workId : undefined) ??
      (await createWork(outline.title)).id;
    await touchWork(workId);
    job.manga = { title: outline.title, lines: linesSummary(everyPanel), volume, cover: wantCover };
    // Kept so the next volume can be written from it (cast, story so far, how this one ended).
    await saveBook({
      workId,
      jobId: job.id,
      volume,
      title: outline.title,
      request: production,
      cast: outline.cast,
      outline: outline.pages,
      pages,
      summary: outline.summary,
      hook: outline.hook,
      history: earlier
        ? [
            ...earlier.history,
            { volume: earlier.volume, title: earlier.title, summary: earlier.summary },
          ]
        : [],
      ...(earlier ? { previousJobId: earlier.jobId } : {}),
      createdAt: Date.now(),
    });
  } finally {
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => undefined);
  }
}
