"""Extract a detected face reference; never substitute a guessed crop for a face."""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

from manga_compose import detect_faces


def detect_reference_faces(image: Image.Image):
    faces = []
    # Reuse only a detector already installed locally; get_model must never download weights.
    model = Path.home() / ".insightface/models/buffalo_l/det_10g.onnx"
    if model.is_file():
        from insightface.model_zoo import get_model

        detector = get_model(str(model), providers=["CPUExecutionProvider"], download=False)
        detector.prepare(ctx_id=-1, input_size=(640, 640))
        boxes, _ = detector.detect(np.asarray(image)[:, :, ::-1].copy())
        faces = [tuple(box[:4]) for box in boxes]
    if not faces:
        faces = detect_faces(image)
    return faces


def main(source: str, target: str) -> None:
    image = Image.open(source).convert("RGB")
    faces = detect_reference_faces(image)
    if not faces:
        print("NONE", flush=True)
        return
    x0, y0, x1, y1 = max(faces, key=lambda box: (box[2] - box[0]) * (box[3] - box[1]))
    width, height = x1 - x0, y1 - y0
    crop = image.crop((
        max(0, int(x0 - width * 0.35)),
        max(0, int(y0 - height * 0.55)),
        min(image.width, int(x1 + width * 0.35)),
        min(image.height, int(y1 + height * 0.25)),
    ))
    crop.save(target)
    print("RESULT " + target, flush=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
