package tech.gulp.lavavisual.module;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import tech.gulp.lavavisual.render.LavaPalette;
import tech.gulp.lavavisual.render.RenderUtils;

public final class Tracers {
    public void render(MatrixStack matrices, VertexConsumer out, Box box, Vector3f start, float distanceRatio) {
        float alpha = 0.9f - 0.55f * distanceRatio;
        RenderUtils.line(matrices, out, start.x, start.y, start.z,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                LavaPalette.color(1), LavaPalette.color(1 - distanceRatio), alpha);
    }
}
