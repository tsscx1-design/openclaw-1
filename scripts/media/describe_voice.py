"""Bounded CPU acoustic measurements; no speaker identity or personality inference."""

import json
import math
import subprocess
import sys
import tempfile
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf

FFMPEG = "D:/media-tools/Wan2GP/ffmpeg_bins/ffmpeg.exe"
FFPROBE = "D:/media-tools/Wan2GP/ffmpeg_bins/ffprobe.exe"
RATE = 16000


def describe(files):
    pitches, probabilities, loudness, centroids = [], [], [], []
    sampled = 0.0
    available = 0.0
    warnings = [
        "数値は声そのものだけでなく、録音機器・雑音・演技・音声分離の影響を受けます。",
        "息っぽさ・かすれ・滑舌・アクセント・語尾・感情・性格は自動判定していません。",
        "複数話者やBGMが残る素材では声の特徴が混ざります。対象話者だけの音声を確認してください。",
        "生成モデルがHzなどの数値を正確に再現する保証はありません。参照音声と試し聞きで調整してください。",
        "声の高さは60〜650Hzの範囲で推定します。この範囲外の声や歌唱は十分に解析できない場合があります。",
    ]
    with tempfile.TemporaryDirectory(prefix="openclaw-voice-description-") as work:
        for index, filename in enumerate(files):
            duration = float(subprocess.check_output([
                FFPROBE, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", filename,
            ], text=True, timeout=30).strip())
            if not math.isfinite(duration) or duration <= 0:
                raise ValueError("音声の長さを確認できませんでした")
            available += duration
            budget = 60.0 / len(files)
            length = min(duration / 3, budget / 3)
            # Equal coverage of the beginning, middle and end, with a 60-second total budget.
            offsets = [0, max(0, (duration - length) / 2), max(0, duration - length)]
            for part, offset in enumerate(offsets):
                output = str(Path(work) / f"{index}-{part}.wav")
                subprocess.run([
                    FFMPEG, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", str(offset), "-i", filename, "-t", str(length),
                    "-vn", "-ac", "1", "-ar", str(RATE), output,
                ], check=True, timeout=60)
                audio, _ = sf.read(output, dtype="float32")
                if len(audio) < RATE * 0.2:
                    continue
                sampled += len(audio) / RATE
                rms = librosa.feature.rms(y=audio, frame_length=1024, hop_length=256)[0]
                if not np.any(rms > 0.001):
                    continue
                pitch, voiced, probability = librosa.pyin(
                    audio, sr=RATE, fmin=60, fmax=650, frame_length=1024,
                    hop_length=256, n_thresholds=50,
                )
                active = rms > max(0.001, float(np.max(rms)) * 0.05)
                valid = voiced & active & np.isfinite(pitch) & (probability >= 0.7)
                pitches.extend(pitch[valid].tolist())
                probabilities.extend(probability[valid].tolist())
                loudness.extend((20 * np.log10(np.maximum(rms[active], 1e-8))).tolist())
                centroid = librosa.feature.spectral_centroid(
                    y=audio, sr=RATE, n_fft=1024, hop_length=256,
                )[0]
                centroids.extend(centroid[active].tolist())
    if len(pitches) < 20:
        raise ValueError("信頼できる声の高さを測れませんでした。雑音の少ない、対象話者の明瞭な台詞を選んでください。")
    low, median, high = np.percentile(pitches, [10, 50, 90])
    semitones = 12 * np.log2(high / low)
    dynamics = float(np.percentile(loudness, 90) - np.percentile(loudness, 10))
    metrics = {
        "pitchMedianHz": round(float(median), 1),
        "pitchP10Hz": round(float(low), 1), "pitchP90Hz": round(float(high), 1),
        "pitchSpanSemitones": round(float(semitones), 1),
        "voicingProbabilityMedian": round(float(np.median(probabilities)), 3),
        "reliablePitchSeconds": round(len(pitches) * 256 / RATE, 1),
        "levelSpanDb": round(dynamics, 1),
        "spectralCentroidHz": round(float(np.median(centroids)), 1),
        "sampledSeconds": round(sampled, 1), "sourceSeconds": round(available, 1),
        "analyzedFiles": len(files),
    }
    height = "低め" if median < 140 else "中程度" if median < 220 else "高め"
    variation = "小さめ" if semitones < 4 else "中程度" if semitones < 9 else "大きめ"
    caption = (
        f"参照音声で観測された声の設定。声の高さは{height}。"
        f"基本周波数の中央値は{median:.1f}Hz、安定して計測できた音域の10〜90%は{low:.1f}〜{high:.1f}Hz。"
        f"音高の変化幅は{variation}（{semitones:.1f}半音）。"
        f"音量の変化幅は{dynamics:.1f}dB。声の高さと抑揚は、この参照音声を基準に調整する。\n"
        "響きの細部、息っぽさ、かすれ、発音、話す速度、間、アクセント、語尾、感情表現は未判定。"
        "これらは試聴して追記する。年齢・性別・性格を音声から推測した設定は加えない。"
    )
    if metrics["reliablePitchSeconds"] < 3:
        warnings.append("安定して測れた音声が3秒未満です。長めの明瞭な台詞で再解析してください。")
    return {"caption": caption, "metrics": metrics, "warnings": warnings}


if __name__ == "__main__":
    files = json.loads(sys.argv[1])
    if not isinstance(files, list) or not 1 <= len(files) <= 12:
        raise ValueError("解析する音声は1〜12ファイルです")
    print("RESULT " + json.dumps(describe(files), ensure_ascii=False, allow_nan=False), flush=True)
