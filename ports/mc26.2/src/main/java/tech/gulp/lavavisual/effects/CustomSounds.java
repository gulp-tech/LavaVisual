package tech.gulp.lavavisual.effects;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import tech.gulp.lavavisual.LavaVisual;

/** User .ogg files from config/lavavisual-hud/sounds exposed through a generated local resource pack. */
public final class CustomSounds {
    private static final String PACK = "LavaVisual Sounds";
    private static final List<String> NAMES = new ArrayList<>();
    private CustomSounds() { }
    public static List<String> names() { return NAMES; }
    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("lavavisual-hud").resolve("sounds"); }
    private static Path packDir() { return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK); }
    private static String packId() { return "file/" + PACK; }
    public static synchronized void refresh(Minecraft client) {
        NAMES.clear();
        Path source = dir();
        if (Files.isDirectory(source)) try (Stream<Path> files = Files.list(source)) {
            files.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"))
                    .map(f -> f.getFileName().toString().substring(0, f.getFileName().toString().length() - 4))
                    .map(name -> name.replaceAll("[^a-z0-9/._-]", "_")).sorted().distinct().forEach(NAMES::add);
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("Cannot read custom sounds folder", error);
        }
        if (client == null) return;
        var repository = client.getResourcePackRepository();
        if (repository == null) return;
        try {
            Path pack = packDir();
            if (NAMES.isEmpty()) {
                if (Files.exists(pack)) deleteRecursively(pack);
            } else {
                Path sounds = pack.resolve("assets/lavavisual/sounds/custom");
                Files.createDirectories(sounds);
                Files.writeString(pack.resolve("pack.mcmeta"),
                        "{\"pack\": {\"pack_format\": 34, \"supported_formats\": {\"min_inclusive\": 9, \"max_inclusive\": 999}, \"description\": \"LavaVisual custom sounds\"}}");
                JsonObject root = new JsonObject();
                for (String name : NAMES) {
                    Files.copy(source.resolve(name + ".ogg"), sounds.resolve(name + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
                    JsonObject entry = new JsonObject();
                    var array = new com.google.gson.JsonArray();
                    array.add("lavavisual:sounds/custom/" + name);
                    entry.add("sounds", array);
                    root.add("lavavisual:custom/" + name, entry);
                }
                Files.writeString(pack.resolve("assets/lavavisual/sounds.json"), root.toString());
            }
            List<String> selected = new ArrayList<>(repository.getSelectedPacks().stream().map(p -> p.getId()).toList());
            boolean has = selected.remove(packId());
            if (!NAMES.isEmpty()) selected.add(packId());
            if (has != NAMES.isEmpty() || !NAMES.isEmpty()) {
                repository.setSelected(selected);
                client.options.updateResourcePacks(repository);
                client.reloadResourcePacks();
            }
        } catch (IOException | RuntimeException error) {
            LavaVisual.LOGGER.warn("Cannot apply custom sounds pack", error);
        }
        LavaVisual.LOGGER.info("LavaVisual custom sounds: {}", NAMES.size());
    }
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }
}
