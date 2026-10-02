import { describe, expect, it, vi } from "vitest";
import { parseJsonObject } from "../../gateway/llm-json.js";
import {
  buildPartnerSheetMarkdown,
  parsePartnerSheet,
  PARTNER_SHEET_CATEGORIES,
} from "../../gateway/partner-sheet-schema.js";
import type { AnyAgentTool } from "./common.js";
import { createPartnerCreateTool } from "./partner-create-tool.js";

function textTool(text: string): AnyAgentTool {
  return {
    name: "t",
    label: "t",
    description: "t",
    parameters: {},
    execute: vi.fn(async () => ({ content: [{ type: "text", text }] })),
  } as unknown as AnyAgentTool;
}

describe("partner sheet schema", () => {
  it("round-trips fixed fields and keeps free text", () => {
    const markdown = buildPartnerSheetMarkdown({
      name: "ラファエル",
      values: { 一人称: "私", 語尾の癖: "〜です", 独自項目: "解析鑑定" },
      freeText: "### セリフ例\n「告。解析が完了しました」",
    });
    const parsed = parsePartnerSheet(markdown);
    expect(parsed.attributes.get("一人称")).toBe("私");
    expect(parsed.attributes.get("独自項目")).toBe("解析鑑定");
    expect(parsed.attributes.has("種族")).toBe(false);
    expect(parsed.freeText).toContain("告。解析が完了しました");
    expect(parsed.freeText).toContain("### セリフ例");
    expect(parsed.freeText).not.toContain("詳細キャラクター設定");
  });

  it("covers the Android editor's 100 fields", () => {
    expect(PARTNER_SHEET_CATEGORIES.flatMap((c) => c.items)).toHaveLength(100);
  });
});

describe("parseJsonObject", () => {
  it("reads fenced JSON and salvages almost-JSON", () => {
    expect(parseJsonObject('はい\n```json\n{"一人称": "俺"}\n```')).toEqual({ 一人称: "俺" });
    expect(parseJsonObject('{"一人称": "俺", "語尾の癖": "だぜ",}')).toEqual({
      一人称: "俺",
      語尾の癖: "だぜ",
    });
  });
});

describe("partner_create_from_character", () => {
  it("researches, fills every category and saves the sheet with free text", async () => {
    const complete = vi.fn(async ({ prompt }: { prompt: string }) => {
      if (prompt.includes('- "一人称"')) {
        return '{"一人称": "私"}';
      }
      if (prompt.includes('- "ユーザーの呼び方"')) {
        return '```json\n{"ユーザーの呼び方": "マスター"}\n```';
      }
      if (prompt.includes("### 概要")) {
        return "### 概要\n全知の演算能力を持つ究極能力。\n### セリフ例\n「告。」";
      }
      return "{}";
    });
    const save = vi.fn(
      async (params: { name: string; persona: string; characterSheet: string }) => ({
        id: "partner-x",
        name: params.name,
        avatarUrl: "",
        persona: params.persona,
        modelId: "",
        createdAt: 0,
        updatedAt: 0,
      }),
    );
    const callGateway = vi.fn(async () => ({}));
    const tool = createPartnerCreateTool({
      agentSessionKey: "agent:main:main",
      callGateway: callGateway as never,
      webSearchTool: textTool("1. https://example.com/raphael"),
      webFetchTool: textTool("ラファエルは大賢者が進化した究極能力。一人称は私。".repeat(20)),
      isMainRunActive: () => false,
      complete,
      save: save as never,
    });

    const result = await tool.execute("call-1", {
      name: "ラファエル",
      source: "転生したらスライムだった件",
    });

    expect(JSON.stringify(result)).toContain("background_started");
    await vi.waitFor(() => expect(save).toHaveBeenCalled(), { timeout: 5_000 });
    const saved = save.mock.calls[0]?.[0];
    const sheet = parsePartnerSheet(saved?.characterSheet ?? "");
    expect(sheet.attributes.get("一人称")).toBe("私");
    expect(sheet.attributes.get("ユーザーの呼び方")).toBe("マスター");
    expect(sheet.freeText).toContain("「告。」");
    expect(saved?.persona).toContain("全知の演算能力");
    // One digest per source, one call per category, one free-text call.
    expect(complete.mock.calls.length).toBeGreaterThanOrEqual(PARTNER_SHEET_CATEGORIES.length + 1);
    await vi.waitFor(
      () =>
        expect(callGateway).toHaveBeenCalledWith(
          "chat.inject",
          expect.objectContaining({ message: expect.stringContaining("作成しました") }),
        ),
      { timeout: 5_000 },
    );
  });
});
