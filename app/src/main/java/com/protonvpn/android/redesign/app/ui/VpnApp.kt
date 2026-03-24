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

package com.protonvpn.android.redesign.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel.LoaderState
import com.protonvpn.android.ui.noconnections.NoConnectionsScreen

@Composable
fun VpnApp(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    appContent: @Composable (Modifier) -> Unit
) {
    val viewModel: VpnAppViewModel = hiltViewModel()

    when (val state = viewModel.loadingState.collectAsState().value) {
        is LoaderState.Error -> {
            NoConnectionsScreen(
                state = state,
                onRefresh = state.retryAction,
                onLogout = onSignOut,
                modifier = modifier,
            )
        }

        LoaderState.Loading -> {
            FullScreenLoading(modifier = modifier)
        }

        LoaderState.Loaded -> {
            appContent(modifier)
        }

        null -> Unit
    }
}
