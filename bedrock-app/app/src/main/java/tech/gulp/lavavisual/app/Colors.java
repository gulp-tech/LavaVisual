package tech.gulp.lavavisual.app;

/** Colour helpers, the same maths as the Java mod and the Geyser extension. */
public final class Colors {
    private Colors() { }

    /** RGB of a shared colour code: 1..27 hues, 28..31 white, light grey, dark grey, black (0 = rainbow). */
    public static int code(int code) {
        switch (code) {
            case 28: return 0xF2F2F2;
            case 29: return 0xA8ADB6;
            case 30: return 0x555A63;
            case 31: return 0x1A1B20;
            default: return hsv((Math.max(1, code) - 1) / 27.0, 0.75, 1);
        }
    }

    public static int hsv(double h, double s, double v) {
        h = (h - Math.floor(h)) * 6;
        s = clamp(s); v = clamp(v);
        int sector = (int) h;
        double f = h - sector, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (sector % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }

    static double clamp(double value) { return value < 0 ? 0 : value > 1 ? 1 : value; }

    public static double[] toHsv(int rgb) {
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6;
        else if (max == g) h = ((b - r) / d + 2) / 6;
        else h = ((r - g) / d + 4) / 6;
        return new double[]{h - Math.floor(h), max == 0 ? 0 : d / max, max};
    }

    /** Second tone of a colour: a slightly lighter neighbour hue; greys stay grey. */
    public static int companion(int rgb) {
        double[] hsv = toHsv(rgb & 0xFFFFFF);
        if (hsv[1] < 0.08) return rgb & 0xFFFFFF;
        return hsv(hsv[0] + 0.09, Math.min(1, hsv[1] * 0.92), Math.min(1, hsv[2] * 0.9 + 0.1));
    }

    public static int scale(int rgb, double k) {
        int r = (int) Math.round((rgb >> 16 & 255) * k), g = (int) Math.round((rgb >> 8 & 255) * k), b = (int) Math.round((rgb & 255) * k);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    public static int opaque(int rgb) { return 0xFF000000 | rgb; }
}
