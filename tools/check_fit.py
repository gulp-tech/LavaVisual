#!/usr/bin/env python3
"""Checks that the accessories, capes and wings stay outside the player model.

Every model in hats.json (written by tools/make_hats.py) is tessellated with the interpreter the previews use and
placed the way effects/WorldCosmetics.java places it. Its surface points are then tested against the boxes of the player model in
pixels: the head, torso and arms with their outer skin layers (hat layer +0.5 px, jacket and sleeves +0.25 px), and
armour (helmet and chestplate +1 px). Wings are also tested at the ends of their beat and of the physics sweep.

    python3 tools/check_fit.py          # the deepest point inside each box; exit 1 when something is inside
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_hats as mh  # noqa: E402

PX = mh.PX
# Placement, the same numbers as WorldCosmetics.java (pixels of the player model; y up, +z towards the face).
HEAD_GROW = {'bare': 1.0, 'hat': 1.13, 'helmet': 1.26}  # head accessories scaled about the centre of the head
CHEST = (1.25, 1.0, 1.36)       # the scarf over a chestplate
HELMET_DROP = 0.5               # the scarf under a helmet
CAPE_BACK, CAPE_BACK_ARMOR = 2.6, 3.45
WINGS_BACK, WINGS_BACK_ARMOR, WINGS_DOWN = 3.0, 4.0, 3.0
TOLERANCE = 0.02                # px
WING_PHYSICS_TOLERANCE = 0.25   # px: the ends of the physics sweep only last a moment (a sprint jump)


def box(x0, y0, z0, x1, y1, z1):
    return (x0, y0, z0), (x1, y1, z1)


# The arms are the wide ones, the tighter case at the shoulders.
HEAD, HAT, HELMET = box(-4, 0, -4, 4, 8, 4), box(-4.5, -0.5, -4.5, 4.5, 8.5, 4.5), box(-5, -1, -5, 5, 9, 5)
TORSO, JACKET, CHESTPLATE = box(-4, -12, -2, 4, 0, 2), box(-4.25, -12.25, -2.25, 4.25, 0.25, 2.25), box(-5, -13, -3, 5, 1, 3)
SLEEVE, PAULDRON = box(3.75, -12.25, -2.25, 8.25, 0.25, 2.25), box(3, -13, -3, 9, 1, 3)


def mirror(b):
    (x0, y0, z0), (x1, y1, z1) = b
    return (-x1, y0, z0), (-x0, y1, z1)


def points(parts, swing=None, flap=1.0, spread=0.0, lift=0.0):
    """Surface points of one model in pixels (quad corners, edge midpoints and centres). swing: the beat of the wing
    roots as one value in -1..1 per swing axis (None = at rest); spread and lift as in Hats.Look."""
    out = []
    builder = mh.Builder(0xFF6A2B, 0xB45CFF)

    def emit(g, pts, *args, **kwargs):
        q = [mh.apply(g, p) for p in pts]
        out.extend(q)
        for i in range(4):
            a, b = q[i], q[(i + 1) % 4]
            out.append(tuple((a[j] + b[j]) / 2 for j in range(3)))
        out.append(tuple(sum(p[j] for p in q) / 4 for j in range(3)))
    builder.emit = emit
    original = builder.group

    def group(part, g, index):
        spec = part['group']
        if 'swing' not in spec:
            return original(part, g, index)
        # A wing root, in the order of Hats.java: rot, then spread and lift, then the beat.
        m = mh.matmul(g, mh.translate(*spec.get('at', [0, 0, 0])))
        rx, ry, rz = (math.radians(v) for v in spec.get('rot', [0, 0, 0]))
        m = mh.matmul(m, mh.matmul(mh.rot_y(ry), mh.matmul(mh.rot_x(rx), mh.rot_z(rz))))
        m = mh.matmul(mh.matmul(m, mh.rot_y(spread)), mh.rot_z(lift))
        for k, (axis, degrees, speed, phase) in enumerate(spec['swing']):
            s = 0.0 if swing is None else swing[min(k, len(swing) - 1)]
            m = mh.matmul(m, {'x': mh.rot_x, 'y': mh.rot_y, 'z': mh.rot_z}[axis](math.radians(degrees) * flap * s))
        builder.build(part['parts'], m, index)
    builder.group = group
    builder.build(parts)
    return [(x / PX, y / PX, z / PX) for x, y, z in out]


def place(pts, sx=1.0, sy=1.0, sz=1.0, dx=0.0, dy=0.0, dz=0.0):
    return [(x * sx + dx, y * sy + dy, z * sz + dz) for x, y, z in pts]


def deepest(pts, b):
    (x0, y0, z0), (x1, y1, z1) = b
    best = 0.0
    for x, y, z in pts:
        if x0 < x < x1 and y0 < y < y1 and z0 < z < z1:
            best = max(best, min(x - x0, x1 - x, y - y0, y1 - y, z - z0, z1 - z))
    return best


def main():
    data = json.loads(mh.OUT.read_text(encoding='utf-8'))
    rows, failures = [], []

    def report(label, pts, boxes, limit=TOLERANCE):
        depths = {name: deepest(pts, b) for name, b in boxes.items()}
        bad = [name for name, d in depths.items() if d > limit]
        rows.append((label, depths, bad))
        if bad:
            failures.append(label)

    for model in data['extras']:
        raw = points(model['parts'])
        if model['attach'] == 'head':
            for case, grow in HEAD_GROW.items():
                layer = {'bare': {}, 'hat': {'hat layer': HAT}, 'helmet': {'helmet': HELMET}}[case]
                report(f"{model['name']} ({case})", place(raw, grow, grow, grow, 0, 4 * grow + 4, 0), {'head': HEAD, **layer})
        else:
            # The sides of the scarf pass under the arms (there is no neck between them), so only those may be inside.
            report(model['name'], raw, {'head': HEAD, 'hat layer': HAT, 'torso': TORSO, 'jacket': JACKET})
            report(f"{model['name']} (armour)", place(raw, *CHEST, 0, -HELMET_DROP, 0), {'helmet': HELMET, 'chestplate': CHESTPLATE})
    for model in data['capes']:
        raw = points(model['parts'])
        report(f"cape {model['name']}", place(raw, dz=-CAPE_BACK),
               {'torso': TORSO, 'jacket': JACKET, 'sleeve': SLEEVE, 'left sleeve': mirror(SLEEVE)})
        report(f"cape {model['name']} (armour)", place(raw, dz=-CAPE_BACK_ARMOR),
               {'chestplate': CHESTPLATE, 'pauldron': PAULDRON, 'left pauldron': mirror(PAULDRON)})
    for model in data['wings']:
        # At rest and at the ends of a walking beat (1.3) the wings stay clear; at the ends of the physics sweep and
        # lift they may touch the back by a fraction of a pixel. They rise behind the back of the head by design (the
        # head hides that part), so only the body is checked.
        parts = model['parts']
        beats = [points(parts)] + [points(parts, swing=s, flap=1.3) for s in ((1, 1), (1, -1), (-1, 1), (-1, -1))]
        swept = [points(parts, spread=a, lift=b)
                 for a, b in ((-0.1, 0), (0.42, 0), (0, -0.22), (0, 0.38), (-0.1, -0.22), (0.42, 0.38), (-0.1, 0.38), (0.42, -0.22))]
        for label, poses, limit in (('', beats, TOLERANCE), (' physics', swept, WING_PHYSICS_TOLERANCE)):
            raw = [p for pose in poses for p in pose]
            report(f"wings {model['name']}{label}", place(raw, dy=-WINGS_DOWN, dz=-WINGS_BACK), {'torso': TORSO, 'jacket': JACKET}, limit)
            report(f"wings {model['name']}{label} (armour)", place(raw, dy=-WINGS_DOWN, dz=-WINGS_BACK_ARMOR), {'chestplate': CHESTPLATE}, limit)

    width = max(len(r[0]) for r in rows)
    for label, depths, bad in rows:
        cells = '  '.join(f"{name} {d:4.2f}" for name, d in depths.items())
        print(f"{'FAIL' if bad else 'ok  '} {label:<{width}}  {cells}")
    print('deepest point inside each box of the player model, px (0 = outside)')
    if failures:
        print('inside the player:', ', '.join(failures))
        return 1
    print('LavaVisual fit ok: accessories, capes and wings stay outside the skin and armour')
    return 0


if __name__ == '__main__':
    sys.exit(main())
