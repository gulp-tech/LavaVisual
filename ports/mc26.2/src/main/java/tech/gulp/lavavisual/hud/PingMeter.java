package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

/**
 * Live round trip to the server for the watermark, measured with the vanilla ping request (the packet behind the F3
 * network chart). The tab-list latency is a slow average that the server refreshes every 30 seconds and that includes
 * its own tick delay; this is the current network delay. One tiny packet every 2 seconds, only while the ping is on
 * screen; servers that do not answer are left alone after three tries (then the tab-list value is shown).
 */
public final class PingMeter {
    private static final long INTERVAL = 2000, TIMEOUT = 5000, SHOWN_FOR = 3000;
    private static volatile long pending = -1;
    private static volatile int smoothed = -1;
    private static volatile long lastPong = -1;
    private static long nextAt, shownAt = -1;
    private static int misses;
    private static Object connection;
    private PingMeter() { }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    /** Called by the watermark whenever it draws the ping. */
    static void shown() { shownAt = now(); }

    public static void tick(Minecraft mc) {
        var conn = mc.getConnection();
        long now = now();
        if (conn == null || mc.player == null || mc.isLocalServer()) { connection = null; return; }
        if (conn != connection) {
            connection = conn; pending = -1; smoothed = -1; lastPong = -1; misses = 0; nextAt = now + 3000;
        }
        long sent = pending;
        if (sent >= 0 && now - sent > TIMEOUT) {
            pending = -1;
            if (++misses >= 3) nextAt = Long.MAX_VALUE;
        }
        if (pending < 0 && now >= nextAt && shownAt >= 0 && now - shownAt < SHOWN_FOR) {
            nextAt = now + INTERVAL;
            pending = now;
            conn.send(new ServerboundPingRequestPacket(now));
        }
    }

    /** Pong seen by the packet listener (network or game thread); only answers to our own request count. */
    public static void pong(long time) {
        long sent = pending;
        if (sent < 0 || time != sent) return;
        pending = -1;
        long now = now(), rtt = now - time;
        if (rtt < 0 || rtt > 10_000) return;
        int previous = smoothed;
        smoothed = previous < 0 ? (int) rtt : (int) Math.round(previous * 0.6 + rtt * 0.4);
        lastPong = now;
        misses = 0;
    }

    /** Current round trip in ms, or -1 when unknown. */
    public static int latency() {
        long last = lastPong;
        return last >= 0 && now() - last < 10_000 ? smoothed : -1;
    }
}
