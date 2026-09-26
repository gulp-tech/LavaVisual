package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.ColorMath;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.effects.Hats;

/**
 * Live wings editor: the menu closes and the camera looks at the player from behind (front view optional), so every
 * change is seen on the real wings. Left panel: model and placement; right panel: motion and colour. The previous
 * camera comes back when the editor closes.
 */
public final class WingsEditorScreen extends Screen {
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
    private boolean back = true;
    private int px, pw, cursor, row = 24, buttonRow = 24;

    public WingsEditorScreen(Screen parent) { super(UiFont.component("Крылья")); this.parent = parent; }

    @Override protected void init() {
        if (previous == null && minecraft.options != null) {
            previous = minecraft.options.getCameraType();
            minecraft.options.setCameraType(back ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        }
        var c = LavaVisualClient.config();
        if (!c.wingsEnabled) { c.wingsEnabled = true; LavaVisualClient.save(); }
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
    private static boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear(); bars.clear();
        var c = LavaVisualClient.config();
        boolean compact = height < 290;
        row = compact ? 20 : 24; buttonRow = compact ? 22 : 24;
        int panelW = Math.max(120, Math.min(180, (width - 40) / 2 - 30));
        int top = 8, panelH = Math.min(height - 16, 34 + row * 6 + 6);

        // Left: model and placement.
        px = 8; pw = panelW;
        panel(g, top, panelH, "Крылья · вид", minecraft.level == null ? "зайдите в мир, чтобы видеть крылья" : "изменения видны сразу");
        cursor = top + 34;
        int ty = cursor;
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? px + 8 : px + pw - 28, step = side == 0 ? -1 : 1;
            boolean hover = over(mx, my, x, ty, 20, 18);
            UiDraw.round(g, x, ty, 20, 18, 5, hover ? 0xFF3A3F4B : 0xFF2A2E37);
            UiFont.icon(g, font, side == 0 ? Icons.CHEVRON_LEFT : Icons.CHEVRON_RIGHT, x + 4, ty + 3, hover ? accent() : 0xFFD5DAE3);
            hits.add(new Hit(x, ty, 20, 18, () -> c.wingsType = Math.floorMod(c.wingsType - 1 + step, Hats.WING_COUNT) + 1));
        }
        String name = Hats.wingName(c.wingsType), position = c.wingsType + "/" + Hats.WING_COUNT;
        int nw = UiFont.width(g, font, name, UiFont.Face.BOLD), cw = UiFont.width(g, font, position, UiFont.Face.SMALL), nx = px + (pw - nw - cw - 5) / 2;
        UiFont.text(g, font, name, nx, ty + 5, 0xFFF1F4F8, nw + 2, UiFont.Face.BOLD);
        UiFont.text(g, font, position, nx + nw + 5, ty + 6, 0xFF8C93A1, cw + 2, UiFont.Face.SMALL);
        cursor += row;
        bar(g, "Размер", c.wingsSize, 0.5, 1.6, v -> c.wingsSize = v, "%.2f");
        bar(g, "Высота на спине", c.wingsLift, -0.25, 0.35, v -> c.wingsLift = snap(v), "%+.2f");
        bar(g, "Отступ от спины", c.wingsBack, -0.1, 0.25, v -> c.wingsBack = snap(v), "%+.2f");
        bar(g, "Наклон назад", c.wingsTilt, -30, 30, v -> c.wingsTilt = Math.abs(v) < 1.5 ? 0 : v, "%+.0f°");
        bar(g, "Раскрытие", c.wingsSpread, -35, 35, v -> c.wingsSpread = Math.abs(v) < 1.5 ? 0 : v, "%+.0f°");

        // Right: motion and colour.
        px = width - 8 - panelW; pw = panelW;
        int rightH = Math.min(height - 16, 34 + row * 4 + 18 + buttonRow * 3 + 6);
        panel(g, top, rightH, "Движение и цвет", "взмахи, прозрачность, цвет");
        cursor = top + 34;
        bar(g, "Сила взмахов · 0 = неподвижно", c.wingsFlap, 0, 2, v -> c.wingsFlap = v < 0.06 ? 0 : v, "%.2f");
        bar(g, "Скорость взмахов", c.wingsSpeed, 0.3, 2.5, v -> c.wingsSpeed = v, "%.2f");
        bar(g, "Прозрачность", c.wingsOpacity, 0.15, 1, v -> c.wingsOpacity = v, "%.2f");
        hueBar(g, c);
        int sw = (pw - 16 - (SWATCHES.length - 1) * 3) / SWATCHES.length;
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = px + 8 + i * (sw + 3), color = SWATCHES[i];
            boolean selected = c.customColor("wings") && (c.colors.get("wings") & 0xFFFFFF) == color;
            if (selected) UiDraw.round(g, x - 1, cursor - 1, sw + 2, 14, 4, 0xFFFFFFFF);
            UiDraw.round(g, x, cursor, sw, 12, 3, 0xFF000000 | color);
            hits.add(new Hit(x, cursor, sw, 12, () -> { c.colors.put("wings", color); c.chroma.remove("wings"); }));
        }
        cursor += 18;
        int half = (pw - 20) / 2;
        String[] styles = {"узор", "сплошной", "градиент"};
        button(g, mx, my, "Стиль: " + styles[Math.floorMod(c.wingsStyle, 3)], px + 8, half, () -> c.wingsStyle = (Math.floorMod(c.wingsStyle, 3) + 1) % 3);
        button(g, mx, my, "Радуга: " + (c.chroma.contains("wings") ? "вкл" : "выкл"), px + 12 + half, half, () -> { if (!c.chroma.remove("wings")) c.chroma.add("wings"); });
        cursor += buttonRow;
        button(g, mx, my, c.customColor("wings") || c.chroma.contains("wings") ? "Цвет темы" : "Цвет: тема", px + 8, half, () -> { c.colors.remove("wings"); c.chroma.remove("wings"); });
        button(g, mx, my, "Вид: " + (back ? "сзади" : "спереди"), px + 12 + half, half, () -> {
            back = !back;
            if (previous != null) minecraft.options.setCameraType(back ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        });
        cursor += buttonRow;
        button(g, mx, my, "Сброс", px + 8, half, () -> {
            HudConfig d = new HudConfig();
            c.wingsSize = d.wingsSize; c.wingsOpacity = d.wingsOpacity; c.wingsFlap = d.wingsFlap; c.wingsStyle = d.wingsStyle;
            c.wingsLift = d.wingsLift; c.wingsBack = d.wingsBack; c.wingsTilt = d.wingsTilt; c.wingsSpread = d.wingsSpread; c.wingsSpeed = d.wingsSpeed;
            c.colors.remove("wings"); c.chroma.remove("wings");
        });
        button(g, mx, my, "Готово", px + 12 + half, half, this::onClose);
        super.extractRenderState(g, mx, my, delta);
    }
    private static double snap(double v) { return Math.abs(v) < 0.012 ? 0 : v; }
    private void panel(GuiGraphicsExtractor g, int top, int h, String title, String sub) {
        var c = LavaVisualClient.config();
        UiDraw.round(g, px, top, pw, h, 9, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.9));
        UiDraw.round(g, px + 8, top + 8, 18, 18, 6, accent());
        UiFont.icon(g, font, Icons.WIND, px + 12, top + 12, 0xFF11181A);
        UiFont.text(g, font, title, px + 32, top + 9, 0xFFF1F4F8, pw - 40, UiFont.Face.BOLD);
        UiFont.text(g, font, sub, px + 32, top + 20, 0xFF8C93A1, pw - 40, UiFont.Face.SMALL);
    }
    private void bar(GuiGraphicsExtractor g, String label, double value, double min, double max, DoubleConsumer setter, String format) {
        int x = px + 10, w = pw - 20;
        String shown = String.format(Locale.ROOT, format, value);
        int vw = UiFont.width(g, font, shown, UiFont.Face.SMALL);
        UiFont.text(g, font, label, x, cursor, 0xFFB8C0CD, w - vw - 6, UiFont.Face.SMALL);
        UiFont.text(g, font, shown, x + w - vw, cursor, 0xFFF2F4F8, vw + 2, UiFont.Face.SMALL);
        double progress = Math.clamp((value - min) / (max - min), 0, 1);
        int filled = (int) Math.round(w * progress);
        UiDraw.round(g, x, cursor + 12, w, 4, 2, 0xFF353A43);
        if (min < 0 && max > 0) { int zero = x + (int) Math.round(w * (-min / (max - min))); g.fill(zero, cursor + 10, zero + 1, cursor + 18, 0x668C93A1); }
        if (filled > 0) UiDraw.roundH(g, x, cursor + 12, filled, 4, 2, accent(), 0xFF000000 | UiDraw.mix(accent(), LavaVisualClient.config().color2("menu"), progress));
        UiDraw.circle(g, x + filled, cursor + 14, 6, UiDraw.alpha(accent(), 0.25));
        UiDraw.circle(g, x + filled, cursor + 14, 4, 0xFFF2F5FA);
        bars.add(new Bar(x, cursor + 6, w, min, max, setter));
        cursor += row;
    }
    private void hueBar(GuiGraphicsExtractor g, HudConfig c) {
        int x = px + 10, w = pw - 20;
        double[] hsv = ColorMath.toHsv(c.color("wings") & 0xFFFFFF);
        UiFont.text(g, font, "Цвет крыльев · " + ColorMath.hex(c.color("wings")), x, cursor, 0xFFB8C0CD, w, UiFont.Face.SMALL);
        HatEditorScreen.gradient(g, x, cursor + 11, w, 6, t -> ColorMath.hsv(t / 1000.0, 1, 1));
        int knob = x + (int) Math.round(w * hsv[0]);
        UiDraw.round(g, knob - 3, cursor + 9, 6, 10, 3, 0xFFFFFFFF);
        double s = Math.max(0.55, hsv[1]), v = Math.max(0.6, hsv[2]);
        bars.add(new Bar(x, cursor + 6, w, 0, 1, h -> { c.colors.put("wings", ColorMath.hsv(Math.min(0.999, h), s, v)); c.chroma.remove("wings"); }));
        cursor += row;
    }
    private void button(GuiGraphicsExtractor g, int mx, int my, String title, int x, int w, Runnable action) {
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
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
