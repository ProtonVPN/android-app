/*
 * Copyright (c) 2017 Proton AG
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
package com.protonvpn.android.utils

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.appconfig.UserCountryPhysical
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.excludedlocations.ExcludedLocations
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper.ProfileType
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.models.vpn.usecase.SmartProtocols
import com.protonvpn.android.models.vpn.usecase.supportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.isExcluded
import com.protonvpn.android.redesign.vpn.satisfiesFeatures
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.servers.ServersDataManager.ServerLists
import com.protonvpn.android.servers.ServersResult
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ServerManagerState(
    val serverListAppVersionCode: Int = 0,
    @Deprecated("The timestamp is now stored by ServersDataManager.")
    val lastUpdateTimestamp: Long = 0L,

    /** Can be checked even before servers are loaded from storage */
    val hasGateways: Boolean = false,
    val hasCountries: Boolean = false,
)

@Deprecated("Use ServerManager2 in new code")
@Singleton
class ServerManager @Inject constructor(
    mainScope: CoroutineScope,
    val serversDataManager: ServersDataManager,
    val physicalUserCountry: UserCountryPhysical,
) {
    private var savedState: ServerManagerState

    private var guestHoleServers: List<Server>? = null
    private val isLoaded = MutableStateFlow(false)

    // Expose a version number of the server list so that it can be used in flow operators like
    // combine to react to updates.
    val serverListVersion = MutableStateFlow(0)

    val isDownloadedAtLeastOnce get() = serversDataManager.lastUpdateTimestamp > 0
    /** Can be checked even before servers are loaded from storage */
    val hasCountriesFlow = serverListVersion.map { savedState.hasCountries }.distinctUntilChanged()
    val hasGatewaysFlow = serverListVersion.map { savedState.hasGateways }.distinctUntilChanged()

    // The cached values are to be used only by legacy code.
    private val serversDataCachedFlow =
        serversDataManager.serverLists.stateIn(mainScope, SharingStarted.Eagerly, ServerLists.Empty)
    private val serversDataCached get() = serversDataCachedFlow.value // May be empty.

    suspend fun needsUpdate(): Boolean {
        val serversData = serversDataManager.serverLists.first()
        return serversDataManager.lastUpdateTimestamp == 0L || serversData.allServers.isEmpty() ||
            !haveWireGuardSupport() || savedState.serverListAppVersionCode < BuildConfig.VERSION_CODE
    }

    val allServers get() = serversDataCached.allServers
    val allServersByScore get() = serversDataCached.allServersByScore

    val freeCountries
        get() = getVpnCountries()
            .filter { country -> country.serverList.any { server -> server.isFreeServer } }

    init {
        val loadedState = Storage.load(ServerManager::class.java, ServerManagerState.serializer())
        if (loadedState != null) {
            savedState = loadedState
            serverListVersion.value = 1
        } else {
            savedState = ServerManagerState()
        }

        mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Starts undispatched to call load() immediately. This makes sure initialization
            // happens before any other calls to ServersDataManager.
            // It's not very robust, we should improve it in the future.
            val loaded = serversDataManager.load()
            if (!loaded) {
                // We had servers saved but failed to load them, reset state.
                updateAndSave(
                    savedState.copy(
                        lastUpdateTimestamp = 0L,
                        hasGateways = false,
                        hasCountries = false,
                    )
                )
            } else if (
                serversDataManager.lastUpdateTimestamp == 0L &&
                savedState.lastUpdateTimestamp > 0
            ) {
                serversDataManager.updateLastUpdateTimestamp(savedState.lastUpdateTimestamp)
                updateAndSave(savedState.copy(lastUpdateTimestamp = 0L))
            }
            // Yield to allow pending work to process, most importantly serversDataCachedFlow.
            // This is a hack, once we rewrite the TV UI all the synchronous getters will be
            // removed and this will not be needed.
            yield()
            // Notify of loaded state and update after everything has been updated.
            isLoaded.value = true
        }
        serversDataManager.serverLists.onEach { serversData ->
            updateAndSave(
                savedState.copy(
                    serverListAppVersionCode = BuildConfig.VERSION_CODE,
                    hasGateways = serversData.gateways.isNotEmpty(),
                    hasCountries = serversData.vpnCountries.isNotEmpty(),
                )
            )
            serverListVersion.value++
        }.launchIn(mainScope)
    }

    suspend fun ensureLoaded() {
        isLoaded.first { isLoaded -> isLoaded }
    }

    fun getExitCountries(secureCore: Boolean) = if (secureCore)
        serversDataCached.secureCoreExitCountries else serversDataCached.vpnCountries

    override fun toString(): String {
        val lastUpdateTimestampLog = serversDataManager.lastUpdateTimestamp
                .takeIf { it != 0L }
                ?.let { ProtonLogger.formatTime(it) }
        return "vpnCountries: ${serversDataCached.vpnCountries.size} gateways: ${serversDataCached.gateways.size}" +
            " exit: ${serversDataCached.secureCoreExitCountries.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        savedState = ServerManagerState()
        Storage.delete(ServerManager::class.java)
        // The server list itself is not deleted.
    }

    suspend fun setGuestHoleServers(serverList: List<Server>) {
        DebugUtils.debugAssert("Guest hole servers can only be set when regular servers are not available") {
            !isDownloadedAtLeastOnce
        }
        setServers(serverList, null)
    }

    fun getDownloadedServersForGuestHole(
        serverCount: Int,
        protocol: ProtocolSelection,
        smartProtocols: SmartProtocols
    ): List<Server> {
        val bestScoreServer = getBestScoreServer(
            allServersByScore.filter { it.online },
            vpnUser = null,
            protocol,
            smartProtocols
        )
        val servers = listOfNotNull(bestScoreServer) +
            getExitCountries(false).flatMap { country ->
                country.serverList.filter { it.online && supportsProtocol(it, protocol, smartProtocols) }
            }

        return servers.takeRandomStable(serverCount).shuffled().distinct()
    }

    @VisibleForTesting
    suspend fun setServers(
        serverList: List<Server>,
        statusId: LogicalsStatusId?,
        retainIDs: Set<String> = emptySet()
    ) {
        ensureLoaded()
        serversDataManager.replaceServers(serverList, statusId, retainIDs)
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        ensureLoaded()
        serversDataManager.updateServerDomainStatus(connectingDomain)
    }

    fun getServerById(id: String) =
        allServers.firstOrNull { it.serverId == id } ?: guestHoleServers?.firstOrNull { it.serverId == id }

    fun getVpnCountries(): List<VpnCountry> = serversDataCached.vpnCountries.sortedByLocaleAware { it.countryName }

    fun getGateways(): List<GatewayGroup> = serversDataCached.gateways

    @Deprecated("Use the suspending getVpnExitCountry from ServerManager2")
    fun getVpnExitCountry(countryCode: String, secureCoreCountry: Boolean): VpnCountry? =
        getExitCountries(secureCoreCountry).firstOrNull { it.flag == countryCode }

    @VisibleForTesting
    fun getBestScoreServer(
        serverList: Iterable<Server>,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection,
        smartProtocols: SmartProtocols
    ): Server? {
        val eligibleServers = serverList.sortedBy { it.score }.asSequence()
            .filter { supportsProtocol(it, protocol, smartProtocols) }
        return with(eligibleServers) {
            firstOrNull { vpnUser.hasAccessToServer(it) && it.online }
                ?: firstOrNull { vpnUser.hasAccessToServer(it) }
                ?: firstOrNull()
        }
    }

    fun getRandomServer(vpnUser: VpnUser?, protocol: ProtocolSelection, smartProtocols: SmartProtocols): Server? {
        val allCountries = getExitCountries(secureCore = false)
        val accessibleCountries = allCountries.filter { it.hasAccessibleOnlineServer(vpnUser) }
        return accessibleCountries
            .ifEmpty { allCountries }
            .randomNullable()
            ?.let { getRandomServer(it, vpnUser, protocol, smartProtocols) }
    }

    private fun getRandomServer(
        country: VpnCountry,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection,
        smartProtocols: SmartProtocols
    ): Server? {
        val online = country.serverList.filter(Server::online)
        val accessible = online.filter {
            vpnUser.hasAccessToServer(it) && supportsProtocol(it, protocol, smartProtocols)
        }
        return accessible.randomNullable()
    }

    fun getSecureCoreExitCountries(): List<VpnCountry> =
        serversDataCached.secureCoreExitCountries.sortedByLocaleAware { it.countryName }

    @Deprecated("Use getServerForConnectIntent")
    fun getServerForProfile(
        profile: Profile,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection,
        smartProtocols: SmartProtocols
    ): Server? {
        val wrapper = profile.wrapper
        return when (wrapper.type) {
            ProfileType.FASTEST -> {
                val tvServers = allServersByScore.filter { it.online && !it.isGatewayServer }
                getBestScoreServer(tvServers, vpnUser, protocol, smartProtocols)
            }

            ProfileType.RANDOM ->
                getRandomServer(vpnUser, protocol, smartProtocols)

            ProfileType.RANDOM_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, false)?.let {
                    getRandomServer(it, vpnUser, protocol, smartProtocols)
                }

            ProfileType.FASTEST_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, false)?.let {
                    getBestScoreServer(it.serverList, vpnUser, protocol, smartProtocols)
                }

            ProfileType.DIRECT ->
                getServerById(wrapper.serverId!!)
        }
    }

    fun getBestServerForConnectIntent(
        connectIntent: AnyConnectIntent,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection,
        smartProtocols: SmartProtocols,
        excludedLocations: ExcludedLocations,
    ): Server? = forConnectIntent(
        connectIntent = connectIntent,
        fallbackResult = null,
        excludedLocations = excludedLocations,
    ) { serversResult ->
        getBestScoreServer(serversResult.servers, vpnUser, protocol, smartProtocols)
    }

    /*
     * Perform operations related to ConnectIntent.
     *
     * ConnectIntent can specify either a fastest server overall, fastest in country, a specific server and so on.
     * Use this function to implement operations for a ConnectIntent like checking if its country/city/server is
     * available.
     */
    fun <T> forConnectIntent(
        connectIntent: AnyConnectIntent,
        fallbackResult: T,
        excludedLocations: ExcludedLocations,
        onServersResult: (ServersResult) -> T,
    ): T {
        fun Iterable<Server>.filterFeatures() = filter { it.satisfiesFeatures(connectIntent.features) }
        fun Server.satisfiesFeatures() = satisfiesFeatures(connectIntent.features)

        fun allRegularServersFor(
            secureCore: Boolean,
            features: Set<ServerFeature>,
            excludedCountryId: CountryId?,
        ): Sequence<Server> {
            var serversSequence = allServersByScore.asSequence()

            if (excludedCountryId != null) {
                serversSequence = serversSequence.filterNot { it.exitCountry == excludedCountryId.countryCode }
            }

            return serversSequence.filter { server ->
                server.isSecureCoreServer == secureCore &&
                        !server.isGatewayServer &&
                        server.satisfiesFeatures(features)
            }
        }

        return when (connectIntent) {
            is ConnectIntent.FastestInCountry ->
                if (connectIntent.country.isFastest) {
                    var hasAppliedExclusions = false

                    allRegularServersFor(
                        secureCore = false,
                        features = connectIntent.features,
                        excludedCountryId = ifOrNull(
                            predicate = connectIntent.country.isFastestExcludingMyCountry,
                            block = physicalUserCountry::invoke,
                        ),
                    )
                        .asIterable()
                        .filterNot { server ->
                            server.isExcluded(excludedLocations).also { isExcluded ->
                                if (isExcluded) {
                                    hasAppliedExclusions = true
                                }
                            }
                        }
                        .let { servers -> ServersResult.Regular(servers, hasAppliedExclusions) }
                        .let { serversResult -> onServersResult(serversResult) }
                } else {
                    getVpnExitCountry(connectIntent.country.countryCode, false)
                        ?.serverList
                        ?.filterFeatures()
                        .handleServersResult(onServersResult, fallbackResult)
                }

            is ConnectIntent.FastestInCity -> {
                getVpnExitCountry(connectIntent.country.countryCode, false)
                    ?.serverList
                    ?.filter { server -> server.city == connectIntent.cityEn && server.satisfiesFeatures() }
                    .handleServersResult(onServersResult, fallbackResult)
            }

            is ConnectIntent.FastestInState -> {
                getVpnExitCountry(connectIntent.country.countryCode, false)
                    ?.serverList
                    ?.filter { server -> server.state == connectIntent.stateEn && server.satisfiesFeatures() }
                    .handleServersResult(onServersResult, fallbackResult)
            }

            is ConnectIntent.SecureCore ->
                if (connectIntent.exitCountry.isFastest) {
                    var hasAppliedExclusions = false

                    allRegularServersFor(
                        secureCore = true,
                        features = connectIntent.features,
                        excludedCountryId = ifOrNull(
                            predicate = connectIntent.exitCountry.isFastestExcludingMyCountry,
                            block = physicalUserCountry::invoke,
                        ),
                    )
                        .asIterable()
                        .filterNot { server ->
                            server.isExcluded(excludedLocations).also { isExcluded ->
                                if (isExcluded) {
                                    hasAppliedExclusions = true
                                }
                            }
                        }
                        .let { servers -> ServersResult.Regular(servers, hasAppliedExclusions) }
                        .let { serversResult -> onServersResult(serversResult) }
                } else {
                    getVpnExitCountry(connectIntent.exitCountry.countryCode, true)
                        ?.serverList
                        ?.let { servers ->
                            if (connectIntent.entryCountry.isFastest) {
                                servers.filterFeatures()
                            } else {
                                servers.find { server ->
                                    server.entryCountry == connectIntent.entryCountry.countryCode && server.satisfiesFeatures()
                                }
                                ?.let(::listOf)
                            }
                        }
                        .handleServersResult(onServersResult, fallbackResult)
                }

            is ConnectIntent.Gateway -> {
                if (connectIntent.serverId != null) {
                    getServerById(connectIntent.serverId).handleServersResult(onServersResult, fallbackResult)
                } else {
                    getGateways()
                        .find { it.name() == connectIntent.gatewayName }
                        ?.serverList
                        ?.filterFeatures()
                        .handleServersResult(onServersResult, fallbackResult)
                }
            }

            is ConnectIntent.Server -> {
                getServerById(connectIntent.serverId).handleServersResult(onServersResult, fallbackResult)
            }

            is AnyConnectIntent.GuestHole -> {
                getServerById(connectIntent.serverId).handleServersResult(onServersResult, fallbackResult)
            }
        }
    }

    private fun updateAndSave(newState: ServerManagerState) {
        savedState = newState
        Storage.save(savedState, ServerManager::class.java, ServerManagerState.serializer())
    }

    private fun haveWireGuardSupport() =
        serversDataCached.allServers.any { server -> server.connectingDomains.any { it.publicKeyX25519 != null } }

    private fun <T> Server?.handleServersResult(
        onServersResult: (ServersResult) -> T,
        fallbackResult: T,
    ): T = this?.let(::listOf).handleServersResult(onServersResult, fallbackResult)

    private fun <T> List<Server>?.handleServersResult(
        onServersResult: (ServersResult) -> T,
        fallbackResult: T,
    ): T = this?.let { servers -> onServersResult(ServersResult.Regular(servers)) } ?: fallbackResult

}
