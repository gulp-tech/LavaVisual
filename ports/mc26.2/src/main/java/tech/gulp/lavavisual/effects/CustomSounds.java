package tech.gulp.lavavisual.effects;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.audio.MusicPlayer;
import tech.gulp.lavavisual.audio.AudioInfo;

/**
 * The user's own sound files (.mp3, .ogg, .opus, .wav). The folders are created automatically:
 * .minecraft/LavaVisual/sounds/{hits, crits, totems, kills} - each file shows by its own name in that list
 * (files directly in sounds/ show in every list), and .minecraft/LavaVisual/music for the music player.
 * Files play directly through OpenAL (LavaAudio), so no resource pack or reload is needed and any file name works.
 */
public final class CustomSounds {
    public static final String[] FOLDERS = {"hits", "crits", "totems", "kills"};
    public static final String[] FOLDER_TITLES = {"удары", "криты", "тотемы", "убийства"};
    /** key: folder/file name ("*" for the shared folder); name: file name without the extension. */
    public record Entry(String key, String name, Path file, boolean playable) { }
    private static final List<List<Entry>> GROUPS = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private static int files, skipped;
    private static long lastScan;
    private CustomSounds() { }

    /** Settings folder (hud.json); sounds and music lived here before 1.0. */
    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("lavavisual-hud"); }
    /** Sounds and music: .minecraft/LavaVisual, next to mods, easy to find in any file manager (phones included). */
    public static Path media() { return FabricLoader.getInstance().getGameDir().resolve("LavaVisual"); }
    public static Path dir() { return media().resolve("sounds"); }
    public static Path musicDir() { return media().resolve("music"); }
    private record Probe(long size, long modified, boolean playable) { }
    private static final java.util.Map<Path, Probe> PROBES = new java.util.HashMap<>();

    /** Moves files from the old folders (config/lavavisual-hud/sounds and music) once; nothing is overwritten. */
    private static void migrate() {
        for (String sub : new String[]{"sounds", "music"}) {
            Path from = root().resolve(sub), to = media().resolve(sub);
            if (!Files.isDirectory(from)) continue;
            int moved = 0;
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (file.getFileName().toString().equals("ПРОЧТИ.txt")) { Files.deleteIfExists(file); continue; }
                    Path target = to.resolve(from.relativize(file).toString());
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) { Files.move(file, target); moved++; }
                }
            } catch (IOException | RuntimeException error) {
                LavaVisual.LOGGER.warn("LavaVisual: cannot move {} to {}", from, to, error);
            }
            // Only empty folders are removed; anything left behind stays where it was.
            try (Stream<Path> walk = Files.walk(from)) {
                walk.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).forEach(d -> { try { Files.delete(d); } catch (IOException ignored) { } });
            } catch (IOException | RuntimeException ignored) { }
            if (moved > 0) LavaVisual.LOGGER.info("LavaVisual: moved {} files to {}", moved, to);
        }
    }

    /** Startup: folders, removal of the old generated resource pack, first scan of sounds and music. */
    public static void init() {
        migrate();
        ensureFolders();
        try {
            Path old = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("LavaVisual Sounds");
            if (Files.isDirectory(old)) try (Stream<Path> walk = Files.walk(old)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
            }
        } catch (IOException | RuntimeException ignored) { }
        scan();
        MusicPlayer.rescan(musicDir());
    }
    public static void ensureFolders() {
        try {
            Files.createDirectories(dir());
            for (String folder : FOLDERS) Files.createDirectories(dir().resolve(folder));
            Files.createDirectories(musicDir());
            Path readme = dir().resolve("ПРОЧТИ.txt");
            if (!Files.exists(readme) || Files.readString(readme).contains("(Vorbis)")) Files.writeString(readme, """
                    Свои звуки LavaVisual: файлы .mp3, .ogg, .opus или .wav.
                    hits   - звуки ударов
                    crits  - звуки критов
                    totems - звуки тотема
                    kills  - звуки убийства
                    Файлы прямо в этой папке появятся во всех списках.
                    Имя файла может быть любым (русские буквы и пробелы тоже).
                    Список обновляется сам, выбор в меню LavaVisual → Звуки (стрелки у каждого события).
                    """, StandardCharsets.UTF_8);
            Path music = musicDir().resolve("ПРОЧТИ.txt");
            if (!Files.exists(music) || Files.readString(music).contains("(Vorbis)")) Files.writeString(music, """
                    Музыка LavaVisual: файлы .mp3, .ogg, .opus или .wav.
                    Плеер открывается клавишей M (меняется во вкладке «Бинды»).
                    Обложка: встроенная в файл или картинка рядом с тем же именем (.png / .jpg).
                    """, StandardCharsets.UTF_8);
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot create the sound folders", error);
        }
    }
    public static synchronized void scan() {
        lastScan = System.currentTimeMillis();
        List<Entry> shared = list(dir(), "*");
        int total = shared.size(), bad = (int) shared.stream().filter(e -> !e.playable()).count();
        for (int g = 0; g < FOLDERS.length; g++) {
            List<Entry> own = list(dir().resolve(FOLDERS[g]), FOLDERS[g]);
            total += own.size();
            bad += (int) own.stream().filter(e -> !e.playable()).count();
            List<Entry> group = GROUPS.get(g);
            group.clear();
            group.addAll(own);
            group.addAll(shared);
        }
        files = total;
        skipped = bad;
    }
    /** The Sounds page calls this every frame: files appear without any button. */
    public static void scanIfStale() { if (System.currentTimeMillis() - lastScan > 2000) scan(); }
    private static List<Entry> list(Path folder, String key) {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (Stream<Path> stream = Files.list(folder)) {
            for (Path file : stream.filter(AudioInfo::isAudio).sorted().toList()) {
                String name = file.getFileName().toString();
                // Only new or changed files are opened: the Sounds page rescans every two seconds.
                long size = Files.size(file), modified = Files.getLastModifiedTime(file).toMillis();
                Probe probe = PROBES.get(file);
                if (probe == null || probe.size() != size || probe.modified() != modified) {
                    probe = new Probe(size, modified, AudioInfo.read(file, 65536, false).playable());
                    if (PROBES.size() > 4096) PROBES.clear();
                    PROBES.put(file, probe);
                }
                out.add(new Entry(key + "/" + name, AudioInfo.baseName(file), file, probe.playable()));
            }
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read {}", folder, error);
        }
        return out;
    }
    public static synchronized List<Entry> list(int group) { return List.copyOf(GROUPS.get(Math.clamp(group, 0, 3))); }
    public static int files() { return files; }
    public static int skipped() { return skipped; }

    /** Copies dropped audio files into the folder; returns how many were copied. */
    public static int importFiles(List<Path> dropped, Path target) {
        ensureFolders();
        int copied = 0;
        for (Path file : dropped) {
            if (!AudioInfo.isAudio(file)) continue;
            try {
                Files.copy(file, target.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException error) {
                LavaVisual.LOGGER.warn("LavaVisual: cannot copy {}", file, error);
            }
        }
        scan();
        MusicPlayer.rescan(musicDir());
        return copied;
    }
    public static void open(Path folder) {
        ensureFolders();
        if (tech.gulp.lavavisual.Platform.android()) {
            // Phones cannot open a folder from the game: copy the path and say where to find it.
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(folder.toAbsolutePath().toString());
            tech.gulp.lavavisual.input.Binds.Toast.show("Путь скопирован · Проводник → Расположения → лаунчер → .minecraft/LavaVisual");
            return;
        }
        net.minecraft.util.Util.getPlatform().openPath(folder);
    }
}
