import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { installBlockedTerms } from "./blocked-terms-test-support.js";
import {
  hasStudentTerms,
  MOTION_OPTION_IDS,
  motionImageNegative,
  motionVideoNegative,
  MOTION_TEMPLATE_IDS,
  motionOptionNotes,
  motionCuts,
  motionKeyJobs,
  motionSystemPrompt,
  parseMotionScript,
} from "./motion-comic.js";

let restoreBlockedTerms: () => void = () => undefined;
beforeAll(() => {
  restoreBlockedTerms = installBlockedTerms();
});
afterAll(() => {
  restoreBlockedTerms();
});

const reply = JSON.stringify({
  title: "ダウナーな彼女",
  name: "クロエ",
  age: 25,
  voice: "25歳くらいの大人の女性。低めで気だるげな声。",
  look: "black hair, long hair, hime cut, red eyes, large breasts, gothic lolita, black dress, black thighhighs",
  place: "gothic bedroom, on bed, candlelight",
  position: "lying on back, missionary, spread legs",
  finish: "creampie",
  fellatio: true,
  lines: {
    slow: ["…ふぅん。😪 するの？", "ん…😮‍💨 …別に、平気だし。"],
    fast: ["はぁ、はぁ…🌬️ …やだ、声…出る…🥵"],
    climax: ["あっ、あっ…🥵 …だめ、もう…イく…っ！😱"],
    after: ["😮‍💨……ばか。🫶"],
    fellatio: ["…きれいにしてあげる。"],
    end: ["…んく。…苦い。"],
  },
  breaths: { slow: "ん…😮‍💨", fast: "はぁ…🌬️", climax: "あっ、あっ…🥵" },
});

describe("parseMotionScript", () => {
  it("reads the writer's JSON and keeps the lines and tags", () => {
    const script = parseMotionScript(`はい。\n${reply}`);
    expect(script.name).toBe("クロエ");
    expect(script.age).toBe(25);
    expect(script.fellatio).toBe(true);
    expect(script.lines.slow).toHaveLength(2);
    expect(script.look).toContain("gothic lolita");
  });

  it("does not judge the age by its number (the blocked-terms file decides)", () => {
    expect(() => parseMotionScript(reply.replace('"age":25', '"age":"x"'))).toThrow(
      "年齢が不正です",
    );
    expect(
      hasStudentTerms(parseMotionScript(reply.replace("gothic lolita", "school girl")).look),
    ).toBe(true);
  });

  it("strips anything but plain tag text from the English prompts", () => {
    const script = parseMotionScript(reply.replace("black dress", "black dress\\n--steps 99; rm"));
    expect(script.look).not.toMatch(/[;\n]/);
  });
});

describe("hasStudentTerms", () => {
  it("is true when a word of the blocked-terms file is in the text, and false otherwise", () => {
    expect(hasStudentTerms("学生のコスプレ")).toBe(true);
    expect(hasStudentTerms("schoolgirl")).toBe(true);
    expect(hasStudentTerms("25歳のOL、ゴスロリ")).toBe(false);
  });
});

describe("the negative tags", () => {
  it("add the tags of the blocked-terms file to the picture and the video", () => {
    expect(motionImageNegative()).toContain("test-image-neg");
    expect(motionVideoNegative()).toContain("test-video-neg");
    expect(motionImageNegative()).toContain("lowres");
  });
});

describe("motion cuts and key images", () => {
  it("builds loops on one image and changes between two, with fellatio at the end", () => {
    const script = parseMotionScript(reply);
    const cuts = motionCuts(script, 1);
    expect(cuts.map((c) => c.name)).toEqual([
      "c1-slow",
      "c2-fast",
      "c3-build",
      "c4-climax",
      "c5-cum",
      "c5b-pullout",
      "c6-after",
      "c7-fella",
      "c8-mouth",
    ]);
    for (const cut of cuts) expect(cut.loop).toBe(cut.start === cut.end);
    const keys = motionKeyJobs(script, 100);
    expect(keys.map((k) => k.id)).toEqual(["k1", "k2", "k3", "k4", "f1", "f2"]);
    // Every cut starts and ends on a key image that is drawn.
    const ids = new Set(keys.map((k) => k.id));
    for (const cut of cuts) expect(ids.has(cut.start) && ids.has(cut.end)).toBe(true);
    expect(keys.every((k) => k.prompt.includes("adult"))).toBe(true);
  });

  it("makes longer loops for a longer film and drops fellatio when not asked", () => {
    const script = { ...parseMotionScript(reply), fellatio: false };
    const short = motionCuts(script, 1);
    const long = motionCuts(script, 5);
    expect(long).toHaveLength(7);
    const total = (cuts: typeof short) => cuts.reduce((sum, c) => sum + c.seconds, 0);
    expect(total(long)).toBeGreaterThan(total(short) * 3);
    expect(motionKeyJobs(script, 1).map((k) => k.id)).toEqual(["k1", "k2", "k3", "k4"]);
  });

  it("tells the writer the length and the adult rule", () => {
    const prompt = motionSystemPrompt(3);
    expect(prompt).toContain("約3分");
    expect(prompt).toContain("20歳以上");
  });
});

describe("motion templates", () => {
  it("draws every key image and cut in the chosen position", () => {
    const script = parseMotionScript(reply);
    const keys = motionKeyJobs(script, 1, "doggystyle");
    expect(keys[0]?.prompt).toContain("doggystyle, all fours");
    expect(keys[1]?.prompt).toContain("doggystyle");
    const cuts = motionCuts(script, 1, "doggystyle");
    expect(cuts[1]?.prompt).toContain("from behind");
    expect(cuts.find((c) => c.name === "c2-fast")?.insert).toEqual([0.3, 0.3, 0.7, 0.75]);
  });

  it("falls back to the original flow for an unknown template", () => {
    const script = parseMotionScript(reply);
    expect(motionKeyJobs(script, 1, "nope")[0]?.prompt).toContain("missionary");
    expect(MOTION_TEMPLATE_IDS).toEqual(
      expect.arrayContaining([
        "missionary",
        "cowgirl",
        "doggystyle",
        "standing",
        "bath",
        "reverse_cowgirl",
        "mating_press",
        "spooning",
        "lotus",
        "prone",
        "paizuri",
        "suspended_congress",
        "full_nelson",
        "piledriver",
        "amazon",
        "bent_over_desk",
        "knees_to_chest",
        "matsubakuzushi",
        "squatting_cowgirl",
        "standing_missionary",
        "desk_missionary",
        "sixtynine",
      ]),
    );
    // Paizuri is not vaginal sex: its key images and finish say so.
    const paizuri = motionKeyJobs(script, 1, "paizuri");
    expect(paizuri[0]?.prompt).toContain("penis between breasts");
    expect(paizuri[0]?.prompt).not.toContain("vaginal");
    expect(paizuri[2]?.prompt).toContain("cum on breasts");
  });
});

describe("the cum scene", () => {
  it("pulses inside, pulls out slowly and drips, instead of one burst", () => {
    const script = parseMotionScript(reply);
    const cuts = motionCuts(script, 1);
    expect(cuts.find((c) => c.name === "c5-cum")?.prompt).toContain("throbs slowly four times");
    expect(cuts.find((c) => c.name === "c5b-pullout")).toMatchObject({
      start: "k3",
      end: "k4",
      loop: false,
    });
    expect(cuts.find((c) => c.name === "c6-after")?.prompt).toContain("keep slowly dripping");
    expect(motionKeyJobs(script, 1).find((k) => k.id === "k4")?.prompt).toContain(
      "penis pulled out",
    );
  });

  it("does not pull out for an outside finish or oral templates", () => {
    const outside = { ...parseMotionScript(reply), finish: "outside" as const };
    expect(motionCuts(outside, 1).some((c) => c.name === "c5b-pullout")).toBe(false);
    expect(motionCuts(outside, 1).find((c) => c.name === "c5-cum")?.prompt).toContain(
      "four separate spurts",
    );
    const script = parseMotionScript(reply);
    expect(motionKeyJobs(script, 1, "paizuri").some((k) => k.id === "k4")).toBe(false);
  });
});

describe("motion options", () => {
  it("adds option tags to the pictures, motions and the writer's notes", () => {
    const script = parseMotionScript(reply);
    const keys = motionKeyJobs(script, 1, "missionary", ["virgin", "womb_tattoo", "faint"]);
    expect(keys[0]?.prompt).toContain("defloration, blood");
    expect(keys[0]?.prompt).toContain("womb tattoo");
    expect(keys[0]?.prompt).not.toContain("fainted");
    expect(keys[1]?.prompt).toContain("fainted");
    expect(keys.find((k) => k.id === "k4")?.prompt).toContain("blood mixed with cum");
    const cuts = motionCuts(script, 1, "missionary", ["womb_tattoo", "faint"]);
    expect(cuts[0]?.prompt).toContain("womb tattoo on her lower belly pulses");
    expect(cuts.find((c) => c.name === "c4-climax")?.prompt).toContain("faints");
    expect(motionOptionNotes(["virgin", "ball_gag", "nope"])).toContain("処女");
    expect(MOTION_OPTION_IDS.length).toBeGreaterThan(25);
  });
});
