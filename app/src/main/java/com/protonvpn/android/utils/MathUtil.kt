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

package com.protonvpn.android.utils

import android.graphics.RectF

fun RectF.scale(sx: Float, sy: Float) =
        RectF(left * sx, top * sy, right * sx, bottom * sy)

/* Translates rect to coordinate system based on some other rectangle (width and height of that
   rect in this coordinate system have value 1. */
fun RectF.inCoordsOf(o: RectF) = RectF(
    left - o.left,
    top - o.top,
    right - o.left,
    bottom - o.top).scale(1f / o.width(), 1f / o.height())

fun Int.hasFlag(flag: Int) = this and flag == flag
