import { createHash } from "node:crypto";
import * as fs from "node:fs/promises";
import * as path from "node:path";
import { assertAgeAllowed } from "../blocked-terms-config.js";
import { characterMotionPrompt, readCharacterMotionProfile } from "../character-motion-profile.js";
import { characterLook, getCharacter, lookWithOutfit, specSummary } from "../comic-characters.js";
import { stopIrodoriWorker, synthesizeIrodori } from "../irodori-tts.js";
import { generationCudaDevices } from "../media-gpu.js";
import { getMediaJob } from "../media-studio.js";
import {
  MOTION_TEMPLATES,
  motionCuts,
  motionImageNegative,
  motionKeyJobs,
  type MotionMinutes,
  motionOptionNotes,
  type MotionPhase,
  type MotionScript,
  motionSummary,
  motionSystemPrompt,
  parseMotionScript,
} from "../motion-comic.js";
import {
  applyMotionFlow,
  DEFAULT_MOTION_SFX,
  MOTION_TALK,
  phasesInFlow,
  sfxForCut,
  talkNote,
  voiceStyleNote,
} from "../motion-extras.js";
import { referenceClip } from "../partner-voice-clone.js";
import { isSdxlModel, SDXL_MODELS, sdxlSpecOf } from "./config.js";
import { acquireGpu } from "./gpu-lease.js";
import { stopImageWorker } from "./image.js";
import { writeScript } from "./manga.js";
import { runMotionJoin } from "./motion-join.js";
import { exists, outDir, resultPath } from "./paths.js";
import { markStage, runTool } from "./runner.js";
import { assertAllowedRequest, productionRequest } from "./text.js";
import type { ImageModel, MediaJob, MediaOptions } from "./types.js";
import { WAN2GP_PYTHON } from "./video.js";

/** Motion comic runner (key images, Wan 2.2 cuts, film) and its sound effects; outside the repo. */
export const MOTION_SCRIPT = "D:\\media-tools\\motion-comic\\motion_comic.py";

const MOTION_FELLATIO_SFX = "D:\\media-tools\\motion-comic\\sfx\\tsumugi\\008_フェラ.wav";

/** Spoken calmly in the described voice; the film's lines then copy this voice. */
const MOTION_VOICE_SAMPLE =
  "…こんばんは。今日は、ゆっくりしていってね。あなたのこと、ずっと待ってたんだから。";

/** A cut of 3 s took ~11 min at 832x480; the fast profile (640x368, Real-ESRGAN) takes ~6 min, see clips.py. */
export const MOTION_TIMEOUT_MS = 4 * 60 * 60_000;

/**
 * An adult scene as AI video cuts: the writer fills in the content, the voices are made with
 * Irodori (large), then motion_comic.py draws the key images, makes every cut and joins the film.
 */
export async function runMotionJob(job: MediaJob): Promise<void> {
  if (job.options.motionRevoiceJobId) return revoiceMotionJob(job);
  if (job.options.motionContinueJobId) return continueMotionJob(job);
  if (job.options.motionJoinJobIds?.length) return runMotionJoin(job);
  const inherited = await applyMotionInherit(job);
  const talk = job.options.motionTalk;
  const minutes = job.options.motionMinutes ?? 1;
  // A library character keeps the same face as in the comics (its look and trained LoRA).
  const lockedCharacterId = job.options.characterLock?.characterIds[0];
  const heroineId = job.options.motionCharacterId ?? lockedCharacterId;
  const heroine = heroineId ? await getCharacter(heroineId) : undefined;
  // 多人数シーン: 2〜4人目のキャラクター
  const heroine2 = job.options.motionCharacterId2
    ? await getCharacter(job.options.motionCharacterId2)
    : undefined;
  const heroine3 = job.options.motionCharacterId3
    ? await getCharacter(job.options.motionCharacterId3)
    : undefined;
  const heroine4 = job.options.motionCharacterId4
    ? await getCharacter(job.options.motionCharacterId4)
    : undefined;
  // Her whole setting sheet, adult part included: first person, catchphrase, weak spots, kinks,
  // how she moans — the writer has to use them in the lines.
  const castNote = heroine
    ? `\n女性はキャラ図鑑の「${heroine.name}」で固定（name はこの名前）。設定: ${heroine.profile}${
        heroine.spec
          ? `\n設定シート（セリフと流れに必ず反映する。一人称・口癖・口調を守り、弱いところ・性癖・好きなこと・喘ぎ方を使い、NGは避ける）: ${specSummary(heroine.spec)}`
          : ""
      }${heroine2 ? `\n2人目: 「${heroine2.name}」設定: ${heroine2.profile}` : ""}${heroine3 ? `\n3人目: 「${heroine3.name}」設定: ${heroine3.profile}` : ""}${heroine4 ? `\n4人目: 「${heroine4.name}」設定: ${heroine4.profile}` : ""}`
    : "";
  // 前段シーンのカスタムステップをプロンプトに注入
  const approachNote =
    job.options.motionApproachSteps && job.options.motionApproachSteps.length > 0
      ? `\n\n【前段シーン構成】本番前に以下のステップを順番に描写すること:\n${job.options.motionApproachSteps
          .map((s, i) => `${i + 1}. ${s.label}${s.tags ? ` (${s.tags})` : ""}`)
          .join("\n")}`
      : "";
  const production = await productionRequest(job);
  const script = await writeScript(
    job,
    "台本を書いています",
    motionSystemPrompt(minutes),
    `依頼: ${production}${castNote}${inherited.note}${talkNote(talk)}${voiceStyleNote(job.options.motionVoiceStyles ?? [])}${approachNote}${
      job.options.motionTemplate
        ? `\n体位・場面は「${MOTION_TEMPLATES[job.options.motionTemplate].label}」で固定。セリフもこの体位に合わせる。`
        : ""
    }${motionOptionNotes(job.options.motionOptions) ? `\n${motionOptionNotes(job.options.motionOptions)}` : ""}`,
    2_500,
    (reply) =>
      parseMotionScript(reply, talk === "tuna" ? 0 : talk === "few" || talk === "silent" ? 1 : 2),
  );
  if (talk) {
    // The writer does not always keep to the number it was given.
    const limit = MOTION_TALK[talk].lines;
    script.lines.slow = script.lines.slow.slice(0, limit.slow);
    script.lines.fast = script.lines.fast.slice(0, limit.fast);
    script.lines.climax = script.lines.climax.slice(0, limit.climax);
    script.lines.after = script.lines.after.slice(0, limit.climax);
  }
  if (inherited.script) {
    // The same person as before, in the same place: name, age, voice, look.
    script.name = inherited.script.name;
    script.age = inherited.script.age;
    script.voice = inherited.script.voice;
    script.look = inherited.script.look;
    script.place = inherited.script.place;
  }
  if (job.options.motionFellatio !== undefined) script.fellatio = job.options.motionFellatio;
  if (heroine) {
    script.name = heroine.name;
    // An outfit from her library entry replaces the clothes of her usual look.
    const outfit = heroine.outfits?.find((o) => o.name === job.options.motionOutfit);
    const heroineLook = characterLook(heroine);
    const look = outfit ? lookWithOutfit(heroineLook, outfit.tags) : heroineLook;
    script.look = [heroine.trigger, look].filter(Boolean).join(", ");
  }
  // 多人数シーン: 2〜4人目の look を追加 (Python スクリプトに渡すため拡張)
  const extraLoras: Array<{ path: string; scale: number }> = [];
  const extraLooks: string[] = [];
  for (const [extra, outfitKey] of [
    [heroine2, job.options.motionOutfit2],
    [heroine3, job.options.motionOutfit3],
    [heroine4, job.options.motionOutfit4],
    // SAFETY: each element is [CharacterEntry|undefined, string|undefined]; the tuple type is exact.
  ] as [typeof heroine, string | undefined][]) {
    if (!extra) continue;
    const outfit = extra.outfits?.find((o) => o.name === outfitKey);
    const extraLook = characterLook(extra);
    const look = outfit ? lookWithOutfit(extraLook, outfit.tags) : extraLook;
    extraLooks.push([extra.trigger, look].filter(Boolean).join(", "));
    if (extra.lora && extra.loraStatus === "ready")
      extraLoras.push({ path: extra.lora, scale: 0.8 });
  }
  const allLines = Object.values(script.lines).flat();
  assertAllowedRequest(job.request, script.look, script.position, ...allLines);
  assertAgeAllowed(script.age);
  job.manga = { title: motionSummary(script, minutes), lines: allLines };

  const dir = path.join(outDir(), `${job.id}-motion`);
  await fs.mkdir(dir, { recursive: true });
  if (inherited.dir) {
    // The same voice as before: its reference recording is carried over.
    const reference = path.join(inherited.dir, "voice-reference.wav");
    const mine = path.join(dir, "voice-reference.wav");
    if ((await exists(reference)) && !(await exists(mine))) await fs.copyFile(reference, mine);
  }
  const state: MotionState = {
    script,
    extraLoras,
    extraLooks,
    minutes,
    options: job.options,
    baseSeed: Number.parseInt(job.id.slice(0, 6), 16) % 100_000,
  };
  await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
  if (job.options.motionPreview !== false) return motionPreviewPhase(job, dir, state);
  return motionFilmPhase(job, dir, state);
}

/** Voices, cuts and the film, once the script (and the sample picture) is settled. */
async function motionFilmPhase(job: MediaJob, dir: string, state: MotionState): Promise<void> {
  const { script, extraLoras, extraLooks, minutes, options } = state;
  const heroine = options.motionCharacterId
    ? await getCharacter(options.motionCharacterId)
    : undefined;
  const heroine2 = options.motionCharacterId2
    ? await getCharacter(options.motionCharacterId2)
    : undefined;
  const heroine3 = options.motionCharacterId3
    ? await getCharacter(options.motionCharacterId3)
    : undefined;
  const heroine4 = options.motionCharacterId4
    ? await getCharacter(options.motionCharacterId4)
    : undefined;
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();

  // Voices first, while the GPU is free of the image and video models. Every line speaks with one
  // reference recording: lines made from a description alone drift to another person by the end.
  // The voice asked for, else the heroine's own voice from the character library.
  const voiceId = options.voiceId ?? (options.voiceCaption?.trim() ? undefined : heroine?.voiceId);
  let referenceWav = voiceId ? await referenceClip(voiceId) : undefined;
  if (voiceId && !referenceWav)
    throw new Error("選んだ声の音声見本が見つかりません。声の設定を確認してください");
  const voiceFiles = new Map<string, string>();
  const toSpeak: Array<[string, string]> = [];
  const flowPhases = phasesInFlow(options.motionFlow);
  for (const [phase, lines] of Object.entries(script.lines)) {
    if (flowPhases && !flowPhases.has(phase as MotionPhase)) continue;
    lines.forEach((line, i) => toSpeak.push([`line-${phase}-${i}`, line]));
  }
  for (const [phase, text] of Object.entries(script.breaths))
    toSpeak.push([`breath-${phase}`, text]);
  const voiceDevices = generationCudaDevices(job.options.gpuMode === "fast");
  const voiceDual = voiceDevices.length >= 2;
  try {
    if (!referenceWav) {
      // A calm sample of the described voice becomes the reference for every line.
      const caption = options.voiceCaption ?? heroine?.voiceCaption ?? script.voice;
      const captionKey = createHash("sha256")
        .update(JSON.stringify(["irodori-large", caption, MOTION_VOICE_SAMPLE]))
        .digest("hex");
      const sample = path.join(dir, `voice-reference-${captionKey}.wav`);
      if (!(await exists(sample))) {
        markStage(job, "声の見本を作っています（最初はモデルの読み込みがあります）");
        const result = await synthesizeIrodori({
          text: MOTION_VOICE_SAMPLE,
          model: "large",
          caption,
          device: "cuda",
          lane: 0,
          cudaDevice: voiceDual ? voiceDevices[0] : undefined,
        });
        await fs.writeFile(sample, result.audio);
      }
      referenceWav = sample;
    }
    const referenceKey = createHash("sha256")
      .update(await fs.readFile(referenceWav))
      .digest("hex");
    // Every line speaks with the same reference recording, so the lines do not depend on each other:
    // with two GPUs, two workers take lines side by side. If the second GPU cannot hold the model
    // (the smaller card), its line goes back to the first worker and the second one stops.
    const speakOne = async (index: number, lane: number) => {
      const [name, text] = toSpeak[index]!;
      const audioKey = createHash("sha256")
        .update(JSON.stringify(["irodori-large", referenceKey, text]))
        .digest("hex");
      const file = path.join(dir, `${name}-${audioKey}.wav`);
      if (!(await exists(file))) {
        markStage(job, `声を作っています（${index + 1}/${toSpeak.length}）`);
        const result = await synthesizeIrodori({
          text,
          model: "large",
          referenceWav,
          device: "cuda",
          lane,
          cudaDevice: voiceDual ? voiceDevices[lane] : undefined,
        });
        await fs.writeFile(file, result.audio);
      }
      voiceFiles.set(name, file);
    };
    let nextLine = 0;
    const runLane = async (lane: number): Promise<void> => {
      for (;;) {
        if (job.cancelRequested) throw new Error("中止しました");
        const index = nextLine++;
        if (index >= toSpeak.length) return;
        try {
          await speakOne(index, lane);
        } catch (error) {
          if (lane === 0 || !voiceDual) throw error;
          await speakOne(index, 0);
          return;
        }
      }
    };
    await Promise.all(voiceDual ? [runLane(0), runLane(1)] : [runLane(0)]);
  } finally {
    stopIrodoriWorker();
  }

  const cuts = applyMotionFlow(
    motionCuts(script, minutes, options.motionTemplate, options.motionOptions, options.motionTalk),
    options.motionFlow,
  );
  if (heroine && !job.options.motionRevoiceJobId) {
    const constraints = characterMotionPrompt(readCharacterMotionProfile(heroine.id));
    if (constraints) for (const cut of cuts) cut.prompt = `${cut.prompt}, ${constraints}`;
  }
  const sfxSelected = options.motionSfx ?? DEFAULT_MOTION_SFX;
  const baseSeed = state.baseSeed ?? Number.parseInt(job.id.slice(0, 6), 16) % 100_000;
  const linesOf = (phase?: MotionPhase) =>
    phase
      ? script.lines[phase].map((text, i) => ({ wav: voiceFiles.get(`line-${phase}-${i}`), text }))
      : [];
  const plan = {
    name: script.name,
    out: "motion.mp4",
    cuts: cuts.map((cut) => ({
      clip: `${cut.name}.webm`,
      loop: cut.loop,
      seconds: cut.seconds,
      lines: linesOf(cut.phase),
      ...(cut.breath ? { breath: voiceFiles.get(`breath-${cut.breath}`) } : {}),
      ...(cut.fade ? { fade: cut.fade } : {}),
      ...(cut.flash ? { flash: true } : {}),
      ...(cut.insert ? { insert: cut.insert } : {}),
      ...(() => {
        const sfx = [
          ...(cut.sfx === "fellatio" ? [{ wav: MOTION_FELLATIO_SFX, gain: 0.55 }] : []),
          ...sfxForCut(cut.name, sfxSelected),
        ];
        return sfx.length > 0 ? { sfx } : {};
      })(),
      ...(options.motionTalk ? { breathGain: MOTION_TALK[options.motionTalk].breathGain } : {}),
    })),
  };
  const request = path.join(dir, "job.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(options.motionImageModel),
      reuseCutsOnly: Boolean(job.options.motionRevoiceJobId),
      // The tags of the JSON, and those the user set on each heroine in the app.
      negative: [
        motionImageNegative(),
        ...[heroine, heroine2, heroine3, heroine4].map((h) => h?.spec?.customNegative),
      ]
        .filter(Boolean)
        .join(", "),
      keys: motionKeyJobs(script, baseSeed, options.motionTemplate, options.motionOptions),
      loras: [
        ...(heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : []),
        ...extraLoras,
      ],
      // 多人数シーン: 2〜4人目の look タグ（Pythonスクリプトがキャラごとのプロンプトに使用）
      extraLooks: extraLooks.length > 0 ? extraLooks : undefined,
      // 前段シーン: カスタムステップ（Pythonスクリプトが着衣→本番シーケンスを生成）
      approachSteps: options.motionApproachSteps,
      cuts: cuts.map((cut, i) => ({
        name: cut.name,
        start: cut.start,
        end: cut.end,
        prompt: cut.prompt,
        seed: baseSeed + 10 + i,
      })),
      plan,
      // A draft stops after the key images and shows them on one sheet.
      stopAfterKeys: options.motionDraft === true,
    }),
  );
  await runMotionScript(job, dir, request, options.motionDraft === true);
}

/** Runs motion_comic.py on a job.json; a draft gives the key-image sheet, else the film. */
async function runMotionScript(
  job: MediaJob,
  dir: string,
  request: string,
  draft: boolean,
): Promise<void> {
  let produced = "";
  const devices = generationCudaDevices(job.options.gpuMode === "fast");
  await runTool(WAN2GP_PYTHON, [MOTION_SCRIPT, request], {
    cwd: path.dirname(MOTION_SCRIPT),
    cudaDevice: devices[0],
    env:
      devices.length > 1
        ? { OPENCLAW_DUAL_CUDA_DEVICES: devices.join(";") }
        : { OPENCLAW_DUAL_CUDA_DEVICES: "" },
    timeoutMs: MOTION_TIMEOUT_MS,
    onLine: (line) => {
      if (line.startsWith("STAGE ")) markStage(job, line.slice(6));
      else if (line.startsWith("RESULT ")) produced = line.slice(7).trim();
    },
  });
  if (!produced || !(await exists(produced)))
    throw new Error(draft ? "下書きの絵ができませんでした" : "動画ができませんでした");
  const file = await resultPath(job, draft ? ".png" : ".mp4");
  await fs.copyFile(produced, file);
  job.file = file;
  job.mime = draft ? "image/png" : "video/mp4";
  job.motion = { dir, ...(draft ? { draft: true } : {}) };
}

/** The checkpoint file of the key pictures: the chosen SDXL model, else the standard one. */
export function motionModelFile(model?: ImageModel): string {
  return (model && isSdxlModel(model) ? sdxlSpecOf(model) : SDXL_MODELS.wai).file;
}

/** Takes the cast, voice, look and place of a finished motion comic for the new one. */
async function applyMotionInherit(
  job: MediaJob,
): Promise<{ note: string; script?: MotionScript; dir?: string }> {
  const id = job.options.motionInheritJobId;
  if (!id) return { note: "" };
  const source = await getMediaJob(id);
  const dir = source?.motion?.dir;
  if (!source || !dir) throw new Error("引き継ぐモーションコミックが見つかりません");
  const state = await fs
    .readFile(path.join(dir, "state.json"), "utf-8")
    .then((text) => JSON.parse(text) as MotionState)
    .catch(() => undefined);
  const from = state?.options ?? source.options;
  const mine = job.options as Record<string, unknown>;
  for (const key of [
    "motionCharacterId",
    "motionCharacterId2",
    "motionCharacterId3",
    "motionCharacterId4",
    "motionOutfit",
    "motionOutfit2",
    "motionOutfit3",
    "motionOutfit4",
    "voiceId",
    "voiceCaption",
    "motionImageModel",
  ] as const) {
    const value = (from as Record<string, unknown>)[key];
    if (mine[key] === undefined && value !== undefined) mine[key] = value;
  }
  const script = state?.script;
  const note = script
    ? `\n【前作の続き】前作と同じ登場人物。名前は「${script.name}」、${script.age}歳。声は「${script.voice}」。見た目と場所はそのまま。前作の続きの話として書く。`
    : "";
  return { note, ...(script ? { script } : {}), dir };
}

/** What a motion comic keeps in its folder once the script is written: enough to go on after the sample picture is approved, and to be inherited. */
type MotionState = {
  script: MotionScript;
  extraLoras: Array<{ path: string; scale: number }>;
  extraLooks: string[];
  minutes: MotionMinutes;
  options: MediaOptions;
  baseSeed?: number;
};

/** The first of the key pictures only, shown for approval before voices and video are made. */
async function motionPreviewPhase(job: MediaJob, dir: string, state: MotionState): Promise<void> {
  const { script, extraLoras, extraLooks, options } = state;
  const heroine = options.motionCharacterId
    ? await getCharacter(options.motionCharacterId)
    : undefined;
  const others = await Promise.all(
    [options.motionCharacterId2, options.motionCharacterId3, options.motionCharacterId4].map(
      (id) => (id ? getCharacter(id) : undefined),
    ),
  );
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  markStage(job, "確認用の絵を1枚描いています");
  const baseSeed = state.baseSeed ?? Number.parseInt(job.id.slice(0, 6), 16) % 100_000;
  const request = path.join(dir, "job.json");
  await fs.writeFile(
    request,
    JSON.stringify({
      dir,
      model: motionModelFile(options.motionImageModel),
      negative: [motionImageNegative(), ...[heroine, ...others].map((h) => h?.spec?.customNegative)]
        .filter(Boolean)
        .join(", "),
      keys: motionKeyJobs(script, baseSeed, options.motionTemplate, options.motionOptions),
      loras: [
        ...(heroine?.lora && heroine.loraStatus === "ready" && (await exists(heroine.lora))
          ? [{ path: heroine.lora, scale: 0.8 }]
          : []),
        ...extraLoras,
      ],
      extraLooks: extraLooks.length > 0 ? extraLooks : undefined,
      approachSteps: options.motionApproachSteps,
      cuts: [],
      plan: {},
      previewOnly: true,
      stopAfterKeys: true,
    }),
  );
  await runMotionScript(job, dir, request, true);
  job.motion = { dir, draft: true, preview: true };
}

/** Copy a finished production into this job's workspace before changing its soundtrack. */
async function revoiceMotionJob(job: MediaJob): Promise<void> {
  const source = await getMediaJob(job.options.motionRevoiceJobId ?? "");
  const sourceDir = source?.motion?.dir;
  if (
    !source ||
    source.kind !== "motion" ||
    source.status !== "done" ||
    !source.mime?.startsWith("video/") ||
    !sourceDir
  ) {
    throw new Error("声を変更する完成済みモーションコミックが見つかりません");
  }
  const state = JSON.parse(
    await fs.readFile(path.join(sourceDir, "state.json"), "utf-8"),
  ) as MotionState;
  const previous = JSON.parse(await fs.readFile(path.join(sourceDir, "job.json"), "utf-8")) as {
    keys: Array<{ id: string; seed: number }>;
    cuts: Array<{ name: string }>;
  };
  // Never silently regenerate a missing source cut with a different identity or seed.
  for (const cut of previous.cuts) {
    if (!(await exists(path.join(sourceDir, `${cut.name}.webm`)))) {
      throw new Error(
        "元作品の動画素材が不足しています。元作品を完成させてから声を変更してください",
      );
    }
  }
  const dir = path.join(outDir(), `${job.id}-motion`);
  markStage(job, "映像素材を引き継いでいます（元作品は保存されます）");
  await fs.cp(sourceDir, dir, { recursive: true, errorOnExist: true, force: false });
  state.baseSeed ??= previous.keys.find((key) => key.id === "k1")?.seed;
  if (state.baseSeed === undefined) throw new Error("元作品の生成設定が不足しています");
  state.options = {
    ...state.options,
    voiceId: job.options.voiceId,
    voiceCaption: job.options.voiceCaption,
  };
  await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
  job.manga = source.manga;
  await motionFilmPhase(job, dir, state);
}

/**
 * Goes on from a copy of a draft, keeping the script, voices and approved pictures.
 * With motionRedrawKeys the named key images (and those made from them) are drawn again with new
 * seeds and the result is another draft; otherwise the cuts and the film are made.
 */
async function continueMotionJob(job: MediaJob): Promise<void> {
  const source = await getMediaJob(job.options.motionContinueJobId ?? "");
  const sourceDir = source?.motion?.dir;
  if (
    !source ||
    source.status !== "done" ||
    !sourceDir ||
    !(await exists(path.join(sourceDir, "job.json")))
  )
    throw new Error("続きを作る下書きが見つかりません");
  const dir = path.join(outDir(), `${job.id}-motion`);
  await fs.cp(sourceDir, dir, { recursive: true, errorOnExist: true, force: false });
  const request = path.join(dir, "job.json");
  job.manga = source.manga;
  if (source.motion?.preview && !job.options.motionRedrawKeys?.length) {
    // The sample picture is approved: make voices, cuts and the film in the copied workspace.
    const state = JSON.parse(
      await fs.readFile(path.join(dir, "state.json"), "utf-8"),
    ) as MotionState;
    const previous = JSON.parse(await fs.readFile(request, "utf-8")) as {
      keys: Array<{ id: string; seed: number }>;
    };
    state.baseSeed ??= previous.keys.find((key) => key.id === "k1")?.seed;
    if (state.baseSeed === undefined) throw new Error("下書きの生成設定が不足しています");
    await fs.writeFile(path.join(dir, "state.json"), JSON.stringify(state));
    return motionFilmPhase(job, dir, state);
  }
  const data = JSON.parse(await fs.readFile(request, "utf-8")) as {
    dir: string;
    keys: Array<{ id: string; seed: number; src?: string }>;
    cuts: Array<{ name: string; start: string; end: string }>;
    plan?: {
      cuts: Array<{
        lines?: Array<{ wav?: string }>;
        breath?: string;
        sfx?: Array<{ wav?: string }>;
      }>;
    };
    stopAfterKeys?: boolean;
    previewOnly?: boolean;
  };
  data.dir = dir;
  const localAudio = (file: string | undefined): string | undefined =>
    file && file.startsWith(sourceDir + path.sep)
      ? path.join(dir, path.relative(sourceDir, file))
      : file;
  for (const cut of data.plan?.cuts ?? []) {
    for (const line of cut.lines ?? []) line.wav = localAudio(line.wav);
    cut.breath = localAudio(cut.breath);
    for (const effect of cut.sfx ?? []) effect.wav = localAudio(effect.wav);
  }
  const redraw = new Set<string>();
  for (const key of job.options.motionRedrawKeys ?? []) {
    if (!data.keys.some((k) => k.id === key)) continue;
    redraw.add(key);
  }
  // Follow the actual dependency graph, including indirect derivatives.
  let expanded = true;
  while (expanded) {
    expanded = false;
    for (const key of data.keys) {
      if (key.src && redraw.has(key.src) && !redraw.has(key.id)) {
        redraw.add(key.id);
        expanded = true;
      }
    }
  }
  for (const key of redraw) {
    const entry = data.keys.find((k) => k.id === key);
    if (entry) entry.seed += 977;
    for (const name of [`${key}.png`, `${key}-v.png`, `${key}-mask.png`]) {
      await fs.rm(path.join(dir, name), { force: true });
    }
  }
  // Cuts drawn from a redrawn picture are made again too.
  for (const cut of data.cuts) {
    if (redraw.has(cut.start) || redraw.has(cut.end))
      await fs.rm(path.join(dir, `${cut.name}.webm`), { force: true });
  }
  const draft = redraw.size > 0;
  data.stopAfterKeys = draft;
  await fs.writeFile(request, JSON.stringify(data));
  markStage(job, "GPU を空けています");
  await acquireGpu(job);
  stopImageWorker();
  await runMotionScript(job, dir, request, draft);
  if (data.previewOnly) job.motion = { dir, draft: true, preview: true };
}
