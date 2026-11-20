#!/usr/bin/env python3
"""Converts a recipe web page (schema.org/Recipe JSON-LD, which most recipe sites embed) to mcc-recipe/1.

Machine settings written in the usual Monsieur Cuisine / Thermomix style are detected in the instruction
text and turned into machine steps, e.g. "20 seg./vel. 5", "5 min/100 °C/speed 1", "3 Min./Varoma/Stufe 1",
"turbo 2 seconds". Detected steps are marked in "notes": check them before cooking.

usage: web_jsonld.py <url-or-html-file> <out_dir> [--id ID] [--lang xx]
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mcc_format  # noqa: E402

LEVELS = sorted(mcc_format.TEMPS_C)

# ---------------------------------------------------------------- JSON-LD extraction

_LD = re.compile(r'<script[^>]+type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', re.S | re.I)


def find_recipe(page: str) -> dict:
    for block in _LD.findall(page):
        try:
            data = json.loads(html.unescape(block.strip()))
        except json.JSONDecodeError:
            continue
        r = _search(data)
        if r:
            return r
    raise ValueError("no schema.org Recipe found on the page")


def _search(node):
    if isinstance(node, list):
        for n in node:
            r = _search(n)
            if r:
                return r
    elif isinstance(node, dict):
        t = node.get("@type")
        if t == "Recipe" or (isinstance(t, list) and "Recipe" in t):
            return node
        for k in ("@graph", "mainEntity", "itemListElement"):
            if k in node:
                r = _search(node[k])
                if r:
                    return r
    return None


def _text(v) -> str:
    if v is None:
        return ""
    if isinstance(v, (list, tuple)):
        return ", ".join(_text(x) for x in v if _text(x))
    if isinstance(v, dict):
        return _text(v.get("text") or v.get("name") or v.get("url") or "")
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", str(v)))).strip()


def _minutes(iso: str | None) -> int | None:
    m = re.fullmatch(r"P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?", (iso or "").strip())
    if not m or not any(m.groups()):
        return None
    d, h, mi, s = (int(x or 0) for x in m.groups())
    return d * 1440 + h * 60 + mi + (1 if s else 0) or None


def _instructions(v) -> list[str]:
    """Flattens HowToStep / HowToSection / strings into a list of step texts."""
    out: list[str] = []
    if isinstance(v, str):
        parts = [p for p in re.split(r"\n+|(?<=\.)\s+(?=\d+\.\s)", html.unescape(v)) if p.strip()]
        out += [_text(p) for p in parts]
    elif isinstance(v, list):
        for x in v:
            out += _instructions(x)
    elif isinstance(v, dict):
        if v.get("@type") == "HowToSection" or "itemListElement" in v:
            if v.get("name"):
                out.append(_text(v["name"]) + ":")
            out += _instructions(v.get("itemListElement") or [])
        else:
            out.append(_text(v.get("text") or v.get("name")))
    return [re.sub(r"^\d+[.)]\s*", "", s).strip() for s in out if s and s.strip()]


# ---------------------------------------------------------------- machine settings in text

_TIME_T = r"\d+(?:[.,]\d+)?\s*(?:seg(?:undos?)?|sek(?:unden)?|sec(?:onds?)?|s|min(?:utos?|uten|utes?)?|h|horas?|stunden?|hours?)\.?(?![a-zà-ü])"
_TEMP_T = r"\d{2,3}\s*°\s*C?|varoma"
_REV_T = r"\u21ba|sentido\s+inverso|invers[ao]|reverse|linkslauf|giro\s+a\s+la\s+izquierda"
_SPEED_T = (r"(?:vel(?:ocidade|ocidad)?|speed(?:\s+setting)?|stufe|niveau|livello|snelheid)\.?\s*"
            r"(?:\d+(?:[.,]5)?|colher|spoon|cuchara|löffel|sanft)(?:\s+(?:" + _REV_T + r"))?")
_TOKEN = f"(?:{_TIME_T}|{_TEMP_T}|{_SPEED_T}|{_REV_T})"
_GROUP = re.compile(rf"{_TOKEN}(?:\s*/\s*{_TOKEN})+", re.I)
_TURBO = re.compile(rf"turbo\D{{0,30}}?({_TIME_T})|({_TIME_T})\W{{0,5}}turbo", re.I)

# Preset buttons ("roast button/3 minutes", "função amassar 2 min", "Knetstufe/2 Min.").
_PRESETS = {
    "roast": r"roast(?:ing)?\s+(?:button|function|programme|program)|(?:bot[aã]o|tecla|fun[cç][aã]o|programa)\s+(?:de\s+)?(?:refogar|alourar|tostar)|anbrat\w*|rissoler",
    "knead": r"knead(?:ing)?\s+(?:button|function|programme|program)|(?:bot[aã]o|tecla|fun[cç][aã]o|programa)\s+(?:de\s+)?amassar|teigkne\w*|knetstufe",
    "steam": r"steam(?:ing)?\s+(?:button|function|programme|program)|(?:bot[aã]o|tecla|fun[cç][aã]o|programa)\s+(?:de\s+)?vapor|dampfgar\w*",
}
_PRESET_RES = {k: re.compile(rf"(?:{v})\D{{0,30}}?({_TIME_T})", re.I) for k, v in _PRESETS.items()}

_TIME = re.compile(r"(\d+(?:[.,]\d+)?)\s*([a-z]+)", re.I)
_TEMP = re.compile(r"(\d{2,3})\s*°|(varoma)", re.I)
_SPEED = re.compile(r"(?:vel(?:ocidade|ocidad)?|speed(?:\s+setting)?|stufe|niveau|livello|snelheid)\.?\s*"
                    r"(\d+(?:[.,]5)?|colher|spoon|cuchara|löffel|sanft)", re.I)
_REVERSE = re.compile(_REV_T, re.I)


def _seconds(token: str) -> int:
    m = _TIME.match(token.strip())
    v = float(m.group(1).replace(",", "."))
    u = m.group(2).lower()
    if u.startswith("min"):
        v *= 60
    elif u.startswith(("h", "stund")):
        v *= 3600
    return int(round(v))


def _level_at_most(c: int) -> int:
    return max([t for t in LEVELS if t <= c] or [0])


def detect_settings(text: str, notes: list[str], where: str) -> list[dict]:
    """Returns machine steps, in order, for every 'time/temp/speed' group and turbo mention in the text."""
    found: list[tuple[int, dict]] = []
    for m in _TURBO.finditer(text):
        sec = max(1, min(_seconds(m.group(1) or m.group(2)), 10))
        found.append((m.start(), {"type": "machine", "text": "", "mode": "turbo", "segments": [{"seconds": sec}]}))
        notes.append(f"{where}: turbo detected ({m.group(0).strip()[:40]})")
    for mode, rx in _PRESET_RES.items():
        for m in rx.finditer(text):
            seg = {"seconds": _seconds(m.group(1))}
            if mode == "roast":
                seg["tempC"] = 130
            if 0 < seg["seconds"] <= 99 * 60:
                found.append((m.start(), {"type": "machine", "text": "", "mode": mode, "segments": [seg]}))
                notes.append(f"{where}: {mode} detected ({m.group(0)[:40]}), check before cooking")
    for m in _GROUP.finditer(text):
        group = m.group(0)
        parts = [p.strip() for p in group.split("/")]
        times = [p for p in parts if re.fullmatch(_TIME_T, p, re.I)]
        sp, tp = _SPEED.search(group), _TEMP.search(group)
        if not times or not (sp or tp):
            continue
        seconds = _seconds(times[0])
        speed = 0
        if sp:
            raw = sp.group(1).lower()
            speed = max(1, int(float(raw.replace(",", ".")))) if raw[0].isdigit() else 1
            if not raw[0].isdigit() or "." in raw or "," in raw:
                notes.append(f"{where}: speed '{raw}' mapped to {speed}")
        temp, mode = 0, "run"
        if tp:
            if tp.group(2):  # Varoma = steaming
                mode = "steam"
            else:
                temp = _level_at_most(min(int(tp.group(1)), 130))
        reverse = bool(_REVERSE.search(group))
        if temp > 0 and speed > 3:
            notes.append(f"{where}: speed {speed} with heat is not allowed; using 3")
            speed = 3
        if reverse and speed > 3:
            notes.append(f"{where}: reverse at speed {speed} is not allowed; reverse dropped")
            reverse = False
        if not 0 < seconds <= 99 * 60:
            continue
        seg = {"seconds": seconds} if mode == "steam" else {"seconds": seconds, "speed": speed, "tempC": temp, "reverse": reverse}
        found.append((m.start(), {"type": "machine", "text": "", "mode": mode, "segments": [seg]}))
        notes.append(f"{where}: machine step detected ({group[:50]}), check before cooking")
    return [s for _, s in sorted(found, key=lambda x: x[0])]


# ---------------------------------------------------------------- conversion

def convert(page: str, source_url: str | None, rid: str | None = None, lang: str | None = None) -> dict:
    r = find_recipe(page)
    notes: list[str] = []
    title = _text(r.get("name")) or "Recipe"
    steps: list[dict] = []
    for i, t in enumerate(_instructions(r.get("recipeInstructions")), 1):
        steps.append({"type": "instruction", "text": t})
        steps += detect_settings(t, notes, f"instruction {i}")
    if not steps:
        raise ValueError("recipe has no instructions")
    image = r.get("image")
    if isinstance(image, list):
        image = image[0] if image else None
    if isinstance(image, dict):
        image = image.get("url")
    kw = r.get("keywords")
    tags = [t.strip() for t in (kw.split(",") if isinstance(kw, str) else kw or []) if str(t).strip()]
    for k in ("recipeCategory", "recipeCuisine"):
        v = r.get(k)
        tags += [v] if isinstance(v, str) else list(v or [])
    nutrition = None
    cal = re.search(r"\d+", _text((r.get("nutrition") or {}).get("calories")) if isinstance(r.get("nutrition"), dict) else "")
    if cal:
        nutrition = {"kcal": int(cal.group(0))}
    language = (lang or str(r.get("inLanguage") or "")[:2] or "xx").lower()
    out = {
        "format": mcc_format.FORMAT,
        "id": rid or f"web-{mcc_format.slug(title)}",
        "title": title,
        "language": language,
        "servings": _text(r.get("recipeYield")) or None,
        "activeMinutes": _minutes(r.get("prepTime")),
        "totalMinutes": _minutes(r.get("totalTime")) or _minutes(r.get("cookTime")),
        "tags": list(dict.fromkeys(_text(t) for t in tags if _text(t)))[:12],
        "image": image if isinstance(image, str) else None,
        "source": {"kind": "web", "ref": None, "url": source_url},
        "nutritionPerServing": nutrition,
        "ingredients": [{"group": None, "items": [{"text": _text(i)} for i in r.get("recipeIngredient") or [] if _text(i)]}],
        "steps": steps,
        "notes": notes,
    }
    out["ingredients"] = [{k: v for k, v in g.items() if v is not None} for g in out["ingredients"]]
    return {k: v for k, v in out.items() if v is not None}


def load(src: str) -> tuple[str, str | None]:
    if re.match(r"https?://", src):
        req = urllib.request.Request(src, headers={"User-Agent": "Mozilla/5.0 (recipe-converter)"})
        with urllib.request.urlopen(req, timeout=20) as resp:
            charset = resp.headers.get_content_charset() or "utf-8"
            return resp.read().decode(charset, "replace"), src
    return Path(src).read_text(encoding="utf-8", errors="replace"), None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("src", help="URL or saved HTML file")
    ap.add_argument("out_dir")
    ap.add_argument("--id")
    ap.add_argument("--lang")
    a = ap.parse_args()
    page, url = load(a.src)
    r = convert(page, url, a.id, a.lang)
    path = mcc_format.write(r, Path(a.out_dir))
    print(f"wrote {path} ({len(r['steps'])} steps)")
    for n in r["notes"]:
        print("  note:", n)
    return 0


if __name__ == "__main__":
    sys.exit(main())
