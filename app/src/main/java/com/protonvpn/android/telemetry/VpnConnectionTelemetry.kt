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
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Reusable
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject
import javax.inject.Singleton

private class ConnectionTelemetryDebug(message: String) : Throwable(message)

@OptIn(ExperimentalProtonFeatureFlag::class)
@Reusable
class ConnectionTelemetrySentryDebugEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager
)  {
    suspend operator fun invoke(): Boolean =
        featureFlagManager.getValue(currentUser.user()?.userId, FeatureId("ConnectionTelemetrySentryDebug"))
}

@Singleton
class VpnConnectionTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    @ElapsedRealtimeClock private val clock: () -> Long,
    private val commonDimensions: CommonDimensions,
    private val vpnStateMonitor: VpnStateMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
    private val helper: TelemetryFlowHelper,
    private val isSentryDebugEnabled: ConnectionTelemetrySentryDebugEnabled,
) {

    private enum class Outcome(val statsKeyword: String) {
        SUCCESS("success"), FAILURE("failure"), ABORTED("aborted")
    }

    private data class ConnectionInitInfo(
        val trigger: ConnectTrigger,
        val timestampMs: Long,
        val vpnOn: Boolean
    )

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
                reportImmediateAbortToSentry("new connection start")
                sendConnectionEvent(Outcome.ABORTED, it, null)
            }
            connectionInProgress = ConnectionInitInfo(trigger, clock(), vpnStateMonitor.isConnected)
        }
    }

    fun onConnectionAbort(isFailure: Boolean = false, report: Boolean = true, sentryInfo: String? = null) {
        if (report) {
            if (!isFailure && sentryInfo != null) reportImmediateAbortToSentry(sentryInfo)
            onConnectingFinished(if (isFailure) Outcome.FAILURE else Outcome.ABORTED, null)
        } else {
            connectionInProgress = null
        }
    }

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
            if (outcome == Outcome.ABORTED) reportImmediateAbortToSentry("disconnect")
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
            helper.event {
                val dimensions = buildMap {
                    this["vpn_status"] = if (vpnOn) "on" else "off"
                    this["vpn_trigger"] = trigger.statsName
                    addCommonDimensions(outcome, connectionParams)
                    addServerFeatures(connectionParams?.server)
                }
                TelemetryEventData(MEASUREMENT_GROUP, EVENT_CONNECT, values, dimensions)
            }
        }
    }

    private fun sendDisconnectEvent(trigger: DisconnectTrigger, connectionParams: ConnectionParams?) {
        val values = buildMap {
            this["session_length"] = lastConnectTimestampMs?.let { clock() - it } ?: 0
        }
        helper.event {
            val dimensions = buildMap {
                this["vpn_trigger"] = trigger.statsName
                addCommonDimensions(trigger.isSuccess.toOutcome(), connectionParams)
            }
            TelemetryEventData(MEASUREMENT_GROUP, EVENT_DISCONNECT, values, dimensions)
        }
    }

    private suspend fun MutableMap<String, String>.addCommonDimensions(
        outcome: Outcome,
        connectionParams: ConnectionParams?
    ) {
        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY,
            CommonDimensions.Key.ISP, CommonDimensions.Key.USER_TIER)
        this["outcome"] = outcome.statsKeyword
        this["vpn_country"] = connectionParams?.server?.exitCountry?.uppercase() ?: NO_VALUE
        this["server"] = connectionParams?.server?.serverName ?: NO_VALUE
        this["port"] = connectionParams?.port?.toString() ?: NO_VALUE
        this["protocol"] = connectionParams?.protocolSelection?.toStats() ?: NO_VALUE
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

    private fun reportImmediateAbortToSentry(sentryInfo: String) {
        val inProgress = connectionInProgress
        if (inProgress != null && clock() - inProgress.timestampMs < 150) {
            mainScope.launch {
                if (isSentryDebugEnabled()) {
                    val trigger = inProgress.trigger.statsName
                    Sentry.captureException(ConnectionTelemetryDebug("'$trigger' connection aborted: $sentryInfo"))
                }
            }
        }
    }

    companion object {
        const val MEASUREMENT_GROUP = "vpn.any.connection"
        private const val EVENT_CONNECT = "vpn_connection"
        private const val EVENT_DISCONNECT = "vpn_disconnection"
    }
}
