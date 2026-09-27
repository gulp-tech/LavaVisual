package tech.gulp.lavavisual.hud;

/** Real clicks per second from mouse button presses (fed by MouseHandlerMixin), not only hits on entities. */
public final class ClickCounter {
    private static final long[] LEFT = new long[64], RIGHT = new long[64];
    private static int left, right;
    private ClickCounter() { }
    public static void press(boolean leftButton) {
        long now = System.nanoTime();
        if (leftButton) LEFT[left++ & 63] = now; else RIGHT[right++ & 63] = now;
    }
    public static int cps(boolean leftButton) {
        long now = System.nanoTime(); int count = 0;
        for (long time : leftButton ? LEFT : RIGHT) if (time != 0 && now - time <= 1_000_000_000L) count++;
        return count;
    }
}
