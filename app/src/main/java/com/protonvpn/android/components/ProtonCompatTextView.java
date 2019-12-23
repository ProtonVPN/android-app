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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.protonvpn.android.R;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatDrawableManager;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.ViewCompat;

public class ProtonCompatTextView extends AppCompatTextView {

    public ProtonCompatTextView(Context context) {
        super(context);
        init(null);
    }

    public ProtonCompatTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ProtonCompatTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            Context context = getContext();
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProtonCompatTextView);

            AppCompatDrawableManager dm = AppCompatDrawableManager.get();
            boolean rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;

            int startDrawableRes = a.getResourceId(R.styleable.ProtonCompatTextView_drawableStart, 0);
            int topDrawableRes = a.getResourceId(R.styleable.ProtonCompatTextView_drawableTop, 0);
            int endDrawableRes = a.getResourceId(R.styleable.ProtonCompatTextView_drawableEnd, 0);
            int bottomDrawableRes = a.getResourceId(R.styleable.ProtonCompatTextView_drawableBottom, 0);

            Drawable[] currentDrawables = getCompoundDrawables();
            Drawable left =
                startDrawableRes != 0 ? dm.getDrawable(context, startDrawableRes) : currentDrawables[0];
            Drawable right =
                endDrawableRes != 0 ? dm.getDrawable(context, endDrawableRes) : currentDrawables[1];
            Drawable top =
                topDrawableRes != 0 ? dm.getDrawable(context, topDrawableRes) : currentDrawables[2];
            Drawable bottom =
                bottomDrawableRes != 0 ? dm.getDrawable(context, bottomDrawableRes) : currentDrawables[3];

            Drawable start = rtl ? right : left;
            Drawable end = rtl ? left : right;
            setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);

            a.recycle();
        }
    }
}