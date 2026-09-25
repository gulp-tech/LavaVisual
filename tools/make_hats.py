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
    blade = [[0.018, -0.016], [0.13, -0.034], [0.205, -0.026], [0.218, 0.0], [0.205, 0.022], [0.12, 0.028], [0.018, 0.016]]
    h.append({'name': 'Пропеллер', 'parts': [
        {'revolve': [[0.25, 0.0], [0.246, 0.04], [0.228, 0.085], [0.19, 0.123], [0.13, 0.149], [0.065, 0.161], [0.0, 0.164]], 'seg': 32,
         'paint': 'c', 'alt': 'l', 'pattern': {'stripes': 4}, 'mat': 'satin'},
        {'revolve': [[0.4, 0.005], [0.34, 0.017], [0.28, 0.021], [0.235, 0.021]], 'arc': [-55, 55], 'seg': 18, 'two': True, 'paint': 'd', 'mat': 'satin'},
        {'torus': [0.249, 0.012], 'y': 0.006, 'seg': 32, 'sides': 6, 'paint': 'd', 'mat': 'satin'},
        {'revolve': [[0.014, 0.158], [0.014, 0.2], [0.0, 0.2]], 'seg': 10, 'paint': 'g', 'mat': 'metal'},
        {'sphere': [0, 0.206, 0], 'r': 0.024, 'seg': 12, 'paint': 'g', 'mat': 'metal'},
        {'group': {'at': [0, 0.207, 0], 'repeat': 3, 'spin': ['y', 9.0]}, 'parts': [
            {'group': {'rot': [-76, 0, 0]}, 'parts': [
                {'prism': blade, 'z': [-0.0035, 0.0035], 'paint': {'cycle': ['c', 'l', 'w']}, 'mat': 'gloss'},
            ]},
        ]},
        {'glowflat': [0.22], 'y': 0.207, 'paint': 'l', 'alpha': 0.12},
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
        {'revolve': [[0.43, 0.004], [0.36, 0.016], [0.29, 0.02], [0.235, 0.02]], 'arc': [-62, 62], 'seg': 22, 'two': True, 'paint': 'd', 'mat': 'satin'},
        {'torus': [0.253, 0.01], 'y': 0.006, 'seg': 36, 'sides': 6, 'paint': 'd', 'mat': 'satin'},
        {'sphere': [0, 0.169, 0], 'r': 0.021, 'seg': 10, 'paint': 'l', 'mat': 'satin'},
        {'group': {'at': [0, 0.085, 0.228], 'rot': [-28, 0, 0]}, 'parts': [
            {'prism': [[0.045 * math.cos(2 * math.pi * i / 16), 0.045 * math.sin(2 * math.pi * i / 16)] for i in range(16)], 'z': [0.0, 0.004], 'paint': 'l', 'mat': 'satin'},
            {'prism': star_polygon(0.03, 0.013), 'z': [0.004, 0.008], 'paint': 'g', 'mat': 'metal'},
        ]},
    ]})
    return h


# ---------------------------------------------------------------------------------------------- wings

def wing_group(parts, sweep=26, lift=6, swing=None, at=(0.035, 0.0, -0.012)):
    swing = swing or [['y', 16, 2.2, 0.0], ['z', 5, 2.2, 0.9]]
    return [{'group': {'mirror': True}, 'parts': [
        {'group': {'at': list(at), 'rot': [0, sweep, lift], 'swing': swing}, 'parts': parts},
    ]}]


def feathered(arm, primaries, secondaries, coverts, paints, mat, flame=False, embers=0):
    """Bird-like wing in the XY plane: arm polyline (root, elbow, wrist, tip) and feather rows."""
    root, elbow, wrist, tip = arm
    parts = [{'tube': [[r4(p[0]), r4(p[1]), 0.0] for p in (root, elbow, wrist, tip)], 'radius': [0.022, 0.008], 'sides': 8, 'paint': paints['arm'],
              'mat': 'metal' if flame else 'satin'}]
    profile = FLAME if flame else FEATHER
    count, (a0, a1), (l0, l1), width = primaries
    web = [root, elbow, wrist, tip]
    for i in range(count - 1, -1, -1):
        u = i / (count - 1)
        a = math.radians(a0 + (a1 - a0) * u)
        base = lerp2(wrist, tip, u)
        web.append((base[0] + math.cos(a) * (l0 + (l1 - l0) * u) * 0.8, base[1] + math.sin(a) * (l0 + (l1 - l0) * u) * 0.8))
    sc, (sa0, sa1), (sl0, sl1), _ = secondaries
    for i in range(sc - 1, -1, -1):
        u = i / (sc - 1)
        a = math.radians(sa0 + (sa1 - sa0) * u)
        base = lerp2(root, elbow, u * 2) if u < 0.5 else lerp2(elbow, wrist, (u - 0.5) * 2)
        web.append((base[0] + math.cos(a) * (sl0 + (sl1 - sl0) * u) * 0.8, base[1] + math.sin(a) * (sl0 + (sl1 - sl0) * u) * 0.8))
    center = (wrist[0] * 0.55, wrist[1] * 0.1 - 0.06)
    parts.append({'poly': [[r4(x), r4(y), 0.012, 0.3] for x, y in web], 'fan': [r4(center[0]), r4(center[1]), 0.012, 0.3],
                  'paint': paints['covert'], 'mat': mat})
    for i in range(count):
        u = i / (count - 1)
        base = lerp2(wrist, tip, u)
        angle = a0 + (a1 - a0) * u
        length = l0 + (l1 - l0) * u
        parts.append({'strip': ribbon(base, angle, length, width, profile, z=0.004 * (count - i), bend=0.09 if flame else 0.04, wave=0.05 if flame else 0.0,
                                      samples=FEATHER_AT), 'paint': paints['primary'], 'mat': mat})
        if flame and embers and i % 2 == 0:
            a = math.radians(angle)
            parts.append({'glowdisc': [r4(base[0] + math.cos(a) * length * 0.95), r4(base[1] + math.sin(a) * length * 0.95), 0.0], 'size': 0.07,
                          'paint': 'l', 'alpha': 0.4, 'detail': True})
    count, (a0, a1), (l0, l1), width = secondaries
    for i in range(count):
        u = i / (count - 1)
        base = lerp2(root, elbow, u * 2) if u < 0.5 else lerp2(elbow, wrist, (u - 0.5) * 2)
        angle = a0 + (a1 - a0) * u
        length = l0 + (l1 - l0) * u
        parts.append({'strip': ribbon(base, angle, length, width, profile, z=-0.003 - 0.002 * i, bend=0.05 if flame else 0.02, wave=0.04 if flame else 0.0,
                                      samples=FEATHER_AT), 'paint': paints['secondary'], 'mat': mat})
    for row, (count, length, width, drop) in enumerate(coverts):
        for i in range(count):
            u = i / max(1, count - 1)
            base = lerp2(root, elbow, u * 2) if u < 0.5 else lerp2(elbow, tip, (u - 0.5) * 2 * 0.72)
            base = (base[0], base[1] - drop)
            parts.append({'strip': ribbon(base, -92 + 26 * u, length * (1 - 0.25 * u), width, profile, z=-0.016 - 0.01 * row, bend=0.02,
                                          samples=FEATHER_AT), 'paint': paints['covert'], 'mat': mat})
    return parts


def membrane(root, elbow, wrist, tips, body, depth, bone_paint, skin_paint, skin_mat, thick=0.024, spikes=0, claw='w'):
    parts = [{'tube': [[r4(p[0]), r4(p[1]), 0.0] for p in (root, elbow, wrist)], 'radius': [thick, thick * 0.7], 'sides': 8, 'paint': bone_paint, 'mat': 'gloss',
              'pattern': {'ridges': 3}, 'alt': 'd'}]
    for tip in tips:
        mid = lerp2(wrist, tip, 0.5)
        bend = (mid[0] + (tip[1] - wrist[1]) * 0.06, mid[1] - (tip[0] - wrist[0]) * 0.06)
        parts.append({'tube': [[r4(wrist[0]), r4(wrist[1]), 0.0], [r4(bend[0]), r4(bend[1]), 0.0], [r4(tip[0]), r4(tip[1]), 0.0]],
                      'radius': [thick * 0.55, thick * 0.18], 'sides': 6, 'paint': bone_paint, 'mat': 'gloss'})
        parts.append({'gem': [r4(tip[0]), r4(tip[1]), 0.0], 'r': thick * 0.3, 'up': thick * 0.5, 'down': thick * 0.5, 'sides': 4, 'paint': claw, 'mat': 'gloss', 'detail': True})
    outline = []
    for a, b in zip(tips, tips[1:]):
        outline.append([a, scallop(a, b, wrist, depth), b])
    for (a, pts, b) in outline:
        poly = [[r4(a[0]), r4(a[1]), 0.0, 1.0]] + [[r4(x), r4(y), 0.0, r4(t)] for x, y, t in pts] + [[r4(b[0]), r4(b[1]), 0.0, 1.0]]
        parts.append({'poly': poly, 'fan': [r4(wrist[0]), r4(wrist[1]), 0.0, 0.0], 'paint': skin_paint, 'mat': skin_mat, 'alpha': 0.96})
    last = tips[-1]
    inner = [[r4(last[0]), r4(last[1]), 0.0, 1.0]] + [[r4(x), r4(y), 0.0, r4(t)] for x, y, t in scallop(last, body, wrist, depth * 0.8)] + \
            [[r4(body[0]), r4(body[1]), 0.0, 0.9], [r4(root[0]), r4(root[1]), 0.0, 0.4], [r4(elbow[0]), r4(elbow[1]), 0.0, 0.2]]
    parts.append({'poly': inner, 'fan': [r4(wrist[0]), r4(wrist[1]), 0.0, 0.0], 'paint': skin_paint, 'mat': skin_mat, 'alpha': 0.96})
    parts.append({'gem': [r4(wrist[0]), r4(wrist[1] + 0.02), 0.0], 'r': thick * 0.55, 'up': thick * 2.6, 'down': thick * 0.4, 'sides': 5, 'paint': claw, 'mat': 'gloss'})
    for i in range(spikes):
        u = (i + 0.5) / spikes
        p = lerp2(root, elbow, u * 2) if u < 0.5 else lerp2(elbow, wrist, (u - 0.5) * 2)
        parts.append({'gem': [r4(p[0] - 0.01), r4(p[1] + thick * 0.8), 0.0], 'r': thick * 0.35, 'up': thick * 1.6, 'down': thick * 0.3, 'sides': 4, 'paint': 'l', 'mat': 'gloss'})
    return parts


def wings():
    w = []
    w.append({'name': 'Ангел', 'parts': wing_group(feathered(
        ((0.0, 0.0), (0.15, 0.25), (0.3, 0.43), (0.5, 0.47)),
        (12, (-90, -38), (0.4, 0.5), 0.12),
        (11, (-101, -90), (0.34, 0.43), 0.12),
        [(13, 0.2, 0.095, 0.03), (11, 0.12, 0.08, -0.02)],
        {'arm': 'w', 'primary': ['w', 'lw', 'cw'], 'secondary': ['w', 'lw'], 'covert': ['w', 'w']}, 'satin'), sweep=24, lift=8)})
    w.append({'name': 'Демон', 'parts': wing_group(membrane(
        (0.0, 0.0), (0.16, 0.22), (0.33, 0.36),
        [(0.68, 0.25), (0.62, -0.03), (0.45, -0.25)], (0.06, -0.2), 0.3, 'k', ['d', 'c'], 'satin', thick=0.022, claw='w'),
        sweep=22, lift=4, swing=[['y', 14, 1.8, 0.0], ['z', 6, 1.8, 0.8]])})
    fore = catmull([(0.02, 0.05), (0.09, 0.3), (0.23, 0.45), (0.4, 0.47), (0.53, 0.39), (0.53, 0.25), (0.44, 0.12), (0.29, 0.04), (0.12, 0.005)], 4)
    hind = catmull([(0.02, -0.02), (0.18, -0.03), (0.34, -0.11), (0.38, -0.25), (0.3, -0.37), (0.16, -0.39), (0.06, -0.27), (0.0, -0.1)], 4)

    def shape(points, z, shrink, paint, mat, alpha=1.0, anchor=(0.03, 0.0)):
        cx, cy = anchor
        pts = []
        for x, y in points:
            d = math.hypot(x - cx, y - cy)
            pts.append([r4(cx + (x - cx) * shrink), r4(cy + (y - cy) * shrink), r4(z), r4(min(1.0, d / 0.5))])
        return {'poly': pts, 'fan': [cx, cy, r4(z), 0.0], 'paint': paint, 'mat': mat, 'alpha': alpha}

    butterfly = []
    for pts, anchor in ((fore, (0.03, 0.03)), (hind, (0.03, -0.03))):
        butterfly.append(shape(pts, 0.0, 1.0, 'k', 'satin', anchor=anchor))
        for z in (0.0025, -0.0025):
            butterfly.append(shape(pts, z, 0.84, ['c', 'l'], 'satin', anchor=anchor))
    for z in (0.0045, -0.0045):
        butterfly.append({'poly': circle(0.22, -0.21, z, 0.055, 16, 0.5), 'paint': 'w', 'mat': 'gloss'})
        butterfly.append({'poly': circle(0.22, -0.21, z * 1.3, 0.032, 14, 0.5), 'paint': 'k', 'mat': 'gloss'})
        butterfly.append({'poly': circle(0.215, -0.2, z * 1.6, 0.012, 10, 0.5), 'paint': 'lw', 'mat': 'glow'})
        for x, y, r in ((0.47, 0.37, 0.016), (0.5, 0.3, 0.013), (0.43, 0.42, 0.012), (0.34, 0.44, 0.01)):
            butterfly.append({'poly': circle(x, y, z, r, 10, 0.5), 'paint': 'w', 'mat': 'satin', 'detail': True})
    butterfly.append({'sphere': [0.0, 0.0, 0.0], 'r': [0.02, 0.05, 0.02], 'seg': 8, 'paint': 'k', 'mat': 'gloss'})
    w.append({'name': 'Бабочка', 'parts': wing_group(butterfly, sweep=30, lift=4, swing=[['y', 30, 6.0, 0.0], ['z', 4, 6.0, 0.5]], at=(0.02, -0.01, -0.02))})
    w.append({'name': 'Дракон', 'parts': wing_group(membrane(
        (0.0, 0.0), (0.18, 0.27), (0.37, 0.43),
        [(0.84, 0.36), (0.82, 0.03), (0.65, -0.26), (0.4, -0.38)], (0.05, -0.25), 0.36, 'dl', ['c', 'd'], 'satin', thick=0.03, spikes=4, claw='w'),
        sweep=20, lift=6, swing=[['y', 12, 1.4, 0.0], ['z', 7, 1.4, 0.9]])})
    w.append({'name': 'Феникс', 'parts': wing_group(feathered(
        ((0.0, 0.0), (0.15, 0.25), (0.31, 0.44), (0.52, 0.5)),
        (11, (-86, -26), (0.4, 0.54), 0.13),
        (10, (-102, -88), (0.32, 0.42), 0.13),
        [(11, 0.18, 0.1, 0.03)],
        {'arm': 'g', 'primary': ['g', 'c', 'l'], 'secondary': ['g', 'c'], 'covert': ['g', 'cw']}, 'glow', flame=True, embers=1),
        sweep=24, lift=10, swing=[['y', 12, 1.6, 0.0], ['z', 6, 1.6, 0.9]]) + [
        {'glowdisc': [0.0, 0.05, -0.05], 'size': 0.22, 'paint': 'c', 'alpha': 0.28}]})
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
            if not part.get('two', 'poly' in part or 'strip' in part):
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


def main():
    data = {'version': 2, 'hats': hats(), 'wings': wings()}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    total = 0
    far = translate(0.0, -0.3, -2.4)
    for kind in ('hats', 'wings'):
        for model in data[kind]:
            b = Builder(0xFF6A2B, 0xB45CFF, world=far)
            b.build(model['parts'])
            total += len(b.quads)
            print(f"{kind[:-1]:<5} {model['name']:<10} {len(b.quads):5d} visible quads")
    print('hats', len(data['hats']), 'wings', len(data['wings']), 'visible quads', total, '->', OUT.relative_to(ROOT),
          f"{OUT.stat().st_size // 1024} KB")
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1], data)


if __name__ == '__main__':
    main()
