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
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VpnStateViewModel @Inject constructor(
    private val vpnConnectionManager: VpnConnectionManager,
    trafficMonitor: TrafficMonitor,
    private val userData: UserData,
    currentUser: CurrentUser
) : ViewModel() {

    val eventCollapseBottomSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCollapseBottomSheetLV = eventCollapseBottomSheet.asLiveData()

    val trafficStatus = trafficMonitor.trafficStatus
    val netShieldViewState: StateFlow<NetShieldViewState> =
        combine(
            userData.netShieldStateFlow,
            vpnConnectionManager.netShieldStats,
            currentUser.vpnUserFlow
        ) { state, stats, user ->
            if (user == null || user.isFreeUser) NetShieldViewState.UpgradeBanner
            else {
                NetShieldViewState.NetShieldState(state, stats)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NetShieldViewState.UpgradeBanner)

    val netShieldExpandStatus = MutableStateFlow(false)
    val bottomSheetFullyExpanded = MutableLiveData(false)

    fun getCurrentNetShield() = userData.netShieldStateFlow

    fun setNetShieldProtocol(netShieldProtocol: NetShieldProtocol) {
        userData.setNetShieldProtocol(netShieldProtocol)
    }

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
