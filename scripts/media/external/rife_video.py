"""Frame interpolation for OpenClaw media studio videos (RIFE v4.26 from Wan2GP, on the GPU).

Usage: python rife_video.py <input video> <output.mp4> [multiplier=2]
Doubles (or triples/quadruples) the frame rate so motion looks smoother; the duration and the
motion stay the same. The audio track, if any, is not carried over. Prints "RESULT <path>".
"""

import os
import sys

import av
import numpy as np
import torch

WAN2GP_DIR = r"D:\media-tools\Wan2GP"
RIFE_WEIGHTS = os.path.join(WAN2GP_DIR, "ckpts", "rife4.26.pkl")
sys.path.insert(0, WAN2GP_DIR)

from postprocessing.rife.inference import temporal_interpolation  # noqa: E402


def read_video(path: str) -> tuple[torch.Tensor, float]:
    with av.open(path) as container:
        stream = container.streams.video[0]
        fps = float(stream.average_rate or stream.guessed_rate or 16)
        frames = [frame.to_ndarray(format="rgb24") for frame in container.decode(stream)]
    # (T, H, W, C) uint8 -> (C, T, H, W), the layout RIFE's helper expects.
    return torch.from_numpy(np.stack(frames)).permute(3, 0, 1, 2).contiguous(), fps


def write_video(path: str, frames: torch.Tensor, fps: float) -> None:
    data = frames.permute(1, 2, 3, 0).contiguous().numpy()
    with av.open(path, mode="w") as container:
        stream = container.add_stream("libx264", rate=round(fps))
        stream.width = data.shape[2] - data.shape[2] % 2
        stream.height = data.shape[1] - data.shape[1] % 2
        stream.pix_fmt = "yuv420p"
        stream.options = {"crf": "18", "preset": "medium"}
        for image in data:
            frame = av.VideoFrame.from_ndarray(image[: stream.height, : stream.width], format="rgb24")
            container.mux(stream.encode(frame))
        container.mux(stream.encode())


def main() -> int:
    source, target = sys.argv[1], sys.argv[2]
    multiplier = int(sys.argv[3]) if len(sys.argv) > 3 else 2
    frames, fps = read_video(source)
    output = temporal_interpolation(RIFE_WEIGHTS, frames, multiplier, device="cuda")
    write_video(target, output.cpu(), fps * multiplier)
    print(f"RESULT {target}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
