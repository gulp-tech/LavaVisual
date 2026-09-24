package tech.gulp.lavavisual;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.config.VisualConfig;
import tech.gulp.lavavisual.module.BoxEsp;
import tech.gulp.lavavisual.module.Chams;
import tech.gulp.lavavisual.module.Tracers;
import tech.gulp.lavavisual.render.LavaRenderLayers;
import tech.gulp.lavavisual.render.RenderUtils;

public final class LavaVisualClient implements ClientModInitializer {
    public static final VisualConfig CONFIG = VisualConfig.load();
    public static final Chams CHAMS = new Chams();
    private final BoxEsp boxes = new BoxEsp();
    private final Tracers tracers = new Tracers();
    private final VertexConsumerProvider.Immediate lines = VertexConsumerProvider.immediate(new net.minecraft.client.util.BufferAllocator(32768));
    private final Vector3f tracerOrigin = new Vector3f();

    @Override
    public void onInitializeClient() {
        CONFIG.save();
        KeyBinding master = key("toggle", GLFW.GLFW_KEY_V);
        KeyBinding box = key("boxes", GLFW.GLFW_KEY_B);
        KeyBinding tracer = key("tracers", GLFW.GLFW_KEY_J);
        KeyBinding chams = key("chams", GLFW.GLFW_KEY_K);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (master.wasPressed()) {
                CONFIG.enabled = !CONFIG.enabled;
                changed(client, "LavaVisual: " + (CONFIG.enabled ? "ON" : "OFF"));
            }
            while (box.wasPressed()) {
                VisualConfig.BoxMode[] modes = VisualConfig.BoxMode.values();
                CONFIG.boxMode = modes[(CONFIG.boxMode.ordinal() + 1) % modes.length];
                changed(client, "ESP: " + CONFIG.boxMode);
            }
            while (tracer.wasPressed()) {
                CONFIG.tracers = !CONFIG.tracers;
                changed(client, "Tracers: " + (CONFIG.tracers ? "ON" : "OFF"));
            }
            while (chams.wasPressed()) {
                CONFIG.chams = !CONFIG.chams;
                changed(client, "Chams: " + (CONFIG.chams ? "ON" : "OFF"));
            }
        });
        WorldRenderEvents.START.register(context -> CHAMS.beginFrame());
        WorldRenderEvents.LAST.register(this::render);
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lavavisual." + name,
                InputUtil.Type.KEYSYM, code, "category.lavavisual"));
    }

    private static void changed(MinecraftClient client, String message) {
        CONFIG.save();
        if (client.player != null) client.player.sendMessage(Text.literal(message), true);
    }

    public static boolean isTarget(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CONFIG.enabled || client.player == null || entity == client.player || entity == client.getCameraEntity()
                || !(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) return false;
        if (entity instanceof PlayerEntity player) {
            if (!CONFIG.players || player.isSpectator()) return false;
        } else if (!(entity instanceof MobEntity) || !CONFIG.mobs) return false;
        return entity.squaredDistanceTo(client.gameRenderer.getCamera().getPos()) <= CONFIG.maxDistance * CONFIG.maxDistance;
    }

    private void render(WorldRenderContext context) {
        RenderSystem.assertOnRenderThread();
        CHAMS.flush();
        if (!CONFIG.enabled || (CONFIG.boxMode == VisualConfig.BoxMode.OFF && !CONFIG.tracers)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        // A point just beyond the near plane avoids an undefined projection at the camera origin.
        tracerOrigin.set(0, 0, -0.25f).rotate(context.camera().getRotation());
        var matrices = context.matrixStack();
        matrices.push();
        try {
            VertexConsumer out = lines.getBuffer(LavaRenderLayers.LINES);
            int rendered = 0;
            for (Entity entity : client.world.getEntities()) {
                if (!isTarget(entity)) continue;
                if (rendered++ >= CONFIG.maxEntities) break;
                Box b = RenderUtils.interpolatedBox(entity, context.tickCounter().getTickDelta(false), context.camera().getPos()).expand(0.04);
                float ratio = (float) Math.min(1, Math.sqrt(entity.squaredDistanceTo(context.camera().getPos())) / CONFIG.maxDistance);
                float alpha = 1.0f - 0.45f * ratio;
                switch (CONFIG.boxMode) {
                    case BOX_3D -> boxes.render3d(matrices, out, b, alpha);
                    case BOX_2D -> boxes.render2d(matrices, out, b, context.camera().getYaw(), alpha);
                    case OFF -> { }
                }
                if (CONFIG.tracers) tracers.render(matrices, out, b, tracerOrigin, ratio);
            }
        } finally {
            lines.draw();
            matrices.pop();
        }
    }
}
