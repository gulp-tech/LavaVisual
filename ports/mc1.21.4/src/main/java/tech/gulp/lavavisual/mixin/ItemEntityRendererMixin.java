package tech.gulp.lavavisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.gulp.lavavisual.compat.Submitter;
import tech.gulp.lavavisual.effects.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void lava$capture(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) { ItemPhysics.capture(entity, state); }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void lava$render(ItemEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ItemPhysics.submit(state, pose, new Submitter(buffers), light)) ci.cancel();
    }
}
