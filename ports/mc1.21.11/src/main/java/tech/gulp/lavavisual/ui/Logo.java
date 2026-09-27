package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The LV logo. Like the fonts, every size exists once per half pixel scale (1, 1.5 ... 8 pixels per GUI unit,
 * tools/make_logo.py), so one texel covers exactly one screen pixel and the logo stays sharp without filtering,
 * also on resized HUD elements.
 */
public final class Logo {
    private Logo() { }
    public static final int MENU_H = LogoSizes.MENU_H, HUD_H = LogoSizes.HUD_H;
    private static final Identifier[] MENU = new Identifier[15], HUD = new Identifier[15];
    /** Width in GUI units (rounded up) of the menu or HUD logo. */
    public static int width(boolean hud) { return (int) Math.ceil((hud ? LogoSizes.HUD_W[14] : LogoSizes.MENU_W[14]) / 8.0); }
    /** Draws the logo with its top-left corner at (x, y); {@code color} tints and fades it (0xFFFFFFFF = as is). */
    public static void draw(GuiGraphicsExtractor g, boolean hud, int x, int y, int color) {
        int h = UiFont.halfScale(g), i = h - 2;
        Identifier[] ids = hud ? HUD : MENU;
        if (ids[i] == null) ids[i] = Identifier.fromNamespaceAndPath("lavavisual", "textures/gui/logo/" + (hud ? "hud_" : "menu_") + (h / 2) + (h % 2 == 0 ? "" : "_5") + ".png");
        int tw = (hud ? LogoSizes.HUD_W : LogoSizes.MENU_W)[i], th = (hud ? HUD_H : MENU_H) * h / 2;
        g.pose().pushMatrix();
        try {
            UiFont.snap(g, x, y);
            g.pose().translate(x, y);
            g.pose().scale(2f / h);
            g.blit(RenderPipelines.GUI_TEXTURED, ids[i], 0, 0, 0, 0, tw, th, tw, th, tw, th, color);
        } finally { g.pose().popMatrix(); }
    }
}
