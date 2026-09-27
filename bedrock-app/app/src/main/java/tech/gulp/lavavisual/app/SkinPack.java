package tech.gulp.lavavisual.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Turns the chosen skin and the chosen accessories into a .mcpack Minecraft can import. */
final class SkinPack {
    private SkinPack() { }

    static File folder(Context context) {
        File folder = new File(context.getCacheDir(), "packs");
        folder.mkdirs();
        return folder;
    }

    /** Reads the skin, paints the palette, writes the pack. Returns the file. */
    static File build(Context context, Cfg cfg) throws IOException {
        if (cfg.skinUri().isEmpty()) throw new IOException("Сначала выбери файл скина .png");
        Bitmap source = read(context, Uri.parse(cfg.skinUri()));
        if (source == null) throw new IOException("Не удалось прочитать картинку");
        Bitmap skin = square(source);
        if (skin == null) throw new IOException("Скин должен быть 64×64 или 64×32 пикселя, а этот "
                + source.getWidth() + "×" + source.getHeight());

        int[] pixels = new int[SkinMath.SIZE * SkinMath.SIZE];
        skin.getPixels(pixels, 0, SkinMath.SIZE, 0, 0, SkinMath.SIZE, SkinMath.SIZE);
        SkinMath.paint(pixels, cfg.colorCode());
        Bitmap painted = Bitmap.createBitmap(pixels, SkinMath.SIZE, SkinMath.SIZE, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        painted.compress(Bitmap.CompressFormat.PNG, 100, png);

        int mask = cfg.mask();
        if (mask == 0) throw new IOException("Включи хотя бы один аксессуар");
        String name = SkinMath.geometryName(mask, cfg.rainbow(), cfg.slim());
        String geometry = asset(context, "geometry/" + name + ".json");
        byte[] pack = PackWriter.build("LavaVisual", "LavaVisual", png.toByteArray(), geometry,
                "geometry.lavavisual." + name, cfg.slim(), name + "-" + cfg.colorCode());

        File out = new File(folder(context), "LavaVisual-" + name + "-" + cfg.colorCode() + ".mcpack");
        try (FileOutputStream stream = new FileOutputStream(out)) {
            stream.write(pack);
        }
        return out;
    }

    private static Bitmap read(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return in == null ? null : BitmapFactory.decodeStream(in);
        }
    }

    /** 64x64 as is; the old 64x32 layout is padded; anything else is rejected. */
    private static Bitmap square(Bitmap source) {
        if (source.getWidth() != SkinMath.SIZE) return null;
        if (source.getHeight() == SkinMath.SIZE) return source.copy(Bitmap.Config.ARGB_8888, true);
        if (source.getHeight() != SkinMath.SIZE / 2) return null;
        Bitmap grown = Bitmap.createBitmap(SkinMath.SIZE, SkinMath.SIZE, Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(grown).drawBitmap(source, 0, 0, null);
        return grown;
    }

    private static String asset(Context context, String path) throws IOException {
        try (InputStream in = context.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
