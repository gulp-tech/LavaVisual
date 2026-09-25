#!/usr/bin/env python3
"""LavaVisual hat models.

    python3 tools/make_hats.py                  # writes assets/lavavisual/hats.json
    python3 tools/make_hats.py --preview a.png  # also renders a contact sheet of every hat

Local frame of a hat: origin = top of the head (skin hat layer), +Y up, +Z towards the face, units = blocks
at player scale 1 (the head is 0.5 blocks wide). The Java side (effects/Hats.java) interprets the same JSON;
`Builder` below mirrors it one to one so the preview shows what the game draws.

Part keys
  revolve [[r, y], ...]  surface of revolution; open profiles run from the outer/lower end towards the axis,
                         closed ones counter-clockwise in (r, y). Options: closed, two (two-sided), seg, phase,
                         arc [from, to] in degrees (0 = front), curl [lift, r0, r1] (brim lifted at the sides).
  tube / bezier + n      swept circle along points; radius [r0, r1], power, sides, caps, closed.
  torus [R, r]           closed tube around the Y axis at height y.
  sphere [x, y, z], r    r may be [rx, ry, rz].
  gem [x, y, z]          bipyramid: r, up, down, sides.
  prism [[x, y], ...]    star-shaped polygon in the XY plane extruded along z [z0, z1].
  poly [[x, y, z], ...]  flat star-shaped polygon, two-sided.
  glowring [R, w], glowflat [R], glowdisc [x, y, z] + size: soft additive-looking glow (no depth write).
  group {at, rot [x, y, z] (applied Z, X, then Y), spin [axis, rad/s], bob [amp, freq, phase step],
         repeat n (around Y), mirror (X)} + parts.
Paint: key, [from, to] gradient, or {"cycle": [...]} by repeat index. Keys: c (hat colour), l (second tone),
m, d (dark tint), dl, cw, lw, w (white), k (black), g (gold), p (pink). alt + pattern (stripes n [twist],
ridges k, facets) apply to the "pattern" style only; lit=false parts are not shaded.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/hats.json'


def bezier(a, b, c, d, n):
    points = []
    for i in range(n):
        t = i / (n - 1)
        u = 1 - t
        points.append([round(u * u * u * a[k] + 3 * u * u * t * b[k] + 3 * u * t * t * c[k] + t * t * t * d[k], 4) for k in range(3)])
    return points


def star_polygon(outer, inner, points=5):
    result = []
    for i in range(points * 2):
        angle = math.pi / 2 + i * math.pi / points
        radius = outer if i % 2 == 0 else inner
        result.append([round(math.cos(angle) * radius, 4), round(math.sin(angle) * radius, 4)])
    return result


def cone_profile(radius, height, rings):
    return [[round(radius * (1 - i / rings), 4), round(height * i / rings, 4)] for i in range(rings + 1)]


def hats():
    h = []
    h.append({'name': 'Конус', 'parts': [
        {'revolve': cone_profile(0.52, 0.26, 3), 'seg': 36, 'two': True, 'paint': ['c', 'l'], 'alt': ['m', 'l'], 'pattern': {'stripes': 18}},
        {'torus': [0.52, 0.011], 'seg': 36, 'sides': 6, 'paint': 'l', 'lit': False},
        {'sphere': [0, 0.262, 0], 'r': 0.02, 'seg': 8, 'paint': 'l'},
    ]})
    h.append({'name': 'Нимб', 'parts': [
        {'group': {'at': [0, 0.2, 0], 'bob': [0.014, 2.2, 0]}, 'parts': [
            {'torus': [0.2, 0.024], 'seg': 40, 'sides': 8, 'paint': ['c', 'l']},
            {'glowring': [0.2, 0.09], 'paint': 'c', 'alpha': 0.5},
        ]},
    ]})
    spikes = []
    for k in range(10):
        a0, a1, am = (math.radians(36 * k + d) for d in (-18, 18, 0))
        tall = k % 2 == 0
        top = 0.075 + (0.108 if tall else 0.064)
        apex_r = 0.2 * math.cos(math.radians(18)) + 0.014
        p0 = [round(0.2 * math.sin(a0), 4), 0.075, round(0.2 * math.cos(a0), 4)]
        p1 = [round(0.2 * math.sin(a1), 4), 0.075, round(0.2 * math.cos(a1), 4)]
        apex = [round(apex_r * math.sin(am), 4), round(top, 4), round(apex_r * math.cos(am), 4)]
        spikes.append({'poly': [p0, p1, apex], 'paint': ['c', 'm']})
        spikes.append({'sphere': apex, 'r': 0.016 if tall else 0.011, 'seg': 8, 'paint': 'g'})
    h.append({'name': 'Корона', 'parts': [
        {'revolve': [[0.185, 0.0], [0.2, 0.0], [0.2, 0.075], [0.185, 0.075]], 'closed': True, 'seg': 10, 'phase': 18, 'paint': 'c'},
        {'sphere': [0, 0, 0], 'r': [0.178, 0.088, 0.178], 'seg': 16, 'paint': 'dl'},
        *spikes,
        {'group': {'repeat': 5}, 'parts': [
            {'gem': [0, 0.0375, 0.196], 'r': 0.021, 'up': 0.028, 'down': 0.028, 'sides': 4, 'paint': 'l', 'alt': 'lw', 'pattern': {'facets': True}},
        ]},
    ]})
    h.append({'name': 'Цилиндр', 'parts': [
        {'revolve': [[0.19, 0.0], [0.355, 0.0], [0.366, 0.011], [0.355, 0.022], [0.19, 0.022]], 'closed': True, 'seg': 36,
         'curl': [0.05, 0.25, 0.366], 'paint': 'd'},
        {'revolve': [[0.2, 0.02], [0.192, 0.2], [0.212, 0.375], [0.0, 0.378]], 'seg': 32, 'paint': 'd'},
        {'revolve': [[0.197, 0.032], [0.207, 0.032], [0.204, 0.098], [0.193, 0.098]], 'closed': True, 'seg': 32, 'paint': 'c'},
    ]})
    witch = bezier([0, 0.004, 0], [0, 0.3, 0], [0, 0.46, -0.04], [0, 0.5, -0.24], 16)
    h.append({'name': 'Ведьмина', 'parts': [
        {'revolve': [[0.45, 0.045], [0.41, 0.022], [0.34, 0.007], [0.26, 0.0], [0.17, 0.0]], 'seg': 40, 'two': True, 'paint': 'd'},
        {'tube': witch, 'radius': [0.2, 0.0], 'sides': 20, 'caps': False, 'paint': 'd'},
        {'revolve': [[0.186, 0.012], [0.207, 0.012], [0.196, 0.07], [0.175, 0.07]], 'closed': True, 'seg': 32, 'paint': 'c'},
        {'group': {'at': [0, 0.041, 0.2], 'rot': [-10, 0, 0]}, 'parts': [
            {'prism': [[-0.028, -0.023], [0.028, -0.023], [0.028, 0.023], [-0.028, 0.023]], 'z': [0.0, 0.006], 'paint': 'g'},
            {'prism': [[-0.016, -0.012], [0.016, -0.012], [0.016, 0.012], [-0.016, 0.012]], 'z': [0.006, 0.009], 'paint': 'd'},
        ]},
    ]})
    h.append({'name': 'Колпак', 'parts': [
        {'group': {'at': [0.035, -0.004, 0], 'rot': [0, 0, -13]}, 'parts': [
            {'revolve': cone_profile(0.17, 0.39, 8), 'seg': 28, 'paint': 'c', 'alt': 'l', 'pattern': {'stripes': 8, 'twist': 9}},
            {'torus': [0.168, 0.017], 'y': 0.008, 'seg': 28, 'sides': 8, 'paint': 'w'},
            {'sphere': [0, 0.395, 0], 'r': 0.045, 'seg': 12, 'paint': 'w'},
        ]},
    ]})
    horn = bezier([0.115, -0.02, 0.06], [0.145, 0.1, 0.085], [0.26, 0.17, 0.03], [0.25, 0.275, -0.06], 14)
    h.append({'name': 'Рожки', 'parts': [
        {'group': {'mirror': True}, 'parts': [
            {'tube': horn, 'radius': [0.06, 0.0], 'power': 0.9, 'sides': 12, 'caps': False, 'paint': ['c', 'l'], 'alt': ['d', 'c'], 'pattern': {'ridges': 3}},
        ]},
    ]})
    h.append({'name': 'Ушки', 'parts': [
        {'group': {'mirror': True}, 'parts': [
            {'group': {'at': [0.13, -0.012, -0.005], 'rot': [-8, 0, -12]}, 'parts': [
                {'prism': [[-0.09, 0.0], [0.09, 0.0], [0.016, 0.19]], 'z': [-0.02, 0.02], 'paint': 'c'},
                {'prism': [[-0.054, 0.018], [0.054, 0.018], [0.011, 0.14]], 'z': [0.02, 0.026], 'paint': 'p'},
            ]},
        ]},
    ]})
    h.append({'name': 'Кристалл', 'parts': [
        {'group': {'at': [0, 0.34, 0], 'bob': [0.03, 1.6, 0], 'spin': ['y', 0.9]}, 'parts': [
            {'gem': [0, 0, 0], 'r': 0.095, 'up': 0.175, 'down': 0.115, 'sides': 6, 'paint': 'c', 'alt': 'l', 'pattern': {'facets': True}},
            {'glowdisc': [0, 0, 0], 'size': 0.26, 'paint': 'c', 'alpha': 0.35},
        ]},
        {'group': {'at': [0, 0.3, 0], 'repeat': 3, 'bob': [0.022, 2.0, 2.09], 'spin': ['y', -1.4]}, 'parts': [
            {'gem': [0.2, 0, 0], 'r': 0.028, 'up': 0.052, 'down': 0.038, 'sides': 4, 'paint': 'l', 'alt': 'lw', 'pattern': {'facets': True}},
        ]},
    ]})
    h.append({'name': 'Сомбреро', 'parts': [
        {'revolve': [[0.64, 0.085], [0.6, 0.048], [0.53, 0.02], [0.43, 0.006], [0.31, 0.0], [0.18, 0.0]], 'seg': 48, 'two': True,
         'paint': 'c', 'alt': 'm', 'pattern': {'stripes': 16}},
        {'torus': [0.64, 0.017], 'y': 0.085, 'seg': 48, 'sides': 8, 'paint': 'l'},
        {'revolve': [[0.19, 0.0], [0.182, 0.17], [0.168, 0.25], [0.13, 0.298], [0.07, 0.32], [0.0, 0.325]], 'seg': 32, 'paint': 'c'},
        {'revolve': [[0.186, 0.014], [0.197, 0.014], [0.19, 0.075], [0.179, 0.075]], 'closed': True, 'seg': 32, 'paint': 'l'},
        {'group': {'repeat': 16}, 'parts': [{'sphere': [0, 0.062, 0.662], 'r': 0.017, 'seg': 8, 'paint': 'g', 'detail': True}]},
    ]})
    blade = [[0.018, -0.016], [0.13, -0.034], [0.205, -0.026], [0.218, 0.0], [0.205, 0.022], [0.12, 0.028], [0.018, 0.016]]
    h.append({'name': 'Пропеллер', 'parts': [
        {'revolve': [[0.25, 0.0], [0.246, 0.04], [0.228, 0.085], [0.19, 0.123], [0.13, 0.149], [0.065, 0.161], [0.0, 0.164]], 'seg': 32,
         'paint': 'c', 'alt': 'l', 'pattern': {'stripes': 4}},
        {'torus': [0.249, 0.012], 'y': 0.006, 'seg': 32, 'sides': 6, 'paint': 'd'},
        {'revolve': [[0.013, 0.158], [0.013, 0.2], [0.0, 0.2]], 'seg': 10, 'paint': 'k'},
        {'sphere': [0, 0.205, 0], 'r': 0.023, 'seg': 10, 'paint': 'l'},
        {'group': {'at': [0, 0.206, 0], 'repeat': 3, 'spin': ['y', 9.0]}, 'parts': [
            {'group': {'rot': [-76, 0, 0]}, 'parts': [
                {'prism': blade, 'z': [-0.0035, 0.0035], 'paint': {'cycle': ['c', 'l', 'w']}},
            ]},
        ]},
        {'glowflat': [0.22], 'y': 0.206, 'paint': 'l', 'alpha': 0.1},
    ]})
    cycle = {'cycle': ['c', 'l', 'g', 'c', 'l']}
    h.append({'name': 'Звёзды', 'parts': [
        {'group': {'at': [0, 0.075, 0], 'repeat': 5, 'bob': [0.035, 2.3, 1.2566], 'spin': ['y', 1.15]}, 'parts': [
            {'group': {'at': [0.31, 0, 0], 'rot': [0, 90, 0], 'spin': ['z', 2.2]}, 'parts': [
                {'prism': star_polygon(0.064, 0.027), 'z': [-0.008, 0.008], 'paint': cycle, 'lit': False},
            ]},
            {'glowdisc': [0.31, 0, 0], 'size': 0.16, 'paint': cycle, 'alpha': 0.3},
        ]},
        {'glowring': [0.31, 0.02], 'y': 0.075, 'paint': 'c', 'alpha': 0.16},
    ]})
    santa = bezier([0, 0.03, 0], [0, 0.27, -0.02], [0.03, 0.375, -0.15], [0.09, 0.255, -0.285], 16)
    h.append({'name': 'Санта', 'parts': [
        {'tube': santa, 'radius': [0.205, 0.022], 'power': 1.1, 'sides': 20, 'caps': False, 'paint': 'c'},
        {'torus': [0.215, 0.05], 'y': 0.035, 'seg': 28, 'sides': 10, 'paint': 'w'},
        {'sphere': [0.09, 0.255, -0.285], 'r': 0.056, 'seg': 12, 'paint': 'w'},
    ]})
    h.append({'name': 'Кепка', 'parts': [
        {'revolve': [[0.255, 0.0], [0.25, 0.045], [0.228, 0.092], [0.185, 0.13], [0.12, 0.155], [0.06, 0.166], [0.0, 0.169]], 'seg': 32,
         'paint': 'c', 'alt': 'm', 'pattern': {'stripes': 6}},
        {'revolve': [[0.43, 0.004], [0.36, 0.016], [0.29, 0.02], [0.235, 0.02]], 'arc': [-62, 62], 'seg': 20, 'two': True, 'paint': 'd'},
        {'torus': [0.253, 0.01], 'y': 0.006, 'seg': 32, 'sides': 6, 'paint': 'd'},
        {'sphere': [0, 0.168, 0], 'r': 0.02, 'seg': 8, 'paint': 'l'},
    ]})
    return h


# ---------------------------------------------------------------------------------------------- interpreter

def mix(a, b, t):
    t = min(1.0, max(0.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


class Builder:
    """Mirror of effects/Hats.java: emits shaded quads (4 points + 4 RGBA colours) and glow shapes."""
    LIGHT = (0.33, 0.88, 0.34)

    def __init__(self, main, second, style=0, time=0.0, quality=1.0, alpha=1.0):
        self.c, self.l = rgb(main), rgb(second)
        self.style, self.time, self.quality, self.alpha = style, time, quality, alpha
        n = math.sqrt(sum(v * v for v in self.LIGHT))
        self.light = tuple(v / n for v in self.LIGHT)
        self.quads, self.glows = [], []

    # colours
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
        }[key]

    def paint(self, paint, t, y, index):
        if isinstance(paint, dict):
            cycle = paint['cycle']
            return self.key(cycle[index % len(cycle)], y)
        if isinstance(paint, list):
            return mix(self.key(paint[0], y), self.key(paint[1], y), t)
        return self.key(paint, y)

    # emission
    def emit(self, g, pts, params, hint, part, index, odd=False, two=False):
        """pts: local points; params: gradient parameter per point; hint: outward direction (local)."""
        local = [apply(g, p) for p in pts]
        world = local  # preview: hat space == world space
        normal = newell(world)
        if length(normal) < 1e-12:
            return
        h = apply_dir(g, hint)
        if dot(normal, h) < 0:
            world, local, params = world[::-1], local[::-1], params[::-1]
            normal = tuple(-v for v in normal)
        n = normalize(normal)
        pattern = odd and self.style == 0 and 'alt' in part
        paint = part['alt'] if pattern else part['paint']
        brightness = 1.0
        if part.get('lit', True):
            d = dot(n, self.light)
            brightness = 0.55 + 0.5 * max(0.0, d)
        colors = []
        for p, t in zip(local, params):
            base = self.paint(paint, t, p[1], index)
            colors.append(tuple(min(255, int(v * brightness)) for v in base) + (self.alpha * part.get('alpha', 1.0),))
        self.quads.append((world, colors, n))
        if two:
            self.emit(g, pts, params, tuple(-v for v in hint), part, index, odd, False)

    def segments(self, count):
        return max(6, int(round(count * (0.5 + 0.5 * self.quality))))

    def build(self, parts, g=None, index=0):
        g = g or identity()
        for part in parts:
            if part.get('detail') and self.quality < 0.5:
                continue
            if 'group' in part:
                self.group(part, g, index)
            elif 'revolve' in part:
                self.revolve(part, g, index)
            elif 'tube' in part or 'bezier' in part:
                self.tube(part, g, index, part.get('tube') or bezier(*part['bezier'], part.get('n', 12)), part.get('closed', False))
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
            elif 'glowring' in part or 'glowflat' in part or 'glowdisc' in part:
                self.glows.append((part, g, index))

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
                    angle = speed * self.time
                    m = matmul(m, {'x': rot_x, 'y': rot_y, 'z': rot_z}[axis](angle))
                self.build(part['parts'], m, k if repeat > 1 else index)

    def revolve(self, part, g, index):
        profile = part['revolve']
        closed, two = part.get('closed', False), part.get('two', False)
        seg = self.segments(part.get('seg', 24))
        start, end = (math.radians(v) for v in part.get('arc', [0, 360]))
        phase = math.radians(part.get('phase', 0))
        curl = part.get('curl')
        pattern = part.get('pattern', {})
        n = len(profile)

        def at(point, angle):
            r, y = point
            if curl:
                lift, r0, r1 = curl
                f = max(0.0, (r - r0) / (r1 - r0))
                y += lift * math.sin(angle) ** 2 * f * f
            return (r * math.sin(angle), y, r * math.cos(angle))

        for i in range(n - 1 + (1 if closed else 0)):
            p, q = profile[i], profile[(i + 1) % n]
            if p[0] < 1e-6 and q[0] < 1e-6:
                continue
            dr, dy = q[0] - p[0], q[1] - p[1]
            tp, tq = i / max(1, n - 1), ((i + 1) % n) / max(1, n - 1)
            for j in range(seg):
                a0 = phase + start + (end - start) * j / seg
                a1 = phase + start + (end - start) * (j + 1) / seg
                am = (a0 + a1) / 2
                pts = [at(p, a0), at(p, a1), at(q, a1), at(q, a0)]
                hint = (dy * math.sin(am), -dr, dy * math.cos(am))
                odd = False
                if 'stripes' in pattern:
                    odd = int(math.floor((j + 0.5) / seg * pattern['stripes'] + pattern.get('twist', 0) * (p[1] + q[1]) / 2)) % 2 == 1
                self.emit(g, pts, [tp, tp, tq, tq], hint, part, index, odd, two)

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

        def ring(i, k):
            a = 2 * math.pi * k / sides
            nn, bb = frames[i]
            return add(points[i], tuple(radii[i] * (nn[j] * math.cos(a) + bb[j] * math.sin(a)) for j in range(3)))

        for i in range(n if closed else n - 1):
            nxt = (i + 1) % n
            center = tuple((points[i][j] + points[nxt][j]) / 2 for j in range(3))
            for k in range(sides):
                pts = [ring(i, k), ring(i, k + 1), ring(nxt, k + 1), ring(nxt, k)]
                mid = tuple(sum(p[j] for p in pts) / 4 for j in range(3))
                odd = False
                if 'ridges' in pattern:
                    odd = i % pattern['ridges'] == pattern['ridges'] - 1
                elif 'stripes' in pattern:
                    odd = int((k + 0.5) / sides * pattern['stripes']) % 2 == 1
                self.emit(g, pts, [params[i], params[i], params[nxt], params[nxt]], sub(mid, center), part, index, odd)
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

        def at(a, b):
            phi = math.pi * a / lat - math.pi / 2
            theta = 2 * math.pi * b / lon
            return (c[0] + r[0] * math.cos(phi) * math.sin(theta), c[1] + r[1] * math.sin(phi), c[2] + r[2] * math.cos(phi) * math.cos(theta))

        for a in range(lat):
            for b in range(lon):
                pts = [at(a, b), at(a, b + 1), at(a + 1, b + 1), at(a + 1, b)]
                mid = tuple(sum(p[j] for p in pts) / 4 for j in range(3))
                self.emit(g, pts, [a / lat, a / lat, (a + 1) / lat, (a + 1) / lat], sub(mid, c), part, index)

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
        pts3 = part['poly']
        n = len(pts3)
        c = tuple(sum(p[j] for p in pts3) / n for j in range(3))
        normal = newell(pts3)
        ys = [p[1] for p in pts3]
        lo, hi = min(ys), max(ys)

        def t(y):
            return (y - lo) / (hi - lo) if hi > lo else 0.5

        for k in range(n):
            a, b = pts3[k], pts3[(k + 1) % n]
            self.emit(g, [c, a, b, b], [t(c[1]), t(a[1]), t(b[1]), t(b[1])], normal, part, index, False, part.get('two', True))


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

def preview(path, data, main=0xFF6A2B, second=0xB45CFF):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection

    views = [(18, -58), (8, 32)]
    count = len(data['hats'])
    fig = plt.figure(figsize=(len(views) * 2.6 * 2, math.ceil(count / 2) * 2.6), facecolor='#23262d')
    for index, hat in enumerate(data['hats']):
        builder = Builder(main, second, time=0.7)
        builder.build(hat['parts'])
        for v, (elev, azim) in enumerate(views):
            ax = fig.add_subplot(math.ceil(count / 2), len(views) * 2, index * len(views) + v + 1, projection='3d')
            ax.set_facecolor('#23262d')
            e, a = math.radians(elev), math.radians(azim)
            # matplotlib: x right, y depth, z up. Map hat (x, y, z) -> (x, z, y); camera direction for culling.
            cam = (math.cos(e) * math.cos(a), math.cos(e) * math.sin(a), math.sin(e))
            polys, colors = [], []
            # head: 0.5 block cube below the origin, face towards +Z (hat) = +y (plot)
            head = [
                [(-0.25, -0.25, 0), (0.25, -0.25, 0), (0.25, 0.25, 0), (-0.25, 0.25, 0)],
                [(-0.25, 0.25, -0.5), (0.25, 0.25, -0.5), (0.25, 0.25, 0), (-0.25, 0.25, 0)],
                [(0.25, -0.25, -0.5), (0.25, 0.25, -0.5), (0.25, 0.25, 0), (0.25, -0.25, 0)],
                [(-0.25, -0.25, -0.5), (-0.25, 0.25, -0.5), (-0.25, 0.25, 0), (-0.25, -0.25, 0)],
                [(-0.25, -0.25, -0.5), (0.25, -0.25, -0.5), (0.25, -0.25, 0), (-0.25, -0.25, 0)],
            ]
            for face, shade in zip(head, (0.95, 0.8, 0.7, 0.7, 0.6)):
                polys.append(face)
                colors.append((0.78 * shade, 0.6 * shade, 0.47 * shade, 1))
            for pts, cols, n in builder.quads:
                plot_n = (n[0], n[2], n[1])
                if dot(plot_n, cam) < 0:
                    continue
                polys.append([(p[0], p[2], p[1]) for p in pts])
                avg = [sum(c[i] for c in cols) / 4 / 255 for i in range(3)]
                colors.append((*avg, cols[0][3]))
            for part, g, idx in builder.glows:
                color = [v / 255 for v in builder.paint(part['paint'], 0.5, 0, idx)]
                alpha = part.get('alpha', 0.3)
                if 'glowring' in part or 'glowflat' in part:
                    radius = part.get('glowring', part.get('glowflat'))[0]
                    y = part.get('y', 0.0)
                    ring = [apply(g, (radius * math.sin(t / 24 * 2 * math.pi), y, radius * math.cos(t / 24 * 2 * math.pi))) for t in range(24)]
                    polys.append([(p[0], p[2], p[1]) for p in ring])
                    colors.append((*color, alpha * 0.5))
            coll = Poly3DCollection(polys, facecolors=colors, edgecolors='none')
            ax.add_collection3d(coll)
            ax.set_xlim(-0.45, 0.45)
            ax.set_ylim(-0.45, 0.45)
            ax.set_zlim(-0.3, 0.6)
            ax.set_box_aspect((1, 1, 1))
            ax.view_init(elev=elev, azim=azim)
            ax.set_axis_off()
            if v == 0:
                ax.set_title(f"{index + 1}. {hat['name']}  ({len(builder.quads)} q)", color='white', fontsize=9)
    plt.subplots_adjust(left=0, right=1, top=0.97, bottom=0, wspace=0, hspace=0.12)
    fig.savefig(path, dpi=70, facecolor=fig.get_facecolor())


def main():
    data = {'version': 1, 'hats': hats()}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    total = 0
    for hat in data['hats']:
        builder = Builder(0xFF6A2B, 0xB45CFF)
        builder.build(hat['parts'])
        total += len(builder.quads)
        print(f"{hat['name']:<10} {len(builder.quads):5d} quads")
    print('hats', len(data['hats']), 'total quads', total, '->', OUT.relative_to(ROOT))
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1], data)


if __name__ == '__main__':
    main()
