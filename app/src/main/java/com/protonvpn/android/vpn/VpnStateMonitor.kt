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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiManager
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.DISCONNECT_ACTION
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType.AUTH_FAILED
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType.AUTH_FAILED_INTERNAL
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType.MAX_SESSIONS
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType.NO_PORTS_AVAILABLE
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType.UNPAID
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
import javax.inject.Singleton

@Singleton
open class VpnStateMonitor(
    private val userData: UserData,
    private val api: ProtonApiRetroFit,
    private val backendProvider: VpnBackendProvider,
    private val serverListUpdater: ServerListUpdater,
    private val trafficMonitor: TrafficMonitor,
    apiManager: ProtonApiManager,
    private val scope: CoroutineScope
) : VpnStateSource {

    enum class ErrorType {
        AUTH_FAILED_INTERNAL, AUTH_FAILED, PEER_AUTH_FAILED,
        LOOKUP_FAILED, UNREACHABLE, SESSION_IN_USE,
        MAX_SESSIONS, UNPAID, GENERIC_ERROR,
        NO_PORTS_AVAILABLE
    }

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

    private var connectionParams: ConnectionParams? = null
    private var lastProfile: Profile? = null

    override val selfStateObservable = MutableLiveData<VpnState>(Disabled)

    // State taken from active backend or from monitor when no active backend, value always != null
    private val stateInternal: LiveData<VpnState> = Transformations.switchMap(
        activeBackendObservable.eagerMapNotNull { it ?: this }, VpnStateSource::selfStateObservable)
    private val state get() = stateInternal.value!!

    val vpnStatus: LiveData<Status> = stateInternal.eagerMapNotNull(ignoreIfEqual = true) {
        var newState = it ?: Disabled
        if ((newState as? Error)?.type == AUTH_FAILED_INTERNAL) {
            newState = CheckingAvailability
            debugAssert { ongoingConnect == null }
            ongoingConnect = scope.launch {
                checkAuthFailedReason()
            }
        }
        Status(newState, connectionParams)
    }

    val isConnected get() = state == Connected && connectionParams != null

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
            isConnected && connectionParams?.server == server

    fun isConnectedToAny(servers: List<Server>) =
            isConnected && connectionParams?.server?.domain?.let { connectingToDomain ->
                connectingToDomain in servers.asSequence().map { it.domain }
            } == true

    val retryInfo get() = activeBackend?.retryInfo

    init {
        Log.i("create state monitor")
        bindTrafficMonitor()
        apiManager.initVpnState(this)
        ProtonApplication.getAppContext().registerBroadcastReceiver(IntentFilter(DISCONNECT_ACTION)) { intent ->
            when (intent?.action) {
                DISCONNECT_ACTION -> disconnect()
            }
        }

        stateInternal.observeForever {
            Storage.saveString(STORAGE_KEY_STATE, state.name)

            ProtonLogger.log("VpnStateMonitor state=${it.name} backend=${activeBackend?.name}")
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

    private fun bindTrafficMonitor() {
        trafficMonitor.init(vpnStatus)
        trafficMonitor.trafficStatus.observeForever {
            updateNotification(it)
        }
    }

    private fun activateBackend(newBackend: VpnBackend) {
        debugAssert {
            activeBackend == null || activeBackend == newBackend
        }
        if (activeBackend != newBackend) {
            activeBackend?.active = false
            newBackend.active = true
            activeBackendObservable.value = newBackend
            activeBackend?.setSelfState(Connecting)
            setSelfState(Disabled)
        }
    }

    private suspend fun checkAuthFailedReason() {
        var errorType = AUTH_FAILED
        if (userData.vpnInfoResponse.isUserDelinquent) {
            errorType = UNPAID
        } else {
            activeBackend?.setSelfState(CheckingAvailability)
            val sessionCount = api.getSession().valueOrNull?.sessionList?.size ?: 0
            if (userData.vpnInfoResponse.maxSessionCount <= sessionCount)
                errorType = MAX_SESSIONS
        }
        ongoingConnect = null
        activeBackend?.setSelfState(Error(errorType))
    }

    private suspend fun coroutineConnect(profile: Profile) {
        // If smart profile fails we need this to handle reconnect request
        lastProfile = profile
        val server = profile.server!!
        ProtonLogger.log("Connect: ${server.domain}")

        if (profile.getProtocol(userData) == VpnProtocol.Smart)
            setSelfState(ScanningPorts)

        val preparedConnection = backendProvider.prepareConnection(profile, server, userData)
        if (preparedConnection == null) {
            ProtonLogger.log("Smart protocol: no protocol available for ${server.domain}")
            setSelfState(Error(NO_PORTS_AVAILABLE))
            return
        }

        val newBackend = preparedConnection.backend
        if (activeBackend != newBackend)
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
        connect(context, profile, null)
    }

    fun connect(context: Context, profile: Profile, prepareIntentHandler: ((Intent) -> Unit)? = null) {
        val intent = prepare(context)
        if (intent != null) {
            if (prepareIntentHandler != null) {
                prepareIntentHandler(intent)
            } else {
                NotificationHelper.showInformationNotification(
                        context,
                        context.getString(R.string.insufficientPermissionsDetails),
                        context.getString(R.string.insufficientPermissionsTitle),
                        icon = R.drawable.ic_notification_disconnected)
            }
        } else {
            if (profile.server != null) {
                clearOngoingConnection()
                ongoingConnect = scope.launch {
                    coroutineConnect(profile)
                }
            } else {
                NotificationHelper.showInformationNotification(
                        context, context.getString(R.string.error_server_not_set))
            }
        }
    }

    protected open fun prepare(context: Context): Intent? =
            VpnService.prepare(context)

    private suspend fun disconnectBlocking() {
        Storage.delete(ConnectionParams::class.java)
        setSelfState(Disabled)
        activeBackend?.disconnect()
        activeBackend?.active = false
        activeBackendObservable.value = null
        connectionParams = null
    }

    open fun disconnect() {
        clearOngoingConnection()
        scope.launch {
            disconnectBlocking()
            serverListUpdater.onDisconnectedByUser()
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
