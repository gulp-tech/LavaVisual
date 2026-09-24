package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Options;
import net.minecraft.server.level.ClientInformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.Badge;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @ModifyReturnValue(method = "buildPlayerInformation", at = @At("RETURN"))
    private ClientInformation lava$badge(ClientInformation original) { return Badge.mark(original); }
}
