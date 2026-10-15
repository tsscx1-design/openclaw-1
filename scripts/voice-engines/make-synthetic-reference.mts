// Makes a synthetic reference voice (VOICEVOX, not a real person) for testing the cloning engines.
// Usage: node --import ./scripts/tsx.mjs scripts/voice-engines/make-synthetic-reference.mts <out.wav> [speakerId]
import * as fs from "node:fs/promises";
import { ensureTtsEngine, synthesizeSpeech } from "../../src/gateway/partner-tts.js";

const out = process.argv[2];
const speakerId = Number(process.argv[3] ?? 3);
if (!out) throw new Error("usage: make-synthetic-reference.mts <out.wav> [speakerId]");

// A reference needs its exact transcript for some engines, so the text is fixed and printed.
const TEXT = "こんにちは、今日はいい天気ですね。これは、声の確認用の、合成された音声です。";
await ensureTtsEngine("voicevox");
const audio = await synthesizeSpeech({ engine: "voicevox", speakerId }, TEXT);
await fs.writeFile(out, audio);
console.log(JSON.stringify({ out, bytes: audio.length, text: TEXT }));
