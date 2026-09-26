package tech.gulp.lavavisual.effects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;

/**
 * Trails behind thrown things: ender pearls, arrows, tridents, snowballs, eggs, potions, bottles o' enchanting,
 * fireworks, wind charges and eyes of ender. Drawn by this client only, for your own throws unless set otherwise.
 * Positions are sampled per tick; the drawn head follows the interpolated projectile, and samples that are ahead of
 * it are skipped so the ribbon never pokes out in front.
 */
public final class ProjectileTrails {
    private ProjectileTrails() { }
    public static final String[] NAMES = {"Эндер-жемчуг", "Стрелы", "Трезубец", "Снежки", "Яйца", "Зелья", "Пузырёк опыта", "Фейерверк", "Заряд ветра", "Око Эндера"};
    /** Head and tail colours when the trail takes the item's own colour. */
    private static final int[][] COLORS = {{0x2BE8C4, 0x7A3CFF}, {0xFFFFFF, 0x8C96A8}, {0x49E6FF, 0x2458FF}, {0xEAF8FF, 0x7CC8FF}, {0xFFF0C8, 0xE0A860},
            {0xFF5CC8, 0x7E3BFF}, {0xC8FF4A, 0x21E08A}, {0xFFC233, 0xFF4A2B}, {0xD6F4FF, 0x86A8FF}, {0x3DFFA0, 0x0E8A62}};
    private static final int MAX_TRACKS = 48, MAX_NODES = 64;
    private record Node(Vec3 position, int born) { }
    private static final class Track {
        final int kind;
        final ArrayDeque<Node> nodes = new ArrayDeque<>();
        Entity entity;
        Vec3 last;
        int still;
        boolean alive;
        Track(int kind) { this.kind = kind; }
    }
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Map<EntityType<?>, Integer> KINDS = new HashMap<>();
    private static int tick;

    /** Index in HudConfig.PROJECTILE_IDS, or -1. */
    static int kind(Entity entity) {
        return KINDS.computeIfAbsent(entity.getType(), type -> switch (BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()) {
            case "ender_pearl" -> 0;
            case "arrow", "spectral_arrow" -> 1;
            case "trident" -> 2;
            case "snowball" -> 3;
            case "egg" -> 4;
            case "splash_potion", "lingering_potion", "potion" -> 5;
            case "experience_bottle" -> 6;
            case "firework_rocket" -> 7;
            case "wind_charge", "breeze_wind_charge" -> 8;
            case "eye_of_ender" -> 9;
            default -> -1;
        });
    }
    public static int active() { return TRACKS.size(); }

    public static void tick(Minecraft mc) {
        tick++;
        var c = LavaVisualClient.config();
        if (!c.projTrails || mc.level == null || mc.player == null) { TRACKS.clear(); return; }
        double life = life(c);
        for (Track t : TRACKS.values()) t.alive = false;
        var enabled = new HashSet<>(c.projItems);
        for (Entity e : mc.level.entitiesForRendering()) {
            int kind = kind(e);
            if (kind < 0 || !enabled.contains(HudConfig.PROJECTILE_IDS.get(kind))) continue;
            Track t = TRACKS.get(e.getId());
            if (t == null) {
                // Your own: the owner the server sent with the projectile; an eye of ender has none, so it counts
                // as yours when it appears right next to you.
                boolean mine = e instanceof Projectile p ? p.getOwner() == mc.player : e.distanceToSqr(mc.player) < 16;
                if ((c.projOnlyMine && !mine) || TRACKS.size() >= MAX_TRACKS) continue;
                t = new Track(kind);
                TRACKS.put(e.getId(), t);
            }
            t.alive = true;
            t.entity = e;
            Vec3 here = e.position().add(0, e.getBbHeight() / 2, 0);
            if (t.last == null || t.last.distanceToSqr(here) > 0.0025) {
                t.nodes.addLast(new Node(here, tick));
                while (t.nodes.size() > MAX_NODES) t.nodes.removeFirst();
                t.still = 0;
            } else t.still++;
            t.last = here;
        }
        TRACKS.values().removeIf(t -> {
            t.nodes.removeIf(n -> tick - n.born() >= life);
            if (!t.alive) t.entity = null;
            return !t.alive && t.nodes.isEmpty();
        });
    }
    private static double life(HudConfig c) { return Math.max(4, c.projLength * 20); }

    /** Render snapshot: fading points from tail to the interpolated head. */
    static List<WorldCosmetics.ShotTrail> frame(float partial) {
        var c = LavaVisualClient.config();
        if (!c.projTrails || TRACKS.isEmpty()) return List.of();
        double life = life(c), now = tick + partial;
        float half = 0.085f;
        var out = new ArrayList<WorldCosmetics.ShotTrail>();
        for (Track t : TRACKS.values()) {
            if (t.nodes.isEmpty()) continue;
            Vec3 head = null, dir = null;
            if (t.alive && t.entity != null && t.still < 3) {
                head = t.entity.getPosition(partial).add(0, t.entity.getBbHeight() / 2, 0);
                Vec3 v = t.entity.getDeltaMovement();
                if (v.lengthSqr() > 1e-6) dir = v.normalize();
            }
            var points = new ArrayList<WorldCosmetics.TrailPoint>(t.nodes.size() + 1);
            for (Node n : t.nodes) {
                if (head != null && dir != null && n.position().subtract(head).dot(dir) > -0.02) continue;
                points.add(new WorldCosmetics.TrailPoint(n.position(), (float) Math.clamp(1 - (now - n.born()) / life, 0, 1), half));
            }
            if (head != null) points.add(new WorldCosmetics.TrailPoint(head, 1f, half));
            if (points.size() < 2) continue;
            int color = c.projByItem ? COLORS[t.kind][0] : c.color("projectile") & 0xFFFFFF;
            int light = c.projByItem ? COLORS[t.kind][1] : c.color2("projectile") & 0xFFFFFF;
            out.add(new WorldCosmetics.ShotTrail(List.copyOf(points), color, light));
        }
        return out;
    }
}
