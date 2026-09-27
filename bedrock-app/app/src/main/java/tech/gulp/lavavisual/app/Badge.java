package tech.gulp.lavavisual.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The LV mark: a dark rounded square with the accent outline and crisp letters. */
final class Badge {
    private Badge() { }

    static Drawable drawable(Context context, int accent, int alphaPercent) {
        final float radiusHint = Ui.dp(context, 12);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float radius = Math.min(radiusHint, Math.min(bounds.width(), bounds.height()) * 0.28f);
                RectF box = new RectF(bounds);
                box.inset(bounds.width() * 0.02f, bounds.height() * 0.02f);
                int alpha = Math.round(255 * Math.max(20, Math.min(100, alphaPercent)) / 100f);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF12131A);
                paint.setAlpha(alpha);
                canvas.drawRoundRect(box, radius, radius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, bounds.width() * 0.055f));
                paint.setColor(accent);
                paint.setAlpha(alpha);
                canvas.drawRoundRect(box, radius, radius, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFF2F4F8);
                paint.setAlpha(alpha);
                paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(bounds.height() * 0.46f);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                canvas.drawText("LV", box.centerX(), box.centerY() - (metrics.ascent + metrics.descent) / 2, paint);
            }

            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(ColorFilter filter) { }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }
}
