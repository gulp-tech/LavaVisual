package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import tech.gulp.lavavisual.LavaVisualClient;

/** Optional lightweight preset. Only touches vanilla options while enabled and restores them after. */
public final class PerformanceMode {
    private PerformanceMode() { }
    public static boolean active() { return LavaVisualClient.config().fpsBoost; }
    /** Invisible adaptive quality for LavaVisual's own effects; never touches game settings. */
    public static double quality() {
        int fps = LavaVisualClient.STATE.fps;
        double q = fps <= 0 ? 1 : fps >= 50 ? 1 : fps >= 40 ? 0.75 : fps >= 30 ? 0.5 : 0.35;
        return active() ? Math.min(q, 0.6) : q;
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
                c.boostApplied = true;
            }
            // Barely visible: biome colour blending 1 instead of 2+, fewer simulated chunks (singleplayer),
            // and vanilla's AFK frame limiter so an idle game stops burning the GPU / phone battery.
            if (options.biomeBlendRadius().get() > 1) options.biomeBlendRadius().set(1);
            if (options.simulationDistance().get() > 8) options.simulationDistance().set(8);
            options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.AFK);
            options.renderDistance().set(Math.min(options.renderDistance().get(), 12));
            if (options.particles().get() == ParticleStatus.ALL) options.particles().set(ParticleStatus.DECREASED);
            options.entityShadows().set(false);
        } else if (c.boostApplied) {
            if (c.savedRenderDistance >= 0) options.renderDistance().set(c.savedRenderDistance);
            if (c.savedParticles >= 0 && c.savedParticles < ParticleStatus.values().length) options.particles().set(ParticleStatus.values()[c.savedParticles]);
            options.entityShadows().set(c.savedEntityShadows);
            if (c.savedBiomeBlend >= 0) options.biomeBlendRadius().set(c.savedBiomeBlend);
            if (c.savedSimulation >= 0) options.simulationDistance().set(c.savedSimulation);
            var limits = net.minecraft.client.InactivityFpsLimit.values();
            if (c.savedInactivity >= 0 && c.savedInactivity < limits.length) options.inactivityFpsLimit().set(limits[c.savedInactivity]);
            c.boostApplied = false;
        }
        LavaVisualClient.save();
    }
}
