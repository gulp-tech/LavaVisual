package tech.gulp.lavavisual.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Hands the built .mcpack to Minecraft: a tiny file provider, so no extra library is needed. */
public class PackProvider extends ContentProvider {
    static Uri uri(String packageName, File file) {
        return Uri.parse("content://" + packageName + ".packs/" + file.getName());
    }

    private File file(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) return null;
        return new File(SkinPack.folder(getContext()), name);
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = file(uri);
        if (file == null || !file.exists()) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File file = file(uri);
        if (file == null || !file.exists()) return null;
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
