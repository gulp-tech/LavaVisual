package tech.gulp.lavavisual.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Client-side time of day. The dimension's day clock drives the sun, moon, sky colours and light through the
 * timelines, so reporting another time for that clock changes the whole picture; the server and other players are
 * not affected. The day count is kept, so the moon phase stays real.
 */
public final class TimeChanger {
    private TimeChanger() { }
    public static final String[] PRESETS = {"Рассвет", "Утро", "День", "Закат", "Ночь", "Полночь"};
    public static final int[] PRESET_TICKS = {23200, 1000, 6000, 12400, 14500, 18000};

    public static long apply(Holder<WorldClock> clock, long original) {
        var c = LavaVisualClient.config();
        if (!c.timeEnabled) return original;
        var level = Minecraft.getInstance().level;
        if (level == null) return original;
        var day = level.dimensionType().defaultClock();
        if (day.isEmpty() || !same(day.get(), clock)) return original;
        long target = Math.floorMod(Math.round(c.timeTicks), 24000L);
        return Math.floorDiv(original, 24000L) * 24000L + target;
    }
    private static boolean same(Holder<WorldClock> a, Holder<WorldClock> b) {
        if (a == b) return true;
        var key = a.unwrapKey();
        return key.isPresent() && key.equals(b.unwrapKey());
    }
    /** 0 ticks = 06:00, 6000 = noon, 18000 = midnight. */
    public static String clock(double ticks) {
        int minutes = (int) Math.floorMod(Math.round(ticks * 0.06) + 360, 1440);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
    public static int preset(double ticks) {
        for (int i = 0; i < PRESET_TICKS.length; i++) if (Math.abs(PRESET_TICKS[i] - ticks) < 1) return i;
        return -1;
    }
}
