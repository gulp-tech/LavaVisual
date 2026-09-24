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
                c.boostApplied = true;
            }
            options.renderDistance().set(Math.min(options.renderDistance().get(), 12));
            if (options.particles().get() == ParticleStatus.ALL) options.particles().set(ParticleStatus.DECREASED);
            options.entityShadows().set(false);
        } else if (c.boostApplied) {
            if (c.savedRenderDistance >= 0) options.renderDistance().set(c.savedRenderDistance);
            if (c.savedParticles >= 0 && c.savedParticles < ParticleStatus.values().length) options.particles().set(ParticleStatus.values()[c.savedParticles]);
            options.entityShadows().set(c.savedEntityShadows);
            c.boostApplied = false;
        }
        LavaVisualClient.save();
    }
}
