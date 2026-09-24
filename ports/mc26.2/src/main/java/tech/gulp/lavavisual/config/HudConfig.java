package tech.gulp.lavavisual.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudConfig {
    public static final int SCHEMA = 3;
    /** Built-in sounds in CustomAudio.IDS; index SOUND_LIBRARY means the user's own file. */
    public static final int SOUND_LIBRARY = 27;
    public static final List<String> IDS = List.of("coordinates", "performance", "target", "keys", "armor", "totems", "watermark", "minimap");
    /** Every element with its own colour. Missing from {@link #colors} means "follow the theme colour". */
    public static final List<String> COLOR_KEYS = List.of("menu", "menu_bg", "hud_bg", "watermark", "target", "keys", "armor", "coordinates",
            "performance", "totems", "minimap", "badge", "crosshair", "jump", "particles", "ambient", "marker", "esp", "kill", "hat", "trail", "waypoint");
    public int schemaVersion = SCHEMA;
    /** Theme: accent and second gradient colour. Default = the logo's lava orange to amethyst. */
    public int rgb = 0xFF6A2B, rgb2 = 0xA77BFF;
    /** 0 = config from before 2.9 (single-colour themes); see {@link #sanitize()}. */
    public int styleVersion;
    public static final String[] THEME_NAMES = {"LavaVisual", "Лава", "Мята", "Океан", "Неон", "Золото", "Роза", "Закат", "Лёд"};
    public static final int[][] THEMES = {{0xFF6A2B, 0xA77BFF}, {0xFF5A36, 0xFFC233}, {0x85F56A, 0x2CE0C8}, {0x36C8FF, 0x4C6BFF},
            {0xB45CFF, 0xFF5C9A}, {0xFFC233, 0xFF7A3C}, {0xFF5C9A, 0xFFB36B}, {0xFF8A3C, 0xFF3D7F}, {0x9BE7FF, 0xC7B8FF}};
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
    public int hitSound = 3, critSound = 16, totemSound = 19, killSound = 17;
    public boolean killSoundEnabled;
    public double killVolume = 0.65;
    public double menuScale = 0.8, menuOpacity = 0.9, menuDim = 0.12;
    public boolean markerEnabled, skyEnabled, fpsBoost;
    public boolean badgeEnabled = true;
    public boolean hatEnabled, trailEnabled, espEnabled, killEffect;
    public int swingStyle, particleShape, particlePattern, espStyle;
    public double fireHeight = 1;
    public int markerShape, skyRgb = 0x83B9FF;
    public double markerDuration = 2, markerSize = 0.45, skyStrength = 0.65, targetHold = 3;
    public int savedRenderDistance = -1, savedParticles = -1;
    public boolean savedEntityShadows = true, boostApplied;
    public int accent() { return 0xFF000000 | rgb; }
    /** Per-element colours (RGB) and elements that cycle through the rainbow. New fields: older configs simply follow the theme. */
    public Map<String, Integer> colors = new LinkedHashMap<>();
    public List<String> chroma = new ArrayList<>();
    public double chromaSpeed = 1;
    /** China Hat: level by default; tilt follows the head only when enabled. */
    public double hatSize = 1, hatLift = 0, hatCone = 1, hatOpacity = 0.7, hatSpin = 0;
    public boolean hatTilt;
    public int hatStyle;
    /** Minimap: terrain only, north up. Zoom index into blocks-per-view {48, 64, 96}. */
    public int mapZoom = 1;
    public boolean mapCoords = true, mapWaypoints = true;
    public boolean waypointBeams = true, waypointLabels = true;
    public static int defaultColor(String key, int theme) {
        return switch (key) { case "menu_bg" -> 0x12151B; case "hud_bg" -> 0x111216; default -> theme; };
    }
    /** Opaque ARGB colour of an element: rainbow, custom or the theme colour. */
    public int color(String key) {
        if (chroma != null && chroma.contains(key)) {
            double hue = (System.currentTimeMillis() % 1_000_000L) / 1000.0 * 0.12 * chromaSpeed;
            return 0xFF000000 | ColorMath.hsv(hue - Math.floor(hue), 0.72, 1);
        }
        Integer custom = colors == null ? null : colors.get(key);
        return 0xFF000000 | (custom != null ? custom : defaultColor(key, rgb));
    }
    public int accent2() { return 0xFF000000 | rgb2; }
    /** Second gradient colour of an element: the theme's second colour, a companion of a custom colour, or a rainbow offset. */
    public int color2(String key) {
        if (chroma != null && chroma.contains(key)) {
            double hue = (System.currentTimeMillis() % 1_000_000L) / 1000.0 * 0.12 * chromaSpeed + 0.16;
            return 0xFF000000 | ColorMath.hsv(hue - Math.floor(hue), 0.72, 1);
        }
        Integer custom = colors == null ? null : colors.get(key);
        if (custom != null) return 0xFF000000 | ColorMath.companion(custom);
        return 0xFF000000 | (key.equals("menu_bg") || key.equals("hud_bg") ? defaultColor(key, rgb) : rgb2);
    }
    public boolean customColor(String key) { return colors != null && colors.containsKey(key); }
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
        result.put("coordinates", new Widget(0.01, 0.12));
        result.put("performance", new Widget(0.01, 0.22));
        result.put("target", new Widget(0.5, 0.78));
        result.put("keys", new Widget(0.98, 0.5));
        result.put("armor", new Widget(0.5, 0.9));
        result.put("totems", new Widget(0.98, 0.66));
        result.put("watermark", new Widget(0.01, 0.015));
        result.put("minimap", new Widget(0.99, 0.02));
        return result;
    }
    public void disableAll() {
        widgets.values().forEach(w -> w.visible = false);
        waypointBeams = waypointLabels = false;
        crosshairEnabled = jumpEnabled = particlesEnabled = ambientEnabled = viewModelEnabled = false;
        hitSoundEnabled = critSoundEnabled = totemSoundEnabled = killSoundEnabled = false;
        markerEnabled = skyEnabled = fpsBoost = false;
        hatEnabled = trailEnabled = espEnabled = killEffect = false;
        swingStyle = 0;
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
        rgb2 = Math.max(0, Math.min(0xFFFFFF, rgb2));
        if (styleVersion < 1) {
            // 2.9: themes became two-colour gradients. The untouched old default moves to the new logo theme.
            if (rgb == 0x85F56A) { rgb = THEMES[0][0]; rgb2 = THEMES[0][1]; }
            else {
                rgb2 = ColorMath.companion(rgb);
                for (int[] theme : THEMES) if (theme[0] == rgb) rgb2 = theme[1];
            }
            styleVersion = 1;
        }
        if (mainHand == null) mainHand = new Hand();
        if (offHand == null) offHand = new Hand();
        mainHand.sanitize(); offHand.sanitize();
        jumpRadius = bounded(jumpRadius, 0.5, 2, 1.1);
        particleSize = bounded(particleSize, 0.04, 0.25, 0.12);
        particleCount = Math.max(4, Math.min(24, particleCount));
        hitPreset = Math.floorMod(hitPreset, 3); critPreset = Math.floorMod(critPreset, 3); totemPreset = Math.floorMod(totemPreset, 3);
        hitVolume = bounded(hitVolume, 0, 1, 0.65); critVolume = bounded(critVolume, 0, 1, 0.65); totemVolume = bounded(totemVolume, 0, 1, 0.65);
        killVolume = bounded(killVolume, 0, 1, 0.65);
        hitSound = Math.floorMod(hitSound, SOUND_LIBRARY + 1); critSound = Math.floorMod(critSound, SOUND_LIBRARY + 1);
        totemSound = Math.floorMod(totemSound, SOUND_LIBRARY + 1); killSound = Math.floorMod(killSound, SOUND_LIBRARY + 1);
        crosshairScale = bounded(crosshairScale, 0.6, 2, 1);
        crosshairOpacity = bounded(crosshairOpacity, 0.2, 1, 1);
        menuScale = bounded(menuScale, 0.6, 1.2, 0.8);
        menuOpacity = bounded(menuOpacity, 0.25, 1, 0.9); menuDim = bounded(menuDim, 0, 0.65, 0.12);
        swingStyle = Math.floorMod(swingStyle, 7); particleShape = Math.floorMod(particleShape, 3); particlePattern = Math.floorMod(particlePattern, 3);
        espStyle = Math.floorMod(espStyle, 2); fireHeight = bounded(fireHeight, 0, 1, 1);
        markerShape = Math.floorMod(markerShape, 2); markerDuration = bounded(markerDuration, 1, 3, 2);
        markerSize = bounded(markerSize, 0.15, 0.9, 0.45); targetHold = bounded(targetHold, 0.5, 10, 3);
        skyRgb = Math.clamp(skyRgb, 0, 0xFFFFFF); skyStrength = bounded(skyStrength, 0, 1, 0.65);
        savedRenderDistance = Math.clamp(savedRenderDistance, -1, 32); savedParticles = Math.clamp(savedParticles, -1, 2);
        if (colors == null) colors = new LinkedHashMap<>();
        Map<String, Integer> cleanColors = new LinkedHashMap<>();
        for (var entry : colors.entrySet())
            if (entry.getKey() != null && entry.getValue() != null && COLOR_KEYS.contains(entry.getKey())) cleanColors.put(entry.getKey(), Math.clamp(entry.getValue(), 0, 0xFFFFFF));
        colors = cleanColors;
        if (chroma == null) chroma = new ArrayList<>();
        chroma = new ArrayList<>(chroma.stream().filter(k -> k != null && COLOR_KEYS.contains(k)).distinct().toList());
        chromaSpeed = bounded(chromaSpeed, 0.2, 3, 1);
        hatSize = bounded(hatSize, 0.5, 1.8, 1); hatLift = bounded(hatLift, -0.3, 0.6, 0); hatCone = bounded(hatCone, 0.3, 2.5, 1);
        hatOpacity = bounded(hatOpacity, 0.15, 1, 0.7); hatSpin = bounded(hatSpin, 0, 3, 0); hatStyle = Math.floorMod(hatStyle, 3);
        mapZoom = Math.floorMod(mapZoom, 3);
        schemaVersion = SCHEMA;
    }
    public static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static double clamp(double value) { return bounded(value, 0, 1, 0.02); }
}
