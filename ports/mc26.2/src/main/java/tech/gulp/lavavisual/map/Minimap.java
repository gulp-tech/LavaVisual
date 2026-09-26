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
        long started = System.nanoTime();
        try { update(mc); } finally { tickNanos += System.nanoTime() - started; ticks++; }
    }
    private static long tickNanos, ticks;
    /** CI: average tick cost of the scanner in microseconds since resetCost(). */
    public static double tickMicros() { return ticks == 0 ? 0 : tickNanos / 1000.0 / ticks; }
    private static void update(Minecraft mc) {
        HudConfig c = LavaVisualClient.config();
        if (mc.level == null || mc.player == null) { if (level != null) reset(); return; }
        if (!c.widgets.get("minimap").visible || LavaVisualClient.STATE.hudHidden) return;
        if (mc.level != level) { reset(); level = mc.level; }
        int px = Mth.floor(mc.player.getX()), pz = Mth.floor(mc.player.getZ());
        if (!valid) { originX = px - HALF; originZ = pz - HALF; valid = true; restart(); }
        else if (Math.abs(px - (originX + HALF)) > RECENTER || Math.abs(pz - (originZ + HALF)) > RECENTER) shift(px - HALF - originX, pz - HALF - originZ);
        boolean ceiling = mc.level.dimensionType().hasCeiling();
        // A few map pixels per tick (centre rows first); a standing player's map is refreshed every 10 s, and the
        // texture goes to the GPU at most twice a second (once a second with FPS Boost).
        boolean boost = PerformanceMode.active();
        int budget = boost ? 64 : 128;
        if (ceiling) budget /= 3;
        if (pass >= SIZE) { if (++idle >= 200) restart(); }
        else scan(mc.level, budget, ceiling, Mth.floor(mc.player.getY()));
        uploadTimer++;
        if (dirty && (uploadTimer >= (boost ? 20 : 10) || !uploaded)) upload(mc);
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
        // The round window is cut from the square map with the panel colour, so that panel is opaque.
        double op = round ? 1 : w.opacity;
        int panelTop = round ? UiDraw.alpha(bg, 1) : UiDraw.alpha(UiDraw.mix(bg, 0xFFFFFF, 0.05), op), panelBottom = UiDraw.alpha(bg, op);
        if (c.shadows) {
            UiDraw.round(g, -1, 1, bw + 2, bh + 2, 8, UiDraw.alpha(0, w.opacity * 0.12));
            UiDraw.round(g, 0, 2, bw, bh, 7, UiDraw.alpha(0, w.opacity * 0.22));
        }
        UiDraw.roundV(g, 0, 0, bw, bh, 7, panelTop, panelBottom);
        double cx = m + size / 2.0, cy = m + size / 2.0;
        g.fill(m, m, m + size, m + size, 0xFF0B0D11);
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
        // Window shape, inner shadow, glow and ring come from cached white / black textures tinted when drawn, so
        // rainbow or changed colours never repaint them.
        frame(mc, g, bw, bh, m, size, round, bg, accent, accent2);
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
        arrow(mc, g);
        g.pose().popMatrix();
        if (c.mapCoords) {
            String text = player == null ? "X 0  Y 64  Z 0" : "X " + Mth.floor(px) + "  Y " + Mth.floor(player.getY()) + "  Z " + Mth.floor(pz);
            UiFont.centered(g, font, text, bw / 2, m + size + 4, 0xFFC9CED8, UiFont.Face.SMALL);
        }
    }
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("lavavisual", "minimap_frame"),
            RING = Identifier.fromNamespaceAndPath("lavavisual", "minimap_ring"),
            RING2 = Identifier.fromNamespaceAndPath("lavavisual", "minimap_ring2");
    private static DynamicTexture frameTexture, ringTexture, ring2Texture;
    private static int frameW, frameH, builds;
    private static long frameKey = Long.MIN_VALUE, frameBuilt;
    private static long costNanos, costFrames;
    private static long clockLast, clockSum, clockFrames;
    private static boolean clockOn;
    /** Draw time of the minimap (CI budget check). */
    public static void cost(long nanos) { costNanos += nanos; costFrames++; }
    public static void resetCost() { costNanos = 0; costFrames = 0; tickNanos = 0; ticks = 0; }
    public static double averageMicros() { return costFrames == 0 ? 0 : costNanos / 1000.0 / costFrames; }
    public static long frames() { return costFrames; }
    /** CI: how many times the frame textures were painted (only size or shape changes should do it). */
    public static int builds() { return builds; }
    /** CI frame clock: called once per HUD frame; measures the average time between frames while on. */
    public static void frameClock(long now) {
        if (clockOn && clockLast != 0) { clockSum += now - clockLast; clockFrames++; }
        clockLast = now;
    }
    public static void clock(boolean on) { clockOn = on; clockSum = 0; clockFrames = 0; clockLast = 0; }
    public static double clockMillis() { return clockFrames == 0 ? 0 : clockSum / 1e6 / clockFrames; }
    public static long clockFrames() { return clockFrames; }
    private static double pixels(GuiGraphicsExtractor g) {
        var p = g.pose();
        return UiFont.guiScale() * Math.sqrt(Math.abs(p.m00() * p.m11() - p.m01() * p.m10()));
    }
    /**
     * Three textures that never depend on colours: the window mask (white, tinted with the panel colour) with the
     * inner shadow (black), and the ring in two layers tinted with the two accent colours. The alphas are split so
     * that the two tinted layers reproduce the top-to-bottom gradient exactly. Repainted only when the size or the
     * shape changes (at most four times a second while a HUD animation scales it).
     */
    private static void frame(Minecraft mc, GuiGraphicsExtractor g, int bw, int bh, int m, int size, boolean round, int bg, int accent, int accent2) {
        double s = pixels(g);
        int pw = (int) Math.round(bw * s), ph = (int) Math.round(bh * s);
        if (pw < 8 || ph < 8 || pw > 2048 || ph > 2048) return;
        long key = ((long) pw * 4099 + ph) * 31 + (round ? 1 : 0);
        long now = System.nanoTime();
        if (key != frameKey && (frameTexture == null || now - frameBuilt > 250_000_000L)) {
            if (frameTexture == null || frameW != pw || frameH != ph) {
                frameTexture = new DynamicTexture(() -> "lavavisual minimap frame", pw, ph, true);
                ringTexture = new DynamicTexture(() -> "lavavisual minimap ring", pw, ph, true);
                ring2Texture = new DynamicTexture(() -> "lavavisual minimap ring 2", pw, ph, true);
                var textures = mc.getTextureManager();
                textures.register(FRAME, frameTexture); // replaces (and closes) the previous ones
                textures.register(RING, ringTexture);
                textures.register(RING2, ring2Texture);
                frameW = pw; frameH = ph;
            }
            NativeImage base = frameTexture.getPixels(), ring = ringTexture.getPixels(), ring2 = ring2Texture.getPixels();
            if (base == null || ring == null || ring2 == null) return;
            paintFrame(base, ring, ring2, pw, ph, s, m, size, round);
            frameTexture.upload();
            ringTexture.upload();
            ring2Texture.upload();
            frameKey = key;
            frameBuilt = now;
            builds++;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, 0f, 0f, bw, bh, frameW, frameH, frameW, frameH, 0xFF000000 | bg);
        g.blit(RenderPipelines.GUI_TEXTURED, RING, 0, 0, 0f, 0f, bw, bh, frameW, frameH, frameW, frameH, 0xFF000000 | accent);
        g.blit(RenderPipelines.GUI_TEXTURED, RING2, 0, 0, 0f, 0f, bw, bh, frameW, frameH, frameW, frameH, 0xFF000000 | accent2);
    }
    private static void paintFrame(NativeImage base, NativeImage ring, NativeImage ring2, int pw, int ph, double s, int m, int size, boolean round) {
        double cx = m + size / 2.0, cy = m + size / 2.0, r = size / 2.0, corner = 6;
        for (int py = 0; py < ph; py++) {
            double y = (py + 0.5) / s, t = Math.clamp((y - m) / size, 0, 1);
            for (int px = 0; px < pw; px++) {
                double x = (px + 0.5) / s;
                boolean inside = x >= m && x < m + size && y >= m && y < m + size;
                int out = 0;
                double a = 0; // ring opacity (glow and line)
                if (round) {
                    double d = Math.hypot(x - cx, y - cy);
                    if (inside) {
                        double cover = Math.clamp((d - r) * s + 0.5, 0, 1);
                        if (d < r) { double v = Math.clamp((d - (r - 7)) / 7, 0, 1); out = over(out, (int) Math.round(0.24 * v * v * 255) << 24); }
                        if (cover > 0) out = over(out, (int) Math.round(cover * 255) << 24 | 0xFFFFFF);
                    }
                    if (d > r - 1 && d < r + 3.4) { double k = Math.clamp((d - r) / 3.4, 0, 1); a = 0.2 * (1 - k) * (1 - k); }
                    double edge = Math.abs(d - (r + 0.2)) - 0.7, line = Math.clamp(0.5 - edge * s, 0, 1);
                    if (line > 0) a = a + 0.95 * line * (1 - a);
                } else {
                    double dx = Math.max(Math.abs(x - cx) - (size / 2.0 - corner), 0), dy = Math.max(Math.abs(y - cy) - (size / 2.0 - corner), 0);
                    double d = Math.hypot(dx, dy) - corner;
                    if (inside) { double cover = Math.clamp(d * s + 0.5, 0, 1); if (cover > 0) out = over(out, (int) Math.round(cover * 255) << 24 | 0xFFFFFF); }
                    double edge = Math.abs(d + 0.1) - 0.55, line = Math.clamp(0.5 - edge * s, 0, 1);
                    if (line > 0) a = 0.85 * line;
                }
                base.setPixel(px, py, out);
                // Layer 1 (accent) under layer 2 (accent2 with opacity a*t): together opacity a, colour mixed by t.
                double a2 = a * t, a1 = a2 >= 0.999 ? 0 : a * (1 - t) / (1 - a2);
                ring.setPixel(px, py, (int) Math.round(Math.clamp(a1, 0, 1) * 255) << 24 | 0xFFFFFF);
                ring2.setPixel(px, py, (int) Math.round(Math.clamp(a2, 0, 1) * 255) << 24 | 0xFFFFFF);
            }
        }
    }
    /** Source-over for straight-alpha ARGB. */
    private static int over(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        if (sa == 255 || da == 0) return src;
        double as = sa / 255.0, ad = da / 255.0 * (1 - as), a = as + ad;
        int r = (int) Math.round(((src >> 16 & 255) * as + (dst >> 16 & 255) * ad) / a);
        int gg = (int) Math.round(((src >> 8 & 255) * as + (dst >> 8 & 255) * ad) / a);
        int b = (int) Math.round(((src & 255) * as + (dst & 255) * ad) / a);
        return (int) Math.round(a * 255) << 24 | r << 16 | gg << 8 | b;
    }
    private static final Identifier ARROW = Identifier.fromNamespaceAndPath("lavavisual", "minimap_arrow");
    private static final double[][] ARROW_SHAPE = {{0, -6.6}, {4.9, 5.2}, {0, 2.5}, {-4.9, 5.2}};
    private static DynamicTexture arrowTexture;
    private static int arrowSize;
    /** Player arrow: one cached, anti-aliased 16x18 texture (white with a soft dark rim), drawn rotated in one quad. */
    private static void arrow(Minecraft mc, GuiGraphicsExtractor g) {
        double s = pixels(g);
        int pw = (int) Math.round(16 * s), ph = (int) Math.round(18 * s);
        if (pw < 4 || pw > 512) return;
        if (arrowTexture == null || arrowSize != pw) {
            arrowTexture = new DynamicTexture(() -> "lavavisual minimap arrow", pw, ph, true);
            mc.getTextureManager().register(ARROW, arrowTexture);
            arrowSize = pw;
            NativeImage image = arrowTexture.getPixels();
            if (image == null) return;
            for (int py = 0; py < ph; py++) for (int px = 0; px < pw; px++) {
                int rim = 0, body = 0;
                for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                    double x = (px + (sx + 0.5) / 4) / s - 8, y = (py + (sy + 0.5) / 4) / s - 10;
                    if (insideArrow(x / 1.45, y / 1.45)) rim++;
                    if (insideArrow(x, y)) body++;
                }
                int out = over(0, (int) Math.round(0x90 * rim / 16.0) << 24);
                out = over(out, (int) Math.round(255 * body / 16.0) << 24 | 0xFFFFFF);
                image.setPixel(px, py, out);
            }
            arrowTexture.upload();
        }
        g.blit(RenderPipelines.GUI_TEXTURED, ARROW, -8, -10, 0f, 0f, 16, 18, pw, ph, pw, ph, 0xFFFFFFFF);
    }
    private static boolean insideArrow(double x, double y) {
        boolean in = false;
        for (int i = 0, j = ARROW_SHAPE.length - 1; i < ARROW_SHAPE.length; j = i++) {
            double[] a = ARROW_SHAPE[i], b = ARROW_SHAPE[j];
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
        }
        return in;
    }
}
