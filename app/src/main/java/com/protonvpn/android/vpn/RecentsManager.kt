/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.vpn

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.servers.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.removeFirst
import java.lang.reflect.Type
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentsManager @Inject constructor(
    @Transient private val scope: CoroutineScope,
    @Transient private val vpnStatusProviderUI: VpnStatusProviderUI,
    serverManager: ServerManager,
) : java.io.Serializable {

    @SerializedName("recentConnections")
    private val migrateRecentConnections = LinkedList<Profile>()
    private val recentCountries = ArrayList<String>()

    // Workaround for R8:
    // with R8 there is not enough info to deserialize the ArrayDeque items as Server objects and I can't figure out
    // rules to make it work.
    // As a workaround use an explicit deserializer. In the longer term we should move to storing recents in a DB.
    @JsonAdapter(RecentServersJsonAdapter::class)
    // Country code -> Servers
    private val recentServers = LinkedHashMap<String, ArrayDeque<Server>>()

    @Transient val update = MutableSharedFlow<Unit>()

    init {
        Storage.load(RecentsManager::class.java)?.let { loadedRecents ->
            // Remove migration in some time.
            if (loadedRecents.migrateRecentConnections.isNotEmpty()) {
                recentCountries.addAll(
                    loadedRecents.migrateRecentConnections.filter { it.country.isNotBlank() }.map { it.country }
                )
                // This migration will be saved when the recents are updated. Let's not do this here, while still
                // executing the constructor.
            }
            // Older version might have these fields missing.
            if (loadedRecents.recentCountries != null)
                recentCountries.addAll(loadedRecents.recentCountries)
            if (loadedRecents.recentServers != null) {
                recentServers.putAll(
                    loadedRecents.recentServers
                        .mapValues { (_, servers) ->
                            servers.mapNotNullTo(ArrayDeque()) { serverManager.getServerById(it.serverId) }
                        }
                        .filter { (_, servers) -> servers.isNotEmpty() }
                )
            }
        }

        scope.launch {
            vpnStatusProviderUI.status.collect { status ->
                if (status.state == VpnState.Connected) {
                    status.connectionParams?.let { params ->
                        addToRecentServers(params.server)
                        addToRecentCountries(params.server)
                        Storage.save(this@RecentsManager, RecentsManager::class.java)
                        update.emit(Unit)
                    }
                }
            }
        }
    }

    fun clear() {
        recentCountries.clear()
        recentServers.clear()
        Storage.delete(RecentsManager::class.java)
    }

    fun getRecentCountries(): List<String> = recentCountries

    private fun addToRecentServers(server: Server) {
        recentServers.getOrPut(server.exitCountry) {
            ArrayDeque(RECENT_SERVER_MAX_SIZE + 1)
        }.apply {
            removeFirst { it.serverName == server.serverName }
            addFirst(server)
            if (size > RECENT_SERVER_MAX_SIZE)
                removeAt(lastIndex)
        }
    }

    private fun addToRecentCountries(server: Server) {
        if (server.exitCountry.isNotEmpty()) {
            recentCountries.remove(server.exitCountry)
            if (recentCountries.size > RECENT_MAX_SIZE) {
                recentCountries.removeAt(recentCountries.lastIndex)
            }
            recentCountries.add(0, server.exitCountry)
        }
    }

    fun getRecentServers(country: String): List<Server>? = recentServers[country]

    fun getAllRecentServers(): List<Server> = recentServers.flatMap { (_, servers) -> servers }

    class RecentServersJsonAdapter : JsonDeserializer<LinkedHashMap<String, ArrayDeque<Server>>>,
                                     JsonSerializer<LinkedHashMap<String, ArrayDeque<Server>>>
    {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): LinkedHashMap<String, ArrayDeque<Server>> {
            if (json.isJsonObject) {
                val result = LinkedHashMap<String, ArrayDeque<Server>>()
                val jsonMap = json.asJsonObject
                jsonMap.keySet().associateWithTo(result) { country ->
                    jsonMap.get(country).asJsonArray.asList().mapTo(ArrayDeque()) { jsonServer ->
                        context.deserialize(jsonServer, Server::class.java)
                    }
                }
                return result
            }
            return LinkedHashMap()
        }

        override fun serialize(
            src: LinkedHashMap<String, ArrayDeque<Server>>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement = context.serialize(src)
    }

    companion object {
        const val RECENT_MAX_SIZE = 3
        const val RECENT_SERVER_MAX_SIZE = 3
    }
}
