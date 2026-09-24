package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
    public HandEditorScreen(Screen parent) { super(Component.literal("Редактор рук")); this.parent = parent; }
    private HudConfig.Hand hand() { var c = LavaVisualClient.config(); return main ? c.mainHand : c.offHand; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(UiFont.component("Готово"), b -> onClose()).pos(width / 2 - 135, height - 26).size(80, 20).build());
        addRenderableWidget(Button.builder(UiFont.component("Рука: " + (main ? "основная" : "вторая")), b -> main = !main).pos(width / 2 - 50, height - 26).size(100, 20).build());
        addRenderableWidget(Button.builder(UiFont.component("Сброс"), b -> {
            var c = LavaVisualClient.config();
            if (main) c.mainHand = new HudConfig.Hand(); else c.offHand = new HudConfig.Hand();
            LavaVisualClient.save();
        }).pos(width / 2 + 55, height - 26).size(80, 20).build());
    }
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
        g.fill(0, 0, width, height, 0x38000000);
        String title = "Редактор рук · руки и предмет видны в игре";
        int titleW = Math.min(width - 16, font.width(UiFont.component(title)));
        UiFont.text(g, font, title, (width - titleW) / 2, 10, 0xFFE8EAF0, titleW + 2);
        String[] labels = {"X · вправо / влево", "Y · выше / ниже", "Z · дальше от камеры", "Размер"};
        for (int i = 0; i < 4; i++) {
            int y = 34 + i * 34, x = barX();
            UiFont.text(g, font, labels[i], x, y - 2, 0xFFB8C0CD, 200);
            UiFont.text(g, font, String.format(java.util.Locale.ROOT, "%.2f", value(i)), x + 268, y - 2, 0xFFF2F4F8, 52);
            UiDraw.round(g, x, y + 12, 320, 4, 2, 0xFF353A43);
            double progress = Math.clamp((value(i) - min(i)) / (max(i) - min(i)), 0, 1);
            int filled = (int) Math.round(320 * progress);
            if (filled > 0) UiDraw.round(g, x, y + 12, filled, 4, 2, c.accent());
            UiDraw.round(g, x + filled - 4, y + 9, 8, 10, 4, 0xFFF2F5FA);
        }
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
}
