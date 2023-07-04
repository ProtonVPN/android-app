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
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.ProtocolSelection
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@Deprecated("User ServerManager2 in new code")
@Singleton
class ServerManager @Inject constructor(
    @Transient private val mainScope: CoroutineScope,
    @Transient private val currentUserSettingsCached: EffectiveCurrentUserSettingsCached,
    @Transient val currentUser: CurrentUser,
    @Transient @WallClock private val wallClock: () -> Long,
    @Transient val supportsProtocol: SupportsProtocol,
    @Transient val serversStore: ServersStore,
    @Transient private val profileManager: ProfileManager,
) : Serializable {

    private var serverListAppVersionCode = 0

    // Exist only for migration
    @SerializedName("vpnCountries") private val migrateVpnCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreEntryCountries") private val migrateSecureCoreEntryCountries: MutableList<VpnCountry>? = null
    @SerializedName("secureCoreExitCountries") private val migrateSecureCoreExitCountries: MutableList<VpnCountry>? = null

    @Transient private var grouped = GroupedServers(serversStore)
    @Transient private var guestHoleServers: List<Server>? = null
    @Transient private val isLoaded = MutableStateFlow(false)

    private val secureCoreCached get() = currentUserSettingsCached.value.secureCore
    private val protocolCached get() = currentUserSettingsCached.value.protocol

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

    @Deprecated("Use suspending isDownloadedAtLeastOnce instead. Or even better ServerManager2")
    // This method will not wait for the server list to be loaded. Use with caution.
    val haveLoadedServersAlready get() =
        (lastUpdateTimestamp > 0L || migrateUpdatedAt != null) && serversStore.allServers.isNotEmpty()

    suspend fun isDownloadedAtLeastOnce(): Boolean {
        ensureLoaded()
        return haveLoadedServersAlready
    }

    suspend fun needsUpdate() : Boolean {
        ensureLoaded()
        return lastUpdateTimestamp == 0L || serversStore.allServers.isEmpty() ||
            !haveWireGuardSupport() || serverListAppVersionCode < BuildConfig.VERSION_CODE ||
            translationsLang != Locale.getDefault().language
    }

    val allServers get() = serversStore.allServers

    /** Get the number of all servers. Not very efficient. */
    val allServerCount get() = allServers.count()

    val fastestProfile get() = profileManager.fastestProfile
    val randomProfile get() = profileManager.randomServerProfile

    val defaultConnection: Profile get() = profileManager.getDefaultOrFastest()

    val freeCountries get() = getVpnCountries()
        .filter { country -> country.serverList.any { server -> server.isFreeServer } }

    init {
        val oldManager =
            Storage.load(ServerManager::class.java)
        if (oldManager != null) {
            streamingServices = oldManager.streamingServices
            migrateUpdatedAt = oldManager.migrateUpdatedAt
            lastUpdateTimestamp = oldManager.lastUpdateTimestamp
            serverListAppVersionCode = oldManager.serverListAppVersionCode
            translationsLang = oldManager.translationsLang
        }

        mainScope.launch {
            serversStore.load()
            if (oldManager?.migrateVpnCountries?.isEmpty() == false && serversStore.allServers.isEmpty()) {
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

            grouped.update()

            // Notify of loaded state and update after everything has been updated.
            isLoaded.value = true
            onServersUpdate()

            if (oldManager?.migrateVpnCountries?.isEmpty() == false) {
                Storage.save(this@ServerManager, ServerManager::class.java)
            }
        }
    }

    suspend fun ensureLoaded() {
        isLoaded.first { isLoaded -> isLoaded }
    }

    fun getExitCountries(secureCore: Boolean) = if (secureCore)
        grouped.secureCoreExitCountries else grouped.vpnCountries

    private fun onServersUpdate() {
        ++serverListVersion.value
    }

    override fun toString(): String {
        val lastUpdateTimestampLog = lastUpdateTimestamp.takeIf { it != 0L }?.let { ProtonLogger.formatTime(it) }
        return "vpnCountries: ${grouped.vpnCountries.size} gateways: ${grouped.gateways.size}" +
            " entry: ${grouped.secureCoreEntryCountries.size}" +
            " exit: ${grouped.secureCoreExitCountries.size} " +
            "ServerManager Updated: $lastUpdateTimestampLog"
    }

    fun clearCache() {
        lastUpdateTimestamp = 0L
        Storage.delete(ServerManager::class.java)
        grouped.update()
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
        (listOfNotNull(getBestScoreServer(false, null)) +
            getExitCountries(false).flatMap { country ->
                country.serverList.filter { it.online && supportsProtocol(it, protocol) }
            }.takeRandomStable(serverCount).shuffled()
        ).distinct().take(serverCount)

    suspend fun setServers(serverList: List<Server>, language: String?) {
        ensureLoaded()
        serversStore.allServers = serverList
        serversStore.save()

        lastUpdateTimestamp = wallClock()
        serverListAppVersionCode = BuildConfig.VERSION_CODE
        translationsLang = language
        Storage.save(this, ServerManager::class.java)

        grouped.update()
        onServersUpdate()
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        ensureLoaded()
        allServers.flatMap { it.connectingDomains.asSequence() }
            .find { it.id == connectingDomain.id }?.let {
                it.isOnline = connectingDomain.isOnline
            }

        serversStore.save()
        Storage.save(this, ServerManager::class.java)
        onServersUpdate()
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        ensureLoaded()
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
        Storage.save(this, ServerManager::class.java)
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

    fun getVpnCountries(): List<VpnCountry> = grouped.vpnCountries.sortedByLocaleAware { it.countryName }

    fun getGateways(): List<GatewayGroup> = grouped.gateways

    fun getSecureCoreEntryCountries(): List<VpnCountry> = grouped.secureCoreEntryCountries

    @Deprecated("Use the suspending getVpnExitCountry from ServerManager2")
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
            .filter { !it.isTor && it.online && supportsProtocol(it, protocolCached) }
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
        grouped.secureCoreExitCountries.sortedByLocaleAware { it.countryName }

    fun getServerForProfile(profile: Profile, vpnUser: VpnUser?): Server? =
        getServerForProfile(profile, vpnUser, secureCoreCached)

    fun getServerForProfile(profile: Profile, vpnUser: VpnUser?, secureCoreEnabled: Boolean): Server? {
        val wrapper = profile.wrapper
        val needsSecureCore = profile.isSecureCore ?: secureCoreEnabled
        return when (wrapper.type) {
            ProfileType.FASTEST ->
                getBestScoreServer(secureCoreEnabled, vpnUser)
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

    fun getServerForConnectIntent(connectIntent: AnyConnectIntent, vpnUser: VpnUser?): Server? =
        forConnectIntent(
            connectIntent,
            onFastest = { isSecureCore -> getBestScoreServer(isSecureCore, vpnUser) },
            onFastestInCountry = { vpnCountry, isSecureCore -> getBestScoreServer(vpnCountry.serverList, vpnUser) },
            onFastestInCity = { vpnCountry, servers -> getBestScoreServer(servers, vpnUser) },
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
        onFastest: (isSecureCore: Boolean) -> T,
        onFastestInCountry: (VpnCountry, isSecureCore: Boolean) -> T,
        onFastestInCity: (VpnCountry, List<Server>) -> T,
        onServer: (Server) -> T,
        fallbackResult: T
    ): T = when(connectIntent) {
        is ConnectIntent.FastestInCountry ->
            if (connectIntent.country.isFastest) {
                onFastest(false)
            } else {
                getVpnExitCountry(
                    connectIntent.country.countryCode,
                    false
                )?.let { onFastestInCountry(it, false) } ?: fallbackResult
            }
        is ConnectIntent.FastestInCity -> {
            getVpnExitCountry(connectIntent.country.countryCode, false)?.let { country ->
                onFastestInCity(country, country.serverList.filter { it.city == connectIntent.cityEn })
            } ?: fallbackResult
        }
        is ConnectIntent.SecureCore ->
            if (connectIntent.exitCountry.isFastest) {
                onFastest(true)
            } else {
                val exitCountry = getVpnExitCountry(connectIntent.exitCountry.countryCode, true)
                if (connectIntent.entryCountry.isFastest) {
                    exitCountry?.let { onFastestInCountry(it, true) } ?: fallbackResult
                } else {
                    exitCountry?.serverList?.find {
                        it.entryCountry == connectIntent.entryCountry.countryCode
                    }?.let { onServer(it) } ?: fallbackResult
                }
            }
        is ConnectIntent.Server -> getServerById(connectIntent.serverId)?.let { onServer(it) } ?: fallbackResult
        is AnyConnectIntent.GuestHole -> getServerById(connectIntent.serverId)?.let { onServer(it) } ?: fallbackResult
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
            Storage.save(this, ServerManager::class.java)
        }
    }

    private fun haveWireGuardSupport() =
        serversStore.allServers.any { server -> server.connectingDomains.any { it.publicKeyX25519 != null } }

    @Suppress("ClassOrdering")
    @get:TestOnly val firstNotAccessibleVpnCountry get() =
        getVpnCountries().firstOrNull { !it.hasAccessibleOnlineServer(currentUser.vpnUserCached()) }
            ?: throw UnsupportedOperationException("Should only use this method on free tiers")
}

class GroupedServers(
    private val serverStore: ServersStore,
) {
    var vpnCountries: List<VpnCountry> = emptyList()
        private set
    var secureCoreExitCountries: List<VpnCountry> = emptyList()
        private set
    var secureCoreEntryCountries: List<VpnCountry> = emptyList()
        private set
    var gateways: List<GatewayGroup> = emptyList()
        private set

    init {
        update()
    }

    fun update() {
        group()
    }

    private fun group() {
        fun MutableMap<String, MutableList<Server>>.addServer(key: String, server: Server, uppercase: Boolean = true) {
            val mapKey = if (uppercase) key.uppercase() else key
            getOrPut(mapKey) { mutableListOf() } += server
        }
        fun MutableMap<String, MutableList<Server>>.toVpnCountries(areSecureCoreEntryCountries: Boolean = false) =
            // TODO: remove the use of areSecureCoreEntryCountries when the old map code is removed.
            map { (country, servers) ->
                VpnCountry(country, servers.sortedWith(serverComparator), areSecureCoreEntryCountries)
            }

        val vpnCountries = mutableMapOf<String, MutableList<Server>>()
        val gateways = mutableMapOf<String, MutableList<Server>>()
        val secureCoreEntryCountries = mutableMapOf<String, MutableList<Server>>()
        val secureCoreExitCountries = mutableMapOf<String, MutableList<Server>>()
        for (server in serverStore.allServers) {
            when {
                server.isSecureCoreServer -> {
                    secureCoreEntryCountries.addServer(server.entryCountry, server)
                    secureCoreExitCountries.addServer(server.exitCountry, server)
                }

                server.isGatewayServer && server.gatewayName != null ->
                    gateways.addServer(server.gatewayName!!, server, uppercase = false)

                else ->
                    vpnCountries.addServer(server.flag, server)

            }
        }

        this.vpnCountries = vpnCountries.toVpnCountries()
        this.secureCoreEntryCountries = secureCoreEntryCountries.toVpnCountries()
        this.secureCoreExitCountries = secureCoreExitCountries.toVpnCountries()
        this.gateways = gateways.map { (name, servers) -> GatewayGroup(name, servers) }
    }
}
