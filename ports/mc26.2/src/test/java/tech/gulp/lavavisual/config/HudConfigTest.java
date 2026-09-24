package tech.gulp.lavavisual.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class HudConfigTest {
    @Test void repairsInvalidValues() {
        var c = new HudConfig();
        c.widgets.get("coordinates").x = Double.NaN;
        c.widgets.get("coordinates").y = 20;
        c.crosshair = -1;
        c.friends = new ArrayList<>(Arrays.asList(null, "Bob", "Bob", "../bad"));
        c.sanitize();
        assertEquals(0.02, c.widgets.get("coordinates").x);
        assertEquals(1, c.widgets.get("coordinates").y);
        assertEquals(3, c.crosshair);
        assertEquals(java.util.List.of("Bob"), c.friends);
    }
    @Test void restoresMissingWidgets() {
        var c = new HudConfig(); c.widgets.clear(); c.sanitize();
        assertEquals(6, c.widgets.size());
    }
    @Test void savesIndependentProfiles(@TempDir Path dir) {
        var store = new ConfigStore(dir); var config = new HudConfig();
        config.widgets.get("island").visible = false; config.friends.add("Alice");
        assertTrue(store.save(config, 1));
        assertFalse(store.load(1).widgets.get("island").visible);
        assertEquals(java.util.List.of("Alice"), store.load(1).friends);
        assertTrue(store.load(2).widgets.get("island").visible);
        assertThrows(IllegalArgumentException.class, () -> store.save(config, 4));
    }
    @Test void legacyCombatSettingsAreNotPartOfConfig() {
        for (var field : HudConfig.class.getFields())
            assertFalse(java.util.List.of("chams", "tracers", "maxDistance", "boxMode").contains(field.getName()));
    }
}
