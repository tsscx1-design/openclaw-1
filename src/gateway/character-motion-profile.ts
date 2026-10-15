import {
  deleteConfigMachineState,
  writeConfigMachineState,
} from "../state/config-machine-state-write.js";
import { readConfigMachineState } from "../state/config-machine-state.js";

export const MOTION_PROFILE_FIELDS = {
  shoulderScale: [0.6, 1.6],
  hipScale: [0.6, 1.6],
  armScale: [0.7, 1.3],
  legScale: [0.7, 1.3],
  torsoScale: [0.7, 1.3],
  chestMotion: [0, 1],
  chestSoftness: [0, 1],
  chestDamping: [0, 1],
  clothingSupport: [0, 1],
  hairMotion: [0, 1],
  accessoryMotion: [0, 1],
} as const;

export type CharacterMotionProfile = Partial<Record<keyof typeof MOTION_PROFILE_FIELDS, number>>;

function stateKey(id: string): string {
  if (!/^chara-[a-f0-9]{8}$/.test(id)) throw new Error("キャラクターIDが正しくありません");
  return `media.character-motion.${id}`;
}

export function cleanCharacterMotionProfile(raw: unknown): CharacterMotionProfile {
  if (!raw || typeof raw !== "object" || Array.isArray(raw))
    throw new Error("体格・揺れ設定の形式が正しくありません");
  const input = raw as Record<string, unknown>;
  const result: CharacterMotionProfile = {};
  for (const field of Object.keys(MOTION_PROFILE_FIELDS) as Array<
    keyof typeof MOTION_PROFILE_FIELDS
  >) {
    if (
      input[field] === undefined ||
      (typeof input[field] === "string" && input[field].trim() === "")
    )
      continue;
    if (typeof input[field] !== "number" && typeof input[field] !== "string")
      throw new Error(`${field} は数値で指定してください`);
    const value = typeof input[field] === "number" ? input[field] : Number(input[field]);
    const [min, max] = MOTION_PROFILE_FIELDS[field];
    if (!Number.isFinite(value) || value < min || value > max)
      throw new Error(`${field} は ${min}〜${max} の数値で指定してください`);
    result[field] = Math.round(value * 1000) / 1000;
  }
  return result;
}

export function readCharacterMotionProfile(id: string): CharacterMotionProfile {
  const stored = readConfigMachineState<unknown>(stateKey(id));
  return stored === undefined ? {} : cleanCharacterMotionProfile(stored);
}

export function saveCharacterMotionProfile(id: string, profile: CharacterMotionProfile): void {
  if (Object.keys(profile).length === 0) deleteCharacterMotionProfile(id);
  else writeConfigMachineState(stateKey(id), cleanCharacterMotionProfile(profile));
}

export function deleteCharacterMotionProfile(id: string): void {
  deleteConfigMachineState(stateKey(id));
}

/** These constraints guide the video model; they do not claim a physical simulation or a 3D rig. */
export function characterMotionPrompt(profile: CharacterMotionProfile): string {
  const notes: string[] = [];
  const proportions = [
    ["shoulderScale", "shoulder width"],
    ["hipScale", "hip width"],
    ["armScale", "arm length"],
    ["legScale", "leg length"],
    ["torsoScale", "torso length"],
  ] as const;
  for (const [field, label] of proportions) {
    const value = profile[field];
    if (value !== undefined)
      notes.push(
        `${label} ${value.toFixed(2)} times standard, keep proportions consistent throughout`,
      );
  }
  const motion = [
    ["chestMotion", "chest"],
    ["hairMotion", "hair"],
    ["accessoryMotion", "accessories"],
  ] as const;
  for (const [field, label] of motion) {
    const value = profile[field];
    if (value !== undefined)
      notes.push(
        `${label}: ${value === 0 ? "no secondary movement" : value < 0.35 ? "subtle secondary movement" : value < 0.7 ? "moderate secondary movement" : "pronounced secondary movement"}`,
      );
  }
  if (profile.chestSoftness !== undefined)
    notes.push(
      `chest deformation: ${profile.chestSoftness < 0.5 ? "firm, restrained" : "soft, gradual"}`,
    );
  if (profile.chestDamping !== undefined)
    notes.push(`chest movement settles ${profile.chestDamping >= 0.5 ? "quickly" : "gradually"}`);
  if (profile.clothingSupport !== undefined)
    notes.push(
      `clothing support: ${profile.clothingSupport >= 0.5 ? "restrains secondary chest movement" : "allows natural secondary chest movement"}`,
    );
  return notes.join(", ");
}
