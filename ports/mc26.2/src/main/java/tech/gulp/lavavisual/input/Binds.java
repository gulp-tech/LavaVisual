package tech.gulp.lavavisual.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.ClickGuiScreen;
import tech.gulp.lavavisual.ui.Icons;

/**
 * LavaVisual hotkeys. They are ordinary vanilla key mappings (saved in options.txt, listed in
 * Controls and usable from mobile launchers' on-screen buttons); the "Бинды" tab edits the same objects.
 */
public final class Binds {
    private Binds() { }
    public enum Action {
        MENU("menu", "Открыть меню", Icons.LAYOUT_DASHBOARD, GLFW.GLFW_KEY_RIGHT_SHIFT),
        DISABLE_ALL("toggle", "Выключить всё", Icons.POWER, GLFW.GLFW_KEY_V),
        WAYPOINT_ADD("waypoint_add", "Поставить метку", Icons.MAP_PINNED, GLFW.GLFW_KEY_B),
        WAYPOINTS("waypoints", "Список меток", Icons.FLAG, -1),
        MINIMAP("minimap", "Миникарта", Icons.MAP, -1),
        MINIMAP_ZOOM("minimap_zoom", "Масштаб миникарты", Icons.SCALING, -1),
        HUD("hud", "Скрыть / показать HUD", Icons.EYE_OFF, -1),
        TARGET("target", "Target HUD", Icons.TARGET, -1),
        CROSSHAIR("crosshair", "Прицел", Icons.CROSSHAIR, -1),
        HAT("hat", "Шляпа", Icons.CROWN, -1),
        ESP("esp", "Target ESP", Icons.SCAN_EYE, -1),
        TRAIL("trail", "Trails", Icons.WIND, -1),
        PARTICLES("particles", "Hit Particles", Icons.SPARKLE, -1),
        SOUNDS("sounds", "Звуки ударов", Icons.VOLUME_2, -1),
        BOOST("fps_boost", "FPS Boost", Icons.ROCKET, -1);
        public final String id, title, icon;
        public final int defaultKey;
        Action(String id, String title, String icon, int defaultKey) { this.id = id; this.title = title; this.icon = icon; this.defaultKey = defaultKey; }
    }
    private static final EnumMap<Action, KeyMapping> MAPPINGS = new EnumMap<>(Action.class);

    public static void register(KeyMapping.Category category) {
        for (Action action : Action.values())
            MAPPINGS.put(action, KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lavavisual." + action.id, InputConstants.Type.KEYSYM, action.defaultKey, category)));
    }
    public static KeyMapping mapping(Action action) { return MAPPINGS.get(action); }
    public static String keyName(Action action) {
        KeyMapping mapping = MAPPINGS.get(action);
        if (mapping == null || mapping.isUnbound()) return "не назначено";
        return mapping.getTranslatedKeyMessage().getString();
    }
    /** True when another LavaVisual or vanilla mapping uses the same key. */
    public static boolean conflicts(Action action, Minecraft mc) {
        KeyMapping mapping = MAPPINGS.get(action);
        if (mapping == null || mapping.isUnbound() || mc.options == null) return false;
        // Debug hotkeys (F3 + B, F3 + V, ...) only fire with the debug modifier held; they are not real conflicts.
        for (KeyMapping other : mc.options.keyMappings)
            if (other != mapping && other.same(mapping) && !other.getName().startsWith("key.debug.")) return true;
        return false;
    }
    public static void set(Action action, InputConstants.Key key, Minecraft mc) {
        KeyMapping mapping = MAPPINGS.get(action);
        if (mapping == null) return;
        mapping.setKey(key);
        KeyMapping.resetMapping();
        if (mc.options != null) mc.options.save();
    }
    public static void reset(Action action, Minecraft mc) {
        KeyMapping mapping = MAPPINGS.get(action);
        if (mapping != null) set(action, mapping.getDefaultKey(), mc);
    }
    public static void resetAll(Minecraft mc) {
        for (var entry : MAPPINGS.entrySet()) entry.getValue().setKey(entry.getValue().getDefaultKey());
        KeyMapping.resetMapping();
        if (mc.options != null) mc.options.save();
    }

    /** Edge-triggered: vanilla counts presses, so each press runs once. Only while no screen is open. */
    public static void tick(Minecraft mc) {
        boolean debug = debugHeld(mc);
        for (var entry : MAPPINGS.entrySet()) {
            while (entry.getValue().consumeClick()) if (mc.gui.screen() == null && !debug) run(entry.getKey(), mc);
        }
    }
    /** F3 held: the press belongs to a vanilla debug combo (F3 + B hitboxes, F3 + V version), not to us. */
    private static boolean debugHeld(Minecraft mc) {
        KeyMapping modifier = KeyMapping.get("key.debug.modifier");
        if (modifier != null && modifier.isDown()) return true;
        try { return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F3); } catch (RuntimeException e) { return false; }
    }
    private static void run(Action action, Minecraft mc) {
        var c = LavaVisualClient.config();
        switch (action) {
            case MENU -> mc.gui.setScreen(new ClickGuiScreen());
            case DISABLE_ALL -> { c.disableAll(); tech.gulp.lavavisual.effects.PerformanceMode.update(mc); }
            case WAYPOINT_ADD -> { if (mc.player != null) mc.gui.setScreen(new tech.gulp.lavavisual.ui.WaypointScreen(null, null)); }
            case WAYPOINTS -> mc.gui.setScreen(new ClickGuiScreen(ClickGuiScreen.PAGE_MAP));
            case MINIMAP -> { var w = c.widgets.get("minimap"); w.visible = !w.visible; }
            case MINIMAP_ZOOM -> c.mapZoom = (c.mapZoom + 1) % 3;
            case HUD -> LavaVisualClient.STATE.hudHidden = !LavaVisualClient.STATE.hudHidden;
            case TARGET -> { var w = c.widgets.get("target"); w.visible = !w.visible; }
            case CROSSHAIR -> c.crosshairEnabled = !c.crosshairEnabled;
            case HAT -> c.hatEnabled = !c.hatEnabled;
            case ESP -> c.espEnabled = !c.espEnabled;
            case TRAIL -> c.trailEnabled = !c.trailEnabled;
            case PARTICLES -> c.particlesEnabled = !c.particlesEnabled;
            case SOUNDS -> { boolean on = !(c.hitSoundEnabled || c.critSoundEnabled); c.hitSoundEnabled = on; c.critSoundEnabled = on; }
            case BOOST -> { c.fpsBoost = !c.fpsBoost; tech.gulp.lavavisual.effects.PerformanceMode.update(mc); }
        }
        LavaVisualClient.save();
        if (action != Action.MENU && action != Action.WAYPOINT_ADD && action != Action.WAYPOINTS) Toast.show(action.title + status(action, c));
    }
    private static String status(Action action, tech.gulp.lavavisual.config.HudConfig c) {
        return switch (action) {
            case DISABLE_ALL -> ": всё выключено";
            case MINIMAP -> onOff(c.widgets.get("minimap").visible);
            case MINIMAP_ZOOM -> ": " + new String[]{"ближе", "обычный", "дальше"}[c.mapZoom];
            case HUD -> LavaVisualClient.STATE.hudHidden ? ": скрыт" : ": показан";
            case TARGET -> onOff(c.widgets.get("target").visible);
            case CROSSHAIR -> onOff(c.crosshairEnabled);
            case HAT -> onOff(c.hatEnabled);
            case ESP -> onOff(c.espEnabled);
            case TRAIL -> onOff(c.trailEnabled);
            case PARTICLES -> onOff(c.particlesEnabled);
            case SOUNDS -> onOff(c.hitSoundEnabled);
            case BOOST -> onOff(c.fpsBoost);
            default -> "";
        };
    }
    private static String onOff(boolean on) { return on ? ": вкл" : ": выкл"; }

    /** Tiny non-intrusive confirmation drawn by the HUD for 1.5 s. */
    public static final class Toast {
        private Toast() { }
        public static volatile String text;
        public static volatile long at;
        public static void show(String value) { text = value; at = System.currentTimeMillis(); }
    }
}
