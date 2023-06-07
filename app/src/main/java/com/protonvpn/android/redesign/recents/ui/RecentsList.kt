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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsList(
    viewState: RecentsListViewState,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onOpenPanelClicked: () -> Unit,
    onHelpClicked: () -> Unit,
    onRecentClicked: (id: Long) -> Unit,
    onRecentPinToggle: (item: RecentItemViewState) -> Unit,
    onRecentRemove: (item: RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            VpnConnectionCard(
                viewState = viewState.connectionCard,
                onConnect = onConnectClicked,
                onDisconnect = onDisconnectClicked,
                onOpenPanelClick = onOpenPanelClicked,
                onHelpClick = onHelpClicked,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        if (viewState.recents.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.recents_headline),
                    style = ProtonTheme.typography.captionWeak,
                    modifier = Modifier.
                        padding(top = 24.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
        items(viewState.recents, key = { it.id }) { item ->
            RecentRow(
                item = item,
                onClick = { onRecentClicked(item.id) },
                onTogglePin = { onRecentPinToggle(item) },
                onRemove = { onRecentRemove(item) },
                modifier = Modifier
                    .fillMaxSize()
                    .animateItemPlacement()
            )
        }
    }
}
