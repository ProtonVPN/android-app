/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.utils

object ColorUtils {
    /**
     * A simplified "DST over" operator.
     * Operates on non-premultiplied values, assumes the source alpha is 255.
     */
    @Suppress("MagicNumber")
    fun mixDstOver(src: Int, colorDst: Int, alphaDst: Int): Int {
        val cd = colorDst.toFloat() / 255
        val ad = alphaDst.toFloat() / 255
        val s = src.toFloat() / 255
        val r = cd * ad + (1f - ad) * s
        return (r * 255).toInt()
    }

    @Suppress("MagicNumber")
    fun combineArgb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) + (r shl 16) + (g shl 8) + b
}
