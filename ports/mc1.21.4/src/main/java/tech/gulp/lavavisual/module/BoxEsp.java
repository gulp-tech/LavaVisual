package tech.gulp.lavavisual.module;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import tech.gulp.lavavisual.render.LavaPalette;
import tech.gulp.lavavisual.render.RenderUtils;

public final class BoxEsp {
    public void render3d(MatrixStack matrices, VertexConsumer out, Box b, float alpha) {
        RenderUtils.gradientVertical(matrices, out, b.minX, b.minY, b.maxY, b.minZ, alpha);
        RenderUtils.gradientVertical(matrices, out, b.maxX, b.minY, b.maxY, b.minZ, alpha);
        RenderUtils.gradientVertical(matrices, out, b.maxX, b.minY, b.maxY, b.maxZ, alpha);
        RenderUtils.gradientVertical(matrices, out, b.minX, b.minY, b.maxY, b.maxZ, alpha);
        ring(matrices, out, b, b.minY, LavaPalette.color(0), alpha);
        ring(matrices, out, b, b.maxY, LavaPalette.color(1), alpha);
    }

    private void ring(MatrixStack m, VertexConsumer v, Box b, double y, int color, float alpha) {
        RenderUtils.line(m, v, b.minX, y, b.minZ, b.maxX, y, b.minZ, color, color, alpha);
        RenderUtils.line(m, v, b.maxX, y, b.minZ, b.maxX, y, b.maxZ, color, color, alpha);
        RenderUtils.line(m, v, b.maxX, y, b.maxZ, b.minX, y, b.maxZ, color, color, alpha);
        RenderUtils.line(m, v, b.minX, y, b.maxZ, b.minX, y, b.minZ, color, color, alpha);
    }

    /** Upright camera-facing 2D billboard; no fragile screen-space projection. */
    public void render2d(MatrixStack m, VertexConsumer v, Box b, float yaw, float alpha) {
        double angle = Math.toRadians(yaw);
        double radius = Math.max(b.getLengthX(), b.getLengthZ()) * 0.5;
        double dx = Math.cos(angle) * radius, dz = Math.sin(angle) * radius;
        double x = (b.minX + b.maxX) * 0.5, z = (b.minZ + b.maxZ) * 0.5;
        RenderUtils.gradientVertical(m, v, x - dx, b.minY, b.maxY, z - dz, alpha);
        RenderUtils.gradientVertical(m, v, x + dx, b.minY, b.maxY, z + dz, alpha);
        RenderUtils.line(m, v, x - dx, b.minY, z - dz, x + dx, b.minY, z + dz,
                LavaPalette.color(0), LavaPalette.color(0), alpha);
        RenderUtils.line(m, v, x - dx, b.maxY, z - dz, x + dx, b.maxY, z + dz,
                LavaPalette.color(1), LavaPalette.color(1), alpha);
    }
}
