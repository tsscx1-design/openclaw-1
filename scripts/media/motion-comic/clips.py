"""Makes every clip of a motion comic with Wan 2.2 Remix (sd.cpp), first/last frame guided.
Usage: python clips.py <folder> [clips.json]  (a list: [{"name","start","end","prompt","seed","frames"}])"""
import json, os, subprocess, sys, time
sp = sys.argv[1]
V = r"D:\media-models\video"
SD = os.path.expanduser(r"~\.openclaw\image\bin-cuda\sd-cli.exe")
NEG = ("static, still image, blurry, low quality, distorted, deformed, extra limbs, flicker, watermark, text, "
       "camera movement, zoom, scene change, child, loli, shota, underage, young, petite, flat chest, kid")
for c in json.load(open(os.path.join(sp, sys.argv[2] if len(sys.argv) > 2 else "clips.json"), encoding="utf-8")):
    out = os.path.join(sp, c["name"] + ".webm")
    if os.path.exists(out):
        continue
    pending = os.path.join(sp, c["name"] + ".partial.webm")
    if os.path.exists(pending):
        os.remove(pending)
    t = time.time()
    r = subprocess.run([SD, "-M", "vid_gen",
        "--diffusion-model", V + r"\remix\wan22RemixT2VI2V_i2vLowV30-Q3_K_M.gguf",
        "--high-noise-diffusion-model", V + r"\remix\wan22RemixT2VI2V_i2vHighV30-Q3_K_M.gguf",
        "--steps", "6", "--high-noise-steps", "3", "--cfg-scale", "1", "--high-noise-cfg-scale", "1",
        "--sampling-method", "euler", "--high-noise-sampling-method", "euler", "--flow-shift", "5",
        "--vae", V + r"\wan_2.1_vae.safetensors", "--t5xxl", V + r"\umt5-xxl-encoder-Q8_0.gguf",
        "-i", os.path.join(sp, c["start"]), "--end-img", os.path.join(sp, c["end"]),
        "-p", c["prompt"], "-n", NEG, "-W", "832", "-H", "480", "--video-frames", str(c.get("frames", 49)),
        "--fps", "16", "--offload-to-cpu", "--diffusion-fa", "--vae-tiling", "-s", str(c.get("seed", 42)), "-o", pending],
        capture_output=True, text=True, errors="replace")
    if r.returncode:
        raise SystemExit("FAILED " + c["name"] + ": " + (r.stderr[-1200:] or r.stdout[-1200:]))
    if not os.path.isfile(pending) or os.path.getsize(pending) == 0:
        raise SystemExit("FAILED " + c["name"] + ": 動画ファイルが作られませんでした")
    os.replace(pending, out)
    print(c["name"], "ok", f"{time.time() - t:.0f}s", flush=True)
print("ALL_DONE", flush=True)
