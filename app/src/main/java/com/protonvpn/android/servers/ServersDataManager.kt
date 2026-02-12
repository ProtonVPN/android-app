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
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.LoadUpdate
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.utils.replace
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
    @WallClock private val wallClock: () -> Long,
) {
    data class ServerLists(
        val allServers: List<Server>,
        val allServersByScore: List<Server>,
        val vpnCountries: List<VpnCountry>,
        val secureCoreExitCountries: List<VpnCountry>,
        val gateways: List<GatewayGroup>,
        val statusId: LogicalsStatusId? = null,
    ) {
        companion object {
            val Empty = ServerLists(
                allServers = emptyList(),
                allServersByScore = emptyList(),
                vpnCountries = emptyList(),
                secureCoreExitCountries = emptyList(),
                gateways = emptyList(),
                statusId = null,
            )
        }
    }

    private val serverListsFlow = MutableStateFlow<ServerLists?>(null)
    val serverLists: Flow<ServerLists> = serverListsFlow.filterNotNull()
    var lastUpdateTimestamp: Long = 0
        private set

    private data class UpdateResult(
        val statusId: LogicalsStatusId?,
        val servers: List<Server>,
        val serverListUpdateTimestamp: Long?,
    )

    // Protect all modifications with the mutex. Use updateWithMutex for common cases.
    private val updateMutex = Mutex()

    // Load servers from storage. Returns true if servers were loaded successfully.
    suspend fun load(): Boolean {
        var loaded = false
        updateWithMutex(saveToStorage = false) {
            loaded = serversStore.load()
            with(serversStore) {
                UpdateResult(serversStatusId, allServers, lastUpdateTimestamp)
            }
        }
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
                    val retainedServers = currentServers().allServers.filter { it.serverId in missingServerIDs }
                    UpdateResult(newStatusId, serverList + retainedServers, wallClock())
                }
            } else {
                UpdateResult(newStatusId, serverList, wallClock())
            }
        }
    }

    suspend fun updateServerDomainStatus(connectingDomain: ConnectingDomain) {
        updateWithMutex {
            val allServers = currentServers().allServers
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
            UpdateResult(currentServers().statusId, updatedServers, serverListUpdateTimestamp = null)
        }
    }

    suspend fun updateLoads(loadsList: List<LoadUpdate>) {
        updateWithMutex {
            val loadsMap: Map<String, LoadUpdate> = loadsList.associateBy { it.id }
            val updatedServers = buildList(currentServers().allServers.size) {
                currentServers().allServers.forEach { currentServer ->
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
            UpdateResult(currentServers().statusId, updatedServers, serverListUpdateTimestamp = null)
        }
    }

    suspend fun updateBinaryLoads(statusId: LogicalsStatusId, statusData: ByteArray) {
        updateWithMutex {
            if (statusId != currentServers().statusId) return@updateWithMutex null
            val updatedServers = updateServersWithBinaryStatus(serversStore.allServers, statusData)
            updatedServers?.let {
                UpdateResult(statusId, updatedServers, serverListUpdateTimestamp = null)
            }
        }
    }

    suspend fun updateLastUpdateTimestamp(timestamp: Long = wallClock()) {
        updateWithMutex {
            UpdateResult(currentServers().statusId, currentServers().allServers, timestamp)
        }
    }

    suspend fun updateOrAddServer(server: Server) {
        updateWithMutex {
            withContext(dispatcherProvider.Comp) {
                UpdateResult(
                    currentServers().statusId,
                    currentServers().allServers.toMutableList().apply {
                        removeIf { it.serverId == server.serverId }
                        add(server)
                    },
                    wallClock()
                )
            }
        }
    }

    private suspend fun updateWithMutex(
        saveToStorage: Boolean = true,
        updateBlock: suspend () -> UpdateResult?,
    ) {
        updateMutex.withLock {
            val updateResult: Triple<List<Server>, ServerLists, Long?>? = withContext(dispatcherProvider.Comp) {
                val update = updateBlock() ?: return@withContext null
                val newServers = update.servers.filter { it.isVisible }
                val groupedServers = async { updateServerLists(newServers, update.statusId) }
                val sortedServers = async { newServers.sortedBy { it.score } }
                val newServerLists = groupedServers.await()
                    .copy(allServersByScore = sortedServers.await())
                Triple(update.servers,newServerLists, update.serverListUpdateTimestamp)
            }
            if (updateResult != null) {
                val (allServers, newServerLists, updateTimestamp) = updateResult
                if (updateTimestamp != null) {
                    lastUpdateTimestamp = updateTimestamp
                }
                if (saveToStorage) {
                    serversStore.save(allServers, newServerLists.statusId, lastUpdateTimestamp)
                }

                serverListsFlow.value = newServerLists
            }
        }
    }

    // Use only when protected by the updateMutex.
    private fun currentServers() = serverListsFlow.value ?: ServerLists.Empty

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
