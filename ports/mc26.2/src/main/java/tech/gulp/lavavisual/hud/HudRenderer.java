package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

public final class HudRenderer {
    public static final int WIDTH = 174, HEIGHT = 34;
    public static int x(HudConfig.Widget widget, int screen) { return (int) Math.round(widget.x * Math.max(0, screen - WIDTH)); }
    public static int y(HudConfig.Widget widget, int screen) { return (int) Math.round(widget.y * Math.max(0, screen - HEIGHT)); }
    public static String title(String id) {
        return switch (id) {
            case "coordinates" -> "COORDINATES";
            case "performance" -> "PERFORMANCE";
            case "session" -> "SESSION TIME";
            case "stopwatch" -> "STOPWATCH";
            case "memory" -> "HEAP MONITOR";
            default -> "LAVAVISUAL ISLAND";
        };
    }
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        HudConfig c = LavaVisualClient.config();
        if (c.shadows) g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
        g.fill(x, y, x + w, y + h, c.dark ? 0xE5121419 : 0xB8202837);
        g.fill(x, y, x + 2, y + h, accent);
    }
    public static void draw(GuiGraphicsExtractor g, boolean edit, String selected) {
        HudConfig c = LavaVisualClient.config();
        if (!c.enabled && !edit) return;
        Minecraft mc = Minecraft.getInstance();
        if (!edit && (mc.player == null || mc.gui.screen() instanceof tech.gulp.lavavisual.ui.HudEditorScreen)) return;
        SessionState state = LavaVisualClient.STATE;
        for (String id : HudConfig.IDS) {
            HudConfig.Widget widget = c.widgets.get(id);
            if (!widget.visible && !edit) continue;
            int x = x(widget, g.guiWidth()), y = y(widget, g.guiHeight());
            int accent = HudConfig.COLORS[widget.color];
            SessionState.Notice notice = id.equals("island") ? state.currentNotice() : null;
            int shift = 0;
            if (notice != null && c.animations && !edit) {
                double t = Math.min(1, (System.nanoTime() - notice.created()) / 220_000_000.0);
                shift = (int) ((1 - t) * 8);
            }
            y -= shift;
            panel(g, x, y, WIDTH, HEIGHT, accent);
            if (id.equals(selected)) {
                g.fill(x, y, x + WIDTH, y + 1, accent);
                g.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, accent);
            }
            String value = switch (id) {
                case "coordinates" -> state.coordinates;
                case "performance" -> state.performance;
                case "session" -> state.session;
                case "stopwatch" -> state.stopwatch() + (state.running() ? "  RUN" : "  PAUSE");
                case "memory" -> state.memory;
                default -> notice == null ? "Local HUD / no automation" : notice.text();
            };
            g.text(mc.font, title(id) + (!widget.visible ? " [OFF]" : ""), x + 8, y + 5, accent);
            g.text(mc.font, mc.font.plainSubstrByWidth(value, WIDTH - 16), x + 8, y + 19, 0xFFF4F4F8);
        }
    }
    public static void crosshair(GuiGraphicsExtractor g) {
        HudConfig c = LavaVisualClient.config();
        int x = g.guiWidth() / 2, y = g.guiHeight() / 2, color = HudConfig.COLORS[c.crosshairColor];
        if (c.crosshair == 1) g.fill(x - 1, y - 1, x + 1, y + 1, color);
        else if (c.crosshair == 2) {
            g.fill(x - 6, y, x - 2, y + 1, color); g.fill(x + 3, y, x + 7, y + 1, color);
            g.fill(x, y - 6, x + 1, y - 2, color); g.fill(x, y + 3, x + 1, y + 7, color);
        } else {
            g.fill(x - 4, y - 4, x + 5, y - 3, color); g.fill(x - 4, y + 4, x + 5, y + 5, color);
            g.fill(x - 4, y - 3, x - 3, y + 4, color); g.fill(x + 4, y - 3, x + 5, y + 4, color);
        }
    }
}
