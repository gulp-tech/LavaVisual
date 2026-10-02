package tech.gulp.lavavisual.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.Dummy;
import tech.gulp.lavavisual.effects.Hats;

/**
 * Seated skins. The chair costume is built around a player who sits in it, so the legs of the player wearing it are
 * held forward (thighs on the seat, feet on the footplates) instead of standing. Everything else about the pose is
 * vanilla; the arms keep their own swing, because pushing the wheels is what the arms of that skin do.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerSkinPoseMixin {
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void lavavisual$seatedSkin(AvatarRenderState state, CallbackInfo ci) {
        var c = LavaVisualClient.config();
        if (!c.costumeEnabled) return;
        float seat = Hats.costumeSeat(c.costumeType);
        if (seat <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (state.id != mc.player.getId() && !Dummy.is(state.id))) return;
        float angle = (float) Math.toRadians(-seat);
        this.rightLeg.xRot = angle;
        this.leftLeg.xRot = angle;
        this.rightLeg.yRot = 0;
        this.leftLeg.yRot = 0;
        this.rightLeg.zRot = 0;
        this.leftLeg.zRot = 0;
    }
}
