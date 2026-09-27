package tech.gulp.lavavisual.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The setup screen: pick Minecraft, allow the overlay, start. */
public class MainActivity extends Activity {
    private Cfg cfg;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = new Cfg(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        root = Ui.column(this);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 28));
        scroll.addView(root);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /** Lets a shortcut, a test or the notification start the overlay straight away. */
    private void handle(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra("picker", false)) startActivity(new Intent(this, AppPickerActivity.class));
        if (intent.getBooleanExtra("autostart", false)) start(intent.getBooleanExtra("launch", false));
        if (intent.getBooleanExtra("menu", false)) {
            OverlayService.ask(this, false, true);
            moveTaskToBack(true);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void build() {
        root.removeAllViews();
        int accent = cfg.accent();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View mark = new View(this);
        mark.setBackground(Badge.drawable(this, accent, cfg.badgeAlpha()));
        head.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(Ui.dp(this, 12), 0, 0, 0);
        titles.addView(Ui.text(this, "LavaVisual", 22, Ui.TEXT, true));
        titles.addView(Ui.text(this, "для Minecraft Bedrock · 1.0.0", 13, Ui.DIM, false));
        head.addView(titles);
        root.addView(head);

        root.addView(step(1, "Выбрать приложение",
                cfg.target().isEmpty() ? "Не выбрано" : cfg.targetName(),
                cfg.target().isEmpty() ? null : "Готово",
                accent, view -> startActivity(new Intent(this, AppPickerActivity.class))));

        root.addView(step(2, "Показ поверх других приложений",
                canOverlay() ? "Разрешение выдано" : "Нужно включить в настройках",
                canOverlay() ? "Готово" : null,
                accent, view -> {
                    if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }));

        root.addView(step(3, "Запустить",
                "Откроется игра, а в углу появится значок LV. Нажми на него — это меню визуала.",
                null, accent, view -> start(true)));

        root.addView(Ui.button(this, "Открыть меню без игры", accent, false, view -> start(false)), Ui.wide(this, 10));
        root.addView(Ui.button(this, "Убрать значок", accent, false, view -> {
            stopService(new Intent(this, OverlayService.class));
            toast("Значок убран");
        }), Ui.wide(this, 10));

        LinearLayout help = Ui.cardBox(this);
        help.addView(Ui.text(this, "Как это работает", 16, Ui.TEXT, true));
        help.addView(Ui.text(this, "Значок и меню рисуются поверх игры, поэтому игру менять не нужно и бан за это не грозит:"
                + " приложение ничего не делает внутри Minecraft.\n\n"
                + "Аксессуары (очки, наушники, шарф) собираются в пак скина .mcpack. Нажми «Собрать и открыть» — Minecraft"
                + " импортирует пак, и в Профиле выбери скин LavaVisual. Его увидят и другие игроки Bedrock,"
                + " и игроки Java, если на сервере стоит Geyser с расширением LavaVisual Bedrock.", 13, Ui.DIM, false),
                Ui.wide(this, 8));
        root.addView(help, Ui.wide(this, 16));
    }

    private View step(int number, String title, String subtitle, String done, int accent, View.OnClickListener click) {
        LinearLayout box = Ui.cardBox(this);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView index = Ui.text(this, String.valueOf(number), 15, done == null ? Ui.TEXT : 0xFF0E0F13, true);
        index.setGravity(Gravity.CENTER);
        index.setBackground(Ui.card(done == null ? Ui.LINE : accent, Ui.dp(this, 15), 0));
        line.addView(index, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        LinearLayout labels = Ui.column(this);
        labels.setPadding(Ui.dp(this, 12), 0, 0, 0);
        labels.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        labels.addView(Ui.text(this, subtitle, 12, done == null ? Ui.DIM : accent, false));
        line.addView(labels);
        box.addView(line);
        box.addView(Ui.button(this, done == null ? "Открыть" : "Изменить", accent, number == 3, click), Ui.wide(this, 10));
        LinearLayout.LayoutParams params = Ui.wide(this, 14);
        box.setLayoutParams(params);
        return box;
    }

    private void start(boolean launchGame) {
        if (!canOverlay()) { toast("Сначала разреши показ поверх других приложений"); return; }
        Intent intent = new Intent(this, OverlayService.class).putExtra("launch", launchGame);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        if (launchGame && cfg.target().isEmpty()) toast("Приложение не выбрано — открыто только меню");
        moveTaskToBack(true);
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
