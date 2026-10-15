"""Install the official SDXL IP-Adapter Plus assets into the local model directory."""

from pathlib import Path
from huggingface_hub import hf_hub_download

REPOSITORY = "h94/IP-Adapter"
REVISION = "018e402774aeeddd60609b4ecdb7e298259dc729"
DIRECTORY = Path("D:/media-models/image/ip-adapter-plus")
FILES = (
    "models/image_encoder/config.json",
    "models/image_encoder/model.safetensors",
    "sdxl_models/ip-adapter-plus_sdxl_vit-h.safetensors",
)

if __name__ == "__main__":
    for filename in FILES:
        print(f"Downloading {filename}", flush=True)
        hf_hub_download(REPOSITORY, filename, revision=REVISION, local_dir=DIRECTORY)
    print("READY", flush=True)
