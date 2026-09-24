package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
            Map.entry("watermark", Icons.STAMP), Map.entry("badge", Icons.BADGE_CHECK), Map.entry("crosshair", Icons.CROSSHAIR),
            Map.entry("jump", Icons.CIRCLE_DOT), Map.entry("particles", Icons.SPARKLE), Map.entry("ambient", Icons.SPARKLES),
            Map.entry("marker", Icons.TARGET), Map.entry("esp", Icons.SCAN_EYE), Map.entry("kill", Icons.SKULL),
            Map.entry("hat", Icons.TRIANGLE), Map.entry("trail", Icons.WIND), Map.entry("hands", Icons.HAND),
            Map.entry("sound0", Icons.SWORDS), Map.entry("sound1", Icons.ZAP), Map.entry("sound2", Icons.HEART_PULSE),
            Map.entry("sound3", Icons.SKULL), Map.entry("shadows", Icons.LAYERS), Map.entry("animations", Icons.WAND_SPARKLES),
            Map.entry("sky", Icons.CLOUD_SUN), Map.entry("boost", Icons.ROCKET), Map.entry("setting", Icons.EYE),
            Map.entry("minimap", Icons.MAP), Map.entry("beams", Icons.SIGNPOST), Map.entry("labels", Icons.NAVIGATION),
            Map.entry("mapcoords", Icons.LOCATE_FIXED), Map.entry("mapmarks", Icons.MAP_PINNED), Map.entry("tilt", Icons.MOVE_VERTICAL));
    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final Map<String, Double> motions = new HashMap<>();
    private int page, left, top, panelW, panelH, side, bodyX, bodyW, clipTop, clipBottom, cursor, mx, my;
    private int contentHeight;
    private boolean clippingHits;
    private double scroll, indicator, frameFactor, renderScale = 1;
    private String selected, colorOpen;
    private Slider dragging;
    private Binds.Action capturing;
    private Object confirmDelete;
    private long confirmAt;
    private int tabStep = 29;
    private final Map<String, double[]> hsvCache = new HashMap<>();
    private long opened = System.nanoTime(), lastFrame = opened;
    public ClickGuiScreen() { this(0); }
    public ClickGuiScreen(int page) { this(page, null); }
    public ClickGuiScreen(int page, String selected) {
        super(UiFont.component("LavaVisual")); this.page = Math.clamp(page, 0, TABS.length - 1); indicator = -1;
        if (selected != null && selected.startsWith("color:")) { colorOpen = selected.substring(6); selected = null; }
        this.selected = selected != null && (selected.equals("crosshair") || selected.equals("hat") || HudConfig.IDS.contains(selected)) ? selected : null;
    }
    private String flash;
    private long flashAt;
    private void flash(String message) { flash = message; flashAt = System.currentTimeMillis(); }
    private void changed() { LavaVisualClient.save(); }
    private void navigate(int next) { page = next; selected = null; colorOpen = null; capturing = null; scroll = 0; dragging = null; hits.clear(); sliders.clear(); }
    private void select(String id) { selected = id; scroll = 0; }
    private void hit(int x, int y, int w, int h, Runnable action) { hits.add(new Hit(x, y, w, h, action, clippingHits)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int width) { UiFont.text(g, font, value, x, y, color, Math.max(1, width)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int width, UiFont.Face face) { UiFont.text(g, font, value, x, y, color, Math.max(1, width), face); }
    private int accent() { return LavaVisualClient.config().color("menu"); }
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
        boolean over = hover(x, y, width, 24);
        UiDraw.round(g, x, y, width, 24, 6, over ? 0xFF30333B : 0xFF24272E);
        int tx = x + 9;
        if (icon != null) { UiFont.icon(g, font, icon, x + 8, y + 7, over ? accent() : 0xFFAEB6C4); tx = x + 23; }
        text(g, title, tx, y + 8, 0xFFE8EAF0, width - (tx - x) - 8); hit(x, y, width, 24, callback);
    }
    private void button(GuiGraphicsExtractor g, String title, Runnable callback) { button(g, null, title, callback); }
    private void button(GuiGraphicsExtractor g, String icon, String title, Runnable callback) { action(g, icon, title, bodyX, cursor, bodyW, callback); cursor += 32; }
    private void note(GuiGraphicsExtractor g, String title) { text(g, title, bodyX + 1, cursor + 2, 0xFF838994, bodyW - 2); cursor += 22; }
    private void section(GuiGraphicsExtractor g, String title) {
        UiDraw.round(g, bodyX + 1, cursor + 8, 4, 4, 2, accent());
        text(g, title, bodyX + 10, cursor + 6, accent(), bodyW - 12);
        g.fillGradient(bodyX, cursor + 21, bodyX + bodyW, cursor + 22, UiDraw.alpha(accent(), 0.25), UiDraw.alpha(accent(), 0.25));
        cursor += 28;
    }
    private void tabIcon(GuiGraphicsExtractor g, int i, int x, int y, int color) { UiFont.icon(g, font, TAB_ICONS[i], x, y, color); }
    private void toggle(GuiGraphicsExtractor g, String key, String title, String description, boolean enabled, Runnable callback, Runnable settings) {
        int y = cursor;
        double over = motion("hover:" + key, hover(bodyX, y, bodyW, 48) ? 1 : 0);
        UiDraw.round(g, bodyX, y, bodyW, 48, 7, UiDraw.alpha(blend(0x191C22, 0x262B33, over), LavaVisualClient.config().menuOpacity));
        double lit = motion("lit:" + key, enabled ? 1 : 0);
        if (lit > 0.01) {
            UiDraw.round(g, bodyX, y, bodyW, 48, 7, UiDraw.alpha(accent(), 0.06 * lit));
            UiDraw.round(g, bodyX + 1, y + 11, 2, 26, 1, UiDraw.alpha(accent(), lit));
        }
        String icon = CARD_ICONS.getOrDefault(key.startsWith("setting:") ? "setting" : key, Icons.SLIDERS_HORIZONTAL);
        UiDraw.round(g, bodyX + 10, y + 12, 24, 24, 7, blend(0x262A33, accent() & 0xFFFFFF, lit * 0.3));
        UiFont.icon(g, font, icon, bodyX + 17, y + 19, 0xFF000000 | UiDraw.mix(0xAEB6C4, accent() & 0xFFFFFF, lit));
        text(g, title, bodyX + 44, y + 9, 0xFFE5E9F0, bodyW - 44 - (settings == null ? 51 : 73), UiFont.Face.BOLD);
        text(g, description, bodyX + 44, y + 28, 0xFF838994, bodyW - 44 - 12);
        double on = motion("toggle:" + key, enabled ? 1 : 0);
        int tx = bodyX + bodyW - (settings == null ? 43 : 65);
        UiDraw.round(g, tx, y + 9, 30, 14, 7, blend(0x393E47, accent(), on));
        UiDraw.round(g, tx + 2 + (int) Math.round(on * 16), y + 11, 10, 10, 5, 0xFFF5F7FA);
        hit(bodyX, y, bodyW - (settings == null ? 0 : 26), 48, callback);
        if (settings != null) {
            UiFont.icon(g, font, Icons.CHEVRON_RIGHT, bodyX + bodyW - 20, y + 11, hover(bodyX + bodyW - 26, y, 26, 48) ? accent() : 0xFFB3BAC7);
            hit(bodyX + bodyW - 26, y, 26, 48, settings);
        }
        cursor += 56;
    }
    private void slider(GuiGraphicsExtractor g, String label, double value, double min, double max, DoubleConsumer setter, boolean integer) {
        int y = cursor;
        String shown = integer ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
        text(g, label, bodyX + 2, y + 2, 0xFFB8C0CD, bodyW - 60);
        text(g, shown, bodyX + bodyW - 52, y + 2, 0xFFF2F4F8, 52);
        int x = bodyX + 4, w = bodyW - 8;
        double progress = Math.clamp((value - min) / (max - min), 0, 1);
        UiDraw.round(g, x, y + 21, w, 4, 2, 0xFF353A43);
        int filled = (int) Math.round(w * progress);
        if (filled > 0) UiDraw.round(g, x, y + 21, filled, 4, 2, accent());
        if (filled > 0) g.fillGradient(x, y + 21, x + filled, y + 22, 0x40FFFFFF, 0x00FFFFFF);
        UiDraw.round(g, x + filled - 7, y + 16, 14, 14, 7, UiDraw.alpha(accent(), 0.22));
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
        if (dragging == null) {
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
        panelW = Math.min(600, canvasW - 24); panelH = Math.min(360, canvasH - 24);
        left = (canvasW - panelW) / 2; top = (canvasH - panelH) / 2 + (int) ((1 - enter) * 6);
        side = panelW < 440 ? 88 : 112; bodyX = left + side + 16; bodyW = panelW - side - 32;
        clipTop = top + 49; clipBottom = top + panelH - 33;
        if (c.shadows) {
            UiDraw.round(g, left - 3, top + 2, panelW + 6, panelH + 6, 13, 0x24000000);
            UiDraw.round(g, left, top + 4, panelW, panelH, 11, 0x59000000);
        }
        int menuBg = c.color("menu_bg") & 0xFFFFFF;
        UiDraw.round(g, left, top, panelW, panelH, 10, UiDraw.alpha(menuBg, c.menuOpacity));
        int ac = accent();
        UiDraw.round(g, left + 4, top + 4, side - 6, panelH - 8, 8, UiDraw.alpha(UiDraw.mix(menuBg, 0x000000, 0.35), c.menuOpacity * 0.75));
        g.fillGradient(left + side + 2, top + 1, left + panelW - 10, top + 46, UiDraw.alpha(ac, 0.09), UiDraw.alpha(ac, 0));
        for (int gx = 0; gx < panelW - 24; gx += 3) {
            double t = gx / (double) (panelW - 24), pulse = 0.55 + 0.45 * Math.sin(now / 6e8 + t * 6);
            g.fill(left + 12 + gx, top, left + 15 + gx, top + 1, UiDraw.alpha(ac, 0.8 * Math.sin(Math.PI * t) * pulse));
        }
        g.fillGradient(left + side, top + 15, left + side + 1, top + panelH - 15, UiDraw.alpha(ac, 0.45), 0xFF292D35);
        UiDraw.round(g, left + 9, top + 11, 33, 33, 11, UiDraw.alpha(ac, 0.20));
        UiDraw.round(g, left + 13, top + 15, 25, 25, 7, ac);
        g.fillGradient(left + 15, top + 16, left + 36, top + 28, 0x45FFFFFF, 0x00FFFFFF);
        UiFont.iconLarge(g, font, Icons.FLAME, left + 17, top + 19, 0xFF11181A);
        text(g, "LavaVisual", left + 13, top + 47, 0xFFF1F4F8, side - 18, UiFont.Face.BOLD);
        tabStep = Math.max(20, Math.min(29, (panelH - 72 - 30) / TABS.length));
        int tabH = Math.min(25, tabStep - 2), tabPad = (tabH - 11) / 2;
        if (indicator < 0) indicator = page * tabStep;
        indicator += (page * tabStep - indicator) * frameFactor;
        UiDraw.round(g, left + 8, top + 72 + (int) indicator, side - 16, tabH, 6, UiDraw.alpha(accent(), 0.13));
        UiDraw.round(g, left + 8, top + 72 + tabPad + (int) indicator, 2, 11, 1, accent());
        for (int i = 0; i < TABS.length; i++) {
            int next = i, y = top + 72 + i * tabStep;
            int tabColor = page == i ? accent() : hover(left + 8, y, side - 16, tabH) ? 0xFFC9D0DA : 0xFF929BA9;
            tabIcon(g, i, left + 17, y + tabPad, tabColor);
            text(g, TABS[i], left + 33, y + tabPad + 1, tabColor, side - 40);
            hit(left + 8, y, side - 16, tabH, () -> navigate(next));
        }
        if (72 + TABS.length * tabStep + 14 < panelH - 21) text(g, "26.2 · 2.8", left + 13, top + panelH - 21, 0xFF586272, side - 18);
        String heading = selected == null ? TABS[page] : selected.equals("crosshair") ? "Прицел" : selected.equals("hat") ? "China Hat" : HudRenderer.title(selected);
        text(g, heading, bodyX, top + 17, 0xFFF0F3F7, bodyW - 28, UiFont.Face.HEADING);
        boolean overClose = mx >= left + panelW - 31 && mx < left + panelW - 7 && my >= top + 10 && my < top + 34;
        if (overClose) UiDraw.round(g, left + panelW - 31, top + 10, 24, 24, 6, 0xFF2A2E36);
        UiFont.icon(g, font, Icons.X, left + panelW - 24, top + 17, overClose ? 0xFFFFFFFF : 0xFFABB4C2);
        hit(left + panelW - 31, top + 10, 24, 24, this::onClose);
        g.fill(bodyX, top + 39, bodyX + bodyW, top + 40, 0xFF2B303A);
        g.fill(bodyX, top + 39, bodyX + (int) (bodyW * enter), top + 40, UiDraw.alpha(accent(), 0.32));
        cursor = clipTop + 3 - (int) scroll;
        g.enableScissor(bodyX - 1, clipTop, bodyX + bodyW + 1, clipBottom);
        clippingHits = true;
        if (selected != null) settings(g);
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
            UiDraw.round(g, left + panelW - 8, y, 2, h, 1, UiDraw.alpha(accent(), 0.5));
        }
        if (selected == null) text(g, Binds.keyName(Binds.Action.MENU) + " · меню    " + Binds.keyName(Binds.Action.DISABLE_ALL) + " · всё выкл    "
                + Binds.keyName(Binds.Action.WAYPOINT_ADD) + " · метка", bodyX, top + panelH - 20, 0xFF818C9C, bodyW);
        else {
            UiFont.icon(g, font, Icons.CHEVRON_LEFT, bodyX - 2, top + panelH - 21, 0xFFB3BAC7);
            text(g, "Назад к модулям", bodyX + 11, top + panelH - 20, 0xFFB3BAC7, bodyW - 11);
        }
        if (selected != null) hit(bodyX, clipBottom + 2, bodyW, 27, () -> select(null));
        g.pose().popMatrix();
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
        toggle(g, "badge", "Значок LavaVisual", "Иконка у ников игроков с модом; они видят ваш", cfg.badgeEnabled,
                () -> { cfg.badgeEnabled = !cfg.badgeEnabled; changed(); if (minecraft != null) minecraft.options.broadcastOptions(); }, null);
    }
    private void effects(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
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
        toggle(g, "ambient", "Звёздная пыль", "Декоративные огоньки рядом с вами", c.ambientEnabled, () -> { c.ambientEnabled = !c.ambientEnabled; changed(); }, null);
        toggle(g, "marker", "Маркер удара", "В центре последней видимой цели", c.markerEnabled, () -> { c.markerEnabled = !c.markerEnabled; changed(); }, null);
        button(g, "Форма: " + (c.markerShape == 0 ? "круг" : "квадрат"), () -> { c.markerShape = 1 - c.markerShape; changed(); });
        slider(g, "Длительность · сек", c.markerDuration, 1, 3, v -> c.markerDuration = v, false);
        slider(g, "Размер маркера", c.markerSize, 0.15, 0.9, v -> c.markerSize = v, false);
        toggle(g, "esp", "Target ESP", "Вокруг цели, только если она видна", c.espEnabled, () -> { c.espEnabled = !c.espEnabled; changed(); }, null);
        button(g, "Стиль ESP: " + (c.espStyle == 0 ? "призраки" : "кольцо"), () -> { c.espStyle = 1 - c.espStyle; changed(); });
        toggle(g, "kill", "Kill Effect", "Столб света и искры, когда ваша цель погибает", c.killEffect, () -> { c.killEffect = !c.killEffect; changed(); }, null);
        slider(g, "Огонь на экране · %", c.fireHeight * 100, 0, 100, v -> c.fireHeight = v / 100, true);
        toggle(g, "hat", "China Hat", "Шляпа над головой, вид от 3-го лица", c.hatEnabled, () -> { c.hatEnabled = !c.hatEnabled; changed(); }, () -> select("hat"));
        button(g, Icons.PENCIL, "Редактор шляпы · цвет, размер, высота", () -> minecraft.gui.setScreen(new HatEditorScreen(this)));
        toggle(g, "trail", "Trails", "Светящийся след за вами", c.trailEnabled, () -> { c.trailEnabled = !c.trailEnabled; changed(); }, null);
        note(g, "Эффекты не видны сквозь блоки.");
    }
    private void hands(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
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
            slider(g, "Громкость · %", volume * 100, 0, 100, v -> {
                switch (group) { case 0 -> c.hitVolume = v / 100; case 1 -> c.critVolume = v / 100; case 2 -> c.totemVolume = v / 100; default -> c.killVolume = v / 100; }
            }, true);
            cursor += 8;
        }
        note(g, CustomAudio.IDS.length + " звуков: 21 собственный синтез LavaVisual и 6 Kenney CC0.");
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
        var w = cross ? null : c.widgets.get(selected);
        toggle(g, "setting:" + selected, "Отображение", "Показывать на экране", cross ? c.crosshairEnabled : w.visible, () -> {
            if (cross) c.crosshairEnabled = !c.crosshairEnabled; else w.visible = !w.visible; changed();
        }, null);
        slider(g, "Размер", cross ? c.crosshairScale : w.scale, 0.6, cross ? 2 : 1.6, v -> { if (cross) c.crosshairScale = v; else w.scale = v; }, false);
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
        toggle(g, "hat", "China Hat", "По умолчанию стоит ровно; вид от 3-го лица", c.hatEnabled, () -> { c.hatEnabled = !c.hatEnabled; changed(); }, null);
        button(g, Icons.PENCIL, "Открыть редактор · меню скроется", () -> minecraft.gui.setScreen(new HatEditorScreen(this)));
        slider(g, "Размер", c.hatSize, 0.5, 1.8, v -> c.hatSize = v, false);
        slider(g, "Высота над головой", c.hatLift, -0.3, 0.6, v -> c.hatLift = v, false);
        slider(g, "Высота конуса", c.hatCone, 0.3, 2.5, v -> c.hatCone = v, false);
        slider(g, "Прозрачность", c.hatOpacity, 0.15, 1, v -> c.hatOpacity = v, false);
        slider(g, "Вращение · 0 = стоит ровно", c.hatSpin, 0, 3, v -> c.hatSpin = v < 0.08 ? 0 : v, false);
        String[] styles = {"полосы", "сплошной", "градиент"};
        int half = (bodyW - 8) / 2;
        action(g, "Стиль: " + styles[c.hatStyle], bodyX, cursor, half, () -> { c.hatStyle = (c.hatStyle + 1) % 3; changed(); });
        action(g, "Наклон: " + (c.hatTilt ? "с головой" : "ровно"), bodyX + half + 8, cursor, half, () -> { c.hatTilt = !c.hatTilt; changed(); });
        cursor += 32;
        section(g, "Цвет");
        colorRow(g, "hat", "Цвет шляпы");
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
        String[] themeNames = {"Лава", "Мята", "Океан", "Неон", "Золото", "Роза"};
        int[] themes = {0xFF5A36, 0x85F56A, 0x36C8FF, 0xB45CFF, 0xFFC233, 0xFF5C9A};
        int tw = (bodyW - 16) / 3;
        for (int i = 0; i < themes.length; i++) {
            int theme = i, tx = bodyX + (i % 3) * (tw + 8);
            action(g, themeNames[i], tx, cursor, tw, () -> { c.rgb = themes[theme]; hsvCache.remove("theme"); changed(); });
            UiDraw.round(g, tx + tw - 16, cursor + 8, 8, 8, 4, 0xFF000000 | themes[i]);
            if (i % 3 == 2) cursor += 32;
        }
        colorRow(g, "theme", "Цвет темы · для всего, где «как тема»");
        slider(g, "Скорость переливания", c.chromaSpeed, 0.2, 3, v -> c.chromaSpeed = v, false);
        section(g, "Интерфейс");
        colorRow(g, "menu", "Акцент меню");
        colorRow(g, "menu_bg", "Фон меню");
        colorRow(g, "hud_bg", "Фон панелей HUD");
        section(g, "HUD");
        for (String id : List.of("watermark", "target", "keys", "armor", "coordinates", "performance", "totems", "minimap")) colorRow(g, id, HudRenderer.title(id));
        colorRow(g, "badge", "Значок у ников");
        section(g, "Эффекты");
        String[][] effects = {{"crosshair", "Прицел"}, {"jump", "Jump Circle"}, {"particles", "Hit Particles"}, {"ambient", "Звёздная пыль"},
                {"marker", "Маркер удара"}, {"esp", "Target ESP"}, {"kill", "Kill Effect"}, {"hat", "China Hat"}, {"trail", "Trails"}, {"waypoint", "Новые метки"}};
        for (String[] e : effects) colorRow(g, e[0], e[1]);
        button(g, Icons.ROTATE_CCW, "Все цвета — как тема", () -> { c.colors.clear(); c.chroma.clear(); hsvCache.clear(); changed(); });
    }
    private int currentRgb(String key) {
        var c = LavaVisualClient.config();
        return key.equals("theme") ? c.rgb : c.color(key) & 0xFFFFFF;
    }
    private void setColor(String key, int rgb) {
        var c = LavaVisualClient.config();
        if (key.equals("theme")) c.rgb = rgb & 0xFFFFFF;
        else { c.colors.put(key, rgb & 0xFFFFFF); c.chroma.remove(key); }
    }
    /** One element: swatch, name and state; click to open the picker below. */
    private void colorRow(GuiGraphicsExtractor g, String key, String title) {
        var c = LavaVisualClient.config();
        int y = cursor;
        boolean open = key.equals(colorOpen), theme = key.equals("theme");
        boolean rainbow = !theme && c.chroma.contains(key), custom = theme || c.customColor(key);
        int color = theme ? c.accent() : c.color(key);
        double over = motion("hover:color:" + key, hover(bodyX, y, bodyW, 30) ? 1 : 0);
        UiDraw.round(g, bodyX, y, bodyW, 30, 7, UiDraw.alpha(blend(0x191C22, 0x262B33, open ? 1 : over), c.menuOpacity));
        UiDraw.round(g, bodyX + 8, y + 6, 18, 18, 6, 0x40FFFFFF);
        UiDraw.round(g, bodyX + 9, y + 7, 16, 16, 5, color);
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
        boolean theme = key.equals("theme");
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
        var menuKey = Binds.mapping(Binds.Action.MENU);
        if (menuKey != null && !menuKey.isUnbound() && menuKey.matches(event) && dragging == null) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null) {
            if (event.button() >= 2) { Binds.set(capturing, InputConstants.Type.MOUSE.getOrCreate(event.button()), minecraft); capturing = null; return true; }
            if (event.button() == 1) { capturing = null; return true; }
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if ((event.y() / renderScale) >= clipTop && (event.y() / renderScale) < clipBottom) for (Slider slider : sliders) {
            if ((event.x() / renderScale) >= slider.x - 4 && (event.x() / renderScale) <= slider.x + slider.width + 4 && (event.y() / renderScale) >= slider.y && (event.y() / renderScale) < slider.y + 20) {
                dragging = slider; slider.set((event.x() / renderScale)); return true;
            }
        }
        for (Hit hit : hits) {
            if (hit.clipped && ((event.y() / renderScale) < clipTop || (event.y() / renderScale) >= clipBottom)) continue;
            if ((event.x() / renderScale) >= hit.x && (event.x() / renderScale) < hit.x + hit.w && (event.y() / renderScale) >= hit.y && (event.y() / renderScale) < hit.y + hit.h) { hit.action.run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        dragging.set((event.x() / renderScale)); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
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
    @Override public void onClose() { dragging = null; changed(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
