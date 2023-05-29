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

package com.protonvpn.android.redesign.recents.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsList(
    recents: List<RecentItemViewState>,
    onClickAction: (id: Long) -> Unit,
    onTogglePin: (item: RecentItemViewState) -> Unit,
    onRemoveAction: (item: RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(recents, key = { it.id }) { item ->
            RecentRow(
                item = item,
                onClick = { onClickAction(item.id) },
                onTogglePin = { onTogglePin(item) },
                onRemove = { onRemoveAction(item) },
                modifier = Modifier
                    .fillMaxSize()
                    .animateItemPlacement()
            )
        }
    }
}
