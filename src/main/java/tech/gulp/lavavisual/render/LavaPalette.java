package tech.gulp.lavavisual.render;

public final class LavaPalette {
    private LavaPalette() { }

    /** Bottom to top: burgundy, red, orange. */
    public static int color(float height) {
        float t = Math.max(0, Math.min(1, height));
        if (t < 0.5f) return mix(0x8B0000, 0xFF0000, t * 2);
        return mix(0xFF0000, 0xFF6B00, (t - 0.5f) * 2);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        return (r << 16) | (g << 8);
    }
}
