package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.hud.HudRenderer;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private String selected;
    private boolean dragging;
    private double offsetX, offsetY;
    public HudEditorScreen(Screen parent) { this(parent, null); }
    public HudEditorScreen(Screen parent, String selected) { super(Component.literal("Редактор HUD")); this.parent = parent; this.selected = selected; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(UiFont.component("Сохранить"), b -> onClose()).pos(width / 2 - 125, height - 25).size(95, 20).build());
        addRenderableWidget(Button.builder(UiFont.component("Вкл / выкл"), b -> {
            if (selected != null) { var w = LavaVisualClient.config().widgets.get(selected); w.visible = !w.visible; LavaVisualClient.save(); }
        }).pos(width / 2 - 25, height - 25).size(85, 20).build());

    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x50000000);
        HudRenderer.draw(g, true, selected);
        g.centeredText(font, UiFont.component("Перетащите панель мышью. Предпросмотр показывает и выключенные панели."), width / 2, height - 40, 0xFFFFFFFF);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        for (String id : HudConfig.IDS.reversed()) {
            var widget = LavaVisualClient.config().widgets.get(id);
            int x = HudRenderer.x(id, widget, width), y = HudRenderer.y(id, widget, height);
            if (event.x() >= x && event.x() < x + HudRenderer.width(id, widget) && event.y() >= y && event.y() < y + HudRenderer.height(id, widget)) {
                selected = id; dragging = true; offsetX = event.x() - x; offsetY = event.y() - y; return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || selected == null) return super.mouseDragged(event, dx, dy);
        var widget = LavaVisualClient.config().widgets.get(selected);
        widget.x = HudConfig.clamp((event.x() - offsetX) / Math.max(1, width - HudRenderer.width(selected, widget)));
        widget.y = HudConfig.clamp((event.y() - offsetY) / Math.max(1, height - HudRenderer.height(selected, widget)));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) { dragging = false; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
