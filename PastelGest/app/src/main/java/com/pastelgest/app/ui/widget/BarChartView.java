package com.pastelgest.app.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.pastelgest.app.R;

/** Gráfico de barras agrupadas (vendas vs lucro) desenhado em Canvas, sem bibliotecas externas. */
public class BarChartView extends View {
    private final Paint salesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint profitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String[] labels = new String[0];
    private long[] seriesA = new long[0];
    private long[] seriesB = new long[0];
    private float progress = 1f;

    public BarChartView(Context c) {
        this(c, null);
    }

    public BarChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        salesPaint.setColor(ContextCompat.getColor(c, R.color.pg_chart_sales));
        profitPaint.setColor(ContextCompat.getColor(c, R.color.pg_chart_profit));
        lossPaint.setColor(ContextCompat.getColor(c, R.color.pg_red));
        gridPaint.setColor(ContextCompat.getColor(c, R.color.pg_chart_grid));
        gridPaint.setStrokeWidth(dp(1));
        labelPaint.setColor(ContextCompat.getColor(c, R.color.pg_text_secondary));
        labelPaint.setTextSize(dp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(String[] labels, long[] a, long[] b) {
        this.labels = labels;
        this.seriesA = a;
        this.seriesB = b;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(v -> {
            progress = (float) v.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = labels.length;
        float w = getWidth();
        float h = getHeight();
        float labelH = dp(18);
        float top = dp(6);
        float chartH = h - labelH - top;
        long max = 1, min = 0;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, Math.max(seriesA[i], seriesB[i]));
            min = Math.min(min, Math.min(seriesA[i], seriesB[i]));
        }
        float range = (float) (max - min);
        float zeroY = top + chartH * (max / range);
        for (int g = 0; g <= 4; g++) {
            float y = top + chartH * g / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        if (n == 0) return;
        float slot = w / n;
        float barW = Math.min(dp(14), slot / 3.2f);
        float radius = dp(4);
        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            drawBar(canvas, cx - barW - dp(1), barW, seriesA[i], zeroY, chartH / range, radius, salesPaint);
            drawBar(canvas, cx + dp(1), barW, seriesB[i], zeroY, chartH / range, radius,
                    seriesB[i] < 0 ? lossPaint : profitPaint);
            canvas.drawText(labels[i], cx, h - dp(4), labelPaint);
        }
    }

    private void drawBar(Canvas c, float x, float bw, long v, float zeroY, float scale, float r, Paint p) {
        float len = Math.abs(v) * scale * progress;
        if (len < 1) return;
        if (v >= 0) rect.set(x, zeroY - len, x + bw, zeroY);
        else rect.set(x, zeroY, x + bw, zeroY + len);
        c.drawRoundRect(rect, r, r, p);
    }
}
