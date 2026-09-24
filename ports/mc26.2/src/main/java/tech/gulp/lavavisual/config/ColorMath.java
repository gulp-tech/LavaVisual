package tech.gulp.lavavisual.config;

/** Small HSV helpers without java.awt (not available on every mobile Java launcher). */
public final class ColorMath {
    private ColorMath() { }
    /** h, s, v in 0..1; returns 0xRRGGBB. */
    public static int hsv(double h, double s, double v) {
        h = (h - Math.floor(h)) * 6; s = Math.clamp(s, 0, 1); v = Math.clamp(v, 0, 1);
        int sector = (int) h; double f = h - sector;
        double p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }
    /** Returns {h, s, v} in 0..1 for 0xRRGGBB. */
    public static double[] toHsv(int rgb) {
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6;
        else if (max == g) h = ((b - r) / d + 2) / 6;
        else h = ((r - g) / d + 4) / 6;
        return new double[]{h - Math.floor(h), max == 0 ? 0 : d / max, max};
    }
    public static String hex(int rgb) { return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF); }
    /** Parses "#RRGGBB", "RRGGBB" or "#RGB"; returns -1 when invalid. */
    public static int parse(String text) {
        if (text == null) return -1;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (!t.matches("[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(t, 16);
    }
}
