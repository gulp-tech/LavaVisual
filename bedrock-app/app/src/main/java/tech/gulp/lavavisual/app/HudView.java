package tech.gulp.lavavisual.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.view.Choreographer;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The small line of numbers over the game: clock, battery, frame rate of the screen, server ping. */
class HudView extends View implements Choreographer.FrameCallback {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Cfg cfg;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private long frameWindow, lastSample;
    private int frames, fps;
    private volatile int ping = -1;
    private long lastPing;
    private String[] lines = new String[0];

    HudView(Context context, Cfg cfg) {
        super(context);
        this.cfg = cfg;
        paint.setTypeface(Ui.SANS);
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNanos) {
        frames++;
        if (frameWindow == 0) frameWindow = frameTimeNanos;
        if (frameTimeNanos - frameWindow >= 1_000_000_000L) {
            fps = (int) Math.round(frames * 1e9 / (frameTimeNanos - frameWindow));
            frames = 0;
            frameWindow = frameTimeNanos;
        }
        long now = System.currentTimeMillis();
        if (now - lastSample >= 500) {
            lastSample = now;
            refresh(now);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void refresh(long now) {
        if (cfg.hudPing() && !cfg.pingHost().isEmpty() && now - lastPing > 5000) {
            lastPing = now;
            String address = cfg.pingHost();
            new Thread(() -> ping = Pinger.measure(address, 4000), "lavavisual-ping").start();
        }
        lines = build();
        invalidate();
        requestLayout();
    }

    private String[] build() {
        if (!cfg.hud()) return new String[0];
        String[] all = new String[4];
        int count = 0;
        if (cfg.hudClock()) all[count++] = clock.format(new Date());
        if (cfg.hudFps()) all[count++] = fps + " FPS";
        if (cfg.hudBattery()) all[count++] = battery() + "%";
        if (cfg.hudPing()) all[count++] = (ping < 0 ? "— ms" : ping + " ms");
        String[] shown = new String[count];
        System.arraycopy(all, 0, shown, 0, count);
        return shown;
    }

    private int battery() {
        Intent status = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return 0;
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale <= 0 ? 0 : Math.round(level * 100f / scale);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        paint.setTextSize(Ui.dp(getContext(), cfg.hudSize()));
        float width = 0;
        for (String line : lines) width = Math.max(width, paint.measureText(line));
        int pad = Ui.dp(getContext(), 8);
        float step = paint.getTextSize() * 1.35f;
        setMeasuredDimension(Math.round(width) + pad * 2, Math.round(step * Math.max(1, lines.length)) + pad);
    }

    @Override protected void onDraw(Canvas canvas) {
        paint.setTextSize(Ui.dp(getContext(), cfg.hudSize()));
        float step = paint.getTextSize() * 1.35f, x = Ui.dp(getContext(), 8), y = step;
        for (String line : lines) {
            paint.setColor(0xC0000000);
            canvas.drawText(line, x + 1.5f, y + 1.5f, paint);
            paint.setColor(Ui.TEXT);
            canvas.drawText(line, x, y, paint);
            y += step;
        }
    }
}
