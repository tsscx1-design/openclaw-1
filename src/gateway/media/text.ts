import { assertNoBlockedTerms, isSexualText } from "../blocked-terms-config.js";
import {
  characterLook,
  getCharacter,
  type LibraryCharacter,
  lookWithOutfit,
} from "../comic-characters.js";
import { getMediaJob } from "../media-studio.js";
import {
  SPECIALIST_WORKERS,
  startSpecialistWorker,
  waitForWorkerReady,
} from "../specialist-router.js";
import { isWanImageModel, MAX_TEXT, sdxlModelOf, sdxlSpecOf } from "./config.js";
import { exists, pauseFlag } from "./paths.js";
import type { MediaJob } from "./types.js";

// ---------- Safety ----------

/**
 * 性的な依頼かどうか。成人向けの絵にするか、成人向けのネガティブタグを足すかを決めるための判定で、
 * 禁止の判定ではない。語句は dist/blocked-terms.json の sexualTerms にあり、コードには持たない。
 */
export function isSexualRequest(text: string): boolean {
  return isSexualText(text);
}

/**
 * dist/blocked-terms.json の禁止語句が含まれる依頼は作らない。ファイルが無い・読めないときも作らない。
 * 固定の禁止語は持たず、禁止の中身はすべてそのファイルで決まる。
 */
export function assertAllowedRequest(...texts: string[]): void {
  assertNoBlockedTerms(...texts);
}

/** Printable text only, so a prompt can never turn into extra command-line flags. */
export function sanitizeMediaText(raw: string, max = MAX_TEXT): string {
  return raw
    .replace(/[\r\n\t]+/g, " ")
    .replace(
      /[^\p{L}\p{N}\s,.:;!?()'"_+\-/&\u3001\u3002\u30FB\u30FC\u301C\uFF5E\uFF01\uFF1F\u300C\u300D]/gu,
      "",
    )
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

// ---------- Prompt translation (main LLM on the GPU, else the gemma worker on CPU) ----------

const MAIN_LLM = "http://127.0.0.1:11434";

/**
 * The chat model on the GPU writes a prompt in ~1-2 s where the CPU gemma worker took 5-10 s.
 * It is skipped (undefined) while the GPU is lent out (pause flag), while it is busy answering a
 * chat (one slot), or when it fails, so a media job never waits on the conversation.
 */
export async function askMainLlm(
  system: string,
  user: string,
  maxTokens: number,
  timeoutMs = 30_000,
  schema?: Record<string, unknown>,
): Promise<string | undefined> {
  if (await exists(pauseFlag())) return undefined;
  try {
    const slots = await fetch(`${MAIN_LLM}/slots`, { signal: AbortSignal.timeout(2_000) });
    if (slots.ok) {
      const list = (await slots.json()) as Array<{ is_processing?: boolean }>;
      if (list.some((slot) => slot.is_processing)) return undefined;
    }
    const response = await fetch(`${MAIN_LLM}/v1/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        max_tokens: maxTokens,
        temperature: 0.3,
        chat_template_kwargs: { enable_thinking: false },
        // A schema makes llama.cpp generate only text that fits it (see comic-series.ts).
        ...(schema
          ? { response_format: { type: "json_schema", json_schema: { name: "script", schema } } }
          : {}),
      }),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) return undefined;
    const data = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    return data.choices?.[0]?.message?.content?.trim() || undefined;
  } catch {
    return undefined;
  }
}

async function askPromptWriter(system: string, user: string, maxTokens: number): Promise<string> {
  return (await askMainLlm(system, user, maxTokens)) ?? (await askGemma(system, user, maxTokens));
}

export async function askGemma(system: string, user: string, maxTokens: number): Promise<string> {
  const worker = SPECIALIST_WORKERS.find((entry) => entry.key === "gemma");
  if (!worker) throw new Error("gemma worker is not configured");
  // Probe /health first: the PowerShell port lookup in startSpecialistWorker is slow when the
  // gateway runs as a scheduled task, and the worker is normally already up.
  const up = await fetch(`http://127.0.0.1:${worker.port}/health`, {
    signal: AbortSignal.timeout(3_000),
  })
    .then((res) => res.ok)
    .catch(() => false);
  if (!up) {
    const started = await startSpecialistWorker("gemma");
    if (!started.success) throw new Error(started.message);
    if (!(await waitForWorkerReady(worker.port)))
      throw new Error("gemma worker did not become ready");
  }
  const response = await fetch(`http://127.0.0.1:${worker.port}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      max_tokens: maxTokens,
      temperature: 0.3,
      chat_template_kwargs: { enable_thinking: false },
    }),
    signal: AbortSignal.timeout(5 * 60_000),
  });
  if (!response.ok) throw new Error(`gemma worker HTTP ${response.status}`);
  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  return data.choices?.[0]?.message?.content?.trim() ?? "";
}

const ASCII_ONLY = (text: string) => text.replace(/[^\x20-\x7E]/g, " ");

async function toImageTags(request: string): Promise<string> {
  const tags = await askPromptWriter(
    "You write prompts for an anime Stable Diffusion XL model (Illustrious). Convert the user's request into 10-30 comma-separated English danbooru-style tags: subject count and gender, appearance, clothing, pose, expression, background, lighting, composition. Keep every detail the user asked for. Output only the tags.",
    request,
    200,
  );
  return sanitizeMediaText(ASCII_ONLY(tags), 700);
}

/** Photo-real SDXL checkpoints follow short descriptive phrases better than anime tags. */
async function toPhotoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a photorealistic Stable Diffusion XL model. Convert the user's request into one line of 15-35 comma-separated short English phrases: subject, age, ethnicity, hair, face and expression, body, clothing, pose, setting, lighting, camera shot and lens. People are adults; write an explicit adult age such as 'in her mid 20s'. Unless the user names another ethnicity or nationality, every person is Japanese: write 'Japanese woman' or 'Japanese man' and add 'East Asian facial features'. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 800);
}

/** Z-Image understands sentences better than booru tags. */
async function toNaturalImagePrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a text-to-image model. Rewrite the user's request as two to four vivid English sentences describing subject, appearance, clothing, pose, expression, setting, lighting, art style (anime illustration unless the user asks otherwise) and composition. Keep every detail the user asked for. Output only the prompt.",
    request,
    220,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 900);
}

export async function productionRequest(job: MediaJob): Promise<string> {
  if (job.productionPrompt) return job.productionPrompt;
  const notes: string[] = [];
  const lock = job.options.characterLock;
  if (lock) {
    const characters = (await Promise.all(lock.characterIds.map((id) => getCharacter(id)))).filter(
      (entry): entry is LibraryCharacter => Boolean(entry),
    );
    if (characters.length > 0) {
      const details = characters.map((entry) => {
        const outfit = lock.outfit
          ? entry.outfits?.find((candidate) => candidate.name === lock.outfit)?.tags
          : undefined;
        const look = characterLook(entry);
        return `${entry.name}: ${outfit ? lookWithOutfit(look, outfit) : look}`;
      });
      const kept = [
        lock.keepFace ? "顔" : "",
        lock.keepBody ? "体格" : "",
        lock.keepHair ? "髪" : "",
        lock.keepOutfit ? "衣装" : "",
      ].filter(Boolean);
      notes.push(
        `【キャラクター固定】${details.join(" / ")}。${kept.join("・")}を全カットで同一に保つ。`,
      );
    }
  }
  const scene = job.options.sceneState;
  if (scene) {
    const inherited = scene.inheritJobId ? await getMediaJob(scene.inheritJobId) : undefined;
    const sourceScene = inherited?.options.sceneState;
    const values = {
      place: scene.place ?? sourceScene?.place,
      camera: scene.camera ?? sourceScene?.camera,
      pose: scene.pose ?? sourceScene?.pose,
      wardrobe: scene.wardrobe ?? sourceScene?.wardrobe,
      continuity: scene.continuity ?? sourceScene?.continuity,
    };
    const sceneLines = [
      values.place ? `場所: ${values.place}` : "",
      values.camera ? `カメラ: ${values.camera}` : "",
      values.pose ? `姿勢: ${values.pose}` : "",
      values.wardrobe ? `衣服の状態: ${values.wardrobe}` : "",
      values.continuity ? `引き継ぐ状態: ${values.continuity}` : "",
    ].filter(Boolean);
    if (sceneLines.length > 0)
      notes.push(`【シーン状態】${sceneLines.join("。")}。前の場面との連続性を保つ。`);
  }
  job.productionPrompt = [job.request, ...notes].filter(Boolean).join("\n");
  return job.productionPrompt;
}

/** Prompt text for the chosen image model: booru tags for anime SDXL, phrases for photo SDXL, sentences for Z-Image. */
export async function imagePromptFor(job: MediaJob): Promise<string> {
  const request = await productionRequest(job);
  if (isWanImageModel(job.options.imageModel)) return toNaturalImagePrompt(request);
  return sdxlSpecOf(sdxlModelOf(job)).photo ? toPhotoPrompt(request) : toImageTags(request);
}

export async function toMotionPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for an image-to-video model. Describe in one or two short English sentences the motion and camera movement the user wants (what moves, how, camera). Output only the description.",
    request || "subtle natural motion, gentle camera movement",
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 500);
}

/** LTX-2 renders sound too, so its prompt also describes what is heard. */
export async function toAudioVideoPrompt(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write prompts for a video model that also generates the soundtrack. In two or three short English sentences describe the motion, the camera, and what is heard (voices, ambient sound, music, effects). Keep any spoken line in the user's language inside quotes. Output only the description.",
    request || "subtle natural motion, gentle camera movement, soft ambient sound",
    200,
  );
  return sanitizeMediaText(text.replace(/[\r\n]+/g, " "), 700);
}

const H3_PREFIX = "integrated_multimodal_description:";

/** MiniMax H3 reads a two-part script: the shot (with optional dialogue) and the soundscape. */
export async function toH3Prompt(request: string, seconds: number): Promise<string> {
  const text = await askPromptWriter(
    `You write prompts for MiniMax H3, which animates a first frame into a ${seconds}-second realistic clip with synchronized sound. Write exactly two lines in English:\n${H3_PREFIX} [Shot 1] A ${seconds}-second realistic single take. <who is in the frame, what they do, their expression, how the camera moves>\noverall_soundscape: <the sounds heard>\nPeople are adults. If someone should speak, put the words in Japanese inside the first line as (S1) <d>[Japanese] spoken words</d>. Keep every detail the user asked for. Output only the two lines.`,
    request || "natural subtle motion with a gentle camera push in and soft ambient sound",
    260,
  );
  return sanitizeH3Prompt(text);
}

/**
 * H3's script needs line breaks, brackets and <d> tags, which sanitizeMediaText removes. It only
 * travels inside the Wan2GP request JSON, never on a command line.
 */
export function sanitizeH3Prompt(raw: string): string {
  const text = raw
    .replace(/\r\n?/g, "\n")
    .replace(/[^\p{L}\p{N} \n,.:;!?()'"_+\-/&[\]<>、。・ー〜～！？「」]/gu, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim()
    .slice(0, 1_500);
  return text.startsWith(H3_PREFIX) ? text : `${H3_PREFIX} [Shot 1] ${text}`;
}

export async function toMusicCaption(request: string): Promise<string> {
  const text = await askPromptWriter(
    "You write captions for a text-to-music model. Rewrite the user's request as one English sentence listing genre, mood, tempo feel, instruments and vocal type (for example: 'Upbeat J-pop with bright synths, punchy drums and a cheerful female vocal'). Output only the caption.",
    request,
    120,
  );
  return sanitizeMediaText(ASCII_ONLY(text), 400);
}
