"""Downloads the files a Wan2GP model type needs without loading it on the GPU.

Usage: python openclaw_download.py <model_type> [<model_type> ...]
Runs Wan2GP's own load_models() so files land exactly where generation looks for them, and
stops it right before the weights would be loaded. Prints "DONE <model_type>" per model.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from shared.api import init  # noqa: E402


class DownloadOnly(Exception):
    pass


def main() -> int:
    init(root=ROOT, output_dir=ROOT / "outputs", cli_args=["--attention", "sage2", "--profile", "4"], console_output=True)
    wgp = sys.modules["wgp"]
    for model_type in sys.argv[1:]:
        handler = wgp.model_types_handlers[wgp.get_base_model_type(model_type)]
        original = handler.load_model

        def stop(*_args, **_kwargs):
            raise DownloadOnly()

        handler.load_model = stop
        try:
            wgp.load_models(model_type)
        except DownloadOnly:
            print(f"DONE {model_type}", flush=True)
        finally:
            handler.load_model = original
    return 0


if __name__ == "__main__":
    sys.exit(main())
