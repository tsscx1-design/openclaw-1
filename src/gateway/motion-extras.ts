import type { MotionCut, MotionPhase } from "./motion-comic.js";

// Extras of the motion comic: how much she talks, what kinds of voice she makes, the effect sounds
// under the cuts, and a flow of cuts the user can order by hand instead of the default one.

// ---- how much she talks -----------------------------------------------------------------------

export const MOTION_TALK_LEVELS = [
  "broken",
  "wordless",
  "wild",
  "many",
  "normal",
  "few",
  "silent",
  "tuna",
] as const;
export type MotionTalk = (typeof MOTION_TALK_LEVELS)[number];

type TalkInfo = {
  label: string;
  /** Lines per phase the writer is asked for (slow / fast / climax-after). */
  lines: { slow: number; fast: number; climax: number };
  /** For the script writer. */
  note: string;
  /** Added to the movement of every loop cut (the picture follows how much she talks). */
  motion: string;
  /** The climax cut gets this too. */
  climaxMotion: string;
  /** How loud the breaths filling the cut are (0..1). */
  breathGain: number;
};

export const MOTION_TALK: Record<MotionTalk, TalkInfo> = {
  broken: {
    label: "ぶっ壊れ",
    lines: { slow: 4, fast: 4, climax: 3 },
    note: "理性が壊れている。言葉は途中で途切れ、同じ言葉を繰り返し、意味のない叫びと笑いと懇願が混ざる。セリフは1つ30字以内で、途切れた言葉・何度も繰り返す単語・濁音の叫びを多く入れる。",
    motion:
      "her face is melted with pleasure, mouth wide open and speaking nonstop, eyes rolled up, drooling, her whole body shaking and twitching uncontrollably",
    climaxMotion: "she screams and laughs, eyes rolled back, tongue out, drooling, convulsing",
    breathGain: 0.85,
  },
  wordless: {
    label: "言葉にならない",
    lines: { slow: 3, fast: 4, climax: 3 },
    note: "ほとんど言葉にならない。セリフは「あっ」「んぅっ」「ひぁっ」「おぉっ」のような声と、単語の断片だけ。ちゃんとした文は書かない。",
    motion: "her mouth hangs open making voiceless sounds, gasping, eyes unfocused, lips trembling",
    climaxMotion: "she arches back with a wordless cry, mouth wide open",
    breathGain: 0.8,
  },
  wild: {
    label: "やばい",
    lines: { slow: 3, fast: 4, climax: 3 },
    note: "感じすぎて余裕がない。短い言葉を立て続けに、声が大きく、「やばい」「だめ」「いっちゃう」などを叫ぶ。",
    motion:
      "she keeps talking and moaning loudly, mouth open, head thrown back, flushed and desperate",
    climaxMotion: "she cries out loudly, back arched, eyes squeezed shut",
    breathGain: 0.8,
  },
  many: {
    label: "沢山",
    lines: { slow: 4, fast: 4, climax: 2 },
    note: "よくしゃべる。実況する、相手に呼びかける、感想を言う、おねだりする、と話し続ける。",
    motion: "she talks and moans continuously, mouth often open, looking at him",
    climaxMotion: "she calls out his name with a loud cry",
    breathGain: 0.7,
  },
  normal: {
    label: "普通",
    lines: { slow: 2, fast: 2, climax: 2 },
    note: "",
    motion: "",
    climaxMotion: "",
    breathGain: 0.7,
  },
  few: {
    label: "少ない",
    lines: { slow: 1, fast: 1, climax: 1 },
    note: "口数が少ない。短い一言だけで、あとは息づかいと喘ぎ。",
    motion: "her lips are slightly parted, breathing softly, speaking rarely",
    climaxMotion: "she gasps with a short cry",
    breathGain: 0.7,
  },
  silent: {
    label: "無口（喘ぎ・我慢声あり）",
    lines: { slow: 1, fast: 1, climax: 1 },
    note: "ほとんど話さない。言葉は出さず、我慢する声・漏れる喘ぎ・息をのむ音が中心。セリフは「…っ」「ん…」「く…」のような、こらえる声にする。",
    motion:
      "she bites her lip and holds her voice back, brows furrowed, lips pressed together, muffled breaths",
    climaxMotion: "she stifles a cry, biting her lip, trembling, a small voice escaping",
    breathGain: 0.75,
  },
  tuna: {
    label: "マグロ",
    lines: { slow: 0, fast: 0, climax: 1 },
    note: "ほぼ無反応で、声も言葉もほとんど出さない。セリフは書かない（絶頂の1つだけ、ごく小さな声）。",
    motion:
      "she lies still and passive, mouth closed, face turned slightly away, barely reacting, calm breathing",
    climaxMotion: "a tiny shudder, her mouth only slightly open",
    breathGain: 0.35,
  },
};

export const motionTalkOf = (id?: string): MotionTalk | undefined =>
  MOTION_TALK_LEVELS.find((value) => value === id);

/** The paragraph added to the script writer's prompt for a talk level. */
export function talkNote(talk?: MotionTalk): string {
  if (!talk || talk === "normal") return "";
  const info = MOTION_TALK[talk];
  const counts = `セリフの数: slow は${info.lines.slow}つ、fast は${info.lines.fast}つ、climax・after は${info.lines.climax}つまで（上の数の指定より、こちらを優先する）。`;
  return `\n【セリフの量: ${info.label}】${info.note}${counts}${info.lines.slow + info.lines.fast === 0 ? "slow と fast の配列は空にする。" : ""}`;
}

// ---- kinds of voice -----------------------------------------------------------------------------

export const MOTION_VOICE_STYLES = [
  "oho",
  "pig",
  "cry",
  "yell",
  "scream",
  "beast",
  "whisper",
  "melt",
  "growl",
] as const;
export type MotionVoiceStyle = (typeof MOTION_VOICE_STYLES)[number];

export const MOTION_VOICE_STYLE_INFO: Record<MotionVoiceStyle, { label: string; note: string }> = {
  oho: {
    label: "おほ声",
    note: "下品に濁った大声の喘ぎ。「おほぉっ♡」「おっ、おぉぉっ」「ほぉぉんっ」のように、低くひっくり返る声を混ぜる。",
  },
  pig: {
    label: "豚声",
    note: "鼻にかかった豚のような鳴き声。「ぶひっ」「ぶひぃぃっ♡」「ぶひゅっ」を混ぜ、はしたなく喜ぶ。",
  },
  cry: {
    label: "泣き声",
    note: "すすり泣き・しゃくりあげ・号泣まじりの声。「ひっく」「うぅっ」「ふえぇっ」を混ぜる。",
  },
  yell: {
    label: "喚き",
    note: "取り乱して喚く。「やだぁっ」「むりむりむりぃっ」「やめ、とめてぇっ」のような大声の連呼。",
  },
  scream: {
    label: "絶叫",
    note: "山場で全力の絶叫。「いやぁぁぁっ」「あぁぁぁぁっ」など伸ばした叫びで、短くせず母音を長く。",
  },
  beast: {
    label: "鳴き声（獣）",
    note: "獣のように鳴く。「にゃあっ」「くぅん」「わふっ」「あぉんっ」など、人ではない鳴き声に寄せる。",
  },
  whisper: { label: "囁き", note: "耳元の小さな囁き。息を多めに、ささやくように。" },
  melt: {
    label: "とろけ声",
    note: "舌が回らずとろける。語尾が伸び、ろれつが回らず、甘くだらしない声。",
  },
  growl: {
    label: "低い唸り",
    note: "歯を食いしばった低い唸り。「ぐっ」「うぅぅっ」「くぅっ」と押し殺した重い声。",
  },
};

export const motionVoiceStylesOf = (raw: unknown): MotionVoiceStyle[] =>
  Array.isArray(raw) ? MOTION_VOICE_STYLES.filter((id) => raw.includes(id)) : [];

export function voiceStyleNote(styles: readonly MotionVoiceStyle[]): string {
  if (styles.length === 0) return "";
  return `\n【声の種類】次の声をセリフと息づかいに混ぜる（場面に合わせて使い分ける）:\n${styles
    .map((id) => `- ${MOTION_VOICE_STYLE_INFO[id].label}: ${MOTION_VOICE_STYLE_INFO[id].note}`)
    .join("\n")}`;
}

// ---- effect sounds ------------------------------------------------------------------------------

export const MOTION_SFX_IDS = ["thrust", "spank", "wet"] as const;
export type MotionSfxId = (typeof MOTION_SFX_IDS)[number];
export const MOTION_SFX_LABELS: Record<MotionSfxId, string> = {
  thrust: "打ち付ける音",
  spank: "はたく音",
  wet: "濡れた音",
};
export const DEFAULT_MOTION_SFX: MotionSfxId[] = ["thrust", "wet"];

const SFX_DIR = "D:\\media-tools\\motion-comic\\sfx\\synth\\";

export const motionSfxOf = (raw: unknown): MotionSfxId[] | undefined =>
  Array.isArray(raw) ? MOTION_SFX_IDS.filter((id) => raw.includes(id)) : undefined;

/** The effect sounds that run under a cut (looped by the composer). */
export function sfxForCut(
  cutName: string,
  selected: readonly MotionSfxId[],
): Array<{ wav: string; gain: number }> {
  const base = cutName.replace(/-\d+$/, "");
  const fx: Array<{ wav: string; gain: number }> = [];
  const on = (id: MotionSfxId) => selected.includes(id);
  if (base === "c1-slow") {
    if (on("thrust")) fx.push({ wav: `${SFX_DIR}thrust_slow.wav`, gain: 0.5 });
    if (on("wet")) fx.push({ wav: `${SFX_DIR}wet_slow.wav`, gain: 0.35 });
  } else if (base === "c2-fast") {
    if (on("thrust")) fx.push({ wav: `${SFX_DIR}thrust_fast.wav`, gain: 0.55 });
    if (on("wet")) fx.push({ wav: `${SFX_DIR}wet_fast.wav`, gain: 0.4 });
    if (on("spank")) fx.push({ wav: `${SFX_DIR}spank.wav`, gain: 0.55 });
  } else if (base === "c4-climax") {
    if (on("thrust")) fx.push({ wav: `${SFX_DIR}thrust_hard.wav`, gain: 0.6 });
    if (on("wet")) fx.push({ wav: `${SFX_DIR}wet_fast.wav`, gain: 0.4 });
    if (on("spank")) fx.push({ wav: `${SFX_DIR}spank_rapid.wav`, gain: 0.6 });
  }
  return fx;
}

// ---- the flow of cuts ---------------------------------------------------------------------------

export const MOTION_FLOW_STEPS = [
  "slow",
  "fast",
  "build",
  "climax",
  "cum",
  "after",
  "fellatio",
  "mouth",
] as const;
export type MotionFlowStep = (typeof MOTION_FLOW_STEPS)[number];
export const MOTION_FLOW_LABELS: Record<MotionFlowStep, string> = {
  slow: "ゆっくり",
  fast: "激しく",
  build: "高まる（切り替え）",
  climax: "絶頂",
  cum: "射精（切り替え）",
  after: "余韻",
  fellatio: "フェラ",
  mouth: "口内射精（切り替え）",
};
/** The flow the film has when none is chosen. */
export const DEFAULT_MOTION_FLOW: MotionFlowStep[] = [
  "slow",
  "fast",
  "build",
  "climax",
  "cum",
  "after",
  "fellatio",
  "mouth",
];

export const motionFlowOf = (raw: unknown): MotionFlowStep[] | undefined => {
  if (!Array.isArray(raw)) return undefined;
  const flow = raw
    .filter((id): id is MotionFlowStep => MOTION_FLOW_STEPS.some((step) => step === id))
    .slice(0, 40);
  return flow.length > 0 ? flow : undefined;
};

const STEP_OF_CUT: Record<string, MotionFlowStep> = {
  "c1-slow": "slow",
  "c2-fast": "fast",
  "c3-build": "build",
  "c4-climax": "climax",
  "c5-cum": "cum",
  "c6-after": "after",
  "c7-fella": "fellatio",
  "c8-mouth": "mouth",
};

/**
 * Orders the default cuts by a flow the user wrote: steps may be left out or repeated. The slow
 * pull-out after a creampie follows its cut. A repeated step is a new cut (new name, no lines).
 */
export function applyMotionFlow(cuts: MotionCut[], flow?: readonly MotionFlowStep[]): MotionCut[] {
  if (!flow || flow.length === 0) return cuts;
  const byStep = new Map<MotionFlowStep, MotionCut[]>();
  for (const cut of cuts) {
    const step = STEP_OF_CUT[cut.name] ?? (cut.name === "c5b-pullout" ? "cum" : undefined);
    if (!step) continue;
    byStep.set(step, [...(byStep.get(step) ?? []), cut]);
  }
  const seen = new Map<MotionFlowStep, number>();
  const out: MotionCut[] = [];
  for (const step of flow) {
    const group = byStep.get(step);
    if (!group) continue;
    const n = (seen.get(step) ?? 0) + 1;
    seen.set(step, n);
    for (const cut of group) {
      if (n === 1) {
        out.push(cut);
      } else {
        const { phase: _phase, ...rest } = cut;
        out.push({ ...rest, name: `${cut.name}-${n}` });
      }
    }
  }
  // A film needs a first and last fade so it does not start or end with a hard cut.
  const first = out[0];
  const last = out.at(-1);
  if (first && !first.fade) first.fade = "in";
  if (last && !last.fade) last.fade = "out";
  return out;
}

/** Phases whose lines a flow plays (a step left out drops its lines from the voice work). */
export function phasesInFlow(flow?: readonly MotionFlowStep[]): Set<MotionPhase> | undefined {
  if (!flow) return undefined;
  const out = new Set<MotionPhase>();
  for (const step of flow) {
    if (
      step === "slow" ||
      step === "fast" ||
      step === "climax" ||
      step === "after" ||
      step === "fellatio"
    )
      out.add(step);
    if (step === "mouth") out.add("end");
  }
  return out;
}

// ---- reading the options from a request ------------------------------------------------------

const JOB_ID = /^[a-f0-9]{6,32}$/;

/** The motion options of the new features, from the parameters of a request (the app or the assistant). */
export function motionExtrasFromParams(params: Record<string, unknown>): {
  motionTalk?: MotionTalk;
  motionVoiceStyles?: MotionVoiceStyle[];
  motionSfx?: MotionSfxId[];
  motionFlow?: MotionFlowStep[];
  motionInheritJobId?: string;
  motionJoinJobIds?: string[];
  motionBridgeSeconds?: number;
  motionJoinTrimSec?: Array<number | null>;
  motionPreview?: boolean;
} {
  const joins = Array.isArray(params.motionJoinJobIds)
    ? params.motionJoinJobIds
        .filter((value): value is string => typeof value === "string" && JOB_ID.test(value))
        .slice(0, 40)
    : undefined;
  const seconds = params.motionBridgeSeconds;
  const styles = motionVoiceStylesOf(params.motionVoiceStyles);
  return {
    motionTalk: motionTalkOf(typeof params.motionTalk === "string" ? params.motionTalk : undefined),
    motionVoiceStyles: styles.length > 0 ? styles : undefined,
    motionSfx: motionSfxOf(params.motionSfx),
    motionFlow: motionFlowOf(params.motionFlow),
    motionInheritJobId:
      typeof params.motionInheritJobId === "string" && JOB_ID.test(params.motionInheritJobId)
        ? params.motionInheritJobId
        : undefined,
    motionJoinJobIds: joins && joins.length >= 2 ? joins : undefined,
    motionBridgeSeconds:
      typeof seconds === "number" && Number.isFinite(seconds)
        ? Math.min(60, Math.max(2, Math.round(seconds)))
        : undefined,
    motionJoinTrimSec: Array.isArray(params.motionJoinTrimSec)
      ? params.motionJoinTrimSec
          .slice(0, 40)
          .map((v) =>
            typeof v === "number" && Number.isFinite(v) && v > 0 ? Math.min(3600, v) : null,
          )
      : undefined,
    motionPreview: typeof params.motionPreview === "boolean" ? params.motionPreview : undefined,
  };
}

// ---- the audio drama --------------------------------------------------------------------------

export const DRAMA_AMBIENCE = ["none", "rain", "night", "room", "bath", "wind"] as const;
export type DramaAmbience = (typeof DRAMA_AMBIENCE)[number];
export type VoiceDrama = { minutes: number; ambience: DramaAmbience };

/** A long whispered story: how many minutes, and the background sound under it. */
export function voiceDramaFromParams(raw: unknown): VoiceDrama | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const value = raw as Record<string, unknown>;
  const minutes =
    typeof value.minutes === "number" && Number.isFinite(value.minutes)
      ? Math.min(30, Math.max(1, Math.round(value.minutes)))
      : 5;
  const ambience = DRAMA_AMBIENCE.find((id) => id === value.ambience) ?? "none";
  return { minutes, ambience };
}
