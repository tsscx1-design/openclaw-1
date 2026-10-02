import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AnyAgentTool } from "./common.js";

const executeSpecialistTask = vi.hoisted(() => vi.fn());

vi.mock("../../gateway/specialist-router.js", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../gateway/specialist-router.js")>();
  return { ...actual, executeSpecialistTask };
});

const { createSpecialistTaskTool } = await import("./specialist-task-tool.js");

function textTool(name: string, text: string): AnyAgentTool {
  return {
    name,
    label: name,
    description: name,
    parameters: {},
    execute: vi.fn(async () => ({ content: [{ type: "text", text }] })),
  } as unknown as AnyAgentTool;
}

describe("delegate_specialist_task", () => {
  beforeEach(() => {
    executeSpecialistTask.mockReset();
    executeSpecialistTask.mockResolvedValue({
      text: "調査結果の要約",
      workerPort: 11435,
      workerKey: "general",
      workerName: "Qwen 2.5 汎用ワーカー",
      workerRole: "汎用要約",
      latencyMs: 1200,
    });
  });

  it("returns immediately and posts delegation and completion notices after the main turn ends", async () => {
    let mainActive = true;
    const calls: Array<{ method: string; params: Record<string, unknown> }> = [];
    const callGateway = vi.fn(async (method: string, params: Record<string, unknown>) => {
      calls.push({ method, params });
      return {};
    });
    const tool = createSpecialistTaskTool({
      agentSessionKey: "agent:main:main",
      agentId: "main",
      sessionId: "session-1",
      callGateway: callGateway as never,
      isMainRunActive: () => mainActive,
    });

    const result = await tool.execute("call-1", { prompt: "長文を要約して" });

    expect(JSON.stringify(result)).toContain("background_started");
    await vi.waitFor(() => expect(executeSpecialistTask).toHaveBeenCalled());
    // Nothing touches the transcript while the main model is still in its turn.
    expect(calls.filter((c) => c.method === "chat.inject")).toHaveLength(0);

    mainActive = false;
    await vi.waitFor(
      () => expect(calls.filter((c) => c.method === "chat.inject")).toHaveLength(2),
      { timeout: 5_000 },
    );
    const injected = calls
      .filter((c) => c.method === "chat.inject")
      .map((c) => String(c.params.message));
    expect(injected[0]).toContain("裏作業員に任せました");
    expect(injected[1]).toContain("裏作業完了報告");
    expect(injected[1]).toContain("調査結果の要約");
  });

  it("lets the worker research with web search and fetch", async () => {
    const webSearchTool = textTool(
      "web_search",
      "1. Result https://example.com/a\n2. Other https://example.com/b",
    );
    const webFetchTool = textTool("web_fetch", "ページ本文");
    const tool = createSpecialistTaskTool({
      agentSessionKey: "agent:main:main",
      callGateway: vi.fn(async () => ({})) as never,
      webSearchTool,
      webFetchTool,
      isMainRunActive: () => false,
    });

    await tool.execute("call-2", { prompt: "最新情報をまとめて", query: "OpenClaw release" });

    await vi.waitFor(() => expect(executeSpecialistTask).toHaveBeenCalled());
    expect(webSearchTool.execute).toHaveBeenCalledWith("call-2-search", {
      query: "OpenClaw release",
      count: 5,
    });
    expect(webFetchTool.execute).toHaveBeenCalledTimes(2);
    const request = executeSpecialistTask.mock.calls[0]?.[0] as {
      taskType: string;
      prompt: string;
    };
    expect(request.taskType).toBe("research");
    expect(request.prompt).toContain("ページ本文");
    expect(request.prompt).toContain("https://example.com/a");
  });

  it("reports a failed background task in the chat", async () => {
    executeSpecialistTask.mockRejectedValueOnce(new Error("worker down"));
    const callGateway = vi.fn(async () => ({}));
    const tool = createSpecialistTaskTool({
      agentSessionKey: "agent:main:main",
      callGateway: callGateway as never,
      isMainRunActive: () => false,
    });

    await tool.execute("call-3", { prompt: "翻訳して", taskType: "translate" });

    await vi.waitFor(() =>
      expect(callGateway).toHaveBeenCalledWith(
        "chat.inject",
        expect.objectContaining({ message: expect.stringContaining("worker down") }),
      ),
    );
  });

  it("returns the worker result inline when background is false", async () => {
    const tool = createSpecialistTaskTool({ callGateway: vi.fn(async () => ({})) as never });

    const result = await tool.execute("call-4", { prompt: "短く分類して", background: false });

    expect(JSON.stringify(result)).toContain("調査結果の要約");
  });
});
