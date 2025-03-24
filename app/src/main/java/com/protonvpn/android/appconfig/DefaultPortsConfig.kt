/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.android.appconfig

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DefaultPortsConfig(
    @SerialName(value = "OpenVPN") private val openVpnPorts: DefaultPorts,
    @SerialName(value = "WireGuard") private val wireguardPorts: DefaultPorts
) {
    fun getOpenVPNPorts() =
        if (openVpnPorts.tcpPorts.isEmpty() || openVpnPorts.udpPorts.isEmpty())
            openVPNDefaults
        else
            openVpnPorts

    fun getWireguardPorts() =
        if (wireguardPorts.udpPorts.isEmpty() || wireguardPorts.tcpPorts.isEmpty())
            wireguardDefaults
        else
            wireguardPorts

    companion object {
        private val wireguardDefaults =
            DefaultPorts(listOf(51820), listOf(443))
        private val openVPNDefaults =
            DefaultPorts(listOf(443), listOf(443))
        val defaultConfig = DefaultPortsConfig(openVPNDefaults, wireguardDefaults)
    }
}
