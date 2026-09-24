package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.ColorMath;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * Live China Hat editor: the menu closes, the camera turns to third person so the real hat is visible,
 * and the previous camera is restored when the editor closes.
 */
public final class HatEditorScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) { }
    private record Bar(int x, int y, int w, double min, double max, DoubleConsumer setter) {
        void set(double mouse) { setter.accept(min + Math.clamp((mouse - x) / w, 0, 1) * (max - min)); }
    }
    private static final int[] SWATCHES = {0xFF5A36, 0xFFC233, 0x85F56A, 0x36C8FF, 0x4C6BFF, 0xB45CFF, 0xFF5C9A, 0xFFFFFF, 0x1E1F24};
    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();
    private Bar dragging;
    private CameraType previous;
    private boolean front = true;
    private int px, py, pw, cursor, row = 24, buttonRow = 24;
    public HatEditorScreen(Screen parent) { super(UiFont.component("China Hat")); this.parent = parent; }

    @Override protected void init() {
        if (previous == null && minecraft.options != null) {
            previous = minecraft.options.getCameraType();
            minecraft.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        }
        var c = LavaVisualClient.config();
        if (!c.hatEnabled) { c.hatEnabled = true; LavaVisualClient.save(); }
    }
    @Override public void removed() {
        if (previous != null && minecraft.options != null) minecraft.options.setCameraType(previous);
        previous = null;
        LavaVisualClient.save();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
    }
    private int accent() { return LavaVisualClient.config().color("menu"); }
    private boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear(); bars.clear();
        var c = LavaVisualClient.config();
        pw = Math.min(196, width - 16); px = 8; py = 8;
        // Compact rows on short screens (phones with a large GUI scale).
        boolean compact = height < 290;
        row = compact ? 20 : 24; buttonRow = compact ? 22 : 24;
        int ph = Math.min(height - 16, 34 + row * 6 + 18 + buttonRow * 3 + 6);
        UiDraw.round(g, px, py, pw, ph, 9, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.9));
        UiDraw.round(g, px + 8, py + 8, 18, 18, 6, accent());
        UiFont.icon(g, font, Icons.CONE, px + 12, py + 12, 0xFF11181A);
        UiFont.text(g, font, "China Hat · редактор", px + 32, py + 9, 0xFFF1F4F8, pw - 40, UiFont.Face.BOLD);
        UiFont.text(g, font, minecraft.level == null ? "зайдите в мир, чтобы видеть шляпу" : "изменения видны сразу", px + 32, py + 20, 0xFF8C93A1, pw - 40, UiFont.Face.SMALL);
        cursor = py + 34;
        bar(g, mx, my, "Размер", c.hatSize, 0.5, 1.8, v -> c.hatSize = v, "%.2f");
        bar(g, mx, my, "Высота над головой", c.hatLift, -0.3, 0.6, v -> c.hatLift = v, "%+.2f");
        bar(g, mx, my, "Высота конуса", c.hatCone, 0.3, 2.5, v -> c.hatCone = v, "%.2f");
        bar(g, mx, my, "Прозрачность", c.hatOpacity, 0.15, 1, v -> c.hatOpacity = v, "%.2f");
        bar(g, mx, my, "Вращение · 0 = стоит ровно", c.hatSpin, 0, 3, v -> c.hatSpin = v < 0.08 ? 0 : v, "%.1f");
        double[] hsv = ColorMath.toHsv(c.color("hat") & 0xFFFFFF);
        hueBar(g, mx, my, hsv, c);
        int sw = (pw - 16 - (SWATCHES.length - 1) * 3) / SWATCHES.length;
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = px + 8 + i * (sw + 3), color = SWATCHES[i];
            boolean selected = c.customColor("hat") && (c.colors.get("hat") & 0xFFFFFF) == color;
            if (selected) UiDraw.round(g, x - 1, cursor - 1, sw + 2, 14, 4, 0xFFFFFFFF);
            UiDraw.round(g, x, cursor, sw, 12, 3, 0xFF000000 | color);
            hits.add(new Hit(x, cursor, sw, 12, () -> { c.colors.put("hat", color); c.chroma.remove("hat"); }));
        }
        cursor += 18;
        int half = (pw - 20) / 2;
        String[] styles = {"полосы", "сплошной", "градиент"};
        button(g, mx, my, "Стиль: " + styles[c.hatStyle], px + 8, half, () -> c.hatStyle = (c.hatStyle + 1) % 3);
        button(g, mx, my, "Наклон: " + (c.hatTilt ? "с головой" : "ровно"), px + 12 + half, half, () -> c.hatTilt = !c.hatTilt);
        cursor += buttonRow;
        button(g, mx, my, c.customColor("hat") || c.chroma.contains("hat") ? "Цвет темы" : "Цвет: тема", px + 8, half, () -> { c.colors.remove("hat"); c.chroma.remove("hat"); });
        button(g, mx, my, "Вид: " + (front ? "спереди" : "сзади"), px + 12 + half, half, () -> {
            front = !front;
            if (previous != null) minecraft.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        });
        cursor += buttonRow;
        button(g, mx, my, "Сброс", px + 8, half, () -> {
            HudConfig d = new HudConfig();
            c.hatSize = d.hatSize; c.hatLift = d.hatLift; c.hatCone = d.hatCone; c.hatOpacity = d.hatOpacity; c.hatSpin = d.hatSpin;
            c.hatTilt = false; c.hatStyle = 0; c.colors.remove("hat"); c.chroma.remove("hat");
        });
        button(g, mx, my, "Готово", px + 12 + half, half, this::onClose);
        super.extractRenderState(g, mx, my, delta);
    }
    private void bar(GuiGraphicsExtractor g, int mx, int my, String label, double value, double min, double max, DoubleConsumer setter, String format) {
        int x = px + 10, w = pw - 20;
        UiFont.text(g, font, label, x, cursor, 0xFFB8C0CD, w - 36, UiFont.Face.SMALL);
        String shown = String.format(Locale.ROOT, format, value);
        int vw = UiFont.width(g, font, shown, UiFont.Face.SMALL);
        UiFont.text(g, font, shown, x + w - vw, cursor, 0xFFF2F4F8, vw + 2, UiFont.Face.SMALL);
        double progress = Math.clamp((value - min) / (max - min), 0, 1);
        int filled = (int) Math.round(w * progress);
        UiDraw.round(g, x, cursor + 12, w, 4, 2, 0xFF353A43);
        if (filled > 0) UiDraw.round(g, x, cursor + 12, filled, 4, 2, accent());
        UiDraw.round(g, x + filled - 4, cursor + 9, 8, 10, 4, 0xFFF2F5FA);
        bars.add(new Bar(x, cursor + 6, w, min, max, setter));
        cursor += row;
    }
    private void hueBar(GuiGraphicsExtractor g, int mx, int my, double[] hsv, HudConfig c) {
        int x = px + 10, w = pw - 20;
        UiFont.text(g, font, "Цвет шляпы · " + ColorMath.hex(c.color("hat")), x, cursor, 0xFFB8C0CD, w, UiFont.Face.SMALL);
        gradient(g, x, cursor + 11, w, 6, t -> ColorMath.hsv(t / 1000.0, 1, 1));
        int knob = x + (int) Math.round(w * hsv[0]);
        UiDraw.round(g, knob - 3, cursor + 9, 6, 10, 3, 0xFFFFFFFF);
        double s = Math.max(0.55, hsv[1]), v = Math.max(0.6, hsv[2]);
        bars.add(new Bar(x, cursor + 6, w, 0, 1, h -> { c.colors.put("hat", ColorMath.hsv(Math.min(0.999, h), s, v)); c.chroma.remove("hat"); }));
        cursor += row;
    }
    /** Horizontal gradient from vertical strips; colour(t) gets t in 0..1000. */
    static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, IntUnaryOperator color) {
        int step = 2;
        for (int i = 0; i < w; i += step) g.fill(x + i, y, x + Math.min(w, i + step), y + h, 0xFF000000 | color.applyAsInt((int) (i * 1000L / Math.max(1, w - 1))));
    }
    private void button(GuiGraphicsExtractor g, int mx, int my, String title, int x, int w, Runnable action) {
        boolean hover = over(mx, my, x, cursor, w, 20);
        UiDraw.round(g, x, cursor, w, 20, 6, hover ? 0xFF30333B : 0xFF24272E);
        int tw = Math.min(w - 8, UiFont.width(g, font, title, UiFont.Face.REGULAR));
        UiFont.text(g, font, title, x + (w - tw) / 2, cursor + 6, 0xFFE8EAF0, tw + 2);
        hits.add(new Hit(x, cursor, w, 20, action));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Bar bar : bars) if (event.x() >= bar.x - 4 && event.x() <= bar.x + bar.w + 4 && event.y() >= bar.y && event.y() < bar.y + 16) {
                dragging = bar; bar.set(event.x()); return true;
            }
            for (Hit hit : hits) if (over((int) event.x(), (int) event.y(), hit.x, hit.y, hit.w, hit.h)) { hit.action.run(); LavaVisualClient.save(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        dragging.set(event.x()); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) { dragging = null; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) { return super.keyPressed(event); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
