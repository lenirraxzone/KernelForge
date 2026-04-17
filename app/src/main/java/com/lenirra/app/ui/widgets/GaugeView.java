package com.lenirra.app.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Gauge arc view — LENIRRAxZONE dark techy style
 * Warna: purple (#6C63FF) low → yellow (#FFB800) mid → red (#FF4757) high
 */
public class GaugeView extends View {

    private Paint trackPaint;
    private Paint fillPaint;
    private Paint textPaint;
    private RectF arcRect;

    private int value = 0; // 0-100

    private static final float START_ANGLE = 150f;
    private static final float SWEEP_MAX   = 240f;
    private static final float STROKE_WIDTH_DP = 10f;

    public GaugeView(Context ctx) { super(ctx); init(); }
    public GaugeView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public GaugeView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float sw = STROKE_WIDTH_DP * density;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(sw);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0xFF2A2A3E);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeWidth(sw);
        fillPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF0F0F0);

        arcRect = new RectF();
    }

    public void setValue(int v) {
        this.value = Math.max(0, Math.min(100, v));
        // Warna dinamis berdasarkan value
        if (v < 40)      fillPaint.setColor(0xFF6C63FF); // purple
        else if (v < 70) fillPaint.setColor(0xFFFFB800); // yellow
        else if (v < 85) fillPaint.setColor(0xFFFF6B35); // orange
        else             fillPaint.setColor(0xFFFF4757); // red
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float sw = STROKE_WIDTH_DP * density;
        float pad = sw / 2 + 4 * density;

        arcRect.set(pad, pad, getWidth() - pad, getHeight() - pad);

        // Track
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_MAX, false, trackPaint);

        // Fill
        float sweep = SWEEP_MAX * value / 100f;
        if (sweep > 0) {
            canvas.drawArc(arcRect, START_ANGLE, sweep, false, fillPaint);
        }
    }
}
