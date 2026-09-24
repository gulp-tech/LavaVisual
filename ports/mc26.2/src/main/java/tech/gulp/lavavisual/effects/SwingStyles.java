package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.joml.Quaternionf;
import tech.gulp.lavavisual.LavaVisualClient;

/** First-person swing styles. Render-only: never calls swing(), touches cooldown or sends packets.
    Preset table and envelope adapted from PulseVisual (MIT, Copyright (c) 2026 PulseVisual contributors). */
public final class SwingStyles {
    public static final String[] NAMES = {"Ваниль", "Плавный", "Быстрый", "Резкий", "Мягкий", "Размах", "Рубящий"};
    private record Profile(float duration, float strength, float rotation, float x, float y, float z, float smoothness, int easing,
                           float rotationX, float rotationY, float peak) {
        Profile(float d, float s, float r, float x, float y, float z, float smooth, int ease) { this(d, s, r, x, y, z, smooth, ease, -r * .25f, r * .25f, .36f); }
    }
    private static final long[] STARTED = new long[2];
    private static final Profile[] ACTIVE = new Profile[2];
    private static final Quaternionf ROTATION = new Quaternionf();
    private SwingStyles() { }
    private static Profile profile(int style) {
        return switch (style) {
            case 1 -> new Profile(.48f, .8f, 18, -.025f, 0, 0, 1, 1);
            case 2 -> new Profile(.23f, .85f, 16, -.02f, 0, 0, 1, 1);
            case 3 -> new Profile(.30f, 1, 26, -.04f, -.02f, 0, .65f, 0);
            case 4 -> new Profile(.62f, .6f, 14, -.015f, 0, 0, 1, 1);
            case 5 -> new Profile(.44f, 1, 38, -.09f, 0, -.02f, .85f, 3);
            case 6 -> new Profile(.36f, 1, 8, -.02f, -.06f, -.04f, .9f, 1, -55, 6, .4f);
            default -> null;
        };
    }
    public static boolean active() { int s = LavaVisualClient.config().swingStyle; return s > 0 && s < NAMES.length; }
    public static void tick(Minecraft mc) {
        var player = mc.player;
        if (player == null || !active()) { ACTIVE[0] = ACTIVE[1] = null; return; }
        if (player.swinging && player.swingTime == 0) start(player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm);
    }
    private static void start(InteractionHand hand) {
        int i = hand.ordinal();
        long now = System.nanoTime();
        float running = progress(i, now);
        if (running >= 0 && running < 0.6f) return;
        ACTIVE[i] = profile(LavaVisualClient.config().swingStyle);
        STARTED[i] = now;
    }
    private static float progress(int i, long now) {
        Profile p = ACTIVE[i];
        if (p == null) return -1;
        double t = (now - STARTED[i]) / (Math.max(.05, p.duration()) * 1e9);
        return t >= 1 ? -1 : (float) Math.max(0, t);
    }
    static float envelope(float t, float smoothness, int easing, float peak) {
        float u = t < peak ? t / peak : (1 - t) / (1 - peak);
        u = Math.max(0, Math.min(1, u));
        float eased = switch (easing) {
            case 1 -> (float) (.5 - .5 * Math.cos(Math.PI * u));
            case 3 -> u * u * u * (u * (u * 6 - 15) + 10);
            default -> u * u * (3 - 2 * u);
        };
        return u + (eased - u) * smoothness;
    }
    /** Applies the swing around the grip; the caller translates to the grip anchor first. */
    public static void apply(PoseStack pose, InteractionHand hand, boolean rightArm) {
        int i = hand.ordinal();
        Profile p = ACTIVE[i];
        float t = progress(i, System.nanoTime());
        if (p == null || t < 0) return;
        float wave = envelope(t, p.smoothness(), p.easing(), p.peak()) * p.strength(), side = rightArm ? 1 : -1;
        pose.translate(p.x() * wave * side, p.y() * wave, p.z() * wave);
        pose.mulPose(ROTATION.rotationXYZ((float) Math.toRadians(p.rotationX() * wave), (float) Math.toRadians(p.rotationY() * side * wave),
                (float) Math.toRadians(p.rotation() * side * wave)));
    }
}
