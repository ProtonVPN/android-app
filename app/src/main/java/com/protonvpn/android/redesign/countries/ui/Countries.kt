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

package com.protonvpn.android.redesign.countries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents

@Composable
fun CountriesRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val viewModel: CountriesViewModel = hiltViewModel()
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val mainState = viewModel.stateFlow.collectAsStateWithLifecycle().value
        val subScreenState = viewModel.subScreenStateFlow.collectAsStateWithLifecycle().value
        ServerGroupsWithToolbar(
            mainState,
            subScreenState,
            onNavigateToHomeOnConnect,
            onNavigateToSearch,
            ServerGroupsActions(
                setLocale = { viewModel.localeFlow.value = it },
                onNavigateBack = viewModel::onNavigateBack,
                onClose = viewModel::onClose,
                onItemOpen = viewModel::onItemOpen,
                onItemConnect = viewModel::onItemConnect
            ),
            R.string.countries_title
        )
    }
}