package tech.gulp.lavavisual.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.effects.CustomSounds;

/**
 * Real radio: stations that are on air right now, streamed from the internet and played through the same music player
 * as a local file. The list is grouped — talk radio, where the hosts and the guests actually speak; the radio of
 * Makhachkala and Dagestan; and music. The radio of any other city can be added in {@code LavaVisual/radio.txt}
 * (one line per station: {@code name | genre | url}), which the mod creates on first use. Only MP3 streams are listed,
 * because MP3 is the internet format the mod decodes by itself.
 */
public final class NetRadio {
    private NetRadio() { }

    /** One internet station: the group it is listed under, its name, its genre and the stream address. */
    public record Station(String group, String name, String genre, String url) { }

    public static final String TALK = "Разговорное", LOCAL = "Махачкала · Дагестан", MUSIC = "Музыка", OWN = "Свои станции";

    private static final List<Station> BUILT_IN = List.of(
            // Talk radio: news, interviews and phone-ins — the hosts and the callers never stop talking.
            new Station(TALK, "Радио Маяк", "новости, ток-шоу, разговоры", "http://icecast.vgtrk.cdnvideo.ru/mayakfm_mp3_192kbps"),
            new Station(TALK, "Вести FM", "новости и разговоры в студии", "http://icecast.vgtrk.cdnvideo.ru/vestifm"),
            new Station(TALK, "Радио России", "главный канал страны", "http://icecast.vgtrk.cdnvideo.ru/rrzonam_mp3_128kbps"),
            new Station(TALK, "Комсомольская правда", "разговоры, новости, прямые эфиры", "http://kpradio.hostingradio.ru:8000/russia.radiokp128.mp3"),
            new Station(TALK, "Говорит Москва", "интервью и ток-шоу", "http://media.govoritmoskva.ru:8880/ru64.mp3"),
            new Station(TALK, "Радио Книга", "книги, чтение, разговоры", "http://bookradio.hostingradio.ru:8069/fm"),
            // The radio of this city: the Makhachkala studio Radio05 — Russian and the languages of Dagestan, with
            // hosts, call-ins and the music of every nation of the republic.
            new Station(LOCAL, "Радио Дагестан", "республиканский канал", "http://stream.radio05.ru:8000/radio_dagestan_128"),
            new Station(LOCAL, "Радио Кавказ", "Кавказ: музыка и разговоры", "http://stream.radio05.ru:8000/radio_kavkaz_128"),
            new Station(LOCAL, "Радио Ватан · 106.6 FM", "Махачкала, на русском и аварском", "http://stream.radio05.ru:8000/radio_vatan_128"),
            new Station(LOCAL, "Radio05.Ru", "главный эфир студии", "http://stream.radio05.ru:8000/radio05_128"),
            new Station(LOCAL, "Аварское радио", "на аварском языке", "http://stream.radio05.ru:8000/avarskoe_radio_128"),
            new Station(LOCAL, "Даргинское радио", "на даргинском языке", "http://stream.radio05.ru:8000/darginskoe_radio_128"),
            new Station(LOCAL, "Лезгинское радио", "на лезгинском языке", "http://stream.radio05.ru:8000/lezginskoe_radio_128"),
            new Station(LOCAL, "Кумыкское радио", "на кумыкском языке", "http://stream.radio05.ru:8000/kumykskoe_radio_128"),
            new Station(LOCAL, "Лакское радио", "на лакском языке", "http://stream.radio05.ru:8000/lakskoe_radio_128"),
            new Station(LOCAL, "Рутульское радио", "на рутульском языке", "http://stream.radio05.ru:8000/rutulskoe_radio_128"),
            new Station(LOCAL, "Цахурское радио", "на цахурском языке", "http://stream.radio05.ru:8000/tsakhurskoe_radio_128"),
            new Station(LOCAL, "Агульское радио", "на агульском языке", "http://stream.radio05.ru:8000/agulskoe_radio_128"),
            new Station(LOCAL, "Табасаранское радио", "на табасаранском языке", "http://stream.radio05.ru:8000/tabasaranskoe_radio_128"),
            new Station(LOCAL, "Ногайское радио", "на ногайском языке", "http://stream.radio05.ru:8000/nogayskoe_radio_128"),
            new Station(LOCAL, "Татское радио", "на татском языке", "http://stream.radio05.ru:8000/tatskoe_radio_128"),
            new Station(LOCAL, "Азербайджанское радио", "на азербайджанском языке", "http://stream.radio05.ru:8000/azerbaydzhanskoe_radio_128"),
            new Station(LOCAL, "Чеченское радио", "на чеченском языке", "http://stream.radio05.ru:8000/chechenskoe_radio_128"),
            new Station(LOCAL, "Прибой FM", "музыка и эфиры Махачкалы", "http://stream.radio05.ru:8000/priboyfm_128"),
            // Music of the big Russian stations; every stream here is MP3.
            new Station(MUSIC, "Европа Плюс", "популярная музыка", "http://ep256.hostingradio.ru:8052/europaplus256.mp3"),
            new Station(MUSIC, "Дорожное радио", "музыка для дороги", "http://dorognoe.hostingradio.ru:8000/radio"),
            new Station(MUSIC, "Ретро FM 70-е", "хиты семидесятых", "http://retro70.hostingradio.ru:8025/retro70-128.mp3"),
            new Station(MUSIC, "Радио Шансон", "шансон и городской романс", "http://chanson.hostingradio.ru:8041/chanson256.mp3"),
            new Station(MUSIC, "Русский Рок", "отечественный рок", "http://rock.volna.top/RusRock"),
            new Station(MUSIC, "Русские Песни", "народные и эстрадные песни", "http://listen.rusongs.ru/ru-mp3-128"));

    private static volatile List<Station> list;
    private static volatile long listTime = -60000, fileStamp = Long.MIN_VALUE;

    private static List<Station> reload() {
        List<Station> all = new ArrayList<>(BUILT_IN);
        Path file = file();
        try {
            if (Files.isRegularFile(file)) {
                fileStamp = Files.getLastModifiedTime(file).toMillis();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Station station = parse(line);
                    if (station != null) all.add(station);
                }
            } else template(file);
        } catch (IOException | RuntimeException failure) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read radio.txt", failure);
        }
        return List.copyOf(all);
    }

    /** name | genre | url — everything after the second bar belongs to the address. */
    private static Station parse(String line) {
        String text = line.trim();
        if (text.isEmpty() || text.startsWith("#")) return null;
        String[] parts = text.split("\\|", 3);
        if (parts.length < 3) return null;
        String name = parts[0].trim(), genre = parts[1].trim(), url = parts[2].trim();
        if (name.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        return new Station(OWN, name, genre.isEmpty() ? "своя станция" : genre, url);
    }

    /** Writes the file with a ready example, so the radio of another city is one line away. */
    private static void template(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    # Своё радио: одна станция в строке, формат   имя | жанр | адрес потока
                    # Адрес должен вести на MP3-поток (обычно кончается на .mp3 или на /имя_потока).
                    # Уберите # в начале строки, чтобы включить пример:
                    # Радио города | разговорное | http://example.com:8000/live.mp3
                    # Моя волна | музыка | http://example.com:8000/stream
                    """, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException failure) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot create radio.txt", failure);
        }
    }

    public static Path file() { return CustomSounds.media().resolve("radio.txt"); }

    /** The whole list: built-in stations plus the ones from radio.txt, reread when the file changes. */
    public static List<Station> stations() {
        List<Station> known = list;
        long now = System.currentTimeMillis();
        if (known != null && now - listTime <= 5000) return known;
        long stamp = -1;
        try { stamp = Files.isRegularFile(file()) ? Files.getLastModifiedTime(file()).toMillis() : -2; } catch (IOException ignored) { }
        if (known == null || stamp != fileStamp) { known = list = reload(); }
        listTime = now;
        return known;
    }

    /** Forgets the cached list, so the next call rereads radio.txt (the button in the menu). */
    public static void refresh() { list = null; fileStamp = Long.MIN_VALUE; }

    public static int count() { return stations().size(); }
    public static Station station(int i) {
        List<Station> all = stations();
        return all.get(Math.floorMod(i, all.size()));
    }
    public static String name(int i) { return i >= 0 && i < count() ? stations().get(i).name() : ""; }
    public static String genre(int i) { return i >= 0 && i < count() ? stations().get(i).genre() : ""; }
    public static String group(int i) { return i >= 0 && i < count() ? stations().get(i).group() : OWN; }
    public static String url(int i) { return i >= 0 && i < count() ? stations().get(i).url() : ""; }

    /** A stream as a playlist entry, so the player, the HUD and the player screen show it like any other track. */
    public static MusicPlayer.Track track(int i) {
        Station station = station(i);
        return new MusicPlayer.Track(Path.of("lavavisual-radio", station.name() + ".stream"), station.name(), station.name(),
                "интернет-радио · " + station.genre(), 0, "", "поток");
    }

    /** Tunes in to station i: the MP3 frames are decoded as they arrive, so the sound starts within a second. */
    public static Decoders.Source open(int i) throws IOException {
        Station station = station(i);
        HttpURLConnection connection = (HttpURLConnection) new URL(station.url()).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "LavaVisual (Minecraft mod)");
        connection.setRequestProperty("Accept", "*/*");
        // No titles inside the MP3 frames: they would sit right among the audio and trip the decoder. The title is
        // read by nowPlaying() over a separate connection instead.
        connection.setRequestProperty("Icy-MetaData", "0");
        InputStream in = new BufferedInputStream(connection.getInputStream(), 1 << 16);
        try {
            return Decoders.stream(in, station.name());
        } catch (IOException failure) {
            try { in.close(); } catch (IOException ignored) { }
            throw failure;
        } catch (RuntimeException | LinkageError failure) {
            try { in.close(); } catch (IOException ignored) { }
            throw new IOException(failure.getMessage() == null ? "поток не читается" : failure.getMessage());
        }
    }

    // ------------------------------------------------------------------ what is on air right now

    private static volatile String title = "";
    private static volatile int titleIndex = -1;
    private static volatile long titleNext;
    private static volatile boolean titleBusy;

    /** The show or the song of station i, read from the ICY title of its stream (20 s cache), or "". */
    public static String nowPlaying(int i) {
        if (i < 0 || i >= count()) return "";
        if (titleIndex != i) { titleIndex = i; title = ""; titleNext = 0; }
        if (!titleBusy && System.currentTimeMillis() >= titleNext) {
            titleBusy = true;
            String url = stations().get(i).url();
            Thread thread = new Thread(() -> readTitle(i, url), "LavaVisual radio title");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        return title;
    }

    /** Reads one metadata block over a plain second connection, so the audio is never disturbed. */
    private static void readTitle(int index, String url) {
        long retry = System.currentTimeMillis() + 60000;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "LavaVisual (Minecraft mod)");
            connection.setRequestProperty("Icy-MetaData", "1");
            int interval = connection.getHeaderFieldInt("icy-metaint", -1);
            if (interval > 0) {
                InputStream in = new BufferedInputStream(connection.getInputStream(), 1 << 14);
                try {
                    byte[] skip = new byte[1 << 12];
                    for (int left = interval; left > 0; ) {
                        int n = in.read(skip, 0, Math.min(left, skip.length));
                        if (n < 0) return;
                        left -= n;
                    }
                    int length = in.read();
                    if (length > 0) {
                        byte[] block = new byte[Math.min(4080, length * 16)];
                        int read = 0;
                        while (read < block.length) {
                            int n = in.read(block, read, block.length - read);
                            if (n < 0) break;
                            read += n;
                        }
                        String value = streamTitle(new String(block, 0, read, StandardCharsets.UTF_8));
                        if (!value.isEmpty() && index == titleIndex) title = value;
                        retry = System.currentTimeMillis() + 20000;
                    }
                } finally {
                    in.close();
                }
            } else retry = System.currentTimeMillis() + 300000;   // the station does not announce its titles
        } catch (IOException | RuntimeException ignored) {
            // A title is a nice thing to have and never a reason to fail; the next attempt comes in a minute.
        } finally {
            titleNext = retry;
            titleBusy = false;
        }
    }

    /** StreamTitle='Исполнитель - Песня'; → Исполнитель - Песня */
    private static String streamTitle(String block) {
        int at = block.indexOf("StreamTitle=");
        if (at < 0) return "";
        String value = block.substring(at + "StreamTitle=".length()).trim();
        if (value.startsWith("'")) value = value.substring(1);
        int end = value.indexOf(';');
        if (end >= 0) value = value.substring(0, end);
        return value.replace("'", "").trim();
    }

    /** Checks the station list: names, groups and addresses have to make sense. Reported by the CI smoke test. */
    public static String selfTest() {
        List<Station> all = stations();
        int talk = 0, local = 0, music = 0, own = 0;
        for (Station station : all) {
            if (station.name().isBlank() || station.genre().isBlank()) return "у станции нет имени или жанра";
            if (!station.url().startsWith("http://") && !station.url().startsWith("https://")) return "неверный адрес: " + station.url();
            switch (station.group()) {
                case TALK -> talk++;
                case LOCAL -> local++;
                case MUSIC -> music++;
                default -> own++;
            }
        }
        if (talk < 6) return "разговорных станций мало: " + talk;
        if (local < 18) return "местных станций мало: " + local;
        if (music < 6) return "музыкальных станций мало: " + music;
        return "ok: " + all.size() + " станций, разговорных " + talk + ", местных " + local + ", музыкальных " + music + ", своих " + own;
    }
}
