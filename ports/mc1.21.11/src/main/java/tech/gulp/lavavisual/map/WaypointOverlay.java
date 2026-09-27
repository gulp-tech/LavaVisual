package tech.gulp.lavavisual.map;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.ui.UiDraw;
import tech.gulp.lavavisual.ui.UiFont;

/** Waypoint beams (world pass) and "name · distance" labels projected with the frame's own camera matrices. */
public final class WaypointOverlay {
    private WaypointOverlay() { }
    public record Beam(Vec3 base, int color) { }
    private record Label(float ndcX, float ndcY, boolean front, String name, String distance, int color) { }
    private static volatile List<Label> labels = List.of();

    /** Runs during level extraction; returns beams for the cosmetics pass and stores labels for the HUD. */
    public static List<Beam> extract(Minecraft mc, CameraRenderState camera, float partial) {
        var c = LavaVisualClient.config();
        if (mc.level == null || mc.player == null || camera == null || camera.pos == null || (!c.waypointBeams && !c.waypointLabels)) { labels = List.of(); return List.of(); }
        List<Waypoints.Point> points = Waypoints.here(mc);
        if (points.isEmpty()) { labels = List.of(); return List.of(); }
        Vec3 cam = camera.pos, feet = mc.player.getPosition(partial);
        List<Beam> beams = new ArrayList<>();
        List<Label> out = new ArrayList<>();
        Quaternionf inverse = camera.orientation == null ? null : new Quaternionf(camera.orientation).conjugate();
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        boolean perspective = camera.projectionMatrix != null && Math.abs(camera.projectionMatrix.m23() + 1) < 0.2f;
        for (Waypoints.Point p : points) {
            if (!p.visible) continue;
            double wx = p.x + 0.5, wy = p.y, wz = p.z + 0.5;
            if (c.waypointBeams) beams.add(new Beam(new Vec3(wx, wy, wz), p.color));
            if (!c.waypointLabels) continue;
            double distance = Math.sqrt((wx - feet.x) * (wx - feet.x) + (wy - feet.y) * (wy - feet.y) + (wz - feet.z) * (wz - feet.z));
            double ax = wx - cam.x, ay = wy + 1.3 - cam.y, az = wz - cam.z;
            double length = Math.sqrt(ax * ax + ay * ay + az * az);
            if (length > 512) { ax *= 512 / length; ay *= 512 / length; az *= 512 / length; }
            // View rotation from the camera orientation (valid during extraction); projection from the frame when it is a real perspective matrix.
            Vector3f view = new Vector3f((float) ax, (float) ay, (float) az);
            if (inverse != null) view.rotate(inverse);
            else if (camera.viewRotationMatrix != null) camera.viewRotationMatrix.transformDirection(view);
            else continue;
            boolean front = view.z < -0.01f;
            float nx, ny;
            if (perspective) {
                Vector4f clip = new Vector4f(view, 1f);
                camera.projectionMatrix.transform(clip);
                float w = Math.max(0.01f, Math.abs(clip.w));
                nx = clip.x / w; ny = clip.y / w;
            } else {
                float depth = Math.max(0.01f, Math.abs(view.z));
                nx = (float) (view.x / depth / tanHalf / aspect); ny = (float) (view.y / depth / tanHalf);
            }
            out.add(new Label(nx, ny, front, p.name, Waypoints.distance(distance), p.color));
        }
        labels = List.copyOf(out);
        return beams;
    }
    public static void clear() { labels = List.of(); }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc) {
        List<Label> list = labels;
        if (list.isEmpty() || mc.player == null) return;
        Font font = mc.font;
        int width = g.guiWidth(), height = g.guiHeight();
        for (Label label : list) {
            float sx = (label.ndcX + 1) / 2 * width, sy = (1 - label.ndcY) / 2 * height;
            if (label.front && sx >= 10 && sx <= width - 10 && sy >= 14 && sy <= height - 10) pill(g, font, label, (int) sx, (int) sy);
            else edge(g, font, label, width, height);
        }
    }
    private static void pill(GuiGraphicsExtractor g, Font font, Label label, int x, int y) {
        String name = label.name, distance = label.distance;
        int nameW = UiFont.width(g, font, name, UiFont.Face.BOLD), distW = UiFont.width(g, font, distance, UiFont.Face.SMALL);
        int w = 16 + nameW + 6 + distW + 7, h = 15, left = x - w / 2, top = y - h - 5;
        UiDraw.round(g, left, top, w, h, 5, 0xC80E1014);
        UiDraw.round(g, left + 5, top + 5, 5, 5, 2, 0xFF000000 | label.color);
        UiFont.text(g, font, name, left + 14, top + 3, 0xFFF1F3F7, nameW + 2, UiFont.Face.BOLD);
        UiFont.text(g, font, distance, left + 14 + nameW + 6, top + 4, 0xFFAAB2BF, distW + 2, UiFont.Face.SMALL);
        for (int i = 0; i < 3; i++) g.fill(x - 3 + i, top + h + i, x + 3 - i, top + h + i + 1, 0xC80E1014);
    }
    /** Off-screen or behind: an arrow on the screen border pointing towards the waypoint. */
    private static void edge(GuiGraphicsExtractor g, Font font, Label label, int width, int height) {
        double dx = label.ndcX * width / 2.0, dy = -label.ndcY * height / 2.0;
        if (!label.front && Math.abs(dx) < 1 && Math.abs(dy) < 1) dy = 1;
        if (!label.front && Math.abs(dy) < Math.abs(dx) * 0.2) dy = Math.abs(dx) * 0.2;
        double halfW = width / 2.0 - 26, halfH = height / 2.0 - 22;
        double t = Math.min(halfW / Math.max(1e-6, Math.abs(dx)), halfH / Math.max(1e-6, Math.abs(dy)));
        int x = (int) (width / 2.0 + dx * t), y = (int) (height / 2.0 + dy * t);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate((float) Math.atan2(dy, dx));
        for (int i = 0; i < 7; i++) g.fill(-4 + i - 1, -(7 - i) / 2 - 1 - 1, -4 + i + 1, (7 - i) / 2 + 1 + 1, 0xB0000000);
        for (int i = 0; i < 7; i++) g.fill(-4 + i, -(7 - i) / 2 - 1, -4 + i + 1, (7 - i) / 2 + 1, 0xFF000000 | label.color);
        g.pose().popMatrix();
        String text = label.distance;
        int tw = UiFont.width(g, font, text, UiFont.Face.SMALL);
        double len = Math.max(1e-6, Math.hypot(dx, dy));
        int tx = (int) (x - dx / len * 16) - tw / 2, ty = (int) (y - dy / len * 12) - 4;
        UiDraw.round(g, tx - 3, ty - 2, tw + 6, 11, 4, 0xB00E1014);
        UiFont.text(g, font, text, tx, ty, 0xFFE8EAF0, tw + 2, UiFont.Face.SMALL);
    }
}
