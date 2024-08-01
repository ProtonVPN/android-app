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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val serverComparator = compareBy<Server> { !it.isFreeServer }
    .thenBy { it.serverNumber >= 100 }
    .thenBy { it.serverNumber }

@Singleton
class ServersDataManager @Inject constructor(
    mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val serversStore: ServersStore,
    private val immutableServerList: dagger.Lazy<IsImmutableServerListEnabled>
) {
    private class ServerLists(
        val allServers: List<Server> = emptyList(),
        val vpnCountries: List<VpnCountry> = emptyList(),
        val secureCoreExitCountries: List<VpnCountry> = emptyList(),
        val gateways: List<GatewayGroup> = emptyList()
    )

    // Use the same value for the whole process lifetime.
    private val immutableServerListEnabled = mainScope.async(start = CoroutineStart.LAZY) {
        immutableServerList.get().invoke()
    }

    // Protect all modifications with the mutex. Use updateWithMutex for common cases.
    private val updateMutex = Mutex()
    private var serverLists = ServerLists()

    val allServers: List<Server> get() = serverLists.allServers
    val vpnCountries: List<VpnCountry> get() = serverLists.vpnCountries
    val secureCoreExitCountries: List<VpnCountry> get() = serverLists.secureCoreExitCountries
    val gateways: List<GatewayGroup> get() = serverLists.gateways

    suspend fun load() {
        serversStore.load()
        serverLists = updateServerLists(serversStore.allServers)
    }

    suspend fun replaceServers(serverList: List<Server>) {
        if (immutableServerListEnabled.await()) {
            updateWithMutex {
                serverList
            }
        } else {
            serversStore.allServers = serverList
            serversStore.saveMutable()
            serverLists = updateServerLists(serversStore.allServers)
        }
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        if (immutableServerListEnabled.await()) {
            updateWithMutex {
                buildList(allServers.size) {
                    allServers.forEach { currentServer ->
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
        } else {
            allServers.flatMap { it.connectingDomains.asSequence() }
                .find { it.id == connectingDomain.id }
                ?.let { it.isOnline = connectingDomain.isOnline }
            serversStore.saveMutable()
        }
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        if (immutableServerListEnabled.await()) {
            updateWithMutex {
                val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
                buildList(allServers.size) {
                    allServers.forEach { currentServer ->
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
        } else {
            val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
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

    private suspend fun updateWithMutex(updateBlock: suspend () -> List<Server>) {
        updateMutex.withLock {
            val newServers = withContext(dispatcherProvider.Comp) {
                updateServerLists(updateBlock())
            }
            serversStore.allServers = newServers.allServers
            serversStore.save()
            serverLists = newServers
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
                map { (country, servers) -> VpnCountry(country, servers.sortedWith(serverComparator)) }

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
