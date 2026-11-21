#!/usr/bin/env python3
"""Download nicer program photos from Pixabay for your own build.

    PIXABAY_KEY=your-key python3 tools/fetch_program_photos.py            # first match for every program
    PIXABAY_KEY=your-key python3 tools/fetch_program_photos.py --list rice # show 10 candidates for one program
    PIXABAY_KEY=your-key python3 tools/fetch_program_photos.py --pick rice 3

Get a free key at https://pixabay.com/api/docs/ (sign in). Photos land in common/src/main/assets/programs-pixabay/,
which git ignores: the Pixabay Content License lets you use them in your app but not share the files on their own,
so they must not be committed. The app prefers them over the Wikimedia photos in assets/programs/.
"""
import json, os, sys, urllib.parse, urllib.request
from io import BytesIO

QUERIES = {
    "eggs-soft": "soft boiled egg", "eggs-medium": "boiled egg halves", "eggs-hard": "hard boiled eggs",
    "rice": "bowl white rice", "steam-veg": "steamed vegetables", "melt-chocolate": "melted chocolate",
    "smoothie": "strawberry smoothie", "proof-dough": "bread dough bowl", "reheat": "vegetable soup", "clean": "clean kitchen sink",
    "knead": "kneading dough", "brown": "fried onions pan", "varoma": "bamboo steamer dumplings",
    "clean-deep": "scrubbing brush dishes", "clean-smell": "lemon halves",
}
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "common", "src", "main", "assets", "programs-pixabay")


def search(key, q):
    u = "https://pixabay.com/api/?" + urllib.parse.urlencode(dict(key=key, q=q, image_type="photo", orientation="horizontal",
                                                                  safesearch="true", per_page=10, order="popular", min_width=1200))
    return json.load(urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "mcc-app-tools"}), timeout=30))["hits"]


def save(hit, name):
    from PIL import Image  # pip install pillow
    data = urllib.request.urlopen(urllib.request.Request(hit["largeImageURL"], headers={"User-Agent": "mcc-app-tools"}), timeout=60).read()
    im = Image.open(BytesIO(data)).convert("RGB")
    W, H = 640, 400
    s = max(W / im.width, H / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.Resampling.LANCZOS)
    l, t = (im.width - W) // 2, (im.height - H) // 2
    os.makedirs(OUT, exist_ok=True)
    im.crop((l, t, l + W, t + H)).save(os.path.join(OUT, name + ".jpg"), quality=84, optimize=True, progressive=True)
    print(f"{name}: {hit['pageURL']}")


def main():
    key = os.environ.get("PIXABAY_KEY") or sys.exit("set PIXABAY_KEY (free at https://pixabay.com/api/docs/)")
    a = sys.argv[1:]
    if a[:1] == ["--list"]:
        for i, h in enumerate(search(key, QUERIES.get(a[1], a[1]))):
            print(i, h["pageURL"], h["previewURL"])
    elif a[:1] == ["--pick"]:
        save(search(key, QUERIES.get(a[1], a[1]))[int(a[2])], a[1])
    else:
        for name, q in QUERIES.items():
            hits = search(key, q)
            if hits: save(hits[0], name)
            else: print(f"{name}: no match for '{q}'")


if __name__ == "__main__":
    main()
