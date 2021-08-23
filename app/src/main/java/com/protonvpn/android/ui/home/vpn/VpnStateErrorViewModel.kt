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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.components.VPNException
import com.protonvpn.android.utils.Log
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.RetryInfo
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class VpnStateErrorViewModel @Inject constructor(
    stateMonitor: VpnStateMonitor,
    private val vpnConnectionManager: VpnConnectionManager
) : ViewModel() {

    val errorMessage: Flow<Int> = stateMonitor.status.mapNotNull {
        val state = it.state
        if (state is VpnState.Error) {
            mapToErrorMessage(state)
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

    @StringRes
    private fun mapToErrorMessage(error: VpnState.Error): Int {
        // Note: this only maps those errors for which VpnStateFragment shows VpnStateErrorFragment
        return when (error.type) {
            ErrorType.PEER_AUTH_FAILED -> {
                // Note: logging errors in UI code is not reliable.
                Log.exception(VPNException("Peer Auth: Verifying gateway authentication failed"))
                R.string.error_peer_auth_failed
            }
            ErrorType.UNREACHABLE -> {
                Log.exception(VPNException("Gateway is unreachable"))
                R.string.error_server_unreachable
            }
            else -> {
                Log.exception(VPNException("Unspecified failure while connecting"))
                R.string.error_generic
            }
        }
    }
}
