"""Long-lived Irodori-TTS worker for the OpenClaw gateway.

Reads one JSON request per line on stdin:
  {"id": "...", "checkpoint": "Aratako/Irodori-TTS-v4-Large", "text": "ん…😮‍💨 だめぇ…🥵",
   "caption": "大人の女性の甘い声" | null, "ref_wav": "C:/.../voice.wav" | null,
   "seed": 123 | null, "device": "cuda" | "cpu", "output": "C:/.../out.wav"}
and answers "<id> RESULT <path> <seed>" or "<id> ERROR <message>". The model of the last
checkpoint/device stays loaded (in RAM, and on the GPU between requests). It exits when stdin
closes. Nothing is served on the network.
"""

import json
import os
import sys
import traceback

# The canonical worker is in OpenClaw; Irodori's installed package lives in its working directory.
sys.path.insert(0, os.getcwd())

# Replies go on a private copy of stdout; everything the library prints (download and codec
# notes) goes to stderr, so it can never be mistaken for a reply.
_reply = os.fdopen(os.dup(sys.stdout.fileno()), "w", encoding="utf-8", buffering=1)
sys.stdout = sys.stderr


def reply(text: str) -> None:
    _reply.write(text + "\n")
    _reply.flush()

from irodori_tts.inference_runtime import (
    InferenceRuntime,
    RuntimeKey,
    SamplingRequest,
    download_hf_checkpoint,
    save_wav,
)

_runtime = None
_key = None


def runtime_for(checkpoint: str, device: str):
    global _runtime, _key
    key = (checkpoint, device)
    if _runtime is not None and _key == key:
        return _runtime
    _runtime = None
    import torch

    torch.cuda.empty_cache()
    path = download_hf_checkpoint(checkpoint)
    _runtime = InferenceRuntime.from_key(
        RuntimeKey(
            checkpoint=path,
            model_device=device,
            codec_repo="Aratako/Semantic-DACVAE-Japanese-32dim",
            model_precision="bf16" if device == "cuda" else "fp32",
            codec_device=device,
            codec_precision="fp32",
            codec_deterministic_encode=True,
            codec_deterministic_decode=True,
            compile_model=False,
            compile_dynamic=False,
        )
    )
    _key = key
    return _runtime


def run(req: dict) -> None:
    rid = req["id"]
    rt = runtime_for(req["checkpoint"], req.get("device", "cuda"))
    ref = req.get("ref_wav") or None
    caption = (req.get("caption") or "").strip() or None
    if caption and rt.model_cfg.use_caption_condition:
        tokens = rt.caption_tokenizer.encode(caption).numel()
        if tokens > rt.default_caption_max_len:
            raise ValueError(
                f"声の設定がモデルの上限を超えています（{tokens}/{rt.default_caption_max_len}トークン）。"
                "高さ・響き・発音・速度・語尾の要点にまとめてください。末尾は切り詰めていません。"
            )
    speaker = bool(rt.model_cfg.use_speaker_condition_resolved and ref)
    if caption and not rt.model_cfg.use_caption_condition and not speaker:
        raise ValueError("このモデルは声の文章設定に対応していません。声デザイン対応モデルか参照音声を選んでください。")
    result = rt.synthesize(
        SamplingRequest(
            text=req["text"],
            caption=caption if rt.model_cfg.use_caption_condition else None,
            ref_wav=ref if speaker else None,
            no_ref=not speaker,
            seed=req.get("seed"),
        ),
        log_fn=None,
    )
    save_wav(req["output"], result.audio, result.sample_rate)
    reply(f"{rid} RESULT {req['output']} {result.used_seed}")


def main() -> int:
    reply("READY")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        rid = "?"
        try:
            req = json.loads(line)
            rid = req.get("id", "?")
            run(req)
        except Exception as exc:  # report and keep serving
            traceback.print_exc(file=sys.stderr)
            reply(f"{rid} ERROR {str(exc).splitlines()[0][:300]}")
    return 0


if __name__ == "__main__":
    sys.stdin.reconfigure(encoding="utf-8")
    sys.exit(main())
