package tech.gulp.lavavisual.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.ui.UiDraw;
import tech.gulp.lavavisual.ui.UiFont;

public final class HudRenderer {
    public static int baseWidth(String id) { return id.equals("target") ? 180 : id.equals("keys") ? 84 : id.equals("armor") ? 88 : 156; }
    public static int baseHeight(String id) { return id.equals("target") ? 60 : id.equals("keys") ? 56 : id.equals("armor") ? 24 : 30; }
    public static int width(String id, HudConfig.Widget w) { return (int) Math.ceil(baseWidth(id) * w.scale); }
    public static int height(String id, HudConfig.Widget w) { return (int) Math.ceil(baseHeight(id) * w.scale); }
    public static int x(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.x * Math.max(0, screen - width(id, w))); }
    public static int y(String id, HudConfig.Widget w, int screen) { return (int) Math.round(w.y * Math.max(0, screen - height(id, w))); }
    public static String title(String id) {
        return switch (id) {
            case "coordinates" -> "Координаты";
            case "performance" -> "FPS";
            case "target" -> "Target HUD";
            case "keys" -> "Клавиши";
            case "armor" -> "Броня";
            case "totems" -> "Тотемы";
            default -> "HUD";
        };
    }
    private static double targetAnim, healthShown, healthGhost;
    private static long lastNs;
    private static TargetSnapshot lastTarget;
    private static String lastName = "";
    private record HudParticle(double x, double y, double vx, double vy, long born, int color) { }
    private static final java.util.ArrayList<HudParticle> HUD_PARTICLES = new java.util.ArrayList<>();
    private static float lastHealth = -1;
    public static void draw(GuiGraphicsExtractor g, boolean edit, String selected) {
        HudConfig c = LavaVisualClient.config();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNs) / 1e9); lastNs = now;
        Minecraft mc = Minecraft.getInstance();
        if (!edit && (mc.player == null || mc.gui.screen() instanceof tech.gulp.lavavisual.ui.HudEditorScreen)) return;
        SessionState state = LavaVisualClient.STATE;
        for (String id : HudConfig.IDS) {
            HudConfig.Widget w = c.widgets.get(id);
            if (!w.visible && !edit) continue;
            TargetSnapshot target = TargetSnapshot.current;
            double fade = 1, scaleAnim = 1;
            if (id.equals("target") && !edit) {
                if (target != null) lastTarget = target; else target = lastTarget;
                double goal = TargetSnapshot.current == null ? 0 : 1;
                targetAnim += (goal - targetAnim) * (c.animations ? Math.min(1, dt * 9) : 1);
                if (goal == 0 && (targetAnim <= 0.03 || target == null)) { targetAnim = 0; lastTarget = null; continue; }
                fade = Math.clamp(targetAnim, 0, 1);
                scaleAnim = 0.72 + 0.28 * (1 - Math.pow(1 - fade, 3));
            }
            int x = x(id, w, g.guiWidth()), y = y(id, w, g.guiHeight());
            int bw = baseWidth(id), bh = baseHeight(id), accent = c.accent();
            g.pose().pushMatrix();
            try {
                g.pose().translate(x, y);
                g.pose().scale((float) w.scale);
                if (scaleAnim != 1) {
                    g.pose().translate(bw / 2f, bh / 2f);
                    g.pose().scale((float) scaleAnim);
                    g.pose().translate(-bw / 2f, -bh / 2f);
                }
                if (id.equals("target")) UiDraw.round(g, -2, -2, bw + 4, bh + 4, 7, UiDraw.alpha(accent, 0.14 * fade));
                if (c.shadows) UiDraw.round(g, 1, 2, bw, bh, 5, UiDraw.alpha(0x000000, w.opacity * 0.25 * fade));
                UiDraw.round(g, 0, 0, bw, bh, 5, UiDraw.alpha(0x111216, w.opacity * fade));
                if (id.equals(selected)) g.fill(5, bh - 1, bw - 5, bh, accent);
                if (id.equals("target")) {
                    if (target == null) target = new TargetSnapshot("Предпросмотр", 16, 20, 10, 3.2,
                            mc.player == null ? null : mc.player.getSkin().body().texturePath(), null, 0);
                    g.fillGradient(4, 1, bw - 4, 20, UiDraw.alpha(accent, 0.10 * fade), UiDraw.alpha(accent, 0));
                    UiDraw.round(g, 7, 7, 36, 36, 6, UiDraw.alpha(accent, 0.30 * fade));
                    UiDraw.round(g, 8, 8, 34, 34, 5, UiDraw.alpha(0x1B1E25, fade));
                    int white = UiDraw.alpha(0xFFFFFF, fade);
                    if (target.skin() != null) {
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, target.skin(), 9, 9, 8, 8, 32, 32, 8, 8, 64, 64, white);
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, target.skin(), 9, 9, 40, 8, 32, 32, 8, 8, 64, 64, white);
                    } else if (fade > 0.6 && target.entity() != null && !target.entity().isRemoved()) {
                        try {
                            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, 9, 9, 32, 32, 0, 25f, 25f, 14f, target.entity());
                        } catch (RuntimeException ignored) {
                            UiFont.text(g, mc.font, target.name().isEmpty() ? "?" : target.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT), 21, 21, UiDraw.alpha(accent, fade), 26);
                        }
                    } else UiFont.text(g, mc.font, target.name().isEmpty() ? "?" : target.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT), 21, 21, UiDraw.alpha(accent, fade), 26);
                    var living = target.entity();
                    if (living != null && living.hurtTime > 0)
                        UiDraw.round(g, 9, 9, 32, 32, 3, UiDraw.alpha(0xFF2A2A, 0.5 * living.hurtTime / 10.0 * fade));
                    if (lastHealth >= 0 && target.health() < lastHealth - 0.01 && target.name().equals(lastName) && HUD_PARTICLES.size() < 60) {
                        long born = System.nanoTime();
                        for (int i = 0; i < 12; i++) {
                            double angle = Math.random() * Math.PI * 2, speed = 40 + Math.random() * 70;
                            HUD_PARTICLES.add(new HudParticle(25, 25, Math.cos(angle) * speed, Math.sin(angle) * speed - 40, born, i % 3 == 0 ? 0xFFFFFF : accent & 0xFFFFFF));
                        }
                    }
                    lastHealth = target.health();
                    long particleNow = System.nanoTime();
                    HUD_PARTICLES.removeIf(pt -> particleNow - pt.born() > 700_000_000L);
                    for (HudParticle pt : HUD_PARTICLES) {
                        double age = (particleNow - pt.born()) / 1e9, life = 1 - age / 0.7;
                        int px = (int) (pt.x() + pt.vx() * age), py = (int) (pt.y() + pt.vy() * age + 160 * age * age);
                        UiDraw.round(g, px - 1, py - 1, 3, 3, 1, UiDraw.alpha(pt.color(), life * fade));
                    }
                    if (living != null && fade > 0.7) {
                        net.minecraft.world.item.ItemStack[] gear = {
                                living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                                living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS), living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),
                                living.getMainHandItem() };
                        g.pose().pushMatrix();
                        g.pose().translate(bw - 8 - 5 * 11, 34);
                        g.pose().scale(0.625f);
                        for (int i = 0; i < gear.length; i++) if (!gear[i].isEmpty()) g.item(gear[i], i * 18, 0);
                        g.pose().popMatrix();
                    }
                    int combo = tech.gulp.lavavisual.effects.WorldCosmetics.combo();
                    if (combo >= 2) {
                        UiDraw.round(g, bw - 30, 6, 24, 12, 6, UiDraw.alpha(accent, 0.85 * fade));
                        UiFont.text(g, mc.font, "x" + combo, bw - 26, 8, UiDraw.alpha(0x111216, fade), 20);
                    }
                    UiFont.text(g, mc.font, target.name(), 49, 8, UiDraw.alpha(accent, fade), bw - 57);
                    UiFont.text(g, mc.font, String.format(java.util.Locale.ROOT, "HP %.1f / %.1f", target.health(), target.maximum()), 49, 22, UiDraw.alpha(0xE8E8EB, fade), bw - 57);
                    UiFont.text(g, mc.font, String.format(java.util.Locale.ROOT, "Броня %d · %.1f м", target.armor(), target.distance()), 49, 36, UiDraw.alpha(0x9698A3, fade), living != null ? bw - 57 - 60 : bw - 57);
                    double ratio = target.maximum() > 0 ? Math.max(0, Math.min(1, target.health() / target.maximum())) : 0;
                    if (!target.name().equals(lastName)) { lastName = target.name(); healthShown = healthGhost = ratio; }
                    healthShown += (ratio - healthShown) * Math.min(1, dt * 12);
                    healthGhost = ratio > healthGhost ? ratio : Math.max(healthShown, healthGhost - dt * 0.5);
                    int barW = bw - 18;
                    UiDraw.round(g, 9, 50, barW, 5, 2, UiDraw.alpha(0x2A2C33, fade));
                    if (healthGhost > 0) UiDraw.round(g, 9, 50, Math.max(2, (int) (barW * healthGhost)), 5, 2, UiDraw.alpha(0xFFFFFF, 0.35 * fade));
                    if (healthShown > 0) {
                        int fill = Math.max(2, (int) (barW * healthShown));
                        UiDraw.round(g, 9, 50, fill, 5, 2, UiDraw.alpha(accent, fade));
                        g.fillGradient(10, 50, 9 + fill - 1, 52, UiDraw.alpha(0xFFFFFF, 0.28 * fade), UiDraw.alpha(0xFFFFFF, 0));
                    }
                } else if (id.equals("keys")) {
                    keys(g, mc, fade, accent, edit && !w.visible);
                } else if (id.equals("armor")) {
                    armor(g, mc, fade, edit && !w.visible);
                } else {
                    String value = switch (id) {
                        case "coordinates" -> state.coordinates;
                        case "performance" -> state.performance;
                        case "totems" -> totems(mc);
                        default -> "";
                    };
                    UiFont.text(g, mc.font, title(id) + (edit && !w.visible ? " · выкл" : ""), 8, 4, UiDraw.alpha(accent, fade), bw - 16);
                    UiFont.text(g, mc.font, value, 8, 17, UiDraw.alpha(0xEAEAF0, fade), bw - 16);
                }
            } finally { g.pose().popMatrix(); }
        }
    }
    private static String totems(Minecraft mc) {
        if (mc.player == null) return "0 шт.";
        var inventory = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        if (inventory.getContainerSize() <= 36 && mc.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) count += mc.player.getOffhandItem().getCount();
        return count + " шт.";
    }
    private static void keys(GuiGraphicsExtractor g, Minecraft mc, double fade, int accent, boolean off) {
        var o = mc.options;
        boolean l = o.keyAttack.isDown(), r = o.keyUse.isDown();
        cell(g, mc.font, 8, 8, 32, 14, l, "ЛКМ", fade, accent);
        cell(g, mc.font, 44, 8, 32, 14, r, "ПКМ", fade, accent);
        cell(g, mc.font, 26, 26, 14, 14, o.keyUp.isDown(), "W", fade, accent);
        cell(g, mc.font, 8, 42, 14, 14, o.keyLeft.isDown(), "A", fade, accent);
        cell(g, mc.font, 26, 42, 14, 14, o.keyDown.isDown(), "S", fade, accent);
        cell(g, mc.font, 44, 42, 14, 14, o.keyRight.isDown(), "D", fade, accent);
        UiFont.text(g, mc.font, "CPS " + tech.gulp.lavavisual.effects.WorldCosmetics.clicksPerSecond(), 62, 30, UiDraw.alpha(0xFFEAEAF0, fade), 20);
        if (off) UiFont.text(g, mc.font, "выкл", 62, 44, UiDraw.alpha(0xFF9698A3, fade), 22);
    }
    private static void cell(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, boolean on, String label, double fade, int accent) {
        UiDraw.round(g, x, y, w, h, 3, on ? UiDraw.alpha(accent, 0.85 * fade) : UiDraw.alpha(0x23262D, 0.9 * fade));
        UiFont.text(g, font, label, x + (w - 14) / 2 + 3, y + 4, on ? UiDraw.alpha(0x101418, fade) : UiDraw.alpha(0xFFB8C0CD, fade), 14);
    }
    private static void armor(GuiGraphicsExtractor g, Minecraft mc, double fade, boolean off) {
        var player = mc.player;
        net.minecraft.world.item.ItemStack[] stacks = new net.minecraft.world.item.ItemStack[4];
        if (player != null) {
            var slots = new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET };
            for (int i = 0; i < 4; i++) stacks[i] = player.getItemBySlot(slots[i]);
        }
        for (int i = 0; i < 4; i++) {
            int x = 8 + i * 20;
            UiDraw.round(g, x, 4, 16, 16, 3, UiDraw.alpha(0x23262D, 0.9 * fade));
            var stack = stacks[i];
            if (stack != null && !stack.isEmpty()) {
                double ratio = 1 - (double) stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
                int color = ratio > 0.5 ? 0xFF7ADB6A : ratio > 0.25 ? 0xFFE0C14C : 0xFFE06A4C;
                int h = Math.max(2, (int) Math.round(12 * ratio));
                UiDraw.round(g, x + 3, 6 + (12 - h), 10, h, 2, UiDraw.alpha(color, 0.9 * fade));
            }
        }
        if (off) UiFont.text(g, mc.font, "выкл", 8, 22, UiDraw.alpha(0xFF9698A3, fade), 40);
    }
    public static void crosshair(GuiGraphicsExtractor g) {
        HudConfig c = LavaVisualClient.config();
        int color = UiDraw.alpha(c.accent(), c.crosshairOpacity);
        g.pose().pushMatrix();
        try {
            g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f);
            g.pose().scale((float) c.crosshairScale);
            if (c.crosshairShape == 1) g.fill(-1, -1, 1, 1, color);
            else if (c.crosshairShape == 2) {
                g.fill(-6, 0, -2, 1, color); g.fill(3, 0, 7, 1, color);
                g.fill(0, -6, 1, -2, color); g.fill(0, 3, 1, 7, color);
            } else {
                g.fill(-4, -4, 5, -3, color); g.fill(-4, 4, 5, 5, color);
                g.fill(-4, -3, -3, 4, color); g.fill(4, -3, 5, 4, color);
            }
        } finally { g.pose().popMatrix(); }
    }
}
