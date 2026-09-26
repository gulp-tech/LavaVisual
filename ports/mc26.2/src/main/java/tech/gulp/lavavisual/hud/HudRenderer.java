package tech.gulp.lavavisual.hud;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.effects.WorldCosmetics;
import tech.gulp.lavavisual.ui.Icons;
import tech.gulp.lavavisual.ui.UiDraw;
import tech.gulp.lavavisual.ui.UiFont;
import tech.gulp.lavavisual.ui.UiFont.Face;

public final class HudRenderer {
    private HudRenderer() { }
    private static final int TEXT = 0xF1F3F7, MUTED = 0x8C93A1;
    private static int PANEL = 0x111216;
    /** Partial tick of the current HUD frame (set by the HUD element callback). */
    public static float partial = 1;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_SPRITES = {"container/slot/helmet", "container/slot/chestplate", "container/slot/leggings", "container/slot/boots"};

    public static int baseWidth(String id) {
        int previous = UiFont.use(LavaVisualClient.config().hudFont);
        try { return widthOf(id); } finally { UiFont.use(previous); }
    }
    private static int widthOf(String id) {
        return switch (id) {
            case "target" -> 180;
            case "keys" -> 76;
            case "armor" -> 97;
            case "watermark" -> watermarkWidth(2 * UiFont.guiScale());
            case "minimap" -> tech.gulp.lavavisual.map.Minimap.baseWidth();
            case "music" -> 184;
            default -> 156;
        };
    }
    public static int baseHeight(String id) {
        return switch (id) {
            case "target" -> 80;
            case "keys" -> 92;
            case "armor" -> 36;
            case "watermark" -> 20;
            case "minimap" -> tech.gulp.lavavisual.map.Minimap.baseHeight();
            case "music" -> 70;
            default -> 30;
        };
    }
    /** Widget scale rounded to whole or half screen pixels per unit, so HUD text stays sharp. */
    public static double scale(HudConfig.Widget w) { return UiFont.crisp(w.scale); }
    /** Nearest crisp scale (see {@link UiFont#crisp}); the editor stores it so the shown % matches. */
    public static double snapScale(double scale) { return UiFont.crisp(scale); }
    public static int width(String id, HudConfig.Widget w) { return (int) Math.ceil(baseWidth(id) * scale(w)); }
    public static int height(String id, HudConfig.Widget w) { return (int) Math.ceil(baseHeight(id) * scale(w)); }
    public static int x(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.x * Math.max(0, screen - width(id, w))); }
    public static int y(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.y * Math.max(0, screen - height(id, w))); }
    public static String title(String id) {
        return switch (id) {
            case "coordinates" -> "Координаты";
            case "performance" -> "FPS";
            case "target" -> "Target HUD";
            case "keys" -> "Клавиши";
            case "armor" -> "Броня";
            case "totems" -> "Тотемы";
            case "watermark" -> "Водянка";
            case "minimap" -> "Миникарта";
            case "music" -> "Музыка";
            default -> "HUD";
        };
    }
    private static double targetAnim, healthShown, healthGhost;
    private static long lastNs;
    private static TargetSnapshot lastTarget;
    private static String lastName = "";
    private record HudParticle(double x, double y, double vx, double vy, long born, int color) { }
    private static final ArrayList<HudParticle> HUD_PARTICLES = new ArrayList<>();
    private static float lastHealth = -1;
    private static final double[] KEY_PRESS = new double[7];

    /** Draws every HUD element in the chosen HUD font (Montserrat by default); menus keep Inter. */
    public static void draw(GuiGraphicsExtractor g, boolean edit, String selected) {
        int previous = UiFont.use(LavaVisualClient.config().hudFont);
        try { drawAll(g, edit, selected); } finally { UiFont.use(previous); }
    }
    private static void drawAll(GuiGraphicsExtractor g, boolean edit, String selected) {
        HudConfig c = LavaVisualClient.config();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNs) / 1e9); lastNs = now;
        Minecraft mc = Minecraft.getInstance();
        PANEL = c.color("hud_bg") & 0xFFFFFF;
        if (!edit && (mc.player == null || mc.gui.screen() instanceof tech.gulp.lavavisual.ui.HudEditorScreen)) return;
        if (!edit) {
            toast(g, mc);
            if (LavaVisualClient.STATE.hudHidden) return;
            tech.gulp.lavavisual.map.WaypointOverlay.draw(g, mc);
        }
        for (String id : HudConfig.IDS) {
            HudConfig.Widget w = c.widgets.get(id);
            if (!w.visible && !edit) continue;
            if (id.equals("music") && !edit && c.musicHudAuto && !tech.gulp.lavavisual.audio.MusicPlayer.active()) continue;
            TargetSnapshot target = TargetSnapshot.current;
            double fade = 1, scaleAnim = 1;
            if (id.equals("target") && !edit) {
                if (target != null) lastTarget = target; else target = lastTarget;
                double goal = TargetSnapshot.current == null ? 0 : 1;
                targetAnim += (goal - targetAnim) * (c.animations ? Math.min(1, dt * 9) : 1);
                if (goal == 0 && (targetAnim <= 0.03 || target == null)) { targetAnim = 0; lastTarget = null; continue; }
                fade = Math.clamp(targetAnim, 0, 1);
                scaleAnim = 0.72 + 0.28 * (1 - Math.pow(1 - fade, 3));
            }
            int x = x(id, w, g.guiWidth()), y = y(id, w, g.guiHeight());
            int bw = baseWidth(id), bh = baseHeight(id), accent = c.color(id), accent2 = c.color2(id);
            boolean off = edit && !w.visible;
            g.pose().pushMatrix();
            try {
                g.pose().translate(x, y);
                g.pose().scale((float) scale(w));
                if (scaleAnim != 1) {
                    g.pose().translate(bw / 2f, bh / 2f);
                    g.pose().scale((float) scaleAnim);
                    g.pose().translate(-bw / 2f, -bh / 2f);
                }
                if (edit && id.equals(selected)) g.outline(-3, -3, bw + 6, bh + 6, UiDraw.alpha(accent, 0.9));
                switch (id) {
                    case "target" -> target(g, mc, c, w, target, fade, accent, accent2, dt);
                    case "keys" -> keys(g, mc, c, w, accent, accent2, dt);
                    case "armor" -> armor(g, mc, c, w, accent, accent2);
                    case "watermark" -> watermark(g, mc, c, w, accent, accent2);
                    case "minimap" -> {
                        long started = System.nanoTime();
                        tech.gulp.lavavisual.map.Minimap.draw(g, mc, c, w, accent, partial, edit);
                        tech.gulp.lavavisual.map.Minimap.cost(System.nanoTime() - started);
                    }
                    case "music" -> music(g, mc, c, w, accent, accent2);
                    default -> info(g, mc, c, w, id, accent, accent2);
                }
                if (off) {
                    UiDraw.round(g, 0, -12, 26, 10, 3, 0xCC111216);
                    UiFont.text(g, mc.font, "выкл", 4, -12, 0xFF9AA0AC, 22, Face.SMALL);
                }
            } finally { g.pose().popMatrix(); }
        }
    }

    private static float discAngle;
    private static long discLast;
    /** Music HUD: cover (or a spinning disc), title, artist, progress with times, and the previous / next track. */
    private static void music(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, int accent2) {
        int bw = baseWidth("music"), bh = baseHeight("music");
        panel(g, c, 0, 0, bw, bh, 8, w.opacity, accent, accent2);
        var track = tech.gulp.lavavisual.audio.MusicPlayer.current();
        long now = System.nanoTime();
        double dt = discLast == 0 ? 0 : Math.min(0.1, (now - discLast) / 1e9);
        discLast = now;
        if (tech.gulp.lavavisual.audio.MusicPlayer.playing()) discAngle = (float) ((discAngle + dt * 90) % 360);
        int cs = 44, cx = 8, cy = 8;
        var cover = tech.gulp.lavavisual.audio.Covers.get(mc, track);
        if (cover != null) {
            UiDraw.round(g, cx - 1, cy - 1, cs + 2, cs + 2, 6, 0x70000000);
            int n = tech.gulp.lavavisual.audio.Covers.SIZE;
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, cover, cx, cy, 0f, 0f, cs, cs, n, n, n, n, 0xFFFFFFFF);
        } else disc(g, cx + cs / 2.0, cy + cs / 2.0, cs / 2.0, discAngle, accent, accent2);
        int tx = cx + cs + 8, tw = bw - tx - 8;
        net.minecraft.client.gui.Font font = mc.font;
        String title = track == null ? "Музыка" : track.shown();
        UiFont.text(g, font, title, tx, 8, 0xFFF2F4F8, tw, Face.BOLD);
        String state = tech.gulp.lavavisual.audio.MusicPlayer.paused() ? "пауза" : tech.gulp.lavavisual.audio.MusicPlayer.playing() ? "играет" : "стоп";
        String sub = track == null ? "Плеер: " + tech.gulp.lavavisual.input.Binds.keyName(tech.gulp.lavavisual.input.Binds.Action.MUSIC)
                : (track.artist().isBlank() ? "" : track.artist() + " · ") + state;
        UiFont.text(g, font, sub, tx, 20, 0xFF9AA3B2, tw, Face.SMALL);
        double pos = tech.gulp.lavavisual.audio.MusicPlayer.position(), len = tech.gulp.lavavisual.audio.MusicPlayer.duration();
        double progress = len > 0 ? Math.clamp(pos / len, 0, 1) : 0;
        UiDraw.round(g, tx, 32, tw, 3, 1, 0xFF2A2E37);
        int filled = (int) Math.round(tw * progress);
        if (filled > 1) UiDraw.roundH(g, tx, 32, filled, 3, 1, accent, accent2);
        String a = tech.gulp.lavavisual.audio.MusicPlayer.time(pos), b = tech.gulp.lavavisual.audio.MusicPlayer.time(len);
        UiFont.text(g, font, a, tx, 38, 0xFFB8C0CD, 40, Face.SMALL);
        UiFont.text(g, font, b, tx + tw - UiFont.width(g, font, b, Face.SMALL), 38, 0xFFB8C0CD, 40, Face.SMALL);
        var prev = tech.gulp.lavavisual.audio.MusicPlayer.neighbour(-1);
        var next = tech.gulp.lavavisual.audio.MusicPlayer.neighbour(1);
        int half = (bw - 16) / 2, ly = bh - 13;
        g.fill(8, ly - 3, bw - 8, ly - 2, 0x18FFFFFF);
        UiFont.iconSmall(g, font, Icons.SKIP_BACK, 8, ly, 0xFF7C8594);
        UiFont.text(g, font, prev == null ? "—" : prev.shown(), 20, ly, 0xFF8C95A4, half - 16, Face.SMALL);
        String nextName = next == null ? "—" : next.shown();
        int nw = Math.min(half - 16, UiFont.width(g, font, nextName, Face.SMALL));
        UiFont.text(g, font, nextName, bw - 20 - nw, ly, 0xFF8C95A4, half - 16, Face.SMALL);
        UiFont.iconSmall(g, font, Icons.SKIP_FORWARD, bw - 16, ly, 0xFF7C8594);
    }
    /** Vinyl disc drawn from cached circles; the streaks and the label mark show the rotation. */
    public static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, float angle, int accent, int accent2) {
        UiDraw.circle(g, cx, cy, r, 0xFF0E1015);
        UiDraw.circle(g, cx, cy, r - 2.5, 0xFF17191F);
        UiDraw.circle(g, cx, cy, r - 5.5, 0xFF101217);
        UiDraw.circle(g, cx, cy, r - 8.5, 0xFF181A20);
        UiDraw.circle(g, cx, cy, r * 0.4, 0xFF000000 | accent);
        UiDraw.circle(g, cx, cy, r * 0.4 - 2, 0xFF000000 | UiDraw.mix(accent, accent2, 0.6));
        UiDraw.circle(g, cx, cy, Math.max(1.5, r * 0.07), 0xFF0B0C10);
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) cy);
        g.pose().rotate((float) Math.toRadians(angle));
        g.fill((int) Math.round(r * 0.45), -1, (int) Math.round(r - 3), 0, 0x55FFFFFF);
        g.fill(-(int) Math.round(r - 3), 0, -(int) Math.round(r * 0.45), 1, 0x30FFFFFF);
        g.fill(-1, -(int) Math.round(r * 0.36), 0, -(int) Math.round(r * 0.14), 0xCCFFFFFF);
        g.pose().popMatrix();
    }

    /** HUD panel: soft two-layer shadow, faint top-lit gradient and a hairline in the element's two colours. */
    private static void panel(GuiGraphicsExtractor g, HudConfig c, int x, int y, int w, int h, int radius, double opacity, int accent, int accent2) {
        if (c.shadows) {
            UiDraw.round(g, x - 1, y + 1, w + 2, h + 2, radius + 1, UiDraw.alpha(0, opacity * 0.12));
            UiDraw.round(g, x, y + 2, w, h, radius, UiDraw.alpha(0, opacity * 0.22));
        }
        UiDraw.roundV(g, x, y, w, h, radius, UiDraw.alpha(UiDraw.mix(PANEL, 0xFFFFFF, 0.05), opacity), UiDraw.alpha(PANEL, opacity));
        double line = 0.9 * Math.max(0.45, opacity);
        UiDraw.roundH(g, x + radius, y, w - radius * 2, 1, 0, UiDraw.alpha(accent, line), UiDraw.alpha(accent2, line));
    }
    private static int durability(double ratio) { return ratio > 0.5 ? 0xFF7ADB6A : ratio > 0.25 ? 0xFFE0C14C : 0xFFE06A4C; }

    /** Coordinates, FPS and totem counter: icon badge, small accent title and a bold value. */
    private static void info(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, String id, int accent, int accent2) {
        int bw = 156, bh = 30;
        Font font = mc.font;
        panel(g, c, 0, 0, bw, bh, 6, w.opacity, accent, accent2);
        UiDraw.roundV(g, 5, 5, 20, 20, 5, UiDraw.alpha(accent, 0.26), UiDraw.alpha(accent2, 0.14));
        String value;
        if (id.equals("totems")) {
            // Item components are bound only inside a world; the title-screen editor shows an icon instead.
            if (mc.level != null) g.item(new ItemStack(Items.TOTEM_OF_UNDYING), 7, 7);
            else UiFont.icon(g, font, Icons.HEART_PULSE, 10, 10, UiDraw.alpha(accent, 1));
            value = totems(mc);
        } else {
            UiFont.icon(g, font, id.equals("coordinates") ? Icons.MAP_PIN : Icons.GAUGE, 10, 10, UiDraw.alpha(accent, 1));
            value = id.equals("coordinates") ? LavaVisualClient.STATE.coordinates : LavaVisualClient.STATE.performance;
        }
        UiFont.gradient(g, font, title(id), 31, 4, accent, accent2, 1, Face.SMALL);
        UiFont.text(g, font, value, 31, 15, UiDraw.alpha(TEXT, 1), bw - 36, Face.BOLD);
    }

    private static String totems(Minecraft mc) {
        if (mc.player == null) return "0 шт.";
        var inventory = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (inventory.getContainerSize() <= 36 && mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count += mc.player.getOffhandItem().getCount();
        return count + " шт.";
    }

    /** Keystrokes: separate keys, accent fill that eases in on press, real CPS under both mouse buttons. */
    private static void keys(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, int accent2, double dt) {
        var o = mc.options;
        Font font = mc.font;
        boolean[] down = {o.keyUp.isDown(), o.keyLeft.isDown(), o.keyDown.isDown(), o.keyRight.isDown(), o.keyAttack.isDown(), o.keyUse.isDown(), o.keyJump.isDown()};
        String[] labels = {"W", "A", "S", "D", "ЛКМ", "ПКМ", ""};
        int[][] r = {{27, 2, 22, 22}, {2, 27, 22, 22}, {27, 27, 22, 22}, {52, 27, 22, 22}, {2, 52, 35, 24}, {39, 52, 35, 24}, {2, 79, 72, 11}};
        for (int i = 0; i < 7; i++) {
            double goal = down[i] ? 1 : 0;
            KEY_PRESS[i] += (goal - KEY_PRESS[i]) * (c.animations ? Math.min(1, dt * (down[i] ? 28 : 10)) : 1);
            double p = KEY_PRESS[i];
            int inset = p > 0.5 ? 1 : 0;
            int x = r[i][0] + inset, y = r[i][1] + inset, kw = r[i][2] - inset * 2, kh = r[i][3] - inset * 2;
            if (c.shadows) UiDraw.round(g, x, y + 1, kw, kh, 5, UiDraw.alpha(0, w.opacity * 0.3 * (1 - p)));
            double keyAlpha = w.opacity + (0.96 - w.opacity) * p;
            UiDraw.roundV(g, x, y, kw, kh, 5, UiDraw.alpha(UiDraw.mix(UiDraw.mix(PANEL, 0xFFFFFF, 0.05), accent, p), keyAlpha), UiDraw.alpha(UiDraw.mix(PANEL, accent2, p), keyAlpha));
            if (p > 0.02) UiDraw.round(g, x, y, kw, Math.max(2, kh / 2), 5, UiDraw.alpha(0xFFFFFF, 0.10 * p));
            int fg = 0xFF000000 | UiDraw.mix(0xEEF0F4, 0x0E1014, p);
            int cx = r[i][0] + r[i][2] / 2;
            if (i < 4) UiFont.centered(g, font, labels[i], cx, r[i][1] + 7, fg, Face.BOLD);
            else if (i < 6) {
                UiFont.centered(g, font, labels[i], cx, r[i][1] + 4, fg, Face.BOLD);
                UiFont.centered(g, font, ClickCounter.cps(i == 4) + " CPS", cx, r[i][1] + 13, 0xFF000000 | UiDraw.mix(0x9AA0AC, 0x22262C, p), Face.SMALL);
            } else g.fill(r[i][0] + 24, r[i][1] + 5, r[i][0] + r[i][2] - 24, r[i][1] + 6, UiDraw.alpha(fg, 0.85));
        }
    }

    /** Armor: the actual pieces, durability strip and percent; vanilla slot silhouettes when empty. */
    private static void armor(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, int accent2) {
        int bw = 97, bh = 36;
        Font font = mc.font;
        panel(g, c, 0, 0, bw, bh, 6, w.opacity, accent, accent2);
        var player = mc.player;
        for (int i = 0; i < 4; i++) {
            int x = 4 + i * 23, y = 4;
            UiDraw.round(g, x, y, 20, 20, 4, 0xF21D2027);
            ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemBySlot(ARMOR[i]);
            if (stack.isEmpty()) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("minecraft", ARMOR_SPRITES[i]), x + 2, y + 2, 16, 16, 0x66FFFFFF);
                UiFont.centered(g, font, "—", x + 10, y + 22, 0xFF5D6472, Face.SMALL);
                continue;
            }
            g.item(stack, x + 2, y + 2);
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                double ratio = Math.clamp(1 - (double) stack.getDamageValue() / stack.getMaxDamage(), 0, 1);
                int color = durability(ratio);
                g.fill(x + 3, y + 18, x + 17, y + 19, 0xFF2A2D35);
                g.fill(x + 3, y + 18, x + 3 + Math.max(1, (int) Math.round(14 * ratio)), y + 19, color);
                UiFont.centered(g, font, Math.round(ratio * 100) + "%", x + 10, y + 22, color, Face.SMALL);
            } else UiFont.centered(g, font, "∞", x + 10, y + 22, 0xFF9AA0AC, Face.SMALL);
        }
    }

    private static String subtitle(Minecraft mc) {
        String place;
        if (mc.player == null) place = "Главное меню";
        else if (mc.isLocalServer()) place = "Одиночная игра";
        else place = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Сервер";
        LocalTime time = LocalTime.now();
        return place + " · " + String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }
    private static int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }
    /** Watermark segments after the brand: icon + value, e.g. nickname, fps, ping, time. */
    private record Segment(String icon, String text, int color) { }
    private static List<Segment> segments(Minecraft mc, int accent2) {
        String name = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        int latency = ping(mc);
        int pingColor = latency < 80 ? 0xFF8BE07A : latency < 160 ? 0xFFE6C75A : 0xFFE9755A;
        LocalTime time = LocalTime.now();
        List<Segment> list = new ArrayList<>(4);
        list.add(new Segment(Icons.USER, name, 0xFFF2F4F8));
        list.add(new Segment(Icons.MONITOR, mc.getFps() + " fps", 0xFFF2F4F8));
        if (mc.getConnection() != null && !mc.isLocalServer()) list.add(new Segment(Icons.WIFI, latency + " ms", pingColor));
        list.add(new Segment(Icons.CLOCK, String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute()), 0xFFF2F4F8));
        return list;
    }
    private static final int WM_H = 20, WM_GAP = 7, WM_ICON = 12;
    private static int watermarkWidth(int scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = 5 + tech.gulp.lavavisual.ui.Logo.width(true) + 4 + UiFont.width(font, "LavaVisual", Face.BOLD, scale) + WM_GAP;
        for (Segment segment : segments(mc, 0)) width += 1 + WM_GAP + WM_ICON + UiFont.width(font, segment.text(), Face.REGULAR, scale) + WM_GAP;
        return width;
    }
    /**
     * Watermark: one flat line like the watermarks of visual clients (Celestial, Expensive): dark plate, thin accent
     * outline, logo and name, then icon + value segments split by hairlines. No gradients or glow on purpose.
     */
    private static void watermark(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, int accent, int accent2) {
        Font font = mc.font;
        int scale = UiFont.halfScale(g);
        List<Segment> list = segments(mc, accent2);
        int brandW = UiFont.width(font, "LavaVisual", Face.BOLD, scale), logoW = tech.gulp.lavavisual.ui.Logo.width(true);
        int bw = 5 + logoW + 4 + brandW + WM_GAP;
        int[] widths = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            widths[i] = UiFont.width(font, list.get(i).text(), Face.REGULAR, scale);
            bw += 1 + WM_GAP + WM_ICON + widths[i] + WM_GAP;
        }
        double op = Math.clamp(w.opacity, 0.2, 1);
        if (c.shadows) UiDraw.round(g, 0, 1, bw, WM_H, 5, UiDraw.alpha(0, 0.28 * op));
        UiDraw.round(g, 0, 0, bw, WM_H, 5, UiDraw.alpha(accent, 0.55 * op));
        UiDraw.round(g, 1, 1, bw - 2, WM_H - 2, 4, UiDraw.alpha(0x0E0F13, 0.94 * op));
        UiDraw.round(g, 1, 1, 5 + logoW + 4 + brandW + WM_GAP - 2, WM_H - 2, 4, UiDraw.alpha(accent, 0.1 * op));
        tech.gulp.lavavisual.ui.Logo.draw(g, true, 5, 2, 0xFFFFFFFF);
        int x = 5 + logoW + 4;
        UiFont.text(g, font, "LavaVisual", x, 6, 0xFF000000 | accent, brandW + 2, Face.BOLD);
        x += brandW + WM_GAP;
        for (int i = 0; i < list.size(); i++) {
            Segment segment = list.get(i);
            g.fill(x, 5, x + 1, WM_H - 5, 0x24FFFFFF);
            x += 1 + WM_GAP;
            UiFont.icon(g, font, segment.icon(), x, 6, 0xFF000000 | UiDraw.mix(accent2, 0xFFFFFF, 0.25));
            x += WM_ICON;
            UiFont.text(g, font, segment.text(), x, 6, segment.color(), widths[i] + 2, Face.REGULAR);
            x += widths[i] + WM_GAP;
        }
    }

    /**
     * Mob avatar: a live 3D model. Picture-in-picture states ignore the GUI pose, so the box is transformed to
     * absolute screen coordinates here and drawn with an identity pose.
     */
    private static boolean entityAvatar(GuiGraphicsExtractor g, LivingEntity entity, int x, int y, int size) {
        Vector2f a = g.pose().transformPosition(new Vector2f(x, y));
        Vector2f b = g.pose().transformPosition(new Vector2f(x + size, y + size));
        int x0 = Math.round(Math.min(a.x, b.x)), y0 = Math.round(Math.min(a.y, b.y));
        int x1 = Math.round(Math.max(a.x, b.x)), y1 = Math.round(Math.max(a.y, b.y));
        int box = Math.min(x1 - x0, y1 - y0);
        if (box < 6) return false;
        float extent = Math.max(0.3f, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        int scale = (int) Math.clamp(box * 0.8f / extent, 2, 120);
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
        g.pose().pushMatrix();
        try {
            g.pose().identity();
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0625f, cx + 18, cy - 6, entity);
            return true;
        } catch (RuntimeException error) {
            return false;
        } finally { g.pose().popMatrix(); }
    }

    private static void target(GuiGraphicsExtractor g, Minecraft mc, HudConfig c, HudConfig.Widget w, TargetSnapshot target, double fade, int accent, int accent2, double dt) {
        int bw = 180, ph = 56;
        Font font = mc.font;
        if (target == null) target = new TargetSnapshot("Предпросмотр", 16, 20, 10, 3.2,
                mc.player == null ? null : mc.player.getSkin().body().texturePath(), null, 0);
        UiDraw.roundH(g, -3, -3, bw + 6, ph + 6, 9, UiDraw.alpha(accent, 0.08 * fade), UiDraw.alpha(accent2, 0.08 * fade));
        UiDraw.roundH(g, -2, -2, bw + 4, ph + 4, 8, UiDraw.alpha(accent, 0.12 * fade), UiDraw.alpha(accent2, 0.12 * fade));
        if (c.shadows) UiDraw.round(g, 1, 2, bw, ph, 6, UiDraw.alpha(0, w.opacity * 0.25 * fade));
        UiDraw.roundV(g, 0, 0, bw, ph, 6, UiDraw.alpha(UiDraw.mix(PANEL, 0xFFFFFF, 0.05), w.opacity * fade), UiDraw.alpha(PANEL, w.opacity * fade));
        UiDraw.roundH(g, 4, 1, bw - 8, 19, 5, UiDraw.alpha(accent, 0.10 * fade), UiDraw.alpha(accent2, 0.06 * fade));
        UiDraw.roundH(g, 8, 0, bw - 16, 1, 0, UiDraw.alpha(accent, 0.9 * fade), UiDraw.alpha(accent2, 0.9 * fade));
        UiDraw.roundV(g, 7, 7, 38, 38, 7, UiDraw.alpha(accent, 0.6 * fade), UiDraw.alpha(accent2, 0.6 * fade));
        UiDraw.round(g, 8, 8, 36, 36, 6, UiDraw.alpha(0x1B1E25, fade));
        int white = UiDraw.alpha(0xFFFFFF, fade);
        LivingEntity living = target.entity();
        if (target.skin() != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, target.skin(), 10, 10, 8, 8, 32, 32, 8, 8, 64, 64, white);
            g.blit(RenderPipelines.GUI_TEXTURED, target.skin(), 10, 10, 40, 8, 32, 32, 8, 8, 64, 64, white);
        } else if (!(living != null && !living.isRemoved() && fade > 0.6 && entityAvatar(g, living, 9, 9, 34))) {
            UiDraw.round(g, 14, 14, 24, 24, 12, UiDraw.alpha(accent, 0.16 * fade));
            UiFont.icon(g, font, living == null ? Icons.USER : Icons.GHOST, 21, 21, UiDraw.alpha(accent, fade));
        }
        if (living != null && living.hurtTime > 0)
            UiDraw.round(g, 10, 10, 32, 32, 4, UiDraw.alpha(0xFF2A2A, 0.45 * living.hurtTime / 10.0 * fade));
        if (lastHealth >= 0 && target.health() < lastHealth - 0.01 && target.name().equals(lastName) && HUD_PARTICLES.size() < 60) {
            long born = System.nanoTime();
            for (int i = 0; i < 12; i++) {
                double angle = Math.random() * Math.PI * 2, speed = 40 + Math.random() * 70;
                HUD_PARTICLES.add(new HudParticle(26, 26, Math.cos(angle) * speed, Math.sin(angle) * speed - 40, born, i % 3 == 0 ? 0xFFFFFF : accent & 0xFFFFFF));
            }
        }
        lastHealth = target.health();
        long particleNow = System.nanoTime();
        HUD_PARTICLES.removeIf(pt -> particleNow - pt.born() > 700_000_000L);
        for (HudParticle pt : HUD_PARTICLES) {
            double age = (particleNow - pt.born()) / 1e9, life = 1 - age / 0.7;
            int px = (int) (pt.x() + pt.vx() * age), py = (int) (pt.y() + pt.vy() * age + 160 * age * age);
            UiDraw.round(g, px - 1, py - 1, 3, 3, 1, UiDraw.alpha(pt.color(), life * fade));
        }
        int tx = 52, tw = bw - tx - 8;
        int combo = WorldCosmetics.combo();
        if (combo >= 2) {
            UiDraw.roundH(g, bw - 34, 6, 28, 12, 6, UiDraw.alpha(accent, 0.95 * fade), UiDraw.alpha(accent2, 0.95 * fade));
            UiFont.centered(g, font, "x" + combo, bw - 20, 8, UiDraw.alpha(0x111216, fade), Face.BOLD);
        }
        UiFont.text(g, font, target.name(), tx, 8, UiDraw.alpha(TEXT, fade), tw - (combo >= 2 ? 32 : 0), Face.BOLD);
        UiFont.iconSmall(g, font, Icons.HEART, tx, 20, UiDraw.alpha(0xFF4D5E, fade));
        String hp = String.format(Locale.ROOT, "%.1f / %.1f", target.health(), target.maximum());
        UiFont.text(g, font, hp, tx + 11, 20, UiDraw.alpha(0xE8E8EB, fade), tw - 11);
        float absorption = living == null ? 0 : living.getAbsorptionAmount();
        if (absorption > 0.05f) {
            int ax = tx + 11 + UiFont.width(g, font, hp, Face.REGULAR) + 4;
            UiFont.text(g, font, String.format(Locale.ROOT, "+%.1f", absorption), ax, 20, UiDraw.alpha(0xFFD24A, fade), Math.max(1, bw - 8 - ax));
        }
        int muted = UiDraw.alpha(MUTED, fade);
        String armor = Integer.toString(target.armor());
        UiFont.iconSmall(g, font, Icons.SHIELD, tx, 31, muted);
        UiFont.text(g, font, armor, tx + 11, 31, muted, 24);
        int dx = tx + 11 + UiFont.width(g, font, armor, Face.REGULAR) + 9;
        UiFont.iconSmall(g, font, Icons.RULER, dx, 31, muted);
        UiFont.text(g, font, String.format(Locale.ROOT, "%.1f м", target.distance()), dx + 11, 31, muted, Math.max(1, bw - 8 - dx - 11));
        double ratio = target.maximum() > 0 ? Math.clamp(target.health() / target.maximum(), 0, 1) : 0;
        if (!target.name().equals(lastName)) { lastName = target.name(); healthShown = healthGhost = ratio; }
        healthShown += (ratio - healthShown) * Math.min(1, dt * 12);
        healthGhost = ratio > healthGhost ? ratio : Math.max(healthShown, healthGhost - dt * 0.5);
        int barX = tx, barW = bw - tx - 8, barY = 44;
        UiDraw.round(g, barX, barY, barW, 5, 2, UiDraw.alpha(0x2A2C33, fade));
        if (healthGhost > 0) UiDraw.round(g, barX, barY, Math.max(2, (int) (barW * healthGhost)), 5, 2, UiDraw.alpha(0xFFFFFF, 0.35 * fade));
        if (healthShown > 0) {
            int fill = Math.max(2, (int) (barW * healthShown));
            UiDraw.roundH(g, barX, barY, fill, 5, 2, UiDraw.alpha(accent, fade), UiDraw.alpha(UiDraw.mix(accent, accent2, healthShown), fade));
            g.fillGradient(barX + 1, barY, barX + fill - 1, barY + 2, UiDraw.alpha(0xFFFFFF, 0.28 * fade), UiDraw.alpha(0xFFFFFF, 0));
        }
        // Equipment strip under the panel: helmet, chestplate, leggings, boots, main hand.
        if (living == null || fade > 0.7) {
            ItemStack[] gear = living == null ? new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}
                    : new ItemStack[] {living.getItemBySlot(EquipmentSlot.HEAD), living.getItemBySlot(EquipmentSlot.CHEST),
                    living.getItemBySlot(EquipmentSlot.LEGS), living.getItemBySlot(EquipmentSlot.FEET), living.getMainHandItem()};
            for (int i = 0; i < gear.length; i++) {
                int sx = i * 21, sy = 60;
                if (c.shadows) UiDraw.round(g, sx + 1, sy + 1, 19, 19, 4, UiDraw.alpha(0, w.opacity * 0.25 * fade));
                UiDraw.roundV(g, sx, sy, 19, 19, 4, UiDraw.alpha(UiDraw.mix(PANEL, 0xFFFFFF, 0.05), w.opacity * fade), UiDraw.alpha(PANEL, w.opacity * fade));
                ItemStack stack = gear[i];
                if (stack.isEmpty()) {
                    if (i < 4) g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("minecraft", ARMOR_SPRITES[i]), sx + 2, sy + 2, 15, 15, UiDraw.alpha(0xFFFFFF, 0.3 * fade));
                    continue;
                }
                g.item(stack, sx + 2, sy + 1);
                if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                    double left = Math.clamp(1 - (double) stack.getDamageValue() / stack.getMaxDamage(), 0, 1);
                    g.fill(sx + 3, sy + 17, sx + 16, sy + 18, 0xFF2A2D35);
                    g.fill(sx + 3, sy + 17, sx + 3 + Math.max(1, (int) Math.round(13 * left)), sy + 18, durability(left));
                }
            }
        }
    }

    /** Hotkey confirmation, bottom centre above the hotbar, 1.5 s. */
    private static void toast(GuiGraphicsExtractor g, Minecraft mc) {
        String text = tech.gulp.lavavisual.input.Binds.Toast.text;
        long age = System.currentTimeMillis() - tech.gulp.lavavisual.input.Binds.Toast.at;
        if (text == null || age > 1500) return;
        double fade = Math.min(1, Math.min(age / 120.0, (1500 - age) / 300.0));
        int tw = UiFont.width(g, mc.font, text, Face.REGULAR), w = tw + 20, x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 78;
        UiDraw.round(g, x, y, w, 18, 6, UiDraw.alpha(PANEL, 0.88 * fade));
        UiDraw.roundV(g, x + 6, y + 5, 3, 8, 1, UiDraw.alpha(LavaVisualClient.config().color("menu"), fade), UiDraw.alpha(LavaVisualClient.config().color2("menu"), fade));
        UiFont.text(g, mc.font, text, x + 14, y + 5, UiDraw.alpha(TEXT, Math.max(0.05, fade)), tw + 2);
    }

    public static void crosshair(GuiGraphicsExtractor g) {
        HudConfig c = LavaVisualClient.config();
        int color = UiDraw.alpha(c.color("crosshair"), c.crosshairOpacity);
        g.pose().pushMatrix();
        try {
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f);
            g.pose().scale((float) c.crosshairScale);
            if (c.crosshairShape == 1) g.fill(-1, -1, 1, 1, color);
            else if (c.crosshairShape == 2) {
                g.fill(-6, 0, -2, 1, color); g.fill(3, 0, 7, 1, color);
                g.fill(0, -6, 1, -2, color); g.fill(0, 3, 1, 7, color);
            } else {
                g.fill(-4, -4, 5, -3, color); g.fill(-4, 4, 5, 5, color);
                g.fill(-4, -3, -3, 4, color); g.fill(4, -3, 5, 4, color);
            }
        } finally { g.pose().popMatrix(); }
    }
}
