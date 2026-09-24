package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tech.gulp.lavavisual.LavaVisualClient;

/** Self-drawn rounded buttons for LavaVisual screens (instead of vanilla widgets): collected while drawing, clicked in mouseClicked. */
public final class UiButtons {
    private record Button(int x, int y, int w, int h, Runnable action) { }
    private final List<Button> list = new ArrayList<>();
    public void clear() { list.clear(); }
    /** {@code primary} = filled with the theme gradient; {@code icon} may be null. */
    public void draw(GuiGraphicsExtractor g, Font font, String icon, String label, int x, int y, int w, int h, int mx, int my, boolean primary, Runnable action) {
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        if (primary) {
            if (over) UiDraw.roundH(g, x - 2, y - 2, w + 4, h + 4, 8, UiDraw.alpha(ac, 0.28), UiDraw.alpha(ac2, 0.28));
            UiDraw.roundH(g, x, y, w, h, 6, ac, ac2);
            g.fillGradient(x + 4, y + 1, x + w - 4, y + h / 2, 0x33FFFFFF, 0x00FFFFFF);
        } else {
            UiDraw.roundV(g, x, y, w, h, 6, over ? 0xFF353945 : 0xFF282B33, over ? 0xFF2B2F38 : 0xFF212329);
            g.fill(x + 6, y, x + w - 6, y + 1, over ? 0x1CFFFFFF : 0x0DFFFFFF);
            if (over) UiDraw.roundH(g, x + 7, y + h - 1, w - 14, 1, 0, UiDraw.alpha(ac, 0.9), UiDraw.alpha(ac2, 0.9));
        }
        UiFont.Face face = primary ? UiFont.Face.BOLD : UiFont.Face.REGULAR;
        int iconW = icon == null ? 0 : 14;
        int lw = Math.max(0, Math.min(w - 10 - iconW, UiFont.width(g, font, label, face)));
        int tx = x + (w - lw - iconW) / 2;
        if (icon != null) UiFont.icon(g, font, icon, tx, y + (h - 10) / 2, primary ? 0xFFFFFFFF : over ? ac : 0xFFAEB6C4);
        UiFont.text(g, font, label, tx + iconW, y + (h - 8) / 2, primary ? 0xFFFFFFFF : 0xFFE8EAF0, lw + 2, face);
        list.add(new Button(x, y, w, h, action));
    }
    public boolean over(double mx, double my) {
        for (Button b : list) if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) return true;
        return false;
    }
    public boolean click(double mx, double my) {
        for (Button b : List.copyOf(list)) if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) { b.action.run(); return true; }
        return false;
    }
}
