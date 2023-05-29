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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.redesign.recents.ui.RecentsList
import com.protonvpn.android.redesign.vpn.ui.VpnConnectionCard

class RecentsAndConnectionSample : SampleScreen("Connection+Recents", "connection_with_recents", false) {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        val viewModel: RecentsAndConnectionSampleViewModel = hiltViewModel()

        Column(modifier = modifier.padding(vertical = 16.dp)) {
            val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
            VpnConnectionCard(
                viewState = viewState.connectionCard,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onOpenPanelClick = {},
                onHelpClick = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            RecentsList(viewState.recents, viewModel::connectRecent, viewModel::togglePinned, viewModel::removeRecent)
        }
    }

}
