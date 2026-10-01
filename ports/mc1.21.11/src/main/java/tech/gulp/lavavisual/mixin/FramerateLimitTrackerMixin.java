package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla caps the frame rate at 60 while any screen is open outside a world, whatever Max Framerate says, so the
 * main menu ran at 60 FPS and its animations looked half as smooth as the ones inside a world. LavaVisual's own
 * menus ask for the player's Max Framerate instead.
 *
 * The cap is raised after the tracker has decided, and only when it hands back exactly that menu value while a
 * menu is open outside a world: the AFK caps (30 and 10) and a minimised window (10) return other numbers, and a
 * Max Framerate at or below the cap would only be made slower by touching it. The AFK and minimised limits, and
 * every vanilla screen, therefore keep working exactly as before.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    /** The value the tracker returns for a screen outside a world, in every version LavaVisual supports. */
    private static final int MENU_LIMIT = 60;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void lavavisual$fullRateInMenu(CallbackInfoReturnable<Integer> cir) {
        if (!tech.gulp.lavavisual.ui.MenuRate.menu()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null || client.level != null) return;
        int limit = client.options.framerateLimit().get();
        if (limit > MENU_LIMIT && cir.getReturnValueI() == MENU_LIMIT) cir.setReturnValue(limit);
    }
}
