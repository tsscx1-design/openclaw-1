import * as fs from "node:fs";
import * as path from "node:path";
import { describe, expect, it } from "vitest";
import { ErrorCodes } from "../../packages/gateway-protocol/src/index.js";

// A response whose error has no `code` is dropped by the Android app (the code is required there),
// so the app waits for a time-out instead of showing the reason. A name that is not in ErrorCodes
// reads as undefined, which is exactly that case; this test finds one before it ships.

function sourceFiles(dir: string): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === "node_modules" ? [] : sourceFiles(full);
    return /\.ts$/.test(entry.name) && !/\.test\.ts$/.test(entry.name) ? [full] : [];
  });
}

describe("ErrorCodes usage in the gateway", () => {
  it("only names codes that exist, so every failure response carries a code", () => {
    const known = new Set(Object.keys(ErrorCodes));
    const unknown: string[] = [];
    for (const file of sourceFiles(path.join(__dirname))) {
      const text = fs.readFileSync(file, "utf-8");
      for (const match of text.matchAll(/\bErrorCodes\.([A-Z][A-Z0-9_]*)\b/g)) {
        if (!known.has(match[1] ?? ""))
          unknown.push(`${path.relative(__dirname, file)}: ErrorCodes.${match[1]}`);
      }
    }
    expect(unknown).toEqual([]);
  });
});
