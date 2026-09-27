package tech.gulp.lavavisual.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import tech.gulp.lavavisual.effects.ItemPhysics;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysics.Extra {
    @Unique private int lava$entityId;
    @Unique private boolean lava$onGround, lava$inWater;

    @Override public void lava$physics(int id, boolean ground, boolean water) { lava$entityId = id; lava$onGround = ground; lava$inWater = water; }
    @Override public int lava$id() { return lava$entityId; }
    @Override public boolean lava$ground() { return lava$onGround; }
    @Override public boolean lava$water() { return lava$inWater; }
}
