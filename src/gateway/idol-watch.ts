import fs from "node:fs/promises";
import path from "node:path";
import { normalizeName } from "./idol-extras.js";
import { idolGroups, idolMembers, idolRosterFile } from "./idol-master.js";
import {
  createSourceFetcher,
  extractPeopleFromHtml,
  listSources,
  memberLinksFromHtml,
  personFromProfileHtml,
  SOURCE_PRESETS,
  type SourceFetcher,
} from "./idol-source.js";

// Watches the official member lists of the groups of the fan book: who is new on the site (new member / new face) and who
// is no longer on it (graduated). The site is read one page at a time, with the same rules as the source import (robots.txt,
// no bot-check evasion). A list that only shows new faces (a ranking, a latest list) is checked for new people only.

export type WatchTarget = { group: string; url: string; newOnly: boolean };
export type WatchResult = {
  group: string;
  url: string;
  checkedAt: number;
  added: string[];
  left: string[];
  newOnly: boolean;
  error?: string;
};
type WatchFile = {
  checkedAt: number;
  results: Record<string, WatchResult>;
  dismissed: Record<string, string[]>;
};

const watchFile = (): string => path.join(path.dirname(idolRosterFile()), "member-watch.json");
export const WATCH_INTERVAL_MS = 24 * 60 * 60 * 1000;
const MIN_NAMES = 3;

async function readWatch(): Promise<WatchFile> {
  try {
    const parsed = JSON.parse(await fs.readFile(watchFile(), "utf-8")) as Partial<WatchFile>;
    return {
      checkedAt: Number(parsed.checkedAt) || 0,
      results: parsed.results ?? {},
      dismissed: parsed.dismissed ?? {},
    };
  } catch {
    return { checkedAt: 0, results: {}, dismissed: {} };
  }
}

async function writeWatch(data: WatchFile): Promise<void> {
  const file = watchFile();
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temp = `${file}.tmp-${process.pid}`;
  await fs.writeFile(temp, JSON.stringify(data, null, 1), "utf-8");
  await fs.rename(temp, file);
}

/** The groups to watch: the saved sources, and the readable presets of the groups that are in the fan book. */
export async function watchTargets(): Promise<WatchTarget[]> {
  const present = new Set(idolGroups().map((row) => row.group));
  const targets = new Map<string, WatchTarget>();
  for (const preset of SOURCE_PRESETS) {
    if (preset.readable && present.has(preset.group))
      targets.set(preset.group, {
        group: preset.group,
        url: preset.url,
        newOnly: preset.newOnly === true,
      });
  }
  // A group with several saved addresses: its people are spread over several pages, so only the first page is watched,
  // and only for new people (a name missing from one page is not "gone").
  const counts = new Map<string, number>();
  const savedAll = await listSources();
  for (const saved of savedAll) counts.set(saved.group, (counts.get(saved.group) ?? 0) + 1);
  const taken = new Set<string>();
  for (const saved of savedAll) {
    if (!present.has(saved.group) || taken.has(saved.group)) continue;
    taken.add(saved.group);
    targets.set(saved.group, {
      group: saved.group,
      url: saved.url,
      newOnly: (counts.get(saved.group) ?? 0) > 1 || targets.get(saved.group)?.newOnly === true,
    });
  }
  return [...targets.values()];
}

/** The names on a list page: by the page's shape, or by the pages the pictures link to. */
export async function namesOnPage(
  url: string,
  fetcher: SourceFetcher = createSourceFetcher(),
): Promise<string[]> {
  const html = await fetcher.list(url);
  let names = extractPeopleFromHtml(html, url).map((person) => person.name);
  if (names.length < MIN_NAMES) {
    names = [];
    for (const link of memberLinksFromHtml(html, url)) {
      try {
        const person = personFromProfileHtml(await fetcher.profile(link), link);
        if (person) names.push(person.name);
      } catch {
        // a page that cannot be read is skipped
      }
    }
  }
  return names;
}

/** Compares the names of a page with the members of the group. */
export function compareNames(
  roster: string[],
  page: string[],
  options: { newOnly: boolean; dismissed?: string[] },
): { added: string[]; left: string[] } {
  const skip = new Set((options.dismissed ?? []).map(normalizeName));
  const known = new Set(roster.map(normalizeName));
  const onPage = new Set(page.map(normalizeName));
  const added = page.filter(
    (name, index) =>
      !known.has(normalizeName(name)) &&
      !skip.has(normalizeName(name)) &&
      page.findIndex((other) => normalizeName(other) === normalizeName(name)) === index,
  );
  // A page with only a few names, or a list that shows new faces only, says nothing about who left.
  const left =
    options.newOnly || page.length < MIN_NAMES
      ? []
      : roster.filter((name) => !onPage.has(normalizeName(name)) && !skip.has(normalizeName(name)));
  return { added, left };
}

let running = false;
export const watchRunning = (): boolean => running;

/** Checks every target one after another (a page at a time). Returns the number of groups that changed. */
export async function runWatchCheck(
  fetcher: SourceFetcher = createSourceFetcher(),
  targets?: WatchTarget[],
): Promise<number> {
  if (running) return 0;
  running = true;
  try {
    const data = await readWatch();
    let changed = 0;
    for (const target of targets ?? (await watchTargets())) {
      const result: WatchResult = {
        group: target.group,
        url: target.url,
        checkedAt: Date.now(),
        added: [],
        left: [],
        newOnly: target.newOnly,
      };
      try {
        const page = await namesOnPage(target.url, fetcher);
        const { added, left } = compareNames(
          idolMembers(target.group).map((member) => member.name),
          page,
          { newOnly: target.newOnly, dismissed: data.dismissed[target.group] },
        );
        result.added = added.slice(0, 80);
        result.left = left.slice(0, 80);
      } catch (error) {
        result.error = error instanceof Error ? error.message : String(error);
      }
      if (result.added.length > 0 || result.left.length > 0) changed += 1;
      data.results[target.group] = result;
    }
    data.checkedAt = Date.now();
    await writeWatch(data);
    return changed;
  } finally {
    running = false;
  }
}

export async function watchState(): Promise<{
  checkedAt: number;
  running: boolean;
  results: WatchResult[];
}> {
  const data = await readWatch();
  return {
    checkedAt: data.checkedAt,
    running,
    results: Object.values(data.results).filter(
      (result) => result.added.length > 0 || result.left.length > 0 || result.error,
    ),
  };
}

/** Starts a check in the background when the last one is older than a day. */
export function maybeStartWatch(): void {
  void readWatch().then((data) => {
    if (!running && Date.now() - data.checkedAt > WATCH_INTERVAL_MS)
      void runWatchCheck().catch(() => undefined);
  });
}

/** Forgets some names of a group's result: the ones the user decided about (added, removed or ignored). */
export async function settleWatch(group: string, names: string[], ignore: boolean): Promise<void> {
  const data = await readWatch();
  const result = data.results[group];
  const gone = new Set(names.map(normalizeName));
  if (result) {
    result.added = result.added.filter((name) => !gone.has(normalizeName(name)));
    result.left = result.left.filter((name) => !gone.has(normalizeName(name)));
  }
  if (ignore)
    data.dismissed[group] = [...new Set([...(data.dismissed[group] ?? []), ...names])].slice(-400);
  await writeWatch(data);
}
