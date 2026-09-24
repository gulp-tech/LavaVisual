package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import tech.gulp.lavavisual.LavaVisualClient;

/** First-person rendering only. Does not modify reach, entity dimensions, input or packets. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandViewMixin {
    @WrapMethod(method = "submitArmWithItem")
    private void lava$position(AbstractClientPlayer player, float delta, float pitch, InteractionHand hand,
                               float swing, ItemStack item, float equipped, PoseStack pose,
                               SubmitNodeCollector collector, int light, Operation<Void> original) {
        var config = LavaVisualClient.config();
        if (!config.viewModelEnabled) {
            original.call(player, delta, pitch, hand, swing, item, equipped, pose, collector, light);
            return;
        }
        var settings = hand == InteractionHand.MAIN_HAND ? config.mainHand : config.offHand;
        pose.pushPose();
        try {
            pose.translate(settings.x, settings.y, -settings.z);
            pose.scale((float) settings.scale, (float) settings.scale, (float) settings.scale);
            original.call(player, delta, pitch, hand, swing, item, equipped, pose, collector, light);
        } finally { pose.popPose(); }
    }
}
