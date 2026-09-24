package tech.gulp.lavavisual.mixin;

import tech.gulp.lavavisual.render.TargetState;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.LavaVisualClient;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void lavavisual$extract(LivingEntity entity, LivingEntityRenderState state, float delta, CallbackInfo ci) {
        ((TargetState) state).lavavisual$setTarget(LavaVisualClient.isTarget(entity));
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void lavavisual$renderModel(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices,
                                        int light, int overlay, int color, LivingEntityRenderState state,
                                        MatrixStack originalMatrices, VertexConsumerProvider providers, int originalLight) {
        model.render(matrices, vertices, light, overlay, color);
        if (((TargetState) state).lavavisual$isTarget()) LavaVisualClient.CHAMS.submit(model, matrices);
    }
}
