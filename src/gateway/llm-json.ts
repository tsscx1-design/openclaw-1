/** Extracts the first JSON object from a model reply; small models often wrap it in prose or fences. */
export function parseJsonObject(text: string): Record<string, string> {
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(text)?.[1] ?? text;
  const start = fenced.indexOf("{");
  const end = fenced.lastIndexOf("}");
  if (start === -1 || end <= start) {
    return {};
  }
  const body = fenced.slice(start, end + 1);
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    return Object.fromEntries(
      Object.entries(parsed)
        .filter(([, value]) => typeof value === "string" || typeof value === "number")
        .map(([key, value]) => [key.trim(), String(value).trim()]),
    );
  } catch {
    // Salvage `"label": "value"` pairs from almost-JSON.
    const out: Record<string, string> = {};
    for (const match of body.matchAll(/"([^"\n]+)"\s*:\s*"?([^"\n,}]*)"?/g)) {
      out[(match[1] ?? "").trim()] = (match[2] ?? "").trim();
    }
    return out;
  }
}
