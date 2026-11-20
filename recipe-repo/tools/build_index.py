#!/usr/bin/env python3
"""Validates every recipes/*.json and writes index.json next to the recipes folder.

usage: build_index.py [repo_dir]
"""
from __future__ import annotations

import datetime
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "converters"))
import mcc_format  # noqa: E402


def main() -> int:
    repo = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
    entries, bad = [], 0
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
        entries.append({
            "id": r["id"], "title": r["title"], "language": r.get("language"), "tags": r.get("tags", []),
            "totalMinutes": r.get("totalMinutes"), "image": r.get("image"),
            "file": f"recipes/{p.name}", "sha256": hashlib.sha256(raw).hexdigest(),
        })
    entries.sort(key=lambda e: (e["language"] or "", e["title"].lower()))
    index = {"format": "mcc-index/1",
             "generated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
             "recipes": entries}
    (repo / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"index.json: {len(entries)} recipes, {bad} invalid")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
