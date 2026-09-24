package tech.gulp.lavavisual.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.config.HudConfig;
import tech.gulp.lavavisual.hud.HudRenderer;

/** Compact mouse-driven settings screen. All rectangles shown as controls have actual actions. */
public final class ClickGuiScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) { }
    private final List<Hit> hits = new ArrayList<>();
    private int page;
    private String selected;
    private int left, top, panelW, panelH, side, bodyX, bodyW;
    private final long opened = System.nanoTime();
    public ClickGuiScreen() { this(0); }
    public ClickGuiScreen(int page) { super(Component.literal("LavaVisual")); this.page = Math.max(0, Math.min(2, page)); }
    private void changed() { LavaVisualClient.save(); }
    private void hit(int x, int y, int w, int h, Runnable action) { hits.add(new Hit(x, y, w, h, action)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int maxWidth) {
        g.text(font, font.plainSubstrByWidth(value, Math.max(1, maxWidth)), x, y, color);
    }
    private void action(GuiGraphicsExtractor g, String text, int x, int y, int w, Runnable callback) {
        UiDraw.round(g, x, y, w, 22, 4, 0xFF24252B);
        text(g, text, x + 7, y + 7, 0xFFE1E1E8, w - 14);
        hit(x, y, w, 22, callback);
    }
    private static String description(String id) {
        return switch (id) {
            case "coordinates" -> "Ваша позиция в мире";
            case "performance" -> "Частота кадров";
            case "stopwatch" -> "Ручной отсчёт времени";
            case "island" -> "Только при событии";
            default -> "Цель под прицелом";
        };
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        panelW = Math.min(650, width - 16); panelH = Math.min(332, height - 16);
        left = (width - panelW) / 2; top = (height - panelH) / 2;
        side = panelW < 430 ? 86 : 108; bodyX = left + side + 16; bodyW = panelW - side - 30;
        g.fill(0, 0, width, height, 0x8507080C);
        UiDraw.round(g, left + 4, top + 5, panelW, panelH, 9, 0x66000000);
        UiDraw.round(g, left, top, panelW, panelH, 8, 0xFF111215);
        g.fill(left + side, top + 12, left + side + 1, top + panelH - 12, 0xFF222329);
        text(g, "LV", left + 13, top + 17, 0xFFFF853A, side - 20);
        text(g, "LavaVisual", left + 13, top + 31, 0xFFECECF1, side - 20);
        String[] tabs = {"HUD", "Визуалы", "Настройки"};
        for (int i = 0; i < tabs.length; i++) {
            int n = i, y = top + 62 + i * 33;
            boolean active = page == i;
            if (active) {
                UiDraw.round(g, left + 7, y - 2, side - 14, 28, 6, 0x222D1707);
                UiDraw.round(g, left + 9, y, side - 18, 24, 5, 0xFFFF853A);
            }
            text(g, tabs[i], left + 17, y + 8, active ? 0xFFFFFFFF : 0xFF85868F, side - 30);
            hit(left + 9, y, side - 18, 24, () -> { page = n; selected = null; });
        }
        text(g, "26.2 / 2.1", left + 12, top + panelH - 19, 0xFF555760, side - 20);
        String heading = selected == null ? tabs[page] : selected.equals("crosshair") ? "Настройки прицела" : HudRenderer.title(selected);
        text(g, heading, bodyX, top + 19, 0xFFEDEDF1, bodyW - 35);
        text(g, "×", left + panelW - 23, top + 17, 0xFF9B9CA5, 15);
        hit(left + panelW - 30, top + 9, 25, 25, this::onClose);
        g.fill(bodyX, top + 39, left + panelW - 14, top + 40, 0xFF23242A);
        if (selected != null) settings(g);
        else if (page == 0) hudCards(g);
        else if (page == 1) visuals(g);
        else general(g);
        if (LavaVisualClient.config().animations) {
            float progress = Math.min(1, (System.nanoTime() - opened) / 200_000_000f);
            g.fill(bodyX, top + 39, bodyX + (int) (bodyW * progress), top + 40, 0x99573621);
        }
    }
    private void hudCards(GuiGraphicsExtractor g) {
        int columns = bodyW >= 270 ? 2 : 1;
        int rows = (HudConfig.IDS.size() + columns - 1) / columns;
        int cellW = (bodyW - (columns - 1) * 10) / columns;
        int cellH = Math.min(66, (panelH - 92) / rows);
        for (int i = 0; i < HudConfig.IDS.size(); i++) {
            String id = HudConfig.IDS.get(i);
            var widget = LavaVisualClient.config().widgets.get(id);
            int x = bodyX + i % columns * (cellW + 10), y = top + 52 + i / columns * cellH;
            UiDraw.round(g, x, y, cellW, cellH - 6, 5, 0xFF17181D);
            text(g, HudRenderer.title(id), x + 8, y + 9, 0xFFE2E2E9, cellW - 65);
            if (cellH >= 42) text(g, description(id), x + 8, y + 25, 0xFF73757F, cellW - 16);
            UiDraw.toggle(g, x + cellW - 48, y + 8, widget.visible);
            text(g, "⋮", x + cellW - 17, y + 7, 0xFF9B9CA5, 12);
            hit(x, y, cellW - 25, cellH - 6, () -> { widget.visible = !widget.visible; changed(); });
            hit(x + cellW - 25, y, 25, cellH - 6, () -> selected = id);
        }
        action(g, "Редактор расположения", bodyX, top + panelH - 31, bodyW,
                () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
    }
    private void visuals(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        int y = top + 55;
        UiDraw.round(g, bodyX, y, bodyW, 56, 5, 0xFF17181D);
        text(g, "Прицел", bodyX + 10, y + 10, 0xFFE2E2E9, bodyW - 70);
        text(g, "Форма, цвет, размер", bodyX + 10, y + 32, 0xFF73757F, bodyW - 20);
        UiDraw.toggle(g, bodyX + bodyW - 51, y + 9, c.crosshairEnabled);
        text(g, "⋮", bodyX + bodyW - 18, y + 8, 0xFF9B9CA5, 12);
        hit(bodyX, y, bodyW - 25, 56, () -> { c.crosshairEnabled = !c.crosshairEnabled; changed(); });
        hit(bodyX + bodyW - 25, y, 25, 56, () -> selected = "crosshair");
    }
    private void general(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        action(g, "Тени панелей: " + (c.shadows ? "да" : "нет"), bodyX, top + 56, bodyW, () -> { c.shadows = !c.shadows; changed(); });
        action(g, "Анимация меню: " + (c.animations ? "да" : "нет"), bodyX, top + 84, bodyW, () -> { c.animations = !c.animations; changed(); });
        action(g, "Выключить все модули", bodyX, top + 120, bodyW, () -> { c.disableAll(); changed(); });
        action(g, "Сбросить расположение", bodyX, top + 148, bodyW, LavaVisualClient::resetLayout);
    }
    private void settings(GuiGraphicsExtractor g) {
        var c = LavaVisualClient.config();
        boolean cross = selected.equals("crosshair");
        HudConfig.Widget widget = cross ? null : c.widgets.get(selected);
        int y = top + 49;
        boolean on = cross ? c.crosshairEnabled : widget.visible;
        action(g, "Включено: " + (on ? "да" : "нет"), bodyX, y, bodyW, () -> {
            if (cross) c.crosshairEnabled = !c.crosshairEnabled; else widget.visible = !widget.visible; changed();
        });
        y += 30;
        text(g, "Цвет", bodyX, y + 4, 0xFF92949E, 42);
        for (int i = 0; i < HudConfig.COLORS.length; i++) {
            int color = i, x = bodyX + 44 + i * 22;
            UiDraw.round(g, x, y, 17, 17, 4, HudConfig.COLORS[i]);
            if ((cross ? c.crosshairColor : widget.color) == i) g.fill(x + 4, y + 20, x + 13, y + 21, 0xFFFFFFFF);
            hit(x, y, 18, 21, () -> { if (cross) c.crosshairColor = color; else widget.color = color; changed(); });
        }
        y += 32;
        double scale = cross ? c.crosshairScale : widget.scale;
        adjust(g, "Размер", scale, y, () -> {
            if (cross) c.crosshairScale -= 0.1; else widget.scale -= 0.1; changed();
        }, () -> { if (cross) c.crosshairScale += 0.1; else widget.scale += 0.1; changed(); });
        y += 30;
        double opacity = cross ? c.crosshairOpacity : widget.opacity;
        adjust(g, cross ? "Непрозрачность" : "Плотность фона", opacity, y, () -> {
            if (cross) c.crosshairOpacity -= 0.1; else widget.opacity -= 0.1; changed();
        }, () -> { if (cross) c.crosshairOpacity += 0.1; else widget.opacity += 0.1; changed(); });
        y += 30;
        if (cross) action(g, "Форма: " + new String[]{"", "точка", "плюс", "квадрат"}[c.crosshairShape], bodyX, y, bodyW,
                () -> { c.crosshairShape = c.crosshairShape % 3 + 1; changed(); });
        else action(g, "Переместить на экране", bodyX, y, bodyW, () -> minecraft.gui.setScreen(new HudEditorScreen(this, selected)));
        y += 28;
        if (selected.equals("stopwatch") && y + 24 < top + panelH - 32) {
            action(g, "Пуск / пауза", bodyX, y, bodyW / 2 - 4, LavaVisualClient.STATE::toggleTimer);
            action(g, "Сброс", bodyX + bodyW / 2 + 4, y, bodyW / 2 - 4, LavaVisualClient.STATE::reset);
        }
        action(g, "‹ Назад", bodyX, top + panelH - 30, bodyW, () -> selected = null);
    }
    private void adjust(GuiGraphicsExtractor g, String name, double value, int y, Runnable minus, Runnable plus) {
        text(g, name + " " + Math.round(value * 100) + "%", bodyX, y + 7, 0xFFA1A3AE, bodyW - 70);
        action(g, "−", bodyX + bodyW - 60, y, 26, minus);
        action(g, "+", bodyX + bodyW - 28, y, 26, plus);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        for (Hit hit : hits) if (event.x() >= hit.x && event.x() < hit.x + hit.w && event.y() >= hit.y && event.y() < hit.y + hit.h) {
            hit.action.run(); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void onClose() { LavaVisualClient.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
