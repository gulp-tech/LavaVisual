package tech.gulp.lavavisual.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudConfig {
    public static final int SCHEMA = 2;
    public static final int[] COLORS = {0xFFFF853A, 0xFF65D6C2, 0xFF82B4FF, 0xFFC5A1FF, 0xFFFFD479, 0xFFF0F0F0};
    public static final List<String> IDS = List.of("coordinates", "performance", "stopwatch", "island", "target");
    public int schemaVersion = SCHEMA;
    public boolean shadows = true;
    public boolean animations = true;
    public boolean crosshairEnabled = false;
    public int crosshairShape = 1, crosshairColor = 0;
    public double crosshairScale = 1, crosshairOpacity = 1;
    public Map<String, Widget> widgets = defaults();

    public static final class Widget {
        public boolean visible = false;
        public double x, y;
        public int color;
        public double scale = 1, opacity = 0.9;
        public Widget() { }
        public Widget(double x, double y) { this.x = x; this.y = y; }
    }
    public static Map<String, Widget> defaults() {
        Map<String, Widget> result = new LinkedHashMap<>();
        result.put("coordinates", new Widget(0.02, 0.04));
        result.put("performance", new Widget(0.02, 0.15));
        result.put("stopwatch", new Widget(0.02, 0.26));
        result.put("island", new Widget(0.5, 0.02));
        result.put("target", new Widget(0.5, 0.65));
        return result;
    }
    public void disableAll() { widgets.values().forEach(w -> w.visible = false); crosshairEnabled = false; }
    public void sanitize() {
        if (widgets == null) widgets = defaults();
        Map<String, Widget> clean = defaults();
        for (String key : IDS) {
            Widget widget = widgets.get(key);
            if (widget == null) continue;
            widget.x = clamp(widget.x); widget.y = clamp(widget.y);
            widget.color = Math.floorMod(widget.color, COLORS.length);
            widget.scale = bounded(widget.scale, 0.6, 1.6, 1);
            widget.opacity = bounded(widget.opacity, 0.2, 1, 0.9);
            clean.put(key, widget);
        }
        widgets = clean;
        crosshairShape = Math.max(1, Math.min(3, crosshairShape));
        crosshairColor = Math.floorMod(crosshairColor, COLORS.length);
        crosshairScale = bounded(crosshairScale, 0.6, 2, 1);
        crosshairOpacity = bounded(crosshairOpacity, 0.2, 1, 1);
        schemaVersion = SCHEMA;
    }
    public static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static double clamp(double value) { return bounded(value, 0, 1, 0.02); }
}
