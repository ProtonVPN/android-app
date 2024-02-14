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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.R
import com.protonvpn.android.redesign.app.ui.CoreNavigation
import com.protonvpn.android.redesign.app.ui.FullScreenError
import com.protonvpn.android.redesign.app.ui.FullScreenLoading
import com.protonvpn.android.redesign.app.ui.ServerLoadingViewModel
import com.protonvpn.android.redesign.app.ui.ServerLoadingViewModel.LoaderState
import com.protonvpn.android.redesign.app.ui.nav.RootNav

@Composable
fun VpnApp(
    modifier: Modifier = Modifier,
    coreNavigation: CoreNavigation,
) {
    val rootController = rememberNavController()
    val viewModel: ServerLoadingViewModel = hiltViewModel()

    when (viewModel.serverLoadingState.collectAsState().value) {
        LoaderState.Error -> {
            FullScreenError(
                errorTitle = stringResource(R.string.something_went_wrong),
                errorDescription = stringResource(R.string.error_server_list_load_failed),
                onRetry = { viewModel.updateServerList() }
            )
        }
        LoaderState.Loading -> {
            FullScreenLoading()
        }
        LoaderState.Loaded -> {
            RootNav(rootController).NavHost(
                modifier = modifier,
                coreNavigation = coreNavigation
            )
        }
        null -> { /* Nothing */ }
    }
}
