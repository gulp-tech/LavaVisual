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
 * spells out a short frame, one bit per second: 0 0 0 1, then 21 data bits (hat 4, hat colour 5, wings 3, wing
 * colour 5, check 4) with a 0 stuffed after every two 1s, so no run of 1s inside a frame lasts more than two seconds
 * and a frame can only start after three or more seconds of idle 1. That is at most one settings packet per second,
 * only while other players are around, and a frame takes about half a minute. Receivers sample the bit every tick.
 * Colour codes: 0 rainbow, 1..27 hues, 28..31 white, light grey, dark grey, black. Frames with another preamble
 * (the first protocol used 0 0 1 + RGB444) are ignored.
 *
 * Cape and accessories follow a few seconds after the hat frame in a second frame, 0 0 1 0 then 22 data bits (cape 3,
 * cape colour 5, cape style 2, accessories 3 as a bit mask, accessory colour 5, check 4). Versions without it reject
 * that preamble and simply keep ignoring the frame.
 */
public final class HatSync {
    /** Shared cosmetics of another player; type 0 = none. */
    public record Remote(int hat, int hatRgb, boolean hatRainbow, int wings, int wingRgb, boolean wingRainbow) { }
    /** Shared cape and accessories of another player; cape 0 = none, extras is a bit mask (1 glasses, 2 headphones, 4 scarf). */
    public record Outfit(int cape, int capeRgb, boolean capeRainbow, int capeStyle, int extras, int extrasRgb, boolean extrasRainbow) { }
    private static final int[] PREAMBLE = {0, 0, 0, 1}, OUTFIT = {0, 0, 1, 0};
    static final int HAT_BITS = 21, OUTFIT_BITS = 22;
    private static int lastOutfit = Integer.MIN_VALUE, outfitSent;
    private static boolean outfitDue;
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
            int current = signature(c), outfit = outfitSignature(c);
            if (current != signature || outfit != lastOutfit) { signature = current; lastOutfit = outfit; dirtyAt = now; }
            if (frame == null && now - highSince >= IDLE_BEFORE_FRAME + 2000) {
                if (outfitDue && now - frameEnd >= 2000) {
                    frame = encode(OUTFIT, outfit, OUTFIT_BITS);
                    frameStart = now; outfitDue = false; outfitSent = outfit;
                } else if (now - frameEnd >= 10_000 && wanted(mc, now)) {
                    frame = encode(current);
                    frameStart = lastFrameStart = now; dirtyAt = -1; pendingMarked = pendingAny = false;
                    // The outfit frame follows when there is a cape or accessory to show (or one to take off).
                    outfitDue = hasOutfit(outfit) || hasOutfit(outfitSent);
                }
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
        lastOutfit = Integer.MIN_VALUE; outfitSent = 0; outfitDue = false;
        frameEnd = lastFrameStart = -1_000_000; pendingMarked = pendingAny = false;
        NEARBY.clear(); PEERS.clear();
    }

    /** hat 4 | hat colour 5 | wings 3 | wing colour 5 | check 4, as the low 21 bits. */
    private static int signature(tech.gulp.lavavisual.config.HudConfig c) {
        int hat = c.hatEnabled ? Math.clamp(c.hatType, 1, 15) : 0, wings = c.wingsEnabled ? Math.clamp(c.wingsType, 1, 7) : 0;
        return pack(hat, colorCode(c, "hat"), wings, colorCode(c, "wings"));
    }
    /** cape 3 | cape colour 5 | cape style 2 | accessories 3 | accessory colour 5 | check 4, as the low 22 bits. */
    private static int outfitSignature(tech.gulp.lavavisual.config.HudConfig c) {
        int cape = c.capeEnabled ? Math.clamp(c.capeType, 1, 7) : 0, extras = 0;
        for (int i = 1; i <= 3; i++) if (c.extras.contains(i)) extras |= 1 << (i - 1);
        return packOutfit(cape, colorCode(c, "cape"), Math.floorMod(c.capeStyle, 3), extras, colorCode(c, "outfit"));
    }
    private static boolean hasOutfit(int payload) { return (payload >>> 19 & 7) != 0 || (payload >>> 9 & 7) != 0; }
    static int packOutfit(int cape, int capeColor, int capeStyle, int extras, int extrasColor) {
        return cape << 19 | capeColor << 14 | capeStyle << 12 | extras << 9 | extrasColor << 4 | checkOutfit(cape, capeColor, capeStyle, extras, extrasColor);
    }
    private static int checkOutfit(int cape, int capeColor, int capeStyle, int extras, int extrasColor) {
        return (11 + cape + 3 * capeColor + 5 * capeStyle + 7 * extras + 9 * extrasColor + 13 * (capeColor >> 4) + 15 * (extrasColor >> 4)) & 15;
    }
    private static Outfit outfitOf(int payload) {
        int capeColor = payload >>> 14 & 31, extrasColor = payload >>> 4 & 31;
        return new Outfit(payload >>> 19 & 7, color(capeColor), capeColor == 0, Math.min(2, payload >>> 12 & 3), payload >>> 9 & 7, color(extrasColor), extrasColor == 0);
    }
    private static int colorCode(tech.gulp.lavavisual.config.HudConfig c, String key) {
        if (c.chroma != null && c.chroma.contains(key)) return 0;
        double[] hsv = tech.gulp.lavavisual.config.ColorMath.toHsv(c.color(key) & 0xFFFFFF);
        if (hsv[1] < 0.22) return hsv[2] > 0.85 ? 28 : hsv[2] > 0.55 ? 29 : hsv[2] > 0.25 ? 30 : 31;
        return 1 + (int) Math.round(hsv[0] * 27) % 27;
    }
    static int color(int code) {
        return switch (code) {
            case 28 -> 0xF2F2F2;
            case 29 -> 0xA8ADB6;
            case 30 -> 0x555A63;
            case 31 -> 0x1A1B20;
            default -> tech.gulp.lavavisual.config.ColorMath.hsv((code - 1) / 27.0, 0.75, 1) & 0xFFFFFF;
        };
    }
    static int pack(int hat, int hatColor, int wings, int wingColor) {
        return hat << 17 | hatColor << 12 | wings << 9 | wingColor << 4 | check(hat, hatColor, wings, wingColor);
    }
    private static int check(int hat, int hatColor, int wings, int wingColor) {
        return (hat + 3 * hatColor + 5 * wings + 7 * wingColor + 9 * (hatColor >> 4) + 13 * (wingColor >> 4)) & 15;
    }
    static int[] encode(int payload) { return encode(PREAMBLE, payload, HAT_BITS); }
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
        Outfit outfit;

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
                if (result >= 0) {
                    it.remove();
                    int hatColor = result >>> 12 & 31, wingColor = result >>> 4 & 31;
                    hat = new Remote(result >>> 17 & 15, color(hatColor), hatColor == 0, result >>> 9 & 7, color(wingColor), wingColor == 0);
                    decoded = true;
                    candidates.removeIf(other -> other < start + 40 * SLOT);
                    break;
                }
                int dressed = decodeOutfit(this, start, now);
                if (dressed == -1) continue;
                it.remove();
                if (dressed >= 0) {
                    outfit = outfitOf(dressed);
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
        int payload = read(peer, start, now, PREAMBLE, HAT_BITS);
        if (payload < 0) return payload;
        return check(payload >>> 17 & 15, payload >>> 12 & 31, payload >>> 9 & 7, payload >>> 4 & 31) == (payload & 15) ? payload : -2;
    }
    /** Payload (22 bits) of an outfit frame; -1 while incomplete, -2 when it is not one. */
    static int decodeOutfit(Peer peer, long start, long now) {
        int payload = read(peer, start, now, OUTFIT, OUTFIT_BITS);
        if (payload < 0) return payload;
        return checkOutfit(payload >>> 19 & 7, payload >>> 14 & 31, payload >>> 12 & 3, payload >>> 9 & 7, payload >>> 4 & 31) == (payload & 15) ? payload : -2;
    }
    private static int read(Peer peer, long start, long now, int[] preamble, int bits) {
        int slot = 0;
        for (int expected : preamble) {
            long time = start + slot++ * SLOT + SLOT / 2;
            if (time > now) return -1;
            if (peer.at(time) != expected) return -2;
        }
        int payload = 0, ones = preamble[preamble.length - 1], count = 0;
        while (count < bits) {
            long time = start + slot++ * SLOT + SLOT / 2;
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
    /** What another LavaVisual player shares (hat and/or wings), or null. */
    public static Remote of(Entity entity) {
        Peer peer = peer(entity);
        if (peer == null || peer.hat == null || peer.hat.hat() == 0 && peer.hat.wings() == 0 || peer.lastHigh < 0) return null;
        return System.nanoTime() / 1_000_000L - peer.lastHigh <= MARK_MEMORY ? peer.hat : null;
    }
    /** Cape and accessories another LavaVisual player shares, or null. */
    public static Outfit outfit(Entity entity) {
        Peer peer = peer(entity);
        if (peer == null || peer.outfit == null || peer.outfit.cape() == 0 && peer.outfit.extras() == 0 || peer.lastHigh < 0) return null;
        return System.nanoTime() / 1_000_000L - peer.lastHigh <= MARK_MEMORY ? peer.outfit : null;
    }
    public static boolean any() {
        for (Peer peer : PEERS.values()) {
            if (peer.hat != null && (peer.hat.hat() != 0 || peer.hat.wings() != 0)) return true;
            if (peer.outfit != null && (peer.outfit.cape() != 0 || peer.outfit.extras() != 0)) return true;
        }
        return false;
    }

    /** CI check: frames survive jitter and bit stuffing; mid-frame starts are never taken for frames. */
    public static String selfTest() {
        int[][] cases = {{3, 5, 2, 0}, {14, 31, 5, 31}, {1, 0, 0, 0}, {0, 28, 1, 17}, {7, 13, 7, 27}, {15, 31, 7, 31}};
        java.util.Random random = new java.util.Random(2612);
        for (int[] test : cases) {
            int[] bits = encode(pack(test[0], test[1], test[2], test[3]));
            int run = 0;
            for (int i = PREAMBLE.length - 1; i < bits.length; i++) { run = bits[i] == 1 ? run + 1 : 0; if (run > 2) return "LavaVisual hat sync self-test failed: run of " + run; }
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
            if (decoded == null || decoded.hat() != test[0] || decoded.hatRgb() != color(test[1]) || decoded.hatRainbow() != (test[1] == 0)
                    || decoded.wings() != test[2] || decoded.wingRgb() != color(test[3]) || decoded.wingRainbow() != (test[3] == 0))
                return "LavaVisual hat sync self-test failed: " + java.util.Arrays.toString(test) + " -> " + decoded;
        }
        int[][] outfits = {{2, 5, 1, 3, 17}, {6, 0, 2, 7, 0}, {1, 31, 0, 0, 28}, {0, 13, 0, 4, 9}, {7, 31, 2, 7, 31}};
        for (int[] test : outfits) {
            int[] bits = encode(OUTFIT, packOutfit(test[0], test[1], test[2], test[3], test[4]), OUTFIT_BITS);
            Peer peer = new Peer();
            long base = 1_000_000, start = base + 8000;
            long[] arrive = new long[bits.length + 1];
            for (int i = 0; i <= bits.length; i++) arrive[i] = start + i * SLOT + 40 + random.nextInt(210);
            Outfit decoded = null;
            for (long now = base; now < start + (bits.length + 6) * SLOT; now += 50) {
                boolean bit = true;
                for (int i = 0; i < bits.length; i++) if (now >= arrive[i] && now < arrive[i + 1]) bit = bits[i] == 1;
                if (peer.sample(now, bit)) decoded = peer.outfit;
            }
            if (decoded == null || decoded.cape() != test[0] || decoded.capeRgb() != color(test[1]) || decoded.capeRainbow() != (test[1] == 0)
                    || decoded.capeStyle() != test[2] || decoded.extras() != test[3] || decoded.extrasRgb() != color(test[4]) || peer.hat != null)
                return "LavaVisual hat sync self-test failed: outfit " + java.util.Arrays.toString(test) + " -> " + decoded;
        }
        return "LavaVisual hat sync self-test passed";
    }
}
