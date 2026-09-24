package tech.gulp.lavavisual.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class HudConfigTest {
    @Test void everythingIsInitiallyOff() {
        var c = new HudConfig();
        assertTrue(c.widgets.values().stream().noneMatch(w -> w.visible));
        assertFalse(c.crosshairEnabled);
        assertFalse(c.jumpEnabled || c.particlesEnabled || c.ambientEnabled || c.viewModelEnabled);
        assertFalse(c.hitSoundEnabled || c.critSoundEnabled || c.totemSoundEnabled);
        assertFalse(c.markerEnabled || c.skyEnabled || c.fpsBoost);
    }
    @Test void clampsAppearanceAndPosition() {
        var c = new HudConfig(); var w = c.widgets.get("target");
        w.x = Double.NaN; w.y = 20; w.scale = -2; w.opacity = 100; c.sanitize();
        assertEquals(0.02, w.x); assertEquals(1, w.y);
        assertEquals(0.6, w.scale); assertEquals(1, w.opacity);
        assertEquals(3, c.widgets.size());
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
        assertEquals(java.util.Set.of("coordinates", "performance", "target"), c.widgets.keySet());
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
        assertNotNull(c.offHand); assertEquals(0.65, c.hitVolume); assertEquals(2, c.critPreset); assertEquals(3, c.targetHold, 0.001);
        assertEquals(24, c.particleCount); assertEquals(0.5, c.jumpRadius); assertEquals(0, c.rgb);
        c.disableAll();
        assertFalse(c.jumpEnabled || c.particlesEnabled || c.ambientEnabled || c.viewModelEnabled);
        assertFalse(c.hitSoundEnabled || c.critSoundEnabled || c.totemSoundEnabled);
        assertFalse(c.markerEnabled || c.skyEnabled || c.fpsBoost);
    }
}
