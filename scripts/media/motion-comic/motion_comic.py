"""Runs one motion comic for the OpenClaw media studio: key images -> video cuts -> film.
Usage: python motion_comic.py <job.json>
job.json (written by the gateway, src/gateway/motion-comic.ts):
  {"dir": "...", "model": "...safetensors", "negative": "...",
   "keys": [{"id","mode","prompt","src","strength","mask","seed"}],
   "cuts": [{"name","start","end","prompt","seed"}],
   "plan": {...mc_compose plan...}}
Prints "STAGE <text>" lines for the app and "RESULT <file>" at the end. Finished pieces are kept,
so running it again continues where it stopped."""

import json
import os
import subprocess
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable
job = json.load(open(sys.argv[1], encoding="utf-8"))
sp = job["dir"]
os.makedirs(sp, exist_ok=True)
W, H = 1344, 768


def stage(text):
    print("STAGE " + text, flush=True)


def run(args):
    r = subprocess.run(args, cwd=HERE, capture_output=True, text=True, errors="replace")
    if r.returncode:
        raise SystemExit(f"ERROR {os.path.basename(args[1])}: {r.stderr[-1200:] or r.stdout[-1200:]}")
    return r.stdout


def images(keys, label):
    todo = [k for k in keys if not os.path.exists(os.path.join(sp, k["id"] + ".png"))]
    if not todo:
        return
    stage(f"{label}を描いています（{len(todo)}枚）")
    jobs = []
    for k in todo:
        entry = {"mode": k["mode"], "prompt": k["prompt"], "seed": k["seed"], "out": k["id"] + ".png"}
        if k.get("src"):
            entry["src"] = k["src"] + ".png"
        if k.get("strength"):
            entry["strength"] = k["strength"]
        if k.get("mask") == "mouth":
            entry["mask"] = "mouth"
        elif k.get("mask"):
            entry["mask"] = k["mask"]
        jobs.append(entry)
    path = os.path.join(sp, f"images-{todo[0]['id']}.json")
    json.dump({"model": job["model"], "negative": job["negative"], "width": W, "height": H, "jobs": jobs,
               "loras": job.get("loras", [])},
              open(path, "w", encoding="utf-8"), ensure_ascii=False)
    run([PY, os.path.join(HERE, "mc_image.py"), path])


keys = job["keys"]
if job.get("reuseCutsOnly"):
    required = [k["id"] + ".png" for k in keys] + [c["name"] + ".webm" for c in job["cuts"]]
    if any(not os.path.isfile(os.path.join(sp, name)) for name in required):
        raise SystemExit("ERROR 元作品の素材が不足しています。映像を再生成せずに中止しました")
if job.get("previewOnly"):
    keys = [k for k in keys if k["id"] == "k1"]
# Source pictures precede their derivatives, and share one loaded model.
images(keys, "元の絵と差分")
for k in keys:
    small = os.path.join(sp, k["id"] + "-v.png")
    if not os.path.exists(small):
        Image.open(os.path.join(sp, k["id"] + ".png")).convert("RGB").resize((832, 480), Image.LANCZOS).save(small)

if job.get("stopAfterKeys"):
    # A draft: the key images on one sheet, labelled, for approval before the video.
    from PIL import ImageFont
    labels = {"k1": "k1 元絵", "k2": "k2 山場", "k3": "k3 白濁液", "k4": "k4 抜いた後（垂れ）", "f1": "f1 フェラ", "f2": "f2 口内"}
    ids = [k["id"] for k in keys]
    thumbs = [Image.open(os.path.join(sp, i + ".png")).convert("RGB").resize((672, 384)) for i in ids]
    cols = 2
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (672 * cols, 424 * rows), (20, 20, 24))
    font = ImageFont.truetype(r"C:\Windows\Fonts\BIZ-UDGothicB.ttc", 26)
    draw = ImageDraw.Draw(sheet)
    for n, (i, im) in enumerate(zip(ids, thumbs)):
        x, y = (n % cols) * 672, (n // cols) * 424
        sheet.paste(im, (x, y + 40))
        draw.text((x + 12, y + 6), labels.get(i, i), font=font, fill=(255, 255, 255))
    preview = os.path.join(sp, "draft.png")
    sheet.save(preview)
    print("RESULT " + preview, flush=True)
    sys.exit(0)

clips = [dict(c, start=c["start"] + "-v.png", end=c["end"] + "-v.png") for c in job["cuts"]]
json.dump(clips, open(os.path.join(sp, "clips.json"), "w", encoding="utf-8"), ensure_ascii=False)
for i, c in enumerate(clips):
    if os.path.exists(os.path.join(sp, c["name"] + ".webm")):
        continue
    stage(f"動画AIでカットを作っています（{i + 1}/{len(clips)}、1カット約11分）")
    # clips.py makes every missing cut in order; one call per cut keeps the progress honest.
    one = os.path.join(sp, "clip-one.json")
    json.dump([c], open(one, "w", encoding="utf-8"), ensure_ascii=False)
    out = run([PY, os.path.join(HERE, "clips.py"), sp, "clip-one.json"])
    if "FAILED" in out:
        raise SystemExit("ERROR " + out[-800:])

stage("声と効果音をつないで仕上げています")
json.dump(job["plan"], open(os.path.join(sp, "plan.json"), "w", encoding="utf-8"), ensure_ascii=False)
run([PY, os.path.join(HERE, "mc_compose.py"), sp, "plan.json"])
print("RESULT " + os.path.join(sp, job["plan"]["out"]), flush=True)
