package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiDraw {
    private UiDraw() { }
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        for (int row = 0; row < h; row++) {
            double dy = row < r ? r - row - 0.5 : row >= h - r ? row - (h - r) + 0.5 : 0;
            int inset = dy == 0 ? 0 : (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + row, x + r, y + row + 1, color);
            g.fill(x + w - r, y + row, x + w - inset, y + row + 1, color);
        }
    }
    public static int alpha(int rgb, double opacity) { return ((int) Math.round(255 * opacity) << 24) | (rgb & 0xFFFFFF); }
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        round(g, x, y, 22, 11, 5, enabled ? 0xFFFF853A : 0xFF36383D);
        round(g, x + (enabled ? 13 : 2), y + 2, 7, 7, 3, enabled ? 0xFFFFFFFF : 0xFF96989F);
    }
}
