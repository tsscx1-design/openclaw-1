"""Joins motion-comic clips into one film: loops each clip to its length, smooths it (RIFE x2),
scales it up, adds dialogue text, fades, a white flash at the climax, voices, breaths and sound
effects. Usage: python mc_compose.py <folder> [plan.json]

plan.json:
{"name": "クロエ", "out": "作品.mp4",
 "cuts": [{"clip": "c1-slow.webm", "loop": true, "seconds": 14,
           "lines": [{"wav": "line-0.wav", "text": "…ふぅん。😪 するの？"}],
           "breath": "breath-0.wav", "sfx": [{"wav": "x.wav", "gain": 0.5}],
           "fade": "in" | "out" | "both", "flash": false,
           "insert": [x0, y0, x1, y1]  (optional 0.5 s close-up of that area in the middle)}]}
A loop cut lasts "seconds" (at least as long as its lines); a change cut plays once."""

import json
import hashlib
import math
import os
import re
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sp = os.path.abspath(sys.argv[1])
plan = json.load(open(os.path.join(sp, sys.argv[2] if len(sys.argv) > 2 else "plan.json"), encoding="utf-8"))
FF = r"D:\media-tools\Wan2GP\ffmpeg_bins\ffmpeg.exe"
FP = r"D:\media-tools\Wan2GP\ffmpeg_bins\ffprobe.exe"
PY = r"D:\media-tools\Wan2GP\venv\Scripts\python.exe"
RIFE = r"D:\media-tools\image\rife_video.py"
FONT = r"C:\Windows\Fonts\BIZ-UDGothicB.ttc"
W, H, FPS = 1280, 720, 32
work = os.path.join(sp, "_compose")
os.makedirs(work, exist_ok=True)


def p(name):
    return name if os.path.isabs(name) else os.path.join(sp, name)


def run(args):
    r = subprocess.run(args, capture_output=True, text=True, errors="replace")
    if r.returncode:
        raise SystemExit(f"failed: {' '.join(args[:6])}…\n{r.stderr[-1500:]}")
    return r.stdout


def media_len(path):
    if path.lower().endswith(".wav"):
        with wave.open(path) as w:
            return w.getnframes() / w.getframerate()
    return float(run([FP, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path]).strip())


def source_digest(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


rife_digest = source_digest(RIFE)


# ---------- 1. every cut as a 1280x720, 32 fps piece of the right length ----------
pieces, events, overlays = [], [], []
t = 0.0
for k, cut in enumerate(plan["cuts"]):
    # The intermediate belongs to the exact source bytes and interpolation script.
    # Reordered/repeated cuts reuse it; redrawn cuts cannot pick up the old frames.
    identity = source_digest(p(cut["clip"])) + rife_digest + "|rife-x2"
    smooth = os.path.join(work, "smooth-" + hashlib.sha256(identity.encode()).hexdigest() + ".mp4")
    if not os.path.exists(smooth):
        pending = smooth + ".partial.mp4"
        if os.path.exists(pending):
            os.remove(pending)
        run([PY, RIFE, p(cut["clip"]), pending, "2"])
        if not os.path.isfile(pending) or os.path.getsize(pending) == 0:
            raise SystemExit("failed: interpolation produced no video")
        os.replace(pending, smooth)
    clip_len = media_len(smooth)
    lines = cut.get("lines", [])
    need = 0.6 + sum(media_len(p(l["wav"])) + 0.45 for l in lines) + 0.6
    if cut.get("loop", True):
        seconds = max(float(cut.get("seconds", clip_len)), need)
    else:
        seconds = max(clip_len, float(cut.get("seconds", 0)))
    repeats = max(0, math.ceil(seconds / clip_len) - 1)
    # Slow camera all the time (the reference films almost never hold still): even cuts pull back
    # from a little closer, odd cuts drift sideways. Rendered from a 1.5x frame so it stays sharp.
    frames = max(1, int(seconds * FPS))
    if k % 2 == 0:
        z, x = f"1.12-0.12*on/{frames}", "iw/2-(iw/zoom/2)"
    else:
        z, x = "1.08", f"(iw-iw/zoom)*(0.15+0.7*on/{frames})"
    vf = [f"fps={FPS}"]
    if not cut.get("loop", True) and seconds > clip_len:
        vf.append(f"tpad=stop_mode=clone:stop_duration={seconds - clip_len:.2f}")
    vf += [f"scale={W * 3 // 2}:{H * 3 // 2}:flags=lanczos",
           f"zoompan=z='{z}':x='{x}':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps={FPS}"]
    fade = cut.get("fade", "")
    if fade in ("in", "both"):
        vf.append("fade=t=in:st=0:d=0.8")
    if fade in ("out", "both"):
        vf.append(f"fade=t=out:st={seconds - 0.9:.2f}:d=0.9")
    if cut.get("flash"):
        vf.append("fade=t=in:st=0:d=0.35:color=white")

    def render(out, start, length, filters):
        run([FF, "-y", "-loglevel", "error", "-stream_loop", str(repeats), "-ss", f"{start:.3f}", "-i", smooth,
             "-t", f"{length:.3f}", "-vf", ",".join(filters), "-an", "-c:v", "libx264", "-preset", "medium",
             "-crf", "16", "-pix_fmt", "yuv420p", "-r", str(FPS), out])

    piece = os.path.join(work, f"p{k}.mp4")
    render(piece, 0, seconds, vf)
    # A half-second close-up (face or joined part) cut into the middle, as the reference films do.
    insert = cut.get("insert")
    if insert and seconds > 4:
        x0, y0, x1, y1 = insert
        mid = seconds / 2
        close = [f"fps={FPS}", f"crop=iw*{x1 - x0:.3f}:ih*{y1 - y0:.3f}:iw*{x0:.3f}:ih*{y0:.3f}",
                 f"scale={W}:{H}:flags=lanczos,setsar=1"]
        a, b, c = (os.path.join(work, f"p{k}{part}.mp4") for part in "abc")
        render(a, 0, mid - 0.25, vf[:-1] if cut.get("flash") else vf)
        render(b, mid - 0.25, 0.5, close)
        # The second half keeps the camera path: trim the full piece instead of re-rendering it.
        run([FF, "-y", "-loglevel", "error", "-ss", f"{mid + 0.25:.3f}", "-i", piece, "-an", "-c:v", "libx264",
             "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", "-r", str(FPS), c])
        pieces += [a, b, c]
    else:
        pieces.append(piece)

    # Lines one after another, breaths filling the rest of the cut, effects under all of it.
    at = t + 0.6
    for line in lines:
        d = media_len(p(line["wav"]))
        events.append((at, p(line["wav"]), float(line.get("gain", 1.0))))
        if line.get("text"):
            overlays.append((at - 0.1, at + d + 0.35, line["text"]))
        at += d + 0.45
    if cut.get("breath"):
        bd = media_len(p(cut["breath"]))
        while at + bd < t + seconds - 0.3:
            events.append((at, p(cut["breath"]), float(cut.get("breathGain", 0.7))))
            at += bd + 0.3
    for fx in cut.get("sfx", []):
        fd = media_len(p(fx["wav"]))
        s = t + float(fx.get("offset", 0))
        while s < t + seconds - 0.2:
            events.append((s, p(fx["wav"]), float(fx.get("gain", 0.5)), t + seconds - s))
            s += fd
    t += seconds
total = t
print(f"length {total:.1f}s, {len(pieces)} cuts", flush=True)

# ---------- 2. join the pieces ----------
lst = os.path.join(work, "list.txt")
with open(lst, "w", encoding="utf-8") as f:
    for piece in pieces:
        f.write(f"file '{piece.replace(os.sep, '/')}'\n")
joined = os.path.join(work, "joined.mp4")
run([FF, "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", joined])

# ---------- 3. text box overlays ----------
EMOJI = re.compile(r"[\U0001F000-\U0001FAFF\u2600-\u27BF\u2B00-\u2BFF\uFE0F\u200D\u23E9-\u23FA]")
font = ImageFont.truetype(FONT, 34)
name_font = ImageFont.truetype(FONT, 28)
ov_inputs, ov_chain = [], []
last = "0:v"
for i, (s, e, text) in enumerate(overlays):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    box_h = 150
    for y in range(box_h):
        d.line([(0, H - box_h + y), (W, H - box_h + y)], fill=(10, 5, 15, int(170 * min(1, y / 30))))
    d.text((70, H - box_h + 14), plan.get("name", ""), font=name_font, fill=(255, 150, 190, 255))
    clean = EMOJI.sub("", text).replace("  ", " ").strip()
    d.text((70, H - box_h + 56), f"「{clean}」", font=font, fill=(255, 255, 255, 255), stroke_width=2, stroke_fill=(60, 20, 40, 255))
    path = os.path.join(work, f"t{i}.png")
    img.save(path)
    ov_inputs += ["-i", path]
    ov_chain.append(f"[{last}][{i + 1}:v]overlay=enable='between(t,{s:.2f},{e:.2f})'[v{i}]")
    last = f"v{i}"

# ---------- 4. sound ----------
au_inputs, au_chain, labels = [], [], []
base = 1 + len(overlays)
for k, ev in enumerate(events):
    s, path, gain = ev[0], ev[1], ev[2]
    au_inputs += ["-i", path]
    ms = int(s * 1000)
    trim = f",atrim=0:{ev[3]:.2f}" if len(ev) > 3 else ""
    au_chain.append(f"[{base + k}:a]aresample=48000,aformat=channel_layouts=stereo{trim},volume={gain},adelay={ms}|{ms}[a{k}]")
    labels.append(f"[a{k}]")
graph = ov_chain + au_chain
graph.append(f"{''.join(labels)}amix=inputs={len(labels)}:normalize=0,alimiter=limit=0.95,apad,atrim=duration={total:.2f}[a]")
out = p(plan.get("out", "作品.mp4"))
video_codec = ["-c:v", "libx264", "-preset", "medium", "-crf", "16"] if overlays else ["-c:v", "copy"]
run([FF, "-y", "-loglevel", "error", "-i", joined, *ov_inputs, *au_inputs, "-filter_complex", ";".join(graph),
     "-map", f"[{last}]" if overlays else "0:v", "-map", "[a]", *video_codec,
     "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", out])
print(f"完成 {out} {total:.1f}秒", flush=True)
