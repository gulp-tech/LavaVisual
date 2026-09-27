#!/usr/bin/env python3
"""Bedrock geometry of the LavaVisual accessories (glasses, headphones, scarf) for the Geyser extension.

Bedrock players see Java players through Geyser as skins: a texture plus a geometry of cubes. The extension sends the
skin of a LavaVisual player with this geometry: the standard player bones and one extra bone per accessory. Every
accessory cube takes its colour from one 2x2 texel cell of a palette that the extension paints into the top-left 8x8
corner of the skin, a corner no player bone uses, so the player's own skin stays untouched and one geometry serves
every colour.

    python3 bedrock/tools/make_bedrock.py            # writes the 14 geometries (7 combinations, wide and slim arms)
    python3 bedrock/tools/make_bedrock.py --preview out.png   # also draws them on a test skin

Units are pixels of the player model. The design is written in "cosmetic space" (y up from the neck, +z the face, +x
the player's left) and converted to Bedrock geometry space (the neck at y 24, the face towards -z). The check at the
end keeps every cube outside the skin's outer layers: the hat layer (+0.5 px), the jacket (+0.25 px).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'geyser-extension/src/main/resources/lavavisual/geometry'
NAMES = {1: 'glasses', 2: 'headphones', 4: 'scarf'}
# Palette cells (index -> what it holds); the extension paints the same indices (Accessories.java).
PALETTE = ['main', 'light', 'lens', 'white', 'cushion', 'band', 'hue0', 'hue1', 'hue2', 'hue3', 'hue4', 'hue5', 'dark']
CELL = {name: i for i, name in enumerate(PALETTE)}
GAP = 0.1  # px between an accessory and the outer skin layer


def face_uv(paint):
    i = CELL[paint]
    u, v = (i % 4) * 2 + 0.5, (i // 4) * 2 + 0.5
    return {side: {'uv': [u, v], 'uv_size': [1, 1]} for side in ('north', 'south', 'east', 'west', 'up', 'down')}


def cube(x0, x1, y0, y1, z0, z1, paint):
    """A cube in cosmetic space (y up from the neck, +z the face), as a Bedrock cube."""
    return {'origin': [r(x0), r(24 + y0), r(-z1)], 'size': [r(x1 - x0), r(y1 - y0), r(z1 - z0)], 'uv': face_uv(paint),
            '_box': (x0, x1, y0, y1, z0, z1)}


def r(v):
    return round(v, 3) + 0.0


def mirrored(x0, x1, *rest):
    """The cube and its mirror image across the middle of the body."""
    return [cube(x0, x1, *rest), cube(-x1, -x0, *rest)]


def glasses():
    front0, front1 = 4.5 + GAP + 0.02, 4.5 + GAP + 0.32   # frame depth, in front of the hat layer
    c = []
    for s in (1, -1):
        lo, hi = sorted((s * 0.8, s * 3.1))
        c.append(cube(lo, hi, 2.75, 4.35, 4.5 + GAP, front0 + 0.1, 'lens'))                  # tinted lens
        hl0, hl1 = sorted((s * 2.3, s * 2.75))
        c.append(cube(hl0, hl1, 3.75, 4.15, front0 + 0.1, front0 + 0.14, 'white'))           # glint
        for x0, x1, y0, y1 in ((0.5, 3.4, 4.35, 4.65), (0.5, 3.4, 2.45, 2.75), (0.5, 0.8, 2.75, 4.35), (3.1, 3.4, 2.75, 4.35)):
            a, b = sorted((s * x0, s * x1))
            c.append(cube(a, b, y0, y1, front0, front1, 'main'))                              # frame
        a, b = sorted((s * 3.4, s * (4.5 + GAP + 0.3)))
        c.append(cube(a, b, 3.95, 4.25, front0, front1, 'main'))                              # hinge round the corner
        a, b = sorted((s * (4.5 + GAP), s * (4.5 + GAP + 0.3)))
        c.append(cube(a, b, 3.95, 4.25, 0.8, front1, 'main'))                                 # temple to the ear
    c.append(cube(-0.5, 0.5, 3.9, 4.2, front0, front1, 'main'))                               # bridge
    return c


def headphones():
    top = 8.5 + GAP
    side = 4.5 + GAP
    c = [cube(-side - 0.7, side + 0.7, top, top + 0.7, -0.55, 0.55, 'band'),                  # band over the head
         cube(-3.5, 3.5, top + 0.7, top + 0.95, -0.3, 0.3, 'main')]                          # coloured strip on it
    c += mirrored(side, side + 0.7, 5.3, top + 0.7, -0.55, 0.55, 'band')                      # band down the sides
    c += mirrored(side + 0.3, side + 1.3, 2.3, 5.5, -1.6, 1.6, 'main')                        # cups
    c += mirrored(side, side + 0.3, 2.5, 5.3, -1.4, 1.4, 'cushion')                           # cushions
    c += mirrored(side + 1.3, side + 1.45, 3.0, 4.8, -0.9, 0.9, 'light')                      # glowing rim
    return c


def scarf(rainbow_cells=('main', 'light')):
    """Round the top of the torso just under the hat layer, 0.1 px off the jacket; the sides run inside the arms
    (there is no neck between them), the back is thinner."""
    y0, y1 = -2.15, -0.5 - GAP              # under the hat layer's lower edge
    zf0, zf1 = 2.25 + GAP, 2.25 + GAP + 1.6
    zb0, zb1 = -2.25 - GAP - 1.0, -2.25 - GAP
    c = []
    edges = [-4.9, -2.94, -0.98, 0.98, 2.94, 4.9]
    for i in range(5):
        c.append(cube(edges[i], edges[i + 1], y0, y1, zf0, zf1, ('main', 'light')[i % 2]))    # front, striped
        c.append(cube(edges[i], edges[i + 1], y0 + 0.25, y1 - 0.25, zb0, zb1, ('main', 'light')[i % 2]))  # back
    c += mirrored(4.35, 5.55, y0, y1, zb0, zf1, 'main')                                       # sides, under the arms
    tail = [(-2.6, -0.6), (-4.4, -2.6), (-6.2, -4.4), (-8.2, -6.2)]
    for i, (a, b) in enumerate(tail):
        c.append(cube(0.9, 2.5, a, b, zf0 + 0.2, zf1 - 0.3, ('light', 'main')[i % 2]))       # tail, striped
    c.append(cube(0.75, 2.65, -2.6, y1, zf0 + 0.9, zf1 + 0.35, 'dark'))                       # knot
    for x in (1.0, 1.4, 1.8, 2.2):
        c.append(cube(x, x + 0.25, -8.9, -8.2, zf0 + 0.8, zf0 + 1.05, 'light'))              # fringe
    return c


def rainbow(cubes):
    """Rainbow scarf: the stripes take the six hues instead of two tones."""
    k = 0
    out = []
    for cb in cubes:
        paint = next(name for name, i in CELL.items() if face_uv(name) == cb['uv'])
        if paint in ('main', 'light'):
            x0, x1, y0, y1, z0, z1 = cb['_box']
            cb = cube(x0, x1, y0, y1, z0, z1, f'hue{k % 6}')
            k += 1
        out.append(cb)
    return out


def base_bones(slim):
    """The standard player bones and cubes (the layout Geyser sends for Java players)."""
    arm_w, arm_y, arm_top = (3, 11.5, 21.5) if slim else (4, 12.0, 22.0)
    inner = 4.0
    bones = [
        {'name': 'root', 'pivot': [0.0, 0.0, 0.0]},
        {'name': 'waist', 'parent': 'root', 'pivot': [0.0, 12.0, 0.0]},
        {'name': 'body', 'parent': 'waist', 'pivot': [0.0, 24.0, 0.0], 'cubes': [{'origin': [-4.0, 12.0, -2.0], 'size': [8, 12, 4], 'uv': [16, 16]}]},
        {'name': 'jacket', 'parent': 'body', 'pivot': [0.0, 24.0, 0.0], 'cubes': [{'origin': [-4.0, 12.0, -2.0], 'size': [8, 12, 4], 'uv': [16, 32], 'inflate': 0.25}]},
        {'name': 'cape', 'parent': 'body', 'pivot': [0.0, 24.0, 3.0]},
        {'name': 'head', 'parent': 'body', 'pivot': [0.0, 24.0, 0.0], 'cubes': [{'origin': [-4.0, 24.0, -4.0], 'size': [8, 8, 8], 'uv': [0, 0]}]},
        {'name': 'hat', 'parent': 'head', 'pivot': [0.0, 24.0, 0.0], 'cubes': [{'origin': [-4.0, 24.0, -4.0], 'size': [8, 8, 8], 'uv': [32, 0], 'inflate': 0.5}]},
        {'name': 'leftArm', 'parent': 'body', 'pivot': [5.0, arm_top, 0.0], 'cubes': [{'origin': [inner, arm_y, -2.0], 'size': [arm_w, 12, 4], 'uv': [32, 48]}]},
        {'name': 'leftSleeve', 'parent': 'leftArm', 'pivot': [5.0, arm_top, 0.0], 'cubes': [{'origin': [inner, arm_y, -2.0], 'size': [arm_w, 12, 4], 'uv': [48, 48], 'inflate': 0.25}]},
        {'name': 'leftItem', 'parent': 'leftArm', 'pivot': [6.0, arm_top - 7, 1.0]},
        {'name': 'rightArm', 'parent': 'body', 'pivot': [-5.0, arm_top, 0.0], 'cubes': [{'origin': [-inner - arm_w, arm_y, -2.0], 'size': [arm_w, 12, 4], 'uv': [40, 16]}]},
        {'name': 'rightSleeve', 'parent': 'rightArm', 'pivot': [-5.0, arm_top, 0.0], 'cubes': [{'origin': [-inner - arm_w, arm_y, -2.0], 'size': [arm_w, 12, 4], 'uv': [40, 32], 'inflate': 0.25}]},
        {'name': 'rightItem', 'parent': 'rightArm', 'pivot': [-6.0, arm_top - 7, 1.0], 'locators': {'lead_hold': [-6.0, arm_top - 7, 1.0]}},
        {'name': 'leftLeg', 'parent': 'root', 'pivot': [1.9, 12.0, 0.0], 'cubes': [{'origin': [-0.1, 0.0, -2.0], 'size': [4, 12, 4], 'uv': [16, 48]}]},
        {'name': 'leftPants', 'parent': 'leftLeg', 'pivot': [1.9, 12.0, 0.0], 'cubes': [{'origin': [-0.1, 0.0, -2.0], 'size': [4, 12, 4], 'uv': [0, 48], 'inflate': 0.25}]},
        {'name': 'rightLeg', 'parent': 'root', 'pivot': [-1.9, 12.0, 0.0], 'cubes': [{'origin': [-3.9, 0.0, -2.0], 'size': [4, 12, 4], 'uv': [0, 16]}]},
        {'name': 'rightPants', 'parent': 'rightLeg', 'pivot': [-1.9, 12.0, 0.0], 'cubes': [{'origin': [-3.9, 0.0, -2.0], 'size': [4, 12, 4], 'uv': [0, 32], 'inflate': 0.25}]},
    ]
    return bones


def identifier(mask, slim, rainbow_scarf):
    """geometry.lavavisual.a<mask>[r][s]: accessory mask (1 glasses, 2 headphones, 4 scarf), r = rainbow scarf,
    s = slim arms. Accessories.java builds the same names."""
    return f"geometry.lavavisual.a{mask}{'r' if rainbow_scarf else ''}{'s' if slim else ''}"


def geometry(mask, slim, rainbow_scarf=False):
    bones = base_bones(slim)
    parts = {1: ('lv_glasses', 'head', glasses()), 2: ('lv_headphones', 'head', headphones()),
             4: ('lv_scarf', 'body', rainbow(scarf()) if rainbow_scarf else scarf())}
    for bit, (name, parent, cubes) in parts.items():
        if mask & bit:
            bones.append({'name': name, 'parent': parent, 'pivot': [0.0, 24.0, 0.0],
                          'cubes': [{k: v for k, v in cb.items() if k != '_box'} for cb in cubes]})
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier(mask, slim, rainbow_scarf), 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 2, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


def check():
    """Every cube stays outside the outer skin layers: head accessories off the hat layer, the scarf off the jacket
    and under the hat layer (its sides are inside the arms, which hide them)."""
    hat = (-4.5, 4.5, -0.5, 8.5, -4.5, 4.5)
    jacket = (-4.25, 4.25, -12.25, 0.25, -2.25, 2.25)
    problems = []

    def inside(box, b):
        return all(min(b[2 * k + 1], box[2 * k + 1]) - max(b[2 * k], box[2 * k]) > 1e-6 for k in range(3))
    for name, cubes, boxes in (('glasses', glasses(), [hat]), ('headphones', headphones(), [hat]), ('scarf', scarf(), [hat, jacket])):
        for cb in cubes:
            for box in boxes:
                if inside(box, cb['_box']):
                    problems.append(f"{name} cube {cb['_box']} is inside {box}")
    return problems


def main():
    problems = check()
    if problems:
        print('\n'.join(problems))
        sys.exit(1)
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.json'):
        old.unlink()
    count = 0
    for mask in range(1, 8):
        for slim in (False, True):
            for rain in ((False, True) if mask & 4 else (False,)):
                data = geometry(mask, slim, rain)
                name = identifier(mask, slim, rain).removeprefix('geometry.lavavisual.')
                (OUT / f'{name}.json').write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf-8')
                count += 1
    print(f'LavaVisual Bedrock geometry ok: {count} geometries, every accessory cube outside the hat layer and jacket')
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1])


# ------------------------------------------------------------------------------------------------ preview

def test_skin():
    """A plain, symmetric test skin (face, hair, shirt, trousers) with the palette painted like the extension does."""
    import numpy as np
    img = np.zeros((64, 64, 4), dtype=np.uint8)

    def fill(x0, y0, x1, y1, rgb, a=255):
        img[y0:y1, x0:x1, :3] = rgb
        img[y0:y1, x0:x1, 3] = a
    fill(0, 8, 32, 16, (196, 146, 110))      # head sides
    fill(8, 0, 24, 8, (70, 44, 28))           # head top and bottom
    fill(0, 8, 32, 10, (70, 44, 28))          # hair round the head
    fill(9, 12, 11, 13, (250, 250, 250)); fill(13, 12, 15, 13, (250, 250, 250))
    fill(10, 12, 11, 13, (60, 90, 170)); fill(13, 12, 14, 13, (60, 90, 170))
    fill(11, 14, 13, 15, (150, 92, 70))
    fill(16, 16, 40, 32, (70, 120, 90))       # body
    fill(40, 16, 56, 32, (196, 146, 110)); fill(32, 48, 48, 64, (196, 146, 110))   # arms
    fill(40, 16, 56, 20, (70, 120, 90)); fill(32, 48, 48, 52, (70, 120, 90))       # sleeves' tops
    fill(0, 16, 16, 32, (50, 60, 110)); fill(16, 48, 32, 64, (50, 60, 110))         # legs
    for i, rgb in enumerate(palette(0xFF6A2B)):
        x, y = (i % 4) * 2, (i // 4) * 2
        fill(x, y, x + 2, y + 2, rgb)
    return img


def palette(main):
    """The colours Accessories.java paints for one accessory colour (companion tone as in the mod)."""
    import colorsys
    r, g, b = (main >> 16 & 255) / 255, (main >> 8 & 255) / 255, (main & 255) / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    light = colorsys.hsv_to_rgb((h + 0.09) % 1, min(1, s * 0.92), min(1, v * 0.9 + 0.1)) if s >= 0.08 else (r, g, b)
    dark = (r * 0.62, g * 0.62, b * 0.62)
    hues = [colorsys.hsv_to_rgb(k / 6, 0.72, 1) for k in range(6)]
    cells = [(r, g, b), light, (29 / 255, 30 / 255, 36 / 255), (242 / 255, 244 / 255, 248 / 255), (22 / 255, 22 / 255, 28 / 255),
             (40 / 255, 42 / 255, 50 / 255)] + hues + [dark]
    return [tuple(int(round(c * 255)) for c in cell) for cell in cells]


def preview(path):
    """Front, three-quarter, side and back views of every accessory on the test skin."""
    from PIL import Image, ImageDraw, ImageFont
    try:
        font = ImageFont.truetype(str(ROOT.parent / 'ports/mc26.2/src/main/resources/assets/lavavisual/font/inter-semibold.ttf'), 18)
    except OSError:
        font = ImageFont.load_default()
    skin = test_skin()
    views = [0, 35, 90, 180]
    rows = [(1, 'Очки'), (2, 'Наушники'), (4, 'Шарф'), (7, 'Всё вместе')]
    cell = 280
    sheet = Image.new('RGB', (cell * len(views), cell * len(rows)), (34, 37, 44))
    draw = ImageDraw.Draw(sheet)
    for ri, (mask, label) in enumerate(rows):
        geo = geometry(mask, False)['minecraft:geometry'][0]
        quads = [q for bone in geo['bones'] for cb in bone.get('cubes', []) for q in cube_quads(cb)]
        for vi, yaw in enumerate(views):
            sheet.paste(Image.fromarray(render(quads, skin, yaw, cell)), (vi * cell, ri * cell))
        draw.text((10, ri * cell + 8), label, fill=(235, 238, 245), font=font)
    sheet.save(path)


def cube_quads(cb):
    """Faces of a Bedrock cube: corners (top-left, top-right, bottom-right, bottom-left as seen from outside), normal
    and texture rectangle (box UV or per-face UV)."""
    (x, y, z), (sx, sy, sz) = cb['origin'], cb['size']
    g = cb.get('inflate', 0.0)
    x0, y0, z0, x1, y1, z1 = x - g, y - g, z - g, x + sx + g, y + sy + g, z + sz + g
    faces = {
        'north': ([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], (0, 0, -1)),
        'south': ([(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)], (0, 0, 1)),
        'east': ([(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)], (1, 0, 0)),
        'west': ([(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)], (-1, 0, 0)),
        'up': ([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0)),
        'down': ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
    }
    uv = cb['uv']
    out = []
    for side, (pts, n) in faces.items():
        if isinstance(uv, dict):
            u, v = uv[side]['uv']
            rect = (u, v, 1, 1)
        else:
            u, v = uv
            w, h, d = sx, sy, sz
            rect = {'north': (u + d, v + d, w, h), 'south': (u + 2 * d + w, v + d, w, h), 'west': (u, v + d, d, h),
                    'east': (u + d + w, v + d, d, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}[side]
        out.append((pts, n, rect))
    return out


def render(quads, skin, yaw, size):
    """Orthographic z-buffer render with the skin texture. As in the game, the player's left (+x) shows on the
    viewer's right when the player faces the camera (yaw 0)."""
    import math
    import numpy as np
    img = np.empty((size, size, 3), dtype=np.float32)
    img[:] = (34 / 255, 37 / 255, 44 / 255)
    depth = np.full((size, size), -1e9, dtype=np.float32)
    a = math.radians(yaw)
    ca, sa = math.cos(a), math.sin(a)
    scale, cx, cy = size / 24.0, size / 2, size * 0.06
    light = np.array([0.3, 0.75, 0.6]); light /= np.linalg.norm(light)
    ys, xs = np.mgrid[0:size, 0:size]
    px, py = xs + 0.5, ys + 0.5

    def project(p):
        x, y, z = p
        rx, rz = x * ca + z * sa, -x * sa + z * ca
        return cx + rx * scale, cy + (35 - y) * scale, -rz
    for pts, (nx, ny, nz), (u0, v0, w, h) in quads:
        rnx, rnz = nx * ca + nz * sa, -nx * sa + nz * ca
        if rnz > -1e-6:
            continue  # faces the other way (the camera sits at -z of the turned model)
        shade = 0.5 + 0.5 * max(0.0, float(np.dot((rnx, ny, -rnz), light)))
        P = [project(p) for p in pts]
        (ax, ay, az), (bx, by, bz), _, (dx, dy, dz) = P
        ex, ey, fx, fy = bx - ax, by - ay, dx - ax, dy - ay
        det = ex * fy - ey * fx
        if abs(det) < 1e-9:
            continue
        x0, x1 = max(0, int(min(p[0] for p in P))), min(size, int(max(p[0] for p in P)) + 2)
        y0, y1 = max(0, int(min(p[1] for p in P))), min(size, int(max(p[1] for p in P)) + 2)
        if x0 >= x1 or y0 >= y1:
            continue
        qx, qy = px[y0:y1, x0:x1] - ax, py[y0:y1, x0:x1] - ay
        s = (qx * fy - qy * fx) / det
        t = (ex * qy - ey * qx) / det
        z = az + (bz - az) * s + (dz - az) * t
        tu = np.clip((u0 + s * w).astype(int), 0, 63)
        tv = np.clip((v0 + t * h).astype(int), 0, 63)
        texel = skin[tv, tu]
        ok = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1) & (texel[..., 3] >= 128) & (z > depth[y0:y1, x0:x1])
        depth[y0:y1, x0:x1] = np.where(ok, z, depth[y0:y1, x0:x1])
        img[y0:y1, x0:x1] = np.where(ok[..., None], texel[..., :3] / 255.0 * shade, img[y0:y1, x0:x1])
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


if __name__ == '__main__':
    main()
