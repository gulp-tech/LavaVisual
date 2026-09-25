package tech.gulp.lavavisual.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.effects.PerformanceMode;
import tech.gulp.lavavisual.ui.UiDraw;
import tech.gulp.lavavisual.ui.UiFont;

/**
 * Terrain-only minimap: top-block map colours with vanilla map shading, no entities or players.
 * A 128x128 cache is refreshed a few rows per tick (centre first) and uploaded at most 5 times per second.
 */
public final class Minimap {
    private Minimap() { }
    public static final int SIZE = 128, HALF = 64, RECENTER = 16;
    public static final int[] VIEW = {48, 64, 96};
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("lavavisual", "minimap");
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int[] PIXELS = new int[SIZE * SIZE], HEIGHTS = new int[SIZE * SIZE];
    private static final int[] SCRATCH_P = new int[SIZE * SIZE], SCRATCH_H = new int[SIZE * SIZE];
    private static final int[] ORDER = new int[SIZE];
    static {
        for (int i = 0; i < SIZE; i++) ORDER[i] = HALF + ((i & 1) == 0 ? i / 2 : -(i + 1) / 2);
        Arrays.fill(HEIGHTS, UNKNOWN);
    }
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static DynamicTexture texture;
    private static boolean valid, dirty, uploaded;
    private static int originX, originZ, pass = SIZE, column, idle, uploadTimer;
    private static Object level;

    public static void reset() {
        Arrays.fill(PIXELS, 0); Arrays.fill(HEIGHTS, UNKNOWN);
        valid = false; dirty = true; pass = SIZE; column = 0; idle = 0; level = null; uploaded = false;
    }
    private static void restart() { pass = 0; column = 0; idle = 0; }

    public static void tick(Minecraft mc) {
        HudConfig c = LavaVisualClient.config();
        if (mc.level == null || mc.player == null) { if (level != null) reset(); return; }
        if (!c.widgets.get("minimap").visible || LavaVisualClient.STATE.hudHidden) return;
        if (mc.level != level) { reset(); level = mc.level; }
        int px = Mth.floor(mc.player.getX()), pz = Mth.floor(mc.player.getZ());
        if (!valid) { originX = px - HALF; originZ = pz - HALF; valid = true; restart(); }
        else if (Math.abs(px - (originX + HALF)) > RECENTER || Math.abs(pz - (originZ + HALF)) > RECENTER) shift(px - HALF - originX, pz - HALF - originZ);
        boolean ceiling = mc.level.dimensionType().hasCeiling();
        int budget = PerformanceMode.active() ? 128 : 256;
        if (ceiling) budget /= 3;
        if (pass >= SIZE) { if (++idle >= 40) restart(); }
        else scan(mc.level, budget, ceiling, Mth.floor(mc.player.getY()));
        uploadTimer++;
        if (dirty && (uploadTimer >= 4 || !uploaded)) upload(mc);
    }
    private static void shift(int dx, int dz) {
        Arrays.fill(SCRATCH_P, 0); Arrays.fill(SCRATCH_H, UNKNOWN);
        for (int row = 0; row < SIZE; row++) {
            int from = row + dz;
            if (from < 0 || from >= SIZE) continue;
            int start = Math.max(0, -dx), end = Math.min(SIZE, SIZE - dx);
            if (end <= start) continue;
            System.arraycopy(PIXELS, from * SIZE + start + dx, SCRATCH_P, row * SIZE + start, end - start);
            System.arraycopy(HEIGHTS, from * SIZE + start + dx, SCRATCH_H, row * SIZE + start, end - start);
        }
        System.arraycopy(SCRATCH_P, 0, PIXELS, 0, PIXELS.length);
        System.arraycopy(SCRATCH_H, 0, HEIGHTS, 0, HEIGHTS.length);
        originX += dx; originZ += dz; dirty = true;
        restart();
    }
    private static void scan(ClientLevel world, int budget, boolean ceiling, int playerY) {
        int minY = world.getMinY(), maxY = minY + world.dimensionType().logicalHeight() - 1;
        while (budget-- > 0 && pass < SIZE) {
            int row = ORDER[pass], x = originX + column, z = originZ + row, index = row * SIZE + column;
            if (world.hasChunk(x >> 4, z >> 4)) {
                int color = ceiling ? cave(world, x, z, Math.min(playerY + 1, maxY), minY, playerY, index) : surface(world, x, z, minY, row, index);
                if (PIXELS[index] != color) { PIXELS[index] = color; dirty = true; }
            }
            if (++column >= SIZE) { column = 0; pass++; }
        }
    }
    /** Vanilla map rules at 1:1: first block with a map colour; water by depth, land by the slope to the north. */
    private static int surface(ClientLevel world, int x, int z, int minY, int row, int index) {
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = null;
        MapColor color = MapColor.NONE;
        for (int limit = 0; y >= minY && limit < 24; limit++, y--) {
            state = world.getBlockState(POS.set(x, y, z));
            color = state.getMapColor(world, POS);
            if (color != MapColor.NONE) break;
        }
        if (color == MapColor.NONE || state == null) { HEIGHTS[index] = UNKNOWN; return 0; }
        MapColor.Brightness brightness;
        if (color == MapColor.WATER && !state.getFluidState().isEmpty()) {
            int depth = 0;
            for (int yy = y - 1; depth < 12 && yy >= minY && !world.getBlockState(POS.set(x, yy, z)).getFluidState().isEmpty(); yy--) depth++;
            double f = depth * 0.1 + ((x + z) & 1) * 0.2;
            brightness = f < 0.5 ? MapColor.Brightness.HIGH : f > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        } else {
            int north = row > 0 ? HEIGHTS[index - SIZE] : UNKNOWN;
            double d = north == UNKNOWN ? 0 : (y - north) + (((x + z) & 1) - 0.5) * 0.4;
            brightness = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        HEIGHTS[index] = y;
        return color.calculateARGBColor(brightness);
    }
    /** Dimensions with a ceiling (Nether): the floor below your level, walls at head height darkened. Bounded depth. */
    private static int cave(ClientLevel world, int x, int z, int top, int minY, int playerY, int index) {
        BlockState state = world.getBlockState(POS.set(x, top, z));
        MapColor color = state.getMapColor(world, POS);
        if (color != MapColor.NONE && !state.isAir()) { HEIGHTS[index] = top; return color.calculateARGBColor(MapColor.Brightness.LOWEST); }
        for (int y = top - 1, limit = 0; y >= minY && limit < 40; y--, limit++) {
            state = world.getBlockState(POS.set(x, y, z));
            color = state.getMapColor(world, POS);
            if (color == MapColor.NONE) continue;
            HEIGHTS[index] = y;
            MapColor.Brightness brightness = y >= playerY - 2 ? MapColor.Brightness.HIGH : y >= playerY - 9 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
            return color.calculateARGBColor(brightness);
        }
        HEIGHTS[index] = UNKNOWN;
        return 0;
    }
    private static void upload(Minecraft mc) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "lavavisual minimap", SIZE, SIZE, true);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int i = 0; i < PIXELS.length; i++) image.setPixel(i & (SIZE - 1), i >> 7, PIXELS[i]);
        texture.upload();
        dirty = false; uploaded = true; uploadTimer = 0;
    }

    public static int baseWidth() { return 104; }
    public static int baseHeight() { return LavaVisualClient.config().mapCoords ? 117 : 104; }

    /** Draws at the widget origin in widget units. Round window by default (square optional); no letters. */
    public static void draw(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, float partial, boolean edit) {
        Font font = mc.font;
        int bw = baseWidth(), bh = baseHeight(), m = 4, size = 96;
        int bg = c.color("hud_bg") & 0xFFFFFF;
        int accent2 = c.color2("minimap");
        boolean round = c.mapShape == 0;
        // The round window is cut from the square map with the panel colour, so that panel stays (almost) opaque.
        double op = round ? 1 : w.opacity;
        int panelTop = UiDraw.alpha(UiDraw.mix(bg, 0xFFFFFF, 0.05), op), panelBottom = UiDraw.alpha(bg, op);
        int maskTop = UiDraw.alpha(UiDraw.mix(bg, 0xFFFFFF, 0.05), 1), maskBottom = UiDraw.alpha(bg, 1);
        if (c.shadows) {
            UiDraw.round(g, -1, 1, bw + 2, bh + 2, 8, UiDraw.alpha(0, w.opacity * 0.12));
            UiDraw.round(g, 0, 2, bw, bh, 7, UiDraw.alpha(0, w.opacity * 0.22));
        }
        UiDraw.roundV(g, 0, 0, bw, bh, 7, panelTop, panelBottom);
        double cx = m + size / 2.0, cy = m + size / 2.0, r = size / 2.0;
        if (round) UiDraw.circle(g, cx, cy, r, 0xFF0B0D11);
        else UiDraw.round(g, m, m, size, size, 6, 0xFF0B0D11);
        var player = mc.player;
        boolean live = player != null && mc.level != null && uploaded && valid && texture != null;
        double px = 0, pz = 0, view = VIEW[Math.floorMod(c.mapZoom, 3)];
        if (player != null) {
            var position = player.getPosition(partial);
            px = position.x; pz = position.z;
        }
        if (live) {
            float u = (float) (px - originX - view / 2), v = (float) (pz - originZ - view / 2);
            g.enableScissor(m, m, m + size, m + size);
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, m, m, u, v, size, size, (int) view, (int) view, SIZE, SIZE, 0xFFFFFFFF);
            g.disableScissor();
        } else if (!edit) UiFont.centered(g, font, "загрузка", m + size / 2, m + size / 2 - 4, 0xFF8C93A1, UiFont.Face.SMALL);
        // Cut the square texture to the window shape with the panel colour, then the soft vignette, glow and ring.
        if (round) {
            ring(g, cx, cy, r, r * 1.5, m, m, m + size, m + size, maskTop, maskBottom, 0, bh);
            ring(g, cx, cy, r - 7, r, m, m, m + size, m + size, 0x00000000, 0x00000000, 0, 0, 0x38000000);
            ring(g, cx, cy, r + 0.6, r + 2.6, -2, -2, bw + 2, bh + 2, UiDraw.alpha(accent, 0.16), UiDraw.alpha(accent2, 0.16), m, m + size);
            ring(g, cx, cy, r - 0.5, r + 0.9, -2, -2, bw + 2, bh + 2, UiDraw.alpha(accent, 0.95), UiDraw.alpha(accent2, 0.95), m, m + size);
        } else {
            corners(g, m, m, size, 6, maskTop, maskBottom, bh);
            UiDraw.roundV(g, m - 1, m - 1, size + 2, 1, 0, UiDraw.alpha(accent, 0.9), UiDraw.alpha(accent, 0.9));
        }
        double scale = size / view;
        if (c.mapWaypoints && player != null && mc.level != null) {
            for (Waypoints.Point p : Waypoints.here(mc)) {
                if (!p.visible) continue;
                double dx = (p.x + 0.5 - px) * scale, dz = (p.z + 0.5 - pz) * scale;
                double limit = size / 2.0 - 5, out = round ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz));
                boolean edge = out > limit;
                if (edge) { dx *= limit / out; dz *= limit / out; }
                double wx = cx + dx, wz = cy + dz;
                UiDraw.circle(g, wx, wz, edge ? 3 : 3.8, 0xC0000000);
                UiDraw.circle(g, wx, wz, edge ? 2.2 : 3, 0xFF000000 | p.color);
                if (!edge) UiDraw.circle(g, wx, wz, 1.1, 0xFFFFFFFF);
            }
        }
        float yaw = player == null ? 180 : Mth.lerp(partial, player.yRotO, player.getYRot());
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) cy);
        g.pose().rotate((float) Math.toRadians(yaw - 180));
        arrow(g, 1.45, 0x90000000);
        arrow(g, 1.0, 0xFFFFFFFF);
        g.pose().popMatrix();
        if (c.mapCoords) {
            String text = player == null ? "X 0  Y 64  Z 0" : "X " + Mth.floor(px) + "  Y " + Mth.floor(player.getY()) + "  Z " + Mth.floor(pz);
            UiFont.centered(g, font, text, bw / 2, m + size + 4, 0xFFC9CED8, UiFont.Face.SMALL);
        }
    }
    private static double pixels(GuiGraphicsExtractor g) {
        var p = g.pose();
        return UiFont.guiScale() * Math.sqrt(Math.abs(p.m00() * p.m11() - p.m01() * p.m10()));
    }
    /**
     * Paints, one physical pixel row at a time, the part of the box (x0..x1, y0..y1) between radius r0 and r1 around
     * (cx, cy), with anti-aliased ends. Colour runs top -> bottom between gy0 and gy1. With a vignette colour the band is
     * split into rings that darken towards r1 (inner shadow at the window edge).
     */
    private static void ring(GuiGraphicsExtractor g, double cx, double cy, double r0, double r1, double x0, double y0, double x1, double y1,
                             int top, int bottom, double gy0, double gy1) { ring(g, cx, cy, r0, r1, x0, y0, x1, y1, top, bottom, gy0, gy1, 0); }
    private static void ring(GuiGraphicsExtractor g, double cx, double cy, double r0, double r1, double x0, double y0, double x1, double y1,
                             int top, int bottom, double gy0, double gy1, int vignette) {
        double s = pixels(g);
        if (s <= 0.01) return;
        g.pose().pushMatrix();
        try {
            g.pose().scale((float) (1 / s));
            int rowFrom = (int) Math.floor(Math.max(y0, cy - r1) * s), rowTo = (int) Math.ceil(Math.min(y1, cy + r1) * s);
            int steps = vignette != 0 ? 6 : 1;
            for (int row = rowFrom; row < rowTo; row++) {
                double y = (row + 0.5) / s, dy = y - cy;
                if (Math.abs(dy) >= r1) continue;
                for (int k = 0; k < steps; k++) {
                    // Vignette: concentric bands, darker towards the outer edge.
                    double a0 = vignette != 0 ? r0 + (r1 - r0) * k / steps : r0, a1 = vignette != 0 ? r0 + (r1 - r0) * (k + 1) / steps : r1;
                    int color = vignette != 0 ? UiDraw.fade(vignette, (k + 1.0) / steps) : gy1 > gy0 ? UiDraw.lerpArgb(top, bottom, Math.clamp((y - gy0) / (gy1 - gy0), 0, 1)) : top;
                    if ((color >>> 24) == 0) continue;
                    double outer = Math.sqrt(Math.max(0, a1 * a1 - dy * dy)), inner = Math.abs(dy) < a0 ? Math.sqrt(a0 * a0 - dy * dy) : 0;
                    if (inner <= 0) span(g, s, cx - outer, cx + outer, x0, x1, row, color);
                    else { span(g, s, cx - outer, cx - inner, x0, x1, row, color); span(g, s, cx + inner, cx + outer, x0, x1, row, color); }
                }
            }
        } finally { g.pose().popMatrix(); }
    }
    /** One row segment [a, b] (GUI units) clipped to [x0, x1], in physical pixels with fractional end coverage. */
    private static void span(GuiGraphicsExtractor g, double s, double a, double b, double x0, double x1, int row, int color) {
        a = Math.max(a, x0) * s; b = Math.min(b, x1) * s;
        if (b <= a) return;
        int ia = (int) Math.ceil(a), ib = (int) Math.floor(b);
        if (ib > ia) g.fill(ia, row, ib, row + 1, color);
        if (ia > a && ia - 1 >= Math.floor(a)) g.fill(ia - 1, row, ia, row + 1, UiDraw.fade(color, Math.min(1, ia - a)));
        if (b > ib && ib >= ia) g.fill(ib, row, ib + 1, row + 1, UiDraw.fade(color, Math.min(1, b - ib)));
    }
    /** Rounds the square window's corners with the panel colour. */
    private static void corners(GuiGraphicsExtractor g, int x, int y, int size, int radius, int top, int bottom, int panelHeight) {
        double[][] centres = {{x + radius, y + radius, x, y}, {x + size - radius, y + radius, x + size - radius, y},
                {x + radius, y + size - radius, x, y + size - radius}, {x + size - radius, y + size - radius, x + size - radius, y + size - radius}};
        for (double[] c : centres) ring(g, c[0], c[1], radius, radius * 1.5, c[2], c[3], c[2] + radius, c[3] + radius, top, bottom, 0, panelHeight);
    }
    /** Smooth navigation arrow (pointing up), scan-converted in physical pixels in the current rotated frame. */
    private static void arrow(GuiGraphicsExtractor g, double grow, int color) {
        double[][] shape = {{0, -6.6}, {4.9, 5.2}, {0, 2.5}, {-4.9, 5.2}};
        double s = pixels(g);
        if (s <= 0.01) return;
        g.pose().pushMatrix();
        try {
            g.pose().scale((float) (1 / s));
            int rowFrom = (int) Math.floor(-6.6 * grow * s), rowTo = (int) Math.ceil(5.2 * grow * s);
            double[] xs = new double[4];
            for (int row = rowFrom; row < rowTo; row++) {
                double y = (row + 0.5) / s / grow;
                int count = 0;
                for (int i = 0; i < 4; i++) {
                    double[] p0 = shape[i], p1 = shape[(i + 1) % 4];
                    if ((p0[1] <= y) != (p1[1] <= y)) xs[count++] = p0[0] + (y - p0[1]) / (p1[1] - p0[1]) * (p1[0] - p0[0]);
                }
                java.util.Arrays.sort(xs, 0, count);
                for (int i = 0; i + 1 < count; i += 2) span(g, s, xs[i] * grow, xs[i + 1] * grow, -100, 100, row, color);
            }
        } finally { g.pose().popMatrix(); }
    }
}
