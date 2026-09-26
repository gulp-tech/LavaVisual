package tech.gulp.lavavisual.audio;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import tech.gulp.lavavisual.LavaVisual;

/**
 * PCM sources for the music player and the sound clips. Ogg Vorbis is streamed by the STB decoder that ships with the
 * game; MP3 (JavaMP3), Opus (Concentus, 48 kHz) and WAV are decoded into memory by a low-priority background thread
 * while the track already plays, so seeking is just a jump inside the decoded audio.
 */
public final class Decoders {
    private Decoders() { }

    /** Interleaved signed 16-bit PCM, one or two channels. */
    public interface Source extends AutoCloseable {
        int channels();
        int rate();
        /** Total frames, or -1 while unknown. */
        long length();
        /** Frames copied into out; 0 when the background decoder has not reached this point yet, -1 at the end. */
        int read(short[] out, int maxFrames);
        void seek(long frame);
        /** Why decoding stopped early, or "". */
        String error();
        @Override void close();
    }

    public static Source open(Path file, AudioInfo info, boolean background) throws IOException {
        if (!info.playable()) throw new IOException(info.problem);
        byte[] bytes = Files.readAllBytes(file);
        return switch (info.format) {
            case VORBIS -> new Vorbis(bytes);
            case OPUS -> Memory.start(new Opus(bytes), info, background);
            case MP3 -> Memory.start(new Mp3(bytes, (int) Math.min(info.start, bytes.length)), info, background);
            case WAV -> Memory.start(new Wav(bytes), info, background);
            case UNKNOWN -> throw new IOException("формат не распознан");
        };
    }

    public record Pcm(short[] data, int frames, int channels, int rate) { }
    /** Whole file (short sounds), at most maxSeconds. */
    public static Pcm decodeAll(Path file, int maxSeconds) throws IOException {
        AudioInfo info = AudioInfo.read(file, 1 << 20, false);
        try (Source source = open(file, info, false)) {
            int ch = source.channels(), cap = maxSeconds * source.rate(), frames = 0;
            short[] all = new short[Math.min(cap, 1 << 16) * ch], chunk = new short[4096 * ch];
            while (frames < cap) {
                int n = source.read(chunk, Math.min(4096, cap - frames));
                if (n <= 0) break;
                if ((frames + n) * ch > all.length) all = Arrays.copyOf(all, Math.max(all.length * 2, (frames + n) * ch));
                System.arraycopy(chunk, 0, all, frames * ch, n * ch);
                frames += n;
            }
            if (frames == 0) throw new IOException(source.error().isEmpty() ? "пустой файл" : source.error());
            return new Pcm(all, frames, ch, source.rate());
        }
    }

    /** Ogg Vorbis through STB, decoded chunk by chunk as it plays. */
    private static final class Vorbis implements Source {
        private ByteBuffer data;
        private ShortBuffer scratch;
        private long handle;
        private int channels, rate;
        private long length;

        Vorbis(byte[] bytes) throws IOException {
            data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
                if (handle == 0) { close(); throw new IOException("Vorbis не читается (код " + error.get(0) + ")"); }
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = Math.min(2, info.channels());
                rate = info.sample_rate();
            }
            length = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
        }
        @Override public int channels() { return channels; }
        @Override public int rate() { return rate; }
        @Override public long length() { return length > 0 ? length : -1; }
        @Override public String error() { return ""; }
        @Override public int read(short[] out, int maxFrames) {
            if (handle == 0) return -1;
            int samples = maxFrames * channels;
            if (scratch == null || scratch.capacity() < samples) {
                if (scratch != null) MemoryUtil.memFree(scratch);
                scratch = MemoryUtil.memAllocShort(samples);
            }
            scratch.clear().limit(samples);
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, scratch);
            if (frames <= 0) return -1;
            scratch.get(0, out, 0, frames * channels);
            return frames;
        }
        @Override public void seek(long frame) {
            if (handle != 0) STBVorbis.stb_vorbis_seek(handle, (int) Math.clamp(frame, 0, Math.max(0, length - 1)));
        }
        @Override public void close() {
            if (handle != 0) { STBVorbis.stb_vorbis_close(handle); handle = 0; }
            if (data != null) { MemoryUtil.memFree(data); data = null; }
            if (scratch != null) { MemoryUtil.memFree(scratch); scratch = null; }
        }
    }

    /** A decoder that pushes all of its PCM into a Memory source. */
    private abstract static class Producer {
        int channels, rate;
        abstract void run(Memory out) throws IOException;
    }

    /** Decoded audio kept in 16k-frame blocks; the decoder thread appends, the player reads behind it. */
    private static final class Memory implements Source {
        private static final int BLOCK = 1 << 14;
        private final int channels, rate;
        private final long expected;
        private final List<short[]> blocks = Collections.synchronizedList(new ArrayList<>());
        private long written, pos;
        private volatile long decoded;
        private volatile boolean done, closed;
        private volatile String error = "";

        private Memory(int channels, int rate, long expected) { this.channels = channels; this.rate = rate; this.expected = expected; }

        static Memory start(Producer producer, AudioInfo info, boolean background) throws IOException {
            if (producer.channels < 1 || producer.channels > 2 || producer.rate <= 0) throw new IOException("неверные параметры звука");
            Memory memory = new Memory(producer.channels, producer.rate, info.seconds > 0 ? (long) (info.seconds * producer.rate) : -1);
            Runnable body = () -> {
                try { producer.run(memory); }
                catch (IOException | RuntimeException | LinkageError failure) {
                    memory.error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                    if (!memory.closed) LavaVisual.LOGGER.warn("LavaVisual: decoding stopped: {}", memory.error);
                } finally { memory.done = true; }
            };
            if (!background) body.run();
            else {
                Thread thread = new Thread(body, "LavaVisual decoder");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }
            return memory;
        }
        void push(short[] pcm, int offset, int frames) {
            while (frames > 0 && !closed) {
                int index = (int) (written / BLOCK), at = (int) (written % BLOCK);
                if (index >= blocks.size()) blocks.add(new short[BLOCK * channels]);
                short[] block = blocks.get(index);
                int n = Math.min(frames, BLOCK - at);
                System.arraycopy(pcm, offset, block, at * channels, n * channels);
                offset += n * channels;
                frames -= n;
                written += n;
                decoded = written;
            }
        }
        @Override public int channels() { return channels; }
        @Override public int rate() { return rate; }
        @Override public long length() { return done ? decoded : expected; }
        @Override public String error() { return error; }
        @Override public int read(short[] out, int maxFrames) {
            long available = decoded - pos;
            if (available <= 0) return done ? -1 : 0;
            int n = (int) Math.min(maxFrames, available), copied = 0;
            while (copied < n) {
                int index = (int) (pos / BLOCK), at = (int) (pos % BLOCK);
                short[] block = blocks.get(index);
                int k = Math.min(n - copied, BLOCK - at);
                System.arraycopy(block, at * channels, out, copied * channels, k * channels);
                copied += k;
                pos += k;
            }
            return n;
        }
        @Override public void seek(long frame) { pos = Math.max(0, done ? Math.min(frame, decoded) : frame); }
        @Override public void close() { closed = true; blocks.clear(); }
    }

    /** MP3 through JavaMP3 (16-bit little-endian PCM out). The ID3 tag is skipped so a big cover cannot confuse it. */
    private static final class Mp3 extends Producer {
        private final fr.delthas.javamp3.Sound sound;
        Mp3(byte[] bytes, int start) throws IOException {
            sound = new fr.delthas.javamp3.Sound(new BufferedInputStream(new ByteArrayInputStream(bytes, start, bytes.length - start), 1 << 16));
            channels = sound.isStereo() ? 2 : 1;
            rate = sound.getSamplingFrequency();
        }
        @Override void run(Memory out) throws IOException {
            byte[] buf = new byte[1 << 15];
            short[] pcm = new short[buf.length / 2];
            int carry = 0, frameBytes = 2 * channels;
            try (fr.delthas.javamp3.Sound in = sound) {
                while (!out.closed) {
                    int n = in.read(buf, carry, buf.length - carry);
                    if (n < 0) break;
                    n += carry;
                    int frames = n / frameBytes;
                    for (int i = 0; i < frames * channels; i++) pcm[i] = (short) ((buf[2 * i] & 255) | buf[2 * i + 1] << 8);
                    out.push(pcm, 0, frames);
                    carry = n - frames * frameBytes;
                    if (carry > 0) System.arraycopy(buf, frames * frameBytes, buf, 0, carry);
                }
            }
        }
    }

    /** Ogg Opus: pages are split into packets here, Concentus decodes them. Pre-skip and output gain are applied. */
    private static final class Opus extends Producer {
        private final byte[] data;
        private final int preSkip;
        private final float gain;
        Opus(byte[] bytes) throws IOException {
            data = bytes;
            byte[] head = new Packets(bytes).next();
            if (head == null || head.length < 19 || !AudioInfo.starts(head, 0, "OpusHead")) throw new IOException("не Opus");
            channels = head[9] & 255;
            if (channels < 1 || channels > 2) throw new IOException("Opus с " + channels + " каналами");
            rate = 48000;
            preSkip = AudioInfo.le16(head, 10);
            gain = (float) Math.pow(10, (short) AudioInfo.le16(head, 16) / (20.0 * 256));
        }
        @Override void run(Memory out) throws IOException {
            Packets packets = new Packets(data);
            packets.next(); // OpusHead
            packets.next(); // OpusTags
            io.github.jaredmdobson.concentus.OpusDecoder decoder;
            try { decoder = new io.github.jaredmdobson.concentus.OpusDecoder(48000, channels); }
            catch (io.github.jaredmdobson.concentus.OpusException error) { throw new IOException("Opus: " + error.getMessage()); }
            short[] pcm = new short[5760 * channels];
            int skip = preSkip, bad = 0;
            byte[] packet;
            while (!out.closed && (packet = packets.next()) != null) {
                if (packet.length == 0) continue;
                int frames;
                try { frames = decoder.decode(packet, 0, packet.length, pcm, 0, 5760, false); }
                catch (io.github.jaredmdobson.concentus.OpusException error) {
                    if (++bad > 50) throw new IOException("Opus повреждён");
                    continue;
                }
                if (frames <= 0) continue;
                int from = Math.min(skip, frames);
                skip -= from;
                if (gain != 1f)
                    for (int i = from * channels; i < frames * channels; i++) pcm[i] = (short) Math.clamp(Math.round(pcm[i] * (double) gain), -32768, 32767);
                if (frames > from) out.push(pcm, from * channels, frames - from);
            }
        }
    }

    /** Plain RIFF WAV: 8/16/24/32-bit PCM or 32-bit float; more than two channels keep the first two. */
    private static final class Wav extends Producer {
        private final byte[] d;
        private int tag, bits, block, dataAt, dataLength;
        Wav(byte[] bytes) throws IOException {
            d = bytes;
            int p = 12, source = 0;
            while (p + 8 <= d.length) {
                int size = AudioInfo.le32(d, p + 4), body = p + 8;
                if (AudioInfo.starts(d, p, "fmt ") && size >= 16 && body + 16 <= d.length) {
                    tag = AudioInfo.le16(d, body);
                    source = AudioInfo.le16(d, body + 2);
                    rate = AudioInfo.le32(d, body + 4);
                    block = AudioInfo.le16(d, body + 12);
                    bits = AudioInfo.le16(d, body + 14);
                    if (tag == 0xFFFE && size >= 26 && body + 26 <= d.length) tag = AudioInfo.le16(d, body + 24);
                } else if (AudioInfo.starts(d, p, "data")) {
                    dataAt = body;
                    dataLength = size <= 0 ? d.length - body : Math.min(size, d.length - body);
                    break;
                }
                if (size < 0) break;
                p = body + size + (size & 1);
            }
            if (dataAt == 0 || rate <= 0 || source < 1) throw new IOException("WAV без данных");
            if (!(tag == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32)) && !(tag == 3 && bits == 32)) throw new IOException("сжатый WAV");
            channels = Math.min(2, source);
            if (block <= 0) block = source * bits / 8;
        }
        @Override void run(Memory out) {
            int frames = dataLength / block, step = bits / 8;
            short[] pcm = new short[4096 * channels];
            for (int f = 0; f < frames && !out.closed; ) {
                int n = Math.min(4096, frames - f);
                for (int i = 0; i < n; i++) {
                    int at = dataAt + (f + i) * block;
                    for (int c = 0; c < channels; c++) pcm[i * channels + c] = sample(at + c * step);
                }
                out.push(pcm, 0, n);
                f += n;
            }
        }
        private short sample(int p) {
            return switch (bits) {
                case 8 -> (short) (((d[p] & 255) - 128) << 8);
                case 16 -> (short) ((d[p] & 255) | d[p + 1] << 8);
                case 24 -> (short) ((d[p + 1] & 255) | d[p + 2] << 8);
                default -> tag == 3 ? (short) Math.clamp(Math.round(Float.intBitsToFloat(AudioInfo.le32(d, p)) * 32767.0), -32768, 32767)
                        : (short) ((d[p + 2] & 255) | d[p + 3] << 8);
            };
        }
    }

    /** Ogg packet reader over a whole file in memory (resyncs on "OggS"; a packet may span pages). */
    private static final class Packets {
        private final byte[] d;
        private final ByteArrayOutputStream packet = new ByteArrayOutputStream();
        private int page = -1, segment, segments, body;
        Packets(byte[] d) { this.d = d; }
        byte[] next() {
            while (true) {
                if (page < 0 || segment >= segments) {
                    int p = page < 0 ? 0 : body;
                    while (p + 27 <= d.length && !AudioInfo.starts(d, p, "OggS")) p++;
                    if (p + 27 > d.length) return null;
                    page = p;
                    segments = d[p + 26] & 255;
                    segment = 0;
                    body = p + 27 + segments;
                    if (body > d.length) return null;
                }
                int len = d[page + 27 + segment] & 255;
                segment++;
                int take = Math.min(len, d.length - body);
                if (take > 0) packet.write(d, body, take);
                body += len;
                if (take < len) return null;
                if (len < 255) {
                    byte[] out = packet.toByteArray();
                    packet.reset();
                    return out;
                }
            }
        }
    }
}
