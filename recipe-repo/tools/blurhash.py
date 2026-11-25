"""BlurHash encoder (https://blurha.sh): a recipe photo as a ~30 character string the apps paint as a blurred
placeholder while the photo itself loads. Standard algorithm, so any BlurHash decoder reads it.

Pure Python; build_index.py feeds it a small thumbnail (Pillow), which is all the detail a blur needs.
"""
from __future__ import annotations

import math

_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"


def _b83(value: int, length: int) -> str:
    return "".join(_CHARS[(value // 83 ** (length - i)) % 83] for i in range(1, length + 1))


def _to_linear(v: int) -> float:
    x = v / 255
    return x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4


def _to_srgb(v: float) -> int:
    x = min(max(v, 0.0), 1.0)
    return int(x * 12.92 * 255 + 0.5) if x <= 0.0031308 else int((1.055 * x ** (1 / 2.4) - 0.055) * 255 + 0.5)


def _sign_pow(v: float, e: float) -> float:
    return math.copysign(abs(v) ** e, v)


def encode(pixels: list[tuple[int, int, int]], width: int, height: int, cx: int = 4, cy: int = 3) -> str:
    """[pixels]: width*height (r, g, b) tuples, row by row. [cx] x [cy] components (1..9 each)."""
    if not (1 <= cx <= 9 and 1 <= cy <= 9) or len(pixels) != width * height or not pixels:
        raise ValueError("bad size")
    lin = [(_to_linear(r), _to_linear(g), _to_linear(b)) for r, g, b in pixels]
    cos_x = [[math.cos(math.pi * i * x / width) for x in range(width)] for i in range(cx)]
    cos_y = [[math.cos(math.pi * j * y / height) for y in range(height)] for j in range(cy)]
    factors = []
    for j in range(cy):
        for i in range(cx):
            norm = (1 if i == 0 and j == 0 else 2) / (width * height)
            r = g = b = 0.0
            for y in range(height):
                fy = cos_y[j][y]
                row = y * width
                for x in range(width):
                    f = cos_x[i][x] * fy
                    pr, pg, pb = lin[row + x]
                    r += f * pr
                    g += f * pg
                    b += f * pb
            factors.append((r * norm, g * norm, b * norm))
    dc, ac = factors[0], factors[1:]
    out = _b83((cx - 1) + (cy - 1) * 9, 1)
    if ac:
        actual = max(abs(c) for f in ac for c in f)
        quant = int(max(0, min(82, math.floor(actual * 166 - 0.5))))
        max_value = (quant + 1) / 166
        out += _b83(quant, 1)
    else:
        max_value = 1.0
        out += _b83(0, 1)
    out += _b83((_to_srgb(dc[0]) << 16) + (_to_srgb(dc[1]) << 8) + _to_srgb(dc[2]), 4)
    for f in ac:
        q = [int(max(0, min(18, math.floor(_sign_pow(c / max_value, 0.5) * 9 + 9.5)))) for c in f]
        out += _b83(q[0] * 19 * 19 + q[1] * 19 + q[2], 2)
    return out
