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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.GatewayIndicator
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.headlineNorm

@Composable
fun ServerGroupsBottomSheet(
    modifier: Modifier,
    screen: SubScreenState,
    onNavigateBack: suspend (suspend () -> Unit) -> Unit,
    onNavigateToItem: (ServerGroupItemState) -> Unit,
    onItemClicked: (ServerGroupItemState) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val countryViewStateHolder = rememberSaveableStateHolder()
    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels.dp / LocalDensity.current.density
    val halfScreenHeight = screenHeight / 2

    ModalBottomSheetWithBackNavigation(
        modifier = modifier,
        onNavigateBack = { onHide ->
            // Forget the state of current screen when navigating away back from it
            countryViewStateHolder.removeState(screen.savedState.rememberStateKey)
            onNavigateBack(onHide)
        },
        onClose = onClose,
        sheetState = sheetState,
        scope = scope
    ) {
        // Remembers the state of the country view for back navigation.
        countryViewStateHolder.SaveableStateProvider(screen.savedState.rememberStateKey) {
            BottomSheetScreen(
                screen = screen,
                onItemOpen = onNavigateToItem,
                onItemClick = onItemClicked,
                modifier = Modifier.heightIn(min = halfScreenHeight)
            )
        }
    }
}

@Composable
private fun BottomSheetScreen(
    modifier: Modifier = Modifier,
    screen: SubScreenState,
    onItemOpen: (ServerGroupItemState) -> Unit,
    onItemClick: (ServerGroupItemState) -> Unit,
) {
    val countryId = screen.savedState.filter.country
    val gatewayName = screen.savedState.filter.gatewayName
    Column(modifier = modifier) {
        BottomSheetTitleRow(
            isGateway = screen.savedState.type == SubScreenType.GatewayServers,
            countryId = countryId,
            gatewayName = gatewayName,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )
        if (screen.filterButtons != null && screen.allLabelRes != null) {
            FiltersRow(buttonActions = screen.filterButtons, allLabelRes = screen.allLabelRes)
            Spacer(modifier = Modifier.size(8.dp))
        }
        LazyColumn {
            screen.items.forEach { item ->
                item {
                    ServerGroupItem(item, onItemOpen, onItemClick)
                    VpnDivider()
                }
            }
        }
    }
}

@Composable
private fun BottomSheetTitleRow(
    isGateway: Boolean,
    countryId: CountryId?,
    gatewayName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isGateway) {
            GatewayIndicator(country = countryId)
        } else {
            Flag(exitCountry = countryId ?: CountryId.fastest)
        }
        Text(
            text = countryId?.label() ?: gatewayName ?: "",
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            style = ProtonTheme.typography.headlineNorm
        )
    }
}
