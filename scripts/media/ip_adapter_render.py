"""SDXL reference conditioning, isolated from the resident regional-attention worker."""

import json
import os
import sys
from pathlib import Path

os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

import torch
from PIL import Image, ImageOps
from diffusers import StableDiffusionXLPipeline, DPMSolverMultistepScheduler
from transformers import CLIPVisionModelWithProjection
from external.sdxl_server import encode_pair


def reference(filename):
    # CLIP's square center crop otherwise loses the head, feet and accessories.
    with Image.open(filename) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        return ImageOps.pad(image, (512, 512), color="white", method=Image.Resampling.LANCZOS)


def pipeline(request):
    assets = Path(request["assets"])
    encoder = CLIPVisionModelWithProjection.from_pretrained(
        assets / "models/image_encoder", torch_dtype=torch.float16,
        local_files_only=True, use_safetensors=True,
    )
    pipe = StableDiffusionXLPipeline.from_single_file(
        request["model"], torch_dtype=torch.float16, use_safetensors=True,
        local_files_only=True, image_encoder=encoder,
    )
    pipe.load_ip_adapter(
        str(assets), subfolder="sdxl_models",
        weight_name="ip-adapter-plus_sdxl_vit-h.safetensors", image_encoder_folder=None,
        local_files_only=True,
    )
    pipe.set_ip_adapter_scale(float(request.get("scale", 0.65)))
    pipe.scheduler = DPMSolverMultistepScheduler.from_config(
        pipe.scheduler.config, algorithm_type="sde-dpmsolver++", use_karras_sigmas=True,
    )
    return pipe


def render(request):
    pipe = pipeline(request)
    pipe.enable_model_cpu_offload()
    pipe.enable_vae_tiling()
    pipe.vae.config.force_upcast = True
    pe, ne, pp, np = encode_pair(pipe, request["prompt"], request["negative"])
    images = [reference(filename) for filename in request["references"]]
    # A single adapter consumes several views of the same target as one image batch.
    seed = int(request.get("seed", -1))
    if seed < 0:
        seed = int(torch.randint(0, 2**31 - 1, (1,)).item())
    steps = int(request.get("steps", 30))

    def progress(_pipe, step, _timestep, kwargs):
        print(f"STEP {step + 1}/{steps}", flush=True)
        return kwargs

    output = pipe(
        prompt_embeds=pe, negative_prompt_embeds=ne,
        pooled_prompt_embeds=pp, negative_pooled_prompt_embeds=np,
        ip_adapter_image=[images], width=int(request.get("width", 832)),
        height=int(request.get("height", 1216)), num_inference_steps=steps,
        guidance_scale=float(request.get("cfg", 5)),
        generator=torch.Generator("cuda").manual_seed(seed), callback_on_step_end=progress,
    ).images[0]
    output.save(request["output"])
    print(f"RESULT {request['output']}", flush=True)


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as stream:
        request = json.load(stream)
    if "--validate" in sys.argv[2:]:
        pipe = pipeline(request)
        print(f"READY {type(pipe.unet.encoder_hid_proj).__name__}", flush=True)
    else:
        render(request)
