package tech.gulp.lavavisual.mixin;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.gulp.lavavisual.LavaVisualClient;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void lavavisual$renderModel(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices,
                                        int light, int overlay, float red, float green, float blue, float alpha,
                                        LivingEntity entity, float yaw, float tickDelta, MatrixStack originalMatrices,
                                        VertexConsumerProvider providers, int originalLight) {
        model.render(matrices, vertices, light, overlay, red, green, blue, alpha);
        LavaVisualClient.CHAMS.submit(model, entity, matrices);
    }
}
