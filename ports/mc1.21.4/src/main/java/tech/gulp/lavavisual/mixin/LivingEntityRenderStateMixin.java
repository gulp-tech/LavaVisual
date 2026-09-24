package tech.gulp.lavavisual.mixin;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements TargetState {
    @Unique private boolean lavavisual$target;
    public boolean lavavisual$isTarget() { return lavavisual$target; }
    public void lavavisual$setTarget(boolean target) { lavavisual$target = target; }
}
