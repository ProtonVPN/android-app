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
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.servers.api.LoadUpdate
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.servers.api.LogicalsStatusId
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
    private val updateServersWithBinaryStatus: UpdateServersWithBinaryStatus,
) {
    private data class ServerLists(
        val allServers: List<Server>,
        val allServersByScore: List<Server>,
        val vpnCountries: List<VpnCountry>,
        val secureCoreExitCountries: List<VpnCountry>,
        val gateways: List<GatewayGroup>,
        val statusId: LogicalsStatusId? = null,
    )

    private data class UpdateResult(
        val statusId: LogicalsStatusId?,
        val servers: List<Server>,
    )

    // Protect all modifications with the mutex. Use updateWithMutex for common cases.
    private val updateMutex = Mutex()
    private var serverLists = ServerLists(
        allServers = emptyList(),
        allServersByScore = emptyList(),
        vpnCountries = emptyList(),
        secureCoreExitCountries = emptyList(),
        gateways = emptyList(),
        statusId = null,
    )

    val statusId: LogicalsStatusId? get() = serverLists.statusId
    val allServers: List<Server> get() = serverLists.allServers
    val allServersByScore: List<Server> get() = serverLists.allServersByScore
    val vpnCountries: List<VpnCountry> get() = serverLists.vpnCountries
    val secureCoreExitCountries: List<VpnCountry> get() = serverLists.secureCoreExitCountries
    val gateways: List<GatewayGroup> get() = serverLists.gateways

    // Load servers from storage. Returns true if servers were loaded successfully.
    suspend fun load(): Boolean {
        val loaded = serversStore.load()
        updateWithMutex(saveToStorage = false) { with(serversStore) { UpdateResult(serversStatusId, allServers) } }
        return loaded
    }

    suspend fun replaceServers(serverList: List<Server>, newStatusId: LogicalsStatusId?, retainIDs: Set<String>) {
        updateWithMutex {
            if (retainIDs.isNotEmpty()) {
                withContext(dispatcherProvider.Comp) {
                    val missingServerIDs = retainIDs.toMutableSet()
                    for (server in serverList) {
                        if (server.serverId in retainIDs)
                            missingServerIDs.remove(server.serverId)
                    }
                    val retainedServers = allServers.filter { it.serverId in missingServerIDs }
                    UpdateResult(newStatusId, serverList + retainedServers)
                }
            } else {
                UpdateResult(newStatusId, serverList)
            }
        }
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        updateWithMutex {
            val updatedServers = buildList(allServers.size) {
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
            UpdateResult(statusId, updatedServers)
        }
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        updateWithMutex {
            val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
            val updatedServers = buildList(allServers.size) {
                allServers.forEach { currentServer ->
                    val newValues = loadsMap[currentServer.serverId]
                    val server = if (newValues != null) {
                        // Status update doesn't include physical servers, it's not safe to go from
                        // disabled to enabled without the full information.
                        val newIsOnline = newValues.isOnline.takeIf { currentServer.rawIsOnline } ?: false
                        currentServer.copy(
                            score = newValues.score,
                            load = newValues.load,
                            rawIsOnline = newIsOnline,
                        )
                    } else {
                        currentServer
                    }
                    add(server)
                }
            }
            UpdateResult(statusId, updatedServers)
        }
    }

    suspend fun updateBinaryLoads(statusId: LogicalsStatusId, statusData: ByteArray) {
        updateWithMutex {
            if (statusId != this.statusId) return@updateWithMutex null
            val updatedServers = updateServersWithBinaryStatus(serversStore.allServers, statusData)
            updatedServers?.let { UpdateResult(statusId, updatedServers) }
        }
    }

    suspend fun updateOrAddServer(server: Server) {
        updateWithMutex {
            withContext(dispatcherProvider.Comp) {
                UpdateResult(
                    statusId,
                    allServers.toMutableList().apply {
                        removeIf { it.serverId == server.serverId }
                        add(server)
                    }
                )
            }
        }
    }

    private suspend fun updateWithMutex(
        saveToStorage: Boolean = true,
        updateBlock: suspend () -> UpdateResult?,
    ) {
        updateMutex.withLock {
            val updateResult: Pair<List<Server>, ServerLists>? = withContext(dispatcherProvider.Comp) {
                val update = updateBlock() ?: return@withContext null
                val newServers = update.servers.filter { it.isVisible }
                val groupedServers = async { updateServerLists(newServers, update.statusId) }
                val sortedServers = async { newServers.sortedBy { it.score } }
                val newServerLists = groupedServers.await()
                    .copy(allServersByScore = sortedServers.await())
                update.servers to newServerLists
            }
            if (updateResult != null) {
                val (allServers, newServerLists) = updateResult
                if (saveToStorage) {
                    serversStore.save(allServers, newServerLists.statusId)
                }
                serverLists = newServerLists
            }
        }
    }

    companion object {
        private fun updateServerLists(newServerList: List<Server>, statusId: LogicalsStatusId?): ServerLists {
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
                        vpnCountries.addServer(server.exitCountry, server)

                }
            }

            return ServerLists(
                statusId = statusId,
                allServers = newServerList,
                vpnCountries = vpnCountries.toVpnCountries(),
                secureCoreExitCountries = secureCoreExitCountries.toVpnCountries(),
                gateways = gateways.map { (name, servers) -> GatewayGroup(name, servers) },
                allServersByScore = emptyList() // This value will be set by the caller.
            )
        }
    }
}
