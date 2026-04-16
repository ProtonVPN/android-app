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

import androidx.compose.ui.graphics.Color

fun mixDstOver(src: Color, colorDst: Color, alphaDst: Float): Color {
    val ad = alphaDst.coerceIn(0f, 1f)
    val inv = 1f - ad
    fun mix(chDst: Float, chSrc: Float) = (chDst * ad + chSrc * inv).coerceIn(0f, 1f)

    return Color(
        red = mix(colorDst.red, src.red),
        green = mix(colorDst.green, src.green),
        blue = mix(colorDst.blue, src.blue),
        alpha = mix(colorDst.alpha, src.alpha),
    )
}
