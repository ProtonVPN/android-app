/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.home_screen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCard
import com.protonvpn.android.redesign.vpn.ui.VpnStatusView

@Composable
fun HomeRoute() {
    HomeView()
}

@Composable
fun HomeView() {
    val viewModel: HomeViewModel = hiltViewModel()
    val cardViewState = viewModel.cardViewState.collectAsStateWithLifecycle().value

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        VpnStatusView(
            stateFlow = viewModel.vpnStateViewFlow,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val vpnUiDelegate = LocalVpnUiDelegate.current
            VpnConnectionCard(
                viewState = cardViewState,
                onConnect = { viewModel.connect(vpnUiDelegate) },
                onDisconnect = viewModel::disconnect,
                onOpenPanelClick = { },
                onHelpClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }
    }
}
