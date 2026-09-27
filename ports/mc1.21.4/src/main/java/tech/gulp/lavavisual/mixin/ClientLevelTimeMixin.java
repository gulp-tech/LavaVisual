package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.TimeChanger;

/** Client-side time of day: the sun, moon and sky colours follow the day time reported here. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeMixin {
    @ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
    private long lava$time(long original) { return TimeChanger.apply(original); }
}
