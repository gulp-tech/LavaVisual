package tech.gulp.lavavisual;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.config.ConfigStore;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.hud.HudRenderer;
import tech.gulp.lavavisual.hud.SessionState;
import tech.gulp.lavavisual.ui.ClickGuiScreen;

public final class LavaVisualClient implements ClientModInitializer {
    public static final SessionState STATE = new SessionState();
    private static final ConfigStore STORE = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("lavavisual-hud"));
    private static HudConfig config = STORE.load(0);
    private long nextSample;
    private Object previousWorld;
    private final boolean uiSmoke = Boolean.getBoolean("lavavisual.uiSmoke");
    private int smokeTicks = -1;
    private boolean boostChecked;
    public static HudConfig config() { return config; }
    public static void save() { if (!STORE.save(config, 0)) LavaVisual.LOGGER.error("Could not save LavaVisual settings"); }
    public static boolean saveProfile(int slot) { boolean ok = STORE.save(config, slot); if (ok) save(); return ok; }
    public static boolean loadProfile(int slot) {
        if (!STORE.exists(slot)) return false;
        config = STORE.load(slot);
        save();
        return true;
    }
    public static void resetLayout() {
        var defaults = HudConfig.defaults();
        for (String id : HudConfig.IDS) {
            var widget = config.widgets.get(id); var fresh = defaults.get(id);
            widget.x = fresh.x; widget.y = fresh.y;
        }
        save();
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("lavavisual", path); }

    @Override public void onInitializeClient() {
        save();
        tech.gulp.lavavisual.effects.WorldCosmetics.register();
        tech.gulp.lavavisual.effects.PlayerTags.registerClient();
        var category = KeyMapping.Category.register(id("hud"));
        var menu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lavavisual.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        var toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lavavisual.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Explicit CI-only switch; never enabled by normal game or server settings.
            if (uiSmoke) {
                if (smokeTicks < 0 && client.gui.screen() != null) smokeTicks = 0;
                if (smokeTicks >= 0) {
                    smokeTicks++;
                    if (smokeTicks == 20) client.gui.setScreen(new ClickGuiScreen());
                    if (smokeTicks == 60) client.gui.setScreen(new ClickGuiScreen(1));
                    if (smokeTicks == 100) client.gui.setScreen(new ClickGuiScreen(2));
                    if (smokeTicks == 140) client.gui.setScreen(new ClickGuiScreen(0, "target"));
                    if (smokeTicks == 180) client.gui.setScreen(new ClickGuiScreen(1, "crosshair"));
                    if (smokeTicks == 220) client.gui.setScreen(new tech.gulp.lavavisual.ui.HudEditorScreen(new ClickGuiScreen()));
                    if (smokeTicks == 260) client.gui.setScreen(new ClickGuiScreen(3));
                    if (smokeTicks == 300) client.gui.setScreen(new ClickGuiScreen(4));
                    if (smokeTicks == 350) client.gui.setScreen(new ClickGuiScreen(5));
                    if (smokeTicks == 370) client.gui.setScreen(new tech.gulp.lavavisual.ui.HandEditorScreen(new ClickGuiScreen()));
                    if (smokeTicks == 320) tech.gulp.lavavisual.effects.AudioRegression.run(client);
                    if (smokeTicks == 390) LavaVisual.LOGGER.info("LavaVisual badge marker " + (tech.gulp.lavavisual.effects.Badge.marked(client.options.buildPlayerInformation()) ? "on" : "off"));
                    if (smokeTicks == 400) LavaVisual.LOGGER.info("LavaVisual UI smoke complete");
                }
            }
            while (menu.consumeClick()) if (client.gui.screen() == null) client.gui.setScreen(new ClickGuiScreen());
            while (toggle.consumeClick()) if (client.gui.screen() == null) { config.disableAll(); save(); }
            if (previousWorld != client.level) {
                previousWorld = client.level;
                STATE.coordinates = "X —   Y —   Z —";
                tech.gulp.lavavisual.effects.WorldCosmetics.clear();
            }
            long now = System.nanoTime();
            if (now >= nextSample) {
                nextSample = now + 1_000_000_000L;
                STATE.performance = client.getFps() + " FPS";
                STATE.fps = client.getFps();
            }
            if (!boostChecked && client.options != null) { boostChecked = true; if (config.fpsBoost) tech.gulp.lavavisual.effects.PerformanceMode.update(client); }
            tech.gulp.lavavisual.hud.TargetSnapshot.update(client);
            tech.gulp.lavavisual.effects.WorldCosmetics.tick(client);
            if (client.player != null) {
                var pos = client.player.blockPosition();
                STATE.coordinates = "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("widgets"), (g, delta) -> HudRenderer.draw(g, false, null));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!config.crosshairEnabled || client.player == null || client.player.isSpectator()
                    || !client.options.getCameraType().isFirstPerson()) original.extractRenderState(g, delta);
            else HudRenderer.crosshair(g);
        });
    }
}
