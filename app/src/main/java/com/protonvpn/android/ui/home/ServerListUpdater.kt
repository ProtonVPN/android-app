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
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicApiCallResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerAction
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.LoadsResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerListUpdater @Inject constructor(
    private val scope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val serverManager: ServerManager,
    private val currentUser: CurrentUser,
    private val vpnStateMonitor: VpnStateMonitor,
    userPlanManager: UserPlanManager,
    private val prefs: ServerListUpdaterPrefs,
    private val getNetZone: GetNetZone,
    private val partnershipsRepository: PartnershipsRepository,
    private val guestHole: GuestHole,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsLoggedIn loggedIn: Flow<Boolean>,
    @IsInForeground inForeground: Flow<Boolean>
) {
    val ipAddress = prefs.ipAddressFlow

    // Country and ISP are used by "Report an issue" form.
    val lastKnownCountry: String? get() = prefs.lastKnownCountry
    val lastKnownIsp: String? get() = prefs.lastKnownIsp

    private val isDisconnected = vpnStateMonitor.status.map { it.state == VpnState.Disabled }

    private val serverListUpdate = periodicUpdateManager.registerApiCall(
        "server_list",
        ::updateServers,
        { null as NetworkLoader? },
        PeriodicUpdateSpec(LIST_CALL_DELAY, setOf(loggedIn, inForeground))
    )
    private val locationUpdate = periodicUpdateManager.registerAction(
        "location",
        ::updateLocationIfVpnOff,
        PeriodicUpdateSpec(LOCATION_CALL_DELAY, setOf(inForeground, isDisconnected))
    )
    private val streamingServicesUpdate = periodicUpdateManager.registerApiCall(
        "streaming_services",
        ::updateStreamingServices,
        PeriodicUpdateSpec(STREAMING_CALL_DELAY, setOf(loggedIn, inForeground))
    )

    init {
        migrateIpAddress()

        periodicUpdateManager.registerApiCall(
            "server_loads", ::updateLoads, PeriodicUpdateSpec(LOADS_CALL_DELAY, setOf(loggedIn, inForeground))
        )

        vpnStateMonitor.onDisconnectedByUser.onEach {
            periodicUpdateManager.executeNow(locationUpdate)
        }.launchIn(scope)

        prefs.ipAddressFlow
            .drop(1) // Skip initial value, observe only updates.
            .onEach {
                if (currentUser.isLoggedIn()) periodicUpdateManager.executeNow(serverListUpdate)
            }.launchIn(scope)
        currentUser.eventVpnLogin
            .onEach {
                if (serverManager.streamingServicesModel == null)
                    periodicUpdateManager.executeNow(streamingServicesUpdate)
                periodicUpdateManager.executeNow(serverListUpdate)
            }
            .launchIn(scope)
        userPlanManager.planChangeFlow
            .onEach { periodicUpdateManager.executeNow(serverListUpdate) }
            .launchIn(scope)
    }

    fun onAppStart() {
        if (serverManager.isOutdated) {
            scope.launch {
                if (currentUser.isLoggedIn()) updateServerList()
            }
        }
    }

    fun getServersList(networkLoader: NetworkLoader?): Job = scope.launch(Dispatchers.Main) {
        updateServerList(networkLoader)
    }

    private suspend fun updateLoads(): ApiResult<LoadsResponse> {
        val result = api.getLoads(getNetZone())
        if (result is ApiResult.Success) {
            serverManager.updateLoads(result.value.loadsList)
        }
        return result
    }

    @VisibleForTesting
    suspend fun updateLocationIfVpnOff(): PeriodicActionResult<out Any> {
        val cancelResult = PeriodicActionResult(Unit, true)
        if (!vpnStateMonitor.isDisabled)
            return cancelResult

        return coroutineScope {
            val locationUpdate = async { updateLocationFromApi() }
            val monitorJob = vpnStateMonitor.status
                .onEach {
                    if (it.state != VpnState.Disabled)
                        locationUpdate.cancel()
                }.launchIn(this)
            try {
                PeriodicApiCallResult(locationUpdate.await())
            } catch (_: CancellationException) {
                cancelResult
            } finally {
                monitorJob.cancel()
            }
        }
    }

    private suspend fun updateLocationFromApi(): ApiResult<UserLocation> {
        val result = api.getLocation()
        if (result is ApiResult.Success && vpnStateMonitor.isDisabled) {
            with(result.value) {
                prefs.lastKnownCountry = country
                prefs.lastKnownIsp = isp
                ProtonLogger.logCustom(LogCategory.APP, "location: $country, isp: $isp (as seen by API)")
            }

            val newIp = result.value.ipAddress
            if (newIp.isNotEmpty()) {
                getNetZone.updateIp(newIp)
                return result
            }
        }
        return result
    }

    suspend fun updateServerList(loader: NetworkLoader? = null): ApiResult<ServerList> =
        periodicUpdateManager.executeNow(serverListUpdate, loader)

    private suspend fun updateStreamingServices(): ApiResult<StreamingServicesResponse> =
        api.getStreamingServices().apply {
            valueOrNull?.let { serverManager.setStreamingServices(it) }
        }

    private suspend fun updateServers(networkLoader: NetworkLoader?): ApiResult<ServerList> {
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
                api.getServerList(null, netzone, lang, realProtocolsNames).also { serverListResult ->
                    if (serverListResult.havePartnership()) {
                        partnershipsRepository.refresh()
                    }
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

    private fun ApiResult<ServerList>.havePartnership(): Boolean =
        this is ApiResult.Success && value.serverList.any { it.isPartneshipServer }

    private fun migrateIpAddress() {
        if (prefs.ipAddress.isEmpty()) {
            val oldKey = "IP_ADDRESS"
            val oldValue = Storage.getString(oldKey, "")
            prefs.ipAddress = oldValue
            Storage.delete(oldKey)
        }
    }

    companion object {
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(10)
        private val LOADS_CALL_DELAY = TimeUnit.MINUTES.toMillis(15)
        val STREAMING_CALL_DELAY = TimeUnit.DAYS.toMillis(2)
        val LIST_CALL_DELAY = TimeUnit.HOURS.toMillis(3)
    }
}
