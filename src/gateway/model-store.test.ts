import { describe, expect, it } from "vitest";
import { STORE_MODELS } from "./model-store-data.js";
import {
  isStoreModelId,
  listStore,
  startStoreDownload,
  storeEntry,
  storeSpecOf,
} from "./model-store.js";

describe("model-store", () => {
  it("has unique ids that are accepted as image model ids", () => {
    const ids = STORE_MODELS.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids.every((id) => isStoreModelId(id))).toBe(true);
    expect(isStoreModelId("wai")).toBe(false);
  });

  it("offers only SDXL-type checkpoints for download and lists the rest as information", async () => {
    const { models } = await listStore();
    expect(models.length).toBe(STORE_MODELS.length);
    for (const row of models) {
      if (!row.usable) expect(row.state).toBe("info");
    }
  });

  it("gives no settings for a model that is not on the disk, and refuses to download an unsupported one", () => {
    const usable = STORE_MODELS.find((entry) => entry.usable)!;
    expect(storeSpecOf(usable.id)).toBeUndefined();
    const other = STORE_MODELS.find((entry) => !entry.usable)!;
    expect(() => startStoreDownload(other.id)).toThrow();
    expect(storeEntry("st_nope")).toBeUndefined();
  });
});
