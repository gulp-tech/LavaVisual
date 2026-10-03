#!/usr/bin/env python3
"""Checks the full skins (costumes) of LavaVisual: names in step with the mod, and the body really covered.

Two things break a costume quietly, and neither of them is caught by a compiler:

  * the mod names the skins itself (Hats.COSTUME_NAMES) while the geometry comes from tools/make_hats.py, so the
    editor can show a name or a hint that belongs to a skin that was replaced long ago;
  * a skin is only cloth laid over the player's own boxes. If a ring of cloth sits around the wrong axis, or its
    radius is a hair too small, the bare arm or leg pokes through the dress in game and nothing fails in CI.

The second check samples the outside of the parts a skin promises to hide (its own 'cover' list in
tools/make_hats.py, bands of bone-space heights) and shoots a ray from every sample, straight away from the body. A
sample whose ray leaves the skin without meeting cloth is a place where the player shows through, and it is reported
with its bone-space height. A sleeve standing off the arm is not a fault (the ray still meets the sleeve), so only
escaped rays fail; the printout also shows how far the cloth stands off the skin, and marks samples hidden behind the
back of a quad (cloth drawn inside out would look like a hole in game).

    python3 tools/check_costumes.py [port dir (default ports/mc26.2)]
"""
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))
import make_hats as mh  # noqa: E402

PX = mh.PX
SAMPLE = 4           # rows of samples per covered band
EPS = 1e-9


def body_boxes(cover):
    """The player's own boxes, in the space of the bone each one hangs from, as (pivot, low, high).

    The pivot of a bone is the origin of its own space, so the boxes are the model's boxes with the pivot taken out
    of them. The pose of a seated skin (its 'seat' and 'arms') is not used here: cloth hangs from the same bone as
    the limb under it, so the pose turns both together and cannot change what is covered.
    """
    boxes = {
        'torso': ((0.0, 24 * PX, 0.0), (-4 * PX, -12 * PX, -2 * PX), (4 * PX, 0.0, 2 * PX)),
        'head': ((0.0, 24 * PX, 0.0), (-4 * PX, 0.0, -4 * PX), (4 * PX, 8 * PX, 4 * PX)),
        'armR': ((-5 * PX, 22 * PX, 0.0), (-3 * PX, -10 * PX, -2 * PX), (1 * PX, 2 * PX, 2 * PX)),
        'armL': ((5 * PX, 22 * PX, 0.0), (-1 * PX, -10 * PX, -2 * PX), (3 * PX, 2 * PX, 2 * PX)),
        'legR': ((-2 * PX, 12 * PX, 0.0), (-2 * PX, -12 * PX, -2 * PX), (2 * PX, 0.0, 2 * PX)),
        'legL': ((2 * PX, 12 * PX, 0.0), (-2 * PX, -12 * PX, -2 * PX), (2 * PX, 0.0, 2 * PX)),
    }
    out = {}
    for part, (low, high) in cover.items():
        pivot, lo, hi = boxes[part]
        del pivot                                     # the boxes are already in the bone's own space
        # A band is written in the same units as the boxes, blocks, and is cut to the limb so that the ends of a
        # band are only tested as faces of the body where the band really runs out there.
        band_low, band_high = max(low, lo[1]), min(high, hi[1])
        samples = []
        for step in range(SAMPLE + 1):
            y = band_low + (band_high - band_low) * step / SAMPLE
            for x, normal in ((lo[0], (-1, 0, 0)), (hi[0], (1, 0, 0))):
                for z in (lo[2], (lo[2] + hi[2]) / 2, hi[2]):
                    samples.append(((x, y, z), normal))
            for z, normal in ((lo[2], (0, 0, -1)), (hi[2], (0, 0, 1))):
                for x in (lo[0], (lo[0] + hi[0]) / 2, hi[0]):
                    samples.append(((x, y, z), normal))
            for normal, edge in (((0, 1, 0), hi[1]), ((0, -1, 0), lo[1])):
                if abs(y - edge) > EPS:
                    continue                          # a band ending mid-limb has no face of the body there
                for x in (lo[0], (lo[0] + hi[0]) / 2, hi[0]):
                    for z in (lo[2], (lo[2] + hi[2]) / 2, hi[2]):
                        samples.append(((x, y, z), normal))
        out[part] = (lo, hi, samples)
    return out


def ray_quad(origin, direction, quad):
    """How far along the ray a quad lies, or None. Two triangles, so the ray can meet the quad from either side."""
    best = None
    for a, b, c in ((0, 1, 2), (0, 2, 3)):
        t = ray_triangle(origin, direction, quad[a], quad[b], quad[c])
        if t is not None and (best is None or t < best):
            best = t
    return best


def ray_triangle(o, d, a, b, c):
    """Moeller-Trumbore, both sides of the triangle."""
    e1, e2 = mh.sub(b, a), mh.sub(c, a)
    p = cross(d, e2)
    det = mh.dot(e1, p)
    if abs(det) < 1e-12:
        return None
    inv = 1.0 / det
    s = mh.sub(o, a)
    u = mh.dot(s, p) * inv
    if u < -EPS or u > 1 + EPS:
        return None
    q = cross(s, e1)
    v = mh.dot(d, q) * inv
    if v < -EPS or u + v > 1 + EPS:
        return None
    t = mh.dot(e2, q) * inv
    return t if t > -EPS else None


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def quad_normal(quad):
    n = cross(mh.sub(quad[1], quad[0]), mh.sub(quad[2], quad[0]))
    length = math.sqrt(mh.dot(n, n))
    return tuple(v / length for v in n) if length else (0.0, 0.0, 0.0)


def inside(point, lo, hi):
    """True when a point is inside the body box, with a hair of tolerance so that cloth lying on the skin is out."""
    return all(lo[i] + EPS < point[i] < hi[i] - EPS for i in range(3))


def names_from_java(port):
    source = (ROOT / port / 'src/main/java/tech/gulp/lavavisual/effects/Hats.java').read_text(encoding='utf-8')
    names = re.search(r'COSTUME_NAMES\s*=\s*\{([^}]*)\}', source)
    hints = re.search(r'COSTUME_HINTS\s*=\s*\{((?:[^}]|\\}(?!;))*?)\};', source)
    parse = lambda block: re.findall(r'"([^"]*)"', block)
    return parse(names.group(1)), parse(hints.group(1))


def main():
    port = sys.argv[1] if len(sys.argv) > 1 else 'ports/mc26.2'
    data = json.loads((ROOT / port / 'src/main/resources/assets/lavavisual/hats.json').read_text(encoding='utf-8'))
    costumes = data['costumes']
    java_names, java_hints = names_from_java(port)
    problems = []

    if java_names != [c['name'] for c in costumes]:
        problems.append(f"Hats.COSTUME_NAMES is {java_names}, but hats.json has {[c['name'] for c in costumes]}")
    hints = [c.get('hint', '') for c in costumes]
    if java_hints != hints:
        problems.append(f"Hats.COSTUME_HINTS is {java_hints}, but hats.json has {hints}")

    rows = []
    for costume in costumes:
        # Cloth is measured per bone: the sleeve of armR must cover the arm, not the skirt of the body group that
        # happens to pass nearby, so each group is built on its own, in the space of its own bone.
        groups = {}
        for part in costume['parts']:
            bone = (part.get('group') or {}).get('bone', 'body')
            builder = mh.Builder(costume.get('tint', 0xFF6A2B), 0xB45CFF, time=0.9, bones={bone: mh.identity()})
            builder.build([part])
            groups.setdefault(bone, []).extend(cam for cam, _, _ in builder.quads)
        # The chest of the player is drawn by the 'body' bone; the other names of the body are the mod's own bones.
        bone_of = {'torso': 'body'}
        # Only the parts the skin promises to hide are measured: the face, the bare hands of the maid and the rider's
        # own legs on the footplates are meant to be seen.
        for part in sorted(costume.get('cover') or {}):
            quads = groups.get(bone_of.get(part, part), [])
            if not quads:
                problems.append(f"{costume['name']}: nothing is drawn on the {part}, but the skin promises to cover it")
                continue
            lo, hi, samples = body_boxes({part: costume['cover'][part]})[part]
            bare, standoff, inside_out = [], 0.0, 0
            for point, normal in samples:
                hit, t = None, None
                for quad in quads:
                    where = ray_quad(point, normal, quad)
                    if where is None:
                        continue
                    at = mh.add(point, tuple(where * v for v in normal))
                    if inside(at, lo, hi):
                        continue                      # cloth buried under the skin hides nothing
                    if t is None or where < t:
                        hit, t = quad, where
                if hit is None:
                    bare.append(point)
                    continue
                standoff = max(standoff, t / PX)
                if mh.dot(quad_normal(hit), normal) < 0:      # cloth whose visible face looks at the skin
                    inside_out += 1
            rows.append((costume['name'], part, len(samples), len(bare), standoff, inside_out))
            for point in bare[:4]:
                problems.append(f"{costume['name']}: the {part} shows through at bone height "
                                f"{point[1] / PX:+.2f} px, {tuple(round(v / PX, 1) for v in point)} px across the bone")

    width = max([len(f"{r[0]} {r[1]}") for r in rows] or [1])
    for name, part, total, bare, standoff, inside_out in rows:
        note = f"{bare} of {total} rays escape" if bare else f"cloth stands {standoff:4.2f} px off"
        if inside_out:
            note += f", {inside_out} facing the wrong way"
        print(f"{'FAIL' if bare else 'ok  '} {name + ' ' + part:<{width}}  {note}")
    print("rays shot from the player's own body, outwards: a ray that meets no cloth is bare skin")
    for problem in problems:
        print('COSTUME PROBLEM:', problem)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
