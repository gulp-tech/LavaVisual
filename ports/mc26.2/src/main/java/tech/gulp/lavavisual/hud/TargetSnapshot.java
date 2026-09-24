package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import tech.gulp.lavavisual.LavaVisualClient;

/** A snapshot of the current vanilla crosshair hit, never a remembered or searched-for target. */
public record TargetSnapshot(String name, float health, float maximum, int armor, double distance) {
    public static TargetSnapshot current;
    public static void update(Minecraft client) {
        current = null;
        var config = LavaVisualClient.config();
        if (!config.widgets.get("target").visible || client.player == null || client.level == null
                || client.gui.screen() != null) return;
        if (!(client.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity living)) return;
        if (living == client.player || !living.isAlive() || living.isInvisible()
                || living instanceof Player player && player.isSpectator()) return;
        double distance = client.player.distanceTo(living);
        if (distance > 6 || !client.player.hasLineOfSight(living)) return;
        current = new TargetSnapshot(living.getName().getString(), living.getHealth(), living.getMaxHealth(), living.getArmorValue(), distance);
    }
}
