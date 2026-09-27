package tech.gulp.lavavisual.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * The same call shape the newer versions offer for custom geometry, on top of this version's buffer source:
 * geometry is written straight into the buffer of the requested render type.
 */
public final class Submitter {
    private final MultiBufferSource buffers;
    public Submitter(MultiBufferSource buffers) { this.buffers = buffers; }
    public MultiBufferSource buffers() { return buffers; }

    public void submitCustomGeometry(PoseStack pose, RenderType type, BiConsumer<PoseStack.Pose, VertexConsumer> body) {
        body.accept(pose.last(), buffers.getBuffer(type));
    }
    /** Flushes one render type when the source batches (the level buffer source does). */
    public void flush(RenderType type) {
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
    }
}
