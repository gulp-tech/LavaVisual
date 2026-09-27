#!/usr/bin/env python3
"""Checks the launcher icons are present and the right size (make_icon.py writes them)."""
import os
import struct
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    for density, size in SIZES.items():
        path = os.path.join(RES, "mipmap-" + density, "ic_launcher.png")
        with open(path, "rb") as icon:
            head = icon.read(24)
        if head[:8] != b"\x89PNG\r\n\x1a\n":
            print("not a png: " + path)
            return 1
        width, height = struct.unpack(">II", head[16:24])
        if (width, height) != (size, size):
            print("%s is %dx%d, expected %d" % (path, width, height, size))
            return 1
    print("LavaVisual icons ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
