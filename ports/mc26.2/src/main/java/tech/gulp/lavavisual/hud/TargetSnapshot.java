package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import tech.gulp.lavavisual.LavaVisualClient;

/** Snapshot of the current vanilla crosshair hit; the last visible target lingers for the configured hold time. */
public record TargetSnapshot(String name, float health, float maximum, int armor, double distance,
                             net.minecraft.resources.Identifier skin, LivingEntity entity, long captured) {
    public static TargetSnapshot current;
    private static long tick;
    public static void update(Minecraft client) {
        tick++;
        var config = LavaVisualClient.config();
        if (!config.widgets.get("target").visible || client.player == null || client.level == null || client.gui.screen() != null) {
            current = null; return;
        }
        TargetSnapshot fresh = null;
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && living != client.player && living.isAlive() && !living.isInvisible()
                && !(living instanceof Player player && player.isSpectator())) {
            double distance = client.player.distanceTo(living);
            if (distance <= 6 && client.player.hasLineOfSight(living)) {
                fresh = new TargetSnapshot(living.getName().getString(), living.getHealth(), living.getMaxHealth(),
                        living.getArmorValue(), distance,
                        living instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer ? clientPlayer.getSkin().body().texturePath() : null,
                        living, tick);
            }
        }
        if (fresh != null) current = fresh;
        else if (current == null || tick - current.captured > (long) (config.targetHold * 20)) current = null;
    }
}
