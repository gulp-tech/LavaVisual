package tech.gulp.lavavisual.effects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Serverless hat sharing between LavaVisual players (opt-in together with the badge, see Badge).
 *
 * The only client-controlled value that every vanilla server relays to other players without drawing anything is
 * the unused 0x80 bit of the skin-parts byte. While sharing, the bit stays set (that is the badge); now and then it
 * spells out a short frame, one bit per second: 0 0 1, then 21 data bits (hat type 4, colour RGB444 12, rainbow 1,
 * check 4) with a 0 stuffed after every two 1s, so no run of 1s inside a frame lasts more than two seconds and a
 * frame can only start after three or more seconds of idle 1. That is at most one settings packet per second, only
 * while other players are around, and a frame takes about half a minute. Receivers sample the bit every tick.
 */
public final class HatSync {
    public record Remote(int type, int rgb, boolean rainbow) { }
    static final long SLOT = 1000, IDLE_BEFORE_FRAME = 3000;
    private static final long JOIN_DELAY = 10_000, MARK_MEMORY = 30_000, PEER_MEMORY = 600_000, NEW_PLAYER_MEMORY = 300_000;
    // sender
    private static Object level;
    private static int[] frame;
    private static long frameStart, frameEnd = -1_000_000, lastFrameStart = -1_000_000, joinedAt, highSince = -1, dirtyAt = -1;
    private static int signature = Integer.MIN_VALUE;
    private static boolean advertised, pendingMarked, pendingAny;
    private static final Map<UUID, Long> NEARBY = new HashMap<>();
    // receiver
    private static final Map<UUID, Peer> PEERS = new HashMap<>();
    private static long lastCleanup;
    private HatSync() { }

    /** Bit 0x80 as it should be sent right now (read by Badge when the client information is built). */
    public static boolean advertised() { return advertised; }

    public static void tick(Minecraft mc) {
        long now = System.nanoTime() / 1_000_000L;
        if (mc.player == null || mc.level == null) { reset(); return; }
        if (mc.level != level) { reset(); level = mc.level; joinedAt = now; }
        receive(mc, now);
        var c = LavaVisualClient.config();
        boolean share = c.badgeShare && now - joinedAt >= JOIN_DELAY;
        boolean bit = share;
        if (!share) { frame = null; highSince = -1; }
        else {
            if (highSince < 0) highSince = now;
            int current = signature(c);
            if (current != signature) { signature = current; dirtyAt = now; }
            if (frame == null && now - highSince >= IDLE_BEFORE_FRAME + 2000 && now - frameEnd >= 10_000 && wanted(mc, now)) {
                frame = encode(current >>> 17 & 15, current >>> 5 & 0xFFF, (current >>> 4 & 1) == 1);
                frameStart = lastFrameStart = now; dirtyAt = -1; pendingMarked = pendingAny = false;
            }
            if (frame != null) {
                int slot = (int) ((now - frameStart) / SLOT);
                if (slot >= frame.length) { frame = null; frameEnd = now; highSince = now; }
                else bit = frame[slot] == 1;
            }
        }
        if (bit != advertised) {
            advertised = bit;
            mc.options.broadcastOptions();
        }
    }

    private static void reset() {
        level = null; frame = null; advertised = false; highSince = -1; dirtyAt = -1; signature = Integer.MIN_VALUE;
        frameEnd = lastFrameStart = -1_000_000; pendingMarked = pendingAny = false;
        NEARBY.clear(); PEERS.clear();
    }

    /** type 4 | rgb444 12 | rainbow 1 | check 4, as the low 21 bits. */
    private static int signature(tech.gulp.lavavisual.config.HudConfig c) {
        boolean rainbow = c.chroma != null && c.chroma.contains("hat");
        int type = c.hatEnabled ? Math.clamp(c.hatType, 1, 15) : 0;
        int rgb = rainbow ? 0 : c.color("hat") & 0xFFFFFF;
        int rgb444 = (rgb >> 20 & 15) << 8 | (rgb >> 12 & 15) << 4 | (rgb >> 4 & 15);
        return pack(type, rgb444, rainbow);
    }
    static int pack(int type, int rgb444, boolean rainbow) {
        int check = check(type, rgb444, rainbow);
        return type << 17 | rgb444 << 5 | (rainbow ? 1 : 0) << 4 | check;
    }
    private static int check(int type, int rgb444, boolean rainbow) {
        return (type + 3 * (rgb444 >> 8 & 15) + 5 * (rgb444 >> 4 & 15) + 7 * (rgb444 & 15) + (rainbow ? 11 : 0)) & 15;
    }
    static int[] encode(int type, int rgb444, boolean rainbow) {
        int payload = pack(type, rgb444, rainbow);
        ArrayList<Integer> bits = new ArrayList<>(40);
        bits.add(0); bits.add(0); bits.add(1);
        int ones = 1;
        for (int i = 20; i >= 0; i--) {
            int bit = payload >>> i & 1;
            bits.add(bit);
            ones = bit == 1 ? ones + 1 : 0;
            if (ones == 2) { bits.add(0); ones = 0; }
        }
        int[] result = new int[bits.size()];
        for (int i = 0; i < result.length; i++) result[i] = bits.get(i);
        return result;
    }

    /** Send only when someone can see it: hat changed, a LavaVisual player or any new player came near, or a rare refresh. */
    private static boolean wanted(Minecraft mc, long now) {
        int others = 0;
        for (Player player : mc.level.players()) {
            if (player == mc.player || player.distanceToSqr(mc.player) > 64 * 64) continue;
            others++;
            Long seen = NEARBY.put(player.getUUID(), now);
            if (seen == null || now - seen > NEW_PLAYER_MEMORY) {
                pendingAny = true;
                if (Badge.marked(player)) pendingMarked = true;
            }
        }
        if (NEARBY.size() > 256) NEARBY.values().removeIf(seen -> now - seen > NEW_PLAYER_MEMORY);
        if (others == 0) return false;
        long since = now - lastFrameStart;
        return (dirtyAt >= 0 && now - dirtyAt >= 3000)
                || (pendingMarked && since >= 45_000)
                || (pendingAny && since >= 150_000)
                || since >= 600_000;
    }

    // ------------------------------------------------------------------------------------------------ receiver

    private static final class Peer {
        final long[] times = new long[128];
        final boolean[] values = new boolean[128];
        int size, head;
        boolean last, known;
        long highSince = -1, lastHigh = -1, seen;
        final ArrayList<Long> candidates = new ArrayList<>(4);
        Remote hat;

        void record(long time, boolean value) {
            times[head] = time; values[head] = value;
            head = (head + 1) % times.length; size = Math.min(size + 1, times.length);
        }
        /** Bit at the given time, or -1 when it is older than the recorded history. */
        int at(long time) {
            for (int i = 1; i <= size; i++) {
                int index = Math.floorMod(head - i, times.length);
                if (times[index] <= time) return values[index] ? 1 : 0;
            }
            return -1;
        }
        void restart() { size = 0; head = 0; known = false; highSince = -1; candidates.clear(); }
        /** Feeds one sample; returns true when a frame was decoded. */
        boolean sample(long now, boolean bit) {
            if (!known || bit != last) {
                if (known && last && !bit && highSince >= 0 && now - highSince >= IDLE_BEFORE_FRAME && candidates.size() < 4) candidates.add(now);
                record(now, bit);
                if (bit) highSince = now;
                last = bit; known = true;
            }
            if (bit) lastHigh = now;
            boolean decoded = false;
            for (Iterator<Long> it = candidates.iterator(); it.hasNext(); ) {
                long start = it.next();
                int result = decode(this, start, now);
                if (result == -1) continue;
                it.remove();
                if (result >= 0) {
                    hat = new Remote(result >>> 17 & 15, expand(result >>> 5 & 0xFFF), (result >>> 4 & 1) == 1);
                    decoded = true;
                    candidates.removeIf(other -> other < start + 40 * SLOT);
                    break;
                }
            }
            return decoded;
        }
    }

    /** Payload (21 bits) of the frame that started at start; -1 while incomplete, -2 when it is not a valid frame. */
    static int decode(Peer peer, long start, long now) {
        int slot = 0;
        int[] preamble = {0, 0, 1};
        for (int expected : preamble) {
            long time = start + slot++ * SLOT + SLOT / 2;
            if (time > now) return -1;
            if (peer.at(time) != expected) return -2;
        }
        int payload = 0, ones = 1, count = 0;
        while (count < 21) {
            long time = start + slot++ * SLOT + SLOT / 2;
            if (time > now) return -1;
            int bit = peer.at(time);
            if (bit < 0) return -2;
            if (ones == 2) { if (bit != 0) return -2; ones = 0; continue; }
            payload = payload << 1 | bit;
            count++;
            ones = bit == 1 ? ones + 1 : 0;
        }
        int type = payload >>> 17 & 15, rgb444 = payload >>> 5 & 0xFFF;
        boolean rainbow = (payload >>> 4 & 1) == 1;
        return check(type, rgb444, rainbow) == (payload & 15) ? payload : -2;
    }
    private static int expand(int rgb444) {
        return (rgb444 >> 8 & 15) * 17 << 16 | (rgb444 >> 4 & 15) * 17 << 8 | (rgb444 & 15) * 17;
    }

    private static void receive(Minecraft mc, long now) {
        for (Player player : mc.level.players()) {
            if (player == mc.player || player.distanceToSqr(mc.player) > 96 * 96) continue;
            Peer peer = PEERS.computeIfAbsent(player.getUUID(), id -> new Peer());
            if (now - peer.seen > 2000) peer.restart();
            peer.seen = now;
            peer.sample(now, Badge.marked(player));
        }
        if (now - lastCleanup > 5000) {
            lastCleanup = now;
            PEERS.values().removeIf(peer -> now - peer.seen > PEER_MEMORY);
        }
    }

    private static Peer peer(Entity entity) {
        return entity instanceof Player player ? PEERS.get(player.getUUID()) : null;
    }
    /** Badge check without flicker while a frame is being sent. */
    public static boolean marked(Player player) {
        Peer peer = PEERS.get(player.getUUID());
        if (peer == null || peer.lastHigh < 0) return Badge.marked(player);
        return System.nanoTime() / 1_000_000L - peer.lastHigh <= MARK_MEMORY;
    }
    /** The hat another LavaVisual player shares, or null. */
    public static Remote hatOf(Entity entity) {
        Peer peer = peer(entity);
        if (peer == null || peer.hat == null || peer.hat.type() == 0 || peer.lastHigh < 0) return null;
        return System.nanoTime() / 1_000_000L - peer.lastHigh <= MARK_MEMORY ? peer.hat : null;
    }
    public static boolean any() {
        for (Peer peer : PEERS.values()) if (peer.hat != null && peer.hat.type() != 0) return true;
        return false;
    }

    /** CI check: frames survive jitter and bit stuffing; mid-frame starts are never taken for frames. */
    public static String selfTest() {
        int[][] cases = {{3, 0xF80, 0}, {14, 0xFFF, 1}, {1, 0x000, 0}, {0, 0x123, 0}, {7, 0xB6D, 1}, {15, 0xFFF, 1}};
        java.util.Random random = new java.util.Random(2612);
        for (int[] test : cases) {
            int[] bits = encode(test[0], test[1], test[2] == 1);
            int run = 0;
            for (int i = 3; i < bits.length; i++) { run = bits[i] == 1 ? run + 1 : 0; if (run > 2) return "LavaVisual hat sync self-test failed: run of " + run; }
            Peer peer = new Peer();
            long base = 1_000_000, start = base + 8000;
            // Transitions reach the receiver up to 250 ms late; it samples every 50 ms.
            long[] arrive = new long[bits.length + 1];
            for (int i = 0; i <= bits.length; i++) arrive[i] = start + i * SLOT + 40 + random.nextInt(210);
            Remote decoded = null;
            for (long now = base; now < start + (bits.length + 6) * SLOT; now += 50) {
                boolean bit = true;
                for (int i = 0; i < bits.length; i++) if (now >= arrive[i] && now < arrive[i + 1]) bit = bits[i] == 1;
                if (peer.sample(now, bit)) decoded = peer.hat;
            }
            int rgb = expand(test[1]);
            if (decoded == null || decoded.type() != test[0] || decoded.rgb() != rgb || decoded.rainbow() != (test[2] == 1))
                return "LavaVisual hat sync self-test failed: " + test[0] + "/" + Integer.toHexString(test[1]) + " -> " + decoded;
        }
        return "LavaVisual hat sync self-test passed";
    }
}
