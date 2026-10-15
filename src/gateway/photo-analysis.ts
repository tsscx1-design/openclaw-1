import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { resolveOpenClawPackageRootSync } from "../infra/openclaw-root.js";

// A photo is read by the local Gemma worker (it sees pictures; nothing leaves the PC) so that what is
// in the photo — hair, eyes, skin, clothes, small things — becomes the character's settings and is
// told to the picture editor as words, not only as a reference picture.

export type PhotoTraits = {
  hair?: string;
  hairstyle?: string;
  eyes?: string;
  skin?: string;
  outfit?: string;
  accessories?: string;
  face?: string;
  body?: string;
  /** What the photo is: a real person, a drawing, a face only, a full-body picture. */
  kind?: string;
};

const GEMMA_PORT = 11437;
const SUBJECT_PYTHON = "D:\\media-tools\\Wan2GP\\venv\\Scripts\\python.exe";

function scriptPath(filename: string): string {
  const root = resolveOpenClawPackageRootSync({ moduleUrl: import.meta.url });
  if (!root) {
    throw new Error("OpenClaw の実行場所を確認できません。");
  }
  return path.join(root, "scripts", "media", filename);
}

/** Optional face detail reference from the same anchor; absence is an intentional non-outcome. */
export async function createFaceReference(source: string, output: string): Promise<boolean> {
  return new Promise((resolve, reject) => {
    execFile(
      SUBJECT_PYTHON,
      [scriptPath("character_face_reference.py"), source, output],
      {
        windowsHide: true,
        timeout: 60_000,
      },
      (error, stdout, stderr) => {
        if (error) {
          reject(new Error(stderr.trim() || error.message));
        } else {
          resolve(stdout.includes("RESULT "));
        }
      },
    );
  });
}

/** Isolate the dominant person before either vision analysis or image editing. */
export async function isolatePhotoSubject(source: string, output: string): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    execFile(
      SUBJECT_PYTHON,
      [scriptPath("character-photo-subject.py"), source, output],
      {
        windowsHide: true,
        timeout: 120_000,
      },
      (error, _stdout, stderr) => {
        if (error) {
          reject(new Error(stderr.trim() || error.message));
        } else {
          resolve();
        }
      },
    );
  });
}

const PROMPT =
  "この写真（または絵）の人物の外見を、日本語で、見たままに詳しく読み取って、JSONだけで返してください。" +
  '形: {"kind":"実写／イラスト、顔だけ／全身","hair":"髪の色と長さ","hairstyle":"髪型・前髪","eyes":"目の色と形の印象","skin":"肌の色","face":"顔の特徴（輪郭・眉・口元・ほくろなど）","outfit":"服装（色・形）","accessories":"小物・アクセサリー・眼鏡など","body":"体型の印象（写っている範囲で）"}。' +
  "写っていない項目は空の文字列にする。想像で足さない。背景や他人の服・顔・小物を対象人物の特徴に混ぜない。";

const clip = (value: unknown, max: number): string | undefined => {
  const text = typeof value === "string" ? value.replace(/\s+/g, " ").trim().slice(0, max) : "";
  return text || undefined;
};

/** Reads the traits from a picture file; undefined when the worker is not up or the answer is unusable. */
export async function analyzePhoto(file: string): Promise<PhotoTraits | undefined> {
  try {
    const bytes = await fs.readFile(file);
    const mime = bytes[0] === 0x89 ? "image/png" : "image/jpeg";
    const res = await fetch(`http://127.0.0.1:${GEMMA_PORT}/v1/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({
        messages: [
          {
            role: "user",
            content: [
              { type: "text", text: PROMPT },
              {
                type: "image_url",
                image_url: { url: `data:${mime};base64,${bytes.toString("base64")}` },
              },
            ],
          },
        ],
        max_tokens: 600,
        temperature: 0.1,
        chat_template_kwargs: { enable_thinking: false },
      }),
      signal: AbortSignal.timeout(120_000),
    });
    if (!res.ok) {
      return undefined;
    }
    const data = (await res.json()) as { choices?: Array<{ message?: { content?: string } }> };
    return parseTraits(data.choices?.[0]?.message?.content ?? "");
  } catch {
    return undefined;
  }
}

/** The JSON part of the answer, cleaned; undefined when there is none. */
export function parseTraits(text: string): PhotoTraits | undefined {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return undefined;
  }
  try {
    const row = JSON.parse(text.slice(start, end + 1)) as Record<string, unknown>;
    const traits: PhotoTraits = {
      hair: clip(row.hair, 60),
      hairstyle: clip(row.hairstyle, 60),
      eyes: clip(row.eyes, 60),
      skin: clip(row.skin, 40),
      face: clip(row.face, 100),
      outfit: clip(row.outfit, 100),
      accessories: clip(row.accessories, 100),
      body: clip(row.body, 60),
      kind: clip(row.kind, 30),
    };
    return Object.values(traits).some(Boolean) ? traits : undefined;
  } catch {
    return undefined;
  }
}

/** The traits as one English-friendly line for the picture editor (Japanese words are understood too). */
export function traitsForEditor(traits: PhotoTraits): string {
  const parts: Array<[string, string | undefined]> = [
    ["hair", [traits.hair, traits.hairstyle].filter(Boolean).join("、") || undefined],
    ["eyes", traits.eyes],
    ["skin", traits.skin],
    ["face", traits.face],
    ["outfit", traits.outfit],
    ["accessories", traits.accessories],
    ["body", traits.body],
  ];
  return parts
    .filter(([, value]) => value)
    .map(([key, value]) => `${key}: ${value}`)
    .join("; ");
}
