package tech.gulp.lavavisual.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.hud.HudRenderer;

public final class ClickGuiScreen extends Screen {
    private int page, left, top, panelWidth;
    private int friendPage;
    private long opened = System.nanoTime();
    private EditBox name;
    private String status = "Local cosmetics only. Server permission is not guaranteed.";
    public ClickGuiScreen() { super(Component.literal("LavaVisual HUD")); }
    private void button(String text, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).pos(x, y).size(width, 20).build());
    }
    private void changed() { LavaVisualClient.save(); rebuildWidgets(); }
    @Override protected void init() {
        panelWidth = Math.min(570, width - 12); left = (width - panelWidth) / 2; top = Math.max(4, (height - 306) / 2);
        int third = (panelWidth - 24) / 3;
        button("HUD", left + 8, top + 36, third, () -> { page = 0; rebuildWidgets(); });
        button("Profiles", left + 12 + third, top + 36, third, () -> { page = 1; rebuildWidgets(); });
        button("Friends", left + 16 + third * 2, top + 36, third, () -> { page = 2; rebuildWidgets(); });
        if (page == 0) initHud();
        else if (page == 1) initProfiles();
        else initFriends();
        button("Done", left + panelWidth - 78, top + 273, 70, this::onClose);
    }
    private void initHud() {
        HudConfig c = LavaVisualClient.config();
        int half = panelWidth / 2;
        for (int i = 0; i < HudConfig.IDS.size(); i++) {
            String id = HudConfig.IDS.get(i);
            HudConfig.Widget widget = c.widgets.get(id);
            int y = top + 65 + i * 25;
            button((widget.visible ? "+ " : "- ") + HudRenderer.title(id), left + 8, y, half - 56,
                    () -> { widget.visible = !widget.visible; changed(); });
            button("Color", left + half - 44, y, 42, () -> { widget.color = (widget.color + 1) % HudConfig.COLORS.length; changed(); });
        }
        int x = left + half + 8, w = half - 16;
        button("HUD: " + (c.enabled ? "ON" : "OFF"), x, top + 65, w, () -> { c.enabled = !c.enabled; changed(); });
        button("Theme: " + (c.dark ? "Dark" : "Glass"), x, top + 90, w, () -> { c.dark = !c.dark; changed(); });
        button("Shadows: " + c.shadows, x, top + 115, w, () -> { c.shadows = !c.shadows; changed(); });
        button("Animation: " + c.animations, x, top + 140, w, () -> { c.animations = !c.animations; changed(); });
        button("Crosshair: " + new String[]{"Vanilla", "Dot", "Plus", "Square"}[c.crosshair], x, top + 165, w,
                () -> { c.crosshair = (c.crosshair + 1) % 4; changed(); });
        button("Crosshair color", x, top + 190, w, () -> { c.crosshairColor = (c.crosshairColor + 1) % HudConfig.COLORS.length; changed(); });
        button("Drag HUD elements", left + 8, top + 220, half - 16, () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
        button("Stopwatch: start/pause", x, top + 220, w, () -> LavaVisualClient.STATE.toggleTimer());
        button("Reset layout", left + 8, top + 246, half - 16, () -> { LavaVisualClient.resetLayout(); changed(); });
        button("Reset stopwatch", x, top + 246, w, () -> LavaVisualClient.STATE.reset());
    }
    private void initProfiles() {
        for (int slot = 1; slot <= 3; slot++) {
            final int n = slot;
            int y = top + 80 + (slot - 1) * 48;
            button("Save profile " + slot, left + 12, y, panelWidth / 2 - 20, () -> {
                LavaVisualClient.saveProfile(n); status = "Profile " + n + " save requested; see notifications.";
            });
            button("Load profile " + slot, left + panelWidth / 2 + 4, y, panelWidth / 2 - 16, () -> {
                LavaVisualClient.loadProfile(n); status = "Profile " + n + " load requested; see notifications.";
            });
        }
    }
    private void initFriends() {
        name = new EditBox(font, left + 12, top + 66, panelWidth - 112, 20, Component.literal("Minecraft username"));
        name.setMaxLength(16); addRenderableWidget(name);
        button("Add", left + panelWidth - 88, top + 66, 76, () -> {
            String value = name.getValue().trim();
            if (!value.matches("[A-Za-z0-9_]{1,16}")) { status = "Use a Minecraft name: A-Z, 0-9, underscore."; return; }
            var friends = LavaVisualClient.config().friends;
            if (friends.size() >= 64) { status = "Friend limit: 64"; return; }
            if (friends.stream().noneMatch(s -> s.equalsIgnoreCase(value))) friends.add(value);
            status = "Saved locally. No tracking or player highlighting."; changed();
        });
        var friends = LavaVisualClient.config().friends;
        friendPage = Math.min(friendPage, Math.max(0, (friends.size() - 1) / 5));
        for (int i = 0; i < 5 && friendPage * 5 + i < friends.size(); i++) {
            String value = friends.get(friendPage * 5 + i);
            button(value + "  [remove]", left + 12, top + 96 + i * 26, panelWidth - 24, () -> {
                LavaVisualClient.config().friends.remove(value); changed();
            });
        }
        button("Previous", left + 12, top + 232, 90, () -> { friendPage = Math.max(0, friendPage - 1); rebuildWidgets(); });
        button("Next", left + 108, top + 232, 90, () -> { friendPage++; rebuildWidgets(); });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xB0000000);
        float progress = LavaVisualClient.config().animations ? Math.min(1, (System.nanoTime() - opened) / 180_000_000f) : 1;
        HudRenderer.panel(g, left, top, panelWidth, 300, 0xFFFF783E);
        g.fill(left, top, left + (int) (panelWidth * progress), top + 2, 0xFFFF783E);
        g.text(font, "LAVA / HUD", left + 12, top + 10, 0xFFFF783E);
        g.text(font, "26.2   |   local cosmetic tools", left + 118, top + 10, 0xFFB9C2D2);
        g.text(font, font.plainSubstrByWidth(status, panelWidth - 20), left + 10, top + 24, 0xFF8996AA);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public void onClose() { LavaVisualClient.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
