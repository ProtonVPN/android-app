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

import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.removeFirst
import java.util.LinkedList
import javax.inject.Singleton

@Singleton
class RecentsManager(
    @Transient private val scope: CoroutineScope,
    @Transient private val stateMonitor: VpnStateMonitor,
    @Transient private val serverManager: ServerManager,
    @Transient private val onSessionClosed: OnSessionClosed,
) {

    private val recentConnections = LinkedList<Profile>()

    // Country code -> Servers
    private val recentServers = LinkedHashMap<String, ArrayDeque<Server>>()

    @Transient val update = MutableSharedFlow<Unit>()

    init {
        Storage.load(RecentsManager::class.java)?.let {
            recentConnections.addAll(it.recentConnections)
            // Older version might have this field missing
            if (it.recentServers != null)
                recentServers.putAll(it.recentServers)
        }
        recentConnections.forEach { it.wrapper.setDeliverer(serverManager) }

        scope.launch {
            stateMonitor.status.collect { status ->
                if (status.state == VpnState.Connected) {
                    status.connectionParams?.let { params ->
                        addToRecentServers(params.server)
                        addToRecentCountries(params.profile)
                        Storage.save(this@RecentsManager)
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
        recentConnections.clear()
        recentServers.clear()
        Storage.delete(RecentsManager::class.java)
    }

    fun getRecentCountries(): List<Profile> = recentConnections
        .filter { profile ->
            profile != serverManager.defaultConnection && profile != stateMonitor.connectingToProfile
        }

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

    private fun addToRecentCountries(profile: Profile) {
        recentConnections.removeFirst {
            (profile.name.isNotBlank() && profile.name == it.name)
                    || profile.country == it.country
        }
        if (recentConnections.size > RECENT_MAX_SIZE) {
            recentConnections.removeLast()
        }
        recentConnections.push(profile)
    }

    fun getRecentServers(country: String): List<Server>? = recentServers[country]

    companion object {
        const val RECENT_MAX_SIZE = 3
        const val RECENT_SERVER_MAX_SIZE = 3
    }
}
