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
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.GridLayout;

import com.protonvpn.android.R;

import androidx.annotation.NonNull;
import butterknife.BindView;
import butterknife.ButterKnife;

public class ProtonPalette extends FrameLayout {

    @BindView(R.id.gridLayout) GridLayout gridLayout;
    private String selectedColor = "";

    public ProtonPalette(@NonNull Context context) {
        super(context);
    }

    public ProtonPalette(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProtonPalette(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init() {
        inflate(getContext(), R.layout.item_palette, this);
        ButterKnife.bind(this);
        int count = gridLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            ProtonColorCircle circle = (ProtonColorCircle) gridLayout.getChildAt(i);
            if (circle.isDefaultColor()) {
                selectedColor = circle.getCircleColor();
            }
            circle.init(this);
        }
    }

    public String getSelectedColor() {
        return selectedColor;
    }

    public void setSelectedColor(String color, boolean animate) {
        int count = gridLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            ProtonColorCircle circle = (ProtonColorCircle) gridLayout.getChildAt(i);
            circle.setContentDescription(circle.getCircleColor());
            circle.setChecked(circle.getCircleColor().equals(color), animate);
        }
        selectedColor = color;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }
}