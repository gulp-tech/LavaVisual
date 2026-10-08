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
 * as a local file. The list is grouped: talk radio, where the hosts and the guests actually speak; the radio of the
 * player's city (found by the IP address or chosen in the menu, see {@link CityRadio}); music; and the stations of
 * {@code LavaVisual/radio.txt} (one line per station: {@code name | genre | url}), which the mod creates on first use.
 * Only MP3 streams are played, because MP3 is the internet format the mod decodes by itself.
 */
public final class NetRadio {
    private NetRadio() { }

    /** One internet station: the group it is listed under, its name, its genre and the stream address. */
    public record Station(String group, String name, String genre, String url) { }

    public static final String TALK = "Разговорное", MUSIC = "Музыка", OWN = "Свои станции";

    private static volatile List<Station> list;
    private static volatile long listTime = -60000, fileStamp = Long.MIN_VALUE;

    private static List<Station> reload() {
        List<Station> all = new ArrayList<>(CityRadio.national(TALK));
        CityRadio.City city = CityRadio.current();
        if (city != null) all.addAll(CityRadio.of(city));
        all.addAll(CityRadio.national(MUSIC));
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
        CityLocator.ensure();
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
        InputStream raw = connection.getInputStream();
        // A station can still put its titles between the frames: they are cut out, so the decoder gets audio only.
        int metaInterval = connection.getHeaderFieldInt("icy-metaint", 0);
        InputStream in = new BufferedInputStream(metaInterval > 0 ? new IcyStream(raw, metaInterval) : raw, 1 << 16);
        try {
            return Decoders.stream(in);
        } catch (IOException failure) {
            try { in.close(); } catch (IOException ignored) { }
            throw failure;
        } catch (RuntimeException | LinkageError failure) {
            try { in.close(); } catch (IOException ignored) { }
            throw new IOException(failure.getMessage() == null ? "поток не читается" : failure.getMessage());
        }
    }

    /** Cuts the ICY titles out of an MP3 stream: after every {@code interval} bytes of audio a title block follows. */
    private static final class IcyStream extends java.io.FilterInputStream {
        private final int interval;
        private int untilTitle;
        IcyStream(InputStream in, int interval) {
            super(in);
            this.interval = interval;
            this.untilTitle = interval;
        }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 255;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (untilTitle == 0) {
                int blocks = in.read();                 // the title block: its length in 16-byte units
                if (blocks < 0) return -1;
                byte[] title = new byte[blocks * 16];
                for (int at = 0; at < title.length; ) {
                    int k = in.read(title, at, title.length - at);
                    if (k < 0) return -1;
                    at += k;
                }
                untilTitle = interval;
            }
            int n = in.read(b, off, Math.min(len, untilTitle));
            if (n > 0) untilTitle -= n;
            return n;
        }
        @Override public long skip(long n) throws IOException {
            byte[] scratch = new byte[(int) Math.min(Math.max(n, 0), 8192)];
            return scratch.length == 0 ? 0 : Math.max(0, read(scratch, 0, scratch.length));
        }
        @Override public boolean markSupported() { return false; }
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
        int talk = 0, city = 0, music = 0, own = 0;
        for (Station station : all) {
            if (station.name().isBlank() || station.genre().isBlank()) return "у станции нет имени или жанра";
            if (!station.url().startsWith("http://") && !station.url().startsWith("https://")) return "неверный адрес: " + station.url();
            switch (station.group()) {
                case TALK -> talk++;
                case MUSIC -> music++;
                case OWN -> own++;
                default -> city++;
            }
        }
        if (talk < 6) return "разговорных станций мало: " + talk;
        if (music < 6) return "музыкальных станций мало: " + music;
        CityRadio.City here = CityRadio.current();
        return "ok: " + all.size() + " станций, разговорных " + talk + ", радио города " + city + " (" + (here == null ? "город не определён" : here.name()) + "), музыкальных " + music + ", своих " + own;
    }
}
