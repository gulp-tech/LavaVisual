package tech.gulp.lavavisual.audio;

import java.io.IOException;
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
import org.lwjgl.system.MemoryUtil;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Music from .minecraft/LavaVisual/music (MP3, Ogg Vorbis, Opus, WAV; the format is read from the file itself), played
 * through Minecraft's OpenAL device: play / pause, seeking, previous / next, shuffle and repeat. A small daemon thread opens,
 * decodes and queues the audio (four 0.4 s buffers) without holding anything the game thread waits for, so neither
 * lag spikes in the game stutter the music nor the music stutters the game. The master volume and the player's own volume apply; vanilla background music
 * is kept quiet while a track plays.
 */
public final class MusicPlayer {
    public record Track(Path file, String name, String title, String artist, double seconds, String problem, String format) {
        public boolean playable() { return problem.isEmpty(); }
        public String shown() { return title.isBlank() ? name : title; }
        public String line() { return artist.isBlank() ? shown() : artist + " — " + shown(); }
    }
    private static final int BUFFERS = 4, CHUNK_MS = 400;
    /** Guards the track list, the OpenAL objects and the requests; never held while a file is read or decoded. */
    private static final Object LOCK = new Object();
    private static final List<Track> TRACKS = new ArrayList<>();
    private static final int[] BUFFER_IDS = new int[BUFFERS], BUFFER_FRAMES = new int[BUFFERS];
    private static final ArrayDeque<Integer> QUEUE = new ArrayDeque<>(), FREE = new ArrayDeque<>();
    private static final Random RANDOM = new Random();
    private static final java.util.Map<Path, Scanned> SCANNED = new java.util.concurrent.ConcurrentHashMap<>();
    private record Scanned(long size, long modified, AudioInfo info) { }
    private static int index = -1, channels, rate, source, skipped;
    /** How many times the current internet station has been retried after a drop-out. */
    private static int streamRetries;
    private static long baseFrame, context, lengthFrames = -1;
    // Requests for the streamer thread (under LOCK). trackGen changes with every play / close and retires the open
    // decoder; generation also changes with every seek and retires audio decoded for the old position.
    private static Track openRequest;
    /** Station the streamer thread has to start generating, -1 when a file is being opened. */
    private static int radioRequest = -1;
    /** Station that is tuned in right now, or -1 while a file plays. */
    private static int radioIndex = -1;
    private static long seekRequest = -1;
    private static double openSeek = -1;
    private static boolean closeRequest;
    private static int trackGen, generation;
    // Streamer thread only.
    private static Decoders.Source decoder;
    private static int decoderGen, dChannels, dRate;
    private static short[] chunk;
    private static ShortBuffer pcm;
    private static volatile String error = "", decodeError = "";
    private static volatile boolean failed;
    private static volatile boolean playing, paused, ended, finished;
    private static Thread streamer;
    private static int musicQuietTicks;
    private MusicPlayer() { }

    public static List<Track> tracks() { synchronized (LOCK) { return List.copyOf(TRACKS); } }
    public static int index() { return index; }
    public static int skipped() { return skipped; }
    public static Track current() {
        synchronized (LOCK) {
            if (radioIndex >= 0 && playing) return Radio.trackOf(radioIndex);
            return index >= 0 && index < TRACKS.size() ? TRACKS.get(index) : null;
        }
    }
    /** Station that is on air, or -1 while a file plays. */
    public static int radioIndex() { synchronized (LOCK) { return radioIndex; } }
    /** True while the radio is on air (a station, not a file). */
    public static boolean radioActive() { synchronized (LOCK) { return radioIndex >= 0 && playing; } }
    /** Neighbour in play order (shuffle picks at random, so the list order is shown). */
    public static Track neighbour(int step) {
        synchronized (LOCK) {
            if (radioIndex >= 0) return Radio.trackOf(radioIndex + step);
            if (TRACKS.size() < 2 || index < 0) return null;
            return TRACKS.get(Math.floorMod(index + step, TRACKS.size()));
        }
    }
    public static boolean active() { return playing; }
    public static boolean playing() { return playing && !paused; }
    public static boolean paused() { return playing && paused; }
    /** Why the last track did not play (shown in the player), or "". */
    public static String error() { return error; }

    /** Rescans the music folder (sorted by name); keeps the current track playing when it is still there. Tags are
     *  read once per file version, so later scans only look at file sizes and dates. */
    public static void rescan(Path dir) {
        List<Track> found = new ArrayList<>();
        int bad = 0;
        if (Files.isDirectory(dir)) try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(AudioInfo::isAudio).sorted().toList()) {
                AudioInfo info = info(file);
                if (!info.playable()) bad++;
                found.add(new Track(file, AudioInfo.baseName(file), info.title, info.artist, info.seconds, info.problem, info.format.label));
            }
        } catch (IOException error) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot read the music folder", error);
        }
        SCANNED.keySet().removeIf(file -> !found.isEmpty() && found.stream().noneMatch(t -> t.file().equals(file)));
        synchronized (LOCK) {
            Path now = index >= 0 && index < TRACKS.size() ? TRACKS.get(index).file() : null;
            TRACKS.clear();
            TRACKS.addAll(found);
            skipped = bad;
            index = -1;
            for (int i = 0; i < TRACKS.size(); i++) if (TRACKS.get(i).file().equals(now)) index = i;
            if (index < 0 && playing && radioIndex < 0) close();
        }
    }
    private static AudioInfo info(Path file) {
        long size = -1, modified = -1;
        try {
            var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
            size = attributes.size();
            modified = attributes.lastModifiedTime().toMillis();
            Scanned known = SCANNED.get(file);
            if (known != null && known.size() == size && known.modified() == modified) return known.info();
        } catch (IOException | RuntimeException ignored) { }
        AudioInfo info = AudioInfo.read(file, 256 << 10, false);
        if (size >= 0) SCANNED.put(file, new Scanned(size, modified, info));
        return info;
    }

    /** Starts a track. Only OpenAL objects are made here; the streamer thread opens and decodes the file, so starting,
     *  seeking and switching tracks never stall the game. */
    public static void play(int i) {
        synchronized (LOCK) {
            close();
            radioIndex = -1;
            streamRetries = 0;
            if (i < 0 || i >= TRACKS.size()) return;
            index = i;
            Track track = TRACKS.get(i);
            error = "";
            decodeError = "";
            failed = false;
            if (!track.playable()) { fail(track, track.problem()); return; }
            if (!LavaAudio.ready()) { fail(track, "звук игры недоступен (OpenAL)"); return; }
            try {
                AL10.alGetError();
                source = AL10.alGenSources();
                for (int b = 0; b < BUFFERS; b++) BUFFER_IDS[b] = AL10.alGenBuffers();
                if (AL10.alGetError() != AL10.AL_NO_ERROR) { close(); fail(track, "OpenAL не дал источник звука"); return; }
                context = ALC10.alcGetCurrentContext();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
                AL10.alSourcef(source, AL10.AL_GAIN, gain());
                for (int b = 0; b < BUFFERS; b++) FREE.addLast(BUFFER_IDS[b]);
                baseFrame = 0;
                ended = finished = paused = false;
                playing = true;
                openRequest = track;
                musicQuietTicks = 0;
                startStreamer();
                LOCK.notifyAll();
            } catch (RuntimeException | LinkageError failure) {
                LavaVisual.LOGGER.warn("LavaVisual: cannot play {}", track.file().getFileName(), failure);
                close();
                fail(track, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
        }
    }
    /**
     * Tunes the radio to station i: a station of LavaVisual is composed on the fly, a real one is streamed from the
     * internet. Both go through the same streamer thread, so the player, the HUD and the shortcuts behave the same.
     */
    public static void playRadio(int station) {
        if (station < 0 || station >= Radio.count()) return;
        synchronized (LOCK) {
            if (station != radioIndex) streamRetries = 0;   // a new station counts its own drop-outs
            close();
            radioIndex = station;
            Track track = Radio.trackOf(station);
            error = "";
            decodeError = "";
            failed = false;
            if (!LavaAudio.ready()) { fail(track, "звук игры недоступен (OpenAL)"); return; }
            try {
                AL10.alGetError();
                source = AL10.alGenSources();
                for (int b = 0; b < BUFFERS; b++) BUFFER_IDS[b] = AL10.alGenBuffers();
                if (AL10.alGetError() != AL10.AL_NO_ERROR) { close(); fail(track, "OpenAL не дал источник звука"); return; }
                context = ALC10.alcGetCurrentContext();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
                AL10.alSourcef(source, AL10.AL_GAIN, gain());
                for (int b = 0; b < BUFFERS; b++) FREE.addLast(BUFFER_IDS[b]);
                baseFrame = 0;
                ended = finished = paused = false;
                playing = true;
                radioRequest = station;
                musicQuietTicks = 0;
                startStreamer();
                LOCK.notifyAll();
            } catch (RuntimeException | LinkageError failure) {
                LavaVisual.LOGGER.warn("LavaVisual: cannot start the radio station {}", track.name(), failure);
                close();
                fail(track, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
            }
        }
    }
    private static void fail(Track track, String why) {
        error = why;
        LavaVisual.LOGGER.warn("LavaVisual: {} does not play: {}", track.file().getFileName(), why);
        tech.gulp.lavavisual.input.Binds.Toast.show("Не играет: " + why);
    }
    private static int frames(int buffer) {
        for (int b = 0; b < BUFFERS; b++) if (BUFFER_IDS[b] == buffer) return BUFFER_FRAMES[b];
        return 0;
    }
    private static void startStreamer() {
        if (streamer != null && streamer.isAlive()) return;
        streamer = new Thread(MusicPlayer::streamLoop, "LavaVisual music");
        streamer.setDaemon(true);
        streamer.setPriority(Thread.NORM_PRIORITY + 1);
        streamer.start();
    }

    // ------------------------------------------------------------------------------------------ streamer thread

    private static void streamLoop() {
        while (true) {
            Track open;
            long seekTo;
            boolean closing;
            int tg, g, radio;
            synchronized (LOCK) {
                boolean pending = openRequest != null || radioRequest >= 0 || seekRequest >= 0 || closeRequest;
                if (!pending) try { LOCK.wait(playing ? 20 : 250); } catch (InterruptedException e) { closeDecoder(); return; }
                open = openRequest; openRequest = null;
                radio = radioRequest; radioRequest = -1;
                seekTo = seekRequest; seekRequest = -1;
                closing = closeRequest; closeRequest = false;
                tg = trackGen; g = generation;
            }
            try {
                if (closing || open != null || radio >= 0) closeDecoder();
                if (radio >= 0) {
                    if (openRadio(radio, tg) == -2) continue;
                } else if (open != null) {
                    long first = openDecoder(open, tg);
                    if (first == -2) continue;
                    if (first >= 0) seekTo = first;
                }
                if (decoder == null || decoderGen != tg) continue;
                if (seekTo >= 0) decoder.seek(seekTo);
                pump(g);
            } catch (RuntimeException | LinkageError failure) {
                LavaVisual.LOGGER.warn("LavaVisual music stream", failure);
                synchronized (LOCK) { if (trackGen == tg) { decodeError = String.valueOf(failure.getMessage()); failed = true; } }
                closeDecoder();
            }
        }
    }
    /** Opens the file for track generation tg; returns -2 when it is gone or failed, else the frame to start from
     *  (a seek that arrived before the sample rate was known) or -1. */
    private static long openDecoder(Track track, int tg) {
        Decoders.Source opened;
        try {
            opened = Decoders.open(track.file(), AudioInfo.read(track.file(), 1 << 20, false), true);
        } catch (IOException | RuntimeException | LinkageError failure) {
            LavaVisual.LOGGER.warn("LavaVisual: cannot play {}", track.file().getFileName(), failure);
            synchronized (LOCK) {
                if (trackGen == tg) { decodeError = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); failed = true; }
            }
            return -2;
        }
        long first = -1;
        synchronized (LOCK) {
            if (trackGen != tg || !playing) { opened.close(); return -2; }
            channels = opened.channels();
            rate = opened.rate();
            lengthFrames = opened.length();
            if (openSeek >= 0) {
                first = (long) (openSeek * rate);
                if (lengthFrames > 0) first = Math.min(first, Math.max(0, lengthFrames - 1));
                baseFrame = first;
                openSeek = -1;
            }
        }
        decoder = opened;
        decoderGen = tg;
        dChannels = opened.channels();
        dRate = opened.rate();
        int frames = dRate * CHUNK_MS / 1000;
        chunk = new short[frames * dChannels];
        pcm = MemoryUtil.memAllocShort(frames * dChannels);
        return first;
    }
    /**
     * Tunes in to a station: the composed ones are generated in place, the real ones are decoded from the network as
     * they arrive. A station that cannot be opened says why instead of leaving silence.
     */
    private static long openRadio(int station, int tg) {
        Decoders.Source opened;
        try {
            opened = Radio.stream(station) ? NetRadio.open(station - Radio.stations().size()) : new Radio.Synth(station);
        } catch (IOException | RuntimeException | LinkageError failure) {
            String why = failure.getMessage() == null ? "станция недоступна" : failure.getMessage();
            LavaVisual.LOGGER.warn("LavaVisual: cannot tune in to station {}", Radio.name(station), failure);
            synchronized (LOCK) { if (trackGen == tg) { decodeError = why; failed = true; } }
            return -2;
        }
        synchronized (LOCK) {
            if (trackGen != tg || !playing) { opened.close(); return -2; }
            channels = opened.channels();
            rate = opened.rate();
            lengthFrames = -1;
        }
        decoder = opened;
        decoderGen = tg;
        dChannels = opened.channels();
        dRate = opened.rate();
        int frames = dRate * CHUNK_MS / 1000;
        chunk = new short[frames * dChannels];
        pcm = MemoryUtil.memAllocShort(frames * dChannels);
        return -1;
    }
    private static void closeDecoder() {
        if (decoder != null) { decoder.close(); decoder = null; }
        if (pcm != null) { MemoryUtil.memFree(pcm); pcm = null; }
        chunk = null;
    }
    /** Keeps the source fed: finished buffers come back, new audio is decoded outside the lock and queued unless a seek,
     *  a track change or a stop happened meanwhile. */
    private static void pump(int g) {
        while (true) {
            synchronized (LOCK) {
                if (generation != g || !playing || source == 0 || ALC10.alcGetCurrentContext() != context) return;
                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                while (processed-- > 0) {
                    int buffer = AL10.alSourceUnqueueBuffers(source);
                    QUEUE.pollFirst();
                    baseFrame += frames(buffer);
                    FREE.addLast(buffer);
                }
                if (FREE.isEmpty() || ended) { startIfNeeded(); return; }
            }
            int frames = decoder.read(chunk, chunk.length / dChannels);
            synchronized (LOCK) {
                if (generation != g || !playing || source == 0 || ALC10.alcGetCurrentContext() != context) return;
                if (frames < 0) { ended = true; decodeError = decoder.error(); startIfNeeded(); return; }
                if (frames == 0) { startIfNeeded(); return; }
                Integer buffer = FREE.pollFirst();
                if (buffer == null) return;
                pcm.clear();
                pcm.put(chunk, 0, frames * dChannels).flip();
                AL10.alBufferData(buffer, dChannels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, dRate);
                for (int b = 0; b < BUFFERS; b++) if (BUFFER_IDS[b] == buffer) BUFFER_FRAMES[b] = frames;
                AL10.alSourceQueueBuffers(source, buffer);
                QUEUE.addLast(buffer);
                lengthFrames = decoder.length();
                startIfNeeded();
            }
        }
    }
    /** Under LOCK: plays as soon as audio is queued (also after an underrun); reports the end of the track. */
    private static void startIfNeeded() {
        if (paused || source == 0 || AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) return;
        if (!QUEUE.isEmpty()) AL10.alSourcePlay(source);
        else if (ended) {
            if (baseFrame == 0 && !decodeError.isEmpty()) failed = true; // nothing decoded at all
            else finished = true;
        }
    }

    // ------------------------------------------------------------------------------------------ game thread

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
            int track = index, station = radioIndex;
            synchronized (LOCK) { source = 0; close(); } // the old device's AL objects are gone
            if (LavaAudio.ready()) {
                if (station >= 0) playRadio(station); else { play(track); seek(at); }
                if (wasPaused) toggle();
            }
            return;
        }
        if (failed) {
            failed = false;
            Track track = current();
            String why = decodeError.isEmpty() ? "файл не декодируется" : decodeError;
            synchronized (LOCK) { close(); }
            if (track != null) fail(track, why);
            return;
        }
        if (finished) {
            finished = false;
            // A stream that played for a while before it dropped gets a fresh set of retries: only a quick run of
            // failures in a row counts towards switching the station.
            if (radioIndex >= 0 && rate > 0 && baseFrame >= 30L * rate) streamRetries = 0;
            if (radioIndex >= 0 && Radio.stream(radioIndex) && streamRetries < 3) {
                // An internet stream that dropped is retried on the same station, so a hiccup does not change the
                // channel; only three failed attempts in a row switch to the next one.
                streamRetries++;
                playRadio(radioIndex);
            } else next(true);
        }
        if (playing() && mc.getMusicManager() != null && musicQuietTicks-- <= 0) { mc.getMusicManager().stopPlaying(); musicQuietTicks = 100; }
    }
    private static float gain() {
        var mc = Minecraft.getInstance();
        float master = mc.options == null ? 1 : mc.options.getSoundSourceVolume(SoundSource.MASTER);
        return (float) Math.clamp(LavaVisualClient.config().musicVolume * master, 0, 1);
    }

    public static void toggle() {
        synchronized (LOCK) {
            if (!playing) { if (radioIndex >= 0) playRadio(radioIndex); else play(index >= 0 ? index : 0); return; }
            if (paused) { paused = false; startIfNeeded(); }
            else { AL10.alSourcePause(source); paused = true; }
        }
    }
    public static void stop() { synchronized (LOCK) { close(); } }
    public static void next(boolean automatic) {
        var c = LavaVisualClient.config();
        int stations = Radio.count();
        if (radioIndex >= 0) {
            playRadio(c.musicShuffle && stations > 1 ? RANDOM.nextInt(stations) : (radioIndex + 1) % stations);
            return;
        }
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
        if (radioIndex >= 0) {
            int stations = Radio.count();
            playRadio((radioIndex + stations - 1) % stations);
            return;
        }
        synchronized (LOCK) {
            if (playing && position() > 3) { seek(0); return; }
            int n = TRACKS.size();
            if (n == 0) return;
            int target = index <= 0 ? n - 1 : index - 1;
            play(target);
        }
    }
    public static void skip(double seconds) { synchronized (LOCK) { if (playing) seek(position() + seconds); } }

    /** Jumps to a time: the queued audio is dropped at once and the streamer continues from there. */
    public static void seek(double seconds) {
        synchronized (LOCK) {
            if (!playing || source == 0) return;
            if (rate <= 0) { openSeek = Math.max(0, seconds); return; } // still opening: applied when it is open
            long frame = (long) Math.max(0, seconds * rate);
            if (lengthFrames > 0) frame = Math.min(frame, Math.max(0, lengthFrames - 1));
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            QUEUE.clear();
            FREE.clear();
            for (int b = 0; b < BUFFERS; b++) FREE.addLast(BUFFER_IDS[b]);
            baseFrame = frame;
            ended = finished = false;
            seekRequest = frame;
            generation++;
            LOCK.notifyAll();
        }
    }
    public static double position() {
        synchronized (LOCK) {
            if (!playing || source == 0 || rate <= 0) return 0;
            int offset = ALC10.alcGetCurrentContext() == context ? AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET) : 0;
            double seconds = (baseFrame + Math.max(0, offset)) / (double) rate;
            double duration = duration();
            return duration > 0 ? Math.min(duration, seconds) : seconds;
        }
    }
    public static double duration() {
        synchronized (LOCK) {
            if (radioIndex >= 0) return 0; // live radio has no length
            long length = playing ? lengthFrames : -1;
            if (rate > 0 && length > 0) return length / (double) rate;
            Track t = index >= 0 && index < TRACKS.size() ? TRACKS.get(index) : null;
            return t == null ? 0 : t.seconds();
        }
    }
    /** CI: OpenAL state of the music source. */
    public static int alState() { synchronized (LOCK) { return playing && source != 0 ? AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) : 0; } }

    /** Under LOCK: frees the OpenAL objects now and tells the streamer to close the file. */
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
        FREE.clear();
        playing = paused = ended = finished = false;
        rate = 0;
        channels = 0;
        lengthFrames = -1;
        openRequest = null;
        radioRequest = -1;
        seekRequest = -1;
        openSeek = -1;
        closeRequest = true;
        trackGen++;
        generation++;
        LOCK.notifyAll();
    }
    public static String time(double seconds) {
        int s = (int) Math.max(0, Math.round(seconds));
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
}
