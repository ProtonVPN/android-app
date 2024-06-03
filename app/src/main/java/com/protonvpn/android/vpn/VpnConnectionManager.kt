/*
 * Copyright (c) 2021 Proton Technologies AG
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
import com.protonvpn.android.R
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.AppConfig
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
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.implies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.session.SessionId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VpnConnectionManager @Inject constructor(
    private val permissionDelegate: VpnPermissionDelegate,
    private val appConfig: AppConfig,
    private val userSettings: EffectiveCurrentUserSettings,
    private val backendProvider: VpnBackendProvider,
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
    powerManager: PowerManager,
    private val vpnConnectionTelemetry: VpnConnectionTelemetry
) : VpnStateSource, VpnConnect {

    // Note: the jobs are not set to "null" upon completion, check "isActive" to see if still running.
    private var ongoingConnect: Job? = null
    private var ongoingFallback: Job? = null

    private val activeBackendFlow = MutableStateFlow<VpnBackend?>(null)
    private val activeBackend: VpnBackend? get() = activeBackendFlow.value
    private val connectWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:connect")
    private val fallbackWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:fallback")

    private var connectionParams: ConnectionParams? = null
    private var lastConnectIntent: AnyConnectIntent? = null
    private var lastUnreachable = Long.MIN_VALUE

    override val selfStateFlow = MutableStateFlow<VpnState?>(null)
    private val lastKnownExitIp = activeBackendFlow.flatMapLatest { it?.lastKnownExitIp ?: flowOf(null) }
    val netShieldStats =
        activeBackendFlow.flatMapLatest { it?.netShieldStatsFlow ?: flowOf(NetShieldStats()) }

    // State taken from active backend or from monitor when no active backend, value always != null
    private val stateInternal: StateFlow<VpnState?> =
        activeBackendFlow.flatMapLatest {
            it?.selfStateFlow ?: selfStateFlow
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val state get() = stateInternal.value ?: VpnState.Disabled

    init {
        stateInternal.onEach { newState ->
            if (newState != null) {
                Storage.saveString(STORAGE_KEY_STATE, state.name)

                val errorType = (newState as? VpnState.Error)?.type

                if (errorType != null && errorType in RECOVERABLE_ERRORS) {
                    if (ongoingFallback?.isActive != true) {
                        if (!skipFallback(errorType)) {
                            launchFallback {
                                handleRecoverableError(errorType, connectionParams!!)
                            }
                        }
                    }
                } else {
                    if (errorType != null && ongoingFallback?.isActive != true) {
                        launchFallback {
                            handleUnrecoverableError(errorType)
                        }
                    }

                    // After auth failure OpenVPN will automatically enter DISABLED state, don't clear fallback to allow
                    // it to finish even when we entered DISABLED state.
                    if (state == VpnState.Connected)
                        clearOngoingFallback()

                    vpnStateMonitor.updateStatus(VpnStateMonitor.Status(newState, connectionParams))
                }

                ProtonLogger.log(
                    ConnStateChanged,
                    "${unifiedState(newState)}, internal state: $newState, backend: ${activeBackend?.vpnProtocol}, fallback active: ${ongoingFallback?.isActive}"
                )
                DebugUtils.debugAssert {
                    val isConnectedOrConnecting = state in arrayOf(
                        VpnState.Connecting,
                        VpnState.Connected,
                        VpnState.Reconnecting
                    )
                    isConnectedOrConnecting.implies(connectionParams != null && activeBackend != null)
                }
            }
        }.launchIn(scope)

        stateInternal.onEach {
            if (it == VpnState.Connected) ProtonLogger.log(ConnConnectConnected)
        }.launchIn(scope)

        vpnErrorHandler.switchConnectionFlow
            .onEach { fallback ->
                if (vpnStateMonitor.isEstablishingOrConnected) {
                    fallbackConnect(fallback)
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
    }

    private fun skipFallback(errorType: ErrorType) =
        errorType == ErrorType.UNREACHABLE_INTERNAL &&
            (lastUnreachable > now() - UNREACHABLE_MIN_INTERVAL_MS).also { skip ->
                if (!skip)
                    lastUnreachable = now()
            }

    private fun activateBackend(newBackend: VpnBackend) {
        DebugUtils.debugAssert {
            activeBackend == null || activeBackend == newBackend
        }
        if (activeBackend != newBackend) {
            newBackend.setSelfState(VpnState.Connecting)
            activeBackendFlow.value = newBackend
            setSelfState(VpnState.Disabled)
        }
    }

    private suspend fun handleRecoverableError(errorType: ErrorType, params: ConnectionParams) {
        ProtonLogger.logCustom(LogCategory.CONN, "Attempting to recover from error: $errorType")
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.CheckingAvailability, params))
        val result = when (errorType) {
            ErrorType.UNREACHABLE_INTERNAL, ErrorType.LOOKUP_FAILED_INTERNAL ->
                vpnErrorHandler.onUnreachableError(params)
            ErrorType.SERVER_ERROR ->
                vpnErrorHandler.onServerError(params)
            ErrorType.AUTH_FAILED_INTERNAL, ErrorType.POLICY_VIOLATION_LOW_PLAN ->
                vpnErrorHandler.onAuthError(params)
            else ->
                VpnFallbackResult.Error(errorType)
        }
        when (result) {
            is VpnFallbackResult.Switch ->
                fallbackConnect(result)
            is VpnFallbackResult.Error -> {
                vpnStateMonitor.vpnConnectionNotificationFlow.emit(result)
                ProtonLogger.logCustom(LogCategory.CONN, "Failed to recover, entering $result")
                if (result.type == ErrorType.MAX_SESSIONS) {
                    ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
                    clearOngoingConnection(clearFallback = false)
                    disconnectBlocking(DisconnectTrigger.Error("max sessions reached"))
                } else {
                    activeBackend?.setSelfState(VpnState.Error(result.type, isFinal = false))
                }
            }
        }
    }

    private suspend fun handleUnrecoverableError(errorType: ErrorType) {
        if (errorType == ErrorType.MAX_SESSIONS) {
            vpnStateMonitor.vpnConnectionNotificationFlow.emit(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS))
            ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
            clearOngoingConnection(clearFallback = false)
            disconnectBlocking(DisconnectTrigger.Error("max sessions reached"))
        } else {
            ProtonLogger.logCustom(LogCategory.CONN_DISCONNECT, "disconnecting unrecoverably: ${errorType.name}")
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
        val fallback = if (server == null) {
            vpnErrorHandler.onServerNotAvailable(connectIntent)
        } else {
            vpnErrorHandler.onServerInMaintenance(connectIntent, null)
        }

        if (fallback != null) {
            fallbackConnect(fallback)
        } else {
            vpnConnectionTelemetry.onConnectionAbort(sentryInfo = "no server available")
            vpnBackgroundUiDelegate.showInfoNotification(
                if (server == null) R.string.error_server_not_set
                else R.string.restrictedMaintenanceDescription
            )
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection(clearFallback = false)
        ProtonLogger.log(ConnConnectTrigger, switch.log)
        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Fallback(switch.log))
        launchConnect {
            disconnectForNewConnection(isFallback = true, oldConnectionParams = connectionParams)
            preparedConnect(switch.preparedConnection)
        }
    }

    private suspend fun smartConnect(
        connectIntent: AnyConnectIntent,
        preferredProtocol: ProtocolSelection,
        server: Server,
        isFallback: Boolean
    ) {
        val oldConnectionParams = connectionParams
        connectionParams = ConnectionParams(connectIntent, server, null, null)

        if (activeBackend != null) {
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnecting first...")
            disconnectForNewConnection(connectIntent is AnyConnectIntent.GuestHole, oldConnectionParams, isFallback)
            if (!coroutineContext.isActive)
                return // Don't connect if the scope has been cancelled.
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnected, start connecting to new server.")
        }

        setSelfState(VpnState.ScanningPorts)

        var protocol = preferredProtocol
        val hasNetwork = networkManager.isConnectedToNetwork()
        if (!hasNetwork && protocol.vpn == VpnProtocol.Smart)
            protocol = getFallbackSmartProtocol(server)
        var preparedConnection =
            backendProvider.prepareConnection(protocol, connectIntent, server, alwaysScan = hasNetwork)
        if (preparedConnection == null) {
            if (connectIntent is AnyConnectIntent.GuestHole) {
                // If scanning failed for GH, just try another server to speed things up.
                setSelfState(VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true))
                return
            }
            protocol = if (protocol.vpn == VpnProtocol.Smart)
                getFallbackSmartProtocol(server) else protocol
            ProtonLogger.logCustom(
                LogCategory.CONN_CONNECT,
                "No response for ${server.domain}, using fallback $protocol"
            )

            // If port scanning fails (because e.g. some temporary network situation) just connect without pinging
            preparedConnection = backendProvider.prepareConnection(protocol, connectIntent, server, false)
        }

        if (preparedConnection == null) {
            setSelfState(
                VpnState.Error(ErrorType.GENERIC_ERROR, "Server doesn't support selected protocol", isFinal = true)
            )
        } else {
            preparedConnect(preparedConnection)
        }
    }

    private fun getFallbackSmartProtocol(server: Server): ProtocolSelection {
        val config = appConfig.getSmartProtocolConfig()
        val wireGuardTxxEnabled = appConfig.getFeatureFlags().wireguardTlsEnabled
        val wireGuardUdpServer =
            supportsProtocol(server, ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
        val wireGuardTcpServer =
            supportsProtocol(server, ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP))
        val wireGuardTlsServer =
            supportsProtocol(server, ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
        return when {
            config.wireguardEnabled && wireGuardUdpServer ->
                ProtocolSelection(VpnProtocol.WireGuard)
            config.openVPNEnabled && supportsProtocol(server, ProtocolSelection(VpnProtocol.OpenVPN)) ->
                ProtocolSelection(VpnProtocol.OpenVPN)
            config.wireguardTcpEnabled && wireGuardTcpServer && wireGuardTxxEnabled ->
                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP)
            config.wireguardTlsEnabled && wireGuardTlsServer && wireGuardTxxEnabled ->
                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)
            else ->
                ProtocolSelection(VpnProtocol.WireGuard)
        }
    }

    private suspend fun preparedConnect(preparedConnection: PrepareResult) {
        // If smart profile fails we need this to handle reconnect request
        lastConnectIntent = preparedConnection.connectionParams.connectIntent

        val newBackend = preparedConnection.backend
        if (activeBackend != null && activeBackend != newBackend)
            disconnectBlocking(DisconnectTrigger.NewConnection)

        val sessionId = currentUser.sessionId()
        if (newBackend.vpnProtocol == VpnProtocol.OpenVPN && sessionId != null &&
            preparedConnection.connectionParams.connectIntent !is AnyConnectIntent.GuestHole
        ) {
            // OpenVPN needs a certificate to connect, make sure there is one available (it can be expired, it'll be
            // refreshed via the VPN tunnel if needed).
            if (!ensureCertificateAvailable(sessionId)) {
                // Report LOCAL_AGENT_ERROR, same as other places where CertificateResult.Error is handled.
                val error = VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, "Failed to obtain certificate", isFinal = true)
                setSelfState(error)
                return
            }
        }

        connectionParams = preparedConnection.connectionParams
        with(preparedConnection) {
            val connectIntentInfo = connectionParams.connectIntent.toLog()
            ProtonLogger.log(
                ConnConnectStart,
                "backend: ${backend.vpnProtocol}, params: ${connectionParams.info}, $connectIntentInfo"
            )
        }

        ConnectionParams.store(connectionParams)
        activateBackend(newBackend)
        activeBackend?.connect(preparedConnection.connectionParams)
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
        if (state == VpnState.Disabled && shouldReconnect) {
            connect(vpnBackgroundUiDelegate, connectIntent, ConnectTrigger.Auto("Process restore: $reason, previous state was: $stateKey"))
            return true
        }
        return false
    }

    override fun connect(uiDelegate: VpnUiDelegate, connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger) {
        val intent = permissionDelegate.prepareVpnPermission()
        if (connectIntent !is AnyConnectIntent.GuestHole)
            scope.launch { vpnStateMonitor.newSessionEvent.emit(Unit) }
        if (intent != null) {
            uiDelegate.askForPermissions(intent, connectIntent) {
                connectWithPermission(uiDelegate, connectIntent, triggerAction, clearFallback = true)
            }
        } else {
            connectWithPermission(uiDelegate, connectIntent, triggerAction, clearFallback = true)
        }
    }

    fun connectInBackground(connectIntent: AnyConnectIntent, triggerAction: ConnectTrigger) =
        connect(vpnBackgroundUiDelegate, connectIntent, triggerAction)

    private fun connectWithPermission(
        delegate: VpnUiDelegate,
        connectIntent: AnyConnectIntent,
        triggerAction: ConnectTrigger,
        clearFallback: Boolean,
        preferredServer: Server? = null,
    ) {
        clearOngoingConnection(clearFallback)
        launchConnect {
            connectWithPermissionSync(delegate, connectIntent, triggerAction, preferredServer)
        }
    }

    private suspend fun connectWithPermissionSync(
        delegate: VpnUiDelegate,
        connectIntent: AnyConnectIntent,
        trigger: ConnectTrigger,
        preferredServer: Server? = null
    ) {
        val settings = userSettings.effectiveSettings.first()
        ProtonLogger.log(ConnConnectTrigger, "${connectIntent.toLog()}, reason: ${trigger.description}")
        vpnConnectionTelemetry.onConnectionStart(trigger)
        val vpnUser = currentUser.vpnUser()
        val server = preferredServer ?: serverManager.getServerForConnectIntent(connectIntent, vpnUser)
        if (server?.online == true &&
            (delegate.shouldSkipAccessRestrictions() || vpnUser.hasAccessToServer(server))
        ) {
            val protocol = if (connectIntent is AnyConnectIntent.GuestHole) GuestHole.PROTOCOL else settings.protocol
            val protocolAllowed = trigger is ConnectTrigger.GuestHole || protocol.isSupported(appConfig.getFeatureFlags())
            if (supportsProtocol(server, protocol.vpn) && protocolAllowed) {
                smartConnect(connectIntent, protocol, server, trigger is ConnectTrigger.Fallback)
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

    private suspend fun disconnectBlocking(trigger: DisconnectTrigger) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: ${trigger.description}")
        vpnConnectionTelemetry.onDisconnectionTrigger(trigger, connectionParams)
        ConnectionParams.deleteFromStore("disconnect")
        setSelfState(VpnState.Disabled)
        activeBackend?.disconnect()
        activeBackendFlow.value = null
        connectionParams = null
    }

    private suspend fun disconnectForNewConnection(
        isGuestHoleConnection: Boolean = false,
        oldConnectionParams: ConnectionParams?,
        isFallback: Boolean
    ) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: new connection")
        vpnConnectionTelemetry.onDisconnectionTrigger(
            if (isFallback) DisconnectTrigger.Fallback else DisconnectTrigger.NewConnection,
            oldConnectionParams
        )
        // GuestHole connections should not emit disconnect events to not trigger self canceling for guest hole
        if (!isGuestHoleConnection)
            vpnStateMonitor.onDisconnectedByReconnection.emit(Unit)
        ConnectionParams.deleteFromStore("disconnect for new connection")
        // The UI relies on going through this state to properly show that a new connection is
        // being established (as opposed to reconnecting to the same server).
        setSelfState(VpnState.Disconnecting)
        val previousBackend = activeBackend
        activeBackendFlow.value = null
        // CheckingAvailability seems to be the best state without introducing a new one.
        setSelfState(VpnState.CheckingAvailability)
        previousBackend?.disconnect()
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

    // Will reconnect to current connection params, skipping pinging procedures
    fun reconnectWithCurrentParams(uiDelegate: VpnUiDelegate) = scope.launch {
        clearOngoingConnection(clearFallback = true)
        if (activeBackend != null) {
            vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.Reconnect("reconnect"), connectionParams)
            vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Reconnect)
            activeBackend?.reconnect()
        } else {
            lastConnectIntent?.let { connect(uiDelegate, it, ConnectTrigger.Reconnect) }
        }
    }

    // Will do complete reconnection, which may result in different protocol or server
    // if compared to original connection
    fun reconnect(triggerAction: String, uiDelegate: VpnUiDelegate) {
        scope.launch {
            disconnectBlocking(DisconnectTrigger.Reconnect("reconnect: $triggerAction"))
            lastConnectIntent?.let { connect(uiDelegate, it, ConnectTrigger.Reconnect) }
        }
    }

    fun onVpnServiceDestroyed() {
        ConnectionParams.readIntentFromStore(expectedUuid = connectionParams?.uuid)?.let {
            vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.ServiceDestroyed, connectionParams)
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
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"
        // 2 minutes should be enough even on an unstable network when some requests fail.
        private val WAKELOCK_MAX_MS = TimeUnit.MINUTES.toMillis(2)

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.LOOKUP_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL,
            ErrorType.POLICY_VIOLATION_LOW_PLAN,
            ErrorType.SERVER_ERROR
        )
    }
}
