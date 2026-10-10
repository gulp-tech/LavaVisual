package tech.gulp.lavavisual.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics shown on screen while a track plays. The words come from a file the player keeps next to the track:
 * "song.lrc" beside "song.mp3", with [mm:ss.xx] timestamps. The mod downloads and bundles no lyrics.
 * Each file is read once and kept in memory, so the screen only looks up the current line.
 */
public final class Lyrics {
    public record Line(double at, String text) { }

    private static final long MAX_BYTES = 256 * 1024;
    private static final int MAX_LINES = 5000;
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Map<Path, List<Line>> CACHE = new ConcurrentHashMap<>();

    private Lyrics() { }

    /** Timed lines of the track's .lrc file, sorted by time; empty when there is no file or it cannot be used. */
    public static List<Line> of(Path audio) {
        if (audio == null) return List.of();
        return CACHE.computeIfAbsent(audio, Lyrics::load);
    }

    /** Forgets what was read, so an edited file is read again the next time the track plays. */
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

    private static List<Line> load(Path audio) {
        Path file = lyricsFile(audio);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return List.of();
            return parse(decode(Files.readAllBytes(file)));
        } catch (IOException error) {
            return List.of();
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
