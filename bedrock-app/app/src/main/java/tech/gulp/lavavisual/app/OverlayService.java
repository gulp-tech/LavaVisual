package tech.gulp.lavavisual.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;
import android.widget.Toast;

/** Keeps the LV badge, the HUD and the menu on the screen while the game is in front. */
public class OverlayService extends Service {
    private static final String CHANNEL = "lavavisual";
    static OverlayService running;

    private WindowManager windows;
    private Cfg cfg;
    private View badge, hud;
    private MenuView menu;
    private boolean movingHud;

    @Override public void onCreate() {
        super.onCreate();
        running = this;
        cfg = new Cfg(this);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        foreground();
        addBadge();
        addHud();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("launch", false)) launchGame();
        if (intent != null && intent.getBooleanExtra("menu", false)) openMenu();
        Log.i("LavaVisual", "service command: " + (intent == null ? "restart" : intent.getExtras()));
        return START_STICKY;
    }

    private void launchGame() {
        String target = cfg.target();
        if (target.isEmpty()) return;
        Intent game = getPackageManager().getLaunchIntentForPackage(target);
        if (game == null) {
            Toast.makeText(this, "Не удалось открыть " + cfg.targetName(), Toast.LENGTH_LONG).show();
            return;
        }
        game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(game);
    }

    static int windowType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static WindowManager.LayoutParams params(int flags) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                windowType(), flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    // --- badge ---

    private void addBadge() {
        if (badge != null) return;
        badge = new View(this);
        badge.setBackground(Badge.drawable(this, cfg.accent(), cfg.badgeAlpha()));
        badge.setContentDescription("LavaVisual");
        WindowManager.LayoutParams layout = params(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        int size = Ui.dp(this, cfg.badgeSize());
        layout.width = size; layout.height = size;
        layout.x = cfg.badgeX(); layout.y = cfg.badgeY();
        layout.setTitle("LavaVisual badge");
        badge.setOnTouchListener(new Dragger(layout, badge, (x, y) -> cfg.badgePos(x, y), this::openMenu));
        windows.addView(badge, layout);
    }

    void refreshBadge() {
        if (badge == null) return;
        windows.removeView(badge);
        badge = null;
        addBadge();
    }

    // --- hud ---

    private void addHud() {
        if (hud != null) { windows.removeView(hud); hud = null; }
        if (!cfg.hud()) return;
        hud = new HudView(this, cfg);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | (movingHud ? 0 : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        WindowManager.LayoutParams layout = params(flags);
        layout.x = cfg.hudX(); layout.y = cfg.hudY();
        layout.setTitle("LavaVisual hud");
        if (movingHud) hud.setOnTouchListener(new Dragger(layout, hud, (x, y) -> cfg.hudPos(x, y), null));
        windows.addView(hud, layout);
    }

    void refreshHud() { addHud(); }

    void moveHud(boolean on) {
        movingHud = on;
        addHud();
        Toast.makeText(this, on ? "Перетащи цифры на нужное место" : "Место сохранено", Toast.LENGTH_SHORT).show();
    }

    boolean movingHud() { return movingHud; }

    // --- menu ---

    void openMenu() {
        if (menu != null) return;
        menu = new MenuView(this, cfg, this);
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                windowType(), WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
        layout.setTitle("LavaVisual menu");
        layout.dimAmount = 0.45f;
        layout.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        windows.addView(menu, layout);
        if (badge != null) badge.setVisibility(View.GONE);
        Log.i("LavaVisual", "menu opened");
    }

    void closeMenu() {
        if (menu == null) return;
        windows.removeView(menu);
        menu = null;
        if (badge != null) badge.setVisibility(View.VISIBLE);
    }

    @Override public void onDestroy() {
        closeMenu();
        if (badge != null) windows.removeView(badge);
        if (hud != null) windows.removeView(hud);
        badge = hud = null;
        running = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void foreground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "LavaVisual", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification notification = builder
                .setContentTitle("LavaVisual")
                .setContentText("Значок LV поверх игры")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, notification);
    }

    /** Drag with a tap that still counts as a tap. */
    private final class Dragger implements View.OnTouchListener {
        private final WindowManager.LayoutParams layout;
        private final View view;
        private final Saver saver;
        private final Runnable tap;
        private int startX, startY;
        private float touchX, touchY;
        private long down;
        private boolean moved;

        Dragger(WindowManager.LayoutParams layout, View view, Saver saver, Runnable tap) {
            this.layout = layout; this.view = view; this.saver = saver; this.tap = tap;
        }

        @Override public boolean onTouch(View ignored, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = layout.x; startY = layout.y;
                    touchX = event.getRawX(); touchY = event.getRawY();
                    down = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = Math.round(event.getRawX() - touchX), dy = Math.round(event.getRawY() - touchY);
                    if (Math.abs(dx) > Ui.dp(OverlayService.this, 6) || Math.abs(dy) > Ui.dp(OverlayService.this, 6)) moved = true;
                    if (moved) {
                        layout.x = Math.max(0, startX + dx);
                        layout.y = Math.max(0, startY + dy);
                        windows.updateViewLayout(view, layout);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) saver.save(layout.x, layout.y);
                    else if (tap != null && System.currentTimeMillis() - down < 400) { Log.i("LavaVisual", "badge tapped"); tap.run(); }
                    return true;
                default:
                    return false;
            }
        }
    }

    private interface Saver { void save(int x, int y); }

    /** Lets an activity reach the running overlay. */
    static void ask(Context context, boolean launch, boolean menu) {
        Intent intent = new Intent(context, OverlayService.class).putExtra("launch", launch).putExtra("menu", menu);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
    }
}
