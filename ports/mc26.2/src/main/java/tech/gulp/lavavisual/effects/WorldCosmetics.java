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
    /** kind: 0 hit particles, 1 ambient, 2 kill effect, 3 saturated crit, 4 trail sparks. */
    private record Spark(Vec3 origin, Vec3 velocity, int born, int life, float size, int kind, int shape) {
        boolean ambient() { return kind == 1; }
    }
    private record Beam(Vec3 origin, int born) { }
    private record BeamFrame(Vec3 origin, float alpha, float age) { }
    private record RingFrame(Vec3 origin, float radius, float alpha, boolean echo) { }
    private record TrailNode(Vec3 position, int born) { }
    record TrailPoint(Vec3 position, float alpha, float half) { }
    /** A projectile trail snapshot (ProjectileTrails): points tail to head and its two colours. */
    record ShotTrail(List<TrailPoint> points, int color, int light) { }
    /** Trail look: style 0..4, width and brightness multipliers, comet head size (blocks). */
    private record TrailLook(int style, float width, float bright, float head, boolean facing) { }
    /** Bands turn towards the camera (projectile trails, usually seen along their path) instead of standing upright. */
    private static boolean facing;
    private record SparkFrame(Vec3 origin, float size, float alpha, int shape, int color) { }
    /** A hat on one player's head: base on top of the head, rotation hat space -> world (head yaw, optional tilt, spin). */
    private record HatFrame(Vec3 base, Matrix3f rotation, float scale, float stretch, Hats.Model model, Hats.Look look) { }
    /** Wing beat clock per player: advances faster while walking, so the beat never jumps. */
    private static final class WingClock { double phase; long last; WingClock(long now) { last = now; } }
    private static final java.util.Map<Integer, WingClock> WING_CLOCKS = new java.util.HashMap<>();
    private record MarkerFrame(Vec3 origin, int shape, float size, float alpha) { }
    /** colors: jump, esp, kill, trail, marker (theme or per-element). */
    private record Frame(List<RingFrame> rings, List<SparkFrame> sparks, List<MarkerFrame> markers, int[] colors, int[] lights, List<HatFrame> hats, float spin, List<TrailPoint> trail, Vec3 esp, float espHeight, float espWidth, int espStyle, List<BeamFrame> beams, List<tech.gulp.lavavisual.map.WaypointOverlay.Beam> waypoints, List<ShotTrail> shots) { }
    private static final RenderStateDataKey<Frame> DATA = RenderStateDataKey.create(() -> "lavavisual:cosmetics");
    private static final ArrayList<Ring> RINGS = new ArrayList<>();
    private static final ArrayList<Mark> MARKS = new ArrayList<>();
    private static final ArrayList<Spark> SPARKS = new ArrayList<>();
    private static final ArrayDeque<TrailNode> TRAIL = new ArrayDeque<>();
    /** Torso centre as a share of the player's height (legs end at ~0.39, shoulders at ~0.78). */
    private static final double TORSO = 0.58;
    /** CI: body-trail nodes skipped for being ahead of the drawn body, and drawn points still ahead of it (must be 0). */
    public static volatile int trailSkipped, trailAhead;
    /** CI: capes and accessories drawn. */
    public static long capesDrawn, extrasDrawn;
    private static final PoseStack.Pose IDENTITY = new PoseStack().last();
    private static final Vector3f CAMERA_RIGHT = new Vector3f(1, 0, 0), CAMERA_UP = new Vector3f(0, 1, 0);
    private static double frameNow;
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
    /** Additive (alpha-weighted) glow: trails look saturated and luminous instead of a flat strip. */
    public static final RenderType GLOW_ADD = RenderType.create("lavavisual_cosmetic_glow_add",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("lavavisual", "pipeline/cosmetic_glow_add"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
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
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) helper.register(new CosmeticLayer(avatar));
        });
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
                    add(new Spark(origin, velocity, tick, 14 + RANDOM.nextInt(9), (float) c.particleSize, 0, c.particleShape));
                }
            }
            if (c.markerEnabled && player == mc.player && level == mc.level && !player.isSpectator()
                    && mc.gui.screen() == null && entity instanceof LivingEntity marked && marked.isAlive() && !marked.isInvisible()
                    && mc.hitResult instanceof EntityHitResult aimedMark && aimedMark.getEntity() == entity
                    && player.distanceTo(entity) <= 6 && player.hasLineOfSight(entity)) {
                if (MARKS.size() >= 6) MARKS.removeFirst();
                MARKS.add(new Mark(marked.getBoundingBox().getCenter(), tick, c.markerShape));
            }
            if (player == mc.player && level == mc.level && entity instanceof LivingEntity target && target.isAlive()) {
                lastAttacked = target; lastAttackNanos = System.nanoTime();
                float strength = player.getAttackStrengthScale(0.5f);
                boolean crit = strength > 0.9f && player.fallDistance > 0 && !player.onGround() && !player.onClimbable()
                        && !player.isInWater() && !player.isPassenger() && !player.isSprinting();
                if (c.critBoost && (crit || c.critAlways)) critBurst(mc, target, c);
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
            // Hats and wings are drawn by CosmeticLayer as part of the player model (same pass, real head/body
            // transforms). Extraction only hides the cape under wings and stores the clock and camera for that layer.
            frameNow = now0;
            var orientation = context.levelState().cameraRenderState.orientation;
            CAMERA_RIGHT.set(1, 0, 0).rotate(orientation);
            CAMERA_UP.set(0, 1, 0).rotate(orientation);
            boolean remoteAny = c.hatOthers && HatSync.any();
            if (self != null && mc.level != null && (c.wingsEnabled || c.capeEnabled || remoteAny)) {
                for (var state : context.levelState().entityRenderStates) {
                    if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar && avatar.showCape
                            && (wearsWings(avatar, self.getId(), remoteAny) || c.capeEnabled && (avatar.id == self.getId() || Dummy.is(avatar.id))
                                || remoteAny && remoteCape(avatar)))
                        avatar.showCape = false;
                }
                long nanos = System.nanoTime();
                if (WING_CLOCKS.size() > 64) WING_CLOCKS.values().removeIf(clock -> nanos - clock.last > 5_000_000_000L);
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
            if (RINGS.isEmpty() && SPARKS.isEmpty() && MARKS.isEmpty() && TRAIL.isEmpty() && BEAMS.isEmpty() && hats.isEmpty() && esp == null && waypointBeams.isEmpty()
                    && ProjectileTrails.active() == 0) { context.levelState().setData(DATA, null); return; }
            int particleColor = c.color("particles") & 0xFFFFFF, ambientColor = c.color("ambient") & 0xFFFFFF, killColor = c.color("kill") & 0xFFFFFF,
                    critColor = c.color("crit") & 0xFFFFFF, trailColor = c.color2("trail") & 0xFFFFFF;
            double now = tick + context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var rings = new ArrayList<RingFrame>(); var sparks = new ArrayList<SparkFrame>();
            if (c.jumpEnabled) for (Ring r : RINGS) {
                double age = Math.clamp((now - r.born) / 24.0, 0, 1);
                rings.add(new RingFrame(r.origin, (float) (c.jumpRadius * (0.2 + 0.8 * (1 - Math.pow(1 - age, 3)))), (float) (Math.sin(Math.min(1, age * 5) * Math.PI / 2) * (1 - age)), false));
                double echo = Math.clamp((now - r.born - 5) / 19.0, 0, 1);
                if (now - r.born > 5) rings.add(new RingFrame(r.origin, (float) (c.jumpRadius * 0.85 * (0.2 + 0.8 * (1 - Math.pow(1 - echo, 2)))), (float) ((1 - echo) * 0.6), true));
            }
            for (Spark s : SPARKS) {
                if (!sparkOn(s, c)) continue;
                double age = Math.clamp(now - s.born, 0, s.life);
                double alpha = Math.sin(Math.PI * age / s.life);
                Vec3 position = s.origin.add(s.velocity.scale(age)).add(0, s.ambient() || s.kind == 4 ? 0 : -0.0015 * age * age, 0);
                sparks.add(new SparkFrame(position, s.size, (float) alpha, s.shape, switch (s.kind) { case 1 -> ambientColor; case 2 -> killColor; case 3 -> critColor; case 4 -> trailColor; default -> particleColor; }));
            }
            var markers = new ArrayList<MarkerFrame>();
            if (c.markerEnabled) for (Mark m : MARKS) {
                double age = Math.clamp((now - m.born) / (c.markerDuration * 20), 0, 1);
                markers.add(new MarkerFrame(m.origin, m.shape, (float) c.markerSize, (float) (1 - age)));
            }
            var trail = new ArrayList<TrailPoint>();
            if (c.trailEnabled && self != null && !TRAIL.isEmpty()) {
                // Nodes sit at the torso centre, so the ribbon comes out of the body (not from between the feet).
                float half = self.getBbHeight() * 0.2f;
                double life = trailLife(c);
                // Nodes are taken at tick positions while the body is drawn between ticks, so the newest node can be
                // up to a tick ahead of the drawn body (on the chest when running). Only nodes behind the body are
                // kept, and the ribbon starts behind the back.
                Vec3 torso = self.getPosition(partial).add(0, self.getBbHeight() * TORSO, 0);
                double mx = self.getX() - self.xo, mz = self.getZ() - self.zo, speed = Math.hypot(mx, mz);
                Vec3 dir = speed > 0.02 ? new Vec3(mx / speed, 0, mz / speed) : null;
                Vec3 start = dir == null ? torso : torso.subtract(dir.scale(0.24));
                int dropped = 0;
                for (TrailNode node : TRAIL) {
                    if (dir != null && node.position().subtract(torso).dot(dir) > -0.26) { dropped++; continue; }
                    trail.add(new TrailPoint(node.position(), (float) Math.clamp(1 - (now - node.born()) / life, 0, 1), half));
                }
                trail.add(new TrailPoint(start, 1f, half));
                trailSkipped = dropped;
                int ahead = 0;
                if (dir != null) for (int i = 0; i + 1 < trail.size(); i++) if (trail.get(i).position().subtract(torso).dot(dir) > -0.1) ahead++;
                trailAhead = ahead;
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
                    List.copyOf(trail), esp, espHeight, espWidth, c.espStyle, List.copyOf(beams), waypointBeams, ProjectileTrails.frame(partial)));
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
    private static boolean sparkOn(Spark s, tech.gulp.lavavisual.config.HudConfig c) {
        return switch (s.kind) { case 1 -> c.ambientEnabled; case 2 -> c.killEffect; case 3 -> c.critBoost; case 4 -> c.trailEnabled; default -> c.particlesEnabled; };
    }
    private static double trailLife(tech.gulp.lavavisual.config.HudConfig c) { return Math.max(8, c.trailLength * 20); }
    private static LivingEntity lastAttacked;
    private static long lastAttackNanos;
    /** A sound at this spot right after your own hit (0.7 s, 3 blocks from the target): the target's vanilla hurt sound. */
    public static boolean recentHitNear(double x, double y, double z) {
        var target = lastAttacked;
        if (target == null || System.nanoTime() - lastAttackNanos > 700_000_000L) return false;
        return target.position().distanceToSqr(x, y, z) < 9;
    }
    /** Saturated crit: several extra vanilla crit emitters, optional magic sparks and a coloured star burst. Client-side only. */
    private static void critBurst(Minecraft mc, LivingEntity target, tech.gulp.lavavisual.config.HudConfig c) {
        int n = PerformanceMode.active() ? Math.min(2, c.critMultiplier) : c.critMultiplier;
        for (int i = 0; i < n; i++) mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.CRIT);
        if (c.critMagic) for (int i = 0; i < Math.max(1, n / 2); i++) mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT);
        if (!c.critColored) return;
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.62, 0);
        int count = (PerformanceMode.active() ? 6 : 10) + 3 * n;
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, up = (RANDOM.nextDouble() - 0.35) * 0.16, speed = 0.1 + RANDOM.nextDouble() * 0.12;
            Vec3 velocity = new Vec3(Math.cos(a) * speed, up, Math.sin(a) * speed);
            add(new Spark(chest.add(Math.cos(a) * 0.2, 0, Math.sin(a) * 0.2), velocity, tick, 12 + RANDOM.nextInt(8), (float) (0.07 + RANDOM.nextDouble() * 0.05), 3, i % 3 == 0 ? 0 : 1));
        }
    }
    /** CI smoke: the crit burst on an entity. */
    public static void testCrit(Minecraft mc, LivingEntity target) { critBurst(mc, target, LavaVisualClient.config()); }
    public static int combo() { return tick - lastComboTick <= 40 ? combo : 0; }
    public static int clicksPerSecond() {
        int now = (int) (System.currentTimeMillis() / 50); int count = 0;
        for (int t : CLICKS) if (now - t <= 20) count++;
        return count;
    }
    public static void clear() {
        RINGS.clear(); SPARKS.clear(); MARKS.clear(); CLICKS.clear(); TRAIL.clear(); BEAMS.clear(); CapeCloth.clear(); AccessoryPhysics.clear(); espVisible = false; lastKillId = -1; ready = false; grounded = false; tick = 0; lastHitTick = -100; groundPosition = Vec3.ZERO; combo = 0; comboTarget = null;
    }
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        tick++;
        var c = LavaVisualClient.config();
        var player = mc.player;
        RINGS.removeIf(r -> !c.jumpEnabled || tick - r.born >= 24);
        MARKS.removeIf(m -> !c.markerEnabled || tick - m.born >= (long) (c.markerDuration * 20));
        SPARKS.removeIf(s -> !sparkOn(s, c) || tick - s.born >= s.life);
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
                    add(new Spark(chest, velocity, tick, 18 + RANDOM.nextInt(10), (float) c.particleSize * 1.25f, 2, c.particleShape));
                }
            }
        }
        double trailLife = trailLife(c);
        TRAIL.removeIf(n -> !c.trailEnabled || tick - n.born() >= trailLife);
        if (c.trailEnabled && !player.isSpectator() && !player.isInvisible()) {
            Vec3 here = player.position().add(0, player.getBbHeight() * TORSO, 0);
            double mx = player.getX() - player.xo, mz = player.getZ() - player.zo, speed = Math.hypot(mx, mz);
            Vec3 behind = speed > 0.02 ? here.subtract(mx / speed * 0.3, 0, mz / speed * 0.3) : here;
            if (TRAIL.isEmpty() || TRAIL.peekLast().position().distanceToSqr(here) > 0.04) {
                TRAIL.addLast(new TrailNode(here, tick));
                while (TRAIL.size() > 90) TRAIL.removeFirst();
                // "Sparks" style: glittering stars shed from the body while you move.
                if (c.trailStyle == 3 && tick % (PerformanceMode.active() ? 2 : 1) == 0)
                    for (int i = 0; i < 2; i++) add(new Spark(behind.add((RANDOM.nextDouble() - .5) * .3, (RANDOM.nextDouble() - .5) * player.getBbHeight() * .35, (RANDOM.nextDouble() - .5) * .3),
                            new Vec3((RANDOM.nextDouble() - .5) * .025, (RANDOM.nextDouble() - .3) * .02, (RANDOM.nextDouble() - .5) * .025),
                            tick, 12 + RANDOM.nextInt(10), (float) (0.045 * c.trailWidth), 4, i == 0 ? 1 : 0));
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
                for (HatFrame h : frame.hats) Hats.glow(pose, out, world(h, camera), h.scale(), h.model(), h.look(), right, up);
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
            // Trail: the saturated body with normal blending (true colours), the halo additively on top.
            if (frame.trail.size() > 1) {
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW,
                        (pose, out) -> trail(pose, out, frame.trail, camera, frame.colors[3], frame.lights[3], right, up, frame.spin, false, bodyLook()));
                if (LavaVisualClient.config().trailGlow) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW_ADD,
                        (pose, out) -> trail(pose, out, frame.trail, camera, frame.colors[3], frame.lights[3], right, up, frame.spin, true, bodyLook()));
            }
            if (!frame.shots.isEmpty()) {
                var cfg = LavaVisualClient.config();
                TrailLook look = new TrailLook(cfg.projStyle, (float) cfg.projWidth, (float) cfg.projBright, (float) (0.26 * cfg.projWidth), true);
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW, (pose, out) -> {
                    for (ShotTrail t : frame.shots) trail(pose, out, t.points(), camera, t.color(), t.light(), right, up, frame.spin, false, look);
                });
                if (cfg.projGlow) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW_ADD, (pose, out) -> {
                    for (ShotTrail t : frame.shots) trail(pose, out, t.points(), camera, t.color(), t.light(), right, up, frame.spin, true, look);
                });
            }
            if (!frame.hats.isEmpty()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), HAT, (pose, out) -> {
                for (HatFrame h : frame.hats) Hats.draw(pose, out, world(h, camera), h.model(), h.look());
            });
        } finally { context.poseStack().popPose(); }
    }
    private static Matrix4f world(HatFrame h, Vec3 camera) {
        // Per-frame matrix: camera-relative translation, head/body rotation, uniform size, hat height stretch.
        return new Matrix4f().translation((float) (h.base().x - camera.x), (float) (h.base().y - camera.y), (float) (h.base().z - camera.z))
                .mul(new Matrix4f().set(h.rotation())).scale(h.scale(), h.scale() * h.stretch(), h.scale());
    }
    private static boolean hidden(net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
        return s.isInvisible || s.isSpectator || s.isFallFlying || s.isVisuallySwimming || s.isAutoSpinAttack || s.isUpsideDown
                || s.hasPose(net.minecraft.world.entity.Pose.SLEEPING);
    }
    /** World light at the player (block or sky light), so cosmetics darken in caves like the skin does. */
    private static float env(net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
        int block = s.lightCoords >> 4 & 15, sky = s.lightCoords >> 20 & 15;
        return 0.4f + 0.6f * Math.max(block, sky) / 15f;
    }
    /** Another LavaVisual player shares a cape: the vanilla one is hidden under it. */
    private static boolean remoteCape(net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
        if (s.distanceToCameraSq >= 48 * 48) return false;
        var level = Minecraft.getInstance().level;
        var dress = level == null ? null : HatSync.outfit(level.getEntity(s.id));
        return dress != null && dress.cape() > 0;
    }
    private static boolean wearsWings(net.minecraft.client.renderer.entity.state.AvatarRenderState s, int selfId, boolean remoteAny) {
        var c = LavaVisualClient.config();
        if (s.id == selfId || Dummy.is(s.id)) return c.wingsEnabled;
        if (!remoteAny || s.distanceToCameraSq >= 48 * 48) return false;
        var level = Minecraft.getInstance().level;
        var remote = level == null ? null : HatSync.of(level.getEntity(s.id));
        return remote != null && remote.wings() > 0;
    }
    /** Called by CosmeticLayer for every drawn player model (you, the local dummy, and players who share cosmetics). */
    public static void submitLayer(net.minecraft.client.model.player.PlayerModel model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                   net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || hidden(s)) return;
        var c = LavaVisualClient.config();
        float seconds = (float) (frameNow / 20.0);
        long nanos = System.nanoTime();
        if (s.id == mc.player.getId() || Dummy.is(s.id)) {
            if (c.hatEnabled) hat(model, pose, collector, s, c.hatType, Hats.hat(c.hatType), c.color("hat"), c.color2("hat"), c.hatStyle, (float) c.hatOpacity,
                    c.hatSize, c.hatLift, c.hatCone, (float) (Hats.visor(c.hatType) ? 0 : frameNow * 0.06 * c.hatSpin), seconds);
            if (c.wingsEnabled) wings(model, pose, collector, s, Hats.wing(c.wingsType), c.color("wings"), c.color2("wings"), c.wingsStyle,
                    (float) c.wingsOpacity, c.wingsSize, (float) c.wingsFlap, WingFit.of(c), seconds, nanos);
            if (c.capeEnabled) cape(model, pose, collector, s, Hats.cape(c.capeType), c.color("cape"), c.color2("cape"), c.capeStyle, (float) c.capeOpacity, (float) c.capeSway, seconds);
            for (int i = 1; i <= Hats.EXTRA_COUNT; i++)
                if (c.extras.contains(i)) extra(model, pose, collector, s, i, c.color("outfit"), c.color2("outfit"), c.outfitStyle, seconds);
            return;
        }
        if (!c.hatOthers || !HatSync.any() || s.distanceToCameraSq >= 48 * 48) return;
        var entity = mc.level.getEntity(s.id);
        var remote = HatSync.of(entity);
        var dress = HatSync.outfit(entity);
        if (remote == null && dress == null) return;
        double hue = nanos / 1e9 * 0.12;
        if (dress != null) {
            if (dress.cape() > 0) {
                int color = dress.capeRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue, 0.72, 1) : dress.capeRgb();
                int light = dress.capeRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue + 0.16, 0.72, 1) : tech.gulp.lavavisual.config.ColorMath.companion(color);
                cape(model, pose, collector, s, Hats.cape(dress.cape()), color, light, dress.capeStyle(), 1f, 1f, seconds);
            }
            int color = dress.extrasRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue, 0.72, 1) : dress.extrasRgb();
            int light = dress.extrasRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue + 0.16, 0.72, 1) : tech.gulp.lavavisual.config.ColorMath.companion(color);
            for (int i = 1; i <= Hats.EXTRA_COUNT; i++)
                if ((dress.extras() & 1 << (i - 1)) != 0) extra(model, pose, collector, s, i, color, light, 0, seconds);
        }
        if (remote == null) return;
        if (remote.hat() > 0) {
            int color = remote.hatRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue, 0.72, 1) : remote.hatRgb();
            int light = remote.hatRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue + 0.16, 0.72, 1) : tech.gulp.lavavisual.config.ColorMath.companion(color);
            hat(model, pose, collector, s, remote.hat(), Hats.hat(remote.hat()), color, light, 0, 0.95f, 1, 0, 1, 0, seconds);
        }
        if (remote.wings() > 0) {
            int color = remote.wingRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue, 0.72, 1) : remote.wingRgb();
            int light = remote.wingRainbow() ? tech.gulp.lavavisual.config.ColorMath.hsv(hue + 0.16, 0.72, 1) : tech.gulp.lavavisual.config.ColorMath.companion(color);
            wings(model, pose, collector, s, Hats.wing(remote.wings()), color, light, 0, 0.95f, 1, 1, WingFit.DEFAULT, seconds, nanos);
        }
    }
    /**
     * Rigid hat: starts from the model's own head transform (whatever turned or tilted it this frame) and sits on the
     * hat layer or the helmet, a hair above it so the bottom never shimmers on the skin.
     */
    private static void hat(net.minecraft.client.model.player.PlayerModel model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                            net.minecraft.client.renderer.entity.state.AvatarRenderState s, int type, Hats.Model hat, int color, int light, int style,
                            float opacity, double size, double lift, double stretch, float spin, float seconds) {
        if (hat == null) return;
        float fit = Hats.fit(type);
        double top = (s.headEquipment != null && !s.headEquipment.isEmpty() ? 9.0 : s.showHat ? 8.5 : 8.0) + 0.06;
        pose.pushPose();
        model.head.translateAndRotate(pose);
        pose.scale(1, -1, -1); // model space (y down, face towards -z) -> cosmetic space (y up, face towards +z)
        // Worn hats wrap the head: wider than the 8 px head and sunk half a pixel, so they never float above it.
        pose.translate(0, top / 16.0 + (lift - Hats.sink(type) * size) / 0.9375, 0);
        pose.mulPose(new Quaternionf().rotationY(spin));
        float k = (float) (size / 0.9375);
        pose.scale(k * fit, (float) (k * stretch), k * fit);
        submitModel(collector, pose, hat, new Hats.Look(color, light, style, opacity, seconds, seconds, 1, env(s)), (float) (size * Math.max(0.2, s.scale)));
        pose.popPose();
    }
    /**
     * Cape hinged across the shoulders on the body transform. It leans back with the same movement values as the
     * vanilla cape (running, falling, turning), scaled by the sway setting, and further out when crouching.
     */
    private static void cape(net.minecraft.client.model.player.PlayerModel model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                             net.minecraft.client.renderer.entity.state.AvatarRenderState s, Hats.Model cape, int color, int light, int style,
                             float opacity, float sway, float seconds) {
        if (cape == null) return;
        var chest = s.chestEquipment;
        boolean armor = chest != null && !chest.isEmpty();
        if (armor && chest.is(net.minecraft.world.item.Items.ELYTRA)) return;
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.scale(1, -1, -1);
        // Off the jacket layer and the sleeves (the top corners of the cloth curl forward), or off the chestplate.
        pose.translate(0, 0, -(armor ? 3.45 : 2.6) / 16.0);
        float k = 1 / 0.9375f;
        Hats.Deform cloth = null;
        if (LavaVisualClient.config().capePhysics) {
            pose.scale(k, k, k);
            var level = Minecraft.getInstance().level;
            var entity = level == null ? null : level.getEntity(s.id);
            double vx = 0, vy = 0, vz = 0;
            if (entity != null) { vx = (entity.getX() - entity.xo) * 20; vy = (entity.getY() - entity.yo) * 20; vz = (entity.getZ() - entity.zo) * 20; }
            cloth = CapeCloth.shape(s.id, pose.last().pose(), vx, vy, vz, s.isCrouching, sway, frameNow);
        } else {
            float lean = 5 + Math.clamp(s.capeLean / 2 + s.capeFlap, -10, 95) * sway + (s.isCrouching ? 22 : 0);
            float side = Math.clamp(s.capeLean2 / 2, -25, 25) * sway;
            pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(lean)).rotateZ((float) Math.toRadians(side)));
            pose.scale(k, k, k);
        }
        submitModel(collector, pose, cape, new Hats.Look(color, light, style, opacity, seconds, seconds, 1, env(s), 0, cloth), (float) Math.max(0.2, s.scale));
        pose.popPose();
        capesDrawn++;
    }
    /** Movement of the player drawn with this render state (blocks per second), for cloth and accessory physics. */
    private static final double[] VELOCITY = new double[3];
    private static double[] velocity(net.minecraft.client.renderer.entity.state.AvatarRenderState s) {
        var level = Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(s.id);
        VELOCITY[0] = VELOCITY[1] = VELOCITY[2] = 0;
        if (entity != null) {
            VELOCITY[0] = (entity.getX() - entity.xo) * 20; VELOCITY[1] = (entity.getY() - entity.yo) * 20; VELOCITY[2] = (entity.getZ() - entity.zo) * 20;
        }
        return VELOCITY;
    }
    /** Accessory on the head (on top of the head, over a helmet if worn) or on the body (around the neck). */
    private static void extra(net.minecraft.client.model.player.PlayerModel model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              net.minecraft.client.renderer.entity.state.AvatarRenderState s, int type, int color, int light, int style, float seconds) {
        Hats.Model item = Hats.extra(type);
        if (item == null) return;
        boolean head = Hats.EXTRA_HEAD[type - 1];
        pose.pushPose();
        if (head) {
            model.head.translateAndRotate(pose);
            pose.scale(1, -1, -1);
            // The models keep 0.1 px off the bare head; scaled about its centre they clear the hat layer (9 px) or a
            // helmet (10 px) the same way instead of sinking into them.
            boolean helmet = s.headEquipment != null && !s.headEquipment.isEmpty();
            float grow = helmet ? 1.26f : s.showHat ? 1.13f : 1f;
            pose.translate(0, 4 / 16.0, 0);
            pose.scale(grow, grow, grow);
            pose.translate(0, 4 / 16.0, 0);
        } else {
            model.body.translateAndRotate(pose);
            pose.scale(1, -1, -1);
            // The scarf sits just under the hat layer; under a helmet's rim it moves down, and over a chestplate (1 px
            // thicker all round) it widens instead of going through it. An elytra is not armour.
            if (s.headEquipment != null && !s.headEquipment.isEmpty()) pose.translate(0, -0.5 / 16.0, 0);
            var chest = s.chestEquipment;
            if (chest != null && !chest.isEmpty() && !chest.is(net.minecraft.world.item.Items.ELYTRA)) pose.scale(1.25f, 1f, 1.36f);
        }
        float k = 1 / 0.9375f;
        pose.scale(k, k, k);
        // Head accessories sit rigidly like hats; the scarf tail swings.
        Hats.Deform bend = null;
        if (!head && LavaVisualClient.config().capePhysics) {
            double[] v = velocity(s);
            bend = AccessoryPhysics.scarf(AccessoryPhysics.of(s.id, pose.last().pose(), v[0], v[1], v[2], frameNow));
        }
        submitModel(collector, pose, item, new Hats.Look(color, light, style, 1, seconds, seconds, 1, env(s), 0, bend), (float) Math.max(0.2, s.scale));
        pose.popPose();
        extrasDrawn++;
    }
    /** Wings on the upper back, on the model's body transform (attack twist and crouch lean included); hidden with an elytra. */
    private static void wings(net.minecraft.client.model.player.PlayerModel model, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              net.minecraft.client.renderer.entity.state.AvatarRenderState s, Hats.Model wing, int color, int light, int style,
                              float opacity, double size, float flap, WingFit fit, float seconds, long nanos) {
        if (wing == null) return;
        var chest = s.chestEquipment;
        boolean armor = chest != null && !chest.isEmpty();
        if (armor && chest.is(net.minecraft.world.item.Items.ELYTRA)) return;
        float walk = Math.clamp(s.walkAnimationSpeed, 0, 1);
        WingClock clock = WING_CLOCKS.computeIfAbsent(s.id, id -> new WingClock(nanos));
        double dt = Math.min(0.1, Math.max(0, (nanos - clock.last) / 1e9));
        clock.phase += dt * (1 + 1.4 * walk) * fit.speed();
        clock.last = nanos;
        pose.pushPose();
        model.body.translateAndRotate(pose);
        pose.scale(1, -1, -1);
        // The roots sit just off the jacket layer (or the chestplate), so neither the beat nor the sweep pushes them in.
        pose.translate(0, -3 / 16.0 + fit.lift(), -(armor ? 4.0 : 3.0) / 16.0 - fit.back());
        if (fit.tilt() != 0) pose.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(-fit.tilt())));
        float k = (float) (size / 0.9375);
        pose.scale(k, k, k);
        // Inertia: the wings sweep back with speed and turns, lift while falling and beat harder then.
        float sweep = 0, lift = 0, beat = flap * (0.8f + 0.5f * walk);
        if (LavaVisualClient.config().capePhysics) {
            double[] v = velocity(s);
            AccessoryPhysics.Sim sim = AccessoryPhysics.of(s.id, pose.last().pose(), v[0], v[1], v[2], frameNow);
            sweep = sim.sweep; lift = sim.lift;
            beat *= 1 + Math.clamp(sim.lift * 0.9f, 0f, 0.35f);
        }
        submitModel(collector, pose, wing, new Hats.Look(color, light, style, opacity, seconds, (float) clock.phase, beat, env(s),
                        (float) Math.toRadians(fit.spread()) + sweep, null, lift),
                (float) (size * Math.max(0.2, s.scale)));
        pose.popPose();
    }
    /** Wings editor values: height and distance from the back (blocks), tilt and spread (degrees), beat speed. */
    private record WingFit(float lift, float back, float tilt, float spread, float speed) {
        static final WingFit DEFAULT = new WingFit(0, 0, 0, 0, 1);
        static WingFit of(tech.gulp.lavavisual.config.HudConfig c) {
            return new WingFit((float) c.wingsLift, (float) c.wingsBack, (float) c.wingsTilt, (float) c.wingsSpread, (float) c.wingsSpeed);
        }
    }
    private static void submitModel(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack pose, Hats.Model model, Hats.Look look, float worldScale) {
        Vector3f right = new Vector3f(CAMERA_RIGHT), up = new Vector3f(CAMERA_UP);
        // The captured pose already maps model space to camera-relative world space, so it becomes the model matrix.
        collector.submitCustomGeometry(pose, GLOW, (p, out) -> Hats.glow(IDENTITY, out, new Matrix4f(p.pose()), worldScale, model, look, right, up));
        collector.submitCustomGeometry(pose, HAT, (p, out) -> Hats.draw(IDENTITY, out, new Matrix4f(p.pose()), model, look));
    }
    /** The dummy stands up again: its next death may trigger the kill effect once more. */
    public static void forgetKill(int id) { if (lastKillId == id) lastKillId = -1; }
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
    /**
     * Trail from the torso, in five styles: ribbon (bright core, soft edges), neon (glowing edge lines), helix (two strands
     * winding around the path), sparks (thin ribbon + stars shed in tick) and comet (crossed ribbons with a glowing head).
     * Colour runs from the element colour at the body to its second colour at the tail; brightness and width are settings.
     */
    private static TrailLook bodyLook() {
        var c = LavaVisualClient.config();
        return new TrailLook(c.trailStyle, (float) c.trailWidth, (float) c.trailBrightness, (float) (0.6 * c.trailWidth), false);
    }
    private static void trail(PoseStack.Pose pose, VertexConsumer out, List<TrailPoint> points, Vec3 camera, int color, int light, Vector3f right, Vector3f up,
                              float spin, boolean halo, TrailLook look) {
        int n = points.size();
        if (n < 2) return;
        facing = look.facing();
        try { trailStyles(pose, out, points, camera, color, light, right, up, spin, halo, look); } finally { facing = false; }
    }
    private static void trailStyles(PoseStack.Pose pose, VertexConsumer out, List<TrailPoint> points, Vec3 camera, int color, int light, Vector3f right, Vector3f up,
                                    float spin, boolean halo, TrailLook look) {
        int n = points.size();
        float bright = look.bright(), width = look.width();
        Vec3[] p = new Vec3[n];
        float[] f = new float[n], h = new float[n];
        int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            TrailPoint t = points.get(i);
            p[i] = t.position().subtract(camera);
            f[i] = t.alpha();
            h[i] = t.half() * width * (0.35f + 0.65f * f[i]);
            col[i] = lerp(light, color, f[i]);
        }
        switch (look.style()) {
            case 1 -> { // neon: faint fill, two glowing edge lines and a thin centre line
                for (int i = 0; i + 1 < n; i++) {
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 a0 = p[i].add(0, side * h[i], 0), a1 = p[i + 1].add(0, side * h[i + 1], 0);
                        if (halo) line(pose, out, a0, a1, 0.085f * width, col[i], col[i + 1], f[i] * 0.4f * bright, f[i + 1] * 0.4f * bright);
                        else line(pose, out, a0, a1, 0.024f * width, col[i], col[i + 1], f[i] * bright, f[i + 1] * bright);
                    }
                    if (!halo) {
                        ribbon(pose, out, p[i], p[i + 1], h[i], h[i + 1], col[i], col[i + 1], f[i] * 0.2f * bright, f[i + 1] * 0.2f * bright, 0.6f);
                        line(pose, out, p[i], p[i + 1], 0.012f * width, col[i], col[i + 1], f[i] * 0.6f * bright, f[i + 1] * 0.6f * bright);
                    }
                }
            }
            case 2 -> { // helix: two strands around the path
                Vec3[] s0 = new Vec3[n], s1 = new Vec3[n];
                for (int i = 0; i < n; i++) {
                    Vec3 d = p[Math.min(n - 1, i + 1)].subtract(p[Math.max(0, i - 1)]);
                    double len = Math.sqrt(d.x * d.x + d.z * d.z);
                    double sx = len > 1e-4 ? -d.z / len : 1, sz = len > 1e-4 ? d.x / len : 0;
                    double angle = i * 0.8 - spin * 9, r = h[i] * 0.9;
                    double ox = sx * Math.cos(angle) * r, oy = Math.sin(angle) * r, oz = sz * Math.cos(angle) * r;
                    s0[i] = p[i].add(ox, oy, oz);
                    s1[i] = p[i].add(-ox, -oy, -oz);
                }
                for (int i = 0; i + 1 < n; i++) {
                    if (!halo) ribbon(pose, out, p[i], p[i + 1], h[i] * 0.45f, h[i + 1] * 0.45f, col[i], col[i + 1], f[i] * 0.18f * bright, f[i + 1] * 0.18f * bright, 0.5f);
                    for (Vec3[] strand : new Vec3[][]{s0, s1}) {
                        if (halo) line(pose, out, strand[i], strand[i + 1], 0.1f * width, col[i], col[i + 1], f[i] * 0.35f * bright, f[i + 1] * 0.35f * bright);
                        else line(pose, out, strand[i], strand[i + 1], 0.03f * width, col[i], col[i + 1], f[i] * bright, f[i + 1] * bright);
                    }
                }
            }
            case 3 -> { // sparks: a thin saturated ribbon; the stars come from tick()
                for (int i = 0; i + 1 < n; i++) {
                    if (halo) ribbon(pose, out, p[i], p[i + 1], h[i] * 0.8f, h[i + 1] * 0.8f, col[i], col[i + 1], f[i] * 0.3f * bright, f[i + 1] * 0.3f * bright, 0f);
                    else ribbon(pose, out, p[i], p[i + 1], h[i] * 0.35f, h[i + 1] * 0.35f, col[i], col[i + 1], f[i] * 0.85f * bright, f[i + 1] * 0.85f * bright, 0.25f);
                }
            }
            case 4 -> { // comet: vertical + horizontal ribbons and a glowing head
                if (halo) {
                    for (int i = 0; i + 1 < n; i++)
                        ribbon(pose, out, p[i], p[i + 1], h[i] * 1.7f, h[i + 1] * 1.7f, col[i], col[i + 1], f[i] * 0.25f * bright, f[i + 1] * 0.25f * bright, 0f);
                    glow(pose, out, p[n - 1], right, up, look.head(), color, 0.6f * bright, 16);
                    glow(pose, out, p[n - 1], right, up, look.head() * 0.37f, 0xFFFFFF, 0.5f * bright, 12);
                } else for (int i = 0; i + 1 < n; i++) {
                    ribbon(pose, out, p[i], p[i + 1], h[i], h[i + 1], col[i], col[i + 1], f[i] * 0.75f * bright, f[i + 1] * 0.75f * bright, 0.2f);
                    flat(pose, out, p[i], p[i + 1], h[i], h[i + 1], col[i], col[i + 1], f[i] * 0.55f * bright, f[i + 1] * 0.55f * bright);
                }
            }
            default -> { // ribbon: saturated body + light centre line; the halo pass adds a wide soft glow
                for (int i = 0; i + 1 < n; i++) {
                    if (halo) ribbon(pose, out, p[i], p[i + 1], h[i] * 1.9f, h[i + 1] * 1.9f, col[i], col[i + 1], f[i] * 0.3f * bright, f[i + 1] * 0.3f * bright, 0f);
                    else {
                        ribbon(pose, out, p[i], p[i + 1], h[i], h[i + 1], col[i], col[i + 1], f[i] * 0.9f * bright, f[i + 1] * 0.9f * bright, 0.12f);
                        ribbon(pose, out, p[i], p[i + 1], h[i] * 0.22f, h[i + 1] * 0.22f, brighten(col[i]), brighten(col[i + 1]), f[i] * 0.55f * bright, f[i + 1] * 0.55f * bright, 0.3f);
                    }
                }
            }
        }
    }
    /** Vertical band a..b: brightest in the middle, fading to edge*core alpha at +-h. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, double ha, double hb, int ca, int cb, float fa, float fb, float edge) {
        double ox = 0, oy = 1, oz = 0; // upright band, or across the view when facing the camera
        if (facing) {
            Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5));
            double len = side.length();
            if (len < 1e-6) return;
            ox = side.x / len; oy = side.y / len; oz = side.z / len;
        }
        int ea = UiDraw.alpha(ca, fa * edge), eb = UiDraw.alpha(cb, fb * edge), ma = UiDraw.alpha(ca, fa), mb = UiDraw.alpha(cb, fb);
        vertex(pose, out, a.x - ox * ha, a.y - oy * ha, a.z - oz * ha, ea); vertex(pose, out, b.x - ox * hb, b.y - oy * hb, b.z - oz * hb, eb); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, a.x, a.y, a.z, ma);
        vertex(pose, out, a.x, a.y, a.z, ma); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, b.x + ox * hb, b.y + oy * hb, b.z + oz * hb, eb); vertex(pose, out, a.x + ox * ha, a.y + oy * ha, a.z + oz * ha, ea);
    }
    /** Horizontal band a..b (perpendicular to the path), fading to the sides. */
    private static void flat(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, double ha, double hb, int ca, int cb, float fa, float fb) {
        if (facing) { // second band at a right angle to the camera-facing one
            Vec3 d = b.subtract(a), side = d.cross(a.add(b).scale(0.5)), normal = side.cross(d);
            double len = normal.length();
            if (len < 1e-6) return;
            double nx = normal.x / len, ny = normal.y / len, nz = normal.z / len;
            int ea = UiDraw.alpha(ca, 0), eb = UiDraw.alpha(cb, 0), ma = UiDraw.alpha(ca, fa), mb = UiDraw.alpha(cb, fb);
            vertex(pose, out, a.x - nx * ha, a.y - ny * ha, a.z - nz * ha, ea); vertex(pose, out, b.x - nx * hb, b.y - ny * hb, b.z - nz * hb, eb); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, a.x, a.y, a.z, ma);
            vertex(pose, out, a.x, a.y, a.z, ma); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, b.x + nx * hb, b.y + ny * hb, b.z + nz * hb, eb); vertex(pose, out, a.x + nx * ha, a.y + ny * ha, a.z + nz * ha, ea);
            return;
        }
        double dx = b.x - a.x, dz = b.z - a.z, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        double sx = -dz / len, sz = dx / len;
        int ea = UiDraw.alpha(ca, 0), eb = UiDraw.alpha(cb, 0), ma = UiDraw.alpha(ca, fa), mb = UiDraw.alpha(cb, fb);
        vertex(pose, out, a.x - sx * ha, a.y, a.z - sz * ha, ea); vertex(pose, out, b.x - sx * hb, b.y, b.z - sz * hb, eb); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, a.x, a.y, a.z, ma);
        vertex(pose, out, a.x, a.y, a.z, ma); vertex(pose, out, b.x, b.y, b.z, mb); vertex(pose, out, b.x + sx * hb, b.y, b.z + sz * hb, eb); vertex(pose, out, a.x + sx * ha, a.y, a.z + sz * ha, ea);
    }
    /** Camera-facing line a..b of the given width (camera at the origin: positions are camera-relative). */
    private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, float w, int ca, int cb, float fa, float fb) {
        Vec3 d = b.subtract(a), mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid);
        double len = side.length();
        if (len < 1e-6) return;
        side = side.scale(w / len);
        int a0 = UiDraw.alpha(ca, fa), b0 = UiDraw.alpha(cb, fb);
        vertex(pose, out, a.x - side.x, a.y - side.y, a.z - side.z, a0);
        vertex(pose, out, b.x - side.x, b.y - side.y, b.z - side.z, b0);
        vertex(pose, out, b.x + side.x, b.y + side.y, b.z + side.z, b0);
        vertex(pose, out, a.x + side.x, a.y + side.y, a.z + side.z, a0);
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
