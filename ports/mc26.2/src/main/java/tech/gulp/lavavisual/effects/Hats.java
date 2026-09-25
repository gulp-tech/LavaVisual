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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.gulp.lavavisual.LavaVisual;

/**
 * Hat and wing models from assets/lavavisual/hats.json (generated and previewed by tools/make_hats.py, whose
 * Builder mirrors this interpreter). Hat space: origin on top of the head, +Y up, +Z towards the face; wing space:
 * origin on the upper back. One unit = one block at scale 1.
 *
 * Faces turned away from the camera are skipped on the CPU (winding never matters, half the quads), two-sided
 * surfaces are drawn once and lit from the side that faces the camera. Curved surfaces get smooth per-vertex
 * normals; materials add a Blinn-Phong highlight (integer powers by squaring, no Math.pow) and a soft rim light.
 */
public final class Hats {
    public static final String[] NAMES = {"Конус", "Нимб", "Корона", "Цилиндр", "Ведьмина", "Колпак", "Рожки", "Ушки",
            "Кристалл", "Сомбреро", "Пропеллер", "Звёзды", "Санта", "Кепка"};
    public static final String[] WING_NAMES = {"Ангел", "Демон", "Бабочка", "Дракон", "Феникс"};
    public static final int COUNT = NAMES.length, WING_COUNT = WING_NAMES.length;
    private static final int GROUP = 0, REVOLVE = 1, TUBE = 2, TORUS = 3, SPHERE = 4, GEM = 5, PRISM = 6, POLY = 7, STRIP = 8,
            GLOW_RING = 9, GLOW_FLAT = 10, GLOW_DISC = 11, SHEET = 12;
    private static final String[] KEYS = {"c", "l", "m", "d", "dl", "cw", "lw", "w", "k", "g", "p", "gr", "r"};
    private static final String[] MATS = {"matte", "satin", "gloss", "metal", "gem", "fur", "glow"};
    private static final int GLOW = 6;
    private static final float[] GLOSS = {0.06f, 0.22f, 0.5f, 0.9f, 1.0f, 0f};
    private static final int[] SHINE = {3, 4, 5, 4, 6, 0};
    private static final boolean[] METAL = {false, false, false, true, false, false};
    private static final float[] RIM = {0.08f, 0.14f, 0.18f, 0.1f, 0.4f, 0.36f};
    private static final int DARK = 0x16161C, WHITE = 0xF2F4F8;
    private static final float LX, LY, LZ;
    static {
        float lx = 0.33f, ly = 0.88f, lz = 0.34f, n = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        LX = lx / n; LY = ly / n; LZ = lz / n;
    }
    private static Model[] hats, wings;
    private static boolean loaded;
    private static final Emitter EMITTER = new Emitter();
    private Hats() { }

    /** An opaque loaded model. */
    public static final class Model {
        private final Part[] parts;
        private Model(Part[] parts) { this.parts = parts; }
    }
    /** Colours (RGB), style (0 pattern, 1 solid, 2 gradient), opacity, animation clocks in seconds, wing beat amount, world light 0..1. */
    public record Look(int color, int light, int style, float alpha, float time, float swingTime, float flap, float env) { }

    public static String name(int type) { return NAMES[Math.floorMod(type - 1, COUNT)]; }
    public static String wingName(int type) { return WING_NAMES[Math.floorMod(type - 1, WING_COUNT)]; }
    public static Model hat(int type) { if (!loaded) load(); return hats == null ? null : hats[Math.floorMod(type - 1, Math.min(COUNT, hats.length))]; }
    public static Model wing(int type) { if (!loaded) load(); return wings == null ? null : wings[Math.floorMod(type - 1, Math.min(WING_COUNT, wings.length))]; }

    /** Solid pass: depth-tested, depth-writing quads. world maps model space to camera-relative coordinates. */
    public static void draw(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, Model model, Look look) {
        if (model == null) return;
        EMITTER.setup(pose, out, world, look, false, 1, null, null).build(model.parts, new Matrix4f(), 0);
    }
    /** Glow pass (no depth write): halos, glints, embers and motion blur. */
    public static void glow(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, float scale, Model model, Look look, Vector3f right, Vector3f up) {
        if (model == null) return;
        EMITTER.setup(pose, out, world, look, true, scale, right, up).build(model.parts, new Matrix4f(), 0);
    }

    /** CI check: every model loads and produces geometry. */
    public static String selfTest() {
        try {
            if (!loaded) load();
            if (hats == null || hats.length < COUNT) return "LavaVisual hats failed: " + (hats == null ? 0 : hats.length) + " hats";
            if (wings == null || wings.length < WING_COUNT) return "LavaVisual hats failed: " + (wings == null ? 0 : wings.length) + " wings";
            int total = 0;
            for (int i = 0; i < COUNT + WING_COUNT; i++) {
                Model model = i < COUNT ? hats[i] : wings[i - COUNT];
                Matrix4f world = new Matrix4f().translation(0, -0.3f, -2.4f).rotateY(0.7f + i);
                Look look = new Look(0xFF6A2B, 0xB45CFF, i % 3, 1, i * 0.37f, i * 0.21f, 1, 1);
                Emitter e = EMITTER.setup(null, null, world, look, false, 1, null, null);
                e.count = 0;
                e.build(model.parts, new Matrix4f(), 0);
                if (e.count == 0) return "LavaVisual hats failed: " + (i < COUNT ? name(i + 1) : wingName(i - COUNT + 1)) + " is empty";
                total += e.count;
            }
            return "LavaVisual hats ready: " + COUNT + " hats, " + WING_COUNT + " wings, " + total + " visible quads";
        } catch (RuntimeException error) {
            LavaVisual.LOGGER.error("LavaVisual hats self-test", error);
            return "LavaVisual hats failed: " + error;
        }
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Hats.class.getResourceAsStream("/assets/lavavisual/hats.json")) {
            if (in == null) throw new IllegalStateException("hats.json is missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            hats = models(root.getAsJsonArray("hats"));
            wings = root.has("wings") ? models(root.getAsJsonArray("wings")) : new Model[0];
        } catch (Exception error) {
            LavaVisual.LOGGER.error("LavaVisual: cannot load hat models", error);
            hats = null; wings = null;
        }
    }
    private static Model[] models(JsonArray array) {
        Model[] result = new Model[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = new Model(parts(array.get(i).getAsJsonObject().getAsJsonArray("parts")));
        return result;
    }

    // ------------------------------------------------------------------------------------------------ model

    private static final class Part {
        int kind, mat = 1;
        float[][] points;
        float[][][] strip, sheet, sheetNormals;
        float[] fan, ao;
        boolean closed, two, caps = true, lit = true, detail, cycle, altCycle, facets, mirror;
        int seg = 24, sides = 12, stripes, ridges, repeat = 1, spinAxis = -1;
        float twist, phase, arcFrom = 0, arcTo = 360, r0, r1, power = 1, y, size, alpha = 1, up, down, z0, z1, big, small, radius, spinSpeed;
        float crease = 0.6428f;
        float[] curl, center, radii, at, rot, bob;
        float[][] swing;
        int[] paint = {0}, alt;
        Part[] parts;
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
            if (g.has("swing")) {
                JsonArray swings = g.getAsJsonArray("swing");
                p.swing = new float[swings.size()][];
                for (int i = 0; i < swings.size(); i++) {
                    JsonArray s = swings.get(i).getAsJsonArray();
                    p.swing[i] = new float[]{"xyz".indexOf(s.get(0).getAsString()), (float) Math.toRadians(s.get(1).getAsFloat()), s.get(2).getAsFloat(), s.get(3).getAsFloat()};
                }
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
        else if (o.has("poly")) { p.kind = POLY; p.points = matrix(o.getAsJsonArray("poly")); p.fan = floats(o, "fan"); }
        else if (o.has("strip")) {
            p.kind = STRIP;
            JsonArray rows = o.getAsJsonArray("strip");
            p.strip = new float[rows.size()][][];
            for (int i = 0; i < rows.size(); i++) p.strip[i] = matrix(rows.get(i).getAsJsonArray());
        }
        else if (o.has("sheet")) {
            p.kind = SHEET;
            JsonArray rows = o.getAsJsonArray("sheet");
            p.sheet = new float[rows.size()][][];
            for (int i = 0; i < rows.size(); i++) p.sheet[i] = matrix(rows.get(i).getAsJsonArray());
            p.sheetNormals = sheetNormals(p.sheet);
        }
        else if (o.has("glowring")) { p.kind = GLOW_RING; float[] t = floats(o, "glowring"); p.big = t[0]; p.small = t[1]; }
        else if (o.has("glowflat")) { p.kind = GLOW_FLAT; p.big = floats(o, "glowflat")[0]; }
        else if (o.has("glowdisc")) { p.kind = GLOW_DISC; p.center = floats(o, "glowdisc"); p.size = number(o, "size", 0.1f); }
        else throw new IllegalArgumentException("unknown hat part " + o.keySet());
        p.seg = integer(o, "seg", p.kind == TORUS ? 32 : p.kind == SPHERE ? 12 : 24);
        p.sides = integer(o, "sides", p.kind == TORUS ? 8 : p.kind == GEM ? 6 : 12);
        p.closed = bool(o, "closed", false);
        p.two = bool(o, "two", p.kind == POLY || p.kind == STRIP || p.kind == SHEET);
        p.ao = o.has("ao") ? floats(o, "ao") : null;
        p.caps = bool(o, "caps", true); p.lit = bool(o, "lit", true); p.detail = bool(o, "detail", false);
        p.phase = number(o, "phase", 0);
        p.crease = (float) Math.cos(Math.toRadians(number(o, "crease", 50)));
        if (o.has("arc")) { float[] arc = floats(o, "arc"); p.arcFrom = arc[0]; p.arcTo = arc[1]; }
        p.curl = floats(o, "curl");
        if (o.has("radius")) { float[] r = floats(o, "radius"); p.r0 = r[0]; p.r1 = r[1]; }
        p.power = number(o, "power", 1); p.y = number(o, "y", 0); p.alpha = number(o, "alpha", 1);
        if (o.has("mat")) {
            String mat = o.get("mat").getAsString();
            p.mat = 1;
            for (int i = 0; i < MATS.length; i++) if (MATS[i].equals(mat)) p.mat = i;
        }
        if (p.mat == GLOW) p.lit = false;
        if (o.has("paint")) { p.paint = paint(o.get("paint")); p.cycle = o.get("paint").isJsonObject(); }
        if (o.has("alt")) { p.alt = paint(o.get("alt")); p.altCycle = o.get("alt").isJsonObject(); }
        if (o.has("pattern")) {
            JsonObject pattern = o.getAsJsonObject("pattern");
            p.stripes = integer(pattern, "stripes", 0); p.twist = number(pattern, "twist", 0);
            p.ridges = integer(pattern, "ridges", 0); p.facets = bool(pattern, "facets", false);
        }
        return p;
    }

    /** Per-vertex normals of a sheet grid: cross product of the central differences along rows and columns; a
     *  degenerate point (feather tip where a whole row meets) borrows its neighbour's normal (same as make_hats.py). */
    private static float[][][] sheetNormals(float[][][] grid) {
        int rows = grid.length, cols = grid[0].length;
        float[][][] n = new float[rows][cols][];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float[] u0 = grid[Math.max(0, r - 1)][c], u1 = grid[Math.min(rows - 1, r + 1)][c];
                float[] v0 = grid[r][Math.max(0, c - 1)], v1 = grid[r][Math.min(cols - 1, c + 1)];
                float ux = u1[0] - u0[0], uy = u1[1] - u0[1], uz = u1[2] - u0[2];
                float vx = v1[0] - v0[0], vy = v1[1] - v0[1], vz = v1[2] - v0[2];
                float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
                float length = (float) Math.sqrt(x * x + y * y + z * z);
                n[r][c] = length > 1e-12f ? new float[] {x / length, y / length, z / length} : null;
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (n[r][c] != null) continue;
                float[] near = null;
                if (r > 0 && n[r - 1][c] != null) near = n[r - 1][c];
                else if (r + 1 < rows && n[r + 1][c] != null) near = n[r + 1][c];
                for (int k = 0; near == null && k < cols; k++) if (n[r][k] != null) near = n[r][k];
                n[r][c] = near != null ? near : new float[] {0, 0, 1};
            }
        }
        return n;
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
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(name)) return i;
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
        final Matrix4f world = new Matrix4f(), full = new Matrix4f();
        final Matrix3f normalMatrix = new Matrix3f();
        int c, l, style, count, rim;
        float alpha, time, swingTime, flap, env, quality, scale;
        boolean glowPass;
        Vector3f right, up;
        final Vector3f[] hat = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector3f[] cam = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        final Vector3f tmp = new Vector3f(), tmp2 = new Vector3f(), vn = new Vector3f();
        final float[] p = new float[12], t = new float[4], n = new float[12];

        Emitter setup(PoseStack.Pose pose, VertexConsumer out, Matrix4f world, Look look, boolean glowPass, float scale, Vector3f right, Vector3f up) {
            this.pose = pose; this.out = out; this.world.set(world);
            this.c = look.color() & 0xFFFFFF; this.l = look.light() & 0xFFFFFF; this.style = Math.floorMod(look.style(), 3);
            this.alpha = Math.clamp(look.alpha(), 0, 1); this.time = look.time(); this.swingTime = look.swingTime(); this.flap = look.flap();
            this.env = Math.clamp(look.env(), 0.2f, 1f); this.glowPass = glowPass; this.scale = scale; this.right = right; this.up = up;
            this.rim = mix(this.l, 0xFFFFFF, 0.5f);
            // Fixed detail level: it used to follow the live FPS, so near 30/40/50 FPS the mesh was re-tessellated
            // every second and hats and wings visibly twitched. Only the FPS Boost switch lowers it now.
            this.quality = PerformanceMode.active() ? 0.75f : 1f;
            return this;
        }

        int segments(int count) { return Math.max(6, Math.round(count * (0.5f + 0.5f * quality))); }

        void build(Part[] parts, Matrix4f g, int index) {
            for (Part part : parts) {
                if (part.detail && quality < 0.5f) continue;
                if (part.kind == GROUP) { group(part, g, index); continue; }
                boolean glowShape = part.kind == GLOW_RING || part.kind == GLOW_FLAT || part.kind == GLOW_DISC;
                if (glowShape != glowPass) continue;
                full.set(world).mul(g);
                if (glowShape) { glow(part, g, index); continue; }
                normalMatrix.set(full).invert().transpose();
                switch (part.kind) {
                    case REVOLVE -> revolve(part, g, index);
                    case TUBE -> tube(part, g, index, false);
                    case TORUS -> tube(part, g, index, true);
                    case SPHERE -> sphere(part, g, index);
                    case GEM -> gem(part, g, index);
                    case PRISM -> prism(part, g, index);
                    case POLY -> poly(part, g, index);
                    case STRIP -> strip(part, g, index);
                    case SHEET -> sheet(part, g, index);
                    default -> { }
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
                    if (part.spinAxis >= 0) rotate(g, part.spinAxis, part.spinSpeed * time);
                    if (part.swing != null)
                        for (float[] s : part.swing) rotate(g, (int) s[0], s[1] * flap * (float) Math.sin(s[2] * swingTime + s[3]));
                    build(part.parts, g, part.repeat > 1 ? k : index);
                }
            }
        }
        static void rotate(Matrix4f g, int axis, float angle) {
            if (axis == 0) g.rotateX(angle); else if (axis == 1) g.rotateY(angle); else g.rotateZ(angle);
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
                case 10 -> mix(0xFF9FBF, l, 0.3f);
                case 11 -> mix(0x3CB65A, l, 0.1f);
                default -> 0xE0303A;
            };
        }
        int paint(int[] ids, boolean cycle, float t, float y, int index) {
            if (cycle) return key(ids[Math.floorMod(index, ids.length)], y);
            if (ids.length >= 2) {
                float f = Math.clamp(t, 0, 1) * (ids.length - 1);
                int i = Math.min(ids.length - 2, (int) f);
                return mix(key(ids[i], y), key(ids[i + 1], y), f - i);
            }
            return key(ids[0], y);
        }
        /** Diffuse + Blinn-Phong highlight + rim; the same formula as Builder.shade in tools/make_hats.py. */
        int shade(int base, float nx, float ny, float nz, Vector3f at, Part part) {
            if (!part.lit) return base;
            int m = part.mat;
            float ndl = Math.max(0, nx * LX + ny * LY + nz * LZ);
            float k = METAL[m] ? 0.34f + 0.4f * ndl + 0.22f * (0.5f + 0.5f * ny) : 0.5f + 0.56f * ndl;
            float length = (float) Math.sqrt(at.x * at.x + at.y * at.y + at.z * at.z);
            float vx = length > 1e-6f ? -at.x / length : 0, vy = length > 1e-6f ? -at.y / length : 1, vz = length > 1e-6f ? -at.z / length : 0;
            float spec = 0;
            if (GLOSS[m] > 0) {
                float hx = LX + vx, hy = LY + vy, hz = LZ + vz, hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
                float s = hl > 1e-6f ? Math.max(0, (nx * hx + ny * hy + nz * hz) / hl) : 0;
                for (int i = 0; i < SHINE[m]; i++) s *= s;
                spec = GLOSS[m] * s;
            }
            float facing = 1 - Math.max(0, nx * vx + ny * vy + nz * vz), rimAmount = RIM[m] * facing * facing * facing;
            int r = channel(base >> 16 & 255, rim >> 16 & 255, k, spec, rimAmount, METAL[m]);
            int g = channel(base >> 8 & 255, rim >> 8 & 255, k, spec, rimAmount, METAL[m]);
            int b = channel(base & 255, rim & 255, k, spec, rimAmount, METAL[m]);
            return r << 16 | g << 8 | b;
        }
        int channel(int base, int rimColor, float k, float spec, float rimAmount, boolean metal) {
            float tint = metal ? base * 1.2f : 255f;
            return Math.min(255, (int) (base * k * env + tint * spec * env + rimColor * rimAmount));
        }

        void set(int slot, float x, float y, float z) { p[slot * 3] = x; p[slot * 3 + 1] = y; p[slot * 3 + 2] = z; }
        void normal(int slot, float x, float y, float z) { n[slot * 3] = x; n[slot * 3 + 1] = y; n[slot * 3 + 2] = z; }

        /** Emits the quad in p/t; (hx, hy, hz) points outwards in part space; smooth uses the per-vertex normals in n. */
        void emit(Part part, Matrix4f g, int index, boolean odd, float hx, float hy, float hz, boolean smooth) {
            for (int i = 0; i < 4; i++) {
                g.transformPosition(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], hat[i]);
                world.transformPosition(hat[i], cam[i]);
            }
            float nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < 4; i++) {
                Vector3f a = cam[i], b = cam[(i + 1) & 3];
                float ax = a.x - cam[0].x, ay = a.y - cam[0].y, az = a.z - cam[0].z;
                float bx = b.x - cam[0].x, by = b.y - cam[0].y, bz = b.z - cam[0].z;
                nx += (ay - by) * (az + bz); ny += (az - bz) * (ax + bx); nz += (ax - bx) * (ay + by);
            }
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-12f || !Float.isFinite(length)) return;
            nx /= length; ny /= length; nz /= length;
            full.transformDirection(hx, hy, hz, tmp);
            if (nx * tmp.x + ny * tmp.y + nz * tmp.z < 0) { nx = -nx; ny = -ny; nz = -nz; }
            float cx = (cam[0].x + cam[1].x + cam[2].x + cam[3].x) * 0.25f, cy = (cam[0].y + cam[1].y + cam[2].y + cam[3].y) * 0.25f,
                    cz = (cam[0].z + cam[1].z + cam[2].z + cam[3].z) * 0.25f;
            boolean flip = false;
            if (nx * cx + ny * cy + nz * cz > 0) {
                if (!part.two) return;
                nx = -nx; ny = -ny; nz = -nz;
                flip = true;
            }
            count++;
            if (out == null) return;
            boolean alternate = odd && style == 0 && part.alt != null;
            int[] ids = alternate ? part.alt : part.paint;
            boolean cycle = alternate ? part.altCycle : part.cycle;
            int a = Math.clamp(Math.round(alpha * part.alpha * 255), 0, 255) << 24;
            for (int i = 0; i < 4; i++) {
                float mx = nx, my = ny, mz = nz;
                if (smooth) {
                    normalMatrix.transform(n[i * 3], n[i * 3 + 1], n[i * 3 + 2], vn);
                    float vl = vn.length();
                    if (vl > 1e-9f) {
                        float sx = vn.x / vl, sy = vn.y / vl, sz = vn.z / vl;
                        if (flip) { sx = -sx; sy = -sy; sz = -sz; }
                        if (sx * nx + sy * ny + sz * nz >= 0.05f) { mx = sx; my = sy; mz = sz; }
                    }
                }
                int base = paint(ids, cycle, t[i], hat[i].y, index);
                if (part.ao != null) base = dim(base, part.ao[0] + (part.ao[1] - part.ao[0]) * Math.clamp(t[i], 0, 1));
                int rgb = shade(base, mx, my, mz, cam[i], part);
                out.addVertex(pose, cam[i].x, cam[i].y, cam[i].z).setColor(a | rgb);
            }
        }

        void revolve(Part part, Matrix4f g, int index) {
            float[][] profile = part.points;
            int count = profile.length, seg = segments(part.seg), edges = count - 1 + (part.closed ? 1 : 0);
            double start = Math.toRadians(part.arcFrom), end = Math.toRadians(part.arcTo), phase = Math.toRadians(part.phase);
            float[] segR = new float[edges], segY = new float[edges];
            for (int i = 0; i < edges; i++) {
                float[] a = profile[i], b = profile[(i + 1) % count];
                float dr = b[0] - a[0], dy = b[1] - a[1], ln = (float) Math.hypot(dr, dy);
                if (ln < 1e-9f) ln = 1;
                segR[i] = dy / ln; segY[i] = -dr / ln;
            }
            for (int i = 0; i < edges; i++) {
                float[] a = profile[i], b = profile[(i + 1) % count];
                if (a[0] < 1e-6f && b[0] < 1e-6f) continue;
                float dr = b[0] - a[0], dy = b[1] - a[1];
                float ta = i / (float) Math.max(1, count - 1), tb = ((i + 1) % count) / (float) Math.max(1, count - 1);
                float[] na = smooth(part, segR, segY, edges, i, i - 1), nb = smooth(part, segR, segY, edges, i, i + 1);
                for (int j = 0; j < seg; j++) {
                    double a0 = phase + start + (end - start) * j / seg, a1 = phase + start + (end - start) * (j + 1) / seg, am = (a0 + a1) / 2;
                    around(part, a, a0, 0); around(part, a, a1, 1); around(part, b, a1, 2); around(part, b, a0, 3);
                    float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) Math.cos(a1);
                    normal(0, na[0] * s0, na[1], na[0] * c0); normal(1, na[0] * s1, na[1], na[0] * c1);
                    normal(2, nb[0] * s1, nb[1], nb[0] * c1); normal(3, nb[0] * s0, nb[1], nb[0] * c0);
                    t[0] = t[1] = ta; t[2] = t[3] = tb;
                    boolean odd = part.stripes > 0
                            && Math.floorMod((int) Math.floor((j + 0.5) / seg * part.stripes + part.twist * (a[1] + b[1]) / 2), 2) == 1;
                    emit(part, g, index, odd, (float) (dy * Math.sin(am)), -dr, (float) (dy * Math.cos(am)), true);
                }
            }
        }
        /** Profile normal at the end of segment own shared with segment other, averaged unless the corner is sharp. */
        static float[] smooth(Part part, float[] segR, float[] segY, int edges, int own, int other) {
            if (part.closed) other = Math.floorMod(other, edges);
            else if (other < 0 || other >= edges) return new float[]{segR[own], segY[own]};
            float dotted = segR[own] * segR[other] + segY[own] * segY[other];
            if (dotted < part.crease) return new float[]{segR[own], segY[own]};
            float r = segR[own] + segR[other], y = segY[own] + segY[other], ln = (float) Math.hypot(r, y);
            return ln < 1e-9f ? new float[]{segR[own], segY[own]} : new float[]{r / ln, y / ln};
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
            int count = path.length;
            Vector3f[] tangent = new Vector3f[count], normal = new Vector3f[count], binormal = new Vector3f[count];
            for (int i = 0; i < count; i++) {
                float[] a = closed ? path[Math.floorMod(i - 1, count)] : path[Math.max(0, i - 1)];
                float[] b = closed ? path[(i + 1) % count] : path[Math.min(count - 1, i + 1)];
                tangent[i] = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).normalize();
            }
            Vector3f first = tangent[0];
            Vector3f reference = Math.abs(first.z) < 0.9f ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
            Vector3f current = new Vector3f(first).cross(reference).normalize();
            for (int i = 0; i < count; i++) {
                Vector3f tt = tangent[i];
                current.sub(new Vector3f(tt).mul(current.dot(tt))).normalize();
                normal[i] = new Vector3f(current);
                binormal[i] = new Vector3f(tt).cross(current);
            }
            float r0 = torus ? part.small : part.r0, r1 = torus ? part.small : part.r1;
            float[] radius = new float[count], param = new float[count];
            for (int i = 0; i < count; i++) {
                float s = count > 1 ? i / (float) (count - 1) : 0;
                radius[i] = closed ? r0 : r1 + (r0 - r1) * (float) Math.pow(1 - s, part.power);
                param[i] = closed ? (float) (0.5 - 0.5 * Math.cos(Math.PI * 2 * i / count)) : s;
            }
            part.path = path; part.tangent = tangent; part.normal = normal; part.binormal = binormal;
            part.ringRadius = radius; part.param = param; part.cacheKey = key;
        }
        void ringPoint(Part part, int i, int k, int sides, int slot) {
            double angle = Math.PI * 2 * k / sides;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), r = part.ringRadius[i];
            Vector3f nn = part.normal[i], bb = part.binormal[i];
            float dx = nn.x * cos + bb.x * sin, dy = nn.y * cos + bb.y * sin, dz = nn.z * cos + bb.z * sin;
            float[] q = part.path[i];
            set(slot, q[0] + r * dx, q[1] + r * dy, q[2] + r * dz);
            normal(slot, dx, dy, dz);
        }
        void tube(Part part, Matrix4f g, int index, boolean torus) {
            frames(part, torus);
            boolean closed = torus || part.closed;
            float[][] path = part.path;
            int count = path.length, sides = segments(part.sides);
            if (count < 2) return;
            for (int i = 0; i < (closed ? count : count - 1); i++) {
                int j = (i + 1) % count;
                float mx = (path[i][0] + path[j][0]) / 2, my = (path[i][1] + path[j][1]) / 2, mz = (path[i][2] + path[j][2]) / 2;
                for (int k = 0; k < sides; k++) {
                    ringPoint(part, i, k, sides, 0); ringPoint(part, i, k + 1, sides, 1); ringPoint(part, j, k + 1, sides, 2); ringPoint(part, j, k, sides, 3);
                    t[0] = t[1] = part.param[i]; t[2] = t[3] = part.param[j];
                    boolean odd = part.ridges > 0 ? i % part.ridges == part.ridges - 1
                            : part.stripes > 0 && ((int) ((k + 0.5f) / sides * part.stripes)) % 2 == 1;
                    emit(part, g, index, odd, (p[0] + p[3] + p[6] + p[9]) / 4 - mx, (p[1] + p[4] + p[7] + p[10]) / 4 - my, (p[2] + p[5] + p[8] + p[11]) / 4 - mz, true);
                }
            }
            if (closed || !part.caps) return;
            for (int e = 0; e < 2; e++) {
                int end = e == 0 ? 0 : count - 1;
                float sign = e == 0 ? -1 : 1;
                if (part.ringRadius[end] < 1e-5f) continue;
                for (int k = 0; k < sides; k++) {
                    set(0, path[end][0], path[end][1], path[end][2]);
                    ringPoint(part, end, k, sides, 1); ringPoint(part, end, k + 1, sides, 2); ringPoint(part, end, k + 1, sides, 3);
                    t[0] = t[1] = t[2] = t[3] = part.param[end];
                    Vector3f tangent = part.tangent[end];
                    emit(part, g, index, false, sign * tangent.x, sign * tangent.y, sign * tangent.z, false);
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
                    emit(part, g, index, false, (p[0] + p[3] + p[6] + p[9]) / 4 - c[0], (p[1] + p[4] + p[7] + p[10]) / 4 - c[1], (p[2] + p[5] + p[8] + p[11]) / 4 - c[2], true);
                }
            }
        }
        void spherePoint(Part part, int a, int b, int lat, int lon, int slot) {
            double phi = Math.PI * a / lat - Math.PI / 2, theta = Math.PI * 2 * b / lon;
            float ux = (float) (Math.cos(phi) * Math.sin(theta)), uy = (float) Math.sin(phi), uz = (float) (Math.cos(phi) * Math.cos(theta));
            float[] c = part.center, r = part.radii;
            set(slot, c[0] + r[0] * ux, c[1] + r[1] * uy, c[2] + r[2] * uz);
            normal(slot, ux / r[0], uy / r[1], uz / r[2]);
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
                            (p[0] + p[3] + p[6] + p[9]) / 4 - c[0], (p[1] + p[4] + p[7] + p[10]) / 4 - c[1], (p[2] + p[5] + p[8] + p[11]) / 4 - c[2], false);
                }
            }
        }

        void prism(Part part, Matrix4f g, int index) {
            float[][] poly = part.points;
            int count = poly.length;
            float cx = 0, cy = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float[] q : poly) { cx += q[0] / count; cy += q[1] / count; lo = Math.min(lo, q[1]); hi = Math.max(hi, q[1]); }
            for (int k = 0; k < count; k++) {
                float[] a = poly[k], b = poly[(k + 1) % count];
                for (int face = 0; face < 2; face++) {
                    float z = face == 0 ? part.z1 : part.z0;
                    set(0, cx, cy, z); set(1, a[0], a[1], z); set(2, b[0], b[1], z); set(3, b[0], b[1], z);
                    t[0] = along(cy, lo, hi); t[1] = along(a[1], lo, hi); t[2] = t[3] = along(b[1], lo, hi);
                    emit(part, g, index, false, 0, 0, face == 0 ? 1 : -1, false);
                }
                float ex = b[0] - a[0], ey = b[1] - a[1], nx = ey, ny = -ex;
                if (nx * ((a[0] + b[0]) / 2 - cx) + ny * ((a[1] + b[1]) / 2 - cy) < 0) { nx = -nx; ny = -ny; }
                set(0, a[0], a[1], part.z0); set(1, b[0], b[1], part.z0); set(2, b[0], b[1], part.z1); set(3, a[0], a[1], part.z1);
                t[0] = t[3] = along(a[1], lo, hi); t[1] = t[2] = along(b[1], lo, hi);
                emit(part, g, index, false, nx, ny, 0, false);
            }
        }

        void poly(Part part, Matrix4f g, int index) {
            float[][] points = part.points;
            int count = points.length;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < count; i++) {
                float[] a = points[i], b = points[(i + 1) % count];
                lo = Math.min(lo, a[1]); hi = Math.max(hi, a[1]);
                nx += (a[1] - b[1]) * (a[2] + b[2]); ny += (a[2] - b[2]) * (a[0] + b[0]); nz += (a[0] - b[0]) * (a[1] + b[1]);
            }
            float cx, cy, cz, tc;
            if (part.fan != null) {
                cx = part.fan[0]; cy = part.fan[1]; cz = part.fan[2]; tc = part.fan.length > 3 ? part.fan[3] : 0.5f;
            } else {
                cx = 0; cy = 0; cz = 0; tc = 0;
                for (int i = 0; i < count; i++) { cx += points[i][0] / count; cy += points[i][1] / count; cz += points[i][2] / count; tc += param(points, i, lo, hi) / count; }
            }
            for (int k = 0; k < count; k++) {
                float[] a = points[k], b = points[(k + 1) % count];
                set(0, cx, cy, cz); set(1, a[0], a[1], a[2]); set(2, b[0], b[1], b[2]); set(3, b[0], b[1], b[2]);
                t[0] = tc; t[1] = param(points, k, lo, hi); t[2] = t[3] = param(points, (k + 1) % count, lo, hi);
                emit(part, g, index, false, nx, ny, nz, false);
            }
        }
        static float param(float[][] points, int i, float lo, float hi) { return points[i].length > 3 ? points[i][3] : along(points[i][1], lo, hi); }

        static int dim(int rgb, float f) {
            int r = Math.clamp(Math.round(((rgb >> 16) & 255) * f), 0, 255), gg = Math.clamp(Math.round(((rgb >> 8) & 255) * f), 0, 255);
            int b = Math.clamp(Math.round((rgb & 255) * f), 0, 255);
            return r << 16 | gg << 8 | b;
        }

        /** Curved surface grid with smooth normals (volumetric feathers, billowing membranes, wing blades). */
        void sheet(Part part, Matrix4f g, int index) {
            float[][][] grid = part.sheet, normals = part.sheetNormals;
            int rows = grid.length, cols = grid[0].length, step = quality < 0.5f && rows > 4 ? 2 : 1;
            for (int a = 0; a < rows - 1; ) {
                int b = Math.min(rows - 1, a + step);
                for (int c = 0; c < cols - 1; c++) {
                    float[] p0 = grid[a][c], p1 = grid[b][c], p2 = grid[b][c + 1], p3 = grid[a][c + 1];
                    float[] n0 = normals[a][c], n1 = normals[b][c], n2 = normals[b][c + 1], n3 = normals[a][c + 1];
                    set(0, p0[0], p0[1], p0[2]); set(1, p1[0], p1[1], p1[2]); set(2, p2[0], p2[1], p2[2]); set(3, p3[0], p3[1], p3[2]);
                    t[0] = p0.length > 3 ? p0[3] : 0; t[1] = p1.length > 3 ? p1[3] : 0; t[2] = p2.length > 3 ? p2[3] : 0; t[3] = p3.length > 3 ? p3[3] : 0;
                    normal(0, n0[0], n0[1], n0[2]); normal(1, n1[0], n1[1], n1[2]); normal(2, n2[0], n2[1], n2[2]); normal(3, n3[0], n3[1], n3[2]);
                    float hx = n0[0] + n1[0] + n2[0] + n3[0], hy = n0[1] + n1[1] + n2[1] + n3[1], hz = n0[2] + n1[2] + n2[2] + n3[2];
                    if (hx * hx + hy * hy + hz * hz < 1e-18f) { hx = 0; hy = 0; hz = 1; }
                    emit(part, g, index, false, hx, hy, hz, true);
                }
                a = b;
            }
        }

        void strip(Part part, Matrix4f g, int index) {
            float[][][] rows = part.strip;
            for (int i = 0; i < rows.length - 1; i++) {
                float[] l0 = rows[i][0], r0 = rows[i][1], l1 = rows[i + 1][0], r1 = rows[i + 1][1];
                set(0, l0[0], l0[1], l0[2]); set(1, l1[0], l1[1], l1[2]); set(2, r1[0], r1[1], r1[2]); set(3, r0[0], r0[1], r0[2]);
                t[0] = l0[3]; t[1] = l1[3]; t[2] = r1[3]; t[3] = r0[3];
                emit(part, g, index, false, 0, 0, 1, false);
            }
        }

        // glow shapes (translucent, not shaded, not culled)
        void glowVertex(float x, float y, float z, int rgb, float a) {
            full.transformPosition(x, y, z, tmp);
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
                full.transformPosition(part.center[0], part.center[1], part.center[2], tmp2);
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
                    glowVertex(0, part.y, 0, rgb, a);
                    glowVertex(0, part.y, 0, rgb, a);
                    glowVertex(part.big * s1, part.y, part.big * c1, rgb, 0);
                    glowVertex(part.big * s0, part.y, part.big * c0, rgb, 0);
                    continue;
                }
                float inner = Math.max(0, part.big - part.small), outer = part.big + part.small;
                glowVertex(inner * s0, part.y, inner * c0, rgb, 0);
                glowVertex(inner * s1, part.y, inner * c1, rgb, 0);
                glowVertex(part.big * s1, part.y, part.big * c1, rgb, a);
                glowVertex(part.big * s0, part.y, part.big * c0, rgb, a);
                glowVertex(part.big * s0, part.y, part.big * c0, rgb, a);
                glowVertex(part.big * s1, part.y, part.big * c1, rgb, a);
                glowVertex(outer * s1, part.y, outer * c1, rgb, 0);
                glowVertex(outer * s0, part.y, outer * c0, rgb, 0);
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
}
