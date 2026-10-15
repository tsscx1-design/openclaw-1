// Media jobs and voice training stop the main chat model (port 11434) to take the GPU. When the
// chat itself started them (the media_studio agent tool), that model is still writing its reply,
// so they first wait for it to go quiet.

const MAIN_LLM = "http://127.0.0.1:11434";

/** True while the main llama-server is working on a request (a busy slot in /slots). */
export async function isMainLlmBusy(): Promise<boolean> {
  try {
    const res = await fetch(`${MAIN_LLM}/slots`, { signal: AbortSignal.timeout(2_000) });
    if (!res.ok) return false;
    const slots = (await res.json()) as Array<{ is_processing?: boolean }>;
    return slots.some((slot) => slot.is_processing === true);
  } catch {
    return false;
  }
}

/**
 * Resolves once the main chat model has been idle for `quietMs`, so the GPU is taken between
 * replies rather than in the middle of one. The quiet window covers the short gap between a tool
 * result and the agent's next request. Gives up after `maxMs` (returns false).
 */
export async function waitForChatTurnEnd(
  options: {
    quietMs?: number;
    maxMs?: number;
    pollMs?: number;
    isBusy?: () => Promise<boolean>;
  } = {},
): Promise<boolean> {
  const quietMs = options.quietMs ?? 10_000;
  const maxMs = options.maxMs ?? 180_000;
  const pollMs = options.pollMs ?? 1_000;
  const isBusy = options.isBusy ?? isMainLlmBusy;
  const start = Date.now();
  let quietSince = start;
  while (Date.now() - start < maxMs) {
    if (await isBusy()) quietSince = Date.now();
    else if (Date.now() - quietSince >= quietMs) return true;
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }
  return false;
}
