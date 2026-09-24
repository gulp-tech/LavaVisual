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

    public static int mix(int a, int b, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int v = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | v;
    }
    public static int alpha(int rgb, double opacity) { return ((int) Math.round(255 * Math.clamp(opacity, 0, 1)) << 24) | (rgb & 0xFFFFFF); }
    /** ARGB interpolation including alpha. */
    public static int lerpArgb(int a, int b, double t) {
        t = Math.clamp(t, 0, 1);
        return (int) Math.round((a >>> 24) * (1 - t) + (b >>> 24) * t) << 24 | mix(a, b, t);
    }
    public static int fade(int argb, double k) { return ((int) Math.round((argb >>> 24) * Math.clamp(k, 0, 1)) << 24) | (argb & 0xFFFFFF); }

    /** Vertical gradient (ARGB top to bottom) with the same anti-aliased corners as {@link #round}. */
    public static void roundV(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        if (top == bottom) { round(g, x, y, w, h, radius, top); return; }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.fillGradient(x, y, x + w, y + h, top, bottom); return; }
        if (h > 2 * r) g.fillGradient(x, y + r, x + w, y + h - r, lerpArgb(top, bottom, (double) r / h), lerpArgb(top, bottom, (double) (h - r) / h));
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double boundary = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            int inset = (int) Math.ceil(boundary), bottomRow = y + h - row - 1;
            int ct = lerpArgb(top, bottom, (row + 0.5) / h), cb = lerpArgb(top, bottom, (h - row - 0.5) / h);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, ct);
            g.fill(x + inset, bottomRow, x + w - inset, bottomRow + 1, cb);
            double cover = inset - boundary;
            if (cover > 0.004) {
                int et = fade(ct, cover), eb = fade(cb, cover);
                g.fill(x + inset - 1, y + row, x + inset, y + row + 1, et);
                g.fill(x + w - inset, y + row, x + w - inset + 1, y + row + 1, et);
                g.fill(x + inset - 1, bottomRow, x + inset, bottomRow + 1, eb);
                g.fill(x + w - inset, bottomRow, x + w - inset + 1, bottomRow + 1, eb);
            }
        }
    }
    /** Horizontal gradient (ARGB left to right): the vertical one drawn in a pose turned by -90 degrees. */
    public static void roundH(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int left, int right) {
        if (w <= 0 || h <= 0) return;
        if (left == right) { round(g, x, y, w, h, radius, left); return; }
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y + h);
            g.pose().rotate((float) (-Math.PI / 2));
            roundV(g, 0, 0, h, w, radius, left, right);
        } finally { g.pose().popMatrix(); }
    }
    /** Soft drop shadow from a few stacked translucent layers; strength is the opacity right under the element. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int spread, int drop, double strength) {
        if (spread <= 0 || strength <= 0.003) return;
        int color = alpha(0, 1 - Math.pow(1 - Math.min(0.95, strength), 1.0 / spread));
        for (int i = spread; i >= 1; i--) round(g, x - i, y - i + drop, w + 2 * i, h + 2 * i, radius + i, color);
    }
    /** Coloured halo in a horizontal gradient, e.g. accent to the second theme colour. */
    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int spread, int left, int right, double strength) {
        if (spread <= 0 || strength <= 0.003) return;
        double layer = 1 - Math.pow(1 - Math.min(0.95, strength), 1.0 / spread);
        int a = alpha(left, layer), b = alpha(right, layer);
        for (int i = spread; i >= 1; i--) roundH(g, x - i, y - i, w + 2 * i, h + 2 * i, radius + i, a, b);
    }
}
