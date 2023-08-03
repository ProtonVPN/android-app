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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.profiles.ServerWrapper.ProfileType
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.LoadUpdate
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.models.vpn.isSecureCoreCountry
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.ui.home.ServerListUpdater
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

@Singleton
class ServerManager @Inject constructor(
    @Transient val userData: UserData,
    @Transient val currentUser: CurrentUser,
    @Transient @WallClock private val wallClock: () -> Long,
    @Transient val supportsProtocol: SupportsProtocol,
    @Transient val serversStore: ServersStore,
    appFeaturesPrefs: AppFeaturesPrefs,
) : Serializable {

    private var serverListAppVersionCode = 0

    // Exist only for migration
    @SerializedName("vpnCountries") private val migrateVpnCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreEntryCountries") private val migrateSecureCoreEntryCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreExitCountries") private val migrateSecureCoreExitCountries: MutableList<VpnCountry>? = null

    @Transient private var filteredVpnCountries = listOf<VpnCountry>()
    @Transient private var filteredSecureCoreEntryCountries = listOf<VpnCountry>()
    @Transient private var filteredSecureCoreExitCountries = listOf<VpnCountry>()
    @Transient private var filteredGatewayGroups = listOf<GatewayGroup>()
    @Transient private var guestHoleServers: List<Server>? = null

    private var streamingServices: StreamingServicesResponse? = null
    val streamingServicesModel: StreamingServicesModel?
        get() = streamingServices?.let { StreamingServicesModel(it) }

    var lastUpdateTimestamp: Long = 0L
        private set

    var translationsLang: String? = null
        private set

    @Transient
    val serverComparator = compareBy<Server> { !it.isFreeServer }
        .thenBy { it.serverNumber >= 100 }
        .thenBy { it.serverNumber }

    @Transient
    private val savedProfiles: SavedProfilesV3 =
        Storage.load(SavedProfilesV3::class.java, SavedProfilesV3.defaultProfiles())
            .migrateProfiles(appFeaturesPrefs)

    // Expose a version number of the server list so that it can be used in flow operators like
    // combine to react to updates.
    @Transient val serverListVersion = MutableStateFlow(0)
    @Transient val profiles = MutableStateFlow(savedProfiles.profileList.toList())
    // TODO: remove the LiveDatas once there is no more Java code using them.
    @Transient val serverListVersionLiveData = serverListVersion.asLiveData()
    @Transient val profilesLiveData = profiles.asLiveData()

    // isDownloadedAtLeastOnce should be true if there was an updateAt value. Remove after most users update.
    @SerializedName("updatedAt") private var migrateUpdatedAt: DateTime? = null
    val isDownloadedAtLeastOnce: Boolean
        get() = (lastUpdateTimestamp > 0L || migrateUpdatedAt != null) && serversStore.allServers.isNotEmpty()

    val isOutdated: Boolean
        get() = lastUpdateTimestamp == 0L || serversStore.allServers.isEmpty() ||
            wallClock() - lastUpdateTimestamp >= ServerListUpdater.LIST_CALL_DELAY ||
            !haveWireGuardSupport() || serverListAppVersionCode < BuildConfig.VERSION_CODE ||
            translationsLang != Locale.getDefault().language

    private val allServers get() = serversStore.allServers

    /** Get the number of all servers. Not very efficient. */
    val allServerCount get() = allServers.count()

    val defaultFallbackConnection = getSavedProfiles()[0]

    val defaultConnection: Profile get() = findDefaultProfile() ?: getSavedProfiles().first()

    val defaultAvailableConnection: Profile get() =
        (listOf(findDefaultProfile()) + getSavedProfiles())
            .filterNotNull()
            .first {
                (it.isSecureCore == true).implies(currentUser.vpnUserCached()?.isUserPlusOrAbove == true)
            }

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
        userData.migrateDefaultProfile(this)

        userData.protocolLiveData.observeForever {
            filterServers()
            onServersUpdate()
        }
    }

    private fun getExitCountries(secureCore: Boolean) = if (secureCore)
        filteredSecureCoreExitCountries else filteredVpnCountries

    @VisibleForTesting fun filterCountriesForProtocol(countries: List<VpnCountry>) =
        filterForProtocol(countries, userData.protocol) { country, newServers -> VpnCountry(country.flag, newServers) }

    fun filterGatewaysForProtocol(gateways: List<GatewayGroup>) =
        filterForProtocol(gateways, userData.protocol) { gateway, newServers -> GatewayGroup(gateway.name(), newServers) }

    private fun <T : ServerGroup> filterForProtocol(
        groups: List<T>,
        protocol: ProtocolSelection,
        buildResult: (T, List<Server>) -> T
    ): List<T> =
        groups.mapNotNull { group ->
            val servers = group.serverList
                .filter {
                    supportsProtocol(it, protocol)
                }.map { server ->
                    val filteredDomains = server.connectingDomains.filter { supportsProtocol(it, protocol) }
                    server.copy(
                        isOnline = server.online && filteredDomains.any { it.isOnline },
                        connectingDomains = filteredDomains)
                }
            if (servers.isNotEmpty())
                buildResult(group, servers)
            else
                null
        }

    private fun onServersUpdate() {
        ++serverListVersion.value
    }

    private fun filterServers() {
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
        for (server in allServers) {
            // TODO: secure core countries shouldn't be hardcoded but calculated from server list
            DebugUtils.debugAssert { !server.isSecureCoreServer || isSecureCoreCountry(server.entryCountry) }
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

        filteredVpnCountries = filterCountriesForProtocol(vpnCountries.toVpnCountries())
        filteredSecureCoreEntryCountries = filterCountriesForProtocol(secureCoreEntryCountries.toVpnCountries())
        filteredSecureCoreExitCountries = filterCountriesForProtocol(secureCoreExitCountries.toVpnCountries())
        filteredGatewayGroups =
            filterGatewaysForProtocol(gateways.map { (name, servers) -> GatewayGroup(name, servers) } )
    }

    override fun toString(): String {
        val lastUpdateTimestampLog = lastUpdateTimestamp.takeIf { it != 0L }?.let { ProtonLogger.formatTime(it) }
        return "filtered vpnCountries: ${filteredVpnCountries.size} gateways: ${filteredGatewayGroups.size}" +
            " entry: ${filteredSecureCoreEntryCountries.size}" +
            " exit: ${filteredSecureCoreExitCountries.size} profiles: ${savedProfiles.profileList?.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        lastUpdateTimestamp = 0L
        Storage.delete(ServerManager::class.java)
        serversStore.clear()
    }

    fun setGuestHoleServers(serverList: List<Server>) {
        setServers(serverList, null)
        lastUpdateTimestamp = 0L
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

        filterServers()
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

    fun getVpnCountries(): List<VpnCountry> = filteredVpnCountries.sortedByLocaleAware { it.countryName }

    fun getGateways(): List<GatewayGroup> = filteredGatewayGroups

    fun getSecureCoreEntryCountries(): List<VpnCountry> = filteredSecureCoreEntryCountries

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
        val allCountries = getExitCountries(userData.secureCoreEnabled)
        val accessibleCountries = allCountries.filter { it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
        return accessibleCountries.ifEmpty { allCountries }.randomNullable()?.let { getRandomServer(it, vpnUser) }
    }

    private fun getRandomServer(country: VpnCountry, vpnUser: VpnUser?): Server? {
        val online = country.serverList.filter(Server::online)
        val accessible = online.filter { vpnUser.hasAccessToServer(it) }
        return accessible.ifEmpty { online }.randomNullable()
    }

    fun getSavedProfiles(): List<Profile> =
        savedProfiles.profileList

    fun deleteSavedProfiles() {
        for (profile in getSavedProfiles().toList()) {
            if (!profile.isPreBakedProfile) {
                deleteProfile(profile)
            }
        }
    }

    fun addToProfileList(serverName: String?, color: ProfileColor, server: Server) {
        val newProfile =
            Profile(serverName!!, null, ServerWrapper.makeWithServer(server), color.id, server.isSecureCoreServer)
        addToProfileList(newProfile)
    }

    fun addToProfileList(profileToSave: Profile?) {
        if (!savedProfiles.profileList.contains(profileToSave)) {
            savedProfiles.profileList.add(profileToSave)
            Storage.save(savedProfiles)
            profiles.value = getSavedProfiles().toList()
        }
    }

    fun editProfile(oldProfile: Profile, profileToSave: Profile) {
        savedProfiles.profileList[savedProfiles.profileList.indexOf(oldProfile)] = profileToSave
        Storage.save(savedProfiles)
        profiles.value = getSavedProfiles().toList()
    }

    fun deleteProfile(profileToSave: Profile?) {
        savedProfiles.profileList.remove(profileToSave)
        if (userData.defaultProfileId == profileToSave?.id) userData.defaultProfileId = null
        Storage.save(savedProfiles)
        profiles.value = getSavedProfiles().toList()
    }

    fun getSecureCoreExitCountries(): List<VpnCountry> =
        filteredSecureCoreExitCountries.sortedByLocaleAware { it.countryName }

    fun getServerForProfile(profile: Profile, vpnUser: VpnUser?): Server? {
        val wrapper = profile.wrapper
        val needsSecureCore = profile.isSecureCore ?: userData.secureCoreEnabled
        return when (wrapper.type) {
            ProfileType.FASTEST ->
                getBestScoreServer(userData.secureCoreEnabled, vpnUser)
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
            secureCore -> filteredSecureCoreExitCountries
            gatewayName != null ->
                filteredGatewayGroups.find { it.name() == gatewayName }?.let { listOf(it) } ?: emptyList()
            else -> filteredVpnCountries
        }
        return groups.asSequence().flatMap { group ->
            group.serverList.filter {
                it.online && vpnUser.hasAccessToServer(it) && supportsProtocol(it, protocol)
            }.asSequence()
        }.sortedBy { it.score }.toList()
    }

    fun findDefaultProfile(): Profile? =
        userData.defaultProfileId?.let { defaultId -> getSavedProfiles().find { it.id == defaultId } }

    private fun haveWireGuardSupport() =
        serversStore.allServers.any { server -> server.connectingDomains.any { it.publicKeyX25519 != null } }

    @Suppress("ClassOrdering")
    @get:TestOnly val firstNotAccessibleVpnCountry get() =
        getVpnCountries().firstOrNull { !it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
            ?: throw UnsupportedOperationException("Should only use this method on free tiers")
}
