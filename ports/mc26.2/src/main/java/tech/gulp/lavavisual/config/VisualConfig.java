package tech.gulp.lavavisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import tech.gulp.lavavisual.LavaVisual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VisualConfig {
    public enum BoxMode { OFF, BOX_3D, BOX_2D }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lavavisual.json");
    public boolean enabled = true;
    public BoxMode boxMode = BoxMode.BOX_3D;
    public boolean tracers = true;
    public boolean chams = true;
    public boolean players = true;
    public boolean mobs = true;
    public double maxDistance = 96.0;
    public int maxEntities = 128;
    public float chamsAlpha = 0.38f;

    public static VisualConfig load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                VisualConfig config = GSON.fromJson(reader, VisualConfig.class);
                if (config != null) {
                    config.sanitize();
                    return config;
                }
            } catch (IOException | RuntimeException ex) {
                LavaVisual.LOGGER.warn("Cannot read LavaVisual config; using defaults", ex);
            }
        }
        return new VisualConfig();
    }

    public void sanitize() {
        if (boxMode == null) boxMode = BoxMode.BOX_3D;
        maxDistance = Double.isFinite(maxDistance) ? Math.max(8, Math.min(256, maxDistance)) : 96;
        maxEntities = Math.max(1, Math.min(512, maxEntities));
        chamsAlpha = Float.isFinite(chamsAlpha) ? Math.max(0.05f, Math.min(0.85f, chamsAlpha)) : 0.38f;
    }

    public void save() {
        sanitize();
        Path temporary = FILE.resolveSibling("lavavisual.json.tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, GSON.toJson(this));
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            LavaVisual.LOGGER.warn("Cannot save LavaVisual config", ex);
        }
    }
}
