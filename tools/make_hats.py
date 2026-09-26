#!/usr/bin/env python3
"""LavaVisual hat and wing models.

    python3 tools/make_hats.py                  # writes assets/lavavisual/hats.json
    python3 tools/make_hats.py --preview a.png  # also renders every model (z-buffered, same shading as the game)

Hats: origin = top of the head (skin hat layer), +Y up, +Z towards the face, units = blocks at player scale 1
(the head is 0.47 blocks wide). Wings: origin = attachment point on the upper back, same axes; each model is
built for the right side (+X) inside a mirrored group. effects/Hats.java interprets the same JSON; `Builder`
below mirrors it one to one (culling, smooth normals, materials, lighting), so the preview is what the game draws.

Part keys
  revolve [[r, y], ...]  surface of revolution; open profiles run from the outer/lower end towards the axis,
                         closed ones counter-clockwise in (r, y). Options: closed, two (two-sided), seg, phase,
                         arc [from, to] in degrees (0 = front), curl [lift, r0, r1] (brim lifted at the sides),
                         crease (degrees, default 50: sharper profile corners stay hard).
  tube [[x, y, z], ...]  swept circle along points; radius [r0, r1], power, sides, caps, closed.
  torus [R, r]           closed tube around the Y axis at height y.
  sphere [x, y, z], r    r may be [rx, ry, rz].
  gem [x, y, z]          bipyramid: r, up, down, sides (flat facets).
  prism [[x, y], ...]    star-shaped polygon in the XY plane extruded along z [z0, z1].
  poly [[x, y, z(, t)]]  flat polygon, two-sided, fanned from its centroid or from fan [x, y, z(, t)].
  strip [[[x,y,z,t], [x,y,z,t]], ...]  two-sided ribbon of quads between left/right point pairs (feathers, flames).
  sheet [[[x,y,z,t], ...], ...]  curved surface grid (rows x cols) with smooth per-vertex normals (central
                         differences): volumetric feathers, billowing membranes, wing blades. Two-sided unless
                         two=false (closed slabs made by slab()). At FPS Boost quality < 0.5 every other row is used.
  ao [from, to]          (any part) brightness multiplier along t: tucked-in feather bases, membrane roots.
  glowring [R, w], glowflat [R], glowdisc [x, y, z] + size: soft glow (no depth write).
  group {at, rot [x, y, z] (applied Z, X, then Y), spin [axis, rad/s], bob [amp, freq, phase step],
         swing [[axis, degrees, speed, phase], ...] (wing beats, scaled by the flap amount and tempo),
         repeat n (around Y), mirror (X)} + parts.
Paint: key, [k0, k1, ...] gradient along t, or {"cycle": [...]} by repeat index. Keys: c (main colour), l (second
tone), m, d (dark tint), dl, cw, lw, w (white), k (black), g (gold), p (pink), gr (leaf green), r (berry red).
alt + pattern (stripes n [twist], ridges k, facets) apply to the "pattern" style only.
mat: matte, satin (default), gloss, metal, gem, fur, glow (unlit, like lit=false).
"""
import json
import math
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/hats.json'
PX = 0.9375 / 16


def r4(v):
    return round(v, 4)


def bezier(a, b, c, d, n):
    points = []
    for i in range(n):
        t = i / (n - 1)
        u = 1 - t
        points.append([r4(u * u * u * a[k] + 3 * u * u * t * b[k] + 3 * u * t * t * c[k] + t * t * t * d[k]) for k in range(3)])
    return points


def star_polygon(outer, inner, points=5):
    result = []
    for i in range(points * 2):
        angle = math.pi / 2 + i * math.pi / points
        radius = outer if i % 2 == 0 else inner
        result.append([r4(math.cos(angle) * radius), r4(math.sin(angle) * radius)])
    return result


def cone_profile(radius, height, rings):
    return [[r4(radius * (1 - i / rings)), r4(height * i / rings)] for i in range(rings + 1)]


def circle(cx, cy, z, radius, n=14, t=0.5):
    return [[r4(cx + radius * math.cos(2 * math.pi * i / n)), r4(cy + radius * math.sin(2 * math.pi * i / n)), r4(z), t] for i in range(n)]


def catmull(points, per=4, closed=True):
    """Smooth closed outline through 2D points."""
    out = []
    n = len(points)
    for i in range(n if closed else n - 1):
        p0, p1, p2, p3 = (points[(i + k) % n] for k in (-1, 0, 1, 2))
        for s in range(per):
            t = s / per
            t2, t3 = t * t, t * t * t
            out.append([0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                               + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)])
    return out


def spline(points, per=4):
    """Smooth open curve through 2D points (Catmull-Rom with clamped ends), ends included."""
    pts = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for s in range(per):
            t = s / per
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)))
    out.append(tuple(points[-1]))
    return out


def ribbon(base, angle, length, width, profile, z=0.0, t0=0.0, t1=1.0, bend=0.0, wave=0.0, samples=None):
    """Strip in the XY plane: base point, direction angle in degrees (0 = +X, 90 = up), width profile along the
    length; bend curves it sideways (towards the left normal), wave adds an S-curve (flames)."""
    a = math.radians(angle)
    d, n = (math.cos(a), math.sin(a)), (-math.sin(a), math.cos(a))
    samples = samples or [i / (len(profile) - 1) for i in range(len(profile))]
    rows = []
    for s, w in zip(samples, profile):
        off = bend * length * s * s + wave * length * math.sin(s * math.pi * 1.6) * s
        cx, cy = base[0] + d[0] * s * length + n[0] * off, base[1] + d[1] * s * length + n[1] * off
        half = width * w / 2
        t = t0 + (t1 - t0) * s
        rows.append([[r4(cx + n[0] * half), r4(cy + n[1] * half), r4(z), r4(t)], [r4(cx - n[0] * half), r4(cy - n[1] * half), r4(z), r4(t)]])
    return rows


FEATHER = [0.16, 0.42, 0.52, 0.56, 0.56, 0.5, 0.36, 0.0]
FEATHER_AT = [0.0, 0.1, 0.3, 0.5, 0.7, 0.84, 0.94, 1.0]
FLAME = [0.3, 0.62, 0.66, 0.56, 0.4, 0.24, 0.1, 0.0]


def lerp2(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def scallop(a, b, toward, depth, n=4, t_edge=1.0):
    """Points strictly between a and b on a curve pulled towards `toward` (bat/dragon membranes)."""
    ctrl = lerp2(lerp2(a, b, 0.5), toward, depth)
    pts = []
    for i in range(1, n):
        u = i / n
        x = (1 - u) ** 2 * a[0] + 2 * (1 - u) * u * ctrl[0] + u * u * b[0]
        y = (1 - u) ** 2 * a[1] + 2 * (1 - u) * u * ctrl[1] + u * u * b[1]
        pts.append((x, y, t_edge * (0.82 + 0.18 * abs(2 * u - 1))))
    return pts


# ---------------------------------------------------------------------------------------------- hats

def hats():
    h = []
    tassels = [{'group': {'repeat': 8, 'bob': [0.004, 3.1, 0.8]}, 'parts': [
        {'tube': [[0, 0.004, 0.515], [0, -0.03, 0.52], [0, -0.058, 0.522]], 'radius': [0.004, 0.004], 'sides': 5, 'caps': False, 'paint': 'g', 'mat': 'metal', 'detail': True},
        {'sphere': [0, -0.068, 0.522], 'r': 0.013, 'seg': 8, 'paint': 'l', 'mat': 'gloss', 'detail': True},
    ]}]
    h.append({'name': 'Конус', 'parts': [
        {'revolve': cone_profile(0.52, 0.26, 4), 'seg': 40, 'two': True, 'paint': ['c', 'm'], 'alt': ['m', 'l'], 'pattern': {'stripes': 20}, 'mat': 'satin'},
        {'torus': [0.52, 0.012], 'seg': 40, 'sides': 6, 'paint': 'g', 'mat': 'metal'},
        {'torus': [0.26, 0.006], 'y': 0.131, 'seg': 32, 'sides': 5, 'paint': 'g', 'mat': 'metal'},
        {'revolve': [[0.034, 0.25], [0.03, 0.27], [0.0, 0.272]], 'seg': 12, 'paint': 'g', 'mat': 'metal'},
        {'gem': [0, 0.3, 0], 'r': 0.022, 'up': 0.045, 'down': 0.026, 'sides': 6, 'paint': 'l', 'alt': 'lw', 'pattern': {'facets': True}, 'mat': 'gem'},
        *tassels,
    ]})
    h.append({'name': 'Нимб', 'parts': [
        {'group': {'at': [0, 0.2, 0], 'bob': [0.012, 2.2, 0]}, 'parts': [
            {'torus': [0.2, 0.022], 'seg': 44, 'sides': 10, 'paint': ['l', 'c'], 'mat': 'gloss'},
            {'torus': [0.2, 0.009], 'y': 0.021, 'seg': 44, 'sides': 6, 'paint': 'lw', 'mat': 'glow'},
            {'glowring': [0.2, 0.1], 'paint': 'c', 'alpha': 0.55},
            {'group': {'repeat': 6, 'spin': ['y', 0.7], 'bob': [0.01, 3.0, 1.05]}, 'parts': [
                {'gem': [0.2, 0.05, 0], 'r': 0.01, 'up': 0.018, 'down': 0.018, 'sides': 4, 'paint': 'lw', 'mat': 'gem', 'detail': True},
                {'glowdisc': [0.2, 0.05, 0], 'size': 0.045, 'paint': 'l', 'alpha': 0.4, 'detail': True},
            ]},
        ]},
    ]})
    spikes = []
    for k in range(10):
        a0, a1, am = (math.radians(36 * k + d) for d in (-18, 18, 0))
        tall = k % 2 == 0
        top = 0.082 + (0.118 if tall else 0.07)
        apex_r = 0.2 * math.cos(math.radians(18)) + 0.014
        p0 = [r4(0.2 * math.sin(a0)), 0.082, r4(0.2 * math.cos(a0))]
        p1 = [r4(0.2 * math.sin(a1)), 0.082, r4(0.2 * math.cos(a1))]
        apex = [r4(apex_r * math.sin(am)), r4(top), r4(apex_r * math.cos(am))]
        spikes.append({'poly': [p0, p1, apex], 'paint': 'g', 'mat': 'metal'})
        spikes.append({'sphere': apex, 'r': 0.017 if tall else 0.012, 'seg': 10, 'paint': 'w', 'mat': 'gloss'})
    h.append({'name': 'Корона', 'parts': [
        {'revolve': [[0.186, 0.0], [0.203, 0.0], [0.206, 0.041], [0.203, 0.082], [0.186, 0.082]], 'closed': True, 'seg': 10, 'phase': 18, 'crease': 30, 'paint': 'g', 'mat': 'metal'},
        {'torus': [0.203, 0.02], 'y': 0.004, 'seg': 30, 'sides': 8, 'paint': 'w', 'mat': 'fur'},
        {'sphere': [0, 0.01, 0], 'r': [0.18, 0.13, 0.18], 'seg': 18, 'paint': 'd', 'mat': 'fur'},
        *spikes,
        {'group': {'repeat': 5}, 'parts': [
            {'gem': [0, 0.045, 0.2], 'r': 0.022, 'up': 0.028, 'down': 0.028, 'sides': 4, 'paint': 'c', 'alt': 'cw', 'pattern': {'facets': True}, 'mat': 'gem'},
        ]},
        {'group': {'repeat': 5, 'rot': [0, 36, 0]}, 'parts': [
            {'sphere': [0, 0.045, 0.206], 'r': 0.011, 'seg': 8, 'paint': 'l', 'mat': 'gem', 'detail': True},
        ]},
        {'sphere': [0, 0.155, 0], 'r': 0.03, 'seg': 12, 'paint': 'g', 'mat': 'metal'},
        {'group': {'at': [0, 0.19, 0]}, 'parts': [
            {'prism': [[-0.007, -0.012], [0.007, -0.012], [0.007, 0.03], [-0.007, 0.03]], 'z': [-0.006, 0.006], 'paint': 'g', 'mat': 'metal'},
            {'prism': [[-0.02, 0.005], [0.02, 0.005], [0.02, 0.017], [-0.02, 0.017]], 'z': [-0.006, 0.006], 'paint': 'g', 'mat': 'metal'},
        ]},
    ]})
    bow = [[0.0, 0.0], [0.05, 0.022], [0.056, -0.02]]
    h.append({'name': 'Цилиндр', 'parts': [
        {'revolve': [[0.19, 0.0], [0.355, 0.0], [0.366, 0.011], [0.355, 0.022], [0.19, 0.022]], 'closed': True, 'seg': 40,
         'curl': [0.05, 0.25, 0.366], 'crease': 60, 'paint': 'd', 'mat': 'satin'},
        {'revolve': [[0.2, 0.02], [0.192, 0.2], [0.212, 0.375], [0.21, 0.378], [0.0, 0.38]], 'seg': 36, 'crease': 40, 'paint': 'd', 'mat': 'gloss'},
        {'revolve': [[0.197, 0.032], [0.208, 0.032], [0.205, 0.1], [0.194, 0.1]], 'closed': True, 'seg': 36, 'paint': 'c', 'mat': 'satin'},
        {'group': {'at': [0.203, 0.066, 0.03], 'rot': [0, 70, 0]}, 'parts': [
            {'prism': bow, 'z': [-0.005, 0.005], 'paint': 'c', 'mat': 'satin'},
            {'prism': [[-p[0], p[1]] for p in bow], 'z': [-0.005, 0.005], 'paint': 'c', 'mat': 'satin'},
            {'sphere': [0, 0, 0.004], 'r': 0.013, 'seg': 8, 'paint': 'm', 'mat': 'satin'},
        ]},
        {'group': {'at': [0, 0.066, 0.208]}, 'parts': [
            {'prism': [[-0.022, -0.03], [0.022, -0.03], [0.022, 0.03], [-0.022, 0.03]], 'z': [0.0, 0.005], 'paint': 'g', 'mat': 'metal'},
            {'prism': [[-0.012, -0.019], [0.012, -0.019], [0.012, 0.019], [-0.012, 0.019]], 'z': [0.005, 0.007], 'paint': 'd', 'mat': 'gloss'},
        ]},
    ]})
    witch = bezier([0, 0.004, 0], [0, 0.3, 0], [0, 0.46, -0.04], [0, 0.5, -0.24], 18)
    h.append({'name': 'Ведьмина', 'parts': [
        {'revolve': [[0.45, 0.045], [0.41, 0.022], [0.34, 0.007], [0.26, 0.0], [0.17, 0.0]], 'seg': 44, 'two': True, 'paint': 'd', 'mat': 'matte'},
        {'tube': witch, 'radius': [0.2, 0.0], 'sides': 22, 'caps': False, 'paint': ['d', 'dl'], 'mat': 'matte'},
        {'revolve': [[0.186, 0.012], [0.208, 0.012], [0.197, 0.075], [0.175, 0.075]], 'closed': True, 'seg': 36, 'paint': 'c', 'mat': 'satin'},
        {'group': {'at': [0, 0.043, 0.203], 'rot': [-10, 0, 0]}, 'parts': [
            {'prism': [[-0.03, -0.025], [0.03, -0.025], [0.03, 0.025], [-0.03, 0.025]], 'z': [0.0, 0.006], 'paint': 'g', 'mat': 'metal'},
            {'prism': [[-0.017, -0.013], [0.017, -0.013], [0.017, 0.013], [-0.017, 0.013]], 'z': [0.006, 0.009], 'paint': 'd', 'mat': 'matte'},
        ]},
        {'group': {'at': [0.1, 0.2, 0.105], 'rot': [-25, 40, 12]}, 'parts': [
            {'prism': star_polygon(0.034, 0.014), 'z': [-0.004, 0.004], 'paint': 'g', 'mat': 'metal'},
        ]},
        {'group': {'at': [-0.07, 0.3, 0.07], 'rot': [-30, -35, -8]}, 'parts': [
            {'prism': star_polygon(0.022, 0.009), 'z': [-0.003, 0.003], 'paint': 'l', 'mat': 'glow'},
            {'glowdisc': [0, 0, 0], 'size': 0.06, 'paint': 'l', 'alpha': 0.35, 'detail': True},
        ]},
        {'group': {'at': [0, 0.5, -0.24], 'bob': [0.006, 2.4, 0]}, 'parts': [
            {'tube': [[0, 0, 0], [0, -0.03, -0.005], [0, -0.055, -0.01]], 'radius': [0.003, 0.003], 'sides': 5, 'caps': False, 'paint': 'g', 'mat': 'metal', 'detail': True},
            {'gem': [0, -0.07, -0.01], 'r': 0.014, 'up': 0.016, 'down': 0.026, 'sides': 5, 'paint': 'l', 'mat': 'gem'},
        ]},
    ]})
    confetti = [{'group': {'repeat': 7, 'rot': [0, 17, 0]}, 'parts': [
        {'sphere': [0, 0.08 + 0.04 * (i % 3), r4(0.17 * (1 - (0.08 + 0.04 * (i % 3)) / 0.39) + 0.004)], 'r': 0.01, 'seg': 6,
         'paint': {'cycle': ['w', 'g', 'lw', 'cw', 'p', 'w', 'g']}, 'mat': 'gloss', 'detail': True}
    ]} for i in range(3)]
    for i, group in enumerate(confetti):
        group['group']['rot'] = [0, 17 + 41 * i, 0]
        group['parts'][0]['sphere'][1] = r4(0.07 + 0.09 * i)
        group['parts'][0]['sphere'][2] = r4(0.17 * (1 - (0.07 + 0.09 * i) / 0.39) + 0.005)
    h.append({'name': 'Колпак', 'parts': [
        {'group': {'at': [0.035, -0.004, 0], 'rot': [0, 0, -13]}, 'parts': [
            {'revolve': cone_profile(0.17, 0.39, 9), 'seg': 30, 'paint': 'c', 'alt': 'l', 'pattern': {'stripes': 8, 'twist': 9}, 'mat': 'satin'},
            *confetti,
            {'torus': [0.17, 0.022], 'y': 0.01, 'seg': 30, 'sides': 9, 'paint': 'w', 'mat': 'fur'},
            {'sphere': [0, 0.4, 0], 'r': 0.05, 'seg': 14, 'paint': 'w', 'mat': 'fur'},
            {'sphere': [0.02, 0.425, 0.02], 'r': 0.03, 'seg': 10, 'paint': 'w', 'mat': 'fur', 'detail': True},
        ]},
    ]})
    horn = bezier([0.115, -0.02, 0.06], [0.145, 0.1, 0.085], [0.26, 0.17, 0.03], [0.25, 0.275, -0.06], 16)
    h.append({'name': 'Рожки', 'parts': [
        {'group': {'mirror': True}, 'parts': [
            {'tube': horn, 'radius': [0.062, 0.0], 'power': 0.9, 'sides': 14, 'caps': False, 'paint': ['d', 'c', 'lw'], 'alt': ['k', 'd', 'c'],
             'pattern': {'ridges': 3}, 'mat': 'gloss'},
            {'group': {'at': [0.127, 0.035, 0.07], 'rot': [8, 0, -22]}, 'parts': [
                {'torus': [0.058, 0.009], 'seg': 18, 'sides': 6, 'paint': 'g', 'mat': 'metal'},
            ]},
            {'group': {'at': [0.141, 0.075, 0.08], 'rot': [8, 0, -30]}, 'parts': [
                {'torus': [0.05, 0.006], 'seg': 16, 'sides': 5, 'paint': 'g', 'mat': 'metal', 'detail': True},
            ]},
        ]},
    ]})
    ear = [[-0.09, 0.0], [0.09, 0.0], [0.016, 0.19]]
    inner = [[-0.054, 0.018], [0.054, 0.018], [0.011, 0.14]]
    h.append({'name': 'Ушки', 'parts': [
        {'group': {'mirror': True}, 'parts': [
            {'group': {'at': [0.13, -0.012, -0.005], 'rot': [-8, 0, -12]}, 'parts': [
                {'prism': ear, 'z': [-0.02, 0.02], 'paint': ['c', 'm'], 'mat': 'fur'},
                {'prism': inner, 'z': [0.02, 0.026], 'paint': 'p', 'mat': 'satin'},
                {'sphere': [0.016, 0.185, 0], 'r': 0.022, 'seg': 8, 'paint': 'cw', 'mat': 'fur', 'detail': True},
            ]},
        ]},
        {'group': {'at': [0.06, 0.02, 0.08], 'rot': [-20, 20, 10]}, 'parts': [
            {'prism': bow, 'z': [-0.005, 0.005], 'paint': 'l', 'mat': 'gloss'},
            {'prism': [[-p[0], p[1]] for p in bow], 'z': [-0.005, 0.005], 'paint': 'l', 'mat': 'gloss'},
            {'sphere': [0, 0, 0.003], 'r': 0.013, 'seg': 8, 'paint': 'lw', 'mat': 'gloss'},
        ]},
    ]})
    h.append({'name': 'Кристалл', 'parts': [
        {'group': {'at': [0, 0.34, 0], 'bob': [0.026, 1.6, 0], 'spin': ['y', 0.8]}, 'parts': [
            {'gem': [0, 0, 0], 'r': 0.095, 'up': 0.175, 'down': 0.115, 'sides': 6, 'paint': ['c', 'lw'], 'alt': ['l', 'lw'], 'pattern': {'facets': True}, 'mat': 'gem'},
            {'gem': [0, 0.005, 0], 'r': 0.045, 'up': 0.2, 'down': 0.14, 'sides': 6, 'paint': 'lw', 'mat': 'glow', 'alpha': 0.35},
            {'glowdisc': [0, 0, 0], 'size': 0.3, 'paint': 'c', 'alpha': 0.4},
        ]},
        {'group': {'at': [0, 0.3, 0], 'repeat': 5, 'bob': [0.02, 2.0, 1.25], 'spin': ['y', -1.3]}, 'parts': [
            {'gem': [0.21, 0, 0], 'r': 0.026, 'up': 0.05, 'down': 0.036, 'sides': 4, 'paint': 'l', 'alt': 'lw', 'pattern': {'facets': True}, 'mat': 'gem'},
            {'glowdisc': [0.21, 0, 0], 'size': 0.06, 'paint': 'l', 'alpha': 0.3, 'detail': True},
        ]},
        {'glowring': [0.21, 0.03], 'y': 0.3, 'paint': 'l', 'alpha': 0.2},
    ]})
    h.append({'name': 'Сомбреро', 'parts': [
        {'revolve': [[0.64, 0.085], [0.6, 0.048], [0.53, 0.02], [0.43, 0.006], [0.31, 0.0], [0.18, 0.0]], 'seg': 48, 'two': True,
         'paint': 'c', 'alt': 'm', 'pattern': {'stripes': 16}, 'mat': 'satin'},
        {'torus': [0.64, 0.018], 'y': 0.085, 'seg': 48, 'sides': 8, 'paint': 'l', 'mat': 'satin'},
        {'torus': [0.47, 0.006], 'y': 0.014, 'seg': 40, 'sides': 5, 'paint': 'g', 'mat': 'metal'},
        {'revolve': [[0.19, 0.0], [0.182, 0.17], [0.168, 0.25], [0.13, 0.298], [0.07, 0.32], [0.0, 0.325]], 'seg': 36, 'paint': 'c', 'alt': 'm',
         'pattern': {'stripes': 12}, 'mat': 'satin'},
        {'revolve': [[0.186, 0.014], [0.198, 0.014], [0.191, 0.08], [0.179, 0.08]], 'closed': True, 'seg': 36, 'paint': 'l', 'mat': 'satin'},
        {'group': {'repeat': 12}, 'parts': [{'gem': [0, 0.047, 0.196], 'r': 0.009, 'up': 0.013, 'down': 0.013, 'sides': 4, 'paint': 'g', 'mat': 'metal', 'detail': True}]},
        {'group': {'repeat': 16, 'bob': [0.006, 3.0, 0.6]}, 'parts': [
            {'tube': [[0, 0.085, 0.655], [0, 0.07, 0.66], [0, 0.056, 0.662]], 'radius': [0.003, 0.003], 'sides': 4, 'caps': False, 'paint': 'k', 'mat': 'matte', 'detail': True},
            {'sphere': [0, 0.047, 0.662], 'r': 0.017, 'seg': 8, 'paint': {'cycle': ['g', 'l', 'w', 'c']}, 'mat': 'fur'},
        ]},
    ]})
    def cap_visor():
        # Bill of a baseball cap: starts under the crown's front, widest in the middle (0.145 past the crown),
        # tapering to nothing at the sides, bending down a little towards the edge and more at the sides.
        rows, cols, reach = 6, 17, math.radians(70)
        grid = []
        for i in range(rows):
            u = i / (rows - 1)
            row = []
            for j in range(cols):
                v = j / (cols - 1)
                phi = -reach + 2 * reach * v
                inner = (0.248 * math.sin(phi), 0.248 * math.cos(phi))
                outer = (0.262 * math.sin(phi), 0.40 * math.cos(phi))
                taper = math.sin(math.pi * v) ** 0.55
                x = inner[0] + (outer[0] - inner[0]) * u * taper
                z = inner[1] + (outer[1] - inner[1]) * u * taper
                y = 0.014 - 0.026 * u * u * taper - 0.018 * (2 * v - 1) ** 2 * u
                row.append(v4((x, y, z), u))
            grid.append(row)
        front, back, rim = slab(grid, 0.012)
        return [{'sheet': front, 'paint': 'd', 'mat': 'satin', 'two': False},
                {'sheet': back, 'paint': 'd', 'mat': 'satin', 'ao': [0.8, 0.65], 'two': False},
                {'sheet': rim, 'paint': 'd', 'mat': 'satin'}]
    # Propeller cap: the same dome and curved visor as the cap below, a small propeller on a short mast.
    blade = [[0.012, -0.011], [0.085, -0.023], [0.135, -0.018], [0.145, 0.0], [0.135, 0.015], [0.08, 0.019], [0.012, 0.011]]
    h.append({'name': 'Пропеллер', 'parts': [
        {'revolve': [[0.255, 0.0], [0.25, 0.045], [0.228, 0.092], [0.185, 0.13], [0.12, 0.155], [0.06, 0.166], [0.0, 0.169]], 'seg': 36,
         'paint': 'c', 'alt': 'l', 'pattern': {'stripes': 4}, 'mat': 'satin'},
        *cap_visor(),
        {'torus': [0.253, 0.01], 'y': 0.006, 'seg': 36, 'sides': 6, 'paint': 'd', 'mat': 'satin'},
        {'revolve': [[0.01, 0.164], [0.01, 0.196], [0.0, 0.196]], 'seg': 10, 'paint': 'g', 'mat': 'metal'},
        {'sphere': [0, 0.2, 0], 'r': 0.017, 'seg': 12, 'paint': 'g', 'mat': 'metal'},
        {'group': {'at': [0, 0.2, 0], 'repeat': 3, 'spin': ['y', 9.0]}, 'parts': [
            {'group': {'rot': [-76, 0, 0]}, 'parts': [
                {'prism': blade, 'z': [-0.003, 0.003], 'paint': {'cycle': ['c', 'l', 'w']}, 'mat': 'gloss'},
            ]},
        ]},
        {'glowflat': [0.15], 'y': 0.2, 'paint': 'l', 'alpha': 0.1},
    ]})
    cycle = {'cycle': ['c', 'l', 'g', 'c', 'l']}
    h.append({'name': 'Звёзды', 'parts': [
        {'group': {'at': [0, 0.075, 0], 'repeat': 5, 'bob': [0.032, 2.3, 1.2566], 'spin': ['y', 1.1]}, 'parts': [
            {'group': {'at': [0.31, 0, 0], 'rot': [0, 90, 0], 'spin': ['z', 2.2]}, 'parts': [
                {'prism': star_polygon(0.066, 0.028), 'z': [-0.009, 0.009], 'paint': cycle, 'mat': 'gloss'},
                {'prism': star_polygon(0.04, 0.017), 'z': [0.009, 0.012], 'paint': 'w', 'mat': 'glow', 'alpha': 0.5},
            ]},
            {'glowdisc': [0.31, 0, 0], 'size': 0.17, 'paint': cycle, 'alpha': 0.34},
        ]},
        {'group': {'at': [0, 0.075, 0], 'repeat': 10, 'spin': ['y', 1.1], 'rot': [0, 18, 0]}, 'parts': [
            {'glowdisc': [0.31, 0, 0], 'size': 0.035, 'paint': 'lw', 'alpha': 0.35, 'detail': True},
        ]},
        {'glowring': [0.31, 0.02], 'y': 0.075, 'paint': 'c', 'alpha': 0.18},
    ]})
    santa = bezier([0, 0.03, 0], [0, 0.27, -0.02], [0.03, 0.375, -0.15], [0.09, 0.255, -0.285], 18)
    h.append({'name': 'Санта', 'parts': [
        {'tube': santa, 'radius': [0.205, 0.022], 'power': 1.1, 'sides': 22, 'caps': False, 'paint': ['c', 'm'], 'mat': 'fur'},
        {'torus': [0.215, 0.052], 'y': 0.035, 'seg': 30, 'sides': 10, 'paint': 'w', 'mat': 'fur'},
        {'group': {'repeat': 10, 'rot': [0, 9, 0]}, 'parts': [
            {'sphere': [0, 0.06, 0.232], 'r': 0.026, 'seg': 7, 'paint': 'w', 'mat': 'fur', 'detail': True},
        ]},
        {'sphere': [0.09, 0.255, -0.285], 'r': 0.058, 'seg': 14, 'paint': 'w', 'mat': 'fur'},
        {'group': {'at': [0.13, 0.07, 0.2], 'rot': [-12, 30, 0]}, 'parts': [
            {'prism': [[0, 0], [0.028, 0.02], [0.062, 0.012], [0.03, -0.01]], 'z': [-0.003, 0.003], 'paint': 'gr', 'mat': 'gloss'},
            {'prism': [[0, 0], [-0.028, 0.02], [-0.062, 0.012], [-0.03, -0.01]], 'z': [-0.003, 0.003], 'paint': 'gr', 'mat': 'gloss'},
            {'sphere': [0.0, 0.008, 0.01], 'r': 0.012, 'seg': 8, 'paint': 'r', 'mat': 'gloss'},
            {'sphere': [0.014, -0.004, 0.012], 'r': 0.011, 'seg': 8, 'paint': 'r', 'mat': 'gloss'},
        ]},
    ]})
    h.append({'name': 'Кепка', 'parts': [
        {'revolve': [[0.255, 0.0], [0.25, 0.045], [0.228, 0.092], [0.185, 0.13], [0.12, 0.155], [0.06, 0.166], [0.0, 0.169]], 'seg': 36,
         'paint': 'c', 'alt': 'm', 'pattern': {'stripes': 6}, 'mat': 'satin'},
        *cap_visor(),
        {'torus': [0.253, 0.01], 'y': 0.006, 'seg': 36, 'sides': 6, 'paint': 'd', 'mat': 'satin'},
        {'sphere': [0, 0.169, 0], 'r': 0.021, 'seg': 10, 'paint': 'l', 'mat': 'satin'},
        {'group': {'at': [0, 0.085, 0.228], 'rot': [-28, 0, 0]}, 'parts': [
            {'prism': [[0.045 * math.cos(2 * math.pi * i / 16), 0.045 * math.sin(2 * math.pi * i / 16)] for i in range(16)], 'z': [0.0, 0.004], 'paint': 'l', 'mat': 'satin'},
            {'prism': star_polygon(0.03, 0.013), 'z': [0.004, 0.008], 'paint': 'g', 'mat': 'metal'},
        ]},
    ]})
    return h


# ---------------------------------------------------------------------------------------------- capes

def cape_sheet(length=16.0, bottom=None, rows=19, cols=13, paint=('c', 'l'), mat='satin', thick=0.9, width=10.0, lining=('d',), lining_mat='satin'):
    """Cape cloth in cape space: the top edge runs across the shoulders at y=0, the cloth hangs down (-y) behind the
    back (-z). bottom(u) adds length (px) to the column at u in -1..1 (flame tongues, torn teeth). The side towards
    the body is the lining: it shows when the cloth lifts in the wind."""
    grid = []
    for i in range(rows):
        v = i / (rows - 1)
        row = []
        for j in range(cols):
            u = -1 + 2 * j / (cols - 1)
            extra = bottom(u) if bottom else 0.0
            x = u * width / 2 * (1 + 0.08 * v) * PX
            y = -v * (length + extra) * PX
            z = (0.25 * u * u - 0.55 * math.sin(math.pi * min(v, 1.0)) - 0.35 * v) * PX
            row.append(v4((x, y, z), v))
        grid.append(row)
    front, back, rim = slab(grid, thick * PX)
    return grid, [{'sheet': front, 'paint': list(lining), 'mat': lining_mat, 'ao': [0.72, 0.95], 'two': False},
                  {'sheet': back, 'paint': list(paint), 'mat': mat, 'ao': [0.8, 0.95], 'two': False},
                  {'sheet': rim, 'paint': paint[-1], 'mat': mat}]


def cape_edge(grid, paint='g', mat='metal', r=0.5, bottom=True):
    """Piping along both sides (and the bottom) of the cloth, slightly behind its middle so it wraps the edge."""
    rows, cols = len(grid), len(grid[0])
    left = [[q[0], q[1], q[2] - 0.45 * PX] for q in (grid[i][0] for i in range(rows))]
    right = [[q[0], q[1], q[2] - 0.45 * PX] for q in (grid[i][cols - 1] for i in range(rows))]
    parts = [{'tube': left, 'radius': [r * PX, r * PX], 'sides': 6, 'caps': False, 'paint': paint, 'mat': mat},
             {'tube': right, 'radius': [r * PX, r * PX], 'sides': 6, 'caps': False, 'paint': paint, 'mat': mat}]
    if bottom:
        low = [[q[0], q[1], q[2] - 0.45 * PX] for q in grid[rows - 1]]
        parts.append({'tube': low, 'radius': [r * PX, r * PX], 'sides': 6, 'caps': False, 'paint': paint, 'mat': mat})
    return parts


def on_cape(grid, u, v, lift=1.2):
    """Point on the back of the cloth at (u, v) in 0..1, lifted off it (px) so details sit on the outside."""
    rows, cols = len(grid), len(grid[0])
    q = grid[min(rows - 1, round(v * (rows - 1)))][min(cols - 1, round(u * (cols - 1)))]
    return [r4(q[0]), r4(q[1]), r4(q[2] - lift * PX)]


def capes():
    c = []
    grid, cloth = cape_sheet(paint=('c', 'l'), lining=('dl', 'd'))
    c.append({'name': 'Классический', 'parts': cloth + cape_edge(grid)})

    grid, cloth = cape_sheet(length=17.5, paint=('d', 'c'), lining=('l', 'lw'))
    rows, cols = len(grid), len(grid[0])
    band_top = round(0.84 * (rows - 1))
    band = [[[q[0], q[1], q[2] - 1.2 * PX, 0.0], [p2[0], p2[1], p2[2] - 1.2 * PX, 1.0]]
            for q, p2 in zip(grid[band_top], grid[rows - 1])]
    spots = [{'sphere': on_cape(grid, u, v, 1.35), 'r': [0.38 * PX, 0.62 * PX, 0.3 * PX], 'seg': 6, 'paint': 'k', 'mat': 'fur', 'detail': True}
             for u, v in ((0.12, 0.93), (0.37, 0.95), (0.63, 0.95), (0.88, 0.93), (0.25, 0.88), (0.5, 0.89), (0.75, 0.88))]
    collar = []
    for k in range(11):
        x = (-5.6 + 11.2 * k / 10) * PX
        collar.append({'sphere': [r4(x), r4(0.25 * PX), r4((-0.9 - 0.35 * (1 - abs(k - 5) / 5)) * PX)],
                       'r': (1.3 if k % 2 == 0 else 1.1) * PX, 'seg': 9, 'paint': 'w', 'mat': 'fur'})
    c.append({'name': 'Королевский', 'parts': cloth + cape_edge(grid, r=0.5, bottom=False) + collar + spots + [
        {'strip': band, 'paint': 'w', 'mat': 'fur'},
        {'sphere': [-5.9 * PX, -1.2 * PX, -1.6 * PX], 'r': 0.75 * PX, 'seg': 10, 'paint': 'g', 'mat': 'metal'},
        {'sphere': [5.9 * PX, -1.2 * PX, -1.6 * PX], 'r': 0.75 * PX, 'seg': 10, 'paint': 'g', 'mat': 'metal'},
    ]})

    grid, cloth = cape_sheet(paint=('k', 'd'), lining=('dl',))
    stars = []
    for u, v, r in ((0.22, 0.14, 1.0), (0.7, 0.2, 0.75), (0.45, 0.35, 1.3), (0.15, 0.5, 0.7), (0.82, 0.45, 1.05),
                    (0.55, 0.6, 0.8), (0.3, 0.72, 1.1), (0.75, 0.78, 0.7), (0.5, 0.88, 0.9), (0.1, 0.9, 0.6), (0.92, 0.9, 0.8)):
        stars.append({'group': {'at': on_cape(grid, u, v, 1.15)}, 'parts': [
            {'prism': star_polygon(r * 1.45 * PX, r * 0.55 * PX), 'z': [-0.003, 0.0], 'paint': 'lw', 'mat': 'glow'},
            {'glowdisc': [0, 0, -0.004], 'size': r * 1.5 * PX, 'paint': 'l', 'alpha': 0.3, 'detail': True}]})
    c.append({'name': 'Звёздный', 'parts': cloth + cape_edge(grid, paint='l', mat='glow', r=0.35) + stars})

    def tongues(u, n=5, depth=5.0):
        phase = (u + 1) / 2 * n
        return depth * (0.5 - 0.5 * math.cos(2 * math.pi * phase)) ** 1.4
    grid, cloth = cape_sheet(length=13.0, bottom=tongues, rows=20, cols=31, paint=('c', 'l', 'lw'), lining=('d', 'c'))
    c.append({'name': 'Пламя', 'parts': cloth + cape_edge(grid, paint='lw', mat='glow', r=0.3, bottom=False)})

    def teeth(u, n=6, depth=3.4):
        phase = (u + 1) / 2 * n
        return depth * (1 - abs(2 * (phase % 1.0) - 1))
    grid, cloth = cape_sheet(length=14.5, bottom=teeth, rows=19, cols=25, paint=('l', 'c', 'd'), mat='matte', lining=('k',), lining_mat='matte')
    c.append({'name': 'Рваный', 'parts': cloth + cape_edge(grid, paint='d', mat='satin', r=0.35, bottom=False)})

    # Lava: dark basalt cloth split by glowing cracks that branch downwards, embers where they meet.
    grid, cloth = cape_sheet(length=16.5, paint=('k', 'k', 'dl'), mat='matte', lining=('d', 'c'))
    rng = random.Random(26)
    cracks, embers = [], []
    for start in (0.14, 0.38, 0.63, 0.87):
        u, v = start + rng.uniform(-0.04, 0.04), 0.04
        path = [cape_point(grid, u, v, 1.02)]
        while v < 0.97:
            v = min(0.97, v + rng.uniform(0.045, 0.09))
            u = min(0.95, max(0.05, u + rng.uniform(-0.075, 0.075)))
            path.append(cape_point(grid, u, v, 1.02))
            if rng.random() < 0.22 and v < 0.8:
                bu = min(0.95, max(0.05, u + rng.choice((-1, 1)) * rng.uniform(0.08, 0.14)))
                branch = [path[-1], cape_point(grid, (u + bu) / 2, v + 0.06, 1.02), cape_point(grid, bu, min(0.97, v + 0.13), 1.02)]
                cracks.append({'tube': branch, 'radius': [0.17 * PX, 0.06 * PX], 'sides': 5, 'caps': True, 'paint': 'cw', 'mat': 'glow'})
                embers.append({'glowdisc': cape_point(grid, u, v, 1.4), 'size': 1.2 * PX, 'paint': 'c', 'alpha': 0.4, 'detail': True})
        cracks.append({'tube': path, 'radius': [0.25 * PX, 0.09 * PX], 'sides': 5, 'caps': True, 'paint': ['cw', 'c'], 'mat': 'glow'})
    c.append({'name': 'Лава', 'parts': cloth + cape_edge(grid, paint='c', mat='glow', r=0.28) + cracks + embers})
    return c


def cape_point(grid, u, v, lift=1.2):
    """Point on the back of the cloth at (u, v) in 0..1 (interpolated between grid points), lifted off it (px)."""
    rows, cols = len(grid), len(grid[0])
    fr, fc = v * (rows - 1), u * (cols - 1)
    r0, c0 = min(rows - 2, int(fr)), min(cols - 2, int(fc))
    tr, tc = fr - r0, fc - c0
    q = [0.0, 0.0, 0.0]
    for dr, dc, w in ((0, 0, (1 - tr) * (1 - tc)), (0, 1, (1 - tr) * tc), (1, 0, tr * (1 - tc)), (1, 1, tr * tc)):
        for k in range(3):
            q[k] += grid[r0 + dr][c0 + dc][k] * w
    return [r4(q[0]), r4(q[1]), r4(q[2] - lift * PX)]


# ---------------------------------------------------------------------------------------------- accessories

def rounded_rect(cx, cy, w, h, r, n=4):
    pts = []
    for qx, qy, a0 in ((1, 1, 0), (-1, 1, 90), (-1, -1, 180), (1, -1, 270)):
        ox, oy = cx + qx * (w / 2 - r), cy + qy * (h / 2 - r)
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            pts.append([r4(ox + r * math.cos(a)), r4(oy + r * math.sin(a))])
    return pts


def extras():
    """Accessories. attach 'head': hat space (origin on top of the head, +z the face at z=0.2344); 'body': origin at
    the neck (top centre of the torso, 12 px tall, 8 px wide, 4 px deep), +z the chest.

    Nothing may enter the player: head accessories keep at least 0.1 px off the bare head (the game scales them out
    over the hat layer or a helmet), the scarf stays outside the jacket layer and under the hat layer, and only its
    sides pass under the arms, where no neck is. tools/check_fit.py checks it."""
    e = []
    face, eye = 0.2344, -0.262
    front = face + 0.0145  # frame and lens front, 0.25 px off the face
    lens = []
    for side in (-1, 1):
        cx = side * 0.112
        outline = rounded_rect(cx, eye, 0.118, 0.078, 0.026)
        lens.append({'prism': outline, 'z': [face + 0.008, face + 0.014], 'paint': ['k', 'd'], 'mat': 'gem'})
        lens.append({'tube': [[q[0], q[1], front] for q in outline], 'closed': True, 'radius': [0.0065, 0.0065], 'sides': 5, 'paint': 'c', 'mat': 'metal'})
        # The temple goes round the corner of the head instead of through it, then back along the side to the ear.
        lens.append({'tube': [[side * 0.171, eye + 0.018, front], [side * 0.226, eye + 0.019, front], [side * 0.2445, eye + 0.02, face + 0.009],
                              [side * 0.2495, eye + 0.02, face - 0.012], [side * 0.2495, eye + 0.02, 0.02], [side * 0.249, eye - 0.01, -0.07]],
                     'radius': [0.0065, 0.0055], 'sides': 5, 'paint': 'c', 'mat': 'metal'})
        lens.append({'poly': [[cx - 0.035, eye + 0.028, face + 0.0145], [cx - 0.012, eye + 0.028, face + 0.0145], [cx - 0.042, eye - 0.018, face + 0.0145]], 'paint': 'w', 'mat': 'glow', 'detail': True})
    lens.append({'tube': bezier([-0.053, eye + 0.012, front], [-0.02, eye + 0.03, front + 0.007], [0.02, eye + 0.03, front + 0.007], [0.053, eye + 0.012, front], 6),
                 'radius': [0.006, 0.006], 'sides': 5, 'paint': 'c', 'mat': 'metal'})
    e.append({'name': 'Очки', 'attach': 'head', 'parts': lens})

    cup = [{'revolve': [[0.0, 0.0], [0.07, 0.0], [0.08, 0.018], [0.074, 0.042], [0.0, 0.046]], 'seg': 24, 'paint': ['c', 'l'], 'mat': 'gloss'},
           {'torus': [0.055, 0.007], 'y': 0.046, 'seg': 24, 'sides': 6, 'paint': 'l', 'mat': 'glow'},
           {'torus': [0.066, 0.017], 'y': -0.004, 'seg': 24, 'sides': 8, 'paint': 'k', 'mat': 'fur'}]
    # The band runs 0.26 px over the top of the head and round its corners (it used to cut through them), the coloured
    # strip lies on its outer side instead of dipping into the head, and the cushions stop short of the sides.
    right = [(4.75, -3.4), (4.62, -1.9)] + [(3.6 + 0.95 * math.cos(math.radians(a)), -0.4 + 0.95 * math.sin(math.radians(a))) for a in (0, 22.5, 45, 67.5, 90)]
    top = [(x, 0.55 + 0.12 * (1 - (x / 3.6) ** 2)) for x in (2.4, 1.2, 0.0)]
    half = right + top
    arch = [[r4(-x * PX), r4(y * PX), 0.0] for x, y in half] + [[r4(x * PX), r4(y * PX), 0.0] for x, y in reversed(half[:-1])]
    strip = []
    for i in range(4, len(arch) - 4):
        a, b = arch[i - 1], arch[i + 1]
        tx, ty = b[0] - a[0], b[1] - a[1]
        n = math.hypot(tx, ty)
        strip.append([r4(arch[i][0] - ty / n * 0.011), r4(arch[i][1] + tx / n * 0.011), 0.0])
    e.append({'name': 'Наушники', 'attach': 'head', 'parts': [
        {'tube': arch, 'radius': [0.017, 0.017], 'sides': 8, 'paint': 'k', 'mat': 'satin'},
        {'tube': strip, 'radius': [0.011, 0.011], 'sides': 6, 'paint': 'c', 'mat': 'gloss', 'detail': True},
        {'group': {'mirror': True}, 'parts': [{'group': {'at': [0.266, -0.265, 0.0], 'rot': [0, 0, -90]}, 'parts': cup}]},
    ]})

    # Scarf ring: a rounded rectangle round the top of the torso, 0.1 px off the jacket in front, thinner and closer at
    # the back (it stays inside a cape's cloth), just under the hat layer; its sides run inside the arms.
    y0, zf, zb, side, corner = -1.35 * PX, 3.15 * PX, -2.86 * PX, 5.2 * PX, 1.1 * PX

    def arc(cx, cz, a0, a1, n=5):
        return [[r4(cx + corner * math.sin(math.radians(a0 + (a1 - a0) * k / n))), r4(y0), r4(cz + corner * math.cos(math.radians(a0 + (a1 - a0) * k / n)))]
                for k in range(n + 1)]
    fx, bx = side - corner, side - corner
    front_half = arc(-fx, zf - corner, -90, 0)[:-1] + [[r4(-fx + (2 * fx) * k / 6), r4(y0), r4(zf)] for k in range(7)] + arc(fx, zf - corner, 0, 90)[1:]
    back_left = [[r4(-side), r4(y0), r4(zf - corner)], [r4(-side), r4(y0), r4(zb + corner)]] + arc(-bx, zb + corner, -90, -180)[1:] + [[0.0, r4(y0), r4(zb)]]
    back_right = [[-q[0], q[1], q[2]] for q in back_left]
    thick, thin = 0.047, 0.03
    tail = bezier([0.1, y0, 0.196], [0.14, -0.21, 0.22], [0.11, -0.34, 0.211], [0.13, -0.45, 0.202], 10)
    fringe = [{'tube': [[0.13 + dx, -0.45, 0.202], [0.13 + dx * 1.2, -0.5, 0.202]], 'radius': [0.0055, 0.004], 'sides': 4, 'paint': 'l', 'mat': 'fur', 'detail': True}
              for dx in (-0.024, -0.008, 0.008, 0.024)]
    wool = {'sides': 10, 'paint': ['c', 'l'], 'alt': ['l', 'c'], 'pattern': {'stripes': 7}, 'mat': 'fur'}
    e.append({'name': 'Шарф', 'attach': 'body', 'parts': [
        dict(wool, tube=front_half, radius=[thick, thick]),
        dict(wool, tube=back_left, radius=[thick, thin], power=2),
        dict(wool, tube=back_right, radius=[thick, thin], power=2),
        {'tube': tail, 'radius': [0.042, 0.036], 'sides': 9, 'paint': ['c', 'l'], 'mat': 'fur'},
    ] + fringe})
    return e


# ---------------------------------------------------------------------------------------------- wings

COVERT = [0.46, 0.84, 1.0, 0.98, 0.84, 0.56, 0.0]
COVERT_AT = [0.0, 0.14, 0.32, 0.52, 0.72, 0.88, 1.0]
FLAME_AT = [0.0, 0.12, 0.3, 0.5, 0.68, 0.84, 0.94, 1.0]


def wing_group(parts, sweep=26, lift=6, swing=None, at=(0.035, 0.0, -0.012)):
    swing = swing or [['y', 16, 2.2, 0.0], ['z', 5, 2.2, 0.9]]
    return [{'group': {'mirror': True}, 'parts': [
        {'group': {'at': list(at), 'rot': [0, sweep, lift], 'swing': swing}, 'parts': parts},
    ]}]


def shell(bend, cup):
    """Bends the flat wing layout into a shell: the outer and lower parts curve backwards (-Z)."""
    def warp(x, y, z):
        return (x, y, z - bend * x * x - cup * x * max(0.0, -y))
    return warp


def v4(p, t):
    return [r4(p[0]), r4(p[1]), r4(p[2]), r4(t)]


def feather(base, angle, length, width, warp, z=0.0, arch=0.32, twist=0.3, bend=0.05, curl=0.0, t=(0.0, 1.0),
            profile=FEATHER, at=FEATHER_AT):
    """One volumetric feather: a vane arched around its quill (3 columns: edge, ridge, edge) with a twist so
    neighbours overlap like tiles, curved in its plane (bend) and out of it (curl)."""
    a = math.radians(angle)
    d, n = (math.cos(a), math.sin(a)), (-math.sin(a), math.cos(a))
    rows = []
    for s, w in zip(at, profile):
        off = bend * length * s * s
        cx = base[0] + d[0] * s * length + n[0] * off
        cy = base[1] + d[1] * s * length + n[1] * off
        cz = z - curl * length * s * s
        half = width * w / 2
        tw = twist * (0.35 + 0.65 * s)
        ct, st = math.cos(tw), math.sin(tw)
        left = warp(cx + n[0] * half * ct, cy + n[1] * half * ct, cz + half * st)
        ridge = warp(cx, cy, cz - arch * half)
        right = warp(cx - n[0] * half * ct, cy - n[1] * half * ct, cz - half * st)
        tt = t[0] + (t[1] - t[0]) * s
        rows.append([v4(left, tt), v4(ridge, tt), v4(right, tt)])
    return rows


def along(points, u):
    """Point at fraction u of a polyline (by length)."""
    lengths = [math.dist(a, b) for a, b in zip(points, points[1:])]
    goal = u * sum(lengths)
    for (a, b), ln in zip(zip(points, points[1:]), lengths):
        if goal <= ln or ln == lengths[-1] and b is points[-1]:
            f = 0.0 if ln == 0 else min(1.0, goal / ln)
            return tuple(a[k] + (b[k] - a[k]) * f for k in range(len(a)))
        goal -= ln
    return points[-1]


def bird(arm, warp, primaries, secondaries, coverts, paints, mat='satin', flame=False, alula=True, arm_r=(0.034, 0.011)):
    """Feathered wing built from volumetric feathers in overlapping rows (flight feathers, three covert rows,
    alula) around a thick rounded arm. arm = root, elbow, wrist, tip in the XY plane."""
    root, elbow, wrist, tip = arm
    inner = spline([root, elbow, wrist], 5)
    outer = spline([wrist, (wrist[0] * 0.5 + tip[0] * 0.5, wrist[1] * 0.35 + tip[1] * 0.65 + 0.012), tip], 4)
    spine = inner + outer[1:]
    profile, samples = (FLAME, FLAME_AT) if flame else (FEATHER, FEATHER_AT)
    parts = [{'tube': [list(warp(x, y, -0.004)) for x, y in spine], 'radius': list(arm_r), 'sides': 10, 'paint': paints['arm'],
              'mat': 'metal' if flame else 'satin', 'caps': True}]
    feathers = []
    count, (a0, a1), (l0, l1), width = secondaries
    for i in range(count):
        u = i / (count - 1)
        base = along(inner, 0.04 + 0.96 * u)
        feathers.append(({'sheet': feather(base, a0 + (a1 - a0) * u, l0 + (l1 - l0) * u, width, warp, z=0.004 + 0.0035 * i,
                                           twist=0.26, bend=0.03 if not flame else 0.1, curl=-0.05 if flame else 0.0, profile=profile, at=samples),
                          'paint': paints['secondary'], 'mat': mat, 'ao': [0.6, 1.0]}))
    pc, (a0, a1), (l0, l1), width = primaries
    for i in range(pc):
        u = i / (pc - 1)
        base = along(outer, 0.05 + 0.95 * u)
        length = l0 + (l1 - l0) * math.sin(min(1.0, u * 1.2) * math.pi / 2)
        feathers.append(({'sheet': feather(base, a0 + (a1 - a0) * u, length, width, warp, z=0.004 + 0.0035 * count + 0.0045 * i,
                                           twist=0.34, bend=0.06 if not flame else 0.14, curl=-0.08 if flame else 0.02, profile=profile, at=samples),
                          'paint': paints['primary'], 'mat': mat, 'ao': [0.58, 1.0]}))
    parts += feathers
    parts.append({'sphere': list(warp(0.012, 0.01, -0.01)), 'r': [0.05, 0.062, 0.036], 'seg': 12, 'paint': paints['covert'] if isinstance(paints['covert'], str) else paints['covert'][0],
                  'mat': paints.get('covert_mat', mat)})
    for row, (count, length, width, drop, a0, a1, reach) in enumerate(coverts):
        for i in range(count):
            u = i / (count - 1)
            p = along(spine, 0.1 + u * (reach - 0.1))
            base = (p[0], p[1] - drop)
            parts.append({'sheet': feather(base, a0 + (a1 - a0) * u, length * (1 - 0.22 * u), width, warp, z=-0.014 - 0.012 * row + 0.0012 * i,
                                           arch=0.4, twist=0.22, bend=0.02, profile=COVERT, at=COVERT_AT),
                          'paint': paints['covert'], 'mat': paints.get('covert_mat', mat), 'ao': [0.72 + 0.06 * row, 1.0],
                          **({'detail': True} if row == len(coverts) - 1 else {})})
    if alula:
        for k in range(3):
            base = along(spine, 0.5 + 0.03 * k)
            parts.append({'sheet': feather(base, 26 - 14 * k, 0.1 - 0.018 * k, 0.04, warp, z=-0.05 - 0.003 * k, arch=0.35, twist=0.2,
                                           bend=-0.08, profile=COVERT, at=COVERT_AT),
                          'paint': paints['covert'], 'mat': mat, 'ao': [0.8, 1.0], 'detail': True})
    return parts


def slab(grid, thick, closed=False):
    """Turns one sheet grid (rows x cols of [x, y, z, t]) into a solid: front, back offset by thick along the
    surface normal, and the rim band. Returns three sheet grids."""
    rows, cols = len(grid), len(grid[0])
    P = [[tuple(v[:3]) for v in row] for row in grid]
    N = sheet_normals(P)
    back = [[v4(sub(P[r][c], tuple(thick * x for x in N[r][c])), grid[r][c][3]) for c in range(cols)] for r in range(rows)]
    back = [row[::-1] for row in back]
    loop = [(r, cols - 1) for r in range(rows)] + [(rows - 1, c) for c in range(cols - 2, -1, -1)]
    if not closed:
        loop += [(r, 0) for r in range(rows - 2, -1, -1)] + [(0, c) for c in range(1, cols)]
    rim = [[grid[r][c], back[r][cols - 1 - c]] for r, c in loop]
    return grid, back, rim


def patch(a, b, edge, rows, cols):
    """Coons-like patch from curve a(u) to b(u) (u: wrist -> tips) with its far edge bent to follow edge(v)."""
    grid = []
    for i in range(rows):
        u = i / (rows - 1)
        pa, pb = along(a, u), along(b, u)
        row = []
        for j in range(cols):
            v = j / (cols - 1)
            lin_end = lerp3(a[-1], b[-1], v)
            e = along(edge, v)
            p = tuple(pa[k] + (pb[k] - pa[k]) * v + u ** 2.2 * (e[k] - lin_end[k]) for k in range(3))
            row.append(p)
        grid.append(row)
    return grid


def lerp3(a, b, t):
    return tuple(a[k] + (b[k] - a[k]) * t for k in range(3))


def bat(root, elbow, wrist, tips, body, warp, depth, billow, bone, skin, skin_mat='satin', r=0.024, spikes=0, horn=True,
        claw='w', thick=0.007):
    """Membrane wing: tapered bones with knuckles and claws, skin panels that billow between the fingers and
    have real thickness (front, back and rim)."""
    W = lambda p, z=0.0: warp(p[0], p[1], z)
    arm = [W(p) for p in spline([root, elbow, wrist], 5)]
    parts = [{'tube': [list(p) for p in arm], 'radius': [r, r * 0.72], 'sides': 10, 'paint': bone, 'mat': 'gloss', 'caps': True}]
    for p, rr in ((root, r * 1.15), (elbow, r * 1.12), (wrist, r * 1.1)):
        parts.append({'sphere': list(W(p)), 'r': rr, 'seg': 10, 'paint': bone, 'mat': 'gloss'})
    fingers = []
    for k, tipp in enumerate(tips):
        mid = lerp2(wrist, tipp, 0.5)
        side = (tipp[1] - wrist[1], -(tipp[0] - wrist[0]))
        knuckle = (mid[0] + side[0] * 0.07, mid[1] + side[1] * 0.07)
        pts = [W(p) for p in spline([wrist, knuckle, tipp], 4)]
        fingers.append(pts)
        parts.append({'tube': [list(p) for p in pts], 'radius': [r * 0.62, r * 0.2], 'sides': 8, 'paint': bone, 'mat': 'gloss'})
        parts.append({'sphere': list(W(knuckle)), 'r': r * 0.5, 'seg': 8, 'paint': bone, 'mat': 'gloss', 'detail': True})
        d = (tipp[0] - knuckle[0], tipp[1] - knuckle[1])
        ln = math.hypot(*d) or 1.0
        hook = (tipp[0] + d[0] / ln * r * 1.6 + side[0] * 0.04, tipp[1] + d[1] / ln * r * 1.6 + side[1] * 0.04)
        parts.append({'tube': [list(W(tipp)), list(W(hook, -0.004))], 'radius': [r * 0.3, 0.001], 'sides': 6, 'paint': claw, 'mat': 'gloss'})
    edges = []
    for a, b in zip(tips, tips[1:]):
        edges.append([W(a)] + [W((x, y)) for x, y, _ in scallop(a, b, wrist, depth, n=6)] + [W(b)])
    rim_body = [W(tips[-1])] + [W((x, y)) for x, y, _ in scallop(tips[-1], body, wrist, depth * 0.8, n=6)] + [W(body)]
    panels = [(fingers[k], fingers[k + 1], edges[k]) for k in range(len(fingers) - 1)]
    panels.append((fingers[-1], [W(p) for p in spline([wrist, elbow, root, body], 3)], rim_body))
    for a, b, edge in panels:
        grid = patch(a, b, edge, 7, 6)
        rows, cols = len(grid), len(grid[0])
        for i in range(rows):
            for j in range(cols):
                u, v = i / (rows - 1), j / (cols - 1)
                x, y, z = grid[i][j]
                grid[i][j] = v4((x, y, z - billow * math.sin(math.pi * v) * math.sin(math.pi * min(1.0, u * 1.15))), u)
        front, back, rim = slab(grid, thick)
        parts.append({'sheet': front, 'paint': skin, 'mat': skin_mat, 'ao': [0.7, 1.0], 'two': False})
        parts.append({'sheet': back, 'paint': skin, 'mat': skin_mat, 'ao': [0.55, 0.85], 'two': False})
        parts.append({'sheet': rim, 'paint': 'k' if bone == 'k' else 'd', 'mat': 'satin', 'detail': True})
    if horn:
        top = W((wrist[0] - 0.01, wrist[1] + 0.012))
        parts.append({'tube': [list(top), list(W((wrist[0] - 0.035, wrist[1] + r * 3.2), -0.01)), list(W((wrist[0] - 0.075, wrist[1] + r * 3.8), -0.02))],
                      'radius': [r * 0.55, 0.001], 'sides': 8, 'paint': claw, 'mat': 'gloss'})
    for i in range(spikes):
        u = (i + 0.5) / spikes
        p = along(arm, u * 0.95)
        parts.append({'tube': [list(p), [r4(p[0] - 0.012), r4(p[1] + r * 2.2), r4(p[2] - 0.004)]], 'radius': [r * 0.42, 0.001], 'sides': 6,
                      'paint': 'l', 'mat': 'gloss', 'detail': True})
    return parts


def butterfly_wing(outline, root, warp, thick, paint, veins, rings=6, z=0.0):
    """Thin solid wing blade: radial grid from the root to the outline, raised veins on top."""
    closed = outline + [outline[0]]
    grid = []
    for i in range(rings):
        s = i / (rings - 1)
        row = []
        for x, y in closed:
            px, py = root[0] + (x - root[0]) * s, root[1] + (y - root[1]) * s
            row.append(v4(warp(px, py, z - 0.03 * s * (1 - s)), s))
        grid.append(row)
    front, back, rim = slab(grid[1:], thick, closed=True)
    parts = [{'sheet': front, 'paint': paint, 'mat': 'satin', 'ao': [0.75, 1.0], 'two': False},
             {'sheet': back, 'paint': paint, 'mat': 'satin', 'ao': [0.6, 0.9], 'two': False},
             {'sheet': rim, 'paint': 'k', 'mat': 'satin'},
             {'sheet': [[v4(warp(root[0], root[1], z), 0.0)] * len(closed), grid[1]], 'paint': paint, 'mat': 'satin'}]
    for k in veins:
        x, y = closed[k]
        pts = [list(warp(root[0] + (x - root[0]) * s, root[1] + (y - root[1]) * s, z - 0.03 * s * (1 - s) - thick * 0.6)) for s in (0.08, 0.35, 0.65, 0.93)]
        parts.append({'tube': pts, 'radius': [0.0055, 0.002], 'sides': 5, 'paint': 'k', 'mat': 'gloss', 'detail': True})
    return parts


def wings():
    w = []
    angel = shell(0.34, 0.1)
    w.append({'name': 'Ангел', 'parts': wing_group(bird(
        ((0.0, 0.0), (0.16, 0.17), (0.31, 0.38), (0.52, 0.46)), angel,
        (10, (-88, -34), (0.36, 0.56), 0.105),
        (11, (-102, -90), (0.3, 0.4), 0.108),
        [(12, 0.2, 0.084, 0.045, -96, -66, 0.8), (11, 0.13, 0.07, 0.006, -94, -62, 0.78), (12, 0.078, 0.056, -0.022, -90, -58, 0.8)],
        {'arm': 'w', 'primary': ['w', 'w', 'cw'], 'secondary': ['w', 'w', 'cw'], 'covert': 'w', 'covert_mat': 'fur'}), sweep=24, lift=8)})

    demon = shell(0.28, 0.06)
    w.append({'name': 'Демон', 'parts': wing_group(bat(
        (0.0, 0.0), (0.16, 0.22), (0.33, 0.37), [(0.7, 0.26), (0.64, -0.04), (0.46, -0.27)], (0.06, -0.21), demon, 0.26, 0.05,
        'k', ['d', 'c'], r=0.022, claw='w'), sweep=22, lift=4, swing=[['y', 14, 1.8, 0.0], ['z', 6, 1.8, 0.8]])})

    fly = shell(0.18, 0.0)
    fore = catmull([(0.02, 0.05), (0.09, 0.3), (0.23, 0.45), (0.4, 0.47), (0.53, 0.39), (0.53, 0.25), (0.44, 0.12), (0.29, 0.04), (0.12, 0.005)], 3)
    hind = catmull([(0.02, -0.02), (0.18, -0.03), (0.34, -0.11), (0.38, -0.25), (0.3, -0.37), (0.16, -0.39), (0.06, -0.27), (0.0, -0.1)], 3)
    butterfly = butterfly_wing(fore, (0.03, 0.03), fly, 0.006, ['d', 'c', 'l', 'k'], veins=[3, 6, 9, 12, 15, 18], z=0.0)
    butterfly += butterfly_wing(hind, (0.03, -0.02), fly, 0.006, ['d', 'c', 'l', 'k'], veins=[3, 7, 11, 14, 17], z=0.012)
    for x, y, rr, paint, z, mat in ((0.22, -0.21, 0.052, 'k', -0.012, 'gloss'), (0.22, -0.21, 0.036, 'w', -0.016, 'gloss'), (0.22, -0.21, 0.021, 'c', -0.019, 'gloss'),
                                    (0.215, -0.2, 0.008, 'w', -0.022, 'glow'), (0.46, 0.35, 0.02, 'w', -0.018, 'satin'), (0.49, 0.28, 0.016, 'w', -0.018, 'satin'),
                                    (0.41, 0.41, 0.015, 'w', -0.017, 'satin')):
        wx, wy, wz = fly(x, y, z)
        butterfly.append({'sphere': [r4(wx), r4(wy), r4(wz)], 'r': [rr, rr, rr * 0.22], 'seg': 12, 'paint': paint, 'mat': mat, 'detail': rr < 0.03})
    w.append({'name': 'Бабочка', 'parts': wing_group(butterfly, sweep=30, lift=4, swing=[['y', 30, 6.0, 0.0], ['z', 4, 6.0, 0.5]], at=(0.03, -0.01, -0.02)) + [
        {'sphere': [0.0, 0.0, -0.03], 'r': [0.028, 0.05, 0.026], 'seg': 12, 'paint': 'k', 'mat': 'gloss'},
        {'sphere': [0.0, -0.1, -0.03], 'r': [0.022, 0.075, 0.02], 'seg': 12, 'paint': ['k', 'd'], 'mat': 'gloss', 'pattern': {'ridges': 5}, 'alt': 'dl'},
        {'sphere': [0.0, 0.07, -0.035], 'r': 0.022, 'seg': 10, 'paint': 'k', 'mat': 'gloss'},
        {'group': {'mirror': True}, 'parts': [
            {'tube': [[0.008, 0.085, -0.04], [0.035, 0.15, -0.05], [0.06, 0.2, -0.045]], 'radius': [0.003, 0.002], 'sides': 5, 'paint': 'k', 'mat': 'gloss'},
            {'sphere': [0.061, 0.203, -0.045], 'r': 0.008, 'seg': 8, 'paint': 'c', 'mat': 'gloss'}]}]})

    dragon = shell(0.24, 0.05)
    w.append({'name': 'Дракон', 'parts': wing_group(bat(
        (0.0, 0.0), (0.18, 0.27), (0.37, 0.44), [(0.86, 0.37), (0.84, 0.03), (0.66, -0.27), (0.41, -0.39)], (0.05, -0.26), dragon, 0.32, 0.06,
        'dl', ['c', 'd'], r=0.03, spikes=5, claw='w', thick=0.009), sweep=20, lift=6, swing=[['y', 12, 1.4, 0.0], ['z', 7, 1.4, 0.9]])})

    fire = shell(0.3, 0.08)
    w.append({'name': 'Феникс', 'parts': wing_group(bird(
        ((0.0, 0.0), (0.17, 0.18), (0.32, 0.4), (0.54, 0.49)), fire,
        (10, (-84, -26), (0.38, 0.56), 0.12),
        (9, (-102, -88), (0.31, 0.4), 0.12),
        [(11, 0.19, 0.095, 0.04, -96, -60, 0.8), (10, 0.11, 0.07, -0.006, -92, -56, 0.78)],
        {'arm': 'g', 'primary': ['g', 'c', 'l'], 'secondary': ['g', 'c', 'l'], 'covert': ['g', 'cw']}, mat='gloss', flame=True, alula=False) + [
        {'glowdisc': [r4(0.49 + 0.4 * math.cos(math.radians(a))), r4(0.47 + 0.4 * math.sin(math.radians(a))), -0.1], 'size': 0.07, 'paint': 'l', 'alpha': 0.45,
         'detail': True} for a in (-84, -64, -44, -30)],
        sweep=24, lift=10, swing=[['y', 12, 1.6, 0.0], ['z', 6, 1.6, 0.9]]) + [
        {'glowdisc': [0.0, 0.05, -0.05], 'size': 0.24, 'paint': 'c', 'alpha': 0.3}]})
    return w


# ---------------------------------------------------------------------------------------------- interpreter

MATS = {'matte': (0.06, 3, 0, 0.08), 'satin': (0.22, 4, 0, 0.14), 'gloss': (0.5, 5, 0, 0.18), 'metal': (0.9, 4, 1, 0.1),
        'gem': (1.0, 6, 0, 0.4), 'fur': (0.0, 0, 0, 0.36)}
LIGHT = (0.33, 0.88, 0.34)


def mix(a, b, t):
    t = min(1.0, max(0.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


class Builder:
    """Mirror of effects/Hats.java. world maps model space to camera-relative coordinates (camera at the origin)."""

    def __init__(self, main, second, style=0, time=0.0, quality=1.0, alpha=1.0, world=None, env=1.0, flap=1.0, tempo=1.0, glow=False):
        self.c, self.l = rgb(main), rgb(second)
        self.style, self.time, self.quality, self.alpha = style, time, quality, alpha
        self.world = world or identity()
        self.env, self.flap, self.tempo, self.glow_pass = env, flap, tempo, glow
        n = math.sqrt(sum(v * v for v in LIGHT))
        self.light = tuple(v / n for v in LIGHT)
        self.rim = mix(self.l, (255, 255, 255), 0.5)
        self.quads, self.glows = [], []

    def key(self, key, y):
        dark, white = (22, 22, 28), (242, 244, 248)
        if key in ('c', 'l', 'm', 'd', 'dl'):
            if self.style == 1:
                return self.c
            if self.style == 2:
                return mix(self.c, self.l, y / 0.42)
        return {
            'c': self.c, 'l': self.l, 'm': mix(self.c, self.l, 0.55), 'd': mix(self.c, dark, 0.72), 'dl': mix(self.l, dark, 0.55),
            'cw': mix(self.c, white, 0.4), 'lw': mix(self.l, white, 0.45), 'w': white, 'k': (29, 30, 36),
            'g': mix((255, 207, 90), self.l, 0.22), 'p': mix((255, 159, 191), self.l, 0.3),
            'gr': mix((60, 182, 90), self.l, 0.1), 'r': (224, 48, 58),
        }[key]

    def paint(self, paint, t, y, index):
        if isinstance(paint, dict):
            cycle = paint['cycle']
            return self.key(cycle[index % len(cycle)], y)
        if isinstance(paint, list):
            if len(paint) == 1:
                return self.key(paint[0], y)
            f = min(1.0, max(0.0, t)) * (len(paint) - 1)
            i = min(len(paint) - 2, int(f))
            return mix(self.key(paint[i], y), self.key(paint[i + 1], y), f - i)
        return self.key(paint, y)

    def shade(self, base, n, p, part):
        mat = part.get('mat', 'satin')
        if mat == 'glow' or not part.get('lit', True):
            return base
        gloss, shine, metal, rimk = MATS[mat]
        L = self.light
        ndl = max(0.0, dot(n, L))
        if metal:
            k = 0.34 + 0.4 * ndl + 0.22 * (0.5 + 0.5 * n[1])
        else:
            k = 0.5 + 0.56 * ndl
        v = normalize(tuple(-x for x in p))
        spec = 0.0
        if gloss > 0:
            hv = normalize(add(L, v))
            s = max(0.0, dot(n, hv))
            for _ in range(shine):
                s *= s
            spec = gloss * s
        facing = max(0.0, dot(n, v))
        rim = rimk * (1 - facing) ** 3
        out = []
        for i in range(3):
            tint = base[i] * 1.2 if metal else 255.0
            out.append(min(255.0, base[i] * k * self.env + tint * spec * self.env + self.rim[i] * rim))
        return tuple(out)

    def emit(self, g, pts, params, hint, part, index, odd=False, normals=None):
        hat = [apply(g, p) for p in pts]
        cam = [apply(self.world, p) for p in hat]
        o = cam[0]
        normal = newell([sub(p, o) for p in cam])
        if length(normal) < 1e-12:
            return
        n = normalize(normal)
        full = matmul(self.world, g)
        h = apply_dir(full, hint)
        if dot(n, h) < 0:
            n = tuple(-v for v in n)
        centroid = tuple(sum(p[j] for p in cam) / 4 for j in range(3))
        flip = False
        if dot(n, centroid) > 0:
            if not part.get('two', 'poly' in part or 'strip' in part or 'sheet' in part):
                return
            n = tuple(-v for v in n)
            flip = True
        if normals is not None:
            nm = normal_matrix(full)
            vn = []
            for v in normals:
                m = normalize(apply3(nm, v))
                if flip:
                    m = tuple(-x for x in m)
                if dot(m, n) < 0.05:
                    m = n
                vn.append(m)
        else:
            vn = [n] * 4
        pattern = odd and self.style == 0 and 'alt' in part
        paint = part['alt'] if pattern else part['paint'] if 'paint' in part else 'c'
        alpha = self.alpha * part.get('alpha', 1.0)
        colors = []
        for i in range(4):
            base = self.paint(paint, params[i], hat[i][1], index)
            if 'ao' in part:
                a0, a1 = part['ao']
                f = a0 + (a1 - a0) * min(1.0, max(0.0, params[i]))
                base = tuple(v * f for v in base)
            colors.append(self.shade(base, vn[i], cam[i], part) + (alpha,))
        self.quads.append((cam, colors, n))

    def segments(self, count):
        return max(6, int(round(count * (0.5 + 0.5 * self.quality))))

    def build(self, parts, g=None, index=0):
        g = g or identity()
        for part in parts:
            if part.get('detail') and self.quality < 0.5:
                continue
            if 'group' in part:
                self.group(part, g, index)
            elif 'glowring' in part or 'glowflat' in part or 'glowdisc' in part:
                self.glows.append((part, g, index))
            elif 'revolve' in part:
                self.revolve(part, g, index)
            elif 'tube' in part:
                self.tube(part, g, index, part['tube'], part.get('closed', False))
            elif 'torus' in part:
                big, small = part['torus']
                seg = self.segments(part.get('seg', 32))
                y = part.get('y', 0.0)
                points = [[big * math.sin(2 * math.pi * i / seg), y, big * math.cos(2 * math.pi * i / seg)] for i in range(seg)]
                self.tube(dict(part, radius=[small, small]), g, index, points, True)
            elif 'sphere' in part:
                self.sphere(part, g, index)
            elif 'gem' in part:
                self.gem(part, g, index)
            elif 'prism' in part:
                self.prism(part, g, index)
            elif 'poly' in part:
                self.poly(part, g, index)
            elif 'strip' in part:
                self.strip(part, g, index)
            elif 'sheet' in part:
                self.sheet(part, g, index)

    def group(self, part, g, index):
        spec = part['group']
        repeat = spec.get('repeat', 1)
        for k in range(repeat):
            for mirrored in ((False, True) if spec.get('mirror') else (False,)):
                m = g
                if repeat > 1:
                    m = matmul(m, rot_y(2 * math.pi * k / repeat))
                if mirrored:
                    m = matmul(m, scale(-1, 1, 1))
                at = list(spec.get('at', [0, 0, 0]))
                if 'bob' in spec:
                    amp, freq, step = spec['bob']
                    at[1] += amp * math.sin(freq * self.time + step * k)
                m = matmul(m, translate(*at))
                rx, ry, rz = (math.radians(v) for v in spec.get('rot', [0, 0, 0]))
                m = matmul(m, matmul(rot_y(ry), matmul(rot_x(rx), rot_z(rz))))
                if 'spin' in spec:
                    axis, speed = spec['spin']
                    m = matmul(m, {'x': rot_x, 'y': rot_y, 'z': rot_z}[axis](speed * self.time))
                for axis, degrees, speed, phase in spec.get('swing', []):
                    angle = math.radians(degrees) * self.flap * math.sin(speed * self.tempo * self.time + phase)
                    m = matmul(m, {'x': rot_x, 'y': rot_y, 'z': rot_z}[axis](angle))
                self.build(part['parts'], m, k if repeat > 1 else index)

    def revolve(self, part, g, index):
        profile = part['revolve']
        closed = part.get('closed', False)
        seg = self.segments(part.get('seg', 24))
        start, end = (math.radians(v) for v in part.get('arc', [0, 360]))
        phase = math.radians(part.get('phase', 0))
        curl = part.get('curl')
        pattern = part.get('pattern', {})
        crease = math.cos(math.radians(part.get('crease', 50)))
        n = len(profile)
        edges = n - 1 + (1 if closed else 0)
        seg_normals = []
        for i in range(edges):
            p, q = profile[i], profile[(i + 1) % n]
            dr, dy = q[0] - p[0], q[1] - p[1]
            ln = math.hypot(dr, dy) or 1.0
            seg_normals.append((dy / ln, -dr / ln))

        def smooth(i, point):
            own = seg_normals[i]
            other_index = (i - 1 if point == i else i + 1)
            if closed:
                other_index %= edges
            elif other_index < 0 or other_index >= edges:
                return own
            other = seg_normals[other_index]
            if own[0] * other[0] + own[1] * other[1] < crease:
                return own
            s = (own[0] + other[0], own[1] + other[1])
            ln = math.hypot(*s) or 1.0
            return (s[0] / ln, s[1] / ln)

        def at(point, angle):
            r, y = point
            if curl:
                lift, r0, r1 = curl
                f = max(0.0, (r - r0) / (r1 - r0))
                y += lift * math.sin(angle) ** 2 * f * f
            return (r * math.sin(angle), y, r * math.cos(angle))

        for i in range(edges):
            p, q = profile[i], profile[(i + 1) % n]
            if p[0] < 1e-6 and q[0] < 1e-6:
                continue
            dr, dy = q[0] - p[0], q[1] - p[1]
            tp, tq = i / max(1, n - 1), ((i + 1) % n) / max(1, n - 1)
            np_, nq = smooth(i, i), smooth(i, i + 1)
            for j in range(seg):
                a0 = phase + start + (end - start) * j / seg
                a1 = phase + start + (end - start) * (j + 1) / seg
                am = (a0 + a1) / 2
                pts = [at(p, a0), at(p, a1), at(q, a1), at(q, a0)]
                hint = (dy * math.sin(am), -dr, dy * math.cos(am))
                normals = [(np_[0] * math.sin(a0), np_[1], np_[0] * math.cos(a0)), (np_[0] * math.sin(a1), np_[1], np_[0] * math.cos(a1)),
                           (nq[0] * math.sin(a1), nq[1], nq[0] * math.cos(a1)), (nq[0] * math.sin(a0), nq[1], nq[0] * math.cos(a0))]
                odd = False
                if 'stripes' in pattern:
                    odd = int(math.floor((j + 0.5) / seg * pattern['stripes'] + pattern.get('twist', 0) * (p[1] + q[1]) / 2)) % 2 == 1
                self.emit(g, pts, [tp, tp, tq, tq], hint, part, index, odd, normals)

    def tube(self, part, g, index, points, closed):
        n = len(points)
        sides = self.segments(part.get('sides', 12))
        r0, r1 = part['radius']
        power = part.get('power', 1.0)
        pattern = part.get('pattern', {})
        tangents = []
        for i in range(n):
            if closed:
                a, b = points[(i - 1) % n], points[(i + 1) % n]
            else:
                a, b = points[max(0, i - 1)], points[min(n - 1, i + 1)]
            tangents.append(normalize(sub(b, a)))
        t0 = tangents[0]
        ref = (0.0, 0.0, 1.0) if abs(t0[2]) < 0.9 else (1.0, 0.0, 0.0)
        normal = normalize(cross(t0, ref))
        frames = []
        for i in range(n):
            t = tangents[i]
            normal = normalize(sub(normal, tuple(v * dot(normal, t) for v in t)))
            frames.append((normal, cross(t, normal)))
        radii = []
        for i in range(n):
            s = i / (n - 1) if n > 1 else 0.0
            radii.append(r0 if closed else r1 + (r0 - r1) * (1 - s) ** power)
        params = [0.5 - 0.5 * math.cos(2 * math.pi * i / n) if closed else i / (n - 1) for i in range(n)]

        def direction(i, k):
            a = 2 * math.pi * k / sides
            nn, bb = frames[i]
            return tuple(nn[j] * math.cos(a) + bb[j] * math.sin(a) for j in range(3))

        def ring(i, k):
            d = direction(i, k)
            return add(points[i], tuple(radii[i] * d[j] for j in range(3)))

        for i in range(n if closed else n - 1):
            nxt = (i + 1) % n
            center = tuple((points[i][j] + points[nxt][j]) / 2 for j in range(3))
            for k in range(sides):
                pts = [ring(i, k), ring(i, k + 1), ring(nxt, k + 1), ring(nxt, k)]
                normals = [direction(i, k), direction(i, k + 1), direction(nxt, k + 1), direction(nxt, k)]
                mid = tuple(sum(p[j] for p in pts) / 4 for j in range(3))
                odd = False
                if 'ridges' in pattern:
                    odd = i % pattern['ridges'] == pattern['ridges'] - 1
                elif 'stripes' in pattern:
                    odd = int((k + 0.5) / sides * pattern['stripes']) % 2 == 1
                self.emit(g, pts, [params[i], params[i], params[nxt], params[nxt]], sub(mid, center), part, index, odd, normals)
        if not closed and part.get('caps', True):
            for end, sign in ((0, -1), (n - 1, 1)):
                if radii[end] < 1e-5:
                    continue
                for k in range(sides):
                    pts = [points[end], ring(end, k), ring(end, k + 1), ring(end, k + 1)]
                    self.emit(g, pts, [params[end]] * 4, tuple(sign * v for v in tangents[end]), part, index)

    def sphere(self, part, g, index):
        c = part['sphere']
        r = part['r'] if isinstance(part['r'], list) else [part['r']] * 3
        lon = self.segments(part.get('seg', 12))
        lat = max(3, lon // 2)

        def unit(a, b):
            phi = math.pi * a / lat - math.pi / 2
            theta = 2 * math.pi * b / lon
            return (math.cos(phi) * math.sin(theta), math.sin(phi), math.cos(phi) * math.cos(theta))

        for a in range(lat):
            for b in range(lon):
                us = [unit(a, b), unit(a, b + 1), unit(a + 1, b + 1), unit(a + 1, b)]
                pts = [(c[0] + r[0] * u[0], c[1] + r[1] * u[1], c[2] + r[2] * u[2]) for u in us]
                normals = [(u[0] / r[0], u[1] / r[1], u[2] / r[2]) for u in us]
                mid = tuple(sum(p[j] for p in pts) / 4 for j in range(3))
                self.emit(g, pts, [a / lat, a / lat, (a + 1) / lat, (a + 1) / lat], sub(mid, c), part, index, False, normals)

    def gem(self, part, g, index):
        c = part['gem']
        r, up, down, sides = part['r'], part['up'], part['down'], part.get('sides', 6)
        top, bottom = (c[0], c[1] + up, c[2]), (c[0], c[1] - down, c[2])
        ring = [(c[0] + r * math.sin(2 * math.pi * k / sides), c[1], c[2] + r * math.cos(2 * math.pi * k / sides)) for k in range(sides)]
        facets = part.get('pattern', {}).get('facets', False)
        for k in range(sides):
            a, b = ring[k], ring[(k + 1) % sides]
            for apex, t, parity in ((top, 1.0, 0), (bottom, 0.0, 1)):
                pts = [a, b, apex, apex]
                mid = tuple(sum(p[j] for p in pts) / 4 for j in range(3))
                self.emit(g, pts, [0.5, 0.5, t, t], sub(mid, c), part, index, facets and (k + parity) % 2 == 1)

    def prism(self, part, g, index):
        poly = part['prism']
        z0, z1 = part['z']
        cx = sum(p[0] for p in poly) / len(poly)
        cy = sum(p[1] for p in poly) / len(poly)
        ys = [p[1] for p in poly]
        lo, hi = min(ys), max(ys)

        def t(y):
            return (y - lo) / (hi - lo) if hi > lo else 0.5

        n = len(poly)
        for k in range(n):
            a, b = poly[k], poly[(k + 1) % n]
            for z, sign in ((z1, 1), (z0, -1)):
                pts = [(cx, cy, z), (a[0], a[1], z), (b[0], b[1], z), (b[0], b[1], z)]
                self.emit(g, pts, [t(cy), t(a[1]), t(b[1]), t(b[1])], (0, 0, sign), part, index)
            ex, ey = b[0] - a[0], b[1] - a[1]
            nx, ny = ey, -ex
            if nx * ((a[0] + b[0]) / 2 - cx) + ny * ((a[1] + b[1]) / 2 - cy) < 0:
                nx, ny = -nx, -ny
            pts = [(a[0], a[1], z0), (b[0], b[1], z0), (b[0], b[1], z1), (a[0], a[1], z1)]
            self.emit(g, pts, [t(a[1]), t(b[1]), t(b[1]), t(a[1])], (nx, ny, 0), part, index)

    def poly(self, part, g, index):
        raw = part['poly']
        pts3 = [tuple(p[:3]) for p in raw]
        n = len(pts3)
        ys = [p[1] for p in pts3]
        lo, hi = min(ys), max(ys)

        def t_of(k):
            if len(raw[k]) > 3:
                return raw[k][3]
            return (raw[k][1] - lo) / (hi - lo) if hi > lo else 0.5

        if 'fan' in part:
            f = part['fan']
            c, tc = tuple(f[:3]), (f[3] if len(f) > 3 else 0.5)
        else:
            c = tuple(sum(p[j] for p in pts3) / n for j in range(3))
            tc = sum(t_of(k) for k in range(n)) / n
        normal = newell(pts3)
        for k in range(n):
            a, b = pts3[k], pts3[(k + 1) % n]
            self.emit(g, [c, a, b, b], [tc, t_of(k), t_of((k + 1) % n), t_of((k + 1) % n)], normal, part, index)

    def strip(self, part, g, index):
        rows = part['strip']
        for i in range(len(rows) - 1):
            (l0, r0), (l1, r1) = rows[i], rows[i + 1]
            pts = [tuple(l0[:3]), tuple(l1[:3]), tuple(r1[:3]), tuple(r0[:3])]
            self.emit(g, pts, [l0[3], l1[3], r1[3], r0[3]], (0.0, 0.0, 1.0), part, index)


    def sheet(self, part, g, index):
        grid = part['sheet']
        rows, cols = len(grid), len(grid[0])
        P = [[tuple(v[:3]) for v in row] for row in grid]
        N = sheet_normals(P)
        order = list(range(0, rows, 2 if self.quality < 0.5 and rows > 4 else 1))
        if order[-1] != rows - 1:
            order.append(rows - 1)
        for a, b in zip(order, order[1:]):
            for c in range(cols - 1):
                pts = [P[a][c], P[b][c], P[b][c + 1], P[a][c + 1]]
                ns = [N[a][c], N[b][c], N[b][c + 1], N[a][c + 1]]
                hint = normalize(tuple(sum(n[k] for n in ns) for k in range(3))) if length(tuple(sum(n[k] for n in ns) for k in range(3))) > 1e-9 else (0.0, 0.0, 1.0)
                self.emit(g, pts, [grid[a][c][3], grid[b][c][3], grid[b][c + 1][3], grid[a][c + 1][3]], hint, part, index, normals=ns)


def sheet_normals(P):
    """Per-vertex normals of a grid surface: cross product of the central differences along rows and columns;
    degenerate points (a feather tip where a whole row meets) borrow the normal of the previous row."""
    rows, cols = len(P), len(P[0])
    N = [[None] * cols for _ in range(rows)]
    for r in range(rows):
        for c in range(cols):
            du = sub(P[min(rows - 1, r + 1)][c], P[max(0, r - 1)][c])
            dv = sub(P[r][min(cols - 1, c + 1)], P[r][max(0, c - 1)])
            n = cross(du, dv)
            N[r][c] = normalize(n) if length(n) > 1e-12 else None
    for r in range(rows):
        for c in range(cols):
            if N[r][c] is None:
                near = [N[rr][c] for rr in (r - 1, r + 1) if 0 <= rr < rows and N[rr][c] is not None] or \
                       [N[r][cc] for cc in range(cols) if N[r][cc] is not None] or [(0.0, 0.0, 1.0)]
                N[r][c] = near[0]
    return N


# small vector helpers (tuples, 4x4 row-major lists)

def identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    m = identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def scale(x, y, z):
    m = identity()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    m = identity()
    m[1][1], m[1][2], m[2][1], m[2][2] = c, -s, s, c
    return m


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    m = identity()
    m[0][0], m[0][2], m[2][0], m[2][2] = c, s, -s, c
    return m


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    m = identity()
    m[0][0], m[0][1], m[1][0], m[1][1] = c, -s, s, c
    return m


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def apply_dir(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


def apply3(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


def normal_matrix(m):
    a = [[m[i][j] for j in range(3)] for i in range(3)]
    det = (a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
           + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]))
    if abs(det) < 1e-12:
        return a
    inv = [[0.0] * 3 for _ in range(3)]
    for i in range(3):
        for j in range(3):
            minor = [[a[r][c] for c in range(3) if c != i] for r in range(3) if r != j]
            inv[i][j] = ((-1) ** (i + j)) * (minor[0][0] * minor[1][1] - minor[0][1] * minor[1][0]) / det
    return [[inv[j][i] for j in range(3)] for i in range(3)]


def newell(pts):
    nx = ny = nz = 0.0
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        nx += (a[1] - b[1]) * (a[2] + b[2])
        ny += (a[2] - b[2]) * (a[0] + b[0])
        nz += (a[0] - b[0]) * (a[1] + b[1])
    return (nx, ny, nz)


def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def normalize(a):
    n = length(a)
    return tuple(v / n for v in a) if n > 1e-12 else (0.0, 1.0, 0.0)


# ---------------------------------------------------------------------------------------------- preview

def look_at(eye, target):
    f = normalize(sub(target, eye))
    r = normalize(cross(f, (0.0, 1.0, 0.0)))
    u = cross(r, f)
    return r, u, f


def box(lo, hi, color):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    faces = [
        [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], [(x0, y0, z0), (x0, y0, z1), (x1, y0, z1), (x1, y0, z0)],
        [(x0, y0, z1), (x0, y1, z1), (x1, y1, z1), (x1, y0, z1)], [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
        [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)],
    ]
    middle = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
    return [(f, color, middle) for f in faces]


def render(builder_quads, extra, eye, target, size, glows):
    import numpy as np
    ss = 2
    w = h = size * ss
    img = np.zeros((h, w, 3), dtype=np.float32)
    img[:] = (0.137, 0.149, 0.176)
    zbuf = np.full((h, w), np.inf, dtype=np.float32)
    r, u, f = look_at(eye, target)
    focal = 2.4 * w / 2

    def project(p):
        d = sub(p, eye)
        x, y, z = dot(d, r), dot(d, u), dot(d, f)
        return (w / 2 + focal * x / z, h / 2 - focal * y / z, z)

    tris = []
    L = normalize(LIGHT)
    for face, color, middle in extra:
        n = normalize(newell(face))
        cen = tuple(sum(p[j] for p in face) / 4 for j in range(3))
        if dot(n, sub(cen, middle)) < 0:
            n = tuple(-v for v in n)
        if dot(n, sub(cen, eye)) > 0:
            continue
        k = 0.5 + 0.5 * max(0.0, dot(n, L))
        col = tuple(c * k for c in color)
        tris.append((face, [col] * 4, 1.0))
    for cam, colors, n in builder_quads:
        tris.append((cam, [c[:3] for c in colors], colors[0][3]))
    for pts, cols, alpha in tris:
        pp = [project(p) for p in pts]
        if any(p[2] <= 0.01 for p in pp):
            continue
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            P = np.array([pp[a], pp[b], pp[c]], dtype=np.float64)
            area = (P[1, 0] - P[0, 0]) * (P[2, 1] - P[0, 1]) - (P[2, 0] - P[0, 0]) * (P[1, 1] - P[0, 1])
            if abs(area) < 1e-9:
                continue
            x0, x1 = int(max(0, np.floor(P[:, 0].min()))), int(min(w - 1, np.ceil(P[:, 0].max())))
            y0, y1 = int(max(0, np.floor(P[:, 1].min()))), int(min(h - 1, np.ceil(P[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
            px, py = xs + 0.5, ys + 0.5
            w0 = ((P[1, 0] - px) * (P[2, 1] - py) - (P[2, 0] - px) * (P[1, 1] - py)) / area
            w1 = ((P[2, 0] - px) * (P[0, 1] - py) - (P[0, 0] - px) * (P[2, 1] - py)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * P[0, 2] + w1 * P[1, 2] + w2 * P[2, 2]
            sub_z = zbuf[y0:y1 + 1, x0:x1 + 1]
            mask = inside & (z < sub_z)
            if not mask.any():
                continue
            C = np.array([cols[a], cols[b], cols[c]], dtype=np.float32) / 255.0
            color = w0[..., None] * C[0] + w1[..., None] * C[1] + w2[..., None] * C[2]
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[mask] = region[mask] * (1 - alpha) + color[mask] * alpha
            sub_z[mask] = z[mask]
    for center, radius, color, alpha in glows:
        cx, cy, cz = project(center)
        if cz <= 0.01:
            continue
        rr = focal * radius / cz
        x0, x1 = int(max(0, cx - rr)), int(min(w - 1, cx + rr))
        y0, y1 = int(max(0, cy - rr)), int(min(h - 1, cy + rr))
        if x1 <= x0 or y1 <= y0:
            continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2) / max(rr, 1e-6)
        a = np.clip(1 - d, 0, 1) * alpha
        img[y0:y1 + 1, x0:x1 + 1] += a[..., None] * (np.array(color, dtype=np.float32) / 255.0)
    img = np.clip(img, 0, 1)
    img = img.reshape(size, ss, size, ss, 3).mean(axis=(1, 3))
    return (img * 255).astype(np.uint8)


def glow_list(builder):
    out = []
    for part, g, idx in builder.glows:
        color = builder.paint(part.get('paint', 'c'), 0.5, 0, idx)
        alpha = builder.alpha * part.get('alpha', 0.3)
        if 'glowdisc' in part:
            out.append((apply(builder.world, apply(g, part['glowdisc'])), part['size'], color, alpha))
        else:
            radius = (part.get('glowring') or part.get('glowflat'))[0]
            for k in range(12):
                a = 2 * math.pi * k / 12
                p = apply(builder.world, apply(g, (radius * math.sin(a), part.get('y', 0.0), radius * math.cos(a))))
                out.append((p, radius * 0.35, color, alpha * 0.35))
    return out


def preview(path, data, main=0xFF6A2B, second=0xB45CFF):
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    cell = 300
    models = [('hat', m) for m in data['hats']] + [('wing', m) for m in data['wings']]
    cols = 4
    rows = math.ceil(len(models) * 2 / cols)
    sheet = Image.new('RGB', (cols * cell, rows * (cell + 22)), (35, 38, 45))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(str(ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/font/inter-semibold.ttf'), 15)
    except OSError:
        font = ImageFont.load_default()
    skin = (200, 152, 118)
    shirt = (70, 110, 170)
    for index, (kind, model) in enumerate(models):
        if kind == 'hat':
            views = [((0.9, 0.55, 1.25), (0.0, 0.05, 0.0)), ((-1.3, 0.35, -0.6), (0.0, 0.07, 0.0))]
            head = box((-0.2344, -0.4688, -0.2344), (0.2344, 0.0, 0.2344), skin)
            extra = head
            anchor = identity()
        else:
            views = [((1.6, 1.9, -2.3), (0.0, 1.2, 0.0)), ((2.4, 1.5, 0.9), (0.0, 1.15, 0.0))]
            p = PX
            extra = (box((-4 * p, 24 * p, -4 * p), (4 * p, 32 * p, 4 * p), skin) + box((-4 * p, 12 * p, -2 * p), (4 * p, 24 * p, 2 * p), shirt)
                     + box((4 * p, 12 * p, -2 * p), (8 * p, 24 * p, 2 * p), skin) + box((-8 * p, 12 * p, -2 * p), (-4 * p, 24 * p, 2 * p), skin)
                     + box((-4 * p, 0, -2 * p), (4 * p, 12 * p, 2 * p), (60, 60, 120)))
            anchor = translate(0.0, 21 * p, -2.2 * p)
        for v, (eye, target) in enumerate(views):
            world = matmul(translate(-eye[0], -eye[1], -eye[2]), anchor)
            b = Builder(main, second, time=0.9, world=world)
            b.build(model['parts'])
            quads = [([add(p, eye) for p in cam], colors, n) for cam, colors, n in b.quads]
            glows = [(add(c, eye), s, col, a) for c, s, col, a in glow_list(b)]
            img = render(quads, extra, eye, target, cell, glows)
            slot = index * 2 + v
            x, y = (slot % cols) * cell, (slot // cols) * (cell + 22)
            sheet.paste(Image.fromarray(img), (x, y + 22))
            if v == 0:
                draw.text((x + 8, y + 3), f"{index + 1 if kind == 'hat' else 'Крылья ' + str(index - len(data['hats']) + 1)}. {model['name']}  ({len(b.quads)} видимых)",
                          fill=(235, 238, 245), font=font)
    sheet.save(path)


def preview_outfit(path, data, main=0xFF6A2B, second=0xB45CFF, cell=340):
    """Capes (back, three-quarter, side) and accessories (front three-quarter, side, back) on the body."""
    from PIL import Image, ImageDraw, ImageFont
    p = PX
    body = (box((-4 * p, 24 * p, -4 * p), (4 * p, 32 * p, 4 * p), (200, 152, 118)) + box((-4 * p, 12 * p, -2 * p), (4 * p, 24 * p, 2 * p), (70, 110, 170))
            + box((4 * p, 12 * p, -2 * p), (8 * p, 24 * p, 2 * p), (200, 152, 118)) + box((-8 * p, 12 * p, -2 * p), (-4 * p, 24 * p, 2 * p), (200, 152, 118))
            + box((-4 * p, 0, -2 * p), (4 * p, 12 * p, 2 * p), (60, 60, 120)))
    items = [('cape', m) for m in data['capes']] + [('extra', m) for m in data['extras']]
    sheet = Image.new('RGB', (3 * cell, len(items) * (cell + 22)), (35, 38, 45))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(str(ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/font/inter-semibold.ttf'), 15)
    except OSError:
        font = ImageFont.load_default()
    for index, (kind, model) in enumerate(items):
        if kind == 'cape':
            anchor = matmul(translate(0.0, 24 * p, -2.6 * p), rot_x(math.radians(8)))
            views = [((0.0, 1.2, -2.4), (0.0, 0.95, 0.0)), ((1.8, 1.35, -1.8), (0.0, 0.95, 0.0)), ((2.5, 1.1, 0.1), (0.0, 0.9, 0.0))]
        elif model.get('attach') == 'head':
            anchor = translate(0.0, 32 * p, 0.0)
            views = [((0.55, 1.95, 1.1), (0.0, 1.7, 0.0)), ((1.25, 1.85, 0.05), (0.0, 1.7, 0.0)), ((-0.6, 1.95, -1.05), (0.0, 1.7, 0.0))]
        else:
            anchor = translate(0.0, 24 * p, 0.0)
            views = [((0.9, 1.75, 1.6), (0.0, 1.25, 0.0)), ((1.9, 1.5, 0.2), (0.0, 1.2, 0.0)), ((-0.8, 1.6, -1.7), (0.0, 1.25, 0.0))]
        for v, (eye, target) in enumerate(views):
            world = matmul(translate(-eye[0], -eye[1], -eye[2]), anchor)
            b = Builder(main, second, time=0.9, world=world)
            b.build(model['parts'])
            quads = [([add(q, eye) for q in cam], colors, n) for cam, colors, n in b.quads]
            glows = [(add(c, eye), sz, col, a) for c, sz, col, a in glow_list(b)]
            img = render(quads, body, eye, target, cell, glows)
            x, y = v * cell, index * (cell + 22)
            sheet.paste(Image.fromarray(img), (x, y + 22))
            if v == 0:
                draw.text((x + 8, y + 3), f"{'Плащ' if kind == 'cape' else 'Аксессуар'}: {model['name']}  ({len(b.quads)} видимых)", fill=(235, 238, 245), font=font)
    sheet.save(path)


def preview_wings(path, data, main=0xFF6A2B, second=0xB45CFF, cell=380):
    """Wings only, large: straight behind, three-quarter back, side and front views (shape and volume check)."""
    from PIL import Image, ImageDraw, ImageFont
    views = [((0.0, 1.5, -2.3), (0.0, 1.2, 0.0)), ((1.7, 1.75, -1.9), (0.0, 1.2, 0.0)), ((2.5, 1.35, 0.25), (0.0, 1.2, 0.0)), ((1.0, 1.4, 2.4), (0.0, 1.2, 0.0))]
    p = PX
    body = (box((-4 * p, 24 * p, -4 * p), (4 * p, 32 * p, 4 * p), (200, 152, 118)) + box((-4 * p, 12 * p, -2 * p), (4 * p, 24 * p, 2 * p), (70, 110, 170))
            + box((4 * p, 12 * p, -2 * p), (8 * p, 24 * p, 2 * p), (200, 152, 118)) + box((-8 * p, 12 * p, -2 * p), (-4 * p, 24 * p, 2 * p), (200, 152, 118))
            + box((-4 * p, 0, -2 * p), (4 * p, 12 * p, 2 * p), (60, 60, 120)))
    anchor = translate(0.0, 21 * p, -2.2 * p)
    models = data['wings']
    sheet = Image.new('RGB', (len(views) * cell, len(models) * (cell + 22)), (35, 38, 45))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype(str(ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/font/inter-semibold.ttf'), 15)
    except OSError:
        font = ImageFont.load_default()
    for index, model in enumerate(models):
        for v, (eye, target) in enumerate(views):
            world = matmul(translate(-eye[0], -eye[1], -eye[2]), anchor)
            b = Builder(main, second, time=0.9, world=world)
            b.build(model['parts'])
            quads = [([add(q, eye) for q in cam], colors, n) for cam, colors, n in b.quads]
            glows = [(add(c, eye), sz, col, a) for c, sz, col, a in glow_list(b)]
            img = render(quads, body, eye, target, cell, glows)
            x, y = v * cell, index * (cell + 22)
            sheet.paste(Image.fromarray(img), (x, y + 22))
            if v == 0:
                draw.text((x + 8, y + 3), f"{model['name']}  ({len(b.quads)} видимых)", fill=(235, 238, 245), font=font)
    sheet.save(path)


def main():
    data = {'version': 2, 'hats': hats(), 'wings': wings(), 'capes': capes(), 'extras': extras()}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    total = 0
    far = translate(0.0, -0.3, -2.4)
    for kind in ('hats', 'wings', 'capes', 'extras'):
        for model in data[kind]:
            b = Builder(0xFF6A2B, 0xB45CFF, world=far)
            b.build(model['parts'])
            total += len(b.quads)
            print(f"{kind[:-1]:<5} {model['name']:<10} {len(b.quads):5d} visible quads")
    print('hats', len(data['hats']), 'wings', len(data['wings']), 'capes', len(data['capes']), 'extras', len(data['extras']), 'visible quads', total, '->', OUT.relative_to(ROOT),
          f"{OUT.stat().st_size // 1024} KB")
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1], data)
    if '--wings' in sys.argv:
        preview_wings(sys.argv[sys.argv.index('--wings') + 1], data)
    if '--outfit' in sys.argv:
        preview_outfit(sys.argv[sys.argv.index('--outfit') + 1], data)


if __name__ == '__main__':
    main()
