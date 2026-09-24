package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import tech.gulp.lavavisual.LavaVisualClient;

/** Optional lightweight preset. Only touches vanilla options while enabled and restores them after. */
public final class PerformanceMode {
    private PerformanceMode() { }
    public static boolean active() { return LavaVisualClient.config().fpsBoost; }
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
            options.renderDistance().set(Math.min(options.renderDistance().get(), 8));
            if (options.particles().get().ordinal() < ParticleStatus.MINIMAL.ordinal()) options.particles().set(ParticleStatus.MINIMAL);
            options.entityShadows().set(false);
        } else if (c.boostApplied) {
            if (c.savedRenderDistance >= 0) options.renderDistance().set(c.savedRenderDistance);
            if (c.savedParticles >= 0 && c.savedParticles < ParticleStatus.values().length) options.particles().set(ParticleStatus.values()[c.savedParticles]);
            options.entityShadows().set(c.savedEntityShadows);
            c.boostApplied = false;
        }
        if (client.levelRenderer != null) client.levelRenderer.allChanged();
        LavaVisualClient.save();
    }
}
