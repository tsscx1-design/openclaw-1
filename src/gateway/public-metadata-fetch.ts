import { createHttp1Agent, loadUndiciRuntimeDeps } from "../infra/net/undici-runtime.js";

let dispatcher: ReturnType<typeof createHttp1Agent> | undefined;

/** These public providers stall on this host's IPv6 route; IPv4 retains TLS verification. */
export function fetchPublicMetadata(
  input: string | URL,
  init?: { method?: string; headers?: Record<string, string>; body?: string; signal?: AbortSignal },
): Promise<Pick<Response, "ok" | "status" | "json">> {
  dispatcher ??= createHttp1Agent({ connect: { family: 4 } });
  // Use the same undici version for fetch and dispatcher: Node's bundled dispatcher dialect differs.
  return loadUndiciRuntimeDeps().fetch(input, { ...init, dispatcher, redirect: "error" });
}
