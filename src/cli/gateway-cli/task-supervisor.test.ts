import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const spawn = vi.hoisted(() => vi.fn());
const readGatewayRestartHandoffSync = vi.hoisted(() =>
  vi.fn((): { createdAt: number } | null => null),
);

vi.mock("../../process/supervisor/index.js", () => ({
  getProcessSupervisor: () => ({ spawn }),
}));
vi.mock("../../infra/restart-handoff.js", () => ({
  readGatewayRestartHandoffSync,
}));

function managedChild(exitCode: number, onWait?: () => void) {
  return {
    cancel: vi.fn(),
    wait: async () => {
      onWait?.();
      return { exitCode, exitSignal: null };
    },
    waitForExtinction: vi.fn(async () => {}),
  };
}

describe("Windows Gateway task supervisor", () => {
  const argv = [...process.argv];
  const execArgv = [...process.execArgv];

  beforeEach(() => {
    process.argv = [
      process.execPath,
      "C:\\OpenClaw\\dist\\entry.js",
      "gateway",
      "--task-supervisor",
    ];
    process.execArgv = ["--import", "tsx"];
    vi.spyOn(process, "platform", "get").mockReturnValue("win32");
  });

  afterEach(() => {
    process.argv = [...argv];
    process.execArgv = [...execArgv];
    process.exitCode = undefined;
    vi.restoreAllMocks();
    spawn.mockReset();
    readGatewayRestartHandoffSync.mockReset();
    readGatewayRestartHandoffSync.mockReturnValue(null);
  });

  it("runs the Gateway child through the anchored Job Object and waits for its tree", async () => {
    const child = managedChild(0);
    spawn.mockResolvedValue(child);

    const { runWindowsGatewayTaskSupervisor } = await import("./task-supervisor.js");
    await runWindowsGatewayTaskSupervisor();

    expect(spawn).toHaveBeenCalledOnce();
    expect(spawn).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: "anchored-shell",
        command: expect.stringContaining("gateway"),
        scopeKey: `gateway-task-supervisor:${process.pid}`,
        captureOutput: false,
      }),
    );
    expect(spawn.mock.calls[0]?.[0].command).not.toContain("--task-supervisor");
    expect(spawn.mock.calls[0]?.[0].command).toContain("--import");
    expect(spawn.mock.calls[0]?.[0].command).toContain("tsx");
    expect(child.waitForExtinction).toHaveBeenCalledOnce();
  });

  it("relaunches the Gateway after a planned restart handoff from the exited generation", async () => {
    const first = managedChild(0, () => {
      readGatewayRestartHandoffSync.mockReturnValue({ createdAt: Date.now() });
    });
    const second = managedChild(0, () => {
      readGatewayRestartHandoffSync.mockReturnValue(null);
    });
    spawn.mockResolvedValueOnce(first).mockResolvedValueOnce(second);

    const { runWindowsGatewayTaskSupervisor } = await import("./task-supervisor.js");
    await runWindowsGatewayTaskSupervisor();

    expect(spawn).toHaveBeenCalledTimes(2);
    expect(spawn.mock.calls[0]?.[0].command).toBe(spawn.mock.calls[1]?.[0].command);
    expect(spawn.mock.calls[1]?.[0].scopeKey).toBe(`gateway-task-supervisor:${process.pid}:1`);
    // The next generation starts only after the previous Job tree is gone.
    expect(first.waitForExtinction).toHaveBeenCalledOnce();
  });

  it("ends the task on a clean exit whose handoff predates the generation", async () => {
    readGatewayRestartHandoffSync.mockReturnValue({ createdAt: 1 });
    spawn.mockResolvedValue(managedChild(0));

    const { runWindowsGatewayTaskSupervisor } = await import("./task-supervisor.js");
    await runWindowsGatewayTaskSupervisor();

    expect(spawn).toHaveBeenCalledOnce();
  });

  it("propagates a crash exit code instead of relaunching", async () => {
    readGatewayRestartHandoffSync.mockReturnValue({ createdAt: Date.now() + 60_000 });
    spawn.mockResolvedValue(managedChild(3));

    const { runWindowsGatewayTaskSupervisor } = await import("./task-supervisor.js");
    await runWindowsGatewayTaskSupervisor();

    expect(spawn).toHaveBeenCalledOnce();
    expect(process.exitCode).toBe(3);
  });

  it("does not relaunch after a stop signal even when a restart was requested", async () => {
    const child = managedChild(0, () => {
      readGatewayRestartHandoffSync.mockReturnValue({ createdAt: Date.now() });
      process.emit("SIGTERM");
    });
    spawn.mockResolvedValue(child);

    const { runWindowsGatewayTaskSupervisor } = await import("./task-supervisor.js");
    await runWindowsGatewayTaskSupervisor();

    expect(spawn).toHaveBeenCalledOnce();
    expect(child.cancel).toHaveBeenCalledWith("signal");
  });
});
