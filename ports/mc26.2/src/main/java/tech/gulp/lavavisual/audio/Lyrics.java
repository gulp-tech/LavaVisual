package tech.gulp.lavavisual.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.effects.CustomSounds;

/**
 * Lyrics shown on screen while a track plays. Where they come from, in order:
 * 1. "song.lrc" next to "song.mp3" (the player's own file, always wins);
 * 2. a copy saved earlier in the config folder;
 * 3. the open lyrics database lrclib.net, asked by title, artist and length, in the background, one request at a time.
 * Only the title, artist and length of the track are sent. Nothing is bundled with the mod.
 */
public final class Lyrics {
    public record Line(double at, String text) { }

    private record Answer(int status, String body, long waitMs) { }

    /** Marks a lookup that is running; never modified. */
    private static final List<Line> PENDING = new ArrayList<>();
    /** Marks a lookup paused after a rate limit or a network error; never modified. */
    private static final List<Line> RETRY = new ArrayList<>();
    private static final List<Line> NONE = List.of();
    private static final long MAX_BYTES = 256 * 1024;
    private static final int MAX_LINES = 5000;
    private static final long PAUSE_MS = 60_000, MAX_PAUSE_MS = 600_000, GAP_MS = 300;
    private static final String BASE = "https://lrclib.net";
    private static final String AGENT = "LavaVisual/1.0.0 (https://github.com/gulp-tech/LavaVisual)";
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Map<Path, List<Line>> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService FETCHER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "LavaVisual lyrics");
        thread.setDaemon(true);
        return thread;
    });
    /** nanoTime until which online lookups are paused; 0 means not paused. */
    private static volatile long pausedUntil;

    private Lyrics() { }

    /** Timed lines for this track, sorted by time; empty while there are none or while a lookup runs. */
    public static List<Line> of(MusicPlayer.Track track) {
        if (track == null) return NONE;
        Path audio = track.file();
        List<Line> lines = CACHE.get(audio);
        if (lines == RETRY) {
            if (paused()) return NONE;
            CACHE.remove(audio, RETRY);
            lines = null;
        }
        if (lines == null) {
            lines = resolve(track);
            List<Line> won = CACHE.putIfAbsent(audio, lines);
            if (won != null) lines = won;
        }
        return lines == PENDING ? NONE : lines;
    }

    /** Forgets what was read, so edited files and changed settings take effect the next time a track plays. */
    public static void forget() { CACHE.clear(); }

    /** Index of the line on screen at this second, or -1 before the first line. */
    public static int indexAt(List<Line> lines, double seconds) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).at() <= seconds) { found = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return found;
    }

    static Path lyricsFile(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return audio.resolveSibling(base + ".lrc");
    }

    /** Runs on the render thread once per track: only files are checked here, the network is left to the lookup thread. */
    private static List<Line> resolve(MusicPlayer.Track track) {
        List<Line> local = read(lyricsFile(track.file()));
        if (!local.isEmpty()) return local;
        if (!LavaVisualClient.config().lyricsOnline) return NONE;
        Path saved = savedFile(track);
        Path none = saved.resolveSibling(saved.getFileName() + ".none");
        if (Files.isRegularFile(saved)) {
            List<Line> copy = read(saved);
            return copy.isEmpty() ? NONE : copy;
        }
        if (Files.exists(none)) return NONE;
        if (paused()) return RETRY;
        FETCHER.execute(() -> fetch(track, saved, none));
        return PENDING;
    }

    private static Path savedFile(MusicPlayer.Track track) {
        String key = (track.shown() + "|" + track.artist() + "|" + Math.round(track.seconds())).toLowerCase(Locale.ROOT);
        return CustomSounds.root().resolve("lyrics").resolve(sha(key) + ".lrc");
    }

    private static String sha(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** Lookup thread: title and artist first, then a search by title; the result is kept on disk. */
    private static void fetch(MusicPlayer.Track track, Path saved, Path none) {
        String title = track.shown().strip(), artist = track.artist().strip();
        long seconds = Math.round(track.seconds());
        try {
            JsonObject found = null;
            if (!title.isBlank() && !artist.isBlank()) {
                Map<String, String> query = new LinkedHashMap<>();
                query.put("track_name", title);
                query.put("artist_name", artist);
                if (seconds >= 1 && seconds <= 3600) query.put("duration", Long.toString(seconds));
                Answer answer = call("/api/get", query);
                if (answer.status() == 429 || answer.status() == 503) { pause(track, answer); return; }
                if (answer.status() == 200) found = object(answer.body());
            }
            if (found == null && !title.isBlank()) {
                Map<String, String> query = new LinkedHashMap<>();
                query.put("track_name", title);
                if (!artist.isBlank()) query.put("artist_name", artist);
                Answer answer = call("/api/search", query);
                if (answer.status() == 429 || answer.status() == 503) { pause(track, answer); return; }
                if (answer.status() == 200) found = best(answer.body(), seconds, artist.isBlank());
            }
            String synced = found == null ? "" : text(found, "syncedLyrics");
            if (synced.isBlank() || bool(found, "instrumental")) {
                write(none, "");
                CACHE.put(track.file(), NONE);
                return;
            }
            write(saved, synced);
            CACHE.put(track.file(), parse(synced));
        } catch (IOException | RuntimeException error) {
            pause(track, new Answer(0, "", PAUSE_MS));
        }
    }

    private static void pause(MusicPlayer.Track track, Answer answer) {
        long wait = answer.waitMs() > 0 ? Math.min(answer.waitMs(), MAX_PAUSE_MS) : PAUSE_MS;
        pausedUntil = System.nanoTime() + wait * 1_000_000L;
        CACHE.put(track.file(), RETRY);
    }

    private static boolean paused() {
        long until = pausedUntil;
        return until != 0 && System.nanoTime() < until;
    }

    private static Answer call(String path, Map<String, String> query) throws IOException {
        StringBuilder url = new StringBuilder(BASE).append(path).append('?');
        for (Map.Entry<String, String> entry : query.entrySet()) {
            url.append(encode(entry.getKey())).append('=').append(encode(entry.getValue())).append('&');
        }
        url.setLength(url.length() - 1);
        sleep(GAP_MS);
        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", AGENT);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            String body = "";
            try (InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
                if (stream != null) body = new String(stream.readNBytes((int) MAX_BYTES), StandardCharsets.UTF_8);
            }
            long waitMs = 0;
            String retryAfter = connection.getHeaderField("Retry-After");
            if (retryAfter != null) {
                try { waitMs = Long.parseLong(retryAfter.strip()) * 1000; }
                catch (NumberFormatException ignored) { }
            }
            return new Answer(status, body, waitMs);
        } finally {
            connection.disconnect();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void sleep(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("lyrics lookup interrupted", error);
        }
    }

    private static JsonObject object(String body) {
        JsonElement element = JsonParser.parseString(body);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** First search result with synced lyrics whose length matches the file (within 3 s when the length is known). */
    private static JsonObject best(String body, long seconds, boolean artistMissing) {
        JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonArray()) return null;
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            JsonObject record = item.getAsJsonObject();
            if (text(record, "syncedLyrics").isBlank()) continue;
            if (seconds >= 1) {
                if (Math.abs(number(record, "duration") - seconds) > 3) continue;
            } else if (artistMissing) {
                return null; // a title alone with no length is too vague to trust
            }
            return record;
        }
        return null;
    }

    private static String text(JsonObject record, String key) {
        JsonElement value = record.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static double number(JsonObject record, String key) {
        JsonElement value = record.get(key);
        return value == null || value.isJsonNull() ? -1000 : value.getAsDouble();
    }

    private static boolean bool(JsonObject record, String key) {
        JsonElement value = record == null ? null : record.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<Line> read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return NONE;
            return parse(decode(Files.readAllBytes(file)));
        } catch (IOException error) {
            return NONE;
        }
    }

    /** UTF-8 first; older files saved in Windows-1251 (common for Russian text) are read as that. */
    static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            Charset cp1251 = Charset.isSupported("windows-1251") ? Charset.forName("windows-1251") : StandardCharsets.ISO_8859_1;
            text = new String(bytes, cp1251);
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    static List<Line> parse(String text) {
        long offsetMs = 0;
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            Matcher offset = OFFSET.matcher(raw.strip());
            if (offset.find()) {
                try { offsetMs = Long.parseLong(offset.group(1)); } catch (NumberFormatException ignored) { }
                continue;
            }
            Matcher stamp = STAMP.matcher(raw);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (stamp.find()) {
                double minutes = Integer.parseInt(stamp.group(1)), seconds = Integer.parseInt(stamp.group(2));
                String fraction = stamp.group(3);
                double part = fraction == null ? 0 : Integer.parseInt(fraction) / Math.pow(10, fraction.length());
                times.add(minutes * 60 + seconds + part);
                end = stamp.end();
            }
            if (times.isEmpty()) continue;
            String words = WORD_STAMP.matcher(raw.substring(end)).replaceAll("").strip();
            for (double at : times) {
                if (lines.size() >= MAX_LINES) break;
                lines.add(new Line(Math.max(0, at + offsetMs / 1000.0), words));
            }
        }
        lines.sort(Comparator.comparingDouble(Line::at));
        return List.copyOf(lines);
    }
}
