/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.vpn

import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.R
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ConnConnectConnected
import com.protonvpn.android.logging.ConnConnectStart
import com.protonvpn.android.logging.ConnConnectTrigger
import com.protonvpn.android.logging.ConnDisconnectTrigger
import com.protonvpn.android.logging.ConnServerSwitchFailed
import com.protonvpn.android.logging.ConnStateChanged
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanMaxSessionsReached
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.session.SessionId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val UNREACHABLE_MIN_INTERVAL_MS = StuckConnectionHandler.STUCK_DURATION_MS + TimeUnit.SECONDS.toMillis(10)

enum class ReasonRestricted { SecureCoreUpgradeNeeded, PlusUpgradeNeeded, Maintenance }

interface VpnUiDelegate {
    fun askForPermissions(intent: Intent, connectIntent: AnyConnectIntent, onPermissionGranted: () -> Unit)
    /**
     * Called when server is restricted.
     * Returns true if the situation is handled (e.g. by informing the user).
     * If false is returned, VpnConnectionManager uses a fallback server.
     */
    fun onServerRestricted(reason: ReasonRestricted): Boolean
    fun shouldSkipAccessRestrictions(): Boolean = false
    fun onProtocolNotSupported()
}

// Simpler dependency (that's easier to fake than VpnConnectionManager) for all code that simply
// triggers connection.
fun interface VpnConnect {
    fun connect(uiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger)
    operator fun invoke(uiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger) =
        connect(uiDelegate, connectIntent, triggerAction)
}

private sealed class InternalState {
    data object Disabled : InternalState()

    // backend != null means we're pinging during active connection (to switch to another server)
    data class ScanningPorts(val activeParams: ConnectionParams?, val newParams: ConnectionParams, val activeBackend: VpnBackend?) : InternalState()

    data class SwitchingConnection(val activeParams: ConnectionParams?, val newParams: ConnectionParams, val activeBackend: VpnBackend?) : InternalState()
    data class Active(val params: ConnectionParams, val activeBackend: VpnBackend) : InternalState()
    data class Error(val params: ConnectionParams, val error: VpnState.Error, val activeBackend: VpnBackend?) : InternalState()

    val activeBackendOrNull : VpnBackend? get() = when(this) {
        is ScanningPorts -> activeBackend
        is SwitchingConnection -> activeBackend
        is Active -> activeBackend
        is Error -> activeBackend
        Disabled -> null
    }

    val activeConnectionParamsOrNull : ConnectionParams? get() = when(this) {
        is ScanningPorts -> activeParams
        is SwitchingConnection -> activeParams
        is Active -> params
        is Error -> params
        Disabled -> null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VpnConnectionManager @Inject constructor(
    private val permissionDelegate: VpnPermissionDelegate,
    private val getFeatureFlags: GetFeatureFlags,
    private val settingsForConnection: SettingsForConnection,
    private val backendProvider: dagger.Lazy<VpnBackendProvider>,
    private val networkManager: NetworkManager,
    private val vpnErrorHandler: VpnConnectionErrorHandler,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    private val serverManager: ServerManager2,
    private val certificateRepository: CertificateRepository,
    private val scope: CoroutineScope,
    @WallClock private val now: () -> Long,
    private val currentVpnServiceProvider: CurrentVpnServiceProvider,
    private val currentUser: CurrentUser,
    private val supportsProtocol: SupportsProtocol,
    powerManager: dagger.Lazy<PowerManager>,
    private val vpnConnectionTelemetry: VpnConnectionTelemetry,
    private val autoLoginManager: AutoLoginManager,
    private val vpnErrorAndFallbackObservability: VpnErrorAndFallbackObservability,
) : VpnConnect {

    // Note: the jobs are not set to "null" upon completion, check "isActive" to see if still running.
    private var ongoingConnect: Job? = null
    private var ongoingFallback: Job? = null

    private val connectWakeLock by lazy { powerManager.get().newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:connect") }
    private val fallbackWakeLock by lazy { powerManager.get().newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:fallback") }

    private var lastConnectIntent: AnyConnectIntent? = null
    private var lastUnreachable = Long.MIN_VALUE

    // null is only for initial value and is needed for process restore logic
    private val internalState = MutableStateFlow<InternalState?>(null)
    private val activeBackend: VpnBackend? get() = internalState.value?.activeBackendOrNull
    private val activeBackendFlow = internalState.map { it?.activeBackendOrNull }

    // Connection params for active connection (there might be newer params for upcoming connection)
    private val activeConnectionParams: ConnectionParams? get() = internalState.value?.activeConnectionParamsOrNull

    private val lastKnownExitIp = activeBackendFlow.flatMapLatest { it?.lastKnownExitIp ?: flowOf(null) }
    val netShieldStats =
        activeBackendFlow.flatMapLatest { it?.netShieldStatsFlow ?: flowOf(NetShieldStats()) }

    // Helper flow to produce states for VpnStateMonitor
    private val monitorStatus: Flow<VpnStateMonitor.Status> =
        internalState.filterNotNull().flatMapLatest { internalState ->
            when(internalState) {
                InternalState.Disabled ->
                    flowOf(VpnStateMonitor.Status(VpnState.Disabled, null))
                is InternalState.ScanningPorts ->
                    flowOf(VpnStateMonitor.Status(VpnState.ScanningPorts, internalState.newParams))
                is InternalState.SwitchingConnection ->
                    flowOf(VpnStateMonitor.Status(VpnState.Connecting, internalState.newParams))
                is InternalState.Error ->
                    flowOf(VpnStateMonitor.Status(internalState.error, internalState.params))
                is InternalState.Active ->
                    internalState.activeBackend.selfStateFlow.filterNotNull().map { backendState ->
                        VpnStateMonitor.Status(backendState, internalState.params)
                    }
            }
        }

    init {
        monitorStatus.onEach { newStatus ->
            val newState = newStatus.state
            Storage.saveString(STORAGE_KEY_STATE, newState.name)

            val errorType = (newState as? VpnState.Error)?.type
            if (errorType != null) {
                vpnErrorAndFallbackObservability.reportError(errorType)
            }

            if (errorType != null && errorType in RECOVERABLE_ERRORS) {
                if (ongoingFallback?.isActive != true) {
                    if (!skipFallback(errorType)) {
                        launchFallback {
                            handleRecoverableError(activeBackend, errorType, newStatus.connectionParams!!)
                        }
                    }
                }
            } else {
                if (errorType != null && ongoingFallback?.isActive != true) {
                    launchFallback {
                        handleUnrecoverableError(newStatus.connectionParams!!, errorType)
                    }
                }

                // After auth failure OpenVPN will automatically enter DISABLED state, don't clear fallback to allow
                // it to finish even when we entered DISABLED state.
                if (newState == VpnState.Connected)
                    clearOngoingFallback()

                vpnStateMonitor.updateStatus(newStatus)
            }
            ProtonLogger.log(
                ConnStateChanged,
                "${unifiedState(newState)}, internal state: $newState, backend: ${activeBackend?.vpnProtocol}, fallback active: ${ongoingFallback?.isActive}"
            )
            if (newState == VpnState.Connected) ProtonLogger.log(ConnConnectConnected)
        }.launchIn(scope)

        vpnErrorHandler.switchConnectionFlow
            .onEach { fallback ->
                if (vpnStateMonitor.isEstablishingOrConnected) {
                    vpnErrorAndFallbackObservability.reportFallback(fallback)
                    when (fallback) {
                        is VpnFallbackResult.Error -> {
                            if (fallback.type == ErrorType.NO_PROFILE_FALLBACK_AVAILABLE) {
                                disconnectWithError(
                                    trigger = DisconnectTrigger.Fallback,
                                    originalParams = fallback.originalParams,
                                    error = VpnState.Error(
                                        type = fallback.type,
                                        isFinal = true
                                    )
                                )
                            }
                        }
                        is VpnFallbackResult.Switch -> fallbackConnect(fallback)
                    }
                }
            }.launchIn(scope)
        lastKnownExitIp
            .onEach { vpnStateMonitor.updateLastKnownExitIp(it) }
            .launchIn(scope)
        activeBackendFlow.onEach {
            // Note: it should be CurrentVpnServiceProvider that observes activeBackendObservable but this would cause
            // dependency cycle.
            currentVpnServiceProvider.activeVpnBackend = it?.let { it::class }
        }.launchIn(scope)
        scope.launch {
            activeBackendFlow.collectLatest { backend ->
                if (backend == null)
                    vpnStateMonitor.internalVpnProtocolState.value = VpnState.Disabled
                else {
                    backend.internalVpnProtocolState.collect { state ->
                        vpnStateMonitor.internalVpnProtocolState.value = state
                    }
                }
            }
        }
    }

    private fun setInternalState(newState: InternalState) {
        internalState.value = newState
    }

    private fun skipFallback(errorType: ErrorType) =
        errorType == ErrorType.UNREACHABLE_INTERNAL &&
            (lastUnreachable > now() - UNREACHABLE_MIN_INTERVAL_MS).also { skip ->
                if (!skip)
                    lastUnreachable = now()
            }

    private suspend fun handleRecoverableError(backend: VpnBackend?, errorType: ErrorType, params: ConnectionParams) {
        ProtonLogger.logCustom(LogCategory.CONN, "Attempting to recover from error: $errorType")
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.CheckingAvailability, params))
        val result = when (errorType) {
            ErrorType.UNREACHABLE_INTERNAL ->
                vpnErrorHandler.onUnreachableError(params)
            ErrorType.SERVER_ERROR ->
                vpnErrorHandler.onServerError(params)
            ErrorType.AUTH_FAILED_INTERNAL, ErrorType.POLICY_VIOLATION_LOW_PLAN ->
                vpnErrorHandler.onAuthError(params)
            else ->
                VpnFallbackResult.Error(params, errorType, reason = null)
        }
        when (result) {
            is VpnFallbackResult.Switch -> {
                vpnErrorAndFallbackObservability.reportFallback(result)
                fallbackConnect(result)
            }
            is VpnFallbackResult.Error -> {
                vpnStateMonitor.vpnConnectionNotificationFlow.emit(result)
                ProtonLogger.logCustom(LogCategory.CONN, "Failed to recover, entering $result")
                if (result.type == ErrorType.MAX_SESSIONS) {
                    ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
                    clearOngoingConnection(clearFallback = false)
                    disconnectBlocking(DisconnectTrigger.Error("max sessions reached"))
                } else {
                    backend?.setSelfState(VpnState.Error(result.type, isFinal = false))
                }
            }
        }
    }

    private suspend fun handleUnrecoverableError(originalParams: ConnectionParams, errorType: ErrorType) {
        if (errorType == ErrorType.MAX_SESSIONS) {
            vpnStateMonitor.vpnConnectionNotificationFlow.emit(
                VpnFallbackResult.Error(originalParams, ErrorType.MAX_SESSIONS, reason = null)
            )
            ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
            clearOngoingConnection(clearFallback = false)
            disconnectBlocking(DisconnectTrigger.Error("max sessions reached"))
        } else {
            ProtonLogger.logCustom(LogCategory.CONN, "non-actionable error: ${errorType.name}")
        }
    }

    private suspend fun fallbackConnect(fallback: VpnFallbackResult.Switch) {
        if (fallback.notifyUser && fallback.reason != null) {
            vpnStateMonitor.vpnConnectionNotificationFlow.emit(fallback)
        }

        when (fallback) {
            is VpnFallbackResult.Switch.SwitchConnectIntent ->
                connectWithPermission(
                    vpnBackgroundUiDelegate,
                    fallback.toConnectIntent,
                    preferredServer = fallback.toServer,
                    triggerAction = ConnectTrigger.Fallback(fallback.log),
                    disconnectTrigger = DisconnectTrigger.Fallback,
                    clearFallback = false,
                )
            is VpnFallbackResult.Switch.SwitchServer -> {
                // Do not reconnect if user becomes delinquent
                if (fallback.reason !is SwitchServerReason.UserBecameDelinquent) {
                    // Not compatible protocol needs to ask user permission to switch
                    // If user accepts then continue through broadcast receiver
                    if (fallback.compatibleProtocol) {
                        switchServerConnect(fallback)
                    } else {
                        vpnConnectionTelemetry.onConnectionAbort(isFailure = true)
                    }
                } else {
                    vpnConnectionTelemetry.onConnectionAbort(isFailure = true)
                    ProtonLogger.log(ConnServerSwitchFailed, "User became delinquent")
                }
            }
        }
    }

    private suspend fun onServerNotAvailable(connectIntent: AnyConnectIntent, server: Server?) {
        ProtonLogger.logCustom(LogCategory.CONN, "Current server unavailable")
        val reason =
            if (server == null) SwitchServerReason.ServerUnavailable else SwitchServerReason.ServerInMaintenance
        val fallback = if (server == null) {
            vpnErrorHandler.onServerNotAvailable(connectIntent)
        } else {
            vpnErrorHandler.onServerInMaintenance(connectIntent, null)
        }

        if (fallback != null) {
            vpnErrorAndFallbackObservability.reportFallback(fallback)
            fallbackConnect(fallback)
        } else {
            vpnConnectionTelemetry.onConnectionAbort(sentryInfo = "no server available")
            if (connectIntent is ConnectIntent) {
                vpnErrorAndFallbackObservability.reportFallbackFailure(connectIntent, reason)
                vpnBackgroundUiDelegate.showInfoNotification(
                    if (server == null) R.string.error_server_not_set
                    else R.string.restrictedMaintenanceDescription
                )
            }
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection(clearFallback = false)
        ProtonLogger.log(ConnConnectTrigger, switch.log)
        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Fallback(switch.log))
        launchConnect {
            preparedConnect(switch.preparedConnection, DisconnectTrigger.Fallback)
        }
    }

    private suspend fun smartConnect(
        connectIntent: AnyConnectIntent,
        preferredProtocol: ProtocolSelection,
        server: Server,
        disconnectTrigger: DisconnectTrigger,
    ) {
        val newConnectionParams = ConnectionParams(
            connectIntent, server, null, null, ipv6SettingEnabled = settingsForConnection.getFor(connectIntent).ipV6Enabled)
        setInternalState(InternalState.ScanningPorts(activeConnectionParams, newConnectionParams, activeBackend))

        var protocol = preferredProtocol
        val hasNetwork = networkManager.isConnectedToNetwork()
        if (!hasNetwork && protocol.vpn == VpnProtocol.Smart)
            protocol = getFallbackSmartProtocol(server)
        var preparedConnection =
            backendProvider.get().prepareConnection(protocol, connectIntent, server, alwaysScan = hasNetwork)
        if (preparedConnection == null) {
            if (connectIntent is AnyConnectIntent.GuestHole) {
                // If scanning failed for GH, just try another server to speed things up.
                setInternalState(InternalState.Error(newConnectionParams, VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true), activeBackend))
                return
            }
            protocol = if (protocol.vpn == VpnProtocol.Smart)
                getFallbackSmartProtocol(server) else protocol
            ProtonLogger.logCustom(
                LogCategory.CONN_CONNECT,
                "No response for ${server.serverName}, using fallback $protocol"
            )

            // If port scanning fails (because e.g. some temporary network situation) just connect without pinging
            preparedConnection = backendProvider.get().prepareConnection(protocol, connectIntent, server, false)
        }

        if (preparedConnection == null) {
            setInternalState(InternalState.Error(newConnectionParams, VpnState.Error(ErrorType.GENERIC_ERROR, "Server doesn't support selected protocol", isFinal = true), activeBackend))
        } else {
            preparedConnect(preparedConnection, disconnectTrigger)
        }
    }

    private fun getFallbackSmartProtocol(server: Server): ProtocolSelection {
        val wireGuardTxxEnabled = getFeatureFlags.value.wireguardTlsEnabled
        val fallbackOrder = buildList {
            add(ProtocolSelection(VpnProtocol.WireGuard))
            add(ProtocolSelection(VpnProtocol.OpenVPN))
            if (wireGuardTxxEnabled) {
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP))
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
            }
        }
        return fallbackOrder.firstOrNull { supportsProtocol(server, it) } ?: ProtocolSelection(VpnProtocol.WireGuard)
    }

    private suspend fun preparedConnect(preparedConnection: PrepareResult, disconnectTrigger: DisconnectTrigger) {
        // If smart profile fails we need this to handle reconnect request
        lastConnectIntent = preparedConnection.connectionParams.connectIntent

        val currentBackend = activeBackend
        if (currentBackend != null) {
            setInternalState(InternalState.SwitchingConnection(activeConnectionParams, preparedConnection.connectionParams, currentBackend))
            val isGuestHoleConnection = preparedConnection.connectionParams.connectIntent is AnyConnectIntent.GuestHole
            disconnectForNewConnection(activeConnectionParams, disconnectTrigger, isGuestHoleConnection)
            setInternalState(InternalState.SwitchingConnection(activeConnectionParams, preparedConnection.connectionParams, null))
        }

        val newBackend = preparedConnection.backend
        if (newBackend.vpnProtocol == VpnProtocol.OpenVPN &&
            preparedConnection.connectionParams.connectIntent !is AnyConnectIntent.GuestHole
        ) {
            val sessionId = currentUser.sessionId()
            if (sessionId != null) {
                // OpenVPN needs a certificate to connect, make sure there is one available (it can be expired, it'll be
                // refreshed via the VPN tunnel if needed).
                if (!ensureCertificateAvailable(sessionId)) {
                    // Report LOCAL_AGENT_ERROR, same as other places where CertificateResult.Error is handled.
                    val error = VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, "Failed to obtain certificate", isFinal = true)
                    setInternalState(InternalState.Error(preparedConnection.connectionParams, error, newBackend))
                    return
                }
            }
        }

        with(preparedConnection) {
            val connectIntentInfo = connectionParams.connectIntent.toLog()
            ProtonLogger.log(
                ConnConnectStart,
                "backend: ${backend.vpnProtocol}, params: ${connectionParams.info}, $connectIntentInfo"
            )
        }

        ConnectionParams.store(preparedConnection.connectionParams)
        newBackend.setSelfState(VpnState.Connecting)
        newBackend.connect(preparedConnection.connectionParams)
        setInternalState(InternalState.Active(preparedConnection.connectionParams, newBackend))
    }

    private suspend fun ensureCertificateAvailable(sessionId: SessionId): Boolean {
        var result = certificateRepository.getCertificateWithoutRefresh(sessionId)
        if (result !is CertificateRepository.CertificateResult.Success) {
            // No certificate found, is there an issue with CertificateStorage? Try fetching.
            result = certificateRepository.getCertificate(sessionId)
        }
        return result is CertificateRepository.CertificateResult.Success
    }

    private fun clearOngoingFallback() {
        ongoingFallback?.cancel()
        ongoingFallback = null
    }

    private fun clearOngoingConnection(clearFallback: Boolean) {
        if (clearFallback)
            clearOngoingFallback()
        ongoingConnect?.cancel()
        ongoingConnect = null
    }

    fun onRestoreProcess(connectIntent: AnyConnectIntent, reason: String): Boolean {
        val stateKey = Storage.getString(STORAGE_KEY_STATE, null)
        val shouldReconnect = stateKey != VpnState.Disabled.name &&
            stateKey != VpnState.Disconnecting.name &&
            connectIntent !is AnyConnectIntent.GuestHole
        // Check both state and ongoingConnect because state is not set immediately.
        val isNotConnecting = internalState.value == null && ongoingConnect?.isActive != true
        if (isNotConnecting && shouldReconnect) {
            connect(vpnBackgroundUiDelegate, connectIntent, ConnectTrigger.Auto("Process restore: $reason, previous state was: $stateKey"))
            return true
        }
        return false
    }

    fun onAlwaysOn(connectIntent: AnyConnectIntent) {
        // Check both state and ongoingConnect because state is not set immediately.
        val vpnState = internalState.value
        val isNotConnecting =
            (vpnState == null || vpnState == InternalState.Disabled) && ongoingConnect?.isActive != true
        if (isNotConnecting) {
            connect(vpnBackgroundUiDelegate, connectIntent, ConnectTrigger.Auto("always-on"))
        }
    }

    override fun connect(uiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger) {
        val intent = permissionDelegate.prepareVpnPermission()
        val disconnectTrigger = DisconnectTrigger.NewConnection
        if (connectIntent !is AnyConnectIntent.GuestHole)
            scope.launch { vpnStateMonitor.newSessionEvent.emit(connectIntent to triggerAction) }
        if (intent != null) {
            uiDelegate.askForPermissions(intent, connectIntent) {
                connectWithPermission(uiDelegate, connectIntent, triggerAction, disconnectTrigger, clearFallback = true)
            }
        } else {
            connectWithPermission(uiDelegate, connectIntent, triggerAction, disconnectTrigger, clearFallback = true)
        }
    }

    fun connectInBackground(connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger) =
        connect(vpnBackgroundUiDelegate, connectIntent, triggerAction)

    private fun connectWithPermission(
        delegate: VpnUiDelegate,
        connectIntent: AnyConnectIntent,
        triggerAction: ConnectTrigger,
        disconnectTrigger: DisconnectTrigger,
        clearFallback: Boolean,
        preferredServer: Server? = null,
    ) {
        clearOngoingConnection(clearFallback)
        lastConnectIntent = connectIntent
        launchConnect {
            autoLoginManager.waitForAutoLogin()
            connectWithPermissionSync(delegate, connectIntent, triggerAction, disconnectTrigger, preferredServer)
        }
    }

    private suspend fun connectWithPermissionSync(
        delegate: VpnUiDelegate,
        connectIntent: AnyConnectIntent,
        trigger: ConnectTrigger,
        disconnectTrigger: DisconnectTrigger,
        preferredServer: Server? = null
    ) {
        val settings = settingsForConnection.getFor(connectIntent)
        ProtonLogger.log(ConnConnectTrigger, "${connectIntent.toLog()}, reason: ${trigger.description}")
        vpnConnectionTelemetry.onConnectionStart(trigger)
        val vpnUser = currentUser.vpnUser()
        val server = preferredServer ?: serverManager.getBestServerForConnectIntent(connectIntent, vpnUser, settings.protocol)
        if (server?.online == true &&
            (delegate.shouldSkipAccessRestrictions() || vpnUser.hasAccessToServer(server))
        ) {
            val protocol = if (connectIntent is AnyConnectIntent.GuestHole) GuestHole.PROTOCOL else settings.protocol
            val protocolAllowed = trigger is ConnectTrigger.GuestHole || protocol.isSupported(getFeatureFlags.value)
            if (supportsProtocol(server, protocol.vpn) && protocolAllowed) {
                smartConnect(connectIntent, protocol, server, disconnectTrigger)
            } else {
                vpnConnectionTelemetry.onConnectionAbort(sentryInfo = "no protocol supported")
                delegate.onProtocolNotSupported()
            }
        } else {
            val needsFallback = server == null ||
                delegate.onServerRestricted(
                    when {
                        !server.online -> ReasonRestricted.Maintenance
                        server.isSecureCoreServer && !vpnUser.hasAccessToServer(server) -> ReasonRestricted.SecureCoreUpgradeNeeded
                        else -> ReasonRestricted.PlusUpgradeNeeded
                    }
                ).not()
            if (needsFallback) {
                launchFallback {
                    onServerNotAvailable(connectIntent, server)
                }
            } else {
                // The case has been handled by delegate.onServerRestricted, don't report the event.
                vpnConnectionTelemetry.onConnectionAbort(report = false)
            }
        }
    }

    private suspend fun disconnectBlocking(trigger: DisconnectTrigger, endState: InternalState = InternalState.Disabled) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: ${trigger.description}")
        vpnConnectionTelemetry.onDisconnectionTrigger(trigger, activeConnectionParams)
        ConnectionParams.deleteFromStore("disconnect")
        activeBackend?.disconnect()
        setInternalState(endState)
    }

    private suspend fun disconnectForNewConnection(
        oldConnectionParams: ConnectionParams?,
        trigger: DisconnectTrigger,
        isGuestHoleConnection: Boolean,
    ) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: new connection")
        vpnConnectionTelemetry.onDisconnectionTrigger(trigger, oldConnectionParams)
        if (!isGuestHoleConnection)
            vpnStateMonitor.onDisconnectedByReconnection.emit(Unit)
        ConnectionParams.deleteFromStore("disconnect for new connection")
        val previousBackend = activeBackend
        previousBackend?.disconnect()
    }

    suspend fun disconnectWithError(trigger: DisconnectTrigger, originalParams: ConnectionParams, error: VpnState.Error) {
        disconnectBlocking(
            trigger = trigger,
            endState = InternalState.Error(
                params = originalParams,
                error = error,
                activeBackend = activeBackend
            )
        )
    }

    fun disconnect(trigger: DisconnectTrigger) {
        clearOngoingConnection(clearFallback = true)
        scope.launch {
            disconnectBlocking(trigger)
            vpnStateMonitor.onDisconnectedByUser.emit(Unit)
        }
    }

    suspend fun disconnectAndWait(trigger: DisconnectTrigger) {
        clearOngoingConnection(clearFallback = true)
        disconnectBlocking(trigger)
    }

    suspend fun disconnectGuestHole() {
        // Don't disconnect if another connection has started.
        if (lastConnectIntent is AnyConnectIntent.GuestHole) {
            disconnectAndWait(DisconnectTrigger.GuestHole)
        }
    }

    // Will do complete reconnection, which may result in different protocol or server
    // if compared to original connection
    fun reconnect(triggerAction: String, uiDelegate: VpnUiDelegate) {
        scope.launch {
            val currentConnectionParams = activeConnectionParams
            if (currentConnectionParams != null) {
                lastConnectIntent?.let { connectWithPermission(uiDelegate, it, ConnectTrigger.Reconnect, DisconnectTrigger.Reconnect(triggerAction), clearFallback = true) }
            } else {
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.CONN, "Reconnect called without active connection")
            }
        }
    }

    fun onVpnServiceDestroyed(connectionParamsUuid: UUID?) {
        ConnectionParams.readIntentFromStore(expectedUuid = connectionParamsUuid ?: activeConnectionParams?.uuid)?.let {
            vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.ServiceDestroyed, activeConnectionParams)
            ProtonLogger.logCustom(
                LogCategory.CONN_DISCONNECT, "onDestroy called for current VpnService, deleting ConnectionParams"
            )
            ConnectionParams.deleteFromStore("service destroyed")
        }
    }

    private fun launchConnect(block: suspend () -> Unit) {
        if (ongoingConnect?.isActive != true) {
            ongoingConnect = launchWithWakeLock(connectWakeLock, block)
        } else {
            failInDebugAndLogError("Trying to start connect job while previous is still running")
        }
    }

    private fun launchFallback(block: suspend () -> Unit) {
        if (ongoingFallback?.isActive != true) {
            ongoingFallback = launchWithWakeLock(fallbackWakeLock, block)
        } else {
            failInDebugAndLogError("Trying to start fallback job while previous is still running")
        }
    }

    private fun launchWithWakeLock(wakeLock: PowerManager.WakeLock, block: suspend () -> Unit): Job {
        wakeLock.acquire(WAKELOCK_MAX_MS)
        return scope.launch {
            block()
        }.apply {
            invokeOnCompletion {
                if (wakeLock.isHeld)
                    wakeLock.release()
            }
        }
    }

    private fun failInDebugAndLogError(message: String) {
        DebugUtils.fail(message)
        ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.CONN, message)
    }

    private fun unifiedState(vpnState: VpnState): String = when (vpnState) {
        VpnState.Disabled -> "Disconnected"
        VpnState.ScanningPorts,
        VpnState.CheckingAvailability,
        VpnState.Reconnecting,
        VpnState.WaitingForNetwork,
        VpnState.Connecting -> "Connecting"
        VpnState.Connected -> "Connected"
        VpnState.Disconnecting -> "Disconnecting"
        is VpnState.Error -> if (vpnState.isFinal) "Disconnected" else "Connecting"
    }

    companion object {
        @VisibleForTesting const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"
        // 2 minutes should be enough even on an unstable network when some requests fail.
        private val WAKELOCK_MAX_MS = TimeUnit.MINUTES.toMillis(2)

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL,
            ErrorType.POLICY_VIOLATION_LOW_PLAN,
            ErrorType.SERVER_ERROR
        )
    }
}
