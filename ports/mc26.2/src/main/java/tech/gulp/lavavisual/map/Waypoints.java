package tech.gulp.lavavisual.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;

/** Saved coordinates per server / singleplayer world, filtered by dimension. Stored in waypoints.json next to hud.json. */
public final class Waypoints {
    private Waypoints() { }
    public static final int LIMIT = 64;
    public static final class Point {
        public String name = "Метка";
        public int x, y, z;
        public int color = 0xFF5A36;
        public boolean visible = true;
        public String dimension = "minecraft:overworld";
        public Point() { }
        public Point(String name, int x, int y, int z, int color, String dimension) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.color = color; this.dimension = dimension;
        }
    }
    private static final class Store { Map<String, List<Point>> worlds = new LinkedHashMap<>(); }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static Store store;
    private static Path file() { return LavaVisualClient.configDirectory().resolve("waypoints.json"); }

    private static Store store() {
        if (store != null) return store;
        store = new Store();
        try {
            Path path = file();
            if (Files.exists(path)) {
                Store loaded = JSON.fromJson(Files.readString(path), Store.class);
                if (loaded != null && loaded.worlds != null) store = loaded;
            }
        } catch (Exception e) {
            LavaVisual.LOGGER.warn("Could not read LavaVisual waypoints", e);
        }
        store.worlds.values().removeIf(list -> list == null);
        for (List<Point> list : store.worlds.values()) {
            list.removeIf(p -> p == null);
            for (Point p : list) {
                if (p.name == null || p.name.isBlank()) p.name = "Метка";
                if (p.name.length() > 32) p.name = p.name.substring(0, 32);
                if (p.dimension == null) p.dimension = "minecraft:overworld";
                p.color = Math.clamp(p.color, 0, 0xFFFFFF);
            }
        }
        return store;
    }
    public static void save() {
        try {
            Path path = file(), temp = path.resolveSibling("waypoints.json.tmp");
            Files.createDirectories(path.getParent());
            store().worlds.values().removeIf(List::isEmpty);
            Files.writeString(temp, JSON.toJson(store()));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                Files.writeString(file(), JSON.toJson(store()));
            } catch (Exception again) {
                LavaVisual.LOGGER.error("Could not save LavaVisual waypoints", again);
            }
        }
    }
    /** "server:host" for multiplayer, "world:name" for singleplayer. */
    public static String worldKey(Minecraft mc) {
        try {
            var server = mc.getSingleplayerServer();
            if (server != null) return "world:" + server.getWorldData().getLevelName();
            var data = mc.getCurrentServer();
            if (data != null && data.ip != null) return "server:" + data.ip.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) { }
        return "unknown";
    }
    public static String dimension(Minecraft mc) {
        return mc.level == null ? "minecraft:overworld" : mc.level.dimension().identifier().toString();
    }
    /** Every point saved for the current world (all dimensions); editable list. */
    public static List<Point> all(Minecraft mc) {
        return store().worlds.computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
    }
    /** Points of the current world and dimension. */
    public static List<Point> here(Minecraft mc) {
        if (mc.level == null) return List.of();
        List<Point> list = store().worlds.get(worldKey(mc));
        if (list == null || list.isEmpty()) return List.of();
        String dimension = dimension(mc);
        List<Point> result = new ArrayList<>();
        for (Point p : list) if (dimension.equals(p.dimension)) result.add(p);
        return result;
    }
    public static boolean add(Minecraft mc, Point point) {
        List<Point> list = all(mc);
        if (list.size() >= LIMIT) return false;
        list.add(point); save();
        return true;
    }
    public static void remove(Minecraft mc, Point point) { all(mc).remove(point); save(); }
    public static String distance(double blocks) {
        return blocks < 1000 ? Math.round(blocks) + " м" : String.format(Locale.ROOT, "%.1f км", blocks / 1000);
    }
    public static String nextName(Minecraft mc) { return "Метка " + (all(mc).size() + 1); }
}
