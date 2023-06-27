/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme

val ProtonTheme.MaxContentWidth: Dp get() = 800.dp

// On larger screens (window size class Medium and Expanded) there is additional content padding:
// MediumBaseContentPadding and ExpandedBaseContentPadding respectively.
val ProtonTheme.MediumBaseContentPadding: Dp get() = 56.dp
val ProtonTheme.ExpandedBaseContentPadding: Dp get() = 128.dp

fun ProtonTheme.getPaddingForWindowWidthClass(widthClass: WindowWidthSizeClass): Dp =
    when (widthClass) {
        WindowWidthSizeClass.Compact -> 0.dp
        WindowWidthSizeClass.Medium -> MediumBaseContentPadding
        WindowWidthSizeClass.Expanded -> ExpandedBaseContentPadding
        else -> 0.dp
    }
