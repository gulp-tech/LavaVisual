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
    public HudEditorScreen(Screen parent) { super(Component.literal("Edit HUD")); this.parent = parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back / save"), b -> onClose()).pos(width / 2 - 125, height - 25).size(95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Show/hide"), b -> {
            if (selected != null) { var w = LavaVisualClient.config().widgets.get(selected); w.visible = !w.visible; LavaVisualClient.save(); }
        }).pos(width / 2 - 25, height - 25).size(85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Color"), b -> {
            if (selected != null) { var w = LavaVisualClient.config().widgets.get(selected); w.color = (w.color + 1) % HudConfig.COLORS.length; LavaVisualClient.save(); }
        }).pos(width / 2 + 65, height - 25).size(60, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x50000000);
        HudRenderer.draw(g, true, selected);
        g.centeredText(font, "Drag panels with left mouse. Hidden panels are shown here.", width / 2, height - 40, 0xFFFFFFFF);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        for (String id : HudConfig.IDS.reversed()) {
            var widget = LavaVisualClient.config().widgets.get(id);
            int x = HudRenderer.x(widget, width), y = HudRenderer.y(widget, height);
            if (event.x() >= x && event.x() < x + HudRenderer.WIDTH && event.y() >= y && event.y() < y + HudRenderer.HEIGHT) {
                selected = id; dragging = true; offsetX = event.x() - x; offsetY = event.y() - y; return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || selected == null) return super.mouseDragged(event, dx, dy);
        var widget = LavaVisualClient.config().widgets.get(selected);
        widget.x = HudConfig.clamp((event.x() - offsetX) / Math.max(1, width - HudRenderer.WIDTH));
        widget.y = HudConfig.clamp((event.y() - offsetY) / Math.max(1, height - HudRenderer.HEIGHT));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) { dragging = false; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
