package tech.gulp.lavavisual.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** The look of the client: dark rounded cards, smooth system sans, one accent colour. */
final class Ui {
    static final int BG = 0xFF101116, CARD = 0xFF181A21, LINE = 0xFF262933, TEXT = 0xFFF2F4F8, DIM = 0xFF9AA0AC;
    static final Typeface SANS = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    static GradientDrawable card(int fill, float radius, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        if (strokeColor != 0) shape.setStroke(Math.max(1, Math.round(radius / 14f)), strokeColor);
        return shape;
    }

    static TextView text(Context context, CharSequence value, float sp, int color, boolean strong) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(strong ? Typeface.create("sans-serif-medium", Typeface.BOLD) : SANS);
        view.setLineSpacing(dp(context, 2), 1f);
        return view;
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout.LayoutParams wide(Context context, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topMargin);
        return params;
    }

    static TextView button(Context context, CharSequence label, int accent, boolean filled, View.OnClickListener click) {
        TextView view = text(context, label, 15, filled ? 0xFF0E0F13 : TEXT, true);
        view.setGravity(Gravity.CENTER);
        int pad = dp(context, 13);
        view.setPadding(pad, pad, pad, pad);
        view.setBackground(card(filled ? accent : CARD, dp(context, 14), filled ? 0 : LINE));
        view.setOnClickListener(click);
        return view;
    }

    static LinearLayout cardBox(Context context) {
        LinearLayout box = column(context);
        int pad = dp(context, 14);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(card(CARD, dp(context, 16), LINE));
        return box;
    }

    interface OnToggle { void changed(boolean on); }
    interface OnValue { void changed(int value); }

    static View toggle(Context context, String title, String hint, boolean on, int accent, OnToggle listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));
        LinearLayout labels = column(context);
        labels.addView(text(context, title, 15, TEXT, false));
        if (hint != null) labels.addView(text(context, hint, 12, DIM, false));
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(labels, grow);
        Switch flip = new Switch(context);
        flip.setChecked(on);
        flip.getThumbDrawable().setTint(on ? accent : 0xFF6A7080);
        flip.setOnCheckedChangeListener((view, checked) -> {
            flip.getThumbDrawable().setTint(checked ? accent : 0xFF6A7080);
            listener.changed(checked);
        });
        row.addView(flip);
        return row;
    }

    static View slider(Context context, String title, int min, int max, int value, int accent, OnValue listener) {
        LinearLayout box = column(context);
        box.setPadding(0, dp(context, 8), 0, dp(context, 4));
        TextView label = text(context, title + ": " + value, 14, TEXT, false);
        box.addView(label);
        SeekBar bar = new SeekBar(context);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.getProgressDrawable().setTint(accent);
        bar.getThumb().setTint(accent);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + ": " + (min + progress));
                if (fromUser) listener.changed(min + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(bar, wide(context, 0));
        return box;
    }

    /** The 32 shared colour codes of the mod, 0 being rainbow. */
    static View palette(Context context, int selected, int accent, OnValue listener) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 8), 0, dp(context, 8));
        int size = dp(context, 30);
        for (int code = 0; code < 32; code++) {
            final int value = code;
            View dot = new View(context);
            dot.setBackground(swatch(context, code, code == selected, accent));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.rightMargin = dp(context, 8);
            dot.setOnClickListener(view -> {
                for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setBackground(swatch(context, i, i == value, accent));
                listener.changed(value);
            });
            row.addView(dot, params);
        }
        scroll.addView(row);
        return scroll;
    }

    private static GradientDrawable swatch(Context context, int code, boolean on, int accent) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        if (code == 0) {
            shape.setColors(new int[]{0xFFFF4D4D, 0xFFFFC44D, 0xFF4DFF88, 0xFF4DD2FF, 0xFFB14DFF});
            shape.setGradientType(GradientDrawable.SWEEP_GRADIENT);
        } else {
            shape.setColor(Colors.opaque(Colors.code(code)));
        }
        shape.setStroke(dp(context, on ? 3 : 1), on ? accent : LINE);
        return shape;
    }

    static int textColorOn(int background) {
        double lum = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255;
        return lum > 0.6 ? 0xFF0E0F13 : TEXT;
    }
}
