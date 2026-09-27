package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tech.gulp.lavavisual.Edition;

@Mixin(Minecraft.class)
public abstract class MinecraftTitleMixin {
    @ModifyReturnValue(method = "createTitle", at = @At("RETURN"))
    private String lava$title(String original) { return Edition.title(original); }
}
