package tech.gulp.lavavisual.effects;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.UiDraw;

/** Bounded local cosmetics. Extraction contains no entity references; rendering never queries the world. */
public final class WorldCosmetics {
    private record Ring(Vec3 origin, int born) { }
    private record Mark(Vec3 origin, long born, int shape) { }
    private record Spark(Vec3 origin, Vec3 velocity, int born, int life, float size, boolean ambient, int shape, boolean kill) { }
    private record Beam(Vec3 origin, int born) { }
    private record BeamFrame(Vec3 origin, float alpha, float age) { }
    private record RingFrame(Vec3 origin, float radius, float alpha, boolean echo) { }
    private record TrailNode(Vec3 position, int born) { }
    private record TrailPoint(Vec3 position, float alpha) { }
    private record SparkFrame(Vec3 origin, float size, float alpha, int shape, int color) { }
    /** A hat on one player's head: base on top of the head, rotation hat space -> world (head yaw, optional tilt, spin). */
    private record HatFrame(Vec3 base, Matrix3f rotation, float scale, float stretch, int type, int style, int color, int light, float opacity, float time) { }
    private record MarkerFrame(Vec3 origin, int shape, float size, float alpha) { }
    /** colors: jump, esp, kill, trail, marker (theme or per-element). */
    private record Frame(List<RingFrame> rings, List<SparkFrame> sparks, List<MarkerFrame> markers, int[] colors, int[] lights, List<HatFrame> hats, float spin, List<TrailPoint> trail, Vec3 esp, float espHeight, float espWidth, int espStyle, List<BeamFrame> beams, List<tech.gulp.lavavisual.map.WaypointOverlay.Beam> waypoints) { }
    private static final RenderStateDataKey<Frame> DATA = RenderStateDataKey.create(() -> "lavavisual:cosmetics");
    private static final ArrayList<Ring> RINGS = new ArrayList<>();
    private static final ArrayList<Mark> MARKS = new ArrayList<>();
    private static final ArrayList<Spark> SPARKS = new ArrayList<>();
    private static final ArrayDeque<TrailNode> TRAIL = new ArrayDeque<>();
    private static final ArrayList<Beam> BEAMS = new ArrayList<>();
    private static boolean espVisible;
    private static int lastKillId = -1;
    private static final Random RANDOM = new Random();
    private static int tick, lastHitTick = -100, combo, lastComboTick = -100;
    private static Object comboTarget;
    private static final ArrayDeque<Integer> CLICKS = new ArrayDeque<>();
    private static boolean grounded, ready;
    private static Vec3 groundPosition = Vec3.ZERO;
    public static final RenderType GLOW = RenderType.create("lavavisual_cosmetic_glow",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("lavavisual", "pipeline/cosmetic_glow"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false).build())).sortOnUpload().createRenderSetup());
    /** Solid hats: depth-tested and depth-writing; back faces are dropped on the CPU (see Hats). */
    public static final RenderType HAT = RenderType.create("lavavisual_hat",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("lavavisual", "pipeline/hat"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), true))
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
                for (int i = 0, n = (int) Math.max(3, Math.round(c.particleCount * PerformanceMode.quality())); i < n; i++) {
                    double angle = i * Math.PI * 2 / n;
                    Vec3 velocity = switch (c.particlePattern) {
                        case 1 -> new Vec3(Math.cos(angle) * .13, .01 + RANDOM.nextDouble() * .02, Math.sin(angle) * .13);
                        case 2 -> new Vec3((RANDOM.nextDouble() - .5) * .06, .1 + RANDOM.nextDouble() * .08, (RANDOM.nextDouble() - .5) * .06);
                        default -> new Vec3((RANDOM.nextDouble() - .5) * .16, .015 + RANDOM.nextDouble() * .08, (RANDOM.nextDouble() - .5) * .16);
                    };
                    add(new Spark(origin, velocity, tick, 14 + RANDOM.nextInt(9), (float) c.particleSize, false, c.particleShape, false));
                }
            }
            if (c.markerEnabled && player == mc.player && level == mc.level && !player.isSpectator()
                    && mc.gui.screen() == null && entity instanceof LivingEntity marked && marked.isAlive() && !marked.isInvisible()
                    && mc.hitResult instanceof EntityHitResult aimedMark && aimedMark.getEntity() == entity
                    && player.distanceTo(entity) <= 6 && player.hasLineOfSight(entity)) {
                if (MARKS.size() >= 6) MARKS.removeFirst();
                MARKS.add(new Mark(marked.getBoundingBox().getCenter(), tick, c.markerShape));
            }
            if (player == mc.player && level == mc.level) {
                CLICKS.addLast((int) (System.currentTimeMillis() / 50));
                while (CLICKS.size() > 40) CLICKS.removeFirst();
                if (tick - lastComboTick > 40 || comboTarget != entity) combo = 0;
                combo++; lastComboTick = tick; comboTarget = entity;
            }
            return InteractionResult.PASS;
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var c = LavaVisualClient.config();
            var mc = Minecraft.getInstance();
            var self = mc.player;
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            double now0 = tick + partial;
            // Hats sit on the head of the player's own render state (same position, crouch and head angles the model uses
            // this frame), so they never trail the head; no state means first person or the player is not drawn.
            var hats = new ArrayList<HatFrame>();
            if (self != null && mc.level != null && (c.hatEnabled || c.hatOthers && HatSync.any())) {
                float seconds = (float) (now0 / 20.0);
                for (var state : context.levelState().entityRenderStates) {
                    if (!(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar)) continue;
                    if (avatar.id == self.getId()) {
                        if (c.hatEnabled) hat(hats, avatar, c.hatType, c.color("hat"), c.color2("hat"), c.hatStyle, (float) c.hatOpacity, c.hatSize, c.hatLift,
                                c.hatCone, (float) (now0 * 0.06 * c.hatSpin), c.hatTilt, seconds);
                    } else if (c.hatOthers && hats.size() < 17 && avatar.distanceToCameraSq < 48 * 48) {
                        var remote = HatSync.hatOf(mc.level.getEntity(avatar.id));
                        if (remote == null) continue;
                        int color = remote.rgb(), light = tech.gulp.lavavisual.config.ColorMath.companion(color);
                        if (remote.rainbow()) {
                            double hue = System.nanoTime() / 1e9 * 0.12;
                            color = tech.gulp.lavavisual.config.ColorMath.hsv(hue, 0.72, 1);
                            light = tech.gulp.lavavisual.config.ColorMath.hsv(hue + 0.16, 0.72, 1);
                        }
                        hat(hats, avatar, remote.type(), color, light, 0, 0.92f, 1, 0, 1, 0, false, seconds);
                    }
                }
            }
            var waypointBeams = tech.gulp.lavavisual.map.WaypointOverlay.extract(mc, context.levelState().cameraRenderState, partial);
            Vec3 esp = null;
            float espHeight = 0, espWidth = 0;
            var snapshot = tech.gulp.lavavisual.hud.TargetSnapshot.current;
            if (c.espEnabled && espVisible && snapshot != null && snapshot.entity() != null && snapshot.entity().isAlive() && !snapshot.entity().isRemoved()) {
                esp = snapshot.entity().getPosition(partial);
                espHeight = snapshot.entity().getBbHeight();
                espWidth = snapshot.entity().getBbWidth();
            }
            if (RINGS.isEmpty() && SPARKS.isEmpty() && MARKS.isEmpty() && TRAIL.isEmpty() && BEAMS.isEmpty() && hats.isEmpty() && esp == null && waypointBeams.isEmpty()) { context.levelState().setData(DATA, null); return; }
            int particleColor = c.color("particles") & 0xFFFFFF, ambientColor = c.color("ambient") & 0xFFFFFF, killColor = c.color("kill") & 0xFFFFFF;
            double now = tick + context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var rings = new ArrayList<RingFrame>(); var sparks = new ArrayList<SparkFrame>();
            if (c.jumpEnabled) for (Ring r : RINGS) {
                double age = Math.clamp((now - r.born) / 24.0, 0, 1);
                rings.add(new RingFrame(r.origin, (float) (c.jumpRadius * (0.2 + 0.8 * (1 - Math.pow(1 - age, 3)))), (float) (Math.sin(Math.min(1, age * 5) * Math.PI / 2) * (1 - age)), false));
                double echo = Math.clamp((now - r.born - 5) / 19.0, 0, 1);
                if (now - r.born > 5) rings.add(new RingFrame(r.origin, (float) (c.jumpRadius * 0.85 * (0.2 + 0.8 * (1 - Math.pow(1 - echo, 2)))), (float) ((1 - echo) * 0.6), true));
            }
            for (Spark s : SPARKS) {
                if (s.ambient ? !c.ambientEnabled : s.kill ? !c.killEffect : !c.particlesEnabled) continue;
                double age = Math.clamp(now - s.born, 0, s.life);
                double alpha = Math.sin(Math.PI * age / s.life);
                Vec3 position = s.origin.add(s.velocity.scale(age)).add(0, s.ambient ? 0 : -0.0015 * age * age, 0);
                sparks.add(new SparkFrame(position, s.size, (float) alpha, s.shape, s.ambient ? ambientColor : s.kill ? killColor : particleColor));
            }
            var markers = new ArrayList<MarkerFrame>();
            if (c.markerEnabled) for (Mark m : MARKS) {
                double age = Math.clamp((now - m.born) / (c.markerDuration * 20), 0, 1);
                markers.add(new MarkerFrame(m.origin, m.shape, (float) c.markerSize, (float) (1 - age)));
            }
            var trail = new ArrayList<TrailPoint>();
            if (c.trailEnabled && self != null && !TRAIL.isEmpty()) {
                for (TrailNode node : TRAIL) trail.add(new TrailPoint(node.position(), (float) Math.clamp(1 - (now - node.born()) / 22.0, 0, 1)));
                trail.add(new TrailPoint(self.getPosition(partial), 1f));
            }
            var beams = new ArrayList<BeamFrame>();
            if (c.killEffect) for (Beam b : BEAMS) {
                double age = Math.clamp((now - b.born()) / 26.0, 0, 1);
                beams.add(new BeamFrame(b.origin(), (float) (1 - age * age), (float) age));
            }
            int[] colors = {c.color("jump") & 0xFFFFFF, c.color("esp") & 0xFFFFFF, killColor, c.color("trail") & 0xFFFFFF, c.color("marker") & 0xFFFFFF};
            // Second tones: the theme gradient (lava orange to amethyst by default) instead of a lighter shade.
            int[] lights = {c.color2("jump") & 0xFFFFFF, c.color2("esp") & 0xFFFFFF, c.color2("kill") & 0xFFFFFF, c.color2("trail") & 0xFFFFFF, c.color2("marker") & 0xFFFFFF};
            context.levelState().setData(DATA, new Frame(List.copyOf(rings), List.copyOf(sparks), List.copyOf(markers), colors, lights, List.copyOf(hats), (float) (now * 0.06),
                    List.copyOf(trail), esp, espHeight, espWidth, c.espStyle, List.copyOf(beams), waypointBeams));
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(WorldCosmetics::render);
    }
    private static int mix(int from, int to, double t) {
        int r = (int) (((from >> 16 & 255) * (1 - t)) + ((to >> 16 & 255) * t));
        int g = (int) (((from >> 8 & 255) * (1 - t)) + ((to >> 8 & 255) * t));
        int b = (int) (((from & 255) * (1 - t)) + (to & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    /** Called from LevelRendererMixin at the head of LevelRenderer#render, after sky extraction. */
    public static void tintSky(net.minecraft.client.renderer.state.level.LevelRenderState state) {
        var c = LavaVisualClient.config();
        if (state == null || !c.skyEnabled || c.skyStrength <= 0) return;
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimensionType().hasSkyLight()) return;
        state.skyRenderState.skyColor = mix(state.skyRenderState.skyColor, c.skyRgb, c.skyStrength);
    }
    private static void add(Spark spark) {
        if (SPARKS.size() >= (PerformanceMode.active() ? 48 : 96)) SPARKS.removeFirst();
        SPARKS.add(spark);
    }
    public static int combo() { return tick - lastComboTick <= 40 ? combo : 0; }
    public static int clicksPerSecond() {
        int now = (int) (System.currentTimeMillis() / 50); int count = 0;
        for (int t : CLICKS) if (now - t <= 20) count++;
        return count;
    }
    public static void clear() {
        RINGS.clear(); SPARKS.clear(); MARKS.clear(); CLICKS.clear(); TRAIL.clear(); BEAMS.clear(); espVisible = false; lastKillId = -1; ready = false; grounded = false; tick = 0; lastHitTick = -100; groundPosition = Vec3.ZERO; combo = 0; comboTarget = null;
    }
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        tick++;
        var c = LavaVisualClient.config();
        var player = mc.player;
        RINGS.removeIf(r -> !c.jumpEnabled || tick - r.born >= 24);
        MARKS.removeIf(m -> !c.markerEnabled || tick - m.born >= (long) (c.markerDuration * 20));
        SPARKS.removeIf(s -> (s.ambient ? !c.ambientEnabled : s.kill ? !c.killEffect : !c.particlesEnabled) || tick - s.born >= s.life);
        BEAMS.removeIf(b -> !c.killEffect || tick - b.born() >= 26);
        var snapshot = tech.gulp.lavavisual.hud.TargetSnapshot.current;
        espVisible = c.espEnabled && snapshot != null && snapshot.entity() != null && snapshot.entity().isAlive()
                && player.distanceTo(snapshot.entity()) <= 16 && player.hasLineOfSight(snapshot.entity());
        if ((c.killEffect || c.killSoundEnabled) && comboTarget instanceof LivingEntity victim && tick - lastComboTick <= 60 && victim.isDeadOrDying() && victim.getId() != lastKillId) {
            lastKillId = victim.getId();
            CustomAudio.kill();
            if (c.killEffect) {
                Vec3 at = victim.position();
                if (BEAMS.size() >= 3) BEAMS.removeFirst();
                BEAMS.add(new Beam(at, tick));
                Vec3 chest = at.add(0, victim.getBbHeight() * 0.55, 0);
                for (int i = 0; i < 28; i++) {
                    Vec3 velocity = new Vec3((RANDOM.nextDouble() - .5) * .24, .03 + RANDOM.nextDouble() * .12, (RANDOM.nextDouble() - .5) * .24);
                    add(new Spark(chest, velocity, tick, 18 + RANDOM.nextInt(10), (float) c.particleSize * 1.25f, false, c.particleShape, true));
                }
            }
        }
        TRAIL.removeIf(n -> !c.trailEnabled || tick - n.born() >= 22);
        if (c.trailEnabled && !player.isSpectator() && !player.isInvisible()) {
            Vec3 here = player.position();
            if (TRAIL.isEmpty() || TRAIL.peekLast().position().distanceToSqr(here) > 0.04) {
                TRAIL.addLast(new TrailNode(here, tick));
                while (TRAIL.size() > 40) TRAIL.removeFirst();
            }
        }
        if (ready && c.jumpEnabled && grounded && !player.onGround() && player.getDeltaMovement().y > 0.08
                && mc.options.keyJump.isDown() && !player.getAbilities().flying && !player.isInWater() && !player.isPassenger()) {
            if (RINGS.size() >= 4) RINGS.removeFirst();
            RINGS.add(new Ring(groundPosition.add(0, 0.035, 0), tick));
        }
        grounded = player.onGround(); ready = true;
        if (grounded) groundPosition = player.position();
    }
    private static void render(LevelRenderContext context) {
        Frame frame = context.levelState().getData(DATA);
        if (frame == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        Quaternionf orientation = new Quaternionf(context.levelState().cameraRenderState.orientation);
        Vector3f right = new Vector3f(1, 0, 0).rotate(orientation), up = new Vector3f(0, 1, 0).rotate(orientation);
        context.poseStack().pushPose();
        try {
            // Camera-relative doubles are converted only after subtraction, avoiding far-coordinate jitter.
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW, (pose, out) -> {
                int jump = frame.colors[0], jumpLight = frame.lights[0];
                for (RingFrame ring : frame.rings) {
                    Vec3 p = ring.origin.subtract(camera);
                    float r = ring.radius, a = ring.alpha;
                    if (ring.echo) {
                        ripple(pose, out, p, r * .96f, r, jumpLight, jump, a * .7f, a * .7f, frame.spin);
                        continue;
                    }
                    ripple(pose, out, p, r * .45f, r * .95f, jump, jumpLight, 0, a * .38f, frame.spin);
                    ripple(pose, out, p, r * .95f, r, jumpLight, jump, a, a, frame.spin);
                    ripple(pose, out, p, r, r * 1.14f, jump, jumpLight, a * .75f, 0, frame.spin);
                }
                for (HatFrame h : frame.hats) Hats.glow(pose, out, world(h, camera), h.scale(), h.type(), h.color(), h.light(), h.style(), h.opacity(), h.time(), right, up);
                if (frame.esp != null) {
                    Vec3 base = frame.esp.subtract(camera);
                    int esp = frame.colors[1], espLight = frame.lights[1];
                    switch (frame.espStyle) {
                        case 1 -> circle(pose, out, base, frame.espHeight, frame.espWidth, esp, espLight, frame.spin);
                        case 2 -> crystals(pose, out, base, frame.espHeight, frame.espWidth, esp, espLight, frame.spin, right, up);
                        case 3 -> reticle(pose, out, base, frame.espHeight, frame.espWidth, esp, espLight, frame.spin, right, up);
                        case 4 -> orbits(pose, out, base, frame.espHeight, frame.espWidth, esp, espLight, frame.spin, right, up);
                        default -> ghosts(pose, out, base, frame.espHeight, frame.espWidth, esp, espLight, frame.spin, right, up);
                    }
                }
                for (BeamFrame beam : frame.beams) beam(pose, out, beam.origin().subtract(camera), beam.alpha(), beam.age(), frame.colors[2], frame.lights[2], frame.spin);
                trail(pose, out, frame.trail, camera, frame.colors[3], frame.lights[3]);
                for (MarkerFrame mark : frame.markers) marker(pose, out, mark.origin().subtract(camera), mark, frame.colors[4], right, up);
                for (var waypoint : frame.waypoints) waypointBeam(pose, out, waypoint.base().subtract(camera), waypoint.color(), frame.spin);
                for (SparkFrame spark : frame.sparks) {
                    Vec3 p = spark.origin.subtract(camera);
                    glow(pose, out, p, right, up, spark.size * 2.7f, spark.color, spark.alpha * .35f, 12);
                    if (spark.shape == 1) star(pose, out, p, right, up, spark.size * 1.6f, spark.size * .34f, frame.spin * 3, 0xFFFFFFFF, spark.alpha);
                    else if (spark.shape == 2) heart(pose, out, p, right, up, spark.size * 1.15f, spark.color, spark.alpha);
                    else {
                        glow(pose, out, p, right, up, spark.size, spark.color, spark.alpha, 8);
                        glow(pose, out, p, right, up, spark.size * .38f, 0xFFFFFFFF, spark.alpha, 4);
                    }
                }
            });
            if (!frame.hats.isEmpty()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), HAT, (pose, out) -> {
                for (HatFrame h : frame.hats) Hats.draw(pose, out, world(h, camera), h.type(), h.color(), h.light(), h.style(), h.opacity(), h.time());
            });
        } finally { context.poseStack().popPose(); }
    }
    private static Matrix4f world(HatFrame h, Vec3 camera) {
        return new Matrix4f().translation((float) (h.base().x - camera.x), (float) (h.base().y - camera.y), (float) (h.base().z - camera.z))
                .mul(new Matrix4f().set(h.rotation())).scale(h.scale(), h.scale() * h.stretch(), h.scale());
    }
    /**
     * Places a hat on top of the head. The neck pivot and head size follow the vanilla player model (0.9375 scale,
     * pivot 24 px up, crouch lowers it); the top sits on the hat layer or helmet. "Level" keeps the hat upright but
     * moves it with the head: when you look down it rests on the back of the head, like a real hat would.
     */
    private static void hat(List<HatFrame> out, net.minecraft.client.renderer.entity.state.AvatarRenderState s, int type, int color, int light, int style,
                            float opacity, double size, double lift, double stretch, float spin, boolean tilt, float seconds) {
        if (s.isInvisible || s.isSpectator || s.isFallFlying || s.isVisuallySwimming || s.isAutoSpinAttack || s.isUpsideDown
                || s.hasPose(net.minecraft.world.entity.Pose.SLEEPING)) return;
        double scale = Math.max(0.2, s.scale), px = scale * 0.9375 / 16.0;
        double pivot = s.isCrouching ? 19.8 * px - 0.125 * scale : 24 * px;
        double top = s.headEquipment != null && !s.headEquipment.isEmpty() ? 9.0 : s.showHat ? 8.5 : 8.0;
        float yaw = (float) Math.toRadians(s.bodyRot + s.yRot), pitch = (float) Math.toRadians(s.xRot);
        Matrix3f rotation = new Matrix3f().rotationY(-yaw);
        Vector3f offset;
        if (tilt) {
            rotation.rotateX(pitch);
            offset = rotation.transform(new Vector3f(0, (float) (top * px + lift), 0));
        } else {
            double cos = Math.cos(pitch), sin = Math.sin(pitch);
            offset = rotation.transform(new Vector3f(0, (float) ((top * cos + top / 2 * Math.abs(sin)) * px), (float) (top / 2 * sin * px)));
            offset.y += (float) lift;
        }
        rotation.rotateY(spin);
        out.add(new HatFrame(new Vec3(s.x + offset.x, s.y + pivot + offset.y, s.z + offset.z), rotation, (float) (size * scale), (float) stretch,
                type, style, color, light, opacity, seconds));
    }
    private static int brighten(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return (r + (255 - r) / 2) << 16 | (g + (255 - g) / 2) << 8 | (b + (255 - b) / 2);
    }
    private static int lerp(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
    /** Ring band with a colour gradient travelling around the circumference. */
    private static void ripple(PoseStack.Pose pose, VertexConsumer out, Vec3 p, float inner, float outer, int colorA, int colorB, float innerAlpha, float outerAlpha, float phase) {
        int segments = PerformanceMode.quality() < 0.5 ? 40 : 72;
        for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2 / segments, next = (i + 1) * Math.PI * 2 / segments;
            float t0 = 0.5f + 0.5f * (float) Math.sin(angle * 2 + phase), t1 = 0.5f + 0.5f * (float) Math.sin(next * 2 + phase);
            int c0 = lerp(colorA, colorB, t0), c1 = lerp(colorA, colorB, t1);
            vertex(pose, out, p.x + Math.cos(angle) * inner, p.y, p.z + Math.sin(angle) * inner, UiDraw.alpha(c0, innerAlpha));
            vertex(pose, out, p.x + Math.cos(angle) * outer, p.y, p.z + Math.sin(angle) * outer, UiDraw.alpha(c0, outerAlpha));
            vertex(pose, out, p.x + Math.cos(next) * outer, p.y, p.z + Math.sin(next) * outer, UiDraw.alpha(c1, outerAlpha));
            vertex(pose, out, p.x + Math.cos(next) * inner, p.y, p.z + Math.sin(next) * inner, UiDraw.alpha(c1, innerAlpha));
        }
    }
    /** Waypoint: tall camera-facing light column, kept within 240 blocks so it stays visible in the right direction. */
    private static void waypointBeam(PoseStack.Pose pose, VertexConsumer out, Vec3 p, int color, float spin) {
        double horizontal = Math.sqrt(p.x * p.x + p.z * p.z);
        double x = p.x, z = p.z, y = p.y;
        if (horizontal > 240) { x *= 240 / horizontal; z *= 240 / horizontal; horizontal = 240; }
        if (horizontal < 0.001) return;
        double width = Math.clamp(horizontal * 0.004, 0.09, 0.9);
        double rx = -z / horizontal, rz = x / horizontal, top = y + 180, light = 0;
        int bright = brighten(color);
        for (int layer = 0; layer < 2; layer++) {
            double w = layer == 0 ? width * 3.2 : width;
            double bottomAlpha = layer == 0 ? 0.22 : 0.85, pulse = 0.85 + 0.15 * Math.sin(spin * 4);
            int c0 = layer == 0 ? color : bright;
            vertex(pose, out, x - rx * w, y, z - rz * w, UiDraw.alpha(c0, bottomAlpha * pulse));
            vertex(pose, out, x + rx * w, y, z + rz * w, UiDraw.alpha(c0, bottomAlpha * pulse));
            vertex(pose, out, x + rx * w, top, z + rz * w, UiDraw.alpha(c0, light));
            vertex(pose, out, x - rx * w, top, z - rz * w, UiDraw.alpha(c0, light));
        }
        if (horizontal < 64) ripple(pose, out, new Vec3(x, y + 0.05, z), 0.35f, 0.62f, bright, color, 0.7f, 0.05f, spin * 2);
    }
    /** Fading light ribbon along your recent path. */
    private static void trail(PoseStack.Pose pose, VertexConsumer out, List<TrailPoint> points, Vec3 camera, int color, int light) {
        for (int i = 0; i + 1 < points.size(); i++) {
            TrailPoint a = points.get(i), b = points.get(i + 1);
            Vec3 pa = a.position().subtract(camera), pb = b.position().subtract(camera);
            float fa = a.alpha(), fb = b.alpha();
            vertex(pose, out, pa.x, pa.y + 0.08, pa.z, UiDraw.alpha(color, fa * 0.05));
            vertex(pose, out, pb.x, pb.y + 0.08, pb.z, UiDraw.alpha(color, fb * 0.05));
            vertex(pose, out, pb.x, pb.y + 0.62, pb.z, UiDraw.alpha(light, fb * 0.6));
            vertex(pose, out, pa.x, pa.y + 0.62, pa.z, UiDraw.alpha(light, fa * 0.6));
        }
    }
    private static void band(PoseStack.Pose pose, VertexConsumer out, Vec3 p, float inner, float outer, int color, float innerAlpha, float outerAlpha) {
        int a = UiDraw.alpha(color, innerAlpha), b = UiDraw.alpha(color, outerAlpha);
        int segments = PerformanceMode.quality() < 0.5 ? 36 : 64;
        for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2 / segments, next = (i + 1) * Math.PI * 2 / segments;
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
    private static void marker(PoseStack.Pose pose, VertexConsumer out, Vec3 p, MarkerFrame mark, int color, Vector3f right, Vector3f up) {
        float s = mark.size();
        int fill = UiDraw.alpha(color, mark.alpha() * 0.18f);
        int line = UiDraw.alpha(color, mark.alpha() * 0.9f);
        billboardVertex(pose, out, p, right, up, -s, -s, fill);
        billboardVertex(pose, out, p, right, up, s, -s, fill);
        billboardVertex(pose, out, p, right, up, s, s, fill);
        billboardVertex(pose, out, p, right, up, -s, s, fill);
        float t = Math.max(0.02f, s * 0.14f);
        if (mark.shape() == 0) {
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI / 20, b = (i + 1) * Math.PI / 20;
                billboardVertex(pose, out, p, right, up, Math.cos(a) * (s - t), Math.sin(a) * (s - t), line);
                billboardVertex(pose, out, p, right, up, Math.cos(b) * (s - t), Math.sin(b) * (s - t), line);
                billboardVertex(pose, out, p, right, up, Math.cos(b) * s, Math.sin(b) * s, line);
                billboardVertex(pose, out, p, right, up, Math.cos(a) * s, Math.sin(a) * s, line);
            }
        } else {
            bar(pose, out, p, right, up, -s, s - t, s, s, line);
            bar(pose, out, p, right, up, -s, -s, s, -s + t, line);
            bar(pose, out, p, right, up, -s, -s + t, -s + t, s - t, line);
            bar(pose, out, p, right, up, s - t, -s + t, s, s - t, line);
        }
    }
    private static void bar(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f right, Vector3f up, float x0, float y0, float x1, float y1, int color) {
        billboardVertex(pose, out, p, right, up, x0, y0, color);
        billboardVertex(pose, out, p, right, up, x1, y0, color);
        billboardVertex(pose, out, p, right, up, x1, y1, color);
        billboardVertex(pose, out, p, right, up, x0, y1, color);
    }
    /** Target ESP "ghosts": three glowing orbs with tails orbiting the target (visible targets only). */
    private static void ghosts(PoseStack.Pose pose, VertexConsumer out, Vec3 base, float height, float width, int color, int light, float time, Vector3f right, Vector3f up) {
        double radius = width * 0.75 + 0.3;
        int tail = PerformanceMode.quality() < 0.5 ? 8 : 14;
        for (int k = 0; k < 3; k++) {
            for (int j = tail - 1; j >= 0; j--) {
                double t = time * 2.2 - j * 0.09 + k * Math.PI * 2 / 3;
                double y = height * (0.5 + 0.32 * Math.sin(time * 1.6 + k * 2.1 - j * 0.09));
                Vec3 p = base.add(Math.cos(t) * radius, y, Math.sin(t) * radius);
                float fade = 1 - j / (float) tail;
                glow(pose, out, p, right, up, 0.2f * fade + 0.04f, j == 0 ? light : color, 0.55f * fade, 10);
                if (j == 0) glow(pose, out, p, right, up, 0.07f, 0xFFFFFFFF, 0.9f, 8);
            }
        }
    }
    private static double frac(double v) { return v - Math.floor(v); }
    /** Target ESP "crystals": glowing gems drifting around the target at different heights, each turning on its own axis. */
    private static void crystals(PoseStack.Pose pose, VertexConsumer out, Vec3 base, float height, float width, int color, int light, float time, Vector3f right, Vector3f up) {
        boolean low = PerformanceMode.quality() < 0.5;
        int count = low ? 8 : 12;
        int top = UiDraw.mix(color, 0xFFFFFF, 0.5), side = color, bottom = UiDraw.mix(color, light, 0.55);
        for (int i = 0; i < count; i++) {
            double h1 = frac(i * 0.618034 + 0.13), h2 = frac(i * 0.414214 + 0.37), h3 = frac(i * 0.732051 + 0.71);
            double angle = i * 2.399963 + time * (0.3 + 0.22 * h2) * (i % 2 == 0 ? 1 : -1);
            double radius = width * 0.55 + 0.26 + 0.2 * h3;
            double y = height * (0.06 + 0.9 * h1) + Math.sin(time * 1.7 + i * 1.3) * 0.07;
            Vec3 c = base.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            float s = (float) (0.1 + 0.05 * h2);
            glow(pose, out, c, right, up, s * 2.6f, color, 0.34f, low ? 8 : 12);
            crystal(pose, out, c, s * 0.62, s * 1.25, time * 2.2 + i * 0.7, top, side, bottom);
        }
    }
    /** Bipyramid gem: four upper and four lower facets in alternating shades so it reads as 3D without lighting. */
    private static void crystal(PoseStack.Pose pose, VertexConsumer out, Vec3 c, double r, double h, double spin, int top, int side, int bottom) {
        double[] ex = new double[4], ez = new double[4];
        for (int k = 0; k < 4; k++) { double a = spin + k * Math.PI / 2; ex[k] = c.x + Math.cos(a) * r; ez[k] = c.z + Math.sin(a) * r; }
        int upperA = UiDraw.alpha(top, 0.95), upperB = UiDraw.alpha(UiDraw.mix(top, side, 0.5), 0.95);
        int lowerA = UiDraw.alpha(side, 0.92), lowerB = UiDraw.alpha(bottom, 0.92);
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) & 3, upper = (k & 1) == 0 ? upperA : upperB, lower = (k & 1) == 0 ? lowerA : lowerB;
            vertex(pose, out, c.x, c.y + h, c.z, upper);
            vertex(pose, out, ex[k], c.y, ez[k], upper);
            vertex(pose, out, ex[n], c.y, ez[n], upper);
            vertex(pose, out, ex[n], c.y, ez[n], upper);
            vertex(pose, out, c.x, c.y - h, c.z, lower);
            vertex(pose, out, ex[n], c.y, ez[n], lower);
            vertex(pose, out, ex[k], c.y, ez[k], lower);
            vertex(pose, out, ex[k], c.y, ez[k], lower);
        }
    }
    /** Target ESP "marker": turning arcs and a counter-rotating diamond, facing the camera in front of the target. */
    private static void reticle(PoseStack.Pose pose, VertexConsumer out, Vec3 base, float height, float width, int color, int light, float time, Vector3f right, Vector3f up) {
        Vec3 center = base.add(0, height * 0.5, 0);
        double distance = center.length();
        // Pulled towards the camera so the body does not hide the marker (coordinates are camera-relative).
        Vec3 p = distance > 1e-3 ? center.subtract(center.scale(Math.min(distance * 0.5, width * 0.7 + 0.05) / distance)) : center;
        double s = Math.max(height * 0.42, width * 0.8) + 0.12, outer = s * (1 + 0.06 * Math.sin(time * 4)), inner = outer - Math.max(0.035, s * 0.07);
        glow(pose, out, p, right, up, (float) (outer * 1.15), color, 0.14f, 16);
        int segments = PerformanceMode.quality() < 0.5 ? 6 : 10;
        double spin = time * 1.4;
        for (int k = 0; k < 4; k++) {
            int arc = UiDraw.alpha(UiDraw.mix(color, light, k / 3.0), 0.95);
            double start = spin + k * Math.PI / 2 + 0.22, end = spin + (k + 1) * Math.PI / 2 - 0.22;
            for (int j = 0; j < segments; j++) {
                double a = start + (end - start) * j / segments, b = start + (end - start) * (j + 1) / segments;
                billboardVertex(pose, out, p, right, up, Math.cos(a) * inner, Math.sin(a) * inner, arc);
                billboardVertex(pose, out, p, right, up, Math.cos(b) * inner, Math.sin(b) * inner, arc);
                billboardVertex(pose, out, p, right, up, Math.cos(b) * outer, Math.sin(b) * outer, arc);
                billboardVertex(pose, out, p, right, up, Math.cos(a) * outer, Math.sin(a) * outer, arc);
            }
        }
        double d = s * 0.42, t = Math.max(0.03, s * 0.07), turn = -time * 2.1;
        int diamond = UiDraw.alpha(light, 0.9);
        for (int k = 0; k < 4; k++) {
            double a = turn + k * Math.PI / 2, b = turn + (k + 1) * Math.PI / 2;
            billboardVertex(pose, out, p, right, up, Math.cos(a) * (d - t), Math.sin(a) * (d - t), diamond);
            billboardVertex(pose, out, p, right, up, Math.cos(b) * (d - t), Math.sin(b) * (d - t), diamond);
            billboardVertex(pose, out, p, right, up, Math.cos(b) * d, Math.sin(b) * d, diamond);
            billboardVertex(pose, out, p, right, up, Math.cos(a) * d, Math.sin(a) * d, diamond);
        }
        glow(pose, out, p, right, up, (float) (s * 0.12), 0xFFFFFF, 0.8f, 8);
    }
    /** Target ESP "orbits": three tilted rings turning around the target like a gyroscope, each carrying a spark. */
    private static void orbits(PoseStack.Pose pose, VertexConsumer out, Vec3 base, float height, float width, int color, int light, float time, Vector3f right, Vector3f up) {
        Vec3 center = base.add(0, height * 0.52, 0);
        double radius = Math.max(width * 0.85 + 0.3, height * 0.42);
        int segments = PerformanceMode.quality() < 0.5 ? 28 : 48;
        float w = (float) Math.max(0.025, radius * 0.035);
        for (int k = 0; k < 3; k++) {
            double tilt = Math.toRadians(62 + k * 9), yaw = time * (0.6 + 0.25 * k) + k * Math.PI * 2 / 3;
            double ct = Math.cos(tilt), st = Math.sin(tilt), cy = Math.cos(yaw), sy = Math.sin(yaw);
            Vec3 previous = null;
            int previousColor = 0;
            for (int i = 0; i <= segments; i++) {
                double a = i * Math.PI * 2 / segments, x = Math.cos(a) * radius, z = Math.sin(a) * radius, z1 = z * ct;
                Vec3 point = center.add(x * cy - z1 * sy, -z * st, x * sy + z1 * cy);
                int col = UiDraw.alpha(lerp(color, light, (float) (0.5 + 0.5 * Math.sin(a * 2 - time * 3 + k))), 0.85f);
                if (previous != null) ribbon(pose, out, previous, point, w, previousColor, col);
                previous = point; previousColor = col;
            }
            double spark = time * 2.4 + k * 2.1, x = Math.cos(spark) * radius, z = Math.sin(spark) * radius, z1 = z * ct;
            Vec3 s = center.add(x * cy - z1 * sy, -z * st, x * sy + z1 * cy);
            glow(pose, out, s, right, up, 0.16f, light, 0.7f, 10);
            glow(pose, out, s, right, up, 0.05f, 0xFFFFFF, 0.95f, 6);
        }
    }
    /** Camera-facing strip from a to b; the camera sits at the origin of these camera-relative coordinates. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, float width, int ca, int cb) {
        Vec3 side = b.subtract(a).cross(a.add(b));
        double length = side.length();
        if (length < 1e-9) return;
        side = side.scale(width / 2 / length);
        vertex(pose, out, a.x - side.x, a.y - side.y, a.z - side.z, ca);
        vertex(pose, out, a.x + side.x, a.y + side.y, a.z + side.z, ca);
        vertex(pose, out, b.x + side.x, b.y + side.y, b.z + side.z, cb);
        vertex(pose, out, b.x - side.x, b.y - side.y, b.z - side.z, cb);
    }
    /** Target ESP "circle": a ring gliding up and down the body with a fading curtain. */
    private static void circle(PoseStack.Pose pose, VertexConsumer out, Vec3 base, float height, float width, int color, int light, float time) {
        float radius = width * 0.72f + 0.12f;
        double phase = (Math.sin(time * 1.4) + 1) / 2, direction = Math.cos(time * 1.4);
        Vec3 p = base.add(0, height * (0.08 + 0.84 * phase), 0);
        ripple(pose, out, p, radius - 0.02f, radius + 0.02f, light, color, 0.95f, 0.95f, time * 2);
        double drop = -Math.signum(direction) * 0.35 * Math.min(1, Math.abs(direction) * 1.5 + 0.2);
        int segments = PerformanceMode.quality() < 0.5 ? 32 : 56, top = UiDraw.alpha(color, 0.45), fadeOut = color & 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            vertex(pose, out, p.x + Math.cos(a) * radius, p.y, p.z + Math.sin(a) * radius, top);
            vertex(pose, out, p.x + Math.cos(b) * radius, p.y, p.z + Math.sin(b) * radius, top);
            vertex(pose, out, p.x + Math.cos(b) * radius, p.y + drop, p.z + Math.sin(b) * radius, fadeOut);
            vertex(pose, out, p.x + Math.cos(a) * radius, p.y + drop, p.z + Math.sin(a) * radius, fadeOut);
        }
    }
    /** Kill effect: light pillar plus an expanding ground wave. */
    private static void beam(PoseStack.Pose pose, VertexConsumer out, Vec3 p, float alpha, float age, int color, int light, float spin) {
        double height = 5.5;
        for (int k = 0; k < 2; k++) {
            double a = spin + k * Math.PI / 2, dx = Math.cos(a), dz = Math.sin(a);
            for (int layer = 0; layer < 2; layer++) {
                double w = layer == 0 ? 0.45 : 0.12;
                int tint = layer == 0 ? color : 0xFFFFFF;
                int bottom = UiDraw.alpha(tint, alpha * (layer == 0 ? 0.55 : 0.85)), top = tint & 0xFFFFFF;
                vertex(pose, out, p.x - dx * w, p.y, p.z - dz * w, bottom);
                vertex(pose, out, p.x + dx * w, p.y, p.z + dz * w, bottom);
                vertex(pose, out, p.x + dx * w, p.y + height, p.z + dz * w, top);
                vertex(pose, out, p.x - dx * w, p.y + height, p.z - dz * w, top);
            }
        }
        float r = 0.3f + age * 2.4f;
        Vec3 ground = p.add(0, 0.05, 0);
        ripple(pose, out, ground, r * .82f, r, color, light, 0, alpha, spin);
        ripple(pose, out, ground, r, r * 1.08f, light, color, alpha, 0, spin);
    }
    private static void star(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f right, Vector3f up, float length, float width, float spin, int color, float alpha) {
        int c = UiDraw.alpha(color, alpha);
        for (int k = 0; k < 2; k++) {
            double a = spin + k * Math.PI / 2, cs = Math.cos(a), sn = Math.sin(a);
            billboardVertex(pose, out, p, right, up, -cs * length, -sn * length, c);
            billboardVertex(pose, out, p, right, up, -sn * width, cs * width, c);
            billboardVertex(pose, out, p, right, up, cs * length, sn * length, c);
            billboardVertex(pose, out, p, right, up, sn * width, -cs * width, c);
        }
    }
    private static void heart(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f right, Vector3f up, float size, int color, float alpha) {
        int c = UiDraw.alpha(color, alpha), n = 24;
        double s = size / 17.0;
        for (int i = 0; i < n; i++) {
            double t0 = i * Math.PI * 2 / n, t1 = (i + 1) * Math.PI * 2 / n;
            double x0 = 16 * Math.pow(Math.sin(t0), 3) * s, y0 = (13 * Math.cos(t0) - 5 * Math.cos(2 * t0) - 2 * Math.cos(3 * t0) - Math.cos(4 * t0)) * s;
            double x1 = 16 * Math.pow(Math.sin(t1), 3) * s, y1 = (13 * Math.cos(t1) - 5 * Math.cos(2 * t1) - 2 * Math.cos(3 * t1) - Math.cos(4 * t1)) * s;
            billboardVertex(pose, out, p, right, up, 0, 0, c);
            billboardVertex(pose, out, p, right, up, x0, y0, c);
            billboardVertex(pose, out, p, right, up, x1, y1, c);
            billboardVertex(pose, out, p, right, up, 0, 0, c);
        }
    }
    private static void billboardVertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vector3f r, Vector3f u, double x, double y, int color) {
        vertex(pose, out, p.x + r.x * x + u.x * y, p.y + r.y * x + u.y * y, p.z + r.z * x + u.z * y, color);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, double x, double y, double z, int color) {
        out.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }
}
