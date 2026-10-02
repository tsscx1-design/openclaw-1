import { describe, expect, it } from "vitest";
import { classifyPcHealth, findCpuTempInLhm, parseNvidiaSmi } from "./pc-health.js";

const base = {
  at: 0,
  cpu: { model: "Ryzen", loadPct: 10 },
  ram: { usedGb: 30, totalGb: 64, usedPct: 47 },
  disk: { freeGb: 60, totalGb: 950 },
  services: [{ key: "llm", name: "メインLLM", up: true, required: true }],
};

describe("pc health", () => {
  it("parses nvidia-smi csv", () => {
    expect(
      parseNvidiaSmi("NVIDIA GeForce RTX 4060, 37, 3 %, 7717 MiB, 8188 MiB, [N/A], 30 %"),
    ).toEqual({
      name: "NVIDIA GeForce RTX 4060",
      tempC: 37,
      utilPct: 3,
      vramUsedMb: 7717,
      vramTotalMb: 8188,
      powerW: undefined,
      fanPct: 30,
    });
  });

  it("grades temperatures, memory and stopped services", () => {
    expect(classifyPcHealth(base).level).toBe("stable");
    expect(
      classifyPcHealth({
        ...base,
        gpu: { name: "g", tempC: 82, utilPct: 0, vramUsedMb: 0, vramTotalMb: 1 },
      }).level,
    ).toBe("caution");
    expect(classifyPcHealth({ ...base, cpu: { model: "c", loadPct: 1, tempC: 96 } }).level).toBe(
      "danger",
    );
    expect(
      classifyPcHealth({ ...base, ram: { usedGb: 60, totalGb: 64, usedPct: 94 } }).reasons[0],
    ).toContain("メモリ");
    const stopped = classifyPcHealth({
      ...base,
      services: [{ key: "llm", name: "メインLLM", up: false, required: true }],
    });
    expect(stopped.level).toBe("stopped");
  });

  it("finds the CPU package temperature in LibreHardwareMonitor data", () => {
    const tree = {
      Text: "PC",
      Children: [
        {
          Text: "AMD Ryzen 7 5700X",
          Children: [
            { Text: "Temperatures", Children: [{ Text: "Core (Tctl/Tdie)", Value: "61.3 °C" }] },
          ],
        },
        { Text: "NVIDIA GeForce RTX 4060", Children: [{ Text: "GPU Core", Value: "40.0 °C" }] },
      ],
    };
    expect(findCpuTempInLhm(tree)).toBe(61.3);
  });
});
