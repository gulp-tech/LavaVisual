package tech.gulp.lavavisual.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Bundled OFL/ISC fonts for LavaVisual only; the global Minecraft font is never replaced.
 * Minecraft samples TTF glyph textures without filtering, so text is only sharp when one glyph texel covers
 * exactly one screen pixel. Every face therefore exists once per integer pixel scale (1..8) and the face whose
 * oversample equals guiScale x pose scale is chosen at draw time.
 */
public final class UiFont {
    public enum Face {
        REGULAR("r"), BOLD("b"), SMALL("s"), HEADING("h"), ICON("i"), ICON_SMALL("j"), ICON_LARGE("k");
        private final String key;
        Face(String key) { this.key = key; }
    }
    private static final FontDescription[][] FACES = new FontDescription[Face.values().length][9];
    private UiFont() { }
    public static FontDescription face(Face face, int scale) {
        int s = Math.clamp(scale, 1, 8);
        FontDescription description = FACES[face.ordinal()][s];
        if (description == null) FACES[face.ordinal()][s] = description = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lavavisual", face.key + s));
        return description;
    }
    public static int guiScale() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }
    /** Physical pixels per GUI unit at the current pose. */
    public static int pixelScale(GuiGraphicsExtractor g) {
        var m = g.pose();
        double pose = Math.sqrt(Math.abs(m.m00() * m.m11() - m.m01() * m.m10()));
        return (int) Math.clamp(Math.round(guiScale() * pose), 1, 8);
    }
    /** A scale that keeps text pixel-exact: a multiple of 1 / guiScale. */
    public static double crisp(double scale) {
        int gs = guiScale();
        return Math.max(1, Math.round(scale * gs)) / (double) gs;
    }
    public static Component component(String value) { return component(value, Face.REGULAR, guiScale()); }
    public static Component component(String value, Face face, int scale) {
        FontDescription description = face(face, scale);
        return Component.literal(value).withStyle(style -> style.withFont(description));
    }
    public static int width(Font font, String value, Face face, int scale) { return font.width(component(value, face, scale)); }
    public static int width(GuiGraphicsExtractor g, Font font, String value, Face face) { return width(font, value, face, pixelScale(g)); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width) { text(g, font, value, x, y, color, width, Face.REGULAR); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width, Face face) {
        int scale = pixelScale(g);
        String shown = value == null ? "" : value;
        if (font.width(component(shown, face, scale)) > width) {
            while (!shown.isEmpty() && font.width(component(shown + "…", face, scale)) > width) shown = shown.substring(0, shown.length() - 1);
            shown = shown.isEmpty() ? "" : shown.stripTrailing() + "…";
        }
        g.text(font, component(shown, face, scale), x, y, color, false);
    }
    public static void centered(GuiGraphicsExtractor g, Font font, String value, int centerX, int y, int color, Face face) {
        Component text = component(value, face, pixelScale(g));
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }
    /** 10 x 10 icon whose box starts at (x, y). */
    public static void icon(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { g.text(font, component(icon, Face.ICON, pixelScale(g)), x, y, color, false); }
    /** 8 x 8 icon whose box starts at (x, y + 1). */
    public static void iconSmall(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { g.text(font, component(icon, Face.ICON_SMALL, pixelScale(g)), x, y, color, false); }
    /** 16 x 16 icon whose box starts at (x, y). */
    public static void iconLarge(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { g.text(font, component(icon, Face.ICON_LARGE, pixelScale(g)), x, y, color, false); }
}
