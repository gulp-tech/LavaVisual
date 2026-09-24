package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Always-on fix for a chattering wheel: a single opposite tick within 150 ms of steady
    scrolling is dropped; a real reversal (two opposite ticks or a pause) passes through. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Unique private static long lava$last, lava$pending;
    @Unique private static double lava$direction;
    @WrapMethod(method = "onScroll")
    private void lava$wheel(long handle, double x, double y, Operation<Void> original) {
        if (y != 0) {
            long now = System.currentTimeMillis();
            double direction = Math.signum(y);
            if (lava$direction != 0 && direction != lava$direction && now - lava$last < 150
                    && (lava$pending == 0 || now - lava$pending > 150)) {
                lava$pending = now;
                return;
            }
            lava$pending = 0; lava$direction = direction; lava$last = now;
        }
        original.call(handle, x, y);
    }
}
