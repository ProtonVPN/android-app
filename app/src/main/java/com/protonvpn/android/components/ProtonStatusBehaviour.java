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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import com.google.android.material.appbar.AppBarLayout;

import java.util.List;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;

public class ProtonStatusBehaviour extends AppBarLayout.ScrollingViewBehavior {

    public ProtonStatusBehaviour() { }

    public ProtonStatusBehaviour(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        return dependency instanceof AppBarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
        setDimensions((CoordinatorLayout) child, child.getLayoutParams().width,
            parent.getHeight() - (int) getOffset(parent));
        return true;
    }

    @Override
    protected void layoutChild(final CoordinatorLayout parent, final View child, final int layoutDirection) {
        final List<View> dependencies = parent.getDependencies(child);
        final View header = findFirstDependency(dependencies);
        final Rect available = new Rect();
        if (header != null) {
            final CoordinatorLayout.LayoutParams lp =
                (CoordinatorLayout.LayoutParams) child.getLayoutParams();

            available.set(parent.getPaddingLeft() + lp.leftMargin, header.getBottom() + lp.topMargin,
                parent.getWidth() - parent.getPaddingRight() - lp.rightMargin,
                parent.getHeight() + header.getBottom() - parent.getPaddingBottom() - lp.bottomMargin);

            final Rect out = new Rect();
            GravityCompat.apply(resolveGravity(lp.gravity), child.getMeasuredWidth(),
                child.getMeasuredHeight(), available, out, layoutDirection);
            final int overlap = getOverlapPixelsForOffset();
            child.layout(out.left, out.top - overlap, out.right, parent.getHeight() - overlap);
        }
    }

    private int getOverlapPixelsForOffset() {
        return getOverlayTop() == 0 ? 0 : constrain((int) (1f * getOverlayTop()), 0, getOverlayTop());
    }

    private AppBarLayout findFirstDependency(List<View> views) {
        for (int i = 0, z = views.size(); i < z; i++) {
            View view = views.get(i);
            if (view instanceof AppBarLayout) {
                return (AppBarLayout) view;
            }
        }
        return null;
    }

    private static int constrain(int amount, int low, int high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private static int resolveGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? GravityCompat.START | Gravity.TOP : gravity;
    }

    private void setDimensions(CoordinatorLayout view, int width, int height) {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private float getOffset(CoordinatorLayout coordinatorLayout) {

        for (int i = 0; i < coordinatorLayout.getChildCount(); i++) {
            View child = coordinatorLayout.getChildAt(i);

            if (child instanceof AppBarLayout) {
                return child.getY() + child.getHeight();
            }
        }
        return 0;
    }
}