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
import com.protonvpn.android.auth.data.haveAccessWith
import com.protonvpn.android.utils.CountryTools
import kotlinx.serialization.Transient
import java.io.Serializable

sealed class ServerGroup {
    abstract val serverList: List<Server>

    // Use functions instead of properties to avoid issues with deserialization
    abstract fun id(): String
    abstract fun name(): String

    fun hasAccessibleServer(vpnUser: VpnUser?): Boolean =
        serverList.any { vpnUser.hasAccessToServer(it) }

    fun hasAccessibleServer(userTier: Int?): Boolean =
        serverList.any { it.haveAccessWith(userTier) }

    fun hasAccessibleOnlineServer(vpnUser: VpnUser?): Boolean =
        serverList.any { vpnUser.hasAccessToServer(it) && it.online }

    fun isUnderMaintenance(): Boolean = !serverList.any { it.online }

    fun hasConnectedServer(server: Server?): Boolean {
        if (server == null) {
            return false
        }
        return serverList.any { it.domain == server.domain }
    }
}

class GatewayGroup(
    private val name: String,
    override val serverList: List<Server>,
) : ServerGroup() {
    override fun id(): String = name

    override fun name(): String = name
}

// TODO: remove Serializable when migrations are removed
@kotlinx.serialization.Serializable
data class VpnCountry(
    val flag: String,
    override val serverList: List<Server>,
    private val isSecureCoreEntryCountry: Boolean,
) : ServerGroup(), Serializable {

    @Transient
    val translatedCoordinates: TranslatedCoordinates = TranslatedCoordinates(flag)

    val countryName: String
        get() = CountryTools.getFullName(flag)

    override fun id(): String = flag

    override fun name(): String = countryName

}
