"""Lays out finished panel images as a Japanese manga page or a vertical-scroll comic, with
vertical speech bubbles placed beside the speaker and clear of faces.

Input: one JSON file (argv[1]) with
  layout: "4koma" | "page" | "webtoon"
  title: str, episodeTitle: str (webtoon)
  font, fontBold: paths to a Japanese TrueType/TTC font
  panels: [{image, lines: [{who, text}], narration, speakers: {name: "left"|"center"|"right"}}]
  out: output PNG path (webtoon: <out>-1.png, <out>-2.png, ...)
Reading order is Japanese: top to bottom, and right to left within a row; the first line of a
panel goes furthest right. Faces are found with an anime face detector (ONNX, MIT licence) so
bubbles do not cover them; without the model the bubbles simply stay near the top.
Prints "RESULT <path>" (pages) or "RESULT [<paths>]" (webtoon).
"""

import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROTATE = set("ー－—―〜～…‥（）「」『』()[]【】〈〉《》-=＝")
PUNCT_SHIFT = set("、。，．,.")
SMALL_KANA = set("ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ")
BREAK_AFTER = set("、。！？!?…」』")
FACE_MODEL = r"D:\media-models\image\detect\face_v1.4_s_model.onnx"
FACE_THRESHOLD = 0.31

_face_session = None


# ---------- faces ----------


def detect_faces(image):
    """Face boxes (x0, y0, x1, y1) in the image's pixels; [] when the detector is unavailable."""
    global _face_session
    try:
        import numpy as np
        import onnxruntime as ort

        if _face_session is None:
            if not os.path.exists(FACE_MODEL):
                return []
            _face_session = ort.InferenceSession(FACE_MODEL, providers=["CPUExecutionProvider"])
        size = 640
        scale = min(size / image.width, size / image.height)
        resized = image.resize((max(1, int(image.width * scale)), max(1, int(image.height * scale))))
        canvas = Image.new("RGB", (size, size), (114, 114, 114))
        canvas.paste(resized, (0, 0))
        x = np.asarray(canvas, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
        out = _face_session.run(None, {"images": x})[0][0]
        rows = out.T if out.shape[0] < out.shape[1] else out
        rows = rows[rows[:, 4] > FACE_THRESHOLD]
        boxes = []
        for cx, cy, w, h, score in rows[(-rows[:, 4]).argsort()]:
            box = ((cx - w / 2) / scale, (cy - h / 2) / scale, (cx + w / 2) / scale, (cy + h / 2) / scale)
            if all(iou(box, kept) < 0.4 for kept in boxes):
                boxes.append(box)
        return boxes[:6]
    except Exception as exc:  # the layout still works without faces
        print(f"face detection skipped: {exc}", file=sys.stderr, flush=True)
        return []


def iou(a, b):
    inter = overlap(a, b)
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union > 0 else 0.0


def overlap(a, b):
    w = min(a[2], b[2]) - max(a[0], b[0])
    h = min(a[3], b[3]) - max(a[1], b[1])
    return w * h if w > 0 and h > 0 else 0.0


# ---------- layout ----------


def page_boxes(layout, count):
    """Panel boxes (x, y, w, h) in reading order, and the page size."""
    if layout == "4koma":
        width, margin, title_h, panel_h, gutter = 1000, 40, 96, 640, 28
        boxes = []
        y = margin + title_h
        for _ in range(count):
            boxes.append((margin, y, width - 2 * margin, panel_h))
            y += panel_h + gutter
        return boxes, (width, y - gutter + margin)
    width, height, margin, gutter, title_h = 1800, 2560, 70, 28, 80
    rows = {
        3: [(0.34, [1.0]), (0.33, [1.0]), (0.33, [1.0])],
        4: [(0.30, [1.0]), (0.38, [0.55, 0.45]), (0.32, [1.0])],
        5: [(0.30, [1.0]), (0.38, [0.55, 0.45]), (0.32, [0.4, 0.6])],
        6: [(0.32, [0.6, 0.4]), (0.36, [0.45, 0.55]), (0.32, [0.55, 0.45])],
    }[max(3, min(6, count))]
    inner_w = width - 2 * margin
    inner_h = height - 2 * margin - title_h - gutter * (len(rows) - 1)
    boxes = []
    y = margin + title_h
    for frac, widths in rows:
        row_h = int(inner_h * frac)
        usable = inner_w - gutter * (len(widths) - 1)
        right = width - margin
        for part in widths:
            w = int(usable * part)
            boxes.append((right - w, y, w, row_h))
            right -= w + gutter
        y += row_h + gutter
    return boxes[:count], (width, height)


def cover(image, w, h):
    """Scales and crops to fill w x h, keeping a little more of the top (faces)."""
    scale = max(w / image.width, h / image.height)
    resized = image.resize((math.ceil(image.width * scale), math.ceil(image.height * scale)), Image.LANCZOS)
    left = (resized.width - w) // 2
    top = int((resized.height - h) * 0.3)
    return resized.crop((left, top, left + w, top + h))


# ---------- vertical text ----------


def columns_of(text, per_column):
    """Splits text into vertical columns, preferring breaks after punctuation."""
    text = "".join(ch for ch in text.strip() if ch not in "\r\n")
    cols, current = [], ""
    for ch in text:
        current += ch
        if len(current) >= per_column or (ch in BREAK_AFTER and len(current) >= per_column * 0.6):
            cols.append(current)
            current = ""
    if current:
        # A lone closing mark goes back onto the previous column.
        if cols and all(c in BREAK_AFTER for c in current):
            cols[-1] += current
        else:
            cols.append(current)
    return cols


def draw_vertical(page, x_right, y_top, cols, font, size):
    """Draws columns right to left, each top to bottom, starting at (x_right, y_top)."""
    draw = ImageDraw.Draw(page)
    step = int(size * 1.08)
    col_w = int(size * 1.25)
    for ci, col in enumerate(cols):
        cx = x_right - ci * col_w - size  # left edge of this column's glyph cell
        for ri, ch in enumerate(col):
            cy = y_top + ri * step
            if ch in ROTATE:
                glyph = Image.new("L", (size * 2, size * 2), 0)
                ImageDraw.Draw(glyph).text((size // 2, size // 2), ch, font=font, fill=255)
                glyph = glyph.rotate(-90, resample=Image.BICUBIC)
                bbox = glyph.getbbox()
                if bbox:
                    glyph = glyph.crop(bbox)
                    gx = cx + (size - glyph.width) // 2
                    gy = cy + (size - glyph.height) // 2
                    page.paste((0, 0, 0), (gx, gy), glyph)
            elif ch in PUNCT_SHIFT:
                draw.text((cx + int(size * 0.55), cy - int(size * 0.45)), ch, font=font, fill=(0, 0, 0))
            elif ch in SMALL_KANA:
                draw.text((cx + int(size * 0.1), cy - int(size * 0.1)), ch, font=font, fill=(0, 0, 0))
            else:
                draw.text((cx, cy), ch, font=font, fill=(0, 0, 0))


def text_block(text, size, max_rows):
    cols = columns_of(text, max_rows)
    if not cols:
        return None
    rows = max(len(c) for c in cols)
    text_w = len(cols) * int(size * 1.25)
    text_h = rows * int(size * 1.08)
    pad_x, pad_y = int(size * 0.9), int(size * 0.8)
    return {"cols": cols, "text_w": text_w, "w": text_w + 2 * pad_x, "h": text_h + 2 * pad_y, "pad_x": pad_x, "pad_y": pad_y}


# ---------- bubble placement ----------

SIDE_X = {"right": 0.82, "center": 0.5, "left": 0.18}


def place_bubble(box, bw, bh, faces, taken, prefer_x):
    """Top-left corner for a bw x bh bubble in box: clear of faces and other bubbles, high up,
    near prefer_x (0..1 across the panel)."""
    x0, y0, w, h = box
    margin = 8
    best, best_score = None, None
    max_left = x0 + w - bw - margin
    max_top = y0 + h - bh - margin
    if max_left < x0 + margin or max_top < y0 + margin:
        return (max(x0 + margin, min(max_left, x0 + int(prefer_x * w - bw / 2))), y0 + margin)
    step_x = max(12, w // 40)
    step_y = max(12, h // 30)
    for top in range(y0 + margin, max_top + 1, step_y):
        for left in range(x0 + margin, max_left + 1, step_x):
            rect = (left, top, left + bw, top + bh)
            face_hit = sum(overlap(rect, f) for f in faces)
            bubble_hit = sum(overlap(rect, t) for t in taken)
            centre = (left + bw / 2 - x0) / w
            score = (
                face_hit * 12
                + bubble_hit * 30
                + (top - y0) * bw * 0.6
                + abs(centre - prefer_x) * w * bh * 0.8
            )
            if best_score is None or score < best_score:
                best, best_score = (left, top), score
    return best


def draw_bubble(page, left, top, block, font_path, size, tail_to):
    draw = ImageDraw.Draw(page)
    bw, bh = block["w"], block["h"]
    cx, cy = left + bw / 2, top + bh / 2
    # Tail from the oval's edge toward the speaker (their face, or straight down).
    tx, ty = tail_to if tail_to else (cx, top + bh + size * 2)
    angle = math.atan2(ty - cy, tx - cx)
    ex, ey = cx + math.cos(angle) * bw / 2 * 0.92, cy + math.sin(angle) * bh / 2 * 0.92
    length = min(size * 1.4, max(size * 0.6, math.hypot(tx - ex, ty - ey) * 0.5))
    tip = (ex + math.cos(angle) * length, ey + math.sin(angle) * length)
    nx, ny = -math.sin(angle) * size * 0.32, math.cos(angle) * size * 0.32
    base = [(ex + nx, ey + ny), (ex - nx, ey - ny)]
    draw.polygon([base[0], base[1], tip], fill=(255, 255, 255), outline=(0, 0, 0))
    draw.ellipse((left, top, left + bw, top + bh), fill=(255, 255, 255), outline=(0, 0, 0), width=3)
    inner = [
        (ex + nx * 0.8 - math.cos(angle) * 6, ey + ny * 0.8 - math.sin(angle) * 6),
        (ex - nx * 0.8 - math.cos(angle) * 6, ey - ny * 0.8 - math.sin(angle) * 6),
        (tip[0] - math.cos(angle) * 5, tip[1] - math.sin(angle) * 5),
    ]
    draw.polygon(inner, fill=(255, 255, 255))
    font = ImageFont.truetype(font_path, size)
    draw_vertical(page, left + block["pad_x"] + block["text_w"] - int(size * 0.125), top + block["pad_y"], block["cols"], font, size)
    return (left, top, left + bw, top + bh)


def narration_box(page, box, text, font_path, size):
    x0, y0, w, h = box
    cols = columns_of(text, 10)
    if not cols:
        return None
    font = ImageFont.truetype(font_path, size)
    text_w = len(cols) * int(size * 1.25)
    text_h = max(len(c) for c in cols) * int(size * 1.08)
    pad = int(size * 0.5)
    left, top = x0 + 8, y0 + 8
    rect = (left, top, left + text_w + 2 * pad, top + text_h + 2 * pad)
    draw = ImageDraw.Draw(page)
    draw.rectangle(rect, fill=(255, 255, 255), outline=(0, 0, 0), width=2)
    draw_vertical(page, left + pad + text_w - int(size * 0.125), top + pad, cols, font, size)
    return rect


def letter_panel(page, box, panel, font_path, size, max_rows, faces):
    """Narration and speech bubbles for one panel already pasted at box."""
    taken = []
    if panel.get("narration"):
        rect = narration_box(page, box, panel["narration"], font_path, max(22, size - 6))
        if rect:
            taken.append(rect)
    speakers = panel.get("speakers") or {}
    lines = [l for l in panel.get("lines", []) if (l.get("text") or "").strip()][:3]
    x0, y0, w, h = box
    for i, line in enumerate(lines):
        block = text_block(line["text"], size, max_rows)
        if not block:
            continue
        side = speakers.get(line.get("who", ""))
        # A voice from outside the picture sits against an edge (right first, as text reads).
        prefer = SIDE_X[side] if side else (0.95 if i % 2 == 0 else 0.05)
        left, top = place_bubble(box, block["w"], block["h"], faces, taken, prefer)
        # Point at the face nearest the speaker's side, or out of the panel for an off-screen voice.
        target = None
        if side and faces:
            sx = x0 + SIDE_X[side] * w
            face = min(faces, key=lambda f: abs((f[0] + f[2]) / 2 - sx))
            target = ((face[0] + face[2]) / 2, (face[1] + face[3]) / 2)
        elif not side:
            centre = left + block["w"] / 2
            target = (x0 + w + size * 4, top + block["h"]) if centre > x0 + w / 2 else (x0 - size * 4, top + block["h"])
        taken.append(draw_bubble(page, left, top, block, font_path, size, target))


def faces_in(image, offset):
    ox, oy = offset
    return [(a + ox, b + oy, c + ox, d + oy) for a, b, c, d in detect_faces(image)]


# ---------- pages ----------


def compose_page(spec):
    layout = spec.get("layout", "4koma")
    panels = spec["panels"]
    boxes, (pw, ph) = page_boxes(layout, len(panels))
    page = Image.new("RGB", (pw, ph), (255, 255, 255))
    draw = ImageDraw.Draw(page)
    title = (spec.get("title") or "").strip()
    if title:
        tfont = ImageFont.truetype(spec["fontBold"], 44 if layout == "4koma" else 52)
        tw = draw.textlength(title, font=tfont)
        draw.text(((pw - tw) / 2, 40 if layout == "4koma" else 60), title, font=tfont, fill=(0, 0, 0))
    for panel, box in zip(panels, boxes):
        x0, y0, w, h = box
        art = cover(Image.open(panel["image"]).convert("RGB"), w, h)
        page.paste(art, (x0, y0))
        size = 30 if layout == "4koma" else max(30, min(40, int(min(w, h) / 22)))
        max_rows = max(5, min(10, int(h * 0.55 / (size * 1.08))))
        letter_panel(page, box, panel, spec["font"], size, max_rows, faces_in(art, (x0, y0)))
        draw.rectangle((x0, y0, x0 + w - 1, y0 + h - 1), outline=(0, 0, 0), width=5)
    page.save(spec["out"])
    print("RESULT " + spec["out"], flush=True)


WEBTOON_WIDTH = 900
WEBTOON_GAP = 110
WEBTOON_PANELS_PER_PART = 6


def webtoon_panel(panel, font_path):
    """One full-width webtoon panel with its bubbles, as an image."""
    image = Image.open(panel["image"]).convert("RGB")
    height = int(image.height * WEBTOON_WIDTH / image.width)
    canvas = cover(image, WEBTOON_WIDTH, height)
    box = (0, 0, WEBTOON_WIDTH, height)
    size = 32
    max_rows = max(5, min(11, int(height * 0.5 / (size * 1.08))))
    letter_panel(canvas, box, panel, font_path, size, max_rows, faces_in(canvas, (0, 0)))
    return canvas


def webtoon_header(spec, font_path):
    title = (spec.get("title") or "").strip()
    episode = (spec.get("episodeTitle") or "").strip()
    header = Image.new("RGB", (WEBTOON_WIDTH, 260), (255, 255, 255))
    draw = ImageDraw.Draw(header)
    big = ImageFont.truetype(font_path, 54)
    small = ImageFont.truetype(font_path, 36)
    for text, font, y in ((title, big, 60), (episode, small, 150)):
        if text:
            w = draw.textlength(text, font=font)
            draw.text(((WEBTOON_WIDTH - w) / 2, y), text, font=font, fill=(0, 0, 0))
    return header


def compose_webtoon(spec):
    """Stacks panels into vertical strips of a few panels each: <out>-1.png, <out>-2.png, ..."""
    font_path = spec["font"]
    blocks = [webtoon_panel(p, font_path) for p in spec["panels"]]
    out = spec["out"]
    stem = out[:-4] if out.lower().endswith(".png") else out
    number_font = ImageFont.truetype(font_path, 20)
    files = []
    for part, start in enumerate(range(0, len(blocks), WEBTOON_PANELS_PER_PART)):
        group = blocks[start:start + WEBTOON_PANELS_PER_PART]
        header = [webtoon_header(spec, spec.get("fontBold", font_path))] if part == 0 else []
        pieces = header + group
        height = sum(p.height for p in pieces) + WEBTOON_GAP * (len(pieces) + 1)
        strip = Image.new("RGB", (WEBTOON_WIDTH, height), (255, 255, 255))
        draw = ImageDraw.Draw(strip)
        y = WEBTOON_GAP
        for index, piece in enumerate(pieces):
            strip.paste(piece, (0, y))
            y += piece.height
            # A small panel number in the gap, for "redraw panel N".
            number = start + index - len(header) + 1
            if number >= 1:
                draw.text((WEBTOON_WIDTH - 60, y + 8), f"{number}", font=number_font, fill=(190, 190, 190))
            y += WEBTOON_GAP
        name = f"{stem}-{part + 1}.png"
        strip.save(name)
        files.append(name)
    print("RESULT " + json.dumps(files), flush=True)


def make_icon(source, target, size=256):
    """Square face crop of a character sheet: the largest face with room around it, or the upper
    middle of the picture when no face is found."""
    image = Image.open(source).convert("RGB")
    faces = detect_faces(image)
    if faces:
        x0, y0, x1, y1 = max(faces, key=lambda f: (f[2] - f[0]) * (f[3] - f[1]))
        side = max(x1 - x0, y1 - y0) * 1.9
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + (y1 - y0) * 0.1
    else:
        side = min(image.width, image.height) * 0.5
        cx, cy = image.width / 2, image.height * 0.22
    side = min(side, image.width, image.height)
    left = max(0, min(image.width - side, cx - side / 2))
    top = max(0, min(image.height - side, cy - side / 2))
    icon = image.crop((int(left), int(top), int(left + side), int(top + side))).resize((size, size), Image.LANCZOS)
    icon.save(target)
    print("RESULT " + target, flush=True)


def main():
    if len(sys.argv) >= 4 and sys.argv[1] == "--icon":
        make_icon(sys.argv[2], sys.argv[3])
        return
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    if spec.get("layout") == "webtoon":
        compose_webtoon(spec)
    else:
        compose_page(spec)


if __name__ == "__main__":
    main()
