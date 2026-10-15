import { describe, expect, it } from "vitest";
import { readBackupChunk, savePhoneChunk, stamp } from "./backup.js";

describe("backup", () => {
  it("names backups by local date and time", () => {
    expect(stamp(new Date(2026, 9, 8, 21, 30, 45))).toBe("20261008-213045");
  });

  it("refuses names that are not backup names (no paths, no other files)", async () => {
    await expect(
      savePhoneChunk({ name: "../evil.zip", index: 0, total: 1, dataBase64: "AA==" }),
    ).rejects.toThrow();
    await expect(
      savePhoneChunk({
        name: "OpenClaw-phone-20261008-213045.zip",
        index: 2,
        total: 1,
        dataBase64: "AA==",
      }),
    ).rejects.toThrow();
    await expect(readBackupChunk("pc", "blocked-terms.json", 0, 10)).rejects.toThrow();
  });
});
