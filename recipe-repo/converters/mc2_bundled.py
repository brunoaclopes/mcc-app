#!/usr/bin/env python3
"""Converts the stock app's recipes (de.silpion.mc2 res/raw*/recipes.json, or the same JSON from its
cloud API) to mcc-recipe/1.

usage: mc2_bundled.py <recipes.json> <out_dir> [--lang xx]
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mcc_format  # noqa: E402

MACHINE_MODES = {"cooking": "run", "ramp": "run", "turbo": "turbo", "kneading": "knead",
                 "roasting": "roast", "steaming": "steam"}


def _int(v) -> int:
    try:
        return int(v or 0)
    except (TypeError, ValueError):
        return 0


def _group_title(name: str) -> str | None:
    name = (name or "").strip().rstrip(":").strip()
    if not name:
        return None
    return name.capitalize() if name.isupper() else name


def _ingredient_text(i: dict) -> str:
    parts = [str(i.get("amount") or "").strip(), str(i.get("unit") or "").strip(), str(i.get("name") or "").strip()]
    return " ".join(p for p in parts if p)


def _nutrition(nutrients: list) -> dict | None:
    keys = {"calories": "kcal", "protein": "proteinG", "carbohydrate": "carbsG", "fat": "fatG"}
    out = {}
    for n in nutrients or []:
        k = keys.get(n.get("type"))
        if k:
            try:
                out[k] = round(float(str(n.get("amount")).replace(",", ".")))
            except ValueError:
                pass
    return out or None


def convert_steps(src_steps: list, notes: list[str]) -> list[dict]:
    steps: list[dict] = []
    prev_mode = None
    for s in src_steps:
        mode = s.get("mode")
        mv = s.get("machineValues") or {}
        text = (s.get("text") or "").strip()
        seconds, speed, temp = _int(mv.get("time")), _int(mv.get("speed")), _int(mv.get("temp"))
        reverse = bool(mv.get("reverse"))
        if mode in ("instruction", "preparation", "wait", "end"):
            if text:
                steps.append({"type": "instruction", "text": text})
        elif mode == "scale":
            grams = _int(mv.get("weight"))
            steps.append({"type": "weigh", "text": text, "grams": grams} if grams > 0
                         else {"type": "instruction", "text": text})
        elif mode in MACHINE_MODES:
            m = MACHINE_MODES[mode]
            if seconds <= 0 or (m == "run" and speed == 0 and temp == 0):
                notes.append(f"step {s.get('step')}: {mode} without time/speed/temp kept as text")
                if text:
                    steps.append({"type": "instruction", "text": text})
                prev_mode = mode
                continue
            if m == "run" and reverse and speed > 3:
                notes.append(f"step {s.get('step')}: reverse at speed {speed} is not allowed; reverse dropped")
                reverse = False
            if m == "run":
                seg = {"seconds": seconds, "speed": speed, "tempC": temp, "reverse": reverse}
            elif m == "roast":
                seg = {"seconds": seconds, "tempC": temp or 130}
            else:
                seg = {"seconds": seconds}
            if mode == "ramp" and prev_mode == "ramp" and steps and steps[-1]["type"] == "machine":
                steps[-1]["segments"].append(seg)  # consecutive ramp steps form one ramp (stock behaviour)
            else:
                steps.append({"type": "machine", "text": text, "mode": m, "segments": [seg]})
        else:
            notes.append(f"step {s.get('step')}: unknown mode {mode!r} kept as text")
            if text:
                steps.append({"type": "instruction", "text": text})
        prev_mode = mode
    return steps


def convert(r: dict) -> dict:
    notes: list[str] = []
    lang = (r.get("language") or "xx").lower()
    steps = convert_steps((r.get("guidedCooking") or {}).get("steps") or [], notes)
    if not steps:  # no guided cooking: fall back to the free-text instructions
        steps = [{"type": "instruction", "text": t.strip()} for t in r.get("instructions") or [] if t.strip()]
        notes.append("no guided steps in source; instructions are text only")
    image = (r.get("imageBase") or "") + (r.get("imageName") or "") if r.get("imageName") else None
    servings = " ".join(str(x) for x in (r.get("yield"), r.get("yieldUnit")) if x) or None
    out = {
        "format": mcc_format.FORMAT,
        "id": f"mc2-{r['id']}-{lang}",
        "title": r["name"].strip(),
        "language": lang,
        "servings": servings,
        "activeMinutes": _int(r.get("duration")) or None,
        "totalMinutes": _int(r.get("durationTotal")) or None,
        "difficulty": (r.get("complexity") or "").lower() or None,
        "tags": [t["name"] for t in r.get("tags") or [] if t.get("name")],
        "image": image,
        "source": {"kind": "mc2-bundled", "ref": r.get("originID"), "url": None},
        "nutritionPerServing": _nutrition(r.get("nutrients")),
        "ingredients": [
            {"group": _group_title(b.get("name")),
             "items": [{"text": _ingredient_text(i)} for i in b.get("ingredients") or [] if _ingredient_text(i)]}
            for b in r.get("ingredientsBases") or []
        ],
        "steps": steps,
        "notes": notes,
    }
    return {k: v for k, v in out.items() if v is not None}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out_dir")
    ap.add_argument("--lang", help="only convert recipes in this language")
    a = ap.parse_args()
    data = json.loads(Path(a.src).read_text(encoding="utf-8"))
    ok = fail = 0
    for r in data if isinstance(data, list) else [data]:
        if r.get("remove") or (a.lang and (r.get("language") or "").lower() != a.lang):
            continue
        try:
            mcc_format.write(convert(r), Path(a.out_dir))
            ok += 1
        except (ValueError, KeyError) as e:
            print(f"skip {r.get('id')}: {e}", file=sys.stderr)
            fail += 1
    print(f"converted {ok}, skipped {fail}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
