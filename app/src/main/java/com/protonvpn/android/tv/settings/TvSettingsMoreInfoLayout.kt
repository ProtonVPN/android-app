/*
 * Copyright (c) 2025 Proton AG
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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Cancel
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import me.proton.core.compose.theme.ProtonTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvSettingsMoreInfoLayout(
    title: String,
    paragraphs: Array<String>,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    val focusRequesters = paragraphs.map { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
    ) {
        TvSettingsSubHeadline(
            modifier = Modifier.padding(vertical = 16.dp),
            text = title,
        )

        if (focusRequesters.isNotEmpty()) {
            TvLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                itemsIndexed(paragraphs) { index, paragraph ->
                    Text(
                        modifier = Modifier
                            .focusRequester(focusRequesters[index])
                            .focusProperties {
                                left = Cancel
                                right = Cancel
                                up = if (index.minus(1) in focusRequesters.indices) {
                                    focusRequesters[index.minus(1)]
                                } else {
                                    Cancel
                                }
                                down = if (index.plus(1) in focusRequesters.indices) {
                                    focusRequesters[index.plus(1)]
                                } else {
                                    Cancel
                                }
                            }
                            .focusable(),
                        text = paragraph,
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textWeak,
                    )
                }
            }
        }
    }
}
