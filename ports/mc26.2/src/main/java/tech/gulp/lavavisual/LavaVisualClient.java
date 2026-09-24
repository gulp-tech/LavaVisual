package tech.gulp.lavavisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
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
    public static String exportConfig() { return STORE.export(config); }
    public static boolean importConfig(String text) {
        HudConfig parsed = STORE.parse(text);
        if (parsed == null) return false;
        config = parsed;
        save();
        return true;
    }
    public static String profileInfo(int slot) { return STORE.modified(slot); }
    public static java.nio.file.Path configDirectory() { return STORE.directory(); }
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
        tech.gulp.lavavisual.input.Binds.register(category);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Explicit CI-only switch; never enabled by normal game or server settings.
            if (uiSmoke) {
                if (smokeTicks < 0 && client.gui.screen() != null) smokeTicks = 0;
                if (smokeTicks >= 0) {
                    smokeTicks++;
                    // "smoke shot" lines ask tools/client_smoke.py for a screenshot; each screen then stays for 3 s.
                    if (smokeTicks == 20) client.gui.setScreen(new ClickGuiScreen(1));
                    if (smokeTicks == 50) client.gui.setScreen(new ClickGuiScreen(2));
                    if (smokeTicks == 80) client.gui.setScreen(new ClickGuiScreen(0, "target"));
                    if (smokeTicks == 110) client.gui.setScreen(new ClickGuiScreen(1, "crosshair"));
                    if (smokeTicks == 140) client.gui.setScreen(new tech.gulp.lavavisual.ui.HudEditorScreen(new ClickGuiScreen()));
                    if (smokeTicks == 170) LavaVisual.LOGGER.info("LavaVisual smoke shot hud");
                    if (smokeTicks == 230) client.gui.setScreen(new ClickGuiScreen(3));
                    if (smokeTicks == 250) tech.gulp.lavavisual.effects.AudioRegression.run(client);
                    if (smokeTicks == 270) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_MAP));
                    if (smokeTicks == 300) LavaVisual.LOGGER.info("LavaVisual smoke shot map");
                    if (smokeTicks == 360) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_BINDS));
                    if (smokeTicks == 390) LavaVisual.LOGGER.info("LavaVisual smoke shot binds");
                    if (smokeTicks == 450) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_COLORS, "color:theme"));
                    if (smokeTicks == 480) LavaVisual.LOGGER.info("LavaVisual smoke shot colors");
                    if (smokeTicks == 540) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_WORLD));
                    if (smokeTicks == 560) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_INTERFACE));
                    if (smokeTicks == 580) client.gui.setScreen(new tech.gulp.lavavisual.ui.HandEditorScreen(new ClickGuiScreen()));
                    if (smokeTicks == 600) client.gui.setScreen(new tech.gulp.lavavisual.ui.HatEditorScreen(new ClickGuiScreen(1)));
                    if (smokeTicks == 630) LavaVisual.LOGGER.info("LavaVisual smoke shot hat");
                    if (smokeTicks == 690) client.gui.setScreen(new tech.gulp.lavavisual.ui.WaypointScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_MAP), null));
                    if (smokeTicks == 720) LavaVisual.LOGGER.info("LavaVisual smoke shot waypoint");
                    if (smokeTicks == 770) { config().widgets.get("watermark").visible = true; config().widgets.get("coordinates").visible = true; }
                    if (smokeTicks == 780) client.gui.setScreen(new ClickGuiScreen());
                    if (smokeTicks == 820) LavaVisual.LOGGER.info("LavaVisual smoke shot menu");
                    if (smokeTicks == 870) LavaVisual.LOGGER.info("LavaVisual badge marker " + (tech.gulp.lavavisual.effects.Badge.marked(client.options.buildPlayerInformation()) ? "on" : "off"));
                    if (smokeTicks == 875) config().espStyle = 2;
                    if (smokeTicks == 880) client.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_EFFECTS));
                    if (smokeTicks == 910) LavaVisual.LOGGER.info("LavaVisual smoke shot effects");
                    if (smokeTicks == 970) LavaVisual.LOGGER.info("LavaVisual UI smoke complete");
                }
            }
            tech.gulp.lavavisual.input.Binds.tick(client);
            if (previousWorld != client.level) {
                previousWorld = client.level;
                STATE.coordinates = "X —   Y —   Z —";
                tech.gulp.lavavisual.effects.WorldCosmetics.clear();
                tech.gulp.lavavisual.map.Minimap.reset();
                tech.gulp.lavavisual.map.WaypointOverlay.clear();
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
            tech.gulp.lavavisual.effects.SwingStyles.tick(client);
            tech.gulp.lavavisual.map.Minimap.tick(client);
            if (client.player != null) {
                var pos = client.player.blockPosition();
                STATE.coordinates = "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("widgets"), (g, delta) -> { HudRenderer.partial = delta.getGameTimeDeltaPartialTick(false); HudRenderer.draw(g, false, null); });
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!config.crosshairEnabled || client.player == null || client.player.isSpectator()
                    || !client.options.getCameraType().isFirstPerson()) original.extractRenderState(g, delta);
            else HudRenderer.crosshair(g);
        });
    }
}
