package tech.gulp.lavavisual.effects;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Motion of the accessories that are not fixed to the head (the cape has its own cloth, see CapeCloth): the scarf
 * tail swings like a short pendulum and the wings sweep back and lift with inertia. Both are damped springs driven by
 * the player's movement in the body's own frame (running, strafing, jumping, falling, turning), advanced once per
 * frame and per player however often the player is drawn.
 */
public final class AccessoryPhysics {
    private static final Map<Integer, Sim> SIMS = new HashMap<>();
    private static long sweepAt;
    private AccessoryPhysics() { }

    static final class Sim {
        double frame = Double.NaN;
        long last, seen;
        boolean started;
        float yaw, lastForward, phase;
        /** Scarf swing (radians): fwd > 0 away from the chest, side > 0 towards the model's +x; with velocities. */
        float fwd, side, fwdV, sideV;
        /** Wings: sweep > 0 tips go back, lift > 0 tips go up; with velocities. */
        float sweep, lift, sweepV, liftV;
    }

    /** The player's springs, advanced for this frame. pose maps the body's model space (+y up, +z chest) to world axes. */
    static Sim of(int id, Matrix4f pose, double vx, double vy, double vz, double frame) {
        long now = System.nanoTime();
        Sim sim = SIMS.computeIfAbsent(id, key -> new Sim());
        sim.seen = now;
        Matrix3f m = pose.get3x3(new Matrix3f());
        float scale = m.getColumn(0, new Vector3f()).length();
        // GUI renders (inventory, editors) use huge scales and other angles: they show the state, they do not drive it.
        if (scale > 1e-4f && scale < 4f && sim.frame != frame) {
            sim.frame = frame;
            advance(sim, m, (float) vx, (float) vy, (float) vz, now, id);
        }
        if (now - sweepAt > 5_000_000_000L) {
            sweepAt = now;
            SIMS.values().removeIf(other -> now - other.seen > 10_000_000_000L);
        }
        return sim;
    }
    static void clear() { SIMS.clear(); }

    private static void advance(Sim sim, Matrix3f m, float vx, float vy, float vz, long now, int id) {
        Vector3f side = m.getColumn(0, new Vector3f()).normalize(), up = m.getColumn(1, new Vector3f()).normalize(),
                forward = m.getColumn(2, new Vector3f()).normalize();
        float yaw = (float) Math.atan2(forward.x, forward.z);
        if (!sim.started) {
            sim.started = true; sim.yaw = yaw; sim.last = now; sim.phase = (id & 63) * 0.37f;
            return;
        }
        float dt = Math.min(0.1f, Math.max(0, (now - sim.last) / 1e9f));
        sim.last = now;
        if (dt < 1e-4f) return;
        float turn = yaw - sim.yaw;
        if (turn > Math.PI) turn -= (float) (2 * Math.PI);
        if (turn < -Math.PI) turn += (float) (2 * Math.PI);
        sim.yaw = yaw;
        float yawRate = Math.clamp(turn / dt, -12f, 12f);
        // Velocity in the body frame, blocks per second.
        float vF = vx * forward.x + vy * forward.y + vz * forward.z;
        float vS = vx * side.x + vy * side.y + vz * side.z;
        float vU = vx * up.x + vy * up.y + vz * up.z;
        float accel = Math.clamp((vF - sim.lastForward) / dt, -60f, 60f);
        sim.lastForward = vF;
        sim.phase += dt;
        float breeze = (float) Math.sin(sim.phase * 1.7f), breeze2 = (float) Math.sin(sim.phase * 1.1f + 1.3f);

        // Scarf tail: pressed to the chest when running forward, lifted by falling, left behind when strafing or turning.
        float fwdTarget = Math.clamp(-vF * 0.09f, -0.07f, 0.55f) + Math.clamp(-vU * 0.06f, 0f, 0.6f) + 0.02f * breeze2;
        float sideTarget = Math.clamp(-vS * 0.10f, -0.5f, 0.5f) - Math.clamp(yawRate * 0.05f, -0.35f, 0.35f) + 0.03f * breeze;
        // Wings: swept back by speed and turning, tips up while falling and down on a jump.
        float sweepTarget = Math.clamp(vF * 0.07f, -0.1f, 0.42f) + Math.min(0.2f, Math.abs(yawRate) * 0.03f);
        float liftTarget = Math.clamp(-vU * 0.05f, -0.22f, 0.38f);

        int steps = Math.max(1, (int) Math.ceil(dt * 120));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            sim.fwdV += (45f * (fwdTarget - sim.fwd) - 5.5f * sim.fwdV - accel * 0.02f) * h;
            sim.sideV += (45f * (sideTarget - sim.side) - 5.5f * sim.sideV) * h;
            sim.fwd += sim.fwdV * h;
            sim.side += sim.sideV * h;
            if (sim.fwd < -0.08f) { sim.fwd = -0.08f; sim.fwdV = Math.max(0, sim.fwdV); } // the chest is in the way
            sim.fwd = Math.min(sim.fwd, 1.1f);
            sim.side = Math.clamp(sim.side, -0.8f, 0.8f);

            sim.sweepV += (60f * (sweepTarget - sim.sweep) - 7f * sim.sweepV) * h;
            sim.liftV += (60f * (liftTarget - sim.lift) - 7f * sim.liftV) * h;
            sim.sweep = Math.clamp(sim.sweep + sim.sweepV * h, -0.2f, 0.6f);
            sim.lift = Math.clamp(sim.lift + sim.liftV * h, -0.3f, 0.5f);
        }
    }

    /** CI check: falling lifts the scarf tail and the wing tips, running sweeps the wings back and presses the tail to
     *  the chest, and the bend keeps the tail's length. */
    public static String selfTest() {
        Matrix3f axes = new Matrix3f();
        Sim fall = new Sim(), run = new Sim();
        long now = 0;
        for (int i = 0; i <= 60; i++, now += 16_666_667L) {
            advance(fall, axes, 0, -12, 0, now, 7);
            advance(run, axes, 0, 0, 5.6f, now, 7);
        }
        Vector3f a = new Vector3f(0.1f, -0.2f, 0.18f), b = new Vector3f(0.1f, -0.3f, 0.18f);
        float before = a.distance(b);
        Hats.Deform bend = new ScarfBend(0.6f, 0.3f);
        bend.apply(a);
        bend.apply(b);
        boolean falls = fall.fwd > 0.2f && fall.lift > 0.1f, runs = run.sweep > 0.15f && run.fwd < 0, keeps = Math.abs(a.distance(b) - before) < 0.01f;
        String values = String.format(java.util.Locale.ROOT, "fall %.2f/%.2f, run %.2f/%.2f, length %.3f/%.3f", fall.fwd, fall.lift, run.sweep, run.fwd, before, a.distance(b));
        return (falls && runs && keeps ? "LavaVisual accessory physics ok: " : "LavaVisual accessory physics failed: ") + values;
    }

    /** Bend of the scarf for the current swing, or null when it hangs straight. */
    static Hats.Deform scarf(Sim sim) {
        if (Math.abs(sim.fwd) < 1e-3f && Math.abs(sim.side) < 1e-3f) return null;
        return new ScarfBend(sim.fwd, sim.side);
    }

    /** The tail below the knot follows an arc of constant curvature (so it keeps its length); the ring stays put. */
    private record ScarfBend(float fwd, float side) implements Hats.Deform {
        static final float KNOT = -0.085f, LENGTH = 0.40f;
        @Override public void apply(Vector3f v) {
            float depth = KNOT - v.y;
            if (depth <= 0) return;
            float theta = (float) Math.sqrt(fwd * fwd + side * side);
            if (theta < 1e-4f) return;
            float bend = theta * depth / LENGTH;
            float out = LENGTH / theta * (1 - (float) Math.cos(bend)), down = LENGTH / theta * (float) Math.sin(bend);
            v.x += side / theta * out;
            v.z += fwd / theta * out;
            v.y += depth - down;
        }
        @Override public boolean smoothNormals() { return true; }
    }
}
