package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiDraw {
    private UiDraw() { }
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double boundary = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            int inset = (int) Math.ceil(boundary);
            int edge = ((int) Math.round((color >>> 24) * (inset - boundary)) << 24) | (color & 0xFFFFFF);
            int bottom = y + h - row - 1;
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, bottom, x + w - inset, bottom + 1, color);
            if ((edge >>> 24) != 0) {
                g.fill(x + inset - 1, y + row, x + inset, y + row + 1, edge);
                g.fill(x + w - inset, y + row, x + w - inset + 1, y + row + 1, edge);
                g.fill(x + inset - 1, bottom, x + inset, bottom + 1, edge);
                g.fill(x + w - inset, bottom, x + w - inset + 1, bottom + 1, edge);
            }
        }
    }

    public static int alpha(int rgb, double opacity) { return ((int) Math.round(255 * opacity) << 24) | (rgb & 0xFFFFFF); }
}
