package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import tech.gulp.lavavisual.compat.Submitter;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import tech.gulp.lavavisual.LavaVisualClient;

/**
 * Item physics for dropped items (client rendering only): no hovering or spinning; in the air items tumble, in water
 * they drift, on the ground they settle - flat items lie down, blocks stand on the ground. Each item eases into its
 * rest pose, so landing never snaps.
 */
public final class ItemPhysics {
    private ItemPhysics() { }
    /** Extra per-item data carried by ItemEntityRenderState (added by a mixin). */
    public interface Extra {
        void lava$physics(int id, boolean ground, boolean water);
        int lava$id();
        boolean lava$ground();
        boolean lava$water();
        void lava$flat(boolean flat);
        boolean lava$flat();
    }
    private static final class State { float pitch, roll, yaw, h = Float.NaN, age = Float.NaN; long seen; }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long frames;
    /** CI: items drawn with physics. */
    public static long rendered;

    public static void capture(ItemEntity entity, ItemEntityRenderState state) {
        ((Extra) (Object) state).lava$physics(entity.getId(), entity.onGround(), entity.isInWater());
        // This version has no model bounding box on the render state: flat sprites are everything that is not a block.
        ((Extra) (Object) state).lava$flat(!(entity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem));
    }
    public static boolean submit(ItemEntityRenderState s, PoseStack pose, Submitter collector, int light) {
        var c = LavaVisualClient.config();
        if (!c.itemPhysics || s.item.isEmpty()) return false;
        Extra extra = (Extra) (Object) s;
        Extra shape = (Extra) (Object) s;
        AABB box = shape.lava$flat() ? new AABB(0, 0, 0.46875, 1, 1, 0.53125) : new AABB(0, 0, 0, 1, 1, 1);
        boolean flat = c.itemPhysicsFlat && box.getZsize() < 0.1;
        long now = System.nanoTime();
        State st = STATES.computeIfAbsent(extra.lava$id(), id -> {
            State fresh = new State();
            fresh.yaw = (float) ((id * 2654435761L >>> 8 & 0xFFFF) / 65535.0 * 360);
            return fresh;
        });
        float dt = Float.isNaN(st.age) ? 0 : Math.clamp(s.ageInTicks - st.age, 0, 3);
        st.age = s.ageInTicks;
        st.seen = now;
        float spin = (float) c.itemPhysicsSpin, ease = Math.min(1, dt * 0.4f);
        float size = (float) c.itemPhysicsSize;
        if (extra.lava$ground()) {
            st.pitch += (nearest(st.pitch, flat ? 90 : 0, flat ? 180 : 360) - st.pitch) * ease;
            if (!flat) st.roll += (nearest(st.roll, 0, 360) - st.roll) * ease;
        } else if (extra.lava$water()) {
            st.pitch += dt * 2.2f * spin;
            st.roll += dt * 1.3f * spin;
        } else {
            st.pitch += dt * 17 * spin;
            st.roll += dt * 9 * spin;
        }
        st.pitch %= 720; st.roll %= 720;
        float rest = (float) ((flat ? box.getZsize() : box.getYsize()) / 2 * size) + 0.012f;
        float target = extra.lava$ground() ? rest : 0.125f * size + 0.02f;
        st.h = Float.isNaN(st.h) ? target : st.h + (target - st.h) * Math.min(1, Math.max(ease, 0.05f));
        pose.pushPose();
        pose.translate(0, st.h, 0);
        pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(st.yaw)).rotateX((float) Math.toRadians(st.pitch)).rotateZ((float) Math.toRadians(st.roll)));
        pose.scale(size, size, size);
        pose.translate(-(box.minX + box.maxX) / 2, -(box.minY + box.maxY) / 2, -(box.minZ + box.maxZ) / 2);
        ItemEntityRenderer.renderMultipleFromCount(pose, collector.buffers(), light, s, RANDOM);
        pose.popPose();
        rendered++;
        if (++frames % 900 == 0) STATES.values().removeIf(v -> now - v.seen > 5_000_000_000L);
        return true;
    }
    /** The angle equivalent to base (mod period) closest to value. */
    private static float nearest(float value, float base, float period) {
        return base + Math.round((value - base) / period) * period;
    }
}
