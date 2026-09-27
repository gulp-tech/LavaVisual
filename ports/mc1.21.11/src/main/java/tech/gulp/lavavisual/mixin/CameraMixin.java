package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.CameraControl;

/** Camera only: zoom scales the field of view; FreeLook replaces the camera angles, never the player's rotation. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float lava$zoom(float fov) { return CameraControl.fov(fov); }

    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
    private void lava$freeLook(Camera camera, float yaw, float pitch, Operation<Void> original) {
        if (CameraControl.freeLook()) {
            CameraControl.hooked = true;
            original.call(camera, CameraControl.yaw(), CameraControl.pitch());
        } else original.call(camera, yaw, pitch);
    }
}
