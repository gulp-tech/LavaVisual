package tech.gulp.lavavisual.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LavaPaletteTest {
    @Test void exactStops() {
        assertEquals(0x8B0000, LavaPalette.color(0));
        assertEquals(0xFF0000, LavaPalette.color(0.5f));
        assertEquals(0xFF6B00, LavaPalette.color(1));
    }
    @Test void clamps() {
        assertEquals(0x8B0000, LavaPalette.color(-2));
        assertEquals(0xFF6B00, LavaPalette.color(3));
    }
    @Test void interpolationIsContinuous() {
        int previousRed = 0, previousGreen = 0;
        for (int i = 0; i <= 1000; i++) {
            int color = LavaPalette.color(i / 1000f);
            int red = (color >> 16) & 255, green = (color >> 8) & 255;
            assertTrue(red >= previousRed);
            assertTrue(green >= previousGreen);
            assertEquals(0, color & 255);
            previousRed = red; previousGreen = green;
        }
    }
}
