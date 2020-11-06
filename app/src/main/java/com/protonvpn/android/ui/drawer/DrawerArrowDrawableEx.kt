/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.content.ContextCompat
import com.protonvpn.android.R

class DrawerArrowDrawableEx(val context: Context) : DrawerArrowDrawable(context) {

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorAccent)
    }

    var showIndicator: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (showIndicator) {
            val indicatorSize = context.resources.getDimension(R.dimen.new_indicator_size)
            canvas.drawOval(bounds.width() - indicatorSize, 0f, bounds.width().toFloat(),
                    indicatorSize, indicatorPaint)
        }
    }
}
