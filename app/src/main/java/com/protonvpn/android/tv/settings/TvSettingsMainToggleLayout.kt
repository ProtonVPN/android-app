/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListScope
import com.protonvpn.android.tv.ui.TvUiConstants

@Composable
fun TvSettingsMainToggleLayout(
    title: String,
    toggleValue: Boolean,
    onToggled: () -> Unit,
    modifier: Modifier = Modifier,
    titleImageRes: Int? = null,
    toggleLabel: String = title,
    additionalListContent: TvLazyListScope.() -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
    ) {
        if (titleImageRes == null) {
            TvSettingsHeader(
                title = title,
                modifier = Modifier.padding(top = TvUiConstants.ScreenPaddingVertical)
            )
        } else {
            TvSettingsHeader(
                title = title,
                imageRes = titleImageRes,
                modifier = Modifier.padding(top = TvUiConstants.ScreenPaddingVertical)
            )
        }

        TvLazyColumn(
            modifier = Modifier.padding(top = 28.dp)
        ) {
            item {
                TvSettingsItemSwitch(
                    title = toggleLabel,
                    checked = toggleValue,
                    onClick = onToggled,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }

            additionalListContent()
        }
    }
}
