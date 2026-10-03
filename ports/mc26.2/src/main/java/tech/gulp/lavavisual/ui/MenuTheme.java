package tech.gulp.lavavisual.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import tech.gulp.lavavisual.Edition;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Decoration for the vanilla screens that pick or create a world and the server list: a thin theme bar along the top,
 * a soft glow behind it and a vignette at the bottom, so the plain grey menus look like the rest of LavaVisual. It is
 * drawn after the screen itself has drawn, so nothing is ever hidden — only the edges get colour.
 */
public final class MenuTheme {
    private MenuTheme() { }

    /** True for the screens LavaVisual decorates. */
    public static boolean styled(Screen screen) {
        return screen instanceof SelectWorldScreen || screen instanceof CreateWorldScreen || screen instanceof JoinMultiplayerScreen;
    }

    /** Called once a decorated screen has drawn its widgets. */
    public static void frame(GuiGraphicsExtractor g, Screen screen, int mouseX, int mouseY) {
        if (!styled(screen)) return;
        var c = LavaVisualClient.config();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        int w = screen.width, h = screen.height;
        int ac = c.color("menu"), ac2 = c.color2("menu");
        double time = System.nanoTime() / 1e9;
        // Theme bar and its glow: the same two colours the menus use.
        UiDraw.glowDisc(g, w / 2.0, -6, 110, UiDraw.alpha(UiDraw.mix(ac, ac2, 0.4), 0.10));
        UiDraw.roundH(g, 0, 0, w, 3, 0, UiDraw.alpha(ac, 0.95), UiDraw.alpha(ac2, 0.95));
        g.fillGradient(0, 3, w, 3 + 14, UiDraw.alpha(UiDraw.mix(ac, ac2, 0.5), 0.10), 0x00000000);
        // Vignette: the bottom of the screen darkens, so the list fades into the background.
        g.fillGradient(0, h - 46, w, h, 0x00000000, UiDraw.alpha(0x05070C, 0.5));
        // Brand: the crest in the bottom-left corner and its label, faded while the pointer is near.
        int logoY = h - Logo.HUD_H - 8;
        double near = Math.clamp(1 - Math.hypot(mouseX - 20, mouseY - (logoY + Logo.HUD_H / 2.0)) / 90.0, 0, 1);
        double pulse = 0.75 + 0.25 * Math.sin(time * 1.6);
        Logo.draw(g, true, 8, logoY, UiDraw.alpha(0xFFFFFF, 0.55 + 0.35 * near));
        UiDraw.circle(g, 8 + Logo.width(true) / 2.0, logoY - 4, 2.0 + 0.8 * pulse, UiDraw.alpha(UiDraw.mix(ac, ac2, 0.5), 0.35 + 0.25 * near));
        String brand = "LavaVisual " + Edition.label();
        int brandW = UiFont.width(g, mc.font, brand, UiFont.Face.SMALL);
        UiDraw.round(g, 8, logoY - 16, brandW + 12, 12, 4, UiDraw.alpha(0x14161B, 0.55 + 0.25 * near));
        UiFont.text(g, mc.font, brand, 14, logoY - 13, UiDraw.alpha(0xC5CCD6, 0.6 + 0.35 * near), brandW + 2, UiFont.Face.SMALL);
    }
}
