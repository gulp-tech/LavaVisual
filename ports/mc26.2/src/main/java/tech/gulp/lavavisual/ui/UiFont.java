package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Bundled OFL font affects only LavaVisual, never replaces Minecraft's global font. */
public final class UiFont {
    private static final FontDescription FACE = new FontDescription.Resource(Identifier.fromNamespaceAndPath("lavavisual", "ui"));
    private UiFont() { }
    public static Component component(String value) { return Component.literal(value).withStyle(style -> style.withFont(FACE)); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width) {
        String shown = value;
        while (!shown.isEmpty() && font.width(component(shown)) > width) shown = shown.substring(0, shown.length() - 1);
        g.text(font, component(shown), x, y, color, false);
    }
}
