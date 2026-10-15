import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installBlockedTerms } from "./blocked-terms-test-support.js";

let restoreBlockedTerms: () => void = () => undefined;
beforeAll(() => {
  restoreBlockedTerms = installBlockedTerms();
});
afterAll(() => {
  restoreBlockedTerms();
});

const studio = vi.hoisted(() => ({
  startMediaJob: vi.fn(),
  estimateMediaJob: vi.fn(),
  estimateFaceSwapJob: vi.fn(),
  mediaStudioStatus: vi.fn(),
  listMediaJobs: vi.fn(),
  deleteMediaJob: vi.fn(),
  adoptLatestSheet: vi.fn(),
  assertAllowedRequest: vi.fn(),
  isSexualRequest: (text: string) => /セックス|エロ|sex/i.test(text),
  writeCharacterLook: vi.fn(),
  writeComicCast: vi.fn(),
}));
const characters = vi.hoisted(() => ({
  cleanSpec: (raw: unknown) => raw,
  cleanOutfits: (raw: unknown) => raw,
  characterOptionIds: (raw: unknown) =>
    typeof raw === "string" ? raw.split(",").filter(Boolean) : [],
  getCharacter: vi.fn(),
  listCharacters: vi.fn(),
  registerCharacter: vi.fn(),
  saveCharacter: vi.fn(),
}));
const comics = vi.hoisted(() => ({
  getComicSeries: vi.fn(),
  listComicSeries: vi.fn(),
  newComicSeries: vi.fn(),
  saveComicSeries: vi.fn(),
}));
const voices = vi.hoisted(() => ({
  listVoiceProjects: vi.fn(),
  queueVoiceTraining: vi.fn(),
}));

vi.mock("./media-studio.js", () => studio);
vi.mock("./voice-studio.js", () => voices);
vi.mock("./comic-characters.js", () => characters);
vi.mock("./comic-series.js", () => comics);

const {
  describeStudioForChat,
  extractStudioAction,
  finishedJobNotice,
  minutesText,
  runStudioActionFromChat,
  STUDIO_CHAT_GUIDE,
} = await import("./studio-assistant.js");

beforeEach(() => {
  vi.clearAllMocks();
  studio.estimateMediaJob.mockResolvedValue({ jobSec: 300, waitSec: 0, totalSec: 300, samples: 1 });
  studio.listMediaJobs.mockResolvedValue([]);
  voices.listVoiceProjects.mockResolvedValue([]);
  characters.listCharacters.mockResolvedValue([]);
  comics.listComicSeries.mockResolvedValue([]);
});

describe("extractStudioAction", () => {
  it("hides the action line and reads its JSON", () => {
    const { reply, action } = extractStudioAction(
      'いいね、作り始めるね！\n<<studio {"action":"generate","kind":"image","prompt":"猫","fast":true,}>>',
    );
    expect(reply).toBe("いいね、作り始めるね！");
    expect(action).toEqual({ action: "generate", kind: "image", prompt: "猫", fast: true });
  });

  it("removes system notices the model copied from the history", () => {
    const { reply, action } = extractStudioAction(
      '作り始めるね！\n\n🎨 画像を作り始めます（完成まで約2分）。作っている間はチャットのAIがお休みします。\n✅ 画像ができました:「猫」\n⏳ いま画像を作っています\n<<studio {"action":"generate","kind":"image","prompt":"猫"}>>',
    );
    expect(reply).toBe("作り始めるね！");
    expect(action).toEqual({ action: "generate", kind: "image", prompt: "猫" });
    // Ordinary lines that merely start with an emoji stay.
    expect(extractStudioAction("🎨 いい色だね！").reply).toBe("🎨 いい色だね！");
  });

  it("drops a broken action line without acting", () => {
    expect(extractStudioAction("了解！\n<<studio {action: generate}>>")).toEqual({
      reply: "了解！",
    });
    expect(extractStudioAction("ふつうの返事")).toEqual({ reply: "ふつうの返事" });
  });
});

describe("runStudioActionFromChat", () => {
  it("starts the job for the partner's chat and reports the estimate", async () => {
    studio.startMediaJob.mockResolvedValue({ id: "j1", kind: "video", status: "queued" });
    const line = await runStudioActionFromChat(
      { action: "generate", kind: "video", prompt: "振り向く", engine: "standard" },
      "partner-crie",
    );
    expect(studio.startMediaJob).toHaveBeenCalledWith(
      "video",
      "振り向く",
      expect.objectContaining({ engine: "standard" }),
      { chat: { partnerId: "partner-crie" } },
    );
    expect(line).toBe(
      "🎬 動画を作り始めます（完成まで約5分・作っている間チャットはお休み）。できたらここでお知らせします。",
    );
  });

  it("turns failures into a visible line instead of throwing", async () => {
    studio.startMediaJob.mockRejectedValue(new Error("順番待ちがいっぱいです"));
    expect(
      await runStudioActionFromChat({ action: "generate", kind: "image", prompt: "猫" }, "p"),
    ).toBe("⚠️ できませんでした: 順番待ちがいっぱいです");
    // An action the chat does not know writes nothing.
    expect(await runStudioActionFromChat({ action: "estimate" }, "p")).toBe("");
  });

  it("tells the state of the PC for a status line", async () => {
    studio.mediaStudioStatus.mockResolvedValue({
      busy: true,
      queued: 2,
      chatPaused: true,
      current: {
        kind: "faceswap",
        stage: "顔を入れ替えています（42%）",
        request: "顔入れ替え",
        elapsedSec: 190,
      },
    });
    const line = await runStudioActionFromChat({ action: "status" }, "p");
    expect(line).toContain("顔入れ替え");
    expect(line).toContain("42%");
    expect(line).toContain("順番待ち2件");
    expect(line).toContain("チャットAIは一時停止中");
  });
});

describe("character and comic actions", () => {
  it("trains a character in one go, drawing the sheet first when there is none", async () => {
    const hina = { id: "chr-1", name: "ヒナ", look: "1girl", profile: "p" };
    characters.listCharacters.mockResolvedValue([hina]);
    characters.getCharacter.mockResolvedValue(undefined);
    studio.adoptLatestSheet.mockResolvedValue(hina);
    studio.startMediaJob.mockResolvedValue({ id: "j9", kind: "comic", status: "queued" });
    const line = await runStudioActionFromChat(
      { action: "character_train", character: "ヒナ" },
      "p",
    );
    expect(studio.startMediaJob).toHaveBeenNthCalledWith(
      1,
      "comic",
      "",
      { comicTask: "sheet", comicCharacterId: "chr-1" },
      { chat: { partnerId: "p" } },
    );
    expect(studio.startMediaJob).toHaveBeenNthCalledWith(
      2,
      "comic",
      "",
      { comicTask: "lora", comicCharacterId: "chr-1" },
      { chat: { partnerId: "p" } },
    );
    expect(line).toContain("「ヒナ」の専用学習を設定画を先に描いてから、始めます");
  });

  it("refuses student settings for characters, adult series and adult jobs", async () => {
    expect(
      await runStudioActionFromChat(
        { action: "character_create", name: "会長", description: "生徒会の会長、20歳" },
        "p",
      ),
    ).toContain("⚠️ できませんでした: 学生や学校の設定のキャラは作れません");
    expect(
      await runStudioActionFromChat(
        { action: "comic_series", title: "生徒会にも", premise: "学園のハーレム", rating: "adult" },
        "p",
      ),
    ).toContain("学生や学校の設定の成人向け作品は作れません");
    expect(
      await runStudioActionFromChat(
        { action: "generate", kind: "motion", prompt: "放課後の教室で" },
        "p",
      ),
    ).toContain("学生や学校の設定の成人向け作品は作れません");
    expect(studio.startMediaJob).not.toHaveBeenCalled();
  });

  it("keeps working when the blocked-terms file is wrong, and does not put the warning in the chat", async () => {
    restoreBlockedTerms();
    restoreBlockedTerms = installBlockedTerms("{ broken");
    try {
      const reply = await runStudioActionFromChat({ action: "cancel" }, "p");
      expect(reply).toContain("止める作品が指定されていませんでした");
      expect(reply).not.toContain("間違い");
    } finally {
      restoreBlockedTerms();
      restoreBlockedTerms = installBlockedTerms();
    }
  });

  it("goes on from a motion draft, or redraws some of its pictures", async () => {
    studio.startMediaJob.mockResolvedValue({ id: "m2", kind: "motion", status: "queued" });
    expect(
      await runStudioActionFromChat({ action: "motion_continue", jobId: "abc123" }, "p"),
    ).toContain("動画を作り始めます");
    expect(studio.startMediaJob).toHaveBeenLastCalledWith(
      "motion",
      "（下書きの続き）",
      { motionContinueJobId: "abc123" },
      { chat: { partnerId: "p" } },
    );
    await runStudioActionFromChat({ action: "motion_redraw", jobId: "abc123", keys: ["k1"] }, "p");
    expect(studio.startMediaJob).toHaveBeenLastCalledWith(
      "motion",
      "（下書きの描き直し）",
      { motionContinueJobId: "abc123", motionRedrawKeys: ["k1"], motionDraft: true },
      { chat: { partnerId: "p" } },
    );
  });

  it("tells the model never to claim work it did not start", () => {
    expect(STUDIO_CHAT_GUIDE).toContain(
      "行を書かずに「始めた」「学習した」「作っている」と言ってはいけない",
    );
    expect(STUDIO_CHAT_GUIDE).toContain('"action":"character_train"');
  });

  it("hides the new notices when the model copies them", () => {
    const { reply } = extractStudioAction("いいね！\n🧠「ヒナ」の専用学習を始めます（約1時間）。");
    expect(reply).toBe("いいね！");
  });
});

describe("describeStudioForChat", () => {
  it("lists running work with what is left, recent results and voices", async () => {
    const now = Date.parse("2026-10-04T10:00:00");
    vi.useFakeTimers({ now });
    try {
      studio.listMediaJobs.mockResolvedValue([
        {
          id: "aaa111",
          kind: "video",
          status: "running",
          request: "髪がなびく",
          stage: "動画を作っています 3/8",
          createdAt: now - 120_000,
          startedAt: now - 120_000,
          options: {},
        },
        {
          id: "bbb222",
          kind: "image",
          status: "done",
          request: "夜の街",
          createdAt: now - 3_600_000,
          finishedAt: now - 1_800_000,
          options: {},
        },
      ]);
      voices.listVoiceProjects.mockResolvedValue([
        { id: "v1", name: "わたし", status: "queued", files: [{}, {}] },
      ]);
      const text = await describeStudioForChat(now);
      expect(text).toContain(
        "- 作成中: 動画「髪がなびく」 id=aaa111（動画を作っています 3/8、あと約3分）",
      );
      expect(text).toContain("- 完成（30分前）: 画像「夜の街」 id=bbb222");
      expect(text).toContain("- 「わたし」 id=v1: 学習待ち、録音2件");
      // The fixed guide and rules live in the cached system prompt, not in the per-turn state.
      expect(text).not.toContain("<<studio");
      expect(STUDIO_CHAT_GUIDE).toContain('<<studio {"action":"cancel"');
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("finishedJobNotice", () => {
  it("tells where the result is, or why it failed", () => {
    const base = { id: "x", kind: "image" as const, request: "猫", createdAt: 0, options: {} };
    expect(finishedJobNotice({ ...base, status: "done", durationSec: 42 }).text).toBe(
      "✅ 画像ができました:「猫」（42秒）。アプリの生成スタジオの保存庫で見られます。",
    );
    expect(finishedJobNotice({ ...base, status: "error", error: "メモリ不足" }).title).toBe(
      "⚠️ 画像の作成に失敗",
    );
    expect(minutesText(61)).toBe("約2分");
  });
});
