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
    /** Visual-only aim range: the vanilla crosshair reaches entities only within the attack range (3 blocks). */
    private static final double RANGE = 8;
    /** Closest living entity whose (slightly padded) box the view ray crosses; walls are rejected by hasLineOfSight. */
    private static LivingEntity aimed(Minecraft client, double range) {
        var player = client.player;
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1f), look = player.getViewVector(1f), end = eye.add(look.scale(range));
        var area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var entity : client.level.getEntities(player, area, e -> e instanceof LivingEntity && e.isAlive())) {
            var crossing = entity.getBoundingBox().inflate(0.2).clip(eye, end);
            if (crossing.isEmpty()) continue;
            double distance = eye.distanceToSqr(crossing.get());
            if (distance < bestDistance) { bestDistance = distance; best = (LivingEntity) entity; }
        }
        return best;
    }
    private static long tick;
    public static void update(Minecraft client) {
        tick++;
        var config = LavaVisualClient.config();
        // Needed by the Target HUD and by Target ESP; either one keeps the snapshot alive.
        if (!(config.widgets.get("target").visible || config.espEnabled) || client.player == null || client.level == null || client.gui.screen() != null) {
            current = null; return;
        }
        TargetSnapshot fresh = null;
        LivingEntity living = client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity crosshair ? crosshair : aimed(client, RANGE);
        if (living != null
                && living != client.player && living.isAlive() && !living.isInvisible()
                && !(living instanceof Player player && player.isSpectator())) {
            double distance = client.player.distanceTo(living);
            if (distance <= RANGE && client.player.hasLineOfSight(living)) {
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
