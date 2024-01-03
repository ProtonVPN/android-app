/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.settings.data

import android.os.Build
import com.protonvpn.android.logging.itemCountToLog
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.userstorage.UUIDSerializer
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SplitTunnelingSettings(
    val isEnabled: Boolean = false,
    val excludedIps: List<String> = emptyList(),
    val excludedApps: List<String> = emptyList()
)

@Serializable
data class LocalUserSettings(
    val apiUseDoh: Boolean = true,
    val connectOnBoot: Boolean = false,
    @Serializable(with = UUIDSerializer::class)
    val defaultProfileId: UUID? = null,
    val lanConnections: Boolean = false,
    val mtuSize: Int = 1375,
    val netShield: NetShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
    val protocol: ProtocolSelection = ProtocolSelection.SMART,
    val randomizedNat: Boolean = true,
    val safeMode: Boolean? = true,
    val secureCore: Boolean = false,
    val splitTunneling: SplitTunnelingSettings = SplitTunnelingSettings(),
    val telemetry: Boolean = true,
    val vpnAccelerator: Boolean = true,
    // Whenever adding a new setting add it also in toLogList below.
) {
    companion object {
        val Default = LocalUserSettings()
    }
}

// Provide log strings for all settings.
fun LocalUserSettings.toLogList(profileManager: ProfileManager): List<String> {
    val regularSettings = listOf(
        "Default profile ${profileManager.findProfile(defaultProfileId)?.toLog(this)}",
        "LAN connections: ${lanConnections.toLog()}",
        "MTU size: $mtuSize bytes",
        "NetShield: $netShield",
        "Protocol: ${protocol.apiName}",
        "Restricted NAT: ${randomizedNat.toLog()}",
        "Safe mode: ${safeMode.toLog()}",
        "Secure Core: ${secureCore.toLog()}",
        with(splitTunneling) {
            "Split tunneling: ${isEnabled.toLog()}," +
                " excluded apps: ${excludedApps.itemCountToLog()}, excluded IPs: ${excludedIps.itemCountToLog()}"
        },
        "Telemetry: ${telemetry.toLog()}",
        "Use DoH for API: ${apiUseDoh.toLog()}",
        "VPN Accelerator: ${vpnAccelerator.toLog()}",
    )
    val conditional = buildList {
        if (Build.VERSION.SDK_INT < 26)
            add("Connect on boot: ${connectOnBoot.toLog()}")
    }
    return regularSettings + conditional
}
