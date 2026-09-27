package tech.gulp.lavavisual.config;

/**
 * Rainbow phase that advances by elapsed time times the current speed. Changing the speed only changes how fast the
 * phase moves from now on, so dragging the speed slider never makes the colours jump (a hue computed as absolute
 * time x speed would flicker while the slider moves).
 */
public final class ChromaClock {
    private static long last;
    private static double phase;
    private ChromaClock() { }
    /** Hue in 0..1; one full cycle takes about 8 s at speed 1. */
    public static double phase(double speed) {
        long now = System.nanoTime();
        if (last != 0) {
            double dt = Math.min(0.25, Math.max(0, (now - last) / 1e9));
            phase += dt * 0.12 * speed;
            phase -= Math.floor(phase);
        }
        last = now;
        return phase;
    }
}
