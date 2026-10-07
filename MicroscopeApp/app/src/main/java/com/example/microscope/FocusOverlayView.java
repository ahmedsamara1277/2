package com.example.microscope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** طبقة رسم مؤشر التركيز عند اللمس */
public class FocusOverlayView extends View {

    private float x = -1, y = -1;
    private long showTime = 0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FocusOverlayView(Context c) { super(c); }
    public FocusOverlayView(Context c, AttributeSet a) { super(c, a); }

    public void showFocus(float fx, float fy) {
        x = fx; y = fy;
        showTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (x < 0) return;
        if (System.currentTimeMillis() - showTime > 1000) { x = -1; return; }

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(x, y, 50, paint);
        canvas.drawLine(x - 70, y, x - 50, y, paint);
        canvas.drawLine(x + 50, y, x + 70, y, paint);
        canvas.drawLine(x, y - 70, x, y - 50, paint);
        canvas.drawLine(x, y + 50, x, y + 70, paint);
        postInvalidateDelayed(50);
    }
}
