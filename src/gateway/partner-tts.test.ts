import { afterEach, describe, expect, it, vi } from "vitest";
import { prepareSpeechText, sanitizePartnerVoice, synthesizeSpeech } from "./partner-tts.js";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("partner TTS", () => {
  it("drops action notes and markdown before speaking", () => {
    expect(prepareSpeechText("《告》**マスター**、（静かに頷く）解析完了です。")).toBe(
      "《告》マスター、解析完了です。",
    );
  });

  it("accepts only known engines and clamps tuning", () => {
    expect(sanitizePartnerVoice({ engine: "voicevox", speakerId: 3, speed: 9, pitch: 1 })).toEqual({
      engine: "voicevox",
      speakerId: 3,
      speed: 2,
      pitch: 0.15,
    });
    expect(sanitizePartnerVoice({ engine: "elevenlabs", speakerId: 3 })).toBeUndefined();
    expect(sanitizePartnerVoice({ engine: "aivis", speakerId: 1.5 })).toBeUndefined();
  });

  it("applies the voice tuning to the audio query", async () => {
    const calls: Array<{ url: string; body?: string }> = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), body: init?.body as string | undefined });
        if (String(url).endsWith("/version")) return new Response('"0.25.2"');
        if (String(url).includes("/audio_query"))
          return new Response(JSON.stringify({ speedScale: 1, pitchScale: 0 }));
        return new Response(new Uint8Array([82, 73, 70, 70]));
      }),
    );

    const audio = await synthesizeSpeech(
      { engine: "voicevox", speakerId: 29, speed: 0.9, pitch: -0.05 },
      "告。",
    );

    expect(audio.subarray(0, 4).toString("latin1")).toBe("RIFF");
    expect(calls[1]?.url).toContain("/audio_query?speaker=29");
    expect(JSON.parse(calls[2]?.body ?? "{}")).toEqual({ speedScale: 0.9, pitchScale: -0.05 });
  });
});
