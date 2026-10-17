"""Attaches each screenshot to the commit as a check run (base64 JPEG in output.text)."""
import base64
import io
import json
import os
import subprocess
import sys

from PIL import Image

folder = sys.argv[1]
repo = os.environ["GITHUB_REPOSITORY"]
sha = os.environ["GITHUB_SHA"]
for name in sorted(os.listdir(folder)):
    img = Image.open(os.path.join(folder, name)).convert("RGB")
    width, quality = 900, 60
    while True:
        im = img.copy()
        im.thumbnail((width, width * 3))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=quality)
        data = base64.b64encode(buf.getvalue()).decode()
        if len(data) < 64000 or width < 300:
            break
        width = int(width * 0.85)
        quality = max(35, quality - 5)
    body = {
        "name": "shot: " + name.rsplit(".", 1)[0],
        "head_sha": sha,
        "status": "completed",
        "conclusion": "neutral",
        "output": {"title": name, "summary": f"{im.width}x{im.height} jpeg", "text": data},
    }
    with open("/tmp/check.json", "w") as f:
        json.dump(body, f)
    subprocess.run(["gh", "api", f"repos/{repo}/check-runs", "--input", "/tmp/check.json", "--silent"], check=False)
    print("uploaded", name, len(data))
