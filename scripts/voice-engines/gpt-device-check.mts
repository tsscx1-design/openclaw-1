// Starts GPT-SoVITS the way the Gateway does and reports which device it chose and how long a short line takes.
import {
  ensureGptSovits,
  gptSovitsDevice,
  synthesizeGptSovits,
} from "../../src/gateway/gpt-sovits-tts.js";

const [referenceWav, promptText] = process.argv.slice(2);
const t0 = Date.now();
await ensureGptSovits();
const startSeconds = Number(((Date.now() - t0) / 1000).toFixed(1));
const t1 = Date.now();
const audio = await synthesizeGptSovits({
  text: "今日は、みなさんと、お話しできて、うれしいです。",
  referenceWav: referenceWav!,
  promptText,
});
console.log(
  JSON.stringify({
    device: await gptSovitsDevice(),
    startSeconds,
    lineSeconds: Number(((Date.now() - t1) / 1000).toFixed(1)),
    bytes: audio.length,
  }),
);
