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
import android.os.Build
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.components.BaseActivityV2.Companion.showNoVpnPermissionDialog
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.Companion.EXTRA_SWITCH_PROFILE
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import io.sentry.event.EventBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import javax.inject.Singleton

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
    private val scope: CoroutineScope
) : VpnStateSource {

    companion object {
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"

        private val RECOVERABLE_ERRORS = listOf(
            ErrorType.AUTH_FAILED_INTERNAL,
            ErrorType.LOOKUP_FAILED_INTERNAL,
            ErrorType.UNREACHABLE_INTERNAL)
    }

    private var ongoingConnect: Job? = null
    private var ongoingFallback: Job? = null
    private val activeBackendObservable = MutableLiveData<VpnBackend?>()
    private val activeBackend: VpnBackend? get() = activeBackendObservable.value

    private var connectionParams: ConnectionParams? = null
    private var lastProfile: Profile? = null

    override val selfStateObservable = MutableLiveData<VpnState>(VpnState.Disabled)

    // State taken from active backend or from monitor when no active backend, value always != null
    private val stateInternal: LiveData<VpnState> = Transformations.switchMap(
        activeBackendObservable.eagerMapNotNull { it ?: this }, VpnStateSource::selfStateObservable)
    private val state get() = stateInternal.value!!

    val retryInfo get() = activeBackend?.retryInfo

    var initialized = false

    init {
        Log.i("create state monitor")

        appContext.registerBroadcastReceiver(IntentFilter(NotificationHelper.DISCONNECT_ACTION)) { intent ->
            when (intent?.action) {
                NotificationHelper.DISCONNECT_ACTION -> disconnect()
            }
        }
        appContext.registerBroadcastReceiver(IntentFilter(NotificationHelper.SMART_PROTOCOL_ACTION)) { intent ->
            val profileToSwitch = intent?.getSerializableExtra(EXTRA_SWITCH_PROFILE) as Profile
            profileToSwitch.wrapper.setDeliverer(serverManager)
            notificationHelper.cancelInformationNotification()
            userData.useSmartProtocol = true
            connect(appContext, profileToSwitch)
        }
        stateInternal.observeForever {
            if (initialized) {
                Storage.saveString(STORAGE_KEY_STATE, state.name)

                val newState = it ?: VpnState.Disabled
                val errorType = (newState as? VpnState.Error)?.type

                if (errorType != null && errorType in RECOVERABLE_ERRORS) {
                    if (ongoingFallback?.isActive != true) {
                        ongoingFallback = scope.launch {
                            handleRecoverableError(errorType, connectionParams!!)
                            ongoingFallback = null
                        }
                    }
                } else {
                    // After auth failure OpenVPN will automatically enter DISABLED state, don't clear fallback to allow
                    // it to finish even when we entered DISABLED state.
                    if (state == VpnState.Connected)
                        clearOngoingFallback()

                    vpnStateMonitor.status.value = VpnStateMonitor.Status(newState, connectionParams)
                }

                ProtonLogger.log("VpnStateMonitor state=$newState backend=${activeBackend?.vpnProtocol}")
                DebugUtils.debugAssert {
                    (state in arrayOf(VpnState.Connecting, VpnState.Connected, VpnState.Reconnecting))
                        .implies(connectionParams != null && activeBackend != null)
                }

                when (state) {
                    VpnState.Connected -> {
                        EventBus.postOnMain(ConnectedToServer(connectionParams!!.server))
                    }
                    VpnState.Disabled -> {
                        EventBus.postOnMain(ConnectedToServer(null))
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

    private fun activateBackend(newBackend: VpnBackend) {
        DebugUtils.debugAssert {
            activeBackend == null || activeBackend == newBackend
        }
        if (activeBackend != newBackend) {
            activeBackend?.active = false
            newBackend.active = true
            newBackend.setSelfState(VpnState.Connecting)
            activeBackendObservable.value = newBackend
            setSelfState(VpnState.Disabled)
        }
    }

    private suspend fun handleRecoverableError(errorType: ErrorType, params: ConnectionParams) {
        ProtonLogger.log("Attempting to recover from error: $errorType")
        vpnStateMonitor.status.value = VpnStateMonitor.Status(VpnState.CheckingAvailability, params)
        val result = when (errorType) {
            ErrorType.UNREACHABLE_INTERNAL, ErrorType.LOOKUP_FAILED_INTERNAL ->
                vpnErrorHandler.onUnreachableError(params)
            ErrorType.AUTH_FAILED_INTERNAL ->
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
                    disconnect()
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
                connectWithPermission(appContext, fallback.toProfile)
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
                context, context.getString(if (server == null)
                    R.string.error_server_not_set else R.string.restrictedMaintenanceDescription))
        }
    }

    private fun switchServerConnect(switch: VpnFallbackResult.Switch.SwitchServer) {
        clearOngoingConnection()
        ongoingConnect = scope.launch {
            preparedConnect(switch.preparedConnection)
        }
    }

    private suspend fun smartConnect(profile: Profile, server: Server) {
        ProtonLogger.log("Connect: ${server.domain}")
        connectionParams = ConnectionParams(profile, server, null, null)

        if (profile.getProtocol(userData) == VpnProtocol.Smart)
            setSelfState(VpnState.ScanningPorts)

        var protocol = profile.getProtocol(userData)
        if (!networkManager.isConnectedToNetwork() && protocol == VpnProtocol.Smart)
            protocol = userData.manualProtocol
        var preparedConnection = backendProvider.prepareConnection(protocol, profile, server)
        if (preparedConnection == null) {
            ProtonLogger.log("Smart protocol: no protocol available for ${server.domain}, " +
                "falling back to ${userData.manualProtocol}")

            // If port scanning fails (because e.g. some temporary network situation) just connect
            // without smart protocol
            preparedConnection = backendProvider.prepareConnection(userData.manualProtocol, profile, server)!!
        }

        preparedConnect(preparedConnection)
    }

    private suspend fun preparedConnect(preparedConnection: PrepareResult) {
        // If smart profile fails we need this to handle reconnect request
        lastProfile = preparedConnection.connectionParams.profile

        val newBackend = preparedConnection.backend
        if (activeBackend != null && activeBackend != newBackend)
            disconnectBlocking()

        connectionParams = preparedConnection.connectionParams
        ProtonLogger.log("Connecting using ${connectionParams?.info}")

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
        if (state == VpnState.Disabled && Storage.getString(STORAGE_KEY_STATE, null) == VpnState.Connected.name) {
            connect(context, profile, "Process restore")
            return true
        }
        return false
    }

    fun connect(context: Context, profile: Profile, connectionCauseLog: String? = null) {
        connectionCauseLog?.let { ProtonLogger.log("Connecting caused by: $it") }
        val intent = prepare(context)
        scope.launch { vpnStateMonitor.newSessionEvent.emit(Unit) }
        if (intent != null) {
            if (context is ActivityResultRegistryOwner) {
                val permissionCall = context.activityResultRegistry.register(
                    "VPNPermission", PermissionContract(intent)
                ) { permissionGranted ->
                    if (permissionGranted) {
                        connectWithPermission(context, profile)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        (context as Activity).showNoVpnPermissionDialog()
                    }
                }
                permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
            } else {
                showInsufficientPermissionNotification(context)
            }
        } else {
            connectWithPermission(context, profile)
        }
    }

    private fun connectWithPermission(context: Context, profile: Profile) {
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
            MaterialDialog.Builder(context).theme(Theme.DARK)
                .content(R.string.serverNoWireguardSupport)
                .positiveText(R.string.close)
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

    protected open fun prepare(context: Context): Intent? = VpnService.prepare(context)

    private suspend fun disconnectBlocking() {
        Storage.delete(ConnectionParams::class.java)
        setSelfState(VpnState.Disabled)
        activeBackend?.disconnect()
        activeBackend?.active = false
        activeBackendObservable.value = null
        connectionParams = null
    }

    open fun disconnect() {
        ProtonLogger.log("Manually disconnecting")
        disconnectWithCallback()
    }

    suspend fun disconnectSync() {
        clearOngoingConnection()
        disconnectBlocking()
    }

    open fun disconnectWithCallback(afterDisconnect: (() -> Unit)? = null) {
        clearOngoingConnection()
        scope.launch {
            disconnectBlocking()
            vpnStateMonitor.onDisconnectedByUser.emit(Unit)
            afterDisconnect?.invoke()
        }
    }

    fun reconnect(context: Context) = scope.launch {
        clearOngoingConnection()
        if (activeBackend != null)
            activeBackend?.reconnect()
        else
            lastProfile?.let { connect(context, it, "reconnection") }
    }
}
