"""Transcribes one short reference recording (Japanese) with faster-whisper on the CPU and prints {"text": "..."} as JSON.

Run with the Style-Bert-VITS2 venv python (faster-whisper is installed there):
  python transcribe_ref.py <reference.wav> [model]
The model defaults to "medium" (cached next to small and large-v3). Nothing is downloaded: a missing model is an error.
"""
import json
import os
import sys

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("HF_HUB_OFFLINE", "1")

from faster_whisper import WhisperModel  # noqa: E402

wav = sys.argv[1]
name = sys.argv[2] if len(sys.argv) > 2 else "medium"
model = WhisperModel(name, device="cpu", compute_type="int8")
segments, _info = model.transcribe(wav, language="ja", beam_size=5, vad_filter=False)
text = "".join(segment.text for segment in segments).strip()
sys.stdout.reconfigure(encoding="utf-8")
print(json.dumps({"text": text}, ensure_ascii=False))
