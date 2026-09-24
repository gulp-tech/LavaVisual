package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.UiDraw;

/** Bounded local cosmetics. Extraction contains no entity references; rendering never queries the world. */
public final class WorldCosmetics {
    private record Ring(Vec3 origin, int born) { }
    private record Spark(Vec3 origin, Vec3 velocity, int born, int life, float size, boolean ambient) { }
    private record RingFrame(Vec3 origin, float radius, float alpha) { }
    private record SparkFrame(Vec3 origin, float size, float alpha) { }
    private record Frame(List<RingFrame> rings, List<SparkFrame> sparks, int color) { }
    private static final RenderStateDataKey<Frame> DATA = RenderStateDataKey.create(() -> "lavavisual:cosmetics");
    private static final ArrayList<Ring> RINGS = new ArrayList<>();
    private static final ArrayList<Spark> SPARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int tick, lastHitTick = -100;
    private static boolean grounded, ready;
    private static Vec3 groundPosition = Vec3.ZERO;
    private static final RenderType GLOW = RenderType.create("lavavisual_cosmetic_glow",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("lavavisual:pipeline/cosmetic_glow")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false).build())).sortOnUpload().createRenderSetup());
    private WorldCosmetics() { }
    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            var c = LavaVisualClient.config();
            if (c.particlesEnabled && player == mc.player && level == mc.level && !player.isSpectator()
                    && mc.gui.screen() == null && entity instanceof LivingEntity living && living.isAlive() && !living.isInvisible()
                    && mc.hitResult instanceof EntityHitResult aimed && aimed.getEntity() == entity
                    && player.distanceTo(entity) <= 6 && player.hasLineOfSight(entity) && tick - lastHitTick >= 2) {
                lastHitTick = tick;
                Vec3 origin = aimed.getLocation();
                for (int i = 0; i < c.particleCount; i++) {
                    Vec3 velocity = new Vec3((RANDOM.nextDouble() - .5) * .16, .015 + RANDOM.nextDouble() * .08, (RANDOM.nextDouble() - .5) * .16);
                    add(new Spark(origin, velocity, tick, 14 + RANDOM.nextInt(9), (float) c.particleSize, false));
                }
            }
            return InteractionResult.PASS;
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var c = LavaVisualClient.config();
            if (RINGS.isEmpty() && SPARKS.isEmpty()) { context.levelState().setData(DATA, null); return; }
            double now = tick + context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var rings = new ArrayList<RingFrame>(); var sparks = new ArrayList<SparkFrame>();
            if (c.jumpEnabled) for (Ring r : RINGS) {
                double age = Math.clamp((now - r.born) / 24.0, 0, 1);
                rings.add(new RingFrame(r.origin, (float) (c.jumpRadius * (0.25 + 0.75 * (1 - Math.pow(1 - age, 3)))), (float) (Math.sin(Math.min(1, age * 5) * Math.PI / 2) * (1 - age))));
            }
            for (Spark s : SPARKS) {
                if (s.ambient ? !c.ambientEnabled : !c.particlesEnabled) continue;
                double age = Math.clamp(now - s.born, 0, s.life);
                double alpha = Math.sin(Math.PI * age / s.life);
                Vec3 position = s.origin.add(s.velocity.scale(age)).add(0, s.ambient ? 0 : -0.0015 * age * age, 0);
                sparks.add(new SparkFrame(position, s.size, (float) alpha));
            }
            context.levelState().setData(DATA, new Frame(List.copyOf(rings), List.copyOf(sparks), c.accent()));
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(WorldCosmetics::render);
    }
    private static void add(Spark spark) {
        if (SPARKS.size() >= 96) SPARKS.removeFirst();
        SPARKS.add(spark);
    }
    public static void clear() {
        RINGS.clear(); SPARKS.clear(); ready = false; grounded = false; tick = 0; lastHitTick = -100; groundPosition = Vec3.ZERO;
    }
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        tick++;
        var c = LavaVisualClient.config();
        var player = mc.player;
        RINGS.removeIf(r -> !c.jumpEnabled || tick - r.born >= 24);
        SPARKS.removeIf(s -> (s.ambient ? !c.ambientEnabled : !c.particlesEnabled) || tick - s.born >= s.life);
        if (ready && c.jumpEnabled && grounded && !player.onGround() && player.getDeltaMovement().y > 0.08
                && mc.options.keyJump.isDown() && !player.getAbilities().flying && !player.isInWater() && !player.isPassenger()) {
            if (RINGS.size() >= 4) RINGS.removeFirst();
            RINGS.add(new Ring(groundPosition.add(0, 0.035, 0), tick));
        }
        grounded = player.onGround(); ready = true;
        if (grounded) groundPosition = player.position();
        if (c.ambientEnabled && tick % 5 == 0 && !player.isSpectator()) {
            double angle = RANDOM.nextDouble() * Math.PI * 2, radius = 1.2 + RANDOM.nextDouble() * 2.8;
            Vec3 position = player.position().add(Math.cos(angle) * radius, .3 + RANDOM.nextDouble() * 2.5, Math.sin(angle) * radius);
            add(new Spark(position, new Vec3(0, .008, 0), tick, 70, .055f, true));
        }
    }
    private static void render(LevelRenderContext context) {
        Frame frame = context.levelState().getData(DATA);
        if (frame == null || frame.rings.isEmpty() && frame.sparks.isEmpty()) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Quaternionf orientation = new Quaternionf(context.levelState().cameraRenderState.orientation);
        Vector3f right = new Vector3f(1, 0, 0).rotate(orientation), up = new Vector3f(0, 1, 0).rotate(orientation);
        context.poseStack().pushPose();
        try {
            // Camera-relative doubles are converted only after subtraction, avoiding far-coordinate jitter.
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW, (pose, out) -> {
                for (RingFrame ring : frame.rings) {
                    Vec3 p = ring.origin.subtract(camera);
                    band(pose, out, p, ring.radius * .89f, ring.radius, frame.color, 0, ring.alpha * .75f);
                    band(pose, out, p, ring.radius, ring.radius * 1.11f, frame.color, ring.alpha * .75f, 0);
                    band(pose, out, p, ring.radius * .72f, ring.radius * .75f, frame.color, ring.alpha * .42f, ring.alpha * .42f);
                }
                for (SparkFrame spark : frame.sparks) {
                    Vec3 p = spark.origin.subtract(camera);
                    glow(pose, out, p, right, up, spark.size * 2.7f, frame.color, spark.alpha * .35f, 12);
                    glow(pose, out, p, right, up, spark.size, frame.color, spark.alpha, 8);
                    glow(pose, out, p, right, up, spark.size * .38f, 0xFFFFFFFF, spark.alpha, 4);
                }
            });
        } finally { context.poseStack().popPose(); }
    }
    private static void band(PoseStack.Pose pose, VertexConsumer out, Vec3 p, float inner, float outer, int color, float innerAlpha, float outerAlpha) {
        int a = UiDraw.alpha(color, innerAlpha), b = UiDraw.alpha(color, outerAlpha);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI / 32, next = (i + 1) * Math.PI / 32;
            vertex(pose, out, p.x + Math.cos(angle) * inner, p.y, p.z + Math.sin(angle) * inner, a);
            vertex(pose, out, p.x + Math.cos(angle) * outer, p.y, p.z + Math.sin(angle) * outer, b);
            vertex(pose, out, p.x + Math.cos(next) * outer, p.y, p.z + Math.sin(next) * outer, b);
            vertex(pose, out, p.x + Math.cos(next) * inner, p.y, p.z + Math.sin(next) * inner, a);
        }
    }
    private static void glow(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f right, Vector3f up, float size, int color, float alpha, int segments) {
        int center = UiDraw.alpha(color, alpha), edge = color & 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            vertex(pose, out, p.x, p.y, p.z, center);
            billboardVertex(pose, out, p, right, up, Math.cos(a) * size, Math.sin(a) * size, edge);
            billboardVertex(pose, out, p, right, up, Math.cos(b) * size, Math.sin(b) * size, edge);
            vertex(pose, out, p.x, p.y, p.z, center);
        }
    }
    private static void billboardVertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, double x, double y, int color) {
        vertex(pose, out, p.x + r.x * x + u.x * y, p.y + r.y * x + u.y * y, p.z + r.z * x + u.z * y, color);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, double x, double y, double z, int color) {
        out.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }
}
