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

    /** Draws at the widget origin in widget units. */
    public static void draw(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, float partial, boolean edit) {
        Font font = mc.font;
        int bw = baseWidth(), bh = baseHeight(), m = 4, size = 96;
        int bg = c.color("hud_bg") & 0xFFFFFF;
        if (c.shadows) UiDraw.round(g, 1, 2, bw, bh, 7, UiDraw.alpha(0, w.opacity * 0.28));
        UiDraw.round(g, 0, 0, bw, bh, 7, UiDraw.alpha(bg, w.opacity));
        UiDraw.round(g, m - 1, m - 1, size + 2, size + 2, 3, UiDraw.alpha(accent, 0.35));
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
        } else {
            for (int i = 1; i < 4; i++) {
                g.fill(m + i * size / 4, m, m + i * size / 4 + 1, m + size, 0x14FFFFFF);
                g.fill(m, m + i * size / 4, m + size, m + i * size / 4 + 1, 0x14FFFFFF);
            }
            if (!edit) UiFont.centered(g, font, "загрузка", m + size / 2, m + size / 2 + 12, 0xFF8C93A1, UiFont.Face.SMALL);
        }
        double scale = size / view;
        int cx = m + size / 2, cy = m + size / 2;
        if (c.mapWaypoints && player != null && mc.level != null) {
            for (Waypoints.Point p : Waypoints.here(mc)) {
                if (!p.visible) continue;
                double dx = (p.x + 0.5 - px) * scale, dz = (p.z + 0.5 - pz) * scale;
                double limit = size / 2.0 - 4, out = Math.max(Math.abs(dx), Math.abs(dz));
                boolean edge = out > limit;
                if (edge) { dx *= limit / out; dz *= limit / out; }
                int wx = cx + (int) Math.round(dx), wz = cy + (int) Math.round(dz), r = edge ? 2 : 3;
                g.fill(wx - r - 1, wz - r - 1, wx + r + 1, wz + r + 1, 0xC0000000);
                g.fill(wx - r, wz - r, wx + r, wz + r, 0xFF000000 | p.color);
                if (!edge) g.fill(wx - 1, wz - 1, wx + 1, wz + 1, 0xFFFFFFFF);
            }
        }
        UiDraw.round(g, cx - 5, m + 1, 10, 9, 3, 0xB0000000);
        UiFont.centered(g, font, "С", cx, m + 2, 0xFFFF6B6B, UiFont.Face.SMALL);
        float yaw = player == null ? 180 : Mth.lerp(partial, player.yRotO, player.getYRot());
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate((float) Math.toRadians(yaw - 180));
        arrow(g, 0xE0000000, 1);
        arrow(g, 0xFFFFFFFF, 0);
        g.pose().popMatrix();
        if (c.mapCoords) {
            String text = player == null ? "X 0  Y 64  Z 0" : "X " + Mth.floor(px) + "  Y " + Mth.floor(player.getY()) + "  Z " + Mth.floor(pz);
            UiFont.centered(g, font, text, bw / 2, m + size + 4, 0xFFE8EAF0, UiFont.Face.SMALL);
        }
    }
    /** Arrow pointing up, built from horizontal strips so it rotates cleanly. */
    private static void arrow(GuiGraphicsExtractor g, int color, int grow) {
        int[][] rows = {{-6, 0, 1}, {-5, -1, 2}, {-4, -1, 2}, {-3, -2, 3}, {-2, -2, 3}, {-1, -3, 4}, {0, -3, 4}, {1, -4, 5}, {2, -4, -1}, {2, 2, 5}, {3, -4, -2}, {3, 3, 5}};
        for (int[] r : rows) g.fill(r[1] - grow, r[0] - grow, r[2] + grow, r[0] + 1 + grow, color);
    }
}
