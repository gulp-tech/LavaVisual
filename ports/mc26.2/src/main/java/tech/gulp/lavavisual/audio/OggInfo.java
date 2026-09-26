package tech.gulp.lavavisual.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Reads Ogg Vorbis headers without decoding: codec check (Opus-in-.ogg is rejected, Minecraft and STB only decode
 * Vorbis), sample rate, channels, duration from the last page's granule position, TITLE / ARTIST and an embedded
 * cover (METADATA_BLOCK_PICTURE or the older COVERART). Never throws: a broken file is simply not playable.
 */
public final class OggInfo {
    public static final OggInfo INVALID = new OggInfo(false, 0, 0, 0, "", "", null);
    public final boolean vorbis;
    public final int channels, rate;
    public final double seconds;
    public final String title, artist;
    public final byte[] cover;

    private OggInfo(boolean vorbis, int channels, int rate, double seconds, String title, String artist, byte[] cover) {
        this.vorbis = vorbis; this.channels = channels; this.rate = rate; this.seconds = seconds;
        this.title = title; this.artist = artist; this.cover = cover;
    }
    public boolean playable() { return vorbis && rate > 0 && channels >= 1 && channels <= 2; }

    /** headLimit bytes are scanned for the headers (covers can be large; listing uses a small limit). */
    public static OggInfo read(Path file, int headLimit, boolean wantCover) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long length = in.length();
            if (length < 58) return INVALID;
            byte[] head = new byte[(int) Math.min(length, headLimit)];
            in.readFully(head);
            List<byte[]> packets = packets(head, 2);
            if (packets.isEmpty()) return INVALID;
            byte[] id = packets.get(0);
            if (id.length < 16 || id[0] != 1 || !new String(id, 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) return INVALID;
            int channels = id[11] & 255, rate = le32(id, 12);
            String title = "", artist = "";
            byte[] cover = null;
            if (packets.size() > 1) {
                byte[] c = packets.get(1);
                if (c.length > 11 && c[0] == 3) {
                    int p = 7, vendor = le32(c, p);
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
                        else if (wantCover && cover == null && key.equals("COVERART")) cover = decode(value);
                    }
                }
            }
            byte[] tail = new byte[(int) Math.min(length, 65536)];
            in.seek(length - tail.length);
            in.readFully(tail);
            long granule = -1;
            for (int i = tail.length - 27; i >= 0; i--)
                if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S') { granule = le64(tail, i + 6); break; }
            double seconds = granule > 0 && rate > 0 ? granule / (double) rate : 0;
            return new OggInfo(true, channels, rate, seconds, title, artist, cover);
        } catch (IOException | RuntimeException error) {
            return INVALID;
        }
    }

    /** Reassembles the first packets from Ogg pages (lacing values of 255 continue a packet). */
    private static List<byte[]> packets(byte[] d, int max) {
        List<byte[]> out = new ArrayList<>();
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        int p = 0;
        while (p + 27 <= d.length && out.size() < max) {
            if (d[p] != 'O' || d[p + 1] != 'g' || d[p + 2] != 'g' || d[p + 3] != 'S') break;
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
    private static byte[] picture(String base64) {
        byte[] b = decode(base64);
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
    private static byte[] decode(String base64) {
        try { return Base64.getMimeDecoder().decode(base64); } catch (IllegalArgumentException e) { return null; }
    }
    private static int le32(byte[] b, int p) { return (b[p] & 255) | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16 | (b[p + 3] & 255) << 24; }
    private static int be32(byte[] b, int p) { return (b[p] & 255) << 24 | (b[p + 1] & 255) << 16 | (b[p + 2] & 255) << 8 | (b[p + 3] & 255); }
    private static long le64(byte[] b, int p) { return (le32(b, p) & 0xFFFFFFFFL) | ((long) le32(b, p + 4) << 32); }
}
