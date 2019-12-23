/*
 * Copyright (c) 2017 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

import com.protonvpn.android.R;

import androidx.annotation.ColorRes;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

public class TriangledTextView extends AppCompatTextView {

    private final Paint paint;
    private final Path path;

    public TriangledTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        path = new Path();
        paint.setColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        path.reset();
        path.moveTo(.0f, 0.5f * this.getHeight());
        path.lineTo(50, this.getHeight());
        path.lineTo(this.getWidth(), this.getHeight());
        path.lineTo(this.getWidth(), .0f);
        path.lineTo(50, .0f);
        path.lineTo(.0f, 0.5f * this.getHeight());

        canvas.clipPath(path);
        canvas.drawPath(path, paint);
        super.onDraw(canvas);
    }

    public void setColor(@ColorRes int color) {
        if (paint.getColor() != ContextCompat.getColor(getContext(), color)) {
            paint.setColor(ContextCompat.getColor(getContext(), color));
            invalidate();
        }
    }
}