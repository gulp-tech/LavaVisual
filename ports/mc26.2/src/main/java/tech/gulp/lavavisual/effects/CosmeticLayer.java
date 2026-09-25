package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Hats and wings as a layer of the player model, like vanilla helmets: they are submitted together with the player
 * in the same pass and read the final head / body transforms of the model (turns, pitch, crouch, attack twist,
 * riding and other mods' animations), so a hat cannot lag behind or run ahead of the head.
 */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        WorldCosmetics.submitLayer(getParentModel(), pose, collector, state);
    }
}
