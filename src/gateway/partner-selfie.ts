import { listCharacters } from "./comic-characters.js";
import { parseJsonObject } from "./llm-json.js";
import { listMediaJobs, type MediaJob, type MediaOptions, startMediaJob } from "./media-studio.js";
import { affectNote, loadAffect, sceneForMedia, type Affect } from "./partner-affect.js";
import { localDateKey, partnerFile, readJsonFile, writeJsonFile } from "./partner-store.js";
import {
  buildPartnerCharacterPrompt,
  completeWithMainModel,
  type PartnerProfile,
} from "./server-methods/partners.js";

// Selfies a partner sends by herself: now and then (settings.selfie, up to selfiePerDay a day) she
// decides from her feelings, the scene of the conversation and the hour what picture to send, the
// media studio draws it with her library character, and the chat gets it with a line from her
// (partner-proactive.ts delivers it when the job ends). With selfieVideo the picture is sometimes
// animated into a short film.

type SelfieState = { date?: string; count?: number; lastAt?: number };

/** Between two selfies. */
export const SELFIE_GAP_MS = 3 * 60 * 60_000;
/** She does not interrupt: the last message must be this old. */
export const SELFIE_QUIET_MS = 20 * 60_000;
/** Chance per 15-minute tick once everything else allows one, so the time is not predictable. */
const SELFIE_TICK_CHANCE = 0.25;
export const DEFAULT_SELFIES_PER_DAY = 2;

/** From 8:00 until 2:00 the next night. */
export function selfieHourAllowed(hour: number): boolean {
  return hour >= 8 || hour < 2;
}

/** The day a selfie counts for: until 2:00 it still belongs to the evening before. */
export function selfieDayKey(now: Date): string {
  return localDateKey(now.getHours() < 2 ? new Date(now.getTime() - 3 * 60 * 60_000) : now);
}

/** Decides whether a selfie should be sent now. Pure so the schedule is testable. */
export function shouldSendSelfie(params: {
  now: Date;
  state: SelfieState;
  perDay: number;
  lastChatAt?: number;
  /** Something is waiting or running in the media studio. */
  busy: boolean;
  random?: () => number;
}): boolean {
  const { now, state } = params;
  if (!selfieHourAllowed(now.getHours()) || params.busy) return false;
  const sentToday = state.date === selfieDayKey(now) ? (state.count ?? 0) : 0;
  if (sentToday >= params.perDay) return false;
  if (state.lastAt && now.getTime() - state.lastAt < SELFIE_GAP_MS) return false;
  if (params.lastChatAt && now.getTime() - params.lastChatAt < SELFIE_QUIET_MS) return false;
  return (params.random ?? Math.random)() < SELFIE_TICK_CHANCE;
}

/** A film now and then; more often while she is aroused. */
export function shouldMakeVideo(affect: Affect, random: () => number = Math.random): boolean {
  return random() < (affect.arousal >= 60 ? 0.5 : 0.15);
}

function timeOfDay(hour: number): string {
  if (hour < 2 || hour >= 22) return "深夜";
  if (hour < 11) return "朝";
  if (hour < 16) return "昼";
  if (hour < 19) return "夕方";
  return "夜";
}

export type SelfiePlan = { caption: string; photo: string; motion?: string };

/** Reads the model's answer: the caption, what the photo shows and (for a film) how it moves. */
export function parseSelfiePlan(text: string, wantsMotion: boolean): SelfiePlan | undefined {
  const data = parseJsonObject(text);
  const caption = String(data.caption ?? "").trim();
  const photo = String(data.photo ?? "").trim();
  if (!caption || !photo) return undefined;
  const motion = wantsMotion ? String(data.motion ?? "").trim() : "";
  return {
    caption: caption.slice(0, 300),
    photo: photo.slice(0, 600),
    ...(motion ? { motion: motion.slice(0, 300) } : {}),
  };
}

/** The library character she is drawn as: the one she chats as, else one tied to her. */
async function characterOf(partner: PartnerProfile): Promise<string | undefined> {
  if (partner.sourceCharacterId) return partner.sourceCharacterId;
  return (await listCharacters()).find((c) => c.partnerId === partner.id)?.id;
}

async function planSelfie(
  partner: PartnerProfile,
  affect: Affect,
  now: Date,
  video: boolean,
): Promise<SelfiePlan | undefined> {
  const shape = video
    ? '{"caption":"写真に添える一言","photo":"写真の内容","motion":"その写真が数秒どう動くか（表情・仕草・体の動き）"}'
    : '{"caption":"写真に添える一言","photo":"写真の内容"}';
  const answer = await completeWithMainModel({
    messages: [
      {
        role: "system",
        content: `あなたは「${partner.name}」です。キャラクター設定を守ってください。\n\n${await buildPartnerCharacterPrompt(partner)}\n\n${affectNote(affect, partner.name)}`,
      },
      {
        role: "user",
        content:
          `いまは${timeOfDay(now.getHours())}（${now.getHours()}時）です。あなたは、ユーザーに自分から${video ? "短い動画" : "自撮り写真"}を1つ送ります。` +
          "いまの気持ち・高まり・場面・時間帯に合うものを、あなた自身が考えて決めてください。\n" +
          "caption は写真に添える一言（あなたの口調で1〜2文）。photo は写真の内容を日本語で（構図、表情、服装、ポーズ、場所、光）。" +
          `${video ? "motion は、その写真が数秒どう動くか。" : ""}\n次の JSON だけを出力: ${shape}`,
      },
    ],
    temperature: 0.9,
    maxTokens: 500,
  });
  return parseSelfiePlan(answer, video);
}

/** Starts the selfie jobs; the chat gets the result when the last one ends. */
export async function startPartnerSelfie(
  partner: PartnerProfile,
  now = new Date(),
  random: () => number = Math.random,
): Promise<MediaJob | undefined> {
  const affect = await loadAffect(partner.id);
  const video = partner.settings?.selfieVideo === true && shouldMakeVideo(affect, random);
  const plan = await planSelfie(partner, affect, now, video);
  if (!plan) return undefined;
  const characterId = await characterOf(partner);
  const sceneState = sceneForMedia(affect.scene);
  const options: MediaOptions = {
    aspect: "portrait",
    ...(characterId
      ? {
          characterLock: {
            characterIds: [characterId],
            keepFace: true,
            keepBody: true,
            keepHair: true,
            keepOutfit: false,
          },
        }
      : {}),
    ...(sceneState ? { sceneState } : {}),
  };
  const chat = { partnerId: partner.id, selfie: { caption: plan.caption } };
  if (!plan.motion) return startMediaJob("image", plan.photo, options, { chat });
  // The still is drawn first; the film animates it and carries the caption.
  const still = await startMediaJob("image", plan.photo, options);
  return startMediaJob(
    "video",
    plan.motion,
    { aspect: "portrait", sourceImageJobId: still.id },
    { chat },
  );
}

/** One partner on one tick: sends a selfie when the schedule allows it. */
export async function maybeSendPartnerSelfie(
  partner: PartnerProfile,
  params: { now: Date; lastChatAt?: number; random?: () => number },
): Promise<boolean> {
  if (partner.settings?.selfie !== true || partner.realPerson) return false;
  const statePath = partnerFile(partner.id, "selfie.json");
  const state = await readJsonFile<SelfieState>(statePath, {});
  const busy = (await listMediaJobs(20)).some(
    (job) => job.status === "queued" || job.status === "running",
  );
  if (
    !shouldSendSelfie({
      now: params.now,
      state,
      perDay: partner.settings.selfiePerDay ?? DEFAULT_SELFIES_PER_DAY,
      lastChatAt: params.lastChatAt,
      busy,
      random: params.random,
    })
  )
    return false;
  const job = await startPartnerSelfie(partner, params.now, params.random);
  if (!job) return false;
  const day = selfieDayKey(params.now);
  await writeJsonFile(statePath, {
    date: day,
    count: (state.date === day ? (state.count ?? 0) : 0) + 1,
    lastAt: params.now.getTime(),
  } satisfies SelfieState);
  return true;
}
