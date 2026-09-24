package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.UiDraw;

/**
 * "Air particles": fireflies, snow, stars, embers or hearts floating around you.
 * A fixed pool of primitive arrays (no per-particle objects, no allocation per tick); the world is touched only when
 * a particle respawns. Each particle is a handful of camera-facing quads in the shared glow pipeline, and the count
 * follows FPS Boost, so the effect stays cheap on weak PCs and phones.
 */
public final class AirParticles {
    public static final int FIREFLIES = 0, SNOW = 1, STARS = 2, EMBERS = 3, HEARTS = 4;
    private static final int MAX = 200, STRIDE = 6;
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final double[] OX = new double[MAX], OY = new double[MAX], OZ = new double[MAX];
    private static final float[] VX = new float[MAX], VY = new float[MAX], VZ = new float[MAX], SEED = new float[MAX];
    private static final int[] AGE = new int[MAX], LIFE = new int[MAX];
    private static final Random RANDOM = new Random();
    private static int count, style = -1, tick;
    /** Heart outline (unit size, centre at the origin) used as a triangle-fan of quads. */
    private static final float[] HEART_X = new float[16], HEART_Y = new float[16];
    static {
        for (int i = 0; i < 16; i++) {
            double t = Math.PI * 2 * i / 16, s = Math.sin(t);
            HEART_X[i] = (float) (16 * s * s * s / 17.0);
            HEART_Y[i] = (float) ((13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17.0 + 0.1);
        }
    }
    /** Camera-relative particle data: x, y, z, alpha, size, colour mix (STRIDE floats each). */
    private record Frame(float[] data, int n, int style, int color, int color2, float size, float time) { }
    private static final RenderStateDataKey<Frame> DATA = RenderStateDataKey.create(() -> "lavavisual:air_particles");
    private AirParticles() { }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var c = LavaVisualClient.config();
            var mc = Minecraft.getInstance();
            if (!c.ambientEnabled || count == 0 || mc.player == null) { context.levelState().setData(DATA, null); return; }
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 camera = context.levelState().cameraRenderState.pos;
            float[] data = new float[count * STRIDE];
            int n = 0;
            float time = tick + partial;
            for (int i = 0; i < count; i++) {
                if (LIFE[i] <= 0) continue;
                double age = AGE[i] + partial, life = LIFE[i];
                double fade = Math.min(1, Math.min(age / 20.0, (life - age) / 25.0));
                if (fade <= 0.01) continue;
                double twinkle = switch (style) {
                    case FIREFLIES -> 0.35 + 0.65 * Math.max(0, Math.sin(time * (0.07 + 0.05 * SEED[i]) + SEED[i] * 40));
                    case STARS -> 0.55 + 0.45 * Math.sin(time * (0.11 + 0.06 * SEED[i]) + SEED[i] * 30);
                    default -> 1;
                };
                int o = n * STRIDE;
                data[o] = (float) (OX[i] + (X[i] - OX[i]) * partial - camera.x);
                data[o + 1] = (float) (OY[i] + (Y[i] - OY[i]) * partial - camera.y);
                data[o + 2] = (float) (OZ[i] + (Z[i] - OZ[i]) * partial - camera.z);
                data[o + 3] = (float) (fade * twinkle);
                data[o + 4] = 0.75f + 0.5f * SEED[i];
                data[o + 5] = SEED[i];
                n++;
            }
            context.levelState().setData(DATA, n == 0 ? null : new Frame(data, n, style, c.color("ambient") & 0xFFFFFF,
                    c.color2("ambient") & 0xFFFFFF, (float) c.ambientSize, time));
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(AirParticles::render);
    }

    public static void clear() { count = 0; tick = 0; }

    public static void tick(Minecraft mc) {
        var c = LavaVisualClient.config();
        if (!c.ambientEnabled || mc.player == null || mc.level == null || mc.player.isSpectator()) { count = 0; return; }
        if (mc.isPaused()) return;
        tick++;
        if (style != c.ambientStyle) { style = c.ambientStyle; count = 0; }
        int target = Math.clamp((int) Math.round(c.ambientCount * PerformanceMode.quality()), 0, MAX);
        double range = c.ambientRange, speed = c.ambientSpeed;
        Vec3 center = mc.player.position().add(0, 1.2, 0);
        // The pool fills over a few seconds instead of popping in at once.
        for (int added = 0; count < target && added < 8; added++) spawn(mc, count++, center, range, true);
        if (count > target) count = target;
        double limit = range * 1.35;
        for (int i = 0; i < count; i++) {
            OX[i] = X[i]; OY[i] = Y[i]; OZ[i] = Z[i];
            if (LIFE[i] <= 0) { spawn(mc, i, center, range, false); OX[i] = X[i]; OY[i] = Y[i]; OZ[i] = Z[i]; continue; }
            AGE[i]++;
            move(i, speed);
            double dx = X[i] - center.x, dy = Y[i] - center.y, dz = Z[i] - center.z;
            boolean gone = AGE[i] >= LIFE[i] || dx * dx + dz * dz > limit * limit || dy < -range * 0.7 - 2 || dy > range * 0.8 + 3;
            if (!gone && style == SNOW && (tick + i) % 5 == 0)
                gone = Y[i] < mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(X[i]), (int) Math.floor(Z[i]));
            if (gone) { spawn(mc, i, center, range, false); OX[i] = X[i]; OY[i] = Y[i]; OZ[i] = Z[i]; }
        }
    }

    private static void move(int i, double speed) {
        float s = SEED[i], t = tick;
        switch (style) {
            case FIREFLIES -> {
                VX[i] = (VX[i] + (RANDOM.nextFloat() - 0.5f) * 0.006f) * 0.95f;
                VY[i] = (VY[i] + (RANDOM.nextFloat() - 0.5f) * 0.004f) * 0.95f;
                VZ[i] = (VZ[i] + (RANDOM.nextFloat() - 0.5f) * 0.006f) * 0.95f;
                X[i] += VX[i] * speed; Y[i] += VY[i] * speed; Z[i] += VZ[i] * speed;
            }
            case SNOW -> {
                Y[i] -= (0.03 + 0.025 * s) * speed;
                X[i] += (Math.sin(t * 0.05 + s * 6.3) * 0.009 + 0.004) * speed;
                Z[i] += Math.cos(t * 0.043 + s * 9.1) * 0.009 * speed;
            }
            case STARS -> {
                X[i] += Math.sin(t * 0.021 + s * 7) * 0.0025 * speed;
                Y[i] += 0.0025 * speed;
                Z[i] += Math.cos(t * 0.017 + s * 5) * 0.0025 * speed;
            }
            case EMBERS -> {
                Y[i] += (0.022 + 0.028 * s) * speed;
                X[i] += Math.sin(t * 0.09 + s * 6.3) * 0.011 * speed;
                Z[i] += Math.cos(t * 0.077 + s * 4.1) * 0.011 * speed;
            }
            default -> {
                Y[i] += (0.012 + 0.012 * s) * speed;
                X[i] += Math.sin(t * 0.06 + s * 6.3) * 0.007 * speed;
                Z[i] += Math.cos(t * 0.05 + s * 3.7) * 0.007 * speed;
            }
        }
    }

    /** New particle somewhere in the air around the player; stays dormant (life 0) when no free spot is found. */
    private static void spawn(Minecraft mc, int i, Vec3 center, double range, boolean initial) {
        LIFE[i] = 0; AGE[i] = 0;
        SEED[i] = RANDOM.nextFloat();
        VX[i] = VY[i] = VZ[i] = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2, radius = range * Math.sqrt(0.04 + 0.96 * RANDOM.nextDouble());
            double x = center.x + Math.cos(angle) * radius, z = center.z + Math.sin(angle) * radius;
            double y = switch (style) {
                case SNOW -> center.y + (initial ? -range * 0.4 : range * 0.35) + RANDOM.nextDouble() * range * 0.5;
                case EMBERS, HEARTS -> center.y - 2.5 + RANDOM.nextDouble() * (initial ? range * 0.7 : 3);
                default -> center.y - 1.8 + RANDOM.nextDouble() * Math.min(7, range * 0.6);
            };
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !mc.level.getBlockState(pos).isAir()) continue;
            if (style == SNOW && y < mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) continue;
            X[i] = x; Y[i] = y; Z[i] = z;
            LIFE[i] = switch (style) {
                case FIREFLIES -> 140 + RANDOM.nextInt(160);
                case SNOW -> 420;
                case STARS -> 90 + RANDOM.nextInt(140);
                case EMBERS -> 70 + RANDOM.nextInt(70);
                default -> 90 + RANDOM.nextInt(90);
            };
            if (initial) AGE[i] = RANDOM.nextInt(Math.max(1, LIFE[i] / 2));
            return;
        }
    }

    private static void render(LevelRenderContext context) {
        Frame frame = context.levelState().getData(DATA);
        if (frame == null) return;
        Quaternionf orientation = new Quaternionf(context.levelState().cameraRenderState.orientation);
        Vector3f r = new Vector3f(1, 0, 0).rotate(orientation), u = new Vector3f(0, 1, 0).rotate(orientation);
        context.poseStack().pushPose();
        try {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), WorldCosmetics.GLOW, (pose, out) -> {
                float[] d = frame.data;
                for (int n = 0; n < frame.n; n++) {
                    int o = n * STRIDE;
                    float x = d[o], y = d[o + 1], z = d[o + 2], a = d[o + 3], size = d[o + 4] * frame.size, seed = d[o + 5];
                    int color = UiDraw.mix(frame.color, frame.color2, seed);
                    switch (frame.style) {
                        case FIREFLIES -> {
                            float s = 0.035f * size;
                            disc(pose, out, x, y, z, r, u, s * 4.2f, UiDraw.alpha(color, 0.22 * a), UiDraw.alpha(color, 0), 8);
                            disc(pose, out, x, y, z, r, u, s, UiDraw.alpha(UiDraw.mix(color, 0xFFFFFF, 0.55), a), UiDraw.alpha(color, 0.6 * a), 6);
                        }
                        case SNOW -> {
                            float s = 0.05f * size;
                            int flake = UiDraw.mix(0xFFFFFF, color, 0.18);
                            float spin = frame.time * 0.02f + seed * 6.3f;
                            for (int k = 0; k < 3; k++) {
                                float ang = spin + k * (float) (Math.PI / 3), cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                                float w = s * 0.16f;
                                quad(pose, out, x, y, z, r, u, -cs * s - sn * w, -sn * s + cs * w, cs * s - sn * w, sn * s + cs * w,
                                        cs * s + sn * w, sn * s - cs * w, -cs * s + sn * w, -sn * s - cs * w, UiDraw.alpha(flake, 0.85 * a));
                            }
                            disc(pose, out, x, y, z, r, u, s * 1.9f, UiDraw.alpha(flake, 0.12 * a), UiDraw.alpha(flake, 0), 6);
                        }
                        case STARS -> {
                            float s = 0.07f * size, spin = frame.time * 0.01f + seed * 6.3f;
                            disc(pose, out, x, y, z, r, u, s * 1.6f, UiDraw.alpha(color, 0.18 * a), UiDraw.alpha(color, 0), 8);
                            int core = UiDraw.alpha(UiDraw.mix(color, 0xFFFFFF, 0.5), a);
                            for (int k = 0; k < 2; k++) {
                                float ang = spin + k * (float) (Math.PI / 2), cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                                float lx = cs * s, ly = sn * s, wx = -sn * s * 0.2f, wy = cs * s * 0.2f;
                                quad(pose, out, x, y, z, r, u, -lx, -ly, wx, wy, lx, ly, -wx, -wy, core);
                            }
                        }
                        case EMBERS -> {
                            float s = 0.03f * size;
                            int hot = UiDraw.mix(color, 0xFFF1C0, 0.35);
                            disc(pose, out, x, y, z, r, u, s * 3.4f, UiDraw.alpha(color, 0.2 * a), UiDraw.alpha(color, 0), 8);
                            quad(pose, out, x, y, z, r, u, -s, -s * 2.4f, s, -s * 2.4f, s * 0.8f, s * 1.2f, -s * 0.8f, s * 1.2f, UiDraw.alpha(hot, a));
                        }
                        default -> {
                            float s = 0.06f * size;
                            int pink = UiDraw.mix(color, 0xFF5C9A, 0.35);
                            heart(pose, out, x, y, z, r, u, s * 1.5f, UiDraw.alpha(pink, 0.16 * a), UiDraw.alpha(pink, 0.16 * a));
                            heart(pose, out, x, y, z, r, u, s, UiDraw.alpha(UiDraw.mix(pink, 0xFFFFFF, 0.35), a), UiDraw.alpha(pink, a));
                        }
                    }
                }
            });
        } finally {
            context.poseStack().popPose();
        }
    }

    /** Soft disc as a fan of quads: centre colour fading to the rim colour. */
    private static void disc(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, Vector3f r, Vector3f u, float radius, int inner, int outer, int segments) {
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments;
            float ca = (float) Math.cos(a) * radius, sa = (float) Math.sin(a) * radius, cb = (float) Math.cos(b) * radius, sb = (float) Math.sin(b) * radius;
            vertex(pose, out, x, y, z, r, u, 0, 0, inner);
            vertex(pose, out, x, y, z, r, u, ca, sa, outer);
            vertex(pose, out, x, y, z, r, u, cb, sb, outer);
            vertex(pose, out, x, y, z, r, u, 0, 0, inner);
        }
    }
    private static void heart(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, Vector3f r, Vector3f u, float size, int inner, int outer) {
        for (int i = 0; i < 16; i++) {
            int j = (i + 1) % 16;
            vertex(pose, out, x, y, z, r, u, 0, 0, inner);
            vertex(pose, out, x, y, z, r, u, HEART_X[i] * size, HEART_Y[i] * size, outer);
            vertex(pose, out, x, y, z, r, u, HEART_X[j] * size, HEART_Y[j] * size, outer);
            vertex(pose, out, x, y, z, r, u, 0, 0, inner);
        }
    }
    private static void quad(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, Vector3f r, Vector3f u,
                             float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        vertex(pose, out, x, y, z, r, u, x0, y0, color);
        vertex(pose, out, x, y, z, r, u, x1, y1, color);
        vertex(pose, out, x, y, z, r, u, x2, y2, color);
        vertex(pose, out, x, y, z, r, u, x3, y3, color);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, Vector3f r, Vector3f u, float dx, float dy, int color) {
        out.addVertex(pose, x + r.x * dx + u.x * dy, y + r.y * dx + u.y * dy, z + r.z * dx + u.z * dy).setColor(color);
    }
}
