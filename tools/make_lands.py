"""Draws Neon Rush's ten made-up lands and writes them for both versions of the game.

Run: python3 tools/make_lands.py
Writes godot/data/arenas.json and app/src/main/java/.../game/Arenas.kt.

Every shape is invented here from simple maths (blobs, lobes, a lagoon), so no
map looks like a real country and there are no real borders to argue about.
Each mask is one bit per cell, row by row: 1 = playable.
"""
import base64
import json
import math
import os

import numpy as np

ROOT = os.path.join(os.path.dirname(__file__), "..")


def polar(base, waves, lobes=()):
    """A star-shaped blob: radius wobbles with angle, plus rounded lobes."""
    def inside(x, y):
        a = np.arctan2(y, x)
        r = np.hypot(x, y)
        edge = np.full_like(a, base)
        for k, amp, ph in waves:
            edge += amp * np.sin(k * a + ph)
        for ang, width, reach in lobes:
            d = np.angle(np.exp(1j * (a - ang)))
            edge += reach * np.exp(-(d / width) ** 2)
        return r < edge
    return inside


def minus(shape, cx, cy, rad):
    def inside(x, y):
        return shape(x, y) & (np.hypot(x - cx, y - cy) > rad)
    return inside


def union(*shapes):
    def inside(x, y):
        out = np.zeros_like(x, dtype=bool)
        for s in shapes:
            out |= s(x, y)
        return out
    return inside


def ellipse(cx, cy, rx, ry, rot=0.0):
    def inside(x, y):
        c, s = math.cos(rot), math.sin(rot)
        u = (x - cx) * c + (y - cy) * s
        v = -(x - cx) * s + (y - cy) * c
        return (u / rx) ** 2 + (v / ry) ** 2 < 1
    return inside


def superellipse(rx, ry, p, wobble=0.0):
    def inside(x, y):
        a = np.arctan2(y, x)
        w = 1 + wobble * np.sin(6 * a) + wobble * 0.6 * np.sin(11 * a + 1.3)
        return (np.abs(x / rx) ** p + np.abs(y / ry) ** p) < w
    return inside


# (code, name, target playable cells, shape)
LANDS = [
    ("BAY", "Neon Bay", 2300, minus(polar(0.8, [(2, 0.07, 0.4), (3, 0.05, 1.1), (5, 0.03, 2.0)]), 0.85, 0.15, 0.45)),
    ("PUL", "Pulse Island", 2600, polar(0.85, [(3, 0.08, 0.2), (4, 0.05, 2.2), (7, 0.025, 0.7)])),
    ("VLT", "Volt Canyon", 3100, minus(ellipse(0, 0, 0.98, 0.5, 0.35), -0.05, 0.62, 0.35)),
    ("DLT", "Synth Delta", 3600, polar(0.7, [(3, 0.22, -math.pi / 2), (6, 0.03, 0.4), (9, 0.015, 1.0)])),
    ("LAG", "Laser Lagoon", 3900, minus(polar(0.9, [(2, 0.05, 0.9), (5, 0.04, 0.1)]), 0.1, -0.05, 0.3)),
    ("CHR", "Chrome Coast", 4300, polar(0.78, [(2, 0.12, 0.3), (5, 0.06, 1.7), (8, 0.035, 0.2), (13, 0.015, 2.5)])),
    ("REF", "Star Reef", 4500, polar(0.62, [(5, 0.2, -math.pi / 2), (10, 0.03, 0.0)])),
    ("PKS", "Grid Peaks", 4900, union(ellipse(0, 0, 0.95, 0.38), ellipse(0, 0, 0.38, 0.95), ellipse(0, 0, 0.62, 0.62))),
    ("CRT", "Nova Crater", 5400, minus(minus(polar(0.9, [(3, 0.06, 1.0), (4, 0.05, 0.2), (7, 0.03, 2.2)]), -0.35, 0.25, 0.18), 0.38, -0.2, 0.15)),
    ("COR", "Core City", 6200, superellipse(0.9, 0.82, 4.0, 0.04)),
]


def shift(m, dx, dy):
    out = np.zeros_like(m)
    h, w = m.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = m[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def erode(m):
    out = m.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        out &= shift(m, dx, dy)
    return out


def dilate(m):
    out = m.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        out |= shift(m, dx, dy)
    return out


def largest_part(m):
    """Keeps only the biggest connected piece, so every cell can be reached."""
    h, w = m.shape
    seen = np.zeros_like(m)
    best = None
    for y0 in range(h):
        for x0 in range(w):
            if not m[y0, x0] or seen[y0, x0]:
                continue
            part = []
            stack = [(y0, x0)]
            seen[y0, x0] = True
            while stack:
                y, x = stack.pop()
                part.append((y, x))
                for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                    if 0 <= ny < h and 0 <= nx < w and m[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        stack.append((ny, nx))
            if best is None or len(part) > len(best):
                best = part
    out = np.zeros_like(m)
    for y, x in best:
        out[y, x] = True
    return out


def rasterize(shape, size):
    c = (np.arange(size) + 0.5) / size * 2 - 1
    x, y = np.meshgrid(c * 1.08, c * 1.08)
    m = shape(x, y)
    # Smooth away one-cell spikes and notches, which play badly.
    m = dilate(erode(m))
    m = erode(dilate(m))
    m[0, :] = m[-1, :] = m[:, 0] = m[:, -1] = False
    return largest_part(m)


def fit(shape, target):
    """The grid size whose mask has about the target number of playable cells."""
    lo, hi = 30, 260
    while lo < hi:
        mid = (lo + hi) // 2
        if rasterize(shape, mid).sum() < target:
            lo = mid + 1
        else:
            hi = mid
    return lo, rasterize(shape, lo)


def encode(m):
    flat = m.flatten()
    data = bytearray((len(flat) + 7) // 8)
    for i, v in enumerate(flat):
        if v:
            data[i >> 3] |= 1 << (i & 7)
    return base64.b64encode(bytes(data)).decode()


lands = []
for code, name, target, shape in LANDS:
    size, mask = fit(shape, target)
    lands.append({"code": code, "name": name, "size": size, "bits": encode(mask)})
    print(f"{code} {name:14s} size={size} cells={int(mask.sum())}")
    if os.environ.get("PREVIEW"):
        for row in mask[:: max(1, size // 40)]:
            print("".join("#" if v else "." for v in row[:: max(1, size // 70)]))

with open(os.path.join(ROOT, "godot", "data", "arenas.json"), "w") as f:
    json.dump(lands, f)

kt = os.path.join(ROOT, "app", "src", "main", "java", "com", "vedshubham", "chalkrush", "game", "Arenas.kt")
rows = ",\n".join(f'        Arena("{l["code"]}", "{l["name"]}", {l["size"]}, "{l["bits"]}")' for l in lands)
with open(kt, "w") as f:
    f.write(f'''package com.vedshubham.chalkrush.game

import java.util.Base64

/**
 * The made-up neon lands each level is played on. Each mask is one bit per
 * cell, row by row: 1 = playable. Made by tools/make_lands.py; not edited by hand.
 */
class Arena(val code: String, val name: String, val size: Int, private val bits: String) {{
    /** Which cells are inside the land. */
    fun mask(): BooleanArray {{
        val bytes = Base64.getDecoder().decode(bits)
        return BooleanArray(size * size) {{ (bytes[it / 8].toInt() shr (it % 8)) and 1 == 1 }}
    }}
}}

object Arenas {{
    val all = listOf(
{rows},
    )

    fun byCode(code: String) = all.first {{ it.code == code }}
}}
''')
print("written")
