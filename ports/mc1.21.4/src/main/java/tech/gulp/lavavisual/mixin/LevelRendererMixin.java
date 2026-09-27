package tech.gulp.lavavisual.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.effects.WorldCosmetics;

/** Sky tint is applied right before the sky pass reads the frame state. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void lava$sky(CallbackInfo ci) { WorldCosmetics.tintSky(levelRenderState); }
}
