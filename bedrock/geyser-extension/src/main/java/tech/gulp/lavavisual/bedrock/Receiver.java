package tech.gulp.lavavisual.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Reads what LavaVisual players share, the same way the Java mod does (effects/HatSync.java, frames v4).
 *
 * A LavaVisual player keeps bit 0x80 of their skin-part settings high while sharing and sends a frame by toggling it
 * in 500 ms slots (1000 ms in the frames of 1.0.0): a preamble, the payload with a 0 stuffed after two 1s, and a
 * check. Every player sees that bit in the other players' entity data, and so does Geyser for its Bedrock players.
 * Only the outfit frame (cape and accessories) is used here; hat frames are read and skipped.
 */
final class Receiver {
    /** Cape and accessories of a player: extras is a bit mask (1 glasses, 2 headphones, 4 scarf), colours are codes (0 = rainbow). */
    record Outfit(int cape, int capeColor, int capeStyle, int extras, int extrasColor) { }

    static final int[] PREAMBLE = {0, 0, 0, 1}, OUTFIT = {0, 0, 1, 0};
    static final int HAT_BITS = 21, OUTFIT_BITS = 22;
    static final long SLOT = 500, LEGACY_SLOT = 1000, IDLE_BEFORE_FRAME = 3000;
    /** The accessories stay while the player's bit was high this recently (the mod keeps it high while sharing). */
    static final long MARK_MEMORY = 30_000;
    private static final long UNSEEN_RESTART = 2000, FORGET = 600_000;
    private static final long[] SLOTS = {SLOT, LEGACY_SLOT};
    private final Map<UUID, Peer> peers = new HashMap<>();

    /** The skin-part bit of a player, as it arrived. */
    synchronized void observe(UUID id, boolean bit, long now) {
        Peer peer = peers.computeIfAbsent(id, key -> new Peer());
        if (now - peer.seen > UNSEEN_RESTART) peer.restart();
        peer.seen = now;
        peer.current = bit;
        peer.sample(now, bit);
    }

    /** Runs every tick: samples the players someone can see, so frames complete even when the bit stops changing. */
    synchronized void tick(long now, Predicate<UUID> visible) {
        for (Iterator<Map.Entry<UUID, Peer>> it = peers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Peer> entry = it.next();
            Peer peer = entry.getValue();
            if (visible.test(entry.getKey())) {
                if (now - peer.seen > UNSEEN_RESTART) peer.restart();
                peer.seen = now;
                peer.sample(now, peer.current);
            } else if (now - peer.seen > FORGET) {
                it.remove();
            }
        }
    }

    /** The outfit the player shows right now, or null (nothing shared, no accessories, or sharing stopped). */
    synchronized Outfit active(UUID id, long now) {
        Peer peer = peers.get(id);
        if (peer == null || peer.outfit == null || peer.outfit.extras() == 0 || peer.lastHigh < 0) return null;
        return now - peer.lastHigh <= MARK_MEMORY ? peer.outfit : null;
    }

    /** Players with a decoded outfit (shown or recently shown). */
    synchronized List<UUID> dressed() {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, Peer> entry : peers.entrySet()) if (entry.getValue().outfit != null) ids.add(entry.getKey());
        return ids;
    }

    synchronized int size() { return peers.size(); }

    static final class Peer {
        final long[] times = new long[128];
        final boolean[] values = new boolean[128];
        int size, head;
        boolean last, known, current;
        long highSince = -1, lastHigh = -1, seen;
        final ArrayList<Long> candidates = new ArrayList<>(4);
        Outfit outfit;
        int hatPayload = -1;

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
        /** Average offset of the edges after start from the slot grid, so samples sit in the middle of the slots. */
        long phase(long start, long now, long slot) {
            long sum = 0;
            int count = 1;
            for (int i = 1; i <= size; i++) {
                int index = Math.floorMod(head - i, times.length);
                long t = times[index];
                if (t <= start) break;
                if (t > now) continue;
                long k = Math.round((t - start) / (double) slot);
                if (k < 1 || k > 44) continue;
                sum += t - start - k * slot;
                count++;
            }
            return Math.clamp(sum / count, -slot / 3, slot / 3);
        }
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
                boolean waiting = false;
                int kind = 0, payload = 0;
                long slot = SLOT;
                for (long length : SLOTS) {
                    int result = decode(this, start, now, length);
                    if (result >= 0) { kind = 1; payload = result; slot = length; break; }
                    if (result == -1) waiting = true;
                    result = decodeOutfit(this, start, now, length);
                    if (result >= 0) { kind = 2; payload = result; slot = length; break; }
                    if (result == -1) waiting = true;
                }
                if (kind == 0) { if (!waiting) it.remove(); continue; }
                it.remove();
                if (kind == 1) hatPayload = payload;
                else outfit = new Outfit(payload >>> 19 & 7, payload >>> 14 & 31, Math.min(2, payload >>> 12 & 3), payload >>> 9 & 7, payload >>> 4 & 31);
                decoded = true;
                long end = start + 40 * slot;
                candidates.removeIf(other -> other < end);
                break;
            }
            return decoded;
        }
    }

    // ------------------------------------------------------------------------------------------------ frames

    static int pack(int hat, int hatColor, int wings, int wingColor) {
        return hat << 17 | hatColor << 12 | wings << 9 | wingColor << 4 | check(hat, hatColor, wings, wingColor);
    }
    static int check(int hat, int hatColor, int wings, int wingColor) {
        return (hat + 3 * hatColor + 5 * wings + 7 * wingColor + 9 * (hatColor >> 4) + 13 * (wingColor >> 4)) & 15;
    }
    static int packOutfit(int cape, int capeColor, int capeStyle, int extras, int extrasColor) {
        return cape << 19 | capeColor << 14 | capeStyle << 12 | extras << 9 | extrasColor << 4 | checkOutfit(cape, capeColor, capeStyle, extras, extrasColor);
    }
    static int checkOutfit(int cape, int capeColor, int capeStyle, int extras, int extrasColor) {
        return (11 + cape + 3 * capeColor + 5 * capeStyle + 7 * extras + 9 * extrasColor + 13 * (capeColor >> 4) + 15 * (extrasColor >> 4)) & 15;
    }
    /** Bits of a frame as the mod sends them: preamble, payload (most significant first), a 0 after two 1s. */
    static int[] encode(int[] preamble, int payload, int count) {
        ArrayList<Integer> bits = new ArrayList<>(44);
        for (int bit : preamble) bits.add(bit);
        int ones = preamble[preamble.length - 1];
        for (int i = count - 1; i >= 0; i--) {
            int bit = payload >>> i & 1;
            bits.add(bit);
            ones = bit == 1 ? ones + 1 : 0;
            if (ones == 2) { bits.add(0); ones = 0; }
        }
        int[] result = new int[bits.size()];
        for (int i = 0; i < result.length; i++) result[i] = bits.get(i);
        return result;
    }
    /** Payload (21 bits) of a hat frame that started at start; -1 while incomplete, -2 when it is not one. */
    static int decode(Peer peer, long start, long now, long slot) {
        int payload = read(peer, start, now, PREAMBLE, HAT_BITS, slot);
        if (payload < 0) return payload;
        return check(payload >>> 17 & 15, payload >>> 12 & 31, payload >>> 9 & 7, payload >>> 4 & 31) == (payload & 15) ? payload : -2;
    }
    /** Payload (22 bits) of an outfit frame; -1 while incomplete, -2 when it is not one. */
    static int decodeOutfit(Peer peer, long start, long now, long slot) {
        int payload = read(peer, start, now, OUTFIT, OUTFIT_BITS, slot);
        if (payload < 0) return payload;
        return checkOutfit(payload >>> 19 & 7, payload >>> 14 & 31, payload >>> 12 & 3, payload >>> 9 & 7, payload >>> 4 & 31) == (payload & 15) ? payload : -2;
    }
    private static int read(Peer peer, long start, long now, int[] preamble, int bits, long slot) {
        long origin = start + peer.phase(start, now, slot) + slot / 2;
        int index = 0;
        for (int expected : preamble) {
            long time = origin + index++ * slot;
            if (time > now) return -1;
            if (peer.at(time) != expected) return -2;
        }
        int payload = 0, ones = preamble[preamble.length - 1], count = 0;
        while (count < bits) {
            long time = origin + index++ * slot;
            if (time > now) return -1;
            int bit = peer.at(time);
            if (bit < 0) return -2;
            if (ones == 2) { if (bit != 0) return -2; ones = 0; continue; }
            payload = payload << 1 | bit;
            count++;
            ones = bit == 1 ? ones + 1 : 0;
        }
        return payload;
    }
}
