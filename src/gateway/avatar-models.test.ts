import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { describe, expect, it } from "vitest";
import { analyzeLive2dFolder, glbIsVrm, kindOfFile, safeModelFileName } from "./avatar-models.js";

describe("avatar models", () => {
  it("tells the kind of a file by its name", () => {
    expect(kindOfFile("mao.zip")).toBe("zip");
    expect(kindOfFile("body.VRM")).toBe("vrm");
    expect(kindOfFile("body.glb")).toBe("glb");
    expect(kindOfFile("dance.vrma")).toBe("motion");
    expect(kindOfFile("body.blend")).toBe("3d-source");
    expect(kindOfFile("notes.txt")).toBeUndefined();
  });

  it("keeps file names inside the folder", () => {
    expect(safeModelFileName("../../etc/passwd.zip")).toBe("passwd.zip");
    expect(safeModelFileName(String.raw`C:\a\b\モデル.vrm`)).toBe("モデル.vrm");
  });

  it("finds the VRM extension in a glb header", () => {
    const json = JSON.stringify({
      asset: { version: "2.0" },
      extensionsUsed: ["VRMC_vrm"],
      extensions: { VRMC_vrm: {} },
    });
    const head = Buffer.alloc(20 + json.length);
    head.write("glTF", 0, "ascii");
    head.writeUInt32LE(2, 4);
    head.writeUInt32LE(head.length, 8);
    head.writeUInt32LE(json.length, 12);
    head.write("JSON", 16, "ascii");
    head.write(json, 20, "utf8");
    expect(glbIsVrm(head)).toBe(true);
    head.write(json.replace(/VRMC_vrm/g, "OTHER_ext"), 20, "utf8");
    expect(glbIsVrm(head)).toBe(false);
  });

  it("checks a Live2D folder: one model3.json, its files, the counts", async () => {
    const dir = await fs.mkdtemp(path.join(os.tmpdir(), "live2d-test-"));
    try {
      await fs.writeFile(
        path.join(dir, "a.model3.json"),
        JSON.stringify({
          FileReferences: {
            Moc: "a.moc3",
            Textures: ["t0.png", "t1.png"],
            Motions: { Idle: [{ File: "i.motion3.json" }], Tap: [{}, {}] },
            Expressions: [{}],
          },
        }),
      );
      await fs.writeFile(path.join(dir, "a.moc3"), "MOC3");
      await fs.writeFile(path.join(dir, "t0.png"), "x");
      const result = await analyzeLive2dFolder(dir);
      expect(result.kind).toBe("live2d");
      expect(result.entry).toBe("a.model3.json");
      expect(result.info).toContain("モーション3個");
      expect(result.warnings).toEqual(["テクスチャが足りません：t1.png"]);
      await fs.rm(path.join(dir, "a.model3.json"));
      await expect(analyzeLive2dFolder(dir)).rejects.toThrow("model3.json");
      // a PRO and a FREE model in one pack: the PRO one is used; two plain ones are refused
      await fs.mkdir(path.join(dir, "x_pro", "runtime"), { recursive: true });
      await fs.mkdir(path.join(dir, "x_free", "runtime"), { recursive: true });
      await fs.writeFile(
        path.join(dir, "x_pro", "runtime", "X.model3.json"),
        JSON.stringify({ FileReferences: {} }),
      );
      await fs.writeFile(
        path.join(dir, "x_free", "runtime", "X_free.model3.json"),
        JSON.stringify({ FileReferences: {} }),
      );
      expect((await analyzeLive2dFolder(dir)).entry).toBe("x_pro/runtime/X.model3.json");
      await fs.rename(path.join(dir, "x_pro"), path.join(dir, "x_a"));
      await expect(analyzeLive2dFolder(dir)).rejects.toThrow("2個");
    } finally {
      await fs.rm(dir, { recursive: true, force: true });
    }
  });
});
