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

package com.protonvpn.android.redesign.uicatalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.RecentRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
class RecentsSample : SampleScreen("Recents", "recents", needsScroll = false) {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(modifier = modifier.padding(vertical = 16.dp)) {
            var recents by remember { mutableStateOf(listOf(item1, item2, item3, item4, item5, item6)) }
            val coroutineScope = rememberCoroutineScope()

            SamplesSectionLabel(
                "Fake recents\n(don't pay attention to ordering: items always move to the top and bottom)",
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn {
                itemsIndexed(recents, key = { _, item -> item.id }) { index, item ->
                    RecentRow(
                        item = item,
                        onClick = {},
                        onTogglePin = {
                            coroutineScope.launch {
                                delay(100) // Simulate DB overhead.
                                val recentsWithoutItem = recents - item
                                if (item.isPinned) {
                                    recents = recentsWithoutItem + item.copy(isPinned = false)
                                } else {
                                    recents = listOf(item.copy(isPinned = true)) + recentsWithoutItem
                                }
                            }
                        },
                        onRemove = {
                            coroutineScope.launch {
                                delay(100) // Simulate DB overhead.
                                recents = recents.filterNot { it.id == item.id }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .animateItemPlacement()
                    )
                    if (index < recents.lastIndex) {
                        VpnDivider()
                    }
                }
            }
        }
    }

    companion object {

        private val item1 = RecentItemViewState(
            id = 0,
            ConnectIntentViewState(
                exitCountry = CountryId("ch"),
                entryCountry = CountryId.sweden,
                isSecureCore = true,
                secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(null, CountryId.sweden),
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = false,
            availability = RecentAvailability.ONLINE
        )

        private val item2 = RecentItemViewState(
            id = 1,
            ConnectIntentViewState(
                exitCountry = CountryId("pl"),
                entryCountry = null,
                isSecureCore = false,
                secondaryLabel = null,
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = true,
            availability = RecentAvailability.ONLINE
        )

        private val item3 = RecentItemViewState(
            id = 2,
            ConnectIntentViewState(
                exitCountry = CountryId("lt"),
                entryCountry = null,
                isSecureCore = false,
                secondaryLabel = null,
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = false,
            availability = RecentAvailability.ONLINE
        )

        private val item4 = RecentItemViewState(
            id = 3,
            ConnectIntentViewState(
                exitCountry = CountryId("ch"),
                entryCountry = null,
                isSecureCore = false,
                secondaryLabel = null,
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = false,
            availability = RecentAvailability.AVAILABLE_OFFLINE
        )

        private val item5 = RecentItemViewState(
            id = 4,
            ConnectIntentViewState(
                exitCountry = CountryId("ch"),
                entryCountry = null,
                isSecureCore = false,
                secondaryLabel = ConnectIntentSecondaryLabel.RawText("Zurich"),
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = false,
            availability = RecentAvailability.UNAVAILABLE_PLAN
        )

        private val item6 = RecentItemViewState(
            id = 5,
            ConnectIntentViewState(
                exitCountry = CountryId("us"),
                entryCountry = null,
                isSecureCore = false,
                secondaryLabel = null,
                serverFeatures = emptySet()
            ),
            isPinned = false,
            isConnected = false,
            availability = RecentAvailability.UNAVAILABLE_PROTOCOL
        )
    }
}
