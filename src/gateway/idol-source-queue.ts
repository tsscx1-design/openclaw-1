import { randomBytes } from "node:crypto";
import { idolGroups } from "./idol-master.js";
import {
  createSourceRun,
  listSources,
  sameSite,
  SOURCE_PRESETS,
  type SourceFetcher,
  type SourceRun,
} from "./idol-source.js";

// A waiting line for the import from a member-list page. The user pastes an address, agrees once, and goes on: the
// lines are read in the background, and what is found is written the same way the "reflect" button did (one backup of
// the roster first; what is already there and the user's own photos are never replaced).
// Lines of different sites run at the same time (each with its own run); lines of one site go one after the other, with
// a pause between pages, so that one site is never asked by two readers at once. Writing to the roster is one at a time.

export type SourceJobStatus = "waiting" | "running" | "done" | "cancelled" | "error";

export type SourceJob = {
  id: string;
  group: string;
  url: string;
  savePhotos: boolean;
  addMissing: boolean;
  status: SourceJobStatus;
  queuedAt: number;
  startedAt?: number;
  finishedAt?: number;
  /** While it runs: people read so far / found on the page. */
  done?: number;
  total?: number;
  /** After it: what was written. */
  result?: {
    found: number;
    updated: number;
    added: number;
    photosSaved: number;
    errors: number;
    xLinks: number;
    xPhotos: number;
    xSkipped: Record<string, number>;
    mixchLinks?: number;
    mixchPhotos?: number;
    method?: string;
  };
  error?: string;
};

const MAX_KEPT = 40;
let jobs: SourceJob[] = [];
/** The run of every job that is going (to stop it). */
const runs = new Map<string, SourceRun>();
/** The writes (roster, photos) of finished checks go one after the other: two jobs never rewrite the roster at once. */
let applyChain: Promise<unknown> = Promise.resolve();
function serialApply<T>(task: () => Promise<T>): Promise<T> {
  const next = applyChain.then(task, task);
  applyChain = next.catch(() => undefined);
  return next;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function sourceQueue(): SourceJob[] {
  return jobs.map((job) => ({ ...job }));
}

/** For tests: a page reader in place of the real one. */
const fetchers = new Map<string, SourceFetcher>();

export function enqueueSource(options: {
  group: string;
  url: string;
  savePhotos?: boolean;
  addMissing?: boolean;
  fetcher?: SourceFetcher;
}): SourceJob {
  const group = options.group.trim();
  const url = options.url.trim();
  if (!group || !url) throw new Error("グループと、メンバー一覧のページの URL を指定してください");
  if (
    jobs.some(
      (job) =>
        job.group === group &&
        job.url === url &&
        (job.status === "waiting" || job.status === "running"),
    )
  ) {
    throw new Error("この取り込みは、すでに待機中か、実行中です");
  }
  const job: SourceJob = {
    id: randomBytes(4).toString("hex"),
    group,
    url,
    savePhotos: options.savePhotos !== false,
    addMissing: options.addMissing !== false,
    status: "waiting",
    queuedAt: Date.now(),
  };
  jobs.push(job);
  if (options.fetcher) fetchers.set(job.id, options.fetcher);
  schedule();
  return { ...job };
}

/**
 * Every saved source, and the readable presets of the groups that are in the fan book, go into the line (the ones that
 * are in it already are left out). Used to read everything again from the same links.
 */
export async function enqueueAllSources(
  options: { savePhotos?: boolean; addMissing?: boolean } = {},
): Promise<{ added: number; queue: SourceJob[] }> {
  const present = new Set(idolGroups().map((row) => row.group));
  const wanted = new Map<string, string[]>();
  for (const preset of SOURCE_PRESETS)
    if (preset.readable && present.has(preset.group)) wanted.set(preset.group, [preset.url]);
  // the addresses the user pasted win over the preset (a group can have several)
  const own = new Map<string, string[]>();
  for (const source of await listSources())
    own.set(source.group, [...(own.get(source.group) ?? []), source.url]);
  for (const [group, urls] of own) wanted.set(group, urls);
  let added = 0;
  for (const [group, urls] of wanted) {
    for (const url of urls) {
      try {
        enqueueSource({ group, url, ...options });
        added += 1;
      } catch {
        // already waiting or running
      }
    }
  }
  return { added, queue: sourceQueue() };
}

/** A waiting one is taken out of the line; a running one is stopped (what was read so far is still written). */
export function cancelSourceJob(id: string): SourceJob[] {
  const job = jobs.find((one) => one.id === id);
  if (job?.status === "waiting") {
    job.status = "cancelled";
    job.finishedAt = Date.now();
  } else if (job?.status === "running") {
    runs.get(job.id)?.cancel();
  }
  return sourceQueue();
}

/** Takes the finished ones off the list. */
export function clearSourceQueue(): SourceJob[] {
  jobs = jobs.filter((job) => job.status === "waiting" || job.status === "running");
  return sourceQueue();
}

function trim(): void {
  const finished = jobs.filter((job) => job.status !== "waiting" && job.status !== "running");
  if (finished.length > MAX_KEPT) {
    const drop = new Set(finished.slice(0, finished.length - MAX_KEPT));
    jobs = jobs.filter((job) => !drop.has(job));
  }
}

async function runJob(job: SourceJob): Promise<void> {
  job.status = "running";
  job.startedAt = Date.now();
  const run = createSourceRun();
  runs.set(job.id, run);
  try {
    run.start({
      group: job.group,
      url: job.url,
      ...(fetchers.has(job.id) ? { fetcher: fetchers.get(job.id)! } : {}),
    });
    for (;;) {
      await sleep(1000);
      const state = run.state();
      job.done = state.done;
      job.total = state.total;
      if (state.status !== "running") break;
    }
    const state = run.state();
    if (state.status === "error") throw new Error(state.error ?? "読めませんでした");
    const base = {
      found: state.found,
      errors: state.errors,
      xLinks: state.xLinks,
      xPhotos: state.xPhotos,
      xSkipped: { ...state.xSkipped },
      mixchLinks: state.mixchLinks,
      mixchPhotos: state.mixchPhotos,
      ...(state.method ? { method: state.method } : {}),
    };
    const nothing = state.withNew === 0 && !(job.addMissing && state.missing.length > 0);
    const applied = nothing
      ? { updated: 0, added: 0, photosSaved: 0 }
      : await serialApply(() =>
          run.apply({ savePhotos: job.savePhotos, addMissing: job.addMissing }),
        );
    job.result = {
      ...base,
      updated: applied.updated,
      added: applied.added,
      photosSaved: applied.photosSaved,
    };
    job.status = state.status === "cancelled" ? "cancelled" : "done";
  } catch (error) {
    job.status = "error";
    job.error = error instanceof Error ? error.message : String(error);
  } finally {
    job.finishedAt = Date.now();
    fetchers.delete(job.id);
    runs.delete(job.id);
    run.reset();
  }
}

/** Starts every waiting job whose site has no job running; the others wait for their turn (a finished job calls this again). */
function schedule(): void {
  for (const job of jobs) {
    if (job.status !== "waiting") continue;
    if (jobs.some((other) => other.status === "running" && sameSite(other.url, job.url))) continue;
    void runJob(job).finally(() => {
      trim();
      schedule();
    });
  }
}
