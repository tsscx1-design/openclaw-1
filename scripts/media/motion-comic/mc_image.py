"""Key images for a motion comic: one base CG, pose variants of it and an inpainted cum version.
Usage: python mc_image.py <jobs.json>
jobs.json: {"model": "...safetensors", "negative": "...", "width": 1344, "height": 768,
            "jobs": [{"mode": "t2i", "prompt": "...", "seed": 1, "out": "a.png"},
                     {"mode": "i2i", "src": "a.png", "prompt": "...", "strength": 0.6, "seed": 2, "out": "b.png"},
                     {"mode": "inpaint", "src": "a.png", "mask": [x0, y0, x1, y1] (0..1) | "mask.png",
                      "prompt": "...", "strength": 0.75, "seed": 3, "out": "c.png"}]}
All jobs share one loaded model (weights in RAM, CPU offload)."""

import json
import os
import sys

os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

import torch
from diffusers import (
    EulerAncestralDiscreteScheduler,
    StableDiffusionXLImg2ImgPipeline,
    StableDiffusionXLInpaintPipeline,
    StableDiffusionXLPipeline,
)
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, r"D:\media-tools\image")
from sdxl_server import encode_chunks, split_prompt  # noqa: E402  (prompts longer than 77 tokens)

cfg = json.load(open(sys.argv[1], encoding="utf-8"))
base = os.path.dirname(os.path.abspath(sys.argv[1]))
pipe = StableDiffusionXLPipeline.from_single_file(cfg["model"], torch_dtype=torch.float16)
pipe.scheduler = EulerAncestralDiscreteScheduler.from_config(pipe.scheduler.config)
pipe.enable_model_cpu_offload()
# Character LoRAs (the trained face of a library character), applied to every key image.
loras = [l for l in cfg.get("loras", []) if l.get("path") and os.path.exists(l["path"])]
if loras:
    names = []
    for n, l in enumerate(loras):
        pipe.load_lora_weights(l["path"], adapter_name=f"char{n}")
        names.append(f"char{n}")
    pipe.set_adapters(names, adapter_weights=[float(l.get("scale", 0.8)) for l in loras])
i2i = StableDiffusionXLImg2ImgPipeline(**pipe.components)
inp = StableDiffusionXLInpaintPipeline(**pipe.components)
W, H = int(cfg.get("width", 1344)), int(cfg.get("height", 768))


def embeds(prompt, negative):
    pos, pos_pooled = encode_chunks(pipe, split_prompt(pipe, prompt))
    if negative not in negative_embeddings:
        negative_embeddings[negative] = encode_chunks(pipe, split_prompt(pipe, negative))
    neg, neg_pooled = negative_embeddings[negative]
    # Both sides need the same number of 77-token chunks.
    n = max(pos.shape[1], neg.shape[1])
    pad = lambda e: torch.cat([e, e[:, -77:].repeat(1, (n - e.shape[1]) // 77, 1)], 1) if e.shape[1] < n else e  # noqa: E731
    return dict(prompt_embeds=pad(pos), pooled_prompt_embeds=pos_pooled,
                negative_prompt_embeds=pad(neg), negative_pooled_prompt_embeds=neg_pooled)


def path(p):
    return p if os.path.isabs(p) else os.path.join(base, p)


negative_embeddings = {}

def mouth_mask(src):
    # Detection happens after the source is drawn, within the same model session.
    from manga_compose import detect_faces

    faces = detect_faces(src)
    if not faces:
        raise ValueError("口の位置を検出できません。元絵を描き直してください")
    x0, y0, x1, y1 = max(faces, key=lambda f: (f[2] - f[0]) * (f[3] - f[1]))
    fw, fh = x1 - x0, y1 - y0
    mask = Image.new("L", src.size, 0)
    ImageDraw.Draw(mask).ellipse([x0 - fw * 0.25, y0 + fh * 0.4, x1 + fw * 0.25, y1 + fh * 0.6], fill=255)
    return mask.filter(ImageFilter.GaussianBlur(18))


with torch.no_grad():
    for job in cfg["jobs"]:
        gen = torch.Generator("cpu").manual_seed(int(job.get("seed", 0)))
        e = embeds(job["prompt"], job.get("negative", cfg.get("negative", "")))
        common = dict(num_inference_steps=int(job.get("steps", 28)), guidance_scale=float(job.get("cfg", 6.0)), generator=gen, **e)
        if job["mode"] == "t2i":
            img = pipe(width=W, height=H, **common).images[0]
        elif job["mode"] == "i2i":
            src = Image.open(path(job["src"])).convert("RGB").resize((W, H))
            img = i2i(image=src, strength=float(job.get("strength", 0.6)), **common).images[0]
        else:
            src = Image.open(path(job["src"])).convert("RGB").resize((W, H))
            m = job["mask"]
            if m == "mouth":
                mask = mouth_mask(src)
            elif isinstance(m, str):
                mask = Image.open(path(m)).convert("L").resize((W, H))
            else:
                mask = Image.new("L", (W, H), 0)
                ImageDraw.Draw(mask).ellipse([m[0] * W, m[1] * H, m[2] * W, m[3] * H], fill=255)
                mask = mask.filter(ImageFilter.GaussianBlur(24))
            img = inp(image=src, mask_image=mask, strength=float(job.get("strength", 0.75)), width=W, height=H, **common).images[0]
            # Keep everything outside the mask pixel-identical to the source.
            img = Image.composite(img, src, mask)
        img.save(path(job["out"]))
        print("RESULT", job["out"], flush=True)
