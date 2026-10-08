package tech.gulp.lavavisual.audio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The station list of the music player: thirteen stations composed on the fly by a small deterministic composer plus
 * {@link NetRadio} with the real stations that are on air right now. The composed ones need no files and no internet,
 * so the radio always has something to play; the internet ones are grouped into talk radio, the radio of Makhachkala
 * and Dagestan, and music. Each composed station is an endless 44.1 kHz stereo stream built from a chord progression,
 * a bass line, a pad, an arpeggio, a melody and light percussion; the streamer thread of the music player generates it
 * exactly like a decoded or streamed file, so play / pause / volume, the HUD widget and the shortcuts work the same.
 */
public final class Radio {
    private Radio() { }

    /** One station: what the composer plays (tempo, key, scale, waves) and how it sounds (swing, echo). */
    public record Station(String name, String genre, double bpm, int root, int[] scale,
                          int padWave, int leadWave, int bassWave, double swing, double echo) { }

    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10};
    private static final int[] PENTA = {0, 3, 5, 7, 10};
    private static final int[] LYDIAN = {0, 2, 4, 6, 7, 9, 11};

    private static final List<Station> STATIONS = List.of(
            new Station("Лоу-фай", "спокойный бит и мягкие аккорды", 74, 45, PENTA, 0, 9, 1, 0, 0.30),
            new Station("Тихий вечер", "самый мягкий канал", 64, 43, MINOR, 0, 0, 0, 0, 0.32),
            new Station("Синтвейв", "неон, бас восьмыми, арпеджио", 104, 40, MINOR, 2, 2, 2, 0, 0.22),
            new Station("Космос", "эмбиент без ударных", 62, 38, LYDIAN, 0, 4, 0, 0, 0.44),
            new Station("Чиптюн", "8-битные арпеджио", 132, 48, MAJOR, 3, 3, 3, 0, 0.18),
            new Station("Фортепиано", "переливы без барабанов", 70, 41, MAJOR, 0, 4, 0, 0, 0.26),
            new Station("Эпик", "тёмный дрон и большие аккорды", 92, 33, MINOR, 2, 2, 2, 0, 0.30),
            new Station("Джаз", "свинг, контрабас, щётки", 112, 43, DORIAN, 1, 1, 1, 0.30, 0.20),
            new Station("Техно", "ровный бит, кислый бас", 128, 36, MINOR, 2, 3, 2, 0, 0.16),
            new Station("Фолк", "щипковые струны и дорожный ритм", 96, 45, DORIAN, 1, 9, 1, 0.12, 0.24),
            new Station("Медитация", "дрон и колокольчики", 54, 40, PENTA, 0, 4, 0, 0, 0.50),
            new Station("Ретро-игра", "быстрая 8-битная мелодия", 148, 50, MAJOR, 3, 3, 3, 0, 0.14),
            new Station("Драм-н-бейс", "быстрые хэты и сабвуфер", 170, 34, MINOR, 2, 2, 0, 0, 0.18));

    // One 16-step bar per station: which 16th plays a kick, a snare, a hat, a bass note, an arpeggio or a melody note.
    private static final String[] KICK = {
            "x.......x.......", "................", "x...x...x...x...", "................", "x...x...x...x...",
            "................", "x.......x.......", "x.......x.......", "x...x...x...x...", "x..x..x...x.....",
            "................", "x...x...x...x...", "x.......x......."};
    private static final String[] SNARE = {
            "................", "................", "....x.......x...", "................", "....x.......x...",
            "................", "................", "....x.......x...", "....x.......x...", "....x.......x...",
            "................", "....x.......x...", "....x...x...x..x"};
    private static final String[] HAT = {
            "..x...x...x...x.", "................", "..x...x...x...x.", "................", "..x...x...x...x.",
            "................", "..x...x...x...x.", "..x...x...x...x.", "..x.x.x.x.x.x.x.", "..x...x...x..x..",
            "................", "..x.x.x.x.x.x.x.", "x.x.x.x.x.x.x.x."};
    private static final String[] BASS = {
            "x...x.......x...", "x.......x.......", "x.x.x.x.x.x.x.x.", "x...............", "x..x..x.x..x..x.",
            "x.......x.......", "x...............", "x...x...x...x...", "xxxxxxxxxxxxxxxx", "x.......x.......",
            "x...............", "x..x..x.x..x..x.", "x.....x...x....."};
    private static final String[] ARP = {
            "....x...x...x...", "..............x.", "x.x.x.x.x.x.x.x.", "......x.........", "xxxxxxxxxxxxxxxx",
            "x..x..x.x..x..x.", "................", "................", "..x...x...x...x.", "x.x.x.x.x.x.x.x.",
            "..........x.....", "xxxxxxxxxxxxxxxx", "....x.......x..."};
    private static final String[] LEAD = {
            "................", "................", "..x.......x.....", "................", "x..x..x...x.x...",
            "................", "..x.......x.....", "x..x..x..x..x..x", "................", "x..x.x....x..x..",
            "....x.....x.....", "x..x..x.x..x..x.", "................"};
    /** Chord per bar of the four-bar loop, as degrees of the station scale. */
    private static final int[][] CHORDS = {
            {0, 3, 4, 3}, {0, 5, 3, 4}, {0, 5, 3, 4}, {0, 4, 5, 3}, {0, 4, 5, 4}, {0, 3, 4, 3}, {0, 5, 1, 4},
            {0, 3, 4, 4}, {0, 0, 3, 4}, {0, 6, 3, 4}, {0, 4, 0, 5}, {0, 4, 5, 4}, {0, 3, 0, 4}};
    /** How loud each voice of a station is: kick, snare, hat, bass, arpeggio, melody, pad. */
    private static final double[][] MIX = {
            {0.42, 0.00, 0.06, 0.24, 0.10, 0.00, 0.14}, {0.00, 0.00, 0.00, 0.20, 0.07, 0.09, 0.18},
            {0.50, 0.18, 0.07, 0.26, 0.09, 0.11, 0.12}, {0.00, 0.00, 0.00, 0.00, 0.08, 0.10, 0.20},
            {0.46, 0.16, 0.06, 0.22, 0.10, 0.12, 0.10}, {0.00, 0.00, 0.00, 0.18, 0.16, 0.00, 0.10},
            {0.52, 0.00, 0.04, 0.26, 0.00, 0.13, 0.20}, {0.30, 0.10, 0.07, 0.24, 0.00, 0.12, 0.12},
            {0.52, 0.18, 0.08, 0.28, 0.05, 0.00, 0.10}, {0.34, 0.10, 0.06, 0.22, 0.13, 0.08, 0.12},
            {0.00, 0.00, 0.00, 0.10, 0.00, 0.10, 0.22}, {0.46, 0.16, 0.06, 0.20, 0.12, 0.13, 0.10},
            {0.50, 0.20, 0.08, 0.30, 0.00, 0.08, 0.10}};

    public static List<Station> stations() { return STATIONS; }
    public static Station station(int i) { return STATIONS.get(Math.floorMod(i, STATIONS.size())); }

    /** A station as a playlist entry, so the player, the HUD widget and the music page show it like any other track. */
    public static MusicPlayer.Track track(int i) {
        Station station = station(i);
        return new MusicPlayer.Track(Path.of("lavavisual-radio", station.name() + ".radio"), station.name(),
                station.name(), "LavaVisual Радио", 0, "", "радио");
    }

    /** Title of the group of stations composed on the fly. */
    public static final String SYNTH_GROUP = "Радио LavaVisual";
    /** Every station that can be tuned in: the composed ones first, then the real internet stations. */
    public static int count() { return STATIONS.size() + NetRadio.count(); }
    /** True when station i comes from the internet. */
    public static boolean stream(int i) { return i < 0 || i >= STATIONS.size(); }
    /** Name of station i, whichever kind it is. */
    public static String name(int i) {
        if (i < 0 || i >= count()) return "";
        return i < STATIONS.size() ? STATIONS.get(i).name() : NetRadio.name(i - STATIONS.size());
    }
    /** Genre of station i, whichever kind it is. */
    public static String genre(int i) {
        if (i < 0 || i >= count()) return "";
        return i < STATIONS.size() ? STATIONS.get(i).genre() : NetRadio.genre(i - STATIONS.size());
    }
    /** Station i as a playlist entry: a station composed on the fly or an internet stream. */
    public static MusicPlayer.Track trackOf(int i) {
        int at = Math.floorMod(i, count());
        return at < STATIONS.size() ? track(at) : NetRadio.track(at - STATIONS.size());
    }
    /** One line of the station list as the menu shows it. */
    public record Row(int index, String group, String name, String genre, String url) { }
    /** The catalogue for the menus: the real stations by group first, the composed ones after them. */
    public static List<Row> rows() {
        List<Row> rows = new ArrayList<>(count());
        for (int i = 0; i < NetRadio.count(); i++)
            rows.add(new Row(STATIONS.size() + i, NetRadio.group(i), NetRadio.name(i), NetRadio.genre(i), NetRadio.url(i)));
        for (int i = 0; i < STATIONS.size(); i++)
            rows.add(new Row(i, SYNTH_GROUP, STATIONS.get(i).name(), STATIONS.get(i).genre(), ""));
        return rows;
    }

    private static final double TAU = Math.PI * 2;

    /** The generator itself: the music player streams it through the same interface as a decoded file. */
    public static final class Synth implements Decoders.Source {
        private static final int RATE = 44100, VOICES = 24;
        private final int index;
        private final Station station;
        private final Voice[] voices = new Voice[VOICES];
        private final double[] echoLeft = new double[RATE / 2], echoRight = new double[RATE / 2];
        private final double stepFrames;
        private long pos;
        private double nextStep;
        private int step, cursor;
        private int lastLead = 4;
        private long random = 0x2545F4914F6CDD1DL;
        private boolean closed;

        public Synth(int index) {
            this.index = Math.floorMod(index, STATIONS.size());
            this.station = STATIONS.get(this.index);
            this.stepFrames = RATE * 60.0 / station.bpm() / 4; // one 16th note
            this.random += 0x9E3779B97F4A7C15L * (this.index + 1);
        }

        @Override public int channels() { return 2; }
        @Override public int rate() { return RATE; }
        @Override public long length() { return -1; } // live radio, endless
        @Override public void seek(long frame) { }    // a live stream cannot be rewound
        @Override public String error() { return ""; }
        @Override public void close() { closed = true; }

        @Override public int read(short[] out, int maxFrames) {
            if (closed) return -1;
            int frames = Math.min(maxFrames, out.length / 2);
            double echo = station.echo();
            for (int i = 0; i < frames; i++) {
                while (pos >= nextStep) {
                    schedule();
                    nextStep += stepFrames * (1 + station.swing() * ((step % 4) < 2 ? 1 : -1));
                    step = (step + 1) % 64;
                }
                double left = 0, right = 0;
                for (Voice voice : voices) if (voice != null && voice.alive) {
                    double value = voice.sample() * 0.5;
                    left += value * voice.left;
                    right += value * voice.right;
                }
                int slot = (int) (pos % echoLeft.length);
                double wetLeft = echoLeft[slot], wetRight = echoRight[slot];
                echoLeft[slot] = left * 0.34 + wetLeft * 0.42;
                echoRight[slot] = right * 0.34 + wetRight * 0.42;
                out[i * 2] = clip((left + wetLeft * echo) * 1.35);
                out[i * 2 + 1] = clip((right + wetRight * echo) * 1.35);
                pos++;
            }
            return frames;
        }

        /** One 16th of the loop: pads and the root note once per bar, then whatever the station's patterns ask for. */
        private void schedule() {
            int inBar = step % 16, bar = step / 16 % 4;
            int degree = CHORDS[index][bar];
            int[] tones = {degree, degree + 2, degree + 4};
            double barFrames = stepFrames * 16;
            double[] mix = MIX[index];
            if (inBar == 0) {
                for (int t = 0; t < 3; t++)
                    add(tone(tones[t], station.padWave(), mix[6] * (t == 1 ? 0.75 : 1), 0.9, 3.0, barFrames * 1.25, (t - 1) * 0.26));
                add(tone(degree - 7, station.bassWave(), mix[3] * 0.8, 0.8, 3.2, barFrames * 1.25, 0));
            }
            if (hit(KICK[index], inBar)) add(tone(degree - 12, 6, mix[0], 0.001, 0.3, RATE * 0.5, 0));
            if (hit(SNARE[index], inBar)) add(tone(degree, 7, mix[1], 0.001, 0.14, RATE * 0.3, 0.12));
            if (hit(HAT[index], inBar)) add(tone(degree, 8, mix[2], 0.001, 0.05, RATE * 0.12, chanced(0.5) ? 0.35 : -0.35));
            if (hit(BASS[index], inBar)) add(tone(degree - 7, station.bassWave(), mix[3], 0.012, 0.55, stepFrames * 2.4, 0));
            if (hit(ARP[index], inBar))
                add(tone(tones[(step / 2) % 3] + (step % 8 < 4 ? 0 : 7), station.leadWave(), mix[4], 0.008, 0.4, stepFrames * 2.2,
                        (step % 4 < 2 ? -1 : 1) * 0.3));
            if (hit(LEAD[index], inBar)) {
                int move = (int) Math.round((next() - 0.5) * 4);
                lastLead = Math.clamp(lastLead + move, -3, 9);
                add(tone(lastLead, station.leadWave(), mix[5], 0.02, 0.9, stepFrames * 5, 0.1));
            }
        }

        private boolean hit(String pattern, int inBar) { return pattern.charAt(inBar) == 'x'; }
        private boolean chanced(double chance) { return next() < chance; }

        /** Deterministic pseudo-random in [0,1): the same station always plays the same music. */
        private double next() {
            random ^= random >>> 12;
            random ^= random << 25;
            random ^= random >>> 27;
            return ((random * 0x2545F4914F6CDD1DL) >>> 11) * 0x1.0p-53;
        }

        private double toneNote(int degree) {
            int[] scale = station.scale();
            int octave = Math.floorDiv(degree, scale.length), note = scale[Math.floorMod(degree, scale.length)];
            return 440 * Math.pow(2, (station.root() + note + 12 * octave - 69) / 12.0);
        }

        private Voice tone(int degree, int wave, double amp, double attack, double tau, double life, double pan) {
            if (amp <= 0.001) return null;
            Voice voice = new Voice();
            voice.freq = toneNote(degree);
            voice.wave = wave;
            voice.attack = Math.max(1, attack * RATE);
            voice.tau = Math.max(0.02, tau) * RATE;
            voice.life = Math.max(voice.attack + 64, life);
            voice.release = Math.min(voice.life * 0.45, RATE * 1.2);
            voice.amp = amp;
            voice.left = 1 - Math.max(0, pan);
            voice.right = 1 + Math.min(0, pan);
            return voice;
        }

        private void add(Voice voice) {
            if (voice != null) voices[cursor++ % VOICES] = voice;
        }

        /** One note, a drum or a bell: raw waveform, one envelope, a low-pass that keeps bright waves soft. */
        private static final class Voice {
            double freq, phase, amp, attack, tau, life, release, left, right, lowPass;
            int wave;
            double age;
            boolean alive = true;

            double sample() {
                double seconds = age / RATE;
                double env = age < attack ? age / attack : Math.exp(-(age - attack) / tau);
                env *= Math.min(1, Math.max(0, (life - age) / release));
                double value;
                switch (wave) {
                    case 6 -> { // kick: the pitch falls from 130 Hz to 45 Hz
                        phase += (45 + 85 * Math.exp(-seconds / 0.045)) / RATE;
                        value = Math.sin(phase * TAU);
                    }
                    case 7 -> { // snare: noise plus a little body
                        phase += freq / RATE;
                        value = 0.85 * noise() + 0.3 * Math.sin(phase * TAU);
                    }
                    case 8 -> { // hat: high-passed noise
                        double n = noise();
                        lowPass += (n - lowPass) * 0.65;
                        value = n - lowPass;
                    }
                    default -> {
                        phase += freq / RATE;
                        if (phase >= 1) phase -= 1;
                        if (phase < 0) phase += 1;
                        value = switch (wave) {
                            case 1 -> 4 * Math.abs(phase - 0.5) - 1;
                            case 2 -> 2 * phase - 1;
                            case 3 -> phase < 0.5 ? 0.9 : -0.9;
                            case 4 -> Math.sin(phase * TAU) + 0.42 * Math.sin(phase * TAU * 2.01) + 0.16 * Math.sin(phase * TAU * 3.02);
                            case 9 -> 2 * phase - 1;
                            default -> Math.sin(phase * TAU);
                        };
                        if (wave == 2 || wave == 9) { lowPass += (value - lowPass) * 0.2; value = lowPass * 1.18; }
                        if (wave == 3) { lowPass += (value - lowPass) * 0.16; value = lowPass * 1.25; }
                    }
                }
                age += 1;
                if (age >= life) alive = false;
                return value * env * amp;
            }

            private int noiseState = 1;

            private double noise() {
                noiseState = noiseState * 1664525 + 1013904223;
                return ((noiseState >>> 8) & 0xFFFFF) / 524288.0 - 1;
            }
        }

        private static short clip(double value) {
            double limited = value / (1 + Math.abs(value) * 0.35);
            return (short) Math.round(Math.clamp(limited, -1, 1) * 32000);
        }
    }
}
