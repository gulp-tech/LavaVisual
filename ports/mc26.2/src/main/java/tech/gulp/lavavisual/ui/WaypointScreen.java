package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.map.Waypoints;

/** Add or edit a waypoint: name, X, height (Y) and Z, prefilled with your position. */
public final class WaypointScreen extends Screen {
    private static final int[] COLORS = {0xFF5A36, 0xFFC233, 0x85F56A, 0x36C8FF, 0x4C6BFF, 0xB45CFF, 0xFF5C9A, 0xFFFFFF};
    /** Digits and a leading minus only (also filters paste). */
    private static final class NumberBox extends EditBox {
        NumberBox(Font font, int x, int y, int w, int h, Component label) { super(font, x, y, w, h, label); setMaxLength(9); }
        @Override public void insertText(String text) {
            StringBuilder clean = new StringBuilder();
            for (char ch : text.toCharArray()) if (Character.isDigit(ch) || ch == '-') clean.append(ch);
            if (!clean.isEmpty()) super.insertText(clean.toString());
        }
    }
    private record Swatch(int x, int y, int w, int color) { }
    private final Screen parent;
    private final Waypoints.Point editing;
    private final List<Swatch> swatches = new ArrayList<>();
    private EditBox name, x, y, z;
    private int color, panelX, panelY, panelW, panelH;
    private String error;
    private boolean prepared;
    public WaypointScreen(Screen parent, Waypoints.Point editing) {
        super(UiFont.component(editing == null ? "Новая метка" : "Изменить метку"));
        this.parent = parent; this.editing = editing;
    }
    @Override protected void init() {
        panelW = Math.min(300, width - 20); panelH = Math.min(214, height - 12);
        panelX = (width - panelW) / 2; panelY = (height - panelH) / 2;
        String n = name == null ? null : name.getValue(), vx = x == null ? null : x.getValue(), vy = y == null ? null : y.getValue(), vz = z == null ? null : z.getValue();
        int inner = panelW - 24, third = (inner - 16) / 3, top = panelY + 44;
        name = new EditBox(font, panelX + 12, top + 12, inner, 18, Component.literal("Название"));
        name.setMaxLength(32);
        x = new NumberBox(font, panelX + 12, top + 50, third, 18, Component.literal("X"));
        y = new NumberBox(font, panelX + 12 + third + 8, top + 50, third, 18, Component.literal("Y"));
        z = new NumberBox(font, panelX + 12 + (third + 8) * 2, top + 50, third, 18, Component.literal("Z"));
        if (!prepared) {
            prepared = true;
            var player = minecraft.player;
            if (editing != null) {
                n = editing.name; vx = "" + editing.x; vy = "" + editing.y; vz = "" + editing.z; color = editing.color;
            } else {
                n = minecraft.level == null ? "Метка" : Waypoints.nextName(minecraft);
                vx = player == null ? "0" : "" + player.blockPosition().getX();
                vy = player == null ? "64" : "" + player.blockPosition().getY();
                vz = player == null ? "0" : "" + player.blockPosition().getZ();
                var all = minecraft.level == null ? List.<Waypoints.Point>of() : Waypoints.all(minecraft);
                color = COLORS[all.size() % COLORS.length];
                if (LavaVisualClient.config().customColor("waypoint")) color = LavaVisualClient.config().color("waypoint") & 0xFFFFFF;
            }
        }
        name.setValue(n); x.setValue(vx); y.setValue(vy); z.setValue(vz);
        name.setHint(UiFont.component("Название метки"));
        addRenderableWidget(name); addRenderableWidget(x); addRenderableWidget(y); addRenderableWidget(z);
        int bw = (inner - 16) / 3, by = panelY + panelH - 32;
        addRenderableWidget(Button.builder(UiFont.component("Сохранить"), b -> save()).pos(panelX + 12, by).size(bw, 20).build());
        addRenderableWidget(Button.builder(UiFont.component("Моя позиция"), b -> here()).pos(panelX + 12 + bw + 8, by).size(bw, 20).build());
        addRenderableWidget(Button.builder(UiFont.component("Отмена"), b -> onClose()).pos(panelX + 12 + (bw + 8) * 2, by).size(bw, 20).build());
        setInitialFocus(name);
    }
    private void here() {
        var player = minecraft.player;
        if (player == null) return;
        x.setValue("" + player.blockPosition().getX()); y.setValue("" + player.blockPosition().getY()); z.setValue("" + player.blockPosition().getZ());
    }
    private static Integer parse(String text) {
        try { return Integer.parseInt(text.trim()); } catch (Exception e) { return null; }
    }
    private void save() {
        Integer vx = parse(x.getValue()), vy = parse(y.getValue()), vz = parse(z.getValue());
        if (vx == null || vy == null || vz == null) { error = "Введите целые числа в X, Y и Z"; return; }
        if (minecraft.level == null) { error = "Метки ставятся в мире"; return; }
        if (Math.abs(vx) > 30_000_000 || Math.abs(vz) > 30_000_000 || Math.abs(vy) > 4096) { error = "Координаты вне мира"; return; }
        String label = name.getValue().isBlank() ? Waypoints.nextName(minecraft) : name.getValue().trim();
        if (editing != null) {
            editing.name = label; editing.x = vx; editing.y = vy; editing.z = vz; editing.color = color;
            Waypoints.save();
        } else if (!Waypoints.add(minecraft, new Waypoints.Point(label, vx, vy, vz, color, Waypoints.dimension(minecraft)))) {
            error = "Не больше " + Waypoints.LIMIT + " меток в одном мире"; return;
        }
        onClose();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.extractBackground(g, mouseX, mouseY, delta);
        else g.fill(0, 0, width, height, 0x66000000);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var c = LavaVisualClient.config();
        int accent = c.color("menu");
        UiDraw.round(g, panelX - 2, panelY + 2, panelW + 4, panelH + 4, 12, 0x40000000);
        UiDraw.round(g, panelX, panelY, panelW, panelH, 10, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.96));
        UiDraw.round(g, panelX + 12, panelY + 12, 22, 22, 7, accent);
        UiFont.icon(g, font, Icons.MAP_PINNED, panelX + 18, panelY + 18, 0xFF11181A);
        UiFont.text(g, font, editing == null ? "Новая метка" : "Изменить метку", panelX + 42, panelY + 12, 0xFFF1F4F8, panelW - 54, UiFont.Face.BOLD);
        UiFont.text(g, font, "Мир: " + (minecraft.level == null ? "—" : Waypoints.dimension(minecraft).replace("minecraft:", "")), panelX + 42, panelY + 25, 0xFF8C93A1, panelW - 54, UiFont.Face.SMALL);
        int top = panelY + 44, inner = panelW - 24, third = (inner - 16) / 3;
        UiFont.text(g, font, "Название", panelX + 12, top + 1, 0xFFB8C0CD, inner, UiFont.Face.SMALL);
        String[] labels = {"X", "Y · высота", "Z"};
        for (int i = 0; i < 3; i++) UiFont.text(g, font, labels[i], panelX + 12 + i * (third + 8), top + 39, 0xFFB8C0CD, third, UiFont.Face.SMALL);
        UiFont.text(g, font, "Цвет", panelX + 12, top + 77, 0xFFB8C0CD, inner, UiFont.Face.SMALL);
        swatches.clear();
        int sw = Math.min(24, (inner - 7 * 6) / 8);
        for (int i = 0; i < COLORS.length; i++) {
            int sx = panelX + 12 + i * (sw + 6), sy = top + 89;
            if (COLORS[i] == color) UiDraw.round(g, sx - 2, sy - 2, sw + 4, 18, 5, 0xFFFFFFFF);
            UiDraw.round(g, sx, sy, sw, 14, 4, 0xFF000000 | COLORS[i]);
            swatches.add(new Swatch(sx, sy, sw, COLORS[i]));
        }
        var player = minecraft.player;
        Integer vx = parse(x.getValue()), vy = parse(y.getValue()), vz = parse(z.getValue());
        String info;
        if (error != null) info = error;
        else if (player != null && vx != null && vy != null && vz != null) {
            double dx = vx + 0.5 - player.getX(), dy = vy - player.getY(), dz = vz + 0.5 - player.getZ();
            info = "До метки: " + Waypoints.distance(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " · Enter — сохранить";
        } else info = "Enter — сохранить, Esc — отмена";
        UiFont.text(g, font, info, panelX + 12, top + 112, error != null ? 0xFFFF7A6B : 0xFF8C93A1, inner, UiFont.Face.SMALL);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) for (Swatch s : swatches)
            if (event.x() >= s.x && event.x() < s.x + s.w && event.y() >= s.y && event.y() < s.y + 14) { color = s.color; return true; }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
