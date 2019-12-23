/*
 * Copyright (c) 2018 Proton Technologies AG
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.utils.Storage;

import org.apache.commons.lang3.reflect.FieldUtils;

public class ProtonActionMenu extends FloatingActionMenu {

    FloatingActionButton button;
    private long lastClickTime = 0;
    boolean isInited = false;

    public ProtonActionMenu(Context context) {
        this(context, null);
        initActionButton();
    }

    public ProtonActionMenu(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        initActionButton();
    }

    public ProtonActionMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initActionButton();
    }

    public FloatingActionButton getActionButton() {
        return button;
    }

    private void initActionButton() {
        try {
            button = (FloatingActionButton) FieldUtils.readField(this, "mMenuButton", true);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void onboardingAnimation() {
        if (!Storage.getBoolean(OnboardingPreferences.FLOATING_BUTTON_USED) && !isInited) {
            animateMenu();
            isInited = true;
        }
    }

    public void setOnMenuButtonClickListener(OnClickListener clickListener) {
        super.setOnMenuButtonClickListener(v -> {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1500) {
                return;
            }
            lastClickTime = SystemClock.elapsedRealtime();
            clickListener.onClick(v);
        });
    }

    private void animateMenu() {
        final AnimatorSet animSetXY = new AnimatorSet();
        animSetXY.playTogether(ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.1f, 1f),
            ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.1f, 1f));
        animSetXY.setDuration(1000);
        animSetXY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (!Storage.getBoolean(OnboardingPreferences.FLOATING_BUTTON_USED)) {
                    animateMenu();
                }
            }
        });
        animSetXY.start();
    }
}
