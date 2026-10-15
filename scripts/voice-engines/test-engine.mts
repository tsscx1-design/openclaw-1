// Runs one cloning engine on a reference wav and reports whether audio came out (it cannot judge how good it sounds).
// Usage: node --import ./scripts/tsx.mjs scripts/voice-engines/test-engine.mts <engine> <reference.wav> <out.wav> ["reference words"] ["text"]
import * as fs from "node:fs/promises";

const [
  engine,
  referenceWav,
  out,
  promptText = "",
  text = "今日は、みなさんと、お話しできて、うれしいです。",
] = process.argv.slice(2);
if (!engine || !referenceWav || !out)
  throw new Error("usage: test-engine.mts <engine> <reference.wav> <out.wav> [promptText] [text]");

const started = Date.now();
let audio: Buffer;
if (engine === "gptsovits") {
  const { synthesizeGptSovits } = await import("../../src/gateway/gpt-sovits-tts.js");
  audio = await synthesizeGptSovits({ text, referenceWav, promptText });
} else {
  throw new Error(`unknown engine: ${engine}`);
}
await fs.writeFile(out, audio);

// wav header: 16-bit PCM, so the duration follows from the data size and the byte rate
const byteRate = audio.readUInt32LE(28);
const seconds = byteRate > 0 ? (audio.length - 44) / byteRate : 0;
console.log(
  JSON.stringify({
    engine,
    bytes: audio.length,
    audioSeconds: Number(seconds.toFixed(2)),
    tookSeconds: Number(((Date.now() - started) / 1000).toFixed(1)),
  }),
);
