#!/usr/bin/env python3
"""Writes the launcher icons of the Bedrock app: the LV mark, the same one the overlay badge draws."""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
FONT = os.path.join(HERE, "..", "..", "ports", "mc26.2", "src", "main", "resources", "assets", "lavavisual", "font", "inter-semibold.ttf")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ACCENT = (255, 122, 47, 255)
FILL = (18, 19, 26, 255)
TEXT = (242, 244, 248, 255)


def icon(size):
    scale = 8
    big = size * scale
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pad = big * 0.06
    radius = big * 0.24
    draw.rounded_rectangle([pad, pad, big - pad, big - pad], radius=radius, fill=FILL,
                           outline=ACCENT, width=int(big * 0.05))
    font = ImageFont.truetype(FONT, int(big * 0.42))
    box = draw.textbbox((0, 0), "LV", font=font)
    draw.text(((big - box[2] - box[0]) / 2, (big - box[3] - box[1]) / 2), "LV", font=font, fill=TEXT)
    return image.resize((size, size), Image.LANCZOS)


def main():
    for density, size in SIZES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        icon(size).save(os.path.join(folder, "ic_launcher.png"))
    print("LavaVisual icons ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
