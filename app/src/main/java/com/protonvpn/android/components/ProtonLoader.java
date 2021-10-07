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

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.protonvpn.android.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import butterknife.BindView;
import butterknife.ButterKnife;

public class ProtonLoader extends FrameLayout {

    @BindView(R.id.loaderCircle) View loaderCircle;
    @BindView(R.id.loaderCircle2) View loaderCircle2;

    @Nullable
    private AnimatorSet animations;

    public ProtonLoader(@NonNull Context context) {
        super(context);
    }

    public ProtonLoader(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProtonLoader(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init() {
        inflate(getContext(), R.layout.item_proton_loader, this);
        ButterKnife.bind(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        init();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        stopAnimations();
        animations = new AnimatorSet();
        animations.playTogether(animateView(loaderCircle), animateView(loaderCircle2));
        animations.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimations();
        super.onDetachedFromWindow();
    }

    private AnimatorSet animateView(View view) {
        AnimatorSet set =
            (AnimatorSet) AnimatorInflater.loadAnimator(getContext(), R.animator.animation_rotate);
        set.setTarget(view);
        return set;
    }

    private void stopAnimations() {
        if (animations != null) {
            animations.cancel();
        }
    }
}
