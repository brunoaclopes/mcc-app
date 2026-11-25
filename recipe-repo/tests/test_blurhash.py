"""BlurHash in the index: the encoder against a reference decoder, and build_index writing and caching it."""
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "converters"))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))
import blurhash  # noqa: E402
import build_index  # noqa: E402

CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"


def decode(h: str, w: int, ht: int):
    """Reference decoder, written from the BlurHash spec (the apps' Kotlin decoder follows the same steps)."""
    def d83(s):
        v = 0
        for c in s:
            v = v * 83 + CHARS.index(c)
        return v

    def lin(v):
        x = v / 255
        return x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4

    def srgb(v):
        x = min(max(v, 0), 1)
        return round(x * 12.92 * 255) if x <= 0.0031308 else round((1.055 * x ** (1 / 2.4) - 0.055) * 255)

    flag = d83(h[0])
    ny, nx = flag // 9 + 1, flag % 9 + 1
    assert len(h) == 4 + 2 * nx * ny
    maxv = (d83(h[1]) + 1) / 166
    dc = d83(h[2:6])
    colors = [(lin(dc >> 16), lin((dc >> 8) & 255), lin(dc & 255))]
    for i in range(1, nx * ny):
        v = d83(h[4 + i * 2:6 + i * 2])
        q = (v // 361, (v // 19) % 19, v % 19)
        colors.append(tuple(math.copysign(((c - 9) / 9) ** 2, c - 9) * maxv for c in q))
    out = []
    for y in range(ht):
        for x in range(w):
            r = g = b = 0.0
            for j in range(ny):
                for i in range(nx):
                    f = math.cos(math.pi * x * i / w) * math.cos(math.pi * y * j / ht)
                    c = colors[i + j * nx]
                    r += c[0] * f
                    g += c[1] * f
                    b += c[2] * f
            out.append((srgb(r), srgb(g), srgb(b)))
    return out


class Encoder(unittest.TestCase):
    def test_solid_colour(self):
        h = blurhash.encode([(200, 100, 50)] * (64 * 48), 64, 48)
        self.assertEqual(28, len(h))  # 4 x 3 components
        self.assertEqual("L", h[0])
        out = decode(h, 64, 48)
        for px in (out[24 * 64 + 32], out[10 * 64 + 50], out[40 * 64 + 5]):  # away from the top-left edge
            self.assertTrue(all(abs(a - b) <= 6 for a, b in zip(px, (200, 100, 50))), px)

    def test_left_to_right_gradient(self):
        w, ht = 64, 32
        h = blurhash.encode([(255 * x // (w - 1), 0, 0) for _ in range(ht) for x in range(w)], w, ht)
        out = decode(h, w, ht)
        self.assertLess(out[16 * w + 2][0], 90)        # dark red on the left
        self.assertGreater(out[16 * w + w - 3][0], 170)  # bright red on the right

    def test_fixed_value(self):
        # the Kotlin decoder's test (recipe/BlurHashTest) decodes this exact string
        self.assertEqual("L6TNoS^2fQ^2}Vj@fQj@fQfQfQfQ", blurhash.encode([(255, 128, 0)] * (64 * 48), 64, 48))


class Index(unittest.TestCase):
    def test_build_index_adds_blurhash_and_caches_it(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow not installed")
        with tempfile.TemporaryDirectory() as t:
            repo = Path(t)
            (repo / "recipes").mkdir()
            (repo / "images").mkdir()
            Image.new("RGB", (120, 90), (30, 140, 90)).save(repo / "images" / "soup.jpg")
            for lang in ("pt", "en"):
                (repo / "recipes" / f"soup-{lang}.json").write_text(json.dumps({
                    "format": "mcc-recipe/1", "id": f"soup-{lang}", "title": "Soup", "language": lang,
                    "image": "images/soup.jpg", "steps": [{"type": "instruction", "text": "Cook."}]}))
            argv = sys.argv
            try:
                sys.argv = ["build_index.py", str(repo)]
                self.assertEqual(0, build_index.main())
                index = json.loads((repo / "index.json").read_text())
                hashes = {e["blurhash"] for e in index["recipes"]}
                self.assertEqual(1, len(hashes))
                cache = json.loads((repo / build_index.BLURHASH_CACHE).read_text())
                self.assertIn("images/soup.jpg", cache)
                # the cached value is used again (a changed cache entry shows it was not computed anew)
                cache["images/soup.jpg"][1] = "LCACHED0000000000000000000000"
                (repo / build_index.BLURHASH_CACHE).write_text(json.dumps(cache))
                self.assertEqual(0, build_index.main())
                index = json.loads((repo / "index.json").read_text())
                self.assertEqual({"LCACHED0000000000000000000000"}, {e["blurhash"] for e in index["recipes"]})
            finally:
                sys.argv = argv


if __name__ == "__main__":
    unittest.main()
