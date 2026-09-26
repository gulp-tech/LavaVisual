package tech.gulp.lavavisual.audio;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
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
    private record Clip(int buffer, long stamp) { }
    private static final Map<Path, Clip> CLIPS = new HashMap<>();
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
            AL10.alSourcei(source, AL10.AL_BUFFER, clip.buffer());
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
    private static Clip clip(Path file) throws IOException {
        long stamp = Files.getLastModifiedTime(file).toMillis() * 31 + Files.size(file);
        Clip cached = CLIPS.get(file);
        if (cached != null && cached.stamp() == stamp) return cached;
        if (cached != null) AL10.alDeleteBuffers(cached.buffer());
        CLIPS.remove(file);
        if (Files.size(file) == 0 || Files.size(file) > 24 << 20) return null;
        Decoders.Pcm decoded = Decoders.decodeAll(file, 20);
        ShortBuffer pcm = MemoryUtil.memAllocShort(decoded.frames() * decoded.channels());
        try {
            pcm.put(decoded.data(), 0, decoded.frames() * decoded.channels()).flip();
            AL10.alGetError();
            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, decoded.rate());
            if (AL10.alGetError() != AL10.AL_NO_ERROR) { AL10.alDeleteBuffers(buffer); return null; }
            Clip clip = new Clip(buffer, stamp);
            CLIPS.put(file, clip);
            return clip;
        } finally {
            MemoryUtil.memFree(pcm);
        }
    }
}
