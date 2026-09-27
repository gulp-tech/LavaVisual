package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.effects.WorldCosmetics;

/** Sky tint is applied after all extraction and right before the sky pass reads it. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Inject(method = "render", at = @At("HEAD"))
    private void lava$sky(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState,
                          Matrix4fc modelView, GpuBufferSlice terrainFog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        WorldCosmetics.tintSky(levelRenderState);
    }
}
