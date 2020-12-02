/*
 * Copyright (c) 2019 Proton Technologies AG
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
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.components.BaseActivityV2.Companion.showNoVpnPermissionDialog
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.DISCONNECT_ACTION
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.isChromeOS
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import com.protonvpn.android.vpn.PermissionContract.Companion.VPN_PERMISSION_ACTIVITY
import com.protonvpn.android.vpn.VpnState.CheckingAvailability
import com.protonvpn.android.vpn.VpnState.Connected
import com.protonvpn.android.vpn.VpnState.Connecting
import com.protonvpn.android.vpn.VpnState.Disabled
import com.protonvpn.android.vpn.VpnState.Error
import com.protonvpn.android.vpn.VpnState.Reconnecting
import com.protonvpn.android.vpn.VpnState.ScanningPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.proton.core.network.domain.NetworkManager
import javax.inject.Singleton

@Singleton
open class VpnStateMonitor(
    private val appContext: Context,
    private val userData: UserData,
    private val api: ProtonApiRetroFit,
    private val backendProvider: VpnBackendProvider,
    private val serverListUpdater: ServerListUpdater,
    private val trafficMonitor: TrafficMonitor,
    private val networkManager: NetworkManager,
    private val maintenanceTracker: MaintenanceTracker,
    private val scope: CoroutineScope
) : VpnStateSource {

    data class Status(
        val state: VpnState,
        val connectionParams: ConnectionParams?
    ) {
        val profile get() = connectionParams?.profile
        val server get() = connectionParams?.server
    }

    companion object {
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"
    }

    private var ongoingConnect: Job? = null
    private val activeBackendObservable = MutableLiveData<VpnBackend?>()
    private val activeBackend: VpnBackend? get() = activeBackendObservable.value

    var connectionParams: ConnectionParams? = null
    private var lastProfile: Profile? = null

    override val selfStateObservable = MutableLiveData<VpnState>(Disabled)

    // State taken from active backend or from monitor when no active backend, value always != null
    private val stateInternal: LiveData<VpnState> = Transformations.switchMap(
        activeBackendObservable.eagerMapNotNull { it ?: this }, VpnStateSource::selfStateObservable)
    private val state get() = stateInternal.value!!

    val vpnStatus: LiveData<Status> = stateInternal.eagerMapNotNull(ignoreIfEqual = true) {
        var newState = it ?: Disabled
        if ((newState as? Error)?.type == ErrorType.AUTH_FAILED_INTERNAL) {
            newState = CheckingAvailability
            debugAssert { ongoingConnect == null }
            ongoingConnect = scope.launch {
                checkAuthFailedReason()
            }
        }
        Status(newState, connectionParams)
    }

    val isConnected get() = state == Connected && connectionParams != null
    val isDisabled get() = state == Disabled

    val connectingToServer
        get() = connectionParams?.server?.takeIf {
            state == Connected || state == Connecting
        }

    val connectionProfile
        get() = connectionParams?.profile

    val isConnectingToSecureCore
        get() = connectingToServer?.isSecureCoreServer == true

    val connectionProtocol
        get() = connectionParams?.protocol

    val exitIP
        get() = connectionParams?.exitIpAddress

    fun isConnectedTo(server: Server?) =
            isConnected && connectionParams?.server?.serverId == server?.serverId

    fun isConnectingToCountry(country: String) =
        connectingToServer?.exitCountry == country

    fun isConnectedToAny(servers: List<Server>) =
            isConnected && connectionParams?.server?.domain?.let { connectingToDomain ->
                connectingToDomain in servers.asSequence().map { it.domain }
            } == true

    val retryInfo get() = activeBackend?.retryInfo

    var initialized = false

    init {
        Log.i("create state monitor")
        bindTrafficMonitor()
        maintenanceTracker.initWithStateMonitor(this)
        appContext.registerBroadcastReceiver(IntentFilter(DISCONNECT_ACTION)) { intent ->
            when (intent?.action) {
                DISCONNECT_ACTION -> disconnect()
            }
        }

        stateInternal.observeForever {
            if (initialized) {
                Storage.saveString(STORAGE_KEY_STATE, state.name)

                ProtonLogger.log("VpnStateMonitor state=$it backend=${activeBackend?.name}")
                debugAssert {
                    (state in arrayOf(Connecting, Connected, Reconnecting))
                            .implies(connectionParams != null && activeBackend != null)
                }
                serverListUpdater.isVpnDisconnected = state == Disabled

                when (state) {
                    Connected -> {
                        EventBus.postOnMain(ConnectedToServer(Storage.load(Server::class.java)))
                    }
                    Disabled -> {
                        EventBus.postOnMain(ConnectedToServer(null))
                    }
                }
                updateNotification(null)
            }
        }

        initialized = true
    }

    private fun bindTrafficMonitor() {
        trafficMonitor.init(vpnStatus)
        if (!(appContext.isChromeOS() || appContext.isTV())) {
            trafficMonitor.trafficStatus.observeForever {
                updateNotification(it)
            }
        }
    }

    private fun activateBackend(newBackend: VpnBackend) {
        debugAssert {
            activeBackend == null || activeBackend == newBackend
        }
        if (activeBackend != newBackend) {
            activeBackend?.active = false
            newBackend.active = true
            newBackend.setSelfState(Connecting)
            activeBackendObservable.value = newBackend
            setSelfState(Disabled)
        }
    }

    private suspend fun checkAuthFailedReason() {
        var errorType = ErrorType.AUTH_FAILED
        val vpnInfoResponse = userData.vpnInfoResponse
        if (vpnInfoResponse != null) {
            if (vpnInfoResponse.isUserDelinquent) {
                errorType = ErrorType.UNPAID
            } else {
                activeBackend?.setSelfState(CheckingAvailability)
                val sessionCount = api.getSession().valueOrNull?.sessionList?.size ?: 0
                if (vpnInfoResponse.maxSessionCount <= sessionCount)
                    errorType = ErrorType.MAX_SESSIONS
            }
        }
        if (!maintenanceTracker.checkMaintenanceReconnect()) {
            ongoingConnect = null
            activeBackend?.setSelfState(Error(errorType))
        }
    }

    private suspend fun coroutineConnect(profile: Profile) {
        // If smart profile fails we need this to handle reconnect request
        lastProfile = profile
        val server = profile.server!!
        ProtonLogger.log("Connect: ${server.domain}")

        if (profile.getProtocol(userData) == VpnProtocol.Smart)
            setSelfState(ScanningPorts)

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

        val newBackend = preparedConnection.backend
        if (activeBackend != null && activeBackend != newBackend)
            disconnectBlocking()

        connectionParams = preparedConnection.connectionParams
        ProtonLogger.log("Smart protocol: using ${connectionParams?.info}")

        Storage.save(connectionParams, ConnectionParams::class.java)
        activateBackend(newBackend)
        activeBackend?.connect()
        ongoingConnect = null
    }

    private fun clearOngoingConnection() {
        ongoingConnect?.cancel()
        ongoingConnect = null
    }

    fun onRestoreProcess(context: Context, profile: Profile): Boolean {
        if (state == Disabled && Storage.getString(STORAGE_KEY_STATE, null) == Connected.name) {
            connect(context, profile)
            return true
        }
        return false
    }

    fun connect(context: Context, profile: Profile) {
        val intent = prepare(context)
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
                permissionCall.launch(VPN_PERMISSION_ACTIVITY)
            } else {
                showInsufficientPermissionNotification(context)
            }
        } else {
            connectWithPermission(context, profile)
        }
    }

    private fun connectWithPermission(context: Context, profile: Profile) {
        if (profile.server != null) {
            clearOngoingConnection()
            ongoingConnect = scope.launch {
                coroutineConnect(profile)
            }
        } else {
            NotificationHelper.showInformationNotification(
                context, context.getString(R.string.error_server_not_set)
            )
        }
    }

    private fun showInsufficientPermissionNotification(context: Context) {
        NotificationHelper.showInformationNotification(
            context,
            context.getString(R.string.insufficientPermissionsDetails),
            context.getString(R.string.insufficientPermissionsTitle),
            icon = R.drawable.ic_notification_disconnected
        )
    }

    protected open fun prepare(context: Context): Intent? = VpnService.prepare(context)

    private suspend fun disconnectBlocking() {
        Storage.delete(ConnectionParams::class.java)
        setSelfState(Disabled)
        activeBackend?.disconnect()
        activeBackend?.active = false
        activeBackendObservable.value = null
        connectionParams = null
    }

    open fun disconnect() {
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
            serverListUpdater.onDisconnectedByUser()
            afterDisconnect?.invoke()
        }
    }

    fun reconnect(context: Context) = scope.launch {
        clearOngoingConnection()
        if (activeBackend != null)
            activeBackend?.reconnect()
        else
            lastProfile?.let { connect(context, it) }
    }

    fun buildNotification() =
        NotificationHelper.buildStatusNotification(vpnStatus.value!!, null)

    private fun updateNotification(trafficUpdate: TrafficUpdate?) {
        NotificationHelper.updateStatusNotification(
                ProtonApplication.getAppContext(), vpnStatus.value!!, trafficUpdate)
    }
}
