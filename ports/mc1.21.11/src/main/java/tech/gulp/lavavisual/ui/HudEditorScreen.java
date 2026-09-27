package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.hud.HudRenderer;

/**
 * HUD layout editor: drag a panel to move it, drag its corner handle or scroll over it to resize. Sizes snap to
 * whole or half screen pixels per unit (text stays crisp); panels snap to the screen centre and edges.
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 4;
    private final Screen parent;
    private final UiButtons buttons = new UiButtons();
    private String selected;
    private boolean moving, resizing, guideX, guideY;
    private double offsetX, offsetY, startScale, anchorX, anchorY, startSpan;
    private long scaleShownAt;
    public HudEditorScreen(Screen parent) { this(parent, null); }
    public HudEditorScreen(Screen parent, String selected) { super(Component.literal("Редактор HUD")); this.parent = parent; this.selected = selected; }

    private static HudConfig.Widget widget(String id) { return LavaVisualClient.config().widgets.get(id); }
    private int wx(String id) { return HudRenderer.x(id, widget(id), width); }
    private int wy(String id) { return HudRenderer.y(id, widget(id), height); }
    private static int ww(String id) { return HudRenderer.width(id, widget(id)); }
    private static int wh(String id) { return HudRenderer.height(id, widget(id)); }
    private boolean onHandle(String id, double mx, double my) {
        if (id == null) return false;
        int hx = wx(id) + ww(id) + 3, hy = wy(id) + wh(id) + 3;
        return Math.abs(mx - hx) <= 6 && Math.abs(my - hy) <= 6;
    }
    private String widgetAt(double mx, double my) {
        for (String id : HudConfig.IDS.reversed()) {
            int x = wx(id), y = wy(id);
            if (mx >= x && mx < x + ww(id) && my >= y && my < y + wh(id)) return id;
        }
        return null;
    }
    private int percent(String id) { return (int) Math.round(HudRenderer.scale(widget(id)) * 100); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        buttons.clear();
        g.fill(0, 0, width, height, 0x40000000);
        if (moving && guideX) g.fill(width / 2, 0, width / 2 + 1, height, UiDraw.alpha(ac, 0.7));
        if (moving && guideY) g.fill(0, height / 2, width, height / 2 + 1, UiDraw.alpha(ac2, 0.7));
        HudRenderer.draw(g, true, null);
        String hovered = moving || resizing ? null : widgetAt(mx, my);
        for (String id : HudConfig.IDS) {
            boolean sel = id.equals(selected);
            if (!sel && !id.equals(hovered)) continue;
            int x = wx(id) - 3, y = wy(id) - 3, w = ww(id) + 6, h = wh(id) + 6;
            frame(g, x, y, w, h, sel ? UiDraw.alpha(ac, 0.95) : 0x66FFFFFF, sel ? UiDraw.alpha(ac2, 0.95) : 0x66FFFFFF);
            if (sel) {
                int hx = x + w, hy = y + h;
                boolean hot = resizing || onHandle(id, mx, my);
                UiDraw.circle(g, hx, hy, hot ? 6 : 5, 0xFFFFFFFF);
                UiDraw.circle(g, hx, hy, hot ? 4.5 : 3.5, 0xFF000000 | UiDraw.mix(ac, ac2, 0.5));
                if (resizing || hot || System.currentTimeMillis() - scaleShownAt < 1200) {
                    String label = percent(id) + "%";
                    int lw = UiFont.width(g, font, label, UiFont.Face.BOLD) + 10, lx = Math.min(width - lw - 2, hx + 8), ly = Math.min(height - 16, hy + 4);
                    UiDraw.round(g, lx, ly, lw, 14, 7, 0xE6121419);
                    UiFont.text(g, font, label, lx + 5, ly + 3, 0xFFFFFFFF, lw, UiFont.Face.BOLD);
                }
            }
        }
        // Hint pill at the top.
        String hint = "Тяните панель — переместить · уголок или колёсико — размер · двойной клик — 100%";
        int hw = Math.min(width - 16, UiFont.width(g, font, hint, UiFont.Face.SMALL) + 20), hx0 = (width - hw) / 2;
        UiDraw.round(g, hx0, 8, hw, 16, 8, 0xD9121419);
        UiFont.text(g, font, hint, hx0 + 10, 12, 0xFFC9D0DA, hw - 20, UiFont.Face.SMALL);
        // Toolbar.
        int bw = 96, gap = 6, count = 4, total = bw * count + gap * (count - 1) + 16;
        if (total > width - 8) { bw = Math.max(56, (width - 8 - 16 - gap * (count - 1)) / count); total = bw * count + gap * (count - 1) + 16; }
        int tx = (width - total) / 2, ty = height - 42;
        UiDraw.shadow(g, tx, ty, total, 34, 10, 4, 2, 0.35);
        UiDraw.round(g, tx, ty, total, 34, 10, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.94));
        UiDraw.roundH(g, tx + 10, ty, total - 20, 1, 0, UiDraw.alpha(ac, 0.8), UiDraw.alpha(ac2, 0.8));
        String name = selected == null ? "Панель не выбрана" : HudRenderer.title(selected) + " · " + percent(selected) + "%";
        int nw = UiFont.width(g, font, name, UiFont.Face.SMALL) + 14;
        UiDraw.round(g, (width - nw) / 2, ty - 18, nw, 14, 7, 0xD9121419);
        UiFont.text(g, font, name, (width - nw) / 2 + 7, ty - 15, selected == null ? 0xFF8C93A1 : 0xFFF2F4F8, nw, UiFont.Face.SMALL);
        int bx = tx + 8, by = ty + 7;
        buttons.draw(g, font, Icons.CHECK, "Готово", bx, by, bw, 20, mx, my, true, this::onClose);
        bx += bw + gap;
        boolean visible = selected != null && widget(selected).visible;
        buttons.draw(g, font, visible ? Icons.EYE_OFF : Icons.EYE, selected == null ? "Вкл / выкл" : visible ? "Скрыть" : "Показать", bx, by, bw, 20, mx, my, false, () -> {
            if (selected != null) { var w = widget(selected); w.visible = !w.visible; LavaVisualClient.save(); }
        });
        bx += bw + gap;
        buttons.draw(g, font, Icons.SCALING, "Размер 100%", bx, by, bw, 20, mx, my, false, () -> { if (selected != null) setScale(selected, 1); });
        bx += bw + gap;
        buttons.draw(g, font, Icons.ROTATE_CCW, "Сбросить всё", bx, by, bw, 20, mx, my, false, LavaVisualClient::resetLayout);
        super.extractRenderState(g, mx, my, delta);
    }

    /** Thin rounded frame in a horizontal gradient. */
    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
        UiDraw.roundH(g, x + 4, y, w - 8, 1, 0, left, right);
        UiDraw.roundH(g, x + 4, y + h - 1, w - 8, 1, 0, left, right);
        g.fill(x, y + 4, x + 1, y + h - 4, left);
        g.fill(x + w - 1, y + 4, x + w, y + h - 4, right);
        UiDraw.round(g, x, y, 5, 5, 2, UiDraw.fade(left, 0.8));
        UiDraw.round(g, x + w - 5, y, 5, 5, 2, UiDraw.fade(right, 0.8));
        UiDraw.round(g, x, y + h - 5, 5, 5, 2, UiDraw.fade(left, 0.8));
        UiDraw.round(g, x + w - 5, y + h - 5, 5, 5, 2, UiDraw.fade(right, 0.8));
    }

    /** New scale snapped to crisp steps, keeping the panel's top-left corner where it is. */
    private void setScale(String id, double scale) {
        var w = widget(id);
        int x = wx(id), y = wy(id);
        w.scale = HudRenderer.snapScale(Math.clamp(scale, HudConfig.SCALE_MIN, HudConfig.SCALE_MAX));
        w.x = HudConfig.clamp(x / (double) Math.max(1, width - ww(id)));
        w.y = HudConfig.clamp(y / (double) Math.max(1, height - wh(id)));
        scaleShownAt = System.currentTimeMillis();
        LavaVisualClient.save();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (buttons.click(event.x(), event.y())) return true;
        if (onHandle(selected, event.x(), event.y())) {
            resizing = true; startScale = HudRenderer.scale(widget(selected));
            anchorX = wx(selected); anchorY = wy(selected);
            startSpan = Math.max(8, (event.x() - anchorX) + (event.y() - anchorY));
            return true;
        }
        String id = widgetAt(event.x(), event.y());
        if (id != null) {
            selected = id;
            if (doubleClick) { setScale(id, 1); return true; }
            moving = true; offsetX = event.x() - wx(id); offsetY = event.y() - wy(id);
            return true;
        }
        selected = null;
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (selected == null) return super.mouseDragged(event, dx, dy);
        var widget = widget(selected);
        if (resizing) {
            double span = (event.x() - anchorX) + (event.y() - anchorY);
            double scale = HudRenderer.snapScale(Math.clamp(startScale * span / startSpan, HudConfig.SCALE_MIN, HudConfig.SCALE_MAX));
            if (scale != widget.scale) {
                widget.scale = scale;
                widget.x = HudConfig.clamp(anchorX / Math.max(1, width - ww(selected)));
                widget.y = HudConfig.clamp(anchorY / Math.max(1, height - wh(selected)));
            }
            return true;
        }
        if (!moving) return super.mouseDragged(event, dx, dy);
        int w = ww(selected), h = wh(selected);
        double x = event.x() - offsetX, y = event.y() - offsetY;
        // Snap to the screen centre and edges.
        guideX = Math.abs(x + w / 2.0 - width / 2.0) <= SNAP;
        guideY = Math.abs(y + h / 2.0 - height / 2.0) <= SNAP;
        if (guideX) x = (width - w) / 2.0; else if (Math.abs(x - 2) <= SNAP) x = 2; else if (Math.abs(x + w - (width - 2)) <= SNAP) x = width - 2 - w;
        if (guideY) y = (height - h) / 2.0; else if (Math.abs(y - 2) <= SNAP) y = 2; else if (Math.abs(y + h - (height - 2)) <= SNAP) y = height - 2 - h;
        widget.x = HudConfig.clamp(x / Math.max(1, width - w));
        widget.y = HudConfig.clamp(y / Math.max(1, height - h));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (moving || resizing) { moving = resizing = false; guideX = guideY = false; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        String id = widgetAt(x, y);
        if (id == null) id = selected;
        if (id == null || vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        selected = id;
        double step = 1.0 / (2 * UiFont.guiScale());
        setScale(id, HudRenderer.scale(widget(id)) + Math.signum(vertical) * step);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int dx = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = event.key() == GLFW.GLFW_KEY_UP ? -1 : event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                var w = widget(selected);
                w.x = HudConfig.clamp((wx(selected) + dx) / (double) Math.max(1, width - ww(selected)));
                w.y = HudConfig.clamp((wy(selected) + dy) / (double) Math.max(1, height - wh(selected)));
                LavaVisualClient.save();
                return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
