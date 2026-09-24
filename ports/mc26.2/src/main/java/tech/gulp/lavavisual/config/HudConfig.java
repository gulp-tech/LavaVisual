package tech.gulp.lavavisual.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only local presentation settings. No combat, world scanning or player tracking options. */
public final class HudConfig {
    public static final int[] COLORS = {0xFFFF783E, 0xFF65D6C2, 0xFF82B4FF, 0xFFC5A1FF, 0xFFFFD479, 0xFFF0F0F0};
    public static final List<String> IDS = List.of("coordinates", "performance", "session", "stopwatch", "memory", "island");
    public boolean enabled = true;
    public boolean dark = true;
    public boolean shadows = true;
    public boolean animations = true;
    public int crosshair = 0;
    public int crosshairColor = 0;
    public List<String> friends = new ArrayList<>();
    public Map<String, Widget> widgets = defaults();

    public static final class Widget {
        public boolean visible = true;
        public double x, y;
        public int color;
        public Widget() { }
        public Widget(double x, double y) { this.x = x; this.y = y; }
    }
    public static Map<String, Widget> defaults() {
        Map<String, Widget> result = new LinkedHashMap<>();
        for (int i = 0; i < IDS.size(); i++) result.put(IDS.get(i), new Widget(i == 5 ? 0.5 : 0.02, i == 5 ? 0.02 : 0.08 + i * 0.14));
        return result;
    }
    public void sanitize() {
        if (widgets == null) widgets = defaults();
        Map<String, Widget> clean = defaults();
        for (String key : IDS) {
            Widget widget = widgets.get(key);
            if (widget == null) continue;
            widget.x = clamp(widget.x); widget.y = clamp(widget.y);
            widget.color = Math.floorMod(widget.color, COLORS.length);
            clean.put(key, widget);
        }
        widgets = clean;
        crosshair = Math.floorMod(crosshair, 4);
        crosshairColor = Math.floorMod(crosshairColor, COLORS.length);
        if (friends == null) friends = new ArrayList<>();
        friends = new ArrayList<>(friends.stream().filter(s -> s != null && s.matches("[A-Za-z0-9_]{1,16}"))
                .map(String::trim).distinct().limit(64).toList());
    }
    public static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0.02; }
}
