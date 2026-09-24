package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.ui.UiDraw;
import tech.gulp.lavavisual.ui.UiFont;

public final class HudRenderer {
    public static int baseWidth(String id) { return id.equals("target") ? 180 : 156; }
    public static int baseHeight(String id) { return id.equals("target") ? 60 : 30; }
    public static int width(String id, HudConfig.Widget w) { return (int) Math.ceil(baseWidth(id) * w.scale); }
    public static int height(String id, HudConfig.Widget w) { return (int) Math.ceil(baseHeight(id) * w.scale); }
    public static int x(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.x * Math.max(0, screen - width(id, w))); }
    public static int y(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.y * Math.max(0, screen - height(id, w))); }
    public static String title(String id) {
        return switch (id) {
            case "coordinates" -> "Координаты";
            case "performance" -> "FPS";
            case "target" -> "Target HUD";
            default -> "HUD";
        };
    }
    public static void draw(GuiGraphicsExtractor g, boolean edit, String selected) {
        HudConfig c = LavaVisualClient.config();
        Minecraft mc = Minecraft.getInstance();
        if (!edit && (mc.player == null || mc.gui.screen() instanceof tech.gulp.lavavisual.ui.HudEditorScreen)) return;
        SessionState state = LavaVisualClient.STATE;
        for (String id : HudConfig.IDS) {
            HudConfig.Widget w = c.widgets.get(id);
            if (!w.visible && !edit) continue;
            TargetSnapshot target = TargetSnapshot.current;
            if (!edit && id.equals("target") && target == null) continue;
            int x = x(id, w, g.guiWidth()), y = y(id, w, g.guiHeight());
            int bw = baseWidth(id), bh = baseHeight(id), accent = c.accent();
            g.pose().pushMatrix();
            try {
                g.pose().translate(x, y);
                g.pose().scale((float) w.scale);
                if (c.shadows) UiDraw.round(g, 1, 2, bw, bh, 5, UiDraw.alpha(0x000000, w.opacity * 0.25));
                UiDraw.round(g, 0, 0, bw, bh, 5, UiDraw.alpha(0x111216, w.opacity));
                if (id.equals(selected)) g.fill(5, bh - 1, bw - 5, bh, accent);
                if (id.equals("target")) {
                    if (target == null) target = new TargetSnapshot("Предпросмотр", 16, 20, 10, 3.2,
                            mc.player == null ? null : mc.player.getSkin().body().texturePath());
                    UiDraw.round(g, 8, 8, 34, 34, 5, UiDraw.alpha(accent, 0.18));
                    if (target.skin() != null) {
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, target.skin(), 9, 9, 8, 8, 32, 32, 8, 8, 64, 64);
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, target.skin(), 9, 9, 40, 8, 32, 32, 8, 8, 64, 64);
                    } else if (target.entity() != null && !target.entity().isRemoved()) {
                        try {
                            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, 9, 9, 32, 32, 0, 25f, 25f, 14f, target.entity());
                        } catch (RuntimeException ignored) {
                            UiFont.text(g, mc.font, target.name().isEmpty() ? "?" : target.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT), 21, 21, accent, 26);
                        }
                    } else UiFont.text(g, mc.font, target.name().isEmpty() ? "?" : target.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT), 21, 21, accent, 26);
                    UiFont.text(g, mc.font, target.name(), 49, 8, accent, bw - 57);
                    UiFont.text(g, mc.font, String.format(java.util.Locale.ROOT, "HP %.1f / %.1f", target.health(), target.maximum()), 49, 22, 0xFFE8E8EB, bw - 57);
                    UiFont.text(g, mc.font, String.format(java.util.Locale.ROOT, "Броня %d · %.1f м", target.armor(), target.distance()), 49, 36, 0xFF9698A3, bw - 57);
                    double ratio = target.maximum() > 0 ? Math.max(0, Math.min(1, target.health() / target.maximum())) : 0;
                    UiDraw.round(g, 9, 51, bw - 18, 3, 1, 0xFF303137);
                    if (ratio > 0) UiDraw.round(g, 9, 51, Math.max(1, (int) ((bw - 18) * ratio)), 3, 1, accent);
                } else {
                    String value = switch (id) {
                        case "coordinates" -> state.coordinates;
                        case "performance" -> state.performance;
                        default -> "";
                    };
                    UiFont.text(g, mc.font, title(id) + (edit && !w.visible ? " · выкл" : ""), 8, 4, accent, bw - 16);
                    UiFont.text(g, mc.font, value, 8, 17, 0xFFEAEAF0, bw - 16);
                }
            } finally { g.pose().popMatrix(); }
        }
    }
    public static void crosshair(GuiGraphicsExtractor g) {
        HudConfig c = LavaVisualClient.config();
        int color = UiDraw.alpha(c.accent(), c.crosshairOpacity);
        g.pose().pushMatrix();
        try {
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f);
            g.pose().scale((float) c.crosshairScale);
            if (c.crosshairShape == 1) g.fill(-1, -1, 1, 1, color);
            else if (c.crosshairShape == 2) {
                g.fill(-6, 0, -2, 1, color); g.fill(3, 0, 7, 1, color);
                g.fill(0, -6, 1, -2, color); g.fill(0, 3, 1, 7, color);
            } else {
                g.fill(-4, -4, 5, -3, color); g.fill(-4, 4, 5, 5, color);
                g.fill(-4, -3, -3, 4, color); g.fill(4, -3, 5, 4, color);
            }
        } finally { g.pose().popMatrix(); }
    }
}
