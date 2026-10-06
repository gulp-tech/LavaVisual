#!/usr/bin/env python3
"""Mock-up of the LavaVisual menu, drawn with the same layout and decorations as ui/ClickGuiScreen.java.

The menu cannot be looked at without launching Minecraft, and a build needs the Minecraft and Fabric jars, which are
not always at hand. This tool draws the menu with PIL instead: the same numbers (the panel of 600 x 360, the sidebar of
112, the tab step, cards of 48, chips of 24, sliders of 38, the footer plate, the fade where the page scrolls away) and
the same order of the decorations introduced in the redesign — the corner glows, the rim light, the theme hairline with
its shine, the hairline borders of the cards and chips, the ringed slider knobs, the plate under the hints.

Text comes from the bundled Inter font, icons from the bundled Lucide font, so the sheet looks like the real menu. It
is a mock-up, not a screenshot: it shows the design, not the pixel-exact result.

    python3 tools/preview_menu.py [out.png (default docs/menu-preview.png)]

Two pages are drawn side by side: "Эффекты" (sections, cards with open option groups, chips, sliders) and "Косметика"
with the cape subpage open (chips, an action, a colour row and a note).
"""
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / 'tools' / 'fonts'

# --- the theme and the menu geometry, as the mod has them by default ---------------------------------------------
ACCENT, ACCENT2 = 0xFF6A2B, 0xA77BFF
MENU_BG, OPACITY, DIM = 0x12151B, 0.9, 0.12
SS = 2                       # supersampling: every GUI unit is drawn as SS x SS pixels


def rgb_of(color):
    """Both ints (0xRRGGBB) and ready triples are accepted everywhere: the mock mixes the two freely."""
    return (color >> 16 & 255, color >> 8 & 255, color & 255) if isinstance(color, int) else tuple(int(v) for v in color)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    x, y = rgb_of(a), rgb_of(b)
    return tuple(round(x[i] * (1 - t) + y[i] * t) for i in range(3))


def rgba(color, opacity=1.0):
    r, g, b = rgb_of(color)
    return (r, g, b, max(0, min(255, round(255 * opacity))))


def blend(a, b, t):
    return mix(a, b, t)


class View:
    """Everything is drawn in GUI units on a canvas scaled by SS; the panel of the menu starts at (left, top)."""

    def __init__(self, width, height):
        self.img = Image.new('RGBA', (width * SS, height * SS), (0, 0, 0, 0))
        self.fonts = {}
        self.icons = dict(re.findall(r'String ([A-Z0-9_]+) = "\\u([0-9A-F]{4})"',
                                     (ROOT / 'ports/mc26.2/src/main/java/tech/gulp/lavavisual/ui/Icons.java').read_text(encoding='utf-8')))

    # ---------------------------------------------------------------- primitives
    def font(self, size, bold=False):
        key = (size, bold)
        if key not in self.fonts:
            face = ImageFont.truetype(str(FONTS / 'InterVariable.ttf'), size * SS)
            try:
                face.set_variation_by_name('Bold' if bold else 'Medium')
            except Exception:
                pass
            self.fonts[key] = face
        return self.fonts[key]

    def layer(self):
        overlay = Image.new('RGBA', self.img.size, (0, 0, 0, 0))
        return overlay, ImageDraw.Draw(overlay)

    def paste(self, overlay):
        self.img.alpha_composite(overlay)

    def rr(self, x, y, w, h, radius, color, opacity=1.0):
        if w <= 0 or h <= 0:
            return
        overlay, d = self.layer()
        d.rounded_rectangle([x * SS, y * SS, (x + w) * SS - 1, (y + h) * SS - 1], radius * SS, fill=rgba(color, opacity))
        self.paste(overlay)

    def _gradient(self, x, y, w, h, radius, first, second, vertical, opacity):
        if w <= 0 or h <= 0:
            return
        w, h = max(1, int(w * SS)), max(1, int(h * SS))
        span = h if vertical else w
        strip = Image.new('RGB', (1, span))
        for i in range(span):
            strip.putpixel((0, i), mix(first, second, i / max(1, span - 1)))
        grad = strip.resize((1, h) if vertical else (w, 1)).resize((w, h))
        mask = Image.new('L', (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], max(0, radius * SS), fill=round(255 * opacity))
        grad = grad.convert('RGBA')
        grad.putalpha(mask)
        self.img.alpha_composite(grad, (int(x * SS), int(y * SS)))

    def rrv(self, x, y, w, h, radius, top, bottom, opacity=1.0):
        """Rounded rectangle with a vertical gradient, in the colours of the theme."""
        self._gradient(x, y, w, h, radius, top, bottom, True, opacity)

    def rrh(self, x, y, w, h, radius, left, right, opacity=1.0):
        self._gradient(x, y, w, h, radius, left, right, False, opacity)

    def glow(self, cx, cy, r, color, opacity):
        if r <= 0 or opacity <= 0.002:
            return
        size = 96
        disc = Image.new('L', (size, size), 0)
        for i in range(size):
            for j in range(size):
                d = math.hypot(i + 0.5 - size / 2, j + 0.5 - size / 2) / (size / 2)
                disc.putpixel((i, j), 0 if d >= 1 else round(255 * (1 - d) ** 2.2))
        disc = disc.resize((max(1, int(2 * r * SS)),) * 2)
        overlay = Image.new('RGBA', self.img.size, (0, 0, 0, 0))
        tint = Image.new('RGBA', disc.size, rgba(color, opacity))
        overlay.paste(tint, (int((cx - r) * SS), int((cy - r) * SS)), disc)
        self.paste(overlay)

    def halo(self, x, y, w, h, radius, spread, left, right, strength=0.2):
        """UiDraw.glow: a coloured halo around a rounded rectangle, stacked translucent layers."""
        for i in range(spread, 0, -1):
            self.rrh(x - i, y - i, w + 2 * i, h + 2 * i, radius + i, left, right, strength / spread)

    def shadow(self, x, y, w, h, radius, spread, drop, strength):
        for i in range(spread, 0, -1):
            self.rr(x - i, y - i + drop, w + 2 * i, h + 2 * i, radius + i, 0x000000, strength / spread)

    def circle(self, cx, cy, r, color, opacity=1.0):
        self.rr(cx - r, cy - r, 2 * r, 2 * r, int(round(r)), color, opacity)

    def icon(self, name, x, y, size, color, opacity=1.0):
        code = self.icons.get(name)
        if code is None:
            return
        face = ImageFont.truetype(str(FONTS / 'lucide.ttf'), size * SS)
        overlay, d = self.layer()
        d.text((x * SS, y * SS), chr(int(code, 16)), font=face, fill=rgba(color, opacity))
        self.paste(overlay)

    def text(self, x, y, value, color, opacity=1.0, size=9, bold=False, width=None, align='left'):
        if value is None:
            return 0
        face = self.font(size, bold)
        if width is not None:
            while len(value) > 1 and face.getlength(value) > width * SS:
                value = value[:-1]
        length = face.getlength(value) / SS
        if align == 'right':
            x = x + (width or 0) - length
        elif align == 'center':
            x = x + ((width or 0) - length) / 2
        overlay, d = self.layer()
        d.text((x * SS, y * SS - size * SS * 0.08), value, font=face, fill=rgba(color, opacity))
        self.paste(overlay)
        return length

    def gradient_text(self, x, y, value, left, right, opacity=1.0, size=9, bold=False, align='left', width=None):
        face = self.font(size, bold)
        if width is not None:
            while len(value) > 1 and face.getlength(value) > width * SS:
                value = value[:-1]
        w = max(1, int(face.getlength(value)) + 4)
        h = int(face.size + 6 * SS)
        mask = Image.new('L', (w, h), 0)
        ImageDraw.Draw(mask).text((2, 0), value, font=face, fill=255)
        grad = Image.new('RGB', (w, 1))
        for i in range(w):
            grad.putpixel((i, 0), mix(left, right, i / max(1, w - 1)))
        grad = grad.resize((w, h)).convert('RGBA')
        alpha = mask.point(lambda v: round(v * opacity))
        grad.putalpha(alpha)
        if align == 'center':
            x = x + ((width or 0) - w / SS) / 2
        self.img.alpha_composite(grad, (int(x * SS), int(y * SS - size * SS * 0.08)))


class Menu:
    """The menu of ClickGuiScreen: chrome, sidebar, header, then whatever page is asked for."""

    def __init__(self, view, left, top, width=600, height=360, tab=0, sub=False):
        self.v = view
        self.left, self.top, self.panel_w, self.panel_h = left, top, width, height
        self.side = 112 if width >= 440 else 88
        self.body_x = left + self.side + 16
        self.body_w = width - self.side - 32
        self.clip_top, self.clip_bottom = top + 49, top + height - 33
        self.tab_step = max(20, min(29, (height - 72 - 30) // 11))
        self.tab_h = min(25, self.tab_step - 2)
        self.tab_pad = (self.tab_h - 11) // 2
        self.tab = tab
        self.sub = sub
        self.cursor = self.clip_top + 3

    # ---------------------------------------------------------------- chrome
    def chrome(self, mouse):
        v, left, top, pw, ph, side = self.v, self.left, self.top, self.panel_w, self.panel_h, self.side
        v.shadow(left, top, pw, ph, 10, 7, 3, 0.5)
        v.rrv(left - 6, top - 6, pw + 12, ph + 12, 14, ACCENT, ACCENT2, 0.10)
        v.rrv(left, top, pw, ph, 10, mix(MENU_BG, 0xFFFFFF, 0.035), mix(MENU_BG, 0x000000, 0.16), OPACITY)
        v.glow(left + 30, top + 4, 130, ACCENT, 0.11 * OPACITY)
        v.glow(left + pw - 24, top + ph, 160, ACCENT2, 0.10 * OPACITY)
        v.rrv(left + 8, top + 1, pw - 16, 33, 0, mix(MENU_BG, 0xFFFFFF, 0.06), mix(MENU_BG, 0xFFFFFF, 0.035), 1.0)
        v.rr(left, top, pw, 1, 0, mix(ACCENT, 0xFFFFFF, 0.35), 0.9)
        v.rr(left, top + ph - 1, pw, 1, 0, mix(ACCENT2, 0x000000, 0.4), 0.55)
        v.rrv(left, top + 8, 1, ph - 16, 0, ACCENT, ACCENT2, 0.8)
        v.rrv(left + pw - 1, top + 8, 1, ph - 16, 0, ACCENT2, ACCENT, 0.55)
        v.rr(left + 4, top + 4, side - 6, ph - 8, 8, mix(MENU_BG, 0x000000, 0.42), OPACITY * 0.75)
        v.rrv(left + side + 2, top + 1, pw - side - 12, 45, 0, mix(MENU_BG, 0x000000, 0.28), mix(MENU_BG, 0x000000, 0.0), 0.45)
        shine = 0.34
        for gx in range(0, pw - 24, 2):
            t = gx / (pw - 24)
            far = abs(t - shine)
            pulse = max(0.0, 1 - min(far, 1 - far) * 6)
            v.rrh(left + 12 + gx, top, 2, 1, 0, 0xFFFFFF, mix(ACCENT, ACCENT2, t), 0.85 * pulse * math.sin(math.pi * t))
        v.rrv(left + side, top + 15, 1, ph - 30, 0, ACCENT, ACCENT2, 0.5)
        # brand
        logo = Image.open(ROOT / 'ports/mc26.2/src/main/resources/assets/lavavisual/textures/gui/logo/menu_2.png').convert('RGBA')
        logo = logo.resize((round(logo.width * 30 / logo.height), 30), Image.LANCZOS)
        self.v.img.alpha_composite(logo, ((left + 4 + (side - 6) // 2 - logo.width // 2) * SS, (top + 9) * SS))
        v.gradient_text(left + 4, top + 45, 'LavaVisual', mix(ACCENT, 0xFFFFFF, 0.1), mix(ACCENT2, 0xFFFFFF, 0.1),
                        align='center', size=9, bold=True, width=side - 6)
        v.rrh(left + 16, top + 67, side - 28, 1, 0, 0xFFFFFF, 0xFFFFFF, 0.09)
        # tabs
        self.tabs(mouse)
        if 72 + 11 * self.tab_step + 14 < ph - 21:
            v.rr(left + 11, top + ph - 25, side - 22, 17, 5, 0xFFFFFF, 0.035)
            v.rrh(left + 11, top + ph - 25, side - 22, 1, 0, ACCENT, ACCENT2, 0.28)
            v.text(left + 16, top + ph - 21, '26.2 · LavaVisual', 0x6F7988, size=9)
        # header
        heading = ('Плащ' if self.sub else 'Эффекты')
        v.rrv(self.body_x - 9, top + 18, 3, 12, 1, ACCENT, ACCENT2, 1.0)
        v.glow(self.body_x + 30, top + 24, 66, ACCENT, 0.07)
        search_w = max(70, min(150, self.body_w // 2 - 20))
        search_x = left + pw - 58 - search_w
        v.gradient_text(self.body_x, top + 17, heading, mix(ACCENT, 0xFFFFFF, 0.55), mix(ACCENT2, 0xFFFFFF, 0.45), size=12, bold=True)
        self.search(search_x, top + 11, search_w, 22)
        v.icon('MOVE', left + pw - 50, top + 17, 10, 0x4E5664)
        over_close = abs(mouse[0] - (left + pw - 19)) < 12 and abs(mouse[1] - (top + 22)) < 12
        if over_close:
            v.rr(left + pw - 31, top + 10, 24, 24, 6, 0x2A2E36)
        v.icon('X', left + pw - 24, top + 17, 10, 0xFFFFFF if over_close else 0xABB4C2)
        v.rr(self.body_x, top + 39, self.body_w, 1, 0, 0x262A33)
        v.rrh(self.body_x, top + 39, self.body_w, 1, 0, ACCENT, ACCENT2, 0.95)
        v.rrv(self.body_x, top + 40, self.body_w, 10, 0, mix(MENU_BG, ACCENT, 0.35), mix(MENU_BG, MENU_BG, 0), 0.35)

    def tabs(self, mouse):
        v, left, top, side = self.v, self.left, self.top, self.side
        tabs = ['HUD', 'Эффекты', 'Косметика', 'Руки', 'Звуки', 'Музыка', 'Карта', 'Бинды', 'Цвета', 'Мир / FPS', 'Интерфейс']
        icons = ['LAYOUT_DASHBOARD', 'SPARKLES', 'CROWN', 'HAND', 'VOLUME_2', 'MUSIC', 'MAP', 'KEYBOARD', 'PALETTE', 'EARTH', 'SETTINGS']
        y = top + 72 + self.tab * self.tab_step
        v.halo(left + 8, y, side - 16, self.tab_h, 6, 3, ACCENT, ACCENT2, 0.20)
        v.rrh(left + 8, y, side - 16, self.tab_h, 6, ACCENT, ACCENT2, 0.30)
        v.rr(left + 8, y, side - 16, 1, 0, mix(ACCENT, 0xFFFFFF, 0.3), 0.30)
        v.rrv(left + 8, y + self.tab_pad - 1, 2, 13, 1, ACCENT, ACCENT2, 1.0)
        v.circle(left + side - 17, y + self.tab_h / 2, 2, mix(ACCENT, ACCENT2, 0.45), 0.85)
        for k, (name, icon) in enumerate(zip(tabs, icons)):
            row = top + 72 + k * self.tab_step
            active = k == self.tab
            over = abs(mouse[1] - row) < self.tab_h / 2 and mouse[0] < left + side
            if over and not active:
                v.rr(left + 8, row, side - 16, self.tab_h, 6, 0xFFFFFF, 0.05)
                v.rrv(left + 8, row + self.tab_pad + 1, 2, 9, 1, ACCENT, ACCENT2, 0.55)
            color = 0xFFFFFF if active else (0xD2D8E1 if over else 0x929BA9)
            v.icon(icon, left + 17, row + self.tab_pad, 10, mix(ACCENT, 0xFFFFFF, 0.2) if active else color)
            v.text(left + 33, row + self.tab_pad + 1, name, color, size=9)

    def search(self, x, y, w, h):
        v = self.v
        v.rr(x, y, w, h, 7, 0xFFFFFF, 0.045)
        v.rr(x + 1, y + 1, w - 2, h - 2, 6, 0x1D2026)
        v.icon('SEARCH', x + 7, y + 6, 10, 0x6B7280)
        v.text(x + 22, y + 7, 'Поиск · Ctrl+F', 0x6B7280, size=9)

    # ---------------------------------------------------------------- page body
    def section(self, title):
        v, x, w, y = self.v, self.body_x, self.body_w, self.cursor
        v.rrv(x + 1, y + 5, 3, 10, 1, ACCENT, ACCENT2, 1.0)
        v.circle(x - 4, y + 10, 1.6, mix(ACCENT, ACCENT2, 0.5), 0.8)
        v.gradient_text(x + 10, y + 6, title, mix(ACCENT, 0xFFFFFF, 0.12), mix(ACCENT2, 0xFFFFFF, 0.12), size=9, bold=True)
        v.rrh(x, y + 21, w, 1, 0, ACCENT, mix(ACCENT2, MENU_BG, 0.9), 0.5)
        self.cursor += 28

    def toggle(self, key, title, desc, enabled, settings=False, group=None):
        v, x, w, y = self.v, self.body_x, self.body_w, self.cursor
        over = 0.0
        v.rr(x, y, w, 48, 8, 0xFFFFFF, 0.045)
        v.rrv(x + 1, y + 1, w - 2, 46, 7, blend(0x1C1F26, 0x2A2F38, over), blend(0x16181E, 0x22262E, over), OPACITY)
        v.rr(x + 7, y, w - 14, 1, 0, 0xFFFFFF, 0.04)
        lit = 1.0 if enabled else 0.0
        if lit > 0.01:
            v.rr(x - 1, y - 1, w + 2, 50, 9, ACCENT, 0.11 * lit)
            v.rrh(x + 1, y + 1, w - 2, 46, 7, ACCENT, ACCENT2, 0.13 * lit)
            v.rrv(x + 1, y + 10, 2, 28, 1, ACCENT, ACCENT2, lit)
            v.rrv(x + w - 3, y + 12, 2, 24, 1, ACCENT, ACCENT2, 0.75 * lit)
        v.rr(x + 9, y + 11, 26, 26, 8, 0xFFFFFF, 0.05 + 0.10 * lit)
        v.rrv(x + 10, y + 12, 24, 24, 7, blend(0x2B2F38, ACCENT, lit * 0.42), blend(0x22252D, ACCENT2, lit * 0.36), 1.0)
        v.icon(key_icon(key), x + 17, y + 19, 10, mix(0xAEB6C4, mix(ACCENT, 0xFFFFFF, 0.55), lit))
        v.text(x + 44, y + 9, title, 0xE5E9F0, size=9, bold=True, width=w - 44 - (51 if not settings else 73))
        v.text(x + 44, y + 28, desc, 0x838994, size=9, width=w - 56)
        toggle_x = x + w - (43 if not settings else 65)
        if lit > 0.02:
            v.rrh(toggle_x - 2, y + 7, 34, 18, 9, ACCENT, ACCENT2, 0.2 * lit)
        v.rrh(toggle_x, y + 9, 30, 14, 7, blend(0x393E47, ACCENT, lit), blend(0x393E47, ACCENT2, lit), 1.0)
        knob = toggle_x + 2 + round(lit * 16)
        if lit > 0.4:
            v.circle(knob + 5, y + 16, 5, mix(ACCENT, ACCENT2, lit), 0.35 * lit)
        v.circle(knob + 5, y + 16, 5, 0xF7F8FB)
        if settings:
            v.icon('CHEVRON_RIGHT', x + w - 20, y + 11, 10, 0xB3BAC7)
        self.cursor += 56
        if group and enabled:
            self.group(*group)

    def group(self, height, body):
        """The panel behind the options of a card that is on: drawn first, rows inside it."""
        v, x, w = self.v, self.body_x, self.body_w
        start = self.cursor - 3
        v.rr(x + 6, start, w - 6, height, 7, 0x181A20, 0.85 * OPACITY)
        v.rrv(x + 6, start + 7, 2, max(4, height - 14), 1, ACCENT, ACCENT2, 0.6)
        outer_x, outer_w = self.body_x, self.body_w
        self.body_x, self.body_w = x + 17, w - 23
        self.cursor = start + 6
        body()
        self.body_x, self.body_w = outer_x, outer_w
        self.cursor = start + height + 10

    def chips(self, options, current):
        v, x, w = self.v, self.body_x, self.body_w
        per_row = min(len(options), 4)
        gap, cell = 6, (w - 6 * (min(len(options), 4) - 1)) // 4
        for i, option in enumerate(options):
            cx = x + (i % per_row) * (cell + gap)
            y = self.cursor + (i // per_row) * 30
            on = i == current
            if on:
                v.rrh(cx - 1, y - 1, cell + 2, 26, 7, ACCENT, ACCENT2, 0.25)
                v.rrh(cx, y, cell, 24, 6, ACCENT, ACCENT2, 1.0)
                v.rrv(cx + 3, y + 1, cell - 6, 9, 4, 0xFFFFFF, 0xFFFFFF, 0.18)
            else:
                v.rr(cx, y, cell, 24, 6, 0xFFFFFF, 0.06)
                v.rrv(cx + 1, y + 1, cell - 2, 22, 5, 0x282B33, 0x212329, 1.0)
            v.text(cx, y + 8, option, 0xFFFFFF if on else 0xC9D0DA, size=9, bold=on, width=cell, align='center')
        rows = (len(options) + per_row - 1) // per_row
        self.cursor += rows * 30 + 4

    def caption(self, title):
        self.v.text(self.body_x + 1, self.cursor + 1, title, 0x9AA3B1, size=7)
        self.cursor += 15

    def slider(self, label, shown, progress):
        v, x, w, y = self.v, self.body_x, self.body_w, self.cursor
        v.text(x + 2, y + 2, label, 0xBEC6D3, size=9, width=w - 66)
        v.rr(x + w - 56, y - 1, 56, 16, 5, 0xFFFFFF, 0.05)
        v.text(x + w - 52, y + 3, shown, 0xF2F4F8, size=9, width=48)
        track_x, track_w = x + 4, w - 8
        v.rr(track_x, y + 20, track_w, 5, 2, 0x262A32)
        v.rr(track_x, y + 20, track_w, 1, 0, 0x000000, 0.35)
        filled = round(track_w * progress)
        tip = mix(ACCENT, ACCENT2, progress)
        if filled > 0:
            v.rrh(track_x, y + 20, filled, 5, 2, ACCENT, tip)
            v.rrv(track_x, y + 20, filled, 1, 0, 0xFFFFFF, 0xFFFFFF, 0.3)
        v.circle(track_x + filled, y + 23, 9, tip, 0.13)
        v.circle(track_x + filled, y + 23, 7, tip, 0.30)
        v.circle(track_x + filled, y + 23, 6, tip, 1.0)
        v.rr(track_x + filled - 4, y + 18, 8, 10, 4, 0xF7F9FC)
        self.cursor += 38

    def action(self, icon, title):
        v, x, w, y = self.v, self.body_x, self.body_w, self.cursor
        v.rrv(x, y, w, 24, 6, 0x282B33, 0x212329, 1.0)
        v.rr(x + 6, y, w - 12, 1, 0, 0xFFFFFF, 0.05)
        if icon:
            v.icon(icon, x + 8, y + 7, 10, 0xAEB6C4)
        v.text(x + 23, y + 8, title, 0xE8EAF0, size=9, width=w - 31)
        self.cursor += 32

    def fade(self, x, y, w, h, color, strength, steps=12):
        """The page fading into the plate where it scrolls away, drawn after the page."""
        for i in range(steps):
            self.v.rr(x, y + h * i / steps, w, h / steps + 1, 0, color, strength * (i + 1) / steps)

    def footer(self):
        v, left, top, pw, ph = self.v, self.left, self.top, self.panel_w, self.panel_h
        v.rr(self.body_x - 4, top + ph - 25, self.body_w + 8, 19, 6, 0xFFFFFF, 0.035)
        v.rrh(self.body_x - 4, top + ph - 25, self.body_w + 8, 1, 0, ACCENT, ACCENT2, 0.30)
        v.text(self.body_x, top + ph - 20, 'R · меню    P · всё выкл    B · метка', 0x818C9C, size=9)
        # the page fades out where it scrolls away, then the scrollbar of the body
        self.fade(self.body_x, self.clip_bottom - 18, self.body_w, 18, mix(MENU_BG, 0x000000, 0.30), 0.8 * OPACITY)
        v.rrv(left + pw - 8, self.clip_top + 40, 2, 120, 1, ACCENT, ACCENT2, 0.7)


def key_icon(key):
    return {'esp': 'SCAN_EYE', 'marker': 'TARGET', 'particles': 'SPARKLE', 'cape': 'WIND', 'hat': 'CROWN',
            'wings': 'WIND', 'target': 'TARGET', 'coordinates': 'MAP_PIN', 'armor': 'SHIELD', 'totems': 'HEART_PULSE',
            'keys': 'KEYBOARD', 'watermark': 'STAMP', 'kill': 'SKULL'}.get(key, 'SLIDERS_HORIZONTAL')


def page_effects(menu):
    """Section, two cards, the options of both open: the layout of the Effects page of the menu."""
    menu.section('Цель')
    menu.toggle('esp', 'Target ESP', 'Подсветка игрока или моба, на которого вы навелись', True,
                group=(85, lambda: (menu.chips(['Призраки', 'Круг', 'Кристаллы', 'Маркер', 'Орбиты'], 0),
                                    menu.slider('Удержание цели · сек', '4.00', 0.37))))
    menu.toggle('marker', 'Маркер удара', 'Отметка в центре последней цели', False)
    menu.section('Удары')
    menu.toggle('particles', 'Hit Particles', 'Искры при ручной атаке', True,
                group=(138, lambda: (menu.caption('Форма'),
                                     menu.chips(['Искры', 'Звёзды', 'Сердечки'], 1),
                                     menu.slider('Число искр', '14', 0.5),
                                     menu.slider('Размер искр', '0.12', 0.38))))


def page_cosmetics(menu):
    """The cape subpage: a card with its options open, chips, a slider and a colour row."""
    menu.section('Образ')
    menu.toggle('cape', 'Плащ', '6 видов · ткань развевается на ветру', True, settings=True,
                group=(96, lambda: (menu.caption('Вид'),
                                    menu.chips(['Классический', 'Королевский', 'Звёздный'], 0),
                                    menu.caption('Стиль'),
                                    menu.chips(['узор', 'сплошной', 'градиент'], 2),
                                    menu.slider('Прозрачность', '1.00', 1.0))))
    menu.section('Цвет')
    menu.slider('Развевание', '1.00', 0.5)
    menu.action('PENCIL', 'Редактор шляпы')


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'docs' / 'menu-preview.png'
    width, height = 1290, 430
    view = View(width, height)
    backdrop = Image.new('RGB', (width * SS, height * SS), (6, 9, 15))
    bd = ImageDraw.Draw(backdrop)
    for i in range(34):
        r1, r2 = (math.sin(i * 12.9898) * 43758.5453) % 1, (math.sin(i * 78.233) * 24634.6345) % 1
        x, y = r1 * width, height * (0.2 + 0.8 * ((i * 0.37) % 1))
        color = mix(ACCENT, ACCENT2, r1)
        bd.ellipse([(x - 2) * SS, (y - 2) * SS, (x + 2) * SS, (y + 2) * SS], fill=mix(color, 0x06090F, 0.55))
    view.img.alpha_composite(backdrop.convert('RGBA'))
    view.rr(0, 0, width, height, 0, 0x000000, DIM)
    mouse = (1080, 190)
    for left, tab, sub, page in ((20, 1, False, page_effects), (668, 2, True, page_cosmetics)):
        panel, menu = View(width, height), None
        menu = Menu(panel, left, 40, tab=tab, sub=sub)
        menu.chrome(mouse)
        page_layer = View(width, height)
        menu.v = page_layer
        page(menu)
        menu.v = panel
        menu.footer()
        # the page is drawn inside the body only: the same scissor the menu sets around its rows
        mask = Image.new('L', panel.img.size, 0)
        ImageDraw.Draw(mask).rectangle([(menu.body_x - 1) * SS, menu.clip_top * SS,
                                        (menu.body_x + menu.body_w + 1) * SS - 1, menu.clip_bottom * SS - 1], fill=255)
        panel.img.paste(page_layer.img, (0, 0), mask)
        view.img.alpha_composite(panel.img)
    view.text(width / 2, height - 26, 'макет меню LavaVisual · те же размеры и декор, что и в ui/ClickGuiScreen.java',
              0x7C8695, size=9, align='center', width=width)
    out.parent.mkdir(parents=True, exist_ok=True)
    view.img.resize((width, height), Image.LANCZOS).convert('RGB').save(out)
    print(f'{out} -> {out.stat().st_size // 1024} KB')


if __name__ == '__main__':
    main()
