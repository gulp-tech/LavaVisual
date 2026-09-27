package tech.gulp.lavavisual.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Set;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.junit.jupiter.api.Test;

class AccessoriesTest {
    private static final String[] PLAYER_BONES = {"root", "waist", "body", "jacket", "cape", "head", "hat", "leftArm", "leftSleeve",
        "leftItem", "rightArm", "rightSleeve", "rightItem", "leftLeg", "leftPants", "rightLeg", "rightPants"};

    @Test
    void everyCombinationHasAGeometry() {
        Accessories accessories = Accessories.load();
        assertEquals(22, accessories.count());
        for (int mask = 1; mask <= 7; mask++)
            for (boolean rainbow : new boolean[]{false, true})
                for (boolean slim : new boolean[]{false, true}) {
                    SkinGeometry geometry = accessories.geometry(mask, rainbow, slim);
                    assertNotNull(geometry, mask + " " + rainbow + " " + slim);
                    String name = Accessories.name(mask, rainbow, slim);
                    JsonObject id = JsonParser.parseString(geometry.geometryName()).getAsJsonObject().getAsJsonObject("geometry");
                    assertEquals("geometry.lavavisual." + name, id.get("default").getAsString());
                    JsonObject model = JsonParser.parseString(geometry.geometryData()).getAsJsonObject()
                        .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                    assertEquals("geometry.lavavisual." + name, model.getAsJsonObject("description").get("identifier").getAsString());
                    assertEquals(64, model.getAsJsonObject("description").get("texture_width").getAsInt());
                    Set<String> bones = new HashSet<>();
                    for (JsonElement bone : model.getAsJsonArray("bones")) bones.add(bone.getAsJsonObject().get("name").getAsString());
                    for (String bone : PLAYER_BONES) assertTrue(bones.contains(bone), name + " misses " + bone);
                    assertEquals((mask & 1) != 0, bones.contains("lv_glasses"), name);
                    assertEquals((mask & 2) != 0, bones.contains("lv_headphones"), name);
                    assertEquals((mask & 4) != 0, bones.contains("lv_scarf"), name);
                    JsonArray arms = null;
                    for (JsonElement bone : model.getAsJsonArray("bones"))
                        if (bone.getAsJsonObject().get("name").getAsString().equals("leftArm")) arms = bone.getAsJsonObject().getAsJsonArray("cubes");
                    assertNotNull(arms);
                    assertEquals(slim ? 3 : 4, arms.get(0).getAsJsonObject().getAsJsonArray("size").get(0).getAsInt(), name);
                }
    }

    @Test
    void paletteGoesIntoTheUnusedCornerOnly() {
        byte[] data = new byte[64 * 64 * 4];
        for (int i = 0; i < data.length; i++) data[i] = (byte) 7;
        Skin painted = Accessories.paint(new Skin("https://textures.minecraft.net/texture/abc", data), 5, 9);
        assertNotNull(painted);
        assertEquals("https://textures.minecraft.net/texture/abc#lavavisual-5-9", painted.textureUrl());
        int[] palette = Accessories.palette(9);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int at = (y * 64 + x) * 4, cell = x < 8 && y < 8 ? (y / 2) * 4 + x / 2 : -1;
                int rgb = (painted.skinData()[at] & 255) << 16 | (painted.skinData()[at + 1] & 255) << 8 | painted.skinData()[at + 2] & 255;
                if (cell >= 0 && cell < palette.length) {
                    assertEquals(palette[cell], rgb, "cell " + cell);
                    // mask 5 = glasses and scarf: the headphone cells 3..6 stay transparent, the rest opaque.
                    boolean worn = cell < 3 || cell > 6;
                    assertEquals(worn ? 255 : 0, painted.skinData()[at + 3] & 255, "alpha of cell " + cell);
                } else {
                    assertEquals(0x070707, rgb, "pixel " + x + "," + y);
                }
            }
        assertEquals(7, data[0], "the original skin stays untouched");
        assertNull(Accessories.paint(new Skin("legacy", new byte[64 * 32 * 4]), 1, 1));
    }

    @Test
    void coloursFollowTheMod() {
        assertEquals(16, Accessories.CELLS);
        assertEquals(Colors.code(9), Accessories.palette(9)[0], "the glasses frame");
        assertEquals(Colors.code(9), Accessories.palette(9)[4], "the headphone strip");
        assertEquals(Colors.code(9), Accessories.palette(9)[7], "the scarf");
        assertEquals(Colors.companion(Colors.code(9)), Accessories.palette(9)[8], "the scarf's second tone");
        assertEquals(0xF2F2F2, Accessories.palette(28)[0]);
        Set<Integer> hues = new HashSet<>();
        for (int k = 10; k < 16; k++) hues.add(Accessories.palette(0)[k]);
        assertEquals(6, hues.size(), "a rainbow scarf uses six hues");
        Set<Integer> plain = new HashSet<>();
        for (int k = 10; k < 16; k++) plain.add(Accessories.palette(9)[k]);
        assertEquals(2, plain.size(), "an ordinary scarf keeps its two tones");
    }

    @Test
    void accessoriesThatAreNotWornArePaintedAway() {
        byte[] data = new byte[64 * 64 * 4];
        Skin painted = Accessories.paint(new Skin("x", data), 2, 9);   // headphones only
        assertNotNull(painted);
        int[] owner = {1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 4};
        for (int cell = 0; cell < Accessories.CELLS; cell++) {
            int x = (cell % 4) * 2, y = (cell / 4) * 2, at = (y * 64 + x) * 4;
            assertEquals(owner[cell] == 2 ? 255 : 0, painted.skinData()[at + 3] & 255, "cell " + cell);
        }
    }
}
