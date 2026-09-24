package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapmethod.Operation;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;

/** Always-on correction for faulty scroll wheels: a lone opposite tick right after a
    steady direction streak is dropped instead of scrolling the wrong way. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    private static long lavaLastTime;
    private static double lavaLastY;
    private static int lavaStreak;
    @WrapMethod(method = "onScroll")
    private void lava$wheel(long handle, double x, double y, Operation<Void> operation) {
        long now = System.currentTimeMillis();
        if (y != 0) {
            if (lavaLastY != 0 && Math.signum(y) != Math.signum(lavaLastY)) {
                if (now - lavaLastTime < 90 && lavaStreak >= 2) {
                    lavaStreak = 0;
                    return;
                }
                lavaStreak = 1;
            } else {
                lavaStreak++;
            }
            lavaLastTime = now;
            lavaLastY = y;
        }
        operation.call(handle, x, y);
    }
}
