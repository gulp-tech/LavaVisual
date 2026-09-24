package tech.gulp.lavavisual.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class RenderUtils {
    private RenderUtils() { }
    public static void line(PoseStack matrices, VertexConsumer out,
                            double x1, double y1, double z1, double x2, double y2, double z2,
                            int color1, int color2, float alpha) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-6f) return;
        dx /= length; dy /= length; dz /= length;
        vertex(matrices, out, x1, y1, z1, color1, alpha, dx, dy, dz);
        vertex(matrices, out, x2, y2, z2, color2, alpha, dx, dy, dz);
    }
    private static void vertex(PoseStack matrices, VertexConsumer out, double x, double y, double z,
                               int color, float alpha, float nx, float ny, float nz) {
        out.addVertex(matrices.last(), (float) x, (float) y, (float) z)
                .setColor((Math.round(alpha * 255) << 24) | color)
                .setNormal(matrices.last(), nx, ny, nz).setLineWidth(2.0f);
    }
    public static void gradientVertical(PoseStack matrices, VertexConsumer out,
                                        double x, double bottom, double top, double z, float alpha) {
        double middle = (bottom + top) * 0.5;
        line(matrices, out, x, bottom, z, x, middle, z, LavaPalette.color(0), LavaPalette.color(0.5f), alpha);
        line(matrices, out, x, middle, z, x, top, z, LavaPalette.color(0.5f), LavaPalette.color(1), alpha);
    }
}
