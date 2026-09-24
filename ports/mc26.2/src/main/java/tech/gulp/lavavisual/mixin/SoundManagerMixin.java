package tech.gulp.lavavisual.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import tech.gulp.lavavisual.effects.CustomAudio;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance lava$audio(SoundInstance original) { return CustomAudio.replace(original); }
}
