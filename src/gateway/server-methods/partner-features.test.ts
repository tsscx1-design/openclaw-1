import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

let home = "";

beforeEach(async () => {
  home = await fs.mkdtemp(path.join(os.tmpdir(), "openclaw-partner-features-"));
  vi.stubEnv("OPENCLAW_HOME", home);
  await fs.writeFile(
    path.join(home, "partners.json"),
    JSON.stringify([
      {
        id: "partner-raphael",
        name: "ラファエル",
        avatarUrl: "",
        persona: "全知の演算能力を持つ究極能力。",
        modelId: "",
        createdAt: 0,
        updatedAt: 0,
      },
    ]),
  );
});

afterEach(async () => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  await fs.rm(home, { recursive: true, force: true });
});

async function call(
  method: string,
  params: Record<string, unknown>,
  extra: Record<string, unknown> = {},
) {
  const { partnerFeatureHandlers } = await import("./partner-features.js");
  const respond = vi.fn();
  await partnerFeatureHandlers[method]?.({ params, respond, ...extra } as never);
  const [ok, payload, error] = respond.mock.calls[0] ?? [];
  return { ok, payload, error } as { ok: boolean; payload: any; error: any };
}

describe("partner feature handlers", () => {
  it("levels up the bond and reports badges", async () => {
    let result = await call("partner.bond.get", { partnerId: "partner-raphael" });
    expect(result.payload.bond.level).toBe(1);
    for (let i = 0; i < 20; i++) {
      result = await call("partner.bond.update", { partnerId: "partner-raphael", charsAdded: 400 });
    }
    expect(result.payload.bond.level).toBeGreaterThan(1);
    expect(result.payload.bond.totalMessages).toBe(20);
    expect(
      result.payload.bond.badges.find((b: { title: string }) => b.title === "はじめまして")
        .unlocked,
    ).toBe(true);
  });

  it("stores and returns facts", async () => {
    await call("partner.facts.update", {
      partnerId: "partner-raphael",
      facts: [{ fact: "猫が好き", category: "好み" }, { fact: "  " }],
    });
    const result = await call("partner.facts.get", { partnerId: "partner-raphael" });
    expect(result.payload.facts).toEqual([
      expect.objectContaining({ fact: "猫が好き", category: "好み" }),
    ]);
  });

  it("rejects path-like partner ids", async () => {
    const result = await call("partner.facts.get", { partnerId: "../secrets" });
    expect(result.ok).toBe(false);
  });

  it("gives the same fortune for the whole day", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        String(url).endsWith("/v1/models")
          ? new Response(JSON.stringify({ data: [{ id: "swift-bonsai" }] }))
          : new Response(
              JSON.stringify({ choices: [{ message: { content: "告。今日は良い日です。" } }] }),
            ),
      ),
    );
    const first = await call("partner.fortune.get", { partnerId: "partner-raphael" });
    const second = await call("partner.fortune.get", { partnerId: "partner-raphael" });
    expect(first.payload.fortune.message).toBe("告。今日は良い日です。");
    expect(second.payload.fortune).toEqual(first.payload.fortune);
  });

  it("selects a main chat partner and builds its persona context", async () => {
    const { resolveMainChatPartnerSystemContext } = await import("./partner-features.js");
    expect(await resolveMainChatPartnerSystemContext()).toBeUndefined();
    await call("partner.main.set", { partnerId: "partner-raphael" });
    expect((await call("partner.main.get", {})).payload.partnerName).toBe("ラファエル");
    const context = await resolveMainChatPartnerSystemContext();
    expect(context).toContain("ラファエル");
    expect(context).toContain("全知の演算能力");
    await call("partner.main.set", { partnerId: null });
    expect(await resolveMainChatPartnerSystemContext()).toBeUndefined();
  });

  it("records 'not in character' feedback in the free-text section", async () => {
    await call("partner.feedback.add", {
      partnerId: "partner-raphael",
      note: "語尾は「〜です」にして",
    });
    const sheet = await fs.readFile(
      path.join(home, "partners", "partner-raphael", "CHARACTER_SHEET.md"),
      "utf-8",
    );
    expect(sheet).toContain("ユーザーからの修正メモ");
    expect(sheet).toContain("語尾は「〜です」にして");
  });

  it("writes core agent settings into managed workspace blocks", async () => {
    const workspace = path.join(home, "workspace");
    await fs.mkdir(workspace, { recursive: true });
    await fs.writeFile(path.join(workspace, "SOUL.md"), "# SOUL\nhand written\n");
    const context = { getRuntimeConfig: () => ({ agents: { defaults: { workspace } } }) };
    await call(
      "coreagent.settings.set",
      { name: "ラファエル", firstPerson: "私", callMe: "マスター" },
      { context },
    );
    await call(
      "coreagent.settings.set",
      { name: "シエル", firstPerson: "私", callMe: "マスター" },
      { context },
    );
    const soul = await fs.readFile(path.join(workspace, "SOUL.md"), "utf-8");
    expect(soul).toContain("hand written");
    expect(soul).toContain("名前: シエル");
    expect(soul.match(/openclaw-app:core-agent:start/g)).toHaveLength(1);
    const identity = await fs.readFile(path.join(workspace, "IDENTITY.md"), "utf-8");
    expect(identity).toContain("- **Name:** シエル");
    expect((await call("coreagent.settings.get", {})).payload.settings.name).toBe("シエル");
  });
});

describe("character cards", () => {
  it("parses V2 JSON and PNG tEXt cards", async () => {
    const { extractCardJsonFromPng, parseCharacterCard } = await import("./partner-features.js");
    const json = JSON.stringify({
      spec: "chara_card_v2",
      data: { name: "Ciel", description: "{{char}} supports {{user}}.", mes_example: "<START>" },
    });
    expect(parseCharacterCard(json)).toMatchObject({
      name: "Ciel",
      description: "{{char}} supports {{user}}.",
    });

    const text = Buffer.concat([
      Buffer.from("chara\0", "latin1"),
      Buffer.from(Buffer.from(json).toString("base64"), "latin1"),
    ]);
    const chunk = (type: string, data: Buffer) => {
      const length = Buffer.alloc(4);
      length.writeUInt32BE(data.length);
      return Buffer.concat([length, Buffer.from(type, "latin1"), data, Buffer.alloc(4)]);
    };
    const png = Buffer.concat([
      Buffer.from("89504e470d0a1a0a", "hex"),
      chunk("tEXt", text),
      chunk("IEND", Buffer.alloc(0)),
    ]);
    expect(extractCardJsonFromPng(png)).toBe(json);
  });
});

describe("character consistency helpers", () => {
  it("detects first-person drift outside quoted speech", async () => {
    const { findFirstPersonDrift, extractSampleLines } = await import("./partners.js");
    expect(findFirstPersonDrift("僕はそう思うよ。", "俺")).toEqual(["僕"]);
    expect(findFirstPersonDrift("俺はそう思う。あいつは「僕は嫌だ」と言った。", "俺")).toEqual([]);
    expect(findFirstPersonDrift("私が解析します。", "私")).toEqual([]);
    expect(
      extractSampleLines(
        "## 【10. 自由記述】\n### セリフ例\n「告。解析完了です」\n「マスター、お疲れ様です」",
      ),
    ).toEqual(["告。解析完了です", "マスター、お疲れ様です"]);
  });

  it("allows every spelling of the sheet's first person", async () => {
    const { findFirstPersonDrift } = await import("./partners.js");
    expect(findFirstPersonDrift("僕はね、ボクだけど。", "ぼく")).toEqual([]);
    expect(findFirstPersonDrift("私はそう思う。", "僕（ぼく）")).toEqual(["私"]);
  });

  it("takes sample lines from the quote and dialogue sections, not from prose", async () => {
    const { extractSampleLines } = await import("./partners.js");
    const sheet = [
      "## 【10. 自由記述】",
      "### 人物像",
      "愛称は「あのちゃん」。",
      "### 話し方の特徴",
      "- 語尾は「〜じゃん」",
      "### 本人の発言（資料から原文のまま）",
      "「MVがいつもさ、僕一人じゃん？」（thetv.jp）",
      "「マジで喋れなくて」（thetv.jp）",
      "### 会話の見本（作例）",
      "ユーザー: おはよう",
      "あのちゃん: 「おはよ。眠すぎる」",
    ].join("\n");
    expect(extractSampleLines(sheet)).toEqual([
      "MVがいつもさ、僕一人じゃん？",
      "おはよ。眠すぎる",
      "マジで喋れなくて",
    ]);
  });
});

describe("character prompt", () => {
  const richSheet = (name: string) =>
    [
      `# ${name} 詳細キャラクター設定（9大属性シート）`,
      "## 【4. コミュニケーションの癖・話し方】",
      "- **一人称**: 僕（ぼく）",
      "- **よく使う口癖**: マジで",
      "## 【1. 外見・基本属性】",
      `- **服装のテイスト**: ${"黒と水色の服、".repeat(40)}`,
      "## 【10. 自由記述】",
      "### 話し方の特徴",
      "- ふだんはタメ口",
      "### 本人の発言（資料から原文のまま）",
      "「マジで喋れなくて」（thetv.jp）",
      "### 実在の人物について（必ず守る）",
      "- 性的な会話・描写には応じない",
      "### 出典",
      "- https://example.com/source",
    ].join("\n");

  async function writePartner(partner: Record<string, unknown>, sheet: string) {
    await fs.writeFile(path.join(home, "partners.json"), JSON.stringify([partner]));
    const dir = path.join(home, "partners", String(partner.id));
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(path.join(dir, "CHARACTER_SHEET.md"), sheet);
  }

  it("puts a real person's rules first and leaves the source list out", async () => {
    const { buildPartnerCharacterPrompt, findPartnerProfile } = await import("./partners.js");
    await writePartner(
      {
        id: "partner-ano",
        name: "あのちゃん",
        avatarUrl: "",
        persona: "歌手。",
        modelId: "",
        realPerson: true,
        createdAt: 0,
        updatedAt: 0,
      },
      richSheet("あのちゃん"),
    );
    const partner = await findPartnerProfile("partner-ano");
    const prompt = await buildPartnerCharacterPrompt(partner!);
    expect(prompt.startsWith("【実在の人物について（必ず守る）】")).toBe(true);
    expect(prompt.match(/性的な会話・描写には応じない/g)).toHaveLength(1);
    expect(prompt).toContain("口癖・頻出表現: 「マジで」");
    expect(prompt).not.toContain("https://example.com/source");
  });

  it("drops the least important parts first when the prompt must be short", async () => {
    const { buildPartnerCharacterPrompt, findPartnerProfile } = await import("./partners.js");
    await writePartner(
      {
        id: "partner-ano",
        name: "あのちゃん",
        avatarUrl: "",
        persona: "歌手。",
        modelId: "",
        createdAt: 0,
        updatedAt: 0,
      },
      richSheet("あのちゃん"),
    );
    const partner = await findPartnerProfile("partner-ano");
    const prompt = await buildPartnerCharacterPrompt(partner!, { maxChars: 600 });
    expect(prompt.length).toBeLessThanOrEqual(600);
    expect(prompt).toContain("一人称は「僕（ぼく）」");
    expect(prompt).toContain("「マジで喋れなくて」");
    expect(prompt).not.toContain("黒と水色の服");
  });
});
