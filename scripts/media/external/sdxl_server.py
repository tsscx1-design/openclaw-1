"""Long-lived SDXL worker for the OpenClaw media studio (PyTorch / diffusers on the GPU).

The gateway starts it for image jobs and keeps it while jobs keep coming, so each image skips
the ~15 s of Python start-up and model loading. It reads one JSON request per line on stdin:
  {"id": "...", "model": "D:/.../x.safetensors", "vpred": false, "prompt": "...",
   "negative": "...", "width": 768, "height": 1344, "steps": 28, "cfg": 6.0, "seed": -1,
   "sampler": "euler_a" | "dpmpp_2m_sde_karras" | "lcm",
   "lora": "D:/.../dmd2.safetensors" | null, "lora_scale": 1.0,
   "loras": [{"path": "D:/.../char.safetensors", "scale": 0.8}],          (optional, added to lora)
   "regions": [{"prompt": "1girl, elf, ...", "x0": 0.0, "x1": 0.5}],     (optional)
   "output": "C:/.../out.png"}
and answers on stdout with "<id> STEP i/n", "<id> RESULT <path>" or "<id> ERROR <message>".
It exits when stdin closes. Weights stay in system RAM between images (CPU offload), so the GPU
holds only the CUDA context while idle. Nothing is served on the network.

Prompts longer than CLIP's 77 tokens are encoded in 75-token chunks and joined, so tags at the end
(a character's outfit, the background) are not silently dropped. "regions" draws different people
side by side: inside each region the cross-attention reads that region's prompt (mixed with a
little of the whole prompt), so hair colours and outfits do not bleed between characters.
"""

import json
import math
import os
import random
import sys
import traceback

# 8 GB is tight for the SDXL U-Net plus activations; fragmentation alone can push a step into
# shared system memory (several times slower).
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

import torch
import torch.nn.functional as F
from diffusers import (
    DPMSolverMultistepScheduler,
    EulerAncestralDiscreteScheduler,
    LCMScheduler,
    StableDiffusionXLPipeline,
)
from diffusers.models.attention_processor import AttnProcessor2_0

_pipes: dict[tuple[str, bool], StableDiffusionXLPipeline] = {}
# Scheduler config as shipped with each checkpoint; every sampler is derived from it.
_base_config: dict[tuple[str, bool], dict] = {}
# LoRA adapters already loaded into each pipeline.
_loras: dict[tuple[str, bool], set[str]] = {}
# The U-Net weights as loaded (CPU, fp16), so a fused LoRA can be taken out again.
_base_unet: dict[tuple[str, bool], dict] = {}
# The sampling LoRA (DMD2) fused into the U-Net weights, if any: (path, scale).
_fused: dict[tuple[str, bool], tuple[str, float] | None] = {}

CHUNK_TOKENS = 75
MAX_CHUNKS = 4
# Share of the whole-picture prompt kept inside a character's region (scene, light, style).
REGION_BASE_WEIGHT = 0.25


def pipeline(model: str, vpred: bool) -> StableDiffusionXLPipeline:
    key = (model, vpred)
    if key in _pipes:
        return _pipes[key]
    # One checkpoint at a time keeps RAM use near 8 GB.
    _pipes.clear()
    _base_config.clear()
    _loras.clear()
    _base_unet.clear()
    _fused.clear()
    torch.cuda.empty_cache()
    pipe = StableDiffusionXLPipeline.from_single_file(model, torch_dtype=torch.float16, use_safetensors=True)
    _base_config[key] = dict(pipe.scheduler.config)
    _base_unet[key] = {k: v.detach().clone() for k, v in pipe.unet.state_dict().items()}
    _fused[key] = None
    # 8 GB: only the component in use sits on the GPU; loading everything at once spills into
    # shared system memory and sampling becomes ~10x slower.
    pipe.enable_model_cpu_offload()
    # The SDXL fp16 VAE can overflow to black images; let diffusers upcast it for decoding.
    pipe.vae.config.force_upcast = True
    pipe.enable_vae_tiling()
    _pipes[key] = pipe
    _loras[key] = set()
    return pipe


def set_scheduler(pipe: StableDiffusionXLPipeline, key: tuple[str, bool], sampler: str) -> None:
    config = _base_config[key]
    # V-prediction checkpoints (NoobAI-XL V-Pred) need the matching noise schedule.
    extra = {"prediction_type": "v_prediction", "rescale_betas_zero_snr": True} if key[1] else {}
    if sampler == "lcm":
        # DMD2 / LCM-style few-step sampling.
        pipe.scheduler = LCMScheduler.from_config(config, **extra)
    elif sampler == "dpmpp_2m_sde_karras":
        # What the photo-real checkpoints (LUSTIFY, CyberRealistic) recommend.
        pipe.scheduler = DPMSolverMultistepScheduler.from_config(
            config, algorithm_type="sde-dpmsolver++", use_karras_sigmas=True, **extra
        )
    else:
        pipe.scheduler = EulerAncestralDiscreteScheduler.from_config(config, **extra)


def adapter_name(path: str) -> str:
    return os.path.splitext(os.path.basename(path))[0].replace(".", "_").replace(" ", "_")


def set_fused(pipe: StableDiffusionXLPipeline, key: tuple[str, bool], fused: tuple[str, float] | None) -> None:
    """Fuses the sampling LoRA (DMD2, 376 MB) into the U-Net weights instead of keeping it as an
    adapter, which would sit on the GPU next to the U-Net and leave too little room."""
    if _fused.get(key) == fused:
        return
    # Adapters wrap the layers; take them out before touching the base weights.
    if _loras[key]:
        pipe.unload_lora_weights()
        _loras[key] = set()
    pipe.unet.load_state_dict(_base_unet[key])
    if fused:
        path, scale = fused
        name = "fused_" + adapter_name(path)
        pipe.load_lora_weights(path, adapter_name=name)
        pipe.fuse_lora(adapter_names=[name], lora_scale=scale, components=["unet"])
        pipe.unload_lora_weights()
    _fused[key] = fused


def set_loras(pipe: StableDiffusionXLPipeline, key: tuple[str, bool], loras: list[tuple[str, float]]) -> None:
    loaded = _loras[key]
    if not loras:
        if loaded:
            pipe.disable_lora()
        return
    names, weights = [], []
    for path, scale in loras:
        name = adapter_name(path)
        if name not in loaded:
            pipe.load_lora_weights(path, adapter_name=name)
            loaded.add(name)
        names.append(name)
        weights.append(scale)
    pipe.enable_lora()
    pipe.set_adapters(names, adapter_weights=weights)


# ---------- long prompts ----------


def split_prompt(pipe: StableDiffusionXLPipeline, text: str) -> list[str]:
    """Comma-separated tags grouped into pieces of at most 75 CLIP tokens each."""
    tokenizer = pipe.tokenizer
    chunks, current = [], ""
    for part in [p.strip() for p in text.split(",") if p.strip()]:
        candidate = f"{current}, {part}" if current else part
        if len(tokenizer(candidate).input_ids) - 2 > CHUNK_TOKENS and current:
            chunks.append(current)
            current = part
        else:
            current = candidate
    if current or not chunks:
        chunks.append(current)
    return chunks[:MAX_CHUNKS]


@torch.no_grad()
def encode_chunks(pipe: StableDiffusionXLPipeline, chunks: list[str]) -> tuple[torch.Tensor, torch.Tensor]:
    """Embeddings of every chunk joined along the token axis; pooled from the first chunk."""
    device = pipe._execution_device
    embeds, pooled = [], None
    for chunk in chunks:
        e, _, p, _ = pipe.encode_prompt(
            prompt=chunk, device=device, num_images_per_prompt=1, do_classifier_free_guidance=False
        )
        embeds.append(e)
        if pooled is None:
            pooled = p
    return torch.cat(embeds, dim=1), pooled


def encode_pair(pipe: StableDiffusionXLPipeline, prompt: str, negative: str):
    pos_chunks = split_prompt(pipe, prompt)
    neg_chunks = split_prompt(pipe, negative or "")
    # Positive and negative are batched together for guidance, so they need the same length.
    count = max(len(pos_chunks), len(neg_chunks))
    pos_chunks += [""] * (count - len(pos_chunks))
    neg_chunks += [""] * (count - len(neg_chunks))
    pe, pp = encode_chunks(pipe, pos_chunks)
    ne, npool = encode_chunks(pipe, neg_chunks)
    return pe, ne, pp, npool


# ---------- regional prompts ----------


class RegionalState:
    def __init__(self) -> None:
        self.active = False
        self.embeds: list[torch.Tensor] = []
        self.spans: list[tuple[float, float]] = []
        self.latent_hw = (128, 128)
        self.guided = True
        self._mask_cache: dict[tuple[int, str], list[torch.Tensor]] = {}

    def masks(self, tokens: int, device, dtype) -> list[torch.Tensor]:
        key = (tokens, str(dtype))
        if key in self._mask_cache:
            return self._mask_cache[key]
        lh, lw = self.latent_hw
        factor = math.sqrt(lh * lw / tokens)
        h = max(1, round(lh / factor))
        w = max(1, tokens // h)
        out = []
        for x0, x1 in self.spans:
            m = torch.zeros(h, w, device=device, dtype=dtype)
            a, b = int(round(x0 * w)), int(round(x1 * w))
            m[:, a:max(a + 1, b)] = 1
            out.append(m.reshape(1, h * w, 1))
        self._mask_cache[key] = out
        return out


STATE = RegionalState()


def _attend(attn, query: torch.Tensor, context: torch.Tensor) -> torch.Tensor:
    key = attn.to_k(context)
    value = attn.to_v(context)
    batch = query.shape[0]
    if key.shape[0] != batch:
        key = key.expand(batch, -1, -1)
        value = value.expand(batch, -1, -1)
    head_dim = key.shape[-1] // attn.heads
    q = query.view(batch, -1, attn.heads, head_dim).transpose(1, 2)
    k = key.view(batch, -1, attn.heads, head_dim).transpose(1, 2)
    v = value.view(batch, -1, attn.heads, head_dim).transpose(1, 2)
    out = F.scaled_dot_product_attention(q, k, v)
    return out.transpose(1, 2).reshape(batch, -1, attn.heads * head_dim)


class RegionalAttnProcessor:
    """Cross-attention that reads each region's own prompt inside that region (conditional half)."""

    def __init__(self) -> None:
        self.default = AttnProcessor2_0()

    def __call__(self, attn, hidden_states, encoder_hidden_states=None, attention_mask=None, temb=None, *args, **kwargs):
        if not STATE.active or encoder_hidden_states is None or hidden_states.ndim != 3:
            return self.default(attn, hidden_states, encoder_hidden_states, attention_mask, temb)
        residual = hidden_states
        query = attn.to_q(hidden_states)
        out = _attend(attn, query, encoder_hidden_states)
        half = hidden_states.shape[0] // 2 if STATE.guided else 0
        cond_q = query[half:]
        masks = STATE.masks(hidden_states.shape[1], hidden_states.device, hidden_states.dtype)
        base = out[half:]
        regional = torch.zeros_like(base)
        cover = torch.zeros_like(masks[0]) if masks else None
        for embeds, mask in zip(STATE.embeds, masks):
            context = embeds.to(device=hidden_states.device, dtype=hidden_states.dtype)
            regional = regional + mask * _attend(attn, cond_q, context)
            cover = cover + mask
        if cover is not None:
            cover = cover.clamp(max=1)
            mixed = base * (1 - cover) + (regional * (1 - REGION_BASE_WEIGHT) + base * REGION_BASE_WEIGHT * cover)
            out = torch.cat([out[:half], mixed], dim=0) if half else mixed
        out = attn.to_out[0](out)
        out = attn.to_out[1](out)
        if attn.residual_connection:
            out = out + residual
        return out / attn.rescale_output_factor


# ---------- request ----------


@torch.no_grad()
def run(req: dict) -> None:
    rid = req["id"]
    vpred = bool(req.get("vpred"))
    key = (req["model"], vpred)
    pipe = pipeline(*key)
    set_scheduler(pipe, key, str(req.get("sampler", "euler_a")))
    # The sampling LoRA is fused into the weights; character LoRAs stay small adapters.
    set_fused(pipe, key, (req["lora"], float(req.get("lora_scale", 1.0))) if req.get("lora") else None)
    loras = []
    for entry in req.get("loras") or []:
        if entry.get("path") and os.path.exists(entry["path"]):
            loras.append((entry["path"], float(entry.get("scale", 0.8))))
    set_loras(pipe, key, loras)
    steps = int(req.get("steps", 28))
    seed = int(req.get("seed", -1))
    if seed < 0:
        seed = random.randint(0, 2**31 - 1)
    width = int(req.get("width", 1024))
    height = int(req.get("height", 1024))
    cfg = float(req.get("cfg", 6.0))

    prompt_embeds, negative_embeds, pooled, negative_pooled = encode_pair(
        pipe, req["prompt"], req.get("negative", "")
    )
    regions = [r for r in (req.get("regions") or []) if (r.get("prompt") or "").strip()]
    if regions:
        STATE.embeds = [encode_chunks(pipe, split_prompt(pipe, r["prompt"]))[0] for r in regions]
        STATE.spans = [(float(r.get("x0", 0)), float(r.get("x1", 1))) for r in regions]
        STATE.latent_hw = (height // 8, width // 8)
        STATE.guided = cfg > 1.0
        STATE._mask_cache = {}
        STATE.active = True
        pipe.unet.set_attn_processor(RegionalAttnProcessor())

    def on_step(_pipe, step, _t, kwargs):
        print(f"{rid} STEP {step + 1}/{steps}", flush=True)
        return kwargs

    try:
        image = pipe(
            prompt_embeds=prompt_embeds,
            negative_prompt_embeds=negative_embeds,
            pooled_prompt_embeds=pooled,
            negative_pooled_prompt_embeds=negative_pooled,
            width=width,
            height=height,
            num_inference_steps=steps,
            guidance_scale=cfg,
            guidance_rescale=0.7 if vpred else 0.0,
            generator=torch.Generator("cuda").manual_seed(seed),
            callback_on_step_end=on_step,
        ).images[0]
    finally:
        if STATE.active:
            STATE.active = False
            STATE.embeds = []
            pipe.unet.set_attn_processor(AttnProcessor2_0())
    image.save(req["output"])
    torch.cuda.empty_cache()
    print(f"{rid} SEED {seed}", flush=True)
    print(f"{rid} RESULT {req['output']}", flush=True)


def main() -> int:
    print("READY", flush=True)
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        rid = "?"
        try:
            req = json.loads(line)
            rid = req.get("id", "?")
            run(req)
        except Exception as exc:  # report and keep serving
            traceback.print_exc(file=sys.stderr)
            print(f"{rid} ERROR {str(exc).splitlines()[0][:300]}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
