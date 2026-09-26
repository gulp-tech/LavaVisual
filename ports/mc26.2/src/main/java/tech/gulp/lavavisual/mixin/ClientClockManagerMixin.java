package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.TimeChanger;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long lava$time(long original, Holder<WorldClock> clock) { return TimeChanger.apply(clock, original); }
}
