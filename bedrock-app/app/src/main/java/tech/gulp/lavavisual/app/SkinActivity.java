package tech.gulp.lavavisual.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

/** Picks the skin file and hands the built pack to Minecraft. Has no screen of its own. */
public class SkinActivity extends Activity {
    private static final int PICK = 11;
    private Cfg cfg;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = new Cfg(this);
        String action = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("action"));
        if ("pick".equals(action)) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/png")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick, PICK);
        } else {
            share();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            cfg.skinUri(uri.toString());
            Toast.makeText(this, "Скин выбран", Toast.LENGTH_SHORT).show();
        }
        back();
    }

    private void share() {
        try {
            File pack = SkinPack.build(this, cfg);
            Uri uri = PackProvider.uri(getPackageName(), pack);
            Intent open = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/octet-stream")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            String target = cfg.target();
            if (!target.isEmpty() && getPackageManager().getLaunchIntentForPackage(target) != null) open.setPackage(target);
            try {
                startActivity(open);
            } catch (Exception ignored) {
                startActivity(Intent.createChooser(open.setPackage(null), "Открыть пак в Minecraft")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            Toast.makeText(this, "Пак собран: " + pack.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception failed) {
            Toast.makeText(this, String.valueOf(failed.getMessage()), Toast.LENGTH_LONG).show();
        }
        back();
    }

    private void back() {
        OverlayService.ask(this, false, true);
        finish();
    }
}
