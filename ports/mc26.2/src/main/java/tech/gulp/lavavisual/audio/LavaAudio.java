package tech.gulp.lavavisual.audio;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;
import tech.gulp.lavavisual.LavaVisual;

/**
 * Plays the user's own sound files (MP3, Ogg Vorbis, Opus, WAV) directly through Minecraft's OpenAL device. No resource pack and no resource reload: a file dropped into the folder plays immediately,
 * whatever its name (spaces, capitals, Cyrillic). Short clips are decoded once and cached.
 */
public final class LavaAudio {
    /** Uploaded sound; checked = when the file was last seen unchanged (files are looked at every few seconds, not per hit). */
    private static final class Clip {
        final int buffer;
        final long stamp;
        long checked;
        Clip(int buffer, long stamp, long checked) { this.buffer = buffer; this.stamp = stamp; this.checked = checked; }
    }
    private record Decoded(long stamp, Decoders.Pcm pcm) { }
    private static final Map<Path, Clip> CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Sounds decoded ahead of time by the background thread, waiting for their first play. */
    private static final Map<Path, Decoded> DECODED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService WARM = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "LavaVisual sounds");
        thread.setDaemon(true);
        return thread;
    });
    private static final int[] SOURCES = new int[8];
    private static long context;
    private static int last;
    private static boolean unavailable;
    private LavaAudio() { }

    /** True when an OpenAL context exists; forgets every AL object after Minecraft recreated its sound device. */
    public static boolean ready() {
        if (unavailable) return false;
        try {
            long now = ALC10.alcGetCurrentContext();
            if (now == 0) return false;
            if (now != context) { CLIPS.clear(); Arrays.fill(SOURCES, 0); last = 0; context = now; }
            return true;
        } catch (LinkageError error) {
            unavailable = true;
            LavaVisual.LOGGER.warn("LavaVisual: OpenAL is not available, custom sounds are off", error);
            return false;
        }
    }
    public static long context() { return context; }

    /** Plays a short sound at the listener. gain is the final 0..1 volume. */
    public static boolean play(Path file, float gain) {
        if (!ready()) return false;
        try {
            Clip clip = clip(file);
            if (clip == null) return false;
            int source = source();
            if (source == 0) return false;
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, clip.buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
            AL10.alSourcef(source, AL10.AL_GAIN, Math.clamp(gain, 0f, 1f));
            AL10.alSourcePlay(source);
            last = source;
            return AL10.alGetError() == AL10.AL_NO_ERROR;
        } catch (IOException | RuntimeException | LinkageError error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot play {}", file.getFileName(), error);
            return false;
        }
    }
    /** CI: the last clip is playing (or already finished playing a very short file). */
    public static boolean lastStarted() {
        if (last == 0 || !ready()) return false;
        int state = AL10.alGetSourcei(last, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING || state == AL10.AL_STOPPED;
    }
    private static int source() {
        for (int i = 0; i < SOURCES.length; i++) {
            if (SOURCES[i] == 0 || !AL10.alIsSource(SOURCES[i])) {
                AL10.alGetError();
                int created = AL10.alGenSources();
                if (AL10.alGetError() != AL10.AL_NO_ERROR) return 0;
                return SOURCES[i] = created;
            }
            if (AL10.alGetSourcei(SOURCES[i], AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return SOURCES[i];
        }
        return SOURCES[(int) ((System.nanoTime() >>> 10) % SOURCES.length)];
    }
    /** {size, stamp} of a file from one attribute read. */
    private static long[] stamp(Path file) throws IOException {
        var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        return new long[]{attributes.size(), attributes.lastModifiedTime().toMillis() * 31 + attributes.size()};
    }
    /** Decodes these sounds on a background thread, so even their first play does not stall the game. */
    public static void prewarm(java.util.Collection<Path> files) {
        for (Path file : files) {
            WARM.execute(() -> {
                try {
                    long[] stamp = stamp(file);
                    Clip clip = CLIPS.get(file);
                    Decoded known = DECODED.get(file);
                    if (clip != null && clip.stamp == stamp[1] || known != null && known.stamp() == stamp[1]) return;
                    if (stamp[0] == 0 || stamp[0] > 24 << 20) return;
                    DECODED.put(file, new Decoded(stamp[1], Decoders.decodeAll(file, 20)));
                    if (DECODED.size() > 16) DECODED.keySet().removeIf(other -> !other.equals(file));
                } catch (IOException | RuntimeException | LinkageError ignored) {
                    // It is decoded (and the problem reported) on first play instead.
                }
            });
        }
    }
    private static Clip clip(Path file) throws IOException {
        long now = System.nanoTime();
        Clip cached = CLIPS.get(file);
        if (cached != null && now - cached.checked < 3_000_000_000L) return cached;
        long[] stamp = stamp(file);
        if (cached != null && cached.stamp == stamp[1]) { cached.checked = now; return cached; }
        if (cached != null) AL10.alDeleteBuffers(cached.buffer);
        CLIPS.remove(file);
        if (stamp[0] == 0 || stamp[0] > 24 << 20) return null;
        Decoded ready = DECODED.remove(file);
        Decoders.Pcm decoded = ready != null && ready.stamp() == stamp[1] ? ready.pcm() : Decoders.decodeAll(file, 20);
        ShortBuffer pcm = MemoryUtil.memAllocShort(decoded.frames() * decoded.channels());
        try {
            pcm.put(decoded.data(), 0, decoded.frames() * decoded.channels()).flip();
            AL10.alGetError();
            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, decoded.rate());
            if (AL10.alGetError() != AL10.AL_NO_ERROR) { AL10.alDeleteBuffers(buffer); return null; }
            Clip clip = new Clip(buffer, stamp[1], now);
            CLIPS.put(file, clip);
            return clip;
        } finally {
            MemoryUtil.memFree(pcm);
        }
    }
}
