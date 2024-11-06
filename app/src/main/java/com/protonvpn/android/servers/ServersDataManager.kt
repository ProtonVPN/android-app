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

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.LoadUpdate
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.replace
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServersDataManager @Inject constructor(
    private val dispatcherProvider: VpnDispatcherProvider,
    private val serversStore: ServersStore,
) {
    private data class ServerLists(
        val allServers: List<Server> = emptyList(),
        val allServersByScore: List<Server> = emptyList(),
        val vpnCountries: List<VpnCountry> = emptyList(),
        val secureCoreExitCountries: List<VpnCountry> = emptyList(),
        val gateways: List<GatewayGroup> = emptyList(),
    )

    // Protect all modifications with the mutex. Use updateWithMutex for common cases.
    private val updateMutex = Mutex()
    private var serverLists = ServerLists()

    val allServers: List<Server> get() = serverLists.allServers
    val allServersByScore: List<Server> get() = serverLists.allServersByScore
    val vpnCountries: List<VpnCountry> get() = serverLists.vpnCountries
    val secureCoreExitCountries: List<VpnCountry> get() = serverLists.secureCoreExitCountries
    val gateways: List<GatewayGroup> get() = serverLists.gateways

    suspend fun load() {
        serversStore.load()
        updateWithMutex(serversStore.allServers, saveToStorage = false)
    }

    suspend fun replaceServers(serverList: List<Server>) {
        updateWithMutex(serverList)
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        updateWithMutex(allServers) { servers ->
            buildList(servers.size) {
                servers.forEach { currentServer ->
                    val server = if (currentServer.connectingDomains.any { it.id == connectingDomain.id }) {
                        val updatedConnectingDomains = currentServer.connectingDomains.replace(
                            connectingDomain,
                            predicate = { it.id == connectingDomain.id }
                        )
                        currentServer.copy(connectingDomains = updatedConnectingDomains)
                    } else {
                        currentServer
                    }
                    add(server)
                }
            }
        }
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        updateWithMutex(allServers) { servers ->
            val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
            buildList(servers.size) {
                servers.forEach { currentServer ->
                    val newValues = loadsMap[currentServer.serverId]
                    val server = if (newValues != null) {
                        val updatedConnectingDomains = with(currentServer) {
                            // If server becomes online we don't know which connectingDomains became available
                            // based on /loads response. If there's more than one connectingDomain it'll have to
                            // wait for /logicals response
                            if (online != newValues.isOnline && newValues.isOnline && connectingDomains.size == 1) {
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
        }
    }

    private suspend fun updateWithMutex(
        servers: List<Server>,
        saveToStorage: Boolean = true,
        updateBlock: suspend (List<Server>) -> List<Server> = { it }
    ) {
        updateMutex.withLock {
            val newServerLists = withContext(dispatcherProvider.Comp) {
                val newServers = updateBlock(servers)
                val groupedServers = async { updateServerLists(newServers) }
                val sortedServers = async { newServers.sortedBy { it.score } }
                groupedServers.await()
                    .copy(allServersByScore = sortedServers.await())
            }
            if (saveToStorage) {
                serversStore.allServers = newServerLists.allServers
                serversStore.save()
            }
            serverLists = newServerLists
        }
    }

    companion object {
        private fun updateServerLists(newServerList: List<Server>): ServerLists {
            fun MutableMap<String, MutableList<Server>>.addServer(
                key: String,
                server: Server,
                uppercase: Boolean = true
            ) {
                val mapKey = if (uppercase) key.uppercase() else key
                getOrPut(mapKey) { mutableListOf() } += server
            }

            fun MutableMap<String, MutableList<Server>>.toVpnCountries() =
                map { (country, servers) -> VpnCountry(country, servers) }

            val vpnCountries = mutableMapOf<String, MutableList<Server>>()
            val gateways = mutableMapOf<String, MutableList<Server>>()
            val secureCoreExitCountries = mutableMapOf<String, MutableList<Server>>()
            for (server in newServerList) {
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

            return ServerLists(
                allServers = newServerList,
                vpnCountries = vpnCountries.toVpnCountries(),
                secureCoreExitCountries = secureCoreExitCountries.toVpnCountries(),
                gateways = gateways.map { (name, servers) -> GatewayGroup(name, servers) },
            )
        }
    }
}
