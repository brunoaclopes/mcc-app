"""Shared helpers for writing and validating mcc-recipe/1 documents (no third-party dependencies)."""
from __future__ import annotations

import json
import re
from pathlib import Path

FORMAT = "mcc-recipe/1"
MODES = {"run", "turbo", "knead", "roast", "steam"}
TEMPS_C = {0, 37, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130}


def slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return s[:60] or "recipe"


MAX_SECONDS = 99 * 60
MAX_LONG_SECONDS = 12 * 3600  # long low-temperature segments: slow cooking, sous-vide, fermentation
LONG_MAX_TEMP_C = 95


def max_seconds(mode: str | None, seg: dict) -> int:
    """A run segment heated to 95 degC or less at speed 0-1 may last 12 h (the machine runs it only with long
    programs switched on); everything else 99 min. Mirrors Recipe.maxSeconds in the app."""
    t, sp = seg.get("tempC", 0) or 0, seg.get("speed", 0) or 0
    if mode == "run" and 0 < t <= LONG_MAX_TEMP_C and sp <= 1:
        return MAX_LONG_SECONDS
    return MAX_SECONDS


def validate(r: dict) -> list[str]:
    """Returns a list of problems (empty = valid). Mirrors what the app's parser accepts."""
    p: list[str] = []
    if r.get("format") != FORMAT:
        p.append(f"format must be {FORMAT}")
    for k in ("id", "title"):
        if not isinstance(r.get(k), str) or not r[k].strip():
            p.append(f"{k} missing")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", r.get("id", "") or ""):
        p.append("id may only contain letters, digits, . _ -")
    steps = r.get("steps")
    if not isinstance(steps, list) or not steps:
        p.append("steps missing")
        return p
    for i, s in enumerate(steps, 1):
        t = s.get("type")
        if t == "instruction":
            if not s.get("text"):
                p.append(f"step {i}: instruction without text")
        elif t == "weigh":
            g = s.get("grams")
            if not isinstance(g, int) or g <= 0 or g > 5000:
                p.append(f"step {i}: grams must be 1..5000")
        elif t == "machine":
            if s.get("mode") not in MODES:
                p.append(f"step {i}: bad mode {s.get('mode')}")
            segs = s.get("segments")
            if not isinstance(segs, list) or not segs:
                p.append(f"step {i}: segments missing")
                continue
            for j, g in enumerate(segs, 1):
                sec = g.get("seconds")
                mx = max_seconds(s.get("mode"), g)
                if not isinstance(sec, int) or sec <= 0 or sec > mx:
                    p.append(f"step {i}.{j}: seconds must be 1..{mx}")
                if not 0 <= g.get("speed", 0) <= 10:
                    p.append(f"step {i}.{j}: speed must be 0..10")
                if g.get("tempC", 0) not in TEMPS_C:
                    p.append(f"step {i}.{j}: tempC {g.get('tempC')} is not a machine level")
        else:
            p.append(f"step {i}: unknown type {t}")
    return p


def write(r: dict, out_dir: Path) -> Path:
    problems = validate(r)
    if problems:
        raise ValueError(f"{r.get('id')}: " + "; ".join(problems))
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{r['id']}.json"
    path.write_text(json.dumps(r, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return path
