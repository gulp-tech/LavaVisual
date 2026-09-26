package tech.gulp.lavavisual.effects;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cape cloth: a small Verlet grid in cape space (x across the shoulders, y negative down the back, z negative behind
 * the player). The top row is pinned to the shoulders. Gravity and the air from your own movement are taken from the
 * actual pose, so crouching, swimming and turning look right without special cases. Every cape vertex is then bent
 * through the grid: its position on the cloth plus its distance along the local cloth normal, which keeps the
 * thickness, the piping and the details on the cloth. At rest the bend is exactly the identity.
 */
public final class CapeCloth {
    static final int W = 7, H = 10, N = W * H;
    static final float PX = 0.9375f / 16, HALF = 5.9f * PX, LEN = 19f * PX;
    private static final float STEP = 1 / 60f, GRAVITY = 13f, DRAG = 2.6f, DAMP = 0.992f, BACK = -0.35f * PX;
    private static final int[] LINK_A, LINK_B;
    private static final float[] LINK_REST, REST = new float[N * 3];
    private static final Map<Integer, Sim> SIMS = new HashMap<>();
    private static long sweep;
    /** CI: simulation steps run and the last bottom-row offset behind the rest plane (model units, negative = blown back). */
    public static long steps;
    public static float lastLift;

    static {
        for (int i = 0; i < H; i++)
            for (int j = 0; j < W; j++) {
                int k = (i * W + j) * 3;
                REST[k] = -HALF + 2 * HALF * j / (W - 1);
                REST[k + 1] = -LEN * i / (H - 1);
            }
        java.util.List<int[]> links = new java.util.ArrayList<>();
        for (int i = 0; i < H; i++)
            for (int j = 0; j < W; j++) {
                int a = i * W + j;
                if (j + 1 < W) links.add(new int[]{a, a + 1});
                if (i + 1 < H) links.add(new int[]{a, a + W});
                if (i + 1 < H && j + 1 < W) { links.add(new int[]{a, a + W + 1}); links.add(new int[]{a + 1, a + W}); }
                if (i + 2 < H) links.add(new int[]{a, a + 2 * W}); // keeps folds soft instead of creased
            }
        LINK_A = new int[links.size()]; LINK_B = new int[links.size()]; LINK_REST = new float[links.size()];
        for (int n = 0; n < links.size(); n++) {
            int a = links.get(n)[0] * 3, b = links.get(n)[1] * 3;
            LINK_A[n] = a; LINK_B[n] = b;
            float dx = REST[b] - REST[a], dy = REST[b + 1] - REST[a + 1];
            LINK_REST[n] = (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    private CapeCloth() { }

    /**
     * Steps the cloth of entity id at most once per frame (world passes only; inventory previews reuse the last shape)
     * and returns the bent shape to draw. pose maps cape model units to camera-relative blocks; (vx, vy, vz) is the
     * wearer's velocity in blocks per second.
     */
    public static Shape shape(int id, Matrix4f pose, double vx, double vy, double vz, boolean crouch, float sway, double frame) {
        long now = System.nanoTime();
        Sim sim = SIMS.computeIfAbsent(id, key -> new Sim());
        sim.seen = now;
        Matrix3f m = pose.get3x3(new Matrix3f());
        float scale = m.getColumn(0, new Vector3f()).length();
        if (scale > 1e-4f && scale < 4f && sim.frame != frame) {
            sim.frame = frame;
            sim.advance(m, scale, (float) vx, (float) vy, (float) vz, crouch, sway, now);
        }
        if (now - sweep > 5_000_000_000L) {
            sweep = now;
            SIMS.values().removeIf(other -> now - other.seen > 10_000_000_000L);
        }
        return sim.snapshot();
    }
    public static void clear() { SIMS.clear(); }

    private static final class Sim {
        final float[] p = REST.clone(), q = REST.clone(), s = REST.clone();
        final Matrix3f previous = new Matrix3f();
        boolean oriented;
        long last, seen;
        float acc, time;
        double frame = Double.NaN;

        void reset() {
            System.arraycopy(REST, 0, p, 0, p.length); System.arraycopy(REST, 0, q, 0, q.length); System.arraycopy(REST, 0, s, 0, s.length);
            oriented = false; acc = 0;
        }

        void advance(Matrix3f m, float scale, float vx, float vy, float vz, boolean crouch, float sway, long now) {
            float dt = last == 0 ? 0 : (now - last) / 1e9f;
            last = now;
            if (!Float.isFinite(p[N * 3 - 1]) || !Float.isFinite(p[W * 3 + 2])) { reset(); dt = 0; }
            dt = Math.min(dt, 0.1f);
            Matrix3f rotation = new Matrix3f(m).scale(1 / scale), toCape = new Matrix3f(m).invert();
            // Turning: free cloth keeps most of its direction in the world while the pinned top turns with the body.
            if (oriented && dt > 0) {
                Matrix3f turn = new Matrix3f(rotation).transpose().mul(previous);
                Vector3f v = new Vector3f();
                for (float[] set : new float[][]{p, q, s})
                    for (int k = W * 3; k < set.length; k += 3) {
                        turn.transform(v.set(set[k], set[k + 1], set[k + 2]));
                        set[k] += (v.x - set[k]) * 0.8f; set[k + 1] += (v.y - set[k + 1]) * 0.8f; set[k + 2] += (v.z - set[k + 2]) * 0.8f;
                    }
            }
            previous.set(rotation); oriented = true;
            Vector3f gravity = toCape.transform(new Vector3f(0, -GRAVITY, 0));
            Vector3f wind = toCape.transform(new Vector3f(-vx, -vy, -vz)).mul(sway);
            float speed = (float) Math.sqrt(vx * vx + vz * vz);
            acc = Math.min(acc + dt, STEP * 6);
            while (acc >= STEP) {
                System.arraycopy(p, 0, s, 0, p.length);
                step(gravity, wind, speed * sway, crouch);
                acc -= STEP; time += STEP; steps++;
            }
            float lift = 0;
            for (int j = 0; j < W; j++) lift += p[((H - 1) * W + j) * 3 + 2];
            lastLift = lift / W;
        }

        void step(Vector3f gravity, Vector3f wind, float speed, boolean crouch) {
            float dt2 = STEP * STEP, flutter = 2.4f * Math.min(speed, 8f) + 0.35f;
            for (int k = W * 3; k < p.length; k += 3) {
                int row = k / 3 / W, col = k / 3 % W;
                float x = p[k], y = p[k + 1], z = p[k + 2];
                float ux = (x - q[k]) / STEP, uy = (y - q[k + 1]) / STEP, uz = (z - q[k + 2]) / STEP;
                // Waves run down the cloth, stronger towards the free bottom edge and with speed.
                float wave = flutter * (row / (float) (H - 1)) * (float) Math.sin(time * 9.5f - row * 0.85f + col * 0.55f);
                float ax = gravity.x + DRAG * (wind.x - ux), ay = gravity.y + DRAG * (wind.y - uy), az = gravity.z + DRAG * (wind.z - uz) - wave;
                q[k] = x; q[k + 1] = y; q[k + 2] = z;
                p[k] = x + ux * STEP * DAMP + ax * dt2;
                p[k + 1] = y + uy * STEP * DAMP + ay * dt2;
                p[k + 2] = z + uz * STEP * DAMP + az * dt2;
            }
            for (int iteration = 0; iteration < 4; iteration++) {
                for (int n = 0; n < LINK_A.length; n++) {
                    int a = LINK_A[n], b = LINK_B[n];
                    float dx = p[b] - p[a], dy = p[b + 1] - p[a + 1], dz = p[b + 2] - p[a + 2];
                    float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length < 1e-7f) continue;
                    boolean pinA = a < W * 3;
                    if (b < W * 3) continue;
                    float f = (length - LINK_REST[n]) / length * (pinA ? 1f : 0.5f);
                    if (!pinA) { p[a] += dx * f; p[a + 1] += dy * f; p[a + 2] += dz * f; }
                    p[b] -= dx * f; p[b + 1] -= dy * f; p[b + 2] -= dz * f;
                }
                for (int k = W * 3; k < p.length; k += 3) {
                    // Stay behind the back and the legs (further out when crouching), never above the shoulders.
                    float limit = BACK + (crouch ? p[k + 1] * 0.42f : 0);
                    if (p[k + 2] > limit) p[k + 2] = limit;
                    if (p[k + 1] > -0.25f * PX) p[k + 1] = -0.25f * PX;
                }
            }
        }

        Shape snapshot() {
            float t = Math.clamp(acc / STEP, 0, 1);
            float[] pos = new float[N * 3], nrm = new float[N * 3];
            for (int k = 0; k < pos.length; k++) pos[k] = s[k] + (p[k] - s[k]) * t;
            for (int i = 0; i < H; i++)
                for (int j = 0; j < W; j++) {
                    int k = (i * W + j) * 3, l = (i * W + Math.max(0, j - 1)) * 3, r = (i * W + Math.min(W - 1, j + 1)) * 3;
                    int u = (Math.max(0, i - 1) * W + j) * 3, d = (Math.min(H - 1, i + 1) * W + j) * 3;
                    float ax = pos[r] - pos[l], ay = pos[r + 1] - pos[l + 1], az = pos[r + 2] - pos[l + 2];
                    float bx = pos[d] - pos[u], by = pos[d + 1] - pos[u + 1], bz = pos[d + 2] - pos[u + 2];
                    float nx = by * az - bz * ay, ny = bz * ax - bx * az, nz = bx * ay - by * ax;
                    float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length < 1e-9f) { nrm[k + 2] = 1; continue; }
                    nrm[k] = nx / length; nrm[k + 1] = ny / length; nrm[k + 2] = nz / length;
                }
            return new Shape(pos, nrm);
        }
    }

    /** One frame's cloth: bends model-space cape vertices (bilinear over the grid, extrapolated past its edges). */
    public record Shape(float[] pos, float[] nrm) implements Hats.Deform {
        @Override public void apply(Vector3f v) {
            float u = (v.x + HALF) / (2 * HALF) * (W - 1), r = -v.y / LEN * (H - 1);
            int j = Math.clamp((int) Math.floor(u), 0, W - 2), i = Math.clamp((int) Math.floor(r), 0, H - 2);
            float fu = u - j, fr = r - i;
            float w00 = (1 - fu) * (1 - fr), w01 = fu * (1 - fr), w10 = (1 - fu) * fr, w11 = fu * fr;
            int a = (i * W + j) * 3, b = a + 3, c = a + W * 3, d = c + 3;
            float x = pos[a] * w00 + pos[b] * w01 + pos[c] * w10 + pos[d] * w11;
            float y = pos[a + 1] * w00 + pos[b + 1] * w01 + pos[c + 1] * w10 + pos[d + 1] * w11;
            float z = pos[a + 2] * w00 + pos[b + 2] * w01 + pos[c + 2] * w10 + pos[d + 2] * w11;
            float nx = nrm[a] * w00 + nrm[b] * w01 + nrm[c] * w10 + nrm[d] * w11;
            float ny = nrm[a + 1] * w00 + nrm[b + 1] * w01 + nrm[c + 1] * w10 + nrm[d + 1] * w11;
            float nz = nrm[a + 2] * w00 + nrm[b + 2] * w01 + nrm[c + 2] * w10 + nrm[d + 2] * w11;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1e-6f) { nx /= length; ny /= length; nz /= length; } else { nx = 0; ny = 0; nz = 1; }
            float off = v.z;
            v.set(x + nx * off, y + ny * off, z + nz * off);
        }
    }
}
