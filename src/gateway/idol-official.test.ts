import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { idolRosterFile, parseIdolRoster } from "./idol-master.js";
import {
  applyOfficial,
  mergeOfficialIntoMember,
  officialState,
  parseOfficialList,
  parseOfficialProfile,
  resetOfficial,
  startOfficialCheck,
  type OfficialFetcher,
} from "./idol-official.js";

const LIST_HTML = `
<ul class="memberListsB apBlock">
  <li class="memberList gridMem">
    <a href="/about/members/detail?mid=110" class="clkBtn">
      <figure class="ShapeR2 memberImg"><img src="https://d2r1lkk9i7row.cloudfront.net/mobile/member/83100622.jpg" alt="岩立 沙穂"></figure>
      <div class="memberProf">
        <p class="name clrPink SubTitle fwBold">岩立 沙穂</p>
        <p class="enName enGothic clrPink MTxt">Saho Iwatate</p>
        <figure class="birthDayBlk MTxt"><figcaption class="MTxt fwBold clrPink">1994.10.04</figcaption></figure>
        <p class="MTxt">13期生</p>
      </div>
    </a>
  </li>
  <li class="memberList gridMem">
    <a href="/about/members/detail?mid=152" class="clkBtn">
      <figure class="memberImg"><img src="https://d2r1lkk9i7row.cloudfront.net/mobile/member/83100790.jpg" alt="福岡 聖菜"></figure>
      <div class="memberProf">
        <p class="name clrPink SubTitle fwBold">福岡 聖菜</p>
        <p class="enName enGothic clrPink MTxt">Seina Fukuoka</p>
        <figure class="birthDayBlk MTxt"><figcaption class="MTxt fwBold clrPink">2000.08.01</figcaption></figure>
        <p class="MTxt">15期生</p>
      </div>
    </a>
  </li>
</ul>`;

const PROFILE_HTML = `
<dl class="detailProfTxt bgXlpink ShapeR2">
  <dt class="MTxt fwBold profTtl clrPink">所属事務所</dt><dd class="MTxt profTxt">TRUSTAR</dd>
  <dt class="MTxt fwBold profTtl clrPink">ニックネーム</dt><dd class="MTxt profTxt">さっほー</dd>
  <dt class="MTxt fwBold profTtl clrPink">生年月日</dt><dd class="MTxt profTxt">1994年10月04日</dd>
  <dt class="MTxt fwBold profTtl clrPink">出身地</dt><dd class="MTxt profTxt">神奈川県</dd>
  <dt class="MTxt fwBold profTtl clrPink">ペンライトカラー</dt><dd class="MTxt profTxt">青 &times; 白 &times; 赤</dd>
</dl>`;

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-official-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});
beforeEach(() => {
  resetOfficial();
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(
    idolRosterFile(),
    [
      "AKB48 | 岩立沙穂 | idol",
      "AKB48 | 福岡聖菜 | idol | 愛称=せいな",
      "AKB48 | 名簿だけの人 | idol",
      "SKE48 | 別の人 | idol",
      "",
    ].join("\n"),
    "utf-8",
  );
});

describe("official site pages", () => {
  it("reads the member list: name without the space, English name, birthday, intake, photo and profile id", () => {
    const people = parseOfficialList(LIST_HTML);
    expect(people).toHaveLength(2);
    expect(people[0]).toEqual({
      mid: "110",
      name: "岩立沙穂",
      english: "Saho Iwatate",
      birthday: "1994-10-04",
      generation: "13期",
      photoUrl: "https://d2r1lkk9i7row.cloudfront.net/mobile/member/83100622.jpg",
    });
    expect(people[1]).toMatchObject({
      mid: "152",
      name: "福岡聖菜",
      birthday: "2000-08-01",
      generation: "15期",
    });
  });

  it("reads a profile page: agency, nickname, birthday and hometown", () => {
    expect(parseOfficialProfile(PROFILE_HTML)).toEqual({
      agency: "TRUSTAR",
      nickname: "さっほー",
      birthday: "1994-10-04",
      hometown: "神奈川県",
    });
    expect(parseOfficialProfile("<p>nothing</p>")).toEqual({});
  });

  it("fills only empty fields and keeps what the roster already says", () => {
    const member = parseIdolRoster("AKB48 | 福岡聖菜 | idol | 愛称=せいな").members[0]!;
    const { member: merged, filled } = mergeOfficialIntoMember(
      member,
      {
        mid: "152",
        name: "福岡聖菜",
        nickname: "ほかの愛称",
        birthday: "2000-08-01",
        generation: "15期",
      },
      "公式サイト akb48.co.jp",
    );
    expect(filled).toEqual(["誕生日", "期"]);
    expect(merged.nickname).toBe("せいな");
    expect(merged.birthday).toBe("2000-08-01");
    expect(merged.source).toBe("公式サイト akb48.co.jp");
    expect(merged.age).toBeGreaterThan(20);
    const same = mergeOfficialIntoMember(
      merged,
      { mid: "152", name: "福岡聖菜", birthday: "1999-01-01" },
      "公式サイト akb48.co.jp",
    );
    expect(same.filled).toEqual([]);
    expect(same.member).toBe(merged);
  });
});

async function waitDone() {
  for (let i = 0; i < 200 && officialState().status === "running"; i++)
    await new Promise((resolve) => setTimeout(resolve, 10));
}

describe("official check and apply", () => {
  const fetcher: OfficialFetcher = {
    list: async () => parseOfficialList(LIST_HTML),
    profile: async (_group, mid) => (mid === "110" ? parseOfficialProfile(PROFILE_HTML) : {}),
  };

  it("checks without writing, then writes the filled lines (one backup) when applied", async () => {
    const before = fs.readFileSync(idolRosterFile(), "utf-8");
    startOfficialCheck({ group: "AKB48", fetcher });
    await waitDone();
    const state = officialState();
    expect(state.status).toBe("done");
    expect(state).toMatchObject({ total: 3, found: 2, withNew: 2 });
    expect(state.unmatched).toEqual(["名簿だけの人"]);
    expect(state.fieldCounts["誕生日"]).toBe(2);
    expect(state.fieldCounts["愛称"]).toBe(1);
    expect(fs.readFileSync(idolRosterFile(), "utf-8")).toBe(before); // the check wrote nothing

    const result = await applyOfficial({ savePhotos: false });
    expect(result).toMatchObject({ updated: 2, added: 0, photosSaved: 0 });
    expect(result.backup && fs.existsSync(result.backup)).toBe(true);
    const iwatate = parseIdolRoster(fs.readFileSync(idolRosterFile(), "utf-8")).members.find(
      (m) => m.name === "岩立沙穂",
    )!;
    expect(iwatate).toMatchObject({
      birthday: "1994-10-04",
      generation: "13期",
      nickname: "さっほー",
      hometown: "神奈川県",
      agency: "TRUSTAR",
    });
    // The people the official list does not know are left as they were.
    expect(fs.readFileSync(idolRosterFile(), "utf-8")).toContain("名簿だけの人 | idol");
    await expect(applyOfficial({ savePhotos: false })).rejects.toThrow(); // already applied
  });

  it("refuses a group it cannot read, and an apply before any check", async () => {
    expect(() => startOfficialCheck({ group: "SKE48", fetcher })).toThrow();
    await expect(applyOfficial()).rejects.toThrow();
  });

  it("stops with an error when the official list cannot be read", async () => {
    startOfficialCheck({
      group: "AKB48",
      fetcher: { list: async () => [], profile: async () => ({}) },
    });
    await waitDone();
    expect(officialState().status).toBe("error");
  });
});
