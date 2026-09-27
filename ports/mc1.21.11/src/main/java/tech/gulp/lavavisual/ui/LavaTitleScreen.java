package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import tech.gulp.lavavisual.Edition;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * LavaVisual main menu: the vanilla panorama under a dark gradient in the theme colours, the logo, and a column of
 * large buttons. "Обычное меню" (top right) or the Interface tab brings the vanilla title screen back.
 */
public final class LavaTitleScreen extends Screen {
    /** CI: the title screen was swapped in by the hook (not opened by hand). */
    public static boolean replaced;
    private static boolean logged;
    private final long opened = System.nanoTime();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Double> motions = new HashMap<>();
    private List<Item> items = List.of();
    private long lastFrame = System.nanoTime();
    private double ease = 1;
    private int mx, my;

    private record Hit(int x, int y, int w, int h, Runnable action) { }
    private record Item(String icon, String label, Runnable action) { }

    public LavaTitleScreen() { super(UiFont.component("LavaVisual")); }

    /** Swaps the vanilla title screen for this one, unless it is turned off or the game runs as the demo. */
    public static Screen replace(Screen screen) {
        try {
            Minecraft mc = Minecraft.getInstance();
            boolean title = screen instanceof TitleScreen || screen == null && mc != null && mc.level == null;
            if (!title || mc == null || mc.isDemo() || !LavaVisualClient.config().customTitle) return screen;
            replaced = true;
            return new LavaTitleScreen();
        } catch (RuntimeException | LinkageError error) {
            return screen;
        }
    }

    @Override protected void init() {
        items = items();
        if (!logged) { logged = true; LavaVisual.LOGGER.info("LavaVisual title screen ready"); }
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { extractPanorama(g, delta); }

    private List<Item> items() {
        List<Item> list = new ArrayList<>();
        list.add(new Item(Icons.USER, "Одиночная игра", () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        list.add(new Item(Icons.SERVER, "Сетевая игра", () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this))));
        list.add(new Item(Icons.SPARKLES, "LavaVisual", () -> minecraft.gui.setScreen(new ClickGuiScreen())));
        list.add(new Item(Icons.SETTINGS, "Настройки", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false))));
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) list.add(new Item(Icons.LAYERS, "Моды", this::openMods));
        list.add(new Item(Icons.POWER, "Выход", () -> minecraft.stop()));
        return list;
    }
    private void openMods() {
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(this);
            minecraft.gui.setScreen((Screen) screen);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot open the mod list", error);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX; my = mouseY;
        var c = LavaVisualClient.config();
        long now = System.nanoTime();
        ease = c.animations ? 1 - Math.exp(-Math.min(0.1, (now - lastFrame) / 1e9) * 14) : 1;
        lastFrame = now;
        double t = (now - opened) / 1e9, enter = c.animations ? 1 - Math.pow(1 - Math.clamp(t / 0.7, 0, 1), 3) : 1;
        int ac = c.color("menu"), ac2 = c.color2("menu");

        // Shade: dark behind the text on the left, the panorama stays visible on the right; a warm floor glow.
        UiDraw.roundH(g, 0, 0, width, height, 0, UiDraw.alpha(0x05070C, 0.9 * enter), UiDraw.alpha(0x05070C, 0.3 * enter));
        g.fillGradient(0, height / 2, width, height, 0x00000000, UiDraw.alpha(0x05070C, 0.6 * enter));
        g.fillGradient(0, height - 70, width, height, 0x00000000, UiDraw.alpha(UiDraw.mix(ac, ac2, 0.35), 0.1 * enter));
        embers(g, now, ac, ac2, enter);

        List<Item> list = items;
        int left = Math.max(22, (int) (width * 0.075)), buttonW = Math.clamp(width / 3, 160, 214), buttonH = 24, gap = 6;
        int logoW = Logo.width(false), logoH = Logo.MENU_H, headH = logoH * 2 + 30;
        int blockH = headH + list.size() * (buttonH + gap);
        int top = Math.max(12, (height - blockH) / 2 - 6);

        // Logo (twice the menu size, pixel-exact texture), name and edition.
        int lift = (int) Math.round((1 - enter) * 10);
        // Soft glow behind the logo: many faint discs read as a smooth radial gradient, without a visible edge.
        UiDraw.glowDisc(g, left + logoW, top + logoH + lift, 150, UiDraw.alpha(UiDraw.mix(ac, ac2, 0.3), 0.2 * enter));
        g.pose().pushMatrix();
        g.pose().translate(left, top + lift);
        g.pose().scale(2f);
        Logo.draw(g, false, 0, 0, UiDraw.alpha(0xFFFFFF, Math.max(0.05, enter)));
        g.pose().popMatrix();
        int nameX = left + logoW * 2 + 12, nameY = top + lift + logoH - 16;
        g.pose().pushMatrix();
        g.pose().translate(nameX, nameY);
        g.pose().scale(2f);
        UiFont.gradient(g, font, "LavaVisual", 0, 0, UiDraw.mix(ac, 0xFFFFFF, 0.12), UiDraw.mix(ac2, 0xFFFFFF, 0.12), enter, UiFont.Face.BOLD);
        g.pose().popMatrix();
        String edition = (Edition.client() ? "Client" : "Мод") + "  ·  Minecraft 26.2";
        UiFont.text(g, font, edition, nameX + 1, nameY + 24, UiDraw.alpha(0xA3ACBA, enter), width - nameX - 10, UiFont.Face.REGULAR);

        // Buttons slide in one after another.
        int y = top + headH;
        for (int i = 0; i < list.size(); i++) {
            Item item = list.get(i);
            double appear = c.animations ? Math.clamp((t - 0.1 - i * 0.05) / 0.35, 0, 1) : 1;
            appear = 1 - Math.pow(1 - appear, 3);
            boolean over = mx >= left && mx < left + buttonW && my >= y && my < y + buttonH;
            double h = motion("button" + i, over ? 1 : 0);
            int x = left + (int) Math.round(-14 * (1 - appear) + 4 * h);
            boolean exit = i == list.size() - 1;
            UiDraw.roundV(g, x, y, buttonW, buttonH, 7, UiDraw.alpha(0x1D2028, 0.82 * appear), UiDraw.alpha(0x15171C, 0.82 * appear));
            if (h > 0.01) UiDraw.roundH(g, x, y, buttonW, buttonH, 7, UiDraw.alpha(exit ? 0xC2413B : ac, 0.92 * h * appear), UiDraw.alpha(exit ? 0x8E2A2A : ac2, 0.78 * h * appear));
            g.fill(x + 7, y, x + buttonW - 7, y + 1, UiDraw.alpha(0xFFFFFF, (0.05 + 0.12 * h) * appear));
            int iconColor = UiDraw.alpha(UiDraw.mix(UiDraw.mix(ac, 0xFFFFFF, 0.35), 0xFFFFFF, h), appear);
            UiFont.icon(g, font, item.icon(), x + 10, y + 7, iconColor);
            UiFont.text(g, font, item.label(), x + 29, y + 8, UiDraw.alpha(UiDraw.mix(0xE3E7EE, 0xFFFFFF, h), appear), buttonW - 38, UiFont.Face.BOLD);
            UiFont.icon(g, font, Icons.CHEVRON_RIGHT, x + buttonW - 18, y + 7, UiDraw.alpha(0xFFFFFF, 0.8 * h * appear));
            hits.add(new Hit(left, y, buttonW, buttonH, item.action()));
            y += buttonH + gap;
        }

        // Top right: language and the way back to the vanilla menu.
        corner(g, Icons.EARTH, "Язык", width - 34, 10, () -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())), ac, ac2, enter);
        corner(g, Icons.LAYOUT_DASHBOARD, "Обычное меню", width - 62, 10, () -> {
            c.customTitle = false;
            LavaVisualClient.save();
            minecraft.gui.setScreen(new TitleScreen());
        }, ac, ac2, enter);

        String version = "LavaVisual " + Edition.label();
        UiFont.text(g, font, version, 8, height - 14, UiDraw.alpha(0x7D8795, enter), width / 2, UiFont.Face.SMALL);
        String legal = "Copyright Mojang AB. Do not distribute!";
        int legalW = UiFont.width(g, font, legal, UiFont.Face.SMALL);
        UiFont.text(g, font, legal, width - legalW - 8, height - 14, UiDraw.alpha(0x7D8795, enter), legalW + 2, UiFont.Face.SMALL);
    }

    private void corner(GuiGraphicsExtractor g, String icon, String label, int x, int y, Runnable action, int ac, int ac2, double enter) {
        boolean over = mx >= x && mx < x + 24 && my >= y && my < y + 24;
        double h = motion("corner" + label, over ? 1 : 0);
        UiDraw.round(g, x, y, 24, 24, 7, UiDraw.alpha(0x1D2028, 0.75 * enter));
        if (h > 0.01) UiDraw.roundH(g, x, y, 24, 24, 7, UiDraw.alpha(ac, 0.9 * h), UiDraw.alpha(ac2, 0.75 * h));
        UiFont.icon(g, font, icon, x + 6, y + 7, UiDraw.alpha(UiDraw.mix(0xC5CCD6, 0xFFFFFF, h), enter));
        if (h > 0.05) {
            int w = UiFont.width(g, font, label, UiFont.Face.SMALL);
            UiDraw.round(g, x + 12 - (w + 12) / 2, y + 28, w + 12, 15, 5, UiDraw.alpha(0x14161B, 0.9 * h));
            UiFont.text(g, font, label, x + 12 - w / 2, y + 32, UiDraw.alpha(0xE3E7EE, h), w + 2, UiFont.Face.SMALL);
        }
        hits.add(new Hit(x, y, 24, 24, action));
    }
    /** Slow embers rising in both theme colours; positions are pure functions of time. */
    private void embers(GuiGraphicsExtractor g, long now, int ac, int ac2, double enter) {
        double t = now / 1e9, span = height + 24;
        for (int i = 0, n = tech.gulp.lavavisual.Platform.android() ? 22 : 40; i < n; i++) {
            double r1 = frac(Math.sin(i * 12.9898) * 43758.5453), r2 = frac(Math.sin(i * 78.233) * 24634.6345), r3 = frac(Math.sin(i * 39.425) * 12345.6789);
            double rise = (t * (6 + 14 * r2) + r3 * span) % span, life = rise / span;
            int x = (int) (r1 * width + Math.sin(t * (0.25 + r3 * 0.5) + i) * 8), y = (int) (height + 12 - rise);
            double a = Math.sin(Math.PI * life) * (0.25 + 0.45 * r3) * enter;
            int color = UiDraw.mix(ac, ac2, r1);
            double radius = r2 > 0.72 ? 1.4 : 0.8;
            UiDraw.circle(g, x, y, radius * 2.6, UiDraw.alpha(color, a * 0.2));
            UiDraw.circle(g, x, y, radius, UiDraw.alpha(color, a));
        }
    }
    private static double frac(double v) { return v - Math.floor(v); }
    private double motion(String key, double goal) {
        double next = motions.getOrDefault(key, goal) + (goal - motions.getOrDefault(key, goal)) * ease;
        motions.put(key, next);
        return next;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (event.x() >= hit.x() && event.x() < hit.x() + hit.w() && event.y() >= hit.y() && event.y() < hit.y() + hit.h()) {
                    UiSound.click();
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
