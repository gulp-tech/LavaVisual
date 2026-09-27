package tech.gulp.lavavisual.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Test;

public class PackWriterTest {
    private Map<String, byte[]> entries(byte[] pack) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int read; (read = zip.read(buffer)) > 0; ) out.write(buffer, 0, read);
                files.put(entry.getName(), out.toByteArray());
            }
        }
        return files;
    }

    @Test public void packHasEverythingMinecraftNeeds() throws Exception {
        byte[] pack = PackWriter.build("LavaVisual", "LV", new byte[]{1, 2, 3}, "{\"format_version\":\"1.12.0\"}",
                "geometry.lavavisual.a7", false, "a7-8");
        Map<String, byte[]> files = entries(pack);
        for (String name : new String[]{"manifest.json", "skins.json", "geometry.json", "skin.png", "texts/en_US.lang"})
            assertNotNull(name + " is missing", files.get(name));
        String manifest = new String(files.get("manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("skin_pack"));
        String skins = new String(files.get("skins.json"), StandardCharsets.UTF_8);
        assertTrue(skins.contains("geometry.lavavisual.a7"));
        assertTrue(skins.contains("skin.png"));
        assertEquals(3, files.get("skin.png").length);
    }

    @Test public void theSameLookGivesTheSameUuids() {
        assertEquals(PackWriter.uuid("a7-8:header"), PackWriter.uuid("a7-8:header"));
        assertTrue(!PackWriter.uuid("a7-8:header").equals(PackWriter.uuid("a7-8:module")));
        assertTrue(!PackWriter.uuid("a7-8:header").equals(PackWriter.uuid("a3-8:header")));
    }
}
