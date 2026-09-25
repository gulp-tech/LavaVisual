package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * Full-screen overlay editor: the menu is closed so the real view model stays visible. Opening it switches
 * «Положение рук» on (before, edits did nothing while that toggle was off). Two columns: position / size and
 * rotation around the hand's own resting point. Drag a slider or scroll over it for fine steps.
 */
public final class HandEditorScreen extends Screen {
    private static final String[] LABELS = {"X · вправо / влево", "Y · выше / ниже", "Z · дальше от камеры", "Размер",
            "Наклон · вверх / вниз", "Поворот · к центру / наружу", "Крен · вбок"};
    private static final double[] MIN = {-1, -1, -0.5, 0.4, -60, -60, -60}, MAX = {1, 1, 1.5, 1.8, 60, 60, 60};
    private static final int BAR = 190, GAP = 40, ROW = 34, TOP = 36;
    private final Screen parent;
    private boolean main = true;
    private int drag = -1;
    private final UiButtons buttons = new UiButtons();

    public HandEditorScreen(Screen parent) {
        super(Component.literal("Редактор рук"));
        this.parent = parent;
        LavaVisualClient.config().viewModelEnabled = true;
    }
    private HudConfig.Hand hand() { var c = LavaVisualClient.config(); return main ? c.mainHand : c.offHand; }
    private int barX(int i) { return width / 2 - BAR - GAP / 2 + (i < 4 ? 0 : BAR + GAP); }
    private int barY(int i) { return TOP + (i < 4 ? i : i - 4) * ROW; }
    private double value(int i) {
        HudConfig.Hand h = hand();
        return switch (i) { case 0 -> h.x; case 1 -> h.y; case 2 -> h.z; case 3 -> h.scale; case 4 -> h.pitch; case 5 -> h.yaw; default -> h.roll; };
    }
    private void set(int i, double v) {
        HudConfig.Hand h = hand();
        v = Math.clamp(v, MIN[i], MAX[i]);
        switch (i) { case 0 -> h.x = v; case 1 -> h.y = v; case 2 -> h.z = v; case 3 -> h.scale = v; case 4 -> h.pitch = v; case 5 -> h.yaw = v; default -> h.roll = v; }
        LavaVisualClient.config().viewModelEnabled = true;
    }
    private void apply(int i, double mouse) { set(i, MIN[i] + Math.clamp((mouse - barX(i)) / BAR, 0, 1) * (MAX[i] - MIN[i])); }
    private int slider(double mx, double my) {
        for (int i = 0; i < LABELS.length; i++)
            if (my >= barY(i) + 4 && my < barY(i) + 26 && mx >= barX(i) - 6 && mx <= barX(i) + BAR + 6) return i;
        return -1;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        buttons.clear();
        g.fill(0, 0, width, height, 0x30000000);
        // Rounded panel behind the sliders; the real hands stay visible below it.
        int px = barX(0) - 14, pw = BAR * 2 + GAP + 28, py = 4, ph = TOP + 4 * ROW - 2;
        UiDraw.shadow(g, px, py, pw, ph, 10, 4, 2, 0.3);
        UiDraw.round(g, px, py, pw, ph, 10, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.82));
        UiDraw.roundH(g, px + 10, py, pw - 20, 1, 0, UiDraw.alpha(ac, 0.8), UiDraw.alpha(ac2, 0.8));
        String title = "Редактор рук · " + (main ? "основная рука" : "вторая рука");
        UiFont.icon(g, font, Icons.HAND, px + 10, py + 8, ac);
        UiFont.text(g, font, title, px + 25, py + 9, 0xFFE8EAF0, pw - 35, UiFont.Face.BOLD);
        for (int i = 0; i < LABELS.length; i++) {
            int x = barX(i), y = barY(i);
            double v = value(i);
            UiFont.text(g, font, LABELS[i], x, y - 2, 0xFFB8C0CD, BAR - 44);
            String shown = i >= 4 ? String.format(java.util.Locale.ROOT, "%.0f°", v) : String.format(java.util.Locale.ROOT, "%.2f", v);
            UiFont.text(g, font, shown, x + BAR - 40, y - 2, 0xFFF2F4F8, 40);
            UiDraw.round(g, x, y + 12, BAR, 4, 2, 0xFF353A43);
            double progress = Math.clamp((v - MIN[i]) / (MAX[i] - MIN[i]), 0, 1);
            int filled = (int) Math.round(BAR * progress);
            if (filled > 0) UiDraw.roundH(g, x, y + 12, filled, 4, 2, ac, 0xFF000000 | UiDraw.mix(ac, ac2, progress));
            UiDraw.circle(g, x + filled, y + 14, 6, UiDraw.alpha(ac, drag == i ? 0.45 : 0.25));
            UiDraw.circle(g, x + filled, y + 14, 4, 0xFFF2F5FA);
        }
        int bw = 100, bx = width / 2 - (bw * 3 + 12) / 2, by = height - 30;
        buttons.draw(g, font, Icons.CHECK, "Готово", bx, by, bw, 20, mx, my, true, this::onClose);
        buttons.draw(g, font, Icons.HAND, main ? "Рука: основная" : "Рука: вторая", bx + bw + 6, by, bw, 20, mx, my, false, () -> main = !main);
        buttons.draw(g, font, Icons.ROTATE_CCW, "Сброс", bx + (bw + 6) * 2, by, bw, 20, mx, my, false, () -> {
            if (main) c.mainHand = new HudConfig.Hand(); else c.offHand = new HudConfig.Hand();
            LavaVisualClient.save();
        });
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && buttons.click(event.x(), event.y())) return true;
        if (event.button() == 0) {
            int i = slider(event.x(), event.y());
            if (i >= 0) { drag = i; apply(i, event.x()); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag < 0) return super.mouseDragged(event, dx, dy);
        apply(drag, event.x()); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (drag >= 0) { drag = -1; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int i = slider(x, y);
        if (i < 0 || vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        set(i, value(i) + Math.signum(vertical) * (i >= 4 ? 1 : (MAX[i] - MIN[i]) / 200));
        LavaVisualClient.save();
        return true;
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    /** No blur in a world: the real hands must stay sharp while editing. */
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
    }
}
