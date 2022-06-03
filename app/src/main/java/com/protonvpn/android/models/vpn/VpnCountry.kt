/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.components.Markable
import com.protonvpn.android.utils.CountryTools
import java.io.Serializable
import java.util.Collections

class VpnCountry(
    val flag: String,
    serverList: List<Server>,
) : Markable, Serializable {
    val serverList: List<Server>
    val translatedCoordinates: TranslatedCoordinates

    val countryName: String
        get() = CountryTools.getFullName(flag)

    init {
        this.serverList = sortServers(serverList)
        this.translatedCoordinates = TranslatedCoordinates(flag)
    }

    fun hasAccessibleServer(vpnUser: VpnUser?): Boolean =
        serverList.any { vpnUser.hasAccessToServer(it) }

    fun hasAccessibleOnlineServer(vpnUser: VpnUser?): Boolean =
        serverList.any { vpnUser.hasAccessToServer(it) && it.online }

    fun isUnderMaintenance(): Boolean = !serverList.any { it.online }

    private fun sortServers(serverList: List<Server>): List<Server> {
        Collections.sort(serverList,
                compareBy<Server> { !it.isFreeServer }
                        .thenBy { it.serverNumber >= 100 }
                        .thenBy { it.serverNumber })
        return serverList
    }

    fun hasConnectedServer(server: Server?): Boolean {
        if (server == null) {
            return false
        }
        return serverList.any { it.domain == server.domain }
    }

    override fun getCoordinates(): TranslatedCoordinates = translatedCoordinates

    override fun isSecureCoreMarker(): Boolean = isSecureCoreCountry()

    override fun getMarkerEntryCountryCode(): String? = null

    override fun getMarkerCountryCode(): String = flag

    override fun getConnectableServers(): List<Server> = serverList

    fun isSecureCoreCountry(): Boolean = flag == "IS" || flag == "SE" || flag == "CH"
}
