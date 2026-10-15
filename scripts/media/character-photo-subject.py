"""Keep the dominant detected person in a character reference photo.

Uses the existing DWPose YOLOX person detector. A close-up with no full-body
detection is kept for face analysis; overlapping people are rejected.
"""

import json
import sys

import cv2
import onnxruntime as ort
from PIL import Image

from character_face_reference import detect_reference_faces

sys.path.insert(0, r"D:\media-tools\Wan2GP")
from preprocessing.dwpose.onnxdet import inference_detector  # noqa: E402


def main(source: str, output: str) -> None:
    image = cv2.imread(source)
    if image is None:
        raise ValueError("画像を読み取れません")
    height, width = image.shape[:2]
    detector = ort.InferenceSession(
        r"D:\media-tools\Wan2GP\ckpts\pose\yolox_l.onnx",
        providers=["CPUExecutionProvider"],
    )
    boxes = inference_detector(detector, image)
    if boxes is None or len(boxes) == 0:
        detected = detect_reference_faces(Image.fromarray(cv2.cvtColor(image, cv2.COLOR_BGR2RGB)))
        faces = [(x0, y0, x1 - x0, y1 - y0) for x0, y0, x1, y1 in detected]
        if faces:
            faces = sorted(faces, key=lambda face: face[2] * face[3], reverse=True)
            fx, fy, fw, fh = map(int, faces[0])
            x0, y0 = max(0, fx - fw), max(0, fy - fh // 2)
            x1, y1 = min(width, fx + 2 * fw), min(height, fy + 3 * fh)
            if any(
                max(0, min(x1, ox + ow) - max(x0, ox))
                * max(0, min(y1, oy + oh) - max(y0, oy)) > 0
                for ox, oy, ow, oh in faces[1:]
            ):
                raise ValueError("顔が重なっています。対象の人だけが写る写真を選んでください")
            cv2.imwrite(output, image[y0:y1, x0:x1])
            print(json.dumps({"people": len(faces), "cropped": True}))
            return
        cv2.imwrite(output, image)
        print(json.dumps({"people": 0, "cropped": False}))
        return
    candidates = [list(map(float, box[:4])) for box in boxes]
    candidates.sort(key=lambda box: (box[2] - box[0]) * (box[3] - box[1]), reverse=True)
    target = candidates[0]
    x0, y0, x1, y1 = target
    area = max(1.0, (x1 - x0) * (y1 - y0))
    for other in candidates[1:]:
        overlap = max(0.0, min(x1, other[2]) - max(x0, other[0])) * max(
            0.0, min(y1, other[3]) - max(y0, other[1])
        )
        if overlap / area > 0.12:
            raise ValueError("人物が重なっています。対象の人だけが写る写真を選んでください")
    margin_x, margin_y = (x1 - x0) * 0.04, (y1 - y0) * 0.04
    left, top = max(0, int(x0 - margin_x)), max(0, int(y0 - margin_y))
    right, bottom = min(width, int(x1 + margin_x)), min(height, int(y1 + margin_y))
    crop = image[top:bottom, left:right].copy()
    for other in candidates[1:]:
        a, b = max(left, int(other[0])), max(top, int(other[1]))
        c, d = min(right, int(other[2])), min(bottom, int(other[3]))
        if c > a and d > b:
            crop[b - top : d - top, a - left : c - left] = 255
    if crop.size == 0 or not cv2.imwrite(output, crop):
        raise ValueError("人物の切り出しに失敗しました")
    print(json.dumps({"people": len(candidates), "cropped": True}))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
