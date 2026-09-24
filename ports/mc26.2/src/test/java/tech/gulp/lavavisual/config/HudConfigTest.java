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
    }
    @Test void clampsAppearanceAndPosition() {
        var c = new HudConfig(); var w = c.widgets.get("target");
        w.x = Double.NaN; w.y = 20; w.scale = -2; w.opacity = 100; c.sanitize();
        assertEquals(0.02, w.x); assertEquals(1, w.y);
        assertEquals(0.6, w.scale); assertEquals(1, w.opacity);
        assertEquals(5, c.widgets.size());
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
}
