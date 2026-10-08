package com.takeabreak.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A quiet progress indicator around the home control. */
final class TimerRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float progress;
    private float holdProgress;
    private boolean paused;

    TimerRingView(Context context) {
        super(context);
        float stroke = Ui.dp(context, 5);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Color.rgb(220, 230, 222));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        setProgress(0f, false);
    }

    void setProgress(float value, boolean isPaused) {
        progress = Math.max(0f, Math.min(1f, value));
        paused = isPaused;
        progressPaint.setColor(paused ? Color.rgb(171, 139, 83) : Ui.GREEN);
        invalidate();
    }

    void setHoldProgress(float value) {
        holdProgress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) / 2f - Ui.dp(getContext(), 4);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, -90f, 360f, false, track);
        if (progress > 0) canvas.drawArc(arc, -90f, 360f * progress, false, progressPaint);
        if (holdProgress > 0) {
            progressPaint.setColor(Color.rgb(197, 151, 70));
            canvas.drawArc(arc, -90f, 360f * holdProgress, false, progressPaint);
            progressPaint.setColor(paused ? Color.rgb(171, 139, 83) : Ui.GREEN);
        }
    }
}
