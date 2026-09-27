package tech.gulp.lavavisual.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Facts about an audio file without decoding it. The format is recognised by the content, not by the extension, so an
 * MP3 renamed to .ogg still works: Ogg Vorbis, Ogg Opus, MP3 (MPEG Layer III) and WAV (PCM / float). Sample rate,
 * channels, duration, title / artist and an embedded cover. Never throws: a broken file has a non-empty problem.
 */
public final class AudioInfo {
    public enum Format {
        VORBIS("Ogg Vorbis"), OPUS("Opus"), MP3("MP3"), WAV("WAV"), UNKNOWN("?");
        public final String label;
        Format(String label) { this.label = label; }
    }
    public static final String EXTENSIONS = ".mp3, .ogg, .opus, .wav";
    public static final AudioInfo INVALID = new AudioInfo(Format.UNKNOWN, 0, 0, 0, 0, "", "", null, "не аудиофайл");

    public final Format format;
    public final int channels, rate;
    public final double seconds;
    /** MP3: where the audio frames start (after an ID3v2 tag). */
    public final long start;
    public final String title, artist, problem;
    public final byte[] cover;

    private AudioInfo(Format format, int channels, int rate, double seconds, long start, String title, String artist, byte[] cover, String problem) {
        this.format = format; this.channels = channels; this.rate = rate; this.seconds = seconds; this.start = start;
        this.title = title; this.artist = artist; this.cover = cover; this.problem = problem;
    }
    public boolean playable() { return problem.isEmpty(); }

    public static boolean isAudioName(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ogg") || name.endsWith(".oga") || name.endsWith(".opus") || name.endsWith(".mp3") || name.endsWith(".wav");
    }
    public static boolean isAudio(Path file) { return Files.isRegularFile(file) && isAudioName(file); }
    /** File name without its extension. */
    public static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** headLimit bytes are scanned for the headers (covers can be large; listing uses a small limit). */
    public static AudioInfo read(Path file, int headLimit, boolean wantCover) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long length = in.length();
            if (length < 16) return INVALID;
            byte[] head = new byte[(int) Math.min(length, Math.max(headLimit, 4096))];
            in.readFully(head);
            if (starts(head, 0, "OggS")) return ogg(in, length, head, wantCover);
            if (starts(head, 0, "RIFF") && starts(head, 8, "WAVE")) return wav(head, length);
            return mp3(in, length, head, wantCover);
        } catch (IOException | RuntimeException error) {
            return INVALID;
        }
    }

    private static AudioInfo ogg(RandomAccessFile in, long length, byte[] head, boolean wantCover) throws IOException {
        List<byte[]> packets = packets(head, 2);
        if (packets.isEmpty()) return INVALID;
        byte[] id = packets.get(0);
        Format format;
        int channels, rate, preSkip = 0;
        if (id.length >= 16 && id[0] == 1 && starts(id, 1, "vorbis")) {
            format = Format.VORBIS;
            channels = id[11] & 255;
            rate = le32(id, 12);
        } else if (id.length >= 19 && starts(id, 0, "OpusHead")) {
            format = Format.OPUS;
            channels = id[9] & 255;
            preSkip = le16(id, 10);
            rate = 48000;
        } else {
            return new AudioInfo(Format.UNKNOWN, 0, 0, 0, 0, "", "", null, "Ogg с другим кодеком (нужен Vorbis или Opus)");
        }
        String title = "", artist = "";
        byte[] cover = null;
        if (packets.size() > 1) {
            byte[] c = packets.get(1);
            int p = format == Format.VORBIS ? (c.length > 7 && c[0] == 3 && starts(c, 1, "vorbis") ? 7 : -1) : (starts(c, 0, "OpusTags") ? 8 : -1);
            if (p > 0 && p + 4 <= c.length) {
                int vendor = le32(c, p);
                p += 4 + Math.max(0, vendor);
                int count = p + 4 <= c.length ? le32(c, p) : 0;
                p += 4;
                for (int i = 0; i < count && p + 4 <= c.length; i++) {
                    int len = le32(c, p);
                    p += 4;
                    if (len < 0 || p + len > c.length) break;
                    String entry = new String(c, p, len, StandardCharsets.UTF_8);
                    p += len;
                    int eq = entry.indexOf('=');
                    if (eq <= 0) continue;
                    String key = entry.substring(0, eq).toUpperCase(Locale.ROOT), value = entry.substring(eq + 1).trim();
                    if (key.equals("TITLE") && title.isEmpty()) title = value;
                    else if (key.equals("ARTIST") && artist.isEmpty()) artist = value;
                    else if (wantCover && cover == null && key.equals("METADATA_BLOCK_PICTURE")) cover = picture(value);
                    else if (wantCover && cover == null && key.equals("COVERART")) cover = base64(value);
                }
            }
        }
        byte[] tail = new byte[(int) Math.min(length, 65536)];
        in.seek(length - tail.length);
        in.readFully(tail);
        long granule = -1;
        for (int i = tail.length - 27; i >= 0; i--)
            if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S') { granule = le64(tail, i + 6); break; }
        double seconds = granule > preSkip && rate > 0 ? (granule - preSkip) / (double) rate : 0;
        String problem = rate <= 0 || channels < 1 ? "повреждённый заголовок"
                : format == Format.OPUS && channels > 2 ? "Opus с " + channels + " каналами (нужно моно или стерео)" : "";
        return new AudioInfo(format, Math.min(channels, 2), rate, seconds, 0, title, artist, cover, problem);
    }

    private static AudioInfo wav(byte[] d, long length) {
        int p = 12, tag = 0, channels = 0, rate = 0, bits = 0;
        long data = -1;
        while (p + 8 <= d.length) {
            int size = le32(d, p + 4), body = p + 8;
            if (starts(d, p, "fmt ") && size >= 16 && body + 16 <= d.length) {
                tag = le16(d, body);
                channels = le16(d, body + 2);
                rate = le32(d, body + 4);
                bits = le16(d, body + 14);
                if (tag == 0xFFFE && size >= 26 && body + 26 <= d.length) tag = le16(d, body + 24);
            } else if (starts(d, p, "data")) {
                data = size <= 0 ? length - body : Math.min(size & 0xFFFFFFFFL, length - body);
                break;
            }
            if (size < 0) break;
            p = body + size + (size & 1);
        }
        if (rate <= 0 || channels < 1 || bits <= 0) return new AudioInfo(Format.WAV, 0, 0, 0, 0, "", "", null, "WAV без заголовка fmt");
        boolean pcm = tag == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32), floats = tag == 3 && bits == 32;
        double seconds = data > 0 ? data / (double) (channels * (bits / 8)) / rate : 0;
        String problem = pcm || floats ? "" : "сжатый WAV (нужен обычный PCM)";
        return new AudioInfo(Format.WAV, Math.min(channels, 2), rate, seconds, 0, "", "", null, problem);
    }

    private static AudioInfo mp3(RandomAccessFile in, long length, byte[] head, boolean wantCover) throws IOException {
        long start = 0;
        String title = "", artist = "";
        byte[] cover = null;
        if (starts(head, 0, "ID3") && head.length >= 10) {
            int version = head[3] & 255, flags = head[5] & 255;
            long size = syncsafe(head, 6);
            start = 10 + size + ((flags & 0x10) != 0 ? 10 : 0);
            boolean unsynced = (flags & 0x80) != 0 && version < 4;
            int end = (int) Math.min(head.length, 10 + size);
            int p = 10;
            if ((flags & 0x40) != 0 && version == 3 && p + 4 <= end) p += 4 + be32(head, p); // extended header
            else if ((flags & 0x40) != 0 && version == 4 && p + 4 <= end) p += (int) syncsafe(head, p);
            while (!unsynced && version >= 2 && version <= 4) {
                int idLen = version == 2 ? 3 : 4, headerLen = version == 2 ? 6 : 10;
                if (p + headerLen > end || head[p] == 0) break;
                String frame = new String(head, p, idLen, StandardCharsets.ISO_8859_1);
                int size2 = version == 2 ? (head[p + 3] & 255) << 16 | (head[p + 4] & 255) << 8 | (head[p + 5] & 255)
                        : version == 4 ? (int) syncsafe(head, p + 4) : be32(head, p + 4);
                int body = p + headerLen;
                if (size2 <= 0 || body + size2 > end) break;
                switch (frame) {
                    case "TIT2", "TT2" -> { if (title.isEmpty()) title = text(head, body, size2); }
                    case "TPE1", "TP1" -> { if (artist.isEmpty()) artist = text(head, body, size2); }
                    case "APIC", "PIC" -> { if (wantCover && cover == null) cover = apic(head, body, size2, version == 2); }
                    default -> { }
                }
                p = body + size2;
            }
        }
        // First MPEG audio frame after the tag, confirmed by the frame that follows it.
        byte[] buf;
        int base;
        if (start + 8192 <= head.length) { buf = head; base = (int) start; }
        else {
            if (start >= length) return INVALID;
            buf = new byte[(int) Math.min(length - start, 1 << 17)];
            in.seek(start);
            in.readFully(buf);
            base = 0;
        }
        int limit = Math.min(buf.length - 4, base + (1 << 16));
        for (int p = base; p < limit; p++) {
            int[] h = header(buf, p);
            if (h == null) continue;
            int next = p + h[3];
            if (next + 4 <= buf.length && header(buf, next) == null) continue;
            int channels = h[2], rate = h[1], samples = h[4];
            long frameStart = start + (p - base);
            double seconds = 0;
            boolean mpeg1 = h[5] == 3;
            int side = mpeg1 ? (channels == 1 ? 17 : 32) : (channels == 1 ? 9 : 17);
            int xing = p + 4 + side;
            if (xing + 12 <= buf.length && (starts(buf, xing, "Xing") || starts(buf, xing, "Info")) && (be32(buf, xing + 4) & 1) != 0)
                seconds = (be32(buf, xing + 8) & 0xFFFFFFFFL) * samples / (double) rate;
            else if (p + 36 + 18 <= buf.length && starts(buf, p + 36, "VBRI"))
                seconds = (be32(buf, p + 36 + 14) & 0xFFFFFFFFL) * samples / (double) rate;
            else if (h[0] > 0)
                seconds = (length - frameStart) * 8.0 / (h[0] * 1000.0);
            return new AudioInfo(Format.MP3, channels, rate, seconds, frameStart, title, artist, cover, "");
        }
        return INVALID;
    }
    private static final int[][] BITRATES = {
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}, // MPEG-1 Layer III
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}};    // MPEG-2 / 2.5 Layer III
    private static final int[] RATES = {44100, 48000, 32000};
    /** Layer III frame header at p: {kbps, rate, channels, frame bytes, samples per frame, version}, or null. */
    static int[] header(byte[] b, int p) {
        if (p + 4 > b.length || (b[p] & 255) != 0xFF || (b[p + 1] & 0xE0) != 0xE0) return null;
        int version = (b[p + 1] >> 3) & 3, layer = (b[p + 1] >> 1) & 3;
        int bitrate = (b[p + 2] >> 4) & 15, rateIndex = (b[p + 2] >> 2) & 3, padding = (b[p + 2] >> 1) & 1;
        if (version == 1 || layer != 1 || bitrate == 0 || bitrate == 15 || rateIndex == 3) return null;
        int rate = RATES[rateIndex] >> (version == 3 ? 0 : version == 2 ? 1 : 2);
        int kbps = BITRATES[version == 3 ? 0 : 1][bitrate];
        int samples = version == 3 ? 1152 : 576;
        int bytes = samples / 8 * kbps * 1000 / rate + padding;
        int channels = ((b[p + 3] >> 6) & 3) == 3 ? 1 : 2;
        return bytes < 24 ? null : new int[] {kbps, rate, channels, bytes, samples, version};
    }
    private static String text(byte[] b, int p, int len) {
        if (len < 2) return "";
        Charset charset = switch (b[p]) { case 1 -> StandardCharsets.UTF_16; case 2 -> StandardCharsets.UTF_16BE; case 3 -> StandardCharsets.UTF_8; default -> StandardCharsets.ISO_8859_1; };
        String value = new String(b, p + 1, len - 1, charset);
        int zero = value.indexOf('\0');
        return (zero >= 0 ? value.substring(0, zero) : value).trim();
    }
    /** ID3 picture frame: encoding, mime (or 3-letter format in v2.2), picture type, description, image bytes. */
    private static byte[] apic(byte[] b, int p, int len, boolean v22) {
        int end = p + len, encoding = b[p];
        int q = p + 1;
        if (v22) q += 3;
        else { while (q < end && b[q] != 0) q++; q++; }
        q++; // picture type
        boolean wide = encoding == 1 || encoding == 2;
        if (wide) { while (q + 1 < end && (b[q] != 0 || b[q + 1] != 0)) q += 2; q += 2; }
        else { while (q < end && b[q] != 0) q++; q++; }
        if (q >= end) return null;
        byte[] image = new byte[end - q];
        System.arraycopy(b, q, image, 0, image.length);
        return image;
    }

    /** Reassembles the first packets from Ogg pages (lacing values of 255 continue a packet). */
    private static List<byte[]> packets(byte[] d, int max) {
        List<byte[]> out = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        int p = 0;
        while (p + 27 <= d.length && out.size() < max) {
            if (!starts(d, p, "OggS")) break;
            int segments = d[p + 26] & 255, q = p + 27 + segments;
            if (q > d.length) break;
            for (int i = 0; i < segments; i++) {
                int len = d[p + 27 + i] & 255;
                int take = Math.min(len, d.length - q);
                if (take > 0) current.write(d, q, take);
                q += len;
                if (take < len) { out.add(current.toByteArray()); return out; } // truncated: keep what we have
                if (len < 255) {
                    out.add(current.toByteArray());
                    current.reset();
                    if (out.size() >= max) return out;
                }
            }
            p = q;
        }
        if (current.size() > 0 && out.size() < max) out.add(current.toByteArray());
        return out;
    }
    /** FLAC picture block (base64): type, mime, description, 4 ints, then the image bytes. */
    private static byte[] picture(String value) {
        byte[] b = base64(value);
        if (b == null || b.length < 32) return null;
        int p = 4, mime = be32(b, p);
        p += 4 + mime;
        if (p + 4 > b.length) return null;
        int desc = be32(b, p);
        p += 4 + desc + 16;
        if (p + 4 > b.length) return null;
        int len = be32(b, p);
        p += 4;
        if (len <= 0 || p + len > b.length) return null;
        byte[] image = new byte[len];
        System.arraycopy(b, p, image, 0, len);
        return image;
    }
    private static byte[] base64(String value) {
        try { return Base64.getMimeDecoder().decode(value); } catch (IllegalArgumentException e) { return null; }
    }
    static boolean starts(byte[] b, int p, String ascii) {
        if (p < 0 || p + ascii.length() > b.length) return false;
        for (int i = 0; i < ascii.length(); i++) if (b[p + i] != (byte) ascii.charAt(i)) return false;
        return true;
    }
    private static long syncsafe(byte[] b, int p) { return (b[p] & 127L) << 21 | (b[p + 1] & 127L) << 14 | (b[p + 2] & 127L) << 7 | (b[p + 3] & 127L); }
    static int le16(byte[] b, int p) { return (b[p] & 255) | (b[p + 1] & 255) << 8; }
    static int le32(byte[] b, int p) { return (b[p] & 255) | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16 | (b[p + 3] & 255) << 24; }
    private static int be32(byte[] b, int p) { return (b[p] & 255) << 24 | (b[p + 1] & 255) << 16 | (b[p + 2] & 255) << 8 | (b[p + 3] & 255); }
    private static long le64(byte[] b, int p) { return (le32(b, p) & 0xFFFFFFFFL) | ((long) le32(b, p + 4) << 32); }
}
