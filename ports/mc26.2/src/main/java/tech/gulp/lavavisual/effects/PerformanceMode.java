package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * FPS Boost with four levels. Every level is computed from the player's own settings saved when the boost was switched
 * on, so changing the level (up or down) is exact and switching the boost off restores everything. The two lower
 * levels keep the render distance.
 */
public final class PerformanceMode {
    private PerformanceMode() { }
    public static final String[] LEVELS = {"Лёгкий", "Средний", "Сильный", "Макс"};
    public static boolean active() { return LavaVisualClient.config().fpsBoost; }
    public static int level() { return Math.clamp(LavaVisualClient.config().fpsBoostLevel, 1, 4); }
    /** Invisible adaptive quality for LavaVisual's own effects; never touches game settings. */
    public static double quality() {
        int fps = LavaVisualClient.STATE.fps;
        double q = fps <= 0 ? 1 : fps >= 50 ? 1 : fps >= 40 ? 0.75 : fps >= 30 ? 0.5 : 0.35;
        return active() ? Math.min(q, new double[] {0.85, 0.7, 0.55, 0.4}[level() - 1]) : q;
    }
    public static void update(Minecraft client) {
        if (client == null || client.options == null) return;
        var c = LavaVisualClient.config();
        var options = client.options;
        if (c.fpsBoost) {
            if (!c.boostApplied) {
                c.savedRenderDistance = options.renderDistance().get();
                c.savedParticles = options.particles().get().ordinal();
                c.savedEntityShadows = options.entityShadows().get();
                c.savedBiomeBlend = options.biomeBlendRadius().get();
                c.savedSimulation = options.simulationDistance().get();
                c.savedInactivity = options.inactivityFpsLimit().get().ordinal();
                c.savedClouds = -1;
                c.boostApplied = true;
            }
            if (c.savedClouds < 0) { // values missing from older configs
                c.savedClouds = options.cloudStatus().get().ordinal();
                c.savedEntityDistance = options.entityDistanceScaling().get();
                c.savedBlur = options.menuBackgroundBlurriness().get();
            }
            apply(options, c, level());
        } else if (c.boostApplied) {
            restore(options, c);
            c.boostApplied = false;
        }
        LavaVisualClient.save();
    }
    private static void apply(net.minecraft.client.Options options, tech.gulp.lavavisual.config.HudConfig c, int level) {
        set(options.entityShadows(), false);
        set(options.inactivityFpsLimit(), net.minecraft.client.InactivityFpsLimit.AFK);
        ParticleStatus particles = particles(c);
        if (level >= 3) particles = ParticleStatus.MINIMAL;
        else if (particles == ParticleStatus.ALL) particles = ParticleStatus.DECREASED;
        set(options.particles(), particles);
        int blend = Math.max(0, c.savedBiomeBlend);
        set(options.biomeBlendRadius(), Math.min(blend, level == 1 ? 2 : level == 4 ? 0 : 1));
        var clouds = clouds(c);
        if (level >= 3) clouds = net.minecraft.client.CloudStatus.OFF;
        else if (level == 2 && clouds == net.minecraft.client.CloudStatus.FANCY) clouds = net.minecraft.client.CloudStatus.FAST;
        set(options.cloudStatus(), clouds);
        int simulation = c.savedSimulation > 0 ? c.savedSimulation : options.simulationDistance().get();
        set(options.simulationDistance(), Math.min(simulation, new int[] {32, 10, 8, 6}[level - 1]));
        int render = c.savedRenderDistance > 0 ? c.savedRenderDistance : options.renderDistance().get();
        set(options.renderDistance(), Math.min(render, new int[] {64, 64, 16, 10}[level - 1]));
        double entities = c.savedEntityDistance > 0 ? c.savedEntityDistance : options.entityDistanceScaling().get();
        set(options.entityDistanceScaling(), level == 4 ? Math.min(entities, 0.75) : entities);
        int blur = c.savedBlur >= 0 ? c.savedBlur : options.menuBackgroundBlurriness().get();
        set(options.menuBackgroundBlurriness(), level >= 3 ? 0 : Math.min(blur, level == 2 ? 3 : blur));
    }
    private static void restore(net.minecraft.client.Options options, tech.gulp.lavavisual.config.HudConfig c) {
        if (c.savedRenderDistance > 0) set(options.renderDistance(), c.savedRenderDistance);
        set(options.particles(), particles(c));
        set(options.entityShadows(), c.savedEntityShadows);
        if (c.savedBiomeBlend >= 0) set(options.biomeBlendRadius(), c.savedBiomeBlend);
        if (c.savedSimulation > 0) set(options.simulationDistance(), c.savedSimulation);
        var limits = net.minecraft.client.InactivityFpsLimit.values();
        if (c.savedInactivity >= 0 && c.savedInactivity < limits.length) set(options.inactivityFpsLimit(), limits[c.savedInactivity]);
        if (c.savedClouds >= 0) set(options.cloudStatus(), clouds(c));
        if (c.savedEntityDistance > 0) set(options.entityDistanceScaling(), c.savedEntityDistance);
        if (c.savedBlur >= 0) set(options.menuBackgroundBlurriness(), c.savedBlur);
    }
    private static ParticleStatus particles(tech.gulp.lavavisual.config.HudConfig c) {
        var all = ParticleStatus.values();
        return c.savedParticles >= 0 && c.savedParticles < all.length ? all[c.savedParticles] : ParticleStatus.ALL;
    }
    private static net.minecraft.client.CloudStatus clouds(tech.gulp.lavavisual.config.HudConfig c) {
        var all = net.minecraft.client.CloudStatus.values();
        return c.savedClouds >= 0 && c.savedClouds < all.length ? all[c.savedClouds] : net.minecraft.client.CloudStatus.FANCY;
    }
    /** Sets an option only when it changes (a render distance write reloads chunks). */
    private static <T> void set(net.minecraft.client.OptionInstance<T> option, T value) {
        if (!java.util.Objects.equals(option.get(), value)) option.set(value);
    }
}
