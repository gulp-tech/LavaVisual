package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import tech.gulp.lavavisual.compat.Submitter;

/**
 * Hats and wings as a layer of the player model, like vanilla helmets: they are drawn together with the player
 * in the same pass and read the final head / body transforms of the model (turns, pitch, crouch, attack twist,
 * riding and other mods' animations), so a hat cannot lag behind or run ahead of the head.
 */
public final class CosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    public CosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        WorldCosmetics.submitLayer(getParentModel(), pose, new Submitter(buffers), state);
    }
}
