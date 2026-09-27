package tech.gulp.lavavisual.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class SkinMathTest {
    @Test public void geometryNames() {
        assertEquals("a7", SkinMath.geometryName(7, false, false));
        assertEquals("a7rs", SkinMath.geometryName(7, true, true));
        assertEquals("a3", SkinMath.geometryName(3, true, false));
        assertEquals("a1s", SkinMath.geometryName(1, false, true));
    }

    @Test public void everyGeometryTheMenuCanAskForIsShipped() throws Exception {
        File folder = new File("src/main/assets/geometry");
        assertTrue("assets are missing", folder.isDirectory());
        int count = 0;
        for (int mask = 1; mask <= 7; mask++)
            for (boolean rainbow : new boolean[]{false, true})
                for (boolean slim : new boolean[]{false, true}) {
                    String name = SkinMath.geometryName(mask, rainbow, slim);
                    File file = new File(folder, name + ".json");
                    assertTrue(name + " is missing", file.isFile());
                    String data = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    assertTrue(name + " has the wrong identifier", data.contains("geometry.lavavisual." + name));
                    count++;
                }
        assertEquals(28, count);
        assertEquals(22, folder.listFiles((dir, name) -> name.endsWith(".json")).length);
    }

    @Test public void paletteFollowsTheColourCode() {
        int[] palette = SkinMath.palette(8);
        assertEquals(SkinMath.CELLS, palette.length);
        assertEquals(Colors.code(8), palette[0]);
        assertEquals(Colors.companion(Colors.code(8)), palette[1]);
        assertEquals(Colors.scale(Colors.code(8), 0.62), palette[12]);
        int[] rainbow = SkinMath.palette(0);
        assertEquals(Colors.hsv(0.03, 0.72, 1), rainbow[0]);
        assertEquals(Colors.hsv(0.19, 0.72, 1), rainbow[1]);
    }

    @Test public void paintTouchesOnlyTheUnusedCorner() {
        int[] pixels = new int[SkinMath.SIZE * SkinMath.SIZE];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xFF123456;
        SkinMath.paint(pixels, 8);
        for (int y = 0; y < SkinMath.SIZE; y++)
            for (int x = 0; x < SkinMath.SIZE; x++) {
                boolean corner = x < 8 && y < 8;
                int pixel = pixels[y * SkinMath.SIZE + x];
                if (!corner) assertEquals("pixel " + x + "," + y + " changed", 0xFF123456, pixel);
                else assertEquals(0xFF000000, pixel & 0xFF000000);
            }
        assertEquals(Colors.opaque(SkinMath.palette(8)[0]), pixels[0]);
        assertEquals(Colors.opaque(SkinMath.palette(8)[5]), pixels[2 * SkinMath.SIZE + 2]);
    }

    @Test public void onlySkinsOfTheRightSizeAreAccepted() {
        try {
            SkinMath.paint(new int[10], 8);
            fail("a small image should be rejected");
        } catch (IllegalArgumentException expected) { }
    }
}
