package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla caps the frame rate at 60 FPS while any screen is open outside a world (FramerateLimitTracker,
 * OUT_OF_LEVEL_MENU), no matter what Max Framerate is set to, so the main menu ran at 60 FPS while its animations
 * looked like 30. LavaVisual's own menus ask for the player's Max Framerate there, exactly like inside a world.
 * Every vanilla menu keeps the cap, and the AFK and minimised limits still apply: they are other throttle reasons.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Shadow public abstract FramerateLimitTracker.FramerateThrottleReason getThrottleReason();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void lavavisual$fullRateInMenu(CallbackInfoReturnable<Integer> cir) {
        if (!tech.gulp.lavavisual.ui.MenuRate.menu()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null || client.level != null) return;
        if (getThrottleReason() != FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU) return;
        cir.setReturnValue(client.options.framerateLimit().get());
    }
}
