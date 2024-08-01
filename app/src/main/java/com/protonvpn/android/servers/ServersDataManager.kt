/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.LoadUpdate
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.VpnCountry
import javax.inject.Inject
import javax.inject.Singleton

private val serverComparator = compareBy<Server> { !it.isFreeServer }
    .thenBy { it.serverNumber >= 100 }
    .thenBy { it.serverNumber }

@Singleton
class ServersDataManager @Inject constructor(
    private val serversStore: ServersStore
) {
    val allServers: List<Server> get() = serversStore.allServers
    var vpnCountries: List<VpnCountry> = emptyList()
        private set
    var secureCoreExitCountries: List<VpnCountry> = emptyList()
        private set
    var gateways: List<GatewayGroup> = emptyList()
        private set

    suspend fun load() {
        serversStore.load()
        group()
    }

    fun replaceServers(serverList: List<Server>) {
        serversStore.allServers = serverList
        serversStore.save()
        group()
    }

    fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        allServers.flatMap { it.connectingDomains.asSequence() }
            .find { it.id == connectingDomain.id }
            ?.let { it.isOnline = connectingDomain.isOnline }
        serversStore.save()
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
    }

    private fun group() {
        fun MutableMap<String, MutableList<Server>>.addServer(key: String, server: Server, uppercase: Boolean = true) {
            val mapKey = if (uppercase) key.uppercase() else key
            getOrPut(mapKey) { mutableListOf() } += server
        }
        fun MutableMap<String, MutableList<Server>>.toVpnCountries() =
            map { (country, servers) -> VpnCountry(country, servers.sortedWith(serverComparator)) }

        val vpnCountries = mutableMapOf<String, MutableList<Server>>()
        val gateways = mutableMapOf<String, MutableList<Server>>()
        val secureCoreExitCountries = mutableMapOf<String, MutableList<Server>>()
        for (server in allServers) {
            when {
                server.isSecureCoreServer -> {
                    secureCoreExitCountries.addServer(server.exitCountry, server)
                }

                server.isGatewayServer && server.gatewayName != null ->
                    gateways.addServer(server.gatewayName!!, server, uppercase = false)

                else ->
                    vpnCountries.addServer(server.flag, server)

            }
        }

        this.vpnCountries = vpnCountries.toVpnCountries()
        this.secureCoreExitCountries = secureCoreExitCountries.toVpnCountries()
        this.gateways = gateways.map { (name, servers) -> GatewayGroup(name, servers) }
    }
}
