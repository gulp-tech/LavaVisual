package tech.gulp.lavavisual.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RenderUtils {
    private RenderUtils() { }

    public static Box interpolatedBox(Entity entity, float delta, Vec3d camera) {
        return entity.getBoundingBox().offset(
                MathHelper.lerp(delta, entity.lastRenderX, entity.getX()) - entity.getX() - camera.x,
                MathHelper.lerp(delta, entity.lastRenderY, entity.getY()) - entity.getY() - camera.y,
                MathHelper.lerp(delta, entity.lastRenderZ, entity.getZ()) - entity.getZ() - camera.z);
    }

    public static void line(MatrixStack matrices, VertexConsumer out,
                            double x1, double y1, double z1, double x2, double y2, double z2,
                            int color1, int color2, float alpha) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-6f) return;
        dx /= length; dy /= length; dz /= length;
        vertex(matrices, out, x1, y1, z1, color1, alpha, dx, dy, dz);
        vertex(matrices, out, x2, y2, z2, color2, alpha, dx, dy, dz);
    }

    private static void vertex(MatrixStack matrices, VertexConsumer out, double x, double y, double z,
                               int color, float alpha, float nx, float ny, float nz) {
        out.vertex(matrices.peek().getPositionMatrix(), (float) x, (float) y, (float) z)
                .color(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, alpha)
                .normal(matrices.peek(), nx, ny, nz);
    }

    public static void gradientVertical(MatrixStack matrices, VertexConsumer out,
                                        double x, double bottom, double top, double z, float alpha) {
        double middle = (bottom + top) * 0.5;
        line(matrices, out, x, bottom, z, x, middle, z, LavaPalette.color(0), LavaPalette.color(0.5f), alpha);
        line(matrices, out, x, middle, z, x, top, z, LavaPalette.color(0.5f), LavaPalette.color(1), alpha);
    }
}
