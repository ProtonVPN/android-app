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

import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import me.proton.core.util.kotlin.removeFirst
import java.util.LinkedList
import javax.inject.Singleton

@Singleton
class RecentsManager(
    @Transient private val stateMonitor: VpnStateMonitor,
    @Transient private val serverManager: ServerManager
) {

    private val recentConnections = LinkedList<Profile>()
    private var connectionOnHold: Profile? = null

    init {
        Storage.load(RecentsManager::class.java)?.let {
            recentConnections.addAll(it.recentConnections)
        }
        recentConnections.forEach { it.wrapper.setDeliverer(serverManager) }

        stateMonitor.vpnStatus.observeForever { status ->
            if (status.state == VpnState.Connected) {
                connectionOnHold = status.profile
            }
            if (status.state == VpnState.Disconnecting) {
                connectionOnHold?.let { profile ->
                    if (!profile.isPreBakedProfile)
                        addLastConnectionToRecents(profile)

                    connectionOnHold = null
                    Storage.save(this)
                }
            }
        }
    }

    fun getRecentConnections(): List<Profile> = recentConnections
        .filter {
            it.server?.exitCountry != serverManager.defaultConnection.server?.exitCountry &&
                it.server?.exitCountry != stateMonitor.connectingToServer?.exitCountry
        }

    private fun addLastConnectionToRecents(profile: Profile) {
        recentConnections.removeFirst { profile.name == it.name }
        if (recentConnections.size > RECENT_MAX_SIZE) {
            recentConnections.removeLast()
        }
        recentConnections.push(profile)
    }

    companion object {
        const val RECENT_MAX_SIZE = 3
    }
}
