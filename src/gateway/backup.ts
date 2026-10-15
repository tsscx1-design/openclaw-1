import { execFile } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Backups the user asks for in the app: the data of this PC (characters, partners, settings, voices,
// works…) as one zip, and the zips of the phone's own data. Both are kept by date next to each other,
// outside the OpenClaw folder, so that losing that folder does not lose the backups too.
// blocked-terms.json is never put in a backup (it is the user's own file and is looked after by them).

const BACKUP_ROOT = "D:\\OpenClaw-Backups";
export type BackupKind = "pc" | "phone";

/** What of the OpenClaw folder goes into a PC backup (relative to the folder). Models and tools are too big and can be downloaded again. */
const PC_INCLUDE = [
  "openclaw.json",
  "partners.json",
  "partners",
  "groupchats",
  "agents",
  "state",
  "skills",
  "wiki",
  "workspace",
  path.join("tts", "voices"),
  path.join("media", "comics"),
  path.join("media", "sample-factory"),
  path.join("media", "avatar-models"),
];
const PC_OUTPUTS = path.join("media", "out");

const PC_NAME = /^OpenClaw-PC-\d{8}-\d{6}\.zip$/;
const PHONE_NAME = /^OpenClaw-phone-\d{8}-\d{6}\.zip$/;

export const backupDir = (kind: BackupKind): string => path.join(BACKUP_ROOT, kind);
const nameOk = (kind: BackupKind, name: string): boolean =>
  (kind === "pc" ? PC_NAME : PHONE_NAME).test(name);

/** "20261008-213045" in local time. */
export function stamp(date = new Date()): string {
  const p = (n: number, w = 2) => String(n).padStart(w, "0");
  return `${date.getFullYear()}${p(date.getMonth() + 1)}${p(date.getDate())}-${p(date.getHours())}${p(date.getMinutes())}${p(date.getSeconds())}`;
}

export type BackupInfo = { kind: BackupKind; name: string; bytes: number; at: number };

export async function listBackups(): Promise<BackupInfo[]> {
  const out: BackupInfo[] = [];
  for (const kind of ["pc", "phone"] as const) {
    for (const name of await fs.readdir(backupDir(kind)).catch(() => [] as string[])) {
      if (!nameOk(kind, name)) continue;
      const stat = await fs.stat(path.join(backupDir(kind), name)).catch(() => undefined);
      if (stat?.isFile()) out.push({ kind, name, bytes: stat.size, at: stat.mtimeMs });
    }
  }
  return out.toSorted((a, b) => b.at - a.at);
}

// ---- PC backup (runs in the background; the app asks for its state) ----------------------------

type Task = {
  id: string;
  kind: "create" | "restore";
  state: "running" | "done" | "error";
  message: string;
  name?: string;
};
const tasks = new Map<string, Task>();

const run = (file: string, args: string[]): Promise<void> =>
  new Promise((resolve, reject) => {
    execFile(
      file,
      args,
      { windowsHide: true, maxBuffer: 16 * 1024 * 1024, timeout: 60 * 60_000 },
      (err, _out, stderr) =>
        err ? reject(new Error(String(stderr || err.message).slice(0, 400))) : resolve(),
    );
  });

export function backupTask(id: string): Task | undefined {
  return tasks.get(id);
}

/** Starts a PC backup; returns the task to watch. */
export async function startPcBackup(options: { includeOutputs: boolean }): Promise<Task> {
  const home = resolveOpenClawHome();
  const name = `OpenClaw-PC-${stamp()}.zip`;
  const task: Task = {
    id: randomBytes(4).toString("hex"),
    kind: "create",
    state: "running",
    message: "PCのデータをまとめています…",
    name,
  };
  tasks.set(task.id, task);
  void (async () => {
    try {
      await fs.mkdir(backupDir("pc"), { recursive: true });
      const wanted = options.includeOutputs ? [...PC_INCLUDE, PC_OUTPUTS] : PC_INCLUDE;
      const existing: string[] = [];
      for (const rel of wanted)
        if (
          await fs.access(path.join(home, rel)).then(
            () => true,
            () => false,
          )
        )
          existing.push(rel);
      const target = path.join(backupDir("pc"), name);
      await run("tar", [
        "-a",
        "-c",
        "-f",
        target,
        "--exclude",
        "blocked-terms.json",
        "--exclude",
        "*.tmp",
        "-C",
        home,
        ...existing,
      ]);
      const size = (await fs.stat(target)).size;
      task.state = "done";
      task.message = `${name}（${Math.round(size / 1_000_000)}MB）を作りました`;
    } catch (err) {
      task.state = "error";
      task.message = `バックアップできませんでした：${String(err instanceof Error ? err.message : err)}`;
    }
  })();
  return task;
}

/** Puts a PC backup back over the OpenClaw folder. Files that are not in the backup stay as they are. */
export async function startPcRestore(name: string): Promise<Task> {
  if (!nameOk("pc", name)) throw new Error("そのバックアップは使えません");
  const source = path.join(backupDir("pc"), name);
  if (
    !(await fs.access(source).then(
      () => true,
      () => false,
    ))
  )
    throw new Error("そのバックアップが見つかりません");
  const task: Task = {
    id: randomBytes(4).toString("hex"),
    kind: "restore",
    state: "running",
    message: "復元しています…",
    name,
  };
  tasks.set(task.id, task);
  void (async () => {
    try {
      await run("tar", ["-x", "-f", source, "-C", resolveOpenClawHome()]);
      task.state = "done";
      task.message = `${name} から復元しました。ゲートウェイを再起動すると、すべてに反映されます`;
    } catch (err) {
      task.state = "error";
      task.message = `復元できませんでした：${String(err instanceof Error ? err.message : err)}`;
    }
  })();
  return task;
}

// ---- the phone's zip, kept on this PC ------------------------------------------------------------

/** One piece of a phone backup; the last piece closes the file. */
export async function savePhoneChunk(params: {
  name: string;
  index: number;
  total: number;
  dataBase64: string;
}): Promise<{ done: boolean }> {
  if (!nameOk("phone", params.name)) throw new Error("バックアップの名前が正しくありません");
  if (
    !Number.isInteger(params.index) ||
    !Number.isInteger(params.total) ||
    params.index < 0 ||
    params.index >= params.total
  )
    throw new Error("番号が正しくありません");
  const dir = backupDir("phone");
  await fs.mkdir(dir, { recursive: true });
  const part = path.join(dir, `${params.name}.part`);
  const data = Buffer.from(params.dataBase64, "base64");
  if (params.index === 0) await fs.writeFile(part, data);
  else await fs.appendFile(part, data);
  if (params.index < params.total - 1) return { done: false };
  await fs.rename(part, path.join(dir, params.name));
  return { done: true };
}

/** A piece of a stored backup (for the phone to download when restoring). */
export async function readBackupChunk(
  kind: BackupKind,
  name: string,
  offset: number,
  length: number,
): Promise<{ dataBase64: string; bytes: number; total: number }> {
  if (!nameOk(kind, name)) throw new Error("そのバックアップは使えません");
  const file = path.join(backupDir(kind), name);
  const handle = await fs.open(file, "r");
  try {
    const total = (await handle.stat()).size;
    const size = Math.min(Math.max(1, Math.floor(length)), 1_500_000);
    const buffer = Buffer.alloc(size);
    const { bytesRead } = await handle.read(buffer, 0, size, Math.max(0, Math.floor(offset)));
    return {
      dataBase64: buffer.subarray(0, bytesRead).toString("base64"),
      bytes: bytesRead,
      total,
    };
  } finally {
    await handle.close();
  }
}

export async function deleteBackup(kind: BackupKind, name: string): Promise<boolean> {
  if (!nameOk(kind, name)) return false;
  return await fs.rm(path.join(backupDir(kind), name), { force: true }).then(
    () => true,
    () => false,
  );
}
