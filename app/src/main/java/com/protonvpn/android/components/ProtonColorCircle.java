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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.protonvpn.android.R;

import androidx.annotation.NonNull;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;

public class ProtonColorCircle extends FrameLayout {

    @BindView(R.id.circle) CircleImageView circle;
    @BindView(R.id.imageCheckBox) ImageView imageCheckBox;
    private String circleColor;
    private boolean defaultColor = false;
    private ProtonPalette palette;

    public ProtonColorCircle(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs);
    }

    public ProtonColorCircle(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttrs(attrs);
    }

    private void initAttrs(AttributeSet attrs) {
        TypedArray a =
            getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ProtonColorCircle, 0, 0);

        try {
            circleColor = a.getString(R.styleable.ProtonColorCircle_circleColor);
            defaultColor = a.getBoolean(R.styleable.ProtonColorCircle_defaultColor, false);
        }
        finally {
            a.recycle();
        }
    }

    @OnClick(R.id.circle)
    public void circle() {
        if (!isChecked()) {
            palette.setSelectedColor(circleColor, true);
        }
    }

    private void animateCheckBox(boolean shouldBeVisible, boolean animate) {
        imageCheckBox.animate().alpha(shouldBeVisible ? 1f : 0f).setDuration(animate ? 250 : 0);
    }

    public void setChecked(boolean enable, boolean animate) {
        if (isChecked() != enable) {
            animateCheckBox(enable, animate);
        }
    }

    public boolean isChecked() {
        return palette.getSelectedColor().equals(circleColor);
    }

    public boolean isDefaultColor() {
        return defaultColor;
    }

    public String getCircleColor() {
        return circleColor;
    }

    public void init(ProtonPalette palette) {
        this.palette = palette;
        inflate(getContext(), R.layout.component_color_circle, this);
        ButterKnife.bind(this);
        circle.setImageDrawable(new ColorDrawable(Color.parseColor(circleColor)));
        imageCheckBox.setAlpha(defaultColor ? 1f : 0f);
    }
}