package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;

/** Low Fire: only the first-person overlay changes; burning and damage stay vanilla.
    Anchor constants adapted from PulseVisual (MIT, Copyright (c) 2026 PulseVisual contributors). */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void lava$fireStart(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        float size = (float) LavaVisualClient.config().fireHeight;
        if (size >= 0.999f) return;
        if (size <= 0.001f) { ci.cancel(); return; }
        pose.pushPose();
        pose.translate(0, -.35f * (1 - size), 0);
        pose.scale(1, size, 1);
    }
    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void lava$fireEnd(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        float size = (float) LavaVisualClient.config().fireHeight;
        if (size < 0.999f && size > 0.001f) pose.popPose();
    }
}
