package tech.gulp.lavavisual.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinGeometry;

/**
 * Glasses, headphones and scarf as Bedrock skin geometry (written by bedrock/tools/make_bedrock.py).
 *
 * Each geometry is the standard player plus one bone per accessory. Accessory cubes take their colour from 2x2 texel
 * cells of a small palette painted into the top-left 8x8 corner of the skin, which no player bone uses, so the
 * player's skin is otherwise unchanged and one geometry serves every colour.
 */
final class Accessories {
    static final int SIZE = 64, CELLS = 16;
    /** Which palette cells belong to which accessory (1 glasses, 2 headphones, 4 scarf), in make_bedrock.py's order. */
    private static final int[] OWNER = {1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    private static final int LENS = 0x1D1E24, WHITE = 0xF2F4F8, CUSHION = 0x16161C, BAND = 0x282A32;
    private final Map<String, String> geometries = new HashMap<>();

    static Accessories load() {
        Accessories accessories = new Accessories();
        for (int mask = 1; mask <= 7; mask++)
            for (int rainbow = 0; rainbow < ((mask & 4) != 0 ? 2 : 1); rainbow++)
                for (int slim = 0; slim < 2; slim++) {
                    String name = name(mask, rainbow == 1, slim == 1);
                    try (InputStream in = Accessories.class.getResourceAsStream("/lavavisual/geometry/" + name + ".json")) {
                        if (in != null) accessories.geometries.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException ignored) { }
                }
        return accessories;
    }

    int count() { return geometries.size(); }

    /** a&lt;mask&gt;[r][s]: accessory mask, r = rainbow scarf, s = slim arms (the names make_bedrock.py writes). */
    static String name(int mask, boolean rainbow, boolean slim) {
        return "a" + mask + (rainbow && (mask & 4) != 0 ? "r" : "") + (slim ? "s" : "");
    }

    /** Geometry for these accessories, or null when there is none. */
    SkinGeometry geometry(int mask, boolean rainbow, boolean slim) {
        String name = name(mask & 7, rainbow, slim), data = geometries.get(name);
        if (data == null) return null;
        return new SkinGeometry("{\"geometry\":{\"default\":\"geometry.lavavisual." + name + "\"}}", data);
    }

    /**
     * The skin with the accessory palette painted in; null when the skin is not a 64x64 one. Cells of accessories the
     * player does not wear are left fully transparent, so those cubes disappear in the shared model of the server pack.
     */
    static Skin paint(Skin skin, int mask, int colorCode) {
        byte[] source = skin.skinData();
        if (source == null || source.length != SIZE * SIZE * 4) return null;
        byte[] data = source.clone();
        int[] colors = palette(colorCode);
        for (int i = 0; i < colors.length; i++) {
            int x0 = (i % 4) * 2, y0 = (i / 4) * 2;
            int alpha = (mask & OWNER[i]) != 0 ? 255 : 0;
            for (int y = y0; y < y0 + 2; y++)
                for (int x = x0; x < x0 + 2; x++) {
                    int at = (y * SIZE + x) * 4;
                    data[at] = (byte) (colors[i] >> 16);
                    data[at + 1] = (byte) (colors[i] >> 8);
                    data[at + 2] = (byte) colors[i];
                    data[at + 3] = (byte) alpha;
                }
        }
        // A new id for every look, so Bedrock clients never reuse a cached texture of another one.
        return new Skin(skin.textureUrl() + "#lavavisual-" + (mask & 7) + "-" + colorCode, data);
    }

    /**
     * Palette cells, in make_bedrock.py's order: the glasses (frame, lens, glint), the headphones (band, strip,
     * cushion, rim), the scarf (main, second tone, knot) and the six stripe cells, which carry the rainbow hues for a
     * rainbow scarf and the two ordinary tones otherwise. No cell is shared between two accessories, so each one can
     * be switched off by painting its cells transparent.
     */
    static int[] palette(int code) {
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
}
