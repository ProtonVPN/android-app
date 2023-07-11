/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.profiles

import androidx.lifecycle.ViewModel
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.serverComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ServerSelectionViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val currentUser: CurrentUser
) : ViewModel() {

    data class ServerItem(
        val id: String,
        val flag: String,
        val name: String,
        val online: Boolean,
        val accessible: Boolean
    )

    // TODO: add filtering by protocol
    fun getServers(countryCode: String, secureCore: Boolean): List<ServerItem> =
        getAllServers(countryCode, secureCore).map {
            ServerItem(
                it.serverId,
                if (secureCore) it.entryCountry else it.exitCountry,
                if (secureCore) CountryTools.getFullName(it.entryCountry) else it.serverName,
                it.online,
                currentUser.vpnUserCached().hasAccessToServer(it)
            )
        }

    private fun getAllServers(countryCode: String, secureCore: Boolean): List<Server> {
        val gatewayServers = if (!secureCore) {
            serverManager.getGateways().map { it.serverList }.flatten().filter { it.flag == countryCode }
        } else {
            emptyList()
        }
        val regularServers = serverManager.getVpnExitCountry(countryCode, secureCore)?.serverList ?: emptyList()
        return (regularServers + gatewayServers).sortedWith(serverComparator)
    }
}
