"""Whole-body keypoints (DWPose, 133 points: body, feet, face, hands) for every person in a CG.
Usage: python keypoints.py <image> <out.json> [preview.jpg]
Runs on the CPU so it can work while the GPU makes video."""

import json
import sys

import cv2
import numpy as np

sys.path.insert(0, r"D:\media-tools\Wan2GP")
import onnxruntime as ort  # noqa: E402
from preprocessing.dwpose.onnxdet import inference_detector  # noqa: E402
from preprocessing.dwpose.onnxpose import inference_pose  # noqa: E402

CK = r"D:\media-tools\Wan2GP\ckpts\pose"
providers = ["CPUExecutionProvider"]
det = ort.InferenceSession(CK + r"\yolox_l.onnx", providers=providers)
pose = ort.InferenceSession(CK + r"\dw-ll_ucoco_384.onnx", providers=providers)

img = cv2.imread(sys.argv[1])
boxes = inference_detector(det, img)
people = []
if boxes is not None and len(boxes):
    kps, scores = inference_pose(pose, boxes, img)
    for box, kp, sc in zip(boxes, kps, scores):
        people.append({"box": [float(v) for v in box], "kp": kp.tolist(), "score": sc.tolist()})
json.dump({"size": [img.shape[1], img.shape[0]], "people": people}, open(sys.argv[2], "w"))
print("people", len(people))

if len(sys.argv) > 3:
    view = img.copy()
    names = {0: "nose", 5: "Lsho", 6: "Rsho", 7: "Lelb", 8: "Relb", 9: "Lwri", 10: "Rwri", 11: "Lhip", 12: "Rhip",
             13: "Lkne", 14: "Rkne", 15: "Lank", 16: "Rank", 17: "Lbtoe", 20: "Rbtoe"}
    for p in people:
        x0, y0, x1, y1 = map(int, p["box"])
        cv2.rectangle(view, (x0, y0), (x1, y1), (0, 255, 255), 2)
        for i, ((x, y), s) in enumerate(zip(p["kp"], p["score"])):
            if i >= 23 and i < 91:
                continue  # skip the face points in the preview
            color = (0, 255, 0) if s > 0.4 else (0, 0, 255)
            cv2.circle(view, (int(x), int(y)), 4, color, -1)
            if i in names:
                cv2.putText(view, f"{names[i]} {s:.1f}", (int(x) + 5, int(y)), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1)
    cv2.imwrite(sys.argv[3], view)
