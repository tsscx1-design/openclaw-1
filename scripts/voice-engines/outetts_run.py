"""OuteTTS 1.0 (0.6B, GGUF through llama-cpp-python, on the CPU unless told otherwise) for one sentence.

Run with the dedicated venv (D:\\media-tools\\outetts\\venv):
  python outetts_run.py --ref reference.wav --ref-text "the exact words of the reference" --text-file text.txt --out out.wav [--model gguf] [--gpu-layers N]
The speaker profile made from the reference is kept next to it (reference.wav.speaker.json) and reused.
The first run lets the library fetch the tokenizer and the audio codec (DAC) from Hugging Face.
"""
import argparse
import json
import os
import sys

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
# openai-whisper (used by OuteTTS to time the words of the reference) calls an "ffmpeg" command: a copy lives next to the venv.
os.environ["PATH"] = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(sys.executable)))), "bin") + os.pathsep + os.environ.get("PATH", "")

import soundfile  # noqa: E402
import torch  # noqa: E402
import torchaudio  # noqa: E402


# torchaudio 2.9+ reads audio through torchcodec (not installed here). soundfile does the same job for wav files.
def _load(path, *_args, **_kwargs):
    data, rate = soundfile.read(path, always_2d=True, dtype="float32")
    return torch.from_numpy(data.T.copy()), rate


def _save(path, tensor, sample_rate=24000, *_args, **_kwargs):
    soundfile.write(path, tensor.detach().cpu().numpy().T, int(sample_rate))


torchaudio.load = _load
torchaudio.save = _save

import outetts  # noqa: E402

parser = argparse.ArgumentParser()
parser.add_argument("--ref", required=True)
parser.add_argument("--ref-text", default="")
parser.add_argument("--text-file", required=True)
parser.add_argument("--out", required=True)
parser.add_argument("--model", default=r"D:\llm-models\tts\outetts\OuteTTS-1.0-0.6B-Q8_0.gguf")
parser.add_argument("--gpu-layers", type=int, default=0)
parser.add_argument("--whisper", default="base")
args = parser.parse_args()

with open(args.text_file, encoding="utf-8") as f:
    text = f.read().strip()

interface = outetts.Interface(
    config=outetts.ModelConfig(
        model_path=args.model,
        tokenizer_path="OuteAI/OuteTTS-1.0-0.6B",
        interface_version=outetts.InterfaceVersion.V3,
        backend=outetts.Backend.LLAMACPP,
        n_gpu_layers=args.gpu_layers,
        max_seq_length=8192,
    )
)

profile = args.ref + ".speaker.json"
if os.path.exists(profile) and os.path.getmtime(profile) >= os.path.getmtime(args.ref):
    speaker = interface.load_speaker(profile)
else:
    if args.ref_text:
        speaker = interface.create_speaker(args.ref, transcript=args.ref_text, whisper_model=args.whisper, whisper_device="cpu")
    else:
        speaker = interface.create_speaker(args.ref, whisper_model=args.whisper, whisper_device="cpu")
    interface.save_speaker(speaker, profile)

output = interface.generate(
    config=outetts.GenerationConfig(
        text=text,
        generation_type=outetts.GenerationType.CHUNKED,
        speaker=speaker,
        sampler_config=outetts.SamplerConfig(temperature=0.4, repetition_penalty=1.1, repetition_range=64, top_k=40, top_p=0.9, min_p=0.05),
    )
)
output.save(args.out)
sys.stdout.reconfigure(encoding="utf-8")
print(json.dumps({"out": args.out}, ensure_ascii=False))
