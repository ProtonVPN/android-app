/*
 * Copyright (c) 2020 Proton Technologies AG
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

import com.google.gson.annotations.SerializedName
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.removeFirst
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentsManager @Inject constructor(
    @Transient private val scope: CoroutineScope,
    @Transient private val vpnStatusProviderUI: VpnStatusProviderUI,
    @Transient private val onSessionClosed: OnSessionClosed,
) {

    @SerializedName("recentConnections")
    private val migrateRecentConnections = LinkedList<Profile>()
    private val recentCountries = ArrayList<String>()

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
            if (loadedRecents.recentServers != null)
                recentServers.putAll(loadedRecents.recentServers)
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
        onSessionClosed.logoutFlow.onEach {
            clear()
        }.launchIn(scope)
    }

    fun clear() {
        recentCountries.clear()
        recentServers.clear()
        Storage.delete(RecentsManager::class.java)
    }

    fun getRecentCountries(): List<String> = recentCountries

    private fun addToRecentServers(server: Server) {
        recentServers.getOrPut(server.flag) {
            ArrayDeque(RECENT_SERVER_MAX_SIZE + 1)
        }.apply {
            removeFirst { it.serverName == server.serverName }
            addFirst(server)
            if (size > RECENT_SERVER_MAX_SIZE)
                removeLast()
        }
    }

    private fun addToRecentCountries(server: Server) {
        if (server.exitCountry.isNotEmpty()) {
            recentCountries.remove(server.exitCountry)
            if (recentCountries.size > RECENT_MAX_SIZE) {
                recentCountries.removeLast()
            }
            recentCountries.add(0, server.exitCountry)
        }
    }

    fun getRecentServers(country: String): List<Server>? = recentServers[country]

    companion object {
        const val RECENT_MAX_SIZE = 3
        const val RECENT_SERVER_MAX_SIZE = 3
    }
}
