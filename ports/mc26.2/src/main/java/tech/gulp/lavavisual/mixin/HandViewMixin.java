package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.SwingStyles;

/** First-person rendering only. Does not modify reach, entity dimensions, input, cooldown or packets. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandViewMixin {
    @WrapMethod(method = "submitArmWithItem")
    private void lava$position(AbstractClientPlayer player, float delta, float pitch, InteractionHand hand,
                               float swing, ItemStack item, float equipped, PoseStack pose,
                               SubmitNodeCollector collector, int light, Operation<Void> original) {
        var config = LavaVisualClient.config();
        boolean styled = SwingStyles.active();
        if (!config.viewModelEnabled && !styled) {
            original.call(player, delta, pitch, hand, swing, item, equipped, pose, collector, light);
            return;
        }
        pose.pushPose();
        try {
            if (config.viewModelEnabled) {
                var settings = hand == InteractionHand.MAIN_HAND ? config.mainHand : config.offHand;
                pose.translate(settings.x, settings.y, -settings.z);
                pose.scale((float) settings.scale, (float) settings.scale, (float) settings.scale);
            }
            float shownSwing = swing;
            if (styled) {
                HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                boolean right = arm == HumanoidArm.RIGHT;
                float anchorX = right ? .56f : -.56f, anchorY = -.52f - equipped * .6f;
                pose.translate(anchorX, anchorY, -.72f);
                SwingStyles.apply(pose, hand, right);
                pose.translate(-anchorX, -anchorY, .72f);
                shownSwing = 0;
            }
            original.call(player, delta, pitch, hand, shownSwing, item, equipped, pose, collector, light);
        } finally { pose.popPose(); }
    }
}
