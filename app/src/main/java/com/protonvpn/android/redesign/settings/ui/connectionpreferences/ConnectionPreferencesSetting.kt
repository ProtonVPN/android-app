/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.redesign.settings.ui.connectionpreferences

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.SubSettingWithLazyContent
import com.protonvpn.android.redesign.settings.ui.excludedlocations.ExcludedLocationsViewModel.ExcludedLocationUiItem

@Composable
fun ConnectionPreferencesSetting(
    state: SettingViewState.ConnectionPreferencesState,
    onClose: () -> Unit,
    onDefaultConnectionClick: () -> Unit,
    onExcludeLocationClick: () -> Unit,
    onDeleteExcludedLocationClick: (ExcludedLocationUiItem.Location) -> Unit,
    onExcludedLocationsFeatureDiscovered: () -> Unit,
    onUpsellClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SubSettingWithLazyContent(
        modifier = modifier,
        title = stringResource(id = R.string.settings_connection_preferences_title),
        onClose = onClose,
        snackbarHostState = snackbarHostState,
    ) {
        if (state.isFreeUser) {
            ConnectionPreferencesFreeContent(
                modifier = Modifier.fillMaxWidth(),
                onDefaultConnectionClick = onUpsellClick,
                onExcludeLocationClick = onUpsellClick,
            )
        } else {
            ConnectionPreferencesPaidContent(
                modifier = Modifier.fillMaxWidth(),
                state = state,
                onDefaultConnectionClick = onDefaultConnectionClick,
                onExcludeLocationClick = onExcludeLocationClick,
                onDeleteExcludedLocationClick = onDeleteExcludedLocationClick,
                onExcludedLocationsFeatureDiscovered = onExcludedLocationsFeatureDiscovered,
            )
        }
    }
}
