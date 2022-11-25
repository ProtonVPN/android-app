/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

@HiltViewModel
class VpnStateErrorViewModel @Inject constructor(
    app: Application,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val vpnConnectionManager: VpnConnectionManager
) : AndroidViewModel(app) {

    val errorMessage: Flow<String> = vpnStatusProviderUI.status.mapNotNull {
        val state = it.state
        if (state is VpnState.Error) {
            state.type.mapToErrorMessage(getApplication(), state.description)
        } else {
            null
        }
    }

    val retryInfo: Flow<RetryInfo?> = flow {
        while(vpnConnectionManager.retryInfo != null) {
            val retryInfo = vpnConnectionManager.retryInfo!!
            emit(retryInfo)
            delay(1000)
        }
        emit(null)
        vpnConnectionManager.retryInfo?.timeoutSeconds
    }
}
