#!/usr/bin/env python3
"""Downloads every recipe's remote image once, stores a 640x400 JPEG under images/, and points the recipe's
"image" at that relative path (original URL kept in "imageSource"). The machine then loads images from the
same server as the recipes. Re-run after adding recipes; already-fetched images are skipped.

usage: fetch_images.py [repo_dir]
"""
from __future__ import annotations

import concurrent.futures as cf
import hashlib
import io
import json
import sys
import urllib.request
from pathlib import Path

from PIL import Image

W, H = 640, 400


def fetch(url: str, dest: Path) -> bool:
    if dest.exists():
        return True
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "mcc-recipes/1"})
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read()
        im = Image.open(io.BytesIO(data)).convert("RGB")
        scale = max(W / im.width, H / im.height)
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.Resampling.LANCZOS)
        left, top = (im.width - W) // 2, (im.height - H) // 2
        im.crop((left, top, left + W, top + H)).save(dest, "JPEG", quality=80, optimize=True, progressive=False)
        return True
    except Exception as e:  # noqa: BLE001
        print(f"failed {url}: {e}", file=sys.stderr)
        return False


def main() -> int:
    repo = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
    (repo / "images").mkdir(exist_ok=True)
    jobs = {}
    docs = []
    for p in sorted((repo / "recipes").glob("*.json")):
        r = json.loads(p.read_text(encoding="utf-8"))
        url = r.get("imageSource") or r.get("image")
        if not url or not url.startswith("http"):
            continue
        rel = f"images/{hashlib.sha1(url.encode()).hexdigest()[:16]}.jpg"
        jobs[url] = repo / rel
        docs.append((p, r, url, rel))
    with cf.ThreadPoolExecutor(8) as ex:
        ok = dict(zip(jobs, ex.map(lambda u: fetch(u, jobs[u]), jobs)))
    changed = 0
    for p, r, url, rel in docs:
        if ok.get(url) and r.get("image") != rel:
            r["imageSource"] = url
            r["image"] = rel
            p.write_text(json.dumps(r, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
            changed += 1
    print(f"{sum(ok.values())}/{len(jobs)} images ok, {changed} recipes updated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
