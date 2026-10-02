import { isEmbeddedAgentRunActive } from "../embedded-agent-runner/runs.js";
import type { InProcessGatewayCaller } from "./in-process-gateway.js";

const IDLE_POLL_MS = 1_000;
const IDLE_WAIT_LIMIT_MS = 15 * 60_000;

export type BackgroundChatNotifierOptions = {
  sessionKey?: string;
  agentId?: string;
  /** Live session id: notices wait until the main model's turn is over before touching the transcript. */
  sessionId?: string;
  gatewayCall: InProcessGatewayCaller;
  isMainRunActive?: (sessionId: string) => boolean;
};

export type BackgroundChatNotifier = {
  /** Updates the transient progress card; failures are ignored. */
  progress: (markdown: string) => Promise<void>;
  /** Appends an assistant notice to the chat once the main turn is idle; notices keep their order. */
  post: (message: string, label: string) => Promise<void>;
};

/**
 * Chat notices for work that outlives the tool call. A transcript append must not land between the
 * main model's tool call and its result, so each notice waits for the main turn to finish.
 */
export function createBackgroundChatNotifier(
  options: BackgroundChatNotifierOptions,
): BackgroundChatNotifier {
  const sessionKey = options.sessionKey?.trim();
  const isMainRunActive = options.isMainRunActive ?? isEmbeddedAgentRunActive;
  const scope = {
    ...(sessionKey ? { sessionKey } : {}),
    ...(options.agentId ? { agentId: options.agentId } : {}),
  };

  const progress = async (markdown: string) => {
    if (!sessionKey) {
      return;
    }
    try {
      await options.gatewayCall("progressCard.put", { ...scope, markdown });
    } catch {
      // The progress card is advisory.
    }
  };

  const waitForMainIdle = async () => {
    const sessionId = options.sessionId;
    if (!sessionId) {
      return;
    }
    const deadline = Date.now() + IDLE_WAIT_LIMIT_MS;
    while (isMainRunActive(sessionId) && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, IDLE_POLL_MS));
    }
  };

  let chain: Promise<void> = Promise.resolve();
  const post = (message: string, label: string) => {
    chain = chain.then(async () => {
      if (!sessionKey) {
        return;
      }
      await waitForMainIdle();
      try {
        await options.gatewayCall("chat.inject", { ...scope, message, label });
      } catch {
        await progress(message);
      }
    });
    return chain;
  };

  return { progress, post };
}
