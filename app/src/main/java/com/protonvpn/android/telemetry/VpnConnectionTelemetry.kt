/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.telemetry

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private const val MEASUREMENT_GROUP = "vpn.any.connection"
private const val EVENT_CONNECT = "vpn_connection"
private const val EVENT_DISCONNECT = "vpn_disconnection"
private const val NO_VALUE = "n/a"

@Singleton
class VpnConnectionTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    @ElapsedRealtimeClock private val clock: () -> Long,
    private val telemetry: Telemetry,
    private val vpnStateMonitor: VpnStateMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
    currentUser: CurrentUser,
    private val prefs: ServerListUpdaterPrefs,
) {

    private enum class Outcome(val statsKeyword: String) {
        SUCCESS("success"), FAILURE("failure"), ABORTED("aborted")
    }

    private data class ConnectionInitInfo(
        val trigger: ConnectTrigger,
        val timestampMs: Long,
        val vpnOn: Boolean
    )

    // Cache the current user's tier to avoid async calls when reacting to events.
    private val currentUserTier: StateFlow<String> = currentUser.vpnUserFlow.map { vpnUser ->
        when {
            vpnUser == null -> "non-user"
            vpnUser.userTier == 0 -> "free"
            vpnUser.userTier in 1..2 -> "paid"
            else -> "internal"
        }
    }.stateIn(mainScope, SharingStarted.Eagerly, "non-user")

    private var connectionInProgress: ConnectionInitInfo? = null
    private var lastConnectTimestampMs: Long? = null

    fun start() {
        vpnStateMonitor.status.onEach { status ->
            val state = status.state
            when {
                state is VpnState.Connected -> onConnectingFinished(Outcome.SUCCESS, status.connectionParams)
                state is VpnState.Error && state.isFinal ->
                    onConnectingFinished(Outcome.FAILURE, status.connectionParams)
            }
        }.launchIn(mainScope)
    }

    fun onConnectionStart(trigger: ConnectTrigger) {
        if (trigger !is ConnectTrigger.Fallback || connectionInProgress == null) {
            connectionInProgress?.let {
                sendConnectionEvent(Outcome.ABORTED, it, null)
            }
            connectionInProgress = ConnectionInitInfo(trigger, clock(), vpnStateMonitor.isConnected)
        }
    }

    fun onConnectionAbort(isFailure: Boolean = false) =
        onConnectingFinished(if (isFailure) Outcome.FAILURE else Outcome.ABORTED, null)

    private fun onConnectingFinished(outcome: Outcome, connectionParams: ConnectionParams?) {
        connectionInProgress?.let {
            sendConnectionEvent(outcome, it, connectionParams)
        }
        connectionInProgress = null
    }

    fun onDisconnectionTrigger(trigger: DisconnectTrigger, connectionParams: ConnectionParams?) {
        if (connectionInProgress != null &&
            trigger !is DisconnectTrigger.NewConnection &&
            trigger !is DisconnectTrigger.Fallback
        ) {
            val outcome = if (trigger.isSuccess) Outcome.ABORTED else Outcome.FAILURE
            onConnectingFinished(outcome, connectionParams)
        } else if (lastConnectTimestampMs != null) { // Only log events when previously connected.
            sendDisconnectEvent(trigger, connectionParams)
            lastConnectTimestampMs = null
        }
    }

    private fun sendConnectionEvent(
        outcome: Outcome,
        connectionInfo: ConnectionInitInfo,
        connectionParams: ConnectionParams?
    ) {
        lastConnectTimestampMs = clock()
        with(connectionInfo) {
            val values = buildMap {
                this["time_to_connection"] = clock() - timestampMs
            }
            val dimensions = buildMap {
                this["vpn_status"] = if (vpnOn) "on" else "off"
                this["vpn_trigger"] = trigger.statsName
                addCommonDimensions(outcome, connectionParams)
                addServerFeatures(connectionParams?.server)
            }
            telemetry.event(MEASUREMENT_GROUP, EVENT_CONNECT, values, dimensions)
        }
    }

    private fun sendDisconnectEvent(trigger: DisconnectTrigger, connectionParams: ConnectionParams?) {
        val values = buildMap {
            this["session_length"] = lastConnectTimestampMs?.let { clock() - it } ?: 0
        }
        val dimensions = buildMap {
            this["vpn_trigger"] = trigger.statsName
            addCommonDimensions(trigger.isSuccess.toOutcome(), connectionParams)
        }
        telemetry.event(MEASUREMENT_GROUP, EVENT_DISCONNECT, values, dimensions)
    }

    private fun MutableMap<String, String>.addCommonDimensions(
        outcome: Outcome,
        connectionParams: ConnectionParams?
    ) {
        this["outcome"] = outcome.statsKeyword
        this["vpn_country"] = connectionParams?.server?.exitCountry?.uppercase() ?: NO_VALUE // TODO: 3-letter country codes?
        this["server"] = connectionParams?.server?.serverName ?: NO_VALUE
        this["port"] = connectionParams?.port?.toString() ?: NO_VALUE
        this["protocol"] = connectionParams?.protocolSelection?.toStats() ?: NO_VALUE
        this["user_country"] = prefs.lastKnownCountry?.uppercase() ?: NO_VALUE  // TODO: 3-letter country codes?
        this["isp"] = prefs.lastKnownIsp ?: NO_VALUE
        this["user_tier"] = currentUserTier.value
        this["network_type"] = getNetworkType()
    }

    private fun MutableMap<String, String>.addServerFeatures(server: Server?) {
        this["server_features"] = if (server != null) {
            val featureNames: List<String> = buildList {
                if (server.isFreeServer) add("free")
                if (server.isTor) add("tor")
                if (server.isP2pServer) add("p2p")
                if (server.isPartneshipServer) add("partnership")
                if (server.isStreamingServer) add("streaming")
            }
            featureNames.sorted().joinToString(",")
        } else {
            NO_VALUE
        }
    }

    private fun getNetworkType(): String {
        // VPN is not a "real" transport, remove.
        val transports = connectivityMonitor.defaultNetworkTransports - ConnectivityMonitor.Transport.VPN
        return when {
            transports.contains(ConnectivityMonitor.Transport.CELLULAR) -> "mobile"
            transports.contains(ConnectivityMonitor.Transport.WIFI) -> "wifi"
            transports.isEmpty() -> NO_VALUE
            else -> "other"
        }
    }

    private fun ProtocolSelection.toStats(): String {
        val vpnPrefix = vpn.name.lowercase()
        val transmissionSuffix = transmission?.name?.lowercase()?.let { "_$it" } ?: ""
        return "$vpnPrefix$transmissionSuffix"
    }

    private fun Boolean.toOutcome() = if (this) Outcome.SUCCESS else Outcome.FAILURE
}
