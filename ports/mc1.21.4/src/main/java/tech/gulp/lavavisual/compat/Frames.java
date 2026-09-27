package tech.gulp.lavavisual.compat;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Per-frame camera and data of the world pass, filled once per frame before the cosmetics are built. */
public final class Frames {
    private Frames() { }
    public static final class CameraState {
        public Vec3 pos = Vec3.ZERO;
        public Quaternionf orientation = new Quaternionf();
    }
    private static final CameraState CAMERA = new CameraState();
    private static final Map<Object, Object> DATA = new IdentityHashMap<>();

    public static void begin(WorldRenderContext context) {
        var camera = context.camera();
        CAMERA.pos = camera.getPosition();
        CAMERA.orientation = new Quaternionf(camera.rotation());
    }
    public static CameraState camera() { return CAMERA; }
    public static <T> void set(RenderStateDataKey<T> key, T value) {
        if (value == null) DATA.remove(key); else DATA.put(key, value);
    }
    @SuppressWarnings("unchecked")
    public static <T> T get(RenderStateDataKey<T> key) { return (T) DATA.get(key); }
}
