import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { latestTrainingState, trainingDatasetCount } from "./media-studio.js";

let root: string;
beforeAll(() => {
  root = fs.mkdtempSync(path.join(os.tmpdir(), "lora-resume-test-"));
});
afterAll(() => {
  fs.rmSync(root, { recursive: true, force: true });
});

describe("going on with a training that was cut off", () => {
  it("counts only the pictures that have their caption", async () => {
    const dir = path.join(root, "img");
    fs.mkdirSync(dir, { recursive: true });
    for (const n of ["00", "01", "02"]) {
      fs.writeFileSync(path.join(dir, `${n}.png`), "x");
      fs.writeFileSync(path.join(dir, `${n}.txt`), "caption");
    }
    fs.writeFileSync(path.join(dir, "03.png"), "no caption");
    expect(await trainingDatasetCount(dir)).toBe(3);
    expect(await trainingDatasetCount(path.join(root, "missing"))).toBe(0);
  });

  it("finds the last saved state of the run with the same name, and none for another run", async () => {
    const dir = path.join(root, "out");
    fs.mkdirSync(dir, { recursive: true });
    fs.mkdirSync(path.join(dir, "ocx-aa11-000002-state"));
    fs.mkdirSync(path.join(dir, "ocx-aa11-000004-state"));
    fs.mkdirSync(path.join(dir, "ocx-bb22-000005-state"));
    fs.writeFileSync(path.join(dir, "ocx-aa11-000004.safetensors"), "x");
    expect(await latestTrainingState(dir, "ocx-aa11")).toBe(
      path.join(dir, "ocx-aa11-000004-state"),
    );
    expect(await latestTrainingState(dir, "ocx-cc33")).toBeUndefined();
    expect(await latestTrainingState(path.join(root, "nothing"), "ocx-aa11")).toBeUndefined();
  });
});
