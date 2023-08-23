/*
 * Copyright (c) 2017 Proton Technologies AG
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
import androidx.lifecycle.asLiveData
import com.google.gson.annotations.SerializedName
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.RestrictionsConfig
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
import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.models.vpn.isSecureCoreCountry
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.vpn.ProtocolSelection
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.annotations.TestOnly
import org.joda.time.DateTime
import java.io.Serializable
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

val serverComparator = compareBy<Server> { !it.isFreeServer }
    .thenBy { it.serverNumber >= 100 }
    .thenBy { it.serverNumber }

@Singleton
class ServerManager @Inject constructor(
    @Transient private val currentUserSettingsCached: EffectiveCurrentUserSettingsCached,
    @Transient val currentUser: CurrentUser,
    @Transient @WallClock private val wallClock: () -> Long,
    @Transient val supportsProtocol: SupportsProtocol,
    @Transient val serversStore: ServersStore,
    @Transient private val profileManager: ProfileManager,
    @Transient val restrictions: RestrictionsConfig
) : Serializable {

    private var serverListAppVersionCode = 0

    // Exist only for migration
    @SerializedName("vpnCountries") private val migrateVpnCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreEntryCountries") private val migrateSecureCoreEntryCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreExitCountries") private val migrateSecureCoreExitCountries: MutableList<VpnCountry>? = null

    @Transient private var filtered = FilteredServers(serversStore, currentUserSettingsCached, supportsProtocol)
    @Transient private var guestHoleServers: List<Server>? = null

    private val secureCoreCached get() = currentUserSettingsCached.value.secureCore

    private var streamingServices: StreamingServicesResponse? = null
    val streamingServicesModel: StreamingServicesModel?
        get() = streamingServices?.let { StreamingServicesModel(it) }

    var lastUpdateTimestamp: Long = 0L
        private set

    var translationsLang: String? = null
        private set


    // Expose a version number of the server list so that it can be used in flow operators like
    // combine to react to updates.
    @Transient val serverListVersion = MutableStateFlow(0)
    // TODO: remove the LiveDatas once there is no more Java code using them.
    @Transient val serverListVersionLiveData = serverListVersion.asLiveData()

    // isDownloadedAtLeastOnce should be true if there was an updateAt value. Remove after most users update.
    @SerializedName("updatedAt") private var migrateUpdatedAt: DateTime? = null
    val isDownloadedAtLeastOnce: Boolean
        get() = (lastUpdateTimestamp > 0L || migrateUpdatedAt != null) && serversStore.allServers.isNotEmpty()

    val needsUpdate: Boolean
        get() = lastUpdateTimestamp == 0L || serversStore.allServers.isEmpty() ||
            !haveWireGuardSupport() || serverListAppVersionCode < BuildConfig.VERSION_CODE ||
            translationsLang != Locale.getDefault().language

    val allServers get() = serversStore.allServers

    /** Get the number of all servers. Not very efficient. */
    val allServerCount get() = allServers.count()

    val fastestProfile get() = profileManager.fastestProfile
    val randomProfile get() = profileManager.randomServerProfile
    val defaultFallbackConnection get() = fastestProfile

    val defaultConnection: Profile get() = with(profileManager) {
        if (restrictions.restrictQuickConnectSync()) {
            fastestProfile
        } else {
            findDefaultProfile() ?: fastestProfile
        }
    }

    val defaultAvailableConnection: Profile get() =
        (listOf(defaultConnection) + profileManager.getSavedProfiles())
            .first {
                (it.isSecureCore == true).implies(currentUser.vpnUserCached()?.isUserPlusOrAbove == true)
            }

    val freeCountries get() = getVpnCountries()
        .filter { country -> country.serverList.any { server -> server.isFreeServer } }

    init {
        val oldManager =
            Storage.load(ServerManager::class.java)
        if (oldManager != null) {
            if (oldManager.migrateVpnCountries?.isEmpty() == false && serversStore.allServers.isEmpty()) {
                // Migrate from old server store
                try {
                    serversStore.migrate(
                        vpnCountries = oldManager.migrateVpnCountries,
                        secureCoreEntryCountries = oldManager.migrateSecureCoreEntryCountries
                            ?: emptyList(),
                        secureCoreExitCountries = oldManager.migrateSecureCoreExitCountries
                            ?: emptyList(),
                    )
                } catch (e: Exception) {
                    // With some old/corrupted Storage we can get e.g. NullPointerException on
                    // migration, let's start with empty list in that case
                    serversStore.clear()
                    val event = SentryEvent(e).apply {
                        message = Message().apply { message = "Unable to migrate server list" }
                        level = SentryLevel.ERROR
                    }
                    Sentry.captureEvent(event)
                }
            }
            streamingServices = oldManager.streamingServices
            migrateUpdatedAt = oldManager.migrateUpdatedAt
            lastUpdateTimestamp = oldManager.lastUpdateTimestamp
            serverListAppVersionCode = oldManager.serverListAppVersionCode
            translationsLang = oldManager.translationsLang

            if (oldManager.migrateVpnCountries?.isEmpty() == false) {
                Storage.save(this)
            }
        }
    }

    private fun getExitCountries(secureCore: Boolean) = if (secureCore)
        filtered.secureCoreExitCountries else filtered.vpnCountries

    private fun onServersUpdate() {
        ++serverListVersion.value
    }

    override fun toString(): String {
        val lastUpdateTimestampLog = lastUpdateTimestamp.takeIf { it != 0L }?.let { ProtonLogger.formatTime(it) }
        return "filtered vpnCountries: ${filtered.vpnCountries.size} gateways: ${filtered.gateways.size}" +
            " entry: ${filtered.secureCoreEntryCountries.size}" +
            " exit: ${filtered.secureCoreExitCountries.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        lastUpdateTimestamp = 0L
        Storage.delete(ServerManager::class.java)
        filtered.invalidate()
        // The server list itself is not deleted.
    }

    fun setGuestHoleServers(serverList: List<Server>) {
        setServers(serverList, null)
        lastUpdateTimestamp = 0L
    }

    @VisibleForTesting
    fun setBuiltInGuestHoleServersForTesting(serverList: List<Server>) {
        guestHoleServers = serverList
    }

    fun getDownloadedServersForGuestHole(serverCount: Int, protocol: ProtocolSelection) =
        (listOfNotNull(getBestScoreServer(false, null)) +
            getExitCountries(false).flatMap { country ->
                country.serverList.filter { it.online && supportsProtocol(it, protocol) }
            }.takeRandomStable(serverCount).shuffled()
        ).distinct().take(serverCount)

    fun setServers(serverList: List<Server>, language: String?) {
        serversStore.allServers = serverList
        serversStore.save()

        lastUpdateTimestamp = wallClock()
        serverListAppVersionCode = BuildConfig.VERSION_CODE
        translationsLang = language
        Storage.save(this)

        filtered.invalidate()
        onServersUpdate()
    }

    fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        allServers.flatMap { it.connectingDomains.asSequence() }
            .find { it.id == connectingDomain.id }?.let {
                it.isOnline = connectingDomain.isOnline
            }

        serversStore.save()
        Storage.save(this)
        onServersUpdate()
    }

    fun updateLoads(loadsList: List<LoadUpdate>) {
        val loadsMap = loadsList.asSequence().map { it.id to it }.toMap()
        allServers.forEach { server ->
            loadsMap[server.serverId]?.let {
                server.load = it.load
                server.score = it.score

                // If server becomes online we don't know which connectingDomains became available based on /loads
                // response. If there's more than one connectingDomain it'll have to wait for /logicals response
                if (server.online != it.isOnline && (!it.isOnline || server.connectingDomains.size == 1))
                    server.setOnline(it.isOnline)
            }
        }
        serversStore.save()
        Storage.save(this)
        onServersUpdate()
    }

    fun getGuestHoleServers(): List<Server> =
        guestHoleServers ?: run {
            FileUtils.getObjectFromAssets(
                ListSerializer(Server.serializer()), GuestHole.GUEST_HOLE_SERVERS_ASSET).apply {
                    guestHoleServers = this
            }
        }

    fun getServerById(id: String) =
        allServers.firstOrNull { it.serverId == id } ?: getGuestHoleServers().firstOrNull { it.serverId == id }

    fun getVpnCountries(): List<VpnCountry> = filtered.vpnCountries.sortedByLocaleAware { it.countryName }

    fun getGateways(): List<GatewayGroup> = filtered.gateways

    fun getSecureCoreEntryCountries(): List<VpnCountry> = filtered.secureCoreEntryCountries

    fun getVpnExitCountry(countryCode: String, secureCoreCountry: Boolean): VpnCountry? =
        getExitCountries(secureCoreCountry).firstOrNull { it.flag == countryCode }

    @Deprecated(
        "This method uses a cached VpnUser that could be stale.",
        ReplaceWith("getBestScoreServer(country, vpnUser)")
    )
    fun getBestScoreServer(country: VpnCountry): Server? =
        getBestScoreServer(country.serverList)

    fun getBestScoreServer(secureCore: Boolean, vpnUser: VpnUser?): Server? {
        val countries = getExitCountries(secureCore)
        val map = countries.asSequence()
            .map(VpnCountry::serverList)
            .mapNotNull { getBestScoreServer(it, vpnUser) }
            .groupBy { vpnUser.hasAccessToServer(it) }
            .mapValues { it.value.minByOrNull(Server::score) }
        return map[true] ?: map[false]
    }

    @Deprecated(
        "This method uses a cached VpnUser that could be stale.",
        ReplaceWith("getBestScoreServer(secureCore, vpnUser)")
    )
    fun getBestScoreServer(secureCore: Boolean): Server? =
        getBestScoreServer(secureCore, currentUser.vpnUserCached())

    fun getBestScoreServer(serverList: List<Server>, vpnUser: VpnUser?): Server? {
        val map = serverList.asSequence()
            .filter { !it.isTor && it.online }
            .groupBy { vpnUser.hasAccessToServer(it) }
            .mapValues { it.value.minByOrNull(Server::score) }
        return map[true] ?: map[false]
    }

    @Deprecated(
        "This method uses a cached VpnUser that could be stale.",
        ReplaceWith("getBestScoreServer(serverList, vpnUser)")
    )
    fun getBestScoreServer(serverList: List<Server>): Server? =
        getBestScoreServer(serverList, currentUser.vpnUserCached())

    private fun getRandomServer(vpnUser: VpnUser?): Server? {
        val allCountries = getExitCountries(secureCoreCached)
        val accessibleCountries = allCountries.filter { it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
        return accessibleCountries.ifEmpty { allCountries }.randomNullable()?.let { getRandomServer(it, vpnUser) }
    }

    private fun getRandomServer(country: VpnCountry, vpnUser: VpnUser?): Server? {
        val online = country.serverList.filter(Server::online)
        val accessible = online.filter { vpnUser.hasAccessToServer(it) }
        return accessible.ifEmpty { online }.randomNullable()
    }

    fun getSecureCoreExitCountries(): List<VpnCountry> =
        filtered.secureCoreExitCountries.sortedByLocaleAware { it.countryName }

    fun getServerForProfile(profile: Profile, vpnUser: VpnUser?): Server? {
        val wrapper = profile.wrapper
        val needsSecureCore = profile.isSecureCore ?: secureCoreCached
        return when (wrapper.type) {
            ProfileType.FASTEST ->
                getBestScoreServer(secureCoreCached, vpnUser)
            ProfileType.RANDOM ->
                getRandomServer(vpnUser)
            ProfileType.RANDOM_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, needsSecureCore)?.let {
                    getRandomServer(it, vpnUser)
                }
            ProfileType.FASTEST_IN_COUNTRY ->
                getVpnExitCountry(wrapper.country, needsSecureCore)?.let {
                    getBestScoreServer(it.serverList, vpnUser)
                }
            ProfileType.DIRECT ->
                getServerById(wrapper.serverId!!)
        }
    }

    @Deprecated(
        "This method uses a cached VpnUser that could be stale.",
        ReplaceWith("vpnUser.hasAccessToServer(server)")
    )
    fun hasAccessToServer(server: Server): Boolean =
        currentUser.vpnUserCached().hasAccessToServer(server)

    fun setStreamingServices(value: StreamingServicesResponse) {
        if (streamingServices != value) {
            streamingServices = value
            Storage.save(this)
        }
    }

    // Sorted by score (best at front)
    fun getOnlineAccessibleServers(
        secureCore: Boolean,
        gatewayName: String?,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): List<Server> {
        val groups = when {
            secureCore -> filtered.secureCoreExitCountries
            gatewayName != null ->
                filtered.gateways.find { it.name() == gatewayName }?.let { listOf(it) } ?: emptyList()

            else -> filtered.vpnCountries
        }
        return groups.asSequence().flatMap { group ->
            group.serverList.filter {
                it.online && vpnUser.hasAccessToServer(it) && supportsProtocol(it, protocol)
            }.asSequence()
        }.sortedBy { it.score }.toList()
    }

    private fun haveWireGuardSupport() =
        serversStore.allServers.any { server -> server.connectingDomains.any { it.publicKeyX25519 != null } }

    @Suppress("ClassOrdering")
    @get:TestOnly val firstNotAccessibleVpnCountry get() =
        getVpnCountries().firstOrNull { !it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
            ?: throw UnsupportedOperationException("Should only use this method on free tiers")
}

class FilteredServers(
    private val serverStore: ServersStore,
    private val currentUserSettingsCached: EffectiveCurrentUserSettingsCached,
    private val supportsProtocol: SupportsProtocol
) {
    // Servers are filtered lazily when needed to delay access to user settings. This gives time to read settings in a
    // non-blocking way.
    private val currentProtocol get() = currentUserSettingsCached.value.protocol
    private var filteredForProtocol: ProtocolSelection? = null

    private var filteredVpnCountries: List<VpnCountry> = emptyList()
    private var filteredSecureCoreExitCountries: List<VpnCountry> = emptyList()
    private var filteredSecureCoreEntryCountries: List<VpnCountry> = emptyList()
    private var filteredGateways: List<GatewayGroup> = emptyList()

    val vpnCountries: List<VpnCountry> get() = getCached { filteredVpnCountries }
    val secureCoreExitCountries get() = getCached { filteredSecureCoreExitCountries }
    val secureCoreEntryCountries get() = getCached { filteredSecureCoreEntryCountries }
    val gateways get() = getCached { filteredGateways }

    fun invalidate() {
        filteredForProtocol = null
    }

    private fun groupAndFilter() {
        fun MutableMap<String, MutableList<Server>>.addServer(key: String, server: Server, uppercase: Boolean = true) {
            val mapKey = if (uppercase) key.uppercase() else key
            getOrPut(mapKey) { mutableListOf() } += server
        }
        fun MutableMap<String, MutableList<Server>>.toVpnCountries() =
            map { (country, servers) -> VpnCountry(country, servers.sortedWith(serverComparator)) }

        val vpnCountries = mutableMapOf<String, MutableList<Server>>()
        val gateways = mutableMapOf<String, MutableList<Server>>()
        val secureCoreEntryCountries = mutableMapOf<String, MutableList<Server>>()
        val secureCoreExitCountries = mutableMapOf<String, MutableList<Server>>()
        val protocol = currentProtocol // Use a local copy in case the setting can change on some other thread.
        for (unfilteredServer in serverStore.allServers) {
            // TODO: secure core countries shouldn't be hardcoded but calculated from server list
            DebugUtils.debugAssert { !unfilteredServer.isSecureCoreServer || isSecureCoreCountry(unfilteredServer.entryCountry) }
            val filteredDomains = unfilteredServer.connectingDomains.filter { supportsProtocol(it, currentProtocol) }
            if (filteredDomains.isNotEmpty()) {
                val server = if (unfilteredServer.connectingDomains.size != filteredDomains.size) {
                    unfilteredServer.copy(connectingDomains = filteredDomains)
                } else {
                    unfilteredServer
                }
                when {
                    server.isSecureCoreServer && isSecureCoreCountry(server.entryCountry) -> {
                        secureCoreEntryCountries.addServer(server.entryCountry, server)
                        secureCoreExitCountries.addServer(server.exitCountry, server)
                    }

                    server.isGatewayServer && server.gatewayName != null ->
                        gateways.addServer(server.gatewayName!!, server, uppercase = false)

                    else ->
                        vpnCountries.addServer(server.flag, server)

                }
            }
        }
        filteredForProtocol = protocol
        filteredVpnCountries = vpnCountries.toVpnCountries()
        filteredSecureCoreEntryCountries = secureCoreEntryCountries.toVpnCountries()
        filteredSecureCoreExitCountries = secureCoreExitCountries.toVpnCountries()
        filteredGateways = gateways.map { (name, servers) -> GatewayGroup(name, servers) }
    }

    private fun <T> getCached(getter: () -> T): T {
        if (currentProtocol != filteredForProtocol) groupAndFilter()
        return getter()
    }
}
