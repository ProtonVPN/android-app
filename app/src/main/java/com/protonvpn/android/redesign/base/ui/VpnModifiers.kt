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

import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay

// Adds modifier only if predicate returns true.
fun Modifier.optional(predicate: () -> Boolean, modifier: Modifier): Modifier =
    if (predicate()) this then modifier
    else this

/**
 * Draw composable with alpha mark the server/country/recent etc. is unavailable.
 */
fun Modifier.unavailableServerAlpha(
    isDisabled: Boolean
): Modifier =
    if (isDisabled) {
        alpha(0.3f)
    } else {
        this
    }

fun Modifier.clickableWithDebounce(
    debounceMs: Long = 500,
    action: () -> Unit,
): Modifier = composed {
    var clickEnabled by remember { mutableStateOf(true) }
    if (!clickEnabled) {
        LaunchedEffect(clickEnabled) {
            delay(debounceMs)
            clickEnabled = true
        }
    }
    this.clickable(onClick = {
        clickEnabled = false
        action()
    }, enabled = clickEnabled)
}