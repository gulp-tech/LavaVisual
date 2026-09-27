package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import tech.gulp.lavavisual.effects.CameraControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.hud.ClickCounter;

/**
 * Always-on fix for worn scroll wheels (no menu entry by design):
 * - chatter: opposite ticks during an active gesture (300 ms since the last accepted tick) are dropped
 *   unless they repeat three times in quick succession, which is a deliberate reversal;
 * - bounce: in-game, a second full notch in the same direction within 20 ms is one physical notch;
 * - after a pause every tick passes immediately, so single-notch hotbar switching has no delay.
 * Also counts real mouse presses for the CPS display.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Unique private static long lava$accepted, lava$event;
    @Unique private static double lava$direction;
    @Unique private static int lava$against;
    @Unique private boolean lava$left, lava$right;

    @WrapMethod(method = "onScroll")
    private void lava$wheel(long handle, double x, double y, Operation<Void> original) {
        if (y != 0 && Minecraft.getInstance().gui.screen() == null && CameraControl.scroll(y)) return;
        if (y != 0 && !lava$accept(y)) return;
        original.call(handle, x, y);
    }

    @Unique
    private static boolean lava$accept(double y) {
        long now = System.nanoTime();
        double direction = Math.signum(y);
        long sinceAccepted = now - lava$accepted, sinceEvent = now - lava$event;
        lava$event = now;
        if (lava$direction == 0 || sinceAccepted > 300_000_000L) {
            lava$direction = direction; lava$against = 0; lava$accepted = now;
            return true;
        }
        if (direction == lava$direction) {
            lava$against = 0;
            if (Math.abs(y) >= 1 && sinceAccepted < 20_000_000L && Minecraft.getInstance().gui.screen() == null) return false;
            lava$accepted = now;
            return true;
        }
        if (sinceEvent > 120_000_000L) lava$against = 0;
        if (++lava$against >= 3) {
            lava$direction = direction; lava$against = 0; lava$accepted = now;
            return true;
        }
        return false;
    }

    /** FreeLook takes the mouse movement for the camera; zoom slows the mouse down. Client-side view only. */
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 0)
    private void lava$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (CameraControl.turn(dx, dy)) return;
        double k = CameraControl.sensitivity();
        original.call(player, dx * k, dy * k);
    }

    @Inject(method = "onButton", at = @At("RETURN"))
    private void lava$clicks(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        MouseHandler self = (MouseHandler) (Object) this;
        boolean left = self.isLeftPressed(), right = self.isRightPressed();
        if (left && !lava$left) ClickCounter.press(true);
        if (right && !lava$right) ClickCounter.press(false);
        lava$left = left; lava$right = right;
    }
}
