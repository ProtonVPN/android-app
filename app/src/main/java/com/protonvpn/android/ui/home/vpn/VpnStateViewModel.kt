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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnConnectionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class VpnStateViewModel @Inject constructor(
    private val vpnConnectionManager: VpnConnectionManager,
    trafficMonitor: TrafficMonitor
) : ViewModel() {

    val eventCollapseBottomSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCollapseBottomSheetLV = eventCollapseBottomSheet.asLiveData()

    val trafficStatus = trafficMonitor.trafficStatus

    val netShieldExpandStatus = MutableStateFlow(true)

    fun reconnect(context: Context) {
        vpnConnectionManager.reconnect(context)
    }

    fun disconnect() {
        vpnConnectionManager.disconnect()
    }

    fun disconnectAndClose() {
        ProtonLogger.log("Canceling connection")
        disconnect()
        eventCollapseBottomSheet.tryEmit(Unit)
    }

    fun onNetShieldExpandClicked() {
        netShieldExpandStatus.value = !netShieldExpandStatus.value
    }
}
