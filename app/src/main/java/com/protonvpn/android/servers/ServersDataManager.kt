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
import com.protonvpn.android.utils.replace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

private val serverComparator = compareBy<Server> { !it.isFreeServer }
    .thenBy { it.serverNumber >= 100 }
    .thenBy { it.serverNumber }

@Singleton
class ServersDataManager @Inject constructor(
    mainScope: CoroutineScope,
    private val serversStore: ServersStore,
    private val immutableServerList: dagger.Lazy<IsImmutableServerListEnabled>
) {
    // Use the same value for the whole process lifetime.
    private val immutableServerListEnabled = mainScope.async(start = CoroutineStart.LAZY) {
        immutableServerList.get().invoke()
    }

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

    suspend fun replaceServers(serverList: List<Server>) {
        serversStore.allServers = serverList
        if (immutableServerListEnabled.await()) {
            serversStore.save()
        } else {
            serversStore.saveMutable()
        }
        group()
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        if (immutableServerListEnabled.await()) {
            val updatedServers = buildList(allServers.size) {
                allServers.forEach { currentServer ->
                    val server = if (currentServer.connectingDomains.any { it.id == connectingDomain.id }) {
                        val updatedConnectingDomains =
                            currentServer.connectingDomains.replace(connectingDomain) { it.id == connectingDomain.id }
                        currentServer.copy(connectingDomains = updatedConnectingDomains)
                    } else {
                        currentServer
                    }
                    add(server)
                }
            }
            serversStore.allServers = updatedServers
            serversStore.save()
            group()
        } else {
            allServers.flatMap { it.connectingDomains.asSequence() }
                .find { it.id == connectingDomain.id }
                ?.let { it.isOnline = connectingDomain.isOnline }
            serversStore.saveMutable()
        }
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
        if (immutableServerListEnabled.await()) {
            val updatedServers = buildList(allServers.size) {
                allServers.forEach { currentServer ->
                    val newValues = loadsMap[currentServer.serverId]
                    val server = if (newValues != null) {
                        val updatedConnectingDomains = with(currentServer) {
                            if (online != newValues.isOnline && newValues.isOnline && connectingDomains.size == 1) {
                                // If server becomes online we don't know which connectingDomains became available based on /loads
                                // response. If there's more than one connectingDomain it'll have to wait for /logicals response
                                listOf(connectingDomains.first().copy(isOnline = newValues.isOnline))
                            } else {
                                connectingDomains
                            }
                        }

                        currentServer.copy(
                            score = newValues.score,
                            load = newValues.load,
                            isOnline = newValues.isOnline,
                            connectingDomains = updatedConnectingDomains
                        )
                    } else {
                        currentServer
                    }
                    add(server)
                }
            }
            serversStore.allServers = updatedServers
            serversStore.save()
            group()
        } else {
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
            serversStore.saveMutable()
        }
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
