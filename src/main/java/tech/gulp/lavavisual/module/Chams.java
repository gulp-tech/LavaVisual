package tech.gulp.lavavisual.module;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.render.LavaPalette;
import tech.gulp.lavavisual.render.LavaRenderLayers;

/** Queues actual animated model geometry, then draws it after translucent world rendering. */
public final class Chams {
    private final VertexConsumerProvider.Immediate buffers = VertexConsumerProvider.immediate(new BufferBuilder(65536));
    private int submitted;
    private boolean collecting;

    public void beginFrame() {
        submitted = 0;
        collecting = true;
    }

    public void submit(Model model, LivingEntity entity, MatrixStack matrices) {
        if (!collecting || !LavaVisualClient.CONFIG.chams || !LavaVisualClient.isTarget(entity)
                || submitted >= LavaVisualClient.CONFIG.maxEntities) return;
        submitted++;
        int color = LavaPalette.color(0.75f);
        model.render(matrices, buffers.getBuffer(LavaRenderLayers.CHAMS), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, ((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, 0, LavaVisualClient.CONFIG.chamsAlpha);
    }

    public void flush() {
        collecting = false;
        if (submitted > 0) buffers.draw();
        submitted = 0;
    }
}
