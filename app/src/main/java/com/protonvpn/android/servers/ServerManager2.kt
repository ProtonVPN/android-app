/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.servers

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper for ServerManager with suspending API.
 *
 * New code should use suspending/asynchronous API and ServerManager should be phased out. When most of the code
 * doesn't require the old, synchronous ServerManager storage can be replaced with something more suitable
 * (e.g. a database).
 */
@Singleton
class ServerManager2 @Inject constructor(
    private val serverManager: ServerManager,
    private val currentUserSettings: EffectiveCurrentUserSettings,
    private val supportsProtocol: SupportsProtocol,
) {

    // Same as ServerManager.serverListVersion but emits states only after servers are loaded.
    val serverListVersion = flow {
        serverManager.ensureLoaded()
        emitAll(serverManager.serverListVersion)
    }

    val isDownloadedAtLeastOnceFlow = serverListVersion.map {
        serverManager.isDownloadedAtLeastOnce()
    }

    val allServersFlow get() = serverListVersion.map { serverManager.allServers }

    suspend fun getServerForProfile(profile: Profile, vpnUser: VpnUser?): Server? {
        serverManager.ensureLoaded()
        return serverManager.getServerForProfile(profile, vpnUser, currentUserSettings.secureCore.first())
    }

    suspend fun getVpnExitCountry(countryCode: String, secureCoreCountry: Boolean): VpnCountry? {
        serverManager.ensureLoaded()
        return serverManager.getVpnExitCountry(countryCode, secureCoreCountry)
    }

    suspend fun getFreeCountries(): List<VpnCountry> {
        serverManager.ensureLoaded()
        return serverManager.freeCountries
    }

    suspend fun getServerForConnectIntent(connectIntent: AnyConnectIntent, vpnUser: VpnUser?): Server? {
        serverManager.ensureLoaded()
        return serverManager.getServerForConnectIntent(connectIntent, vpnUser)
    }

    suspend fun getRandomServer(vpnUser: VpnUser?): Server? {
        serverManager.ensureLoaded()
        return serverManager.getRandomServer(vpnUser)
    }

    suspend fun getCountriesAndServersCount(): Pair<Int, Int> {
        serverManager.ensureLoaded()
        return Pair(serverManager.getVpnCountries().size, serverManager.allServerCount)
    }

    /*
     * Perform operations related to ConnectIntent.
     *
     * ConnectIntent can specify either a fastest server overall, fastest in country, a specific server and so on.
     * Use this function to implement operations for a ConnectIntent like checking if its country/city/server is
     * available.
     */
    suspend fun <T> forConnectIntent(
        connectIntent: AnyConnectIntent,
        onFastest: (isSecureCore: Boolean, serverFeatures: Set<ServerFeature>) -> T,
        onFastestInGroup: (List<Server>) -> T,
        onServer: (Server) -> T,
        fallbackResult: T
    ): T {
        serverManager.ensureLoaded()
        return serverManager.forConnectIntent(connectIntent, onFastest, onFastestInGroup, onServer, fallbackResult)
    }

    suspend fun getServerById(id: String): Server? {
        serverManager.ensureLoaded()
        return serverManager.getServerById(id)
    }

    suspend fun getGateways(): List<GatewayGroup> {
        serverManager.ensureLoaded()
        return serverManager.getGateways()
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        serverManager.updateServerDomainStatus(connectingDomain)
    }

    // Sorted by score (best at front)
    suspend fun getOnlineAccessibleServers(
        secureCore: Boolean,
        gatewayName: String?,
        vpnUser: VpnUser?,
        protocol: ProtocolSelection
    ): List<Server> {
        serverManager.ensureLoaded()
        val groups = when {
            secureCore -> serverManager.getExitCountries(secureCore = true)
            gatewayName != null -> serversByGatewayName(gatewayName)
            else -> serverManager.getExitCountries(secureCore = false)
        }
        return groups.asSequence().flatMap { group ->
            group.serverList.filter {
                it.online && vpnUser.hasAccessToServer(it) && supportsProtocol(it, protocol)
            }.asSequence()
        }.sortedBy { it.score }.toList()
    }

    private fun serversByGatewayName(gatewayName: String): List<GatewayGroup> =
        serverManager.getGateways().find { it.name() == gatewayName }?.let { listOf(it) } ?: emptyList()
}
