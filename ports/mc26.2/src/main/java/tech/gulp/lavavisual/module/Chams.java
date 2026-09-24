package tech.gulp.lavavisual.module;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.render.LavaRenderLayers;

public final class Chams {
    private static int submitted;
    private Chams() { }
    public static void beginFrame() { submitted = 0; }

    public static <S> void submit(Model<? super S> model, S state, PoseStack matrices, SubmitNodeCollector collector) {
        if (!(state instanceof LivingEntityRenderState living)) return;
        Integer color = living.getData(LavaVisualClient.CHAMS_COLOR);
        Integer limit = living.getData(LavaVisualClient.CHAMS_LIMIT);
        if (color == null || color == 0 || limit == null || submitted >= limit) return;
        submitted++;
        // Deferred submission preserves the model state and animation; no live entity is retained.
        collector.order(999).submitModel(model, state, matrices, LavaRenderLayers.CHAMS,
                0x00F000F0, OverlayTexture.NO_OVERLAY, color, null, 0, null);
    }
}
