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

package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment

@Composable
fun ProfilesRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
) {
    val viewModel : ProfilesViewModel = hiltViewModel()
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val state = viewModel.state.collectAsStateWithLifecycle().value
        val selectedProfile = viewModel.selectedProfile.collectAsStateWithLifecycle().value
        if (state != null) {
            val context = LocalContext.current
            val uiDelegate = LocalVpnUiDelegate.current
            val navigateToUpsell = { CarouselUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context) }
            Profiles(
                state = state,
                onConnect = { profile ->
                    viewModel.onConnect(profile, uiDelegate, onNavigateToHomeOnConnect, navigateToUpsell)
                },
                onSelect = { profile ->
                    viewModel.onSelect(profile)
                },
            )
        }

        if (selectedProfile != null) {
            ProfileBottomSheet(
                profile = selectedProfile,
                onClose = viewModel::onProfileClose,
                onProfileDelete = viewModel::onProfileDelete,
            )
        }
    }
}