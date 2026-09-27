package tech.gulp.lavavisual.app;

/** The accessory palette and how it is painted into a 64x64 skin, identical to the Geyser extension. */
public final class SkinMath {
    public static final int SIZE = 64, CELLS = 16;
    /** Which accessory owns each palette cell (1 glasses, 2 headphones, 4 scarf). */
    private static final int[] OWNER = {1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    private static final int LENS = 0x1D1E24, WHITE = 0xF2F4F8, CUSHION = 0x16161C, BAND = 0x282A32;

    private SkinMath() { }

    /** a&lt;mask&gt;[r][s]: mask 1 glasses, 2 headphones, 4 scarf; r = rainbow scarf; s = slim arms. */
    public static String geometryName(int mask, boolean rainbow, boolean slim) {
        return "a" + (mask & 7) + (rainbow && (mask & 4) != 0 ? "r" : "") + (slim ? "s" : "");
    }

    public static int[] palette(int code) {
        boolean rainbow = code == 0;
        int main = rainbow ? Colors.hsv(0.03, 0.72, 1) : Colors.code(code);
        int light = rainbow ? Colors.hsv(0.19, 0.72, 1) : Colors.companion(main);
        int[] cells = new int[CELLS];
        cells[0] = main; cells[1] = LENS; cells[2] = WHITE;
        cells[3] = BAND; cells[4] = main; cells[5] = CUSHION; cells[6] = light;
        cells[7] = main; cells[8] = light; cells[9] = Colors.scale(main, 0.62);
        for (int k = 0; k < 6; k++) cells[10 + k] = rainbow ? Colors.hsv(k / 6.0, 0.72, 1) : (k % 2 == 0 ? main : light);
        return cells;
    }

    /** The accessory a palette cell belongs to. */
    public static int owner(int cell) { return OWNER[cell]; }

    /**
     * Paints the palette into the top-left 8x8 corner of an ARGB pixel array of a 64x64 skin, which no player bone
     * uses. Returns the same array. Throws when the array is not a 64x64 skin.
     */
    public static int[] paint(int[] pixels, int colorCode, int mask) {
        if (pixels == null || pixels.length != SIZE * SIZE) throw new IllegalArgumentException("skin must be 64x64");
        int[] colors = palette(colorCode);
        for (int i = 0; i < colors.length; i++) {
            int x0 = (i % 4) * 2, y0 = (i / 4) * 2;
            int pixel = (mask & OWNER[i]) != 0 ? Colors.opaque(colors[i]) : colors[i] & 0xFFFFFF;
            for (int y = y0; y < y0 + 2; y++)
                for (int x = x0; x < x0 + 2; x++) pixels[y * SIZE + x] = pixel;
        }
        return pixels;
    }
}
