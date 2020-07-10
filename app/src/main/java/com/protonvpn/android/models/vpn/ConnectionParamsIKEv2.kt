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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import org.strongswan.android.data.VpnProfile
import org.strongswan.android.data.VpnType

class ConnectionParamsIKEv2(
    profile: Profile,
    server: Server,
    connectingDomain: ConnectingDomain
) : ConnectionParams(profile, server, connectingDomain, VpnProtocol.IKEv2), java.io.Serializable {

    fun getStrongSwanProfile(userData: UserData, appConfig: AppConfig) = VpnProfile().apply {
        name = server.displayName

        mtu = userData.mtuSize
        vpnType = VpnType.IKEV2_EAP
        id = 1
        userName = userData.vpnUserName + profile.getNetShieldProtocol(userData, appConfig).protocolString
        userPassword = userData.vpnPassword
        splitTunneling =
                VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6
        flags = VpnProfile.FLAGS_SUPPRESS_CERT_REQS
        gateway = if (server.isSecureCoreServer) connectingDomain.entryIp else connectingDomain.getExitIP()
        remoteId = connectingDomain.entryDomain

        setExcludedSubnets(if (userData.useSplitTunneling) userData.splitTunnelIpAddresses else ArrayList())
        setSelectedApps(if (userData.useSplitTunneling) userData.splitTunnelApps else ArrayList())
    }
}
