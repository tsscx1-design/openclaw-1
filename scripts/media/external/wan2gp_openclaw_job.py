"""Runs one Wan2GP generation for the OpenClaw gateway (media studio).

Usage: python openclaw_job.py <request.json>
request.json: {"settings": {...WanGP settings...}, "output_dir": "C:/...", "profile": 4}
("profile" is optional: 4 = low RAM/VRAM offloading, 1-2 keep a small model on the GPU)
Prints "PROGRESS <percent> <phase>" lines while running, then "RESULT <json list of files>"
or "ERROR <message>" and exits non-zero. Runs headless: nothing is served on the network.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from shared.api import init  # noqa: E402


def main() -> int:
    request = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    session = init(
        root=ROOT,
        output_dir=Path(request["output_dir"]),
        cli_args=["--attention", "sage2", "--profile", str(request.get("profile", 4))],
        console_output=False,
    )
    job = session.submit_task(request["settings"])
    last = None
    for event in job.events.iter(timeout=0.5):
        if event.kind != "progress":
            continue
        progress = event.data
        line = f"PROGRESS {progress.progress} {progress.phase}"
        if line != last:
            print(line, flush=True)
            last = line
    result = job.result()
    if result.success and result.generated_files:
        print("RESULT " + json.dumps([str(f) for f in result.generated_files]), flush=True)
        return 0
    message = "; ".join(error.message for error in result.errors) or "no output"
    print("ERROR " + message, flush=True)
    return 1


if __name__ == "__main__":
    sys.exit(main())
