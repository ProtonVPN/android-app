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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.isVirtualLocation
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.servers.GetStreamingServices
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.StreamingService
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectionDetailsViewModel @Inject constructor(
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val vpnStateMonitor: VpnStateMonitor,
    private val serverManager2: ServerManager2,
    private val serverListUpdaterPrefs: ServerListUpdater,
    private val currentUser: CurrentUser,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val trafficMonitor: TrafficMonitor,
    private val streamingServices: GetStreamingServices,
) : ViewModel() {

    sealed interface ConnectionDetailsViewState {
        data class Connected(
            val entryIp: String,
            val vpnIp: String,
            val entryCountryId: CountryId?,
            val exitCountryId: CountryId,
            val trafficHistory: List<TrafficUpdate>,
            val connectIntentViewState: ConnectIntentViewState,
            val serverDisplayName: String,
            val serverCity: String?,
            val serverGatewayName: String?,
            val serverLoad: Float,
            @StringRes val protocolDisplay: Int? = null,
            val serverFeatures: ServerFeatures,
        ) : ConnectionDetailsViewState

        object Close : ConnectionDetailsViewState
    }

    data class ServerFeatures(
        val hasTor: Boolean = false,
        val hasP2P: Boolean = false,
        val hasSecureCore: Boolean = false,
        val smartRouting: SmartRouting? = null,
        val streamingServices: List<StreamingService>? = null
    ) {
        fun hasAnyFeatures(): Boolean {
            return hasTor || hasP2P || hasSecureCore || smartRouting != null || !streamingServices.isNullOrEmpty()
        }
    }
    data class SmartRouting(val entryCountry: CountryId, val exitCountry: CountryId)

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
            emptyList(),
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.fastest, null), null, emptySet()),
            "",
            "",
            null,
            0F,
            serverFeatures = ServerFeatures()
        )
    )

    private fun createConnectedViewState(connectionParams: ConnectionParams): Flow<ConnectionDetailsViewState> {
        val streamingList = streamingServices.invoke(connectionParams.server.entryCountry)

        // connectionParams.server is initialized at connection attempt time, and will be outdated after loads update
        val serverFlow = serverManager2.serverListVersion
            .map { serverManager2.getServerById(connectionParams.server.serverId) ?: connectionParams.server }
            .distinctUntilChanged()

        return combine(
            currentUser.vpnUserFlow,
            vpnStateMonitor.exitIp,
            serverListUpdaterPrefs.ipAddress,
            trafficMonitor.trafficHistory.asFlow(),
            serverFlow,
        ) { vpnUser, exitIp, userIp, trafficHistory, server ->
            val connectIntent = connectionParams.connectIntent as ConnectIntent
            val vpnIp = exitIp ?: ""
            val protocol = connectionParams.protocolSelection?.displayName ?: 0
            ConnectionDetailsViewState.Connected(
                entryIp = userIp,
                vpnIp = vpnIp,
                entryCountryId = if (server.isSecureCoreServer) CountryId(server.entryCountry) else null,
                exitCountryId = CountryId(server.exitCountry),
                trafficHistory = trafficHistory,
                connectIntentViewState = getConnectIntentViewState(
                    connectIntent,
                    vpnUser?.isFreeUser == true,
                    server
                ),
                serverDisplayName = server.serverName,
                serverCity = server.displayCity,
                serverGatewayName = server.gatewayName,
                serverLoad = server.load,
                protocolDisplay = protocol,
                serverFeatures = ServerFeatures(
                    server.isTor,
                    server.isP2pServer,
                    server.isSecureCoreServer,
                    smartRouting = if (server.hostCountry != null && server.isVirtualLocation)
                        SmartRouting(entryCountry = CountryId(server.hostCountry), exitCountry = CountryId(server.exitCountry))
                    else
                        null,
                    streamingServices = if (server.isStreamingServer) streamingList else null
                )
            )
        }
    }
}
