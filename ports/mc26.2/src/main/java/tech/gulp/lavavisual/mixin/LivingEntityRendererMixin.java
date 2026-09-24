package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.module.Chams;
import tech.gulp.lavavisual.render.LavaPalette;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void lavavisual$extract(LivingEntity entity, LivingEntityRenderState state, float delta, CallbackInfo ci) {
        var config = LavaVisualClient.CONFIG;
        int color = config.chams && LavaVisualClient.isTarget(entity)
                ? (Math.round(config.chamsAlpha * 255) << 24) | LavaPalette.color(0.75f) : 0;
        state.setData(LavaVisualClient.CHAMS_COLOR, color);
        state.setData(LavaVisualClient.CHAMS_LIMIT, config.maxEntities);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private <S> void lavavisual$submit(SubmitNodeCollector collector, Model<? super S> model, S state,
                                      PoseStack matrices, RenderType layer, int light, int overlay, int color,
                                      TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        collector.submitModel(model, state, matrices, layer, light, overlay, color, sprite, outline, crumbling);
        Chams.submit(model, state, matrices, collector);
    }
}
