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

package com.protonvpn.android.profiles.ui.customdns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.profiles.ui.CreateEditProfileViewModel
import com.protonvpn.android.redesign.settings.ui.customdns.CustomDnsActions
import com.protonvpn.android.redesign.settings.ui.customdns.DnsSettingsScreen
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ProfileCustomDnsRoute(
    viewModel: CreateEditProfileViewModel,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
            .navigationBarsPadding()
    ) {
        val dataSource = viewModel.customDnsHelper
        val viewState = dataSource.customDnsSettingState.collectAsStateWithLifecycle(null).value
        if (viewState != null) {
            DnsSettingsScreen(
                viewState = viewState,
                events = dataSource.events.receiveAsFlow(),
                actions = CustomDnsActions(
                    onClose = onClose,
                    onAddDns = viewModel::validateAndAddDnsAddress,
                    onAddDnsTextChanged = dataSource::onAddDnsTextChanged,
                    removeDns = viewModel::removeDnsAddress,
                    toggleSetting = viewModel::toggleCustomDns,
                    updateDnsList = viewModel::updateDnsList,
                    openAddDnsScreen = dataSource::openAddDnsScreen,
                    closeAddDnsScreen = dataSource::closeAddDnsScreen,
                    showReconnectDialog = null,
                ),
            )
        }
    }
} 