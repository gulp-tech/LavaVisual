package tech.gulp.lavavisual.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudConfig {
    public static final int SCHEMA = 3;
    public static final List<String> IDS = List.of("coordinates", "performance", "target");
    public int schemaVersion = SCHEMA;
    public int rgb = 0x85F56A;
    public boolean shadows = true, animations = true;
    public boolean crosshairEnabled, jumpEnabled, particlesEnabled, ambientEnabled, viewModelEnabled;
    public int crosshairShape = 1;
    public double crosshairScale = 1, crosshairOpacity = 1;
    public double jumpRadius = 1.1, particleSize = 0.12;
    public int particleCount = 12;
    public Hand mainHand = new Hand(), offHand = new Hand();
    public boolean hitSoundEnabled, critSoundEnabled, totemSoundEnabled;
    public int hitPreset, critPreset, totemPreset;
    public double hitVolume = 0.65, critVolume = 0.65, totemVolume = 0.65;
    public int accent() { return 0xFF000000 | rgb; }
    public static final class Hand {
        public double x, y, z, scale = 1;
        public void sanitize() {
            x = bounded(x, -1, 1, 0); y = bounded(y, -1, 1, 0);
            z = bounded(z, -0.5, 1.5, 0); scale = bounded(scale, 0.4, 1.8, 1);
        }
    }
    public Map<String, Widget> widgets = defaults();

    public static final class Widget {
        public boolean visible = false;
        public double x, y;
        public double scale = 1, opacity = 0.9;
        public Widget() { }
        public Widget(double x, double y) { this.x = x; this.y = y; }
    }
    public static Map<String, Widget> defaults() {
        Map<String, Widget> result = new LinkedHashMap<>();
        result.put("coordinates", new Widget(0.02, 0.04));
        result.put("performance", new Widget(0.02, 0.15));
        result.put("target", new Widget(0.5, 0.78));
        return result;
    }
    public void disableAll() {
        widgets.values().forEach(w -> w.visible = false);
        crosshairEnabled = jumpEnabled = particlesEnabled = ambientEnabled = viewModelEnabled = false;
        hitSoundEnabled = critSoundEnabled = totemSoundEnabled = false;
    }
    public void sanitize() {
        if (widgets == null) widgets = defaults();
        Map<String, Widget> clean = defaults();
        for (String key : IDS) {
            Widget widget = widgets.get(key);
            if (widget == null) continue;
            widget.x = clamp(widget.x); widget.y = clamp(widget.y);
            widget.scale = bounded(widget.scale, 0.6, 1.6, 1);
            widget.opacity = bounded(widget.opacity, 0.2, 1, 0.9);
            clean.put(key, widget);
        }
        widgets = clean;
        crosshairShape = Math.max(1, Math.min(3, crosshairShape));
        rgb = Math.max(0, Math.min(0xFFFFFF, rgb));
        if (mainHand == null) mainHand = new Hand();
        if (offHand == null) offHand = new Hand();
        mainHand.sanitize(); offHand.sanitize();
        jumpRadius = bounded(jumpRadius, 0.5, 2, 1.1);
        particleSize = bounded(particleSize, 0.04, 0.25, 0.12);
        particleCount = Math.max(4, Math.min(24, particleCount));
        hitPreset = Math.floorMod(hitPreset, 2); critPreset = Math.floorMod(critPreset, 2); totemPreset = Math.floorMod(totemPreset, 2);
        hitVolume = bounded(hitVolume, 0, 1, 0.65); critVolume = bounded(critVolume, 0, 1, 0.65); totemVolume = bounded(totemVolume, 0, 1, 0.65);
        crosshairScale = bounded(crosshairScale, 0.6, 2, 1);
        crosshairOpacity = bounded(crosshairOpacity, 0.2, 1, 1);
        schemaVersion = SCHEMA;
    }
    public static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static double clamp(double value) { return bounded(value, 0, 1, 0.02); }
}
