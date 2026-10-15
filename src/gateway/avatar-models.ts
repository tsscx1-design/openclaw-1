import { execFile } from "node:child_process";
import { randomBytes } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawHome } from "./partner-store.js";

// Avatar models made elsewhere: a Live2D model (a .zip of the model folder), a VRM or glTF 3D model
// (.vrm / .glb), or a 3D file made in Blender and the like (.fbx / .obj / .blend / .pmx / .vrma for
// motions). The app uploads a file in pieces; the PC keeps it under media/avatar-models/<characterId>/<id>/
// with a manifest, checks what it is, and lists it. Showing the model in the chat is a separate step
// (a WebView page with the Live2D / three-vrm libraries); this is the store behind it.

export type AvatarModelKind = "live2d" | "vrm" | "glb" | "motion" | "3d-source";

export type AvatarModel = {
  id: string;
  ownerId: string;
  name: string;
  kind: AvatarModelKind;
  /** The file the viewer opens, relative to the model folder (e.g. "mao.model3.json" or "model.vrm"). */
  entry: string;
  bytes: number;
  addedAt: number;
  /** What was found inside, in words (counts of motions, expressions…). */
  info: string;
  /** The model the chat shows for this character. */
  active: boolean;
  /** Problems found while checking (the model is kept anyway). */
  warnings: string[];
};

const MAX_BYTES = 300 * 1024 * 1024;
const UPLOAD_EXT = /\.(zip|vrm|glb|gltf|vrma|fbx|obj|blend|pmx|pmd|bvh)$/i;

export function avatarModelsDir(): string {
  return path.join(resolveOpenClawHome(), "media", "avatar-models");
}

const safeSegment = (raw: string): string =>
  raw.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 80) || "x";

/** A file name from the app, reduced to something safe. */
export function safeModelFileName(raw: string): string {
  const base = (raw.replace(/\\/g, "/").split("/").pop() ?? "")
    .replace(/[^\p{L}\p{N}._ -]/gu, "_")
    .trim();
  return base.slice(-100) || "model";
}

function run(exe: string, args: string[], cwd?: string, timeoutMs = 300_000): Promise<string> {
  return new Promise((resolve, reject) => {
    execFile(
      exe,
      args,
      { cwd, windowsHide: true, timeout: timeoutMs, maxBuffer: 16 * 1024 * 1024 },
      (err, stdout, stderr) => {
        if (err) reject(new Error(stderr?.toString().slice(-300) || err.message));
        else resolve(stdout.toString());
      },
    );
  });
}

/** Kind of a single file by its name; the zip is looked into separately. */
export function kindOfFile(name: string): AvatarModelKind | "zip" | undefined {
  const lower = name.toLowerCase();
  if (lower.endsWith(".zip")) return "zip";
  if (lower.endsWith(".vrm")) return "vrm";
  if (lower.endsWith(".glb") || lower.endsWith(".gltf")) return "glb";
  if (lower.endsWith(".vrma") || lower.endsWith(".bvh")) return "motion";
  if (/\.(fbx|obj|blend|pmx|pmd)$/.test(lower)) return "3d-source";
  return undefined;
}

/** True when a GLB's JSON part mentions the VRM extension (VRM 0.x "VRM" or VRM 1.0 "VRMC_vrm"). */
export function glbIsVrm(head: Buffer): boolean {
  if (head.length < 20 || head.toString("ascii", 0, 4) !== "glTF") return false;
  const jsonLength = head.readUInt32LE(12);
  const json = head.toString("utf8", 20, Math.min(head.length, 20 + jsonLength));
  return (
    json.includes("VRMC_vrm") || /"extensions":\s*\{[^}]*"VRM"/.test(json) || json.includes('"VRM"')
  );
}

async function listFilesDeep(dir: string, base = dir): Promise<string[]> {
  const out: string[] = [];
  for (const entry of await fs.readdir(dir, { withFileTypes: true }).catch(() => [])) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await listFilesDeep(full, base)));
    else out.push(path.relative(base, full).replace(/\\/g, "/"));
  }
  return out;
}

type Analysis = { kind: AvatarModelKind; entry: string; info: string; warnings: string[] };

/** Looks into an unpacked Live2D folder: the model3.json, the files it names, the motions and expressions. */
export async function analyzeLive2dFolder(dir: string): Promise<Analysis> {
  const files = await listFilesDeep(dir);
  let models = files.filter((f) => f.toLowerCase().endsWith(".model3.json"));
  // A sample pack can hold a PRO and a FREE model; the PRO one (more parameters) is used.
  if (models.length > 1) {
    const pro = models.filter((f) => /(^|[\/_.-])pro([\/_.-]|$)/i.test(f));
    if (pro.length === 1) models = pro;
  }
  if (models.length !== 1) {
    throw new Error(
      models.length === 0
        ? "Live2D のモデル（*.model3.json）が入っていません"
        : `Live2D のモデル（*.model3.json）が${models.length}個あります。1つだけにしてください`,
    );
  }
  const entry = models[0] as string;
  const warnings: string[] = [];
  let info = "Live2D";
  try {
    const json = JSON.parse(await fs.readFile(path.join(dir, entry), "utf8")) as {
      FileReferences?: {
        Moc?: string;
        Textures?: string[];
        Physics?: string;
        Motions?: Record<string, unknown[]>;
        Expressions?: unknown[];
      };
    };
    const refs = json.FileReferences ?? {};
    const base = path.posix.dirname(entry);
    const exists = (rel: string | undefined) =>
      rel ? files.includes(path.posix.normalize(path.posix.join(base, rel))) : true;
    if (!refs.Moc || !exists(refs.Moc)) warnings.push("モデル本体（.moc3）が見つかりません");
    for (const texture of refs.Textures ?? [])
      if (!exists(texture)) warnings.push(`テクスチャが足りません：${texture}`);
    if (refs.Physics && !exists(refs.Physics)) warnings.push("物理演算のファイルが足りません");
    const motions = Object.values(refs.Motions ?? {}).reduce(
      (sum, list) => sum + (Array.isArray(list) ? list.length : 0),
      0,
    );
    info = `Live2D・モーション${motions}個・表情${refs.Expressions?.length ?? 0}個・テクスチャ${refs.Textures?.length ?? 0}枚`;
  } catch {
    warnings.push("model3.json を読めませんでした");
  }
  return { kind: "live2d", entry, info, warnings };
}

async function analyzeSingle(
  file: string,
  kind: Exclude<AvatarModelKind, "live2d">,
): Promise<Analysis> {
  const name = path.basename(file);
  const warnings: string[] = [];
  let resolved: AvatarModelKind = kind;
  let info = "";
  if (kind === "vrm" || kind === "glb") {
    const handle = await fs.open(file, "r");
    try {
      const head = Buffer.alloc(1024 * 1024);
      const { bytesRead } = await handle.read(head, 0, head.length, 0);
      const slice = head.subarray(0, bytesRead);
      if (name.toLowerCase().endsWith(".gltf")) {
        info = "glTF（3D）";
      } else if (slice.toString("ascii", 0, 4) !== "glTF") {
        warnings.push("3Dモデルのファイルとして読めません（先頭が glTF ではありません）");
      } else if (glbIsVrm(slice)) {
        resolved = "vrm";
        info = "VRM（3Dアバター。表情・口パク・視線に対応）";
      } else {
        resolved = "glb";
        info =
          kind === "vrm"
            ? "3D（VRMの印が見つかりません。表情は使えないかもしれません）"
            : "glb（3D）";
        if (kind === "vrm")
          warnings.push(
            "VRMの拡張が見つかりません。Blender の VRM アドオンで書き出し直してください",
          );
      }
    } finally {
      await handle.close();
    }
  } else if (kind === "motion") {
    info = name.toLowerCase().endsWith(".vrma") ? "VRM用のモーション" : "モーション（bvh）";
  } else {
    info = "3Dの元データ（VRM か glb に書き出すと、アバターに使えます）";
  }
  return { kind: resolved, entry: name, info, warnings };
}

const manifestPath = (dir: string) => path.join(dir, "manifest.json");

/** Receives one piece of an uploaded model file; the last piece closes it, unpacks and checks it. */
export async function saveModelChunk(params: {
  ownerId: string;
  name: string;
  index: number;
  total: number;
  dataBase64: string;
  /** Piece number of a model in progress: the upload id returned by the first piece. */
  uploadId?: string;
}): Promise<{ uploadId: string; done: boolean; bytes: number; model?: AvatarModel }> {
  const ownerId = safeSegment(params.ownerId);
  const name = safeModelFileName(params.name);
  if (!UPLOAD_EXT.test(name))
    throw new Error("Live2D は zip、3D は vrm / glb / fbx / obj / blend などを選んでください");
  if (
    !Number.isInteger(params.index) ||
    !Number.isInteger(params.total) ||
    params.index < 0 ||
    params.index >= params.total
  ) {
    throw new Error("アップロードの番号が正しくありません");
  }
  const uploadId =
    params.index === 0 ? randomBytes(6).toString("hex") : safeSegment(params.uploadId ?? "");
  const ownerDir = path.join(avatarModelsDir(), ownerId);
  const dir = path.join(ownerDir, uploadId);
  await fs.mkdir(dir, { recursive: true });
  const part = path.join(dir, `${name}.part`);
  const data = Buffer.from(params.dataBase64, "base64");
  if (params.index === 0) await fs.writeFile(part, data);
  else await fs.appendFile(part, data);
  const bytes = (await fs.stat(part)).size;
  if (bytes > MAX_BYTES) {
    await fs.rm(dir, { recursive: true, force: true });
    throw new Error("ファイルが大きすぎます（300MBまで）");
  }
  if (params.index < params.total - 1) return { uploadId, done: false, bytes };

  // The last piece: name the file, unpack a zip, check what it is, write the manifest.
  const finalFile = path.join(dir, name);
  await fs.rename(part, finalFile);
  const model = await finalizeModel({ ownerId, uploadId, dir, name, finalFile, bytes });
  return { uploadId, done: true, bytes, model };
}

/** Unpacks a finished model file (a zip), checks what it is and writes its manifest. On any problem the folder is removed. */
async function finalizeModel(params: {
  ownerId: string;
  uploadId: string;
  dir: string;
  name: string;
  finalFile: string;
  bytes: number;
}): Promise<AvatarModel> {
  const { ownerId, uploadId, dir, name, finalFile, bytes } = params;
  try {
    const kind = kindOfFile(name);
    let analysis: Analysis;
    if (kind === "zip") {
      const unpacked = path.join(dir, "files");
      await fs.mkdir(unpacked, { recursive: true });
      // bsdtar (in Windows 10+) reads zip files and refuses paths that leave the folder.
      await run("tar", ["-xf", finalFile, "-C", unpacked]);
      await fs.rm(finalFile, { force: true });
      analysis = await analyzeLive2dFolder(unpacked);
      analysis.entry = `files/${analysis.entry}`;
    } else if (kind) {
      analysis = await analyzeSingle(finalFile, kind as Exclude<AvatarModelKind, "live2d">);
    } else {
      throw new Error("このファイルの種類は使えません");
    }
    const model: AvatarModel = {
      id: uploadId,
      ownerId,
      name: name.replace(/\.[^.]+$/, ""),
      kind: analysis.kind,
      entry: analysis.entry,
      bytes,
      addedAt: Date.now(),
      info: analysis.info,
      active: false,
      warnings: analysis.warnings,
    };
    await fs.writeFile(manifestPath(dir), JSON.stringify(model, null, 2), "utf8");
    return model;
  } catch (error) {
    await fs.rm(dir, { recursive: true, force: true });
    throw error;
  }
}

/** The folder on this PC whose files can be imported as models (the user's downloads). */
export const LOCAL_MODEL_DIR = "D:\\media-tools\\downloads";

export async function listLocalModelFiles(): Promise<Array<{ name: string; bytes: number }>> {
  const names = await fs.readdir(LOCAL_MODEL_DIR).catch(() => [] as string[]);
  const out: Array<{ name: string; bytes: number }> = [];
  for (const name of names) {
    if (!UPLOAD_EXT.test(name)) continue;
    const stat = await fs.stat(path.join(LOCAL_MODEL_DIR, name)).catch(() => undefined);
    if (stat?.isFile() && stat.size <= MAX_BYTES) out.push({ name, bytes: stat.size });
  }
  return out;
}

/** Imports a model file that is already on this PC (in LOCAL_MODEL_DIR) for a character. */
export async function importLocalModel(ownerIdRaw: string, fileName: string): Promise<AvatarModel> {
  const name = safeModelFileName(fileName);
  const source = path.join(LOCAL_MODEL_DIR, name);
  const stat = await fs.stat(source).catch(() => undefined);
  if (!stat?.isFile() || !UPLOAD_EXT.test(name)) throw new Error("そのファイルが見つかりません");
  if (stat.size > MAX_BYTES) throw new Error("ファイルが大きすぎます（300MBまで）");
  const ownerId = safeSegment(ownerIdRaw);
  const uploadId = randomBytes(6).toString("hex");
  const dir = path.join(avatarModelsDir(), ownerId, uploadId);
  await fs.mkdir(dir, { recursive: true });
  const finalFile = path.join(dir, name);
  await fs.copyFile(source, finalFile);
  return await finalizeModel({ ownerId, uploadId, dir, name, finalFile, bytes: stat.size });
}

/** Adds a model file that the gateway made itself (for example a generated GLB) to a character. */
export async function importGeneratedModel(
  ownerIdRaw: string,
  filePath: string,
  displayName: string,
): Promise<AvatarModel> {
  const ownerId = safeSegment(ownerIdRaw);
  const ext = path.extname(filePath).toLowerCase() || ".glb";
  const name = safeModelFileName(`${displayName}${ext}`);
  const stat = await fs.stat(filePath);
  const uploadId = randomBytes(6).toString("hex");
  const dir = path.join(avatarModelsDir(), ownerId, uploadId);
  await fs.mkdir(dir, { recursive: true });
  const finalFile = path.join(dir, name);
  await fs.copyFile(filePath, finalFile);
  return await finalizeModel({ ownerId, uploadId, dir, name, finalFile, bytes: stat.size });
}

/** The files the viewer needs for a model: everything in the folder of its entry file. */
export async function listModelFiles(
  ownerId: string,
  id: string,
): Promise<Array<{ path: string; bytes: number }>> {
  const model = (await listAvatarModels(ownerId)).find((row) => row.id === id);
  if (!model) throw new Error("そのモデルが見つかりません");
  const root = modelDir(model.ownerId, model.id);
  const base = path.dirname(model.entry);
  const out: Array<{ path: string; bytes: number }> = [];
  const walk = async (relative: string): Promise<void> => {
    for (const entry of await fs.readdir(path.join(root, relative), { withFileTypes: true })) {
      const rel = relative === "." ? entry.name : `${relative}/${entry.name}`;
      if (entry.isDirectory()) await walk(rel);
      else out.push({ path: rel, bytes: (await fs.stat(path.join(root, rel))).size });
    }
  };
  await walk(base === "." ? "." : base);
  return out;
}

/** A piece of a model file (for the app, which keeps its own copy). */
export async function readModelChunk(
  ownerId: string,
  id: string,
  relative: string,
  offset: number,
  length: number,
): Promise<{ dataBase64: string; bytes: number }> {
  const full = resolveModelFile(safeSegment(ownerId), safeSegment(id), relative);
  if (!full) throw new Error("そのファイルは開けません");
  const handle = await fs.open(full, "r");
  try {
    const size = Math.min(Math.max(1, Math.floor(length)), 1_500_000);
    const buffer = Buffer.alloc(size);
    const { bytesRead } = await handle.read(buffer, 0, size, Math.max(0, Math.floor(offset)));
    return { dataBase64: buffer.subarray(0, bytesRead).toString("base64"), bytes: bytesRead };
  } finally {
    await handle.close();
  }
}

/** Turns the model off for a character (the chat goes back to the pictures). */
export async function deactivateAvatarModels(ownerId: string): Promise<void> {
  for (const model of await listAvatarModels(ownerId)) {
    if (!model.active) continue;
    model.active = false;
    await fs.writeFile(
      manifestPath(modelDir(model.ownerId, model.id)),
      JSON.stringify(model, null, 2),
      "utf8",
    );
  }
}

export async function listAvatarModels(ownerId?: string): Promise<AvatarModel[]> {
  const root = avatarModelsDir();
  const owners = ownerId
    ? [safeSegment(ownerId)]
    : await fs.readdir(root).catch(() => [] as string[]);
  const out: AvatarModel[] = [];
  for (const owner of owners) {
    for (const id of await fs.readdir(path.join(root, owner)).catch(() => [] as string[])) {
      const text = await fs
        .readFile(manifestPath(path.join(root, owner, id)), "utf8")
        .catch(() => undefined);
      if (!text) continue;
      try {
        out.push(JSON.parse(text) as AvatarModel);
      } catch {
        // a broken manifest is skipped
      }
    }
  }
  return out.toSorted((a, b) => b.addedAt - a.addedAt);
}

function modelDir(ownerId: string, id: string): string {
  return path.join(avatarModelsDir(), safeSegment(ownerId), safeSegment(id));
}

export async function deleteAvatarModel(ownerId: string, id: string): Promise<boolean> {
  const dir = modelDir(ownerId, id);
  if (!(await fs.stat(manifestPath(dir)).catch(() => undefined))) return false;
  await fs.rm(dir, { recursive: true, force: true });
  return true;
}

/** Makes one model the one the chat shows for the character (the others of the same kind family are switched off). */
export async function activateAvatarModel(
  ownerId: string,
  id: string,
): Promise<AvatarModel | undefined> {
  const models = await listAvatarModels(ownerId);
  const target = models.find((model) => model.id === safeSegment(id));
  if (!target) return undefined;
  for (const model of models) {
    const active = model.id === target.id;
    if (model.active === active) continue;
    model.active = active;
    await fs.writeFile(
      manifestPath(modelDir(model.ownerId, model.id)),
      JSON.stringify(model, null, 2),
      "utf8",
    );
  }
  return { ...target, active: true };
}

/** A file of a model (for the app's viewer): only inside the model folder. */
export function resolveModelFile(
  ownerId: string,
  id: string,
  relative: string,
): string | undefined {
  const dir = modelDir(ownerId, id);
  const full = path.resolve(dir, relative);
  return full.startsWith(dir + path.sep) ? full : undefined;
}
