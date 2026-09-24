package tech.gulp.lavavisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import tech.gulp.lavavisual.LavaVisual;

public final class ConfigStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    public ConfigStore(Path directory) { this.directory = directory; }
    private Path path(int slot) {
        if (slot < 0 || slot > 3) throw new IllegalArgumentException("Profile must be 0..3");
        return directory.resolve(slot == 0 ? "hud.json" : "profile-" + slot + ".json");
    }
    public HudConfig load(int slot) {
        Path file = path(slot);
        if (!Files.exists(file)) return new HudConfig();
        try (var reader = Files.newBufferedReader(file)) {
            HudConfig config = JSON.fromJson(reader, HudConfig.class);
            if (config == null) return new HudConfig();
            config.sanitize();
            return config;
        } catch (IOException | RuntimeException error) {
            LavaVisual.LOGGER.warn("Cannot load HUD settings: {}", file, error);
            return new HudConfig();
        }
    }
    public boolean exists(int slot) { return Files.exists(path(slot)); }
    public boolean save(HudConfig config, int slot) {
        config.sanitize();
        Path file = path(slot), temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, JSON.toJson(config));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("Cannot save HUD settings", error);
            return false;
        }
    }
}
