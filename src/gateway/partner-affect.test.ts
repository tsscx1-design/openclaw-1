import { describe, expect, it } from "vitest";
import {
  affectNote,
  extractStateLine,
  mergeScene,
  normalizeAffect,
  sceneForMedia,
} from "./partner-affect.js";

describe("partner scene", () => {
  it("reads the scene fields from the state line and hides the line", () => {
    const { reply, change } = extractStateLine(
      'うん…こっち来て。\n<<state {"arousal":5,"place":"寝室","outfit":"下着姿","pose":""}>>',
    );
    expect(reply).toBe("うん…こっち来て。");
    expect(change?.arousal).toBe(5);
    expect(change?.scene).toEqual({ place: "寝室", outfit: "下着姿", pose: "" });
  });

  it("leaves the scene out when the line has none", () => {
    const { change } = extractStateLine('はい<<state {"mood":"照れ"}>>');
    expect(change?.mood).toBe("shy");
    expect(change?.scene).toBeUndefined();
  });

  it("replaces, keeps and clears fields", () => {
    const scene = mergeScene({ place: "居間", pose: "座っている", updatedAt: 1 }, {}, 5);
    expect(scene).toEqual({ place: "居間", pose: "座っている", updatedAt: 1 });
    const next = mergeScene(scene, { place: "寝室", pose: "", outfit: " パジャマ " }, 9);
    expect(next).toEqual({ place: "寝室", outfit: "パジャマ", updatedAt: 9 });
    expect(mergeScene(next, { place: "", outfit: "" }, 10)).toEqual({});
  });

  it("keeps a saved scene and trims long text", () => {
    const affect = normalizeAffect({
      scene: { place: "浴室", situation: "あ".repeat(500), updatedAt: 3 },
    } as never);
    expect(affect.scene.place).toBe("浴室");
    expect(affect.scene.situation).toHaveLength(200);
    expect(affect.scene.updatedAt).toBe(3);
    expect(normalizeAffect(undefined).scene).toEqual({});
  });

  it("maps the scene onto the media scene state", () => {
    expect(
      sceneForMedia({ place: "寝室", outfit: "下着姿", pose: "寝そべる", situation: "見つめ合う" }),
    ).toEqual({ place: "寝室", wardrobe: "下着姿", pose: "寝そべる", continuity: "見つめ合う" });
    expect(sceneForMedia({})).toBeUndefined();
  });

  it("tells the chat model the scene", () => {
    const affect = normalizeAffect({ scene: { place: "寝室", outfit: "下着姿" } } as never);
    expect(affectNote(affect, "ユイ")).toContain("【いまの場面】場所: 寝室／服装: 下着姿");
    expect(affectNote(normalizeAffect(undefined), "ユイ")).toContain("まだ決まっていない");
  });
});
