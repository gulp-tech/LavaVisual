package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;

/** First-person rendering only. Does not modify reach, entity dimensions, input or packets. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandViewMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void lava$position(AbstractClientPlayer player, float delta, float pitch, InteractionHand hand,
                               float swing, ItemStack item, float equipped, PoseStack pose,
                               SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.pushPose();
        var config = LavaVisualClient.config();
        if (!config.viewModelEnabled) return;
        var settings = hand == InteractionHand.MAIN_HAND ? config.mainHand : config.offHand;
        pose.translate(settings.x, settings.y, -settings.z);
        pose.scale((float) settings.scale, (float) settings.scale, (float) settings.scale);
    }
    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void lava$restore(AbstractClientPlayer player, float delta, float pitch, InteractionHand hand,
                              float swing, ItemStack item, float equipped, PoseStack pose,
                              SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.popPose();
    }
}
