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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.profiles.ServerWrapper.ProfileType
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.LoadUpdate
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import kotlinx.coroutines.flow.MutableStateFlow
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
    appFeaturesPrefs: AppFeaturesPrefs,
) : Serializable {

    private var serverListAppVersionCode = 0
    private val vpnCountries = mutableListOf<VpnCountry>()
    private val secureCoreEntryCountries = mutableListOf<VpnCountry>()
    private val secureCoreExitCountries = mutableListOf<VpnCountry>()

    @Transient private var filteredVpnCountries = listOf<VpnCountry>()
    @Transient private var filteredSecureCoreEntryCountries = listOf<VpnCountry>()
    @Transient private var filteredSecureCoreExitCountries = listOf<VpnCountry>()

    private var streamingServices: StreamingServicesResponse? = null
    val streamingServicesModel: StreamingServicesModel?
        get() = streamingServices?.let { StreamingServicesModel(it) }

    var lastUpdateTimestamp: Long = 0L
        private set

    var translationsLang: String? = null
        private set

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
        get() = (lastUpdateTimestamp > 0L || migrateUpdatedAt != null) && vpnCountries.isNotEmpty()

    val isOutdated: Boolean
        get() = lastUpdateTimestamp == 0L || vpnCountries.isEmpty() ||
            wallClock() - lastUpdateTimestamp >= ServerListUpdater.LIST_CALL_DELAY ||
            !haveWireGuardSupport() || serverListAppVersionCode < BuildConfig.VERSION_CODE ||
            translationsLang != Locale.getDefault().language

    private val allServers get() =
        sequenceOf(vpnCountries, secureCoreEntryCountries, secureCoreExitCountries)
            .flatten().flatMap { it.serverList.asSequence() }

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
            vpnCountries.addAll(oldManager.vpnCountries)
            secureCoreExitCountries.addAll(oldManager.secureCoreExitCountries)
            secureCoreEntryCountries.addAll(oldManager.secureCoreEntryCountries)
            streamingServices = oldManager.streamingServices
            migrateUpdatedAt = oldManager.migrateUpdatedAt
            lastUpdateTimestamp = oldManager.lastUpdateTimestamp
            serverListAppVersionCode = oldManager.serverListAppVersionCode
            translationsLang = oldManager.translationsLang
        }
        userData.migrateDefaultProfile(this)

        userData.protocolLiveData.observeForever {
            onServersUpdate()
        }
    }

    private fun getExitCountries(secureCore: Boolean) = if (secureCore)
        filteredSecureCoreExitCountries else filteredVpnCountries

    @VisibleForTesting fun filterForProtocol(countries: List<VpnCountry>) =
        userData.protocol.let { protocol ->
            countries.mapNotNull { country ->
                val servers = country.serverList
                    .filter {
                        it.supportsProtocol(protocol)
                    }.map { server ->
                        val filteredDomains = server.connectingDomains.filter { it.supportsProtocol(protocol) }
                        server.copy(
                            isOnline = server.online && filteredDomains.any { it.isOnline },
                            connectingDomains = filteredDomains)
                    }
                if (servers.isNotEmpty())
                    VpnCountry(country.flag, servers)
                else
                    null
            }
        }

    private fun onServersUpdate() {
        filterServers()
        ++serverListVersion.value
    }

    private fun filterServers() {
        filteredVpnCountries = filterForProtocol(vpnCountries)
        filteredSecureCoreEntryCountries = filterForProtocol(secureCoreEntryCountries)
        filteredSecureCoreExitCountries = filterForProtocol(secureCoreExitCountries)
    }

    override fun toString(): String {
        val lastUpdateTimestampLog = lastUpdateTimestamp.takeIf { it != 0L }?.let { ProtonLogger.formatTime(it) }
        return "vpnCountries: ${vpnCountries.size} entry: ${secureCoreEntryCountries.size}" +
            " exit: ${secureCoreExitCountries.size} saved: ${savedProfiles.profileList?.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        lastUpdateTimestamp = 0L
        Storage.delete(ServerManager::class.java)
    }

    fun setGuestHoleServers(serverList: List<Server>) {
        setServers(serverList, null)
        lastUpdateTimestamp = 0L
    }

    fun getServersForGuestHole(serverCount: Int) =
        getExitCountries(false).flatMap { country ->
            country.serverList.filter { it.online }
        }.takeRandomStable(serverCount)

    fun setServers(serverList: List<Server>, language: String?) {
        vpnCountries.clear()
        secureCoreEntryCountries.clear()
        secureCoreExitCountries.clear()
        val countries = serverList.asSequence().map(Server::flag).toSet()
        for (country in countries) {
            val servers = serverList.filter { server ->
                !server.isSecureCoreServer && server.flag == country
            }
            vpnCountries.add(VpnCountry(country, servers))
        }
        for (country in countries) {
            if (country == "IS" || country == "SE" || country == "CH") {
                val servers = serverList.filter { server ->
                    server.isSecureCoreServer && server.entryCountry.equals(country, ignoreCase = true)
                }
                secureCoreEntryCountries.add(VpnCountry(country, servers))
            }
        }
        for (country in countries) {
            val servers = serverList.filter { server ->
                server.isSecureCoreServer && server.exitCountry.equals(country, ignoreCase = true)
            }
            if (servers.isNotEmpty())
                secureCoreExitCountries.add(VpnCountry(country, servers))
        }
        lastUpdateTimestamp = wallClock()
        serverListAppVersionCode = BuildConfig.VERSION_CODE
        translationsLang = language
        Storage.save(this)
        onServersUpdate()
    }

    fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        allServers.asSequence().flatMap { it.connectingDomains.asSequence() }
            .find { it.id == connectingDomain.id }?.let {
                it.isOnline = connectingDomain.isOnline
            }

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
        Storage.save(this)
        onServersUpdate()
    }

    fun getServerById(id: String) = allServers.firstOrNull { it.serverId == id }

    fun getVpnCountries(): List<VpnCountry> = filteredVpnCountries.sortedByLocaleAware { it.countryName }

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
            .filter { Server.Keyword.TOR !in it.keywords && it.online }
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
        streamingServices = value
        Storage.save(this)
    }

    // Sorted by score (best at front)
    fun getOnlineAccessibleServers(secureCore: Boolean, vpnUser: VpnUser?): List<Server> =
        getExitCountries(secureCore).asSequence().flatMap { country ->
            country.serverList.filter { it.online && vpnUser.hasAccessToServer(it) }.asSequence()
        }.sortedBy { it.score }.toList()

    fun findDefaultProfile(): Profile? =
        userData.defaultProfileId?.let { defaultId -> getSavedProfiles().find { it.id == defaultId } }

    private fun haveWireGuardSupport() =
        vpnCountries.any { country ->
            country.serverList.any { server ->
                server.connectingDomains.any { it.publicKeyX25519 != null }
            }
        }

    @Suppress("ClassOrdering")
    @get:TestOnly val firstNotAccessibleVpnCountry get() =
        getVpnCountries().firstOrNull { !it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
            ?: throw UnsupportedOperationException("Should only use this method on free tiers")
}
