/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui

import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.protonvpn.android.R

object ServerLoadColor {

    @JvmStatic
    @ColorRes
    fun getColorId(serverLoad: Float, isOnline: Boolean = true): Int =
        if (isOnline) when {
            serverLoad <= 75f -> R.color.serverLoadLow
            serverLoad <= 90f -> R.color.serverLoadMedium
            else -> R.color.serverLoadHigh
        } else {
            R.color.inMaintenance
        }

    @JvmStatic
    @ColorInt
    fun getColor(view: View, serverLoad: Float, isOnline: Boolean = true): Int =
        ContextCompat.getColor(view.context, getColorId(serverLoad, isOnline))
}
