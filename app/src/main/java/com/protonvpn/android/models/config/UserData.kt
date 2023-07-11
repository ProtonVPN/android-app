/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.models.config

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.vpn.ProtocolSelection
import java.io.Serializable
import java.util.UUID

enum class Setting(val logName: String) {
    QUICK_CONNECT_PROFILE("Quick connect"),
    DEFAULT_PROTOCOL("Default protocol"),
    NETSHIELD_PROTOCOL("NetShield protocol"),
    SECURE_CORE("Secure Core"),
    LAN_CONNECTIONS("LAN connections"),
    SPLIT_TUNNEL_ENABLED("Split Tunneling enabled"),
    SPLIT_TUNNEL_APPS("Split Tunneling excluded apps"),
    SPLIT_TUNNEL_IPS("Split Tunneling excluded IPs"),
    DEFAULT_MTU("Default MTU"),
    SAFE_MODE("Safe Mode"),
    RESTRICTED_NAT("Restricted NAT"),
    VPN_ACCELERATOR_ENABLED("VPN Accelerator enabled"),
    VPN_ACCELERATOR_NOTIFICATIONS("VPN Accelerator notifications"),
    API_DOH("Use DoH for API"),
    CONNECT_ON_BOOT("Connect on boot"),
    TELEMETRY("Telemetry")
}

class UserData private constructor() : Serializable {

    val connectOnBoot = false

    var mtuSize = 1375
        private set
    val useSplitTunneling = false
    val splitTunnelApps: List<String> = emptyList()
    val splitTunnelIpAddresses: List<String> = emptyList()
    val defaultProfileId: UUID? = null
    val showVpnAcceleratorNotifications = true
    val bypassLocalTraffic = false

    val secureCoreEnabled = false
    val apiUseDoH: Boolean = true
    val vpnAcceleratorEnabled: Boolean = true
    val safeModeEnabled: Boolean = true
    val randomizedNatEnabled: Boolean = true
    val protocol: ProtocolSelection = ProtocolSelection(VpnProtocol.Smart)
    val telemetryEnabled: Boolean = false
    val netShieldProtocol: NetShieldProtocol? = null

    @VisibleForTesting
    constructor(mtuSize: Int) : this() {
        this.mtuSize = mtuSize
    }
}
