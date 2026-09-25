package tech.gulp.lavavisual.effects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisual;

/**
 * Hat models from assets/lavavisual/hats.json (generated and previewed by tools/make_hats.py, which mirrors this
 * interpreter). Local frame: origin on top of the head, +Y up, +Z towards the face, one unit = one block at scale 1.
 * Faces turned away from the camera are skipped on the CPU, so vertex winding never matters and hats cost about
 * half their quads; two-sided surfaces are drawn once and shaded from the side that faces the camera.
 */
public final class Hats {
    public static final String[] NAMES = {"Конус", "Нимб", "Корона", "Цилиндр", "Ведьмина", "Колпак", "Рожки", "Ушки",
            "Кристалл", "Сомбреро", "Пропеллер", "Звёзды", "Санта", "Кепка"};
    public static final int COUNT = NAMES.length;
    private static final int GROUP = 0, REVOLVE = 1, TUBE = 2, TORUS = 3, SPHERE = 4, GEM = 5, PRISM = 6, POLY = 7,
            GLOW_RING = 8, GLOW_FLAT = 9, GLOW_DISC = 10;
    private static final String KEYS = "c l m d dl cw lw w k g p";
    private static final int DARK = 0x16161C, WHITE = 0xF2F4F8;
    private static final float LX, LY, LZ;
    static {
        float lx = 0.33f, ly = 0.88f, lz = 0.34f, n = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        LX = lx / n; LY = ly / n; LZ = lz / n;
    }
    private static Part[][] models;
    private static boolean loaded;
    private static final Emitter EMITTER = new Emitter();
    private Hats() { }

    public static String name(int type) { return NAMES[Math.floorMod(type - 1, COUNT)]; }

    /** Solid pass: depth-tested, depth-writing quads. world maps hat space to camera-relative coordinates. */
    public static void draw(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, int type, int color, int light, int style, float alpha, float time) {
        Part[] parts = model(type);
        if (parts == null) return;
        Emitter e = EMITTER.setup(pose, out, world, color, light, style, alpha, time, false, 1, null, null);
        e.build(parts, new Matrix4f(), 0);
    }

    /** Glow pass (no depth write): halos, glints and motion blur. */
    public static void glow(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, float scale, int type, int color, int light, int style, float alpha, float time,
                            Vector3f right, Vector3f up) {
        Part[] parts = model(type);
        if (parts == null) return;
        Emitter e = EMITTER.setup(pose, out, world, color, light, style, alpha, time, true, scale, right, up);
        e.build(parts, new Matrix4f(), 0);
    }

    /** CI check: every model loads and produces geometry. */
    public static String selfTest() {
        try {
            if (!loaded) load();
            if (models == null || models.length < COUNT) return "LavaVisual hats failed: " + (models == null ? 0 : models.length) + " models";
            int total = 0;
            for (int type = 1; type <= COUNT; type++) {
                Matrix4f world = new Matrix4f().translation(0, -0.3f, -2.4f).rotateY(0.7f);
                Emitter e = EMITTER.setup(null, null, world, 0xFF6A2B, 0xB45CFF, (type - 1) % 3, 1, type * 0.37f, false, 1, null, null);
                e.count = 0;
                e.build(models[type - 1], new Matrix4f(), 0);
                if (e.count == 0) return "LavaVisual hats failed: " + name(type) + " is empty";
                total += e.count;
            }
            return "LavaVisual hats ready: " + COUNT + " types, " + total + " visible quads";
        } catch (RuntimeException error) {
            LavaVisual.LOGGER.error("LavaVisual hats self-test", error);
            return "LavaVisual hats failed: " + error;
        }
    }

    private static Part[] model(int type) {
        if (!loaded) load();
        if (models == null) return null;
        return models[Math.floorMod(type - 1, Math.min(COUNT, models.length))];
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Hats.class.getResourceAsStream("/assets/lavavisual/hats.json")) {
            if (in == null) throw new IllegalStateException("hats.json is missing");
            JsonArray hats = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("hats");
            Part[][] parsed = new Part[hats.size()][];
            for (int i = 0; i < hats.size(); i++) parsed[i] = parts(hats.get(i).getAsJsonObject().getAsJsonArray("parts"));
            models = parsed;
        } catch (Exception error) {
            LavaVisual.LOGGER.error("LavaVisual: cannot load hat models", error);
            models = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ model

    private static final class Part {
        int kind;
        float[][] points;
        boolean closed, two, caps = true, lit = true, detail, cycle, altCycle, facets;
        int seg = 24, sides = 12, stripes, ridges, repeat = 1, spinAxis = -1;
        float twist, phase, arcFrom = 0, arcTo = 360, r0, r1, power = 1, y, size, alpha = 1, up, down, z0, z1, big, small, radius, spinSpeed;
        float[] curl, center, radii, at, rot, bob;
        int[] paint = {0}, alt;
        boolean mirror;
        Part[] parts;
        // Tube frames depend only on the path (and the ring count for tori), so they are built once.
        int cacheKey = -1;
        float[][] path;
        Vector3f[] tangent, normal, binormal;
        float[] ringRadius, param;
    }

    private static Part[] parts(JsonArray array) {
        Part[] result = new Part[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = parse(array.get(i).getAsJsonObject());
        return result;
    }

    private static Part parse(JsonObject o) {
        Part p = new Part();
        if (o.has("group")) {
            JsonObject g = o.getAsJsonObject("group");
            p.kind = GROUP;
            p.at = floats(g, "at"); p.rot = floats(g, "rot"); p.bob = floats(g, "bob");
            if (g.has("spin")) {
                JsonArray spin = g.getAsJsonArray("spin");
                p.spinAxis = "xyz".indexOf(spin.get(0).getAsString());
                p.spinSpeed = spin.get(1).getAsFloat();
            }
            p.repeat = Math.max(1, integer(g, "repeat", 1));
            p.mirror = bool(g, "mirror", false);
            p.parts = parts(o.getAsJsonArray("parts"));
            return p;
        }
        if (o.has("revolve")) { p.kind = REVOLVE; p.points = matrix(o.getAsJsonArray("revolve")); }
        else if (o.has("tube")) { p.kind = TUBE; p.points = matrix(o.getAsJsonArray("tube")); }
        else if (o.has("torus")) { p.kind = TORUS; float[] t = floats(o, "torus"); p.big = t[0]; p.small = t[1]; }
        else if (o.has("sphere")) {
            p.kind = SPHERE; p.center = floats(o, "sphere");
            JsonElement r = o.get("r");
            p.radii = r.isJsonArray() ? floats(o, "r") : new float[]{r.getAsFloat(), r.getAsFloat(), r.getAsFloat()};
        }
        else if (o.has("gem")) {
            p.kind = GEM; p.center = floats(o, "gem");
            p.radius = number(o, "r", 0.05f); p.up = number(o, "up", 0.05f); p.down = number(o, "down", 0.05f);
        }
        else if (o.has("prism")) { p.kind = PRISM; p.points = matrix(o.getAsJsonArray("prism")); float[] z = floats(o, "z"); p.z0 = z[0]; p.z1 = z[1]; }
        else if (o.has("poly")) { p.kind = POLY; p.points = matrix(o.getAsJsonArray("poly")); }
        else if (o.has("glowring")) { p.kind = GLOW_RING; float[] t = floats(o, "glowring"); p.big = t[0]; p.small = t[1]; }
        else if (o.has("glowflat")) { p.kind = GLOW_FLAT; p.big = floats(o, "glowflat")[0]; }
        else if (o.has("glowdisc")) { p.kind = GLOW_DISC; p.center = floats(o, "glowdisc"); p.size = number(o, "size", 0.1f); }
        else throw new IllegalArgumentException("unknown hat part " + o.keySet());
        p.seg = integer(o, "seg", p.kind == TORUS ? 32 : p.kind == SPHERE ? 12 : 24);
        p.sides = integer(o, "sides", p.kind == TORUS ? 8 : p.kind == GEM ? 6 : 12);
        p.closed = bool(o, "closed", false);
        p.two = bool(o, "two", p.kind == POLY);
        p.caps = bool(o, "caps", true); p.lit = bool(o, "lit", true); p.detail = bool(o, "detail", false);
        p.phase = number(o, "phase", 0);
        if (o.has("arc")) { float[] arc = floats(o, "arc"); p.arcFrom = arc[0]; p.arcTo = arc[1]; }
        p.curl = floats(o, "curl");
        if (o.has("radius")) { float[] r = floats(o, "radius"); p.r0 = r[0]; p.r1 = r[1]; }
        p.power = number(o, "power", 1); p.y = number(o, "y", 0); p.alpha = number(o, "alpha", 1);
        if (o.has("paint")) { p.paint = paint(o.get("paint")); p.cycle = o.get("paint").isJsonObject(); }
        if (o.has("alt")) { p.alt = paint(o.get("alt")); p.altCycle = o.get("alt").isJsonObject(); }
        if (o.has("pattern")) {
            JsonObject pattern = o.getAsJsonObject("pattern");
            p.stripes = integer(pattern, "stripes", 0); p.twist = number(pattern, "twist", 0);
            p.ridges = integer(pattern, "ridges", 0); p.facets = bool(pattern, "facets", false);
        }
        return p;
    }

    private static int[] paint(JsonElement element) {
        if (element.isJsonObject()) element = element.getAsJsonObject().get("cycle");
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            int[] ids = new int[array.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = key(array.get(i).getAsString());
            return ids;
        }
        return new int[]{key(element.getAsString())};
    }
    private static int key(String name) {
        String[] keys = KEYS.split(" ");
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(name)) return i;
        throw new IllegalArgumentException("unknown hat paint " + name);
    }
    private static float[] floats(JsonObject o, String name) {
        if (!o.has(name)) return null;
        JsonArray array = o.getAsJsonArray(name);
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }
    private static float[][] matrix(JsonArray array) {
        float[][] values = new float[array.size()][];
        for (int i = 0; i < values.length; i++) {
            JsonArray row = array.get(i).getAsJsonArray();
            values[i] = new float[row.size()];
            for (int j = 0; j < row.size(); j++) values[i][j] = row.get(j).getAsFloat();
        }
        return values;
    }
    private static float number(JsonObject o, String name, float fallback) { return o.has(name) ? o.get(name).getAsFloat() : fallback; }
    private static int integer(JsonObject o, String name, int fallback) { return o.has(name) ? o.get(name).getAsInt() : fallback; }
    private static boolean bool(JsonObject o, String name, boolean fallback) { return o.has(name) ? o.get(name).getAsBoolean() : fallback; }

    // ------------------------------------------------------------------------------------------------ geometry

    private static final class Emitter {
        PoseStack.Pose pose;
        VertexConsumer out;
        final Matrix4f world = new Matrix4f();
        int c, l, style, count;
        float alpha, time, quality, scale;
        boolean glowPass;
        Vector3f right, up;
        final Vector3f[] hat = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector3f[] cam = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector3f tmp = new Vector3f(), tmp2 = new Vector3f();
        final float[] p = new float[12], t = new float[4];

        Emitter setup(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, int color, int light, int style, float alpha, float time, boolean glowPass,
                      float scale, Vector3f right, Vector3f up) {
            this.pose = pose; this.out = out; this.world.set(world);
            this.c = color & 0xFFFFFF; this.l = light & 0xFFFFFF; this.style = Math.floorMod(style, 3);
            this.alpha = Math.clamp(alpha, 0, 1); this.time = time; this.glowPass = glowPass; this.scale = scale; this.right = right; this.up = up;
            this.quality = (float) PerformanceMode.quality();
            return this;
        }

        int segments(int count) { return Math.max(6, Math.round(count * (0.5f + 0.5f * quality))); }

        void build(Part[] parts, Matrix4f g, int index) {
            for (Part part : parts) {
                if (part.detail && quality < 0.5f) continue;
                switch (part.kind) {
                    case GROUP -> group(part, g, index);
                    case GLOW_RING, GLOW_FLAT, GLOW_DISC -> { if (glowPass) glow(part, g, index); }
                    default -> {
                        if (glowPass) continue;
                        switch (part.kind) {
                            case REVOLVE -> revolve(part, g, index);
                            case TUBE -> tube(part, g, index, false);
                            case TORUS -> tube(part, g, index, true);
                            case SPHERE -> sphere(part, g, index);
                            case GEM -> gem(part, g, index);
                            case PRISM -> prism(part, g, index);
                            case POLY -> poly(part, g, index);
                            default -> { }
                        }
                    }
                }
            }
        }

        void group(Part part, Matrix4f parent, int index) {
            for (int k = 0; k < part.repeat; k++) {
                for (int m = 0; m < (part.mirror ? 2 : 1); m++) {
                    Matrix4f g = new Matrix4f(parent);
                    if (part.repeat > 1) g.rotateY((float) (Math.PI * 2 * k / part.repeat));
                    if (m == 1) g.scale(-1, 1, 1);
                    float ax = 0, ay = 0, az = 0;
                    if (part.at != null) { ax = part.at[0]; ay = part.at[1]; az = part.at[2]; }
                    if (part.bob != null) ay += part.bob[0] * (float) Math.sin(part.bob[1] * time + part.bob[2] * k);
                    g.translate(ax, ay, az);
                    if (part.rot != null) g.rotateY(rad(part.rot[1])).rotateX(rad(part.rot[0])).rotateZ(rad(part.rot[2]));
                    if (part.spinAxis >= 0) {
                        float angle = part.spinSpeed * time;
                        if (part.spinAxis == 0) g.rotateX(angle); else if (part.spinAxis == 1) g.rotateY(angle); else g.rotateZ(angle);
                    }
                    build(part.parts, g, part.repeat > 1 ? k : index);
                }
            }
        }

        // colours
        int key(int id, float y) {
            if (id <= 4) {
                if (style == 1) return c;
                if (style == 2) return mix(c, l, y / 0.42f);
            }
            return switch (id) {
                case 0 -> c;
                case 1 -> l;
                case 2 -> mix(c, l, 0.55f);
                case 3 -> mix(c, DARK, 0.72f);
                case 4 -> mix(l, DARK, 0.55f);
                case 5 -> mix(c, WHITE, 0.4f);
                case 6 -> mix(l, WHITE, 0.45f);
                case 7 -> WHITE;
                case 8 -> 0x1D1E24;
                case 9 -> mix(0xFFCF5A, l, 0.22f);
                default -> mix(0xFF9FBF, l, 0.3f);
            };
        }
        int paint(int[] ids, boolean cycle, float t, float y, int index) {
            if (cycle) return key(ids[Math.floorMod(index, ids.length)], y);
            if (ids.length >= 2) return mix(key(ids[0], y), key(ids[1], y), t);
            return key(ids[0], y);
        }

        void set(int slot, float x, float y, float z) { p[slot * 3] = x; p[slot * 3 + 1] = y; p[slot * 3 + 2] = z; }

        /** Emits the quad in p/t; (hx, hy, hz) points outwards in part space. */
        void emit(Part part, Matrix4f g, int index, boolean odd, float hx, float hy, float hz) {
            for (int i = 0; i < 4; i++) {
                g.transformPosition(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], hat[i]);
                world.transformPosition(hat[i], cam[i]);
            }
            // Newell normal on offsets from the first corner (no precision loss far from the camera).
            float nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < 4; i++) {
                Vector3f a = cam[i], b = cam[(i + 1) & 3];
                float ay = a.y - cam[0].y, az = a.z - cam[0].z, ax = a.x - cam[0].x;
                float by = b.y - cam[0].y, bz = b.z - cam[0].z, bx = b.x - cam[0].x;
                nx += (ay - by) * (az + bz); ny += (az - bz) * (ax + bx); nz += (ax - bx) * (ay + by);
            }
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-12f || !Float.isFinite(length)) return;
            nx /= length; ny /= length; nz /= length;
            g.transformDirection(hx, hy, hz, tmp);
            world.transformDirection(tmp);
            if (nx * tmp.x + ny * tmp.y + nz * tmp.z < 0) { nx = -nx; ny = -ny; nz = -nz; }
            float cx = (cam[0].x + cam[1].x + cam[2].x + cam[3].x) * 0.25f, cy = (cam[0].y + cam[1].y + cam[2].y + cam[3].y) * 0.25f,
                    cz = (cam[0].z + cam[1].z + cam[2].z + cam[3].z) * 0.25f;
            if (nx * cx + ny * cy + nz * cz > 0) {
                if (!part.two) return;
                nx = -nx; ny = -ny; nz = -nz;
            }
            count++;
            if (out == null) return;
            float shade = part.lit ? 0.55f + 0.5f * Math.max(0, nx * LX + ny * LY + nz * LZ) : 1f;
            boolean alternate = odd && style == 0 && part.alt != null;
            int[] ids = alternate ? part.alt : part.paint;
            boolean cycle = alternate ? part.altCycle : part.cycle;
            int a = Math.clamp(Math.round(alpha * part.alpha * 255), 0, 255) << 24;
            for (int i = 0; i < 4; i++) {
                int rgb = shade(paint(ids, cycle, t[i], hat[i].y, index), shade);
                out.addVertex(pose, cam[i].x, cam[i].y, cam[i].z).setColor(a | rgb);
            }
        }

        void revolve(Part part, Matrix4f g, int index) {
            float[][] profile = part.points;
            int n = profile.length, seg = segments(part.seg);
            double start = Math.toRadians(part.arcFrom), end = Math.toRadians(part.arcTo), phase = Math.toRadians(part.phase);
            int edges = n - 1 + (part.closed ? 1 : 0);
            for (int i = 0; i < edges; i++) {
                float[] a = profile[i], b = profile[(i + 1) % n];
                if (a[0] < 1e-6f && b[0] < 1e-6f) continue;
                float dr = b[0] - a[0], dy = b[1] - a[1];
                float ta = i / (float) Math.max(1, n - 1), tb = ((i + 1) % n) / (float) Math.max(1, n - 1);
                for (int j = 0; j < seg; j++) {
                    double a0 = phase + start + (end - start) * j / seg, a1 = phase + start + (end - start) * (j + 1) / seg, am = (a0 + a1) / 2;
                    around(part, a, a0, 0); around(part, a, a1, 1); around(part, b, a1, 2); around(part, b, a0, 3);
                    t[0] = t[1] = ta; t[2] = t[3] = tb;
                    boolean odd = part.stripes > 0
                            && Math.floorMod((int) Math.floor((j + 0.5) / seg * part.stripes + part.twist * (a[1] + b[1]) / 2), 2) == 1;
                    emit(part, g, index, odd, (float) (dy * Math.sin(am)), -dr, (float) (dy * Math.cos(am)));
                }
            }
        }
        void around(Part part, float[] point, double angle, int slot) {
            float r = point[0], y = point[1];
            if (part.curl != null) {
                float f = Math.max(0, (r - part.curl[1]) / (part.curl[2] - part.curl[1]));
                double s = Math.sin(angle);
                y += part.curl[0] * (float) (s * s) * f * f;
            }
            set(slot, (float) (r * Math.sin(angle)), y, (float) (r * Math.cos(angle)));
        }

        void frames(Part part, boolean torus) {
            int key = torus ? segments(part.seg) : 0;
            if (part.cacheKey == key && part.path != null) return;
            float[][] path;
            if (torus) {
                path = new float[key][];
                for (int i = 0; i < key; i++) {
                    double a = Math.PI * 2 * i / key;
                    path[i] = new float[]{(float) (part.big * Math.sin(a)), part.y, (float) (part.big * Math.cos(a))};
                }
            } else path = part.points;
            boolean closed = torus || part.closed;
            int n = path.length;
            Vector3f[] tangent = new Vector3f[n], normal = new Vector3f[n], binormal = new Vector3f[n];
            for (int i = 0; i < n; i++) {
                float[] a = closed ? path[Math.floorMod(i - 1, n)] : path[Math.max(0, i - 1)];
                float[] b = closed ? path[(i + 1) % n] : path[Math.min(n - 1, i + 1)];
                tangent[i] = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
            }
            Vector3f first = tangent[0];
            Vector3f reference = Math.abs(first.z) < 0.9f ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
            Vector3f current = new Vector3f(first).cross(reference).normalize();
            for (int i = 0; i < n; i++) {
                Vector3f t = tangent[i];
                current.sub(new Vector3f(t).mul(current.dot(t))).normalize();
                normal[i] = new Vector3f(current);
                binormal[i] = new Vector3f(t).cross(current);
            }
            float r0 = torus ? part.small : part.r0, r1 = torus ? part.small : part.r1;
            float[] radius = new float[n], param = new float[n];
            for (int i = 0; i < n; i++) {
                float s = n > 1 ? i / (float) (n - 1) : 0;
                radius[i] = closed ? r0 : r1 + (r0 - r1) * (float) Math.pow(1 - s, part.power);
                param[i] = closed ? (float) (0.5 - 0.5 * Math.cos(Math.PI * 2 * i / n)) : s;
            }
            part.path = path; part.tangent = tangent; part.normal = normal; part.binormal = binormal;
            part.ringRadius = radius; part.param = param; part.cacheKey = key;
        }
        void ringPoint(Part part, int i, int k, int sides, int slot) {
            double angle = Math.PI * 2 * k / sides;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), r = part.ringRadius[i];
            Vector3f nn = part.normal[i], bb = part.binormal[i];
            float[] q = part.path[i];
            set(slot, q[0] + r * (nn.x * cos + bb.x * sin), q[1] + r * (nn.y * cos + bb.y * sin), q[2] + r * (nn.z * cos + bb.z * sin));
        }
        void tube(Part part, Matrix4f g, int index, boolean torus) {
            frames(part, torus);
            boolean closed = torus || part.closed;
            float[][] path = part.path;
            int n = path.length, sides = segments(part.sides);
            if (n < 2) return;
            for (int i = 0; i < (closed ? n : n - 1); i++) {
                int j = (i + 1) % n;
                float mx = (path[i][0] + path[j][0]) / 2, my = (path[i][1] + path[j][1]) / 2, mz = (path[i][2] + path[j][2]) / 2;
                for (int k = 0; k < sides; k++) {
                    ringPoint(part, i, k, sides, 0); ringPoint(part, i, k + 1, sides, 1); ringPoint(part, j, k + 1, sides, 2); ringPoint(part, j, k, sides, 3);
                    t[0] = t[1] = part.param[i]; t[2] = t[3] = part.param[j];
                    boolean odd = part.ridges > 0 ? i % part.ridges == part.ridges - 1
                            : part.stripes > 0 && ((int) ((k + 0.5f) / sides * part.stripes)) % 2 == 1;
                    emit(part, g, index, odd, (p[0] + p[3] + p[6] + p[9]) / 4 - mx, (p[1] + p[4] + p[7] + p[10]) / 4 - my, (p[2] + p[5] + p[8] + p[11]) / 4 - mz);
                }
            }
            if (closed || !part.caps) return;
            for (int e = 0; e < 2; e++) {
                int end = e == 0 ? 0 : n - 1;
                float sign = e == 0 ? -1 : 1;
                if (part.ringRadius[end] < 1e-5f) continue;
                for (int k = 0; k < sides; k++) {
                    set(0, path[end][0], path[end][1], path[end][2]);
                    ringPoint(part, end, k, sides, 1); ringPoint(part, end, k + 1, sides, 2); ringPoint(part, end, k + 1, sides, 3);
                    t[0] = t[1] = t[2] = t[3] = part.param[end];
                    Vector3f tangent = part.tangent[end];
                    emit(part, g, index, false, sign * tangent.x, sign * tangent.y, sign * tangent.z);
                }
            }
        }

        void sphere(Part part, Matrix4f g, int index) {
            int lon = segments(part.seg), lat = Math.max(3, lon / 2);
            float[] c = part.center;
            for (int a = 0; a < lat; a++) {
                for (int b = 0; b < lon; b++) {
                    spherePoint(part, a, b, lat, lon, 0); spherePoint(part, a, b + 1, lat, lon, 1);
                    spherePoint(part, a + 1, b + 1, lat, lon, 2); spherePoint(part, a + 1, b, lat, lon, 3);
                    t[0] = t[1] = a / (float) lat; t[2] = t[3] = (a + 1) / (float) lat;
                    emit(part, g, index, false, (p[0] + p[3] + p[6] + p[9]) / 4 - c[0], (p[1] + p[4] + p[7] + p[10]) / 4 - c[1], (p[2] + p[5] + p[8] + p[11]) / 4 - c[2]);
                }
            }
        }
        void spherePoint(Part part, int a, int b, int lat, int lon, int slot) {
            double phi = Math.PI * a / lat - Math.PI / 2, theta = Math.PI * 2 * b / lon;
            float[] c = part.center, r = part.radii;
            set(slot, (float) (c[0] + r[0] * Math.cos(phi) * Math.sin(theta)), (float) (c[1] + r[1] * Math.sin(phi)), (float) (c[2] + r[2] * Math.cos(phi) * Math.cos(theta)));
        }

        void gem(Part part, Matrix4f g, int index) {
            float[] c = part.center;
            int sides = Math.max(3, part.sides);
            for (int k = 0; k < sides; k++) {
                double a0 = Math.PI * 2 * k / sides, a1 = Math.PI * 2 * (k + 1) / sides;
                for (int half = 0; half < 2; half++) {
                    float apex = half == 0 ? c[1] + part.up : c[1] - part.down;
                    set(0, c[0] + part.radius * (float) Math.sin(a0), c[1], c[2] + part.radius * (float) Math.cos(a0));
                    set(1, c[0] + part.radius * (float) Math.sin(a1), c[1], c[2] + part.radius * (float) Math.cos(a1));
                    set(2, c[0], apex, c[2]); set(3, c[0], apex, c[2]);
                    t[0] = t[1] = 0.5f; t[2] = t[3] = half == 0 ? 1 : 0;
                    emit(part, g, index, part.facets && (k + half) % 2 == 1,
                            (p[0] + p[3] + p[6] + p[9]) / 4 - c[0], (p[1] + p[4] + p[7] + p[10]) / 4 - c[1], (p[2] + p[5] + p[8] + p[11]) / 4 - c[2]);
                }
            }
        }

        void prism(Part part, Matrix4f g, int index) {
            float[][] poly = part.points;
            int n = poly.length;
            float cx = 0, cy = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float[] q : poly) { cx += q[0] / n; cy += q[1] / n; lo = Math.min(lo, q[1]); hi = Math.max(hi, q[1]); }
            for (int k = 0; k < n; k++) {
                float[] a = poly[k], b = poly[(k + 1) % n];
                for (int face = 0; face < 2; face++) {
                    float z = face == 0 ? part.z1 : part.z0;
                    set(0, cx, cy, z); set(1, a[0], a[1], z); set(2, b[0], b[1], z); set(3, b[0], b[1], z);
                    t[0] = along(cy, lo, hi); t[1] = along(a[1], lo, hi); t[2] = t[3] = along(b[1], lo, hi);
                    emit(part, g, index, false, 0, 0, face == 0 ? 1 : -1);
                }
                float ex = b[0] - a[0], ey = b[1] - a[1], nx = ey, ny = -ex;
                if (nx * ((a[0] + b[0]) / 2 - cx) + ny * ((a[1] + b[1]) / 2 - cy) < 0) { nx = -nx; ny = -ny; }
                set(0, a[0], a[1], part.z0); set(1, b[0], b[1], part.z0); set(2, b[0], b[1], part.z1); set(3, a[0], a[1], part.z1);
                t[0] = t[3] = along(a[1], lo, hi); t[1] = t[2] = along(b[1], lo, hi);
                emit(part, g, index, false, nx, ny, 0);
            }
        }

        void poly(Part part, Matrix4f g, int index) {
            float[][] points = part.points;
            int n = points.length;
            float cx = 0, cy = 0, cz = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < n; i++) {
                float[] a = points[i], b = points[(i + 1) % n];
                cx += a[0] / n; cy += a[1] / n; cz += a[2] / n; lo = Math.min(lo, a[1]); hi = Math.max(hi, a[1]);
                nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1]);
            }
            for (int k = 0; k < n; k++) {
                float[] a = points[k], b = points[(k + 1) % n];
                set(0, cx, cy, cz); set(1, a[0], a[1], a[2]); set(2, b[0], b[1], b[2]); set(3, b[0], b[1], b[2]);
                t[0] = along(cy, lo, hi); t[1] = along(a[1], lo, hi); t[2] = t[3] = along(b[1], lo, hi);
                emit(part, g, index, false, nx, ny, nz);
            }
        }

        // glow shapes (translucent, not shaded, not culled)
        void glowVertex(Matrix4f g, float x, float y, float z, int rgb, float a) {
            g.transformPosition(x, y, z, tmp);
            world.transformPosition(tmp);
            glowPoint(tmp.x, tmp.y, tmp.z, rgb, a);
        }
        void glowPoint(float x, float y, float z, int rgb, float a) {
            count++;
            if (out != null) out.addVertex(pose, x, y, z).setColor(Math.clamp(Math.round(a * 255), 0, 255) << 24 | rgb);
        }
        void glow(Part part, Matrix4f g, int index) {
            float a = alpha * part.alpha;
            int rgb = paint(part.paint, part.cycle, 0.5f, part.center != null ? part.center[1] : part.y, index);
            int seg = segments(40);
            if (part.kind == GLOW_DISC) {
                if (right == null || up == null) return;
                g.transformPosition(part.center[0], part.center[1], part.center[2], tmp2);
                world.transformPosition(tmp2);
                float size = part.size * scale;
                int steps = Math.max(8, seg / 2);
                for (int j = 0; j < steps; j++) {
                    double a0 = Math.PI * 2 * j / steps, a1 = Math.PI * 2 * (j + 1) / steps;
                    float c0 = (float) Math.cos(a0) * size, s0 = (float) Math.sin(a0) * size, c1 = (float) Math.cos(a1) * size, s1 = (float) Math.sin(a1) * size;
                    glowPoint(tmp2.x, tmp2.y, tmp2.z, rgb, a);
                    glowPoint(tmp2.x, tmp2.y, tmp2.z, rgb, a);
                    glowPoint(tmp2.x + right.x * c1 + up.x * s1, tmp2.y + right.y * c1 + up.y * s1, tmp2.z + right.z * c1 + up.z * s1, rgb, 0);
                    glowPoint(tmp2.x + right.x * c0 + up.x * s0, tmp2.y + right.y * c0 + up.y * s0, tmp2.z + right.z * c0 + up.z * s0, rgb, 0);
                }
                return;
            }
            for (int j = 0; j < seg; j++) {
                float s0 = (float) Math.sin(Math.PI * 2 * j / seg), c0 = (float) Math.cos(Math.PI * 2 * j / seg);
                float s1 = (float) Math.sin(Math.PI * 2 * (j + 1) / seg), c1 = (float) Math.cos(Math.PI * 2 * (j + 1) / seg);
                if (part.kind == GLOW_FLAT) {
                    glowVertex(g, 0, part.y, 0, rgb, a);
                    glowVertex(g, 0, part.y, 0, rgb, a);
                    glowVertex(g, part.big * s1, part.y, part.big * c1, rgb, 0);
                    glowVertex(g, part.big * s0, part.y, part.big * c0, rgb, 0);
                    continue;
                }
                float inner = Math.max(0, part.big - part.small), outer = part.big + part.small;
                glowVertex(g, inner * s0, part.y, inner * c0, rgb, 0);
                glowVertex(g, inner * s1, part.y, inner * c1, rgb, 0);
                glowVertex(g, part.big * s1, part.y, part.big * c1, rgb, a);
                glowVertex(g, part.big * s0, part.y, part.big * c0, rgb, a);
                glowVertex(g, part.big * s0, part.y, part.big * c0, rgb, a);
                glowVertex(g, part.big * s1, part.y, part.big * c1, rgb, a);
                glowVertex(g, outer * s1, part.y, outer * c1, rgb, 0);
                glowVertex(g, outer * s0, part.y, outer * c0, rgb, 0);
            }
        }
    }

    private static float along(float y, float lo, float hi) { return hi > lo ? (y - lo) / (hi - lo) : 0.5f; }
    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
    static int mix(int from, int to, float t) {
        t = Math.clamp(t, 0, 1);
        int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
        int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return r << 16 | g << 8 | b;
    }
    private static int shade(int rgb, float k) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * k)), g = Math.min(255, (int) ((rgb >> 8 & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }
}
