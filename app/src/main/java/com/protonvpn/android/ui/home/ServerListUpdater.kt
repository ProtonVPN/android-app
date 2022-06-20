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

import android.telephony.TelephonyManager
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.jitterMs
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerListUpdater @Inject constructor(
    val scope: CoroutineScope,
    val api: ProtonApiRetroFit,
    val serverManager: ServerManager,
    val currentUser: CurrentUser,
    val vpnStateMonitor: VpnStateMonitor,
    userPlanManager: UserPlanManager,
    private val prefs: ServerListUpdaterPrefs,
    private val telephonyManager: TelephonyManager?,
    @WallClock private val wallClock: () -> Long,
    @ElapsedRealtimeClock private val elapsedRealtimeMs: () -> Long,
) {
    private var networkLoader: NetworkLoader? = null
    private var inForeground = false

    private var lastIpCheck = Long.MIN_VALUE
    private val lastServerListUpdate get() =
        dateToRealtime(serverManager.lastUpdateTimestamp)
    private var lastLoadsUpdateInternal = Long.MIN_VALUE

    val ipAddress = prefs.ipAddressFlow

    // Country and ISP are used by "Report an issue" form.
    val lastKnownCountry: String? get() = prefs.lastKnownCountry
    val lastKnownIsp: String? get() = prefs.lastKnownIsp

    init {
        migrateIpAddress()

        lastIpCheck = dateToRealtime(prefs.ipAddressCheckTimestamp)
        lastLoadsUpdateInternal = dateToRealtime(prefs.loadsUpdateTimestamp)

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

    private val task = ReschedulableTask(scope, elapsedRealtimeMs) {
        val nextScheduleDelay = updateTask()
        if (nextScheduleDelay > 0) scheduleIn(nextScheduleDelay)
    }

    private val lastLoadsUpdate
        get() = lastLoadsUpdateInternal.coerceAtLeast(lastServerListUpdate)

    private fun dateToRealtime(date: Long) =
        elapsedRealtimeMs() - (wallClock() - date).coerceAtLeast(0)

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

    @VisibleForTesting
    suspend fun updateTask(): Long {
        if (currentUser.isLoggedIn()) {
            val now = elapsedRealtimeMs()
            if (now >= lastIpCheck + LOCATION_CALL_DELAY) {
                if (updateLocationIfVpnOff())
                    updateServerList(networkLoader)
            }
            if (serverManager.isOutdated || inForeground && now >= lastServerListUpdate + LIST_CALL_DELAY)
                updateServerList(networkLoader)
            else if (inForeground && now >= lastLoadsUpdate + LOADS_CALL_DELAY)
                updateLoads()

            if (inForeground)
                return jitterMs(MIN_CALL_DELAY)
        }
        return -1
    }

    private suspend fun updateLoads(): Boolean {
        val result = api.getLoads(getNetZone())
        if (result is ApiResult.Success) {
            serverManager.updateLoads(result.value.loadsList)
            lastLoadsUpdateInternal = elapsedRealtimeMs()
            prefs.loadsUpdateTimestamp = wallClock()
            return true
        }
        return false
    }

    // Returns true if IP has changed
    suspend fun updateLocationIfVpnOff(): Boolean {
        if (!vpnStateMonitor.isDisabled)
            return false

        val result = api.getLocation()
        var ipChanged = false
        if (result is ApiResult.Success && vpnStateMonitor.isDisabled) {
            val newIp = result.value.ipAddress
            if (newIp.isNotEmpty() && newIp != prefs.ipAddress) {
                prefs.ipAddress = newIp
                ipChanged = true
            }
            with(result.value) {
                prefs.lastKnownCountry = country
                prefs.lastKnownIsp = isp
            }
            lastIpCheck = elapsedRealtimeMs()
            prefs.ipAddressCheckTimestamp = wallClock()
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
        loaderUI?.switchToLoading()

        api.getStreamingServices().valueOrNull?.let {
            serverManager.setStreamingServices(it)
        }

        val lang = Locale.getDefault().language
        val result = api.getServerList(null, getNetZone(), lang)
        if (result is ApiResult.Success) {
            serverManager.setServers(result.value.serverList, lang)
        }
        loaderUI?.switchToEmpty()
        return result
    }

    @VisibleForTesting
    fun setInForegroundForTest(foreground: Boolean) {
        inForeground = foreground
    }

    private fun migrateIpAddress() {
        if (prefs.ipAddress.isEmpty()) {
            val oldKey = "IP_ADDRESS"
            val oldValue = Storage.getString(oldKey, "")
            prefs.ipAddress = oldValue
            Storage.delete(oldKey)
        }
    }

    // Used in routes that provide server information including a score of how good a server is
    // for the particular user to connect to.
    // To provide relevant scores even when connected to VPN, we send a truncated version of
    // the user's public IP address. In keeping with our no-logs policy, this partial IP address
    // is not stored on the server and is only used to fulfill this one-off API request. If IP
    // is not available/outdated we send network's MCC.
    private fun getNetZone() = when {
        prefs.ipAddress.isNotBlank() && lastIpCheck >= wallClock() - IP_VALIDITY_MS ->
            NetUtils.stripIP(prefs.ipAddress)
        telephonyManager != null && telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_CDMA ->
            telephonyManager.networkCountryIso
        prefs.ipAddress.isNotBlank() ->
            NetUtils.stripIP(prefs.ipAddress)
        else -> null
    }

    companion object {
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(3)
        private val LOADS_CALL_DELAY = TimeUnit.MINUTES.toMillis(15)
        val LIST_CALL_DELAY = TimeUnit.HOURS.toMillis(3)
        private val MIN_CALL_DELAY = minOf(LOCATION_CALL_DELAY, LOADS_CALL_DELAY, LIST_CALL_DELAY)
        val IP_VALIDITY_MS = TimeUnit.DAYS.toMillis(1)
    }
}
