import { execFile, spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fsSync from "node:fs";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import { resolveOpenClawHome } from "./partner-store.js";

// Crie's development mode: when the user switches it on in the settings, Crie can take a request
// to improve this project or to add a feature (searching the internet and reading, adapting and
// building in GitHub projects are allowed). The work is done on a separate branch in a copy of the
// project (a git worktree made from a snapshot of the working folder), by the project's own agent.
// Nothing reaches the real project until the user looks at the changes and taps 適用.
// The only fixed guard: a change that touches blocked-terms.json is never applied (the user keeps
// that file to themselves).

export type DevStatus =
  | "queued"
  | "working"
  | "review"
  | "nochange"
  | "applied"
  | "rejected"
  | "error";
export type DevRequest = {
  id: string;
  title: string;
  description: string;
  githubUrl?: string;
  status: DevStatus;
  createdAt: number;
  updatedAt: number;
  branch?: string;
  worktree?: string;
  snapshot?: string;
  importDir?: string;
  diffStat?: string;
  needsBuild?: boolean;
  log: string[];
  error?: string;
};

const root = () => path.join(resolveOpenClawHome(), "crie-dev");
const settingsFile = () => path.join(root(), "settings.json");
const requestFile = (id: string) => path.join(root(), "requests", `${id}.json`);

const exec = (
  file: string,
  args: string[],
  options: { cwd?: string; env?: NodeJS.ProcessEnv; timeout?: number } = {},
): Promise<string> =>
  new Promise((resolve, reject) => {
    execFile(
      file,
      args,
      {
        windowsHide: true,
        maxBuffer: 64 * 1024 * 1024,
        timeout: options.timeout ?? 10 * 60_000,
        ...(options.cwd ? { cwd: options.cwd } : {}),
        ...(options.env ? { env: options.env } : {}),
      },
      (err, stdout, stderr) =>
        err ? reject(new Error(String(stderr || err.message).slice(0, 600))) : resolve(stdout),
    );
  });

function findRepoRoot(): string {
  const candidates = [
    process.env.OPENCLAW_REPO_ROOT,
    process.cwd(),
    path.dirname(fileURLToPath(import.meta.url)),
  ];
  for (const start of candidates) {
    if (!start) continue;
    let dir = path.resolve(start);
    for (let i = 0; i < 8; i++) {
      if (fsSync.existsSync(path.join(dir, ".git"))) return dir;
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
  }
  throw new Error("プロジェクトのフォルダ（.git のある所）が見つかりません");
}

export async function devEnabled(): Promise<boolean> {
  try {
    return (
      (JSON.parse(await fs.readFile(settingsFile(), "utf-8")) as { enabled?: boolean }).enabled ===
      true
    );
  } catch {
    return false;
  }
}

export async function setDevEnabled(enabled: boolean): Promise<void> {
  await fs.mkdir(root(), { recursive: true });
  await fs.writeFile(settingsFile(), JSON.stringify({ enabled }), "utf-8");
}

async function save(request: DevRequest): Promise<void> {
  request.updatedAt = Date.now();
  await fs.mkdir(path.dirname(requestFile(request.id)), { recursive: true });
  await fs.writeFile(requestFile(request.id), JSON.stringify(request, null, 1), "utf-8");
}

export async function getDevRequest(id: string): Promise<DevRequest | undefined> {
  if (!/^[a-f0-9]{8}$/.test(id)) return undefined;
  try {
    return JSON.parse(await fs.readFile(requestFile(id), "utf-8")) as DevRequest;
  } catch {
    return undefined;
  }
}

export async function listDevRequests(): Promise<DevRequest[]> {
  const names = await fs.readdir(path.join(root(), "requests")).catch(() => [] as string[]);
  const rows = await Promise.all(
    names.filter((n) => n.endsWith(".json")).map((n) => getDevRequest(n.replace(/\.json$/, ""))),
  );
  return rows.filter((r): r is DevRequest => !!r).toSorted((a, b) => b.createdAt - a.createdAt);
}

const log = async (request: DevRequest, line: string): Promise<void> => {
  request.log = [...request.log, `${new Date().toLocaleTimeString("ja-JP")} ${line}`].slice(-60);
  await save(request);
};

/** A commit of the whole working folder (untracked files too), made without touching the real index or branch. */
async function snapshotCommit(repo: string): Promise<string> {
  const index = path.join(root(), `index-${randomBytes(4).toString("hex")}`);
  const env = { ...process.env, GIT_INDEX_FILE: index };
  try {
    await exec("git", ["add", "-A"], { cwd: repo, env });
    const tree = (await exec("git", ["write-tree"], { cwd: repo, env })).trim();
    const head = (await exec("git", ["rev-parse", "HEAD"], { cwd: repo })).trim();
    return (
      await exec(
        "git",
        [
          "-c",
          "user.name=Crie",
          "-c",
          "user.email=crie@localhost",
          "commit-tree",
          tree,
          "-p",
          head,
          "-m",
          "snapshot for Crie",
        ],
        { cwd: repo },
      )
    ).trim();
  } finally {
    await fs.rm(index, { force: true });
  }
}

/** Makes node_modules visible in the copy without copying it (a directory junction). */
async function linkNodeModules(repo: string, worktree: string): Promise<void> {
  const source = path.join(repo, "node_modules");
  if (!fsSync.existsSync(source)) return;
  await fs.symlink(source, path.join(worktree, "node_modules"), "junction").catch(() => undefined);
}

const GITHUB_URL = /^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?\/?$/;

async function importGithub(request: DevRequest): Promise<void> {
  if (!request.githubUrl) return;
  if (!GITHUB_URL.test(request.githubUrl))
    throw new Error(
      "GitHub のアドレスは https://github.com/ユーザー/リポジトリ の形で指定してください",
    );
  const name = request.githubUrl
    .replace(/\/$/, "")
    .split("/")
    .slice(-2)
    .join("__")
    .replace(/\.git$/, "");
  const dir = path.join(root(), "imports", `${request.id}-${name}`);
  await fs.mkdir(path.dirname(dir), { recursive: true });
  await log(request, `GitHub から読み込んでいます（${request.githubUrl}）`);
  await exec("git", ["clone", "--depth", "1", request.githubUrl, dir], { timeout: 15 * 60_000 });
  request.importDir = dir;
  await save(request);
}

function agentPrompt(request: DevRequest, worktree: string): string {
  return [
    `あなたはクリエ。このプロジェクト（OpenClaw）を良くするため、次の依頼を実装する。作業場所は ${worktree} （本体のコピー。ここで自由に直してよい。本体には、ユーザーが確認して「適用」を押すまで反映されない）。`,
    `【依頼】${request.title}`,
    request.description,
    request.importDir
      ? `【参考にする GitHub プロジェクト】${request.githubUrl}\n読み込み済みの場所: ${request.importDir}\nREADME とソースを読んで、使える部分をこのプロジェクトに組み込む・改造してよい（ライセンスの表記は残す）。`
      : "",
    "インターネット検索は使ってよい。調べた内容は、実装に役立つ範囲で使う。",
    "進め方: まず関連するファイルを探して読む → 変更する → 型チェック（node_modules/.bin/tsgo -p tsconfig.core.json --noEmit）で確認する。",
    "守ること: dist/blocked-terms.json と src/gateway/blocked-terms.json は読まない・直さない。ビルド（pnpm build）は実行しない（ユーザーが後でする）。",
    "終わったら、何をどう変えたかを日本語で短くまとめる。",
  ]
    .filter(Boolean)
    .join("\n\n");
}

async function runAgent(request: DevRequest, repo: string, worktree: string): Promise<void> {
  const messageFile = path.join(root(), "requests", `${request.id}.md`);
  await fs.writeFile(messageFile, agentPrompt(request, worktree), "utf-8");
  await log(request, "クリエが作業を始めました（作業用のコピーで）");
  await new Promise<void>((resolve, reject) => {
    const child = spawn(
      process.execPath,
      [
        path.join(repo, "openclaw.mjs"),
        "agent",
        "--local",
        "--message-file",
        messageFile,
        "--timeout",
        "3600",
        "--json",
      ],
      {
        cwd: worktree,
        windowsHide: true,
        stdio: ["ignore", "pipe", "pipe"],
        env: { ...process.env, OPENCLAW_WORKSPACE_DIR: worktree, PYTHONUTF8: "1" },
      },
    );
    let tail = "";
    const keep = (chunk: Buffer) => {
      tail = (tail + chunk.toString("utf-8")).slice(-2_000);
    };
    child.stdout.on("data", keep);
    child.stderr.on("data", keep);
    const timer = setTimeout(() => child.kill(), 65 * 60_000);
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      void log(request, `エージェントの出力の最後: ${tail.replace(/\s+/g, " ").slice(-400)}`).then(
        () =>
          code === 0 ? resolve() : reject(new Error(`作業が途中で終わりました（コード ${code}）`)),
      );
    });
  });
}

async function runRequest(id: string): Promise<void> {
  const request = await getDevRequest(id);
  if (!request) return;
  try {
    request.status = "working";
    await log(request, "準備しています");
    const repo = findRepoRoot();
    await importGithub(request);
    request.snapshot = await snapshotCommit(repo);
    request.branch = `crie/${request.id}`;
    request.worktree = path.join(root(), "work", request.id);
    await fs.mkdir(path.dirname(request.worktree), { recursive: true });
    await exec(
      "git",
      ["worktree", "add", "-b", request.branch, request.worktree, request.snapshot],
      { cwd: repo },
    );
    await linkNodeModules(repo, request.worktree);
    await save(request);
    await runAgent(request, repo, request.worktree);
    await exec("git", ["add", "-A", "--", ".", ":(exclude)node_modules"], {
      cwd: request.worktree,
    });
    const status = (await exec("git", ["status", "--porcelain"], { cwd: request.worktree })).trim();
    if (status)
      await exec(
        "git",
        [
          "-c",
          "user.name=Crie",
          "-c",
          "user.email=crie@localhost",
          "commit",
          "-m",
          `Crie: ${request.title}`,
        ],
        { cwd: request.worktree },
      );
    const stat = (
      await exec("git", ["diff", "--stat", request.snapshot, "HEAD"], { cwd: request.worktree })
    ).trim();
    const names = (
      await exec("git", ["diff", "--name-only", request.snapshot, "HEAD"], {
        cwd: request.worktree,
      })
    )
      .split(/\r?\n/)
      .filter(Boolean);
    request.diffStat = stat;
    request.needsBuild = names.some(
      (n) => n.startsWith("src/") || n.startsWith("apps/android/") || n.startsWith("packages/"),
    );
    request.status = names.length > 0 ? "review" : "nochange";
    await log(
      request,
      names.length > 0
        ? `変更ができました（${names.length}ファイル）。確認して適用してください`
        : "変更はありませんでした",
    );
  } catch (err) {
    request.status = "error";
    request.error = String(err instanceof Error ? err.message : err).slice(0, 500);
    await log(request, `できませんでした：${request.error}`);
  }
}

export async function createDevRequest(input: {
  title: string;
  description?: string;
  githubUrl?: string;
}): Promise<DevRequest> {
  if (!(await devEnabled()))
    throw new Error("クリエ開発機能が切れています。設定の「クリエ開発機能」を入にしてください");
  const title = input.title.trim().slice(0, 120);
  if (!title) throw new Error("依頼の題名が必要です");
  const request: DevRequest = {
    id: randomBytes(4).toString("hex"),
    title,
    description: (input.description ?? "").trim().slice(0, 6000),
    ...(input.githubUrl?.trim() ? { githubUrl: input.githubUrl.trim() } : {}),
    status: "queued",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    log: [],
  };
  if (request.githubUrl && !GITHUB_URL.test(request.githubUrl))
    throw new Error(
      "GitHub のアドレスは https://github.com/ユーザー/リポジトリ の形で指定してください",
    );
  await save(request);
  void runRequest(request.id);
  return request;
}

export async function devDiff(id: string): Promise<{ stat: string; patch: string }> {
  const request = await getDevRequest(id);
  if (!request?.worktree || !request.snapshot) throw new Error("その依頼には変更がありません");
  const patch = await exec("git", ["diff", request.snapshot, "HEAD"], { cwd: request.worktree });
  return { stat: request.diffStat ?? "", patch: patch.slice(0, 60_000) };
}

/** Puts the changes of a request into the real project's working folder (not committed). */
export async function applyDevRequest(id: string): Promise<DevRequest> {
  const request = await getDevRequest(id);
  if (!request || request.status !== "review" || !request.worktree || !request.snapshot)
    throw new Error("適用できる変更がありません");
  const repo = findRepoRoot();
  const names = (
    await exec("git", ["diff", "--name-only", request.snapshot, "HEAD"], { cwd: request.worktree })
  )
    .split(/\r?\n/)
    .filter(Boolean);
  if (names.some((n) => /(^|\/)blocked-terms\.json$/i.test(n))) {
    throw new Error(
      "blocked-terms.json に触れる変更は、適用しません（このファイルはユーザーだけが扱います）",
    );
  }
  const patchFile = path.join(root(), "requests", `${request.id}.patch`);
  await fs.writeFile(
    patchFile,
    await exec("git", ["diff", "--binary", request.snapshot, "HEAD"], { cwd: request.worktree }),
    "utf-8",
  );
  try {
    await exec("git", ["apply", "--3way", "--whitespace=nowarn", patchFile], { cwd: repo });
  } catch (err) {
    throw new Error(
      `本体に重ねられませんでした（本体が作業中に変わったかもしれません）：${String(err instanceof Error ? err.message : err).slice(0, 300)}`,
    );
  }
  request.status = "applied";
  await log(request, "本体に適用しました" + (request.needsBuild ? "（ビルドが必要です）" : ""));
  return request;
}

export async function rejectDevRequest(id: string): Promise<DevRequest> {
  const request = await getDevRequest(id);
  if (!request) throw new Error("その依頼が見つかりません");
  if (request.status === "working") throw new Error("作業中です。終わってから選んでください");
  const repo = findRepoRoot();
  if (request.worktree)
    await exec("git", ["worktree", "remove", "--force", request.worktree], { cwd: repo }).catch(
      () => undefined,
    );
  if (request.branch)
    await exec("git", ["branch", "-D", request.branch], { cwd: repo }).catch(() => undefined);
  request.status = "rejected";
  await log(request, "取りやめました");
  return request;
}
