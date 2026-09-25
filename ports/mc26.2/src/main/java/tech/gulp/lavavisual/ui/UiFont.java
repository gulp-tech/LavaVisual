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
 * exactly one screen pixel. Every face therefore exists once per half pixel scale (1, 1.5, 2 ... 8 pixels per
 * GUI unit), the face whose oversample equals guiScale x pose scale is chosen at draw time, and the text origin is
 * moved to the nearest whole screen pixel so half-step scales (resized HUD elements) stay just as crisp.
 */
public final class UiFont {
    public enum Face {
        REGULAR("r"), BOLD("b"), SMALL("s"), HEADING("h"), ICON("i"), ICON_SMALL("j"), ICON_LARGE("k");
        private final String key;
        Face(String key) { this.key = key; }
    }
    /** Text families (HudConfig.hudFont): Montserrat and Rubik for the HUD, Inter for menus. Icons never change. */
    public static final String[] FAMILIES = {"Montserrat", "Rubik", "Inter"};
    public static final int MENU = 2;
    private static final String[] PREFIX = {"m", "u", ""};
    private static final FontDescription[][][] FACES = new FontDescription[FAMILIES.length][Face.values().length][17];
    private static int family = MENU;
    private UiFont() { }
    /** Switches the text family for the following draw and width calls; returns the previous one for restoring. */
    public static int use(int next) { int previous = family; family = Math.clamp(next, 0, FAMILIES.length - 1); return previous; }
    /** Face for {@code half} half-pixels per GUI unit (2 = one pixel per unit ... 16 = eight). */
    public static FontDescription face(Face face, int half) {
        int h = Math.clamp(half, 2, 16), f = face.ordinal() <= Face.HEADING.ordinal() ? family : MENU;
        FontDescription description = FACES[f][face.ordinal()][h];
        if (description == null) FACES[f][face.ordinal()][h] = description = new FontDescription.Resource(
                Identifier.fromNamespaceAndPath("lavavisual", PREFIX[f] + face.key + (h / 2) + (h % 2 == 0 ? "" : "_5")));
        return description;
    }
    public static int guiScale() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }
    private static double pose(GuiGraphicsExtractor g) {
        var m = g.pose();
        return Math.sqrt(Math.abs(m.m00() * m.m11() - m.m01() * m.m10()));
    }
    /** Physical pixels per GUI unit at the current pose, rounded to a whole number. */
    public static int pixelScale(GuiGraphicsExtractor g) { return (int) Math.clamp(Math.round(guiScale() * pose(g)), 1, 8); }
    /** Twice the physical pixels per GUI unit at the current pose (2..16): selects whole and half-step faces. */
    public static int halfScale(GuiGraphicsExtractor g) { return (int) Math.clamp(Math.round(2 * guiScale() * pose(g)), 2, 16); }
    /** A scale that keeps text pixel-exact: a multiple of 1 / (2 x guiScale), i.e. whole or half pixels per unit. */
    public static double crisp(double scale) {
        int gs = guiScale();
        return Math.clamp(Math.round(scale * gs * 2), 2, 16) / (2.0 * gs);
    }
    /** Moves the pose so local (x, y) lands on a whole screen pixel; call between pushMatrix and popMatrix. */
    static void snap(GuiGraphicsExtractor g, float x, float y) {
        var m = g.pose();
        int gs = guiScale();
        double px = gs * (m.m00() * x + m.m10() * y + m.m20()), py = gs * (m.m01() * x + m.m11() * y + m.m21());
        double fx = Math.round(px) - px, fy = Math.round(py) - py, det = m.m00() * m.m11() - m.m01() * m.m10();
        if ((Math.abs(fx) < 0.02 && Math.abs(fy) < 0.02) || Math.abs(det) < 1e-9) return;
        double ax = fx / gs, ay = fy / gs;
        m.translate((float) ((m.m11() * ax - m.m10() * ay) / det), (float) ((m.m00() * ay - m.m01() * ax) / det));
    }
    /** Draws with the origin moved to the nearest screen pixel, so glyph texels map 1:1 at half-step scales too. */
    private static void draw(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        var m = g.pose();
        m.pushMatrix();
        try {
            snap(g, x, y);
            g.text(font, text, x, y, color, false);
        } finally { m.popMatrix(); }
    }
    public static Component component(String value) { return component(value, Face.REGULAR, 2 * guiScale()); }
    public static Component component(String value, Face face, int scale) {
        FontDescription description = face(face, scale);
        return Component.literal(value).withStyle(style -> style.withFont(description));
    }
    /** One component whose glyphs fade from {@code left} to {@code right} (RGB); drawn in a single text call. */
    public static Component gradient(String value, Face face, int scale, int left, int right) {
        FontDescription description = face(face, scale);
        String shown = value == null ? "" : value;
        int count = Math.max(1, shown.codePointCount(0, shown.length())), index = 0;
        net.minecraft.network.chat.MutableComponent out = Component.literal("");
        for (int i = 0; i < shown.length(); ) {
            int cp = shown.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            int rgb = UiDraw.mix(left, right, count <= 1 ? 0 : index++ / (double) (count - 1));
            out.append(Component.literal(glyph).withStyle(style -> style.withFont(description).withColor(rgb)));
        }
        return out;
    }
    public static void gradient(GuiGraphicsExtractor g, Font font, String value, int x, int y, int left, int right, double opacity, Face face) {
        draw(g, font, gradient(value, face, halfScale(g), left, right), x, y, UiDraw.alpha(0xFFFFFF, opacity));
    }
    public static void gradientCentered(GuiGraphicsExtractor g, Font font, String value, int centerX, int y, int left, int right, double opacity, Face face) {
        Component text = gradient(value, face, halfScale(g), left, right);
        draw(g, font, text, centerX - font.width(text) / 2, y, UiDraw.alpha(0xFFFFFF, opacity));
    }
    /** Width in GUI units; {@code half} is {@link #halfScale} of the pose the text is drawn in. */
    public static int width(Font font, String value, Face face, int half) { return font.width(component(value, face, half)); }
    public static int width(GuiGraphicsExtractor g, Font font, String value, Face face) { return width(font, value, face, halfScale(g)); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width) { text(g, font, value, x, y, color, width, Face.REGULAR); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width, Face face) {
        int scale = halfScale(g);
        String shown = value == null ? "" : value;
        if (font.width(component(shown, face, scale)) > width) {
            while (!shown.isEmpty() && font.width(component(shown + "…", face, scale)) > width) shown = shown.substring(0, shown.length() - 1);
            shown = shown.isEmpty() ? "" : shown.stripTrailing() + "…";
        }
        draw(g, font, component(shown, face, scale), x, y, color);
    }
    public static void centered(GuiGraphicsExtractor g, Font font, String value, int centerX, int y, int color, Face face) {
        Component text = component(value, face, halfScale(g));
        draw(g, font, text, centerX - font.width(text) / 2, y, color);
    }
    /** 10 x 10 icon whose box starts at (x, y). */
    public static void icon(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { draw(g, font, component(icon, Face.ICON, halfScale(g)), x, y, color); }
    /** 8 x 8 icon whose box starts at (x, y + 1). */
    public static void iconSmall(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { draw(g, font, component(icon, Face.ICON_SMALL, halfScale(g)), x, y, color); }
    /** 16 x 16 icon whose box starts at (x, y). */
    public static void iconLarge(GuiGraphicsExtractor g, Font font, String icon, int x, int y, int color) { draw(g, font, component(icon, Face.ICON_LARGE, halfScale(g)), x, y, color); }
}
