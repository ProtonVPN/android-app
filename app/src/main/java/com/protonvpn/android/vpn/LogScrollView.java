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
package com.protonvpn.android.vpn;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

public class LogScrollView extends ScrollView {

    private boolean mAutoScroll = true;

    public LogScrollView(Context context) {
        super(context);
    }

    public LogScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LogScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        /* disable auto-scrolling when the user starts scrolling around */
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mAutoScroll = false;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * Call this to move newly added content into view by scrolling to the bottom.
     * Nothing happens if auto-scrolling is disabled.
     */
    public void autoScroll() {
        if (mAutoScroll) {
            fullScroll(View.FOCUS_DOWN);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        /* if the user scrolls to the bottom we enable auto-scrolling again */
        if (t == getChildAt(getChildCount() - 1).getHeight() - getHeight()) {
            mAutoScroll = true;
        }
    }
}
