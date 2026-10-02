package tech.gulp.lavavisual.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.Dummy;
import tech.gulp.lavavisual.effects.Hats;

/**
 * Seated skins. The wheelchair costume is built around a player who sits in it, so the legs of the player wearing it
 * are held forward (thighs on the seat, feet on the footplates) instead of standing. Everything else about the pose is
 * vanilla; the arms keep their own swing, because pushing the wheels is what the arms of that skin do.
 *
 * The legs live in HumanoidModel, the superclass of the player model, and a shadow field cannot be remapped for an
 * inherited member without a reference map, so the model is reached through the mixin instance instead.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerSkinPoseMixin {
    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void lavavisual$seatedSkin(PlayerRenderState state, CallbackInfo ci) {
        var c = LavaVisualClient.config();
        if (!c.costumeEnabled) return;
        float seat = Hats.costumeSeat(c.costumeType);
        if (seat <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (state.id != mc.player.getId() && !Dummy.is(state.id))) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float angle = (float) Math.toRadians(-seat);
        model.rightLeg.xRot = angle;
        model.leftLeg.xRot = angle;
        model.rightLeg.yRot = 0;
        model.leftLeg.yRot = 0;
        model.rightLeg.zRot = 0;
        model.leftLeg.zRot = 0;
    }
}
