import { execFile } from "node:child_process";
import * as fs from "node:fs/promises";
import * as os from "node:os";

export type PcHealthLevel = "stable" | "caution" | "danger" | "stopped";

export type PcHealthSnapshot = {
  at: number;
  level: PcHealthLevel;
  /** Human-readable reasons behind a non-stable level. */
  reasons: string[];
  gpu?: {
    name: string;
    tempC: number;
    utilPct: number;
    vramUsedMb: number;
    vramTotalMb: number;
    fanPct?: number;
    powerW?: number;
  };
  cpu: { model: string; loadPct: number; tempC?: number };
  ram: { usedGb: number; totalGb: number; usedPct: number };
  disk?: { freeGb: number; totalGb: number };
  services: Array<{ key: string; name: string; up: boolean; required: boolean }>;
};

// Thresholds chosen for an always-on desktop (RTX 4060 / Ryzen 7 5700X).
const GPU_CAUTION_C = 80;
const GPU_DANGER_C = 88;
const CPU_CAUTION_C = 85;
const CPU_DANGER_C = 95;
const RAM_CAUTION_PCT = 92;
const DISK_CAUTION_GB = 15;

function run(cmd: string, args: string[], timeoutMs = 5_000): Promise<string> {
  return new Promise((resolve) => {
    execFile(cmd, args, { timeout: timeoutMs, windowsHide: true }, (err, stdout) =>
      resolve(err ? "" : String(stdout)),
    );
  });
}

export function parseNvidiaSmi(line: string): PcHealthSnapshot["gpu"] {
  const parts = line.split(",").map((p) => p.trim());
  if (parts.length < 5) return undefined;
  const num = (v: string | undefined) => {
    const n = Number.parseFloat((v ?? "").replace(/[^\d.]/g, ""));
    return Number.isFinite(n) ? n : undefined;
  };
  const temp = num(parts[1]);
  if (temp === undefined) return undefined;
  return {
    name: parts[0] ?? "GPU",
    tempC: temp,
    utilPct: num(parts[2]) ?? 0,
    vramUsedMb: num(parts[3]) ?? 0,
    vramTotalMb: num(parts[4]) ?? 0,
    powerW: num(parts[5]),
    fanPct: num(parts[6]),
  };
}

async function readGpu(): Promise<PcHealthSnapshot["gpu"]> {
  const out = await run("nvidia-smi", [
    "--query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total,power.draw,fan.speed",
    "--format=csv,noheader",
  ]);
  return out ? parseNvidiaSmi(out.split(/\r?\n/)[0] ?? "") : undefined;
}

async function sampleCpuLoad(): Promise<number> {
  const snap = () =>
    os
      .cpus()
      .map((c) => ({
        idle: c.times.idle,
        total: Object.values(c.times).reduce((a, b) => a + b, 0),
      }));
  const a = snap();
  await new Promise((resolve) => setTimeout(resolve, 400));
  const b = snap();
  let idle = 0;
  let total = 0;
  b.forEach((cur, i) => {
    idle += cur.idle - (a[i]?.idle ?? 0);
    total += cur.total - (a[i]?.total ?? 0);
  });
  return total > 0 ? Math.round((1 - idle / total) * 100) : 0;
}

/** CPU package temperature from LibreHardwareMonitor's web server, when it runs (needs admin once). */
export function findCpuTempInLhm(node: unknown): number | undefined {
  const temps: Array<{ text: string; value: number }> = [];
  const walk = (n: unknown, underCpu: boolean) => {
    if (!n || typeof n !== "object") return;
    const obj = n as { Text?: string; Value?: string; Children?: unknown[]; ImageURL?: string };
    const text = obj.Text ?? "";
    const isCpu = underCpu || /cpu|ryzen|intel/i.test(text) || /cpu/i.test(obj.ImageURL ?? "");
    const value = Number.parseFloat((obj.Value ?? "").replace(",", "."));
    if (isCpu && /°C/.test(obj.Value ?? "") && Number.isFinite(value)) temps.push({ text, value });
    for (const child of obj.Children ?? []) walk(child, isCpu);
  };
  walk(node, false);
  const preferred = temps.find((t) => /package|tctl|tdie/i.test(t.text)) ?? temps[0];
  return preferred?.value;
}

async function readCpuTemp(): Promise<number | undefined> {
  try {
    const res = await fetch("http://127.0.0.1:8085/data.json", {
      signal: AbortSignal.timeout(1_500),
    });
    return res.ok ? findCpuTempInLhm(await res.json()) : undefined;
  } catch {
    return undefined;
  }
}

async function isHttpUp(url: string): Promise<boolean> {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(2_000) });
    return res.ok;
  } catch {
    return false;
  }
}

async function readDisk(): Promise<PcHealthSnapshot["disk"]> {
  try {
    const stat = await fs.statfs(os.homedir());
    return {
      freeGb: Math.round((stat.bavail * stat.bsize) / 1e9),
      totalGb: Math.round((stat.blocks * stat.bsize) / 1e9),
    };
  } catch {
    return undefined;
  }
}

/** Classifies a snapshot; the main LLM being down outranks temperature warnings. */
export function classifyPcHealth(s: Omit<PcHealthSnapshot, "level" | "reasons">): {
  level: PcHealthLevel;
  reasons: string[];
} {
  const reasons: string[] = [];
  let level: PcHealthLevel = "stable";
  const raise = (to: PcHealthLevel, reason: string) => {
    reasons.push(reason);
    const order: PcHealthLevel[] = ["stable", "caution", "danger", "stopped"];
    if (order.indexOf(to) > order.indexOf(level)) level = to;
  };
  for (const svc of s.services) {
    if (svc.required && !svc.up) raise("stopped", `${svc.name}が停止しています`);
  }
  if (s.gpu) {
    if (s.gpu.tempC >= GPU_DANGER_C) raise("danger", `GPUが高温です（${s.gpu.tempC}℃）`);
    else if (s.gpu.tempC >= GPU_CAUTION_C) raise("caution", `GPUが熱めです（${s.gpu.tempC}℃）`);
  }
  if (s.cpu.tempC !== undefined) {
    if (s.cpu.tempC >= CPU_DANGER_C) raise("danger", `CPUが高温です（${s.cpu.tempC}℃）`);
    else if (s.cpu.tempC >= CPU_CAUTION_C) raise("caution", `CPUが熱めです（${s.cpu.tempC}℃）`);
  }
  if (s.ram.usedPct >= RAM_CAUTION_PCT)
    raise("caution", `メモリ残りわずか（${s.ram.usedPct}%使用）`);
  if (s.disk && s.disk.freeGb < DISK_CAUTION_GB) raise("caution", `ディスク残り${s.disk.freeGb}GB`);
  return { level, reasons };
}

export async function readPcHealth(): Promise<PcHealthSnapshot> {
  const [gpu, loadPct, tempC, disk, llm, voicevox, aivis, ...workers] = await Promise.all([
    readGpu(),
    sampleCpuLoad(),
    readCpuTemp(),
    readDisk(),
    isHttpUp("http://127.0.0.1:11434/health"),
    isHttpUp("http://127.0.0.1:50021/version"),
    isHttpUp("http://127.0.0.1:10101/version"),
    ...[11435, 11436, 11437, 11438].map((port) => isHttpUp(`http://127.0.0.1:${port}/health`)),
  ]);
  const total = os.totalmem();
  const used = total - os.freemem();
  const base = {
    at: Date.now(),
    gpu,
    cpu: {
      model: os.cpus()[0]?.model.trim() ?? "CPU",
      loadPct,
      ...(tempC !== undefined ? { tempC: Math.round(tempC) } : {}),
    },
    ram: {
      usedGb: Math.round((used / 1e9) * 10) / 10,
      totalGb: Math.round((total / 1e9) * 10) / 10,
      usedPct: Math.round((used / total) * 100),
    },
    disk,
    services: [
      { key: "gateway", name: "ゲートウェイ", up: true, required: true },
      { key: "llm", name: "メインLLM", up: llm, required: true },
      {
        key: "workers",
        name: `裏作業員（${workers.filter(Boolean).length}/4 待機）`,
        up: workers.some(Boolean),
        required: false,
      },
      { key: "voicevox", name: "VOICEVOX", up: voicevox, required: false },
      { key: "aivis", name: "AivisSpeech", up: aivis, required: false },
    ],
  };
  return { ...base, ...classifyPcHealth(base) };
}
