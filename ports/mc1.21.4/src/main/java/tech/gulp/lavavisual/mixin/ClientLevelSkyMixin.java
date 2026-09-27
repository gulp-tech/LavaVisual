package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.effects.WorldCosmetics;

/** Sky tint: the colour the sky pass reads is mixed with the chosen one. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private int lava$sky(int original) { return WorldCosmetics.tintSky(original); }
}
