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

package com.protonvpn.android.redesign.settings.ui.customdns

import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import kotlinx.coroutines.flow.Flow

class GlobalDnsSettingsDataSource(
    private val viewModel: CustomDnsViewModel,
) : DnsSettingsDataSource() {

    override val dnsViewState: Flow<SettingsViewModel.SettingViewState.CustomDns?>
        get() = viewModel.dnsViewStateFlow

    override val isConnected: Flow<Boolean>
        get() = viewModel.isConnected

    override fun toggleEnabled() {
        viewModel.toggleCustomDns()
    }
    
    override fun updateDnsList(newList: List<String>) {
        viewModel.updateCustomDnsList(newList)
    }
    
    override fun performAddDnsAddress(address: String) {
        viewModel.addNewDns(address)
    }

    override fun performRemoveDnsAddress(address: String, position: Int) {
        viewModel.removeDnsItem(address)
    }

    override fun performUndoRemoval(undoData: UndoSnackbarData) {
        viewModel.undoRemoval(undoData)
    }

    override fun shouldShowNetShieldConflict(): Boolean {
        // TODO address for global profiles
        return false
    }
} 