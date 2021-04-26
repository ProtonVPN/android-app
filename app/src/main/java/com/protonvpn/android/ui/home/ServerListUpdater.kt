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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.StorageStringObservable
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit

class ServerListUpdater(
    val scope: CoroutineScope,
    val api: ProtonApiRetroFit,
    val serverManager: ServerManager,
    val userData: UserData,
    val vpnStateMonitor: VpnStateMonitor,
    userPlanManager: UserPlanManager,
) {
    companion object {
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(3)
        private val LOADS_CALL_DELAY = TimeUnit.MINUTES.toMillis(15)
        val LIST_CALL_DELAY = TimeUnit.HOURS.toMillis(3)
        private val MIN_CALL_DELAY = minOf(LOCATION_CALL_DELAY, LOADS_CALL_DELAY, LIST_CALL_DELAY)

        private const val KEY_IP_ADDRESS = "IP_ADDRESS"
        private const val KEY_IP_ADDRESS_DATE = "IP_ADDRESS_DATE"
        private const val KEY_LOADS_UPDATE_DATE = "LOADS_UPDATE_DATE"

        private fun now() = SystemClock.elapsedRealtime()
    }

    private var networkLoader: NetworkLoader? = null
    private var inForeground = false

    private var lastIpCheck = Long.MIN_VALUE
    private val lastServerListUpdate get() =
        dateToRealtime(serverManager.updatedAt?.millis ?: 0L)
    private var lastLoadsUpdateInternal = Long.MIN_VALUE

    val ipAddress = StorageStringObservable(KEY_IP_ADDRESS)

    init {
        scope.launch {
            userPlanManager.planChangeFlow.collect {
                updateServerList()
            }
        }
        scope.launch {
            vpnStateMonitor.onDisconnectedByUser.collect {
                task.scheduleIn(0)
            }
        }
    }

    private val task = ReschedulableTask(scope, ::now) {
        if (userData.isLoggedIn) {
            if (vpnStateMonitor.isDisabled && now() >= lastIpCheck + LOCATION_CALL_DELAY) {
                if (updateLocation())
                    updateServerList(networkLoader)
            }
            if (serverManager.isOutdated || inForeground && now() >= lastServerListUpdate + LIST_CALL_DELAY)
                updateServerList(networkLoader)
            else if (inForeground && now() >= lastLoadsUpdate + LOADS_CALL_DELAY)
                updateLoads()

            if (inForeground)
                scheduleIn(MIN_CALL_DELAY)
        }
    }

    private val strippedIP
        get() = ipAddress.value?.takeIf { it.isNotEmpty() }?.let { NetUtils.stripIP(it) }

    private val lastLoadsUpdate
        get() = lastLoadsUpdateInternal.coerceAtLeast(lastServerListUpdate)

    private fun dateToRealtime(date: Long) =
            now() - (DateTime().millis - date).coerceAtLeast(0)

    init {
        val lastIpCheckDate = Storage.getLong(KEY_IP_ADDRESS_DATE, 0L)
        lastIpCheck = dateToRealtime(lastIpCheckDate)
        lastLoadsUpdateInternal = dateToRealtime(Storage.getLong(KEY_LOADS_UPDATE_DATE, 0L))
    }

    fun startSchedule(lifecycle: Lifecycle, loader: NetworkLoader?) {
        networkLoader = loader
        if (serverManager.isOutdated)
            task.scheduleIn(0)

        lifecycle.addObserver(object : LifecycleObserver {
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
                networkLoader = null
            }
        })
    }

    fun getServersList(networkLoader: NetworkLoader?): Job = scope.launch(Dispatchers.Main) {
        updateServerList(networkLoader)
    }

    private suspend fun updateLoads(): Boolean {
        val result = api.getLoads(strippedIP)
        if (result is ApiResult.Success) {
            serverManager.updateLoads(result.value.loadsList)
            lastLoadsUpdateInternal = now()
            Storage.saveLong(KEY_LOADS_UPDATE_DATE, DateTime().millis)
            return true
        }
        return false
    }

    // Returns true if IP has changed
    suspend fun updateLocation(): Boolean {
        val result = api.getLocation()
        var ipChanged = false
        if (result is ApiResult.Success) {
            val newIp = result.value.ipAddress
            if (newIp.isNotEmpty() && newIp != ipAddress.value) {
                ipAddress.setValue(newIp)
                ipChanged = true
            }
            lastIpCheck = now()
            Storage.saveLong(KEY_IP_ADDRESS_DATE, DateTime().millis)
        }
        return ipChanged
    }

    suspend fun updateServerList(
        networkLoader: NetworkLoader? = null
    ): ApiResult<ServerList> {
        val loaderUI = networkLoader?.networkFrameLayout

        loaderUI?.setRetryListener {
            scope.launch(Dispatchers.Main) {
                updateServerList(networkLoader)
            }
        }

        // The following route is used to retrieve VPN server information, including scores for
        // the best server to connect to depending on a user's proximity to a server and its load.
        // To provide relevant scores even when connected to VPN, we send a truncated version of
        // the user's public IP address. In keeping with our no-logs policy, this partial IP address
        // is not stored on the server and is only used to fulfill this one-off API request.
        val result = api.getServerList(loaderUI, strippedIP)
        if (result is ApiResult.Success) {
            serverManager.setServers(result.value.serverList)
            val streamingServices = api.getStreamingServices().valueOrNull
            if (streamingServices != null)
                serverManager.setStreamingServices(streamingServices)
        }
        return result
    }
}
