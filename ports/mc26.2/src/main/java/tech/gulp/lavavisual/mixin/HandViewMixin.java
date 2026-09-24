package tech.gulp.lavavisual.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.SwingStyles;

/** First-person rendering only. Does not modify reach, entity dimensions, input, the real cooldown or packets. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandViewMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack mainHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;

    /**
     * No weapon dip after an attack: vanilla lowers the held item towards cooldown³ every tick. With the option on, the
     * height follows the vanilla rule with a full cooldown instead, so only the item-switch animation remains.
     * The attack indicator under the crosshair reads the player's cooldown directly and is untouched.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void lava$noCooldownDip(CallbackInfo ci) {
        if (!LavaVisualClient.config().noCooldownDip) return;
        var player = minecraft.player;
        if (player == null || player.isHandsBusy()) return;
        // Same item still in hand (durability or count may change on a hit): not a switch, so rise like a charged hit.
        if (mainHandItem.getItem() == player.getMainHandItem().getItem()) mainHandHeight = Math.min(1f, oMainHandHeight + 0.4f);
    }

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
