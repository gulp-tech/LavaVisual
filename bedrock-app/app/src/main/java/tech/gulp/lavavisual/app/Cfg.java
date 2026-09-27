package tech.gulp.lavavisual.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the user can change, kept in one place. */
public final class Cfg {
    private final SharedPreferences prefs;

    public Cfg(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("lavavisual", Context.MODE_PRIVATE);
    }

    public String target() { return prefs.getString("target", ""); }
    public String targetName() { return prefs.getString("targetName", ""); }
    public void target(String packageName, String label) {
        prefs.edit().putString("target", packageName).putString("targetName", label).apply();
    }

    public int badgeX() { return prefs.getInt("badgeX", 12); }
    public int badgeY() { return prefs.getInt("badgeY", 140); }
    public void badgePos(int x, int y) { prefs.edit().putInt("badgeX", x).putInt("badgeY", y).apply(); }

    public int badgeSize() { return prefs.getInt("badgeSize", 42); }
    public int badgeAlpha() { return prefs.getInt("badgeAlpha", 90); }
    public int accent() { return prefs.getInt("accent", 0xFFFF7A2F); }

    public boolean hud() { return prefs.getBoolean("hud", true); }
    public boolean hudClock() { return prefs.getBoolean("hudClock", true); }
    public boolean hudBattery() { return prefs.getBoolean("hudBattery", true); }
    public boolean hudFps() { return prefs.getBoolean("hudFps", true); }
    public boolean hudPing() { return prefs.getBoolean("hudPing", false); }
    public int hudSize() { return prefs.getInt("hudSize", 13); }
    public int hudX() { return prefs.getInt("hudX", 12); }
    public int hudY() { return prefs.getInt("hudY", 12); }
    public void hudPos(int x, int y) { prefs.edit().putInt("hudX", x).putInt("hudY", y).apply(); }
    public String pingHost() { return prefs.getString("pingHost", ""); }

    public boolean glasses() { return prefs.getBoolean("glasses", true); }
    public boolean headphones() { return prefs.getBoolean("headphones", true); }
    public boolean scarf() { return prefs.getBoolean("scarf", true); }
    public boolean rainbow() { return prefs.getBoolean("rainbow", false); }
    public boolean slim() { return prefs.getBoolean("slim", false); }
    public int colorCode() { return prefs.getInt("colorCode", 8); }
    public String skinUri() { return prefs.getString("skinUri", ""); }
    public void skinUri(String uri) { put("skinUri", uri); }

    /** Accessory mask: 1 glasses, 2 headphones, 4 scarf. */
    public int mask() { return (glasses() ? 1 : 0) | (headphones() ? 2 : 0) | (scarf() ? 4 : 0); }

    public boolean flag(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    public int number(String key, int fallback) { return prefs.getInt(key, fallback); }
    public void put(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    public void put(String key, int value) { prefs.edit().putInt(key, value).apply(); }
    public void put(String key, String value) { prefs.edit().putString(key, value).apply(); }
}
