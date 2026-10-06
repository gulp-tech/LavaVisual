#!/usr/bin/env python3
"""Mock-up of the LavaVisual durability visuals, drawn with the same numbers as hud/HudRenderer.java.

The HUD cannot be looked at without launching Minecraft, and the strips live deep inside the HUD renderer. This tool
draws them with PIL instead, using exactly the geometry and the colours of the mod: the armour widget (4 slots of
20 x 20 with a step of 23), the equipment strip of the Target HUD (slots of 19 x 19 with a step of 21), the track of
14 x 3 and 13 x 3, the one-unit highlight, the percent text, and the low-durability warning (the red rim stays under the
slot plate, the breathing wash goes over the item, the percent brightens with the pulse). The pulse is drawn at its
brightest phase.

    python3 tools/preview_durability.py [out.png (default docs/durability-preview.png)]

It is a mock-up, not a screenshot: it shows the design, not the pixel-exact result.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from preview_menu import View, mix, SS  # noqa: E402  (the sheet shares the mock-up canvas of the menu preview)

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'docs' / 'durability-preview.png'

BG = 0x101319
PANEL = 0x161A21
SLOT = 0xF21D2027
TRACK = 0x23262D
WARN = 0xE0453A
TEXT = 0xE8E8EB
DIM = 0x8A9099
ARMOR_ICONS = ['SHIELD', 'SHIELD_HALF', 'GEM', 'CROWN']
HAND_ICON = 'SWORDS'


def durability(ratio):
    """The same thresholds as HudRenderer.durability()."""
    return 0x7ADB6A if ratio > 0.5 else 0xE0C14C if ratio > 0.25 else 0xE06A4C


def shown(color, pulse):
    return mix(color, 0xFFFFFF, 0.45 * pulse)


def armor_slot(view, x, y, ratio, pulse=1.0, warn=True, sprite=None):
    """One slot of the armour widget, in the order of HudRenderer.armor()."""
    low = ratio is not None and ratio <= 0.2 and warn
    if low:
        view.rr(x - 1, y - 1, 22, 22, 5, WARN, 0.35 + 0.4 * pulse)
    view.rr(x, y, 20, 20, 4, SLOT)
    if ratio is None:
        if sprite:
            view.icon(sprite, x + 3, y + 2, 14, DIM, 0.30)
        return
    if sprite:
        view.icon(sprite, x + 3, y + 2, 14, 0xC9D0DA, 0.85)
    if ratio < 0:
        view.text(x, y + 22, '∞', 0x9AA0AC, 1.0, size=6, align='center', width=20)
        return
    color = shown(durability(ratio), pulse) if low else durability(ratio)
    if low:
        view.rr(x, y, 20, 20, 4, WARN, 0.10 + 0.20 * pulse)
    view.rr(x + 3, y + 17, 14, 3, 1, TRACK)
    bar = max(1, round(12 * ratio))
    view.rrh(x + 4, y + 18, bar, 2, 1, color, mix(color, 0xFFFFFF, 0.35))
    view.rr(x + 4, y + 18, bar, 1, 0, 0xFFFFFF, 0.35)
    view.text(x, y + 22, f'{round(ratio * 100)}%', color, 1.0, size=6, align='center', width=20)


def gear_slot(view, x, y, ratio, pulse=1.0, warn=True, sprite=None):
    """One slot of the Target HUD equipment strip, in the order of HudRenderer.gear()."""
    low = ratio is not None and ratio <= 0.2 and warn
    if low:
        view.rr(x - 1, y - 1, 21, 21, 5, WARN, 0.35 + 0.4 * pulse)
    view.rr(x + 1, y + 1, 19, 19, 4, 0x000000, 0.25)
    view.rrv(x, y, 19, 19, 4, mix(PANEL, 0xFFFFFF, 0.05), PANEL)
    if ratio is None:
        if sprite:
            view.icon(sprite, x + 2, y + 2, 14, DIM, 0.30)
        return
    if sprite:
        view.icon(sprite, x + 2, y + 2, 14, 0xC9D0DA, 0.85)
    if ratio < 0:
        return
    color = shown(durability(ratio), pulse) if low else durability(ratio)
    if low:
        view.rr(x, y, 19, 19, 4, WARN, 0.10 + 0.20 * pulse)
    view.rr(x + 3, y + 16, 13, 3, 1, TRACK)
    fill = max(1, round(11 * ratio))
    view.rrh(x + 4, y + 17, fill, 2, 1, color, mix(color, 0xFFFFFF, 0.35))
    view.rr(x + 4, y + 17, fill, 1, 0, 0xFFFFFF, 0.35)


def armor_widget(view, x, y, ratios, pulse=1.0, warn=True):
    view.rr(x, y, 97, 36, 6, PANEL)
    for i, ratio in enumerate(ratios):
        armor_slot(view, x + 4 + i * 23, y + 4, ratio, pulse, warn, ARMOR_ICONS[i])


def gear_strip(view, x, y, ratios, pulse=1.0, warn=True):
    for i, ratio in enumerate(ratios):
        gear_slot(view, x + i * 21, y, ratio, pulse, warn, HAND_ICON if i == 4 else ARMOR_ICONS[i])


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    view = View(322, 224)
    view.img.paste(0x101319FF, (0, 0, 322 * SS, 224 * SS))

    view.text(14, 12, 'LavaVisual 1.1.2 · прочность', TEXT, 1.0, size=13, bold=True)
    view.text(14, 31, 'Одно оформление во всех визуалах: дорожка, градиент, блик, процент по цвету.', DIM, 1.0, size=7)

    view.text(14, 52, 'Броня · обычная прочность', DIM, 1.0, size=8, bold=True)
    armor_widget(view, 14, 63, [1.0, 0.62, 0.34, None])

    view.text(14, 113, 'Броня · 20 % и меньше: слот дышит красным', DIM, 1.0, size=8, bold=True)
    view.text(240, 114, 'яркая и тихая фаза', DIM, 0.8, size=6, align='right', width=68)
    armor_widget(view, 14, 124, [0.18, 0.07, -1, 0.45])
    armor_widget(view, 122, 124, [0.18, 0.07, -1, 0.45], pulse=0.0)

    view.text(14, 174, 'Снаряжение в Target HUD · те же полосы, то же предупреждение', DIM, 1.0, size=8, bold=True)
    gear_strip(view, 14, 185, [0.9, 0.55, 0.3, None, 0.15])

    view.text(14, 213, 'Порог 20 % · выключатель «Подсветка низкой прочности» в настройках виджета брони', DIM, 0.85, size=6)
    view.img.save(out)
    print(f'{out} -> {out.stat().st_size // 1024} KB')


if __name__ == '__main__':
    main()
