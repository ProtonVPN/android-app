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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.distinctUntilChanged
import com.protonvpn.android.R
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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.CertificateData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private val UNREACHABLE_MIN_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)

enum class ReasonRestricted { SecureCoreUpgradeNeeded, PlusUpgradeNeeded, Maintenance }

interface VpnUiDelegate {
    fun askForPermissions(intent: Intent, profile: Profile, onPermissionGranted: () -> Unit)
    /**
     * Called when server is restricted.
     * Returns true if the situation is handled (e.g. by informing the user).
     * If false is returned, VpnConnectionManager uses a fallback server.
     */
    fun onServerRestricted(reason: ReasonRestricted): Boolean
    fun shouldSkipAccessRestrictions(): Boolean = false
    fun onProtocolNotSupported()
}

@Singleton
class VpnConnectionManager @Inject constructor(
    private val permissionDelegate: VpnPermissionDelegate,
    private val userData: UserData,
    private val appConfig: AppConfig,
    private val backendProvider: VpnBackendProvider,
    private val networkManager: NetworkManager,
    private val vpnErrorHandler: VpnConnectionErrorHandler,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    private val serverManager: ServerManager,
    private val certificateRepository: CertificateRepository,
    private val scope: CoroutineScope,
    @WallClock private val now: () -> Long,
    private val currentVpnServiceProvider: CurrentVpnServiceProvider,
    private val currentUser: CurrentUser,
    powerManager: PowerManager
) : VpnStateSource {

    // Note: the jobs are not set to "null" upon completion, check "isActive" to see if still running.
    private var ongoingConnect: Job? = null
    private var ongoingFallback: Job? = null
    private val activeBackendObservable = MutableLiveData<VpnBackend?>()
    private val activeBackend: VpnBackend? get() = activeBackendObservable.value
    private val connectWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:connect")
    private val fallbackWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "ch.protonvpn:fallback")

    private var connectionParams: ConnectionParams? = null
    private var lastProfile: Profile? = null
    private var lastUnreachable = Long.MIN_VALUE

    override val selfStateObservable = MutableLiveData<VpnState>(VpnState.Disabled)

    // State taken from active backend or from monitor when no active backend, value always != null
    private val stateInternal: LiveData<VpnState> = Transformations.switchMap(
        activeBackendObservable.eagerMapNotNull { it ?: this }, VpnStateSource::selfStateObservable
    )
    private val state get() = stateInternal.value!!

    val retryInfo get() = activeBackend?.retryInfo

    var initialized = false

    init {
        stateInternal.observeForever {
            if (initialized) {
                Storage.saveString(STORAGE_KEY_STATE, state.name)

                val newState = it ?: VpnState.Disabled
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
                    "${unifiedState(newState)}, internal state: $newState, backend: ${activeBackend?.vpnProtocol}"
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
        }
        stateInternal.distinctUntilChanged().observeForever {
            if (it == VpnState.Connected) ProtonLogger.log(ConnConnectConnected)
        }

        scope.launch {
            vpnErrorHandler.switchConnectionFlow.collect { fallback ->
                if (vpnStateMonitor.isEstablishingOrConnected)
                    fallbackConnect(fallback)
            }
        }
        activeBackendObservable.observeForever {
            // Note: it should be CurrentVpnServiceProvider that observes activeBackendObservable but this would cause
            // dependency cycle.
            currentVpnServiceProvider.activeVpnBackend = it?.let { it::class }
        }

        initialized = true
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
            activeBackendObservable.value = newBackend
            setSelfState(VpnState.Disabled)
        }
    }

    private suspend fun handleRecoverableError(errorType: ErrorType, params: ConnectionParams) {
        ProtonLogger.logCustom(LogCategory.CONN, "Attempting to recover from error: $errorType")
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.CheckingAvailability, params))
        val result = when (errorType) {
            ErrorType.UNREACHABLE_INTERNAL, ErrorType.LOOKUP_FAILED_INTERNAL ->
                vpnErrorHandler.onUnreachableError(params)
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
                    disconnect("max sessions reached")
                } else {
                    activeBackend?.setSelfState(VpnState.Error(result.type))
                }
            }
        }
    }

    private suspend fun handleUnrecoverableError(errorType: ErrorType) {
        if (errorType == ErrorType.MAX_SESSIONS) {
            vpnStateMonitor.vpnConnectionNotificationFlow.emit(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS))
            ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
            disconnect("max sessions reached")
        }
    }

    private suspend fun fallbackConnect(fallback: VpnFallbackResult.Switch) {
        if (fallback.notifyUser && fallback.reason != null) {
            vpnStateMonitor.vpnConnectionNotificationFlow.emit(fallback)
        }

        when (fallback) {
            is VpnFallbackResult.Switch.SwitchProfile ->
                connectWithPermission(
                    vpnBackgroundUiDelegate,
                    fallback.toProfile,
                    preferredServer = fallback.toServer,
                    triggerAction = fallback.log
                )
            is VpnFallbackResult.Switch.SwitchServer -> {
                // Do not reconnect if user becomes delinquent
                if (fallback.reason !is SwitchServerReason.UserBecameDelinquent) {
                    // Not compatible protocol needs to ask user permission to switch
                    // If user accepts then continue through broadcast receiver
                    if (fallback.compatibleProtocol)
                        switchServerConnect(fallback)
                } else {
                    ProtonLogger.log(ConnServerSwitchFailed, "User became delinquent")
                }
            }
        }
    }

    private suspend fun onServerNotAvailable(profile: Profile, server: Server?) {
        val fallback = if (server == null) {
            vpnErrorHandler.onServerNotAvailable(profile)
        } else {
            vpnErrorHandler.onServerInMaintenance(profile, null)
        }

        if (fallback != null) {
            fallbackConnect(fallback)
        } else {
            vpnBackgroundUiDelegate.showInfoNotification(
                if (server == null) R.string.error_server_not_set
                else R.string.restrictedMaintenanceDescription
            )
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection()
        ProtonLogger.log(ConnConnectTrigger, switch.log)
        launchConnect {
            preparedConnect(switch.preparedConnection)
        }
    }

    private suspend fun smartConnect(profile: Profile, server: Server) {
        connectionParams = ConnectionParams(profile, server, null, null, null)

        if (activeBackend != null) {
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnecting first...")
            disconnectForNewConnection(profile.isGuestHoleProfile)
            if (!coroutineContext.isActive)
                return // Don't connect if the scope has been cancelled.
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnected, start connecting to new server.")
        }

        setSelfState(VpnState.ScanningPorts)

        var protocol: ProtocolSelection = profile.getProtocol(userData)
        val hasNetwork = networkManager.isConnectedToNetwork()
        if (!hasNetwork && protocol.vpn == VpnProtocol.Smart)
            protocol = getFallbackSmartProtocol(server)
        var preparedConnection =
            backendProvider.prepareConnection(protocol, profile, server, alwaysScan = hasNetwork)
        if (preparedConnection == null) {
            val fallbackProtocol = if (protocol.vpn == VpnProtocol.Smart)
                getFallbackSmartProtocol(server) else protocol
            ProtonLogger.logCustom(
                LogCategory.CONN_CONNECT,
                "No response for ${server.domain}, using fallback $fallbackProtocol"
            )

            // If port scanning fails (because e.g. some temporary network situation) just connect without pinging
            preparedConnection =
                backendProvider.prepareConnection(fallbackProtocol, profile, server, false)!!
        }

        preparedConnect(preparedConnection)
    }

    private fun getFallbackSmartProtocol(server: Server): ProtocolSelection {
        val config = appConfig.getSmartProtocolConfig()
        val wireGuardTxxEnabled = appConfig.getFeatureFlags().wireguardTlsEnabled
        val wireGuardServer = server.supportsProtocol(VpnProtocol.WireGuard)
        return when {
            config.wireguardEnabled && wireGuardServer ->
                ProtocolSelection(VpnProtocol.WireGuard)
            config.ikeV2Enabled ->
                ProtocolSelection(VpnProtocol.IKEv2)
            config.openVPNEnabled ->
                ProtocolSelection(VpnProtocol.OpenVPN)
            config.wireguardTcpEnabled && wireGuardServer && wireGuardTxxEnabled ->
                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP)
            config.wireguardTlsEnabled && wireGuardServer && wireGuardTxxEnabled->
                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)
            else ->
                ProtocolSelection(VpnProtocol.WireGuard)
        }
    }

    private suspend fun preparedConnect(preparedConnection: PrepareResult) {
        // If smart profile fails we need this to handle reconnect request
        lastProfile = preparedConnection.connectionParams.profile

        val newBackend = preparedConnection.backend
        if (activeBackend != null && activeBackend != newBackend)
            disconnectBlocking("new connection")

        val sessionId = currentUser.sessionId()
        if (newBackend.vpnProtocol == VpnProtocol.OpenVPN && sessionId != null) {
            // OpenVpnWrapperService is unable to obtain the certificate from
            // CertificateRepository because all the methods are synchronous.
            // Save the certificate to storage for easier access.
            // Also don't try to refresh the certificate now, if it is invalid it will be refreshed
            // via the VPN tunnel.
            val result = certificateRepository.getCertificateWithoutRefresh(sessionId)
            if (result is CertificateRepository.CertificateResult.Success) {
                Storage.save(
                    CertificateData(result.privateKeyPem, result.certificate),
                    CertificateData::class.java
                )
            } else {
                // Report LOCAL_AGENT_ERROR, same as other places where CertificateResult.Error is handled.
                setSelfState(VpnState.Error(ErrorType.LOCAL_AGENT_ERROR, "Failed to obtain certificate"))
                return
            }
        } else {
            Storage.save(null, CertificateData::class.java)
        }

        connectionParams = preparedConnection.connectionParams
        with(preparedConnection) {
            val profileInfo = connectionParams.profile.toLog(userData)
            ProtonLogger.log(
                ConnConnectStart,
                "backend: ${backend.vpnProtocol}, params: ${connectionParams.info}, $profileInfo"
            )
        }

        Storage.save(connectionParams, ConnectionParams::class.java)
        activateBackend(newBackend)
        activeBackend?.connect(preparedConnection.connectionParams)
    }

    private fun clearOngoingFallback() {
        ongoingFallback?.cancel()
        ongoingFallback = null
    }

    private fun clearOngoingConnection() {
        clearOngoingFallback()
        ongoingConnect?.cancel()
        ongoingConnect = null
    }

    fun onRestoreProcess(profile: Profile, reason: String): Boolean {
        val stateKey = Storage.getString(STORAGE_KEY_STATE, null)
        val shouldReconnect = stateKey != VpnState.Disabled.name && stateKey != VpnState.Disconnecting.name
        if (state == VpnState.Disabled && shouldReconnect) {
            connect(vpnBackgroundUiDelegate, profile, "Process restore: $reason, previous state was: $stateKey")
            return true
        }
        return false
    }

    fun connect(uiDelegate: VpnUiDelegate, profile: Profile, triggerAction: String) {
        val intent = permissionDelegate.prepareVpnPermission()
        scope.launch { vpnStateMonitor.newSessionEvent.emit(Unit) }
        if (intent != null) {
            uiDelegate.askForPermissions(intent, profile) {
                connectWithPermission(uiDelegate, profile, triggerAction)
            }
        } else {
            connectWithPermission(uiDelegate, profile, triggerAction)
        }
    }

    fun connectInBackground(profile: Profile, triggerAction: String) =
        connect(vpnBackgroundUiDelegate, profile, triggerAction)

    private fun connectWithPermission(
        delegate: VpnUiDelegate,
        profile: Profile,
        triggerAction: String,
        preferredServer: Server? = null
    ) {
        clearOngoingConnection()
        launchConnect {
            connectWithPermissionSync(delegate, profile, triggerAction, preferredServer)
        }
    }

    private suspend fun connectWithPermissionSync(
        delegate: VpnUiDelegate,
        profile: Profile,
        triggerAction: String,
        preferredServer: Server? = null
    ) {
        ProtonLogger.log(ConnConnectTrigger, "${profile.toLog(userData)}, reason: $triggerAction")
        val vpnUser = currentUser.vpnUser()
        val server = preferredServer ?: serverManager.getServerForProfile(profile, vpnUser)
        if (server?.online == true &&
            (delegate.shouldSkipAccessRestrictions() || vpnUser.hasAccessToServer(server))
        ) {
            val protocolAllowed = triggerAction == Constants.REASON_GUEST_HOLE ||
                profile.getProtocol(userData).isSupported(appConfig.getFeatureFlags())
            if (server.supportsProtocol(profile.getProtocol(userData).vpn) && protocolAllowed) {
                smartConnect(profile, server)
            } else {
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
                    onServerNotAvailable(profile, server)
                }
            }
        }
    }

    private suspend fun disconnectBlocking(triggerAction: String) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: $triggerAction")
        Storage.delete(ConnectionParams::class.java)
        setSelfState(VpnState.Disabled)
        activeBackend?.disconnect()
        activeBackendObservable.value = null
        connectionParams = null
    }

    private suspend fun disconnectForNewConnection(isGuestHoleConnection: Boolean = false) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: new connection")
        // GuestHole connections should not emit disconnect events to not trigger self canceling for guest hole
        if (!isGuestHoleConnection)
            vpnStateMonitor.onDisconnectedByReconnection.emit(Unit)
        Storage.delete(ConnectionParams::class.java)
        // The UI relies on going through this state to properly show that a new connection is
        // being established (as opposed to reconnecting to the same server).
        setSelfState(VpnState.Disconnecting)
        val previousBackend = activeBackend
        activeBackendObservable.value = null
        // CheckingAvailability seems to be the best state without introducing a new one.
        setSelfState(VpnState.CheckingAvailability)
        previousBackend?.disconnect()
    }

    fun disconnect(triggerAction: String) {
        disconnectWithCallback(triggerAction)
    }

    suspend fun disconnectSync(triggerAction: String) {
        clearOngoingConnection()
        disconnectBlocking(triggerAction)
    }

    private fun disconnectWithCallback(triggerAction: String, afterDisconnect: (() -> Unit)? = null) {
        clearOngoingConnection()
        scope.launch {
            disconnectBlocking(triggerAction)
            vpnStateMonitor.onDisconnectedByUser.emit(Unit)
            afterDisconnect?.invoke()
        }
    }

    fun reconnect(uiDelegate: VpnUiDelegate) = scope.launch {
        clearOngoingConnection()
        if (activeBackend != null)
            activeBackend?.reconnect()
        else
            lastProfile?.let { connect(uiDelegate, it, "reconnection") }
    }

    fun fullReconnect(triggerAction: String, uiDelegate: VpnUiDelegate) = scope.launch {
        disconnectBlocking("reconnect: $triggerAction")
        lastProfile?.let { connect(uiDelegate, it, "reconnection") }
    }

    fun onVpnServiceDestroyed() {
        Storage.load(ConnectionParams::class.java)?.takeIf { it.uuid == connectionParams?.uuid }?.let {
            ProtonLogger.logCustom(
                LogCategory.CONN_DISCONNECT, "onDestroy called for current VpnService, deleting ConnectionParams"
            )
            Storage.delete(ConnectionParams::class.java)
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
        VpnState.Connecting -> "Connecting"
        VpnState.Connected -> "Connected"
        VpnState.Disconnecting -> "Disconnecting"
        is VpnState.Error -> "Disconnecting"
        VpnState.Reconnecting -> "Connecting"
        VpnState.WaitingForNetwork -> "Disconnected"
    }

    companion object {
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"
        // 2 minutes should be enough even on an unstable network when some requests fail.
        private val WAKELOCK_MAX_MS = TimeUnit.MINUTES.toMillis(2)

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.LOOKUP_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL,
            ErrorType.POLICY_VIOLATION_LOW_PLAN
        )
    }
}
