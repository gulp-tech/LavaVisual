package tech.gulp.lavavisual.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Frames sent the way the mod sends them reach the receiver over a jittery connection and decode to the outfit. */
class ReceiverTest {
    private static final UUID PLAYER = UUID.fromString("5f2a8d7e-1c3b-4a6f-9e0d-2b7c4a1e8f36");

    /** A LavaVisual player: bit high while sharing, a frame as 0/1 slots, then high again. Edges arrive late. */
    private static long send(Receiver receiver, long start, int[] bits, long slot, Random random, long lateMin, long lateMax) {
        List<long[]> edges = new ArrayList<>();
        boolean level = true;
        for (int i = 0; i < bits.length; i++) {
            boolean bit = bits[i] == 1;
            if (bit != level) {
                edges.add(new long[]{start + i * slot, bit ? 1 : 0});
                level = bit;
            }
        }
        long end = start + bits.length * slot;
        if (!level) edges.add(new long[]{end, 1});
        long now = start;
        int next = 0;
        long arrival = -1;
        boolean current = true;
        while (now < end + 4000) {
            while (next < edges.size() && edges.get(next)[0] + (arrival < 0 ? (arrival = lateMin + (long) (random.nextDouble() * (lateMax - lateMin))) : arrival) <= now) {
                current = edges.get(next)[1] == 1;
                receiver.observe(PLAYER, current, edges.get(next)[0] + arrival);
                next++;
                arrival = -1;
            }
            receiver.tick(now, id -> true);
            now += 50;
        }
        return now;
    }

    private static long idle(Receiver receiver, long now, long millis) {
        receiver.observe(PLAYER, true, now);
        for (long t = now; t < now + millis; t += 50) receiver.tick(t, id -> true);
        return now + millis;
    }

    @Test
    void outfitFramesDecodeThroughJitter() {
        Random random = new Random(26);
        for (int extras = 1; extras <= 7; extras++) {
            for (int color : new int[]{0, 1, 9, 27, 28, 31}) {
                Receiver receiver = new Receiver();
                long now = idle(receiver, 10_000, 4000);
                int payload = Receiver.packOutfit(extras == 4 ? 2 : 0, 5, 1, extras, color);
                now = send(receiver, now, Receiver.encode(Receiver.OUTFIT, payload, Receiver.OUTFIT_BITS), Receiver.SLOT, random, 40, 240);
                Receiver.Outfit outfit = receiver.active(PLAYER, now);
                assertNotNull(outfit, "extras " + extras + " colour " + color);
                assertEquals(extras, outfit.extras());
                assertEquals(color, outfit.extrasColor());
            }
        }
    }

    @Test
    void framesOfTheFirstReleaseStillDecode() {
        Receiver receiver = new Receiver();
        long now = idle(receiver, 5_000, 4000);
        int payload = Receiver.packOutfit(0, 0, 0, 5, 14);
        now = send(receiver, now, Receiver.encode(Receiver.OUTFIT, payload, Receiver.OUTFIT_BITS), Receiver.LEGACY_SLOT, new Random(3), 40, 250);
        Receiver.Outfit outfit = receiver.active(PLAYER, now);
        assertNotNull(outfit);
        assertEquals(5, outfit.extras());
        assertEquals(14, outfit.extrasColor());
    }

    @Test
    void hatFramesAndNoiseDoNotDress() {
        Receiver receiver = new Receiver();
        long now = idle(receiver, 5_000, 4000);
        now = send(receiver, now, Receiver.encode(Receiver.PREAMBLE, Receiver.pack(3, 7, 2, 9), Receiver.HAT_BITS), Receiver.SLOT, new Random(5), 40, 240);
        assertNull(receiver.active(PLAYER, now));
        Random random = new Random(11);
        int[] noise = new int[60];
        for (int i = 0; i < noise.length; i++) noise[i] = random.nextInt(2);
        now = send(receiver, idle(receiver, now, 4000), noise, Receiver.SLOT, random, 40, 240);
        assertNull(receiver.active(PLAYER, now));
    }

    @Test
    void accessoriesGoAwayWhenSharingStops() {
        Receiver receiver = new Receiver();
        long now = idle(receiver, 5_000, 4000);
        now = send(receiver, now, Receiver.encode(Receiver.OUTFIT, Receiver.packOutfit(0, 0, 0, 1, 3), Receiver.OUTFIT_BITS), Receiver.SLOT, new Random(8), 40, 240);
        assertNotNull(receiver.active(PLAYER, now));
        receiver.observe(PLAYER, false, now);
        for (long t = now; t < now + Receiver.MARK_MEMORY + 1000; t += 50) receiver.tick(t, id -> true);
        assertNull(receiver.active(PLAYER, now + Receiver.MARK_MEMORY + 1000));
    }
}
