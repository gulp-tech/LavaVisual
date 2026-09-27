package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.CameraControl;

/** Zoom: the field of view of the frame is scaled, nothing else changes. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float lava$zoom(float fov) { return CameraControl.fov(fov); }
}
