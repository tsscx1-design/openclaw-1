"""Anime image tagger (WD SwinV2 v3, ONNX on the CPU): picture -> Danbooru-style tags.
Usage: python tagger.py <image> [threshold]
Prints one JSON line: {"general": [[tag, score], ...], "character": [...], "rating": {...}}
Used by the character creator partner to turn a submitted picture into look tags."""

import csv
import json
import sys

import numpy as np
import onnxruntime as ort
from PIL import Image

MODEL_DIR = r"D:\media-models\image\tagger\wd-swinv2-v3"
image_path = sys.argv[1]
threshold = float(sys.argv[2]) if len(sys.argv) > 2 else 0.35

session = ort.InferenceSession(MODEL_DIR + r"\model.onnx", providers=["CPUExecutionProvider"])
inp = session.get_inputs()[0]
size = inp.shape[1]

img = Image.open(image_path)
if img.mode in ("RGBA", "LA", "P"):
    img = img.convert("RGBA")
    bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
    bg.alpha_composite(img)
    img = bg
img = img.convert("RGB")
# Pad to a square on white, then resize; the model takes BGR float32 0-255.
side = max(img.size)
square = Image.new("RGB", (side, side), (255, 255, 255))
square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
square = square.resize((size, size), Image.BICUBIC)
arr = np.asarray(square, dtype=np.float32)[:, :, ::-1]
probs = session.run(None, {inp.name: arr[None, ...]})[0][0]

names, cats = [], []
with open(MODEL_DIR + r"\selected_tags.csv", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        names.append(row["name"])
        cats.append(int(row["category"]))

rating = {names[i]: round(float(probs[i]), 3) for i in range(len(names)) if cats[i] == 9}
general = sorted(
    [(names[i], round(float(probs[i]), 3)) for i in range(len(names)) if cats[i] == 0 and probs[i] >= threshold],
    key=lambda x: -x[1],
)
character = sorted(
    [(names[i], round(float(probs[i]), 3)) for i in range(len(names)) if cats[i] == 4 and probs[i] >= 0.8],
    key=lambda x: -x[1],
)
print(json.dumps({"general": general, "character": character, "rating": rating}, ensure_ascii=False))
