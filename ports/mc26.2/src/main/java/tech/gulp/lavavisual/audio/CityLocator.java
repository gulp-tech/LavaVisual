package tech.gulp.lavavisual.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import tech.gulp.lavavisual.LavaVisual;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * Finds the city of the player by the IP address, through ipwho.is. It asks at most once a week, only while the
 * option "определять город по IP" is on, and remembers the answer in the config. The lookup runs in its own thread,
 * so the game never waits for it; a failed lookup is retried in an hour.
 */
public final class CityLocator {
    private CityLocator() { }

    /** The address of the service: it answers with the city, the region and the coordinates of the caller. */
    public static final String ENDPOINT = "https://ipwho.is/";
    private static final long WEEK = 7L * 24 * 3600_000, RETRY = 3600_000;
    private static volatile boolean busy;
    private static volatile long retryAt;
    private static volatile String error = "";

    /** True when the config holds a real position (the defaults are out of the globe). */
    public static boolean located(HudConfig c) {
        return c.radioLat >= -90 && c.radioLat <= 90 && c.radioLon >= -180 && c.radioLon <= 180;
    }

    /** The reason of the last failed lookup, or an empty text. */
    public static String error() { return error; }

    /** Starts a lookup when the option is on and the saved position is older than a week. Never blocks. */
    public static void ensure() {
        HudConfig c = LavaVisualClient.config();
        if (c == null || !c.radioAutoCity || busy) return;
        long now = System.currentTimeMillis();
        if (now < retryAt) return;
        if (located(c) && now - c.radioLocatedAt < WEEK) return;
        start();
    }

    /** Asks the service again right now (the button of the menu). */
    public static void detectNow() {
        if (!busy) start();
    }

    private static void start() {
        busy = true;
        Thread thread = new Thread(() -> {
            try {
                lookup();
            } finally {
                busy = false;
            }
        }, "LavaVisual city");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void lookup() {
        HudConfig c = LavaVisualClient.config();
        long now = System.currentTimeMillis();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "LavaVisual (Minecraft mod)");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("сервис города ответил " + code);
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject answer = JsonParser.parseString(body).getAsJsonObject();
            if (!answer.has("success") || !answer.get("success").getAsBoolean()) throw new IOException("сервис не определил город");
            String city = text(answer, "city"), region = text(answer, "region");
            if (city.isEmpty() || !answer.has("latitude") || !answer.has("longitude")) throw new IOException("сервис не вернул город");
            c.radioDetected = city;
            c.radioRegion = region;
            c.radioLat = answer.get("latitude").getAsDouble();
            c.radioLon = answer.get("longitude").getAsDouble();
            c.radioLocatedAt = now;
            error = "";
            NetRadio.refresh();
            LavaVisualClient.save();
        } catch (IOException | RuntimeException failure) {
            error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            retryAt = now + RETRY;
            LavaVisual.LOGGER.warn("LavaVisual: the city is not known: {}", error);
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString().trim() : "";
    }
}
