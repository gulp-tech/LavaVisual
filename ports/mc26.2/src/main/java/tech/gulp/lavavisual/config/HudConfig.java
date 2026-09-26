package tech.gulp.lavavisual.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudConfig {
    public static final int SCHEMA = 3;
    /** Built-in sounds in CustomAudio.IDS; index SOUND_LIBRARY means the user's own file. */
    public static final int SOUND_LIBRARY = 35;
    /** The first library had 27 sounds and index 27 meant «Свой файл»; ConfigStore migrates such files. */
    public static final int OLD_CUSTOM_SOUND = 27;
    /** Written to every saved file; files without it use the first sound library (see ConfigStore). */
    public int soundVersion = 1;
    /** 1: sharing hats and wings is on by default (see ConfigStore.migrateShare). */
    public int shareVersion = 1;
    public static final List<String> IDS = List.of("coordinates", "performance", "target", "keys", "armor", "totems", "watermark", "minimap", "music");
    /** Every element with its own colour. Missing from {@link #colors} means "follow the theme colour". */
    public static final List<String> COLOR_KEYS = List.of("menu", "menu_bg", "hud_bg", "watermark", "target", "keys", "armor", "coordinates",
            "performance", "totems", "minimap", "badge", "crosshair", "jump", "particles", "ambient", "marker", "esp", "kill", "hat", "trail", "waypoint", "crit", "music", "cape", "outfit", "projectile");
    /** Thrown things that can leave a trail (ProjectileTrails.NAMES in the same order). */
    public static final List<String> PROJECTILE_IDS = List.of("pearl", "arrow", "trident", "snowball", "egg", "potion", "bottle", "firework", "wind", "eye");
    public int schemaVersion = SCHEMA;
    /** Theme: accent and second gradient colour. Default = the logo's lava orange to amethyst. */
    public int rgb = 0xFF6A2B, rgb2 = 0xA77BFF;
    /** 0 = config with single-colour themes; see {@link #sanitize()}. */
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
    public int hitSound = 27, critSound = 31, totemSound = 19, killSound = 17;
    /** The held item does not dip while the attack cooldown recharges; the crosshair indicator stays vanilla. */
    public boolean noCooldownDip = true;
    public boolean killSoundEnabled;
    public double killVolume = 0.65;
    public double menuScale = 0.8, menuOpacity = 0.9, menuDim = 0.12;
    public boolean markerEnabled, skyEnabled, fpsBoost;
    /** Camera: zoom and FreeLook while their keys are held (CameraControl). */
    public boolean zoomEnabled = true, zoomSmooth = true, zoomSlowMouse = true, freeLookEnabled = true;
    public double zoomLevel = 4;
    /** Saturated crits: more vanilla crit particles, a coloured burst, optional magic sparks / on every hit. */
    public boolean critBoost = true, critColored = true, critMagic, critAlways;
    public int critMultiplier = 3;
    /** Trails: 0 ribbon, 1 neon, 2 helix, 3 sparks, 4 comet; length in seconds, width and brightness multipliers. */
    public int trailStyle;
    public double trailLength = 1.1, trailWidth = 1, trailBrightness = 0.9;
    public boolean trailGlow = true;
    /** Minimap window: 0 circle, 1 square. */
    public int mapShape;
    /** With a LavaVisual hit sound on, the target's vanilla hurt / no-damage sounds of your hits are muted. */
    public boolean muteVanillaHits = true;
    /** Chosen user files per event, as "folder/file.ogg" (folder "*" = the shared sounds folder); used when the index is CUSTOM. */
    public String hitCustom = "", critCustom = "", totemCustom = "", killCustom = "";
    /** Music player: volume 0..1, shuffle, repeat 0 off / 1 all / 2 one; the HUD hides while nothing plays. */
    public double musicVolume = 0.7;
    public boolean musicShuffle, musicHudAuto = true;
    public int musicRepeat = 1;
    /** Extra vanilla options stored by FPS Boost (-1 = not stored). */
    public int savedBiomeBlend = -1, savedSimulation = -1, savedInactivity = -1;
    /** badgeEnabled: show other LavaVisual players' marks. badgeShare: mark your own skin and share the hat (opt-in, see HatSync). */
    public boolean badgeEnabled = true, badgeShare = true;
    /** Menu position as a fraction of the free space around the panel (0.5 = centred); set by dragging the header. */
    public double menuX = 0.5, menuY = 0.5;
    /** Air particles: style (fireflies, snow, stars, embers, hearts), count, size, radius around you, speed. */
    public int ambientStyle, ambientCount = 60;
    public double ambientSize = 1, ambientRange = 10, ambientSpeed = 1;
    public boolean hatEnabled, trailEnabled, espEnabled, killEffect;
    /** Local practice dummy slowly turns around (see effects.Dummy). */
    public boolean dummySpin;
    public int swingStyle, particleShape, particlePattern, espStyle;
    public double fireHeight = 1;
    public int markerShape, skyRgb = 0x83B9FF;
    public double markerDuration = 2, markerSize = 0.45, skyStrength = 0.65, targetHold = 3;
    public int savedRenderDistance = -1, savedParticles = -1, savedClouds = -1, savedBlur = -1;
    public double savedEntityDistance = -1;
    /** FPS Boost level 1..4 (Лёгкий, Средний, Сильный, Макс). */
    public int fpsBoostLevel = 2;
    public boolean savedEntityShadows = true, boostApplied;
    public int accent() { return 0xFF000000 | rgb; }
    /** Per-element colours (RGB) and elements that cycle through the rainbow. New fields: older configs simply follow the theme. */
    public Map<String, Integer> colors = new LinkedHashMap<>();
    public List<String> chroma = new ArrayList<>();
    public double chromaSpeed = 1;
    /** Hats: model 1..Hats.COUNT, level by default (tilt follows the head only when enabled); hatCone stretches the height.
        hatOthers shows the hats other LavaVisual players share. */
    public double hatSize = 1, hatLift = 0, hatCone = 1, hatOpacity = 0.9, hatSpin = 0;
    public boolean hatTilt, hatOthers = true;
    public int hatStyle, hatType = 1;
    /** Wings: model 1..Hats.WING_COUNT on the upper back; wingsFlap scales the wing beat (0 = still). */
    public boolean wingsEnabled;
    public int wingsType = 1, wingsStyle;
    public double wingsSize = 1, wingsOpacity = 0.95, wingsFlap = 1;
    /** Wings editor: height and distance from the back (blocks), forward tilt and spread (degrees), beat speed. */
    public double wingsLift, wingsBack, wingsTilt, wingsSpread, wingsSpeed = 1;
    /** Cape 1..Hats.CAPE_COUNT; accessories are numbers of Hats.EXTRA_NAMES (any combination). */
    public boolean capeEnabled;
    public int capeType = 1, capeStyle = 2, outfitStyle = 2;
    public double capeOpacity = 1, capeSway = 1;
    public List<Integer> extras = new ArrayList<>();
    /** Dropped items: tumble in the air, settle on the ground (flat items lie down). */
    public boolean itemPhysics, itemPhysicsFlat = true;
    public double itemPhysicsSpin = 1, itemPhysicsSize = 1;
    /** Trails behind thrown things (PROJECTILE_IDS), your own by default. */
    public boolean projTrails, projOnlyMine = true, projGlow = true, projByItem = true;
    public List<String> projItems = new ArrayList<>(PROJECTILE_IDS);
    public int projStyle;
    public double projLength = 1, projWidth = 1, projBright = 1;
    /** Client-side time of day in ticks (0 = 06:00, 6000 = noon, 18000 = midnight). */
    public boolean timeEnabled;
    public double timeTicks = 18000;
    /** HUD text family (UiFont.FAMILIES): 0 Montserrat (default, like visual clients), 1 Rubik, 2 Inter (menu font). */
    public int hudFont;
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
            return 0xFF000000 | ColorMath.hsv(ChromaClock.phase(chromaSpeed), 0.72, 1);
        }
        Integer custom = colors == null ? null : colors.get(key);
        return 0xFF000000 | (custom != null ? custom : defaultColor(key, rgb));
    }
    public int accent2() { return 0xFF000000 | rgb2; }
    /** Second gradient colour of an element: the theme's second colour, a companion of a custom colour, or a rainbow offset. */
    public int color2(String key) {
        if (chroma != null && chroma.contains(key)) {
            double hue = ChromaClock.phase(chromaSpeed) + 0.16;
            return 0xFF000000 | ColorMath.hsv(hue - Math.floor(hue), 0.72, 1);
        }
        Integer custom = colors == null ? null : colors.get(key);
        if (custom != null) return 0xFF000000 | ColorMath.companion(custom);
        return 0xFF000000 | (key.equals("menu_bg") || key.equals("hud_bg") ? defaultColor(key, rgb) : rgb2);
    }
    public boolean customColor(String key) { return colors != null && colors.containsKey(key); }
    public static final class Hand {
        /** Position (blocks, from the camera), size, and rotation in degrees around the hand's resting point. */
        public double x, y, z, scale = 1, pitch, yaw, roll;
        public void sanitize() {
            x = bounded(x, -1, 1, 0); y = bounded(y, -1, 1, 0);
            z = bounded(z, -0.5, 1.5, 0); scale = bounded(scale, 0.4, 1.8, 1);
            pitch = bounded(pitch, -60, 60, 0); yaw = bounded(yaw, -60, 60, 0); roll = bounded(roll, -60, 60, 0);
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
        Widget music = new Widget(0.01, 0.62);
        music.visible = true; // shows only while a track is loaded (musicHudAuto)
        result.put("music", music);
        return result;
    }
    public void disableAll() {
        widgets.values().forEach(w -> w.visible = false);
        waypointBeams = waypointLabels = false;
        crosshairEnabled = jumpEnabled = particlesEnabled = ambientEnabled = viewModelEnabled = false;
        hitSoundEnabled = critSoundEnabled = totemSoundEnabled = killSoundEnabled = false;
        markerEnabled = skyEnabled = fpsBoost = critBoost = false;
        hatEnabled = wingsEnabled = trailEnabled = espEnabled = killEffect = false;
        capeEnabled = itemPhysics = projTrails = timeEnabled = false;
        extras.clear();
        swingStyle = 0;
        noCooldownDip = false;
    }
    public void sanitize() {
        if (widgets == null) widgets = defaults();
        Map<String, Widget> clean = defaults();
        for (String key : IDS) {
            Widget widget = widgets.get(key);
            if (widget == null) continue;
            widget.x = clamp(widget.x); widget.y = clamp(widget.y);
            widget.scale = bounded(widget.scale, SCALE_MIN, SCALE_MAX, 1);
            widget.opacity = bounded(widget.opacity, 0.2, 1, 0.9);
            clean.put(key, widget);
        }
        widgets = clean;
        crosshairShape = Math.max(1, Math.min(3, crosshairShape));
        rgb = Math.max(0, Math.min(0xFFFFFF, rgb));
        rgb2 = Math.max(0, Math.min(0xFFFFFF, rgb2));
        if (styleVersion < 1) {
            // Themes are two-colour gradients; an untouched single-colour default moves to the logo theme.
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
        espStyle = Math.floorMod(espStyle, 5); fireHeight = bounded(fireHeight, 0, 1, 1);
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
        menuX = bounded(menuX, 0, 1, 0.5); menuY = bounded(menuY, 0, 1, 0.5);
        ambientStyle = Math.floorMod(ambientStyle, 5); ambientCount = Math.max(10, Math.min(200, ambientCount));
        ambientSize = bounded(ambientSize, 0.5, 2, 1); ambientRange = bounded(ambientRange, 4, 24, 10); ambientSpeed = bounded(ambientSpeed, 0.2, 3, 1);
        hatSize = bounded(hatSize, 0.5, 1.8, 1); hatLift = bounded(hatLift, -0.3, 0.6, 0); hatCone = bounded(hatCone, 0.3, 2.5, 1);
        hatOpacity = bounded(hatOpacity, 0.15, 1, 0.9); hatSpin = bounded(hatSpin, 0, 3, 0); hatStyle = Math.floorMod(hatStyle, 3);
        hatType = hatType < 1 || hatType > tech.gulp.lavavisual.effects.Hats.COUNT ? 1 : hatType;
        wingsType = wingsType < 1 || wingsType > tech.gulp.lavavisual.effects.Hats.WING_COUNT ? 1 : wingsType;
        wingsLift = bounded(wingsLift, -0.25, 0.35, 0); wingsBack = bounded(wingsBack, -0.1, 0.25, 0);
        wingsTilt = bounded(wingsTilt, -30, 30, 0); wingsSpread = bounded(wingsSpread, -35, 35, 0); wingsSpeed = bounded(wingsSpeed, 0.3, 2.5, 1);
        capeType = capeType < 1 || capeType > tech.gulp.lavavisual.effects.Hats.CAPE_COUNT ? 1 : capeType;
        capeStyle = Math.floorMod(capeStyle, 3); outfitStyle = Math.floorMod(outfitStyle, 3);
        capeOpacity = bounded(capeOpacity, 0.3, 1, 1); capeSway = bounded(capeSway, 0, 2, 1);
        if (extras == null) extras = new ArrayList<>();
        extras = new ArrayList<>(extras.stream().filter(i -> i != null && i >= 1 && i <= tech.gulp.lavavisual.effects.Hats.EXTRA_COUNT).distinct().toList());
        itemPhysicsSpin = bounded(itemPhysicsSpin, 0, 3, 1); itemPhysicsSize = bounded(itemPhysicsSize, 0.5, 2, 1);
        if (projItems == null) projItems = new ArrayList<>(PROJECTILE_IDS);
        projItems = new ArrayList<>(projItems.stream().filter(k -> k != null && PROJECTILE_IDS.contains(k)).distinct().toList());
        projStyle = Math.clamp(projStyle, 0, 4);
        projLength = bounded(projLength, 0.3, 3, 1); projWidth = bounded(projWidth, 0.3, 2.5, 1); projBright = bounded(projBright, 0.3, 1.6, 1);
        timeTicks = bounded(timeTicks, 0, 23999, 18000);
        fpsBoostLevel = Math.clamp(fpsBoostLevel, 1, 4); savedClouds = Math.clamp(savedClouds, -1, 2); savedBlur = Math.clamp(savedBlur, -1, 10);
        wingsSize = bounded(wingsSize, 0.5, 1.6, 1); wingsOpacity = bounded(wingsOpacity, 0.15, 1, 0.95); wingsFlap = bounded(wingsFlap, 0, 2, 1);
        wingsStyle = Math.floorMod(wingsStyle, 3);
        hudFont = Math.floorMod(hudFont, 3);
        mapZoom = Math.floorMod(mapZoom, 3);
        mapShape = Math.floorMod(mapShape, 2); trailStyle = Math.floorMod(trailStyle, 5);
        musicVolume = bounded(musicVolume, 0, 1, 0.7); musicRepeat = Math.floorMod(musicRepeat, 3);
        zoomLevel = bounded(zoomLevel, 1.5, 15, 4); critMultiplier = Math.max(1, Math.min(6, critMultiplier));
        trailLength = bounded(trailLength, 0.4, 3, 1.1); trailWidth = bounded(trailWidth, 0.4, 2, 1); trailBrightness = bounded(trailBrightness, 0.3, 1, 0.9);
        schemaVersion = SCHEMA;
    }
    public static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    /** HUD element size range; the renderer snaps it to whole or half physical pixels per GUI unit. */
    public static final double SCALE_MIN = 0.5, SCALE_MAX = 2.5;
    public static double clamp(double value) { return bounded(value, 0, 1, 0.02); }
}
