// Regression coverage for client-controlled ids and model names reaching the filesystem or a shell.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const execFile = vi.hoisted(() => vi.fn());
const mutateConfigFileWithRetry = vi.hoisted(() => vi.fn(async () => ({})));

vi.mock("node:child_process", async () => {
  const actual = await vi.importActual<typeof import("node:child_process")>("node:child_process");
  return { ...actual, execFile, execSync: vi.fn() };
});
vi.mock("../../config/config.js", () => ({ mutateConfigFileWithRetry }));

type Handler = (opts: { params: unknown; respond: (...args: unknown[]) => void }) => Promise<void>;

async function invoke(handler: Handler | undefined, params: unknown) {
  if (!handler) {
    throw new Error("handler missing");
  }
  const respond = vi.fn();
  await handler({ params, respond });
  return respond;
}

describe("custom gateway handlers: partner id validation", () => {
  let home: string;
  const previousHome = process.env.OPENCLAW_HOME;

  beforeEach(() => {
    home = fs.mkdtempSync(path.join(os.tmpdir(), "oc-partner-ids-"));
    process.env.OPENCLAW_HOME = home;
  });

  afterEach(() => {
    if (previousHome === undefined) {
      delete process.env.OPENCLAW_HOME;
    } else {
      process.env.OPENCLAW_HOME = previousHome;
    }
    fs.rmSync(home, { recursive: true, force: true });
  });

  it("accepts generated partner ids and rejects path-like ids", async () => {
    const { getPartnerDir, isValidPartnerId } = await import("./partners.js");

    expect(getPartnerDir("partner-luna")).toBe(path.join(home, "partners", "partner-luna"));
    expect(isValidPartnerId("partner-1790000000000-ab12")).toBe(true);
    for (const id of ["..", "../outside", "a/b", "a\\b", "", ".hidden", "C:\\x"]) {
      expect(isValidPartnerId(id)).toBe(false);
      expect(() => getPartnerDir(id)).toThrow(/Invalid partner id/);
    }
  });

  it("refuses memory writes that would escape the partners directory", async () => {
    const { memoriesHandlers } = await import("./memories.js");

    const respond = await invoke(memoriesHandlers["memory.update"] as Handler, {
      partnerId: "../escaped",
      target: "long-term",
      content: "pwned",
    });

    expect(respond.mock.calls[0]?.[0]).toBe(false);
    expect(fs.existsSync(path.join(home, "escaped", "MEMORY.md"))).toBe(false);
  });

  it("skips path-like partner ids during mobile sync instead of writing outside", async () => {
    const { syncHandlers } = await import("./sync.js");

    const respond = await invoke(syncHandlers["sync.bidirectional"] as Handler, {
      pendingMessages: [{ partnerId: "../escaped", role: "user", content: "hi", timestamp: 1 }],
      pendingFacts: [{ partnerId: "../escaped", fact: "x", timestamp: 1 }],
    });

    expect(respond.mock.calls[0]?.[0]).toBe(true);
    expect(fs.existsSync(path.join(home, "escaped"))).toBe(false);
  });
});

describe("custom gateway handlers: local model switch", () => {
  beforeEach(() => {
    execFile.mockReset();
    execFile.mockImplementation((...args: unknown[]) => {
      const callback = args.at(-1);
      if (typeof callback === "function") {
        callback(null, "", "");
      }
    });
    mutateConfigFileWithRetry.mockClear();
  });

  it("rejects shell metacharacters in a local model alias before running anything", async () => {
    const { ollamaHandlers } = await import("./ollama.js");

    const respond = await invoke(ollamaHandlers["ollama.model.switch"] as Handler, {
      model: 'bonsai/swift-bonsai" & calc & "',
    });

    expect(respond.mock.calls[0]?.[0]).toBe(false);
    expect(execFile).not.toHaveBeenCalled();
  });

  it("passes a known alias and an allow-listed thinking level as separate arguments", async () => {
    const { ollamaHandlers } = await import("./ollama.js");

    const respond = await invoke(ollamaHandlers["ollama.model.switch"] as Handler, {
      model: "bonsai/swift-bonsai-ptq1",
      thinkingLevel: 'high"; Remove-Item -Recurse C:\\ ;"',
    });

    expect(respond.mock.calls[0]?.[0]).toBe(true);
    expect(execFile).toHaveBeenCalledOnce();
    const [file, args] = execFile.mock.calls[0] as [string, string[]];
    expect(file).toBe("powershell.exe");
    expect(args).toEqual(
      expect.arrayContaining([
        "-ModelFile",
        "Swift-Bonsai-2-PTQ1_0.gguf",
        "-Alias",
        "swift-bonsai-ptq1",
        "-ThinkingLevel",
        "medium",
      ]),
    );
    // Local Bonsai switches never rewrite openclaw.json (that would hot-restart the Gateway).
    expect(mutateConfigFileWithRetry).not.toHaveBeenCalled();
  });
});
