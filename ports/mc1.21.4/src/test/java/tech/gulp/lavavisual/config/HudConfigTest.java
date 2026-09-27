package tech.gulp.lavavisual.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class HudConfigTest {
    @Test void everythingIsInitiallyOff() {
        var c = new HudConfig();
        // The music HUD is the one exception: it only appears while the user plays music (musicHudAuto).
        assertTrue(c.widgets.entrySet().stream().noneMatch(e -> e.getValue().visible && !e.getKey().equals("music")));
        assertTrue(c.musicHudAuto);
        assertFalse(c.crosshairEnabled);
        assertFalse(c.jumpEnabled || c.particlesEnabled || c.ambientEnabled || c.viewModelEnabled);
        assertFalse(c.hitSoundEnabled || c.critSoundEnabled || c.totemSoundEnabled);
        assertFalse(c.markerEnabled || c.skyEnabled || c.fpsBoost);
    }
    @Test void clampsAppearanceAndPosition() {
        var c = new HudConfig(); var w = c.widgets.get("target");
        w.x = Double.NaN; w.y = 20; w.scale = -2; w.opacity = 100; c.sanitize();
        assertEquals(0.02, w.x); assertEquals(1, w.y);
        assertEquals(0.5, w.scale); assertEquals(1, w.opacity);
        assertEquals(9, c.widgets.size());
    }
    @Test void existingNewSettingsArePreserved(@TempDir Path dir) {
        var store = new ConfigStore(dir); var c = new HudConfig();
        c.widgets.get("target").visible = true; c.crosshairEnabled = true;
        assertTrue(store.save(c, 0));
        var loaded = store.load(0);
        assertTrue(loaded.widgets.get("target").visible); assertTrue(loaded.crosshairEnabled);
    }
    @Test void migratesOldAutoEnabledDefaultsOnlyOnce(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hud.json"), "{\"widgets\":{\"coordinates\":{\"visible\":true,\"x\":0.42,\"y\":0.24}}}");
        var store = new ConfigStore(dir); var c = store.load(0);
        assertFalse(c.widgets.get("coordinates").visible);
        assertEquals(0.42, c.widgets.get("coordinates").x);
        c.widgets.get("coordinates").visible = true;
        assertTrue(store.save(c, 0)); assertTrue(store.load(0).widgets.get("coordinates").visible);
    }
    @Test void removedPanelsDoNotSurviveMigration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("hud.json"), "{\"schemaVersion\":2,\"widgets\":{\"stopwatch\":{\"visible\":true},\"island\":{\"visible\":true}}}");
        var store = new ConfigStore(dir); var c = store.load(0);
        assertEquals(java.util.Set.of("coordinates", "performance", "target", "keys", "armor", "totems", "watermark", "minimap", "music"), c.widgets.keySet());
        assertTrue(store.save(c, 0));
        assertFalse(Files.readString(dir.resolve("hud.json")).contains("stopwatch"));
        assertFalse(Files.readString(dir.resolve("hud.json")).contains("island"));
    }
    @Test void allCosmeticsDisableAndClamp() {
        var c = new HudConfig();
        c.jumpEnabled = c.particlesEnabled = c.ambientEnabled = c.viewModelEnabled = true;
        c.hitSoundEnabled = c.critSoundEnabled = c.totemSoundEnabled = true;
        c.mainHand.x = Double.NaN; c.mainHand.z = 40; c.mainHand.scale = -10;
        c.offHand = null; c.hitVolume = Double.POSITIVE_INFINITY; c.critPreset = -10;
        c.particleCount = 9999; c.jumpRadius = -5; c.rgb = -1; c.targetHold = 99; c.sanitize();
        assertEquals(0, c.mainHand.x); assertEquals(1.5, c.mainHand.z); assertEquals(0.4, c.mainHand.scale);
        assertNotNull(c.offHand); assertEquals(0.65, c.hitVolume); assertEquals(2, c.critPreset); assertEquals(10, c.targetHold, 0.001);
        assertEquals(24, c.particleCount); assertEquals(0.5, c.jumpRadius); assertEquals(0, c.rgb);
        c.disableAll();
        assertFalse(c.jumpEnabled || c.particlesEnabled || c.ambientEnabled || c.viewModelEnabled);
        assertFalse(c.hitSoundEnabled || c.critSoundEnabled || c.totemSoundEnabled);
        assertFalse(c.markerEnabled || c.skyEnabled || c.fpsBoost);
    }
    @Test void soundLibraryIndicesWrapAndKillSoundDisables() {
        var c = new HudConfig();
        c.hitSound = -1; c.critSound = HudConfig.SOUND_LIBRARY + 5; c.killVolume = Double.NaN; c.killSoundEnabled = true;
        c.sanitize();
        assertEquals(HudConfig.SOUND_LIBRARY, c.hitSound); assertEquals(4, c.critSound); assertEquals(0.65, c.killVolume);
        c.disableAll();
        assertFalse(c.killSoundEnabled);
        assertFalse(c.widgets.get("watermark").visible);
    }

    @org.junit.jupiter.api.Test
    void perElementColorsFollowThemeUntilCustomised() {
        HudConfig c = new HudConfig();
        c.rgb = 0x123456;
        assertEquals(0xFF123456, c.color("target"));
        assertEquals(0xFF111216, c.color("hud_bg"));
        c.colors.put("target", 0xABCDEF);
        c.colors.put("unknown", 5);
        c.colors.put("menu", 0x7FFFFFFF);
        c.chroma.add("nope");
        c.hatSize = 9; c.hatSpin = -1; c.mapZoom = 7;
        c.sanitize();
        assertEquals(0xFFABCDEF, c.color("target"));
        assertEquals(0xFFFFFFFF, c.color("menu"));
        assertFalse(c.colors.containsKey("unknown"));
        assertTrue(c.chroma.isEmpty());
        assertEquals(1.8, c.hatSize); assertEquals(0, c.hatSpin); assertEquals(1, c.mapZoom);
        assertFalse(c.widgets.get("minimap").visible);
    }
    @org.junit.jupiter.api.Test
    void themesBecomeGradientsOnce() {
        HudConfig old = new HudConfig();
        old.rgb = 0x85F56A; old.styleVersion = 0; old.sanitize();
        assertEquals(HudConfig.THEMES[0][0], old.rgb); assertEquals(HudConfig.THEMES[0][1], old.rgb2); assertEquals(1, old.styleVersion);
        old.rgb = 0x85F56A; old.sanitize();
        assertEquals(0x85F56A, old.rgb, "an explicit choice after the migration stays");
        HudConfig lava = new HudConfig();
        lava.rgb = 0xFF5A36; lava.sanitize();
        assertEquals(0xFF5A36, lava.rgb); assertEquals(0xFFC233, lava.rgb2);
        HudConfig fresh = new HudConfig();
        assertEquals(0xFF000000 | HudConfig.THEMES[0][1], fresh.color2("target"));
        assertEquals(0xFF111216, fresh.color2("hud_bg"));
        fresh.colors.put("target", 0xFF0000);
        assertNotEquals(0xFFFF0000, fresh.color2("target"));
        assertEquals(0xFF808080, 0xFF000000 | ColorMath.companion(0x808080));
        assertEquals(HudConfig.THEMES.length, HudConfig.THEME_NAMES.length);
    }
    @org.junit.jupiter.api.Test
    void colorMathRoundTrips() {
        for (int rgb : new int[]{0xFF5A36, 0x85F56A, 0x36C8FF, 0xB45CFF, 0x000000, 0xFFFFFF, 0x808080}) {
            double[] hsv = ColorMath.toHsv(rgb);
            assertEquals(rgb, ColorMath.hsv(hsv[0], hsv[1], hsv[2]));
        }
        assertEquals(0xFF5A36, ColorMath.parse("#ff5a36"));
        assertEquals(0xFFAA00, ColorMath.parse("fa0"));
        assertEquals(-1, ColorMath.parse("zz"));
        assertEquals("#0A0B0C", ColorMath.hex(0x0A0B0C));
    }
}
