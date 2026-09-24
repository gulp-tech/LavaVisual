package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Use the current Minecraft resource-pack font consistently in menus and HUD. */
public final class UiFont {
    private UiFont() { }
    public static Component component(String value) { return Component.literal(value); }
    public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, int width) {
        String shown = value;
        while (!shown.isEmpty() && font.width(component(shown)) > width) shown = shown.substring(0, shown.length() - 1);
        g.text(font, component(shown), x, y, color, false);
    }
}
