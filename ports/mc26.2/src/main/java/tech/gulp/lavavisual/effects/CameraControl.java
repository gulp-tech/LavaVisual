package tech.gulp.lavavisual.effects;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.input.Binds;

/**
 * Zoom and FreeLook — camera only, nothing is sent to the server.
 * Zoom: hold the key (C by default); the wheel changes the zoom while held (not the hotbar); eased, slower mouse.
 * FreeLook: hold the key (Left Alt by default) to orbit the third-person camera around you with the mouse; your view
 * direction, aim and movement stay exactly where they were, and the previous perspective returns on release.
 */
public final class CameraControl {
    private static boolean free, forcedFree;
    private static float yaw, pitch;
    private static CameraType previous;
    private static double zoom = 1, target = 1, wheel, forcedZoom;
    private static long last;
    /** CI: the FreeLook camera hook ran while FreeLook was active. */
    public static volatile boolean hooked;
    private CameraControl() { }

    public static boolean freeLook() { return free; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
    public static boolean zooming() { return target > 1.001; }

    public static void tick(Minecraft mc) {
        var c = LavaVisualClient.config();
        boolean world = mc.player != null && mc.level != null, playing = world && mc.gui.screen() == null;
        boolean zoomHeld = forcedZoom > 0 || (c.zoomEnabled && playing && Binds.mapping(Binds.Action.ZOOM).isDown());
        if (zoomHeld) target = Math.clamp((forcedZoom > 0 ? forcedZoom : c.zoomLevel) * Math.pow(1.25, wheel), 1.2, 50);
        else { target = 1; wheel = 0; }
        boolean freeHeld = world && (forcedFree || (c.freeLookEnabled && playing && Binds.mapping(Binds.Action.FREELOOK).isDown()));
        if (freeHeld && !free) {
            free = true;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            previous = mc.options.getCameraType();
            if (previous != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!freeHeld && free) {
            free = false;
            if (previous != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(previous);
            previous = null;
        }
    }

    /** Camera FOV hook (every frame): eased zoom. */
    public static float fov(float fov) {
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        double k = LavaVisualClient.config().zoomSmooth ? 1 - Math.exp(-dt * 12) : 1;
        zoom += (target - zoom) * k;
        if (Math.abs(zoom - target) < 0.002) zoom = target;
        return zoom > 1.001 ? (float) (fov / zoom) : fov;
    }

    /** Mouse hook: true when FreeLook took the movement (the player does not turn). */
    public static boolean turn(double dx, double dy) {
        if (!free) return false;
        yaw += (float) dx * 0.15f;
        pitch = Math.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
        return true;
    }

    /** Slower mouse while zoomed, so aim keeps the same feel on screen. */
    public static double sensitivity() { return zoom > 1.001 && LavaVisualClient.config().zoomSlowMouse ? 1 / zoom : 1; }

    /** Wheel while zooming changes the zoom instead of the hotbar slot. */
    public static boolean scroll(double y) {
        if (!zooming() || y == 0) return false;
        wheel = Math.clamp(wheel + Math.signum(y), -4, 8);
        return true;
    }

    /** CI smoke only: pretend the keys are held. */
    public static void force(double zoomLevel, boolean freeLook) { forcedZoom = zoomLevel; forcedFree = freeLook; }
    /** CI smoke only: orbit the FreeLook camera. */
    public static void orbit(float degrees) { if (free) yaw += degrees; }
}
