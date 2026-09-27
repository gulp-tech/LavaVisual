#!/usr/bin/env python3
"""
Builds the Bedrock resource pack the server sends to its Bedrock players: LavaVisual-Bedrock-Pack.mcpack.

The pack replaces the player model (geometry.humanoid.custom and .customSlim) with the same model plus the LavaVisual
accessories. The accessory cubes take their colour from the palette corner of the skin, which is empty on ordinary
skins, so players without LavaVisual look exactly as before and nobody has to install anything by hand.

    python3 bedrock/tools/make_serverpack.py [out.mcpack]
"""
import io
import json
import os
import sys
import uuid
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import make_bedrock  # noqa: E402

NAMESPACE = uuid.UUID("2c1f8e64-9a07-4d13-8f52-b6d3e1a47c90")
FONT = os.path.join(HERE, "..", "..", "ports", "mc26.2", "src", "main", "resources", "assets", "lavavisual", "font", "inter-semibold.ttf")


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
    manifest = {
        "format_version": 2,
        "header": {
            "name": "LavaVisual",
            "description": "Очки, наушники и шарф игроков LavaVisual",
            "uuid": str(uuid.uuid5(NAMESPACE, "header")),
            "version": [1, 0, 0],
            "min_engine_version": [1, 20, 0],
        },
        "modules": [{
            "type": "resources",
            "description": "LavaVisual accessories on the player model",
            "uuid": str(uuid.uuid5(NAMESPACE, "module")),
            "version": [1, 0, 0],
        }],
    }
    geometry = make_bedrock.server_geometry()
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as pack:
        def put(name, data):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            pack.writestr(info, data)

        put("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        put("models/entity/player.geo.json", json.dumps(geometry))
        put("pack_icon.png", icon())
    return geometry


def check(path):
    """The pack is a resource pack that really replaces both player models and keeps every standard bone."""
    standard = {"root", "body", "head", "hat", "leftArm", "rightArm", "leftLeg", "rightLeg", "jacket", "cape"}
    with zipfile.ZipFile(path) as pack:
        names = set(pack.namelist())
        for required in ("manifest.json", "models/entity/player.geo.json", "pack_icon.png"):
            assert required in names, "missing " + required
        manifest = json.loads(pack.read("manifest.json"))
        assert manifest["modules"][0]["type"] == "resources", "not a resource pack"
        models = json.loads(pack.read("models/entity/player.geo.json"))["minecraft:geometry"]
        found = {model["description"]["identifier"] for model in models}
        assert found == {"geometry.humanoid.custom", "geometry.humanoid.customSlim"}, found
        for model in models:
            bones = {bone["name"] for bone in model["bones"]}
            assert standard <= bones, "missing player bones: " + str(standard - bones)
            for accessory in ("lv_glasses", "lv_headphones", "lv_scarf"):
                assert accessory in bones, model["description"]["identifier"] + " misses " + accessory
            arm = next(bone for bone in model["bones"] if bone["name"] == "leftArm")
            slim = model["description"]["identifier"].endswith("Slim")
            assert arm["cubes"][0]["size"][0] == (3 if slim else 4), "wrong arm width"
            # Every accessory cube must read the palette corner, never the player's own pixels.
            for bone in model["bones"]:
                if not bone["name"].startswith("lv_"):
                    continue
                for cube in bone["cubes"]:
                    for face in cube["uv"].values():
                        u, v = face["uv"]
                        assert 0 <= u < 8 and 0 <= v < 8, "cube reads the skin at %s,%s" % (u, v)
    return len(models)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "artifacts", "bedrock", "LavaVisual-Bedrock-Pack-1.0.0.mcpack")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    build(path)
    models = check(path)
    print("LavaVisual server pack ok: %d player models with accessories, %d bytes" % (models, os.path.getsize(path)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
