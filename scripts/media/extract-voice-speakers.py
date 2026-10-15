"""Separate a video's vocals, diarize two voices, and export clean per-speaker WAVs.

The source is a user-uploaded artifact. Existing Wan2GP RoFormer and Sherpa
models provide separation and diarization; they are downloaded on first use.
"""

import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, r"D:\media-tools\Wan2GP")
from preprocessing.extract_vocals import get_vocals  # noqa: E402
from preprocessing.speaker_separator import separator as speaker_separator  # noqa: E402

FFMPEG = r"D:\media-tools\Wan2GP\ffmpeg_bins\ffmpeg.exe"
FFPROBE = r"D:\media-tools\Wan2GP\ffmpeg_bins\ffprobe.exe"
RATE = 16000


def convert(source: str, target: str, *, clean: bool = False) -> None:
    filters = ["highpass=f=75", "lowpass=f=7500"]
    if clean:
        filters.append("afftdn=nr=5")
    subprocess.run(
        [FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", source,
         "-vn", "-af", ",".join(filters), "-ac", "1" if clean else "2",
         "-ar", str(RATE if clean else 44100), target],
        check=True,
        timeout=1800,
    )


def clean_intervals(segments, label):
    own = [(float(s.start), float(s.end)) for s in segments.label_timeline(label)]
    others = [
        (float(s.start), float(s.end))
        for s, _, speaker in segments.itertracks(yield_label=True) if speaker != label
    ]
    for start, end in own:
        parts = [(start, end)]
        for other_start, other_end in others:
            next_parts = []
            for a, b in parts:
                if other_end <= a or other_start >= b:
                    next_parts.append((a, b))
                else:
                    if other_start - a >= 0.4:
                        next_parts.append((a, other_start))
                    if b - other_end >= 0.4:
                        next_parts.append((other_end, b))
            parts = next_parts
        yield from ((a, b) for a, b in parts if b - a >= 0.4)


def main(source: str, output_dir: str) -> None:
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    duration = float(subprocess.check_output(
        [FFPROBE, "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", source], text=True
    ).strip())
    if not 1 <= duration <= 1800:
        raise ValueError("音声の長さは1秒から30分までにしてください")
    with tempfile.TemporaryDirectory(prefix="openclaw-voice-") as work:
        prepared = os.path.join(work, "source.wav")
        vocals = os.path.join(work, "vocals.wav")
        clean = os.path.join(work, "clean.wav")
        convert(source, prepared)
        vocal_file = get_vocals(prepared, vocals)
        convert(vocal_file, clean, clean=True)
        # The host helper defaults to exactly two clusters; let its clustering
        # threshold estimate the number of speakers for arbitrary conversations.
        speaker_separator.SHERPA_ONNX_NUM_SPEAKERS = -1
        diarizer = speaker_separator.OptimizedPyannote31SpeakerSeparator()
        diarization = diarizer.perform_optimized_diarization(clean)
        audio, rate = sf.read(clean, dtype="float32", always_2d=False)
        if rate != RATE:
            raise ValueError("音声のサンプルレートが不正です")
        if audio.ndim > 1:
            audio = audio.mean(axis=1)
        count = 0
        for label in sorted(diarization.labels()):
            clips = []
            for start, end in clean_intervals(diarization, label):
                chunk = audio[max(0, int(start * RATE)):min(len(audio), int(end * RATE))].copy()
                if len(chunk) < RATE * 0.4:
                    continue
                fade = min(len(chunk) // 4, int(RATE * 0.015))
                chunk[:fade] *= np.linspace(0, 1, fade, dtype=np.float32)
                chunk[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)
                clips.extend((chunk, np.zeros(int(RATE * 0.08), dtype=np.float32)))
            if not clips:
                continue
            joined = np.concatenate(clips)
            if len(joined) < RATE * 3:
                continue
            count += 1
            sf.write(output / f"speaker-{count}.wav", joined, RATE, subtype="PCM_16")
            sf.write(output / f"speaker-{count}-preview.wav", joined[:RATE * 12], RATE, subtype="PCM_16")
            print(f"SPEAKER {count} {len(joined) / RATE:.1f}s", flush=True)
        if count == 0:
            raise ValueError("話者の音声を検出できませんでした")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
