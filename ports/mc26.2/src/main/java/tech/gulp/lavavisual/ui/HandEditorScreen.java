package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/** Full-screen overlay editor: the menu is closed so the real view model stays visible. */
public final class HandEditorScreen extends Screen {
    private final Screen parent;
    private boolean main = true;
    private int drag = -1;
    private final UiButtons buttons = new UiButtons();
    public HandEditorScreen(Screen parent) { super(Component.literal("Редактор рук")); this.parent = parent; }
    private HudConfig.Hand hand() { var c = LavaVisualClient.config(); return main ? c.mainHand : c.offHand; }
    private int barX() { return width / 2 - 160; }
    private double value(int index) {
        HudConfig.Hand h = hand();
        return index == 0 ? h.x : index == 1 ? h.y : index == 2 ? h.z : h.scale;
    }
    private double min(int index) { return index == 2 ? -0.5 : index == 3 ? 0.4 : -1; }
    private double max(int index) { return index == 2 ? 1.5 : index == 3 ? 1.8 : 1; }
    private void apply(int index, double mouse) {
        HudConfig.Hand h = hand();
        double t = min(index) + Math.clamp((mouse - barX()) / 320.0, 0, 1) * (max(index) - min(index));
        if (index == 0) h.x = t; else if (index == 1) h.y = t; else if (index == 2) h.z = t; else h.scale = t;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        buttons.clear();
        g.fill(0, 0, width, height, 0x30000000);
        // Rounded panel behind the sliders; the real hands stay visible around it.
        int px = barX() - 14, pw = 348, py = 4, ph = 34 + 3 * 34 + 30;
        UiDraw.shadow(g, px, py, pw, ph, 10, 4, 2, 0.3);
        UiDraw.round(g, px, py, pw, ph, 10, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.82));
        UiDraw.roundH(g, px + 10, py, pw - 20, 1, 0, UiDraw.alpha(ac, 0.8), UiDraw.alpha(ac2, 0.8));
        String title = "Редактор рук · " + (main ? "основная рука" : "вторая рука");
        UiFont.icon(g, font, Icons.HAND, px + 10, py + 8, ac);
        UiFont.text(g, font, title, px + 25, py + 9, 0xFFE8EAF0, pw - 35, UiFont.Face.BOLD);
        String[] labels = {"X · вправо / влево", "Y · выше / ниже", "Z · дальше от камеры", "Размер"};
        for (int i = 0; i < 4; i++) {
            int y = 34 + i * 34, x = barX();
            UiFont.text(g, font, labels[i], x, y - 2, 0xFFB8C0CD, 200);
            UiFont.text(g, font, String.format(java.util.Locale.ROOT, "%.2f", value(i)), x + 268, y - 2, 0xFFF2F4F8, 52);
            UiDraw.round(g, x, y + 12, 320, 4, 2, 0xFF353A43);
            double progress = Math.clamp((value(i) - min(i)) / (max(i) - min(i)), 0, 1);
            int filled = (int) Math.round(320 * progress);
            if (filled > 0) UiDraw.roundH(g, x, y + 12, filled, 4, 2, ac, 0xFF000000 | UiDraw.mix(ac, ac2, progress));
            UiDraw.circle(g, x + filled, y + 14, 6, UiDraw.alpha(ac, 0.25));
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
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        for (int i = 0; i < 4; i++) {
            int y = 34 + i * 34;
            if (event.y() >= y + 4 && event.y() < y + 26 && event.x() >= barX() - 6 && event.x() <= barX() + 326) {
                drag = i; apply(i, event.x()); return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag < 0) return super.mouseDragged(event, dx, dy);
        apply(drag, event.x()); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (drag >= 0) { drag = -1; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    /** No blur in a world: the real hands must stay sharp while editing. */
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
    }
}
