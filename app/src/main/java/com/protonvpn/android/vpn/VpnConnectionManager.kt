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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.Companion.EXTRA_SWITCH_PROFILE
import com.protonvpn.android.logging.ConnConnectStart
import com.protonvpn.android.logging.ConnConnectTrigger
import com.protonvpn.android.logging.ConnDisconnectTrigger
import com.protonvpn.android.logging.ConnStateChange
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanMaxSessionsReached
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import io.sentry.event.EventBuilder
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

interface VpnPermissionDelegate {
    fun askForPermissions(intent: Intent, onPermissionGranted: () -> Unit)
    fun getContext(): Context
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
    private val serverManager: ServerManager,
    private val scope: CoroutineScope,
    private val now: () -> Long
) : VpnStateSource {

    companion object {
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.LOOKUP_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL,
            ErrorType.POLICY_VIOLATION_LOW_PLAN
        )
    }

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
                NotificationHelper.DISCONNECT_ACTION -> disconnect("user via notification")
            }
        }
        appContext.registerBroadcastReceiver(IntentFilter(NotificationHelper.SMART_PROTOCOL_ACTION)) { intent ->
            val profileToSwitch = intent?.getSerializableExtra(EXTRA_SWITCH_PROFILE) as Profile
            profileToSwitch.wrapper.setDeliverer(serverManager)
            notificationHelper.cancelInformationNotification()
            userData.setProtocols(VpnProtocol.Smart, null)
            connectInBackground(appContext, profileToSwitch, "Enable Smart protocol from notification")
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
                    // After auth failure OpenVPN will automatically enter DISABLED state, don't clear fallback to allow
                    // it to finish even when we entered DISABLED state.
                    if (state == VpnState.Connected)
                        clearOngoingFallback()

                    vpnStateMonitor.updateStatus(VpnStateMonitor.Status(newState, connectionParams))
                }

                ProtonLogger.log(
                    ConnStateChange,
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
        ProtonLogger.log("Attempting to recover from error: $errorType")
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
                vpnStateMonitor.fallbackConnectionFlow.emit(result)
                ProtonLogger.log("Failed to recover, entering $result")
                if (result.type == ErrorType.MAX_SESSIONS) {
                    ProtonLogger.log(UserPlanMaxSessionsReached, "disconnecting")
                    disconnect("max sessions reached")
                } else {
                    activeBackend?.setSelfState(VpnState.Error(result.type))
                }
            }
        }
    }

    private suspend fun fallbackConnect(fallback: VpnFallbackResult.Switch) {
        fallback.notificationReason?.let {
            vpnStateMonitor.fallbackConnectionFlow.emit(fallback)
        }

        val sentryEvent = EventBuilder()
            .withMessage("Fallback connect")
            .withExtra("From", connectionParams?.info)
            .withExtra("Info", fallback.log)
            .build()
        ProtonLogger.logSentryEvent(sentryEvent)
        ProtonLogger.log("Fallback connect: ${fallback.log}")

        when (fallback) {
            is VpnFallbackResult.Switch.SwitchProfile ->
                connectWithPermission(appContext, fallback.toProfile, fallback.log)
            is VpnFallbackResult.Switch.SwitchServer -> {
                // Do not reconnect if user becomes delinquent
                if (fallback.notificationReason !is SwitchServerReason.UserBecameDelinquent) {
                    // Not compatible protocol needs to ask user permission to switch
                    // If user accepts then continue through broadcast receiver
                    if (fallback.compatibleProtocol)
                        switchServerConnect(fallback)
                }
            }
        }
    }

    private suspend fun onServerNotAvailable(context: Context, profile: Profile, server: Server?) {
        val fallback = if (server == null) {
            ProtonLogger.log("Server not available. Finding alternative...")
            vpnErrorHandler.onServerNotAvailable(profile)
        } else {
            ProtonLogger.log("Server in maintenance. Finding alternative...")
            vpnErrorHandler.onServerInMaintenance(profile, null)
        }

        if (fallback != null) {
            fallbackConnect(fallback)
        } else {
            notificationHelper.showInformationNotification(
                context,
                context.getString(
                    if (server == null) R.string.error_server_not_set
                    else R.string.restrictedMaintenanceDescription
                )
            )
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection()
        ongoingConnect = scope.launch {
            preparedConnect(switch.preparedConnection)
        }
    }

    private suspend fun smartConnect(profile: Profile, server: Server) {
        connectionParams = ConnectionParams(profile, server, null, null)

        if (activeBackend != null) {
            ProtonLogger.log("Disconnecting first...")
            disconnectForNewConnection()
            if (!coroutineContext.isActive)
                return // Don't connect if the scope has been cancelled.
            ProtonLogger.log("Disconnected, start connecting to new server.")
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
            ProtonLogger.log("No response for ${server.domain}, using fallback $fallbackProtocol")

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

        connectionParams = preparedConnection.connectionParams
        with (preparedConnection) {
            ProtonLogger.log(
                ConnConnectStart,
                "backend: ${backend.vpnProtocol}, params: ${connectionParams.info}"
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

    fun onRestoreProcess(context: Context, profile: Profile): Boolean {
        val stateKey = Storage.getString(STORAGE_KEY_STATE, null)
        if (state == VpnState.Disabled && stateKey == VpnState.Connected.name) {
            connectInBackground(context, profile, "Process restore")
            return true
        }
        return false
    }

    fun connect(
        permissionDelegate: VpnPermissionDelegate,
        profile: Profile,
        triggerAction: String
    ) {
        connect(permissionDelegate.getContext(), profile, triggerAction) { intent ->
            permissionDelegate.askForPermissions(intent) {
                connectWithPermission(permissionDelegate.getContext(), profile, triggerAction)
            }
        }
    }

    fun connectInBackground(context: Context, profile: Profile, connectionCauseLog: String) {
        connect(context, profile, connectionCauseLog) {
            showInsufficientPermissionNotification(context)
        }
    }

    private fun connect(
        context: Context,
        profile: Profile,
        triggerAction: String,
        onPermissionNeeded: (Intent) -> Unit
    ) {
        val intent = prepare(context)
        scope.launch { vpnStateMonitor.newSessionEvent.emit(Unit) }
        if (intent != null) {
            onPermissionNeeded(intent)
        } else {
            connectWithPermission(context, profile, triggerAction)
        }
    }

    private fun connectWithPermission(context: Context, profile: Profile, triggerAction: String) {
        ProtonLogger.log(ConnConnectTrigger, "Profile: ${profile.toLog(userData)}, reason: $triggerAction")
        val server = profile.server
        if (server?.online == true) {
            if (server.supportsProtocol(profile.getProtocol(userData))) {
                clearOngoingConnection()
                ongoingConnect = scope.launch {
                    smartConnect(profile, server)
                    ongoingConnect = null
                }
            } else {
                protocolNotSupportedDialog(context)
            }
        } else {
            ongoingFallback = scope.launch {
                onServerNotAvailable(context, profile, server)
                ongoingFallback = null
            }
        }
    }

    private fun protocolNotSupportedDialog(context: Context) {
        if (context is Activity) {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.serverNoWireguardSupport)
                .setPositiveButton(R.string.close, null)
                .show()
        } else {
            notificationHelper.showInformationNotification(
                context,
                context.getString(R.string.serverNoWireguardSupport),
                icon = R.drawable.ic_notification_disconnected
            )
        }
    }

    private fun showInsufficientPermissionNotification(context: Context) {
        notificationHelper.showInformationNotification(
            context,
            context.getString(R.string.insufficientPermissionsDetails),
            context.getString(R.string.insufficientPermissionsTitle),
            icon = R.drawable.ic_notification_disconnected
        )
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

    fun reconnect(permissionDelegate: VpnPermissionDelegate) = scope.launch {
        clearOngoingConnection()
        if (activeBackend != null)
            activeBackend?.reconnect()
        else
            lastProfile?.let { connect(permissionDelegate, it, "reconnection") }
    }

    fun fullReconnect(triggerAction: String, permissionDelegate: VpnPermissionDelegate) = scope.launch {
        disconnectBlocking("reconnect: $triggerAction")
        lastProfile?.let { connect(permissionDelegate, it, "reconnection") }
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
}
