"""Borrow the GPU from the chat model and give it back: python gpu.py pause|resume"""

import os
import subprocess
import sys
import time

BONSAI = os.path.expanduser(r"~\.openclaw\bonsai")
FLAG = os.path.join(BONSAI, "llm-pause.flag")
CMDLINE = os.path.join(BONSAI, "last_main_cmdline.txt")


def listening_pid(port):
    out = subprocess.run(["netstat", "-ano", "-p", "TCP"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        cols = line.split()
        if len(cols) >= 5 and cols[1].endswith(f":{port}") and cols[3] == "LISTENING":
            return int(cols[4])
    return None


def pause():
    open(FLAG, "w").write(str(int(time.time() * 1000)))
    pid = listening_pid(11434)
    if pid:
        subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True)
        for _ in range(40):
            if not listening_pid(11434):
                break
            time.sleep(0.25)
        time.sleep(1)
    print("paused", flush=True)


def resume():
    if os.path.exists(FLAG):
        os.remove(FLAG)
    if not listening_pid(11434) and os.path.exists(CMDLINE):
        cmd = open(CMDLINE, encoding="utf-8").read().strip()
        subprocess.Popen(cmd, cwd=os.path.join(BONSAI, "bin"), creationflags=0x00000008 | 0x00000200)
    print("resumed", flush=True)


if __name__ == "__main__":
    {"pause": pause, "resume": resume}[sys.argv[1]]()
