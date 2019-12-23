/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.SeekBar;

public class SeekBarTicks extends SeekBar {
    private Paint mTickPaint;
    private float mTickHeight;

    private float tickHeightRatio = 0.6f;

    public SeekBarTicks(Context context, AttributeSet attrs) {
        super (context, attrs);

        initTicks (context, attrs, android.R.attr.seekBarStyle);
    }


    public SeekBarTicks(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        initTicks (context, attrs, defStyle);

        /*mTickHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                tickHeightDP,
                ctx.getResources().getDisplayMetrics()); */
    }

    private void initTicks(Context context, AttributeSet attrs, int defStyle) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] { android.R.attr.secondaryProgress }, defStyle, 0);

        mTickPaint = new Paint();
        //noinspection deprecation
        mTickPaint.setColor( context.getResources().getColor(android.R.color.black));
        a.recycle();
    }


    @Override
    protected synchronized void onDraw(Canvas canvas) {
        drawTicks(canvas);
        super.onDraw(canvas);
    }

    private void drawTicks(Canvas canvas) {

        final int available = getWidth() - getPaddingLeft() - getPaddingRight();
        final int availableHeight = getHeight() - getPaddingBottom() - getPaddingTop();

        int extrapadding = (int) ((availableHeight- (availableHeight * tickHeightRatio))/2);

        int tickSpacing = available / (getMax() );

        for (int i = 1; i < getMax(); i++) {
            final float x = getPaddingLeft() + i * tickSpacing;

            canvas.drawLine(x, getPaddingTop()+extrapadding, x, getHeight()-getPaddingBottom()-extrapadding, mTickPaint);
        }
    }
}
