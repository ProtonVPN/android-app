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
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.protonvpn.android.databinding.ComponentColorCircleBinding;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class ProtonColorCircle extends FrameLayout {

    private boolean isChecked = false;
    @NonNull
    private ComponentColorCircleBinding views;

    public ProtonColorCircle(@NonNull Context context) {
        super(context);
        init();
    }

    public ProtonColorCircle(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProtonColorCircle(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        views = ComponentColorCircleBinding.inflate(LayoutInflater.from(getContext()), this, true);
    }

    private void animateCheckBox(boolean shouldBeVisible, boolean animate) {
        views.imageCheckBox.animate().alpha(shouldBeVisible ? 1f : 0f).setDuration(animate ? 250 : 0);
    }

    public void setChecked(boolean enable, boolean animate) {
        if (isChecked() != enable) {
            isChecked = enable;
            animateCheckBox(enable, animate);
        }
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setColor(@ColorRes int colorRes) {
        views.circle.setBackgroundTintList(
            ColorStateList.valueOf(ContextCompat.getColor(getContext(), colorRes)));
    }
}
