package tech.gulp.lavavisual.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pick the game the overlay should sit on: Minecraft, or any launcher that runs it. */
public class AppPickerActivity extends Activity {
    private static final String[] KNOWN = {"com.mojang.minecraftpe", "com.mojang.minecrafttrialpe"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Cfg cfg = new Cfg(this);
        int accent = cfg.accent();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, Ui.dp(this, 26), pad, pad);
        root.addView(Ui.text(this, "Выбери приложение", 22, Ui.TEXT, true));
        root.addView(Ui.text(this, "Обычно это Minecraft. Значок LV появится поверх него.", 13, Ui.DIM, false), Ui.wide(this, 4));

        PackageManager packages = getPackageManager();
        Intent launchable = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(packages.queryIntentActivities(launchable, 0));
        Collections.sort(apps, (a, b) -> {
            int rankA = rank(a.activityInfo.packageName), rankB = rank(b.activityInfo.packageName);
            if (rankA != rankB) return rankA - rankB;
            return a.loadLabel(packages).toString().compareToIgnoreCase(b.loadLabel(packages).toString());
        });

        for (ResolveInfo app : apps) {
            String name = app.activityInfo.packageName;
            if (name.equals(getPackageName())) continue;
            String label = app.loadLabel(packages).toString();
            Drawable icon = app.loadIcon(packages);
            LinearLayout row = Ui.cardBox(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            row.addView(image, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
            LinearLayout labels = Ui.column(this);
            labels.setPadding(Ui.dp(this, 12), 0, 0, 0);
            labels.addView(Ui.text(this, label, 16, Ui.TEXT, false));
            labels.addView(Ui.text(this, name, 11, Ui.DIM, false));
            row.addView(labels);
            row.setOnClickListener(view -> {
                cfg.target(name, label);
                finish();
            });
            root.addView(row, Ui.wide(this, 10));
        }

        root.addView(Ui.button(this, "Назад", accent, false, view -> finish()), Ui.wide(this, 14));
        scroll.addView(root);
        setContentView(scroll);
    }

    private int rank(String packageName) {
        for (String known : KNOWN) if (known.equals(packageName)) return 0;
        String lower = packageName.toLowerCase();
        if (lower.contains("minecraft") || lower.contains("mojang") || lower.contains("pojav") || lower.contains("mojo")) return 1;
        return 2;
    }
}
