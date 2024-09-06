/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.base.ui

import kotlin.math.cos
import kotlin.math.sin

// r,g, and b in [0, 1]
fun rgbToHueInRadians(r: Float, g: Float, b: Float): Float {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var hueInDegrees: Float
    if (delta == 0f) {
        hueInDegrees = 0f
    } else {
        hueInDegrees = when (max) {
            r -> ((g - b) / delta) % 6
            g -> ((b - r) / delta) + 2
            else -> ((r - g) / delta) + 4
        }
        hueInDegrees *= 60f // Convert to degrees
        if (hueInDegrees < 0) {
            hueInDegrees += 360f // Ensure the hue is non-negative
        }
    }
    val hueInRadians = hueInDegrees * (Math.PI / 180).toFloat()
    return hueInRadians
}

// theta in radians
fun createHueRotationMatrix(theta: Float): FloatArray {
    val cosVal = cos(theta)
    val sinVal = sin(theta)
    val lumR = 0.213f
    val lumG = 0.715f
    val lumB = 0.072f
    return floatArrayOf(
        lumR + cosVal * (1 - lumR) + (sinVal * (-lumR)),
        lumG + cosVal * (-lumG) + sinVal * (-lumG),
        lumB + cosVal * (-lumB) + sinVal * (1 - lumB),
        0f,
        0f,

        lumR + cosVal * (-lumR) + sinVal * (0.143f),
        lumG + cosVal * (1 - lumG) + sinVal * (0.140f),
        lumB + cosVal * (-lumB) + sinVal * (-0.283f),
        0f,
        0f,

        lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)),
        lumG + cosVal * (-lumG) + sinVal * (lumG),
        lumB + cosVal * (1 - lumB) + sinVal * (lumB),
        0f,
        0f,

        0f, 0f, 0f, 1f, 0f,
        0f, 0f, 0f, 0f, 1f
    )
}