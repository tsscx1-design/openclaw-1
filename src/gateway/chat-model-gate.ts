// The chat model is stopped while a generation job runs and comes back some time after the last
// job (media-studio). Chat code asks this gate first, so the first message after a job starts the
// model at once instead of waiting out the delay. A hook keeps media-studio out of the imports of
// the chat code.

type RestoreHook = () => Promise<boolean>;

let restoreHook: RestoreHook | undefined;

export function registerChatModelRestore(hook: RestoreHook): void {
  restoreHook = hook;
}

/** True when the chat model was started just now; the caller then waits until it answers. */
export async function restoreChatModelIfPending(): Promise<boolean> {
  return restoreHook ? restoreHook() : false;
}
