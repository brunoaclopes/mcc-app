#!/usr/bin/env python3
"""Validates every recipes/*.json and writes index.json next to the recipes folder.

Every recipe must exist in each of LANGUAGES, as <key>-<language>.json; a missing translation fails the build.

usage: build_index.py [repo_dir]
"""
from __future__ import annotations

import datetime
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "converters"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import blurhash  # noqa: E402
import mcc_format  # noqa: E402

try:  # optional: without Pillow the index just has no "blurhash" fields
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None

# BlurHash of each photo, kept between runs (the recipe server rebuilds the index every minute)
BLURHASH_CACHE = ".mcc-blurhash.json"

# every recipe exists in all of these; the app shows the version that matches its own language
LANGUAGES = ("pt", "en")


def missing_translations(entries: list[dict]) -> list[str]:
    """Returns '<key>: no <language> version' for every key that lacks one of LANGUAGES."""
    have: dict[str, set[str]] = {}
    for e in entries:
        have.setdefault(e["key"], set()).add(e["language"])
    return [f"{k}: no {lang} version" for k, langs in sorted(have.items()) for lang in LANGUAGES if lang not in langs]


def photo_blurhash(repo: Path, image, cache: dict) -> str | None:
    """BlurHash of a photo stored in the repository (images/…); None for web addresses, missing files or no Pillow."""
    if Image is None or not isinstance(image, str) or image.startswith(("http://", "https://")):
        return None
    p = repo / image
    try:
        st = p.stat()
    except OSError:
        return None
    stamp = f"{st.st_size}:{st.st_mtime_ns}"
    hit = cache.get(image)
    if isinstance(hit, list) and len(hit) == 2 and hit[0] == stamp:
        return hit[1]
    try:
        with Image.open(p) as im:
            im.draft("RGB", (128, 96))  # JPEG: decode at a fraction of the size, much faster
            # 64 x 48 is plenty for a blur; much smaller and the format's edge bias (about 2/width) shows
            small = im.convert("RGB").resize((64, 48))
            h = blurhash.encode(list(small.getdata()), 64, 48)
    except Exception:  # noqa: BLE001 - a photo that can't be read just gets no placeholder
        return None
    cache[image] = [stamp, h]
    return h


def main() -> int:
    repo = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
    entries, bad = [], 0
    try:
        cache = json.loads((repo / BLURHASH_CACHE).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        cache = {}
    for p in sorted((repo / "recipes").glob("*.json")):
        raw = p.read_bytes()
        r = json.loads(raw)
        problems = mcc_format.validate(r)
        if problems:
            print(f"INVALID {p.name}: {'; '.join(problems)}", file=sys.stderr)
            bad += 1
            continue
        if p.stem != r["id"]:
            print(f"INVALID {p.name}: file name must be <id>.json", file=sys.stderr)
            bad += 1
            continue
        lang = r.get("language")
        if lang not in LANGUAGES or not r["id"].endswith(f"-{lang}"):
            print(f"INVALID {p.name}: language must be one of {', '.join(LANGUAGES)} and the id must end with "
                  f"-<language>", file=sys.stderr)
            bad += 1
            continue
        entries.append({
            "id": r["id"], "key": r["id"][:-len(lang) - 1], "title": r["title"], "language": lang,
            "tags": r.get("tags", []),
            "totalMinutes": r.get("totalMinutes"), "image": r.get("image"),
            "file": f"recipes/{p.name}", "sha256": hashlib.sha256(raw).hexdigest(),
        })
        bh = photo_blurhash(repo, r.get("image"), cache)
        if bh:
            entries[-1]["blurhash"] = bh
    missing = missing_translations(entries)
    for m in missing:
        print(f"MISSING {m}", file=sys.stderr)
    entries.sort(key=lambda e: (e["language"], e["title"].lower()))
    index = {"format": "mcc-index/1",
             "generated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
             "recipes": entries}
    (repo / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    try:
        (repo / BLURHASH_CACHE).write_text(json.dumps(cache, sort_keys=True), encoding="utf-8")
    except OSError:
        pass  # read-only folder: computed again next time
    print(f"index.json: {len(entries)} recipes, {bad} invalid, {len(missing)} missing translations")
    return 1 if bad or missing else 0


if __name__ == "__main__":
    sys.exit(main())
