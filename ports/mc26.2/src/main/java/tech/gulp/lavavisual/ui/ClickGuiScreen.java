package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.config.ColorMath;
import tech.gulp.lavavisual.input.Binds;
import tech.gulp.lavavisual.map.Waypoints;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.effects.CustomAudio;
import tech.gulp.lavavisual.effects.Hats;
import tech.gulp.lavavisual.hud.HudRenderer;

public final class ClickGuiScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action, boolean clipped) { }
    private record Slider(int x, int y, int width, double min, double max, DoubleConsumer setter) {
        void set(double mouse) { setter.accept(min + Math.clamp((mouse - x) / width, 0, 1) * (max - min)); }
    }
    public static final int PAGE_HUD = 0, PAGE_EFFECTS = 1, PAGE_HANDS = 2, PAGE_SOUNDS = 3, PAGE_MAP = 4, PAGE_BINDS = 5, PAGE_COLORS = 6, PAGE_WORLD = 7, PAGE_INTERFACE = 8;
    private static final String[] TABS = {"HUD", "Эффекты", "Руки", "Звуки", "Карта", "Бинды", "Цвета", "Мир / FPS", "Интерфейс"};
    private static final String[] TAB_ICONS = {Icons.LAYOUT_DASHBOARD, Icons.SPARKLES, Icons.HAND, Icons.VOLUME_2, Icons.MAP, Icons.KEYBOARD, Icons.PALETTE, Icons.EARTH, Icons.SETTINGS};
    private static final int[] PRESETS = {0xFF5A36, 0xFF8A3C, 0xFFC233, 0xE8FF5A, 0x85F56A, 0x2CE08A, 0x36C8FF, 0x4C6BFF, 0xB45CFF, 0xFF5C9A, 0xFFFFFF, 0x9AA3B2};
    private static final Map<String, String> CARD_ICONS = Map.ofEntries(
            Map.entry("target", Icons.TARGET), Map.entry("coordinates", Icons.MAP_PIN), Map.entry("performance", Icons.GAUGE),
            Map.entry("keys", Icons.KEYBOARD), Map.entry("armor", Icons.SHIELD), Map.entry("totems", Icons.HEART_PULSE),
            Map.entry("watermark", Icons.STAMP), Map.entry("badge", Icons.BADGE_CHECK), Map.entry("badge_share", Icons.USER), Map.entry("crosshair", Icons.CROSSHAIR),
            Map.entry("jump", Icons.CIRCLE_DOT), Map.entry("particles", Icons.SPARKLE), Map.entry("ambient", Icons.SPARKLES),
            Map.entry("marker", Icons.TARGET), Map.entry("esp", Icons.SCAN_EYE), Map.entry("kill", Icons.SKULL),
            Map.entry("hat", Icons.CROWN), Map.entry("wings", Icons.WIND), Map.entry("hat_others", Icons.EYE), Map.entry("trail", Icons.WIND), Map.entry("hands", Icons.HAND),
            Map.entry("sound0", Icons.SWORDS), Map.entry("sound1", Icons.ZAP), Map.entry("sound2", Icons.HEART_PULSE),
            Map.entry("sound3", Icons.SKULL), Map.entry("shadows", Icons.LAYERS), Map.entry("animations", Icons.WAND_SPARKLES),
            Map.entry("sky", Icons.CLOUD_SUN), Map.entry("boost", Icons.ROCKET), Map.entry("setting", Icons.EYE),
            Map.entry("minimap", Icons.MAP), Map.entry("beams", Icons.SIGNPOST), Map.entry("labels", Icons.NAVIGATION),
            Map.entry("mapcoords", Icons.LOCATE_FIXED), Map.entry("mapmarks", Icons.MAP_PINNED), Map.entry("tilt", Icons.MOVE_VERTICAL), Map.entry("cooldown", Icons.TIMER));
    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final Map<String, Double> motions = new HashMap<>();
    private int page, left, top, panelW, panelH, side, bodyX, bodyW, clipTop, clipBottom, cursor, mx, my;
    private int contentHeight;
    private boolean clippingHits;
    private double scroll, indicator, frameFactor, renderScale = 1;
    private String selected, colorOpen;
    private Slider dragging;
    /** Menu drag from the header or the logo block: grab offset in menu units and the free space of the last frame. */
    private boolean moving;
    private double grabX, grabY;
    private int freeW, freeH;
    private Binds.Action capturing;
    private Object confirmDelete;
    private long confirmAt;
    private int tabStep = 29;
    private final Map<String, double[]> hsvCache = new HashMap<>();
    private long opened = System.nanoTime(), lastFrame = opened;
    /** Menu search: every row of every page, collected once by running the pages without drawing (see buildIndex). */
    private record Entry(String title, String context, int page, String sub, int offset, String key, String haystack) { }
    private List<Entry> searchIndex, results = List.of();
    private boolean collecting, searchFocused;
    private String query = "", resultsFor, indexSection, indexContext, indexSub, highlight, highlightSub;
    private int indexPage, highlightPage, searchX, searchY, searchW;
    private long caretAt, highlightAt;
    private static final String LAYOUT_EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`", LAYOUT_RU = "йцукенгшщзхъфывапролджэячсмитьбюё";
    private static int lastPage;
    private static String lastSelected;
    private static double lastScroll;
    /** The menu key reopens the menu where it was closed: same tab, same subpage, same scroll position. */
    public static int lastPage() { return lastPage; }
    public static ClickGuiScreen restore() {
        ClickGuiScreen screen = new ClickGuiScreen(lastPage, lastSelected);
        screen.scroll = lastScroll;
        return screen;
    }
    @Override public void removed() {
        boolean searching = query != null && !query.isBlank();
        lastPage = page;
        lastSelected = searching ? null : colorOpen != null ? "color:" + colorOpen : selected;
        lastScroll = searching ? 0 : scroll;
        super.removed();
    }
    public ClickGuiScreen() { this(0); }
    public ClickGuiScreen(int page) { this(page, null); }
    public ClickGuiScreen(int page, String selected) {
        super(UiFont.component("LavaVisual")); this.page = Math.clamp(page, 0, TABS.length - 1); indicator = -1;
        if (selected != null && selected.startsWith("color:")) { colorOpen = selected.substring(6); selected = null; }
        this.selected = selected != null && (selected.equals("crosshair") || selected.equals("hat") || selected.equals("wings") || HudConfig.IDS.contains(selected)) ? selected : null;
    }
    private String flash;
    private long flashAt;
    private void flash(String message) { flash = message; flashAt = System.currentTimeMillis(); }
    private void changed() { LavaVisualClient.save(); }
    private void navigate(int next) {
        page = next; selected = null; colorOpen = null; capturing = null; scroll = 0; dragging = null; hits.clear(); sliders.clear();
        query = ""; resultsFor = null; results = List.of(); searchFocused = false;
    }
    private void select(String id) { selected = id; scroll = 0; }
    private void hit(int x, int y, int w, int h, Runnable action) { hits.add(new Hit(x, y, w, h, action, clippingHits)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int width) { UiFont.text(g, font, value, x, y, color, Math.max(1, width)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int width, UiFont.Face face) { UiFont.text(g, font, value, x, y, color, Math.max(1, width), face); }
    private int accent() { return LavaVisualClient.config().color("menu"); }
    private int accent2() { return LavaVisualClient.config().color2("menu"); }
    private static double frac(double v) { return v - Math.floor(v); }
    /** Slow embers rising behind the menu in both theme colours; positions are pure functions of time (no state, no allocation). */
    private void embers(GuiGraphicsExtractor g, int canvasW, int canvasH, long now, int ac, int ac2, double enter) {
        double t = now / 1e9, span = canvasH + 24;
        for (int i = 0; i < 34; i++) {
            double r1 = frac(Math.sin(i * 12.9898) * 43758.5453), r2 = frac(Math.sin(i * 78.233) * 24634.6345), r3 = frac(Math.sin(i * 39.425) * 12345.6789);
            double rise = (t * (5 + 13 * r2) + r3 * span) % span, life = rise / span;
            int x = (int) (r1 * canvasW + Math.sin(t * (0.25 + r3 * 0.5) + i) * 7), y = (int) (canvasH + 12 - rise);
            double a = Math.sin(Math.PI * life) * (0.22 + 0.4 * r3) * enter;
            int color = UiDraw.mix(ac, ac2, r1);
            double radius = r2 > 0.72 ? 1.3 : 0.75;
            UiDraw.circle(g, x, y, radius * 2.4, UiDraw.alpha(color, a * 0.2));
            UiDraw.circle(g, x, y, radius, UiDraw.alpha(color, a));
        }
    }
    private double motion(String key, double goal) {
        double previous = motions.getOrDefault(key, goal);
        double next = previous + (goal - previous) * frameFactor;
        motions.put(key, next); return next;
    }
    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int v = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | v;
    }
    private boolean hover(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom; }
    private void action(GuiGraphicsExtractor g, String title, int x, int y, int width, Runnable callback) { action(g, null, title, x, y, width, callback); }
    private void action(GuiGraphicsExtractor g, String icon, String title, int x, int y, int width, Runnable callback) {
        if (collecting) { index(title, null, y); return; }
        mark(g, title, x, y, width, 24);
        boolean over = hover(x, y, width, 24);
        UiDraw.roundV(g, x, y, width, 24, 6, over ? 0xFF353945 : 0xFF282B33, over ? 0xFF2B2F38 : 0xFF212329);
        g.fill(x + 6, y, x + width - 6, y + 1, over ? 0x1CFFFFFF : 0x0DFFFFFF);
        if (over) UiDraw.roundH(g, x + 7, y + 23, width - 14, 1, 0, UiDraw.alpha(accent(), 0.9), UiDraw.alpha(accent2(), 0.9));
        int tx = x + 9;
        if (icon != null) { UiFont.icon(g, font, icon, x + 8, y + 7, over ? accent() : 0xFFAEB6C4); tx = x + 23; }
        text(g, title, tx, y + 8, 0xFFE8EAF0, width - (tx - x) - 8); hit(x, y, width, 24, callback);
    }
    private void button(GuiGraphicsExtractor g, String title, Runnable callback) { button(g, null, title, callback); }
    private static final String[] ESP_STYLES = {"Призраки", "Круг", "Кристаллы", "Маркер", "Орбиты"};
    private static final String[] AIR_STYLES = {"Светлячки", "Снег", "Звёзды", "Угольки", "Сердечки"};
    /** One-of-N choice as a row of chips; the chosen one is filled with the theme gradient. */
    private void chips(GuiGraphicsExtractor g, String[] options, int current, java.util.function.IntConsumer pick) { chips(g, options, current, pick, options.length); }
    private void chips(GuiGraphicsExtractor g, String[] options, int current, java.util.function.IntConsumer pick, int wide) {
        if (collecting) {
            int rowSize = Math.min(options.length, bodyW >= 380 ? wide : 3);
            for (String option : options) index(option, null, cursor);
            cursor += (options.length + rowSize - 1) / rowSize * 30 + 4;
            return;
        }
        int perRow = Math.min(options.length, bodyW >= 380 ? wide : 3), gap = 6, w = (bodyW - gap * (perRow - 1)) / perRow, ac = accent(), ac2 = accent2();
        for (int i = 0; i < options.length; i++) {
            int column = i % perRow, x = bodyX + column * (w + gap), y = cursor, index = i;
            boolean on = i == current, over = hover(x, y, w, 24);
            mark(g, options[i], x, y, w, 24);
            if (on) {
                UiDraw.roundH(g, x - 1, y - 1, w + 2, 26, 7, UiDraw.alpha(ac, 0.25), UiDraw.alpha(ac2, 0.25));
                UiDraw.roundH(g, x, y, w, 24, 6, ac, ac2);
                g.fillGradient(x + 3, y + 1, x + w - 3, y + 10, 0x30FFFFFF, 0x00FFFFFF);
            } else UiDraw.roundV(g, x, y, w, 24, 6, over ? 0xFF353945 : 0xFF282B33, over ? 0xFF2B2F38 : 0xFF212329);
            UiFont.Face face = on ? UiFont.Face.BOLD : UiFont.Face.REGULAR;
            int lw = Math.min(w - 8, UiFont.width(g, font, options[i], face));
            text(g, options[i], x + (w - lw) / 2, y + 8, on ? 0xFFFFFFFF : 0xFFC9D0DA, lw + 2, face);
            hit(x, y, w, 24, () -> pick.accept(index));
            if (column == perRow - 1 || i == options.length - 1) cursor += 30;
        }
        cursor += 4;
    }
    private void button(GuiGraphicsExtractor g, String icon, String title, Runnable callback) { action(g, icon, title, bodyX, cursor, bodyW, callback); cursor += 32; }
    private void note(GuiGraphicsExtractor g, String title) { if (collecting) { cursor += 22; return; } text(g, title, bodyX + 1, cursor + 2, 0xFF838994, bodyW - 2); cursor += 22; }
    private void section(GuiGraphicsExtractor g, String title) {
        if (collecting) { indexContext = null; index(title, null, cursor); indexSection = indexContext = title; cursor += 28; return; }
        mark(g, title, bodyX, cursor, bodyW, 22);
        int ac = accent(), ac2 = accent2();
        UiDraw.roundV(g, bodyX + 1, cursor + 5, 3, 10, 1, ac, ac2);
        UiFont.gradient(g, font, title, bodyX + 10, cursor + 6, UiDraw.mix(ac, 0xFFFFFF, 0.12), UiDraw.mix(ac2, 0xFFFFFF, 0.12), 1, UiFont.Face.BOLD);
        UiDraw.roundH(g, bodyX, cursor + 21, bodyW, 1, 0, UiDraw.alpha(ac, 0.5), UiDraw.alpha(ac2, 0.02));
        cursor += 28;
    }
    private void tabIcon(GuiGraphicsExtractor g, int i, int x, int y, int color) { UiFont.icon(g, font, TAB_ICONS[i], x, y, color); }
    private void toggle(GuiGraphicsExtractor g, String key, String title, String description, boolean enabled, Runnable callback, Runnable settings) {
        if (collecting) { indexContext = indexSection; index(title, description, cursor); indexContext = title; cursor += 56; return; }
        int y = cursor;
        mark(g, title, bodyX, y, bodyW, 48);
        double over = motion("hover:" + key, hover(bodyX, y, bodyW, 48) ? 1 : 0), op = LavaVisualClient.config().menuOpacity;
        int ac = accent(), ac2 = accent2();
        UiDraw.roundV(g, bodyX, y, bodyW, 48, 7, UiDraw.alpha(blend(0x1C1F26, 0x2A2F38, over), op), UiDraw.alpha(blend(0x16181E, 0x22262E, over), op));
        g.fill(bodyX + 7, y, bodyX + bodyW - 7, y + 1, UiDraw.alpha(0xFFFFFF, 0.04 + 0.04 * over));
        double lit = motion("lit:" + key, enabled ? 1 : 0);
        if (lit > 0.01) {
            UiDraw.roundH(g, bodyX, y, bodyW, 48, 7, UiDraw.alpha(ac, 0.13 * lit), UiDraw.alpha(ac2, 0.02 * lit));
            UiDraw.roundV(g, bodyX + 1, y + 10, 2, 28, 1, UiDraw.alpha(ac, lit), UiDraw.alpha(ac2, lit));
        }
        String icon = CARD_ICONS.getOrDefault(key.startsWith("setting:") ? "setting" : key, Icons.SLIDERS_HORIZONTAL);
        UiDraw.roundV(g, bodyX + 10, y + 12, 24, 24, 7, blend(0x2B2F38, ac & 0xFFFFFF, lit * 0.42), blend(0x22252D, ac2 & 0xFFFFFF, lit * 0.36));
        UiFont.icon(g, font, icon, bodyX + 17, y + 19, 0xFF000000 | UiDraw.mix(0xAEB6C4, UiDraw.mix(ac, 0xFFFFFF, 0.55), lit));
        text(g, title, bodyX + 44, y + 9, 0xFFE5E9F0, bodyW - 44 - (settings == null ? 51 : 73), UiFont.Face.BOLD);
        text(g, description, bodyX + 44, y + 28, 0xFF838994, bodyW - 44 - 12);
        double on = motion("toggle:" + key, enabled ? 1 : 0);
        int tx = bodyX + bodyW - (settings == null ? 43 : 65);
        if (on > 0.02) UiDraw.roundH(g, tx - 2, y + 7, 34, 18, 9, UiDraw.alpha(ac, 0.2 * on), UiDraw.alpha(ac2, 0.2 * on));
        UiDraw.roundH(g, tx, y + 9, 30, 14, 7, blend(0x393E47, ac, on), blend(0x393E47, ac2, on));
        int knob = tx + 2 + (int) Math.round(on * 16);
        UiDraw.round(g, knob, y + 12, 10, 10, 5, 0x50000000);
        UiDraw.round(g, knob, y + 11, 10, 10, 5, 0xFFF7F8FB);
        hit(bodyX, y, bodyW - (settings == null ? 0 : 26), 48, callback);
        if (settings != null) {
            UiFont.icon(g, font, Icons.CHEVRON_RIGHT, bodyX + bodyW - 20, y + 11, hover(bodyX + bodyW - 26, y, 26, 48) ? accent() : 0xFFB3BAC7);
            hit(bodyX + bodyW - 26, y, 26, 48, settings);
        }
        cursor += 56;
    }
    private void slider(GuiGraphicsExtractor g, String label, double value, double min, double max, DoubleConsumer setter, boolean integer) {
        if (collecting) { index(label, null, cursor); cursor += 38; return; }
        int y = cursor;
        mark(g, label, bodyX, y, bodyW, 32);
        String shown = integer ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
        text(g, label, bodyX + 2, y + 2, 0xFFB8C0CD, bodyW - 60);
        text(g, shown, bodyX + bodyW - 52, y + 2, 0xFFF2F4F8, 52);
        int x = bodyX + 4, w = bodyW - 8;
        double progress = Math.clamp((value - min) / (max - min), 0, 1);
        UiDraw.round(g, x, y + 21, w, 4, 2, 0xFF30343D);
        int filled = (int) Math.round(w * progress), ac = accent(), tip = 0xFF000000 | UiDraw.mix(ac, accent2(), progress);
        if (filled > 0) UiDraw.roundH(g, x, y + 21, filled, 4, 2, ac, tip);
        if (filled > 0) g.fillGradient(x, y + 21, x + filled, y + 22, 0x40FFFFFF, 0x00FFFFFF);
        UiDraw.round(g, x + filled - 8, y + 15, 16, 16, 8, UiDraw.alpha(tip, 0.16));
        UiDraw.round(g, x + filled - 6, y + 17, 12, 12, 6, UiDraw.alpha(tip, 0.28));
        UiDraw.round(g, x + filled - 4, y + 18, 8, 10, 4, 0xFFF2F5FA);
        sliders.add(new Slider(x, y + 14, w, min, max, setter)); cursor += 38;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear(); sliders.clear();
        long now = System.nanoTime();
        var c = LavaVisualClient.config();
        frameFactor = c.animations ? 1 - Math.exp(-Math.min(0.1, (now - lastFrame) / 1e9) * 16) : 1;
        lastFrame = now;
        double enter = c.animations ? 1 - Math.pow(1 - Math.clamp((now - opened) / 240_000_000.0, 0, 1), 3) : 1;
        if (dragging == null && !moving) {
            // Whole screen pixels per menu unit: crisp text and icons instead of resampled glyphs.
            double fit = Math.min((width - 16) / 364.0, (height - 16) / 294.0);
            int gs = UiFont.guiScale(), k = Math.max(1, (int) Math.round(Math.min(c.menuScale, fit) * gs));
            while (k > 1 && (double) k / gs > fit + 1e-6) k--;
            renderScale = (double) k / gs;
        }
        renderScale = Math.max(0.25, renderScale);
        mx = (int) (mouseX / renderScale); my = (int) (mouseY / renderScale);
        int canvasW = (int) (width / renderScale), canvasH = (int) (height / renderScale);
        g.fill(0, 0, width, height, UiDraw.alpha(0x06090F, c.menuDim * enter));
        g.pose().pushMatrix();
        g.pose().scale((float) renderScale);
        int ac = accent(), ac2 = accent2();
        g.fillGradient(0, 0, canvasW, canvasH / 3, UiDraw.alpha(0x05070B, 0.28 * enter), 0x00000000);
        g.fillGradient(0, canvasH * 2 / 3, canvasW, canvasH, 0x00000000, UiDraw.alpha(0x05070B, 0.38 * enter));
        if (c.animations) embers(g, canvasW, canvasH, now, ac, ac2, enter);
        panelW = Math.min(600, canvasW - 24); panelH = Math.min(360, canvasH - 24);
        freeW = Math.max(0, canvasW - panelW); freeH = Math.max(0, canvasH - panelH);
        left = (int) Math.round(freeW * c.menuX); top = (int) Math.round(freeH * c.menuY) + (int) ((1 - enter) * 6);
        side = panelW < 440 ? 88 : 112; bodyX = left + side + 16; bodyW = panelW - side - 32;
        clipTop = top + 49; clipBottom = top + panelH - 33;
        if (enter < 1) {
            // Entrance: a short scale-in around the panel centre; exactly 1 afterwards, so text stays pixel-exact.
            float grow = (float) (0.965 + 0.035 * enter);
            g.pose().translate(left + panelW / 2f, top + panelH / 2f);
            g.pose().scale(grow);
            g.pose().translate(-(left + panelW / 2f), -(top + panelH / 2f));
        }
        if (c.shadows) {
            UiDraw.shadow(g, left, top, panelW, panelH, 10, 7, 3, 0.5 * enter);
            UiDraw.glow(g, left, top, panelW, panelH, 10, 6, ac, ac2, 0.2 * enter);
        }
        int menuBg = c.color("menu_bg") & 0xFFFFFF;
        UiDraw.roundV(g, left, top, panelW, panelH, 10, UiDraw.alpha(UiDraw.mix(menuBg, 0xFFFFFF, 0.025), c.menuOpacity), UiDraw.alpha(UiDraw.mix(menuBg, 0x000000, 0.12), c.menuOpacity));
        UiDraw.round(g, left + 4, top + 4, side - 6, panelH - 8, 8, UiDraw.alpha(UiDraw.mix(menuBg, 0x000000, 0.35), c.menuOpacity * 0.75));
        g.fillGradient(left + side + 2, top + 1, left + panelW - 10, top + 46, UiDraw.alpha(ac, 0.09), UiDraw.alpha(ac, 0));
        for (int gx = 0; gx < panelW - 24; gx += 3) {
            double t = gx / (double) (panelW - 24), pulse = 0.6 + 0.4 * Math.sin(now / 6e8 + t * 6);
            g.fill(left + 12 + gx, top, left + 15 + gx, top + 1, UiDraw.alpha(UiDraw.mix(ac, ac2, t), 0.9 * Math.sin(Math.PI * t) * pulse));
        }
        g.fillGradient(left + side, top + 15, left + side + 1, top + panelH - 15, UiDraw.alpha(ac, 0.5), UiDraw.alpha(ac2, 0.12));
        // Brand: the LV logo (pixel-exact texture) over a warm glow, and the name in the theme gradient.
        int sideCx = left + 4 + (side - 6) / 2, logoW = Logo.width(false);
        UiDraw.roundV(g, left + 4, top + 4, side - 6, 62, 8, UiDraw.alpha(ac, 0.13), UiDraw.alpha(ac2, 0));
        Logo.draw(g, false, sideCx - logoW / 2, top + 9, UiDraw.alpha(0xFFFFFF, Math.max(0.05, enter)));
        UiFont.gradientCentered(g, font, "LavaVisual", sideCx, top + 45, UiDraw.mix(ac, 0xFFFFFF, 0.1), UiDraw.mix(ac2, 0xFFFFFF, 0.1), 1, UiFont.Face.BOLD);
        tabStep = Math.max(20, Math.min(29, (panelH - 72 - 30) / TABS.length));
        int tabH = Math.min(25, tabStep - 2), tabPad = (tabH - 11) / 2;
        if (indicator < 0) indicator = page * tabStep;
        indicator += (page * tabStep - indicator) * frameFactor;
        int indicatorY = top + 72 + (int) indicator;
        UiDraw.roundH(g, left + 8, indicatorY, side - 16, tabH, 6, UiDraw.alpha(ac, 0.26), UiDraw.alpha(ac2, 0.06));
        UiDraw.roundV(g, left + 8, indicatorY + tabPad - 1, 2, 13, 1, ac, ac2);
        for (int i = 0; i < TABS.length; i++) {
            int next = i, y = top + 72 + i * tabStep;
            boolean active = page == i, overTab = hover(left + 8, y, side - 16, tabH);
            if (overTab && !active) UiDraw.round(g, left + 8, y, side - 16, tabH, 6, 0x0CFFFFFF);
            int tabColor = active ? 0xFFFFFFFF : overTab ? 0xFFD2D8E1 : 0xFF929BA9;
            tabIcon(g, i, left + 17, y + tabPad, active ? 0xFF000000 | UiDraw.mix(ac, 0xFFFFFF, 0.2) : tabColor);
            text(g, TABS[i], left + 33, y + tabPad + 1, tabColor, side - 40);
            hit(left + 8, y, side - 16, tabH, () -> navigate(next));
        }
        if (72 + TABS.length * tabStep + 14 < panelH - 21) text(g, "26.2 · 2.14", left + 13, top + panelH - 21, 0xFF586272, side - 18);
        boolean searching = !query.isBlank();
        String heading = searching ? "Поиск" : selected == null ? TABS[page] : selected.equals("crosshair") ? "Прицел" : selected.equals("hat") ? "Шляпы" : selected.equals("wings") ? "Крылья" : HudRenderer.title(selected);
        searchW = Math.max(70, Math.min(150, bodyW / 2 - 20)); searchX = left + panelW - 58 - searchW; searchY = top + 11;
        text(g, heading, bodyX, top + 17, 0xFFF0F3F7, searchX - bodyX - 10, UiFont.Face.HEADING);
        searchField(g, ac, ac2);
        boolean grab = moving || grabZone(mx, my);
        UiFont.icon(g, font, Icons.MOVE, left + panelW - 50, top + 17, grab ? 0xFF000000 | UiDraw.mix(ac, 0xFFFFFF, 0.3) : 0xFF4E5664);
        boolean overClose = mx >= left + panelW - 31 && mx < left + panelW - 7 && my >= top + 10 && my < top + 34;
        if (overClose) UiDraw.round(g, left + panelW - 31, top + 10, 24, 24, 6, 0xFF2A2E36);
        UiFont.icon(g, font, Icons.X, left + panelW - 24, top + 17, overClose ? 0xFFFFFFFF : 0xFFABB4C2);
        hit(left + panelW - 31, top + 10, 24, 24, this::onClose);
        g.fill(bodyX, top + 39, bodyX + bodyW, top + 40, 0xFF262A33);
        UiDraw.roundH(g, bodyX, top + 39, (int) (bodyW * enter), 1, 0, UiDraw.alpha(ac, 0.95), UiDraw.alpha(ac2, 0.05));
        if (searching && searchIndex == null) buildIndex(g);
        cursor = clipTop + 3 - (int) scroll;
        g.enableScissor(bodyX - 1, clipTop, bodyX + bodyW + 1, clipBottom);
        clippingHits = true;
        if (searching) searchResults(g);
        else if (selected != null) settings(g);
        else switch (page) {
            case PAGE_HUD -> hud(g); case PAGE_EFFECTS -> effects(g); case PAGE_HANDS -> hands(g); case PAGE_SOUNDS -> audio(g);
            case PAGE_MAP -> map(g); case PAGE_BINDS -> binds(g); case PAGE_COLORS -> colors(g); case PAGE_WORLD -> world(g);
            default -> appearance(g);
        }
        clippingHits = false;
        contentHeight = cursor + (int) scroll - clipTop;
        g.disableScissor();
        int max = Math.max(0, contentHeight - (clipBottom - clipTop));
        scroll = Math.clamp(scroll, 0, max);
        if (max > 0) {
            int h = Math.max(14, (clipBottom - clipTop) * (clipBottom - clipTop) / contentHeight);
            int y = clipTop + (int) ((clipBottom - clipTop - h) * scroll / max);
            UiDraw.roundV(g, left + panelW - 8, y, 2, h, 1, UiDraw.alpha(ac, 0.7), UiDraw.alpha(ac2, 0.7));
        }
        if (searching) text(g, "Enter · открыть первое    Esc · очистить    Ctrl+F · поиск", bodyX, top + panelH - 20, 0xFF818C9C, bodyW);
        else if (selected == null) text(g, Binds.keyName(Binds.Action.MENU) + " · меню    " + Binds.keyName(Binds.Action.DISABLE_ALL) + " · всё выкл    "
                + Binds.keyName(Binds.Action.WAYPOINT_ADD) + " · метка", bodyX, top + panelH - 20, 0xFF818C9C, bodyW);
        else {
            UiFont.icon(g, font, Icons.CHEVRON_LEFT, bodyX - 2, top + panelH - 21, 0xFFB3BAC7);
            text(g, "Назад к модулям", bodyX + 11, top + panelH - 20, 0xFFB3BAC7, bodyW - 11);
        }
        if (selected != null && !searching) hit(bodyX, clipBottom + 2, bodyW, 27, () -> select(null));
        g.pose().popMatrix();
    }
    private static String norm(String value) { return value.toLowerCase(Locale.ROOT).replace('ё', 'е'); }
    /** Search box in the header: click, Ctrl+F or just start typing. */
    private void searchField(GuiGraphicsExtractor g, int ac, int ac2) {
        int x = searchX, y = searchY, w = searchW, h = 22;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        double focus = motion("search:focus", searchFocused ? 1 : 0);
        if (focus > 0.02) UiDraw.roundH(g, x - 1, y - 1, w + 2, h + 2, 8, UiDraw.alpha(ac, 0.75 * focus), UiDraw.alpha(ac2, 0.75 * focus));
        UiDraw.round(g, x, y, w, h, 7, searchFocused ? 0xFF16181E : over ? 0xFF262A32 : 0xFF1D2026);
        boolean active = searchFocused || !query.isEmpty();
        UiFont.icon(g, font, Icons.SEARCH, x + 7, y + 6, active ? 0xFF000000 | UiDraw.mix(ac, 0xFFFFFF, 0.35) : over ? 0xFFAEB6C4 : 0xFF6B7280);
        int tx = x + 22, room = w - 22 - (query.isEmpty() ? 6 : 20), caretX = tx;
        if (query.isEmpty()) {
            String hint = searchFocused ? "Что найти?" : room > 84 ? "Поиск · Ctrl+F" : "Поиск";
            text(g, hint, tx, y + 7, searchFocused ? 0xFF7C8594 : 0xFF6B7280, room);
        } else {
            String shown = query;
            while (shown.length() > 1 && UiFont.width(g, font, shown, UiFont.Face.REGULAR) > room - 3) shown = shown.substring(1);
            text(g, shown, tx, y + 7, 0xFFE8EAF0, room);
            caretX = tx + UiFont.width(g, font, shown, UiFont.Face.REGULAR);
            boolean overClear = mx >= x + w - 20 && mx < x + w && my >= y && my < y + h;
            UiFont.iconSmall(g, font, Icons.X, x + w - 14, y + 7, overClear ? 0xFFFFFFFF : 0xFF8A93A1);
            hit(x + w - 20, y, 20, h, () -> { setQuery(""); searchFocused = true; });
        }
        if (searchFocused && (System.currentTimeMillis() - caretAt) % 1060 < 560) g.fill(caretX + 1, y + 6, caretX + 2, y + 16, UiDraw.alpha(ac, 0.95));
        hit(x, y, w - (query.isEmpty() ? 0 : 20), h, () -> { searchFocused = true; caretAt = System.currentTimeMillis(); });
    }
    /**
     * Runs every page once with drawing switched off: the row helpers record their titles instead of drawing, so the index
     * always matches the real menu. Direct draws of a page go into an empty scissor; hits and sliders are dropped afterwards.
     */
    private void buildIndex(GuiGraphicsExtractor g) {
        searchIndex = new ArrayList<>();
        int savedPage = page, savedMx = mx, savedMy = my, hitCount = hits.size(), sliderCount = sliders.size();
        String savedSelected = selected, savedColor = colorOpen;
        Binds.Action savedCapture = capturing;
        collecting = true; mx = my = -100000; colorOpen = null; capturing = null;
        g.enableScissor(-4, -4, -3, -3);
        try {
            for (int i = 0; i < TABS.length; i++) {
                indexPage = i; indexSub = null; indexSection = indexContext = null;
                index(TABS[i], "вкладка раздел", clipTop + 3);
            }
            for (int i = 0; i < TABS.length; i++) collect(g, i, null);
            collect(g, PAGE_EFFECTS, "crosshair");
            collect(g, PAGE_EFFECTS, "hat");
            collect(g, PAGE_EFFECTS, "wings");
        } finally {
            g.disableScissor();
            collecting = false; page = savedPage; selected = savedSelected; colorOpen = savedColor; capturing = savedCapture; mx = savedMx; my = savedMy;
            hits.subList(hitCount, hits.size()).clear(); sliders.subList(sliderCount, sliders.size()).clear();
        }
    }
    private void collect(GuiGraphicsExtractor g, int target, String sub) {
        page = target; selected = sub; indexPage = target; indexSub = sub; indexSection = indexContext = null; cursor = clipTop + 3;
        try {
            if (sub != null) settings(g);
            else switch (target) {
                case PAGE_HUD -> hud(g); case PAGE_EFFECTS -> effects(g); case PAGE_HANDS -> hands(g); case PAGE_SOUNDS -> audio(g);
                case PAGE_MAP -> map(g); case PAGE_BINDS -> binds(g); case PAGE_COLORS -> colors(g); case PAGE_WORLD -> world(g);
                default -> appearance(g);
            }
        } catch (RuntimeException error) {
            tech.gulp.lavavisual.LavaVisual.LOGGER.warn("LavaVisual search: cannot index page {}", target, error);
        }
    }
    private void index(String title, String extra, int y) {
        if (title == null || title.isBlank()) return;
        String context = indexContext == null || indexContext.equals(title) ? null : indexContext;
        String where = TABS[indexPage] + (indexSub == null ? "" : indexSub.equals("hat") ? " шляпы шляпа hat корона нимб цилиндр" : indexSub.equals("wings") ? " крылья wings ангел демон бабочка дракон феникс" : " прицел");
        String haystack = norm(title + " " + (extra == null ? "" : extra) + " " + (context == null ? "" : context) + " " + where);
        searchIndex.add(new Entry(title, context, indexPage, indexSub, Math.max(0, y - (clipTop + 3)), norm(title), haystack));
    }
    /** All words must match; title prefix first. Typed in the wrong keyboard layout? The swapped layout is tried too. */
    private List<Entry> search(String raw) {
        String q = norm(raw.trim()).replaceAll("\\s+", " ");
        if (q.isEmpty() || searchIndex == null) return List.of();
        List<Entry> found = match(q);
        if (found.isEmpty()) {
            StringBuilder swapped = new StringBuilder(q.length());
            for (char ch : q.toCharArray()) {
                int en = LAYOUT_EN.indexOf(ch), ru = LAYOUT_RU.indexOf(ch);
                swapped.append(en >= 0 ? LAYOUT_RU.charAt(en) : ru >= 0 ? LAYOUT_EN.charAt(ru) : ch);
            }
            found = match(norm(swapped.toString()));
        }
        return found;
    }
    private List<Entry> match(String q) {
        String[] words = q.split(" ");
        List<List<Entry>> ranks = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Set<String> seen = new HashSet<>();
        for (Entry e : searchIndex) {
            boolean all = true;
            for (String word : words) if (!e.haystack().contains(word)) { all = false; break; }
            if (!all || !seen.add(e.page() + "|" + e.sub() + "|" + e.key() + "|" + e.context())) continue;
            int rank = e.key().startsWith(q) ? 0 : (" " + e.key()).contains(" " + words[0]) ? 1 : e.key().contains(words[0]) ? 2 : 3;
            ranks.get(rank).add(e);
        }
        List<Entry> out = new ArrayList<>();
        for (List<Entry> rank : ranks) out.addAll(rank);
        return out.size() > 40 ? List.copyOf(out.subList(0, 40)) : out;
    }
    private void searchResults(GuiGraphicsExtractor g) {
        String q = query.trim();
        if (!q.equals(resultsFor)) { results = search(q); resultsFor = q; scroll = 0; cursor = clipTop + 3; }
        var c = LavaVisualClient.config();
        int cx = bodyX + bodyW / 2;
        if (results.isEmpty()) {
            int y = cursor + 14;
            UiDraw.round(g, cx - 18, y, 36, 36, 10, UiDraw.alpha(0x1C1F26, c.menuOpacity));
            UiFont.icon(g, font, Icons.SEARCH, cx - 5, y + 13, 0xFF6B7280);
            UiFont.centered(g, font, "Ничего не найдено", cx, y + 48, 0xFFE5E9F0, UiFont.Face.BOLD);
            UiFont.centered(g, font, "Попробуйте: шляпа, звук, цвет, карта, прицел", cx, y + 64, 0xFF838994, UiFont.Face.REGULAR);
            cursor = y + 84;
            return;
        }
        int ac = accent(), ac2 = accent2();
        for (int i = 0; i < results.size(); i++) {
            Entry e = results.get(i);
            int y = cursor;
            boolean first = i == 0;
            double over = motion("hover:result:" + i, hover(bodyX, y, bodyW, 36) ? 1 : 0);
            UiDraw.roundV(g, bodyX, y, bodyW, 36, 7, UiDraw.alpha(blend(0x1C1F26, 0x2A2F38, over), c.menuOpacity), UiDraw.alpha(blend(0x16181E, 0x22262E, over), c.menuOpacity));
            if (first) {
                UiDraw.roundH(g, bodyX, y, bodyW, 36, 7, UiDraw.alpha(ac, 0.12), UiDraw.alpha(ac2, 0.02));
                UiDraw.roundV(g, bodyX + 1, y + 9, 2, 18, 1, ac, ac2);
            }
            double lit = first ? 1 : over;
            UiDraw.roundV(g, bodyX + 9, y + 7, 22, 22, 6, blend(0x2B2F38, ac & 0xFFFFFF, 0.42 * lit), blend(0x22252D, ac2 & 0xFFFFFF, 0.36 * lit));
            UiFont.icon(g, font, TAB_ICONS[e.page()], bodyX + 15, y + 13, 0xFF000000 | UiDraw.mix(0xAEB6C4, UiDraw.mix(ac, 0xFFFFFF, 0.55), lit));
            text(g, e.title(), bodyX + 40, y + 6, 0xFFE5E9F0, bodyW - 40 - 34, UiFont.Face.BOLD);
            text(g, path(e), bodyX + 40, y + 21, 0xFF838994, bodyW - 40 - 34, UiFont.Face.SMALL);
            UiFont.icon(g, font, first ? Icons.CORNER_DOWN_LEFT : Icons.CHEVRON_RIGHT, bodyX + bodyW - 22, y + 13, first || over > 0.5 ? accent() : 0xFF9AA3B2);
            hit(bodyX, y, bodyW, 36, () -> openResult(e));
            cursor += 40;
        }
        note(g, results.size() >= 40 ? "Показаны первые 40 — уточните запрос." : "Найдено: " + results.size() + " · нажмите, чтобы перейти");
    }
    private String path(Entry e) {
        if (e.context() == null && e.sub() == null && e.title().equals(TABS[e.page()])) return "Открыть вкладку";
        String where = TABS[e.page()];
        if ("hat".equals(e.sub())) where += " · Шляпы";
        if ("wings".equals(e.sub())) where += " · Крылья";
        else if ("crosshair".equals(e.sub())) where += " · Прицел";
        if (e.context() != null) where += " · " + e.context();
        return where;
    }
    private void openResult(Entry e) {
        navigate(e.page());
        if (e.sub() != null) selected = e.sub();
        scroll = Math.max(0, e.offset() - 10);
        highlight = e.key(); highlightPage = e.page(); highlightSub = e.sub(); highlightAt = System.currentTimeMillis();
    }
    /** After a jump from the search the found row glows for a moment. */
    private void mark(GuiGraphicsExtractor g, String title, int x, int y, int w, int h) {
        if (highlight == null || title == null) return;
        long age = System.currentTimeMillis() - highlightAt;
        if (age > 1900) { highlight = null; return; }
        if (page != highlightPage || !Objects.equals(selected, highlightSub) || !highlight.equals(norm(title))) return;
        double a = (age < 1100 ? 1 : 1 - (age - 1100) / 800.0) * (0.7 + 0.3 * Math.sin(age / 95.0));
        UiDraw.roundH(g, x - 3, y - 3, w + 6, h + 6, 9, UiDraw.alpha(accent(), 0.6 * a), UiDraw.alpha(accent2(), 0.6 * a));
    }
    private void hud(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        for (String id : HudConfig.IDS) {
            var w = c.widgets.get(id);
            String desc = switch (id) {
                case "target" -> "Аватар и данные видимой цели";
                case "coordinates" -> "Ваша позиция в мире";
                case "keys" -> "WASD, кнопки мыши и CPS";
                case "armor" -> "Прочность надетой брони";
                case "totems" -> "Сколько тотемов в инвентаре";
                case "watermark" -> "Логотип, место, время, пинг и FPS";
                case "minimap" -> "Только местность, без игроков и мобов";
                default -> "Частота кадров";
            };
            toggle(g, id, HudRenderer.title(id), desc, w.visible, () -> { w.visible = !w.visible; changed(); }, () -> select(id));
        }
        button(g, Icons.MOVE, "Редактор расположения", () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
        var cfg = LavaVisualClient.config();
        toggle(g, "badge", "Значки LavaVisual", "Иконка у ников игроков, которые делятся значком", cfg.badgeEnabled,
                () -> { cfg.badgeEnabled = !cfg.badgeEnabled; changed(); }, null);
        toggle(g, "badge_share", "Делиться значком, шляпой и крыльями", "Игроки с LavaVisual увидят значок, шляпу и крылья", cfg.badgeShare,
                () -> { cfg.badgeShare = !cfg.badgeShare; changed(); }, null);
        note(g, "По умолчанию выключено: мод ничего не отправляет серверу.");
    }
    private void effects(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "esp", "Target ESP", "Вокруг игрока или моба, на которого вы навелись", c.espEnabled, () -> { c.espEnabled = !c.espEnabled; changed(); }, null);
        chips(g, ESP_STYLES, c.espStyle, i -> { c.espStyle = i; c.espEnabled = true; changed(); });
        slider(g, "Удержание цели · сек", c.targetHold, 0.5, 10, v -> c.targetHold = v, false);
        toggle(g, "crosshair", "Прицел", "Форма, размер и прозрачность", c.crosshairEnabled, () -> { c.crosshairEnabled = !c.crosshairEnabled; changed(); }, () -> select("crosshair"));
        toggle(g, "jump", "Jump Circle", "Кольцо при вашем прыжке", c.jumpEnabled, () -> { c.jumpEnabled = !c.jumpEnabled; changed(); }, null);
        slider(g, "Радиус кольца", c.jumpRadius, 0.5, 2, v -> c.jumpRadius = v, false);
        toggle(g, "particles", "Hit Particles", "Искры при ручной атаке", c.particlesEnabled, () -> { c.particlesEnabled = !c.particlesEnabled; changed(); }, null);
        slider(g, "Число искр", c.particleCount, 4, 24, v -> c.particleCount = (int) Math.round(v), true);
        slider(g, "Размер искр", c.particleSize, 0.04, 0.25, v -> c.particleSize = v, false);
        String[] shapes = {"искры", "звёзды", "сердечки"}, patterns = {"взрыв", "кольцо", "фонтан"};
        int half = (bodyW - 8) / 2;
        action(g, "Форма: " + shapes[c.particleShape], bodyX, cursor, half, () -> { c.particleShape = (c.particleShape + 1) % 3; changed(); });
        action(g, "Разлёт: " + patterns[c.particlePattern], bodyX + half + 8, cursor, half, () -> { c.particlePattern = (c.particlePattern + 1) % 3; changed(); });
        cursor += 32;
        toggle(g, "ambient", "Частицы в воздухе", "Светлячки, снег, звёзды, угольки или сердечки вокруг вас", c.ambientEnabled, () -> { c.ambientEnabled = !c.ambientEnabled; changed(); }, null);
        chips(g, AIR_STYLES, c.ambientStyle, i -> { c.ambientStyle = i; c.ambientEnabled = true; changed(); });
        slider(g, "Количество", c.ambientCount, 10, 200, v -> c.ambientCount = (int) Math.round(v), true);
        slider(g, "Размер частиц", c.ambientSize, 0.5, 2, v -> c.ambientSize = v, false);
        slider(g, "Радиус вокруг вас · блоки", c.ambientRange, 4, 24, v -> c.ambientRange = v, false);
        slider(g, "Скорость", c.ambientSpeed, 0.2, 3, v -> c.ambientSpeed = v, false);
        toggle(g, "marker", "Маркер удара", "В центре последней видимой цели", c.markerEnabled, () -> { c.markerEnabled = !c.markerEnabled; changed(); }, null);
        button(g, "Форма: " + (c.markerShape == 0 ? "круг" : "квадрат"), () -> { c.markerShape = 1 - c.markerShape; changed(); });
        slider(g, "Длительность · сек", c.markerDuration, 1, 3, v -> c.markerDuration = v, false);
        slider(g, "Размер маркера", c.markerSize, 0.15, 0.9, v -> c.markerSize = v, false);
        toggle(g, "kill", "Kill Effect", "Столб света и искры, когда ваша цель погибает", c.killEffect, () -> { c.killEffect = !c.killEffect; changed(); }, null);
        slider(g, "Огонь на экране · %", c.fireHeight * 100, 0, 100, v -> c.fireHeight = v / 100, true);
        toggle(g, "hat", "Шляпы", Hats.COUNT + " видов: корона, нимб, цилиндр, рожки… · выбор по стрелке", c.hatEnabled,
                () -> { c.hatEnabled = !c.hatEnabled; changed(); }, () -> select("hat"));
        button(g, Icons.PENCIL, "Редактор шляпы · вид, цвет, размер", () -> minecraft.gui.setScreen(new HatEditorScreen(this)));
        toggle(g, "wings", "Крылья", Hats.WING_COUNT + " видов: ангел, демон, бабочка, дракон, феникс · по стрелке", c.wingsEnabled,
                () -> { c.wingsEnabled = !c.wingsEnabled; changed(); }, () -> select("wings"));
        toggle(g, "trail", "Trails", "Светящийся след за вами", c.trailEnabled, () -> { c.trailEnabled = !c.trailEnabled; changed(); }, null);
        note(g, "Эффекты не видны сквозь блоки.");
    }
    private void hands(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "cooldown", "Без анимации перезарядки", "Оружие не опускается после удара · линия у прицела остаётся", c.noCooldownDip,
                () -> { c.noCooldownDip = !c.noCooldownDip; changed(); }, null);
        toggle(g, "hands", "Положение рук", "Только вид от первого лица", c.viewModelEnabled, () -> { c.viewModelEnabled = !c.viewModelEnabled; changed(); }, null);
        button(g, Icons.PENCIL, "Редактировать в игре", () -> minecraft.gui.setScreen(new HandEditorScreen(this)));
        var swingNames = tech.gulp.lavavisual.effects.SwingStyles.NAMES;
        button(g, Icons.SWORDS, "Анимация удара: " + swingNames[c.swingStyle], () -> { c.swingStyle = (c.swingStyle + 1) % swingNames.length; changed(); });
        section(g, "Пресеты рук");
        String[] presetNames = {"Ваниль", "Компакт", "Низко", "PvP"};
        double[][] presetValues = {{0, 0, 0, 1}, {.06, .04, .12, .8}, {0, -.14, .05, 1}, {.1, -.06, .18, .85}};
        int pw = (bodyW - 24) / 4;
        for (int i = 0; i < presetNames.length; i++) {
            int preset = i;
            action(g, presetNames[i], bodyX + i * (pw + 8), cursor, pw, () -> {
                double[] v = presetValues[preset];
                c.mainHand.x = v[0]; c.mainHand.y = v[1]; c.mainHand.z = v[2]; c.mainHand.scale = v[3];
                c.offHand.x = -v[0]; c.offHand.y = v[1]; c.offHand.z = v[2]; c.offHand.scale = v[3];
                if (preset != 0) c.viewModelEnabled = true;
                changed();
            });
        }
        cursor += 32;
        note(g, "Редактор оставляет центр и руки видимыми.");
        note(g, "Дальность — от камеры, не дальность удара.");
        button(g, Icons.ROTATE_CCW, "Сбросить обе руки", () -> { c.mainHand = new HudConfig.Hand(); c.offHand = new HudConfig.Hand(); changed(); });
    }
    private void hand(GuiGraphicsExtractor g, String label, HudConfig.Hand h) {
        section(g, label);
        slider(g, "X · вправо / влево", h.x, -1, 1, v -> h.x = v, false);
        slider(g, "Y · выше / ниже", h.y, -1, 1, v -> h.y = v, false);
        slider(g, "Z · дальше от камеры", h.z, -0.5, 1.5, v -> h.z = v, false);
        slider(g, "Размер", h.scale, 0.4, 1.8, v -> h.scale = v, false);
    }
    private void audio(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        String[] titles = {"Удары", "Криты", "Тотем", "Убийство"};
        String[] descriptions = {"Вместо ванильного звука удара", "Вместо звука критического удара", "Когда срабатывает тотем", "Когда ваша цель погибает"};
        for (int i = 0; i < 4; i++) {
            int group = i;
            boolean on = switch (i) { case 0 -> c.hitSoundEnabled; case 1 -> c.critSoundEnabled; case 2 -> c.totemSoundEnabled; default -> c.killSoundEnabled; };
            double volume = switch (i) { case 0 -> c.hitVolume; case 1 -> c.critVolume; case 2 -> c.totemVolume; default -> c.killVolume; };
            toggle(g, "sound" + i, titles[i], descriptions[i], on, () -> {
                switch (group) {
                    case 0 -> c.hitSoundEnabled = !c.hitSoundEnabled;
                    case 1 -> c.critSoundEnabled = !c.critSoundEnabled;
                    case 2 -> c.totemSoundEnabled = !c.totemSoundEnabled;
                    default -> c.killSoundEnabled = !c.killSoundEnabled;
                }
                changed();
            }, null);
            int y = cursor, listen = 92, nameW = bodyW - 24 * 2 - 16 - listen - 8;
            iconButton(g, Icons.CHEVRON_LEFT, bodyX, y, () -> step(group, -1));
            int nx = bodyX + 28, index = CustomAudio.selected(group);
            UiDraw.round(g, nx, y, nameW, 24, 6, 0xFF1C1F26);
            UiDraw.round(g, nx + 1, y + 7, 2, 10, 1, accent());
            String counter = Math.min(index + 1, CustomAudio.count()) + " / " + CustomAudio.count();
            int counterW = UiFont.width(g, font, counter, UiFont.Face.SMALL);
            text(g, CustomAudio.name(index), nx + 10, y + 8, 0xFFE8EAF0, nameW - counterW - 22);
            text(g, counter, nx + nameW - counterW - 8, y + 9, 0xFF6B7280, counterW + 2, UiFont.Face.SMALL);
            hit(nx, y, nameW, 24, () -> step(group, 1));
            iconButton(g, Icons.CHEVRON_RIGHT, nx + nameW + 4, y, () -> step(group, 1));
            action(g, Icons.PLAY, "Слушать", bodyX + bodyW - listen, y, listen, () -> CustomAudio.preview(group));
            cursor += 32;
            if (group < 2) {
                // 2.11: the saturated sounds one click away (they are also in the full list above).
                String[] rich = group == 0 ? new String[]{"Сочный", "Панч", "Хлёсткий", "Бум"} : new String[]{"Клинок", "Взрыв", "Молния", "Сияние"};
                int base = CustomAudio.RICH + group * 4, chosen = CustomAudio.selected(group) - base;
                chips(g, rich, chosen >= 0 && chosen < 4 ? chosen : -1, k -> {
                    CustomAudio.select(group, base + k);
                    if (group == 0) c.hitSoundEnabled = true; else c.critSoundEnabled = true;
                    changed(); CustomAudio.preview(group);
                });
            }
            slider(g, "Громкость · %", volume * 100, 0, 100, v -> {
                switch (group) { case 0 -> c.hitVolume = v / 100; case 1 -> c.critVolume = v / 100; case 2 -> c.totemVolume = v / 100; default -> c.killVolume = v / 100; }
            }, true);
            cursor += 8;
        }
        if (collecting) { indexContext = "Библиотека звуков"; for (String name : CustomAudio.NAMES) index(name, "звук", clipTop + 3 + 56); }
        note(g, CustomAudio.IDS.length + " звуков: " + (CustomAudio.IDS.length - 6) + " своих LavaVisual и 6 Kenney CC0.");
        button(g, Icons.REFRESH_CW, "Свои звуки: " + tech.gulp.lavavisual.effects.CustomSounds.names().size() + " · обновить", () -> tech.gulp.lavavisual.effects.CustomSounds.refresh(minecraft));
        note(g, "Папка: config/lavavisual-hud/sounds, файлы .ogg");
        note(g, "После обновления в списке появится «Свой файл».");
    }
    private void iconButton(GuiGraphicsExtractor g, String icon, int x, int y, Runnable callback) {
        boolean over = hover(x, y, 24, 24);
        UiDraw.round(g, x, y, 24, 24, 6, over ? 0xFF30333B : 0xFF24272E);
        UiFont.icon(g, font, icon, x + 7, y + 7, over ? accent() : 0xFFC9D0DA);
        hit(x, y, 24, 24, callback);
    }
    private void step(int group, int delta) {
        CustomAudio.select(group, Math.floorMod(CustomAudio.selected(group) + delta, CustomAudio.count()));
        changed();
        CustomAudio.preview(group);
    }
    private void appearance(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        note(g, "Шрифт HUD · Montserrat как у визуалов, Rubik мягче, Inter как в меню");
        chips(g, UiFont.FAMILIES, c.hudFont, i -> { c.hudFont = i; changed(); });
        slider(g, "Масштаб меню", c.menuScale, 0.6, 1.2, v -> c.menuScale = v, false);
        slider(g, "Непрозрачность меню", c.menuOpacity, 0.25, 1, v -> c.menuOpacity = v, false);
        slider(g, "Затемнение мира", c.menuDim, 0, 0.65, v -> c.menuDim = v, false);
        button(g, Icons.PALETTE, "Цвета меню и всех модулей · вкладка «Цвета»", () -> navigate(PAGE_COLORS));
        toggle(g, "shadows", "Тени панелей", "Мягкая глубина интерфейса", c.shadows, () -> { c.shadows = !c.shadows; changed(); }, null);
        toggle(g, "animations", "Анимации", "Плавные вкладки и переключатели", c.animations, () -> { c.animations = !c.animations; changed(); }, null);
        section(g, "Конфиги");
        for (int i = 1; i <= 5; i++) {
            int slot = i, y = cursor, bw2 = Math.min(84, (bodyW - 150) / 2);
            String info = LavaVisualClient.profileInfo(slot);
            UiDraw.round(g, bodyX, y, bodyW, 28, 6, UiDraw.alpha(0x191C22, c.menuOpacity));
            if (info != null) UiDraw.round(g, bodyX + 1, y + 8, 2, 12, 1, accent());
            text(g, "Конфиг " + slot, bodyX + 10, y + 10, 0xFFE5E9F0, 64);
            text(g, info == null ? "пусто" : info, bodyX + 76, y + 10, info == null ? 0xFF6B7280 : 0xFF9AA3B2, bodyW - 90 - bw2 * 2);
            action(g, "Сохранить", bodyX + bodyW - bw2 * 2 - 8, y + 2, bw2, () -> flash(LavaVisualClient.saveProfile(slot) ? "Конфиг " + slot + " сохранён" : "Не удалось сохранить"));
            if (info != null) action(g, "Загрузить", bodyX + bodyW - bw2 - 2, y + 2, bw2, () -> flash(LavaVisualClient.loadProfile(slot) ? "Конфиг " + slot + " загружен" : "Не удалось загрузить"));
            cursor += 32;
        }
        int hw = (bodyW - 8) / 2;
        action(g, Icons.COPY, "Экспорт в буфер", bodyX, cursor, hw, () -> { minecraft.keyboardHandler.setClipboard(LavaVisualClient.exportConfig()); flash("Конфиг скопирован — можно отправить другу"); });
        action(g, Icons.CLIPBOARD_PASTE, "Импорт из буфера", bodyX + hw + 8, cursor, hw, () -> flash(LavaVisualClient.importConfig(minecraft.keyboardHandler.getClipboard()) ? "Конфиг импортирован" : "В буфере нет конфига LavaVisual"));
        cursor += 32;
        button(g, Icons.FOLDER_OPEN, "Открыть папку конфигов", () -> net.minecraft.util.Util.getPlatform().openPath(LavaVisualClient.configDirectory()));
        note(g, flash != null && System.currentTimeMillis() - flashAt < 3000 ? flash : "Конфиг — это текст: экспортируйте и делитесь.");
        button(g, Icons.POWER, "Выключить все модули", () -> { c.disableAll(); changed(); });
        button(g, Icons.ROTATE_CCW, "Сбросить расположение HUD", LavaVisualClient::resetLayout);
    }
    private void world(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "sky", "Редактор неба", "Оттенок обычного неба, не время суток", c.skyEnabled, () -> { c.skyEnabled = !c.skyEnabled; changed(); }, null);
        String[] channels = {"Небо · R", "Небо · G", "Небо · B"};
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 8;
            slider(g, channels[i], c.skyRgb >> shift & 255, 0, 255, v -> { c.skyRgb = c.skyRgb & ~(255 << shift) | (int) Math.round(v) << shift; c.skyEnabled = true; }, true);
        }
        slider(g, "Смешивание оттенка", c.skyStrength, 0, 1, v -> { c.skyStrength = v; c.skyEnabled = true; }, false);
        UiDraw.round(g, bodyX, cursor, bodyW, 10, 5, 0xFF000000 | c.skyRgb);
        cursor += 18;
        int pw = (bodyW - 16) / 3;
        int[][] presets = {{0x83B9FF, 0}, {0xFF8A3C, 100}, {0x8A4CFF, 100}, {0x2CE08A, 100}, {0xFF4C6A, 100}, {0x0A0E18, 100}};
        String[] presetNames = {"Ваниль", "Закат", "Неон", "Изумруд", "Алый", "Бездна"};
        for (int i = 0; i < presets.length; i++) {
            int preset = i;
            action(g, presetNames[i], bodyX + (i % 3) * (pw + 8), cursor, pw, () -> {
                if (preset == 0) c.skyStrength = 0; else { c.skyRgb = presets[preset][0]; c.skyStrength = 1; c.skyEnabled = true; }
                changed();
            });
            if (i % 3 == 2) cursor += 32;
        }
        if (presets.length % 3 != 0) cursor += 32;
        toggle(g, "boost", "FPS Boost", "Тихая оптимизация без заметной потери картинки", c.fpsBoost, () -> { c.fpsBoost = !c.fpsBoost; tech.gulp.lavavisual.effects.PerformanceMode.update(minecraft); changed(); }, null);
        note(g, "Выключение возвращает прежние настройки.");
    }
    private void settings(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config(); boolean cross = selected.equals("crosshair");
        if (selected.equals("hat")) { hatSettings(g); return; }
        if (selected.equals("wings")) { wingsSettings(g); return; }
        var w = cross ? null : c.widgets.get(selected);
        toggle(g, "setting:" + selected, "Отображение", "Показывать на экране", cross ? c.crosshairEnabled : w.visible, () -> {
            if (cross) c.crosshairEnabled = !c.crosshairEnabled; else w.visible = !w.visible; changed();
        }, null);
        slider(g, "Размер", cross ? c.crosshairScale : w.scale, cross ? 0.6 : HudConfig.SCALE_MIN, cross ? 2 : HudConfig.SCALE_MAX,
                v -> { if (cross) c.crosshairScale = v; else w.scale = HudRenderer.snapScale(v); }, false);
        slider(g, cross ? "Непрозрачность" : "Плотность фона", cross ? c.crosshairOpacity : w.opacity, 0.2, 1, v -> { if (cross) c.crosshairOpacity = v; else w.opacity = v; }, false);
        if (cross) button(g, "Форма: " + new String[]{"", "точка", "плюс", "квадрат"}[c.crosshairShape], () -> { c.crosshairShape = c.crosshairShape % 3 + 1; changed(); });
        else {
            if (selected.equals("target")) slider(g, "Удержание цели · сек", c.targetHold, 0.5, 10, v -> c.targetHold = v, false);
            if (selected.equals("minimap")) mapOptions(g);
            button(g, Icons.MOVE, "Переместить на экране", () -> minecraft.gui.setScreen(new HudEditorScreen(this, selected)));
        }
        section(g, "Цвет");
        colorRow(g, selected, cross ? "Цвет прицела" : "Цвет модуля");
        if (selected.equals("target") || cross) note(g, "Фон всех панелей HUD — во вкладке «Цвета».");
    }

    private void hatSettings(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "hat", "Шляпы", "Вид от 3-го лица · по умолчанию стоит ровно", c.hatEnabled, () -> { c.hatEnabled = !c.hatEnabled; changed(); }, null);
        section(g, "Вид шляпы");
        java.util.function.IntConsumer pickHat = i -> { c.hatType = i + 1; c.hatEnabled = true; changed(); };
        carousel(g, "Вид шляпы", Hats.NAMES, c.hatType - 1, pickHat);
        chips(g, Hats.NAMES, c.hatType - 1, pickHat, 4);
        button(g, Icons.PENCIL, "Открыть редактор · меню скроется", () -> minecraft.gui.setScreen(new HatEditorScreen(this)));
        section(g, "Настройка");
        slider(g, "Размер", c.hatSize, 0.5, 1.8, v -> c.hatSize = v, false);
        slider(g, "Высота над головой", c.hatLift, -0.3, 0.6, v -> c.hatLift = v, false);
        slider(g, "Высота шляпы", c.hatCone, 0.3, 2.5, v -> c.hatCone = v, false);
        slider(g, "Прозрачность", c.hatOpacity, 0.15, 1, v -> c.hatOpacity = v, false);
        slider(g, "Вращение · 0 = стоит ровно", c.hatSpin, 0, 3, v -> c.hatSpin = v < 0.08 ? 0 : v, false);
        String[] styles = {"узор", "сплошной", "градиент"};
        button(g, Icons.PAINTBRUSH, "Стиль: " + styles[c.hatStyle], () -> { c.hatStyle = (c.hatStyle + 1) % 3; changed(); });
        othersSection(g);
        section(g, "Цвет");
        colorRow(g, "hat", "Цвет шляпы");
    }
    private void wingsSettings(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "wings", "Крылья", "Вид от 3-го лица · машут сильнее при ходьбе", c.wingsEnabled, () -> { c.wingsEnabled = !c.wingsEnabled; changed(); }, null);
        section(g, "Вид крыльев");
        java.util.function.IntConsumer pickWings = i -> { c.wingsType = i + 1; c.wingsEnabled = true; changed(); };
        carousel(g, "Вид крыльев", Hats.WING_NAMES, c.wingsType - 1, pickWings);
        chips(g, Hats.WING_NAMES, c.wingsType - 1, pickWings, 5);
        section(g, "Настройка");
        slider(g, "Размер", c.wingsSize, 0.5, 1.6, v -> c.wingsSize = v, false);
        slider(g, "Взмахи · 0 = неподвижно", c.wingsFlap, 0, 2, v -> c.wingsFlap = v < 0.05 ? 0 : v, false);
        slider(g, "Прозрачность", c.wingsOpacity, 0.15, 1, v -> c.wingsOpacity = v, false);
        String[] styles = {"узор", "сплошной", "градиент"};
        button(g, Icons.PAINTBRUSH, "Стиль: " + styles[c.wingsStyle], () -> { c.wingsStyle = (c.wingsStyle + 1) % 3; changed(); });
        othersSection(g);
        section(g, "Цвет");
        colorRow(g, "wings", "Цвет крыльев");
    }
    private void othersSection(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        section(g, "Другие игроки");
        toggle(g, "hat_others", "Шляпы и крылья других игроков", "Видны у игроков LavaVisual, которые ими делятся", c.hatOthers,
                () -> { c.hatOthers = !c.hatOthers; changed(); }, null);
        toggle(g, "badge_share", "Делиться значком, шляпой и крыльями", "Игроки с LavaVisual увидят ваши шляпу и крылья", c.badgeShare,
                () -> { c.badgeShare = !c.badgeShare; changed(); }, null);
        note(g, "Без сервера: вид и цвет передаются через невидимый бит скина, ~30 сек.");
    }
    /** Arrow selector: ‹ name › with the position underneath; the arrows wrap around. */
    private void carousel(GuiGraphicsExtractor g, String title, String[] names, int current, java.util.function.IntConsumer pick) {
        if (collecting) { index(title, String.join(" ", names), cursor); cursor += 50; return; }
        int y = cursor, h = 42, ac = accent(), ac2 = accent2(), n = names.length;
        current = Math.floorMod(current, n);
        mark(g, title, bodyX, y, bodyW, h);
        UiDraw.roundV(g, bodyX, y, bodyW, h, 8, 0xFF23262E, 0xFF1B1D23);
        UiDraw.roundH(g, bodyX, y, bodyW, h, 8, UiDraw.alpha(ac, 0.12), UiDraw.alpha(ac2, 0.05));
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? bodyX + 6 : bodyX + bodyW - 36, target = Math.floorMod(current + (side == 0 ? -1 : 1), n);
            boolean over = hover(x, y + 6, 30, 30);
            UiDraw.round(g, x, y + 6, 30, 30, 8, over ? 0xFF3A3F4B : 0xFF2A2E37);
            UiFont.icon(g, font, side == 0 ? Icons.CHEVRON_LEFT : Icons.CHEVRON_RIGHT, x + 9, y + 15, over ? ac : 0xFFD5DAE3);
            hit(x, y + 6, 30, 30, () -> pick.accept(target));
        }
        String name = names[current], position = (current + 1) + " / " + n;
        int tw = UiFont.width(g, font, name, UiFont.Face.BOLD), pw = UiFont.width(g, font, position, UiFont.Face.SMALL);
        UiFont.gradient(g, font, name, bodyX + (bodyW - tw) / 2, y + 9, UiDraw.mix(ac, 0xFFFFFF, 0.25), UiDraw.mix(ac2, 0xFFFFFF, 0.25), 1, UiFont.Face.BOLD);
        text(g, position, bodyX + (bodyW - pw) / 2, y + 26, 0xFF8C93A1, pw + 2, UiFont.Face.SMALL);
        cursor += 50;
    }
    private void mapOptions(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        String[] zooms = {"ближе", "обычный", "дальше"};
        int half = (bodyW - 8) / 2;
        action(g, Icons.SCALING, "Масштаб: " + zooms[c.mapZoom], bodyX, cursor, half, () -> { c.mapZoom = (c.mapZoom + 1) % 3; changed(); });
        action(g, Icons.LOCATE_FIXED, "Координаты: " + (c.mapCoords ? "вкл" : "выкл"), bodyX + half + 8, cursor, half, () -> { c.mapCoords = !c.mapCoords; changed(); });
        cursor += 32;
        button(g, Icons.MAP_PINNED, "Метки на карте: " + (c.mapWaypoints ? "вкл" : "выкл"), () -> { c.mapWaypoints = !c.mapWaypoints; changed(); });
    }
    private void map(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        var w = c.widgets.get("minimap");
        toggle(g, "minimap", "Миникарта", "Только местность — без игроков, мобов и предметов", w.visible, () -> { w.visible = !w.visible; changed(); }, () -> select("minimap"));
        mapOptions(g);
        note(g, "Карта строится по нескольку строк за тик — FPS не проседает.");
        section(g, "Метки");
        toggle(g, "beams", "Лучи меток", "Столб света над меткой, сквозь блоки не виден", c.waypointBeams, () -> { c.waypointBeams = !c.waypointBeams; changed(); }, null);
        toggle(g, "labels", "Подписи и стрелки", "Название и расстояние; за экраном — стрелка у края", c.waypointLabels, () -> { c.waypointLabels = !c.waypointLabels; changed(); }, null);
        button(g, Icons.PLUS, "Добавить метку · клавиша " + Binds.keyName(Binds.Action.WAYPOINT_ADD), () -> minecraft.gui.setScreen(new WaypointScreen(this, null)));
        if (minecraft.level == null) { note(g, "Зайдите в мир, чтобы увидеть свои метки."); return; }
        var list = Waypoints.all(minecraft);
        if (list.isEmpty()) note(g, "Меток пока нет: нажмите «Добавить метку» или " + Binds.keyName(Binds.Action.WAYPOINT_ADD) + " в игре.");
        String here = Waypoints.dimension(minecraft);
        var player = minecraft.player;
        for (Waypoints.Point p : List.copyOf(list)) {
            int y = cursor, bw = 24;
            UiDraw.round(g, bodyX, y, bodyW, 38, 7, UiDraw.alpha(0x191C22, c.menuOpacity));
            UiDraw.round(g, bodyX + 1, y + 9, 3, 20, 1, 0xFF000000 | p.color);
            text(g, p.name, bodyX + 12, y + 7, p.visible ? 0xFFE5E9F0 : 0xFF7C8594, bodyW - 12 - bw * 3 - 24, UiFont.Face.BOLD);
            String where = "X " + p.x + "  Y " + p.y + "  Z " + p.z;
            if (!here.equals(p.dimension)) where += " · " + p.dimension.replace("minecraft:", "");
            else if (player != null) where += " · " + Waypoints.distance(Math.sqrt(player.distanceToSqr(p.x + 0.5, p.y, p.z + 0.5)));
            text(g, where, bodyX + 12, y + 22, 0xFF838994, bodyW - 12 - bw * 3 - 24, UiFont.Face.SMALL);
            int bx = bodyX + bodyW - (bw + 4) * 3 - 3;
            iconButton(g, p.visible ? Icons.EYE : Icons.EYE_OFF, bx, y + 7, () -> { p.visible = !p.visible; Waypoints.save(); });
            iconButton(g, Icons.PENCIL, bx + bw + 4, y + 7, () -> minecraft.gui.setScreen(new WaypointScreen(this, p)));
            boolean armed = confirmDelete == p && System.currentTimeMillis() - confirmAt < 3000;
            if (armed) UiDraw.round(g, bx + (bw + 4) * 2 - 1, y + 6, bw + 2, bw + 2, 7, 0xFFE0524A);
            iconButton(g, Icons.TRASH_2, bx + (bw + 4) * 2, y + 7, () -> {
                if (confirmDelete == p && System.currentTimeMillis() - confirmAt < 3000) { Waypoints.remove(minecraft, p); confirmDelete = null; flash("Метка удалена"); }
                else { confirmDelete = p; confirmAt = System.currentTimeMillis(); flash("Нажмите ещё раз, чтобы удалить «" + p.name + "»"); }
            });
            cursor += 44;
        }
        note(g, flash != null && System.currentTimeMillis() - flashAt < 3000 ? flash : "Метки хранятся отдельно для каждого сервера и мира.");
    }
    private void binds(GuiGraphicsExtractor g) {
        note(g, "Работают в игре, когда меню закрыто. Нажмите на клавишу справа.");
        int keyW = Math.min(130, bodyW / 3);
        for (Binds.Action action : Binds.Action.values()) {
            int y = cursor;
            if (collecting) index(action.title, "бинд клавиша", y);
            boolean listening = capturing == action, conflict = Binds.conflicts(action, minecraft);
            UiDraw.round(g, bodyX, y, bodyW, 30, 7, UiDraw.alpha(0x191C22, LavaVisualClient.config().menuOpacity));
            UiFont.icon(g, font, action.icon, bodyX + 10, y + 10, listening ? accent() : 0xFFAEB6C4);
            text(g, action.title, bodyX + 28, y + 11, 0xFFE5E9F0, bodyW - keyW - 70);
            int kx = bodyX + bodyW - keyW - 34;
            boolean over = hover(kx, y + 4, keyW, 22);
            UiDraw.round(g, kx, y + 4, keyW, 22, 6, listening ? UiDraw.alpha(accent(), 0.28) : over ? 0xFF30333B : 0xFF24272E);
            String label = listening ? "нажмите клавишу…" : Binds.keyName(action);
            int lw = Math.min(keyW - 10, UiFont.width(g, font, label, UiFont.Face.REGULAR));
            text(g, label, kx + (keyW - lw) / 2, y + 11, listening ? accent() : conflict ? 0xFFFF7A6B : Binds.mapping(action).isUnbound() ? 0xFF6B7280 : 0xFFE8EAF0, lw + 2);
            hit(kx, y + 4, keyW, 22, () -> capturing = capturing == action ? null : action);
            int rx = bodyX + bodyW - 28;
            boolean isDefault = Binds.mapping(action).isDefault();
            if (!isDefault) {
                boolean o = hover(rx, y + 4, 22, 22);
                UiDraw.round(g, rx, y + 4, 22, 22, 6, o ? 0xFF30333B : 0xFF24272E);
                UiFont.icon(g, font, Icons.ROTATE_CCW, rx + 6, y + 10, o ? accent() : 0xFF9AA3B2);
                hit(rx, y + 4, 22, 22, () -> { Binds.reset(action, minecraft); capturing = null; });
            }
            cursor += 34;
        }
        note(g, capturing != null ? "Esc — отмена, Backspace — удалить бинд, кнопки мыши 3–8 тоже можно." : "Красным — клавиша занята другим действием.");
        button(g, Icons.ROTATE_CCW, "Сбросить все бинды", () -> { Binds.resetAll(minecraft); capturing = null; });
        note(g, "Те же бинды есть в Настройки → Управление → LavaVisual.");
    }
    private void colors(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        section(g, "Тема");
        int tw = (bodyW - 16) / 3;
        for (int i = 0; i < HudConfig.THEMES.length; i++) {
            int[] pair = HudConfig.THEMES[i];
            int tx = bodyX + (i % 3) * (tw + 8);
            action(g, HudConfig.THEME_NAMES[i], tx, cursor, tw - 24, () -> { c.rgb = pair[0]; c.rgb2 = pair[1]; hsvCache.remove("theme"); hsvCache.remove("theme2"); changed(); });
            UiDraw.roundH(g, tx + tw - 20, cursor + 2, 20, 20, 6, 0xFF000000 | pair[0], 0xFF000000 | pair[1]);
            if (c.rgb == pair[0] && c.rgb2 == pair[1]) UiFont.icon(g, font, Icons.CHECK, tx + tw - 15, cursor + 7, 0xFF101217);
            hit(tx + tw - 20, cursor + 2, 20, 20, () -> { c.rgb = pair[0]; c.rgb2 = pair[1]; hsvCache.remove("theme"); hsvCache.remove("theme2"); changed(); });
            if (i % 3 == 2 || i == HudConfig.THEMES.length - 1) cursor += 32;
        }
        colorRow(g, "theme", "Цвет темы · для всего, где «как тема»");
        colorRow(g, "theme2", "Второй цвет темы · градиенты");
        slider(g, "Скорость переливания", c.chromaSpeed, 0.2, 3, v -> c.chromaSpeed = v, false);
        section(g, "Интерфейс");
        colorRow(g, "menu", "Акцент меню");
        colorRow(g, "menu_bg", "Фон меню");
        colorRow(g, "hud_bg", "Фон панелей HUD");
        section(g, "HUD");
        for (String id : List.of("watermark", "target", "keys", "armor", "coordinates", "performance", "totems", "minimap")) colorRow(g, id, HudRenderer.title(id));
        colorRow(g, "badge", "Значок у ников");
        section(g, "Эффекты");
        String[][] effects = {{"crosshair", "Прицел"}, {"jump", "Jump Circle"}, {"particles", "Hit Particles"}, {"ambient", "Частицы в воздухе"},
                {"marker", "Маркер удара"}, {"esp", "Target ESP"}, {"kill", "Kill Effect"}, {"hat", "Шляпа"}, {"wings", "Крылья"}, {"trail", "Trails"}, {"waypoint", "Новые метки"}};
        for (String[] e : effects) colorRow(g, e[0], e[1]);
        button(g, Icons.ROTATE_CCW, "Все цвета — как тема", () -> { c.colors.clear(); c.chroma.clear(); hsvCache.clear(); changed(); });
    }
    private int currentRgb(String key) {
        var c = LavaVisualClient.config();
        return key.equals("theme") ? c.rgb : key.equals("theme2") ? c.rgb2 : c.color(key) & 0xFFFFFF;
    }
    private void setColor(String key, int rgb) {
        var c = LavaVisualClient.config();
        if (key.equals("theme")) c.rgb = rgb & 0xFFFFFF;
        else if (key.equals("theme2")) c.rgb2 = rgb & 0xFFFFFF;
        else { c.colors.put(key, rgb & 0xFFFFFF); c.chroma.remove(key); }
    }
    /** One element: swatch, name and state; click to open the picker below. */
    private void colorRow(GuiGraphicsExtractor g, String key, String title) {
        if (collecting) { index(title, "цвет", cursor); cursor += 34; return; }
        var c = LavaVisualClient.config();
        int y = cursor;
        mark(g, title, bodyX, y, bodyW, 30);
        boolean open = key.equals(colorOpen), theme = key.equals("theme") || key.equals("theme2");
        boolean rainbow = !theme && c.chroma.contains(key), custom = theme || c.customColor(key);
        int color = key.equals("theme") ? c.accent() : key.equals("theme2") ? c.accent2() : c.color(key);
        int color2 = theme ? color : c.color2(key);
        double over = motion("hover:color:" + key, hover(bodyX, y, bodyW, 30) ? 1 : 0);
        double lift = open ? 1 : over;
        UiDraw.roundV(g, bodyX, y, bodyW, 30, 7, UiDraw.alpha(blend(0x1C1F26, 0x2A2F38, lift), c.menuOpacity), UiDraw.alpha(blend(0x16181E, 0x22262E, lift), c.menuOpacity));
        UiDraw.round(g, bodyX + 8, y + 6, 18, 18, 6, 0x40FFFFFF);
        UiDraw.roundH(g, bodyX + 9, y + 7, 16, 16, 5, color, color2);
        String state = rainbow ? "переливание" : custom ? ColorMath.hex(color) : "как тема";
        int sw = UiFont.width(g, font, state, UiFont.Face.SMALL);
        text(g, title, bodyX + 34, y + 11, 0xFFE5E9F0, bodyW - 34 - sw - 34);
        text(g, state, bodyX + bodyW - sw - 26, y + 12, custom || rainbow ? 0xFFB8C0CD : 0xFF6B7280, sw + 2, UiFont.Face.SMALL);
        UiFont.icon(g, font, open ? Icons.CHEVRON_LEFT : Icons.CHEVRON_RIGHT, bodyX + bodyW - 18, y + 10, open ? accent() : 0xFF9AA3B2);
        hit(bodyX, y, bodyW, 30, () -> { colorOpen = open ? null : key; hsvCache.remove(key); });
        cursor += 34;
        if (open) picker(g, key);
    }
    private void picker(GuiGraphicsExtractor g, String key) {
        var c = LavaVisualClient.config();
        boolean theme = key.equals("theme") || key.equals("theme2");
        int rgb = currentRgb(key);
        double[] hsv = hsvCache.get(key);
        if (hsv == null || ColorMath.hsv(hsv[0], hsv[1], hsv[2]) != rgb) {
            double[] fresh = ColorMath.toHsv(rgb);
            if (hsv != null && fresh[1] < 0.01) fresh[0] = hsv[0];
            hsv = fresh; hsvCache.put(key, hsv);
        }
        final double[] state = hsv;
        int x = bodyX + 10, w = bodyW - 20;
        UiDraw.round(g, bodyX, cursor - 2, bodyW, 4, 2, UiDraw.alpha(accent(), 0.25));
        if (!theme) {
            String[] modes = {"Как тема", "Свой цвет", "Переливание"};
            int mode = c.chroma.contains(key) ? 2 : c.customColor(key) ? 1 : 0, mw = (bodyW - 16) / 3;
            for (int i = 0; i < 3; i++) {
                int m = i, mx0 = bodyX + i * (mw + 8);
                boolean on = mode == i, o = hover(mx0, cursor + 6, mw, 22);
                UiDraw.round(g, mx0, cursor + 6, mw, 22, 6, on ? UiDraw.alpha(accent(), 0.3) : o ? 0xFF30333B : 0xFF24272E);
                int lw = Math.min(mw - 8, UiFont.width(g, font, modes[i], UiFont.Face.REGULAR));
                text(g, modes[i], mx0 + (mw - lw) / 2, cursor + 13, on ? 0xFFFFFFFF : 0xFFC9D0DA, lw + 2);
                hit(mx0, cursor + 6, mw, 22, () -> {
                    if (m == 0) { c.colors.remove(key); c.chroma.remove(key); }
                    else if (m == 1) { c.chroma.remove(key); c.colors.put(key, c.color(key) & 0xFFFFFF); }
                    else if (!c.chroma.contains(key)) c.chroma.add(key);
                    hsvCache.remove(key); changed();
                });
            }
            cursor += 34;
        }
        gradientBar(g, "Оттенок", state[0], t -> ColorMath.hsv(t, 1, 1), v -> { state[0] = Math.min(0.999, v); setColor(key, ColorMath.hsv(state[0], state[1], state[2])); });
        gradientBar(g, "Насыщенность", state[1], t -> ColorMath.hsv(state[0], t, Math.max(0.35, state[2])), v -> { state[1] = v; setColor(key, ColorMath.hsv(state[0], state[1], state[2])); });
        gradientBar(g, "Яркость", state[2], t -> ColorMath.hsv(state[0], state[1], t), v -> { state[2] = v; setColor(key, ColorMath.hsv(state[0], state[1], state[2])); });
        int pw = (bodyW - (PRESETS.length - 1) * 4) / PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) {
            int preset = PRESETS[i], px = bodyX + i * (pw + 4);
            if ((rgb & 0xFFFFFF) == preset && (theme || c.customColor(key))) UiDraw.round(g, px - 1, cursor - 1, pw + 2, 18, 5, 0xFFFFFFFF);
            UiDraw.round(g, px, cursor, pw, 16, 4, 0xFF000000 | preset);
            hit(px, cursor, pw, 16, () -> { setColor(key, preset); hsvCache.remove(key); changed(); });
        }
        cursor += 24;
        int hw = (bodyW - 16) / 3;
        UiDraw.round(g, bodyX, cursor, hw, 24, 6, 0xFF1C1F26);
        UiDraw.round(g, bodyX + 6, cursor + 6, 12, 12, 4, 0xFF000000 | rgb);
        text(g, ColorMath.hex(rgb), bodyX + 24, cursor + 8, 0xFFE8EAF0, hw - 28);
        action(g, Icons.COPY, "Копировать", bodyX + hw + 8, cursor, hw, () -> { minecraft.keyboardHandler.setClipboard(ColorMath.hex(currentRgb(key))); flash("Цвет скопирован"); });
        action(g, Icons.CLIPBOARD_PASTE, "Вставить HEX", bodyX + (hw + 8) * 2, cursor, hw, () -> {
            int parsed = ColorMath.parse(minecraft.keyboardHandler.getClipboard());
            if (parsed < 0) flash("В буфере нет цвета вида #FF5A36"); else { setColor(key, parsed); hsvCache.remove(key); changed(); flash("Цвет вставлен"); }
        });
        cursor += 30;
        if (flash != null && System.currentTimeMillis() - flashAt < 2500) note(g, flash);
        cursor += 4;
    }
    /** Slider whose track shows the resulting colours; value in 0..1. */
    private void gradientBar(GuiGraphicsExtractor g, String label, double value, java.util.function.DoubleUnaryOperator colorAt, DoubleConsumer setter) {
        int y = cursor, x = bodyX + 4, w = bodyW - 8;
        text(g, label, bodyX + 2, y + 1, 0xFFB8C0CD, bodyW - 60, UiFont.Face.SMALL);
        String shown = Math.round(value * 100) + "%";
        int vw = UiFont.width(g, font, shown, UiFont.Face.SMALL);
        text(g, shown, bodyX + bodyW - vw - 2, y + 1, 0xFFF2F4F8, vw + 2, UiFont.Face.SMALL);
        for (int i = 0; i < w; i += 2) g.fill(x + i, y + 13, x + Math.min(w, i + 2), y + 19, 0xFF000000 | (int) colorAt.applyAsDouble(i / (double) Math.max(1, w - 1)));
        int knob = x + (int) Math.round(w * Math.clamp(value, 0, 1));
        UiDraw.round(g, knob - 4, y + 10, 8, 12, 4, 0xFF101217);
        UiDraw.round(g, knob - 3, y + 11, 6, 10, 3, 0xFFFFFFFF);
        sliders.add(new Slider(x, y + 8, w, 0, 1, setter));
        cursor += 28;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) capturing = null;
            else if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) { Binds.set(capturing, InputConstants.UNKNOWN, minecraft); capturing = null; }
            else { Binds.set(capturing, InputConstants.getKey(event), minecraft); capturing = null; }
            return true;
        }
        int key = event.key();
        boolean ctrl = (event.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_F) { searchFocused = true; caretAt = System.currentTimeMillis(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && (searchFocused || !query.isEmpty())) {
            if (!query.isEmpty()) setQuery(""); else searchFocused = false;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !query.isBlank()) {
            if (!results.isEmpty() && query.trim().equals(resultsFor)) openResult(results.getFirst());
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (ctrl) setQuery(""); else if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_V) { paste(); return true; }
            if (key < 256 && !ctrl) return true; // printable keys arrive in charTyped; a letter bound to the menu must not close it
        }
        var menuKey = Binds.mapping(Binds.Action.MENU);
        if (menuKey != null && !menuKey.isUnbound() && menuKey.matches(event) && dragging == null) { onClose(); return true; }
        return super.keyPressed(event);
    }
    /** Typing anywhere in the menu starts a search. */
    @Override public boolean charTyped(CharacterEvent event) {
        if (capturing != null) return true;
        int cp = event.codepoint();
        if (Character.isISOControl(cp) || !Character.isDefined(cp)) return false;
        if (!searchFocused) {
            if (Character.isWhitespace(cp) || dragging != null || moving) return false;
            searchFocused = true;
        }
        setQuery(query + Character.toString(cp));
        return true;
    }
    private void paste() {
        String clip = minecraft == null ? null : minecraft.keyboardHandler.getClipboard();
        if (clip == null) return;
        StringBuilder text = new StringBuilder();
        clip.codePoints().forEach(cp -> { if (!Character.isISOControl(cp)) text.appendCodePoint(cp); else if (cp == '\n' || cp == '\t') text.append(' '); });
        setQuery((query + text).replaceAll("\\s+", " "));
    }
    private void setQuery(String value) {
        query = value.length() > 48 ? value.substring(0, 48) : value;
        caretAt = System.currentTimeMillis();
        if (query.isBlank()) { resultsFor = null; results = List.of(); }
    }
    /** Opens the menu with a search already typed (UI smoke test). */
    public ClickGuiScreen withSearch(String text) { setQuery(text); searchFocused = true; return this; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null) {
            if (event.button() >= 2) { Binds.set(capturing, InputConstants.Type.MOUSE.getOrCreate(event.button()), minecraft); capturing = null; return true; }
            if (event.button() == 1) { capturing = null; return true; }
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double ux = event.x() / renderScale, uy = event.y() / renderScale;
        if (!(ux >= searchX && ux < searchX + searchW && uy >= searchY && uy < searchY + 22)) searchFocused = false;
        if ((event.y() / renderScale) >= clipTop && (event.y() / renderScale) < clipBottom) for (Slider slider : sliders) {
            if ((event.x() / renderScale) >= slider.x - 4 && (event.x() / renderScale) <= slider.x + slider.width + 4 && (event.y() / renderScale) >= slider.y && (event.y() / renderScale) < slider.y + 20) {
                dragging = slider; slider.set((event.x() / renderScale)); return true;
            }
        }
        for (Hit hit : hits) {
            if (hit.clipped && ((event.y() / renderScale) < clipTop || (event.y() / renderScale) >= clipBottom)) continue;
            if ((event.x() / renderScale) >= hit.x && (event.x() / renderScale) < hit.x + hit.w && (event.y() / renderScale) >= hit.y && (event.y() / renderScale) < hit.y + hit.h) { hit.action.run(); return true; }
        }
        if (grabZone((int) ux, (int) uy)) {
            var c = LavaVisualClient.config();
            if (doubleClick) { c.menuX = 0.5; c.menuY = 0.5; changed(); return true; }
            moving = true; grabX = ux - left; grabY = uy - top; return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    /** Header strip (without the close button) and the logo block move the menu; double-click centres it. */
    private boolean grabZone(int x, int y) {
        boolean header = x >= left + side && x < left + panelW - 34 && y >= top && y < top + 38
                && !(x >= searchX - 3 && x < searchX + searchW + 3 && y >= searchY - 3 && y < searchY + 25);
        boolean brand = x >= left + 4 && x < left + side - 2 && y >= top + 4 && y < top + 66;
        return panelW > 0 && (header || brand);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (moving) {
            var c = LavaVisualClient.config();
            double nx = event.x() / renderScale - grabX, ny = event.y() / renderScale - grabY;
            c.menuX = freeW > 0 ? Math.clamp(nx / freeW, 0, 1) : 0.5;
            c.menuY = freeH > 0 ? Math.clamp(ny / freeH, 0, 1) : 0.5;
            return true;
        }
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        dragging.set((event.x() / renderScale)); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (moving) { moving = false; changed(); return true; }
        if (dragging != null) { dragging = null; changed(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        x /= renderScale; y /= renderScale;
        if (x >= bodyX && x <= bodyX + bodyW && y >= clipTop && y < clipBottom && dragging == null) {
            scroll = Math.clamp(scroll - vertical * 30, 0, Math.max(0, contentHeight - (clipBottom - clipTop))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { dragging = null; moving = false; changed(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
