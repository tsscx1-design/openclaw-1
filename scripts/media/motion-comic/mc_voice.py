"""Voices for a motion comic with Irodori (large, GPU), all in ONE voice: a calm sample is made from
the description first, then every line speaks with that sample as its reference.
Usage: python mc_voice.py <voices.json>
voices.json: {"dir": "...", "caption": "声の説明", "lines": {"name": "セリフ（絵文字可）", ...}}
Writes voice-reference.wav and <name>.wav into dir (existing files are kept)."""

import json
import os
import subprocess
import sys

IRODORI = r"D:\media-tools\Irodori-TTS"
CHECKPOINT = "Aratako/Irodori-TTS-v4-Large"
SAMPLE = "…こんばんは。今日は、ゆっくりしていってね。あなたのこと、ずっと待ってたんだから。"

cfg = json.load(open(sys.argv[1], encoding="utf-8"))
sp = cfg["dir"]
proc = subprocess.Popen(
    [os.path.join(IRODORI, ".venv", "Scripts", "python.exe"), os.path.join(IRODORI, "openclaw_server.py")],
    cwd=IRODORI, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
    text=True, encoding="utf-8", env={**os.environ, "PYTHONIOENCODING": "utf-8"},
)


def speak(rid, text, out, caption=None, ref=None):
    if os.path.exists(out):
        return
    proc.stdin.write(json.dumps({"id": rid, "checkpoint": CHECKPOINT, "text": text, "caption": caption,
                                 "ref_wav": ref, "seed": None, "device": "cuda", "output": out}, ensure_ascii=False) + "\n")
    proc.stdin.flush()
    while True:
        line = proc.stdout.readline()
        if not line:
            raise SystemExit("Irodori stopped")
        if line.startswith(rid + " "):
            if " ERROR " in line:
                raise SystemExit(line)
            print(rid, "ok", flush=True)
            return


ref = os.path.join(sp, "voice-reference.wav")
speak("ref", SAMPLE, ref, caption=cfg["caption"])
for name, text in cfg["lines"].items():
    speak(name, text, os.path.join(sp, name + ".wav"), ref=ref)
proc.stdin.close()
proc.wait()
print("VOICES_DONE", flush=True)
