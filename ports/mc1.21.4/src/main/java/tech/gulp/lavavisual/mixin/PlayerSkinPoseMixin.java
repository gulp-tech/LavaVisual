package tech.gulp.lavavisual.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.Dummy;
import tech.gulp.lavavisual.effects.Hats;

/**
 * The pose of a worn skin. A skin knows what it replaces: the crewmate covers both arms and both legs with its own
 * capsule (the mod draws them invisible, so no player skin shows through), while the wheelchair carries its rider's
 * legs on its footplates (they must not walk) and holds the arms forward from the rest pose, where the gloved hands
 * then sit exactly on the push rings.
 *
 * The limbs live in HumanoidModel, the superclass of the player model, and a shadow field cannot be remapped for an
 * inherited member without a reference map, so the model is reached through the mixin instance instead.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerSkinPoseMixin {
    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void lavavisual$skinPose(PlayerRenderState state, CallbackInfo ci) {
        var c = LavaVisualClient.config();
        if (!c.costumeEnabled || Hats.costume(c.costumeType) == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (state.id != mc.player.getId() && !Dummy.is(state.id))) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        int hide = Hats.costumeHide(c.costumeType);
        int arms = Hats.costumeArms(c.costumeType);
        if (arms != 0) {
            // The arms keep their own swing (that swing is the push) and only lean a little forward, so the hands of
            // the skin land on the push rings; the player's own arms are hidden underneath when the skin covers them.
            float angle = (float) Math.toRadians(-arms);
            model.rightArm.xRot += angle;
            model.leftArm.xRot += angle;
        }
        if ((hide & 1) != 0) {
            model.rightArm.visible = false;
            model.leftArm.visible = false;
        }
        if ((hide & 4) != 0) {
            // A skin that swallows the whole head (the crewmate's capsule) hides the player's head and the vanilla hat
            // layer too, so a helmet never pokes out of the suit; LavaVisual's own hat is drawn on the suit instead.
            model.head.visible = false;
            model.hat.visible = false;
        }
        if ((hide & 2) != 0) {
            model.rightLeg.visible = false;
            model.leftLeg.visible = false;
            return;
        }
        float seat = Hats.costumeSeat(c.costumeType);
        if (seat <= 0) return;
        float angle = (float) Math.toRadians(-seat);
        model.rightLeg.xRot = angle;
        model.leftLeg.xRot = angle;
        model.rightLeg.yRot = 0;
        model.leftLeg.yRot = 0;
        model.rightLeg.zRot = 0;
        model.leftLeg.zRot = 0;
    }
}
