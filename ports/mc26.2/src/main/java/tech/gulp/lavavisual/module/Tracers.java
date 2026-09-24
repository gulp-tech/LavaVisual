package tech.gulp.lavavisual.module;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import tech.gulp.lavavisual.render.LavaPalette;
import tech.gulp.lavavisual.render.RenderUtils;

public final class Tracers {
    public void render(PoseStack matrices, VertexConsumer out, AABB box, Vector3f start, float distanceRatio) {
        float alpha = 0.9f - 0.55f * distanceRatio;
        RenderUtils.line(matrices, out, start.x, start.y, start.z,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                LavaPalette.color(1), LavaPalette.color(1 - distanceRatio), alpha);
    }
}
