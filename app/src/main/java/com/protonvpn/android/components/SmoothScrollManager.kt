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
package com.protonvpn.android.components

import android.content.Context
import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.utils.ViewUtils.toPx

class SmoothScrollManager(private val context: Context) : LinearLayoutManager(context) {

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State?,
        position: Int
    ) {

        val smoothScroller = object : LinearSmoothScroller(context) {

            override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                return this@SmoothScrollManager.computeScrollVectorForPosition(targetPosition)
            }

            override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
                val layoutManager = layoutManager
                if (layoutManager == null || !layoutManager.canScrollVertically()) {
                    return 0
                }
                val params = view.layoutParams as RecyclerView.LayoutParams
                val top = layoutManager.getDecoratedTop(view) - params.topMargin
                val bottom = layoutManager.getDecoratedBottom(view) + params.bottomMargin
                val start = layoutManager.paddingTop
                val end = layoutManager.height - layoutManager.paddingBottom
                var pixelsToAnimate = calculateDtToFit(top, bottom, start, end, snapPreference)
                val overlap = 26.toPx()
                if (pixelsToAnimate != 0) {
                    pixelsToAnimate -= overlap
                }
                return pixelsToAnimate
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return 200f / displayMetrics.densityDpi
            }
        }

        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }
}
