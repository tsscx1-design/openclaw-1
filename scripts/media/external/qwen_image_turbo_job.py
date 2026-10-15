"""Run the official Qwen-Image-2.1-Turbo checkpoint for one OpenClaw image job."""

import json
import sys
from pathlib import Path


def main() -> int:
    request = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    output = Path(request["output"])
    settings = request["settings"]
    print("PROGRESS 5 モデルを読み込んでいます", flush=True)

    import torch
    from diffusers import QwenImage21Pipeline

    model = settings.get("model", "Qwen/Qwen-Image-2.1-Turbo")
    pipe = QwenImage21Pipeline.from_pretrained(model, torch_dtype=torch.bfloat16).to("cuda")
    print("PROGRESS 35 Turboで生成しています", flush=True)
    image = pipe(
        prompt=settings["prompt"],
        width=settings["width"],
        height=settings["height"],
        use_kv_cache=True,
        generator=torch.Generator("cpu").manual_seed(int(settings.get("seed", 0))),
    ).images[0]
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)
    print("PROGRESS 100 完了", flush=True)
    print("RESULT " + json.dumps([str(output)]), flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print("ERROR " + str(error), flush=True)
        raise
