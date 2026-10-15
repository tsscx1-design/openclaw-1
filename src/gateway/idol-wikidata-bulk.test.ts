import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { idolRosterFile, parseIdolRoster, serializeRosterMember } from "./idol-master.js";
import {
  applyBulk,
  bulkState,
  groupAliases,
  mergeWikidataIntoMember,
  namesGroup,
  personInGroup,
  resetBulk,
  startBulkCheck,
  type BulkLookup,
} from "./idol-wikidata-bulk.js";
import type { WikidataPerson } from "./idol-wikidata.js";

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-bulk-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});

const person = (over: Partial<WikidataPerson> = {}): WikidataPerson => ({
  qid: "Q1",
  name: "岩立沙穂",
  description: "",
  groups: [],
  occupations: [],
  sourceUrl: "https://www.wikidata.org/wiki/Q1",
  ...over,
});
const member = (line: string) => parseIdolRoster(line).members[0]!;

describe("group names", () => {
  it("knows the Wikidata names of a group and falls back to the group's own name", () => {
    expect(groupAliases("Nogizaka46")).toContain("乃木坂46");
    expect(groupAliases("MyGroup")).toEqual(["mygroup"]);
    expect(namesGroup("Japanese singer and AKB48 Team B member", "AKB48")).toBe(true);
    expect(namesGroup("日本の女優", "AKB48")).toBe(false);
  });

  it("puts a person in a group by Wikidata's membership, else by the description", () => {
    expect(personInGroup(person({ groups: ["AKB48 Team B"] }), "AKB48")).toBe(true);
    expect(personInGroup(person({ description: "AKB48のメンバー" }), "AKB48")).toBe(true);
    expect(personInGroup(person({ groups: ["SKE48"], description: "歌手" }), "AKB48")).toBe(false);
  });
});

describe("filling a member", () => {
  it("fills only the empty fields, keeps what is set, counts age from the birthday and records the source", () => {
    const m = member("AKB48 | 岩立沙穂 | idol | 正規メンバー | 誕生日=1994-10-04 | 愛称=さっほー");
    const { member: merged, filled } = mergeWikidataIntoMember(
      m,
      person({
        birthday: "1999-01-01",
        bloodType: "B",
        hometown: "神奈川県",
        heightCm: 156.5,
        xUrl: "https://x.com/a",
      }),
    );
    expect(filled).toEqual(["血液型", "出身地", "身長", "X"]);
    expect(merged).toMatchObject({
      birthday: "1994-10-04",
      bloodType: "B",
      hometown: "神奈川県",
      heightCm: 156.5,
      nickname: "さっほー",
      source: "Wikidata Q1",
    });
  });

  it("fills the age from a birthday that was empty, and joins a second source with the first", () => {
    const m = member("G | A | idol | 出典=48pedia");
    const { member: merged, filled } = mergeWikidataIntoMember(
      m,
      person({ birthday: "1994-10-04" }),
    );
    expect(filled).toEqual(["誕生日"]);
    expect(merged.age).toBeGreaterThanOrEqual(31);
    expect(merged.source).toBe("48pedia / Wikidata Q1");
  });

  it("returns the member unchanged when Wikidata has nothing new", () => {
    const m = member("G | A | idol | 誕生日=1994-10-04");
    const result = mergeWikidataIntoMember(m, person({ birthday: "2001-01-01" }));
    expect(result.filled).toEqual([]);
    expect(result.member).toBe(m);
  });
});

describe("one roster line per member", () => {
  it("writes every set field and reads back to the same member", () => {
    const line =
      "AKB48 | 岩立沙穂 | idol, smile | pop/cool | 誕生日=1994-10-04 | 血液型=B | 身長=156.5 | 出身地=神奈川県 | 期=13期生 | 愛称=さっほー | 事務所=TRUSTAR | 略歴=元チームBキャプテン。 | 写真=https://example.com/a.jpg | X=https://x.com/a | 出典=Wikidata Q1";
    const original = member(line);
    const back = member(serializeRosterMember(original));
    expect(back).toMatchObject({
      group: "AKB48",
      name: "岩立沙穂",
      description: "idol, smile",
      tags: ["pop", "cool"],
      birthday: "1994-10-04",
      bloodType: "B",
      heightCm: 156.5,
      hometown: "神奈川県",
      generation: "13期生",
      nickname: "さっほー",
      agency: "TRUSTAR",
      bio: "元チームBキャプテン。",
      photoUrl: "https://example.com/a.jpg",
      xUrl: "https://x.com/a",
      source: "Wikidata Q1",
    });
    expect(back.id).toBe(original.id);
  });

  it("writes a bare member as a short line and keeps an age that has no birthday", () => {
    expect(serializeRosterMember(member("G | A | idol"))).toBe("G | A");
    expect(serializeRosterMember(member("G | A | idol | pop | 年齢=22"))).toBe(
      "G | A |  | pop | 年齢=22",
    );
  });
});

describe("check, then apply", () => {
  const SEED = [
    "AKB48 | 岩立沙穂 | idol | 正規メンバー | 誕生日=1994-10-04 | 愛称=さっほー",
    "AKB48 | 福岡聖菜 | idol | 正規メンバー",
    "AKB48 | 無名の人 | idol | 研究生",
    "",
  ].join("\n");
  beforeEach(() => {
    resetBulk();
    fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
    fs.writeFileSync(idolRosterFile(), SEED, "utf-8");
  });

  const lookup: BulkLookup = async (m) => {
    if (m.name === "岩立沙穂")
      return person({ qid: "Q10", name: "岩立沙穂", birthday: "1999-01-01", bloodType: "B" });
    if (m.name === "福岡聖菜")
      return person({ qid: "Q20", name: "福岡聖菜", birthday: "2000-02-02", hometown: "兵庫県" });
    return undefined;
  };

  it("writes nothing in the check, then writes exactly what the check kept after one backup", async () => {
    const before = fs.readFileSync(idolRosterFile(), "utf-8");
    startBulkCheck({ lookup });
    await vi.waitFor(() => expect(bulkState().status).toBe("done"));
    expect(bulkState()).toMatchObject({
      total: 3,
      done: 3,
      found: 2,
      withNew: 2,
      unmatched: ["無名の人"],
      errors: 0,
    });
    expect(bulkState().fieldCounts).toEqual({ 血液型: 1, 誕生日: 1, 出身地: 1 });
    expect(fs.readFileSync(idolRosterFile(), "utf-8")).toBe(before);

    const result = await applyBulk();
    expect(result).toMatchObject({ added: 0, updated: 2 });
    expect(result.backup && fs.readFileSync(result.backup, "utf-8")).toBe(before);
    const rows = fs.readFileSync(idolRosterFile(), "utf-8").split("\n");
    expect(rows[0]).toContain("誕生日=1994-10-04"); // the roster's own value stays
    expect(rows[0]).toContain("血液型=B");
    expect(rows[0]).toContain("愛称=さっほー");
    expect(rows[0]).toContain("出典=Wikidata Q10");
    expect(rows[1]).toContain("誕生日=2000-02-02");
    expect(rows[1]).toContain("出身地=兵庫県");
    expect(rows[2]).toBe("AKB48 | 無名の人 | idol | 研究生");
    await expect(applyBulk()).rejects.toThrow();
  });

  it("refuses to apply before a finished check", async () => {
    await expect(applyBulk()).rejects.toThrow();
  });

  it("can be limited to one group, and refuses a group that has nobody", () => {
    expect(() => startBulkCheck({ group: "NoSuchGroup", lookup })).toThrow();
  });

  it("stops after five errors in a row and says why", async () => {
    fs.writeFileSync(
      idolRosterFile(),
      `${Array.from({ length: 7 }, (_, i) => `G | P${i} | idol`).join("\n")}\n`,
      "utf-8",
    );
    startBulkCheck({
      lookup: async () => {
        throw new Error("offline");
      },
    });
    await vi.waitFor(() => expect(bulkState().status).toBe("error"));
    expect(bulkState().errors).toBe(5);
    expect(bulkState().error).toContain("offline");
  });
});
