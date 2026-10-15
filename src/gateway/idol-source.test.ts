import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { idolRosterFile, parseIdolRoster } from "./idol-master.js";
import {
  applySource,
  extractPeopleFromHtml,
  listSources,
  desktopAddress,
  looksLikeBotCheck,
  splitUrls,
  xProfileLink,
  mixchProfileLink,
  isPersonName,
  memberLinksFromHtml,
  personFromProfileHtml,
  parseProfileHtml,
  sameSite,
  removeSource,
  resetSource,
  robotsAllows,
  saveSource,
  saveSources,
  sourceState,
  startSourceCheck,
  type SourceFetcher,
} from "./idol-source.js";

const card = (n: number, name: string, date: string, gen: string) => `
  <li class="member-item">
    <a href="/members/${n}.html">
      <img src="/img/m${n}.jpg" alt="${name}" width="200" height="260">
      <div class="info"><span class="name">${name}</span><span class="date">${date}</span><p>${gen}</p></div>
    </a>
  </li>`;

const LIST = `
<html><body>
  <header><img src="/logo.png" alt="logo"><nav><a href="/">TOP</a><a href="/news">NEWS</a></nav></header>
  <ul class="members">
    ${card(1, "山田 花子", "2000.01.02", "5期生")}
    ${card(2, "佐藤 みさき", "2001年3月4日", "6期生")}
    ${card(3, "鈴木 あかり", "1999/12/31", "5期生")}
    ${card(4, "高橋 りな", "2002-05-06", "7期生")}
  </ul>
  <footer><img src="/banner.png" alt="banner"></footer>
</body></html>`;

describe("reading a member list by its structure", () => {
  it("finds the repeated cards: name, birthday in any form, intake, photo and profile link (absolute)", () => {
    const people = extractPeopleFromHtml(LIST, "https://example.jp/members/");
    expect(people.map((p) => p.name)).toEqual(["山田花子", "佐藤みさき", "鈴木あかり", "高橋りな"]);
    expect(people.map((p) => p.birthday)).toEqual([
      "2000-01-02",
      "2001-03-04",
      "1999-12-31",
      "2002-05-06",
    ]);
    expect(people.map((p) => p.generation)).toEqual(["5期", "6期", "5期", "7期"]);
    expect(people[0]).toMatchObject({
      photoUrl: "https://example.jp/img/m1.jpg",
      profileUrl: "https://example.jp/members/1.html",
    });
  });

  it("does not take the logo or the banner for people, and gives nothing for a page with fewer than three", () => {
    const two = `<body><img src="/a.png"><ul><li><img src="/1.jpg"><span>山田 花子</span></li><li><img src="/2.jpg"><span>佐藤 みさき</span></li></ul></body>`;
    expect(extractPeopleFromHtml(two, "https://example.jp/")).toEqual([]);
    expect(
      extractPeopleFromHtml("<body><p>nothing here</p></body>", "https://example.jp/"),
    ).toEqual([]);
  });

  it("reads lazy-loaded pictures (data-src) and cards that are plain divs", () => {
    const divs = [1, 2, 3, 4]
      .map(
        (n) =>
          `<div class="card"><img data-src="https://cdn.example.jp/p${n}.webp" src="data:image/gif;base64,AAAA"><h3>名前${"一二三四"[n - 1]}さん</h3></div>`,
      )
      .join("");
    const people = extractPeopleFromHtml(`<body>${divs}</body>`, "https://example.jp/");
    expect(people).toHaveLength(4);
    expect(people[1]?.photoUrl).toBe("https://cdn.example.jp/p2.webp");
  });
});

describe("pictures that load late", () => {
  it("takes the background-image of a placeholder picture (dummy.png) instead of the placeholder", () => {
    const li = (n: number, name: string) =>
      `<li><div class="member"><a href="/p/${n}"><figure class="ph"><img src="/static/common/dummy.png" alt="" style="background-image:url(https://img.example.jp/p${n}.jpg)"></figure><p class="name">${name}<span>ROMAJI ${n}</span></p></a></div></li>`;
    const html = `<body><ul>${li(1, "甲斐 心愛")}${li(2, "谷口 愛季")}${li(3, "原 優乃")}</ul></body>`;
    const people = extractPeopleFromHtml(html, "https://example.jp/");
    expect(people.map((p) => p.photoUrl)).toEqual([
      "https://img.example.jp/p1.jpg",
      "https://img.example.jp/p2.jpg",
      "https://img.example.jp/p3.jpg",
    ]);
    expect(people[0]).toMatchObject({ name: "甲斐心愛", profileUrl: "https://example.jp/p/1" });
  });

  it("takes the style of the box around the picture, and gives no photo when there is only a placeholder", () => {
    const html = `<body>${[1, 2, 3].map((n) => `<div class="c"><div class="bg" style="background-image: url('/real/${n}.webp')"><img src="/spacer.gif"></div><h3>名前${"一二三"[n - 1]}</h3></div>`).join("")}</body>`;
    expect(extractPeopleFromHtml(html, "https://example.jp/").map((p) => p.photoUrl)).toEqual([
      "https://example.jp/real/1.webp",
      "https://example.jp/real/2.webp",
      "https://example.jp/real/3.webp",
    ]);
    const none = `<body>${[1, 2, 3].map((n) => `<div class="c"><img src="/dummy.png"><h3>名前${"一二三"[n - 1]}</h3></div>`).join("")}</body>`;
    expect(
      extractPeopleFromHtml(none, "https://example.jp/").every((p) => p.photoUrl === undefined),
    ).toBe(true);
  });
});

describe("profile pages", () => {
  it("reads term/value pairs from definition lists, tables and 'label: value' lines", () => {
    const html = `<body>
      <dl><dt>生年月日</dt><dd>1994年10月04日</dd><dt>出身地</dt><dd>神奈川県</dd><dt>ニックネーム</dt><dd>さっほー</dd></dl>
      <table><tr><th>血液型</th><td>B型</td></tr><tr><th>身長</th><td>156.5cm</td></tr></table>
      <p>所属事務所：TRUSTAR</p></body>`;
    expect(parseProfileHtml(html)).toMatchObject({
      birthday: "1994-10-04",
      hometown: "神奈川県",
      nickname: "さっほー",
      bloodType: "B",
      heightCm: 156.5,
      agency: "TRUSTAR",
    });
    expect(parseProfileHtml("<body>nothing</body>")).toEqual({});
    // <li><span>label</span>value</li> with the intake as a label, and a table with only <td> cells
    const li = `<ul><li><span class="data">ニックネーム</span>ほのの</li><li><span class="data">7期生</span>2015年3月15日加入</li><li><span class="data">生年月日</span>2003年10月22日</li><li><span class="data">血液型</span>O型</li><li><span class="data">出身地</span>愛知県</li><li><span class="data">身長</span>154cm</li></ul>`;
    expect(parseProfileHtml(`<body>${li}</body>`)).toEqual({
      nickname: "ほのの",
      generation: "7期",
      birthday: "2003-10-22",
      bloodType: "O",
      hometown: "愛知県",
      heightCm: 154,
    });
    const tds = `<table><tr><td> 生年月日 </td><td> 2004年8月6日 </td></tr><tr><td>星座</td><td>しし座</td></tr><tr><td>身長</td><td>158.0cm</td></tr><tr><td>出身地</td><td>東京都</td></tr><tr><td>血液型</td><td>A型</td></tr></table>`;
    expect(parseProfileHtml(`<body>${tds}</body>`)).toEqual({
      birthday: "2004-08-06",
      heightCm: 158,
      hometown: "東京都",
      bloodType: "A",
    });
  });
});

describe("what is not read", () => {
  it("follows robots.txt: Disallow, Allow, the longest rule, and a rule for this tool", () => {
    const text = [
      "User-agent: *",
      "Disallow: /private/",
      "Allow: /private/open/",
      "",
      "User-agent: OpenClaw-idol-master",
      "Disallow: /blocked/",
    ].join("\n");
    expect(robotsAllows(text, "/members/")).toBe(true);
    expect(robotsAllows(text, "/blocked/x")).toBe(false);
    expect(
      robotsAllows(
        ["User-agent: *", "Disallow: /private/", "Allow: /private/open/"].join("\n"),
        "/private/open/a",
      ),
    ).toBe(true);
    expect(robotsAllows(["User-agent: *", "Disallow: /private/"].join("\n"), "/private/a")).toBe(
      false,
    );
    expect(robotsAllows("", "/anything")).toBe(true);
    expect(robotsAllows(["User-agent: *", "Disallow: /"].join("\n"), "/members/")).toBe(false);
  });

  it("recognizes the bot check of a protection service", () => {
    expect(looksLikeBotCheck(403, "")).toBe(true);
    expect(looksLikeBotCheck(200, "<title>Just a moment...</title>")).toBe(true);
    expect(looksLikeBotCheck(200, "<title>しばらくお待ちください...</title>")).toBe(true);
    expect(looksLikeBotCheck(200, "<title>メンバー</title>")).toBe(false);
  });
});

let home: string;
let previousHome: string | undefined;
beforeAll(() => {
  previousHome = process.env.OPENCLAW_HOME;
  home = fs.mkdtempSync(path.join(os.tmpdir(), "idol-source-test-"));
  process.env.OPENCLAW_HOME = home;
});
afterAll(() => {
  if (previousHome === undefined) delete process.env.OPENCLAW_HOME;
  else process.env.OPENCLAW_HOME = previousHome;
  fs.rmSync(home, { recursive: true, force: true });
});
beforeEach(() => {
  resetSource();
  fs.mkdirSync(path.dirname(idolRosterFile()), { recursive: true });
  fs.writeFileSync(
    idolRosterFile(),
    [
      "NewGroup | 山田花子 | idol",
      "NewGroup | 佐藤みさき | idol | 愛称=みさ",
      "NewGroup | 名簿だけの人 | idol",
      "",
    ].join("\n"),
    "utf-8",
  );
});

async function waitDone() {
  for (let i = 0; i < 300 && sourceState().status === "running"; i++)
    await new Promise((resolve) => setTimeout(resolve, 10));
}

describe("saved sources", () => {
  it("keeps one address per group, and refuses an address that is not a web page", async () => {
    expect(await listSources()).toEqual([]);
    await saveSource("NewGroup", "https://example.jp/members/");
    await saveSource("NewGroup", "https://example.jp/members2/");
    expect(await listSources()).toEqual([
      { group: "NewGroup", url: "https://example.jp/members2/" },
    ]);
    await expect(saveSource("NewGroup", "not a url")).rejects.toThrow();
    await expect(saveSource("NewGroup", "ftp://example.jp/")).rejects.toThrow();
    await expect(saveSource("NewGroup", "https://user:pass@example.jp/")).rejects.toThrow();
    expect(await removeSource("NewGroup")).toEqual([]);
  });
});

describe("check and apply", () => {
  const fetcher: SourceFetcher = {
    list: async () => LIST.replace(/\/members\//g, "https://example.jp/members/"),
    profile: async () => "<dl><dt>出身地</dt><dd>東京都</dd></dl>",
  };

  it("checks without writing; apply fills the empty fields, adds the missing people only when asked", async () => {
    const before = fs.readFileSync(idolRosterFile(), "utf-8");
    startSourceCheck({ group: "NewGroup", url: "https://example.jp/members/", fetcher });
    await waitDone();
    const state = sourceState();
    expect(state.status).toBe("done");
    expect(state.method).toBe("structure");
    expect(state).toMatchObject({ found: 4, withNew: 2 });
    expect(state.missing).toEqual(["鈴木あかり", "高橋りな"]);
    expect(state.unmatched).toEqual(["名簿だけの人"]);
    expect(state.fieldCounts["誕生日"]).toBe(2);
    expect(state.fieldCounts["出身地"]).toBe(2);
    expect(state.fieldCounts["愛称"]).toBeUndefined(); // nobody gave a nickname
    expect(fs.readFileSync(idolRosterFile(), "utf-8")).toBe(before);

    const result = await applySource({ savePhotos: false, addMissing: true });
    expect(result).toMatchObject({ updated: 2, added: 2, photosSaved: 0 });
    expect(result.backup && fs.existsSync(result.backup)).toBe(true);
    const members = parseIdolRoster(fs.readFileSync(idolRosterFile(), "utf-8")).members;
    expect(members.find((m) => m.name === "山田花子")).toMatchObject({
      birthday: "2000-01-02",
      generation: "5期",
      hometown: "東京都",
    });
    expect(members.find((m) => m.name === "佐藤みさき")).toMatchObject({
      nickname: "みさ",
      birthday: "2001-03-04",
    }); // the nickname stays
    expect(members.find((m) => m.name === "高橋りな")).toMatchObject({
      group: "NewGroup",
      birthday: "2002-05-06",
    });
    expect(members.find((m) => m.name === "名簿だけの人")).toBeDefined();
    await expect(applySource({ savePhotos: false })).rejects.toThrow();
  });

  it("leaves the missing people out when not asked to add them", async () => {
    startSourceCheck({ group: "NewGroup", url: "https://example.jp/members/", fetcher });
    await waitDone();
    const result = await applySource({ savePhotos: false });
    expect(result).toMatchObject({ updated: 2, added: 0 });
    expect(fs.readFileSync(idolRosterFile(), "utf-8")).not.toContain("鈴木あかり");
  });

  it("asks the AI only when the structure gives nobody, and marks the result as read by the AI", async () => {
    const ai: SourceFetcher = {
      list: async () => "<body><p>山田花子 2000.01.02 5期生 佐藤みさき 鈴木あかり</p></body>",
      profile: async () => "",
      ai: async () => [
        { name: "山田花子", birthday: "2000-01-02" },
        { name: "佐藤みさき" },
        { name: "鈴木あかり" },
      ],
    };
    startSourceCheck({ group: "NewGroup", url: "https://example.jp/members/", fetcher: ai });
    await waitDone();
    expect(sourceState()).toMatchObject({ status: "done", method: "ai", found: 3 });
  });

  it("stops with a clear error when the page gives nobody, and when the page is behind a bot check", async () => {
    startSourceCheck({
      group: "NewGroup",
      url: "https://example.jp/x/",
      fetcher: { list: async () => "<body>nothing</body>", profile: async () => "" },
    });
    await waitDone();
    expect(sourceState().status).toBe("error");
    resetSource();
    startSourceCheck({
      group: "NewGroup",
      url: "https://example.jp/x/",
      fetcher: {
        list: async () => {
          throw new Error("ボット対策で守られたページで、読めません（回避は、しません）");
        },
        profile: async () => "",
      },
    });
    await waitDone();
    expect(sourceState()).toMatchObject({ status: "error" });
    expect(sourceState().error).toContain("ボット対策");
  });
});

describe("same site check for profile pages", () => {
  it("treats www / sp / bare hosts of one site as the same, and different sites as different", () => {
    expect(
      sameSite("https://ske48.co.jp/feature/a", "https://www.ske48.co.jp/profile/list.php"),
    ).toBe(true);
    expect(sameSite("https://sp.stu48.com/feature/a", "https://www.stu48.com/")).toBe(true);
    expect(sameSite("https://ngt48.jp/profile/detail/1", "https://ngt48.jp/profile")).toBe(true);
    expect(sameSite("https://evil.example.com/a", "https://www.stu48.com/")).toBe(false);
    expect(sameSite("https://stu48.com.evil.com/a", "https://www.stu48.com/")).toBe(false);
    expect(sameSite("not a url", "https://www.stu48.com/")).toBe(false);
  });
});

describe("starting a check while one is running", () => {
  it("carries on with the running check of the same group, and refuses a different group", () => {
    resetSource();
    const slow: SourceFetcher = {
      list: () => new Promise<string>(() => {}),
      profile: async () => "",
    };
    startSourceCheck({ group: "GroupA", url: "https://example.jp/a/", fetcher: slow });
    expect(
      startSourceCheck({ group: "GroupA", url: "https://example.jp/a/", fetcher: slow }).status,
    ).toBe("running");
    expect(() =>
      startSourceCheck({ group: "GroupB", url: "https://example.jp/b/", fetcher: slow }),
    ).toThrow(/GroupA/);
    resetSource();
  });
});

describe("index pages with one page per member", () => {
  const index = `<body><nav><a href="/feature/profile">PROFILE</a><a href="/feature/about">ABOUT</a><a href="/feature/specialsite_mail_x">mail</a></nav>
    ${["otani_emiri", "oba_hana", "otoshima_risa", "saito_kiara", "sasaki_maika", "takamatsu_hitomi"].map((slug) => `<a href="/feature/${slug}"><img src="/p/${slug}.jpg"></a>`).join("")}
    <a href="https://x.com/someone">x</a></body>`;

  it("finds the run of member links and ignores the menu", () => {
    const links = memberLinksFromHtml(index, "https://equal-love.jp/feature/profile");
    expect(links).toHaveLength(6);
    expect(links[0]).toBe("https://equal-love.jp/feature/otani_emiri");
    expect(links.some((link) => link.includes("about") || link.includes("specialsite"))).toBe(
      false,
    );
    expect(
      memberLinksFromHtml("<body><a href='/feature/a_b'>x</a></body>", "https://equal-love.jp/"),
    ).toEqual([]);
  });

  it("reads a member's name, picture and profile from her own page", () => {
    const page = `<html><head><title>大谷映美里 | =LOVE OFFICIAL SITE</title><meta property="og:image" content="/img/otani.jpg"></head>
      <body><ul><li><span class="data">生年月日</span>1998年12月2日</li><li><span class="data">身長</span>153cm</li></ul></body></html>`;
    expect(personFromProfileHtml(page, "https://equal-love.jp/feature/otani_emiri")).toMatchObject({
      name: "大谷映美里",
      birthday: "1998-12-02",
      heightCm: 153,
      photoUrl: "https://equal-love.jp/img/otani.jpg",
    });
    expect(
      personFromProfileHtml(
        "<html><head><title>PROFILE | =LOVE OFFICIAL SITE</title></head></html>",
        "https://equal-love.jp/x",
      ),
    ).toBeUndefined();
    // a site title in og:title and the name (with a space) in the title
    const spaced = `<html><head><title>
      大谷 映美里 |
      ＝LOVE（イコールラブ） オフィシャルサイト</title><meta property="og:title" content="＝LOVE（イコールラブ） オフィシャルサイト"></head><body><dl><dt>血液型</dt><dd>O型</dd><dt>身長</dt><dd>155cm</dd></dl></body></html>`;
    expect(
      personFromProfileHtml(spaced, "https://equal-love.jp/feature/otani_emiri"),
    ).toMatchObject({ name: "大谷映美里", bloodType: "O", heightCm: 155 });
  });
});

describe("names that are not names, and mobile addresses", () => {
  it("does not take a campaign title or a menu word for a person's name", () => {
    expect(isPersonName("大谷映美里")).toBe(true);
    expect(isPersonName("Hana Oba")).toBe(true);
    expect(isPersonName("メッセージ入り限定トレカ")).toBe(false);
    expect(isPersonName("連動企画「スタンプラリー」")).toBe(false);
    expect(isPersonName("ファンクラブ入会")).toBe(false);
    expect(isPersonName("あ")).toBe(false);
  });

  it("finds the desktop address of a mobile one", () => {
    expect(desktopAddress("https://sp.equal-love.jp/feature/profile_fs")).toBe(
      "https://equal-love.jp/feature/profile_fs",
    );
    expect(desktopAddress("https://m.example.jp/a?b=1")).toBe("https://example.jp/a?b=1");
    expect(desktopAddress("https://equal-love.jp/feature/profile")).toBeUndefined();
  });

  it("gives nobody from a page whose cards are campaign titles", () => {
    const card = (title: string) =>
      `<li><a href="/x/${title}"><img src="/i/${title}.jpg"><p>${title}</p></a></li>`;
    const html = `<ul>${["メッセージ入り限定トレカ", "連動企画「スタンプラリー」", "連動企画「メンバーくじ」", "ランダムトレカプレゼント"].map(card).join("")}</ul>`;
    expect(extractPeopleFromHtml(html, "https://example.jp/")).toEqual([]);
  });
});

describe("links on the list: the member's page and her X account", () => {
  it("finds the account of an X link, not a share link or a link to the home page", () => {
    expect(xProfileLink("https://x.com/yamada_hanako", "https://example.jp/")).toBe(
      "https://x.com/yamada_hanako",
    );
    expect(xProfileLink("https://twitter.com/yamada_hanako?lang=ja", "https://example.jp/")).toBe(
      "https://x.com/yamada_hanako",
    );
    expect(
      xProfileLink("https://x.com/intent/tweet?text=a", "https://example.jp/"),
    ).toBeUndefined();
    expect(xProfileLink("https://x.com/", "https://example.jp/")).toBeUndefined();
    expect(xProfileLink("https://example.jp/a", "https://example.jp/")).toBeUndefined();
    expect(xProfileLink("", "https://example.jp/")).toBeUndefined();
  });

  it("takes the member's own page and her X account from a card that links to both", () => {
    const card = (name: string, handle: string) =>
      `<li><a href="/m/${handle}"><img src="/i/${handle}.jpg"><p class="name">${name}</p></a><a href="https://x.com/${handle}">X</a></li>`;
    const html = `<ul>${[card("山田花子", "hanako_y"), card("佐藤みさき", "misaki_s"), card("鈴木あかり", "akari_s")].join("")}</ul>`;
    const people = extractPeopleFromHtml(html, "https://example.jp/members/");
    expect(people.map((p) => p.xUrl)).toEqual([
      "https://x.com/hanako_y",
      "https://x.com/misaki_s",
      "https://x.com/akari_s",
    ]);
    expect(people[0]!.profileUrl).toBe("https://example.jp/m/hanako_y");
  });

  it("reads the X link of a member's own page", () => {
    expect(
      parseProfileHtml(
        '<body><a href="https://x.com/hanako_y">X</a><dl><dt>出身地</dt><dd>東京都</dd></dl></body>',
      ),
    ).toMatchObject({ xUrl: "https://x.com/hanako_y", hometown: "東京都" });
  });
});

describe("links to mixch.tv", () => {
  it("finds a page of mixch.tv, not its top page or another site", () => {
    expect(mixchProfileLink("https://mixch.tv/u/12345?ref=a", "https://example.jp/")).toBe(
      "https://mixch.tv/u/12345",
    );
    expect(mixchProfileLink("https://www.mixch.tv/u/12345/", "https://example.jp/")).toBe(
      "https://mixch.tv/u/12345",
    );
    expect(mixchProfileLink("https://mixch.tv/", "https://example.jp/")).toBeUndefined();
    expect(mixchProfileLink("https://example.jp/u/1", "https://example.jp/")).toBeUndefined();
    expect(mixchProfileLink("https://notmixch.tv/u/1", "https://example.jp/")).toBeUndefined();
  });

  it("takes the MIXCH link from a card and from a member's own page", () => {
    const card = (name: string, id: string) =>
      `<li><a href="/m/${id}"><img src="/i/${id}.jpg"><p class="name">${name}</p></a><a href="https://mixch.tv/u/${id}">MIXCH</a></li>`;
    const html = `<ul>${[card("山田花子", "1"), card("佐藤みさき", "2"), card("鈴木あかり", "3")].join("")}</ul>`;
    const people = extractPeopleFromHtml(html, "https://example.jp/members/");
    expect(people.map((p) => p.mixchUrl)).toEqual([
      "https://mixch.tv/u/1",
      "https://mixch.tv/u/2",
      "https://mixch.tv/u/3",
    ]);
    expect(people[0]!.profileUrl).toBe("https://example.jp/m/1");
    expect(parseProfileHtml('<body><a href="https://mixch.tv/u/9">MIXCH</a></body>')).toMatchObject(
      { mixchUrl: "https://mixch.tv/u/9" },
    );
  });
});

describe("several addresses for one group", () => {
  it("splits addresses written one under another, with spaces or commas, once each", () => {
    expect(splitUrls("https://a.jp/x\nhttps://a.jp/y")).toEqual([
      "https://a.jp/x",
      "https://a.jp/y",
    ]);
    expect(splitUrls("  https://a.jp/x  https://a.jp/x,https://b.jp/ ")).toEqual([
      "https://a.jp/x",
      "https://b.jp/",
    ]);
    expect(splitUrls("")).toEqual([]);
  });

  it("keeps every address of a group", async () => {
    await saveSources("MultiGroup", ["https://example.jp/a/", "https://example.jp/b/"]);
    expect((await listSources()).filter((s) => s.group === "MultiGroup").map((s) => s.url)).toEqual(
      ["https://example.jp/a/", "https://example.jp/b/"],
    );
    await saveSources("MultiGroup", ["https://example.jp/c/"]);
    expect((await listSources()).filter((s) => s.group === "MultiGroup").map((s) => s.url)).toEqual(
      ["https://example.jp/c/"],
    );
    await expect(saveSources("MultiGroup", [])).rejects.toThrow();
    await removeSource("MultiGroup");
  });
});
