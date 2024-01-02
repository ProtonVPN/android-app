/*
 * Copyright (c) 2023. Proton Technologies AG
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

package com.protonvpn.android.redesign.home_screen.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectionDetailsViewModel @Inject constructor(
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val vpnStateMonitor: VpnStateMonitor,
    private val serverListUpdaterPrefs: ServerListUpdater,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val trafficMonitor: TrafficMonitor
) : ViewModel() {

    sealed interface ConnectionDetailsViewState {
        data class Connected(
            val entryIp: String,
            val vpnIp: String,
            val entryCountryId: CountryId?,
            val exitCountryId: CountryId,
            val trafficUpdate: TrafficUpdate?,
            val connectIntentViewState: ConnectIntentViewState,
            val serverDisplayName: String,
            val serverCity: String?,
            val serverGatewayName: String?,
            val serverLoad: Float,
            @StringRes val protocolDisplay: Int? = null
        ) : ConnectionDetailsViewState

        object Close : ConnectionDetailsViewState
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionDetailsViewState = vpnStatusProviderUI.uiStatus.flatMapLatest {
        if (it.state is VpnState.Connected) {
            createConnectedViewState(requireNotNull(it.connectionParams))
        } else {
            flowOf(ConnectionDetailsViewState.Close)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = ConnectionDetailsViewState.Connected(
            "",
            "",
            CountryId.fastest,
            CountryId.fastest,
            null,
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.fastest, null), null, emptySet()),
            "",
            "",
            null,
            0F
        )
    )

    private fun createConnectedViewState(connectionParams: ConnectionParams) =
        combine(
            vpnStateMonitor.exitIp,
            serverListUpdaterPrefs.ipAddress,
            trafficMonitor.trafficStatus.asFlow()
        ) { exitIp, userIp, trafficUpdate ->
            val connectIntent = connectionParams.connectIntent as ConnectIntent
            val server = connectionParams.server
            val vpnIp = exitIp ?: ""
            val protocol = connectionParams.protocolSelection?.displayName ?: 0
            ConnectionDetailsViewState.Connected(
                entryIp = userIp,
                vpnIp = vpnIp,
                entryCountryId = if (server.isSecureCoreServer) CountryId(server.entryCountry) else null,
                exitCountryId = CountryId(server.exitCountry),
                trafficUpdate = trafficUpdate,
                connectIntentViewState = getConnectIntentViewState(connectIntent, server),
                serverDisplayName = server.serverName,
                serverCity = server.displayCity,
                serverGatewayName = server.gatewayName,
                serverLoad = server.load,
                protocolDisplay = protocol
            )
        }
}
