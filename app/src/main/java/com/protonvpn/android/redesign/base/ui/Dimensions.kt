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

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.window.layout.WindowMetricsCalculator
import me.proton.core.compose.theme.ProtonTheme

private val ProtonTheme.MaxContentWidth: Dp get() = 800.dp

// On larger screens (window size class Medium and Expanded) there is additional content padding:
// MediumBaseContentPadding and ExpandedBaseContentPadding respectively.
private val ProtonTheme.MediumBaseContentPadding: Dp get() = 56.dp
private val ProtonTheme.ExpandedBaseContentPadding: Dp get() = 128.dp

@Composable
private fun calculateWindowSize(activity: Activity): DpSize {
    // Copied from androidx.compose.material3.windowsizeclass.calculateWindowSizeClass.

    // Observe view configuration changes and recalculate the size class on each change. We can't
    // use Activity#onConfigurationChanged as this will sometimes fail to be called on different
    // API levels, hence why this function needs to be @Composable so we can observe the
    // ComposeView's configuration changes.
    LocalConfiguration.current
    val density = LocalDensity.current
    val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
    return with(density) { metrics.bounds.toComposeRect().size.toDpSize() }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun ProtonTheme.extraPaddingForWindowSize(size: DpSize): Dp {
    val windowSizeClass = WindowSizeClass.calculateFromSize(size)
    val extraPadding = when ( windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.dp
        WindowWidthSizeClass.Medium -> MediumBaseContentPadding
        WindowWidthSizeClass.Expanded -> ExpandedBaseContentPadding
        else -> 0.dp
    }
    // Subtract 16.dp which is the regular screen padding.
    val maxExtraWidthPadding = (size.width - ProtonTheme.MaxContentWidth) / 2 - 16.dp
    return max(extraPadding, maxExtraWidthPadding)
}

@Composable
fun largeScreenContentPadding(): Dp =
    if (LocalInspectionMode.current) {
        0.dp
    } else {
        val activity = LocalActivity.current!!
        ProtonTheme.extraPaddingForWindowSize(calculateWindowSize(activity))
    }

// Set additional padding (beyond the default 16.dp) for the content based on the activity's dimensions.
fun Modifier.largeScreenContentPadding() = composed {
    val extraScreenPadding = com.protonvpn.android.redesign.base.ui.largeScreenContentPadding()
    this.padding(horizontal = extraScreenPadding)
}
