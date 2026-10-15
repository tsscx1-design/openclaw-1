import { describe, expect, it, vi } from "vitest";

const sparqlCalls: string[] = [];
vi.mock("./idol-wikidata.js", () => ({
  searchWikidata: async () => [
    { qid: "Q1", label: "=LOVE", description: "日本の10人組女性アイドルグループ" },
    { qid: "Q2", label: "=LOVE", description: "=LOVEによる2017年のシングル" },
    { qid: "Q3", label: "嵐", description: "日本の男性アイドルグループ" },
    { qid: "Q4", label: "愛", description: "感情" },
    { qid: "Q5", label: "Some Band", description: "Japanese rock band" },
  ],
  wikidataSparql: async (query: string) => {
    sparqlCalls.push(query);
    return {
      results: {
        bindings: [
          { p: { value: "http://www.wikidata.org/entity/Q10" } },
          { p: { value: "http://www.wikidata.org/entity/Q11" } },
          { p: { value: "http://www.wikidata.org/entity/Q10" } },
        ],
      },
    };
  },
}));

import { browseGroups, groupMembers } from "./idol-catalog.js";

describe("groups by name", () => {
  it("keeps the groups and drops songs and unrelated items", async () => {
    const hits = await browseGroups({ query: "love" });
    expect(hits.map((hit) => hit.qid)).toEqual(["Q1", "Q3", "Q5"]);
    expect(hits[0]).toMatchObject({
      name: "=LOVE",
      description: "日本の10人組女性アイドルグループ",
    });
  });
});

describe("members of a group", () => {
  it("lists present members once, and narrows to women or men when asked", async () => {
    expect(await groupMembers("Q1")).toEqual(["Q10", "Q11"]);
    expect(sparqlCalls.at(-1)).toContain("wd:Q1");
    expect(sparqlCalls.at(-1)).toContain("FILTER NOT EXISTS");
    expect(sparqlCalls.at(-1)).not.toContain("P21");
    await groupMembers("Q1", "female");
    expect(sparqlCalls.at(-1)).toContain("wdt:P21 wd:Q6581072");
    await groupMembers("Q1", "male");
    expect(sparqlCalls.at(-1)).toContain("wdt:P21 wd:Q6581097");
    await expect(groupMembers("bad")).rejects.toThrow();
  });
});
