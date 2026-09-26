package tech.gulp.lavavisual.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import tech.gulp.lavavisual.LavaVisualClient;
import tech.gulp.lavavisual.audio.Covers;
import tech.gulp.lavavisual.audio.MusicPlayer;
import tech.gulp.lavavisual.effects.CustomSounds;
import tech.gulp.lavavisual.hud.HudRenderer;
import tech.gulp.lavavisual.input.Binds;

/**
 * Music player (M by default): cover or spinning disc, title, a progress bar you can click or drag to seek, previous /
 * back 10 s / play-pause / forward 10 s / next, shuffle and repeat, volume, the track list and the folder buttons.
 * Keys: Space pause, arrows seek and volume, N / P next / previous. .ogg files dropped on the window go to the folder.
 */
public final class MusicScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) { }
    private static final int ROW = 18;
    private final Screen parent;
    private final UiButtons buttons = new UiButtons();
    private final List<Hit> hits = new ArrayList<>();
    private boolean seeking, volumeDrag;
    private double seekPreview;
    private int scroll, barX, barY, barW, volX, volY, volW, listX, listY, listW, listH;
    private float angle;
    private long last;

    public MusicScreen(Screen parent) {
        super(Component.literal("Музыка"));
        this.parent = parent;
        CustomSounds.ensureFolders();
        MusicPlayer.rescan(CustomSounds.musicDir());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var c = LavaVisualClient.config();
        int ac = c.color("menu"), ac2 = c.color2("menu");
        buttons.clear();
        hits.clear();
        int pw = Math.min(430, width - 16), ph = Math.min(310, height - 16), px = (width - pw) / 2, py = (height - ph) / 2;
        UiDraw.shadow(g, px, py, pw, ph, 12, 6, 3, 0.35);
        UiDraw.round(g, px, py, pw, ph, 12, UiDraw.alpha(c.color("menu_bg") & 0xFFFFFF, 0.96));
        UiDraw.roundH(g, px + 12, py, pw - 24, 1, 0, UiDraw.alpha(ac, 0.9), UiDraw.alpha(ac2, 0.9));
        UiFont.icon(g, font, Icons.MUSIC, px + 12, py + 10, ac);
        UiFont.text(g, font, "Музыка", px + 27, py + 11, 0xFFE8EAF0, 120, UiFont.Face.BOLD);
        boolean overClose = mx >= px + pw - 26 && mx < px + pw - 8 && my >= py + 7 && my < py + 25;
        UiFont.icon(g, font, Icons.X, px + pw - 22, py + 10, overClose ? ac : 0xFF9AA3B2);
        hits.add(new Hit(px + pw - 26, py + 7, 18, 18, this::onClose));

        var track = MusicPlayer.current();
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (MusicPlayer.playing()) angle = (float) ((angle + dt * 90) % 360);
        int cs = 72, cx = px + 14, cy = py + 32;
        var cover = Covers.get(minecraft, track);
        if (cover != null) {
            UiDraw.round(g, cx - 1, cy - 1, cs + 2, cs + 2, 8, 0x70000000);
            g.blit(RenderPipelines.GUI_TEXTURED, cover, cx, cy, 0f, 0f, cs, cs, Covers.SIZE, Covers.SIZE, Covers.SIZE, Covers.SIZE, 0xFFFFFFFF);
        } else HudRenderer.disc(g, cx + cs / 2.0, cy + cs / 2.0, cs / 2.0, angle, ac, ac2);

        int ix = cx + cs + 14, iw = px + pw - 14 - ix;
        var tracks = MusicPlayer.tracks();
        String title = track == null ? (tracks.isEmpty() ? "Треков нет" : "Выберите трек") : track.shown();
        UiFont.text(g, font, title, ix, cy + 2, 0xFFF2F4F8, iw, UiFont.Face.BOLD);
        String sub = track == null ? (tracks.isEmpty() ? "Положите .ogg в папку music или перетащите сюда" : "Нажмите на трек в списке")
                : (track.artist().isBlank() ? "" : track.artist() + " · ") + (MusicPlayer.paused() ? "пауза" : MusicPlayer.playing() ? "играет" : "стоп");
        UiFont.text(g, font, sub, ix, cy + 15, 0xFF9AA3B2, iw);
        barX = ix; barY = cy + 32; barW = iw;
        double len = MusicPlayer.duration(), pos = seeking ? seekPreview : MusicPlayer.position();
        double progress = len > 0 ? Math.clamp(pos / len, 0, 1) : 0;
        UiDraw.round(g, barX, barY, barW, 4, 2, 0xFF353A43);
        int filled = (int) Math.round(barW * progress);
        if (filled > 1) UiDraw.roundH(g, barX, barY, filled, 4, 2, ac, 0xFF000000 | UiDraw.mix(ac, ac2, progress));
        boolean overBar = my >= barY - 5 && my < barY + 9 && mx >= barX - 4 && mx <= barX + barW + 4;
        if (overBar || seeking) UiDraw.circle(g, barX + filled, barY + 2, 6, UiDraw.alpha(ac, 0.3));
        UiDraw.circle(g, barX + filled, barY + 2, 3.5, 0xFFF2F5FA);
        String a = MusicPlayer.time(pos), b = MusicPlayer.time(len);
        UiFont.text(g, font, a, barX, barY + 9, 0xFFB8C0CD, 50, UiFont.Face.SMALL);
        UiFont.text(g, font, b, barX + barW - UiFont.width(g, font, b, UiFont.Face.SMALL), barY + 9, 0xFFB8C0CD, 50, UiFont.Face.SMALL);

        // Controls: shuffle, previous, -10 s, play / pause, +10 s, next, repeat.
        int by = cy + 58, mid = ix + iw / 2;
        round(g, mx, my, mid - 96, by, 9, Icons.SHUFFLE, c.musicShuffle ? ac : 0xFF8C95A4, () -> { c.musicShuffle = !c.musicShuffle; LavaVisualClient.save(); });
        round(g, mx, my, mid - 66, by, 10, Icons.SKIP_BACK, 0xFFE8EAF0, MusicPlayer::previous);
        round(g, mx, my, mid - 38, by, 10, Icons.REWIND, 0xFFE8EAF0, () -> MusicPlayer.skip(-10));
        boolean on = MusicPlayer.playing();
        boolean overPlay = Math.hypot(mx - mid, my - by) <= 14;
        UiDraw.circle(g, mid, by, 14, overPlay ? 0xFF000000 | UiDraw.mix(ac, 0xFFFFFF, 0.15) : ac);
        UiFont.icon(g, font, on ? Icons.PAUSE : Icons.PLAY, mid - 5, by - 5, 0xFFFFFFFF);
        hits.add(new Hit(mid - 14, by - 14, 28, 28, MusicPlayer::toggle));
        round(g, mx, my, mid + 38, by, 10, Icons.FAST_FORWARD, 0xFFE8EAF0, () -> MusicPlayer.skip(10));
        round(g, mx, my, mid + 66, by, 10, Icons.SKIP_FORWARD, 0xFFE8EAF0, () -> MusicPlayer.next(false));
        String repeatIcon = c.musicRepeat == 2 ? Icons.REPEAT_1 : Icons.REPEAT;
        round(g, mx, my, mid + 96, by, 9, repeatIcon, c.musicRepeat == 0 ? 0xFF8C95A4 : ac, () -> { c.musicRepeat = (c.musicRepeat + 1) % 3; LavaVisualClient.save(); });

        // Volume.
        volX = ix + 16; volY = cy + 80; volW = iw - 50;
        UiFont.iconSmall(g, font, c.musicVolume <= 0.001 ? Icons.VOLUME_X : Icons.VOLUME_2, ix, volY - 3, 0xFFB8C0CD);
        UiDraw.round(g, volX, volY, volW, 3, 1, 0xFF353A43);
        int vf = (int) Math.round(volW * c.musicVolume);
        if (vf > 1) UiDraw.roundH(g, volX, volY, vf, 3, 1, ac, ac2);
        UiDraw.circle(g, volX + vf, volY + 1.5, 3, 0xFFF2F5FA);
        UiFont.text(g, font, Math.round(c.musicVolume * 100) + "%", volX + volW + 6, volY - 3, 0xFFB8C0CD, 40, UiFont.Face.SMALL);

        // Track list.
        listX = px + 14; listY = cy + cs + 22; listW = pw - 28; listH = Math.max(ROW, py + ph - 40 - listY);
        UiFont.text(g, font, "Треки · " + tracks.size() + (MusicPlayer.skipped() > 0 ? " · не Vorbis: " + MusicPlayer.skipped() : ""), listX, listY - 11, 0xFF838994, listW, UiFont.Face.SMALL);
        UiDraw.round(g, listX, listY, listW, listH, 6, 0x40000000);
        int max = Math.max(0, tracks.size() * ROW - listH);
        scroll = Math.clamp(scroll, 0, max);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int current = MusicPlayer.index();
        for (int i = 0; i < tracks.size(); i++) {
            int y = listY + i * ROW - scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            var t = tracks.get(i);
            boolean hover = mx >= listX && mx < listX + listW && my >= Math.max(y, listY) && my < Math.min(y + ROW, listY + listH);
            if (i == current) UiDraw.round(g, listX + 2, y + 1, listW - 4, ROW - 2, 5, UiDraw.alpha(ac, 0.22));
            else if (hover) UiDraw.round(g, listX + 2, y + 1, listW - 4, ROW - 2, 5, 0x18FFFFFF);
            int color = !t.playable() ? 0xFF6B7280 : i == current ? 0xFFF2F4F8 : 0xFFC9D0DA;
            String number = (i + 1 < 10 ? "0" : "") + (i + 1);
            if (i == current && MusicPlayer.active()) UiFont.iconSmall(g, font, MusicPlayer.paused() ? Icons.PAUSE : Icons.PLAY, listX + 7, y + 4, ac);
            else UiFont.text(g, font, number, listX + 8, y + 5, i == current ? ac : 0xFF6B7280, 16, UiFont.Face.SMALL);
            String dur = t.playable() ? MusicPlayer.time(t.seconds()) : "не Vorbis";
            int dw = UiFont.width(g, font, dur, UiFont.Face.SMALL);
            UiFont.text(g, font, t.line(), listX + 28, y + 5, color, listW - 44 - dw);
            UiFont.text(g, font, dur, listX + listW - 8 - dw, y + 5, 0xFF8C95A4, dw + 2, UiFont.Face.SMALL);
            int index = i;
            if (y + ROW > listY && y < listY + listH) hits.add(new Hit(listX, Math.max(y, listY), listW, Math.min(y + ROW, listY + listH) - Math.max(y, listY), () -> MusicPlayer.play(index)));
        }
        if (tracks.isEmpty()) UiFont.centered(g, font, "Папка music пуста", listX + listW / 2, listY + listH / 2 - 4, 0xFF6B7280, UiFont.Face.REGULAR);
        g.disableScissor();
        if (max > 0) {
            int h = Math.max(12, listH * listH / (tracks.size() * ROW)), y = listY + (int) ((listH - h) * (double) scroll / max);
            UiDraw.round(g, listX + listW - 3, y, 2, h, 1, UiDraw.alpha(ac, 0.7));
        }

        int bw = (pw - 28 - 12) / 3, bx = px + 14, bottom = py + ph - 28;
        buttons.draw(g, font, Icons.FOLDER_OPEN, "Папка music", bx, bottom, bw, 20, mx, my, false, () -> CustomSounds.open(CustomSounds.musicDir()));
        buttons.draw(g, font, Icons.REFRESH_CW, "Обновить", bx + bw + 6, bottom, bw, 20, mx, my, false, () -> MusicPlayer.rescan(CustomSounds.musicDir()));
        buttons.draw(g, font, Icons.CHECK, "Готово", bx + 2 * (bw + 6), bottom, bw, 20, mx, my, true, this::onClose);
        String hint = "Пробел — пауза · ← → — 10 сек · ↑ ↓ — громкость · клавиша плеера: " + Binds.keyName(Binds.Action.MUSIC);
        UiFont.centered(g, font, hint, width / 2, Math.min(height - 10, py + ph + 4), 0xFF8C95A4, UiFont.Face.SMALL);
        super.extractRenderState(g, mx, my, delta);
    }
    private void round(GuiGraphicsExtractor g, int mx, int my, int x, int y, int r, String icon, int color, Runnable action) {
        boolean over = Math.hypot(mx - x, my - y) <= r;
        UiDraw.circle(g, x, y, r, over ? 0xFF30333B : 0xFF23262D);
        UiFont.icon(g, font, icon, x - 5, y - 5, over ? 0xFFFFFFFF : color);
        hits.add(new Hit(x - r, y - r, 2 * r, 2 * r, action));
    }
    private void seekTo(double mouseX) {
        double len = MusicPlayer.duration();
        seekPreview = len * Math.clamp((mouseX - barX) / barW, 0, 1);
    }
    private void volumeTo(double mouseX) {
        LavaVisualClient.config().musicVolume = Math.clamp((mouseX - volX) / volW, 0, 1);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (buttons.click(x, y)) return true;
        if (y >= barY - 5 && y < barY + 9 && x >= barX - 4 && x <= barX + barW + 4 && MusicPlayer.active()) { seeking = true; seekTo(x); return true; }
        if (y >= volY - 5 && y < volY + 8 && x >= volX - 4 && x <= volX + volW + 4) { volumeDrag = true; volumeTo(x); return true; }
        for (Hit hit : List.copyOf(hits))
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) { hit.action().run(); return true; }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (seeking) { seekTo(event.x()); return true; }
        if (volumeDrag) { volumeTo(event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (seeking) { seeking = false; MusicPlayer.seek(seekPreview); return true; }
        if (volumeDrag) { volumeDrag = false; LavaVisualClient.save(); return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= listX && x < listX + listW && y >= listY && y < listY + listH) { scroll -= (int) Math.round(vertical * ROW); return true; }
        if (y >= volY - 8 && y < volY + 10 && x >= volX - 20 && x <= volX + volW + 30) {
            var c = LavaVisualClient.config();
            c.musicVolume = Math.clamp(c.musicVolume + Math.signum(vertical) * 0.05, 0, 1);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        var c = LavaVisualClient.config();
        switch (event.key()) {
            case GLFW.GLFW_KEY_SPACE -> MusicPlayer.toggle();
            case GLFW.GLFW_KEY_LEFT -> MusicPlayer.skip(-10);
            case GLFW.GLFW_KEY_RIGHT -> MusicPlayer.skip(10);
            case GLFW.GLFW_KEY_UP -> c.musicVolume = Math.clamp(c.musicVolume + 0.05, 0, 1);
            case GLFW.GLFW_KEY_DOWN -> c.musicVolume = Math.clamp(c.musicVolume - 0.05, 0, 1);
            case GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_PAGE_DOWN -> MusicPlayer.next(false);
            case GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_PAGE_UP -> MusicPlayer.previous();
            default -> {
                var open = Binds.mapping(Binds.Action.MUSIC);
                if (open != null && !open.isUnbound() && open.matches(event)) { onClose(); return true; }
                return super.keyPressed(event);
            }
        }
        return true;
    }
    @Override public void onFilesDrop(List<Path> files) {
        int copied = CustomSounds.importFiles(files, CustomSounds.musicDir());
        Binds.Toast.show(copied > 0 ? "Добавлено треков: " + copied : "Нужны файлы .ogg");
    }
    @Override public void onClose() { LavaVisualClient.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
