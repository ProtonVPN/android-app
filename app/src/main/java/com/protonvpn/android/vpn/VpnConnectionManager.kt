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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.distinctUntilChanged
import com.protonvpn.android.R
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.Companion.EXTRA_SWITCH_PROFILE
import com.protonvpn.android.logging.ConnConnectConnected
import com.protonvpn.android.logging.ConnConnectStart
import com.protonvpn.android.logging.ConnConnectTrigger
import com.protonvpn.android.logging.ConnDisconnectTrigger
import com.protonvpn.android.logging.ConnServerSwitchFailed
import com.protonvpn.android.logging.ConnStateChanged
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.logging.UserPlanMaxSessionsReached
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.CertificateData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
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
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private val FALLBACK_PROTOCOL = VpnProtocol.IKEv2
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
open class VpnConnectionManager(
    private val appContext: Context,
    private val userData: UserData,
    private val backendProvider: VpnBackendProvider,
    private val networkManager: NetworkManager,
    private val vpnErrorHandler: VpnConnectionErrorHandler,
    private val vpnStateMonitor: VpnStateMonitor,
    private val notificationHelper: NotificationHelper,
    private val vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    private val serverManager: ServerManager,
    private val certificateRepository: CertificateRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val currentUser: CurrentUser
) : VpnStateSource {

    private var ongoingConnect: Job? = null
    private var ongoingFallback: Job? = null
    private val activeBackendObservable = MutableLiveData<VpnBackend?>()
    private val activeBackend: VpnBackend? get() = activeBackendObservable.value

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
        Log.i("create state monitor")

        appContext.registerBroadcastReceiver(IntentFilter(NotificationHelper.DISCONNECT_ACTION)) { intent ->
            when (intent?.action) {
                NotificationHelper.DISCONNECT_ACTION -> {
                    ProtonLogger.log(UiDisconnect, "notification")
                    disconnect("user via notification")
                }
            }
        }
        appContext.registerBroadcastReceiver(IntentFilter(NotificationHelper.SMART_PROTOCOL_ACTION)) { intent ->
            val profileToSwitch = intent?.getSerializableExtra(EXTRA_SWITCH_PROFILE) as Profile
            profileToSwitch.wrapper.setDeliverer(serverManager)
            notificationHelper.cancelInformationNotification()
            ProtonLogger.logUiSettingChange(Setting.DEFAULT_PROTOCOL, "notification action")
            userData.setProtocols(VpnProtocol.Smart, null)
            connect(vpnBackgroundUiDelegate, profileToSwitch, "Enable Smart protocol from notification")
        }
        stateInternal.observeForever {
            if (initialized) {
                Storage.saveString(STORAGE_KEY_STATE, state.name)

                val newState = it ?: VpnState.Disabled
                val errorType = (newState as? VpnState.Error)?.type

                if (errorType != null && errorType in RECOVERABLE_ERRORS) {
                    if (ongoingFallback?.isActive != true) {
                        if (!skipFallback(errorType)) {
                            ongoingFallback = scope.launch {
                                handleRecoverableError(errorType, connectionParams!!)
                                ongoingFallback = null
                            }
                        }
                    }
                } else {
                    if (errorType != null) {
                        ongoingFallback = scope.launch {
                            handleUnrecoverableError(errorType)
                            ongoingFallback = null
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

                when (state) {
                    VpnState.Connected -> {
                        EventBus.post(ConnectedToServer(connectionParams!!.server))
                    }
                    VpnState.Disabled -> {
                        EventBus.post(ConnectedToServer(null))
                    }
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
            notificationHelper.showInformationNotification(
                if (server == null) R.string.error_server_not_set
                else R.string.restrictedMaintenanceDescription
            )
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection()
        ProtonLogger.log(ConnConnectTrigger, switch.log)
        ongoingConnect = scope.launch {
            preparedConnect(switch.preparedConnection)
        }
    }

    private suspend fun smartConnect(profile: Profile, server: Server) {
        connectionParams = ConnectionParams(profile, server, null, null)

        if (activeBackend != null) {
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnecting first...")
            disconnectForNewConnection()
            if (!coroutineContext.isActive)
                return // Don't connect if the scope has been cancelled.
            ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Disconnected, start connecting to new server.")
        }

        setSelfState(VpnState.ScanningPorts)

        var protocol = profile.getProtocol(userData)
        val hasNetwork = networkManager.isConnectedToNetwork()
        if (!hasNetwork && protocol == VpnProtocol.Smart)
            protocol = FALLBACK_PROTOCOL
        var preparedConnection =
            backendProvider.prepareConnection(protocol, profile, server, alwaysScan = hasNetwork)
        if (preparedConnection == null) {
            val fallbackProtocol =
                if (protocol == VpnProtocol.Smart) FALLBACK_PROTOCOL else protocol
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
        ongoingConnect = null
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

    fun onRestoreProcess(profile: Profile, reason: String = ""): Boolean {
        val stateKey = Storage.getString(STORAGE_KEY_STATE, null)
        if (state == VpnState.Disabled && stateKey == VpnState.Connected.name) {
            connect(vpnBackgroundUiDelegate, profile, "Process restore: $reason")
            return true
        }
        return false
    }

    fun connect(uiDelegate: VpnUiDelegate, profile: Profile, triggerAction: String) {
        val intent = prepare(appContext) // TODO: can this be appContext?
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
        ongoingConnect = scope.launch {
            connectWithPermissionSync(delegate, profile, triggerAction, preferredServer)
            ongoingConnect = null
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
            if (server.supportsProtocol(profile.getProtocol(userData))) {
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
                ongoingFallback = scope.launch {
                    onServerNotAvailable(profile, server)
                    ongoingFallback = null
                }
            }
        }
    }

    open fun prepare(context: Context): Intent? = VpnService.prepare(context)

    private suspend fun disconnectBlocking(triggerAction: String) {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: $triggerAction")
        Storage.delete(ConnectionParams::class.java)
        setSelfState(VpnState.Disabled)
        activeBackend?.disconnect()
        activeBackendObservable.value = null
        connectionParams = null
    }

    private suspend fun disconnectForNewConnection() {
        ProtonLogger.log(ConnDisconnectTrigger, "reason: new connection")
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

    open fun disconnect(triggerAction: String) {
        disconnectWithCallback(triggerAction)
    }

    suspend fun disconnectSync(triggerAction: String) {
        clearOngoingConnection()
        disconnectBlocking(triggerAction)
    }

    open fun disconnectWithCallback(triggerAction: String, afterDisconnect: (() -> Unit)? = null) {
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

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.LOOKUP_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL,
            ErrorType.POLICY_VIOLATION_LOW_PLAN
        )
    }
}
