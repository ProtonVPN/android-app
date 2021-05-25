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
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.Constants

open class ConnectionParams(
    val profile: Profile,
    val server: Server,
    val connectingDomain: ConnectingDomain?,
    val protocol: VpnProtocol?
) : java.io.Serializable {

    open val info get() = "Profile: ${profile.name} IP: ${connectingDomain?.entryDomain} Protocol: $protocol"
    open val transmission: TransmissionProtocol? get() = null

    val exitIpAddress: String?
        get() = connectingDomain?.getExitIP()

    fun getVpnUsername(userData: UserData, appConfig: AppConfig): String {
        var username = userData.vpnUserName + profile.getNetShieldProtocol(userData, appConfig).protocolString +
            Constants.VPN_USERNAME_PRODUCT_SUFFIX
        if (!connectingDomain?.label.isNullOrBlank())
            username += "+b:${connectingDomain?.label}"
        if (appConfig.getFeatureFlags().vpnAccelerator && !userData.isSmartReconnectEnabled)
            username += "+nst"
        return username
    }

    override fun toString() = info

    open fun hasSameProtocolParams(other: ConnectionParams) =
        other.protocol == protocol
}
