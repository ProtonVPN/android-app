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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.bus.ConnectedToServer
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.DISCONNECT_ACTION
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.utils.implies
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorState.AUTH_FAILED
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorState.AUTH_FAILED_INTERNAL
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorState.MAX_SESSIONS
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorState.NO_ERROR
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorState.UNPAID
import com.protonvpn.android.vpn.VpnStateMonitor.State.CHECKING_AVAILABILITY
import com.protonvpn.android.vpn.VpnStateMonitor.State.CONNECTED
import com.protonvpn.android.vpn.VpnStateMonitor.State.CONNECTING
import com.protonvpn.android.vpn.VpnStateMonitor.State.DISABLED
import com.protonvpn.android.vpn.VpnStateMonitor.State.ERROR
import com.protonvpn.android.vpn.VpnStateMonitor.State.RECONNECTING
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
open class VpnStateMonitor(
    private val userData: UserData,
    private val api: ProtonApiRetroFit,
    private val backendProvider: VpnBackendProvider,
    private val serverListUpdater: ServerListUpdater,
    private val trafficMonitor: TrafficMonitor,
    coroutineContext: CoroutineContext
) {

    enum class State {
        DISABLED, CHECKING_AVAILABILITY, WAITING_FOR_NETWORK, CONNECTING, CONNECTED, RECONNECTING,
        DISCONNECTING, ERROR
    }

    enum class ErrorState {
        NO_ERROR, AUTH_FAILED_INTERNAL, AUTH_FAILED, PEER_AUTH_FAILED, LOOKUP_FAILED, UNREACHABLE,
        SESSION_IN_USE, MAX_SESSIONS, UNPAID, GENERIC_ERROR
    }

    data class ConnectionInfo(val profile: Profile, val server: Server) {
        init {
            profile.server!!.reinitFromOldProfile(server)
        }
    }

    data class VpnState(
        val state: State,
        val error: ConnectionError?,
        val connectionInfo: ConnectionInfo?
    ) {
        val profile get() = connectionInfo?.profile
        val server get() = connectionInfo?.server
    }

    companion object {
        private const val STORAGE_KEY_STATE = "VpnStateMonitor.VPN_STATE_NAME"
    }

    private val scope = CoroutineScope(coroutineContext)
    private var ongoingConnect: Job? = null
    private val activeBackendObservable = MutableLiveData<VpnBackend?>()
    private val activeBackend: VpnBackend? get() = activeBackendObservable.value

    private var connectionInfo: ConnectionInfo? = null

    private val stateInternal: LiveData<State> =
            Transformations.switchMap(activeBackendObservable) {
                it?.stateObservable
            }

    val vpnState: LiveData<VpnState> = stateInternal.eagerMapNotNull(ignoreIfEqual = true) {
        var newState = it ?: DISABLED
        if (newState == ERROR && error?.errorState == AUTH_FAILED_INTERNAL) {
            newState = CHECKING_AVAILABILITY
            debugAssert { ongoingConnect == null }
            ongoingConnect = scope.launch {
                checkAuthFailedReason()
            }
        }
        VpnState(newState, error, connectionInfo)
    }

    private val state get() = stateInternal.value ?: DISABLED
    private val error get() = activeBackend?.error

    val isConnected get() = state == CONNECTED && connectionInfo != null

    val connectingToServer
        get() = connectionInfo?.server?.takeIf {
            state == CONNECTED || state == CONNECTING
        }

    val connectionProfile
        get() = connectionInfo?.profile

    val isConnectingToSecureCore
        get() = connectingToServer?.isSecureCoreServer == true

    val connectionProtocolString
        get() = connectionProfile?.getProtocol(userData)

    fun isConnectedTo(server: Server?) =
            isConnected && connectionInfo?.server == server

    fun isConnectedToAny(servers: List<Server>) =
            isConnected && connectionInfo?.server?.domain?.let { connectingToDomain ->
                connectingToDomain in servers.asSequence().map { it.domain }
            } == true

    val retryInfo get() = activeBackend?.retryInfo

    init {
        Log.i("create state monitor")
        bindTrafficMonitor()
        ProtonApplication.getAppContext().registerBroadcastReceiver(IntentFilter(DISCONNECT_ACTION)) { intent ->
            when (intent?.action) {
                DISCONNECT_ACTION -> disconnect()
            }
        }

        stateInternal.observeForever {
            Storage.saveString(STORAGE_KEY_STATE, state.name)

            Log.i("VpnStateMonitor state=$it error=${error?.errorState} backend=${activeBackend?.name}")
            debugAssert {
                (state in arrayOf(CONNECTING, CONNECTED, RECONNECTING))
                        .implies(connectionInfo != null && activeBackend != null)
            }
            serverListUpdater.isVpnDisconnected = state == DISABLED

            when (state) {
                CONNECTED -> {
                    EventBus.postOnMain(ConnectedToServer(Storage.load(Server::class.java)))
                }
                DISABLED -> {
                    activeBackend?.error?.errorState = NO_ERROR
                    EventBus.postOnMain(ConnectedToServer(null))
                }
                RECONNECTING -> {
                }
                else -> Log.d("Current state: $it")
            }
            updateNotification(null)
        }
    }

    private fun bindTrafficMonitor() {
        trafficMonitor.init(vpnState)
        trafficMonitor.trafficStatus.observeForever {
            updateNotification(it)
        }
    }

    private fun activateBackend(profile: Profile? = null) {
        val newBackend = backendProvider.getFor(userData, profile)
        debugAssert {
            activeBackend == null || activeBackend == newBackend
        }
        if (activeBackend != newBackend) {
            activeBackend?.active = false
            newBackend.active = true
            activeBackendObservable.value = newBackend
        }
    }

    private suspend fun checkAuthFailedReason() {
        var error = AUTH_FAILED
        if (userData.vpnInfoResponse.isUserDelinquent) {
            error = UNPAID
        } else {
            activeBackend?.setState(CHECKING_AVAILABILITY)
            val result = getSession()
            if (userData.vpnInfoResponse.maxSessionCount <= result.sessionList.size)
                error = MAX_SESSIONS
        }
        ongoingConnect = null
        activeBackend?.error?.errorState = error
        activeBackend?.setState(ERROR)
    }

    private suspend fun coroutineConnect(profile: Profile) {
        if (activeBackend != null && activeBackend != backendProvider.getFor(userData, profile)) {
            disconnectBlocking()
        }

        val serverToConnect = profile.server!!.prepareForConnection(userData)
        Storage.save(serverToConnect)
        // We need to save and use direct server as well, as using just Profile is not enough
        // in cases where profile is pointing to random server
        connectionInfo = ConnectionInfo(profile, serverToConnect)

        activateBackend(profile)
        activeBackend?.connect()
        ongoingConnect = null
    }

    protected open suspend fun getSession(): SessionListResponse = suspendCancelableCoroutine { continuation ->
        val call = api.getSession { result ->
            continuation.resume(result)
        }
        continuation.invokeOnCancellation {
            call?.cancel()
        }
    }

    private fun clearOngoingConnection() {
        ongoingConnect?.cancel()
        ongoingConnect = null
    }

    fun onRestoreProcess(context: Context, profile: Profile): Boolean {
        if (state == DISABLED && Storage.getString(STORAGE_KEY_STATE, null) == CONNECTED.name) {
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
                NotificationHelper.showInformationNotification(context, context.getString(R.string.error_server_not_set))
            }
        }
    }

    protected open fun prepare(context: Context): Intent? =
            VpnService.prepare(context)

    private suspend fun disconnectBlocking() {
        Storage.delete(Server::class.java)
        activeBackend?.disconnect()
        activeBackend?.active = false
        activeBackendObservable.value = null
        connectionInfo = null
    }

    open fun disconnect() {
        clearOngoingConnection()
        scope.launch {
            disconnectBlocking()
            serverListUpdater.onDisconnectedByUser()
        }
    }

    fun reconnect() = scope.launch {
        clearOngoingConnection()
        activateBackend()
        activeBackend?.reconnect()
    }

    fun buildNotification() =
        NotificationHelper.buildStatusNotification(vpnState.value!!, null)

    private fun updateNotification(trafficUpdate: TrafficUpdate?) {
        NotificationHelper.updateStatusNotification(
                ProtonApplication.getAppContext(), vpnState.value!!, trafficUpdate)
    }
}
