package tech.gulp.lavavisual.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PingerTest {
    @Test public void addressesAreSplit() {
        assertNull(Pinger.parse("  "));
        assertArrayEquals(new int[]{19132}, Pinger.parse("play.example.com:19132"));
        assertArrayEquals(new int[]{25565, 19132}, Pinger.parse("play.example.com"));
        assertEquals("play.example.com", Pinger.host("play.example.com:19132"));
        assertEquals("play.example.com", Pinger.host(" play.example.com "));
    }

    @Test public void anAddressThatDoesNotAnswerGivesMinusOne() {
        assertEquals(-1, Pinger.measure("", 50));
        assertEquals(-1, Pinger.measure("lavavisual.invalid:1", 50));
    }
}
