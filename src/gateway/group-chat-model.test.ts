import { describe, expect, it } from "vitest";
import { GROUP_CHAT_MODELS, groupModelCmdline } from "./group-chat-model.js";

describe("groupModelCmdline", () => {
  it("serves the group model on the main port with room left for the clone voices", () => {
    const model = GROUP_CHAT_MODELS[0];
    if (!model) throw new Error("no group chat models");
    const cmd = groupModelCmdline(model, "C:\\llama\\llama-server.exe");
    expect(cmd.startsWith('"C:\\llama\\llama-server.exe" -m "')).toBe(true);
    expect(cmd).toContain("--port 11434");
    expect(cmd).toContain("--fit-target 1800");
    expect(cmd).toContain(`-a groupchat-${model.id}`);
  });

  it("lists the recommended model first", () => {
    expect(GROUP_CHAT_MODELS[0]?.id).toBe("gemma4-26b-a4b-jp");
  });
});
