/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.tv.main

import android.graphics.RectF
import kotlin.math.max

// Map region defined in coordinates where width is fixed as 1f
data class MapRegion(val x: Float, val y: Float, val w: Float, val h: Float) {

    fun withPadding(dx: Float = 0f, dy: Float = 0f) = MapRegion(x - dx, y - dy, w + 2 * dx, h + 2 * dy)
    fun withPadding(d: Float) = withPadding(d, d)

    fun toRectF() = RectF(x, y, x + w, y + h)

    // grows region to have given aspect ratio
    fun expandToAspectRatio(viewportNormalH: Float, centerBias: Float = 0f): MapRegion {
        val normalH = h / w
        return if (viewportNormalH > normalH) {
            val newH = w * viewportNormalH
            val dh = newH - h
            MapRegion(x, y - dh * centerBias, w, newH)
        } else {
            val newW = h / viewportNormalH
            val dw = newW - w
            MapRegion(x - dw * centerBias, y, newW, h)
        }
    }

    // shifts region to fit in given bounds if possible (not too big)
    fun shiftToMapBounds(maxY: Float): MapRegion {
        var (x, y, w, h) = this
        if (x + w > 1f)
            x = 1f - w
        if (y + h > maxY)
            y = maxY - h
        if (x < 0f)
            x = 0f
        if (y < 0f)
            y = 0f
        return MapRegion(x, y, w, h)
    }

    // ensures that region is wide enough keeping original aspect ratio
    fun minWidth(minW: Float) =
        if (w < minW) {
            val scale = minW / w
            val dw = minW - w
            val newH = h * scale
            val dh = newH - h
            MapRegion(x - dw / 2, y - dh / 2, minW, newH)
        } else
            this
}
