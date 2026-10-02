import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  appendVoiceUpload,
  createVoiceProject,
  isInTrainingWindow,
  listVoiceProjects,
  queueVoiceTraining,
  runVoiceTrainingTick,
} from "./voice-studio.js";

let home = "";

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "openclaw-voice-studio-"));
  vi.stubEnv("OPENCLAW_HOME", home);
});

afterEach(async () => {
  vi.unstubAllEnvs();
  await fs.rm(home, { recursive: true, force: true });
});

describe("voice studio", () => {
  it("requires explicit consent", async () => {
    await expect(createVoiceProject("ルナの声", false)).rejects.toThrow(/Consent/);
  });

  it("assembles chunked uploads and queues training", async () => {
    const voice = await createVoiceProject("ルナの声", true);
    const bytes = Buffer.from("0123456789");
    await appendVoiceUpload({
      id: voice.id,
      fileName: "take1.mp3",
      chunkIndex: 0,
      chunkCount: 2,
      dataBase64: bytes.subarray(0, 6).toString("base64"),
    });
    const after = await appendVoiceUpload({
      id: voice.id,
      fileName: "take1.mp3",
      chunkIndex: 1,
      chunkCount: 2,
      dataBase64: bytes.subarray(6).toString("base64"),
    });
    expect(after.files).toEqual([{ name: "take1.mp3", bytes: 10 }]);
    expect(
      await fs.readFile(path.join(home, "tts", "voices", voice.id, "raw", "take1.mp3"), "utf-8"),
    ).toBe("0123456789");
    await expect(
      appendVoiceUpload({
        id: voice.id,
        fileName: "evil.exe",
        chunkIndex: 0,
        chunkCount: 1,
        dataBase64: "AA==",
      }),
    ).rejects.toThrow();
    expect((await queueVoiceTraining(voice.id)).status).toBe("queued");
    expect((await listVoiceProjects()).map((v) => v.status)).toEqual(["queued"]);
  });

  it("rejects path-like ids", async () => {
    await expect(queueVoiceTraining("../../etc")).rejects.toThrow(/Invalid voice id/);
  });

  it("only trains inside the night window", async () => {
    expect(isInTrainingWindow(2, { windowStartHour: 1, windowEndHour: 6 })).toBe(true);
    expect(isInTrainingWindow(7, { windowStartHour: 1, windowEndHour: 6 })).toBe(false);
    expect(isInTrainingWindow(23, { windowStartHour: 23, windowEndHour: 5 })).toBe(true);
    expect(isInTrainingWindow(3, { windowStartHour: 23, windowEndHour: 5 })).toBe(true);
    expect(isInTrainingWindow(12, { windowStartHour: 23, windowEndHour: 5 })).toBe(false);
    const voice = await createVoiceProject("昼間", true);
    await appendVoiceUpload({
      id: voice.id,
      fileName: "a.wav",
      chunkIndex: 0,
      chunkCount: 1,
      dataBase64: "AA==",
    });
    await queueVoiceTraining(voice.id);
    expect(await runVoiceTrainingTick(new Date("2026-10-02T12:00:00"))).toBeUndefined();
  });
});
