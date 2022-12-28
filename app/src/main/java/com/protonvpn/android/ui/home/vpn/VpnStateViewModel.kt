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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class VpnStateViewModel @Inject constructor(
    private val vpnConnectionManager: VpnConnectionManager,
    trafficMonitor: TrafficMonitor
) : ViewModel() {

    val eventCollapseBottomSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCollapseBottomSheetLV = eventCollapseBottomSheet.asLiveData()

    val trafficStatus = trafficMonitor.trafficStatus

    val netShieldExpandStatus = MutableStateFlow(false)
    val bottomSheetFullyExpanded = MutableLiveData(false)

    fun reconnect(vpnUiDelegate: VpnUiDelegate) {
        vpnConnectionManager.reconnectWithCurrentParams(vpnUiDelegate)
    }

    fun disconnect(trigger: DisconnectTrigger) {
        ProtonLogger.log(UiDisconnect, trigger.description)
        vpnConnectionManager.disconnect(trigger)
    }

    fun disconnectAndClose(trigger: DisconnectTrigger) {
        disconnect(trigger)
        eventCollapseBottomSheet.tryEmit(Unit)
    }

    fun onNetShieldExpandClicked() {
        netShieldExpandStatus.value = !netShieldExpandStatus.value
    }

    fun onBottomStateChanges(newState: Int) {
        bottomSheetFullyExpanded.value = newState == BottomSheetBehavior.STATE_EXPANDED
    }
}
