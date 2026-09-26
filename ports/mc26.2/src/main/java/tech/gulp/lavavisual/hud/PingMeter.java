package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

/**
 * Live round trip to the server for the watermark, measured with the vanilla ping request (the packet behind the F3
 * network chart). The tab-list latency is a slow average that servers refresh every 30 seconds; this one is measured
 * every second and shown as the median of the last three answers, so one late packet does not make it jump. While an
 * answer is overdue the number counts up, so a freeze shows at once. Requests go out only while the ping is on
 * screen; servers that never answer are left alone after three tries (then the tab-list value is shown).
 */
public final class PingMeter {
    private static final long INTERVAL = 1000, TIMEOUT = 4000, SHOWN_FOR = 3000, FIRST = 800;
    private static final int[] recent = new int[3];
    private static int count, answers;
    private static volatile long pending = -1, waiting = -1;
    private static volatile int value = -1;
    private static volatile boolean answered, fresh, testing;
    private static long nextAt, shownAt = -1;
    private static boolean visible;
    private static int misses;
    private static Object connection;
    private PingMeter() { }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    /** Called by the watermark whenever it draws the ping. */
    static void shown() { shownAt = now(); }

    public static void tick(Minecraft mc) {
        var conn = mc.getConnection();
        long now = now();
        if (conn == null || mc.player == null || mc.isLocalServer() && !testing) {
            if (connection != null) reset(null, now);
            return;
        }
        if (conn != connection) reset(conn, now);
        long sent = pending;
        if (sent >= 0 && now - sent > TIMEOUT) {
            pending = -1;
            if (++misses >= 3 && !answered) nextAt = Long.MAX_VALUE;
        }
        boolean show = testing || shownAt >= 0 && now - shownAt < SHOWN_FOR;
        if (show && !visible) fresh = true;
        visible = show;
        if (!show) { waiting = -1; return; }
        if (pending < 0 && now >= nextAt) {
            nextAt = now + INTERVAL;
            pending = now;
            if (waiting < 0) waiting = now;
            conn.send(new ServerboundPingRequestPacket(now));
        }
    }

    private static synchronized void reset(Object conn, long now) {
        connection = conn;
        pending = -1; waiting = -1; value = -1; count = 0; misses = 0;
        answered = false; fresh = false; visible = false;
        nextAt = now + FIRST;
    }

    /** Pong seen by the packet listener (network or game thread); only the answer to our current request counts. */
    public static synchronized void pong(long time) {
        long sent = pending;
        if (sent < 0 || time != sent) return;
        pending = -1;
        long rtt = now() - time;
        if (rtt < 0 || rtt > 60_000) return;
        if (fresh) { count = 0; fresh = false; }
        if (count < recent.length) recent[count++] = (int) rtt;
        else { System.arraycopy(recent, 1, recent, 0, recent.length - 1); recent[recent.length - 1] = (int) rtt; }
        value = median(recent, count);
        waiting = -1;
        misses = 0;
        answered = true;
        answers++;
    }

    static int median(int[] values, int n) {
        if (n <= 0) return -1;
        if (n == 1) return values[0];
        if (n == 2) return (values[0] + values[1] + 1) / 2;
        int a = values[0], b = values[1], c = values[2];
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    /** Current round trip in ms, or -1 when the server has not answered yet. */
    public static int latency() {
        int v = value;
        if (!answered || v < 0) return -1;
        long since = waiting;
        if (since >= 0) {
            long overdue = now() - since;
            if (overdue > Math.max(2L * v, v + 150)) return (int) Math.min(overdue, 99_999);
        }
        return v;
    }

    /** CI only: measure through the integrated server, which answers the same request as a real one. */
    public static void test(boolean on) { testing = on; }

    public static synchronized String selfTest() {
        boolean math = median(new int[] {40, 900, 42}, 3) == 42 && median(new int[] {40, 60, 0}, 2) == 50
                && median(new int[] {70, 0, 0}, 1) == 70;
        int v = latency();
        boolean ok = math && answers >= 3 && v >= 0 && v < 1000;
        return (ok ? "LavaVisual smoke ping ok" : "LavaVisual smoke ping failed") + ": answers " + answers + ", ping " + v
                + " ms, median " + (math ? "ok" : "wrong");
    }
}
