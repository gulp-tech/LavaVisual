package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final String[] TABS = {"HUD", "Эффекты", "Руки", "Звуки", "RGB / UI", "Мир / FPS"};
    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final Map<String, Double> motions = new HashMap<>();
    private int page, left, top, panelW, panelH, side, bodyX, bodyW, clipTop, clipBottom, cursor, mx, my;
    private int contentHeight;
    private boolean clippingHits;
    private double scroll, indicator, frameFactor, renderScale = 1;
    private String selected;
    private Slider dragging;
    private long opened = System.nanoTime(), lastFrame = opened;
    public ClickGuiScreen() { this(0); }
    public ClickGuiScreen(int page) { this(page, null); }
    public ClickGuiScreen(int page, String selected) {
        super(UiFont.component("LavaVisual")); this.page = Math.clamp(page, 0, 5); indicator = this.page * 29;
        this.selected = selected != null && (selected.equals("crosshair") || HudConfig.IDS.contains(selected)) ? selected : null;
    }
    private void changed() { LavaVisualClient.save(); }
    private void navigate(int next) { page = next; selected = null; scroll = 0; dragging = null; hits.clear(); sliders.clear(); }
    private void select(String id) { selected = id; scroll = 0; }
    private void hit(int x, int y, int w, int h, Runnable action) { hits.add(new Hit(x, y, w, h, action, clippingHits)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int width) { UiFont.text(g, font, value, x, y, color, Math.max(1, width)); }
    private int accent() { return LavaVisualClient.config().accent(); }
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
    private void action(GuiGraphicsExtractor g, String title, int x, int y, int width, Runnable callback) {
        UiDraw.round(g, x, y, width, 24, 6, hover(x, y, width, 24) ? 0xFF30333B : 0xFF24272E);
        text(g, title, x + 9, y + 7, 0xFFE8EAF0, width - 18); hit(x, y, width, 24, callback);
    }
    private void button(GuiGraphicsExtractor g, String title, Runnable callback) { action(g, title, bodyX, cursor, bodyW, callback); cursor += 32; }
    private void note(GuiGraphicsExtractor g, String title) { text(g, title, bodyX + 1, cursor + 2, 0xFF838994, bodyW - 2); cursor += 22; }
    private void section(GuiGraphicsExtractor g, String title) { text(g, title, bodyX + 1, cursor + 6, accent(), bodyW - 2); cursor += 28; }
    private void toggle(GuiGraphicsExtractor g, String key, String title, String description, boolean enabled, Runnable callback, Runnable settings) {
        int y = cursor;
        double over = motion("hover:" + key, hover(bodyX, y, bodyW, 48) ? 1 : 0);
        UiDraw.round(g, bodyX, y, bodyW, 48, 7, UiDraw.alpha(blend(0x191C22, 0x262B33, over), LavaVisualClient.config().menuOpacity));
        text(g, title, bodyX + 11, y + 9, 0xFFE5E9F0, bodyW - 84);
        text(g, description, bodyX + 11, y + 29, 0xFF838994, bodyW - 22);
        double on = motion("toggle:" + key, enabled ? 1 : 0);
        int tx = bodyX + bodyW - (settings == null ? 43 : 65);
        UiDraw.round(g, tx, y + 9, 30, 14, 7, blend(0x393E47, accent(), on));
        UiDraw.round(g, tx + 2 + (int) Math.round(on * 16), y + 11, 10, 10, 5, 0xFFF5F7FA);
        hit(bodyX, y, bodyW - (settings == null ? 0 : 26), 48, callback);
        if (settings != null) {
            text(g, "›", bodyX + bodyW - 19, y + 9, 0xFFB3BAC7, 14);
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
        if (dragging == null) renderScale = Math.min(c.menuScale, Math.min((width - 16) / 364.0, (height - 16) / 294.0));
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
        UiDraw.round(g, left, top, panelW, panelH, 10, UiDraw.alpha(0x12151B, c.menuOpacity));
        g.fill(left + side, top + 15, left + side + 1, top + panelH - 15, 0xFF292D35);
        UiDraw.round(g, left + 13, top + 15, 25, 25, 7, accent());
        text(g, "LV", left + 17, top + 23, 0xFF11181A, 23);
        text(g, "LavaVisual", left + 13, top + 47, 0xFFF1F4F8, side - 18);
        indicator += (page * 29 - indicator) * frameFactor;
        UiDraw.round(g, left + 8, top + 72 + (int) indicator, side - 16, 25, 6, UiDraw.alpha(accent(), 0.13));
        UiDraw.round(g, left + 8, top + 79 + (int) indicator, 2, 11, 1, accent());
        for (int i = 0; i < TABS.length; i++) {
            int next = i, y = top + 72 + i * 29;
            text(g, TABS[i], left + 19, y + 8, page == i ? accent() : 0xFF929BA9, side - 26);
            hit(left + 8, y, side - 16, 25, () -> navigate(next));
        }
        if (panelH > 300) text(g, "26.2 · 2.3", left + 13, top + panelH - 21, 0xFF586272, side - 18);
        text(g, selected == null ? TABS[page] : selected.equals("crosshair") ? "Прицел" : HudRenderer.title(selected), bodyX, top + 20, 0xFFF0F3F7, bodyW - 28);
        text(g, "×", left + panelW - 26, top + 17, 0xFFABB4C2, 16);
        hit(left + panelW - 31, top + 10, 24, 24, this::onClose);
        g.fill(bodyX, top + 39, bodyX + bodyW, top + 40, 0xFF2B303A);
        g.fill(bodyX, top + 39, bodyX + (int) (bodyW * enter), top + 40, UiDraw.alpha(accent(), 0.32));
        cursor = clipTop + 3 - (int) scroll;
        g.enableScissor(bodyX - 1, clipTop, bodyX + bodyW + 1, clipBottom);
        clippingHits = true;
        if (selected != null) settings(g);
        else switch (page) { case 0 -> hud(g); case 1 -> effects(g); case 2 -> hands(g); case 3 -> audio(g); case 4 -> appearance(g); default -> world(g); }
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
        text(g, selected == null ? "Right Shift · меню    V · всё выкл" : "‹ Назад к модулям", bodyX, top + panelH - 20, 0xFF818C9C, bodyW);
        if (selected != null) hit(bodyX, clipBottom + 2, bodyW, 27, () -> select(null));
        g.pose().popMatrix();
    }
    private void hud(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        for (String id : HudConfig.IDS) {
            var w = c.widgets.get(id);
            String desc = id.equals("target") ? "Аватар и данные видимой цели" : id.equals("coordinates") ? "Ваша позиция в мире" : "Частота кадров";
            toggle(g, id, HudRenderer.title(id), desc, w.visible, () -> { w.visible = !w.visible; changed(); }, () -> select(id));
        }
        button(g, "Редактор расположения", () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
    }
    private void effects(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "crosshair", "Прицел", "Форма, размер и прозрачность", c.crosshairEnabled, () -> { c.crosshairEnabled = !c.crosshairEnabled; changed(); }, () -> select("crosshair"));
        toggle(g, "jump", "Jump Circle", "Кольцо при вашем прыжке", c.jumpEnabled, () -> { c.jumpEnabled = !c.jumpEnabled; changed(); }, null);
        slider(g, "Радиус кольца", c.jumpRadius, 0.5, 2, v -> c.jumpRadius = v, false);
        toggle(g, "particles", "Hit Particles", "Искры при ручной атаке", c.particlesEnabled, () -> { c.particlesEnabled = !c.particlesEnabled; changed(); }, null);
        slider(g, "Число искр", c.particleCount, 4, 24, v -> c.particleCount = (int) Math.round(v), true);
        slider(g, "Размер искр", c.particleSize, 0.04, 0.25, v -> c.particleSize = v, false);
        toggle(g, "ambient", "Звёздная пыль", "Декоративные огоньки рядом с вами", c.ambientEnabled, () -> { c.ambientEnabled = !c.ambientEnabled; changed(); }, null);
        toggle(g, "marker", "Маркер удара", "В центре последней видимой цели", c.markerEnabled, () -> { c.markerEnabled = !c.markerEnabled; changed(); }, null);
        button(g, "Форма: " + (c.markerShape == 0 ? "круг" : "квадрат"), () -> { c.markerShape = 1 - c.markerShape; changed(); });
        slider(g, "Длительность · сек", c.markerDuration, 1, 3, v -> c.markerDuration = v, false);
        slider(g, "Размер маркера", c.markerSize, 0.15, 0.9, v -> c.markerSize = v, false);
        note(g, "Эффекты не видны сквозь блоки.");
    }
    private void hands(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "hands", "Положение рук", "Только вид от первого лица", c.viewModelEnabled, () -> { c.viewModelEnabled = !c.viewModelEnabled; changed(); }, null);
        button(g, "Редактировать в игре", () -> minecraft.gui.setScreen(new HandEditorScreen(this)));
        note(g, "Редактор оставляет центр и руки видимыми.");
        note(g, "Дальность — от камеры, не дальность удара.");
        button(g, "Сбросить обе руки", () -> { c.mainHand = new HudConfig.Hand(); c.offHand = new HudConfig.Hand(); changed(); });
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
        String[][] labels = {{"Удары", "Мягкий взмах", "Тяжёлый удар"}, {"Криты", "Металл", "Аркада"}, {"Тотем", "Колокольчик", "Power Up"}};
        for (int i = 0; i < 3; i++) {
            int group = i;
            boolean on = i == 0 ? c.hitSoundEnabled : i == 1 ? c.critSoundEnabled : c.totemSoundEnabled;
            int preset = i == 0 ? c.hitPreset : i == 1 ? c.critPreset : c.totemPreset;
            double volume = i == 0 ? c.hitVolume : i == 1 ? c.critVolume : c.totemVolume;
            toggle(g, "sound" + i, labels[i][0], "Замена ванильного звука · Kenney CC0", on, () -> {
                if (group == 0) c.hitSoundEnabled = !c.hitSoundEnabled; else if (group == 1) c.critSoundEnabled = !c.critSoundEnabled; else c.totemSoundEnabled = !c.totemSoundEnabled; changed();
            }, null);
            int w = (bodyW - 8) / 2;
            int presets = tech.gulp.lavavisual.effects.CustomSounds.names().isEmpty() ? 2 : 3;
            String presetName = preset == 2 ? "Свой" : labels[i][preset + 1];
            action(g, presetName + " ›", bodyX, cursor, w, () -> {
                int next = (preset + 1) % presets;
                if (group == 0) c.hitPreset = next; else if (group == 1) c.critPreset = next; else c.totemPreset = next; changed();
            });
            action(g, "Слушать", bodyX + w + 8, cursor, w, () -> CustomAudio.preview(group)); cursor += 32;
            slider(g, "Громкость · %", volume * 100, 0, 100, v -> {
                if (group == 0) c.hitVolume = v / 100; else if (group == 1) c.critVolume = v / 100; else c.totemVolume = v / 100;
            }, true); cursor += 8;
        }
        button(g, "Свои звуки: " + tech.gulp.lavavisual.effects.CustomSounds.names().size() + " · обновить", () -> tech.gulp.lavavisual.effects.CustomSounds.refresh(minecraft));
        note(g, "Папка: config/lavavisual-hud/sounds, файлы .ogg");
        note(g, "Выберите вариант «Свой» после обновления папки.");
    }
    private void appearance(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        slider(g, "Масштаб меню", c.menuScale, 0.6, 1.2, v -> c.menuScale = v, false);
        slider(g, "Непрозрачность меню", c.menuOpacity, 0.25, 1, v -> c.menuOpacity = v, false);
        slider(g, "Затемнение мира", c.menuDim, 0, 0.65, v -> c.menuDim = v, false);
        UiDraw.round(g, bodyX, cursor, bodyW, 24, 7, accent());
        int luminance = (c.rgb >> 16 & 255) * 3 + (c.rgb >> 8 & 255) * 6 + (c.rgb & 255);
        text(g, String.format(Locale.ROOT, "RGB  #%06X", c.rgb), bodyX + 10, cursor + 8, luminance > 1350 ? 0xFF101820 : 0xFFFFFFFF, bodyW - 20); cursor += 32;
        String[] names = {"R · Красный", "G · Зелёный", "B · Синий"};
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 8;
            slider(g, names[i], c.rgb >> shift & 255, 0, 255, v -> c.rgb = c.rgb & ~(255 << shift) | (int) Math.round(v) << shift, true);
            cursor -= 6;
        }
        note(g, "Один цвет для меню, HUD и эффектов.");
        toggle(g, "shadows", "Тени панелей", "Мягкая глубина интерфейса", c.shadows, () -> { c.shadows = !c.shadows; changed(); }, null);
        toggle(g, "animations", "Анимации", "Плавные вкладки и переключатели", c.animations, () -> { c.animations = !c.animations; changed(); }, null);
        section(g, "Профили настроек");
        int cw = (bodyW - 16) / 3;
        for (int i = 1; i <= 3; i++) {
            int slot = i;
            action(g, "Сохранить " + i, bodyX + (i - 1) * (cw + 8), cursor, cw, () -> LavaVisualClient.saveProfile(slot));
        }
        cursor += 32;
        for (int i = 1; i <= 3; i++) {
            int slot = i;
            action(g, "Загрузить " + i, bodyX + (i - 1) * (cw + 8), cursor, cw, () -> LavaVisualClient.loadProfile(slot));
        }
        cursor += 32;
        note(g, "Профили хранятся рядом с основными настройками.");
        button(g, "Выключить все модули", () -> { c.disableAll(); changed(); });
        button(g, "Сбросить расположение HUD", LavaVisualClient::resetLayout);
    }
    private void world(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        toggle(g, "sky", "Редактор неба", "Оттенок обычного неба, не время суток", c.skyEnabled, () -> { c.skyEnabled = !c.skyEnabled; changed(); }, null);
        String[] channels = {"Небо · R", "Небо · G", "Небо · B"};
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 8;
            slider(g, channels[i], c.skyRgb >> shift & 255, 0, 255, v -> c.skyRgb = c.skyRgb & ~(255 << shift) | (int) Math.round(v) << shift, true);
        }
        slider(g, "Смешивание оттенка", c.skyStrength, 0, 1, v -> c.skyStrength = v, false);
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
        toggle(g, "boost", "FPS Boost", "8 чанков, меньше частиц, без теней сущностей", c.fpsBoost, () -> { c.fpsBoost = !c.fpsBoost; tech.gulp.lavavisual.effects.PerformanceMode.update(minecraft); changed(); }, null);
        note(g, "Выключение возвращает прежние настройки.");
        note(g, "Также уменьшает число наших искр и колец.");
    }
    private void settings(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config(); boolean cross = selected.equals("crosshair");
        var w = cross ? null : c.widgets.get(selected);
        toggle(g, "setting:" + selected, "Отображение", "Цвет задаётся во вкладке RGB / UI", cross ? c.crosshairEnabled : w.visible, () -> {
            if (cross) c.crosshairEnabled = !c.crosshairEnabled; else w.visible = !w.visible; changed();
        }, null);
        slider(g, "Размер", cross ? c.crosshairScale : w.scale, 0.6, cross ? 2 : 1.6, v -> { if (cross) c.crosshairScale = v; else w.scale = v; }, false);
        slider(g, cross ? "Непрозрачность" : "Плотность фона", cross ? c.crosshairOpacity : w.opacity, 0.2, 1, v -> { if (cross) c.crosshairOpacity = v; else w.opacity = v; }, false);
        if (cross) button(g, "Форма: " + new String[]{"", "точка", "плюс", "квадрат"}[c.crosshairShape], () -> { c.crosshairShape = c.crosshairShape % 3 + 1; changed(); });
        else {
            if (selected.equals("target")) slider(g, "Удержание цели · сек", c.targetHold, 0.5, 10, v -> c.targetHold = v, false);
            button(g, "Переместить на экране", () -> minecraft.gui.setScreen(new HudEditorScreen(this, selected)));
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
