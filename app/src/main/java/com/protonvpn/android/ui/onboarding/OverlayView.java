/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 Douglas Nassif Roma Junior
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
// original file: io.github.douglasjunior.androidSimpleTooltip.OverlayView

package com.protonvpn.android.ui.onboarding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltipUtils;

public class OverlayView extends View {

    public static final int HIGHLIGHT_SHAPE_OVAL = 0;
    public static final int HIGHLIGHT_SHAPE_RECTANGULAR = 1;
    private static final int DEFAULT_OVERLAY_ALPHA_RES =
        io.github.douglasjunior.androidSimpleTooltip.R.integer.simpletooltip_overlay_alpha;

    private View mAnchorView;
    private Bitmap bitmap;

    private boolean invalidated = true;
    private final int highlightShape;
    private final float mOffset;

    OverlayView(Context context, View anchorView, int highlightShape, float offset) {
        super(context);
        this.mAnchorView = anchorView;
        this.mOffset = offset;
        this.highlightShape = highlightShape;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (invalidated || bitmap == null || bitmap.isRecycled()) {
            createWindowFrame();
        }
        // The bitmap is checked again because of Android memory cleanup behavior. (See #42)
        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
    }

    private void createWindowFrame() {
        final int width = getMeasuredWidth(), height = getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas osCanvas = new Canvas(bitmap);

        RectF outerRectangle = new RectF(0, 0, width, height);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setAlpha(getResources().getInteger(DEFAULT_OVERLAY_ALPHA_RES));
        osCanvas.drawRect(outerRectangle, paint);

        paint.setColor(Color.TRANSPARENT);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));

        RectF anchorRecr = SimpleTooltipUtils.calculateRectInWindow(mAnchorView);
        RectF overlayRecr = SimpleTooltipUtils.calculateRectInWindow(this);

        float left = anchorRecr.left - overlayRecr.left;
        float top = anchorRecr.top - overlayRecr.top;

        RectF rect = new RectF(left - mOffset, top - mOffset, left + mAnchorView.getMeasuredWidth() + mOffset,
            top + mAnchorView.getMeasuredHeight() + mOffset);

        if (highlightShape == HIGHLIGHT_SHAPE_RECTANGULAR) {
            osCanvas.drawRect(rect, paint);
        }
        else {
            osCanvas.drawOval(rect, paint);
        }

        invalidated = false;
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        invalidated = true;
    }

    public View getAnchorView() {
        return mAnchorView;
    }

    public void setAnchorView(View anchorView) {
        this.mAnchorView = anchorView;
        invalidate();
    }
}
