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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.redesign.countries.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ModalBottomSheetWithBackNavigation(
    modifier: Modifier,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetState: SheetState = rememberModalBottomSheetState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    onNavigateBack: suspend (suspend () -> Unit) -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler {
        scope.launch {
            onNavigateBack { sheetState.hide() }
        }
    }

    val requester = remember { FocusRequester() }
    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        sheetState = sheetState,
        modifier = modifier
            .focusRequester(requester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp && !it.nativeKeyEvent.isCanceled) {
                    backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
                    return@onPreviewKeyEvent true
                }
                return@onPreviewKeyEvent false
            },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            securePolicy = SecureFlagPolicy.Inherit,
            isFocusable = true,
        ),
        content = content
    )

    LaunchedEffect(Unit) {
        delay(100) //TODO: without delay it crashes after activity restore, need investigation
        requester.requestFocus()
    }
}
