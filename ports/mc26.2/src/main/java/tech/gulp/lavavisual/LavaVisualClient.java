package tech.gulp.lavavisual;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.config.VisualConfig;
import tech.gulp.lavavisual.module.BoxEsp;
import tech.gulp.lavavisual.module.Tracers;
import tech.gulp.lavavisual.module.Chams;
import tech.gulp.lavavisual.render.LavaRenderLayers;

import java.util.ArrayList;
import java.util.List;

public final class LavaVisualClient implements ClientModInitializer {
    public static final VisualConfig CONFIG = VisualConfig.load();
    public static final RenderStateDataKey<Integer> CHAMS_COLOR = RenderStateDataKey.create(() -> "lavavisual model tint");
    public static final RenderStateDataKey<Integer> CHAMS_LIMIT = RenderStateDataKey.create(() -> "lavavisual model budget");
    private static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create(() -> "lavavisual frame snapshot");
    private static final BoxEsp BOXES = new BoxEsp();
    private static final Tracers TRACERS = new Tracers();
    private record Target(AABB box, float ratio) { }
    private record Frame(List<Target> targets, float yaw, float originX, float originY, float originZ,
                         VisualConfig.BoxMode mode, boolean tracers) { }

    @Override
    public void onInitializeClient() {
        CONFIG.save();
        LavaRenderLayers.initialize();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lavavisual", "visuals"));
        KeyMapping master = key("toggle", GLFW.GLFW_KEY_V, category);
        KeyMapping boxes = key("boxes", GLFW.GLFW_KEY_B, category);
        KeyMapping tracers = key("tracers", GLFW.GLFW_KEY_J, category);
        KeyMapping chams = key("chams", GLFW.GLFW_KEY_K, category);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (master.consumeClick()) {
                CONFIG.enabled = !CONFIG.enabled;
                changed(client, "LavaVisual: " + (CONFIG.enabled ? "ON" : "OFF"));
            }
            while (boxes.consumeClick()) {
                var modes = VisualConfig.BoxMode.values();
                CONFIG.boxMode = modes[(CONFIG.boxMode.ordinal() + 1) % modes.length];
                changed(client, "ESP: " + CONFIG.boxMode);
            }
            while (tracers.consumeClick()) {
                CONFIG.tracers = !CONFIG.tracers;
                changed(client, "Tracers: " + (CONFIG.tracers ? "ON" : "OFF"));
            }
            while (chams.consumeClick()) {
                CONFIG.chams = !CONFIG.chams;
                changed(client, "Chams: " + (CONFIG.chams ? "ON" : "OFF"));
            }
        });
        LevelExtractionEvents.END_EXTRACTION.register(this::extract);
        LevelRenderEvents.START_MAIN.register(context -> Chams.beginFrame());
        LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lavavisual." + name, InputConstants.Type.KEYSYM, code, category));
    }
    private static void changed(Minecraft client, String message) {
        CONFIG.save();
        if (client.player != null) client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
    }
    /** Called only during extraction, never from a deferred drawing callback. */
    public static boolean isTarget(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!CONFIG.enabled || client.player == null || entity == client.player || entity == client.getCameraEntity()
                || !(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) return false;
        if (entity instanceof Player player) {
            if (!CONFIG.players || player.isSpectator()) return false;
        } else if (!(entity instanceof Mob) || !CONFIG.mobs) return false;
        return entity.distanceToSqr(client.getCameraEntity().position()) <= CONFIG.maxDistance * CONFIG.maxDistance;
    }
    private void extract(LevelExtractionContext context) {
        List<Target> targets = new ArrayList<>();
        Vec3 camera = context.camera().position();
        float delta = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        if (CONFIG.enabled && (CONFIG.boxMode != VisualConfig.BoxMode.OFF || CONFIG.tracers)) {
            for (Entity entity : context.level().entitiesForRendering()) {
                if (!isTarget(entity)) continue;
                Vec3 interpolated = entity.getPosition(delta);
                AABB box = entity.getBoundingBox().move(interpolated.subtract(entity.position())).move(camera.scale(-1)).inflate(0.04);
                float ratio = (float) Math.min(1, Math.sqrt(entity.distanceToSqr(camera)) / CONFIG.maxDistance);
                targets.add(new Target(box, ratio));
                if (targets.size() >= CONFIG.maxEntities) break;
            }
        }
        Vector3f origin = new Vector3f(0, 0, -0.25f).rotate(context.camera().rotation());
        context.levelState().setData(FRAME, new Frame(List.copyOf(targets), context.camera().yRot(),
                origin.x, origin.y, origin.z, CONFIG.boxMode, CONFIG.tracers));
    }
    private void submit(LevelRenderContext context) {
        Frame frame = context.levelState().getData(FRAME);
        if (frame == null || frame.targets().isEmpty()) return;
        context.submitNodeCollector().order(1000).submitCustomGeometry(context.poseStack(), LavaRenderLayers.LINES, (pose, out) -> {
            PoseStack matrices = new PoseStack();
            matrices.last().set(pose);
            Vector3f origin = new Vector3f(frame.originX(), frame.originY(), frame.originZ());
            for (Target target : frame.targets()) {
                float alpha = 1 - 0.45f * target.ratio();
                switch (frame.mode()) {
                    case BOX_3D -> BOXES.render3d(matrices, out, target.box(), alpha);
                    case BOX_2D -> BOXES.render2d(matrices, out, target.box(), frame.yaw(), alpha);
                    case OFF -> { }
                }
                if (frame.tracers()) TRACERS.render(matrices, out, target.box(), origin, target.ratio());
            }
        });
    }
}
