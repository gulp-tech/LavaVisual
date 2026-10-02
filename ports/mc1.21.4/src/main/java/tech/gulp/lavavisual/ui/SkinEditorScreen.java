package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import tech.gulp.lavavisual.compat.KeyEvent;
import tech.gulp.lavavisual.compat.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.Hats;
import tech.gulp.lavavisual.config.ColorMath;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * Live skin editor: the menu closes, the camera turns to third person so the whole skin is visible, and the
 * previous camera is restored when the editor closes.
 */
public final class SkinEditorScreen extends Screen {
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
    public SkinEditorScreen(Screen parent) { super(UiFont.component("Скины")); this.parent = parent; }

    @Override protected void init() {
        if (previous == null && minecraft.options != null) {
            previous = minecraft.options.getCameraType();
            minecraft.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        }
        var c = LavaVisualClient.config();
        if (!c.costumeEnabled) { c.costumeEnabled = true; LavaVisualClient.save(); }
    }
    @Override public void removed() {
        if (previous != null && minecraft.options != null) minecraft.options.setCameraType(previous);
        previous = null;
        LavaVisualClient.save();
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
    }
    private int accent() { return LavaVisualClient.config().color("menu"); }
    private boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear(); bars.clear();
        var c = LavaVisualClient.config();
        pw = Math.min(196, width - 16); px = 8; py = 8;
        // Compact rows on short screens (phones with a large GUI scale).
        boolean compact = height < 290;
        row = compact ? 20 : 24; buttonRow = compact ? 22 : 24;
        int ph = Math.min(height - 16, 34 + row * 3 + 18 + buttonRow * 3 + 6);
        UiDraw.round(g, px, py, pw, ph, 9, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.9));
        UiDraw.round(g, px + 8, py + 8, 18, 18, 6, accent());
        UiFont.icon(g, font, Icons.CROWN, px + 12, py + 12, 0xFF11181A);
        UiFont.text(g, font, "Скин · редактор", px + 32, py + 9, 0xFFF1F4F8, pw - 40, UiFont.Face.BOLD);
        UiFont.text(g, font, minecraft.level == null ? "зайдите в мир, чтобы видеть скин" : "изменения видны сразу, шляпа и аксессуары подстроятся", px + 32, py + 20, 0xFF8C93A1, pw - 40, UiFont.Face.SMALL);
        cursor = py + 34;
        // Skin: ‹ name › like every selector in the menu; the arrows wrap around.
        int ty = cursor;
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? px + 8 : px + pw - 28, step = side == 0 ? -1 : 1;
            boolean hover = over(mx, my, x, ty, 20, 18);
            UiDraw.round(g, x, ty, 20, 18, 5, hover ? 0xFF3A3F4B : 0xFF2A2E37);
            UiFont.icon(g, font, side == 0 ? Icons.CHEVRON_LEFT : Icons.CHEVRON_RIGHT, x + 4, ty + 3, hover ? accent() : 0xFFD5DAE3);
            hits.add(new Hit(x, ty, 20, 18, () -> c.costumeType = Math.floorMod(c.costumeType - 1 + step, Hats.COSTUME_COUNT) + 1));
        }
        String skinName = Hats.costumeName(c.costumeType), position = c.costumeType + "/" + Hats.COSTUME_COUNT;
        int nw = UiFont.width(g, font, skinName, UiFont.Face.BOLD), cw = UiFont.width(g, font, position, UiFont.Face.SMALL), nx = px + (pw - nw - cw - 5) / 2;
        UiFont.text(g, font, skinName, nx, ty + 5, 0xFFF1F4F8, nw + 2, UiFont.Face.BOLD);
        UiFont.text(g, font, position, nx + nw + 5, ty + 6, 0xFF8C93A1, cw + 2, UiFont.Face.SMALL);
        cursor += row;
        bar(g, mx, my, "Прозрачность · 1 = непрозрачный", c.costumeOpacity, 0.3, 1, v -> c.costumeOpacity = v, "%.2f");
        double[] hsv = ColorMath.toHsv(c.color("costume") & 0xFFFFFF);
        hueBar(g, mx, my, hsv, c);
        int sw = (pw - 16 - (SWATCHES.length - 1) * 3) / SWATCHES.length;
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = px + 8 + i * (sw + 3), color = SWATCHES[i];
            boolean selected = c.customColor("costume") && (c.colors.get("costume") & 0xFFFFFF) == color;
            if (selected) UiDraw.round(g, x - 1, cursor - 1, sw + 2, 14, 4, 0xFFFFFFFF);
            UiDraw.round(g, x, cursor, sw, 12, 3, 0xFF000000 | color);
            hits.add(new Hit(x, cursor, sw, 12, () -> { c.colors.put("costume", color); c.chroma.remove("costume"); }));
        }
        cursor += 18;
        int half = (pw - 20) / 2;
        String[] styles = {"узор", "сплошной", "градиент"};
        button(g, mx, my, "Стиль: " + styles[c.costumeStyle], px + 8, half, () -> c.costumeStyle = (c.costumeStyle + 1) % 3);
        button(g, mx, my, "Радуга: " + (c.chroma.contains("costume") ? "вкл" : "выкл"), px + 12 + half, half, () -> { if (!c.chroma.remove("costume")) c.chroma.add("costume"); });
        cursor += buttonRow;
        button(g, mx, my, c.customColor("costume") || c.chroma.contains("costume") ? "Цвет темы" : "Цвет: тема", px + 8, half, () -> { c.colors.remove("costume"); c.chroma.remove("costume"); });
        button(g, mx, my, "Вид: " + (front ? "спереди" : "сзади"), px + 12 + half, half, () -> {
            front = !front;
            if (previous != null) minecraft.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        });
        cursor += buttonRow;
        button(g, mx, my, "Сброс", px + 8, half, () -> {
            HudConfig d = new HudConfig();
            c.costumeOpacity = d.costumeOpacity; c.costumeStyle = d.costumeStyle;
            c.colors.remove("costume"); c.chroma.remove("costume");
        });
        button(g, mx, my, "Готово", px + 12 + half, half, this::onClose);
        super.render(g, mx, my, delta);
    }
    private void bar(GuiGraphics g, int mx, int my, String label, double value, double min, double max, DoubleConsumer setter, String format) {
        int x = px + 10, w = pw - 20;
        UiFont.text(g, font, label, x, cursor, 0xFFB8C0CD, w - 36, UiFont.Face.SMALL);
        String shown = String.format(Locale.ROOT, format, value);
        int vw = UiFont.width(g, font, shown, UiFont.Face.SMALL);
        UiFont.text(g, font, shown, x + w - vw, cursor, 0xFFF2F4F8, vw + 2, UiFont.Face.SMALL);
        double progress = Math.clamp((value - min) / (max - min), 0, 1);
        int filled = (int) Math.round(w * progress);
        UiDraw.round(g, x, cursor + 12, w, 4, 2, 0xFF353A43);
        if (filled > 0) UiDraw.roundH(g, x, cursor + 12, filled, 4, 2, accent(), 0xFF000000 | UiDraw.mix(accent(), LavaVisualClient.config().color2("menu"), progress));
        UiDraw.circle(g, x + filled, cursor + 14, 6, UiDraw.alpha(accent(), 0.25));
        UiDraw.circle(g, x + filled, cursor + 14, 4, 0xFFF2F5FA);
        bars.add(new Bar(x, cursor + 6, w, min, max, setter));
        cursor += row;
    }
    private void hueBar(GuiGraphics g, int mx, int my, double[] hsv, HudConfig c) {
        int x = px + 10, w = pw - 20;
        UiFont.text(g, font, "Цвет скина · " + ColorMath.hex(c.color("costume")), x, cursor, 0xFFB8C0CD, w, UiFont.Face.SMALL);
        gradient(g, x, cursor + 11, w, 6, t -> ColorMath.hsv(t / 1000.0, 1, 1));
        int knob = x + (int) Math.round(w * hsv[0]);
        UiDraw.round(g, knob - 3, cursor + 9, 6, 10, 3, 0xFFFFFFFF);
        double s = Math.max(0.55, hsv[1]), v = Math.max(0.6, hsv[2]);
        bars.add(new Bar(x, cursor + 6, w, 0, 1, h -> { c.colors.put("costume", ColorMath.hsv(Math.min(0.999, h), s, v)); c.chroma.remove("costume"); }));
        cursor += row;
    }
    /** Horizontal gradient from vertical strips; colour(t) gets t in 0..1000. */
    static void gradient(GuiGraphics g, int x, int y, int w, int h, IntUnaryOperator color) {
        int step = 2;
        for (int i = 0; i < w; i += step) g.fill(x + i, y, x + Math.min(w, i + step), y + h, 0xFF000000 | color.applyAsInt((int) (i * 1000L / Math.max(1, w - 1))));
    }
    private void button(GuiGraphics g, int mx, int my, String title, int x, int w, Runnable action) {
        boolean hover = over(mx, my, x, cursor, w, 20), primary = title.equals("Готово");
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        if (primary) {
            UiDraw.roundH(g, x, cursor, w, 20, 6, ac, ac2);
            g.fillGradient(x + 4, cursor + 1, x + w - 4, cursor + 10, 0x33FFFFFF, 0x00FFFFFF);
        } else {
            UiDraw.roundV(g, x, cursor, w, 20, 6, hover ? 0xFF353945 : 0xFF282B33, hover ? 0xFF2B2F38 : 0xFF212329);
            if (hover) UiDraw.roundH(g, x + 7, cursor + 19, w - 14, 1, 0, UiDraw.alpha(ac, 0.9), UiDraw.alpha(ac2, 0.9));
        }
        UiFont.Face face = primary ? UiFont.Face.BOLD : UiFont.Face.REGULAR;
        int tw = Math.min(w - 8, UiFont.width(g, font, title, face));
        UiFont.text(g, font, title, x + (w - tw) / 2, cursor + 6, primary ? 0xFFFFFFFF : 0xFFE8EAF0, tw + 2, face);
        hits.add(new Hit(x, cursor, w, 20, action));
    }
    @Override public boolean mouseClicked(double lavaX, double lavaY, int lavaButton) {
        MouseButtonEvent event = new MouseButtonEvent(lavaX, lavaY, lavaButton);
        if (event.button() == 0) {
            for (Bar bar : bars) if (event.x() >= bar.x - 4 && event.x() <= bar.x + bar.w + 4 && event.y() >= bar.y && event.y() < bar.y + 16) {
                dragging = bar; bar.set(event.x()); return true;
            }
            for (Hit hit : hits) if (over((int) event.x(), (int) event.y(), hit.x, hit.y, hit.w, hit.h)) { hit.action.run(); LavaVisualClient.save(); return true; }
        }
        return super.mouseClicked(event.x(), event.y(), event.button());
    }
    @Override public boolean mouseDragged(double lavaX, double lavaY, int lavaButton, double dx, double dy) {
        MouseButtonEvent event = new MouseButtonEvent(lavaX, lavaY, lavaButton);
        if (dragging == null) return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
        dragging.set(event.x()); return true;
    }
    @Override public boolean mouseReleased(double lavaX, double lavaY, int lavaButton) {
        MouseButtonEvent event = new MouseButtonEvent(lavaX, lavaY, lavaButton);
        if (dragging != null) { dragging = null; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event.x(), event.y(), event.button());
    }
    @Override public boolean keyPressed(int lavaKey, int lavaScancode, int lavaModifiers) {
        KeyEvent event = new KeyEvent(lavaKey, lavaScancode, lavaModifiers); return super.keyPressed(event.key(), event.scancode(), event.modifiers()); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
