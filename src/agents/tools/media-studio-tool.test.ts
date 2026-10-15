import { beforeEach, describe, expect, it, vi } from "vitest";

const studio = vi.hoisted(() => ({
  startMediaJob: vi.fn(),
  estimateMediaJob: vi.fn(),
  getMediaJob: vi.fn(),
  listMediaJobs: vi.fn(),
  deleteMediaJob: vi.fn(),
}));
const voices = vi.hoisted(() => ({
  listVoiceProjects: vi.fn(),
  queueVoiceTraining: vi.fn(),
}));
const proactive = vi.hoisted(() => ({ startVoiceTraining: vi.fn() }));

vi.mock("../../gateway/media-studio.js", () => studio);
vi.mock("../../gateway/voice-studio.js", () => voices);
vi.mock("../../gateway/partner-proactive.js", () => proactive);

const { createMediaStudioTool } = await import("./media-studio-tool.js");

function details(result: unknown): Record<string, unknown> {
  return (result as { details: Record<string, unknown> }).details;
}

beforeEach(() => {
  vi.clearAllMocks();
  studio.estimateMediaJob.mockResolvedValue({ jobSec: 150, waitSec: 0, totalSec: 150, samples: 2 });
});

describe("media_studio tool", () => {
  it("starts a job as a chat job so it waits for the reply and is announced", async () => {
    studio.startMediaJob.mockResolvedValue({
      id: "job1",
      kind: "image",
      status: "queued",
      request: "猫",
      createdAt: Date.now(),
      options: {},
    });
    const result = details(
      await createMediaStudioTool().execute("call1", {
        action: "generate",
        kind: "image",
        prompt: "猫",
        imageModel: "wai",
        fast: true,
        aspect: "portrait",
      }),
    );
    expect(studio.startMediaJob).toHaveBeenCalledWith(
      "image",
      "猫",
      expect.objectContaining({ imageModel: "wai", fast: true, aspect: "portrait" }),
      { chat: {} },
    );
    expect(result.estimate).toBe("完成まで約3分");
    expect((result.started as { jobId: string }).jobId).toBe("job1");
  });

  it("ignores unknown models and engines", async () => {
    await createMediaStudioTool().execute("call2", {
      action: "estimate",
      kind: "video",
      engine: "turbo",
      imageModel: "sd15",
    });
    expect(studio.estimateMediaJob).toHaveBeenCalledWith(
      "video",
      expect.objectContaining({ engine: undefined, imageModel: undefined }),
    );
  });

  it("does not train designed voices and starts recorded ones now", async () => {
    voices.listVoiceProjects.mockResolvedValue([
      { id: "v1", name: "合成", source: "design", status: "ready", files: [] },
      { id: "v2", name: "わたし", status: "uploading", files: [{ name: "a.wav", bytes: 1 }] },
    ]);
    voices.queueVoiceTraining.mockResolvedValue({ id: "v2", name: "わたし", status: "queued" });
    const tool = createMediaStudioTool();
    expect(details(await tool.execute("c3", { action: "train_voice", voiceId: "v1" })).error).toBe(
      "声デザインで作った声は学習しなくてもそのまま使えます",
    );
    expect(voices.queueVoiceTraining).not.toHaveBeenCalled();
    await tool.execute("c4", { action: "train_voice", voiceId: "わたし", now: true });
    expect(voices.queueVoiceTraining).toHaveBeenCalledWith("v2", { now: true });
    expect(proactive.startVoiceTraining).toHaveBeenCalledOnce();
  });

  it("needs a job id to cancel", async () => {
    await expect(createMediaStudioTool().execute("c5", { action: "cancel" })).rejects.toThrow(
      "cancel needs jobId",
    );
  });
});
