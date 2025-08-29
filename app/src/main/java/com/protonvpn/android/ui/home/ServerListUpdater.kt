/*
 * Copyright (c) 2019 Proton AG
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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicApiCallResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.UpdateAction
import com.protonvpn.android.appconfig.periodicupdates.registerAction
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.appconfig.periodicupdates.toPeriodicActionResultWithCustomValue
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.servers.FetchServerListWithStatus
import com.protonvpn.android.servers.IsBinaryServerStatusEnabled
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ServersCountResponse
import com.protonvpn.android.servers.api.StreamingServicesResponse
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.mapState
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import dagger.Reusable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
@Reusable
class ServerListUpdaterRemoteConfig(
    private val flow: StateFlow<Config>
) : StateFlow<ServerListUpdaterRemoteConfig.Config> by flow {
    data class Config(val backgroundDelayMs: Long, val foregroundDelayMs: Long)

    @Inject
    constructor(appConfig: AppConfig)
        : this(appConfig.appConfigFlow.mapState { response ->
            Config(
                backgroundDelayMs = TimeUnit.MINUTES.toMillis(response.logicalsRefreshBackgroundDelayMinutes),
                foregroundDelayMs = TimeUnit.MINUTES.toMillis(response.logicalsRefreshForegroundDelayMinutes),
            )
        })
}

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
    private val guestHole: GuestHole,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsLoggedIn private val loggedIn: Flow<Boolean>,
    @IsInForeground private val inForeground: Flow<Boolean>,
    private val remoteConfig: ServerListUpdaterRemoteConfig,
    @WallClock private val wallClock: () -> Long,
    private val fetchServerListWithStatus: FetchServerListWithStatus,
    private val binaryServerStatusEnabled: IsBinaryServerStatusEnabled,
    private val getTruncationMustHaveIDs: GetTruncationMustHaveIDs,
) {
    sealed interface Result {
        object Success : Result
        data class Error(val apiError: ApiResult.Error?) : Result
    }

    val ipAddress = prefs.ipAddressFlow

    // Country and ISP are used by "Report an issue" form.
    val lastKnownCountry: String? get() = prefs.lastKnownCountry
    val lastKnownIsp: String? get() = prefs.lastKnownIsp

    private val isDisconnected = vpnStateMonitor.status.map { it.state == VpnState.Disabled }

    private val serverListUpdate = UpdateAction("server_list", ::updateServers)
    private val locationUpdate = periodicUpdateManager.registerAction(
        "location",
        ::updateLocationIfVpnOff,
        PeriodicUpdateSpec(LOCATION_CALL_DELAY, setOf(inForeground, isDisconnected))
    )
    private val streamingServicesUpdate = periodicUpdateManager.registerApiCall(
        "streaming_services",
        ::updateStreamingServices,
        PeriodicUpdateSpec(STREAMING_CALL_DELAY, setOf(inForeground))
    )

    @VisibleForTesting
    suspend fun freeOnlyUpdateNeeded() =
        freeOnlyUpdateAllowed() &&
        wallClock() - prefs.lastFullUpdateTimestamp < FULL_SERVER_LIST_CALL_DELAY

    private suspend fun freeOnlyUpdateAllowed() =
        !binaryServerStatusEnabled() && currentUser.vpnUser()?.isFreeUser == true

    suspend fun needsUpdate() = serverManager.needsUpdate() ||
        wallClock() - serverManager.lastUpdateTimestamp >= 4 * remoteConfig.value.foregroundDelayMs

    init {
        migrateIpAddress()

        remoteConfig.onEach {
            val updateSpec = it.listUpdateSpec()
            if (updateSpec == null)
                periodicUpdateManager.unregister(serverListUpdate)
            else
                periodicUpdateManager.registerUpdateAction(serverListUpdate, *updateSpec)
        }.launchIn(scope)

        periodicUpdateManager.registerAction(
            "server_loads", ::updateLoads, PeriodicUpdateSpec(LOADS_CALL_DELAY, setOf(loggedIn, inForeground))
        )
        periodicUpdateManager.registerApiCall(
            "server_country_count",
            ::updateServerCountryCount,
            PeriodicUpdateSpec(SERVER_COUNT_CALL_DELAY, SERVER_COUNT_ERROR_DELAY, setOf(inForeground))
        )

        vpnStateMonitor.onDisconnectedByUser.onEach {
            periodicUpdateManager.executeNow(locationUpdate)
        }.launchIn(scope)

        prefs.ipAddressFlow
            .drop(1) // Skip initial value, observe only updates.
            .onEach {
                if (currentUser.isLoggedIn()) periodicUpdateManager.executeNow(serverListUpdate)
            }
            .launchIn(scope)
        currentUser.eventVpnLogin
            .onEach {
                if (serverManager.streamingServicesModel == null) {
                    periodicUpdateManager.executeNow(streamingServicesUpdate)
                }

                updateServerList(forceFreshUpdate = true)
            }
            .launchIn(scope)
        userPlanManager.planChangeFlow
            .onEach {
                updateServerList(forceFreshUpdate = true)
            }
            .launchIn(scope)
    }

    private fun ServerListUpdaterRemoteConfig.Config.listUpdateSpec() = buildList {
        if (foregroundDelayMs > 0)
            add(PeriodicUpdateSpec(foregroundDelayMs, setOf(loggedIn, inForeground)))
        if (backgroundDelayMs > 0)
            add(PeriodicUpdateSpec(backgroundDelayMs, setOf(loggedIn)))
    }.toTypedArray().takeIf { it.isNotEmpty() }

    fun onAppStart() {
        scope.launch {
            if (needsUpdate() && currentUser.isLoggedIn())
                updateServerList()
        }
    }

    private suspend fun updateLoads(): PeriodicActionResult<out Any> {
        serverManager.ensureLoaded()
        val statusId = serverManager.logicalsStatusId
        return if (binaryServerStatusEnabled() && statusId != null) {
            val result = api.getBinaryStatus(statusId)
            if (result is ApiResult.Success) {
                serverManager.updateBinaryLoads(statusId, result.value)
            }
            PeriodicApiCallResult(result)
        } else {
            val result = api.getLoads(getNetZone(), freeOnlyUpdateAllowed())
            if (result is ApiResult.Success) {
                serverManager.updateLoads(result.value.loadsList)
            }
            PeriodicApiCallResult(result)
        }
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
                prefs.lastKnownIpLatitude = latitude
                prefs.lastKnownIpLongitude = longitude
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

    suspend fun updateServerList(forceFreshUpdate: Boolean = false): Result {
        if (forceFreshUpdate) {
            // Force update regardless of the timestamp.
            prefs.serverListLastModified = 0
            prefs.lastFullUpdateTimestamp = 0
        }
        return periodicUpdateManager.executeNow(serverListUpdate)
    }

    private suspend fun updateStreamingServices(): ApiResult<StreamingServicesResponse> =
        api.getStreamingServices().apply {
            valueOrNull?.let { serverManager.setStreamingServices(it) }
        }

    private suspend fun updateServerCountryCount(): ApiResult<ServersCountResponse> =
        api.getServerCountryCount().also { response ->
            response.valueOrNull?.let {
                prefs.vpnServerCount = it.serverCount
                prefs.vpnCountryCount = it.countryCount
            }
        }

    @VisibleForTesting
    suspend fun updateServers(): PeriodicActionResult<Result> {
        val lang = Locale.getDefault().language
        val netzone = getNetZone()

        val serverListResult = coroutineScope {
            guestHole.runWithGuestHoleFallback {
                fetchServerListWithStatus(
                    netzone = netzone,
                    lang = lang,
                    freeOnly = freeOnlyUpdateNeeded(),
                    serverListLastModified = prefs.serverListLastModified
                )
            }
        }

        // TODO: consider moving the following part to a use-case (perhaps combine with FetchServerListWithStatus and
        //  rename it to UpdateServerListFromApi). This will allow removing a few more dependencies from ServerListUpdater.
        if (serverListResult is FetchServerListWithStatus.FetchResult.NewServers) {
            serverManager.ensureLoaded()
            val retainIDs = if (serverListResult.isListTruncated == true) {
                // retain only those ID that were not in must-haves for this call
                getTruncationMustHaveIDs() - serverListResult.usedMustHaveIDs
            } else {
                emptySet()
            }

            val newList = if (serverListResult.freeOnly == true) {
                serverManager.allServers.updateTier(serverListResult.newServers, VpnUser.FREE_TIER, retainIDs)
            } else {
                serverListResult.newServers
            }
            if (serverListResult.lastModified != null)
                prefs.serverListLastModified = serverListResult.lastModified.time

            DebugUtils.debugAssert("Country with no continent") {
                val countriesWithNoContinent = newList
                    .flatMapTo(HashSet()) { listOf(it.entryCountry, it.exitCountry) }
                    .filter { CountryTools.oldMapLocations[it]?.continent == null }
                countriesWithNoContinent.isEmpty()
            }

            serverManager.setServers(newList,  statusId = serverListResult.statusId,lang, retainIDs = retainIDs)
            serverManager.updateTimestamp()
            if (!serverListResult.freeOnly)
                prefs.lastFullUpdateTimestamp = wallClock()
        }

        return when(serverListResult) {
            is FetchServerListWithStatus.FetchResult.ApiError ->
                serverListResult.apiError.toPeriodicActionResultWithCustomValue(
                    Result.Error(serverListResult.apiError),
                    isSuccess = false,
                )

            is FetchServerListWithStatus.FetchResult.EmptyBody,
            is FetchServerListWithStatus.FetchResult.BinaryStatusError ->
                PeriodicActionResult(Result.Error(null), isSuccess = false)

            is FetchServerListWithStatus.FetchResult.NewServers,
            is FetchServerListWithStatus.FetchResult.NotModified ->
                PeriodicActionResult(Result.Success, isSuccess = true)
        }
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
        private val LOCATION_CALL_DELAY = TimeUnit.MINUTES.toMillis(10)
        private val LOADS_CALL_DELAY = TimeUnit.MINUTES.toMillis(15)
        val FULL_SERVER_LIST_CALL_DELAY = TimeUnit.DAYS.toMillis(2)
        private val STREAMING_CALL_DELAY = TimeUnit.DAYS.toMillis(2)
        private val SERVER_COUNT_CALL_DELAY = TimeUnit.DAYS.toMillis(7)
        private val SERVER_COUNT_ERROR_DELAY = TimeUnit.DAYS.toMillis(1)
        private const val HTTP_NOT_MODIFIED_304 = 304
    }
}

@VisibleForTesting
fun List<Server>.updateTier(update: List<Server>, tier: Int, retainIDs: Set<String>) : List<Server> {
    val updateIDs = update.mapTo(mutableSetOf()) { it.serverId }
    return update + filter {
        // keep servers that are not in the update if they are from different tier or in the retainIDs
        it.serverId !in updateIDs && (it.tier != tier || it.serverId in retainIDs)
    }
}
