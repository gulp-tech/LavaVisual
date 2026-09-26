package tech.gulp.lavavisual.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Music from config/lavavisual-hud/music (.ogg Vorbis), streamed through Minecraft's OpenAL device: play / pause,
 * seeking, previous / next, shuffle and repeat. A small daemon thread keeps four 0.4 s buffers queued, so lag spikes
 * in the game do not stutter the music. The master volume and the player's own volume apply; vanilla background music
 * is kept quiet while a track plays.
 */
public final class MusicPlayer {
    public record Track(Path file, String name, String title, String artist, double seconds, boolean playable) {
        public String shown() { return title.isBlank() ? name : title; }
        public String line() { return artist.isBlank() ? shown() : artist + " — " + shown(); }
    }
    private static final int BUFFERS = 4, CHUNK_MS = 400;
    private static final Object LOCK = new Object();
    private static final List<Track> TRACKS = new ArrayList<>();
    private static final int[] BUFFER_IDS = new int[BUFFERS], BUFFER_FRAMES = new int[BUFFERS];
    private static final ArrayDeque<Integer> QUEUE = new ArrayDeque<>();
    private static final Random RANDOM = new Random();
    private static int index = -1, channels, rate, source, lengthFrames, skipped;
    private static long handle, baseFrame, context;
    private static ByteBuffer data;
    private static ShortBuffer pcm;
    private static volatile boolean playing, paused, ended, finished;
    private static Thread streamer;
    private static int musicQuietTicks;
    private MusicPlayer() { }

    public static List<Track> tracks() { synchronized (LOCK) { return List.copyOf(TRACKS); } }
    public static int index() { return index; }
    public static int skipped() { return skipped; }
    public static Track current() { synchronized (LOCK) { return index >= 0 && index < TRACKS.size() ? TRACKS.get(index) : null; } }
    /** Neighbour in play order (shuffle picks at random, so the list order is shown). */
    public static Track neighbour(int step) {
        synchronized (LOCK) {
            if (TRACKS.size() < 2 || index < 0) return null;
            return TRACKS.get(Math.floorMod(index + step, TRACKS.size()));
        }
    }
    public static boolean active() { return playing; }
    public static boolean playing() { return playing && !paused; }
    public static boolean paused() { return playing && paused; }

    /** Rescans the music folder (sorted by name); keeps the current track playing when it is still there. */
    public static void rescan(Path dir) {
        List<Track> found = new ArrayList<>();
        int bad = 0;
        if (Files.isDirectory(dir)) try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(MusicPlayer::isOgg).sorted().toList()) {
                OggInfo info = OggInfo.read(file, 256 << 10, false);
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 4);
                if (!info.playable()) bad++;
                found.add(new Track(file, name, info.title, info.artist, info.seconds, info.playable()));
            }
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read the music folder", error);
        }
        synchronized (LOCK) {
            Path now = index >= 0 && index < TRACKS.size() ? TRACKS.get(index).file() : null;
            TRACKS.clear();
            TRACKS.addAll(found);
            skipped = bad;
            index = -1;
            for (int i = 0; i < TRACKS.size(); i++) if (TRACKS.get(i).file().equals(now)) index = i;
            if (index < 0 && playing) close();
        }
    }
    public static boolean isOgg(Path file) { return Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"); }

    public static void play(int i) {
        synchronized (LOCK) {
            close();
            if (i < 0 || i >= TRACKS.size()) return;
            index = i;
            Track track = TRACKS.get(i);
            if (!track.playable() || !LavaAudio.ready()) return;
            try {
                byte[] bytes = Files.readAllBytes(track.file());
                data = MemoryUtil.memAlloc(bytes.length);
                data.put(bytes).flip();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer error = stack.mallocInt(1);
                    handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
                    if (handle == 0) { close(); return; }
                    STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                    STBVorbis.stb_vorbis_get_info(handle, info);
                    channels = info.channels();
                    rate = info.sample_rate();
                }
                if (channels < 1 || channels > 2 || rate <= 0) { close(); return; }
                lengthFrames = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
                pcm = MemoryUtil.memAllocShort(rate * CHUNK_MS / 1000 * channels);
                AL10.alGetError();
                source = AL10.alGenSources();
                for (int b = 0; b < BUFFERS; b++) BUFFER_IDS[b] = AL10.alGenBuffers();
                if (AL10.alGetError() != AL10.AL_NO_ERROR) { close(); return; }
                context = ALC10.alcGetCurrentContext();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
                AL10.alSourcef(source, AL10.AL_GAIN, gain());
                baseFrame = 0;
                ended = finished = paused = false;
                for (int b = 0; b < BUFFERS; b++) if (!fill(BUFFER_IDS[b])) { ended = true; break; }
                AL10.alSourcePlay(source);
                playing = true;
                musicQuietTicks = 0;
                startStreamer();
            } catch (IOException | RuntimeException | LinkageError error) {
                LavaVisual.LOGGER.warn("LavaVisual: cannot play {}", track.file().getFileName(), error);
                close();
            }
        }
    }
    /** Decodes the next chunk into the buffer and queues it; false at the end of the track. */
    private static boolean fill(int buffer) {
        pcm.clear();
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
        if (frames <= 0) return false;
        pcm.limit(frames * channels);
        AL10.alBufferData(buffer, channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, rate);
        for (int b = 0; b < BUFFERS; b++) if (BUFFER_IDS[b] == buffer) BUFFER_FRAMES[b] = frames;
        AL10.alSourceQueueBuffers(source, buffer);
        QUEUE.addLast(buffer);
        return true;
    }
    private static int frames(int buffer) {
        for (int b = 0; b < BUFFERS; b++) if (BUFFER_IDS[b] == buffer) return BUFFER_FRAMES[b];
        return 0;
    }
    private static void startStreamer() {
        if (streamer != null && streamer.isAlive()) return;
        streamer = new Thread(() -> {
            while (true) {
                try { Thread.sleep(20); } catch (InterruptedException e) { return; }
                synchronized (LOCK) {
                    try { stream(); } catch (RuntimeException | LinkageError error) { LavaVisual.LOGGER.warn("LavaVisual music stream", error); close(); }
                }
            }
        }, "LavaVisual music");
        streamer.setDaemon(true);
        streamer.start();
    }
    private static void stream() {
        if (!playing || source == 0 || ALC10.alcGetCurrentContext() != context) return;
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            QUEUE.pollFirst();
            baseFrame += frames(buffer);
            if (!ended && !fill(buffer)) ended = true;
        }
        if (!paused && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
            if (!QUEUE.isEmpty()) AL10.alSourcePlay(source); // recovered from an underrun
            else if (ended) finished = true;
        }
    }
    /** Client tick: track end -> next, live volume, device restarts, quiet vanilla music. */
    public static void tick(Minecraft mc) {
        boolean restart = false;
        double at = 0;
        synchronized (LOCK) {
            if (playing && context != ALC10.alcGetCurrentContext()) { restart = true; at = position(); }
            if (playing && source != 0 && !restart) AL10.alSourcef(source, AL10.AL_GAIN, gain());
        }
        if (restart) {
            boolean wasPaused = paused;
            int track = index;
            synchronized (LOCK) { source = 0; close(); } // the old device's AL objects are gone
            if (LavaAudio.ready()) { play(track); seek(at); if (wasPaused) toggle(); }
            return;
        }
        if (finished) { finished = false; next(true); }
        if (playing() && mc.getMusicManager() != null && musicQuietTicks-- <= 0) { mc.getMusicManager().stopPlaying(); musicQuietTicks = 100; }
    }
    private static float gain() {
        var mc = Minecraft.getInstance();
        float master = mc.options == null ? 1 : mc.options.getSoundSourceVolume(SoundSource.MASTER);
        return (float) Math.clamp(LavaVisualClient.config().musicVolume * master, 0, 1);
    }

    public static void toggle() {
        synchronized (LOCK) {
            if (!playing) { int start = index >= 0 ? index : 0; play(start); return; }
            if (paused) { AL10.alSourcePlay(source); paused = false; }
            else { AL10.alSourcePause(source); paused = true; }
        }
    }
    public static void stop() { synchronized (LOCK) { close(); } }
    public static void next(boolean automatic) {
        var c = LavaVisualClient.config();
        int target;
        synchronized (LOCK) {
            int n = TRACKS.size();
            if (n == 0) { close(); return; }
            if (automatic && c.musicRepeat == 2) target = Math.max(0, index);
            else if (c.musicShuffle && n > 1) { do target = RANDOM.nextInt(n); while (target == index); }
            else if (index + 1 < n) target = index + 1;
            else if (automatic && c.musicRepeat == 0) { close(); return; }
            else target = 0;
        }
        play(target);
    }
    /** Back: restarts the track after 3 s, otherwise goes to the previous one. */
    public static void previous() {
        synchronized (LOCK) {
            if (playing && position() > 3) { seek(0); return; }
            int n = TRACKS.size();
            if (n == 0) return;
            int target = index <= 0 ? n - 1 : index - 1;
            play(target);
        }
    }
    public static void skip(double seconds) { synchronized (LOCK) { if (playing) seek(position() + seconds); } }

    public static void seek(double seconds) {
        synchronized (LOCK) {
            if (!playing || handle == 0) return;
            int frame = (int) Math.clamp(seconds * rate, 0, Math.max(0, lengthFrames - 1));
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            QUEUE.clear();
            STBVorbis.stb_vorbis_seek(handle, frame);
            baseFrame = frame;
            ended = finished = false;
            for (int b = 0; b < BUFFERS; b++) if (!fill(BUFFER_IDS[b])) { ended = true; break; }
            if (!paused) AL10.alSourcePlay(source);
        }
    }
    public static double position() {
        synchronized (LOCK) {
            if (!playing || source == 0 || rate <= 0) return 0;
            int offset = ALC10.alcGetCurrentContext() == context ? AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET) : 0;
            return Math.min(duration(), (baseFrame + Math.max(0, offset)) / (double) rate);
        }
    }
    public static double duration() {
        synchronized (LOCK) {
            if (playing && rate > 0 && lengthFrames > 0) return lengthFrames / (double) rate;
            Track t = index >= 0 && index < TRACKS.size() ? TRACKS.get(index) : null;
            return t == null ? 0 : t.seconds();
        }
    }
    /** CI: OpenAL state of the music source. */
    public static int alState() { synchronized (LOCK) { return playing && source != 0 ? AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) : 0; } }

    private static void close() {
        boolean same = context != 0 && ALC10.alcGetCurrentContext() == context;
        if (source != 0 && same) {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(source);
            for (int b = 0; b < BUFFERS; b++) if (BUFFER_IDS[b] != 0) AL10.alDeleteBuffers(BUFFER_IDS[b]);
        }
        source = 0;
        java.util.Arrays.fill(BUFFER_IDS, 0);
        QUEUE.clear();
        if (handle != 0) { STBVorbis.stb_vorbis_close(handle); handle = 0; }
        if (data != null) { MemoryUtil.memFree(data); data = null; }
        if (pcm != null) { MemoryUtil.memFree(pcm); pcm = null; }
        playing = paused = ended = finished = false;
        lengthFrames = 0;
    }
    public static String time(double seconds) {
        int s = (int) Math.max(0, Math.round(seconds));
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
}
