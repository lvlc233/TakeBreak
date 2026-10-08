package com.takeabreak.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(26, 54, 49);
    static final int MUTED = Color.rgb(92, 111, 104);
    static final int PAPER = Color.rgb(247, 246, 240);
    static final int GREEN = Color.rgb(35, 93, 77);
    static final int MINT = Color.rgb(220, 239, 226);
    static final int WHITE = Color.WHITE;
    private Ui() {}
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable box(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setShape(GradientDrawable.OVAL); return d;
    }
    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    static TextView button(Context c, String value, int bg, int fg) {
        TextView t = text(c, value, 16, fg, true);
        t.setGravity(Gravity.CENTER); t.setBackground(box(c, bg, 16));
        t.setMinHeight(dp(c, 54)); t.setClickable(true); t.setFocusable(true);
        return t;
    }
}
