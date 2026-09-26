package tech.gulp.lavavisual.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Rounded shapes that stay smooth at every GUI scale. Coordinates are GUI units, but corners are drawn in physical
 * pixels: the pose is scaled down to one unit per screen pixel and each corner is a quarter of an anti-aliased
 * circle from a small generated atlas (one texel per pixel, 2D coverage), tinted by the vertex colour. A rounded
 * rectangle is 4 corner quads plus 3 fills, so it is cheaper than the old per-row corners and has no stair steps
 * at GUI scale 2-4.
 */
public final class UiDraw {
    private UiDraw() { }

    /** Generated circle atlas: every radius 1..MAX_R once, as a full disc of white texels with coverage alpha. */
    private static final class Circles {
        static final int MAX_R = 72, WIDTH = 1024;
        static final int[] U = new int[MAX_R + 1], V = new int[MAX_R + 1];
        static final int HEIGHT;
        static final Identifier ID = Identifier.fromNamespaceAndPath("lavavisual", "ui_circles");
        static DynamicTexture texture;
        static boolean failed;
        static {
            int x = 0, y = 0, row = 0;
            for (int r = 1; r <= MAX_R; r++) {
                int size = 2 * r + 1;
                if (x + size > WIDTH) { x = 0; y += row; row = 0; }
                U[r] = x; V[r] = y; x += size; row = Math.max(row, size);
            }
            HEIGHT = (y + row + 3) & ~3;
        }
        static boolean ready() {
            if (texture != null) return true;
            if (failed) return false;
            try {
                DynamicTexture created = new DynamicTexture(() -> "lavavisual ui circles", WIDTH, HEIGHT, true);
                NativeImage image = created.getPixels();
                if (image == null) { failed = true; return false; }
                for (int r = 1; r <= MAX_R; r++) {
                    for (int j = 0; j < 2 * r; j++) for (int i = 0; i < 2 * r; i++) {
                        // Coverage of the pixel by the disc: 4 x 4 samples near the edge, exact inside/outside.
                        double cx = i + 0.5 - r, cy = j + 0.5 - r, d = Math.sqrt(cx * cx + cy * cy);
                        double cover;
                        if (d <= r - 0.75) cover = 1;
                        else if (d >= r + 0.75) cover = 0;
                        else {
                            int inside = 0;
                            for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                                double px = i + (sx + 0.5) / 4 - r, py = j + (sy + 0.5) / 4 - r;
                                if (px * px + py * py <= (double) r * r) inside++;
                            }
                            cover = inside / 16.0;
                        }
                        image.setPixel(U[r] + i, V[r] + j, ((int) Math.round(cover * 255) << 24) | 0xFFFFFF);
                    }
                }
                created.upload();
                Minecraft.getInstance().getTextureManager().register(ID, created);
                texture = created;
                return true;
            } catch (RuntimeException | LinkageError error) {
                failed = true;
                return false;
            }
        }
    }

    /** Soft radial glow (white texels, alpha easing out to the edge), drawn scaled as a single quad. */
    private static final class Glow {
        static final int SIZE = 256;
        static final Identifier ID = Identifier.fromNamespaceAndPath("lavavisual", "ui_glow");
        static DynamicTexture texture;
        static boolean failed;
        static boolean ready() {
            if (texture != null) return true;
            if (failed) return false;
            try {
                DynamicTexture created = new DynamicTexture(() -> "lavavisual ui glow", SIZE, SIZE, true);
                NativeImage image = created.getPixels();
                if (image == null) { failed = true; return false; }
                for (int j = 0; j < SIZE; j++) for (int i = 0; i < SIZE; i++) {
                    double dx = (i + 0.5) / SIZE * 2 - 1, dy = (j + 0.5) / SIZE * 2 - 1, d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
                    double a = (1 - d) * (1 - d) * (1 + 2 * d) * (1 - d);
                    image.setPixel(i, j, ((int) Math.round(Math.clamp(a, 0, 1) * 255) << 24) | 0xFFFFFF);
                }
                created.upload();
                Minecraft.getInstance().getTextureManager().register(ID, created);
                texture = created;
                return true;
            } catch (RuntimeException | LinkageError error) {
                failed = true;
                return false;
            }
        }
    }
    /** Soft glow centred at (cx, cy) fading out at radius r (GUI units); one textured quad at any size. */
    public static void glowDisc(GuiGraphicsExtractor g, double cx, double cy, double r, int color) {
        if (r <= 0 || (color >>> 24) == 0 || !Glow.ready()) return;
        int x = (int) Math.round(cx - r), y = (int) Math.round(cy - r), d = Math.max(1, (int) Math.round(2 * r));
        g.blit(RenderPipelines.GUI_TEXTURED, Glow.ID, x, y, 0, 0, d, d, Glow.SIZE, Glow.SIZE, Glow.SIZE, Glow.SIZE, color);
    }

    /** Physical pixels per local unit at the current pose. */
    private static double pixels(GuiGraphicsExtractor g) {
        var m = g.pose();
        return UiFont.guiScale() * Math.sqrt(Math.abs(m.m00() * m.m11() - m.m01() * m.m10()));
    }

    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        if (radius <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        shape(g, x, y, w, h, radius, color, color);
    }

    /** Vertical gradient (ARGB top to bottom) with the same smooth corners as {@link #round}. */
    public static void roundV(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0 || ((top | bottom) >>> 24) == 0) return;
        if (radius <= 0) { if (top == bottom) g.fill(x, y, x + w, y + h, top); else g.fillGradient(x, y, x + w, y + h, top, bottom); return; }
        shape(g, x, y, w, h, radius, top, bottom);
    }

    /** Rounded rectangle in physical pixels; falls back to per-row corners for radii beyond the atlas. */
    private static void shape(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int bottom) {
        double s = pixels(g);
        if (s <= 0.01) return;
        int x0 = (int) Math.round(x * s), y0 = (int) Math.round(y * s), x1 = (int) Math.round((x + w) * s), y1 = (int) Math.round((y + h) * s);
        int pw = x1 - x0, ph = y1 - y0;
        if (pw <= 0 || ph <= 0) return;
        int r = Math.min((int) Math.round(Math.min(radius, Math.min(w, h) / 2.0) * s), Math.min(pw, ph) / 2);
        g.pose().pushMatrix();
        try {
            g.pose().scale((float) (1 / s));
            if (r <= 0) {
                if (top == bottom) g.fill(x0, y0, x1, y1, top); else g.fillGradient(x0, y0, x1, y1, top, bottom);
            } else if (r <= Circles.MAX_R && Circles.ready()) {
                corners(g, x0, y0, x1, y1, r, top, bottom);
            } else {
                rows(g, x0, y0, pw, ph, r, top, bottom);
            }
        } finally { g.pose().popMatrix(); }
    }

    private static void corners(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int top, int bottom) {
        int h = y1 - y0, u = Circles.U[r], v = Circles.V[r];
        int ct = top == bottom ? top : lerpArgb(top, bottom, r * 0.5 / h), cb = top == bottom ? bottom : lerpArgb(top, bottom, 1 - r * 0.5 / h);
        blit(g, x0, y0, u, v, r, ct);
        blit(g, x1 - r, y0, u + r, v, r, ct);
        blit(g, x0, y1 - r, u, v + r, r, cb);
        blit(g, x1 - r, y1 - r, u + r, v + r, r, cb);
        if (top == bottom) {
            if (x1 - x0 > 2 * r) { g.fill(x0 + r, y0, x1 - r, y0 + r, top); g.fill(x0 + r, y1 - r, x1 - r, y1, top); }
            if (h > 2 * r) g.fill(x0, y0 + r, x1, y1 - r, top);
        } else {
            int mt = lerpArgb(top, bottom, (double) r / h), mb = lerpArgb(top, bottom, (double) (h - r) / h);
            if (x1 - x0 > 2 * r) { g.fillGradient(x0 + r, y0, x1 - r, y0 + r, top, mt); g.fillGradient(x0 + r, y1 - r, x1 - r, y1, mb, bottom); }
            if (h > 2 * r) g.fillGradient(x0, y0 + r, x1, y1 - r, mt, mb);
        }
    }

    private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, Circles.ID, x, y, u, v, size, size, size, size, Circles.WIDTH, Circles.HEIGHT, color);
    }

    /** Per-row anti-aliased corners (large radii or when the atlas is unavailable). */
    private static void rows(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (h > 2 * r) {
            if (top == bottom) g.fill(x, y + r, x + w, y + h - r, top);
            else g.fillGradient(x, y + r, x + w, y + h - r, lerpArgb(top, bottom, (double) r / h), lerpArgb(top, bottom, (double) (h - r) / h));
        }
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double boundary = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            int inset = (int) Math.ceil(boundary), bottomRow = y + h - row - 1;
            int ct = top == bottom ? top : lerpArgb(top, bottom, (row + 0.5) / h), cb = top == bottom ? top : lerpArgb(top, bottom, (h - row - 0.5) / h);
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

    /** Smooth filled circle centred at (cx, cy) with radius r (GUI units, may be fractional). */
    public static void circle(GuiGraphicsExtractor g, double cx, double cy, double r, int color) {
        if (r <= 0 || (color >>> 24) == 0) return;
        double s = pixels(g);
        int pr = Math.max(1, (int) Math.round(r * s)), px = (int) Math.round(cx * s) - pr, py = (int) Math.round(cy * s) - pr;
        g.pose().pushMatrix();
        try {
            g.pose().scale((float) (1 / s));
            if (pr <= Circles.MAX_R && Circles.ready())
                g.blit(RenderPipelines.GUI_TEXTURED, Circles.ID, px, py, Circles.U[pr], Circles.V[pr], 2 * pr, 2 * pr, 2 * pr, 2 * pr, Circles.WIDTH, Circles.HEIGHT, color);
            else rows(g, px, py, 2 * pr, 2 * pr, pr, color, color);
        } finally { g.pose().popMatrix(); }
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
