#!/usr/bin/env python3
"""
Builds the ready-made Bedrock skin pack: LavaVisual accessories (glasses, headphones, scarf) on a plain character,
in several colours, classic and slim. Import it in Minecraft Bedrock and pick a skin — no app, no server needed.

    python3 bedrock/tools/make_mcpack.py [out.mcpack]
"""
import io
import json
import os
import sys
import uuid
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import make_bedrock  # noqa: E402  (the geometry and the palette live there)

NAMESPACE = uuid.UUID("7f9b4a2c-1d3e-4f60-9a18-5c2b6e8d0a41")
FONT = os.path.join(HERE, "..", "..", "ports", "mc26.2", "src", "main", "resources", "assets", "lavavisual", "font", "inter-semibold.ttf")

# name, colour of the accessories, mask (1 glasses, 2 headphones, 4 scarf), rainbow scarf
LOOKS = [
    ("Оранжевый", 0xFF6A2B, 7, False),
    ("Синий", 0x2B8BFF, 7, False),
    ("Зелёный", 0x35D07F, 7, False),
    ("Розовый", 0xFF4FA3, 7, False),
    ("Фиолетовый", 0x9B5CFF, 7, False),
    ("Радужный шарф", 0xFF6A2B, 7, True),
    ("Только очки", 0xFF6A2B, 1, False),
    ("Только наушники", 0xFF6A2B, 2, False),
    ("Только шарф", 0xFF6A2B, 4, False),
]
ARMS = [("Классик", False), ("Тонкие руки", True)]


def base_skin(accent):
    """A plain character: dark hoodie in the accessory colour, jeans, neutral face. Drawn here, nothing copied."""
    import numpy as np

    img = np.zeros((64, 64, 4), dtype=np.uint8)

    def fill(x0, y0, x1, y1, rgb, a=255):
        img[y0:y1, x0:x1, :3] = rgb
        img[y0:y1, x0:x1, 3] = a

    skin_tone = (222, 178, 142)
    hair = (48, 36, 32)
    hoodie = tuple(max(24, int(c * 0.42)) for c in ((accent >> 16 & 255), (accent >> 8 & 255), (accent & 255)))
    hoodie_dark = tuple(int(c * 0.72) for c in hoodie)
    jeans = (52, 58, 84)
    shoes = (34, 34, 40)

    fill(0, 8, 32, 16, skin_tone)          # head sides
    fill(8, 0, 24, 8, hair)                # head top and bottom
    fill(0, 8, 32, 10, hair)               # hair round the head
    fill(24, 8, 32, 16, hair)              # back of the head
    fill(9, 12, 11, 13, (250, 250, 252)); fill(13, 12, 15, 13, (250, 250, 252))   # eyes
    fill(10, 12, 11, 13, (58, 86, 168)); fill(13, 12, 14, 13, (58, 86, 168))
    fill(11, 14, 13, 15, (176, 118, 96))   # mouth

    fill(16, 16, 40, 32, hoodie)           # body
    fill(20, 20, 28, 32, hoodie_dark)      # front panel
    fill(20, 20, 28, 21, ((accent >> 16 & 255), (accent >> 8 & 255), (accent & 255)))   # a stripe in the accent colour
    fill(40, 16, 56, 32, hoodie)           # right arm: sleeve
    fill(44, 28, 48, 32, skin_tone); fill(40, 28, 44, 32, skin_tone)              # hands
    fill(32, 48, 48, 64, hoodie)           # left arm
    fill(36, 60, 40, 64, skin_tone); fill(32, 60, 36, 64, skin_tone)
    fill(0, 16, 16, 32, jeans)             # right leg
    fill(16, 48, 32, 64, jeans)            # left leg
    fill(0, 28, 16, 32, shoes); fill(16, 60, 32, 64, shoes)

    for i, rgb in enumerate(make_bedrock.palette(accent)):
        x, y = (i % 4) * 2, (i // 4) * 2
        fill(x, y, x + 2, y + 2, rgb)
    return img


def png(image):
    from PIL import Image

    out = io.BytesIO()
    Image.fromarray(image, "RGBA").save(out, "PNG")
    return out.getvalue()


def icon():
    from PIL import Image, ImageDraw, ImageFont

    size, scale = 128, 6
    big = size * scale
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pad = big * 0.05
    draw.rounded_rectangle([pad, pad, big - pad, big - pad], radius=big * 0.22, fill=(18, 19, 26, 255),
                           outline=(255, 122, 47, 255), width=int(big * 0.05))
    font = ImageFont.truetype(FONT, int(big * 0.42))
    box = draw.textbbox((0, 0), "LV", font=font)
    draw.text(((big - box[2] - box[0]) / 2, (big - box[3] - box[1]) / 2), "LV", font=font, fill=(242, 244, 248, 255))
    out = io.BytesIO()
    image.resize((size, size), Image.LANCZOS).save(out, "PNG")
    return out.getvalue()


def build(path):
    geometries, skins, texts, textures = [], [], [], {}
    seen = set()
    for arm_name, slim in ARMS:
        for look_name, accent, mask, rainbow in LOOKS:
            identifier = make_bedrock.identifier(mask, slim, rainbow)
            if identifier not in seen:
                seen.add(identifier)
                geometries.extend(make_bedrock.geometry(mask, slim, rainbow)["minecraft:geometry"])
            texture = "skin_%06x_%s.png" % (accent, "slim" if slim else "wide")
            if texture not in textures:
                textures[texture] = png(base_skin(accent))
            key = "lv_%d_%s_%s" % (mask, "r" if rainbow else "n", "slim" if slim else "wide")
            key += "_%06x" % accent
            skins.append({
                "localization_name": key,
                "geometry": identifier,
                "texture": texture,
                "type": "free",
            })
            texts.append("skin.LavaVisual.%s=%s · %s" % (key, look_name, arm_name))

    manifest = {
        "format_version": 1,
        "header": {
            "name": "LavaVisual",
            "description": "Очки, наушники и шарф LavaVisual",
            "uuid": str(uuid.uuid5(NAMESPACE, "header")),
            "version": [1, 0, 0],
        },
        "modules": [{
            "type": "skin_pack",
            "uuid": str(uuid.uuid5(NAMESPACE, "module")),
            "version": [1, 0, 0],
        }],
    }
    skins_json = {"serialize_name": "LavaVisual", "localization_name": "LavaVisual", "skins": skins}
    geometry_json = {"format_version": "1.12.0", "minecraft:geometry": geometries}
    lang = "skinpack.LavaVisual=LavaVisual\n" + "\n".join(texts) + "\n"

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as pack:
        def put(name, data):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            pack.writestr(info, data)

        put("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        put("skins.json", json.dumps(skins_json, ensure_ascii=False, indent=2))
        put("geometry.json", json.dumps(geometry_json))
        put("pack_icon.png", icon())
        put("texts/languages.json", json.dumps(["en_US", "ru_RU"]))
        put("texts/en_US.lang", lang)
        put("texts/ru_RU.lang", lang)
        for name, data in sorted(textures.items()):
            put(name, data)
    return len(skins), len(geometries), len(textures)


def check(path):
    """The pack is a zip Minecraft can read: every skin points at a texture and a geometry that are really inside."""
    with zipfile.ZipFile(path) as pack:
        names = set(pack.namelist())
        for required in ("manifest.json", "skins.json", "geometry.json", "pack_icon.png"):
            assert required in names, "missing " + required
        skins = json.loads(pack.read("skins.json"))
        geometry = json.loads(pack.read("geometry.json"))
        ids = {entry["description"]["identifier"] for entry in geometry["minecraft:geometry"]}
        assert skins["skins"], "no skins"
        for skin in skins["skins"]:
            assert skin["texture"] in names, "no texture " + skin["texture"]
            assert skin["geometry"] in ids, "no geometry " + skin["geometry"]
            data = pack.read(skin["texture"])
            assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png: " + skin["texture"]
            width = int.from_bytes(data[16:20], "big")
            height = int.from_bytes(data[20:24], "big")
            assert (width, height) == (64, 64), "%s is %dx%d" % (skin["texture"], width, height)
        manifest = json.loads(pack.read("manifest.json"))
        assert manifest["modules"][0]["type"] == "skin_pack"
    return len(skins["skins"])


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "artifacts", "bedrock", "LavaVisual-Skins-1.0.0.mcpack")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    skins, geometries, textures = build(path)
    checked = check(path)
    print("LavaVisual skin pack ok: %d skins, %d geometries, %d textures, %d bytes (%d checked)"
          % (skins, geometries, textures, os.path.getsize(path), checked))
    return 0


if __name__ == "__main__":
    sys.exit(main())
