export type MediaQualityMode = "off" | "balanced" | "max";
export type MediaGpuMode = "parallel" | "fast";

export type MediaRepairRegion = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type MediaRepair = {
  sourceJobId: string;
  part?: number;
  region: MediaRepairRegion;
  instruction: string;
  strength: number;
};

export type MediaCharacterLock = {
  characterIds: string[];
  outfit?: string;
  keepFace: boolean;
  keepBody: boolean;
  keepHair: boolean;
  keepOutfit: boolean;
};

export type MediaSceneState = {
  inheritJobId?: string;
  place?: string;
  camera?: string;
  pose?: string;
  wardrobe?: string;
  continuity?: string;
};

export type MediaVoiceTimelineCue = {
  atSec: number;
  kind: "line" | "sfx";
  text?: string;
  sfx?: "thrust" | "spank" | "wet";
  volume: number;
};

export type MediaBranch = {
  parentJobId: string;
  name?: string;
  atSec?: number;
};

export type MediaProductionOptions = {
  gpuMode?: MediaGpuMode;
  repair?: MediaRepair;
  characterLock?: MediaCharacterLock;
  sceneState?: MediaSceneState;
  qualityMode?: MediaQualityMode;
  voiceTimeline?: MediaVoiceTimelineCue[];
  branch?: MediaBranch;
};

const JOB_ID = /^[a-f0-9]{6,32}$/;

function text(value: unknown, max: number): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim().slice(0, max) : undefined;
}

function numberIn(value: unknown, min: number, max: number): number | undefined {
  return typeof value === "number" && Number.isFinite(value)
    ? Math.min(max, Math.max(min, value))
    : undefined;
}

function object(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : undefined;
}

function repairOf(raw: unknown): MediaRepair | undefined {
  const value = object(raw);
  const region = object(value?.region);
  const sourceJobId = text(value?.sourceJobId, 32);
  const instruction = text(value?.instruction, 500);
  if (!value || !region || !sourceJobId || !JOB_ID.test(sourceJobId) || !instruction)
    return undefined;
  const x = numberIn(region.x, 0, 0.98) ?? 0;
  const y = numberIn(region.y, 0, 0.98) ?? 0;
  const width = Math.min(1 - x, numberIn(region.width, 0.02, 1) ?? 1);
  const height = Math.min(1 - y, numberIn(region.height, 0.02, 1) ?? 1);
  return {
    sourceJobId,
    ...(typeof value.part === "number" ? { part: Math.max(0, Math.floor(value.part)) } : {}),
    region: { x, y, width, height },
    instruction,
    strength: numberIn(value.strength, 0.15, 0.95) ?? 0.55,
  };
}

function characterLockOf(raw: unknown): MediaCharacterLock | undefined {
  const value = object(raw);
  if (!value) return undefined;
  const characterIds = Array.isArray(value.characterIds)
    ? value.characterIds
        .filter((id): id is string => typeof id === "string" && /^[A-Za-z0-9_-]{4,60}$/.test(id))
        .slice(0, 4)
    : [];
  if (characterIds.length === 0) return undefined;
  return {
    characterIds,
    ...(text(value.outfit, 80) ? { outfit: text(value.outfit, 80) } : {}),
    keepFace: value.keepFace !== false,
    keepBody: value.keepBody !== false,
    keepHair: value.keepHair !== false,
    keepOutfit: value.keepOutfit === true,
  };
}

function sceneStateOf(raw: unknown): MediaSceneState | undefined {
  const value = object(raw);
  if (!value) return undefined;
  const inheritJobId = text(value.inheritJobId, 32);
  const state: MediaSceneState = {
    ...(inheritJobId && JOB_ID.test(inheritJobId) ? { inheritJobId } : {}),
    ...(text(value.place, 160) ? { place: text(value.place, 160) } : {}),
    ...(text(value.camera, 160) ? { camera: text(value.camera, 160) } : {}),
    ...(text(value.pose, 160) ? { pose: text(value.pose, 160) } : {}),
    ...(text(value.wardrobe, 160) ? { wardrobe: text(value.wardrobe, 160) } : {}),
    ...(text(value.continuity, 400) ? { continuity: text(value.continuity, 400) } : {}),
  };
  return Object.keys(state).length > 0 ? state : undefined;
}

function voiceTimelineOf(raw: unknown): MediaVoiceTimelineCue[] | undefined {
  if (!Array.isArray(raw)) return undefined;
  const cues = raw.slice(0, 80).flatMap((entry): MediaVoiceTimelineCue[] => {
    const value = object(entry);
    if (!value) return [];
    const atSec = numberIn(value.atSec, 0, 60 * 60) ?? 0;
    const volume = numberIn(value.volume, 0.05, 2) ?? 1;
    if (value.kind === "sfx") {
      const sfx = (["thrust", "spank", "wet"] as const).find((id) => id === value.sfx);
      return sfx ? [{ atSec, kind: "sfx", sfx, volume }] : [];
    }
    const cueText = text(value.text, 240);
    return cueText ? [{ atSec, kind: "line", text: cueText, volume }] : [];
  });
  return cues.length > 0 ? cues.toSorted((a, b) => a.atSec - b.atSec) : undefined;
}

function branchOf(raw: unknown): MediaBranch | undefined {
  const value = object(raw);
  const parentJobId = text(value?.parentJobId, 32);
  if (!value || !parentJobId || !JOB_ID.test(parentJobId)) return undefined;
  return {
    parentJobId,
    ...(text(value.name, 80) ? { name: text(value.name, 80) } : {}),
    ...(typeof value.atSec === "number" ? { atSec: numberIn(value.atSec, 0, 60 * 60) ?? 0 } : {}),
  };
}

export function mediaProductionOptionsFromParams(
  params: Record<string, unknown>,
): MediaProductionOptions {
  const qualityMode = (["off", "balanced", "max"] as const).find(
    (mode) => mode === params.qualityMode,
  );
  const gpuMode = (["parallel", "fast"] as const).find((mode) => mode === params.gpuMode);
  return {
    gpuMode,
    repair: repairOf(params.repair),
    characterLock: characterLockOf(params.characterLock),
    sceneState: sceneStateOf(params.sceneState),
    qualityMode,
    voiceTimeline: voiceTimelineOf(params.voiceTimeline),
    branch: branchOf(params.branch),
  };
}
