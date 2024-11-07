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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay

// Adds modifier only if predicate returns true.
fun Modifier.optional(predicate: () -> Boolean, modifier: Modifier): Modifier =
    if (predicate()) this then modifier
    else this

fun Modifier.thenNotNull(optionalModifier: Modifier?): Modifier =
    if (optionalModifier != null) this then optionalModifier
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

fun Modifier.clickableNoMultiClick(
    disabledForMs: Long = 500,
    action: () -> Unit,
): Modifier = composed {
    val (enabledState, wrappedAction) = preventMultiClick(disabledForMs, action)
    this.clickable(onClick = wrappedAction, enabled = enabledState.value)
}

@Composable
fun preventMultiClick(
    disabledForMs: Long = 500L,
    action: () -> Unit
): Pair<MutableState<Boolean>, () -> Unit> {
    val enabled = remember { mutableStateOf(true) }
    if (!enabled.value) {
        LaunchedEffect(true) {
            delay(disabledForMs)
            enabled.value = true
        }
    }
    return enabled to {
        if (enabled.value) {
            enabled.value = false
            action()
        }
    }
}

@Stable
fun Modifier.rtlMirror(): Modifier = composed {
    if (LocalLayoutDirection.current == LayoutDirection.Rtl)
        scale(scaleX = -1f, scaleY = 1f)
    else
        this
}
