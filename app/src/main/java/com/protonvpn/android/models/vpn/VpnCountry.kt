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

import android.content.Context
import com.protonvpn.android.components.Listable
import com.protonvpn.android.components.Markable
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.utils.CountryTools
import java.io.Serializable
import java.util.Collections
import kotlin.collections.ArrayList

class VpnCountry(
    val flag: String,
    serverList: List<Server>,
    deliverer: ServerDeliver
) : Markable, Serializable, Listable {
    val serverList: List<Server>
    val translatedCoordinates: TranslatedCoordinates

    @Transient var deliverer: ServerDeliver

    val countryName: String
        get() = CountryTools.getFullName(flag)

    val wrapperServers: List<ServerWrapper>
        get() {
            val wrapperList = ArrayList<ServerWrapper>()
            if (connectableServers.size > 1) {
                wrapperList.add(ServerWrapper.makeFastestForCountry(flag, deliverer))
                wrapperList.add(ServerWrapper.makeRandomForCountry(flag, deliverer))
            }
            connectableServers.mapTo(wrapperList) { ServerWrapper.makeWithServer(it, deliverer) }
            return wrapperList
        }

    val keywords get() = serverList.flatMap { it.keywords }.distinct()

    init {
        this.serverList = sortServers(serverList)
        this.deliverer = deliverer
        this.translatedCoordinates = TranslatedCoordinates(flag)
    }

    fun hasAccessibleServer(userData: UserData): Boolean =
        serverList.any { userData.hasAccessToServer(it) }

    fun hasAccessibleOnlineServer(userData: UserData): Boolean =
        serverList.any { userData.hasAccessToServer(it) && it.online }

    fun isUnderMaintenance(): Boolean = !serverList.any { it.online }

    private fun sortServers(serverList: List<Server>): List<Server> {
        Collections.sort(serverList,
                compareBy<Server> { !it.isFreeServer }
                        .thenBy { it.serverNumber >= 100 }
                        .thenBy { it.domain }
                        .thenBy { it.tier })
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

    override fun getMarkerText(): String = countryName

    override fun getConnectableServers(): List<Server> = serverList

    override fun getLabel(context: Context): String = countryName

    fun isSecureCoreCountry(): Boolean = flag == "IS" || flag == "SE" || flag == "CH"
}
