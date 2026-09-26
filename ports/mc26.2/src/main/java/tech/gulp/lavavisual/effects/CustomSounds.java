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
import tech.gulp.lavavisual.audio.OggInfo;

/**
 * The user's own .ogg files. The folders are created automatically:
 * config/lavavisual-hud/sounds/{hits, crits, totems, kills} — each file shows by its own name in that list
 * (files directly in sounds/ show in every list), and config/lavavisual-hud/music for the music player.
 * Files play directly through OpenAL (LavaAudio), so no resource pack or reload is needed and any file name works.
 */
public final class CustomSounds {
    public static final String[] FOLDERS = {"hits", "crits", "totems", "kills"};
    public static final String[] FOLDER_TITLES = {"удары", "криты", "тотемы", "убийства"};
    /** key: folder/file name ("*" for the shared folder); name: file name without .ogg. */
    public record Entry(String key, String name, Path file, boolean playable) { }
    private static final List<List<Entry>> GROUPS = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private static int files, skipped;
    private static long lastScan;
    private CustomSounds() { }

    public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("lavavisual-hud"); }
    public static Path dir() { return root().resolve("sounds"); }
    public static Path musicDir() { return root().resolve("music"); }

    /** Startup: folders, removal of the pre-2.18 generated resource pack, first scan of sounds and music. */
    public static void init() {
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
            if (!Files.exists(readme)) Files.writeString(readme, """
                    Свои звуки LavaVisual — файлы .ogg (Vorbis).
                    hits   — звуки ударов
                    crits  — звуки критов
                    totems — звуки тотема
                    kills  — звуки убийства
                    Файлы прямо в этой папке появятся во всех списках.
                    Имя файла может быть любым (русские буквы и пробелы тоже).
                    Список обновляется сам, выбор — в меню LavaVisual → Звуки (стрелки у каждого события).
                    """, StandardCharsets.UTF_8);
            Path music = musicDir().resolve("ПРОЧТИ.txt");
            if (!Files.exists(music)) Files.writeString(music, """
                    Музыка LavaVisual — файлы .ogg (Vorbis).
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
            for (Path file : stream.filter(MusicPlayer::isOgg).sorted().toList()) {
                String name = file.getFileName().toString();
                out.add(new Entry(key + "/" + name, name.substring(0, name.length() - 4), file, OggInfo.read(file, 65536, false).playable()));
            }
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read {}", folder, error);
        }
        return out;
    }
    public static synchronized List<Entry> list(int group) { return List.copyOf(GROUPS.get(Math.clamp(group, 0, 3))); }
    public static int files() { return files; }
    public static int skipped() { return skipped; }

    /** Copies dropped .ogg files into the folder; returns how many were copied. */
    public static int importFiles(List<Path> dropped, Path target) {
        ensureFolders();
        int copied = 0;
        for (Path file : dropped) {
            if (!MusicPlayer.isOgg(file)) continue;
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
        net.minecraft.util.Util.getPlatform().openPath(folder);
    }
}
