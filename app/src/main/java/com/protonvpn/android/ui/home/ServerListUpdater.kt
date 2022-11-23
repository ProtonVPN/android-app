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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.jitterMs
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
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
    @WallClock private val wallClock: () -> Long,
    private val getNetZone: GetNetZone,
    private val partnershipsRepository: PartnershipsRepository,
    private val guestHole: GuestHole
) {
    private var networkLoader: NetworkLoader? = null
    private var inForeground = false

    private val lastServerListUpdate get() = serverManager.lastUpdateTimestamp

    val ipAddress = prefs.ipAddressFlow

    // Country and ISP are used by "Report an issue" form.
    val lastKnownCountry: String? get() = prefs.lastKnownCountry
    val lastKnownIsp: String? get() = prefs.lastKnownIsp

    private val task = ReschedulableTask(scope, wallClock) {
        val nextScheduleDelay = updateTask()
        if (nextScheduleDelay > 0) scheduleIn(nextScheduleDelay)
    }

    init {
        migrateIpAddress()

        userPlanManager.planChangeFlow.onEach {
            updateServerList()
        }.launchIn(scope)

        vpnStateMonitor.onDisconnectedByUser.onEach {
            task.scheduleIn(0)
        }.launchIn(scope)
    }


    private val lastLoadsUpdate
        get() = prefs.loadsUpdateTimestamp.coerceAtLeast(lastServerListUpdate)

    fun onAppStart() {
        task.scheduleIn(0)
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

    @VisibleForTesting
    suspend fun updateTask(): Long {
        if (currentUser.isLoggedIn()) {
            val now = wallClock()
            if (now >= getNetZone.lastLocationIpCheck + LOCATION_CALL_DELAY) {
                val newNetZone = updateLocationIfVpnOff()
                if (newNetZone != null && newNetZone != prefs.lastNetzoneForLogicals)
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
            prefs.loadsUpdateTimestamp = wallClock()
            return true
        }
        return false
    }

    // Returns new netzone or null if not updated.
    suspend fun updateLocationIfVpnOff(): String? {
        if (!vpnStateMonitor.isDisabled)
            return null

        val result = api.getLocation()
        if (result is ApiResult.Success && vpnStateMonitor.isDisabled) {
            with(result.value) {
                prefs.lastKnownCountry = country
                prefs.lastKnownIsp = isp
                ProtonLogger.logCustom(LogCategory.APP, "location: $country, isp: $isp (as seen by API)")
            }

            val newIp = result.value.ipAddress
            if (newIp.isNotEmpty()) {
                getNetZone.updateIpFromLocation(newIp)
                return getNetZone()
            }
        }
        return null
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

        val lang = Locale.getDefault().language
        val netzone = getNetZone()
        val realProtocolsNames = ProtocolSelection.REAL_PROTOCOLS.map {
            it.apiName
        }

        val serverListResult = coroutineScope {
            guestHole.runWithGuestHoleFallback {
                val streamingServicesJob = launch {
                    api.getStreamingServices().valueOrNull?.let {
                        serverManager.setStreamingServices(it)
                    }
                }
                val partnershipsJob = launch {
                    partnershipsRepository.refresh()
                }
                api.getServerList(null, netzone, lang, realProtocolsNames).also {
                    // Make sure all requests finish before the UI is updated.
                    joinAll(streamingServicesJob, partnershipsJob)
                }
            }
        }

        if (serverListResult is ApiResult.Success) {
            prefs.lastNetzoneForLogicals = netzone
            serverManager.setServers(serverListResult.value.serverList, lang)
        }
        loaderUI?.switchToEmpty()
        return serverListResult
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

    companion object {
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(3)
        private val LOADS_CALL_DELAY = TimeUnit.MINUTES.toMillis(15)
        val LIST_CALL_DELAY = TimeUnit.HOURS.toMillis(3)
        private val MIN_CALL_DELAY = minOf(LOCATION_CALL_DELAY, LOADS_CALL_DELAY, LIST_CALL_DELAY)
    }
}
