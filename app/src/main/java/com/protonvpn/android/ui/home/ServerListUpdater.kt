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
package com.protonvpn.android.ui.home

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.NetworkResultCallback
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.StorageStringObservable
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.joda.time.DateTime

class ServerListUpdater(
    val coroutineContext: CoroutineContext,
    val api: ProtonApiRetroFit,
    val serverManager: ServerManager,
    val userData: UserData
) {
    companion object {
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(3)
        private val LIST_CALL_DELAY = TimeUnit.MINUTES.toMillis(3)

        private const val KEY_IP_ADDRESS = "IP_ADDRESS"
        private const val KEY_IP_ADDRESS_DATE = "IP_ADDRESS_DATE"

        private fun now() = SystemClock.elapsedRealtime()
    }

    private val scope = CoroutineScope(coroutineContext)

    private var homeActivity: HomeActivity? = null
    private var inForeground = false
    var isVpnDisconnected = true

    private var lastIpCheck = Long.MIN_VALUE
    private var lastServerListUpdate = Long.MIN_VALUE

    val ipAddress = StorageStringObservable(KEY_IP_ADDRESS)

    private val task = ReschedulableTask(scope, ::now) {
        if (userData.isLoggedIn) {
            if (isVpnDisconnected && (now() >= lastIpCheck + LOCATION_CALL_DELAY)) {
                if (updateLocation())
                    updateServerList(homeActivity)
            }
            if (serverManager.isOutdated || (inForeground && now() >= lastServerListUpdate + LIST_CALL_DELAY))
                updateServerList(homeActivity)

            if (inForeground)
                scheduleIn(LIST_CALL_DELAY)
        }
    }

    private fun dateToRealtime(date: Long) =
            now() - (DateTime().millis - date).coerceAtLeast(0)

    init {
        val lastIpCheckDate = Storage.getLong(KEY_IP_ADDRESS_DATE, 0L)
        lastIpCheck = dateToRealtime(lastIpCheckDate)
        lastServerListUpdate = dateToRealtime(serverManager.updatedAt?.millis ?: 0L)
    }

    fun onHomeActivityCreated(activity: HomeActivity) {
        homeActivity = activity
        if (serverManager.isOutdated)
            task.scheduleIn(0)

        activity.lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                inForeground = true
                task.scheduleIn(0)
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                inForeground = false
                task.cancelSchedule()
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                homeActivity = null
            }
        })
    }

    fun onDisconnectedByUser() {
        task.scheduleIn(0)
    }

    fun getServersList(networkLoader: NetworkLoader): Job = scope.launch(Dispatchers.Main) {
        updateServerList(networkLoader)
    }

    // Returns true if IP has changed
    private suspend fun updateLocation() = suspendCoroutine<Boolean> { continuation ->
        api.getLocation { location ->
            var ipChanged = false
            location?.let { loc ->
                val newIp = loc.ipAddress
                if (newIp.isNotEmpty() && newIp != ipAddress.value) {
                    ipAddress.setValue(newIp)
                    ipChanged = true
                }
                lastIpCheck = now()
                Storage.saveLong(KEY_IP_ADDRESS_DATE, DateTime().millis)
            }
            continuation.resume(ipChanged)
        }
    }

    private suspend fun updateServerList(
        networkLoader: NetworkLoader?
    ): Boolean = suspendCoroutine { continuation ->
        val strippedIP = ipAddress.value?.takeIf { it.isNotEmpty() }?.let { NetUtils.stripIP(it) }

        networkLoader?.networkFrameLayout?.setRetryListener {
            scope.launch(Dispatchers.Main) {
                updateServerList(networkLoader)
            }
        }

        // The following route is used to retrieve VPN server information, including scores for
        // the best server to connect to depending on a user's proximity to a server and its load.
        // To provide relevant scores even when connected to VPN, we send a truncated version of
        // the user's public IP address. In keeping with our no-logs policy, this partial IP address
        // is not stored on the server and is only used to fulfill this one-off API request.
        api.getServerList(networkLoader, strippedIP, object : NetworkResultCallback<ServerList> {
            override fun onSuccess(serverList: ServerList?) {
                if (serverList != null) {
                    serverManager.setServers(serverList.serverList)
                    lastServerListUpdate = now()
                }
                continuation.resume(serverList != null)
            }

            override fun onFailure() {
                continuation.resume(false)
            }
        })
    }
}
