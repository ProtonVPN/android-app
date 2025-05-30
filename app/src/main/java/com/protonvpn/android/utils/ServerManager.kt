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
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper.ProfileType
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.LoadUpdate
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.satisfiesFeatures
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.ui.home.GetUserCountry
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.Serializable
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("User ServerManager2 in new code")
@Singleton
class ServerManager @Inject constructor(
    @Transient private val mainScope: CoroutineScope,
    @Transient val currentUser: CurrentUser,
    @Transient @WallClock private val wallClock: () -> Long,
    @Transient val supportsProtocol: SupportsProtocol,
    @Transient val serversData: ServersDataManager,
    @Transient val getUserCountry: GetUserCountry,
) : Serializable {

    private var serverListAppVersionCode = 0

    @Transient private var guestHoleServers: List<Server>? = null
    @Transient private val isLoaded = MutableStateFlow(false)

    private var streamingServices: StreamingServicesResponse? = null
    val streamingServicesModel: StreamingServicesModel?
        get() = streamingServices?.let { StreamingServicesModel(it) }

    var lastUpdateTimestamp: Long = 0L
        private set

    var translationsLang: String? = null
        private set

    // Expose a version number of the server list so that it can be used in flow operators like
    // combine to react to updates.
    @Transient
    val serverListVersion = MutableStateFlow(0)

    /** Can be checked even before servers are loaded from storage */
    private var hasDownloadedServers: Boolean = false

    /** Can be checked even before servers are loaded from storage */
    private var hasGateways: Boolean = false

    /** Can be checked even before servers are loaded from storage */
    val isDownloadedAtLeastOnce get() = lastUpdateTimestamp > 0 && hasDownloadedServers

    @Transient
    val isDownloadedAtLeastOnceFlow = serverListVersion.map { isDownloadedAtLeastOnce }.distinctUntilChanged()

    @Transient
    val hasGatewaysFlow = serverListVersion.map { hasGateways }.distinctUntilChanged()

    suspend fun needsUpdate(): Boolean {
        ensureLoaded()
        return lastUpdateTimestamp == 0L || serversData.allServers.isEmpty() ||
            !haveWireGuardSupport() || serverListAppVersionCode < BuildConfig.VERSION_CODE ||
            translationsLang != Locale.getDefault().language
    }

    val allServers get() = serversData.allServers
    val allServersByScore get() = serversData.allServersByScore

    val freeCountries
        get() = getVpnCountries()
            .filter { country -> country.serverList.any { server -> server.isFreeServer } }

    init {
        val oldManager =
            Storage.load(ServerManager::class.java)
        if (oldManager != null) {
            streamingServices = oldManager.streamingServices
            lastUpdateTimestamp = oldManager.lastUpdateTimestamp
            serverListAppVersionCode = oldManager.serverListAppVersionCode
            translationsLang = oldManager.translationsLang
            hasDownloadedServers = oldManager.hasDownloadedServers
            hasGateways = oldManager.hasGateways

            serverListVersion.value = 1
        }

        mainScope.launch {
            serversData.load()
            updateInternal()

            // Notify of loaded state and update after everything has been updated.
            isLoaded.value = true
            onServersUpdate()
        }
    }

    suspend fun ensureLoaded() {
        isLoaded.first { isLoaded -> isLoaded }
    }

    fun getExitCountries(secureCore: Boolean) = if (secureCore)
        serversData.secureCoreExitCountries else serversData.vpnCountries

    private fun updateInternal() {
        hasGateways = serversData.gateways.isNotEmpty()
        hasDownloadedServers = true
    }

    private fun onServersUpdate() {
        ++serverListVersion.value
    }

    override fun toString(): String {
        val lastUpdateTimestampLog = lastUpdateTimestamp.takeIf { it != 0L }?.let { ProtonLogger.formatTime(it) }
        return "vpnCountries: ${serversData.vpnCountries.size} gateways: ${serversData.gateways.size}" +
            " exit: ${serversData.secureCoreExitCountries.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        lastUpdateTimestamp = 0L
        Storage.delete(ServerManager::class.java)
        updateInternal()
        // The server list itself is not deleted.
    }

    suspend fun setGuestHoleServers(serverList: List<Server>) {
        setServers(serverList, null)
        lastUpdateTimestamp = 0L
    }

    @VisibleForTesting
    fun setBuiltInGuestHoleServersForTesting(serverList: List<Server>) {
        guestHoleServers = serverList
    }

    fun getDownloadedServersForGuestHole(serverCount: Int, protocol: ProtocolSelection) =
        (listOfNotNull(getBestScoreServer(false, emptySet(), null, protocol)) +
            getExitCountries(false).flatMap { country ->
                country.serverList.filter { it.online && supportsProtocol(it, protocol) }
            }.takeRandomStable(serverCount).shuffled()
            ).distinct().take(serverCount)

    suspend fun setServers(serverList: List<Server>, language: String?, retainIDs: Set<String> = emptySet()) {
        ensureLoaded()
        serversData.replaceServers(serverList, retainIDs)

        lastUpdateTimestamp = wallClock()
        serverListAppVersionCode = BuildConfig.VERSION_CODE
        translationsLang = language
        Storage.save(this, ServerManager::class.java)

        updateInternal()
        onServersUpdate()
    }

    fun updateTimestamp() {
        lastUpdateTimestamp = wallClock()
        Storage.save(this, ServerManager::class.java)
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        ensureLoaded()
        serversData.updateServerDomainStatus(connectingDomain)

        Storage.save(this, ServerManager::class.java)
        onServersUpdate()
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        ensureLoaded()
        serversData.updateLoads(loadsList)

        Storage.save(this, ServerManager::class.java)
        onServersUpdate()
    }

    suspend fun updateOrAddServer(server: Server) {
        ensureLoaded()
        serversData.updateOrAddServer(server)

        Storage.save(this, ServerManager::class.java)
        onServersUpdate()
    }

    fun getGuestHoleServers(): List<Server> =
        guestHoleServers ?: run {
            FileUtils.getObjectFromAssets(
                ListSerializer(Server.serializer()), GuestHole.GUEST_HOLE_SERVERS_ASSET
            ).apply {
                guestHoleServers = this
            }
        }

    fun getServerById(id: String) =
        allServers.firstOrNull { it.serverId == id } ?: getGuestHoleServers().firstOrNull { it.serverId == id }

    fun getVpnCountries(): List<VpnCountry> = serversData.vpnCountries.sortedByLocaleAware { it.countryName }

    fun getGateways(): List<GatewayGroup> = serversData.gateways

    @Deprecated("Use the suspending getVpnExitCountry from ServerManager2")
    fun getVpnExitCountry(countryCode: String, secureCoreCountry: Boolean): VpnCountry? =
        getExitCountries(secureCoreCountry).firstOrNull { it.flag == countryCode }

    fun getBestScoreServer(
        secureCore: Boolean,
        serverFeatures: Set<ServerFeature>,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection,
        excludedCountryId: CountryId? = null
    ): Server? {
        val excludedCountry = excludedCountryId?.countryCode
        val eligibleServers = serversData.allServersByScore.asSequence()
            .filter {
                it.online
                    && supportsProtocol(it, protocol)
                    && it.isSecureCoreServer == secureCore
                    && it.satisfiesFeatures(serverFeatures)
                    && !it.isGatewayServer
                    && it.exitCountry != excludedCountry
            }
        return with(eligibleServers) { firstOrNull { vpnUser.hasAccessToServer(it) } ?: firstOrNull() }
    }

    @VisibleForTesting
    fun getBestScoreServer(serverList: List<Server>, vpnUser: VpnUser?, protocol: ProtocolSelection): Server? {
        val eligibleServers = serverList.sortedBy { it.score }.asSequence()
            .filter { it.online && supportsProtocol(it, protocol) }
        return with(eligibleServers) { firstOrNull { vpnUser.hasAccessToServer(it) } ?: firstOrNull() }
    }

    fun getRandomServer(vpnUser: VpnUser?, protocol: ProtocolSelection): Server? {
        val allCountries = getExitCountries(secureCore = false)
        val accessibleCountries = allCountries.filter { it.hasAccessibleOnlineServer(vpnUser) }
        return accessibleCountries
            .ifEmpty { allCountries }
            .randomNullable()
            ?.let { getRandomServer(it, vpnUser, protocol) }
    }

    private fun getRandomServer(country: VpnCountry, vpnUser: VpnUser?, protocol: ProtocolSelection): Server? {
        val online = country.serverList.filter(Server::online)
        val accessible = online.filter { vpnUser.hasAccessToServer(it) && supportsProtocol(it, protocol) }
        return accessible.randomNullable()
    }

    fun getSecureCoreExitCountries(): List<VpnCountry> =
        serversData.secureCoreExitCountries.sortedByLocaleAware { it.countryName }

    @Deprecated("Use getServerForConnectIntent")
    fun getServerForProfile(profile: Profile, vpnUser: VpnUser?, protocol: ProtocolSelection): Server? {
        val wrapper = profile.wrapper
        val needsSecureCore = profile.isSecureCore ?: false
        return when (wrapper.type) {
            ProfileType.FASTEST ->
                getBestScoreServer(needsSecureCore, emptySet(), vpnUser, protocol)

            ProfileType.RANDOM ->
                getRandomServer(vpnUser, protocol)

            ProfileType.RANDOM_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, needsSecureCore)?.let {
                    getRandomServer(it, vpnUser, protocol)
                }

            ProfileType.FASTEST_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, needsSecureCore)?.let {
                    getBestScoreServer(it.serverList, vpnUser, protocol)
                }

            ProfileType.DIRECT ->
                getServerById(wrapper.serverId!!)
        }
    }

    fun getBestServerForConnectIntent(connectIntent: AnyConnectIntent, vpnUser: VpnUser?, protocol: ProtocolSelection): Server? =
        forConnectIntent(
            connectIntent,
            onFastest = { isSecureCore, serverFeatures, excludedCountry ->
                getBestScoreServer(isSecureCore, serverFeatures, vpnUser, protocol, excludedCountry)
            },
            onFastestInGroup = { servers -> getBestScoreServer(servers, vpnUser, protocol) },
            onServer = { server -> server },
            fallbackResult = null
        )

    /*
     * Perform operations related to ConnectIntent.
     *
     * ConnectIntent can specify either a fastest server overall, fastest in country, a specific server and so on.
     * Use this function to implement operations for a ConnectIntent like checking if its country/city/server is
     * available.
     */
    fun <T> forConnectIntent(
        connectIntent: AnyConnectIntent,
        onFastest: (isSecureCore: Boolean, serverFeatures: Set<ServerFeature>, excludedCountryId: CountryId?) -> T,
        onFastestInGroup: (List<Server>) -> T,
        onServer: (Server) -> T,
        fallbackResult: T
    ): T {
        fun Iterable<Server>.filterFeatures() = filter { it.satisfiesFeatures(connectIntent.features) }
        fun Server.satisfiesFeatures() = satisfiesFeatures(connectIntent.features)

        return when (connectIntent) {
            is ConnectIntent.FastestInCountry ->
                if (connectIntent.country.isFastest) {
                    val excludedCountry = if (connectIntent.country.isFastestExcludingMyCountry) getUserCountry() else null
                    onFastest(false, connectIntent.features, excludedCountry)
                } else {
                    getVpnExitCountry(
                        connectIntent.country.countryCode,
                        false
                    )?.let { onFastestInGroup(it.serverList.filterFeatures()) } ?: fallbackResult
                }

            is ConnectIntent.FastestInCity -> {
                getVpnExitCountry(connectIntent.country.countryCode, false)?.let { country ->
                    onFastestInGroup(
                        country.serverList.filter { it.city == connectIntent.cityEn && it.satisfiesFeatures() }
                    )
                } ?: fallbackResult
            }

            is ConnectIntent.FastestInState -> {
                getVpnExitCountry(connectIntent.country.countryCode, false)?.let { country ->
                    onFastestInGroup(
                        country.serverList.filter { it.state == connectIntent.stateEn && it.satisfiesFeatures() }
                    )
                } ?: fallbackResult
            }

            is ConnectIntent.SecureCore ->
                if (connectIntent.exitCountry.isFastest) {
                    val excludedCountry = if (connectIntent.exitCountry.isFastestExcludingMyCountry) getUserCountry() else null
                    onFastest(true, connectIntent.features, excludedCountry)
                } else {
                    val exitCountry = getVpnExitCountry(connectIntent.exitCountry.countryCode, true)
                    if (connectIntent.entryCountry.isFastest) {
                        exitCountry?.let { onFastestInGroup(it.serverList.filterFeatures()) } ?: fallbackResult
                    } else {
                        exitCountry?.serverList?.find {
                            it.entryCountry == connectIntent.entryCountry.countryCode && it.satisfiesFeatures()
                        }?.let { onServer(it) } ?: fallbackResult
                    }
                }

            is ConnectIntent.Gateway ->
                if (connectIntent.serverId != null) {
                    getServerById(connectIntent.serverId)?.let { onServer(it) } ?: fallbackResult
                } else {
                    getGateways()
                        .find { it.name() == connectIntent.gatewayName }
                        ?.let { onFastestInGroup(it.serverList.filterFeatures()) }
                        ?: fallbackResult
                }

            is ConnectIntent.Server -> getServerById(connectIntent.serverId)?.let { onServer(it) } ?: fallbackResult
            is AnyConnectIntent.GuestHole -> getServerById(connectIntent.serverId)?.let { onServer(it) }
                ?: fallbackResult
        }
    }

    fun setStreamingServices(value: StreamingServicesResponse) {
        if (streamingServices != value) {
            streamingServices = value
            Storage.save(this, ServerManager::class.java)
        }
    }

    private fun haveWireGuardSupport() =
        serversData.allServers.any { server -> server.connectingDomains.any { it.publicKeyX25519 != null } }
}
